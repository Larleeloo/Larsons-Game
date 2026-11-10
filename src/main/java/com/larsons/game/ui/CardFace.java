package com.larsons.game.ui;

import com.larsons.game.cards.Card;
import com.larsons.game.cards.CardBook;
import com.larsons.game.gfx.Font;
import com.larsons.game.world.Rarity;

/**
 * A card drawn on the screen, the way it is everywhere - in the hand on the
 * card table, on its piles, in a shop, in the compendium, in the class
 * picker:
 *
 * <pre>
 *   (cog)--- its name on a ribbon ---      the cog: how long it lasts (5m, 30s),
 *   |  its picture, in a brass frame  |    how many hits (x3), or "now"
 *   |      [ Mage · Rare ]            |
 *   |  what it does, on parchment     |    the frame, the ribbon and the gem at
 *   +--------------<>-----------------+    its foot: its rarity's colour
 * </pre>
 *
 * Its body is oak stained its class's colour. It is laid out once, {@link
 * #BASE} points wide, and drawn at whatever size and angle it is asked for
 * ({@link Ui#turn}), so its lettering is the same on a small card as on a
 * large one.
 */
public final class CardFace {

    /** A card's height to its width. */
    public static final float TALL = 1.42f;
    /** The width a card is laid out at. */
    public static final float BASE = 240;

    private static final float[] WHITE = {1, 1, 1, 1};

    private CardFace() {}

    /** {@code card} in the box {@code (x, y, w, w * TALL)}; {@code hot}: the pointer is on it; {@code dim}: not to be played. */
    public static void draw(Ui ui, Card card, float x, float y, float w, boolean hot, boolean dim) {
        draw(ui, card, x + w / 2, y + w * TALL / 2, w, 0, hot, dim ? 0.5f : 1f);
    }

