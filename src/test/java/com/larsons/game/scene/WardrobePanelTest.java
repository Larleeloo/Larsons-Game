package com.larsons.game.scene;

import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.Variants;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Wearables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** What the wardrobe offers: the items the player owns, what she already wears, and the free layers. */
class WardrobePanelTest {

    @TempDir
    Path root;
    SpriteLibrary lib;
    Player player;

    /** The own colours of the shirt below. */
    static final String SHIRT_COLOURS = """
            {"version": 1, "labels": 1, "default": {},
             "channels": {"own": {"labels": [0],
               "options": {"pink": ["#ff5175"], "black": ["#130e0f"], "mint": ["#55c77b"]},
               "swatch": {"pink": "#ff5175", "black": "#130e0f", "mint": "#55c77b"}}}}
            """;

    @BeforeEach
    void setUp() throws Exception {
        // a few folders, as the sprites folder has them (empty: names are all the options read)
        for (String rel : List.of("ears/elven", "ears/round", "mouth/full", "hair/long_waves_px128", "hair/bob_px128",
                "shirt/plaid_flannel", "wristwear/gold_bangle", "carry_right/sword", "carry_right/battle_axe")) {
            Files.createDirectories(root.resolve(rel));
        }
        Files.writeString(root.resolve("shirt/plaid_flannel/variants.json"), SHIRT_COLOURS, StandardCharsets.UTF_8);
        lib = new SpriteLibrary(root, 1L << 30, 1.0);
        player = new Player(new Wardrobe());
    }

    @AfterEach
    void close() {
        if (lib != null) lib.close();
    }

    private List<String> items(Slot slot) {
        return WardrobePanel.options(lib, slot, player).stream().map(WardrobePanel.Option::item).toList();
    }

    private List<String> labels(Slot slot) {
        return WardrobePanel.options(lib, slot, player).stream().map(o -> o.label(slot)).toList();
    }

    private static ItemDef w(String id) {
        return Wearables.byId(id);
    }

    @Test
    void aNewCharacterHasHerUnderwearTheStarterLookAndTheFreeLayersOnly() {
        assertEquals(java.util.Arrays.asList(null, "cotton_briefs"), items(Slot.UNDERWEAR));
        assertEquals(List.of("None", "Underwear"), labels(Slot.UNDERWEAR));
        assertEquals(List.of("None", "Bra"), labels(Slot.BRA));
        assertEquals(List.of("None", "Long waves hairstyle", "Side part hairstyle"), labels(Slot.HAIR),
                "two hair styles to start with, a long and a short");
        assertEquals(List.of("None", "Arched eyebrows"), labels(Slot.EYEBROWS));
        for (Slot s : List.of(Slot.SHIRT, Slot.CHESTPLATE, Slot.WRISTWEAR, Slot.MAKEUP)) {
            assertEquals(java.util.Collections.singletonList(null), items(s), s + ": nothing found yet");
        }
        // the free layers: every folder there is
        assertEquals(java.util.Arrays.asList(null, "elven", "round"), items(Slot.EARS));
        assertEquals(java.util.Arrays.asList(null, "full"), items(Slot.MOUTH));
        // the hands are the hotbar's, not the wardrobe's
        player.inventory().add(Item.of(w("belt")));
        for (Slot s : List.of(Slot.CARRY_RIGHT, Slot.CARRY_LEFT)) {
            assertEquals(java.util.Collections.singletonList(null), items(s), s + ": nothing to choose");
            assertFalse(WardrobePanel.SLOTS.contains(s), s + ": not on the page");
        }
    }

