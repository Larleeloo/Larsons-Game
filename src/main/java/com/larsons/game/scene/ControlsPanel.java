package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Game;
import com.larsons.game.input.Action;
import com.larsons.game.input.Bind;
import com.larsons.game.input.Bindings;
import com.larsons.game.input.Input;
import com.larsons.game.ui.Glyphs;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.nio.file.Path;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The Controls page: every {@link Action} in its group, with the keys (or
 * mouse buttons) and the gamepad inputs it is - two of each. A click on one
 * waits for the next key or button pressed and makes it that; a right-click
 * takes it away. What is pressed is taken from whatever had it. Kept in
 * {@code config/controls.json} as it changes.
 */
public final class ControlsPanel {

    private static final float ROW = 30, SLOT_W = 128;

    private final Game game;
    private float scroll;
    /** The slot waiting for an input: its action, which of its two, on the gamepad or not - or none. */
    private Action waiting;
    private int waitingSlot;
    private boolean waitingPad;
    /** The frame the wait began does not count (the click that began it). */
    private boolean armed;

    public ControlsPanel(Game game) {
        this.game = game;
    }

    /** Where the bindings are kept. */
    public static Path file(Game game) {
        return game.settings().config.resolve("controls.json");
    }

    /** Whether it is waiting for a key (the page's Esc then stops the wait, not the page). */
    public boolean capturing() { return waiting != null; }

    /** Stop waiting, the page left. */
    public void leave() {
        waiting = null;
        game.window().input().setCapturing(false);
    }

    public void draw(Ui ui, float x, float y, float w, float h) {
        Input in = ui.input();
        Bindings b = in.bindings();
        capture(in, b);

        // the head: what is plugged in, and the two ways back to the defaults
        String pad = in.padPresent() ? "Controller: " + in.padName() : "No controller found (plug one in at any time)";
        ui.text(ui.small, pad + "   ·   the prompts follow whichever you touched last", x, y,
                in.padPresent() ? Theme.OK : Theme.ITEM_DISABLED);
        if (ui.button("Reset keys", x + w - 250, y - 6, 120, 28)) {
            b.reset(false);
            changed(b);
        }
        if (ui.button("Reset controller", x + w - 124, y - 6, 124, 28)) {
            b.reset(true);
            changed(b);
        }
        float top = y + 30;
        float labelW = Math.max(180, w - 4 * (SLOT_W + 6) - 20);
        ui.text(ui.small, "Keyboard and mouse", x + labelW, top, Theme.ACCENT);
        ui.text(ui.small, "Controller", x + labelW + 2 * (SLOT_W + 6), top, Theme.ACCENT);
        top += 22;
        float viewH = h - (top - y) - 30;

        float total = 0;
        for (Action.Group g : Action.Group.values()) total += ROW + count(g) * ROW;
        scroll = Math.max(0, Math.min(Math.max(0, total - viewH), scroll - (float) in.scroll() * 48));
        in.consumeScroll();

        ui.clip(x, top, w, viewH);
        float cy = top - scroll;
        for (Action.Group g : Action.Group.values()) {
            ui.text(ui.body, g.label(), x, cy + 4, Theme.TITLE);
            ui.rect(x + ui.body.width(g.label()) + 10, cy + 16, w - ui.body.width(g.label()) - 24, 1,
                    Theme.withAlpha(Theme.BRASS, 0.5f));
            cy += ROW;
            for (Action a : Action.values()) {
                if (a.group() != g) continue;
                if (cy > top - ROW && cy < top + viewH) row(ui, in, b, a, x, cy, labelW);
                cy += ROW;
            }
        }
        ui.unclip();
        // how far down the list is
        if (total > viewH) {
            float bar = viewH * viewH / total;
            ui.rect(x + w - 5, top + (viewH - bar) * scroll / (total - viewH), 4, bar, Theme.BRASS);
        }
        String hint = waiting != null
                ? (waitingPad ? "Press a button, a trigger or push a stick" : "Press a key or a mouse button")
                + " for \"" + waiting.label() + "\"  ·  Esc: leave it as it is"
                : "Click a key or a button to change it  ·  Right-click: take it away  ·  the wheel scrolls";
        ui.text(ui.small, hint, x, y + h - 18, waiting != null ? Theme.ACCENT : Theme.ITEM_DISABLED);
    }

