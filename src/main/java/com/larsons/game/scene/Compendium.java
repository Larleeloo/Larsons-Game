package com.larsons.game.scene;

import com.larsons.game.cards.Card;
import com.larsons.game.cards.CardBook;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Rarity;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The compendium (Pause, Compendium): every card there is, to look through -
 * by class along the top (all of them, each of the nine, and the cards of no
 * class), by rarity under that (Common to Mythic). Each is drawn as it is on
 * the card table, with where it is had under it - a class's starting deck,
 * its perk tree, a shop and for how much - and how many of it she owns now;
 * the one under the pointer is shown large.
 */
final class Compendium {

    private static final String ALL = "*", NONE = "";

    private final World world;
    private final CardGrid grid = new CardGrid();
    private String hero;
    private Rarity rarity;

    Compendium(World world) {
        this.world = world;
    }

    /** Opened afresh: on her own class's cards. */
    void open() {
        String own = world.player().hero().heroClass();
        hero = own == null ? ALL : own;
        rarity = null;
        grid.rewind();
    }

    void draw(Ui ui, float x, float y, float w, float h) {
        CardBook book = CardBook.get();
        if (hero == null) open();
        // whose: all, each class, no class
        List<String> ids = new ArrayList<>(), names = new ArrayList<>();
        ids.add(ALL);
        names.add("All");
        for (CardBook.Hero c : book.heroes()) {
            ids.add(c.id());
            names.add(c.name());
        }
        ids.add(NONE);
        names.add("Any class");
        float gap = 4, bw = (w - (ids.size() - 1) * gap) / ids.size();
        for (int i = 0; i < ids.size(); i++) {
            if (ui.button(Ui.fit(ui.body, names.get(i), bw - 8), x + i * (bw + gap), y, bw, 32,
                    ids.get(i).equals(hero), true)) {
                hero = ids.get(i);
                grid.rewind();
            }
        }
        // how rare: all, or one
        float ry = y + 40, rw = 108;
        if (ui.button("Every rarity", x, ry, rw + 22, 28, rarity == null, true)) {
            rarity = null;
            grid.rewind();
        }
        float rx = x + rw + 22 + gap;
        for (Rarity r : Rarity.values()) {
            if (ui.button(r.label(), rx, ry, rw, 28, rarity == r, true)) {
                rarity = r;
                grid.rewind();
            }
            ui.rect(rx + 6, ry + 23, rw - 12, 2, r.colour(1f));
            rx += rw + gap;
        }

        Map<String, Integer> owned = new HashMap<>();
        for (String id : world.player().hero().deck().owned()) owned.merge(id, 1, Integer::sum);
        List<Card> cards = new ArrayList<>();
        int have = 0;
        for (Card c : book.cards()) {
            if (!hero.equals(ALL) && !hero.equals(c.hero() == null ? NONE : c.hero())) continue;
            if (rarity != null && c.rarity() != rarity) continue;
            cards.add(c);
            if (owned.containsKey(c.id())) have++;
        }
        cards.sort(java.util.Comparator.comparing((Card c) -> c.rarity().ordinal()).thenComparing(Card::name));
        String count = cards.size() + (cards.size() == 1 ? " card" : " cards") + " · " + have + " in your deck · "
                + book.cards().size() + " in all";
        ui.text(ui.small, count, x + w - ui.small.width(count), ry + 6, Theme.HINT);

        float top = ry + 40, cardW = Math.max(124, Math.min(158, (w - 14) / 6 - 12));
        grid.draw(ui, cards, i -> {
            Card c = cards.get(i);
            int n = owned.getOrDefault(c.id(), 0);
            String where = switch (c.source()) {
                case START -> "Starting deck";
                case PERK -> "Perk tree";
                case SHOP -> "Shops · " + c.price() + " coins";
            };
            return n > 0 ? where + " · yours ×" + n : where;
        }, null, x, top, w, y + h - top, cardW);
        grid.preview(ui);
    }
}
