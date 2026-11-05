package com.larsons.game.world;

import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.Texture;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;

/**
 * The weather falling and blowing round the camera ({@link Weather.Now}):
 * rain in slanting streaks (heavier and more slanted in a thunderstorm),
 * snow drifting down in flakes, a sandstorm's sand streaming along the wind,
 * the wind's dust and leaves skittering by. Fog has none of its own - it is
 * the sky's fog and the mist ({@link Weathering}).
 *
 * <p>Each mote has its own place in a box of air round the camera's target,
 * moving with the weather from it and wrapped round the box - fixed in the
 * world, so they do not slide as the camera turns - and as many are drawn
 * as the weather is heavy. Lit as the scene is, tested against what stands
 * (not written into the depth).
 */
final class WeatherRenderer {

    /** The box of air round the camera's target: half its width, its height, metres. */
    static final double HALF = 10, TALL = 8;
    /** Motes nearer the eye than this are left out: so near, they would be blots across the view. */
    static final double NEAR = 3;
    /** The most motes of each weather at its heaviest. */
    static final int RAIN = 2000, SNOW = 1500, SAND = 2400, DUST = 360;

    private Texture white;

    /** One frame of the weather {@code now}, at world time {@code time}, in the {@code light} of the hour. */
    void draw(Batch batch, Weather.Now now, OrbitCamera camera, Mat4 viewProj, float[] fog, float fogFar,
              DayCycle.Daylight light, double time) {
        Weather.Kind kind = now.kind();
        double level = now.level();
        if (kind == Weather.Kind.CLEAR || kind == Weather.Kind.FOG || level < 0.01) return;
        if (white == null) white = Texture.white();
        Vec3 target = camera.target(), eye = camera.eye();
        float[] a = light.ambient();
        float lit = (float) Math.min(1.2, 0.35 + 0.75 * (a[0] + a[1] + a[2]) / 3 + now.flash() * 0.6);
        batch.begin(viewProj, fog, 0, fogFar);
        Vec3 wind = Scenery.WIND;
        switch (kind) {
            case RAIN, THUNDERSTORM -> {
                boolean storm = kind == Weather.Kind.THUNDERSTORM;
                Vec3 v = new Vec3(wind.x() * (storm ? 4.5 : 2.2), -12, wind.z() * (storm ? 4.5 : 2.2));
                int n = (int) (RAIN * level * (storm ? 1.2 : 1));
                streaks(batch, n, 0, v, storm ? 0.65 : 0.5, 0.032, target, eye, time,
                        0.72f * lit, 0.8f * lit, 0.92f * lit, (float) (0.38 + 0.27 * level));
            }
            case SNOW -> flakes(batch, (int) (SNOW * level), target, eye, camera, time, lit);
            case SANDSTORM -> {
                Vec3 v = new Vec3(wind.x() * 11, -0.8, wind.z() * 11);
                streaks(batch, (int) (SAND * level), 7, v, 0.45, 0.04, target, eye, time,
                        0.86f * lit, 0.7f * lit, 0.46f * lit, (float) (0.3 + 0.3 * level));
            }
            case WIND -> {
                Vec3 v = new Vec3(wind.x() * 7, -0.2, wind.z() * 7);
                streaks(batch, (int) (DUST * level), 13, v, 0.16, 0.07, target, eye, time,
                        0.62f * lit, 0.6f * lit, 0.38f * lit, 0.85f);
            }
            default -> { }
        }
        batch.end();
    }

    /**
     * {@code n} motes moving at {@code v}, each a streak {@code length}
     * metres long the way it moves and {@code width} across, turned to the
     * eye; {@code salt} keeps one weather's motes from another's places.
     */
    private void streaks(Batch batch, int n, int salt, Vec3 v, double length, double width, Vec3 target, Vec3 eye,
                         double time, float r, float g, float b, float alpha) {
        double speed = v.length();
        Vec3 dir = v.scale(1 / speed);
        for (int i = 0; i < n; i++) {
            Vec3 p = mote(i, salt, v, target, time);
            Vec3 toEye = eye.sub(p);
            double d = toEye.length();
            if (d < NEAR) continue;
            Vec3 across = dir.cross(toEye.scale(1 / d));
            double al = across.length();
            if (al < 1e-4) continue;
            Vec3 side = across.scale(width / al);
            Vec3 tail = dir.scale(-length);
            Vec3 tl = p.add(tail).sub(side.scale(0.5));
            batch.quad(white, tl, side, tail.scale(-1), 0, 0, 1, 1, r, g, b, alpha);
        }
    }

    /** Snow: flakes drifting down, swaying as they fall, each a little square turned to the camera. */
    private void flakes(Batch batch, int n, Vec3 target, Vec3 eye, OrbitCamera camera, double time, float lit) {
        Vec3 right = camera.right(), up = camera.up();
        Vec3 wind = Scenery.WIND;
        Vec3 v = new Vec3(wind.x() * 1.1, -1.3, wind.z() * 1.1);
        for (int i = 0; i < n; i++) {
            Vec3 p = mote(i, 29, v, target, time);
            double sway = Math.sin(time * 1.3 + i * 0.77) * 0.35;
            p = p.add(right.scale(sway));
            if (p.sub(eye).length() < NEAR) continue;
            double s = 0.06 + 0.04 * ((i * 7919) % 5) / 4.0;
            Vec3 tl = p.sub(right.scale(s / 2)).add(up.scale(s / 2));
            batch.quad(white, tl, right.scale(s), up.scale(-s), 0, 0, 1, 1, 0.95f * lit, 0.97f * lit, lit, 0.9f);
        }
    }

    private static double wrap(double a, double m) {
        double r = a % m;
        return r < 0 ? r + m : r;
    }

    /** Where mote {@code i} is at {@code time}: its own place, moved at {@code v}, wrapped round the box. */
    static Vec3 mote(int i, int salt, Vec3 v, Vec3 target, double time) {
        long h = (i + 1L) * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        double bx = ((h >>> 11) & 0xFFFF) / 65536.0, by = ((h >>> 27) & 0xFFFF) / 65536.0,
                bz = ((h >>> 43) & 0xFFFF) / 65536.0;
        double x0 = target.x() - HALF, y0 = target.y() - 1.5, z0 = target.z() - HALF;
        double x = x0 + wrap(bx * 2 * HALF + v.x() * time - x0, 2 * HALF);
        double y = y0 + wrap(by * TALL + v.y() * time - y0, TALL);
        double z = z0 + wrap(bz * 2 * HALF + v.z() * time - z0, 2 * HALF);
        return new Vec3(x, y, z);
    }
}
