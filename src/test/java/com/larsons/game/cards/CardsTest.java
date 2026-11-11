package com.larsons.game.cards;

import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Buffs;
import com.larsons.game.world.Player;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The cards: the book of 210, the nine classes' decks, drawing, playing, reshuffling, the perk trees. */
class CardsTest {

    private static final List<String> CLASSES = List.of("mage", "bard", "knight", "warrior", "warlock", "druid",
            "rogue", "ranger", "cleric");

    @Test
    void theBookHasTwoHundredAndTenUniquelyNamedCardsAndNineClasses() {
        CardBook book = CardBook.get();
        assertEquals(210, book.cards().size());
        Set<String> names = new HashSet<>();
        for (Card c : book.cards()) {
            assertTrue(names.add(c.name()), "one card called " + c.name());
            assertFalse(c.effects().isEmpty(), c.name() + " does something");
            for (Card.Effect e : c.effects()) {
                assertTrue(e.buff() != null || e.instant() != null, c.name() + ": " + e.kind() + " is a kind");
            }
        }
        assertEquals(CLASSES, book.heroes().stream().map(CardBook.Hero::id).toList());
        for (String h : CLASSES) {
            assertEquals(8, book.startingDeck(h).size(), h + "'s starting deck: 8 cards");
            assertEquals(12, book.perks(h).size(), h + "'s perk tree");
            assertTrue(book.cardsOf(h).size() >= 22);
        }
        // the Mage's deck as Larson made it: 4 Cast, 2 Bind, a Transfigure, a Healing Spell
        List<String> mage = book.startingDeck("mage");
        assertEquals(4, mage.stream().filter("cast"::equals).count());
        assertEquals(2, mage.stream().filter("bind"::equals).count());
        assertTrue(mage.contains("transfigure") && mage.contains("healing_spell"));
    }

    @Test
    void sheDrawsFourThenOneEveryFiveMinutesUpToEight() {
        Deck d = new Deck(new Random(1));
        d.start(CardBook.get().startingDeck("knight"), 4, 8, 300);
        assertEquals(4, d.hand().size());
        assertEquals(4, d.drawPile().size());
        d.tick(299);
        assertEquals(4, d.hand().size());
        d.tick(2);
        assertEquals(5, d.hand().size(), "five minutes on: one more");
        for (int i = 0; i < 10; i++) d.tick(301);
        assertEquals(8, d.hand().size(), "never more than eight - and the deck is all in hand");
        assertTrue(d.drawPile().isEmpty());
        d.play(0);
        d.play(0);
        assertEquals(2, d.discardPile().size(), "played cards are discarded");
        d.gain("tavern_cheer");
        assertEquals(3, d.discardPile().size(), "a new card goes onto the discard pile");
        d.reshuffle();
        assertEquals(4, d.hand().size(), "a fresh hand");
        assertTrue(d.discardPile().isEmpty());
        assertEquals(9, d.owned().size());
    }

    @Test
    void playingTheMagesCardsHelpsHer() {
        World w = new World(new Wardrobe());
        Player p = w.player();
        Hero h = p.hero();
        h.choose("mage", p);
        // find Cast and Healing Spell (the deck is shuffled): play every card in hand that is one of them
        h.deck().drawCards(8);
        int cast = h.deck().hand().indexOf("cast");
        assertTrue(cast >= 0);
        Hero.Played played = h.play(cast, w);
        assertEquals("Cast", played.card().name());
        assertEquals(0.5, p.buffs().times(Buffs.Kind.MANA_COST, Buffs.Only.SPELL), 1e-9, "spells at half the mana");
        assertTrue(h.deck().discardPile().contains("cast"));

        p.setHealth(20);
        w.combat().setMana(50);
        Hero.Played heal = h.play(h.deck().hand().indexOf("healing_spell"), w);
        assertNotNull(heal);
        assertEquals(70, p.health(), 1e-9, "all her mana into health");
        assertEquals(0, w.combat().mana(), 1e-9);
    }

    @Test
    void experienceGivesLevelsAndPerkPointsForHerTree() {
        World w = new World(new Wardrobe());
        Player p = w.player();
        Hero h = p.hero();
        h.choose("knight", p);
        assertEquals(1, h.level());
        assertFalse(h.gain(99, null));
        assertTrue(h.gain(1, null), "100: level 2");
        assertEquals(2, h.level());
        assertEquals(1, h.points());
        List<CardBook.Perk> tree = CardBook.get().perks("knight");
        CardBook.Perk second = tree.stream().filter(k -> k.row() == 1).findFirst().orElseThrow();
        assertFalse(h.canTake(second.id()), "a row-two perk needs one of row one first");
        CardBook.Perk passive = tree.stream().filter(k -> k.row() == 0 && !k.passive().isEmpty()).findFirst()
                .orElseThrow();
        assertTrue(h.take(passive.id(), p));
        assertEquals(0, h.points());
        assertEquals(0.92, p.buffs().times(Buffs.Kind.DEFENSE), 1e-9, "Plate Training, for good");
        h.addPoints(1);
        assertTrue(h.take(second.id(), p));
        assertTrue(h.deck().discardPile().contains(second.card()), "its card onto the discard pile");

        // a new class: the points back, the perks gone
        h.choose("rogue", p);
        assertEquals(2, h.points());
        assertTrue(h.perks().isEmpty());
        assertEquals(1, p.buffs().times(Buffs.Kind.DEFENSE), 1e-9);
    }

    @Test
    void felledDummiesGiveExperienceAndCoins() {
        World w = new World(new Wardrobe());
        Player p = w.player();
        int coins = p.coins();
        w.earn(50);
        assertEquals(50, p.hero().xp());
        p.buffs().add(new Buffs.Active("Test", Buffs.Kind.XP, 2, 60, 0, Buffs.Only.ANY));
        w.earn(25);
        assertEquals(100 % 100, p.hero().xp(), "50 + 25 x2 = 100: level 2, none over");
        assertEquals(2, p.hero().level());
        assertEquals(coins, p.coins());
    }
}
