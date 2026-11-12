package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.save.SaveGame;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.story.Quest;
import com.larsons.game.story.Quests;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Coins in her purse, ammunition in her pouch: never a slot, always taken, taken as she walks over them. */
class PouchTest {

    @Test
    void ammunitionGoesIntoThePouchHoweverFullTheSlotsAre() {
        Inventory inv = new Inventory(5);
        for (int i = 0; i < 5; i++) inv.add(Item.of(ItemDef.SWORD));
        assertTrue(inv.isFull());
        assertEquals(Inventory.POUCH, inv.add(Item.of(Supplies.ARROW_BUNDLE)));
        assertEquals(0, inv.put(Item.of(Supplies.ARROW_BUNDLE), 4, true), "all of it, none left over");
        assertEquals(5, inv.pouched(Supplies.ARROW_BUNDLE));
        assertEquals(5, inv.total(Item.of(Supplies.ARROW_BUNDLE)));
        assertTrue(inv.hasRoomFor(Item.of(Supplies.WHETSTONE)));
        assertEquals(5, Recipes.count(inv, Supplies.ARROW_BUNDLE.id()), "crafting counts the pouch");
        assertTrue(Recipes.take(inv, Supplies.ARROW_BUNDLE.id(), 2));
        assertEquals(3, inv.pouched(Supplies.ARROW_BUNDLE));
        // a bow shoots from the pouch
        Ammo ammo = new Ammo();
        ammo.setEnabled(true);
        Item bow = Item.of(ItemDef.byId("longbow"));
        assertEquals(Ammo.Use.RELOADED, ammo.use(bow, inv, false));
        assertEquals(2, inv.pouched(Supplies.ARROW_BUNDLE));
        assertEquals(2, ammo.carried(bow, inv));
    }

    @Test
    void ammunitionInASlotFromAnOldSaveIsMovedIntoThePouchAndThePouchIsSaved() {
        Inventory inv = new Inventory(10);
        inv.set(7, Item.of(Supplies.WHETSTONE), 6);
        assertTrue(inv.pouchAmmo());
        assertNull(inv.get(7));
        assertEquals(6, inv.pouched(Supplies.WHETSTONE));

        Player p = new Player(new Wardrobe(), 10);
        p.inventory().put(Item.of(Supplies.BOLT_BUNDLE), 9, true);
        SaveGame s = new SaveGame();
        s.take(p);
        SaveGame back = SaveGame.parse(s.json());
        Player q = new Player(new Wardrobe(), 10);
        back.give(q);
        assertEquals(9, q.inventory().pouched(Supplies.BOLT_BUNDLE));
        assertEquals(0, q.inventory().count(), "no slot taken");
    }

    @Test
    void coinsAndAmmunitionAreTakenAsSheWalksOverThemButNothingElse() {
        World w = new World(new Wardrobe(), 5);
        for (int i = 0; i < 5; i++) w.player().inventory().add(Item.of(ItemDef.SWORD));
        Vec3 her = w.player().ground();
        w.spawn(Item.of(Supplies.COINS), her.add(1, 0, 0), 12);
        w.spawn(Item.of(Supplies.ARROW_BUNDLE), her.add(0, 0, 1.5), 3);
        w.spawn(Item.of(ItemDef.SWORD), her.add(-1, 0, 0), 1);
        w.spawn(Item.of(Supplies.COINS), her.add(9, 0, 0), 5);
        var got = w.autoPickUp(2.5);
        assertEquals(2, got.size());
        assertEquals(12, w.player().coins());
        assertEquals(3, w.player().inventory().pouched(Supplies.ARROW_BUNDLE));
        assertEquals(2, w.items().size(), "the sword (no room, and not walked up for) and the far coins lie still");
    }

    @Test
    void sheFollowsUpToFourMissionsAndPicksWhich() {
        Map<String, Object> store = new LinkedHashMap<>();
        List<Quest> qs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("title", "Task " + i);
            props.put("kind", "talk");
            props.put("target", "Nobody");
            qs.add(Quest.of("here", i, Vec3.ZERO, props));
        }
        Quests quests = new Quests(store, qs, "Here");
        for (Quest q : qs) quests.accept(q);
        assertEquals(Quests.MOST_TRACKED, quests.tracked().size(), "the first four followed as they are taken");
        assertFalse(quests.tracked(qs.get(5).id()));
        assertFalse(quests.setTracked(qs.get(5).id(), true), "no fifth");
        quests.setTracked(qs.get(0).id(), false);
        assertTrue(quests.setTracked(qs.get(5).id(), true));
        assertTrue(quests.tracked().contains(qs.get(5)));
        assertFalse(quests.tracked().contains(qs.get(0)));
        assertFalse(quests.journal().stream().filter(e -> e.id().equals(qs.get(0).id())).findFirst().orElseThrow()
                .tracked());
    }
}
