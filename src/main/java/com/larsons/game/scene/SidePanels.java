package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.cards.Card;
import com.larsons.game.cards.CardBook;
import com.larsons.game.cards.Hero;
import com.larsons.game.core.Game;
import com.larsons.game.ui.CardFace;
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
 * slots: a weapon's <b>upgrade tiers</b> (right-click a weapon), and her
 * <b>cards</b> (the Cards button, or J) - her hand to play from, her perk
 * tree, and her deck's piles. The game stands still while they are open.
 */
final class SidePanels {

    /** Which of her cards' pages is showing. */
    enum CardsPage { HAND, PERKS }

    private final Game game;
    private final World world;
    CardsPage page = CardsPage.HAND;

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
        float by = y + h - 108;
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

    // --- her cards --------------------------------------------------------------------

    /** Her cards: the hand (click one to play it) or her perk tree. Returns false when it is to close. */
    boolean cards(Ui ui, float x, float y, float w, float h) {
        Hero hero = world.player().hero();
        CardBook book = CardBook.get();
        CardBook.Hero cls = book.hero(hero.heroClass());
        frame(ui, x, y, w, h, cls == null ? "Cards" : cls.name() + "'s cards");
        float tw = (w - 40) / 2;
        if (ui.button("Hand", x + 16, y + 50, tw, 28, page == CardsPage.HAND, true)) page = CardsPage.HAND;
        String perks = "Perks" + (hero.points() > 0 ? " (" + hero.points() + ")" : "");
        if (ui.button(perks, x + 24 + tw, y + 50, tw, 28, page == CardsPage.PERKS, true)) page = CardsPage.PERKS;
        if (cls == null) {
            ui.paragraph(ui.body, "Choose a class first: Pause → Class.", x + 20, y + 100, w - 40, Theme.HINT);
        } else if (page == CardsPage.HAND) {
            hand(ui, hero, x, y + 90, w, h - 90);
        } else {
            perkTree(ui, hero, cls, x, y + 90, w, h - 90);
        }
        return !ui.button("Close", x + w / 2 - 60, y + h - 40, 120, 28);
    }

    private void hand(Ui ui, Hero hero, float x, float y, float w, float h) {
        CardBook book = CardBook.get();
        List<String> hand = hero.deck().hand();
        int cols = 4;
        float gap = 8, cw = (w - 28 - (cols - 1) * gap) / cols, ch = cw * CardFace.TALL;
        float cx0 = x + 14;
        int played = -1;
        for (int i = 0; i < hand.size(); i++) {
            Card c = book.card(hand.get(i));
            if (c == null) continue;
            float cx = cx0 + (i % cols) * (cw + gap), cy = y + (i / cols) * (ch + gap);
            boolean hot = ui.hover(cx, cy, cw, ch);
            CardFace.draw(ui, c, cx, cy, cw, hot, false);
            if (ui.clicked(cx, cy, cw, ch)) played = i;
        }
        if (hand.isEmpty()) {
            ui.paragraph(ui.body, "No cards in hand. One is drawn every " + minutes(book.drawEvery())
                    + " while the deck has any; a reshuffle scroll deals a fresh hand.", x + 20, y + 10, w - 40,
                    Theme.HINT);
        }
        float iy = y + 2 * (ch + gap) + 4;
        String piles = "Hand " + hand.size() + " / " + hero.deck().most() + " · deck " + hero.deck().drawPile().size()
                + " · discarded " + hero.deck().discardPile().size();
        ui.textCentered(ui.small, piles, x + w / 2, iy, Theme.ITEM);
        String next = hand.size() >= hero.deck().most() ? "Your hand is full"
                : hero.deck().drawPile().isEmpty() ? "The deck is empty: reshuffle to draw again"
                : "Next card in " + clock(hero.deck().untilDraw());
        ui.textCentered(ui.small, next, x + w / 2, iy + 20, Theme.HINT);
        ui.textCentered(ui.small, "Level " + hero.level() + " · " + hero.xp() + " / " + hero.toNext() + " experience",
                x + w / 2, iy + 40, Theme.ACCENT);
        Inventory inv = world.player().inventory();
        int scrolls = inv.total(Item.of(Supplies.RESHUFFLE_SCROLL));
        if (ui.button("Reshuffle (" + scrolls + (scrolls == 1 ? " scroll)" : " scrolls)"), x + 30, iy + 64, w - 60, 32,
                false, scrolls > 0 && !hero.deck().owned().isEmpty())) {
            inv.takeOne(inv.indexOf(Supplies.RESHUFFLE_SCROLL));
            hero.deck().reshuffle();
            Sounds.play(SoundKeys.ui("card_shuffle"));
            game.toast("Reshuffled: a fresh hand of " + hero.deck().hand().size(), Theme.HINT);
        }
        if (played >= 0) {
            Hero.Played p = hero.play(played, world);
            if (p != null) {
                Sounds.play(SoundKeys.ui("card_play"));
                String said = p.said().isEmpty() ? p.card().text() : String.join(", ", p.said());
                game.toast(p.card().name() + ": " + said, p.card().rarity().colour(1f));
            }
        }
    }

