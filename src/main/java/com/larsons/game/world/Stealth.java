package com.larsons.game.world;

import com.larsons.game.building.Building;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.SceneryProfile;

/**
 * How easily she is noticed: a number the foes', the people's and the
 * animals' noticing distances are multiplied by - 1 walking about in the
 * open by day, less crouched, still, in tall grass, in the dark or under the
 * water, more running, sprinting, riding or fighting - and how far off she
 * is heard ({@link #noise}).
 *
 * <pre>
 *   crouched and still  0.35   crouched, moving  0.5    standing still  0.7
 *   walking             1.0    running           1.35   sprinting       1.7
 *   riding              1.5    swimming          0.8    under the water 0.25
 *   swinging or casting +0.3
 *   in tall grass       x0.6 crouched (x0.85 standing)
 *   in the dark         x0.6 at night (twilight between; a cave is always dark)
 *   in a light's glow   the dark's help taken away, as far as the light reaches her
 *   fog, rain, snow     x0.85
 *   a card's cloak      x its own ({@code Buffs})
 *   her gear's Stealth  x (1 - its share)
 * </pre>
 *
 * <p><b>Light.</b> At night the dark hides her - unless she stands in a
 * light: a street lamp, a lantern on a post, a candle, a fireplace, an open
 * chest, a glowing crystal, or the lantern she carries lit (which lights her
 * as brightly as day). Each light's glow is brightest at it and gone at the
 * edge of its reach ({@link #light}); in it she loses that much of the dark's
 * help. Her gear's <b>Shadow</b> lets the lights show her less.
 *
 * <p><b>Noise.</b> Behind a foe's back she is not seen but heard ({@code
 * actor/Folks}): {@link #noise} times {@link #HEARS} metres - a crouched
 * creep is barely heard, a sprint far off. Rain and wind cover her steps;
 * her gear's <b>Quiet steps</b> softens them.
 *
 * <p>At {@link #HIDDEN} or less she is <b>hidden</b>: a skittish animal lets
 * her come nearly up to it, and - so long as nobody has spotted her ({@code
 * World#spotted}) - her blows are <b>sneak attacks</b>: twice the damage
 * ({@link #SNEAK_DAMAGE}, more with her gear's Sneak attack), said under the
 * dice.
 *
 * @param score  the number
 * @param grass  whether she is in tall grass
 * @param dark   how dark it is, 0 (day) - 1 (night)
 * @param lit    how much light falls on her as it counts against the dark, 0 – 1 (her gear's Shadow taken off)
 * @param noise  how loud she is: times {@link #HEARS} is how far off she is heard
 */
public record Stealth(double score, boolean grass, double dark, double lit, double noise) {

    /** At or below this she is hidden. */
    public static final double HIDDEN = 0.45;
    /** A sneak attack's damage, times a blow's. */
    public static final double SNEAK_DAMAGE = 2.0;
    /** How near a tuft of tall grass has to be to hide her, metres. */
    public static final double GRASS_REACH = 0.9;
    /** How far off she is heard walking (noise 1), metres. */
    public static final double HEARS = 3.5;
    /** How dark it is in a cave, whatever the hour. */
    public static final double CAVE_DARK = 0.85;
    /** How much of the dark's help a light's glow takes away at its brightest; lights add up to no more than 1. */
    static final double GLOW = 1.3;

    public static final Stealth OPEN = new Stealth(1, false, 0, 0, 1);

    /** One with no light on her, as loud as walking. */
    public Stealth(double score, boolean grass, double dark) {
        this(score, grass, dark, 0, 1);
    }

    public boolean hidden() { return score <= HIDDEN; }

    /** What a sneak attack's damage is times, with what she wears: twice, more with her gear's Sneak attack. */
    public static double sneakDamage(Gear.Totals worn) {
        return SNEAK_DAMAGE * (worn == null ? 1 : worn.sneakDamageTimes());
    }

    /** Whether it is dark and a light shows her up: what the HUD says ("in the light"). */
    public boolean inTheLight() { return dark > 0.3 && lit > 0.35; }

    /** How far off she is heard now, metres. */
    public double heard() { return HEARS * noise; }

    /** What the HUD says: Hidden, Quiet (hard to see, but in view), In view or Loud. */
    public String label() {
        return hidden() ? "Hidden" : score < 0.9 ? "Quiet" : score > 1.2 ? "Loud" : "In view";
    }

    /** How she moves, swims or rides: the first column of the table. */
    public static double pace(Player p) {
        if (p.riding() != Player.Riding.NO) return 1.5;
        if (p.swimming()) return p.underWater() ? 0.25 : 0.8;
        double speed = p.velocity().horizontalLength();
        double base;
        if (p.crouched()) base = speed > 0.25 ? 0.5 : 0.35;
        else if (speed < 0.25) base = 0.7;
        else if (speed <= AnimState.WALK.speed() + 0.3) base = 1.0;
        else if (speed <= AnimState.RUN.speed() + 0.3) base = 1.35;
        else base = 1.7;
        if (fighting(p)) base += 0.3;
        return base;
    }

