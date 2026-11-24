package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The trees of a world that have been struck with an axe ({@link Felling}):
 * those chopped at and still standing, those coming down, those lying on
 * the ground, and the stumps of those felled - each until it grows back.
 * A tree nobody has touched is not here at all.
 *
 * <p>{@link #chop} strikes one; {@link #tick} brings the felled down, breaks
 * them up into what they leave and grows them back, telling the world what
 * happened ({@link Event}) - the creak, the crash, the leaves and the loot -
 * for it to make the sounds, throw the pieces and lay the loot.
 */
public final class Fellings {

    /** Where a tree is in its felling. */
    public enum Stage {
        /** Struck, still standing: so many blows in it. */
        STANDING,
        /** The last blow struck: coming down. */
        FALLING,
        /** On the ground, whole: about to break up into what it leaves. */
        DOWN,
        /** Gone: its stump stands, until it grows back. */
        STUMP
    }

    /** One tree struck. */
    public static final class Tree {
        private final Scenery.Placed placed;
        private final Felling.Rule rule;
        private double struck;
        private double lastBlow = -1e9;
        private Stage stage = Stage.STANDING;
        private double felledAt;
        private double heading;
        private double land = Math.PI / 2;
        private double backAt = Double.MAX_VALUE;
        private boolean crashed;

        Tree(Scenery.Placed placed, Felling.Rule rule) {
            this.placed = placed;
            this.rule = rule;
        }

        Tree(Tree t) {
            this(t.placed, t.rule);
            struck = t.struck;
            lastBlow = t.lastBlow;
            stage = t.stage;
            felledAt = t.felledAt;
            heading = t.heading;
            land = t.land;
            backAt = t.backAt;
            crashed = t.crashed;
        }

        public Scenery.Placed placed() { return placed; }

        public Felling.Rule rule() { return rule; }

        public Stage stage() { return stage; }

        /** How many blows' worth are in it. */
        public double struck() { return struck; }

        /** Whether it still stands (struck or not): drawn whole. */
        public boolean standing() { return stage == Stage.STANDING; }

        /** The way it falls ({@link SpriteView#headingVector}). */
        public double heading() { return heading; }

        /** How far over it is at world time {@code now}, radians from upright (0 while it stands). */
        public double angle(double now) {
            if (stage == Stage.STANDING || stage == Stage.STUMP) return 0;
            return Felling.fallAngle(now - felledAt, Felling.fallSeconds(placed), land);
        }

        /** How far its crown shakes at world time {@code now} from the last blow, metres (0: still). */
        public double shake(double now) {
            double t = now - lastBlow;
            if (stage != Stage.STANDING || t < 0 || t > 0.5) return 0;
            return 0.06 * Math.sin(t * 38) * (1 - t / 0.5);
        }

        /** The world time of its last blow. */
        public double lastBlow() { return lastBlow; }

        /** The world time it fell (its last blow). */
        public double felledAt() { return felledAt; }
    }

    /** What happened to a tree, for the world to make it heard and seen. */
    public record Event(Kind kind, Tree tree, Vec3 at, List<Drops.Drop> drops) {
        public enum Kind {
            /** A blow struck home: chips fly. */
            CHOPPED,
            /** The last blow: it creaks and starts to go. */
            FELLED,
            /** It hit the ground. */
            CRASHED,
            /** It broke up: leaves, bark and what it leaves ({@link #drops}), along where it lay. */
            BROKE,
            /** It grew back. */
            REGREW
        }
    }

    /** A blow's outcome: how many blows' worth are in the tree now, of how many it takes, and whether it fell. */
    public record Blow(Tree tree, double struck, int chops, boolean felled) {}

    private final Map<Scenery.Placed, Tree> trees = new IdentityHashMap<>();
    private final List<Event> events = new ArrayList<>();

    /** How near she may be for a stump to grow back, metres: it grows back where she does not see it happen. */
    public static final double REGROW_AWAY = 22;

    /** The tree {@code p} as felling has left it, or null (never struck). */
    public Tree of(Scenery.Placed p) {
        return trees.get(p);
    }

    /** Whether {@code p} is standing - never struck, or struck and still up. */
    public boolean standing(Scenery.Placed p) {
        Tree t = trees.get(p);
        return t == null || t.standing();
    }

    /** Every tree struck. */
    public java.util.Collection<Tree> all() {
        return Collections.unmodifiableCollection(trees.values());
    }

    /**
     * Strike {@code p} with a blow of {@code power} ({@link Felling#power})
     * from {@code from} at world time {@code now}: null when it cannot be
     * felled or is down already. The blow that brings it to its {@link
     * Felling.Rule#chops} fells it, away from {@code from}.
     */
    public Blow chop(Scenery.Placed p, double power, Vec3 from, double now, Terrain terrain) {
        Felling.Rule rule = Felling.rule(p);
        if (rule == null) return null;
        Tree t = trees.computeIfAbsent(p, k -> new Tree(k, rule));
        if (!t.standing()) return null;
        t.struck += Math.max(0.05, power);
        t.lastBlow = now;
        boolean felled = t.struck >= t.rule.chops() - 1e-9;
        events.add(new Event(Event.Kind.CHOPPED, t, Felling.bite(p, from), List.of()));
        if (felled) {
            t.stage = Stage.FALLING;
            t.felledAt = now;
            t.heading = Felling.fallHeading(p, from);
            t.land = Felling.landAngle(p, t.heading, terrain);
            t.crashed = false;
            events.add(new Event(Event.Kind.FELLED, t, p.position(), List.of()));
        }
        return new Blow(t, t.struck, t.rule.chops(), felled);
    }

    /**
     * Bring the felled down and break them up, and grow back the stumps
     * whose time has come while {@code her} is not near; {@code random}
     * rolls what each leaves ({@code loot}: what "chest loot" draws from).
     */
    public void tick(double now, Vec3 her, Random random, Loot loot) {
        List<Scenery.Placed> back = new ArrayList<>();
        for (Tree t : trees.values()) {
            double since = now - t.felledAt, fall = Felling.fallSeconds(t.placed);
            switch (t.stage) {
                case FALLING -> {
                    if (since >= fall && !t.crashed) {
                        t.crashed = true;
                        events.add(new Event(Event.Kind.CRASHED, t, crown(t, 0.55), List.of()));
                    }
                    if (since >= fall + Felling.BOUNCE_SECONDS) t.stage = Stage.DOWN;
                }
                case DOWN -> {
                    if (since >= fall + Felling.BOUNCE_SECONDS + Felling.LIE_SECONDS) {
                        t.stage = Stage.STUMP;
                        t.backAt = t.rule.regrow() > 0 ? now + t.rule.regrow() : Double.MAX_VALUE;
                        events.add(new Event(Event.Kind.BROKE, t, crown(t, 0.5), t.rule.drops().roll(random, loot)));
                    }
                }
                case STUMP -> {
                    if (now >= t.backAt && (her == null
                            || Math.hypot(her.x() - t.placed.x(), her.z() - t.placed.z()) > REGROW_AWAY)) {
                        back.add(t.placed);
                    }
                }
                case STANDING -> {
                    // a tree chopped at and left: the cuts heal after a while
                    if (t.struck > 0 && now - t.lastBlow > 120) back.add(t.placed);
                }
            }
        }
        for (Scenery.Placed p : back) {
            Tree t = trees.remove(p);
            if (t != null && t.stage == Stage.STUMP) events.add(new Event(Event.Kind.REGREW, t, p.position(), List.of()));
        }
    }

    /**
     * A point {@code along} (0 to 1) the fallen trunk, from its cut to its
     * crown, lying on the ground: where the crash is heard, where what it
     * leaves lies.
     */
    public static Vec3 crown(Tree t, double along) {
        Scenery.Placed p = t.placed;
        Vec3 d = SpriteView.headingVector(t.heading);
        double reach = Felling.cut(p) + (Felling.height(p) - Felling.cut(p)) * along;
        return new Vec3(p.x() + d.x() * reach, p.y(), p.z() + d.z() * reach);
    }

    /** What happened since the last time this was asked. */
    public List<Event> drainEvents() {
        if (events.isEmpty()) return List.of();
        List<Event> out = List.copyOf(events);
        events.clear();
        return out;
    }

    /** Forget the trees no longer standing in {@code kept} (the scenery replaced). */
    public void keepOnly(Set<Scenery.Placed> kept) {
        trees.keySet().removeIf(p -> !kept.contains(p));
    }

    public void clear() {
        trees.clear();
        events.clear();
    }

    /** A copy of every tree's felling as it is (a place left, to come back to). */
    public Map<Scenery.Placed, Tree> copy() {
        Map<Scenery.Placed, Tree> out = new IdentityHashMap<>();
        for (Map.Entry<Scenery.Placed, Tree> e : trees.entrySet()) out.put(e.getKey(), new Tree(e.getValue()));
        return out;
    }

    /** The trees as {@code saved} ({@link #copy}) had them. */
    public void restore(Map<Scenery.Placed, Tree> saved) {
        trees.clear();
        events.clear();
        if (saved != null) for (Map.Entry<Scenery.Placed, Tree> e : saved.entrySet()) trees.put(e.getKey(), new Tree(e.getValue()));
    }
}
