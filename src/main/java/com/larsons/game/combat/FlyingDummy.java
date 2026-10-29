package com.larsons.game.combat;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.world.Player;
import com.larsons.game.world.Terrain;

/**
 * The flying strike dummy: the training dummy's burlap body, its painted
 * target, on a pair of burlap wings - a 2D sprite drawn from the same 24
 * views ({@code assets/sprites/objects/flying_dummy/}) that the game flies
 * about, its shadow on the ground under it.
 *
 * <pre>
 *   FLY    on its rounds: a circle {@link #RADIUS_OF_ROUNDS} m round its middle,
 *          {@link #ALTITUDE} m over the ground, out of a sword's reach
 *     --she comes within {@link #NOTICE} m-->  WARN (hangs, facing her, screeching)
 *     -->  SWOOP: dives through where her chest was and climbs away the
 *          other side; low enough in the dive for a sword, and if it passes
 *          through her it strikes ({@link #SWOOP_DAMAGE}) - unless she rolls
 *          through it (dodged), blocks (half) or parries it (knocked back,
 *          unhurt)  -->  FLY, the next dive {@link #SWOOP_EVERY} s later
 *   hit    -->  HIT (knocked back from the blow, flapping to steady itself)  -->  FLY
 *   health gone  -->  FALL (tumbles out of the air to the ground)
 *     -->  DOWN (for a while)  -->  RISE (rights itself, beats back up, health full)  -->  FLY
 * </pre>
 *
 * <p>As the ground dummy's, each reaction is drawn struck on its front, back,
 * left or right ({@link Dummy.Side}). Its {@link #position()} is its lowest
 * point - the tip of its straw tail - and it is a {@link Target} {@link
 * #HEIGHT} m tall from there.
 */
public final class FlyingDummy implements Target {

    /** The sprite folder, {@code assets/sprites/objects/flying_dummy/}. */
    public static final String SPRITES = "flying_dummy";
    /** How far from its middle a blow or a shot touches it (its wings reach a little wider). */
    public static final double BODY = 0.5;
    /** How tall it is, from its tail's tip to the top of its head. */
    public static final double HEIGHT = 1.0;
    /** Its body's middle, over its lowest point: what strikes her in a dive. */
    public static final double MIDDLE = 0.55;
    /** Its rounds: metres over the ground at their middle, metres round, metres a second. */
    public static final double ALTITUDE = 4.0, RADIUS_OF_ROUNDS = 4.5, SPEED = 2.6;
    /** How near the middle of its rounds she has to come for it to dive at her, metres. */
    public static final double NOTICE = 11;
    /** How long it hangs screeching before it dives, and the least time from one dive to the next. */
    public static final double WARN_SECONDS = 0.7, SWOOP_EVERY = 4.5;
    /** How fast it dives, m/s, and how far past her it climbs away. */
    public static final double SWOOP_SPEED = 8.5, SWOOP_PAST = 6;
    /** What a dive that strikes her takes, and how near her chest its body has to pass to strike. */
    public static final double SWOOP_DAMAGE = 8, CONTACT = 0.85;
    /** Where her chest is, over her feet - what it dives at. */
    public static final double CHEST = 1.05;
    /** How long it flashes when struck, in seconds. */
    public static final double FLASH = 0.14;

    /** Its sheets play at 24 fps: a hit is 12 frames, a fall 12, rising 18. */
    private static final double FPS = 24;
    private static final double GRAVITY = 12;

    /** What it is doing. */
    public enum Phase {
        FLY("fly"), WARN("warn"), SWOOP("swoop"), HIT("hit", 12 / FPS), FALL("fall", 12 / FPS), DOWN("down"),
        RISE("rise", 18 / FPS);

        private final String key;
        private final double duration;

        Phase(String key) {
            this(key, 0);
        }

        Phase(String key, double duration) {
            this.key = key;
            this.duration = duration;
        }

        public String key() { return key; }

        /** How long its animation plays (0: as long as it lasts). */
        public double duration() { return duration; }
    }

    /** What its dive did this frame, for the world to show. */
    public enum Outcome { NONE, WARNED, STRUCK, DODGED, BLOCKED, PARRIED }

