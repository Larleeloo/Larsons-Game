package com.larsons.game.ambient;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.math.Vec3;
import com.larsons.game.world.Biome;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** The animals: who lives where, what they do when she comes near, when she hits them - and when they hit her. */
class AnimalsTest {

    /** A place all one biome, flat at 0, with a pond of {@code pond} m round the middle (0: none) and a tree at (5, 5). */
    private static Animals.Place place(Biome b, double pond) {
        return new Animals.Place() {
            @Override
            public double ground(double x, double z) {
                return 0;
            }

            @Override
            public double water(double x, double z, double groundY) {
                return Math.hypot(x, z) < pond ? 0.6 : 0;
            }

            @Override
            public Biome biome(double x, double z) {
                return b;
            }

            @Override
            public boolean blocked(double x, double z, double radius) {
                return Math.hypot(x - 5, z - 5) < 0.6 + radius;
            }
        };
    }

    /** The player standing at {@code at}: what she does, and what she has taken. */
    private static final class Her implements Animals.Quarry {
        Vec3 at;
        boolean rolling, parrying, blocking;
        double taken;

        Her(Vec3 at) {
            this.at = at;
        }

        @Override
        public Vec3 feet() { return at; }

        @Override
        public boolean dodging() { return rolling; }

        @Override
        public boolean parrying() { return parrying; }

        @Override
        public boolean blocking() { return blocking; }

        @Override
        public double hurt(double damage) {
            taken += damage;
            return damage;
        }

        @Override
        public boolean down() { return false; }
    }

    private static List<Animals.Event> run(Animals a, Animals.Place p, Her her, double seconds, List<String> heard) {
        List<Animals.Event> out = new ArrayList<>();
        for (double t = 0; t < seconds; t += 0.05) {
            out.addAll(a.tick(0.05, t, 1, her == null ? Vec3.ZERO : her.at, her, p,
                    (key, at, volume) -> heard.add(key)));
        }
        return out;
    }

    private static Animals fresh(long seed) {
        Animals a = new Animals();
        a.seed(seed);
        return a;
    }

    @Test
    void theyComeWhereTheyLiveAndKeepTheirWater() {
        Animals plains = fresh(3);
        run(plains, place(Biome.PLAINS, 0), null, 40, new ArrayList<>());
        assertTrue(plains.count(AnimalKind.RABBIT) >= 2, "rabbits in the meadow: " + plains.count(AnimalKind.RABBIT));
        assertTrue(plains.count(AnimalKind.HORSE) >= 1, "horses on the plains");
        assertEquals(0, plains.count(AnimalKind.DUCK), "no ducks far from water");
        assertEquals(0, plains.count(AnimalKind.IGUANA), "no iguanas in the meadow");

        Animals marsh = fresh(4);
        Animals.Place pond = place(Biome.MARSH, 12);
        run(marsh, pond, null, 60, new ArrayList<>());
        assertTrue(marsh.count(AnimalKind.DUCK) >= 2, "ducks on the marsh's water: " + marsh.count(AnimalKind.DUCK));
        for (Animal a : marsh.live()) {
            double r = Math.hypot(a.position().x(), a.position().z());
            if (!a.kind().swims() && !a.kind().flies() && a.kind().wade() < 0.6) {
                assertTrue(r >= 12 - 0.5, a.kind() + " in the deep pond at " + r);
            }
            if (a.swimming()) assertEquals(0.6 - Animals.sink(a.kind()), a.position().y(), 1e-9, "at its waterline");
        }

        Animals waste = fresh(5);
        run(waste, place(Biome.WASTE, 0), null, 30, new ArrayList<>());
        assertEquals(0, waste.count(AnimalKind.HORSE));
        assertEquals(0, waste.count(AnimalKind.RABBIT));
    }

