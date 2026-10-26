package com.larsons.game.combat;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.world.Border;
import com.larsons.game.world.Item;
import com.larsons.game.world.Player;
import com.larsons.game.world.Rarity;
import com.larsons.game.world.Tier;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * The fighting: every swing she makes rolls the weapon's dice - a D20 for a
 * Common weapon, up to six for a Mythic ({@link CombatTuning}) - which
 * tumble above her head ({@link #show()}) and come up as the blow lands,
 * and their total is the damage. A swing hits whatever is in reach in front
 * of her ({@link Move}); a bow, a crossbow, a casting staff or a wand sends
 * a shot ({@link Projectile}) that carries the roll to whatever it strikes.
 *
 * <p>The rare borders ({@link Border}) change what lands:
 * <ul>
 *   <li><b>Venom</b>: every hit also poisons, for half its damage again over
 *       the next ten seconds, a tick a second - 100 becomes 150;</li>
 *   <li><b>Mystic</b>: +150% damage (×2.5), for twice the mana - a spell's
 *       cost doubled, and a weapon that casts nothing draws a swing's worth
 *       ({@link CombatTuning#mysticBaseCost}) twice over. Short of mana, a
 *       spell fizzles and a Mystic swing lands as a plain one;</li>
 *   <li><b>Bonded</b>: +10% for every living thing hit with that weapon in
 *       the last five minutes, the one it is hitting now among them - hit
 *       five and, while all five stand, every hit is +50%.</li>
 * </ul>
 *
 * <p>Mana fills back up over time. What is heard is collected for the
 * world's sounds to play ({@link #drainSounds()}).
 */
public final class Combat {

    /** How long a damage number floats, in seconds. */
    public static final double POPUP_SECONDS = 1.1;
    /** How long a hit's spark or a spell's burst lasts. */
    public static final double EFFECT_SECONDS = 0.4;
    /**
     * The soonest the dice can have come up: their landing's 8 frames at 24
     * fps. A blow that lands sooner (a sword's, a third of a second in) has
     * its dice settle just after it.
     */
    public static final double MIN_ROLL = 8 / 24.0;
    /** How long the dice stay up once they have landed, before they fade. */
    public static final double SHOW_HOLD = 1.5;
    /** How far round she turns at the start of a swing to face something just off to one side, degrees. */
    public static final double SOFT_LOCK = 50;

    /** A number (or a word) floating up off something: {@code rise} over where it stands, {@code ground}. */
    public record Popup(String text, int rgb, Vec3 ground, double rise, double born, boolean small) {}

    /** What a hit throws off: a spark, or a spell's burst in its colour. */
    public record Effect(Kind kind, Vec3 at, double born, int colour) {
        public enum Kind { SPARK, BURST }
    }

    /** A line under the dice: what the weapon's border adds. */
    public record Note(String text, int rgb) {}

    /**
     * The dice above her head: the strike's roll, tumbling from {@code start}
     * and come up by {@code land}, with the border's notes under them.
     */
    public record Show(Strike strike, double start, double land, List<Note> notes) {
        /** When it has faded away. */
        public double end() {
            return land + SHOW_HOLD + 0.3;
        }
    }

    /** A sound to play: its key, and where it happens. */
    public record Sound(String key, Vec3 at) {}

    /** The swing under way: what it does, and when; what it was paid for and showed, to undo if it is cut short. */
    private static final class Swing {
        final Move move;
        final Item weapon;
        final Strike strike;      // null: a spell with no mana for it
        final double at;
        final double cost;
        final Show show;
        boolean done;

        Swing(Move move, Item weapon, Strike strike, double at, double cost, Show show) {
            this.move = move;
            this.weapon = weapon;
            this.strike = strike;
            this.at = at;
            this.cost = cost;
            this.show = show;
        }
    }

    /** Venom working on a target: a tick a second. */
    private static final class Dot {
        final Target target;
        final double perTick;
        final Vec3 from;
        int ticksLeft;
        double next;

        Dot(Target target, double perTick, int ticks, double next, Vec3 from) {
            this.target = target;
            this.perTick = perTick;
            this.ticksLeft = ticks;
            this.next = next;
            this.from = from;
        }
    }

    private CombatTuning tuning;
    private Random random = new Random();
    private double mana;
    private double now;

    private AnimState swingState;
    private double swingClock;
    private Swing swing;
    private Show show;

    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Dot> dots = new ArrayList<>();
    private final List<Popup> popups = new ArrayList<>();
    private final List<Effect> effects = new ArrayList<>();
    private final List<Sound> sounds = new ArrayList<>();
    /** Bonded: when each weapon last hit each thing. */
    private final Map<Item, Map<Target, Double>> bonds = new HashMap<>();

    public Combat(CombatTuning tuning) {
        use(tuning);
        mana = this.tuning.maxMana;
    }

    /** Play by {@code t}'s numbers from now on. */
    public void use(CombatTuning t) {
        tuning = t == null ? CombatTuning.defaults() : t;
        mana = Math.min(mana, tuning.maxMana);
    }

    public CombatTuning tuning() { return tuning; }

    /** Roll the dice from this seed from now on (tests and scripted runs). */
    public void seed(long seed) {
        random = new Random(seed);
    }

    // --- the clock ------------------------------------------------------------------

    /**
     * Advance a frame, after the player has moved: a swing starting or
     * landing, the shots in flight, the poison, mana filling back up.
     */
    public void tick(double dt, World world) {
        now = world.time();
        mana = Math.min(tuning.maxMana, mana + tuning.manaRegen * dt);
        Player p = world.player();
        List<Target> targets = world.targets();

        AnimState a = p.action();
        double t = p.stateTime();
        // put down, or swapped for something else, before the blow: it never lands
        if (swing != null && !swing.done && !Objects.equals(world.wielded(), swing.weapon)) cancel();
        if (a != swingState || t + 1e-9 < swingClock) {
            if (swing != null && !swing.done) cancel();     // cut short before it landed
            swingState = a;
            swing = a == null ? null : begin(a, world, targets);
        }
        swingClock = t;
        if (swing != null && !swing.done && t >= swing.at) {
            swing.done = true;
            land(swing, p, targets);
        }

        for (Projectile pr : projectiles) fly(pr, dt, targets);
        projectiles.removeIf(Projectile::done);
        poison(targets);
        popups.removeIf(pp -> now - pp.born() > POPUP_SECONDS);
        effects.removeIf(e -> now - e.born() > EFFECT_SECONDS);
        if (show != null && now > show.end()) show = null;
    }

    /** A swing (or a shot) starts: pay for it, roll its dice. */
    private Swing begin(AnimState state, World world, List<Target> targets) {
        Item weapon = world.wielded();
        Move move = Move.of(state, weapon);
        if (move == null) return null;
        Player p = world.player();
        double at = move.at() * p.actionDuration();

        boolean plain = plainBlow(weapon, move);
        boolean mystic = !plain && weapon != null && weapon.border() == Border.MYSTIC;
        double cost = plain ? 0 : manaCost(weapon, move);
        boolean paid = cost <= mana + 1e-9;
        if (paid) mana -= cost;
        if (!paid) {
            popups.add(new Popup("Not enough mana", 0x5D86FA, p.ground(), 2.0, now, true));
            sounds.add(new Sound("combat/fizzle", p.ground()));
            if (move.kind() == Move.Kind.SPELL) return new Swing(move, weapon, null, at, 0, null);
        }
        if (move.kind() == Move.Kind.SPELL) sounds.add(new Sound("combat/spell_cast", p.ground()));
        Target aimed = softLock(p, move, targets);

        int n = plain ? tuning.wandHeavyDice
                : (weapon == null ? tuning.unarmedDice : tuning.dice(weapon.rarity())) + (move.heavy() ? tuning.heavyExtra : 0);
        Strike strike = new Strike(weapon, DiceRoll.roll(n, tuning.sides, random), mystic && paid, plain);
        show = new Show(strike, now, now + Math.max(MIN_ROLL, at), notes(strike, aimed));
        sounds.add(new Sound("combat/dice_roll", p.ground()));
        return new Swing(move, weapon, strike, at, paid ? cost : 0, show);
    }

    /** The swing under way stops before its blow: its mana back, its dice taken down. */
    private void cancel() {
        mana = Math.min(tuning.maxMana, mana + swing.cost);
        if (show != null && show == swing.show) show = null;
        swing = null;
    }

    /** What a swing costs: a spell's mana, Mystic's double. */
    public double manaCost(Item weapon, Move move) {
        double base = move != null && move.kind() == Move.Kind.SPELL ? tuning.spellCost : 0;
        if (weapon != null && weapon.border() == Border.MYSTIC) {
            return Border.MYSTIC_MANA * Math.max(base, tuning.mysticBaseCost);
        }
        return base;
    }

    /** What the border adds, said under the dice ({@code also}: what it is aimed at, for Bonded's count). */
    private List<Note> notes(Strike s, Target also) {
        List<Note> out = new ArrayList<>();
        Border b = s.border();
        if (b == Border.MYSTIC) {
            out.add(!s.mystic() ? new Note("Mystic: no mana, no bonus", b.rgb())
                    : new Note("Mystic ×" + trim(Border.MYSTIC_DAMAGE), b.rgb()));
        } else if (b == Border.BONDED) {
            int n = bondedCount(s.weapon(), also);
            out.add(new Note("Bonded +" + Math.round(n * Border.BONDED_PER_HIT * 100) + "% (" + n + " alive)",
                    b.rgb()));
        } else if (b == Border.VENOM) {
            out.add(new Note("Venom +" + Math.round(Border.VENOM_SHARE * 100) + "% over "
                    + trim(Border.VENOM_SECONDS) + " s", b.rgb()));
        }
        return out;
    }

    /**
     * At the start of a swing or a shot, turn to face what is nearly in
     * front of her: within {@link #SOFT_LOCK}° and in reach for a swing,
     * within the aim assist and range for a shot.
     */
    private Target softLock(Player p, Move move, List<Target> targets) {
        Target t = move.shoots() ? aimAt(p.ground(), p.heading(), targets)
                : nearest(p.ground(), p.heading(), targets, move.reach() + 0.4, SOFT_LOCK);
        if (t == null) return null;
        Vec3 d = t.position().sub(p.ground());
        p.turnTo(SpriteView.headingOf(d.x(), d.z()));
        return t;
    }

    /** The blow lands, or the shot leaves. */
    private void land(Swing s, Player p, List<Target> targets) {
        if (s.strike == null) return;                     // a spell with no mana for it
        if (s.move.shoots()) {
            shoot(s.move.kind(), s.strike, p, targets);
            return;
        }
        Vec3 from = p.ground();
        List<Target> hit = new ArrayList<>();
        for (Target t : targets) {
            if (t.alive() && inReach(from, p.heading(), t, s.move.reach(), s.move.arc())) hit.add(t);
        }
        for (Target t : hit) remember(s.strike.weapon(), t);
        // every blow's damage before any of them lands, so one felled first leaves the others' Bonded count be
        double[] dealt = new double[hit.size()];
        for (int i = 0; i < hit.size(); i++) dealt[i] = damage(s.strike, hit.get(i));
        renote(s.strike, null);
        for (int i = 0; i < hit.size(); i++) strike(s.strike, hit.get(i), dealt[i], from, true);
    }

    /** The notes under {@code s}'s dice, again, now its targets are known. */
    private void renote(Strike s, Target also) {
        if (show != null && show.strike() == s && s.border() == Border.BONDED) {
            show = new Show(s, show.start(), show.land(), notes(s, also));
        }
    }

    /** Whether {@code t} is within {@code reach} of {@code from}, inside the arc round {@code heading}. */
    static boolean inReach(Vec3 from, double heading, Target t, double reach, double arc) {
        Vec3 d = t.position().sub(from);
        double dist = d.horizontalLength();
        if (dist - t.radius() > reach) return false;
        if (arc >= 360 || dist <= t.radius()) return true;
        Vec3 f = SpriteView.headingVector(heading);
        double cos = (d.x() * f.x() + d.z() * f.z()) / dist;
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
        double halfWidth = Math.toDegrees(Math.asin(Math.min(1, t.radius() / dist)));
        return angle <= arc / 2 + halfWidth;
    }

    /** The living target within {@code range} and {@code cone}° either side of {@code heading} most nearly ahead. */
    static Target nearest(Vec3 from, double heading, List<Target> targets, double range, double cone) {
        Vec3 f = SpriteView.headingVector(heading);
        Target best = null;
        double bestScore = Double.MAX_VALUE;
        for (Target t : targets) {
            if (!t.alive()) continue;
            Vec3 d = t.position().sub(from);
            double dist = d.horizontalLength();
            if (dist < 1e-6 || dist - t.radius() > range) continue;
            double cos = (d.x() * f.x() + d.z() * f.z()) / dist;
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
            if (angle > cone) continue;
            double score = angle + dist * 0.5;
            if (score < bestScore) {
                best = t;
                bestScore = score;
            }
        }
        return best;
    }

    /** What a shot from {@code from} along {@code heading} homes in on, or null. */
    Target aimAt(Vec3 from, double heading, List<Target> targets) {
        return nearest(from, heading, targets, tuning.range, tuning.aimAssist);
    }

    /** Send off an arrow, a bolt or a spell from her hands, at what she aims at or on ahead. */
    private void shoot(Move.Kind kind, Strike strike, Player p, List<Target> targets) {
        Vec3 f = SpriteView.headingVector(p.heading());
        double h = switch (kind) {
            case ARROW -> 1.35;
            case BOLT -> 1.2;
            default -> 1.45;
        };
        double speed = switch (kind) {
            case ARROW -> tuning.arrowSpeed;
            case BOLT -> tuning.boltSpeed;
            default -> tuning.spellSpeed;
        };
        Vec3 hand = p.ground().add(f.scale(0.45)).add(0, h, 0);
        Target t = aimAt(p.ground(), p.heading(), targets);
        Vec3 to;
        if (t != null) to = t.position().add(0, t.height() * 0.62, 0);
        else if (kind == Move.Kind.SPELL) to = hand.add(f.scale(tuning.range));
        else to = p.ground().add(f.scale(tuning.range * 0.7));     // on ahead, coming down in the grass
        Vec3 v = Projectile.aim(kind, hand, to, speed);
        double life = kind == Move.Kind.SPELL ? tuning.range / Math.max(1e-6, speed) : 8;
        projectiles.add(new Projectile(kind, strike, spellColour(strike.weapon()), hand, v, life));
    }

    /** One shot's flight this frame: what it strikes, where it comes down. */
    private void fly(Projectile pr, double dt, List<Target> targets) {
        if (pr.done()) return;
        if (pr.stuck()) {
            pr.advance(dt);
            if (pr.stuckFor() >= tuning.stuckSeconds) pr.finish();
            return;
        }
        // in steps short enough that nothing is flown through
        double speed = Math.max(1, pr.velocity().length());
        int steps = Math.max(1, (int) Math.ceil(speed * dt / 0.25));
        for (int i = 0; i < steps && !pr.done() && !pr.stuck(); i++) {
            Vec3 a = pr.advance(dt / steps), b = pr.position();
            Target hit = struck(a, b, targets);
            if (hit != null) {
                Vec3 back = pr.velocity().normalize(Vec3.ZERO);
                remember(pr.strike().weapon(), hit);
                renote(pr.strike(), hit);
                strike(pr.strike(), hit, damage(pr.strike(), hit), hit.position().sub(back), false);
                Vec3 at = closestOnSegment(a, b, hit.position());
                effects.add(pr.kind() == Move.Kind.SPELL ? new Effect(Effect.Kind.BURST, at, now, pr.colour())
                        : new Effect(Effect.Kind.SPARK, at, now, 0));
                sounds.add(new Sound(pr.kind() == Move.Kind.SPELL ? "combat/spell_hit"
                        : pr.kind() == Move.Kind.BOLT ? "combat/bolt_hit" : "combat/arrow_hit", at));
                pr.finish();
            } else if (b.y() <= 0) {
                double k = a.y() / Math.max(1e-9, a.y() - b.y());
                Vec3 ground = a.add(b.sub(a).scale(Math.max(0, Math.min(1, k))));
                ground = new Vec3(ground.x(), 0, ground.z());
                if (pr.kind() == Move.Kind.SPELL) {
                    effects.add(new Effect(Effect.Kind.BURST, ground.add(0, 0.1, 0), now, pr.colour()));
                    pr.finish();
                } else {
                    pr.stick(ground);
                    sounds.add(new Sound("combat/arrow_stick", ground));
                }
            }
        }
        if (!pr.done() && !pr.stuck() && pr.age() >= pr.life()) {
            if (pr.kind() == Move.Kind.SPELL) effects.add(new Effect(Effect.Kind.BURST, pr.position(), now, pr.colour()));
            pr.finish();
        }
    }

    /** The living target the segment {@code a}–{@code b} passes through, or null. */
    static Target struck(Vec3 a, Vec3 b, List<Target> targets) {
        Target best = null;
        double bestT = Double.MAX_VALUE;
        for (Target t : targets) {
            if (!t.alive()) continue;
            Vec3 q = closestOnSegment(a, b, t.position());
            double r = t.radius() + Projectile.THICKNESS;
            if (q.horizontalDistance(t.position()) > r || q.y() < 0 || q.y() > t.height()) continue;
            double along = q.sub(a).length();
            if (along < bestT) {
                best = t;
                bestT = along;
            }
        }
        return best;
    }

    /** The point of the segment {@code a}–{@code b} nearest the upright line through {@code c} (measured flat). */
    static Vec3 closestOnSegment(Vec3 a, Vec3 b, Vec3 c) {
        double dx = b.x() - a.x(), dz = b.z() - a.z();
        double len2 = dx * dx + dz * dz;
        double t = len2 < 1e-12 ? 0 : ((c.x() - a.x()) * dx + (c.z() - a.z()) * dz) / len2;
        t = Math.max(0, Math.min(1, t));
        return a.add(b.sub(a).scale(t));
    }

    // --- damage ---------------------------------------------------------------------

    /**
     * What {@code s} does to {@code t}: the dice's total, ×2.5 for a Mystic
     * weapon that had the mana, +10% for each living thing a Bonded weapon
     * has hit in the last five minutes ({@code t} counted), rounded to a
     * whole number.
     */
    public double damage(Strike s, Target t) {
        double d = s.roll().total();
        if (s.mystic()) d *= Border.MYSTIC_DAMAGE;
        if (s.border() == Border.BONDED) d *= 1 + Border.BONDED_PER_HIT * bondedCount(s.weapon(), t);
        return Math.round(d);
    }

    /**
     * {@code s} lands on {@code t} for {@code d}, dealt from {@code from}; a
     * swing's blow ({@code melee}) throws a spark off the side it struck (a
     * shot makes its own).
     */
    private void strike(Strike s, Target t, double d, Vec3 from, boolean melee) {
        t.hurt(d, from, true);
        popups.add(new Popup(format(d), s.rarity().rgb(), t.position(), t.height() + 0.25, now, false));
        if (s.border() == Border.VENOM && d > 0) {
            int ticks = (int) Math.round(Border.VENOM_SECONDS);
            dots.add(new Dot(t, d * Border.VENOM_SHARE / ticks, ticks, now + 1, from));
        }
        if (melee) {
            Vec3 toward = from.sub(t.position());
            toward = new Vec3(toward.x(), 0, toward.z()).normalize(Vec3.ZERO);
            effects.add(new Effect(Effect.Kind.SPARK,
                    t.position().add(0, t.height() * 0.62, 0).add(toward.scale(t.radius())), now, 0));
            sounds.add(new Sound("combat/blow", t.position()));
        }
    }

    /** The poison ticks due this frame. */
    private void poison(List<Target> targets) {
        for (Iterator<Dot> it = dots.iterator(); it.hasNext(); ) {
            Dot d = it.next();
            if (!d.target.alive()) {
                it.remove();
                continue;
            }
            while (d.ticksLeft > 0 && now >= d.next) {
                d.target.hurt(d.perTick, d.from, false);
                popups.add(new Popup(format(d.perTick), Border.VENOM.rgb(), d.target.position(), d.target.height() + 0.25,
                        now, true));
                sounds.add(new Sound("combat/venom_tick", d.target.position()));
                d.ticksLeft--;
                d.next += 1;
                if (!d.target.alive()) break;
            }
            if (d.ticksLeft <= 0 || !d.target.alive()) it.remove();
        }
    }

    /** Bonded: remember that {@code weapon} hit {@code t} now. */
    private void remember(Item weapon, Target t) {
        if (weapon == null || weapon.border() != Border.BONDED) return;
        bonds.computeIfAbsent(weapon, k -> new IdentityHashMap<>()).put(t, now);
    }

    /**
     * How many living things {@code weapon} has hit in the last five
     * minutes, counting {@code also} (the one being hit now; null: none).
     */
    public int bondedCount(Item weapon, Target also) {
        int n = 0;
        Map<Target, Double> hits = weapon == null ? null : bonds.get(weapon);
        if (hits != null) {
            hits.values().removeIf(at -> now - at > Border.BONDED_SECONDS);
            for (Target t : hits.keySet()) if (t.alive() || t == also) n++;
        }
        if (also != null && (hits == null || !hits.containsKey(also))) n++;
        return n;
    }

    // --- weapons --------------------------------------------------------------------

    /**
     * Whether {@code weapon}'s main attack is a spell: a wand's always, a
     * staff's from the Magic Staff up (a Basic Staff is plain wood, and
     * thrusts).
     */
    public static boolean casts(Item weapon) {
        if (weapon == null) return false;
        if (weapon.id().contains("wand")) return true;
        return weapon.def().stance() == Stance.STAFF && weapon.tier() != null && weapon.tier() != Tier.BASIC;
    }

    /**
     * Whether {@code move} with {@code weapon} is a plain blow: a wand's heavy
     * attack. Its great two-handed cast sends no spell - it is the wand
     * brought down on whatever is in front of her - so it rolls one D20
     * ({@link CombatTuning#wandHeavyDice}) whatever the wand's rarity, costs
     * no mana, and its border (Mystic, Venom, Bonded) adds nothing.
     */
    public static boolean plainBlow(Item weapon, Move move) {
        return weapon != null && move != null && move.heavy() && !move.shoots()
                && (weapon.def().stance() == Stance.WAND || weapon.id().contains("wand"));
    }

    /**
     * The colour of what {@code weapon} shoots: its tier's (blue for Magic,
     * yellow for Honed), else its rarity's - but Magic's blue for a Common
     * one (a plain wand), whose white would hardly show.
     */
    public static int spellColour(Item weapon) {
        if (weapon == null) return Tier.MAGIC.projectile();
        if (weapon.tier() != null && weapon.tier().projectile() >= 0) return weapon.tier().projectile();
        if (weapon.rarity() == Rarity.COMMON) return Tier.MAGIC.projectile();
        return weapon.rarity().rgb();
    }

    // --- what is shown and heard ------------------------------------------------------

    /** A damage number as shown: whole, or to a tenth when under ten and not whole (a poison tick). */
    public static String format(double d) {
        if (Math.abs(d - Math.rint(d)) < 0.05 || d >= 10) return String.valueOf(Math.round(d));
        return String.format(java.util.Locale.ROOT, "%.1f", d);
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    public double mana() { return mana; }

    public double maxMana() { return tuning.maxMana; }

    /** Set the mana (scripts and tests). */
    public void setMana(double m) {
        mana = Math.max(0, Math.min(tuning.maxMana, m));
    }

    /** The dice above her head, or null. */
    public Show show() { return show; }

    public List<Projectile> projectiles() { return projectiles; }

    public List<Popup> popups() { return popups; }

    public List<Effect> effects() { return effects; }

    /** The world's time as of the last tick. */
    public double now() { return now; }

    /** The sounds made since the last call, for the world's sounds to play. */
    public List<Sound> drainSounds() {
        List<Sound> out = new ArrayList<>(sounds);
        sounds.clear();
        return out;
    }
}
