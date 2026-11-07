package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.SceneryProfile;

/**
 * How easily she is noticed: a number the animals' noticing distances are
 * multiplied by (and the enemies' to come) - 1 walking about in the open by
 * day, less crouched, still, in tall grass, in the dark or under the water,
 * more running, sprinting, riding or fighting.
 *
 * <pre>
 *   crouched and still  0.35   crouched, moving  0.5    standing still  0.7
 *   walking             1.0    running           1.35   sprinting       1.7
 *   riding              1.5    swimming          0.8    under the water 0.25
 *   swinging or casting +0.3
 *   in tall grass       x0.6 crouched (x0.85 standing)
 *   at night            x0.6 (twilight between)         fog, rain, snow x0.85
 *   a card's cloak      x its own ({@code Buffs})
 * </pre>
 *
 * <p>At {@link #HIDDEN} or less she is <b>hidden</b>: a skittish animal lets
 * her come nearly up to it, and her blows are <b>sneak attacks</b> - twice
 * the damage ({@link #SNEAK_DAMAGE}), said under the dice.
 *
 * @param score  the number
 * @param grass  whether she is in tall grass
 * @param dark   how dark it is, 0 (day) - 1 (night)
 */
public record Stealth(double score, boolean grass, double dark) {

    /** At or below this she is hidden. */
    public static final double HIDDEN = 0.45;
    /** A sneak attack's damage, times a blow's. */
    public static final double SNEAK_DAMAGE = 2.0;
    /** How near a tuft of tall grass has to be to hide her, metres. */
    public static final double GRASS_REACH = 0.9;

    public static final Stealth OPEN = new Stealth(1, false, 0);

    public boolean hidden() { return score <= HIDDEN; }

    /** What the HUD says: Hidden, Unseen (quiet, but in view) or Exposed. */
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
        AnimState a = p.action();
        if (a != null && (a.key().contains("attack") || a.key().endsWith("_fire") || a.key().contains("bash"))) {
            base += 0.3;
        }
        return base;
    }

    /** How she stands now in {@code world}: her pace, its cover, its light, and any card's cloak. */
    public static Stealth of(World world) {
        Player p = world.player();
        double score = pace(p);
        boolean grass = !p.swimming() && inGrass(world.scenery(), p.ground());
        if (grass) score *= p.crouched() ? 0.6 : 0.85;
        double dark = 1 - Math.max(0, Math.min(1, world.daylight().day()));
        score *= 1 - 0.4 * dark;
        if (world.weatherNow().level() > 0.3) score *= 0.85;
        score *= p.buffs().stealth();
        return new Stealth(score, grass, dark);
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
