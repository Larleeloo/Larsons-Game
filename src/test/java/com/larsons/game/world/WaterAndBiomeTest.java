package com.larsons.game.world;

import com.larsons.game.audio.WorldSounds;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The water painted into a level and the sea, the biomes read back from the
 * ground and the water, the dozen grounds in their three groups, and all of
 * it saved with the level and brought back.
 */
class WaterAndBiomeTest {

    /** A bowl 2 m deep round (0, 0), 6 m across, its rim lowest (1.5 m) on the east; flat ground at 0 beyond. */
    private static double bowl(double x, double z) {
        double d = Math.hypot(x, z);
        if (d > 6) return 0;
        double rim = x > 3 && Math.abs(z) < 1.5 ? 1.5 : 2.5;
        return d < 4 ? -2 + d * 0.25 : -1 + (d - 4) / 2 * (rim + 1);
    }

    @Test
    void aHollowFillsToJustItsBrim() {
        WaterMap w = new WaterMap(64);
        double brim = w.brim(0, 0, WaterAndBiomeTest::bowl, 40, 100_000);
        assertTrue(brim > -0.2 && brim < 1.6, "to its lowest rim, not over it: " + brim);
        int n = w.fill(WaterMap.Kind.CLEAR, brim - 0.04, 0, 0, WaterAndBiomeTest::bowl, 100_000);
        assertTrue(n > 100, "the hollow full");
        assertTrue(w.depth(0, 0, bowl(0, 0)) > 1.5, "deep in the middle");
        assertEquals(0, w.depth(20, 20, 0), 1e-9, "nothing out over the flat beyond");
        assertEquals(0, w.depth(-5.9, 0, bowl(-5.9, 0)), 1e-9, "the far bank above it, dry");
        // on flat ground there is no hollow to fill
        WaterMap flat = new WaterMap(64);
        assertEquals(0, flat.brim(10, 10, (x, z) -> 0, 40, 10_000), 1e-9);
        assertEquals(0, flat.fill(WaterMap.Kind.CLEAR, 0, 10, 10, (x, z) -> 0, 10_000));
    }

    @Test
    void paintedWaterHasASurfaceAKindAndAFlow() {
        WaterMap w = new WaterMap(64).paint(WaterMap.Kind.CLEAR, -0.5, 4, 4, 2, 1, 0);
        assertEquals(-0.5, w.surface(4, 4, -1.5), 1e-6);
        assertEquals(1.0, w.depth(4, 4, -1.5), 1e-6);
        assertEquals(0, w.depth(4, 4, 0), 1e-9, "dry where the ground is above it");
        assertEquals(WaterMap.Kind.CLEAR, w.kindAt(4, 4, -1.5));
        assertNull(w.kindAt(4, 4, 0));
        assertNull(w.kindAt(20, 20, -5), "none painted there");
        assertFalse(w.isEmpty());
        w.erase(4, 4, 3);
        assertEquals(0, w.depth(4, 4, -1.5), 1e-9, "taken away");
        assertTrue(new WaterMap(64).isEmpty());
        // too fast a current is slowed to the fastest
        WaterMap fast = new WaterMap(16).paint(WaterMap.Kind.CLEAR, 0, 0, 0, 1, 30, 40);
        int c = (int) ((0 - fast.west()) / WaterMap.CELL);
        assertEquals(WaterMap.MAX_FLOW, Math.hypot(fast.flowX(c, c), fast.flowZ(c, c)), 1e-4);
    }

    @Test
    void theSeaFillsEverythingBelowItsLevelAndAPoolAboveItWins() {
        WaterMap w = new WaterMap(64).setSeaLevel(-1);
        assertEquals(WaterMap.Kind.SEA, w.kindAt(10, 10, -3));
        assertNull(w.kindAt(10, 10, 0));
        assertFalse(w.isEmpty(), "a sea is water");
        assertTrue(w.nonePainted());
        w.paint(WaterMap.Kind.SWAMP, -0.4, 10, 10, 1, 0, 0);
        assertEquals(WaterMap.Kind.SWAMP, w.kindAt(10, 10, -3), "the pool's surface is above the sea's");
        assertEquals(2.6, w.depth(10, 10, -3), 1e-6);
    }

