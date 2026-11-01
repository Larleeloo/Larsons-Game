package com.larsons.game.world;

import com.larsons.game.gfx.SceneLight;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelIO;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.math.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The time of day: the sun's round, the planet that never moves but waxes
 * and wanes with it, the light from noon to the planet-lit night, the clock
 * and the time-lapse, the mist and the light rays by the hour, the lights of
 * their own, and a level's time and weather kept in its file.
 */
class DayCycleTest {

    @Test
    void theSunRisesInTheEastStandsSouthAtNoonAndSetsInTheWest() {
        Vec3 dawn = DayCycle.sun(6), noon = DayCycle.sun(12), dusk = DayCycle.sun(18), midnight = DayCycle.sun(0);
        assertEquals(1, dawn.x(), 1e-9, "east at six");
        assertEquals(0, dawn.y(), 1e-9, "on the horizon at six");
        assertEquals(-1, dusk.x(), 1e-9, "west at six in the evening");
        assertTrue(noon.z() > 0, "south (z) at noon");
        assertEquals(DayCycle.NOON_ELEVATION, DayCycle.sunElevation(12), 1e-9);
        assertEquals(-DayCycle.NOON_ELEVATION, DayCycle.sunElevation(0), 1e-9, "as far below the north at midnight");
        assertTrue(midnight.z() < 0);
        assertTrue(DayCycle.sunElevation(9) > 0 && DayCycle.sunElevation(9) < DayCycle.sunElevation(12));
    }

    @Test
    void thePlanetHangsWhereItIsPutAndIsFullWhenTheSunIsOppositeIt() {
        Atmosphere air = Atmosphere.DEFAULT.withPlanetAt(180, 20);
        Vec3 p = DayCycle.planet(air);
        assertEquals(0, p.x(), 1e-9);
        assertTrue(p.z() > 0, "180 degrees is south");
        assertEquals(Math.sin(Math.toRadians(20)), p.y(), 1e-9);
        Sky sky = Sky.DEFAULT.withAir(air);
        double noon = DayCycle.of(sky, 12).planetLit(), midnight = DayCycle.of(sky, 0).planetLit();
        assertTrue(noon < 0.25, "a crescent by day, near the sun: " + noon);
        assertTrue(midnight > 0.75, "near full at midnight: " + midnight);
        assertEquals(0, DayCycle.of(Sky.DEFAULT.withAir(air.withPlanet(false)), 0).planetLit(), "no planet, no light");
    }

    @Test
    void theDayIsBrightAndWhiteTheNightDarkBlueAndAFullPlanetLightsIt() {
        DayCycle.Daylight noon = DayCycle.of(Sky.DEFAULT, 12), night = DayCycle.of(Sky.DEFAULT, 0);
        assertEquals(1, noon.day(), 1e-6);
        assertArrayEquals(new float[]{1, 1, 1}, noon.ambient(), 1e-6f);
        assertEquals(0, night.day(), 1e-6);
        assertTrue(night.ambient()[2] > night.ambient()[0], "the night is blue");
        assertTrue(night.ambient()[0] < 0.6 && night.ambient()[0] > 0.2, "dark, but not too dark to play");
        float[] planetless = DayCycle.of(Sky.DEFAULT.withAir(Atmosphere.DEFAULT.withPlanet(false)), 0).ambient();
        assertTrue(night.ambient()[0] > planetless[0], "the planet lights the night");
        assertTrue(night.stars() > 0.5 && noon.stars() == 0, "stars by night only");
        DayCycle.Daylight sunset = DayCycle.of(Sky.DEFAULT, 18);
        assertTrue(sunset.twilight() > 0.9, "the sunset's glow at six");
        assertTrue(sunset.horizon()[0] > sunset.horizon()[2], "a warm horizon at sunset");
    }

    @Test
    void theStillSkyIsNoonAsTheArtWasDrawn() {
        DayCycle.Daylight still = DayCycle.Daylight.noon(Sky.DEFAULT);
        assertArrayEquals(new float[]{1, 1, 1}, still.ambient(), 0f);
        assertEquals(0, still.mist(), 0);
        assertEquals(0, still.stars(), 0);
    }

    @Test
    void theClockRunsAtTheDaysPaceAndFasterInTheTimeLapse() {
        DayCycle c = new DayCycle(10);
        Atmosphere air = Atmosphere.DEFAULT.withDayMinutes(24);           // an hour a minute
        c.tick(60, air);
        assertEquals(11, c.hour(), 1e-9);
        c.setSpeed(DayCycle.TIME_LAPSE);
        c.tick(60, air);
        assertEquals((11 + 60) % 24, c.hour(), 1e-9, "sixty hours on, round the clock");
        c.setSpeed(1);
        c.setHour(23.5);
        c.tick(60, air);
        assertEquals(0.5, c.hour(), 1e-9, "past midnight it starts again");
        c.tick(600, air.withDayMinutes(0));
        assertEquals(0.5, c.hour(), 1e-9, "a day of no minutes: the time stands still");
        assertEquals("06:30", DayCycle.clock(6.5));
        assertEquals("00:00", DayCycle.clock(24));
    }

