package com.larsons.game.world;

import com.larsons.game.building.Building;
import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Beds: which things are one, standing at one, and sleeping the night through. */
class BedTest {

    private static World at(double hour) {
        World w = new World(new Wardrobe());
        w.setSky(Sky.DEFAULT.withAir(Atmosphere.DEFAULT.withStartHour(hour).withDayMinutes(24)));
        w.startDay();
        return w;
    }

    @Test
    void sheSleepsUntilMorningButOnlyAfterDark() {
        World w = at(14);
        w.player().setHealth(40);
        assertFalse(w.bedtime(), "the afternoon");
        assertFalse(w.sleep());
        assertEquals(14, w.clock().hour(), 1e-9, "nothing happened");
        assertEquals(40, w.player().health(), 1e-9);

        w.clock().setHour(22);
        assertTrue(w.bedtime());
        assertTrue(w.sleep());
        assertEquals(World.MORNING, w.clock().hour(), 1e-9, "the night skipped");
        assertEquals(Player.MAX_HEALTH, w.player().health(), 1e-9, "and she is whole again");
        assertFalse(w.bedtime(), "it is day");
        assertFalse(w.sleep(), "no sleeping the day away");

        // the small hours are night still; dusk is late enough
        w.clock().setHour(3);
        assertTrue(w.bedtime());
        w.clock().setHour(DayCycle.SUNSET - 0.25);
        assertTrue(w.bedtime());
        w.clock().setHour(DayCycle.SUNSET - 1.5);
        assertFalse(w.bedtime());
    }

    @Test
    void whereTheTimeStandsStillThereIsNoNightToSleepThrough() {
        World w = new World(new Wardrobe());
        w.setSky(Sky.DEFAULT.withAir(Atmosphere.DEFAULT.withStartHour(23).withDayMinutes(0)));
        w.startDay();
        assertFalse(w.bedtime());
        assertFalse(w.sleep());
        assertEquals(23, w.clock().hour(), 1e-9);
    }

    @Test
    void aBedIsSleptInFromBesideIt() {
        for (String bed : new String[]{"bed", "bed_double", "straw_bed", "bedroll", "hammock", "cot"}) {
            assertTrue(World.isBed(bed), bed);
        }
        for (String not : new String[]{"table", "bedside_table", "wardrobe", ""}) assertFalse(World.isBed(not), not);
        assertFalse(World.isBed(null));

        BuildingCatalog c = BuildingCatalog.load(Path.of("assets", "buildings"));
        assertNotNull(c.profile("bed"), "the furniture has a bed");
        World w = at(22);
        Building bed = w.addBuilding(new Building(1, c.profile("bed"), new Vec3(6, 0, 6), 0, null, null));
        w.addBuilding(new Building(2, c.profile("table"), new Vec3(-6, 0, -6), 0, null, null));
        assertNull(w.reachableBed(), "not from where she starts");
        boolean beside = false;
        for (double d = 3; d > 0.2 && !beside; d -= 0.1) {
            w.player().setPosition(new Vec3(6 + d, 0, 6));
            beside = w.reachableBed() == bed;
        }
        assertTrue(beside, "from beside it");
        w.player().setPosition(new Vec3(-6.8, 0, -6));
        assertNull(w.reachableBed(), "a table is no bed");
        w.player().setPosition(new Vec3(20, 0, 20));
        assertNull(w.reachableBed(), "not from across the room");
    }
}
