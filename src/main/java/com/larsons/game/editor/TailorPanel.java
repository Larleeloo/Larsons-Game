package com.larsons.game.editor;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Game;
import com.larsons.game.gfx.Batch;
import com.larsons.game.sprite.ItemSprites;
import com.larsons.game.sprite.Slot;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Armour;
import com.larsons.game.world.Gear;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Tailor;
import com.larsons.game.world.Tier;
import com.larsons.game.world.Upgrades;
import com.larsons.game.world.Wearables;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Creative Mode's <b>Tailor</b>, its <i>Every piece</i> page: every piece
 * of clothing and armour the game has ({@link Tailor#pieces}), and what it
 * gives worn at each of its tiers - any of the gear's special effects
 * ({@link Gear.Effect}), each set with its - / +, and the tier's own name if
 * wanted. Upgrading a piece with ingots (right-click it in the inventory)
 * takes it a tier up, and it gives what that tier says. Kept as it is changed
 * ({@link Tailor#save}), for every level.
 */
final class TailorPanel {

    private final Game game;
    private final Widgets widgets;
    /** The level's armour rules (combat.json's): the dice each rarity rolls. */
    private final java.util.function.Supplier<Armour.Rules> rules;
    /** The piece being tailored (in {@link Tailor#pieces}), and its tier (from 0). */
    private int piece, tier;
    /** A tier's name being typed, kept until the typing ends. */
    private String naming;

    TailorPanel(Game game, Widgets widgets, java.util.function.Supplier<Armour.Rules> rules) {
        this.game = game;
        this.widgets = widgets;
        this.rules = rules;
    }

    private Armour.Rules rules() {
        Armour.Rules r = rules == null ? null : rules.get();
        return r != null ? r : Armour.Rules.defaults();
    }

    /** The piece being tailored. */
    ItemDef def() {
        List<ItemDef> all = Tailor.pieces();
        piece = Math.floorMod(piece, all.size());
        return all.get(piece);
    }

    /** Tailor {@code id} (the script's {@code tailor piece <id>}), at its tier {@code n} (from 0). */
    boolean pick(String id, int n) {
        List<ItemDef> all = Tailor.pieces();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(id)) {
                piece = i;
                tier = Math.max(0, n);
                return true;
            }
        }
        return false;
    }

    int tier() {
        return tier;
    }

    void tier(int n) {
        tier = Math.max(0, n);
    }

    /** What a piece is called in the list: its name, and whether it is armour. */
    static String label(ItemDef d) {
        return Armour.sides(d) > 0 ? d.name() + " (armour)" : d.name();
    }

    /** Where a piece is worn, in a word: "head", "shirt", "wrists". */
    static String where(ItemDef d) {
        List<Slot> slots = d.wearable().slots();
        if (slots.isEmpty()) return "worn";
        Armour.Location at = Armour.location(slots.get(0));
        if (Armour.sides(d) > 0 && at != null) return at.key();
        return switch (slots.get(0)) {
            case WRISTWEAR, WRISTWEAR_LEFT -> "wrists";
            case OTHER -> "back";
            default -> slots.get(0).name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    // --- the left panel ------------------------------------------------------------------

    /** The page: the piece, its tiers, and what the chosen one gives. */
    void panel(Ui ui, float x, float y, float w) {
        List<ItemDef> all = Tailor.pieces();
        List<String> names = new ArrayList<>();
        for (ItemDef d : all) names.add(label(d));
        int was = Math.floorMod(piece, all.size());
        int now = widgets.choice(ui, "Piece", names, was, x, y, w, 0.24f);
        if (now != was) {
            piece = now;
            tier = 0;
            naming = null;
        }
        y += 34;
        ItemDef d = all.get(Math.floorMod(piece, all.size()));
        List<Tier> tiers = Tailor.tiers(d);
        tier = Math.max(0, Math.min(tiers.size() - 1, tier));
        boolean armour = Armour.sides(d) > 0;
        String kind = (armour ? "Armour" : "Clothing") + " · " + where(d) + " · found "
                + d.wearable().rarity().label() + " · " + tiers.size() + (tiers.size() == 1 ? " tier" : " tiers");
        ui.text(ui.small, Ui.fit(ui.small, kind, w), x, y, Theme.HINT);
        y += 22;

        // its tiers: a button each, in its rarity's colour
        float tw = Math.min(44, (w - (tiers.size() - 1) * 3) / Math.max(1, tiers.size()));
        for (int i = 0; i < tiers.size(); i++) {
            float bx = x + i * (tw + 3);
            if (ui.button(String.valueOf(i + 1), bx, y, tw, 26, tier == i, true) && tier != i) {
                tier = i;
                naming = null;
                widgets.stopTyping();
            }
            ui.rect(bx + 4, y + 22, tw - 8, 3, tiers.get(i).rarity().colour(tier == i ? 1f : 0.45f));
        }
        y += 34;
        Tier t = tiers.get(tier);
        List<Tailor.Rung> rungs = new ArrayList<>(Tailor.rungs(d));
        Tailor.Rung r = rungs.get(tier);

        // its name at this tier ("" : the tier's own)
        String fallback = Tier.row(d.id()).names().get(t);
        String id = "tailor-name-" + d.id() + "-" + tier;
        String typed = widgets.text(ui, id, "Name", naming != null ? naming : r.name(), x, y, w, fallback);
        if (widgets.typingIn(id)) {
            naming = typed;
        } else if (naming != null) {
            String n = naming.strip().equals(fallback) ? "" : naming.strip();
            naming = null;
            if (!n.equals(r.name())) {
                r = r.withName(n);
                rungs.set(tier, r);
                save(d, rungs);
            }
        }
        y += 34;
        Item at = Item.of(d, t);
        int cost = tier == 0 ? 0 : Upgrades.cost(t.rarity());
        String line = t.rarity().label() + " · " + Tier.label(d, t)
                + (armour ? " · blocks with " + rules().dice(t.rarity()) + "d" + Armour.sides(d) : "")
                + (tier == 0 ? " · as it is found" : " · " + cost + (cost == 1 ? " ingot" : " ingots") + " to reach");
        y += ui.paragraph(ui.small, line, x, y, w, at.rarity().colour(1f)) + 8;

        ui.text(ui.small, "What it gives worn at tier " + (tier + 1), x, y, Theme.ACCENT);
        y += 22;
        Map<Gear.Effect, Double> g = gearRows(ui, r.gear(), x, y, w);
        y += gearRowsHeight();
        if (!g.equals(r.gear())) {
            rungs.set(tier, r.withGear(g));
            save(d, rungs);
        }
        y += 6;

        // help with the tiers above
        float bw = (w - 6) / 2;
        boolean top = tier >= tiers.size() - 1;
        if (ui.button("Same up the tiers", x, y, bw, 28, false, !top)) {
            save(d, Tailor.upTheTiers(rungs, tier, false));
            game.toast("Every tier above " + (tier + 1) + " gives the same", Theme.HINT);
        }
        if (ui.button("Grow up the tiers", x + bw + 6, y, bw, 28, false, !top)) {
            save(d, Tailor.upTheTiers(rungs, tier, true));
            game.toast("Every tier above " + (tier + 1) + " gives more, tier by tier", Theme.HINT);
        }
        y += 34;
        if (ui.button("Nothing at this tier", x, y, bw, 28, false, !r.gear().isEmpty())) {
            rungs.set(tier, r.withGear(Map.of()));
            save(d, rungs);
        }
        if (ui.button("Its default", x + bw + 6, y, bw, 28, false, Tailor.edited(d))) {
            try {
                Tailor.reset(d);
                Sounds.play(SoundKeys.ui("remove"));
                game.toast(d.name() + ": back to its default", Theme.HINT);
            } catch (java.io.IOException e) {
                game.toast("Could not reset it: " + e.getMessage(), Theme.WARNING);
            }
        }
        y += 36;
        Tailor.Theme th = Tailor.theme(d);
        String about = Tailor.edited(d) ? "Set in the Tailor, kept in assets/tailor/" + d.id() + ".json."
                : th == null ? "Its default: nothing."
                : "Its default: nothing as it is found, then " + Gear.shortly(th.effect(), th.each())
                        + " a rarity up the tiers.";
        y += ui.paragraph(ui.small, about + " Each tier says all it gives; worn, everything adds up.", x, y, w,
                Theme.ITEM_DISABLED) + 6;
    }

    /** Keep {@code d}'s tiers as {@code rungs}, or say why not. */
    private void save(ItemDef d, List<Tailor.Rung> rungs) {
        try {
            Tailor.save(d, rungs);
        } catch (java.io.IOException e) {
            game.toast("Could not save it: " + e.getMessage(), Theme.WARNING);
        }
    }

    // --- the right panel: the piece as it will be ----------------------------------------

    /** The piece at its chosen tier, turning, and what every tier of it gives. */
    void preview(Ui ui, float x, float y, float w, double time) {
        ItemDef d = def();
        List<Tier> tiers = Tailor.tiers(d);
        tier = Math.max(0, Math.min(tiers.size() - 1, tier));
        Item it = Item.of(d, tiers.get(tier));
        ui.text(ui.body, "As it will be", x, y, Theme.ACCENT);
        y += 30;
        y = icon(ui, game.items(), it, x, y, w, time);
        ui.text(ui.body, Ui.fit(ui.body, it.name(), w), x, y, it.rarity().colour(1f));
        y += 28;
        ui.text(ui.small, Ui.fit(ui.small, it.rarity().label() + " - " + where(d) + (Armour.sides(d) > 0
                ? " - rolls " + rules().dice(it.rarity()) + "d" + Armour.sides(d) + " to block" : ""),
                w), x, y, Theme.ITEM);
        y += 26;
        y = ladder(ui, tiers.size(), n -> {
            Item a = Item.of(d, tiers.get(n));
            return new Rungs(a.name(), a.rarity().colour(1f), Tailor.gear(d, tiers.get(n)));
        }, tier, x, y, w);
    }

    /** One tier's row in the ladder: its name, its colour, what it gives. */
    record Rungs(String name, float[] colour, Map<Gear.Effect, Double> gives) {}

    /**
     * Every tier of a piece, a row each - its number and name in its
     * rarity's colour, then what it gives, each effect in its colour - the
     * chosen one ({@code now}) marked. Returns the foot.
     */
    static float ladder(Ui ui, int n, java.util.function.IntFunction<Rungs> rung, int now, float x, float y, float w) {
        ui.text(ui.small, "Tier by tier", x, y, Theme.ACCENT);
        y += 22;
        for (int i = 0; i < n; i++) {
            Rungs r = rung.apply(i);
            List<String> lines = new ArrayList<>();
            List<float[]> colours = new ArrayList<>();
            for (var e : r.gives().entrySet()) {
                lines.add(Gear.shortly(e.getKey(), e.getValue()));
                colours.add(Theme.hex(e.getKey().rgb(), 1f));
            }
            if (lines.isEmpty()) {
                lines.add("nothing");
                colours.add(Theme.ITEM_DISABLED);
            }
            float h = 22 + 18 * lines.size() + 6;
            if (i == now) ui.rect(x - 4, y - 2, w + 8, h - 2, Theme.withAlpha(Theme.ACCENT, 0.14f));
            ui.text(ui.small, Ui.fit(ui.small, (i + 1) + ". " + r.name(), w), x, y, r.colour());
            float ly = y + 20;
            for (int l = 0; l < lines.size(); l++) {
                ui.text(ui.small, Ui.fit(ui.small, lines.get(l), w - 14), x + 14, ly, colours.get(l));
                ly += 18;
            }
            y += h;
        }
        return y;
    }

    /** {@code it} turning as it lies about (a shield or a shirt on a hanger, in its rarity's colour). Returns the foot. */
    static float icon(Ui ui, ItemSprites items, Item it, float x, float y, float w, double time) {
        Wearables.Pickup pickup = Wearables.pickup(it);
        float s = w * 0.62f;
        ItemSprites.Spin spin = pickup == null ? null : items.spin(pickup.spin());
        ItemSprites.Icon icon = spin == null ? null : items.icon(pickup.spin(), pickup.part(), pickup.rgb());
        if (icon != null) {
            int frame = (int) Math.floor(time / 6.0 * icon.frames());
            float[] uv = icon.uv(frame);
            float ix = x + (w - s) / 2;
            Batch b = ui.batch();
            float[] rc = it.rarity().colour(1f);
            b.glowRect(icon.halo(), ix, y, s, s, uv[0], uv[1], uv[2], uv[3], rc[0], rc[1], rc[2], 0.55f);
            b.rect(icon.sprite(), ix, y, s, s, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
        }
        return y + s + 6;
    }

    // --- the effects' rows, the Forge's Tailor's too ------------------------------------

    /** How tall {@link #gearRows} is. */
    static float gearRowsHeight() {
        return 31 * Gear.Effect.values().length;
    }

    /**
     * Each of the gear's special effects on or off, and how much of it (its
     * - / +): a row each. Returns {@code gear} as it is after this frame's
     * clicks.
     */
    static Map<Gear.Effect, Double> gearRows(Ui ui, Map<Gear.Effect, Double> gear, float x, float y, float w) {
        EnumMap<Gear.Effect, Double> out = new EnumMap<>(Gear.Effect.class);
        out.putAll(gear);
        float bw = (float) Math.floor(w * 0.52f);
        for (Gear.Effect e : Gear.Effect.values()) {
            Double amount = out.get(e);
            boolean on = amount != null;
            // (the small font on every one, so a short name is not drawn bigger than a long one)
            if (ui.button("", x, y, bw, 28, on, true)) {
                if (on) out.remove(e);
                else out.put(e, e.fallback());
            }
            ui.textCentered(ui.small, fit(ui, e.label(), bw - 8), x + bw / 2, y + 7, on ? Theme.TITLE : Theme.ITEM);
            ui.rect(x + 4, y + 24, bw - 8, 3, Theme.hex(e.rgb(), on ? 1f : 0.3f));
            float sx = x + bw + 6, sw = w - bw - 6;
            if (on && !e.onOff()) {
                double v = amount;
                if (ui.button("-", sx, y, 26, 28)) v = e.clamp(v - e.step());
                if (ui.button("+", sx + sw - 26, y, 26, 28)) v = e.clamp(v + e.step());
                ui.textCentered(ui.small, fit(ui, e.amount(v), sw - 56), sx + sw / 2, y + 7, Theme.TITLE);
                if (v != amount) out.put(e, v);
            } else {
                ui.text(ui.small, fit(ui, on ? "On" : "Off", sw), sx + 4, y + 7, on ? Theme.TITLE : Theme.ITEM_DISABLED);
            }
            y += 31;
        }
        return out;
    }

    /** {@code s} cut to {@code width} in the small font, with an ellipsis. */
    static String fit(Ui ui, String s, float width) {
        if (ui.small.width(s) <= width) return s;
        String out = s;
        while (out.length() > 1 && ui.small.width(out + "…") > width) out = out.substring(0, out.length() - 1);
        return out + "…";
    }
}
