package com.larsons.game.world;

import java.util.Locale;

/**
 * The kinds of country the moon has: what the ground is made of in each,
 * what grows there (the scenery whose profile names the biome), what water
 * runs through it, and - for the critters and the sounds - which biome a
 * spot is in.
 *
 * <p>A biome is painted in Creative Mode with the biome brush: its {@link
 * #ground} with patches of its {@link #accent} worked through it (where a
 * slow noise is high), and, if asked, its plants, trees and rocks sown over
 * it. Which biome a spot is in ({@link #at}) is read back from the ground: the
 * material that shows there says the biome - mud is the marsh, ash the
 * waste - and open water makes it the river or the sea.
 */
public enum Biome {
    PLAINS("plains", "Plains", -1, GroundMap.MEADOW, 0.62, WaterMap.Kind.CLEAR, "plains", "crickets"),
    MARSH("marsh", "Marsh", GroundMap.MUD, GroundMap.MEADOW, 0.6, WaterMap.Kind.SWAMP, "marsh", "frogs"),
    MOUNTAINS("mountains", "Mountains", GroundMap.STONE, GroundMap.SNOW, 0.42, WaterMap.Kind.CLEAR, "mountains",
            "mountains"),
    JUNGLE("jungle", "Jungle", GroundMap.JUNGLE, GroundMap.FOREST, 0.55, WaterMap.Kind.SWAMP, "jungle", "jungle_night"),
    SAVANNAH("savannah", "Savannah", GroundMap.DRY_GRASS, GroundMap.DIRT, 0.66, WaterMap.Kind.CLEAR, "savannah",
            "crickets"),
    OCEAN("ocean", "Ocean shore", GroundMap.SAND, GroundMap.GRAVEL, 0.7, WaterMap.Kind.SEA, "ocean", "ocean"),
    RIVER("river", "River", GroundMap.GRAVEL, GroundMap.SAND, 0.5, WaterMap.Kind.CLEAR, "stream", "stream"),
    WASTE("waste", "Waste", GroundMap.ASH, GroundMap.STONE, 0.6, WaterMap.Kind.SWAMP, "waste", "waste"),
    CRYSTAL("crystal", "Crystal", GroundMap.CRYSTAL, GroundMap.STONE, 0.72, WaterMap.Kind.CRYSTAL, "crystal",
            "crystal");

    private final String key, label, ambient, ambientNight;
    private final int ground, accent;
    private final double accentAbove;
    private final WaterMap.Kind water;

    Biome(String key, String label, int ground, int accent, double accentAbove, WaterMap.Kind water, String ambient,
          String ambientNight) {
        this.key = key;
        this.label = label;
        this.ground = ground;
        this.accent = accent;
        this.accentAbove = accentAbove;
        this.water = water;
        this.ambient = ambient;
        this.ambientNight = ambientNight;
    }

    /** Its name in files and in the scenery's profiles: {@code marsh}. */
    public String key() { return key; }

    /** Its name in the editor: "Marsh". */
    public String label() { return label; }

    /** The ground it is mostly ({@link GroundMap}'s material; -1: grass). */
    public int ground() { return ground; }

    /** The ground in patches through it. */
    public int accent() { return accent; }

    /** The water that runs through it. */
    public WaterMap.Kind water() { return water; }

    /** Its sound by day, heard while standing in it: the name of an {@code ambient/} loop ({@code marsh}). */
    public String ambient() { return ambient; }

    /** Its sound by night (the plains' crickets, the marsh's frogs). */
    public String ambientNight() { return ambientNight; }

    /**
     * The ground the biome brush paints at {@code (x, z)}: its accent where a
     * slow noise through the world is high, its own ground elsewhere.
     */
    public int groundAt(double x, double z) {
        return patch(x, z) > accentAbove ? accent : ground;
    }

    /** A slow noise in 0 - 1, the same everywhere for everyone: where the accent's patches lie. */
    static double patch(double x, double z) {
        double n = 0.6 * noise(x / 6.0, z / 6.0) + 0.4 * noise(x / 2.3 + 17.1, z / 2.3 - 5.3);
        return n;
    }

    private static double noise(double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz), b = hash(ix + 1, iz), c = hash(ix, iz + 1), d = hash(ix + 1, iz + 1);
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    private static double hash(int x, int z) {
        long h = x * 374761393L + z * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    /**
     * The biome {@code (x, z)} is in: open water's (the sea, or a river or
     * stream), else the one whose ground shows there, the plains on grass
     * and on what is every biome's (the paths, the forest floor, gravel).
     */
    public static Biome at(GroundMap ground, WaterMap water, double x, double z, double groundY) {
        if (water != null) {
            WaterMap.Kind k = water.kindAt(x, z, groundY);
            if (k == WaterMap.Kind.SEA) return OCEAN;
            if (k == WaterMap.Kind.CRYSTAL) return CRYSTAL;
            if (k != null) return k == WaterMap.Kind.SWAMP ? MARSH : RIVER;
        }
        if (ground == null) return PLAINS;
        return switch (ground.material(x, z)) {
            case GroundMap.MUD -> MARSH;
            case GroundMap.STONE, GroundMap.SNOW -> MOUNTAINS;
            case GroundMap.JUNGLE -> JUNGLE;
            case GroundMap.DRY_GRASS -> SAVANNAH;
            case GroundMap.SAND -> OCEAN;
            case GroundMap.ASH -> WASTE;
            case GroundMap.CRYSTAL -> CRYSTAL;
            default -> PLAINS;
        };
    }

    /** The biome called {@code key} (any case), or null. */
    public static Biome byKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (Biome b : values()) if (b.key.equals(k)) return b;
        return null;
    }
}
