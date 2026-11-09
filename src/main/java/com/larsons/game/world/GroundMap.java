package com.larsons.game.world;

/**
 * What the ground is made of, where: how much of each of the ground textures
 * beside the grass covers each spot - {@link #DIRT} on the paths, {@link
 * #FOREST} (the forest floor) under the groves, {@link #GRAVEL} round the
 * rock beds, {@link #MEADOW} (grass thick with flowers) in open patches, and
 * the biomes' ground: {@link #SAND}, {@link #MUD}, {@link #SNOW}, {@link
 * #STONE}, the {@link #JUNGLE} floor, {@link #DRY_GRASS}, {@link #ASH} and
 * {@link #CRYSTAL} - and the grass everywhere else.
 *
 * <p>The weights are kept four materials to a group ({@link #GROUPS}), one
 * RGBA texture each on the GPU and one image each in a level's folder; a
 * group nothing of has been painted takes no memory.
 *
 * <p>A square grid of {@value #CELL} m cells - {@value #SIZE} metres a side
 * centred on the spawn for the demo, a level's own size for a level - four weights a cell from 0 to 1, painted with soft
 * round shapes ({@link #disc}, {@link #stroke}) whose edge - weight 0.5 -
 * is where the shape ends. {@link GroundTexture} uploads it as an RGBA
 * texture (red dirt, green forest floor, blue gravel, alpha meadow) and the
 * ground's shader turns it into ragged, pixel-hard edges; off the grid it is
 * all grass. {@link SceneryLayout} paints the demo's.
 *
 * <p>Pure data - nothing here touches the GPU.
 */
public final class GroundMap {

    public static final int DIRT = 0, FOREST = 1, GRAVEL = 2, MEADOW = 3, SAND = 4, MUD = 5, SNOW = 6, STONE = 7,
            JUNGLE = 8, DRY_GRASS = 9, ASH = 10, CRYSTAL = 11;
    /** The roads and paths: cobbles, flagstones, bricks, packed earth and gravel. */
    public static final int COBBLES = 12, FLAGSTONES = 13, BRICKS = 14, PACKED = 15;
    /** How many materials there are beside the grass. */
    public static final int MATERIALS = 16;
    /** How many groups of four they are kept in. */
    public static final int GROUPS = MATERIALS / 4;
    /**
     * The layers after the materials': where the rock of a face too steep to
     * walk is painted over ({@link #COVER}: its first channel - the ground
     * painted there, grass or a material, shows on the face instead of the
     * rock), and the ground's tint ({@link #TINT}: a colour and how much of
     * it, the editor's tint brush).
     */
    public static final int COVER = GROUPS, TINT = GROUPS + 1;
    /** Every layer it keeps: the materials' groups, the cover and the tint. */
    public static final int LAYERS = GROUPS + 2;
    /** The materials' names, as their textures are called in {@code assets/scenery/ground/}. */
    public static final String[] NAMES = {"dirt", "forest_floor", "gravel", "meadow", "sand", "mud", "snow",
            "stone", "jungle_floor", "dry_grass", "ash", "crystal", "cobbles", "flagstones", "brick_path",
            "packed_path"};
    /**
     * Which lies over which where two meet, the bottom first: the biomes'
     * ground, then the meadow, the forest floor, the gravel and the paths'
     * dirt over everything.
     */
    public static final int[] ORDER = {DRY_GRASS, JUNGLE, SAND, MUD, ASH, STONE, SNOW, CRYSTAL, MEADOW, FOREST,
            GRAVEL, DIRT, PACKED, BRICKS, FLAGSTONES, COBBLES};
    /** Metres the demo's map spans each way, centred on the origin. */
    public static final double SIZE = 128;
    /** Metres a cell. */
    public static final double CELL = 0.25;
    /** Cells a side of the demo's map. */
    public static final int CELLS = (int) Math.round(SIZE / CELL);
    /** How far a shape's edge fades, in metres either side of it (the shader roughens it). */
    public static final double SOFT = 0.35;

