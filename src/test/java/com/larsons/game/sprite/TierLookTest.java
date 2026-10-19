package com.larsons.game.sprite;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The weapon tiers' looks (tiers.json), the 128-colour palette they snap to,
 * and the turning item sprites with their icons - against the real assets.
 */
class TierLookTest {

    static final Path SPRITES = Path.of("assets", "sprites");

    static PixelPalette palette() {
        PixelPalette p = PixelPalette.load(SPRITES);
        assertNotNull(p, "assets/sprites/pixel_palette.json");
        return p;
    }

    /** A sheet's palette, straight ARGB: transparent, the marker, then the labels. */
    static int[] palette(Path png) throws Exception {
        BufferedImage img = ImageIO.read(png.toFile());
        IndexColorModel icm = (IndexColorModel) img.getColorModel();
        int[] p = new int[256];
        icm.getRGBs(p);
        for (int i = icm.getMapSize(); i < 256; i++) p[i] = 0;
        return p;
    }

    static Path anySheet(String folder) throws Exception {
        try (var files = Files.list(SPRITES.resolve(folder))) {
            return files.filter(f -> f.toString().endsWith(".png")).sorted().findFirst().orElseThrow();
        }
    }

    static double hue(int rgb) {
        double[] l = Variants.Oklab.of(rgb & 0xFFFFFF);
        return Math.toDegrees(Math.atan2(l[2], l[1]));
    }

    static double lightness(int rgb) {
        return Variants.Oklab.of(rgb & 0xFFFFFF)[0];
    }

    /** Entries of materials {@code mats}. */
    static List<Integer> entries(int... mats) {
        List<Integer> out = new java.util.ArrayList<>();
        for (int m : mats) for (int j = 0; j < 6; j++) out.add(2 + 6 * m + j);
        return out;
    }

    @Test
    void thePaletteHasItsHundredFillsAndTwentyEightLinesAndSnapsIntoThem() {
        PixelPalette p = palette();
        assertEquals(100, p.fills().length);
        assertEquals(28, p.lines().length);
        for (int rgb : new int[]{0x123456, 0xFF00FF, 0x00FFFF, 0xFFFFFF, 0x000000}) {
            assertTrue(p.contains(p.snap(rgb, false)));
            assertTrue(p.contains(p.snap(rgb, true)));
        }
        assertEquals(0xB722E7, p.snap(0xB722E7, false), "a palette colour is its own nearest");
        // the rarities' and borders' colours are the palette's own
        for (int rgb : new int[]{0xFFFFFF, 0x24C835, 0x5D86FA, 0xB722E7, 0xFB992C, 0x439F9D,
                0x7EE728, 0x636092, 0xFD81A5}) {
            assertTrue(p.contains(rgb), Integer.toHexString(rgb));
        }
    }

    @Test
    void aSharperAxeHasABlackHandleAndItsOwnBlade() throws Exception {
        TierLook look = TierLook.load(SPRITES, palette());
        int[] base = palette(anySheet("carry_right/battle_axe_px128"));
        int[] sharper = look.palette(base, "battle_axe", "sharper", look.carried("battle_axe"));
        assertNotSame(base, sharper);
        for (int e : entries(1)) assertEquals(base[e], sharper[e], "the blade is as drawn");
        assertTrue(lightness(sharper[3]) < lightness(base[3]) - 0.15, "the handle's mid is much darker");
        assertTrue(lightness(sharper[3]) < 0.32, "black");
        for (int e : entries(0)) {
            assertTrue(palette().contains(sharper[e]), "in the palette: " + Integer.toHexString(sharper[e]));
            assertEquals(base[e] >>> 24, sharper[e] >>> 24, "alpha kept");
        }
        assertSame(sharper, look.palette(base, "battle_axe", "sharper", look.carried("battle_axe")),
                "the same array frame after frame");
        assertSame(base, look.palette(base, "battle_axe", "basic", look.carried("battle_axe")),
                "a Basic Axe is as drawn");
    }

    @Test
    void aCrystalSwordHasAPinkPurpleBladeAndAPerfectOneIsAllGold() throws Exception {
        TierLook look = TierLook.load(SPRITES, palette());
        int[] base = palette(anySheet("carry_right/sword_px128"));
        Map<String, int[]> parts = look.carried("sword");
        int[] crystal = look.palette(base, "sword", "crystal", parts);
        double h = hue(crystal[3]);
        assertTrue(h < -40 || h > 280, "the blade's mid is purple: " + h);
        assertTrue(Math.hypot(Variants.Oklab.of(crystal[3])[1], Variants.Oklab.of(crystal[3])[2]) > 0.1);
        for (int e : entries(1, 2, 3)) assertEquals(base[e], crystal[e], "only the blade");

        int[] gold = look.palette(base, "sword", "perfect", parts);
        for (int m = 0; m < 4; m++) {
            int mid = gold[2 + 6 * m + 1];
            double gh = hue(mid);
            assertTrue(gh > 40 && gh < 110, "material " + m + " gold: hue " + gh);
            for (int e : entries(m)) assertTrue(palette().contains(gold[e]));
        }
        assertTrue(lightness(gold[2 + 6 * 3 + 1]) < lightness(gold[2 + 1]),
                "the grip a darker gold than the blade");

        int[] mythic = look.palette(base, "sword", "mythic", parts);
        double mh = hue(mythic[3]);
        assertTrue(mh > 240 || mh < -60, "smoky purple: " + mh);
    }

