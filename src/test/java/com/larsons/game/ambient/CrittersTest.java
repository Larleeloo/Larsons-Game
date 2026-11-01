package com.larsons.game.ambient;

import com.larsons.game.audio.SoundCategory;
import com.larsons.game.audio.SoundKeys;
import com.larsons.game.math.Vec3;
import com.larsons.game.world.Biome;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Who lives where, and when: the little creatures by biome, water and the time of day. */
class CrittersTest {

    /** A place all one biome, flat at 0 - with a pond of {@code pond} m round the middle (0: none). */
    private static Critters.Place place(Biome b, double pond) {
        return new Critters.Place() {
            @Override
            public double ground(double x, double z) {
                return 0;
            }

            @Override
            public double water(double x, double z, double groundY) {
                return Math.hypot(x, z) < pond ? 0.4 : 0;
            }

            @Override
            public Biome biome(double x, double z) {
                return b;
            }
        };
    }

    private static Critters run(Biome b, double pond, double day, double seconds, List<String> heard) {
        Critters c = new Critters();
        c.seed(7);
        Critters.Place p = place(b, pond);
        for (double t = 0; t < seconds; t += 0.1) {
            c.tick(0.1, t, day, Vec3.ZERO, null, p, (key, at, volume) -> heard.add(key));
        }
        return c;
    }

    @Test
    void firefliesComeOutAfterDarkAndDragonfliesByDayOverWater() {
        Critters night = run(Biome.MARSH, 6, 0, 30, new ArrayList<>());
        assertTrue(night.count(CritterKind.FIREFLY) > 20, "the marsh at night: " + night.count(CritterKind.FIREFLY));
        assertEquals(0, night.count(CritterKind.DRAGONFLY), "no dragonflies at night");
        Critters day = run(Biome.MARSH, 6, 1, 30, new ArrayList<>());
        assertEquals(0, day.count(CritterKind.FIREFLY), "no fireflies by day");
        assertTrue(day.count(CritterKind.DRAGONFLY) > 0, "dragonflies over the water");
        assertTrue(day.count(CritterKind.FROG) > 0, "frogs by the water");
        Critters dry = run(Biome.MARSH, 0, 1, 30, new ArrayList<>());
        assertEquals(0, dry.count(CritterKind.FROG), "no frog far from water");
        assertEquals(0, dry.count(CritterKind.DRAGONFLY));
    }

    @Test
    void theWasteAndTheOpenSeaAreEmpty() {
        Critters waste = run(Biome.WASTE, 0, 0, 20, new ArrayList<>());
        assertEquals(0, waste.count(CritterKind.FIREFLY));
        assertEquals(0, waste.count(CritterKind.FROG));
        Critters plains = run(Biome.PLAINS, 0, 0, 20, new ArrayList<>());
        assertTrue(plains.count(CritterKind.CRICKET) > 4, "crickets in the meadow at night");
    }

    @Test
    void theCricketsAndFrogsAreHeardAndNoneLeaveTheWaterTheyWadeIn() {
        List<String> heard = new ArrayList<>();
        Critters c = run(Biome.MARSH, 5, 0, 60, heard);
        assertTrue(heard.contains(SoundKeys.creature("cricket", "chirp")), heard.toString());
        assertTrue(heard.contains(SoundKeys.creature("frog", "croak")), heard.toString());
        for (Critter k : c.live()) {
            if (k.kind().flies()) continue;
            double r = Math.hypot(k.position().x(), k.position().z());
            if (k.kind() == CritterKind.CRICKET) assertTrue(r >= 5 - 0.6, "a cricket in the pond at " + r);
        }
    }

