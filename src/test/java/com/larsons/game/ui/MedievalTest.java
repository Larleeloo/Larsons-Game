package com.larsons.game.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The wardrobe's generated textures: opaque, seamless where tiled, and round where round. */
class MedievalTest {

    static int alpha(int argb) { return argb >>> 24; }
    static int red(int argb) { return (argb >> 16) & 255; }
    static int green(int argb) { return (argb >> 8) & 255; }
    static int blue(int argb) { return argb & 255; }

    /** The mean step in brightness between neighbours across the seam, over that between neighbours inside. */
    static double seam(int[] argb, int n) {
        double across = 0, inside = 0;
        for (int i = 0; i < n; i++) {
            across += Math.abs(red(argb[i * n + n - 1]) - red(argb[i * n])) + Math.abs(red(argb[(n - 1) * n + i]) - red(argb[i]));
            inside += Math.abs(red(argb[i * n + n / 2]) - red(argb[i * n + n / 2 + 1]))
                    + Math.abs(red(argb[(n / 2) * n + i]) - red(argb[(n / 2 + 1) * n + i]));
        }
        return across / Math.max(1, inside);
    }

    @Test
    void theWoodAndTheParchmentTileWithNoSeam() {
        int n = 128;
        for (int[] tex : new int[][]{Medieval.woodPixels(n, 7), Medieval.parchmentPixels(n, 11)}) {
            assertEquals(n * n, tex.length);
            for (int p : tex) assertEquals(255, alpha(p));
            assertTrue(seam(tex, n) < 2.5, "no seam: " + seam(tex, n));
        }
    }

    @Test
    void theWoodIsBrownAndHasAGrain() {
        int n = 128;
        int[] wood = Medieval.woodPixels(n, 7);
        int min = 255, max = 0;
        for (int p : wood) {
            assertTrue(red(p) >= green(p) && green(p) >= blue(p), "brown: " + Integer.toHexString(p));
            min = Math.min(min, red(p));
            max = Math.max(max, red(p));
        }
        assertTrue(max - min > 50, "lighter and darker streaks");
        assertArrayEquals(wood, Medieval.woodPixels(n, 7), "the same seed, the same wood");
        int[] down = Medieval.transpose(wood, n);
        assertEquals(wood[3 * n + 5], down[5 * n + 3], "the boards' grain runs down");
        assertArrayEquals(wood, Medieval.transpose(down, n));
    }

    @Test
    void theNoiseRepeatsAndStaysInRange() {
        for (double x = -3; x < 6; x += 0.37) {
            for (double y = -2; y < 5; y += 0.41) {
                double v = Medieval.noise(x, y, 3, 2, 5);
                assertTrue(v >= 0 && v <= 1, "in [0, 1]: " + v);
                assertEquals(v, Medieval.noise(x + 3, y + 2, 3, 2, 5), 1e-9, "every 3 across and 2 down");
            }
        }
    }

    @Test
    void theRivetAndTheDiscAreRoundWithSoftEdges() {
        int n = 32;
        for (int[] tex : new int[][]{Medieval.rivetPixels(n), Medieval.discPixels(n)}) {
            assertEquals(0, alpha(tex[0]), "corner clear");
            assertEquals(255, alpha(tex[(n / 2) * n + n / 2]), "centre solid");
            int edge = alpha(tex[(n / 2) * n]);
            assertTrue(edge > 0 && edge < 255, "the rim smoothed: " + edge);
        }
        int[] rivet = Medieval.rivetPixels(n);
        assertTrue(red(rivet[(n / 4) * n + n / 3]) > red(rivet[(3 * n / 4) * n + 2 * n / 3]), "lit from the top left");
        assertEquals(0xffffff, Medieval.discPixels(n)[(n / 2) * n + n / 2] & 0xffffff, "white, tinted when drawn");
    }
}
