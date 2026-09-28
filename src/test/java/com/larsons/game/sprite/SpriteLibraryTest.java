package com.larsons.game.sprite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The folder index and the lookup rules — no GPU involved. */
class SpriteLibraryTest {

    @TempDir
    Path root;
    SpriteLibrary lib;

    @AfterEach
    void close() {
        if (lib != null) lib.close();
    }

    private void png(String rel) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        ImageIO.write(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB), "png", p.toFile());
    }

    @Test
    void indexesItemsPerSlot() throws Exception {
        png("body/hero/walk_side_e.png");
        png("body/hero/Idle-45-S.png");                // non-canonical, still read
        png("hat/straw_boater/walk_side_e.png");
        png("hat/straw_boater/walk_side_e_0001.png");  // a loose frame: needs importing
        lib = new SpriteLibrary(root, 1L << 30, 1.0);
        assertEquals(java.util.List.of("hero"), lib.items(Slot.BODY));
        assertEquals(java.util.List.of("straw_boater"), lib.items(Slot.HAT));
        assertTrue(lib.items(Slot.SHIRT).isEmpty());
        assertEquals(2, lib.entry(Slot.BODY, "hero").count());
        assertNotNull(lib.resolve(Slot.BODY, "hero", AnimState.IDLE, Elevation.MIDDLE, Facing.SOUTH));
        assertEquals(1, lib.entry(Slot.HAT, "straw_boater").skipped().size());
    }

    @Test
    void aMissingWestViewBorrowsTheEastOneMirrored() throws Exception {
        png("body/hero/walk_side_e.png");
        lib = new SpriteLibrary(root, 1L << 30, 1.0);
        SpriteLibrary.Resolved east = lib.resolve(Slot.BODY, "hero", AnimState.WALK, Elevation.SIDE, Facing.EAST);
        SpriteLibrary.Resolved west = lib.resolve(Slot.BODY, "hero", AnimState.WALK, Elevation.SIDE, Facing.WEST);
        assertFalse(east.mirrored());
        assertTrue(west.mirrored());
        assertEquals(east.file(), west.file());
        assertNull(lib.resolve(Slot.BODY, "hero", AnimState.WALK, Elevation.SIDE, Facing.NORTH),
                "north has no twin");
        assertNull(lib.resolve(Slot.BODY, "hero", AnimState.RUN, Elevation.SIDE, Facing.EAST));
        assertNull(lib.resolve(Slot.BODY, null, AnimState.WALK, Elevation.SIDE, Facing.EAST),
                "no item → fallback");
    }

    @Test
    void rescanPicksUpNewFolders() throws Exception {
        lib = new SpriteLibrary(root, 1L << 30, 1.0);
        int before = lib.generation();
        assertTrue(lib.items(Slot.SHIRT).isEmpty());
        png("shirt/tunic/idle_top_n.png");
        lib.rescan();
        assertEquals(java.util.List.of("tunic"), lib.items(Slot.SHIRT));
        assertTrue(lib.generation() > before);
    }
}
