package com.larsons.game.scene;

import com.larsons.game.core.Game;
import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Texture;
import com.larsons.game.grade.Colours;
import com.larsons.game.grade.Grading;
import com.larsons.game.grade.Preset;
import com.larsons.game.input.Input;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

/**
 * Pause, Colours: the colour grade ({@link Grading}). The page stands at the
 * right of the screen so the game shows beside it, graded as it is changed.
 *
 * <ul>
 *   <li><b>Presets</b> along the top - the art as drawn, Gameboy, N64, one
 *       colour and the rest ({@link Preset}).</li>
 *   <li>The <b>128 colours</b> as swatches, by hue: each shows what it is
 *       shown as now, with a corner of what it was drawn as when that has
 *       changed. Click one and turn it on the <b>colour wheel</b> beside
 *       them (round for its hue, out from the middle for how strong) and the
 *       slider under the wheel (how bright).</li>
 *   <li><b>All together</b>: hue, saturation, brightness and contrast for
 *       every colour at once; <b>One hue</b> gives them all the hue the Hue
 *       slider is at.</li>
 *   <li><b>Colours</b>: how many the palette has, from all 128 down to 2 -
 *       the nearest are merged - and whether every pixel is <b>snapped</b>
 *       to the palette (always, with fewer than 128) or the light and mist
 *       keep their own shades.</li>
 *   <li><b>Reset all</b>, and three places to <b>keep</b> a grade of your
 *       own ({@code config/palettes/}).</li>
 * </ul>
 */
final class ColoursPanel implements AutoCloseable {

    private static final float[] WHITE = {1, 1, 1, 1};
    private static final int WHEEL = 192;

    private final Game game;
    private Texture wheel;
    private int selected = -1;
    private boolean turning;
    private List<Integer> order;

    ColoursPanel(Game game) {
        this.game = game;
    }

    /** Where the page stands: the right of the screen, the game showing to its left. */
    static float[] box(Ui ui) {
        float w = Math.min(640, ui.width() * 0.5f), h = Math.min(740, ui.height() - 24);
        return new float[]{ui.width() - w - 12, (ui.height() - h) / 2, w, h};
    }

    /** Leaving the page: the grade is kept. */
    void leave() {
        turning = false;
        game.saveGrading();
    }

