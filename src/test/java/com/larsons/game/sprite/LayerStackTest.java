package com.larsons.game.sprite;

import com.larsons.game.math.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Where a cropped layer's card goes — no GPU involved. */
class LayerStackTest {

    // A whole-frame card: 2.4 m wide, standing up, facing down the z axis.
    static final Vec3 TOP_LEFT = new Vec3(-1.2, 2.1, 0.3);
    static final Vec3 RIGHT = new Vec3(2.4, 0, 0);
    static final Vec3 DOWN = new Vec3(0, -2.4, 0);

    static void assertNear(Vec3 expected, Vec3 actual) {
        assertEquals(0, expected.distance(actual), 1e-9, expected + " vs " + actual);
    }

    /** The world point of a spot {@code (u, v)} (fractions) across a card. */
    static Vec3 at(Vec3[] card, double u, double v) {
        return card[0].add(card[1].scale(u)).add(card[2].scale(v));
    }

    @Test
    void theWholeFrameIsTheWholeCard() {
        Vec3[] c = LayerStack.card(TOP_LEFT, RIGHT, DOWN, new double[]{0, 0, 1, 1});
        assertNear(TOP_LEFT, c[0]);
        assertNear(RIGHT, c[1]);
        assertNear(DOWN, c[2]);
    }

    /**
     * Every texel of a cropped sheet lands on the world point its pixel had
     * on the whole-frame card, so a cropped hat still sits on the head — and
     * the same when the sheet is drawn mirrored as its west-facing twin.
     */
    @Test
    void aTexelLandsWhereItsPixelWasOnTheWholeFrame() {
        SheetImage s = SheetImage.decode(SheetImageTest.smallItem(), 512, 512, 1.0, 4096, false);
        int[] crop = s.crop();
        Vec3[] whole = {TOP_LEFT, RIGHT, DOWN};
        for (boolean mirrored : new boolean[]{false, true}) {
            Vec3[] c = LayerStack.card(TOP_LEFT, RIGHT, DOWN, s.region(mirrored));
            for (int[] texel : new int[][]{{0, 0}, {13, 17}, {79, 63}, {40, 5}}) {
                // Texel centre, across the cell as the card's uv runs (reversed when mirrored).
                double u = (texel[0] + 0.5) / s.frameWidth(), v = (texel[1] + 0.5) / s.frameHeight();
                double x = crop[0] + texel[0] + 0.5, y = crop[1] + texel[1] + 0.5;
                Vec3 expected = mirrored ? at(whole, 1 - x / 512, y / 512) : at(whole, x / 512, y / 512);
                assertNear(expected, at(c, mirrored ? 1 - u : u, v));
            }
        }
    }
}
