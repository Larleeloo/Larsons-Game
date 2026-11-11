package com.larsons.game.level;

import com.larsons.game.actor.Breed;
import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.combat.CombatTuning;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Item;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The horde game: how its rounds grow, its rules kept with a level, and the maps it comes with. */
class HordeTest {

    @Test
    void eachRoundBringsMoreOfThemQuickerAndTougher() {
        HordeRules r = HordeRules.DEFAULT;
        assertEquals(6, r.count(1));
        assertEquals(12, r.upAtOnce(1));
        assertEquals(1, r.health(1), 1e-9);
        for (int n = 2; n <= 60; n++) {
            assertTrue(r.count(n) > r.count(n - 1) || r.count(n) == 400, "round " + n);
            assertTrue(r.upAtOnce(n) >= r.upAtOnce(n - 1));
            assertTrue(r.health(n) > r.health(n - 1));
            assertTrue(r.every(n) <= r.every(n - 1));
            assertTrue(r.upAtOnce(n) <= 40 && r.count(n) <= 400 && r.every(n) >= 0.35, "round " + n);
        }
        assertEquals(6 + 3 * 9 + 100 / 6, r.count(10));
        assertEquals(1 + 0.12 * 9, r.health(10), 1e-9);
        assertEquals(21, r.upAtOnce(10));
        // a felled one pays more than the blows it took
        assertTrue(HordeRules.KILL > HordeRules.HIT);
        // every Risen there is comes when the map names none; only those it names when it does
        assertFalse(HordeRules.risen().isEmpty());
        for (Breed b : HordeRules.risen()) {
            assertTrue(b.risen());
            assertTrue(r.comes(b), b.key());
        }
        HordeRules picky = new HordeRules(500, 6, 3, 12, 0.12, 8, 1, "", List.of("risen_runner"));
        assertTrue(picky.comes(Breed.RISEN_RUNNER));
        assertFalse(picky.comes(Breed.RISEN_WALKER));
    }

    @Test
    void rulesOutOfBoundsAreBroughtIn() {
        HordeRules r = new HordeRules(-5, 0, -1, 0, 9, 0, 0, null, null);
        assertEquals(0, r.points());
        assertEquals(1, r.first());
        assertEquals(0, r.more());
        assertEquals(1, r.atOnce());
        assertEquals(1, r.growth(), 1e-9);
        assertEquals(1, r.rest(), 1e-9);
        assertEquals(1, r.lives());
        assertEquals("", r.kit());
        assertTrue(r.kinds().isEmpty());
        HordeRules big = new HordeRules(1 << 30, 9999, 9999, 9999, 0.3, 9999, 99, "sword", List.of());
        assertEquals(100000, big.points());
        assertEquals(200, big.first());
        assertEquals(50, big.more());
        assertEquals(40, big.atOnce());
        assertEquals(60, big.rest(), 1e-9);
        assertEquals(9, big.lives());
        assertEquals(400, big.count(30));
        assertEquals(7, HordeRules.DEFAULT.withLives(7).lives());
        assertEquals(900, HordeRules.DEFAULT.withPoints(900).points());
    }

    @Test
    void aLevelIsAHordeMapWhenItHasTheRulesAndKeepsThemWhenSaved(@TempDir Path dir) throws IOException {
        Level level = Level.blank("Pit", 64);
        assertFalse(HordeRules.has(level));
        assertNull(HordeRules.of(level));
        assertFalse(HordeRules.has(null));
        HordeRules rules = new HordeRules(750, 8, 4, 14, 0.2, 15, 3, "sword, health potion x2",
                List.of("risen_walker", "risen_brute"));
        rules.put(level);
        assertTrue(HordeRules.has(level));
        assertEquals(rules, HordeRules.of(level));
        level.add(HordeTypes.SPAWN, 10, 4, 0, new java.util.LinkedHashMap<>(java.util.Map.of("from", 2.0)));
        Path folder = dir.resolve("pit");
        LevelIO.save(level, folder);
        Level back = LevelIO.load(folder);
        assertEquals(rules, HordeRules.of(back));
        assertEquals(1, back.ofType(HordeTypes.SPAWN).size());
        // a kind the game does not have is dropped; a number missing is the usual
        java.util.Map<String, Object> kept = new java.util.LinkedHashMap<>();
        ((java.util.Map<?, ?>) back.extra().get(HordeRules.KEY)).forEach((k, v) -> kept.put(String.valueOf(k), v));
        kept.put("kinds", List.of("risen_brute", "ghoul"));
        kept.remove("rest");
        back.extra().put(HordeRules.KEY, kept);
        HordeRules read = HordeRules.of(back);
        assertEquals(List.of("risen_brute"), read.kinds());
        assertEquals(HordeRules.DEFAULT.rest(), read.rest(), 1e-9);
    }

