package com.larsons.game.combat;

import com.larsons.game.world.Border;
import com.larsons.game.world.Buffs;
import com.larsons.game.world.Item;
import com.larsons.game.world.Rarity;

/**
 * One swing's or shot's damage, as it was rolled: the weapon (null: nothing
 * in hand), the dice, whether a Mystic weapon had the mana for its bonus,
 * and whether it is a plain blow ({@link Combat#plainBlow}: a wand's heavy
 * attack, no magic in it) whose dice are all it does - its weapon's border
 * adds nothing. What it does to a target is worked out as it lands ({@link
 * Combat#damage}), since Bonded counts the living things the weapon has hit
 * by then. A forged weapon's dice are its own ({@link
 * com.larsons.game.world.Forged}), as many effects as it was forged with.
 */
public record Strike(Item weapon, DiceRoll roll, boolean mystic, boolean plain, boolean sneak, double times,
                     Buffs.Only what) {

    public Strike {
        if (times <= 0) times = 1;
        if (what == null) what = Buffs.Only.ANY;
    }

    /** A strike with no card's help and no sneaking. */
    public Strike(Item weapon, DiceRoll roll, boolean mystic, boolean plain) {
        this(weapon, roll, mystic, plain, false, 1, Buffs.Only.ANY);
    }

    /** A strike its weapon's border has its say in. */
    public Strike(Item weapon, DiceRoll roll, boolean mystic) {
        this(weapon, roll, mystic, false);
    }

    /** The dice's colour: the weapon's rarity. */
    public Rarity rarity() {
        return weapon == null ? Rarity.COMMON : weapon.rarity();
    }

    /**
     * The borders' effects it carries: its weapon's border, or those the
     * weapon was forged with - none for a plain blow, which they add nothing to.
     */
    public java.util.List<Border> borders() {
        return weapon == null || plain ? java.util.List.of() : weapon.borders();
    }

    /** Whether it carries {@code b}'s effect ({@link #borders}). */
    public boolean has(Border b) {
        return weapon != null && !plain && weapon.has(b);
    }

    /** The colour its dice's rims take: its first effect's, or null for none. */
    public Border border() {
        java.util.List<Border> b = borders();
        return b.isEmpty() ? null : b.get(0);
    }

    /** The dice's own colour, a forged weapon's ({@code 0xRRGGBB}), or -1 for its rarity's. */
    public int diceRgb() {
        return weapon == null || weapon.forged() == null ? -1 : weapon.forged().diceRgb();
    }
}