    @Test
    void theWorldsClockStartsAtItsSkysHourAndRunsWithIt() {
        World w = new World(new com.larsons.game.sprite.Wardrobe());
        w.setSky(Sky.DEFAULT.withAir(Atmosphere.DEFAULT.withStartHour(7).withDayMinutes(24)));
        w.startDay();
        assertEquals(7, w.clock().hour(), 1e-9);
        w.tick(30);
        assertEquals(7.5, w.clock().hour(), 1e-9);
        w.idle(30);
        assertEquals(7.5, w.clock().hour(), 1e-9, "the editor's idle world keeps its hour");
    }

    @Test
    void theMistIsThickestAtDawnAndTheRaysOnlyWhileTheSunIsLow() {
        Sky sky = Sky.DEFAULT.withAir(Atmosphere.DEFAULT.withMist(0.5).withRays(1));
        assertTrue(DayCycle.of(sky, 6.5).mist() > DayCycle.of(sky, 13).mist());
        assertEquals(0, DayCycle.of(sky, 2).rays(), 1e-9, "no rays by night");
        assertEquals(0, DayCycle.of(sky, 12).rays(), 1e-9, "none at noon, the sun too high");
        assertTrue(DayCycle.of(sky, 7.5).rays() > 0.3, "morning rays");
        assertTrue(DayCycle.of(sky, 16.8).rays() > 0.3, "evening rays");
    }

    @Test
    void lightsLightWhatIsRoundThemInBands() {
        SceneLight l = new SceneLight();
        l.ambient[0] = l.ambient[1] = l.ambient[2] = 0.3f;
        assertTrue(l.add(0, 1, 0, 4, 0xFF0000, 1));
        float[] near = l.at(0, 1, 0), mid = l.at(0, 1, 2), far = l.at(0, 1, 5);
        assertTrue(near[0] > mid[0] && mid[0] > far[0]);
        assertEquals(0.3f, far[0], 1e-6f, "out of its reach: only the ambient light");
        assertEquals(0.3f, near[1], 1e-6f, "a red light adds no green");
        // banded: a step the size of a fifth of its strength
        double k = (mid[0] - 0.3) * 5;
        assertEquals(Math.round(k), k, 1e-4);
        for (int i = 1; i < SceneLight.MAX; i++) l.add(i, 0, 0, 1, 0xFFFFFF, 1);
        assertFalse(l.add(99, 0, 0, 1, 0xFFFFFF, 1), "sixteen at most");
        assertEquals(0, l.clearLights().lights());
    }

    @Test
    void aLevelKeepsItsTimeAndWeatherAndItsLights(@TempDir Path dir) throws Exception {
        Level level = Level.blank("Dusk", 64);
        Atmosphere air = Atmosphere.DEFAULT.withStartHour(19.5).withDayMinutes(40).withPlanetAt(120, 22)
                .withPlanetSize(33).withRings(true).withPlanetSeed(7).withClouds(0.8).withStars(0.4)
                .withAurora(0.9).withMist(0.6).withMistHeight(4).withRays(0.2);
        level.setSky(level.sky().withAir(air));
        level.add(ObjectTypes.LIGHT, 2, 3, 0, Map.of("colour", "crystal", "radius", 7.0));
        level.add(ObjectTypes.RAYS, -2, 1, 0, Map.of("count", 6.0));
        Path folder = dir.resolve("dusk");
        LevelIO.save(level, folder);
        Level back = LevelIO.load(folder);
        assertEquals(air, back.sky().air());
        World w = new World(new com.larsons.game.sprite.Wardrobe());
        com.larsons.game.level.LevelWorld.populate(w, back, com.larsons.game.sprite.ScenerySprites.NONE,
                com.larsons.game.combat.CombatTuning.defaults());
        assertEquals(19.5, w.clock().hour(), 1e-9, "played from its hour");
        List<World.LightSpot> lights = w.lights();
        assertEquals(1, lights.size());
        assertEquals(0x7FE8FF, lights.get(0).rgb());
        assertEquals(7, lights.get(0).radius(), 1e-9);
        assertEquals(1, w.raySpots().size());
        assertEquals(6, w.raySpots().get(0).count());
    }

    @Test
    void aLevelMadeBeforeThereWasTimeGetsTheDemosDay(@TempDir Path dir) throws Exception {
        Path folder = dir.resolve("old");
        java.nio.file.Files.createDirectories(folder);
        java.nio.file.Files.writeString(folder.resolve(LevelIO.FILE), "{\"format\": 1, \"name\": \"Old\", "
                + "\"size\": 64, \"sky\": {\"zenith\": \"#4A6FA5\", \"horizon\": \"#BDC9DB\", \"fog\": 70}, "
                + "\"objects\": []}");
        assertEquals(Atmosphere.DEFAULT, LevelIO.load(folder).sky().air());
    }
}
