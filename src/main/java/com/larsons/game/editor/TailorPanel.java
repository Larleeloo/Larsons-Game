package com.larsons.game.editor;

import com.larsons.game.gfx.Batch;
import com.larsons.game.sprite.ItemSprites;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Gear;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Wearables;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Tailor's rows, shared by its pages (Creative Mode's Tailor makes the
 * clothes and the armour of your own: {@link Forged}): a piece's tiers as a
 * ladder, each with what it gives worn, and the gear's special effects
 * ({@link Gear.Effect}) each with its - / +.
 */
final class TailorPanel {

    private TailorPanel() {}

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

    /** How tall {@link #gearRows} is ({@code pack}: with the backpack's carry space). */
    static float gearRowsHeight(boolean pack) {
        return 31 * effects(pack).size();
    }

    /** The effects a piece can be made with: all but the carry space, which only a pack has. */
    static List<Gear.Effect> effects(boolean pack) {
        List<Gear.Effect> out = new ArrayList<>();
        for (Gear.Effect e : Gear.Effect.values()) if (pack || e != Gear.Effect.CARRY) out.add(e);
        if (pack) {
            // a pack's room first
            out.remove(Gear.Effect.CARRY);
            out.add(0, Gear.Effect.CARRY);
        }
        return out;
    }

    /**
     * Each of the gear's special effects on or off, and how much of it (its
     * - / +): a row each. Returns {@code gear} as it is after this frame's
     * clicks.
     */
    static Map<Gear.Effect, Double> gearRows(Ui ui, Map<Gear.Effect, Double> gear, boolean pack, float x, float y,
                                             float w) {
        EnumMap<Gear.Effect, Double> out = new EnumMap<>(Gear.Effect.class);
        out.putAll(gear);
        if (!pack) out.remove(Gear.Effect.CARRY);
        float bw = (float) Math.floor(w * 0.52f);
        for (Gear.Effect e : effects(pack)) {
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
