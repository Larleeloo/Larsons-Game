package com.larsons.game.actor;

import com.larsons.game.ambient.Animals;
import com.larsons.game.combat.Move;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The folk of a place, and what they do: the people stand about, wander a
 * little and turn to her as she comes up; the foes notice her (from farther
 * off the less she hides), come for her, swing, shoot, cast, put up a guard,
 * rock from her blows, and go down. One thing at a time ({@link Folk.Mode}):
 *
 * <pre>
 *   STAND, WANDER   about its spot, until it notices her
 *   CHASE           coming for her - or, with a bow, a crossbow, a wand or a
 *                   staff, to where it can shoot from, and no nearer
 *   STRIKE          its attack plays; the blow lands (or the shot leaves) at
 *                   the moment its animation says, and she can roll through
 *                   it, parry it (it is knocked back) or block it (half)
 *   RECOVER, GUARD  getting its breath - some put their guard up, and take
 *                   a third of what she deals from the front
 *   STAGGER         knocked back by her blow
 *   DOWN            felled: it lies there a while, and fades
 * </pre>
 *
 * <p>No GL and no sprites: how long an attack takes comes from a {@link
 * Clock} (the sheets', in the game; a table's, in the tests).
 */
public final class Folks {

    /** What one did to her, or had done to it. */
    public enum Outcome { NOTICED, STRUCK, BLOCKED, DODGED, PARRIED, GUARDED, FELL }

    /** What happened: who, what, and what it took off her. */
    public record Event(Folk folk, Outcome outcome, double damage) {}

    /** A shot one of them sends at her: the world flies it ({@code Combat#foeShot}). */
    public record Loosed(Folk by, Move.Kind kind, int colour, Vec3 from, Vec3 to, double damage) {}

    /** How long one pass of a person's state lasts, seconds. */
    public interface Clock {
        double seconds(Folk f, AnimState s);
    }

    /**
     * How one is made, where its kind leaves a choice.
     *
     * @param body    "feminine", "masculine", or anything else for either
     * @param weapon  an item ("sword", "battle_axe crystal"), "none", or "own" for one its kind favours
     * @param size    its height times the usual, or 0 for one of its kind's
     * @param seed    what its look is drawn from (0: anything)
     * @param name    its name, or null
     * @param skin    its skin: a colour option of the body ("violet_ombre"), "yellow" or "purple" for a Bortzal; null: any
     * @param hostile whether it fights her on sight
     * @param dress   how it is dressed, or null: as its kind is
     */
    public record Spec(String body, String weapon, double size, long seed, String name, String skin, boolean hostile,
                       Looks.Dress dress) {
        /** One as its kind comes. */
        public static final Spec ANY = new Spec("either", "own", 0, 0, null, null, true, null);

        public Spec weapon(String w) {
            return new Spec(body, w, size, seed, name, skin, hostile, dress);
        }

        public Spec size(double s) {
            return new Spec(body, weapon, s, seed, name, skin, hostile, dress);
        }
    }

    private static final double[] ROUND = {0, 0.6, -0.6, 1.2, -1.2, 1.9, -1.9};

    private final List<Folk> all = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private final List<Loosed> loosed = new ArrayList<>();
    private Random random = new Random(1025);
    private Clock clock = (f, s) -> AnimState.duration(s.defaultFrames(), 30);
    private int nextId = 500000;

    /** Everyone here: the people, the foes, the felled not yet faded. */
    public List<Folk> all() { return all; }

    /**
     * A hole in the sky the Vérnees come out of: where, how wide, in what
     * light - and how many more are to come out of it, one every so often
     * while she is within {@code range} of the ground under it (0: wherever
     * she is).
     */
    public static final class Portal {
        private final int id;
        private final Vec3 at;
        private final double radius, every, range, size;
        private final int rgb;
        private final Serpent.Colour colour;
        private int left;
        private double clock = 1.2;

        public Portal(int id, Vec3 at, double radius, int rgb, int count, double every, double range,
                      Serpent.Colour colour, double size) {
            this.id = id;
            this.at = at;
            this.radius = radius;
            this.rgb = rgb;
            this.left = count;
            this.every = Math.max(0.5, every);
            this.range = range;
            this.colour = colour;
            this.size = size;
        }

        public int id() { return id; }

        public Vec3 at() { return at; }

        public double radius() { return radius; }

        public int rgb() { return rgb; }

