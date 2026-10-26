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

    /** A made-up item to fill slots with. */
    private static Item thing(String id) {
        return Item.of(new ItemDef(id, id, Slot.OTHER, 1.0, null, Stance.SWORD));
    }

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
        for (int i = 0; i < Inventory.HOTBAR; i++) assertEquals(i, inv.add(thing("item" + i)));
        assertEquals(5, inv.add(thing("sixth")), "the hotbar full, the rest of the inventory");
        assertEquals(6, inv.add(thing("seventh")));
        assertEquals(7, inv.add(thing("eighth")));
        assertTrue(inv.isFull());
        assertEquals(-1, inv.add(thing("ninth")), "no room");
        assertEquals(8, inv.count());
        assertEquals(List.of("item0", "item1", "item2", "item3", "item4", "sixth", "seventh", "eighth"),
                inv.items().stream().map(Item::id).toList());
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
        inv.set(4, Item.of(ItemDef.SWORD));
        assertEquals(Item.of(ItemDef.SWORD), inv.held());
    }

    @Test
    void resizingKeepsWhatFitsAndHandsBackTheRest() {
        Inventory inv = new Inventory(10);
        Item a = thing("a"), c = thing("c");
        inv.set(1, a);
        inv.set(6, thing("b"));
        inv.set(9, c);
        assertTrue(inv.resize(14).isEmpty());
        assertEquals(14, inv.size());
        assertEquals(c, inv.get(9));
        assertEquals(List.of(), inv.resize(8), "c moves into a free slot that stays");
        assertEquals(8, inv.size());
        assertTrue(inv.contains(c));
        inv.set(7, thing("d"));
        assertEquals(4, inv.count());
        List<Item> spilled = inv.resize(5);
        assertEquals(3, spilled.size(), "nothing is lost without a word");
        assertEquals(a, inv.get(1), "the hotbar stays");
    }

    @Test
    void swappingAndTaking() {
        Inventory inv = new Inventory(6);
        Item x = thing("x");
        inv.set(0, x);
        inv.swap(0, 5);
        assertNull(inv.get(0));
        assertEquals(x, inv.get(5));
        assertEquals(x, inv.take(5));
        assertEquals(0, inv.count());
        assertFalse(inv.remove(x));
    }

    @Test
    void pickingUpPutsItInTheHotbarAndInHand() {
        World w = new World(swordAndShield());
        w.spawn(ItemDef.BATTLE_AXE, new Vec3(1, 0, 0));
        w.spawn(ItemDef.LONGBOW, new Vec3(1, 0, 0.2));
        assertTrue(w.pickUp(w.reachable()));
        Player p = w.player();
        assertEquals(Stance.AXE, p.stance());
        assertEquals(0, p.inventory().indexOf(ItemDef.BATTLE_AXE));
        assertEquals(Item.of(ItemDef.BATTLE_AXE, Tier.BASIC), p.inventory().get(0), "lying about: a Basic Axe");
        assertTrue(w.pickUp(w.reachable()));
        assertEquals(1, p.inventory().selected(), "the slot it lands in is selected");
        assertEquals(Stance.BOW, p.stance());
        // choosing the other slot takes the other weapon up
        w.select(0);
        assertEquals(Stance.AXE, p.stance());
        w.select(3);
        assertTrue(p.handsEmpty(), "an empty slot: empty hands, not the wardrobe's sword and shield");
        assertEquals(Stance.BLADE, p.stance());
        assertNull(w.wielded());
    }

    @Test
    void theCrystalStaffIsPickedUpIntoTheHotbarAndHeldInItsStance() {
        World w = new World(swordAndShield());
        w.spawn(ItemDef.CRYSTAL_STAFF, new Vec3(1, 0, 0));
        assertTrue(w.pickUp(w.reachable()));
        Player p = w.player();
        assertEquals(Stance.STAFF, p.stance());
        assertEquals(0, p.inventory().indexOf(ItemDef.CRYSTAL_STAFF));
        assertEquals(ItemDef.CRYSTAL_STAFF, ItemDef.of(Stance.STAFF));
        w.select(3);
        assertTrue(p.handsEmpty());
        w.select(0);
        assertEquals(Stance.STAFF, p.stance());
        assertFalse(p.handsEmpty());
    }

    @Test
    void theOneHandedWeaponsArePickedUpIntoTheHotbarAndHeldInTheirStances() {
        World w = new World(swordAndShield());
        Player p = w.player();
        ItemDef[] weapons = {ItemDef.WAND, ItemDef.MACE, ItemDef.DAGGER};
        Stance[] stances = {Stance.WAND, Stance.MACE, Stance.DAGGER};
        for (int i = 0; i < weapons.length; i++) {
            w.spawn(weapons[i], new Vec3(1, 0, 0));
            assertTrue(w.pickUp(w.reachable()), weapons[i].id());
            assertEquals(stances[i], p.stance());
            assertEquals(i, p.inventory().indexOf(weapons[i]));
            assertEquals(weapons[i], ItemDef.of(stances[i]));
            assertEquals(weapons[i], ItemDef.byId(weapons[i].id()));
        }
        assertEquals("wand", ItemDef.WAND.id());
        assertEquals("mace", ItemDef.MACE.id());
        assertEquals("dagger", ItemDef.DAGGER.id());
        w.select(3);
        assertTrue(p.handsEmpty());
        w.select(0);
        assertEquals(Stance.WAND, p.stance());
        w.select(1);
        assertEquals(Stance.MACE, p.stance());
        w.select(2);
        assertEquals(Stance.DAGGER, p.stance());
    }

    @Test
    void aWeaponInTheRestOfTheInventoryIsSwappedIntoHandToBeWielded() {
        World w = new World(swordAndShield(), 10);
        Inventory inv = w.player().inventory();
        inv.set(7, Item.of(ItemDef.CROSSBOW, Tier.PERFECT));
        inv.select(2);
        assertTrue(w.wield(Stance.CROSSBOW));
        assertEquals(Item.of(ItemDef.CROSSBOW, Tier.PERFECT), inv.get(2));
        assertEquals(inv.get(2), w.wielded(), "her hands draw the Perfect Crossbow");
        assertEquals(Stance.CROSSBOW, w.player().stance());
        assertFalse(w.wield(Stance.AXE));
    }

    @Test
    void aFullInventoryLeavesTheItemLying() {
        World w = new World(swordAndShield(), 5);
        for (int i = 0; i < 5; i++) w.player().inventory().add(thing("rock" + i));
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
        int at = w.player().inventory().indexOf(ItemDef.SWORD);
        assertEquals(Item.of(ItemDef.SWORD), w.dropSlot(at));
        assertNull(w.player().wardrobe().get(Slot.CARRY_RIGHT));
        assertEquals(1, w.items().size(), "lying on the ground before her");
        assertNull(w.dropSlot(at), "nothing left to drop");
    }

    // --- what is in her hands ------------------------------------------------------------

    @Test
    void theSwordIsInHerHandOnlyWhileItsHotbarSlotIsSelected() {
        World w = new World(swordAndShield());
        Player p = w.player();
        w.equipSelected();
        assertTrue(p.handsEmpty(), "carrying nothing she holds nothing, whatever the wardrobe has in its hands");
        assertNull(w.wielded());
        p.inventory().add(Item.of(ItemDef.SWORD));
        w.equipSelected();
        assertFalse(p.handsEmpty());
        assertEquals(Stance.SWORD, p.stance(), "the sword, and the wardrobe's shield with it");
        assertEquals(Item.of(ItemDef.SWORD), w.wielded());
        w.select(2);
        assertTrue(p.handsEmpty(), "an empty slot: no sword");
        assertNull(w.wielded(), "and a swing is a blow with no weapon");
        w.select(0);
        assertFalse(p.handsEmpty());
        // moved out of the hotbar into the rest of the inventory, it is no longer in hand
        p.inventory().swap(0, 7);
        w.equipSelected();
        assertTrue(p.handsEmpty());
        assertTrue(w.wield(Stance.SWORD), "taken up again: swapped into the selected slot");
        assertEquals(0, p.inventory().indexOf(ItemDef.SWORD));
        assertFalse(p.handsEmpty());
        assertTrue(w.wield(Stance.FREE), "empty hands: an empty hotbar slot is selected");
        assertTrue(p.handsEmpty());
        assertNull(p.inventory().held());
    }

    @Test
    void theSwordSelectedIsPutBackInTheWardrobesHandIfItHadLetGo() {
        World w = new World(new Wardrobe());
        w.player().inventory().add(Item.of(ItemDef.SWORD));
        w.equipSelected();
        assertEquals("sword", w.player().wardrobe().get(Slot.CARRY_RIGHT));
        assertFalse(w.player().handsEmpty());
    }

    // --- sorting -----------------------------------------------------------------------

    private static Item wearable(String id) {
        return Item.of(Wearables.byId(id));
    }

    @Test
    void theRestOfTheInventorySortsByNameOrRarityAndTheHotbarStays() {
        Inventory inv = new Inventory(14);
        Item mace = Item.of(ItemDef.MACE), wand = Item.of(ItemDef.WAND);
        inv.set(0, mace);
        inv.set(2, wand);
        Item belt = wearable("belt"), epicBelt = Item.of(Wearables.byId("belt"), Rarity.EPIC);
        Item beanie = wearable("beanie"), hoops = wearable("gold_hoops"), shoes = wearable("shoes");
        Item necklace = wearable("silver_necklace"), gloves = wearable("gloves");
        inv.set(6, shoes);
        inv.set(7, necklace);
        inv.set(9, belt);
        inv.set(10, hoops);
        inv.set(11, beanie);
        inv.set(12, gloves);
        inv.set(13, epicBelt);

        inv.sort(Inventory.Order.NAME);
        assertEquals(List.of(beanie, epicBelt, belt, gloves, hoops, shoes, necklace), bag(inv),
                "A to Z, packed to the front; the rarer of two alike first");
        assertNull(inv.get(12));
        assertNull(inv.get(13));
        assertEquals(mace, inv.get(0), "the hotbar stays as it is");
        assertNull(inv.get(1));
        assertEquals(wand, inv.get(2));

        inv.sort(Inventory.Order.RARITY);
        assertEquals(List.of(epicBelt, hoops, necklace, belt, gloves, beanie, shoes), bag(inv),
                "the rarest first (Epic, Rare, Uncommon, Common), by name within one");
        assertEquals(mace, inv.get(0));
        assertEquals(9, inv.count(), "nothing lost or doubled");
    }

    /** What is in the rest of the inventory, in slot order. */
    private static List<Item> bag(Inventory inv) {
        List<Item> out = new java.util.ArrayList<>();
        for (int i = Inventory.HOTBAR; i < inv.size(); i++) if (inv.get(i) != null) out.add(inv.get(i));
        return out;
    }

    @Test
    void stowingPutsAnItemInTheRestOfTheInventoryBeforeTheHotbar() {
        Inventory inv = new Inventory(7);
        assertEquals(Inventory.HOTBAR, inv.stow(Item.of(ItemDef.DAGGER)));
        assertEquals(Inventory.HOTBAR + 1, inv.stow(Item.of(ItemDef.MACE)));
        assertEquals(0, inv.stow(Item.of(ItemDef.WAND)), "the rest full: the hotbar");
    }

    @Test
    void clearingTheGroundTakesEveryItemOffIt() {
        World w = new World(swordAndShield());
        w.spawn(ItemDef.BATTLE_AXE, new Vec3(1, 0, 0));
        w.spawn(ItemDef.LONGBOW, new Vec3(2, 0, 0));
        w.spawn(Item.of(Wearables.byId("belt")), new Vec3(3, 0, 0));
        assertEquals(3, w.clearGround());
        assertTrue(w.items().isEmpty());
        assertNull(w.reachable());
        assertEquals(0, w.clearGround());
    }
}
