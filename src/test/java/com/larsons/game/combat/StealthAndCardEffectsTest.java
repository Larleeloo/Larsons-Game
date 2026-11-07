package com.larsons.game.combat;

import com.larsons.game.ambient.Animal;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Buffs;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Stealth;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;

import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Stealth (hidden, sneak attacks) and what the cards' helps do in a fight. */
class StealthAndCardEffectsTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);
    private static final Player.Intent ATTACK = new Player.Intent(0, 0, false, false, false, true);
    private static final Player.Intent CROUCH = new Player.Intent(0, 0, false, false, false, false, false, true,
            false, false, false, false, false, null);

    private static World holding(Item weapon) {
        Wardrobe wardrobe = new Wardrobe();
        if (weapon != null && weapon.is(ItemDef.SWORD)) wardrobe.set(Slot.CARRY_RIGHT, "sword");
        World w = new World(wardrobe);
        if (weapon != null) w.player().inventory().add(weapon);
        w.equipSelected();
        w.seed(11);
        w.animals().setEnabled(false);
        return w;
    }

    private static void play(World w, Player.Intent first, double seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            w.player().update(DT, i == 0 ? first : Player.Intent.NONE, DUR);
            w.tick(DT);
        }
    }

    private static Dummy ahead(World w, double health) {
        return w.addDummy(new Dummy(new Vec3(0, 0, -1.2), SpriteView.headingOf(0, 1), health, 6));
    }

    @Test
    void crouchedInTheDarkSheIsHidden() {
        World w = holding(Item.of(ItemDef.SWORD));
        w.clock().setHour(12);
        play(w, Player.Intent.NONE, 0.5);
        assertFalse(w.stealth().hidden(), "standing about at noon: in view");
        assertEquals(0.7, w.stealth().score(), 0.05);
        w.clock().setHour(0);
        play(w, CROUCH, 0.5);
        assertTrue(w.player().crouched());
        assertTrue(w.stealth().hidden(), "crouched still at midnight: hidden");
        assertTrue(w.stealth().score() < Stealth.HIDDEN);
    }

    @Test
    void aBlowFromHidingIsASneakAttackForTwiceTheDamage() {
        World w = holding(Item.of(ItemDef.SWORD));
        Dummy d = ahead(w, 1000);
        w.clock().setHour(0);
        play(w, CROUCH, 0.5);
        assertTrue(w.stealth().hidden());
        play(w, ATTACK, 0.05);
        Combat.Show show = w.combat().show();
        assertTrue(show.strike().sneak());
        assertTrue(show.notes().stream().anyMatch(n -> n.text().startsWith("Sneak attack")));
        play(w, Player.Intent.NONE, 0.6);
        assertEquals(1000 - 2 * show.strike().roll().total(), d.health(), 1e-9);
    }

    @Test
    void cardsMultiplyDamageAndAddDice() {
        World w = holding(Item.of(ItemDef.SWORD));
        Dummy d = ahead(w, 1000);
        w.clock().setHour(12);
        w.buffs().add(new Buffs.Active("Test", Buffs.Kind.DAMAGE, 1.5, 60, 0, Buffs.Only.ANY));
        w.buffs().add(new Buffs.Active("Test", Buffs.Kind.EXTRA_DICE, 2, 60, 0, Buffs.Only.MELEE));
        play(w, ATTACK, 0.05);
        Combat.Show show = w.combat().show();
        assertEquals(3, show.strike().roll().count(), "a Common sword's die and the card's two");
        play(w, Player.Intent.NONE, 0.6);
        assertEquals(1000 - Math.round(show.strike().roll().total() * 1.5), d.health(), 1e-9);
    }

    @Test
    void bindFreezesWhatIsHitAndTransfigureTurnsItIntoAnAnimalForAWhile() {
        World w = holding(Item.of(ItemDef.SWORD));
        Dummy d = ahead(w, 1000);
        w.buffs().add(new Buffs.Active("Bind", Buffs.Kind.FREEZE, 4, 60, 0, Buffs.Only.ANY));
        play(w, ATTACK, 0.6);
        assertTrue(d.frozen(), "frozen stock still");
        w.buffs().clear();
        w.buffs().add(new Buffs.Active("Transfigure", Buffs.Kind.TRANSFIGURE, 3, 0, 3, Buffs.Only.ANY));
        int before = w.animals().live().size();
        play(w, ATTACK, 0.8);
        assertTrue(d.hidden(), "the dummy is gone");
        assertFalse(w.targets().contains(d));
        assertEquals(before + 1, w.animals().live().size(), "an animal stands where it stood");
        Animal stand = w.animals().live().get(w.animals().live().size() - 1);
        assertEquals(2, w.buffs().active().get(0).charges(), "two more hits to turn");
        play(w, Player.Intent.NONE, 3.5);
        assertFalse(d.hidden(), "back when it is over");
        assertFalse(w.animals().live().contains(stand), "and the animal gone");
    }

    @Test
    void aWardSoaksUpBlowsAndDefenseHalvesThem() {
        World w = holding(Item.of(ItemDef.SWORD));
        Player p = w.player();
        p.buffs().add(new Buffs.Active("Aegis", Buffs.Kind.WARD, 30, 60, 0, Buffs.Only.ANY));
        assertEquals(0, p.hurt(20), 1e-9, "soaked up");
        assertEquals(10, p.hurt(20), 1e-9, "the ward's last ten, then ten through");
        p.buffs().clear();
        p.buffs().add(new Buffs.Active("Stoneskin", Buffs.Kind.DEFENSE, 0.5, 60, 0, Buffs.Only.ANY));
        assertEquals(10, p.hurt(20), 1e-9);
    }
}
