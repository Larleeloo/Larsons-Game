package com.larsons.game.world;

import com.larsons.game.math.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The pieces a barrel, a crate, a chest or a pot shatters into: splinters,
 * staves and shards thrown up and out from where it stood, tumbling, falling,
 * bouncing on the ground and skidding to a stop - then fading away. Each is
 * a little flat piece in one of what broke's colours; {@code WorldRenderer}
 * draws them.
 */
public final class Shards {

    /** How long a piece lies once it has come to rest before it is gone, and how long it fades. */
    public static final double REST = 2.2, FADE = 0.8;
    private static final double GRAVITY = 14, BOUNCE = 0.38, FRICTION = 0.55;

    /** One piece: where it is, how it flies, how it turns, how big it is, its colour. */
    public static final class Piece {
        Vec3 at, velocity;
        double angle, spin, width, height, age, still;
        /** The floor it falls to when what broke stood on one above the ground (indoors, upstairs), else NaN. */
        double floor = Double.NaN;
        final int rgb;
        boolean resting;

        Piece(Vec3 at, Vec3 velocity, double spin, double width, double height, int rgb) {
            this.at = at;
            this.velocity = velocity;
            this.spin = spin;
            this.width = width;
            this.height = height;
            this.rgb = rgb;
        }

        public Vec3 at() { return at; }

        /** How far it has turned, radians - in the screen's plane, as it is drawn. */
        public double angle() { return angle; }

        public double width() { return width; }

        public double height() { return height; }

        public int rgb() { return rgb; }

        /** How much of it shows: 1 until it has lain still a while, then fading to 0. */
        public double alpha() {
            return still <= REST ? 1 : Math.max(0, 1 - (still - REST) / FADE);
        }

        public boolean gone() { return still > REST + FADE || age > 12; }

        /**
         * How high its middle is over the ground when it lies there: its
         * lowest corner just on it, as it is turned (it is drawn upright,
         * and what of it went under the ground would be hidden).
         */
        public double lift() {
            return 0.5 * (width * Math.abs(Math.sin(angle)) + height * Math.abs(Math.cos(angle))) + 0.005;
        }
    }

    private final List<Piece> pieces = new ArrayList<>();
    private final Random random = new Random(0x5A4D);

    /** The pieces flying or lying about. */
    public List<Piece> pieces() { return Collections.unmodifiableList(pieces); }

    /**
     * {@code count} pieces from something {@code height} metres tall and
     * {@code radius} round, standing at {@code at} - each one of {@code
     * colours} (its parts' colours, the more of a colour the more pieces in
     * it), each {@code size} metres across or so.
     */
    public void burst(Vec3 at, double radius, double height, int[] colours, int count, double size, Terrain terrain) {
        if (colours == null || colours.length == 0) colours = new int[]{0x8A5A30};
        double ground = terrain == null ? 0 : terrain.height(at.x(), at.z());
        double floor = at.y() - ground > 0.3 ? at.y() : Double.NaN;
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = radius * Math.sqrt(random.nextDouble());
            double y = height * (0.15 + 0.8 * random.nextDouble());
            Vec3 from = at.add(Math.cos(a) * r, y, Math.sin(a) * r);
            double out = 1.4 + 2.6 * random.nextDouble();
            Vec3 v = new Vec3(Math.cos(a) * out, 2.0 + 3.5 * random.nextDouble(), Math.sin(a) * out);
            double s = size * (0.45 + 0.9 * random.nextDouble());
            double stretch = 0.35 + 0.65 * random.nextDouble();          // splinters long and thin, some square
            int rgb = colours[random.nextInt(colours.length)];
            double spin = (random.nextBoolean() ? 1 : -1) * (5 + 9 * random.nextDouble());
            Piece p = new Piece(from, v, spin, s, s * stretch, shade(rgb, 0.78 + 0.34 * random.nextDouble()));
            p.angle = random.nextDouble() * Math.PI * 2;
            p.floor = floor;
            pieces.add(p);
        }
    }

    /** Fly, fall, bounce and settle on {@code terrain}; those long still fade and go. */
    public void tick(double dt, Terrain terrain) {
        if (pieces.isEmpty()) return;
        for (Piece p : pieces) {
            p.age += dt;
            if (p.resting) {
                p.still += dt;
                continue;
            }
            Vec3 v = p.velocity.add(0, -GRAVITY * dt, 0);
            Vec3 next = p.at.add(v.scale(dt));
            double floor = !Double.isNaN(p.floor) ? p.floor : terrain == null ? 0 : terrain.height(next.x(), next.z());
            double lift = p.lift();
            if (next.y() <= floor + lift) {
                next = new Vec3(next.x(), floor + lift, next.z());
                if (Math.abs(v.y()) < 1.2) {
                    p.resting = true;
                    v = Vec3.ZERO;
                    p.spin = 0;
                } else {
                    v = new Vec3(v.x() * FRICTION, -v.y() * BOUNCE, v.z() * FRICTION);
                    p.spin *= 0.6;
                }
            }
            p.at = next;
            p.velocity = v;
            p.angle += p.spin * dt;
        }
        pieces.removeIf(Piece::gone);
    }

    public void clear() {
        pieces.clear();
    }

    private static int shade(int rgb, double k) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * k);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * k);
        int b = (int) Math.min(255, (rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }
}