        /** How many are still to come out of it. */
        public int left() { return left; }

        /** {@code n} more to come out of it. */
        public void open(int n) {
            left += Math.max(0, n);
        }
    }

    private final List<Serpent> serpents = new ArrayList<>();
    private final List<Portal> portals = new ArrayList<>();

    /** The Vérnees about. */
    public List<Serpent> serpents() { return serpents; }

    public List<Portal> portals() { return portals; }

    public Portal addPortal(Portal p) {
        portals.add(p);
        return p;
    }

    /** A Vérnees out of the sky at {@code from}, to go round over {@code over}: {@code colour} null, any of theirs. */
    public Serpent serpent(Serpent.Colour colour, double size, Vec3 from, Vec3 over) {
        if (colour == null) colour = Serpent.Colour.values()[random.nextInt(Serpent.Colour.values().length)];
        if (size <= 0) size = 0.75 + random.nextDouble() * 0.6;
        Serpent sp = new Serpent(nextId++, colour, size, from, over);
        serpents.add(sp);
        return sp;
    }

    /** Take everyone and everything of theirs away (the level is being put together again). */
    public void clear() {
        all.clear();
        serpents.clear();
        portals.clear();
        events.clear();
    }

    private int emerged;

    /** How many Vérnees have come out of their portals since this was last asked. */
    public int drainEmerged() {
        int n = emerged;
        emerged = 0;
        return n;
    }

    /** A number of its own for something summoned. */
    public int nextId() { return nextId++; }

    /** The foes still standing. */
    public List<Folk> foes() {
        List<Folk> out = new ArrayList<>();
        for (Folk f : all) if (f.alive() && f.role() != Folk.Role.PERSON) out.add(f);
        return out;
    }

    public void seed(long seed) {
        random = new Random(seed);
    }

    /** Take the length of a person's states from {@code c} from now on. */
    public void setClock(Clock c) {
        if (c != null) clock = c;
    }

    public Folk add(Folk f) {
        all.add(f);
        return f;
    }

    /** The person or foe that is level object {@code id}, or null. */
    public Folk byId(int id) {
        for (Folk f : all) if (f.id() == id) return f;
        return null;
    }

    /** The person standing nearest {@code at} that she can talk to, or null. */
    public Folk reachable(Vec3 at) {
        Folk best = null;
        double bestD = Double.MAX_VALUE;
        for (Folk f : all) {
            if (f.role() != Folk.Role.PERSON || !f.inReach(at)) continue;
            double d = f.position().horizontalDistance(at);
            if (d < bestD) {
                best = f;
                bestD = d;
            }
        }
        return best;
    }

    // --- making them -------------------------------------------------------------------

    /** One of {@code breed} at {@code at} (a number of its own for its id). */
    public Folk spawn(Breed breed, Vec3 at, double heading, Spec spec) {
        return spawn(nextId++, breed, at, heading, spec);
    }

    /** One of {@code breed}, the level's object {@code id}, at {@code at}. */
    public Folk spawn(int id, Breed breed, Vec3 at, double heading, Spec spec) {
        if (spec == null) spec = Spec.ANY;
        long seed = spec.seed() != 0 ? spec.seed() : random.nextLong() | 1;
        Random r = new Random(seed);
        boolean masc = "masculine".equals(spec.body()) || (!"feminine".equals(spec.body()) && r.nextBoolean());
        double size = spec.size() > 0 ? spec.size()
                : breed.smallest() + r.nextDouble() * (breed.tallest() - breed.smallest());
        Folk f;
        if (breed.creature()) {
            int outfit = 1 + r.nextInt(5);
            String w = spec.weapon() == null ? "own" : spec.weapon();
            if (w.contains("club") || w.equals("none")) outfit = new int[]{1, 3, 5}[r.nextInt(3)];
            else if (w.contains("bow")) outfit = r.nextBoolean() ? 2 : 4;
            f = new Folk(id, breed, breed.people(), breed.role(), masc, null,
                    "bortzal_" + (masc ? "m" : "f") + outfit, at, heading);
            f.tinted(breed.risen() ? Folk.GRAVE_GREEN : bortzalSkin(spec.skin(), r));
            f.sized(size);
            if (outfit == 2 || outfit == 4) f.archer();
        } else {
            Looks.Dress dress = spec.dress() == null || spec.dress() == Looks.Dress.OWN ? breed.dress() : spec.dress();
            Wardrobe w = Looks.of(breed.people(), masc, dress, seed);
            if (spec.skin() != null && breed.people().skins().contains(spec.skin())) w.setColour(Slot.BODY, spec.skin());
            f = new Folk(id, breed, breed.people(), breed.role(), masc, w, null, at, heading);
            f.crouched = breed.crawls();
            f.sized(size);
            f.arm(weapon(breed, spec.weapon(), r));
        }
        f.walk = breed.walk();
        f.run = breed.run();
        f.sight = breed.sight();
        f.guard = breed.guard();
        f.steady = breed.steady();
        f.hostile = spec.hostile();
        f.named(spec.name());
        f.timer = r.nextDouble() * 3;
        all.add(f);
        return f;
    }

