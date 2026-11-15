package com.larsons.game.scene;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;

/** The small map - what she has seen of a level, and keeping it - and the compass's bearings. */
class MapAndCompassTest {

    @Test
    void theSmallMapShowsOnlyWhereSheHasBeen() {
        World w = new World(new Wardrobe());
        try (MiniMap map = new MiniMap(w, null)) {
            assertEquals(0, map.seenShare(), 1e-9, "a level she has never been in is dark");
            double west = w.ground().west(), size = w.ground().size();
            Vec3 here = new Vec3(west + size * 0.2, 0, west + size * 0.2);
            Vec3 on = new Vec3(west + size * 0.8, 0, west + size * 0.8);
            assertTrue(here.horizontalDistance(on) > MiniMap.SIGHT * 1.5, "a level bigger than she sees across");
            assertFalse(map.seen(here.x(), here.z()));
            assertTrue(map.discover(here), "new ground");
            assertTrue(map.seen(here.x(), here.z()));
            assertTrue(map.seen(here.x() + MiniMap.SIGHT * 0.6, here.z()), "as far as she sees");
            assertFalse(map.seen(on.x(), on.z()), "and no further");
            assertFalse(map.discover(here), "standing still shows nothing new");
            double first = map.seenShare();
            assertTrue(first > 0 && first < 0.6, "a part of it: " + first);
            // walking shows more
            assertTrue(map.discover(on));
            assertTrue(map.seen(on.x(), on.z()));
            assertTrue(map.seenShare() > first);
            assertFalse(map.seen(west - 5, west + 1), "off the map");
            assertFalse(map.seen(west + size + 5, west + 1));

            // kept with the playthrough, and there when she comes back
            String kept = map.encode();
            try (MiniMap back = new MiniMap(w, kept)) {
                assertEquals(map.seenShare(), back.seenShare(), 1e-12);
                assertTrue(back.seen(here.x(), here.z()));
                assertTrue(back.seen(on.x(), on.z()));
                assertEquals(kept, back.encode());
            }
            // what was kept for a level of another size, or is not a map at all, is nothing seen
            int cells = Integer.parseInt(kept.substring(0, kept.indexOf(':')));
            assertTrue(MiniMap.decode(kept, cells).cardinality() > 0);
            assertEquals(new BitSet(), MiniMap.decode(kept, cells + 1));
            assertEquals(new BitSet(), MiniMap.decode("not a map", cells));
            assertEquals(new BitSet(), MiniMap.decode(cells + ":!!!", cells));
            assertEquals(new BitSet(), MiniMap.decode(null, cells));
        }
    }

    @Test
    void theCompassPutsThingsWhereTheyLieFromWhereTheCameraLooks() {
        // x east, z south
        assertEquals(0, Compass.bearing(0, -10), 1e-9, "north");
        assertEquals(Math.PI / 2, Compass.bearing(10, 0), 1e-9, "east");
        assertEquals(Math.PI, Math.abs(Compass.bearing(0, 10)), 1e-9, "south");
        assertEquals(-Math.PI / 2, Compass.bearing(-10, 0), 1e-9, "west");
        assertEquals(0, Compass.wrap(2 * Math.PI), 1e-9);
        assertEquals(-Math.PI / 2, Compass.wrap(1.5 * Math.PI), 1e-9);
        assertEquals(Math.PI / 2, Compass.wrap(-1.5 * Math.PI), 1e-9);
        double looking = Compass.looking(0.4);
        // dead ahead is the middle of the bar; a little to the right is right of it; the arc's end is the bar's
        assertEquals(0, Compass.along(looking, looking), 1e-9);
        assertTrue(Compass.along(looking + 0.2, looking) > 0);
        assertTrue(Compass.along(looking - 0.2, looking) < 0);
        assertEquals(1, Compass.along(looking + Compass.ARC, looking), 1e-9);
        assertEquals(-1, Compass.along(looking - Compass.ARC, looking), 1e-9);
        assertTrue(Math.abs(Compass.along(looking + Math.PI, looking)) > 1, "what is behind is off the bar");
        // turning the camera slides everything the other way
        double north = Compass.bearing(0, -1);
        assertTrue(Compass.along(north, Compass.looking(0.3)) != Compass.along(north, Compass.looking(-0.3)));
        assertEquals(-Compass.along(north, Compass.looking(0.3)), Compass.along(north, Compass.looking(-0.3)), 1e-9);
    }
}
