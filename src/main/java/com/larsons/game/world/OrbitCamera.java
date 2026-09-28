package com.larsons.game.world;

import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Elevation;

/**
 * The third-person camera: orbits a target (the player's pivot) at a yaw, a
 * pitch from 0° (level with the character) to 90° (straight down, birds-eye),
 * and a zoom distance.
 *
 * <p>The pitch is what picks the character's sprite set — see
 * {@link Elevation}: under 34° the side renders, 34°–75° the 45° renders, 75°
 * and up the top-down renders. The three {@link #PRESET_PITCHES} sit in the
 * middle of those zones.
 *
 * <p>Everything the player asks for is a <em>desired</em> value the camera
 * eases toward, so a preset or a zoom glides rather than cuts, while the
 * mouse drag drives it directly.
 */
public final class OrbitCamera {

    public static final double MIN_DISTANCE = 1.6;
    public static final double MAX_DISTANCE = 45;
    public static final double MIN_PITCH = 0;
    public static final double MAX_PITCH = Math.PI / 2;

    /** Tab cycles these: the middle of the side, 45° and top-down zones. */
    public static final double[] PRESET_PITCHES = {
            Math.toRadians(12), Math.toRadians(45), Math.toRadians(90)};

    private double yaw, pitch, distance;
    private double wantYaw, wantPitch, wantDistance;
    private Vec3 target = new Vec3(0, 0.9, 0);
    private final double fovY = Math.toRadians(50);

    public OrbitCamera(double yaw, double pitch, double distance) {
        this.yaw = wantYaw = yaw;
        this.pitch = wantPitch = clampPitch(pitch);
        this.distance = wantDistance = clampDistance(distance);
    }

    /** Ease toward the desired angles and zoom. */
    public void update(double dt, Vec3 follow) {
        double k = 1 - Math.exp(-dt * 12);
        yaw += shortestAngle(wantYaw - yaw) * k;
        pitch += (wantPitch - pitch) * k;
        // Zoom eases in log space, so zooming feels the same near and far.
        distance = Math.exp(Math.log(distance) + (Math.log(wantDistance) - Math.log(distance)) * k);
        target = target.lerp(follow, 1 - Math.exp(-dt * 18));
    }

    /** Jump straight to the target without easing (scene start). */
    public void snapTo(Vec3 follow) {
        target = follow;
        yaw = wantYaw;
        pitch = wantPitch;
        distance = wantDistance;
    }

    /** Orbit directly (mouse drag): radians of yaw and pitch. */
    public void rotate(double dYaw, double dPitch) {
        wantYaw += dYaw;
        wantPitch = clampPitch(wantPitch + dPitch);
        yaw += dYaw;
        pitch = clampPitch(pitch + dPitch);
    }

    /** Zoom by wheel notches; positive zooms in. */
    public void zoom(double notches) {
        wantDistance = clampDistance(wantDistance * Math.pow(0.88, notches));
    }

    public void setDesiredPitch(double radians) {
        wantPitch = clampPitch(radians);
    }

    public void setDesiredYaw(double radians) {
        wantYaw = radians;
    }

    public void setDesiredDistance(double metres) {
        wantDistance = clampDistance(metres);
    }

    /** Move to the next of the three height presets, from wherever the camera is. */
    public int cyclePreset() {
        double current = wantPitch;
        for (int i = 0; i < PRESET_PITCHES.length; i++) {
            if (PRESET_PITCHES[i] > current + 1e-3) {
                wantPitch = PRESET_PITCHES[i];
                return i;
            }
        }
        wantPitch = PRESET_PITCHES[0];
        return 0;
    }

    public double yaw() { return yaw; }

    public double pitch() { return pitch; }

    public double distance() { return distance; }

    public Vec3 target() { return target; }

    public double fovY() { return fovY; }

    public Vec3 eye() {
        double cp = Math.cos(pitch);
        return target.add(Math.sin(yaw) * cp * distance, Math.sin(pitch) * distance,
                Math.cos(yaw) * cp * distance);
    }

    /** Unit vector from the eye toward the target. */
    public Vec3 forward() {
        double cp = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cp, -Math.sin(pitch), -Math.cos(yaw) * cp);
    }

    /**
     * Screen right. Built from the yaw alone, so it is defined even looking
     * straight down — which is why {@link Mat4#view} takes a basis rather than
     * an up vector.
     */
    public Vec3 right() {
        return new Vec3(Math.cos(yaw), 0, -Math.sin(yaw));
    }

    public Vec3 up() {
        return right().cross(forward());
    }

    /** Forward flattened onto the ground — what W walks along. */
    public Vec3 groundForward() {
        return new Vec3(-Math.sin(yaw), 0, -Math.cos(yaw));
    }

    public Mat4 view() {
        return Mat4.view(eye(), right(), up(), forward());
    }

    public Mat4 projection(double aspect) {
        return Mat4.perspective(fovY, aspect, 0.05, 600);
    }

    public Mat4 viewProjection(double aspect) {
        return projection(aspect).mul(view());
    }

    /** The zone the camera is in, as seen from the orbit target. */
    public Elevation elevation() {
        return Elevation.forAngle(Math.toDegrees(pitch));
    }

    private static double clampPitch(double p) {
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, p));
    }

    private static double clampDistance(double d) {
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, d));
    }

    private static double shortestAngle(double a) {
        a = (a + Math.PI) % (Math.PI * 2);
        if (a < 0) a += Math.PI * 2;
        return a - Math.PI;
    }
}
