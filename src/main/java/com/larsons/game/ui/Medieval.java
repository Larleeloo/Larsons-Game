package com.larsons.game.ui;

import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.Font;
import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Texture;

/**
 * The wardrobe's look: an oak armoire with its doors open, drawn as if it
 * stood in the room - carved and bevelled wood lit from the top left, a
 * recess with walls in perspective, shelves with a top and a front, iron
 * corner brackets and rivets, a gilded mirror with an arched top, parchment
 * labels and serif lettering - rather than the flat panels of the other
 * menus ({@link Theme}).
 *
 * <p>Everything is made here: the wood (with its grain across, or down the
 * boards), the parchment, the rivet and the round gems are textures generated
 * once on the CPU ({@link #woodPixels}, {@link #parchmentPixels}, {@link
 * #rivetPixels}, {@link #discPixels}), and the rest is quads tinted and
 * stacked: light along an edge that faces up and left, shadow along one that
 * faces down and right, which is all the eye needs to see depth.
 */
public final class Medieval implements AutoCloseable {

    /** The generated textures' size, in texels. */
    public static final int TILE = 256;

    // the palette: oak, iron, brass and gold, ink on parchment, and the light
    public static final float[] INK = Theme.rgb(52, 32, 18, 1f);
    public static final float[] INK_FADED = Theme.rgb(120, 96, 70, 1f);
    public static final float[] GOLD = Theme.rgb(240, 200, 116, 1f);
    public static final float[] BRASS = Theme.rgb(214, 170, 92, 1f);
    public static final float[] CREAM = Theme.rgb(246, 230, 196, 1f);
    public static final float[] IRON = Theme.rgb(46, 46, 50, 1f);
    public static final float[] EMBER = Theme.rgb(196, 72, 46, 1f);
    private static final float[] LIGHT = {1f, 0.94f, 0.82f, 1f}, SHADE = {0.06f, 0.03f, 0.01f, 1f};

    private final Ui ui;
    private final Texture woodAcross, woodDown, parchment, rivet, disc;
    /** The lettering: serif, as carved and as inked. */
    public final Font title, heading, body, small;

    public Medieval(Ui ui) {
        this.ui = ui;
        int[] across = woodPixels(TILE, 7);
        woodAcross = Texture.tiled(PixelData.of(across, TILE, TILE));
        woodDown = Texture.tiled(PixelData.of(transpose(across, TILE), TILE, TILE));
        parchment = Texture.tiled(PixelData.of(parchmentPixels(TILE, 11), TILE, TILE));
        rivet = Texture.upload(PixelData.of(rivetPixels(32), 32, 32), false);
        disc = Texture.upload(PixelData.of(discPixels(64), 64, 64), false);
        float s = ui.scale();
        title = new Font(java.awt.Font.SERIF, 34, true, s);
        heading = new Font(java.awt.Font.SERIF, 19, true, s);
        body = new Font(java.awt.Font.SERIF, 17, false, s);
        small = new Font(java.awt.Font.SERIF, 14, true, s);
    }

    private Batch batch() {
        return ui.batch();
    }

    // --- surfaces ----------------------------------------------------------------------

    /** Oak over a rectangle, its grain across it (or down it), {@code shade} of its light (1: as it is). */
    public void wood(float x, float y, float w, float h, float shade, boolean down) {
        Texture t = down ? woodDown : woodAcross;
        batch().rect(t, x, y, w, h, x / TILE, y / TILE, (x + w) / TILE, (y + h) / TILE, shade, shade, shade, 1);
    }

    /** Boards standing side by side, {@code board} wide: the wood down them and a seam between each two. */
    public void boards(float x, float y, float w, float h, float board, float shade) {
        wood(x, y, w, h, shade, true);
        for (float sx = x + board; sx < x + w - board / 3; sx += board) {
            ui.rect(sx, y, 2, h, Theme.withAlpha(SHADE, 0.45f));
            ui.rect(sx + 2, y, 1, h, Theme.withAlpha(LIGHT, 0.10f));
        }
    }

    /** Parchment over a rectangle, a little darker towards its edges, sunk into what it lies on. */
    public void parchment(float x, float y, float w, float h) {
        batch().rect(parchment, x, y, w, h, x / TILE, y / TILE, (x + w) / TILE, (y + h) / TILE, 1, 1, 1, 1);
        for (int i = 0; i < 3; i++) ui.outline(x + i, y + i, w - 2 * i, h - 2 * i, 1, Theme.rgb(110, 76, 40, 0.22f - i * 0.06f));
        sunken(x, y, w, h, 1, 0.5f);
    }

