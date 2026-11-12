package com.larsons.game.level;

import com.larsons.game.actor.Folk;
import com.larsons.game.actor.Folks;
import com.larsons.game.actor.Schedule;
import com.larsons.game.ambient.Animals;
import com.larsons.game.building.Building;
import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.combat.CombatTuning;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The folk's days: home at night and out of storms, back to their posts at their hours, as the editor sets them. */
class DaysTest {

    @BeforeAll
    static void buildings() {
        BuildingCatalog.use(BuildingCatalog.load(Path.of("assets", "buildings")));
    }

    @Test
    void aDayIsReadAsWrittenAndHoldsFromEachTimeToTheNext() {
        Schedule s = Schedule.parse("21:00=sleep@home; 06:30=stand@post; 12:00=wander@well; junk; 13:00=work");
        assertEquals(4, s.entries().size(), "the line that is no entry is passed over");
        assertEquals("06:30=stand@post; 12:00=wander@well; 13:00=work@post; 21:00=sleep@home", s.text());
        assertEquals(Schedule.Doing.SLEEP, s.at(3).doing(), "before the first: the last of the day before");
        assertEquals(Schedule.Doing.STAND, s.at(6.5).doing());
        assertEquals("well", s.at(12.5).where());
        assertEquals(Schedule.Doing.WORK, s.at(20.99).doing());
        assertEquals(Schedule.Doing.SLEEP, s.at(23.9).doing());
        assertEquals(List.of("well"), s.places());
        assertEquals(Schedule.DEFAULT, Schedule.parse("").text(), "blank: the day most keep");
        assertEquals(-1, Schedule.minute("25:00"));
        assertEquals(6 * 60 + 5, Schedule.minute("6:05"));
    }

    /** A level: a cottage, and a villager at her post some way off, keeping {@code day}. */
    private static World town(String day, String home) {
        Level level = new Level("days", 96);
        LevelObject cottage = level.add(ObjectTypes.BUILDING, 10, 0, 0, Map.of("kind", "cottage"));
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", "Mira");
        p.put("schedule", day);
        p.put("home", home == null ? FolkTypes.AUTO : home.replace("#", String.valueOf(cottage.id())));
        level.add(FolkTypes.PERSON, -10, 0, 0, p);
        level.add(ObjectTypes.MARKER, -10, 12, 0, Map.of("name", "well"));
        World world = new World(new Wardrobe());
        LevelWorld.populate(world, level, ScenerySprites.NONE, CombatTuning.defaults());
        return world;
    }

    private static Folk mira(World w) {
        return w.folks().all().stream().filter(f -> f.name().equals("Mira")).findFirst().orElseThrow();
    }

    /** She, standing at {@code at}. */
    private static Animals.Quarry her(Vec3 at) {
        return new Animals.Quarry() {
            @Override public Vec3 feet() { return at; }
            @Override public boolean dodging() { return false; }
            @Override public boolean parrying() { return false; }
            @Override public boolean blocking() { return false; }
            @Override public double hurt(double d) { return d; }
            @Override public boolean down() { return false; }
        };
    }

    private static void live(World w, Animals.Quarry her, double hour, boolean storm, double seconds) {
        Folks folks = w.folks();
        for (double t = 0; t < seconds; t += 1 / 30.0) {
            folks.setDay(hour, storm);
            folks.tick(1 / 30.0, her, w.folkPlace());
        }
    }

    @Test
    void atNightSheGoesHomeAndInAndInTheMorningComesBackToHerPost() {
        World w = town("07:00=stand@post; 21:00=sleep@home", null);
        Folk f = mira(w);
        Building cottage = w.buildings().stream().filter(b -> b.key().equals("cottage")).findFirst().orElseThrow();
        Animals.Quarry away = her(new Vec3(-40, 0, -40));
        live(w, away, 12, false, 1);
        assertFalse(f.indoors());
        assertEquals(-10, f.position().x(), 0.5, "at her post by day");
        // night falls: to her door, in, and out of sight
        live(w, away, 21.2, false, 40);
        assertTrue(f.indoors(), "in for the night: " + f.position());
        assertEquals(cottage.id(), f.insideOf());
        assertTrue(f.position().horizontalDistance(cottage.door()) < 1.5, "she went in at its door");
        assertTrue(f.alpha() < 0.05);
        assertNull(w.folks().reachable(f.position()), "nobody to talk to at the door at night");
        // morning: out again, and back to her post
        live(w, away, 7.1, false, 40);
        assertFalse(f.indoors());
        assertEquals(1, f.alpha(), 1e-9);
        assertTrue(f.position().horizontalDistance(new Vec3(-10, 0, 0)) < 0.6, "back at her post: " + f.position());
    }

    @Test
    void aStormSendsHerInAndItsPassingBringsHerOut() {
        World w = town("06:00=wander@well; 20:00=sleep@home", null);
        Folk f = mira(w);
        Animals.Quarry away = her(new Vec3(-40, 0, -40));
        live(w, away, 10, false, 1);
        assertTrue(f.position().horizontalDistance(new Vec3(-10, 0, 12)) < 6, "about the well: " + f.position());
        live(w, away, 10, true, 40);
        assertTrue(f.indoors(), "in out of the storm");
        live(w, away, 10, false, 40);
        assertFalse(f.indoors(), "the storm over: out again");
        assertTrue(f.position().horizontalDistance(new Vec3(-10, 0, 12)) < 6, "back about the well: " + f.position());
    }