    void draw(Ui ui, float x, float y, float w, float h) {
        Grading g = game.grading();
        Input in = ui.input();
        if (order == null) order = g.order();
        float cy = y;

        // --- the presets -----------------------------------------------------------------
        Preset[] presets = Preset.values();
        int cols = 4;
        float gap = 5, bw = (w - (cols - 1) * gap) / cols, bh = 25;
        Preset about = Preset.byKey(g.preset());
        for (int i = 0; i < presets.length; i++) {
            float bx = x + (i % cols) * (bw + gap), by = cy + (i / cols) * (bh + gap);
            boolean on = presets[i].key().equals(g.preset());
            if (ui.hover(bx, by, bw, bh)) about = presets[i];
            if (ui.button(Ui.fit(ui.body, presets[i].label(), bw - 8), bx, by, bw, bh, on, true)) {
                g.apply(presets[i]);
                selected = -1;
            }
        }
        cy += ((presets.length + cols - 1) / cols) * (bh + gap);
        ui.text(ui.small, Ui.fit(ui.small, about != null ? about.about() : "Your own grade.", w), x, cy, Theme.HINT);
        cy += ui.small.lineHeight() + 7;

        // --- the 128 colours, and the wheel ------------------------------------------------
        int[] shown = g.shown();
        int per = 16, rows = (g.size() + per - 1) / per;
        float wheelSize = Math.min(176, w * 0.3f);
        float cell = Math.min(24, (w - wheelSize - 18) / per);
        float sx = x, sy = cy;
        int hot = -1;
        for (int k = 0; k < order.size(); k++) {
            int i = order.get(k);
            float cx = sx + (k % per) * cell, cyy = sy + (k / per) * cell;
            ui.rect(cx, cyy, cell - 2, cell - 2, Colours.rgba(shown[i], 1f));
            if (shown[i] != g.original(i)) {
                // what it was drawn as, in its corner
                ui.rect(cx, cyy, (cell - 2) * 0.42f, (cell - 2) * 0.42f, Colours.rgba(g.original(i), 1f));
                ui.outline(cx, cyy, (cell - 2) * 0.42f + 1, (cell - 2) * 0.42f + 1, 1, Theme.SOOT);
            }
            if (ui.hover(cx, cyy, cell, cell)) hot = i;
            if (i == selected) {
                ui.outline(cx - 2, cyy - 2, cell + 2, cell + 2, 2, WHITE);
                ui.outline(cx - 3, cyy - 3, cell + 4, cell + 4, 1, Theme.SOOT);
            }
            if (ui.clicked(cx, cyy, cell - 2, cell - 2)) selected = i;
        }
        float wx = x + w - wheelSize, wy = sy;
        wheel(ui, g, in, wx, wy, wheelSize);
        cy += Math.max(rows * cell, wheelSize + 30) + 6;

        // the colour picked: what it was, what it is now
        int at = hot >= 0 ? hot : selected;
        if (at >= 0) {
            ui.rect(x, cy + 2, 18, 18, Colours.rgba(g.original(at), 1f));
            ui.rect(x + 22, cy + 2, 18, 18, Colours.rgba(shown[at], 1f));
            ui.outline(x, cy + 2, 40, 18, 1, Theme.SOOT);
            String s = String.format(Locale.ROOT, "Colour %d: drawn #%06X, shown #%06X", at + 1, g.original(at), shown[at]);
            ui.text(ui.small, s, x + 48, cy + 3, Theme.ITEM);
        } else {
            ui.text(ui.small, "Click a colour, then turn it on the wheel.", x, cy + 3, Theme.ITEM_DISABLED);
        }
        if (selected >= 0 && ui.button("Reset it", x + w - 96, cy - 2, 96, 24, false, g.edited(selected)
                != g.original(selected))) {
            g.reset(selected);
        }
        cy += 30;

        // --- all together ----------------------------------------------------------------
        float lw = 96, sw = w - lw - 150;
        double hue = g.hue(), sat = g.saturation(), bri = g.brightness(), con = g.contrast();
        boolean one = g.colourise();
        ui.text(ui.small, one ? "Hue" : "Hue turn", x, cy + 3, Theme.ITEM);
        double hv = one ? hue / 360 : (hue + 180) / 360;
        hv = ui.slider(x + lw, cy, sw, 20, hv);
        hue = one ? Math.round(hv * 360) : Math.round(hv * 360 - 180);
        ui.text(ui.small, (one ? "" : hue > 0 ? "+" : "") + (int) hue + "°", x + lw + sw + 12, cy + 3, Theme.HINT);
        if (ui.button("One hue", x + w - 86, cy - 2, 86, 24, one, true)) {
            one = !one;
            hue = one ? 38 : 0;
        }
        cy += 27;
        sat = named(ui, "Saturation", sat, 0, 2, x, cy, lw, sw);
        cy += 27;
        bri = named(ui, "Brightness", bri, 0.4, 1.6, x, cy, lw, sw);
        cy += 27;
        con = named(ui, "Contrast", con, 0.4, 1.6, x, cy, lw, sw);
        if (ui.button("Level", x + w - 86, cy - 29, 86, 24, false, sat != 1 || bri != 1 || con != 1 || hue != 0
                || one)) {
            hue = 0;
            sat = bri = con = 1;
            one = false;
        }
        cy += 32;
        g.shift(hue, sat, bri, con, one);

        // --- how many colours --------------------------------------------------------------
        ui.text(ui.small, "Colours", x, cy + 3, Theme.ITEM);
        int max = g.size();
        // (the slider's left end is all of them: fewer to the right, the last steps one at a time)
        double pos = Math.log(max / (double) g.count()) / Math.log(max / (double) Grading.FEWEST);
        double moved = ui.slider(x + lw, cy, sw, 20, pos);
        int count = g.count();
        if (Math.abs(moved - pos) > 1e-6) {
            count = (int) Math.round(max / Math.pow(max / (double) Grading.FEWEST, moved));
        }
        if (ui.button("-", x + lw + sw + 60, cy - 2, 26, 24, false, count > Grading.FEWEST)) count--;
        if (ui.button("+", x + lw + sw + 90, cy - 2, 26, 24, false, count < max)) count++;
        g.setCount(count);
        ui.text(ui.small, String.valueOf(g.count()), x + lw + sw + 12, cy + 3, g.count() < max ? Theme.ACCENT : Theme.HINT);
        cy += 28;
        boolean forced = g.count() < max;
        if (ui.button(g.snap() ? "Every pixel snapped to the palette" : "Smooth: light and mist keep their shades",
                x, cy, w - 150, 26, g.snap(), !forced)) {
            g.setSnap(!g.snap());
        }
        ui.text(ui.small, g.distinct() + " shown", x + w - 140, cy + 5, Theme.HINT);
        cy += 36;

        // --- reset, and the three places to keep one ----------------------------------------
        if (ui.button("Reset all", x, cy, 110, 28, false, !g.identity())) {
            g.resetAll();
            selected = -1;
        }
        float kx = x + 126;
        ui.text(ui.small, "Keep:", kx, cy + 6, Theme.ITEM);
        kx += 46;
        for (int n = 1; n <= 3; n++) {
            Path file = kept(n);
            boolean there = Files.isRegularFile(file);
            if (ui.button("Save " + n, kx, cy, 68, 28)) {
                g.save(file);
                game.toast("Colours kept in place " + n, Theme.OK);
            }
            if (ui.button("Load " + n, kx + 72, cy, 68, 28, false, there)) {
                g.load(file);
                selected = -1;
            }
            kx += 150;
        }
        cy += 38;
        ui.text(ui.small, "The game beside this page shows the grade. The pause menu itself is never graded.", x, cy,
                Theme.ITEM_DISABLED);
        if (!in.mouseDown(GLFW_MOUSE_BUTTON_LEFT)) turning = false;
    }

