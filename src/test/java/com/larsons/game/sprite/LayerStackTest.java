package com.larsons.game.sprite;

import com.larsons.game.math.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Where a cropped layer lands on its card — no GPU involved. */
class LayerStackTest {

    /** The texture coordinate at fraction {@code f} across a quad whose ends are {@code a} and {@code b}. */
    static double at(float a, float b, double f) {
        return a + f * (b - a);
    }

    /**
     * The texture coordinate a layer's quad puts at fraction {@code f} of the
     * whole card: the quad spans {@code r0}–{@code r1} of the card, with the
     * cell's coordinates {@code a} and {@code b} at its ends.
     */
    static double onCard(double r0, double r1, float a, float b, double f) {
        return at(a, b, (f - r0) / (r1 - r0));
    }

    @Test
    void aWholeFrameIsTheWholeCard() {
        Vec3 topLeft = new Vec3(1, 2, 3), right = new Vec3(2, 0, 0), down = new Vec3(0, -3, 0);
        Vec3[] q = LayerStack.regionQuad(topLeft, right, down, new double[]{0, 0, 1, 1});
        assertEquals(topLeft, q[0]);
        assertEquals(right, q[1]);
        assertEquals(down, q[2]);
    }

    /** A region's quad is that part of the card: its corners are the card's points at the region's fractions. */
    @Test
    void aRegionIsThatPartOfTheCard() {
        Vec3 topLeft = new Vec3(-1, 2.4, 0.5), right = new Vec3(1.2, 0, 0.6), down = new Vec3(0.1, -2.4, -0.2);
        double[] region = {0.375, 0.5625, 0.53125, 0.6875};
        Vec3[] q = LayerStack.regionQuad(topLeft, right, down, region);
        Vec3 first = topLeft.add(right.scale(region[0])).add(down.scale(region[1]));
        Vec3 last = topLeft.add(right.scale(region[2])).add(down.scale(region[3]));
        assertEquals(0, first.distance(q[0]), 1e-12);
        assertEquals(0, last.distance(q[0].add(q[1]).add(q[2])), 1e-12);
    }

    /**
     * Every pixel of a cropped sheet is drawn at the spot on the card where
     * it was in the frame — so a cropped hat still sits on the head — and the
     * same when the sheet is drawn mirrored as its west-facing twin.
     */
    @Test
    void aTexelLandsWhereItsPixelWasInTheFrame() {
        SheetImage s = SheetImage.decode(SheetImageTest.smallItem(), 512, 512, 1.0, 4096, false);
        int[] crop = s.crop();
        float w = s.atlas().getWidth(), h = s.atlas().getHeight();
        float cu = s.frameWidth() / w, cv = s.frameHeight() / h;   // frame 0's cell is at the atlas origin
        for (boolean mirrored : new boolean[]{false, true}) {
            float[] uv = mirrored ? new float[]{cu, 0, 0, cv} : new float[]{0, 0, cu, cv};
            double[] region = s.region(mirrored);
            for (int[] px : new int[][]{{192, 288}, {205, 305}, {271, 351}, {240, 300}}) {
                double fx = (px[0] + 0.5) / 512, fy = (px[1] + 0.5) / 512;
                if (mirrored) fx = 1 - fx;
                assertTrue(fx > region[0] && fx < region[2] && fy > region[1] && fy < region[3],
                        "pixel " + px[0] + "," + px[1] + " is inside the layer's quad");
                assertEquals((px[0] - crop[0] + 0.5) / w, onCard(region[0], region[2], uv[0], uv[2], fx), 1e-6,
                        "u of pixel " + px[0] + "," + px[1] + (mirrored ? " mirrored" : ""));
                assertEquals((px[1] - crop[1] + 0.5) / h, onCard(region[1], region[3], uv[1], uv[3], fy), 1e-6,
                        "v of pixel " + px[0] + "," + px[1] + (mirrored ? " mirrored" : ""));
            }
        }
    }
}
