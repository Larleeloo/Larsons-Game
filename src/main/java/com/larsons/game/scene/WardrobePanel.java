package com.larsons.game.scene;

import com.larsons.game.core.Game;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The cosmetics editor, from the pause menu: every layer of the sprite stack
 * with the items found for it under {@code assets/sprites/<slot>/}, and a live
 * preview that can be turned through the eight directions, tipped through the
 * three elevations and played in all six states.
 *
 * <p>A style row switches the whole character between the 512-pixel renders
 * and the pixel art made of them ({@link Wardrobe.Style}); the items' versions
 * in a style are not listed as items of their own.
 *
 * <p>Changes apply to the character immediately and are saved to
 * {@code config/wardrobe.json} when the menu closes.
 */
final class WardrobePanel {

    private final Game game;
    private final LayerStack.Memory previewMemory = new LayerStack.Memory();
    private Facing facing = Facing.SOUTH_EAST;
    private Elevation elevation = Elevation.MIDDLE;
    private AnimState state = AnimState.IDLE;
    private boolean spin = true;
    private double time, spinTimer;

    WardrobePanel(Game game) {
        this.game = game;
    }

    /** The choices for a slot: nothing (or the fallback body), then every item folder. */
    static List<String> options(SpriteLibrary lib, Slot slot, Player player) {
        List<String> out = new ArrayList<>();
        out.add(null);
        for (String item : lib.items(slot)) if (!Wardrobe.Style.isVersion(item)) out.add(item);
        if (slot.carried()) {
            for (String held : player.inventory()) if (!out.contains(held)) out.add(held);
        }
        return out;
    }

    static String label(Slot slot, String item) {
        if (item == null) return slot == Slot.BODY ? "Fallback (32 px)" : "None";
        return item;
    }

    void draw(Ui ui, double dt, float x, float y, float w, float h, Player player) {
        Wardrobe wardrobe = player.wardrobe();
        SpriteLibrary lib = game.sprites();
        time += dt;
        if (spin) {
            spinTimer += dt;
            if (spinTimer > 0.9) {
                spinTimer = 0;
                facing = facing.counterClockwise();
            }
        }

        // --- the slot list, two columns ----------------------------------------------
        Slot[] slots = Slot.values();
        int perColumn = (slots.length + 1) / 2;
        float colW = Math.min(300, (w - 360) / 2 - 10), rowH = 40;
        for (int i = 0; i < slots.length; i++) {
            Slot slot = slots[i];
            float cx = x + (i / perColumn) * (colW + 16);
            float cy = y + (i % perColumn) * rowH;
            List<String> opts = options(lib, slot, player);
            String current = wardrobe.get(slot);
            int idx = Math.max(0, opts.indexOf(current));
            boolean missing = current != null && !opts.contains(current);

            ui.text(ui.small, slot.label(), cx, cy + 2, Theme.ITEM_DISABLED);
            boolean any = opts.size() > 1;
            if (ui.button("<", cx, cy + 18, 22, 20, false, any)) {
                wardrobe.set(slot, opts.get(Math.floorMod(idx - 1, opts.size())));
            }
            String text = missing ? current + " (missing)" : label(slot, current);
            float[] c = missing ? Theme.WARNING : current == null ? Theme.ITEM_DISABLED : Theme.ITEM_SELECTED;
            ui.text(ui.body, fit(ui, text, colW - 60), cx + 28, cy + 18, c);
            if (ui.button(">", cx + colW - 26, cy + 18, 22, 20, false, any)) {
                wardrobe.set(slot, opts.get((idx + 1) % opts.size()));
            }
        }
        float listBottom = y + perColumn * rowH + 6;
        ui.paragraph(ui.small, "Items are the folders in assets/sprites/<layer>/. "
                        + "Drop new sheets on the window to add more; empty layers are not drawn.",
                x, listBottom, colW * 2 + 16, Theme.ITEM_DISABLED);

        // --- the style: the renders, or the pixel art made of them -------------------
        float sy = listBottom + 44;
        ui.text(ui.body, "Style", x, sy + 6, Theme.ITEM);
        Wardrobe.Style[] styles = Wardrobe.Style.values();
        for (int i = 0; i < styles.length; i++) {
            if (ui.button(styles[i].label(), x + 70 + i * 116, sy, 110, 30,
                    wardrobe.style() == styles[i], true)) {
                wardrobe.setStyle(styles[i]);
            }
        }

        // --- the preview ------------------------------------------------------------
        float px = x + w - 340, pw = 340, ph = h - 110;
        ui.rect(px, y, pw, ph, Theme.withAlpha(Theme.BACKGROUND, 0.9f));
        ui.outline(px, y, pw, ph, 1, Theme.PANEL_EDGE);
        SpriteView view = new SpriteView(elevation, facing, elevation.renderAngle());
        double duration = LayerStack.duration(lib, wardrobe, state, view);
        double t = state.loops() ? time : time % (duration + 0.4);
        LayerStack.Result r = LayerStack.resolve(lib, wardrobe, state, t, view, previewMemory);
        float ppm = (float) Math.min((ph - 30) / r.framing().frameWorldHeight(),
                (pw - 20) / r.framing().frameWorldSize());
        double[] anchor = r.framing().anchor(elevation);
        float frameH = (float) (r.framing().frameWorldHeight() * ppm);
        float footY = y + (ph - frameH) / 2 + (float) anchor[1] * frameH;
        LayerStack.drawFlat(ui.batch(), r, px + pw / 2, footY, ppm);
        ui.text(ui.small, facing.key().toUpperCase() + " · " + elevation.label() + " · " + state.label()
                + " · frame " + (r.frame() + 1) + "/" + r.frames(), px + 10, y + ph - 22, Theme.HINT);

        // Preview controls.
        float by = y + ph + 8;
        if (ui.button("<", px, by, 34, 30)) {
            facing = facing.clockwise();
            spin = false;
        }
        if (ui.button(spin ? "Stop" : "Spin", px + 40, by, 70, 30)) spin = !spin;
        if (ui.button(">", px + 116, by, 34, 30)) {
            facing = facing.counterClockwise();
            spin = false;
        }
        for (int i = 0; i < Elevation.values().length; i++) {
            Elevation e = Elevation.values()[i];
            if (ui.button(e.key(), px + 160 + i * 60, by, 56, 30, e == elevation, true)) elevation = e;
        }
        by += 36;
        AnimState[] states = AnimState.values();
        float sw = (pw - 5 * 4) / states.length;
        for (int i = 0; i < states.length; i++) {
            if (ui.button(states[i].key(), px + i * (sw + 4), by, sw, 30, states[i] == state, true)) {
                state = states[i];
                time = 0;
            }
        }
    }

    private static String fit(Ui ui, String s, float width) {
        if (ui.body.width(s) <= width) return s;
        while (s.length() > 1 && ui.body.width(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
