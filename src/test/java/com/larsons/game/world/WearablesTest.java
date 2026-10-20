package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Wearables as items: the table, writing them down, the chests' loot, and losing them. */
class WearablesTest {

    private static ItemDef w(String id) {
        ItemDef d = Wearables.byId(id);
        assertNotNull(d, id);
        return d;
    }

    @Test
    void everyWearableIsAnItemWrittenAsOneWordWithItsOwnRarity() {
        Set<String> ids = new HashSet<>();
        for (ItemDef d : ItemDef.ALL) ids.add(d.id());
        for (ItemDef d : Wearables.ALL) {
            assertTrue(ids.add(d.id()), "one of each: " + d.id());
            assertTrue(d.isWearable() && !d.held() && !d.inHand(), d.id());
            assertSame(d, ItemDef.byId(d.id()));
            Item item = Item.of(d);
            assertEquals(d.wearable().rarity(), item.rarity(), d.id());
            assertEquals(d.id(), item.key(), "its own rarity goes without saying");
            assertEquals(item, Item.parse(item.key()));
            assertNotNull(d.fallbackIcon().get(), d.id());
        }
        assertTrue(ItemDef.ALL.stream().noneMatch(ItemDef::isWearable), "ItemDef.ALL is the weapons");
        assertEquals(61, Wearables.ALL.size());
        assertEquals(Rarity.RARE, Item.parse("silver_necklace").rarity());
        assertEquals("Silver necklace", Item.parse("silver_necklace").name());
        assertEquals("Pink dye", Item.parse("dye_pink").name());
        assertEquals("Honey blonde dye", Item.parse("dye_honey_blonde").name());
        assertEquals("Long waves hairstyle", Item.parse("hair_long_waves").name());
        assertEquals("Arched eyebrows", Item.parse("eyebrows_arched").name());
        assertEquals("Noir makeup", Item.parse("makeup_noir").name());
        Item epicBelt = Item.parse("belt epic venom");
        assertEquals("belt:epic:venom", epicBelt.key(), "another rarity is written down");
        assertEquals(epicBelt, Item.parse(epicBelt.key()));
        assertEquals("sword", Item.of(ItemDef.SWORD).key(), "the weapons are as they were");
    }

    @Test
    void theTableIsLarsonsAndThePicksHeDidNotMake() {
        for (String id : List.of("underwear", "bra", "shoes", "pants", "baggy_shirt", "straw_sun_hat", "beanie")) {
            assertEquals(Rarity.COMMON, w(id).wearable().rarity(), id);
        }
        for (String id : List.of("belt", "gloves", "sheath", "laced_shirt", "wool_mantle", "chainmail_shirt",
                "chainmail_pants")) {
            assertEquals(Rarity.UNCOMMON, w(id).wearable().rarity(), id);
        }
        for (String id : List.of("laced_dress", "silver_wrist_band", "silver_necklace", "silver_hoops", "dye_mint",
                "makeup_plum", "lipstick", "hair_braids", "eyebrows_thin", "iron_chestplate", "iron_leg_plating",
                "iron_gauntlets", "iron_helmet")) {
            assertEquals(Rarity.RARE, w(id).wearable().rarity(), id);
        }
        for (String id : List.of("gold_wrist_band", "gold_necklace", "gold_hoops", "steel_chestplate",
                "steel_leg_plating", "steel_gauntlets", "steel_helmet")) {
            assertEquals(Rarity.EPIC, w(id).wearable().rarity(), id);
        }
        assertEquals(14, Wearables.ALL.stream().filter(d -> d.wearable().dye() != null).count());
        assertEquals(11, Wearables.ALL.stream().filter(d -> d.wearable().slots().contains(Slot.HAIR)).count());
        // where each piece of armour goes, and in which folder
        assertTrue(w("chainmail_shirt").wearable().puts(Slot.CHAINMAIL, "chainmail_shirt"));
        assertTrue(w("chainmail_pants").wearable().puts(Slot.CHAINMAIL_LEGS, "chainmail_pants"));
        assertTrue(w("steel_chestplate").wearable().puts(Slot.CHESTPLATE, "steel_chestplate"));
        assertTrue(w("iron_leg_plating").wearable().puts(Slot.LEG_PLATING, "iron_leg_plating"));
        assertTrue(w("iron_gauntlets").wearable().puts(Slot.GLOVES, "iron_gauntlets"));
        assertTrue(w("steel_helmet").wearable().puts(Slot.HAT, "steel_helmet"));
        // the metals: one folder, two colours
        assertEquals(w("silver_wrist_band"), Wearables.wornAs(Slot.WRISTWEAR_LEFT, "gold_bangle", "silver"));
        assertEquals(w("gold_wrist_band"), Wearables.wornAs(Slot.WRISTWEAR, "gold_bangle", "gold"));
        assertEquals(w("gold_necklace"), Wearables.wornAs(Slot.NECKLACE, "gold_pendant", "gold"));
        assertEquals(w("silver_hoops"), Wearables.wornAs(Slot.EARRINGS, "gold_hoops", "silver"));
        assertNull(Wearables.wornAs(Slot.NECKLACE, "gold_pendant", null), "a pendant in no metal is neither");
        assertEquals(List.of(Wearables.UNDERWEAR, Wearables.BRA, w("hair_long_waves"), w("eyebrows_arched")),
                Wearables.OWNED_BY_DEFAULT, "the underwear, the bra and the starter look");
    }