    // --- light and shadow --------------------------------------------------------------

    /** A raised piece's edges: the top and the left catching the light, the bottom and the right in shadow. */
    public void bevel(float x, float y, float w, float h, float t, float strength) {
        ui.rect(x, y, w, t, Theme.withAlpha(LIGHT, 0.30f * strength));
        ui.rect(x, y + t, t, h - t, Theme.withAlpha(LIGHT, 0.20f * strength));
        ui.rect(x, y + h - t, w, t, Theme.withAlpha(SHADE, 0.55f * strength));
        ui.rect(x + w - t, y, t, h - t, Theme.withAlpha(SHADE, 0.45f * strength));
    }

    /** A sunken piece's edges: the light the other way round. */
    public void sunken(float x, float y, float w, float h, float t, float strength) {
        ui.rect(x, y, w, t, Theme.withAlpha(SHADE, 0.55f * strength));
        ui.rect(x, y + t, t, h - t, Theme.withAlpha(SHADE, 0.45f * strength));
        ui.rect(x, y + h - t, w, t, Theme.withAlpha(LIGHT, 0.25f * strength));
        ui.rect(x + w - t, y + t, t, h - 2 * t, Theme.withAlpha(LIGHT, 0.18f * strength));
    }

    /** The soft shadow a piece {@code d} out from the wall casts, down and to the right. */
    public void shadow(float x, float y, float w, float h, float d) {
        for (int i = 1; i <= 4; i++) {
            float k = d * i / 4f;
            ui.rect(x + k, y + k, w, h, Theme.withAlpha(SHADE, 0.10f));
        }
    }

    // --- the joinery -------------------------------------------------------------------

    /**
     * A box sunk {@code depth} into the wood, seen from in front: its back
     * wall darker, and its four walls in perspective - the top one in shadow,
     * the left lit, the floor catching the light, the right in half shadow.
     * The back wall is {@code depth} in from the rectangle all round.
     */
    public void recess(float x, float y, float w, float h, float depth) {
        float d = depth;
        boards(x + d, y + d, w - 2 * d, h - 2 * d, 120, 0.52f);
        // the walls: quads from the opening's edge to the back wall's
        wall(woodAcross, x, y, x + w, y, x + w - d, y + d, x + d, y + d, 0.30f);          // top
        wall(woodDown, x, y, x + d, y + d, x + d, y + h - d, x, y + h, 0.62f);              // left
        wall(woodAcross, x + d, y + h - d, x + w - d, y + h - d, x + w, y + h, x, y + h, 0.78f); // floor
        wall(woodDown, x + w - d, y + d, x + w, y, x + w, y + h, x + w - d, y + h - d, 0.42f); // right
        // the shadow the top wall and the left wall throw on the back wall
        ui.rect(x + d, y + d, w - 2 * d, 10, Theme.withAlpha(SHADE, 0.35f));
        ui.rect(x + d, y + d + 10, w - 2 * d, 10, Theme.withAlpha(SHADE, 0.15f));
        ui.rect(x + d, y + d, 8, h - 2 * d, Theme.withAlpha(SHADE, 0.22f));
        ui.outline(x, y, w, h, 1, Theme.withAlpha(SHADE, 0.7f));
    }

    private void wall(Texture t, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                      float shade) {
        float u0 = Math.min(Math.min(x0, x1), Math.min(x2, x3)) / TILE, v0 = Math.min(Math.min(y0, y1), Math.min(y2, y3)) / TILE;
        float u1 = Math.max(Math.max(x0, x1), Math.max(x2, x3)) / TILE, v1 = Math.max(Math.max(y0, y1), Math.max(y2, y3)) / TILE;
        batch().quad(t, x0, y0, 0, x1, y1, 0, x2, y2, 0, x3, y3, 0, u0, v0, u1, v1, shade, shade, shade, 1);
    }

