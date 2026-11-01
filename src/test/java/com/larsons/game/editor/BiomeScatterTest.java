package com.larsons.game.editor;

import com.larsons.game.level.Level;
import com.larsons.game.level.LevelObject;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.world.Biome;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.WaterMap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** What the biome brush and the scatter brush's biome sets sow, and that nothing but reeds grows in water. */
class BiomeScatterTest {

    private static ScenerySprites.Sprite sprite(String name, SceneryProfile.Kind kind, List<String> biomes) {
        SceneryProfile p = new SceneryProfile(kind, 64, 64, 1, 1, 1.2, kind == SceneryProfile.Kind.TUFT ? 0 : 0.3,
                0.5, false, biomes, -1, false);
        return ScenerySprites.describe(name, p, Path.of(name + ".png"), 64, 64);
    }

    private static final ScenerySprites SPRITES = ScenerySprites.of(List.of(
            sprite("grass_tuft_a", SceneryProfile.Kind.TUFT, List.of()),
            sprite("oak_tree", SceneryProfile.Kind.TREE, List.of()),
            sprite("reeds", SceneryProfile.Kind.TUFT, List.of("marsh", "river")),
            sprite("willow_tree", SceneryProfile.Kind.TREE, List.of("marsh", "river")),
            sprite("fern", SceneryProfile.Kind.TUFT, List.of("jungle", "plains"))), null);

    @Test
    void eachBiomeSowsWhatNamesItAndThePlainsTheMeadowsOwnToo() {
        assertEquals(List.of("reeds"), Scatter.biomeSet(SPRITES, Biome.MARSH, SceneryProfile.Kind.TUFT).sprites());
        assertEquals(List.of("willow_tree"), Scatter.biomeSet(SPRITES, Biome.RIVER, SceneryProfile.Kind.TREE)
                .sprites());
        assertEquals(List.of("grass_tuft_a", "fern"),
                Scatter.biomeSet(SPRITES, Biome.PLAINS, SceneryProfile.Kind.TUFT).sprites());
        assertTrue(Scatter.biomeSet(SPRITES, Biome.WASTE, SceneryProfile.Kind.TREE).sprites().isEmpty());
        List<String> labels = Scatter.sets(SPRITES).stream().map(Scatter.Set::label).toList();
        assertEquals("Grass (mixed)", labels.get(0));
        assertEquals(List.of("grass_tuft_a"), Scatter.sets(SPRITES).get(0).sprites(), "the meadow's own, mixed");
        assertTrue(labels.contains("Marsh: plants") && labels.contains("Jungle: plants"), labels.toString());
        assertFalse(labels.contains("Waste: trees"), "none grow there");
    }

    @Test
    void onlyTheWetBiomesPlantsStandInWater() {
        Level level = Level.blank("Pond", 64);
        level.water().paint(WaterMap.Kind.SWAMP, 0.3, 0, 0, 6, 0, 0);
        Random random = new Random(1);
        List<LevelObject> reeds = Scatter.sow(level, Terrain.FLAT, SPRITES,
                Scatter.biomeSet(SPRITES, Biome.MARSH, SceneryProfile.Kind.TUFT), 0, 0, 5, 1, 60, random);
        assertFalse(reeds.isEmpty(), "reeds wade in");
        List<LevelObject> trees = Scatter.sow(level, Terrain.FLAT, SPRITES,
                Scatter.biomeSet(SPRITES, Biome.MARSH, SceneryProfile.Kind.TREE), 0, 0, 5, 1, 400, random);
        assertTrue(trees.isEmpty(), "no willow stands in the pond");
    }

    @Test
    void theForagingBushesAreSownApartAndThinly() {
        SceneryProfile bushProfile = new SceneryProfile(SceneryProfile.Kind.TUFT, 64, 64, 1, 1, 1.2, 0, 0.5, false,
                List.of("plains"), -1, false, new SceneryProfile.Forage("wild_berries", 2, 4, 240, "berry_bush_picked"),
                null);
        SceneryProfile bareProfile = new SceneryProfile(SceneryProfile.Kind.TUFT, 64, 64, 1, 1, 1.2, 0, 0.5, false,
                List.of(), -1, false, null, "berry_bush");
        ScenerySprites sprites = ScenerySprites.of(List.of(
                sprite("grass_tuft_a", SceneryProfile.Kind.TUFT, List.of()),
                ScenerySprites.describe("berry_bush", bushProfile, Path.of("b.png"), 64, 64),
                ScenerySprites.describe("berry_bush_picked", bareProfile, Path.of("p.png"), 64, 64)), null);
        assertEquals(List.of("grass_tuft_a"), Scatter.biomeSet(sprites, Biome.PLAINS, SceneryProfile.Kind.TUFT)
                .sprites(), "not among the grass");
        assertEquals(List.of("berry_bush"), Scatter.bushSet(sprites, Biome.PLAINS).sprites());
        List<String> labels = Scatter.sets(sprites).stream().map(Scatter.Set::label).toList();
        assertTrue(labels.contains("Plains: bushes"), labels.toString());
        assertFalse(labels.contains("Berry bush picked"), "the bare look is not sown on its own");
        Level level = Level.blank("Meadow", 64);
        List<LevelObject> sown = Scatter.sow(level, Terrain.FLAT, sprites, Scatter.bushSet(sprites, Biome.PLAINS),
                0, 0, 10, 1, Math.PI * 100, new Random(3));
        assertTrue(sown.size() > 3 && sown.size() < 20, "about one every 30 square metres: " + sown.size());
    }
}