    /** Someone to talk to, of {@code people}: the level's object {@code id}. */
    public Folk person(int id, People people, Vec3 at, double heading, Spec spec) {
        if (spec == null) spec = Spec.ANY;
        long seed = spec.seed() != 0 ? spec.seed() : id * 7919L + 13;
        Random r = new Random(seed);
        boolean masc = "masculine".equals(spec.body()) || (!"feminine".equals(spec.body()) && r.nextBoolean());
        Wardrobe w = Looks.of(people, masc, spec.dress(), seed);
        if (spec.skin() != null && people.skins().contains(spec.skin())) w.setColour(Slot.BODY, spec.skin());
        Folk f = new Folk(id, null, people, Folk.Role.PERSON, masc, w, null, at, heading);
        double lo = Math.max(0.7, people.smallest()), hi = Math.min(1.3, people.tallest());
        f.sized(spec.size() > 0 ? spec.size() : lo + r.nextDouble() * (hi - lo));
        Item held = spec.weapon() == null || spec.weapon().equals("own") || spec.weapon().equals("none") ? null
                : Item.parse(spec.weapon());
        f.arm(held);
        f.walk = 1.3;
        f.hostile = false;
        f.named(spec.name());
        f.timer = r.nextDouble() * 4;
        all.add(f);
        return f;
    }

    private static int bortzalSkin(String skin, Random r) {
        if (skin != null) {
            if (skin.contains("purple") || skin.contains("violet")) return Folk.ASHY_PURPLE;
            if (skin.contains("yellow") || skin.contains("blonde")) return Folk.GOBLIN_YELLOW;
        }
        return r.nextInt(3) == 0 ? Folk.ASHY_PURPLE : Folk.GOBLIN_YELLOW;
    }

    private static Item weapon(Breed breed, String asked, Random r) {
        if (asked == null || asked.isBlank() || asked.equals("own")) {
            if (breed.weapons().isEmpty()) return null;
            return Item.parse(breed.weapons().get(r.nextInt(breed.weapons().size())));
        }
        if (asked.equals("none")) return null;
        Item item = Item.parse(asked.replace(' ', '_'));
        if (item == null) item = Item.parse(asked);
        return item != null && item.def().carrySlot() != null ? item : null;
    }

    // --- what they do ------------------------------------------------------------------

    /**
     * A frame: everyone goes about what they are doing. {@code her} is she as
     * they see her (null: nobody is playing - they only stand about).
     */
    public List<Event> tick(double dt, Animals.Quarry her, Animals.Place place) {
        events.clear();
        for (Folk f : all) one(f, dt, her, place);
        apart();
        if (her != null) {
            for (Portal p : portals) {
                if (p.left <= 0) continue;
                Vec3 me = her.feet();
                if (p.range > 0 && Math.hypot(me.x() - p.at.x(), me.z() - p.at.z()) > p.range) continue;
                p.clock -= dt;
                if (p.clock > 0) continue;
                p.clock = p.every;
                p.left--;
                double gx = p.at.x(), gz = p.at.z();
                serpent(p.colour, p.size, p.at, new Vec3(gx, place.ground(gx, gz), gz));
                emerged++;
            }
        }
        all.removeIf(Folk::gone);
        return List.copyOf(events);
    }

    /** The shots loosed since the last call. */
    public List<Loosed> drainLoosed() {
        List<Loosed> out = List.copyOf(loosed);
        loosed.clear();
        return out;
    }

