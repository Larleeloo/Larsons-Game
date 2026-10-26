package com.larsons.game.scene;

import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
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
        assertEquals(List.of("None", "party_hat"), labels(Slot.HAT));
    }

    private long pinkDyes() {
        return player.inventory().items().stream().filter(i -> i.def() == w("dye_pink")).count();
    }

    private static List<String> open(List<Wearables.Colour> colours) {
        return colours.stream().filter(Wearables.Colour::unlocked).map(Wearables.Colour::option).toList();
    }

    private static List<String> options(List<Wearables.Colour> colours) {
        return colours.stream().map(Wearables.Colour::option).toList();
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
        assertEquals(List.of(), open(Wearables.colours(Slot.HAIR, "bob", all, null, dye, wr)),
                "a dye in the bag unlocks nothing until it is used");
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
    void everythingUnlockedOpensEveryColour() {
        List<String> all = List.of("brown", "pink");
        try {
            Wearables.setEverything(true);
            assertEquals(all, open(Wearables.colours(Slot.SHIRT, "plaid_flannel", all, null,
                    Wearables.owned(player.inventory()), new Wardrobe())));
        } finally {
            Wearables.setEverything(false);
        }
    }
}
