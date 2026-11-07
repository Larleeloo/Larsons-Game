package com.larsons.game.ui;

import com.larsons.game.cards.Card;
import com.larsons.game.cards.CardBook;
import com.larsons.game.gfx.Font;
import com.larsons.game.world.Rarity;

/**
 * A card drawn on the screen: a tall panel in its class's colour (a neutral
 * card's grey), framed in its rarity's, its name across the top, what it
 * does below, its class and rarity along the bottom - in the hand, in a
 * shop, in Creative Mode's Cards tool, in the class picker.
 */
public final class CardFace {

    /** A card's height to its width. */
    public static final float TALL = 1.42f;

    private CardFace() {}

    /** {@code card} in the box {@code (x, y, w, w * TALL)}; {@code hot}: the pointer is on it; {@code dim}: not to be played. */
    public static void draw(Ui ui, Card card, float x, float y, float w, boolean hot, boolean dim) {
        float h = w * TALL;
        CardBook.Hero hero = CardBook.get().hero(card.hero());
        int rgb = hero == null ? 0x9098A8 : hero.rgb();
        float a = dim ? 0.45f : 1f;
        ui.rect(x, y, w, h, Theme.withAlpha(Theme.rgb(16, 16, 26, 1f), 0.96f * a + 0.04f));
        ui.rect(x + 3, y + 3, w - 6, h * 0.22f, rgba(rgb, 0.35f * a));
        ui.rect(x + 3, y + h - 22, w - 6, 19, rgba(rgb, 0.22f * a));
        float[] edge = card.rarity().colour(hot ? 1f : 0.85f * a);
        ui.outline(x, y, w, h, hot ? 3 : 2, edge);
        Font nameFont = ui.body.width(card.name()) <= w - 12 ? ui.body : ui.small;
        float nameH = ui.paragraph(nameFont, card.name(), x + 7, y + 6, w - 14,
                Theme.withAlpha(card.rarity() == Rarity.COMMON ? Theme.TITLE : card.rarity().colour(1f), a));
        // what it does, when it fits (a small card - in the hand - shows its name; its words are shown below it)
        float top = Math.max(y + h * 0.26f, y + 8 + nameH), room = y + h - 24 - top;
        if (fits(ui, card, w)) ui.paragraph(ui.small, card.text(), x + 7, top, w - 14, Theme.withAlpha(Theme.HINT, a));
        else if (room > 0) ui.rect(x + w / 2 - 12, top + room / 2 - 1, 24, 2, rgba(rgb, 0.6f * a));
        String foot = (hero == null ? "Any class" : hero.name()) + " · " + card.rarity().label();
        if (ui.small.width(foot) > w - 14) foot = card.rarity().label();
        if (ui.small.width(foot) <= w - 14) ui.text(ui.small, foot, x + 7, y + h - 20, Theme.withAlpha(Theme.ITEM, a));
    }

    /** Whether {@code card}'s words fit on a card {@code w} wide. */
    public static boolean fits(Ui ui, Card card, float w) {
        float h = w * TALL;
        Font nameFont = ui.body.width(card.name()) <= w - 12 ? ui.body : ui.small;
        float nameH = ui.paragraphHeight(nameFont, card.name(), w - 14);
        float top = Math.max(h * 0.26f, 8 + nameH);
        return ui.paragraphHeight(ui.small, card.text(), w - 14) <= h - 24 - top;
    }

    private static float[] rgba(int rgb, float a) {
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, a};
    }
}
