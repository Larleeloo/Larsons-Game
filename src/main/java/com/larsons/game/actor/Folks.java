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

        public Spec seed(long s) {
            return new Spec(body, weapon, size, s, name, skin, hostile, dress);
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
        if (spec == null) spec = Spec.ANY;
        // (summoned, risen: each a look of its own, by chance)
        if (spec.seed() == 0) spec = spec.seed(random.nextLong() | 1);
        return spawn(nextId++, breed, at, heading, spec);
    }

    /** One of {@code breed}, the level's object {@code id}, at {@code at}. */
    public Folk spawn(int id, Breed breed, Vec3 at, double heading, Spec spec) {
        if (spec == null) spec = Spec.ANY;
        // a level's own foe: the same look every time it is stood up (the editor redraws the level as the
        // pointer moves - it must not change face with every move), and in the game as it was in the editor
        long seed = spec.seed() != 0 ? spec.seed() : id * 7919L + breed.ordinal() * 104729L + 17;
        Random r = new Random(seed);
        boolean masc = "masculine".equals(spec.body()) || (!"feminine".equals(spec.body()) && r.nextBoolean());
        double size = spec.size() > 0 ? spec.size()
                : breed.smallest() + r.nextDouble() * (breed.tallest() - breed.smallest());
        Folk f;
        if (breed.risen()) {
            // the horde: a creature of its own, bare-handed, its skin the grave's - green, ashen or bruised
            List<String> sheets = breed.risenSprites(masc);
            f = new Folk(id, breed, breed.people(), breed.role(), masc, null, sheets.get(r.nextInt(sheets.size())),
                    at, heading);
            f.tinted(Folk.RISEN_SKINS[r.nextInt(Folk.RISEN_SKINS.length)]);
            // (the brute and the Bortzal are drawn their own size; the rest a little either way)
            f.sized(breed == Breed.RISEN_BRUTE || breed == Breed.RISEN_BORTZAL ? 1 + (size - 1) * 0.3 : size);
            f.crouched = breed.crawls();
        } else if (breed.creature()) {
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
        searches = SEARCHES;
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
            if (f.role() == Folk.Role.PERSON) f.crouched = false;
            f.act(Folk.Act.IDLE, 0);
            return;
        }
        if (f.role() == Folk.Role.PERSON && her != null) {
            if (f.visiting) {
                visit(f, dt, her, place);
                return;
            }
            if (f.schedule != null) {
                daily(f, dt, her, place);
                return;
            }
        }
        if (f.mode == Folk.Mode.WANDER) {
            // (home from a chase may be round a wall: the way found; a stroll is short and straight)
            boolean far = Math.hypot(f.toX - f.x, f.toZ - f.z) > 6;
            boolean there = far ? travel(f, f.toX, f.toZ, f.walk, dt, place) : go(f, f.toX, f.toZ, f.walk, dt, place);
            if (there || f.modeTime > (far ? 40 : 9)) {
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

    // --- a person's day -------------------------------------------------------------------

    /** The hour of the day (0 – 24) and whether a storm is on: what their days go by. */
    private double hour = 12;
    private boolean storm;

    /** It is {@code hour} o'clock (0 – 24), and a storm is on or not: each person goes about her day by it. */
    public void setDay(double hour, boolean storm) {
        this.hour = hour;
        this.storm = storm;
    }

    public double hour() { return hour; }

    /** Out of a storm: home, inside. */
    static final Schedule.Entry SHELTER = new Schedule.Entry(0, Schedule.Doing.INSIDE, Schedule.HOME);

    /** What {@code f}'s day has her doing now: its entry - or, in a storm, home out of it (when she has one). */
    private Schedule.Entry wanted(Folk f) {
        Schedule.Entry e = f.schedule.at(hour);
        if (storm && f.shelters && !e.doing().indoors() && f.places.containsKey(Schedule.HOME)) return SHELTER;
        return e;
    }

    /** Where entry {@code e} of her day is (x, z, facing there - NaN: as she comes - and the building, or -1), or null. */
    private static double[] where(Folk f, Schedule.Entry e) {
        if (e.where().equals(Schedule.POST)) return new double[]{f.homeX, f.homeZ, f.postHeading, -1};
        return f.places.get(e.where());
    }

    /**
     * A person about her day ({@link Schedule}): when the hour comes for
     * the next thing, she walks there - round what is in the way - and
     * does it: stands at her post, works at it, kneels to tend it, keeps
     * watch, strolls about, walks to and fro, chats with whoever is near,
     * or goes in at her door and is not seen until she comes out again.
     */
    private void daily(Folk f, double dt, Animals.Quarry her, Animals.Place place) {
        Schedule.Entry want = wanted(f);
        double[] p = where(f, want);
        if (p == null) {
            // a place she does not know (a spot taken away, no house near): her post - and not in
            want = new Schedule.Entry(want.minute(), want.doing().indoors() ? Schedule.Doing.STAND : want.doing(),
                    Schedule.POST);
            p = where(f, want);
        }
        if (!want.equals(f.doing)) begin(f, want, p, place);
        f.dayFor += dt;
        if (f.indoors) {
            f.alpha = 0;
            f.act(Folk.Act.IDLE, 0);
            return;
        }
        if (f.entering) {
            // in at the door: gone from sight
            f.alpha = Math.max(0, f.alpha - dt / 0.45);
            f.act(Folk.Act.IDLE, 0);
            if (f.alpha <= 0) {
                f.indoors = true;
                f.entering = false;
            }
            return;
        }
        if (f.alpha < 1) f.alpha = Math.min(1, f.alpha + dt / 0.6);
        Schedule.Doing d = want.doing();
        Vec3 h = her.feet();
        double hx = h.x() - f.x, hz = h.z() - f.z;
        boolean near = hx * hx + hz * hz < 9;
        if (!f.arrived) {
            double left = Math.hypot(p[0] - f.x, p[1] - f.z);
            boolean loose = d == Schedule.Doing.WANDER || d == Schedule.Doing.CHAT;
            boolean there = left < (loose ? 1.5 : 0.3) || travel(f, p[0], p[1], f.walk, dt, place);
            if (there && Math.hypot(p[0] - f.x, p[1] - f.z) > (loose ? 3 : 1.2)) there = false;   // (stuck short)
            if (!there && f.dayFor > 45 && h.horizontalDistance(f.position()) > 28) {
                // a way that will not get her there: once she is out of her sight, she is there
                f.x = p[0];
                f.z = p[1];
                there = true;
            }
            if (!there) return;
            f.arrived = true;
            f.path = null;
            f.timer = 1 + random.nextDouble() * 2;
            f.lookTo = Double.isNaN(p[2]) ? f.heading : p[2];
            f.outbound = false;
            f.enter(Folk.Mode.STAND);
            if (d.indoors()) {
                f.entering = true;
                if (!Double.isNaN(p[2])) f.heading = p[2];
            }
            f.act(Folk.Act.IDLE, 0);
            return;
        }
        switch (d) {
            case WORK -> {
                // at it, whoever comes by - unless she is right by her
                if (hx * hx + hz * hz < 2.5) {
                    turn(f, hx, hz, dt);
                    f.act(Folk.Act.IDLE, 0);
                    break;
                }
                face(f, f.lookTo, dt);
                if (f.act() == Folk.Act.ATTACK) {
                    if (f.stateTime() >= clock.seconds(f, f.state())) f.act(Folk.Act.IDLE, 0);
                } else {
                    f.act(Folk.Act.IDLE, 0);
                    f.timer -= dt;
                    if (f.timer <= 0) {
                        f.timer = 1.6 + random.nextDouble() * 2.6;
                        f.act(Folk.Act.ATTACK, 0);
                    }
                }
            }
            case TEND -> {
                f.crouched = true;
                f.act(Folk.Act.IDLE, 0);
                face(f, f.lookTo, dt);
            }
            case WATCH -> {
                f.act(Folk.Act.IDLE, 0);
                if (near) {
                    turn(f, hx, hz, dt);
                    break;
                }
                f.timer -= dt;
                if (f.timer <= 0) {
                    // a look one way, then the other, then back the way she keeps watch
                    f.timer = 2.5 + random.nextDouble() * 3.5;
                    double base = Double.isNaN(p[2]) ? f.heading : p[2];
                    f.lookTo = random.nextInt(3) == 0 ? base : base + (random.nextDouble() * 2 - 1) * 1.2;
                }
                face(f, f.lookTo, dt);
            }
            case WANDER -> stroll(f, p[0], p[1], Math.max(4, f.roam), near ? hx : Double.NaN, hz, dt, place);
            case PATROL -> {
                if (f.mode == Folk.Mode.WANDER) {
                    double tx = f.outbound ? p[0] : f.homeX, tz = f.outbound ? p[1] : f.homeZ;
                    if (travel(f, tx, tz, f.walk, dt, place) || f.modeTime > 60) {
                        f.enter(Folk.Mode.STAND);
                        f.timer = 2 + random.nextDouble() * 3;
                        f.act(Folk.Act.IDLE, 0);
                    }
                    break;
                }
                f.act(Folk.Act.IDLE, 0);
                if (near) turn(f, hx, hz, dt);
                f.timer -= dt;
                if (f.timer <= 0 && !near) {
                    f.outbound = !f.outbound;
                    f.path = null;
                    f.enter(Folk.Mode.WANDER);
                }
            }
            case CHAT -> {
                Folk other = nearestPerson(f, 6);
                if (near) turn(f, hx, hz, dt);
                else if (other != null) turn(f, other.x - f.x, other.z - f.z, dt);
                if (f.act() == Folk.Act.LAUGH) {
                    if (f.stateTime() >= clock.seconds(f, f.state())) f.act(Folk.Act.IDLE, 0);
                    break;
                }
                f.act(Folk.Act.IDLE, 0);
                f.timer -= dt;
                if (f.timer <= 0) {
                    f.timer = 5 + random.nextDouble() * 9;
                    if (other != null && random.nextBoolean()) f.act(Folk.Act.LAUGH, 0);
                }
            }
            default -> {
                // standing at it, as she was put there - turned to her as she comes up
                f.act(Folk.Act.IDLE, 0);
                if (near) turn(f, hx, hz, dt);
                else face(f, f.lookTo, dt);
            }
        }
    }

    /** The next thing in her day begins: to {@code p}, unless she is already in there. */
    private void begin(Folk f, Schedule.Entry e, double[] p, Animals.Place place) {
        Schedule.Entry was = f.doing;
        double[] from = was == null ? null : where(f, was);
        f.doing = e;
        f.dayFor = 0;
        f.crouched = false;
        f.path = null;
        f.stuckFor = 0;
        f.nearest = Double.MAX_VALUE;
        f.enter(Folk.Mode.STAND);
        f.act(Folk.Act.IDLE, 0);
        if (was == null) {
            // the level begun: she is where her day has her now, not on her way there
            f.x = p[0];
            f.z = p[1];
            f.y = place.ground(f.x, f.z);
            if (!Double.isNaN(p[2])) f.heading = p[2];
            f.lookTo = f.heading;
            f.arrived = true;
            f.indoors = e.doing().indoors();
            f.entering = false;
            f.alpha = f.indoors ? 0 : 1;
            f.crouched = e.doing() == Schedule.Doing.TEND;
            return;
        }
        if ((f.indoors || f.entering) && e.doing().indoors() && from != null && from[0] == p[0] && from[1] == p[1]) {
            f.arrived = true;                       // (in, and staying in)
            return;
        }
        if (f.indoors || f.entering) {
            // out of the door she went in by
            f.indoors = false;
            f.entering = false;
            f.alpha = Math.min(f.alpha, 0.05);
        }
        f.arrived = false;
    }

    /** About {@code (cx, cz)} within {@code r}: standing a while, a few steps, standing again (turned to her when near). */
    private void stroll(Folk f, double cx, double cz, double r, double hx, double hz, double dt, Animals.Place place) {
        if (f.mode == Folk.Mode.WANDER) {
            if (go(f, f.toX, f.toZ, f.walk * 0.85, dt, place) || f.modeTime > 9) {
                f.enter(Folk.Mode.STAND);
                f.timer = 2 + random.nextDouble() * 5;
                f.act(Folk.Act.IDLE, 0);
            }
            return;
        }
        f.act(Folk.Act.IDLE, 0);
        if (!Double.isNaN(hx)) {
            turn(f, hx, hz, dt);
            return;
        }
        f.timer -= dt;
        if (f.timer > 0) return;
        f.timer = 3 + random.nextDouble() * 5;
        double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
        double tx = cx + Math.cos(a) * d, tz = cz + Math.sin(a) * d;
        if (free(f, tx, tz, place) && Paths.clear(f.x, f.z, tx, tz, f.radius() * 0.8, place)) {
            f.toX = tx;
            f.toZ = tz;
            f.enter(Folk.Mode.WANDER);
        }
    }

    /**
     * Seen inside the building she is in, as she walks in ({@link #visitor}):
     * about the room, turned to her as she comes up - and, when her day
     * calls her out, out of its door ({@link Folk#visitDone}).
     */
    private void visit(Folk f, double dt, Animals.Quarry her, Animals.Place place) {
        f.alpha = f.entering ? Math.max(0, f.alpha - dt / 0.45) : Math.min(1, f.alpha + dt / 0.6);
        if (f.entering) {
            f.act(Folk.Act.IDLE, 0);
            if (f.alpha <= 0) f.visitDone = true;
            return;
        }
        Schedule.Entry want = f.schedule == null ? null : wanted(f);
        if (want != null && !want.doing().indoors() && where(f, want) != null) {
            // out: to the door, and through it
            f.dayFor += dt;
            if (travel(f, f.exitX, f.exitZ, f.walk, dt, place) || f.dayFor > 30) f.entering = true;
            return;
        }
        f.dayFor = 0;
        Vec3 h = her.feet();
        double hx = h.x() - f.x, hz = h.z() - f.z;
        boolean near = hx * hx + hz * hz < 9;
        stroll(f, f.visitX, f.visitZ, 1.6, near ? hx : Double.NaN, hz, dt, place);
    }

    /**
     * {@code f}, who is in the building she walks into, seen in it: standing
     * at {@code at}, the way out at {@code (exitX, exitZ)}. Added to this
     * (the inside's) folk; {@link #takeVisitors} gives her back.
     */
    public void visitor(Folk f, Vec3 at, double exitX, double exitZ) {
        f.visiting = true;
        f.visitDone = false;
        f.indoors = false;
        f.entering = false;
        f.alpha = 1;
        f.x = f.visitX = at.x();
        f.y = at.y();
        f.z = f.visitZ = at.z();
        f.exitX = exitX;
        f.exitZ = exitZ;
        f.heading = SpriteView.headingOf(exitX - at.x(), exitZ - at.z());
        f.path = null;
        f.dayFor = 0;
        f.crouched = false;
        f.enter(Folk.Mode.STAND);
        f.act(Folk.Act.IDLE, 0);
        f.timer = 2 + random.nextDouble() * 4;
        if (!all.contains(f)) all.add(f);
    }

    /**
     * Those seen inside ({@link #visitor}) taken out of this - all of them
     * ({@code all}: she is leaving), or those gone out of its door - each
     * put back in, at her door outdoors, to come out when her day says.
     */
    public List<Folk> takeVisitors(boolean every) {
        List<Folk> out = new ArrayList<>();
        for (Folk f : all) if (f.visiting && (every || f.visitDone)) out.add(f);
        all.removeAll(out);
        for (Folk f : out) {
            f.visiting = false;
            f.visitDone = false;
            f.entering = false;
            f.path = null;
            double[] p = f.doing == null ? null : where(f, f.doing);
            if (p != null) {
                f.x = p[0];
                f.z = p[1];
            }
            f.indoors = true;
            f.alpha = 0;
        }
        return out;
    }

    /** The other person standing nearest {@code f}, within {@code r}, or null. */
    private Folk nearestPerson(Folk f, double r) {
        Folk best = null;
        double bestD = r;
        for (Folk o : all) {
            if (o == f || o.role() != Folk.Role.PERSON || !o.standing()) continue;
            double d = Math.hypot(o.x - f.x, o.z - f.z);
            if (d < bestD) {
                best = o;
                bestD = d;
            }
        }
        return best;
    }

    /** Turn to face {@code heading}. */
    private static void face(Folk f, double heading, double dt) {
        if (Double.isNaN(heading)) return;
        Vec3 v = SpriteView.headingVector(heading);
        turn(f, v.x(), v.z(), dt);
    }

    /** How wide its eyes take in what is before it, either way of where it faces (radians), and how near it hears her. */
    static final double CONE = Math.toRadians(65), HEARS = 3.5;

    /**
     * Whether {@code f} sees her: near enough (its sight, less when she is
     * hidden, more once it is after her), before it (its eyes' cone - or so
     * near it hears her, or once it is after her), and nothing in the way
     * (no hill, no wall: {@link Paths#sees}). Looked at a few times a second.
     */
    private boolean seesHer(Folk f, Vec3 h, double dist, double dt, Animals.Place place) {
        if (f.relentless) return true;
        double range = f.alerted ? f.sight * 1.6 : f.sight * placeSight(f, place) * conspicuous;
        if (dist > range) return false;
        f.lookIn -= dt;
        if (f.lookIn > 0) return f.seesHer;
        f.lookIn = 0.2 + random.nextDouble() * 0.1;
        boolean before = true;
        if (!f.alerted && dist > HEARS) {
            Vec3 fwd = SpriteView.headingVector(f.heading);
            double dot = ((h.x() - f.x) * fwd.x() + (h.z() - f.z) * fwd.z()) / Math.max(1e-6, dist);
            before = dot >= Math.cos(CONE);
        }
        double eyes = f.y + (f.crouched ? 0.7 : 1.55) * f.size();
        f.seesHer = before && Paths.sees(f.x, eyes, f.z, h.x(), h.y() + 1.2, h.z(), place);
        return f.seesHer;
    }

    /** How far it sees where it stands (1: as its kind does). */
    private static double placeSight(Folk f, Animals.Place place) {
        return 1;
    }

    /** How plain she is to see this frame (her stealth): set from her each tick. */
    private double conspicuous = 1;

    private void fight(Folk f, double dt, Animals.Quarry her, Animals.Place place) {
        Vec3 h = her.feet();
        double dx = h.x() - f.x, dz = h.z() - f.z, dist = Math.hypot(dx, dz), dy = h.y() - f.y;
        conspicuous = her.conspicuous();
        boolean sees = seesHer(f, h, dist, dt, place);
        if (sees) {
            f.seenHer = true;
            f.seenX = h.x();
            f.seenZ = h.z();
        }
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
                    // out of its sight: to where it last saw her, looking about; then it gives up and goes home
                    f.lostFor += dt;
                    if (f.lostFor > 6 || !f.seenHer) {
                        f.alerted = false;
                        f.seenHer = false;
                        f.path = null;
                        f.toX = f.homeX;
                        f.toZ = f.homeZ;
                        f.enter(Folk.Mode.WANDER);
                        return;
                    }
                    if (!f.hasVia) {
                        if (travel(f, f.seenX, f.seenZ, f.run, dt, place)) {
                            f.heading += dt * 1.8;          // (there, and turning to look for her)
                            f.act(Folk.Act.IDLE, 0);
                        }
                        return;
                    }
                } else {
                    f.lostFor = 0;
                }
                if (f.hasVia) {
                    // by way of the gap it comes through: up to it, at what bars it, and on
                    double vx = f.viaX - f.x, vz = f.viaZ - f.z, vd = Math.hypot(vx, vz);
                    if (vd > 1.0) {
                        travel(f, f.viaX, f.viaZ, f.run, dt, place);
                        return;
                    }
                    if (f.barred) {
                        turn(f, vx, vz, dt * 2);
                        if (f.cooldown <= 0) strike(f, dist < f.reach + 0.6 ? dx : vx, dist < f.reach + 0.6 ? dz : vz);
                        else f.act(Folk.Act.IDLE, 0);
                        return;
                    }
                    f.hasVia = false;
                }
                if (f.shot() != null) {
                    if (dist > f.keep || !sees) {
                        travel(f, h.x(), h.z(), f.run, dt, place);
                    } else if (dist < 2.6 && dist > 1e-3 && f.cooldown > 0.2) {
                        go(f, f.x - dx / dist * 4, f.z - dz / dist * 4, f.walk * 1.4, dt, place);
                    } else {
                        turn(f, dx, dz, dt * 2);
                        f.act(Folk.Act.IDLE, 0);
                    }
                    if (f.cooldown <= 0 && sees && dist <= f.keep * 1.2 && dist > 1.0) strike(f, dx, dz);
                } else {
                    double near = f.reach + 0.3;
                    if (dist > near * 0.85) {
                        travel(f, h.x(), h.z(), f.run, dt, place);
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
            if (!free(f, nx, nz, t == 0 ? d : Paths.CELL, place)) continue;
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

    /** How many ways may be looked for in a frame, between them all (a horde does not stall the game). */
    static final int SEARCHES = 2;
    private int searches = SEARCHES;

    /**
     * Toward {@code (tx, tz)} at {@code speed} the way there is: straight
     * when the straight way is clear, else round what is in the way - a way
     * found ({@link Paths#find}) and walked point by point, looked for again
     * when she has moved on, when it is stuck, and every second or so. True
     * once there - or when there is no way nearer.
     */
    private boolean travel(Folk f, double tx, double tz, double speed, double dt, Animals.Place place) {
        double d = Math.hypot(tx - f.x, tz - f.z);
        if (d < 0.3) {
            f.path = null;
            return true;
        }
        double r = f.radius() * 0.8;
        f.replan -= dt;
        boolean moved = f.path != null && Math.hypot(tx - f.pathTx, tz - f.pathTz) > Math.max(1.5, d * 0.25);
        if ((f.replan <= 0 || moved || f.stuckFor > 1.0) && searches > 0) {
            f.replan = 0.9 + random.nextDouble() * 0.6;
            if (d < 40 && Paths.clear(f.x, f.z, tx, tz, r, place)) {
                f.path = null;
            } else {
                searches--;
                f.path = Paths.find(f.x, f.z, tx, tz, r, place);
                f.pathAt = 0;
                f.pathTx = tx;
                f.pathTz = tz;
                // no way there (walled in, the far side of a river): it looks again less often
                f.hopeless = f.path == null || Math.hypot(f.path[f.path.length - 2] - tx,
                        f.path[f.path.length - 1] - tz) > 0.5;
                if (f.hopeless) f.replan = 3 + random.nextDouble() * 2;
            }
            f.stuckFor = 0;
            f.nearest = Double.MAX_VALUE;
        }
        double gx = tx, gz = tz;
        if (f.path != null) {
            int n = f.path.length / 2;
            while (f.pathAt < n - 1 && Math.hypot(f.path[2 * f.pathAt] - f.x, f.path[2 * f.pathAt + 1] - f.z) < 0.3) {
                f.pathAt++;
                f.nearest = Double.MAX_VALUE;
            }
            gx = f.path[2 * f.pathAt];
            gz = f.path[2 * f.pathAt + 1];
            if (f.pathAt == n - 1 && Math.hypot(gx - f.x, gz - f.z) < 0.3) {
                // the end of the way found: there - or as near as there is a way (then it waits to look again)
                f.path = null;
                if (!f.hopeless) f.replan = 0;
                return Math.hypot(tx - f.x, tz - f.z) < 1.0 || f.stuckFor > 2;
            }
        }
        go(f, gx, gz, speed, dt, place);
        // stuck: no nearer to where it is going for a while (going round in a dither counts)
        double dg = Math.hypot(gx - f.x, gz - f.z);
        if (dg < f.nearest - 0.15) {
            f.nearest = dg;
            f.stuckFor = 0;
        } else {
            f.stuckFor += dt;
        }
        return f.stuckFor > 3;
    }

    /** Whether {@code f} can step to {@code (x, z)}: in bounds, clear, not deep water - and not up a cliff or a wall. */
    private static boolean free(Folk f, double x, double z, Animals.Place place) {
        return free(f, x, z, Paths.CELL, place);
    }

    /**
     * {@link #free(Folk, double, double, Animals.Place)}, {@code left} metres
     * short of where it is going: the slope judged over a cell's length the
     * way it goes, or what is left (a frame's step is too short to tell a
     * cliff by).
     */
    private static boolean free(Folk f, double x, double z, double left, Animals.Place place) {
        return Paths.steps(f.x, f.z, f.y, x, z, left, f.radius() * 0.8, place);
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
                double dx = b.x - a.x, dz = b.z - a.z, d = Math.hypot(dx, dz), r = (a.radius() + b.radius()) * 1.3;
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
