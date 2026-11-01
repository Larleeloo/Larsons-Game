package com.larsons.game.world;

import com.larsons.game.ambient.Animal;
import com.larsons.game.ambient.AnimalKind;
import com.larsons.game.ambient.Life;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The saddle, saddling a horse, getting on it, riding it - and getting off. */
class RidingTest {

    private static final double DT = 1 / 60.0;

    /** The saddled horse's ride.json as the 3D-Modeling repository writes it (a shortened track). */
    private static final String RIDE_JSON = "{\"seat\": [0.0, -0.08, 1.583], "
            + "\"stirrups\": [[0.32, -0.14, 0.92], [-0.32, -0.14, 0.92]], "
            + "\"track\": {\"idle\": [[-0.08, 1.583], [-0.08, 1.59]], "
            + "\"walk\": [[-0.08, 1.595], [-0.08, 1.583], [-0.08, 1.571], [-0.08, 1.583]], "
            + "\"run\": [[-0.08, 1.603], [-0.107, 1.576], [-0.107, 1.536]]}}";

    /** A world with nothing coming by, her at the middle facing north, a horse 1.5 m east of her. */
    private static World world() {
        World w = new World(new Wardrobe());
        Life quiet = new Life();
        w.animals().setLife(quiet);
        w.animals().setEnabled(true);
        w.setRideTrack(RideTrack.parse(RIDE_JSON));
        w.player().setPosition(Vec3.ZERO);
        w.player().snapHeading(0);
        return w;
    }

    private static Animal horse(World w, boolean saddled) {
        Animal h = w.addAnimal(AnimalKind.HORSE, new Vec3(1.5, 0, 0), null);
        h.faceTo(new Vec3(1.5, 0, -5));
        if (saddled) h.setSaddled(true);
        return h;
    }

