package com.larsons.game.world;

import com.larsons.game.math.Vec3;

import java.util.List;

/**
 * The ground's height: flat at {@code y = 0} everywhere but inside one box,
 * where a heightmap - a grid of heights {@link #STEP} apart - raises hills
 * and cuts a hollow. Inside the box the ground is the grid's triangles, two a
 * cell, exactly as {@link TerrainRenderer} draws them, so where she stands is
 * where she is seen to stand; along the box's edges the heights are 0, so the
 * hills meet the endless flat ground ({@link VoidRenderer}) without a seam.
 *
 * <p>The demo's ({@link #highlands()}) stands in {@link SceneryLayout#HILLS},
 * north of the spawn, at the end of the north path:
 *
 * <ul>
 *   <li><b>the plateau</b>, a flat-topped rock {@value #PLATEAU_HEIGHT} m high
 *       - its west side a sheer <b>ledge</b> to jump down (a fall that hurts,
 *       or a roll that does not), its east side a sheer <b>cliff</b> hung with
 *       vines that she can <b>climb</b> ({@link #walls()}), its north and south
 *       sides rock too steep to walk;</li>
 *   <li><b>the ramp</b>, the long way up: the north path climbing its south
 *       side, steep enough to slow her ({@link #trail()});</li>
 *   <li><b>the hill</b> north-west of it, a round summit {@value #HILL_HEIGHT} m
 *       high, walkable to the top, slowly;</li>
 *   <li><b>the hollow</b> south-east of it, a bowl {@value #HOLLOW_DEPTH} m
 *       deep.</li>
 * </ul>
 *
 * <p>Pure data, no GL: the player walks on it ({@link Player}), the shots
 * stick in it, the scenery and the shadows stand on it.
 */
public final class Terrain {

    /** Metres between the grid's heights. */
    public static final double STEP = 0.5;
    /**
     * The steepest ground she can walk up (rise over run): 45°. Steeper
     * ground stops her unless it is a wall she can climb.
     */
    public static final double STEEP = 1.0;

    /** The plateau's top, the hill's summit and the hollow's floor, in metres. */
    public static final double PLATEAU_HEIGHT = 6.0, HILL_HEIGHT = 7.0, HOLLOW_DEPTH = 2.5;

    /** Flat ground everywhere: what a world stands on until it is given hills. */
    public static final Terrain FLAT = new Terrain(0, 0, 0, 0, new float[0], List.of(), List.of(), List.of());

    /**
     * A rock face she can climb: its foot runs from {@code (x0, z0)} to
     * {@code (x1, z1)} on the ground, it faces out along {@code (nx, nz)} (a
     * unit vector, away from the rock), and it rises from {@code bottom} to
     * {@code top} (metres above {@code y = 0}).
     */
    public record Wall(double x0, double z0, double x1, double z1, double nx, double nz, double bottom, double top) {

        /** Metres along its foot. */
        public double length() { return Math.hypot(x1 - x0, z1 - z0); }

        /** How far along its foot {@code (x, z)} is, metres from its first end (not clamped). */
        public double along(double x, double z) {
            double len = Math.max(1e-9, length());
            return ((x - x0) * (x1 - x0) + (z - z0) * (z1 - z0)) / len;
        }

        /** How far out from its foot {@code (x, z)} is, metres (negative: inside the rock). */
        public double out(double x, double z) {
            return (x - x0) * nx + (z - z0) * nz;
        }

        /** The point {@code along} metres along its foot and {@code out} metres out from it, on the ground plane. */
        public Vec3 at(double along, double out) {
            double len = Math.max(1e-9, length());
            double ux = (x1 - x0) / len, uz = (z1 - z0) / len;
            return new Vec3(x0 + ux * along + nx * out, 0, z0 + uz * along + nz * out);
        }

        /** The heading that faces into it. */
        public double facing() {
            return com.larsons.game.sprite.SpriteView.headingOf(-nx, -nz);
        }
    }

    /** A spot worth a name, for the demo's {@code goto}: where it is, standing on the ground. */
    public record Place(String name, double x, double z) {}

    private final double x0, z0;
    private final int nx, nz;
    private final float[] heights;
    private final List<Wall> walls;
    private final List<double[]> trail;
    private final List<Place> places;

    private Terrain(double x0, double z0, int nx, int nz, float[] heights, List<Wall> walls, List<double[]> trail,
                    List<Place> places) {
        this.x0 = x0;
        this.z0 = z0;
        this.nx = nx;
        this.nz = nz;
        this.heights = heights;
        this.walls = List.copyOf(walls);
        this.trail = List.copyOf(trail);
        this.places = List.copyOf(places);
    }

    // --- the demo's hills ------------------------------------------------------------

    /** The plateau's top: x from, x to, z from, z to. */
    static final double[] PLATEAU = {0, 12, -42, -30};
    /** The ramp up its south side: x from, x to (and a metre's shoulder either side); z from its foot to its top. */
    static final double[] RAMP = {1.5, 4.5, -19, -30};
    /** The hill's summit (x, z) and its radius; the hollow's middle and radius. */
    static final double[] HILL = {-9, -43, 13}, HOLLOW = {15.5, -24.5, 5.5};
    /** How wide the plateau's rock sides are: sheer (the ledge, the cliff), and the steep ones. */
    static final double SHEER = 0.5, ROCKY = 3.0;

