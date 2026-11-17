package com.larsons.game.overworld;

import com.larsons.game.level.LevelIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The overworld map: its destinations and paths, where she may walk, and the map the game comes with. */
class OverworldTest {

    private static final Path ASSETS = Path.of("assets");

    /** home - gate (a level to clear) - far, and a side road home - open (free to pass) - far. */
    private static Overworld small() {
        Overworld map = new Overworld("Test", 24, 16);
        Overworld.Node home = map.add("Home", 2, 8);
        home.open = true;
        home.level = "home";
        Overworld.Node gate = map.add("Gate", 8, 8);
        gate.level = "gate";
        Overworld.Node far = map.add("Far", 14, 8);
        far.level = "far";
        Overworld.Node end = map.add("End", 20, 8);
        end.level = "end";
        map.link(home.id, gate.id, List.of());
        map.link(gate.id, far.id, List.of(new int[]{11, 6}));
        map.link(far.id, end.id, List.of());
        map.setStart(home.id);
        return map;
    }

    @Test
    void sheWalksOnOnlyFromAPlaceThatIsOpenOrCleared() {
        Overworld map = small();
        Overworld.Node home = map.start(), gate = map.node("gate"), far = map.node("far"), end = map.node("end");
        assertEquals("home", home.id);
        Set<String> cleared = new HashSet<>();
        assertTrue(map.passable(home, cleared), "an open place");
        assertFalse(map.passable(gate, cleared));
        // she can stand at the gate, but not go on from it
        assertTrue(map.reachable(gate, cleared));
        assertFalse(map.reachable(far, cleared));
        assertEquals(List.of("home", "gate"), map.route("home", "gate", cleared));
        assertTrue(map.route("home", "far", cleared).isEmpty());
        Overworld.Way on = map.way("gate", "far"), back = map.way("home", "gate");
        assertFalse(map.walkable(on, "gate", cleared), "onward is shut");
        assertTrue(map.walkable(back, "gate", cleared), "the way she came is not");
        // the gate cleared: on to the next, which stops her in turn
        cleared.add("gate");
        assertTrue(map.reachable(far, cleared));
        assertFalse(map.reachable(end, cleared));
        assertEquals(List.of("home", "gate", "far"), map.route("home", "far", cleared));
        cleared.add("far");
        assertEquals(List.of("home", "gate", "far", "end"), map.route("home", "end", cleared));
        assertEquals(List.of("end", "far", "gate", "home"), map.route("end", "home", cleared));
        // a place with no level is only a crossing
        gate.level = "";
        assertTrue(map.passable(gate, new HashSet<>()));
    }

    @Test
    void aPushOfTheStickTakesThePathThatSetsOffThatWay() {
        Overworld map = small();
        assertEquals(map.way("home", "gate"), map.toward("home", 1, 0));
        assertNull(map.toward("home", -1, 0), "nothing goes west of home");
        assertNull(map.toward("home", 0, 0));
        assertEquals(map.way("gate", "home"), map.toward("gate", -1, 0));
        // gate - far sets off up and to the right, by its bend
        assertEquals(map.way("gate", "far"), map.toward("gate", 1, -1));
        List<float[]> there = map.points(map.way("gate", "far"), "gate");
        List<float[]> back = map.points(map.way("gate", "far"), "far");
        assertEquals(3, there.size());
        assertArrayEquals(there.get(0), back.get(2));
        assertArrayEquals(new float[]{11.5f, 6.5f}, there.get(1));
    }

    @Test
    void aMapIsWrittenAndReadBackAsItWas(@TempDir Path dir) throws IOException {
        Overworld map = small();
        map.setMusic("shuffle");
        map.paint(3, 3, Overworld.TERRAINS.get(1));
        Overworld.Node gate = map.node("gate");
        gate.kind = "castle";
        gate.clear = "foes";
        gate.about = "A gate.";
        Overworld.Node end = map.node("end");
        end.clear = "quest";
        end.quest = "the_end";
        end.toMap = "next";
        Path file = dir.resolve("test.json");
        map.save(file);
        Overworld back = Overworld.load(file);
        assertEquals(map.json(), back.json());
        assertEquals("Test", back.name());
        assertEquals(24, back.width());
        assertEquals(16, back.height());
        assertEquals("home", back.start().id);
        assertEquals(Overworld.TERRAINS.get(1), back.terrain(3, 3));
        assertEquals("castle", back.node("gate").kind);
        assertEquals("foes", back.node("gate").clear);
        assertEquals("the_end", back.node("end").quest);
        assertTrue(back.node("end").travels());
        assertEquals(1, back.way("gate", "far").via.size());
        assertEquals(3, back.ways().size());
        // taking a destination off the map takes its paths with it
        back.remove(back.node("far"));
        assertNull(back.node("far"));
        assertEquals(1, back.ways().size());
    }

