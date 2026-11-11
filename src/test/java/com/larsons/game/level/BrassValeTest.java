package com.larsons.game.level;

import com.larsons.game.actor.Breed;
import com.larsons.game.actor.Folk;
import com.larsons.game.actor.Looks;
import com.larsons.game.actor.People;
import com.larsons.game.ambient.AnimalKind;
import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.combat.CombatTuning;
import com.larsons.game.overworld.Overworld;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.story.Quest;
import com.larsons.game.story.Quests;
import com.larsons.game.world.Goods;
import com.larsons.game.world.Inventory;
import com.larsons.game.world.Item;
import com.larsons.game.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Brass Vale, the first of the main lands: each of the map's eleven
 * levels as it is on file - that she can get in and out, that its people,
 * foes, shops and quests are of things the game has, and that every quest
 * can be done and every locked door opened.
 */
class BrassValeTest {

    private static final Path ASSETS = Path.of("assets");
    private static Overworld vale;
    private static final Map<String, Level> levels = new LinkedHashMap<>();

    @BeforeAll
    static void load() throws IOException {
        BuildingCatalog.use(BuildingCatalog.load(ASSETS.resolve("buildings")));
        vale = Overworld.byId(ASSETS, "brass_vale");
        assertNotNull(vale);
        for (Overworld.Node n : vale.nodes()) {
            if (n.playable()) levels.put(n.id, LevelIO.load(ASSETS.resolve("levels").resolve(n.level)));
        }
        assertEquals(11, levels.size());
    }

    /** "coins x60, leather x4" as the items it names; a fault for each that is nothing. */
    private static List<Item> items(String text, String where, List<String> faults) {
        List<Item> out = new ArrayList<>();
        for (String part : text.split(",")) {
            String s = part.strip().toLowerCase(Locale.ROOT).replaceAll("\\s*@\\s*\\d+$", "")
                    .replaceAll("\\s*[x×*]\\s*\\d+$", "").strip();
            if (s.isEmpty() || s.equals("cards") || s.startsWith("card:")) continue;
            Item item = Item.parse(s.replace(' ', '_'));
            if (item == null) item = Item.parse(s);
            if (item == null) faults.add(where + ": no such item \"" + s + "\"");
            else out.add(item);
        }
        return out;
    }

    @Test
    void sheCanGetIntoAndOutOfEveryLevelAndItCanBeClearedTheWayTheMapSays() {
        for (Overworld.Node n : vale.nodes()) {
            if (!n.playable()) continue;
            Level level = levels.get(n.id);
            assertEquals(1, level.ofType(ObjectTypes.SPAWN).size(), n.level + ": one way in");
            List<LevelObject> out = level.ofType(StoryTypes.WAY_OUT);
            assertFalse(out.isEmpty(), n.level + ": no way back to the map");
            LevelObject spawn = level.ofType(ObjectTypes.SPAWN).get(0);
            for (LevelObject o : level.objects()) {
                assertTrue(Math.abs(o.x()) <= level.half() && Math.abs(o.z()) <= level.half(),
                        n.level + ": " + o.type() + " " + o.id() + " is off the level");
            }
            switch (n.clear) {
                case "exit" -> {
                    List<LevelObject> far = out.stream().filter(o -> o.bool("clears", true)).toList();
                    assertFalse(far.isEmpty(), n.level + ": no way out that clears it");
                    for (LevelObject o : far) {
                        assertTrue(Math.hypot(o.x() - spawn.x(), o.z() - spawn.z()) > 40,
                                n.level + ": the way out that clears it is at the way in");
                    }
                }
                case "foes" -> assertFalse(level.ofType(FolkTypes.FOE).isEmpty(), n.level + ": no foes to clear");
                case "quest" -> assertTrue(level.ofType(FolkTypes.QUEST).stream()
                        .anyMatch(o -> n.quest.isBlank() || o.str(Quest.ID, "").equals(n.quest)),
                        n.level + ": no quest " + n.quest);
                default -> assertEquals("visit", n.clear);
            }
            // a way back that does not clear it is where she came in, so she can always turn round
            if (!n.clear.equals("visit")) {
                assertTrue(out.stream().anyMatch(o -> !o.bool("clears", true)), n.level + ": no way to turn back");
            }
        }
    }

