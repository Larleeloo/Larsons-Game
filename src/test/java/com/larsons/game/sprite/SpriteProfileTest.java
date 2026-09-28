package com.larsons.game.sprite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SpriteProfileTest {

    @Test
    void feetAreBelowThePivotByItsHeightTimesTheCosineOfTheRenderAngle() {
        SpriteProfile p = SpriteProfile.defaults();
        assertEquals(0.875, p.anchor(Elevation.SIDE)[1], 1e-9);          // 0.5 + 0.9/2.4
        assertEquals(0.5 + 0.9 * Math.cos(Math.PI / 4) / 2.4, p.anchor(Elevation.MIDDLE)[1], 1e-9);
        assertEquals(0.5, p.anchor(Elevation.TOP)[1], 1e-9);             // straight down: centre
        assertEquals(0.5, p.anchor(Elevation.SIDE)[0], 1e-9);
    }

    @Test
    void profileJsonOverridesOnlyWhatItSays(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("profile.json"), """
                { "frameWidth": 256, "frameHeight": 256, "stateFps": { "idle": 12 },
                  "anchors": { "top": [0.5, 0.6] } }
                """);
        SpriteProfile p = SpriteProfile.load(dir, SpriteProfile.defaults());
        assertEquals(256, p.frameWidth());
        assertEquals(2.4, p.frameWorldSize(), 1e-9);
        assertEquals(12, p.fps(AnimState.IDLE), 1e-9);
        assertEquals(30, p.fps(AnimState.WALK), 1e-9);
        assertEquals(0.6, p.anchor(Elevation.TOP)[1], 1e-9);
    }

    @Test
    void wardrobeRoundTrips(@TempDir Path dir) {
        Wardrobe w = new Wardrobe();
        w.set(Slot.HAT, "red_cap");
        w.set(Slot.CARRY_RIGHT, "sword");
        w.save(dir.resolve("w.json"));
        Wardrobe back = Wardrobe.load(dir.resolve("w.json"));
        assertEquals("red_cap", back.get(Slot.HAT));
        assertTrue(back.wearing(Slot.CARRY_RIGHT, "sword"));
        assertNull(back.get(Slot.BODY));
    }
}
