package com.larsons.game.actor;

import com.larsons.game.ambient.Animals;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.world.Biome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A foe's line of sight decides how far off it notices her ({@link Sight}):
 * far straight ahead, less to the sides, nothing behind - where she can only
 * be heard, and it turns to look - never through a wall, and crouched a low
 * rock hides her.
 */
class SightTest {

    /** Flat open ground; {@code blocks} stand where they are, each {x0, z0, x1, z1, top}. */
    private static Animals.Place place(double[]... blocks) {
        return new Animals.Place() {
            @Override public double ground(double x, double z) { return 0; }
            @Override public double water(double x, double z, double g) { return 0; }
            @Override public Biome biome(double x, double z) { return Biome.PLAINS; }

            @Override
            public double sightTop(double x, double z) {
                double top = Double.NEGATIVE_INFINITY;
                for (double[] b : blocks) {
                    if (x > b[0] && x < b[2] && z > b[1] && z < b[3]) top = Math.max(top, b[4]);
                }
                return top;
            }

            @Override
            public boolean sightBlocked(double x, double z, double y) { return sightTop(x, z) > y; }
        };
    }

    /** She, standing still at {@code at}: as plain to see as {@code conspicuous}, heard {@code heard} m off. */
    private static final class Her implements Animals.Quarry {
        Vec3 at;
        double conspicuous = 1, heard = 3.5, height = Sight.STANDING;

        Her(Vec3 at) { this.at = at; }

        @Override public Vec3 feet() { return at; }
        @Override public boolean dodging() { return false; }
        @Override public boolean parrying() { return false; }
        @Override public boolean blocking() { return false; }
        @Override public double hurt(double d) { return d; }
        @Override public boolean down() { return false; }
        @Override public double conspicuous() { return conspicuous; }
        @Override public double seenHeight() { return height; }
        @Override public double heard() { return heard; }
    }

    /** A bandit at the middle, facing +z, standing its ground. */
    private static Folk bandit(Folks folks) {
        Folk f = folks.spawn(Breed.BANDIT, Vec3.ZERO, SpriteView.headingOf(0, 1),
                Folks.Spec.ANY.weapon("sword").size(1));
        f.roam = 0;
        return f;
    }

    private static List<Folks.Event> run(Folks folks, Her her, Animals.Place place, double seconds) {
        List<Folks.Event> all = new java.util.ArrayList<>();
        for (double t = 0; t < seconds; t += 1 / 30.0) all.addAll(folks.tick(1 / 30.0, her, place));
        return all;
    }

    @Test
    void itsEyesSeeAllTheWayAheadLessToTheSidesAndNothingBehind() {
        assertEquals(1, Sight.cone(0), 1e-9);
        assertEquals(1, Sight.cone(Sight.FULL), 1e-9);
        assertEquals(Sight.SIDE, Sight.cone(Sight.EDGE), 1e-9);
        assertEquals(0, Sight.cone(Math.toRadians(90)), 1e-9);
        assertEquals(0, Sight.cone(Math.PI), 1e-9);
        assertTrue(Sight.cone(Math.toRadians(50)) < 1 && Sight.cone(Math.toRadians(50)) > Sight.SIDE);

        Folk f = bandit(new Folks());
        Animals.Place open = place();
        double sight = f.sight();
        // straight ahead it sees as far as its kind does; 60° off, only so far; behind, not at all
        assertTrue(Sight.sees(f, new Vec3(0, 0, sight - 0.5), Sight.STANDING, 1, open));
        assertFalse(Sight.sees(f, new Vec3(0, 0, sight + 0.5), Sight.STANDING, 1, open));
        double off = Math.toRadians(60), side = sight * Sight.cone(off);
        Vec3 dir = SpriteView.headingVector(f.heading() + off);
        assertTrue(Sight.sees(f, dir.scale(side - 0.5), Sight.STANDING, 1, open), "to the side, near");
        assertFalse(Sight.sees(f, dir.scale(side + 0.5), Sight.STANDING, 1, open), "to the side, past its reach");
        assertFalse(Sight.sees(f, new Vec3(0, 0, -4), Sight.STANDING, 1, open), "behind it");
        assertTrue(Sight.sees(f, new Vec3(0, 0, -0.8), Sight.STANDING, 1, open), "so near it could touch her");
        // hidden (conspicuous 0.3): a third as far
        assertFalse(Sight.sees(f, new Vec3(0, 0, sight * 0.3 + 0.5), Sight.STANDING, 0.3, open));
        assertTrue(Sight.sees(f, new Vec3(0, 0, sight * 0.3 - 0.5), Sight.STANDING, 0.3, open));
    }

