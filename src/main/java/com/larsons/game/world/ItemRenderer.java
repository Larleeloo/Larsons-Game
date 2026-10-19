package com.larsons.game.world;

import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Texture;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.ItemSprites;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.SheetTexture;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.SpriteView;

import java.util.HashMap;
import java.util.Map;

/**
 * How an item lying in the world is drawn - {@link WorldRenderer} calls it
 * in two of its passes:
 *
 * <ul>
 *   <li><b>On the ground</b> ({@link #ground}, the shadows pass): a pool of
 *       its rarity's light under it.</li>
 *   <li><b>The item</b> ({@link #draw}, the sprites pass, where it falls far
 *       to near): its turning sprite ({@link ItemSprites}) at the frame its
 *       spin has reached, from the height the camera sees it from, in its
 *       weapon tier's colours; round it the halo of its rarity - a soft glow
 *       and a brighter one hugging its outline; a rare border, if it has one,
 *       as a crisp outline in the border's colour with what comes off it
 *       (Venom's lime drips, Mystic's smoky purple puffs, Bonded's pink
 *       hearts); and, when it came out of a chest, a Borderlands beam of light
 *       in its rarity's colour round it and up into the air above it,
 *       pulsing, with sparks rising in it.</li>
 * </ul>
 *
 * <p>The halos, the beam and the sparks are light ({@link Batch#glow}): they
 * add to what is behind them rather than cover it. An item with no turning
 * sprites (or whose sheet is still loading) is drawn from its icon, in the
 * same light.
 */
final class ItemRenderer implements AutoCloseable {

