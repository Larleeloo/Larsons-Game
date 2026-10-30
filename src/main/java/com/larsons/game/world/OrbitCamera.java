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
 *
 * <p>On top of the angle asked for, the game can raise or lower it by itself
 * ({@link #setAutoHeight}: higher and farther back on a peak for the view,
 * lower in a hollow) - which may move the camera into another of the three
 * sprite zones - and keeps the eye out of the hills ({@link #keepAbove}).
 * Both ease in and out slowly; {@link #pitch()} and {@link #distance()} are
 * the camera's as it stands, with them.
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
    /** The automatic height: pitch added (radians) and distance scaled - as eased, and as wanted. */
    private double autoPitch, wantAutoPitch, autoZoom = 1, wantAutoZoom = 1;
    /** Pitch added to keep the eye out of the ground. */
    private double lift;
    private Vec3 target = new Vec3(0, 0.9, 0);
    private final double fovY = Math.toRadians(50);
    private double maxDistance = MAX_DISTANCE;

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
        double slow = 1 - Math.exp(-dt * 1.4);
        autoPitch += (wantAutoPitch - autoPitch) * slow;
        autoZoom += (wantAutoZoom - autoZoom) * slow;
    }

    /**
     * Raise the camera's angle by {@code pitch} radians (negative: lower it)
     * and move it {@code zoom} times as far back, easing there over a second
     * or two; 0 and 1 to go back to the angle asked for.
     */
    public void setAutoHeight(double pitch, double zoom) {
        wantAutoPitch = pitch;
        wantAutoZoom = Math.max(0.5, Math.min(2, zoom));
    }

    /** The automatic height's pitch as it stands, radians. */
    public double autoPitch() { return autoPitch; }

    /**
     * Keep the eye at least {@code clearance} metres over {@code terrain},
     * and the ground from between it and her: where a hill or a cliff behind
     * her would take it into the ground or hide her, the camera swings up
     * over it (easing, and back down once clear).
     */
    public void keepAbove(Terrain terrain, double clearance) {
        if (terrain.flat()) {
            lift *= 0.8;
            return;
        }
        double save = lift, want = 0;
        for (int i = 0; i < 30; i++) {
            lift = want;
            if (clear(terrain, clearance) || pitch() >= MAX_PITCH) break;
            want += Math.toRadians(3);
        }
        lift = save + (want - save) * (want > save ? 0.35 : 0.08);
    }

    /** Whether the eye is out of the ground and nothing of it stands between the eye and her. */
    private boolean clear(Terrain terrain, double clearance) {
        Vec3 e = eye();
        for (int k = 1; k <= 16; k++) {
            double f = k / 16.0;
            Vec3 at = target.add(e.sub(target).scale(f));
            if (at.y() < terrain.height(at.x(), at.z()) + clearance * f) return false;
        }
        return true;
    }

    /** Jump straight to the target without easing (scene start). */
    public void snapTo(Vec3 follow) {
        target = follow;
        yaw = wantYaw;
        pitch = wantPitch;
        distance = wantDistance;
        autoPitch = wantAutoPitch;
        autoZoom = wantAutoZoom;
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

    /** The camera's angle as it stands: the one asked for, raised or lowered by the automatic height. */
    public double pitch() { return clampPitch(pitch + autoPitch + lift); }

    /** How far back it stands: the zoom asked for, times the automatic height's. */
    public double distance() { return distance * autoZoom; }

    /** The angle asked for (keys, presets, the mouse), without the automatic height. */
    public double basePitch() { return pitch; }

    public Vec3 target() { return target; }

    public double fovY() { return fovY; }

    public Vec3 eye() {
        double p = pitch(), d = distance(), cp = Math.cos(p);
        return target.add(Math.sin(yaw) * cp * d, Math.sin(p) * d, Math.cos(yaw) * cp * d);
    }

    /** Unit vector from the eye toward the target. */
    public Vec3 forward() {
        double p = pitch(), cp = Math.cos(p);
        return new Vec3(-Math.sin(yaw) * cp, -Math.sin(p), -Math.cos(yaw) * cp);
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
        return Elevation.forAngle(Math.toDegrees(pitch()));
    }

    private static double clampPitch(double p) {
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, p));
    }

    private double clampDistance(double d) {
        return Math.max(MIN_DISTANCE, Math.min(maxDistance, d));
    }

    /** Let it zoom out as far as {@code metres} (the level editor's view; {@link #MAX_DISTANCE} otherwise). */
    public void setMaxDistance(double metres) {
        maxDistance = Math.max(MIN_DISTANCE, metres);
        wantDistance = clampDistance(wantDistance);
    }

    private static double shortestAngle(double a) {
        a = (a + Math.PI) % (Math.PI * 2);
        if (a < 0) a += Math.PI * 2;
        return a - Math.PI;
    }
}
