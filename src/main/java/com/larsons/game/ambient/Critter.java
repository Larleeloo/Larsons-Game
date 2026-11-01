package com.larsons.game.ambient;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;

import java.util.Random;

/**
 * One little creature: where it is, which way it faces, what it is doing
 * (its sprite's state and how far into it), how much of it shows (it fades
 * in when it comes and out when it goes) - and, for a firefly, how bright it
 * is this moment. {@link Critters} moves it.
 */
public final class Critter {

    final CritterKind kind;
    final Critters.Spot spot;               // the spot it lives at, or null (it lives round the player)
    double x, y, z, heading;
    String state;
    double stateTime;
    double alpha;
    boolean leaving, gone;
    double homeX, homeZ, roam;
    int rgb = 0xD8FF5A;
    // what it is doing
    double timer, phase, period, lift;
    double fromX, fromY, fromZ, toX, toY, toZ, hopTime, hopLength, arc;
    double vx, vy, vz;
    boolean hovering = true;

    Critter(CritterKind kind, Critters.Spot spot, double x, double y, double z, Random random) {
        this.kind = kind;
        this.spot = spot;
        this.x = x;
        this.y = y;
        this.z = z;
        this.homeX = x;
        this.homeZ = z;
        this.heading = random.nextDouble() * Math.PI * 2;
        this.state = kind == CritterKind.DRAGONFLY ? "fly" : "idle";
        this.phase = random.nextDouble() * 10;
        this.period = 2.5 + random.nextDouble() * 2;
        this.timer = 1 + random.nextDouble() * 6;
        this.lift = 0.3 + random.nextDouble() * 1.4;
        this.toX = x;
        this.toY = y;
        this.toZ = z;
    }

    public CritterKind kind() { return kind; }

    /** Where it is: a flier where it flies, the rest on the ground (a frog's feet). */
    public Vec3 position() { return new Vec3(x, y, z); }

    /** The way it faces, radians ({@link SpriteView#headingVector}). */
    public double heading() { return heading; }

    /** The sheet it plays: {@code idle}, {@code croak}, {@code hop}, {@code chirp}, {@code fly}. */
    public String state() { return state; }

    /** Seconds into it. */
    public double stateTime() { return stateTime; }

    /** Whether that sheet repeats (a hop and a croak play once). */
    public boolean loops() {
        return !state.equals("hop") && !state.equals("croak");
    }

    /** How much of it shows, 0 – 1. */
    public double alpha() { return alpha; }

    /** How long a firefly's flash lasts, seconds. */
    static final double FLASH = 0.9;

    /** A firefly's light this moment, 0 – 1 (its blink, as much of it as shows). */
    public double glow(double time) {
        if (kind != CritterKind.FIREFLY) return 0;
        double t = (time + phase) % period;
        double flash = t < FLASH ? Math.sin(Math.PI * t / FLASH) : 0;
        return alpha * (0.25 + 0.75 * flash);
    }

    /** A firefly's colour. */
    public int rgb() { return rgb; }

    /** The placed spot it lives at, or null. */
    public Critters.Spot spot() { return spot; }

    void set(String s) {
        if (!s.equals(state)) {
            state = s;
            stateTime = 0;
        }
    }
}