    @Test
    void whatSheOwnsIsOfferedByName() {
        player.inventory().add(Item.of(w("hair_bob")));
        player.inventory().add(Item.of(w("hair_long_waves")));          // a second of the starter's: listed once
        player.inventory().add(Item.of(w("silver_wrist_band")));
        player.inventory().add(Item.of(w("sheath")));
        player.inventory().add(Item.of(w("iron_chestplate")));
        player.inventory().add(Item.of(w("dye_pink")));
        assertEquals(List.of("None", "Long waves hairstyle", "Side part hairstyle", "Bob hairstyle"), labels(Slot.HAIR));
        assertEquals(java.util.Arrays.asList(null, "long_waves", "side_part", "bob"), items(Slot.HAIR));
        assertEquals(List.of("None", "Silver wrist band"), labels(Slot.WRISTWEAR));
        assertEquals(List.of("None", "Silver wrist band"), labels(Slot.WRISTWEAR_LEFT), "either wrist");
        assertEquals(List.of("None", "Sheath (sword left hip)", "Sheath (sword right hip)"), labels(Slot.SHEATH));
        assertEquals(java.util.Arrays.asList(null, "iron_chestplate"), items(Slot.CHESTPLATE));
        assertEquals(java.util.Collections.singletonList(null), items(Slot.NECKLACE), "a dye is worn nowhere");
    }

    @Test
    void choosingAMetalPutsOnItsFolderInItsColour() {
        player.inventory().add(Item.of(w("silver_wrist_band")));
        player.inventory().add(Item.of(w("gold_wrist_band")));
        Wardrobe wr = player.wardrobe();
        List<WardrobePanel.Option> opts = WardrobePanel.options(lib, Slot.WRISTWEAR_LEFT, player);
        assertEquals(List.of("None", "Silver wrist band", "Gold wrist band"),
                opts.stream().map(o -> o.label(Slot.WRISTWEAR_LEFT)).toList());
        WardrobePanel.choose(wr, Slot.WRISTWEAR_LEFT, opts.get(2));
        assertEquals("gold_bangle", wr.get(Slot.WRISTWEAR_LEFT));
        assertEquals("gold", wr.colour(Slot.WRISTWEAR_LEFT));
        assertEquals(2, WardrobePanel.current(opts, Slot.WRISTWEAR_LEFT, wr));
        WardrobePanel.choose(wr, Slot.WRISTWEAR_LEFT, opts.get(1));
        assertEquals("silver", wr.colour(Slot.WRISTWEAR_LEFT));
        assertEquals(1, WardrobePanel.current(opts, Slot.WRISTWEAR_LEFT, wr), "the same folder, told by its colour");
        WardrobePanel.choose(wr, Slot.WRISTWEAR_LEFT, opts.get(0));
        assertNull(wr.get(Slot.WRISTWEAR_LEFT));
        assertNull(wr.colour(Slot.WRISTWEAR_LEFT), "the metal's colour comes off with it");
    }

    @Test
    void anOutfitSavedBeforeKeepsWhatItWearsUntilItIsTakenOff() {
        Wardrobe wr = player.wardrobe();
        wr.set(Slot.HAIR, "bob");
        wr.set(Slot.SHIRT, "plaid_flannel");
        wr.set(Slot.NECKLACE, "gold_pendant");
        wr.setColour(Slot.NECKLACE, "silver");
        List<WardrobePanel.Option> hair = WardrobePanel.options(lib, Slot.HAIR, player);
        assertEquals(List.of("None", "Long waves hairstyle", "Side part hairstyle", "Bob hairstyle"),
                hair.stream().map(o -> o.label(Slot.HAIR)).toList());
        assertEquals(3, WardrobePanel.current(hair, Slot.HAIR, wr));
        assertEquals(List.of("None", "Baggy shirt"), labels(Slot.SHIRT));
        assertEquals(List.of("None", "Silver necklace"), labels(Slot.NECKLACE));
        WardrobePanel.choose(wr, Slot.HAIR, hair.get(0));
        assertEquals(java.util.Arrays.asList(null, "long_waves", "side_part"), items(Slot.HAIR),
                "once off, only if she owns it");
        // a folder no item puts there is listed by its folder
        wr.set(Slot.HAT, "party_hat");
        assertEquals(List.of("None", "Party hat"), labels(Slot.HAT));
    }

