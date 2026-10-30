package com.larsons.game.scene;

import com.larsons.game.combat.CombatTuning;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelIO;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.LevelWorld;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.SceneryProfile.Kind;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Scenery;
import com.larsons.game.world.SceneryLayout;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The demo as a Creative Mode level - the template "Copy the demo" starts
 * from: the highlands' ground, its paths, every tree, rock and tuft, the
 * weapons, chests, dummies, fliers, the cliff's vines and the named spots -
 * and that level played: the world it builds is the demo's.
 */
class DemoLevelTest {

    /** The art's sprites, without their files: an oak, a pine, three tufts and two rocks. */
    static ScenerySprites sprites() {
        List<ScenerySprites.Sprite> s = new ArrayList<>();
        s.add(sprite("oak_tree", new SceneryProfile(Kind.TREE, 128, 128, 48, 12, 4.8, 0.3, 1.6, false), 1024, 768));
        s.add(sprite("pine_tree", new SceneryProfile(Kind.TREE, 128, 160, 48, 12, 4.8, 0.25, 1.3, false), 1024, 960));
        for (String t : List.of("grass_tuft_a", "grass_tuft_b", "grass_tuft_c")) {
            s.add(sprite(t, new SceneryProfile(Kind.TUFT, 32, 32, 24, 12, 0.6, 0, 0, false), 192, 128));
        }
        s.add(sprite("boulder", new SceneryProfile(Kind.ROCK, 128, 128, 1, 1, 2.4, 0.6, 0.95, true), 128, 128));
        s.add(sprite("stones", new SceneryProfile(Kind.ROCK, 128, 128, 1, 1, 2.4, 0.6, 0.9, true), 128, 128));
        return ScenerySprites.of(s, null);
    }

    private static ScenerySprites.Sprite sprite(String name, SceneryProfile p, int w, int h) {
        return ScenerySprites.describe(name, p, Path.of(name, "sheet.png"), w, h);
    }

    private static Scenery demoScenery(ScenerySprites sprites) {
        return SceneryLayout.demo(sprites, DemoScene.clearings());
    }

    @Test
    void theDemoBecomesALevelWithEverythingInIt() {
        ScenerySprites sprites = sprites();
        Scenery scenery = demoScenery(sprites);
        Level l = DemoLevel.build("Demo copy", scenery);
        assertEquals("Demo copy", l.name());
        assertNotNull(l.spawn());
        assertEquals(List.of(1 + DemoScene.WEAPONS_AT.length, 2,
                        DemoScene.DUMMIES_AT.length + DemoScene.HIGHLAND_DUMMIES_AT.length, DemoScene.FLIERS_AT.length),
                DemoLevel.counts(l), "items, chests, dummies, fliers");
        assertEquals(scenery.placed().size(), l.ofType(ObjectTypes.SCENERY).size(), "every tree, rock and tuft");
        Terrain highlands = Terrain.highlands();
        assertEquals(highlands.walls().size(), l.ofType(ObjectTypes.VINES).size());
        assertEquals(highlands.places().size(), l.ofType(ObjectTypes.MARKER).size());
        for (double[] p : new double[][]{{0, 0}, {10, -30}, {-20, 15}, {8.8, -37.9}}) {
            assertEquals(highlands.height(p[0], p[1]), l.heights().height(p[0], p[1]), 0.02,
                    "the ground at " + p[0] + ", " + p[1]);
        }
        assertArrayEquals(scenery.ground().rgba(), l.ground().rgba(), "the paths and patches");
    }

    @Test
    void playedItIsTheDemosWorld() {
        ScenerySprites sprites = sprites();
        Level l = DemoLevel.build("Demo copy", demoScenery(sprites));
        World w = new World(new Wardrobe());
        LevelWorld.populate(w, l, sprites, CombatTuning.defaults());
        assertEquals(1 + DemoScene.WEAPONS_AT.length, w.items().size());
        assertEquals(2, w.chests().size());
        assertEquals(DemoScene.DUMMIES_AT.length + DemoScene.HIGHLAND_DUMMIES_AT.length, w.dummies().size());
        assertEquals(DemoScene.FLIERS_AT.length, w.fliers().size());
        assertEquals(l.ofType(ObjectTypes.SCENERY).size(), w.scenery().placed().size());
        Terrain t = w.terrain();
        Terrain highlands = Terrain.highlands();
        assertEquals(highlands.walls().size(), t.walls().size(), "the cliff's vines climb as they did");
        for (int i = 0; i < t.walls().size(); i++) {
            Terrain.Wall a = highlands.walls().get(i), b = t.walls().get(i);
            assertEquals(a.x0(), b.x0(), 0.05);
            assertEquals(a.z1(), b.z1(), 0.05);
            assertEquals(a.top(), b.top(), 0.6, "up to the plateau");
        }
        assertEquals(0, w.player().ground().horizontalLength(), 0.01, "she starts at the spawn");
        LevelObject spawn = l.spawn();
        assertEquals(spawn.yaw(), w.player().heading(), 1e-6, "facing its way");
        assertEquals(64, w.bounds()[1], 1e-9, "and cannot walk off its edge");
    }

    @Test
    void itSurvivesTheTripToDisk(@TempDir Path root) throws IOException {
        ScenerySprites sprites = sprites();
        Level l = DemoLevel.build("Demo copy", demoScenery(sprites));
        LevelIO.save(l, root.resolve("demo"));
        Level back = LevelIO.load(root.resolve("demo"));
        assertEquals(DemoLevel.counts(l), DemoLevel.counts(back));
        assertEquals(l.objects().size(), back.objects().size());
        assertArrayEquals(l.ground().rgba(), back.ground().rgba());
        assertEquals(l.heights().height(8.8, -37.9), back.heights().height(8.8, -37.9), 1 / 256.0);
    }
}