    @Test
    void theChestsDropTheWearablesAndTheArmourButNotWhatIsOwnedByDefault() {
        Loot loot = new World(new Wardrobe()).loot();
        assertTrue(loot.pool(Rarity.COMMON).contains(Item.of(w("straw_sun_hat"))));
        assertTrue(loot.pool(Rarity.UNCOMMON).contains(Item.of(w("chainmail_shirt"))));
        assertTrue(loot.pool(Rarity.RARE).contains(Item.of(w("iron_gauntlets"))));
        assertTrue(loot.pool(Rarity.RARE).contains(Item.of(w("dye_royal_blue"))));
        assertTrue(loot.pool(Rarity.EPIC).contains(Item.of(w("steel_helmet"))));
        assertTrue(loot.pool(Rarity.EPIC).contains(Item.of(w("gold_hoops"))));
        for (Rarity r : Rarity.values()) {
            for (Item i : loot.pool(r)) {
                assertFalse(Wearables.OWNED_BY_DEFAULT.contains(i.def()), "never dropped: " + i);
            }
        }
        for (Rarity r : Rarity.values()) {
            for (List<Item> drop : loot.drops(r)) {
                for (Item i : drop) assertEquals(r, i.rarity(), "a drop is of its rarity: " + i);
            }
        }
        // weapons as before: a Basic one of all five, and five Mythic
        assertEquals(5, new Loot().pool(Rarity.COMMON).size());
        assertEquals(5, loot.pool(Rarity.COMMON).size() - 5, "and the five Common wearables");
        assertEquals(5, loot.pool(Rarity.MYTHIC).size());
        // each family is one drop
        List<List<Item>> rare = loot.drops(Rarity.RARE);
        assertEquals(3 + 4 + 1 + 1 + 1 + 1 + 1 + 4, rare.size(),
                "three Rare weapons; the dress and the silver; the dyes, the makeup, the lipstick, the hair "
                        + "styles and the eyebrows one each; the iron armour");
        assertTrue(rare.stream().anyMatch(d -> d.size() == 14), "the dyes");
        assertTrue(rare.stream().anyMatch(d -> d.size() == 10), "the hair styles, less the starter's");
        assertTrue(rare.stream().anyMatch(d -> d.size() == 2 && d.get(0).id().startsWith("eyebrows_")));
        assertTrue(rare.stream().anyMatch(d -> d.size() == 3 && d.get(0).id().startsWith("makeup_")));
    }

    @Test
    void aFamilyComesUpAsOftenAsOneDropAndThenAnyMemberAlike() {
        Loot loot = Wearables.addTo(new Loot());
        int drops = loot.drops(Rarity.RARE).size();
        Random random = new Random(7);
        int n = 170_000, dyes = 0, weapons = 0;
        int[] perDye = new int[Wearables.DYE_COLOURS.size()];
        for (int i = 0; i < n; i++) {
            Item item = loot.pick(random, Rarity.RARE);
            String dye = item.def().isWearable() ? item.def().wearable().dye() : null;
            if (dye != null) {
                dyes++;
                perDye[Wearables.DYE_COLOURS.indexOf(dye)]++;
            }
            if (!item.def().isWearable()) weapons++;
        }
        assertEquals(1.0 / drops, dyes / (double) n, 0.006, "the fourteen dyes together are one drop");
        assertEquals(3.0 / drops, weapons / (double) n, 0.008, "and the weapons are not drowned out");
        for (int c : perDye) assertEquals(dyes / 14.0, c, dyes / 14.0 * 0.15, "every dye alike");
    }

    @Test
    void aChestStillGivesThreeToSevenOneAtLeastRare() {
        Loot loot = Wearables.addTo(new Loot());
        Random random = new Random(8);
        boolean wearable = false;
        for (int i = 0; i < 20_000; i++) {
            List<Item> lot = loot.roll(random);
            assertTrue(lot.size() >= Loot.MIN && lot.size() <= Loot.MAX);
            assertTrue(lot.stream().anyMatch(it -> it.rarity().atLeast(Loot.GUARANTEED)), lot.toString());
            wearable |= lot.stream().anyMatch(it -> it.def().isWearable());
        }
        assertTrue(wearable);
    }