    @Test
    void waterSurvivesItsBytes() {
        WaterMap w = new WaterMap(32).paint(WaterMap.Kind.CRYSTAL, 1.25, -3, 2, 1.5, 0, -0.9)
                .paint(WaterMap.Kind.SWAMP, -2.5, 6, -6, 1, 0, 0);
        WaterMap back = new WaterMap(32).setBytes(w.bytes());
        for (double[] p : new double[][]{{-3, 2}, {6, -6}, {0, 10}}) {
            assertEquals(w.kindAt(p[0], p[1]), back.kindAt(p[0], p[1]));
            assertEquals(w.surface(p[0], p[1], -10), back.surface(p[0], p[1], -10), 1 / 256.0);
        }
        int c = (int) ((-3 - w.west()) / WaterMap.CELL), r = (int) ((2 - w.west()) / WaterMap.CELL);
        assertEquals(-0.9, back.flowZ(c, r), 0.05, "the current, to its sixteenth of a turn and 1/255 of the fastest");
    }

    @Test
    void theMeshRunsOnFromCellToCellAndSlantsWhereThePaintStops() {
        WaterMap w = new WaterMap(16).paint(WaterMap.Kind.CLEAR, -0.3, 0, 0, 2, 0, 0)
                .paint(WaterMap.Kind.CLEAR, -0.5, 2.5, 0, 1.2, 0, 0);
        Terrain bed = Terrain.of(-8, -8, 17, 17, flat(17, -1.0f), List.of(), List.of());
        float[] mesh = WaterRenderer.mesh(w, bed);
        assertTrue(mesh.length > 0);
        int stride = 7;
        boolean slanted = false;
        for (int i = 0; i < mesh.length; i += stride) {
            double y = mesh[i + 1], depth = mesh[i + 3];
            assertTrue(y <= -0.3 + 1e-6 && y >= -0.5 - 1e-6, "a corner's surface is its cells' mean");
            if (depth < 0) slanted = true;
        }
        assertTrue(slanted, "the edge of the paint ends under the surface, so it is cut on a slant");
    }

    private static float[] flat(int n, float h) {
        float[] a = new float[n * n];
        java.util.Arrays.fill(a, h);
        return a;
    }

    @Test
    void aBiomeIsReadFromTheGroundAndTheWater() {
        GroundMap g = new GroundMap().disc(GroundMap.MUD, 10, 0, 3).disc(GroundMap.CRYSTAL, -10, 0, 3)
                .disc(GroundMap.SNOW, 0, 10, 3);
        WaterMap w = new WaterMap(64).paint(WaterMap.Kind.CLEAR, 0.5, 0, -10, 2, 1, 0).setSeaLevel(-2);
        assertEquals(Biome.MARSH, Biome.at(g, w, 10, 0, 0));
        assertEquals(Biome.CRYSTAL, Biome.at(g, w, -10, 0, 0));
        assertEquals(Biome.MOUNTAINS, Biome.at(g, w, 0, 10, 0));
        assertEquals(Biome.RIVER, Biome.at(g, w, 0, -10, 0), "in the stream");
        assertEquals(Biome.OCEAN, Biome.at(g, w, 20, 20, -5), "under the sea");
        assertEquals(Biome.PLAINS, Biome.at(g, w, 20, 20, 0), "grass");
        assertEquals(Biome.PLAINS, Biome.at(null, null, 0, 0, 0));
        assertEquals(Biome.SAVANNAH, Biome.byKey(" Savannah "));
        assertNull(Biome.byKey("moon"));
    }