    /** Nothing but grass. */
    public static final GroundMap NONE = new GroundMap();

    private final int cells;
    private final double origin, cell;
    /** The weights, a group of four materials an array (null: nothing of them painted), four a cell. */
    private final float[][] w = new float[LAYERS][];
    private final boolean[] empty = filled(LAYERS);
    private int version;
    private final int[] groupVersion = new int[LAYERS];
    /** Whether the brushes paint squares rather than discs ({@link #square(boolean)}). */
    private boolean square;

    private static boolean[] filled(int n) {
        boolean[] b = new boolean[n];
        java.util.Arrays.fill(b, true);
        return b;
    }

    /** The demo's map: {@value #SIZE} m a side, all grass. */
    public GroundMap() {
        this(SIZE);
    }

    /** A map {@code size} metres a side (rounded to whole cells), centred on the origin, all grass. */
    public GroundMap(double size) {
        cell = cellFor(size);
        cells = Math.max(1, (int) Math.round(size / cell));
        origin = -cells * cell / 2;
        w[0] = new float[cells * cells * 4];
    }

    private float[] group(int g) {
        if (w[g] == null) w[g] = new float[cells * cells * 4];
        return w[g];
    }

    /** The west and north edge of the demo's grid, metres (x, z of cell 0's corner). */
    public static double origin() { return -SIZE / 2; }

    /** The west and north edge of this grid, metres (x, z of cell 0's corner). */
    public double west() { return origin; }

    /** Metres this map spans each way. */
    public double size() { return cells * cell; }

    /** Metres a cell of this map. */
    public double cell() { return cell; }

    /**
     * Metres a cell of a map {@code size} metres a side: {@value #CELL} up to
     * 256 m, and coarser the bigger it is past that (a 2048 m forest's
     * cells 2 m), so a big level's map stays about a thousand cells a side.
     */
    public static double cellFor(double size) {
        if (size <= 256) return CELL;
        if (size <= 512) return 0.5;
        if (size <= 1024) return 1.0;
        return 2.0;
    }

    /** Cells a side. */
    public int cells() { return cells; }

    /**
     * How many times it has been painted on: what is drawn of it is brought
     * up to date when this changes ({@link GroundTexture#use}).
     */
    public int version() { return version; }

    /** How many times group {@code g} has been changed: its texture is uploaded again when this changes. */
    public int version(int g) { return groupVersion[g]; }

    /** Cover a round patch of {@code radius} metres round {@code (x, z)} with {@code material}. */
    public GroundMap disc(int material, double x, double z, double radius) {
        return stroke(material, x, z, x, z, radius);
    }

    /**
     * Cover the ground within {@code halfWidth} metres of the segment from
     * {@code (x0, z0)} to {@code (x1, z1)} with {@code material} - a stretch
     * of path, its ends round.
     */
    public GroundMap stroke(int material, double x0, double z0, double x1, double z1, double halfWidth) {
        int g = material / 4, ch = material % 4;
        float[] a = this == NONE ? null : group(g);
        groupVersion[g]++;
        return shape(x0, z0, x1, z1, halfWidth, (i, v) -> {
            if (v > a[i + ch]) {
                a[i + ch] = (float) v;
                empty[g] = false;
            }
        });
    }

    /**
     * Paint a round patch of {@code radius} metres round {@code (x, z)} as
     * the level editor's brush does: {@code material} over whatever was
     * there - the others taken away where it now shows - or, for {@code
     * material} -1, back to grass. {@code strength} (0 to 1) is how much of
     * the way a stroke goes at once.
     */
    public GroundMap paint(int material, double x, double z, double radius, double strength) {
        return paint((px, pz) -> material, x, z, radius, strength);
    }

    /** Paint squares ({@code true}: {@code radius} metres to each side) rather than discs from now on. */
    public GroundMap square(boolean on) {
        square = on;
        return this;
    }

    /** How soft the brushes' edges are, 0 (as hard as the cells let it) – 1 (soft from the middle out). */
    private double feather;
    /** Where the brushes paint: the editor's filters. */
    private BrushMask mask = BrushMask.ALL;