    /** Whether she is fighting: a blow, a shot - or full auto's bolts streaming out. */
    static boolean fighting(Player p) {
        AnimState a = p.action();
        // (an axe's felling blow rings out through the wood like any other)
        return a != null && (a.key().contains("attack") || a.key().endsWith("_fire") || a.key().contains("bash")
                || a == AnimState.AXE_CHOP)
                || p.autoFiring();
    }

    /**
     * How loud she is (times {@link #HEARS} is how far off she is heard):
     * crouched and still hardly at all, creeping a little, walking 1,
     * running and sprinting more - and a blow, a shot or a spell more again.
     */
    public static double noise(Player p) {
        double n;
        if (p.riding() != Player.Riding.NO) n = 2.4;
        else if (p.swimming()) n = p.underWater() ? 0.2 : 1.0;
        else {
            double speed = p.velocity().horizontalLength();
            if (p.crouched()) n = speed <= 0.25 ? 0.12 : p.state().key().endsWith("_fast") ? 0.55 : 0.3;
            else if (speed < 0.25) n = 0.3;
            else if (speed <= AnimState.WALK.speed() + 0.3) n = 1.0;
            else if (speed <= AnimState.RUN.speed() + 0.3) n = 1.8;
            else n = 2.6;
        }
        if (fighting(p)) n += 0.8;
        return n;
    }

    /** How she stands now in {@code world}: her pace, its cover, its light, any card's cloak and her gear. */
    public static Stealth of(World world) {
        Player p = world.player();
        Gear.Totals worn = p.gear();
        double score = pace(p);
        boolean grass = !p.swimming() && inGrass(world.scenery(), p.ground());
        if (grass) score *= p.crouched() ? 0.6 : 0.85;
        double dark = 1 - Math.max(0, Math.min(1, world.daylight().day()));
        if (world.over() == Sky.Cover.CAVE) dark = Math.max(dark, CAVE_DARK);
        double lit = dark > 0.02 ? light(world) * worn.lightTimes() : 0;
        score *= 1 - 0.4 * dark * (1 - lit);
        boolean weather = world.weatherNow().level() > 0.3;
        if (weather) score *= 0.85;
        score *= p.buffs().stealth();
        score *= worn.stealthTimes();
        double noise = noise(p) * worn.noiseTimes() * (weather ? 0.75 : 1);
        return new Stealth(score, grass, dark, lit, noise);
    }

    /**
     * How much light falls on her, 0 – 1: the glow of every light near her,
     * each brightest at it and gone at the edge of its reach, together never
     * more than 1 - and her own lantern, lit, all of it.
     */
    public static double light(World world) {
        Player p = world.player();
        if (p.lanternLit()) return 1;
        Vec3 at = p.ground().add(0, 0.9, 0);
        double dark = 1;                        // (what is left unlit: each light takes its share of it)
        for (World.LightSpot s : world.lights()) dark *= 1 - glow(at, s.at(), s.radius(), s.strength());
        for (Building b : world.buildings()) {
            if (!burns(b)) continue;
            Vec3 front = com.larsons.game.sprite.SpriteView.headingVector(b.heading());
            dark *= 1 - glow(at, b.position().add(front.x() * 0.6, 0.5, front.z() * 0.6), 4.8, 0.8);
        }
        for (Chest c : world.chests()) {
            if (c.light() > 0) dark *= 1 - glow(at, c.position().add(0, 0.7, 0), 3.6, 0.95 * c.light());
        }
        for (Scenery.Placed s : world.scenery().placed()) {
            SceneryProfile pr = s.sprite().profile();
            if (pr.glow() < 0) continue;
            if (Math.abs(s.x() - at.x()) > 6 || Math.abs(s.z() - at.z()) > 6) continue;
            boolean tree = pr.kind() == SceneryProfile.Kind.TREE;
            Vec3 heart = s.position().add(0, pr.frameWorldHeight() * s.scale() * 0.3, 0);
            dark *= 1 - glow(at, heart, tree ? 4.0 : 2.4, tree ? 0.8 : 0.65);
        }
        return Math.max(0, Math.min(1, 1 - dark));
    }

    /** One light's glow on {@code at}: {@code strength} times {@link #GLOW} at it, nothing at {@code radius}, at most 1. */
    static double glow(Vec3 at, Vec3 light, double radius, double strength) {
        if (radius <= 0 || strength <= 0) return 0;
        double d = at.sub(light).length();
        if (d >= radius) return 0;
        return Math.max(0, Math.min(1, strength * GLOW * (1 - d / radius)));
    }

    /** Whether a building has a part that is always lit (a fireplace's fire), as the renderer lights it. */
    static boolean burns(Building b) {
        for (com.larsons.game.building.BuildingProfile.Part part : b.profile().parts()) if (part.always()) return true;
        return false;
    }

    /** Whether a tuft of tall grass stands within {@link #GRASS_REACH} of {@code at}. */
    static boolean inGrass(Scenery scenery, Vec3 at) {
        if (scenery == null) return false;
        for (Scenery.Placed s : scenery.placed()) {
            if (s.sprite().profile().kind() != SceneryProfile.Kind.TUFT) continue;
            double dx = s.x() - at.x(), dz = s.z() - at.z();
            double r = GRASS_REACH * s.scale();
            if (dx * dx + dz * dz <= r * r && s.sprite().name().contains("grass")) return true;
        }
        return false;
    }
}