    @Test
    void aRisenFromASpawnMakesForJustOutsideItsBarricade() {
        Vec3 spawn = new Vec3(0, 0, -20), boards = new Vec3(0, 0, -11);
        Vec3 at = HordeTypes.outside(spawn, boards);
        assertEquals(0, at.x(), 1e-9);
        assertEquals(-11.95, at.z(), 1e-9);
        assertEquals(boards, HordeTypes.outside(boards, boards));
        // what a board can sell: weapons, ammunition, potions and scrolls - and nothing twice
        List<String> sold = HordeTypes.sold();
        assertTrue(sold.containsAll(List.of("sword", "longbow", "wand", "health_potion", "scroll_of_fury")));
        assertEquals(sold.size(), new HashSet<>(sold).size());
        for (String id : sold) assertNotNull(Item.parse(id), id);
    }

    @Test
    void theHordeMapsLoadWithAWayInForTheRisenBoardsToBuyAtAndABox() throws IOException {
        BuildingCatalog.use(BuildingCatalog.load(Path.of("assets", "buildings")));
        List<Path> maps;
        try (Stream<Path> s = Files.list(Path.of("assets", "horde"))) {
            maps = s.filter(Files::isDirectory).sorted().toList();
        }
        assertTrue(maps.size() >= 2);
        for (Path folder : maps) {
            String id = folder.getFileName().toString();
            Level level = LevelIO.load(folder);
            HordeRules rules = HordeRules.of(level);
            assertNotNull(rules, id + " is no horde map");
            assertFalse(rules.kit().isBlank(), id);
            for (String thing : rules.kit().split(",")) {
                String what = thing.trim().replaceAll("\\s+x\\d+$", "").replace(' ', '_');
                assertNotNull(Item.parse(what), id + " starts her with " + what);
            }
            assertEquals(1, level.ofType(ObjectTypes.SPAWN).size(), id);
            assertEquals(1, level.ofType(HordeTypes.BOX).size(), id);
            assertTrue(level.ofType(HordeTypes.WALL_BUY).size() >= 8, id);
            Set<String> doors = new HashSet<>();
            for (LevelObject o : level.ofType(StoryTypes.LOCK)) doors.add(o.str("name", ""));
            boolean atOnce = false;
            for (LevelObject o : level.ofType(HordeTypes.SPAWN)) {
                String kind = o.str("kind", HordeTypes.ANY), after = o.str("after", "");
                if (!kind.equals(HordeTypes.ANY)) {
                    Breed b = Breed.byKey(kind);
                    assertTrue(b != null && b.risen(), id + ": " + kind);
                }
                if (!after.isEmpty()) assertTrue(doors.contains(after), id + ": no door called " + after);
                atOnce |= o.num("from", 1) <= 1 && after.isEmpty() && kind.equals(HordeTypes.ANY);
            }
            assertTrue(atOnce, id + ": nothing comes in the first round");
            int cheapest = Integer.MAX_VALUE;
            for (LevelObject o : level.ofType(HordeTypes.WALL_BUY)) {
                String item = o.str("item", ""), tier = o.str("tier", HordeTypes.LOWEST);
                assertTrue(HordeTypes.sold().contains(item), id + " sells " + item);
                if (!tier.equals(HordeTypes.LOWEST)) assertNotNull(Item.parse(item + ":" + tier), id + ": " + item + " " + tier);
                assertTrue(o.num("cost", 0) > 0, id + ": " + item + " is free");
                cheapest = Math.min(cheapest, (int) o.num("cost", 0));
            }
            assertTrue(cheapest <= rules.points(), id + ": she can buy nothing with what she starts with");

            World world = new World(new Wardrobe());
            LevelWorld.populate(world, level, ScenerySprites.NONE, CombatTuning.defaults());
            assertEquals(level.ofType(HordeTypes.SPAWN).size(), world.hordeSpawns().size(), id);
            // a spawn is outside the middle she holds, and not in a building
            for (World.HordeSpawn s : world.hordeSpawns()) {
                assertTrue(world.buildings().stream().noneMatch(b -> b.blocks(s.at().x(), s.at().z(), 0.3)),
                        id + ": a spawn inside a building at " + s.at());
            }
        }
    }
}
