package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Recipes.Part;
import com.larsons.game.world.Recipes.Recipe;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The crafting as Larson asked for it: what goes in is gathered, hunted,
 * fished, picked or made of those - never ammunition - and everything
 * gathered or made in between is used for something.
 */
class CraftingTest {

    private static Set<String> needed() {
        Set<String> out = new HashSet<>();
        for (Recipe r : Recipes.all()) for (Part p : r.needs()) out.add(p.item());
        return out;
    }

    @Test
    void noRecipeUsesUpAmmunition() {
        for (Recipe r : Recipes.all()) {
            for (Part p : r.needs()) {
                assertFalse(Supplies.isAmmo(p.def()), r.id() + " uses up " + p.item() + ", which is ammunition");
            }
        }
    }

    @Test
    void everythingGatheredOrMadeInBetweenIsUsedForSomething() {
        Set<String> needed = needed();
        for (Goods.Good g : Goods.ofKind(Goods.Kind.MATERIAL)) assertTrue(needed.contains(g.id()), g.id() + " is for nothing");
        for (Goods.Good g : Goods.ofKind(Goods.Kind.MAKING)) assertTrue(needed.contains(g.id()), g.id() + " is for nothing");
        for (ItemDef d : Hunted.ALL) assertTrue(needed.contains(d.id()) || d.edible(), d.id() + " is for nothing");
        for (ItemDef d : Pickings.ALL) assertTrue(needed.contains(d.id()), d.id() + " goes into nothing");
        // and what is made at the end has a use of its own: drunk, worn, carried, read, held
        for (Goods.Good g : Goods.ofKind(Goods.Kind.KIT)) assertTrue(g.usable() && g.spent(), g.id());
        for (Goods.Good g : List.of(Goods.SPYGLASS, Goods.LANTERN)) assertTrue(g.usable(), g.id());
    }

    @Test
    void everyMaterialIsGatheredSomewhereAndEverySpotHasItsModels() {
        Set<String> gathered = new HashSet<>();
        for (Gathering.Kind k : Gathering.Kind.values()) {
            assertFalse(k.yields().isEmpty(), k.key());
            for (Drops.Line l : k.yields().lines()) {
                assertNotNull(Drops.item(l.item()), k.key() + " gives " + l.item() + ", which is nothing");
                gathered.add(l.item());
            }
            for (String m : List.of(k.model(), k.spentModel())) {
                assertTrue(Files.isRegularFile(Path.of("assets/buildings", m, "profile.json")), m + " has no model");
            }
        }
        for (Goods.Good g : Goods.ofKind(Goods.Kind.MATERIAL)) assertTrue(gathered.contains(g.id()), g.id() + " grows nowhere");
        assertTrue(Files.isRegularFile(Path.of("assets/buildings/smelter/profile.json")));
    }

    @Test
    void aSpotGivesThenStandsWorkedOutUntilItComesBack() {
        World w = new World(new Wardrobe());
        Spot s = w.addSpot(new Spot(7, Gathering.SPOT, "Iron vein", new Vec3(2, 0, 0), 0,
                Map.of("kind", "iron_vein", "regrow", 30)));
        List<Drops.Drop> got = w.gather(s);
        assertNotNull(got);
        int ore = 0;
        for (Drops.Drop d : got) if (d.item().def() == Goods.IRON_ORE.def()) ore += d.amount();
        assertTrue(ore >= 2 && ore <= 4, "ore: " + ore);
        assertNull(w.gather(s), "worked out");
        assertTrue(Gathering.untilBack(s, w.time()) > 29);
        for (int i = 0; i < 31 * 30; i++) w.tick(1 / 30.0);
        assertFalse(Gathering.spent(s, w.time()));
        assertNotNull(w.gather(s), "back again");
    }

    @Test
    void theYieldTimesGivesMore() {
        Spot s = new Spot(1, Gathering.SPOT, "Sand", Vec3.ZERO, 0, Map.of("kind", "sand_bank", "yield", 3));
        List<Drops.Drop> got = Gathering.work(s, 0, new Random(4));
        assertNotNull(got);
        assertTrue(got.get(0).amount() >= 9, "three times 3 to 5 sand: " + got.get(0).amount());
    }

    @Test
    void twoWaysOfMakingOneThingAreToldApart() {
        List<Recipes.Recipe> f = Recipes.at(Recipes.Station.FURNACE);
        Recipes.Recipe coal = f.stream().filter(r -> r.id().equals("iron_bar")).findFirst().orElseThrow();
        Recipes.Recipe charcoal = f.stream().filter(r -> r.id().equals("iron_bar_2")).findFirst().orElseThrow();
        assertEquals("with coal", Recipes.variant(coal, f));
        assertEquals("with charcoal", Recipes.variant(charcoal, f));
        Recipes.Recipe crucible = f.stream().filter(r -> r.out().equals("crucible")).findFirst().orElseThrow();
        assertEquals("", Recipes.variant(crucible, f));
    }
}
