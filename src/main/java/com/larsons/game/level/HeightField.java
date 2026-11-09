package com.larsons.game.level;

import com.larsons.game.world.Terrain;

import java.util.Arrays;
import java.util.List;

/**
 * A level's ground: a height every {@link Terrain#STEP} metres over the
 * whole square of the level, centred on the origin - flat at {@code y = 0}
 * until it is sculpted - and the brushes the editor sculpts it with: raise,
 * lower, smooth, flatten.
 *
 * <p>Its edge stays at {@code y = 0}, where it meets the endless flat
 * ground round the level; nothing is higher or deeper than {@link #LIMIT}.
 * {@link #terrain} makes the {@link Terrain} she walks on and the hills are
 * drawn from.
 */
public final class HeightField {

    /** The highest and lowest the ground can be sculpted, metres from {@code y = 0}. */
    public static final double LIMIT = 100;

    private final int n;
    private final double origin, step;
    private final float[] h;
    private int version;

    /** Flat ground over a square {@code size} metres a side, centred on the origin. */
    public HeightField(double size) {
        step = stepFor(size);
        n = Math.max(2, (int) Math.round(size / step) + 1);
        origin = -(n - 1) * step / 2;
        h = new float[n * n];
    }

    /**
     * Metres between the heights of a level {@code size} metres a side:
     * {@link Terrain#STEP} up to 256 m, and coarser the bigger it is past
     * that - about a thousand heights a side at most (a 2048 m forest's 2 m)
     * - so a big level's ground is as quick to sculpt, to keep and to draw.
     */
    public static double stepFor(double size) {
        if (size <= 256) return Terrain.STEP;
        if (size <= 512) return 0.5;
        if (size <= 1024) return 1.0;
        return 2.0;
    }

    /** Metres between its heights. */
    public double step() { return step; }

    /** Heights a side. */
    public int points() { return n; }

    /** Metres the field spans each way. */
    public double size() { return (n - 1) * step; }

    /** Its west and north edge (x, z of its first point). */
    public double west() { return origin; }

    /** How many times it has been sculpted (the editor rebuilds the ground when this changes). */
    public int version() { return version; }

    /** The height at grid point {@code (i, j)}. */
    public float at(int i, int j) {
        return h[j * n + i];
    }

    /** Set grid point {@code (i, j)}: within {@link #LIMIT}, and 0 on the edge. */
    public void set(int i, int j, double v) {
        if (i <= 0 || j <= 0 || i >= n - 1 || j >= n - 1) return;
        h[j * n + i] = (float) Math.max(-LIMIT, Math.min(LIMIT, v));
    }

    /** Whether it is flat at {@code y = 0} everywhere. */
    public boolean flat() {
        for (float v : h) if (v != 0) return false;
        return true;
    }

    /** The heights, row by row from the north-west corner (a copy). */
    public float[] heights() { return h.clone(); }

