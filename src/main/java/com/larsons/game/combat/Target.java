package com.larsons.game.combat;

import com.larsons.game.math.Vec3;

/**
 * Something that can be hit: a training dummy, a flying dummy, anything that
 * fights back later. It is an upright cylinder {@link #radius()} across and
 * {@link #height()} tall whose bottom is at {@link #position()} - on the
 * ground for a dummy, in the air for a flier - what a swing reaches and a
 * shot strikes.
 */
public interface Target {

    /** Where its bottom is: where it stands on the ground, or the lowest point of a flier in the air. */
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

    /** Which way and how fast it is going, metres a second: what a shot at it leads it by. */
    default Vec3 velocity() { return Vec3.ZERO; }

    /** Whether it flies (its shadow is on the ground under it, not at its feet). */
    default boolean flies() { return false; }

    /** Seconds since it was last hurt (a lot: never) - its health is shown for a while after. */
    default double sinceHurt() { return Double.MAX_VALUE; }

    /** What it is called (the lock-on says it). */
    default String name() { return "Target"; }
}