    @Test
    void theStringIsNeverRecolouredAndHonedWoodIsDarker() throws Exception {
        TierLook look = TierLook.load(SPRITES, palette());
        int[] bow = palette(anySheet("carry_left/longbow_px128"));
        int[] perfect = look.palette(bow, "longbow", "perfect", look.carried("longbow"));
        for (int e : entries(0)) assertEquals(bow[e], perfect[e], "the string");
        assertNotEquals(bow[2 + 6 + 1], perfect[2 + 6 + 1]);
        int[] crafted = look.palette(bow, "longbow", "crafted", look.carried("longbow"));
        assertTrue(lightness(crafted[2 + 6 + 1]) < 0.32, "a Crafted Bow's body is black");

        int[] staff = palette(anySheet("carry_right/crystal_staff_px128"));
        Map<String, int[]> parts = look.carried("crystal_staff");
        int[] honed = look.palette(staff, "crystal_staff", "honed", parts);
        for (int j = 0; j < 4; j++) {
            assertTrue(lightness(honed[2 + j]) <= lightness(staff[2 + j]) + 1e-9, "darker wood, label " + j);
        }
        for (int e : entries(1)) assertEquals(staff[e], honed[e], "its crystal as drawn");
        assertSame(staff, look.palette(staff, "crystal_staff", "magic", parts), "the Magic Staff is as drawn");
        int[] basic = look.palette(staff, "crystal_staff", "basic", parts);
        double[] crystalMid = Variants.Oklab.of(basic[2 + 6 + 1]);
        assertTrue(Math.hypot(crystalMid[1], crystalMid[2]) < 0.04, "the Basic Staff's crystal is dull grey");
    }

    @Test
    void theFiveWeaponsTurnInPlaceWithTheirPartsNamed() {
        ItemSprites items = new ItemSprites(SPRITES);
        for (String id : List.of("sword", "battle_axe", "longbow", "crossbow", "crystal_staff")) {
            ItemSprites.Spin s = items.spin(id);
            assertNotNull(s, id);
            assertEquals(3, s.sheets().size(), id + ": side, middle and top");
            assertEquals(32, s.frames());
            assertEquals(128, s.profile().frameWidth());
            assertTrue(s.profile().pixelArt());
            assertTrue(s.profile().frameWorldSize() > s.length(), "room round it for its halo");
            assertFalse(s.parts().isEmpty(), id + " parts");
            assertNotNull(items.resolve(id, Elevation.MIDDLE));
        }
        assertTrue(items.spin("crystal_staff").parts().containsKey("blade"), "the crystal");
        assertTrue(items.spin("longbow").parts().containsKey("string"));
        assertNull(items.spin("no_such_thing"));
        assertNull(items.resolve("no_such_thing", Elevation.SIDE));
    }

    @Test
    void anIconIsEachFrameReducedAsPixelArtWithAHaloAndARingRoundIt() throws Exception {
        ItemSprites items = new ItemSprites(SPRITES);
        ItemSprites.Spin s = items.spin("battle_axe");
        BufferedImage img = ImageIO.read(s.sheet(Elevation.TOP).toFile());
        int[] base = palette(s.sheet(Elevation.TOP));
        ItemSprites.IconPixels px = ItemSprites.iconPixels(img, 128, 128, 32, base);
        assertEquals(8 * ItemSprites.ICON, px.width());
        assertEquals(4 * ItemSprites.ICON, px.height());
        assertEquals(32, px.frames());
        int f = ItemSprites.ICON_REST_FRAME;
        int ox = (f % 8) * ItemSprites.ICON, oy = (f / 8) * ItemSprites.ICON;
        int solid = 0, ring = 0, ringOnItem = 0, haloOutside = 0;
        for (int y = 0; y < ItemSprites.ICON; y++) {
            for (int x = 0; x < ItemSprites.ICON; x++) {
                int o = (oy + y) * px.width() + ox + x;
                boolean item = (px.sprite()[o] >>> 24) >= 128;
                if (item) solid++;
                if ((px.ring()[o] >>> 24) == 255) {
                    ring++;
                    if (item) ringOnItem++;
                }
                if (!item && (px.halo()[o] >>> 24) > 20) haloOutside++;
            }
        }
        assertTrue(solid > 150, "the axe is there: " + solid);
        assertTrue(ring > 80, "a ring round it: " + ring);
        assertEquals(0, ringOnItem, "the ring is outside the item");
        assertTrue(haloOutside > ring, "the halo spreads past it");
        // the frame's corners are clear
        assertEquals(0, px.sprite()[oy * px.width() + ox] >>> 24);
        // reduced as pixel art: every pixel solid, and one of the palette's
        PixelPalette pal = palette();
        for (int c : px.sprite()) {
            int a = c >>> 24;
            assertTrue(a == 0 || a == 255, "solid or clear");
            if (a == 255) assertTrue(pal.contains(c), Integer.toHexString(c));
        }
    }
}
