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
        assertNotEquals(v.recolor(Map.of("own", "black")).key(), rc.key(), "each choice decodes separately");
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
        assertEquals(List.of("royal_blue", "black"), lib.colourOptions(Slot.SHIRT, "tunic", Wardrobe.Style.RENDERED),
                "no 512-pixel version: the rendered style draws the 128-pixel one, in its colours");
        SpriteLibrary.Source src = lib.source(Slot.SHIRT, "tunic", w.style(), false);
        assertNull(lib.recolor(src, Slot.SHIRT, w));
        w.setColour(Slot.SHIRT, "black");
        w.setColour(Slot.BODY, "mint");
        Variants.Recolor rc = lib.recolor(src, Slot.SHIRT, w);
        assertEquals("own=black,skin=mint", rc.key());
    }

    @Test
    void aLeftHandedCharacterUsesLeftHandedArtOrTheTwinMirrored() throws Exception {
        palettePng("carry_right/sword/attack_side_e.png");
        palettePng("carry_right/sword/attack_side_w.png");
        palettePng("carry_left/sword_px128_lh/attack_side_e.png");
        palettePng("carry_right/sword_px128/attack_side_e.png");
        palettePng("carry_left/sword_px128/attack_side_e.png");
        lib = new SpriteLibrary(root, 1L << 30, 1.0);
        assertEquals(List.of(), lib.items(Slot.CARRY_LEFT).stream().filter(i -> !Wardrobe.Style.isVersion(i)).toList(),
                "the left-handed version is not an item of its own");
        // pixel art: the left-handed version, as it is
        SpriteLibrary.Source px = lib.source(Slot.CARRY_LEFT, "sword", Wardrobe.Style.PIXEL_128, true);
        assertEquals(new SpriteLibrary.Source(Slot.CARRY_LEFT, "sword_px128_lh", false), px);
        // the renders have none: the right hand's sword, from the other side, mirrored (not
        // the pixel art, although the left hand has some)
        SpriteLibrary.Source r = lib.source(Slot.CARRY_LEFT, "sword", Wardrobe.Style.RENDERED, true);
        assertEquals(new SpriteLibrary.Source(Slot.CARRY_RIGHT, "sword", true), r);
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
        Slot[] order = LayerStack.drawOrder(true);
        assertTrue(indexOf(order, Slot.CARRY_RIGHT) < indexOf(order, Slot.CARRY_LEFT),
                "left-handed, the left hand is drawn last");
        assertEquals(Slot.values().length, order.length);
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
}
