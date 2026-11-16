package com.larsons.game.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A weapon's tier: what its name starts with ("Crystal Axe"), how rare it is,
 * and how it looks - the same weapon recoloured in the engine, never a new
 * model ({@link com.larsons.game.sprite.TierLook}):
 *
 * <pre>
 *   Basic     Common     exactly as rendered
 *   Sharper   Uncommon   a black handle
 *   Magic     Rare       as rendered (the crystal staff is the Magic Staff); blue projectiles
 *   Crafted   Rare       a black body, like the sharper ones
 *   Honed     Epic       a darker wood; yellow projectiles
 *   Crystal   Epic       a pink-purple blade or tip
 *   Perfect   Legendary  gold
 *   Mythic    Mythic     smoky purple
 * </pre>
 *
 * <p>Which weapon comes in which tiers, and what each is called, is Larson's
 * chart ({@link #CHART}): the battle axe is a Basic, Sharper, Crystal, Perfect
 * or Mythic Axe; the staff a Basic, Magic, Honed, Perfect or Mythic Staff;
 * the wand a Magic, Honed, Perfect or Mythic Wand.
 */
public enum Tier {
    BASIC("basic", "Basic", Rarity.COMMON, -1),
    SHARPER("sharper", "Sharper", Rarity.UNCOMMON, -1),
    MAGIC("magic", "Magic", Rarity.RARE, 0x5D86FA),
    CRAFTED("crafted", "Crafted", Rarity.RARE, -1),
    HONED("honed", "Honed", Rarity.EPIC, 0xF9C36D),
    CRYSTAL("crystal", "Crystal", Rarity.EPIC, -1),
    PERFECT("perfect", "Perfect", Rarity.LEGENDARY, -1),
    MYTHIC("mythic", "Mythic", Rarity.MYTHIC, -1);

    private final String key, label;
    private final Rarity rarity;
    private final int projectile;

    Tier(String key, String label, Rarity rarity, int projectile) {
        this.key = key;
        this.label = label;
        this.rarity = rarity;
        this.projectile = projectile;
    }

    public String key() { return key; }

    public String label() { return label; }

    public Rarity rarity() { return rarity; }

    /**
     * The colour of the projectiles a weapon of this tier shoots, as the item
     * descriptions give it ({@code 0xRRGGBB}: blue for Magic, yellow for
     * Honed), or -1 where they say nothing.
     */
    public int projectile() { return projectile; }

    public static Tier byKey(String key) {
        for (Tier t : values()) if (t.key.equalsIgnoreCase(key)) return t;
        return null;
    }

    /** One weapon's tiers, lowest first, and the name of each ("Crafted Bow"). */
    public record Row(String item, Map<Tier, String> names) {

        public List<Tier> tiers() { return new ArrayList<>(names.keySet()); }

        /** Its lowest tier: what one lying in the world at the start is. */
        public Tier lowest() { return names.keySet().iterator().next(); }
    }

    /** Larson's weapon chart, by item id. */
    public static final Map<String, Row> CHART = chart(
            tiers("battle_axe", "Axe", BASIC, SHARPER, CRYSTAL, PERFECT, MYTHIC),
            tiers("sword", "Sword", BASIC, SHARPER, CRYSTAL, PERFECT, MYTHIC),
            tiers("crystal_staff", "Staff", BASIC, MAGIC, HONED, PERFECT, MYTHIC),
            tiers("longbow", "Bow", BASIC, CRAFTED, PERFECT, MYTHIC).rename(BASIC, "Basic Longbow"),
            tiers("crossbow", "Crossbow", BASIC, CRAFTED, PERFECT, MYTHIC),
            tiers("wand", "Wand", MAGIC, HONED, PERFECT, MYTHIC),
            tiers("mace", "Mace", BASIC, SHARPER, CRYSTAL, PERFECT, MYTHIC),
            tiers("dagger", "Dagger", BASIC, SHARPER, CRYSTAL, PERFECT, MYTHIC));

    /** {@code item}'s row of the chart, or null for an item with no tiers. */
    public static Row row(String item) {
        Row r = CHART.get(item);
        return r != null ? r : item == null ? null : ArmourRows.ROWS.get(item);
    }

    /**
     * The armour's tiers ({@link Armour}): every piece climbs from the rarity
     * it is found as to Mythic - Reinforced, Riveted, Tempered, Perfect,
     * Mythic - each tier up a rarity, so more dice to block with. Upgraded
     * with ingots like a weapon ({@link Upgrades}); the lowest tier is the
     * piece as it is found, named as it is ("Iron helmet", "Tempered iron
     * helmet").
     */
    public static Map<String, Row> armour() {
        return ArmourRows.ROWS;
    }

    /** What a tier is called on armour: Reinforced, Riveted, Tempered, Perfect, Mythic (Basic: as it is). */
    public static String armourLabel(Tier t) {
        return switch (t) {
            case BASIC -> "Plain";
            case SHARPER -> "Reinforced";
            case MAGIC, CRAFTED -> "Riveted";
            case HONED, CRYSTAL -> "Tempered";
            case PERFECT -> "Perfect";
            case MYTHIC -> "Mythic";
        };
    }

    /** The armour's rows, made the first time they are asked for (the wearables are made first). */
    private static final class ArmourRows {
        static final Map<String, Row> ROWS = make();

        private static Map<String, Row> make() {
            Tier[] climb = {BASIC, SHARPER, CRAFTED, HONED, PERFECT, MYTHIC};
            Map<String, Row> out = new LinkedHashMap<>();
            for (ItemDef d : Wearables.ALL) {
                if (Armour.sides(d) <= 0) continue;
                Map<Tier, String> names = new LinkedHashMap<>();
                Rarity from = d.wearable().rarity();
                String low = d.name().substring(0, 1).toLowerCase(java.util.Locale.ROOT) + d.name().substring(1);
                for (Tier t : climb) {
                    if (t.rarity.ordinal() < from.ordinal()) continue;
                    names.put(t, names.isEmpty() ? d.name() : armourLabel(t) + " " + low);
                }
                out.put(d.id(), new Row(d.id(), java.util.Collections.unmodifiableMap(names)));
            }
            return java.util.Collections.unmodifiableMap(out);
        }
    }

    /** What {@code item} is called in {@code tier} ("Perfect Bow"), or null when it has no such tier. */
    public static String name(String item, Tier tier) {
        Row r = row(item);
        return r == null || tier == null ? null : r.names().get(tier);
    }

    private static RowBuilder tiers(String item, String noun, Tier... tiers) {
        Map<Tier, String> names = new LinkedHashMap<>();
        for (Tier t : tiers) names.put(t, t.label + " " + noun);
        return new RowBuilder(item, names);
    }

    private record RowBuilder(String item, Map<Tier, String> names) {
        RowBuilder rename(Tier t, String name) {
            names.put(t, name);
            return this;
        }
    }

    private static Map<String, Row> chart(RowBuilder... rows) {
        Map<String, Row> out = new LinkedHashMap<>();
        for (RowBuilder b : rows) out.put(b.item, new Row(b.item, java.util.Collections.unmodifiableMap(b.names)));
        return java.util.Collections.unmodifiableMap(out);
    }
}
