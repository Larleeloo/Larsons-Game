package com.larsons.game.level;

import com.larsons.game.combat.CombatTuning;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.GroundMap;
import com.larsons.game.world.Sky;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A Creative Mode level: its objects and their numbers, its sculpted ground
 * and the brushes, its size changing, and its folder on disk - written and
 * read back as it was, what this game does not know kept.
 */
class LevelTest {

    @Test
    void aNewLevelHasItsSpawnInTheMiddleAndOnlyOne() {
        Level l = Level.blank("Mill", 64);
        assertEquals(1, l.objects().size());
        assertEquals(0, l.spawn().x(), 1e-9);
        l.add(ObjectTypes.SPAWN, 5, 6, 0, Map.of());
        assertEquals(1, l.ofType(ObjectTypes.SPAWN).size(), "a level has one spawn");
        assertEquals(5, l.spawn().x(), 1e-9);
        assertTrue(l.contains(32, -32));
        assertFalse(l.contains(32.5, 0));
    }

    @Test
    void objectsAreNumberedOnceEach() {
        Level l = new Level("x", 64);
        LevelObject a = l.add(ObjectTypes.DUMMY, 1, 1, 0, Map.of());
        LevelObject b = l.add(ObjectTypes.DUMMY, 2, 2, 0, Map.of());
        assertNotEquals(a.id(), b.id());
        LevelObject pasted = l.add(a.copy());
        assertNotEquals(a.id(), pasted.id(), "a pasted copy gets a number of its own");
        assertSame(a, l.byId(a.id()));
        List<LevelObject> kept = l.copyObjects();
        l.remove(b);
        l.restoreObjects(kept);
        assertEquals(3, l.objects().size());
        assertTrue(l.add(ObjectTypes.DUMMY, 0, 0, 0, Map.of()).id() > pasted.id(), "numbers are never reused");
    }

    @Test
    void propsAreReadWithFallbacks() {
        LevelObject o = new LevelObject(1, ObjectTypes.DUMMY, 0, 0, 0, Map.of("health", 250, "name", "Bob"));
        assertEquals(250, o.num("health", 100), 1e-9);
        assertEquals(7, o.num("missing", 7), 1e-9);
        assertEquals("Bob", o.str("name", ""));
        assertFalse(o.bool("mirrored", false));
        LevelObject c = o.copy();
        c.set("health", 10);
        assertEquals(250, o.num("health", 100), 1e-9, "a copy's props are its own");
    }

    @Test
    void theBrushesRaiseSmoothAndFlattenButNeverTheEdge() {
        HeightField hf = new HeightField(32);
        assertTrue(hf.flat());
        int v = hf.version();
        hf.raise(0, 0, 4, 3);
        assertTrue(hf.version() > v, "sculpting counts");
        assertEquals(3, hf.height(0, 0), 0.05, "full height in the middle");
        assertEquals(0, hf.height(4.5, 0), 1e-9, "nothing past the brush");
        assertTrue(hf.height(2, 0) > 0.5 && hf.height(2, 0) < 2.5, "falling off between");
        hf.raise(16, 0, 6, 5);
        assertEquals(0, hf.height(16, 0), 1e-9, "the edge stays where the ground beyond is");
        hf.raise(0, 0, 4, 500);
        assertEquals(HeightField.LIMIT, hf.height(0, 0), 1e-6, "no higher than the limit");

        HeightField bumpy = new HeightField(32);
        bumpy.set(10, 10, 4);
        double x = bumpy.west() + 10 * Terrain.STEP;
        bumpy.smooth(x, x, 2, 1);
        assertTrue(bumpy.at(10, 10) < 4 && bumpy.at(11, 10) > 0, "the spike spread out");

        HeightField ledge = new HeightField(32);
        ledge.raise(0, 0, 8, 6);
        for (int k = 0; k < 40; k++) ledge.flatten(0, 0, 3, 2, 0.5);
        assertEquals(2, ledge.height(0, 0), 0.05, "brought to the height asked for");
    }

