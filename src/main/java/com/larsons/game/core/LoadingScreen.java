package com.larsons.game.core;

import com.larsons.game.gfx.Font;
import com.larsons.game.gfx.GpuInfo;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.util.List;

/**
 * The loading screen: everything {@link Boot} loads as the game starts, and
 * where it has got, in the main menu's colours.
 *
 * <pre>
 *   Larson's Game                                 +- Loading now ------------+
 *   Loading - 9 of 19 steps done                  | Indexing the character   |
 *                                                 | sprite sheets            |
 *   GRAPHICS                                      | sprites_masculine/hair/..|
 *   * Opening the window and OpenGL  llvmpipe  0.31 s | ######.... 211 of 859  |
 *   * Compiling the sprite shader ...   4 fonts  0.22 s |                      |
 *   SPRITES                                       | Also running             |
 *   o Indexing the character sprite sheets  3.1 s | Drawing the stand-in 87% |
 *   . Drawing the stand-in character              |                          |
 *   ...                                           | Just loaded              |
 *                                                 | 2,056 sheets  hair/...   |
 *   34% ##########...........................................  12.4 s     |
 *   GPU: ...                                      +--------------------------+
 * </pre>
 *
 * <p>On the left, every step in order under its heading - done (green),
 * working (gold), still to come (grey) or failed (red) - with what it found
 * and how long it took; on the right, the step at work, what it is on and how
 * far through it is, the background work going on beside it and the last
 * things loaded; along the bottom, the whole boot, weighted by how long each
 * step took last time. A step that fails stays on screen with what went
 * wrong.
 */
public final class LoadingScreen {

    private static final float[] DONE = Theme.OK;
    private static final float[] WORKING = Theme.ACCENT;
    private static final float[] WAITING = Theme.ITEM_DISABLED;
    private static final float[] FAILED = Theme.WARNING;
    private static final float[] FAINT = Theme.rgb(150, 154, 172, 1f);
    private static final float[] TRACK = Theme.rgb(36, 38, 56, 1f);

    private final Ui ui;
    private final Boot boot;
    private final GpuInfo gpu;
    private final long born = System.nanoTime();
    /** Everything is drawn this opaque: 1 while loading, down to 0 as the game fades in under it. */
    private float alpha = 1;

    public LoadingScreen(Ui ui, Boot boot, GpuInfo gpu) {
        this.ui = ui;
        this.boot = boot;
        this.gpu = gpu;
    }

    /**
     * Draw it, {@code fade} of the way in: 1 covers everything; on the way to
     * 0 the writing goes first and then the backdrop, so the game shows
     * through a clean screen rather than through the lists. Between {@link
     * Ui#begin} and {@link Ui#end}.
     */
    public void draw(float fade) {
        float f = Math.max(0, Math.min(1, fade));
        float w = ui.width(), h = ui.height();
        ui.rect(0, 0, w, h, Theme.withAlpha(Theme.BACKGROUND, Math.min(1, 2 * f)));
        alpha = Math.max(0, 2 * f - 1);
        if (alpha <= 0) return;

        float m = Math.max(24, Math.min(64, w * 0.05f));
        float panelW = Math.max(280, Math.min(460, w * 0.34f));
        float panelX = w - m - panelW;
        float top = Math.max(20, h * 0.06f);
        float barY = h - 58;

        // the title, as on the main menu
        ui.textShadowed(ui.title, "Larson's Game", m, top, c(Theme.TITLE));
        float y = top + ui.title.lineHeight() + 2;
        Boot.Status failure = boot.failure();
        String headline = failure != null ? "Could not finish loading"
                : boot.finished() ? "Ready"
                : "Loading - " + boot.doneCount() + " of " + boot.steps().size() + " steps done";
        ui.text(ui.body, headline, m + 4, y, c(failure != null ? FAILED : Theme.ACCENT));
        y += ui.body.lineHeight() + 22;

        drawSteps(m, y, panelX - 40, barY - 40);
        drawPanel(panelX, top, panelW, barY - 30 - top, failure);
        drawBar(m, barY, w - 2 * m);
    }

    // --- the steps, on the left ----------------------------------------------------------

