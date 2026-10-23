package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.scene.DemoScene;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.SceneryProfile.Kind;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The demo's meadow: where the layout plants the trees, rocks and grass (the
 * same every run, clear of the spawn, the chest and the weapons, nothing on
 * top of anything solid), the wind running across it, and the player kept
 * out of the trees and rocks but not the grass.
 */
class SceneryTest {

    /** The art's sprites, without their files: an oak, three tufts and three rocks. */
    private static ScenerySprites sprites(boolean rocks) {
        List<ScenerySprites.Sprite> s = new ArrayList<>();
        s.add(sprite("oak_tree", new SceneryProfile(Kind.TREE, 128, 128, 48, 12, 4.8, 0.3, 1.6, false), 1024, 768));
        for (String t : List.of("grass_tuft_a", "grass_tuft_b", "grass_tuft_c")) {
            s.add(sprite(t, new SceneryProfile(Kind.TUFT, 32, 32, 24, 12, 0.6, 0, 0, false), 192, 128));
        }
        if (rocks) {
            s.add(sprite("boulder", new SceneryProfile(Kind.ROCK, 128, 128, 1, 1, 2.4, 0.6, 0.95, true), 128, 128));
            s.add(sprite("mossy_rock", new SceneryProfile(Kind.ROCK, 128, 128, 1, 1, 2.4, 0.45, 0.75, true), 128, 128));
            s.add(sprite("stones", new SceneryProfile(Kind.ROCK, 128, 128, 1, 1, 2.4, 0.6, 0.9, true), 128, 128));
        }
        return ScenerySprites.of(s, null);
    }

    private static ScenerySprites.Sprite sprite(String name, SceneryProfile p, int w, int h) {
        return ScenerySprites.describe(name, p, Path.of(name, "sheet.png"), w, h);
    }

    private static final List<SceneryLayout.Clearing> CLEAR = List.of(
            new SceneryLayout.Clearing(0, 0, 1.2), new SceneryLayout.Clearing(-0.3, -3.4, 1.3),
            new SceneryLayout.Clearing(1.4, -2.2, 0.6));

    private static List<Scenery.Placed> of(Scenery s, Kind kind) {
        return s.placed().stream().filter(p -> p.kind() == kind).toList();
    }

    @Test
    void theSameSeedPlantsTheSameMeadow() {
        Scenery a = SceneryLayout.demo(sprites(true), CLEAR), b = SceneryLayout.demo(sprites(true), CLEAR);
        assertEquals(a.placed().size(), b.placed().size());
        for (int i = 0; i < a.placed().size(); i++) {
            Scenery.Placed p = a.placed().get(i), q = b.placed().get(i);
            assertEquals(p.sprite().name(), q.sprite().name());
            assertEquals(p.x(), q.x(), 0);
            assertEquals(p.z(), q.z(), 0);
            assertEquals(p.yaw(), q.yaw(), 0);
            assertEquals(p.phase(), q.phase(), 0);
        }
        Scenery other = SceneryLayout.layout(sprites(true), CLEAR, 7);
        assertNotEquals(a.placed().get(10).x(), other.placed().get(10).x(), "another seed, another meadow");
    }

    @Test
    void itIsAMeadowOfTreesRocksAndGrass() {
        Scenery s = SceneryLayout.demo(sprites(true), CLEAR);
        List<Scenery.Placed> trees = of(s, Kind.TREE), rocks = of(s, Kind.ROCK), tufts = of(s, Kind.TUFT);
        assertTrue(trees.size() >= 30, trees.size() + " trees");
        assertTrue(rocks.size() >= 12, rocks.size() + " rocks");
        assertTrue(tufts.size() >= SceneryLayout.TUFTS * 0.9 && tufts.size() <= SceneryLayout.TUFTS,
                tufts.size() + " tufts");
        for (double[] spot : SceneryLayout.TREE_SPOTS) {
            assertTrue(trees.stream().anyMatch(t -> t.x() == spot[0] && t.z() == spot[1]),
                    "a tree where an old one stood, at " + spot[0] + ", " + spot[1]);
        }
        for (int i = 0; i < SceneryLayout.ROCK_SPOTS.length; i++) {
            double[] spot = SceneryLayout.ROCK_SPOTS[i];
            Scenery.Placed rock = rocks.stream().filter(r -> r.x() == spot[0] && r.z() == spot[1]).findFirst()
                    .orElseThrow(() -> new AssertionError("no rock at " + spot[0] + ", " + spot[1]));
            assertEquals(List.of("boulder", "mossy_rock", "stones").get(i), rock.sprite().name(),
                    "each old rock a different one");
        }
        assertTrue(trees.stream().anyMatch(t -> Math.hypot(t.x() - SceneryLayout.COTTAGE[0],
                t.z() - SceneryLayout.COTTAGE[1]) < 4), "trees where the cottage stood");
        assertTrue(trees.stream().allMatch(t -> t.position().horizontalLength() <= SceneryLayout.TREES_OUT_TO + 1e-9));
        assertTrue(tufts.stream().allMatch(t -> t.position().horizontalLength() <= SceneryLayout.TUFTS_OUT_TO + 1.5));
        assertEquals(trees.size() + rocks.size(), s.solids().size(), "the trees and rocks are in her way");
        assertEquals(trees.size() + rocks.size(), s.shadowed().size(), "and cast shadows; the grass does neither");
        assertEquals(7, s.bySprite().size(), "a group a sprite");
    }