    @Test
    void aLevelCanThinThemOrSwarmThem() {
        Life none = new Life();
        none.setDensity(CritterKind.FIREFLY, 0);
        Critters c = new Critters();
        c.setLife(none);
        Critters.Place p = place(Biome.PLAINS, 0);
        for (double t = 0; t < 20; t += 0.1) c.tick(0.1, t, 0, Vec3.ZERO, null, p, (k, at, v) -> { });
        assertEquals(0, c.count(CritterKind.FIREFLY));
        Life many = new Life();
        many.setDensity(CritterKind.FIREFLY, 3);
        c.setLife(many);
        for (double t = 20; t < 50; t += 0.1) c.tick(0.1, t, 0, Vec3.ZERO, null, p, (k, at, v) -> { });
        assertTrue(c.count(CritterKind.FIREFLY) > CritterKind.FIREFLY.cap(), "a swarm: " + c.count(CritterKind.FIREFLY));
    }

    @Test
    void aSpotKeepsItsSwarmWhileSheIsNearAndLetsItGoWhenTakenAway() {
        Critters c = new Critters();
        Critters.Place p = place(Biome.WASTE, 0);          // (none of their own here)
        Critters.Spot s = new Critters.Spot(CritterKind.FIREFLY, new Vec3(4, 0, 0), 10, 3, Critters.When.ALWAYS);
        c.spots().add(s);
        for (double t = 0; t < 5; t += 0.1) c.tick(0.1, t, 1, Vec3.ZERO, null, p, (k, at, v) -> { });
        assertEquals(10, c.count(CritterKind.FIREFLY), "a swarm even by day, as placed");
        for (Critter k : c.live()) {
            if (k.kind() != CritterKind.FIREFLY) continue;       // (a cricket of the waste's own may come by)
            assertNotNull(k.spot());
            assertTrue(Math.hypot(k.position().x() - 4, k.position().z()) < 3 + 3.5, "they keep to their spot");
        }
        // the same spot made again (the editor rebuilt the level) keeps its swarm
        c.spots().clear();
        c.spots().add(new Critters.Spot(CritterKind.FIREFLY, new Vec3(4, 0, 0), 10, 3, Critters.When.ALWAYS));
        c.tick(0.1, 5, 1, Vec3.ZERO, null, p, (k, at, v) -> { });
        assertEquals(10, c.count(CritterKind.FIREFLY));
        c.spots().clear();
        for (double t = 5; t < 8; t += 0.1) c.tick(0.1, t, 1, Vec3.ZERO, null, p, (k, at, v) -> { });
        assertEquals(0, c.count(CritterKind.FIREFLY), "gone with their spot");
    }

    @Test
    void lifeIsWrittenOnlyWhenItIsNotAsItComes() {
        Life l = new Life();
        assertTrue(l.isDefault());
        l.setDensity(CritterKind.FROG, 2.5);
        l.setRegrow(0.25);
        Life back = Life.fromJson(l.toJson());
        assertEquals(2.5, back.density(CritterKind.FROG), 1e-9);
        assertEquals(0.25, back.regrow(), 1e-9);
        assertEquals(1, back.yield(), 1e-9);
        assertFalse(back.isDefault());
        l.setDensity(CritterKind.FROG, 99);
        assertEquals(Life.MOST, l.density(CritterKind.FROG), 1e-9, "kept to three times");
        assertEquals(CritterKind.FIREFLY, CritterKind.byKey("Fireflies"));
        assertEquals(CritterKind.DRAGONFLY, CritterKind.byKey("dragonflies"));
        assertEquals(CritterKind.FROG, CritterKind.byKey("frogs"));
    }

    @Test
    void theirSoundsAreTheCreaturesAndFallBackLikeTheRest() {
        String croak = SoundKeys.creature("frog", "croak");
        assertEquals("creature/frog/croak", croak);
        assertEquals("creatures/frog/croak.mp3", SoundKeys.preferredFile(croak));
        assertEquals(List.of("creatures/frog/croak", "creatures/croak"), SoundKeys.paths(croak));
        assertEquals(SoundCategory.CREATURES, SoundKeys.categoryOf(croak));
        assertTrue(SoundKeys.in(SoundCategory.CREATURES).stream().anyMatch(e -> e.key().equals(croak)));
        assertTrue(SoundKeys.folders().contains("creatures/frog"));
        assertTrue(SoundKeys.in(SoundCategory.WORLD).stream().anyMatch(e -> e.key().equals("world/forage_pick")));
    }
}
