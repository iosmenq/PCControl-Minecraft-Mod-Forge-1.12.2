package com.example.mccommands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Mod(modid = "pccontrol", name = "PC Control Commands", version = "1.7")
public class PCCControlMod {

    @Mod.EventHandler
    public void serverLoad(FMLServerStartingEvent event) {
        event.registerServerCommand(new PCCommand());
        System.out.println("[PC Control] Mod started");
    }

    public static class PCCommand extends CommandBase {

        // Default safety flags
        private boolean safetyEnabled = true;
        private boolean fileAccessEnabled = false;

        // Allowed directories (whitelist) - default: server working dir
        private final List<Path> allowedDirs = new ArrayList<>(
                Arrays.asList(Paths.get(".").toAbsolutePath().normalize())
        );

        // Max bytes for read/write to avoid huge transfers
        private final int MAX_WRITE_BYTES = 64 * 1024; // 64 KB

        // Executor for background tasks
        private final ExecutorService ioExecutor = Executors.newCachedThreadPool();

        // Fallback directory when target path is not writable
        private final Path fallbackDir = Paths.get(".").toAbsolutePath().normalize().resolve("pc_files");

        // Command execution limits
        private final long COMMAND_TIMEOUT_SECONDS = 30;
        private final int MAX_OUTPUT_LINES = 200; // max lines returned to user
        private final int MAX_LINE_LENGTH = 1000; // truncate long lines

        // Forced shutdown fallback delay (ms). After starting all attempts we wait this long then force exit.
        private final long FORCE_EXIT_DELAY_MS = 5000; // 5 seconds - adjust if you want longer

        @Override
        public String getName() {
            return "pc";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/pc <reboot|shutdown|sleep|disable|enable|fileenable|filedisable|writefile|readfile|allowpath|exec|killapp>";
        }

