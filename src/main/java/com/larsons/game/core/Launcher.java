package com.larsons.game.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Starts the game from its jar the way {@code ./gradlew runGpu} starts it,
 * however the jar was started - a launcher script, {@code java -jar}, or a
 * double-click, which cannot pass the JVM a single flag. It relaunches the
 * game once, in a child JVM, with:
 *
 * <ul>
 *   <li>the build's JVM flags: a heap of 60 % of the machine, the ZGC collector,
 *       headless AWT and, on macOS, {@code -XstartOnFirstThread} (GLFW must
 *       have the process's first thread there, and a manifest cannot ask for it);
 *       on a machine with 8 GB of memory or less (a MacBook Air, which shares
 *       it with the graphics) a heap of half of it, kept to under a third
 *       while the game does not need more ({@link #memoryFlags});</li>
 *   <li>{@code runGpu}'s settings: {@code larsons.gpu=required}, and on
 *       Linux the hint for the discrete GPU (multisampling is the game's own
 *       setting: Settings, Display);</li>
 *   <li>the jar's own folder as the working directory, so {@code assets/},
 *       {@code config/} and {@code screenshots/} are the ones beside it,
 *       wherever it was started from;</li>
 *   <li>the sprite sheets of a fresh copy, which come in parts, put together
 *       first ({@link SpritePack#joinParts}), once;</li>
 *   <li>everything it prints copied to {@code logs/latest.log} beside the jar
 *       (the run before is kept as {@code logs/previous.log}), for whoever is
 *       sending feedback.</li>
 * </ul>
 *
 * Anything given on the command line wins ({@code java -Xmx4g
 * -Dlarsons.sprites.scale=0.5 -jar larsons-game.jar}), and
 * {@code -Dlarsons.launch.relaunch=false} runs the game in the JVM it was
 * started in. Under Gradle or IntelliJ (no jar) only the macOS first-thread
 * relaunch is left, as the engine's {@code MacGlLauncher} did - the Gradle
 * tasks pass the flags themselves.
 */
public final class Launcher {

    static final String CHILD_PROPERTY = "larsons.launch.relaunched";
    static final String RELAUNCH_PROPERTY = "larsons.launch.relaunch";

    /** The JVM refused a flag (an old Windows has no ZGC): run again with only the plain ones. */
    private static final String[] VM_FAILED = {
            "Could not create the Java Virtual Machine", "Error occurred during initialization of VM"};

    private Launcher() {}

    /** Bytes of memory at or under which the heap is kept smaller: 8 GB, with room for what the system keeps. */
    static final long LOW_MEMORY = Hardware.LOW_MEMORY;

    /** What the child JVM is started for: this machine, or a test's ({@code memory} bytes; 0: unknown). */
    record Host(String os, String arch, int javaFeature, long memory) {

        Host(String os, String arch, int javaFeature) {
            this(os, arch, javaFeature, 0);
        }

        static Host current() {
            return new Host(System.getProperty("os.name", ""), System.getProperty("os.arch", ""),
                    Runtime.version().feature(), Hardware.totalMemory());
        }

        /** Whether the machine has 8 GB of memory or less. */
        boolean lowMemory() {
            return memory > 0 && memory <= LOW_MEMORY;
        }

        boolean mac() { return os.toLowerCase(Locale.ROOT).contains("mac"); }

        boolean windows() { return os.toLowerCase(Locale.ROOT).contains("windows"); }

        boolean linux() { return os.toLowerCase(Locale.ROOT).contains("linux"); }

        /** ZGC runs on 64-bit x86 everywhere and on ARM64 Linux and macOS (as in the build). */
        boolean zgc() {
            String a = arch.toLowerCase(Locale.ROOT);
            return a.equals("amd64") || a.equals("x86_64") || (a.equals("aarch64") && !windows());
        }
    }

    /** True if the game ran (and finished) in a relaunched child process. */
    public static boolean relaunchIfNeeded(String[] args) {
        if (Boolean.getBoolean(CHILD_PROPERTY)) return false;
        Host host = Host.current();
        boolean firstThread = !host.mac()
                || "1".equals(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + ProcessHandle.current().pid()));
        Path jar = runningJar();
        boolean relaunchJar = jar != null && !"false".equalsIgnoreCase(System.getProperty(RELAUNCH_PROPERTY));
        if (!relaunchJar && firstThread) return false;

        List<String> given = ManagementFactory.getRuntimeMXBean().getInputArguments();
        String java = ProcessHandle.current().info().command().orElse(
                Path.of(System.getProperty("java.home"), "bin", host.windows() ? "java.exe" : "java").toString());
        // the jar by its full path: the child runs in the jar's folder, not where this one was started
        String classpath = relaunchJar ? jar.toAbsolutePath().toString() : System.getProperty("java.class.path", ".");
        Path folder = relaunchJar ? jar.toAbsolutePath().getParent() : null;
        try {
            int status = run(command(host, java, given, relaunchJar, true, classpath, args), host, folder);
            if (status == VM_FAILED_STATUS) {
                System.err.println("[launch] the JVM did not take the game's flags; starting with the plain ones");
                status = run(command(host, java, given, relaunchJar, false, classpath, args), host, folder);
                if (status == VM_FAILED_STATUS) status = 1;
            }
            System.exit(status);
            return true;
        } catch (Exception e) {
            System.err.println("[launch] could not relaunch the game: " + e);
            return false;
        }
    }

    /** The jar the game is running from, or null (classes folders: Gradle, IntelliJ). */
    static Path runningJar() {
        try {
            Path p = Path.of(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")
                    ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The child's command line: {@code java}, its JVM flags - the ones this JVM
     * was given, plus (from a jar) the game's own where none was given;
     * {@code tuned} false leaves out the collector and the heap - then the
     * classpath, the main class and {@code args}.
     */
    static List<String> command(Host host, String java, List<String> given, boolean fromJar, boolean tuned,
                                String classpath, String[] args) {
        List<String> c = new ArrayList<>();
        c.add(java);
        if (host.mac()) c.add("-XstartOnFirstThread");
        c.add("-Djava.awt.headless=true");
        c.add("-D" + CHILD_PROPERTY + "=true");
        if (fromJar) {
            boolean heapGiven = given.stream().anyMatch(a -> a.startsWith("-Xmx") || a.startsWith("-XX:MaxRAM"));
            // A collector that never stops the game for more than a fraction of a
            // millisecond (see build.gradle.kts); generational ZGC is a flag on 21-22.
            boolean zgc = tuned && host.zgc() && given.stream().noneMatch(a -> a.matches("-XX:\\+Use\\w*GC"));
            if (tuned && !heapGiven) c.addAll(memoryFlags(host, zgc));
            if (zgc) {
                c.add("-XX:+UseZGC");
                if (host.javaFeature() < 23) c.add("-XX:+ZGenerational");
            }
            Map<String, String> defaults = new LinkedHashMap<>();
            defaults.put("larsons.gpu", "required");
            defaults.forEach((k, v) -> {
                if (given.stream().noneMatch(a -> a.startsWith("-D" + k + "="))) c.add("-D" + k + "=" + v);
            });
        }
        for (String a : given) {
            if (a.startsWith("-agentlib:jdwp") || a.equals("-XstartOnFirstThread")
                    || a.startsWith("-D" + CHILD_PROPERTY) || a.equals("-Djava.awt.headless=true")) continue;
            if (!tuned && (a.startsWith("-XX:+UseZGC") || a.startsWith("-XX:+ZGenerational"))) continue;
            c.add(a);
        }
        c.add("-cp");
        c.add(classpath);
        c.add(com.larsons.game.Main.class.getName());
        c.addAll(List.of(args));
        return c;
    }

    /**
     * The heap's flags for {@code host}: 60 % of the machine's memory; on one
     * of 8 GB or less, half of it - and with ZGC (which otherwise keeps what it
     * has once used) kept to under a third, handing back what it no longer
     * needs after half a minute, so the graphics, which share that memory on a
     * MacBook, are left room. Same rule as build.gradle.kts.
     */
    static List<String> memoryFlags(Host host, boolean zgc) {
        if (!host.lowMemory()) return List.of("-XX:MaxRAMPercentage=60");
        List<String> f = new ArrayList<>();
        f.add("-XX:MaxRAMPercentage=50");
        if (zgc) {
            f.add("-XX:SoftMaxHeapSize=" + (host.memory() * 3 / 10 >> 20) + "m");
            f.add("-XX:ZUncommitDelay=30");
        }
        return f;
    }

    /**
     * The environment that asks a hybrid-graphics Linux machine for its
     * discrete GPU, as {@code runGpu} sets it (Windows and macOS choose for
     * themselves): Mesa's DRI_PRIME, and NVIDIA's offload only where its
     * driver is loaded. What is set already is left alone.
     */
    static Map<String, String> gpuEnvironment(Host host, Map<String, String> current, boolean nvidia) {
        Map<String, String> env = new LinkedHashMap<>();
        if (!host.linux()) return env;
        env.put("DRI_PRIME", "1");
        if (nvidia) {
            env.put("__NV_PRIME_RENDER_OFFLOAD", "1");
            env.put("__GLX_VENDOR_LIBRARY_NAME", "nvidia");
        }
        env.keySet().removeIf(current::containsKey);
        return env;
    }

    private static final int VM_FAILED_STATUS = -1;

    /**
     * Runs {@code command}; from a jar ({@code folder} not null) in that folder,
     * with its output copied to the log. The child's exit status, or {@link
     * #VM_FAILED_STATUS} when its JVM would not start with the flags given.
     */
    private static int run(List<String> command, Host host, Path folder) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command).redirectInput(ProcessBuilder.Redirect.INHERIT);
        if (folder == null) {
            return pb.inheritIO().start().waitFor();
        }
        pb.directory(folder.toFile());
        pb.environment().putAll(gpuEnvironment(host, pb.environment(),
                Files.exists(Path.of("/proc/driver/nvidia/version"))));
        OutputStream log = openLog(folder);
        // a fresh copy's sprite sheets come in parts: joined here, once, with
        // the console window to say so, before the game opens its own
        SpritePack.joinParts(folder.resolve("assets"), new PrintStream(new Tee(System.out, log), true));
        Process p = pb.start();
        Thread hook = new Thread(p::destroy, "launcher-stop-game");
        Runtime.getRuntime().addShutdownHook(hook);
        boolean[] vmFailed = {false};
        Thread out = pump(p.getInputStream(), System.out, log, vmFailed);
        Thread err = pump(p.getErrorStream(), System.err, log, vmFailed);
        long started = System.nanoTime();
        int status = p.waitFor();
        out.join();
        err.join();
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // already shutting down
        }
        if (log != null) {
            try {
                log.close();
            } catch (IOException ignored) {
                // the game has finished; a log that did not close is no reason to fail
            }
        }
        boolean quick = System.nanoTime() - started < 10_000_000_000L;
        return status != 0 && quick && vmFailed[0] ? VM_FAILED_STATUS : status;
    }

    /** {@code logs/latest.log} beside the jar (the last one moved to previous.log), or null. */
    private static OutputStream openLog(Path folder) {
        try {
            Path logs = Files.createDirectories(folder.resolve("logs"));
            Path latest = logs.resolve("latest.log");
            if (Files.exists(latest)) {
                Files.move(latest, logs.resolve("previous.log"), StandardCopyOption.REPLACE_EXISTING);
            }
            return Files.newOutputStream(latest);
        } catch (IOException | RuntimeException e) {
            System.err.println("[launch] no log file beside the game: " + e.getMessage());
            return null;
        }
    }

    /** Writes to the console and the log (if there is one); a log that fails stops only itself. */
    private static final class Tee extends OutputStream {
        private final PrintStream console;
        private final OutputStream log;

        Tee(PrintStream console, OutputStream log) {
            this.console = console;
            this.log = log;
        }

        @Override
        public void write(int b) {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            console.write(b, off, len);
            if (log == null) return;
            synchronized (log) {
                try {
                    log.write(b, off, len);
                } catch (IOException ignored) {
                    // a full disk stops the log, not the game
                }
            }
        }

        @Override
        public void flush() {
            console.flush();
            if (log == null) return;
            synchronized (log) {
                try {
                    log.flush();
                } catch (IOException ignored) {
                    // as above
                }
            }
        }
    }

    private static Thread pump(InputStream from, PrintStream console, OutputStream log, boolean[] vmFailed) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[8192];
            try (from) {
                int n;
                while ((n = from.read(buf)) > 0) {
                    console.write(buf, 0, n);
                    console.flush();
                    if (log != null) {
                        synchronized (log) {
                            try {
                                log.write(buf, 0, n);
                                log.flush();
                            } catch (IOException ignored) {
                                // a full disk stops the log, not the game
                            }
                        }
                    }
                    if (!vmFailed[0]) {
                        String s = new String(buf, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1);
                        for (String m : VM_FAILED) if (s.contains(m)) vmFailed[0] = true;
                    }
                }
            } catch (IOException ignored) {
                // the game has exited
            }
        }, "launcher-output");
        t.setDaemon(true);
        t.start();
        return t;
    }
}
