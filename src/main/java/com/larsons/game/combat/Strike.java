package com.larsons.game.combat;

import com.larsons.game.world.Border;
import com.larsons.game.world.Item;
import com.larsons.game.world.Rarity;

/**
 * One swing's or shot's damage, as it was rolled: the weapon (null: nothing
 * in hand), the dice, and whether a Mystic weapon had the mana for its
 * bonus. What it does to a target is worked out as it lands ({@link
 * Combat#damage}), since Bonded counts the living things the weapon has hit
 * by then.
 */
public record Strike(Item weapon, DiceRoll roll, boolean mystic) {

    /** The dice's colour: the weapon's rarity. */
    public Rarity rarity() {
        return weapon == null ? Rarity.COMMON : weapon.rarity();
    }

    /** The weapon's rare border, or null. */
    public Border border() {
        return weapon == null ? null : weapon.border();
    }
}
