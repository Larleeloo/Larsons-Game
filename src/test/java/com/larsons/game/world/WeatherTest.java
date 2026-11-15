package com.larsons.game.world;

import com.larsons.game.level.Level;
import com.larsons.game.level.LevelIO;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The weather: fixed, by biome in spells, fading in and out; thunder; what it does to the sky; under a roof. */
class WeatherTest {

    private static final double DT = 0.1;

    @Test
    void fixedWeatherFadesInAndStays() {
        Weather w = Weather.NONE.withMode(Weather.Mode.FIXED).withKind(Weather.Kind.SNOW).withStrength(0.6);
        Weather.Now now = new Weather.Now(1);
        now.tick(DT, 0, w, Biome.PLAINS);
        assertEquals(Weather.Kind.SNOW, now.kind());
        assertTrue(now.level() < 0.05, "it comes in slowly");
        for (int i = 0; i < 200; i++) now.tick(DT, i * DT, w, Biome.PLAINS);
        assertEquals(0.6, now.level(), 1e-9);
        // another weather: the snow goes before the rain comes
        Weather rain = w.withKind(Weather.Kind.RAIN);
        for (int i = 0; i < 30; i++) now.tick(DT, 20 + i * DT, rain, Biome.PLAINS);
        assertEquals(Weather.Kind.SNOW, now.kind(), "still snowing, less");
        assertTrue(now.level() < 0.6);
        for (int i = 0; i < 400; i++) now.tick(DT, 30 + i * DT, rain, Biome.PLAINS);
        assertEquals(Weather.Kind.RAIN, now.kind());
        assertEquals(0.6, now.level(), 1e-9);
        // none: it clears
        for (int i = 0; i < 400; i++) now.tick(DT, 80 + i * DT, Weather.NONE, Biome.PLAINS);
        assertEquals(Weather.Kind.CLEAR, now.kind());
    }

    @Test
    void eachBiomeHasItsOwnWeatherInSpells() {
        Weather w = Weather.DEMO.withOften(0.6);
        for (Biome b : new Biome[]{Biome.MOUNTAINS, Biome.SAVANNAH, Biome.MARSH}) {
            Weather.Now now = new Weather.Now(2);
            Set<Weather.Kind> seen = EnumSet.noneOf(Weather.Kind.class);
            int wet = 0, steps = 0;
            for (double t = 0; t < Weather.SPELL * 200; t += 1) {
                now.tick(1, t, w, b);
                if (now.kind() != Weather.Kind.CLEAR) {
                    seen.add(now.kind());
                    wet++;
                }
                steps++;
            }
            assertTrue(Weather.of(b).containsAll(seen), b + ": " + seen);
            assertTrue(seen.contains(Weather.of(b).get(0)), b + " has its likeliest weather");
            double share = wet / (double) steps;
            assertTrue(share > 0.35 && share < 0.75, b + " has weather " + share + " of the time");
        }
        assertEquals(Weather.Kind.SANDSTORM, Weather.of(Biome.WASTE).get(0));
        assertEquals(Weather.Kind.SNOW, Weather.of(Biome.MOUNTAINS).get(0));
    }

    @Test
    void aThunderstormHasLightningAndThenThunder() {
        Weather w = Weather.NONE.withMode(Weather.Mode.FIXED).withKind(Weather.Kind.THUNDERSTORM).withStrength(1);
        Weather.Now now = new Weather.Now(3);
        now.set(Weather.Kind.THUNDERSTORM, 1);
        double brightest = 0;
        int heard = 0;
        for (int i = 0; i < 1200; i++) {
            now.tick(DT, i * DT, w, Biome.PLAINS);
            brightest = Math.max(brightest, now.flash());
            heard += now.drainThunder().size();
        }
        assertTrue(now.bolts() >= 5, "bolts: " + now.bolts());
        assertTrue(brightest > 0.6);
        assertTrue(heard >= now.bolts() - 1, "each bolt's thunder heard after it");
    }

    @Test
    void rainGreysTheSkyAndBringsTheFogInButNotUnderARoof() {
        World world = new World(new Wardrobe());
        world.setSky(Sky.DEFAULT);
        world.weatherNow().set(Weather.Kind.RAIN, 1);
        Sky wet = world.skyNow();
        assertTrue(wet.fog() < Sky.DEFAULT.fog() * 0.7, "the fog comes in");
        assertTrue(wet.air().clouds() > 0.9, "overcast");
        assertEquals(0, wet.air().rays(), 1e-9, "no light rays");
        assertTrue(lum(world.daylight().ambient()) < lum(world.clock().daylight(Sky.DEFAULT).ambient()), "dimmer");
        world.setCover(Sky.Cover.CAVE);
        assertTrue(world.roofed());
        assertEquals(Sky.DEFAULT, world.skyNow(), "in a cave the weather is not seen");
        world.setCover(Sky.Cover.OPEN);
        world.setIndoors(true);
        assertEquals(Sky.Cover.INSIDE, world.over());
        world.weatherNow().set(Weather.Kind.SANDSTORM, 1);
        world.setIndoors(false);
        int h = world.skyNow().horizon();
        assertTrue(((h >> 16) & 255) > (h & 255) + 40, "a sandstorm's sky is the sand's colour");
    }

