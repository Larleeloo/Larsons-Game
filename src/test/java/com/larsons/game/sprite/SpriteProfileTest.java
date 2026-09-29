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
    void pixelArtIsSmallFramesOrWhatTheProfileSays(@TempDir Path dir) throws Exception {
        assertFalse(SpriteProfile.defaults().pixelArt(), "512-pixel renders are filtered");
        assertTrue(SpriteProfile.defaults().withFrameSize(32, 32).pixelArt(), "the fallback is pixel art");
        Files.writeString(dir.resolve("profile.json"), """
                { "frameWidth": 128, "frameHeight": 128, "pixelArt": true }
                """);
        SpriteProfile p = SpriteProfile.load(dir, SpriteProfile.defaults());
        assertTrue(p.pixelArt(), "128-pixel pixel art, as its profile says");
        assertEquals(true, p.toJson().get("pixelArt"));
        assertTrue(p.copy().pixelArt());
        Files.writeString(dir.resolve("profile.json"), """
                { "frameWidth": 64, "frameHeight": 64, "pixelArt": false }
                """);
        assertFalse(SpriteProfile.load(dir, SpriteProfile.defaults()).pixelArt());
        assertFalse(SpriteProfile.defaults().toJson().containsKey("pixelArt"));
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
        assertEquals(Wardrobe.Style.RENDERED, back.style());
    }

    @Test
    void theStyleIsSavedAndTheItemsKeepTheirNames(@TempDir Path dir) {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        w.setStyle(Wardrobe.Style.PIXEL_64);
        w.save(dir.resolve("w.json"));
        Wardrobe back = Wardrobe.load(dir.resolve("w.json"));
        assertEquals(Wardrobe.Style.PIXEL_64, back.style());
        assertTrue(back.wearing(Slot.CARRY_RIGHT, "sword"), "the sword in hand is still the sword");
        assertEquals(Wardrobe.Style.PIXEL_64, back.copy().style());
        assertEquals("sword_px128", Wardrobe.Style.PIXEL_128.folder("sword"));
        assertTrue(Wardrobe.Style.isVersion("straw_sun_hat_px64"));
        assertFalse(Wardrobe.Style.isVersion("straw_sun_hat"));
        assertEquals(Wardrobe.Style.PIXEL_128, Wardrobe.Style.byKey("px128"));
    }
}
