package com.larsons.game.actor;

import com.larsons.game.combat.Target;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.world.Player;
import com.larsons.game.world.Terrain;

import java.util.List;
import java.util.Locale;

/**
 * A Vérnees: a serpent of another world, five metres of it, that comes out
 * of a portal in the sky and flies as a ribbon does. It winds down from its
 * portal, goes round over the ground under it - drifting after her - and
 * every so often hangs, hisses, and comes down at her, jaws wide; then away
 * up again. Felled, it drops out of the air, lies, and fades.
 *
 * <p>Drawn from {@code assets/sprites/objects/vernees} ({@code fly}, {@code
 * attack}, {@code fall}), its skin tinted one of its {@link Colour}s. Its
 * {@link #position()} is the lowest point of its body; it is a {@link
 * Target} {@link #HEIGHT} m tall from there.
 */
public final class Serpent implements Target {

    public static final String SPRITES = "vernees";

    /** The colours they come in. */
    public enum Colour {
        RED("red", "Red", 0xC0281E), BURGUNDY("burgundy", "Burgundy", 0x6E1428), MAUVE("mauve", "Mauve", 0xA878A0),
        GREY_GREEN("grey_green", "Grey-green", 0x7E8C74), FOREST("forest_green", "Forest green", 0x2C6A34);

        private final String key, label;
        private final int rgb;

        Colour(String key, String label, int rgb) {
            this.key = key;
            this.label = label;
            this.rgb = rgb;
        }

        public String key() { return key; }

        public String label() { return label; }

        public int rgb() { return rgb; }

        /** The colour called {@code key}, or null ("any": one of them). */
        public static Colour byKey(String key) {
            if (key != null) {
                String k = key.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
                for (Colour c : values()) if (c.key.equals(k)) return c;
            }
            return null;
        }

        public static List<String> keys() {
            return java.util.Arrays.stream(values()).map(Colour::key).toList();
        }
    }

    public enum Phase { EMERGE, FLY, WARN, SWOOP, CLIMB, FALL, DOWN }

    /** What its dive did this frame, for the world to show. */
    public enum Outcome { NONE, WARNED, STRUCK, DODGED, BLOCKED, PARRIED, FELL }

    /** How far from its middle a blow touches it, and how tall it is, at its usual size. */
    public static final double BODY = 0.9, HEIGHT = 1.3;
    /** Its rounds: metres over the ground, metres round, metres a second. */
    public static final double ALTITUDE = 6.5, ROUNDS = 6, SPEED = 4.2;
    /** How near the middle of its rounds she has to be for it to come at her; how far it follows her. */
    public static final double NOTICE = 18, FOLLOWS = 60;
    public static final double WARN_SECONDS = 0.8, SWOOP_EVERY = 5, SWOOP_SPEED = 11, CONTACT = 1.2, CHEST = 1.05;
    public static final double HEALTH = 70, DAMAGE = 12, FLASH = 0.14, LIES_FOR = 4, FADE = 1.2;
    /** Its sheets: a blow is 6 frames, a fall 6, at 10 a second. */
    public static final double ATTACK = 0.6, FALLS = 0.6;
    public static final int XP = 45;

    private final int id;
    private final Colour colour;
    private final double size, maxHealth, damage;
    private double cx, cz;
    private Vec3 position, velocity = Vec3.ZERO;
    private double heading, health, round, time, clock, sinceSwoop, sinceHurt = Double.MAX_VALUE, frozen, alpha = 1;
    private Phase phase = Phase.EMERGE;
    private String sheet = "fly";
    private double sheetTime;
    private boolean struck, gone, hostile = true;
    private Vec3 swoopAt = Vec3.ZERO;
    private String name;

    /**
     * @param from where it comes out (its portal, up in the sky)
     * @param over the ground it goes round over
     */
    public Serpent(int id, Colour colour, double size, Vec3 from, Vec3 over) {
        this.id = id;
        this.colour = colour == null ? Colour.RED : colour;
        this.size = Math.max(0.5, Math.min(2, size));
        this.maxHealth = health = Math.round(HEALTH * Math.pow(this.size, 1.5));
        this.damage = Math.round(DAMAGE * this.size);
        this.position = from;
        this.cx = over.x();
        this.cz = over.z();
        this.round = (id * 2.399963) % (Math.PI * 2);
        this.sinceSwoop = SWOOP_EVERY * 0.5;
    }