    @Test
    void turnedOffInTheEditorTheyAreGoneAtOnce() {
        Animals a = fresh(11);
        a.setStill(true);
        Animals.Place p = place(Biome.PLAINS, 0);
        run(a, p, null, 30, new ArrayList<>());
        assertFalse(a.live().isEmpty(), "some come by in the editor's view");
        Life off = new Life();
        for (AnimalKind k : AnimalKind.values()) off.setDensity(k, 0);
        a.setLife(off);
        a.tick(0.05, 30, 1, Vec3.ZERO, null, p, (key, at, volume) -> { });
        assertTrue(a.live().isEmpty(), "spawning off: none left standing about, " + a.live().size());
        run(a, p, null, 20, new ArrayList<>());
        assertTrue(a.live().isEmpty(), "and none come");

        // in play, they go too - wherever they are, not only once far off
        Animals play = fresh(11);
        run(play, p, null, 30, new ArrayList<>());
        assertFalse(play.live().isEmpty());
        play.setLife(off);
        run(play, p, null, 4, new ArrayList<>());
        assertTrue(play.live().isEmpty(), "faded away: " + play.live().size());
    }

    @Test
    void inTheEditorAHerdStandsOnItsSpotsUntilPlayed() {
        Animals a = fresh(12);
        a.setStill(true);
        Life none = new Life();
        for (AnimalKind k : AnimalKind.values()) none.setDensity(k, 0);
        a.setLife(none);
        Animals.Herd cows = new Animals.Herd(AnimalKind.COW, new Vec3(-10, 0, 0), 5, 6, null,
                Critters.When.NIGHT);
        Animals.Herd one = new Animals.Herd(AnimalKind.HORSE, new Vec3(12, 0, -3), 1, 3, null,
                Critters.When.ITS_TIME, false, List.of(), 1, 1, true, 1.0);
        a.herds().add(cows);
        a.herds().add(one);
        Animals.Place p = place(Biome.PLAINS, 0);
        a.tick(0.05, 0, 1, Vec3.ZERO, null, p, (key, at, volume) -> { });
        assertEquals(5, a.count(AnimalKind.COW), "the whole herd at once, whatever the hour");
        assertEquals(1, a.count(AnimalKind.HORSE));
        java.util.Map<Animal, Vec3> where = new java.util.HashMap<>();
        for (Animal an : a.live()) {
            where.put(an, an.position());
            assertEquals(1, an.alpha(), 1e-9, "all there, not fading in");
        }
        Animal horse = a.live().stream().filter(an -> an.kind() == AnimalKind.HORSE).findFirst().orElseThrow();
        assertEquals(12, horse.position().x(), 1e-9);
        assertEquals(-3, horse.position().z(), 1e-9);
        assertEquals(1.0, horse.heading(), 1e-9, "facing the way it was turned");
        run(a, p, null, 60, new ArrayList<>());
        assertEquals(5, a.count(AnimalKind.COW));
        for (Animal an : a.live()) {
            assertEquals(where.get(an), an.position(), an.kind() + " has not moved off its spot");
            assertEquals(1.0, an.kind() == AnimalKind.HORSE ? an.heading() : 1.0, 1e-9);
        }
        // played, they go about as ever
        a.setStill(false);
        run(a, p, null, 40, new ArrayList<>());
        boolean moved = false;
        for (Animal an : a.live()) if (where.containsKey(an) && !where.get(an).equals(an.position())) moved = true;
        assertTrue(moved, "they wander once played");
    }

    @Test
    void theLevelsDensitySaysHowMany() {
        Animals none = fresh(6);
        Life l = new Life();
        for (AnimalKind k : AnimalKind.values()) l.setDensity(k, 0);
        none.setLife(l);
        run(none, place(Biome.PLAINS, 0), null, 30, new ArrayList<>());
        assertTrue(none.live().isEmpty(), "none at 0: " + none.live().size());

        Animals more = fresh(6);
        Life m = new Life();
        m.setDensity(AnimalKind.SHEEP, 3);
        more.setLife(m);
        Animals usual = fresh(6);
        run(more, place(Biome.MOUNTAINS, 0), null, 40, new ArrayList<>());
        run(usual, place(Biome.MOUNTAINS, 0), null, 40, new ArrayList<>());
        assertTrue(more.count(AnimalKind.SHEEP) > usual.count(AnimalKind.SHEEP),
                more.count(AnimalKind.SHEEP) + " vs " + usual.count(AnimalKind.SHEEP));
    }

