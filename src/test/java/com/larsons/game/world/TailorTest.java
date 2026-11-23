package com.larsons.game.world;

import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Tailor ({@link Tailor}): every piece of clothing and armour climbs
 * tiers, and gives more at each - as the Tailor sets it, else its kind's
 * default; and the Tailor's own pieces (forged), each tier giving what it is
 * made with.
 */
class TailorTest {

    @TempDir
    Path dir;

    @BeforeEach
    void ownFolders() {
        Tailor.useDir(dir.resolve("tailor"));
        Forged.useDir(dir.resolve("forge"));
    }

    @AfterEach
    void backToTheGames() {
        Tailor.useDir(Path.of("assets", Tailor.FOLDER));
        Forged.useDir(Path.of("assets", Forged.FOLDER));
    }

    private static ItemDef w(String id) {
        ItemDef d = Wearables.byId(id);
        assertNotNull(d, id);
        return d;
    }

    /** Her, carrying {@code item} and wearing it where it goes (as armour if it is armour). */
    private static Player wearing(Item item) {
        Player p = new Player(new Wardrobe());
        p.inventory().add(item);
        Slot slot = item.def().wearable().slots().get(0);
        String folder = item.def().wearable().folders().get(0);
        p.wardrobe().wear(slot, Wardrobe.isArmour(folder), folder);
        p.wardrobe().setId(slot, Wardrobe.isArmour(folder),
                item.forged() != null ? Armour.forgedLook(item.forged()) : item.def().id());
        return p;
    }

    // --- every piece has tiers ------------------------------------------------------------

    @Test
    void everyGarmentAndEveryPieceOfArmourClimbsTiersAndNothingElseWornDoes() {
        int clothes = 0, armour = 0;
        for (ItemDef d : Wearables.ALL) {
            boolean piece = Forged.wornBase(d);
            assertEquals(piece, Tailor.tailors(d), d.id());
            if (!piece) {
                assertNull(Tier.row(d.id()), d.id() + ": a dye, a hair style, makeup - no tiers");
                continue;
            }
            Tier.Row row = Tier.row(d.id());
            assertNotNull(row, d.id());
            assertEquals(d.wearable().rarity(), row.lowest().rarity(), d.id() + " starts as it is found");
            assertEquals(Tier.MYTHIC, row.tiers().get(row.tiers().size() - 1), d.id() + " climbs to Mythic");
            if (Armour.sides(d) > 0) armour++;
            else clothes++;
        }
        assertTrue(clothes > 40, "the clothes: " + clothes);
        assertTrue(armour > 20, "the armour: " + armour);
        assertEquals(clothes + armour, Tailor.pieces().size());
        assertEquals(Tier.armour().size(), armour);
        assertEquals(Tier.clothing().size(), clothes);
    }

    @Test
    void aGarmentIsFoundAsItIsAndNamedForItsTierAbove() {
        ItemDef shirt = w("baggy_shirt");
        Item found = Item.of(shirt);
        assertEquals(Tier.BASIC, found.tier());
        assertEquals("baggy_shirt", found.key(), "a garment as found is saved as before");
        assertEquals("Baggy shirt", found.name());
        assertEquals(List.of(Tier.BASIC, Tier.SHARPER, Tier.CRAFTED, Tier.HONED, Tier.PERFECT, Tier.MYTHIC),
                Tier.row("baggy_shirt").tiers());
        assertEquals("Fine baggy shirt", Item.of(shirt, Tier.SHARPER).name());
        assertEquals("Masterwork baggy shirt", Item.of(shirt, Tier.HONED).name());
        assertEquals("Fine", Tier.label(shirt, Tier.SHARPER));
        assertEquals("Reinforced", Tier.label(w("iron_helmet"), Tier.SHARPER));
        assertEquals("Sharper", Tier.label(ItemDef.SWORD, Tier.SHARPER));
        Item back = Item.parse(Item.of(shirt, Tier.PERFECT).key());
        assertEquals(Tier.PERFECT, back.tier());
        assertEquals(Rarity.LEGENDARY, back.rarity());
        // an Epic necklace has only its Epic, Legendary and Mythic tiers
        assertEquals(List.of(Tier.HONED, Tier.PERFECT, Tier.MYTHIC), Tier.row("gold_necklace").tiers());
    }

    // --- what each tier gives ----------------------------------------------------------------

