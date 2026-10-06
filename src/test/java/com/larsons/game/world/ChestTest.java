package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The chest's animations: shut, opening, open, closing - and its light. */
class ChestTest {

    private static final double DT = 1 / 60.0;

    private static void run(Chest c, double seconds) {
        for (double t = 0; t < seconds; t += DT) c.tick(DT, null);
    }

    @Test
    void itOpensStaysOpenAndShutsAgain() {
        Chest c = new Chest("ornate_chest", new Vec3(0, 0, 0), 0);
        assertEquals(Chest.State.IDLE, c.state());
        run(c, 5);
        assertEquals(Chest.State.IDLE, c.state(), "shut, its smoke swirling, until opened");
        assertEquals(0, c.light());
        c.toggle(null);
        assertEquals(Chest.State.OPENING, c.state());
        assertTrue(c.isOpen());
        run(c, 0.4);
        assertTrue(c.light() > 0 && c.light() <= 1, "the light comes up with the lid");
        run(c, 1);
        assertEquals(Chest.State.OPEN, c.state(), "the opening plays once and it stands open");
        assertEquals(1, c.light());
        run(c, 5);
        assertEquals(Chest.State.OPEN, c.state());
        c.toggle(null);
        assertEquals(Chest.State.CLOSING, c.state());
        run(c, 1);
        assertEquals(Chest.State.IDLE, c.state());
        assertEquals(0, c.light());
    }

    @Test
    void itsAnimationsLastAsLongAsTheirSheets() {
        Chest c = new Chest("ornate_chest", new Vec3(0, 0, 0), 0);
        c.toggle(s -> s == Chest.State.OPENING ? 2.0 : 0);
        run(c, 1.9);
        assertEquals(Chest.State.OPENING, c.state());
        run(c, 0.2);
        assertEquals(Chest.State.OPEN, c.state());
    }

    @Test
    void aLidOnItsWayUpCanBeSlammedBackDown() {
        Chest c = new Chest("ornate_chest", new Vec3(0, 0, 0), 0);
        c.toggle(null);
        run(c, 0.2);
        c.toggle(null);
        assertEquals(Chest.State.CLOSING, c.state());
        assertEquals(0, c.time());
    }

    @Test
    void thePlayerOpensTheNearestInReachAndCannotWalkIntoIt() {
        World w = new World(new Wardrobe());
        Chest c = w.addChest(new Chest("ornate_chest", new Vec3(0, 0, -1.2), 0));
        assertSame(c, w.reachableChest());
        w.player().setPosition(new Vec3(0, 0, -5));
        assertNull(w.reachableChest());
        w.player().setPosition(new Vec3(0.1, 0, -1.3));
        w.tick(DT);
        assertTrue(w.player().ground().horizontalDistance(c.position()) >= Chest.RADIUS - 1e-9,
                "pushed back out of its footprint");
    }
}
