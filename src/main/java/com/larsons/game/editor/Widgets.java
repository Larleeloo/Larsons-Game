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

    /** When the field being typed in was last drawn: one no longer drawn (its panel shut, scrolled off) lets go. */
    private long typingSeen;

    /** Whether a text field (or a long list dropped down, narrowed by typing) has the keys: the editor's own wait. */
    public boolean typing() {
        if (typing != null && System.nanoTime() - typingSeen > 300_000_000L) stopTyping();
        return typing != null || dropId != null && searchable();
    }

    /** Let go of the keys (the field keeps what was typed). */
    public void stopTyping() {
        if (typing != null) committed = true;
        typing = null;
    }

    /** Whether a slider is being dragged. */
    public boolean dragging() { return dragging != null; }

    /** Whether the text field {@code id} has the keys. */
    public boolean typingIn(String id) { return id.equals(typing); }

    /** Give the text field {@code id} (holding {@code value}) the keys, as if it were clicked (a shortcut: Ctrl+F). */
    public void focusText(String id, String value) {
        typing = id;
        typingSeen = System.nanoTime();
        buffer = original = value == null ? "" : value;
    }

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
    private List<String> dropShown = List.of(), dropAll = List.of();
    /** Which of all the options each row shown is (the list narrowed by what is typed). */
    private List<Integer> dropRows = List.of();
    /** What has been typed over the list: only the options with it in them are shown. */
    private String dropFilter = "";
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
        int rows = Math.max(1, Math.min(DROP_ROWS, dropShown.size()));
        return new float[]{dropX, dropY, dropW, rows * DROP_ROW + 4 + (searchable() ? DROP_ROW : 0)};
    }

    /** A long list can be narrowed by typing (a search line along its top). */
    private boolean searchable() {
        return dropAll.size() > DROP_ROWS;
    }

    /** The list narrowed to the options with every word typed in them. */
    private void filterDrop() {
        List<String> shown = new java.util.ArrayList<>();
        List<Integer> rows = new java.util.ArrayList<>();
        String[] words = dropFilter.toLowerCase(java.util.Locale.ROOT).trim().split("\\s+");
        for (int i = 0; i < dropAll.size(); i++) {
            String o = dropAll.get(i).toLowerCase(java.util.Locale.ROOT);
            boolean all = true;
            for (String w : words) if (!w.isEmpty() && !o.contains(w)) all = false;
            if (all) {
                shown.add(dropAll.get(i));
                rows.add(i);
            }
        }
        dropShown = shown;
        dropRows = rows;
        dropScroll = 0;
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
        if (searchable()) {
            // typing over a long list narrows it; Enter picks the first left
            String typed = in.typedText();
            boolean changed = false;
            for (char c : typed.toCharArray()) {
                if (c >= ' ' && dropFilter.length() < 30) {
                    dropFilter += c;
                    changed = true;
                }
            }
            if (in.repeated(GLFW_KEY_BACKSPACE) && !dropFilter.isEmpty()) {
                dropFilter = dropFilter.substring(0, dropFilter.length() - 1);
                changed = true;
            }
            if (changed) filterDrop();
            if ((in.pressed(GLFW_KEY_ENTER) || in.pressed(GLFW_KEY_KP_ENTER)) && !dropShown.isEmpty()) {
                in.consumeKey(GLFW_KEY_ENTER);
                pickedId = dropId;
                pickedIndex = dropRows.get(0);
                dropId = null;
                return;
            }
        }
        float[] r = dropRect(ui);
        double mx = in.mouseX(), my = in.mouseY();
        boolean over = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
        int rows = Math.min(DROP_ROWS, dropShown.size());
        float top = r[1] + 2 + (searchable() ? DROP_ROW : 0);
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
            if (over && my >= top) {
                int i = dropScroll + (int) ((my - top) / DROP_ROW);
                if (i >= 0 && i < dropShown.size()) {
                    pickedId = dropId;
                    pickedIndex = dropRows.get(i);
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
            float top = r[1] + 2;
            if (searchable()) {
                // the search line: what is typed, or how to
                boolean blink = (System.nanoTime() / 500_000_000L) % 2 == 0;
                String line = dropFilter.isEmpty() ? "Type to search " + dropAll.size() + "..."
                        : dropFilter + (blink ? "|" : "") + "   (" + dropShown.size() + ")";
                ui.rect(r[0] + 1, top, r[2] - 2, DROP_ROW - 2, Theme.withAlpha(Theme.BUTTON, 0.9f));
                ui.text(ui.small, Ui.fit(ui.small, line, r[2] - 16), r[0] + 8,
                        top + (DROP_ROW - 2 - ui.small.lineHeight()) / 2,
                        dropFilter.isEmpty() ? Theme.ITEM_DISABLED : Theme.ACCENT);
                top += DROP_ROW;
            }
            if (dropShown.isEmpty()) {
                ui.text(ui.small, "Nothing has that in it", r[0] + 8, top + 4, Theme.ITEM_DISABLED);
            }
            for (int k = 0; k < rows; k++) {
                int i = dropScroll + k;
                float ry = top + k * DROP_ROW;
                boolean hot = ui.hover(r[0], ry, r[2], DROP_ROW);
                ui.target(r[0], ry, r[2], DROP_ROW, Ui.Kind.BUTTON);
                if (dropRows.get(i) == dropIndex) {
                    ui.rect(r[0] + 1, ry, r[2] - 2, DROP_ROW, Theme.withAlpha(Theme.ACCENT, 0.35f));
                }
                else if (hot) ui.rect(r[0] + 1, ry, r[2] - 2, DROP_ROW, Theme.BUTTON_HOVER);
                String t = Ui.fit(ui.small, dropShown.get(i), r[2] - 24);
                ui.text(ui.small, t, r[0] + 8, ry + (DROP_ROW - ui.small.lineHeight()) / 2,
                        dropRows.get(i) == dropIndex || hot ? Theme.TITLE : Theme.ITEM);
            }
            if (dropShown.size() > rows) {
                // where in the list it is
                float th = r[3] - 4 - (top - r[1] - 2), bar = Math.max(16, th * rows / dropShown.size());
                float by = top + (th - bar) * dropScroll / Math.max(1, dropShown.size() - rows);
                ui.rect(r[0] + r[2] - 7, top, 4, th, Theme.BUTTON);
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

    /**
     * A number: a slider to drag (or the wheel over it, a {@code step} a
     * notch; the gamepad's left and right) and its value in a box beside it
     * - click the box and type a number, Enter to keep it, Esc to leave it
     * as it was. Kept within {@code min} to {@code max}, and dragged to the
     * nearest {@code step}. A wide range (a price up to 100000) slides finer
     * near its low end.
     */
    public double number(Ui ui, String id, String label, double value, double step, double min, double max,
                         String format, float x, float y, float w) {
        float lw = label.isEmpty() ? 0 : w * 0.42f;
        if (!label.isEmpty()) label(ui, label, x, y, lw);
        return slideAndType(ui, id, value, step, min, max, format, x + lw, y, w - lw);
    }

    /**
     * The rail and the value box of {@link #number}, in {@code bw} from
     * {@code bx} (a narrow one is the box alone). {@code value} itself when
     * nothing touched it, so a value between steps is left as it is.
     */
    private double slideAndType(Ui ui, String id, double value, double step, double min, double max, String format,
                                float bx, float y, float bw) {
        return slideAndType(ui, id, value, step, min, max, format, bx, y, bw, null);
    }

    /**
     * A number in a box alone (a row's column), {@code shown} its words
     * while it is not being typed in (null: the number) - "∞" for a count
     * of 0 - the wheel over it a {@code step} a notch.
     */
    public double numberBox(Ui ui, String id, double value, double step, double min, double max, String format,
                            String shown, float x, float y, float w) {
        return slideAndType(ui, id, value, step, min, max, format, x, y, w, shown);
    }

    private double slideAndType(Ui ui, String id, double value, double step, double min, double max, String format,
                                float bx, float y, float bw, String words) {
        Input in = ui.input();
        float vw = Math.min(72, Math.max(54, bw * 0.38f));
        boolean railed = bw - vw - 8 >= 34;
        if (!railed) vw = bw;
        float rw = railed ? bw - vw - 8 : 0, vx = bx + bw - vw;
        double nudge = step > 0 ? step : (max - min) / 50;
        boolean wide = max - min > 2000 && min >= 0;
        double out = value;
        boolean changed = false, dragged = false;
        if (railed) {
            double t = Math.max(0, Math.min(1, (value - min) / (max - min)));
            if (wide) t = Math.sqrt(t);
            float rail = y + ROW / 2f - 3;
            ui.rect(bx, rail, rw, 6, Theme.BUTTON);
            ui.rect(bx, rail, (float) (rw * t), 6, Theme.withAlpha(Theme.ACCENT, 0.7f));
            boolean held = id.equals(dragging);
            boolean hot = held || ui.hover(bx - 6, y, rw + 12, ROW);
            ui.rect((float) (bx + rw * t - 5), y + 4, 10, ROW - 8,
                    held ? Theme.ACCENT : hot ? Theme.ITEM_SELECTED : Theme.TITLE);
            ui.target(bx - 6, y, rw + 12, ROW, Ui.Kind.SLIDER);
            ui.hint(hot ? "slide" : null);
            if (ui.clicked(bx - 6, y, rw + 12, ROW) && !in.padFocusing()) dragging = id;
            if (id.equals(dragging)) {
                if (!in.mouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
                    dragging = null;
                } else {
                    double f = Math.max(0, Math.min(1, (in.mouseX() - bx) / rw));
                    if (wide) f = f * f;
                    out = min + (max - min) * f;
                    changed = dragged = true;
                }
            }
            if (hot && !held && in.scroll() != 0) {
                out = value + Math.signum(in.scroll()) * nudge;
                in.consumeScroll();
                changed = true;
            }
            if (in.padFocusing() && in.navAdjust() != 0 && ui.hover(bx - 6, y, rw + 12, ROW)) {
                out = value + in.navAdjust() * nudge;
                in.adjust(0);
                changed = true;
            }
        }
        // the value, typed in
        String key = id + "#value";
        boolean mine = key.equals(typing);
        if (mine) typingSeen = System.nanoTime();
        ui.target(vx, y, vw, ROW, Ui.Kind.TEXT);
        if (!mine && ui.clicked(vx, y, vw, ROW)) {
            typing = key;
            typingSeen = System.nanoTime();
            buffer = "";
            original = String.format(format, value).trim();
            mine = true;
            ui.textPressed();
        } else if (mine && in.mousePressed(GLFW_MOUSE_BUTTON_LEFT) && !ui.hover(vx, y, vw, ROW)) {
            mine = false;
            typing = null;
            out = parse(buffer, value);
            changed = out != value;
        }
        if (mine) {
            for (char c : in.typedText().toCharArray()) {
                if ((Character.isDigit(c) || c == '.' || c == '-' || c == ',') && buffer.length() < 12) buffer += c;
            }
            if (in.repeated(GLFW_KEY_BACKSPACE) && !buffer.isEmpty()) buffer = buffer.substring(0, buffer.length() - 1);
            if (in.pressed(GLFW_KEY_ENTER) || in.pressed(GLFW_KEY_KP_ENTER) || in.pressed(GLFW_KEY_TAB)) {
                in.consumeKey(GLFW_KEY_ENTER);
                in.consumeKey(GLFW_KEY_KP_ENTER);
                in.consumeKey(GLFW_KEY_TAB);
                typing = null;
                out = parse(buffer, value);
                changed = out != value;
                mine = false;
            } else if (in.pressed(GLFW_KEY_ESCAPE)) {
                in.consumeKey(GLFW_KEY_ESCAPE);
                typing = null;
                mine = false;
            }
        }
        boolean over = ui.hover(vx, y, vw, ROW);
        if (!railed && over && !mine && in.scroll() != 0) {
            out = value + Math.signum(in.scroll()) * nudge;
            in.consumeScroll();
            changed = true;
        }
        if (over && !mine) ui.hint("text");
        ui.rect(vx, y, vw, ROW, mine ? Theme.BUTTON_HOVER : over ? Theme.withAlpha(Theme.BUTTON_HOVER, 0.8f) : Theme.BUTTON);
        ui.outline(vx, y, vw, ROW, 1, mine ? Theme.ACCENT : Theme.PANEL_EDGE);
        String shown = mine ? buffer + ((System.nanoTime() / 500_000_000L) % 2 == 0 ? "|" : "")
                : words != null ? words : String.format(format, value).trim();
        if (mine && buffer.isEmpty()) ui.text(ui.tiny, original, vx + 6, y + (ROW - ui.tiny.lineHeight()) / 2,
                Theme.ITEM_DISABLED);
        shown = Ui.fit(ui.small, shown, vw - 8);
        ui.text(ui.small, shown, vx + vw - 5 - ui.small.width(shown), y + (ROW - ui.small.lineHeight()) / 2, Theme.TITLE);
        if (over && !mine) tip("Click to type a value (" + String.format(format, min).trim() + " to "
                + String.format(format, max).trim() + ")", vx - 120, y + ROW + 2);
        if (!changed) return value;
        // dragged: to the nearest step; nudged or typed: as it is, kept within the range
        return dragged ? snap(out, step, min, max) : snap(out, 0, min, max);
    }

    /** {@code v} kept within {@code min}..{@code max}, to the nearest {@code step} (from {@code min}). */
    static double snap(double v, double step, double min, double max) {
        if (Double.isNaN(v)) return min;
        if (step > 0) v = min + Math.round((v - min) / step) * step;
        v = Math.max(min, Math.min(max, v));
        // (no 0.30000000000000004 from adding tenths)
        return Math.round(v * 1e6) / 1e6;
    }

    /** A number typed in ("1,500", "-2.5", "") - or {@code fallback} for what is not one. */
    static double parse(String typed, double fallback) {
        String t = typed == null ? "" : typed.replace(",", "").trim();
        if (t.isEmpty() || t.equals("-") || t.equals(".")) return fallback;
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * A number with − and + buttons a {@code step} each, kept within
     * {@code min} to {@code max} - and between them a slider and a box to
     * type the value straight in ({@link #number}), so a long way is not a
     * hundred clicks.
     */
    public double stepper(Ui ui, String label, double value, double step, double min, double max, String format,
                   float x, float y, float w) {
        float lw = w * 0.42f, bx = x + lw, bw = w - lw;
        label(ui, label, x, y, lw);
        double v = value;
        if (ui.button("-", bx, y, 26, ROW)) v = value - step;
        if (ui.button("+", bx + bw - 26, y, 26, ROW)) v = value + step;
        if (v != value) return snap(v, 0, min, max);
        return slideAndType(ui, "step:" + label + "@" + Math.round(x) + "," + Math.round(y), value, step, min, max,
                format, bx + 30, y, bw - 60);
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
        return box(ui, id, shown, i, bx + aw + 2, y, bw - 2 * aw - 4, bx, bw);
    }

    /**
     * One of {@code shown}, in a box {@code w} wide with no arrows (a row's
     * column): a click drops down the list of them all - typed over, it
     * narrows - and the gamepad's left and right step through them.
     * {@code id} tells it from every other on the screen.
     */
    public int dropdown(Ui ui, String id, List<String> shown, int index, float x, float y, float w) {
        int n = shown.size();
        if (n == 0) return index;
        int i = Math.floorMod(index, n);
        String key = id + "#" + n;
        if (key.equals(pickedId)) {
            pickedId = null;
            if (pickedIndex >= 0 && pickedIndex < n) i = pickedIndex;
        }
        return box(ui, key, shown, i, x, y, w, x, w);
    }

    /** The box of a choice ({@code (mx, mw)}) and the list it drops down ({@code (bx, bw)} at the least). */
    private int box(Ui ui, String id, List<String> shown, int i, float mx, float y, float mw, float bx, float bw) {
        int n = shown.size();
        // the gamepad's left and right step through it while it has the focus
        ui.target(mx, y, mw, ROW, Ui.Kind.CHOICE);
        Input pad = ui.input();
        if (pad.padFocusing() && pad.navAdjust() != 0 && ui.hover(mx, y, mw, ROW)) {
            i = Math.floorMod(i + pad.navAdjust(), n);
            pad.adjust(0);
        }
        // (a narrow box - a row's column - has no room for the mark of a list)
        boolean mark = n > 2 && mw >= 70;
        String full = shown.get(i), text = Ui.fit(ui.small, full, mw - (mark ? 22 : 8));
        boolean open = id.equals(dropId);
        boolean hot = open || ui.hover(mx, y, mw, ROW);
        ui.rect(mx, y, mw, ROW, hot ? Theme.BUTTON_HOVER : Theme.BUTTON);
        if (open) ui.outline(mx, y, mw, ROW, 1, Theme.ACCENT);
        float tx = mx + (mw - (mark ? 14 : 0) - ui.small.width(text)) / 2;
        ui.text(ui.small, text, tx, y + (ROW - ui.small.lineHeight()) / 2, Theme.TITLE);
        if (mark) {
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
                dropAll = List.copyOf(shown);
                dropFilter = "";
                filterDrop();
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
        return text(ui, id, label, value, x, y, w, "");
    }

    /** {@link #text(Ui, String, String, String, float, float, float)}, saying {@code placeholder} while it is empty. */
    public String text(Ui ui, String id, String label, String value, float x, float y, float w, String placeholder) {
        Input in = ui.input();
        float lw = label.isEmpty() ? 0 : w * 0.42f, bx = x + lw, bw = w - lw;
        if (!label.isEmpty()) label(ui, label, x, y, lw);
        boolean mine = id.equals(typing);
        if (mine) typingSeen = System.nanoTime();
        ui.target(bx, y, bw, ROW, Ui.Kind.TEXT);
        if (!mine && ui.clicked(bx, y, bw, ROW)) {
            typingSeen = System.nanoTime();
            typing = id;
            buffer = original = value;
            mine = true;
            ui.textPressed();
        } else if (mine && in.mousePressed(GLFW_MOUSE_BUTTON_LEFT) && !ui.hover(bx, y, bw, ROW)) {
            typing = null;
            mine = false;
        }
        String out = value;
        if (mine) {
            for (char c : in.typedText().toCharArray()) if (c >= ' ' && buffer.length() < 600) buffer += c;
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
        if (out.isEmpty() && !placeholder.isEmpty()) {
            ui.text(ui.small, Ui.fit(ui.small, placeholder, bw - 24), bx + (mine ? 14 : 8),
                    y + (ROW - ui.small.lineHeight()) / 2, Theme.ITEM_DISABLED);
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
