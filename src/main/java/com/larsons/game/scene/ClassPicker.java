package com.larsons.game.scene;

import com.larsons.game.cards.Card;
import com.larsons.game.cards.CardBook;
import com.larsons.game.ui.Pointers;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Choosing her class: the nine of them as tiles, three by three - each its
 * name in its colour, what it is about, and its starting deck ("4 Cast, 2
 * Bind, Transfigure, Healing Spell") - at the start of a game, and again from
 * Pause → Class (a new class starts her deck afresh).
 */
final class ClassPicker {

    private ClassPicker() {}

    /**
     * The tiles in {@code (x, y, w, h)}, {@code now} (her class, or null)
     * marked; returns the id of the one clicked, or null.
     */
    static String draw(Ui ui, Pointers pointers, String now, float x, float y, float w, float h) {
        CardBook book = CardBook.get();
        List<CardBook.Hero> heroes = book.heroes();
        int cols = 3, rows = (heroes.size() + cols - 1) / cols;
        float gap = 10, tw = (w - (cols - 1) * gap) / cols, th = (h - (rows - 1) * gap) / Math.max(1, rows);
        String picked = null;
        for (int i = 0; i < heroes.size(); i++) {
            CardBook.Hero c = heroes.get(i);
            float tx = x + (i % cols) * (tw + gap), ty = y + (i / cols) * (th + gap);
            boolean hot = ui.hover(tx, ty, tw, th), mine = c.id().equals(now);
            float[] rgb = rgba(c.rgb(), 1f);
            ui.rect(tx, ty, tw, th, Theme.withAlpha(hot ? Theme.BUTTON_HOVER : Theme.BUTTON, 0.96f));
            ui.rect(tx, ty, 5, th, rgb);
            ui.outline(tx, ty, tw, th, mine || hot ? 2 : 1, mine ? Theme.ACCENT : hot ? rgb : Theme.PANEL_EDGE);
            if (pointers != null) pointers.drawIcon(ui, c.pointer(), tx + tw - 44, ty + 8, 36);
            ui.text(ui.large, c.name(), tx + 16, ty + 8, rgb);
            float py = ty + 8 + ui.large.lineHeight() + 2;
            py += ui.paragraph(ui.small, c.about(), tx + 16, py, tw - 70, Theme.HINT) + 2;
            ui.paragraph(ui.small, "Deck: " + deck(book, c), tx + 16, py, tw - 26, Theme.ITEM);
            if (mine) ui.text(ui.small, "your class", tx + tw - 8 - ui.small.width("your class"), ty + th - 22,
                    Theme.ACCENT);
            if (ui.clicked(tx, ty, tw, th)) picked = c.id();
        }
        return picked;
    }

    /** "4 Cast, 2 Bind, Transfigure, Healing Spell". */
    static String deck(CardBook book, CardBook.Hero h) {
        List<String> out = new ArrayList<>();
        for (CardBook.Copies k : h.deck()) {
            Card c = book.card(k.card());
            if (c != null) out.add((k.copies() > 1 ? k.copies() + " " : "") + c.name());
        }
        return String.join(", ", out);
    }

    private static float[] rgba(int rgb, float a) {
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, a};
    }
}
