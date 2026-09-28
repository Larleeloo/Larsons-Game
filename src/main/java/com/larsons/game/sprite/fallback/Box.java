package com.larsons.game.sprite.fallback;

import com.larsons.game.math.Mat4;

/**
 * One cuboid of the fallback puppet: a unit box scaled to {@code half}-extents
 * and placed by {@code transform} in the character's own space (feet on
 * {@code y = 0}, facing −Z, right hand on +X).
 *
 * @param transform where the box's centre and axes are
 * @param hx        half width (x)
 * @param hy        half height (y)
 * @param hz        half depth (z)
 * @param rgb       flat colour, 0xRRGGBB
 * @param layer     which output layer the box renders into
 */
record Box(Mat4 transform, double hx, double hy, double hz, int rgb, Layer layer) {

    /** The layers the puppet is split into — one sprite sheet each. */
    enum Layer {
        /** The base body: what {@code body/} sheets replace. */
        BODY,
        /** A cap, only in the generated sample set ({@code hat/red_cap}). */
        HAT,
        /** The sword in the right hand: what {@code carry_right/sword/} sheets replace. */
        SWORD
    }
}
