package com.larsons.game.world;

import java.util.ArrayList;
import java.util.List;

/**
 * A weapon's upgrade tiers, from the one it is now up: a chart weapon climbs
 * its row of Larson's chart ({@link Tier#CHART}: a Basic Sword, a Sharper
 * Sword, a Crystal Sword ...), recoloured tier by tier as the chart has it;
 * a forged one its own tiers, made in the Forge (2 to 10 of them, each with
 * its own name, rarity, dice, colours, ammunition and price, any of them a
 * mystery until it is reached - {@link Forged.Upgrade}).
 *
 * <p>A tier up costs {@link Supplies#UPGRADE_INGOT}s: a chart weapon's by the
 * rarity of the tier it goes to ({@link #cost}), a forged one's what the Forge
 * set. Right-click a weapon in the inventory to see every tier and upgrade it.
 * The top tier of a chart weapon (its Mythic) is a mystery until it is
 * reached.
 */
public final class Upgrades {

    private Upgrades() {}

    /** One tier on a weapon's path: what it is called, how rare, its dice, its ammunition's uses, its price. */
    public record Step(String name, Rarity rarity, String dice, int ammoEvery, int cost, boolean mystery,
                       boolean reached, boolean now) {}

    /** Ingots a chart weapon's tier of {@code r} costs to reach. */
    public static int cost(Rarity r) {
        return switch (r) {
            case COMMON -> 1;
            case UNCOMMON -> 1;
            case RARE -> 2;
            case EPIC -> 3;
            case LEGENDARY -> 5;
            case MYTHIC -> 8;
        };
    }

    /** Whether {@code item} has tiers at all. */
    public static boolean has(Item item) {
        if (item == null) return false;
        if (item.forged() != null) return item.forged().tiers() > 1;
        return item.tier() != null && Tier.row(item.def().id()) != null;
    }

    /** Every tier of {@code item}'s, lowest first ({@code tuning}: the dice a chart weapon of each rarity rolls). */
    public static List<Step> path(Item item, java.util.function.ToIntFunction<Rarity> dice, int sides) {
        List<Step> out = new ArrayList<>();
        if (item == null) return out;
        Forged f = item.forged();
        if (f != null) {
            for (int n = 0; n < f.tiers(); n++) {
                Forged at = f.at(n);
                boolean mystery = n > 0 && at.tier().mystery();
                out.add(new Step(at.name(), at.rarity(), at.diceText(), at.ammoEvery(),
                        n == 0 ? 0 : at.tier().cost(), mystery, n <= f.level(), n == f.level()));
            }
            return out;
        }
        Tier.Row row = Tier.row(item.def().id());
        if (row == null || item.tier() == null) return out;
        List<Tier> tiers = row.tiers();
        int now = tiers.indexOf(item.tier());
        for (int n = 0; n < tiers.size(); n++) {
            Tier t = tiers.get(n);
            Item at = new Item(item.def(), t, null, item.border());
            out.add(new Step(at.name(), t.rarity(), dice.applyAsInt(t.rarity()) + Forged.die(sides),
                    Ammo.every(at), n == 0 ? 0 : cost(t.rarity()), n == tiers.size() - 1 && n > now,
                    n <= now, n == now));
        }
        return out;
    }

    /** {@code item} one tier up, or null at its last (or with no tiers). */
    public static Item next(Item item) {
        if (item == null) return null;
        Forged f = item.forged();
        if (f != null) {
            Forged up = f.upgraded();
            return up == null ? null : up.item();
        }
        Tier.Row row = Tier.row(item.def().id());
        if (row == null || item.tier() == null) return null;
        List<Tier> tiers = row.tiers();
        int now = tiers.indexOf(item.tier());
        return now < 0 || now + 1 >= tiers.size() ? null : new Item(item.def(), tiers.get(now + 1), null, item.border());
    }

    /** The ingots {@code item}'s next tier costs, or -1 at its last. */
    public static int nextCost(Item item) {
        if (item == null) return -1;
        if (item.forged() != null) return item.forged().nextCost();
        Item up = next(item);
        return up == null ? -1 : cost(up.tier().rarity());
    }

    /** How it went. */
    public enum Result { UPGRADED, TOP, SHORT, NONE }

    /**
     * Upgrade the weapon in {@code inv}'s slot {@code slot}, paying its
     * ingots out of {@code inv}: what happened.
     */
    public static Result upgrade(Inventory inv, int slot) {
        Item item = inv.get(slot);
        if (!has(item)) return Result.NONE;
        Item up = next(item);
        if (up == null) return Result.TOP;
        int cost = nextCost(item);
        Item ingot = Item.of(Supplies.UPGRADE_INGOT);
        if (inv.total(ingot) < cost) return Result.SHORT;
        for (int paid = 0; paid < cost; paid++) inv.takeOne(inv.indexOf(ingot));
        inv.set(slot, up);
        return Result.UPGRADED;
    }
}
