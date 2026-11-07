package com.larsons.game.editor;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.cards.Card;
import com.larsons.game.cards.CardBook;
import com.larsons.game.core.Game;
import com.larsons.game.ui.CardFace;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Buffs;
import com.larsons.game.world.Rarity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Creative Mode's Cards tool: every card of every class - its name, what it
 * says, its rarity, where it is had (a starting deck, the perk tree, a
 * shop), its price, and what it does, effect by effect (a help for so long
 * or so many hits, on which blows; or something at once) - a class's
 * starting deck (how many copies of each), and its perk tree (which card or
 * help each node gives, and its price in points). New cards, and cards
 * thrown away. Saved to {@code assets/cards/cards.json}.
 */
final class CardsTool {

    private final Game game;
    private final Widgets widgets;
    private int heroIndex, cardIndex, perkIndex;
    private boolean dirty;

    CardsTool(Game game, Widgets widgets) {
        this.game = game;
        this.widgets = widgets;
    }

    /** The classes, then "Any class" (the neutral cards). */
    private static List<String> heroIds(CardBook book) {
        List<String> out = new ArrayList<>();
        for (CardBook.Hero h : book.heroes()) out.add(h.id());
        out.add(null);
        return out;
    }

    private String hero(CardBook book) {
        List<String> ids = heroIds(book);
        heroIndex = Math.floorMod(heroIndex, ids.size());
        return ids.get(heroIndex);
    }

    /** The card being edited, or null (a class with none). */
    Card card() {
        CardBook book = CardBook.get();
        List<Card> cards = book.cardsOf(hero(book));
        if (cards.isEmpty()) return null;
        cardIndex = Math.floorMod(cardIndex, cards.size());
        return cards.get(cardIndex);
    }

