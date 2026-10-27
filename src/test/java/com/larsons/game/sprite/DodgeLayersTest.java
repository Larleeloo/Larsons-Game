package com.larsons.game.sprite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The dash and the roll: played in every stance, with whatever is in her hands drawn in them. */
class DodgeLayersTest {

    @TempDir
    Path root;
    SpriteLibrary lib;

    @AfterEach
    void close() {
        if (lib != null) lib.close();
    }

    @Test
    void theDodgesArePlayedWithWhateverIsInHand() {
        for (AnimState s : new AnimState[]{AnimState.DASH, AnimState.ROLL}) {
            assertEquals(Stance.ALL, s.stance(), s.key());
            assertFalse(s.loops(), s.key());
            assertFalse(s.handsFree(), s.key());
        }
        assertEquals(AnimState.SPRINT, AnimState.DASH.fallback());
        assertEquals(AnimState.DASH, AnimState.ROLL.fallback(), "a set with no roll dashes instead");
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        w.set(Slot.CARRY_LEFT, "round_shield");
        assertEquals(Stance.AXE, Stance.ALL.in(Stance.AXE, w));
        assertEquals(Stance.SWORD, Stance.ALL.in(null, w), "not known: the wardrobe's own");
        assertEquals(Stance.MACE, Stance.MACE.in(Stance.AXE, w), "a stance's own states draw its own");
        assertNull(Stance.ALL.attack(false));
        assertNull(Stance.ALL.parry());
        assertFalse(Stance.ALL.canJump());
    }

    @Test
    void theWeaponInHandIsDrawnInTheDash() throws Exception {
        for (String rel : List.of("body/hero", "carry_right/sword", "carry_left/round_shield",
                "carry_right/battle_axe")) {
            Path dir = root.resolve(rel);
            Files.createDirectories(dir);
            ImageIO.write(VariantsTest.sheet(), "png", dir.resolve("dash_side_e.png").toFile());
            Files.writeString(dir.resolve("profile.json"),
                    "{\"frameWidth\": 4, \"frameHeight\": 1, \"pixelArt\": true}", StandardCharsets.UTF_8);
        }
        lib = new SpriteLibrary(root, 1L << 30, 1.0, (layout, pixels, source) -> {
            pixels.free();
            return new SheetTexture(layout, SpriteLibrary.bytesOf(layout), source);
        });
        Wardrobe w = new Wardrobe();
        w.set(Slot.BODY, "hero");
        w.set(Slot.CARRY_RIGHT, "sword");
        w.set(Slot.CARRY_LEFT, "round_shield");
        assertEquals(List.of("hero", "round_shield", "sword"), drawn(w, Stance.SWORD, false));
        assertEquals(List.of("hero", "round_shield", "sword"), drawn(w, null, false));
        assertEquals(List.of("hero", "battle_axe"), drawn(w, Stance.AXE, false),
                "the axe in her hands, the sword and shield put away");
        assertEquals(List.of("hero"), drawn(w, Stance.BLADE, true), "her hands empty: nothing in them");
    }

    /** The items drawn for {@code w} dashing east holding {@code holding}, once every sheet is in. */
    private List<String> drawn(Wardrobe w, Stance holding, boolean handsEmpty) {
        SpriteView view = new SpriteView(Elevation.SIDE, Facing.EAST, Elevation.SIDE.renderAngle());
        for (String[] k : new String[][]{{"body", "hero"}, {"carry_right", "sword"}, {"carry_left", "round_shield"},
                {"carry_right", "battle_axe"}}) {
            SpriteLibrary.Resolved file = lib.resolve(lib.source("hero", Slot.byKey(k[0]), k[1], w.style(), false),
                    AnimState.DASH, Elevation.SIDE, Facing.EAST);
            if (file != null) lib.sheet(file);
        }
        lib.awaitDecodes();
        lib.pumpAll();
        LayerStack.Result r = LayerStack.resolve(lib, w, AnimState.DASH, 0, view, new LayerStack.Memory(),
                handsEmpty, holding);
        assertFalse(r.fallbackBody());
        assertEquals(AnimState.DASH, r.state());
        return r.layers().stream().map(LayerStack.Layer::item).sorted((a, b) -> a.equals("hero") ? -1
                : b.equals("hero") ? 1 : a.compareTo(b)).toList();
    }
}