    @Test
    void aHerdKeepsRoundItsSpotAndBringsItsOwnNature() {
        Animals a = fresh(7);
        Life none = new Life();
        for (AnimalKind k : AnimalKind.values()) none.setDensity(k, 0);
        a.setLife(none);
        Animals.Herd cows = new Animals.Herd(AnimalKind.COW, new Vec3(20, 0, 0), 4, 6, null, Critters.When.ALWAYS);
        Animals.Herd geese = new Animals.Herd(AnimalKind.GOOSE, new Vec3(-20, 0, 0), 3, 4, Temperament.TAME,
                Critters.When.ALWAYS);
        a.herds().add(cows);
        a.herds().add(geese);
        run(a, place(Biome.WASTE, 0), null, 60, new ArrayList<>());
        assertEquals(4, a.count(AnimalKind.COW));
        assertEquals(3, a.count(AnimalKind.GOOSE));
        for (Animal an : a.live()) {
            Vec3 home = an.herd().at();
            assertTrue(Math.hypot(an.position().x() - home.x(), an.position().z() - home.z()) < an.herd().radius() + 2,
                    an.kind() + " strayed");
            assertEquals(an.kind() == AnimalKind.GOOSE ? Temperament.TAME : Temperament.CALM, an.temperament());
        }
        // the herd taken out of the level: they go
        a.herds().clear();
        run(a, place(Biome.WASTE, 0), null, 5, new ArrayList<>());
        assertTrue(a.live().isEmpty());
    }

    @Test
    void aSkittishOneRunsWhenSheComesNearAndACalmOneDoesNot() {
        Animals a = fresh(8);
        Animals.Place p = place(Biome.WASTE, 0);
        Her her = new Her(new Vec3(0, 0, 0));
        Animal rabbit = a.add(AnimalKind.RABBIT, new Vec3(3, 0, 0), null, p);
        Animal cow = a.add(AnimalKind.COW, new Vec3(-2.5, 0, 0), null, p);
        run(a, p, her, 0.5, new ArrayList<>());
        assertEquals(Animal.Mode.FLEE, rabbit.mode());
        assertNotEquals(Animal.Mode.FLEE, cow.mode());
        run(a, p, her, 4, new ArrayList<>());
        assertTrue(Math.hypot(rabbit.position().x(), rabbit.position().z()) > 7, "it got away");
    }

    @Test
    void aFriendlyDogComesUpToHer() {
        Animals a = fresh(9);
        Animals.Place p = place(Biome.WASTE, 0);
        Her her = new Her(new Vec3(0, 0, 0));
        Animal dog = a.add(AnimalKind.DOG, new Vec3(7, 0, 0), null, p);
        run(a, p, her, 8, new ArrayList<>());
        assertEquals(Animal.Mode.FOLLOW, dog.mode());
        assertTrue(Math.hypot(dog.position().x(), dog.position().z()) < 3, "by her side");
    }

    @Test
    void aGooseWarnsHerOffThenGoesForHerAndHerRollParryAndBlockWork() {
        Animals a = fresh(10);
        Animals.Place p = place(Biome.WASTE, 0);
        Her her = new Her(new Vec3(0, 0, 0));
        Animal goose = a.add(AnimalKind.GOOSE, new Vec3(3, 0, 0), null, p);
        List<String> heard = new ArrayList<>();
        List<Animals.Event> ev = run(a, p, her, 0.3, heard);
        assertEquals(Animal.Mode.WARN, goose.mode());
        assertTrue(ev.stream().anyMatch(e -> e.outcome() == Animals.Outcome.WARNED));
        assertTrue(heard.contains(SoundKeys.creature("goose", "hiss")), heard.toString());
        // she stays: it goes for her, and its pecks land
        ev = run(a, p, her, 6, heard);
        assertTrue(her.taken >= AnimalKind.GOOSE.damage(), "pecked: " + her.taken);
        assertTrue(ev.stream().anyMatch(e -> e.outcome() == Animals.Outcome.STRUCK));
        // rolling, nothing lands; parrying, it is knocked back; blocking, half
        her.rolling = true;
        double before = her.taken;
        ev = run(a, p, her, 3, heard);
        assertEquals(before, her.taken, 1e-9);
        assertTrue(ev.stream().anyMatch(e -> e.outcome() == Animals.Outcome.DODGED));
        her.rolling = false;
        her.parrying = true;
        ev = run(a, p, her, 3, heard);
        assertEquals(before, her.taken, 1e-9);
        assertTrue(ev.stream().anyMatch(e -> e.outcome() == Animals.Outcome.PARRIED));
        her.parrying = false;
        her.blocking = true;
        ev = run(a, p, her, 3, heard);
        assertTrue(ev.stream().anyMatch(e -> e.outcome() == Animals.Outcome.BLOCKED));
        Animals.Event block = ev.stream().filter(e -> e.outcome() == Animals.Outcome.BLOCKED).findFirst().orElseThrow();
        assertEquals(Math.round(AnimalKind.GOOSE.damage() / 2), block.damage(), 1e-9);
        // she gets away: it gives up
        her.blocking = false;
        her.at = new Vec3(60, 0, 0);
        run(a, p, her, 3, heard);
        assertFalse(goose.hostile(), "it gave up on her");
    }