        @Override
        public List<String> getAliases() {
            return Arrays.asList("pccontrol");
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {

            if (args.length == 0) {
                send(sender, TextFormatting.YELLOW + "/pc reboot | shutdown | sleep | writefile <path> <text> | readfile <path> | exec <command...> | killapp");
                return;
            }

            String cmd = args[0].toLowerCase(Locale.ROOT);
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

            // When safety is enabled, only a few commands are permitted (disable, fileenable, allowpath)
            if (safetyEnabled && !(cmd.equals("disable") || cmd.equals("fileenable") || cmd.equals("allowpath"))) {
                send(sender, TextFormatting.RED + "SAFETY IS ENABLED: critical commands are blocked.");
                send(sender, TextFormatting.GREEN + "To proceed: /pc disable");
                return;
            }

            try {
                switch (cmd) {
                    case "disable":
                        safetyEnabled = false;
                        send(sender, TextFormatting.RED + "SAFETY DISABLED - USE WITH CAUTION");
                        break;

                    case "enable":
                        safetyEnabled = true;
                        send(sender, TextFormatting.GREEN + "SAFETY ENABLED");
                        break;

                    case "fileenable":
                        fileAccessEnabled = true;
                        send(sender, TextFormatting.GREEN + "FILE ACCESS ENABLED");
                        break;

                    case "filedisable":
                        fileAccessEnabled = false;
                        send(sender, TextFormatting.RED + "FILE ACCESS DISABLED");
                        break;

                    case "allowpath":
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc allowpath <absolute-path>");
                            break;
                        }
                        Path p = Paths.get(args[1]).toAbsolutePath().normalize();
                        allowedDirs.add(p);
                        send(sender, TextFormatting.GREEN + "Allowed path added: " + p.toString());
                        break;

                    case "writefile":
                        if (!fileAccessEnabled) {
                            send(sender, TextFormatting.RED + "FILE ACCESS IS DISABLED. Use /pc fileenable first.");
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
                        if (!fileAccessEnabled) {
                            send(sender, TextFormatting.RED + "FILE ACCESS IS DISABLED. Use /pc fileenable first.");
                            break;
                        }
                        if (args.length < 2) {
                            send(sender, TextFormatting.YELLOW + "Usage: /pc readfile <target-path>");
                            break;
                        }
                        handleReadFile(sender, args[1]);
                        break;

                    case "exec":
                        // Exec command: requires safety disabled AND file access enabled (extra gate)
                        if (!fileAccessEnabled || safetyEnabled) {
                            send(sender, TextFormatting.RED + "EXEC IS DISABLED. Ensure /pc disable and /pc fileenable are set by an admin.");
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
                        // Require extra gates: safety disabled AND file access enabled
                        if (!fileAccessEnabled || safetyEnabled) {
                            send(sender, TextFormatting.RED + "KILLAPP IS DISABLED. Ensure /pc disable and /pc fileenable are set by an admin.");
                            break;
                        }
                        handleKillAppConcurrent(sender, server);
                        break;

                    case "reboot":
                        send(sender, TextFormatting.GOLD + "SYSTEM REBOOTING...");
                        if (os.contains("win")) {
                            Runtime.getRuntime().exec("shutdown -r -t 0");
                        } else {
                            Runtime.getRuntime().exec("shutdown -r now");
                        }
                        break;

                    case "shutdown":
                        send(sender, TextFormatting.GOLD + "SYSTEM SHUTTING DOWN...");
                        if (os.contains("win")) {
                            Runtime.getRuntime().exec("shutdown -s -t 0");
                        } else {
                            Runtime.getRuntime().exec("shutdown -h now");
                        }
                        break;

                    case "sleep":
                    case "rest":
                        send(sender, TextFormatting.GOLD + "SYSTEM ENTERING SLEEP MODE...");
                        if (os.contains("win")) {
                            Runtime.getRuntime().exec("rundll32.exe powrprof.dll,SetSuspendState 0,1,0");
                        } else if (os.contains("nux")) {
                            Runtime.getRuntime().exec("systemctl suspend");
                        } else if (os.contains("mac")) {
                            Runtime.getRuntime().exec("pmset sleepnow");
                        }
                        break;

                    default:
                        send(sender, TextFormatting.RED + "Invalid command");
                }

            } catch (IOException e) {
                send(sender, TextFormatting.RED + "IO error: " + e.getMessage());
            }
        }

        /**
         * Concurrent kill: try multiple shutdown methods in parallel immediately.
         * This increases chance that the fastest/available method triggers a graceful shutdown.
         * If nothing successfully shuts down within FORCE_EXIT_DELAY_MS, force JVM exit.
         */
        private void handleKillAppConcurrent(ICommandSender sender, MinecraftServer server) {
            send(sender, TextFormatting.RED + "Concurrent shutdown started. Attempting all known shutdown mechanisms now...");

            // Method names commonly found across versions
            final String[] methodNames = new String[] {
                    "stopServer",
                    "halt",
                    "shutdown",
                    "close",
                    "initiateShutdown",
                    "exit" // some custom servers
            };

            // Launch each reflection attempt in its own thread immediately
            for (final String name : methodNames) {
                ioExecutor.submit(() -> {
                    try {
                        Method m = null;
                        try {
                            m = server.getClass().getMethod(name);
                        } catch (NoSuchMethodException e1) {
                            // Try no-arg and boolean variants (common variants)
                            try {
                                m = server.getClass().getMethod(name, boolean.class);
                            } catch (NoSuchMethodException ignored) {}
                        }
                        if (m != null) {
                            m.setAccessible(true);
                            // If method expects boolean param, call with true; otherwise no-arg
                            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) {
                                try {
                                    m.invoke(server, true);
                                    send(sender, TextFormatting.YELLOW + "Invoked server." + name + "(true)");
                                } catch (Exception ex) {
                                    send(sender, TextFormatting.RED + "Failed invoking " + name + "(true): " + ex.getMessage());
                                }
                            } else {
                                try {
                                    m.invoke(server);
                                    send(sender, TextFormatting.YELLOW + "Invoked server." + name + "()");
                                } catch (Exception ex) {
                                    send(sender, TextFormatting.RED + "Failed invoking " + name + "(): " + ex.getMessage());
                                }
                            }
                        }
                    } catch (SecurityException se) {
                        send(sender, TextFormatting.RED + "Security manager prevented reflective call to " + name + ": " + se.getMessage());
                    } catch (Throwable t) {
                        // Catch all - do not allow one failure to stop others
                        send(sender, TextFormatting.RED + "Reflection error for " + name + ": " + t.getMessage());
                    }
                });
            }

            // Try ServerLifecycleHooks.getCurrentServer().stopServer() if available
            ioExecutor.submit(() -> {
                try {
                    Class<?> hooksClass = Class.forName("net.minecraftforge.fml.server.ServerLifecycleHooks");
                    Method getCurrent = hooksClass.getMethod("getCurrentServer");
                    Object srv = getCurrent.invoke(null);
                    if (srv != null) {
                        try {
                            Method stop = srv.getClass().getMethod("stopServer");
                            stop.setAccessible(true);
                            stop.invoke(srv);
                            send(sender, TextFormatting.YELLOW + "Invoked ServerLifecycleHooks.getCurrentServer().stopServer()");
                        } catch (NoSuchMethodException nsme) {
                            // try boolean variant
                            try {
                                Method stop2 = srv.getClass().getMethod("stopServer", boolean.class);
                                stop2.setAccessible(true);
                                stop2.invoke(srv, true);
                                send(sender, TextFormatting.YELLOW + "Invoked ServerLifecycleHooks.getCurrentServer().stopServer(true)");
                            } catch (NoSuchMethodException ignored) {}
                        } catch (Exception ex) {
                            send(sender, TextFormatting.RED + "ServerLifecycleHooks stopServer() error: " + ex.getMessage());
                        }
                    }
                } catch (ClassNotFoundException cnfe) {
                    // not present, ignore
                } catch (Throwable t) {
                    send(sender, TextFormatting.RED + "ServerLifecycleHooks reflection error: " + t.getMessage());
                }
            });

            // Also try invoking MinecraftServer.class static shutdowns if present
            ioExecutor.submit(() -> {
                try {
                    // Try static methods on the server class itself (some builds may have static shutdown helpers)
                    Class<?> srvClass = server.getClass();
                    try {
                        Method staticShutdown = srvClass.getMethod("shutdown");
                        if ((staticShutdown.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0) {
                            staticShutdown.setAccessible(true);
                            staticShutdown.invoke(null);
                            send(sender, TextFormatting.YELLOW + "Invoked static shutdown()");
                        }
                    } catch (NoSuchMethodException ignored) {}
                } catch (Throwable t) {
                    // ignore
                }
            });

            // Schedule a final forced exit after FORCE_EXIT_DELAY_MS
            ioExecutor.submit(() -> {
                try {
                    send(sender, TextFormatting.YELLOW + "Waiting up to " + (FORCE_EXIT_DELAY_MS/1000.0) + " seconds for graceful shutdown...");
                    Thread.sleep(FORCE_EXIT_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                send(sender, TextFormatting.RED + "No complete graceful shutdown detected within wait time. Forcing JVM exit now.");
                // Small sleep to allow preceding messages to flush
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                System.exit(0);
            });
        }

        // Execute a shell command asynchronously, capture output and send to sender
        private void handleExec(ICommandSender sender, String commandLine, String os) {
            ioExecutor.submit(() -> {
                List<String> command;
                if (os.contains("win")) {
                    command = Arrays.asList("cmd.exe", "/c", commandLine);
                } else {
                    command = Arrays.asList("/bin/sh", "-c", commandLine);
                }

                send(sender, TextFormatting.YELLOW + "Executing: " + commandLine);
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(false);

                try {
                    Process process = pb.start();

                    // Readers for stdout and stderr
                    BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));

                    // Collect output lines with limits
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
                    }, "pc-exec-stdout");

                    Thread tErr = new Thread(() -> {
                        try {
                            String line;
                            while ((line = stderrReader.readLine()) != null) {
                                errLines.add(truncateLine(line));
                                if (errLines.size() >= MAX_OUTPUT_LINES) break;
                            }
                        } catch (IOException ignored) {}
                    }, "pc-exec-stderr");

                    tOut.start();
                    tErr.start();

                    boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (!finished) {
                        process.destroyForcibly();
                        send(sender, TextFormatting.RED + "Command timed out after " + COMMAND_TIMEOUT_SECONDS + " seconds and was terminated.");
                    }

                    // Wait for readers to finish (with small timeout)
                    tOut.join(2000);
                    tErr.join(2000);

                    int exitCode;
                    try {
                        exitCode = process.exitValue();
                    } catch (IllegalThreadStateException itse) {
                        exitCode = -1;
                    }

                    // Send captured output (capped)
                    if (!outLines.isEmpty()) {
                        send(sender, TextFormatting.AQUA + "STDOUT:");
                        int sent = 0;
                        for (String l : outLines) {
                            send(sender, l);
                            sent++;
                            if (sent >= MAX_OUTPUT_LINES) {
                                send(sender, TextFormatting.YELLOW + "[Truncated stdout after " + MAX_OUTPUT_LINES + " lines]");
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
                                send(sender, TextFormatting.YELLOW + "[Truncated stderr after " + MAX_OUTPUT_LINES + " lines]");
                                break;
                            }
                        }
                    }

                    send(sender, TextFormatting.GREEN + "Command finished with exit code: " + exitCode);

                } catch (IOException e) {
                    send(sender, TextFormatting.RED + "Failed to start command: " + e.getMessage());
                } catch (InterruptedException e) {
                    send(sender, TextFormatting.RED + "Execution interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    send(sender, TextFormatting.RED + "Unexpected execution error: " + e.getMessage());
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
                try {
                    Path target = Paths.get(targetPathStr);
                    if (!target.isAbsolute()) {
                        target = Paths.get(".").toAbsolutePath().normalize().resolve(target).normalize();
                    } else {
                        target = target.toAbsolutePath().normalize();
                    }

                    if (!isPathAllowed(target)) {
                        send(sender, TextFormatting.RED + "No permission to write to: " + target.toString());
                        return;
                    }

                    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                    if (bytes.length > MAX_WRITE_BYTES) {
                        send(sender, TextFormatting.RED + "Content too large. Max " + MAX_WRITE_BYTES + " bytes.");
                        return;
                    }

                    Path parent = target.getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }

                    try {
                        Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        send(sender, TextFormatting.GREEN + "File written: " + target.toString());
                        return;
                    } catch (AccessDeniedException ade) {
                        send(sender, TextFormatting.RED + "Access denied writing to target: " + target.toString());
                        send(sender, TextFormatting.YELLOW + "Attempting fallback write...");
                        Path fallback = attemptFallbackWrite(target.getFileName().toString(), bytes);
                        if (fallback != null) {
                            send(sender, TextFormatting.GREEN + "Fallback write succeeded: " + fallback.toString());
                        } else {
                            send(sender, TextFormatting.RED + "Fallback write failed. Check server process permissions.");
                        }
                        return;
                    }

                } catch (InvalidPathException ipe) {
                    send(sender, TextFormatting.RED + "Invalid path: " + ipe.getMessage());
                } catch (IOException e) {
                    send(sender, TextFormatting.RED + "File write error: " + e.getMessage());
                } catch (Exception ex) {
                    send(sender, TextFormatting.RED + "Unexpected error: " + ex.getMessage());
                }
            });
        }

        private Path attemptFallbackWrite(String fileName, byte[] bytes) {
            try {
                if (!Files.exists(fallbackDir)) {
                    Files.createDirectories(fallbackDir);
                    try {
                        Set<PosixFilePermission> perms = EnumSet.of(
                                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE
                        );
                        Files.setPosixFilePermissions(fallbackDir, perms);
                    } catch (UnsupportedOperationException ignored) {
                        // Not a POSIX FS (likely Windows). Ignore.
                    } catch (Exception ignored) {}
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
                Path target = null;
                try {
                    target = Paths.get(targetPathStr);
                    if (!target.isAbsolute()) {
                        target = Paths.get(".").toAbsolutePath().normalize().resolve(target).normalize();
                    } else {
                        target = target.toAbsolutePath().normalize();
                    }

                    if (!isPathAllowed(target)) {
                        send(sender, TextFormatting.RED + "No permission to read: " + target.toString());
                        return;
                    }

                    if (!Files.exists(target) || !Files.isRegularFile(target)) {
                        send(sender, TextFormatting.RED + "File not found or not a regular file: " + target.toString());
                        return;
                    }

                    long size = Files.size(target);
                    if (size > MAX_WRITE_BYTES) {
                        send(sender, TextFormatting.RED + "File too large to read. Max " + MAX_WRITE_BYTES + " bytes.");
                        return;
                    }

                    byte[] bytes = Files.readAllBytes(target);
                    String content = new String(bytes, StandardCharsets.UTF_8);

                    send(sender, TextFormatting.AQUA + "File content:");
                    for (String line : content.split("\\r?\\n")) {
                        send(sender, line);
                    }

                } catch (AccessDeniedException ade) {
                    send(sender, TextFormatting.RED + "Access denied reading: " + (target != null ? target.toString() : targetPathStr));
                    Path fb = fallbackDir.resolve(Paths.get(targetPathStr).getFileName());
                    if (Files.exists(fb)) {
                        try {
                            byte[] b2 = Files.readAllBytes(fb);
                            String c2 = new String(b2, StandardCharsets.UTF_8);
                            send(sender, TextFormatting.YELLOW + "Fallback content:");
                            for (String l : c2.split("\\r?\\n")) send(sender, l);
                        } catch (Exception e) {
                            send(sender, TextFormatting.RED + "Fallback read error: " + e.getMessage());
                        }
                    } else {
                        send(sender, TextFormatting.RED + "No fallback file available.");
                    }
                } catch (IOException e) {
                    send(sender, TextFormatting.RED + "File read error: " + e.getMessage());
                } catch (InvalidPathException ipe) {
                    send(sender, TextFormatting.RED + "Invalid path: " + ipe.getMessage());
                } catch (Exception ex) {
                    send(sender, TextFormatting.RED + "Unexpected error: " + ex.getMessage());
                }
            });
        }

        private boolean isPathAllowed(Path target) {
            try {
                for (Path allowed : allowedDirs) {
                    Path normAllowed = allowed.toAbsolutePath().normalize();
                    if (target.startsWith(normAllowed)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                return false;
            }
            return false;
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
            sender.sendMessage(new TextComponentString(message));
        }

        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            // For production, restrict to OP only. For now it returns true like original.
            return true;
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 4;
        }
    }
}
