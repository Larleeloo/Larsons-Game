package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Game;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Variants;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.ui.Medieval;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Wearables;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A character's look, layer by layer, in the plain style of the menus: a row
 * for each layer - what is worn in it, stepped through with its arrows, and a
 * chip of its colour - and under the rows the colours of the layer picked:
 * the item's own few, then every colour of the palette ({@link
 * Wearables#PALETTE}).
 *
 * <p>For a player ({@link #draw} with her) it offers what she owns, and a
 * colour she has not unlocked takes a dye, as in the wardrobe ({@link
 * WardrobePanel}): the new game's Look page. With everything on ({@link
 * #setEverything}) it offers every item there are sprites for and every
 * colour: how the people of a level are dressed in the editor.
 */
public final class LookEditor {

    /** The layers in the order a face and an outfit are thought of: the skin, the head, then the clothes outward. */
    public static final List<Slot> ORDER = List.of(Slot.BODY, Slot.HAIR, Slot.EYEBROWS, Slot.EYES, Slot.NOSE,
            Slot.MOUTH, Slot.EARS, Slot.MAKEUP, Slot.HORNS, Slot.TAIL, Slot.SHIRT, Slot.PANTS, Slot.SHOES,
            Slot.UNDERWEAR, Slot.BRA, Slot.HAT, Slot.EYEWEAR, Slot.OTHER, Slot.BELT, Slot.GLOVES, Slot.NECKLACE, Slot.EARRINGS,
            Slot.WRISTWEAR, Slot.WRISTWEAR_LEFT, Slot.SHEATH, Slot.CHAINMAIL, Slot.CHAINMAIL_LEGS, Slot.CHESTPLATE,
            Slot.LEG_PLATING);

    private final Game game;
    private Medieval icons;
    private boolean everything;
    /** The layers whose item is not chosen here (the body, a people's horns): only their colour. */
    private Set<Slot> fixed = EnumSet.noneOf(Slot.class);
    /** Whether a layer with nothing to choose and no colour is left out. */
    private boolean compact;
    private Slot focus = Slot.HAIR;
    /** A locked colour of the focused layer clicked with a dye of it carried: offered to be used up, or null. */
    private String offered;

    public LookEditor(Game game) {
        this.game = game;
    }

    /** Offer every item there are sprites for and every colour (on), or what the player owns (off). */
    public void setEverything(boolean on) {
        everything = on;
    }

    public void setFixed(Set<Slot> slots) {
        fixed = slots.isEmpty() ? EnumSet.noneOf(Slot.class) : EnumSet.copyOf(slots);
    }

    public void setCompact(boolean on) {
        compact = on;
    }

    public Slot focus() { return focus; }

    public void focusOn(Slot slot) {
        if (focus != slot) offered = null;
        focus = slot;
    }

    // --- what there is to choose ------------------------------------------------------------

    /** The choices for {@code slot}: nothing, then what the player owns - or, with everything on, all there is art for. */
    public List<WardrobePanel.Option> options(Slot slot, Wardrobe wardrobe, Player player) {
        SpriteLibrary lib = game.sprites();
        if (!everything && player != null) return WardrobePanel.options(lib, slot, player);
        List<WardrobePanel.Option> out = new ArrayList<>();
        out.add(new WardrobePanel.Option(null, null));
        if (slot.carried()) return out;
        String body = wardrobe.get(Slot.BODY);
        if (Wearables.free(slot)) {
            for (String item : lib.items(body, slot)) {
                String base = Wardrobe.Style.base(item);
                if (Stance.ofItem(base) != null) continue;
                WardrobePanel.Option o = new WardrobePanel.Option(base, null);
                if (!out.contains(o)) out.add(o);
            }
            return out;
        }
        // every wearable's folder once (a tailored garment is its folder in a colour: the colour is picked here);
        // a metal's in each of its metals
        List<String> seen = new ArrayList<>();
        for (ItemDef d : Wearables.ALL) {
            if (!d.wearable().slots().contains(slot)) continue;
            for (String folder : d.wearable().folders()) {
                String key = folder + "/" + d.wearable().colour();
                if (seen.contains(key) || (Wearables.tailoredColour(d) != null && seen.contains(folder + "/null"))) {
                    continue;
                }
                if (lib.entry(body, slot, lib.styled(body, slot, folder, wardrobe.style())) == null) continue;
                seen.add(key);
                out.add(new WardrobePanel.Option(folder, Wearables.tailoredColour(d) != null ? null : d));
            }
        }
        // the armour made in the Forge: each piece its own choice, in its own colours
        for (com.larsons.game.world.Forged f : com.larsons.game.world.Forged.all()) {
            ItemDef d = f.def();
            if (d == null || !d.isWearable() || !d.wearable().slots().contains(slot)) continue;
            String folder = d.wearable().folders().get(0);
            if (lib.entry(body, slot, lib.styled(body, slot, folder, wardrobe.style())) == null) continue;
            out.add(new WardrobePanel.Option(folder, d, f.at(0)));
        }
        // what is worn there that is none of these (the armour, the clothes under it)
        for (boolean armour : new boolean[]{true, false}) {
            String worn = wardrobe.get(slot, armour);
            if (worn != null && WardrobePanel.current(out, slot, wardrobe, armour) < 0) {
                out.add(new WardrobePanel.Option(worn, null));
            }
        }
        return out;
    }

    /** The colours of what is worn in {@code slot}: its own options, then the palette's; unlocked or not. */
    public List<Wearables.Colour> colours(Slot slot, Wardrobe wardrobe, Player player) {
        return colours(slot, wardrobe, player, null);
    }

    /**
     * The colours of what is worn in {@code slot}'s {@code layer} (the armour,
     * the clothes under it; null: what is on top) - armour and a forged piece
     * in any colour of the palette.
     */
    public List<Wearables.Colour> colours(Slot slot, Wardrobe wardrobe, Player player, Boolean layer) {
        SpriteLibrary lib = game.sprites();
        if (!everything && player != null) return WardrobePanel.colours(lib, slot, player, layer);
        String item = WardrobePanel.worn(wardrobe, slot, layer);
        List<Wearables.Colour> out = new ArrayList<>();
        boolean forged = com.larsons.game.world.Armour.forgedId(WardrobePanel.wornId(wardrobe, slot, layer)) != null;
        if (item == null || Wearables.metal(slot, item) && !forged) return out;
        List<String> all = lib.colourOptions(wardrobe.get(Slot.BODY), slot, item, wardrobe.style());
        for (String c : all) out.add(new Wearables.Colour(c, true));
        if (!all.isEmpty() || Wardrobe.isArmour(item) || forged) {
            for (String c : Wearables.PALETTE) out.add(new Wearables.Colour(c, true));
        }
        return out;
    }

    /** The colour {@code option} of the item worn in {@code slot} shows as (RGB), or -1. */
    public int swatch(Slot slot, Wardrobe wardrobe, String option) {
        if (option == null) {
            if (slot != Slot.MOUTH) return -1;
            int skin = swatch(Slot.BODY, wardrobe, wardrobe.skin());
            return skin < 0 ? -1 : Variants.lips(skin);
        }
        if (Variants.isCustom(option)) return Wearables.rgb(option);
        return game.sprites().swatch(wardrobe.get(Slot.BODY), slot, wardrobe.get(slot), wardrobe.style(), option);
    }

    private String rowLabel(Slot slot) {
        return slot == Slot.BODY ? (fixed.contains(slot) ? "Skin" : "Body, skin") : slot == Slot.OTHER ? "Mantle"
                : slot.label();
    }

    private static String readable(String s) {
        if (s == null || s.isEmpty()) return "None";
        String t = s.replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** What the colour of {@code slot} is called: "Natural" for lips nobody painted, "Its own" for none picked. */
    private static String colourLabel(Slot slot, String colour) {
        if (colour == null) return slot == Slot.MOUTH ? "Natural" : "Its own";
        return Wearables.colourName(colour);
    }

    // --- drawing ---------------------------------------------------------------------------

    /**
     * Draw the rows and the colours in {@code (x, y, w, h)} and handle them.
     * {@code player} is who owns what is offered (null with everything on).
     * Returns whether the wardrobe changed.
     */
    public boolean draw(Ui ui, float x, float y, float w, float h, Wardrobe wardrobe, Player player) {
        if (icons == null) icons = new Medieval(ui);
        SpriteLibrary lib = game.sprites();
        boolean changed = false;
        List<Slot> rows = new ArrayList<>();
        for (Slot slot : ORDER) {
            if (slot == Slot.BODY && fixed.contains(slot) && wardrobe.get(slot) == null) continue;
            boolean choice = !fixed.contains(slot) && options(slot, wardrobe, player).size() > 1;
            boolean coloured = wardrobe.get(slot) != null && !colours(slot, wardrobe, player).isEmpty();
            if (compact && !choice && !coloured) continue;
            if (fixed.contains(slot) && wardrobe.get(slot) == null) continue;
            rows.add(slot);
        }
        if (!rows.contains(focus) && !rows.isEmpty()) focusOn(rows.get(0));

        float coloursH = 176;
        int perColumn = (rows.size() + 1) / 2;
        float rowH = Math.max(22, Math.min(32, (h - coloursH - 6) / Math.max(1, perColumn)));
        float gap = 18, colW = (w - gap) / 2, labelW = Math.min(96, colW * 0.27f), knob = Math.min(26, rowH - 4);
        for (int i = 0; i < rows.size(); i++) {
            Slot slot = rows.get(i);
            float rx = x + (i / perColumn) * (colW + gap), ry = y + (i % perColumn) * rowH;
            float bh = rowH - 4;
            boolean on = focus == slot;
            if (on) ui.rect(rx - 4, ry - 2, colW + 8, rowH, Theme.withAlpha(Theme.ACCENT, 0.10f));
            ui.text(ui.small, Ui.fit(ui.small, rowLabel(slot), labelW - 4), rx, ry + (bh - ui.small.lineHeight()) / 2,
                    on ? Theme.ACCENT : Theme.ITEM);
            List<WardrobePanel.Option> opts = options(slot, wardrobe, player);
            int at = WardrobePanel.current(opts, slot, wardrobe);
            int idx = Math.max(0, at);
            boolean any = !fixed.contains(slot) && opts.size() > 1;
            float kx = rx + labelW;
            List<Wearables.Colour> cols = colours(slot, wardrobe, player);
            float chip = 28;
            float stripW = colW - labelW - 2 * knob - 8 - chip - 4;
            if (ui.button("<", kx, ry, knob, bh, false, any)) {
                WardrobePanel.choose(wardrobe, slot, opts.get(Math.floorMod(idx - 1, opts.size())));
                Sounds.play(SoundKeys.ui("wear"));
                focusOn(slot);
                changed = true;
            }
            float sx = kx + knob + 4;
            ui.rect(sx, ry, stripW, bh, Theme.withAlpha(Theme.SOOT, 0.45f));
            String worn = wardrobe.get(slot);
            String name = at >= 0 ? opts.get(at).label(slot) : WardrobePanel.label(slot, worn);
            if (slot == Slot.BODY && worn == null) name = "None";
            // (where the body is not chosen here, its row is the skin's: it names the tone)
            else if (slot == Slot.BODY && fixed.contains(slot)) name = colourLabel(slot, wardrobe.skin());
            ui.text(ui.small, Ui.fit(ui.small, readable(name), stripW - 10), sx + 6,
                    ry + (bh - ui.small.lineHeight()) / 2, worn == null ? Theme.ITEM_DISABLED : Theme.TITLE);
            if (ui.button(">", sx + stripW + 4, ry, knob, bh, false, any)) {
                WardrobePanel.choose(wardrobe, slot, opts.get((idx + 1) % opts.size()));
                Sounds.play(SoundKeys.ui("wear"));
                focusOn(slot);
                changed = true;
            }
            // the chip of its colour: click it (or the row) to set its colours out below
            float cx = rx + colW - chip;
            if (!cols.isEmpty()) {
                int rgb = swatch(slot, wardrobe, wardrobe.colour(slot));
                ui.rect(cx, ry, chip, bh, Theme.SOOT);
                if (rgb >= 0) ui.rect(cx + 2, ry + 2, chip - 4, bh - 4, Theme.hex(rgb & 0xFFFFFF, 1f));
                else ui.text(ui.tiny, "own", cx + (chip - ui.tiny.width("own")) / 2, ry + (bh - ui.tiny.lineHeight()) / 2,
                        Theme.ITEM_DISABLED);
                ui.outline(cx, ry, chip, bh, on ? 2 : 1, on ? Theme.ACCENT : Theme.PANEL_EDGE);
                if (ui.clicked(cx, ry, chip, bh)) focusOn(slot);
            }
            if (ui.hover(rx, ry, labelW, bh) && ui.clicked(rx, ry, labelW, bh)) focusOn(slot);
        }

        // --- the colours of the layer picked -------------------------------------------------
        float cy = y + perColumn * rowH + 8;
        ui.rect(x, cy - 4, w, 1, Theme.PANEL_EDGE);
        String item = wardrobe.get(focus);
        List<Wearables.Colour> cols = colours(focus, wardrobe, player);
        String picked = item == null ? "nothing worn" : cols.isEmpty()
                ? (Wearables.metal(focus, item) ? "the metal's own" : "it has one colour")
                : colourLabel(focus, wardrobe.colour(focus));
        ui.text(ui.body, "Colour  ·  " + rowLabel(focus) + ": " + picked, x, cy + 2, cols.isEmpty() ? Theme.ITEM
                : Theme.ACCENT);
        if (offered != null && cols.stream().noneMatch(o -> o.option().equals(offered) && !o.unlocked())) offered = null;
        cy += 30;
        if (cols.isEmpty()) return changed;

        // "its own" (none picked), then every colour it can be as one grid in the palette's order -
        // the item's own shades among them, where they show (WardrobePanel.grid)
        float sh = 22;
        if (focus != Slot.BODY) {
            String own = focus == Slot.MOUTH ? "Natural" : "Its own";
            float ow = ui.small.width(own) + 18;
            if (ui.button(own, x, cy, ow, sh, wardrobe.colour(focus) == null, true) && wardrobe.colour(focus) != null) {
                wardrobe.setColour(focus, null);
                Sounds.play(SoundKeys.ui("wear"));
                changed = true;
            }
            cy += sh + 8;
        }
        List<WardrobePanel.Cell> cells = WardrobePanel.grid(cols, o -> swatch(focus, wardrobe, o));
        int columns = 25;
        float pw = (float) Math.floor((w + 2) / columns), ph = 20;
        for (int i = 0; i < cells.size(); i++) {
            float px = x + (i % columns) * pw, py = cy + (i / columns) * (ph + 2);
            changed |= chip(ui, px, py, pw - 2, ph, cells.get(i), wardrobe, player);
        }
        cy += ((cells.size() + columns - 1) / columns) * (ph + 2) + 4;

        // --- a dye, where the colour is not yet hers ---------------------------------------
        if (offered != null && player != null) {
            String dye = Wearables.colourName(offered);
            String on = readable(WardrobePanel.label(focus, item)).toLowerCase(Locale.ROOT);
            String ask = "Use up a " + dye + " dye to make the " + on + " " + dye.toLowerCase(Locale.ROOT) + " for good?";
            ui.text(ui.small, Ui.fit(ui.small, ask, w - 220), x, cy + 5, Theme.HINT);
            if (ui.button("Use dye", x + w - 210, cy, 100, 26)) {
                if (Wearables.useDye(wardrobe, player.inventory(), focus, offered)) {
                    Sounds.play(SoundKeys.ui("secret"));
                    changed = true;
                }
                offered = null;
            }
            if (ui.button("Keep it", x + w - 104, cy, 100, 26)) offered = null;
        } else if (!everything && cols.stream().anyMatch(c -> !c.unlocked())) {
            ui.text(ui.small, "A locked colour takes a dye of it: one dye colours this item for good.", x, cy + 5,
                    Theme.ITEM_DISABLED);
        }
        return changed;
    }

    /** One colour to click: lit when it is on, a padlock on one not unlocked (a bottle with a dye of it carried). */
    private boolean chip(Ui ui, float x, float y, float w, float h, WardrobePanel.Cell cell, Wardrobe wardrobe,
                         Player player) {
        Wearables.Colour c = cell.colour();
        int rgb = cell.rgb();
        boolean on = WardrobePanel.worn(cell, wardrobe.colour(focus)) || c.option().equals(offered);
        ui.rect(x, y, w, h, Theme.SOOT);
        ui.rect(x + 1, y + 1, w - 2, h - 2, rgb < 0 ? Theme.IRON : Theme.hex(rgb & 0xFFFFFF, 1f));
        int carried = 0;
        if (!c.unlocked()) {
            ui.rect(x + 1, y + 1, w - 2, h - 2, Theme.withAlpha(Theme.SOOT, 0.5f));
            if (player != null) {
                ItemDef d = Wearables.dye(c.option());
                for (var it : player.inventory()) if (it.def() == d) carried++;
            }
            if (carried > 0) icons.bottle(x + w / 2 - 3, y + h - 3, rgb < 0 ? 0xffffff : rgb);
            else icons.padlock(x + w / 2, y + h / 2 + 1);
        }
        if (on) ui.outline(x - 2, y - 2, w + 4, h + 4, 2, Theme.ACCENT);
        else if (ui.hover(x, y, w, h)) ui.outline(x - 1, y - 1, w + 2, h + 2, 1, Theme.TITLE);
        if (!ui.clicked(x, y, w, h)) return false;
        if (c.unlocked()) {
            wardrobe.setColour(focus, c.option());
            offered = null;
            Sounds.play(SoundKeys.ui("wear"));
            return true;
        }
        offered = carried > 0 ? c.option() : null;
        return false;
    }
}