    @Test
    void itsPeopleFoesShopsAndChestsAreOfThingsTheGameHas() {
        List<String> faults = new ArrayList<>();
        int people = 0, foes = 0, sellers = 0;
        Set<People> met = new HashSet<>();
        Set<Breed> fought = new HashSet<>();
        for (Map.Entry<String, Level> e : levels.entrySet()) {
            String id = e.getKey();
            for (LevelObject o : e.getValue().objects()) {
                String where = id + " " + o.type() + " " + o.str("name", String.valueOf(o.id()));
                for (String key : List.of("stock", "reward", "contents")) {
                    if (o.props().get(key) != null) items(o.str(key, ""), where, faults);
                }
                switch (o.type()) {
                    case FolkTypes.PERSON -> {
                        people++;
                        String name = o.str("name", "");
                        if (name.isBlank()) faults.add(where + ": no name");
                        String p = o.str("people", "human");
                        if (!People.playableKeys().contains(p)) faults.add(where + ": people " + p);
                        else met.add(People.byKey(p));
                        String outfit = o.str("outfit", FolkTypes.OWN);
                        if (!outfit.equals(FolkTypes.OWN) && !Looks.Dress.keys().contains(outfit)) {
                            faults.add(where + ": outfit " + outfit);
                        }
                        if (o.str("greeting", "").isBlank()) faults.add(where + ": nothing to say");
                        if (!o.str("stock", "").isBlank()) sellers++;
                        double size = o.num("size", 1);
                        if (size != 0 && (size < 0.5 || size > 1.5)) faults.add(where + ": size " + size);
                    }
                    case FolkTypes.FOE -> {
                        foes++;
                        Breed b = Breed.byKey(o.str("breed", ""));
                        if (b == null) faults.add(where + ": breed " + o.str("breed", ""));
                        else fought.add(b);
                        String weapon = o.str("weapon", FolkTypes.OWN);
                        if (!List.of(FolkTypes.OWN, FolkTypes.NONE, FolkTypes.ANY).contains(weapon)
                                && Item.parse(weapon.replace(' ', '_')) == null) {
                            faults.add(where + ": weapon " + weapon);
                        }
                    }
                    case ObjectTypes.SHOP -> sellers++;
                    case "animals" -> {
                        if (AnimalKind.byKey(o.str("kind", "")) == null) faults.add(where + ": " + o.str("kind", ""));
                    }
                    default -> { }
                }
            }
        }
        assertTrue(faults.isEmpty(), String.join("; ", faults));
        assertTrue(people >= 30, people + " people");
        assertTrue(foes >= 50, foes + " foes");
        assertTrue(sellers >= 8, sellers + " who sell");
        // every people of the main lands lives somewhere in the Vale, and every living foe is met in it
        assertEquals(new HashSet<>(People.playableOnes()), met);
        for (Breed b : Breed.living()) assertTrue(fought.contains(b), b.key() + " is nowhere");
        assertTrue(fought.stream().anyMatch(Breed::risen), "the marsh's dead");
    }

