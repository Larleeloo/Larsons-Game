package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** A loot pool of the level's own: read, written, rolled - and a chest that gives only its own. */
class DropsTest {

    @Test
    void readAndWrittenAsOneLine() {
        Drops d = Drops.parse("coins x1-5, any_dye 25%, sword:rare 10%, wild berries x3");
        assertEquals(4, d.lines().size());
        assertEquals(new Drops.Line("coins", 1, 5, 100), d.lines().get(0));
        assertEquals(new Drops.Line(Drops.ANY_DYE, 1, 1, 25), d.lines().get(1));
        assertEquals(new Drops.Line("sword:rare", 1, 1, 10), d.lines().get(2));
        assertEquals(new Drops.Line("wild_berries", 3, 3, 100), d.lines().get(3));
        assertEquals("coins x1-5, any_dye 25%, sword:rare 10%, wild_berries x3", d.text());
        assertEquals(d.lines(), Drops.parse(d.text()).lines());
        assertTrue(Drops.parse("").isEmpty());
        assertTrue(Drops.parse(null).isEmpty());
    }

    @Test
    void eachLineComesByItsChanceAndBetweenItsFromAndTo() {
        Drops d = Drops.parse("coins x1-5, any_dye 25%");
        Random r = new Random(3);
        int dyes = 0, n = 20_000;
        for (int i = 0; i < n; i++) {
            List<Drops.Drop> got = d.roll(r, new Loot());
            Drops.Drop coins = got.get(0);
            assertSame(Supplies.COINS, coins.item().def());
            assertTrue(coins.amount() >= 1 && coins.amount() <= 5);
            for (Drops.Drop g : got.subList(1, got.size())) {
                assertTrue(g.item().def().id().startsWith("dye_"), g.item().def().id());
                dyes++;
            }
        }
        assertEquals(0.25, dyes / (double) n, 0.02);
    }

    @Test
    void chestLootIsDrawnAndAnUnknownItemGivesNothing() {
        Drops d = Drops.parse("any_loot x2, no_such_thing x9");
        List<Drops.Drop> got = d.roll(new Random(5), new Loot());
        assertEquals(2, got.size());
        for (Drops.Drop g : got) assertEquals(1, g.amount());
    }

    @Test
    void aChestWithAPoolOfItsOwnGivesThatAndNothingElse() {
        World w = new World(new Wardrobe());
        Chest c = w.addChest(new Chest("ornate_chest", new Vec3(0, 0, -1.2), 0).drops("coins x7, dye_emerald"));
        w.items().clear();
        c.toggle(null);
        for (int i = 0; i < 60; i++) w.tick(1 / 30.0);
        assertEquals(2, w.items().size());
        assertSame(Supplies.COINS, w.items().get(0).def);
        assertEquals(7, w.items().get(0).amount());
        assertEquals("dye_emerald", w.items().get(1).def.id());
    }

    @Test
    void aWeaponLiesOneByOneAndCoinsInOneHeap() {
        assertTrue(Drops.heaps(Item.of(Supplies.COINS)));
        assertFalse(Drops.heaps(Drops.item("sword")));
    }
}