    /** The left panel: which card, and all of it. */
    void panel(Ui ui, float x, float y, float w) {
        CardBook book = CardBook.get();
        ui.text(ui.body, "Cards", x, y, Theme.ACCENT);
        y += 30;
        List<String> ids = heroIds(book);
        List<String> names = new ArrayList<>();
        for (String id : ids) names.add(id == null ? "Any class" : book.hero(id).name());
        int hi = widgets.choice(ui, "Class", names, heroIndex, x, y, w, 0.3f);
        if (hi != heroIndex) {
            heroIndex = hi;
            cardIndex = 0;
            perkIndex = 0;
        }
        y += 34;
        String hero = hero(book);
        List<Card> cards = book.cardsOf(hero);
        List<String> cardNames = new ArrayList<>();
        for (Card c : cards) cardNames.add(c.name());
        if (!cards.isEmpty()) cardIndex = widgets.choice(ui, "Card", cardNames, cardIndex, x, y, w, 0.3f);
        y += 34;
        float bw = (w - 8) / 3;
        if (ui.button("New card", x, y, bw, 26)) {
            Card c = new Card(book.freeId("new card"), "New card", hero, Rarity.COMMON, "What it does.",
                    List.of(new Card.Effect("heal", 20, 0, 0, Buffs.Only.ANY)), Card.price(Rarity.COMMON),
                    Card.Source.SHOP);
            book.put(c);
            cardIndex = book.cardsOf(hero).indexOf(c);
            dirty = true;
        }
        Card card = card();
        if (ui.button("Delete", x + bw + 4, y, bw, 26, false, card != null)) {
            book.remove(card.id());
            cardIndex = Math.max(0, cardIndex - 1);
            dirty = true;
            card = card();
        }
        if (ui.button(dirty ? "Save *" : "Save", x + 2 * (bw + 4), y, bw, 26, dirty, true)) save();
        y += 36;
        if (card == null) return;
        Card was = card;
        String name = widgets.text(ui, "card-name-" + card.id(), "Name", card.name(), x, y, w);
        if (!name.isBlank() && !name.strip().equals(card.name())) card = card.withName(name);
        y += 34;
        String text = widgets.text(ui, "card-text-" + card.id(), "Says", card.text(), x, y, w);
        if (!text.strip().equals(card.text())) card = card.withText(text);
        y += 34;
        List<String> rarities = new ArrayList<>();
        for (Rarity r : Rarity.values()) rarities.add(r.label());
        int ri = widgets.choice(ui, "Rarity", rarities, card.rarity().ordinal(), x, y, w, 0.3f);
        if (ri != card.rarity().ordinal()) card = card.withRarity(Rarity.values()[ri]);
        y += 32;
        List<String> sources = List.of("Starting deck", "Perk tree", "Shops");
        int si = widgets.choice(ui, "Had from", sources, card.source().ordinal(), x, y, w, 0.3f);
        if (si != card.source().ordinal()) card = card.withSource(Card.Source.values()[si]);
        y += 32;
        int price = (int) Math.round(widgets.stepper(ui, "Price", card.price(), 5, 0, 5000, "%.0f coins", x, y, w));
        if (price != card.price()) card = card.withPrice(price);
        y += 32;
        if (hero != null) {
            int copies = copies(book, hero, card.id());
            int nc = (int) Math.round(widgets.stepper(ui, "In her first deck", copies, 1, 0, 8, "%.0f", x, y, w));
            if (nc != copies) setCopies(book, hero, card.id(), nc);
            y += 32;
        }
        // what it does
        ui.text(ui.small, "Does:", x, y + 4, Theme.ITEM);
        y += 24;
        List<Card.Effect> fx = new ArrayList<>(card.effects());
        int remove = -1;
        List<String> kinds = Card.Effect.kinds();
        List<String> kindNames = new ArrayList<>();
        for (String k : kinds) kindNames.add(Card.Effect.label(k));
        for (int i = 0; i < fx.size(); i++) {
            Card.Effect e = fx.get(i);
            int ki = Math.max(0, kinds.indexOf(e.kind()));
            int nk = widgets.choice(ui, "Effect " + (i + 1), kindNames, ki, x, y, w - 32, 0.3f);
            if (nk != ki) e = e.withKind(kinds.get(nk));
            if (ui.button("x", x + w - 28, y, 28, Widgets.ROW)) remove = i;
            y += 30;
            Buffs.Kind b = e.buff();
            double step = b != null && b.times() ? 0.05 : b == Buffs.Kind.REGEN_HEALTH || b == Buffs.Kind.REGEN_MANA
                    || b == Buffs.Kind.REGEN_STAMINA || b == Buffs.Kind.LIFESTEAL || b == Buffs.Kind.THORNS
                    || b == Buffs.Kind.POISON ? 0.05 : 1;
            double v = widgets.stepper(ui, "  how much", e.value(), step, 0, 1000, step < 1 ? "%.2f" : "%.0f", x, y, w);
            if (Math.abs(v - e.value()) > 1e-9) e = e.withValue(Math.round(v / step) * step);
            y += 30;
            if (b != null) {
                double sec = widgets.stepper(ui, "  for (s)", e.seconds(), 15, 0, 3600, "%.0f", x, y, w);
                if (sec != e.seconds()) e = e.withSeconds(sec);
                y += 30;
                int ch = (int) Math.round(widgets.stepper(ui, "  hits", e.charges(), 1, 0, 50, "%.0f", x, y, w));
                if (ch != e.charges()) e = e.withCharges(ch);
                y += 30;
                List<String> onlies = List.of("Any blow", "Melee", "Ranged", "Spells");
                int oi = widgets.choice(ui, "  on", onlies, e.only().ordinal(), x, y, w, 0.3f);
                if (oi != e.only().ordinal()) e = e.withOnly(Buffs.Only.values()[oi]);
                y += 32;
            }
            fx.set(i, e);
        }
        if (remove >= 0) fx.remove(remove);
        if (fx.size() < 4 && ui.button("Add an effect", x, y, w, 26)) {
            fx.add(new Card.Effect("damage", 1.2, 60, 0, Buffs.Only.ANY));
        }
        if (!fx.equals(card.effects())) card = card.withEffects(fx);
        if (!card.equals(was)) {
            book.put(card);
            dirty = true;
        }
    }

