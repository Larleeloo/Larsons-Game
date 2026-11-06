package com.larsons.game.cards;

import com.larsons.game.world.Buffs;
import com.larsons.game.world.Player;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Who she is as a fighter: her <b>class</b> (Mage, Bard, Knight, Warrior,
 * Warlock, Druid, Rogue, Ranger, Cleric - {@link CardBook#heroes}), her
 * <b>deck</b> of cards ({@link Deck}), her <b>experience</b> and the level it
 * comes to, and the <b>perk points</b> each level gives, spent on her class's
 * perk tree ({@link CardBook.Perk}): a node there puts a card into her deck,
 * or helps her for good.
 *
 * <p>Levels: the n-th takes {@code 100 n} experience more than the one
 * before (100, 200, 300 ...); each gives a perk point. Experience comes from
 * what she fells, hunts and opens ({@link #XP_FELLED} ...), times any card's.
 */
public final class Hero {

    /** Experience for felling a dummy, a flier; for hunting an animal; for opening a chest. */
    public static final int XP_FELLED = 20, XP_FLIER = 30, XP_HUNTED = 10, XP_CHEST = 10;

    private String hero;
    private final Deck deck;
    private int xp, level = 1, points;
    private final Set<String> perks = new LinkedHashSet<>();

    public Hero(Random random) {
        this.deck = new Deck(random);
    }

    /** Her class's id, or null before she has chosen one. */
    public String heroClass() { return hero; }

    public Deck deck() { return deck; }

    public int xp() { return xp; }

    public int level() { return level; }

    public int points() { return points; }

    public Set<String> perks() { return Collections.unmodifiableSet(perks); }

    /** Experience from the start of her level to the next ({@code 100 n}). */
    public int toNext() { return 100 * level; }

    /**
     * She becomes {@code heroClass}: its starting deck shuffled and a hand
     * dealt, her perks and their helps for good gone (her experience, level
     * and points kept, spent points given back), and whatever cards are doing
     * for her ended.
     */
    public void choose(String heroClass, Player player) {
        CardBook book = CardBook.get();
        if (book.hero(heroClass) == null) return;
        for (String id : perks) {
            CardBook.Perk p = book.perk(hero, id);
            if (p != null) points += p.cost();
        }
        perks.clear();
        hero = heroClass;
        deck.start(book.startingDeck(heroClass), book.drawAtStart(), book.handMost(), book.drawEvery());
        if (player != null) player.buffs().clear();
    }

    /** {@code n} experience earned (times any card's); whether she went up a level. */
    public boolean gain(double n, Buffs buffs) {
        int got = (int) Math.round(n * (buffs == null ? 1 : buffs.times(Buffs.Kind.XP)));
        if (got <= 0) return false;
        xp += got;
        boolean up = false;
        while (xp >= toNext()) {
            xp -= toNext();
            level++;
            points++;
            up = true;
        }
        return up;
    }

    /** Points given her outright (Creative Mode, a script). */
    public void addPoints(int n) {
        points = Math.max(0, points + n);
    }

    /** Whether she can take perk {@code id} now: her class's, not taken, paid for, and one of the row above taken. */
    public boolean canTake(String id) {
        CardBook book = CardBook.get();
        CardBook.Perk p = book.perk(hero, id);
        if (p == null || perks.contains(id) || points < p.cost()) return false;
        if (p.row() == 0) return true;
        for (CardBook.Perk q : book.perks(hero)) if (q.row() == p.row() - 1 && perks.contains(q.id())) return true;
        return false;
    }

    /** Take perk {@code id}: its card onto her discard pile, or its helps on her for good. Whether she could. */
    public boolean take(String id, Player player) {
        if (!canTake(id)) return false;
        CardBook.Perk p = CardBook.get().perk(hero, id);
        points -= p.cost();
        perks.add(id);
        if (p.card() != null) deck.gain(p.card());
        if (player != null) for (Card.Effect e : p.passive()) lasting(player, "Perk: " + p.name(), e, true);
        return true;
    }

    /** The helps for good of every perk she has taken, put on her again (her cards' helps were cleared). */
    public void restorePerks(Player player) {
        CardBook book = CardBook.get();
        for (String id : perks) {
            CardBook.Perk p = book.perk(hero, id);
            if (p != null) for (Card.Effect e : p.passive()) lasting(player, "Perk: " + p.name(), e, true);
        }
    }

    /** A card got (a shop's, a find): onto her discard pile. */
    public void gainCard(String id) {
        deck.gain(id);
    }

    /** What playing a card did, for the toast. */
    public record Played(Card card, List<String> said) {}

    /**
     * Play the card at {@code index} in her hand in {@code world}: each of
     * its effects - a help for a while (or so many hits), or something at
     * once - and the card onto the discard pile. Null when there is none.
     */
    public Played play(int index, World world) {
        if (index < 0 || index >= deck.hand().size()) return null;
        Card card = CardBook.get().card(deck.hand().get(index));
        deck.play(index);
        if (card == null) return null;
        List<String> said = new ArrayList<>();
        Player p = world.player();
        for (Card.Effect e : card.effects()) {
            if (e.buff() != null) {
                lasting(p, card.name(), e, false);
                continue;
            }
            Card.Instant at = e.instant();
            if (at == null) continue;
            switch (at) {
                case HEAL -> {
                    p.heal(e.value());
                    said.add("+" + Math.round(e.value()) + " health");
                }
                case MANA -> {
                    world.combat().setMana(world.combat().mana() + e.value());
                    said.add("+" + Math.round(e.value()) + " mana");
                }
                case STAMINA -> {
                    p.setStamina(p.stamina() + e.value());
                    said.add("+" + Math.round(e.value()) + " stamina");
                }
                case MANA_TO_HEALTH -> {
                    double m = world.combat().mana();
                    world.combat().setMana(0);
                    p.heal(m);
                    said.add(Math.round(m) + " mana into health");
                }
                case HEALTH_TO_MANA -> {
                    double h = Math.min(e.value(), Math.max(0, p.health() - 1));
                    p.setHealth(p.health() - h);
                    world.combat().setMana(world.combat().mana() + 2 * h);
                    said.add(Math.round(h) + " health into " + Math.round(2 * h) + " mana");
                }
                case DRAW -> said.add("drew " + deck.drawCards((int) Math.max(1, Math.round(e.value()))));
                case COINS_NOW -> {
                    p.addCoins((int) Math.round(e.value()));
                    said.add("+" + Math.round(e.value()) + " coins");
                }
                case XP_NOW -> {
                    gain(e.value(), null);
                    said.add("+" + Math.round(e.value()) + " experience");
                }
                case BREATH_NOW -> {
                    p.refillBreath();
                    said.add("breath back");
                }
            }
        }
        return new Played(card, said);
    }

    private static void lasting(Player p, String source, Card.Effect e, boolean forGood) {
        Buffs.Kind k = e.buff();
        if (k == null) return;
        double seconds = forGood ? 0 : e.seconds();
        int charges = forGood ? 0 : e.charges();
        if (!forGood && seconds <= 0 && charges <= 0) seconds = 60;     // a help needs a time or some hits
        double value = e.value() == 0 && (k == Buffs.Kind.SNEAK || k == Buffs.Kind.AMMO_FREE) ? 1 : e.value();
        p.buffs().add(new Buffs.Active(source, k, value, seconds, charges, e.only()));
    }
}
