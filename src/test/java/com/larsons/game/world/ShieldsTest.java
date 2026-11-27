package com.larsons.game.world;

import com.larsons.game.combat.Move;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** The shields: twelve to carry on the off-hand arm, raised to roll their dice against blows from the front. */
class ShieldsTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);

    /** The sword in her right hand and {@code shield} on her left arm. */
    private static Player carrying(String shield) {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        Player p = new Player(w);
        if (shield != null) {
            Item it = Item.of(Shields.def(shield));
            p.inventory().put(it, 1, true);
            assertTrue(Shields.carry(w, it));
        }
        p.useArmourDice(new Random(7));
        return p;
    }

    private static Player.Intent in(String... keys) {
        Set<String> k = Set.of(keys);
        return new Player.Intent(0, 0, false, false, false, k.contains("attack"), k.contains("attack"), false, false,
                false, false, k.contains("block"), k.contains("bash"), null);
    }

    /** Hold block until her shield is up (or her weapon across her). */
    private static void raise(Player p, int frames) {
        for (int f = 0; f < frames; f++) p.update(DT, in("block"), DUR);
    }

    // (she faces -z: heading 0)
    private static final Vec3 FRONT = new Vec3(0, 0, -3), BEHIND = new Vec3(0, 0, 3), SIDE = new Vec3(3, 0, 0);

    @Test
    void thereAreTwelveEachAWearableForTheHandsWithItsOwnGift() {
        List<Shields.Shield> all = Shields.all();
        assertEquals(12, all.size());
        Set<Shields.Trait> traits = new HashSet<>();
        for (Shields.Shield s : all) {
            ItemDef d = Shields.def(s.id());
            assertTrue(Wearables.ALL.contains(d), s.id());
            assertSame(d, ItemDef.byId(s.id()));
            assertEquals(List.of(Slot.CARRY_LEFT, Slot.CARRY_RIGHT), d.wearable().slots());
            assertTrue(d.isWearable() && !d.held() && !d.inHand(), "never in her hands as a weapon: " + s.id());
            assertEquals(s.rarity(), Item.of(d).rarity());
            assertTrue(s.dice() >= 1 && s.arc() >= 45 && s.arc() <= 95, s.id());
            if (!s.id().equals("round_shield")) assertTrue(traits.add(s.trait()), "a gift of its own: " + s.id());
            assertFalse(Shields.describe(Item.of(d)).isEmpty());
            assertNotNull(Wearables.pickup(Item.of(d)), "it lies in the world as itself");
            assertEquals(s.id(), Wearables.pickup(Item.of(d)).spin());
        }
        assertEquals(Shields.Trait.NONE, Shields.byId("round_shield").trait());
        assertEquals(11, traits.size());
    }

    @Test
    void aShieldGoesOnTheOffHandArmAndComesOffAgain() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        Item kite = Item.of(Shields.def("kite_shield"));
        assertTrue(Shields.carry(w, kite));
        assertEquals("kite_shield", w.get(Slot.CARRY_LEFT));
        assertEquals(Slot.CARRY_LEFT, Shields.carriedIn(w));
        assertTrue(Shields.carrying(w, kite));
        assertFalse(Shields.carry(w, kite), "already on");
        assertTrue(Stance.carriesShield(w));
        assertEquals(Stance.SWORD, Stance.ofWardrobe(w));
        // another takes its place
        Item buckler = Item.of(Shields.def("buckler"));
        assertTrue(Shields.carry(w, buckler));
        assertFalse(Shields.carrying(w, kite));
        assertEquals("buckler", Shields.carriedFolder(w));
        assertTrue(Shields.carry(w, null));
        assertNull(Shields.carriedIn(w));
        assertEquals(Stance.BLADE, Stance.ofWardrobe(w), "the sword alone");
        // a left-handed character carries it on her right arm
        Wardrobe left = new Wardrobe();
        left.setHand(Wardrobe.Hand.LEFT);
        Shields.carry(left, kite);
        assertEquals("kite_shield", left.get(Slot.CARRY_RIGHT));
        assertNull(left.get(Slot.CARRY_LEFT));
    }

    @Test
    void anyOneHandedWeaponIsFoughtWithInTheSwordAndShieldsMoves() {
        Player p = carrying("heater_shield");
        for (Stance one : List.of(Stance.MACE, Stance.WAND, Stance.DAGGER)) {
            p.setStance(one);
            assertTrue(one.oneHanded());
            assertEquals(Stance.SWORD, p.stance(), one + " and a shield");
            assertEquals(one, p.weaponStance());
            Wardrobe looks = p.looks();
            assertEquals(one.item(), looks.get(Slot.CARRY_RIGHT), "drawn in her hand");
            assertEquals("heater_shield", looks.get(Slot.CARRY_LEFT), "and the shield on her arm");
            assertEquals("sword", p.wardrobe().get(Slot.CARRY_RIGHT), "her own wardrobe as it was");
        }
        // two hands: the shield put away while she holds it
        for (Stance two : List.of(Stance.AXE, Stance.STAFF, Stance.BOW, Stance.CROSSBOW)) {
            p.setStance(two);
            assertFalse(two.oneHanded());
            assertEquals(two, p.stance());
            assertSame(p.wardrobe(), p.looks());
        }
        // and with no shield, each its own
        Player bare = carrying(null);
        bare.setStance(Stance.MACE);
        assertEquals(Stance.MACE, bare.stance());
    }

    @Test
    void aWandsSlashWithAShieldIsItsSpell() {
        Item wand = Item.of(ItemDef.WAND);
        assertEquals(Move.Kind.SPELL, Move.of(AnimState.ATTACK, wand).kind());
        assertEquals(Move.Kind.SWING, Move.of(AnimState.ATTACK, Item.of(ItemDef.MACE)).kind());
    }

    @Test
    void raisedItRollsItsDiceAgainstABlowFromTheFrontOnly() {
        Player p = carrying("tower_shield");
        raise(p, 20);
        assertEquals(AnimState.SHIELD_READY, p.state());
        assertTrue(p.shieldUp());
        double before = p.health();
        double took = p.struck(12, FRAME_FRONT(p), false);
        Player.ShieldBlock b = p.lastShield();
        assertNotNull(b);
        assertTrue(b.blocked() > 0, "the shield rolled");
        assertEquals(Math.max(0, 12 - b.blocked()), took, 1e-9);
        assertEquals(before - took, p.health(), 1e-9);
        assertTrue(p.armourRoll().armoured());
        assertSame(b.shield().def(), p.armourRoll().blocks().get(0).piece().item().def(), "its dice shown first");
        assertTrue(p.drainEvents().stream().anyMatch(e -> e.kind() == Player.Event.Kind.SHIELD_BLOCKED));

        // from behind it gets past
        Player q = carrying("round_shield");
        raise(q, 20);
        double h = q.health();
        assertEquals(12, q.struck(12, BEHIND, false), 1e-9);
        assertNull(q.lastShield(), "the shield never rolled");
        assertEquals(h - 12, q.health(), 1e-9);
    }

    /** In front of her, wherever she stands. */
    private static Vec3 FRAME_FRONT(Player p) {
        return p.feet().add(FRONT);
    }

    @Test
    void theTowerShieldCoversHerSidesAndTheRoundShieldDoesNot() {
        assertTrue(Shields.inFront(Vec3.ZERO, 0, SIDE, Shields.byId("tower_shield").arc()));
        assertFalse(Shields.inFront(Vec3.ZERO, 0, SIDE, Shields.byId("round_shield").arc()));
        assertTrue(Shields.inFront(Vec3.ZERO, 0, FRONT, 45));
        assertFalse(Shields.inFront(Vec3.ZERO, 0, BEHIND, 95));
        assertFalse(Shields.inFront(Vec3.ZERO, 0, null, 95), "from nowhere in particular: not the front");
        // turned round, behind is in front
        assertTrue(Shields.inFront(Vec3.ZERO, Math.PI, BEHIND, 60));
    }

    @Test
    void loweredItDoesNothing() {
        Player p = carrying("crystal_aegis");
        double h = p.health();
        assertEquals(10, p.struck(10, FRONT, false), 1e-9);
        assertNull(p.lastShield());
        assertEquals(h - 10, p.health(), 1e-9);
    }

    @Test
    void eachPointHeldCostsStaminaAndOutOfItTheGuardBreaks() {
        Player p = carrying("heater_shield");
        raise(p, 20);
        p.setStamina(0);
        p.drainEvents();
        double h = p.health();
        assertEquals(9, p.struck(9, FRONT, false), 1e-9);
        assertTrue(p.lastShield().broken());
        assertEquals(0, p.lastShield().blocked(), 1e-9);
        assertEquals(h - 9, p.health(), 1e-9);
        assertTrue(p.drainEvents().stream().anyMatch(e -> e.kind() == Player.Event.Kind.GUARD_BROKEN));
        // stamina for what it holds: half a point a point, a Steady shield half that, a Light one none
        Item heater = Item.of(Shields.def("heater_shield"));
        assertEquals(10 * Shields.STAMINA_PER_POINT / 2, Shields.staminaFor(heater, 10), 1e-9);
        assertEquals(10 * Shields.STAMINA_PER_POINT, Shields.staminaFor(Item.of(Shields.round()), 10), 1e-9);
        assertEquals(0, Shields.staminaFor(Item.of(Shields.def("wicker_shield")), 10), 1e-9);
        // a Light shield holds with no stamina at all
        Player w = carrying("wicker_shield");
        raise(w, 20);
        w.setStamina(0);
        w.struck(9, FRONT, false);
        assertFalse(w.lastShield().broken());
        assertTrue(w.lastShield().blocked() > 0);
    }

    @Test
    void theKiteShieldStopsAShotFromTheFrontWhole() {
        Player p = carrying("kite_shield");
        raise(p, 20);
        double h = p.health();
        assertEquals(0, p.struck(30, FRONT, true), 1e-9);
        assertEquals(30, p.lastShield().blocked(), 1e-9);
        assertTrue(p.lastShield().shot());
        assertEquals(h, p.health(), 1e-9);
    }

    @Test
    void theBucklerParriesWhatComesAsItIsRaised() {
        Player p = carrying("buckler");
        raise(p, 6);                                  // a tenth of a second up
        assertTrue(p.guardTime() <= Shields.QUICK_WINDOW);
        double h = p.health();
        assertEquals(0, p.struck(20, FRONT, false), 1e-9);
        assertTrue(p.lastShield().parried());
        assertEquals(h, p.health(), 1e-9);
        // held up longer, it only blocks
        raise(p, 40);
        assertTrue(p.guardTime() > Shields.QUICK_WINDOW);
        p.struck(20, FRONT, false);
        assertFalse(p.lastShield().parried());
    }

    @Test
    void itsBashRollsItsOwnDice() {
        assertArrayEquals(new int[]{1, 6}, Shields.bash(Item.of(Shields.round())));
        assertArrayEquals(new int[]{1 + Shields.STUDDED_DICE, 6}, Shields.bash(Item.of(Shields.def("targe"))),
                "Studded: two dice more");
        assertArrayEquals(new int[]{2, 8}, Shields.bash(Item.of(Shields.def("spiked_shield"))));
    }

    @Test
    void aShieldOfOnesOwnRollsTheDiceTheTailorGaveIt() {
        Forged f = Forged.freshWorn("Bulwark of the dawn").withId("bulwark_of_the_dawn").withBase("kite_shield")
                .withDice(5).withSides(12);
        Item it = f.item();
        assertTrue(Shields.is(it));
        assertEquals("5d12", Shields.guardText(it));
        assertEquals(Shields.Trait.ARROW_WARD, Shields.trait(it), "the gift of what it was made from");
        Random r = new Random(3);
        for (int i = 0; i < 50; i++) {
            double t = Shields.roll(it, r).total();
            assertTrue(t >= 5 && t <= 60, "5d12: " + t);
        }
        // carried as itself, and what it gives worn counts
        Wardrobe w = new Wardrobe();
        Inventory inv = new Inventory(10);
        Forged g = f.withGear(Gear.Effect.values()[0], Gear.Effect.values()[0].step());
        Item mine = g.item();
        inv.put(mine, 1, true);
        Shields.carry(w, mine);
        assertTrue(Shields.carrying(w, mine));
        assertEquals(Forged.PREFIX + g.id(), w.id(Slot.CARRY_LEFT));
        assertTrue(Gear.of(w, inv).amounts().containsKey(Gear.Effect.values()[0]), "its effects worn");
    }

    @Test
    void theShieldOnHerArmIsHers() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        w.set(Slot.CARRY_LEFT, "round_shield");
        World world = new World(w);
        assertFalse(world.player().inventory().contains(Shields.round()));
        world.ownWhatIsWorn();
        assertTrue(world.player().inventory().contains(Shields.round()));
    }

    @Test
    void aShieldPickedUpGoesOnHerArmWhenItIsBare() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        World world = new World(w);
        world.spawn(Shields.def("targe"), new Vec3(1, 0, 0));
        assertTrue(world.pickUp(world.reachable()));
        assertEquals("targe", Shields.carriedFolder(world.player().wardrobe()));
        assertEquals(Stance.SWORD, Stance.ofWardrobe(world.player().wardrobe()), "the sword and shield");
        // a second one goes into the bag, the first kept on
        world.spawn(Shields.def("buckler"), new Vec3(1, 0, 0));
        assertTrue(world.pickUp(world.reachable()));
        assertEquals("targe", Shields.carriedFolder(world.player().wardrobe()));
        // dropped, it comes off her arm
        Item targe = null;
        for (Item i : world.player().inventory()) if (i != null && i.def() == Shields.def("targe")) targe = i;
        int at = world.player().inventory().indexOf(targe);
        world.dropSlot(at);
        assertNull(Shields.carriedFolder(world.player().wardrobe()));
    }

    @Test
    void itIsPutAwayWhileHerHandsHoldATwoHandedWeaponAndSaysWhy() {
        Player p = carrying("heater_shield");
        p.setStance(Stance.SWORD);
        assertNull(Shields.awayBecause(p), "with the sword it is up to be used");
        for (Stance one : List.of(Stance.MACE, Stance.WAND, Stance.DAGGER)) {
            p.setStance(one);
            assertNull(Shields.awayBecause(p), one.toString());
        }
        p.setStance(Stance.AXE);
        assertEquals("the battle axe takes both hands", Shields.awayBecause(p));
        p.setStance(Stance.BOW);
        assertEquals("the bow takes both hands", Shields.awayBecause(p));
        p.setStance(Stance.SWORD);
        p.setHandsEmpty(true);
        assertNull(Shields.awayBecause(p), "with empty hands it is up to be used too");
        p.setHandsEmpty(false);
        assertNull(Shields.awayBecause(carrying(null)), "no shield, nothing to say");
    }

    @Test
    void itNeedsNoWeaponHerHandsEmptySheRaisesItAndBashesWithIt() {
        Player p = carrying("kite_shield");
        p.setStance(Stance.SWORD);
        p.setHandsEmpty(true);
        assertTrue(p.handsEmpty());
        assertEquals(Stance.SWORD, p.stance(), "the sword and shield's moves, the shield alone in them");
        raise(p, 20);
        assertEquals(AnimState.SHIELD_READY, p.state());
        assertTrue(p.shieldUp(), "raised with nothing in her hand");
        double took = p.struck(12, FRAME_FRONT(p), false);
        Player.ShieldBlock b = p.lastShield();
        assertNotNull(b, "its dice rolled against the blow");
        assertTrue(b.blocked() > 0);
        assertEquals(Math.max(0, 12 - b.blocked()), took, 1e-9);
        // and with no shield, empty hands are the lone blade's (nothing drawn in them)
        Player bare = carrying(null);
        bare.setStance(Stance.SWORD);
        bare.setHandsEmpty(true);
        assertEquals(Stance.BLADE, bare.stance());
    }
}