    /** The beam: how tall (metres from the ground), how wide, how fast it pulses (per second), how long it takes to come up once the item has landed (seconds). */
    static final double BEAM_HEIGHT = 3.6, BEAM_WIDTH = 0.9, BEAM_HZ = 0.8, BEAM_RISE = 0.45;
    /** Sparks rising in a beam. */
    static final int SPARKS = 9;
    /** The soft halo's width, in item lengths, and its strength. */
    static final double HALO_SIZE = 1.25, HALO = 0.30;
    /** The halo hugging the outline: how far out each ring of it is (texels) and how strong. */
    private static final double[] OUTLINE_GLOW = {1.5, 3.0}, OUTLINE_GLOW_STRENGTH = {0.11, 0.05};
    /** The eight ways out from an outline, unit steps. */
    private static final double[][] AROUND = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {0.7071, 0.7071}, {-0.7071, 0.7071}, {0.7071, -0.7071}, {-0.7071, -0.7071}};
    /** Motes a border gives off at a time. */
    static final int MOTES = 6;

    private final Batch batch;
    private final SpriteLibrary sprites;
    private final ItemSprites items;
    private final Texture beam, dot, star, heart;
    /** The last sheet each item was drawn from: drawn again while the one it wants now loads. */
    private final Map<String, SheetTexture> shown = new HashMap<>();

    ItemRenderer(Batch batch, SpriteLibrary sprites, ItemSprites items) {
        this.batch = batch;
        this.sprites = sprites;
        this.items = items;
        this.beam = Texture.upload(PixelData.of(beamPixels(32, 128), 32, 128), false);
        this.dot = Texture.upload(PixelData.of(dotPixels(32), 32, 32), false);
        this.star = Texture.upload(PixelData.of(starPixels(32), 32, 32), false);
        this.heart = Texture.upload(PixelData.of(heartPixels(32), 32, 32), false);
    }

    // --- the passes --------------------------------------------------------------------

    /** The shadows pass: the pool of its rarity's light on the ground under it - bright and pulsing in a beam. */
    void ground(World.GroundItem g, double t) {
        if (!g.out(t)) return;
        Vec3 c = g.centre(t);
        float[] rc = rgb(g.item.rarity().rgb());
        double b = beamStrength(g, t), p = pulse(t, g.phase());
        double r = 0.55 + 0.35 * b * p;
        float k = (float) ((0.16 + 0.34 * b * p) * g.flight(t));
        batch.glow(dot, new Vec3(c.x() - r, 0.006, c.z() - r), new Vec3(2 * r, 0, 0), new Vec3(0, 0, 2 * r),
                rc[0], rc[1], rc[2], k);
    }

    /**
     * The sprites pass: the beam behind it, its halo, a border's outline,
     * the item, then what its border gives off and the beam's sparks in
     * front. {@code right} and {@code up} are the camera's.
     */
    void draw(World.GroundItem g, double t, Vec3 eye, double yaw, Vec3 right, Vec3 up) {
        if (!g.out(t)) return;
        Vec3 c = g.centre(t);
        String id = g.def.id();
        float[] rc = rgb(g.item.rarity().rgb());
        double b = beamStrength(g, t);
        // the beam stands upright and turns to face the camera only about the vertical
        Vec3 level = new Vec3(right.x(), 0, right.z()).normalize(new Vec3(1, 0, 0));
        Vec3 foot = new Vec3(g.position.x(), 0, g.position.z());
        if (b > 0) beam(foot, level, rc, b, t, g.phase());

        ItemSprites.Spin spin = items.spin(id);
        double length = spin == null ? g.def.worldSize() : spin.length();
        double hs = length * HALO_SIZE;
        float halo = (float) (HALO * (0.9 + 0.1 * pulse(t, g.phase())));
        batch.glow(dot, corner(c, right, up, hs), right.scale(hs), up.scale(-hs), rc[0], rc[1], rc[2], halo);

        SheetTexture sheet = spin == null ? null : sheet(id, SpriteView.of(eye, c, 0, yaw).elevation());
        Border border = g.item.border();
        if (sheet != null) {
            drawSprite(g, t, c, right, up, spin, sheet, rc, border);
        } else {
            Texture icon = sprites.icon(id, g.def.fallbackIcon());
            double s = g.def.worldSize();
            batch.quad(icon, corner(c, right, up, s), right.scale(s), up.scale(-s), 0, 0, 1, 1, 1, 1, 1, 1);
        }
        if (border != null) motes(border, c, right, up, length, t, g.phase());
        if (b > 0) sparks(foot, level, right, up, rc, b, t, g.phase());
    }

    private void drawSprite(World.GroundItem g, double t, Vec3 c, Vec3 right, Vec3 up, ItemSprites.Spin spin,
                            SheetTexture sheet, float[] rc, Border border) {
        double w = spin.profile().frameWorldSize(), h = spin.profile().frameWorldHeight();
        double texel = w / Math.max(1, spin.profile().frameWidth());
        Vec3 topLeft = c.sub(right.scale(w / 2)).add(up.scale(h / 2));
        Vec3[] q = LayerStack.regionQuad(topLeft, right.scale(w), up.scale(-h), sheet.region(false));
        float[] uv = sheet.uv(frame(g.spin(t), sheet.frameCount()), false);
        float cu0 = Math.min(uv[0], uv[2]), cv0 = Math.min(uv[1], uv[3]);
        float cu1 = Math.max(uv[0], uv[2]), cv1 = Math.max(uv[1], uv[3]);

        // its outline as light, a ring or two out round it; a border's outline, solid
        int silhouette = batch.palette(items.silhouette(sheet.sharedPalette()));
        for (int ring = 0; ring < OUTLINE_GLOW.length; ring++) {
            for (double[] d : AROUND) {
                Vec3 at = q[0].add(right.scale(d[0] * OUTLINE_GLOW[ring] * texel))
                        .add(up.scale(d[1] * OUTLINE_GLOW[ring] * texel));
                batch.glow(sheet.texture(), at, q[1], q[2], uv[0], uv[1], uv[2], uv[3], cu0, cv0, cu1, cv1,
                        rc[0], rc[1], rc[2], (float) OUTLINE_GLOW_STRENGTH[ring], silhouette);
            }
        }
        if (border != null) {
            float[] bc = rgb(border.rgb());
            float k = (float) (0.8 + 0.2 * pulse(t * 1.6, g.phase()));
            for (double[] d : AROUND) {
                Vec3 at = q[0].add(right.scale(Math.signum(d[0]) * texel)).add(up.scale(Math.signum(d[1]) * texel));
                batch.quadClipped(sheet.texture(), at, q[1], q[2], uv[0], uv[1], uv[2], uv[3],
                        cu0, cv0, cu1, cv1, bc[0] * k, bc[1] * k, bc[2] * k, 1, silhouette);
            }
        }
        String tier = g.item.tier() == null ? null : g.item.tier().key();
        int palette = batch.palette(items.palette(sheet, g.def.id(), tier));
        batch.quadClipped(sheet.texture(), q[0], q[1], q[2], uv[0], uv[1], uv[2], uv[3],
                cu0, cv0, cu1, cv1, 1, 1, 1, 1, palette);
    }

    /** {@code id}'s turning sheet seen from {@code e}: the last one drawn while it loads, null with none. */
    private SheetTexture sheet(String id, Elevation e) {
        SheetTexture s = sprites.sheet(items.resolve(id, e));
        if (s != null && !s.closed()) {
            shown.put(id, s);
            return s;
        }
        SheetTexture last = shown.get(id);
        if (last == null || last.closed()) return null;
        sprites.touch(last);
        return last;
    }

    private void beam(Vec3 foot, Vec3 level, float[] rc, double strength, double t, double phase) {
        double p = pulse(t, phase);
        float k = (float) (strength * p);
        double w = BEAM_WIDTH * (0.92 + 0.08 * p);
        batch.glow(beam, foot.sub(level.scale(w / 2)).add(0, BEAM_HEIGHT, 0), level.scale(w),
                new Vec3(0, -BEAM_HEIGHT, 0), rc[0], rc[1], rc[2], 0.8f * k);
        // a narrower, whiter core
        double cw = w * 0.4, ch = BEAM_HEIGHT * 0.85;
        float[] core = whiter(rc, 0.5f);
        batch.glow(beam, foot.sub(level.scale(cw / 2)).add(0, ch, 0), level.scale(cw), new Vec3(0, -ch, 0),
                core[0], core[1], core[2], 0.55f * k);
    }

    private void sparks(Vec3 foot, Vec3 level, Vec3 right, Vec3 up, float[] rc, double strength,
                        double t, double phase) {
        float[] sc = whiter(rc, 0.5f);
        for (int i = 0; i < SPARKS; i++) {
            Mote m = spark(i, t, phase);
            Vec3 at = foot.add(level.scale(m.x())).add(0, m.y(), 0);
            batch.glow(star, corner(at, right, up, m.size()), right.scale(m.size()), up.scale(-m.size()),
                    sc[0], sc[1], sc[2], (float) (m.alpha() * strength));
        }
    }

    /** What {@code border} gives off round an item {@code length} long centred on {@code c}. */
    private void motes(Border border, Vec3 c, Vec3 right, Vec3 up, double length, double t, double phase) {
        float[] bc = rgb(border.rgb());
        for (int i = 0; i < MOTES; i++) {
            Mote m = mote(border, i, t, phase, length);
            if (m.alpha() <= 0) continue;
            Vec3 at = c.add(right.scale(m.x())).add(up.scale(m.y()));
            double s = m.size();
            switch (border) {
                case VENOM -> {
                    // a drop, a little longer than it is wide, and its glint
                    batch.quad(dot, at.sub(right.scale(s / 2)).add(up.scale(s * 0.75)), right.scale(s),
                            up.scale(-s * 1.5), 0, 0, 1, 1, bc[0], bc[1], bc[2], (float) m.alpha());
                    batch.glow(dot, corner(at, right, up, s * 2), right.scale(s * 2), up.scale(-s * 2),
                            bc[0], bc[1], bc[2], (float) (0.35 * m.alpha()));
                }
                case MYSTIC -> batch.quad(dot, corner(at, right, up, s), right.scale(s), up.scale(-s),
                        0, 0, 1, 1, bc[0], bc[1], bc[2], (float) m.alpha());
                case BONDED -> {
                    batch.quad(heart, corner(at, right, up, s), right.scale(s), up.scale(-s),
                            0, 0, 1, 1, bc[0], bc[1], bc[2], (float) m.alpha());
                    double ss = s * 0.9;
                    Vec3 twinkle = at.add(right.scale(s * 0.9)).add(up.scale(s * 0.5));
                    batch.glow(star, corner(twinkle, right, up, ss), right.scale(ss), up.scale(-ss),
                            bc[0], bc[1], bc[2], (float) (0.8 * m.alpha()));
                }
            }
        }
    }

    // --- the arithmetic (no GL: tested) --------------------------------------------------

    /** A small thing drawn round an item: where (metres across and up from where it is measured), how big, how much of it. */
    record Mote(double x, double y, double size, double alpha) {}

    /** 0.5 – 1: the beam's and the halo's pulse at time {@code t}. */
    static double pulse(double t, double phase) {
        return 0.75 + 0.25 * Math.sin(2 * Math.PI * BEAM_HZ * t + phase);
    }

    /** 0 – 1: how much of its beam an item has - none unless it came out of a chest, rising once it has landed. */
    static double beamStrength(World.GroundItem g, double t) {
        if (!g.beam() || !g.landed(t)) return 0;
        double since = t - g.landedAt();
        return Math.max(0, Math.min(1, since / BEAM_RISE));
    }

    /** The frame of {@code frames} an item {@code turns} turns round is drawn at. */
    static int frame(double turns, int frames) {
        int n = Math.max(1, frames);
        return Math.floorMod((int) Math.floor(turns * n), n);
    }

    /** Spark {@code i} of a beam at time {@code t}: rising up it, across from its middle by up to a third of its width. */
    static Mote spark(int i, double t, double phase) {
        double period = 1.8 + 0.9 * hash(i, 1);
        double f = fract(t / period + hash(i, 2) + phase * 0.13);
        double x = (hash(i, 3) - 0.5) * BEAM_WIDTH * 0.66 + 0.04 * Math.sin(t * 3 + i);
        double y = 0.1 + f * (BEAM_HEIGHT - 0.3);
        return new Mote(x, y, 0.07 + 0.06 * hash(i, 4), Math.sin(Math.PI * f));
    }

    /**
     * Mote {@code i} {@code border} gives off at time {@code t} round an item
     * {@code length} long, measured from its centre: Venom's drips fall from
     * it, Mystic's puffs rise and spread, Bonded's hearts float up and sway.
     */
    static Mote mote(Border border, int i, double t, double phase, double length) {
        double half = length / 2, across = Math.max(0.12, length * 0.22);
        return switch (border) {
            case VENOM -> {
                double period = 1.1 + 0.4 * hash(i, 11);
                double f = fract(t / period + hash(i, 12) + phase * 0.21);
                double s = f * period;
                double x = (hash(i, 13) - 0.5) * 2 * across;
                double y = (hash(i, 14) - 0.6) * half - 0.5 * 2.2 * s * s;
                yield new Mote(x, y, 0.045 + 0.02 * hash(i, 15), f < 0.08 ? f / 0.08 : 1 - (f - 0.08) / 0.92);
            }
            case MYSTIC -> {
                double period = 2.6 + 0.8 * hash(i, 21);
                double f = fract(t / period + hash(i, 22) + phase * 0.17);
                double x = (hash(i, 23) - 0.5) * 2 * across + 0.08 * Math.sin(t * 1.3 + i);
                double y = (hash(i, 24) - 0.5) * half + 0.5 * f;
                yield new Mote(x, y, 0.12 + 0.26 * f, 0.42 * Math.sin(Math.PI * f));
            }
            case BONDED -> {
                double period = 2.2 + 0.6 * hash(i, 31);
                double f = fract(t / period + hash(i, 32) + phase * 0.19);
                double x = (hash(i, 33) - 0.5) * 2 * across + 0.07 * Math.sin(t * 2.4 + i * 1.7);
                double y = (hash(i, 34) - 0.4) * half + 0.45 * f;
                yield new Mote(x, y, 0.08 + 0.03 * hash(i, 35), Math.sin(Math.PI * f));
            }
        };
    }

    /** A steady pseudo-random number in [0, 1) for {@code i} and {@code salt}. */
    static double hash(int i, int salt) {
        return fract(Math.sin(i * 12.9898 + salt * 78.233) * 43758.5453);
    }

    private static double fract(double v) {
        return v - Math.floor(v);
    }

    /** {@code 0xRRGGBB} as floats. */
    static float[] rgb(int rgb) {
        return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f};
    }

    private static float[] whiter(float[] c, float w) {
        return new float[]{c[0] + (1 - c[0]) * w, c[1] + (1 - c[1]) * w, c[2] + (1 - c[2]) * w};
    }

    /** The top-left corner of a camera-facing square {@code size} across centred on {@code c}. */
    private static Vec3 corner(Vec3 c, Vec3 right, Vec3 up, double size) {
        return c.sub(right.scale(size / 2)).add(up.scale(size / 2));
    }

    // --- the textures (white, alpha the shape: tinted when drawn) -------------------------

    /** The beam: bright down its middle and softer out to its sides, brightest at the ground and fading out at the top. */
    static int[] beamPixels(int w, int h) {
        int[] argb = new int[w * h];
        for (int y = 0; y < h; y++) {
            double up = 1 - (y + 0.5) / h;          // 0 at the ground, 1 at the top
            double fall = Math.pow(1 - up, 1.3) * smooth(up / 0.03);
            for (int x = 0; x < w; x++) {
                double u = (x + 0.5) / w * 2 - 1;
                double across = (0.75 * Math.exp(-(u / 0.2) * (u / 0.2)) + 0.35 * Math.exp(-(u / 0.55) * (u / 0.55)))
                        * (1 - u * u);
                argb[y * w + x] = alpha(across * fall);
            }
        }
        return argb;
    }

    /** A soft round blob. */
    static int[] dotPixels(int n) {
        int[] argb = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r2 = dx * dx + dy * dy;
                argb[y * n + x] = alpha(r2 >= 1 ? 0 : Math.pow(1 - r2, 2));
            }
        }
        return argb;
    }

    /** A four-pointed sparkle. */
    static int[] starPixels(int n) {
        int[] argb = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double u = (x + 0.5) / n * 2 - 1, v = (y + 0.5) / n * 2 - 1;
                double core = Math.exp(-(u * u + v * v) / 0.03);
                double rays = Math.exp(-(u / 0.07) * (u / 0.07)) * sq(1 - Math.abs(v))
                        + Math.exp(-(v / 0.07) * (v / 0.07)) * sq(1 - Math.abs(u));
                argb[y * n + x] = alpha(core + 0.8 * rays);
            }
        }
        return argb;
    }

    /** A heart, its edge smoothed (each pixel sampled 4 x 4). */
    static int[] heartPixels(int n) {
        int[] argb = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int in = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        double u = ((x + (sx + 0.5) / 4) / n * 2 - 1) * 1.3;
                        double v = -((y + (sy + 0.5) / 4) / n * 2 - 1) * 1.3 + 0.15;
                        double a = u * u + v * v - 1;
                        if (a * a * a - u * u * v * v * v <= 0) in++;
                    }
                }
                argb[y * n + x] = alpha(in / 16.0);
            }
        }
        return argb;
    }

    private static int alpha(double a) {
        int ia = (int) Math.round(Math.max(0, Math.min(1, a)) * 255);
        return (ia << 24) | 0xFFFFFF;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private static double sq(double v) {
        return v * v;
    }

    @Override
    public void close() {
        beam.close();
        dot.close();
        star.close();
        heart.close();
    }
}