    /** The wardrobe's sword in the right hand and the shield on the left arm. */
    private static Wardrobe swordAndShield() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        w.set(Slot.CARRY_LEFT, "round_shield");
        return w;
    }

    @Test
    void pickingUpAWearableChangesNeitherHerStanceNorHerHands() {
        World world = new World(swordAndShield());
        Wardrobe before = world.player().wardrobe().copy();
        world.spawn(w("silver_necklace"), new Vec3(0.3, 0, 0));
        assertTrue(world.pickUp(world.reachable()));
        assertEquals(Stance.SWORD, world.player().stance());
        assertEquals(before.toJson(), world.player().wardrobe().toJson(), "nothing put on, nothing in hand");
        assertEquals(Item.of(w("silver_necklace")), world.player().inventory().get(0));
        // selected, it is no weapon: the sword stance
        assertEquals(Item.of(w("silver_necklace")), world.select(0));
        assertEquals(Stance.SWORD, world.player().stance());

        // with the axe in hand, a wearable picked up leaves it there
        world.select(1);
        world.spawn(ItemDef.BATTLE_AXE, new Vec3(0.3, 0, 0));
        assertTrue(world.pickUp(world.reachable()));
        assertEquals(Stance.AXE, world.player().stance());
        world.spawn(Item.of(w("iron_helmet")), new Vec3(0.3, 0, 0));
        assertTrue(world.pickUp(world.reachable()));
        assertEquals(Stance.AXE, world.player().stance(), "still the axe");
        assertEquals(1, world.player().inventory().selected());
        assertEquals("sword", world.player().wardrobe().get(Slot.CARRY_RIGHT));
        assertNull(world.player().wardrobe().get(Slot.HAT));
        assertEquals(world.player().inventory().get(1), world.wielded());
    }

    @Test
    void droppingTheLastOneTakesItOff() {
        World world = new World(new Wardrobe());
        Player p = world.player();
        Wardrobe wr = p.wardrobe();
        Item silver = Item.of(w("silver_necklace"));
        p.inventory().add(silver);
        p.inventory().add(silver);
        p.inventory().add(Item.of(w("gold_necklace")));
        wr.set(Slot.NECKLACE, "gold_pendant");
        wr.setColour(Slot.NECKLACE, "silver");
        world.dropSlot(0);
        assertEquals("gold_pendant", wr.get(Slot.NECKLACE), "she has another");
        world.dropSlot(1);
        assertNull(wr.get(Slot.NECKLACE), "the gold one is not silver: off it comes");
        assertNull(wr.colour(Slot.NECKLACE));

        // the underwear, the bra and the starter look are always hers; the free layers are no items
        wr.set(Slot.UNDERWEAR, "cotton_briefs");
        wr.set(Slot.HAIR, "long_waves");
        wr.set(Slot.EARS, "elven");
        p.inventory().add(Item.of(Wearables.UNDERWEAR));
        world.dropSlot(p.inventory().indexOf(Wearables.UNDERWEAR));
        assertEquals("cotton_briefs", wr.get(Slot.UNDERWEAR));
        p.inventory().add(Item.of(w("hair_long_waves")));
        world.dropSlot(p.inventory().indexOf(w("hair_long_waves")));
        assertEquals("long_waves", wr.get(Slot.HAIR));
        // something she wears and never owned stays on when something else goes
        wr.set(Slot.HAIR, "bob");
        p.inventory().add(Item.of(w("belt")));
        world.dropSlot(p.inventory().indexOf(w("belt")));
        assertEquals("bob", wr.get(Slot.HAIR));
        assertEquals("elven", wr.get(Slot.EARS));

        // spilled out of a smaller inventory, it is lost too
        World small = new World(new Wardrobe(), 10);
        Inventory inv = small.player().inventory();
        Item helmet = Item.of(w("iron_helmet"));
        for (int i = 0; i < 9; i++) inv.set(i, silver);
        inv.set(9, helmet);
        small.player().wardrobe().set(Slot.HAT, "iron_helmet");
        List<Item> spilled = inv.resize(5);
        assertTrue(spilled.contains(helmet));
        for (Item s : spilled) small.dropItem(s);
        assertNull(small.player().wardrobe().get(Slot.HAT));
    }

    @Test
    void losingTheLastDyeOfAColourTakesThatColourOff() {
        World world = new World(new Wardrobe());
        Player p = world.player();
        Wardrobe wr = p.wardrobe();
        p.inventory().add(Item.of(w("dye_pink")));
        p.inventory().add(Item.of(Wearables.LIPSTICK));
        for (Slot s : List.of(Slot.SHIRT, Slot.HAIR, Slot.EYES, Slot.MOUTH, Slot.BODY)) {
            wr.set(s, "something");
            wr.setColour(s, "pink");
        }
        wr.setColour(Slot.PANTS, "black");
        world.dropSlot(0);
        assertNull(wr.colour(Slot.SHIRT));
        assertNull(wr.colour(Slot.HAIR));
        assertEquals("pink", wr.colour(Slot.EYES), "the eyes' colour is free");
        assertEquals("pink", wr.colour(Slot.BODY), "and the skin tone");
        assertEquals("pink", wr.colour(Slot.MOUTH), "the lips are the lipstick's");
        assertEquals("black", wr.colour(Slot.PANTS), "another colour stays");
        assertEquals("something", wr.get(Slot.SHIRT), "what was dyed stays on");
        world.dropSlot(1);
        assertNull(wr.colour(Slot.MOUTH));
        assertEquals("something", wr.get(Slot.MOUTH));
    }
}
