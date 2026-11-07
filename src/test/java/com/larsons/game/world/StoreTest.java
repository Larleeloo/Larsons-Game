package com.larsons.game.world;

import com.larsons.game.combat.Combat;
import com.larsons.game.combat.Target;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Barrels, crates and storage chests: what they hold, reaching into them, breaking them open. */
class StoreTest {

    @Test
    void whatItHoldsIsWrittenAsAListOfItems() {
        Store s = new Store(1, Store.Kind.CRATE, Vec3.ZERO, 0);
        int took = s.fill("wild berries x6, Sage, longbow perfect, no such thing, sword *2");
        assertEquals(10, took);
        Inventory in = s.items();
        assertEquals(6, in.total(Item.of(Pickings.WILD_BERRIES)), "a picking stacks");
        assertEquals(1, in.total(Item.of(Pickings.SAGE)));
        assertEquals(Item.of(ItemDef.LONGBOW, Tier.PERFECT), in.get(in.indexOf(ItemDef.LONGBOW)), "its tier after it");
        assertEquals(2, in.total(Item.of(ItemDef.SWORD)));
        assertEquals(0, new Store(2, Store.Kind.BARREL, Vec3.ZERO, 0).fill(""));
        // no more than it has room for
        Store small = new Store(3, Store.Kind.BARREL, Vec3.ZERO, 0);
        assertEquals(Store.Kind.BARREL.slots(), small.fill("sword x40"));
    }

    @Test
    void thingsMoveInAndOutAWholeStackAtATime() {
        Inventory her = new Inventory(Inventory.DEFAULT_SLOTS);
        Item berries = Item.of(Pickings.WILD_BERRIES);
        assertEquals(0, her.put(berries, 25, true), "two stacks of them");
        assertEquals(Pickings.STACK, her.amount(Inventory.HOTBAR), "stowed out of the way of what is in hand");
        assertEquals(5, her.amount(Inventory.HOTBAR + 1));
        assertNull(her.get(0));
        assertEquals(0, her.put(berries, 3, true));
        assertEquals(8, her.amount(Inventory.HOTBAR + 1), "onto the stack with room first");
        Inventory crate = new Inventory(5);
        for (int i = 0; i < 5; i++) crate.set(i, Item.of(ItemDef.SWORD));
        assertEquals(4, crate.put(berries, 4, false), "none fit in a full one");
    }

    @Test
    void aBarrelIsReachedFromBesideItAndBreaksSpillingWhatItHeld() {
        World w = new World(new Wardrobe());
        Store barrel = w.addStore(new Store(7, Store.Kind.BARREL, new Vec3(2, 0, 0), 0));
        barrel.fill("sage x3, sword");
        w.player().setPosition(new Vec3(1, 0, 0));
        assertSame(barrel, w.reachableStore(), "from beside it");
        w.player().setPosition(new Vec3(-2, 0, 0));
        assertNull(w.reachableStore(), "not from across the room");
        assertTrue(w.targets().contains(barrel), "her blows land on it");
        assertFalse(barrel.living(), "but she does not lock on to it");
        barrel.hurt(Store.Kind.BARREL.toughness() + 1, Vec3.ZERO, true);
        assertTrue(barrel.broken());
        w.tick(0.01);
        assertFalse(w.stores().contains(barrel), "gone");
        assertFalse(w.targets().contains(barrel));
        List<World.GroundItem> spilled = List.copyOf(w.items());
        assertEquals(2, spilled.size(), "the sage in one heap and the sword");
        int sage = 0;
        for (World.GroundItem g : spilled) {
            assertTrue(g.position.horizontalDistance(barrel.position()) < 1, "where it stood");
            if (g.item.def() == Pickings.SAGE) sage += g.amount();
        }
        assertEquals(3, sage);
    }

    @Test
    void aStorageChestShattersAndSpillsWhatItHeld() {
        World w = new World(new Wardrobe());
        Chest c = w.addChest(new Chest("ornate_chest", new Vec3(0, 0, -3), Math.PI));
        Store s = w.addStore(new Store(4, Store.Kind.CHEST, c.position(), Math.PI));
        c.setStore(s);
        s.fill("sage x2");
        assertTrue(s.alive(), "something to hit");
        assertEquals(20, s.items().size());
        s.hurt(1000, Vec3.ZERO, true);
        w.tick(0.01);
        assertFalse(w.chests().contains(c), "the chest is gone");
        assertFalse(w.stores().contains(s));
        assertEquals(Store.Kind.CHEST.pieces(), w.shards().pieces().size(), "in pieces");
        assertEquals(1, w.items().size(), "the sage spilled out");
    }

    @Test
    void piecesFallSettleAndFade() {
        Shards shards = new Shards();
        shards.burst(new Vec3(0, 0, 0), 0.3, 0.9, new int[]{0x8A5A30, 0x5A5A60}, 20, 0.12, Terrain.FLAT);
        assertEquals(20, shards.pieces().size());
        double highest = 0;
        for (int i = 0; i < 30; i++) {
            shards.tick(0.02, Terrain.FLAT);
            for (Shards.Piece p : shards.pieces()) highest = Math.max(highest, p.at().y());
        }
        assertTrue(highest > 0.9, "thrown up");
        for (int i = 0; i < 300; i++) shards.tick(0.02, Terrain.FLAT);
        for (Shards.Piece p : shards.pieces()) assertEquals(p.lift(), p.at().y(), 1e-9, "lying on the ground");
        for (int i = 0; i < 300; i++) shards.tick(0.02, Terrain.FLAT);
        assertTrue(shards.pieces().isEmpty(), "faded away");
    }

    @Test
    void aPotShattersAtATouch() {
        assertEquals(Store.Kind.POT, Store.Kind.ofBuilding("pot"));
        assertEquals(Store.Kind.LARGE_POT, Store.Kind.ofBuilding("large_pot"));
        Store pot = new Store(2, Store.Kind.POT, Vec3.ZERO, 0);
        pot.hurt(8, Vec3.ZERO, true);
        assertTrue(pot.broken());
        assertTrue(Store.Kind.POT.clay());
    }

    @Test
    void aStorageChestThrowsOutNoLoot() {
        World w = new World(new Wardrobe());
        w.seed(5);
        Chest c = w.addChest(new Chest("ornate_chest", new Vec3(0, 0, -3), Math.PI));
        Store s = w.addStore(new Store(9, Store.Kind.CHEST, c.position(), Math.PI));
        c.setStore(s);
        s.fill("sage");
        w.player().setPosition(new Vec3(0, 0, -1.6));
        assertNull(w.reachableStore(), "a chest's is opened as a chest, not as a barrel");
        c.toggle(null);
        for (int i = 0; i < 200; i++) w.tick(0.01);
        assertTrue(w.items().isEmpty(), "nothing thrown out of it");
        assertEquals(1, s.items().count(), "what it holds stays in it");
    }

    @Test
    void lockingOnPassesOverWhatIsNotAlive() {
        Store barrel = new Store(1, Store.Kind.BARREL, new Vec3(0, 0, -3), 0);
        List<Target> targets = List.of(barrel);
        assertNull(Combat.lockCandidate(new Vec3(0, 1.5, 0), new Vec3(0, 0, -1), Vec3.ZERO, targets, null));
    }
}
