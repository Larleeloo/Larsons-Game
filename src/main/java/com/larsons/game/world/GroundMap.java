package com.larsons.game.world;

/**
 * What the ground is made of, where: how much of each of the four ground
 * textures beside the grass covers each spot - {@link #DIRT} on the paths,
 * {@link #FOREST} (the forest floor) under the groves, {@link #GRAVEL} round
 * the rock beds, {@link #MEADOW} (grass thick with flowers) in open patches -
 * and the grass everywhere else.
 *
 * <p>A square grid of {@value #CELL} m cells, {@value #SIZE} metres a side
 * centred on the spawn, four weights a cell from 0 to 1, painted with soft
 * round shapes ({@link #disc}, {@link #stroke}) whose edge - weight 0.5 -
 * is where the shape ends. {@link GroundTexture} uploads it as an RGBA
 * texture (red dirt, green forest floor, blue gravel, alpha meadow) and the
 * ground's shader turns it into ragged, pixel-hard edges; off the grid it is
 * all grass. {@link SceneryLayout} paints the demo's.
 *
 * <p>Pure data - nothing here touches the GPU.
 */
public final class GroundMap {

    public static final int DIRT = 0, FOREST = 1, GRAVEL = 2, MEADOW = 3;
    /** The materials' names, as their textures are called in {@code assets/scenery/ground/}. */
    public static final String[] NAMES = {"dirt", "forest_floor", "gravel", "meadow"};
    /** Metres the map spans each way, centred on the origin. */
    public static final double SIZE = 128;
    /** Metres a cell. */
    public static final double CELL = 0.25;
    /** Cells a side. */
    public static final int CELLS = (int) Math.round(SIZE / CELL);
    /** How far a shape's edge fades, in metres either side of it (the shader roughens it). */
    public static final double SOFT = 0.35;

    /** Nothing but grass. */
    public static final GroundMap NONE = new GroundMap();

    private final float[] w = new float[CELLS * CELLS * 4];
    private boolean empty = true;

    /** The west and north edge of the grid, metres (x, z of cell 0's corner). */
    public static double origin() { return -SIZE / 2; }

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
        if (this == NONE) throw new UnsupportedOperationException("GroundMap.NONE stays grass");
        double pad = halfWidth + SOFT;
        int c0 = cell(Math.min(x0, x1) - pad), c1 = cell(Math.max(x0, x1) + pad);
        int r0 = cell(Math.min(z0, z1) - pad), r1 = cell(Math.max(z0, z1) + pad);
        double dx = x1 - x0, dz = z1 - z0, len2 = dx * dx + dz * dz;
        for (int r = Math.max(r0, 0); r <= Math.min(r1, CELLS - 1); r++) {
            for (int c = Math.max(c0, 0); c <= Math.min(c1, CELLS - 1); c++) {
                double px = origin() + (c + 0.5) * CELL, pz = origin() + (r + 0.5) * CELL;
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * dx + (pz - z0) * dz) / len2));
                double d = Math.hypot(px - (x0 + t * dx), pz - (z0 + t * dz));
                double v = Math.max(0, Math.min(1, 0.5 + (halfWidth - d) / (2 * SOFT)));
                int i = (r * CELLS + c) * 4 + material;
                if (v > w[i]) {
                    w[i] = (float) v;
                    empty = false;
                }
            }
        }
        return this;
    }

    private static int cell(double metres) {
        return (int) Math.floor((metres - origin()) / CELL);
    }

    /**
     * How much {@code material} covers {@code (x, z)}, 0 to 1, read between
     * the cells' centres as the shader's linear filtering reads it; 0 off the
     * grid. Above 0.5 it is that material.
     */
    public double weight(int material, double x, double z) {
        double fx = (x - origin()) / CELL - 0.5, fz = (z - origin()) / CELL - 0.5;
        int c = (int) Math.floor(fx), r = (int) Math.floor(fz);
        double tx = fx - c, tz = fz - r;
        return (1 - tz) * ((1 - tx) * at(material, c, r) + tx * at(material, c + 1, r))
                + tz * ((1 - tx) * at(material, c, r + 1) + tx * at(material, c + 1, r + 1));
    }

    private double at(int material, int c, int r) {
        if (c < 0 || r < 0 || c >= CELLS || r >= CELLS) return 0;
        return w[(r * CELLS + c) * 4 + material];
    }

    /** The material that shows at {@code (x, z)} before the shader's roughening: the last over 0.5, or -1 for grass. */
    public int material(double x, double z) {
        int shown = -1;
        for (int m : new int[]{MEADOW, FOREST, GRAVEL, DIRT}) if (weight(m, x, z) > 0.5) shown = m;
        return shown;
    }

    /** Whether nothing but grass has been painted. */
    public boolean isEmpty() { return empty; }

    /** The weights as RGBA bytes, row by row from the north-west corner (z rows, x columns), for the GPU. */
    public byte[] rgba() {
        byte[] out = new byte[w.length];
        for (int i = 0; i < w.length; i++) out[i] = (byte) Math.round(w[i] * 255);
        return out;
    }
}
