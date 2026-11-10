package com.larsons.game.world;

import java.util.ArrayList;
import java.util.List;

/**
 * What is made of what, and where: at a <b>workbench</b> the makings, the
 * rods, the packs, the trinkets, the keys and the ammunition; at a
 * <b>cauldron</b> the potions (each wants an empty bottle) and the meals.
 * Scrolls and spawner eggs are never made - they are found, won and bought.
 *
 * <p>A recipe's needs are counted by kind - any sword is a sword, whatever
 * its tier - and taken from her inventory when it is made.
 */
public final class Recipes {

    /** Where a thing is made. */
    public enum Station {
        BENCH("workbench", "Workbench", "Make"), CAULDRON("cauldron", "Cauldron", "Brew");

        private final String key, label, verb;

        Station(String key, String label, String verb) {
            this.key = key;
            this.label = label;
            this.verb = verb;
        }

        public String key() { return key; }

        public String label() { return label; }

        public String verb() { return verb; }
    }

    /** So many of an item. */
    public record Part(String item, int count) {
        public ItemDef def() { return ItemDef.byId(item); }

        public String name() {
            ItemDef d = def();
            return d == null ? item : d.name();
        }
    }

    /**
     * A recipe: at {@code station}, {@code needs} make {@code makes} of {@code out}.
     *
     * @param seconds how long the making takes (a brew bubbles a while)
     */
    public record Recipe(String id, Station station, String out, int makes, double seconds, List<Part> needs) {

        public Item item() {
            return Item.parse(out);
        }

        public String name() {
            Item i = item();
            return i == null ? out : i.name();
        }
    }

    private static final List<Recipe> ALL = new ArrayList<>();

