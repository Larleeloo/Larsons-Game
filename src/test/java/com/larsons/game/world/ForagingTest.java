package com.larsons.game.world;

import com.larsons.game.ambient.Life;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Picking from the foraging bushes, the pickings' stacks in the inventory, and eating them. */
class ForagingTest {

    private static final SceneryProfile.Forage BERRIES = new SceneryProfile.Forage("wild_berries", 3, 3, 240,
            "berry_bush_picked");

    private static Scenery.Placed bush(double x, double z) {
        SceneryProfile p = new SceneryProfile(SceneryProfile.Kind.TUFT, 64, 64, 24, 12, 1.2, 0, 0.5, false,
                List.of("plains"), -1, false, BERRIES, null);
        ScenerySprites.Sprite s = ScenerySprites.describe("berry_bush", p, Path.of("berry_bush.png"), 64 * 24, 64);
        return new Scenery.Placed(s, x, z, 0, false, 0);
    }

    @Test
    void theProfileReadsWhatABushGivesAndItsPickedSheet() {
        SceneryProfile p = SceneryProfile.parse("{\"kind\": \"tuft\", \"frameWidth\": 64, \"frameHeight\": 64, "
                + "\"forage\": {\"item\": \"sage\", \"count\": [1, 3], \"regrow\": 300, \"picked\": \"sage_bush_picked\"}}",
                SceneryProfile.Kind.TUFT);
        assertTrue(p.forages());
        assertEquals(new SceneryProfile.Forage("sage", 1, 3, 300, "sage_bush_picked"), p.forage());
        SceneryProfile bare = SceneryProfile.parse("{\"kind\": \"tuft\", \"variantOf\": \"sage_bush\"}",
                SceneryProfile.Kind.TUFT);
        assertTrue(bare.isVariant());
        assertFalse(bare.forages());
        SceneryProfile one = SceneryProfile.parse("{\"forage\": {\"item\": \"sunfruit\", \"count\": 2}}",
                SceneryProfile.Kind.TUFT);
        assertEquals(2, one.forage().least());
        assertEquals(2, one.forage().most());
        assertEquals(240, one.forage().regrow(), 1e-9, "four minutes unless it says");
    }

    @Test
    void pickingsStackAndFillTheirStacksBeforeANewSlot() {
        Inventory inv = new Inventory(8);
        Item berry = Item.of(Pickings.WILD_BERRIES);
        for (int i = 0; i < Pickings.STACK + 3; i++) assertTrue(inv.stow(berry) >= 0);
        int first = inv.indexOf(berry);
        assertEquals(Pickings.STACK, inv.amount(first), "a full stack");
        assertEquals(Pickings.STACK + 3, inv.total(berry));
        assertEquals(2, inv.count(), "the rest on a second stack");
        assertEquals(berry, inv.takeOne(first));
        assertEquals(Pickings.STACK - 1, inv.amount(first));
        // a sword never stacks
        inv.add(Item.of(ItemDef.SWORD));
        inv.add(Item.of(ItemDef.SWORD));
        assertEquals(1, inv.amount(inv.indexOf(ItemDef.SWORD)));
        // merging onto a stack keeps to its size
        int second = -1;
        for (int i = 0; i < inv.size(); i++) if (i != first && berry.equals(inv.get(i))) second = i;
        assertEquals(3, inv.amount(second));
        assertEquals(2, inv.merge(first, berry, 3), "one fitted, two did not");
        assertEquals(Pickings.STACK, inv.amount(first));
        assertEquals(5, inv.merge(inv.indexOf(ItemDef.SWORD), berry, 5), "nothing onto a sword");
    }

    @Test
    void sortingSwappingAndShrinkingKeepEveryStacksSize() {
        Inventory inv = new Inventory(10);
        inv.set(7, Item.of(Pickings.SAGE), 9);
        inv.set(6, Item.of(Pickings.BILBERRIES), 4);
        inv.sort(Inventory.Order.NAME);
        assertEquals(Pickings.BILBERRIES, inv.get(Inventory.HOTBAR).def());
        assertEquals(4, inv.amount(Inventory.HOTBAR));
        assertEquals(9, inv.amount(Inventory.HOTBAR + 1));
        inv.swap(0, Inventory.HOTBAR + 1);
        assertEquals(9, inv.amount(0));
        inv.set(9, Item.of(Pickings.SUNFRUIT), 3);
        inv.set(8, Item.of(ItemDef.SWORD));
        inv.set(7, Item.of(ItemDef.SWORD));
        inv.set(Inventory.HOTBAR + 1, Item.of(ItemDef.SWORD));
        inv.set(Inventory.HOTBAR + 2, Item.of(ItemDef.SWORD));
        List<Item> spilled = inv.resize(8);
        assertEquals(4, spilled.size(), "the stack of three that had no room spills as three, and the sword");
        assertEquals(3, spilled.stream().filter(i -> i.def() == Pickings.SUNFRUIT).count());
    }