    /** A shelf {@code w} wide whose top is at {@code y}: its top face in perspective, its front, and the shadow under it. */
    public void shelf(float x, float y, float w) {
        float top = 7, front = 9;
        wall(woodAcross, x + 6, y, x + w - 6, y, x + w, y + top, x, y + top, 0.95f);
        wood(x, y + top, w, front, 0.70f, false);
        bevel(x, y + top, w, front, 2, 0.8f);
        ui.rect(x, y + top + front, w, 4, Theme.withAlpha(SHADE, 0.40f));
        ui.rect(x, y + top + front + 4, w, 5, Theme.withAlpha(SHADE, 0.16f));
    }

    /** A carved plaque: darker raised wood with a bevelled edge and a rivet at each end. */
    public void plaque(float x, float y, float w, float h) {
        shadow(x, y, w, h, 4);
        wood(x, y, w, h, 0.62f, false);
        bevel(x, y, w, h, 3, 1f);
        ui.outline(x + 5, y + 5, w - 10, h - 10, 1, Theme.withAlpha(SHADE, 0.35f));
        ui.outline(x + 6, y + 6, w - 12, h - 12, 1, Theme.withAlpha(LIGHT, 0.10f));
        rivet(x + 11, y + h / 2, 4);
        rivet(x + w - 11, y + h / 2, 4);
    }

    /** A moulding along the cabinet's top: a raised band of wood, rounded by its light. */
    public void moulding(float x, float y, float w, float h, float shade) {
        shadow(x, y, w, h, 3);
        wood(x, y, w, h, shade, false);
        ui.rect(x, y + 1, w, h * 0.35f, Theme.withAlpha(LIGHT, 0.14f));
        ui.rect(x, y + h * 0.7f, w, h * 0.3f, Theme.withAlpha(SHADE, 0.28f));
        bevel(x, y, w, h, 2, 0.9f);
    }

    /** A domed iron rivet. */
    public void rivet(float cx, float cy, float r) {
        batch().rect(rivet, cx - r, cy - r, 2 * r, 2 * r, 0, 0, 1, 1, 1, 1, 1, 1);
    }

    /**
     * An iron bracket over the corner at ({@code cx}, {@code cy}): two straps
     * {@code len} long running in from it along the edges, riveted - {@code
     * sx}, {@code sy} (1 or -1) which way they run.
     */
    public void bracket(float cx, float cy, float len, int sx, int sy) {
        float t = 12;
        float hx = sx > 0 ? cx : cx - len, vy = sy > 0 ? cy : cy - len;
        float hy = sy > 0 ? cy : cy - t, vx = sx > 0 ? cx : cx - t;
        for (float[] r : new float[][]{{hx, hy, len, t}, {vx, vy, t, len}}) {
            shadow(r[0], r[1], r[2], r[3], 2);
            ui.rect(r[0], r[1], r[2], r[3], IRON);
            bevel(r[0], r[1], r[2], r[3], 2, 0.9f);
        }
        rivet(vx + t / 2, hy + t / 2, 3.5f);
        rivet(hx + (sx > 0 ? len - 7 : 7), hy + t / 2, 3f);
        rivet(vx + t / 2, vy + (sy > 0 ? len - 7 : 7), 3f);
    }

