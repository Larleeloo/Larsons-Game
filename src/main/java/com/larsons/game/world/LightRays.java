package com.larsons.game.world;

import com.larsons.game.gfx.PixelData;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SceneryProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Shafts of sunlight slanting down through the trees and the mist while the
 * sun is low - in the morning and the evening ({@link DayCycle.Daylight#rays}):
 * a few under every tree's crown, falling the way the light falls, and those
 * placed in a level ({@link World.RaySpot}). Each shaft is a ribbon of light
 * turned to face the camera along its length, brightest down its middle in
 * bands as the pixel art is, fading in under the leaves and out on the
 * ground, and slowly breathing brighter and dimmer.
 *
 * <p>Pure data: {@link #shafts} says where they are; {@code WorldRenderer}
 * draws them as light added to the frame.
 */
public final class LightRays {

    /** One shaft: from {@code top} down to {@code bottom}, {@code width} metres across, {@code strength} bright. */
    public record Shaft(Vec3 top, Vec3 bottom, double width, double strength) {}

    /** The most shafts drawn a frame. */
    public static final int MAX = 160;
    /** How far from the eye a tree's shafts are drawn, metres. */
    public static final double RANGE = 45;

    private LightRays() {}

    /**
     * The shafts in {@code world} at world time {@code time} as {@code light}
     * has the sun, seen from {@code eye}: none at all while it is too high
     * or down.
     */
    public static List<Shaft> shafts(World world, DayCycle.Daylight light, Vec3 eye, double time) {
        List<Shaft> out = new ArrayList<>();
        double strength = light.rays() * (0.55 + 0.9 * light.mist());
        if (strength <= 0.01 || light.sun().y() <= 0.02) return out;
        Vec3 down = light.sun().scale(-1);        // the way the light falls
        for (Scenery.Placed p : world.scenery().placed()) {
            if (out.size() >= MAX) break;
            if (p.kind() != SceneryProfile.Kind.TREE) continue;
            if (Math.abs(p.x() - eye.x()) > RANGE || Math.abs(p.z() - eye.z()) > RANGE) continue;
            SceneryProfile prof = p.profile();
            double crown = prof.frameWorldHeight() * 0.72, reach = prof.frameWorldSize() * 0.3;
            long seed = Double.doubleToLongBits(p.x() * 31.7 + p.z() * 7.3);
            for (int k = 0; k < 2; k++) {
                double h1 = hash(seed + k * 101), h2 = hash(seed + k * 211 + 7), h3 = hash(seed + k * 331 + 13);
                if (h3 < 0.35) continue;
                double a = h1 * Math.PI * 2, r = reach * (0.3 + 0.7 * h2);
                Vec3 top = new Vec3(p.x() + Math.cos(a) * r, p.y() + crown * (0.85 + 0.15 * h3),
                        p.z() + Math.sin(a) * r);
                out.add(shaft(world, top, down, 0.25 + 0.45 * h2, strength * breath(time, h1) * 0.55));
            }
        }
        for (World.RaySpot s : world.raySpots()) {
            for (int k = 0; k < s.count() && out.size() < MAX; k++) {
                double h1 = hash(k * 977L + (long) (s.at().x() * 13)), h2 = hash(k * 389L + (long) (s.at().z() * 17));
                double a = h1 * Math.PI * 2, r = s.spread() * Math.sqrt(h2);
                Vec3 foot = s.at().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                Vec3 top = foot.add(down.scale(-s.height() / Math.max(0.15, -down.y())));
                out.add(shaft(world, top, down, 0.3 + 0.5 * h2, light.rays() * s.strength() * breath(time, h1)));
            }
        }
        return out;
    }

    /** A shaft from {@code top} down along {@code down} to the ground. */
    private static Shaft shaft(World world, Vec3 top, Vec3 down, double width, double strength) {
        double drop = top.y() - world.terrain().height(top.x(), top.z());
        double len = Math.max(0.5, drop / Math.max(0.15, -down.y()));
        Vec3 bottom = top.add(down.scale(len));
        bottom = new Vec3(bottom.x(), world.terrain().height(bottom.x(), bottom.z()), bottom.z());
        return new Shaft(top, bottom, width, strength);
    }

    /** How bright a shaft is at {@code time}: breathing slowly, each on its own beat. */
    static double breath(double time, double phase) {
        return 0.7 + 0.3 * Math.sin(time * (0.35 + phase * 0.3) + phase * 40);
    }

    static double hash(long n) {
        long h = n * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }

    /**
     * The shaft's picture: white light, brightest down its middle and fading
     * to its edges, in under the leaves at its top and out over the ground
     * at its foot - all in bands, as the pixel art is shaded.
     */
    public static PixelData pixels() {
        int w = 16, h = 64;
        int[] argb = new int[w * h];
        for (int y = 0; y < h; y++) {
            double v = (y + 0.5) / h;
            double along = Math.min(1, Math.min(v / 0.25, (1 - v) / 0.4));
            for (int x = 0; x < w; x++) {
                double u = Math.abs((x + 0.5) / w * 2 - 1);
                double across = u < 0.25 ? 1 : u < 0.55 ? 0.6 : u < 0.85 ? 0.3 : 0;
                double a = Math.floor(across * Math.max(0, along) * 5 + 0.5) / 5;
                argb[y * w + x] = ((int) Math.round(a * 255) << 24) | 0xFFFFFF;
            }
        }
        return PixelData.of(argb, w, h);
    }
}
