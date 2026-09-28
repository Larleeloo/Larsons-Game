package com.larsons.game.sprite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Where a cropped layer lands on its card — no GPU involved. */
class LayerStackTest {

    /** The texture coordinate at fraction {@code f} across a card whose ends are {@code a} and {@code b}. */
    static double at(float a, float b, double f) {
        return a + f * (b - a);
    }

    @Test
    void aWholeFrameKeepsItsCoordinates() {
        float[] uv = {0.25f, 0.5f, 0.375f, 0.75f};
        assertArrayEquals(uv, LayerStack.wholeCardUv(new double[]{0, 0, 1, 1}, uv), 1e-7f);
    }

    /**
     * Every pixel of a cropped sheet is drawn at the spot on the whole card
     * where it was in the frame — so a cropped hat still sits on the head —
     * and the same when the sheet is drawn mirrored as its west-facing twin.
     */
    @Test
    void aTexelLandsWhereItsPixelWasInTheFrame() {
        SheetImage s = SheetImage.decode(SheetImageTest.smallItem(), 512, 512, 1.0, 4096, false);
        int[] crop = s.crop();
        float w = s.atlas().getWidth(), h = s.atlas().getHeight();
        float cu = s.frameWidth() / w, cv = s.frameHeight() / h;   // frame 0's cell is at the atlas origin
        for (boolean mirrored : new boolean[]{false, true}) {
            float[] uv = mirrored ? new float[]{cu, 0, 0, cv} : new float[]{0, 0, cu, cv};
            float[] card = LayerStack.wholeCardUv(s.region(mirrored), uv);
            for (int[] px : new int[][]{{192, 288}, {205, 305}, {271, 351}, {240, 300}}) {
                double fx = (px[0] + 0.5) / 512, fy = (px[1] + 0.5) / 512;
                if (mirrored) fx = 1 - fx;
                assertEquals((px[0] - crop[0] + 0.5) / w, at(card[0], card[2], fx), 1e-6,
                        "u of pixel " + px[0] + "," + px[1] + (mirrored ? " mirrored" : ""));
                assertEquals((px[1] - crop[1] + 0.5) / h, at(card[1], card[3], fy), 1e-6,
                        "v of pixel " + px[0] + "," + px[1] + (mirrored ? " mirrored" : ""));
            }
        }
    }

    /** The cell's own edges fall exactly on the edges of the part of the card it covers. */
    @Test
    void theCellFillsItsRegionAndTheClipIsItsEdges() {
        double[] region = {0.375, 0.5625, 0.53125, 0.6875};
        float[] uv = {0.1f, 0.2f, 0.3f, 0.35f};
        float[] card = LayerStack.wholeCardUv(region, uv);
        assertEquals(uv[0], at(card[0], card[2], region[0]), 1e-6);
        assertEquals(uv[2], at(card[0], card[2], region[2]), 1e-6);
        assertEquals(uv[1], at(card[1], card[3], region[1]), 1e-6);
        assertEquals(uv[3], at(card[1], card[3], region[3]), 1e-6);
    }
}
