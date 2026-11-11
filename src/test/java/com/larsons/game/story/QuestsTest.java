package com.larsons.game.story;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Inventory;
import com.larsons.game.world.Item;
import com.larsons.game.world.Supplies;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The quests: taken, done in their different ways, handed in for what they pay - and kept by a save. */
class QuestsTest {

    private static Quest quest(String id, Quest.Kind kind, String target, int count, double radius, String reward,
                               String needs) {
        return new Quest(id, "The " + id, kind, target, count, new Vec3(10, 0, 10), radius, "Maren", "Maren",
                "Will you?", "Not yet.", "Thank you.", reward, 25, needs);
    }

    @Test
    void aKillQuestCountsItsQuarryFelledInItsPlace() {
        Quest bears = quest("bears", Quest.Kind.KILL, "bear", 2, 30, "coins x40", "");
        Quests q = new Quests(new LinkedHashMap<>(), List.of(bears), "The wood");
        assertEquals(Quests.State.NONE, q.state(bears));
        assertTrue(q.offered(bears));
        q.felled(new World.Kill("bear", null, new Vec3(12, 0, 12), false));
        assertEquals(0, q.count(bears), "not taken yet: nothing counts");
        q.accept(bears);
        assertFalse(q.offered(bears));
        assertEquals("New quest: The bears", q.drainNews().get(0).text());
        q.felled(new World.Kill("boar", null, new Vec3(12, 0, 12), false));          // not its quarry
        q.felled(new World.Kill("bear", null, new Vec3(80, 0, 80), false));          // not in its place
        assertEquals(0, q.count(bears));
        q.felled(new World.Kill("bear", null, new Vec3(12, 0, 12), false));
        assertEquals(1, q.count(bears));
        assertTrue(q.goal(bears).contains("1/2"), q.goal(bears));
        assertEquals(Quests.State.ACTIVE, q.state(bears));
        assertFalse(q.canHandIn(bears, new Inventory(10)));
        q.felled(new World.Kill("bear", null, new Vec3(9, 0, 9), false));
        assertEquals(Quests.State.READY, q.state(bears));
        assertEquals(List.of(bears), q.tracked());
        assertTrue(q.canHandIn(bears, new Inventory(10)));
        List<Map.Entry<Item, Integer>> pay = q.handIn(bears, new Inventory(10));
        assertEquals(Quests.State.DONE, q.state(bears));
        assertEquals(1, pay.size());
        assertSame(Supplies.COINS, pay.get(0).getKey().def());
        assertEquals(40, pay.get(0).getValue());
        assertTrue(q.allDone());
        assertTrue(q.tracked().isEmpty());
    }

    @Test
    void aNamedFoeIsAQuarryByItsNameAndTheRisenByTheirKind() {
        Quest thrakk = quest("warlord", Quest.Kind.KILL, "Warlord Thrakk", 1, 0, "", "");
        Quest risen = quest("risen", Quest.Kind.KILL, "risen", 2, 0, "", "");
        Quests q = new Quests(new LinkedHashMap<>(), List.of(thrakk, risen), "The tower");
        q.accept(thrakk);
        q.accept(risen);
        q.felled(new World.Kill("bortzal", "Grukk the Wide", Vec3.ZERO, false));
        assertEquals(Quests.State.ACTIVE, q.state(thrakk));
        q.felled(new World.Kill("bortzal", "Warlord Thrakk", Vec3.ZERO, false));
        assertEquals(Quests.State.READY, q.state(thrakk));
        q.felled(new World.Kill("risen_walker", null, Vec3.ZERO, true));
        q.felled(new World.Kill("risen_brute", null, Vec3.ZERO, true));
        assertEquals(Quests.State.READY, q.state(risen), "any of the Risen is one of them");
    }