    public int id() { return id; }

    public Colour colour() { return colour; }

    public double size() { return size; }

    public double damage() { return damage; }

    public Phase phase() { return phase; }

    public double heading() { return heading; }

    public String sheet() { return sheet; }

    public boolean loops() { return sheet.equals("fly"); }

    public double sheetTime() { return sheetTime; }

    public double alpha() { return alpha; }

    public boolean gone() { return gone; }

    public Serpent named(String n) {
        name = n == null || n.isBlank() ? null : n.strip();
        return this;
    }

    public Serpent peaceful() {
        hostile = false;
        return this;
    }

    public double flash() {
        return sinceHurt < FLASH ? 1 - sinceHurt / FLASH : 0;
    }

    // --- a target ----------------------------------------------------------------------

    @Override
    public String name() { return name != null ? name : "Vérnees"; }

    @Override
    public Vec3 position() { return position; }

    @Override
    public double radius() { return BODY * size; }

    @Override
    public double height() { return HEIGHT * size; }

    @Override
    public boolean alive() { return health > 0 && phase != Phase.FALL && phase != Phase.DOWN; }

    @Override
    public double health() { return health; }

    @Override
    public double maxHealth() { return maxHealth; }

    @Override
    public void hurt(double amount, Vec3 from, boolean react) {
        if (!alive()) return;
        health -= amount;
        sinceHurt = 0;
        hostile = true;
        if (react && from != null) {
            Vec3 away = position.sub(from);
            double d = Math.hypot(away.x(), away.z());
            if (d > 1e-6) velocity = velocity.add(new Vec3(away.x() / d * 3, 0.6, away.z() / d * 3));
        }
        if (health <= 0) {
            health = 0;
            enter(Phase.FALL);
            play("fall");
        } else if (react && phase == Phase.SWOOP && !struck) {
            // struck out of its dive: away up again
            enter(Phase.CLIMB);
            sinceSwoop = 0;
        }
    }

    @Override
    public Vec3 velocity() { return velocity; }

    @Override
    public double sinceHurt() { return sinceHurt; }

    @Override
    public void freeze(double seconds) {
        frozen = Math.max(frozen, seconds);
    }

    @Override
    public boolean frozen() { return frozen > 0; }

    // --- its flight --------------------------------------------------------------------

    private void enter(Phase p) {
        phase = p;
        time = 0;
        struck = false;
    }

    private void play(String s) {
        if (!s.equals(sheet)) sheetTime = 0;
        sheet = s;
    }

