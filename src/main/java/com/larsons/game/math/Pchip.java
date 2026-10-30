package com.larsons.game.math;

/**
 * A monotone cubic through keys (Fritsch-Carlson slopes: no overshoot between
 * keys, flat at a turning point and at both ends) - the same curve the
 * 3D-Modeling scripts key their animations with ({@code
 * feminine_model_armed.pchip}), so the game can follow a path a sprite was
 * posed along exactly.
 */
public final class Pchip {

    private Pchip() {}

    /**
     * The curve through {@code keys} (rows of {x, y0, y1, ...}, x rising) for
     * column {@code col}, at {@code x}; held flat beyond the first and last keys.
     */
    public static double at(double[][] keys, int col, double x) {
        int n = keys.length;
        if (n == 1 || x <= keys[0][0]) return keys[0][col];
        if (x >= keys[n - 1][0]) return keys[n - 1][col];
        int i = 0;
        while (i < n - 2 && x >= keys[i + 1][0]) i++;
        double x0 = keys[i][0], x1 = keys[i + 1][0], h = x1 - x0;
        double t = (x - x0) / h, t2 = t * t, t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * keys[i][col] + (t3 - 2 * t2 + t) * h * slope(keys, col, i)
                + (-2 * t3 + 3 * t2) * keys[i + 1][col] + (t3 - t2) * h * slope(keys, col, i + 1);
    }

    /** The slope at key {@code i}: 0 at the ends and where the curve turns, else a weighted harmonic mean. */
    private static double slope(double[][] keys, int col, int i) {
        if (i == 0 || i == keys.length - 1) return 0;
        double h0 = keys[i][0] - keys[i - 1][0], h1 = keys[i + 1][0] - keys[i][0];
        double d0 = (keys[i][col] - keys[i - 1][col]) / h0, d1 = (keys[i + 1][col] - keys[i][col]) / h1;
        if (d0 * d1 <= 0) return 0;
        double w1 = 2 * h1 + h0, w2 = h1 + 2 * h0;
        return (w1 + w2) / (w1 / d0 + w2 / d1);
    }
}
