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
        ui.paragraph(nameFont, card.name(), x + 7, y + 6, w - 14, Theme.withAlpha(card.rarity() == Rarity.COMMON
                ? Theme.TITLE : card.rarity().colour(1f), a));
        ui.paragraph(ui.small, card.text(), x + 7, y + h * 0.26f, w - 14, Theme.withAlpha(Theme.HINT, a));
        String foot = (hero == null ? "Any class" : hero.name()) + " · " + card.rarity().label();
        ui.text(ui.small, foot, x + 7, y + h - 20, Theme.withAlpha(Theme.ITEM, a));
    }

    private static float[] rgba(int rgb, float a) {
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, a};
    }
}
