package com.larsons.game.world;

import com.larsons.game.actor.Breed;
import com.larsons.game.actor.Folk;
import com.larsons.game.actor.Folks;
import com.larsons.game.actor.People;
import com.larsons.game.combat.Combat;
import com.larsons.game.combat.Dummy;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sneaking: the lights showing her up at night, the stamina a jump, a second
 * jump and the crouch walk cost, the Tailor's sneaking effects, no sneak
 * attacks once anyone has spotted her - and the colour the Forge gives a
 * wand's or a staff's bolts.
 */
class SneakingTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);
    private static final Player.Intent CROUCH = new Player.Intent(0, 0, false, false, false, false, false, true,
            false, false, false, false, false, null);
    private static final Player.Intent CREEP = new Player.Intent(0, 1, false, false, false, false);
    private static final Player.Intent CREEP_FAST = new Player.Intent(0, 1, true, false, false, false);
    private static final Player.Intent JUMP = new Player.Intent(0, 0, false, false, true, false);
    private static final Player.Intent ATTACK = new Player.Intent(0, 0, false, false, false, true);

    @TempDir
    Path dir;

    @BeforeEach
    void forgeHere() {
        Forged.useDir(dir);
    }

    @AfterEach
    void forgeBack() {
        Forged.useDir(Path.of("assets", Forged.FOLDER));
    }

    /** A world at midnight, nothing in it but her (and her sword, if she has one). */
    private static World midnight(boolean sword) {
        Wardrobe wardrobe = new Wardrobe();
        if (sword) wardrobe.set(Slot.CARRY_RIGHT, "sword");
        World w = new World(wardrobe);
        if (sword) w.player().inventory().add(Item.of(ItemDef.SWORD));
        w.equipSelected();
        w.seed(5);
        w.animals().setEnabled(false);
        w.clock().setHour(0);
        return w;
    }

    private static void play(World w, Player.Intent first, Player.Intent then, double seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            w.player().update(DT, i == 0 ? first : then, DUR);
            w.tick(DT);
        }
    }

    /** A piece of her own made in the Tailor from {@code base}, giving {@code gear}. */
    private static Forged piece(String id, String base, Map<Gear.Effect, Double> gear) {
        return new Forged(id, "Forged " + base, base, Rarity.EPIC, Map.of(), 1, 8, -1, Set.of(), List.of(),
                Forged.AMMO_EVERY, List.of(), 0, gear);
    }

    /** {@code p} wearing {@code f} where it goes, and carrying it. */
    private static void wear(Player p, Forged f) {
        p.inventory().add(f.item());
        Slot slot = f.def().wearable().slots().get(0);
        String folder = f.def().wearable().folders().get(0);
        p.wardrobe().wear(slot, Wardrobe.isArmour(folder), folder);
        p.wardrobe().setId(slot, Wardrobe.isArmour(folder), Armour.forgedLook(f));
    }

    /** A street lamp over her head. */
    private static void lampOver(World w) {
        w.lights().add(new World.LightSpot(w.player().ground().add(0, 2.6, 0.4), 9, 0xFFC878, 1, true, true));
    }

    // --- the light --------------------------------------------------------------------------

    @Test
    void atNightALampsLightShowsHerUpAndTheDarkHidesHer() {
        World w = midnight(false);
        play(w, CROUCH, CREEP, 1);
        assertTrue(w.player().crouched());
        Stealth dark = w.stealth();
        assertTrue(dark.hidden(), "creeping in the dark: hidden " + dark.score());
        assertEquals(0, dark.lit(), 1e-9);
        assertFalse(dark.inTheLight());

        lampOver(w);
        play(w, CREEP, CREEP, 0.5);
        Stealth lit = w.stealth();
        assertTrue(lit.lit() > 0.8, "under the lamp: " + lit.lit());
        assertTrue(lit.inTheLight());
        assertFalse(lit.hidden(), "creeping under a lamp: seen " + lit.score());
        assertEquals(0.5, lit.score(), 0.06, "as by day");

        // out past its reach, the dark hides her again
        w.lights().clear();
        w.lights().add(new World.LightSpot(w.player().ground().add(30, 2.6, 0), 9, 0xFFC878, 1, true, true));
        play(w, CREEP, CREEP, 0.5);
        assertTrue(w.stealth().hidden());

        // by day a lamp makes no odds
        w.clock().setHour(12);
        lampOver(w);
        play(w, CREEP, CREEP, 0.5);
        assertEquals(0, w.stealth().lit(), 1e-9);
    }

    @Test
    void herOwnLanternLitShowsHerUpTooAndShadowGearHidesHerFromTheLights() {
        World w = midnight(false);
        Player p = w.player();
        p.inventory().add(Goods.item(Goods.LANTERN));
        p.setLanternLit(true);
        play(w, CROUCH, CREEP, 1);
        assertEquals(1, w.stealth().lit(), 1e-9, "her lantern lights her up");
        assertFalse(w.stealth().hidden());
        p.setLanternLit(false);
        play(w, CREEP, CREEP, 0.5);
        assertTrue(w.stealth().hidden(), "out: the dark hides her");

        // a cloak made with Shadow 100 %: the lamps show her no more than the dark does
        lampOver(w);
        wear(p, piece("shade", "wool_mantle", Map.of(Gear.Effect.SHADOW, 100.0)));
        play(w, CREEP, CREEP, 0.6);
        assertEquals(0, w.stealth().lit(), 1e-9);
        assertTrue(w.stealth().hidden(), "under the lamp, in Shadow");
    }

    @Test
    void stealthAndQuietStepsGearMakeHerHarderToSeeAndToHear() {
        World w = midnight(false);
        play(w, CROUCH, CREEP, 1);
        double score = w.stealth().score(), heard = w.stealth().heard();
        assertEquals(Stealth.HEARS * 0.3, heard, 1e-9, "a creep is heard a metre off");
        wear(w.player(), piece("soft", "shoes", Map.of(Gear.Effect.STEALTH, 40.0,
                Gear.Effect.QUIET_STEPS, 50.0)));
        play(w, CREEP, CREEP, 0.6);
        assertEquals(score * 0.6, w.stealth().score(), 1e-6);
        assertEquals(heard * 0.5, w.stealth().heard(), 1e-6);
        // running is heard far off
        World loud = midnight(false);
        play(loud, new Player.Intent(0, 1, false, true, false, false), new Player.Intent(0, 1, false, true, false,
                false), 1);
        assertTrue(loud.stealth().heard() > 8, "sprinting: " + loud.stealth().heard());
    }

    // --- stamina ----------------------------------------------------------------------------

    @Test
    void aJumpCostsStaminaAndTooTiredSheDoesNotJump() {
        Player p = new Player(new Wardrobe());
        p.update(DT, JUMP, DUR);
        assertEquals(Player.MAX_STAMINA - Player.JUMP_COST, p.stamina(), 1e-9);
        assertEquals(p.stance().jump(), p.state());
        for (int i = 0; i < 120; i++) p.update(DT, Player.Intent.NONE, DUR);
        assertFalse(p.airborne());

        p.setStamina(Player.JUMP_COST - 1);
        p.drainEvents();
        p.update(DT, JUMP, DUR);
        assertNotEquals(p.stance().jump(), p.state(), "too tired to jump");
        assertTrue(p.drainEvents().stream().anyMatch(e -> e.kind() == Player.Event.Kind.TOO_TIRED));
        for (int i = 0; i < 30; i++) p.update(DT, Player.Intent.NONE, DUR);
        assertFalse(p.airborne());
    }

    @Test
    void theSecondJumpCostsStaminaToo() {
        Player p = new Player(new Wardrobe());
        wear(p, piece("springs", "sabatons", Map.of(Gear.Effect.DOUBLE_JUMP, 1.0)));
        p.update(DT, JUMP, DUR);
        for (int i = 0; i < 25; i++) p.update(DT, Player.Intent.NONE, DUR);
        assertTrue(p.canDoubleJump());
        double before = p.stamina();
        p.update(DT, JUMP, DUR);
        assertEquals(AnimState.DOUBLE_JUMP, p.state());
        assertEquals(before - Player.DOUBLE_JUMP_COST, p.stamina(), 0.5);

        // too tired for it: no second jump
        Player tired = new Player(new Wardrobe());
        wear(tired, piece("springs2", "sabatons", Map.of(Gear.Effect.DOUBLE_JUMP, 1.0)));
        tired.update(DT, JUMP, DUR);
        for (int i = 0; i < 25; i++) tired.update(DT, Player.Intent.NONE, DUR);
        tired.setStamina(2);
        tired.update(DT, JUMP, DUR);
        assertNotEquals(AnimState.DOUBLE_JUMP, tired.state());
        assertFalse(tired.doubleJumped());
    }

    @Test
    void sneakingCostsStaminaAndOutOfItSheCreeps() {
        Player p = new Player(new Wardrobe());
        p.update(DT, CROUCH, DUR);
        for (int i = 0; i < 120; i++) p.update(DT, CREEP, DUR);
        assertTrue(p.crouched());
        assertEquals(Player.MAX_STAMINA - 2 * Player.SNEAK_COST, p.stamina(), 0.3, "two seconds' crouch walk");
        double was = p.stamina();
        for (int i = 0; i < 60; i++) p.update(DT, CREEP_FAST, DUR);
        assertEquals(was - Player.SNEAK_FAST_COST, p.stamina(), 0.3, "a second of the fast crouch walk");
        double speed = p.speed();

        // run out, she creeps
        p.setStamina(0.05);
        p.drainEvents();
        for (int i = 0; i < 30; i++) p.update(DT, CREEP, DUR);
        assertTrue(p.winded());
        assertTrue(p.drainEvents().stream().anyMatch(e -> e.kind() == Player.Event.Kind.WINDED));
        assertTrue(p.crouched(), "still crouched");
        assertEquals(AnimState.CROUCH_WALK.speed() * Player.CREEP, p.speed(), 0.05);
        assertTrue(p.speed() < speed);
    }

    @Test
    void sneakStaminaAndSneakSpeedGear() {
        Player plain = new Player(new Wardrobe());
        Player p = new Player(new Wardrobe());
        wear(p, piece("creepers", "shoes", Map.of(Gear.Effect.SNEAK_STAMINA, 100.0,
                Gear.Effect.SNEAK_SPEED, 50.0)));
        plain.update(DT, CROUCH, DUR);
        p.update(DT, CROUCH, DUR);
        for (int i = 0; i < 120; i++) {
            plain.update(DT, CREEP, DUR);
            p.update(DT, CREEP, DUR);
        }
        assertEquals(Player.MAX_STAMINA, p.stamina(), 1e-6, "sneaking costs her nothing");
        assertTrue(plain.stamina() < Player.MAX_STAMINA);
        assertEquals(1.5, p.speed() / plain.speed(), 0.03, "half as fast again crouched");
    }

    // --- sneak attacks: only while nobody has spotted her -----------------------------------

    private static Dummy ahead(World w) {
        return w.addDummy(new Dummy(new Vec3(0, 0, -1.2), SpriteView.headingOf(0, 1), 1000, 6));
    }

    @Test
    void seenByAFoeShesNoSneakAttackNorWatchedByAPerson() {
        World w = midnight(true);
        ahead(w);
        // a foe told to leave her be, a little way ahead, looking at her
        Folk foe = w.folks().spawn(Breed.BANDIT, new Vec3(0.6, 0, -2.6), SpriteView.headingOf(0, 1),
                Folks.Spec.ANY.weapon("sword").size(1)).peaceful();
        play(w, CROUCH, Player.Intent.NONE, 0.8);
        assertTrue(w.stealth().hidden(), "crouched in the dark");
        assertTrue(foe.seesHer());
        assertTrue(w.spotted() > 0);
        assertFalse(w.sneaky());
        play(w, ATTACK, Player.Intent.NONE, 0.05);
        assertFalse(w.combat().show().strike().sneak(), "seen: no sneak attack");

        // a person in the street, watching her
        World town = midnight(true);
        ahead(town);
        town.folks().person(1, People.HUMAN, new Vec3(-0.6, 0, -2.6), SpriteView.headingOf(0, 1), null);
        play(town, CROUCH, Player.Intent.NONE, 0.8);
        assertTrue(town.spotted() > 0);
        play(town, ATTACK, Player.Intent.NONE, 0.05);
        assertFalse(town.combat().show().strike().sneak(), "watched: no sneak attack");
    }

    @Test
    void unseenHerBlowIsASneakAttackHarderWithHerGear() {
        World w = midnight(true);
        ahead(w);
        // a person with her back to her: nobody sees her
        w.folks().person(1, People.HUMAN, new Vec3(0, 0, -6), SpriteView.headingOf(0, -1), null);
        wear(w.player(), piece("gloves", "gloves", Map.of(Gear.Effect.SNEAK_ATTACK, 50.0)));
        play(w, CROUCH, Player.Intent.NONE, 0.8);
        assertEquals(0, w.spotted());
        assertTrue(w.sneaky());
        play(w, ATTACK, Player.Intent.NONE, 0.05);
        Combat.Show show = w.combat().show();
        assertTrue(show.strike().sneak());
        assertEquals(Stealth.SNEAK_DAMAGE * 1.5, show.strike().times(), 1e-9);
        assertTrue(show.notes().stream().anyMatch(n -> n.text().equals("Sneak attack ×3")),
                show.notes().toString());
    }

    // --- the Tailor's sneaking effects -------------------------------------------------------

    @Test
    void theSneakingEffectsAreTheTailorsOwnSaidAndKept() {
        int sneaking = 0;
        for (Gear.Effect e : Gear.Effect.values()) {
            if (!e.sneaking()) continue;
            sneaking++;
            assertEquals(Gear.SNEAK_RGB, e.rgb());
            assertFalse(e.onOff());
            assertSame(e, Gear.Effect.byKey(e.key()));
            assertFalse(Gear.shortly(e, e.fallback()).isBlank());
        }
        assertEquals(6, sneaking);
        assertEquals("-50% light on you", Gear.shortly(Gear.Effect.SHADOW, 50));
        assertEquals("+25% sneak attack damage", Gear.shortly(Gear.Effect.SNEAK_ATTACK, 25));
        // kept in its file, read back the same
        Forged f = piece("hood", "wool_mantle", Map.of(Gear.Effect.STEALTH, 20.0, Gear.Effect.QUIET_STEPS, 40.0));
        Forged back = Forged.parse("hood", Forged.json(f));
        assertEquals(f.gear(), back.gear());
        // and never below what makes sense
        Gear.Totals all = new Gear.Totals(Map.of(Gear.Effect.STEALTH, 300.0, Gear.Effect.QUIET_STEPS, 300.0,
                Gear.Effect.SHADOW, 300.0, Gear.Effect.SNEAK_STAMINA, 300.0), List.of());
        assertEquals(0.25, all.stealthTimes(), 1e-9);
        assertEquals(0.1, all.noiseTimes(), 1e-9);
        assertEquals(0, all.lightTimes(), 1e-9);
        assertEquals(0, all.sneakCostTimes(), 1e-9);
    }

    // --- the Forge: the colour of a wand's bolts ------------------------------------------

    @Test
    void aForgedWandsBoltsAreTheColourTheForgeGivesThem() {
        Forged wand = new Forged(null, "Rosethorn", "wand", Rarity.RARE, Map.of(), 2, 12, -1, Set.of());
        int asIs = Combat.spellColour(wand.item());
        assertEquals(-1, wand.boltRgb());
        Forged pink = wand.withBoltRgb(0xFF5AD2);
        assertEquals(0xFF5AD2, pink.boltRgb());
        assertEquals(0xFF5AD2, Combat.spellColour(pink.item()));
        assertEquals(asIs, Combat.spellColour(pink.withBoltRgb(-1).item()), "back to its tier's or rarity's");
        // kept in its file
        String text = Forged.json(pink.withId("rosethorn"));
        assertTrue(text.contains("\"boltColour\""), text);
        assertEquals(0xFF5AD2, Forged.parse("rosethorn", text).boltRgb());
        assertFalse(Forged.json(wand.withId("plain")).contains("boltColour"), "none kept when it has none");
        // full auto's bolts too, and its other settings kept with it
        Forged auto = pink.withFullAuto(true).withBoltRate(9);
        assertEquals(0xFF5AD2, auto.boltRgb());
        assertEquals(9, auto.boltRate(), 1e-9);
        // a sword shoots nothing: no colour of bolts
        Forged sword = new Forged(null, "Edge", "sword", Rarity.RARE, Map.of(), 2, 8, -1, Set.of()).withBoltRgb(0x00FF00);
        assertEquals(-1, sword.boltRgb());
    }
}
