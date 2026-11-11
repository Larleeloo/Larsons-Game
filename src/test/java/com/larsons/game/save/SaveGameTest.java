package com.larsons.game.save;

import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Goods;
import com.larsons.game.world.Inventory;
import com.larsons.game.world.Item;
import com.larsons.game.world.Player;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** A playthrough: who she is, what she carries, where she stands on the map - written, read back, kept in a slot. */
class SaveGameTest {

    @Test
    void aNewPlaythroughIsATravellerAtTheStartOfTheMap() {
        SaveGame s = new SaveGame();
        assertEquals("Traveller", s.name());
        assertEquals("human", s.people());
        assertEquals(1, s.size());
        assertEquals("", s.heroClass());
        assertEquals(1, s.level());
        assertEquals(Inventory.DEFAULT_SLOTS, s.inventorySize());
        assertEquals("", s.map());
        assertEquals("", s.node());
        assertTrue(s.cleared().isEmpty());
        // what she is told to be is kept to what she can be
        s.setName("   ");
        assertEquals("Traveller", s.name());
        s.setName("  Mara ");
        assertEquals("Mara", s.name());
        s.setPeople("bortzal");
        assertEquals("human", s.people(), "not a people she can be of");
        for (String p : SaveGame.PEOPLES) {
            s.setPeople(p);
            assertEquals(p, s.people());
        }
        s.setSize(5);
        assertEquals(SaveGame.TALLEST, s.size());
        s.setSize(0.1);
        assertEquals(SaveGame.SMALLEST, s.size());
        s.setCoins(-4);
        assertEquals(0, s.coins());
        s.setInventorySize(1);
        assertEquals(Inventory.HOTBAR, s.inventorySize());
        s.played(90 * 60);
        s.played(-5);
        assertEquals("1 h 30 min", s.playedFor());
    }

    @Test
    void everythingAboutHerIsWrittenAndReadBack(@TempDir Path dir) throws IOException {
        SaveGame s = new SaveGame();
        s.setName("Mara");
        s.setPeople("khorigale");
        s.setSize(0.9);
        s.setHeroClass("knight");
        s.setCoins(321);
        s.setInventorySize(35);
        s.setPlace("brass_vale", "brasswick");
        s.cleared().add("oakhollow");
        s.cleared().add("millers");
        s.flags().put("door:12", "open");
        Map<String, Object> quests = new LinkedHashMap<>();
        quests.put("blue_bear", "ready");
        s.setQuests(quests);
        Wardrobe w = new Wardrobe();
        w.set(Slot.HAIR, "braids");
        w.setColour(Slot.HAIR, "emerald");
        s.setWardrobe(w);
        s.played(125);
        Path file = dir.resolve("deep").resolve("slot1.json");
        s.save(file);
        assertFalse(Files.exists(file.resolveSibling("slot1.json.part")), "the half-written file is gone");

        SaveGame back = SaveGame.load(file);
        assertEquals(s.json(), back.json());
        assertEquals("Mara", back.name());
        assertEquals("khorigale", back.people());
        assertEquals(0.9, back.size(), 1e-9);
        assertEquals("knight", back.heroClass());
        assertEquals(321, back.coins());
        assertEquals(35, back.inventorySize());
        assertEquals("brass_vale", back.map());
        assertEquals("brasswick", back.node());
        assertEquals(List.of("oakhollow", "millers"), List.copyOf(back.cleared()));
        assertEquals("open", back.flags().get("door:12"));
        assertEquals("ready", back.quests().get("blue_bear"));
        assertEquals("braids", back.wardrobe().get(Slot.HAIR));
        assertEquals("emerald", back.wardrobe().colour(Slot.HAIR));
        assertEquals(125, back.seconds(), 1e-9);
        assertEquals(s.created(), back.created());
        // a file with nothing in it is still someone
        assertEquals("Traveller", SaveGame.parse(new LinkedHashMap<>()).name());
        assertEquals("human", SaveGame.parse(null).people());
    }

