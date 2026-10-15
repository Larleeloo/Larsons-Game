package com.larsons.game.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** How frames are paced for each display - no window needed. */
class FramePacerTest {

    private static FramePacer.Plan plan(int target, boolean vsync, int refresh) {
        return FramePacer.plan(target, vsync, refresh);
    }

    @Test
    void vsyncIsTheCadenceWhereTheDisplayRefreshesAtTheTarget() {
        assertEquals(new FramePacer.Plan(1, 0), plan(120, true, 120), "120 Hz: every refresh");
        assertEquals(new FramePacer.Plan(1, 0), plan(120, true, 119), "119.88 Hz reports as 119");
        assertEquals(new FramePacer.Plan(2, 0), plan(120, true, 240), "240 Hz: every other refresh");
        assertEquals(new FramePacer.Plan(3, 0), plan(120, true, 360));
    }

    @Test
    void aDisplayThatIsNotAMultipleOfTheTargetIsCappedAtIt() {
        assertEquals(new FramePacer.Plan(1, 120), plan(120, true, 144));
        assertEquals(new FramePacer.Plan(1, 120), plan(120, true, 165));
        assertEquals(new FramePacer.Plan(1, 120), plan(120, true, 0), "an unknown refresh rate is capped too");
    }

    @Test
    void aSlowerDisplayPacesTheGameItself() {
        assertEquals(new FramePacer.Plan(1, 0), plan(120, true, 60));
        assertEquals(new FramePacer.Plan(1, 0), plan(120, true, 75));
    }

    @Test
    void withoutVsyncTheGameKeepsItsOwnTime() {
        assertEquals(new FramePacer.Plan(0, 120), plan(120, false, 60));
        assertEquals(new FramePacer.Plan(0, 120), plan(120, false, 144));
        assertEquals(new FramePacer.Plan(0, 0), plan(0, false, 144), "uncapped");
        assertEquals(new FramePacer.Plan(1, 0), plan(0, true, 144), "uncapped with vsync: the display's rate");
    }
}