    @Test
    void everyQuestHasSomeoneToGiveItSomethingToDoAndSomethingForIt() {
        List<String> faults = new ArrayList<>();
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        Set<String> everyone = new HashSet<>();
        for (Level level : levels.values()) {
            for (LevelObject o : level.ofType(FolkTypes.PERSON)) everyone.add(o.str("name", ""));
        }
        Set<Quest.Kind> kinds = new HashSet<>();
        for (Map.Entry<String, Level> e : levels.entrySet()) {
            Level level = e.getValue();
            Set<String> here = new HashSet<>(), twice = new HashSet<>();
            for (LevelObject o : level.ofType(FolkTypes.PERSON)) {
                if (!here.add(o.str("name", ""))) twice.add(o.str("name", ""));
            }
            for (LevelObject o : level.ofType(FolkTypes.FOE)) here.add(o.str("name", ""));
            for (LevelObject o : level.ofType(FolkTypes.QUEST)) {
                String id = o.str(Quest.ID, ""), where = e.getKey() + " quest " + id;
                if (id.isBlank()) faults.add(where + ": no id (a save could not keep it)");
                // told in two levels (asked for in one, done in the other), it is the same quest in both
                Map<String, Object> said = new LinkedHashMap<>(o.props());
                Map<String, Object> before = byId.putIfAbsent(id, said);
                if (before != null && !before.equals(said)) faults.add(where + ": told two ways");
                if (!Quest.Kind.keys().contains(o.str("kind", ""))) {
                    faults.add(where + ": kind " + o.str("kind", ""));
                    continue;
                }
                Quest.Kind kind = Quest.Kind.byKey(o.str("kind", ""));
                kinds.add(kind);
                String giver = o.str("giver", ""), target = o.str("target", "");
                if (!everyone.contains(giver)) faults.add(where + ": nobody is called " + giver);
                // (two guards may both be "Guard"; whoever a quest is about is the only one of the name)
                if (twice.contains(giver) || twice.contains(target)) faults.add(where + ": two of that name here");
                for (String line : List.of("title", "offer", "remind", "thanks")) {
                    if (o.str(line, "").isBlank()) faults.add(where + ": no " + line);
                }
                if (items(o.str("reward", ""), where, faults).isEmpty()) faults.add(where + ": no reward");
                if (o.num("xp", 0) <= 0) faults.add(where + ": no experience");
                switch (kind) {
                    case TALK -> {
                        if (!everyone.contains(target)) faults.add(where + ": nobody to talk to called " + target);
                    }
                    case FETCH -> {
                        if (Item.parse(target.replace(' ', '_')) == null) faults.add(where + ": fetch " + target);
                    }
                    case KILL -> {
                        // someone with a name, wherever in the Vale they are - or enough of a kind, here
                        if (named(target)) break;
                        int there = count(level, target, o);
                        if (there < (int) o.num("count", 1)) {
                            faults.add(where + ": " + there + " of " + target + " to fell, "
                                    + (int) o.num("count", 1) + " asked for");
                        }
                    }
                    case CLEAR -> {
                        if (level.ofType(FolkTypes.FOE).isEmpty()) faults.add(where + ": nothing to clear");
                    }
                    case REACH -> {
                        if (o.num("radius", 0) <= 0) faults.add(where + ": nowhere to reach");
                    }
                }
            }
        }
        assertTrue(faults.isEmpty(), String.join("; ", faults));
        assertTrue(byId.size() >= 12, byId.size() + " quests");
        assertEquals(Set.of(Quest.Kind.values()), kinds, "one of every kind of quest");
        // the three Larson named: a large blue bear in its own place, an area cleared, someone found and talked to
        Map<String, Object> bear = byId.get("wood_blue_bear");
        assertNotNull(bear);
        assertEquals("bear", bear.get("target"));
        assertTrue(((Number) bear.get("radius")).doubleValue() > 0);
        assertEquals("clear", byId.get("millers_mill").get("kind"));
        assertEquals("talk", byId.get("oakhollow_smith").get("kind"));
    }

    @Test
    void aQuestGivenInOneLevelIsDoneInAnotherAndHandedInBackWhereItWasGiven() {
        // the Reeve of Brasswick sends her to the Magister at the observatory: one quest, told in both levels
        Map<String, Object> saved = new LinkedHashMap<>();
        World town = new World(new Wardrobe()), dome = new World(new Wardrobe());
        LevelWorld.populate(town, levels.get("brasswick"), ScenerySprites.NONE, CombatTuning.defaults());
        LevelWorld.populate(dome, levels.get("observatory"), ScenerySprites.NONE, CombatTuning.defaults());
        Quests inTown = new Quests(saved, town.quests(), "Brasswick");
        Quest word = inTown.byId("brasswick_magister");
        assertNotNull(word, "the quest keeps the id the level gives it");
        assertEquals("Reeve Aldous", word.giver());
        assertTrue(inTown.offered(word));
        inTown.accept(word);
        inTown.talked("Reeve Aldous");
        assertEquals(Quests.State.ACTIVE, inTown.state(word));

        Quests atDome = new Quests(saved, dome.quests(), "The Drurin Observatory");
        Quest there = atDome.byId("brasswick_magister");
        assertNotNull(there);
        assertEquals(Quests.State.ACTIVE, atDome.state(there), "under way when she gets there");
        atDome.talked("Magister Ilvaren");
        assertEquals(Quests.State.READY, atDome.state(there));

        Quests back = new Quests(saved, town.quests(), "Brasswick");
        assertEquals(Quests.State.READY, back.state(word));
        Inventory inv = new Inventory(Inventory.DEFAULT_SLOTS);
        assertTrue(back.canHandIn(word, inv));
        assertFalse(back.handIn(word, inv).isEmpty(), "and paid for");
        assertEquals(Quests.State.DONE, back.state(word));
        // the warlord of the watchtower is Brasswick's quarry the same way
        assertNotNull(inTown.byId("brasswick_warlord"));
        World tower = new World(new Wardrobe());
        LevelWorld.populate(tower, levels.get("watchtower"), ScenerySprites.NONE, CombatTuning.defaults());
        assertNotNull(new Quests(saved, tower.quests(), "The Old Watchtower").byId("brasswick_warlord"));
        assertTrue(tower.folks().all().stream().anyMatch(f -> "Warlord Thrakk".equals(f.name()) && f.hostile()));
    }