    @Test
    void crouchedBehindALowRockSheIsHiddenStandingSheIsSeenOverIt() {
        Folk f = bandit(new Folks());
        // a rock 0.9 m high, 6 m ahead; she just behind it
        Animals.Place rock = place(new double[]{-0.6, 5.6, 0.6, 6.4, 0.9});
        Vec3 behind = new Vec3(0, 0, 7);
        assertTrue(Sight.sees(f, behind, Sight.STANDING, 1, rock), "standing: seen over the rock");
        assertFalse(Sight.sees(f, behind, Sight.CROUCHED, 1, rock), "crouched: hidden behind it");
        // a wall is higher than she ever is
        Animals.Place wall = place(new double[]{-4, 5.7, 4, 6.3, 3});
        assertFalse(Sight.sees(f, behind, Sight.STANDING, 1, wall));
    }

    @Test
    void behindItSheIsHeardAndItTurnsToLookThenSeesHer() {
        Animals.Place open = place();
        // sprinting up behind it (heard 9 m off): it hears her, turns, and sees her
        Folks folks = new Folks();
        Folk f = bandit(folks);
        Her her = new Her(new Vec3(0, 0, -5));
        her.heard = 9;
        List<Folks.Event> events = run(folks, her, open, 0.3);
        assertTrue(events.stream().anyMatch(e -> e.outcome() == Folks.Outcome.HEARD), "heard");
        assertTrue(f.listening());
        assertFalse(f.alerted(), "heard, not yet seen");
        events = run(folks, her, open, 2);
        assertTrue(f.alerted(), "it turned, and saw her");
        assertTrue(events.stream().anyMatch(e -> e.outcome() == Folks.Outcome.NOTICED));

        // creeping up behind it (heard 1 m off): it never knows she is there
        Folks quiet = new Folks();
        Folk g = bandit(quiet);
        Her creep = new Her(new Vec3(0, 0, -2));
        creep.heard = 1.0;
        creep.height = Sight.CROUCHED;
        creep.conspicuous = 0.5;
        run(quiet, creep, open, 3);
        assertFalse(g.listening());
        assertFalse(g.alerted(), "not heard, not seen");
        assertFalse(g.spotted());
    }

    @Test
    void aPersonWatchingHerHasSpottedHerOneFacingAwayHasNot() {
        Animals.Place open = place();
        Folks folks = new Folks();
        Folk watching = folks.person(1, People.HUMAN, Vec3.ZERO, SpriteView.headingOf(0, 1), null);
        Folk away = folks.person(2, People.HUMAN, new Vec3(4, 0, 0), SpriteView.headingOf(0, -1), null);
        watching.roam = 0;
        away.roam = 0;
        Her her = new Her(new Vec3(0, 0, 6));
        her.heard = 1.0;
        run(folks, her, open, 1);
        assertTrue(watching.seesHer());
        assertTrue(watching.spotted());
        assertFalse(away.seesHer(), "she is behind its back");
        assertFalse(away.spotted());
        assertEquals(1, folks.spotting());
    }

    @Test
    void itsFanIsCutShortByAWallAndSpreadsAsFarAsItsEyes() {
        Folk f = bandit(new Folks());
        // a wall 5 m ahead, 2 m wide
        Animals.Place wall = place(new double[]{-1, 5, 1, 5.6, 3});
        int rays = 11;
        double[] fan = Sight.fan(f, wall, 1, Sight.STANDING, rays);
        assertEquals(rays * 2, fan.length);
        int mid = rays / 2;
        assertEquals(f.heading(), fan[mid], 1e-9, "the middle way straight ahead");
        assertEquals(5.5, fan[rays + mid], 0.5, "cut short at the wall");
        assertEquals(f.sight() * Sight.SIDE, fan[rays], 0.6, "at the edge of its eyes, as far as it sees there");
        assertEquals(f.heading() - Sight.EDGE, fan[0], 1e-9);
        assertEquals(f.heading() + Sight.EDGE, fan[rays - 1], 1e-9);
        // hidden, it all draws in
        double[] hidden = Sight.fan(f, place(), 0.3, Sight.CROUCHED, rays);
        assertEquals(f.sight() * 0.3, hidden[rays + mid], 0.6);
    }
}
