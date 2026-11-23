package com.larsons.game.world;

import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Wardrobe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the clothes and the armour she wears do for her: the special effects
 * a piece of your own gives at its tier - what it was made with in the
 * Tailor ({@link Forged#gear}, tier by tier) - more health, mana or stamina; each filling back faster; some of
 * every blow she lands drawn back as health, mana or stamina; a higher jump;
 * a second jump in the air; a shorter wait between heavy attacks; more speed.
 *
 * <p>Everything worn counts, whatever it is worn under: a necklace under a
 * gorget, a shirt under a gambeson ({@link Wardrobe#get(Slot, boolean)}) -
 * covered, it is still worn. What several pieces give adds up ({@link
 * #of}); the double jump is had or not.
 */
public final class Gear {

    private Gear() {}

    /**
     * A special effect a forged piece can have, and how much of it: an
     * amount (health, a second, a share, a percent) the Forge sets between
     * {@code min} and {@code max} by {@code step}, from {@code fallback}.
     */
    public enum Effect {
        HEALTH("health", "Health boost", "+%s max health", 25, 5, 200, 5),
        MANA("mana", "Mana boost", "+%s max mana", 25, 5, 200, 5),
        STAMINA("stamina", "Stamina boost", "+%s max stamina", 25, 5, 200, 5),
        HEALTH_REGEN("health_regen", "Health regen", "+%s health a second", 1, 0.5, 20, 0.5),
        MANA_REGEN("mana_regen", "Mana regen", "+%s mana a second", 2, 0.5, 20, 0.5),
        STAMINA_REGEN("stamina_regen", "Stamina regen", "+%s stamina a second", 5, 1, 50, 1),
        HEALTH_ABSORB("health_absorb", "Health absorb", "%s%% of damage dealt back as health", 5, 1, 50, 1),
        MANA_ABSORB("mana_absorb", "Mana absorb", "%s%% of damage dealt back as mana", 10, 1, 100, 1),
        STAMINA_ABSORB("stamina_absorb", "Stamina absorb", "%s%% of damage dealt back as stamina", 10, 1, 100, 1),
        JUMP("jump", "Jump height", "+%s%% jump height", 25, 5, 150, 5),
        DOUBLE_JUMP("double_jump", "Double jump", "A second jump in the air", 1, 1, 1, 1),
        HEAVY_COOLDOWN("heavy_cooldown", "Heavy attack cooldown", "-%s%% wait between heavy attacks", 25, 5, 90, 5),
        SPEED("speed", "Speed boost", "+%s%% speed", 10, 5, 100, 5),
        /** More slots in her inventory while it is worn: a backpack's (only packs are made with it). */
        CARRY("carry", "Carry space", "+%s inventory slots", 10, 1, 100, 1);

        private final String key, label, said;
        private final double fallback, min, max, step;

        Effect(String key, String label, String said, double fallback, double min, double max, double step) {
            this.key = key;
            this.label = label;
            this.said = said;
            this.fallback = fallback;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        public String key() { return key; }

        public String label() { return label; }

        /** What a piece's amount of it comes to, in a line: "+25 max health", "A second jump in the air". */
        public String said(double amount) {
            return said.contains("%s") ? String.format(Locale.ROOT, said, trim(amount)) : said;
        }

        /** Its amount, short, for the Forge's - / +: "+25", "+2/s", "10%", "-25%", "On". */
        public String amount(double amount) {
            if (onOff()) return "On";
            String n = trim(amount);
            return switch (this) {
                case HEALTH, MANA, STAMINA -> "+" + n;
                case HEALTH_REGEN, MANA_REGEN, STAMINA_REGEN -> "+" + n + "/s";
                case HEALTH_ABSORB, MANA_ABSORB, STAMINA_ABSORB -> n + "%";
                case HEAVY_COOLDOWN -> "-" + n + "%";
                case CARRY -> "+" + n;
                default -> "+" + n + "%";
            };
        }

        /** What it is of, in a word or two after its amount: "+25 health", "-25% heavy wait". */
        public String noun() {
            return switch (this) {
                case HEALTH, HEALTH_REGEN -> "health";
                case MANA, MANA_REGEN -> "mana";
                case STAMINA, STAMINA_REGEN -> "stamina";
                case HEALTH_ABSORB -> "health from blows";
                case MANA_ABSORB -> "mana from blows";
                case STAMINA_ABSORB -> "stamina from blows";
                case JUMP -> "jump";
                case DOUBLE_JUMP -> "double jump";
                case HEAVY_COOLDOWN -> "heavy wait";
                case SPEED -> "speed";
                case CARRY -> "slots";
            };
        }

        /** The amount a piece is forged with at first. */
        public double fallback() { return fallback; }

        public double min() { return min; }

        public double max() { return max; }

        public double step() { return step; }

        /** Whether it is had or not, with no amount to set. */
        public boolean onOff() { return min == max; }

        /** {@code v} kept between its least and its most. */
        public double clamp(double v) {
            return Math.max(min, Math.min(max, v));
        }

        /** Its colour in a tooltip and the Forge: the health's red, the mana's blue, the stamina's green, the rest gold. */
        public int rgb() {
            return switch (this) {
                case HEALTH, HEALTH_REGEN, HEALTH_ABSORB -> 0xE8605A;
                case MANA, MANA_REGEN, MANA_ABSORB -> 0x6E9BFF;
                case STAMINA, STAMINA_REGEN, STAMINA_ABSORB -> 0x8CD25A;
                default -> 0xF0C850;
            };
        }

        public static Effect byKey(String key) {
            for (Effect e : values()) if (e.key.equalsIgnoreCase(key)) return e;
            return null;
        }
    }

    /**
     * What everything worn comes to: each effect's amounts added up (the
     * double jump 1 when any piece has it), and the pieces that gave them.
     */
    public record Totals(Map<Effect, Double> amounts, List<String> from) {

        public static final Totals NONE = new Totals(Map.of(), List.of());

        public Totals {
            EnumMap<Effect, Double> a = new EnumMap<>(Effect.class);
            if (amounts != null) a.putAll(amounts);
            amounts = Collections.unmodifiableMap(a);
            from = from == null ? List.of() : List.copyOf(from);
        }

        /** How much of {@code e} (0: none). */
        public double get(Effect e) {
            return amounts.getOrDefault(e, 0.0);
        }

        public boolean has(Effect e) {
            return get(e) > 0;
        }

        /** {@code e}'s percent as a share: 25 % is 0.25. */
        public double share(Effect e) {
            return get(e) / 100.0;
        }

        /** Jump height, times: +25 % is 1.25. */
        public double jumpTimes() {
            return 1 + share(Effect.JUMP);
        }

        /** Speed, times. */
        public double speedTimes() {
            return 1 + share(Effect.SPEED);
        }

        /** The heavy attack's wait, times: -25 % is 0.75 (never below a tenth). */
        public double heavyTimes() {
            return Math.max(0.1, 1 - share(Effect.HEAVY_COOLDOWN));
        }

        public boolean isEmpty() {
            return amounts.isEmpty();
        }

        /** Each effect in a line, for the HUD and the wardrobe: "Health boost +25 max health". */
        public List<String> lines() {
            List<String> out = new ArrayList<>();
            for (Map.Entry<Effect, Double> e : amounts.entrySet()) out.add(e.getKey().said(e.getValue()));
            return out;
        }
    }

    /**
     * What {@code wardrobe} wears comes to, piece by piece as the items in
     * {@code inventory} (null: as found - the folk's): every slot's armour and
     * the clothes under it alike, each at its tier - the best of it she
     * carries.
     */
    public static Totals of(Wardrobe wardrobe, Inventory inventory) {
        if (wardrobe == null) return Totals.NONE;
        EnumMap<Effect, Double> sum = new EnumMap<>(Effect.class);
        List<String> from = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            if (slot.carried()) continue;
            for (boolean armour : new boolean[]{true, false}) {
                Item item = Armour.item(wardrobe, inventory, slot, armour);
                if (item == null) continue;
                // (a pair of bracers or wrist bands on both wrists is one piece)
                if (slot == Slot.WRISTWEAR_LEFT && pairWith(wardrobe, inventory, item, armour)) continue;
                Map<Effect, Double> g = of(item);
                if (g.isEmpty()) continue;
                for (Map.Entry<Effect, Double> e : g.entrySet()) {
                    double v = e.getKey().onOff() ? 1 : e.getValue();
                    sum.merge(e.getKey(), v, e.getKey().onOff() ? Math::max : Double::sum);
                }
                from.add(item.name());
            }
        }
        return new Totals(sum, from);
    }

    /**
     * What one piece gives: a piece of your own (made in the Tailor) what it
     * was made with at its tier; one the game comes with nothing - but a
     * pack its room ({@link #STOCK_PACK_SLOTS} slots).
     */
    public static Map<Effect, Double> of(Item item) {
        if (item == null || !item.def().isWearable()) return Map.of();
        if (item.forged() != null) return item.forged().gear();
        if (isPack(item.def())) return Map.of(Effect.CARRY, (double) STOCK_PACK_SLOTS);
        return Map.of();
    }

    /** The inventory slots the game's own leather backpack adds while worn. */
    public static final int STOCK_PACK_SLOTS = 5;

    /** Whether {@code d} is a pack worn on the back (what {@link Effect#CARRY} is for). */
    public static boolean isPack(ItemDef d) {
        return d != null && d.id().contains("backpack");
    }

    /** {@code g} with every amount kept within its effect's least and most, and none at 0. */
    public static Map<Effect, Double> clean(Map<Effect, Double> g) {
        EnumMap<Effect, Double> out = new EnumMap<>(Effect.class);
        if (g != null) {
            for (Map.Entry<Effect, Double> e : g.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) continue;
                out.put(e.getKey(), e.getKey().onOff() ? 1.0 : e.getKey().clamp(e.getValue()));
            }
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /** What {@code g} gives, in short: "+20 health, +1/s stamina" ("" for nothing). */
    public static String shortText(Map<Effect, Double> g) {
        List<String> out = new ArrayList<>();
        if (g != null) for (Map.Entry<Effect, Double> e : g.entrySet()) out.add(shortly(e.getKey(), e.getValue()));
        return String.join(", ", out);
    }

    private static boolean pairWith(Wardrobe wardrobe, Inventory inventory, Item left, boolean armour) {
        Item right = Armour.item(wardrobe, inventory, Slot.WRISTWEAR, armour);
        if (right == null) return false;
        if (left.forged() != null || right.forged() != null) {
            return right.forged() != null && left.forged() != null && right.forged().id().equals(left.forged().id());
        }
        return right.def() == left.def();
    }

    /** {@code e} at {@code amount}, short: "+20 health", "+1/s stamina", "10% mana from blows", "Double jump". */
    public static String shortly(Effect e, double amount) {
        return e.onOff() ? e.label() : e.amount(amount) + " " + e.noun();
    }

    static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