    @Test
    void byDefaultAPieceGivesNothingAsFoundAndMoreEachTierUp() {
        ItemDef helmet = w("iron_helmet");     // found Rare: Rare, Epic, Legendary, Mythic
        assertTrue(Tailor.gear(helmet, Tier.CRAFTED).isEmpty(), "as it is found: nothing");
        assertEquals(30, Tailor.gear(helmet, Tier.HONED).get(Gear.Effect.HEALTH), 1e-9, "Epic: three times 10");
        assertEquals(40, Tailor.gear(helmet, Tier.PERFECT).get(Gear.Effect.HEALTH), 1e-9);
        assertEquals(50, Tailor.gear(helmet, Tier.MYTHIC).get(Gear.Effect.HEALTH), 1e-9);
        assertEquals(10, Tailor.gear(w("riding_boots"), Tier.CRAFTED).get(Gear.Effect.SPEED), 1e-9, "boots: speed");
        assertEquals(10, Tailor.gear(w("baggy_shirt"), Tier.SHARPER).get(Gear.Effect.STAMINA), 1e-9, "a shirt: stamina");
        assertEquals(10, Tailor.gear(w("straw_sun_hat"), Tier.SHARPER).get(Gear.Effect.MANA), 1e-9, "a hat: mana");
        assertEquals(50, Tailor.gear(w("clockwork_wings"), Tier.MYTHIC).get(Gear.Effect.JUMP), 1e-9, "wings: jump");
        for (ItemDef d : Tailor.pieces()) {
            assertNotNull(Tailor.theme(d), d.id());
            assertTrue(Tailor.gear(d, Tier.row(d.id()).lowest()).isEmpty(), d.id() + " gives nothing as found");
        }
    }

    @Test
    void anUpgradedGarmentWornGivesItsTiersGifts() {
        ItemDef boots = w("riding_boots");
        Player found = wearing(Item.of(boots));
        assertTrue(Gear.of(found.wardrobe(), found.inventory()).isEmpty(), "as found, nothing");
        Player upgraded = wearing(Item.of(boots, Tier.PERFECT));
        assertEquals(1.20, Gear.of(upgraded.wardrobe(), upgraded.inventory()).speedTimes(), 1e-9,
                "a Legendary pair: +20% speed");
        // the best she carries is the one worn
        found.inventory().add(Item.of(boots, Tier.HONED));
        assertEquals(1.15, Gear.of(found.wardrobe(), found.inventory()).speedTimes(), 1e-9);
    }

    @Test
    void aGarmentIsUpgradedWithIngotsAndItsPathSaysWhatEachTierGives() {
        Inventory inv = new Inventory(25);
        inv.set(0, Item.of(w("baggy_shirt")));
        assertTrue(Upgrades.has(inv.get(0)));
        List<Upgrades.Step> path = Upgrades.path(inv.get(0), r -> 1, 20);
        assertEquals(6, path.size());
        for (Upgrades.Step s : path) {
            assertTrue(s.worn(), "no ammunition");
            assertFalse(s.rolls(), "clothes roll no dice");
        }
        assertTrue(path.get(0).gives().isEmpty());
        assertEquals(List.of("+10 stamina"), path.get(1).gifts());
        assertEquals(List.of("+50 stamina"), path.get(5).gifts());
        inv.put(Item.of(Supplies.UPGRADE_INGOT), 3, true);
        assertEquals(Upgrades.Result.UPGRADED, Upgrades.upgrade(inv, 0));
        assertEquals("Fine baggy shirt", inv.get(0).name());
        // armour blocks with its dice at each tier, and gives what the tier says
        List<Upgrades.Step> helm = Upgrades.path(Item.of(w("iron_helmet")), r -> 1, 20);
        assertEquals("1d8", helm.get(0).dice());
        assertEquals("2d8", helm.get(1).dice());
        assertEquals(List.of("+30 health"), helm.get(1).gifts());
    }