    @Test
    void aBushIsPickedIntoTheInventoryAndGrowsBack() {
        World w = new World(new Wardrobe());
        Scenery.Placed b = bush(0.8, 0);
        w.setScenery(new Scenery(List.of(b)));
        assertEquals(List.of(b), w.bushes());
        assertSame(b, w.reachableBush(), "within her reach");
        List<Item> got = w.pick(b);
        assertEquals(3, got.size());
        assertEquals(3, w.player().inventory().total(Item.of(Pickings.WILD_BERRIES)));
        assertEquals(1, w.player().inventory().count(), "one stack");
        assertTrue(w.picked(b));
        assertNull(w.reachableBush(), "bare");
        assertTrue(w.pick(b).isEmpty(), "nothing on it");
        assertEquals(240, w.regrowsIn(b), 1e-6);
        for (int i = 0; i < 241; i++) w.idle(1);
        assertFalse(w.picked(b), "grown back");
        // a level that grows them back twice as fast, and gives twice as much
        Life life = new Life();
        life.setRegrow(0.5);
        life.setYield(2);
        w.setLife(life);
        assertEquals(6, w.pick(b).size());
        assertEquals(120, w.regrowsIn(b), 1e-6);
    }

    @Test
    void whatHasNoRoomFallsAtHerFeet() {
        World w = new World(new Wardrobe(), Inventory.HOTBAR);
        for (int i = 0; i < Inventory.HOTBAR; i++) w.player().inventory().set(i, Item.of(ItemDef.SWORD));
        Scenery.Placed b = bush(0.8, 0);
        w.setScenery(new Scenery(List.of(b)));
        assertTrue(w.pick(b).isEmpty());
        assertTrue(w.picked(b), "picked all the same");
        assertEquals(1, w.items().size(), "one heap");
        assertEquals(3, w.items().get(0).amount());
    }

    @Test
    void eatingGivesBackOnlyWhatIsSpentAndNothingWhenFull() {
        World w = new World(new Wardrobe());
        Inventory inv = w.player().inventory();
        inv.set(0, Item.of(Pickings.BILBERRIES), 3);
        World.Eaten full = w.eat(0);
        assertFalse(full.ate(), "full: not eaten");
        assertEquals(3, inv.amount(0));
        w.player().setHealth(90);
        w.player().setStamina(50);
        World.Eaten e = w.eat(0);
        assertTrue(e.ate());
        assertEquals(10, e.health(), 1e-9, "only what she was short of");
        assertEquals(15, e.stamina(), 1e-9);
        assertEquals(100, w.player().health(), 1e-9);
        assertEquals(65, w.player().stamina(), 1e-9);
        assertEquals(2, inv.amount(0));
        assertNull(w.eat(1), "nothing to eat there");
        w.combat().setMana(0);
        inv.set(1, Item.of(Pickings.CRYSTAL_BLOOM));
        assertEquals(40, w.eat(1).mana(), 1e-9);
        assertNull(inv.get(1), "the last one eaten");
    }

    @Test
    void aHeapOfPickingsIsPickedUpOntoItsStack() {
        World w = new World(new Wardrobe());
        Item sage = Item.of(Pickings.SAGE);
        w.player().inventory().set(Inventory.HOTBAR, sage, 5);
        World.GroundItem g = w.spawn(sage, w.player().ground(), 4);
        assertTrue(w.pickUp(g));
        assertEquals(9, w.player().inventory().amount(Inventory.HOTBAR));
        assertTrue(w.items().isEmpty());
        assertEquals("+30 stamina", Pickings.SAGE.food().effect());
        assertEquals(Pickings.SUNFRUIT, ItemDef.byId("sunfruit"));
    }
}
