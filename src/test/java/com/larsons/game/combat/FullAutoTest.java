package com.larsons.game.combat;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Border;
import com.larsons.game.world.Forged;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Rarity;
import com.larsons.game.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full auto: a wand or a staff forged to stream bolts of magic while the
 * attack is held, standing or on the move; what every weapon's attacks cost
 * now - a wand's and a staff's mana (never stamina), a bow's and a
 * crossbow's nothing, a blade's stamina - and the Forge's share of each.
 */
class FullAutoTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);
    private static final Player.Intent PRESS = new Player.Intent(0, 0, false, false, false, true, true, false,
            false, false, false, false, false, null);
    private static final Player.Intent HOLD = new Player.Intent(0, 0, false, false, false, false, true, false,
            false, false, false, false, false, null);
    private static final Player.Intent ATTACK = new Player.Intent(0, 0, false, false, false, true);
    private static final Player.Intent HEAVY = new Player.Intent(0, 0, false, false, false, false, false, false,
            true, false, false, false, false, null);

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

    /** A forged {@code base} (a wand, a staff, a sword ...), Rare, rolling two of its kind's dice. */
    private static Forged forged(String base) {
        return new Forged(null, "Sparkcaster", base, Rarity.RARE, Map.of(), 2,
                CombatTuning.SIDES.getOrDefault(base, 12), -1, Set.of());
    }

    private static World holding(Item weapon) {
        World w = new World(new Wardrobe());
        w.player().inventory().add(weapon);
        w.equipSelected();
        w.seed(7);
        w.animals().setEnabled(false);
        return w;
    }

    /** {@code seconds} of {@code in} held (its first frame {@code first}), counting the spells sent off. */
    private static int run(World w, Player.Intent first, Player.Intent in, double seconds, Set<Projectile> seen) {
        int frames = (int) Math.round(seconds / DT), sent = 0;
        for (int i = 0; i < frames; i++) {
            w.player().update(DT, i == 0 ? first : in, DUR);
            w.tick(DT);
            for (Projectile p : w.combat().projectiles()) {
                if (p.kind() == Move.Kind.SPELL && seen.add(p)) sent++;
            }
        }
        return sent;
    }

    private static Set<Projectile> seen() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    // --- full auto ------------------------------------------------------------------------------

    @Test
    void heldItStreamsBoltsAtItsRateStandingInTheCastsPose() {
        World w = holding(forged("wand").withFullAuto(true).item());
        Player p = w.player();
        Combat c = w.combat();
        assertTrue(p.fullAuto());
        int sent = run(w, PRESS, HOLD, 1.0, seen());
        assertTrue(sent >= 6 && sent <= 7, "six a second (the first the moment it is held): " + sent);
        assertNull(p.action(), "no cast is started: nothing roots her");
        assertTrue(p.autoFiring());
        assertEquals(AnimState.WAND_ATTACK, p.state(), "standing, she holds the cast's pose");
        assertEquals(Player.MAX_STAMINA, p.stamina(), 1e-9, "a wand costs no stamina");
        double drawn = sent * c.tuning().autoBoltCost - c.tuning().manaRegen * 1.0;
        assertEquals(c.maxMana() - drawn, c.mana(), 0.6, "each bolt its mana");
        for (Projectile pr : c.projectiles()) assertEquals(Combat.BOLT_SCALE, pr.scale(), 1e-9, "smaller than a spell");
        assertNotNull(c.show(), "the last bolt's dice are up");
        // let go: no more
        Set<Projectile> after = seen();
        after.addAll(c.projectiles());
        assertEquals(0, run(w, Player.Intent.NONE, Player.Intent.NONE, 0.5, after));
        assertFalse(p.autoFiring());
    }

    @Test
    void theRateOfFireIsTheForgesAndTappingIsNoQuicker() {
        World w = holding(forged("crystal_staff").withFullAuto(true).withBoltRate(12).item());
        int sent = run(w, PRESS, HOLD, 1.0, seen());
        assertTrue(sent >= 12 && sent <= 13, "twelve a second: " + sent);

        World slow = holding(forged("wand").withFullAuto(true).withBoltRate(2).item());
        Set<Projectile> s = seen();
        int tapped = 0;
        for (int i = 0; i < 10; i++) {
            tapped += run(slow, PRESS, HOLD, 0.05, s);
            tapped += run(slow, Player.Intent.NONE, Player.Intent.NONE, 0.05, s);
        }
        assertTrue(tapped <= 3, "ten taps in a second at two a second: no more than its rate, " + tapped);
    }

    @Test
    void itFiresOnTheMoveAndTheBoltsHitWhatTheyreAimedAt() {
        World w = holding(forged("wand").withFullAuto(true).item());
        Player p = w.player();
        Dummy d = w.addDummy(new Dummy(new Vec3(0, 0, -9), SpriteView.headingOf(0, 1), 100000, 6));
        Player.Intent walkAndFire = new Player.Intent(0, -1, false, false, false, true, true, false, false, false,
                false, false, false, null);
        Player.Intent walking = new Player.Intent(0, -1, false, false, false, false, true, false, false, false,
                false, false, false, null);
        int sent = run(w, walkAndFire, walking, 0.8, seen());
        assertTrue(sent >= 4, "bolts on the move: " + sent);
        assertTrue(p.feet().z() < -0.8, "she walked on as she fired: " + p.feet());
        assertEquals(AnimState.WAND_WALK, p.state(), "her walk, not a cast");
        run(w, Player.Intent.NONE, Player.Intent.NONE, 1.0, seen());
        assertTrue(d.health() < 100000, "the bolts struck the dummy ahead");
    }

    @Test
    void withoutManaNothingGoesAndItSaysSoOnce() {
        World w = holding(forged("wand").withFullAuto(true).item());
        Combat c = w.combat();
        c.setMana(0);
        // (the pool fills 5 a second: a bolt of 2 now and then)
        int sent = run(w, PRESS, HOLD, 0.3, seen());
        assertEquals(0, sent, "no mana, no bolt");
        assertEquals(1, c.popups().stream().filter(pp -> pp.text().equals("Not enough mana")).count(),
                "said once, not every frame");
    }

    @Test
    void itsHeavyAttackStillPlaysAndElseItCastsNothingButBolts() {
        World w = holding(forged("wand").withFullAuto(true).item());
        Player p = w.player();
        run(w, HEAVY, Player.Intent.NONE, 0.1, seen());
        assertEquals(AnimState.WAND_HEAVY_ATTACK, p.state(), "the heavy key still brings it down");
        World single = holding(forged("wand").item());
        assertFalse(single.player().fullAuto(), "forged without full auto: one spell a press");
        run(single, ATTACK, Player.Intent.NONE, 0.05, seen());
        assertEquals(AnimState.WAND_ATTACK, single.player().action());
    }

    @Test
    void aCrystalsUseLastsSixBolts() {
        World w = holding(forged("wand").withFullAuto(true).item());
        w.ammo().setEnabled(true);
        w.player().inventory().add(Item.of(com.larsons.game.world.Supplies.SMALL_CRYSTAL));
        Item wand = w.wielded();
        int sent = run(w, PRESS, HOLD, 1.0, seen());
        int uses = (sent + Combat.BOLTS_A_USE - 1) / Combat.BOLTS_A_USE;
        assertEquals(com.larsons.game.world.Ammo.every(wand) - uses, w.ammo().left(wand),
                sent + " bolts: a use every six");
    }

    @Test
    void onlyAWandOrAStaffFiresFullAuto() {
        assertFalse(forged("sword").withFullAuto(true).fullAuto());
        assertFalse(forged("longbow").withFullAuto(true).fullAuto());
        assertTrue(forged("wand").withFullAuto(true).fullAuto());
        assertTrue(forged("crystal_staff").withFullAuto(true).fullAuto());
        assertTrue(Combat.casts(forged("crystal_staff").item()), "a forged staff casts (it thrust before)");
    }

    // --- what it costs ----------------------------------------------------------------------------

    @Test
    void aWandOrAStaffDrawsManaNotStaminaAndTheForgeScalesIt() {
        World w = holding(forged("wand").item());
        Combat c = w.combat();
        Player p = w.player();
        run(w, ATTACK, Player.Intent.NONE, 1.0, seen());
        assertEquals(Player.MAX_STAMINA, p.stamina(), 1e-9, "a spell costs no stamina");
        assertEquals(c.maxMana(), c.mana() + c.tuning().spellCost - c.tuning().manaRegen * 1.0, 0.3);

        // its heavy blow (no spell in it) draws a heavy blow's worth of mana instead
        c.setMana(c.maxMana());
        run(w, HEAVY, Player.Intent.NONE, 0.1, seen());
        assertEquals(Player.MAX_STAMINA, p.stamina(), 1e-9);
        assertEquals(c.maxMana() - c.tuning().staminaHeavy, c.mana(), 0.6);

        Item half = forged("crystal_staff").withManaUse(0.5).item();
        Move cast = Move.of(AnimState.STAFF_ATTACK, half);
        assertEquals(Move.Kind.SPELL, cast.kind());
        assertEquals(c.tuning().spellCost / 2, c.manaCost(half, cast), 1e-9, "half the mana");
        Item free = forged("wand").withManaUse(0).item();
        assertEquals(0, c.manaCost(free, Move.of(AnimState.WAND_ATTACK, free)), 1e-9, "none at all");
        Item auto = forged("wand").withFullAuto(true).withManaUse(2).item();
        assertEquals(2 * c.tuning().autoBoltCost, c.boltCost(auto), 1e-9, "a bolt's, doubled");
        Item mystic = new Forged(null, "M", "wand", Rarity.RARE, Map.of(), 1, 12, -1, Set.of(Border.MYSTIC))
                .withFullAuto(true).item();
        assertEquals(Border.MYSTIC_MANA * c.tuning().autoBoltCost, c.boltCost(mystic), 1e-9, "Mystic: twice");
    }

    @Test
    void aWandsBlowWithNoManaForItIsATiredOne() {
        World w = holding(forged("wand").item());
        w.combat().setMana(0);
        run(w, HEAVY, Player.Intent.NONE, 0.05, seen());
        Combat.Show show = w.combat().show();
        assertNotNull(show);
        assertEquals(w.combat().tuning().tiredDamage, show.strike().times(), 1e-9);
        assertTrue(show.notes().stream().anyMatch(n -> n.text().startsWith("No mana")));
    }

    @Test
    void aBowAndACrossbowCostNoStamina() {
        World bow = holding(Item.of(ItemDef.LONGBOW));
        Player.Intent hold = HOLD;
        run(bow, PRESS, hold, 1.0, seen());
        run(bow, Player.Intent.NONE, Player.Intent.NONE, 0.5, seen());
        assertEquals(1, bow.combat().projectiles().size(), "loosed");
        assertEquals(Player.MAX_STAMINA, bow.player().stamina(), 1e-9, "a bow costs nothing");

        World crossbow = holding(Item.of(ItemDef.CROSSBOW));
        run(crossbow, ATTACK, Player.Intent.NONE, 0.6, seen());
        assertEquals(1, crossbow.combat().projectiles().size());
        assertEquals(Player.MAX_STAMINA, crossbow.player().stamina(), 1e-9, "nor does a crossbow");
        assertEquals(0, crossbow.combat().staminaCost(Item.of(ItemDef.CROSSBOW),
                Move.of(AnimState.CROSSBOW_FIRE, Item.of(ItemDef.CROSSBOW)), AnimState.CROSSBOW_FIRE), 1e-9);
    }

    @Test
    void theForgeSetsASwordsAMacesAndAnAxesStamina() {
        CombatTuning t = CombatTuning.defaults();
        for (String base : new String[]{"sword", "mace", "battle_axe", "dagger"}) {
            Item twice = forged(base).withStaminaUse(2).item();
            Move swing = new Move(Move.Kind.SWING, 0.4, 1.7, 120, false);
            assertEquals(2 * t.staminaSwing, Combat.staminaCost(t, twice, swing, AnimState.ATTACK), 1e-9, base);
            Item none = forged(base).withStaminaUse(0).item();
            assertEquals(0, Combat.staminaCost(t, none, swing, AnimState.ATTACK), 1e-9, base);
        }
        // in a fight: a sword forged to cost half
        World w = holding(forged("sword").withStaminaUse(0.5).item());
        run(w, ATTACK, Player.Intent.NONE, 0.05, seen());
        assertEquals(Player.MAX_STAMINA - t.staminaSwing / 2, w.player().stamina(), 1e-6);
        assertEquals("3 stamina a swing · 7 a heavy attack",
                WeaponStats.costs(forged("sword").withStaminaUse(0.5).item(), t));
    }

    @Test
    void theStatsSayWhatEachAttackCosts() {
        CombatTuning t = CombatTuning.defaults();
        assertEquals("10 mana a spell · no stamina", WeaponStats.costs(forged("wand").item(), t));
        assertEquals("2 mana a bolt · 12 a second", WeaponStats.costs(forged("wand").withFullAuto(true).item(), t));
        assertEquals("No stamina: a bow costs none", WeaponStats.costs(Item.of(ItemDef.LONGBOW), t));
        assertEquals("6 stamina a swing · 14 a heavy attack", WeaponStats.costs(Item.of(ItemDef.SWORD), t));
        WeaponStats.Dps auto = WeaponStats.of(forged("wand").withFullAuto(true).withBoltRate(10).item(), t);
        assertEquals(0.1, auto.cycle(), 1e-9, "a bolt every tenth of a second");
        assertTrue(auto.line().contains("a bolt every"));
    }

    // --- kept ---------------------------------------------------------------------------------------

    @Test
    void itsHandlingIsKeptInItsFile() throws Exception {
        Forged f = Forged.save(forged("wand").withFullAuto(true).withBoltRate(9.5).withManaUse(0.75));
        Forged back = Forged.byId(f.id());
        assertTrue(back.fullAuto());
        assertEquals(9.5, back.boltRate(), 1e-9);
        assertEquals(0.75, back.manaUse(), 1e-9);
        Forged sword = Forged.byId(Forged.save(forged("sword").withStaminaUse(1.5)).id());
        assertEquals(1.5, sword.staminaUse(), 1e-9);
        assertEquals(Forged.Handling.STANDARD, Forged.byId(Forged.save(forged("mace")).id()).handling(),
                "one used as its kind is says nothing of it");
        // clamped to what the Forge offers
        assertEquals(Forged.MAX_BOLTS, forged("wand").withBoltRate(99).boltRate(), 1e-9);
        assertEquals(Forged.MAX_USE, forged("wand").withManaUse(9).manaUse(), 1e-9);
        assertEquals(0, forged("sword").withStaminaUse(-1).staminaUse(), 1e-9);
        // its tiers keep it (they once dropped its rate of fire and accuracy)
        Forged tiered = forged("wand").withFullAuto(true).withFireRate(2).withAccuracy(70)
                .withUpgrades(java.util.List.of(new Forged.Upgrade("", Rarity.EPIC, 3, 12, java.util.List.of(), -1,
                        Map.of(), Forged.AMMO_EVERY, 1, false)));
        assertTrue(tiered.at(1).fullAuto());
        assertEquals(2, tiered.fireRate(), 1e-9);
        assertEquals(70, tiered.accuracy());
    }
}
