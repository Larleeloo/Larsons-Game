package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** The player's state machine beyond walking and swinging: crouching, stances, actions, pick-ups. */
class StancesAndActionsTest {

    /** The wardrobe's sword in the right hand and the shield on the left arm: the sword-and-shield stance. */
    private static Wardrobe swordAndShield() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        w.set(Slot.CARRY_LEFT, "round_shield");
        return w;
    }

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);

    /** One frame of controls, by name: move, run, attack, held, crouch, heavy, spin, parry, block, bash. */
    private static Player.Intent in(double moveX, String... keys) {
        java.util.Set<String> k = java.util.Set.of(keys);
        return new Player.Intent(moveX, 0, k.contains("run"), k.contains("sprint"), k.contains("jump"),
                k.contains("attack"), k.contains("held") || k.contains("attack"), k.contains("crouch"),
                k.contains("heavy"), k.contains("spin"), k.contains("parry"), k.contains("block"),
                k.contains("bash"), null);
    }

    private static void run(Player p, int frames, Player.Intent i) {
        for (int f = 0; f < frames; f++) p.update(DT, i, DUR);
    }

    @Test
    void crouchingTogglesAndHasItsOwnWalks() {
        Player p = new Player(swordAndShield());
        run(p, 1, in(0, "crouch"));
        run(p, 5, Player.Intent.NONE);
        assertEquals(AnimState.CROUCH_IDLE, p.state());
        run(p, 60, in(1));
        assertEquals(AnimState.CROUCH_WALK, p.state());
        assertEquals(AnimState.CROUCH_WALK.speed(), p.speed(), 0.05);
        run(p, 60, in(1, "run"));
        assertEquals(AnimState.CROUCH_WALK_FAST, p.state());
        run(p, 60, in(1, "sprint"));
        assertEquals(AnimState.SPRINT, p.state(), "sprinting stands her up");
        assertFalse(p.crouched());
    }

    @Test
    void aWeaponPickedUpTakesUpItsStanceAndDroppingItGoesBack() {
        Wardrobe shield = new Wardrobe();
        shield.set(Slot.CARRY_LEFT, "round_shield");
        World w = new World(shield);
        w.spawn(ItemDef.BATTLE_AXE, new Vec3(1, 0, 0));
        w.pickUp(w.reachable());
        Player p = w.player();
        assertEquals(Stance.AXE, p.stance());
        assertNull(p.wardrobe().get(com.larsons.game.sprite.Slot.CARRY_RIGHT), "held, not worn");
        run(p, 5, Player.Intent.NONE);
        assertEquals(AnimState.AXE_IDLE, p.state());
        run(p, 1, in(0, "attack"));
        assertEquals(AnimState.AXE_ATTACK, p.state());
        run(p, 120, Player.Intent.NONE);
        run(p, 1, in(0, "heavy"));
        assertEquals(AnimState.AXE_HEAVY_ATTACK, p.state());
        run(p, 120, Player.Intent.NONE);
        run(p, 1, in(0, "jump"));
        assertEquals(AnimState.AXE_JUMP, p.state(), "the axe's own jump");
        double peak = 0;
        for (int i = 0; i < 120; i++) {
            p.update(DT, Player.Intent.NONE, DUR);
            peak = Math.max(peak, p.height());
        }
        assertTrue(peak > 0.2, "left the ground: " + peak);
        assertEquals(AnimState.AXE_IDLE, p.state());
        assertEquals(ItemDef.BATTLE_AXE, w.dropHeld());
        assertEquals(Stance.SWORD, p.stance());
        assertFalse(w.wield(Stance.AXE), "not carried any more");
        assertTrue(w.wield(Stance.SWORD));
    }

    @Test
    void theBowIsDrawnWhileHeldAndLoosedOnRelease() {
        Player p = new Player(swordAndShield());
        p.setStance(Stance.BOW);
        run(p, 1, in(0, "attack"));
        assertEquals(AnimState.BOW_DRAW, p.state());
        run(p, 120, in(0, "held"));                         // well past the draw's length: held at full draw
        assertEquals(AnimState.BOW_DRAW, p.state());
        run(p, 1, Player.Intent.NONE);
        assertEquals(AnimState.BOW_FIRE, p.state());
        run(p, 120, Player.Intent.NONE);
        assertEquals(AnimState.BOW_IDLE, p.state());
        // let go early: the string let down, no shot
        run(p, 1, in(0, "attack"));
        run(p, 3, in(0, "held"));
        run(p, 1, Player.Intent.NONE);
        assertEquals(AnimState.BOW_IDLE, p.state());
    }

    @Test
    void blockingIsHeldAndTheShieldBashesFromBehindIt() {
        Player p = new Player(swordAndShield());
        run(p, 10, in(0, "block"));
        assertEquals(AnimState.SHIELD_READY, p.state());
        run(p, 10, in(1, "block"));
        assertEquals(AnimState.SHIELD_READY, p.state(), "no walking while blocking");
        assertEquals(0, p.speed(), 1e-3);
        run(p, 1, in(0, "block", "attack"));
        assertEquals(AnimState.SHIELD_BASH, p.state());
        run(p, 120, Player.Intent.NONE);
        run(p, 1, in(0, "spin"));
        assertEquals(AnimState.SPIN_ATTACK, p.state());
        run(p, 120, Player.Intent.NONE);
        run(p, 1, in(0, "parry"));
        assertEquals(AnimState.PARRY, p.state());
    }

    @Test
    void withNoShieldTheSwordIsHeldAloneInTheFirstVersionsMoves() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        Player p = new Player(w);
        assertEquals(Stance.BLADE, p.stance());
        run(p, 5, Player.Intent.NONE);
        assertEquals(AnimState.BLADE_IDLE, p.state());
        run(p, 60, in(1));
        assertEquals(AnimState.BLADE_WALK, p.state());
        run(p, 60, in(1, "run"));
        assertEquals(AnimState.BLADE_RUN, p.state());
        run(p, 120, Player.Intent.NONE);
        run(p, 10, in(0, "block"));
        assertEquals(AnimState.BLADE_IDLE, p.state(), "no shield to raise");
        run(p, 1, in(0, "block", "attack"));
        assertEquals(AnimState.BLADE_ATTACK, p.state(), "no shield to bash with: she swings");
        run(p, 120, Player.Intent.NONE);
        run(p, 1, in(0, "spin"));
        assertEquals(AnimState.BLADE_SPIN_ATTACK, p.state());
        run(p, 120, Player.Intent.NONE);
        run(p, 1, in(0, "crouch"));
        run(p, 5, Player.Intent.NONE);
        assertEquals(AnimState.BLADE_CROUCH_IDLE, p.state());
        run(p, 1, in(0, "crouch"));
        // a shield taken up: the sword and shield's moves
        w.set(Slot.CARRY_LEFT, "round_shield");
        assertEquals(Stance.SWORD, p.stance());
        run(p, 5, Player.Intent.NONE);
        assertEquals(AnimState.IDLE, p.state());
        run(p, 10, in(0, "block"));
        assertEquals(AnimState.SHIELD_READY, p.state());
        // and put down again
        w.clear(Slot.CARRY_LEFT);
        run(p, 5, Player.Intent.NONE);
        assertEquals(AnimState.BLADE_IDLE, p.state());
        // nothing in either hand is no shield either
        w.clear(Slot.CARRY_RIGHT);
        assertEquals(Stance.BLADE, p.stance());
        // asking for the sword alone is asking for the wardrobe's stance
        p.setStance(Stance.BLADE);
        w.set(Slot.CARRY_LEFT, "round_shield");
        assertEquals(Stance.SWORD, p.stance());
    }

    @Test
    void anEmotePlaysOnceAndMovingCutsItShort() {
        Player p = new Player(swordAndShield());
        p.update(DT, new Player.Intent(0, 0, false, false, false, false, false, false, false, false, false,
                false, false, AnimState.EMOTE_LAUGH), DUR);
        assertEquals(AnimState.EMOTE_LAUGH, p.state());
        run(p, 20, Player.Intent.NONE);
        assertEquals(AnimState.EMOTE_LAUGH, p.state());
        run(p, 30, in(1));
        assertEquals(AnimState.WALK, p.state());
    }

    @Test
    void aPickUpTakesTheItemAsTheHandClosesOnIt() {
        Player p = new Player(swordAndShield());
        boolean[] grabbed = {false};
        assertTrue(p.pickUp(() -> grabbed[0] = true, DUR));
        assertEquals(AnimState.PICKUP, p.state());
        run(p, 5, Player.Intent.NONE);
        assertFalse(grabbed[0], "still reaching");
        assertFalse(p.pickUp(() -> { }, DUR), "busy");
        run(p, 30, Player.Intent.NONE);
        assertTrue(grabbed[0]);
        run(p, 60, Player.Intent.NONE);
        assertEquals(AnimState.IDLE, p.state());
        // crouched, the crouched one
        run(p, 1, in(0, "crouch"));
        assertTrue(p.pickUp(() -> { }, DUR));
        assertEquals(AnimState.CROUCH_PICKUP, p.state());
    }
}