    /**
     * A door of the armoire swung open towards the viewer, hung on the
     * cabinet's edge at {@code hingeX} from {@code y} for {@code h}, {@code
     * dw} wide: its far edge shorter (it is nearer), its inside face of
     * boards with a sunken panel, the hinges, and an iron ring to pull it by.
     * {@code side} -1 for the left door, 1 for the right.
     */
    public void door(float hingeX, float y, float h, float dw, int side) {
        float near = Math.min(22, h * 0.04f);
        float fx = hingeX + side * dw;
        // shadow on the wall behind it
        Batch b = batch();
        b.quad(ui.white(), hingeX, y + 10, 0, fx + side * 8, y + near + 14, 0, fx + side * 8, y + h - near + 14, 0,
                hingeX, y + h + 10, 0, 0, 0, 1, 1, 0, 0, 0, 0.30f);
        float x0 = Math.min(hingeX, fx), x1 = Math.max(hingeX, fx);
        float[] top = side < 0 ? new float[]{fx, y - near, hingeX, y} : new float[]{hingeX, y, fx, y - near};
        float[] bot = side < 0 ? new float[]{hingeX, y + h, fx, y + h + near} : new float[]{fx, y + h + near, hingeX, y + h};
        b.quad(woodDown, top[0], top[1], 0, top[2], top[3], 0, bot[0], bot[1], 0, bot[2], bot[3], 0,
                x0 / TILE, (y - near) / TILE, x1 / TILE, (y + h + near) / TILE, 0.78f, 0.78f, 0.78f, 1);
        // a sunken panel on its inside face, in the same perspective
        float in = 0.18f;
        float ax = hingeX + side * dw * in, bx = hingeX + side * dw * (1 - in);
        float ay0 = y + h * 0.08f - near * in, ay1 = y + h * 0.92f + near * in;
        float by0 = y + h * 0.08f - near * (1 - in), by1 = y + h * 0.92f + near * (1 - in);
        b.quad(ui.white(), side < 0 ? bx : ax, side < 0 ? by0 : ay0, 0, side < 0 ? ax : bx, side < 0 ? ay0 : by0, 0,
                side < 0 ? ax : bx, side < 0 ? ay1 : by1, 0, side < 0 ? bx : ax, side < 0 ? by1 : ay1, 0,
                0, 0, 1, 1, 0, 0, 0, 0.22f);
        // its edges: the far one lit, the hinge side in shadow
        b.quad(ui.white(), fx - side * 3, y - near + 1, 0, fx, y - near, 0, fx, y + h + near, 0,
                fx - side * 3, y + h + near - 1, 0, 0, 0, 1, 1, LIGHT[0], LIGHT[1], LIGHT[2], 0.25f);
        ui.rect(side < 0 ? hingeX - 4 : hingeX, y, 4, h, Theme.withAlpha(SHADE, 0.5f));
        // the hinges and the ring
        for (float hy : new float[]{y + h * 0.16f, y + h * 0.84f}) {
            float hw = Math.min(26, dw * 0.45f);
            ui.rect(side < 0 ? hingeX - hw : hingeX, hy - 5, hw, 10, IRON);
            bevel(side < 0 ? hingeX - hw : hingeX, hy - 5, hw, 10, 2, 0.8f);
            rivet(hingeX + side * (hw - 5), hy, 3f);
        }
        float rx = hingeX + side * dw * 0.82f, ry = y + h / 2;
        ui.outline(rx - 6, ry - 2, 12, 14, 2, IRON);
        rivet(rx, ry - 3, 4);
    }

    // --- the mirror -------------------------------------------------------------------

    /**
     * A shape with an arched top: a rectangle whose top {@code arch} pixels
     * round off into half an ellipse, filled in {@code c} - row by row.
     */
    public void arch(float x, float y, float w, float h, float arch, float[] c) {
        float step = 2;
        for (float yy = 0; yy < arch; yy += step) {
            double e = 1 - (yy + step / 2) / arch;                // 1 at the top, 0 where the arch ends
            float half = (float) (w / 2 * Math.sqrt(Math.max(0, 1 - e * e)));
            ui.rect(x + w / 2 - half, y + yy, 2 * half, Math.min(step, arch - yy), c);
        }
        ui.rect(x, y + arch, w, h - arch, c);
    }

