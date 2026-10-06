package com.larsons.game.sprite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Objects' sprite sheets: their names, their layers' order, mirrored twins, and the lit smoke. */
class ObjectSpritesTest {

    @Test
    void sheetNamesTakeAnyStateName() {
        assertEquals("open_middle_se", ObjectSprites.sheetKey("open_middle_se.png"));
        assertEquals("smoke_swirl_top_n", ObjectSprites.sheetKey("Smoke_Swirl_top_n.PNG"));
        assertNull(ObjectSprites.sheetKey("open_middle.png"));
        assertNull(ObjectSprites.sheetKey("open_up_se.png"));
        assertNull(ObjectSprites.sheetKey("profile.json"));
    }

    @Test
    void anObjectsLayersAndTheirSheets(@TempDir Path sprites) throws IOException {
        Path chest = sprites.resolve("objects/box");
        for (String f : List.of("lid/open_side_e.png", "lid/idle_side_s.png", "fog_back/swirl_side_s.png",
                "fog_front/swirl_side_s.png")) {
            Files.createDirectories(chest.resolve(f).getParent());
            Files.write(chest.resolve(f), new byte[]{0});
        }
        Files.writeString(chest.resolve("profile.json"), "{\"frameWidth\": 128, \"fps\": 24, \"pivotHeight\": 0.6}");
        ObjectSprites objects = new ObjectSprites(sprites);
        assertEquals(List.of("box"), objects.names());
        assertEquals(24, objects.profile("box").fps(), 1e-9);
        assertEquals(List.of("fog_back", "lid", "fog_front"), ObjectStack.drawOrder(objects.entry("box")),
                "behind, the object, in front");
        SpriteLibrary.Resolved r = objects.resolve("box", "lid", "open", Elevation.SIDE, Facing.EAST);
        assertNotNull(r);
        assertFalse(r.mirrored());
        SpriteLibrary.Resolved twin = objects.resolve("box", "lid", "open", Elevation.SIDE, Facing.WEST);
        assertNotNull(twin, "a missing west view borrows the east one");
        assertTrue(twin.mirrored());
        assertNull(objects.resolve("box", "lid", "open", Elevation.TOP, Facing.EAST));
        assertNull(objects.resolve("nothing", "lid", "open", Elevation.SIDE, Facing.EAST));
    }

    @Test
    void theRepositorysChestHasEveryViewOfEveryState() {
        ObjectSprites objects = new ObjectSprites(Path.of("assets", "sprites"));
        ObjectSprites.Entry e = objects.entry("ornate_chest");
        assertNotNull(e, "assets/sprites/objects/ornate_chest");
        assertEquals(List.of("smoke_back", "chest", "smoke_front"), ObjectStack.drawOrder(e));
        for (Elevation el : Elevation.values()) {
            for (Facing f : Facing.values()) {
                for (String s : List.of("idle", "open", "opened", "close")) {
                    SpriteLibrary.Resolved r = objects.resolve("ornate_chest", "chest", s, el, f);
                    assertNotNull(r, s + " " + el + " " + f);
                    assertFalse(r.mirrored(), "all 24 views are drawn");
                }
                assertNotNull(objects.resolve("ornate_chest", "smoke_back", "swirl", el, f));
                assertNotNull(objects.resolve("ornate_chest", "smoke_front", "swirl", el, f));
            }
        }
        assertTrue(e.profile().pixelArt());
        assertEquals(128, e.profile().frameWidth());
    }

    @Test
    void framesLoopOrHold() {
        assertEquals(3, ObjectStack.frameAt(0.13, 24, 48, true));
        assertEquals(2, ObjectStack.frameAt(2.0 + 0.1, 24, 48, true), "48 frames at 24 fps loop every 2 s");
        assertEquals(19, ObjectStack.frameAt(5, 24, 20, false), "a one-shot holds its last frame");
    }

    @Test
    void lightWarmsAPaletteAndKeepsItsAlpha() {
        int[] smoke = {0x00000000, 0xE08A66C8, 0xFF3A2068};
        assertArrayEquals(smoke, ObjectStack.lit(smoke, 0));
        int[] lit = ObjectStack.lit(smoke, 1);
        assertEquals(0xE0, lit[1] >>> 24);
        assertTrue(((lit[1] >> 16) & 255) > 0x8A, "redder");
        assertTrue((lit[1] & 255) < 0xC8, "less blue");
    }
}
