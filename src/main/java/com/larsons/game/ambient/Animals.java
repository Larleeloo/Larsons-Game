package com.larsons.game.ambient;

import com.larsons.game.world.Terrain;
import com.larsons.game.world.Player;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.world.Biome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

/**
 * The animals living round the player - those that come by where their
 * {@link AnimalKind} lives, as many as the level's {@link Life} asks, and
 * the herds placed in Creative Mode ({@link Herd}), which keep round their
 * spot.
 *
 * <p><b>Who lives here</b> is the level's to say: none come by on their
 * own unless its Life page turns a kind up ({@link Life}) - the biome does
 * not bring them. Turned up, every second it looks round her, out to
 * {@value #REACH} m, at the water there (a duck wants a pond near), and
 * brings in or lets go of each kind to match its {@link AnimalKind#cap}
 * times the level's number for it. A new one comes out of her sight's way
 * (past {@value #COME} m), often near another of its kind - they keep in
 * herds - and those left far behind are gone.
 *
 * <p><b>What they do</b> is their {@link Temperament}'s: they stand about,
 * graze and wander round home; a skittish one runs when she comes near (a
 * duck takes off); a friendly one comes up to her and keeps by her a while;
 * a defensive one warns her off - a goose's hiss - and goes for her if she
 * stays; a hostile one goes for her on sight. Hit, a calm one runs and a
 * defensive one fights back (a bee's whole swarm with it). A blow of theirs
 * is dodged by her roll, turned by her parry, halved by her block. They give
 * up when she gets away or {@value #CALM_DOWN} s after she last provoked
 * them; beaten - their health gone - they run off, and are gone. They keep
 * out of deep water (the birds swim on it), off cliffs, and round the trees
 * and the rocks.
 *
 * <p>Pure simulation: the place comes in through {@link Place}, the player
 * through {@link Quarry}, the sounds go out through {@link Critters.Voice},
 * and what they did to her comes back as {@link Event}s; {@code
 * world/WorldRenderer} draws them.
 */
public final class Animals {

    /** How far round the player the ones that come by live, metres. */
    public static final double REACH = 34;
    /** How near her a new one may come in, metres (nearer, she would see it appear); a water bird nearer. */
    public static final double COME = 16, COME_TO_WATER = 8;
    /** How far a placed herd's are kept while she is away, metres. */
    public static final double HERD_REACH = 70;
    /** How far its sounds carry, metres. */
    public static final double HEARD = 26;
    /** Seconds after she last provoked one that it gives up on her. */
    public static final double CALM_DOWN = 12;
    /** How far from it she must get for one going for her to give up, metres. */
    public static final double GIVE_UP = 20;
    /** How long a blow takes to land once it is wound up, seconds; and between one and the next. */
    public static final double WIND_UP = 0.32, BETWEEN = 1.3;
    /** How far round a stung bee its swarm comes with it, metres. */
    public static final double SWARM = 8;
    /** A friendly one notices her this near, metres. */
    public static final double FRIENDLY = 9;
    /** A hostile one goes for her this near, metres. */
    public static final double SIGHT = 14;

    /** What the world is like where they are: the critters' ground, water and biomes, and what is in the way. */
    public interface Place extends Critters.Place {
        /** Whether something solid (a tree, a rock) stands within {@code radius} of {@code (x, z)}. */
        default boolean blocked(double x, double z, double radius) { return false; }

        /** The level's edges they keep within - x from, x to, z from, z to - or null (none). */
        default double[] bounds() { return null; }

        /** Whether a building (a wall, a house) stands at {@code (x, z)} taller than {@code y}: nobody sees through it. */
        default boolean sightBlocked(double x, double z, double y) { return false; }
    }

    /** How far inside a level's edges they keep, metres. */
    static final double EDGE = 0.75;

    /** Whether {@code (x, z)} is inside the level's edges (by {@link #EDGE}), or there are none. */
    static boolean inside(double x, double z, Place place) {
        double[] b = place.bounds();
        return b == null || (x >= b[0] + EDGE && x <= b[1] - EDGE && z >= b[2] + EDGE && z <= b[3] - EDGE);
    }

    /**
     * Back inside the level's edges, if it got out (pushed, staggered, or put
     * there): onto the edge, its way out stopped - one that flies off may go,
     * it is fading away.
     */
    static void keepIn(Animal a, Place place) {
        double[] b = place.bounds();
        if (b == null || a.mode == Animal.Mode.FLY_OFF) return;
        double x0 = b[0] + EDGE, x1 = b[1] - EDGE, z0 = b[2] + EDGE, z1 = b[3] - EDGE;
        if (x0 > x1 || z0 > z1) return;
        boolean out = false;
        if (a.x < x0) { a.x = x0; a.vx = Math.max(0, a.vx); out = true; }
        if (a.x > x1) { a.x = x1; a.vx = Math.min(0, a.vx); out = true; }
        if (a.z < z0) { a.z = z0; a.vz = Math.max(0, a.vz); out = true; }
        if (a.z > z1) { a.z = z1; a.vz = Math.min(0, a.vz); out = true; }
        if (out) {
            // where it was going is outside too: somewhere back in
            a.toX = Math.max(x0, Math.min(x1, a.toX));
            a.toZ = Math.max(z0, Math.min(z1, a.toZ));
            settle(a, place);
        }
    }

    /** The player, as the animals see her. */
    public interface Quarry {
        /** Her feet. */
        Vec3 feet();

        /** Rolling: a blow goes through her. */
        boolean dodging();

        /** Parrying: a blow is turned, and its giver knocked back. */
        boolean parrying();

        /** Behind her block: a blow takes half. */
        boolean blocking();

        /** Take {@code damage}; returns what was taken. */
        double hurt(double damage);

        /** Whether she is down (her health gone): they leave her be. */
        boolean down();

        /**
         * How easily she is noticed ({@code world.Stealth}): what every
         * distance an animal notices her at is multiplied by (1: in the open).
         */
        default double conspicuous() { return 1; }
    }

