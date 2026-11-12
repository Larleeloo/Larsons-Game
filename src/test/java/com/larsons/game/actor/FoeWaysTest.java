package com.larsons.game.actor;

import com.larsons.game.ambient.Animals;
import com.larsons.game.math.Vec3;
import com.larsons.game.world.Biome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** How the foes get about: round walls, never up a cliff, and seeing only what is in their sight. */
class FoeWaysTest {

    /** A place: flat ground, a wall from {@code (wx0, wz0)} to {@code (wx1, wz1)} {@code tall} high, and a cliff. */
    private static Animals.Place place(double wx0, double wz0, double wx1, double wz1, double tall,
                                       java.util.function.DoubleBinaryOperator ground) {
        return new Animals.Place() {
            @Override
            public double ground(double x, double z) { return ground.applyAsDouble(x, z); }

            @Override
            public double water(double x, double z, double g) { return 0; }

            @Override
            public Biome biome(double x, double z) { return Biome.PLAINS; }

            @Override
            public boolean blocked(double x, double z, double r) {
                return x > wx0 - r && x < wx1 + r && z > wz0 - r && z < wz1 + r;
            }

            @Override
            public boolean sightBlocked(double x, double z, double y) {
                return y < tall && x > wx0 && x < wx1 && z > wz0 && z < wz1;
            }
        };
    }

    /** She, standing still at {@code at}. */
    private static final class Her implements Animals.Quarry {
        Vec3 at;
        double took;

        Her(Vec3 at) { this.at = at; }

        @Override public Vec3 feet() { return at; }
        @Override public boolean dodging() { return false; }
        @Override public boolean parrying() { return false; }
        @Override public boolean blocking() { return false; }
        @Override public double hurt(double d) { took += d; return d; }
        @Override public boolean down() { return false; }
    }

    private static void run(Folks folks, Her her, Animals.Place place, double seconds) {
        for (double t = 0; t < seconds; t += 1 / 30.0) folks.tick(1 / 30.0, her, place);
    }

    @Test
    void aFoeAfterHerGoesRoundAWallNotIntoIt() {
        // a wall 16 m long across the way between them, open at its ends
        Animals.Place place = place(-8, -0.3, 8, 0.3, 3, (x, z) -> 0);
        Folks folks = new Folks();
        Folk f = folks.spawn(Breed.BANDIT, new Vec3(0, 0, -6), 0, Folks.Spec.ANY.weapon("sword"));
        f.alerted = true;
        f.relentless = true;                     // (it knows where she is: the way is what is tested)
        Her her = new Her(new Vec3(0, 0, 6));
        run(folks, her, place, 14);
        assertTrue(f.position().z() > 0.5, "round the wall to her side: " + f.position());
        assertTrue(f.position().horizontalDistance(her.at) < 3, "and up to her: " + f.position());
        assertTrue(her.took > 0, "and at her");
    }

    /** {@code f} as the game's ground has it: heights every half metre, and between them the slope from one to the next. */
    private static java.util.function.DoubleBinaryOperator grid(java.util.function.DoubleBinaryOperator f) {
        return (x, z) -> {
            double gx = Math.floor(x / 0.5) * 0.5, gz = Math.floor(z / 0.5) * 0.5;
            double tx = (x - gx) / 0.5, tz = (z - gz) / 0.5;
            double a = f.applyAsDouble(gx, gz), b = f.applyAsDouble(gx + 0.5, gz);
            double c = f.applyAsDouble(gx, gz + 0.5), d = f.applyAsDouble(gx + 0.5, gz + 0.5);
            return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
        };
    }

