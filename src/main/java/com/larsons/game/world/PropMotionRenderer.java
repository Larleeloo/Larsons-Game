package com.larsons.game.world;

import com.larsons.game.building.Building;
import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.building.BuildingModel;
import com.larsons.game.building.BuildingProfile;
import com.larsons.game.building.PropMotion;
import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Texture;
import com.larsons.game.math.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What moves on the things that stand about ({@link PropMotion}), drawn over
 * the scene after the light rays: the brew's bubbles, the lightning coil's
 * arcs, a fire's flicker and its embers, candle flames, chimney smoke, steam,
 * a fountain's spray, crystal glints, bees. Light is added ({@link
 * Batch#glow}); smoke, steam, water and bees are laid over what is behind
 * them. All of it is tested against the depth of what stands in front.
 *
 * <p>Only what is near the camera moves ({@value #REACH} metres, the nearest
 * {@value #MOST} things first): far off, none of it would show.
 */
final class PropMotionRenderer implements AutoCloseable {

    /** How far from the camera's aim things move, metres. */
    static final double REACH = 70;
    /** The most things moving at once. */
    static final int MOST = 48;

    private final Batch batch;
    private final Texture dot, star, puff;
    /** Each kind of building's moving parts, read off its model once. */
    private final Map<String, List<PropMotion.Source>> sources = new HashMap<>();

    PropMotionRenderer(Batch batch) {
        this.batch = batch;
        this.dot = Texture.upload(PixelData.of(dotPixels(32), 32, 32), false);
        this.star = Texture.upload(PixelData.of(starPixels(32), 32, 32), false);
        this.puff = Texture.upload(PixelData.of(puffPixels(64), 64, 64), false);
    }

    /** {@code p}'s moving parts (none: still), read the first time it is asked for. */
    List<PropMotion.Source> sources(BuildingProfile p) {
        List<PropMotion.Source> s = sources.get(p.key());
        if (s != null) return s;
        s = List.of();
        if (PropMotion.moves(p)) {
            try {
                BuildingModel m = BuildingModel.load(BuildingCatalog.get().root().resolve(p.key()));
                s = List.copyOf(PropMotion.of(p, m));
            } catch (Exception e) {
                System.err.println("[buildings] " + p.key() + " moving parts: " + e.getMessage());
            }
        }
        sources.put(p.key(), s);
        return s;
    }

    /** Where {@code b}'s own point (x right, y back, z up) is in the world. */
    static Vec3 at(Building b, double x, double y, double z) {
        double[] sc = b.profile().scale();
        Vec3 ax = b.axisX(), ay = b.axisY();
        return b.position().add(ax.scale(x * sc[0])).add(ay.scale(y * sc[1])).add(0, z * sc[2], 0);
    }

    /**
     * Draw it all, in a batch already begun: {@code aim} where the camera
     * looks, {@code right} and {@code up} its own, {@code lit} how light it is
     * (0 night, 1 day: smoke and water are seen by it), {@code wind} which way
     * smoke drifts (metres a second across the ground).
     */
    void draw(World world, Vec3 aim, Vec3 right, Vec3 up, double t, double lit, Vec3 wind) {
        record Near(Building b, double d) {}
        List<Near> near = new ArrayList<>();
        for (Building b : world.buildings()) {
            BuildingProfile p = b.profile();
            if (p.room() != null || !PropMotion.moves(p)) continue;
            double d = b.position().sub(aim).horizontalLength() - p.reach();
            if (d > REACH) continue;
            near.add(new Near(b, d));
        }
        near.sort((a, c) -> Double.compare(a.d(), c.d()));
        for (int i = 0; i < Math.min(MOST, near.size()); i++) {
            Building b = near.get(i).b();
            for (PropMotion.Source s : sources(b.profile())) draw(b, s, right, up, t, lit, wind);
        }
    }

    private void draw(Building b, PropMotion.Source s, Vec3 right, Vec3 up, double t, double lit, Vec3 wind) {
        double scale = b.profile().scale()[2];
        Vec3 c = at(b, s.x(), s.y(), s.z());
        double seed = b.id() * 7.31 + s.x() * 3.1 + s.y() * 5.7 + s.z();
        int rgb = colour(b, s);
        switch (s.kind()) {
            case BUBBLES -> bubbles(b, s, c, right, up, t, seed, rgb);
            case ARCS -> arcs(c, right, up, t, seed, scale);
            case FIRE -> fire(s, c, right, up, t, seed, scale);
            case EMBERS -> embers(s, c, right, up, t, seed, scale);
            case FLICKER -> flicker(c, right, up, t, seed, scale);
            case SMOKE -> smoke(s, c, right, up, t, seed, lit, wind, scale, false);
            case STEAM -> smoke(s, c, right, up, t, seed, lit, wind, scale, true);
            case SPRAY -> spray(b, s, c, right, up, t, seed, lit, scale);
            case GLINTS -> glints(b, s, right, up, t, seed);
            case BEES -> bees(c, right, up, t, seed, lit, scale);
        }
    }

    /** The part's colour as this one is coloured (its look), else as it comes. */
    private static int colour(Building b, PropMotion.Source s) {
        for (BuildingProfile.Part p : b.profile().parts()) {
            if (p.colour() == s.rgb()) return b.look().colour(p) & 0xFFFFFF;
        }
        return s.rgb();
    }

    // --- each kind ------------------------------------------------------------------------

    /** Bubbles of the brew rising off its top, swelling and bursting; now and then a wisp of steam. */
    private void bubbles(Building b, PropMotion.Source s, Vec3 c, Vec3 right, Vec3 up, double t, double seed, int rgb) {
        float[] bc = rgb(rgb), light = whiter(bc, 0.5f);
        double sc = b.profile().scale()[0];
        double rx = s.rx() * sc * 0.75, ry = s.ry() * sc * 0.75;
        int n = Math.max(4, Math.min(9, (int) Math.round((s.rx() + s.ry()) * 14)));
        double rise = Math.max(0.12, Math.min(0.32, (s.rx() + s.ry()) * 0.6));
        for (int k = 0; k < n; k++) {
            double period = 1.1 + 0.5 * hash(seed + k);
            double f = ((t + hash(seed * 3 + k) * period) / period) % 1.0;
            int cycle = (int) Math.floor((t + hash(seed * 3 + k) * period) / period);
            double ang = 2 * Math.PI * hash(seed + k * 13 + cycle * 0.37), r = Math.sqrt(hash(seed + k * 7 + cycle));
            Vec3 base = at(b, s.x() + Math.cos(ang) * r * rx / sc, s.y() + Math.sin(ang) * r * ry / sc, s.z());
            Vec3 p = base.add(0, f * rise, 0);
            double size = 0.025 + 0.035 * Math.sin(Math.PI * Math.min(1, f * 1.2));
            float a = (float) Math.sin(Math.PI * f);
            glow(dot, p, size * 2.2, right, up, bc, 0.45f * a);
            glow(dot, p, size, right, up, light, 0.9f * a);
        }
        // a wisp of steam off it
        for (int k = 0; k < 3; k++) {
            double f = ((t * 0.35 + k / 3.0 + hash(seed + k)) % 1.0);
            Vec3 p = c.add(right.scale(Math.sin(t * 0.8 + k * 2) * 0.08)).add(0, 0.1 + f * 0.7, 0);
            double size = 0.18 + f * 0.35;
            float a = (float) (0.16 * Math.sin(Math.PI * f));
            quad(puff, p, size, right, up, whiter(bc, 0.75f), a);
        }
    }

    /** Lightning: a few jagged arcs leaping off it, new ones every few hundredths of a second, and a flash. */
    private void arcs(Vec3 c, Vec3 right, Vec3 up, double t, double seed, double scale) {
        int tick = (int) Math.floor(t * 9 + seed);
        double since = t * 9 + seed - tick;
        boolean on = hash(seed + tick * 0.13) > 0.18;           // now and then a beat with none
        float[] blue = {0.62f, 0.78f, 1f}, white = {0.92f, 0.96f, 1f};
        double flash = on ? (1 - since) : 0;
        glow(dot, c, (0.9 + 0.4 * flash) * scale, right, up, blue, (float) (0.25 + 0.45 * flash));
        if (!on) return;
        int arcs = 2 + (int) (hash(seed + tick) * 3);
        for (int a = 0; a < arcs; a++) {
            double h = seed + tick * 1.7 + a * 11;
            // out and a little down, any way round
            double yaw = 2 * Math.PI * hash(h), drop = -0.2 - 0.9 * hash(h + 1);
            double len = (0.45 + 0.7 * hash(h + 2)) * scale;
            Vec3 dir = new Vec3(Math.cos(yaw), drop, Math.sin(yaw)).normalize(new Vec3(1, 0, 0));
            Vec3 side = dir.cross(new Vec3(0, 1, 0)).normalize(new Vec3(0, 0, 1));
            Vec3 prev = c;
            int steps = 7;
            for (int k = 1; k <= steps; k++) {
                double f = (double) k / steps;
                double jag = (hash(h + k * 3.3) - 0.5) * 0.22 * scale * Math.sin(Math.PI * f);
                double jagUp = (hash(h + k * 5.1) - 0.5) * 0.18 * scale * Math.sin(Math.PI * f);
                Vec3 p = c.add(dir.scale(len * f)).add(side.scale(jag)).add(0, jagUp, 0);
                // the segment as a string of beads of light
                for (int q = 0; q < 4; q++) {
                    Vec3 bead = prev.add(p.sub(prev).scale(q / 4.0));
                    float fade = (float) ((1 - since * 0.8) * (1 - 0.4 * f));
                    glow(dot, bead, 0.09 * scale, right, up, blue, 0.55f * fade);
                    glow(dot, bead, 0.035 * scale, right, up, white, 1.0f * fade);
                }
                prev = p;
            }
            glow(star, prev, 0.16 * scale, right, up, white, (float) (0.9 * (1 - since)));
        }
    }

    /** A fire's glow, flickering in its bands, over where it burns. */
    private void fire(PropMotion.Source s, Vec3 c, Vec3 right, Vec3 up, double t, double seed, double scale) {
        double f = 0.82 + 0.1 * Math.sin(t * 7.3 + seed) + 0.06 * Math.sin(t * 13.7 + seed * 2) + 0.04 * Math.sin(t * 23.1);
        double size = Math.max(0.35, Math.min(2.4, (s.rx() + s.ry()) * 2.2)) * scale;
        glow(dot, c.add(0, 0.05 * Math.sin(t * 5 + seed), 0), size * f, right, up, new float[]{1f, 0.55f, 0.2f},
                (float) (0.38 * f));
        glow(dot, c, size * 0.45 * f, right, up, new float[]{1f, 0.82f, 0.45f}, (float) (0.4 * f));
    }

    /** Sparks going up off an open fire, wandering, winking out. */
    private void embers(PropMotion.Source s, Vec3 c, Vec3 right, Vec3 up, double t, double seed, double scale) {
        int n = 7;
        double top = c.y() + (s.z() - s.low()) * 0.5 * scale;
        for (int k = 0; k < n; k++) {
            double period = 1.4 + hash(seed + k) * 1.2;
            double ph = (t + hash(seed * 5 + k) * period) / period;
            double f = ph % 1.0;
            int cycle = (int) Math.floor(ph);
            double ox = (hash(seed + k * 3 + cycle) - 0.5) * s.rx() * 1.6 * scale;
            double oz = (hash(seed + k * 9 + cycle) - 0.5) * s.ry() * 1.6 * scale;
            double sway = Math.sin(t * 3 + k * 1.9) * 0.08 * f;
            Vec3 p = new Vec3(c.x() + ox + sway, top + f * 1.1 * scale, c.z() + oz);
            float a = (float) ((1 - f) * Math.min(1, f * 6));
            glow(star, p, 0.06 * scale, right, up, new float[]{1f, 0.62f, 0.22f}, 0.9f * a);
        }
    }

    /** A small flame wavering: its glow swells and shrinks, its tip leans. */
    private void flicker(Vec3 c, Vec3 right, Vec3 up, double t, double seed, double scale) {
        double f = 0.85 + 0.09 * Math.sin(t * 9.1 + seed) + 0.06 * Math.sin(t * 15.7 + seed * 1.3);
        Vec3 tip = c.add(right.scale(0.008 * Math.sin(t * 6.3 + seed))).add(0, 0.01, 0);
        glow(dot, tip, 0.3 * f * scale, right, up, new float[]{1f, 0.7f, 0.3f}, (float) (0.28 * f));
        glow(dot, tip, 0.08 * f * scale, right, up, new float[]{1f, 0.92f, 0.65f}, (float) (0.7 * f));
    }

    /** Smoke (or steam) going up off it in puffs, swelling, drifting with the wind, thinning to nothing. */
    private void smoke(PropMotion.Source s, Vec3 c, Vec3 right, Vec3 up, double t, double seed, double lit, Vec3 wind,
                       double scale, boolean steam) {
        int n = steam ? 10 : 8;
        double life = steam ? 2.6 : 6.0, climb = steam ? 0.8 : 0.55;
        double width = Math.max(0.25, Math.min(1.4, (s.rx() + s.ry()))) * scale;
        float shade = (float) (0.35 + 0.65 * lit);
        float[] col = steam ? new float[]{0.94f * shade, 0.95f * shade, 0.96f * shade}
                : new float[]{0.42f * shade, 0.41f * shade, 0.4f * shade};
        for (int k = 0; k < n; k++) {
            double f = ((t / life) + (double) k / n + hash(seed + k) * 0.05) % 1.0;
            double age = f * life;
            Vec3 drift = wind.scale(age * (steam ? 0.5 : 0.8));
            double wob = Math.sin(t * 0.9 + k * 2.1) * 0.12 * f;
            Vec3 p = c.add(drift).add(right.scale(wob)).add(0, 0.12 + age * climb, 0);
            double size = width * (0.8 + f * (steam ? 1.6 : 2.6));
            float a = (float) ((steam ? 0.66 : 0.5) * Math.min(1, f * 5) * (1 - f));
            quad(puff, p, size, right, up, col, a);
        }
    }

    /** Water thrown up off its spout and falling back to the water, catching the light as it goes. */
    private void spray(Building b, PropMotion.Source s, Vec3 c, Vec3 right, Vec3 up, double t, double seed, double lit,
                       double scale) {
        double fall = Math.max(0.1, (s.z() - s.low()) * scale);
        double out = Math.max(0.12, Math.min(0.9, Math.min(s.rx(), s.ry()) * 0.45)) * scale;
        double g = 9.8;
        double vUp = 2.2;
        // how long a drop is in the air: up, then down past where it started to its foot
        double flight = (vUp + Math.sqrt(vUp * vUp + 2 * g * fall)) / g;
        float shade = (float) (0.55 + 0.45 * lit);
        float[] water = {0.9f * shade, 0.96f * shade, 1f * shade};
        int n = 40;
        for (int k = 0; k < n; k++) {
            double ph = t / flight + (double) k / n + hash(seed + k) * 0.03;
            double f = ph % 1.0;
            int cycle = (int) Math.floor(ph);
            double ang = 2 * Math.PI * ((double) k / n + hash(seed + cycle * 0.7 + k) * 0.08);
            double tt = f * flight;
            double vOut = out / flight * (0.7 + 0.6 * hash(seed + k * 2 + cycle));
            Vec3 p = c.add(Math.cos(ang) * vOut * tt, vUp * tt - 0.5 * g * tt * tt, Math.sin(ang) * vOut * tt);
            quad(dot, p, 0.065 * scale, right, up, water, 0.9f);
        }
        // glints on the water below
        for (int k = 0; k < 3; k++) {
            int cycle = (int) Math.floor(t * 1.7 + k * 0.33);
            double f = (t * 1.7 + k * 0.33) % 1.0;
            Vec3 p = at(b, s.x() + (hash(seed + cycle + k) - 0.5) * s.rx() * 1.6,
                    s.y() + (hash(seed * 2 + cycle + k) - 0.5) * s.ry() * 1.6, s.low());
            glow(star, p.add(0, 0.03, 0), 0.12 * scale, right, up, new float[]{1, 1, 1},
                    (float) (0.7 * Math.sin(Math.PI * f) * shade));
        }
    }

    /** Light catching on a crystal here and there: a star that swells and goes, somewhere new each time. */
    private void glints(Building b, PropMotion.Source s, Vec3 right, Vec3 up, double t, double seed) {
        for (int k = 0; k < 3; k++) {
            double period = 1.3 + hash(seed + k) * 0.9;
            double ph = (t + k * 0.5) / period;
            int cycle = (int) Math.floor(ph);
            double f = ph % 1.0;
            double h = seed + cycle * 1.31 + k * 7;
            Vec3 p = at(b, s.x() + (hash(h) - 0.5) * s.rx() * 1.6, s.y() + (hash(h + 1) - 0.5) * s.ry() * 1.6,
                    s.low() + (s.z() - s.low()) * (0.5 + hash(h + 2)));
            float a = (float) Math.pow(Math.sin(Math.PI * f), 3);
            glow(star, p, 0.22 * b.profile().scale()[2], right, up, new float[]{1, 1, 1}, 0.9f * a);
        }
    }

    /** Bees going round and round the hive, in loops, now high, now low. */
    private void bees(Vec3 c, Vec3 right, Vec3 up, double t, double seed, double lit, double scale) {
        float shade = (float) (0.25 + 0.5 * lit);
        for (int k = 0; k < 6; k++) {
            double sp = 1.6 + hash(seed + k) * 1.4, ph = hash(seed * 3 + k) * 6.28;
            double r = (0.35 + 0.45 * hash(seed + k * 5)) * scale;
            double a = t * sp + ph;
            Vec3 p = c.add(Math.cos(a) * r + 0.12 * Math.sin(a * 2.7), 0.25 * Math.sin(a * 1.3 + k) * scale,
                    Math.sin(a) * r * 0.8);
            quad(dot, p, 0.045 * scale, right, up, new float[]{0.22f * shade, 0.17f * shade, 0.05f * shade}, 0.95f);
            // its wings, a blur over it
            quad(dot, p.add(0, 0.015, 0), 0.05 * scale, right, up, new float[]{0.95f, 0.95f, 0.9f},
                    (float) (0.35 * (0.6 + 0.4 * Math.sin(t * 40 + k))));
        }
    }

    // --- drawing ----------------------------------------------------------------------------

    private void glow(Texture tex, Vec3 at, double size, Vec3 right, Vec3 up, float[] c, float k) {
        if (k <= 0.003) return;
        batch.glow(tex, at.sub(right.scale(size / 2)).add(up.scale(size / 2)), right.scale(size), up.scale(-size),
                c[0], c[1], c[2], k);
    }

    private void quad(Texture tex, Vec3 at, double size, Vec3 right, Vec3 up, float[] c, float a) {
        if (a <= 0.003) return;
        batch.quad(tex, at.sub(right.scale(size / 2)).add(up.scale(size / 2)), right.scale(size), up.scale(-size),
                0, 0, 1, 1, c[0], c[1], c[2], a);
    }

    /** A number in 0..1 from {@code x}, the same each time. */
    static double hash(double x) {
        double s = Math.sin(x * 12.9898 + 78.233) * 43758.5453;
        return s - Math.floor(s);
    }

    private static float[] rgb(int rgb) {
        return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f};
    }

    private static float[] whiter(float[] c, float k) {
        return new float[]{c[0] + (1 - c[0]) * k, c[1] + (1 - c[1]) * k, c[2] + (1 - c[2]) * k};
    }

    // --- the textures -----------------------------------------------------------------------

    /** A soft round dot, white: a bubble, a bead of lightning, a drop of water. */
    static int[] dotPixels(int n) {
        int[] argb = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r = Math.sqrt(dx * dx + dy * dy);
                double a = r >= 1 ? 0 : Math.pow(1 - r, 1.6);
                argb[y * n + x] = ((int) Math.round(Math.min(1, a * 1.4) * 255) << 24) | 0xFFFFFF;
            }
        }
        return argb;
    }

    /** A four-pointed star of light, white: a glint, an ember. */
    static int[] starPixels(int n) {
        int[] argb = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = Math.abs((x + 0.5) / n * 2 - 1), dy = Math.abs((y + 0.5) / n * 2 - 1);
                double arms = Math.max(Math.max(0, 1 - dx) * Math.max(0, 1 - dy * 9),
                        Math.max(0, 1 - dy) * Math.max(0, 1 - dx * 9));
                double core = Math.max(0, 1 - Math.sqrt(dx * dx + dy * dy) * 2.2);
                double a = Math.min(1, Math.pow(arms, 1.5) + core);
                argb[y * n + x] = ((int) Math.round(a * 255) << 24) | 0xFFFFFF;
            }
        }
        return argb;
    }

    /** A puff of smoke, white, its edge lumpy and soft. */
    static int[] puffPixels(int n) {
        int[] argb = new int[n * n];
        double[][] lumps = {{-0.3, -0.2, 0.55}, {0.3, -0.15, 0.5}, {0, 0.25, 0.6}, {-0.1, -0.35, 0.4}, {0.25, 0.3, 0.4}};
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double px = (x + 0.5) / n * 2 - 1, py = (y + 0.5) / n * 2 - 1;
                double a = 0;
                for (double[] l : lumps) {
                    double d = Math.hypot(px - l[0], py - l[1]) / l[2];
                    a = Math.max(a, d >= 1 ? 0 : Math.pow(1 - d * d, 1.5));
                }
                argb[y * n + x] = ((int) Math.round(a * 255) << 24) | 0xFFFFFF;
            }
        }
        return argb;
    }

    @Override
    public void close() {
        dot.close();
        star.close();
        puff.close();
    }
}
