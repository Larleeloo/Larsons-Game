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
 *
 * <p>The clothes and the armour the game comes with have no tiers: only a
 * piece of your own, made in Creative Mode's Tailor ({@link Forged}), climbs
 * tiers - its own, each giving what it was made with.
 */
public final class Upgrades {

    private Upgrades() {}

    /**
     * One tier on an item's path: what it is called, how rare, its dice (""
     * for clothes, which roll none), its ammunition's uses (0: it has none -
     * armour and clothes), its price, and what it gives worn ({@code gives}:
     * each effect's amount; none for a weapon).
     */
    public record Step(String name, Rarity rarity, String dice, int ammoEvery, int cost, boolean mystery,
                       boolean reached, boolean now, java.util.Map<Gear.Effect, Double> gives) {

        public Step {
            gives = gives == null ? java.util.Map.of() : java.util.Collections.unmodifiableMap(
                    gives.isEmpty() ? new java.util.EnumMap<>(Gear.Effect.class) : new java.util.EnumMap<>(gives));
        }

        public Step(String name, Rarity rarity, String dice, int ammoEvery, int cost, boolean mystery,
                    boolean reached, boolean now) {
            this(name, rarity, dice, ammoEvery, cost, mystery, reached, now, java.util.Map.of());
        }

        /** What it gives, a short line each: "+20 health", "+1/s stamina", "Double jump". */
        public List<String> gifts() {
            List<String> out = new ArrayList<>();
            for (var e : gives.entrySet()) out.add(Gear.shortly(e.getKey(), e.getValue()));
            return out;
        }

        /** Whether it is worn (armour or clothes): it has no ammunition. */
        public boolean worn() { return ammoEvery <= 0; }

        /** Whether it rolls dice (a weapon to strike, armour to block). */
        public boolean rolls() { return !dice.isEmpty(); }
    }



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
        return path(item, dice, sides, Armour.Rules.defaults());
    }

    /**
     * Every tier of {@code item}'s, lowest first: a weapon's dice as {@code
     * dice} and {@code sides} say, a piece of armour's as {@code armour} does
     * (and no ammunition: its {@link Step#ammoEvery} is 0).
     */
    public static List<Step> path(Item item, java.util.function.ToIntFunction<Rarity> dice, int sides,
                                  Armour.Rules armour) {
        List<Step> out = new ArrayList<>();
        if (item == null) return out;
        // worn: armour (which blocks with its dice) or clothes (which roll none); either has no ammunition
        boolean worn = item.def().isWearable();
        boolean blocks = Armour.sides(item.def()) > 0;
        Forged f = item.forged();
        if (f != null) {
            for (int n = 0; n < f.tiers(); n++) {
                Forged at = f.at(n);
                boolean mystery = n > 0 && at.tier().mystery();
                out.add(new Step(at.name(), at.rarity(), worn && !blocks ? "" : at.diceText(), worn ? 0 : at.ammoEvery(),
                        n == 0 ? 0 : at.tier().cost(), mystery, n <= f.level(), n == f.level(),
                        worn ? at.gear() : java.util.Map.of()));
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
            String rolls = blocks ? armour.dice(t.rarity()) + Forged.die(Armour.sides(item.def()))
                    : worn ? "" : dice.applyAsInt(t.rarity()) + Forged.die(sides);
            out.add(new Step(at.name(), t.rarity(), rolls, worn ? 0 : Ammo.every(at),
                    n == 0 ? 0 : cost(t.rarity()), n == tiers.size() - 1 && n > now, n <= now, n == now,
                    java.util.Map.of()));
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