    /**
     * A standing mirror in a gilded frame with an arched top; returns the
     * glass's rectangle {@code {x, y, w, h}}, to draw the reflection in.
     */
    public float[] mirror(float x, float y, float w, float h) {
        float arch = Math.min(w * 0.42f, h * 0.3f), frame = 14;
        shadow(x, y + arch * 0.3f, w, h - arch * 0.3f, 7);
        arch(x, y, w, h, arch, Theme.rgb(96, 64, 22, 1f));                       // the frame's dark edge
        arch(x + 2, y + 2, w - 4, h - 4, arch - 2, Theme.rgb(196, 146, 58, 1f)); // the gilding
        arch(x + 4, y + 3, w - 8, h * 0.45f, arch - 3, Theme.rgb(236, 196, 104, 0.55f)); // its lit upper half
        arch(x + frame - 4, y + frame - 4, w - 2 * frame + 8, h - 2 * frame + 8, arch - frame + 4,
                Theme.rgb(120, 82, 28, 1f));                                              // the inner moulding
        float gx = x + frame, gy = y + frame, gw = w - 2 * frame, gh = h - 2 * frame, ga = arch - frame;
        // the glass: dark, a little lighter towards the bottom, where the room's light falls
        int bands = 12;
        for (int i = 0; i < bands; i++) {
            float t = i / (float) (bands - 1);
            float[] c = Theme.rgb((int) (22 + 22 * t), (int) (30 + 22 * t), (int) (38 + 18 * t), 1f);
            float by = gy + gh * i / bands, bh = gh / bands + 1;
            if (by + bh <= gy + ga) {
                // inside the arch: drawn by the arch itself below
                continue;
            }
            ui.rect(gx, Math.max(by, gy + ga), gw, by + bh - Math.max(by, gy + ga), c);
        }
        arch(gx, gy, gw, ga + 1, ga, Theme.rgb(22, 30, 38, 1f));
        // a sheen across it, corner to corner
        Batch b = batch();
        float[] sheen = {1f, 1f, 1f, 0.05f};
        b.quad(ui.white(), gx + gw * 0.15f, gy + ga * 0.4f, 0, gx + gw * 0.42f, gy + ga * 0.4f, 0,
                gx + gw * 0.05f, gy + gh * 0.75f, 0, gx, gy + gh * 0.75f, 0, 0, 0, 1, 1,
                sheen[0], sheen[1], sheen[2], sheen[3]);
        b.quad(ui.white(), gx + gw * 0.55f, gy + ga * 0.5f, 0, gx + gw * 0.62f, gy + ga * 0.5f, 0,
                gx + gw * 0.2f, gy + gh * 0.95f, 0, gx + gw * 0.13f, gy + gh * 0.95f, 0, 0, 0, 1, 1,
                1, 1, 1, 0.035f);
        // a finial at the arch's crown
        ui.diamond(x + w / 2, y + 3, 9, Theme.rgb(236, 196, 104, 1f));
        ui.diamond(x + w / 2, y + 3, 5, Theme.rgb(140, 96, 34, 1f));
        return new float[]{gx, gy, gw, gh};
    }

    // --- controls ----------------------------------------------------------------------

    /**
     * A button carved out of a board: raised oak with a bevelled edge (sunk
     * in, and its lettering gold, while {@code on}), lighter under the pointer;
     * returns whether it was clicked this frame.
     */
    public boolean button(String label, float x, float y, float w, float h, boolean on, boolean enabled) {
        boolean hot = enabled && ui.hover(x, y, w, h);
        if (!on) shadow(x, y, w, h, 3);
        wood(x, y, w, h, on ? 0.48f : hot ? 0.98f : 0.80f, false);
        if (on) sunken(x, y, w, h, 3, 1f);
        else bevel(x, y, w, h, 3, 1f);
        Font f = h >= 40 ? heading : body;
        if (f.width(label) > w - 6) f = small;
        float[] c = !enabled ? Theme.withAlpha(CREAM, 0.35f) : on ? GOLD : hot ? Theme.rgb(255, 244, 220, 1f) : CREAM;
        float tx = x + (w - f.width(label)) / 2, ty = y + (h - f.lineHeight()) / 2 + (on ? 1 : 0);
        ui.text(f, label, tx + 1, ty + 1, Theme.withAlpha(SHADE, 0.7f));
        ui.text(f, label, tx, ty, c);
        return enabled && ui.clicked(x, y, w, h);
    }

    /** A small square iron knob with a sign on it ("<", ">"); returns whether it was clicked this frame. */
    public boolean knob(String sign, float x, float y, float s, boolean enabled) {
        boolean hot = enabled && ui.hover(x, y, s, s);
        shadow(x, y, s, s, 2);
        ui.rect(x, y, s, s, hot ? Theme.rgb(78, 78, 84, 1f) : enabled ? Theme.rgb(58, 58, 64, 1f) : Theme.rgb(44, 42, 42, 1f));
        bevel(x, y, s, s, 2, 1f);
        float[] c = !enabled ? Theme.withAlpha(CREAM, 0.25f) : hot ? GOLD : CREAM;
        ui.text(body, sign, x + (s - body.width(sign)) / 2, y + (s - body.lineHeight()) / 2 - 1, c);
        return enabled && ui.clicked(x, y, s, s);
    }

