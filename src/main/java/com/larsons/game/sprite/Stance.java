package com.larsons.game.sprite;

/**
 * What a character holds while a state plays - the same stances as the
 * 3D-Modeling repository's {@code feminine_model_states.py}.
 *
 * <ul>
 *   <li>{@link #SWORD}: the wardrobe's own carried items - the sword in the
 *       weapon hand, the shield (or whatever else) in the other;</li>
 *   <li>{@link #AXE}, {@link #BOW}, {@link #CROSSBOW}: that weapon, in both
 *       hands; whatever the wardrobe carries is put away while she holds it;</li>
 *   <li>{@link #FREE}: nothing - the emotes and the pick-ups are played with
 *       empty hands.</li>
 * </ul>
 *
 * <p>A weapon stance's weapon is not a wardrobe item: its sheets are under
 * {@code assets/sprites/<slot>/<item>/} like any carried item's, but they are
 * drawn by the stance - in {@link #hand} - instead of by the wardrobe (see
 * {@link #carried}). A right-handed character holds the axe and the crossbow
 * in the right hand and the bow in the left (the right draws the string); a
 * left-handed one the other way round.
 *
 * <p>Each stance also says which {@link AnimState} plays for what she does -
 * its idle, its walk, its attack - or {@code null} where the stance has no
 * such move (the bow has no spin attack). Those are methods rather than
 * constructor arguments because the two enums refer to each other.
 */
public enum Stance {

    SWORD("sword", "Sword and shield", null, null),
    AXE("axe", "Battle axe", Slot.CARRY_RIGHT, "battle_axe"),
    BOW("bow", "Bow", Slot.CARRY_LEFT, "longbow"),
    CROSSBOW("crossbow", "Crossbow", Slot.CARRY_RIGHT, "crossbow"),
    FREE("free", "Empty hands", null, null);

    /** How fast she moves: the walk, the run (Shift), the sprint (Ctrl). */
    public enum Pace { WALK, RUN, SPRINT }

    private final String key;
    private final String label;
    private final Slot slot;
    private final String item;

    Stance(String key, String label, Slot slot, String item) {
        this.key = key;
        this.label = label;
        this.slot = slot;
        this.item = item;
    }

    public String key() { return key; }

    public String label() { return label; }

    /** The weapon this stance draws (its folder name), or null: the wardrobe's (sword) or none (free). */
    public String item() { return item; }

    /** Whether this stance holds a weapon of its own (the axe, the bow, the crossbow). */
    public boolean weapon() { return item != null; }

    /** The hand the weapon is in, or null: the right-handed slot, its twin for a left-handed character. */
    public Slot hand(boolean leftHanded) {
        if (slot == null) return null;
        return leftHanded ? slot.twin() : slot;
    }

    /**
     * What is drawn in carried slot {@code slot} while a state of this stance
     * plays: the wardrobe's item (the sword stance), the stance's weapon in
     * its hand and nothing in the other (a weapon stance), nothing (free).
     */
    public String carried(Slot slot, Wardrobe wardrobe) {
        return switch (this) {
            case SWORD -> wardrobe.get(slot);
            case FREE -> null;
            default -> slot == hand(wardrobe.leftHanded()) ? item : null;
        };
    }

    // --- the states it plays ---------------------------------------------------------

    /** Standing still (crouched or not). */
    public AnimState idle(boolean crouched) {
        return switch (this) {
            case AXE -> crouched ? AnimState.AXE_CROUCH_IDLE : AnimState.AXE_IDLE;
            case BOW -> crouched ? AnimState.BOW_CROUCH_IDLE : AnimState.BOW_IDLE;
            case CROSSBOW -> crouched ? AnimState.CROSSBOW_CROUCH_IDLE : AnimState.CROSSBOW_IDLE;
            default -> crouched ? AnimState.CROUCH_IDLE : AnimState.IDLE;
        };
    }

