package com.larsons.game.core;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Relaunches the game on macOS's first thread when it was started without
 * {@code -XstartOnFirstThread} — which is what double-clicking a jar does,
 * because a manifest cannot ask for it.
 *
 * <p>Adapted from the engine's {@code MacGlLauncher}. The engine's version
 * also has to decide whether to fall back to Java2D; this game is GPU-only,
 * so it only ever relaunches. The Gradle tasks already pass the flag, so
 * under {@code ./gradlew runGpu} this does nothing.
 */
public final class MacLauncher {

    static final String CHILD_PROPERTY = "larsons.launch.relaunched";

    private MacLauncher() {}

    /** True if the game ran (and finished) in a relaunched child process. */
    public static boolean relaunchIfNeeded(String[] args) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) return false;
        if (Boolean.getBoolean(CHILD_PROPERTY)) return false;
        String marker = System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + ProcessHandle.current().pid());
        if ("1".equals(marker)) return false;

        List<String> command = new ArrayList<>();
        command.add(ProcessHandle.current().info().command().orElse(
                System.getProperty("java.home") + "/bin/java"));
        command.add("-XstartOnFirstThread");
        command.add("-Djava.awt.headless=true");
        command.add("-D" + CHILD_PROPERTY + "=true");
        for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (a.startsWith("-agentlib:jdwp") || a.startsWith("-XstartOnFirstThread")) continue;
            command.add(a);
        }
        command.add("-cp");
        command.add(System.getProperty("java.class.path", "."));
        command.add(com.larsons.game.Main.class.getName());
        command.addAll(List.of(args));
        try {
            int status = new ProcessBuilder(command).inheritIO().start().waitFor();
            System.exit(status);
            return true;
        } catch (Exception e) {
            System.err.println("[launch] could not relaunch on the first thread: " + e);
            return false;
        }
    }
}
