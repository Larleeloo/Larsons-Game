package com.larsons.game.world;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The places the makings of things are gathered: an iron vein, a copper
 * vein, a coal seam, a crystal cluster, a sand bank, a clay bank, fallen
 * logs, a patch of flax, a pine tapped for its resin. Each is a spot she
 * walks up to and works (E): it gives what its kind gives ({@link #yields},
 * a {@link Drops} pool - a copper vein now and then calamine with its ore),
 * then stands worked out - its model swapped for its spent one, the ore
 * picked off, the logs gone - until it comes back, {@link Kind#regrow}
 * seconds on (or what the level set it to).
 *
 * <p>What is gathered goes to the furnace, the bench and the cauldron
 * ({@link Recipes}); 3D-Modeling's {@code buildings/gathering.py} makes the
 * models ({@code <key>} and {@code <key>_spent}).
 */
public final class Gathering {

    /** The spots' kind ({@link Spot#kind}): a level's {@code gather} objects. */
    public static final String SPOT = "gather";
    /** A spot's state while it is worked out: the world's time it comes back. */
    public static final String BACK = "back";

    private Gathering() {}

    /** A kind of gathering spot. */
    public enum Kind {
        IRON_VEIN("iron_vein", "Iron vein", "Mine", "gather/mine", 300, "iron_ore x2-4, coal 20%"),
        COPPER_VEIN("copper_vein", "Copper vein", "Mine", "gather/mine", 300, "copper_ore x2-4, calamine x1-2 60%"),
        COAL_SEAM("coal_seam", "Coal seam", "Mine", "gather/mine", 240, "coal x3-5"),
        CRYSTAL_CLUSTER("crystal_cluster", "Crystal cluster", "Chip", "gather/chip", 420, "raw_crystal x1-3"),
        SAND_BANK("sand_bank", "Sand bank", "Dig", "gather/dig", 180, "sand x3-5"),
        CLAY_BANK("clay_bank", "Clay bank", "Dig", "gather/dig", 180, "clay x2-4, sand 25%"),
        LOG_PILE("log_pile", "Fallen logs", "Cut", "gather/chop", 240, "timber x2-4, resin 20%"),
        FLAX_PATCH("flax_patch", "Flax", "Cut", "gather/cut", 240, "flax x3-6"),
        RESIN_PINE("resin_pine", "Tapped pine", "Take resin from", "gather/tap", 200, "resin x1-3");

        private final String key, label, verb, sound, yields;
        private final double regrow;

        Kind(String key, String label, String verb, String sound, double regrow, String yields) {
            this.key = key;
            this.label = label;
            this.verb = verb;
            this.sound = sound;
            this.regrow = regrow;
            this.yields = yields;
        }

        public String key() { return key; }

        public String label() { return label; }

        /** What working it is called: "Mine", "Dig", "Cut". */
        public String verb() { return verb; }

        /** The sound of it being worked. */
        public String sound() { return sound; }

        /** How long it stands worked out, seconds, unless the level says. */
        public double regrow() { return regrow; }

        /** What one working of it gives. */
        public Drops yields() { return Drops.parse(yields); }

        /** Its model, standing ready. */
        public String model() { return key; }

        /** Its model, worked out. */
        public String spentModel() { return key + "_spent"; }

        /** What the prompt says: "Mine the iron vein", "Cut the flax". */
        public String prompt() {
            String l = label.toLowerCase(Locale.ROOT);
            return verb + " the " + l;
        }

        public static Kind byKey(String key) {
            for (Kind k : values()) if (k.key.equals(key)) return k;
            return null;
        }

        public static List<String> keys() {
            return java.util.Arrays.stream(values()).map(Kind::key).toList();
        }
    }

    /** {@code s}'s kind, or null (not a gathering spot). */
    public static Kind of(Spot s) {
        return s == null || !SPOT.equals(s.kind()) ? null : Kind.byKey(s.str("kind", Kind.IRON_VEIN.key()));
    }

    /** How long {@code s} stands worked out, seconds: what the level set it to, else its kind's. */
    public static double regrow(Spot s) {
        Kind k = of(s);
        double set = s.num("regrow", 0);
        return set > 0 ? set : k == null ? 240 : k.regrow();
    }

    /** Whether {@code s} is worked out at {@code now}. */
    public static boolean spent(Spot s, double now) {
        Object back = s.state().get(BACK);
        return back instanceof Number n && now < n.doubleValue();
    }

    /** Seconds until {@code s} comes back at {@code now} (0: ready). */
    public static double untilBack(Spot s, double now) {
        Object back = s.state().get(BACK);
        return back instanceof Number n ? Math.max(0, n.doubleValue() - now) : 0;
    }

    /**
     * Work {@code s} at {@code now}: what it gives (more by {@code s}'s
     * "yield" times), and it stands worked out - or null when it already is.
     */
    public static List<Drops.Drop> work(Spot s, double now, Random random) {
        Kind k = of(s);
        if (k == null || spent(s, now)) return null;
        List<Drops.Drop> got = new java.util.ArrayList<>();
        double more = Math.max(0.25, s.num("yield", 1));
        for (Drops.Drop d : k.yields().roll(random, null)) {
            got.add(new Drops.Drop(d.item(), Math.max(1, (int) Math.round(d.amount() * more))));
        }
        s.state().put(BACK, now + regrow(s));
        return got;
    }
}
