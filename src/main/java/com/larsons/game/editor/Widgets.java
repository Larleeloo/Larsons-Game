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
final class Widgets {

    /** A control's height, pixels. */
    static final float ROW = 28;

    private String dragging;
    private String typing;
    private String buffer = "", original = "";
    private boolean committed;

    /** Whether a text field has the keys: the editor's own keys wait while it does. */
    boolean typing() { return typing != null; }

    /** Let go of the keys (the field keeps what was typed). */
    void stopTyping() {
        if (typing != null) committed = true;
        typing = null;
    }

    /** Whether a slider is being dragged. */
    boolean dragging() { return dragging != null; }

    /** A label on the left of a row, the control filling the rest from {@code split} of the width on. */
    private static void label(Ui ui, String label, float x, float y) {
        ui.text(ui.small, label, x, y + (ROW - ui.small.lineHeight()) / 2, Theme.ITEM);
    }

    /** A slider from {@code min} to {@code max}; the value as it is dragged (or as it was). */
    double slider(Ui ui, String id, String label, double value, double min, double max, String format,
                  float x, float y, float w) {
        Input in = ui.input();
        float lw = w * 0.42f, bx = x + lw, bw = w - lw;
        label(ui, label, x, y);
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
    double stepper(Ui ui, String label, double value, double step, double min, double max, String format,
                   float x, float y, float w) {
        float lw = w * 0.42f, bx = x + lw, bw = w - lw;
        label(ui, label, x, y);
        double v = value;
        if (ui.button("-", bx, y, 28, ROW)) v -= step;
        if (ui.button("+", bx + bw - 28, y, 28, ROW)) v += step;
        String text = String.format(format, value);
        ui.text(ui.small, text, bx + (bw - ui.small.width(text)) / 2, y + (ROW - ui.small.lineHeight()) / 2,
                Theme.TITLE);
        return Math.max(min, Math.min(max, v));
    }

    /** On or off. */
    boolean toggle(Ui ui, String label, boolean on, float x, float y, float w) {
        float lw = w * 0.42f, bx = x + lw, bw = w - lw;
        label(ui, label, x, y);
        return ui.button(on ? "On" : "Off", bx, y, bw, ROW, on, true) != on;
    }

    /** One of {@code options} (shown as {@code shown}); the arrows, or a click, step through them. */
    int choice(Ui ui, String label, List<String> shown, int index, float x, float y, float w) {
        float lw = w * 0.42f, bx = x + lw, bw = w - lw;
        label(ui, label, x, y);
        int n = shown.size();
        if (n == 0) return index;
        int i = Math.floorMod(index, n);
        if (ui.button("<", bx, y, 26, ROW)) i = Math.floorMod(i - 1, n);
        if (ui.button(">", bx + bw - 26, y, 26, ROW)) i = Math.floorMod(i + 1, n);
        String text = shown.get(i);
        while (text.length() > 3 && ui.small.width(text) > bw - 60) text = text.substring(0, text.length() - 2) + "…";
        boolean hot = ui.hover(bx + 28, y, bw - 56, ROW);
        ui.rect(bx + 28, y, bw - 56, ROW, hot ? Theme.BUTTON_HOVER : Theme.BUTTON);
        ui.text(ui.small, text, bx + 28 + (bw - 56 - ui.small.width(text)) / 2, y + (ROW - ui.small.lineHeight()) / 2,
                Theme.TITLE);
        if (ui.clicked(bx + 28, y, bw - 56, ROW)) i = Math.floorMod(i + 1, n);
        return i;
    }

    /**
     * The palette's colours as a grid of {@code columns}, each {@code size}
     * pixels; the one matching {@code selected} outlined. The colour clicked,
     * or -1.
     */
    int swatches(Ui ui, int[] colours, int selected, float x, float y, int columns, float size) {
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
    static float swatchesHeight(int n, int columns, float size) {
        return (float) Math.ceil(n / (double) columns) * size;
    }

    /**
     * A line of text to type in: clicked, it takes the keys until Enter, Esc
     * or a click elsewhere. Returns what it says now (what is being typed,
     * while it is).
     */
    String text(Ui ui, String id, String label, String value, float x, float y, float w) {
        Input in = ui.input();
        float lw = label.isEmpty() ? 0 : w * 0.42f, bx = x + lw, bw = w - lw;
        if (!label.isEmpty()) label(ui, label, x, y);
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
        ui.text(ui.small, shown, bx + 8, y + (ROW - ui.small.lineHeight()) / 2, Theme.TITLE);
        return out;
    }

    /** Whether a text field finished being typed in since this was last asked. */
    boolean committed() {
        boolean c = committed;
        committed = false;
        return c;
    }
}