    @Test
    void begunAtNightSheIsAlreadyInAndWithNoHomeSheStaysAtHerPost() {
        World w = town("07:00=stand@post; 21:00=sleep@home", null);
        live(w, her(new Vec3(-40, 0, -40)), 23, false, 0.2);
        assertTrue(mira(w).indoors(), "not walking home at the start: in already");

        World none = town("07:00=stand@post; 21:00=sleep@home", FolkTypes.NONE);
        live(none, her(new Vec3(-40, 0, -40)), 23, false, 2);
        Folk f = mira(none);
        assertFalse(f.indoors(), "no house: she stays out");
        assertTrue(f.position().horizontalDistance(new Vec3(-10, 0, 0)) < 0.6);
    }

    @Test
    void theHousePickedForHerIsHersAndSeenInsideSheTalksAndGoesOutWhenItIsTime() {
        World w = town("07:00=stand@post; 21:00=sleep@home", "building:#");
        Folk f = mira(w);
        live(w, her(new Vec3(-40, 0, -40)), 23, false, 0.2);
        assertTrue(f.indoors());
        // she walks in: Mira is seen inside, a few steps in, to be talked to
        Folks in = new Folks();
        in.visitor(f, new Vec3(0, 0, -2), 0, 3);
        assertTrue(f.visiting() && !f.indoors());
        Vec3 me = new Vec3(0, 0, -1);
        Animals.Place room = new Animals.Place() {
            @Override public double ground(double x, double z) { return 0; }
            @Override public double water(double x, double z, double g) { return 0; }
            @Override public com.larsons.game.world.Biome biome(double x, double z) {
                return com.larsons.game.world.Biome.PLAINS;
            }
            @Override public double[] bounds() { return new double[]{-6, 6, -6, 6}; }
        };
        for (int i = 0; i < 30; i++) {
            in.setDay(23, false);
            in.tick(1 / 30.0, her(me), room);
        }
        assertSame(f, in.reachable(me), "talked to inside");
        // morning comes while she is in: out of the door, and given back to the outdoors
        for (int i = 0; i < 30 * 20 && !f.visitDone(); i++) {
            in.setDay(7.5, false);
            in.tick(1 / 30.0, her(new Vec3(5, 0, -5)), room);
        }
        assertTrue(f.visitDone(), "out of its door: " + f.position());
        assertEquals(List.of(f), in.takeVisitors(false));
        assertTrue(in.all().isEmpty());
        assertTrue(f.indoors(), "at her door outdoors, to come out");
        Building cottage = w.buildings().stream().filter(b -> b.key().equals("cottage")).findFirst().orElseThrow();
        assertTrue(f.position().horizontalDistance(cottage.door()) < 0.01);
        live(w, her(new Vec3(-40, 0, -40)), 7.5, false, 40);
        assertTrue(f.position().horizontalDistance(new Vec3(-10, 0, 0)) < 0.6, "and to her post: " + f.position());
    }

    @Test
    void theEditorsRowsReadAndWriteTheTextTheGameReads() {
        Rows stock = Rows.stock("");
        List<List<String>> rows = stock.read().apply("health potion x5 @20, sword:crystal, cards x3, whetstone");
        assertEquals(List.of("health_potion", "", "5", "20"), rows.get(0));
        assertEquals("sword", rows.get(1).get(0));
        assertEquals("crystal", rows.get(1).get(1), "its tier kept");
        assertEquals("basic", stock.read().apply("sword").get(0).get(1), "a weapon without its tier: its lowest");
        assertEquals(Rows.CARDS, rows.get(2).get(0));
        assertEquals("health_potion x5 @20, " + rows.get(1).get(0) + ":" + rows.get(1).get(1) + ", cards x3, whetstone",
                stock.write().apply(rows));
        // what the shop reads of the rows is what was written by hand
        com.larsons.game.world.Shop a = new com.larsons.game.world.Shop(1, "a", Vec3.ZERO, 0,
                "health potion x5 @20, sword:crystal", false);
        com.larsons.game.world.Shop b = new com.larsons.game.world.Shop(1, "b", Vec3.ZERO, 0,
                stock.write().apply(stock.read().apply("health potion x5 @20, sword:crystal")), false);
        assertEquals(a.stock(null).size(), b.stock(null).size());
        for (int i = 0; i < a.stock(null).size(); i++) {
            assertEquals(a.stock(null).get(i).item().key(), b.stock(null).get(i).item().key());
            assertEquals(a.stock(null).get(i).price(), b.stock(null).get(i).price());
        }
        Rows pay = Rows.reward();
        assertEquals("coins x40, health_potion x2", pay.write().apply(pay.read().apply("coins x40, health potion x2")));
        Rows topics = Rows.topics();
        List<List<String>> t = topics.read().apply("The mill=It burned down.; Work?=Ask the smith.");
        assertEquals(List.of("Work?", "Ask the smith."), t.get(1));
        t.get(0).set(1, "It burned; nobody = blamed.");
        assertEquals("The mill=It burned, nobody = blamed.; Work?=Ask the smith.", topics.write().apply(t));
        Rows day = Rows.schedule();
        List<List<String>> d = day.read().apply("");
        assertEquals(Schedule.parse("").entries().size(), d.size(), "a person with no day set: the usual day, to change");
        d.add(List.of("05:15", "tend", "well"));
        assertTrue(day.write().apply(d).startsWith("05:15=tend@well; 06:30=stand@post"));
    }
}