    private void one(Folk f, double dt, Animals.Quarry her, Animals.Place place) {
        f.sinceHurt += dt;
        f.modeTime += dt;
        f.cooldown -= dt;
        f.mvx = f.mvz = 0;
        if (f.guardedHit) {
            events.add(new Event(f, Outcome.GUARDED, 0));
            f.guardedHit = false;
        }
        if (f.mode == Folk.Mode.DOWN) {
            f.advance(dt);
            if (!f.struck) {
                f.struck = true;
                events.add(new Event(f, Outcome.FELL, 0));
            }
            f.downFor += dt;
            if (f.downFor > Folk.LIES_FOR) {
                f.alpha = Math.max(0, 1 - (f.downFor - Folk.LIES_FOR) / Folk.FADE);
                if (f.alpha <= 0) f.gone = true;
            }
            return;
        }
        if (f.frozen > 0) {
            f.frozen -= dt;
            return;
        }
        f.advance(dt);
        if (Math.abs(f.vx) + Math.abs(f.vz) > 0.05) {
            double nx = f.x + f.vx * dt, nz = f.z + f.vz * dt;
            if (free(f, nx, nz, place)) {
                f.x = nx;
                f.z = nz;
            }
            double k = Math.exp(-7 * dt);
            f.vx *= k;
            f.vz *= k;
        }
        if (f.role() == Folk.Role.PERSON || !f.hostile || her == null || her.down()) about(f, dt, her, place);
        else fight(f, dt, her, place);
        f.y = place.ground(f.x, f.z);
    }

    /** Standing about its spot, wandering a little; a person turns to her as she comes up. */
    private void about(Folk f, double dt, Animals.Quarry her, Animals.Place place) {
        if (f.mode == Folk.Mode.STAGGER) {
            if (f.modeTime > 0.35) f.enter(Folk.Mode.STAND);
            return;
        }
        if (f.mode != Folk.Mode.STAND && f.mode != Folk.Mode.WANDER) f.enter(Folk.Mode.STAND);
        if (f.talking && her != null) {
            f.faceTo(her.feet());
            f.act(Folk.Act.IDLE, 0);
            return;
        }
        if (f.mode == Folk.Mode.WANDER) {
            if (go(f, f.toX, f.toZ, f.walk, dt, place) || f.modeTime > 9) {
                f.enter(Folk.Mode.STAND);
                f.timer = 2 + random.nextDouble() * 6;
                f.act(Folk.Act.IDLE, 0);
            }
            return;
        }
        f.act(Folk.Act.IDLE, 0);
        if (f.role() == Folk.Role.PERSON && her != null) {
            Vec3 h = her.feet();
            double dx = h.x() - f.x, dz = h.z() - f.z;
            if (dx * dx + dz * dz < 9) {
                turn(f, dx, dz, dt);
                return;
            }
        }
        f.timer -= dt;
        if (f.timer <= 0) {
            f.timer = 3 + random.nextDouble() * 6;
            if (f.roam > 0.5) {
                double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * f.roam;
                double tx = f.homeX + Math.cos(a) * d, tz = f.homeZ + Math.sin(a) * d;
                if (free(f, tx, tz, place)) {
                    f.toX = tx;
                    f.toZ = tz;
                    f.enter(Folk.Mode.WANDER);
                }
            }
        }
    }

