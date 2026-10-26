package com.larsons.game.combat;

import com.larsons.game.world.Border;
import com.larsons.game.world.Item;
import com.larsons.game.world.Rarity;

/**
 * One swing's or shot's damage, as it was rolled: the weapon (null: nothing
 * in hand), the dice, whether a Mystic weapon had the mana for its bonus,
 * and whether it is a plain blow ({@link Combat#plainBlow}: a wand's heavy
 * attack, no magic in it) whose dice are all it does - its weapon's border
 * adds nothing. What it does to a target is worked out as it lands ({@link
 * Combat#damage}), since Bonded counts the living things the weapon has hit
 * by then.
 */
public record Strike(Item weapon, DiceRoll roll, boolean mystic, boolean plain) {

    /** A strike its weapon's border has its say in. */
    public Strike(Item weapon, DiceRoll roll, boolean mystic) {
        this(weapon, roll, mystic, false);
    }

    /** The dice's colour: the weapon's rarity. */
    public Rarity rarity() {
        return weapon == null ? Rarity.COMMON : weapon.rarity();
    }

    /** The weapon's rare border, or null - and null for a plain blow, which its border adds nothing to. */
    public Border border() {
        return weapon == null || plain ? null : weapon.border();
    }
}
