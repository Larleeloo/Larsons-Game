package com.larsons.game.sprite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Colour variants: variants.json, palette swaps, and left-handed characters. */
class VariantsTest {

    @TempDir
    Path root;
    SpriteLibrary lib;

    @AfterEach
    void close() {
        if (lib != null) lib.close();
    }

    /** Two labels of the own channel (0, 1), one skin (2), one fixed (3). */
    static final String JSON = """
            {"version": 1, "labels": 4, "default": {},
             "channels": {
              "own": {"labels": [0, 1],
                      "options": {"royal_blue": ["#1020a0", "#081050"], "black": ["#111111", "#050505"]},
                      "swatch": {"royal_blue": "#1020a0", "black": "#111111"}},
              "skin": {"labels": [2], "options": {"mint": ["#80e0a0"]}, "swatch": {"mint": "#80e0a0"}}}}
            """;

    /** A 4 x 1 palette PNG: transparent, marker, then the four labels in their own colours. */
    static BufferedImage sheet() {
        byte[] r = {0, 0, (byte) 200, (byte) 100, (byte) 180, (byte) 240};
        byte[] g = {0, 0, 10, 5, (byte) 140, (byte) 200};
        byte[] b = {0, 0, 10, 5, 100, 40};
        byte[] a = {0, 1, (byte) 255, (byte) 255, (byte) 255, (byte) 255};
        IndexColorModel cm = new IndexColorModel(8, 6, r, g, b, a);
        BufferedImage img = new BufferedImage(4, 1, BufferedImage.TYPE_BYTE_INDEXED, cm);
        for (int i = 0; i < 4; i++) img.getRaster().setSample(i, 0, 0, i + 2);
        return img;
    }

    @Test
    void aChoiceSwapsOnlyItsChannelsEntries() {
        Variants v = Variants.parse(JSON);
        assertEquals(List.of("royal_blue", "black"), v.channel("own").names());
        assertNull(v.recolor(Map.of()), "nothing picked: the sheet's own colours");
        Variants.Recolor rc = v.recolor(Map.of("own", "royal_blue", "skin", "mint"));
        BufferedImage out = Variants.apply(sheet(), rc);
        assertEquals(0x1020a0, out.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(0x081050, out.getRGB(1, 0) & 0xFFFFFF);
        assertEquals(0x80e0a0, out.getRGB(2, 0) & 0xFFFFFF, "skin follows the skin choice");
        assertEquals(0xf0c828, out.getRGB(3, 0) & 0xFFFFFF, "fixed labels keep their colour");
        assertEquals(0xFF, out.getRGB(3, 0) >>> 24);
        assertNull(v.recolor(Map.of("own", "no_such_colour")), "an option the item lacks changes nothing");
        assertNotEquals(v.recolor(Map.of("own", "black")).key(), rc.key(), "each choice is a palette of its own");
    }

    @Test
    void aSwapIsMadeOnTheLayersPaletteAndKeepsItsAlpha() {
        Variants v = Variants.parse(JSON);
        int[] base = {0x00000000, 0x01000000, 0xFFC80A0A, 0xFF640505, 0xFFB48C64, 0xFFF0C828};
        int[] p = Variants.recolored(base, v.recolor(Map.of("own", "royal_blue", "skin", "mint")));
        assertArrayEquals(new int[]{0x00000000, 0x01000000, 0xFF1020A0, 0xFF081050, 0xFF80E0A0, 0xFFF0C828}, p);
        assertEquals(0xFFC80A0A, base[2], "the sheet's own palette is untouched");
        assertSame(base, Variants.recolored(base, null), "no choice: the sheet's own colours");
    }

    @Test
    void everyColourOfASheetIsOneTexture() throws Exception {
        palettePng("shirt/tunic_px128/walk_side_e.png");
        Files.writeString(root.resolve("shirt/tunic_px128/variants.json"), JSON, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("shirt/tunic_px128/profile.json"),
                "{\"frameWidth\": 4, \"frameHeight\": 1, \"pixelArt\": true}", StandardCharsets.UTF_8);
        lib = new SpriteLibrary(root, 1L << 30, 1.0, (layout, pixels, source) -> {
            assertTrue(pixels.indexed(), "a palette sheet goes up as its indices");
            assertEquals(pixels.width() * (long) pixels.height(), pixels.bytes());
            pixels.free();
            return new SheetTexture(layout, SpriteLibrary.bytesOf(layout), source);
        });
        Variants v = Variants.parse(JSON);
        SpriteLibrary.Source src = lib.source(Slot.SHIRT, "tunic", Wardrobe.Style.PIXEL_128, false);
        SpriteLibrary.Resolved file = lib.resolve(src, AnimState.WALK, Elevation.SIDE, Facing.EAST);
        SpriteLibrary.Resolved blue = file.withRecolor(v.recolor(Map.of("own", "royal_blue")));
        SpriteLibrary.Resolved black = file.withRecolor(v.recolor(Map.of("own", "black")));
        assertEquals(blue.key(), black.key());
        assertNull(lib.sheet(blue));
        lib.awaitDecodes();
        lib.pumpAll();
        SheetTexture t = lib.sheet(black);
        assertNotNull(t, "already resident: the blue one is the same texture");
        assertEquals(1, lib.residentCount());
        assertTrue(t.indexed());
        assertEquals(0xFFC80A0A, t.palette()[2], "the sheet's own colours, label 0");
        assertEquals(0xFF050505, Variants.recolored(t.palette(), black.recolor())[3], "black, label 1");
    }

    private void palettePng(String rel) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        ImageIO.write(sheet(), "png", p.toFile());
    }

