package com.larsons.game.math;

/**
 * What a camera can see, as the five planes round its view (left, right,
 * bottom, top and near - not the far one: what is far is fogged, not
 * gone): asked whether a thing may be on screen before the work of drawing
 * it is done. It never says no to something that is in view; it may say
 * yes to something just outside a corner.
 */
public final class Frustum {

    /** Each plane as (a, b, c, d) with (a, b, c) a unit vector pointing into the view. */
    private final double[][] planes = new double[5][4];

    /** The view of {@code viewProj} (a projection times a view, as the shaders are given). */
    public Frustum(Mat4 viewProj) {
        double[] row0 = row(viewProj, 0), row1 = row(viewProj, 1), row2 = row(viewProj, 2), row3 = row(viewProj, 3);
        set(0, row3, row0, 1);          // left:    w + x >= 0
        set(1, row3, row0, -1);         // right:   w - x >= 0
        set(2, row3, row1, 1);          // bottom:  w + y >= 0
        set(3, row3, row1, -1);         // top:     w - y >= 0
        set(4, row3, row2, 1);          // near:    w + z >= 0
    }

    private static double[] row(Mat4 m, int r) {
        return new double[]{m.at(r, 0), m.at(r, 1), m.at(r, 2), m.at(r, 3)};
    }

    private void set(int i, double[] w, double[] v, double sign) {
        double a = w[0] + sign * v[0], b = w[1] + sign * v[1], c = w[2] + sign * v[2], d = w[3] + sign * v[3];
        double len = Math.sqrt(a * a + b * b + c * c);
        if (len < 1e-12) len = 1;
        planes[i][0] = a / len;
        planes[i][1] = b / len;
        planes[i][2] = c / len;
        planes[i][3] = d / len;
    }

    /** Whether a ball of {@code radius} round {@code (x, y, z)} may be in view. */
    public boolean sees(double x, double y, double z, double radius) {
        for (double[] p : planes) {
            if (p[0] * x + p[1] * y + p[2] * z + p[3] < -radius) return false;
        }
        return true;
    }

    public boolean sees(Vec3 at, double radius) {
        return sees(at.x(), at.y(), at.z(), radius);
    }
}