    private long pinkDyes() {
        return player.inventory().items().stream().filter(i -> i.def() == w("dye_pink")).count();
    }

    /** The item's own colours that are unlocked (the palette's, which follow them, apart: {@link #palette}). */
    private static List<String> open(List<Wearables.Colour> colours) {
        return colours.stream().filter(c -> !Variants.isCustom(c.option())).filter(Wearables.Colour::unlocked)
                .map(Wearables.Colour::option).toList();
    }

    /** The item's own colours. */
    private static List<String> options(List<Wearables.Colour> colours) {
        return colours.stream().map(Wearables.Colour::option).filter(c -> !Variants.isCustom(c)).toList();
    }

    /** The palette's colours among them: all of them ({@code unlocked} null), or those unlocked or not. */
    private static List<String> palette(List<Wearables.Colour> colours, Boolean unlocked) {
        return colours.stream().filter(c -> Variants.isCustom(c.option()))
                .filter(c -> unlocked == null || c.unlocked() == unlocked).map(Wearables.Colour::option).toList();
    }

    @Test
    void everyColourIsShownAndADyeUnlocksOneForThatItemForGood() {
        Wardrobe wr = player.wardrobe();
        wr.set(Slot.SHIRT, "plaid_flannel");
        List<Wearables.Colour> colours = WardrobePanel.colours(lib, Slot.SHIRT, player);
        assertEquals(List.of("pink", "black", "mint"), options(colours), "in the item's own order");
        assertEquals(List.of(), open(colours), "no dye used yet: all locked");
        Wearables.Colour pink = colours.get(0);
        assertNull(WardrobePanel.pick(wr, player, Slot.SHIRT, pink), "no pink dye: nothing to offer");
        assertNull(wr.colour(Slot.SHIRT));

        player.inventory().add(Item.of(w("dye_pink")));
        player.inventory().add(Item.of(w("dye_pink")));
        assertEquals("pink", WardrobePanel.pick(wr, player, Slot.SHIRT, pink), "offered, not yet used");
        assertNull(wr.colour(Slot.SHIRT));
        assertTrue(Wearables.useDye(wr, player.inventory(), Slot.SHIRT, "pink"));
        assertEquals("pink", wr.colour(Slot.SHIRT));
        assertEquals(1, pinkDyes(), "one dye used up");
        assertFalse(Wearables.useDye(wr, player.inventory(), Slot.SHIRT, "pink"), "already unlocked: no dye wasted");
        assertEquals(1, pinkDyes());
        assertEquals(List.of("pink"), open(WardrobePanel.colours(lib, Slot.SHIRT, player)));

        // its own colours back, then pink again with no dye: it stays unlocked
        wr.setColour(Slot.SHIRT, null);
        player.inventory().remove(Wearables.dyeIn(player.inventory(), "pink"));
        Wearables.Colour unlocked = WardrobePanel.colours(lib, Slot.SHIRT, player).get(0);
        assertTrue(unlocked.unlocked());
        assertNull(WardrobePanel.pick(wr, player, Slot.SHIRT, unlocked));
        assertEquals("pink", wr.colour(Slot.SHIRT));

        // that shirt only: another item's pink is still locked
        assertFalse(wr.unlocked(Slot.HAIR, "long_waves", "pink"));
        assertFalse(Wearables.useDye(wr, player.inventory(), Slot.HAIR, "mint"), "no mint dye");
        wr.clear(Slot.SHIRT);
        assertFalse(Wearables.useDye(wr, player.inventory(), Slot.SHIRT, "pink"), "nothing worn");
    }