    /**
     * {@code card} centred on {@code (cx, cy)}, {@code w} wide, turned
     * {@code angle} radians; {@code glow}: lit round its edge in its rarity's
     * colour (the pointer is on it, it is about to be played); {@code alpha}:
     * how solid.
     */
    public static void draw(Ui ui, Card card, float cx, float cy, float w, double angle, boolean glow, float alpha) {
        float s = w / BASE, W = BASE, H = BASE * TALL;
        float x = cx - W / 2, y = cy - H / 2;
        ui.turn(cx, cy, angle, s);
        CardBook.Hero hero = CardBook.get().hero(card.hero());
        int rgb = hero == null ? 0x8A8478 : hero.rgb();
        Rarity rarity = card.rarity();
        float[] edge = frameColour(rarity, alpha);

        if (glow) {
            for (int i = 4; i >= 1; i--) {
                float g = i * 3;
                ui.rect(x - g, y - g, W + 2 * g, H + 2 * g, rarity.colour(0.10f * alpha));
            }
        }
        // the body: a dark edge, the rarity's frame, oak stained the class's colour
        ui.rect(x, y, W, H, Theme.withAlpha(Theme.SOOT, alpha));
        ui.rect(x + 2, y + 2, W - 4, H - 4, edge);
        ui.rect(x + 2, y + 2, W - 4, 2, lighter(edge, 0.35f));
        ui.rect(x + 2, y + H - 4, W - 4, 2, darker(edge, 0.35f));
        ui.rect(x + 7, y + 7, W - 14, H - 14, Theme.withAlpha(Theme.SOOT, alpha));
        ui.wood(x + 8, y + 8, W - 16, H - 16, new float[]{0.9f, 0.86f, 0.82f, alpha});
        ui.rect(x + 8, y + 8, W - 16, H - 16, stain(rgb, alpha));
        ui.rect(x + 8, y + 8, W - 16, 60, Theme.withAlpha(Theme.SOOT, 0.25f * alpha));
        ui.rect(x + 8, y + H - 70, W - 16, 62, Theme.withAlpha(Theme.SOOT, 0.3f * alpha));

        // its picture, in a brass frame
        float ax = x + 18, ay = y + 50, aw = W - 36, ah = aw / CardArt.WIDE;
        ui.rect(ax - 4, ay - 4, aw + 8, ah + 8, Theme.withAlpha(Theme.SOOT, alpha));
        ui.rect(ax - 3, ay - 3, aw + 6, ah + 6, Theme.withAlpha(Theme.BRASS, alpha));
        ui.rect(ax - 3, ay - 3, aw + 6, 1, Theme.withAlpha(Theme.BRASS_LIGHT, alpha));
        ui.rect(ax - 3, ay + ah + 2, aw + 6, 1, Theme.withAlpha(Theme.BRASS_DARK, alpha));
        ui.rect(ax - 1, ay - 1, aw + 2, ah + 2, Theme.withAlpha(Theme.SOOT, alpha));
        if (!CardArt.draw(ui, card.id(), ax, ay, aw, ah, alpha)) {
            ui.rect(ax, ay, aw, ah, rgba(rgb, 0.45f * alpha));
            String kind = card.effects().isEmpty() ? null : card.effects().get(0).kind();
            CardArt.icon(ui, kind, ax + aw / 2 - 36, ay + ah / 2 - 36, 72, alpha);
        }

        // its name, on a ribbon of its rarity's colour
        float bx = x + 12, by = y + 12, bw = W - 24, bh = 30;
        ui.rect(bx, by + 2, bw, bh, Theme.withAlpha(Theme.SOOT, 0.9f * alpha));
        ui.rect(bx + 1, by, bw - 2, bh, darker(edge, 0.45f));
        ui.rect(bx + 1, by, bw - 2, 2, lighter(edge, 0.25f));
        ui.rect(bx + 1, by + bh - 3, bw - 2, 3, darker(edge, 0.62f));
        ui.outline(bx, by, bw, bh, 1, Theme.withAlpha(Theme.SOOT, alpha));
        float room = bw - 46;
        Font nameFont = ui.body.width(card.name()) <= room ? ui.body : ui.small.width(card.name()) <= room ? ui.small
                : ui.tiny;
        String name = Ui.fit(nameFont, card.name(), room);
        float nx = bx + 40 + (room - nameFont.width(name)) / 2, ny = by + (bh - nameFont.lineHeight()) / 2;
        ui.text(nameFont, name, nx + 1, ny + 1, Theme.withAlpha(Theme.SOOT, 0.85f * alpha));
        ui.text(nameFont, name, nx, ny, Theme.withAlpha(Theme.TITLE, alpha));

        // the cog at its corner: how long it lasts
        float ox = x + 28, oy = y + 27, or = 25;
        ui.disc(ox + 1, oy + 2, or - 2, Theme.withAlpha(Theme.SOOT, 0.7f * alpha));
        ui.cog(ox, oy, or, 0.2, Theme.withAlpha(Theme.BRASS, alpha));
        ui.disc(ox, oy, or * 0.66f, Theme.withAlpha(Theme.SOOT, alpha));
        ui.disc(ox, oy, or * 0.6f, Theme.withAlpha(Theme.rgb(52, 38, 26, 1f), alpha));
        String span = span(card);
        Font spanFont = ui.small.width(span) <= or * 1.1f ? ui.small : ui.tiny;
        ui.text(spanFont, span, ox - spanFont.width(span) / 2, oy - spanFont.lineHeight() / 2,
                Theme.withAlpha(Theme.ACCENT, alpha));

        // what it does, on parchment
        float px = x + 16, py = ay + ah + 16, pw = W - 32, ph = y + H - 22 - py;
        ui.rect(px - 1, py - 1, pw + 2, ph + 2, Theme.withAlpha(Theme.SOOT, alpha));
        if (alpha >= 1) ui.parchment(px, py, pw, ph);
        else ui.rect(px, py, pw, ph, Theme.rgb(222, 200, 160, alpha));
        Font textFont = ui.paragraphHeight(ui.small, card.text(), pw - 16) <= ph - 22 ? ui.small : ui.tiny;
        float th = ui.paragraphHeight(textFont, card.text(), pw - 16);
        ui.paragraphCentered(textFont, card.text(), px + 8, py + 12 + Math.max(0, (ph - 20 - th) / 2), pw - 16,
                Theme.withAlpha(Theme.INK, alpha));

        // whose it is and how rare, on a plate across the picture's foot
        String type = (hero == null ? "Any class" : hero.name()) + " · " + rarity.label();
        float tw = ui.tiny.width(type) + 22, tx = x + (W - tw) / 2, ty = ay + ah - 6;
        ui.rect(tx - 1, ty - 1, tw + 2, 20, Theme.withAlpha(Theme.SOOT, alpha));
        ui.rect(tx, ty, tw, 18, darker(edge, 0.55f));
        ui.rect(tx, ty, tw, 1, lighter(edge, 0.2f));
        ui.text(ui.tiny, type, tx + 11, ty + (18 - ui.tiny.lineHeight()) / 2, Theme.withAlpha(Theme.TITLE, alpha));

        // the gem at its foot
        float gx = x + W / 2, gy = y + H - 12;
        ui.diamond(gx, gy, 11, Theme.withAlpha(Theme.SOOT, alpha));
        ui.diamond(gx, gy, 9, Theme.withAlpha(Theme.BRASS, alpha));
        ui.diamond(gx, gy, 6.5f, rarity.colour(alpha));
        ui.diamond(gx - 1.5f, gy - 1.5f, 2.2f, new float[]{1, 1, 1, 0.75f * alpha});
        // brass rivets in its corners
        if (alpha >= 1) {
            ui.rivet(x + 9, y + H - 17, 8);
            ui.rivet(x + W - 17, y + H - 17, 8);
            ui.rivet(x + W - 17, y + 9, 8);
        }
        ui.straight();
    }

