package com.larsons.game.combat;

import com.larsons.game.math.Vec3;

/**
 * Something that can be hit: a training dummy now, anything that fights back
 * later. It stands on the ground at {@link #position()} as an upright
 * cylinder {@link #radius()} across and {@link #height()} tall - what a
 * swing reaches and a shot strikes.
 */
public interface Target {

    /** Where it stands, on the ground. */
    Vec3 position();

    /** How far from its middle a swing or a shot touches it, in metres. */
    double radius();

    /** How tall it stands, in metres. */
    double height();

    /** Whether it can be hit: standing (not knocked down) and not dead. */
    boolean alive();

    double health();

    double maxHealth();

    /**
     * Take {@code damage} dealt from {@code from} (where the blow came from,
     * on the ground). {@code react}: a blow it rocks from; false for damage
     * over time, which only wears it down.
     */
    void hurt(double damage, Vec3 from, boolean react);
}
