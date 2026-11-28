package com.larsons.game.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The report a player sends when the game freezes or crashes: why, the machine, and where every thread is. */
class WatchdogTest {

    @Test
    void aReportSaysWhyTheMachineTheErrorAndEveryThreadsWholeStack() {
        String text = Watchdog.text("The game has drawn no frame for 12 seconds", new IllegalStateException("boom"));
        assertTrue(text.startsWith("Larson's Game - The game has drawn no frame for 12 seconds"));
        assertTrue(text.contains("Java " + System.getProperty("java.version")));
        assertTrue(text.contains("What went wrong:\njava.lang.IllegalStateException: boom"));
        assertTrue(text.contains("Threads:\n"));
        assertTrue(text.contains('"' + Thread.currentThread().getName() + '"'), "this thread is in it");
        assertTrue(text.contains("WatchdogTest.aReportSaysWhyTheMachineTheErrorAndEveryThreadsWholeStack"),
                "with its stack, frame by frame");
    }

    @Test
    void aHangAndACrashAreFilesOfTheirOwnInTheFolder(@TempDir Path logs) throws Exception {
        Path hang = Watchdog.report(logs.resolve("logs"), "frozen", null);
        assertNotNull(hang);
        assertTrue(hang.getFileName().toString().matches("hang-\\d{8}-\\d{6}\\.txt"));
        assertTrue(Files.readString(hang).contains("frozen"));
        Path crash = Watchdog.report(logs.resolve("logs"), "crashed", new RuntimeException("x"));
        assertTrue(crash.getFileName().toString().startsWith("crash-"));
    }
}
