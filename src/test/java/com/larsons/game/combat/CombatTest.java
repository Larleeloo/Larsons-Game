package com.larsons.game.combat;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Border;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Rarity;
import com.larsons.game.world.Tier;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** The fighting: the dice, swings and shots, mana, and the rare borders' effects. */
class CombatTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);
    private static final Player.Intent ATTACK = new Player.Intent(0, 0, false, false, false, true);
    private static final Player.Intent HEAVY = new Player.Intent(0, 0, false, false, false, false, false, false,
            true, false, false, false, false, null);

    /** A world whose player holds {@code weapon} (null: nothing), her dice rolled from a fixed seed. */
    private static World holding(Item weapon) {
        Wardrobe wardrobe = new Wardrobe();
        if (weapon != null && weapon.is(ItemDef.SWORD)) wardrobe.set(Slot.CARRY_RIGHT, "sword");
        World w = new World(wardrobe);
        if (weapon != null) w.player().inventory().add(weapon);
        w.equipSelected();
        w.seed(42);
        return w;
    }

    /** A dummy {@code metres} straight ahead of her (she faces -z), facing her, that takes {@code health}. */
    private static Dummy ahead(World w, double metres, double health) {
        return w.addDummy(new Dummy(new Vec3(0, 0, -metres), SpriteView.headingOf(0, 1), health, 6));
    }

    /** {@code frames} frames of the player doing {@code first} then nothing, the world following. */
    private static void play(World w, Player.Intent first, int frames) {
        for (int i = 0; i < frames; i++) {
            w.player().update(DT, i == 0 ? first : Player.Intent.NONE, DUR);
            w.tick(DT);
        }
    }

    private static void idle(World w, double seconds) {
        play(w, Player.Intent.NONE, (int) Math.round(seconds / DT));
    }

    // --- the dice -----------------------------------------------------------------------

    @Test
    void eachRarityRollsOneMoreD20ThanTheLast() {
        CombatTuning t = CombatTuning.defaults();
        int n = 1;
        for (Rarity r : Rarity.values()) assertEquals(n++, t.dice(r), r.label());
        assertEquals(20, t.sides);
        assertEquals(1, t.heavyExtra);
    }

    @Test
    void aRollIsItsFacesAndTheirSum() {
        DiceRoll r = DiceRoll.roll(6, 20, new Random(3));
        assertEquals(6, r.count());
        int sum = 0;
        for (int i = 0; i < r.count(); i++) {
            assertTrue(r.face(i) >= 1 && r.face(i) <= 20);
            sum += r.face(i);
        }
        assertEquals(sum, r.total());
        assertEquals("6d20", r.notation());
        int[] seen = new int[21];
        Random random = new Random(9);
        for (int i = 0; i < 4000; i++) seen[DiceRoll.roll(1, 20, random).total()]++;
        for (int f = 1; f <= 20; f++) assertTrue(seen[f] > 100, "face " + f + " comes up");
    }

    @Test
    void theTuningFileOverridesWhatItSaysAndKeepsTheRest() {
        CombatTuning t = CombatTuning.parse("{\"dice\": {\"perRarity\": {\"common\": 2}, \"heavyExtra\": 2},"
                + " \"mana\": {\"max\": 50}}");
        assertEquals(2, t.dice(Rarity.COMMON));
        assertEquals(2, t.dice(Rarity.UNCOMMON));
        assertEquals(6, t.dice(Rarity.MYTHIC));
        assertEquals(2, t.heavyExtra);
        assertEquals(50, t.maxMana);
        assertEquals(10, t.spellCost);
        assertEquals(6, CombatTuning.parse("not json at all").dice(Rarity.MYTHIC));
    }

    @Test
    void theRepositorysTuningFileIsTheDefaults() {
        CombatTuning file = CombatTuning.load(Path.of("assets"));
        CombatTuning d = CombatTuning.defaults();
        for (Rarity r : Rarity.values()) assertEquals(d.dice(r), file.dice(r), r.label());
        assertEquals(d.heavyExtra, file.heavyExtra);
        assertEquals(d.maxMana, file.maxMana);
        assertEquals(d.manaRegen, file.manaRegen);
        assertEquals(d.spellCost, file.spellCost);
        assertEquals(d.mysticBaseCost, file.mysticBaseCost);
        assertEquals(d.arrowSpeed, file.arrowSpeed);
        assertEquals(d.range, file.range);
        assertEquals(d.dummyHealth, file.dummyHealth);
        assertEquals(d.dummyDownSeconds, file.dummyDownSeconds);
    }

    // --- swings -------------------------------------------------------------------------

    @Test
    void aSwordSwingRollsItsDiceAndTheDummyInFrontTakesTheirTotal() {
        World w = holding(Item.of(ItemDef.SWORD));
        Dummy d = ahead(w, 1.2, 1000);
        play(w, ATTACK, 2);
        Combat.Show show = w.combat().show();
        assertNotNull(show, "the dice are up as the swing starts");
        assertEquals(1, show.strike().roll().count(), "a Basic Sword is Common: one die");
        assertEquals(1000, d.health(), "nothing lands before the blow does");
        idle(w, 0.5);
        assertEquals(1000 - show.strike().roll().total(), d.health(), 1e-9);
        assertEquals(Dummy.Phase.HIT, d.phase());
        assertEquals(Dummy.Side.FRONT, d.side());
        assertFalse(w.combat().popups().isEmpty(), "the damage floats up off it");
        assertEquals(String.valueOf(show.strike().roll().total()), w.combat().popups().get(0).text());
    }

    @Test
    void aSwingMissesWhatIsBehindHerOrOutOfReach() {
        World w = holding(Item.of(ItemDef.SWORD));
        Dummy behind = w.addDummy(new Dummy(new Vec3(0, 0, 1.2), 0, 1000, 6));
        Dummy far = ahead(w, 4, 1000);
        play(w, ATTACK, 60);
        assertEquals(1000, behind.health());
        assertEquals(1000, far.health());
        assertNotNull(w.combat().show(), "the dice roll all the same");
    }

    @Test
    void aSpinHitsAllRound() {
        World w = holding(Item.of(ItemDef.SWORD));
        Dummy behind = w.addDummy(new Dummy(new Vec3(0, 0, 1.2), 0, 1000, 6));
        Dummy side = w.addDummy(new Dummy(new Vec3(1.2, 0, 0), 0, 1000, 6));
        play(w, new Player.Intent(0, 0, false, false, false, false, false, false, false, true, false, false,
                false, null), 60);
        assertTrue(behind.health() < 1000);
        assertTrue(side.health() < 1000);
    }

    @Test
    void reachAndArc() {
        Dummy d = new Dummy(new Vec3(0, 0, -1.5), 0, 100, 6);
        assertTrue(Combat.inReach(Vec3.ZERO, 0, d, 1.7, 120));
        assertFalse(Combat.inReach(Vec3.ZERO, Math.PI, d, 1.7, 120), "facing away");
        assertTrue(Combat.inReach(Vec3.ZERO, Math.PI, d, 1.7, 360), "a spin");
        assertFalse(Combat.inReach(Vec3.ZERO, 0, d, 1.0, 120), "out of reach (its edge is 1.1 off)");
        assertTrue(Combat.inReach(Vec3.ZERO, Math.toRadians(70), d, 1.7, 120),
                "70 degrees off: inside the arc's 60 and its body's width");
        assertFalse(Combat.inReach(Vec3.ZERO, Math.toRadians(100), d, 1.7, 120));
    }

    @Test
    void aHeavyAttackRollsOneDieMore() {
        World w = holding(Item.of(ItemDef.BATTLE_AXE, Tier.CRYSTAL));
        play(w, ATTACK, 2);
        assertEquals(4, w.combat().show().strike().roll().count(), "a Crystal Axe is Epic: 4d20");
        idle(w, 1.5);
        play(w, HEAVY, 2);
        assertEquals(AnimState.AXE_HEAVY_ATTACK, w.player().state());
        assertEquals(5, w.combat().show().strike().roll().count());
    }

    @Test
    void emptyHandedSheSwingsOneCommonDie() {
        World w = holding(null);
        play(w, ATTACK, 2);
        assertEquals(1, w.combat().show().strike().roll().count());
        assertEquals(Rarity.COMMON, w.combat().show().strike().rarity());
    }

    @Test
    void parriesBlocksAndTheBowsDrawRollNothing() {
        Item axe = Item.of(ItemDef.BATTLE_AXE);
        assertNull(Move.of(AnimState.AXE_PARRY, axe));
        assertNull(Move.of(AnimState.BOW_DRAW, Item.of(ItemDef.LONGBOW)));
        assertNull(Move.of(AnimState.PICKUP, axe));
        assertNull(Move.of(AnimState.EMOTE_LAUGH, axe));
        assertNull(Move.of(AnimState.IDLE, axe));
    }

    @Test
    void everyAttackAndShotHasAMove() {
        for (AnimState s : AnimState.values()) {
            String k = s.key();
            boolean attack = (k.endsWith("attack") || k.endsWith("_fire") || k.equals("shield_bash"));
            Move m = Move.of(s, Item.of(ItemDef.CRYSTAL_STAFF, Tier.MAGIC));
            assertEquals(attack, m != null, k);
            if (m != null) assertTrue(m.at() >= 0 && m.at() < 1, k);
        }
        assertEquals(Move.Kind.ARROW, Move.of(AnimState.BOW_FIRE, Item.of(ItemDef.LONGBOW)).kind());
        assertEquals(Move.Kind.BOLT, Move.of(AnimState.CROSSBOW_CROUCH_FIRE, Item.of(ItemDef.CROSSBOW)).kind());
        assertTrue(Move.of(AnimState.AXE_HEAVY_ATTACK, null).heavy());
        assertEquals(360, Move.of(AnimState.SPIN_ATTACK, null).arc());
    }

    // --- staffs, wands and mana ---------------------------------------------------------------

    @Test
    void aMagicStaffCastsBlueAHonedStaffYellowAndABasicStaffThrusts() {
        assertTrue(Combat.casts(Item.of(ItemDef.CRYSTAL_STAFF, Tier.MAGIC)));
        assertTrue(Combat.casts(Item.of(ItemDef.CRYSTAL_STAFF, Tier.HONED)));
        assertTrue(Combat.casts(Item.of(ItemDef.CRYSTAL_STAFF, Tier.MYTHIC)));
        assertFalse(Combat.casts(Item.of(ItemDef.CRYSTAL_STAFF, Tier.BASIC)));
        assertFalse(Combat.casts(Item.of(ItemDef.BATTLE_AXE, Tier.MYTHIC)));
        assertEquals(0x5D86FA, Combat.spellColour(Item.of(ItemDef.CRYSTAL_STAFF, Tier.MAGIC)));
        assertEquals(0xF9C36D, Combat.spellColour(Item.of(ItemDef.CRYSTAL_STAFF, Tier.HONED)));
        assertEquals(Rarity.LEGENDARY.rgb(), Combat.spellColour(Item.of(ItemDef.CRYSTAL_STAFF, Tier.PERFECT)));
        assertEquals(Move.Kind.SPELL, Move.of(AnimState.STAFF_ATTACK, Item.of(ItemDef.CRYSTAL_STAFF, Tier.MAGIC)).kind());
        assertEquals(Move.Kind.SWING, Move.of(AnimState.STAFF_ATTACK, Item.of(ItemDef.CRYSTAL_STAFF, Tier.BASIC)).kind());
    }

    @Test
    void aWandCastsWhateverItsAttackIsCalled() {
        ItemDef wand = new ItemDef("wand", "Wand", Slot.CARRY_RIGHT, 0.6, null, Stance.STAFF);
        Item magic = Item.of(wand, Tier.MAGIC);
        assertTrue(Combat.casts(magic));
        assertEquals(Move.Kind.SPELL, Move.of(AnimState.STAFF_ATTACK, magic).kind());
        assertEquals(Move.Kind.SWING, Move.of(AnimState.STAFF_HEAVY_ATTACK, magic).kind());
    }

    @Test
    void aSpellCostsManaFliesAndLandsItsRoll() {
        World w = holding(Item.of(ItemDef.CRYSTAL_STAFF, Tier.MAGIC));
        Dummy d = ahead(w, 5, 1000);
        Combat c = w.combat();
        play(w, ATTACK, 2);
        assertEquals(c.maxMana() - c.tuning().spellCost, c.mana(), 0.5);
        assertEquals(3, c.show().strike().roll().count(), "a Magic Staff is Rare: 3d20");
        idle(w, 0.42);
        assertEquals(1, c.projectiles().size(), "the bolt is away");
        Projectile bolt = c.projectiles().get(0);
        assertEquals(Move.Kind.SPELL, bolt.kind());
        assertEquals(0x5D86FA, bolt.colour());
        assertEquals(1000, d.health());
        idle(w, 0.6);
        assertTrue(c.projectiles().isEmpty(), "it struck");
        assertEquals(1000 - c.show().strike().roll().total(), d.health(), 1e-9);
        assertTrue(c.popups().stream().anyMatch(p -> p.text().equals(String.valueOf(c.show().strike().roll().total()))),
                "its damage floats up off the dummy");
        assertTrue(c.effects().stream().anyMatch(e -> e.kind() == Combat.Effect.Kind.BURST));
    }

    @Test
    void withoutTheManaASpellFizzles() {
        World w = holding(Item.of(ItemDef.CRYSTAL_STAFF, Tier.MAGIC));
        Dummy d = ahead(w, 3, 1000);
        w.combat().setMana(4);
        play(w, ATTACK, 30);
        assertTrue(w.combat().popups().stream().anyMatch(p -> p.text().equals("Not enough mana")));
        assertNull(w.combat().show(), "no dice for a spell that never was");
        idle(w, 1);
        assertTrue(w.combat().projectiles().isEmpty());
        assertEquals(1000, d.health());
    }

    @Test
    void manaFillsBackUp() {
        World w = holding(null);
        w.combat().setMana(0);
        idle(w, 2);
        assertEquals(2 * w.combat().tuning().manaRegen, w.combat().mana(), 0.2);
        idle(w, 60);
        assertEquals(w.combat().maxMana(), w.combat().mana(), 1e-9);
    }

    // --- shots -------------------------------------------------------------------------------

    @Test
    void anArrowArcsOntoTheDummyItIsAimedAt() {
        World w = holding(Item.of(ItemDef.LONGBOW));
        Dummy d = w.addDummy(new Dummy(new Vec3(1.5, 0, -12), 0, 1000, 6));   // 7 degrees off where she faces
        Player p = w.player();
        Player.Intent draw = new Player.Intent(0, 0, false, false, false, true, true, false, false, false,
                false, false, false, null);
        Player.Intent hold = new Player.Intent(0, 0, false, false, false, false, true, false, false, false,
                false, false, false, null);
        p.update(DT, draw, DUR);
        w.tick(DT);
        for (int i = 0; i < 60; i++) {
            p.update(DT, hold, DUR);
            w.tick(DT);
        }
        assertNull(w.combat().show(), "drawing the bow rolls nothing");
        play(w, Player.Intent.NONE, 3);                                         // let go
        assertEquals(AnimState.BOW_FIRE, p.state());
        assertEquals(1, w.combat().projectiles().size());
        assertEquals(Move.Kind.ARROW, w.combat().projectiles().get(0).kind());
        idle(w, 1.0);
        assertEquals(1000 - w.combat().show().strike().roll().total(), d.health(), 1e-9, "aim assist found it");
    }

    @Test
    void anArrowThatMissesSticksInTheGroundAWhile() {
        World w = holding(Item.of(ItemDef.CROSSBOW));
        Combat c = w.combat();
        play(w, ATTACK, 30);
        assertEquals(1, c.projectiles().size());
        idle(w, 2);
        Projectile bolt = c.projectiles().get(0);
        assertTrue(bolt.stuck(), "come down in the grass");
        assertEquals(0, bolt.position().y(), 1e-9);
        assertTrue(bolt.position().horizontalLength() > 10, "a long way off");
        idle(w, c.tuning().stuckSeconds + 0.5);
        assertTrue(c.projectiles().isEmpty(), "and then gone");
    }

    @Test
    void aimingAShotFromAboveComesDownOnItsMark() {
        Vec3 from = new Vec3(0, 1.35, 0), to = new Vec3(0, 1.0, -20);
        Vec3 v = Projectile.aim(Move.Kind.ARROW, from, to, 30);
        double t = 20 / 30.0;
        assertEquals(to.y(), from.y() + v.y() * t - 0.5 * Projectile.GRAVITY * t * t, 1e-9);
        assertEquals(-20, v.z() * t, 1e-9);
        Vec3 s = Projectile.aim(Move.Kind.SPELL, from, to, 16);
        assertEquals(16, s.length(), 1e-9);
    }

    @Test
    void aShotPassesOverAKnockedDownDummy() {
        Dummy d = new Dummy(new Vec3(0, 0, -5), 0, 10, 6);
        d.hurt(10, Vec3.ZERO, true);
        assertFalse(d.alive());
        assertNull(Combat.struck(new Vec3(0, 1, 0), new Vec3(0, 1, -10), List.of(d)));
        Dummy up = new Dummy(new Vec3(0, 0, -5), 0, 10, 6);
        assertSame(up, Combat.struck(new Vec3(0, 1, 0), new Vec3(0, 1, -10), List.of(up)));
        assertNull(Combat.struck(new Vec3(0, 2.5, 0), new Vec3(0, 2.5, -10), List.of(up)), "over its head");
    }

    // --- the borders ---------------------------------------------------------------------------

    @Test
    void mysticIsTwoAndAHalfTimesTheDamageForTwiceTheMana() {
        World w = holding(Item.of(ItemDef.BATTLE_AXE, Tier.CRYSTAL).withBorder(Border.MYSTIC));
        Dummy d = ahead(w, 1.3, 1000);
        Combat c = w.combat();
        play(w, ATTACK, 2);
        assertEquals(c.maxMana() - Border.MYSTIC_MANA * c.tuning().mysticBaseCost, c.mana(), 0.5);
        assertTrue(c.show().strike().mystic());
        idle(w, 1.5);
        assertEquals(1000 - Math.round(c.show().strike().roll().total() * Border.MYSTIC_DAMAGE), d.health(), 1e-9);

        World staff = holding(Item.of(ItemDef.CRYSTAL_STAFF, Tier.MAGIC).withBorder(Border.MYSTIC));
        assertEquals(2 * staff.combat().tuning().spellCost,
                staff.combat().manaCost(staff.wielded(), Move.of(AnimState.STAFF_ATTACK, staff.wielded())), 1e-9);
    }

    @Test
    void aMysticWeaponShortOfManaHitsAsAPlainOne() {
        World w = holding(Item.of(ItemDef.BATTLE_AXE, Tier.CRYSTAL).withBorder(Border.MYSTIC));
        Dummy d = ahead(w, 1.3, 1000);
        w.combat().setMana(3);
        play(w, ATTACK, 2);
        assertFalse(w.combat().show().strike().mystic());
        idle(w, 1.5);
        assertEquals(1000 - w.combat().show().strike().roll().total(), d.health(), 1e-9);
    }

    @Test
    void venomDealsHalfAsMuchAgainOverTenSeconds() {
        World w = holding(Item.of(ItemDef.BATTLE_AXE, Tier.CRYSTAL).withBorder(Border.VENOM));
        Dummy d = ahead(w, 1.3, 10000);
        play(w, ATTACK, 2);
        int hit = w.combat().show().strike().roll().total();
        idle(w, 0.6);
        assertEquals(10000 - hit, d.health(), 1e-9, "the blow");
        idle(w, 5);
        assertEquals(10000 - hit - hit * 0.25, d.health(), 1e-6, "half the poison five seconds on");
        idle(w, 6);
        assertEquals(10000 - hit * (1 + Border.VENOM_SHARE), d.health(), 1e-6, "100 becomes 150");
        assertTrue(w.combat().popups().stream().noneMatch(p -> !p.small() && p.rgb() == Border.VENOM.rgb()));
    }

    @Test
    void bondedIsTenPercentMoreForEachLivingThingTheWeaponHasHit() {
        Item axe = Item.of(ItemDef.BATTLE_AXE, Tier.SHARPER).withBorder(Border.BONDED);
        World w = holding(axe);
        Combat c = w.combat();
        Dummy a = w.addDummy(new Dummy(new Vec3(0, 0, -1.2), 0, 1000, 6));
        Dummy b = w.addDummy(new Dummy(new Vec3(30, 0, 0), 0, 1000, 6));
        Dummy e = w.addDummy(new Dummy(new Vec3(-30, 0, 0), 0, 1000, 6));
        assertEquals(1, c.bondedCount(axe, a), "the one it is hitting counts");
        play(w, ATTACK, 2);
        int first = c.show().strike().roll().total();
        idle(w, 1.5);
        assertEquals(1000 - Math.round(first * 1.1), a.health(), 1e-9);

        // the same axe strikes b and e elsewhere: three alive, +30%
        w.player().setPosition(new Vec3(30, 0, 1.2));
        play(w, ATTACK, 2);
        idle(w, 1.5);
        w.player().setPosition(new Vec3(-30, 0, 1.2));
        play(w, ATTACK, 2);
        int third = c.show().strike().roll().total();
        idle(w, 1.5);
        assertEquals(1000 - Math.round(third * 1.3), e.health(), 1e-9, "a, b and e itself");
        w.player().setPosition(Vec3.ZERO);
        assertEquals(3, c.bondedCount(axe, a));
        e.hurt(5000, null, false);
        assertFalse(e.alive(), "e has been knocked down");
        assertEquals(2, c.bondedCount(axe, a), "and no longer counts");
        double before = a.health();
        play(w, ATTACK, 2);
        int roll = c.show().strike().roll().total();
        idle(w, 1.5);
        assertEquals(before - Math.round(roll * 1.2), a.health(), 1e-9);
    }

    @Test
    void bondedForgetsAfterFiveMinutes() {
        Item axe = Item.of(ItemDef.BATTLE_AXE).withBorder(Border.BONDED);
        World w = holding(axe);
        Dummy a = ahead(w, 1.2, 1000);
        Dummy far = w.addDummy(new Dummy(new Vec3(40, 0, 0), 0, 1000, 6));
        play(w, ATTACK, 60);
        assertEquals(1, w.combat().bondedCount(axe, null));
        assertEquals(2, w.combat().bondedCount(axe, far));
        idle(w, Border.BONDED_SECONDS + 1);
        assertEquals(0, w.combat().bondedCount(axe, null));
        assertTrue(a.alive());
    }

    // --- the dummy ------------------------------------------------------------------------------

    @Test
    void aDummyRocksFromTheSideItWasHitOn() {
        Dummy d = new Dummy(Vec3.ZERO, 0, 100, 6);              // its face looks along -z, its right is +x
        assertEquals(Dummy.Side.FRONT, d.sideStruck(new Vec3(0, 0, -2)));
        assertEquals(Dummy.Side.BACK, d.sideStruck(new Vec3(0.3, 0, 2)));
        assertEquals(Dummy.Side.RIGHT, d.sideStruck(new Vec3(2, 0, -0.5)));
        assertEquals(Dummy.Side.LEFT, d.sideStruck(new Vec3(-2, 0, 0.5)));
        d.hurt(10, new Vec3(-2, 0, 0), true);
        assertEquals("hit_left", d.sheet());
        assertEquals(1, d.flash(), 1e-9);
    }

    @Test
    void knockedDownADummyLiesAWhileAndSpringsBackUpWhole() {
        Dummy d = new Dummy(Vec3.ZERO, 0, 50, 6);
        d.hurt(30, new Vec3(0, 0, 2), true);
        assertEquals(Dummy.Phase.HIT, d.phase());
        assertEquals("hit_back", d.sheet());
        tick(d, 1);
        assertEquals(Dummy.Phase.IDLE, d.phase());
        assertEquals("idle", d.sheet());
        d.hurt(30, new Vec3(0, 0, -2), true);
        assertEquals(Dummy.Phase.FALL, d.phase());
        assertEquals("fall_front", d.sheet());
        assertFalse(d.alive());
        d.hurt(30, new Vec3(0, 0, 2), true);
        assertEquals("fall_front", d.sheet(), "it is already going over");
        tick(d, 1);
        assertEquals(Dummy.Phase.DOWN, d.phase());
        assertEquals("down_front", d.sheet());
        tick(d, 6);
        assertEquals(Dummy.Phase.RISE, d.phase());
        assertEquals(0, d.health());
        tick(d, 1);
        assertEquals(Dummy.Phase.IDLE, d.phase());
        assertEquals(50, d.health());
        assertTrue(d.alive());
    }

    @Test
    void poisonWearsADummyDownWithoutRockingIt() {
        Dummy d = new Dummy(Vec3.ZERO, 0, 50, 6);
        d.hurt(5, new Vec3(0, 0, 2), false);
        assertEquals(Dummy.Phase.IDLE, d.phase());
        assertEquals(45, d.health());
        d.hurt(50, new Vec3(0, 0, 2), false);
        assertEquals(Dummy.Phase.FALL, d.phase(), "but it does fall when it runs out");
        assertEquals("fall_back", d.sheet());
    }

    private static void tick(Dummy d, double seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) d.tick(DT);
    }

    // --- colours ---------------------------------------------------------------------------------

    @Test
    void aDieIsItsRaritysColourWithItsBorderRoundTheRim() {
        int[] rare = CombatColours.diePalette(Rarity.RARE, null);
        assertEquals(0, rare[0] >>> 24, "slot 0 is clear");
        assertEquals(0xFF000000 | 0x5D86FA, rare[4], "the light faces are Rare's blue");
        assertEquals(0xFF000000 | Border.VENOM.rgb(), CombatColours.diePalette(Rarity.RARE, Border.VENOM)[1]);
        assertSame(rare, CombatColours.diePalette(Rarity.RARE, null), "the same palette every frame");
        for (Rarity r : Rarity.values()) assertEquals(6, CombatColours.die(r, null).length);
    }

    @Test
    void aSpellSheetIsRecolouredKeepingItsGlowSeeThrough() {
        int[] sheet = new int[256];
        sheet[1] = 0xFF140B7D;
        sheet[2] = 0x78302AE5;
        int[] yellow = CombatColours.spellPalette(sheet, 0xF9C36D);
        assertEquals(0xFF000000 | CombatColours.YELLOW[0], yellow[1]);
        assertEquals(0x78000000 | CombatColours.YELLOW[1], yellow[2]);
        assertEquals(0, yellow[0]);
    }

    @Test
    void damageReadsAsAWholeNumberExceptAPoisonTick() {
        assertEquals("37", Combat.format(37));
        assertEquals("0.5", Combat.format(0.5));
        assertEquals("2", Combat.format(2.0));
        assertEquals("13", Combat.format(12.6));
    }
}