    @Test
    void theHeightsMakeTheGroundSheWalksOn() {
        Level l = Level.blank("hill", 64);
        l.heights().raise(4, -4, 6, 3);
        l.add(ObjectTypes.MARKER, 4, -4, 0, Map.of("name", "top"));
        Terrain t = LevelWorld.terrain(l);
        assertEquals(l.heights().height(4, -4), t.height(4, -4), 1e-6);
        assertEquals(0, t.height(200, 200), 1e-9, "flat beyond the level");
        assertTrue(t.places().stream().anyMatch(p -> p.name().equals("top")));
        assertTrue(t.places().stream().anyMatch(p -> p.name().equals("spawn")));
    }

    @Test
    void resizingKeepsTheMiddleAndUndoingPutsItBack() {
        Level l = Level.blank("grow", 128);
        l.heights().raise(10, 10, 4, 2);
        l.ground().paint(GroundMap.DIRT, 10, 10, 2, 1);
        l.add(ObjectTypes.DUMMY, 50, 50, 0, Map.of());
        l.resize(64);
        assertEquals(64, l.size(), 1e-9);
        assertEquals(64, l.heights().size(), 1e-9);
        assertEquals(2, l.heights().height(10, 10), 0.05, "the hill kept");
        assertEquals(GroundMap.DIRT, l.ground().material(10, 10), "the path kept");
        assertTrue(l.ofType(ObjectTypes.DUMMY).isEmpty(), "what stood past the new edge is gone");
        l.resize(128);
        assertEquals(2, l.heights().height(10, 10), 0.05);
    }

    @Test
    void namesBecomeFolders(@TempDir Path root) throws IOException {
        assertEquals("the_old_mill", LevelIO.slug("The Old Mill!"));
        assertEquals("level", LevelIO.slug("???"));
        LevelIO.save(Level.blank("The Old Mill", 64), root.resolve("the_old_mill"));
        assertEquals(root.resolve("the_old_mill_2"), LevelIO.freeFolder(root, "the old mill"));
        assertEquals("The Old Mill 2", LevelIO.freeName(root, "The Old Mill"));
        assertEquals(List.of("The Old Mill"), LevelIO.list(root).stream().map(LevelIO.Saved::name).toList());
        LevelIO.delete(root.resolve("the_old_mill"));
        assertFalse(Files.exists(root.resolve("the_old_mill")));
        assertTrue(LevelIO.list(root).isEmpty());
    }

    @Test
    void aLevelComesBackFromItsFolderAsItWasSaved(@TempDir Path root) throws IOException {
        Level l = Level.blank("Round trip", 64);
        l.heights().raise(-6, 3, 5, 4.25);
        l.heights().raise(8, 8, 3, -2);
        l.ground().paint(GroundMap.GRAVEL, 2, 2, 3, 1);
        l.ground().paint(GroundMap.MEADOW, -10, 5, 2, 0.5);
        l.setSky(Sky.DEFAULT.withZenith(0x203040).withFog(120).withBackdrops(List.of(
                Sky.Backdrop.of(Sky.Kind.MOUNTAINS).withSeed(7).withArc(30, 200),
                Sky.Backdrop.of(Sky.Kind.OCEAN).withDistance(45))));
        l.add(ObjectTypes.SCENERY, 3.25, -4.5, Math.toRadians(37.5), Map.of("sprite", "oak_tree", "mirrored", true));
        l.add(ObjectTypes.ITEM, 1, 2, 0, Map.of("item", "sword", "tier", "iron"));
        l.add(ObjectTypes.VINES, 0, 10, Math.PI / 2, Map.of("length", 6.5)).setLift(0.25);
        l.add(new LevelObject(0, "npc", 4, 4, 0, Map.of("dialogue", "hello", "likes", List.of("cats", 3))));
        l.extra().put("music", Map.of("track", "village"));

        Path folder = root.resolve("round_trip");
        LevelIO.save(l, folder);
        assertTrue(Files.exists(folder.resolve(LevelIO.HEIGHTS)));
        assertTrue(Files.exists(folder.resolve(LevelIO.GROUND)));
        String json = Files.readString(folder.resolve(LevelIO.FILE));
        assertTrue(json.contains("\"name\": \"Round trip\""), json);
        assertTrue(json.lines().anyMatch(line -> line.contains("\"type\": \"scenery\"") && line.contains("37.5")),
                "one object a line, its yaw in degrees:\n" + json);

        Level back = LevelIO.load(folder);
        assertEquals("Round trip", back.name());
        assertEquals(64, back.size(), 1e-9);
        assertEquals(l.sky(), back.sky());
        assertEquals(l.objects().size(), back.objects().size());
        for (int i = 0; i < l.objects().size(); i++) {
            LevelObject a = l.objects().get(i), b = back.objects().get(i);
            assertEquals(a.id(), b.id());
            assertEquals(a.type(), b.type());
            assertEquals(a.x(), b.x(), 1e-3);
            assertEquals(a.z(), b.z(), 1e-3);
            assertEquals(a.yaw(), b.yaw(), 1e-3);
            assertEquals(a.lift(), b.lift(), 1e-3);
        }
        assertTrue(back.byId(2).bool("mirrored", false));
        assertEquals("iron", back.ofType(ObjectTypes.ITEM).get(0).str("tier", ""));
        LevelObject npc = back.ofType("npc").get(0);
        assertEquals("hello", npc.str("dialogue", ""), "a kind this game does not know is kept");
        assertEquals(List.of("cats", 3.0), npc.props().get("likes"));
        assertEquals(Map.of("track", "village"), back.extra().get("music"), "and keys it does not know");
        for (double[] p : new double[][]{{-6, 3}, {8, 8}, {0, 0}, {-3, 1}}) {
            assertEquals(l.heights().height(p[0], p[1]), back.heights().height(p[0], p[1]), 1 / 256.0,
                    "the height at " + p[0] + ", " + p[1]);
        }
        assertArrayEquals(l.ground().rgba(), back.ground().rgba(), "the paint");
        assertTrue(back.nextId() > l.objects().stream().mapToInt(LevelObject::id).max().orElse(0));

        Path again = root.resolve("again");
        LevelIO.save(back, again);
        assertEquals(json, Files.readString(again.resolve(LevelIO.FILE)), "written back the same");
    }

