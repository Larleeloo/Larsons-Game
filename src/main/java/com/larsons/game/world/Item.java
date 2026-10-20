package com.larsons.game.world;

import java.util.Objects;

/**
 * One item: what kind of thing it is ({@link ItemDef}), its {@link Tier}
 * where it is a weapon of Larson's chart, how rare it is, and the rare
 * {@link Border} it may have dropped with. What a slot of the {@link
 * Inventory} holds and what lies in the world.
 *
 * <p>Two items that agree on all four are the same item - two Basic Axes are
 * interchangeable. A weapon's rarity is its tier's; anything without tiers
 * carries its own - a wearable is found as the rarity it drops as ({@link
 * Wearable#rarity}: a silver necklace is Rare), anything else as Common.
 *
 * <p>{@link #key()} writes it as one word for scripts and save files -
 * {@code battle_axe}, {@code battle_axe:crystal}, {@code sword:perfect:venom},
 * {@code silver_necklace}, {@code dye_pink}, {@code belt:epic} - and {@link
 * #parse} reads it back.
 *
 * @param def    what kind of thing it is
 * @param tier   its tier, or null (an item with no tiers)
 * @param rarity how rare it is (a weapon's is its tier's)
 * @param border its rare border, or null
 */
public record Item(ItemDef def, Tier tier, Rarity rarity, Border border) {

    public Item {
        Objects.requireNonNull(def, "def");
        if (tier != null) rarity = tier.rarity();
        if (rarity == null) rarity = Rarity.COMMON;
    }

    /**
     * {@code def} as it is found lying about: a weapon in its lowest tier, a
     * wearable of its own rarity, anything else Common.
     */
    public static Item of(ItemDef def) {
        Tier.Row row = Tier.row(def.id());
        return new Item(def, row == null ? null : row.lowest(), foundAs(def), null);
    }

    /** The rarity an item with no tiers is found as: a wearable's own, else Common. */
    private static Rarity foundAs(ItemDef def) {
        return def.isWearable() ? def.wearable().rarity() : Rarity.COMMON;
    }

    /** {@code def} in {@code tier}. */
    public static Item of(ItemDef def, Tier tier) {
        return new Item(def, tier, null, null);
    }

    /** An item with no tiers, of {@code rarity}. */
    public static Item of(ItemDef def, Rarity rarity) {
        return new Item(def, null, rarity, null);
    }

    /** This item with {@code border} (null: none). */
    public Item withBorder(Border border) {
        return new Item(def, tier, rarity, border);
    }

    /** The kind's id: the folder its sprites are in, the sounds it makes. */
    public String id() { return def.id(); }

    /** What it is called: the chart's name ("Crystal Axe"), else the kind's ("Belt"). */
    public String name() {
        String n = Tier.name(def.id(), tier);
        if (n != null) return n;
        return tier == null ? def.name() : tier.label() + " " + def.name();
    }

    /** Its name with its border, if it has one: "Venom Crystal Axe". */
    public String fullName() {
        return border == null ? name() : border.label() + " " + name();
    }

    /** Whether it is {@code def}, whatever its tier. */
    public boolean is(ItemDef d) {
        return def == d;
    }

    /** One word for it: {@code id[:tier|:rarity][:border]} (see the class comment). */
    public String key() {
        StringBuilder s = new StringBuilder(def.id());
        Tier.Row row = Tier.row(def.id());
        if (tier != null) {
            if (row == null || tier != row.lowest()) s.append(':').append(tier.key());
        } else if (rarity != foundAs(def)) {
            s.append(':').append(rarity.key());
        }
        if (border != null) s.append(':').append(border.key());
        return s.toString();
    }

    /**
     * An item from its {@link #key()} - or from a script's looser words:
     * {@code battle_axe crystal venom}, {@code sword:mythic} - or null when
     * the kind is not known. Parts it does not know are ignored; a weapon
     * without a tier is in its lowest.
     */
    public static Item parse(String text) {
        if (text == null || text.isBlank()) return null;
        String[] parts = text.trim().split("[:\\s]+");
        ItemDef def = ItemDef.byId(parts[0]);
        if (def == null) return null;
        Item item = of(def);
        Tier.Row row = Tier.row(def.id());
        for (int i = 1; i < parts.length; i++) {
            Tier t = Tier.byKey(parts[i]);
            Rarity r = Rarity.byKey(parts[i]);
            Border b = Border.byKey(parts[i]);
            if (t != null && row != null && row.names().containsKey(t)) {
                item = new Item(def, t, null, item.border);
            } else if (r != null && row == null) {
                item = new Item(def, null, r, item.border);
            } else if (r != null) {
                // a weapon asked for by rarity: its tier of that rarity, if it has one
                for (Tier tt : row.tiers()) if (tt.rarity() == r) item = new Item(def, tt, null, item.border);
            } else if (b != null) {
                item = item.withBorder(b);
            }
        }
        return item;
    }

    @Override
    public String toString() {
        return key();
    }
}