    @Test
    void whatTheTailorSetsIsKeptAndWornAndItsDefaultComesBack() throws Exception {
        ItemDef hat = w("wizard_hat");               // Uncommon: Uncommon, Rare, Epic, Legendary, Mythic
        List<Tailor.Rung> rungs = Tailor.rungs(hat);
        assertEquals(5, rungs.size());
        rungs = new java.util.ArrayList<>(rungs);
        rungs.set(0, rungs.get(0).withGear(Gear.Effect.MANA_REGEN, 1));
        rungs.set(1, new Tailor.Rung("Starlit wizard's hat", Map.of(Gear.Effect.MANA, 40.0, Gear.Effect.JUMP, 10.0)));
        Tailor.save(hat, rungs);
        assertTrue(Files.exists(dir.resolve("tailor/wizard_hat.json")));
        assertTrue(Tailor.edited(hat));
        assertEquals("Starlit wizard's hat", Item.of(hat, Tier.CRAFTED).name(), "the tier's own name");
        assertEquals(1, Tailor.gear(hat, Tier.SHARPER).get(Gear.Effect.MANA_REGEN), 1e-9, "set as found too");
        // read again from the file, as another start of the game would
        Tailor.reload();
        assertEquals(Map.of(Gear.Effect.MANA, 40.0, Gear.Effect.JUMP, 10.0), Tailor.gear(hat, Tier.CRAFTED));
        assertEquals(rungs.get(2).gear(), Tailor.gear(hat, Tier.HONED), "the tiers not changed kept as they were");
        Player p = wearing(Item.of(hat, Tier.CRAFTED));
        assertEquals(40, Gear.of(p.wardrobe(), p.inventory()).get(Gear.Effect.MANA), 1e-9);
        // back to its default
        Tailor.reset(hat);
        assertFalse(Tailor.edited(hat));
        assertFalse(Files.exists(dir.resolve("tailor/wizard_hat.json")));
        assertEquals(20, Tailor.gear(hat, Tier.CRAFTED).get(Gear.Effect.MANA), 1e-9);
        assertEquals("Superior wizard's hat", Item.of(hat, Tier.CRAFTED).name());
    }

    @Test
    void theTiersAboveCanGiveTheSameOrGrow() {
        List<Tailor.Rung> rungs = new java.util.ArrayList<>(Tailor.rungs(w("baggy_shirt")));
        rungs.set(1, rungs.get(1).withGear(Map.of(Gear.Effect.HEALTH, 10.0, Gear.Effect.DOUBLE_JUMP, 1.0)));
        List<Tailor.Rung> same = Tailor.upTheTiers(rungs, 1, false);
        for (int i = 2; i < same.size(); i++) assertEquals(rungs.get(1).gear(), same.get(i).gear());
        assertEquals(rungs.get(0), same.get(0), "below it, as it was");
        List<Tailor.Rung> grown = Tailor.upTheTiers(rungs, 1, true);
        assertEquals(20, grown.get(2).gear().get(Gear.Effect.HEALTH), 1e-9);
        assertEquals(50, grown.get(5).gear().get(Gear.Effect.HEALTH), 1e-9);
        assertEquals(1, grown.get(5).gear().get(Gear.Effect.DOUBLE_JUMP), 1e-9, "had or not");
        // from the first tier: as much again each tier
        rungs.set(0, rungs.get(0).withGear(Map.of(Gear.Effect.SPEED, 5.0)));
        List<Tailor.Rung> fromFirst = Tailor.upTheTiers(rungs, 0, true);
        assertEquals(10, fromFirst.get(1).gear().get(Gear.Effect.SPEED), 1e-9);
        assertEquals(30, fromFirst.get(5).gear().get(Gear.Effect.SPEED), 1e-9);
    }

    @Test
    void aPairOfBandsOnBothWristsCountsOnce() {
        ItemDef band = w("silver_wrist_band");
        Player p = new Player(new Wardrobe());
        p.inventory().add(Item.of(band, Tier.PERFECT));
        for (Slot s : List.of(Slot.WRISTWEAR, Slot.WRISTWEAR_LEFT)) {
            p.wardrobe().wear(s, false, "gold_bangle");
            p.wardrobe().setColour(s, false, "silver");
            p.wardrobe().setId(s, false, band.id());
        }
        assertEquals(8, Gear.of(p.wardrobe(), p.inventory()).get(Gear.Effect.STAMINA_ABSORB), 1e-9);
    }

    // --- the Tailor's own pieces: each tier gives what it is made with ------------------------------------

    private static Forged boots(List<Forged.Upgrade> ups) {
        return new Forged("springs", "Springs", "riding_boots", Rarity.RARE, Map.of(), 1, 4, -1, Set.of(), List.of(),
                Forged.AMMO_EVERY, ups, 0, Map.of(Gear.Effect.SPEED, 10.0, Gear.Effect.JUMP, 10.0));
    }

