package com.larsons.game.ambient;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.world.Biome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The little creatures living round the player: they come where their
 * {@link CritterKind} lives, when it is out, as many as the level's {@link
 * Life} asks, and go again as she leaves them behind - and the ones placed
 * in Creative Mode ({@link Spot}s), which keep to their spot.
 *
 * <p><b>Who lives here.</b> Every second it looks round the player - 24
 * points out to {@value #REACH} m - at the biome each is in and the water
 * near it, and works out how many of each kind belong round her: its {@link
 * CritterKind#cap} times the level's density, times how many are out at this
 * hour ({@link CritterKind#out}), times how much of what is round her is
 * home to it. Short of that it brings new ones in, at spots it would live
 * (a frog only at the water's edge, a cricket only on dry ground), out of
 * her way; over it, the farthest fade away. Beyond {@value #REACH} m and a
 * little they are gone.
 *
 * <p><b>What they do.</b> A firefly drifts about at head height, blinking.
 * A cricket sits in the grass, and sings at night; come too near and it
 * hops away. A frog sits at the edge, croaking now and then, more at night;
 * come too near and it hops for the water - and in it is gone with a plop.
 * A dragonfly hovers over the water and darts from spot to spot, and away
 * from her. Their sounds are heard where they are ({@link Voice}), the
 * nearer the louder.
 *
 * <p>Pure simulation: what is where comes in through {@link Place}, the
 * sounds go out through {@link Voice}; {@code world/WorldRenderer} draws
 * them.
 */
public final class Critters {

    /** How far round the player they live, metres. */
    public static final double REACH = 16;
    /** How far a placed spot's are kept while she is away, metres. */
    public static final double SPOT_REACH = 50;
    /** How near she can come before a cricket, a frog or a dragonfly makes off, metres. */
    public static final double SHY_CRICKET = 1.4, SHY_FROG = 2.0, SHY_DRAGONFLY = 1.6;
    /** How far its sounds carry, metres. */
    public static final double HEARD = 18;

    /** What the world is like where they are. */
    public interface Place {
        /** The ground's height. */
        double ground(double x, double z);

        /** How deep the water is over ground {@code groundY} (0: dry). */
        double water(double x, double z, double groundY);

        /** The biome. */
        Biome biome(double x, double z);
    }

    /** Where their sounds go: a key, where, how loud (0 – 1). */
    public interface Voice {
        void sound(String key, Vec3 at, double volume);
    }

    /** When a placed spot's are about. */
    public enum When {
        /** When its kind is out (fireflies at night, dragonflies by day). */
        ITS_TIME("its_time", "Its own time"),
        ALWAYS("always", "Always"),
        DAY("day", "By day"),
        NIGHT("night", "At night");

        private final String key, label;

        When(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() { return key; }

        public String label() { return label; }

        public static When byKey(String k) {
            if (k != null) {
                for (When w : values()) if (w.key.equals(k.trim().toLowerCase(Locale.ROOT))) return w;
            }
            return ITS_TIME;
        }

        double factor(CritterKind kind, double day) {
            return switch (this) {
                case ITS_TIME -> kind.out(day);
                case ALWAYS -> 1;
                case DAY -> CritterKind.smooth(0.35, 0.6, day);
                case NIGHT -> CritterKind.smooth(0.35, 0.6, 1 - day);
            };
        }
    }

    /** A swarm placed in a level: {@code count} of a kind living within {@code radius} m of {@code at}. */
    public record Spot(CritterKind kind, Vec3 at, int count, double radius, When when) {}

    private final List<Critter> live = new ArrayList<>();
    private final List<Spot> spots = new ArrayList<>();
    private final EnumMap<CritterKind, Double> share = new EnumMap<>(CritterKind.class);
    private Life life = new Life();
    private Random random = new Random(20261101);
    private double survey, thin;
    private boolean enabled = true;

    public Critters() {
        for (CritterKind k : CritterKind.values()) share.put(k, 0.0);
    }

    /** The ones about now. */
    public List<Critter> live() { return Collections.unmodifiableList(live); }

    /** The swarms placed in the level. */
    public List<Spot> spots() { return spots; }

    public Life life() { return life; }

    /** The level's say over how thick each kind lives. */
    public void setLife(Life l) {
        life = l == null ? new Life() : l;
    }

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
        spots.clear();
        survey = 0;
    }

    /** How many of {@code kind} are about. */
    public int count(CritterKind kind) {
        int n = 0;
        for (Critter c : live) if (c.kind == kind && !c.leaving) n++;
        return n;
    }

    /** How many of a kind belong round the player now (the last look round): for tests and the editor. */
    public double wanted(CritterKind kind, double day) {
        return kind.cap() * life.density(kind) * kind.out(day) * Math.min(1.5, share.get(kind));
    }

    /**
     * Advance them {@code dt} seconds: {@code time} the world's clock, {@code
     * day} how light it is (0 night, 1 day), {@code focus} where they live
     * round (the player, or the editor's view), {@code player} her feet - whom
     * they shy away from - or null.
     */
    public void tick(double dt, double time, double day, Vec3 focus, Vec3 player, Place place, Voice voice) {
        if (!enabled || focus == null) return;
        survey -= dt;
        if (survey <= 0) {
            survey = 1;
            look(focus, place);
        }
        populate(dt, day, focus, place);
        for (Critter c : live) {
            c.stateTime += dt;
            c.alpha = c.leaving ? Math.max(0, c.alpha - dt / 0.8) : Math.min(1, c.alpha + dt / 0.6);
            switch (c.kind) {
                case FIREFLY -> firefly(c, dt, time, place);
                case CRICKET -> cricket(c, dt, day, player, place, voice, focus);
                case FROG -> frog(c, dt, day, player, place, voice, focus);
                case DRAGONFLY -> dragonfly(c, dt, time, player, place, voice, focus);
            }
        }
        live.removeIf(c -> c.gone || (c.leaving && c.alpha <= 0));
    }

    // --- who lives here ---------------------------------------------------------------

    /** Look round {@code focus}: how much of it is home to each kind. */
    void look(Vec3 focus, Place place) {
        EnumMap<CritterKind, Double> sum = new EnumMap<>(CritterKind.class);
        for (CritterKind k : CritterKind.values()) sum.put(k, 0.0);
        int n = 0;
        for (double r : new double[]{3, 8, 13}) {
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8 + r;
                double x = focus.x() + Math.cos(a) * r, z = focus.z() + Math.sin(a) * r;
                Biome b = place.biome(x, z);
                n++;
                for (CritterKind k : CritterKind.values()) {
                    double w = k.weight(b);
                    if (w > 0 && k.nearWater() > 0 && !nearWater(x, z, k.nearWater(), place)) w = 0;
                    sum.put(k, sum.get(k) + w);
                }
            }
        }
        for (CritterKind k : CritterKind.values()) share.put(k, sum.get(k) / n);
    }

    /** Bring new ones in where they are wanted, let the extra and the left-behind go. */
    private void populate(double dt, double day, Vec3 focus, Place place) {
        // the free ones: round the player
        for (CritterKind k : CritterKind.values()) {
            double want = wanted(k, day);
            int have = 0;
            for (Critter c : live) if (c.kind == k && c.spot == null && !c.leaving) have++;
            if (have < Math.round(want)) {
                for (int t = 0; t < 2 && have < Math.round(want); t++) if (spawnFree(k, focus, place)) have++;
            }
            thin -= dt;
            if (have > Math.round(want) && thin <= 0) {
                thin = 0.35;
                Critter far = null;
                double fd = -1;
                for (Critter c : live) {
                    if (c.kind != k || c.spot != null || c.leaving) continue;
                    double d = Math.hypot(c.x - focus.x(), c.z - focus.z());
                    if (d > fd) {
                        fd = d;
                        far = c;
                    }
                }
                if (far != null) far.leaving = true;
            }
        }
        for (Critter c : live) {
            if (c.spot == null && Math.hypot(c.x - focus.x(), c.z - focus.z()) > REACH + 6) c.gone = true;
        }
        // the placed ones: round their spots, while she is anywhere near (a spot taken away: they go)
        for (Critter c : live) if (c.spot != null && !spots.contains(c.spot)) c.leaving = true;
        for (Spot s : spots) {
            boolean near = Math.hypot(s.at().x() - focus.x(), s.at().z() - focus.z()) < SPOT_REACH;
            int want = near ? (int) Math.round(s.count() * s.when().factor(s.kind(), day)) : 0;
            int have = 0;
            Critter extra = null;
            for (Critter c : live) {
                if (!s.equals(c.spot) || c.leaving) continue;
                have++;
                extra = c;
            }
            if (!near) {
                for (Critter c : live) if (s.equals(c.spot)) c.gone = true;
            } else if (have < want) {
                spawnAt(s, place);
            } else if (have > want && extra != null) {
                extra.leaving = true;
            }
        }
    }

    private boolean spawnFree(CritterKind k, Vec3 focus, Place place) {
        double inner = k.flies() ? 3 : 5;
        for (int t = 0; t < 8; t++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(inner * inner + random.nextDouble() * (REACH * REACH - inner * inner));
            double x = focus.x() + Math.cos(a) * r, z = focus.z() + Math.sin(a) * r;
            Biome b = place.biome(x, z);
            double w = k.weight(b);
            if (w <= 0 || random.nextDouble() * 1.5 > w) continue;
            if (!livable(k, x, z, place)) continue;
            Critter c = make(k, null, x, z, place);
            if (b == Biome.CRYSTAL && k == CritterKind.FIREFLY) c.rgb = random.nextBoolean() ? 0x9CE8FF : 0xD8A8FF;
            live.add(c);
            return true;
        }
        return false;
    }

    private void spawnAt(Spot s, Place place) {
        for (int t = 0; t < 6; t++) {
            double a = random.nextDouble() * Math.PI * 2, r = s.radius() * Math.sqrt(random.nextDouble());
            double x = s.at().x() + Math.cos(a) * r, z = s.at().z() + Math.sin(a) * r;
            // a frog or a cricket in deep water would only drown: somewhere else in the spot
            if (!s.kind().flies() && place.water(x, z, place.ground(x, z)) > Math.max(0.02, s.kind().wade())) continue;
            Critter c = make(s.kind(), s, x, z, place);
            c.homeX = s.at().x();
            c.homeZ = s.at().z();
            c.roam = s.radius();
            if (s.kind() == CritterKind.FIREFLY && place.biome(x, z) == Biome.CRYSTAL) {
                c.rgb = random.nextBoolean() ? 0x9CE8FF : 0xD8A8FF;
            }
            live.add(c);
            return;
        }
    }

    private Critter make(CritterKind k, Spot spot, double x, double z, Place place) {
        double g = place.ground(x, z);
        double y = k.flies() ? surface(x, z, place) + (k == CritterKind.FIREFLY ? 0.4 + random.nextDouble() * 1.4
                : 0.4 + random.nextDouble() * 0.9) : g;
        Critter c = new Critter(k, spot, x, y, z, random);
        c.roam = k == CritterKind.FIREFLY ? 3 : k == CritterKind.DRAGONFLY ? 3.5 : 2.5;
        if (k == CritterKind.FROG) c.timer = 2 + random.nextDouble() * 10;
        return c;
    }

    /** Whether a {@code k} could live at {@code (x, z)}: the water it wants near, the dry (or shallow) ground under it. */
    static boolean livable(CritterKind k, double x, double z, Place place) {
        double g = place.ground(x, z);
        double deep = place.water(x, z, g);
        if (!k.flies() && deep > Math.max(0.02, k.wade())) return false;
        return k.nearWater() <= 0 || nearWater(x, z, k.nearWater(), place);
    }

    /** Whether there is water within {@code reach} m of {@code (x, z)}. */
    static boolean nearWater(double x, double z, double reach, Place place) {
        if (place.water(x, z, place.ground(x, z)) > 0.02) return true;
        for (double r : new double[]{reach * 0.5, reach}) {
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6 + r;
                double px = x + Math.cos(a) * r, pz = z + Math.sin(a) * r;
                if (place.water(px, pz, place.ground(px, pz)) > 0.02) return true;
            }
        }
        return false;
    }

    /** The top of what is there: the water's surface over it, or the ground. */
    static double surface(double x, double z, Place place) {
        double g = place.ground(x, z);
        return g + place.water(x, z, g);
    }

    // --- what they do -----------------------------------------------------------------

    private void firefly(Critter c, double dt, double time, Place place) {
        c.timer -= dt;
        double dx = c.toX - c.x, dz = c.toZ - c.z;
        if (c.timer <= 0 || Math.hypot(dx, dz) < 0.2) {
            c.timer = 2 + random.nextDouble() * 3;
            double a = random.nextDouble() * Math.PI * 2, r = c.roam * Math.sqrt(random.nextDouble());
            c.toX = c.homeX + Math.cos(a) * r;
            c.toZ = c.homeZ + Math.sin(a) * r;
            c.lift = 0.35 + random.nextDouble() * 1.5;
        }
        dx = c.toX - c.x;
        dz = c.toZ - c.z;
        double d = Math.max(1e-6, Math.hypot(dx, dz)), speed = 0.35;
        double k = Math.min(1, dt * 1.2);
        c.vx += (dx / d * speed - c.vx) * k;
        c.vz += (dz / d * speed - c.vz) * k;
        c.x += (c.vx + 0.12 * Math.sin(time * 1.7 + c.phase)) * dt;
        c.z += (c.vz + 0.12 * Math.cos(time * 1.3 + c.phase * 1.3)) * dt;
        double goal = surface(c.x, c.z, place) + c.lift + 0.08 * Math.sin(time * 2.1 + c.phase);
        c.y += (goal - c.y) * Math.min(1, dt * 0.8);
    }

    private void cricket(Critter c, double dt, double day, Vec3 player, Place place, Voice voice, Vec3 focus) {
        if (c.state.equals("hop")) {
            hop(c, CritterKind.CRICKET_HOP, place);
            if (c.state.equals("hop")) return;
        }
        boolean night = day < 0.45;
        if (player != null && Math.hypot(player.x() - c.x, player.z() - c.z) < SHY_CRICKET) {
            startHop(c, away(c, player), 0.5 + random.nextDouble() * 0.35, 0.12, place, false);
            return;
        }
        c.timer -= dt;
        c.y = place.ground(c.x, c.z);
        if (c.timer > 0) return;
        if (c.state.equals("chirp")) {
            c.set("idle");
            c.timer = night ? 1.5 + random.nextDouble() * 4 : 4 + random.nextDouble() * 8;
        } else if (night && random.nextDouble() < 0.75) {
            c.set("chirp");
            c.timer = 1.5 + random.nextDouble() * 2.5;
            say(voice, c, SoundKeys.creature("cricket", "chirp"), focus, 0.55);
        } else if (random.nextDouble() < 0.4) {
            double a = random.nextDouble() * Math.PI * 2;
            startHop(c, a, 0.4 + random.nextDouble() * 0.3, 0.1, place, false);
        } else {
            c.timer = 2 + random.nextDouble() * 5;
        }
    }

    private void frog(Critter c, double dt, double day, Vec3 player, Place place, Voice voice, Vec3 focus) {
        if (c.state.equals("hop")) {
            hop(c, CritterKind.FROG_HOP, place);
            if (c.state.equals("hop")) return;
            // landed: in the water, it is gone with a plop
            if (place.water(c.x, c.z, place.ground(c.x, c.z)) > 0.08) {
                say(voice, c, SoundKeys.creature("frog", "plop"), focus, 0.8);
                c.gone = true;
                return;
            }
        }
        if (c.state.equals("croak") && c.stateTime >= CritterKind.FROG_CROAK) c.set("idle");
        if (player != null && Math.hypot(player.x() - c.x, player.z() - c.z) < SHY_FROG) {
            double toWater = towardWater(c, place);
            startHop(c, Double.isNaN(toWater) ? away(c, player) : toWater, 0.7 + random.nextDouble() * 0.3, 0.2,
                    place, true);
            return;
        }
        c.timer -= dt;
        c.y = place.ground(c.x, c.z);
        if (c.timer > 0 || !c.state.equals("idle")) return;
        boolean night = day < 0.45;
        if (random.nextDouble() < 0.7) {
            c.set("croak");
            c.timer = night ? 4 + random.nextDouble() * 9 : 9 + random.nextDouble() * 18;
            say(voice, c, SoundKeys.creature("frog", "croak"), focus, 0.75);
        } else {
            // a hop along the edge, keeping near its water
            double a = c.heading + (random.nextDouble() - 0.5) * 2.2;
            c.timer = 5 + random.nextDouble() * 10;
            startHop(c, a, 0.4 + random.nextDouble() * 0.3, 0.15, place, false);
        }
    }

    private void dragonfly(Critter c, double dt, double time, Vec3 player, Place place, Voice voice, Vec3 focus) {
        c.timer -= dt;
        boolean shy = player != null && Math.hypot(player.x() - c.x, player.z() - c.z) < SHY_DRAGONFLY
                && c.hovering;
        if (c.hovering) {
            c.y += (c.toY + 0.03 * Math.sin(time * 5 + c.phase) - c.y) * Math.min(1, dt * 4);
            if (c.timer > 0 && !shy) return;
            // dart to a new spot: away from her if she is near, else over the water round its home
            double bx = Double.NaN, bz = 0, by = 0;
            for (int t = 0; t < 6; t++) {
                double a = shy ? away(c, player) + (random.nextDouble() - 0.5) : random.nextDouble() * Math.PI * 2;
                double r = shy ? 2 + random.nextDouble() * 1.5 : 1 + random.nextDouble() * 2;
                double x = shy ? c.x + Math.cos(a) * r : c.homeX + Math.cos(a) * r * c.roam / 3;
                double z = shy ? c.z + Math.sin(a) * r : c.homeZ + Math.sin(a) * r * c.roam / 3;
                boolean wet = place.water(x, z, place.ground(x, z)) > 0.02;
                if (Double.isNaN(bx) || wet) {
                    bx = x;
                    bz = z;
                    by = surface(x, z, place) + 0.35 + random.nextDouble() * 0.9;
                    if (wet) break;
                }
            }
            c.fromX = c.x;
            c.fromY = c.y;
            c.fromZ = c.z;
            c.toX = bx;
            c.toY = by;
            c.toZ = bz;
            c.hopLength = Math.max(0.15, Math.hypot(bx - c.x, bz - c.z) / 3.5);
            c.hopTime = 0;
            c.hovering = false;
            c.heading = SpriteView.headingOf(bx - c.x, bz - c.z);
            if (player != null && Math.hypot(player.x() - c.x, player.z() - c.z) < 5 && random.nextDouble() < 0.5) {
                say(voice, c, SoundKeys.creature("dragonfly", "buzz"), focus, 0.45);
            }
            return;
        }
        c.hopTime += dt;
        double u = Math.min(1, c.hopTime / c.hopLength);
        double e = u * u * (3 - 2 * u);
        c.x = c.fromX + (c.toX - c.fromX) * e;
        c.z = c.fromZ + (c.toZ - c.fromZ) * e;
        c.y = c.fromY + (c.toY - c.fromY) * e;
        if (u >= 1) {
            c.hovering = true;
            c.timer = 0.5 + random.nextDouble() * 2.2;
        }
    }

    /** Set off on a hop {@code length} m along heading {@code a}, as high as {@code arc}; into water only if {@code wet}. */
    private void startHop(Critter c, double a, double length, double arc, Place place, boolean wet) {
        Vec3 d = SpriteView.headingVector(a);
        double x = c.x + d.x() * length, z = c.z + d.z() * length;
        // not off into the deep unless it means to, nor far from home
        if (!wet && place.water(x, z, place.ground(x, z)) > Math.max(0.02, c.kind.wade())) {
            x = c.x - d.x() * length;
            z = c.z - d.z() * length;
            a = a + Math.PI;
        }
        if (Math.hypot(x - c.homeX, z - c.homeZ) > c.roam * 1.6 && !wet) {
            a = SpriteView.headingOf(c.homeX - c.x, c.homeZ - c.z);
            d = SpriteView.headingVector(a);
            x = c.x + d.x() * length;
            z = c.z + d.z() * length;
        }
        c.heading = a;
        c.fromX = c.x;
        c.fromY = c.y;
        c.fromZ = c.z;
        c.toX = x;
        c.toZ = z;
        c.toY = place.ground(x, z);
        c.arc = arc;
        c.set("hop");
    }

    /** Along the hop that is under way; back to sitting when it has landed. */
    private static void hop(Critter c, double length, Place place) {
        double u = Math.min(1, c.stateTime / length);
        // the leap is its middle: a crouch before it, the landing after
        double v = Math.max(0, Math.min(1, (u - 0.15) / 0.65));
        c.x = c.fromX + (c.toX - c.fromX) * v;
        c.z = c.fromZ + (c.toZ - c.fromZ) * v;
        c.y = c.fromY + (c.toY - c.fromY) * v + c.arc * 4 * v * (1 - v);
        if (u >= 1) {
            c.y = place.ground(c.x, c.z);
            c.set("idle");
            c.timer = Math.max(c.timer, 1.5);
        }
    }

    /** The heading away from {@code from}. */
    private static double away(Critter c, Vec3 from) {
        return SpriteView.headingOf(c.x - from.x(), c.z - from.z());
    }

    /** The heading of the nearest water within 3 m, or NaN when there is none. */
    private static double towardWater(Critter c, Place place) {
        for (double r : new double[]{0.7, 1.5, 3}) {
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                Vec3 d = SpriteView.headingVector(a);
                double x = c.x + d.x() * r, z = c.z + d.z() * r;
                if (place.water(x, z, place.ground(x, z)) > 0.1) return a;
            }
        }
        return Double.NaN;
    }

    /** A sound of {@code c}'s, as loud as it is near {@code focus}. */
    private static void say(Voice voice, Critter c, String key, Vec3 focus, double loud) {
        if (voice == null) return;
        double d = Math.hypot(c.x - focus.x(), c.z - focus.z());
        double v = loud * (1 - d / HEARD) * c.alpha;
        if (v > 0.04) voice.sound(key, c.position(), v);
    }
}