    private static double lum(float[] c) {
        return c[0] + c[1] + c[2];
    }

    @Test
    void aLevelKeepsItsWeatherAndWhatIsOverIt(@TempDir Path root) throws Exception {
        Level l = Level.blank("Storm cave", 64);
        assertFalse(LevelIO.json(l).contains("\"weather\""), "nothing to write while it is as it comes");
        l.setWeather(Weather.NONE.withMode(Weather.Mode.FIXED).withKind(Weather.Kind.SANDSTORM).withStrength(0.5));
        l.setCover(Sky.Cover.CAVE);
        LevelIO.save(l, root.resolve("storm"));
        Level back = LevelIO.load(root.resolve("storm"));
        assertEquals(l.weather(), back.weather());
        assertEquals(Sky.Cover.CAVE, back.cover());
        assertFalse(back.extra().containsKey("weather"), "read as the level's own");
        World w = new World(new Wardrobe());
        com.larsons.game.level.LevelWorld.populate(w, back, com.larsons.game.sprite.ScenerySprites.NONE,
                com.larsons.game.combat.CombatTuning.defaults());
        assertEquals(Weather.Kind.SANDSTORM, w.weather().kind());
        assertEquals(Sky.Cover.CAVE, w.cover());
    }


    @Test
    void aLevelsOwnEventsComeAsOftenAsEachIsSetInSpellsAsLongAsSet(@TempDir Path root) throws Exception {
        Weather w = Weather.NONE.withMode(Weather.Mode.CUSTOM).withOften(0.5).withSpell(40).withStrength(0.8);
        for (Weather.Kind k : Weather.Kind.values()) w = w.withEvent(k, 0);
        assertEquals(Weather.Kind.CLEAR, w.eventFor(0.4), "no event set: clear skies");
        w = w.withEvent(Weather.Kind.SNOW, 1).withEvent(Weather.Kind.SANDSTORM, 0.25);
        assertEquals(1, w.event(Weather.Kind.SNOW), 1e-9);
        assertEquals(0, w.event(Weather.Kind.RAIN), 1e-9);
        assertEquals(0, w.withEvent(Weather.Kind.CLEAR, 1).event(Weather.Kind.CLEAR), 1e-9, "clear is no event");
        int snow = 0, sand = 0;
        for (int i = 0; i < 1000; i++) {
            Weather.Kind k = w.eventFor(i / 1000.0);
            if (k == Weather.Kind.SNOW) snow++;
            else if (k == Weather.Kind.SANDSTORM) sand++;
            else fail("only what is set comes: " + k);
        }
        assertEquals(800, snow, 2, "four times in five");
        assertEquals(200, sand, 2);

        // through the day: only those two, about half of the time, in spells of 40 seconds
        Weather.Now now = new Weather.Now(3);
        Set<Weather.Kind> seen = EnumSet.noneOf(Weather.Kind.class);
        int wet = 0, steps = 0;
        for (double t = 0; t < 40 * 600; t += 1) {
            now.tick(1, t, w, Biome.MARSH);
            if (now.kind() != Weather.Kind.CLEAR) {
                seen.add(now.kind());
                wet++;
            }
            steps++;
        }
        assertEquals(EnumSet.of(Weather.Kind.SNOW, Weather.Kind.SANDSTORM), seen, "whatever the biome");
        double share = wet / (double) steps;
        assertTrue(share > 0.3 && share < 0.7, "weather " + share + " of the time");
        assertEquals(Weather.SHORTEST_SPELL, w.withSpell(1).spell(), 1e-9);
        assertEquals(Weather.LONGEST_SPELL, w.withSpell(1e6).spell(), 1e-9);
        assertEquals(Weather.SPELL, Weather.DEMO.spell(), 1e-9, "the usual length unless a level says");

        // and a level keeps them
        Level l = Level.blank("Snowfield", 64);
        l.setWeather(w);
        LevelIO.save(l, root.resolve("snowfield"));
        Level back = LevelIO.load(root.resolve("snowfield"));
        assertEquals(w, back.weather());
        assertEquals(40, back.weather().spell(), 1e-9);
        assertEquals(0.25, back.weather().event(Weather.Kind.SANDSTORM), 1e-9);
    }
}