    private void drawSteps(float x, float top, float right, float bottom) {
        List<Boot.Status> steps = boot.steps();
        int groups = 0;
        Boot.Group last = null;
        for (Boot.Status s : steps) {
            if (s.step.group != last) groups++;
            last = s.step.group;
        }
        Font font = ui.small;
        float row = clamp((bottom - top) / (steps.size() + groups * 1.25f), 14, 24);
        float textY = (row - font.lineHeight()) / 2;

        // the titles' column is as wide as the longest title, the rest for what each found
        float titleX = x + 20;
        float titles = 0;
        for (Boot.Status s : steps) titles = Math.max(titles, font.width(s.title()));
        float noteX = Math.min(titleX + titles + 18, x + (right - x) * 0.62f);
        float timeW = font.width("00.00 s");

        float y = top;
        last = null;
        for (Boot.Status s : steps) {
            if (s.step.group != last) {
                if (last != null) y += row * 0.25f;
                ui.text(font, s.step.group.label.toUpperCase(), x, y + textY, c(Theme.withAlpha(Theme.ACCENT, 0.7f)));
                y += row;
                last = s.step.group;
            }
            float cy = y + row / 2;
            marker(x + 6, cy, s.phase());
            float[] col = switch (s.phase()) {
                case DONE -> Theme.HINT;
                case RUNNING -> WORKING;
                case WAITING -> WAITING;
                case FAILED -> FAILED;
            };
            ui.text(font, fit(font, s.title(), noteX - titleX - 12, false), titleX, y + textY, c(col));
            if (s.phase() != Boot.Phase.WAITING) {
                String time = seconds(s.seconds());
                ui.text(font, time, right - font.width(time), y + textY, c(s.phase() == Boot.Phase.RUNNING ? WORKING : WAITING));
            }
            String note = switch (s.phase()) {
                case DONE -> s.summary();
                case RUNNING -> s.total() > 0 ? count(s) : firstLine(s.detail());
                case FAILED -> s.error();
                case WAITING -> "";
            };
            if (!note.isEmpty()) {
                float[] nc = s.phase() == Boot.Phase.FAILED ? FAILED : FAINT;
                ui.text(font, fit(font, note, right - timeW - 14 - noteX, false), noteX, y + textY, c(nc));
            }
            y += row;
        }
    }

    /** A step's marker: a diamond, filled when done, pulsing while it works, an outline still to come. */
    private void marker(float cx, float cy, Boot.Phase phase) {
        switch (phase) {
            case DONE -> ui.diamond(cx, cy, 5, c(DONE));
            case FAILED -> ui.diamond(cx, cy, 5, c(FAILED));
            case WAITING -> ui.diamondOutline(cx, cy, 5, 1.2f, c(WAITING));
            case RUNNING -> {
                float pulse = 0.55f + 0.45f * (float) Math.sin((System.nanoTime() - born) / 1e9 * 6);
                ui.diamond(cx, cy, 7, c(Theme.withAlpha(WORKING, 0.25f * pulse)));
                ui.diamond(cx, cy, 5, c(Theme.withAlpha(WORKING, 0.5f + 0.5f * pulse)));
            }
        }
    }

    // --- the step at work, on the right ------------------------------------------------

    private void drawPanel(float x, float y, float w, float h, Boot.Status failure) {
        ui.rect(x, y, w, h, c(Theme.PANEL));
        ui.outline(x, y, w, h, 1, c(Theme.PANEL_EDGE));
        float pad = 20, in = w - 2 * pad, cx = x + pad, bottom = y + h - pad;
        float cy = y + pad;

        if (failure != null) {
            cy = heading("Could not finish loading", cx, cy, FAILED);
            cy += ui.paragraph(ui.large, failure.title(), cx, cy, in, c(Theme.TITLE)) + 8;
            cy += ui.paragraph(ui.body, failure.error(), cx, cy, in, c(FAILED)) + 14;
            ui.paragraph(ui.small, "The console has the details. Press any key, or close the window, to quit.",
                    cx, cy, in, c(Theme.HINT));
            return;
        }

        Boot.Status now = boot.current();
        cy = heading(now == null ? "Loaded" : "Loading now", cx, cy, Theme.ACCENT);
        if (now == null) {
            cy += ui.paragraph(ui.large, "Everything is in", cx, cy, in, c(Theme.TITLE)) + 6;
            ui.text(ui.small, String.format("in %.1f seconds", boot.seconds()), cx, cy, c(FAINT));
            cy += ui.small.lineHeight() + 18;
        } else {
            cy += ui.paragraph(ui.large, now.title(), cx, cy, in, c(Theme.TITLE)) + 6;
            // what it is on (a path keeps its end, which says the most), and a second line if it has one
            String[] detail = now.detail().split("\n", 2);
            for (int i = 0; i < detail.length; i++) {
                if (detail[i].isEmpty()) continue;
                ui.text(ui.small, fit(ui.small, detail[i], in, i == 0), cx, cy, c(i == 0 ? Theme.HINT : FAINT));
                cy += ui.small.lineHeight() + 2;
            }
            cy += 6;
            bar(cx, cy, in, 6, now.fraction(), WORKING);
            cy += 12;
            String under = (now.total() > 0 ? count(now) + "   " : "") + seconds(now.seconds());
            double before = boot.lastSeconds(now.step);
            if (before >= 0.05) under += "   (" + seconds(before) + " last time)";
            ui.text(ui.small, under, cx, cy, c(FAINT));
            cy += ui.small.lineHeight() + 18;

            List<Boot.Status> also = boot.alsoRunning(now);
            if (!also.isEmpty()) {
                cy = heading("Also going on", cx, cy, Theme.ACCENT);
                for (Boot.Status s : also) {
                    String right = s.total() > 0 ? count(s) : seconds(s.seconds());
                    float rw = ui.small.width(right);
                    ui.text(ui.small, fit(ui.small, s.title(), in - rw - 12, false), cx, cy, c(Theme.HINT));
                    ui.text(ui.small, right, cx + in - rw, cy, c(FAINT));
                    cy += ui.small.lineHeight() + 3;
                    bar(cx, cy, in, 3, s.fraction(), WORKING);
                    cy += 10;
                }
                cy += 8;
            }
        }

        // the last things loaded, newest at the bottom, the older fading
        List<String> log = boot.log();
        if (log.isEmpty() || bottom - cy < 60) return;
        cy = heading("Just loaded", cx, cy, Theme.ACCENT);
        float line = ui.small.lineHeight() + 2;
        int fit = (int) ((bottom - cy) / line);
        int from = Math.max(0, log.size() - fit);
        for (int i = from; i < log.size(); i++) {
            float age = (log.size() - 1 - i) / (float) Math.max(1, fit);
            ui.text(ui.small, fit(ui.small, log.get(i), in, false), cx, cy,
                    c(Theme.withAlpha(FAINT, 1f - 0.7f * age)));
            cy += line;
        }
    }

