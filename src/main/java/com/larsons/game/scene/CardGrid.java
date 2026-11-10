package com.larsons.game.scene;

import com.larsons.game.cards.Card;
import com.larsons.game.ui.CardFace;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * Cards laid out in rows that scroll - a pile looked through on the card
 * table, the compendium's pages: each drawn small, the one under the pointer
 * drawn again large beside it ({@link #preview}), so its words can be read.
 */
final class CardGrid {

    private float scroll;
    private Card hot;
    private float hotX, hotY, hotW;

    /** Back to its top (another pile, another page). */
    void rewind() {
        scroll = 0;
    }

    /**
     * {@code cards} in rows within {@code (x, y, w, h)}, each {@code cardW}
     * wide; {@code note}: a line under card i (null: none); {@code dim}:
     * whether card i is drawn faded. Returns the one under the pointer (-1:
     * none); a click on it is the caller's to ask ({@link Ui#clicked}).
     */
    int draw(Ui ui, List<Card> cards, IntFunction<String> note, IntPredicate dim, float x, float y, float w, float h,
             float cardW) {
        float gap = 12, noteH = note == null ? 0 : ui.tiny.lineHeight() + 4;
        int cols = Math.max(1, (int) ((w - 14 + gap) / (cardW + gap)));
        float cellH = cardW * CardFace.TALL + noteH + gap;
        int rows = (cards.size() + cols - 1) / cols;
        float total = rows * cellH + 4, most = Math.max(0, total - h);
        if (ui.hover(x, y, w, h)) scroll -= (float) ui.input().scroll() * 70;
        scroll = Math.max(0, Math.min(most, scroll));
        float x0 = x + (w - 14 - (cols * cardW + (cols - 1) * gap)) / 2;
        hot = null;
        int over = -1;
        ui.clip(x, y, w, h);
        for (int i = 0; i < cards.size(); i++) {
            float cx = x0 + (i % cols) * (cardW + gap), cy = y + 4 + (i / cols) * cellH - scroll;
            if (cy + cellH < y || cy > y + h) continue;
            Card c = cards.get(i);
            boolean hover = ui.hover(cx, cy, cardW, cardW * CardFace.TALL);
            boolean faded = dim != null && dim.test(i);
            CardFace.draw(ui, c, cx + cardW / 2, cy + cardW * CardFace.TALL / 2, cardW, 0, hover, faded ? 0.42f : 1f);
            if (note != null) {
                String s = note.apply(i);
                if (s != null) {
                    ui.textCentered(ui.tiny, s, cx + cardW / 2, cy + cardW * CardFace.TALL + 3,
                            faded ? Theme.ITEM_DISABLED : Theme.HINT);
                }
            }
            if (hover) {
                over = i;
                hot = c;
                hotX = cx;
                hotY = cy;
                hotW = cardW;
            }
        }
        ui.unclip();
        if (most > 0) {
            // how far down it is scrolled: a brass slide in a groove
            float bx = x + w - 8, bh = Math.max(30, h * h / total), by = y + (h - bh) * scroll / most;
            ui.rect(bx, y, 6, h, Theme.withAlpha(Theme.SOOT, 0.7f));
            ui.rect(bx + 1, by, 4, bh, Theme.BRASS);
            ui.rect(bx + 1, by, 4, 1, Theme.BRASS_LIGHT);
        }
        if (cards.isEmpty()) ui.textCentered(ui.body, "No cards here.", x + w / 2, y + h / 2 - 12, Theme.ITEM_DISABLED);
        return over;
    }

    /** The card under the pointer, drawn large beside where it lies (call it last, over everything). */
    void preview(Ui ui) {
        if (hot == null) return;
        float w = Math.min(CardFace.BASE * 1.15f, ui.height() * 0.55f / CardFace.TALL * 1.3f), h = w * CardFace.TALL;
        float x = hotX + hotW + 14;
        if (x + w > ui.width() - 8) x = hotX - w - 14;
        float y = Math.max(8, Math.min(ui.height() - h - 8, hotY + hotW * CardFace.TALL / 2 - h / 2));
        CardFace.draw(ui, hot, x + w / 2, y + h / 2, w, 0, true, 1f);
    }
}