    @Test
    void theLibraryReadsAnItemsColoursAndRecolorsForTheSlot() throws Exception {
        palettePng("shirt/tunic_px128/walk_side_e.png");
        Files.writeString(root.resolve("shirt/tunic_px128/variants.json"), JSON, StandardCharsets.UTF_8);
        palettePng("body/hero/walk_side_e.png");
        lib = new SpriteLibrary(root, 1L << 30, 1.0);
        Wardrobe w = new Wardrobe();
        w.setStyle(Wardrobe.Style.PIXEL_128);
        w.set(Slot.SHIRT, "tunic");
        assertEquals(List.of("royal_blue", "black"), lib.colourOptions(Slot.SHIRT, "tunic", w.style()));
        assertEquals(0x1020a0, lib.swatch(Slot.SHIRT, "tunic", w.style(), "royal_blue"));
        assertEquals(List.of("royal_blue", "black"), lib.colourOptions(Slot.SHIRT, "tunic", Wardrobe.Style.PIXEL_64),
                "no 64-pixel version: that style draws the 128-pixel one, in its colours");
        SpriteLibrary.Source src = lib.source(Slot.SHIRT, "tunic", w.style(), false);
        assertNull(lib.recolor(src, Slot.SHIRT, w));
        w.setColour(Slot.SHIRT, "black");
        w.setColour(Slot.BODY, "mint");
        Variants.Recolor rc = lib.recolor(src, Slot.SHIRT, w);
        assertEquals("own=black,skin=mint", rc.key());
    }

    @Test
    void aLeftHandedCharacterUsesLeftHandedArtOrTheTwinMirrored() throws Exception {
        palettePng("carry_left/sword_px128_lh/attack_side_e.png");
        palettePng("carry_right/sword_px128/attack_side_e.png");
        palettePng("carry_right/sword_px128/attack_side_w.png");
        palettePng("carry_left/sword_px128/attack_side_e.png");
        lib = new SpriteLibrary(root, 1L << 30, 1.0);
        assertEquals(List.of(), lib.items(Slot.CARRY_LEFT).stream().filter(i -> !Wardrobe.Style.isVersion(i)).toList(),
                "the left-handed version is not an item of its own");
        // pixel art: the left-handed version, as it is
        SpriteLibrary.Source px = lib.source(Slot.CARRY_LEFT, "sword", Wardrobe.Style.PIXEL_128, true);
        assertEquals(new SpriteLibrary.Source(Slot.CARRY_LEFT, "sword_px128_lh", false), px);
        // the 64-pixel art has no left-handed version: the right hand's sword, from the other side, mirrored
        SpriteLibrary.Source r = lib.source(Slot.CARRY_LEFT, "sword", Wardrobe.Style.PIXEL_64, true);
        assertEquals(new SpriteLibrary.Source(Slot.CARRY_RIGHT, "sword_px128", true), r);
        SpriteLibrary.Resolved east = lib.resolve(r, AnimState.ATTACK, Elevation.SIDE, Facing.EAST);
        assertTrue(east.mirrored());
        assertTrue(east.file().endsWith("attack_side_w.png"), "seen from the east: the west view, mirrored");
        // right-handed: as it always was
        assertEquals(new SpriteLibrary.Source(Slot.CARRY_RIGHT, "sword_px128", false),
                lib.source(Slot.CARRY_RIGHT, "sword", Wardrobe.Style.PIXEL_128, false));
    }

