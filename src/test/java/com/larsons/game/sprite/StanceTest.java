package com.larsons.game.sprite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The stances, the states they play, and the fallbacks between states. */
class StanceTest {

    @TempDir
    Path root;
    SpriteLibrary lib;

    @AfterEach
    void close() {
        if (lib != null) lib.close();
    }

    @Test
    void everyFallbackChainEndsInTheFirstSix() {
        int roots = 0;
        for (AnimState s : AnimState.values()) {
            if (s.fallback() == null) {
                roots++;
            } else {
                assertNotNull(AnimState.byKey(s.fallback().key()), s.key());
            }
            AnimState r = s.root();
            assertNull(r.fallback(), s.key());
            assertTrue(r.ordinal() < 6, s.key() + " ends in " + r.key());
        }
        assertEquals(6, roots);
        assertEquals(AnimState.IDLE, AnimState.AXE_BLOCK.root());
        assertEquals(AnimState.WALK, AnimState.CROSSBOW_CROUCH_WALK.root());
    }

    @Test
    void eachStancePlaysItsOwnStates() {
        for (Stance st : new Stance[]{Stance.SWORD, Stance.AXE, Stance.BOW, Stance.CROSSBOW}) {
            for (boolean crouched : new boolean[]{false, true}) {
                assertEquals(st, st.idle(crouched).stance(), st + " idle");
                assertEquals(st, st.attack(crouched).stance(), st + " attack");
                for (Stance.Pace pace : Stance.Pace.values()) {
                    AnimState m = st.moving(pace, crouched);
                    assertEquals(st, m.stance(), st + " " + pace);
                    assertTrue(m.speed() > 0, m.key());
                    assertTrue(m.loops(), m.key());
                }
            }
            assertEquals(st, st.parry().stance());
            assertEquals(st, st.jump().stance());
            assertEquals(AnimState.JUMP, st.jump().root());
            assertEquals(st, st.block().stance());
            assertTrue(st.block().loops());
        }
        assertEquals(AnimState.CROUCH_WALK_FAST, Stance.SWORD.moving(Stance.Pace.RUN, true));
        assertEquals(AnimState.AXE_CROUCH_ATTACK, Stance.AXE.attack(true));
        assertEquals(AnimState.BOW_CROUCH_FIRE, Stance.BOW.release(true));
        assertNull(Stance.AXE.release(false));
        assertNull(Stance.BOW.spin());
        assertEquals(AnimState.AXE_HEAVY_ATTACK, Stance.AXE.heavy());
        assertTrue(Stance.SWORD.canJump());
        assertEquals(AnimState.JUMP, Stance.SWORD.jump());
        assertEquals(AnimState.CROSSBOW_JUMP, Stance.CROSSBOW.jump());
        assertFalse(Stance.FREE.canJump());
        for (AnimState s : new AnimState[]{AnimState.PICKUP, AnimState.CROUCH_PICKUP, AnimState.EMOTE_LAUGH,
                AnimState.EMOTE_CRY, AnimState.EMOTE_SURPRISE, AnimState.EMOTE_ANGRY}) {
            assertTrue(s.handsFree(), s.key());
            assertFalse(s.loops(), s.key());
        }
    }

    @Test
    void theSwordAloneHasItsOwnStatesAndNoShieldMoves() {
        Stance st = Stance.BLADE;
        for (boolean crouched : new boolean[]{false, true}) {
            assertEquals(st, st.idle(crouched).stance());
            assertEquals(AnimState.BLADE_ATTACK, st.attack(crouched), "she stands to swing");
            for (Stance.Pace pace : Stance.Pace.values()) {
                AnimState m = st.moving(pace, crouched);
                assertEquals(st, m.stance(), pace.toString());
                assertTrue(m.speed() > 0 && m.loops(), m.key());
            }
        }
        assertEquals(AnimState.BLADE_PARRY, st.parry());
        assertEquals(AnimState.BLADE_SPIN_ATTACK, st.spin());
        assertEquals(AnimState.BLADE_JUMP, st.jump());
        assertEquals(AnimState.JUMP, st.jump().root());
        assertNull(st.block(), "no shield to raise");
        assertNull(st.bash(), "nor to bash with");
        assertNull(st.heavy());
        // each falls back on the sword and shield's own move (a set drawn before they existed)
        for (AnimState s : AnimState.values()) {
            if (s.stance() != Stance.BLADE) continue;
            assertEquals(s.key(), "blade_" + s.fallback().key());
            assertEquals(Stance.SWORD, s.fallback().stance());
        }
        // which of the two the wardrobe's carried things are held in
        Wardrobe w = new Wardrobe();
        assertEquals(Stance.BLADE, Stance.ofWardrobe(w), "nothing in hand");
        w.set(Slot.CARRY_RIGHT, "sword");
        assertEquals(Stance.BLADE, Stance.ofWardrobe(w));
        assertEquals("sword", Stance.BLADE.carried(Slot.CARRY_RIGHT, w));
        w.set(Slot.CARRY_LEFT, "round_shield_px128");
        assertEquals(Stance.SWORD, Stance.ofWardrobe(w), "a shield in any style");
        w.clear(Slot.CARRY_LEFT);
        w.set(Slot.CARRY_RIGHT, "round_shield");
        assertEquals(Stance.SWORD, Stance.ofWardrobe(w), "a left-handed character's shield is in the right hand");
    }