    private static void recipe(Station st, String out, int makes, Object... needs) {
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i + 1 < needs.length; i += 2) parts.add(new Part((String) needs[i], (Integer) needs[i + 1]));
        String id = out;
        int n = 2;
        for (boolean taken = true; taken; ) {
            taken = false;
            for (Recipe r : ALL) if (r.id().equals(id)) taken = true;
            if (taken) id = out + "_" + n++;
        }
        ALL.add(new Recipe(id, st, out, makes, st == Station.CAULDRON ? 3.0 : 1.2, List.copyOf(parts)));
    }

    static {
        Station b = Station.BENCH, c = Station.CAULDRON;
        // --- the makings ---
        recipe(b, "empty_bottle", 3, "small_crystal", 2);
        recipe(b, "parchment", 2, "deer_hide", 1);
        recipe(b, "parchment", 2, "cow_hide", 1);
        recipe(b, "ink", 1, "thornberries", 3, "empty_bottle", 1);
        recipe(b, "twine", 3, "wool", 1);
        recipe(b, "cloth", 2, "wool", 2);
        recipe(b, "leather", 2, "cow_hide", 1);
        recipe(b, "leather", 2, "deer_hide", 1);
        recipe(b, "leather", 3, "bear_pelt", 1);
        recipe(b, "iron_bar", 1, "iron_wedges", 4);
        recipe(b, "fish_hook", 2, "iron_bar", 1);
        recipe(b, "brass_gear", 1, "mace_studs", 3, "iron_bar", 1);
        recipe(b, "crystal_dust", 1, "large_crystal", 2);
        // --- ammunition, the ingot, the reshuffle scroll ---
        recipe(b, "arrow_bundle", 6, "feather", 2, "iron_bar", 1);
        recipe(b, "bolt_bundle", 6, "feather", 1, "iron_bar", 1);
        recipe(b, "whetstone", 6, "iron_bar", 1);
        recipe(b, "upgrade_ingot", 1, "iron_bar", 3, "brass_gear", 1);
        recipe(b, "reshuffle_scroll", 1, "parchment", 1, "ink", 1);
        // --- fishing rods, each made of the last ---
        recipe(b, "willow_rod", 1, "twine", 2, "fish_hook", 1);
        recipe(b, "reed_rod", 1, "willow_rod", 1, "twine", 2, "feather", 2);
        recipe(b, "brass_rod", 1, "reed_rod", 1, "brass_gear", 2, "iron_bar", 1);
        recipe(b, "clockwork_rod", 1, "brass_rod", 1, "brass_gear", 4, "upgrade_ingot", 1);
        recipe(b, "crystal_rod", 1, "clockwork_rod", 1, "crystal_dust", 4, "large_crystal", 4);
        // --- packs, each made of the last ---
        recipe(b, "satchel", 1, "leather", 3, "twine", 2);
        recipe(b, "backpack", 1, "satchel", 1, "leather", 4, "cloth", 3, "brass_gear", 1);
        recipe(b, "explorers_pack", 1, "backpack", 1, "leather", 6, "cloth", 4, "brass_gear", 3, "upgrade_ingot", 1);
        // --- keys and trinkets ---
        recipe(b, "iron_key", 1, "iron_bar", 1);
        recipe(b, "brass_key", 1, "brass_gear", 1, "iron_bar", 1);
        recipe(b, "drinking_horn", 1, "antler", 1, "leather", 1, "twine", 1);
        recipe(b, "crystal_ball", 1, "crystal_dust", 3, "large_crystal", 3, "brass_gear", 2);
        recipe(b, "crystal_shield", 1, "crystal_dust", 4, "iron_bar", 3, "leather", 2);
        // --- potions ---
        recipe(c, "health_potion", 1, "empty_bottle", 1, "wild_berries", 3, "sage", 1);
        recipe(c, "mana_potion", 1, "empty_bottle", 1, "bilberries", 3, "small_crystal", 1);
        recipe(c, "stamina_potion", 1, "empty_bottle", 1, "bilberries", 2, "sage", 2);
        recipe(c, "greater_health_potion", 1, "health_potion", 1, "sunfruit", 2, "crystal_bloom", 1);
        recipe(c, "swiftness_potion", 1, "empty_bottle", 1, "feather", 2, "sea_buckthorn", 2);
        recipe(c, "swiftness_potion", 1, "empty_bottle", 1, "feather", 2, "rabbit_pelt", 1);
        recipe(c, "might_potion", 1, "empty_bottle", 1, "bear_meat", 1, "thornberries", 3);
        recipe(c, "might_potion", 1, "empty_bottle", 1, "raw_beef", 2, "thornberries", 3);
        recipe(c, "stoneskin_potion", 1, "empty_bottle", 1, "antler", 1, "sage", 2, "large_crystal", 1);
        recipe(c, "shadow_potion", 1, "empty_bottle", 1, "ink", 1, "thornberries", 3);
        recipe(c, "gills_potion", 1, "empty_bottle", 1, "minnow", 2, "sea_buckthorn", 1);
        recipe(c, "gills_potion", 1, "empty_bottle", 1, "trout", 1, "sage", 1);
        recipe(c, "spring_potion", 1, "empty_bottle", 1, "rabbit_pelt", 1, "sunfruit", 1);
        recipe(c, "thorn_potion", 1, "empty_bottle", 1, "thornberries", 4, "emberfin", 1);
        recipe(c, "fortune_potion", 1, "empty_bottle", 1, "goldscale", 1, "sunfruit", 2, "crystal_dust", 1);
        recipe(c, "crystal_elixir", 1, "empty_bottle", 1, "crystal_bloom", 3, "crystal_dust", 2, "crystal_eel", 1);
        // --- meals ---
        recipe(c, "roast_meat", 1, "raw_beef", 1);
        recipe(c, "roast_meat", 1, "raw_pork", 1);
        recipe(c, "roast_meat", 1, "raw_mutton", 1);
        recipe(c, "roast_meat", 1, "raw_game", 2);
        recipe(c, "roast_meat", 1, "raw_fowl", 2);
        recipe(c, "grilled_fish", 1, "trout", 1);
        recipe(c, "grilled_fish", 1, "perch", 1);
        recipe(c, "grilled_fish", 1, "sea_bass", 1);
        recipe(c, "grilled_fish", 1, "minnow", 3);
        recipe(c, "grilled_fish", 2, "pike", 1);
        recipe(c, "hunters_stew", 1, "raw_venison", 1, "sage", 1, "wild_berries", 2);
        recipe(c, "hunters_stew", 1, "bear_meat", 1, "sage", 1, "wild_berries", 2);
        recipe(c, "berry_tart", 1, "wild_berries", 4, "bilberries", 2);
    }

    private Recipes() {}

    public static List<Recipe> all() { return List.copyOf(ALL); }

    /** What is made at {@code station}, in order. */
    public static List<Recipe> at(Station station) {
        return ALL.stream().filter(r -> r.station() == station).toList();
    }

    public static Recipe byId(String id) {
        for (Recipe r : ALL) if (r.id().equals(id)) return r;
        return null;
    }

    /** How many of the item called {@code id} (of any tier or rarity) {@code inv} holds. */
    public static int count(Inventory inv, String id) {
        int n = 0;
        for (int i = 0; i < inv.size(); i++) {
            Item it = inv.get(i);
            if (it != null && it.def().id().equals(id)) n += Math.max(1, inv.amount(i));
        }
        return n;
    }

    /** Take {@code n} of the item called {@code id} out of {@code inv}; whether there were that many. */
    public static boolean take(Inventory inv, String id, int n) {
        if (count(inv, id) < n) return false;
        for (int i = inv.size() - 1; i >= 0 && n > 0; i--) {
            Item it = inv.get(i);
            while (n > 0 && it != null && it.def().id().equals(id)) {
                inv.takeOne(i);
                n--;
                it = inv.get(i);
            }
        }
        return n == 0;
    }

    /** Whether {@code inv} holds all {@code r} needs. */
    public static boolean can(Recipe r, Inventory inv) {
        for (Part p : r.needs()) if (count(inv, p.item()) < p.count()) return false;
        return true;
    }

    /** How many times over {@code inv} holds what {@code r} needs. */
    public static int times(Recipe r, Inventory inv) {
        int n = Integer.MAX_VALUE;
        for (Part p : r.needs()) n = Math.min(n, count(inv, p.item()) / p.count());
        return n == Integer.MAX_VALUE ? 0 : n;
    }

    /**
     * Make {@code r} once: its needs out of {@code inv}, what it makes in -
     * returns what was made and could not be held (to lie at her feet), or
     * null when she has not all it needs.
     */
    public static List<Item> make(Recipe r, Inventory inv) {
        Item out = r.item();
        if (out == null || !can(r, inv)) return null;
        for (Part p : r.needs()) take(inv, p.item(), p.count());
        List<Item> over = new ArrayList<>();
        int left = inv.put(out, r.makes(), true);
        for (int i = 0; i < left; i++) over.add(out);
        return over;
    }
}