    @Test
    void theUnlockedColoursAreSavedWithTheOutfit() {
        Wardrobe wr = player.wardrobe();
        wr.set(Slot.HAIR, "long_waves");
        player.inventory().add(Item.of(w("dye_cherry_red")));
        assertTrue(Wearables.useDye(wr, player.inventory(), Slot.HAIR, "cherry_red"));
        Wardrobe back = Wardrobe.fromJson(wr.toJson());
        assertTrue(back.unlocked(Slot.HAIR, "long_waves", "cherry_red"));
        assertFalse(back.unlocked(Slot.HAIR, "long_waves", "pink"));
        assertEquals("cherry_red", back.colour(Slot.HAIR));
        assertTrue(wr.copy().unlocked(Slot.HAIR, "long_waves", "cherry_red"));
    }

    @Test
    void theSkinAndEyesAreFreeTheLipsTheLipsticksAndAMetalItsOwn() {
        List<String> all = List.of("brown", "pink", "royal_blue");
        Wardrobe wr = new Wardrobe();
        Set<ItemDef> none = Wearables.owned(player.inventory());
        assertEquals(all, open(Wearables.colours(Slot.BODY, "feminine", all, null, none, wr)));
        assertEquals(all, open(Wearables.colours(Slot.EYES, "hazel", all, null, none, wr)));
        assertEquals(all, options(Wearables.colours(Slot.MOUTH, "full", all, null, none, wr)), "all shown");
        assertEquals(List.of(), open(Wearables.colours(Slot.MOUTH, "full", all, null, none, wr)));
        assertEquals(List.of("pink"), open(Wearables.colours(Slot.MOUTH, "full", all, "pink", none, wr)),
                "lips already pink");
        player.inventory().add(Item.of(w("dye_pink")));
        Set<ItemDef> dye = Wearables.owned(player.inventory());
        assertEquals(List.of(), open(Wearables.colours(Slot.SHIRT, "plaid_flannel", all, null, dye, wr)),
                "a dye in the bag unlocks nothing until it is used");
        for (Slot s : List.of(Slot.HAIR, Slot.EYEBROWS)) {
            List<Wearables.Colour> hers = Wearables.colours(s, "bob", all, null, none, wr);
            assertEquals(all, open(hers), s + ": every colour from the start");
            assertEquals(Wearables.PALETTE, palette(hers, true), s + ": and every colour of the palette");
        }
        wr.unlock(Slot.MAKEUP, "eyeliner", "pink");
        assertEquals(List.of("pink"), open(Wearables.colours(Slot.MAKEUP, "eyeliner", all, null, none, wr)),
                "makeup is dyed like the rest");
        player.inventory().add(Item.of(Wearables.LIPSTICK));
        Set<ItemDef> lipstick = Wearables.owned(player.inventory());
        assertEquals(all, open(Wearables.colours(Slot.MOUTH, "full", all, null, lipstick, wr)));
        assertEquals(List.of(), Wearables.colours(Slot.WRISTWEAR, "gold_bangle", List.of("gold", "silver", "pink"),
                "silver", lipstick, wr), "a metal is its own colour");
        assertEquals(all, open(Wearables.colours(Slot.CARRY_LEFT, "round_shield", all, null, none, wr)),
                "the hands as before");
    }

