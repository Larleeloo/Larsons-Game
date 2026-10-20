package com.larsons.game.world;

import java.util.Random;

/**
 * How rare an item is: six tiers, each with its colour - the halo every item
 * of it glows with, the beam of light over it when it comes out of a chest,
 * its slot in the inventory and the die its damage is rolled on - and how
 * often it drops ({@link #weight}, in percent: they add up to 100).
 *
 * <pre>
 *   Common     white    45 %       Epic       purple   3.9 %
 *   Uncommon   green    35 %       Legendary  orange   1 %
 *   Rare       blue     15 %       Mythic     cyan     0.1 %
 * </pre>
 *
 * <p>The colours are entries of the art's 128-colour palette ({@code
 * assets/sprites/pixel_palette.json}); the palette has no pure cyan, so
 * Mythic is its nearest, a sea teal.
 */
public enum Rarity {
    COMMON("common", "Common", 0xFFFFFF, 45.0),
    UNCOMMON("uncommon", "Uncommon", 0x24C835, 35.0),
    RARE("rare", "Rare", 0x5D86FA, 15.0),
    EPIC("epic", "Epic", 0xB722E7, 3.9),
    LEGENDARY("legendary", "Legendary", 0xFB992C, 1.0),
    MYTHIC("mythic", "Mythic", 0x439F9D, 0.1);

    private final String key, label;
    private final int rgb;
    private final double weight;

    Rarity(String key, String label, int rgb, double weight) {
        this.key = key;
        this.label = label;
        this.rgb = rgb;
        this.weight = weight;
    }

    public String key() { return key; }

    public String label() { return label; }

    /** Its colour, {@code 0xRRGGBB} (a palette entry). */
    public int rgb() { return rgb; }

    /** Its colour as straight RGBA floats, alpha {@code a}: for the UI and the batch. */
    public float[] colour(float a) {
        return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a};
    }

    /** How often an item of it drops, in percent of all drops. */
    public double weight() { return weight; }

    /** Whether it is this rare or rarer: {@code r.atLeast(RARE)} for Rare, Epic, Legendary and Mythic. */
    public boolean atLeast(Rarity other) {
        return ordinal() >= other.ordinal();
    }

    /** A rarity drawn by the drop rates. */
    public static Rarity roll(Random random) {
        return roll(random, COMMON);
    }

    /** A rarity of at least {@code floor}, drawn by the drop rates of those that are. */
    public static Rarity roll(Random random, Rarity floor) {
        double total = 0;
        for (Rarity r : values()) if (r.atLeast(floor)) total += r.weight;
        double x = random.nextDouble() * total;
        Rarity last = floor;
        for (Rarity r : values()) {
            if (!r.atLeast(floor)) continue;
            last = r;
            x -= r.weight;
            if (x < 0) return r;
        }
        return last;
    }

    public static Rarity byKey(String key) {
        for (Rarity r : values()) if (r.key.equalsIgnoreCase(key)) return r;
        return null;
    }
}