    /**
     * A herd placed in a level: {@code count} of a kind living within {@code
     * radius} m of {@code at}, of their own nature or {@code temperament}'s
     * (null: their own), about when {@code when} says - each in one of its
     * {@code colours} (0xRRGGBB, picked at random for each one, -1 for one as
     * drawn; none: all as drawn), {@code sizeMin} to {@code sizeMax} times its
     * kind's size. A {@code single} one is one animal, put just where it
     * stands, facing {@code heading}, wandering no further than {@code radius}.
     */
    public record Herd(AnimalKind kind, Vec3 at, int count, double radius, Temperament temperament,
                       Critters.When when, boolean saddled, List<Integer> colours, double sizeMin, double sizeMax,
                       boolean single, double heading) {

        public Herd {
            colours = colours == null ? List.of() : List.copyOf(colours);
            sizeMin = Math.max(0.2, sizeMin);
            sizeMax = Math.max(sizeMin, sizeMax);
        }

        /** A herd of its kind's colours and size. */
        public Herd(AnimalKind kind, Vec3 at, int count, double radius, Temperament temperament,
                    Critters.When when, boolean saddled) {
            this(kind, at, count, radius, temperament, when, saddled, List.of(), 1, 1, false, 0);
        }

        /** A herd without the saddle on (any but horses are never saddled). */
        public Herd(AnimalKind kind, Vec3 at, int count, double radius, Temperament temperament,
                    Critters.When when) {
            this(kind, at, count, radius, temperament, when, false);
        }
    }

    /** How long one hunted down lies where it fell before it fades, seconds. */
    public static final double LIES_FOR = 14;

    /** What an animal did to her, or had done to it, for the world to say and to play. */
    public enum Outcome { WARNED, STRUCK, BLOCKED, DODGED, PARRIED, RAN_OFF, DOWN }

    /** {@code animal} did {@code outcome}; {@code damage} what it took from her. */
    public record Event(Animal animal, Outcome outcome, double damage) {}

    private final List<Animal> live = new ArrayList<>();
    private final List<Herd> herds = new ArrayList<>();
    private final EnumMap<AnimalKind, Double> share = new EnumMap<>(AnimalKind.class);
    private final List<Event> events = new ArrayList<>();
    private Life life = new Life();
    private Random random = new Random(20261102);
    private double survey, thin;
    private boolean enabled = true;
    private boolean still;

    public Animals() {
        for (AnimalKind k : AnimalKind.values()) share.put(k, 0.0);
    }

    /** The ones about now. */
    public List<Animal> live() { return Collections.unmodifiableList(live); }

    /** The herds placed in the level. */
    public List<Herd> herds() { return herds; }

    public Life life() { return life; }

    public void setLife(Life l) {
        life = l == null ? new Life() : l;
    }

    /**
     * Whether they stand still where they are put - Creative Mode's view,
     * where nobody plays: every herd all there at once, each one standing on
     * its own spot (a single one just where it was placed) facing its way and
     * going nowhere until the level is played, so where they live is plain to
     * see; and a kind turned off there (none to come by) gone at once.
     */
    public void setStill(boolean on) {
        still = on;
    }

    public boolean still() { return still; }

    /** Whether any come at all (off: none, and those about are gone). */
    public void setEnabled(boolean on) {
        enabled = on;
        if (!on) live.clear();
    }

    public void seed(long seed) {
        random = new Random(seed);
    }

    /** Send every one away at once (a new level). */
    public void clear() {
        live.clear();
        herds.clear();
        survey = 0;
    }

    /** How many of {@code kind} are about. */
    public int count(AnimalKind kind) {
        int n = 0;
        for (Animal a : live) if (a.kind == kind && !a.leaving && !a.gone) n++;
        return n;
    }

    /** How many of a kind that come by belong round the player now (the last look round). */
    public double wanted(AnimalKind kind, double day) {
        return kind.cap() * life.density(kind) * kind.out(day) * Math.min(1.5, share.get(kind));
    }

    /** Take {@code a} out of the world at once (a card turned it into something else). */
    public void remove(Animal a) {
        if (a == null) return;
        a.gone = true;
        a.alpha = 0;
        live.remove(a);
    }

    /** Put one in, now, at {@code at} (a script, a test): of its own nature, or {@code temperament}'s. */
    public Animal add(AnimalKind kind, Vec3 at, Temperament temperament, Place place) {
        Animal a = make(kind, null, temperament == null ? kind.temperament() : temperament, at.x(), at.z(), place);
        a.alpha = 1;
        a.roam = 6;
        live.add(a);
        return a;
    }

    /**
     * Advance them {@code dt} seconds: {@code time} the world's clock, {@code
     * day} how light it is (0 night, 1 day), {@code focus} where they live
     * round (the player, or the editor's view), {@code her} the player - or
     * null (nobody playing: they only go about). Returns what they did to her.
     */
    public List<Event> tick(double dt, double time, double day, Vec3 focus, Quarry her, Place place,
                            Critters.Voice voice) {
        events.clear();
        if (!enabled || focus == null) return events;
        survey -= dt;
        if (survey <= 0) {
            survey = 1;
            look(focus, place);
        }
        populate(dt, day, focus, place);
        Quarry q = her != null && !her.down() ? her : null;
        for (int i = 0; i < live.size(); i++) {
            Animal a = live.get(i);
            if (a.ridden) {
                // she rides it: the world moves it, and its sheet goes by her clock
                a.sinceHurt += dt;
                a.alpha = 1;
                continue;
            }
            a.sheetTime += dt * a.rate;
            a.sinceHurt += dt;
            a.modeTime += dt;
            a.cooldown = Math.max(0, a.cooldown - dt);
            a.ignore = Math.max(0, a.ignore - dt);
            boolean fading = a.leaving || a.mode == Animal.Mode.RUN_OFF
                    || (a.mode == Animal.Mode.FLY_OFF && a.modeTime > 3.5)
                    || (a.mode == Animal.Mode.DOWN && a.modeTime > LIES_FOR);
            double fade = a.mode == Animal.Mode.RUN_OFF || a.mode == Animal.Mode.DOWN ? 2.6 : 1.2;
            a.alpha = fading ? Math.max(0, a.alpha - dt / fade) : Math.min(1, a.alpha + dt / 0.8);
            if (a.alpha <= 0 && fading) a.gone = true;
            if (still) {
                stand(a, place);
                continue;
            }
            if (a.frozen > 0) {
                // a card's Freeze: it stands where it is, its sheet stopped
                a.frozen -= dt;
                a.vx = a.vz = 0;
                a.sheetTime -= dt * a.rate;
                continue;
            }
            react(a, q, place, voice, focus);
            behave(a, dt, time, q, place, voice, focus);
            keepIn(a, place);
            dress(a);
        }
        live.removeIf(a -> a.gone);
        return events;
    }

