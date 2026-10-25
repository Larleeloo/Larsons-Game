package com.larsons.game.combat;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;

/**
 * A training dummy: a straw-stuffed burlap figure on a stake, a target
 * painted on its chest - a 2D sprite like the chest, drawn from the same 24
 * views ({@code assets/sprites/objects/attack_dummy/}).
 *
 * <pre>
 *   IDLE  --hit-->  HIT (rocks away from the blow, wobbles back)  -->  IDLE
 *   health gone -->  FALL (tips over away from the blow)  -->  DOWN (for a while)
 *         -->  RISE (springs back up, health full)  -->  IDLE
 * </pre>
 *
 * <p>Every reaction is drawn four ways - struck on its front (the painted
 * target), its back, its left or its right - and it plays the one for the
 * side the blow came from ({@link #sideStruck}). Lying knocked over it is
 * not {@link #alive()}: nothing hits it until it is back up.
 */
public final class Dummy implements Target {

    /** The sprite folder, {@code assets/sprites/objects/attack_dummy/}. */
    public static final String SPRITES = "attack_dummy";
    /** How far from its middle the player is kept (the stake and the mound). */
    public static final double RADIUS = 0.5;
    /** How far from its middle a blow or a shot touches it (its arms reach a little wider). */
    public static final double BODY = 0.4;
    /** How tall it stands. */
    public static final double HEIGHT = 1.62;
    /** How long it flashes when struck, in seconds. */
    public static final double FLASH = 0.14;

    /** Its sheets play at 24 fps: a hit is 16 frames, a fall 12, rising 18. */
    private static final double FPS = 24;

    /** The side a blow struck, in the dummy's own terms (its face is the front, its left arm its left). */
    public enum Side {
        FRONT("front"), BACK("back"), LEFT("left"), RIGHT("right");

        private final String key;

        Side(String key) { this.key = key; }

        public String key() { return key; }
    }

    /** What it is doing. */
    public enum Phase {
        IDLE("idle", 2.0), HIT("hit", 16 / FPS), FALL("fall", 12 / FPS), DOWN("down", 0), RISE("rise", 18 / FPS);

        private final String key;
        private final double duration;

        Phase(String key, double duration) {
            this.key = key;
            this.duration = duration;
        }

        public String key() { return key; }

        /** How long its animation plays (DOWN: as long as the dummy is set to lie). */
        public double duration() { return duration; }
    }

    private final Vec3 position;
    private final double heading;
    private final double maxHealth, downSeconds;
    private double health;
    private Phase phase = Phase.IDLE;
    private Side side = Side.FRONT;
    private double time, sinceHurt = Double.MAX_VALUE;

    /**
     * @param position    where it stands
     * @param heading     which way its face looks, as a character's heading
     * @param maxHealth   how much it takes to knock it over
     * @param downSeconds how long it lies before it springs back up
     */
    public Dummy(Vec3 position, double heading, double maxHealth, double downSeconds) {
        this.position = new Vec3(position.x(), 0, position.z());
        this.heading = heading;
        this.maxHealth = Math.max(1, maxHealth);
        this.downSeconds = Math.max(0, downSeconds);
        this.health = this.maxHealth;
    }

    public void tick(double dt) {
        time += dt;
        if (sinceHurt < Double.MAX_VALUE) sinceHurt += dt;
        switch (phase) {
            case HIT -> {
                if (time >= Phase.HIT.duration()) enter(Phase.IDLE);
            }
            case FALL -> {
                if (time >= Phase.FALL.duration()) enter(Phase.DOWN);
            }
            case DOWN -> {
                if (time >= downSeconds) enter(Phase.RISE);
            }
            case RISE -> {
                if (time >= Phase.RISE.duration()) {
                    health = maxHealth;
                    enter(Phase.IDLE);
                }
            }
            case IDLE -> { }
        }
    }

    private void enter(Phase p) {
        phase = p;
        time = 0;
    }

    @Override
    public void hurt(double damage, Vec3 from, boolean react) {
        if (!alive() || damage <= 0) return;
        health = Math.max(0, health - damage);
        sinceHurt = 0;
        if (from != null && (react || health <= 0)) side = sideStruck(from);
        if (health <= 0) enter(Phase.FALL);
        else if (react) enter(Phase.HIT);
    }

    /**
     * The side of it a blow from {@code from} lands on: the one facing that
     * way most nearly.
     */
    public Side sideStruck(Vec3 from) {
        Vec3 f = SpriteView.headingVector(heading);
        double dx = from.x() - position.x(), dz = from.z() - position.z();
        double ahead = dx * f.x() + dz * f.z();
        double right = dx * -f.z() + dz * f.x();       // its right: (-fz, 0, fx)
        if (Math.abs(ahead) >= Math.abs(right)) return ahead >= 0 ? Side.FRONT : Side.BACK;
        return right >= 0 ? Side.RIGHT : Side.LEFT;
    }

    /** The sheet it plays now: {@code idle}, or the phase on its side, {@code hit_left}. */
    public String sheet() {
        return phase == Phase.IDLE ? Phase.IDLE.key() : phase.key() + "_" + side.key();
    }

    /** Whether its sheet repeats (the idle sway) or plays once and holds. */
    public boolean loops() {
        return phase == Phase.IDLE;
    }

    /** 0 – 1: how white it flashes, just struck. */
    public double flash() {
        return sinceHurt >= FLASH ? 0 : 1 - sinceHurt / FLASH;
    }

    /** Seconds since it last took damage (a lot: never). */
    public double sinceHurt() { return sinceHurt; }

    @Override
    public boolean alive() {
        return phase == Phase.IDLE || phase == Phase.HIT;
    }

    @Override
    public Vec3 position() { return position; }

    @Override
    public double radius() { return BODY; }

    @Override
    public double height() { return HEIGHT; }

    @Override
    public double health() { return health; }

    @Override
    public double maxHealth() { return maxHealth; }

    public double heading() { return heading; }

    public Phase phase() { return phase; }

    public Side side() { return side; }

    /** Seconds into the current phase. */
    public double time() { return time; }
}
