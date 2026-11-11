package com.larsons.game.world;

import com.larsons.game.world.Goods.Good;
import com.larsons.game.world.Recipes.Part;
import com.larsons.game.world.Recipes.Recipe;
import com.larsons.game.world.Recipes.Station;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The goods (potions, scrolls, keys, eggs, rods, packs, trinkets) and what the workbench and the cauldron make. */
class GoodsAndRecipesTest {

    @Test
    void everyGoodHasARarityAPriceAndANameThatReadsBack() {
        Set<String> ids = new HashSet<>();
        for (Good g : Goods.all()) {
            assertTrue(ids.add(g.id()), g.id() + " twice");
            assertNotNull(g.rarity(), g.id());
            assertTrue(g.price() > 0, g.id() + " has no price");
            assertFalse(g.def().name().isBlank(), g.id());
            assertSame(g.def(), ItemDef.byId(g.id()), g.id());
            Item found = Goods.item(g);
            assertEquals(g.rarity(), found.rarity(), g.id());
            assertEquals(found, Item.parse(found.key()), g.id());
            assertSame(g, Goods.of(found));
            assertEquals(g.price(), Shop.worth(found), g.id());
            assertEquals(g.rarity(), Goods.rarityOf(g.def()));
        }
        for (Goods.Kind k : Goods.Kind.values()) assertFalse(Goods.ofKind(k).isEmpty(), k.label());
        assertNull(Goods.get("no_such_thing"));
        assertNull(Goods.get(null));
    }

    @Test
    void theThingsLarsonAskedForAreAllThere() {
        for (String id : new String[]{"crystal_ball", "crystal_shield", "drinking_horn", "dragon_egg", "health_potion",
                "scroll_of_fury", "skeleton_key", "satchel", "backpack", "willow_rod"}) {
            Good g = id.endsWith("_egg") ? eggOf(id.substring(0, id.length() - 4)) : Goods.get(id);
            assertNotNull(g, id);
        }
        // rods of every rarity from Common up, each dearer than the last
        for (int i = 1; i < Goods.RODS.size(); i++) {
            Good a = Goods.RODS.get(i - 1), b = Goods.RODS.get(i);
            assertTrue(b.rarity().ordinal() > a.rarity().ordinal(), b.id());
            assertTrue(b.price() > a.price(), b.id());
        }
        assertEquals(Rarity.COMMON, Goods.RODS.get(0).rarity());
        // packs too, each holding more
        int slots = 0;
        for (Good p : Goods.PACKS) {
            int more = Integer.parseInt(p.arg());
            assertTrue(more > slots, p.id());
            slots = more;
            assertTrue(p.spent() && p.usable(), p.id());
        }
        // an egg for every creature, and the dragon's - the rarest thing there is - on top
        Good dragon = eggOf("dragon");
        assertEquals(Rarity.MYTHIC, dragon.rarity());
        for (Good e : Goods.EGGS) {
            assertEquals(Goods.Kind.EGG, e.kind());
            assertFalse(e.arg().isBlank(), e.id());
            assertTrue(e.price() <= dragon.price(), e.id());
        }
        assertTrue(Goods.EGGS.size() >= 20);
        // a key of each rarity, the skeleton key over them all
        Set<Rarity> keyed = new HashSet<>();
        for (Good k : Goods.KEYS) keyed.add(k.rarity());
        assertTrue(keyed.containsAll(List.of(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC,
                Rarity.LEGENDARY)));
        assertEquals(Rarity.MYTHIC, Goods.SKELETON_KEY.rarity());
        // a potion or a scroll does something
        for (Good g : Goods.all()) {
            if (g.kind() != Goods.Kind.POTION && g.kind() != Goods.Kind.SCROLL) continue;
            assertTrue(g.spent() && g.usable(), g.id());
            assertFalse(g.about().isBlank(), g.id());
        }
    }

    private static Good eggOf(String creature) {
        for (Good e : Goods.EGGS) if (e.arg().equals(creature)) return e;
        return null;
    }

