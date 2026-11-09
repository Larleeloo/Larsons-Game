package com.larsons.game.level;

import com.larsons.game.audio.Songs;
import com.larsons.game.combat.CombatTuning;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.GroundMap;
import com.larsons.game.math.Vec3;
import com.larsons.game.world.Sky;
import com.larsons.game.world.Store;
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
                Sky.Backdrop.of(Sky.Kind.OCEAN).withDistance(45),
                Sky.Backdrop.of(Sky.Kind.TREES).withColour2(0x553322).withExtent(0.45).withEdge(false))));
        l.add(ObjectTypes.SCENERY, 3.25, -4.5, Math.toRadians(37.5), Map.of("sprite", "oak_tree", "mirrored", true));
        l.add(ObjectTypes.ITEM, 1, 2, 0, Map.of("item", "sword", "tier", "iron"));
        l.add(ObjectTypes.VINES, 0, 10, Math.PI / 2, Map.of("length", 6.5)).setLift(0.25);
        l.add(new LevelObject(0, "npc", 4, 4, 0, Map.of("dialogue", "hello", "likes", List.of("cats", 3))));
        l.setMusic("dream_on");
        l.extra().put("seasons", Map.of("winter", "long"));

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
        assertEquals("dream_on", back.music());
        assertTrue(json.contains("\"music\": \"dream_on\""), json);
        assertEquals(Map.of("winter", "long"), back.extra().get("seasons"), "and keys it does not know");
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
    void aLevelPlaysEverySongShuffledUntilToldOtherwise(@TempDir Path root) throws IOException {
        Level l = Level.blank("Tunes", 64);
        assertEquals(Songs.SHUFFLE, l.music(), "a new level");
        l.setMusic("  ");
        assertEquals(Songs.NONE, l.music(), "blank is none");

        Path folder = Files.createDirectories(root.resolve("old"));          // saved before levels had music
        Files.writeString(folder.resolve(LevelIO.FILE), "{\"format\": 1, \"name\": \"Old\", \"size\": 64, \"objects\": []}");
        assertEquals(Songs.SHUFFLE, LevelIO.load(folder).music());

        Files.writeString(folder.resolve(LevelIO.FILE),                     // a later game's music, not a name
                "{\"format\": 1, \"name\": \"New\", \"size\": 64, \"music\": {\"day\": \"a\", \"night\": \"b\"}, \"objects\": []}");
        Level later = LevelIO.load(folder);
        assertEquals(Songs.SHUFFLE, later.music());
        LevelIO.save(later, root.resolve("again"));
        assertTrue(Files.readString(root.resolve("again").resolve(LevelIO.FILE)).contains("\"music\": \"shuffle\""));
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

    @Test
    void aBarrelACrateAndAStorageChestHoldWhatTheyAreGiven() {
        Level l = Level.blank("cellar", 64);
        LevelObject barrel = l.add(ObjectTypes.BUILDING, 2, 0, 0, Map.of("kind", "barrel", "contents", "sage x4"));
        l.add(ObjectTypes.BUILDING, -2, 0, 0, Map.of("kind", "crate"));
        l.add(ObjectTypes.BUILDING, 9, 9, 0, Map.of("kind", "cottage", "contents", "sage"));
        LevelObject chest = l.add(ObjectTypes.CHEST, 0, 4, 0, Map.of("loot", false, "contents", "sword"));
        LevelObject loot = l.add(ObjectTypes.CHEST, 0, -4, 0, Map.of("contents", "sword"));
        World w = new World(new Wardrobe());
        LevelWorld.populate(w, l, ScenerySprites.NONE, CombatTuning.defaults());
        assertEquals(3, w.stores().size(), "a cottage holds nothing, and a loot chest gives its loot");
        assertEquals(4, w.store(barrel.id()).items().amount(0));
        assertEquals(com.larsons.game.world.Store.Kind.CHEST, w.store(chest.id()).kind());
        // the chest for keeping things in is the 3D storage chest; the loot chest is the sprite it always was
        assertEquals(1, w.chests().size());
        assertNull(w.chests().get(0).store());
        assertEquals("storage_chest", w.building(chest.id()).key());
        w.player().setPosition(new Vec3(0, 0, 3.2));
        assertSame(w.store(chest.id()), w.reachableStore(), "opened from beside it");
        // what is asked in the properties panel: what it holds only where it holds anything
        ObjectType.Prop contents = ObjectTypes.get(ObjectTypes.CHEST).props().stream()
                .filter(p -> p.key().equals("contents")).findFirst().orElseThrow();
        assertTrue(ObjectTypes.applies(chest, contents));
        assertFalse(ObjectTypes.applies(loot, contents));
        assertTrue(ObjectTypes.palette().stream().anyMatch(e -> e.label().equals("Storage chest")));
        LevelWorld.spawnAll(w, List.of(), l, w.terrain(), ScenerySprites.NONE, CombatTuning.defaults());
        assertTrue(w.stores().isEmpty(), "taken away with the rest");
    }

    @Test
    void thingsSetOutForShowStayPutAndAWeaponRackShowsWhatItHolds() {
        Level l = Level.blank("hall", 64);
        LevelObject meat = l.add(ObjectTypes.SHOWN, 3, 0, 0.5, Map.of("item", "bear_meat"));
        meat.setLift(0.9);                                             // (on a counter)
        LevelObject rack = l.add(ObjectTypes.BUILDING, -3, 0, 0, Map.of("kind", "weapon_rack",
                "contents", "sword, longbow"));
        World w = new World(new Wardrobe());
        LevelWorld.populate(w, l, ScenerySprites.NONE, CombatTuning.defaults());
        World.GroundItem shown = w.items().stream().filter(g -> g.def.id().equals("bear_meat")).findFirst()
                .orElseThrow();
        assertTrue(shown.shown());
        assertEquals(0.9, shown.position.y(), 1e-6, "on the counter");
        assertEquals(shown.centre(0).y(), shown.centre(3.7).y(), 1e-9, "it does not bob");
        assertEquals(shown.spin(0), shown.spin(5), 1e-9, "nor turn");
        w.player().setPosition(new Vec3(3, 0, 0));
        assertNull(w.reachable(), "and is not to be picked up");
        // the rack: its weapons stand on its pegs, set out again as she hangs up another
        Store s = w.store(rack.id());
        assertEquals(Store.Kind.RACK, s.kind());
        assertFalse(s.alive(), "a rack does not break");
        assertEquals(2, w.items().stream().filter(g -> g.shown() && g != shown).count());
        s.items().add(com.larsons.game.world.Item.of(com.larsons.game.world.ItemDef.SWORD));
        w.tick(0.01);
        assertEquals(3, w.items().stream().filter(g -> g.shown() && g != shown).count());
        for (World.GroundItem g : w.items()) {
            if (g == shown) continue;
            assertTrue(g.position.horizontalDistance(new Vec3(-3, 0, 0)) < 0.7, "on the rack");
        }
        s.items().set(0, null);
        w.tick(0.01);
        assertEquals(2, w.items().stream().filter(g -> g.shown() && g != shown).count(), "one taken down");
    }

    @Test
    void aLevelKeepsItsLifeAndItsSwarmsOfCritters(@TempDir Path root) throws IOException {
        Level l = Level.blank("Pond", 64);
        assertFalse(LevelIO.json(l).contains("\"life\""), "nothing to write while it is as it comes");
        l.life().setDensity(com.larsons.game.ambient.CritterKind.FIREFLY, 2);
        l.life().setYield(0.5);
        l.add(ObjectTypes.CRITTERS, 3, -2, 0, Map.of("kind", "frog", "count", 5.0, "radius", 2.5, "when", "night"));
        Path folder = root.resolve("pond");
        LevelIO.save(l, folder);
        Level back = LevelIO.load(folder);
        assertEquals(2, back.life().density(com.larsons.game.ambient.CritterKind.FIREFLY), 1e-9);
        assertEquals(0.5, back.life().yield(), 1e-9);
        assertFalse(back.extra().containsKey("life"), "read as the level's own");
        com.larsons.game.world.World w = new com.larsons.game.world.World(new com.larsons.game.sprite.Wardrobe());
        LevelWorld.populate(w, back, com.larsons.game.sprite.ScenerySprites.NONE,
                CombatTuning.defaults());
        assertEquals(2, w.life().density(com.larsons.game.ambient.CritterKind.FIREFLY), 1e-9);
        assertEquals(1, w.critters().spots().size());
        com.larsons.game.ambient.Critters.Spot s = w.critters().spots().get(0);
        assertEquals(com.larsons.game.ambient.CritterKind.FROG, s.kind());
        assertEquals(5, s.count());
        assertEquals(com.larsons.game.ambient.Critters.When.NIGHT, s.when());
        assertEquals("Frogs (5)", ObjectTypes.label(back.objects().get(back.objects().size() - 1)));
    }
}