    @Test
    void flatGrassNeedsNoImages(@TempDir Path root) throws IOException {
        Path folder = root.resolve("plain");
        Level l = Level.blank("Plain", 64);
        l.heights().raise(0, 0, 3, 1);
        l.ground().paint(GroundMap.DIRT, 0, 0, 2, 1);
        LevelIO.save(l, folder);
        LevelIO.save(Level.blank("Plain", 64), folder);
        assertFalse(Files.exists(folder.resolve(LevelIO.HEIGHTS)), "an old height map is not left behind");
        assertFalse(Files.exists(folder.resolve(LevelIO.GROUND)));
        Level back = LevelIO.load(folder);
        assertTrue(back.heights().flat());
        assertTrue(back.ground().isEmpty());
    }

    @Test
    void aBrokenFileIsReported(@TempDir Path root) throws IOException {
        Path folder = Files.createDirectories(root.resolve("broken"));
        Files.writeString(folder.resolve(LevelIO.FILE), "{\"name\": ");
        assertThrows(RuntimeException.class, () -> LevelIO.load(folder));
        assertTrue(LevelIO.list(root).isEmpty(), "and left off the Dev tab's list");
    }

    @Test
    void aSoundSpotKeepsPlayingWhereItIsPut() {
        Level l = Level.blank("brook", 64);
        l.heights().raise(6, 2, 4, 2);
        l.add(ObjectTypes.SOUND, 6, 2, 0, Map.of("sound", "ambient/stream"));
        assertTrue(ObjectTypes.palette().stream().anyMatch(e -> e.type().equals(ObjectTypes.SOUND)
                && "ambient/crickets".equals(e.props().get("sound"))), "each held sound is in the palette");
        World w = new World(new Wardrobe());
        LevelWorld.populate(w, l, ScenerySprites.NONE, CombatTuning.defaults());
        assertEquals(1, w.soundSpots().size());
        World.SoundSpot s = w.soundSpots().get(0);
        assertEquals("ambient/stream", s.key());
        assertEquals(3, s.at().y(), 0.05, "a metre over the ground there");
        LevelWorld.spawnAll(w, List.of(), l, w.terrain(), ScenerySprites.NONE, CombatTuning.defaults());
        assertTrue(w.soundSpots().isEmpty(), "taken away with the rest");
    }
}
