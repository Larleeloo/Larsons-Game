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

    @Test
    void aDisplaySetBelowWhatItCanDoIsSaidAndWhatToDoAboutIt() {
        FramePacer.Plan plan = FramePacer.plan(120, true, 60);
        String d = FramePacer.describe(plan, 120, 60, 144);
        assertTrue(d.contains("set to 60 Hz but can do 144"), d);
        assertTrue(FramePacer.held(120, 60, 144));
        assertTrue(FramePacer.held(0, 60, 144), "uncapped: the display's fastest is the aim");
        assertFalse(FramePacer.held(120, 60, 60), "a 60 Hz display is not held back");
        assertFalse(FramePacer.held(120, 120, 144), "at the target already");
        assertFalse(FramePacer.held(120, 0, 144), "an unknown rate says nothing");
        assertEquals("vsync 60 Hz (the display is below 120)", FramePacer.describe(plan, 120, 60, 60));
        assertEquals("vsync 120 Hz", FramePacer.describe(FramePacer.plan(120, true, 120), 120, 120, 144));
        String advice = FramePacer.advice(60, 144);
        assertTrue(advice.contains("F11") && advice.contains("vsync off"), advice);
    }
}
