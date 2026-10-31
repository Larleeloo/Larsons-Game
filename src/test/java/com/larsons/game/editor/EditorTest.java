package com.larsons.game.editor;

import com.larsons.game.audio.Songs;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.LevelWorld;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.SceneryProfile.Kind;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.world.GroundMap;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.SceneryLayout;
import com.larsons.game.world.Sky;
import com.larsons.game.world.Terrain;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The level editor's workings without a window: undo and redo of every kind
 * of change, the scatter brush keeping trees apart and off cliffs, and
 * pointing at the ground and at things through the camera.
 */
class EditorTest {

    private static ScenerySprites sprites() {
        List<ScenerySprites.Sprite> s = new ArrayList<>();
        s.add(sprite("oak_tree", new SceneryProfile(Kind.TREE, 128, 128, 48, 12, 4.8, 0.3, 1.6, false), 1024, 768));
        s.add(sprite("pine_tree", new SceneryProfile(Kind.TREE, 128, 160, 48, 12, 4.8, 0.25, 1.3, false), 1024, 960));
        s.add(sprite("grass_tuft_a", new SceneryProfile(Kind.TUFT, 32, 32, 24, 12, 0.6, 0, 0, false), 192, 128));
        s.add(sprite("boulder", new SceneryProfile(Kind.ROCK, 128, 128, 1, 1, 2.4, 0.6, 0.95, true), 128, 128));
        return ScenerySprites.of(s, null);
    }

    private static ScenerySprites.Sprite sprite(String name, SceneryProfile p, int w, int h) {
        return ScenerySprites.describe(name, p, Path.of(name, "sheet.png"), w, h);
    }

    @Test
    void everyKindOfChangeUndoesAndRedoes() {
        Level l = Level.blank("u", 64);
        Undo undo = new Undo();
        assertFalse(undo.canUndo());

        undo.before(l, Undo.Part.OBJECTS, "Place");
        l.add(ObjectTypes.DUMMY, 3, 3, 0, Map.of());
        undo.before(l, Undo.Part.HEIGHTS, "Sculpt");
        l.heights().raise(0, 0, 4, 2);
        undo.before(l, Undo.Part.GROUND, "Paint");
        l.ground().paint(GroundMap.DIRT, 0, 0, 2, 1);
        undo.before(l, Undo.Part.SKY, "Sky");
        l.setSky(l.sky().withFog(200));
        undo.before(l, Undo.Part.SKY, "Music");
        l.setMusic("dream_on");

        assertEquals("Music", undo.nextUndo());
        assertEquals("Music", undo.undo(l));
        assertEquals(Songs.SHUFFLE, l.music());
        assertEquals("Sky", undo.undo(l));
        assertEquals(Sky.DEFAULT.fog(), l.sky().fog(), 1e-9);
        assertEquals("Paint", undo.undo(l));
        assertTrue(l.ground().isEmpty());
        assertEquals("Sculpt", undo.undo(l));
        assertTrue(l.heights().flat());
        assertEquals("Place", undo.undo(l));
        assertTrue(l.ofType(ObjectTypes.DUMMY).isEmpty());
        assertNull(undo.undo(l), "nothing further back");

        assertEquals("Place", undo.redo(l));
        assertEquals(1, l.ofType(ObjectTypes.DUMMY).size());
        assertEquals("Sculpt", undo.redo(l));
        assertEquals(2, l.heights().height(0, 0), 0.05);
        assertEquals("Paint", undo.redo(l));
        assertEquals("Sky", undo.redo(l));
        assertEquals("Music", undo.redo(l));
        assertEquals("dream_on", l.music());
        assertEquals("Music", undo.undo(l));
        assertEquals("Music", undo.nextRedo());
        undo.before(l, Undo.Part.OBJECTS, "Delete");
        assertFalse(undo.canRedo(), "a new change drops what could have been redone");
    }

    @Test
    void theSongPlayersClockReadsMinutesAndSeconds() {
        assertEquals("0:00", CreativeScene.clock(0));
        assertEquals("1:05", CreativeScene.clock(65.9));
        assertEquals("2:00", CreativeScene.clock(120));
    }

    @Test
    void resizingUndoesWithEverythingItCutOff() {
        Level l = Level.blank("r", 128);
        l.heights().raise(50, 50, 4, 3);
        l.ground().paint(GroundMap.GRAVEL, 50, 50, 3, 1);
        l.add(ObjectTypes.DUMMY, 50, 50, 0, Map.of());
        Undo undo = new Undo();
        undo.before(l, Undo.Part.ALL, "Resize");
        l.resize(64);
        assertTrue(l.ofType(ObjectTypes.DUMMY).isEmpty());
        undo.undo(l);
        assertEquals(128, l.size(), 1e-9);
        assertEquals(1, l.ofType(ObjectTypes.DUMMY).size());
        assertEquals(3, l.heights().height(50, 50), 0.05);
        assertEquals(GroundMap.GRAVEL, l.ground().material(50, 50));
        undo.redo(l);
        assertEquals(64, l.size(), 1e-9);
    }