    @Test
    void everyColourOfThePaletteFollowsTheItemsOwnAndTakesADyeOfIt() {
        Wardrobe wr = player.wardrobe();
        wr.set(Slot.SHIRT, "plaid_flannel");
        List<Wearables.Colour> colours = WardrobePanel.colours(lib, Slot.SHIRT, player);
        assertEquals(Wearables.PALETTE, palette(colours, null), "after its own three, the palette's hundred");
        assertEquals(100, Wearables.PALETTE.size());
        assertEquals(List.of(), palette(colours, true), "what is worn takes a dye for each");
        // a colour of the palette no named dye is: its own dye, by its colour
        String teal = Wearables.PALETTE.stream().filter(c -> !Wearables.DYE_RGB.containsValue(Wearables.rgb(c)))
                .findFirst().orElseThrow();
        ItemDef dye = Wearables.dye(teal);
        assertNotNull(dye, teal);
        assertEquals("dye_" + teal.substring(1), dye.id());
        assertEquals(Wearables.colourName(teal) + " dye", dye.name());
        assertFalse(Wearables.colourName(teal).startsWith("#"), "a palette colour has a name: " + teal);
        Wearables.Colour locked = colours.stream().filter(c -> c.option().equals(teal)).findFirst().orElseThrow();
        assertNull(WardrobePanel.pick(wr, player, Slot.SHIRT, locked), "no dye of it: nothing to offer");
        player.inventory().add(Item.of(dye));
        assertEquals(teal, WardrobePanel.pick(wr, player, Slot.SHIRT, locked), "offered");
        assertTrue(Wearables.useDye(wr, player.inventory(), Slot.SHIRT, teal));
        assertEquals(teal, wr.colour(Slot.SHIRT));
        assertNull(Wearables.dyeIn(player.inventory(), teal), "used up");
        assertEquals(List.of(teal), palette(WardrobePanel.colours(lib, Slot.SHIRT, player), true));
        // one of the 14 named dyes' own colours is that dye's
        String pink = String.format("#%06x", Wearables.DYE_RGB.get("pink"));
        assertTrue(Wearables.PALETTE.contains(pink), "the dyes' colours are the palette's");
        assertSame(w("dye_pink"), Wearables.dye(pink));
        // an item with no colour of its own to change takes none of the palette's either
        assertEquals(List.of(), Wearables.colours(Slot.SHIRT, "one_colour", List.of(), null,
                Wearables.owned(player.inventory()), wr));
        // every name is its own
        assertEquals(100, Wearables.PALETTE.stream().map(Wearables::colourName).distinct().count());
    }

    @Test
    void everythingUnlockedOpensEveryColour() {
        List<String> all = List.of("brown", "pink");
        try {
            Wearables.setEverything(true);
            List<Wearables.Colour> colours = Wearables.colours(Slot.SHIRT, "plaid_flannel", all, null,
                    Wearables.owned(player.inventory()), new Wardrobe());
            assertEquals(all, open(colours));
            assertEquals(Wearables.PALETTE, palette(colours, true), "and the palette's");
        } finally {
            Wearables.setEverything(false);
        }
    }
    @Test
    void aTailoredGarmentStaysPickedAndThePlainOneAfterIt() {
        for (String id : List.of("baggy_shirt", "herders_shirt", "millers_shirt")) player.inventory().add(Item.of(w(id)));
        Wardrobe wr = player.wardrobe();
        List<WardrobePanel.Option> opts = WardrobePanel.options(lib, Slot.SHIRT, player);
        assertEquals(List.of("None", "Baggy shirt", "Herder's shirt", "Miller's shirt"),
                opts.stream().map(o -> o.label(Slot.SHIRT)).toList());
        for (int i = 1; i < opts.size(); i++) {
            WardrobePanel.choose(wr, Slot.SHIRT, opts.get(i));
            assertEquals("plaid_flannel", wr.get(Slot.SHIRT));
            assertEquals(i, WardrobePanel.current(opts, Slot.SHIRT, wr), opts.get(i).label(Slot.SHIRT) + " stays picked");
        }
        assertEquals("platinum", wr.colour(Slot.SHIRT), "the Miller's shirt is cut in platinum");
        WardrobePanel.choose(wr, Slot.SHIRT, opts.get(1));
        assertNull(wr.colour(Slot.SHIRT), "the plain shirt after it: its own colours, not the Miller's");
        assertEquals(1, WardrobePanel.current(opts, Slot.SHIRT, wr));
        // the plain one dyed is still the plain one; a tailored one dyed (without the plain one) is still itself
        wr.setColour(Slot.SHIRT, "mint");
        assertEquals(1, WardrobePanel.current(opts, Slot.SHIRT, wr));
        assertSame(w("herders_shirt"), Wearables.wornAs(Slot.SHIRT, "plaid_flannel", "copper"));
    }

