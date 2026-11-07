package com.larsons.game.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What the cards she has played are doing for her now: each a kind of help
 * ({@link Kind}), how much, for how long, and - for those that act on what
 * she hits - how many more hits it has. Fighting, moving, the stealth and
 * the HUD ask this what they come to; it ticks the times down.
 */
public final class Buffs {

    /**
     * The kinds of help. Times-kinds multiply what they change (two at once
     * multiply together); plus-kinds add.
     */
    public enum Kind {
        /** Every blow and shot's damage, times. */
        DAMAGE("damage", true, "Damage"),
        /** A spell's damage (a wand's or a staff's), times. */
        SPELL_DAMAGE("spell_damage", true, "Spell damage"),
        /** A swing's damage, times. */
        MELEE_DAMAGE("melee_damage", true, "Melee damage"),
        /** An arrow's or a bolt's damage, times. */
        RANGED_DAMAGE("ranged_damage", true, "Ranged damage"),
        /** A spell's mana, times (0.5: half). */
        MANA_COST("mana_cost", true, "Mana cost"),
        /** Stamina spent running, climbing, swimming, dodging, times. */
        STAMINA_COST("stamina_cost", true, "Stamina cost"),
        /** How fast she goes, times. */
        SPEED("speed", true, "Speed"),
        /** Damage she takes, times (0.5: half). */
        DEFENSE("defense", true, "Damage taken"),
        /** How easily she is noticed ({@link Stealth}), times. */
        STEALTH("stealth", true, "Noticed"),
        /** How high she jumps, times. */
        JUMP("jump", true, "Jump"),
        /** How long her breath lasts under water, times. */
        BREATH("breath", true, "Breath"),
        /** Coins picked up and paid for what she sells, times. */
        COINS("coins", true, "Coins"),
        /** Experience earned, times. */
        XP("xp", true, "Experience"),
        /** More dice every roll. */
        EXTRA_DICE("extra_dice", false, "Extra dice"),
        /** Health back a second. */
        REGEN_HEALTH("regen_health", false, "Health a second"),
        /** Mana back a second. */
        REGEN_MANA("regen_mana", false, "Mana a second"),
        /** Stamina back a second. */
        REGEN_STAMINA("regen_stamina", false, "Stamina a second"),
        /** Of the damage she deals, this share back as health. */
        LIFESTEAL("lifesteal", false, "Life drain"),
        /** Of a blow she takes, this share dealt back to whatever gave it. */
        THORNS("thorns", false, "Thorns"),
        /** What she hits stands frozen this many seconds (only: which blows). */
        FREEZE("freeze", false, "Freeze"),
        /** What she hits is turned into an animal for this many seconds (charges: how many). */
        TRANSFIGURE("transfigure", false, "Transfigure"),
        /** What she hits is poisoned for this share of the blow again, over ten seconds. */
        POISON("poison", false, "Poison"),
        /** No ammunition is used. */
        AMMO_FREE("ammo_free", false, "No ammo used"),
        /** The next blows (charges) land as sneak attacks wherever she is. */
        SNEAK("sneak", false, "Sneak attacks"),
        /** Blows that would hurt her are soaked up to this much health. */
        WARD("ward", false, "Ward");

        private final String key, label;
        private final boolean times;

        Kind(String key, boolean times, String label) {
            this.key = key;
            this.times = times;
            this.label = label;
        }

        public String key() { return key; }

        public String label() { return label; }

        /** Whether it multiplies (else it adds). */
        public boolean times() { return times; }

        public static Kind byKey(String key) {
            for (Kind k : values()) if (k.key.equalsIgnoreCase(key)) return k;
            return null;
        }
    }

    /** Which blows a help acts on. */
    public enum Only {
        ANY, MELEE, RANGED, SPELL;

        public static Only byKey(String key) {
            if (key == null) return ANY;
            for (Only o : values()) if (o.name().equalsIgnoreCase(key)) return o;
            return ANY;
        }
    }

    /**
     * One help under way: from which card, what, how much, the seconds left
     * (infinite: until its charges are spent), and its charges (-1: none).
     */
    public static final class Active {
        public final String source;
        public final Kind kind;
        public final double value;
        public final Only only;
        double left;
        int charges;
        /** A ward's health still to soak up. */
        double ward;

