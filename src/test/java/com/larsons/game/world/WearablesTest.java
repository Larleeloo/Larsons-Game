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
        assertEquals(List.of(Wearables.UNDERWEAR, Wearables.BRA, w("hair_long_waves"), w("hair_side_part"),
                w("eyebrows_arched")), Wearables.OWNED_BY_DEFAULT,
                "the underwear, the bra and the starter look: a long and a short hair style");
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
        // weapons as before: a Basic one of all eight but the wand, and eight Mythic
        assertEquals(7, new Loot().pool(Rarity.COMMON).size());
        assertEquals(5, loot.pool(Rarity.COMMON).size() - 7, "and the five Common wearables");
        assertEquals(8, loot.pool(Rarity.MYTHIC).size());
        // each family is one drop
        List<List<Item>> rare = loot.drops(Rarity.RARE);
        assertEquals(4 + 4 + 1 + 1 + 1 + 1 + 1 + 4, rare.size(),
                "four Rare weapons (the Magic Staff and Wand, the Crafted Bow and Crossbow); the dress and the silver; the dyes, the makeup, the lipstick, the hair "
                        + "styles and the eyebrows one each; the iron armour");
        assertTrue(rare.stream().anyMatch(d -> d.size() == 14), "the dyes");
        assertTrue(rare.stream().anyMatch(d -> d.size() == 9), "the hair styles, less the starter's two");
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
        assertEquals(4.0 / drops, weapons / (double) n, 0.008, "and the weapons are not drowned out");
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
        assertTrue(world.player().handsEmpty(), "no sword of hers in hand: the wardrobe's is not drawn");
        assertEquals(before.toJson(), world.player().wardrobe().toJson(), "nothing put on, nothing in hand");
        assertEquals(Item.of(w("silver_necklace")), world.player().inventory().get(Inventory.HOTBAR),
                "into the rest of the inventory, out of the hotbar's way");
        // put in the hotbar and selected, it is no weapon: her hands stay empty
        world.player().inventory().swap(Inventory.HOTBAR, 0);
        assertEquals(Item.of(w("silver_necklace")), world.select(0));
        assertTrue(world.player().handsEmpty());
        assertEquals(Stance.BLADE, world.player().stance());
        assertNull(world.wielded());

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
    void aDyeColourIsTheItemsForGoodTheLipsAreTheLipsticks() {
        World world = new World(new Wardrobe());
        Player p = world.player();
        Wardrobe wr = p.wardrobe();
        p.inventory().add(Item.of(w("dye_pink")));
        p.inventory().add(Item.of(Wearables.LIPSTICK));
        for (Slot s : List.of(Slot.SHIRT, Slot.HAIR, Slot.EYES, Slot.MOUTH, Slot.BODY)) {
            wr.set(s, "something");
            wr.setColour(s, "pink");
        }
        world.dropSlot(0);
        for (Slot s : List.of(Slot.SHIRT, Slot.HAIR, Slot.EYES, Slot.BODY)) {
            assertEquals("pink", wr.colour(s), s + ": a dye in the bag is no colour; losing it takes none off");
        }
        assertEquals("pink", wr.colour(Slot.MOUTH), "the lips are the lipstick's");
        world.dropSlot(1);
        assertNull(wr.colour(Slot.MOUTH), "no lipstick, no lip colour");
        assertEquals("something", wr.get(Slot.MOUTH));

        // a lip colour a dye unlocked stays without the lipstick
        p.inventory().add(Item.of(w("dye_cherry_red")));
        p.inventory().add(Item.of(Wearables.LIPSTICK));
        assertTrue(Wearables.useDye(wr, p.inventory(), Slot.MOUTH, "cherry_red"));
        world.dropSlot(p.inventory().indexOf(Wearables.LIPSTICK));
        assertEquals("cherry_red", wr.colour(Slot.MOUTH));
    }

    @Test
    void thePickupsAreAHangerAShieldAndABottleInTheirColours() {
        Item dress = Item.of(w("laced_dress"));
        assertEquals(new Wearables.Pickup(Wearables.HANGER, "cloth", dress.rarity().rgb()), Wearables.pickup(dress));
        assertEquals(Wearables.HANGER, Wearables.pickup(Item.of(w("hair_bob"))).spin(), "a hair style too");
        assertEquals(Wearables.HANGER, Wearables.pickup(Item.of(w("silver_necklace"))).spin());
        for (String armour : List.of("iron_chestplate", "steel_helmet", "iron_helmet")) {
            Item item = Item.of(w(armour));
            assertEquals(new Wearables.Pickup(Wearables.SHIELD, "face", item.rarity().rgb()), Wearables.pickup(item),
                    armour);
        }
        Wearables.Pickup dye = Wearables.pickup(Item.of(w("dye_cherry_red")));
        assertEquals(new Wearables.Pickup(Wearables.BOTTLE, "liquid", 0xca1525), dye, "in the dye's own colour");
        for (String colour : Wearables.DYE_COLOURS) assertTrue(Wearables.DYE_RGB.containsKey(colour), colour);
        assertNull(Wearables.pickup(Item.parse("sword")), "a weapon is its own sprite");
    }

    @Test
    void whatSheWearsAtTheStartIsHersAndComesOffWhenItLeavesTheInventory() {
        Wardrobe wardrobe = new Wardrobe();
        wardrobe.set(Slot.CHAINMAIL, "chainmail_shirt");
        wardrobe.set(Slot.UNDERWEAR, "cotton_briefs");
        World world = new World(wardrobe);
        assertEquals(1, world.ownWhatIsWorn(), "the chainmail; the underwear is hers from the start");
        Inventory inv = world.player().inventory();
        assertEquals(Inventory.HOTBAR, inv.indexOf(w("chainmail_shirt")), "into the bag, out of the hotbar's way");
        assertEquals(0, world.ownWhatIsWorn(), "hers now");
        assertNotNull(world.dropSlot(Inventory.HOTBAR));
        assertNull(world.player().wardrobe().get(Slot.CHAINMAIL), "out of the inventory: taken off");
        assertEquals("cotton_briefs", world.player().wardrobe().get(Slot.UNDERWEAR));
    }

    @Test
    void theDebugSwitchOffersEveryWearable() {
        Inventory inv = new Inventory(Inventory.DEFAULT_SLOTS);
        try {
            Wearables.setEverything(true);
            assertTrue(Wearables.owned(inv).containsAll(Wearables.ALL));
        } finally {
            Wearables.setEverything(false);
        }
        assertEquals(Set.copyOf(Wearables.OWNED_BY_DEFAULT), Wearables.owned(inv));
    }
}
