package com.larsons.game.combat;

import com.larsons.game.sprite.AnimState;
import com.larsons.game.world.Item;

import java.util.EnumMap;
import java.util.Map;

/**
 * What one of her attack animations does: when in it the blow lands (or the
 * shot leaves), and what it is - a swing that hits whatever is in reach in
 * front of her, an arrow or a bolt, or a spell.
 *
 * <p>The moments are fractions of the animation, read off the sheets: the
 * sword's swing meets its target at 0.42 of the way through, the axe's
 * chop at 0.46, the bow's arrow leaves as the loose starts. An attack the
 * table does not know - a weapon whose stance is newer than this table,
 * the wand, the mace, the dagger - still hits: any state named {@code
 * ..._attack} is a swing, its heavy and spin by their names, and a wand's
 * attack is a spell.
 *
 * @param kind  what it does
 * @param at    when, as a fraction of the animation
 * @param reach how far in front of her a swing hits, metres from her middle to the target's edge
 * @param arc   how wide a swing sweeps, in degrees (360: all round)
 * @param heavy whether it is a heavy attack, which rolls more dice ({@link CombatTuning#heavyExtra})
 */
public record Move(Kind kind, double at, double reach, double arc, boolean heavy) {

    /** What a move does. */
    public enum Kind { SWING, ARROW, BOLT, SPELL }

    private static final Map<AnimState, Move> TABLE = new EnumMap<>(AnimState.class);

    static {
        Move sword = swing(0.42, 1.7, 120), spin = swing(0.5, 1.8, 360);
        TABLE.put(AnimState.ATTACK, sword);
        TABLE.put(AnimState.BLADE_ATTACK, sword);
        TABLE.put(AnimState.SPIN_ATTACK, spin);
        TABLE.put(AnimState.BLADE_SPIN_ATTACK, spin);
        TABLE.put(AnimState.SHIELD_BASH, swing(0.32, 1.4, 90));

        TABLE.put(AnimState.AXE_ATTACK, swing(0.46, 1.8, 120));
        TABLE.put(AnimState.AXE_CROUCH_ATTACK, swing(0.45, 1.7, 120));
        TABLE.put(AnimState.AXE_HEAVY_ATTACK, new Move(Kind.SWING, 0.55, 1.9, 140, true));
        TABLE.put(AnimState.AXE_SPIN_ATTACK, swing(0.55, 1.9, 360));

        TABLE.put(AnimState.BOW_FIRE, shot(Kind.ARROW, 0.02));
        TABLE.put(AnimState.BOW_CROUCH_FIRE, shot(Kind.ARROW, 0.02));
        TABLE.put(AnimState.CROSSBOW_FIRE, shot(Kind.BOLT, 0.33));
        TABLE.put(AnimState.CROSSBOW_CROUCH_FIRE, shot(Kind.BOLT, 0.33));

        // the staff's cast is a thrust when the staff casts nothing (a Basic Staff): see of()
        TABLE.put(AnimState.STAFF_ATTACK, shot(Kind.SPELL, 0.5));
        TABLE.put(AnimState.STAFF_HEAVY_ATTACK, new Move(Kind.SWING, 0.55, 2.0, 120, true));
        TABLE.put(AnimState.STAFF_SPIN_ATTACK, swing(0.5, 2.0, 360));
        TABLE.put(AnimState.STAFF_CROUCH_ATTACK, swing(0.5, 2.0, 160));

        // aimed up: as their level ones (the same frames, the upper body leant back)
        TABLE.put(AnimState.BOW_FIRE_UP, shot(Kind.ARROW, 0.02));
        TABLE.put(AnimState.CROSSBOW_FIRE_UP, shot(Kind.BOLT, 0.33));
        TABLE.put(AnimState.STAFF_ATTACK_UP, shot(Kind.SPELL, 0.5));
        TABLE.put(AnimState.WAND_ATTACK_UP, shot(Kind.SPELL, 0.45));
    }

    private static Move swing(double at, double reach, double arc) {
        return new Move(Kind.SWING, at, reach, arc, false);
    }

    private static Move shot(Kind kind, double at) {
        return new Move(kind, at, 0, 0, false);
    }

    /** Whether it sends something flying rather than hitting what is in reach. */
    public boolean shoots() {
        return kind != Kind.SWING;
    }

    /**
     * What {@code state} does with {@code weapon} in hand (null: nothing in
     * hand), or null when it is not an attack (a parry, the bow's draw, an
     * emote).
     */
    public static Move of(AnimState state, Item weapon) {
        if (state == null) return null;
        boolean caster = Combat.casts(weapon);
        Move m = TABLE.get(state);
        if (m != null) {
            if (m.kind == Kind.SPELL && !caster) return new Move(Kind.SWING, m.at, 2.0, 60, false);
            return m;
        }
        String key = state.key();
        if (key.contains("parry") || key.contains("block") || key.contains("draw")) return null;
        if (key.endsWith("_fire")) return shot(key.startsWith("crossbow") ? Kind.BOLT : Kind.ARROW, 0.3);
        if (!key.endsWith("attack")) return null;
        boolean heavy = key.contains("heavy"), spin = key.contains("spin");
        if (caster && !heavy && !spin) return shot(Kind.SPELL, 0.45);
        return new Move(Kind.SWING, 0.45, 1.7, spin ? 360 : 120, heavy);
    }
}