    private final Vec3 centre;
    private final double maxHealth, downSeconds;
    private Vec3 position, velocity = Vec3.ZERO;
    private double heading;
    private double health;
    private Phase phase = Phase.FLY;
    private Dummy.Side side = Dummy.Side.FRONT;
    private double time, clock, sinceHurt = Double.MAX_VALUE, sinceSwoop;
    /** Where on its rounds it is, radians. */
    private double round;
    /** The dive under way: from, the bend that takes it through her chest, to; how long; whether it has struck. */
    private Vec3 swoopFrom, swoopBend, swoopTo;
    private double swoopSeconds;
    private boolean swoopDone;

    /**
     * @param centre      the middle of its rounds, on the ground
     * @param start       how far round its rounds it starts, radians
     * @param maxHealth   how much it takes to knock it out of the air
     * @param downSeconds how long it lies before it beats back up
     * @param terrain     the ground under it
     */
    public FlyingDummy(Vec3 centre, double start, double maxHealth, double downSeconds, Terrain terrain) {
        this.centre = terrain.onGround(centre);
        this.round = start;
        this.maxHealth = Math.max(1, maxHealth);
        this.downSeconds = Math.max(0, downSeconds);
        this.health = this.maxHealth;
        this.position = onRounds(round, 0);
        this.heading = SpriteView.headingOf(-Math.sin(round), Math.cos(round));
        this.sinceSwoop = SWOOP_EVERY * 0.5;
    }

    /** Where on its rounds it would be at angle {@code a}, bobbing by {@code t}. */
    private Vec3 onRounds(double a, double t) {
        return new Vec3(centre.x() + RADIUS_OF_ROUNDS * Math.cos(a),
                centre.y() + ALTITUDE + 0.18 * Math.sin(t * 2.3),
                centre.z() + RADIUS_OF_ROUNDS * Math.sin(a));
    }

    /**
     * Advance a frame: its rounds, a dive at {@code player} when she is near
     * - striking her if it passes through her - or its fall, its time on the
     * ground and its rising. Returns what its dive did this frame.
     */
    public Outcome tick(double dt, Terrain terrain, Player player) {
        time += dt;
        clock += dt;
        sinceSwoop += dt;
        if (sinceHurt < Double.MAX_VALUE) sinceHurt += dt;
        Outcome out = Outcome.NONE;
        double ground = terrain.height(position.x(), position.z());
        switch (phase) {
            case FLY -> {
                fly(dt, 1);
                if (sinceSwoop >= SWOOP_EVERY && wants(player)) {
                    enter(Phase.WARN);
                    out = Outcome.WARNED;
                }
            }
            case WARN -> {
                velocity = velocity.scale(Math.exp(-dt * 6));
                position = position.add(velocity.scale(dt));
                face(player.feet().sub(position));
                if (time >= WARN_SECONDS) {
                    if (wants(player)) startSwoop(player, terrain);
                    else enter(Phase.FLY);
                }
            }
            case SWOOP -> out = swoop(dt, player);
            case HIT -> {
                velocity = velocity.scale(Math.exp(-dt * 4));
                position = position.add(velocity.scale(dt));
                if (time >= Phase.HIT.duration()) enter(Phase.FLY);
            }
            case FALL -> {
                velocity = new Vec3(velocity.x() * Math.exp(-dt * 2), velocity.y() - GRAVITY * dt,
                        velocity.z() * Math.exp(-dt * 2));
                position = position.add(velocity.scale(dt));
                if (position.y() <= ground) {
                    position = new Vec3(position.x(), ground, position.z());
                    velocity = Vec3.ZERO;
                    enter(Phase.DOWN);
                }
            }
            case DOWN -> {
                position = new Vec3(position.x(), ground, position.z());
                if (time >= downSeconds) enter(Phase.RISE);
            }
            case RISE -> {
                // rights itself, then beats up off the ground
                if (time > 0.35) velocity = new Vec3(0, 2.6, 0);
                position = position.add(velocity.scale(dt));
                if (time >= Phase.RISE.duration()) {
                    health = maxHealth;
                    round = Math.atan2(position.z() - centre.z(), position.x() - centre.x());
                    sinceSwoop = 0;
                    enter(Phase.FLY);
                }
            }
        }
        // never into the ground
        double under = terrain.height(position.x(), position.z());
        if (phase != Phase.DOWN && position.y() < under) position = new Vec3(position.x(), under, position.z());
        return out;
    }