    /**
     * Moving at {@code pace}. Crouched, the run is the sword stance's fast
     * crouch walk (the weapon stances have only the one crouch walk), and
     * there is no crouched sprint - the caller stands her up for that.
     */
    public AnimState moving(Pace pace, boolean crouched) {
        if (crouched) {
            return switch (this) {
                case AXE -> AnimState.AXE_CROUCH_WALK;
                case BOW -> AnimState.BOW_CROUCH_WALK;
                case CROSSBOW -> AnimState.CROSSBOW_CROUCH_WALK;
                default -> pace == Pace.WALK ? AnimState.CROUCH_WALK : AnimState.CROUCH_WALK_FAST;
            };
        }
        return switch (this) {
            case AXE -> switch (pace) {
                case WALK -> AnimState.AXE_WALK;
                case RUN -> AnimState.AXE_RUN;
                case SPRINT -> AnimState.AXE_SPRINT;
            };
            case BOW -> switch (pace) {
                case WALK -> AnimState.BOW_WALK;
                case RUN -> AnimState.BOW_RUN;
                case SPRINT -> AnimState.BOW_SPRINT;
            };
            case CROSSBOW -> switch (pace) {
                case WALK -> AnimState.CROSSBOW_WALK;
                case RUN -> AnimState.CROSSBOW_RUN;
                case SPRINT -> AnimState.CROSSBOW_SPRINT;
            };
            default -> switch (pace) {
                case WALK -> AnimState.WALK;
                case RUN -> AnimState.RUN;
                case SPRINT -> AnimState.SPRINT;
            };
        };
    }

    /**
     * The attack: the sword's slash, the axe's chop, the crossbow's shot - and
     * for the bow, the draw, which holds at full draw until {@link #release}.
     * Crouched where the stance has a crouched one (the sword has none: she
     * stands up to swing).
     */
    public AnimState attack(boolean crouched) {
        return switch (this) {
            case SWORD -> AnimState.ATTACK;
            case AXE -> crouched ? AnimState.AXE_CROUCH_ATTACK : AnimState.AXE_ATTACK;
            case BOW -> crouched ? AnimState.BOW_CROUCH_DRAW : AnimState.BOW_DRAW;
            case CROSSBOW -> crouched ? AnimState.CROSSBOW_CROUCH_FIRE : AnimState.CROSSBOW_FIRE;
            case FREE -> null;
        };
    }

    /** Whether the attack is held (the bow's draw) and loosed by {@link #release}. */
    public boolean drawn() { return this == BOW; }

    /** The bow's loose, from full draw; null for every other stance. */
    public AnimState release(boolean crouched) {
        return this == BOW ? (crouched ? AnimState.BOW_CROUCH_FIRE : AnimState.BOW_FIRE) : null;
    }

    /** The heavy attack: the axe's great overhead chop. */
    public AnimState heavy() {
        return this == AXE ? AnimState.AXE_HEAVY_ATTACK : null;
    }

    /** The spin attack: the sword's (the light weapon) or the axe's (the heavy one). */
    public AnimState spin() {
        return switch (this) {
            case SWORD -> AnimState.SPIN_ATTACK;
            case AXE -> AnimState.AXE_SPIN_ATTACK;
            default -> null;
        };
    }

    /** A parry: a quick deflection with the weapon. */
    public AnimState parry() {
        return switch (this) {
            case SWORD -> AnimState.PARRY;
            case AXE -> AnimState.AXE_PARRY;
            case BOW -> AnimState.BOW_PARRY;
            case CROSSBOW -> AnimState.CROSSBOW_PARRY;
            case FREE -> null;
        };
    }

    /** Guarding, held: the shield up (the sword stance) or the weapon across. */
    public AnimState block() {
        return switch (this) {
            case SWORD -> AnimState.SHIELD_READY;
            case AXE -> AnimState.AXE_BLOCK;
            case BOW -> AnimState.BOW_BLOCK;
            case CROSSBOW -> AnimState.CROSSBOW_BLOCK;
            case FREE -> null;
        };
    }

    /** The shield bash, from behind the shield. */
    public AnimState bash() {
        return this == SWORD ? AnimState.SHIELD_BASH : null;
    }

    /** The jump: the first six's with the sword and shield, each weapon's own with it; none with empty hands. */
    public AnimState jump() {
        return switch (this) {
            case SWORD -> AnimState.JUMP;
            case AXE -> AnimState.AXE_JUMP;
            case BOW -> AnimState.BOW_JUMP;
            case CROSSBOW -> AnimState.CROSSBOW_JUMP;
            case FREE -> null;
        };
    }

    /** Whether she can jump holding what this stance holds. */
    public boolean canJump() { return jump() != null; }

    // --- lookups ---------------------------------------------------------------------

    /** The weapon stance whose weapon is {@code item}, or null. */
    public static Stance ofItem(String item) {
        if (item == null) return null;
        String base = Wardrobe.Style.base(item);
        for (Stance s : values()) {
            if (s.item != null && s.item.equals(base)) return s;
        }
        return null;
    }

    public static Stance byKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase();
        for (Stance s : values()) if (s.key.equals(k)) return s;
        return null;
    }
}
