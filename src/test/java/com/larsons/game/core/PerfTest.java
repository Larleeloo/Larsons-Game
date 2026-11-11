package com.larsons.game.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The performance overlay's numbers: counted through a frame, shown for the frame after. */
class PerfTest {

    @Test
    void aFramesDrawCallsAndCountsAreShownOnceItIsOver() {
        Perf.reset();
        Perf.drew(120);
        Perf.drew(0);
        Perf.drew(30);
        Perf.count("folk", 12);
        Perf.count("folk", 3);
        Perf.count("scenery", 900);
        assertEquals(0, Perf.drawCalls(), "the frame is not over");
        Perf.endFrame(2_000_000, 6_000_000, 1 / 60.0, false);
        assertEquals(3, Perf.drawCalls());
        assertEquals(150, Perf.quads());
        assertEquals(15, Perf.counts().get("folk"));
        assertEquals(900, Perf.counts().get("scenery"));
        assertTrue(Perf.updateMs() > 0 && Perf.updateMs() <= 2);
        assertTrue(Perf.renderMs() > Perf.updateMs());
        List<String> lines = Perf.lines();
        assertTrue(lines.get(0).startsWith("update "));
        assertTrue(lines.get(1).startsWith("draw "));
        assertEquals("3 draw calls, 150 quads", lines.get(2));
        assertEquals("15 folk, 900 scenery", lines.get(3));
        // the next frame starts from nothing
        Perf.endFrame(1_000_000, 1_000_000, 1 / 60.0, false);
        assertEquals(0, Perf.drawCalls());
        assertTrue(Perf.counts().isEmpty());
        assertEquals(3, Perf.lines().size());
        // the times follow the frames: steady frames settle on what they take
        for (int i = 0; i < 400; i++) Perf.endFrame(4_000_000, 9_000_000, 1 / 60.0, false);
        assertEquals(4, Perf.updateMs(), 0.05);
        assertEquals(9, Perf.renderMs(), 0.05);
        Perf.reset();
    }
}
