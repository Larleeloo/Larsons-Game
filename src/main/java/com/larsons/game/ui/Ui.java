package com.larsons.game.ui;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.Font;
import com.larsons.game.gfx.Texture;
import com.larsons.game.gfx.Window;
import com.larsons.game.input.Input;
import com.larsons.game.math.Mat4;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.opengl.GL33C.*;

/**
 * An immediate-mode UI drawn on the GPU: call {@link #begin()}, describe the
 * frame's panels, text and buttons, call {@link #end()}. A button reports a
 * click the frame it happens; nothing is retained between frames except
 * which widget has keyboard focus, which the menus keep themselves.
 *
 * <p>Coordinates are logical pixels from the top left, like the engine's
 * Java2D menus, so HiDPI displays get sharper text and the same layout.
 */
public final class Ui implements AutoCloseable {

    private final Window window;
    private final Batch batch;
    private final Texture white;
    /** The look's own textures, made once: oak, parchment, a rivet, a round disc and a cog. */
    private final Texture wood, parchment, rivet, disc, cog;
    /** The smallest: a label that would not otherwise fit, on two lines. */
    public final Font tiny;
    public final Font small;
    /** What a gauge is called, on the HUD: small, but lettered like the rest. */
    public final Font label;
    public final Font body;
    public final Font large;
    public final Font title;

    private boolean clickTaken;
    /** Where drawing (and the pointer) is kept to - a scrolled panel - or none (w &lt; 0). */
    private float clipX, clipY, clipW = -1, clipH;
    /** The lowest point drawn since {@link #measure()}. */
    private float lowest = Float.NEGATIVE_INFINITY;
    /** The slider being dragged (by where it is drawn), or {@code Long.MIN_VALUE}. */
    private long dragging = Long.MIN_VALUE;

    public Ui(Window window, Batch batch) {
        this.window = window;
        this.batch = batch;
        this.white = Texture.white();
        float s = window.scale();
        // the small print stays plain, to be read; what is said larger is in serif, as inked
        tiny = new Font(12, false, s);
        small = new Font(14, false, s);
        label = new Font(java.awt.Font.SERIF, 15, true, s);
        body = new Font(java.awt.Font.SERIF, 18, false, s);
        large = new Font(java.awt.Font.SERIF, 24, true, s);
        title = new Font(java.awt.Font.SERIF, 56, true, s);
        int n = Medieval.TILE;
        wood = Texture.tiled(com.larsons.game.gfx.PixelData.of(Medieval.woodPixels(n, 7), n, n));
        parchment = Texture.tiled(com.larsons.game.gfx.PixelData.of(Medieval.parchmentPixels(n, 11), n, n));
        rivet = Texture.upload(com.larsons.game.gfx.PixelData.of(brassRivet(32), 32, 32), false);
        disc = Texture.upload(com.larsons.game.gfx.PixelData.of(Medieval.discPixels(64), 64, 64), false);
        cog = Texture.upload(com.larsons.game.gfx.PixelData.of(cogPixels(128, 10), 128, 128), false);
    }

