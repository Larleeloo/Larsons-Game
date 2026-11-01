package com.larsons.game.building;

import java.util.List;

/**
 * A set of buildings placed together as one - a small castle is its keep,
 * a round tower at each corner, the curtain walls between them and the
 * gatehouse in the front wall - each piece then a building of its own, to
 * be moved, turned, coloured or taken away as any other.
 *
 * <p>Measured as a building is: metres, the set's front towards -y.
 *
 * @param key    how it is written
 * @param label  what the editor calls it
 * @param pieces its buildings
 */
public record Prefab(String key, String label, List<Piece> pieces) {

    /**
     * One of its buildings: its kind, its middle ({@code x}, {@code y} in
     * the set's metres) and the way its front faces ({@code faceX}, {@code
     * faceY}, in the set's metres too).
     */
    public record Piece(String kind, double x, double y, double faceX, double faceY) {}

    /** How far from its middle its pieces reach, with {@code catalog}'s buildings' own reach. */
    public double reach(BuildingCatalog catalog) {
        double r = 1;
        for (Piece p : pieces) {
            BuildingProfile b = catalog.profile(p.kind());
            r = Math.max(r, Math.hypot(p.x(), p.y()) + (b == null ? 1 : b.reach()));
        }
        return r;
    }

    /** Where the corner towers of the small castle stand from its middle, along each side. */
    static final double CASTLE_CORNER = 14.0;

    /** Every set there is. */
    public static final List<Prefab> ALL = List.of(smallCastle());

    /** The set called {@code key}, or null. */
    public static Prefab byKey(String key) {
        for (Prefab p : ALL) if (p.key.equals(key)) return p;
        return null;
    }

    /**
     * A keep in a square of curtain walls (three a side), a round tower at
     * each corner with its door to the courtyard, and the gatehouse in the
     * middle of the front wall.
     */
    static Prefab smallCastle() {
        double c = CASTLE_CORNER, s = 7.5;      // (a length of wall is 7.5 m)
        List<Piece> p = new java.util.ArrayList<>();
        p.add(new Piece("keep", 0, 1.875, 0, -1));
        p.add(new Piece("round_tower", -c, -c, 1, 1));
        p.add(new Piece("round_tower", c, -c, -1, 1));
        p.add(new Piece("round_tower", -c, c, 1, -1));
        p.add(new Piece("round_tower", c, c, -1, -1));
        p.add(new Piece("castle_wall", -s, -c, 0, -1));
        p.add(new Piece("gatehouse", 0, -c, 0, -1));
        p.add(new Piece("castle_wall", s, -c, 0, -1));
        for (double along : new double[]{-s, 0, s}) {
            p.add(new Piece("castle_wall", along, c, 0, 1));
            p.add(new Piece("castle_wall", -c, along, -1, 0));
            p.add(new Piece("castle_wall", c, along, 1, 0));
        }
        return new Prefab("small_castle", "Small castle", List.copyOf(p));
    }
}