    @Test
    void theBrassValeIsTheMapTheGameStartsOnAndEveryDestinationHasItsLevel() throws IOException {
        assertEquals("brass_vale", Overworld.campaignStart(ASSETS));
        Overworld vale = Overworld.byId(ASSETS, "brass_vale");
        assertNotNull(vale);
        assertEquals("The Brass Vale", vale.name());
        assertEquals("oakhollow", vale.start().id);
        assertTrue(vale.start().open, "home is never shut");
        Set<String> ids = new HashSet<>(), levels = new HashSet<>();
        for (Overworld.Node n : vale.nodes()) {
            assertTrue(ids.add(n.id), n.id + " twice");
            assertTrue(vale.inside(n.x, n.y), n.id);
            assertFalse(vale.terrain(n.x, n.y).water(), n.id + " is in the water");
            assertTrue(Overworld.isKind(n.kind), n.id + ": " + n.kind);
            assertTrue(Overworld.CLEARS.contains(n.clear), n.id + ": " + n.clear);
            assertFalse(n.about.isBlank(), n.id);
            assertFalse(vale.waysAt(n.id).isEmpty(), n.id + " has no path to it");
            if (!n.playable()) continue;
            Path folder = ASSETS.resolve("levels").resolve(n.level);
            assertTrue(Files.isRegularFile(folder.resolve(LevelIO.FILE)), n.id + ": no level " + n.level);
            assertTrue(levels.add(n.level), n.level + " is two destinations'");
        }
        assertEquals(11, levels.size());
        for (Overworld.Way w : vale.ways()) {
            assertNotNull(vale.node(w.from), w.from);
            assertNotNull(vale.node(w.to), w.to);
            for (int[] v : w.via) assertTrue(vale.inside(v[0], v[1]), w.from + " - " + w.to);
        }
        // at the start she can reach the villages around home, and nothing past the mill until it is won
        Set<String> cleared = new HashSet<>();
        assertTrue(vale.reachable(vale.node("millers"), cleared));
        assertTrue(vale.reachable(vale.node("fishers"), cleared));
        assertFalse(vale.reachable(vale.node("brasswick"), cleared));
        assertFalse(vale.reachable(vale.node("gate"), cleared));
        // with every level cleared, every destination is reached
        for (Overworld.Node n : vale.nodes()) cleared.add(n.id);
        for (Overworld.Node n : vale.nodes()) {
            assertTrue(vale.reachable(n, cleared), n.id);
            assertFalse(vale.route("oakhollow", n.id, cleared).isEmpty(), n.id);
        }
    }

    @Test
    void mapsAreListedAndANewOneGetsANameOfItsOwn(@TempDir Path assets) throws IOException {
        assertTrue(Overworld.list(assets).isEmpty());
        assertEquals("", Overworld.campaignStart(assets), "no map yet");
        String id = Overworld.freeId(assets, "The North");
        assertFalse(id.isBlank());
        small().save(Overworld.folder(assets).resolve(id + ".json"));
        assertEquals(id, Overworld.campaignStart(assets), "the first there is");
        assertNotEquals(id, Overworld.freeId(assets, "The North"));
        assertEquals(1, Overworld.list(assets).size());
        assertEquals(id, Overworld.list(assets).get(0).id());
        Overworld.setCampaignStart(assets, id);
        assertEquals(id, Overworld.campaignStart(assets));
        assertNotNull(Overworld.byId(assets, id));
        assertNull(Overworld.byId(assets, "nowhere"));
    }

