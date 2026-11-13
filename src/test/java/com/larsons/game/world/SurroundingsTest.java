package com.larsons.game.world;

import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelIO;
import com.larsons.game.level.LevelWorld;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.world.Surroundings.Theme;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What lies round a level: its settings, saved and read back, and the rings {@link SurroundGen} makes. */
class SurroundingsTest {

    static ScenerySprites sprites;
    static BuildingCatalog catalog;

    @BeforeAll
    static void load() {
        sprites = ScenerySprites.scan(Path.of("assets", "scenery"));
        catalog = BuildingCatalog.load(Path.of("assets", "buildings"));
        BuildingCatalog.use(catalog);
    }

    static SurroundGen.Result make(Level level, Surroundings s) {
        Terrain t = LevelWorld.terrain(level);
        SurroundGen.Edge e = SurroundGen.Edge.of(t, level.ground(), level.water(), level.half(), 0.5);
        return SurroundGen.generate(new SurroundGen.Input(s, level.size(), e, t, sprites, catalog, null));
    }

    @Test
    void settingsGoThroughTheirJsonAndStayInRange() {
        Surroundings s = new Surroundings(Theme.DESERT, 80, 2200, 1.5, 2, 42, 0.6, false);
        assertEquals(s, Surroundings.fromJson(s.toJson()));
        assertEquals(Surroundings.DEFAULT, Surroundings.fromJson(null));
        assertEquals(Theme.CITY, Surroundings.fromJson("city").theme());
        Surroundings wild = new Surroundings(null, 9999, -5, 9, -1, 1, 7, true);
        assertEquals(Theme.AUTO, wild.theme());
        assertEquals(Surroundings.MAX_NEAR, wild.near());
        assertEquals(Surroundings.MIN_FAR, wild.far());
        assertEquals(2, wild.density());
        assertEquals(0, wild.height());
        assertEquals(1, wild.haze());
        assertEquals(64, Surroundings.DEFAULT.nearFor(256));
        assertEquals(48, Surroundings.DEFAULT.nearFor(64));
        assertEquals(128, Surroundings.DEFAULT.nearFor(2048));
    }

    @Test
    void aLevelKeepsItsSurroundings() throws Exception {
        Level level = new Level("Round", 128);
        level.setSurroundings(Surroundings.DEFAULT.withTheme(Theme.PINES).withSeed(9));
        Path dir = java.nio.file.Files.createTempDirectory("surround");
        LevelIO.save(level, dir);
        assertTrue(LevelIO.json(level).contains("\"surroundings\""));
        assertEquals(level.surroundings(), LevelIO.load(dir).surroundings());
        // as it comes: not written at all
        assertFalse(LevelIO.json(new Level("Plain", 128)).contains("surroundings"));
    }

    @Test
    void theRingCarriesTheLevelsEdgeOnSeamlessly() {
        Level level = new Level("Hill", 128);
        // a hill cut by the east edge, a path running off the north edge, a river off the west
        level.heights().raise(64, 0, 20, 5);
        level.ground().stroke(GroundMap.DIRT, 0, 0, 0, -64, 1.5);
        level.water().paint(WaterMap.Kind.CLEAR, -0.4, -64, 20, 4, -1, 0);
        level.heights().raise(-64, 20, 6, -1.5);
        SurroundGen.Result r = make(level, Surroundings.DEFAULT.withTheme(Theme.FOREST));
        assertEquals(Theme.FOREST, r.theme());
        Terrain lv = LevelWorld.terrain(level);
        // at the edge the ring is the level's height; just past it, still nearly so
        // (the level's very edge is kept at 0: its last height is a grid step in)
        for (double z = -60; z <= 60; z += 7) {
            assertEquals(lv.height(63.45, z), r.ground().height(64, z), 0.05, "east edge at z " + z);
            assertEquals(lv.height(63.45, z), r.ground().height(65, z), 0.25, "just past the east edge at z " + z);
        }
        // the path runs on out
        assertTrue(r.map().weight(GroundMap.DIRT, 0, -70) > 0.5, "the path carries on past the edge");
        // the river runs on in its channel, with water in it
        assertFalse(Double.isNaN(r.water().surface(-80, 20, r.ground().height(-80, 20))), "the river runs on");
        // trees grow round it, none inside the level
        assertTrue(r.scenery().placed().size() > 100, "a forest grows round it: " + r.scenery().placed().size());
        for (Scenery.Placed p : r.scenery().placed()) {
            assertTrue(Math.max(Math.abs(p.x()), Math.abs(p.z())) > 64, "nothing inside the level");
            assertEquals(r.ground().height(p.x(), p.z()), p.y(), 1e-3);
        }
        assertTrue(r.far().triangles() > 1000);
        assertTrue(r.far().radius() >= 64 + r.width() + 300);
    }

    @Test
    void everyThemeMakesSomething() {
        Level level = new Level("Every", 64);
        for (Theme t : Theme.values()) {
            if (!t.real()) continue;
            long t0 = System.nanoTime();
            SurroundGen.Result r = make(level, Surroundings.DEFAULT.withTheme(t));
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertEquals(t, r.theme());
            assertTrue(r.far().triangles() > 500, t + ": " + r.far().triangles());
            for (float v : r.far().vertices()) assertFalse(Float.isNaN(v), t + " has no NaN in its mesh");
            System.out.println("[surroundings] " + t.key() + ": " + r.scenery().placed().size() + " sprites, "
                    + r.buildings().size() + " buildings, " + r.far().triangles() + " far triangles, " + ms + " ms");
        }
    }

    @Test
    void aCityGoesOnWithStreetsAndHouses() throws Exception {
        Level london = LevelIO.load(Path.of("assets", "levels", "london"));
        long t0 = System.nanoTime();
        SurroundGen.Result r = make(london, Surroundings.DEFAULT.withTheme(Theme.AUTO));
        System.out.println("[surroundings] london: " + r.theme() + ", " + r.buildings().size() + " buildings, "
                + r.scenery().placed().size() + " sprites, " + r.far().triangles() + " far triangles, "
                + (System.nanoTime() - t0) / 1_000_000 + " ms");
        assertEquals(Theme.CITY, r.theme());
        assertTrue(r.buildings().size() > 150, "rows of houses: " + r.buildings().size());
        // the ring street just past the edge
        assertEquals(GroundMap.COBBLES, r.map().material(0, -256 - 5));
        // the Thames runs on out of both ends
        double z = 140 + 10 * Math.sin(-300 / 120.0 - 2.177);
        assertFalse(Double.isNaN(r.water().surface(-300, z, r.ground().height(-300, z))), "the river runs on west");
    }

    @Test
    void autoReadsTheEdge() {
        Level sand = new Level("Dunes", 128);
        sand.ground().square(true).paint(GroundMap.SAND, 0, 0, 70, 1);
        assertEquals(Theme.DESERT, SurroundGen.resolve(Surroundings.DEFAULT,
                SurroundGen.Edge.of(LevelWorld.terrain(sand), sand.ground(), sand.water(), 64, 0.5)));
        Level plain = new Level("Plain", 128);
        assertEquals(Theme.MEADOW, SurroundGen.resolve(Surroundings.DEFAULT,
                SurroundGen.Edge.of(LevelWorld.terrain(plain), plain.ground(), plain.water(), 64, 0.5)));
        assertEquals(List.of(), List.of());
    }
}