    /**
     * The demo's highlands, in {@link SceneryLayout#HILLS}: the plateau with
     * its ledge, its vine cliff and its ramp, the hill and the hollow.
     */
    public static Terrain highlands() {
        double[] box = SceneryLayout.HILLS;
        double bx0 = box[0], bx1 = box[1], bz0 = box[2], bz1 = box[3];
        int cx = (int) Math.round((bx1 - bx0) / STEP) + 1, cz = (int) Math.round((bz1 - bz0) / STEP) + 1;
        float[] h = new float[cx * cz];
        for (int j = 0; j < cz; j++) {
            for (int i = 0; i < cx; i++) {
                double x = bx0 + i * STEP, z = bz0 + j * STEP;
                double edge = smooth(0, 2, x - bx0) * smooth(0, 2, bx1 - x) * smooth(0, 2, z - bz0)
                        * smooth(0, 2, bz1 - z);
                h[j * cx + i] = (float) (highlandsAt(x, z) * edge);
            }
        }
        // the vine cliff: the plateau's east face, from 2 m in from its north corner to 2 m from its south
        Wall cliff = new Wall(PLATEAU[1] + SHEER, PLATEAU[2] + 2, PLATEAU[1] + SHEER, PLATEAU[3] - 2, 1, 0, 0,
                PLATEAU_HEIGHT);
        List<double[]> trail = List.of(new double[]{(RAMP[0] + RAMP[1]) / 2, RAMP[2] + 1},
                new double[]{(RAMP[0] + RAMP[1]) / 2, RAMP[3]},
                new double[]{(RAMP[0] + RAMP[1]) / 2 + 1.5, RAMP[3] - 2.5});
        List<Place> places = List.of(
                new Place("hills", 3, -16),
                new Place("ramp", (RAMP[0] + RAMP[1]) / 2, RAMP[2] - 2),
                new Place("plateau", 7.5, -36),
                new Place("ledge", PLATEAU[0] + 0.8, -36),
                new Place("cliff", PLATEAU[1] + 2.2, -36),
                new Place("hill", HILL[0], HILL[1]),
                new Place("hollow", HOLLOW[0], HOLLOW[1]));
        return new Terrain(bx0, bz0, cx, cz, h, List.of(cliff), trail, places);
    }

    /** The highlands' height at {@code (x, z)}, before the grid (and the box's edges). */
    static double highlandsAt(double x, double z) {
        // the plateau: flat on top, each side its own fall - sheer west (the ledge) and east
        // (the cliff), steep rock north and south
        double fx = x < PLATEAU[0] ? 1 - smooth(0, SHEER, PLATEAU[0] - x)
                : x > PLATEAU[1] ? 1 - smooth(0, SHEER, x - PLATEAU[1]) : 1;
        double fz = z < PLATEAU[2] ? 1 - smooth(0, ROCKY, PLATEAU[2] - z)
                : z > PLATEAU[3] ? 1 - smooth(0, ROCKY, z - PLATEAU[3]) : 1;
        double plateau = PLATEAU_HEIGHT * fx * fz;
        // the ramp: straight up from its foot to the plateau's edge, a metre's shoulder either side
        double up = Math.max(0, Math.min(1, (RAMP[2] - z) / (RAMP[2] - RAMP[3])));
        double across = x < RAMP[0] ? 1 - smooth(0, 1, RAMP[0] - x) : x > RAMP[1] ? 1 - smooth(0, 1, x - RAMP[1]) : 1;
        double ramp = z > RAMP[2] ? 0 : PLATEAU_HEIGHT * up * across;
        // the hill: a round summit, its sides a cosine's
        double r = Math.hypot(x - HILL[0], z - HILL[1]) / HILL[2];
        double hill = r >= 1 ? 0 : HILL_HEIGHT * 0.5 * (1 + Math.cos(Math.PI * r));
        // the hollow
        double rh = Math.hypot(x - HOLLOW[0], z - HOLLOW[1]) / HOLLOW[2];
        double hollow = rh >= 1 ? 0 : -HOLLOW_DEPTH * 0.5 * (1 + Math.cos(Math.PI * rh));
        return Math.max(Math.max(plateau, ramp), hill) + hollow;
    }

