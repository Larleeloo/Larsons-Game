package com.larsons.game.core;

/**
 * One screen of the game — the main menu, the demo void. The {@link Game}
 * runs exactly one at a time: {@link #update} then {@link #render} every
 * frame, {@link #enter} and {@link #exit} around a switch.
 */
public interface Scene {

    default void enter() {}

    /** Advance by {@code dt} seconds and handle this frame's input. */
    void update(double dt);

    /** Draw the frame (the window is cleared before this). */
    void render();

    default void exit() {}

    /**
     * How far in from the window's top right corner the performance overlay
     * (F8) goes, pixels across and down: clear of whatever the scene keeps
     * there (an editor's panels).
     */
    default float[] perfInset() { return new float[]{10, 10}; }

    /** Title-bar suffix. */
    default String name() { return getClass().getSimpleName(); }

    /** What it loaded as it was opened, in a few words, for the loading screen ("" to say nothing). */
    default String loaded() { return ""; }
}
