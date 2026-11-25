package com.larsons.game.world;

import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Texture;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.ScenerySprites;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws the {@link Scenery}: every tree and tuft as two cards crossing at
 * right angles through its axis, both showing its frame of the moment, the
 * frame's bottom edge on the ground and {@code frameWorldSize} metres
 * across - and every rock as one such card turned to face the camera
 * ({@link SceneryProfile#facesCamera()}), as the characters and the items
 * lying about are: a low, wide rock drawn as a cross is an X seen from
 * above. A rock's card is tested and written at the depth of an upright card
 * on its spot ({@link Batch#setDepthPlane}), the same depth the characters
 * are tested at, so she is in front of it or behind it as her feet are.
 *
 * <p>The cards are <b>cutouts</b>: drawn through the sprite {@link Batch}
 * with its alpha cut at {@value #CUT}, so every texel of the pixel art is
 * either there or not, and with depth test <em>and</em> depth write on, so
 * they stand in the depth buffer as solid as real geometry - the character
 * (a billboard drawn later, depth-tested) walks behind a tree or stands in a
 * tuft and is hidden by it texel for texel, and their order does not matter.
 * No face culling: the back of a card is the picture mirrored, as Minecraft's
 * plants are. They take the batch's fog, like the sprites. Tall grass (a
 * profile that {@link SceneryProfile#bend}s) leans away from whoever walks
 * through it, its cards' tops pushed aside and down ({@link #bendAwayFrom}).
 * A foraging bush that has been picked bare ({@link #showBare}) is drawn
 * from its picked sheet ({@link SceneryProfile.Forage#picked}) until it
 * grows its pickings back.
 *
 * <p>A tree felled with an axe ({@link #showFelled}, {@link Felling}) shakes
 * at each blow; felled, its two cards are cut at {@link Felling#cut} and
 * the part above turned together about the cut, along its fall ({@link
 * Fellings.Tree#angle}), the part below left standing as its stump with a
 * pale cut face on top - or its kind's own stump sprite, when it names one.
 *
 * <p>Every sheet is uploaded once, when the game starts (all of them together
 * are a few megabytes), as a {@link Texture#cutout} - nearest-neighbour, the
 * pixel art's hard edges at any distance, but mipmapped so the far grass does
 * not sparkle. All the crosses of one sprite come from its one sheet, so the
 * whole meadow is a draw call per sprite.
 */
public final class SceneryRenderer implements AutoCloseable {

    /** The alpha cut the cards are drawn with: a texel is in or out. */
    public static final float CUT = 0.5f;

    /** How near (metres) someone must come to tall grass for it to bend aside. */
    public static final double BEND_REACH = 0.85;

    private final Map<String, Texture> sheets = new TreeMap<>();
    private final Map<String, ScenerySprites.Sprite> byName = new TreeMap<>();
    /** Who the tall grass bends away from this frame: their feet. */
    private List<Vec3> walkers = List.of();
    /** Which foraging bushes are bare this frame (picked, growing back). */
    private java.util.function.Predicate<Scenery.Placed> bare = p -> false;
    /** The trees felling has left as they are ({@code World#felled}: null for one never struck), and the world's time. */
    private java.util.function.Function<Scenery.Placed, Fellings.Tree> felled = p -> null;
    private double fellTime;
    /** A white texel: the stumps' cut faces. */
    private Texture plain;

    /** Upload every sheet of {@code sprites}. Must be made on the GL thread. */
    public SceneryRenderer(ScenerySprites sprites) {
        this(sprites, (name, done) -> {});
    }

    /** The renderer, telling {@code progress} each sheet's name as it starts on it, and how many are done. */
    public SceneryRenderer(ScenerySprites sprites, java.util.function.ObjIntConsumer<String> progress) {
        int done = 0;
        for (ScenerySprites.Sprite s : sprites.sprites()) {
            progress.accept(s.name(), done++);
            byName.put(s.name(), s);
            try {
                BufferedImage image = ImageIO.read(s.sheet().toFile());
                if (image == null) throw new IOException("not an image");
                sheets.put(s.name(), Texture.cutout(PixelData.of(image), s.profile().frameWidth(),
                        s.profile().frameHeight()));
            } catch (IOException | RuntimeException e) {
                System.err.println("[scenery] cannot load " + s.sheet() + ": " + e.getMessage());
            }
        }
        if (!sheets.isEmpty()) System.out.println("[scenery] " + String.join(", ", sheets.keySet()));
    }

    /**
     * The feet of everyone walking about this frame - the player, and
     * whatever else wanders the meadow - whom the tall grass ({@link
     * SceneryProfile#bend}) leans away from.
     */
    public void bendAwayFrom(List<Vec3> feet) {
        walkers = feet == null ? List.of() : feet;
    }

    /** Which foraging bushes are picked bare - drawn from their picked sheet ({@code World#picked}). */
    public void showBare(java.util.function.Predicate<Scenery.Placed> picked) {
        bare = picked == null ? p -> false : picked;
    }

    /**
     * How the trees struck with an axe stand at world time {@code now}:
     * {@code felled} gives each one's felling (null: never struck, drawn as
     * it always is).
     */
    public void showFelled(java.util.function.Function<Scenery.Placed, Fellings.Tree> felled, double now) {
        this.felled = felled == null ? p -> null : felled;
        fellTime = now;
    }

    /** How many pieces of scenery the last {@link #draw} had in view. */
    private int drawn;

    public int drawn() { return drawn; }

    /** Whether there is any scenery to draw. */
    public boolean isEmpty() { return sheets.isEmpty(); }

    /**
     * Draw everything in {@code scenery} as it stands at world time {@code
     * time}, seen from {@code camera}; {@code uprightNormal} is the way the
     * characters' upright depth planes face (the rocks are tested on the
     * same planes).
     */
    public void draw(Batch batch, Scenery scenery, Mat4 viewProj, float[] fogColor, float fogFar, double time,
                     OrbitCamera camera, Vec3 uprightNormal) {
        draw(batch, scenery, viewProj, fogColor, fogFar, time, camera, uprightNormal, 0);
    }

    /** {@link #draw}, leaving out what stands farther than {@code farthest} metres from the eye (0: none left out). */
    public void draw(Batch batch, Scenery scenery, Mat4 viewProj, float[] fogColor, float fogFar, double time,
                     OrbitCamera camera, Vec3 uprightNormal, double farthest) {
        if (scenery.isEmpty() || sheets.isEmpty()) return;
        Vec3 eye = camera.eye();
        double reachSq = farthest > 0 ? farthest * farthest : Double.MAX_VALUE;
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        batch.shadowBias(SHADOW_BIAS);
        batch.begin(viewProj, fogColor, 0, fogFar);
        batch.shadowBias(Batch.SPRITE_SHADOW_BIAS);
        batch.setAlphaCut(CUT);
        batch.setDepthPlane(camera.eye(), uprightNormal);
        Vec3 right = camera.right(), up = camera.up();
        com.larsons.game.math.Frustum frustum = new com.larsons.game.math.Frustum(viewProj);
        drawn = 0;
        for (Map.Entry<ScenerySprites.Sprite, List<Scenery.Placed>> e : scenery.bySprite().entrySet()) {
            Texture sheet = sheets.get(e.getKey().name());
            if (sheet == null) continue;
            double wide = e.getKey().profile().frameWorldSize() / 2, tall = e.getKey().profile().frameWorldHeight();
            boolean facing = e.getKey().profile().facesCamera(), bends = e.getKey().profile().bend();
            SceneryProfile.Forage forage = e.getKey().profile().forage();
            ScenerySprites.Sprite bareSprite = forage == null || forage.picked() == null ? null
                    : byName.get(forage.picked());
            Texture bareSheet = bareSprite == null ? null : sheets.get(bareSprite.name());
            for (Scenery.Placed p : e.getValue()) {
                double ex = p.x() - eye.x(), ez = p.z() - eye.z();
                if (ex * ex + ez * ez > reachSq) continue;
                // (out of view: not drawn - a ball round its card or cross, with room for a lean)
                double sc = p.scale();
                // (a card that faces the camera leans back with the view: its top may be half its height off)
                double reach = Math.hypot(wide, tall / 2) * sc + (facing ? tall * sc / 2 : 0) + 0.5;
                if (!frustum.sees(p.x(), p.y() + tall * sc / 2, p.z(), reach)) continue;
                drawn++;
                batch.tint(p.tint());
                Texture t = sheet;
                ScenerySprites.Sprite frames = p.sprite();
                if (bareSheet != null && bare.test(p)) {
                    t = bareSheet;
                    frames = bareSprite;
                }
                Fellings.Tree ft = facing ? null : felled.apply(p);
                if (facing) {
                    batch.depthAnchor(p.position());
                    facingCamera(batch, t, frames, p, time, right, up);
                } else if (ft != null) {
                    batch.depthAnchor(null);
                    felled(batch, t, frames, p, time, ft);
                } else {
                    batch.depthAnchor(null);
                    cross(batch, t, frames, p, time, bends ? lean(p, walkers) : null);
                }
            }
        }
        batch.tint(0);
        batch.depthAnchor(null);
        batch.setAlphaCut(Batch.DEFAULT_ALPHA_CUT);
        batch.end();
    }

    /**
     * How far towards the sun the cards look themselves up in the sun's
     * shadows, metres: past a tree's own crown, so its leaves are not in the
     * shadow of the card it casts its shadow with - but in a nearer tree's.
     */
    static final float SHADOW_BIAS = 2.4f;
    /** Scenery lower than this casts no shadow of the sun (grass, flowers: the ground is in the shade of them enough). */
    static final double LEAST_SHADOW = 0.75;

    /**
     * Draw what of {@code scenery} casts a shadow into the sun's shadow map
     * (begun already) through {@code sunViewProj}: each piece within {@code
     * reach} of {@code centre} as one card standing across the way to the
     * sun ({@code towardSun}), so its shadow has the outline of its picture.
     */
    public void drawShadows(Batch batch, Scenery scenery, Mat4 sunViewProj, Vec3 towardSun, Vec3 centre,
                            double reach, double time) {
        if (scenery.isEmpty() || sheets.isEmpty()) return;
        double ax = -towardSun.z(), az = towardSun.x(), al = Math.hypot(ax, az);
        if (al < 1e-4) {
            ax = 1;
            az = 0;
        } else {
            ax /= al;
            az /= al;
        }
        batch.begin(sunViewProj);
        batch.setAlphaCut(CUT);
        for (Map.Entry<ScenerySprites.Sprite, List<Scenery.Placed>> e : scenery.bySprite().entrySet()) {
            Texture sheet = sheets.get(e.getKey().name());
            if (sheet == null) continue;
            SceneryProfile prof = e.getKey().profile();
            double tall = prof.frameWorldHeight();
            for (Scenery.Placed p : e.getValue()) {
                double h = tall * p.scale();
                if (h < LEAST_SHADOW) continue;
                double dx = p.x() - centre.x(), dz = p.z() - centre.z(), out = reach + h * 2.5;
                if (dx * dx + dz * dz > out * out) continue;
                Fellings.Tree ft = felled.apply(p);
                if (ft != null && !ft.standing()) {
                    // felled: what is coming down or lying casts its shadow as it lies; a stump, none worth one
                    if (ft.stage() != Fellings.Stage.STUMP) {
                        float[] uv = p.sprite().uv(p.sprite().frameAt(time + p.phase()));
                        fallen(batch, sheet, p, ft, uv);
                    }
                    continue;
                }
                ScenerySprites.Sprite frames = p.sprite();
                Texture t = sheet;
                if (bare.test(p) && prof.forage() != null && prof.forage().picked() != null) {
                    ScenerySprites.Sprite b = byName.get(prof.forage().picked());
                    Texture bt = b == null ? null : sheets.get(b.name());
                    if (bt != null) {
                        t = bt;
                        frames = b;
                    }
                }
                float[] uv = frames.uv(frames.frameAt(time + p.phase()));
                float u0 = p.mirrored() ? uv[2] : uv[0], u1 = p.mirrored() ? uv[0] : uv[2];
                double half = prof.frameWorldSize() * p.scale() / 2;
                card(batch, t, p.x(), p.y(), p.z(), ax * half, az * half, (float) h, u0, uv[1], u1, uv[3], null);
            }
        }
        batch.setAlphaCut(Batch.DEFAULT_ALPHA_CUT);
        batch.end();
    }

    /** One card facing the camera, its bottom edge's middle on the spot, leaning back with the view. */
    private static void facingCamera(Batch batch, Texture sheet, ScenerySprites.Sprite frames, Scenery.Placed p,
                                     double time, Vec3 right, Vec3 up) {
        SceneryProfile profile = p.profile();
        float[] uv = frames.uv(frames.frameAt(time + p.phase()));
        float u0 = p.mirrored() ? uv[2] : uv[0], u1 = p.mirrored() ? uv[0] : uv[2];
        double w = profile.frameWorldSize() * p.scale(), h = profile.frameWorldHeight() * p.scale();
        Vec3 topLeft = p.position().sub(right.scale(w / 2)).add(up.scale(h));
        batch.quad(sheet, topLeft, right.scale(w), up.scale(-h), u0, uv[1], u1, uv[3], 1, 1, 1, 1);
    }

    /**
     * How far tall grass at {@code p} leans away from the walkers near it:
     * the shift of its top, x, down, z metres (null: it stands straight).
     * It leans harder the nearer they are, flattened most underfoot.
     */
    static double[] lean(Scenery.Placed p, List<Vec3> walkers) {
        double h = p.profile().frameWorldHeight() * p.scale(), lx = 0, lz = 0;
        for (Vec3 w : walkers) {
            double dx = p.x() - w.x(), dz = p.z() - w.z(), d = Math.hypot(dx, dz);
            if (d >= BEND_REACH || Math.abs(w.y() - p.y()) > h + 0.5) continue;
            double k = 1 - d / BEND_REACH;
            k = k * k * (3 - 2 * k);
            if (d < 1e-4) {
                dx = 1;
                d = 1;
            }
            lx += dx / d * k;
            lz += dz / d * k;
        }
        double n = Math.hypot(lx, lz);
        if (n < 1e-3) return null;
        double amount = Math.min(1, n);
        lx *= amount / n;
        lz *= amount / n;
        return new double[]{lx * h * 0.55, amount * h * 0.3, lz * h * 0.55};
    }

    /** One cross: a card along its yaw and one at right angles to it, the same frame on both, leaning by {@code lean}. */
    private static void cross(Batch batch, Texture sheet, ScenerySprites.Sprite frames, Scenery.Placed p,
                              double time, double[] lean) {
        SceneryProfile profile = p.profile();
        float[] uv = frames.uv(frames.frameAt(time + p.phase()));
        float u0 = p.mirrored() ? uv[2] : uv[0], u1 = p.mirrored() ? uv[0] : uv[2];
        double half = profile.frameWorldSize() * p.scale() / 2;
        float h = (float) (profile.frameWorldHeight() * p.scale());
        double c = Math.cos(p.yaw()) * half, s = Math.sin(p.yaw()) * half;
        card(batch, sheet, p.x(), p.y(), p.z(), c, s, h, u0, uv[1], u1, uv[3], lean);
        card(batch, sheet, p.x(), p.y(), p.z(), -s, c, h, u0, uv[1], u1, uv[3], lean);
    }

    /**
     * A tree struck with an axe, as felling has left it: still standing (and
     * shaking from the last blow), coming down or lying (its stump and the
     * rest turned about the cut), or its stump alone.
     */
    private void felled(Batch batch, Texture sheet, ScenerySprites.Sprite frames, Scenery.Placed p, double time,
                        Fellings.Tree ft) {
        if (ft.standing()) {
            double sh = ft.shake(fellTime);
            double[] lean = sh == 0 ? null : new double[]{Math.cos(p.yaw()) * sh, 0, Math.sin(p.yaw()) * sh};
            cross(batch, sheet, frames, p, time, lean);
            return;
        }
        float[] uv = frames.uv(frames.frameAt(time + p.phase()));
        stump(batch, sheet, frames, p, uv, ft);
        if (ft.stage() != Fellings.Stage.STUMP) fallen(batch, sheet, p, ft, uv);
    }

    /**
     * {@code p}'s stump: its kind's own stump sprite as a cross, or the foot
     * of its own picture up to the cut - with the cut face on top.
     */
    private void stump(Batch batch, Texture sheet, ScenerySprites.Sprite frames, Scenery.Placed p, float[] uv,
                       Fellings.Tree ft) {
        String own = ft.rule().stump();
        ScenerySprites.Sprite s = own == null ? null : byName.get(own);
        Texture st = s == null ? null : sheets.get(s.name());
        if (st != null) {
            float[] su = s.uv(0);
            float u0 = p.mirrored() ? su[2] : su[0], u1 = p.mirrored() ? su[0] : su[2];
            double half = s.profile().frameWorldSize() * p.scale() / 2;
            float h = (float) (s.profile().frameWorldHeight() * p.scale());
            double c = Math.cos(p.yaw()) * half, sn = Math.sin(p.yaw()) * half;
            card(batch, st, p.x(), p.y(), p.z(), c, sn, h, u0, su[1], u1, su[3], null);
            card(batch, st, p.x(), p.y(), p.z(), -sn, c, h, u0, su[1], u1, su[3], null);
            return;
        }
        SceneryProfile profile = p.profile();
        double half = profile.frameWorldSize() * p.scale() / 2, h = profile.frameWorldHeight() * p.scale();
        double cut = Math.min(h, Felling.cut(p));
        float u0 = p.mirrored() ? uv[2] : uv[0], u1 = p.mirrored() ? uv[0] : uv[2];
        float vCut = (float) (uv[3] + (uv[1] - uv[3]) * cut / h);
        double c = Math.cos(p.yaw()) * half, sn = Math.sin(p.yaw()) * half;
        card(batch, sheet, p.x(), p.y(), p.z(), c, sn, (float) cut, u0, vCut, u1, uv[3], null);
        card(batch, sheet, p.x(), p.y(), p.z(), -sn, c, (float) cut, u0, vCut, u1, uv[3], null);
        cutFace(batch, p, p.y() + cut);
    }

    /** The pale face of a stump's cut, at {@code y}: an octagon of fresh wood in a ring of bark, a ring or two in it. */
    private void cutFace(Batch batch, Scenery.Placed p, double y) {
        if (plain == null) plain = Texture.white();
        TreeColours colours = TreeColours.of(p);
        int[] chips = colours.chips();
        int bark = chips[chips.length - 1], wood = chips[0], ring = chips[1];
        double r = p.radius() * 1.05;
        batch.tint(0);
        octagon(batch, p.x(), y, p.z(), r, p.yaw(), bark);
        octagon(batch, p.x(), y + 0.004, p.z(), r * 0.82, p.yaw(), wood);
        octagon(batch, p.x(), y + 0.008, p.z(), r * 0.42, p.yaw(), ring);
        octagon(batch, p.x(), y + 0.012, p.z(), r * 0.3, p.yaw(), wood);
        batch.tint(p.tint());
    }

    /** A level octagon of {@code rgb} round {@code (x, y, z)}, {@code r} across its corners: three quads. */
    private void octagon(Batch batch, double x, double y, double z, double r, double yaw, int rgb) {
        float[] px = new float[8], pz = new float[8];
        for (int i = 0; i < 8; i++) {
            double a = yaw + Math.PI / 8 + i * Math.PI / 4;
            px[i] = (float) (x + Math.cos(a) * r);
            pz[i] = (float) (z + Math.sin(a) * r);
        }
        float fy = (float) y, cr = ((rgb >> 16) & 255) / 255f, cg = ((rgb >> 8) & 255) / 255f, cb = (rgb & 255) / 255f;
        int[][] quads = {{0, 1, 2, 3}, {0, 3, 4, 7}, {4, 5, 6, 7}};
        for (int[] q : quads) {
            batch.quad(plain, px[q[0]], fy, pz[q[0]], px[q[1]], fy, pz[q[1]], px[q[2]], fy, pz[q[2]],
                    px[q[3]], fy, pz[q[3]], 0, 0, 1, 1, cr, cg, cb, 1);
        }
    }

    /**
     * The felled part of {@code p} - its two cards above the cut - turned
     * about the cut along its fall, as far over as it has come.
     */
    private void fallen(Batch batch, Texture sheet, Scenery.Placed p, Fellings.Tree ft, float[] uv) {
        SceneryProfile profile = p.profile();
        double half = profile.frameWorldSize() * p.scale() / 2, h = profile.frameWorldHeight() * p.scale();
        double cut = Math.min(h, Felling.cut(p));
        float u0 = p.mirrored() ? uv[2] : uv[0], u1 = p.mirrored() ? uv[0] : uv[2];
        float vCut = (float) (uv[3] + (uv[1] - uv[3]) * cut / h);
        double angle = ft.angle(fellTime);
        Vec3 d = com.larsons.game.sprite.SpriteView.headingVector(ft.heading());
        double c = Math.cos(p.yaw()) * half, sn = Math.sin(p.yaw()) * half;
        turned(batch, sheet, p, d, angle, cut, h - cut, c, sn, u0, uv[1], u1, vCut);
        turned(batch, sheet, p, d, angle, cut, h - cut, -sn, c, u0, uv[1], u1, vCut);
    }

    /**
     * One card reaching {@code (ax, az)} either side of the tree's axis, from
     * {@code cut} metres up to {@code len} above that, turned {@code angle}
     * about the cut towards {@code d}.
     */
    private static void turned(Batch batch, Texture sheet, Scenery.Placed p, Vec3 d, double angle, double cut,
                               double len, double ax, double az, float u0, float v0, float u1, float v1) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double bx = p.x(), by = p.y() + cut, bz = p.z();
        float[] out = new float[12];
        // top-left, top-right, bottom-right, bottom-left: (-a, len), (+a, len), (+a, 0), (-a, 0)
        double[][] corners = {{-1, len}, {1, len}, {1, 0}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            double wx = ax * corners[i][0], wz = az * corners[i][0], u = corners[i][1];
            double wd = wx * d.x() + wz * d.z();
            double kx = wx - wd * d.x(), kz = wz - wd * d.z();
            double along = wd * cos + u * sin, rise = u * cos - wd * sin;
            out[i * 3] = (float) (bx + kx + along * d.x());
            out[i * 3 + 1] = (float) (by + rise);
            out[i * 3 + 2] = (float) (bz + kz + along * d.z());
        }
        batch.quad(sheet, out[0], out[1], out[2], out[3], out[4], out[5], out[6], out[7], out[8], out[9], out[10],
                out[11], u0, v0, u1, v1, 1, 1, 1, 1);
    }

    /**
     * A card standing on the ground at {@code (x, y, z)}, reaching {@code (dx,
     * dz)} either side of it, its top shifted by {@code lean} (or upright).
     */
    private static void card(Batch batch, Texture sheet, double x, double y, double z, double dx, double dz, float h,
                             float u0, float v0, float u1, float v1, double[] lean) {
        float lx = (float) (x - dx), lz = (float) (z - dz), rx = (float) (x + dx), rz = (float) (z + dz);
        float bottom = (float) y, top = bottom + h;
        float sx = 0, sy = 0, sz = 0;
        if (lean != null) {
            sx = (float) lean[0];
            sy = (float) -lean[1];
            sz = (float) lean[2];
        }
        batch.quad(sheet, lx + sx, top + sy, lz + sz, rx + sx, top + sy, rz + sz, rx, bottom, rz, lx, bottom, lz,
                u0, v0, u1, v1, 1, 1, 1, 1);
    }

    @Override
    public void close() {
        for (Texture t : sheets.values()) t.close();
        sheets.clear();
        if (plain != null) plain.close();
        plain = null;
    }
}