    @Test
    void whatSheCarriesHerPurseAndHerClassComeBackAsTheyWere() {
        World world = new World(new Wardrobe(), 30);
        Player p = world.player();
        Inventory inv = p.inventory();
        Item potion = Goods.item(Goods.HEALTH_POTION), key = Goods.item(Goods.BRASS_KEY);
        Item sword = Item.parse("sword");
        assertNotNull(sword);
        inv.set(0, sword);
        inv.set(7, potion, 4);
        inv.set(29, key);
        inv.select(0);
        p.setCoins(77);
        p.setHealth(40);
        p.hero().choose("knight", p);
        assertEquals("knight", p.hero().heroClass());

        SaveGame s = new SaveGame();
        s.take(p);
        assertEquals(30, s.inventorySize());
        assertEquals(3, s.items().size());
        assertEquals(new SaveGame.Stack(7, potion.key(), 4), s.items().get(1));
        assertEquals("knight", s.heroClass());
        SaveGame read = SaveGame.parse(s.json());

        World next = new World(new Wardrobe(), read.inventorySize());
        Player q = next.player();
        q.inventory().set(3, potion, 9);                       // whatever she had there goes
        read.give(q);
        assertEquals(sword, q.inventory().get(0));
        assertNull(q.inventory().get(3));
        assertEquals(potion, q.inventory().get(7));
        assertEquals(4, q.inventory().amount(7));
        assertEquals(key, q.inventory().get(29));
        assertEquals(77, q.coins());
        assertEquals(40, q.health(), 1e-9);
        assertEquals("knight", q.hero().heroClass());
        assertEquals(p.hero().deck().hand(), q.hero().deck().hand());
        assertEquals(p.hero().deck().drawPile(), q.hero().deck().drawPile());

        // an item the game no longer has is left out; one whose slot is gone finds another
        SaveGame odd = new SaveGame();
        odd.items().add(new SaveGame.Stack(2, "a_thing_that_was", 1));
        odd.items().add(new SaveGame.Stack(400, potion.key(), 2));
        World third = new World(new Wardrobe());
        odd.give(third.player());
        assertEquals(1, third.player().inventory().count());
        assertEquals(2, third.player().inventory().total(potion));
    }

    @Test
    void thereAreThreeSlotsAndTheOneSavedLastIsTheOneToContinue(@TempDir Path dir) throws Exception {
        try {
            Saves.useFolder(dir);
            Saves.stop();
            assertEquals(3, Saves.SLOTS);
            assertEquals(0, Saves.free());
            assertEquals(-1, Saves.latest());
            assertNull(Saves.peek(0));
            assertFalse(Saves.write(), "nothing is being played");

            SaveGame first = new SaveGame();
            first.setName("First");
            Saves.play(first, 0);
            assertSame(first, Saves.active());
            assertTrue(Saves.write());
            assertTrue(Saves.exists(0));
            assertEquals(1, Saves.free());
            assertEquals(0, Saves.latest());

            Thread.sleep(15);                                   // (a save's time is to the clock's tick)
            SaveGame second = new SaveGame();
            second.setName("Second");
            Saves.play(second, 2);
            assertTrue(Saves.write());
            assertEquals(2, Saves.latest());
            assertEquals("Second", Saves.peek(2).name());
            assertEquals("First", Saves.peek(0).name());
            assertEquals(1, Saves.free());

            Thread.sleep(15);
            Saves.play(Saves.peek(0), 0);
            Saves.active().setCoins(5);
            assertTrue(Saves.write());
            assertEquals(0, Saves.latest(), "played again, it is the latest");
            assertEquals(5, Saves.peek(0).coins());

            // a slot that cannot be read is no playthrough, and does not stop the others
            Files.writeString(Saves.file(1), "{ not a save");
            assertNull(Saves.peek(1));
            assertEquals(0, Saves.latest());
            assertEquals(-1, Saves.free());

            Saves.delete(0);
            assertFalse(Saves.exists(0));
            assertNull(Saves.active(), "the one being played was deleted");
            assertEquals(-1, Saves.activeSlot());
            assertEquals(2, Saves.latest());
            Saves.delete(0);                                    // (nothing there: nothing happens)
        } finally {
            Saves.stop();
            Saves.useFolder(Path.of("config", "saves"));
        }
    }
}