    private static int count(Action.Group g) {
        int n = 0;
        for (Action a : Action.values()) if (a.group() == g) n++;
        return n;
    }

    private void row(Ui ui, Input in, Bindings b, Action a, float x, float y, float labelW) {
        ui.text(ui.small, a.label(), x + 12, y + 7, Theme.ITEM);
        for (int col = 0; col < 4; col++) {
            boolean pad = col >= 2;
            int slot = col % 2;
            List<Bind> binds = b.of(a, pad);
            Bind bind = slot < binds.size() ? binds.get(slot) : null;
            float sx = x + labelW + col * (SLOT_W + 6), sw = SLOT_W;
            boolean wait = waiting == a && waitingSlot == slot && waitingPad == pad;
            boolean hot = ui.hover(sx, y + 2, sw, ROW - 4);
            ui.rect(sx, y + 2, sw, ROW - 4, wait ? Theme.BUTTON_HOVER : Theme.withAlpha(Theme.SOOT, hot ? 0.55f : 0.35f));
            ui.outline(sx, y + 2, sw, ROW - 4, 1, wait ? Theme.ACCENT : hot ? Theme.BRASS_LIGHT
                    : Theme.withAlpha(Theme.BRASS, 0.45f));
            if (wait) {
                ui.text(ui.small, "press…", sx + (sw - ui.small.width("press…")) / 2, y + 7, Theme.ACCENT);
            } else if (bind != null) {
                float gw = Glyphs.width(ui, bind, 20);
                if (gw <= sw - 8) Glyphs.draw(ui, bind, sx + (sw - gw) / 2, y + 5, 20);
                else ui.text(ui.tiny, Ui.fit(ui.tiny, bind.label(), sw - 8), sx + 4, y + 8, Theme.ITEM);
            } else {
                ui.text(ui.small, "-", sx + sw / 2 - 2, y + 7, Theme.ITEM_DISABLED);
            }
            if (waiting != null) continue;
            if (ui.clicked(sx, y + 2, sw, ROW - 4)) {
                waiting = a;
                waitingSlot = Math.min(slot, binds.size());
                waitingPad = pad;
                armed = false;
                in.setCapturing(true);
            } else if (hot && in.mousePressed(GLFW_MOUSE_BUTTON_RIGHT) && bind != null) {
                in.consumeMouse(GLFW_MOUSE_BUTTON_RIGHT);
                if (b.clear(a, slot, pad)) changed(b);
                else game.toast(a.label() + " must keep a key", Theme.WARNING);
            }
        }
    }

    /** While a slot waits: the next key, mouse button or gamepad input pressed is it; Esc leaves it. */
    private void capture(Input in, Bindings b) {
        if (waiting == null) return;
        if (!armed) {
            armed = true;      // (from the next frame on)
            return;
        }
        if (in.pressed(GLFW_KEY_ESCAPE)) {
            in.consumeKey(GLFW_KEY_ESCAPE);
            leave();
            return;
        }
        Bind got = in.firstPressed(waitingPad);
        if (got == null) return;
        if (got.kind() == Bind.Kind.KEY) in.consumeKey(got.code());
        else if (got.kind() == Bind.Kind.MOUSE) in.consumeMouse(got.code());
        Action had = b.set(waiting, waitingSlot, got);
        if (had != null) game.toast(got.label() + " was \"" + had.label() + "\": now \"" + waiting.label() + "\"",
                Theme.HINT);
        Sounds.play(SoundKeys.ui("click"));
        changed(b);
        leave();
    }

    private void changed(Bindings b) {
        b.save(file(game));
    }
}