    @Test
    void autoIsLeftHandedWithTheSwordInTheLeftHandOnly() {
        Wardrobe w = new Wardrobe();
        assertFalse(w.leftHanded());
        w.set(Slot.CARRY_LEFT, "sword");
        assertTrue(w.leftHanded());
        w.set(Slot.CARRY_RIGHT, "sword");
        assertFalse(w.leftHanded(), "a sword in each hand: right-handed");
        w.setHand(Wardrobe.Hand.LEFT);
        assertTrue(w.leftHanded());
        Slot[] order = LayerStack.drawOrder(true, Elevation.SIDE, Facing.NORTH);
        assertTrue(indexOf(order, Slot.CARRY_RIGHT) < indexOf(order, Slot.CARRY_LEFT),
                "left-handed, the left hand is drawn last");
        assertEquals(Slot.values().length, order.length);
    }

    @Test
    void aCapeIsUnderEverythingFromTheFrontAndOverTheClothesFromBehind() {
        for (Facing f : new Facing[] {Facing.SOUTH, Facing.SOUTH_EAST, Facing.SOUTH_WEST}) {
            for (Elevation e : new Elevation[] {Elevation.SIDE, Elevation.MIDDLE}) {
                Slot[] order = LayerStack.drawOrder(false, e, f);
                assertEquals(Slot.OTHER, order[0], "facing the camera: behind the body and all");
                assertEquals(Slot.values().length, order.length);
            }
        }
        for (Facing f : new Facing[] {Facing.NORTH, Facing.EAST, Facing.WEST, Facing.NORTH_WEST}) {
            Slot[] order = LayerStack.drawOrder(false, Elevation.SIDE, f);
            int cape = indexOf(order, Slot.OTHER);
            for (Slot s : new Slot[] {Slot.BODY, Slot.SHIRT, Slot.PANTS, Slot.SHEATH, Slot.MOUTH}) {
                assertTrue(indexOf(order, s) < cape, s + " is under the cape from behind");
            }
            for (Slot s : new Slot[] {Slot.HAIR, Slot.HAT, Slot.CARRY_LEFT, Slot.CARRY_RIGHT}) {
                assertTrue(indexOf(order, s) > cape, s + " is over the cape");
            }
        }
        assertTrue(indexOf(LayerStack.drawOrder(false, Elevation.TOP, Facing.SOUTH), Slot.OTHER) > 0,
                "from above the cape is on the back, over the clothes");
    }

    private static int indexOf(Slot[] a, Slot s) {
        for (int i = 0; i < a.length; i++) if (a[i] == s) return i;
        return -1;
    }