    @Test
    void armourWithNothingUnderItHidesNothingAndNeverThrows() {
        Wardrobe wr = new Wardrobe();
        wr.set(Slot.CHESTPLATE, "iron_chestplate");
        wr.set(Slot.CHAINMAIL, "chainmail_shirt");
        for (Slot s : Slot.values()) assertDoesNotThrow(() -> wr.hidden(s), s + " with nothing worn there");
        assertFalse(wr.hidden(Slot.NECKLACE));
        assertNull(wr.cutBy(Slot.SHIRT));
        wr.set(Slot.NECKLACE, "amulet");
        wr.set(Slot.SHIRT, "plaid_flannel");
        assertTrue(wr.hidden(Slot.NECKLACE), "an amulet lies under the chestplate");
        assertTrue(wr.hidden(Slot.SHIRT), "a shirt under the mail");
        wr.set(Slot.SHIRT, "robe");
        assertFalse(wr.hidden(Slot.SHIRT));
        assertEquals(Slot.CHAINMAIL, wr.cutBy(Slot.SHIRT), "a robe is cut at the mail's edge");
    }

    @Test
    void theItemsOwnColoursAreSetAmongThePalettesNotApart() {
        Wardrobe wr = player.wardrobe();
        wr.set(Slot.SHIRT, "plaid_flannel");
        List<Wearables.Colour> colours = WardrobePanel.colours(lib, Slot.SHIRT, player);
        // "black" shows as one of the palette's colours; "pink" and "mint" as none of them
        String black = Wearables.PALETTE.get(0);
        java.util.Map<String, Integer> shows = java.util.Map.of("pink", 0xff5276, "black", Wearables.rgb(black),
                "mint", 0x56c77c);
        assertFalse(Wearables.PALETTE.contains("#ff5276") || Wearables.PALETTE.contains("#56c77c"));
        List<WardrobePanel.Cell> grid = WardrobePanel.grid(colours, o -> shows.getOrDefault(o, -1));
        assertEquals(Wearables.PALETTE.size() + 2, grid.size(), "the palette's hundred, black among them, and two more");
        assertEquals("black", grid.get(0).colour().option(), "its own black takes the palette's black's place");
        assertEquals(1, grid.stream().filter(c -> c.rgb() == Wearables.rgb(black)).count(), "once");
        for (String own : List.of("pink", "mint")) {
            int at = -1;
            for (int i = 0; i < grid.size(); i++) if (grid.get(i).colour().option().equals(own)) at = i;
            assertTrue(at > 0, own + " is in the grid");
            assertTrue(Variants.isCustom(grid.get(at - 1).colour().option()), own + " sits beside a palette colour");
        }
        // the colour worn is lit whether it was put on by its name or as the palette's colour
        assertTrue(WardrobePanel.worn(grid.get(0), "black"));
        assertTrue(WardrobePanel.worn(grid.get(0), black));
        assertFalse(WardrobePanel.worn(grid.get(1), "black"));
    }

