package com.larsons.game.combat;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Player;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;

import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** The flying strike dummy: its rounds, its dive at her and what she can do about it, its fall and its rising. */
class FlyingDummyTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);

    private static Wardrobe sword(boolean shield) {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        if (shield) w.set(Slot.CARRY_LEFT, "round_shield");
        return w;
    }

    private static void run(Player p, int frames, Player.Intent i) {
        for (int f = 0; f < frames; f++) p.update(DT, i, DUR);
    }

    private static Player.Intent controls(boolean parry, boolean block) {
        return new Player.Intent(0, 0, false, false, false, false, false, false, false, false, parry, block, false,
                null);
    }

    @Test
    void onItsRoundsItFliesOutOfASwordsReach() {
        World w = new World(sword(false));
        FlyingDummy f = w.addFlier(new FlyingDummy(new Vec3(30, 0, 30), 0, 50, 3, Terrain.FLAT));
        for (int i = 0; i < 600; i++) {
            w.player().update(DT, Player.Intent.NONE, DUR);
            w.tick(DT);
            assertEquals(FlyingDummy.Phase.FLY, f.phase(), "she is far off: it keeps to its rounds");
            assertEquals(FlyingDummy.ALTITUDE, f.position().y(), 0.4, "at its height");
            assertEquals(FlyingDummy.RADIUS_OF_ROUNDS, f.position().horizontalDistance(f.centre()), 1.0, "round its middle");
        }
        assertTrue(f.flies());
        assertTrue(f.velocity().horizontalLength() > FlyingDummy.SPEED * 0.7, "on the wing");
        assertEquals("fly", f.sheet());
        assertTrue(f.loops());
    }

    @Test
    void nearHerItScreechesThenDivesThroughHerAndClimbsAway() {
        World w = new World(sword(false));
        Player p = w.player();
        FlyingDummy f = w.addFlier(new FlyingDummy(new Vec3(3, 0, 0), 0, 50, 3, Terrain.FLAT));
        boolean warned = false, swooped = false, said = false;
        for (int i = 0; i < 600 && !said; i++) {
            p.update(DT, Player.Intent.NONE, DUR);
            w.tick(DT);
            warned |= f.phase() == FlyingDummy.Phase.WARN;
            swooped |= f.phase() == FlyingDummy.Phase.SWOOP;
            if (f.phase() == FlyingDummy.Phase.SWOOP) assertTrue(warned, "a screech first");
            said = w.combat().popups().stream().anyMatch(pp -> pp.text().startsWith("Swooped!"));
        }
        assertTrue(warned && swooped);
        assertTrue(said, "it struck her");
        assertEquals(Player.MAX_HEALTH - FlyingDummy.SWOOP_DAMAGE, p.health(), 1e-9);
        for (int i = 0; i < 180 && f.phase() == FlyingDummy.Phase.SWOOP; i++) {
            p.update(DT, Player.Intent.NONE, DUR);
            w.tick(DT);
        }
        assertEquals(FlyingDummy.Phase.FLY, f.phase(), "up and away");
        assertTrue(f.position().y() > 2, "climbed back out of reach: " + f.position().y());
        assertEquals(Player.MAX_HEALTH - FlyingDummy.SWOOP_DAMAGE, p.health(), 1e-9, "once a dive");
    }

    @Test
    void sheRollsThroughItBlocksHalfOfItOrParriesItAway() {
        FlyingDummy f = new FlyingDummy(new Vec3(3, 0, 0), 0, 50, 3, Terrain.FLAT);

        Player rolling = new Player(sword(false));
        Player.Intent roll = Player.Intent.NONE.withDodge(true, true);
        for (int i = 0; i < 60 && !rolling.invulnerable(); i++) rolling.update(DT, i == 0 ? roll : roll.withDodge(false, true), DUR);
        assertTrue(rolling.invulnerable(), "into the roll");
        assertEquals(FlyingDummy.Outcome.DODGED, f.strike(rolling));
        assertEquals(Player.MAX_HEALTH, rolling.health(), 1e-9);

        Player blocking = new Player(sword(true));
        run(blocking, 10, controls(false, true));
        assertEquals(AnimState.SHIELD_READY, blocking.state());
        assertEquals(FlyingDummy.Outcome.BLOCKED, f.strike(blocking));
        assertEquals(Player.MAX_HEALTH - FlyingDummy.SWOOP_DAMAGE / 2, blocking.health(), 1e-9, "half");

        Player parrying = new Player(sword(false));
        run(parrying, 1, controls(true, false));
        run(parrying, 3, Player.Intent.NONE);
        assertTrue(parrying.action().key().contains("parry"));
        assertEquals(FlyingDummy.Outcome.PARRIED, f.strike(parrying));
        assertEquals(Player.MAX_HEALTH, parrying.health(), 1e-9, "unhurt");
        assertEquals(FlyingDummy.Phase.HIT, f.phase(), "knocked back");
        assertTrue(f.sheet().startsWith("hit_"));

        Player standing = new Player(sword(false));
        assertEquals(FlyingDummy.Outcome.STRUCK, f.strike(standing));
        assertEquals(Player.MAX_HEALTH - FlyingDummy.SWOOP_DAMAGE, standing.health(), 1e-9);
    }

    @Test
    void knockedOutOfTheAirItFallsLiesThereThenBeatsBackUpWhole() {
        World w = new World(sword(false));
        Player p = w.player();
        FlyingDummy f = w.addFlier(new FlyingDummy(new Vec3(0, 0, -25), 0, 10, 2, Terrain.FLAT));
        f.hurt(4, p.feet(), true);
        assertEquals(6, f.health(), 1e-9);
        assertEquals(FlyingDummy.Phase.HIT, f.phase());
        assertTrue(f.flash() > 0.9, "flashes white");
        f.hurt(10, p.feet(), true);
        assertEquals(0, f.health(), 1e-9);
        assertEquals(FlyingDummy.Phase.FALL, f.phase());
        assertFalse(f.alive(), "no more to hit");
        assertTrue(f.sheet().startsWith("fall_"));
        assertTrue(w.combat().locked() == null);

        double seen = 0;
        FlyingDummy.Phase last = f.phase();
        StringBuilder order = new StringBuilder(last.key());
        for (int i = 0; i < 60 * 8 && !(last == FlyingDummy.Phase.FLY); i++) {
            p.update(DT, Player.Intent.NONE, DUR);
            w.tick(DT);
            if (f.phase() == FlyingDummy.Phase.DOWN) {
                assertEquals(0, f.position().y(), 1e-9, "lying on the ground");
                assertFalse(f.flies());
                seen += DT;
            }
            if (f.phase() != last) order.append(' ').append((last = f.phase()).key());
        }
        assertEquals("fall down rise fly", order.toString());
        assertEquals(2, seen, 0.05, "down for its while");
        assertEquals(f.maxHealth(), f.health(), 1e-9, "whole again");
        assertTrue(f.alive());
    }

    @Test
    void aShotIsLedToWhereAFlierWillBe() {
        FlyingDummy f = new FlyingDummy(new Vec3(0, 0, -20), 0, 10, 2, Terrain.FLAT);
        Player p = new Player(sword(false));
        for (int i = 0; i < 120; i++) f.tick(DT, Terrain.FLAT, p);
        Vec3 hand = new Vec3(0, 1.35, 0), v = f.velocity();
        assertTrue(v.length() > 1);
        Vec3 aim = f.position().add(0, f.height() * 0.62, 0);
        Vec3 led = Combat.lead(hand, f, 30);
        double flight = hand.distance(led) / 30;
        assertEquals(0, led.distance(aim.add(v.scale(flight))), 0.05, "where it will be when the arrow gets there");
        Dummy still = new Dummy(new Vec3(0, 0, -10), 0, 10, 2);
        assertEquals(still.position().add(0, still.height() * 0.62, 0), Combat.lead(hand, still, 30), "a still one: at it");
    }
}
