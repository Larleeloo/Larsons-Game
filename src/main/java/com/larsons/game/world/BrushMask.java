package com.larsons.game.world;

/**
 * Where a brush works, and how much: 1 where it works fully, 0 where it does
 * nothing - the editor's filters (a slope's steepness, a height, what the
 * ground is painted, water), as World Painter has them. Every brush's weight
 * at a spot is multiplied by it.
 */
@FunctionalInterface
public interface BrushMask {

    /** Everywhere, fully. */
    BrushMask ALL = (x, z) -> 1;

    /** How much the brush works at {@code (x, z)}, 0 – 1. */
    double at(double x, double z);
}
