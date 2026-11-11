package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.cards.Card;
import com.larsons.game.cards.CardBook;
import com.larsons.game.cards.Hero;
import com.larsons.game.core.Game;
import com.larsons.game.input.Action;
import com.larsons.game.input.Input;
import com.larsons.game.ui.BuffPlates;
import com.larsons.game.ui.CardArt;
import com.larsons.game.ui.CardFace;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Inventory;
import com.larsons.game.world.Item;
import com.larsons.game.world.Player;
import com.larsons.game.world.Supplies;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

/**
 * The card table (J): the game stands still behind it, dimmed, and her cards
 * are laid on an oak board along the bottom of the screen.
 *
 * <ul>
 *   <li>Her <b>hand</b> is fanned in the middle. The card under the pointer
 *       rises out of the fan, large enough to read. <b>Click it, or drag it
 *       up off the board and let go, to play it</b>: it flies to the middle,
 *       flares in its rarity's colour, and goes onto the discard pile.
 *       Dropped back on the board (or right-clicked while held) it returns
 *       to the hand.</li>
 *   <li>The <b>draw pile</b> lies face down on the left, with how many are
 *       in it and when the next is drawn; the <b>discard pile</b> face up on
 *       the right, with the Reshuffle button over it. Click either to look
 *       through it.</li>
 *   <li>Along the top: her class, level and experience, and <b>Hand</b>,
 *       <b>Deck</b> (every card she owns), <b>Perks</b> (her class's perk
 *       tree, a point a level) and <b>Close</b>.</li>
 *   <li>What her cards are doing for her now stands down the left ({@link
 *       BuffPlates}), as it does while she plays.</li>
 * </ul>
 */
final class CardTable {

    /** What is on the table. */
    enum View { TABLE, DRAW, DISCARD, DECK, PERKS }

    /** A card on its way from her hand to the middle of the table and on to the discard pile. */
    private static final class Flight {
        final Card card;
        final String said;
        final float x, y, w;
        double t;

        Flight(Card card, String said, float x, float y, float w) {
            this.card = card;
            this.said = said;
            this.x = x;
            this.y = y;
            this.w = w;
        }
    }

    private static final double FLY_IN = 0.16, FLARE = 0.5, FLY_OUT = 0.26;

    private final Game game;
    private final World world;
    private final CardGrid grid = new CardGrid();
    private boolean open;
    private View view = View.TABLE;
    /** Where each card of her hand lies now: x, y, angle, width (eased toward where it belongs). */
    private final List<float[]> lie = new ArrayList<>();
    private final List<Flight> flights = new ArrayList<>();
    private int hot = -1, held = -1;
    private float pressX, pressY, grabX, grabY;
    private boolean dragged;
    private double time;
    private float shown;

    CardTable(Game game, World world) {
        this.game = game;
        this.world = world;
    }

    boolean open() { return open; }

    View view() { return view; }

    /** Lay the table (or clear it). */
    void toggle() {
        if (open) close();
        else show(View.TABLE);
    }

    void show(View v) {
        if (!open) {
            lie.clear();
            flights.clear();
            shown = 0;
            Sounds.play(SoundKeys.ui("card_open"));
        }
        open = true;
        view = v;
        held = hot = -1;
        grid.rewind();
    }

    void close() {
        if (open) Sounds.play(SoundKeys.ui("inventory_close"));
        open = false;
        held = hot = -1;
        flights.clear();
    }

    /** Esc: back from a pile or the perks to the hand, or the table cleared. */
    void escape() {
        if (view != View.TABLE) {
            view = View.TABLE;
            Sounds.play(SoundKeys.ui("back"));
        } else {
            close();
        }
    }