    private static void run(World w, double seconds, Player.Intent in) {
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            w.player().update(DT, in, s -> s.defaultFrames() / AnimState.DEFAULT_FPS);
            w.tick(DT);
        }
    }

    /** She, up on {@code h}. */
    private static void mounted(World w, Animal h) {
        assertTrue(w.mount(h));
        run(w, Player.MOUNT_TIME + 0.1, Player.Intent.NONE);
        assertTrue(w.player().mounted());
    }

    /** Push forward (her north, -z) at a pace: walking, running (the canter) or sprinting (the gallop). */
    private static Player.Intent ahead(boolean run, boolean sprint) {
        return new Player.Intent(0, -1, run, sprint, false, false);
    }

    @Test
    void theRideJsonGivesTheSeatInEachFrame() {
        RideTrack t = RideTrack.parse(RIDE_JSON);
        assertEquals(1.583, t.seatUp(), 1e-9);
        assertEquals(0.08, t.seatAhead(), 1e-9, "-y is ahead");
        assertEquals(4, t.frames("walk"));
        assertEquals(1.583, t.seat("walk", 0.1)[1], 1e-9, "the second frame at 10 fps");
        assertEquals(1.595, t.seat("walk", 0.4)[1], 1e-9, "round again after four");
        assertEquals(0.107, t.seat("run", 0.15)[0], 1e-9);
        assertEquals(1.583 - RideTrack.SEAT_Z, t.lift("eat", 3), 1e-9, "a sheet it has no track for: the seat");
        RideTrack none = RideTrack.load(null);
        assertEquals(RideTrack.SEAT_UP, none.seatUp(), 1e-9);
    }

    @Test
    void theSaddleIsTackStowedOutOfTheHotbarAndFoundInChests() {
        assertSame(Tack.SADDLE, ItemDef.byId("saddle"));
        assertTrue(Tack.SADDLE.isTack());
        assertFalse(Tack.SADDLE.inHand(), "never in her hand");
        assertFalse(Tack.SADDLE.isWearable());
        assertNotNull(Wearables.pickup(Item.of(Tack.SADDLE)), "its turning sprite: the saddle, its pad red");
        assertEquals("saddle", Wearables.pickup(Item.of(Tack.SADDLE)).spin());

        World w = world();
        w.spawn(Tack.SADDLE, Vec3.ZERO);
        World.GroundItem g = w.items().get(0);
        Item before = w.player().inventory().held();
        assertTrue(w.pickUp(g));
        assertTrue(w.player().inventory().contains(Tack.SADDLE));
        assertEquals(before, w.player().inventory().held(), "what she holds is unchanged");

        boolean found = false;
        for (Rarity r : Rarity.values()) {
            for (Item i : w.loot().pool(r)) found |= i.def() == Tack.SADDLE;
        }
        assertTrue(found, "a chest can give it");
    }

    @Test
    void sheSaddlesAHorseWithTheSaddleSheCarries() {
        World w = world();
        Animal h = horse(w, false);
        assertNull(w.saddleable(), "no saddle on her");
        assertNull(w.reachableHorse(), "nothing to ride");
        w.player().inventory().stow(Item.of(Tack.SADDLE));
        assertSame(h, w.saddleable());
        assertTrue(w.saddle(h));
        assertTrue(h.saddled());
        assertEquals("horse_saddled", h.sprite());
        assertFalse(w.player().inventory().contains(Tack.SADDLE), "the saddle is on the horse now");
        assertSame(h, w.reachableHorse());
        assertNull(w.saddleable());
        // a cow is never saddled
        Animal cow = w.addAnimal(AnimalKind.COW, new Vec3(-1.5, 0, 0), null);
        cow.setSaddled(true);
        assertFalse(cow.saddled());
    }

    @Test
    void sheHopsUpIntoTheSaddleAndSitsAtItsHeight() {
        World w = world();
        Animal h = horse(w, true);
        Player p = w.player();
        assertTrue(w.mount(h));
        assertEquals(Player.Riding.MOUNTING, p.riding());
        assertTrue(h.ridden());
        assertFalse(w.mount(h), "on it already");
        double highest = 0;
        for (int i = 0; i < Math.round(Player.MOUNT_TIME / DT) + 4; i++) {
            run(w, DT, Player.Intent.NONE);
            highest = Math.max(highest, p.rideLift());
        }
        assertTrue(p.mounted());
        assertEquals(AnimState.RIDE, p.state());
        double seat = 1.583 - RideTrack.SEAT_Z;
        assertTrue(highest > seat + 0.1, "a hop up over the saddle: " + highest);
        assertEquals(h.position().x(), p.ground().x(), 1e-6, "sitting where the horse stands");
        assertEquals(h.heading(), p.heading(), 1e-6, "facing its way");
        assertEquals(p.ground().y() + p.rideLift(), p.feet().y(), 1e-9);
        assertTrue(Math.abs(p.rideLift() - seat) < 0.02, "her sprite's ground SEAT_Z under the seat: " + p.rideLift());
        assertFalse(w.targets().contains(h), "her own horse is no target");
    }

    @Test
    void theHorseGoesWhereSheSteersItAtAWalkACanterAndAGallop() {
        World w = world();
        Animal h = horse(w, true);
        mounted(w, h);
        Player p = w.player();

        run(w, 2, ahead(false, false));
        assertEquals("walk", h.sheet());
        assertEquals(AnimState.RIDE, p.state());
        assertEquals(Player.RIDE_WALK, p.speed(), 0.1);
        assertEquals(p.ground().x(), h.position().x(), 1e-9, "the horse is where she is");
        assertEquals(p.ground().z(), h.position().z(), 1e-9);
        assertEquals(h.sheetTime(), p.stateTime(), 1e-9, "her clock is its clock");

        run(w, 3, ahead(true, false));
        assertEquals("run", h.sheet());
        assertEquals(AnimState.RIDE_RUN, p.state());
        assertEquals(Player.RIDE_CANTER, p.speed(), 0.3);

        run(w, 3, ahead(false, true));
        assertEquals(Player.RIDE_GALLOP, p.speed(), 0.4);
        assertTrue(h.position().z() < -20, "well up the way: " + h.position());

        // let go: it pulls up and stands, and she sits still
        run(w, 3, Player.Intent.NONE);
        assertEquals("idle", h.sheet());
        assertEquals(AnimState.RIDE, p.state());
        assertEquals(0, p.stateTime(), 1e-9);
        assertEquals(0, p.speed(), 1e-6);
    }

    @Test
    void itTurnsNoFasterThanAHorseCan() {
        World w = world();
        Animal h = horse(w, true);
        mounted(w, h);
        Player p = w.player();
        double start = p.heading();
        // steer straight back the way it faces (south, +z)
        Player.Intent back = new Player.Intent(0, 1, false, false, false, false);
        run(w, 0.5, back);
        double turned = Math.abs(Math.atan2(Math.sin(p.heading() - start), Math.cos(p.heading() - start)));
        assertTrue(turned <= Player.RIDE_TURN * 0.5 + 1e-6, "turned " + turned);
        assertTrue(turned > 0.5, "turning all the same");
        assertTrue(p.speed() < Player.RIDE_WALK * 0.6, "slow, turning round: " + p.speed());
        run(w, 2.5, back);
        Vec3 way = SpriteView.headingVector(p.heading());
        assertTrue(way.z() > 0.95, "round and going south: " + way);
    }

    @Test
    void itNeverGoesOffALedge() {
        // ground at 0 to x = 4, a 3 m drop beyond it
        int cols = 81, rows = 81;
        float[] heights = new float[cols * rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) heights[r * cols + c] = -20 + c * Terrain.STEP > 4 ? -3f : 0f;
        }
        World w = world();
        w.setTerrain(Terrain.of(-20, -20, cols, rows, heights, List.of(), List.of()));
        Animal h = horse(w, true);
        mounted(w, h);
        Player p = w.player();
        Player.Intent east = new Player.Intent(1, 0, true, false, false, false);
        run(w, 4, east);
        assertTrue(p.ground().x() < 4.6, "pulled up at the edge: " + p.ground());
        assertEquals(0, p.ground().y(), 1e-6, "on the top");
        assertTrue(p.mounted());
        // on foot she would have gone over
        assertTrue(w.dismount());
        run(w, Player.DISMOUNT_TIME + 0.1, Player.Intent.NONE);
        assertEquals(Player.Riding.NO, p.riding());
    }

    @Test
    void sheGetsDownBesideItAndItWaitsThere() {
        World w = world();
        Animal h = horse(w, true);
        mounted(w, h);
        Player p = w.player();
        run(w, 2, ahead(false, false));
        Vec3 stood = h.position();
        assertTrue(w.dismount());
        assertEquals(Player.Riding.DISMOUNTING, p.riding());
        List<Player.Event> events = new ArrayList<>();
        for (int i = 0; i < Math.round(Player.DISMOUNT_TIME / DT) + 4; i++) {
            p.update(DT, Player.Intent.NONE, s -> s.defaultFrames() / AnimState.DEFAULT_FPS);
            w.tick(DT);
        }
        assertEquals(Player.Riding.NO, p.riding());
        assertEquals(0, p.rideLift(), 1e-9);
        assertEquals(p.ground().y(), p.feet().y(), 1e-9, "on her feet");
        double off = p.ground().horizontalDistance(h.position());
        assertTrue(off > h.radius() && off < h.radius() + 1.2, "beside it: " + off);
        assertFalse(h.ridden());
        assertNull(w.steed());
        assertEquals(stood.x(), h.position().x(), 0.2, "where she left it");
        // it stays, however far she goes
        p.setPosition(new Vec3(0, 0, 400));
        run(w, 3, Player.Intent.NONE);
        assertTrue(w.animals().live().contains(h), "a saddled horse is never sent away");
        assertTrue(h.position().horizontalDistance(stood) <= Animal.STAY + 1.0);
    }

    @Test
    void putSomewhereElseSheIsOffTheHorse() {
        World w = world();
        Animal h = horse(w, true);
        mounted(w, h);
        w.player().setPosition(new Vec3(10, 0, 10));
        run(w, DT, Player.Intent.NONE);
        assertEquals(Player.Riding.NO, w.player().riding());
        assertFalse(h.ridden());
        // and a horse gone from under her (a new level) puts her on her feet
        Animal again = horse(w, true);
        w.player().setPosition(again.position().add(-1.5, 0, 0));
        mounted(w, again);
        w.animals().clear();
        run(w, DT, Player.Intent.NONE);
        assertEquals(Player.Riding.NO, w.player().riding());
    }
}