    /** The right panel: the card as it looks, and its class's perk tree. */
    void preview(Ui ui, float x, float y, float w) {
        CardBook book = CardBook.get();
        Card card = card();
        ui.text(ui.body, "As it looks", x, y, Theme.ACCENT);
        y += 30;
        if (card != null) {
            float cw = Math.min(170, w * 0.6f);
            CardFace.draw(ui, card, x + (w - cw) / 2, y, cw, false, false);
            y += cw * CardFace.TALL + 14;
        }
        String hero = hero(book);
        if (hero == null) return;
        ui.text(ui.body, book.hero(hero).name() + "'s perk tree", x, y, Theme.ACCENT);
        y += 28;
        List<CardBook.Perk> tree = new ArrayList<>(book.perks(hero));
        if (tree.isEmpty()) return;
        List<String> names = new ArrayList<>();
        for (CardBook.Perk p : tree) names.add("Row " + (p.row() + 1) + ": " + p.name());
        perkIndex = widgets.choice(ui, "Node", names, perkIndex, x, y, w, 0.22f);
        y += 32;
        CardBook.Perk p = tree.get(Math.floorMod(perkIndex, tree.size()));
        CardBook.Perk was = p;
        int cost = (int) Math.round(widgets.stepper(ui, "Costs", p.cost(), 1, 1, 9, "%.0f points", x, y, w));
        if (cost != p.cost()) p = new CardBook.Perk(p.id(), p.name(), p.text(), p.row(), cost, p.card(), p.passive());
        y += 30;
        int row = (int) Math.round(widgets.stepper(ui, "Row", p.row() + 1, 1, 1, 8, "%.0f", x, y, w)) - 1;
        if (row != p.row()) p = new CardBook.Perk(p.id(), p.name(), p.text(), row, p.cost(), p.card(), p.passive());
        y += 30;
        if (p.card() != null) {
            List<Card> mine = book.cardsOf(hero);
            List<String> cn = new ArrayList<>();
            int ci = 0;
            for (int i = 0; i < mine.size(); i++) {
                cn.add(mine.get(i).name());
                if (mine.get(i).id().equals(p.card())) ci = i;
            }
            int nc = widgets.choice(ui, "Gives", cn, ci, x, y, w, 0.22f);
            if (nc != ci) {
                Card c = mine.get(nc);
                p = new CardBook.Perk(p.id(), c.name(), p.text(), p.row(), p.cost(), c.id(), p.passive());
            }
        } else {
            StringBuilder s = new StringBuilder("For good: ");
            for (Card.Effect e : p.passive()) s.append(Card.Effect.label(e.kind())).append(' ').append(e.value()).append("  ");
            ui.paragraph(ui.small, s.toString(), x, y + 4, w, Theme.HINT);
        }
        if (!p.equals(was)) {
            tree.set(Math.floorMod(perkIndex, tree.size()), p);
            book.putPerks(hero, tree);
            dirty = true;
        }
    }

    private static int copies(CardBook book, String hero, String card) {
        for (CardBook.Copies c : book.hero(hero).deck()) if (c.card().equals(card)) return c.copies();
        return 0;
    }

    private static void setCopies(CardBook book, String hero, String card, int n) {
        CardBook.Hero h = book.hero(hero);
        List<CardBook.Copies> deck = new ArrayList<>();
        boolean found = false;
        for (CardBook.Copies c : h.deck()) {
            if (c.card().equals(card)) {
                found = true;
                if (n > 0) deck.add(new CardBook.Copies(card, n));
            } else {
                deck.add(c);
            }
        }
        if (!found && n > 0) deck.add(new CardBook.Copies(card, n));
        book.putHero(h.withDeck(deck));
    }

    private void save() {
        try {
            CardBook.save(CardBook.get());
            dirty = false;
            Sounds.play(SoundKeys.ui("save"));
            game.toast("Cards saved (assets/cards/cards.json)", Theme.HINT);
        } catch (IOException e) {
            game.toast("Could not save the cards: " + e.getMessage(), Theme.WARNING);
        }
    }
}