    /** A domed brass rivet, {@code n} across, lit from the top left. */
    static int[] brassRivet(int n) {
        int[] argb = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r = Math.hypot(dx, dy);
                double a = Math.max(0, Math.min(1, (1 - r) * n / 2));
                double nz = Math.sqrt(Math.max(0, 1 - r * r));
                double lit = Math.max(0, Math.min(1, -0.5 * dx - 0.55 * dy + 0.65 * nz));
                double k = 0.35 + 0.75 * Math.pow(lit, 1.6), spec = Math.pow(lit, 14) * 90;
                int rr = (int) Math.min(255, 196 * k + spec), gg = (int) Math.min(255, 150 * k + spec),
                        bb = (int) Math.min(255, 70 * k + spec * 0.7);
                argb[y * n + x] = ((int) Math.round(a * 255) << 24) | (rr << 16) | (gg << 8) | bb;
            }
        }
        return argb;
    }

    /**
     * A white cog, {@code n} across, with {@code teeth} teeth, a rim and a
     * hub with spokes between: tinted brass or iron when it is drawn.
     */
    static int[] cogPixels(int n, int teeth) {
        int[] argb = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                // 3 x 3 samples a pixel, for a smooth edge
                int hit = 0;
                for (int sy = 0; sy < 3; sy++) {
                    for (int sx = 0; sx < 3; sx++) {
                        double dx = (x + (sx + 0.5) / 3) / n * 2 - 1, dy = (y + (sy + 0.5) / 3) / n * 2 - 1;
                        double r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
                        double tooth = Math.cos(a * teeth);
                        double outer = tooth > 0.25 ? 0.98 : 0.80;
                        boolean solid = r <= outer && r >= 0.56;                       // the rim and its teeth
                        solid |= r <= 0.26 && r >= 0.09;                                // the hub
                        solid |= r < 0.58 && r > 0.24 && Math.abs(Math.sin(a * 2.5)) < 0.22 / Math.max(0.2, r * 2.2);
                        if (solid) hit++;
                    }
                }
                argb[y * n + x] = (Math.round(hit * 255f / 9) << 24) | 0xFFFFFF;
            }
        }
        return argb;
    }

    public Batch batch() { return batch; }

    /** Device pixels per logical pixel (2 on a Retina screen): what a font made for the UI is rasterised at. */
    public float scale() { return window.scale(); }

    public Texture white() { return white; }

    public Input input() { return window.input(); }

    public int width() { return window.width(); }

    public int height() { return window.height(); }

    /** Start drawing UI on top of whatever is on screen. */
    public void begin() {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        ortho = Mat4.orthographic(0, window.width(), window.height(), 0, -1, 1);
        batch.begin(ortho);
        clickTaken = false;
        turned = false;
    }

    private Mat4 ortho;
    private boolean turned;

    /**
     * Draw what follows turned {@code angle} radians (clockwise) and {@code
     * scale} times as large about {@code (cx, cy)} - a card fanned in the
     * hand, a card drawn at any size from the one size it is laid out at -
     * until {@link #straight()}. (The pointer is not turned with it: what is
     * drawn so is looked at, not clicked.)
     */
    public void turn(float cx, float cy, double angle, float scale) {
        batch.projection(ortho.mul(Mat4.translation(cx, cy, 0)).mul(Mat4.rotationZ(angle))
                .mul(Mat4.scale(scale, scale, 1)).mul(Mat4.translation(-cx, -cy, 0)));
        turned = true;
    }

    /** Draw straight and at its own size again. */
    public void straight() {
        if (!turned) return;
        batch.projection(ortho);
        turned = false;
    }

    public void end() {
        straight();
        unclip();
        batch.end();
        glDepthMask(true);
    }

    /**
     * Keep what is drawn from now on - and what the pointer can be over or
     * click - within {@code (x, y, w, h)}: a panel that scrolls. Until
     * {@link #unclip()}.
     */
    public void clip(float x, float y, float w, float h) {
        batch.flush();
        clipX = x;
        clipY = y;
        clipW = Math.max(0, w);
        clipH = Math.max(0, h);
        float s = (float) window.framebufferHeight() / Math.max(1, window.height());
        float sx = (float) window.framebufferWidth() / Math.max(1, window.width());
        glEnable(GL_SCISSOR_TEST);
        glScissor(Math.round(x * sx), Math.round(window.framebufferHeight() - (y + h) * s), Math.round(clipW * sx),
                Math.round(clipH * s));
    }

    /** Draw anywhere again. */
    public void unclip() {
        if (clipW < 0) return;
        batch.flush();
        glDisable(GL_SCISSOR_TEST);
        clipW = -1;
    }

    /** Start measuring how low what is drawn goes ({@link #lowest()}). */
    public void measure() {
        lowest = Float.NEGATIVE_INFINITY;
    }

    /** The lowest point drawn since {@link #measure()} (a scrolled panel's content's foot). */
    public float lowest() { return lowest; }

    // --- primitives ------------------------------------------------------------------

    public void rect(float x, float y, float w, float h, float[] c) {
        lowest = Math.max(lowest, y + h);
        batch.rect(white, x, y, w, h, 0, 0, 1, 1, c[0], c[1], c[2], c[3]);
    }

    public void outline(float x, float y, float w, float h, float t, float[] c) {
        rect(x, y, w, t, c);
        rect(x, y + h - t, w, t, c);
        rect(x, y, t, h, c);
        rect(x + w - t, y, t, h, c);
    }

    /** A texture stretched over a rectangle, tinted {@code c} (white: as it is). */
    public void image(Texture tex, float x, float y, float w, float h, float[] c) {
        batch.rect(tex, x, y, w, h, 0, 0, 1, 1, c[0], c[1], c[2], c[3]);
    }

    /**
     * A filled diamond - a square stood on its corner - centred on {@code
     * (cx, cy)}, {@code r} from the centre to each corner.
     */
    public void diamond(float cx, float cy, float r, float[] c) {
        batch.quad(white, cx, cy - r, 0, cx + r, cy, 0, cx, cy + r, 0, cx - r, cy, 0,
                0, 0, 1, 1, c[0], c[1], c[2], c[3]);
    }

    /** A diamond's border, {@code t} thick, inside its edge. */
    public void diamondOutline(float cx, float cy, float r, float t, float[] c) {
        float in = r - t * 1.41421356f;
        float[][] outer = {{cx, cy - r}, {cx + r, cy}, {cx, cy + r}, {cx - r, cy}};
        float[][] inner = {{cx, cy - in}, {cx + in, cy}, {cx, cy + in}, {cx - in, cy}};
        for (int i = 0; i < 4; i++) {
            float[] a = outer[i], b = outer[(i + 1) % 4], bi = inner[(i + 1) % 4], ai = inner[i];
            batch.quad(white, a[0], a[1], 0, b[0], b[1], 0, bi[0], bi[1], 0, ai[0], ai[1], 0,
                    0, 0, 1, 1, c[0], c[1], c[2], c[3]);
        }
    }

    /** Whether the pointer is inside the diamond at {@code (cx, cy)}. */
    public boolean hoverDiamond(float cx, float cy, float r) {
        return Math.abs(input().mouseX() - cx) + Math.abs(input().mouseY() - cy) <= r;
    }

    /**
     * A panel: stained oak in a brass frame, a rivet in each corner of a
     * large one - what every menu stands on.
     */
    public void panel(float x, float y, float w, float h) {
        lowest = Math.max(lowest, y + h);
        // the oak, dark enough to read cream lettering on
        float t = Medieval.TILE * 1.5f;
        batch.rect(wood, x, y, w, h, x / t, y / t, (x + w) / t, (y + h) / t, 0.42f, 0.38f, 0.36f, 0.97f);
        // darker toward its edges, as if set back in its frame
        float in = Math.min(18, Math.min(w, h) / 4);
        rect(x, y, w, in, Theme.withAlpha(Theme.SOOT, 0.16f));
        rect(x, y + h - in, w, in, Theme.withAlpha(Theme.SOOT, 0.22f));
        frame(x, y, w, h, w >= 150 && h >= 90 ? 4 : 3);
        if (w >= 220 && h >= 130) {
            float r = 9, o = 7;
            image(rivet, x + o, y + o, r, r, WHITE);
            image(rivet, x + w - o - r, y + o, r, r, WHITE);
            image(rivet, x + o, y + h - o - r, r, r, WHITE);
            image(rivet, x + w - o - r, y + h - o - r, r, r, WHITE);
        }
    }

    private static final float[] WHITE = {1, 1, 1, 1};

    /**
     * A brass frame {@code t} thick just inside the box: lit along its top
     * and left, in shadow along its bottom and right, a dark line either side.
     */
    public void frame(float x, float y, float w, float h, float t) {
        outline(x, y, w, h, 1, Theme.SOOT);
        outline(x + 1, y + 1, w - 2, h - 2, t, Theme.BRASS);
        rect(x + 1, y + 1, w - 2, 1, Theme.BRASS_LIGHT);
        rect(x + 1, y + 1, 1, h - 2, Theme.BRASS_LIGHT);
        rect(x + 1, y + h - 2, w - 2, 1, Theme.BRASS_DARK);
        rect(x + w - 2, y + 1, 1, h - 2, Theme.BRASS_DARK);
        outline(x + 1 + t, y + 1 + t, w - 2 - 2 * t, h - 2 - 2 * t, 1, Theme.withAlpha(Theme.SOOT, 0.7f));
    }

    /**
     * A small dark plate with a thin edge of {@code edge} (null: brass): a
     * prompt, a tooltip, a toast - what is said over the game.
     */
    public void plate(float x, float y, float w, float h, float[] edge) {
        rect(x, y, w, h, Theme.withAlpha(Theme.rgb(26, 19, 14, 1f), 0.9f));
        rect(x + 2, y + 2, w - 4, 1, Theme.withAlpha(Theme.BRASS_LIGHT, 0.10f));
        outline(x, y, w, h, 1, Theme.SOOT);
        outline(x + 1, y + 1, w - 2, h - 2, 1, edge == null ? Theme.BRASS : edge);
    }

    /** A sheet of parchment, a little darker at its edges: what is written on - a letter, a quest, a card's words. */
    public void parchment(float x, float y, float w, float h) {
        lowest = Math.max(lowest, y + h);
        float t = Medieval.TILE;
        batch.rect(parchment, x, y, w, h, x / t, y / t, (x + w) / t, (y + h) / t, 1, 1, 1, 1);
        float in = Math.min(10, Math.min(w, h) / 5);
        float[] edge = Theme.rgb(96, 62, 30, 0.22f);
        rect(x, y, w, in, edge);
        rect(x, y + h - in, w, in, edge);
        rect(x, y, in, h, edge);
        rect(x + w - in, y, in, h, edge);
        outline(x, y, w, h, 1, Theme.rgb(70, 46, 24, 0.9f));
    }

    /** Oak stained {@code c} (white: as it is): a card's body, a plank. */
    public void wood(float x, float y, float w, float h, float[] c) {
        lowest = Math.max(lowest, y + h);
        float t = Medieval.TILE * 1.5f;
        batch.rect(wood, x, y, w, h, x / t, y / t, (x + w) / t, (y + h) / t, c[0], c[1], c[2], c[3]);
    }

    /** Part of a texture - {@code (u0, v0)} to {@code (u1, v1)} - over a rectangle, tinted {@code c}. */
    public void image(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1,
                      float[] c) {
        batch.rect(tex, x, y, w, h, u0, v0, u1, v1, c[0], c[1], c[2], c[3]);
    }

    /** A round disc {@code r} in radius, tinted {@code c}. */
    public void disc(float cx, float cy, float r, float[] c) {
        image(disc, cx - r, cy - r, 2 * r, 2 * r, c);
    }

    /** A cog {@code r} in radius turned {@code angle} radians, tinted {@code c} (brass, iron). */
    public void cog(float cx, float cy, float r, double angle, float[] c) {
        float co = (float) Math.cos(angle) * r, si = (float) Math.sin(angle) * r;
        // its four corners, turned about its middle
        batch.quad(cog, cx - co + si, cy - si - co, 0, cx + co + si, cy + si - co, 0,
                cx + co - si, cy + si + co, 0, cx - co - si, cy - si + co, 0, 0, 0, 1, 1, c[0], c[1], c[2], c[3]);
    }

    /** A rivet {@code size} across with its top-left corner at {@code (x, y)}. */
    public void rivet(float x, float y, float size) {
        image(rivet, x, y, size, size, WHITE);
    }

    /**
     * A bar - health, stamina, mana, a round's progress - {@code filled} of
     * the way along in {@code colour}: a dark channel in a brass rim, the
     * fill lit along its top.
     */
    public void bar(float x, float y, float w, float h, double filled, float[] colour) {
        float f = (float) Math.max(0, Math.min(1, filled));
        rect(x, y, w, h, Theme.rgb(18, 13, 10, 0.92f));
        if (f > 0) {
            float fw = (w - 4) * f;
            rect(x + 2, y + 2, fw, h - 4, colour);
            rect(x + 2, y + 2, fw, Math.max(1, (h - 4) * 0.3f), new float[]{1, 1, 1, 0.22f * colour[3]});
            rect(x + 2, y + h - 2 - Math.max(1, (h - 4) * 0.25f), fw, Math.max(1, (h - 4) * 0.25f),
                    new float[]{0, 0, 0, 0.25f * colour[3]});
        }
        outline(x, y, w, h, 1, Theme.SOOT);
        outline(x + 1, y + 1, w - 2, h - 2, 1, Theme.withAlpha(Theme.BRASS, colour[3]));
    }

    /**
     * A gauge of her own - health, mana, stamina: a glass tube between two
     * brass rails, ruled in quarters and capped at its far end, with a round
     * boss at its head showing what it measures ({@code icon}, one of {@link
     * CardArt}'s; none: a cap there too). {@code (x, y)} is the tube's
     * top-left corner; {@code mirrored} puts the boss at its right end and
     * fills the tube from there.
     */
    public void gauge(float x, float y, float w, float h, double filled, float[] colour, String icon,
                      boolean mirrored) {
        float f = (float) Math.max(0, Math.min(1, filled)), a = colour[3];
        float[] soot = Theme.withAlpha(Theme.SOOT, 0.95f * a);
        // the rails over and under the glass
        rect(x - 2, y - 4, w + 4, h + 8, soot);
        rect(x - 1, y - 3, w + 2, 1, Theme.withAlpha(Theme.BRASS_LIGHT, a));
        rect(x - 1, y - 2, w + 2, 1, Theme.withAlpha(Theme.BRASS, a));
        rect(x - 1, y + h + 1, w + 2, 1, Theme.withAlpha(Theme.BRASS, a));
        rect(x - 1, y + h + 2, w + 2, 1, Theme.withAlpha(Theme.BRASS_DARK, a));
        // the glass, and what is in it: lit along its top, bright where it ends
        rect(x, y, w, h, Theme.rgb(24, 17, 13, 0.96f * a));
        if (f > 0) {
            float fw = w * f, fx = mirrored ? x + w - fw : x;
            rect(fx, y, fw, h, colour);
            rect(fx, y, fw, Math.max(1, h * 0.3f), new float[]{1, 1, 1, 0.26f * a});
            float low = Math.max(1, h * 0.25f);
            rect(fx, y + h - low, fw, low, new float[]{0, 0, 0, 0.28f * a});
            if (f < 1) rect(mirrored ? fx : fx + fw - 1, y, 1, h, new float[]{1, 1, 1, 0.4f * a});
        }
        for (int i = 1; i < 4; i++) {
            rect(x + Math.round(w * i / 4f), y, 1, Math.max(2, h * 0.4f), Theme.withAlpha(Theme.BRASS_LIGHT, 0.6f * a));
        }
        float cy = y + h / 2;
        for (int end = 0; end < 2; end++) {
            boolean head = (end == 0) != mirrored;                 // the boss's end
            float ex = end == 0 ? x - 6 : x + w;
            if (head && icon != null) continue;
            rect(ex - 1, y - 6, 8, h + 12, soot);
            rect(ex, y - 5, 6, h + 10, Theme.withAlpha(Theme.BRASS, a));
            rect(ex, y - 5, 6, 1, Theme.withAlpha(Theme.BRASS_LIGHT, a));
            rect(ex, y - 5, 1, h + 10, Theme.withAlpha(Theme.BRASS_LIGHT, 0.8f * a));
            rect(ex + 5, y - 4, 1, h + 9, Theme.withAlpha(Theme.BRASS_DARK, a));
            rect(ex, y + h + 4, 6, 1, Theme.withAlpha(Theme.BRASS_DARK, a));
            image(rivet, ex + 0.5f, cy - 2.5f, 5, 5, new float[]{1, 1, 1, a});
        }
        if (icon != null) {
            float r = 16, cx = mirrored ? x + w + r - 5 : x - r + 5;
            disc(cx, cy, r + 1.5f, soot);
            disc(cx, cy, r, Theme.withAlpha(Theme.BRASS_DARK, a));
            disc(cx - 0.7f, cy - 0.7f, r - 0.8f, Theme.withAlpha(Theme.BRASS_LIGHT, a));
            disc(cx, cy, r - 1.6f, Theme.withAlpha(Theme.BRASS, a));
            disc(cx, cy, r - 3.4f, soot);
            disc(cx, cy, r - 4.2f, Theme.rgb(34, 25, 19, a));
            CardArt.icon(this, icon, Math.round(cx - 12), Math.round(cy - 12), 24, a);
        }
    }

    /** Dim everything behind a modal. */
    public void scrim() {
        rect(0, 0, width(), height(), Theme.SCRIM);
    }

    public float text(Font font, String s, float x, float y, float[] c) {
        lowest = Math.max(lowest, y + font.lineHeight());
        return font.draw(batch, s, x, y, c[0], c[1], c[2], c[3]);
    }

    /** {@code s} cut short with "…" to fit {@code width} in {@code font} (as it is, if it fits). */
    public static String fit(Font font, String s, float width) {
        if (s == null || font.width(s) <= width) return s;
        String t = s;
        while (t.length() > 1 && font.width(t + "…") > width) t = t.substring(0, t.length() - 1);
        return t.stripTrailing() + "…";
    }

    public float textShadowed(Font font, String s, float x, float y, float[] c) {
        return font.drawShadowed(batch, s, x, y, c[0], c[1], c[2], c[3]);
    }

    public void textCentered(Font font, String s, float cx, float y, float[] c) {
        text(font, s, cx - font.width(s) / 2, y, c);
    }

    /**
     * Word-wrapped text within {@code width}; returns the height used.
     * Explicit newlines start new paragraphs.
     */
    public float paragraph(Font font, String s, float x, float y, float width, float[] c) {
        return paragraph(font, s, x, y, width, c, true);
    }

    /** {@link #paragraph}, each line centred within {@code width}. */
    public float paragraphCentered(Font font, String s, float x, float y, float width, float[] c) {
        centred = true;
        try {
            return paragraph(font, s, x, y, width, c, true);
        } finally {
            centred = false;
        }
    }

    private boolean centred;

    /** The height {@link #paragraph} would take, drawing nothing. */
    public float paragraphHeight(Font font, String s, float width) {
        return paragraph(font, s, 0, 0, width, null, false);
    }

    private float paragraph(Font font, String s, float x, float y, float width, float[] c, boolean draw) {
        float line = font.lineHeight() + 3;
        float space = font.width(" ");
        float cy = y;
        for (String para : s.split("\n", -1)) {
            // the line so far is para[start, end), lineWidth wide; words are split at single spaces
            int start = 0, end = 0;
            float lineWidth = 0;
            for (int i = 0; ; ) {
                int sp = para.indexOf(' ', i);
                int wordEnd = sp < 0 ? para.length() : sp;
                float wordWidth = font.width(para, i, wordEnd);
                if (end == start) {
                    start = i;
                    end = wordEnd;
                    lineWidth = wordWidth;
                } else if (lineWidth + space + wordWidth > width) {
                    if (draw) text(font, para.substring(start, end), centred ? x + (width - lineWidth) / 2 : x, cy, c);
                    cy += line;
                    start = i;
                    end = wordEnd;
                    lineWidth = wordWidth;
                } else {
                    end = wordEnd;
                    lineWidth += space + wordWidth;
                }
                if (sp < 0) break;
                i = sp + 1;
            }
            if (draw) text(font, para.substring(start, end), centred ? x + (width - lineWidth) / 2 : x, cy, c);
            cy += line;
        }
        return cy - y;
    }

    // --- interaction -----------------------------------------------------------------

    public boolean hover(float x, float y, float w, float h) {
        double mx = input().mouseX(), my = input().mouseY();
        if (clipW >= 0 && (mx < clipX || mx >= clipX + clipW || my < clipY || my >= clipY + clipH)) return false;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * Whether the left button was clicked inside the box this frame (and
     * takes the click). Every click anything in the interface takes sounds
     * here ({@code ui/click}) - a button, a knob, a wardrobe gem.
     */
    public boolean clicked(float x, float y, float w, float h) {
        if (clickTaken || !input().mousePressed(GLFW_MOUSE_BUTTON_LEFT) || !hover(x, y, w, h)) {
            return false;
        }
        clickTaken = true;
        input().consumeMouse(GLFW_MOUSE_BUTTON_LEFT);
        Sounds.play(SoundKeys.ui("click"));
        return true;
    }

    /**
     * A horizontal slider showing {@code value} (0..1): press on it and drag
     * to change it. Returns the value now - {@code value} unless it is being
     * dragged.
     */
    public double slider(float x, float y, float w, float h, double value) {
        long id = ((long) Float.floatToIntBits(x) << 32) ^ Float.floatToIntBits(y);
        Input in = input();
        if (!clickTaken && in.mousePressed(GLFW_MOUSE_BUTTON_LEFT) && hover(x - 6, y - 4, w + 12, h + 8)) {
            clickTaken = true;
            in.consumeMouse(GLFW_MOUSE_BUTTON_LEFT);
            dragging = id;
        }
        if (dragging == id) {
            if (in.mouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
                value = Math.max(0, Math.min(1, (in.mouseX() - x) / w));
            } else {
                dragging = Long.MIN_VALUE;
            }
        }
        float v = (float) Math.max(0, Math.min(1, value));
        // a brass rail with a cog riding on it, turning as it goes
        float track = Math.max(4, h * 0.3f), ty = y + (h - track) / 2;
        rect(x, ty, w, track, Theme.rgb(18, 13, 10, 0.95f));
        rect(x, ty, w * v, track, Theme.BRASS);
        rect(x, ty, w * v, 1, Theme.BRASS_LIGHT);
        outline(x, ty, w, track, 1, Theme.SOOT);
        boolean hot = dragging == id || hover(x - 6, y - 4, w + 12, h + 8);
        float r = Math.max(7, h * 0.52f);
        cog(x + w * v, y + h / 2, r + 1, v * w / r, Theme.SOOT);
        cog(x + w * v, y + h / 2, r, v * w / r, hot ? Theme.BRASS_LIGHT : Theme.BRASS);
        return value;
    }

    /**
     * A button. {@code focused} draws the keyboard-focus highlight; returns
     * whether it was clicked this frame.
     */
    public boolean button(String label, float x, float y, float w, float h, boolean focused,
                          boolean enabled) {
        boolean hot = enabled && hover(x, y, w, h);
        // a plate of dark leather in a brass rim, lit along its top; gold when it is chosen
        rect(x, y, w, h, hot || focused ? Theme.BUTTON_HOVER : Theme.BUTTON);
        rect(x + 2, y + 2, w - 4, Math.max(1, h * 0.42f), new float[]{1, 0.9f, 0.7f, hot || focused ? 0.07f : 0.04f});
        rect(x + 2, y + h - 3, w - 4, 1, Theme.withAlpha(Theme.SOOT, 0.35f));
        outline(x, y, w, h, 1, Theme.SOOT);
        outline(x + 1, y + 1, w - 2, h - 2, focused ? 2 : 1, focused ? Theme.ACCENT
                : !enabled ? Theme.BRASS_DARK : hot ? Theme.BRASS_LIGHT : Theme.BRASS);
        float[] c = !enabled ? Theme.ITEM_DISABLED : (hot || focused) ? Theme.ITEM_SELECTED : Theme.ITEM;
        // the largest font the label fits in
        Font f = h >= 44 ? large : body;
        if (f == large && f.width(label) > w - 12) f = body;
        if (f.width(label) > w - 8) f = small;
        if (f.width(label) > w - 6) f = tiny;
        String shown = fit(f, label, w - 6);
        text(f, shown, x + (w - f.width(shown)) / 2, y + (h - f.lineHeight()) / 2, c);
        return enabled && clicked(x, y, w, h);
    }

    public boolean button(String label, float x, float y, float w, float h) {
        return button(label, x, y, w, h, false, true);
    }

    @Override
    public void close() {
        tiny.close();
        small.close();
        label.close();
        body.close();
        large.close();
        title.close();
        wood.close();
        parchment.close();
        rivet.close();
        disc.close();
        cog.close();
        white.close();
    }
}