    /** A card lying face down ({@code w} wide, centred on {@code (cx, cy)}, turned {@code angle}): the pile it is on. */
    public static void back(Ui ui, float cx, float cy, float w, double angle, float alpha) {
        float s = w / BASE, W = BASE, H = BASE * TALL;
        float x = cx - W / 2, y = cy - H / 2;
        ui.turn(cx, cy, angle, s);
        ui.rect(x, y, W, H, Theme.withAlpha(Theme.SOOT, alpha));
        ui.rect(x + 2, y + 2, W - 4, H - 4, Theme.withAlpha(Theme.BRASS, alpha));
        ui.rect(x + 2, y + 2, W - 4, 2, Theme.withAlpha(Theme.BRASS_LIGHT, alpha));
        ui.rect(x + 2, y + H - 4, W - 4, 2, Theme.withAlpha(Theme.BRASS_DARK, alpha));
        ui.rect(x + 7, y + 7, W - 14, H - 14, Theme.withAlpha(Theme.SOOT, alpha));
        ui.wood(x + 8, y + 8, W - 16, H - 16, new float[]{0.42f, 0.2f, 0.16f, alpha});
        ui.outline(x + 18, y + 18, W - 36, H - 36, 2, Theme.withAlpha(Theme.BRASS_DARK, alpha));
        ui.outline(x + 24, y + 24, W - 48, H - 48, 1, Theme.withAlpha(Theme.BRASS, 0.7f * alpha));
        float gx = x + W / 2, gy = y + H / 2;
        ui.disc(gx, gy, 62, Theme.withAlpha(Theme.SOOT, 0.55f * alpha));
        ui.cog(gx, gy, 60, 0.3, Theme.withAlpha(Theme.BRASS, alpha));
        ui.cog(gx + 46, gy + 52, 26, 0.9, Theme.withAlpha(Theme.COPPER, alpha));
        ui.cog(gx - 50, gy - 54, 22, 0.1, Theme.withAlpha(Theme.IRON, alpha));
        ui.diamond(gx, gy, 16, Theme.withAlpha(Theme.SOOT, alpha));
        ui.diamond(gx, gy, 12, Theme.withAlpha(Theme.ACCENT, alpha));
        ui.rivet(x + 10, y + 10, 9);
        ui.rivet(x + W - 19, y + 10, 9);
        ui.rivet(x + 10, y + H - 19, 9);
        ui.rivet(x + W - 19, y + H - 19, 9);
        ui.straight();
    }

    /** How long a card lasts, as its cog says it: "5m", "30s", "x3", "now". */
    public static String span(Card card) {
        double seconds = 0;
        int charges = 0;
        boolean lasting = false;
        for (Card.Effect e : card.effects()) {
            if (e.buff() == null) continue;
            lasting = true;
            seconds = Math.max(seconds, e.seconds());
            charges = Math.max(charges, e.charges());
        }
        if (!lasting) return "now";
        if (seconds <= 0 && charges > 0) return "×" + charges;
        if (seconds <= 0) return "1m";
        if (seconds >= 60 && seconds % 60 == 0) return (int) (seconds / 60) + "m";
        return (int) seconds + "s";
    }

    /** Whether {@code card}'s words fit on a card (they always do now: it is laid out at one size). */
    public static boolean fits(Ui ui, Card card, float w) {
        return true;
    }

    /** The frame's colour: the rarity's (a common card's white as worn pewter). */
    private static float[] frameColour(Rarity r, float alpha) {
        if (r == Rarity.COMMON) return Theme.rgb(176, 172, 162, alpha);
        return r.colour(alpha);
    }

    /** The stain over the oak: the class's colour, deep enough to sit behind the picture and the words. */
    private static float[] stain(int rgb, float alpha) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        return new float[]{r * 0.62f, g * 0.62f, b * 0.62f, 0.66f * alpha};
    }

    private static float[] lighter(float[] c, float by) {
        return new float[]{c[0] + (1 - c[0]) * by, c[1] + (1 - c[1]) * by, c[2] + (1 - c[2]) * by, c[3]};
    }

    private static float[] darker(float[] c, float by) {
        return new float[]{c[0] * (1 - by), c[1] * (1 - by), c[2] * (1 - by), c[3]};
    }

    private static float[] rgba(int rgb, float a) {
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, a};
    }
}
