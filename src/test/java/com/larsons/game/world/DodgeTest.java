package com.larsons.game.world;

import com.larsons.game.combat.Dummy;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** The dodge key: a tap dashes, held it turns into the combat roll. */
class DodgeTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);

    private static Wardrobe swordAndShield() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        w.set(Slot.CARRY_LEFT, "round_shield");
        return w;
    }

    /** A frame of controls: moving {@code moveX} to the side, the dodge key gone down and/or held. */
    private static Player.Intent dodge(double moveX, boolean pressed, boolean held) {
        return new Player.Intent(moveX, 0, false, false, false, false).withDodge(pressed, held);
    }

    private static final Player.Intent LET_GO = dodge(0, false, false);
    private static final Player.Intent HELD = dodge(0, false, true);

    private static void run(Player p, int frames, Player.Intent i) {
        for (int f = 0; f < frames; f++) p.update(DT, i, DUR);
    }

    /** Frames until the dodge under way is over (at most two seconds' worth). */
    private static int finish(Player p, Player.Intent i) {
        int n = 0;
        while (p.dodging() && n < 120) {
            p.update(DT, i, DUR);
            n++;
        }
        return n;
    }

    @Test
    void aTapDashes() {
        Player p = new Player(swordAndShield());
        p.snapHeading(0);                                   // facing -z
        p.update(DT, dodge(0, true, true), DUR);
        assertEquals(AnimState.DASH, p.state());
        assertTrue(p.dodging());
        assertFalse(p.invulnerable(), "a dash is no roll");
        run(p, 2, HELD);
        run(p, 1, LET_GO);                                  // let go well before the push-off is over
        boolean ghosts = false;
        while (p.dodging()) {
            ghosts |= p.afterimages();
            assertEquals(AnimState.DASH, p.state(), "it stays a dash");
            p.update(DT, LET_GO, DUR);
        }
        assertTrue(ghosts, "the burst leaves afterimages");
        assertEquals(3.5, Player.dodgeDistance(AnimState.DASH), 0.1);
        assertEquals(Player.dodgeDistance(AnimState.DASH), -p.ground().z(), 0.15, "the way she faces");
        assertEquals(0, p.ground().x(), 1e-9);
        run(p, 30, Player.Intent.NONE);
        assertEquals(AnimState.IDLE, p.state());
    }

    @Test
    void heldThroughThePushOffItTurnsIntoTheRollAtTheSameFrame() {
        Player p = new Player(swordAndShield());
        p.snapHeading(0);
        p.update(DT, dodge(0, true, true), DUR);
        double shared = AnimState.DODGE_SHARED / AnimState.DEFAULT_FPS;
        while (p.state() == AnimState.DASH) {
            assertTrue(p.stateTime() < shared + 1e-9, "a dash past its push-off");
            p.update(DT, HELD, DUR);
        }
        assertEquals(AnimState.ROLL, p.state());
        assertEquals(shared, p.stateTime(), DT + 1e-9, "carried on from the frame the dash had reached");
        assertTrue(p.passesThrough());
        boolean safe = false;
        while (p.dodging()) {
            safe |= p.invulnerable();
            p.update(DT, LET_GO, DUR);
        }
        assertTrue(safe, "nothing can hurt her in the roll");
        assertFalse(p.invulnerable());
        double roll = Player.dodgeDistance(AnimState.ROLL);
        assertTrue(roll > Player.dodgeDistance(AnimState.DASH) + 0.8, "a roll goes further than a dash");
        assertEquals(roll, -p.ground().z(), 0.15);
    }

    @Test
    void sheDodgesTheWaySheMovesTurningToItAtOnce() {
        Player p = new Player(swordAndShield());
        p.snapHeading(0);
        p.update(DT, dodge(1, true, false), DUR);
        double h = SpriteView.headingOf(1, 0);
        assertEquals(h, p.heading(), 1e-9);
        finish(p, dodge(1, false, false));
        assertEquals(Player.dodgeDistance(AnimState.DASH), p.ground().x(), 0.15);
        assertEquals(0, p.ground().z(), 1e-6);
    }

    @Test
    void notInTheAirNorMidSwingAndOnlyOnceTheLastHasCooledDown() {
        Player p = new Player(swordAndShield());
        run(p, 1, new Player.Intent(0, 0, false, false, true, false));
        run(p, 12, Player.Intent.NONE);
        assertTrue(p.height() > 0);
        run(p, 1, dodge(0, true, false));
        assertFalse(p.dodging(), "not in the air");
        run(p, 90, Player.Intent.NONE);
        run(p, 1, new Player.Intent(0, 0, false, false, false, true));
        assertEquals(AnimState.ATTACK, p.state());
        run(p, 1, dodge(0, true, false));
        assertFalse(p.dodging(), "not in the middle of a swing");
        run(p, 60, Player.Intent.NONE);

        run(p, 1, dodge(0, true, false));
        assertTrue(p.dodging());
        finish(p, LET_GO);
        assertEquals(Player.DODGE_COOLDOWN, p.dodgeWait(), 1e-9);
        run(p, 1, dodge(0, true, false));
        assertFalse(p.dodging(), "cooling down");
        run(p, (int) Math.ceil(Player.DODGE_COOLDOWN / DT), Player.Intent.NONE);
        run(p, 1, dodge(0, true, false));
        assertTrue(p.dodging(), "cooled down");
        finish(p, LET_GO);
        p.setDodgeCooldown(0);
        run(p, 1, dodge(0, true, false));
        assertTrue(p.dodging(), "no wait at all");
    }

    @Test
    void crouchedOrBehindTheShieldSheStillDodges() {
        Player p = new Player(swordAndShield());
        run(p, 1, new Player.Intent(0, 0, false, false, false, false, false, true, false, false, false, false, false,
                null));
        assertTrue(p.crouched());
        run(p, 1, dodge(0, true, false));
        assertEquals(AnimState.DASH, p.state());
        assertFalse(p.crouched(), "she comes up out of it standing");
        finish(p, LET_GO);
        run(p, 30, Player.Intent.NONE);

        Player.Intent block = new Player.Intent(0, 0, false, false, false, false, false, false, false, false, false,
                true, false, null);
        run(p, 5, block);
        assertEquals(AnimState.SHIELD_READY, p.state());
        run(p, 1, block.withDodge(true, false));
        assertEquals(AnimState.DASH, p.state());
        finish(p, block);
        run(p, 1, block);
        assertEquals(AnimState.SHIELD_READY, p.state(), "and back behind it");
    }

    @Test
    void aDodgeCutsAnEmoteShortAndGoesOnWhateverIsTakenUp() {
        Player p = new Player(swordAndShield());
        run(p, 1, new Player.Intent(0, 0, false, false, false, false, false, false, false, false, false, false,
                false, AnimState.EMOTE_LAUGH));
        assertEquals(AnimState.EMOTE_LAUGH, p.state());
        run(p, 1, dodge(0, true, true));
        assertEquals(AnimState.DASH, p.state());
        p.setStance(Stance.AXE);
        run(p, 1, HELD);
        assertTrue(p.dodging(), "swapping weapons does not stop it");
        assertEquals(Stance.AXE, p.stance());
        p.setStance(Stance.ALL);
        assertEquals(Stance.AXE, p.stance(), "no stance holds 'whatever is in hand'");
    }

    @Test
    void sheRollsThroughADummyButADashStopsAtIt() {
        for (boolean roll : new boolean[]{false, true}) {
            World w = new World(swordAndShield());
            w.addDummy(new Dummy(new Vec3(0, 0, -2), 0, 100, 6));
            Player p = w.player();
            p.snapHeading(0);
            p.update(DT, dodge(0, true, roll), DUR);
            w.tick(DT, null);
            for (int f = 0; f < 120 && p.dodging(); f++) {
                p.update(DT, dodge(0, false, roll), DUR);
                w.tick(DT, null);
            }
            if (roll) {
                assertTrue(p.ground().z() < -2 - Dummy.RADIUS, "rolled through, out the other side: " + p.ground());
            } else {
                assertTrue(p.ground().z() >= -2 + Dummy.RADIUS - 1e-6, "stopped at it: " + p.ground());
            }
        }
    }
}