    @Test
    void aForgedPiecesTiersEachGiveTheirOwnOrTheTierBefores() {
        Forged.Upgrade two = new Forged.Upgrade("", Rarity.EPIC, 1, 4, List.of(), -1, Map.of(), 12, 2, false,
                Map.of(Gear.Effect.SPEED, 20.0, Gear.Effect.DOUBLE_JUMP, 1.0));
        Forged.Upgrade three = new Forged.Upgrade("Cloudsteps", Rarity.LEGENDARY, 1, 4, List.of(), -1, Map.of(), 12, 3,
                false, null);
        Forged f = boots(List.of(two, three));
        assertEquals(Map.of(Gear.Effect.SPEED, 10.0, Gear.Effect.JUMP, 10.0), f.gear(), "as forged");
        assertEquals(Map.of(Gear.Effect.SPEED, 20.0, Gear.Effect.DOUBLE_JUMP, 1.0), f.at(1).gear(), "its own");
        assertEquals(f.at(1).gear(), f.at(2).gear(), "as the tier before");
        assertEquals(f.gear(), f.at(2).baseGear());
        // saved and read back
        Forged back = Forged.parse("springs", Forged.json(f));
        assertEquals(f.at(1).gear(), back.at(1).gear());
        assertNull(back.upgrades().get(1).gear(), "still as the tier before");
        assertEquals(f.at(2).gear(), back.at(2).gear());
        // worn at its third tier (the one she carries): the boots' tier gives
        Player p = wearing(back.at(2).item());
        Gear.Totals g = Gear.of(p.wardrobe(), p.inventory());
        assertEquals(1.2, g.speedTimes(), 1e-9);
        assertTrue(g.has(Gear.Effect.DOUBLE_JUMP));
        assertFalse(g.has(Gear.Effect.JUMP), "a tier says all it gives");
        // its path: no dice (clothes), no ammunition, and what each tier gives
        List<Upgrades.Step> path = Upgrades.path(back.item(), r -> 1, 20);
        assertEquals(3, path.size());
        assertEquals("", path.get(1).dice());
        assertEquals(0, path.get(1).ammoEvery());
        assertEquals(List.of("Double jump", "+20% speed"), path.get(1).gifts());
        assertEquals("Cloudsteps", path.get(2).name());
    }

    @Test
    void aWeaponsTiersGiveNoneOfTheGearsEffects() {
        Forged.Upgrade up = new Forged.Upgrade("", Rarity.EPIC, 2, 8, List.of(), -1, Map.of(), 12, 2, false,
                Map.of(Gear.Effect.HEALTH, 50.0));
        Forged sword = new Forged("blade", "Blade", "sword", Rarity.RARE, Map.of(), 1, 8, -1, Set.of(), List.of(),
                Forged.AMMO_EVERY, List.of(up), 0, Map.of());
        assertNull(sword.upgrades().get(0).gear());
        assertTrue(sword.at(1).gear().isEmpty());
    }

    @Test
    void aForgedArmourTierStillTakesADye() {
        // the dye is the wardrobe's, whatever the tier: worn as the forged piece, in the dye's colour
        Forged helm = new Forged("helm", "Helm", "iron_helmet", Rarity.RARE, Map.of("main", 0x222222), 1, 8, -1,
                Set.of(), List.of(), Forged.AMMO_EVERY,
                List.of(new Forged.Upgrade("", Rarity.EPIC, 2, 8, List.of(), -1, Map.of("main", 0xF9C36D), 12, 2,
                        false, Map.of(Gear.Effect.HEALTH, 20.0))), 0, Map.of());
        Player p = wearing(helm.at(1).item());
        p.wardrobe().setColour(Slot.HAT, true, Wearables.PALETTE.get(40));
        assertEquals(Armour.forgedLook(helm), p.wardrobe().id(Slot.HAT), "still worn as itself");
        assertEquals(Wearables.PALETTE.get(40), p.wardrobe().colour(Slot.HAT), "in its dye");
        Item worn = Armour.item(p.wardrobe(), p.inventory(), Slot.HAT);
        assertEquals(1, worn.forged().level(), "at the tier she carries");
        assertEquals(20, Gear.of(p.wardrobe(), p.inventory()).get(Gear.Effect.HEALTH), 1e-9);
    }
}