    private void fight(Folk f, double dt, Animals.Quarry her, Animals.Place place) {
        Vec3 h = her.feet();
        double dx = h.x() - f.x, dz = h.z() - f.z, dist = Math.hypot(dx, dz), dy = h.y() - f.y;
        boolean sees = f.relentless || dist <= (f.alerted ? f.sight * 1.6 : f.sight * her.conspicuous());
        switch (f.mode) {
            case STAND, WANDER -> {
                if (sees) {
                    if (!f.alerted) events.add(new Event(f, Outcome.NOTICED, 0));
                    f.alerted = true;
                    f.lostFor = 0;
                    f.enter(Folk.Mode.CHASE);
                } else {
                    about(f, dt, her, place);
                }
            }
            case CHASE -> {
                if (!sees) {
                    f.lostFor += dt;
                    if (f.lostFor > 3) {
                        f.alerted = false;
                        f.toX = f.homeX;
                        f.toZ = f.homeZ;
                        f.enter(Folk.Mode.WANDER);
                        return;
                    }
                } else {
                    f.lostFor = 0;
                }
                if (f.shot() != null) {
                    if (dist > f.keep) {
                        go(f, h.x(), h.z(), f.run, dt, place);
                    } else if (dist < 2.6 && dist > 1e-3 && f.cooldown > 0.2) {
                        go(f, f.x - dx / dist * 4, f.z - dz / dist * 4, f.walk * 1.4, dt, place);
                    } else {
                        turn(f, dx, dz, dt * 2);
                        f.act(Folk.Act.IDLE, 0);
                    }
                    if (f.cooldown <= 0 && dist <= f.keep * 1.2 && dist > 1.0) strike(f, dx, dz);
                } else {
                    double near = f.reach + 0.3;
                    if (dist > near * 0.85) {
                        go(f, h.x(), h.z(), f.run, dt, place);
                    } else {
                        turn(f, dx, dz, dt * 2);
                        f.act(Folk.Act.IDLE, 0);
                    }
                    if (f.cooldown <= 0 && dist <= near && Math.abs(dy) < 1.8) strike(f, dx, dz);
                }
            }
            case STRIKE -> {
                if (!f.creature() && f.stance().drawn() && f.act() == Folk.Act.ATTACK && f.modeTime >= f.timer) {
                    f.act(Folk.Act.LOOSE, 0);
                }
                if (!f.struck && f.modeTime >= f.blowAt) {
                    f.struck = true;
                    blow(f, her);
                }
                if (f.modeTime >= f.strikeFor && f.mode == Folk.Mode.STRIKE) {
                    f.cooldown = f.every * (0.8 + 0.4 * random.nextDouble());
                    if (f.guard > 0 && f.shot() == null && random.nextDouble() < f.guard) {
                        f.enter(Folk.Mode.GUARD);
                        f.timer = 0.7 + random.nextDouble() * 0.8;
                        f.act(Folk.Act.GUARD, 0);
                    } else {
                        f.enter(Folk.Mode.RECOVER);
                    }
                }
            }
            case RECOVER -> {
                f.act(Folk.Act.IDLE, 0);
                turn(f, dx, dz, dt);
                if (f.modeTime > 0.25) f.enter(Folk.Mode.CHASE);
            }
            case GUARD -> {
                turn(f, dx, dz, dt * 2);
                f.timer -= dt;
                if (f.timer <= 0) f.enter(Folk.Mode.CHASE);
            }
            case STAGGER -> {
                if (f.modeTime > (f.creature() ? Folk.CREATURE_HURT : 0.35)) f.enter(Folk.Mode.CHASE);
            }
            default -> f.enter(Folk.Mode.CHASE);
        }
    }

    /** Its attack begins: it turns on her once, and from then on swings where it was aimed. */
    private void strike(Folk f, double dx, double dz) {
        if (dx * dx + dz * dz > 1e-6) f.heading = SpriteView.headingOf(dx, dz);
        f.enter(Folk.Mode.STRIKE);
        if (f.creature()) {
            f.act(Folk.Act.ATTACK, 0);
            f.blowAt = Folk.CREATURE_BLOW;
            f.strikeFor = Folk.CREATURE_ATTACK;
        } else if (f.breed() != null && f.breed().spits()) {
            f.act(Folk.Act.SPIT, 0);
            double d = clock.seconds(f, f.state());
            f.blowAt = d * 0.45;
            f.strikeFor = d * 0.8;
        } else {
            f.act(Folk.Act.ATTACK, 0);
            double d = clock.seconds(f, f.state());
            if (f.stance().drawn()) {
                AnimState loose = f.stance().release(false);
                f.timer = d;
                f.blowAt = d + 0.03;
                f.strikeFor = d + (loose == null ? 0.3 : clock.seconds(f, loose));
            } else {
                Move m = Move.of(f.state(), f.weapon());
                f.blowAt = d * (m == null ? 0.45 : m.at());
                f.strikeFor = d;
            }
        }
    }