    /** A small heading in capitals; returns where the text under it starts. */
    private float heading(String text, float x, float y, float[] col) {
        ui.text(ui.small, text.toUpperCase(), x, y, c(Theme.withAlpha(col, 0.8f)));
        return y + ui.small.lineHeight() + 8;
    }

    // --- the whole boot, along the bottom ------------------------------------------------

    private void drawBar(float x, float y, float w) {
        double f = boot.fraction();
        // never 100% while something is still loading
        String pct = (boot.finished() ? 100 : Math.min(99, (int) Math.floor(f * 100))) + "%";
        ui.text(ui.body, pct, x, y - ui.body.lineHeight() - 6, c(boot.failure() != null ? FAILED : Theme.ACCENT));
        String time = seconds(boot.seconds());
        ui.text(ui.small, time, x + w - ui.small.width(time), y - ui.small.lineHeight() - 7, c(FAINT));
        bar(x, y, w, 10, f, boot.failure() != null ? FAILED : WORKING);
        ui.text(ui.small, "GPU: " + gpu.describe(), x, y + 20, c(gpu.software() ? Theme.WARNING : Theme.ITEM_DISABLED));
    }

    private void bar(float x, float y, float w, float h, double fraction, float[] fill) {
        ui.rect(x, y, w, h, c(TRACK));
        float fw = (float) (w * Math.max(0, Math.min(1, fraction)));
        if (fw > 0) {
            ui.rect(x, y, fw, h, c(fill));
            ui.rect(x, y, fw, Math.max(1, h / 3), c(Theme.withAlpha(Theme.TITLE, 0.18f)));
        }
    }

    // --- bits ----------------------------------------------------------------------------

    /** {@code col} at this frame's opacity. */
    private float[] c(float[] col) {
        return alpha >= 1 ? col : Theme.withAlpha(col, col[3] * alpha);
    }

    /** "211 of 859 folders". */
    static String count(Boot.Status s) {
        return String.format("%,d of %,d", s.done(), s.total()) + (s.unit().isEmpty() ? "" : " " + s.unit());
    }

    /** The first line of a step's detail (what the list beside it shows). */
    static String firstLine(String detail) {
        int nl = detail.indexOf('\n');
        return nl < 0 ? detail : detail.substring(0, nl);
    }

    /** "0.42 s", "12.4 s". */
    static String seconds(double s) {
        return s < 10 ? String.format("%.2f s", s) : String.format("%.1f s", s);
    }

    /**
     * {@code text} cut to {@code width} with an ellipsis: its end kept ({@code
     * keepEnd}, for a path, whose end says the most) or its start.
     */
    static String fit(Font font, String text, float width, boolean keepEnd) {
        return fit(text, width, font::width, font.has('…') ? "…" : "...", keepEnd);
    }

    /** {@link #fit(Font, String, float, boolean)} measured by {@code width}. */
    static String fit(String text, float width, java.util.function.ToDoubleFunction<String> measure, String dots,
                      boolean keepEnd) {
        if (text == null || text.isEmpty() || measure.applyAsDouble(text) <= width) return text == null ? "" : text;
        int lo = 0, hi = text.length();
        while (lo < hi) {                      // the most characters that fit beside the dots
            int mid = (lo + hi + 1) / 2;
            String t = keepEnd ? dots + text.substring(text.length() - mid) : text.substring(0, mid) + dots;
            if (measure.applyAsDouble(t) <= width) lo = mid;
            else hi = mid - 1;
        }
        if (lo == 0) return "";
        return keepEnd ? dots + text.substring(text.length() - lo) : text.substring(0, lo).stripTrailing() + dots;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