    @Test
    void theBiomeBrushPaintsItsGroundWithPatchesOfItsAccent() {
        GroundMap g = new GroundMap(64);
        Biome b = Biome.MARSH;
        g.paint(b::groundAt, 0, 0, 20, 1);
        int ground = 0, accent = 0;
        double h = GroundMap.CELL / 2;                      // (the cells' middles: no blending between them)
        for (double x = -15 + h; x <= 15; x += 1) {
            for (double z = -15 + h; z <= 15; z += 1) {
                if (Math.hypot(x, z) > 18) continue;
                int m = g.material(x, z);
                assertEquals(b.groundAt(x, z), m, "at " + x + ", " + z);
                if (m == b.ground()) ground++;
                else if (m == b.accent()) accent++;
            }
        }
        assertTrue(ground > accent && accent > 20, "mostly mud, meadow in patches: " + ground + " / " + accent);
        assertFalse(g.isEmpty(0), "the meadow is in the first group");
        assertFalse(g.isEmpty(1), "mud in the second");
        assertTrue(g.isEmpty(2));
        // and back to grass
        g.paint(-1, 0, 0, 25, 1);
        assertEquals(-1, g.material(3, 3));
    }

    @Test
    void theTwelveGroundsLieInThreeGroupsAndComeBackFromASnapshot() {
        GroundMap g = new GroundMap(32).disc(GroundMap.DIRT, -6, 0, 2).disc(GroundMap.SAND, 0, 0, 2)
                .disc(GroundMap.CRYSTAL, 6, 0, 2);
        assertEquals(GroundMap.MATERIALS, GroundMap.NAMES.length);
        for (int gr = 0; gr < GroundMap.GROUPS; gr++) assertFalse(g.isEmpty(gr), "group " + gr);
        byte[][] snap = g.snapshot();
        g.paint(GroundMap.ASH, 0, 0, 20, 1);
        assertEquals(GroundMap.ASH, g.material(6, 0));
        g.restore(snap);
        assertEquals(GroundMap.CRYSTAL, g.material(6, 0));
        assertEquals(GroundMap.SAND, g.material(0, 0));
        assertEquals(GroundMap.DIRT, g.material(-6, 0));
    }

    @Test
    void aLevelKeepsItsWaterItsSeaAndAllItsGrounds(@TempDir Path root) throws IOException {
        Level l = Level.blank("Wet", 64);
        l.ground().paint(GroundMap.SNOW, -10, 0, 3, 1);
        l.ground().paint(GroundMap.CRYSTAL, 10, 0, 3, 1);
        l.water().paint(WaterMap.Kind.SWAMP, -0.75, 0, 10, 3, 0, 0).setSeaLevel(-1.5);
        Path folder = root.resolve("wet");
        LevelIO.save(l, folder);
        assertTrue(Files.exists(folder.resolve(LevelIO.WATER)));
        assertTrue(Files.exists(folder.resolve(LevelIO.groundFile(1))));
        assertTrue(Files.exists(folder.resolve(LevelIO.groundFile(2))));
        assertTrue(Files.readString(folder.resolve(LevelIO.FILE)).contains("\"sea\": -1.5"));
        Level back = LevelIO.load(folder);
        assertEquals(-1.5, back.water().seaLevel(), 1e-9);
        assertEquals(WaterMap.Kind.SWAMP, back.water().kindAt(0, 10, -2));
        assertEquals(-0.75, back.water().surface(0, 10, -2), 1 / 256.0);
        assertEquals(GroundMap.SNOW, back.ground().material(-10, 0));
        assertEquals(GroundMap.CRYSTAL, back.ground().material(10, 0));
        for (int g = 0; g < GroundMap.GROUPS; g++) {
            assertArrayEquals(l.ground().rgba(g), back.ground().rgba(g), "group " + g);
        }

        // dried up and grassed over, the files go
        Level dry = Level.blank("Wet", 64);
        LevelIO.save(dry, folder);
        assertFalse(Files.exists(folder.resolve(LevelIO.WATER)));
        assertFalse(Files.exists(folder.resolve(LevelIO.groundFile(1))));
        assertTrue(LevelIO.load(folder).water().isEmpty());
    }

