package com.larsons.game.ambient;

import com.larsons.game.combat.Target;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;

import java.util.Random;

/**
 * One animal: where it is, which way it faces, what it is doing and which
 * of its sheets that plays, its health - it can be hit, as a dummy can
 * ({@link Target}) - and how much of it shows (it fades in when it comes,
 * and out when it is gone: left behind, or beaten and run off). {@link
 * Animals} moves it.
 */
public final class Animal implements Target {

    /** What it is doing. */
    public enum Mode {
        /** Standing about (a bird on the water: floating). */
        REST,
        /** Grazing, nibbling, dabbling. */
        GRAZE,
        /** Going somewhere near home, at a walk. */
        WANDER,
        /** Running from her. */
        FLEE,
        /** Up and away over everything (a startled bird): gone when out of sight. */
        FLY_OFF,
        /** Coming up to her, keeping by her (a friendly one). */
        FOLLOW,
        /** Facing her, warning her off (a defensive one). */
        WARN,
        /** Going for her. */
        CHASE,
        /** Its blow, wound up, about to land. */
        STRIKE,
        /** Knocked back by a blow. */
        STAGGER,
        /** Beaten: running off, fading, gone. */
        RUN_OFF
    }

    /** How long a hit's white flash lasts, seconds. */
    public static final double FLASH = 0.14;

    final AnimalKind kind;
    final Animals.Herd herd;               // the herd placed in the level it belongs to, or null (it came by)
    final Temperament temperament;
    double x, y, z, heading;
    Mode mode = Mode.REST;
    String sheet;
    double sheetTime, rate = 1;
    double alpha;
    boolean leaving, gone;
    double homeX, homeZ, roam;
    double health;
    double sinceHurt = Double.MAX_VALUE;
    double timer, modeTime, angry, cooldown, phase, lift;
    double toX, toZ;
    double vx, vy, vz;
    double fleeX, fleeZ;                    // what it runs from
    boolean swimming, airborne, warned;
    double ignore;                          // a friendly one, done with her: how long it leaves her be
    boolean saddled;                        // a horse with the saddle on: she can ride it, and it stays
    boolean ridden;                         // she is on it (or getting on or off): the world moves it

    Animal(AnimalKind kind, Animals.Herd herd, Temperament temperament, double x, double y, double z, Random random) {
        this.kind = kind;
        this.herd = herd;
        this.temperament = temperament;
        this.x = x;
        this.y = y;
        this.z = z;
        this.homeX = x;
        this.homeZ = z;
        this.toX = x;
        this.toZ = z;
        this.heading = random.nextDouble() * Math.PI * 2;
        this.health = kind.maxHealth();
        this.timer = 0.5 + random.nextDouble() * 4;
        this.phase = random.nextDouble() * 10;
        this.lift = 0.4 + random.nextDouble() * 1.1;
        this.sheet = kind.flies() ? "fly" : "idle";
    }

    public AnimalKind kind() { return kind; }

    /** The herd placed in the level it lives in, or null. */
    public Animals.Herd herd() { return herd; }

    /** Its nature: its kind's, or its herd's. */
    public Temperament temperament() { return temperament; }

    public Mode mode() { return mode; }

    /** Its feet (a flier's lowest point; a swimmer's waterline). */
    @Override
    public Vec3 position() { return new Vec3(x, y, z); }

    /** The way it faces, radians ({@link SpriteView#headingVector}). */
    public double heading() { return heading; }

    /** The sheet it plays now: {@code idle}, {@code walk}, {@code run}, {@code eat}, {@code hop}, {@code swim}, ... */
    public String sheet() { return sheet; }

    /** How far into its sheet it is, seconds - at the speed it is played. */
    public double sheetTime() { return sheetTime; }

    /** Whether its sheet repeats (all but a blow: a hopper going hops on). */
    public boolean loops() {
        return !(sheet.equals("attack") && mode == Mode.STRIKE);
    }

    /** How much of it shows, 0 – 1. */
    public double alpha() { return alpha; }

    /** Whether it is swimming: on water, sunk to its waterline. */
    public boolean swimming() { return swimming; }

    /**
     * Whether it has the saddle on (a horse she saddled, or one placed
     * saddled): she can ride it, it keeps where she left it, and it is never
     * sent away with those that come and go.
     */
    public boolean saddled() { return saddled; }

