package com.larsons.game.gfx;

import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;

import java.util.Random;

/**
 * Builds low-poly, flat-shaded geometry out of primitives — boxes, prisms,
 * cylinders, cones, lumpy spheres — each with one flat colour. The same "every
 * surface is one flat colour" look as the engine's box models.
 *
 * <p>Every triangle gets its own face normal (vertices are not shared), which
 * is what makes the shading flat. Winding is counter-clockwise seen from
 * outside; normals are recomputed from the winding so a primitive cannot get
 * them backwards.
 */
public final class MeshBuilder {

    private float[] data = new float[4096];
    private int size;

    public Mesh build() {
        float[] exact = new float[size];
        System.arraycopy(data, 0, exact, 0, size);
        return new Mesh(exact);
    }

    /** Floats written so far (for tests). */
    public int floats() { return size; }

    public MeshBuilder triangle(Vec3 a, Vec3 b, Vec3 c, int rgb) {
        Vec3 n = b.sub(a).cross(c.sub(a)).normalize(Vec3.UP);
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, bl = (rgb & 0xFF) / 255f;
        vertex(a, n, r, g, bl);
        vertex(b, n, r, g, bl);
        vertex(c, n, r, g, bl);
        return this;
    }

    public MeshBuilder quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb) {
        triangle(a, b, c, rgb);
        return triangle(a, c, d, rgb);
    }

    private void vertex(Vec3 p, Vec3 n, float r, float g, float b) {
        if (size + Mesh.FLOATS_PER_VERTEX > data.length) {
            data = java.util.Arrays.copyOf(data, data.length * 2);
        }
        data[size++] = (float) p.x();
        data[size++] = (float) p.y();
        data[size++] = (float) p.z();
        data[size++] = (float) n.x();
        data[size++] = (float) n.y();
        data[size++] = (float) n.z();
        data[size++] = r;
        data[size++] = g;
        data[size++] = b;
    }

    /** A box of half-extents {@code (hx, hy, hz)}, placed by {@code t}. */
    public MeshBuilder box(Mat4 t, double hx, double hy, double hz, int rgb) {
        Vec3[] c = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            c[i] = t.transformPoint(new Vec3(
                    (i & 1) != 0 ? hx : -hx, (i & 2) != 0 ? hy : -hy, (i & 4) != 0 ? hz : -hz));
        }
        quad(c[1], c[3], c[7], c[5], rgb); // +x
        quad(c[4], c[6], c[2], c[0], rgb); // -x
        quad(c[2], c[6], c[7], c[3], rgb); // +y
        quad(c[0], c[1], c[5], c[4], rgb); // -y
        quad(c[4], c[5], c[7], c[6], rgb); // +z
        quad(c[0], c[2], c[3], c[1], rgb); // -z
        return this;
    }

    /**
     * A prism roof: a triangle cross-section in the XY plane extruded along Z,
     * ridge at the top. Width {@code 2hx}, height {@code h}, length {@code 2hz}.
     */
    public MeshBuilder roof(Mat4 t, double hx, double h, double hz, int rgb, int gableRgb) {
        Vec3 l0 = t.transformPoint(new Vec3(-hx, 0, -hz)), r0 = t.transformPoint(new Vec3(hx, 0, -hz));
        Vec3 l1 = t.transformPoint(new Vec3(-hx, 0, hz)), r1 = t.transformPoint(new Vec3(hx, 0, hz));
        Vec3 p0 = t.transformPoint(new Vec3(0, h, -hz)), p1 = t.transformPoint(new Vec3(0, h, hz));
        quad(l0, l1, p1, p0, rgb);        // left slope
        quad(r1, r0, p0, p1, rgb);        // right slope
        triangle(r0, l0, p0, gableRgb);   // back gable
        triangle(l1, r1, p1, gableRgb);   // front gable
        quad(l0, r0, r1, l1, rgb);        // underside
        return this;
    }

    /** An n-sided cylinder (or cone when {@code topRadius} is 0) standing on {@code base}. */
    public MeshBuilder cylinder(Vec3 base, double bottomRadius, double topRadius, double height,
                                int sides, int rgb) {
        Vec3 top = base.add(0, height, 0);
        for (int i = 0; i < sides; i++) {
            double a0 = Math.PI * 2 * i / sides, a1 = Math.PI * 2 * (i + 1) / sides;
            Vec3 b0 = base.add(Math.cos(a0) * bottomRadius, 0, Math.sin(a0) * bottomRadius);
            Vec3 b1 = base.add(Math.cos(a1) * bottomRadius, 0, Math.sin(a1) * bottomRadius);
            Vec3 t0 = top.add(Math.cos(a0) * topRadius, 0, Math.sin(a0) * topRadius);
            Vec3 t1 = top.add(Math.cos(a1) * topRadius, 0, Math.sin(a1) * topRadius);
            if (topRadius > 1e-6) {
                quad(b0, t0, t1, b1, rgb);
                triangle(top, t1, t0, rgb);
            } else {
                triangle(b0, top, b1, rgb);
            }
            triangle(base, b0, b1, rgb);
        }
        return this;
    }

    /**
     * A lumpy low-poly sphere: an icosahedron, subdivided once, each vertex
     * pushed in or out by up to {@code roughness}. Rocks and tree canopies.
     */
    public MeshBuilder blob(Vec3 centre, double rx, double ry, double rz, double roughness,
                            long seed, int rgb) {
        double t = (1 + Math.sqrt(5)) / 2;
        Vec3[] v = {
                new Vec3(-1, t, 0), new Vec3(1, t, 0), new Vec3(-1, -t, 0), new Vec3(1, -t, 0),
                new Vec3(0, -1, t), new Vec3(0, 1, t), new Vec3(0, -1, -t), new Vec3(0, 1, -t),
                new Vec3(t, 0, -1), new Vec3(t, 0, 1), new Vec3(-t, 0, -1), new Vec3(-t, 0, 1)};
        int[][] f = {
                {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
                {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
                {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        Random rnd = new Random(seed);
        java.util.Map<String, Vec3> jitter = new java.util.HashMap<>();
        for (int[] tri : f) {
            Vec3 a = v[tri[0]].normalize(), b = v[tri[1]].normalize(), c = v[tri[2]].normalize();
            Vec3 ab = a.add(b).normalize(), bc = b.add(c).normalize(), ca = c.add(a).normalize();
            Vec3[][] subs = {{a, ab, ca}, {ab, b, bc}, {ca, bc, c}, {ab, bc, ca}};
            for (Vec3[] s : subs) {
                Vec3[] p = new Vec3[3];
                for (int k = 0; k < 3; k++) {
                    Vec3 u = s[k];
                    String key = Math.round(u.x() * 1e4) + ":" + Math.round(u.y() * 1e4) + ":"
                            + Math.round(u.z() * 1e4);
                    Vec3 j = jitter.computeIfAbsent(key,
                            kk -> u.scale(1 + (rnd.nextDouble() * 2 - 1) * roughness));
                    p[k] = centre.add(j.x() * rx, j.y() * ry, j.z() * rz);
                }
                // Keep every face wound outward, whatever the jitter did.
                Vec3 n = p[1].sub(p[0]).cross(p[2].sub(p[0]));
                Vec3 out = p[0].add(p[1]).add(p[2]).scale(1 / 3.0).sub(centre);
                if (n.dot(out) >= 0) triangle(p[0], p[1], p[2], rgb);
                else triangle(p[0], p[2], p[1], rgb);
            }
        }
        return this;
    }
}
