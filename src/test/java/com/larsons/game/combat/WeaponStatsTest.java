package com.larsons.game.combat;

import com.larsons.game.world.Forged;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A weapon's damage per second, as the inventory shows it over the weapon. */
class WeaponStatsTest {

    private static final CombatTuning T = CombatTuning.defaults();

    @Test
    void itIsWhatItsDiceComeToOverHowLongAnAttackTakes() {
        Item sword = Item.of(ItemDef.SWORD);
        WeaponStats.Dps d = WeaponStats.of(sword, T, s -> 0.5);
        assertEquals(WeaponStats.mean(T.dice(sword.rarity()), 8), d.perHit(), 1e-9, "a sword's D8s");
        assertEquals(0.5, d.cycle(), 1e-9);
        assertEquals(d.perHit() / 0.5, d.perSecond(), 1e-9);
        assertFalse(d.ranged());
        assertTrue(d.line().startsWith("About "), d.line());

        WeaponStats.Dps hands = WeaponStats.of(null, T, s -> 0.5);
        assertEquals(WeaponStats.mean(T.unarmedDice, 4), hands.perHit(), 1e-9, "bare hands: D4s");
        assertTrue(hands.perSecond() < d.perSecond());
    }

    @Test
    void aForgedBowShootsAsFastAndAsTrueAsItWasMade() {
        Forged bow = Forged.fresh("Quick bow").withBase(ItemDef.LONGBOW.id());
        WeaponStats.Dps plain = WeaponStats.of(bow.item(), T, s -> 0.4);
        WeaponStats.Dps quick = WeaponStats.of(bow.withFireRate(2).withAccuracy(60).item(), T, s -> 0.4);
        assertTrue(plain.ranged());
        assertEquals(2 * plain.perSecond(), quick.perSecond(), 1e-9, "twice the rate of fire, twice the damage a second");
        assertEquals(plain.perHit(), quick.perHit(), 1e-9);
        assertEquals(60, quick.accuracy());
        assertEquals(100, plain.accuracy());
    }
}
