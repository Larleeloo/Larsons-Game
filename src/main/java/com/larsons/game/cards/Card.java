package com.larsons.game.cards;

import com.larsons.game.world.Buffs;
import com.larsons.game.world.Rarity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One of the cards ({@link CardBook}): its name, whose class it is (null: any
 * class's), how rare, what it says, what it does ({@link Effect}s), what a
 * shop asks for it and where it is had - in a class's starting deck, from
 * its perk tree, or bought at a shop.
 */
public record Card(String id, String name, String hero, Rarity rarity, String text, List<Effect> effects, int price,
                   Source source) {

    /** Where a card is had. */
    public enum Source {
        START, PERK, SHOP;

        public String key() { return name().toLowerCase(Locale.ROOT); }

        public static Source byKey(String k) {
            for (Source s : values()) if (s.key().equalsIgnoreCase(k)) return s;
            return SHOP;
        }
    }

    /** The things a card does at once, rather than for a while ({@link Buffs.Kind}s). */
    public enum Instant {
        HEAL("heal", "Health"), MANA("mana", "Mana"), STAMINA("stamina", "Stamina"),
        MANA_TO_HEALTH("mana_to_health", "All mana into health"), HEALTH_TO_MANA("health_to_mana", "Health into mana"),
        DRAW("draw", "Draw cards"), COINS_NOW("coins_now", "Coins"), XP_NOW("xp_now", "Experience"),
        BREATH_NOW("breath_now", "Breath back");

        private final String key, label;

        Instant(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() { return key; }

        public String label() { return label; }

        public static Instant byKey(String k) {
            for (Instant i : values()) if (i.key.equalsIgnoreCase(k)) return i;
            return null;
        }
    }

    /**
     * One thing a card does: a help of {@code kind} ({@link Buffs.Kind}'s key,
     * or an {@link Instant}'s), how much, for how many seconds (0: until its
     * charges are spent - or, on a perk, for good), how many hits (0: none),
     * and on which blows.
     */
    public record Effect(String kind, double value, double seconds, int charges, Buffs.Only only) {
        public Effect {
            kind = kind == null ? "heal" : kind.toLowerCase(Locale.ROOT);
            seconds = Math.max(0, seconds);
            charges = Math.max(0, charges);
            if (only == null) only = Buffs.Only.ANY;
        }

        /** Its help, or null for one that happens at once. */
        public Buffs.Kind buff() { return Buffs.Kind.byKey(kind); }

        public Instant instant() { return Instant.byKey(kind); }

        /** Every kind there is, the helps then the at-once ones (the Cards tool's list). */
        public static List<String> kinds() {
            List<String> out = new ArrayList<>();
            for (Buffs.Kind k : Buffs.Kind.values()) out.add(k.key());
            for (Instant i : Instant.values()) out.add(i.key());
            return out;
        }

        /** What a kind is called. */
        public static String label(String kind) {
            Buffs.Kind b = Buffs.Kind.byKey(kind);
            if (b != null) return b.label();
            Instant i = Instant.byKey(kind);
            return i != null ? i.label() : kind;
        }

        public Effect withKind(String k) { return new Effect(k, value, seconds, charges, only); }

        public Effect withValue(double v) { return new Effect(kind, v, seconds, charges, only); }

        public Effect withSeconds(double s) { return new Effect(kind, value, s, charges, only); }

        public Effect withCharges(int c) { return new Effect(kind, value, seconds, c, only); }

        public Effect withOnly(Buffs.Only o) { return new Effect(kind, value, seconds, charges, o); }

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", kind);
            if (value != 0) m.put("value", value);
            if (seconds > 0) m.put("seconds", seconds);
            if (charges > 0) m.put("charges", (double) charges);
            if (only != Buffs.Only.ANY) m.put("only", only.name().toLowerCase(Locale.ROOT));
            return m;
        }
    }

    public Card {
        name = name == null || name.isBlank() ? "A card" : name.strip();
        if (rarity == null) rarity = Rarity.COMMON;
        text = text == null ? "" : text.strip();
        effects = effects == null ? List.of() : List.copyOf(effects);
        price = Math.max(0, price);
        if (source == null) source = Source.SHOP;
    }

    /** What a shop asks for a card of {@code r}, unless it says otherwise. */
    public static int price(Rarity r) {
        return switch (r) {
            case COMMON -> 30;
            case UNCOMMON -> 50;
            case RARE -> 80;
            case EPIC -> 120;
            case LEGENDARY -> 200;
            case MYTHIC -> 320;
        };
    }

    public Card withName(String n) { return new Card(id, n, hero, rarity, text, effects, price, source); }

    public Card withHero(String h) { return new Card(id, name, h, rarity, text, effects, price, source); }

    public Card withRarity(Rarity r) { return new Card(id, name, hero, r, text, effects, price, source); }

    public Card withText(String t) { return new Card(id, name, hero, rarity, t, effects, price, source); }

    public Card withEffects(List<Effect> e) { return new Card(id, name, hero, rarity, text, e, price, source); }

    public Card withPrice(int p) { return new Card(id, name, hero, rarity, text, effects, p, source); }

    public Card withSource(Source s) { return new Card(id, name, hero, rarity, text, effects, price, s); }
}