    /** Play the card at {@code index} of her hand (a click, a script); whether there was one. */
    boolean play(int index) {
        Hero hero = world.player().hero();
        if (index < 0 || index >= hero.deck().hand().size()) return false;
        float[] l = index < lie.size() ? lie.remove(index) : null;
        Hero.Played p = hero.play(index, world);
        if (p == null) return false;
        Sounds.play(SoundKeys.ui("card_play"));
        String said = p.said().isEmpty() ? "" : String.join(", ", p.said());
        // (on the table the card itself is shown played; off it - a script - a line says so)
        if (open && l != null) flights.add(new Flight(p.card(), said, l[0], l[1], l[3]));
        else game.toast(p.card().name() + ": " + (said.isEmpty() ? p.card().text() : said), p.card().rarity().colour(1f));
        return true;
    }

    // --- drawing --------------------------------------------------------------------

    void draw(Ui ui, double dt) {
        if (!open) return;
        time += dt;
        shown = (float) Math.min(1, shown + dt * 7);
        Player p = world.player();
        Hero hero = p.hero();
        CardBook.Hero cls = CardBook.get().hero(hero.heroClass());
        float W = ui.width(), H = ui.height();
        float cw = Math.max(118, Math.min(178, W * 0.118f)), ch = cw * CardFace.TALL;
        float board = H - ch * 0.66f * shown;

        ui.rect(0, 0, W, H, Theme.withAlpha(Theme.SCRIM, 0.6f * shown));
        // the board her cards lie on: oak, with a brass rail along its edge
        ui.wood(0, board, W, H - board, new float[]{0.36f, 0.31f, 0.28f, 0.97f});
        ui.rect(0, board + 6, W, 26, Theme.withAlpha(Theme.SOOT, 0.25f));
        ui.rect(0, board - 1, W, 1, Theme.SOOT);
        ui.rect(0, board, W, 4, Theme.BRASS);
        ui.rect(0, board, W, 1, Theme.BRASS_LIGHT);
        ui.rect(0, board + 4, W, 1, Theme.SOOT);
        for (float rx = 24; rx < W; rx += 96) ui.rivet(rx, board + 9, 7);

        bar(ui, hero, cls, W);
        if (cls == null) {
            ui.textCentered(ui.large, "Choose a class first", W / 2, H * 0.36f, Theme.TITLE);
            ui.textCentered(ui.body, "Pause → Class: each of the nine has its own deck of cards.", W / 2, H * 0.36f + 40,
                    Theme.HINT);
            return;
        }
        BuffPlates.draw(ui, p.buffs(), 16, 82, time);
        switch (view) {
            case TABLE -> table(ui, dt, hero, W, H, cw, board);
            case PERKS -> perks(ui, hero, cls, W, H);
            default -> pile(ui, hero, W, H);
        }
    }

    /** Along the top: whose cards, her level and experience, and the table's pages. */
    private void bar(Ui ui, Hero hero, CardBook.Hero cls, float W) {
        float x = 10, y = 8, w = W - 20, h = 58;
        ui.panel(x, y, w, h);
        String title = cls == null ? "Cards" : cls.name() + "'s cards";
        float tx = x + 24;
        ui.text(ui.large, title, tx, y + 14, Theme.TITLE);
        tx += ui.large.width(title) + 26;
        // her level, in a cog; her experience on a gauge
        ui.cog(tx + 16, y + h / 2, 21, time * 0.25, Theme.BRASS);
        ui.disc(tx + 16, y + h / 2, 13, Theme.rgb(30, 22, 16, 1f));
        String level = String.valueOf(hero.level());
        ui.text(ui.small, level, tx + 16 - ui.small.width(level) / 2, y + h / 2 - ui.small.lineHeight() / 2, Theme.ACCENT);
        tx += 46;
        ui.text(ui.tiny, "Level " + hero.level() + " · " + hero.xp() + " / " + hero.toNext() + " experience", tx, y + 12,
                Theme.HINT);
        ui.bar(tx, y + 31, 190, 13, hero.xp() / (double) Math.max(1, hero.toNext()), Theme.ACCENT);

        float bw = 96, bh = 34, bx = x + w - 18 - bw, by = y + 12;
        if (ui.button("Close", bx, by, bw, bh)) {
            close();
            return;
        }
        if (cls == null) return;
        bx -= bw + 34;
        String perks = hero.points() > 0 ? "Perks (" + hero.points() + ")" : "Perks";
        if (ui.button(perks, bx - 20, by, bw + 20, bh, view == View.PERKS, true)) go(View.PERKS);
        if (hero.points() > 0 && view != View.PERKS) {
            // a point to spend: a gem on the button, pulsing
            float k = 0.6f + 0.4f * (float) Math.sin(time * 5);
            ui.diamond(bx + bw - 4, by + 2, 7, Theme.SOOT);
            ui.diamond(bx + bw - 4, by + 2, 5, Theme.withAlpha(Theme.OK, k));
        }
        bx -= bw + 28;
        if (ui.button("Deck", bx, by, bw, bh, view == View.DECK, true)) go(View.DECK);
        bx -= bw + 8;
        if (ui.button("Hand", bx, by, bw, bh, view == View.TABLE, true)) go(View.TABLE);
    }

