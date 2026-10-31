package com.larsons.game.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What loads as the game starts, and what the loading screen is told:
 * the steps in order, the screen drawn as they start and report (but never
 * more than 30 times a second), background work watched until it is done,
 * a failure held on screen, a closed window let go of, and the bar weighted
 * by last time's timings. No GPU: a fake clock and a fake screen.
 */
class BootTest {

    /** A clock moved by hand, in milliseconds. */
    private static final class Clock {
        long ns;

        void ms(double ms) {
            ns += (long) (ms * 1_000_000);
        }
    }

    /** A screen that counts its frames, and what was on it at each. */
    private static final class Screen implements Boot.Display {
        final List<String> frames = new ArrayList<>();
        Boot boot;
        Boot.Status failed;
        boolean closeOnNextFrame;

        @Override
        public void frame() {
            if (closeOnNextFrame) throw new Boot.Closed();
            Boot.Status s = boot.current();
            frames.add(s == null ? "-" : s.step.name() + ":" + s.detail());
        }

        @Override
        public void failed(Boot.Status step, Throwable error) {
            failed = step;
        }
    }

    private final Clock clock = new Clock();

    private Boot boot(Map<Boot.Step, Double> last, Screen screen) {
        Boot b = new Boot(last, () -> clock.ns);
        if (screen != null) {
            screen.boot = b;
            b.setDisplay(screen);
        }
        return b;
    }

    @Test
    void aStepRunsTellsTheScreenWhatItIsOnAndKeepsWhatItFound() {
        Screen screen = new Screen();
        Boot b = boot(Map.of(), screen);
        String made = b.run(Boot.Step.SPRITE_INDEX, r -> {
            clock.ms(50);
            r.detail("sprites/body/feminine");
            r.progress(1, 2, "folders");
            clock.ms(50);
            r.summary("6 sheets");
            return "library";
        });
        assertEquals("library", made);
        Boot.Status s = b.status(Boot.Step.SPRITE_INDEX);
        assertEquals(Boot.Phase.DONE, s.phase());
        assertEquals("6 sheets", s.summary());
        assertEquals(0.1, s.seconds(), 1e-9);
        assertEquals("folders", s.unit());
        // a frame as it started (it is a long step), and one as it reported, 50 ms later
        assertEquals(List.of("SPRITE_INDEX:", "SPRITE_INDEX:sprites/body/feminine"), screen.frames);
        assertNull(b.current());
    }

    @Test
    void theScreenIsDrawnNoMoreThanThirtyTimesASecond() {
        Screen screen = new Screen();
        Boot b = boot(Map.of(), screen);
        b.run(Boot.Step.SPRITE_INDEX, r -> {
            for (int i = 0; i < 1000; i++) {   // a folder a millisecond, for a second
                clock.ms(1);
                r.progress(i, 1000);
            }
            return null;
        });
        assertTrue(screen.frames.size() <= Boot.FPS + 1, screen.frames.size() + " frames");
        assertTrue(screen.frames.size() >= Boot.FPS - 1, screen.frames.size() + " frames");
    }

    @Test
    void aQuickStepIsNotDrawnJustForStartingButALongOneIs() {
        Screen screen = new Screen();
        Boot b = boot(Map.of(Boot.Step.TERRAIN, 0.002, Boot.Step.PLANTING, 0.5), screen);
        b.run(Boot.Step.SPRITE_INDEX, r -> null);   // never timed, guessed at seconds: drawn
        b.run(Boot.Step.TERRAIN, r -> null);        // two milliseconds last time: not worth a frame
        b.run(Boot.Step.PLANTING, r -> null);       // half a second: on screen while it works
        assertEquals(List.of("SPRITE_INDEX:", "PLANTING:"), screen.frames);
    }

    @Test
    void backgroundWorkIsWatchedUntilItSaysItIsDone() {
        Boot b = boot(Map.of(), null);
        int[] decoded = {0};
        b.watch(Boot.Step.SOUND_EFFECTS, r -> {
            r.progress(decoded[0], 3, "files");
            return decoded[0] >= 3;
        });
        Boot.Status s = b.status(Boot.Step.SOUND_EFFECTS);
        assertEquals(Boot.Phase.RUNNING, s.phase());
        assertEquals(List.of(s), b.alsoRunning(null));
        assertEquals(List.of(), b.alsoRunning(s));
        decoded[0] = 2;
        b.poll();
        assertEquals(2, s.done());
        assertEquals(Boot.Phase.RUNNING, s.phase());
        decoded[0] = 3;
        b.poll();
        assertEquals(Boot.Phase.DONE, s.phase());
    }

    @Test
    void theBootIsFinishedOnlyOnceEveryStepIs() {
        Boot b = boot(Map.of(), null);
        for (Boot.Step s : Boot.Step.values()) {
            assertFalse(b.finished());
            if (s.background) b.watch(s, r -> true);
            else b.run(s, r -> null);
        }
        assertTrue(b.poll());
        assertEquals(Boot.Step.values().length, b.doneCount());
        assertEquals(1, b.fraction(), 1e-9);
    }

    @Test
    void backgroundWorkThatBreaksIsLetGoNotFatal() {
        Boot b = boot(Map.of(), null);
        b.watch(Boot.Step.MUSIC, r -> {
            throw new IllegalStateException("no such song");
        });
        assertEquals(Boot.Phase.DONE, b.status(Boot.Step.MUSIC).phase());
        assertEquals("no such song", b.status(Boot.Step.MUSIC).error());
        assertTrue(b.log().get(b.log().size() - 1).contains("no such song"));
    }

