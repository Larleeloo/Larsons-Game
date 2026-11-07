package com.larsons.game.world;

import java.util.HashMap;
import java.util.Map;

/**
 * Each weapon's ammunition ({@link Supplies}), one used up every so many
 * swings, shots or spells:
 *
 * <pre>
 *   sword, dagger       a whetstone
 *   battle axe          iron wedges
 *   mace                mace studs
 *   wand                a small crystal
 *   crystal staff       a large crystal
 *   longbow             an arrow bundle
 *   crossbow            a bolt bundle
 * </pre>
 *
 * <p>How many uses one lasts goes with the weapon's tier: {@value #EVERY}
 * at a chart weapon's first, {@value #EVERY_STEP} more each tier up; a forged
 * weapon's are its own, tier by tier ({@link Forged#ammoEvery}). When the one
 * in use is spent the next is taken from her inventory; with none left a
 * bow, a crossbow, a wand or a staff cannot shoot or cast, and a blade, an
 * axe or a mace is <b>dull</b>: half the damage ({@link #DULL}). A card may
 * spare it ({@link Buffs.Kind#AMMO_FREE}); a level may do without it
 * altogether (its {@code ammo} setting, Creative Mode's Level tool).
 */
public final class Ammo {

    /** Uses one lasts at a chart weapon's first tier. */
    public static final int EVERY = 12;
    /** ... and this many more a tier up. */
    public static final int EVERY_STEP = 4;
    /** A dull weapon's damage, times. */
    public static final double DULL = 0.5;

    /** How a swing, a shot or a spell went for ammunition. */
    public enum Use {
        /** It needs none (or none is kept here). */
        FREE,
        /** One in use had a use left. */
        LOADED,
        /** The one in use was spent: another taken from the inventory. */
        RELOADED,
        /** None left, and a blade: half the damage. */
        DULL,
        /** None left, and a bow, a crossbow, a wand or a staff: no shot. */
        EMPTY
    }

    /** Uses left of the one in use, by ammunition (its id). */
    private final Map<String, Integer> loaded = new HashMap<>();
    private boolean enabled;

    /** Whether ammunition is used up here (a level's setting; off for a world made without one). */
    public boolean enabled() { return enabled; }

    public void setEnabled(boolean on) {
        enabled = on;
    }

    /** The ammunition {@code weapon} uses, or null for none (her fists, a wearable ...). */
    public static ItemDef of(ItemDef weapon) {
        if (weapon == null) return null;
        return switch (weapon.id()) {
            case "sword", "dagger" -> Supplies.WHETSTONE;
            case "battle_axe" -> Supplies.IRON_WEDGES;
            case "mace" -> Supplies.MACE_STUDS;
            case "wand" -> Supplies.SMALL_CRYSTAL;
            case "crystal_staff" -> Supplies.LARGE_CRYSTAL;
            case "longbow" -> Supplies.ARROW_BUNDLE;
            case "crossbow" -> Supplies.BOLT_BUNDLE;
            default -> null;
        };
    }

    /** Whether, with none left, {@code weapon} cannot be used at all (it shoots or casts) rather than going dull. */
    public static boolean shoots(ItemDef weapon) {
        ItemDef a = of(weapon);
        return a == Supplies.SMALL_CRYSTAL || a == Supplies.LARGE_CRYSTAL || a == Supplies.ARROW_BUNDLE
                || a == Supplies.BOLT_BUNDLE;
    }

    /** How many uses one of {@code weapon}'s ammunition lasts, at its tier. */
    public static int every(Item weapon) {
        if (weapon == null) return EVERY;
        if (weapon.forged() != null) return weapon.forged().ammoEvery();
        Tier.Row row = Tier.row(weapon.def().id());
        if (row == null || weapon.tier() == null) return EVERY;
        return EVERY + EVERY_STEP * Math.max(0, row.tiers().indexOf(weapon.tier()));
    }

    /** Uses left of the one in use for {@code weapon} (0: none in use). */
    public int left(Item weapon) {
        ItemDef a = weapon == null ? null : of(weapon.def());
        return a == null ? 0 : loaded.getOrDefault(a.id(), 0);
    }

    /** How many more of {@code weapon}'s ammunition she carries, and the one in use's uses. */
    public int carried(Item weapon, Inventory inv) {
        ItemDef a = weapon == null ? null : of(weapon.def());
        return a == null ? 0 : inv.total(Item.of(a));
    }

    /**
     * A swing, a shot or a spell of {@code weapon}: one use of its ammunition
     * from {@code inv} ({@code free}: a card spares it).
     */
    public Use use(Item weapon, Inventory inv, boolean free) {
        ItemDef a = weapon == null ? null : of(weapon.def());
        if (!enabled || a == null || free) return Use.FREE;
        int left = loaded.getOrDefault(a.id(), 0);
        if (left > 0) {
            loaded.put(a.id(), left - 1);
            return Use.LOADED;
        }
        int slot = inv.indexOf(a);
        if (slot < 0) return shoots(weapon.def()) ? Use.EMPTY : Use.DULL;
        inv.takeOne(slot);
        loaded.put(a.id(), every(weapon) - 1);
        return Use.RELOADED;
    }

    /** A use given back (a swing cut short before it landed). */
    public void refund(Item weapon) {
        ItemDef a = weapon == null ? null : of(weapon.def());
        if (a != null && enabled && loaded.containsKey(a.id())) loaded.merge(a.id(), 1, Integer::sum);
    }

    /** Every weapon's in use put away (a new game). */
    public void clear() {
        loaded.clear();
    }

    /** What its ammunition is called, lower case and plural where it reads so: "arrows", "a whetstone". */
    public static String said(ItemDef ammo) {
        if (ammo == null) return "";
        return switch (ammo.id()) {
            case "arrow_bundle" -> "arrows";
            case "bolt_bundle" -> "bolts";
            case "small_crystal" -> "small crystals";
            case "large_crystal" -> "large crystals";
            case "iron_wedges" -> "wedges";
            case "mace_studs" -> "studs";
            default -> "whetstones";
        };
    }
}
