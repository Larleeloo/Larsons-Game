package com.larsons.game.world;

import com.larsons.game.math.Vec3;

import java.util.function.ToDoubleFunction;

/**
 * A treasure chest standing on the ground: a 2D sprite like the character,
 * drawn from the same 24 views (its sheets are under {@code
 * assets/sprites/objects/<id>/}), with magical smoke swirling round it, and
 * a lid the player can open.
 *
 * <pre>
 *   IDLE  --E-->  OPENING  --(its animation ends)-->  OPEN
 *   OPEN  --E-->  CLOSING  --(its animation ends)-->  IDLE
 * </pre>
 *
 * <p>{@link #light()} is how bright it is inside - nothing shut, full open,
 * rising and falling with the lid - which the renderer tints the smoke with.
 * Each state is an animation with its own sound ({@code
 * chests/<id>/<state>.mp3}, {@code audio/WorldSounds}).
 *
 * <p>It is meant to become a loot chest: what the player finds when it opens
 * will hang off {@link #isOpen()}.
 */
public final class Chest {

    /** How close the player must stand to open it, in metres from its middle. */
    public static final double REACH = 1.6;
    /** How far from its middle the player is kept (its footprint and its feet). */
    public static final double RADIUS = 0.68;

    /** What the chest is doing - each an animation, named as its sheets and sounds are. */
    public enum State {
        IDLE("idle", true), OPENING("open", false), OPEN("opened", true), CLOSING("close", false);

        private final String key;
        private final boolean loops;

        State(String key, boolean loops) {
            this.key = key;
            this.loops = loops;
        }

        /** The sheet and sound name: {@code idle}, {@code open}, {@code opened}, {@code close}. */
        public String key() { return key; }

        /** Whether the animation repeats ({@code idle}, {@code opened}) or plays once and moves on. */
        public boolean loops() { return loops; }

        /** The animation's length when the sheets cannot say (20 and 16 frames at 24 fps). */
        public double defaultDuration() {
            return switch (this) {
                case OPENING -> 20 / 24.0;
                case CLOSING -> 16 / 24.0;
                default -> 2.0;
            };
        }
    }

    private final String id;
    private final Vec3 position;
    private final double heading;
    private State state = State.IDLE;
    private double time;
    private double duration = State.IDLE.defaultDuration();

    /**
     * @param id       the sprite folder, {@code assets/sprites/objects/<id>/}
     * @param position where it stands
     * @param heading  which way its front (the lock) faces, as a character's heading
     */
    public Chest(String id, Vec3 position, double heading) {
        this.id = id;
        this.position = new Vec3(position.x(), 0, position.z());
        this.heading = heading;
    }

    /**
     * Open it, or shut it if it is open; a lid on its way up is slammed back
     * down, one on its way down thrown open again.
     */
    public void toggle(ToDoubleFunction<State> durations) {
        switch (state) {
            case IDLE, CLOSING -> enter(State.OPENING, durations);
            case OPEN, OPENING -> enter(State.CLOSING, durations);
        }
    }

    /** Advance its animation; a one-shot that has played through moves on. */
    public void tick(double dt, ToDoubleFunction<State> durations) {
        time += dt;
        if (!state.loops() && time >= duration) {
            enter(state == State.OPENING ? State.OPEN : State.IDLE, durations);
        }
    }

    private void enter(State s, ToDoubleFunction<State> durations) {
        state = s;
        time = 0;
        double d = durations == null ? 0 : durations.applyAsDouble(s);
        duration = d > 0 ? d : s.defaultDuration();
    }

    /**
     * How bright it is inside, 0 – 1: dark shut, lit open, coming up as the
     * lid clears the rim and going as it falls back (as the sheets draw it).
     */
    public double light() {
        double p = Math.min(1, time / Math.max(1e-6, duration));
        return switch (state) {
            case IDLE -> 0;
            case OPEN -> 1;
            case OPENING -> clamp((p - 0.2) / 0.2);
            case CLOSING -> clamp((0.75 - p) / 0.15);
        };
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** Whether it is open or opening - what a loot screen would wait on. */
    public boolean isOpen() {
        return state == State.OPEN || state == State.OPENING;
    }

    /** Whether a player standing at {@code at} can reach it. */
    public boolean inReach(Vec3 at) {
        return position.horizontalDistance(at) <= REACH;
    }

    public String id() { return id; }

    public Vec3 position() { return position; }

    public double heading() { return heading; }

    public State state() { return state; }

    /** Seconds into the current state's animation. */
    public double time() { return time; }
}
