package com.larsons.game.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Full and Lite graphics: what each puts into the settings, and telling them from a custom mix. */
class GraphicsProfileTest {

    @Test
    void aFirstStartIsDrawnAsTheFullGraphics() {
        Settings s = Settings.defaults();
        assertTrue(GraphicsProfile.FULL.matches(s, 1), "the settings as they come are the Full graphics");
        assertTrue(GraphicsProfile.FULL.matches(s, 2));
        assertFalse(GraphicsProfile.LITE.matches(s, 2));
    }

    @Test
    void liteTurnsDownWhatCostsALowEndMachineTheMost() {
        Settings s = Settings.defaults();
        GraphicsProfile.LITE.apply(s, 2);
        assertEquals("OFF", s.shadows);
        assertFalse(s.vibrant);
        assertFalse(s.lightRays);
        assertEquals(60, s.savedFps());
        assertEquals(0, s.savedMsaa());
        assertEquals(0.5, s.renderScale, 1e-9, "one pixel per point on a Retina screen");
        assertEquals(0.7, s.viewDistance, 1e-9);
        assertEquals(0.5, s.wildlife, 1e-9);
        assertEquals(768L << 20, s.vramBytes);
        assertTrue(GraphicsProfile.LITE.matches(s, 2));
        assertFalse(GraphicsProfile.FULL.matches(s, 2));
    }

    @Test
    void onAnOrdinaryScreenLiteDrawsTheWorldAtFullSize() {
        Settings s = Settings.defaults();
        GraphicsProfile.LITE.apply(s, 1);
        assertEquals(1, s.renderScale, 1e-9, "halving a screen of one pixel a point would blur the pixel art");
        assertEquals(0.5, GraphicsProfile.LITE.renderScale(2));
        assertEquals(1, GraphicsProfile.FULL.renderScale(2));
    }

    @Test
    void fullPutsEverythingBackVibrantAtItsUsualStrength() {
        Settings s = Settings.defaults();
        s.vibrantStrength = 0;
        GraphicsProfile.LITE.apply(s, 2);
        GraphicsProfile.FULL.apply(s, 2);
        assertTrue(s.vibrant);
        assertTrue(s.vibrantStrength > 0);
        assertEquals(4, s.savedMsaa());
        assertEquals(1, s.renderScale, 1e-9);
        assertTrue(GraphicsProfile.FULL.matches(s, 2));
    }

    @Test
    void oneSettingChangedByHandIsNeitherProfile() {
        Settings s = Settings.defaults();
        GraphicsProfile.LITE.apply(s, 2);
        s.viewDistance = 1;
        assertFalse(GraphicsProfile.LITE.matches(s, 2));
        assertFalse(GraphicsProfile.FULL.matches(s, 2));
    }

    @Test
    void graphicsGivenForOneRunAreNotSavedButWhatThePlayerChangesIs() {
        Settings s = Settings.defaults();
        s.force(GraphicsProfile.LITE, 2);               // -Dlarsons.graphics=lite
        assertFalse(s.vibrant, "drawn as Lite this run");
        assertEquals("MEDIUM", s.toJson().get("shadows"), "saved as it was");
        assertEquals(4, s.toJson().get("msaa"));
        assertEquals(1.0, s.toJson().get("renderScale"));
        s.viewDistance = 0.5;                            // the player, in Settings
        assertEquals(0.5, s.toJson().get("viewDistance"));
        s.keepGraphics();                                // the player picks Lite in Settings
        assertEquals("OFF", s.toJson().get("shadows"));
    }

    @Test
    void theSavedNameIsReadBackAndAnythingElseIsTheFallback() {
        assertEquals(GraphicsProfile.LITE, GraphicsProfile.of("lite", GraphicsProfile.FULL));
        assertEquals(GraphicsProfile.LITE, GraphicsProfile.of(" LITE ", null));
        assertEquals(GraphicsProfile.FULL, GraphicsProfile.of("full", GraphicsProfile.LITE));
        assertNull(GraphicsProfile.of("auto", null));
        assertEquals(GraphicsProfile.FULL, GraphicsProfile.of(null, GraphicsProfile.FULL));
        assertEquals("lite", GraphicsProfile.LITE.key());
    }
}
