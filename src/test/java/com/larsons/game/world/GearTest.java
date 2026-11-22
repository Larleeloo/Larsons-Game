package com.larsons.game.world;

import com.larsons.game.util.Json;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Armour and clothes the Forge gives special effects ({@link Gear}), worn
 * over and under each other ({@link Wardrobe#wear}), and what they do for
 * her in play; and stamina spent running.
 */
class GearTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);
    private static final Player.Intent JUMP = new Player.Intent(0, 0, false, false, true, false);

    private static ItemDef w(String id) {
        ItemDef d = Wearables.byId(id);
        assertNotNull(d, id);
        return d;
    }

    /** A piece forged from {@code base}, its id {@code id}, with {@code gear}. */
    private static Forged forged(String id, String base, Map<Gear.Effect, Double> gear) {
        return new Forged(id, "Forged " + base, base, Rarity.EPIC, Map.of(), 1, 8, -1, Set.of(), List.of(),
                Forged.AMMO_EVERY, List.of(), 0, gear);
    }

    /** Her, wearing {@code f} where it goes (as armour if it is armour), carrying it. */
    private static Player wearing(Forged... pieces) {
        Player p = new Player(new Wardrobe());
        for (Forged f : pieces) {
            p.inventory().add(f.item());
            Slot slot = f.def().wearable().slots().get(0);
            String folder = f.def().wearable().folders().get(0);
            p.wardrobe().wear(slot, Wardrobe.isArmour(folder), folder);
            p.wardrobe().setId(slot, Wardrobe.isArmour(folder), Armour.forgedLook(f));
        }
        return p;
    }

    // --- the Forge's effects ---------------------------------------------------------------

    @Test
    void armourAndClothesTakeTheGearsEffectsAndNeverTheWeaponsBorders() {
        Forged gorget = new Forged("g", "Gorget", "gorget", Rarity.RARE, Map.of(), 1, 6, -1,
                Set.of(Border.VENOM, Border.MYSTIC, Border.BONDED), List.of(), Forged.AMMO_EVERY, List.of(), 0,
                Map.of(Gear.Effect.HEALTH, 40.0));
        assertTrue(gorget.effects().isEmpty(), "no Venom, Mystic or Bonded on armour");
        assertEquals(40, gorget.gear().get(Gear.Effect.HEALTH));
        assertTrue(gorget.worn());

        Forged shirt = forged("s", "baggy_shirt", Map.of(Gear.Effect.SPEED, 999.0, Gear.Effect.DOUBLE_JUMP, 5.0));
        assertTrue(Forged.wornBase(w("baggy_shirt")), "clothes are forged too");
        assertEquals(Gear.Effect.SPEED.max(), shirt.gear().get(Gear.Effect.SPEED), "kept within its most");
        assertEquals(1, shirt.gear().get(Gear.Effect.DOUBLE_JUMP), "had or not");
        assertTrue(shirt.effects().isEmpty());

        Forged sword = new Forged("w", "Blade", "sword", Rarity.EPIC, Map.of(), 1, 20, -1, Set.of(Border.VENOM),
                List.of(), Forged.AMMO_EVERY, List.of(), 0, Map.of(Gear.Effect.HEALTH, 40.0));
        assertEquals(Set.of(Border.VENOM), sword.effects(), "a weapon keeps its border");
        assertTrue(sword.gear().isEmpty(), "and has none of the gear's");
        assertFalse(Forged.wornBase(w("dye_pink")), "a dye is not forged");
    }

    @Test
    void theGearsEffectsAreSavedAndReadBack() {
        Forged f = forged("amulet", "gorget", Map.of(Gear.Effect.MANA_REGEN, 2.5, Gear.Effect.DOUBLE_JUMP, 1.0));
        Forged back = Forged.parse("amulet", Forged.json(f));
        assertEquals(f.gear(), back.gear());
        assertEquals(List.of("+2.5 mana a second", "A second jump in the air"), back.gearText());
        Forged off = back.withGear(Gear.Effect.MANA_REGEN, 0);
        assertFalse(off.gear().containsKey(Gear.Effect.MANA_REGEN));
    }

    @Test
    void everythingWornAddsUpTheNecklaceUnderTheGorgetToo() {
        Forged gorget = forged("gorget1", "gorget", Map.of(Gear.Effect.HEALTH, 25.0, Gear.Effect.JUMP, 20.0));
        Forged chain = forged("chain1", "silver_necklace", Map.of(Gear.Effect.HEALTH, 10.0));
        Player p = new Player(new Wardrobe());
        p.inventory().add(gorget.item());
        p.inventory().add(chain.item());
        Wardrobe wd = p.wardrobe();
        wd.wear(Slot.NECKLACE, false, "gold_pendant");
        wd.setId(Slot.NECKLACE, false, Armour.forgedLook(chain));
        wd.wear(Slot.NECKLACE, true, "gorget");
        wd.setId(Slot.NECKLACE, true, Armour.forgedLook(gorget));
        assertEquals("gorget", wd.get(Slot.NECKLACE), "the gorget drawn over it");
        assertEquals("gold_pendant", wd.get(Slot.NECKLACE, false), "the necklace still worn under it");
        Gear.Totals g = Gear.of(wd, p.inventory());
        assertEquals(35, g.get(Gear.Effect.HEALTH), 1e-9);
        assertEquals(1.2, g.jumpTimes(), 1e-9);
        assertEquals(135, p.maxHealth(), 1e-9);
        // the gorget off: the necklace comes back up, and counts alone
        wd.wear(Slot.NECKLACE, true, null);
        assertEquals("gold_pendant", wd.get(Slot.NECKLACE));
        assertEquals(10, Gear.of(wd, p.inventory()).get(Gear.Effect.HEALTH), 1e-9);
    }

    @Test
    void aPairOfForgedBandsOnBothWristsCountsOnce() {
        Forged band = forged("band", "silver_wrist_band", Map.of(Gear.Effect.SPEED, 10.0));
        Player p = new Player(new Wardrobe());
        p.inventory().add(band.item());
        String folder = band.def().wearable().folders().get(0);
        for (Slot s : List.of(Slot.WRISTWEAR, Slot.WRISTWEAR_LEFT)) {
            p.wardrobe().wear(s, false, folder);
            p.wardrobe().setId(s, false, Armour.forgedLook(band));
        }
        assertEquals(1.1, Gear.of(p.wardrobe(), p.inventory()).speedTimes(), 1e-9);
    }

    // --- armour over clothes ------------------------------------------------------------------

    @Test
    void armourGoesOverTheClothesAndHiddenArmourShowsThem() {
        Wardrobe wd = new Wardrobe();
        wd.set(Slot.HAT, "straw_sun_hat");
        wd.setColour(Slot.HAT, "red");
        wd.wear(Slot.HAT, true, "iron_helmet");
        assertEquals("iron_helmet", wd.get(Slot.HAT));
        assertEquals("straw_sun_hat", wd.get(Slot.HAT, false));
        assertEquals("red", wd.colour(Slot.HAT, false), "the hat keeps its colour under the helmet");
        assertNull(wd.colour(Slot.HAT));
        // another hat goes under the helmet, not instead of it
        wd.wear(Slot.HAT, false, "wizard_hat");
        assertEquals("iron_helmet", wd.get(Slot.HAT));
        assertEquals("wizard_hat", wd.get(Slot.HAT, false));
        // saved and read back
        Wardrobe back = Wardrobe.fromJson(Json.asObject(Json.parse(Json.stringify(wd.toJson()))));
        assertEquals("iron_helmet", back.get(Slot.HAT));
        assertEquals("wizard_hat", back.get(Slot.HAT, false));
        // the armour out of sight: the hat under it is what is drawn
        wd.setArmourHidden(true);
        assertEquals("wizard_hat", wd.drawn().get(Slot.HAT));
        assertFalse(wd.drawn().armourHidden());
        assertSame(wd.drawn(), wd.drawn(), "made once until it changes");
        wd.setArmourHidden(false);
        assertSame(wd, wd.drawn());
    }

    @Test
    void aForgedPieceSavedBeforeIsWornAsItself() {
        Wardrobe wd = Wardrobe.fromJson(Json.asObject(Json.parse(
                "{\"items\": {\"necklace\": \"gorget\"}, \"colours\": {\"necklace\": \"forged:old\"}}")));
        assertEquals("forged:old", wd.id(Slot.NECKLACE));
        assertNull(wd.colour(Slot.NECKLACE), "its colour free to be dyed");
    }

    // --- what it does for her ---------------------------------------------------------------

    @Test
    void healthManaAndStaminaGrowAndFillFaster() {
        Player p = wearing(forged("helm", "iron_helmet", Map.of(Gear.Effect.HEALTH, 50.0,
                Gear.Effect.STAMINA, 20.0, Gear.Effect.HEALTH_REGEN, 10.0)));
        assertEquals(150, p.maxHealth(), 1e-9);
        assertEquals(120, p.maxStamina(), 1e-9);
        p.setHealth(101);
        p.hurt(1);
        for (int i = 0; i < 60; i++) p.update(DT, Player.Intent.NONE, DUR);
        assertEquals(110, p.health(), 0.5, "ten a second, even just after being hurt");
        // taken off, no more than she can hold now
        p.setHealth(150);
        p.wardrobe().wear(Slot.HAT, true, null);
        p.update(DT, Player.Intent.NONE, DUR);
        assertEquals(Player.MAX_HEALTH, p.health(), 1e-9);
    }

    @Test
    void theSecondJumpIsHadOnlyWithTheGearForIt() {
        Player plain = new Player(new Wardrobe());
        plain.update(DT, JUMP, DUR);
        for (int i = 0; i < 25; i++) plain.update(DT, Player.Intent.NONE, DUR);
        assertTrue(plain.airborne());
        plain.update(DT, JUMP, DUR);
        assertNotEquals(AnimState.DOUBLE_JUMP, plain.state(), "no gear, no second jump");

        Player p = wearing(forged("boots", "sabatons", Map.of(Gear.Effect.DOUBLE_JUMP, 1.0)));
        p.update(DT, JUMP, DUR);
        double peakOne = 0, peakTwo = 0;
        for (int i = 0; i < 25; i++) {
            p.update(DT, Player.Intent.NONE, DUR);
            peakOne = Math.max(peakOne, p.height());
        }
        assertTrue(p.canDoubleJump());
        p.update(DT, JUMP, DUR);
        assertEquals(AnimState.DOUBLE_JUMP, p.state());
        assertTrue(p.doubleJumped());
        assertFalse(p.canDoubleJump(), "once a jump");
        for (int i = 0; i < 200 && p.airborne(); i++) {
            p.update(DT, i == 3 ? JUMP : Player.Intent.NONE, DUR);
            peakTwo = Math.max(peakTwo, p.height());
        }
        assertTrue(peakTwo > peakOne + 0.2, "higher again: " + peakOne + " then " + peakTwo);
        assertFalse(p.airborne());
        p.update(DT, Player.Intent.NONE, DUR);
        assertFalse(p.doubleJumped(), "back on the ground, hers again");
    }

    @Test
    void aJumpGoesHigherAndTheHeavyAttackComesSoonerAndSheIsFaster() {
        Player p = wearing(forged("light", "leather_greaves", Map.of(Gear.Effect.JUMP, 50.0,
                Gear.Effect.HEAVY_COOLDOWN, 40.0, Gear.Effect.SPEED, 20.0)));
        Player plain = new Player(new Wardrobe());
        double peak = 0, plainPeak = 0;
        p.update(DT, JUMP, DUR);
        plain.update(DT, JUMP, DUR);
        for (int i = 0; i < 90; i++) {
            p.update(DT, Player.Intent.NONE, DUR);
            plain.update(DT, Player.Intent.NONE, DUR);
            peak = Math.max(peak, p.height());
            plainPeak = Math.max(plainPeak, plain.height());
        }
        assertEquals(1.5, peak / plainPeak, 0.08, "half as high again");
        assertEquals(Player.HEAVY_COOLDOWN * 0.6, p.heavyCooldown(), 1e-9);
        for (int i = 0; i < 60; i++) {
            p.update(DT, new Player.Intent(1, 0, false, false, false, false), DUR);
            plain.update(DT, new Player.Intent(1, 0, false, false, false, false), DUR);
        }
        assertEquals(1.2, p.speed() / plain.speed(), 0.02);
    }

    @Test
    void fallingOnPastTheJumpShowsTheFall() {
        // a jump higher than its animation lasts: she falls on, in the fall, once it is over
        Player p = wearing(forged("springs", "leather_greaves", Map.of(Gear.Effect.JUMP, 150.0)));
        p.update(DT, JUMP, DUR);
        boolean fell = false;
        for (int i = 0; i < 300 && !fell; i++) {
            p.update(DT, Player.Intent.NONE, DUR);
            fell = p.state() == AnimState.FALL;
        }
        assertTrue(fell, "the fall shows once the jump is over and she is still up");
        assertTrue(AnimState.FALL.loops());
        assertEquals(AnimState.JUMP, AnimState.FALL.fallback());
        assertEquals(AnimState.JUMP, AnimState.DOUBLE_JUMP.fallback());
    }

    @Test
    void theForgesTwoExamplesAreAnArmourPieceAndAGarmentWithGifts() throws java.io.IOException {
        Forged guard = Forged.parse("wyrmguard",
                java.nio.file.Files.readString(java.nio.file.Path.of("assets", "forge", "wyrmguard.json")));
        assertTrue(guard.worn());
        assertTrue(Wardrobe.isArmour(guard.item().def().wearable().folders().get(0)), "a gorget: armour");
        assertEquals(50, guard.gear().get(Gear.Effect.HEALTH), 1e-9);
        assertTrue(guard.effects().isEmpty(), "no borders on armour");
        Forged boots = Forged.parse("springheel_boots",
                java.nio.file.Files.readString(java.nio.file.Path.of("assets", "forge", "springheel_boots.json")));
        assertTrue(boots.worn());
        assertFalse(Wardrobe.isArmour(boots.item().def().wearable().folders().get(0)), "riding boots: clothes");
        assertEquals(1, boots.gear().get(Gear.Effect.DOUBLE_JUMP), 1e-9);
    }

    // --- stamina spent running ----------------------------------------------------------------

    @Test
    void runningTiresHerAndOutOfBreathSheWalks() {
        Player p = new Player(new Wardrobe());
        Player.Intent sprint = new Player.Intent(1, 0, false, true, false, false);
        for (int i = 0; i < 60; i++) p.update(DT, sprint, DUR);
        assertEquals(Player.MAX_STAMINA - Player.SPRINT_COST, p.stamina(), 0.5, "a second's sprint");
        p.setStamina(0.05);
        for (int i = 0; i < 5; i++) p.update(DT, sprint, DUR);
        assertTrue(p.winded());
        for (int i = 0; i < 30; i++) p.update(DT, sprint, DUR);
        assertEquals(AnimState.WALK, p.state().root(), "she walks till it comes back");
        for (int i = 0; i < 180; i++) p.update(DT, Player.Intent.NONE, DUR);
        assertFalse(p.winded());
        for (int i = 0; i < 30; i++) p.update(DT, sprint, DUR);
        assertEquals(AnimState.SPRINT, p.state().root());
        // walking costs nothing
        p.setStamina(50);
        for (int i = 0; i < 60; i++) p.update(DT, new Player.Intent(1, 0, false, false, false, false), DUR);
        assertTrue(p.stamina() >= 50);
    }
}