    // --- who lives here ---------------------------------------------------------------

    /** Look round {@code focus}: how much of it is home to each kind. */
    void look(Vec3 focus, Place place) {
        EnumMap<AnimalKind, Double> sum = new EnumMap<>(AnimalKind.class);
        for (AnimalKind k : AnimalKind.values()) sum.put(k, 0.0);
        int n = 0;
        for (double r : new double[]{6, 16, 28}) {
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8 + r * 0.37;
                double x = focus.x() + Math.cos(a) * r, z = focus.z() + Math.sin(a) * r;
                n++;
                for (AnimalKind k : AnimalKind.values()) {
                    // (anywhere it can live: the biome brings none, the level's number does)
                    double w = 1;
                    if (k.nearWater() > 0 && !Critters.nearWater(x, z, k.nearWater(), place)) w = 0;
                    sum.put(k, sum.get(k) + w);
                }
            }
        }
        for (AnimalKind k : AnimalKind.values()) share.put(k, sum.get(k) / n);
    }

    /** One standing still where it was put (the editor's view): its idle (a bird on the water floats), at rest. */
    private static void stand(Animal a, Place place) {
        a.vx = a.vz = 0;
        if (!a.leaving) a.alpha = 1;
        if (a.mode != Animal.Mode.REST) a.enter(Animal.Mode.REST);
        settle(a, place);
        dress(a);
    }

    private void populate(double dt, double day, Vec3 focus, Place place) {
        thin -= dt;
        for (AnimalKind k : AnimalKind.values()) {
            long want = Math.round(wanted(k, day));
            if (life.density(k) <= 0) {
                // turned off: none come by, and those that came by go - at once where nobody plays (one put
                // there by a card, a script, a herd, stays)
                for (Animal a : live) {
                    if (a.kind != k || !a.wild || a.herd != null || a.saddled || a.ridden) continue;
                    if (still) a.gone = true;
                    else a.leaving = true;
                }
                continue;
            }
            int have = 0;
            for (Animal a : live) if (a.kind == k && a.wild && a.herd == null && !a.leaving && !a.saddled) have++;
            if (have < want && spawnFree(k, focus, place)) have++;
            if (have > want && thin <= 0) {
                thin = 0.5;
                Animal far = null;
                double fd = -1;
                for (Animal a : live) {
                    if (a.kind != k || !a.wild || a.herd != null || a.leaving || a.hostile() || a.saddled) continue;
                    double d = Math.hypot(a.x - focus.x(), a.z - focus.z());
                    if (d > fd) {
                        fd = d;
                        far = a;
                    }
                }
                if (far != null && fd > COME * 0.8) far.leaving = true;
            }
        }
        for (Animal a : live) {
            // a herd taken out of the level (or moved, or changed - the editor's ghost under the pointer) takes
            // its own with it, saddled ones and all; only a horse she saddled herself is hers to keep
            if (a.herd != null && !herds.contains(a.herd) && !a.saddledByHer && !a.ridden) {
                a.leaving = true;
                // a ghost's, and any in the editor's still view: gone at once, not left fading in a trail
                if (a.saddled || still) a.gone = true;
                continue;
            }
            if (a.saddled) continue;        // a saddled horse stays where she left it, however far she goes
            if (a.herd == null && Math.hypot(a.x - focus.x(), a.z - focus.z()) > REACH + 12) a.gone = true;
        }
        for (Herd h : herds) {
            boolean near = Math.hypot(h.at().x() - focus.x(), h.at().z() - focus.z()) < HERD_REACH;
            int want = near ? (int) Math.round(h.count() * (still ? 1 : when(h, day))) : 0;
            if (still && near) {
                // the editor's view: the whole herd there at once, where each will stand
                int have = 0;
                for (Animal a : live) if (h.equals(a.herd) && !a.leaving) have++;
                for (int tries = 0; have < want && tries < want * 3; tries++) {
                    if (spawnIn(h, focus, place)) have++;
                }
                continue;
            }
            int have = 0;
            Animal extra = null;
            for (Animal a : live) {
                if (!h.equals(a.herd) || a.leaving) continue;
                // the beaten and the flown are not back until the herd is next made up
                have++;
                if (!a.hostile() && !a.saddled) extra = a;
            }
            if (!near) {
                for (Animal a : live) if (h.equals(a.herd) && !a.saddled) a.gone = true;
            } else if (have < want) {
                spawnIn(h, focus, place);
            } else if (have > want && extra != null) {
                extra.leaving = true;
            }
        }
    }

    /** How many of a herd are about at this hour, of its count. */
    static double when(Herd h, double day) {
        return switch (h.when()) {
            case ITS_TIME -> h.kind().out(day);
            case ALWAYS -> 1;
            case DAY -> CritterKind.smooth(0.35, 0.6, day);
            case NIGHT -> CritterKind.smooth(0.35, 0.6, 1 - day);
        };
    }

    private boolean spawnFree(AnimalKind k, Vec3 focus, Place place) {
        // often near another of its kind: they keep in herds
        Animal kin = null;
        if (k.herd() > 1 && random.nextDouble() < 0.75) {
            for (Animal a : live) {
                if (a.kind == k && a.herd == null && !a.leaving && a.alpha > 0.2) {
                    int near = 0;
                    for (Animal b : live) if (b.kind == k && Math.hypot(b.x - a.x, b.z - a.z) < 6) near++;
                    if (near < k.herd()) {
                        kin = a;
                        break;
                    }
                }
            }
        }
        for (int t = 0; t < 10; t++) {
            double x, z;
            if (kin != null && t < 5) {
                double a = random.nextDouble() * Math.PI * 2, r = 1.5 + random.nextDouble() * 3;
                x = kin.x + Math.cos(a) * r;
                z = kin.z + Math.sin(a) * r;
                if (Math.hypot(x - focus.x(), z - focus.z()) < COME * 0.7) continue;
            } else {
                double a = random.nextDouble() * Math.PI * 2, in = k.nearWater() > 0 ? COME_TO_WATER : COME;
                double r = Math.sqrt(in * in + random.nextDouble() * (REACH * REACH - in * in));
                x = focus.x() + Math.cos(a) * r;
                z = focus.z() + Math.sin(a) * r;
                if (k.nearWater() > 0 && !Critters.nearWater(x, z, k.nearWater(), place)) continue;
            }
            if (!livable(k, x, z, place)) continue;
            // a swan comes on the water, mostly; a duck often
            boolean wet = place.water(x, z, place.ground(x, z)) > 0.15;
            if (k.swims() && !wet && t < 8 && random.nextDouble() < (k == AnimalKind.SWAN ? 0.8 : 0.4)) continue;
            Animal a = make(k, null, k.temperament(), x, z, place);
            a.wild = true;
            a.roam = 10;
            if (kin != null) {
                a.homeX = kin.homeX;
                a.homeZ = kin.homeZ;
            }
            live.add(a);
            return true;
        }
        return false;
    }

    private boolean spawnIn(Herd h, Vec3 focus, Place place) {
        AnimalKind k = h.kind();
        if (h.single()) {
            // one animal, just where it was put, facing the way it was turned
            Animal an = make(k, h, h.temperament() == null ? k.temperament() : h.temperament(), h.at().x(),
                    h.at().z(), place);
            an.heading = h.heading();
            an.roam = Math.max(0.5, h.radius());
            an.timer = 3 + random.nextDouble() * 4;
            if (h.saddled() && k == AnimalKind.HORSE) an.saddled = true;
            if (still) an.alpha = 1;
            live.add(an);
            return true;
        }
        for (int t = 0; t < 8; t++) {
            double a = random.nextDouble() * Math.PI * 2, r = h.radius() * Math.sqrt(random.nextDouble());
            double x = h.at().x() + Math.cos(a) * r, z = h.at().z() + Math.sin(a) * r;
            // not in deep water (unless it swims), nor in a tree, nor under her nose: somewhere else
            if (t < 7 && !passable(k, x, z, place)) continue;
            if (!still && t < 5 && Math.hypot(x - focus.x(), z - focus.z()) < 8) continue;
            Animal an = make(k, h, h.temperament() == null ? k.temperament() : h.temperament(), x, z, place);
            an.homeX = h.at().x();
            an.homeZ = h.at().z();
            an.roam = Math.max(1, h.radius());
            if (h.saddled() && k == AnimalKind.HORSE) {
                an.saddled = true;
                an.roam = Math.min(an.roam, Animal.STAY * 2);
            }
            if (still) an.alpha = 1;
            live.add(an);
            return true;
        }
        return false;
    }

    private Animal make(AnimalKind k, Herd herd, Temperament t, double x, double z, Place place) {
        double g = place.ground(x, z);
        Animal a = new Animal(k, herd, t, x, g, z, random);
        if (herd != null) {
            if (!herd.colours().isEmpty()) a.colour = herd.colours().get(random.nextInt(herd.colours().size()));
            a.size = herd.sizeMin() + random.nextDouble() * (herd.sizeMax() - herd.sizeMin());
        }
        if (k.flies()) a.y = Critters.surface(x, z, place) + a.lift;
        settle(a, place);
        return a;
    }

    /** Whether a {@code k} would live at {@code (x, z)}: its water near, the ground under it fit for it. */
    static boolean livable(AnimalKind k, double x, double z, Place place) {
        if (!passable(k, x, z, place) || !dry(k, x, z, place)) return false;
        return k.nearWater() <= 0 || Critters.nearWater(x, z, k.nearWater(), place);
    }

    /** Whether a {@code k} could stand (or swim, or fly) at {@code (x, z)}. */
    static boolean passable(AnimalKind k, double x, double z, Place place) {
        if (!inside(x, z, place)) return false;
        if (k.flies()) return true;
        double deep = place.water(x, z, place.ground(x, z));
        if (!k.swims() && !k.paddles() && deep > k.wade()) return false;
        return !place.blocked(x, z, k.radius() * 0.6);
    }

    /** Whether {@code (x, z)} is ground a {@code k} that swims only when it must stands on: no deeper than it wades. */
    static boolean dry(AnimalKind k, double x, double z, Place place) {
        return !k.paddles() || place.water(x, z, place.ground(x, z)) <= k.wade();
    }

    // --- what they do -----------------------------------------------------------------

    /** What a blow did to it, since it last looked: it runs off beaten, or it runs, or it fights back. */
    private void react(Animal a, Quarry her, Place place, Critters.Voice voice, Vec3 focus) {
        if (a.beaten) {
            a.beaten = false;
            a.provoked = false;
            a.hurtFrom = false;
            say(voice, a, a.kind.hurt(), focus, 0.9);
            if (a.mode == Animal.Mode.DOWN) {
                // hunted down: it falls where it is (out of the air, a bird), and leaves what it gives
                a.vx = a.vz = 0;
                a.set("fall");
                events.add(new Event(a, Outcome.DOWN, 0));
                return;
            }
            events.add(new Event(a, Outcome.RAN_OFF, 0));
            if (a.kind.swims() && !a.swimming || a.kind.flies()) takeOff(a, voice, focus);
            return;
        }
        if (!a.hurtFrom) return;
        a.hurtFrom = false;
        say(voice, a, a.kind.hurt(), focus, 0.85);
        if (!a.provoked) return;
        a.provoked = false;
        boolean busy = a.mode == Animal.Mode.STAGGER || a.mode == Animal.Mode.CHASE || a.mode == Animal.Mode.STRIKE;
        if (!a.temperament.fightsBack()) {
            // worn down (no blow to rock it): it runs all the same, but the tame
            if (!busy && a.mode != Animal.Mode.FLEE && a.temperament != Temperament.TAME) {
                flee(a, a.fleeX, a.fleeZ, 5 + random.nextDouble() * 2, voice, focus);
            }
            return;
        }
        a.angry = CALM_DOWN;
        if (!busy && her != null) a.enter(Animal.Mode.CHASE);
        if (a.kind == AnimalKind.BEE) {
            // the swarm comes with it
            for (Animal b : live) {
                if (b != a && b.kind == AnimalKind.BEE && b.alive() && b.temperament.fightsBack()
                        && Math.hypot(b.x - a.x, b.z - a.z) < SWARM) {
                    b.angry = CALM_DOWN;
                    if (her != null) b.enter(Animal.Mode.CHASE);
                }
            }
        }
    }

    private void behave(Animal a, double dt, double time, Quarry her, Place place, Critters.Voice voice,
                        Vec3 focus) {
        AnimalKind k = a.kind;
        double d = her == null ? Double.MAX_VALUE : Math.hypot(her.feet().x() - a.x, her.feet().z() - a.z);
        switch (a.mode) {
            case DOWN -> {
                a.vx = a.vz = 0;
                if (a.airborne) {
                    // out of the air, onto the ground (or the water)
                    a.vy -= 9.8 * dt;
                    a.y += a.vy * dt;
                    double g = place.ground(a.x, a.z), floor = g + place.water(a.x, a.z, g) * 0.6;
                    if (a.y <= floor) {
                        a.airborne = false;
                        a.vy = 0;
                        a.y = floor;
                    }
                }
            }
            case LIE -> {
                a.vx = a.vz = 0;
                settle(a, place);
                // up when she comes near (the calm let her come nearer), or when it has rested
                double wake = (a.temperament == Temperament.SKITTISH ? Math.max(3, k.shy() * 1.2)
                        : a.temperament == Temperament.TAME ? 1.2 : 2.2) * (her == null ? 1 : her.conspicuous());
                a.timer -= dt;
                if (a.timer <= 0 || d < wake || a.swimming) {
                    rest(a);
                    notice(a, her, d, voice, focus);
                }
            }
            case STAGGER -> {
                double s = Math.max(0, 1 - a.modeTime / 0.3);
                a.x += a.vx * s * dt;
                a.z += a.vz * s * dt;
                settle(a, place);
                if (a.modeTime >= 0.3) {
                    a.vx = a.vz = 0;
                    if (a.angry > 0 && her != null) {
                        a.enter(Animal.Mode.CHASE);
                        say(voice, a, k.angry(), focus, 0.9);
                    } else if (a.temperament == Temperament.TAME) {
                        a.enter(Animal.Mode.REST);
                    } else {
                        flee(a, a.fleeX, a.fleeZ, 5 + random.nextDouble() * 2, voice, focus);
                    }
                }
            }
            case RUN_OFF -> {
                if (a.airborne || k.flies()) {
                    flyAway(a, dt, place);
                } else {
                    run(a, a.x - a.fleeX, a.z - a.fleeZ, k.run(), dt, place);
                }
            }
            case FLY_OFF -> flyAway(a, dt, place);
            case FLEE -> {
                a.timer -= dt;
                if (her != null) {
                    a.fleeX = her.feet().x();
                    a.fleeZ = her.feet().z();
                }
                double far = Math.max(8, k.shy() * 2.2);
                run(a, a.x - a.fleeX, a.z - a.fleeZ, k.run(), dt, place);
                if (a.timer <= 0 && (her == null || d > far)) rest(a);
            }
            case FOLLOW -> {
                a.timer -= dt;
                if (her == null || a.timer <= 0 || d > 16) {
                    a.ignore = 25 + random.nextDouble() * 20;
                    wander(a, place);
                } else if (d > 2.2) {
                    double speed = d > 6 ? k.run() * 0.75 : k.walk();
                    go(a, her.feet().x(), her.feet().z(), speed, dt, place);
                } else {
                    a.vx = a.vz = 0;
                    turnTo(a, her.feet().x() - a.x, her.feet().z() - a.z, dt);
                    if (random.nextDouble() < dt * 0.12) say(voice, a, k.call(), focus, 0.7);
                }
            }
            case WARN -> {
                a.vx = a.vz = 0;
                if (her == null || d > k.warn() * 1.4) {
                    rest(a);
                    break;
                }
                turnTo(a, her.feet().x() - a.x, her.feet().z() - a.z, dt);
                a.timer -= dt;
                if (a.timer <= 0) {
                    a.timer = 1.6;
                    say(voice, a, k.angry(), focus, 0.8);
                }
                if (d < k.warn() * 0.55 || a.modeTime > 3.2) {
                    a.angry = CALM_DOWN;
                    a.enter(Animal.Mode.CHASE);
                }
            }
            case CHASE -> {
                a.angry -= dt;
                if (her == null || a.angry <= 0 || d > GIVE_UP) {
                    a.angry = 0;
                    rest(a);
                    break;
                }
                if (k.flies()) {
                    Vec3 chest = her.feet().add(0, 1.0, 0);
                    dart(a, chest, k.run(), dt, time);
                    if (a.position().sub(chest).length() <= k.reach() && a.cooldown <= 0) strikeStart(a);
                } else {
                    if (d - k.radius() > k.reach() * 0.75) {
                        double speed = k.run() * (k.gait() == AnimalKind.Gait.BIRD ? 1 : 0.9);
                        go(a, her.feet().x(), her.feet().z(), speed, dt, place);
                    } else {
                        a.vx = a.vz = 0;
                        turnTo(a, her.feet().x() - a.x, her.feet().z() - a.z, dt);
                    }
                    if (d - k.radius() <= k.reach() && a.cooldown <= 0) strikeStart(a);
                }
            }
            case STRIKE -> {
                a.vx = a.vz = 0;
                if (her == null) {
                    rest(a);
                    break;
                }
                if (k.flies()) {
                    dart(a, her.feet().add(0, 1.0, 0), k.run() * 0.4, dt, time);
                } else {
                    turnTo(a, her.feet().x() - a.x, her.feet().z() - a.z, dt);
                }
                if (a.modeTime >= WIND_UP) {
                    double reach = k.flies() ? a.position().sub(her.feet().add(0, 1.0, 0)).length()
                            : d - k.radius();
                    if (reach <= k.reach() * 1.35 + 0.15) strike(a, her, voice, focus);
                    a.cooldown = BETWEEN * (0.85 + random.nextDouble() * 0.3);
                    a.enter(Animal.Mode.CHASE);
                }
            }
            case WANDER -> {
                a.timer -= dt;
                if (notice(a, her, d, voice, focus)) break;
                double speed = a.swimming ? k.walk() * 1.1 : k.walk();
                if (k.flies()) {
                    drift(a, dt, time, place);
                    if (a.timer <= 0) rest(a);
                } else if (go(a, a.toX, a.toZ, speed, dt, place) || a.timer <= 0) {
                    rest(a);
                }
            }
            case REST, GRAZE -> {
                if (k.flies()) drift(a, dt, time, place);
                else {
                    a.vx = a.vz = 0;
                    settle(a, place);
                    if (a.swimming) {
                        // drifting a little on the water
                        a.x += Math.sin(time * 0.3 + a.phase) * 0.04 * dt;
                        a.z += Math.cos(time * 0.27 + a.phase) * 0.04 * dt;
                    }
                }
                if (notice(a, her, d, voice, focus)) break;
                a.timer -= dt;
                if (a.timer > 0) break;
                double r = random.nextDouble();
                boolean eats = k.gait() != AnimalKind.Gait.FLY && k.gait() != AnimalKind.Gait.CRAWL;
                if (k.liesDown() && !a.swimming && !a.saddled && r < 0.14) {
                    // down to rest a while
                    a.enter(Animal.Mode.LIE);
                    a.timer = 10 + random.nextDouble() * 18;
                } else if (r < 0.4 && eats) {
                    a.enter(Animal.Mode.GRAZE);
                    a.timer = 3 + random.nextDouble() * 6;
                } else if (r < 0.8) {
                    wander(a, place);
                } else {
                    a.enter(Animal.Mode.REST);
                    a.timer = 2 + random.nextDouble() * 5;
                }
                if (random.nextDouble() < 0.3) say(voice, a, k.call(), focus, 0.6);
            }
        }
    }

    /** Whether she, near, makes it do something else: run, come up, warn her off, go for her. */
    private boolean notice(Animal a, Quarry her, double d, Critters.Voice voice, Vec3 focus) {
        if (her == null || a.saddled) return false;             // a saddled horse waits for her
        AnimalKind k = a.kind;
        double c = her.conspicuous();
        switch (a.temperament) {
            case SKITTISH -> {
                if (k.shy() > 0 && d < k.shy() * c) {
                    flee(a, her.feet().x(), her.feet().z(), 3 + random.nextDouble() * 2, voice, focus);
                    return true;
                }
            }
            case FRIENDLY -> {
                if (a.ignore <= 0 && d < FRIENDLY) {
                    a.enter(Animal.Mode.FOLLOW);
                    a.timer = 12 + random.nextDouble() * 10;
                    say(voice, a, k.call(), focus, 0.75);
                    return true;
                }
            }
            case DEFENSIVE -> {
                if (k.warn() > 0 && d < k.warn() * c) {
                    a.enter(Animal.Mode.WARN);
                    a.timer = 1.6;
                    say(voice, a, k.angry(), focus, 0.85);
                    events.add(new Event(a, Outcome.WARNED, 0));
                    return true;
                }
            }
            case HOSTILE -> {
                if (d < SIGHT * c) {
                    a.angry = CALM_DOWN;
                    a.enter(Animal.Mode.CHASE);
                    say(voice, a, k.angry() != null ? k.angry() : k.call(), focus, 0.9);
                    return true;
                }
            }
            default -> { }
        }
        return false;
    }

    private void rest(Animal a) {
        a.enter(Animal.Mode.REST);
        a.timer = 1 + random.nextDouble() * 3;
        a.vx = a.vz = 0;
    }

    /** Off somewhere near home, at a walk: on the water, mostly, for a bird that swims. */
    private void wander(Animal a, Place place) {
        AnimalKind k = a.kind;
        a.enter(Animal.Mode.WANDER);
        a.timer = 6 + random.nextDouble() * 8;
        double wet = switch (k) {
            case SWAN -> 0.85;
            case DUCK -> 0.6;
            case GOOSE -> 0.4;
            case FLAMINGO -> 0.6;
            default -> 0;
        };
        boolean toWater = random.nextDouble() < wet;
        double bx = a.x, bz = a.z;
        for (int t = 0; t < 8; t++) {
            double ang = random.nextDouble() * Math.PI * 2, r = a.roam * Math.sqrt(random.nextDouble());
            double x = a.homeX + Math.cos(ang) * r, z = a.homeZ + Math.sin(ang) * r;
            if (!passable(k, x, z, place) || !dry(k, x, z, place)) continue;
            bx = x;
            bz = z;
            if (!toWater || place.water(x, z, place.ground(x, z)) > 0.15) break;
        }
        a.toX = bx;
        a.toZ = bz;
    }

    private void flee(Animal a, double fromX, double fromZ, double seconds, Critters.Voice voice, Vec3 focus) {
        a.fleeX = fromX;
        a.fleeZ = fromZ;
        a.timer = seconds;
        a.angry = 0;
        // a duck on land takes off, and a perching or a walking bird; one on the water, and every other,
        // runs (swims) for it
        AnimalKind.Gait g = a.kind.gait();
        boolean flits = a.kind == AnimalKind.DUCK || (g == AnimalKind.Gait.PERCH || g == AnimalKind.Gait.FOWL)
                && a.kind.takesOff();
        if (flits && !a.swimming) {
            takeOff(a, voice, focus);
            return;
        }
        a.enter(Animal.Mode.FLEE);
    }

    private void takeOff(Animal a, Critters.Voice voice, Vec3 focus) {
        if (a.mode != Animal.Mode.RUN_OFF) a.enter(Animal.Mode.FLY_OFF);
        a.airborne = true;
        a.swimming = false;
        say(voice, a, a.kind.flap(), focus, 0.8);
    }

    private void strikeStart(Animal a) {
        a.enter(Animal.Mode.STRIKE);
        a.set("attack");
    }

    /** Its blow lands - or does not: her roll, her parry, her block. */
    private void strike(Animal a, Quarry her, Critters.Voice voice, Vec3 focus) {
        AnimalKind k = a.kind;
        if (her.dodging()) {
            events.add(new Event(a, Outcome.DODGED, 0));
            return;
        }
        if (her.parrying()) {
            Vec3 away = new Vec3(a.x - her.feet().x(), 0, a.z - her.feet().z()).normalize(new Vec3(0, 0, 1));
            a.vx = away.x() * 3.5;
            a.vz = away.z() * 3.5;
            a.enter(Animal.Mode.STAGGER);
            events.add(new Event(a, Outcome.PARRIED, 0));
            return;
        }
        if (her.blocking()) {
            double took = her.hurt(Math.round(k.damage() / 2));
            events.add(new Event(a, Outcome.BLOCKED, took));
            return;
        }
        double took = her.hurt(k.damage());
        if (k == AnimalKind.BEE) say(voice, a, "sting", focus, 0.9);
        events.add(new Event(a, Outcome.STRUCK, took));
    }

    // --- going about ------------------------------------------------------------------

    /** Toward {@code (tx, tz)} at {@code speed}; true when there (or stuck). */
    private boolean go(Animal a, double tx, double tz, double speed, double dt, Place place) {
        double dx = tx - a.x, dz = tz - a.z, dist = Math.hypot(dx, dz);
        if (dist < 0.3) {
            a.vx = a.vz = 0;
            return true;
        }
        double step = Math.min(dist, speed * dt);
        if (!move(a, dx / dist, dz / dist, step, place)) {
            a.vx = a.vz = 0;
            return true;
        }
        a.vx = dx / dist * speed;
        a.vz = dz / dist * speed;
        turnTo(a, dx, dz, dt);
        return false;
    }

    /** Along {@code (dx, dz)} at {@code speed}, round what is in the way. */
    private void run(Animal a, double dx, double dz, double speed, double dt, Place place) {
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) {
            double h = a.heading;
            Vec3 f = SpriteView.headingVector(h);
            dx = f.x();
            dz = f.z();
            len = 1;
        }
        dx /= len;
        dz /= len;
        double step = speed * dt;
        // straight on, or turned a little, or a lot, to get round what is in the way
        for (double turn : new double[]{0, 0.5, -0.5, 1.0, -1.0, 1.6, -1.6}) {
            double c = Math.cos(turn), s = Math.sin(turn);
            double ux = dx * c - dz * s, uz = dx * s + dz * c;
            if (move(a, ux, uz, step, place)) {
                a.vx = ux * speed;
                a.vz = uz * speed;
                turnTo(a, ux, uz, dt);
                return;
            }
        }
        a.vx = a.vz = 0;
    }

    /** One step along a unit {@code (ux, uz)}, if the ground there will have it. */
    private boolean move(Animal a, double ux, double uz, double step, Place place) {
        AnimalKind k = a.kind;
        if (a.swimming && k.paddles()) step *= SWIM_PACE;
        double nx = a.x + ux * step, nz = a.z + uz * step;
        if (!inside(nx, nz, place) && inside(a.x, a.z, place)) return false;      // (never out over the level's edge)
        if (!k.flies()) {
            if (!footing(a, ux, uz, place)) return false;
            if (!passable(k, nx, nz, place) && passable(k, a.x, a.z, place)) return false;
        }
        a.x = nx;
        a.z = nz;
        settle(a, place);
        return true;
    }

    /** How far ahead the ground's slope is judged, metres: about a stride. */
    static final double FOOTING = 0.5;

    /**
     * Whether the ground ahead along a unit {@code (ux, uz)} is ground it
     * could walk: up no steeper than she could ({@link Terrain#STEEP}, 45°),
     * down no steeper than she could stand on ({@link Player#SLIDE}) - never
     * up a rock face or off a ledge. Judged over a stride, not the frame's
     * step, so a slope is the same slope however slowly it goes. One stuck on
     * a face (put there, or the ground sculpted under it) may go down or
     * along it, never further up.
     */
    static boolean footing(Animal a, double ux, double uz, Place place) {
        double here = place.ground(a.x, a.z);
        double ahead = place.ground(a.x + ux * FOOTING, a.z + uz * FOOTING);
        double rise = (ahead - here) / FOOTING;
        if (rise > Terrain.STEEP) return false;
        if (rise < -Player.SLIDE) {
            // off a ledge: only if where it stands is a face already (it is on its way down)
            double d = 0.15;
            double gx = (place.ground(a.x + d, a.z) - place.ground(a.x - d, a.z)) / (2 * d);
            double gz = (place.ground(a.x, a.z + d) - place.ground(a.x, a.z - d)) / (2 * d);
            return Math.hypot(gx, gz) > Player.SLIDE;
        }
        return true;
    }

    /** How much slower one that paddles goes swimming than on its feet. */
    static final double SWIM_PACE = 0.55;

    /**
     * Its feet onto the ground - or, swimming, its waterline onto the water:
     * a water bird as soon as it floats, one that paddles once the water
     * comes up over its back (wading till then).
     */
    private static void settle(Animal a, Place place) {
        if (a.kind.flies() || a.airborne) return;
        double g = place.ground(a.x, a.z);
        double deep = place.water(a.x, a.z, g);
        double sink = sink(a.kind) * a.size;
        a.swimming = a.kind.swims() ? deep > 0.15 : a.kind.paddles() && sink > 0 && deep > sink;
        a.y = a.swimming ? g + deep - sink : g;
    }

    /** How far under the water a swimmer's feet are: a bird's body floats a third under; one that paddles, its waterline. */
    public static double sink(AnimalKind k) {
        return switch (k) {
            case DUCK -> 0.16;
            case GOOSE -> 0.26;
            case SWAN -> 0.31;
            default -> k.waterline();
        };
    }

    /** Up and away, faster and faster, from where it was put up. */
    private void flyAway(Animal a, double dt, Place place) {
        a.airborne = true;
        double dx = a.x - a.fleeX, dz = a.z - a.fleeZ, len = Math.max(1e-6, Math.hypot(dx, dz));
        if (len < 1e-3) {
            Vec3 f = SpriteView.headingVector(a.heading);
            dx = f.x();
            dz = f.z();
            len = 1;
        }
        double speed = Math.min(7, 2 + a.modeTime * 3);
        a.vx = dx / len * speed;
        a.vz = dz / len * speed;
        a.vy = Math.max(0, 2.2 - a.modeTime * 0.4);
        a.x += a.vx * dt;
        a.z += a.vz * dt;
        a.y = Math.max(a.y + a.vy * dt, Critters.surface(a.x, a.z, place) + 0.2);
        turnTo(a, dx, dz, dt * 2);
    }

    /** A butterfly's flitting, a bee's zipping, from flower to flower round home. */
    private void drift(Animal a, double dt, double time, Place place) {
        boolean bee = a.kind == AnimalKind.BEE;
        double dx = a.toX - a.x, dz = a.toZ - a.z;
        if (Math.hypot(dx, dz) < 0.25 || a.timer <= 0 && a.mode == Animal.Mode.WANDER) {
            double ang = random.nextDouble() * Math.PI * 2, r = Math.min(a.roam, 5) * Math.sqrt(random.nextDouble());
            a.toX = a.homeX + Math.cos(ang) * r;
            a.toZ = a.homeZ + Math.sin(ang) * r;
            a.lift = bee ? 0.3 + random.nextDouble() * 0.9 : 0.4 + random.nextDouble() * 1.3;
            a.timer = 3 + random.nextDouble() * 4;
            dx = a.toX - a.x;
            dz = a.toZ - a.z;
        }
        double dist = Math.max(1e-6, Math.hypot(dx, dz));
        double speed = a.kind.walk();
        double k = Math.min(1, dt * (bee ? 3 : 1.5));
        a.vx += (dx / dist * speed - a.vx) * k;
        a.vz += (dz / dist * speed - a.vz) * k;
        double wob = bee ? 0.25 : 0.5;
        a.x += (a.vx + wob * Math.sin(time * 3.1 + a.phase)) * dt;
        a.z += (a.vz + wob * Math.cos(time * 2.7 + a.phase * 1.3)) * dt;
        double goal = Critters.surface(a.x, a.z, place) + a.lift
                + (bee ? 0.05 : 0.18) * Math.sin(time * (bee ? 7 : 4) + a.phase);
        a.y += (goal - a.y) * Math.min(1, dt * 2);
        a.vy = 0;
        turnTo(a, a.vx, a.vz, dt * 2);
    }

    /** A bee straight at {@code at}, weaving. */
    private void dart(Animal a, Vec3 at, double speed, double dt, double time) {
        Vec3 d = at.sub(a.position());
        double len = d.length();
        if (len < 0.05) return;
        double s = Math.min(len, speed * dt);
        a.x += d.x() / len * s + 0.4 * Math.sin(time * 9 + a.phase) * dt;
        a.y += d.y() / len * s;
        a.z += d.z() / len * s + 0.4 * Math.cos(time * 8 + a.phase) * dt;
        a.vx = d.x() / len * speed;
        a.vy = d.y() / len * speed;
        a.vz = d.z() / len * speed;
        turnTo(a, d.x(), d.z(), dt * 2);
    }

    /** Turn toward {@code (dx, dz)}, quickly but not at once. */
    private static void turnTo(Animal a, double dx, double dz, double dt) {
        if (Math.hypot(dx, dz) < 1e-6) return;
        double want = SpriteView.headingOf(dx, dz);
        double diff = Math.atan2(Math.sin(want - a.heading), Math.cos(want - a.heading));
        double most = dt * 7;
        a.heading += Math.max(-most, Math.min(most, diff));
    }

    /** Its sheet, and how fast it plays, for what it is doing. */
    static void dress(Animal a) {
        AnimalKind k = a.kind;
        if (a.mode == Animal.Mode.DOWN) {
            a.set("fall");
            a.rate = 1;
            return;
        }
        double speed = Math.hypot(a.vx, a.vz);
        boolean moving = speed > 0.05;
        switch (k.gait()) {
            case FLY -> {
                a.set("fly");
                a.rate = a.hostile() ? 1.4 : 1;
            }
            case CRAWL -> {
                a.set(a.swimming ? "swim" : moving ? "walk" : "idle");
                a.rate = moving ? clamp(speed / k.walkPace(), 0.6, 3) : 1;
            }
            case PERCH, FOWL -> {
                boolean hostile = a.mode == Animal.Mode.WARN || a.mode == Animal.Mode.STRIKE
                        || a.mode == Animal.Mode.CHASE && !moving;
                if (a.airborne) {
                    a.set("fly");
                    a.rate = 1;
                } else if (hostile) {
                    // the peacock fans its train at her; an eagle mantles, its wings up
                    a.set(k == AnimalKind.PEACOCK ? "attack" : "fly");
                    a.rate = 1;
                } else if (moving) {
                    a.set(k.gait() == AnimalKind.Gait.PERCH ? "hop" : "walk");
                    a.rate = clamp(speed / k.walkPace(), 0.6, 3);
                } else {
                    a.set(a.mode == Animal.Mode.GRAZE ? "eat" : "idle");
                    a.rate = 1;
                }
            }
            case HOP -> {
                if (moving) {
                    a.set("hop");
                    a.rate = clamp(speed / k.walkPace(), 0.6, 2.2);
                } else {
                    a.set(a.mode == Animal.Mode.GRAZE ? "eat" : "idle");
                    a.rate = 1;
                }
            }
            case BIRD -> {
                if (a.airborne) {
                    a.set("fly");
                    a.rate = 1;
                } else if (a.mode == Animal.Mode.WARN || a.mode == Animal.Mode.STRIKE
                        || a.mode == Animal.Mode.CHASE && !moving) {
                    a.set("attack");
                    a.rate = 1;
                } else if (a.swimming) {
                    a.set("swim");
                    a.rate = moving ? clamp(speed / 0.5, 0.8, 2) : 0.8;
                } else if (moving) {
                    a.set(a.mode == Animal.Mode.CHASE ? "attack" : "walk");
                    a.rate = a.mode == Animal.Mode.CHASE ? 1.2 : clamp(speed / k.walkPace(), 0.6, 3);
                } else {
                    a.set(a.mode == Animal.Mode.GRAZE ? "eat" : "idle");
                    a.rate = 1;
                }
            }
            case FOUR -> {
                if (a.swimming) {
                    a.set("swim");
                    a.rate = moving ? clamp(speed / (k.walkPace() * SWIM_PACE), 0.7, 2) : 0.7;
                } else if (a.mode == Animal.Mode.LIE) {
                    a.set("lie");
                    a.rate = 1;
                } else if (moving) {
                    boolean runs = speed > (k.walk() + k.run()) * 0.5 * 0.8;
                    a.set(runs ? "run" : "walk");
                    a.rate = clamp(speed / (runs ? k.runPace() : k.walkPace()), 0.6, 2.2);
                } else {
                    a.set(a.mode == Animal.Mode.GRAZE ? "eat" : "idle");
                    a.rate = 1;
                }
            }
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** A sound of {@code a}'s, as loud as it is near {@code focus}. */
    private static void say(Critters.Voice voice, Animal a, String sound, Vec3 focus, double loud) {
        if (voice == null || sound == null) return;
        double d = Math.hypot(a.x - focus.x(), a.z - focus.z());
        double v = loud * (1 - d / HEARD) * Math.max(0.3, a.alpha);
        if (v > 0.04) voice.sound(SoundKeys.creature(a.kind.key(), sound), a.position(), v);
    }
}