    @Test
    void theMainStorysMapIsKeptApartListedFirstAndNeverShadowed(@TempDir Path assets) throws IOException {
        Overworld story = small();
        story.setName("The Story");
        story.save(Overworld.storyFolder(assets).resolve("tale.json"));
        Overworld mine = small();
        mine.setName("Mine");
        mine.save(Overworld.folder(assets).resolve("mine.json"));
        // (a map of one's own under the story's id is never read in its place)
        mine.save(Overworld.folder(assets).resolve("tale.json"));
        List<Overworld.Saved> all = Overworld.list(assets);
        assertEquals(2, all.size());
        assertEquals("tale", all.get(0).id());
        assertTrue(all.get(0).story());
        assertEquals("Main story: The Story", all.get(0).shown());
        assertFalse(all.get(1).story());
        assertEquals("tale", Overworld.campaignStart(assets), "with no campaign.json: the story's");
        assertTrue(Overworld.byId(assets, "tale").story());
        assertEquals("The Story", Overworld.byId(assets, "tale").name());
        assertFalse(Overworld.byId(assets, "mine").story());
        assertNotEquals("tale", Overworld.freeId(assets, "Tale"), "a new map never takes the story's id");
        assertTrue(Overworld.byId(assets, "tale").copy().story(), "a copy for undoing is still the story's");
    }

    @Test
    void theBrassValeIsTheStorysOwn() {
        assertTrue(Overworld.isStory(ASSETS, "brass_vale"));
        assertFalse(Files.exists(Overworld.folder(ASSETS).resolve("brass_vale.json")), "not among the editable maps");
        assertTrue(Overworld.list(ASSETS).get(0).story());
    }

    @Test
    void aPictureRoadLooksAndMarksAreKeptInTheFile() {
        Overworld map = small();
        map.setBackground("my_land.png");
        map.setTilesShown(false);
        map.setRoadStyle("dashed");
        map.setSmoothRoads(true);
        map.node("home").kind = "standing_stone";
        Overworld back = Overworld.parse(map.json());
        assertEquals("my_land.png", back.background());
        assertFalse(back.tilesShown());
        assertEquals("dashed", back.roadStyle());
        assertTrue(back.smoothRoads());
        assertEquals("standing_stone", back.node("home").kind);
        assertEquals(Overworld.MARKERS.indexOf("standing_stone"), back.node("home").marker());
        assertEquals(-1, back.node("gate").marker(), "a painted icon");
        back.setRoadStyle("nonsense");
        assertEquals("dotted", back.roadStyle());
        back.setBackground("");
        assertTrue(back.tilesShown(), "with no picture the tiles always show");
        // a map with none of it writes none of it
        Map<String, Object> plain = small().json();
        assertFalse(plain.containsKey("background"));
        assertFalse(plain.containsKey("roadStyle"));
    }

    @Test
    void theMarksPictureHasEveryMarkFilledAndHollow() throws IOException {
        BufferedImage marks = ImageIO.read(ASSETS.resolve("overworld").resolve("markers.png").toFile());
        assertNotNull(marks);
        assertEquals(MapView.MARK * Overworld.MARKERS.size(), marks.getWidth());
        assertEquals(MapView.MARK * 2, marks.getHeight());
        for (String k : Overworld.MARKERS) {
            assertTrue(Overworld.isKind(k));
            assertFalse(Overworld.KINDS.contains(k), k + " is an icon's name too");
        }
    }

    @Test
    void movingPaintingAndCopying() {
        Overworld map = small();
        map.fillRect(0, 0, 2, 1, Overworld.terrain('m'));
        assertEquals('m', map.terrain(2, 1).key());
        assertEquals('w', map.terrain(3, 1).key());
        map.line(0, 5, 6, 5, 1, Overworld.terrain('s'));
        for (int x = 0; x <= 6; x++) assertEquals('s', map.terrain(x, 5).key());
        Overworld before = map.copy();
        map.shift(2, 1);
        assertEquals(4, map.node("home").x);
        assertEquals(9, map.node("home").y);
        assertEquals('m', map.terrain(4, 2).key());
        assertEquals(13, map.way("gate", "far").via.get(0)[0]);
        assertEquals(2, before.node("home").x, "the copy is apart");
        Overworld.Node c = map.addCopy(map.node("gate"), 1, 1);
        assertNotEquals("gate", c.id);
        assertEquals("gate", c.level);
        assertEquals(map.node("gate").kind, c.kind);
    }
}
