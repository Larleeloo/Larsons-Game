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
    void aNewCharacterHasHerUnderwearAndTheFreeLayersOnly() {
        assertEquals(java.util.Arrays.asList(null, "cotton_briefs"), items(Slot.UNDERWEAR));
        assertEquals(List.of("None", "Underwear"), labels(Slot.UNDERWEAR));
        assertEquals(List.of("None", "Bra"), labels(Slot.BRA));
        for (Slot s : List.of(Slot.HAIR, Slot.EYEBROWS, Slot.SHIRT, Slot.CHESTPLATE, Slot.WRISTWEAR, Slot.MAKEUP)) {
            assertEquals(java.util.Collections.singletonList(null), items(s), s + ": bald, browless, nothing found yet");
        }
        // the free layers: every folder there is
        assertEquals(java.util.Arrays.asList(null, "elven", "round"), items(Slot.EARS));
        assertEquals(java.util.Arrays.asList(null, "full"), items(Slot.MOUTH));
        // the hands: what is carried that is held in them, not the stances' weapons
        player.inventory().add(Item.of(w("belt")));
        assertEquals(java.util.Arrays.asList(null, "sword"), items(Slot.CARRY_RIGHT));
    }

    @Test
    void whatSheOwnsIsOfferedByName() {
        player.inventory().add(Item.of(w("hair_bob")));
        player.inventory().add(Item.of(w("hair_long_waves")));
        player.inventory().add(Item.of(w("silver_wrist_band")));
        player.inventory().add(Item.of(w("sheath")));
        player.inventory().add(Item.of(w("iron_chestplate")));
        player.inventory().add(Item.of(w("dye_pink")));
        assertEquals(List.of("None", "Bob hairstyle", "Long waves hairstyle"), labels(Slot.HAIR));
        assertEquals(java.util.Arrays.asList(null, "bob", "long_waves"), items(Slot.HAIR));
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
        wr.set(Slot.HAIR, "long_waves");
        wr.set(Slot.SHIRT, "plaid_flannel");
        wr.set(Slot.NECKLACE, "gold_pendant");
        wr.setColour(Slot.NECKLACE, "silver");
        List<WardrobePanel.Option> hair = WardrobePanel.options(lib, Slot.HAIR, player);
        assertEquals(List.of("None", "Long waves hairstyle"), hair.stream().map(o -> o.label(Slot.HAIR)).toList());
        assertEquals(1, WardrobePanel.current(hair, Slot.HAIR, wr));
        assertEquals(List.of("None", "Baggy shirt"), labels(Slot.SHIRT));
        assertEquals(List.of("None", "Silver necklace"), labels(Slot.NECKLACE));
        WardrobePanel.choose(wr, Slot.HAIR, hair.get(0));
        assertEquals(java.util.Collections.singletonList(null), items(Slot.HAIR), "once off, only if she owns it");
        // a folder no item puts there is listed by its folder
        wr.set(Slot.HAT, "party_hat");
        assertEquals(List.of("None", "party_hat"), labels(Slot.HAT));
    }

    @Test
    void theColoursAreTheDyesSheOwns() {
        Wardrobe wr = player.wardrobe();
        wr.set(Slot.SHIRT, "plaid_flannel");
        assertEquals(List.of(), WardrobePanel.colours(lib, Slot.SHIRT, player), "no dyes: its own colours only");
        player.inventory().add(Item.of(w("dye_mint")));
        player.inventory().add(Item.of(w("dye_pink")));
        assertEquals(List.of("pink", "mint"), WardrobePanel.colours(lib, Slot.SHIRT, player),
                "in the item's own order");
        wr.setColour(Slot.SHIRT, "black");
        assertEquals(List.of("pink", "black", "mint"), WardrobePanel.colours(lib, Slot.SHIRT, player),
                "a colour already on stays, and is shown");
    }

    @Test
    void theSkinAndEyesAreFreeTheLipsTheLipsticksAndAMetalItsOwn() {
        List<String> all = List.of("brown", "pink", "royal_blue");
        Set<ItemDef> none = Wearables.owned(player.inventory());
        assertEquals(all, Wearables.colours(Slot.BODY, "feminine", all, null, none));
        assertEquals(all, Wearables.colours(Slot.EYES, "hazel", all, null, none));
        assertEquals(List.of(), Wearables.colours(Slot.MOUTH, "full", all, null, none));
        assertEquals(List.of("pink"), Wearables.colours(Slot.MOUTH, "full", all, "pink", none), "lips already pink");
        player.inventory().add(Item.of(w("dye_pink")));
        Set<ItemDef> dye = Wearables.owned(player.inventory());
        assertEquals(List.of(), Wearables.colours(Slot.MOUTH, "full", all, null, dye), "a dye is not lipstick");
        assertEquals(List.of("pink"), Wearables.colours(Slot.HAIR, "bob", all, null, dye));
        player.inventory().add(Item.of(Wearables.LIPSTICK));
        Set<ItemDef> lipstick = Wearables.owned(player.inventory());
        assertEquals(all, Wearables.colours(Slot.MOUTH, "full", all, null, lipstick));
        assertEquals(List.of(), Wearables.colours(Slot.WRISTWEAR, "gold_bangle", List.of("gold", "silver", "pink"),
                "silver", lipstick), "a metal is its own colour");
        assertEquals(all, Wearables.colours(Slot.CARRY_LEFT, "round_shield", all, null, none), "the hands as before");
    }
}
