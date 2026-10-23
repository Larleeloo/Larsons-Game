package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Rarity, the weapon chart, items, and what a loot chest gives. */
class LootTest {

    @Test
    void theDropRatesAddUpToAHundredAndComeOutAsWritten() {
        double total = 0;
        for (Rarity r : Rarity.values()) total += r.weight();
        assertEquals(100, total, 1e-9);
        Random random = new Random(1);
        Map<Rarity, Integer> seen = new EnumMap<>(Rarity.class);
        int n = 400_000;
        for (int i = 0; i < n; i++) seen.merge(Rarity.roll(random), 1, Integer::sum);
        for (Rarity r : Rarity.values()) {
            double share = 100.0 * seen.getOrDefault(r, 0) / n;
            assertEquals(r.weight(), share, Math.max(0.05, r.weight() * 0.03), r.label());
        }
    }

    @Test
    void aFloorDrawsOnlyFromTheRaritiesAboveIt() {
        Random random = new Random(2);
        for (int i = 0; i < 10_000; i++) assertTrue(Rarity.roll(random, Rarity.RARE).atLeast(Rarity.RARE));
    }

    @Test
    void eachBorderIsOneInAHundredAndNeverTwo() {
        Random random = new Random(3);
        Map<Border, Integer> seen = new EnumMap<>(Border.class);
        int n = 300_000;
        for (int i = 0; i < n; i++) {
            Border b = Border.roll(random);
            if (b != null) seen.merge(b, 1, Integer::sum);
        }
        for (Border b : Border.values()) {
            assertEquals(1.0, 100.0 * seen.getOrDefault(b, 0) / n, 0.08, b.label());
        }
    }

    @Test
    void theChartNamesEveryTierAsLarsonWroteIt() {
        assertEquals("Basic Axe", Item.of(ItemDef.BATTLE_AXE).name());
        assertEquals("Sharper Sword", Item.of(ItemDef.SWORD, Tier.SHARPER).name());
        assertEquals("Magic Staff", Item.of(ItemDef.CRYSTAL_STAFF, Tier.MAGIC).name());
        assertEquals("Basic Longbow", Item.of(ItemDef.LONGBOW).name());
        assertEquals("Crafted Bow", Item.of(ItemDef.LONGBOW, Tier.CRAFTED).name());
        assertEquals("Perfect Crossbow", Item.of(ItemDef.CROSSBOW, Tier.PERFECT).name());
        assertEquals("Mythic Axe", Item.of(ItemDef.BATTLE_AXE, Tier.MYTHIC).name());
        assertEquals(Rarity.EPIC, Item.of(ItemDef.SWORD, Tier.CRYSTAL).rarity());
        assertEquals(Rarity.RARE, Item.of(ItemDef.CRYSTAL_STAFF, Tier.MAGIC).rarity());
        assertEquals("Venom Crystal Axe", Item.of(ItemDef.BATTLE_AXE, Tier.CRYSTAL).withBorder(Border.VENOM).fullName());
        // each column of the chart: its tier's rarity
        assertEquals(List.of(Tier.MAGIC, Tier.HONED, Tier.PERFECT, Tier.MYTHIC), Tier.row("wand").tiers());
        assertEquals(0x5D86FA, Tier.MAGIC.projectile(), "the Magic Staff shoots blue");
    }

    @Test
    void anItemIsWrittenAsOneWordAndReadBack() {
        Item axe = Item.of(ItemDef.BATTLE_AXE, Tier.CRYSTAL).withBorder(Border.BONDED);
        assertEquals("battle_axe:crystal:bonded", axe.key());
        assertEquals(axe, Item.parse(axe.key()));
        assertEquals("sword", Item.of(ItemDef.SWORD).key(), "the lowest tier goes without saying");
        assertEquals(Item.of(ItemDef.SWORD), Item.parse("sword"));
        assertEquals(Item.of(ItemDef.LONGBOW, Tier.PERFECT), Item.parse("longbow perfect"));
        assertEquals(Item.of(ItemDef.CROSSBOW, Tier.CRAFTED), Item.parse("crossbow rare"), "by rarity: its tier of it");
        assertEquals(Item.of(ItemDef.SWORD), Item.parse("sword magic"), "a tier it does not come in is ignored");
        assertNull(Item.parse("no_such_thing"));
    }

