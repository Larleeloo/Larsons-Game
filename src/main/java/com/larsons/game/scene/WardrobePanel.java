package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Game;
import com.larsons.game.gfx.Font;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.ui.Medieval;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Wearables;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The cosmetics editor, from the pause menu, drawn as an oak armoire standing
 * open ({@link Medieval}): every worn layer of the sprite stack on its shelf
 * with what the player owns to wear in it, and her reflection in a gilded
 * mirror, which can be turned through the eight directions, tipped through
 * the three elevations and played in every state.
 *
 * <p>What is worn is items ({@link Wearables}): a layer offers the wearables
 * in her inventory - picked up, or out of a chest - and the underwear, the
 * bra and the starter look, which are always hers, each by its name ("Silver
 * wrist band"). What she wears that she does not own (an outfit saved before)
 * stays on and is listed too; once taken off it is offered again only when
 * she owns it. The body, the ears, the eyes, the nose and the mouth are not
 * items: every folder found for the body under {@code assets/sprites/<slot>/}
 * is offered. What is in her hands - the sword, the shield - is not the
 * wardrobe's: the hotbar puts it there ({@link #SLOTS}).
 *
 * <p>A style row switches the whole character between the 512-pixel renders
 * and the pixel art made of them ({@link Wardrobe.Style}); the items' versions
 * in a style are not listed as items of their own, and an item that is only
 * pixel art is drawn from its 128-pixel version among the renders (marked
 * "(pixel art)"). An item with a colour choice in the style (its {@code
 * variants.json}) shows a gem beside it: click the gem (or the layer's name)
 * and its colours are set out on the dye shelf - for the base body that is the
 * skin tone, which every item showing skin follows.
 *
 * <p>The colours are unlocked with dyes ({@link Wearables#colours}): a colour
 * not yet unlocked for the item is shown locked, and with a dye of it in her
 * bag, clicking it offers to use the dye up and unlock that colour on that
 * item for good ({@link Wearables#useDye}). The lips take every colour with
 * the lipstick, a metal wearable only its own (silver, gold); the skin tone
 * and the eyes' colour are free. A colour already applied stays.
 *
 * <p>A hand row makes the character left- or right-handed (Auto:
 * left-handed while the sword is in the left hand only).
 *
 * <p>Changes apply to the character immediately and are saved to
 * {@code config/wardrobe.json} when the menu closes.
 */
final class WardrobePanel {

    /** The layers the wardrobe dresses: every one but the hands, which hold what the hotbar gives them. */
    static final List<Slot> SLOTS = slots();

    private final Game game;
    private final LayerStack.Memory previewMemory = new LayerStack.Memory();
    private Medieval skin;
    private Facing facing = Facing.SOUTH_EAST;
    private Elevation elevation = Elevation.MIDDLE;
    private AnimState state = AnimState.IDLE;
    private boolean spin = true;
    private double time, spinTimer;
    /** The layer whose colours the dye shelf shows. */
    private Slot focus = Slot.HAIR;
    /** A locked colour of the focused layer she has clicked and has a dye of: offered to be used up, or null. */
    private String offered;

    WardrobePanel(Game game) {
        this.game = game;
    }

    private static List<Slot> slots() {
        List<Slot> out = new ArrayList<>();
        for (Slot s : Slot.values()) if (!s.carried()) out.add(s);
        return List.copyOf(out);
    }

    /**
     * One choice for a slot: the item folder it puts there (null: nothing),
     * and the wearable that is (null for a free layer's folder, or a folder
     * no wearable puts there).
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
     *       mouth: {@link Wearables#FREE}), every item folder found for the
     *       body worn;</li>
     *   <li>for any other layer, the wearables she owns that go there
     *       ({@link Wearables#owned}): each of their folders (a sheath on
     *       either hip; a wrist band on either wrist) - and what she wears
     *       there now, owned or not.</li>
     * </ul>
     * The hands are not the wardrobe's: they offer nothing.
     */
    static List<Option> options(SpriteLibrary lib, Slot slot, Player player) {
        List<Option> out = new ArrayList<>();
        out.add(new Option(null, null));
        if (slot.carried()) return out;
        Wardrobe wardrobe = player.wardrobe();
        if (Wearables.free(slot)) {
            // the items drawn for the body worn (every body under the body itself)
            for (String item : lib.items(wardrobe.get(Slot.BODY), slot)) {
                String base = Wardrobe.Style.base(item);
                if (Stance.ofItem(base) != null) continue;
                Option o = new Option(base, null);
                if (!out.contains(o)) out.add(o);        // (an item with only pixel art is listed too)
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
     * The colours of what is worn in {@code slot}: its {@code variants.json}'s,
     * each unlocked or not ({@link Wearables#colours}).
     */
    static List<Wearables.Colour> colours(SpriteLibrary lib, Slot slot, Player player) {
        Wardrobe wardrobe = player.wardrobe();
        String item = wardrobe.get(slot);
        if (item == null) return List.of();
        List<String> all = lib.colourOptions(wardrobe.get(Slot.BODY), slot, item, wardrobe.style());
        return Wearables.colours(slot, item, all, wardrobe.colour(slot), Wearables.owned(player.inventory()),
                wardrobe);
    }

    /**
     * Click colour {@code c} of what is worn in {@code slot}: put it on if it
     * is unlocked; otherwise, with a dye of it in her bag, offer it (the
     * caller asks before the dye is used up); otherwise nothing. Returns the
     * colour now offered, or null.
     */
    static String pick(Wardrobe wardrobe, Player player, Slot slot, Wearables.Colour c) {
        if (c.unlocked()) {
            wardrobe.setColour(slot, c.option());
            return null;
        }
        return Wearables.dyeIn(player.inventory(), c.option()) != null ? c.option() : null;
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

    /** How many dyes of {@code colour} she carries. */
    private static int dyes(Player player, String colour) {
        ItemDef d = Wearables.dye(colour);
        int n = 0;
        for (Item item : player.inventory()) if (item.def() == d) n++;
        return n;
    }

    // --- drawing ---------------------------------------------------------------------------

    /**
     * Draw the whole page - the armoire filling {@code x, y, w, h}, its
     * title on its cornice - and handle it. Returns whether its Back button
     * was clicked.
     */
    boolean draw(Ui ui, double dt, float x, float y, float w, float h, Player player) {
        if (skin == null) skin = new Medieval(ui);
        Medieval m = skin;
        time += dt;
        if (spin) {
            spinTimer += dt;
            if (spinTimer > 0.9) {
                spinTimer = 0;
                facing = facing.counterClockwise();
            }
        }

        // --- the armoire: its doors open, its boards, its cornice and its iron --------
        float door = Math.min(84, (ui.width() - w) / 2 - 4);
        if (door > 30) {
            m.door(x, y + 30, h - 40, door, -1);
            m.door(x + w, y + 30, h - 40, door, 1);
        }
        m.shadow(x, y, w, h, 12);
        m.boards(x, y, w, h, 96, 0.92f);
        m.bevel(x, y, w, h, 4, 1f);
        float corniceH = 66;
        m.moulding(x - 6, y - 6, w + 12, 16, 0.85f);
        m.moulding(x + 4, y + 10, w - 8, 12, 0.72f);
        m.moulding(x + 10, y + corniceH - 14, w - 20, 10, 0.78f);
        float pw = 300, ph = 44;
        m.plaque(x + (w - pw) / 2, y + 14, pw, ph);
        m.carvedCentred(m.title, "Wardrobe", x + w / 2, y + 14 + (ph - m.title.lineHeight()) / 2, Medieval.GOLD);
        boolean back = m.button("Back", x + w - 128, y + 22, 100, 34, false, true);
        m.bracket(x, y, 54, 1, 1);
        m.bracket(x + w, y, 54, -1, 1);
        m.bracket(x, y + h, 54, 1, -1);
        m.bracket(x + w, y + h, 54, -1, -1);

        // --- the open cabinet: a recess with the shelves on the left, the mirror on the right
        float ix = x + 20, iy = y + corniceH + 6, iw = w - 40, ih = h - corniceH - 26;
        m.recess(ix, iy, iw, ih, 14);
        float cx = ix + 26, cy = iy + 24, cw = iw - 52, ch = ih - 40;
        float mirrorW = Math.min(320, cw * 0.33f);
        float listW = cw - mirrorW - 24;
        shelves(ui, m, cx, cy, listW, player);
        mirror(ui, m, cx + cw - mirrorW, cy - 6, mirrorW, ch + 10, player);
        return back;
    }

    /** The layers on their shelves, the dye shelf under them, the style and the hand, and a note. */
    private void shelves(Ui ui, Medieval m, float x, float y, float w, Player player) {
        Wardrobe wardrobe = player.wardrobe();
        SpriteLibrary lib = game.sprites();
        Wardrobe.Style style = wardrobe.style();
        String body = wardrobe.get(Slot.BODY);

        int columns = 3;
        int perColumn = (SLOTS.size() + columns - 1) / columns;
        float gap = 14, colW = (w - (columns - 1) * gap) / columns, rowH = 36, knob = 20;
        for (int i = 0; i < SLOTS.size(); i++) {
            Slot slot = SLOTS.get(i);
            float sx = x + (i / perColumn) * (colW + gap);
            float sy = y + (i % perColumn) * rowH;
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
            if (ui.clicked(sx, sy, colW, 14)) focusOn(slot);
            m.carved(m.small, name.toUpperCase(Locale.ROOT), sx + 2, sy, focus == slot ? Medieval.GOLD : Medieval.BRASS);
            boolean any = opts.size() > 1;
            float ry = sy + 15;
            if (m.knob("<", sx, ry, knob, any)) {
                choose(wardrobe, slot, opts.get(Math.floorMod(idx - 1, opts.size())));
                Sounds.play(SoundKeys.ui("wear"));
                focusOn(slot);
            }
            List<Wearables.Colour> colours = colours(lib, slot, player);
            float gemW = colours.isEmpty() ? 0 : 24;
            float stripX = sx + knob + 3, stripW = colW - 2 * knob - 6 - gemW;
            m.parchment(stripX, ry, stripW, knob);
            String shown = at < 0 ? label(slot, current) : opts.get(at).label(slot);
            String text = missing ? current + " (missing)" : !hasArt ? shown + " (no art)"
                    : asPixels ? shown + " (pixel art)" : shown;
            float[] c = missing || !hasArt ? Medieval.EMBER : current == null ? Medieval.INK_FADED : Medieval.INK;
            ui.text(m.body, fit(m.body, text, stripW - 10), stripX + 5, ry + (knob - m.body.lineHeight()) / 2, c);
            if (m.knob(">", sx + colW - knob - gemW, ry, knob, any)) {
                choose(wardrobe, slot, opts.get((idx + 1) % opts.size()));
                Sounds.play(SoundKeys.ui("wear"));
                focusOn(slot);
            }
            if (!colours.isEmpty()) {
                // the colour picked for it (its own colours: an empty bezel)
                float gx = sx + colW - 10, gy = ry + knob / 2;
                String picked = wardrobe.colour(slot);
                int sw = picked == null ? -1 : lib.swatch(body, slot, current, style, picked);
                m.gem(gx, gy, 10, sw, true);
                if (focus == slot) ui.outline(gx - 12, gy - 12, 24, 24, 1, Medieval.GOLD);
                if (ui.clicked(gx - 10, gy - 10, 20, 20)) focusOn(slot);
            }
        }
        float shelfY = y + perColumn * rowH + 2;
        m.shelf(x - 8, shelfY, w + 16);

        // --- the dye shelf: the focused layer's colours ----------------------------------
        float dy = shelfY + 26;
        String fItem = wardrobe.get(focus);
        List<Wearables.Colour> fOpts = colours(lib, focus, player);
        boolean anyInStyle = fItem != null && !lib.colourOptions(body, focus, fItem, style).isEmpty();
        String what = focus == Slot.BODY ? "Skin" : focus.label();
        String picked = fItem == null ? "nothing worn"
                : !fOpts.isEmpty() ? colourName(wardrobe.colour(focus))
                : Wearables.metal(focus, fItem) ? colourName(wardrobe.colour(focus)) + " (the metal's own)"
                : !anyInStyle ? "none in this style"
                : "own colours";
        if (offered != null && fOpts.stream().noneMatch(o -> o.option().equals(offered) && !o.unlocked())) {
            offered = null;
        }
        m.carved(m.heading, "Colour · " + what + ": " + picked, x, dy, fOpts.isEmpty() ? Medieval.BRASS : Medieval.GOLD);
        float gy = dy + 36, r = 13, step = 2 * r + 9;
        if (!fOpts.isEmpty()) {
            if (m.knob("-", x, gy - 10, 20, wardrobe.colour(focus) != null)) wardrobe.setColour(focus, null);
            for (int i = 0; i < fOpts.size(); i++) {
                float gx = x + 40 + i * step;
                if (gx + r > x + w) break;
                Wearables.Colour o = fOpts.get(i);
                int rgb = lib.swatch(body, focus, fItem, style, o.option());
                int carried = o.unlocked() ? 0 : dyes(player, o.option());
                m.gem(gx, gy, r, rgb, o.unlocked() || carried > 0);
                boolean on = o.option().equals(wardrobe.colour(focus)) || o.option().equals(offered);
                if (on) ui.outline(gx - r - 3, gy - r - 3, 2 * r + 6, 2 * r + 6, 2, Medieval.GOLD);
                else if (ui.hover(gx - r, gy - r, 2 * r, 2 * r)) ui.outline(gx - r - 2, gy - r - 2, 2 * r + 4, 2 * r + 4, 1, Medieval.CREAM);
                if (!o.unlocked()) {
                    if (carried > 0) {
                        m.bottle(gx + r - 4, gy + r + 2, rgb >= 0 ? rgb : 0xffffff);
                        if (carried > 1) ui.text(m.small, "x" + carried, gx + r - 2, gy - r - 8, Medieval.CREAM);
                    } else {
                        m.padlock(gx + r - 5, gy + r - 6);
                    }
                }
                if (ui.clicked(gx - r, gy - r, 2 * r, 2 * r)) {
                    offered = pick(wardrobe, player, focus, o);
                    if (o.unlocked()) Sounds.play(SoundKeys.ui("wear"));
                }
            }
        }
        float ny = gy + r + 10;
        if (offered != null) {
            String dye = colourName(offered);
            String on = focus == Slot.BODY ? "the skin" : current(focus, player);
            String ask = "Use up a " + dye + " dye to make " + on + " " + dye.toLowerCase(Locale.ROOT) + " for good?";
            ui.text(m.small, fit(m.small, ask, w - 214), x, ny + 6, Medieval.CREAM);
            if (m.button("Use dye", x + w - 204, ny - 2, 100, 30, false, true)) {
                if (Wearables.useDye(wardrobe, player.inventory(), focus, offered)) {
                    Sounds.play(SoundKeys.ui("secret"));
                    game.toast(dye + " unlocked for " + on, com.larsons.game.world.Rarity.RARE.colour(1f));
                    game.saveWardrobe();
                }
                offered = null;
            }
            if (m.button("Keep it", x + w - 100, ny - 2, 100, 30, false, true)) offered = null;
        } else if (!fOpts.isEmpty()) {
            ui.text(m.small, "Locked colours take a dye of their colour: one dye unlocks it on this item for good.",
                    x, ny + 6, Theme.withAlpha(Medieval.CREAM, 0.75f));
        }

        // --- the style and the hand, side by side ----------------------------------------
        float stY = ny + 34;
        float bw = Math.min(92, (w - 130) / 6 - 4);
        m.carved(m.heading, "Style", x, stY + 4, Medieval.BRASS);
        Wardrobe.Style[] styles = Wardrobe.Style.values();
        for (int i = 0; i < styles.length; i++) {
            if (m.button(styles[i].label(), x + 58 + i * (bw + 4), stY, bw, 30, style == styles[i], true)) {
                wardrobe.setStyle(styles[i]);
            }
        }
        float hx = x + 58 + styles.length * (bw + 4) + 14;
        m.carved(m.heading, "Hand", hx, stY + 4, Medieval.BRASS);
        hx += m.heading.width("Hand") + 8 - 54;
        Wardrobe.Hand[] hands = Wardrobe.Hand.values();
        for (int i = 0; i < hands.length; i++) {
            String l = hands[i] == Wardrobe.Hand.AUTO ? "Auto (" + (wardrobe.leftHanded() ? "L" : "R") + ")"
                    : hands[i].label();
            if (m.button(l, hx + 54 + i * (bw + 4), stY, bw, 30, wardrobe.hand() == hands[i], true)) {
                wardrobe.setHand(hands[i]);
            }
        }
    }

    /** What the focused layer wears, by name: "the Baggy shirt". */
    private String current(Slot slot, Player player) {
        List<Option> opts = options(game.sprites(), slot, player);
        int at = current(opts, slot, player.wardrobe());
        return "the " + (at < 0 ? label(slot, player.wardrobe().get(slot)) : opts.get(at).label(slot));
    }

    private void focusOn(Slot slot) {
        if (focus != slot) offered = null;
        focus = slot;
    }

    /** Her reflection in the mirror, and the controls that turn it. */
    private void mirror(Ui ui, Medieval m, float x, float y, float w, float h, Player player) {
        Wardrobe wardrobe = player.wardrobe();
        SpriteLibrary lib = game.sprites();
        float controls = 80;
        float[] glass = m.mirror(x, y, w, h - controls);
        float gx = glass[0], gy = glass[1], gw = glass[2], gh = glass[3];
        SpriteView view = new SpriteView(elevation, facing, elevation.renderAngle());
        double duration = LayerStack.duration(lib, wardrobe, state, view);
        double t = state.loops() ? time : time % (duration + 0.4);
        LayerStack.Result r = LayerStack.resolve(lib, wardrobe, state, t, view, previewMemory);
        float ppm = (float) Math.min((gh - 40) / r.framing().frameWorldHeight(),
                (gw - 10) / r.framing().frameWorldSize());
        double[] anchor = r.framing().anchor(elevation);
        float frameH = (float) (r.framing().frameWorldHeight() * ppm);
        float footY = gy + 10 + (gh - 30 - frameH) / 2 + (float) anchor[1] * frameH;
        // her shadow on the floor the mirror shows
        ui.batch().rect(ui.white(), gx + gw / 2 - 40, footY - 4, 80, 8, 0, 0, 1, 1, 0, 0, 0, 0.25f);
        LayerStack.drawFlat(ui.batch(), r, gx + gw / 2, footY, ppm);
        String caption = facing.key().toUpperCase() + " · " + elevation.label() + " · frame " + (r.frame() + 1)
                + "/" + r.frames();
        ui.text(m.small, caption, gx + (gw - m.small.width(caption)) / 2, gy + gh - 22, Theme.withAlpha(Medieval.CREAM, 0.7f));

        // the controls, on the mirror's stand
        float by = y + h - controls + 10;
        if (m.button("<", x, by, 36, 30, false, true)) {
            facing = facing.clockwise();
            spin = false;
        }
        if (m.button(spin ? "Stop" : "Spin", x + 40, by, 66, 30, false, true)) spin = !spin;
        if (m.button(">", x + 110, by, 36, 30, false, true)) {
            facing = facing.counterClockwise();
            spin = false;
        }
        float ew = (w - 154) / Elevation.values().length;
        for (int i = 0; i < Elevation.values().length; i++) {
            Elevation e = Elevation.values()[i];
            if (m.button(e.key(), x + 154 + i * ew, by, ew - 4, 30, e == elevation, true)) elevation = e;
        }
        by += 36;
        // the state: one of many, stepped through
        AnimState[] states = AnimState.values();
        int at = state.ordinal(), step = 0;
        if (m.button("<", x, by, 36, 30, false, true)) step = -1;
        if (m.button(">", x + w - 36, by, 36, 30, false, true)) step = 1;
        if (step != 0) {
            state = states[Math.floorMod(at + step, states.length)];
            time = 0;
        }
        m.parchment(x + 42, by, w - 84, 30);
        String s = state.label() + "  (" + (state.ordinal() + 1) + " / " + states.length + ")";
        ui.text(m.body, fit(m.body, s, w - 94), x + w / 2 - Math.min(m.body.width(s), w - 94) / 2,
                by + (30 - m.body.lineHeight()) / 2, Medieval.INK);
    }

    private static String fit(Font font, String s, float width) {
        if (font.width(s) <= width) return s;
        while (s.length() > 1 && font.width(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
