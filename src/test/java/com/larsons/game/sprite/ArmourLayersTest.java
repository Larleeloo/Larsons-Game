package com.larsons.game.sprite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The armour's layers: where they go in the draw order, and what they hide. */
class ArmourLayersTest {

    @TempDir
    Path root;
    SpriteLibrary lib;

    @AfterEach
    void close() {
        if (lib != null) lib.close();
    }

    @Test
    void theArmourGoesOverTheClothesItIsWornOverAsInBlender() {
        // feminine_model_sprites.py's SLOTS
        List<String> blender = List.of("body", "underwear", "bra", "shoes", "pants", "chainmail_legs", "leg_plating",
                "shirt", "chainmail", "chestplate", "belt", "necklace", "gloves", "wristwear", "wristwear_left",
                "sheath", "ears", "earrings", "nose", "eyes", "eyebrows", "makeup", "mouth", "hair", "eyewear", "hat", "horns",
                "other", "tail", "carry_left", "carry_right");
        assertEquals(blender, Arrays.stream(Slot.values()).map(Slot::key).toList());
        assertEquals(31, Slot.values().length);
        assertEquals("Mail leggings", Slot.CHAINMAIL_LEGS.label());
        assertEquals("Leg plating", Slot.LEG_PLATING.label());
        assertEquals("Mail shirt", Slot.CHAINMAIL.label());
        assertEquals(Slot.CHESTPLATE, Slot.byKey("chestplate"));
        assertEquals(Slot.CHAINMAIL, Slot.CHAINMAIL.twin());
        assertEquals(31, LayerStack.drawOrder(false, Elevation.SIDE, Facing.EAST).length);
    }