    @Test
    void whatIsDrawnInTheHandsFollowsTheStance() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        w.set(Slot.CARRY_LEFT, "round_shield");
        assertEquals("sword", Stance.SWORD.carried(Slot.CARRY_RIGHT, w));
        assertEquals("round_shield", Stance.SWORD.carried(Slot.CARRY_LEFT, w));
        // the axe and the crossbow in the right hand, the bow in the left; the other hand empty
        assertEquals("battle_axe", Stance.AXE.carried(Slot.CARRY_RIGHT, w));
        assertNull(Stance.AXE.carried(Slot.CARRY_LEFT, w));
        assertEquals("longbow", Stance.BOW.carried(Slot.CARRY_LEFT, w));
        assertNull(Stance.BOW.carried(Slot.CARRY_RIGHT, w));
        assertNull(Stance.FREE.carried(Slot.CARRY_RIGHT, w));
        // a left-handed character the other way round
        w.setHand(Wardrobe.Hand.LEFT);
        assertEquals("battle_axe", Stance.AXE.carried(Slot.CARRY_LEFT, w));
        assertEquals("longbow", Stance.BOW.carried(Slot.CARRY_RIGHT, w));
        assertEquals(Stance.AXE, Stance.ofItem("battle_axe_px128_lh"));
        assertNull(Stance.ofItem("sword"));
    }

    private void png(String rel) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        ImageIO.write(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB), "png", p.toFile());
    }

    @Test
    void aStateWithoutSheetsIsShownAsTheFirstOfItsFallbacksThatHasThem() throws Exception {
        png("body/hero/idle_middle_s.png");
        png("body/hero/crouch_idle_middle_s.png");
        lib = new SpriteLibrary(root, 1L << 30, 1.0);
        SpriteLibrary.Source body = lib.source(Slot.BODY, "hero", Wardrobe.Style.RENDERED, false);
        Elevation m = Elevation.MIDDLE;
        assertEquals(AnimState.CROUCH_IDLE, LayerStack.shown(lib, body, AnimState.CROUCH_IDLE, m, Facing.SOUTH));
        // the axe's crouch falls back to the sword's crouch, which this set has
        assertEquals(AnimState.CROUCH_IDLE, LayerStack.shown(lib, body, AnimState.AXE_CROUCH_IDLE, m, Facing.SOUTH));
        // the axe's block: axe idle, then idle
        assertEquals(AnimState.IDLE, LayerStack.shown(lib, body, AnimState.AXE_BLOCK, m, Facing.SOUTH));
        // no sheets at all from here: the chain's end
        assertEquals(AnimState.IDLE, LayerStack.shown(lib, body, AnimState.AXE_BLOCK, Elevation.TOP, Facing.NORTH));
        // the generated body knows only the first six
        assertEquals(AnimState.WALK, LayerStack.shown(lib, null, AnimState.BOW_CROUCH_WALK, m, Facing.SOUTH));
    }

    @Test
    void manyWordStateNamesAreRead() {
        assertEquals(AnimState.CROUCH_WALK_FAST, SpriteNames.parse("crouch_walk_fast_middle_ne.png").state());
        assertEquals(AnimState.CROUCH_WALK, SpriteNames.parse("CrouchWalk-45-East.png").state());
        assertEquals(AnimState.AXE_HEAVY_ATTACK, SpriteNames.parse("axe_heavy_attack_top_s.png").state());
        assertEquals(AnimState.CROSSBOW_CROUCH_FIRE, SpriteNames.parse("Crossbow Crouch Fire 0 W.png").state());
        assertEquals(AnimState.PICKUP, SpriteNames.parse("pick-up_side_s.png").state());
        assertEquals(AnimState.EMOTE_LAUGH, SpriteNames.parse("laugh_side_s.png").state());
        SpriteNames.Parsed p = SpriteNames.parse("bow_crouch_draw_side_sw.png");
        assertEquals(AnimState.BOW_CROUCH_DRAW, p.state());
        assertEquals(Facing.SOUTH_WEST, p.facing());
    }
}
