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
import java.util.Locale;

/**
 * The cosmetics editor, from the pause menu: every layer of the sprite stack
 * with the items found for it under {@code assets/sprites/<slot>/}, and a live
 * preview that can be turned through the eight directions, tipped through the
 * three elevations and played in all six states.
 *
 * <p>A style row switches the whole character between the 512-pixel renders
 * and the pixel art made of them ({@link Wardrobe.Style}); the items' versions
 * in a style are not listed as items of their own. An item with a colour
 * choice in the style (its {@code variants.json}) shows a swatch beside it:
 * click the swatch (or the layer's name) and pick the colour in the colour
 * row - for the base body that is the skin tone, which every item showing
 * skin follows. A hand row makes the character left- or right-handed (Auto:
 * left-handed while the sword is in the left hand only).
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
    /** The layer whose colours the colour row shows. */
    private Slot focus = Slot.HAIR;

    WardrobePanel(Game game) {
        this.game = game;
    }

    /** The choices for a slot: nothing (or the fallback body), then every item folder. */
    static List<String> options(SpriteLibrary lib, Slot slot, Player player) {
        List<String> out = new ArrayList<>();
        out.add(null);
        for (String item : lib.items(slot)) {
            String base = Wardrobe.Style.base(item);
            if (!out.contains(base)) out.add(base);        // (an item with only pixel art is listed too)
        }
        if (slot.carried()) {
            for (String held : player.inventory()) if (!out.contains(held)) out.add(held);
        }
        return out;
    }

    static String label(Slot slot, String item) {
        if (item == null) return slot == Slot.BODY ? "Fallback (32 px)" : "None";
        return item;
    }

    /** "royal_blue" -> "Royal blue". */
    static String colourName(String option) {
        if (option == null) return "Own colours";
        String s = option.replace('_', ' ');
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static float[] rgb(int rgb) {
        return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f};
    }

    void draw(Ui ui, double dt, float x, float y, float w, float h, Player player) {
        Wardrobe wardrobe = player.wardrobe();
        SpriteLibrary lib = game.sprites();
        Wardrobe.Style style = wardrobe.style();
        time += dt;
        if (spin) {
            spinTimer += dt;
            if (spinTimer > 0.9) {
                spinTimer = 0;
                facing = facing.counterClockwise();
            }
        }

        // --- the slot list, three columns ---------------------------------------------
        Slot[] slots = Slot.values();
        int columns = 3;
        int perColumn = (slots.length + columns - 1) / columns;
        float listW = w - 360;
        float colW = Math.min(250, (listW - (columns - 1) * 12) / columns), rowH = 40;
        for (int i = 0; i < slots.length; i++) {
            Slot slot = slots[i];
            float cx = x + (i / perColumn) * (colW + 12);
            float cy = y + (i % perColumn) * rowH;
            List<String> opts = options(lib, slot, player);
            String current = wardrobe.get(slot);
            int idx = Math.max(0, opts.indexOf(current));
            boolean missing = current != null && !opts.contains(current);
            boolean hasArt = current == null || lib.entry(slot, lib.styled(slot, current, style)) != null;

            String name = slot == Slot.BODY ? "Base body · skin" : slot.label();
            if (ui.clicked(cx, cy, colW, 16)) focus = slot;
            ui.text(ui.small, name, cx, cy + 2, focus == slot ? Theme.ACCENT : Theme.ITEM_DISABLED);
            boolean any = opts.size() > 1;
            if (ui.button("<", cx, cy + 18, 22, 20, false, any)) {
                wardrobe.set(slot, opts.get(Math.floorMod(idx - 1, opts.size())));
                focus = slot;
            }
            List<String> colours = current == null ? List.of() : lib.colourOptions(slot, current, style);
            float swW = colours.isEmpty() ? 0 : 24;
            String text = missing ? current + " (missing)" : !hasArt ? current + " (pixel art)" : label(slot, current);
            float[] c = missing || !hasArt ? Theme.WARNING : current == null ? Theme.ITEM_DISABLED : Theme.ITEM_SELECTED;
            ui.text(ui.body, fit(ui, text, colW - 60 - swW), cx + 28, cy + 18, c);
            if (ui.button(">", cx + colW - 26 - swW, cy + 18, 22, 20, false, any)) {
                wardrobe.set(slot, opts.get((idx + 1) % opts.size()));
                focus = slot;
            }
            if (!colours.isEmpty()) {
                // the colour picked for it (the item's own colours: an outline)
                float sx = cx + colW - 20, sy = cy + 19;
                String picked = wardrobe.colour(slot);
                int sw = picked == null ? -1 : lib.swatch(slot, current, style, picked);
                if (sw >= 0) ui.rect(sx, sy, 18, 18, rgb(sw));
                ui.outline(sx, sy, 18, 18, focus == slot ? 2 : 1, focus == slot ? Theme.ACCENT : Theme.PANEL_EDGE);
                if (ui.clicked(sx, sy, 18, 18)) focus = slot;
            }
        }
        float by0 = y + perColumn * rowH + 4;

        // --- the colour row: the focused layer's colours -----------------------------
        String fItem = wardrobe.get(focus);
        List<String> fOpts = fItem == null ? List.of() : lib.colourOptions(focus, fItem, style);
        String what = focus == Slot.BODY ? "Skin" : focus.label();
        ui.text(ui.body, "Colour · " + what + ": "
                        + (fOpts.isEmpty() ? (fItem == null ? "nothing worn" : "none in this style")
                        : colourName(wardrobe.colour(focus))),
                x, by0 + 4, fOpts.isEmpty() ? Theme.ITEM_DISABLED : Theme.ITEM);
        float sy = by0 + 26;
        if (!fOpts.isEmpty()) {
            float s = 22, gap = 4;
            if (ui.button("-", x, sy, s, s, wardrobe.colour(focus) == null, true)) wardrobe.setColour(focus, null);
            for (int i = 0; i < fOpts.size(); i++) {
                float bx = x + (i + 1) * (s + gap);
                if (bx + s > x + listW) break;
                String o = fOpts.get(i);
                int rgb = lib.swatch(focus, fItem, style, o);
                ui.rect(bx, sy, s, s, rgb >= 0 ? rgb(rgb) : Theme.BUTTON);
                boolean on = o.equals(wardrobe.colour(focus));
                ui.outline(bx, sy, s, s, on ? 2 : 1, on ? Theme.ACCENT : ui.hover(bx, sy, s, s) ? Theme.HINT : Theme.PANEL_EDGE);
                if (ui.clicked(bx, sy, s, s)) wardrobe.setColour(focus, o);
            }
        }

        // --- the style: the renders, or the pixel art made of them -------------------
        float stY = sy + 34;
        ui.text(ui.body, "Style", x, stY + 6, Theme.ITEM);
        Wardrobe.Style[] styles = Wardrobe.Style.values();
        for (int i = 0; i < styles.length; i++) {
            if (ui.button(styles[i].label(), x + 70 + i * 116, stY, 110, 30, style == styles[i], true)) {
                wardrobe.setStyle(styles[i]);
            }
        }
        // --- the hand that leads -----------------------------------------------------
        float hy = stY + 36;
        ui.text(ui.body, "Hand", x, hy + 6, Theme.ITEM);
        Wardrobe.Hand[] hands = Wardrobe.Hand.values();
        for (int i = 0; i < hands.length; i++) {
            if (ui.button(hands[i].label(), x + 70 + i * 116, hy, 110, 30, wardrobe.hand() == hands[i], true)) {
                wardrobe.setHand(hands[i]);
            }
        }
        ui.text(ui.small, wardrobe.leftHanded() ? "left-handed" : "right-handed",
                x + 70 + hands.length * 116 + 8, hy + 8, Theme.HINT);
        ui.paragraph(ui.small, "Items are the folders in assets/sprites/<layer>/. "
                        + "Drop new sheets on the window to add more; empty layers are not drawn.",
                x, hy + 40, listW, Theme.ITEM_DISABLED);

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
