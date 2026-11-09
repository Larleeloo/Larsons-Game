package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Swimming: into deep water she swims on the surface, dives under, runs out of breath, and climbs out. */
class SwimmingTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);
    private static final double SURFACE = 3.0;
    private static final Player.Intent EAST = new Player.Intent(1, 0, false, false, false, false);
    private static final Player.Intent WEST = new Player.Intent(-1, 0, false, false, false, false);
    private static final Player.Intent DIVE = new Player.Intent(0, 0, false, false, false, false, false, true,
            false, false, false, false, false, null);
    private static final Player.Intent UP = new Player.Intent(0, 0, false, false, true, false);

    /** A world with a pool 3 m deep from x = 6 to x = 26 (flat ground at 0). */
    private static World pool() {
        World w = new World(new Wardrobe());
        w.animals().setEnabled(false);
        WaterMap water = new WaterMap(96);
        for (double x = 7; x <= 25; x += 1) water.paint(WaterMap.Kind.CLEAR, SURFACE, x, 0, 3, 0, 0);
        w.setWater(water);
        w.player().setPosition(new Vec3(0, 0, 0));
        return w;
    }

    private static void run(World w, double seconds, Player.Intent in) {
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            w.player().update(DT, in, DUR);
            w.tick(DT);
        }
    }

    private static void once(World w, Player.Intent in) {
        w.player().update(DT, in, DUR);
        w.tick(DT);
    }

    @Test
    void walkingIntoDeepWaterSheSwimsOnTheSurface() {
        World w = pool();
        Player p = w.player();
        for (int i = 0; i < 900 && !p.swimming(); i++) once(w, EAST);
        assertTrue(p.swimming(), "in over her head, she swims");
        assertFalse(p.underWater());
        run(w, 2, EAST);
        assertEquals(AnimState.SWIM, p.state(), "the breaststroke, going along");
        assertEquals(SURFACE - Player.SURFACE_LINE, p.feet().y(), 0.1, "the water at her shoulders");
        double x = p.ground().x();
        run(w, 1, EAST);
        assertTrue(p.ground().x() > x + 0.8, "swimming along");
        run(w, 3, Player.Intent.NONE);
        assertEquals(AnimState.SWIM_IDLE, p.state(), "still: treading water");
        assertEquals(SURFACE - Player.TREAD_LINE, p.feet().y(), 0.05);
    }

    @Test
    void sheDivesHoldsHerBreathAndDrownsUntilSheComesUp() {
        World w = pool();
        Player p = w.player();
        p.setPosition(new Vec3(16, 0, 0));
        run(w, 0.2, Player.Intent.NONE);
        assertTrue(p.swimming());
        once(w, DIVE);
        assertTrue(p.underWater());
        run(w, 3, Player.Intent.NONE);
        assertEquals(AnimState.SWIM_UNDER, p.state());
        assertEquals(Player.DIVE_STEP, p.diveDepth(), 0.05, "a step down");
        once(w, DIVE);
        run(w, 3, Player.Intent.NONE);
        assertEquals(SURFACE - Player.OFF_BOTTOM, p.diveDepth(), 0.05, "no deeper than over the bottom");
        assertTrue(p.breath() < Player.MAX_BREATH - 5);
        double health = p.health();
        run(w, Player.MAX_BREATH, Player.Intent.NONE);
        assertEquals(0, p.breath(), 1e-9);
        assertTrue(p.health() < health - 5, "out of breath, she drowns");
        once(w, UP);
        once(w, UP);
        run(w, 4, Player.Intent.NONE);
        assertFalse(p.underWater(), "up for air");
        run(w, 2, Player.Intent.NONE);
        assertTrue(p.breath() > 5, "her breath comes back");
    }

    @Test
    void sheSwimsBackToTheShallowsAndStandsUp() {
        World w = pool();
        Player p = w.player();
        p.setPosition(new Vec3(9, 0, 0));
        run(w, 0.2, Player.Intent.NONE);
        assertTrue(p.swimming());
        for (int i = 0; i < 900 && p.swimming(); i++) once(w, WEST);
        assertFalse(p.swimming(), "out of the water");
        assertEquals(0, p.feet().y(), 1e-6, "on her feet on the ground");
    }

    @Test
    void aFallIntoDeepWaterDoesNotHurt() {
        World w = pool();
        Player p = w.player();
        p.setPosition(new Vec3(16, 0, 0));
        p.lift(12);
        double health = p.health();
        run(w, 4, Player.Intent.NONE);
        assertTrue(p.swimming());
        assertEquals(health, p.health(), 1e-9, "the water broke her fall");
    }

    @Test
    void underWaterAtTheEdgeSheComesUpAndOutNeverIntoTheGround() {
        World w = pool();
        Player p = w.player();
        p.setPosition(new Vec3(9, 0, 0));
        run(w, 0.2, Player.Intent.NONE);
        once(w, DIVE);
        once(w, DIVE);
        run(w, 2, Player.Intent.NONE);
        assertTrue(p.underWater());
        for (int i = 0; i < 1200 && p.swimming(); i++) {
            once(w, WEST);
            assertTrue(Double.isFinite(p.feet().y()), "never lost under the map");
            assertTrue(p.feet().y() >= -1e-6, "never under the ground");
        }
        assertFalse(p.swimming(), "out of the water");
        assertFalse(p.underWater());
        assertEquals(0, p.feet().y(), 1e-6, "on her feet on the bank");
    }

    @Test
    void theWaterTakenAwayUnderHerSheStandsOnTheGround() {
        World w = pool();
        Player p = w.player();
        p.setPosition(new Vec3(16, 0, 0));
        run(w, 0.2, Player.Intent.NONE);
        once(w, DIVE);
        run(w, 1, Player.Intent.NONE);
        assertTrue(p.swimming());
        WaterMap dry = new WaterMap(96);
        w.setWater(dry);
        run(w, 0.5, EAST);
        assertFalse(p.swimming());
        assertTrue(Double.isFinite(p.feet().y()));
        assertEquals(0, p.feet().y(), 1e-6);
    }
}