    /** Advance a frame; returns what its dive did. */
    public Outcome tick(double dt, Terrain terrain, Player player) {
        if (gone) return Outcome.NONE;
        if (sinceHurt < Double.MAX_VALUE) sinceHurt += dt;
        if (frozen > 0 && alive()) {
            frozen -= dt;
            return Outcome.NONE;
        }
        time += dt;
        clock += dt;
        sheetTime += dt;
        sinceSwoop += dt;
        double ground = terrain.height(position.x(), position.z());
        Vec3 her = player.feet();
        Outcome out = Outcome.NONE;
        switch (phase) {
            case EMERGE, FLY, CLIMB -> {
                play("fly");
                // the middle of its rounds drifts after her
                double dx = her.x() - cx, dz = her.z() - cz, d = Math.hypot(dx, dz);
                if (hostile && d > ROUNDS * 0.5 && d < FOLLOWS) {
                    double step = Math.min(d, 2.2 * dt);
                    cx += dx / d * step;
                    cz += dz / d * step;
                }
                round += SPEED / (ROUNDS * size) * dt;
                double r = ROUNDS * size * (1 + 0.18 * Math.sin(clock * 0.7 + id));
                double wantY = terrain.height(cx, cz) + ALTITUDE + 1.1 * Math.sin(clock * 1.3 + id * 1.7);
                Vec3 want = new Vec3(cx + Math.cos(round) * r, Math.max(wantY, ground + 2.5), cz + Math.sin(round) * r);
                steer(want, phase == Phase.FLY ? SPEED : SPEED * 1.8, dt);
                if (phase != Phase.FLY && position.distance(want) < 2.5) enter(Phase.FLY);
                if (phase == Phase.FLY && hostile && sinceSwoop >= SWOOP_EVERY && player.health() > 0
                        && Math.hypot(her.x() - cx, her.z() - cz) <= NOTICE) {
                    enter(Phase.WARN);
                    out = Outcome.WARNED;
                }
            }
            case WARN -> {
                velocity = velocity.scale(Math.exp(-dt * 5));
                position = position.add(velocity.scale(dt));
                face(her.sub(position));
                if (time >= WARN_SECONDS) {
                    Vec3 chest = her.add(0, CHEST, 0);
                    Vec3 dir = chest.sub(position);
                    double len = Math.max(1e-6, dir.length());
                    // through her, and on a little
                    swoopAt = chest.add(dir.scale(3.5 / len));
                    enter(Phase.SWOOP);
                }
            }
            case SWOOP -> {
                Vec3 to = swoopAt.sub(position);
                double len = to.length();
                if (len < 1.2 || time > 3.5) {
                    sinceSwoop = 0;
                    enter(Phase.CLIMB);
                    break;
                }
                velocity = to.scale(SWOOP_SPEED / len);
                position = position.add(velocity.scale(dt));
                if (position.y() < ground + 0.3) position = new Vec3(position.x(), ground + 0.3, position.z());
                face(velocity);
                Vec3 chest = her.add(0, CHEST, 0), body = position.add(0, HEIGHT * size * 0.5, 0);
                double near = body.distance(chest);
                if (near < 5 && !sheet.equals("attack")) play("attack");
                if (!struck && near <= CONTACT * Math.max(0.8, size)) {
                    struck = true;
                    out = strike(player);
                }
            }
            case FALL -> {
                velocity = new Vec3(velocity.x() * Math.exp(-dt * 2), velocity.y() - 12 * dt,
                        velocity.z() * Math.exp(-dt * 2));
                position = position.add(velocity.scale(dt));
                if (position.y() <= ground) {
                    position = new Vec3(position.x(), ground, position.z());
                    velocity = Vec3.ZERO;
                    enter(Phase.DOWN);
                    out = Outcome.FELL;
                }
            }
            case DOWN -> {
                if (time > LIES_FOR) alpha = Math.max(0, 1 - (time - LIES_FOR) / FADE);
                if (time > LIES_FOR + FADE) gone = true;
            }
        }
        return out;
    }

    private void steer(Vec3 want, double speed, double dt) {
        Vec3 to = want.sub(position);
        double len = to.length();
        Vec3 aim = len < 1e-6 ? Vec3.ZERO : to.scale(Math.min(speed, len * 2) / len);
        double k = 1 - Math.exp(-dt * 2.5);
        velocity = velocity.add(aim.sub(velocity).scale(k));
        position = position.add(velocity.scale(dt));
        face(velocity);
    }

    private void face(Vec3 d) {
        if (d.x() * d.x() + d.z() * d.z() > 1e-4) heading = SpriteView.headingOf(d.x(), d.z());
    }

    /** Its dive passes through her: rolling she is missed, parrying unhurt, behind a block half. */
    private Outcome strike(Player p) {
        if (p.invulnerable()) return Outcome.DODGED;
        if (p.action() != null && p.action().key().contains("parry")) {
            hurt(0, p.feet(), true);
            return Outcome.PARRIED;
        }
        if (p.stance().block() != null && p.state() == p.stance().block()) {
            p.hurt(Math.round(damage / 2));
            return Outcome.BLOCKED;
        }
        p.hurt(damage);
        double thorns = p.buffs().plus(com.larsons.game.world.Buffs.Kind.THORNS);
        if (thorns > 0) hurt(damage * thorns, p.feet(), true);
        return Outcome.STRUCK;
    }
}