    /** Feather the brushes' edges {@code f} (0 – 1) from now on. */
    public GroundMap feather(double f) {
        feather = Math.max(0, Math.min(1, f));
        return this;
    }

    /** Keep the brushes to where {@code m} says from now on (null: everywhere). */
    public GroundMap mask(BrushMask m) {
        mask = m == null ? BrushMask.ALL : m;
        return this;
    }

    /**
     * Paint over the rock round {@code (x, z)} ({@code on}): where the ground
     * is too steep to walk the ground painted there shows - grass, or a
     * material - instead of bare rock; or ({@code !on}) bare it again.
     */
    public GroundMap cover(double x, double z, double radius, double strength, boolean on) {
        double k = Math.max(0, Math.min(1, strength));
        float[] a = group(COVER);
        groupVersion[COVER]++;
        empty[COVER] = false;
        return shape(x, z, x, z, radius, (i, v) -> {
            double now = on ? Math.min(1, a[i] + v * k) : Math.max(0, a[i] - v * k);
            a[i] = (float) now;
        });
    }

    /**
     * Tint the ground round {@code (x, z)} {@code rgb} ({@code 0xRRGGBB}) -
     * {@code amount} (0 to 1) of the way to it, at the brush's middle - or,
     * {@code rgb} -1, take the tint off.
     */
    public GroundMap tint(double x, double z, double radius, double strength, int rgb, double amount) {
        double k = Math.max(0, Math.min(1, strength));
        float[] a = group(TINT);
        groupVersion[TINT]++;
        empty[TINT] = false;
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        return shape(x, z, x, z, radius, (i, v) -> {
            double t = Math.min(1, v * k * 1.5);
            if (rgb < 0) {
                a[i + 3] = (float) Math.max(0, a[i + 3] - t);
                return;
            }
            a[i] = (float) (a[i] + (r - a[i]) * t);
            a[i + 1] = (float) (a[i + 1] + (g - a[i + 1]) * t);
            a[i + 2] = (float) (a[i + 2] + (b - a[i + 2]) * t);
            a[i + 3] = (float) (a[i + 3] + (amount - a[i + 3]) * t);
        });
    }

    /** How much the rock is painted over at {@code (x, z)}, 0 to 1. */
    public double cover(double x, double z) {
        float[] a = w[COVER];
        if (a == null) return 0;
        int c = cell(x), r = cell(z);
        if (c < 0 || r < 0 || c >= cells || r >= cells) return 0;
        return a[(r * cells + c) * 4];
    }

    /** What a brush paints where: the material at {@code (x, z)}, or -1 for grass. */
    @FunctionalInterface
    public interface Pattern {
        int at(double x, double z);
    }

    /**
     * {@link #paint(int, double, double, double, double)} with a material of
     * its own at each spot, as {@code pattern} says - the biome brush's mix
     * of a ground and patches of another.
     */
    public GroundMap paint(Pattern pattern, double x, double z, double radius, double strength) {
        double k = Math.max(0, Math.min(1, strength));
        shape(x, z, x, z, radius, (i, v) -> {
            int ci = i / 4;
            int material = pattern.at(origin + (ci % cells + 0.5) * cell, origin + (ci / cells + 0.5) * cell);
            if (material >= 0) group(material / 4);
            double a = v * k;
            for (int g = 0; g < GROUPS; g++) {
                float[] arr = w[g];
                if (arr == null) continue;
                for (int c = 0; c < 4; c++) {
                    int m = g * 4 + c;
                    float old = arr[i + c];
                    float now = (float) (m == material ? Math.min(1, old + a) : Math.max(0, old - a));
                    if (now != old) {
                        arr[i + c] = now;
                        if (now > 0) empty[g] = false;
                    }
                }
            }
        });
        for (int g = 0; g < GROUPS; g++) if (w[g] != null) groupVersion[g]++;
        return this;
    }

    private interface Cell {
        void cover(int index, double weight);
    }

