package com.larsons.game.building;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A building's 3D model, from its {@code mesh.bin} (3D-Modeling's {@code
 * buildings/buildings.py mesh} writes it): each part's polygons in the
 * building's own metres - x to its right, y towards its back, z up - each
 * corner with where on its part's pattern it is; the turning part (a mill's
 * sails, its wheel) apart, with what it turns about.
 *
 * <p>Read into triangles ready to draw ({@link #FLOATS} a corner): its
 * point, its face's normal, its pattern place, and what the line round
 * each face needs - how far each corner is from each of its triangle's
 * edges, and which of those edges are drawn as a line: an edge where its
 * face turns a corner into the next (not where a round tower's faces meet,
 * nearly flat), meets another part, or ends.
 */
public final class BuildingModel {

    /** Floats a corner: point 3, normal 3, pattern 2, which corner 3, edge heights 3, part, edge lines. */
    public static final int FLOATS = 16;
    /** Faces that meet at less than this (the cosine between them) share no line: they read as one surface. */
    static final double FLAT = 0.78;

    private final float[] vertices;
    private final int fixed, turning;
    private final double[] pivot;
    private final char axis;
    private final double top;

    private BuildingModel(float[] vertices, int fixed, int turning, double[] pivot, char axis, double top) {
        this.vertices = vertices;
        this.fixed = fixed;
        this.turning = turning;
        this.pivot = pivot;
        this.axis = axis;
        this.top = top;
    }

    /** Every corner of every triangle: the fixed parts' first, then the turning part's. */
    public float[] vertices() { return vertices; }

    /** How many corners are of the fixed parts. */
    public int fixedCorners() { return fixed; }

    /** How many corners are of the turning part (after the fixed ones). */
    public int turningCorners() { return turning; }

    /** What the turning part turns about (its own metres), or null. */
    public double[] pivot() { return pivot == null ? null : pivot.clone(); }

    /** The axis it turns round: 'x', 'y' or 'z'. */
    public char axis() { return axis; }

    /** Its highest point, metres. */
    public double top() { return top; }

    /** {@code dir/mesh.bin}, or null when there is none. */
    public static BuildingModel load(Path dir) throws IOException {
        Path f = dir.resolve("mesh.bin");
        if (!Files.isRegularFile(f)) return null;
        return parse(Files.readAllBytes(f));
    }

    private record Poly(int part, boolean turns, double[][] corners) {}

    static BuildingModel parse(byte[] bytes) throws IOException {
        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 9 || in.get() != 'L' || in.get() != 'G' || in.get() != 'M' || in.get() != '1') {
            throw new IOException("not a building model");
        }
        int groups = in.getInt();
        List<Poly> polys = new ArrayList<>();
        try {
            for (int g = 0; g < groups; g++) {
                int part = in.get() & 255;
                boolean turns = in.get() != 0;
                int n = in.getInt();
                for (int i = 0; i < n; i++) {
                    int c = in.get() & 255;
                    double[][] corners = new double[c][5];
                    for (int k = 0; k < c; k++) for (int j = 0; j < 5; j++) corners[k][j] = in.getFloat();
                    polys.add(new Poly(part, turns, corners));
                }
            }
            double[] pivot = null;
            char axis = 'x';
            if (in.hasRemaining() && in.get() != 0) {
                pivot = new double[]{in.getFloat(), in.getFloat(), in.getFloat()};
                axis = (char) (in.get() & 255);
            }
            return build(polys, pivot, axis);
        } catch (java.nio.BufferUnderflowException e) {
            throw new IOException("a building model cut short");
        }
    }

    private static BuildingModel build(List<Poly> polys, double[] pivot, char axis) {
        // each face's normal, and which faces meet along each edge
        double[][] normals = new double[polys.size()][];
        Map<String, List<int[]>> edges = new HashMap<>();
        double top = 0;
        for (int p = 0; p < polys.size(); p++) {
            double[][] c = polys.get(p).corners();
            normals[p] = normal(c);
            for (int k = 0; k < c.length; k++) {
                top = Math.max(top, c[k][2]);
                edges.computeIfAbsent(key(c[k], c[(k + 1) % c.length]), x -> new ArrayList<>()).add(new int[]{p, k});
            }
        }
        List<float[]> fixed = new ArrayList<>(), turning = new ArrayList<>();
        for (int p = 0; p < polys.size(); p++) {
            Poly poly = polys.get(p);
            double[][] c = poly.corners();
            if (c.length < 3 || normals[p] == null) continue;
            boolean[] line = new boolean[c.length];
            for (int k = 0; k < c.length; k++) line[k] = drawn(p, polys, normals, edges.get(key(c[k], c[(k + 1) % c.length])));
            // a fan from its first corner: of each triangle, the edges that are the face's own
            for (int k = 1; k + 1 < c.length; k++) {
                double[][] t = {c[0], c[k], c[k + 1]};
                // edge i is the one across from corner i: (1-2) is the face's edge k, (2-0) the last one, (0-1) the first
                boolean e0 = line[k];
                boolean e1 = k + 1 == c.length - 1 && line[c.length - 1];
                boolean e2 = k == 1 && line[0];
                triangle(poly.turns() ? turning : fixed, t, normals[p], poly.part(), e0, e1, e2);
            }
        }
        int fixedCorners = fixed.size(), turningCorners = turning.size();
        float[] out = new float[(fixedCorners + turningCorners) * FLOATS];
        int o = 0;
        for (float[] v : fixed) {
            System.arraycopy(v, 0, out, o, FLOATS);
            o += FLOATS;
        }
        for (float[] v : turning) {
            System.arraycopy(v, 0, out, o, FLOATS);
            o += FLOATS;
        }
        return new BuildingModel(out, fixedCorners, turningCorners, pivot, axis, top);
    }

    /** Whether the edge {@code faces} share (face {@code p} one of them) is drawn as a line. */
    private static boolean drawn(int p, List<Poly> polys, double[][] normals, List<int[]> faces) {
        if (faces == null) return true;
        int others = 0;
        for (int[] f : faces) {
            if (f[0] == p) continue;
            others++;
            if (normals[f[0]] == null) continue;
            if (polys.get(f[0]).part() != polys.get(p).part()) return true;
            double[] a = normals[p], b = normals[f[0]];
            if (a[0] * b[0] + a[1] * b[1] + a[2] * b[2] < FLAT) return true;
        }
        return others != 1;                     // an open edge (or more than two faces on it): a line
    }

    private static void triangle(List<float[]> out, double[][] t, double[] n, int part, boolean e0, boolean e1,
                                 boolean e2) {
        // each corner's height over the edge across from it: twice the area over that edge's length
        double[] ab = sub(t[1], t[0]), ac = sub(t[2], t[0]);
        double area2 = len(cross(ab, ac));
        if (area2 < 1e-10) return;
        double h0 = area2 / Math.max(1e-9, len(sub(t[2], t[1])));
        double h1 = area2 / Math.max(1e-9, len(sub(t[0], t[2])));
        double h2 = area2 / Math.max(1e-9, len(sub(t[1], t[0])));
        float mask = (e0 ? 1 : 0) + (e1 ? 2 : 0) + (e2 ? 4 : 0);
        for (int i = 0; i < 3; i++) {
            float[] v = new float[FLOATS];
            v[0] = (float) t[i][0];
            v[1] = (float) t[i][1];
            v[2] = (float) t[i][2];
            v[3] = (float) n[0];
            v[4] = (float) n[1];
            v[5] = (float) n[2];
            v[6] = (float) t[i][3];
            v[7] = (float) t[i][4];
            v[8 + i] = 1;
            v[11] = (float) h0;
            v[12] = (float) h1;
            v[13] = (float) h2;
            v[14] = part;
            v[15] = mask;
            out.add(v);
        }
    }

    /** A polygon's normal (Newell's), or null for one with no area. */
    static double[] normal(double[][] c) {
        double nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < c.length; i++) {
            double[] a = c[i], b = c[(i + 1) % c.length];
            nx += (a[1] - b[1]) * (a[2] + b[2]);
            ny += (a[2] - b[2]) * (a[0] + b[0]);
            nz += (a[0] - b[0]) * (a[1] + b[1]);
        }
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return l < 1e-12 ? null : new double[]{nx / l, ny / l, nz / l};
    }

    private static String key(double[] a, double[] b) {
        long[] qa = q(a), qb = q(b);
        boolean swap = qa[0] > qb[0] || qa[0] == qb[0] && (qa[1] > qb[1] || qa[1] == qb[1] && qa[2] > qb[2]);
        long[] x = swap ? qb : qa, y = swap ? qa : qb;
        return x[0] + "," + x[1] + "," + x[2] + ";" + y[0] + "," + y[1] + "," + y[2];
    }

    /** A point to the millimetre (corners of faces that meet are the same corner). */
    private static long[] q(double[] p) {
        return new long[]{Math.round(p[0] * 1000), Math.round(p[1] * 1000), Math.round(p[2] * 1000)};
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double len(double[] a) {
        return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
    }
}
