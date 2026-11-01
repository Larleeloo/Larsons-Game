package com.larsons.game.ambient;

import com.larsons.game.world.Biome;

import java.util.Locale;

/**
 * The little creatures that live round the player - the Ambient milestone's
 * crickets, frogs, fireflies and dragonflies - each with where it lives,
 * when it is about and how many there are at once.
 *
 * <p>A kind has a <b>habitat</b>: a weight for every {@link Biome} (how
 * thick it is there, 0: never), whether it keeps near water ({@link
 * #nearWater}: frogs at the edge, dragonflies over it), and the time of day
 * it is out ({@link #out}: fireflies after dark, dragonflies by day, the
 * crickets always - singing at night). {@link #cap} is how many live round
 * the player at once where all of that is at its best, at a level density of
 * 1 ({@link Life}).
 *
 * <p>Its sprite is an object of {@code assets/sprites/objects/<sprite>/}
 * (3D-Modeling's {@code creatures/critters.py}), drawn from the character's
 * 24 views; a firefly has none - it is a point of light.
 */
public enum CritterKind {

    FIREFLY("firefly", "Fireflies", null, 36, 0, 0.0,
            new double[]{1.0, 1.4, 0.2, 1.0, 0.4, 0.1, 1.2, 0.0, 0.8}),
    CRICKET("cricket", "Crickets", "cricket", 14, 0, 0.0,
            new double[]{1.0, 0.5, 0.3, 0.5, 1.2, 0.0, 0.6, 0.1, 0.2}),
    FROG("frog", "Frogs", "frog", 10, 3.0, 0.12,
            new double[]{0.4, 1.5, 0.0, 0.8, 0.0, 0.0, 1.2, 0.0, 0.3}),
    DRAGONFLY("dragonfly", "Dragonflies", "dragonfly", 8, 6.0, 0.0,
            new double[]{0.5, 1.3, 0.0, 0.8, 0.2, 0.0, 1.3, 0.0, 0.6});

    /** How long one of each sheet plays, from 3D-Modeling's frames and fps: a frog's hop, croak; a cricket's hop. */
    public static final double FROG_HOP = 8 / 12.0, FROG_CROAK = 10 / 12.0, CRICKET_HOP = 6 / 12.0;

    private final String key;
    private final String label;
    private final String sprite;
    private final int cap;
    private final double nearWater;
    private final double wade;
    private final double[] weights;      // by Biome.ordinal()

    CritterKind(String key, String label, String sprite, int cap, double nearWater, double wade, double[] weights) {
        this.key = key;
        this.label = label;
        this.sprite = sprite;
        this.cap = cap;
        this.nearWater = nearWater;
        this.wade = wade;
        this.weights = weights;
    }

    /** One word for it, in level files and scripts: {@code firefly}. */
    public String key() { return key; }

    /** What the editor calls a lot of them: "Fireflies". */
    public String label() { return label; }

    /** Its object sprite's folder, or null (a firefly: a point of light). */
    public String sprite() { return sprite; }

    /** How many live round the player at once at their best, at density 1. */
    public int cap() { return cap; }

    /** How near water (metres) it keeps; 0: anywhere. */
    public double nearWater() { return nearWater; }

    /** How deep water it may sit in (a frog in the shallows); 0: dry ground only (a flier: over anything). */
    public double wade() { return wade; }

    /** Whether it flies (it is not on the ground). */
    public boolean flies() { return this == FIREFLY || this == DRAGONFLY; }

    /** How thick it is in {@code biome}: 0 never, 1 at home, more where it swarms. */
    public double weight(Biome biome) {
        return biome == null ? 0 : weights[biome.ordinal()];
    }

    /**
     * How many of its number are out at a time of day ({@code day}: 0 the
     * dead of night, 1 full day, as {@code DayCycle.Daylight#day}): the
     * fireflies from dusk, the dragonflies while the sun is well up, the
     * crickets and the frogs always, a few more by night.
     */
    public double out(double day) {
        return switch (this) {
            case FIREFLY -> smooth(0.55, 0.9, 1 - day);
            case DRAGONFLY -> smooth(0.45, 0.8, day);
            case CRICKET -> 0.5 + 0.5 * (1 - day);
            case FROG -> 0.7 + 0.3 * (1 - day);
        };
    }

    /** The kind written {@code key} (any case), or null. */
    public static CritterKind byKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (CritterKind c : values()) {
            if (c.key.equals(k) || c.label.toLowerCase(Locale.ROOT).equals(k)) return c;
        }
        if (k.endsWith("ies")) return byKey(k.substring(0, k.length() - 3) + "y");
        if (k.endsWith("s")) return byKey(k.substring(0, k.length() - 1));
        return null;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