    /**
     * A round gem of colour {@code rgb} (0xRRGGBB; negative: none, the
     * bezel's own dark) set in an iron bezel, {@code r} round, with a glint;
     * dimmed when not {@code lit}.
     */
    public void gem(float cx, float cy, float r, int rgb, boolean lit) {
        Batch b = batch();
        b.rect(disc, cx - r - 1, cy - r + 1, 2 * r + 2, 2 * r + 2, 0, 0, 1, 1, 0, 0, 0, 0.45f);
        b.rect(rivet, cx - r, cy - r, 2 * r, 2 * r, 0, 0, 1, 1, 1, 1, 1, 1);
        float ir = r - 3;
        if (rgb >= 0) {
            float k = lit ? 1f : 0.62f;
            b.rect(disc, cx - ir, cy - ir, 2 * ir, 2 * ir, 0, 0, 1, 1,
                    ((rgb >> 16) & 255) / 255f * k, ((rgb >> 8) & 255) / 255f * k, (rgb & 255) / 255f * k, 1);
        } else {
            b.rect(disc, cx - ir, cy - ir, 2 * ir, 2 * ir, 0, 0, 1, 1, 0.16f, 0.12f, 0.10f, 1);
        }
        float g = Math.max(2, ir * 0.38f);
        b.rect(disc, cx - ir * 0.45f - g / 2, cy - ir * 0.45f - g / 2, g, g, 0, 0, 1, 1, 1, 1, 1, lit ? 0.75f : 0.3f);
    }

    /** A small iron padlock centred on ({@code cx}, {@code cy}): a colour that is not unlocked. */
    public void padlock(float cx, float cy) {
        float[] dark = Theme.rgb(24, 22, 22, 1f), iron = Theme.rgb(150, 146, 140, 1f), lit = Theme.rgb(220, 214, 200, 1f);
        ui.outline(cx - 4, cy - 8, 8, 8, 2, dark);
        ui.outline(cx - 3, cy - 7, 6, 7, 1, iron);
        ui.rect(cx - 6, cy - 2, 12, 9, dark);
        ui.rect(cx - 5, cy - 1, 10, 7, iron);
        ui.rect(cx - 5, cy - 1, 10, 1, lit);
        ui.rect(cx - 1, cy + 1, 2, 3, dark);
    }