    /** Whether some foe or person anywhere in the Vale is called {@code name}. */
    private static boolean named(String name) {
        for (Level level : levels.values()) {
            for (LevelObject o : level.objects()) {
                if ((o.type().equals(FolkTypes.FOE) || o.type().equals(FolkTypes.PERSON))
                        && o.str("name", "").equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How many of {@code target} (a foe's breed, an animal's kind) {@code level} has within the quest's ring. */
    private static int count(Level level, String target, LevelObject quest) {
        Quest q = Quest.of("test", quest.id(), new com.larsons.game.math.Vec3(quest.x(), 0, quest.z()), quest.props());
        int n = 0;
        for (LevelObject o : level.objects()) {
            double reach = o.num("radius", 0) + o.num("roam", 0);
            boolean inside = q.radius() <= 0
                    || Math.hypot(o.x() - quest.x(), o.z() - quest.z()) <= q.radius() + reach;
            if (!inside) continue;
            if (o.type().equals(FolkTypes.FOE) && q.targets(o.str("breed", ""), o.str("name", null))) n++;
            if (o.type().equals("animals") && q.targets(o.str("kind", ""), null)) {
                n += o.bool("single", false) ? 1 : (int) o.num("count", 1);
            }
        }
        return n;
    }

    @Test
    void everyLockedDoorHasAKeySomewhereInTheVale() {
        Set<String> found = new HashSet<>();
        List<String> faults = new ArrayList<>();
        for (Map.Entry<String, Level> e : levels.entrySet()) {
            for (LevelObject o : e.getValue().objects()) {
                for (String key : List.of("stock", "reward", "contents")) {
                    if (o.props().get(key) == null) continue;
                    for (Item i : items(o.str(key, ""), e.getKey(), faults)) found.add(i.def().id());
                }
                if (o.type().equals(ObjectTypes.ITEM)) found.add(o.str("item", "").replace(' ', '_'));
            }
        }
        int locks = 0;
        for (Map.Entry<String, Level> e : levels.entrySet()) {
            for (LevelObject o : e.getValue().ofType(StoryTypes.LOCK)) {
                locks++;
                String key = o.str("key", "");
                if (key.isBlank()) continue;                    // (it opens for coins, or to anyone)
                Goods.Good g = Goods.get(key);
                assertNotNull(g, e.getKey() + ": " + o.str("name", "") + " wants " + key);
                assertEquals(Goods.Kind.KEY, g.kind());
                assertTrue(found.contains(key) || found.contains(Goods.SKELETON_KEY.id()),
                        e.getKey() + ": no " + key + " anywhere for " + o.str("name", ""));
            }
        }
        assertTrue(locks >= 2, "locked doors: " + locks);
        assertTrue(faults.isEmpty(), String.join("; ", faults));
    }

    @Test
    void everyLevelComesToLifeWithItsFolkItsQuestsAndItsShops() {
        for (Map.Entry<String, Level> e : levels.entrySet()) {
            String id = e.getKey();
            Level level = e.getValue();
            World world = new World(new Wardrobe());
            LevelWorld.populate(world, level, ScenerySprites.NONE, CombatTuning.defaults());
            int foes = level.ofType(FolkTypes.FOE).size(), people = level.ofType(FolkTypes.PERSON).size();
            long hostile = world.folks().all().stream().filter(Folk::hostile).count();
            assertEquals(foes, hostile, id + ": its foes");
            assertEquals(people, world.folks().all().size() - hostile, id + ": its people");
            assertEquals(level.ofType(FolkTypes.QUEST).size(), world.quests().size(), id + ": its quests");
            for (LevelObject o : level.ofType(FolkTypes.PERSON)) {
                String name = o.str("name", "");
                assertTrue(world.folks().all().stream().anyMatch(f -> name.equals(f.name()) && !f.hostile()), id + ": " + name);
            }
            for (Quest q : world.quests()) {
                assertFalse(q.id().isBlank());
                assertFalse(q.title().isBlank());
            }
            // nobody stands inside a building's walls, and she does not arrive in one
            LevelObject spawn = level.ofType(ObjectTypes.SPAWN).get(0);
            assertTrue(world.buildings().stream().noneMatch(b -> b.blocks(spawn.x(), spawn.z(), 0.3)),
                    id + ": she arrives inside a wall");
        }
    }
}
