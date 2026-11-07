package com.larsons.game.cards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Her cards in play: the <b>draw pile</b>, her <b>hand</b> and the <b>discard
 * pile</b>, and every card she owns among them.
 *
 * <ul>
 *   <li>She starts with a hand of {@link CardBook#drawAtStart} (4) from her
 *       shuffled deck,</li>
 *   <li>draws one more every {@link CardBook#drawEvery} seconds (5 minutes)
 *       while her hand has fewer than {@link CardBook#handMost} (8),</li>
 *   <li>a card she plays goes onto the discard pile, and stays there,</li>
 *   <li>until a reshuffle scroll puts her hand and the discard pile back into
 *       the deck, shuffled, and deals her a fresh hand.</li>
 *   <li>A card she gets - a perk's, a shop's - goes onto the discard pile.</li>
 * </ul>
 */
public final class Deck {

    private final List<String> draw = new ArrayList<>(), hand = new ArrayList<>(), discard = new ArrayList<>();
    private double untilDraw;
    private final Random random;
    private int dealt = 4, most = 8;
    private double every = 300;

    public Deck(Random random) {
        this.random = random;
    }

    /** A new deck of {@code cards} (an id a copy), shuffled, and a hand dealt from it. */
    public void start(List<String> cards, int dealt, int most, double every) {
        this.dealt = Math.max(0, dealt);
        this.most = Math.max(1, most);
        this.every = Math.max(1, every);
        draw.clear();
        hand.clear();
        discard.clear();
        draw.addAll(cards);
        Collections.shuffle(draw, random);
        deal();
        untilDraw = this.every;
    }

    public List<String> drawPile() { return Collections.unmodifiableList(draw); }

    public List<String> hand() { return Collections.unmodifiableList(hand); }

    public List<String> discardPile() { return Collections.unmodifiableList(discard); }

    /** Every card she owns, wherever it lies. */
    public List<String> owned() {
        List<String> out = new ArrayList<>(draw);
        out.addAll(hand);
        out.addAll(discard);
        return out;
    }

    /** Seconds until she draws again (when her hand has room). */
    public double untilDraw() { return untilDraw; }

    public int most() { return most; }

    /** Time passes: every so often a card is drawn. */
    public void tick(double dt) {
        if (hand.size() >= most || draw.isEmpty()) {
            untilDraw = every;
            return;
        }
        untilDraw -= dt;
        if (untilDraw <= 0) {
            drawCards(1);
            untilDraw = every;
        }
    }

    /** Draw up to {@code n} cards into her hand (no more than it holds, no more than the pile has); how many. */
    public int drawCards(int n) {
        int took = 0;
        while (took < n && hand.size() < most && !draw.isEmpty()) {
            hand.add(draw.remove(draw.size() - 1));
            took++;
        }
        return took;
    }

    /** The card at {@code index} in her hand, played: onto the discard pile. Returns its id, or null. */
    public String play(int index) {
        if (index < 0 || index >= hand.size()) return null;
        String id = hand.remove(index);
        discard.add(id);
        return id;
    }

    /** Her hand and the discard pile back into the deck, shuffled, and a fresh hand dealt. */
    public void reshuffle() {
        draw.addAll(hand);
        draw.addAll(discard);
        hand.clear();
        discard.clear();
        Collections.shuffle(draw, random);
        deal();
        untilDraw = every;
    }

    /** A new card of hers: onto the discard pile. */
    public void gain(String id) {
        if (id != null) discard.add(id);
    }

    /** A card taken out of her deck altogether (wherever it lies); whether it was there. */
    public boolean lose(String id) {
        return hand.remove(id) || discard.remove(id) || draw.remove(id);
    }

    private void deal() {
        drawCards(dealt);
    }
}