    /** On its rounds, steering for its place on them ({@code pull}: how hard). */
    private void fly(double dt, double pull) {
        round += SPEED / RADIUS_OF_ROUNDS * dt;
        Vec3 want = onRounds(round, clock);
        Vec3 steer = want.sub(position).scale(2.2 * pull);
        double max = SPEED * 2.2;
        if (steer.length() > max) steer = steer.normalize().scale(max);
        velocity = velocity.lerp(steer, 1 - Math.exp(-dt * 3));
        position = position.add(velocity.scale(dt));
        face(velocity);
    }

    /** Whether it dives at {@code p}: she is about, near its rounds, not up a wall. */
    private boolean wants(Player p) {
        return p.health() > 0 && p.climbing() == null
                && p.ground().horizontalDistance(centre) <= NOTICE && p.feet().y() < position.y() + 1;
    }

    private void startSwoop(Player p, Terrain terrain) {
        Vec3 chest = p.feet().add(0, CHEST - MIDDLE, 0);
        Vec3 flat = new Vec3(chest.x() - position.x(), 0, chest.z() - position.z());
        Vec3 dir = flat.normalize(new Vec3(1, 0, 0));
        Vec3 past = chest.add(dir.scale(SWOOP_PAST));
        Vec3 to = new Vec3(past.x(), terrain.height(past.x(), past.z()) + ALTITUDE * 0.8, past.z());
        swoopFrom = position;
        swoopTo = to;
        // the bend that takes the curve through her chest halfway along it
        swoopBend = chest.scale(2).sub(swoopFrom.add(swoopTo).scale(0.5));
        swoopSeconds = Math.max(0.6, (swoopFrom.distance(chest) + chest.distance(swoopTo)) / SWOOP_SPEED);
        swoopDone = false;
        enter(Phase.SWOOP);
    }

    private Outcome swoop(double dt, Player p) {
        double u = Math.min(1, time / swoopSeconds);
        Vec3 before = position;
        position = bezier(swoopFrom, swoopBend, swoopTo, u);
        velocity = position.sub(before).scale(1 / Math.max(1e-6, dt));
        face(velocity);
        Outcome out = Outcome.NONE;
        if (!swoopDone) {
            Vec3 body = position.add(0, MIDDLE, 0), chest = p.feet().add(0, CHEST, 0);
            if (body.distance(chest) <= CONTACT) {
                swoopDone = true;
                out = strike(p);
            }
        }
        if (u >= 1 && phase == Phase.SWOOP) {
            sinceSwoop = 0;
            round = Math.atan2(position.z() - centre.z(), position.x() - centre.x());
            enter(Phase.FLY);
        }
        return out;
    }

    /**
     * Its dive passes through her: what it does depends on what she is
     * doing - rolling, it misses (dodged); parrying, it is knocked back and
     * she is unhurt; behind a block, half; else {@link #SWOOP_DAMAGE}.
     */
    Outcome strike(Player p) {
        if (p.invulnerable()) return Outcome.DODGED;
        if (p.action() != null && p.action().key().contains("parry")) {
            hurt(0, p.feet(), true);
            knock(p.feet());
            return Outcome.PARRIED;
        }
        if (p.stance().block() != null && p.state() == p.stance().block()) {
            p.hurt(Math.round(SWOOP_DAMAGE / 2));
            return Outcome.BLOCKED;
        }
        p.hurt(SWOOP_DAMAGE);
        return Outcome.STRUCK;
    }