    @Test
    void sheKnowsHowDeepSheStandsAndWhereSheIs() {
        World world = new World(new com.larsons.game.sprite.Wardrobe());
        world.player().setPosition(new com.larsons.game.math.Vec3(0, 0, 0));
        WaterMap w = new WaterMap(64).paint(WaterMap.Kind.CLEAR, 0.6, 0, 0, 3, 0, 0)
                .paint(WaterMap.Kind.CLEAR, 2.0, 10, 0, 3, 0, 0);
        world.setWater(w);
        assertEquals(0.6, world.player().waterDepth(), 1e-6);
        assertEquals(Biome.RIVER, world.biome(0, 0));
        assertTrue(world.water().depth(0, 0, 0) > WorldSounds.WADING, "her steps splash");
        assertTrue(world.water().depth(10, 0, 0) > WaterMap.TOO_DEEP, "2 m deep: too deep to wade into");

        // what she hears: the river all round her, its sound by day, by night the same
        Map<String, Double> noon = WorldSounds.biomeMix(world, new com.larsons.game.math.Vec3(0, 0, 0), 1);
        assertEquals(1.0, noon.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertTrue(noon.get("ambient/stream") > 0.1);
        Map<String, Double> night = WorldSounds.biomeMix(world, new com.larsons.game.math.Vec3(30, 0, 30), 0);
        assertEquals(1.0, night.get("ambient/crickets"), 1e-9, "the plains at night: crickets");
        assertEquals(0.0, night.getOrDefault("ambient/plains", 0.0), 1e-9);
    }

    @Test
    void tallGrassLeansAwayFromWhoeverWalksThroughIt() {
        var profile = new com.larsons.game.sprite.SceneryProfile(com.larsons.game.sprite.SceneryProfile.Kind.TUFT,
                64, 64, 1, 1, 1.2, 0, 0, false, List.of("plains"), -1, true);
        var sprite = com.larsons.game.sprite.ScenerySprites.describe("tall_grass_a", profile, Path.of("x.png"), 64, 64);
        Scenery.Placed p = new Scenery.Placed(sprite, 0, 0, 0, 0, false, 0);
        assertNull(SceneryRenderer.lean(p, List.of(new com.larsons.game.math.Vec3(3, 0, 0))), "nobody near");
        double[] lean = SceneryRenderer.lean(p, List.of(new com.larsons.game.math.Vec3(0.3, 0, 0)));
        assertNotNull(lean);
        assertTrue(lean[0] < 0 && Math.abs(lean[2]) < 1e-9, "away from her, along x");
        assertTrue(lean[1] > 0, "and pressed down");
        double[] closer = SceneryRenderer.lean(p, List.of(new com.larsons.game.math.Vec3(0.1, 0, 0)));
        assertTrue(closer[0] < lean[0], "harder the nearer she is");
    }

    @Test
    void eachGroundHasItsFootstep() {
        Map<Integer, String> want = Map.of(GroundMap.SAND, "sand", GroundMap.ASH, "sand", GroundMap.MUD, "mud",
                GroundMap.SNOW, "snow", GroundMap.STONE, "stone", GroundMap.CRYSTAL, "crystal",
                GroundMap.JUNGLE, "forest", GroundMap.DRY_GRASS, "grass");
        for (Map.Entry<Integer, String> e : want.entrySet()) {
            GroundMap g = new GroundMap().disc(e.getKey(), 0, 0, 3);
            assertEquals(e.getValue(), WorldSounds.surface(g, 0, 0), GroundMap.NAMES[e.getKey()]);
        }
    }
}