    @Test
    void hidingTheArmourLeavesEveryPieceUndrawnButStillWornAndGuarding() {
        // the wardrobe knows armour by its folder: every piece that rolls dice, and nothing else
        for (ItemDef d : Wearables.ALL) {
            boolean listed = false;
            for (String f : d.wearable().folders()) listed |= Wardrobe.ARMOUR.contains(f);
            assertEquals(Wearables.armour(d), listed, d.id());
        }
        Wardrobe w = new Wardrobe();
        w.set(Slot.SHIRT, "plaid_flannel");
        w.set(Slot.CHAINMAIL, "chainmail_shirt");
        w.set(Slot.CHESTPLATE, "iron_chestplate");
        w.set(Slot.NECKLACE, "amulet");
        w.set(Slot.HAT, "great_helm");
        w.set(Slot.GLOVES, "iron_gauntlets");
        w.set(Slot.WRISTWEAR, "gear_cuff");
        assertTrue(w.hidden(Slot.SHIRT), "shown, the mail covers the shirt");
        assertFalse(w.hidden(Slot.HAT));
        int guarding = com.larsons.game.world.Armour.worn(w, null).size();
        assertEquals(4, guarding);

        w.setArmourHidden(true);
        for (Slot s : List.of(Slot.HAT, Slot.CHAINMAIL, Slot.CHESTPLATE, Slot.GLOVES)) {
            assertTrue(w.hidden(s), s + ": out of sight");
            assertNotNull(w.get(s), s + ": still worn");
        }
        for (Slot s : List.of(Slot.SHIRT, Slot.NECKLACE, Slot.WRISTWEAR)) {
            assertFalse(w.hidden(s), s + ": nothing drawn covers it now");
        }
        assertNull(w.cutBy(Slot.SHIRT));
        assertEquals(guarding, com.larsons.game.world.Armour.worn(w, null).size(), "hidden, it still rolls its dice");
        assertTrue(Wardrobe.fromJson(w.toJson()).armourHidden(), "kept with the look");
        assertTrue(w.copy().armourHidden());
        w.setArmourHidden(false);
        assertTrue(w.hidden(Slot.SHIRT));
        assertFalse(Wardrobe.fromJson(w.toJson()).armourHidden());
    }
    // --- armour over clothes, by the part of the body ------------------------------------------

    private static WardrobePanel.Row row(List<WardrobePanel.Row> rows, Slot slot) {
        for (WardrobePanel.Row r : rows) if (r.slot() == slot) return r;
        throw new AssertionError(slot + " has no row");
    }

    @Test
    void theArmourRowsAreSetOutByThePartOfTheBodyTheyGuard() {
        java.util.Map<com.larsons.game.world.Armour.Location, List<String>> by = new java.util.LinkedHashMap<>();
        int n = 0;
        for (var at : com.larsons.game.world.Armour.Location.values()) {
            List<String> labels = WardrobePanel.armourOf(at).stream().map(WardrobePanel.Row::label).toList();
            by.put(at, labels);
            n += labels.size();
        }
        assertEquals(List.of("Helmet", "Gorget"), by.get(com.larsons.game.world.Armour.Location.HEAD));
        assertEquals(List.of("Gambeson · jerkin", "Mail shirt", "Chestplate"),
                by.get(com.larsons.game.world.Armour.Location.TORSO));
        assertEquals(List.of("Gauntlets", "Right bracer", "Left bracer"),
                by.get(com.larsons.game.world.Armour.Location.ARMS));
        assertEquals(List.of("Mail leggings", "Leg plating"), by.get(com.larsons.game.world.Armour.Location.LEGS));
        assertEquals(List.of("Sabatons"), by.get(com.larsons.game.world.Armour.Location.FEET));
        assertEquals(WardrobePanel.ARMOUR.size(), n, "every armour row in one of them");
        for (WardrobePanel.Row r : WardrobePanel.ARMOUR) assertEquals(Boolean.TRUE, r.layer(), r.label());
    }