    static double smooth(double e0, double e1, double v) {
        double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    // --- heights -----------------------------------------------------------------------

    /** Whether it is flat everywhere. */
    public boolean flat() { return heights.length == 0; }

    /**
     * The ground's height at {@code (x, z)}: 0 outside the box, inside it the
     * grid's triangle under the spot (each cell split from its north-east
     * corner to its south-west, as drawn).
     */
    public double height(double x, double z) {
        if (heights.length == 0) return 0;
        double gx = (x - x0) / STEP, gz = (z - z0) / STEP;
        if (gx < 0 || gz < 0 || gx > nx - 1 || gz > nz - 1) return 0;
        int i = Math.min(nx - 2, (int) gx), j = Math.min(nz - 2, (int) gz);
        double fx = gx - i, fz = gz - j;
        double h00 = at(i, j), h10 = at(i + 1, j), h01 = at(i, j + 1), h11 = at(i + 1, j + 1);
        if (fx + fz <= 1) return h00 + (h10 - h00) * fx + (h01 - h00) * fz;
        return h11 + (h01 - h11) * (1 - fx) + (h10 - h11) * (1 - fz);
    }

    public double height(Vec3 p) {
        return height(p.x(), p.z());
    }

    /** {@code p} put on the ground: its height the ground's there. */
    public Vec3 onGround(Vec3 p) {
        return new Vec3(p.x(), height(p.x(), p.z()), p.z());
    }

    /** The grid's height at column {@code i}, row {@code j}. */
    float at(int i, int j) {
        return heights[j * nx + i];
    }

    /**
     * Whether the grid's cell from column {@code i} to {@code i + 1} and row
     * {@code j} to {@code j + 1} is flat at {@code y = 0}: the flat ground's
     * own, which {@link TerrainRenderer} leaves to it.
     */
    boolean flatCell(int i, int j) {
        float m = Math.max(Math.max(Math.abs(at(i, j)), Math.abs(at(i + 1, j))),
                Math.max(Math.abs(at(i, j + 1)), Math.abs(at(i + 1, j + 1))));
        return m < 1e-4f;
    }

    /**
     * How steeply the ground rises at {@code (x, z)} going along {@code (dx,
     * dz)} (a unit vector), rise over run across half a metre: negative
     * downhill.
     */
    public double rise(double x, double z, double dx, double dz) {
        double d = 0.25;
        return (height(x + dx * d, z + dz * d) - height(x - dx * d, z - dz * d)) / (2 * d);
    }

    /** How steep the ground is at {@code (x, z)}, rise over run the steepest way. */
    public double slope(double x, double z) {
        return slope(x, z, 0.25);
    }

    /** How steep the very triangle under {@code (x, z)} is: a lip is not the face below it. */
    public double slopeUnder(double x, double z) {
        return slope(x, z, 0.02);
    }

    private double slope(double x, double z, double d) {
        double gx = (height(x + d, z) - height(x - d, z)) / (2 * d);
        double gz = (height(x, z + d) - height(x, z - d)) / (2 * d);
        return Math.hypot(gx, gz);
    }

    /** The ground's up-facing unit normal at {@code (x, z)}. */
    public Vec3 normal(double x, double z) {
        double d = 0.25;
        double gx = (height(x + d, z) - height(x - d, z)) / (2 * d);
        double gz = (height(x, z + d) - height(x, z - d)) / (2 * d);
        if (gx == 0 && gz == 0) return Vec3.UP;
        return new Vec3(-gx, 1, -gz).normalize(Vec3.UP);
    }

    // --- what is on it -----------------------------------------------------------------

    /** The faces she can climb. */
    public List<Wall> walls() { return walls; }

    /** The north path's way on up the ramp and onto the plateau, {x, z} points from its foot. */
    public List<double[]> trail() { return trail; }

    /** The named spots ({@code goto}). */
    public List<Place> places() { return places; }

    /** The named spot {@code name}, or null. */
    public Place place(String name) {
        for (Place p : places) if (p.name().equalsIgnoreCase(name)) return p;
        return null;
    }

    /**
     * The wall {@code (x, z)} is at the foot of, within {@code reach} metres
     * out from it and along its length: null when none.
     */
    public Wall wallAt(double x, double z, double reach) {
        for (Wall w : walls) {
            double out = w.out(x, z), along = w.along(x, z);
            if (out >= -0.05 && out <= reach && along >= 0 && along <= w.length()) return w;
        }
        return null;
    }

    /**
     * Where wall {@code w}'s rock face is at height {@code y}, {@code along}
     * metres along it: metres out from its foot (negative: in from it, as the
     * face leans back). At its top, its lip.
     */
    public double faceOut(Wall w, double along, double y) {
        double in = -4, out = 1;
        Vec3 a = w.at(along, in), b = w.at(along, out);
        if (height(a.x(), a.z()) < y) return in;
        if (height(b.x(), b.z()) >= y) return out;
        for (int i = 0; i < 24; i++) {
            double mid = (in + out) / 2;
            Vec3 p = w.at(along, mid);
            if (height(p.x(), p.z()) >= y) in = mid;
            else out = mid;
        }
        return (in + out) / 2;
    }

    /** Wall {@code w}'s lip {@code along} metres along it: metres out from its foot. */
    public double lipOut(Wall w, double along) {
        return faceOut(w, along, w.top() - 0.01);
    }

    // --- the grid, for drawing ---------------------------------------------------------

    /** The box's west (x) and north (z) edges; the grid's columns and rows. */
    public double originX() { return x0; }

    public double originZ() { return z0; }

    public int columns() { return nx; }

    public int rows() { return nz; }
}
