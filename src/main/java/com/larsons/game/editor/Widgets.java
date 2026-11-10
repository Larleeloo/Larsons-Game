package com.larsons.game.editor;

import com.larsons.game.input.Input;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The level editor's controls, drawn with {@link Ui} as the menus are:
 * sliders, steppers, toggles, choices, colour swatches and text fields -
 * each a single call a frame that draws it and returns its value as the
 * pointer and the keys have left it. A slider being dragged and the text
 * field being typed in are remembered here between frames, by an id.
 */
public final class Widgets {

    /** A control's height, pixels. */
    public static final float ROW = 28;

    private String dragging;
    private String typing;
    private String buffer = "", original = "";
    private boolean committed;

    /** Whether a text field has the keys: the editor's own keys wait while it does. */
    public boolean typing() { return typing != null; }

    /** Let go of the keys (the field keeps what was typed). */
    public void stopTyping() {
        if (typing != null) committed = true;
        typing = null;
    }

    /** Whether a slider is being dragged. */
    public boolean dragging() { return dragging != null; }

    /** A label on the left of a row, {@code w} wide at most: on one line, or two smaller, or cut short (said in full when pointed at). */
    private void label(Ui ui, String label, float x, float y, float w) {
        if (ui.small.width(label) <= w - 4) {
            ui.text(ui.small, label, x, y + (ROW - ui.small.lineHeight()) / 2, Theme.ITEM);
            return;
        }
        // two lines of the small print, split at the space that evens them best
        String a = label, b = "";
        float best = Float.MAX_VALUE;
        for (int i = label.indexOf(' '); i > 0; i = label.indexOf(' ', i + 1)) {
            String l1 = label.substring(0, i), l2 = label.substring(i + 1);
            float worst = Math.max(ui.tiny.width(l1), ui.tiny.width(l2));
            if (worst < best) {
                best = worst;
                a = l1;
                b = l2;
            }
        }
        float lh = ui.tiny.lineHeight();
        boolean cut = ui.tiny.width(a) > w - 4 || ui.tiny.width(b) > w - 4;
        float top = y + (ROW - lh * (b.isEmpty() ? 1 : 2)) / 2;
        ui.text(ui.tiny, Ui.fit(ui.tiny, a, w - 4), x, top, Theme.ITEM);
        if (!b.isEmpty()) ui.text(ui.tiny, Ui.fit(ui.tiny, b, w - 4), x, top + lh, Theme.ITEM);
        if (cut && ui.hover(x, y, w, ROW)) tip(label, x, y + ROW + 2);
    }

    private void label(Ui ui, String label, float x, float y) {
        label(ui, label, x, y, 1e9f);
    }

    // --- what is said over everything else: a tip, a list dropped down ----------------------

    private String tip;
    private float tipX, tipY;

    /** Say {@code text} at {@code (x, y)} this frame, over everything (a label cut short, said in full). */
    public void tip(String text, float x, float y) {
        tip = text;
        tipX = x;
        tipY = y;
    }

    /** The choice whose list is dropped down: its id, its options, where it hangs from - or none. */
    private String dropId;
    private List<String> dropShown = List.of();
    private int dropIndex, dropScroll;
    private float dropX, dropY, dropW;
    /** A pick made from the list, for its choice to take on the next frame. */
    private String pickedId;
    private int pickedIndex;

    /** The most rows a dropped-down list shows at once (the wheel scrolls the rest). */
    static final int DROP_ROWS = 14;
    static final float DROP_ROW = 24;

    /** Whether a list is dropped down (the pointer over it is over the interface). */
    public boolean dropped() { return dropId != null; }

