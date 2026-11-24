package com.larsons.game.actor;

import com.larsons.game.ambient.Animals;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;

/**
 * What one of the folk sees - a foe, or a person in the street - and how far:
 * its line of sight decides how far off it notices her.
 *
 * <pre>
 *   straight ahead, within 30° either way    its whole reach
 *   out to the edge of its eyes (75°)        less and less of it, down to 40 %
 *   behind that                              nothing: she can only be heard
 *   its reach                                its kind's sight, times how easily she is noticed
 *                                            ({@code world.Stealth}: crouched, the dark, a lamp's light ...)
 *   once it is after her                     all round, half as far again, whatever she does
 * </pre>
 *
 * <p>And only along a clear line from its eyes to her ({@link Paths#sees}):
 * not through a hill, a wall, a house, a tree's trunk or a rock - which hide
 * her better crouched, as she is lower behind them.
 *
 * <p>Behind it, or too far to be seen, she may still be <b>heard</b> ({@code
 * Stealth#heard}: a creep hardly, a sprint from far off): it turns to look
 * where the sound came from ({@link #LISTEN} s) - and sees her, if she is
 * there to be seen. So near it could touch her ({@link #TOUCH}) it knows she
 * is there whatever she does.
 *
 * <p>{@link #fan} is what it sees of the ground, for the fan drawn under a foe
 * as she sneaks: each way across its eyes, how far along it she would be
 * seen, crouched or standing as she is - the same reckoning, line by line.
 */
public final class Sight {

    private Sight() {}

    /** Within this either way of where it faces it sees as far as it can; at {@link #EDGE} its eyes end. */
    public static final double FULL = Math.toRadians(30), EDGE = Math.toRadians(75);
    /** At the edge of its eyes, the share of its reach it still sees. */
    public static final double SIDE = 0.4;
    /** So near it knows she is there, whatever she does (bumped into), metres. */
    public static final double TOUCH = 1.1;
    /** Once it is after her, how much farther it sees - and all round. */
    public static final double CHASING = 1.6;
    /** How long it stands looking toward a sound it heard, seconds. */
    public static final double LISTEN = 2.5;
    /** How high her eyes-worth is to be seen at: standing, crouched, swimming, riding (metres over her feet). */
    public static final double STANDING = 1.2, CROUCHED = 0.7, SWIMMING = 0.35, RIDING = 2.0;

    /**
     * How much of its reach it sees {@code off} radians either side of where
     * it faces: all of it within {@link #FULL}, down to {@link #SIDE} at
     * {@link #EDGE}, none past it.
     */
    public static double cone(double off) {
        off = Math.abs(off);
        if (off <= FULL) return 1;
        if (off > EDGE + 1e-9) return 0;
        return 1 - (1 - SIDE) * Math.min(1, (off - FULL) / (EDGE - FULL));
    }

    /** How far {@code a} radians is from {@code b}, either way (0 – π). */
    public static double between(double a, double b) {
        double d = a - b;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return Math.abs(d);
    }

    /**
     * How far {@code f} sees her looking {@code bearing} (a heading, radians)
     * when she is {@code conspicuous} ({@code world.Stealth}): its sight
     * times that, as much of it as its eyes take in that way - or, once it is
     * after her, half as far again all round.
     */
    public static double reach(Folk f, double bearing, double conspicuous) {
        if (f.alerted) return f.sight * CHASING;
        return f.sight * conspicuous * cone(between(bearing, f.heading));
    }

    /** Where {@code f}'s eyes are, metres up. */
    public static double eyes(Folk f) {
        return f.y + (f.crouched ? 0.7 : 1.55) * f.size();
    }

    /** The heading from {@code f} to {@code (x, z)}. */
    static double bearing(Folk f, double x, double z) {
        return SpriteView.headingOf(x - f.x, z - f.z);
    }

    /**
     * Whether {@code f} sees her at {@code feet}, {@code height} metres of her
     * over them to be seen, when she is {@code conspicuous}: near enough that
     * way for its eyes ({@link #reach}), and a clear line to her - or so near
     * it could touch her.
     */
    public static boolean sees(Folk f, Vec3 feet, double height, double conspicuous, Animals.Place place) {
        double dx = feet.x() - f.x, dz = feet.z() - f.z, dist = Math.hypot(dx, dz);
        if (dist <= TOUCH && Math.abs(feet.y() - f.y) < 2) return true;
        if (dist > reach(f, bearing(f, feet.x(), feet.z()), conspicuous)) return false;
        return Paths.sees(f.x, eyes(f), f.z, feet.x(), feet.y() + height, feet.z(), place);
    }

    /** How far a step of the {@link #fan} is, metres. */
    public static final double FAN_STEP = 0.5;

    /**
     * What {@code f} sees of the ground before it, {@code rays} ways across
     * its eyes from one edge to the other: how far along each she would be
     * seen with {@code height} metres of her to be seen, when she is {@code
     * conspicuous} - its {@link #reach} that way, cut short where a hill, a
     * wall, a house, a tree or a rock first hides her ({@link
     * Animals.Place#sightTop}). Returns {headings..., distances...}: the
     * first {@code rays} the ways, the rest how far.
     */
    public static double[] fan(Folk f, Animals.Place place, double conspicuous, double height, int rays) {
        double[] out = new double[rays * 2];
        double eye = eyes(f);
        for (int k = 0; k < rays; k++) {
            double bearing = f.heading - EDGE + 2 * EDGE * k / Math.max(1, rays - 1);
            out[k] = bearing;
            double far = reach(f, bearing, conspicuous);
            Vec3 dir = SpriteView.headingVector(bearing);
            double most = Double.NEGATIVE_INFINITY;              // (the steepest anything in the way rises, seen from its eyes)
            double seen = far;
            for (double s = FAN_STEP; s <= far; s += FAN_STEP) {
                double x = f.x + dir.x() * s, z = f.z + dir.z() * s;
                double g = place.ground(x, z);
                // she, standing here: seen if the line to her clears everything before her
                if ((g + height - eye) / s < most - 1e-9) {
                    seen = s - FAN_STEP / 2;
                    break;
                }
                double top = Math.max(g, place.sightTop(x, z));
                most = Math.max(most, (top - eye) / s);
            }
            out[rays + k] = Math.max(0, seen);
        }
        return out;
    }
}
