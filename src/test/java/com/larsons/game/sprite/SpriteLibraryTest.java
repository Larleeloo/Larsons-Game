package com.larsons.game.sprite;

import com.larsons.game.gfx.Texture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    // --- the memory budget -----------------------------------------------------------

    /** Bytes one of {@link #opaque}'s sheets takes on the GPU: 64 × 64, mipmapped. */
    static final long SHEET = Texture.bytesFor(64, 64, false);

    final List<String> uploads = new ArrayList<>();

    /** A one-frame 64 × 64 sheet with no transparent margin, so nothing is cropped. */
    private void opaque(String rel) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 64, 64);
        g.dispose();
        ImageIO.write(img, "png", p.toFile());
    }

    /** A library that "uploads" without GL, and remembers what it uploaded. */
    private SpriteLibrary budgeted(long budget) {
        return new SpriteLibrary(root, budget, 1.0, (layout, pixels, source) -> {
            pixels.free();
            uploads.add(source);
            BufferedImage a = layout.atlas();
            return new SheetTexture(layout, Texture.bytesFor(a.getWidth(), a.getHeight(), false), source);
        });
    }

    private SpriteLibrary.Resolved hero(AnimState state) {
        return lib.resolve(Slot.BODY, "hero", state, Elevation.SIDE, Facing.SOUTH);
    }

    /**
     * One game frame, the way {@link LayerStack} drives the library: draw
     * {@code onScreen}; once all of it is in, prefetch {@code ahead}; then let
     * the decoders finish and upload what they made.
     */
    private boolean frame(List<SpriteLibrary.Resolved> onScreen, List<SpriteLibrary.Resolved> ahead) {
        lib.beginFrame();
        boolean all = true;
        for (SpriteLibrary.Resolved r : onScreen) all &= lib.sheet(r) != null;
        if (all) for (SpriteLibrary.Resolved r : ahead) lib.prefetch(r);
        lib.awaitDecodes();
        lib.pump();
        return all;
    }

    private void heroSheets() throws Exception {
        for (AnimState s : AnimState.values()) opaque("body/hero/" + s.key() + "_side_s.png");
    }

    private List<SpriteLibrary.Resolved> heroAllBut(AnimState state) {
        List<SpriteLibrary.Resolved> out = new ArrayList<>();
        for (AnimState s : AnimState.values()) if (s != state) out.add(hero(s));
        return out;
    }

    @Test
    void prefetchFillsTheSpareRoomAndThenSettles() throws Exception {
        heroSheets();
        lib = budgeted(3 * SHEET + SHEET / 2); // room for what is on screen and two more
        List<SpriteLibrary.Resolved> onScreen = List.of(hero(AnimState.IDLE));
        List<SpriteLibrary.Resolved> ahead = heroAllBut(AnimState.IDLE);
        for (int i = 0; i < 10; i++) frame(onScreen, ahead);
        int settled = uploads.size();
        for (int i = 0; i < 50; i++) frame(onScreen, ahead);

        assertEquals(3, lib.residentCount(), "the idle sheet and two prefetched ones");
        assertTrue(lib.residentBytes() <= 3 * SHEET + SHEET / 2);
        assertEquals(0, lib.pendingCount(), "nothing left loading");
        assertEquals(settled, uploads.size(), "and nothing loaded again and again");
        assertTrue(settled <= 4, "at most one prefetch wasted while sizes were unknown: " + uploads);
    }

    @Test
    void aStackOverTheBudgetOnItsOwnStopsPrefetching() throws Exception {
        heroSheets();
        opaque("hat/cap/idle_side_s.png");
        lib = budgeted(SHEET + SHEET / 2); // less than the two sheets on screen
        List<SpriteLibrary.Resolved> onScreen = List.of(hero(AnimState.IDLE),
                lib.resolve(Slot.HAT, "cap", AnimState.IDLE, Elevation.SIDE, Facing.SOUTH));
        List<SpriteLibrary.Resolved> ahead = heroAllBut(AnimState.IDLE);
        for (int i = 0; i < 60; i++) frame(onScreen, ahead);

        assertEquals(2, lib.residentCount(), "what is on screen stays, over budget or not");
        assertEquals(0, lib.pendingCount());
        assertEquals(2, uploads.size(), "no prefetch was ever uploaded, let alone evicted and reloaded");
    }

    @Test
    void aPrefetchThatArrivesToNoRoomIsDropped() throws Exception {
        heroSheets();
        lib = budgeted(2 * SHEET);
        frame(List.of(hero(AnimState.IDLE)), List.of()); // idle is known: sizes are known
        // A sheet the camera wants is on its way when a prefetch that fits
        // right now is queued behind it; by the time the prefetch arrives the
        // other one has taken the room.
        lib.beginFrame();
        assertNotNull(lib.sheet(hero(AnimState.IDLE)));
        assertNull(lib.sheet(hero(AnimState.WALK)));
        lib.awaitDecodes();
        lib.prefetch(hero(AnimState.RUN));
        lib.awaitDecodes();
        lib.pump();

        assertEquals(2, lib.residentCount());
        assertFalse(uploads.stream().anyMatch(u -> u.contains("run_")), "run was dropped, not uploaded");
        assertEquals(0, lib.pendingCount());
        // And with idle and walk on screen it is not asked for again.
        int before = uploads.size();
        for (int i = 0; i < 20; i++) frame(List.of(hero(AnimState.IDLE), hero(AnimState.WALK)),
                List.of(hero(AnimState.RUN)));
        assertEquals(before, uploads.size());
        assertEquals(0, lib.pendingCount());
    }

    @Test
    void aPrefetchWantedOnScreenLoadsWhateverTheBudget() throws Exception {
        heroSheets();
        lib = budgeted(2 * SHEET);
        frame(List.of(hero(AnimState.IDLE)), List.of());
        lib.beginFrame();
        lib.sheet(hero(AnimState.IDLE));
        assertNull(lib.sheet(hero(AnimState.WALK)));    // takes the last of the room…
        lib.awaitDecodes();
        lib.prefetch(hero(AnimState.RUN));              // …queued while it still looked free…
        assertNull(lib.sheet(hero(AnimState.RUN)));     // …and then the camera wants it
        lib.awaitDecodes();
        lib.pump();

        assertTrue(uploads.stream().anyMatch(u -> u.contains("run_")), "run is on screen: it loads");
        assertEquals(3, lib.residentCount());
    }

    @Test
    void aStyleDrawsAnItemsVersionInItWhenThereIsOne() throws Exception {
        png("hat/cap/walk_side_e.png");
        png("hat/cap_px64/walk_side_e.png");
        png("body/hero/walk_side_e.png");
        lib = new SpriteLibrary(root, 1L << 30, 1.0);
        assertEquals("cap_px64", lib.styled(Slot.HAT, "cap", Wardrobe.Style.PIXEL_64));
        assertEquals("cap", lib.styled(Slot.HAT, "cap", Wardrobe.Style.RENDERED));
        assertEquals("hero", lib.styled(Slot.BODY, "hero", Wardrobe.Style.PIXEL_64),
                "no 64-pixel hero: the hero as it is");
        assertNull(lib.styled(Slot.BODY, null, Wardrobe.Style.PIXEL_64));
    }

    @Test
    void anItemThatIsOnlyPixelArtIsDrawnFromItsBiggerVersionInTheRenderedStyle() throws Exception {
        png("hat/beanie_px128/walk_side_e.png");
        png("hat/beanie_px64/walk_side_e.png");
        png("hat/beanie_px128_lh/walk_side_e.png");
        lib = new SpriteLibrary(root, 1L << 30, 1.0);
        assertEquals("beanie_px128", lib.styled(Slot.HAT, "beanie", Wardrobe.Style.RENDERED));
        assertEquals("beanie_px64", lib.styled(Slot.HAT, "beanie", Wardrobe.Style.PIXEL_64));
        assertEquals(new SpriteLibrary.Source(Slot.HAT, "beanie_px128_lh", false),
                lib.source(Slot.HAT, "beanie", Wardrobe.Style.RENDERED, true));
    }

    @Test
    void aBodyWithAFolderOfItsOwnHasItsItemsLookedUpThere() throws Exception {
        // assets/sprites/ (her body and items) and assets/sprites_masculine/ (his)
        png("sprites/body/feminine_px128/idle_side_s.png");
        png("sprites/hat/cap_px128/idle_side_s.png");
        png("sprites/hat/sun_hat_px128/idle_side_s.png");
        png("sprites/carry_right/sword_px128/idle_side_s.png");
        png("sprites_masculine/body/masculine_px128/idle_side_s.png");
        png("sprites_masculine/hat/cap_px128/idle_side_s.png");
        png("sprites_masculine/carry_right/sword_px128/idle_side_s.png");
        Files.writeString(root.resolve("sprites_notes.txt"), "not a folder");
        lib = new SpriteLibrary(root.resolve("sprites"), 1L << 30, 1.0);
        assertEquals(List.of("", "masculine"), lib.roots());
        assertEquals("masculine", lib.rootOf("masculine"));
        assertEquals("masculine", lib.rootOf("masculine_px128_lh"));
        assertEquals("", lib.rootOf("feminine"));
        assertEquals("", lib.rootOf(null));
        assertEquals(List.of("feminine_px128", "masculine_px128"), lib.items(Slot.BODY),
                "every folder's bodies are listed");
        assertEquals(lib.items(Slot.BODY), lib.items("masculine", Slot.BODY));
        assertEquals(List.of("cap_px128"), lib.items("masculine", Slot.HAT));
        assertEquals(List.of("cap_px128", "sun_hat_px128"), lib.items("feminine", Slot.HAT));

        // the same item names, each body's own sheets
        Path his = root.resolve("sprites_masculine/hat/cap_px128/idle_side_s.png");
        Path hers = root.resolve("sprites/hat/cap_px128/idle_side_s.png");
        SpriteLibrary.Source cap = lib.source("masculine", Slot.HAT, "cap", Wardrobe.Style.PIXEL_128, false);
        assertEquals(new SpriteLibrary.Source(Slot.HAT, "cap_px128", false, "masculine"), cap);
        assertEquals(his, lib.resolve(cap, AnimState.IDLE, Elevation.SIDE, Facing.SOUTH).file());
        assertEquals(hers, lib.resolve(lib.source("feminine", Slot.HAT, "cap", Wardrobe.Style.PIXEL_128, false),
                AnimState.IDLE, Elevation.SIDE, Facing.SOUTH).file());
        assertEquals(hers, lib.resolve(lib.source(Slot.HAT, "cap", Wardrobe.Style.PIXEL_128, false),
                AnimState.IDLE, Elevation.SIDE, Facing.SOUTH).file(), "no body: the default folder");
        // his body is found whichever body asks
        assertEquals(root.resolve("sprites_masculine/body/masculine_px128/idle_side_s.png"),
                lib.resolve(lib.source("feminine", Slot.BODY, "masculine", Wardrobe.Style.PIXEL_128, false),
                        AnimState.IDLE, Elevation.SIDE, Facing.SOUTH).file());
        assertNotNull(lib.entry(Slot.BODY, "masculine_px128"));
        // an item drawn only on her is not drawn on him
        assertNull(lib.resolve(lib.source("masculine", Slot.HAT, "sun_hat", Wardrobe.Style.PIXEL_128, false),
                AnimState.IDLE, Elevation.SIDE, Facing.SOUTH));
        assertEquals("sun_hat", lib.styled("masculine", Slot.HAT, "sun_hat", Wardrobe.Style.PIXEL_128));
        // the left hand's sword: his right-hand one, mirrored, from his folder
        SpriteLibrary.Source left = lib.source("masculine", Slot.CARRY_LEFT, "sword", Wardrobe.Style.PIXEL_128, true);
        assertEquals(new SpriteLibrary.Source(Slot.CARRY_RIGHT, "sword_px128", true, "masculine"), left);
        assertEquals(root.resolve("sprites_masculine/carry_right/sword_px128/idle_side_s.png"),
                lib.resolve(left, AnimState.IDLE, Elevation.SIDE, Facing.SOUTH).file());
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