    /** Hand every cell within {@code halfWidth} metres of the segment (its soft edge too) its weight. */
    private GroundMap shape(double x0, double z0, double x1, double z1, double halfWidth, Cell cell) {
        if (this == NONE) throw new UnsupportedOperationException("GroundMap.NONE stays grass");
        double pad = halfWidth + SOFT;
        int c0 = cell(Math.min(x0, x1) - pad), c1 = cell(Math.max(x0, x1) + pad);
        int r0 = cell(Math.min(z0, z1) - pad), r1 = cell(Math.max(z0, z1) + pad);
        double dx = x1 - x0, dz = z1 - z0, len2 = dx * dx + dz * dz;
        for (int r = Math.max(r0, 0); r <= Math.min(r1, cells - 1); r++) {
            for (int c = Math.max(c0, 0); c <= Math.min(c1, cells - 1); c++) {
                double px = origin + (c + 0.5) * this.cell, pz = origin + (r + 0.5) * this.cell;
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * dx + (pz - z0) * dz) / len2));
                double d = square ? Math.max(Math.abs(px - (x0 + t * dx)), Math.abs(pz - (z0 + t * dz)))
                        : Math.hypot(px - (x0 + t * dx), pz - (z0 + t * dz));
                double v = Math.max(0, Math.min(1, 0.5 + (halfWidth - d) / (2 * SOFT)));
                if (feather > 0 && v > 0) {
                    // feathered: full only within (1 - feather) of the way out, then falling away smoothly
                    double inner = halfWidth * (1 - feather), outer = halfWidth + SOFT;
                    double u = Math.max(0, Math.min(1, (d - inner) / Math.max(1e-9, outer - inner)));
                    v = Math.min(v, 1 - u * u * (3 - 2 * u));
                }
                if (v > 0 && mask != BrushMask.ALL) v *= mask.at(px, pz);
                if (v > 0) cell.cover((r * cells + c) * 4, v);
            }
        }
        version++;
        return this;
    }

    private int cell(double metres) {
        return (int) Math.floor((metres - origin) / cell);
    }

    /**
     * How much {@code material} covers {@code (x, z)}, 0 to 1, read between
     * the cells' centres as the shader's linear filtering reads it; 0 off the
     * grid. Above 0.5 it is that material.
     */
    public double weight(int material, double x, double z) {
        double fx = (x - origin) / cell - 0.5, fz = (z - origin) / cell - 0.5;
        int c = (int) Math.floor(fx), r = (int) Math.floor(fz);
        double tx = fx - c, tz = fz - r;
        return (1 - tz) * ((1 - tx) * at(material, c, r) + tx * at(material, c + 1, r))
                + tz * ((1 - tx) * at(material, c, r + 1) + tx * at(material, c + 1, r + 1));
    }

    private double at(int material, int c, int r) {
        if (c < 0 || r < 0 || c >= cells || r >= cells) return 0;
        float[] a = w[material / 4];
        return a == null ? 0 : a[(r * cells + c) * 4 + material % 4];
    }

    /** The material that shows at {@code (x, z)} before the shader's roughening: the top one over 0.5, or -1 for grass. */
    public int material(double x, double z) {
        int shown = -1;
        for (int m : ORDER) if (w[m / 4] != null && weight(m, x, z) > 0.5) shown = m;
        return shown;
    }

    /** Whether nothing but grass has been painted. */
    public boolean isEmpty() {
        for (boolean e : empty) if (!e) return false;
        return true;
    }

    /** Whether nothing of group {@code g}'s four materials has been painted. */
    public boolean isEmpty(int g) { return empty[g]; }

    /** The first four materials' weights as RGBA bytes ({@link #rgba(int)} of group 0). */
    public byte[] rgba() {
        return rgba(0);
    }

    /**
     * Group {@code g}'s weights as RGBA bytes - red its first material, green
     * its second, blue its third, alpha its fourth -, row by row from the
     * north-west corner (z rows, x columns), for the GPU and a level's file.
     */
    public byte[] rgba(int g) {
        byte[] out = new byte[cells * cells * 4];
        float[] a = w[g];
        if (a != null) for (int i = 0; i < out.length; i++) out[i] = (byte) Math.round(a[i] * 255);
        return out;
    }

    /** Set the first four materials' weights from {@code rgba} ({@link #setRgba(int, byte[])} of group 0). */
    public GroundMap setRgba(byte[] rgba) {
        return setRgba(0, rgba);
    }

    /** Set group {@code g}'s weights from {@code rgba} - {@link #rgba(int)}'s bytes; null: none of them. */
    public GroundMap setRgba(int g, byte[] rgba) {
        empty[g] = true;
        version++;
        groupVersion[g]++;
        if (rgba == null) {
            if (g == 0) java.util.Arrays.fill(w[0], 0);
            else w[g] = null;
            return this;
        }
        float[] a = group(g);
        if (rgba.length != a.length) throw new IllegalArgumentException(rgba.length + " bytes for " + a.length);
        for (int i = 0; i < a.length; i++) {
            a[i] = (rgba[i] & 0xFF) / 255f;
            if (a[i] > 0) empty[g] = false;
        }
        return this;
    }

    /** Every layer's bytes (null for one never painted), to put back with {@link #restore}: an undo step. */
    public byte[][] snapshot() {
        byte[][] out = new byte[LAYERS][];
        for (int g = 0; g < LAYERS; g++) out[g] = w[g] == null || empty[g] && g > 0 ? null : rgba(g);
        return out;
    }

    /** Put back what {@link #snapshot} kept. */
    public GroundMap restore(byte[][] groups) {
        for (int g = 0; g < LAYERS; g++) setRgba(g, g < groups.length ? groups[g] : null);
        return this;
    }

    /** A copy to paint on without changing this one. */
    public GroundMap copy() {
        GroundMap m = new GroundMap(size());
        for (int g = 0; g < LAYERS; g++) {
            if (w[g] != null) m.w[g] = w[g].clone();
            m.empty[g] = empty[g];
        }
        return m;
    }

    /** {@code other}'s weights, put over the middle of this map (what lies outside either is left out). */
    public GroundMap copyFrom(GroundMap other) {
        int shift = (int) Math.round((other.origin - origin) / cell);
        for (int g = 0; g < LAYERS; g++) {
            empty[g] = true;
            groupVersion[g]++;
            if (other.w[g] == null) {
                if (g == 0) java.util.Arrays.fill(w[0], 0);
                else w[g] = null;
                continue;
            }
            float[] a = group(g), b = other.w[g];
            java.util.Arrays.fill(a, 0);
            if (other.cell != cell) {
                // cells of another size (a level grown past 256 m, or shrunk back): each cell takes the one under it
                for (int r = 0; r < cells; r++) {
                    int rr = (int) Math.floor((origin + (r + 0.5) * cell - other.origin) / other.cell);
                    if (rr < 0 || rr >= other.cells) continue;
                    for (int c = 0; c < cells; c++) {
                        int cc = (int) Math.floor((origin + (c + 0.5) * cell - other.origin) / other.cell);
                        if (cc < 0 || cc >= other.cells) continue;
                        for (int m = 0; m < 4; m++) {
                            float v = b[(rr * other.cells + cc) * 4 + m];
                            a[(r * cells + c) * 4 + m] = v;
                            if (v > 0) empty[g] = false;
                        }
                    }
                }
                continue;
            }
            for (int r = 0; r < other.cells; r++) {
                int rr = r + shift;
                if (rr < 0 || rr >= cells) continue;
                for (int c = 0; c < other.cells; c++) {
                    int cc = c + shift;
                    if (cc < 0 || cc >= cells) continue;
                    for (int m = 0; m < 4; m++) {
                        float v = b[(r * other.cells + c) * 4 + m];
                        a[(rr * cells + cc) * 4 + m] = v;
                        if (v > 0) empty[g] = false;
                    }
                }
            }
        }
        version++;
        return this;
    }
}
