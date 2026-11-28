package com.larsons.game.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The child JVM the jar starts the game in: its flags, whatever the jar was started with. */
class LauncherTest {

    private static final Launcher.Host WINDOWS = new Launcher.Host("Windows 11", "amd64", 21);
    private static final Launcher.Host MAC_ARM = new Launcher.Host("Mac OS X", "aarch64", 21);
    private static final Launcher.Host LINUX_25 = new Launcher.Host("Linux", "amd64", 25);
    private static final Launcher.Host WINDOWS_ARM = new Launcher.Host("Windows 11", "aarch64", 21);

    private static List<String> jarCommand(Launcher.Host host, List<String> given, boolean tuned) {
        return Launcher.command(host, "java", given, true, tuned, "larsons-game.jar", new String[]{"x"});
    }

    @Test
    void fromTheJarItGetsTheBuildsFlagsAndRunGpusSettings() {
        List<String> c = jarCommand(WINDOWS, List.of(), true);
        assertEquals("java", c.get(0));
        assertTrue(c.contains("-XX:MaxRAMPercentage=60"));
        assertTrue(c.contains("-XX:+UseZGC"));
        assertTrue(c.contains("-XX:+ZGenerational"), "generational ZGC is a flag on 21");
        assertTrue(c.contains("-Djava.awt.headless=true"));
        assertTrue(c.contains("-Dlarsons.gpu=required"));
        assertTrue(c.stream().noneMatch(a -> a.startsWith("-Dlarsons.gpu.msaa")),
                "multisampling is the game's own setting (Lite turns it off)");
        assertTrue(c.contains("-D" + Launcher.CHILD_PROPERTY + "=true"));
        assertFalse(c.contains("-XstartOnFirstThread"), "only macOS");
        assertEquals(List.of("-cp", "larsons-game.jar", "com.larsons.game.Main", "x"), c.subList(c.size() - 4, c.size()));
    }

    @Test
    void macOsGetsTheFirstThread() {
        List<String> c = jarCommand(MAC_ARM, List.of(), true);
        assertEquals("-XstartOnFirstThread", c.get(1));
        assertTrue(c.contains("-XX:+UseZGC"), "ZGC runs on ARM64 macOS");
        // and under Gradle or IntelliJ (no jar) that is all it adds
        List<String> ide = Launcher.command(MAC_ARM, "java", List.of("-Xmx2g"), false, true, "classes", new String[0]);
        assertEquals(List.of("java", "-XstartOnFirstThread", "-Djava.awt.headless=true",
                "-D" + Launcher.CHILD_PROPERTY + "=true", "-Xmx2g", "-cp", "classes", "com.larsons.game.Main"), ide);
    }

    @Test
    void whatTheCommandLineGaveWins() {
        List<String> given = List.of("-Xmx3g", "-XX:+UseG1GC", "-Dlarsons.gpu=off", "-Dlarsons.sprites.scale=0.5",
                "-agentlib:jdwp=transport=dt_socket", "-XstartOnFirstThread");
        List<String> c = jarCommand(WINDOWS, given, true);
        assertFalse(c.contains("-XX:MaxRAMPercentage=60"));
        assertFalse(c.contains("-XX:+UseZGC"));
        assertFalse(c.contains("-Dlarsons.gpu=required"));
        assertTrue(c.containsAll(List.of("-Xmx3g", "-XX:+UseG1GC", "-Dlarsons.gpu=off", "-Dlarsons.sprites.scale=0.5")));
        assertTrue(c.stream().noneMatch(a -> a.startsWith("-agentlib:jdwp")), "a debugger stays with the first JVM");
        assertFalse(c.contains("-XstartOnFirstThread"), "not on Windows, even when given");
    }

    @Test
    void aMachineWith8GbGetsASmallerHeapThatGivesMemoryBack() {
        Launcher.Host air = new Launcher.Host("Mac OS X", "aarch64", 21, 8L << 30);
        List<String> c = jarCommand(air, List.of(), true);
        assertTrue(c.contains("-XX:MaxRAMPercentage=50"));
        assertFalse(c.contains("-XX:MaxRAMPercentage=60"));
        assertTrue(c.contains("-XX:SoftMaxHeapSize=" + (8L * 1024 * 3 / 10) + "m"), c.toString());
        assertTrue(c.contains("-XX:ZUncommitDelay=30"));
        assertTrue(c.contains("-XX:+UseZGC"));
        // 16 GB: as before
        List<String> big = jarCommand(new Launcher.Host("Mac OS X", "aarch64", 21, 16L << 30), List.of(), true);
        assertTrue(big.contains("-XX:MaxRAMPercentage=60"));
        assertTrue(big.stream().noneMatch(a -> a.startsWith("-XX:SoftMaxHeapSize")));
        // a heap given on the command line wins
        assertTrue(jarCommand(air, List.of("-Xmx3g"), true).stream().noneMatch(a -> a.startsWith("-XX:MaxRAM")
                || a.startsWith("-XX:SoftMax")));
        // without ZGC (Windows on ARM): only the smaller heap
        List<String> arm = jarCommand(new Launcher.Host("Windows 11", "aarch64", 21, 8L << 30), List.of(), true);
        assertTrue(arm.contains("-XX:MaxRAMPercentage=50"));
        assertTrue(arm.stream().noneMatch(a -> a.startsWith("-XX:SoftMax")));
    }

    @Test
    void zgcOnlyWhereItRunsAndItsGenerationalFlagOnlyWhereItIsOne() {
        assertFalse(jarCommand(WINDOWS_ARM, List.of(), true).contains("-XX:+UseZGC"));
        List<String> c = jarCommand(LINUX_25, List.of(), true);
        assertTrue(c.contains("-XX:+UseZGC"));
        assertFalse(c.contains("-XX:+ZGenerational"), "the default from 23 on");
    }

    @Test
    void thePlainRunLeavesTheCollectorAndHeapToTheJvm() {
        List<String> c = jarCommand(WINDOWS, List.of("-XX:+UseZGC"), false);
        assertFalse(c.contains("-XX:+UseZGC"));
        assertFalse(c.contains("-XX:MaxRAMPercentage=60"));
        assertTrue(c.contains("-Dlarsons.gpu=required"));
    }

    @Test
    void theDiscreteGpuHintIsLinuxOnlyAndNeverOverridesTheUsers() {
        assertTrue(Launcher.gpuEnvironment(WINDOWS, Map.of(), true).isEmpty());
        assertEquals(Map.of("DRI_PRIME", "1"), Launcher.gpuEnvironment(LINUX_25, Map.of(), false));
        assertEquals(Map.of("__NV_PRIME_RENDER_OFFLOAD", "1", "__GLX_VENDOR_LIBRARY_NAME", "nvidia"),
                Launcher.gpuEnvironment(LINUX_25, Map.of("DRI_PRIME", "0"), true));
    }
}