    private void go(View v) {
        view = v;
        held = hot = -1;
        grid.rewind();
    }

    // --- the hand and the piles -------------------------------------------------------

    private void table(Ui ui, double dt, Hero hero, float W, float H, float cw, float board) {
        CardBook book = CardBook.get();
        Input in = ui.input();
        List<String> hand = hero.deck().hand();
        int n = hand.size();
        float ch = cw * CardFace.TALL;
        float mx = (float) in.mouseX(), my = (float) in.mouseY();

        // the piles: face down on the left, face up on the right
        float pw = cw * 0.74f, ph = pw * CardFace.TALL;
        float drawX = 34 + pw / 2, discardX = W - 34 - pw / 2, pileY = H - ph / 2 - 30;
        int inDraw = hero.deck().drawPile().size(), inDiscard = hero.deck().discardPile().size();
        boolean overDraw = held < 0 && ui.hover(drawX - pw / 2, pileY - ph / 2, pw, ph);
        boolean overDiscard = held < 0 && ui.hover(discardX - pw / 2, pileY - ph / 2, pw, ph);
        if (inDraw == 0) slot(ui, drawX, pileY, pw, ph);
        for (int k = Math.min(3, inDraw) - 1; k >= 0; k--) {
            CardFace.back(ui, drawX - k * 3, pileY - k * 3, pw, -0.03 * k, 1f);
        }
        if (overDraw) ui.outline(drawX - pw / 2 - 3, pileY - ph / 2 - 3, pw + 6, ph + 6, 2, Theme.ACCENT);
        badge(ui, drawX + pw / 2 - 4, pileY - ph / 2 + 4, inDraw);
        ui.textCentered(ui.tiny, "Draw pile", drawX, H - 24, Theme.HINT);
        String next = n >= hero.deck().most() ? "Hand full" : inDraw == 0 ? "Pile empty"
                : "Next card " + SidePanels.clock(hero.deck().untilDraw());
        ui.textCentered(ui.tiny, next, drawX, pileY - ph / 2 - 22, Theme.ACCENT);

        if (inDiscard == 0) {
            slot(ui, discardX, pileY, pw, ph);
        } else {
            List<String> pile = hero.deck().discardPile();
            if (inDiscard > 1) CardFace.back(ui, discardX + 3, pileY - 3, pw, 0.05, 1f);
            Card top = book.card(pile.get(pile.size() - 1));
            if (top != null) CardFace.draw(ui, top, discardX, pileY, pw, 0.04, false, 1f);
        }
        if (overDiscard) ui.outline(discardX - pw / 2 - 3, pileY - ph / 2 - 3, pw + 6, ph + 6, 2, Theme.ACCENT);
        badge(ui, discardX - pw / 2 + 4, pileY - ph / 2 + 4, inDiscard);
        ui.textCentered(ui.tiny, "Discard pile", discardX, H - 24, Theme.HINT);
        Inventory inv = world.player().inventory();
        int scrolls = inv.total(Item.of(Supplies.RESHUFFLE_SCROLL));
        float rw = Math.max(pw + 30, 150), rx = Math.min(W - rw - 12, discardX - rw / 2);
        if (ui.button("Reshuffle (" + scrolls + ")", rx, pileY - ph / 2 - 46, rw, 30, false,
                scrolls > 0 && !hero.deck().owned().isEmpty() && flights.isEmpty())) {
            inv.takeOne(inv.indexOf(Supplies.RESHUFFLE_SCROLL));
            hero.deck().reshuffle();
            lie.clear();
            held = hot = -1;
            Sounds.play(SoundKeys.ui("card_shuffle"));
            game.toast("Reshuffled: a fresh hand of " + hero.deck().hand().size(), Theme.HINT);
            return;
        }
        if (overDraw && ui.clicked(drawX - pw / 2, pileY - ph / 2, pw, ph)) {
            go(View.DRAW);
            Sounds.play(SoundKeys.ui("click"));
            return;
        }
        if (overDiscard && ui.clicked(discardX - pw / 2, pileY - ph / 2, pw, ph)) {
            go(View.DISCARD);
            Sounds.play(SoundKeys.ui("click"));
            return;
        }

        // where each card of the hand belongs: fanned about the middle, lower toward its ends
        while (lie.size() > n) lie.remove(lie.size() - 1);
        while (lie.size() < n) {
            lie.add(new float[]{drawX, pileY, 0, pw});          // (dealt from the draw pile)
            Sounds.play(SoundKeys.ui("card_draw"), 0.7);
        }
        float spacing = n > 1 ? Math.min(cw * 0.86f, W * 0.54f / (n - 1)) : 0;
        float baseY = H - ch / 2 - 14;
        float hw = Math.min(CardFace.BASE, cw * 1.62f), hh = hw * CardFace.TALL;
        float[] bx = new float[n];
        for (int i = 0; i < n; i++) bx[i] = W / 2 + (i - (n - 1) / 2f) * spacing;

        // the card the pointer is on: the one whose stretch of the fan it is over
        int over = -1;
        if (held < 0 && !overDraw && !overDiscard) {
            float top = hot >= 0 && hot < n ? H - hh - 16 : baseY - ch / 2 - 6;
            if (my >= top) {
                for (int i = 0; i < n; i++) {
                    float left = bx[i] - cw / 2, right = i < n - 1 ? bx[i + 1] - cw / 2 : bx[i] + cw / 2;
                    if (mx >= left && mx < right) over = i;
                }
                if (over < 0 && hot >= 0 && hot < n && Math.abs(mx - bx[hot]) <= hw / 2) over = hot;
            }
        }
        if (over != hot && over >= 0) Sounds.play(SoundKeys.ui("hover"), 0.5);
        hot = over;

        // taking one up, carrying it, letting it go
        if (held >= n) held = -1;
        if (held < 0 && hot >= 0 && in.mousePressed(GLFW_MOUSE_BUTTON_LEFT)) {
            held = hot;
            pressX = mx;
            pressY = my;
            grabX = lie.get(held)[0] - mx;
            grabY = lie.get(held)[1] - my;
            dragged = false;
            Sounds.play(SoundKeys.ui("item_pick"), 0.7);
        }
        boolean inPlay = false;
        if (held >= 0) {
            if (Math.hypot(mx - pressX, my - pressY) > 9) dragged = true;
            float[] l = lie.get(held);
            inPlay = dragged && l[1] < board - ch * 0.12f;
            if (in.mousePressed(GLFW_MOUSE_BUTTON_RIGHT)) {
                held = -1;                                    // (put back)
                Sounds.play(SoundKeys.ui("item_place"), 0.7);
            } else if (!in.mouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
                int was = held;
                held = -1;
                hot = -1;
                if (!dragged || inPlay) {
                    play(was);
                    return;
                }
                Sounds.play(SoundKeys.ui("item_place"), 0.7);
            }
        }

        // where to play it: shown while one is carried
        float zy = 82, zh = board - 34 - zy, zx = W * 0.22f, zw = W * 0.56f;
        if (held >= 0 && dragged) {
            float k = inPlay ? 0.9f : 0.35f + 0.15f * (float) Math.sin(time * 6);
            ui.rect(zx, zy, zw, zh, Theme.withAlpha(Theme.ACCENT, inPlay ? 0.10f : 0.04f));
            ui.outline(zx, zy, zw, zh, 2, Theme.withAlpha(Theme.ACCENT, k));
            ui.textCentered(ui.large, inPlay ? "Let go to play it" : "Carry it up here to play it", W / 2,
                    zy + 14, Theme.withAlpha(Theme.TITLE, inPlay ? 1f : 0.7f));
        } else if (n == 0 && flights.isEmpty()) {
            ui.textCentered(ui.body, "No cards in hand.", W / 2, zy + zh / 2 - 24, Theme.TITLE);
            ui.textCentered(ui.small, "One is drawn every " + SidePanels.minutes(book.drawEvery())
                    + " while the draw pile has any; a reshuffle scroll deals a fresh hand.", W / 2, zy + zh / 2 + 6,
                    Theme.HINT);
        } else if (hot < 0 && flights.isEmpty()) {
            String how = "Click a card to play it, or drag it up off the board  ·  " + in.label(Action.CARDS)
                    + " or Esc: back to the game";
            float hy = baseY - ch / 2 - 44;
            float hwid = ui.small.width(how) + 28;
            ui.plate(W / 2 - hwid / 2, hy - 6, hwid, ui.small.lineHeight() + 12, null);
            ui.textCentered(ui.small, how, W / 2, hy, Theme.HINT);
        }

        // the hand: each card eased to where it belongs, the one pointed at (or carried) drawn last
        float ease = (float) (1 - Math.exp(-dt * 16));
        int front = held >= 0 ? held : hot;
        for (int i = 0; i < n; i++) {
            float off = i - (n - 1) / 2f;
            float tx = bx[i], ty = baseY + off * off * cw * 0.035f, ta = Math.max(-0.3f, Math.min(0.3f, off * 0.06f));
            float tw = cw;
            if (i == held) {
                tx = mx + grabX;
                ty = my + grabY;
                ta = 0;
                tw = cw * 1.12f;
            } else if (i == hot) {
                tx = Math.max(hw / 2 + 8, Math.min(W - hw / 2 - 8, bx[i]));
                ty = H - hh / 2 - 12;
                ta = 0;
                tw = hw;
            } else if (hot >= 0 && held < 0) {
                tx += (i < hot ? -1 : 1) * (hw - cw) * 0.42f;      // (the others make room)
            }
            float[] l = lie.get(i);
            float e = i == held ? Math.min(1, ease * 2.4f) : ease;
            l[0] += (tx - l[0]) * e;
            l[1] += (ty - l[1]) * e;
            l[2] += (ta - l[2]) * e;
            l[3] += (tw - l[3]) * e;
        }
        for (int i = 0; i < n; i++) if (i != front) card(ui, book.card(hand.get(i)), lie.get(i), false);
        if (front >= 0 && front < n) card(ui, book.card(hand.get(front)), lie.get(front), true);

        // the cards just played: to the middle, a flare, and onto the discard pile
        for (int i = flights.size() - 1; i >= 0; i--) {
            Flight f = flights.get(i);
            f.t += dt;
            if (f.t >= FLY_IN + FLARE + FLY_OUT) {
                flights.remove(i);
                continue;
            }
            float midX = W / 2, midY = Math.max(hh / 2 + 76, (82 + board) / 2), midW = hw;
            if (f.t < FLY_IN) {
                float k = smooth(f.t / FLY_IN);
                CardFace.draw(ui, f.card, mix(f.x, midX, k), mix(f.y, midY, k), mix(f.w, midW, k), 0, true, 1f);
            } else if (f.t < FLY_IN + FLARE) {
                float k = (float) ((f.t - FLY_IN) / FLARE);
                // the flare: rings of its rarity's colour widening and fading
                for (int r = 0; r < 3; r++) {
                    float g = (k + r * 0.22f) % 1f, s = 18 + g * 90;
                    ui.outline(midX - midW / 2 - s, midY - hh / 2 - s, midW + 2 * s, hh + 2 * s, 3,
                            f.card.rarity().colour(0.5f * (1 - g)));
                }
                CardFace.draw(ui, f.card, midX, midY, midW * (1 + 0.05f * (float) Math.sin(k * Math.PI)), 0, true, 1f);
                effects(ui, f.card, midX + midW / 2 + 34, midY, k);
                if (!f.said.isEmpty()) {
                    float a = Math.min(1, k * 5) * Math.min(1, (1 - k) * 5);
                    float sw = ui.body.width(f.said) + 32, sy = midY - hh / 2 - 44;
                    ui.rect(midX - sw / 2, sy, sw, 30, Theme.withAlpha(Theme.SOOT, 0.85f * a));
                    ui.outline(midX - sw / 2, sy, sw, 30, 1, f.card.rarity().colour(a));
                    ui.textCentered(ui.body, f.said, midX, sy + 4, Theme.withAlpha(Theme.TITLE, a));
                }
            } else {
                float k = smooth((f.t - FLY_IN - FLARE) / FLY_OUT);
                CardFace.draw(ui, f.card, mix(midX, discardX, k), mix(midY, pileY, k), mix(midW, pw, k), 0.04 * k,
                        false, 1f - 0.3f * k);
            }
        }
    }

