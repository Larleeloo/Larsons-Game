package com.larsons.game.building;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.PixelPalette;
import com.larsons.game.sprite.SpriteView;

/**
 * A building standing in the world: which kind ({@link BuildingProfile}),
 * where its middle is on the ground, which way its front faces (a heading,
 * as {@link SpriteView} has them), how it looks ({@link BuildingLook}), and
 * the inside it opens onto, if it has one.
 *
 * <p>Its footprint is solid ({@link #pushOut}, {@link #blocks}): she walks
 * round it, the animals go round it.
 */
public final class Building {

    private final int id;
    private final BuildingProfile profile;
    private final Vec3 position;
    private final double heading;
    private final BuildingLook look;
    private final String interior;
    private final double hx, hz;            // its front, along the ground
    private int[][] ramps;                  // each part's six colours, once worked out
    private PixelPalette rampsFor;

    /**
     * {@code profile} standing at {@code position} (its middle, on the
     * ground), its front facing {@code heading}, looking {@code look}; {@code
     * interior} the inside it opens onto (null: none); {@code id} the level
     * object it is.
     */
    public Building(int id, BuildingProfile profile, Vec3 position, double heading, BuildingLook look,
                    String interior) {
        this.id = id;
        this.profile = profile;
        this.position = position;
        this.heading = heading;
        this.look = look == null ? new BuildingLook() : look;
        this.interior = interior;
        Vec3 h = SpriteView.headingVector(heading);
        this.hx = h.x();
        this.hz = h.z();
    }

    public int id() { return id; }

    public BuildingProfile profile() { return profile; }

    public String key() { return profile.key(); }

    /** Its middle, on the ground. */
    public Vec3 position() { return position; }

    public double heading() { return heading; }

    public BuildingLook look() { return look; }

    /** The inside it opens onto, or null. */
    public String interior() { return interior; }

    /** A point of its own (model metres: x to the right as you face its front, y towards its back) in the world, on its ground. */
    public Vec3 toWorld(double mx, double my) {
        return new Vec3(position.x() + mx * hz - my * hx, position.y(), position.z() - mx * hx - my * hz);
    }

    /** A world point's place in its own metres: {x, y}. */
    public double[] toModel(double x, double z) {
        double dx = x - position.x(), dz = z - position.z();
        return new double[]{dx * hz - dz * hx, -dx * hx - dz * hz};
    }

    /** The world's direction of its own axis x (to the right as you face its front). */
    public Vec3 axisX() { return new Vec3(hz, 0, -hx); }

    /** The world's direction of its own axis y (towards its back). */
    public Vec3 axisY() { return new Vec3(-hx, 0, -hz); }

    /** Where she stands to go in, on the ground, or null. */
    public Vec3 door() {
        double[] d = profile.door();
        return d == null ? null : toWorld(d[0], d[1]);
    }

    /** How far from its middle its footprint reaches. */
    public double reach() { return profile.reach(); }

    /** Whether a circle of {@code radius} round {@code (x, z)} overlaps its footprint. */
    public boolean blocks(double x, double z, double radius) {
        double dx = x - position.x(), dz = z - position.z();
        double r = profile.reach() + radius;
        if (dx * dx + dz * dz > r * r) return false;
        double[] m = toModel(x, z);
        for (BuildingProfile.Shape s : profile.footprint()) {
            if (s.circle()) {
                if (Math.hypot(m[0] - s.x(), m[1] - s.y()) < s.w() + radius) return true;
            } else {
                double cx = Math.max(s.x() - s.w() / 2, Math.min(s.x() + s.w() / 2, m[0]));
                double cy = Math.max(s.y() - s.d() / 2, Math.min(s.y() + s.d() / 2, m[1]));
                if (Math.hypot(m[0] - cx, m[1] - cy) < radius) return true;
            }
        }
        return false;
    }