    @Test
    void nothingIsPlantedInAClearingOrOnTopOfAnythingSolid() {
        Scenery s = SceneryLayout.demo(sprites(true), CLEAR);
        for (Scenery.Placed p : s.placed()) {
            for (SceneryLayout.Clearing c : CLEAR) {
                assertTrue(Math.hypot(p.x() - c.x(), p.z() - c.z()) >= c.radius() + p.footprint() - 1e-9,
                        p.sprite().name() + " at " + p.x() + ", " + p.z() + " is in a clearing");
            }
        }
        List<Scenery.Placed> all = s.placed();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Scenery.Placed a = all.get(i), b = all.get(j);
                double d = Math.hypot(a.x() - b.x(), a.z() - b.z());
                if (a.kind() == Kind.TREE && b.kind() == Kind.TREE) {
                    assertTrue(d >= SceneryLayout.TREE_SPACING - 1e-9, "two trees " + d + " m apart");
                } else if (a.kind() != Kind.TUFT && b.kind() != Kind.TUFT) {
                    assertTrue(d >= a.footprint() + b.footprint() + SceneryLayout.ROCK_GAP - 1e-9,
                            "a rock " + d + " m from something solid: room to walk between");
                } else if (a.kind() != b.kind()) {
                    assertTrue(d >= a.footprint() + b.footprint() - 1e-9, "a tuft inside a trunk or a rock");
                }
            }
        }
    }

    @Test
    void theDemosOwnClearingsAreKept() {
        List<SceneryLayout.Clearing> clear = DemoScene.clearings();
        assertTrue(clear.size() >= 6, "the spawn, the chest and every weapon");
        Scenery s = SceneryLayout.demo(sprites(true), clear);
        for (Scenery.Placed p : s.placed()) {
            assertTrue(p.position().horizontalLength() >= 1.2 + p.footprint() - 1e-9, "nothing at the spawn");
        }
    }

    @Test
    void aMissingKindIsLeftOutAndMovesNothingElse() {
        Scenery with = SceneryLayout.demo(sprites(true), CLEAR), without = SceneryLayout.demo(sprites(false), CLEAR);
        assertTrue(of(without, Kind.ROCK).isEmpty());
        List<Scenery.Placed> a = of(with, Kind.TREE), b = of(without, Kind.TREE);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).x(), b.get(i).x(), 0, "the trees stand where they stand, rocks or none");
            assertEquals(a.get(i).z(), b.get(i).z(), 0);
        }
        assertTrue(SceneryLayout.demo(ScenerySprites.NONE, CLEAR).isEmpty(), "no sprites, no scenery");
    }

    @Test
    void aGustRollsDownwindAcrossTheField() {
        // downwind is later: what stands farther along the wind runs behind what the gust reached first
        Vec3 w = Scenery.WIND;
        double near = Scenery.windPhase(0, 0, 0), far = Scenery.windPhase(w.x() * 6, w.z() * 6, 0);
        assertEquals(-6 / Scenery.GUST_SPEED, far - near, 1e-9);
        assertEquals(0, Scenery.windPhase(0, 0, 0), 1e-12);
        assertEquals(0.2, Scenery.windPhase(w.z(), -w.x(), 0.2), 1e-9, "across the wind: the same moment");
        Scenery s = SceneryLayout.demo(sprites(true), CLEAR);
        for (Scenery.Placed p : s.placed()) {
            int f = p.frame(0.37);
            assertTrue(f >= 0 && f < p.sprite().frames(), p.sprite().name() + " frame " + f);
            if (p.kind() == Kind.ROCK) assertEquals(0, p.phase(), 0, "a rock has one frame and no wind");
        }
    }

    @Test
    void sheCannotWalkIntoATreeOrARockButWalksThroughTheGrass() {
        ScenerySprites sp = sprites(true);
        Scenery s = new Scenery(List.of(
                new Scenery.Placed(sp.sprite("oak_tree"), 3, 0, 0, false, 0),
                new Scenery.Placed(sp.sprite("boulder"), -3, 0, 0, false, 0),
                new Scenery.Placed(sp.sprite("grass_tuft_a"), 0, 3, 0, false, 0)));
        World world = new World(new Wardrobe());
        world.setScenery(s);
        Player p = world.player();

        p.setPosition(new Vec3(3.1, 0, 0));
        world.tick(1 / 60.0);
        assertEquals(0.3 + World.FEET, p.ground().horizontalDistance(new Vec3(3, 0, 0)), 1e-9,
                "pushed out to the trunk's edge");
        p.setPosition(new Vec3(-3, 0, 0.2));
        world.tick(1 / 60.0);
        assertEquals(0.6 + World.FEET, p.ground().horizontalDistance(new Vec3(-3, 0, 0)), 1e-9,
                "and out of the rock");
        p.setPosition(new Vec3(0, 0, 3));
        world.tick(1 / 60.0);
        assertEquals(new Vec3(0, 0, 3), p.ground(), "standing in a tuft");

        world.setScenery(null);
        p.setPosition(new Vec3(3, 0, 0.1));
        world.tick(1 / 60.0);
        assertEquals(new Vec3(3, 0, 0.1), p.ground(), "the scenery hidden: nothing in her way");
    }
}
