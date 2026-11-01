package com.larsons.game.ambient;

import com.larsons.game.world.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The animals of the world - the farm's, the woods', the water's and a few
 * small ones - each with its body (how big a target it is, how much it
 * takes), how it goes, its nature, where it lives and its sounds.
 *
 * <p><b>How it goes</b> ({@link Gait}) is which sheets its sprite has
 * (3D-Modeling's {@code creatures/animals.py}, 8 directions x 3 heights):
 * a four-legged animal idles, walks, runs and grazes; a hopper hops; a bird
 * walks, swims, flies, and the goose and the swan go for her with their
 * wings up; the butterfly and the bee only fly; the iguana walks. Its
 * {@link #walk} and {@link #run} are how fast it goes, metres a second, and
 * {@link #walkPace} / {@link #runPace} how fast its sheets were drawn going
 * (the feet keep to the ground when it goes faster or slower: the sheet
 * plays faster or slower).
 *
 * <p><b>Its nature</b> is its {@link Temperament}, which a herd placed in
 * Creative Mode can change: a rabbit runs from her, a cow minds its own
 * business, a dog comes up to her, a goose warns her off and goes for her
 * when she stays - or hits it. {@link #shy} is how near she can come before
 * a skittish one runs; {@link #warn} how near before a defensive one warns
 * her off. When it fights its blow takes {@link #damage} within {@link
 * #reach}. Beaten - its health gone - it runs off, and is gone.
 *
 * <p><b>Where it lives</b>: a weight for every {@link Biome} (0: never) and
 * how near water it keeps, as the critters'; {@link #cap} is how many live
 * round the player at once where all of that is at its best, at the level's
 * density 1 ({@link Life}); they come in herds of up to {@link #herd}.
 */
public enum AnimalKind {

    HORSE("horse", "Horse", "Horses", Gait.FOUR, Temperament.CALM, 0.55, 1.7, 40, 1.3, 6.5, 1.25, 6.0,
            0, 0, 8, 1.5, 3, 4, 0, w(1.0, 0, 0.3, 0, 1.2, 0, 0.4, 0, 0), "neigh", "snort", null, null),
    COW("cow", "Cow", "Cows", Gait.FOUR, Temperament.CALM, 0.6, 1.5, 40, 1.0, 4.0, 1.11, 4.6,
            0, 0, 6, 1.4, 3, 4, 0, w(0.9, 0.2, 0.3, 0, 0.6, 0, 0.4, 0, 0), "moo", "bellow", null, null),
    BEAR("bear", "Bear", "Bears", Gait.FOUR, Temperament.CALM, 0.6, 1.25, 60, 1.0, 5.0, 1.0, 4.6,
            0, 0, 10, 1.4, 1, 1, 0, w(0.3, 0.1, 1.0, 0.3, 0, 0, 0.5, 0, 0), "growl", "roar", "roar", null),
    SHEEP("sheep", "Sheep", "Sheep", Gait.FOUR, Temperament.SKITTISH, 0.4, 0.95, 20, 0.8, 3.2, 0.73, 3.07,
            2.5, 0, 3, 1.0, 4, 6, 0, w(0.8, 0, 1.0, 0, 0.2, 0, 0.3, 0, 0), "baa", "bleat", null, null),
    PIG("pig", "Pig", "Pigs", Gait.FOUR, Temperament.CALM, 0.4, 0.8, 25, 0.7, 2.6, 0.6, 2.04,
            0, 0, 4, 1.0, 2, 3, 0, w(0.6, 0.8, 0, 0.5, 0, 0, 0.3, 0, 0), "oink", "squeal", null, null),
    DOG("dog", "Dog", "Dogs", Gait.FOUR, Temperament.FRIENDLY, 0.3, 0.75, 20, 1.0, 4.5, 0.73, 3.5,
            0, 0, 4, 1.0, 1, 1, 0, w(0.3, 0, 0.1, 0, 0.2, 0.2, 0.2, 0, 0), "bark", "whine", "growl", null),
    FOX("fox", "Fox", "Foxes", Gait.FOUR, Temperament.SKITTISH, 0.28, 0.5, 15, 0.8, 4.5, 0.69, 3.8,
            6, 0, 4, 0.9, 1, 1, 0, w(0.8, 0.3, 0.6, 0.2, 0.4, 0.1, 0.5, 0.2, 0.2), "yip", "yelp", null, null),
    CAT("cat", "Cat", "Cats", Gait.FOUR, Temperament.FRIENDLY, 0.22, 0.4, 10, 0.6, 3.5, 0.57, 3.1,
            0, 0, 2, 0.8, 1, 1, 0, w(0.3, 0, 0, 0.1, 0.1, 0.2, 0.1, 0.1, 0), "meow", "yowl", "hiss", null),
    RABBIT("rabbit", "Rabbit", "Rabbits", Gait.HOP, Temperament.SKITTISH, 0.2, 0.4, 6, 0.9, 3.4, 0.9, 2.2,
            5, 0, 1, 0.6, 4, 2, 0, w(1.2, 0.3, 0.5, 0.2, 0.7, 0.2, 0.7, 0, 0.3), "thump", "squeak", null, null),
    SQUIRREL("squirrel", "Squirrel", "Squirrels", Gait.HOP, Temperament.SKITTISH, 0.17, 0.35, 5, 0.7, 2.8, 0.7,
            1.8, 4, 0, 1, 0.6, 3, 1, 0, w(1.0, 0.2, 0.6, 0.6, 0.1, 0, 0.7, 0, 0.1), "chatter", "squeak", null, null),
    DUCK("duck", "Duck", "Ducks", Gait.BIRD, Temperament.SKITTISH, 0.22, 0.45, 8, 0.4, 1.4, 0.45, 0.45,
            4, 0, 2, 0.7, 4, 4, 6, w(0.5, 1.2, 0, 0.3, 0, 0.3, 1.2, 0, 0), "quack", "squawk", null, "flap"),
    GOOSE("goose", "Goose", "Geese", Gait.BIRD, Temperament.DEFENSIVE, 0.32, 0.8, 18, 0.5, 2.0, 0.5, 0.5,
            0, 4, 3, 0.95, 3, 4, 10, w(0.6, 0.8, 0, 0, 0.3, 0.3, 1.0, 0, 0), "honk", "squawk", "hiss", "flap"),
    SWAN("swan", "Swan", "Swans", Gait.BIRD, Temperament.DEFENSIVE, 0.42, 1.0, 28, 0.5, 1.8, 0.55, 0.55,
            0, 4.5, 5, 1.15, 2, 2, 4, w(0.4, 0.6, 0, 0, 0, 0.2, 1.0, 0, 0.4), "trumpet", "squawk", "hiss", "flap"),
    BUTTERFLY("butterfly", "Butterfly", "Butterflies", Gait.FLY, Temperament.CALM, 0.16, 0.2, 2, 0.8, 2.5, 0.8,
            0.8, 0, 0, 0, 0.5, 6, 3, 0, w(1.2, 0.5, 0.5, 1.3, 0.6, 0.3, 0.9, 0, 0.8), null, null, null, null),
    BEE("bee", "Bee", "Bees", Gait.FLY, Temperament.DEFENSIVE, 0.14, 0.15, 3, 1.4, 4.5, 1.4, 1.4,
            0, 0, 2, 0.6, 5, 4, 0, w(1.0, 0.2, 0.4, 0.7, 0.6, 0, 0.6, 0, 0.3), "buzz", null, "swarm", null),
    IGUANA("iguana", "Iguana", "Iguanas", Gait.CRAWL, Temperament.SKITTISH, 0.3, 0.3, 10, 0.3, 1.2, 0.26, 0.26,
            3, 0, 2, 0.7, 2, 2, 0, w(0, 0.3, 0, 1.2, 0.7, 0.7, 0.1, 0.5, 0), null, "hiss", "hiss", null);

    /** How it goes - which sheets its sprite has. */
    public enum Gait {
        /** Four legs: idle, walk, run, eat. */
        FOUR,
        /** A rabbit, a squirrel: idle, hop, eat - it goes a hop at a time. */
        HOP,
        /** A duck, a goose, a swan: idle, walk, swim, fly, attack, eat. */
        BIRD,
        /** A butterfly, a bee: fly - it is never on the ground. */
        FLY,
        /** The iguana: idle, walk. */
        CRAWL
    }

    /** How long one of its hops lasts, seconds, as drawn (6 frames at 12 a second). */
    public static final double HOP = 0.5;

    private final String key, name, label;
    private final Gait gait;
    private final Temperament temperament;
    private final double radius, height, health, walk, run, walkPace, runPace, shy, warn, damage, reach;
    private final int cap, herd;
    private final double nearWater;
    private final double[] weights;      // by Biome.ordinal()
    private final String call, hurt, angry, flap;

    AnimalKind(String key, String name, String label, Gait gait, Temperament temperament, double radius,
               double height, double health, double walk, double run, double walkPace, double runPace, double shy,
               double warn, double damage, double reach, int cap, int herd, double nearWater, double[] weights,
               String call, String hurt, String angry, String flap) {
        this.key = key;
        this.name = name;
        this.label = label;
        this.gait = gait;
        this.temperament = temperament;
        this.radius = radius;
        this.height = height;
        this.health = health;
        this.walk = walk;
        this.run = run;
        this.walkPace = walkPace;
        this.runPace = runPace;
        this.shy = shy;
        this.warn = warn;
        this.damage = damage;
        this.reach = reach;
        this.cap = cap;
        this.herd = herd;
        this.nearWater = nearWater;
        this.weights = weights;
        this.call = call;
        this.hurt = hurt;
        this.angry = angry;
        this.flap = flap;
    }

    private static double[] w(double... byBiome) {
        return byBiome;
    }

    /** One word for it, in level files and scripts, and its sprite's folder: {@code goose}. */
    public String key() { return key; }

    /** What one is called: "Goose". */
    public String singular() { return name; }

    /** What a lot of them are called: "Geese". */
    public String label() { return label; }

    /** Its object sprite's folder (assets/sprites/objects/). */
    public String sprite() { return key; }

    public Gait gait() { return gait; }

    /** Its nature, unless a herd placed in Creative Mode says otherwise. */
    public Temperament temperament() { return temperament; }

    /** How far from its middle a blow touches it, metres. */
    public double radius() { return radius; }

    /** How tall it stands, metres. */
    public double height() { return height; }

    public double maxHealth() { return health; }

    /** How fast it walks (a hopper: how far one hop takes it walking), metres a second. */
    public double walk() { return walk; }

    /** How fast it runs (a bird: waddles at her), metres a second. */
    public double run() { return run; }

    /** How fast its walk was drawn going: its sheet plays at {@code speed / walkPace}. */
    public double walkPace() { return walkPace; }

    /** How fast its run was drawn going. */
    public double runPace() { return runPace; }

    /** How near she can come before it runs (a skittish one), metres; 0: it does not mind. */
    public double shy() { return shy; }

    /** How near she can come before it warns her off (a defensive one), metres; 0: it never warns. */
    public double warn() { return warn; }

    /** What its blow takes, when it fights. */
    public double damage() { return damage; }

    /** How near her its blow reaches, metres between her and its middle. */
    public double reach() { return reach; }

    /** How many live round the player at once at their best, at density 1. */
    public int cap() { return cap; }

    /** The most of a herd that come together. */
    public int herd() { return herd; }

    /** How near water it keeps, metres; 0: anywhere. */
    public double nearWater() { return nearWater; }

    /** Whether it swims (it sits on water deeper than it wades). */
    public boolean swims() { return gait == Gait.BIRD; }

    /** Whether it is always in the air. */
    public boolean flies() { return gait == Gait.FLY; }

    /** How deep a water it walks into, metres (deeper: it swims, or it keeps out). */
    public double wade() {
        return switch (this) {
            case HORSE, COW, BEAR -> 0.35;
            case SHEEP, PIG, DOG -> 0.15;
            default -> 0.04;
        };
    }

    /** How thick it lives in {@code biome}: 0 never, 1 at home. */
    public double weight(Biome biome) {
        return biome == null ? 0 : weights[biome.ordinal()];
    }

    /** How many of its number are about at a time of day (0 night, 1 day): the insects by day only. */
    public double out(double day) {
        return flies() ? CritterKind.smooth(0.4, 0.7, day) : 1;
    }

    /** Its sounds' names, in order: its call, its hurt, its angry one, its wings - those it has. */
    public List<String> sounds() {
        List<String> out = new ArrayList<>();
        for (String s : new String[]{call, hurt, angry, flap}) if (s != null && !out.contains(s)) out.add(s);
        if (this == BEE) out.add("sting");
        return out;
    }

    /** The sound it makes now and then, or null. */
    public String call() { return call; }

    /** Its sound when it is hit, or null. */
    public String hurt() { return hurt; }

    /** Its sound going for her (or warning her off), or null. */
    public String angry() { return angry; }

    /** Its wings taking off, or null. */
    public String flap() { return flap; }

    /** The kind written {@code key} (any case, one or many: "goose", "Geese"), or null. */
    public static AnimalKind byKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (AnimalKind a : values()) {
            if (a.key.equals(k) || a.label.toLowerCase(Locale.ROOT).equals(k)
                    || a.name.toLowerCase(Locale.ROOT).equals(k)) return a;
        }
        return null;
    }
}