    @Test
    void hitACalmOneRunsADefensiveOneFightsBackAndBeatenTheyRunOffAndAreGone() {
        Animals a = fresh(11);
        Animals.Place p = place(Biome.WASTE, 0);
        Her her = new Her(new Vec3(0, 0, 0));
        Animal sheep = a.add(AnimalKind.SHEEP, new Vec3(6, 0, 0), null, p);
        Animal swan = a.add(AnimalKind.SWAN, new Vec3(-8, 0, 0), null, p);
        sheep.hurt(3, her.at, true);
        swan.hurt(3, her.at, true);
        run(a, p, her, 0.6, new ArrayList<>());
        assertEquals(Animal.Mode.FLEE, sheep.mode());
        assertEquals(Animal.Mode.CHASE, swan.mode());
        assertTrue(swan.hostile());

        List<String> heard = new ArrayList<>();
        swan.hurt(swan.health() + 1, her.at, true);
        assertFalse(swan.alive(), "beaten: no more a target");
        List<Animals.Event> ev = run(a, p, her, 0.2, heard);
        // hunted down: game falls where it is, for what it leaves
        assertTrue(ev.stream().anyMatch(e -> e.outcome() == Animals.Outcome.DOWN && e.animal() == swan));
        assertEquals(Animal.Mode.DOWN, swan.mode());
        assertEquals("fall", swan.sheet());
        assertTrue(heard.contains(SoundKeys.creature("swan", "squawk")), heard.toString());
        run(a, p, her, 5, heard);
        assertTrue(a.live().contains(swan), "lying there a while");
        run(a, p, her, Animals.LIES_FOR, heard);
        assertFalse(a.live().contains(swan), "and then gone");

        // the tame are no game: beaten, a horse runs off, and is gone
        Animal horse = a.add(AnimalKind.HORSE, new Vec3(0, 0, 8), null, p);
        horse.hurt(horse.health() + 1, her.at, true);
        ev = run(a, p, her, 0.2, heard);
        assertTrue(ev.stream().anyMatch(e -> e.outcome() == Animals.Outcome.RAN_OFF && e.animal() == horse));
        run(a, p, her, 8, heard);
        assertFalse(a.live().contains(horse), "run off, and gone");
    }

    @Test
    void stingOneBeeAndTheSwarmComes() {
        Animals a = fresh(12);
        Animals.Place p = place(Biome.WASTE, 0);
        Her her = new Her(new Vec3(0, 0, 0));
        List<Animal> bees = new ArrayList<>();
        for (int i = 0; i < 4; i++) bees.add(a.add(AnimalKind.BEE, new Vec3(4 + i * 0.5, 0, 1), null, p));
        Animal far = a.add(AnimalKind.BEE, new Vec3(-30, 0, 0), null, p);
        run(a, p, her, 1, new ArrayList<>());
        for (Animal b : bees) assertFalse(b.hostile(), "a bee minds its own business");
        bees.get(0).hurt(1, her.at, true);
        run(a, p, her, 0.5, new ArrayList<>());
        for (Animal b : bees) assertTrue(b.hostile(), "the swarm comes");
        assertFalse(far.hostile(), "but not a bee far off");
        List<String> heard = new ArrayList<>();
        run(a, p, her, 5, heard);
        assertTrue(her.taken > 0, "stung");
        assertTrue(heard.contains(SoundKeys.creature("bee", "sting")), heard.toString());
    }