    /** Point {@code u} along the curve from {@code a} bent by {@code b} to {@code c}. */
    static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, double u) {
        double v = 1 - u;
        return a.scale(v * v).add(b.scale(2 * u * v)).add(c.scale(u * u));
    }

    private void face(Vec3 d) {
        if (d.horizontalLength() > 0.2) heading = SpriteView.headingOf(d.x(), d.z());
    }

    /** Knocked back from {@code from}, flapping to steady itself. */
    private void knock(Vec3 from) {
        Vec3 away = position.sub(from);
        away = new Vec3(away.x(), 0, away.z()).normalize(new Vec3(0, 0, 1));
        velocity = away.scale(3).add(0, 1.5, 0);
        enter(Phase.HIT);
    }

    private void enter(Phase p) {
        phase = p;
        time = 0;
    }

    @Override
    public void hurt(double damage, Vec3 from, boolean react) {
        if (!alive()) return;
        if (from != null && (react || damage >= health)) side = sideStruck(from);
        if (damage <= 0) return;
        health = Math.max(0, health - damage);
        sinceHurt = 0;
        if (health <= 0) {
            Vec3 away = from == null ? Vec3.ZERO : position.sub(from);
            away = new Vec3(away.x(), 0, away.z()).normalize(Vec3.ZERO);
            velocity = away.scale(2).add(0, 1, 0);
            enter(Phase.FALL);
        } else if (react) {
            knock(from == null ? position.add(0, -1, 0) : from);
        }
    }

    /** The side of it a blow from {@code from} lands on (as {@link Dummy#sideStruck}). */
    public Dummy.Side sideStruck(Vec3 from) {
        Vec3 f = SpriteView.headingVector(heading);
        double dx = from.x() - position.x(), dz = from.z() - position.z();
        double ahead = dx * f.x() + dz * f.z();
        double right = dx * -f.z() + dz * f.x();
        if (Math.abs(ahead) >= Math.abs(right)) return ahead >= 0 ? Dummy.Side.FRONT : Dummy.Side.BACK;
        return right >= 0 ? Dummy.Side.RIGHT : Dummy.Side.LEFT;
    }

    /** The sheet it plays now: {@code fly}, {@code swoop}, or a reaction on its side, {@code hit_left}. */
    public String sheet() {
        return switch (phase) {
            case FLY, WARN -> "fly";
            case SWOOP -> "swoop";
            default -> phase.key() + "_" + side.key();
        };
    }

    /** Whether its sheet repeats (flying, diving) or plays once and holds. */
    public boolean loops() {
        return phase == Phase.FLY || phase == Phase.WARN || phase == Phase.SWOOP;
    }

    /** Seconds into its sheet: its own clock while it flies (a dive or a pause does not restart the flapping). */
    public double sheetTime() {
        return loops() ? clock : time;
    }

    /** 0 – 1: how white it flashes, just struck. */
    public double flash() {
        return sinceHurt >= FLASH ? 0 : 1 - sinceHurt / FLASH;
    }

    @Override
    public double sinceHurt() { return sinceHurt; }

    @Override
    public boolean alive() {
        return phase == Phase.FLY || phase == Phase.WARN || phase == Phase.SWOOP || phase == Phase.HIT;
    }

    @Override
    public Vec3 position() { return position; }

    @Override
    public Vec3 velocity() { return alive() ? velocity : Vec3.ZERO; }

    @Override
    public boolean flies() { return phase != Phase.DOWN; }

    @Override
    public double radius() { return BODY; }

    @Override
    public double height() { return HEIGHT; }

    @Override
    public double health() { return health; }

    @Override
    public double maxHealth() { return maxHealth; }

    @Override
    public String name() { return "Flying dummy"; }

    public double heading() { return heading; }

    public Phase phase() { return phase; }

    public Dummy.Side side() { return side; }

    /** The middle of its rounds, on the ground. */
    public Vec3 centre() { return centre; }

    /** Seconds into the current phase. */
    public double time() { return time; }

    /** Put it at {@code at} on its rounds' way (scripts and tests). */
    public void place(Vec3 at) {
        position = at;
        velocity = Vec3.ZERO;
    }

    /** Dive at {@code p} now (scripts and tests), from wherever it is. */
    public void swoopAt(Player p, Terrain terrain) {
        if (!alive()) return;
        startSwoop(p, terrain);
    }
}