    /** Every height from {@code heights} ({@link #heights}'s order); the edge is put back to 0. */
    public HeightField setHeights(float[] heights) {
        if (heights.length != h.length) throw new IllegalArgumentException(heights.length + " heights for " + h.length);
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) set(i, j, heights[j * n + i]);
        version++;
        return this;
    }

    /** {@code other}'s heights over the middle of this field (what lies outside either is left out). */
    public HeightField copyFrom(HeightField other) {
        Arrays.fill(h, 0);
        if (other.step != step) {
            // a grid of another spacing (a level grown past 256 m, or shrunk back): its heights where these are
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) set(i, j, other.height(origin + i * step, origin + j * step));
            }
            version++;
            return this;
        }
        int shift = (int) Math.round((other.origin - origin) / step);
        for (int j = 0; j < other.n; j++) {
            for (int i = 0; i < other.n; i++) set(i + shift, j + shift, other.at(i, j));
        }
        version++;
        return this;
    }

    public HeightField copy() {
        HeightField c = new HeightField(size());
        System.arraycopy(h, 0, c.h, 0, h.length);
        return c;
    }

    /** The ground she walks on: these heights, with {@code walls} to climb and {@code places} with names. */
    public Terrain terrain(List<Terrain.Wall> walls, List<Terrain.Place> places) {
        return Terrain.of(origin, origin, n, n, step, h, walls, places);
    }

    // --- the brushes ---------------------------------------------------------------------

    /** A brush's weight {@code d} metres from its middle, {@code radius} metres round: 1 to 0, smoothly. */
    static double falloff(double d, double radius) {
        return falloff(d, radius, 1);
    }

    /**
     * {@link #falloff(double, double)} feathered {@code feather} (0 – 1) of
     * the way in: full out to {@code (1 - feather)} of the radius, falling
     * off smoothly from there to the rim - 1 soft all the way from its
     * middle, 0 a hard edge (a step, a cut).
     */
    static double falloff(double d, double radius, double feather) {
        if (d >= radius) return 0;
        double f = Math.max(0, Math.min(1, feather));
        double inner = radius * (1 - f);
        if (d <= inner) return 1;
        double t = (d - inner) / Math.max(1e-9, radius - inner);
        return 1 - t * t * (3 - 2 * t);
    }

    /**
     * A square brush's weight {@code (dx, dz)} metres from its middle, {@code
     * radius} metres to each side: full over the square but for a rim a
     * fifth of it (at least a grid step) where it falls off - so a square
     * plateau, a square pit, has sides, not slopes.
     */
    static double squareFalloff(double dx, double dz, double radius, double step) {
        return squareFalloff(dx, dz, radius, step, 1);
    }

    /** {@link #squareFalloff(double, double, double, double)} with its rim {@code feather} (0 – 1) of a fifth of it. */
    static double squareFalloff(double dx, double dz, double radius, double step, double feather) {
        double d = Math.max(Math.abs(dx), Math.abs(dz));
        if (d >= radius) return 0;
        double rim = Math.max(step, radius * 0.2 * Math.max(0, Math.min(1, feather)));
        rim = Math.min(rim, radius);
        if (d <= radius - rim) return 1;
        double t = (d - (radius - rim)) / rim;
        return 1 - t * t * (3 - 2 * t);
    }

    /** Whether the brushes are square ({@link #squareFalloff}) rather than round. */
    private boolean square;

    /** Sculpt with a square brush ({@code true}) or a round one from now on. */
    public HeightField square(boolean on) {
        square = on;
        return this;
    }

    /** How soft the brushes' edges are, 0 (hard) – 1 (soft from the middle out): {@link #falloff(double, double, double)}. */
    private double feather = 1;
    /** Where the brushes work: the editor's filters. */
    private com.larsons.game.world.BrushMask mask = com.larsons.game.world.BrushMask.ALL;

    /** Feather the brushes' edges {@code f} (0 – 1) from now on. */
    public HeightField feather(double f) {
        feather = Math.max(0, Math.min(1, f));
        return this;
    }

    /** Keep the brushes to where {@code m} says from now on (null: everywhere). */
    public HeightField mask(com.larsons.game.world.BrushMask m) {
        mask = m == null ? com.larsons.game.world.BrushMask.ALL : m;
        return this;
    }

    /** What a brush does to one height: given it and the brush's weight there, the new height. */
    private interface Stroke {
        double apply(int i, int j, double height, double weight);
    }

    private void brush(double x, double z, double radius, Stroke stroke) {
        int i0 = (int) Math.floor((x - radius - origin) / step), i1 = (int) Math.ceil((x + radius - origin) / step);
        int j0 = (int) Math.floor((z - radius - origin) / step), j1 = (int) Math.ceil((z + radius - origin) / step);
        float[] before = null;
        for (int j = Math.max(1, j0); j <= Math.min(n - 2, j1); j++) {
            for (int i = Math.max(1, i0); i <= Math.min(n - 2, i1); i++) {
                double px = origin + i * step, pz = origin + j * step;
                double w = square ? squareFalloff(px - x, pz - z, radius, step, feather)
                        : falloff(Math.hypot(px - x, pz - z), radius, feather);
                if (w <= 0) continue;
                if (mask != com.larsons.game.world.BrushMask.ALL) w *= mask.at(px, pz);
                if (w <= 0) continue;
                if (before == null) before = h.clone();
                set(i, j, stroke.apply(i, j, before[j * n + i], w));
            }
        }
        if (before != null) version++;
    }

    /** Raise the ground round {@code (x, z)} by up to {@code metres} (negative: lower it). */
    public void raise(double x, double z, double radius, double metres) {
        brush(x, z, radius, (i, j, v, w) -> v + metres * w);
    }

    /** Ease the ground round {@code (x, z)} towards its neighbours' average, {@code strength} 0 to 1 of the way. */
    public void smooth(double x, double z, double radius, double strength) {
        float[] was = h.clone();
        brush(x, z, radius, (i, j, v, w) -> {
            double sum = 0;
            int count = 0;
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    int a = i + di, b = j + dj;
                    if (a < 0 || b < 0 || a >= n || b >= n) continue;
                    sum += was[b * n + a];
                    count++;
                }
            }
            return v + (sum / count - v) * Math.min(1, strength) * w;
        });
    }

    /** Bring the ground round {@code (x, z)} towards {@code height}, {@code strength} 0 to 1 of the way. */
    public void flatten(double x, double z, double radius, double height, double strength) {
        brush(x, z, radius, (i, j, v, w) -> v + (height - v) * Math.min(1, strength) * w);
    }

    /** The height at {@code (x, z)}, between the grid's points as {@link Terrain#height} has it. */
    public double height(double x, double z) {
        double gx = (x - origin) / step, gz = (z - origin) / step;
        if (gx < 0 || gz < 0 || gx > n - 1 || gz > n - 1) return 0;
        int i = Math.min(n - 2, (int) gx), j = Math.min(n - 2, (int) gz);
        double fx = gx - i, fz = gz - j;
        double h00 = at(i, j), h10 = at(i + 1, j), h01 = at(i, j + 1), h11 = at(i + 1, j + 1);
        if (fx + fz <= 1) return h00 + (h10 - h00) * fx + (h01 - h00) * fz;
        return h11 + (h01 - h11) * (1 - fx) + (h10 - h11) * (1 - fz);
    }
}