    /** What a card just played does, as its icons in a column beside it. */
    private static void effects(Ui ui, Card card, float x, float cy, float k) {
        int n = card.effects().size();
        float size = 48, gap = 12, y = cy - (n * size + (n - 1) * gap) / 2;
        float a = Math.min(1, k * 4) * Math.min(1, (1 - k) * 4);
        for (Card.Effect e : card.effects()) {
            ui.disc(x + size / 2, y + size / 2, size / 2 + 5, Theme.withAlpha(Theme.BRASS_DARK, a));
            ui.disc(x + size / 2, y + size / 2, size / 2 + 3, Theme.withAlpha(Theme.SOOT, a));
            CardArt.icon(ui, e.kind(), x, y, size, a);
            y += size + gap;
        }
    }

    private static void card(Ui ui, Card c, float[] l, boolean front) {
        if (c == null) return;
        CardFace.draw(ui, c, l[0], l[1], l[3], l[2], front, 1f);
    }

    /** An empty place for a pile. */
    private static void slot(Ui ui, float cx, float cy, float w, float h) {
        ui.rect(cx - w / 2, cy - h / 2, w, h, Theme.withAlpha(Theme.SOOT, 0.35f));
        ui.outline(cx - w / 2, cy - h / 2, w, h, 2, Theme.withAlpha(Theme.BRASS_DARK, 0.8f));
    }