    /** Whether {@code (mx, my)} is over the list dropped down, if any. */
    public boolean overDrop(double mx, double my) {
        if (dropId == null) return false;
        float[] r = dropRect();
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private float[] dropRect(Ui ui) {
        float[] r = dropRect();
        // kept on the screen: above its control when there is no room below
        if (r[1] + r[3] > ui.height() - 4) r[1] = Math.max(4, dropY - ROW - r[3] - 2);
        if (r[0] + r[2] > ui.width() - 4) r[0] = Math.max(4, ui.width() - 4 - r[2]);
        return r;
    }

    private float[] dropRect() {
        int rows = Math.min(DROP_ROWS, dropShown.size());
        return new float[]{dropX, dropY, dropW, rows * DROP_ROW + 4};
    }

    /**
     * The frame's start: a list dropped down takes the pointer first - a
     * click on one of its rows picks it, anywhere else closes it; the wheel
     * over it scrolls it.
     */
    public void beginFrame(Ui ui) {
        tip = null;
        if (dropId == null) return;
        Input in = ui.input();
        float[] r = dropRect(ui);
        double mx = in.mouseX(), my = in.mouseY();
        boolean over = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
        int rows = Math.min(DROP_ROWS, dropShown.size());
        if (over && in.scroll() != 0) {
            dropScroll -= (int) Math.signum(in.scroll()) * 3;
            in.consumeScroll();
        }
        dropScroll = Math.max(0, Math.min(dropShown.size() - rows, dropScroll));
        if (in.pressed(GLFW_KEY_ESCAPE)) {
            in.consumeKey(GLFW_KEY_ESCAPE);
            dropId = null;
            return;
        }
        if (in.mousePressed(GLFW_MOUSE_BUTTON_LEFT)) {
            if (over) {
                int i = dropScroll + (int) ((my - r[1] - 2) / DROP_ROW);
                if (i >= 0 && i < dropShown.size()) {
                    pickedId = dropId;
                    pickedIndex = i;
                    com.larsons.game.audio.Sounds.play(com.larsons.game.audio.SoundKeys.ui("click"));
                }
            }
            in.consumeMouse(GLFW_MOUSE_BUTTON_LEFT);
            dropId = null;
        }
    }

    /** The frame's end: the list dropped down, and any tip, over everything. */
    public void overlay(Ui ui) {
        if (dropId != null) {
            float[] r = dropRect(ui);
            int rows = Math.min(DROP_ROWS, dropShown.size());
            ui.rect(r[0] + 3, r[1] + 3, r[2], r[3], Theme.withAlpha(Theme.SCRIM, 0.5f));
            ui.rect(r[0], r[1], r[2], r[3], Theme.withAlpha(Theme.PANEL, 1f));
            ui.outline(r[0], r[1], r[2], r[3], 1, Theme.ACCENT);
            for (int k = 0; k < rows; k++) {
                int i = dropScroll + k;
                float ry = r[1] + 2 + k * DROP_ROW;
                boolean hot = ui.hover(r[0], ry, r[2], DROP_ROW);
                if (i == dropIndex) ui.rect(r[0] + 1, ry, r[2] - 2, DROP_ROW, Theme.withAlpha(Theme.ACCENT, 0.35f));
                else if (hot) ui.rect(r[0] + 1, ry, r[2] - 2, DROP_ROW, Theme.BUTTON_HOVER);
                String t = Ui.fit(ui.small, dropShown.get(i), r[2] - 24);
                ui.text(ui.small, t, r[0] + 8, ry + (DROP_ROW - ui.small.lineHeight()) / 2,
                        i == dropIndex || hot ? Theme.TITLE : Theme.ITEM);
            }
            if (dropShown.size() > rows) {
                // where in the list it is
                float th = r[3] - 4, bar = Math.max(16, th * rows / dropShown.size());
                float by = r[1] + 2 + (th - bar) * dropScroll / Math.max(1, dropShown.size() - rows);
                ui.rect(r[0] + r[2] - 7, r[1] + 2, 4, th, Theme.BUTTON);
                ui.rect(r[0] + r[2] - 7, by, 4, bar, Theme.ACCENT);
            }
        }
        if (tip != null) {
            float w = Math.min(360, ui.small.width(tip) + 16);
            float h = ui.paragraphHeight(ui.small, tip, w - 16) + 8;
            float x = Math.min(tipX, ui.width() - w - 4), y = Math.min(tipY, ui.height() - h - 4);
            ui.rect(x, y, w, h, Theme.withAlpha(Theme.PANEL, 1f));
            ui.outline(x, y, w, h, 1, Theme.PANEL_EDGE);
            ui.paragraph(ui.small, tip, x + 8, y + 4, w - 16, Theme.TITLE);
        }
    }

    /** A slider from {@code min} to {@code max}; the value as it is dragged (or as it was). */
    public double slider(Ui ui, String id, String label, double value, double min, double max, String format,
                  float x, float y, float w) {
        return slider(ui, id, label, value, min, max, format, x, y, w, false);
    }

    /**
     * A slider; {@code stacked}, its label over its left end, its value over its right, the bar the
     * whole width (for a narrow column).
     */
    public double slider(Ui ui, String id, String label, double value, double min, double max, String format,
                  float x, float y, float w, boolean stacked) {
        Input in = ui.input();
        float lw = stacked ? 0 : w * 0.42f, bx = x + lw, bw = w - lw;
        if (stacked) ui.text(ui.small, Ui.fit(ui.small, label, w * 0.6f), x, y - 10, Theme.ITEM);
        else label(ui, label, x, y, lw);
        float t = (float) ((value - min) / (max - min));
        t = Math.max(0, Math.min(1, t));
        ui.rect(bx, y + ROW / 2 - 3, bw, 6, Theme.BUTTON);
        ui.rect(bx, y + ROW / 2 - 3, bw * t, 6, Theme.withAlpha(Theme.ACCENT, 0.7f));
        ui.rect(bx + bw * t - 4, y + 5, 8, ROW - 10, id.equals(dragging) ? Theme.ACCENT : Theme.TITLE);
        String text = String.format(format, value);
        ui.text(ui.small, text, bx + bw - ui.small.width(text), y - 10, Theme.HINT);
        if (ui.clicked(bx - 6, y, bw + 12, ROW)) dragging = id;
        if (id.equals(dragging)) {
            if (!in.mouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
                dragging = null;
            } else {
                double f = Math.max(0, Math.min(1, (in.mouseX() - bx) / bw));
                return min + (max - min) * f;
            }
        }
        return value;
    }

    /** A number with − and + buttons a {@code step} each, kept within {@code min} to {@code max}. */
    public double stepper(Ui ui, String label, double value, double step, double min, double max, String format,
                   float x, float y, float w) {
        float lw = w * 0.42f, bx = x + lw, bw = w - lw;
        label(ui, label, x, y, lw);
        double v = value;
        if (ui.button("-", bx, y, 28, ROW)) v -= step;
        if (ui.button("+", bx + bw - 28, y, 28, ROW)) v += step;
        String text = String.format(format, value);
        ui.text(ui.small, text, bx + (bw - ui.small.width(text)) / 2, y + (ROW - ui.small.lineHeight()) / 2,
                Theme.TITLE);
        return Math.max(min, Math.min(max, v));
    }

    /** On or off. */
    public boolean toggle(Ui ui, String label, boolean on, float x, float y, float w) {
        float lw = w * 0.42f, bx = x + lw, bw = w - lw;
        label(ui, label, x, y, lw);
        return ui.button(on ? "On" : "Off", bx, y, bw, ROW, on, true) != on;
    }

    /** One of {@code options} (shown as {@code shown}); the arrows, or a click, step through them. */
    public int choice(Ui ui, String label, List<String> shown, int index, float x, float y, float w) {
        return choice(ui, label, shown, index, x, y, w, 0.42f);
    }

    /**
     * {@link #choice(Ui, String, List, int, float, float, float)} with its
     * label {@code share} of the width. Of two, a click on it swaps them; of
     * more, it drops down the list of them all - said in full, scrolled with
     * the wheel - to pick from.
     */
    public int choice(Ui ui, String label, List<String> shown, int index, float x, float y, float w, float share) {
        float lw = label.isEmpty() ? 0 : w * share, bx = x + lw, bw = w - lw;
        if (!label.isEmpty()) label(ui, label, x, y, lw);
        int n = shown.size();
        if (n == 0) return index;
        int i = Math.floorMod(index, n);
        String id = label + "@" + (int) x + "," + (int) y + "#" + n;
        if (id.equals(pickedId)) {
            pickedId = null;
            if (pickedIndex >= 0 && pickedIndex < n) i = pickedIndex;
        }
        final float aw = 22;
        if (ui.button("<", bx, y, aw, ROW)) i = Math.floorMod(i - 1, n);
        if (ui.button(">", bx + bw - aw, y, aw, ROW)) i = Math.floorMod(i + 1, n);
        float mw = bw - 2 * aw - 4, mx = bx + aw + 2;
        String full = shown.get(i), text = Ui.fit(ui.small, full, mw - (n > 2 ? 22 : 8));
        boolean open = id.equals(dropId);
        boolean hot = open || ui.hover(mx, y, mw, ROW);
        ui.rect(mx, y, mw, ROW, hot ? Theme.BUTTON_HOVER : Theme.BUTTON);
        if (open) ui.outline(mx, y, mw, ROW, 1, Theme.ACCENT);
        float tx = mx + (mw - (n > 2 ? 14 : 0) - ui.small.width(text)) / 2;
        ui.text(ui.small, text, tx, y + (ROW - ui.small.lineHeight()) / 2, Theme.TITLE);
        if (n > 2) {
            // the mark of a list that drops down
            float cx = mx + mw - 10, cy = y + ROW / 2f;
            ui.rect(cx - 4, cy - 2, 8, 2, Theme.HINT);
            ui.rect(cx - 2, cy, 4, 2, Theme.HINT);
        }
        if (!text.equals(full) && ui.hover(mx, y, mw, ROW) && !open) tip(full, bx, y + ROW + 2);
        if (ui.clicked(mx, y, mw, ROW)) {
            if (n <= 2) {
                i = Math.floorMod(i + 1, n);
            } else {
                dropId = id;
                dropShown = List.copyOf(shown);
                dropIndex = i;
                float widest = 0;
                for (String o : shown) widest = Math.max(widest, ui.small.width(o));
                dropW = Math.max(bw, Math.min(440, widest + 36));
                dropX = bx;
                dropY = y + ROW + 2;
                int rows = Math.min(DROP_ROWS, n);
                dropScroll = Math.max(0, Math.min(n - rows, i - rows / 2));
            }
        }
        return i;
    }

    /**
     * The palette's colours as a grid of {@code columns}, each {@code size}
     * pixels; the one matching {@code selected} outlined. The colour clicked,
     * or -1.
     */
    public int swatches(Ui ui, int[] colours, int selected, float x, float y, int columns, float size) {
        int picked = -1;
        for (int i = 0; i < colours.length; i++) {
            float sx = x + (i % columns) * size, sy = y + (i / columns) * size;
            int c = colours[i];
            ui.rect(sx, sy, size - 1, size - 1, Theme.rgb((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, 1f));
            if ((c & 0xFFFFFF) == (selected & 0xFFFFFF)) ui.outline(sx - 1, sy - 1, size + 1, size + 1, 2, Theme.TITLE);
            if (ui.clicked(sx, sy, size - 1, size - 1)) picked = c;
        }
        return picked;
    }

    /** Pixels {@link #swatches} takes downwards for {@code n} colours in {@code columns}. */
    public static float swatchesHeight(int n, int columns, float size) {
        return (float) Math.ceil(n / (double) columns) * size;
    }

    /**
     * A line of text to type in: clicked, it takes the keys until Enter, Esc
     * or a click elsewhere. Returns what it says now (what is being typed,
     * while it is).
     */
    public String text(Ui ui, String id, String label, String value, float x, float y, float w) {
        Input in = ui.input();
        float lw = label.isEmpty() ? 0 : w * 0.42f, bx = x + lw, bw = w - lw;
        if (!label.isEmpty()) label(ui, label, x, y, lw);
        boolean mine = id.equals(typing);
        if (!mine && ui.clicked(bx, y, bw, ROW)) {
            typing = id;
            buffer = original = value;
            mine = true;
        } else if (mine && in.mousePressed(GLFW_MOUSE_BUTTON_LEFT) && !ui.hover(bx, y, bw, ROW)) {
            typing = null;
            mine = false;
        }
        String out = value;
        if (mine) {
            for (char c : in.typedText().toCharArray()) if (c >= ' ' && buffer.length() < 60) buffer += c;
            if (in.repeated(GLFW_KEY_BACKSPACE) && !buffer.isEmpty()) buffer = buffer.substring(0, buffer.length() - 1);
            if (in.pressed(GLFW_KEY_ENTER) || in.pressed(GLFW_KEY_KP_ENTER)) {
                typing = null;
                in.consumeKey(GLFW_KEY_ENTER);
            }
            if (in.pressed(GLFW_KEY_ESCAPE)) {
                in.consumeKey(GLFW_KEY_ESCAPE);
                typing = null;
                buffer = original;
            }
            out = buffer;
        }
        ui.rect(bx, y, bw, ROW, mine ? Theme.BUTTON_HOVER : Theme.BUTTON);
        ui.outline(bx, y, bw, ROW, 1, mine ? Theme.ACCENT : Theme.PANEL_EDGE);
        String shown = out + (mine && (System.nanoTime() / 500_000_000L) % 2 == 0 ? "|" : "");
        // what fits: being typed, its end (where the typing is); else its start, cut short
        if (ui.small.width(shown) > bw - 12) {
            if (mine) {
                String t = shown;
                while (t.length() > 1 && ui.small.width("…" + t) > bw - 12) t = t.substring(1);
                shown = "…" + t;
            } else {
                if (ui.hover(bx, y, bw, ROW)) tip(out, bx, y + ROW + 2);
                shown = Ui.fit(ui.small, shown, bw - 12);
            }
        }
        ui.text(ui.small, shown, bx + 8, y + (ROW - ui.small.lineHeight()) / 2, Theme.TITLE);
        return out;
    }

    /** Whether a text field finished being typed in since this was last asked. */
    public boolean committed() {
        boolean c = committed;
        committed = false;
        return c;
    }
}
