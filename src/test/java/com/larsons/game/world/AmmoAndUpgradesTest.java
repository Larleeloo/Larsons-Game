package com.larsons.game.world;

import com.larsons.game.combat.Combat;
import com.larsons.game.combat.Dummy;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Ammunition used up every so many swings or shots, coins, and climbing a weapon's upgrade tiers. */
class AmmoAndUpgradesTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);
    private static final Player.Intent ATTACK = new Player.Intent(0, 0, false, false, false, true);

    private static World holding(Item weapon) {
        Wardrobe wardrobe = new Wardrobe();
        if (weapon.is(ItemDef.SWORD)) wardrobe.set(Slot.CARRY_RIGHT, "sword");
        World w = new World(wardrobe);
        w.player().inventory().add(weapon);
        w.equipSelected();
        w.seed(11);
        w.animals().setEnabled(false);
        w.ammo().setEnabled(true);
        return w;
    }

    private static void play(World w, Player.Intent first, double seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            w.player().update(DT, i == 0 ? first : Player.Intent.NONE, DUR);
            w.tick(DT);
        }
    }

    @Test
    void eachWeaponHasItsAmmunitionLastingLongerTierByTier() {
        assertSame(Supplies.WHETSTONE, Ammo.of(ItemDef.SWORD));
        assertSame(Supplies.WHETSTONE, Ammo.of(ItemDef.DAGGER));
        assertSame(Supplies.IRON_WEDGES, Ammo.of(ItemDef.BATTLE_AXE));
        assertSame(Supplies.MACE_STUDS, Ammo.of(ItemDef.MACE));
        assertSame(Supplies.SMALL_CRYSTAL, Ammo.of(ItemDef.WAND));
        assertSame(Supplies.LARGE_CRYSTAL, Ammo.of(ItemDef.CRYSTAL_STAFF));
        assertSame(Supplies.ARROW_BUNDLE, Ammo.of(ItemDef.LONGBOW));
        assertSame(Supplies.BOLT_BUNDLE, Ammo.of(ItemDef.CROSSBOW));
        assertEquals(Ammo.EVERY, Ammo.every(Item.of(ItemDef.SWORD)));
        assertEquals(Ammo.EVERY + 2 * Ammo.EVERY_STEP, Ammo.every(Item.parse("sword crystal")));
        assertTrue(Ammo.shoots(ItemDef.LONGBOW));
        assertFalse(Ammo.shoots(ItemDef.MACE));
    }

    @Test
    void oneWhetstoneLastsTwelveSwingsAndWithoutOneTheSwordIsDull() {
        World w = holding(Item.of(ItemDef.SWORD));
        Inventory inv = w.player().inventory();
        inv.put(Item.of(Supplies.WHETSTONE), 1, true);
        Item sword = w.wielded();
        assertEquals(Ammo.Use.RELOADED, w.ammo().use(sword, inv, false));
        assertEquals(0, inv.total(Item.of(Supplies.WHETSTONE)), "taken from her inventory");
        for (int i = 0; i < 11; i++) assertEquals(Ammo.Use.LOADED, w.ammo().use(sword, inv, false));
        assertEquals(0, w.ammo().left(sword));
        assertEquals(Ammo.Use.DULL, w.ammo().use(sword, inv, false));
        assertEquals(Ammo.Use.FREE, w.ammo().use(sword, inv, true), "a card spares it");

        // in a fight: half the damage, said under the dice
        Dummy d = w.addDummy(new Dummy(new Vec3(0, 0, -1.2), SpriteView.headingOf(0, 1), 1000, 6));
        w.clock().setHour(12);
        play(w, ATTACK, 0.05);
        Combat.Show show = w.combat().show();
        assertTrue(show.notes().stream().anyMatch(n -> n.text().startsWith("Dull")));
        play(w, Player.Intent.NONE, 0.6);
        assertEquals(1000 - Math.round(show.strike().roll().total() * Ammo.DULL), d.health(), 1e-9);
    }

    @Test
    void aBowWithNoArrowsDoesNotShoot() {
        World w = holding(Item.of(ItemDef.LONGBOW));
        assertEquals(Ammo.Use.EMPTY, w.ammo().use(w.wielded(), w.player().inventory(), false));
        w.player().inventory().put(Item.of(Supplies.ARROW_BUNDLE), 2, true);
        assertEquals(Ammo.Use.RELOADED, w.ammo().use(w.wielded(), w.player().inventory(), false));
        assertEquals(1, w.ammo().carried(w.wielded(), w.player().inventory()));
    }

    @Test
    void coinsGoIntoHerPurse() {
        World w = holding(Item.of(ItemDef.SWORD));
        World.GroundItem heap = w.spawn(Item.of(Supplies.COINS), w.player().ground(), 25);
        assertTrue(w.pickUp(heap));
        assertEquals(25, w.player().coins());
        assertFalse(w.player().inventory().contains(Supplies.COINS), "never in a slot");
        assertTrue(w.player().spend(20));
        assertFalse(w.player().spend(20));
        assertEquals(5, w.player().coins());
    }

    @Test
    void aWeaponClimbsItsTiersForIngots() {
        Inventory inv = new Inventory(25);
        inv.set(0, Item.parse("sword basic"));
        List<Upgrades.Step> path = Upgrades.path(inv.get(0), r -> 1, 20);
        assertEquals(5, path.size(), "Basic, Sharper, Crystal, Perfect, Mythic");
        assertTrue(path.get(0).now());
        assertTrue(path.get(4).mystery(), "the Mythic a mystery until it is reached");
        assertEquals(Upgrades.Result.SHORT, Upgrades.upgrade(inv, 0));
        inv.put(Item.of(Supplies.UPGRADE_INGOT), 4, true);
        assertEquals(1, Upgrades.nextCost(inv.get(0)), "an Uncommon tier: one ingot");
        assertEquals(Upgrades.Result.UPGRADED, Upgrades.upgrade(inv, 0));
        assertEquals("Sharper Sword", inv.get(0).name());
        assertEquals(3, inv.total(Item.of(Supplies.UPGRADE_INGOT)));
        assertEquals(3, Upgrades.nextCost(inv.get(0)), "an Epic tier: three");
        assertEquals(Upgrades.Result.UPGRADED, Upgrades.upgrade(inv, 0));
        assertEquals("Crystal Sword", inv.get(0).name());
        assertEquals(Upgrades.Result.SHORT, Upgrades.upgrade(inv, 0), "a Legendary tier costs five");
        assertFalse(Upgrades.path(inv.get(0), r -> 1, 20).get(4).reached());
    }
}