    /** How many cards a pile has, on a brass stud at its corner. */
    private static void badge(Ui ui, float cx, float cy, int count) {
        ui.disc(cx, cy, 17, Theme.SOOT);
        ui.disc(cx, cy, 15, Theme.BRASS);
        ui.disc(cx, cy, 12, Theme.rgb(30, 22, 16, 1f));
        String s = String.valueOf(count);
        ui.text(ui.small, s, cx - ui.small.width(s) / 2, cy - ui.small.lineHeight() / 2, Theme.TITLE);
    }

    private static float mix(float a, float b, float k) {
        return a + (b - a) * k;
    }

    private static float smooth(double k) {
        float t = (float) Math.max(0, Math.min(1, k));
        return t * t * (3 - 2 * t);
    }

    // --- a pile looked through ---------------------------------------------------------

    private void pile(Ui ui, Hero hero, float W, float H) {
        CardBook book = CardBook.get();
        float x = 270, y = 76, w = W - x - 20, h = H - y - 16;
        ui.panel(x, y, w, h);
        List<Card> cards = new ArrayList<>();
        List<String> where = new ArrayList<>();
        String title, note;
        switch (view) {
            case DRAW -> {
                for (String id : hero.deck().drawPile()) add(cards, book.card(id));
                cards.sort(Comparator.comparing(Card::name));        // (not in the order they will be drawn)
                title = "Draw pile";
                note = "In no order: which comes next is not told.";
            }
            case DISCARD -> {
                List<String> pile = hero.deck().discardPile();
                for (int i = pile.size() - 1; i >= 0; i--) add(cards, book.card(pile.get(i)));
                title = "Discard pile";
                note = "The last played first. A reshuffle scroll puts them back into the draw pile.";
            }
            default -> {
                record Owned(Card card, String where) {}
                List<Owned> all = new ArrayList<>();
                for (String id : hero.deck().hand()) if (book.card(id) != null) all.add(new Owned(book.card(id), "in hand"));
                for (String id : hero.deck().drawPile()) {
                    if (book.card(id) != null) all.add(new Owned(book.card(id), "draw pile"));
                }
                for (String id : hero.deck().discardPile()) {
                    if (book.card(id) != null) all.add(new Owned(book.card(id), "discarded"));
                }
                all.sort(Comparator.comparing((Owned o) -> -o.card().rarity().ordinal())
                        .thenComparing(o -> o.card().name()));
                for (Owned o : all) {
                    cards.add(o.card());
                    where.add(o.where());
                }
                title = "Your deck";
                note = "Every card you own, the rarest first. Perks and shops add to it.";
            }
        }
        ui.text(ui.large, title + " (" + cards.size() + ")", x + 22, y + 14, Theme.TITLE);
        ui.text(ui.small, note, x + 22, y + 48, Theme.ITEM_DISABLED);
        float cardW = Math.max(120, Math.min(160, (w - 60) / 6 - 12));
        grid.draw(ui, cards, where.isEmpty() ? null : where::get, null, x + 16, y + 76, w - 32, h - 90, cardW);
        grid.preview(ui);
    }