    @Test
    void everyRecipeIsOfRealThingsAndEveryPotionCanBeBrewedButNoScroll() {
        Set<String> ids = new HashSet<>(), made = new HashSet<>();
        for (Recipe r : Recipes.all()) {
            assertTrue(ids.add(r.id()), r.id() + " twice");
            assertSame(r, Recipes.byId(r.id()));
            Item out = r.item();
            assertNotNull(out, r.id() + " makes nothing the game has");
            assertEquals(r.out(), out.def().id(), r.id());
            assertTrue(r.makes() >= 1 && r.seconds() > 0, r.id());
            assertFalse(r.needs().isEmpty(), r.id());
            Set<String> parts = new HashSet<>();
            for (Part p : r.needs()) {
                assertNotNull(p.def(), r.id() + " needs " + p.item() + ", which is nothing");
                assertTrue(p.count() >= 1, r.id());
                assertTrue(parts.add(p.item()), r.id() + " needs " + p.item() + " twice");
                assertNotEquals(r.out(), p.item(), r.id() + " is made of itself");
            }
            made.add(r.out());
            Good g = Goods.get(r.out());
            if (g != null && (g.kind() == Goods.Kind.POTION || g.kind() == Goods.Kind.MEAL)) {
                assertEquals(Station.CAULDRON, r.station(), r.id());
            } else {
                assertEquals(Station.BENCH, r.station(), r.id());
            }
        }
        assertEquals(Recipes.all().size(), Recipes.at(Station.BENCH).size() + Recipes.at(Station.CAULDRON).size());
        for (Good g : Goods.ofKind(Goods.Kind.POTION)) assertTrue(made.contains(g.id()), g.id() + " cannot be brewed");
        for (Good g : Goods.ofKind(Goods.Kind.SCROLL)) assertFalse(made.contains(g.id()), g.id() + " can be made");
        for (Good g : Goods.ofKind(Goods.Kind.EGG)) assertFalse(made.contains(g.id()), g.id() + " can be made");
        for (Good g : Goods.RODS) assertTrue(made.contains(g.id()), g.id());
        for (Good g : Goods.PACKS) assertTrue(made.contains(g.id()), g.id());
        for (Good g : Goods.ofKind(Goods.Kind.MAKING)) assertTrue(made.contains(g.id()), g.id() + " is only bought");
        for (String id : new String[]{"crystal_ball", "crystal_shield", "drinking_horn"}) assertTrue(made.contains(id), id);
        assertNull(Recipes.byId("philosophers_stone"));
    }

    @Test
    void makingTakesWhatItNeedsAndGivesWhatItMakes() {
        Recipe potion = Recipes.byId("health_potion");
        Inventory inv = new Inventory(Inventory.DEFAULT_SLOTS);
        assertFalse(Recipes.can(potion, inv));
        assertEquals(0, Recipes.times(potion, inv));
        assertNull(Recipes.make(potion, inv), "nothing to make it of");
        for (Part p : potion.needs()) inv.put(Item.of(p.def()), p.count() * 2 + 1, true);
        assertTrue(Recipes.can(potion, inv));
        assertEquals(2, Recipes.times(potion, inv));
        List<Item> over = Recipes.make(potion, inv);
        assertNotNull(over);
        assertTrue(over.isEmpty());
        assertEquals(1, Recipes.count(inv, "health_potion"));
        for (Part p : potion.needs()) assertEquals(p.count() + 1, Recipes.count(inv, p.item()), p.item());
        assertNotNull(Recipes.make(potion, inv));
        assertEquals(2, Recipes.count(inv, "health_potion"));
        assertNull(Recipes.make(potion, inv), "one of each left is not enough");
        for (Part p : potion.needs()) assertEquals(1, Recipes.count(inv, p.item()), p.item());
        // the greater potion is brewed of the lesser
        Recipe greater = Recipes.byId("greater_health_potion");
        assertTrue(greater.needs().stream().anyMatch(p -> p.item().equals("health_potion")));
        // with no room for what is made, it lies at her feet
        Inventory full = new Inventory(Inventory.HOTBAR);
        Recipe bottles = Recipes.byId("empty_bottle");
        Part glass = bottles.needs().get(0);
        full.set(0, Item.of(glass.def()), glass.count());
        for (int i = 1; i < full.size(); i++) full.set(i, Item.parse("sword"));
        over = Recipes.make(bottles, full);
        assertNotNull(over);
        assertEquals(bottles.makes(), Recipes.count(full, "empty_bottle") + over.size());
        // taking more than there is takes nothing
        assertFalse(Recipes.take(inv, "health_potion", 3));
        assertEquals(2, Recipes.count(inv, "health_potion"));
        assertTrue(Recipes.take(inv, "health_potion", 2));
        assertEquals(0, Recipes.count(inv, "health_potion"));
    }

    @Test
    void nothingMadeSellsForMoreThanItsPartsCostToBuy() {
        // (a shop pays half what a thing is worth: buying the parts, making and selling must never turn a profit)
        List<String> machines = new ArrayList<>();
        for (Recipe r : Recipes.all()) {
            int cost = 0;
            for (Part p : r.needs()) cost += p.count() * Shop.worth(Item.of(p.def()));
            int sells = r.makes() * Math.max(1, Shop.worth(r.item()) / 2);
            if (sells > cost) machines.add(r.id() + ": parts " + cost + ", sells for " + sells);
        }
        assertTrue(machines.isEmpty(), String.join("; ", machines));
    }
}