    @Test
    void nobodyWalksUpACliffTheyGoRoundByTheRamp() {
        // a sheer 4 m step up at z = 0 from x = -30 to 4; past x = 4 a ramp goes up gently
        Animals.Place place = place(100, 100, 101, 101, 0, grid((x, z) -> {
            if (z <= 0) return 0;
            if (x < 4) return 4;
            return Math.min(4, z * 0.4);                   // (the ramp: 0.4 rise a metre)
        }));
        Folks folks = new Folks();
        Folk f = folks.spawn(Breed.KHORIGALE, new Vec3(0, 0, -2), 0, Folks.Spec.ANY.weapon("mace"));
        f.alerted = true;
        f.relentless = true;
        Her her = new Her(new Vec3(0, 4, 4));
        // straight at the cliff: it does not go up it
        for (double t = 0; t < 2; t += 1 / 30.0) {
            folks.tick(1 / 30.0, her, place);
            assertTrue(f.position().y() < 0.8 || f.position().x() > 3, "up the cliff at " + f.position());
        }
        run(folks, her, place, 24);
        assertEquals(4, f.position().y(), 0.3, "up on top, by the ramp: " + f.position());
        assertTrue(f.position().horizontalDistance(her.at) < 3, "and up to her: " + f.position());
    }

    @Test
    void itDoesNotSeeHerThroughAWallNorBehindItButSeesHerInTheOpen() {
        Animals.Place walled = place(-4, -0.3, 4, 0.3, 3, (x, z) -> 0);
        Folks folks = new Folks();
        Folk f = folks.spawn(Breed.BANDIT, new Vec3(0, 0, -5), 0, Folks.Spec.ANY.weapon("sword"));
        f.roam = 0;                                                         // (it stands its ground)
        f.heading = com.larsons.game.sprite.SpriteView.headingOf(0, 1);     // facing her, over the wall
        Her her = new Her(new Vec3(0, 0, 5));
        run(folks, her, walled, 3);
        assertFalse(f.alerted, "a wall between: it never saw her");

        // the wall gone: it sees her where it is looking
        Animals.Place open = place(100, 100, 101, 101, 0, (x, z) -> 0);
        f.heading = com.larsons.game.sprite.SpriteView.headingOf(0, 1);
        run(folks, her, open, 1);
        assertTrue(f.alerted, "in the open, before it: seen");

        // behind its back, far off, it does not
        Folks more = new Folks();
        Folk g = more.spawn(Breed.BANDIT, new Vec3(0, 0, -5), 0, Folks.Spec.ANY.weapon("sword"));
        g.roam = 0;
        g.heading = com.larsons.game.sprite.SpriteView.headingOf(0, -1);    // facing away
        run(more, her, open, 2);
        assertFalse(g.alerted, "behind it: not seen");
    }

    @Test
    void theWayRoundIsFoundAndPulledStraight() {
        Animals.Place place = place(-8, -0.3, 8, 0.3, 3, (x, z) -> 0);
        double[] way = Paths.find(0, -6, 0, 6, 0.3, place);
        assertNotNull(way);
        assertEquals(0, way[way.length - 2], 1e-9);
        assertEquals(6, way[way.length - 1], 1e-9);
        assertTrue(way.length / 2 <= 6, "a few points, not one a cell: " + way.length / 2);
        // every leg of it is clear
        double px = 0, pz = -6;
        for (int i = 0; i < way.length; i += 2) {
            assertTrue(Paths.clear(px, pz, way[i], way[i + 1], 0.3, place));
            px = way[i];
            pz = way[i + 1];
        }
        // a place it cannot get to (walled all round): as near as it can
        Animals.Place boxed = new Animals.Place() {
            @Override public double ground(double x, double z) { return 0; }
            @Override public double water(double x, double z, double g) { return 0; }
            @Override public Biome biome(double x, double z) { return Biome.PLAINS; }
            @Override public boolean blocked(double x, double z, double r) {
                double d = Math.hypot(x, z - 6);
                return d > 2 - r && d < 3 + r;
            }
        };
        double[] near = Paths.find(0, -6, 0, 6, 0.3, boxed);
        assertNotNull(near);
        double ex = near[near.length - 2], ez = near[near.length - 1];
        assertTrue(Math.hypot(ex, ez - 6) > 2.9 && Math.hypot(ex, ez - 6) < 4.5, "at the ring's edge: " + ex + "," + ez);
    }
}