    private static void add(List<Card> to, Card c) {
        if (c != null) to.add(c);
    }

    // --- her perk tree -----------------------------------------------------------------

    private void perks(Ui ui, Hero hero, CardBook.Hero cls, float W, float H) {
        CardBook book = CardBook.get();
        List<CardBook.Perk> tree = book.perks(cls.id());
        float x = 270, y = 76, w = W - x - 20, h = H - y - 16;
        ui.panel(x, y, w, h);
        ui.text(ui.large, cls.name() + "'s perks", x + 22, y + 14, Theme.TITLE);
        String points = hero.points() + (hero.points() == 1 ? " perk point" : " perk points");
        ui.text(ui.body, points, x + w - 22 - ui.body.width(points), y + 18, hero.points() > 0 ? Theme.OK : Theme.ITEM);
        ui.text(ui.small, "Every level gives a point. A row needs one perk of the row above. A card perk puts its card "
                + "on your discard pile; the others help for good.", x + 22, y + 48, Theme.ITEM_DISABLED);

        int rows = 0;
        for (CardBook.Perk p : tree) rows = Math.max(rows, p.row() + 1);
        float side = Math.min(CardFace.BASE, Math.min(w * 0.3f, (h - 110) / CardFace.TALL));
        float tw = w - side - 66, top = y + 84, rowH = Math.min(118, (h - 100) / Math.max(1, rows));
        CardBook.Perk hovered = null;
        for (int r = 0; r < rows; r++) {
            final int rr = r;
            List<CardBook.Perk> row = tree.stream().filter(p -> p.row() == rr).toList();
            float nw = (tw - (row.size() - 1) * 12) / Math.max(1, row.size());
            // the brass pipe down from the row above
            if (r > 0) ui.rect(x + 22 + tw / 2 - 2, top + r * rowH - 14, 4, 14, Theme.BRASS_DARK);
            for (int i = 0; i < row.size(); i++) {
                CardBook.Perk p = row.get(i);
                float nx = x + 22 + i * (nw + 12), ny = top + r * rowH, nh = rowH - 14;
                boolean have = hero.perks().contains(p.id()), can = hero.canTake(p.id());
                boolean over = ui.hover(nx, ny, nw, nh);
                if (over) hovered = p;
                float[] edge = have ? Theme.OK : can ? Theme.ACCENT : Theme.BRASS_DARK;
                ui.plate(nx, ny, nw, nh, edge);
                if (have) ui.rect(nx + 2, ny + 2, nw - 4, nh - 4, Theme.withAlpha(Theme.OK, 0.14f));
                else if (can && over) ui.rect(nx + 2, ny + 2, nw - 4, nh - 4, Theme.withAlpha(Theme.ACCENT, 0.12f));
                if (can && !have) {
                    float k = 0.45f + 0.35f * (float) Math.sin(time * 4 + i);
                    ui.outline(nx - 1, ny - 1, nw + 2, nh + 2, 1, Theme.withAlpha(Theme.ACCENT, k));
                }
                // its icon: what its card (or it) does
                Card c = p.card() == null ? null : book.card(p.card());
                String kind = c != null && !c.effects().isEmpty() ? c.effects().get(0).kind()
                        : !p.passive().isEmpty() ? p.passive().get(0).kind() : null;
                float is = nh >= 64 ? 48 : 24, ix = nx + 10, iy = ny + (nh - is) / 2;
                ui.disc(ix + is / 2, iy + is / 2, is / 2 + 4, Theme.withAlpha(Theme.SOOT, 0.9f));
                CardArt.icon(ui, kind, ix, iy, is, have || can ? 1f : 0.45f);
                float lx = ix + is + 12, lw = nx + nw - 10 - lx;
                float[] ink = have ? Theme.OK : can ? Theme.TITLE : Theme.ITEM_DISABLED;
                ui.text(ui.body.width(p.name()) <= lw ? ui.body : ui.small, Ui.fit(ui.small, p.name(), lw), lx, ny + 10, ink);
                String what = c != null ? "Card · " + c.rarity().label() : "For good";
                ui.text(ui.tiny, what, lx, ny + 36, c != null ? c.rarity().colour(have || can ? 1f : 0.5f) : Theme.HINT);
                String cost = have ? "Taken" : p.cost() + (p.cost() == 1 ? " point" : " points");
                ui.text(ui.tiny, cost, lx, ny + nh - ui.tiny.lineHeight() - 8, have ? Theme.OK : Theme.ITEM);
                if (can && ui.clicked(nx, ny, nw, nh) && hero.take(p.id(), world.player())) {
                    Sounds.play(SoundKeys.ui("perk"));
                    game.toast(p.name() + (p.card() != null ? ": its card is on your discard pile" : ": yours for good"),
                            Theme.OK);
                }
            }
        }
        // the perk under the pointer: its card, or what it does for good
        float sx = x + w - side - 22, sy = y + 84;
        if (hovered != null) {
            Card c = hovered.card() == null ? null : book.card(hovered.card());
            if (c != null) {
                CardFace.draw(ui, c, sx + side / 2, sy + side * CardFace.TALL / 2, side, 0, false, 1f);
            } else {
                ui.parchment(sx, sy, side, 150);
                ui.textCentered(ui.body, hovered.name(), sx + side / 2, sy + 14, Theme.INK);
                ui.paragraphCentered(ui.small, hovered.text(), sx + 14, sy + 50, side - 28, Theme.INK);
            }
        } else {
            ui.paragraphCentered(ui.small, "Point at a perk to see its card.", sx, sy + 40, side, Theme.ITEM_DISABLED);
        }
    }
}