    /** A tiny bottle of colour {@code rgb} at ({@code x}, {@code y}) (its foot's left): a dye she carries. */
    public void bottle(float x, float y, int rgb) {
        float[] glass = Theme.rgb(210, 226, 232, 1f), c = Theme.rgb((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 1f);
        ui.rect(x - 1, y - 11, 9, 12, Theme.withAlpha(SHADE, 0.6f));
        ui.rect(x, y - 7, 7, 7, c);
        ui.rect(x + 2, y - 10, 3, 3, glass);
        ui.rect(x + 2, y - 11, 3, 1, Theme.rgb(160, 110, 60, 1f));
        ui.rect(x + 1, y - 6, 1, 2, Theme.withAlpha(LIGHT, 0.7f));
    }

    /** Lettering carved into the wood and gilded: a dark cut below and right of it, a lit edge above and left. */
    public void carved(Font f, String s, float x, float y, float[] c) {
        ui.text(f, s, x + 2, y + 2, Theme.withAlpha(SHADE, 0.85f));
        ui.text(f, s, x - 1, y - 1, Theme.withAlpha(LIGHT, 0.35f));
        ui.text(f, s, x, y, c);
    }

    /** {@link #carved}, centred on {@code cx}. */
    public void carvedCentred(Font f, String s, float cx, float y, float[] c) {
        carved(f, s, cx - f.width(s) / 2, y, c);
    }

    // --- the textures (pure: tested) -----------------------------------------------------

    /**
     * Oak, {@code n} square and seamless, its grain running across: long
     * streaks of lighter and darker wood bent by a slow wobble, a fine
     * striping, and here and there a darker knot ring.
     */
    public static int[] woodPixels(int n, long seed) {
        int[] argb = new int[n * n];
        double[] dark = {54, 32, 17}, mid = {104, 66, 36}, light = {146, 100, 58};
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double u = x / (double) n, v = y / (double) n;
                double wobble = 0.06 * noise(u * 3, v * 2, 3, 2, seed) + 0.02 * noise(u * 9, v * 5, 9, 5, seed + 1);
                double rings = 0.5 + 0.5 * Math.sin(2 * Math.PI * (v + wobble) * 14);
                double streak = noise(u * 2, v * 24, 2, 24, seed + 2);
                double fine = noise(u * 64, v * 96, 64, 96, seed + 3);
                double knot = knot(u, v, 0.31, 0.27) + knot(u, v, 0.78, 0.71);
                double t = clamp01(0.30 + 0.38 * streak + 0.22 * Math.pow(rings, 3) + 0.12 * (fine - 0.5) - 0.55 * knot);
                double[] c = t < 0.5 ? lerp(dark, mid, t / 0.5) : lerp(mid, light, (t - 0.5) / 0.5);
                argb[y * n + x] = opaque(c);
            }
        }
        return argb;
    }

    /** A dark ring round ({@code kx}, {@code ky}), where the grain bends round a knot: 0 to 1. */
    private static double knot(double u, double v, double kx, double ky) {
        double dx = wrap(u - kx), dy = wrap(v - ky) * 3.2;
        double d = Math.hypot(dx, dy);
        return Math.exp(-Math.pow((d - 0.025) / 0.012, 2)) * 0.8 + Math.exp(-Math.pow(d / 0.012, 2));
    }

    /** Parchment, {@code n} square and seamless: cream, mottled with darker stains and fibres. */
    public static int[] parchmentPixels(int n, long seed) {
        int[] argb = new int[n * n];
        double[] stain = {196, 162, 112}, cream = {240, 224, 186};
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double u = x / (double) n, v = y / (double) n;
                double blot = noise(u * 3, v * 3, 3, 3, seed) * 0.6 + noise(u * 7, v * 7, 7, 7, seed + 1) * 0.4;
                double fibre = noise(u * 48, v * 12, 48, 12, seed + 2);
                double t = clamp01(0.80 - 0.45 * Math.max(0, blot - 0.45) - 0.10 * (fibre - 0.5));
                argb[y * n + x] = opaque(lerp(stain, cream, t));
            }
        }
        return argb;
    }

    /** A domed iron rivet, {@code n} across: dark, lit at its top left, round and smoothed at its edge. */
    public static int[] rivetPixels(int n) {
        int[] argb = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r = Math.hypot(dx, dy);
                double a = clamp01((1 - r) * n / 2);
                double nz = Math.sqrt(Math.max(0, 1 - r * r));
                double lit = clamp01(-0.5 * dx - 0.55 * dy + 0.65 * nz);
                double g = 28 + 110 * Math.pow(lit, 2.2);
                double spec = Math.pow(clamp01(lit), 18) * 120;
                argb[y * n + x] = argb(a, g + spec, g + spec * 0.95, g * 1.06 + spec * 0.9);
            }
        }
        return argb;
    }

    /** A white disc, {@code n} across, its edge smoothed: tinted when drawn. */
    public static int[] discPixels(int n) {
        int[] argb = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double a = clamp01((1 - Math.hypot(dx, dy)) * n / 2);
                argb[y * n + x] = argb(a, 255, 255, 255);
            }
        }
        return argb;
    }

    static int[] transpose(int[] argb, int n) {
        int[] out = new int[argb.length];
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) out[x * n + y] = argb[y * n + x];
        return out;
    }

    /**
     * Smooth value noise in [0, 1] at ({@code x}, {@code y}), repeating every
     * {@code px} across and {@code py} down (whole numbers), so a texture of
     * it tiles.
     */
    static double noise(double x, double y, int px, int py, long seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        double sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
        double a = lattice(Math.floorMod(x0, px), Math.floorMod(y0, py), seed);
        double b = lattice(Math.floorMod(x0 + 1, px), Math.floorMod(y0, py), seed);
        double c = lattice(Math.floorMod(x0, px), Math.floorMod(y0 + 1, py), seed);
        double d = lattice(Math.floorMod(x0 + 1, px), Math.floorMod(y0 + 1, py), seed);
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sy;
    }

    private static double lattice(int x, int y, long seed) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xBF58476D1CE4E5B9L + y * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double wrap(double d) {
        return d - Math.rint(d);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static int opaque(double[] c) {
        return argb(1, c[0], c[1], c[2]);
    }

    private static int argb(double a, double r, double g, double b) {
        int ia = (int) Math.round(clamp01(a) * 255);
        return (ia << 24) | (byte8(r) << 16) | (byte8(g) << 8) | byte8(b);
    }

    private static int byte8(double v) {
        return (int) Math.round(Math.max(0, Math.min(255, v)));
    }

    @Override
    public void close() {
        woodAcross.close();
        woodDown.close();
        parchment.close();
        rivet.close();
        disc.close();
        title.close();
        heading.close();
        body.close();
        small.close();
    }
}