    @Test
    void aStepThatFailsIsHeldOnScreenAndThenThrown() {
        Screen screen = new Screen();
        Boot b = boot(Map.of(), screen);
        Boot.Failed f = assertThrows(Boot.Failed.class, () -> b.run(Boot.Step.ITEMS, r -> {
            throw new java.io.IOException("tiers.json: unexpected end of input");
        }));
        assertEquals(Boot.Step.ITEMS, f.step);
        assertInstanceOf(java.io.IOException.class, f.getCause());
        Boot.Status s = b.status(Boot.Step.ITEMS);
        assertEquals(Boot.Phase.FAILED, s.phase());
        assertEquals("tiers.json: unexpected end of input", s.error());
        assertSame(s, screen.failed);
        assertSame(s, b.failure());
    }

    @Test
    void closingTheWindowWhileLoadingIsNotAFailure() {
        Screen screen = new Screen();
        Boot b = boot(Map.of(), screen);
        screen.closeOnNextFrame = true;
        assertThrows(Boot.Closed.class, () -> b.run(Boot.Step.SPRITE_INDEX, r -> null));
        assertNull(screen.failed);
        assertNull(b.failure());
    }

    @Test
    void theBarIsWeightedByHowLongEachStepTookLastTime() {
        Map<Boot.Step, Double> last = new EnumMap<>(Boot.Step.class);
        for (Boot.Step s : Boot.Step.values()) last.put(s, 0.1);
        last.put(Boot.Step.SPRITE_INDEX, 8.2);       // 19 steps: 1.8 s of the rest, 8.2 s of this one
        Boot b = boot(last, null);
        b.run(Boot.Step.SPRITE_INDEX, r -> {
            r.progress(1, 2);
            assertEquals(4.1 / 10, b.fraction(), 1e-9, "halfway through the long step is 41%");
            return null;
        });
        assertEquals(8.2 / 10, b.fraction(), 1e-9);
        b.run(Boot.Step.TERRAIN, r -> null);
        assertEquals(8.3 / 10, b.fraction(), 1e-9);
    }

    @Test
    void aStepThatCannotCountMovesTheBarByItsTimeAgainstLastTimesButNeverPastNinetyPercent() {
        Boot b = boot(Map.of(Boot.Step.MUSIC, 2.0), null);
        b.watch(Boot.Step.MUSIC, r -> false);
        Boot.Status s = b.status(Boot.Step.MUSIC);
        clock.ms(1000);
        assertEquals(0.5, s.fraction(), 1e-9);
        clock.ms(5000);
        assertEquals(0.9, s.fraction(), 1e-9);
    }

    @Test
    void stepsDoneBeforeTheScreenExistedAreShownDoneWithTheirTimes() {
        Boot b = boot(Map.of(), null);
        clock.ms(500);
        b.done(Boot.Step.WINDOW, 300_000_000L, "llvmpipe, OpenGL 4.5");
        Boot.Status s = b.status(Boot.Step.WINDOW);
        assertEquals(Boot.Phase.DONE, s.phase());
        assertEquals(0.3, s.seconds(), 1e-9);
        assertEquals("llvmpipe, OpenGL 4.5", s.summary());
    }

    @Test
    void theListOfWhatJustLoadedKeepsTheLatestLines() {
        Boot b = boot(Map.of(), null);
        b.run(Boot.Step.SCENERY_SHEETS, r -> {
            for (int i = 0; i < Boot.LOG_LINES + 10; i++) r.log("sheet " + i);
            return null;
        });
        List<String> log = b.log();
        assertEquals(Boot.LOG_LINES, log.size());
        assertEquals("sheet " + (Boot.LOG_LINES + 9), log.get(log.size() - 1));
    }

    @Test
    void timingsAreKeptForNextTime(@TempDir Path dir) throws Exception {
        Boot b = boot(Map.of(), null);
        b.run(Boot.Step.SPRITE_INDEX, r -> {
            clock.ms(6189);
            return null;
        });
        b.run(Boot.Step.TERRAIN, r -> {
            clock.ms(31);
            return null;
        });
        Path file = dir.resolve("config").resolve("boot.json");
        Boot.save(file, b.timings());
        Map<Boot.Step, Double> read = Boot.load(file);
        assertEquals(2, read.size(), "only the steps that ran");
        assertEquals(6.189, read.get(Boot.Step.SPRITE_INDEX), 1e-9);
        assertEquals(0.031, read.get(Boot.Step.TERRAIN), 1e-9);
        assertEquals(6.189, boot(read, null).lastSeconds(Boot.Step.SPRITE_INDEX), 1e-9);
        assertEquals(-1, boot(read, null).lastSeconds(Boot.Step.MUSIC), 1e-9);

        Files.writeString(file, "{ not json");
        assertTrue(Boot.load(file).isEmpty(), "a file that cannot be read is no timings, not a crash");
        assertTrue(Boot.load(dir.resolve("missing.json")).isEmpty());
    }

    @Test
    void theReportNamesTheSlowestSteps() {
        Boot b = boot(Map.of(), null);
        b.run(Boot.Step.SPRITE_INDEX, r -> {
            clock.ms(2000);
            return null;
        });
        b.run(Boot.Step.TERRAIN, r -> {
            clock.ms(10);
            return null;
        });
        String report = b.report();
        assertTrue(report.startsWith("ready in 2.01 s - Indexing the character sprite sheets 2.00 s"), report);
    }

    @Test
    void everyStepHasATitleAndTheGroupsComeInOrder() {
        Boot.Group last = Boot.Group.values()[0];
        for (Boot.Step s : Boot.Step.values()) {
            assertFalse(s.title.isBlank());
            assertTrue(s.group.ordinal() >= last.ordinal(), s + " is listed under an earlier heading");
            last = s.group;
        }
    }
}
