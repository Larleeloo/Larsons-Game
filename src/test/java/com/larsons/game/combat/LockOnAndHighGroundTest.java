package com.larsons.game.combat;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.World;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** The lock-on, shots aimed up at what flies, and the high ground's extra die and reach. */
class LockOnAndHighGroundTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);
    private static final Player.Intent DRAW = new Player.Intent(0, 0, false, false, false, true, true, false, false,
            false, false, false, false, null);
    private static final Player.Intent HOLD = new Player.Intent(0, 0, false, false, false, false, true, false, false,
            false, false, false, false, null);
    private static final Player.Intent ATTACK = new Player.Intent(0, 0, false, false, false, true);
    private static final Player.Intent CROUCH_ATTACK = new Player.Intent(0, 0, false, false, false, true, true, true,
            false, false, false, false, false, null);

    private static World holding(Item weapon) {
        World w = new World(new Wardrobe());
        w.player().inventory().add(weapon);
        w.equipSelected();
        w.seed(7);
        return w;
    }

    private static void step(World w, Player.Intent i) {
        w.player().update(DT, i, DUR);
        w.tick(DT);
    }

    // --- the lock-on --------------------------------------------------------------------

    @Test
    void itLocksOnToWhatIsInTheMiddleOfTheViewAndNextGoesRoundLeftToRight() {
        Dummy left = new Dummy(new Vec3(-5, 0, -10), 0, 10, 2), middle = new Dummy(new Vec3(0, 0, -10), 0, 10, 2),
                right = new Dummy(new Vec3(5, 0, -10), 0, 10, 2), far = new Dummy(new Vec3(0, 0, -40), 0, 10, 2);
        List<Target> all = List.of(left, middle, right, far);
        Vec3 eye = new Vec3(0, 3, 6), look = new Vec3(0, -0.2, -1).normalize(), feet = Vec3.ZERO;
        assertSame(middle, Combat.lockCandidate(eye, look, feet, all, null));
        assertSame(right, Combat.lockCandidate(eye, look, feet, all, middle), "next: on to the right");
        assertSame(left, Combat.lockCandidate(eye, look, feet, all, right), "and round again from the left");
        assertNull(Combat.lockCandidate(eye, look, feet, List.of(far), null), "past the lock's range");
    }

    @Test
    void whenWhatSheIsLockedOnToGoesDownTheLockMovesOnThenLetsGo() {
        World w = holding(Item.of(ItemDef.LONGBOW));
        Dummy a = w.addDummy(new Dummy(new Vec3(0, 0, -6), 0, 10, 5));
        Dummy b = w.addDummy(new Dummy(new Vec3(4, 0, -9), 0, 10, 5));
        FlyingDummy f = w.addFlier(new FlyingDummy(new Vec3(-30, 0, 0), 0, 10, 5, Terrain.FLAT));
        Combat c = w.combat();
        c.lock(f);
        assertSame(f, c.locked(), "a flier can be locked on to, as anything can");
        c.lock(a);
        assertTrue(c.drainSounds().stream().anyMatch(s -> s.key().equals("combat/lock_on")));
        a.hurt(100, Vec3.ZERO, true);
        step(w, Player.Intent.NONE);
        assertSame(b, c.locked(), "on to the next nearest");
        b.hurt(100, Vec3.ZERO, true);
        step(w, Player.Intent.NONE);
        assertSame(f, c.locked(), "the flier, 25 m off, is still within reach");
        f.hurt(100, Vec3.ZERO, true);
        step(w, Player.Intent.NONE);
        assertNull(c.locked(), "nothing left standing");
        assertTrue(c.drainSounds().stream().anyMatch(s -> s.key().equals("combat/lock_off")));
    }

    // --- aiming up ------------------------------------------------------------------------

    @Test
    void atAFlierOverheadTheBowIsDrawnAndLoosedAimedUp() {
        World w = holding(Item.of(ItemDef.LONGBOW));
        Player p = w.player();
        Dummy level = w.addDummy(new Dummy(new Vec3(0, 0, -8), 0, 1000, 5));
        FlyingDummy f = w.addFlier(new FlyingDummy(new Vec3(0, 0, -3), 0, 1000, 5, Terrain.FLAT));
        assertFalse(w.combat().aimsUp(w), "the dummy on the ground ahead is level with her");
        w.combat().lock(f);
        assertTrue(w.combat().aimsUp(w), "the flier is well over her");

        step(w, DRAW);
        for (int i = 0; i < 50; i++) step(w, HOLD);
        assertEquals(AnimState.BOW_DRAW_UP, p.state());
        for (int i = 0; i < 3; i++) step(w, Player.Intent.NONE);
        assertEquals(AnimState.BOW_FIRE_UP, p.state());
        assertEquals(1, w.combat().projectiles().size());
        assertTrue(w.combat().projectiles().get(0).velocity().y() > 3, "loosed upward");
        assertEquals(1000, level.health(), 1e-9);
    }

    @Test
    void theCrossbowFiresUpStandingButNotCrouched() {
        World w = holding(Item.of(ItemDef.CROSSBOW));
        FlyingDummy f = w.addFlier(new FlyingDummy(new Vec3(0, 0, -3), 0, 1000, 5, Terrain.FLAT));
        w.combat().lock(f);
        step(w, ATTACK);
        assertEquals(AnimState.CROSSBOW_FIRE_UP, w.player().state());
        for (int i = 0; i < 90; i++) step(w, Player.Intent.NONE);
        for (int i = 0; i < 3; i++) step(w, CROUCH_ATTACK);
        AnimState crouched = w.player().state();
        assertTrue(crouched.key().contains("crouch") && !crouched.aimedUp(), "crouched it is the crouching shot: " + crouched);
    }

    @Test
    void theAimedUpAttacksAreMovesAsTheLevelOnesAre() {
        assertEquals(Move.Kind.ARROW, Move.of(AnimState.BOW_FIRE_UP, Item.of(ItemDef.LONGBOW)).kind());
        assertEquals(Move.Kind.BOLT, Move.of(AnimState.CROSSBOW_FIRE_UP, Item.of(ItemDef.CROSSBOW)).kind());
        assertNotNull(Move.of(AnimState.STAFF_ATTACK_UP, Item.of(ItemDef.CRYSTAL_STAFF, com.larsons.game.world.Tier.MAGIC)));
        assertNull(Move.of(AnimState.BOW_DRAW_UP, Item.of(ItemDef.LONGBOW)), "drawing is not a shot");
        for (AnimState s : AnimState.values()) {
            if (s.up() != s) {
                assertTrue(s.up().aimedUp());
                assertEquals(s.stance(), s.up().stance(), s.key());
            }
        }
    }

    // --- the high ground --------------------------------------------------------------------

    @Test
    void aShotFromTheHighGroundRollsADieMore() {
        Terrain t = Terrain.highlands();
        World w = holding(Item.of(ItemDef.CROSSBOW));
        w.setTerrain(t);
        Player p = w.player();
        p.setPosition(new Vec3(1.5, 0, -36));                   // up on the plateau, at the ledge
        Dummy below = w.addDummy(new Dummy(t.onGround(new Vec3(-4, 0, -36)), 0, 1000, 5));
        assertTrue(Combat.highGround(p.feet(), below), "6 m over it");
        w.combat().lock(below);
        step(w, ATTACK);
        Combat.Show high = w.combat().show();
        int dice = w.combat().tuning().dice(Item.of(ItemDef.CROSSBOW).rarity());
        assertEquals(dice + Combat.HIGH_DICE, high.strike().roll().count());
        assertTrue(high.notes().stream().anyMatch(n -> n.text().startsWith("High ground")));

        World level = holding(Item.of(ItemDef.CROSSBOW));
        Dummy ahead = level.addDummy(new Dummy(new Vec3(0, 0, -8), 0, 1000, 5));
        level.combat().lock(ahead);
        step(level, ATTACK);
        assertEquals(dice, level.combat().show().strike().roll().count(), "level with it: no more than the bow's own");
    }
}
