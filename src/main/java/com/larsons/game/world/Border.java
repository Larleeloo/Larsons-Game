package com.larsons.game.world;

import java.util.Random;

/**
 * A rare border an item can drop with, whatever its rarity: one chance in a
 * hundred each, and never more than one. It is drawn round the item - its
 * sprite in the world, its icon in the inventory - in its colour, and it
 * changes what the weapon does in a fight:
 *
 * <ul>
 *   <li><b>Venom</b> (lime green) - after the first hit, half as much damage
 *       again as damage over time for 10 seconds: a 100-damage hit is 150
 *       after 10 seconds.</li>
 *   <li><b>Mystic</b> (smoky purple) - +150 % damage, but the weapon uses
 *       twice the mana.</li>
 *   <li><b>Bonded</b> (pink) - +10 % damage for every living thing hit with
 *       it in the last 5 minutes: five enemies hit and all still alive is
 *       +50 % a hit.</li>
 * </ul>
 *
 * <p>The numbers are here for the fight to use ({@link #damageOverTime},
 * {@link #damageBonus}, ...); the colours are entries of the art's
 * 128-colour palette.
 */
public enum Border {
    VENOM("venom", "Venom", 0x7EE728,
            "+50% of damage as damage over time for 10 s after the first hit"),
    MYSTIC("mystic", "Mystic", 0x636092,
            "+150% damage, but the weapon uses twice the mana"),
    BONDED("bonded", "Bonded", 0xFD81A5,
            "+10% damage for each living thing hit with it in the last 5 minutes");

    /** The chance of each border on any item. */
    public static final double CHANCE = 0.01;

    /** Venom: the share of a hit's damage dealt again over time, and over how long (seconds). */
    public static final double VENOM_SHARE = 0.5, VENOM_SECONDS = 10;
    /** Mystic: the damage multiplier, and how many times the mana it costs. */
    public static final double MYSTIC_DAMAGE = 2.5, MYSTIC_MANA = 2;
    /** Bonded: the bonus for each living thing hit, and how long a hit counts (seconds). */
    public static final double BONDED_PER_HIT = 0.10, BONDED_SECONDS = 5 * 60;

    private final String key, label, effect;
    private final int rgb;

    Border(String key, String label, int rgb, String effect) {
        this.key = key;
        this.label = label;
        this.rgb = rgb;
        this.effect = effect;
    }

    public String key() { return key; }

    public String label() { return label; }

    /** What it does, in a line: for the inventory's tooltip. */
    public String effect() { return effect; }

    /** Its colour, {@code 0xRRGGBB} (a palette entry). */
    public int rgb() { return rgb; }

    /** Its colour as straight RGBA floats, alpha {@code a}. */
    public float[] colour(float a) {
        return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a};
    }

    /** A border for a new drop: each one {@link #CHANCE}, or none (null). */
    public static Border roll(Random random) {
        double x = random.nextDouble();
        for (Border b : values()) {
            if (x < CHANCE) return b;
            x -= CHANCE;
        }
        return null;
    }

    public static Border byKey(String key) {
        for (Border b : values()) if (b.key.equalsIgnoreCase(key)) return b;
        return null;
    }
}