    @Test
    void aGorgetIsWornOverANecklaceAndNeitherTakesTheOtherOff() {
        player.inventory().add(Item.of(w("gorget")));
        player.inventory().add(Item.of(w("silver_necklace")));
        Wardrobe wr = player.wardrobe();
        WardrobePanel.Row gorget = row(WardrobePanel.ARMOUR, Slot.NECKLACE);
        WardrobePanel.Row necklace = row(WardrobePanel.CLOTHING, Slot.NECKLACE);
        List<WardrobePanel.Option> plates = WardrobePanel.rowOptions(lib, gorget, player);
        List<WardrobePanel.Option> chains = WardrobePanel.rowOptions(lib, necklace, player);
        assertEquals(List.of("None", "Steel gorget"), plates.stream().map(o -> o.label(Slot.NECKLACE)).toList());
        assertEquals(List.of("None", "Silver necklace"), chains.stream().map(o -> o.label(Slot.NECKLACE)).toList());

        WardrobePanel.chooseIn(wr, necklace, chains.get(1));
        WardrobePanel.chooseIn(wr, gorget, plates.get(1));
        assertEquals("gorget", wr.get(Slot.NECKLACE), "the gorget is drawn");
        assertEquals("gold_pendant", wr.get(Slot.NECKLACE, false), "the necklace is still worn, under it");
        assertEquals("silver", wr.colour(Slot.NECKLACE, false));
        assertEquals(1, WardrobePanel.current(WardrobePanel.rowOptions(lib, gorget, player), Slot.NECKLACE, wr,
                gorget.layer()));
        assertEquals(1, WardrobePanel.current(WardrobePanel.rowOptions(lib, necklace, player), Slot.NECKLACE, wr,
                necklace.layer()));
        assertEquals("Steel gorget", WardrobePanel.over(WardrobePanel.options(lib, Slot.NECKLACE, player), necklace,
                wr));
        // another necklace goes on under the gorget; the gorget off brings it back up
        WardrobePanel.chooseIn(wr, necklace, chains.get(0));
        assertEquals("gorget", wr.get(Slot.NECKLACE));
        assertNull(wr.get(Slot.NECKLACE, false));
        WardrobePanel.chooseIn(wr, necklace, chains.get(1));
        WardrobePanel.chooseIn(wr, gorget, plates.get(0));
        assertEquals("gold_pendant", wr.get(Slot.NECKLACE));
        assertEquals("silver", wr.colour(Slot.NECKLACE));
        assertNull(WardrobePanel.over(WardrobePanel.options(lib, Slot.NECKLACE, player), necklace, wr));
    }

    @Test
    void aDyedPieceIsStillItselfAndAForgedOneIsDyedLikeAnyOther() {
        player.inventory().add(Item.of(w("bronze_helmet")));
        Wardrobe wr = player.wardrobe();
        WardrobePanel.Row helmet = row(WardrobePanel.ARMOUR, Slot.HAT);
        List<WardrobePanel.Option> opts = WardrobePanel.rowOptions(lib, helmet, player);
        WardrobePanel.chooseIn(wr, helmet, opts.get(1));
        assertEquals("bronze_helmet", wr.id(Slot.HAT, true));
        wr.setColour(Slot.HAT, true, "#3c6cc4");
        assertEquals(1, WardrobePanel.current(opts, Slot.HAT, wr, true), "dyed blue, still the bronze helmet");
        assertEquals(w("bronze_helmet"), com.larsons.game.world.Armour.item(wr, player.inventory(), Slot.HAT)
                .def());

        // a forged gorget: listed by its name, worn as itself, and the palette's colours to dye it
        var forged = new com.larsons.game.world.Forged("my_gorget", "Wyrm collar", "gorget",
                com.larsons.game.world.Rarity.EPIC, java.util.Map.of(), 1, 6, -1, Set.of());
        player.inventory().add(forged.item());
        WardrobePanel.Row gorget = row(WardrobePanel.ARMOUR, Slot.NECKLACE);
        List<WardrobePanel.Option> plates = WardrobePanel.rowOptions(lib, gorget, player);
        List<String> labels = plates.stream().map(o -> o.label(Slot.NECKLACE)).toList();
        int wyrm = labels.indexOf("Wyrm collar (forged)");
        assertTrue(wyrm > 0, "listed by its name: " + labels);
        WardrobePanel.chooseIn(wr, gorget, plates.get(wyrm));
        assertEquals("forged:my_gorget", wr.id(Slot.NECKLACE, true));
        assertNull(wr.colour(Slot.NECKLACE, true), "in its own look");
        assertEquals(wyrm, WardrobePanel.current(plates, Slot.NECKLACE, wr, true));
        int steel = labels.indexOf("Steel gorget");
        if (steel > 0) assertNotEquals(steel, WardrobePanel.current(plates, Slot.NECKLACE, wr, true),
                "the forged gorget is not taken for the steel one it is made from");
        assertFalse(WardrobePanel.colours(lib, Slot.NECKLACE, player, true).isEmpty(), "dyed like any other");
    }
}