    @Test
    void aTameOneNeverRunsNorFightsAndAHostileOneComesOnSight() {
        Animals a = fresh(13);
        Animals.Place p = place(Biome.WASTE, 0);
        Her her = new Her(new Vec3(0, 0, 0));
        Animal tame = a.add(AnimalKind.GOOSE, new Vec3(1.5, 0, 0), Temperament.TAME, p);
        Animal bear = a.add(AnimalKind.BEAR, new Vec3(-12, 0, 0), Temperament.HOSTILE, p);
        run(a, p, her, 0.5, new ArrayList<>());
        assertFalse(tame.hostile());
        assertEquals(Animal.Mode.CHASE, bear.mode());
        tame.hurt(2, her.at, true);
        run(a, p, her, 1, new ArrayList<>());
        assertFalse(tame.hostile());
        assertNotEquals(Animal.Mode.FLEE, tame.mode());
        run(a, p, her, 5, new ArrayList<>());
        assertTrue(her.taken >= AnimalKind.BEAR.damage(), "mauled");
    }

    @Test
    void theyGoRoundTreesAndOffNotIntoTheDeep() {
        Animals a = fresh(14);
        Animals.Place p = place(Biome.WASTE, 3);
        Her her = new Her(new Vec3(-10, 0, -10));
        Animal horse = a.add(AnimalKind.HORSE, new Vec3(7, 0, 7), null, p);
        // hit from the far side of the tree: it runs past it, not through it
        horse.hurt(1, new Vec3(9, 0, 9), true);
        for (double t = 0; t < 4; t += 0.02) {
            a.tick(0.02, t, 1, Vec3.ZERO, her, p, null);
            assertTrue(Math.hypot(horse.position().x() - 5, horse.position().z() - 5) > 0.6 + horse.radius() * 0.6 - 0.05,
                    "in the tree");
            assertTrue(Math.hypot(horse.position().x(), horse.position().z()) > 3 - 0.2 || p.water(horse.position().x(),
                    horse.position().z(), 0) <= AnimalKind.HORSE.wade(), "in the deep pond");
        }
    }

    @Test
    void aSwimmersWaterlineIsWhereItsSheetsWereDrawn() throws IOException {
        // the sheets carry where the water comes up to on it as drawn swimming; the game keeps it to that
        Pattern line = Pattern.compile("\"waterline\"\\s*:\\s*([0-9.]+)");
        for (AnimalKind k : AnimalKind.values()) {
            if (!k.paddles()) continue;
            Path profile = Path.of("assets/sprites/objects", k.key(), "profile.json");
            Matcher m = line.matcher(Files.readString(profile));
            assertTrue(m.find(), k + " has no waterline");
            assertEquals(Double.parseDouble(m.group(1)), k.waterline(), 0.01, k.toString());
        }
    }

    @Test
    void everyAnimalHasItsSoundsAndItsSheetsAreNamed() {
        for (AnimalKind k : AnimalKind.values()) {
            List<String> listed = SoundKeys.ANIMAL_SOUNDS.getOrDefault(k.key(), List.of());
            for (String s : k.sounds()) assertTrue(listed.contains(s), k + " " + s);
            assertNotNull(AnimalKind.byKey(k.key()));
            assertEquals(k, AnimalKind.byKey(k.label()));
        }
        assertEquals(AnimalKind.GOOSE, AnimalKind.byKey("Geese"));
        assertEquals(Temperament.HOSTILE, Temperament.byKey("hostile"));
        assertNull(Temperament.byKey("own"));
    }

    @Test
    void lifeKeepsTheAnimalsDensities() {
        Life l = new Life();
        assertTrue(l.isDefault());
        l.setDensity(AnimalKind.BEAR, 0);
        l.setDensity(AnimalKind.RABBIT, 2.5);
        assertFalse(l.isDefault());
        Map<String, Object> json = l.toJson();
        Life back = Life.fromJson(json);
        assertEquals(0, back.density(AnimalKind.BEAR));
        assertEquals(2.5, back.density(AnimalKind.RABBIT));
        assertEquals(1, back.density(AnimalKind.COW));
        assertEquals(1, Life.fromJson(Map.of("critters", Map.of())).density(AnimalKind.COW), "an older level: all 1");
        assertEquals(2.5, l.copy().density(AnimalKind.RABBIT));
    }
}