    @Test
    void undoGoesBackSoFar() {
        Level l = Level.blank("d", 64);
        Undo undo = new Undo();
        for (int i = 0; i < Undo.DEPTH + 10; i++) undo.before(l, Undo.Part.SKY, "step " + i);
        int n = 0;
        while (undo.undo(l) != null) n++;
        assertEquals(Undo.DEPTH, n);
    }

    @Test
    void scatteringKeepsTreesApartAndOnTheLevel() {
        ScenerySprites sprites = sprites();
        Level l = Level.blank("s", 64);
        Terrain t = LevelWorld.terrain(l);
        Scatter.Set trees = Scatter.sets(sprites).stream().filter(s -> s.label().startsWith("Trees")).findFirst()
                .orElseThrow();
        assertEquals(List.of("oak_tree", "pine_tree"), trees.sprites());
        Random r = new Random(3);
        for (int i = 0; i < 40; i++) Scatter.sow(l, t, sprites, trees, 28, 0, 10, 1, 300, r);
        List<LevelObject> sown = l.ofType(ObjectTypes.SCENERY);
        assertTrue(sown.size() > 5, "some trees: " + sown.size());
        for (LevelObject a : sown) {
            assertTrue(l.contains(a.x(), a.z()), "on the level");
            for (LevelObject b : sown) {
                if (a == b) continue;
                assertTrue(Math.hypot(a.x() - b.x(), a.z() - b.z()) >= SceneryLayout.TREE_SPACING - 1e-9);
            }
        }
        int pulled = Scatter.clear(l, trees, 28, 0, 30);
        assertEquals(sown.size(), pulled);
        assertTrue(l.ofType(ObjectTypes.SCENERY).isEmpty());
    }

    @Test
    void nothingIsSownOnACliff() {
        ScenerySprites sprites = sprites();
        Level l = Level.blank("c", 64);
        for (int k = 0; k < 30; k++) l.heights().raise(0, 0, 3, 3);
        Terrain t = LevelWorld.terrain(l);
        Scatter.Set rocks = Scatter.sets(sprites).stream().filter(s -> s.label().startsWith("Rocks")).findFirst()
                .orElseThrow();
        Scatter.sow(l, t, sprites, rocks, 0, 0, 2.5, 1, 5000, new Random(1));
        for (LevelObject o : l.ofType(ObjectTypes.SCENERY)) {
            assertTrue(t.slope(o.x(), o.z()) <= SceneryLayout.STEEPEST + 1e-9, "a rock on a slope of "
                    + t.slope(o.x(), o.z()));
        }
    }

    @Test
    void theMiddleOfTheViewPointsAtWhatTheCameraLooksAt() {
        OrbitCamera cam = new OrbitCamera(0.3, Math.toRadians(40), 20);
        Vec3 focus = new Vec3(5, 0, -3);
        cam.snapTo(focus);
        Picking.Ray ray = Picking.ray(cam, 640, 360, 1280, 720);
        Vec3 hit = Picking.ground(ray, Terrain.FLAT);
        assertNotNull(hit);
        assertEquals(cam.target().x(), hit.x(), 0.05);
        assertEquals(cam.target().z(), hit.z(), 0.05);
        assertEquals(0, hit.y(), 1e-9);
        assertTrue(Picking.cylinder(ray, cam.target().x(), cam.target().z(), 0.5, -1, 2) > 0, "a thing standing there");
        assertEquals(-1, Picking.cylinder(ray, cam.target().x() + 5, cam.target().z(), 0.5, -1, 2), 1e-9,
                "and not one off to the side");
        Picking.Ray up = new Picking.Ray(new Vec3(0, 5, 0), new Vec3(0, 1, 0));
        assertNull(Picking.ground(up, Terrain.FLAT), "looking at the sky");
    }

    @Test
    void aHillStopsTheRayBeforeTheGroundBehindIt() {
        Level l = Level.blank("h", 64);
        for (int k = 0; k < 4; k++) l.heights().raise(0, 0, 6, 2);
        Terrain t = LevelWorld.terrain(l);
        Picking.Ray ray = new Picking.Ray(new Vec3(-20, 3, 0), new Vec3(1, 0, 0));
        Vec3 hit = Picking.ground(ray, t);
        assertNotNull(hit);
        assertTrue(hit.x() < 0 && hit.x() > -6, "on the hill's near side: " + hit);
        assertEquals(3, hit.y(), 0.05);
    }
}