        public Active(String source, Kind kind, double value, double seconds, int charges, Only only) {
            this.source = source;
            this.kind = kind;
            this.value = value;
            this.left = seconds <= 0 ? Double.POSITIVE_INFINITY : seconds;
            this.charges = charges <= 0 ? -1 : charges;
            this.only = only == null ? Only.ANY : only;
            this.ward = kind == Kind.WARD ? value : 0;
        }

        public double left() { return left; }

        public int charges() { return charges; }

        /** "Damage ×1.5 · 4:12" for the HUD. */
        public String describe() {
            StringBuilder s = new StringBuilder(kind.label());
            if (kind.times()) s.append(" ×").append(trim(value));
            else if (kind == Kind.FREEZE || kind == Kind.TRANSFIGURE) s.append(' ').append(trim(value)).append(" s");
            else if (kind == Kind.LIFESTEAL || kind == Kind.THORNS || kind == Kind.POISON) {
                s.append(' ').append(Math.round(value * 100)).append('%');
            } else if (kind != Kind.AMMO_FREE && kind != Kind.SNEAK) s.append(" +").append(trim(value));
            if (only != Only.ANY) s.append(" (").append(only.name().toLowerCase(Locale.ROOT)).append(')');
            if (kind == Kind.WARD) s.append(" (").append(Math.round(ward)).append(" left)");
            if (charges > 0) s.append(" · ").append(charges).append(charges == 1 ? " hit" : " hits");
            if (left < Double.POSITIVE_INFINITY) {
                int t = (int) Math.ceil(left);
                s.append(" · ").append(t / 60).append(':').append(String.format(Locale.ROOT, "%02d", t % 60));
            }
            return s.toString();
        }
    }

    private final List<Active> active = new ArrayList<>();

    /** Start {@code a}. */
    public void add(Active a) {
        active.add(a);
    }

    public List<Active> active() {
        return Collections.unmodifiableList(active);
    }

    /** Time passes: the helps run out. */
    public void tick(double dt) {
        for (Active a : active) a.left -= dt;
        active.removeIf(a -> a.left <= 0 || a.charges == 0);
    }

    public void clear() {
        active.clear();
    }

    /** What the times-kind {@code k} comes to now (1: nothing). */
    public double times(Kind k) {
        return times(k, Only.ANY);
    }

    /** {@code k} for blows of {@code what}. */
    public double times(Kind k, Only what) {
        double v = 1;
        for (Active a : active) if (a.kind == k && applies(a, what)) v *= a.value;
        return v;
    }

    /** What the plus-kind {@code k} comes to now (0: nothing). */
    public double plus(Kind k) {
        return plus(k, Only.ANY);
    }

    public double plus(Kind k, Only what) {
        double v = 0;
        for (Active a : active) if (a.kind == k && applies(a, what)) v += a.value;
        return v;
    }

    public boolean has(Kind k) {
        for (Active a : active) if (a.kind == k) return true;
        return false;
    }

    /**
     * A blow of {@code what} lands with a charged help of {@code k} (Freeze,
     * Transfigure, Sneak): its value, one charge used - or 0 when none acts on it.
     */
    public double use(Kind k, Only what) {
        for (Active a : active) {
            if (a.kind != k || !applies(a, what)) continue;
            if (a.charges > 0) a.charges--;
            return a.value;
        }
        return 0;
    }

    /** A ward soaks up {@code damage}: what is left of it. */
    public double soak(double damage) {
        for (Active a : active) {
            if (a.kind != Kind.WARD || damage <= 0) continue;
            double took = Math.min(damage, a.ward);
            a.ward -= took;
            damage -= took;
            if (a.ward <= 0) a.charges = 0;
        }
        return damage;
    }

    private static boolean applies(Active a, Only what) {
        return a.only == Only.ANY || what == Only.ANY || a.only == what;
    }

    /** How easily she is noticed ({@link Stealth}), times. */
    public double stealth() {
        return times(Kind.STEALTH);
    }

    static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.2f", d)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