    @Test
    void gauntletsHideTheWristsAndAChestplateTheNecklace() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.WRISTWEAR, "gold_bangle");
        w.set(Slot.WRISTWEAR_LEFT, "gold_bangle");
        w.set(Slot.NECKLACE, "gold_pendant");
        for (Slot s : Slot.values()) assertFalse(w.hidden(s), s + " with no armour");
        w.set(Slot.GLOVES, "black_leather");
        assertFalse(w.hidden(Slot.WRISTWEAR), "leather gloves leave the bangle showing");
        for (String gauntlets : List.of("iron_gauntlets", "steel_gauntlets")) {
            w.set(Slot.GLOVES, gauntlets);
            assertTrue(w.hidden(Slot.WRISTWEAR), gauntlets);
            assertTrue(w.hidden(Slot.WRISTWEAR_LEFT), gauntlets);
            assertFalse(w.hidden(Slot.NECKLACE));
            assertFalse(w.hidden(Slot.GLOVES), "the gauntlets themselves are drawn");
        }
        w.set(Slot.CHESTPLATE, "steel_chestplate");
        assertTrue(w.hidden(Slot.NECKLACE));
        w.clear(Slot.CHESTPLATE);
        w.clear(Slot.GLOVES);
        assertFalse(w.hidden(Slot.NECKLACE), "off comes the armour, back comes the necklace");
        assertFalse(w.hidden(Slot.WRISTWEAR));
        assertEquals("gold_bangle", w.get(Slot.WRISTWEAR), "what was hidden stayed worn");
    }

    @Test
    void theMailHidesTheClothesThatWouldPokeThroughItButADressKeepsItsSkirt() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.UNDERWEAR, "cotton_briefs");
        w.set(Slot.BRA, "leather_bralette");
        w.set(Slot.PANTS, "cargo_pants");
        w.set(Slot.SHIRT, "plaid_flannel");
        w.set(Slot.CHAINMAIL_LEGS, "chainmail_pants");
        for (Slot s : List.of(Slot.UNDERWEAR, Slot.PANTS)) assertTrue(w.hidden(s), s + " under mail leggings");
        assertFalse(w.hidden(Slot.SHIRT));
        assertFalse(w.hidden(Slot.BRA));
        w.set(Slot.CHAINMAIL, "chainmail_shirt");
        assertTrue(w.hidden(Slot.SHIRT), "the baggy shirt under the mail shirt");
        assertTrue(w.hidden(Slot.BRA));
        assertNull(w.cutBy(Slot.SHIRT));
        w.set(Slot.SHIRT, "laced_dress");
        assertFalse(w.hidden(Slot.SHIRT), "a dress shows below the mail");
        assertEquals(Slot.CHAINMAIL, w.cutBy(Slot.SHIRT), "but is cut where the mail covers it");
        w.clear(Slot.CHAINMAIL);
        w.clear(Slot.CHAINMAIL_LEGS);
        assertNull(w.cutBy(Slot.SHIRT));
        for (Slot s : Slot.values()) assertFalse(w.hidden(s), s + " with the mail off");
        assertEquals("cargo_pants", w.get(Slot.PANTS), "what was hidden stayed worn");
    }

    @Test
    void aHiddenLayerIsLeftOutOfTheStack() throws Exception {
        for (String rel : List.of("body/hero", "wristwear/gold_bangle", "necklace/gold_pendant",
                "gloves/iron_gauntlets", "chestplate/iron_chestplate")) {
            Path dir = root.resolve(rel);
            Files.createDirectories(dir);
            ImageIO.write(VariantsTest.sheet(), "png", dir.resolve("walk_side_e.png").toFile());
            Files.writeString(dir.resolve("profile.json"),
                    "{\"frameWidth\": 4, \"frameHeight\": 1, \"pixelArt\": true}", StandardCharsets.UTF_8);
        }
        lib = new SpriteLibrary(root, 1L << 30, 1.0, (layout, pixels, source) -> {
            pixels.free();
            return new SheetTexture(layout, SpriteLibrary.bytesOf(layout), source);
        });
        Wardrobe w = new Wardrobe();
        w.set(Slot.BODY, "hero");
        w.set(Slot.WRISTWEAR, "gold_bangle");
        w.set(Slot.NECKLACE, "gold_pendant");
        assertEquals(List.of(Slot.BODY, Slot.NECKLACE, Slot.WRISTWEAR), drawn(w));
        w.set(Slot.GLOVES, "iron_gauntlets");
        w.set(Slot.CHESTPLATE, "iron_chestplate");
        assertEquals(List.of(Slot.BODY, Slot.CHESTPLATE, Slot.GLOVES), drawn(w));
        w.set(Slot.CHESTPLATE, "steel_chestplate");
        assertEquals(List.of(Slot.BODY, Slot.GLOVES), drawn(w),
                "armour with no sheets yet is not drawn, and still hides what it covers");
    }

    /** The slots drawn for {@code w} walking east, once every sheet is in. */
    private List<Slot> drawn(Wardrobe w) {
        SpriteView view = new SpriteView(Elevation.SIDE, Facing.EAST, Elevation.SIDE.renderAngle());
        // (every sheet asked for first: a stack resolved before its body is in draws the generated one, on the GPU)
        for (Slot s : Slot.values()) {
            String item = w.get(s);
            SpriteLibrary.Resolved file = item == null ? null
                    : lib.resolve(lib.source("hero", s, item, w.style(), false), AnimState.WALK, Elevation.SIDE, Facing.EAST);
            if (file != null) lib.sheet(file);
        }
        lib.awaitDecodes();
        lib.pumpAll();
        LayerStack.Result r = LayerStack.resolve(lib, w, AnimState.WALK, 0, view, new LayerStack.Memory());
        assertFalse(r.fallbackBody());
        return r.layers().stream().map(LayerStack.Layer::slot).toList();
    }


    @Test
    void longHairIsGatheredAtTheHeadWhileSheSwimsAStroke() throws Exception {
        Wardrobe w = new Wardrobe();
        w.set(Slot.BODY, "hero");
        for (String hair : List.of("long_waves", "braids", "curls")) {
            w.set(Slot.HAIR, hair);
            assertEquals("bob", w.keptTo(Slot.HAIR, AnimState.SWIM), hair);
        }
        w.set(Slot.HAIR, "beanie_long");
        assertEquals("beanie_short", w.keptTo(Slot.HAIR, AnimState.SWIM), "under its beanie still");
        assertNull(w.keptTo(Slot.HAIR, AnimState.SWIM_IDLE), "treading water she is upright: it hangs as it does");
        assertNull(w.keptTo(Slot.HAIR, AnimState.WALK));
        assertNull(w.keptTo(Slot.SHIRT, AnimState.SWIM), "her hair only");
        w.set(Slot.HAIR, "pixie");
        assertNull(w.keptTo(Slot.HAIR, AnimState.SWIM), "short hair is as it is");
        w.clear(Slot.HAIR);
        assertNull(w.keptTo(Slot.HAIR, AnimState.SWIM));

        // in the stack: her long hair's sheet is the one kept near the bob's, the same state from the same view
        for (String rel : List.of("body/hero", "hair/long_waves", "hair/bob")) {
            Path dir = root.resolve(rel);
            Files.createDirectories(dir);
            ImageIO.write(VariantsTest.sheet(), "png", dir.resolve("swim_side_e.png").toFile());
            ImageIO.write(VariantsTest.sheet(), "png", dir.resolve("walk_side_e.png").toFile());
            Files.writeString(dir.resolve("profile.json"),
                    "{\"frameWidth\": 4, \"frameHeight\": 1, \"pixelArt\": true}", StandardCharsets.UTF_8);
        }
        lib = new SpriteLibrary(root, 1L << 30, 1.0, (layout, pixels, source) -> {
            pixels.free();
            return new SheetTexture(layout, SpriteLibrary.bytesOf(layout), source);
        });
        w.set(Slot.HAIR, "long_waves");
        SpriteView view = new SpriteView(Elevation.SIDE, Facing.EAST, Elevation.SIDE.renderAngle());
        SpriteLibrary.Resolved hair = lib.resolve(lib.source("hero", Slot.HAIR, "long_waves", w.style(), false),
                AnimState.SWIM, Elevation.SIDE, Facing.EAST);
        SpriteLibrary.Resolved bob = lib.resolve(lib.source("hero", Slot.HAIR, "bob", w.style(), false),
                AnimState.SWIM, Elevation.SIDE, Facing.EAST);
        SpriteLibrary.Resolved kept = hair.keptNear(bob.file());
        assertTrue(kept.keep());
        assertNotEquals(hair.key(), kept.key(), "a sheet of its own beside the whole one");
        assertNotEquals(hair.cutBy(bob.file()).key(), kept.key(), "kept near it is not cut under it");
        assertEquals(hair.key(), hair.keptNear(null).key());
        // (every sheet asked for first: a stack resolved before its body is in draws the generated one, on the GPU)
        SpriteLibrary.Resolved whole = lib.resolve(lib.source("hero", Slot.HAIR, "long_waves", w.style(), false),
                AnimState.WALK, Elevation.SIDE, Facing.EAST);
        for (AnimState state : List.of(AnimState.SWIM, AnimState.WALK)) {
            lib.sheet(lib.resolve(lib.source("hero", Slot.BODY, "hero", w.style(), false), state, Elevation.SIDE,
                    Facing.EAST));
        }
        lib.sheet(kept);
        lib.sheet(whole);
        lib.awaitDecodes();
        lib.pumpAll();
        assertNotNull(lib.sheet(kept));
        LayerStack.Result r = LayerStack.resolve(lib, w, AnimState.SWIM, 0, view, new LayerStack.Memory());
        assertFalse(r.fallbackBody());
        assertEquals(List.of(Slot.BODY, Slot.HAIR), r.layers().stream().map(LayerStack.Layer::slot).toList());
        assertSame(lib.sheet(kept), r.layers().get(1).sheet(), "swimming: the sheet kept near the bob's");
        // walking, it is the whole sheet
        LayerStack.Result walk = LayerStack.resolve(lib, w, AnimState.WALK, 0, view, new LayerStack.Memory());
        assertNotSame(lib.sheet(kept), lib.sheet(whole));
        assertSame(lib.sheet(whole), walk.layers().get(1).sheet());
    }
}
