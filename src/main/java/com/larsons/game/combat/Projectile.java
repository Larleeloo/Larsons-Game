package com.larsons.game.combat;

import com.larsons.game.math.Vec3;

/**
 * Something she has shot, in flight: an arrow, a crossbow bolt or a spell's
 * bolt of light. Arrows and bolts arc under gravity and stick in the ground
 * where they come down; a spell flies straight and bursts at the end of its
 * range. It carries the shot's damage roll ({@link Strike}) to whatever it
 * hits.
 */
public final class Projectile {

    /** How fast an arrow or a bolt falls, metres a second a second. */
    public static final double GRAVITY = 9.8;
    /** How thick a shot is, for what it touches, in metres. */
    public static final double THICKNESS = 0.08;

    private final Move.Kind kind;
    private final Strike strike;
    private final int colour;
    private final double life;
    private Vec3 position, velocity;
    private double age;
    private boolean stuck, done;
    private double stuckFor;

    /**
     * @param kind     an arrow, a bolt or a spell
     * @param strike   the damage it carries
     * @param colour   a spell's colour ({@code 0xRRGGBB}); unused for the others
     * @param from     where it leaves her hands
     * @param velocity its starting velocity
     * @param life     how long it may fly before it gives out, in seconds
     */
    public Projectile(Move.Kind kind, Strike strike, int colour, Vec3 from, Vec3 velocity, double life) {
        this.kind = kind;
        this.strike = strike;
        this.colour = colour;
        this.position = from;
        this.velocity = velocity;
        this.life = life;
    }

    /** How much it is pulled down: arrows and bolts fall, a spell does not. */
    public double gravity() {
        return kind == Move.Kind.SPELL ? 0 : GRAVITY;
    }

    /**
     * The velocity that sends a shot from {@code from} at {@code speed} to
     * {@code to}: straight at it for a spell, arcing up and over for an arrow
     * so it comes down on its mark.
     */
    public static Vec3 aim(Move.Kind kind, Vec3 from, Vec3 to, double speed) {
        Vec3 d = to.sub(from);
        double flat = d.horizontalLength();
        if (kind == Move.Kind.SPELL || flat < 1e-6) return d.normalize(new Vec3(0, 0, -1)).scale(speed);
        double t = flat / speed;
        return new Vec3(d.x() / t, d.y() / t + 0.5 * GRAVITY * t, d.z() / t);
    }

    /** Fly on for {@code dt} seconds (or, stuck, stay put); returns where it was before. */
    Vec3 advance(double dt) {
        Vec3 before = position;
        age += dt;
        if (stuck) {
            stuckFor += dt;
            return before;
        }
        velocity = velocity.add(0, -gravity() * dt, 0);
        position = position.add(velocity.scale(dt));
        return before;
    }

    /** Come to rest at {@code at}, stuck in the ground. */
    void stick(Vec3 at) {
        position = at;
        stuck = true;
    }

    /** Gone: it hit something, burst, or has been lying long enough. */
    void finish() {
        done = true;
    }

    public Move.Kind kind() { return kind; }

    public Strike strike() { return strike; }

    public int colour() { return colour; }

    public Vec3 position() { return position; }

    /** Which way it flies (stuck: the way it was flying as it went in). */
    public Vec3 velocity() { return velocity; }

    public double age() { return age; }

    public double life() { return life; }

    public boolean stuck() { return stuck; }

    /** Seconds it has been stuck in the ground. */
    public double stuckFor() { return stuckFor; }

    public boolean done() { return done; }
}
