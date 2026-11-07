package com.larsons.game.combat;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;

import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** The jump attack: attack in the air, she drops onto what is under her and strikes all round as she lands. */
class JumpAttackTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);
    private static final Player.Intent JUMP = new Player.Intent(0, 0, false, false, true, false);
    private static final Player.Intent ATTACK = new Player.Intent(0, 0, false, false, false, true);

    private static World holding(Item weapon) {
        Wardrobe wardrobe = new Wardrobe();
        if (weapon != null && weapon.is(ItemDef.SWORD)) wardrobe.set(Slot.CARRY_RIGHT, "sword");
        World w = new World(wardrobe);
        if (weapon != null) w.player().inventory().add(weapon);
        w.equipSelected();
        w.seed(7);
        w.animals().setEnabled(false);
        return w;
    }

    private static void step(World w, Player.Intent i) {
        w.player().update(DT, i, DUR);
        w.tick(DT);
    }

    /** Jump, and once she is up off the ground, attack: the frames it took to get there. */
    private static void jumpAndAttack(World w) {
        step(w, JUMP);
        int n = 0;
        while (w.player().feet().y() - w.player().ground().y() < 0.3 && n++ < 120) step(w, Player.Intent.NONE);
        assertTrue(w.player().airborne(), "she is up");
        step(w, ATTACK);
    }

    @Test
    void attackingInTheAirDropsHerIntoTheJumpAttackAndHoldsItTillSheLands() {
        World w = holding(Item.of(ItemDef.SWORD));
        jumpAndAttack(w);
        Player p = w.player();
        assertEquals(AnimState.JUMP_ATTACK, p.state());
        assertTrue(p.plunging());
        double hold = DUR.applyAsDouble(AnimState.JUMP_ATTACK) * Player.PLUNGE_HOLD / Player.PLUNGE_FRAMES;
        int n = 0;
        while (p.airborne() && n++ < 240) {
            step(w, Player.Intent.NONE);
            assertTrue(p.stateTime() <= hold + 1e-9, "held on the drop while she is in the air");
        }
        assertFalse(p.airborne(), "she came down");
        assertFalse(p.plunging());
        assertEquals(AnimState.JUMP_ATTACK, p.state(), "and strikes as she lands");
        assertTrue(w.combat().effects().stream().anyMatch(e -> e.kind() == Combat.Effect.Kind.SLAM),
                "the landing throws up its ring");
    }

    @Test
    void theBlowLandsAllRoundHerWithADieMore() {
        World w = holding(Item.of(ItemDef.SWORD));
        Dummy front = w.addDummy(new Dummy(new Vec3(0, 0, -1.5), SpriteView.headingOf(0, 1), 1000, 6));
        Dummy behind = w.addDummy(new Dummy(new Vec3(0, 0, 1.8), SpriteView.headingOf(0, -1), 1000, 6));
        Dummy far = w.addDummy(new Dummy(new Vec3(4, 0, 0), SpriteView.headingOf(-1, 0), 1000, 6));
        jumpAndAttack(w);
        Combat.Show show = w.combat().show();
        assertNotNull(show);
        assertEquals(w.combat().tuning().dice(Item.of(ItemDef.SWORD).rarity()) + w.combat().tuning().heavyExtra,
                show.strike().roll().count(), "a die more than a swing");
        for (int i = 0; i < 240 && w.player().state() == AnimState.JUMP_ATTACK; i++) step(w, Player.Intent.NONE);
        int dealt = show.strike().roll().total();
        assertEquals(1000 - dealt, front.health(), 1e-9, "in front");
        assertEquals(1000 - dealt, behind.health(), 1e-9, "and behind: all round her");
        assertEquals(1000, far.health(), 1e-9, "not what is out of reach");
    }

    @Test
    void aBowLoosesAShotFromTheAirInstead() {
        World w = holding(Item.of(ItemDef.LONGBOW));
        jumpAndAttack(w);
        Player p = w.player();
        assertEquals(AnimState.BOW_FIRE, p.state(), "the shot, loosed in the air");
        assertTrue(p.aerialShot());
        assertFalse(p.plunging());
        for (int i = 0; i < 30; i++) step(w, Player.Intent.NONE);
        assertFalse(w.combat().projectiles().isEmpty(), "an arrow is on its way");
    }

    @Test
    void onTheGroundTheAttackIsAnOrdinarySwing() {
        World w = holding(Item.of(ItemDef.SWORD));
        step(w, ATTACK);
        assertNotEquals(AnimState.JUMP_ATTACK, w.player().state());
        assertFalse(w.player().plunging());
    }
}