    private void perkTree(Ui ui, Hero hero, CardBook.Hero cls, float x, float y, float w, float h) {
        CardBook book = CardBook.get();
        List<CardBook.Perk> tree = book.perks(cls.id());
        ui.textCentered(ui.small, hero.points() + (hero.points() == 1 ? " perk point" : " perk points")
                + " · a level gives one · a row needs one of the row above", x + w / 2, y, Theme.ACCENT);
        int rows = 0;
        for (CardBook.Perk p : tree) rows = Math.max(rows, p.row() + 1);
        float top = y + 24, rowH = Math.min(120, (h - 110) / Math.max(1, rows));
        CardBook.Perk hovered = null;
        for (int r = 0; r < rows; r++) {
            final int rr = r;
            List<CardBook.Perk> row = tree.stream().filter(p -> p.row() == rr).toList();
            float nw = (w - 28 - (row.size() - 1) * 8) / Math.max(1, row.size());
            for (int i = 0; i < row.size(); i++) {
                CardBook.Perk p = row.get(i);
                float nx = x + 14 + i * (nw + 8), ny = top + r * rowH;
                boolean have = hero.perks().contains(p.id()), can = hero.canTake(p.id());
                boolean hot = ui.hover(nx, ny, nw, rowH - 8);
                if (hot) hovered = p;
                ui.rect(nx, ny, nw, rowH - 8, have ? Theme.withAlpha(Theme.OK, 0.22f)
                        : Theme.withAlpha(Theme.BUTTON, can ? 1f : 0.6f));
                ui.outline(nx, ny, nw, rowH - 8, have || hot ? 2 : 1, have ? Theme.OK : can ? Theme.ACCENT
                        : Theme.PANEL_EDGE);
                ui.paragraph(ui.small, p.name(), nx + 6, ny + 5, nw - 12, have ? Theme.OK : can ? Theme.TITLE
                        : Theme.ITEM_DISABLED);
                String what = p.card() != null ? "Card" : "For good";
                ui.text(ui.small, what + " · " + p.cost() + (p.cost() == 1 ? " pt" : " pts"), nx + 6,
                        ny + rowH - 8 - ui.small.lineHeight() - 4, have ? Theme.OK : Theme.ITEM);
                if (can && ui.clicked(nx, ny, nw, rowH - 8) && hero.take(p.id(), world.player())) {
                    Sounds.play(SoundKeys.ui("perk"));
                    game.toast(p.name() + (p.card() != null ? ": its card is on your discard pile" : ": yours for good"),
                            Theme.OK);
                }
            }
        }
        if (hovered != null) {
            Card c = hovered.card() == null ? null : book.card(hovered.card());
            String text = c != null ? c.name() + ": " + c.text() : hovered.text();
            ui.paragraph(ui.small, text, x + 20, top + rows * rowH + 4, w - 40, Theme.HINT);
        }
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
