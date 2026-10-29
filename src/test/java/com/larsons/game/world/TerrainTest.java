package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The ground's height: flat, and the demo's highlands - the plateau, its ledge and cliff, the ramp, the hill, the hollow. */
class TerrainTest {

    private static final Terrain HIGH = Terrain.highlands();

    @Test
    void flatGroundIsAtZeroEverywhere() {
        assertTrue(Terrain.FLAT.flat());
        assertEquals(0, Terrain.FLAT.height(3, -40), 0);
        assertEquals(0, Terrain.FLAT.slope(3, -40), 0);
        assertEquals(Vec3.UP, Terrain.FLAT.normal(1, 1));
        assertTrue(Terrain.FLAT.walls().isEmpty());
    }

    @Test
    void theHillsMeetTheFlatGroundWithoutASeam() {
        double[] box = SceneryLayout.HILLS;
        for (double x = box[0]; x <= box[1]; x += 0.25) {
            assertEquals(0, HIGH.height(x, box[2]), 1e-6, "north edge at x " + x);
            assertEquals(0, HIGH.height(x, box[3]), 1e-6, "south edge at x " + x);
        }
        for (double z = box[2]; z <= box[3]; z += 0.25) {
            assertEquals(0, HIGH.height(box[0], z), 1e-6, "west edge at z " + z);
            assertEquals(0, HIGH.height(box[1], z), 1e-6, "east edge at z " + z);
        }
        assertEquals(0, HIGH.height(0, 0), 0, "the spawn is flat");
        assertEquals(0, HIGH.height(40, -40), 0, "outside the box");
    }

    @Test
    void theFeaturesStandWhereTheyShould() {
        assertEquals(Terrain.PLATEAU_HEIGHT, HIGH.height(6, -36), 1e-4, "the plateau's top");
        assertEquals(Terrain.PLATEAU_HEIGHT / 2, HIGH.height(3, -24.5), 0.05, "halfway up the ramp");
        assertEquals(Terrain.HILL_HEIGHT, HIGH.height(-9, -43), 0.05, "the hill's summit");
        assertEquals(-Terrain.HOLLOW_DEPTH, HIGH.height(15.5, -24.5), 0.05, "the hollow's floor");
        // the ledge and the cliff are sheer: the whole height inside a metre
        assertTrue(HIGH.height(0.1, -36) - HIGH.height(-0.6, -36) > 5, "the west ledge");
        assertTrue(HIGH.height(12, -36) - HIGH.height(12.6, -36) > 5.9, "the east cliff");
    }

    @Test
    void heightsAreTheTrianglesDrawn() {
        // at a grid point it is the grid's height; across a cell it never leaves the cell's corners' range
        for (int i = 0; i < HIGH.columns(); i += 7) {
            for (int j = 0; j < HIGH.rows(); j += 5) {
                double x = HIGH.originX() + i * Terrain.STEP, z = HIGH.originZ() + j * Terrain.STEP;
                assertEquals(HIGH.at(i, j), HIGH.height(x, z), 1e-5);
            }
        }
        double x = 12.2, z = -35.9;
        double h = HIGH.height(x, z);
        assertTrue(h <= Terrain.PLATEAU_HEIGHT + 1e-6 && h >= 0);
    }

    @Test
    void theRampAndTheHillCanBeWalkedTheRockCannot() {
        for (double z = -20; z > -29.5; z -= 0.5) {
            assertTrue(HIGH.rise(3, z, 0, -1) <= Terrain.STEEP, "up the ramp at z " + z);
            assertTrue(HIGH.rise(3, z, 0, -1) > 0.3, "it climbs at z " + z);
        }
        for (double r = 0; r < 13; r += 0.25) {
            assertTrue(HIGH.rise(-9 + r, -43, -1, 0) <= Terrain.STEEP + 1e-6, "the hill " + r + " m out");
        }
        assertTrue(HIGH.slope(12.25, -36) > 5, "the cliff");
        assertTrue(HIGH.slope(6, -43.5) > Terrain.STEEP, "the plateau's north side");
    }

    @Test
    void theVineCliffCanBeClimbedFromItsFoot() {
        assertEquals(1, HIGH.walls().size());
        Terrain.Wall w = HIGH.walls().get(0);
        assertEquals(Terrain.PLATEAU_HEIGHT, w.top(), 0);
        assertEquals(1, w.nx(), 0);
        assertSame(w, HIGH.wallAt(12.8, -36, 0.6), "at its foot");
        assertNull(HIGH.wallAt(14, -36, 0.6), "too far out");
        assertNull(HIGH.wallAt(11, -36, 0.6), "on top");
        assertNull(HIGH.wallAt(12.8, -41.5, 0.6), "past its end");
        Vec3 at = w.at(2, 0.3);
        assertEquals(12.8, at.x(), 1e-9);
        assertEquals(2, w.along(at.x(), at.z()), 1e-9);
        assertEquals(0.3, w.out(at.x(), at.z()), 1e-9);
        Vec3 into = com.larsons.game.sprite.SpriteView.headingVector(w.facing());
        assertEquals(-1, into.x(), 1e-9, "she faces west, into it");
        assertEquals(0, into.z(), 1e-9);
    }

    @Test
    void theNamedSpotsAreThere() {
        for (String name : new String[]{"hills", "ramp", "plateau", "ledge", "cliff", "hill", "hollow"}) {
            assertNotNull(HIGH.place(name), name);
        }
        assertNull(HIGH.place("moon"));
        Terrain.Place top = HIGH.place("plateau");
        assertEquals(Terrain.PLATEAU_HEIGHT, HIGH.height(top.x(), top.z()), 1e-4);
    }
}
