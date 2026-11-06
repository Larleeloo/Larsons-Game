package com.larsons.game.world;

import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.fallback.FallbackSprites;

import java.util.List;

/**
 * What she carries for her weapons and her cards, stacked in the inventory
 * as the pickings are ({@link Pickings}): each weapon's <b>ammunition</b>
 * ({@link Ammo}) - one used up every so many swings, shots or spells - the
 * <b>upgrade ingot</b> a weapon's next tier costs some of ({@link
 * Upgrades}), the <b>reshuffle scroll</b> that shuffles her used cards back
 * into her deck ({@link Deck}), and <b>coins</b>, which go straight into her
 * purse when picked up ({@link Player#coins}).
 *
 * <pre>
 *   whetstone       swords, daggers        stack 40
 *   iron_wedges     battle axes            stack 40
 *   mace_studs      maces                  stack 40
 *   small_crystal   wands                  stack 40
 *   large_crystal   staffs                 stack 40
 *   arrow_bundle    longbows               stack 40
 *   bolt_bundle     crossbows              stack 40
 *   upgrade_ingot   a weapon's next tier   stack 20
 *   reshuffle_scroll  the cards            stack 10
 *   coins           the purse              (never carried)
 * </pre>
 *
 * <p>Each is drawn, lying about and in the inventory, as one of the turning
 * pickups 3D-Modeling's {@code items/item_spin_sprites.py} builds for them,
 * painted its own colour.
 */
public final class Supplies {

    /** How many of an ammunition go in one inventory slot. */
    public static final int AMMO_STACK = 40;

    /** The pickups they are drawn as ({@code assets/sprites/items/<pickup>/}). */
    public static final String P_WHETSTONE = "supply_whetstone", P_WEDGE = "supply_wedge", P_STUDS = "supply_studs",
            P_CRYSTAL = "supply_crystal", P_ARROWS = "supply_arrows", P_BOLTS = "supply_bolts",
            P_INGOT = "supply_ingot", P_SCROLL = "supply_scroll", P_COINS = "supply_coins";

    public static final ItemDef WHETSTONE = supply("whetstone", "Whetstone", "Ammunition", AMMO_STACK, P_WHETSTONE,
            "stone", 0x8C9098);
    public static final ItemDef IRON_WEDGES = supply("iron_wedges", "Iron wedges", "Ammunition", AMMO_STACK, P_WEDGE,
            "iron", 0x6E7480);
    public static final ItemDef MACE_STUDS = supply("mace_studs", "Mace studs", "Ammunition", AMMO_STACK, P_STUDS,
            "metal", 0xB08A4A);
    public static final ItemDef SMALL_CRYSTAL = supply("small_crystal", "Small crystal", "Ammunition", AMMO_STACK,
            P_CRYSTAL, "crystal", 0x6FB8FF);
    public static final ItemDef LARGE_CRYSTAL = supply("large_crystal", "Large crystal", "Ammunition", AMMO_STACK,
            P_CRYSTAL, "crystal", 0xC86FFF);
    public static final ItemDef ARROW_BUNDLE = supply("arrow_bundle", "Arrow bundle", "Ammunition", AMMO_STACK,
            P_ARROWS, "fletching", 0xD8283C);
    public static final ItemDef BOLT_BUNDLE = supply("bolt_bundle", "Bolt bundle", "Ammunition", AMMO_STACK, P_BOLTS,
            "fletching", 0x2F6FD8);
    public static final ItemDef UPGRADE_INGOT = supply("upgrade_ingot", "Upgrade ingot", "Upgrade", 20, P_INGOT,
            "metal", 0xE8B830);
    public static final ItemDef RESHUFFLE_SCROLL = supply("reshuffle_scroll", "Reshuffle scroll", "Cards", 10,
            P_SCROLL, "ribbon", 0x9B59D0);
    public static final ItemDef COINS = supply("coins", "Coins", "Coins", 999, P_COINS, "gold", 0xF0C030);

    /** The ammunition, in the order the chart's weapons need them. */
    public static final List<ItemDef> AMMO = List.of(WHETSTONE, IRON_WEDGES, MACE_STUDS, SMALL_CRYSTAL, LARGE_CRYSTAL,
            ARROW_BUNDLE, BOLT_BUNDLE);

    public static final List<ItemDef> ALL = List.of(WHETSTONE, IRON_WEDGES, MACE_STUDS, SMALL_CRYSTAL, LARGE_CRYSTAL,
            ARROW_BUNDLE, BOLT_BUNDLE, UPGRADE_INGOT, RESHUFFLE_SCROLL, COINS);

    private Supplies() {}

    private static ItemDef supply(String id, String name, String kind, int stack, String pickup, String part, int rgb) {
        Food f = new Food(kind, 0, 0, 0, stack, pickup, part, rgb);
        return new ItemDef(id, name, null, id.equals("large_crystal") ? 0.5 : 0.4,
                () -> FallbackSprites.wearableIcon(rgb), Stance.SWORD, null, f);
    }

    /** The supply called {@code id}, or null. */
    public static ItemDef byId(String id) {
        for (ItemDef d : ALL) if (d.id().equals(id)) return d;
        return null;
    }

    /** Whether {@code def} is one of these. */
    public static boolean is(ItemDef def) {
        return def != null && ALL.contains(def);
    }

    /** Whether {@code def} is a weapon's ammunition. */
    public static boolean isAmmo(ItemDef def) {
        return def != null && AMMO.contains(def);
    }
}
