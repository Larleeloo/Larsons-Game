package com.larsons.game.math;

/**
 * An immutable 3-vector in world space: metres, +Y up, the ground at
 * {@code y = 0}. Right-handed, so with +Y up and the default camera looking
 * down −Z, +X is to the right.
 */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 UP = new Vec3(0, 1, 0);

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }

    public Vec3 add(double dx, double dy, double dz) { return new Vec3(x + dx, y + dy, z + dz); }

    public Vec3 sub(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }

    public Vec3 scale(double s) { return new Vec3(x * s, y * s, z * s); }

    public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }

    public Vec3 cross(Vec3 o) {
        return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double length() { return Math.sqrt(x * x + y * y + z * z); }

    /** Length in the ground plane, ignoring height. */
    public double horizontalLength() { return Math.sqrt(x * x + z * z); }

    /** This vector at unit length, or {@code fallback} when it is (nearly) zero. */
    public Vec3 normalize(Vec3 fallback) {
        double len = length();
        return len < 1e-9 ? fallback : scale(1.0 / len);
    }

    public Vec3 normalize() { return normalize(ZERO); }

    /** Linear interpolation toward {@code o}. */
    public Vec3 lerp(Vec3 o, double t) {
        return new Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }

    public double distance(Vec3 o) { return sub(o).length(); }

    public double horizontalDistance(Vec3 o) { return sub(o).horizontalLength(); }
}
