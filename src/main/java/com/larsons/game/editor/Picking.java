package com.larsons.game.editor;

import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Terrain;

/**
 * What the pointer is over: the ray from the camera through it, where that
 * ray meets the ground, which object it passes through - and, the other way,
 * where a spot in the world is on the screen.
 *
 * <p>Pure maths on the {@link OrbitCamera}; no GL.
 */
public final class Picking {

    /** How far out the ground is looked for along a ray, metres. */
    public static final double REACH = 800;

    private Picking() {}

    /** A ray: where it starts and the way it goes (a unit vector). */
    public record Ray(Vec3 from, Vec3 dir) {

        public Vec3 at(double t) {
            return from.add(dir.scale(t));
        }
    }

    /**
     * The ray from {@code camera} through the point {@code (px, py)} of a
     * view {@code width} by {@code height} (pixels from its top-left).
     */
    public static Ray ray(OrbitCamera camera, double px, double py, double width, double height) {
        double aspect = width / Math.max(1, height);
        double tanY = Math.tan(camera.fovY() / 2);
        double nx = px / Math.max(1, width) * 2 - 1, ny = 1 - py / Math.max(1, height) * 2;
        Vec3 dir = camera.forward().add(camera.right().scale(nx * tanY * aspect)).add(camera.up().scale(ny * tanY));
        return new Ray(camera.eye(), dir.normalize(camera.forward()));
    }

    /**
     * Where {@code ray} first meets {@code terrain}'s ground (the flat ground
     * round it too), or null if it does not within {@link #REACH}.
     */
    public static Vec3 ground(Ray ray, Terrain terrain) {
        double step = 0.25;
        double prev = 0;
        if (ray.from().y() <= terrain.height(ray.from().x(), ray.from().z())) return null;
        for (double t = step; t <= REACH; t += step) {
            Vec3 p = ray.at(t);
            if (p.y() <= terrain.height(p.x(), p.z())) {
                double lo = prev, hi = t;
                for (int i = 0; i < 20; i++) {
                    double mid = (lo + hi) / 2;
                    Vec3 m = ray.at(mid);
                    if (m.y() <= terrain.height(m.x(), m.z())) hi = mid;
                    else lo = mid;
                }
                Vec3 hit = ray.at(hi);
                return new Vec3(hit.x(), terrain.height(hit.x(), hit.z()), hit.z());
            }
            prev = t;
            // farther out, longer steps (the ground there is a pixel or two)
            if (t > 60) step = 0.5;
            if (t > 200) step = 1;
        }
        return null;
    }

    /**
     * How far along {@code ray} it passes into the upright cylinder of
     * {@code radius} round {@code (x, z)}, from {@code y0} up to {@code y1} -
     * or -1 when it misses it.
     */
    public static double cylinder(Ray ray, double x, double z, double radius, double y0, double y1) {
        Vec3 o = ray.from(), d = ray.dir();
        double ox = o.x() - x, oz = o.z() - z;
        double a = d.x() * d.x() + d.z() * d.z();
        double tIn, tOut;
        if (a < 1e-12) {
            if (ox * ox + oz * oz > radius * radius) return -1;
            tIn = -Double.MAX_VALUE;
            tOut = Double.MAX_VALUE;
        } else {
            double b = 2 * (ox * d.x() + oz * d.z()), c = ox * ox + oz * oz - radius * radius;
            double disc = b * b - 4 * a * c;
            if (disc < 0) return -1;
            double s = Math.sqrt(disc);
            tIn = (-b - s) / (2 * a);
            tOut = (-b + s) / (2 * a);
        }
        // and between its bottom and its top
        if (Math.abs(d.y()) < 1e-12) {
            if (o.y() < y0 || o.y() > y1) return -1;
        } else {
            double ta = (y0 - o.y()) / d.y(), tb = (y1 - o.y()) / d.y();
            tIn = Math.max(tIn, Math.min(ta, tb));
            tOut = Math.min(tOut, Math.max(ta, tb));
        }
        if (tOut < tIn || tOut < 0) return -1;
        return Math.max(0, tIn);
    }

    /**
     * Where {@code p} is on a screen {@code width} by {@code height} through
     * {@code viewProj}: pixels from its top-left, and whether it is in front
     * of the camera at all ({@code z} 1) or behind it (0).
     */
    public static Vec3 screen(Mat4 viewProj, Vec3 p, double width, double height) {
        double x = viewProj.at(0, 0) * p.x() + viewProj.at(0, 1) * p.y() + viewProj.at(0, 2) * p.z() + viewProj.at(0, 3);
        double y = viewProj.at(1, 0) * p.x() + viewProj.at(1, 1) * p.y() + viewProj.at(1, 2) * p.z() + viewProj.at(1, 3);
        double w = viewProj.at(3, 0) * p.x() + viewProj.at(3, 1) * p.y() + viewProj.at(3, 2) * p.z() + viewProj.at(3, 3);
        if (w <= 1e-6) return new Vec3(0, 0, 0);
        return new Vec3((x / w * 0.5 + 0.5) * width, (0.5 - y / w * 0.5) * height, 1);
    }
}
