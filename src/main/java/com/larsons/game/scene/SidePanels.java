package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Game;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Inventory;
import com.larsons.game.world.Item;
import com.larsons.game.world.Supplies;
import com.larsons.game.world.Upgrades;
import com.larsons.game.world.World;

import java.util.List;
import java.util.Locale;

/**
 * What can stand beside the inventory instead of a barrel's or a chest's
 * slots: a weapon's <b>upgrade tiers</b> (right-click a weapon). The game
 * stands still while they are open. (Her cards have a table of their own:
 * {@link CardTable}.)
 */
final class SidePanels {

    private final Game game;
    private final World world;

    SidePanels(Game game, World world) {
        this.game = game;
        this.world = world;
    }

    private static void frame(Ui ui, float x, float y, float w, float h, String title) {
        ui.rect(x, y, w, h, Theme.withAlpha(Theme.PANEL, 0.96f));
        ui.outline(x, y, w, h, 1, Theme.PANEL_EDGE);
        ui.textCentered(ui.large, title, x + w / 2, y + 12, Theme.TITLE);
    }

    // --- a weapon's tiers -------------------------------------------------------------

    /**
     * Inventory slot {@code slot}'s weapon's tiers, one a row: each tier's
     * name and rarity, its dice, how long its ammunition lasts, what it costs
     * - the one it is at marked, a mystery's as ??? - and the Upgrade button.
     * Returns false when the panel is to close.
     */
    boolean tiers(Ui ui, Inventory inv, int slot, float x, float y, float w, float h) {
        Item item = inv.get(slot);
        if (!Upgrades.has(item)) return false;
        frame(ui, x, y, w, h, "Upgrades");
        ui.textCentered(ui.body, item.fullName(), x + w / 2, y + 52, item.rarity().colour(1f));
        var tuning = world.combat().tuning();
        List<Upgrades.Step> path = Upgrades.path(item, tuning::dice, tuning.sides);
        float rowH = Math.min(58, (h - 210) / Math.max(1, path.size()));
        float ry = y + 86;
        for (int i = 0; i < path.size(); i++) {
            Upgrades.Step s = path.get(i);
            float rx = x + 14, rw = w - 28;
            if (s.now()) ui.rect(rx, ry, rw, rowH - 4, Theme.withAlpha(Theme.ACCENT, 0.16f));
            ui.outline(rx, ry, rw, rowH - 4, s.now() ? 2 : 1, s.now() ? Theme.ACCENT : Theme.PANEL_EDGE);
            boolean hidden = s.mystery() && !s.reached();
            String name = hidden ? "???" : s.name();
            float[] c = hidden ? Theme.ITEM_DISABLED : s.rarity().colour(1f);
            ui.text(ui.body, (i + 1) + ".  " + name, rx + 8, ry + 4, c);
            String line = hidden ? "A mystery until it is reached"
                    : s.rarity().label() + " · rolls " + s.dice() + " · ammunition lasts " + s.ammoEvery();
            ui.text(ui.small, line, rx + 8, ry + 4 + ui.body.lineHeight() + 1,
                    hidden ? Theme.ITEM_DISABLED : Theme.withAlpha(Theme.HINT, s.reached() ? 1f : 0.75f));
            String right = s.now() ? "now" : s.reached() ? "" : s.cost() + (s.cost() == 1 ? " ingot" : " ingots");
            ui.text(ui.small, right, rx + rw - 8 - ui.small.width(right), ry + 6, s.now() ? Theme.ACCENT : Theme.ITEM);
            ry += rowH;
        }
        int cost = Upgrades.nextCost(item);
        int have = inv.total(Item.of(Supplies.UPGRADE_INGOT));
        float by = y + h - 116;
        if (cost < 0) {
            ui.textCentered(ui.body, "At its last tier", x + w / 2, by, Theme.ACCENT);
        } else {
            ui.textCentered(ui.small, "Upgrade ingots: " + have + " carried, " + cost + " needed", x + w / 2, by,
                    have >= cost ? Theme.HINT : Theme.WARNING);
            Item up = Upgrades.next(item);
            boolean secret = path.get(Math.min(path.size() - 1, path.indexOf(current(path)) + 1)).mystery();
            String label = "Upgrade to " + (secret ? "???" : up.name());
            if (ui.button(label, x + 24, by + 26, w - 48, 40, false, have >= cost)) {
                Upgrades.Result r = Upgrades.upgrade(inv, slot);
                if (r == Upgrades.Result.UPGRADED) {
                    Sounds.play(SoundKeys.ui("upgrade"));
                    game.toast("Upgraded to " + inv.get(slot).fullName(), inv.get(slot).rarity().colour(1f));
                    world.equipSelected();
                }
            }
        }
        return !ui.button("Close", x + w / 2 - 60, y + h - 44, 120, 30);
    }

    private static Upgrades.Step current(List<Upgrades.Step> path) {
        for (Upgrades.Step s : path) if (s.now()) return s;
        return path.get(0);
    }

    static String clock(double seconds) {
        int t = (int) Math.ceil(Math.max(0, seconds));
        return t / 60 + ":" + String.format(Locale.ROOT, "%02d", t % 60);
    }

    static String minutes(double seconds) {
        int m = (int) Math.round(seconds / 60);
        return m == 1 ? "minute" : m + " minutes";
    }
}