    /** Its blow lands - or does not: she may have stepped away, rolled, parried or blocked. */
    private void blow(Folk f, Animals.Quarry her) {
        Vec3 h = her.feet();
        Vec3 fwd = SpriteView.headingVector(f.heading);
        if (f.shot() != null) {
            double up = (f.crouched ? 0.6 : 1.3) * f.size();
            Vec3 from = new Vec3(f.x + fwd.x() * 0.45 * f.size(), f.y + up, f.z + fwd.z() * 0.45 * f.size());
            Vec3 to = h.add((random.nextDouble() - 0.5) * 0.7, 1.0, (random.nextDouble() - 0.5) * 0.7);
            loosed.add(new Loosed(f, f.shot(), f.shotColour(), from, to, f.damage));
            return;
        }
        double dx = h.x() - f.x, dz = h.z() - f.z, dist = Math.hypot(dx, dz);
        double ahead = dist < 1e-6 ? 1 : (dx * fwd.x() + dz * fwd.z()) / dist;
        if (dist > f.reach + 0.45 || ahead < 0.3 || Math.abs(h.y() - f.y) > 2) return;
        if (her.dodging()) {
            events.add(new Event(f, Outcome.DODGED, 0));
            return;
        }
        if (her.parrying()) {
            if (dist > 1e-6) {
                f.vx = -dx / dist * 3.5;
                f.vz = -dz / dist * 3.5;
            }
            f.enter(Folk.Mode.STAGGER);
            f.act(Folk.Act.HURT, 0);
            f.cooldown = f.every;
            events.add(new Event(f, Outcome.PARRIED, 0));
            return;
        }
        if (her.blocking()) {
            events.add(new Event(f, Outcome.BLOCKED, her.hurt(Math.round(f.damage / 2))));
            return;
        }
        events.add(new Event(f, Outcome.STRUCK, her.hurt(f.damage)));
    }

    // --- going about -------------------------------------------------------------------

    /** Toward {@code (tx, tz)} at {@code speed}, round what is in the way; true when there (or stuck). */
    private boolean go(Folk f, double tx, double tz, double speed, double dt, Animals.Place place) {
        double dx = tx - f.x, dz = tz - f.z, d = Math.hypot(dx, dz);
        if (d < 0.25) return true;
        double ux = dx / d, uz = dz / d, step = Math.min(d, speed * dt);
        int side = (f.id() & 1) == 0 ? 1 : -1;
        for (double t : ROUND) {
            double c = Math.cos(t * side), s = Math.sin(t * side);
            double vx = ux * c - uz * s, vz = ux * s + uz * c;
            double nx = f.x + vx * step, nz = f.z + vz * step;
            if (!free(f, nx, nz, place)) continue;
            f.x = nx;
            f.z = nz;
            f.mvx = vx * speed;
            f.mvz = vz * speed;
            turn(f, vx, vz, dt);
            f.act(speed > f.walk * 1.25 ? Folk.Act.RUN : Folk.Act.WALK, speed);
            return false;
        }
        f.act(Folk.Act.IDLE, 0);
        return true;
    }

    private static boolean free(Folk f, double x, double z, Animals.Place place) {
        double[] b = place.bounds();
        if (b != null && (x < b[0] + 0.5 || x > b[1] - 0.5 || z < b[2] + 0.5 || z > b[3] - 0.5)) return false;
        if (place.blocked(x, z, f.radius() * 0.8)) return false;
        double g = place.ground(x, z);
        if (Math.abs(g - f.y) > 1.3) return false;
        return place.water(x, z, g) < 0.9;
    }

    /** Turn towards {@code (dx, dz)}, as fast as it can in {@code dt}. */
    private static void turn(Folk f, double dx, double dz, double dt) {
        if (dx * dx + dz * dz < 1e-9) return;
        double want = SpriteView.headingOf(dx, dz), diff = want - f.heading;
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        double most = 9 * dt;
        f.heading += Math.max(-most, Math.min(most, diff));
    }

    /** Nobody stands in anybody. */
    private void apart() {
        for (int i = 0; i < all.size(); i++) {
            Folk a = all.get(i);
            if (!a.standing()) continue;
            for (int j = i + 1; j < all.size(); j++) {
                Folk b = all.get(j);
                if (!b.standing()) continue;
                double dx = b.x - a.x, dz = b.z - a.z, d = Math.hypot(dx, dz), r = a.radius() + b.radius();
                if (d >= r || Math.abs(a.y - b.y) > 2) continue;
                if (d < 1e-6) {
                    dx = 1;
                    dz = 0;
                    d = 1;
                }
                double push = (r - d) / 2;
                boolean fixA = a.role() == Folk.Role.PERSON, fixB = b.role() == Folk.Role.PERSON;
                if (!fixA) {
                    a.x -= dx / d * push;
                    a.z -= dz / d * push;
                }
                if (!fixB) {
                    b.x += dx / d * push;
                    b.z += dz / d * push;
                }
            }
        }
    }
}
