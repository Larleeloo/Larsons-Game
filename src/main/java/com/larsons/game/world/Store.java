package com.larsons.game.world;

import com.larsons.game.combat.Target;
import com.larsons.game.math.Vec3;

import java.util.Locale;

/**
 * Somewhere to keep things: a barrel, a crate, a pot, or a chest that is
 * not a loot chest. Each holds so many slots of anything ({@link #items}),
 * filled in Creative Mode ({@link #fill}) or by her - E by it opens it
 * beside her inventory, to move things in and out.
 *
 * <p>A barrel, a crate, a pot or a storage chest breaks: it is a {@link
 * Target} her blows and shots land on, and broken it shatters into pieces
 * ({@link Shards}), what it held spilled out round where it stood ({@code
 * World}). A loot chest is not a store, and does not.
 */
public final class Store implements Target {

    /**
     * What it is: its slots, how much a blow it takes, how big it is to hit -
     * and, broken, how many pieces it shatters into and how big they are.
     */
    public enum Kind {
        BARREL("barrel", "Barrel", 9, 30, 0.34, 0.95, 26, 0.13),
        CRATE("crate", "Crate", 12, 22, 0.36, 0.72, 24, 0.13),
        CHEST("chest", "Chest", 20, 40, Chest.RADIUS, 0.8, 30, 0.12),
        POT("pot", "Pot", 4, 6, 0.24, 0.5, 18, 0.08),
        LARGE_POT("large_pot", "Large pot", 6, 10, 0.3, 0.85, 24, 0.1);

        private final String key, label;
        private final int slots, pieces;
        private final double health, radius, height, pieceSize;

        Kind(String key, String label, int slots, double health, double radius, double height, int pieces,
             double pieceSize) {
            this.key = key;
            this.label = label;
            this.slots = slots;
            this.health = health;
            this.radius = radius;
            this.height = height;
            this.pieces = pieces;
            this.pieceSize = pieceSize;
        }

        /** How many pieces it shatters into. */
        public int pieces() { return pieces; }

        /** About how big a piece of it is, metres. */
        public double pieceSize() { return pieceSize; }

        /** Whether it is fired clay: it shatters, rather than breaking. */
        public boolean clay() { return this == POT || this == LARGE_POT; }

        public String key() { return key; }

        public String label() { return label; }

        /** How many slots it has. */
        public int slots() { return slots; }

        /** How much damage breaks it (0: it does not break). */
        public double toughness() { return health; }

        /** Whether it breaks. */
        public boolean breakable() { return health > 0; }

        /** The kind a building of kind {@code key} is ("barrel", "crate"), or null. */
        public static Kind ofBuilding(String key) {
            if (key == null) return null;
            return switch (key) {
                case "barrel" -> BARREL;
                case "crate" -> CRATE;
                case "pot" -> POT;
                case "large_pot" -> LARGE_POT;
                default -> null;
            };
        }
    }

    /** "x6" after an item: how many. */
    private static final java.util.regex.Pattern HOW_MANY = java.util.regex.Pattern.compile("^(.*?)\\s*[x×*]\\s*(\\d+)$");

    /** How far beyond its side she can reach into it, metres. */
    public static final double REACH = 1.0;

    private final int id;
    private final Kind kind;
    private final Vec3 position;
    private final double heading;
    private final Inventory items;
    private double health, sinceHurt = Double.MAX_VALUE;
    private boolean broken;

    /** {@code kind}, the level's object {@code id}, standing at {@code position}, its front to {@code heading}. */
    public Store(int id, Kind kind, Vec3 position, double heading) {
        this.id = id;
        this.kind = kind;
        this.position = position;
        this.heading = heading;
        this.items = new Inventory(kind.slots());
        this.health = kind.toughness();
    }

    /** The level object it is (its building's, its chest's). */
    public int id() { return id; }

    public Kind kind() { return kind; }

    public double heading() { return heading; }

    /** What it holds. */
    public Inventory items() { return items; }

    /** Whether it has been broken open. */
    public boolean broken() { return broken; }

    /** Whether she, standing at {@code at}, can reach into it. */
    public boolean inReach(Vec3 at) {
        return !broken && position.horizontalDistance(at) <= kind.radius + REACH && Math.abs(at.y() - position.y()) <= 1.5;
    }

    /**
     * Put what {@code contents} lists in it: item ids, separated by commas,
     * each with how many after it if more than one, and its tier after it if
     * it has tiers - {@code "wild berries x6, sage, longbow perfect"}. What
     * is not an item is left out; what does not fit, too. Returns how many
     * it took.
     */
    public int fill(String contents) {
        if (contents == null || contents.isBlank()) return 0;
        int n = 0;
        for (String part : contents.split(",")) {
            String s = part.trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) continue;
            int count = 1;
            java.util.regex.Matcher m = HOW_MANY.matcher(s);
            if (m.matches()) {
                s = m.group(1).trim();
                count = Math.min(999, Integer.parseInt(m.group(2)));
            }
            // "wild berries", "wild_berries", or with its tier, rarity, border after it: "longbow perfect"
            Item item = Item.parse(s.replace(' ', '_'));
            if (item == null) item = Item.parse(s);
            if (item == null) continue;
            for (int i = 0; i < count; i++) {
                if (items.add(item) < 0) return n;
                n++;
            }
        }
        return n;
    }

    public void tick(double dt) {
        sinceHurt += dt;
    }

    // --- a target ---------------------------------------------------------------------------

    @Override
    public Vec3 position() { return position; }

    @Override
    public double radius() { return kind.radius; }

    @Override
    public double height() { return kind.height; }

    @Override
    public boolean alive() { return kind.breakable() && !broken; }

    @Override
    public double health() { return health; }

    @Override
    public double maxHealth() { return Math.max(1, kind.toughness()); }

    @Override
    public void hurt(double damage, Vec3 from, boolean react) {
        if (!alive()) return;
        health -= damage;
        sinceHurt = 0;
        if (health <= 0) {
            health = 0;
            broken = true;
        }
    }

    @Override
    public double sinceHurt() { return sinceHurt; }

    @Override
    public String name() { return kind.label(); }

    @Override
    public boolean living() { return false; }
}