    /**
     * {@code (x, z)} put {@code radius} clear of its footprint (out the
     * nearest way), or null when it is clear already.
     */
    public Vec3 pushOut(double x, double z, double radius) {
        double dx0 = x - position.x(), dz0 = z - position.z();
        double r = profile.reach() + radius;
        if (dx0 * dx0 + dz0 * dz0 > r * r) return null;
        double[] m = toModel(x, z);
        double mx = m[0], my = m[1];
        boolean moved = false;
        for (int pass = 0; pass < 3; pass++) {
            boolean any = false;
            for (BuildingProfile.Shape s : profile.footprint()) {
                if (s.circle()) {
                    double ex = mx - s.x(), ey = my - s.y(), d = Math.hypot(ex, ey), want = s.w() + radius;
                    if (d >= want) continue;
                    if (d < 1e-6) {
                        ex = 0;
                        ey = -1;
                        d = 1;
                    }
                    mx = s.x() + ex / d * want;
                    my = s.y() + ey / d * want;
                    any = true;
                } else {
                    double x0 = s.x() - s.w() / 2, x1 = s.x() + s.w() / 2, y0 = s.y() - s.d() / 2, y1 = s.y() + s.d() / 2;
                    double cx = Math.max(x0, Math.min(x1, mx)), cy = Math.max(y0, Math.min(y1, my));
                    double ex = mx - cx, ey = my - cy, d = Math.hypot(ex, ey);
                    if (d >= radius) continue;
                    if (d > 1e-6) {
                        mx = cx + ex / d * radius;
                        my = cy + ey / d * radius;
                    } else {
                        // inside the box: out through its nearest side
                        double l = mx - x0, rr = x1 - mx, f = my - y0, b = y1 - my;
                        double least = Math.min(Math.min(l, rr), Math.min(f, b));
                        if (least == l) mx = x0 - radius;
                        else if (least == rr) mx = x1 + radius;
                        else if (least == f) my = y0 - radius;
                        else my = y1 + radius;
                    }
                    any = true;
                }
            }
            if (!any) break;
            moved = true;
        }
        if (!moved) return null;
        Vec3 w = toWorld(mx, my);
        return new Vec3(w.x(), 0, w.z());
    }

    /**
     * How far along the ray from {@code from} going {@code dir} it first
     * meets the building - its footprint, raised to its height - or -1 when
     * it misses it (the editor's click).
     */
    public double rayHit(Vec3 from, Vec3 dir) {
        double[] o = toModel(from.x(), from.z());
        double dx = dir.x() * hz - dir.z() * hx, dy = -dir.x() * hx - dir.z() * hz;
        double y0 = position.y() - 0.1, y1 = position.y() + profile.height();
        double best = -1;
        for (BuildingProfile.Shape s : profile.footprint()) {
            double tIn = Double.NEGATIVE_INFINITY, tOut = Double.POSITIVE_INFINITY;
            if (s.circle()) {
                double ox = o[0] - s.x(), oy = o[1] - s.y();
                double a = dx * dx + dy * dy, b = 2 * (ox * dx + oy * dy), c = ox * ox + oy * oy - s.w() * s.w();
                if (a < 1e-12) {
                    if (c > 0) continue;
                } else {
                    double disc = b * b - 4 * a * c;
                    if (disc < 0) continue;
                    double r = Math.sqrt(disc);
                    tIn = (-b - r) / (2 * a);
                    tOut = (-b + r) / (2 * a);
                }
            } else {
                double[] lo = {s.x() - s.w() / 2, s.y() - s.d() / 2}, hi = {s.x() + s.w() / 2, s.y() + s.d() / 2};
                double[] org = {o[0], o[1]}, d = {dx, dy};
                boolean miss = false;
                for (int k = 0; k < 2 && !miss; k++) {
                    if (Math.abs(d[k]) < 1e-12) {
                        miss = org[k] < lo[k] || org[k] > hi[k];
                    } else {
                        double ta = (lo[k] - org[k]) / d[k], tb = (hi[k] - org[k]) / d[k];
                        tIn = Math.max(tIn, Math.min(ta, tb));
                        tOut = Math.min(tOut, Math.max(ta, tb));
                    }
                }
                if (miss) continue;
            }
            if (Math.abs(dir.y()) < 1e-12) {
                if (from.y() < y0 || from.y() > y1) continue;
            } else {
                double ta = (y0 - from.y()) / dir.y(), tb = (y1 - from.y()) / dir.y();
                tIn = Math.max(tIn, Math.min(ta, tb));
                tOut = Math.min(tOut, Math.max(ta, tb));
            }
            if (tOut < tIn || tOut < 0) continue;
            double t = Math.max(0, tIn);
            if (best < 0 || t < best) best = t;
        }
        return best;
    }

    /** Each part's six colours, dark to light, from {@code palette} (worked out once). */
    public int[][] ramps(PixelPalette palette) {
        if (ramps == null || rampsFor != palette) {
            int[][] out = new int[profile.parts().size()][];
            for (int i = 0; i < out.length; i++) {
                out[i] = BuildingLook.ramp(look.colour(profile.parts().get(i)), palette);
            }
            ramps = out;
            rampsFor = palette;
        }
        return ramps;
    }
}