    @Test
    void coloursAndTheHandAreSaved() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.HAIR, "crew_cut");
        w.setColour(Slot.HAIR, "royal_blue");
        w.setColour(Slot.BODY, "copper");
        w.setHand(Wardrobe.Hand.LEFT);
        Wardrobe back = Wardrobe.fromJson(w.toJson());
        assertEquals("royal_blue", back.colour(Slot.HAIR));
        assertEquals("copper", back.skin());
        assertEquals(Wardrobe.Hand.LEFT, back.hand());
        assertEquals("crew_cut", back.get(Slot.HAIR));
        assertEquals("sword", Wardrobe.Style.base("sword_px64_lh"));
        assertEquals(Slot.WRISTWEAR, Slot.WRISTWEAR_LEFT.twin());
        assertEquals(Slot.HAIR, Slot.HAIR.twin());
    }


    @Test
    void anyColourShadesAnItemAndWithThePaletteKnownStaysInIt() {
        assertTrue(Variants.isCustom("#208040"));
        for (String no : List.of("mint", "#12345", "#gggggg", "208040x")) assertFalse(Variants.isCustom(no), no);
        assertFalse(Variants.isCustom(null));
        Variants v = Variants.parse(JSON);
        BufferedImage out = Variants.apply(sheet(), v.recolor(Map.of("own", "#208040")));
        int main = out.getRGB(0, 0) & 0xFFFFFF, shade = out.getRGB(1, 0) & 0xFFFFFF;
        for (int shift = 0; shift <= 16; shift += 8) {
            assertEquals((0x208040 >> shift) & 0xFF, (main >> shift) & 0xFF, 2, "the label the swatch is of: the colour");
        }
        assertTrue(Variants.Oklab.of(shade)[0] < Variants.Oklab.of(main)[0] - 0.05, "its shade as much darker as before");
        assertEquals(0xb48c64, out.getRGB(2, 0) & 0xFFFFFF, "the skin's label is not the item's own");
        // the art's palette known: every label is one of its colours
        PixelPalette p = PixelPalette.load(Path.of("assets", "sprites"));
        assertNotNull(p, "assets/sprites/pixel_palette.json");
        try {
            Variants.setPalette(p);
            for (int rgb : new int[]{0x208040, 0x123456, 0xfefefe, 0x4e2507}) {
                for (int c : Variants.custom(Variants.parse(JSON).channel("own"), rgb)) {
                    assertTrue(p.contains(c), String.format("#%06x of #%06x", c, rgb));
                }
            }
        } finally {
            Variants.setPalette(null);
        }
        // the fills the game offers as colours are the palette file's
        int[] file = p.fills(), built = PixelPalette.FILLS.clone();
        java.util.Arrays.sort(file);
        java.util.Arrays.sort(built);
        assertArrayEquals(file, built, "PixelPalette.FILLS is pixel_palette.json's fills");
    }

    @Test
    void unpaintedLipsAreTheSkinALittleDarkerWhateverTheSkin() {
        Variants v = Variants.parse(JSON);
        String lips = v.natural("mint");
        assertEquals(String.format("#%06x", Variants.lips(0x80e0a0)), lips, "from the skin's swatch");
        assertEquals(String.format("#%06x", Variants.lips(0x4e2507)), v.natural("#4e2507"), "or any colour of skin");
        assertNull(v.natural(null));
        assertNull(v.natural("no_such_tone"));
        for (int skin : PixelPalette.FILLS) {
            double[] s = Variants.Oklab.of(skin), l = Variants.Oklab.of(Variants.lips(skin));
            assertTrue(l[0] <= s[0] + 0.01, String.format("#%06x: no lighter than the skin", skin));
            assertTrue(l[0] >= s[0] - 0.12, String.format("#%06x: only a little darker", skin));
            assertTrue(Math.hypot(l[1] - s[1], l[2] - s[2]) < 0.05,
                    String.format("#%06x: the skin's own colour, not a red painted on it", skin));
        }
        // asked for as the mouth's colour, it is the lips of the skin picked
        Variants.Recolor natural = v.recolor(Map.of("own", Variants.NATURAL, "skin", "mint"));
        Variants.Recolor same = Variants.parse(JSON).recolor(Map.of("own", lips, "skin", "mint"));
        assertNotNull(natural);
        assertEquals(same.key(), natural.key());
        assertNull(Variants.parse(JSON).recolor(Map.of("own", Variants.NATURAL)), "no skin picked: its own colours");
    }

    @Test
    void aWardrobeBecomesAnotherButKeepsWhatIsInItsHands() {
        Wardrobe mine = new Wardrobe();
        mine.set(Slot.SHIRT, "baggy_tee");
        mine.set(Slot.HAT, "straw_sun_hat");
        mine.set(Slot.CARRY_RIGHT, "sword");
        Wardrobe other = new Wardrobe();
        other.set(Slot.BODY, "masculine");
        other.setColour(Slot.BODY, "#4e2507");
        other.set(Slot.SHIRT, "laced_shirt");
        other.setColour(Slot.SHIRT, "emerald");
        other.set(Slot.CARRY_RIGHT, "mace");
        other.setStyle(Wardrobe.Style.PIXEL_64);
        other.setHand(Wardrobe.Hand.LEFT);
        mine.become(other);
        assertEquals("masculine", mine.get(Slot.BODY));
        assertEquals("#4e2507", mine.skin());
        assertEquals("laced_shirt", mine.get(Slot.SHIRT));
        assertEquals("emerald", mine.colour(Slot.SHIRT));
        assertNull(mine.get(Slot.HAT), "what the other does not wear comes off");
        assertEquals("sword", mine.get(Slot.CARRY_RIGHT), "the hands are the weapons'");
        assertEquals(Wardrobe.Style.PIXEL_64, mine.style());
        assertEquals(Wardrobe.Hand.LEFT, mine.hand());
        other.set(Slot.SHIRT, "baggy_tee");
        assertEquals("laced_shirt", mine.get(Slot.SHIRT), "a look of its own from then on");
    }
}