    private static double named(Ui ui, String label, double value, double lo, double hi, float x, float y, float lw,
                                float sw) {
        ui.text(ui.small, label, x, y + 3, Theme.ITEM);
        double v = ui.slider(x + lw, y, sw, 20, (value - lo) / (hi - lo));
        double out = Math.round((lo + v * (hi - lo)) * 100) / 100.0;
        ui.text(ui.small, String.format(Locale.ROOT, "×%.2f", out), x + lw + sw + 12, y + 3, Theme.HINT);
        return out;
    }

    /** The wheel for the colour picked: round it for the hue, out from its middle for how strong; under it, how bright. */
    private void wheel(Ui ui, Grading g, Input in, float x, float y, float size) {
        if (wheel == null) wheel = Texture.upload(PixelData.of(wheelPixels(WHEEL), WHEEL, WHEEL), false);
        boolean live = selected >= 0;
        float[] hsv = live ? Colours.hsv(g.edited(selected)) : new float[]{0, 0, 1};
        float v = live ? Math.max(0.15f, hsv[2]) : 0.45f;
        float cx = x + size / 2, cy = y + size / 2, r = size / 2;
        ui.disc(cx, cy, r + 3, Theme.SOOT);
        ui.disc(cx, cy, r + 2, Theme.BRASS);
        ui.image(wheel, x, y, size, size, new float[]{v, v, v, 1});
        double mx = in.mouseX() - cx, my = in.mouseY() - cy, d = Math.hypot(mx, my);
        if (live && in.mousePressed(GLFW_MOUSE_BUTTON_LEFT) && d <= r + 2) turning = true;
        if (live && turning && in.mouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
            double hue = (Math.toDegrees(Math.atan2(my, mx)) + 360) % 360, sat = Math.min(1, d / r);
            g.set(selected, Colours.rgb(hue, sat, hsv[2]));
            hsv = Colours.hsv(g.edited(selected));
        }
        if (live) {
            double a = Math.toRadians(hsv[0]);
            float px = cx + (float) (Math.cos(a) * hsv[1] * r), py = cy + (float) (Math.sin(a) * hsv[1] * r);
            ui.disc(px, py, 7, Theme.SOOT);
            ui.disc(px, py, 5.5f, WHITE);
            ui.disc(px, py, 4, Colours.rgba(g.edited(selected), 1f));
        }
        // how bright
        float by = y + size + 8;
        double nv = ui.slider(x, by, size, 18, live ? hsv[2] : 0);
        if (live && Math.abs(nv - hsv[2]) > 1e-4) g.set(selected, Colours.rgb(hsv[0], hsv[1], nv));
    }

    /** A disc of every hue round it, white at its middle: straight ARGB. */
    static int[] wheelPixels(int n) {
        int[] argb = new int[n * n];
        double c = (n - 1) / 2.0, r = n / 2.0 - 1;
        for (int yy = 0; yy < n; yy++) {
            for (int xx = 0; xx < n; xx++) {
                double dx = xx - c, dy = yy - c, d = Math.hypot(dx, dy);
                double edge = Math.max(0, Math.min(1, r - d + 0.5));
                int rgb = Colours.rgb((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360, Math.min(1, d / r), 1);
                argb[yy * n + xx] = ((int) Math.round(edge * 255) << 24) | rgb;
            }
        }
        return argb;
    }

    private Path kept(int n) {
        return game.settings().config.resolve("palettes").resolve(n + ".json");
    }

    @Override
    public void close() {
        if (wheel != null) wheel.close();
        wheel = null;
    }
}