    /** Put the saddle on it, or take it off; a horse only (any other is left as it is). */
    public void setSaddled(boolean on) {
        if (kind != AnimalKind.HORSE) return;
        saddled = on;
        if (on) {
            leaving = false;
            homeX = x;
            homeZ = z;
            roam = Math.min(roam, STAY);
            if (mode == Mode.FLEE || mode == Mode.FOLLOW || mode == Mode.WANDER) {
                mode = Mode.REST;
                modeTime = 0;
                vx = vz = 0;
            }
        }
    }

    /** How far a saddled horse wanders from where she left it, metres. */
    public static final double STAY = 2.5;

    /** Whether she is on it, or getting on or off it: it goes where she steers it ({@link #carry}). */
    public boolean ridden() { return ridden; }

    /**
     * Whether she rides it ({@code on}) or has got off: while she does, it
     * does nothing of its own; once she is off, it stays about where she left it.
     */
    public void setRidden(boolean on) {
        if (ridden == on) return;
        ridden = on;
        mode = Mode.REST;
        modeTime = 0;
        vx = vz = 0;
        angry = 0;
        timer = 2 + phase % 3;
        if (!on) {
            homeX = x;
            homeZ = z;
            roam = STAY;
            sheet = "idle";
            sheetTime = 0;
            rate = 1;
        }
    }

    /**
     * Where she has steered it this frame: at {@code at}, facing {@code
     * heading}, going {@code velocity}, playing {@code sheet} {@code time}
     * into it (her clock, so she rises and falls with its back) at {@code rate}.
     */
    public void carry(Vec3 at, double heading, String sheet, double time, double rate, Vec3 velocity) {
        x = at.x();
        y = at.y();
        z = at.z();
        this.heading = heading;
        this.sheet = sheet;
        sheetTime = time;
        this.rate = rate;
        vx = velocity.x();
        vz = velocity.z();
        alpha = 1;
        swimming = false;
        airborne = false;
    }

    /** The sprite it is drawn from: its kind's - the saddled horse's with the saddle on. */
    public String sprite() {
        return saddled ? kind.sprite() + "_saddled" : kind.sprite();
    }

    /** Whether it is angry with her: warning her off, or going for her. */
    public boolean hostile() {
        return mode == Mode.WARN || mode == Mode.CHASE || mode == Mode.STRIKE;
    }

    /** How white its hit flash is now, 0 – 1. */
    public double flash() {
        return sinceHurt < FLASH ? 1 - sinceHurt / FLASH : 0;
    }

    @Override
    public double radius() { return kind.radius(); }

    @Override
    public double height() { return kind.height(); }

    @Override
    public boolean alive() {
        return health > 0 && !leaving && !gone && mode != Mode.RUN_OFF && mode != Mode.FLY_OFF && alpha > 0.3;
    }

    @Override
    public double health() { return health; }

    @Override
    public double maxHealth() { return kind.maxHealth(); }

    /** A blow: its health down, a flash, knocked back; and then - by its nature - it runs, or it fights. */
    @Override
    public void hurt(double damage, Vec3 from, boolean react) {
        if (!alive() || ridden) return;
        if (damage > 0) {
            health = Math.max(0, health - damage);
            sinceHurt = 0;
        }
        Vec3 f = from == null ? new Vec3(x, y, z + 1) : from;
        fleeX = f.x();
        fleeZ = f.z();
        hurtFrom = true;
        if (health <= 0) {
            mode = Mode.RUN_OFF;
            modeTime = 0;
            beaten = true;
            return;
        }
        if (react) {
            Vec3 away = new Vec3(x - f.x(), 0, z - f.z()).normalize(new Vec3(0, 0, 1));
            vx = away.x() * 2.2;
            vz = away.z() * 2.2;
            mode = Mode.STAGGER;
            modeTime = 0;
        }
        provoked = true;
    }

    // what happened to it since Animals last looked
    boolean hurtFrom, beaten, provoked;

    @Override
    public Vec3 velocity() { return new Vec3(vx, vy, vz); }

    @Override
    public boolean flies() { return kind.flies() || airborne; }

    @Override
    public double sinceHurt() { return sinceHurt; }

    @Override
    public String name() { return kind.singular(); }

    /** Turn to face {@code at} at once. */
    public void faceTo(Vec3 at) {
        if (Math.hypot(at.x() - x, at.z() - z) > 1e-6) heading = SpriteView.headingOf(at.x() - x, at.z() - z);
    }

    void set(String s) {
        if (!s.equals(sheet)) {
            sheet = s;
            sheetTime = 0;
        }
    }

    void enter(Mode m) {
        if (m != mode) {
            mode = m;
            modeTime = 0;
        }
    }
}