    @Test
    void theDropsAreEveryTierOfEveryWeaponTheGameHas() {
        Loot loot = new Loot();
        assertEquals(7, loot.pool(Rarity.COMMON).size(), "a Basic one of all but the wand");
        assertTrue(loot.pool(Rarity.UNCOMMON).contains(Item.of(ItemDef.BATTLE_AXE, Tier.SHARPER)));
        assertTrue(loot.pool(Rarity.RARE).contains(Item.of(ItemDef.CRYSTAL_STAFF, Tier.MAGIC)));
        assertTrue(loot.pool(Rarity.RARE).contains(Item.of(ItemDef.WAND, Tier.MAGIC)));
        assertEquals(8, loot.pool(Rarity.MYTHIC).size());
        int wand = 0;
        for (Rarity r : Rarity.values()) for (Item i : loot.pool(r)) if (i.id().equals("wand")) wand++;
        assertEquals(4, wand, "a Magic, Honed, Perfect and Mythic Wand");
        // and anything else added with its rarity
        ItemDef belt = new ItemDef("belt", "Belt", Slot.BELT, 0.8, null, com.larsons.game.sprite.Stance.SWORD);
        loot.add(Item.of(belt, Rarity.UNCOMMON));
        assertTrue(loot.pool(Rarity.UNCOMMON).contains(Item.of(belt, Rarity.UNCOMMON)));
    }

    @Test
    void aChestGivesThreeToSevenItemsOneAtLeastRare() {
        Loot loot = new Loot();
        Random random = new Random(4);
        int[] counts = new int[Loot.MAX + 1];
        for (int i = 0; i < 20_000; i++) {
            List<Item> lot = loot.roll(random);
            assertTrue(lot.size() >= Loot.MIN && lot.size() <= Loot.MAX, "size " + lot.size());
            assertTrue(lot.stream().anyMatch(it -> it.rarity().atLeast(Rarity.RARE)), lot.toString());
            counts[lot.size()]++;
        }
        for (int n = Loot.MIN; n <= Loot.MAX; n++) assertTrue(counts[n] > 3000, "every count comes up: " + n);
    }

    @Test
    void openingTheChestThrowsItsLootOutAndItLandsRoundItsFront() {
        World w = new World(new Wardrobe());
        w.seed(5);
        Chest c = w.addChest(new Chest("ornate_chest", new Vec3(0, 0, -3), Math.PI));   // its front to her
        w.player().setPosition(new Vec3(0, 0, -1.4));
        c.toggle(null);
        w.tick(0.01);
        List<World.GroundItem> lot = List.copyOf(w.items());
        assertTrue(lot.size() >= Loot.MIN && lot.size() <= Loot.MAX);
        assertTrue(lot.stream().allMatch(World.GroundItem::beam), "every one in a beam of light");
        assertTrue(lot.stream().noneMatch(g -> g.out(w.time())), "still in the chest");
        assertNull(w.reachable(), "nothing to pick up while they are in the air");
        for (int i = 0; i < 300; i++) w.tick(0.01);
        for (World.GroundItem g : lot) {
            assertTrue(g.landed(w.time()));
            double d = g.position.horizontalDistance(c.position());
            assertTrue(d > Chest.REACH - 0.3 && d < 2.4, "landed " + d + " m out");
            assertEquals(g.position.x(), g.centre(w.time()).x(), 1e-9);
        }
        assertTrue(lot.stream().allMatch(g -> g.position.z() > c.position().z() - 0.6), "in front of it");
        w.player().setPosition(lot.get(0).position);
        assertNotNull(w.reachable());
        // shut and open again with the lot still lying there: nothing more
        c.toggle(null);
        for (int i = 0; i < 100; i++) w.tick(0.01);
        c.toggle(null);
        w.tick(0.01);
        assertEquals(lot.size(), w.items().size(), "it gives again only once all of it is picked up");
        // pick everything up, and it gives a new lot
        w.player().inventory().resize(40);
        for (World.GroundItem g : lot) assertTrue(w.pickUp(g));
        c.toggle(null);
        for (int i = 0; i < 100; i++) w.tick(0.01);
        c.toggle(null);
        w.tick(0.01);
        assertTrue(w.items().size() >= Loot.MIN);
    }

    @Test
    void anItemDroppedAgainHasNoBeam() {
        World w = new World(new Wardrobe());
        Item crystal = Item.of(ItemDef.BATTLE_AXE, Tier.CRYSTAL);
        w.player().inventory().add(crystal);
        assertEquals(crystal, w.dropSlot(0));
        assertFalse(w.items().get(0).beam());
        assertEquals(crystal, w.items().get(0).item, "the tier goes with it");
    }
}