    @Test
    void talkingFetchingReachingAndClearingAreQuestsToo() {
        Quest talk = quest("talk", Quest.Kind.TALK, "Garrick", 1, 0, "", "");
        Quest fetch = quest("fetch", Quest.Kind.FETCH, "health potion", 2, 0, "mana potion x1", "");
        Quest reach = quest("reach", Quest.Kind.REACH, "", 1, 6, "", "");
        Quest clear = quest("clear", Quest.Kind.CLEAR, "", 1, 20, "", "");
        Quests q = new Quests(new LinkedHashMap<>(), List.of(talk, fetch, reach, clear), "Here");
        for (Quest x : q.all()) q.accept(x);

        q.talked("Maren");
        assertEquals(Quests.State.ACTIVE, q.state(talk));
        q.talked(" garrick ");
        assertEquals(Quests.State.READY, q.state(talk));

        Inventory inv = new Inventory(12);
        inv.add(Item.parse("health_potion"));
        assertFalse(q.canHandIn(fetch, inv), "one of two");
        inv.add(Item.parse("health_potion"));
        assertTrue(q.canHandIn(fetch, inv));
        assertTrue(q.goal(fetch).contains("2 health potion"), q.goal(fetch));
        List<Map.Entry<Item, Integer>> pay = q.handIn(fetch, inv);
        assertEquals(0, com.larsons.game.world.Recipes.count(inv, "health_potion"), "what was fetched goes");
        assertEquals("mana_potion", pay.get(0).getKey().def().id());

        // a place reached, and a place with no foe left in it
        World world = new World(new Wardrobe());
        q.tick(1, world);
        assertEquals(Quests.State.READY, q.state(clear), "nothing hostile is there");
        assertEquals(Quests.State.ACTIVE, q.state(reach), "she is not there yet");
        world.player().setPosition(new Vec3(11, 0, 11));
        q.tick(1, world);
        assertEquals(Quests.State.READY, q.state(reach));
        assertFalse(q.allDone());
    }

    @Test
    void aQuestWaitsForTheOneItNeedsAndASaveRemembersQuestsOfOtherPlaces() {
        Map<String, Object> saved = new LinkedHashMap<>();
        Quest first = quest("first", Quest.Kind.TALK, "Garrick", 1, 0, "", "");
        Quest second = quest("second", Quest.Kind.TALK, "Tilda", 1, 0, "", "first");
        Quests village = new Quests(saved, List.of(first, second), "Oakhollow");
        assertTrue(village.offered(first));
        assertFalse(village.offered(second), "it needs the first handed in");
        village.accept(first);
        village.talked("Garrick");
        village.handIn(first, new Inventory(4));
        assertTrue(village.offered(second));
        village.accept(second);

        // another level, the same save: its own quest, one it shares by id, and the journal of all of them
        Quest shared = quest("second", Quest.Kind.TALK, "Tilda", 1, 0, "", "");
        Quest own = quest("third", Quest.Kind.KILL, "", 3, 0, "", "");
        Quests town = new Quests(saved, List.of(shared, own), "Brasswick");
        assertEquals(Quests.State.ACTIVE, town.state(shared), "taken in the village, under way here");
        town.talked("Tilda");
        assertEquals(Quests.State.READY, village.state(second), "and done here is done there");
        town.accept(own);
        List<Quests.Entry> journal = town.journal();
        assertEquals(3, journal.size());
        assertEquals(List.of("third", "second", "first"), journal.stream().map(Quests.Entry::id).toList(),
                "under way first, then to hand in, then handed in");
        assertEquals("Oakhollow", journal.get(2).place());
        assertEquals("The first", journal.get(2).title());
        assertEquals("Fell foes  0/3", journal.get(0).goal());
    }

    @Test
    void aRewardIsReadFromItsWords() {
        List<Map.Entry<Item, Integer>> r = Quests.rewards("coins x40, health potion x2, iron key, no such thing x3");
        assertEquals(3, r.size());
        assertSame(Supplies.COINS, r.get(0).getKey().def());
        assertEquals(40, r.get(0).getValue());
        assertEquals("health_potion", r.get(1).getKey().def().id());
        assertEquals(2, r.get(1).getValue());
        assertEquals("iron_key", r.get(2).getKey().def().id());
        assertEquals(1, r.get(2).getValue());
        assertTrue(Quests.rewardText("coins x40, health potion x2").startsWith("40 coins, 2 × "));
        assertTrue(Quests.rewards(null).isEmpty());
        assertEquals("", Quests.rewardText(""));
    }
}
