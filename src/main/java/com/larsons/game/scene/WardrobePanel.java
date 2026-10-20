package com.larsons.game.scene;

import com.larsons.game.core.Game;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Wearables;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The cosmetics editor, from the pause menu: every layer of the sprite stack
 * with what the player owns to wear in it, and a live preview that can be
 * turned through the eight directions, tipped through the three elevations
 * and played in all six states.
 *
 * <p>What is worn is items ({@link Wearables}): a layer offers the wearables
 * in her inventory - picked up, or out of a chest - and the underwear and the
 * bra, which are always hers, each by its name ("Silver wrist band"). What
 * she wears that she does not own (an outfit saved before) stays on and is
 * listed too; once taken off it is offered again only when she owns it. The
 * body, the ears, the eyes, the nose and the mouth are not items: every
 * folder found for the body under {@code assets/sprites/<slot>/} is offered,
 * as are the carried items for the hands.
 *
 * <p>A style row switches the whole character between the 512-pixel renders
 * and the pixel art made of them ({@link Wardrobe.Style}); the items' versions
 * in a style are not listed as items of their own, and an item that is only
 * pixel art is drawn from its 128-pixel version among the renders (marked
 * "(pixel art)"). An item with a colour
 * choice in the style (its {@code variants.json}) shows a swatch beside it:
 * click the swatch (or the layer's name) and pick the colour in the colour
 * row - for the base body that is the skin tone, which every item showing
 * skin follows. The colours are items too: a layer offers those she has a
 * dye for, the lips every colour with the lipstick, and a metal wearable only
 * its own (silver, gold); the skin tone and the eyes' colour are free. A
 * colour already applied stays, and is listed.
 *
 * <p>A hand row makes the character left- or right-handed (Auto:
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

    /**
     * One choice for a slot: the item folder it puts there (null: nothing),
     * and the wearable that is (null for a free layer's folder, a carried
     * item, or a folder no wearable puts there).
     */
    record Option(String item, ItemDef wearable) {

        /** What the row says: the wearable's name ("Silver wrist band"), else the folder's. */
        String label(Slot slot) {
            if (item == null || wearable == null) return WardrobePanel.label(slot, item);
            List<String> folders = wearable.wearable().folders();
            return folders.size() == 1 ? wearable.name() : wearable.name() + " (" + item.replace('_', ' ') + ")";
        }

        /** The colour it is always worn in (a metal's), or null. */
        String colour() {
            return wearable == null ? null : wearable.wearable().colour();
        }
    }

    /**
     * The choices for a slot: nothing (or the fallback body), then
     * <ul>
     *   <li>for a free layer (the body, the ears, the eyes, the nose, the
     *       mouth: {@link Wearables#FREE}) and the hands, every item folder
     *       found for the body worn - but not the stances' weapons (the
     *       battle axe, the bow, the crossbow), which are held by picking
     *       them up, not worn - and in the hands what she carries;</li>
     *   <li>for any other layer, the wearables she owns that go there
     *       ({@link Wearables#owned}): each of their folders (a sheath on
     *       either hip; a wrist band on either wrist) - and what she wears
     *       there now, owned or not.</li>
     * </ul>
     */
    static List<Option> options(SpriteLibrary lib, Slot slot, Player player) {
        List<Option> out = new ArrayList<>();
        out.add(new Option(null, null));
        Wardrobe wardrobe = player.wardrobe();
        if (Wearables.free(slot) || slot.carried()) {
            // the items drawn for the body worn (every body under the body itself)
            for (String item : lib.items(wardrobe.get(Slot.BODY), slot)) {
                String base = Wardrobe.Style.base(item);
                if (Stance.ofItem(base) != null) continue;
                Option o = new Option(base, null);
                if (!out.contains(o)) out.add(o);        // (an item with only pixel art is listed too)
            }
            if (slot.carried()) {
                for (com.larsons.game.world.Item carried : player.inventory()) {
                    Option o = new Option(carried.id(), null);
                    if (carried.def().inHand() && !out.contains(o) && Stance.ofItem(o.item()) == null) out.add(o);
                }
            }
            return out;
        }
        for (ItemDef d : Wearables.owned(player.inventory())) {
            if (!d.wearable().slots().contains(slot)) continue;
            for (String folder : d.wearable().folders()) {
                Option o = new Option(folder, d);
                if (!out.contains(o)) out.add(o);
            }
        }
        // what she wears and no longer owns stays on until it is taken off
        String worn = wardrobe.get(slot);
        if (worn != null && current(out, slot, wardrobe) < 0) {
            out.add(new Option(worn, Wearables.wornAs(slot, worn, wardrobe.colour(slot))));
        }
        return out;
    }

    /** Which of {@code options} is worn in {@code slot}: its folder, in its colour if it has one; or -1. */
    static int current(List<Option> options, Slot slot, Wardrobe wardrobe) {
        String item = wardrobe.get(slot), colour = wardrobe.colour(slot);
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            if (java.util.Objects.equals(o.item(), item) && (o.colour() == null || o.colour().equals(colour))) return i;
        }
        return -1;
    }

    /**
     * Wear {@code option} in {@code slot}: its folder, and a metal's colour
     * with it (taking a metal off takes its colour off too).
     */
    static void choose(Wardrobe wardrobe, Slot slot, Option option) {
        String was = wardrobe.get(slot);
        wardrobe.set(slot, option.item());
        if (option.colour() != null) wardrobe.setColour(slot, option.colour());
        else if (was != null && Wearables.metal(slot, was)) wardrobe.setColour(slot, null);
    }

    /**
     * The colours offered for what is worn in {@code slot}: its {@code
     * variants.json}'s, as far as she owns them ({@link Wearables#colours}).
     */
    static List<String> colours(SpriteLibrary lib, Slot slot, Player player) {
        Wardrobe wardrobe = player.wardrobe();
        String item = wardrobe.get(slot);
        if (item == null) return List.of();
        List<String> all = lib.colourOptions(wardrobe.get(Slot.BODY), slot, item, wardrobe.style());
        return Wearables.colours(slot, item, all, wardrobe.colour(slot), Wearables.owned(player.inventory()));
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
        String body = wardrobe.get(Slot.BODY);
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
        float colW = Math.min(250, (listW - (columns - 1) * 12) / columns), rowH = 36;
        for (int i = 0; i < slots.length; i++) {
            Slot slot = slots[i];
            float cx = x + (i / perColumn) * (colW + 12);
            float cy = y + (i % perColumn) * rowH;
            List<Option> opts = options(lib, slot, player);
            String current = wardrobe.get(slot);
            int at = current(opts, slot, wardrobe);
            int idx = Math.max(0, at);
            boolean missing = current != null && at < 0;
            String folder = current == null ? null : lib.styled(body, slot, current, style);
            boolean hasArt = current == null || lib.entry(body, slot, folder) != null;
            // an item that is only pixel art is drawn from it among the renders
            boolean asPixels = hasArt && current != null && style == Wardrobe.Style.RENDERED && !current.equals(folder);

            String name = slot == Slot.BODY ? "Base body · skin" : slot.label();
            if (ui.clicked(cx, cy, colW, 14)) focus = slot;
            ui.text(ui.small, name, cx, cy, focus == slot ? Theme.ACCENT : Theme.ITEM_DISABLED);
            boolean any = opts.size() > 1;
            if (ui.button("<", cx, cy + 15, 22, 20, false, any)) {
                choose(wardrobe, slot, opts.get(Math.floorMod(idx - 1, opts.size())));
                focus = slot;
            }
            List<String> colours = colours(lib, slot, player);
            float swW = colours.isEmpty() ? 0 : 24;
            String shown = at < 0 ? label(slot, current) : opts.get(at).label(slot);
            String text = missing ? current + " (missing)" : !hasArt ? shown + " (no art)"
                    : asPixels ? shown + " (pixel art)" : shown;
            float[] c = missing || !hasArt ? Theme.WARNING : current == null ? Theme.ITEM_DISABLED : Theme.ITEM_SELECTED;
            ui.text(ui.body, fit(ui, text, colW - 60 - swW), cx + 28, cy + 15, c);
            if (ui.button(">", cx + colW - 26 - swW, cy + 15, 22, 20, false, any)) {
                choose(wardrobe, slot, opts.get((idx + 1) % opts.size()));
                focus = slot;
            }
            if (!colours.isEmpty()) {
                // the colour picked for it (the item's own colours: an outline)
                float sx = cx + colW - 20, sy = cy + 16;
                String picked = wardrobe.colour(slot);
                int sw = picked == null ? -1 : lib.swatch(body, slot, current, style, picked);
                if (sw >= 0) ui.rect(sx, sy, 18, 18, rgb(sw));
                ui.outline(sx, sy, 18, 18, focus == slot ? 2 : 1, focus == slot ? Theme.ACCENT : Theme.PANEL_EDGE);
                if (ui.clicked(sx, sy, 18, 18)) focus = slot;
            }
        }
        float by0 = y + perColumn * rowH + 4;

        // --- the colour row: the focused layer's colours -----------------------------
        String fItem = wardrobe.get(focus);
        List<String> fOpts = colours(lib, focus, player);
        boolean anyInStyle = fItem != null && !lib.colourOptions(body, focus, fItem, style).isEmpty();
        String what = focus == Slot.BODY ? "Skin" : focus.label();
        String picked = fItem == null ? "nothing worn"
                : !fOpts.isEmpty() ? colourName(wardrobe.colour(focus))
                : Wearables.metal(focus, fItem) ? colourName(wardrobe.colour(focus)) + " (the metal's own)"
                : !anyInStyle ? "none in this style"
                : focus == Slot.MOUTH ? "own colours (the lipstick has the rest)"
                : "own colours (dyes add more)";
        ui.text(ui.body, "Colour · " + what + ": " + picked,
                x, by0 + 4, fOpts.isEmpty() ? Theme.ITEM_DISABLED : Theme.ITEM);
        float sy = by0 + 24;
        if (!fOpts.isEmpty()) {
            float s = 22, gap = 4;
            if (ui.button("-", x, sy, s, s, wardrobe.colour(focus) == null, true)) wardrobe.setColour(focus, null);
            for (int i = 0; i < fOpts.size(); i++) {
                float bx = x + (i + 1) * (s + gap);
                if (bx + s > x + listW) break;
                String o = fOpts.get(i);
                int rgb = lib.swatch(body, focus, fItem, style, o);
                ui.rect(bx, sy, s, s, rgb >= 0 ? rgb(rgb) : Theme.BUTTON);
                boolean on = o.equals(wardrobe.colour(focus));
                ui.outline(bx, sy, s, s, on ? 2 : 1, on ? Theme.ACCENT : ui.hover(bx, sy, s, s) ? Theme.HINT : Theme.PANEL_EDGE);
                if (ui.clicked(bx, sy, s, s)) wardrobe.setColour(focus, o);
            }
        }

        // --- the style: the renders, or the pixel art made of them -------------------
        float stY = sy + 30;
        ui.text(ui.body, "Style", x, stY + 6, Theme.ITEM);
        Wardrobe.Style[] styles = Wardrobe.Style.values();
        for (int i = 0; i < styles.length; i++) {
            if (ui.button(styles[i].label(), x + 70 + i * 116, stY, 110, 30, style == styles[i], true)) {
                wardrobe.setStyle(styles[i]);
            }
        }
        // --- the hand that leads -----------------------------------------------------
        float hy = stY + 34;
        ui.text(ui.body, "Hand", x, hy + 6, Theme.ITEM);
        Wardrobe.Hand[] hands = Wardrobe.Hand.values();
        for (int i = 0; i < hands.length; i++) {
            if (ui.button(hands[i].label(), x + 70 + i * 116, hy, 110, 30, wardrobe.hand() == hands[i], true)) {
                wardrobe.setHand(hands[i]);
            }
        }
        ui.text(ui.small, wardrobe.leftHanded() ? "left-handed" : "right-handed",
                x + 70 + hands.length * 116 + 8, hy + 8, Theme.HINT);
        ui.paragraph(ui.small, "Items come from your inventory - picked up, or out of a chest - and the "
                        + "underwear and bra are always yours. Dyes and lipstick unlock colours. Empty layers "
                        + "are not drawn.",
                x, hy + 36, listW, Theme.ITEM_DISABLED);

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
        // the state: one of many, stepped through
        AnimState[] states = AnimState.values();
        int at = state.ordinal(), step = 0;
        if (ui.button("<", px, by, 34, 30)) step = -1;
        if (ui.button(">", px + pw - 34, by, 34, 30)) step = 1;
        if (step != 0) {
            state = states[Math.floorMod(at + step, states.length)];
            time = 0;
        }
        ui.textCentered(ui.body, state.label() + "   (" + (state.ordinal() + 1) + " / " + states.length + ")",
                px + pw / 2f, by + 5, Theme.ITEM);
    }

    private static String fit(Ui ui, String s, float width) {
        if (ui.body.width(s) <= width) return s;
        while (s.length() > 1 && ui.body.width(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
