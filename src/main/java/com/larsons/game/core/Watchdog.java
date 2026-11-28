package com.larsons.game.core;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Says what the game was doing when it stopped drawing: a report in {@code
 * logs/} of every thread and where each one is, the machine, the graphics and
 * what was loading - written when the main thread has drawn no frame for
 * {@link #STALL_SECONDS} seconds (a hang), when the loading screen gives up
 * waiting on a step ({@code Boot#LATE}), and when the game crashes.
 *
 * <p>A game that freezes on a machine nobody here can test on (a MacBook
 * Air) cannot be fixed without knowing where it stopped; the report is that.
 * The game keeps going (a stall may pass); the console says where the report
 * is, and the player sends it.
 *
 * <p>The main thread calls {@link #beat} once a frame (and as the loading
 * screen draws); a daemon thread checks on it.
 */
public final class Watchdog {

    /** Seconds without a frame that count as a hang. */
    public static final int STALL_SECONDS = 10;

    private static volatile long lastBeat = System.nanoTime();
    private static volatile boolean reported;
    private static volatile Supplier<String> about = () -> "";
    private static volatile Path folder = Path.of("logs");
    private static Thread thread;

    private Watchdog() {}

    /**
     * Start watching the main thread; {@code about} says what the game is
     * (the machine, the graphics, what is loading) for a report, written to
     * {@code logs}.
     */
    public static synchronized void start(Path logs, Supplier<String> aboutGame) {
        folder = logs;
        about = aboutGame == null ? () -> "" : aboutGame;
        beat();
        if (thread != null) return;
        thread = new Thread(Watchdog::watch, "watchdog");
        thread.setDaemon(true);
        thread.start();
    }

    /** The main thread has drawn a frame. */
    public static void beat() {
        lastBeat = System.nanoTime();
        reported = false;
    }

    /** The main thread is waiting on the player on purpose (a file dialog): not a hang until {@code false}. */
    public static void hold(boolean on) {
        held = on;
        beat();
    }

    private static volatile boolean held;

    private static void watch() {
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            long quiet = (System.nanoTime() - lastBeat) / 1_000_000_000L;
            if (quiet >= STALL_SECONDS && !reported && !held) {
                reported = true;
                Path p = report("The game has drawn no frame for " + quiet + " seconds: it looks frozen. "
                        + "Where every thread is now (the main thread draws):", null);
                System.err.println("[watchdog] no frame for " + quiet + " s - what each thread is doing is in "
                        + (p == null ? "the console above" : p.toAbsolutePath()) + ". Please send that file.");
            }
        }
    }

    /**
     * Write a report: {@code why}, the machine and the game ({@code about}),
     * the memory, {@code error} if there is one, and every thread's stack.
     * Returns the file, or null if it could not be written (it is printed then).
     */
    public static Path report(String why, Throwable error) {
        return report(folder, why, error);
    }

    /** {@link #report(String, Throwable)}, into {@code folder}. */
    static Path report(Path folder, String why, Throwable error) {
        String text = text(why, error);
        String name = "hang-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt";
        if (error != null) name = name.replace("hang-", "crash-");
        try {
            Files.createDirectories(folder);
            Path f = folder.resolve(name);
            Files.writeString(f, text, StandardCharsets.UTF_8);
            return f;
        } catch (IOException | RuntimeException e) {
            System.err.println(text);
            return null;
        }
    }

    static String text(String why, Throwable error) {
        StringBuilder sb = new StringBuilder();
        sb.append("Larson's Game - ").append(why).append("\n\n");
        try {
            String a = about.get();
            if (a != null && !a.isBlank()) sb.append(a.strip()).append("\n\n");
        } catch (RuntimeException e) {
            sb.append("(could not say what the game was doing: ").append(e).append(")\n\n");
        }
        Runtime rt = Runtime.getRuntime();
        sb.append(String.format("Java %s (%s) on %s %s; memory: %d MB used of %d MB, at most %d MB%n%n",
                System.getProperty("java.version"), System.getProperty("java.vendor"),
                System.getProperty("os.name"), System.getProperty("os.arch"),
                (rt.totalMemory() - rt.freeMemory()) >> 20, rt.totalMemory() >> 20, rt.maxMemory() >> 20));
        if (error != null) {
            java.io.StringWriter w = new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(w));
            sb.append("What went wrong:\n").append(w).append('\n');
        }
        sb.append("Threads:\n");
        try {
            ThreadInfo[] infos = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
            for (ThreadInfo t : infos) sb.append(full(t)).append('\n');
        } catch (RuntimeException | LinkageError e) {
            for (Map.Entry<Thread, StackTraceElement[]> t : Thread.getAllStackTraces().entrySet()) {
                sb.append('"').append(t.getKey().getName()).append("\" ").append(t.getKey().getState()).append('\n');
                for (StackTraceElement el : t.getValue()) sb.append("    at ").append(el).append('\n');
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /** A thread's whole stack ({@link ThreadInfo#toString} stops at eight frames). */
    private static String full(ThreadInfo t) {
        StringBuilder sb = new StringBuilder();
        sb.append('"').append(t.getThreadName()).append("\" ").append(t.getThreadState());
        if (t.getLockName() != null) sb.append(" on ").append(t.getLockName());
        if (t.getLockOwnerName() != null) sb.append(" owned by \"").append(t.getLockOwnerName()).append('"');
        if (t.isInNative()) sb.append(" (in native code)");
        sb.append('\n');
        for (StackTraceElement el : t.getStackTrace()) sb.append("    at ").append(el).append('\n');
        return sb.toString();
    }
}
