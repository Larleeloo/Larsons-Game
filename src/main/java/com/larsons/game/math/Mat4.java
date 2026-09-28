package com.larsons.game.math;

/**
 * A 4×4 matrix in the layout OpenGL wants: sixteen floats, column major, so
 * {@code m[column * 4 + row]}.
 *
 * <p>Adapted from Larsons-Game-Engine's {@code graphics.Mat4}. The engine's
 * version deliberately looks down <b>+Z</b> so it agrees with its CPU
 * {@code EyeCamera}; this game has no CPU 3D path to agree with, so it uses the
 * textbook OpenGL conventions instead: eye space looks down −Z and
 * counter-clockwise faces are front faces.
 *
 * <p>Immutable; every operation returns a new matrix.
 */
public final class Mat4 {

    private final float[] m;

    private Mat4(float[] m) {
        this.m = m;
    }

    /** The matrix as OpenGL wants it — column major, sixteen floats. */
    public float[] columnMajor() {
        return m.clone();
    }

    /** Copy into {@code out} without allocating (for uniform uploads). */
    public void store(float[] out) {
        System.arraycopy(m, 0, out, 0, 16);
    }

    /** One element, by row and column. */
    public double at(int row, int col) {
        return m[col * 4 + row];
    }

    public static Mat4 identity() {
        float[] out = new float[16];
        out[0] = out[5] = out[10] = out[15] = 1;
        return new Mat4(out);
    }

    public static Mat4 translation(double x, double y, double z) {
        float[] out = new float[16];
        out[0] = out[5] = out[10] = out[15] = 1;
        out[12] = (float) x;
        out[13] = (float) y;
        out[14] = (float) z;
        return new Mat4(out);
    }

    public static Mat4 scale(double x, double y, double z) {
        float[] out = new float[16];
        out[0] = (float) x;
        out[5] = (float) y;
        out[10] = (float) z;
        out[15] = 1;
        return new Mat4(out);
    }

    /** Rotation about +Y by {@code radians} (counter-clockwise seen from above). */
    public static Mat4 rotationY(double radians) {
        float c = (float) Math.cos(radians), s = (float) Math.sin(radians);
        float[] out = new float[16];
        out[0] = c;
        out[2] = -s;
        out[5] = 1;
        out[8] = s;
        out[10] = c;
        out[15] = 1;
        return new Mat4(out);
    }

    /** Rotation about +X by {@code radians}. */
    public static Mat4 rotationX(double radians) {
        float c = (float) Math.cos(radians), s = (float) Math.sin(radians);
        float[] out = new float[16];
        out[0] = 1;
        out[5] = c;
        out[6] = s;
        out[9] = -s;
        out[10] = c;
        out[15] = 1;
        return new Mat4(out);
    }

    /** Rotation about +Z by {@code radians}. */
    public static Mat4 rotationZ(double radians) {
        float c = (float) Math.cos(radians), s = (float) Math.sin(radians);
        float[] out = new float[16];
        out[0] = c;
        out[1] = s;
        out[4] = -s;
        out[5] = c;
        out[10] = 1;
        out[15] = 1;
        return new Mat4(out);
    }

    /**
     * A standard OpenGL perspective projection.
     *
     * @param fovY   vertical field of view in radians
     * @param aspect width / height
     */
    public static Mat4 perspective(double fovY, double aspect, double near, double far) {
        double f = 1.0 / Math.tan(fovY / 2);
        float[] out = new float[16];
        out[0] = (float) (f / Math.max(1e-6, aspect));
        out[5] = (float) f;
        out[10] = (float) ((far + near) / (near - far));
        out[11] = -1;
        out[14] = (float) (2 * far * near / (near - far));
        return new Mat4(out);
    }

    /** An orthographic projection; for the UI, {@code (0,0)} at the top left. */
    public static Mat4 orthographic(double left, double right, double bottom, double top,
                                    double near, double far) {
        float[] out = new float[16];
        out[0] = (float) (2 / (right - left));
        out[5] = (float) (2 / (top - bottom));
        out[10] = (float) (-2 / (far - near));
        out[12] = (float) (-(right + left) / (right - left));
        out[13] = (float) (-(top + bottom) / (top - bottom));
        out[14] = (float) (-(far + near) / (far - near));
        out[15] = 1;
        return new Mat4(out);
    }

    /**
     * A view matrix from an eye position and an explicit orthonormal basis.
     *
     * <p>Taking the basis rather than a target and a world "up" is what keeps
     * the camera sound when it looks straight down: {@code lookAt} with up =
     * +Y degenerates at a 90° pitch, which is exactly the birds-eye view this
     * game needs. The orbit camera builds its right vector from the yaw alone,
     * which never degenerates.
     */
    public static Mat4 view(Vec3 eye, Vec3 right, Vec3 up, Vec3 forward) {
        float[] out = new float[16];
        out[0] = (float) right.x();
        out[4] = (float) right.y();
        out[8] = (float) right.z();
        out[1] = (float) up.x();
        out[5] = (float) up.y();
        out[9] = (float) up.z();
        out[2] = (float) -forward.x();
        out[6] = (float) -forward.y();
        out[10] = (float) -forward.z();
        out[12] = (float) -right.dot(eye);
        out[13] = (float) -up.dot(eye);
        out[14] = (float) forward.dot(eye);
        out[15] = 1;
        return new Mat4(out);
    }

    /** {@code this × o}: applying the result applies {@code o} first. */
    public Mat4 mul(Mat4 o) {
        float[] a = m, b = o.m, out = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) sum += a[k * 4 + row] * b[col * 4 + k];
                out[col * 4 + row] = sum;
            }
        }
        return new Mat4(out);
    }

    /** Transform a point (w = 1), dividing by w. */
    public Vec3 transformPoint(Vec3 p) {
        double x = m[0] * p.x() + m[4] * p.y() + m[8] * p.z() + m[12];
        double y = m[1] * p.x() + m[5] * p.y() + m[9] * p.z() + m[13];
        double z = m[2] * p.x() + m[6] * p.y() + m[10] * p.z() + m[14];
        double w = m[3] * p.x() + m[7] * p.y() + m[11] * p.z() + m[15];
        if (Math.abs(w) < 1e-12) w = 1e-12;
        return new Vec3(x / w, y / w, z / w);
    }

    /** Transform a direction (w = 0). */
    public Vec3 transformDirection(Vec3 d) {
        return new Vec3(
                m[0] * d.x() + m[4] * d.y() + m[8] * d.z(),
                m[1] * d.x() + m[5] * d.y() + m[9] * d.z(),
                m[2] * d.x() + m[6] * d.y() + m[10] * d.z());
    }
}
