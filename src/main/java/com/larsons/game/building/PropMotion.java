package com.larsons.game.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What moves on a thing that stands still: a cauldron's brew bubbling, a
 * lightning coil's sparks crackling off its crown, a fire's flames flickering
 * and its embers going up, a candle's flame wavering, smoke from a chimney,
 * steam off a boiler, a fountain's water leaping and falling, a crystal
 * glinting, bees round a hive. Worked out once from a model's own parts
 * ({@link #of}) - where its brew, its spark, its fire, its chimneys are - and
 * drawn round it as it stands, turned as it is turned ({@code
 * world/PropMotionRenderer}).
 *
 * <p>Which parts move is by their names in the model (3D-Modeling's
 * {@code buildings.py}): {@code brew}, {@code potion}, {@code spark},
 * {@code fire}, {@code flame}, {@code chimney}/{@code stack}, {@code water},
 * {@code crystal}; and a few things by what they are (a boiler's steam, a
 * stove's smoke, a hive's bees).
 */
public final class PropMotion {

    /** What moves. */
    public enum Kind {
        /** Bubbles rising off a brew and bursting, a wisp of steam over them. */
        BUBBLES,
        /** Lightning: jagged arcs leaping off it, a flash with each. */
        ARCS,
        /** A fire's glow, flickering. */
        FIRE,
        /** Sparks going up off an open fire. */
        EMBERS,
        /** A small flame wavering: a candle's, a lamp's. */
        FLICKER,
        /** Smoke going up and drifting off. */
        SMOKE,
        /** Steam: whiter, quicker and sooner gone than smoke. */
        STEAM,
        /** Water leaping up and falling back. */
        SPRAY,
        /** Light catching on it here and there, now and then. */
        GLINTS,
        /** Bees about a hive. */
        BEES
    }

    /**
     * One thing moving, in the model's own metres (x to its right, y towards
     * its back, z up): the middle of the top of the part it comes off, the
     * part's half width and depth, its foot, and its colour.
     */
    public record Source(Kind kind, double x, double y, double z, double rx, double ry, double low, int rgb) {}

    /** The parts' names that move, and how. */
    private static final Set<String> CHIMNEYS = Set.of("chimney", "stack", "stacks", "funnel", "flue");
    /** Fires that are open to the air: their embers go up. (A stove's, an oven's, burn behind a door.) */
    private static final Set<String> OPEN_FIRES = Set.of("cauldron", "smelter", "ironworks", "campfire", "brazier",
            "firepit", "bonfire");
    /** What smokes from its highest point (no chimney part of its own). */
    private static final Set<String> SMOKES = Set.of("stove", "smelter", "bread_oven");
    /** What steams from its highest point. */
    private static final Set<String> STEAMS = Set.of("boiler", "distiller");
    /** What has water leaping in it. */
    private static final Set<String> SPRAYS = Set.of("fountain", "drinking_fountain");
    /** The most chimneys one thing smokes from. */
    static final int MOST_CHIMNEYS = 4;

    private PropMotion() {}

    /** Whether {@code p} could have anything moving, by its parts' names and what it is - before its model is read. */
    public static boolean moves(BuildingProfile p) {
        if (p == null) return false;
        String key = p.key();
        if (SMOKES.contains(key) || STEAMS.contains(key) || SPRAYS.contains(key) || key.equals("beehive")) return true;
        for (BuildingProfile.Part part : p.parts()) if (kindOf(key, part.key()) != null) return true;
        return false;
    }

    /** What part {@code part} of {@code key} does, or null: still. */
    static Kind kindOf(String key, String part) {
        String k = part.toLowerCase(Locale.ROOT);
        if (k.equals("brew") || k.startsWith("potion")) return Kind.BUBBLES;
        if (k.equals("spark")) return Kind.ARCS;
        if (k.equals("fire")) return Kind.FIRE;
        if (k.equals("flame")) return Kind.FLICKER;
        if (CHIMNEYS.contains(k)) return Kind.SMOKE;
        if (k.equals("water") && SPRAYS.contains(key)) return Kind.SPRAY;
        if (k.startsWith("crystal")) return Kind.GLINTS;
        return null;
    }

    /** What moves on {@code p}, read off its model {@code m} (none without one). */
    public static List<Source> of(BuildingProfile p, BuildingModel m) {
        List<Source> out = new ArrayList<>();
        if (p == null || m == null) return out;
        float[] v = m.vertices();
        int corners = (m.fixedCorners() + m.turningCorners());
        List<BuildingProfile.Part> parts = p.parts();
        String key = p.key();
        for (int i = 0; i < parts.size(); i++) {
            BuildingProfile.Part part = parts.get(i);
            Kind kind = kindOf(key, part.key());
            if (kind == null) continue;
            List<double[]> pts = new ArrayList<>();
            for (int c = 0; c < corners; c++) {
                int o = c * BuildingModel.FLOATS;
                if ((int) (v[o + 14] + 0.5f) == i) pts.add(new double[]{v[o], v[o + 1], v[o + 2]});
            }
            if (pts.isEmpty()) continue;
            if (kind == Kind.SMOKE || kind == Kind.FLICKER) {
                // each chimney, each candle, by itself
                List<List<double[]>> groups = clusters(pts, kind == Kind.SMOKE ? 0.9 : 0.12);
                groups.sort((a, b) -> Double.compare(top(b), top(a)));
                int most = kind == Kind.SMOKE ? MOST_CHIMNEYS : 12;
                for (int g = 0; g < Math.min(most, groups.size()); g++) out.add(source(kind, groups.get(g), part.colour()));
            } else if (kind == Kind.SPRAY) {
                out.add(jet(source(kind, pts, part.colour()), v, corners));
            } else {
                out.add(source(kind, pts, part.colour()));
                if (kind == Kind.FIRE && OPEN_FIRES.contains(key)) out.add(source(Kind.EMBERS, pts, part.colour()));
            }
        }
        if (SMOKES.contains(key) || STEAMS.contains(key)) {
            List<double[]> all = new ArrayList<>();
            for (int c = 0; c < corners; c++) {
                int o = c * BuildingModel.FLOATS;
                all.add(new double[]{v[o], v[o + 1], v[o + 2]});
            }
            if (!all.isEmpty()) {
                List<double[]> crown = highest(all, 0.12);
                Kind k = STEAMS.contains(key) ? Kind.STEAM : Kind.SMOKE;
                out.add(source(k, crown, k == Kind.STEAM ? 0xF4F4F0 : 0x6E6A66));
            }
        }
        if (key.equals("beehive")) {
            out.add(new Source(Kind.BEES, 0, 0, m.top() * 0.6, 0.6, 0.6, 0, 0x2A2010));
        }
        return out;
    }

    /** {@code kind} off the top of {@code pts}: the middle of what is near their top, their spread, their foot. */
    private static Source source(Kind kind, List<double[]> pts, int rgb) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y0 = Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        for (double[] q : pts) {
            lo = Math.min(lo, q[2]);
            hi = Math.max(hi, q[2]);
            x0 = Math.min(x0, q[0]);
            x1 = Math.max(x1, q[0]);
            y0 = Math.min(y0, q[1]);
            y1 = Math.max(y1, q[1]);
        }
        // a fire glows from its middle; the rest come off the top
        double z = kind == Kind.FIRE || kind == Kind.GLINTS ? (lo + hi) / 2 : hi;
        return new Source(kind, (x0 + x1) / 2, (y0 + y1) / 2, z, Math.max(0.02, (x1 - x0) / 2),
                Math.max(0.02, (y1 - y0) / 2), lo, rgb & 0xFFFFFF);
    }

    /**
     * A fountain's water leaps from the highest thing standing over the middle
     * of it - its spout, its statue - and falls back to the water: {@code w}
     * (its water, top and spread) with its top raised to that and its foot the
     * water's top.
     */
    private static Source jet(Source w, float[] v, int corners) {
        double near = Math.min(0.4, Math.max(0.08, Math.min(w.rx(), w.ry()) * 0.3));
        double top = w.z();
        for (int c = 0; c < corners; c++) {
            int o = c * BuildingModel.FLOATS;
            if (Math.hypot(v[o] - w.x(), v[o + 1] - w.y()) <= near) top = Math.max(top, v[o + 2]);
        }
        return new Source(Kind.SPRAY, w.x(), w.y(), Math.max(top, w.z() + 0.05), w.rx(), w.ry(), w.z(), w.rgb());
    }

    /** The points within {@code within} metres of the highest. */
    private static List<double[]> highest(List<double[]> pts, double within) {
        double hi = top(pts);
        List<double[]> out = new ArrayList<>();
        for (double[] q : pts) if (q[2] >= hi - within) out.add(q);
        return out;
    }

    private static double top(List<double[]> pts) {
        double hi = -Double.MAX_VALUE;
        for (double[] q : pts) hi = Math.max(hi, q[2]);
        return hi;
    }

    /** {@code pts} in groups: two points closer than {@code gap} (across the ground) are of one. */
    static List<List<double[]>> clusters(List<double[]> pts, double gap) {
        int n = pts.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        // grid the points so only neighbours are compared
        java.util.Map<Long, List<Integer>> grid = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            double[] q = pts.get(i);
            long gx = (long) Math.floor(q[0] / gap), gy = (long) Math.floor(q[1] / gap);
            grid.computeIfAbsent(gx * 1_000_003L + gy, k -> new ArrayList<>()).add(i);
        }
        for (int i = 0; i < n; i++) {
            double[] q = pts.get(i);
            long gx = (long) Math.floor(q[0] / gap), gy = (long) Math.floor(q[1] / gap);
            for (long dx = -1; dx <= 1; dx++) {
                for (long dy = -1; dy <= 1; dy++) {
                    List<Integer> cell = grid.get((gx + dx) * 1_000_003L + gy + dy);
                    if (cell == null) continue;
                    for (int j : cell) {
                        if (j <= i) continue;
                        double[] r = pts.get(j);
                        if (Math.hypot(q[0] - r[0], q[1] - r[1]) < gap) union(parent, i, j);
                    }
                }
            }
        }
        java.util.Map<Integer, List<double[]>> groups = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(pts.get(i));
        return new ArrayList<>(groups.values());
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }
}
