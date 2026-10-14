package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The inventory, its hotbar, and what the selected slot puts in her hands. */
class InventoryTest {

    /** The wardrobe's sword in the right hand and the shield on the left arm: the sword-and-shield stance. */
    private static Wardrobe swordAndShield() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        w.set(Slot.CARRY_LEFT, "round_shield");
        return w;
    }

    @Test
    void itemsGoToTheHotbarFirstThenTheRest() {
        Inventory inv = new Inventory(8);
        for (int i = 0; i < Inventory.HOTBAR; i++) assertEquals(i, inv.add("item" + i));
        assertEquals(5, inv.add("sixth"), "the hotbar full, the rest of the inventory");
        assertEquals(6, inv.add("seventh"));
        assertEquals(7, inv.add("eighth"));
        assertTrue(inv.isFull());
        assertEquals(-1, inv.add("ninth"), "no room");
        assertEquals(8, inv.count());
        assertEquals(List.of("item0", "item1", "item2", "item3", "item4", "sixth", "seventh", "eighth"),
                inv.items());
    }

    @Test
    void anyNumberOfSlotsButNeverFewerThanTheHotbar() {
        assertEquals(5, new Inventory(2).size());
        assertEquals(40, new Inventory(40).size());
        assertEquals(Inventory.DEFAULT_SLOTS, new Player(swordAndShield()).inventory().size());
        assertEquals(12, new World(swordAndShield(), 12).player().inventory().size());
    }

    @Test
    void theHotbarSelectionWrapsWithTheWheelAndClampsWithTheKeys() {
        Inventory inv = new Inventory(10);
        assertEquals(0, inv.selected());
        inv.scroll(-1);
        assertEquals(4, inv.selected(), "left of the first is the last");
        inv.scroll(2);
        assertEquals(1, inv.selected());
        inv.select(9);
        assertEquals(4, inv.selected(), "only the hotbar can be selected");
        inv.set(4, "sword");
        assertEquals("sword", inv.held());
    }

    @Test
    void resizingKeepsWhatFitsAndHandsBackTheRest() {
        Inventory inv = new Inventory(10);
        inv.set(1, "a");
        inv.set(6, "b");
        inv.set(9, "c");
        assertTrue(inv.resize(14).isEmpty());
        assertEquals(14, inv.size());
        assertEquals("c", inv.get(9));
        assertEquals(List.of(), inv.resize(8), "c moves into a free slot that stays");
        assertEquals(8, inv.size());
        assertTrue(inv.contains("c"));
        inv.set(7, "d");
        assertEquals(4, inv.count());
        List<String> spilled = inv.resize(5);
        assertEquals(3, spilled.size(), "nothing is lost without a word");
        assertEquals("a", inv.get(1), "the hotbar stays");
    }

    @Test
    void swappingAndTaking() {
        Inventory inv = new Inventory(6);
        inv.set(0, "x");
        inv.swap(0, 5);
        assertNull(inv.get(0));
        assertEquals("x", inv.get(5));
        assertEquals("x", inv.take(5));
        assertEquals(0, inv.count());
        assertFalse(inv.remove("x"));
    }

    @Test
    void pickingUpPutsItInTheHotbarAndInHand() {
        World w = new World(swordAndShield());
        w.spawn(ItemDef.BATTLE_AXE, new Vec3(1, 0, 0));
        w.spawn(ItemDef.LONGBOW, new Vec3(1, 0, 0.2));
        assertTrue(w.pickUp(w.reachable()));
        Player p = w.player();
        assertEquals(Stance.AXE, p.stance());
        assertEquals(0, p.inventory().indexOf("battle_axe"));
        assertTrue(w.pickUp(w.reachable()));
        assertEquals(1, p.inventory().selected(), "the slot it lands in is selected");
        assertEquals(Stance.BOW, p.stance());
        // choosing the other slot takes the other weapon up
        w.select(0);
        assertEquals(Stance.AXE, p.stance());
        w.select(3);
        assertEquals(Stance.SWORD, p.stance(), "an empty hand: the wardrobe's sword and shield");
    }

    @Test
    void aWeaponInTheRestOfTheInventoryIsSwappedIntoHandToBeWielded() {
        World w = new World(swordAndShield(), 10);
        Inventory inv = w.player().inventory();
        inv.set(7, "crossbow");
        inv.select(2);
        assertTrue(w.wield(Stance.CROSSBOW));
        assertEquals("crossbow", inv.get(2));
        assertEquals(Stance.CROSSBOW, w.player().stance());
        assertFalse(w.wield(Stance.AXE));
    }

    @Test
    void aFullInventoryLeavesTheItemLying() {
        World w = new World(swordAndShield(), 5);
        for (int i = 0; i < 5; i++) w.player().inventory().add("rock" + i);
        w.spawn(ItemDef.CROSSBOW, new Vec3(0.5, 0, 0));
        assertFalse(w.pickUp(w.reachable()));
        assertEquals(1, w.items().size());
    }

    @Test
    void droppingTheSwordTakesItOutOfHerHand() {
        World w = new World(swordAndShield());
        w.spawn(ItemDef.SWORD, new Vec3(0.5, 0, 0));
        assertTrue(w.pickUp(w.reachable()));
        assertTrue(w.player().wardrobe().wearing(Slot.CARRY_RIGHT, "sword"));
        int at = w.player().inventory().indexOf("sword");
        assertEquals(ItemDef.SWORD, w.dropSlot(at));
        assertNull(w.player().wardrobe().get(Slot.CARRY_RIGHT));
        assertEquals(1, w.items().size(), "lying on the ground before her");
        assertNull(w.dropSlot(at), "nothing left to drop");
    }
}
