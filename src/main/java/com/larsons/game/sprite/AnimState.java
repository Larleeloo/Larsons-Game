package com.larsons.game.sprite;

/**
 * The animation states every character sprite set is rendered in - the same
 * table as the 3D-Modeling repository's {@code feminine_model_states.py}.
 *
 * <p>Each state is its own set of sheets (8 directions × 3 elevations). The
 * frame count of a sheet is whatever the Blender render produced — it varies by
 * state — and every layer stacked on a character plays the same frame index as
 * the base body, so a held sword stays in the hand.
 *
 * <p>A state belongs to a {@link Stance}: the weapon the character holds while
 * it plays, which decides what is drawn in her hands ({@link Stance#carried}).
 * The emotes and the pick-ups are played with empty hands ({@link
 * Stance#FREE}): nothing carried is drawn; the dash and the roll with whatever
 * she holds ({@link Stance#ALL}). A state can name a {@link
 * #fallback()} - the state shown instead where a character has no sheets for
 * it (a 512-pixel rendered set made before it existed, the generated stand-in
 * body) - and every chain of fallbacks ends in one of the first six.
 *
 * <p>{@link #defaultFrames()} is only used by the procedural fallback
 * character; a real sheet's frame count always comes from the sheet.
 */
public enum AnimState {

    // --- the first six
    IDLE("idle", "Idle", true, 60, Stance.SWORD, 0, null),
    WALK("walk", "Walk", true, 30, Stance.SWORD, 1.7, null),
    RUN("run", "Run", true, 20, Stance.SWORD, 4.2, null),
    SPRINT("sprint", "Sprint", true, 16, Stance.SWORD, 7.0, null),
    JUMP("jump", "Jump", false, 24, Stance.SWORD, 0, null),
    ATTACK("attack", "Attack", false, 18, Stance.SWORD, 0, null),
    // --- crouching, with the sword and shield
    CROUCH_IDLE("crouch_idle", "Crouch", true, 48, Stance.SWORD, 0, "idle"),
    CROUCH_WALK("crouch_walk", "Crouch walk", true, 32, Stance.SWORD, 0.9, "walk"),
    CROUCH_WALK_FAST("crouch_walk_fast", "Crouch walk, fast", true, 24, Stance.SWORD, 1.8, "walk"),
    // --- reaching down for something (empty hands)
    PICKUP("pickup", "Pick up", false, 30, Stance.FREE, 0, "idle"),
    CROUCH_PICKUP("crouch_pickup", "Pick up, crouched", false, 30, Stance.FREE, 0, "crouch_idle"),
    // --- emotes (empty hands)
    EMOTE_LAUGH("emote_laugh", "Laugh", false, 48, Stance.FREE, 0, "idle"),
    EMOTE_CRY("emote_cry", "Cry", false, 60, Stance.FREE, 0, "idle"),
    EMOTE_SURPRISE("emote_surprise", "Surprise", false, 36, Stance.FREE, 0, "idle"),
    EMOTE_ANGRY("emote_angry", "Angry", false, 48, Stance.FREE, 0, "idle"),
    // --- the sword (the light weapon) and the shield
    SPIN_ATTACK("spin_attack", "Spin attack", false, 24, Stance.SWORD, 0, "attack"),
    PARRY("parry", "Parry", false, 18, Stance.SWORD, 0, "attack"),
    SHIELD_READY("shield_ready", "Shield ready", true, 30, Stance.SWORD, 0, "idle"),
    SHIELD_BASH("shield_bash", "Shield bash", false, 20, Stance.SWORD, 0, "attack"),
    // --- the two-handed battle axe (the heavy weapon)
    AXE_IDLE("axe_idle", "Axe idle", true, 48, Stance.AXE, 0, "idle"),
    AXE_WALK("axe_walk", "Axe walk", true, 28, Stance.AXE, 1.7, "walk"),
    AXE_RUN("axe_run", "Axe run", true, 20, Stance.AXE, 4.2, "run"),
    AXE_SPRINT("axe_sprint", "Axe sprint", true, 16, Stance.AXE, 7.0, "sprint"),
    AXE_JUMP("axe_jump", "Axe jump", false, 27, Stance.AXE, 0, "jump"),
    AXE_CROUCH_IDLE("axe_crouch_idle", "Axe crouch", true, 48, Stance.AXE, 0, "crouch_idle"),
    AXE_CROUCH_WALK("axe_crouch_walk", "Axe crouch walk", true, 32, Stance.AXE, 0.9, "crouch_walk"),
    AXE_ATTACK("axe_attack", "Axe attack", false, 24, Stance.AXE, 0, "attack"),
    AXE_HEAVY_ATTACK("axe_heavy_attack", "Axe heavy attack", false, 32, Stance.AXE, 0, "axe_attack"),
    AXE_SPIN_ATTACK("axe_spin_attack", "Axe spin attack", false, 30, Stance.AXE, 0, "axe_attack"),
    AXE_CROUCH_ATTACK("axe_crouch_attack", "Axe crouch attack", false, 24, Stance.AXE, 0, "axe_attack"),
    AXE_PARRY("axe_parry", "Axe parry", false, 18, Stance.AXE, 0, "parry"),
    AXE_BLOCK("axe_block", "Axe block", true, 30, Stance.AXE, 0, "axe_idle"),
    // --- the bow
    BOW_IDLE("bow_idle", "Bow idle", true, 48, Stance.BOW, 0, "idle"),
    BOW_WALK("bow_walk", "Bow walk", true, 28, Stance.BOW, 1.7, "walk"),
    BOW_RUN("bow_run", "Bow run", true, 20, Stance.BOW, 4.2, "run"),
    BOW_SPRINT("bow_sprint", "Bow sprint", true, 16, Stance.BOW, 7.0, "sprint"),
    BOW_JUMP("bow_jump", "Bow jump", false, 27, Stance.BOW, 0, "jump"),
    BOW_CROUCH_IDLE("bow_crouch_idle", "Bow crouch", true, 48, Stance.BOW, 0, "crouch_idle"),
    BOW_CROUCH_WALK("bow_crouch_walk", "Bow crouch walk", true, 32, Stance.BOW, 0.9, "crouch_walk"),
    BOW_DRAW("bow_draw", "Bow draw", false, 24, Stance.BOW, 0, "bow_idle"),
    BOW_FIRE("bow_fire", "Bow fire", false, 18, Stance.BOW, 0, "bow_idle"),
    BOW_CROUCH_DRAW("bow_crouch_draw", "Bow draw, crouched", false, 24, Stance.BOW, 0, "bow_crouch_idle"),
    BOW_CROUCH_FIRE("bow_crouch_fire", "Bow fire, crouched", false, 18, Stance.BOW, 0, "bow_crouch_idle"),
    BOW_PARRY("bow_parry", "Bow parry", false, 18, Stance.BOW, 0, "parry"),
    BOW_BLOCK("bow_block", "Bow block", true, 30, Stance.BOW, 0, "bow_idle"),
    // --- the crossbow
    CROSSBOW_IDLE("crossbow_idle", "Crossbow idle", true, 48, Stance.CROSSBOW, 0, "idle"),
    CROSSBOW_WALK("crossbow_walk", "Crossbow walk", true, 28, Stance.CROSSBOW, 1.7, "walk"),
    CROSSBOW_RUN("crossbow_run", "Crossbow run", true, 20, Stance.CROSSBOW, 4.2, "run"),
    CROSSBOW_SPRINT("crossbow_sprint", "Crossbow sprint", true, 16, Stance.CROSSBOW, 7.0, "sprint"),
    CROSSBOW_JUMP("crossbow_jump", "Crossbow jump", false, 27, Stance.CROSSBOW, 0, "jump"),
    CROSSBOW_CROUCH_IDLE("crossbow_crouch_idle", "Crossbow crouch", true, 48, Stance.CROSSBOW, 0, "crouch_idle"),
    CROSSBOW_CROUCH_WALK("crossbow_crouch_walk", "Crossbow crouch walk", true, 32, Stance.CROSSBOW, 0.9,
            "crouch_walk"),
    CROSSBOW_FIRE("crossbow_fire", "Crossbow fire", false, 30, Stance.CROSSBOW, 0, "crossbow_idle"),
    CROSSBOW_CROUCH_FIRE("crossbow_crouch_fire", "Crossbow fire, crouched", false, 30, Stance.CROSSBOW, 0,
            "crossbow_crouch_idle"),
    CROSSBOW_PARRY("crossbow_parry", "Crossbow parry", false, 18, Stance.CROSSBOW, 0, "parry"),
    CROSSBOW_BLOCK("crossbow_block", "Crossbow block", true, 30, Stance.CROSSBOW, 0, "crossbow_idle"),
    // --- the sword with no shield: the first version of the sword stance's moves,
    // with the free arm down (the second version holds the shield up)
    BLADE_IDLE("blade_idle", "Sword idle", true, 60, Stance.BLADE, 0, "idle"),
    BLADE_WALK("blade_walk", "Sword walk", true, 30, Stance.BLADE, 1.7, "walk"),
    BLADE_RUN("blade_run", "Sword run", true, 20, Stance.BLADE, 4.2, "run"),
    BLADE_SPRINT("blade_sprint", "Sword sprint", true, 16, Stance.BLADE, 7.0, "sprint"),
    BLADE_JUMP("blade_jump", "Sword jump", false, 24, Stance.BLADE, 0, "jump"),
    BLADE_ATTACK("blade_attack", "Sword attack", false, 18, Stance.BLADE, 0, "attack"),
    BLADE_CROUCH_IDLE("blade_crouch_idle", "Sword crouch", true, 48, Stance.BLADE, 0, "crouch_idle"),
    BLADE_CROUCH_WALK("blade_crouch_walk", "Sword crouch walk", true, 32, Stance.BLADE, 0.9, "crouch_walk"),
    BLADE_CROUCH_WALK_FAST("blade_crouch_walk_fast", "Sword crouch walk, fast", true, 24, Stance.BLADE, 1.8,
            "crouch_walk_fast"),
    BLADE_SPIN_ATTACK("blade_spin_attack", "Sword spin attack", false, 24, Stance.BLADE, 0, "spin_attack"),
    BLADE_PARRY("blade_parry", "Sword parry", false, 18, Stance.BLADE, 0, "parry"),
    // --- the crystal staff: carried in the right hand (the walk uses it as a walking
    // stick), both hands on it to fight
    STAFF_IDLE("staff_idle", "Staff idle", true, 48, Stance.STAFF, 0, "idle"),
    STAFF_WALK("staff_walk", "Staff walk", true, 28, Stance.STAFF, 1.7, "walk"),
    STAFF_RUN("staff_run", "Staff run", true, 20, Stance.STAFF, 4.2, "run"),
    STAFF_SPRINT("staff_sprint", "Staff sprint", true, 16, Stance.STAFF, 7.0, "sprint"),
    STAFF_CROUCH_IDLE("staff_crouch_idle", "Staff crouch", true, 48, Stance.STAFF, 0, "crouch_idle"),
    STAFF_CROUCH_WALK("staff_crouch_walk", "Staff crouch walk", true, 32, Stance.STAFF, 0.9, "crouch_walk"),
    STAFF_JUMP("staff_jump", "Staff jump", false, 27, Stance.STAFF, 0, "jump"),
    STAFF_ATTACK("staff_attack", "Staff cast", false, 24, Stance.STAFF, 0, "attack"),
    STAFF_HEAVY_ATTACK("staff_heavy_attack", "Staff slam", false, 32, Stance.STAFF, 0, "staff_attack"),
    STAFF_SPIN_ATTACK("staff_spin_attack", "Staff spin attack", false, 30, Stance.STAFF, 0, "staff_attack"),
    STAFF_CROUCH_ATTACK("staff_crouch_attack", "Staff sweep, crouched", false, 24, Stance.STAFF, 0,
            "staff_attack"),
    STAFF_PARRY("staff_parry", "Staff parry", false, 18, Stance.STAFF, 0, "parry"),
    STAFF_BLOCK("staff_block", "Staff block", true, 30, Stance.STAFF, 0, "staff_idle"),
    // --- the wand: in the right hand, casts - a flick at the enemy, a great cast with
    // both hands, a casting turn, a flick from the crouch, a ward thrown up and a ward held
    WAND_IDLE("wand_idle", "Wand idle", true, 48, Stance.WAND, 0, "idle"),
    WAND_WALK("wand_walk", "Wand walk", true, 28, Stance.WAND, 1.7, "walk"),
    WAND_RUN("wand_run", "Wand run", true, 20, Stance.WAND, 4.2, "run"),
    WAND_SPRINT("wand_sprint", "Wand sprint", true, 16, Stance.WAND, 7.0, "sprint"),
    WAND_CROUCH_IDLE("wand_crouch_idle", "Wand crouch", true, 48, Stance.WAND, 0, "crouch_idle"),
    WAND_CROUCH_WALK("wand_crouch_walk", "Wand crouch walk", true, 32, Stance.WAND, 0.9, "crouch_walk"),
    WAND_JUMP("wand_jump", "Wand jump", false, 27, Stance.WAND, 0, "jump"),
    WAND_ATTACK("wand_attack", "Wand cast", false, 24, Stance.WAND, 0, "attack"),
    WAND_HEAVY_ATTACK("wand_heavy_attack", "Wand great cast", false, 32, Stance.WAND, 0, "wand_attack"),
    WAND_SPIN_ATTACK("wand_spin_attack", "Wand casting turn", false, 30, Stance.WAND, 0, "wand_attack"),
    WAND_CROUCH_ATTACK("wand_crouch_attack", "Wand flick, crouched", false, 24, Stance.WAND, 0,
            "wand_attack"),
    WAND_PARRY("wand_parry", "Wand parry", false, 18, Stance.WAND, 0, "parry"),
    WAND_BLOCK("wand_block", "Wand block", true, 30, Stance.WAND, 0, "wand_idle"),
    // --- the mace: in the right hand - an overhand smash, a two-handed smash into the
    // ground, a spin, a low sweep; the haft thrown up overhead to parry, held across in
    // both hands to block
    MACE_IDLE("mace_idle", "Mace idle", true, 48, Stance.MACE, 0, "idle"),
    MACE_WALK("mace_walk", "Mace walk", true, 28, Stance.MACE, 1.7, "walk"),
    MACE_RUN("mace_run", "Mace run", true, 20, Stance.MACE, 4.2, "run"),
    MACE_SPRINT("mace_sprint", "Mace sprint", true, 16, Stance.MACE, 7.0, "sprint"),
    MACE_CROUCH_IDLE("mace_crouch_idle", "Mace crouch", true, 48, Stance.MACE, 0, "crouch_idle"),
    MACE_CROUCH_WALK("mace_crouch_walk", "Mace crouch walk", true, 32, Stance.MACE, 0.9, "crouch_walk"),
    MACE_JUMP("mace_jump", "Mace jump", false, 27, Stance.MACE, 0, "jump"),
    MACE_ATTACK("mace_attack", "Mace smash", false, 24, Stance.MACE, 0, "attack"),
    MACE_HEAVY_ATTACK("mace_heavy_attack", "Mace ground smash", false, 32, Stance.MACE, 0, "mace_attack"),
    MACE_SPIN_ATTACK("mace_spin_attack", "Mace spin attack", false, 30, Stance.MACE, 0, "mace_attack"),
    MACE_CROUCH_ATTACK("mace_crouch_attack", "Mace sweep, crouched", false, 24, Stance.MACE, 0,
            "mace_attack"),
    MACE_PARRY("mace_parry", "Mace parry", false, 18, Stance.MACE, 0, "parry"),
    MACE_BLOCK("mace_block", "Mace block", true, 30, Stance.MACE, 0, "mace_idle"),
    // --- the dagger: in the right hand, the left up as a guard - a thrust, a slash and
    // a lunge, a spinning slash, a rising stab from the crouch, a parry and a guard
    DAGGER_IDLE("dagger_idle", "Dagger idle", true, 48, Stance.DAGGER, 0, "idle"),
    DAGGER_WALK("dagger_walk", "Dagger walk", true, 28, Stance.DAGGER, 1.7, "walk"),
    DAGGER_RUN("dagger_run", "Dagger run", true, 20, Stance.DAGGER, 4.2, "run"),
    DAGGER_SPRINT("dagger_sprint", "Dagger sprint", true, 16, Stance.DAGGER, 7.0, "sprint"),
    DAGGER_CROUCH_IDLE("dagger_crouch_idle", "Dagger crouch", true, 48, Stance.DAGGER, 0, "crouch_idle"),
    DAGGER_CROUCH_WALK("dagger_crouch_walk", "Dagger crouch walk", true, 32, Stance.DAGGER, 0.9,
            "crouch_walk"),
    DAGGER_JUMP("dagger_jump", "Dagger jump", false, 27, Stance.DAGGER, 0, "jump"),
    DAGGER_ATTACK("dagger_attack", "Dagger thrust", false, 24, Stance.DAGGER, 0, "attack"),
    DAGGER_HEAVY_ATTACK("dagger_heavy_attack", "Dagger slash and lunge", false, 32, Stance.DAGGER, 0,
            "dagger_attack"),
    DAGGER_SPIN_ATTACK("dagger_spin_attack", "Dagger spinning slash", false, 30, Stance.DAGGER, 0,
            "dagger_attack"),
    DAGGER_CROUCH_ATTACK("dagger_crouch_attack", "Dagger rising stab, crouched", false, 24, Stance.DAGGER, 0,
            "dagger_attack"),
    DAGGER_PARRY("dagger_parry", "Dagger parry", false, 18, Stance.DAGGER, 0, "parry"),
    DAGGER_BLOCK("dagger_block", "Dagger block", true, 30, Stance.DAGGER, 0, "dagger_idle"),
    // --- the dodges, played in every stance with whatever is in her hands (Stance.ALL):
    // the dash, a low burst forward, and the combat roll, a dive into a shoulder roll
    // and up again - its first DODGE_SHARED frames the dash's own, so a dash turns
    // into a roll there without a jump in the picture
    DASH("dash", "Dash", false, 12, Stance.ALL, 0, "sprint"),
    ROLL("roll", "Combat roll", false, 22, Stance.ALL, 0, "dash"),
    // --- aimed up: the ranged attacks with her upper body leant back 40° from the waist
    // (feminine_model_aim_up.py), the bow, the bolt and the spells sent up at what flies
    // or stands well above her - each played in place of its level one ({@link #up()})
    BOW_DRAW_UP("bow_draw_up", "Bow draw, aimed up", false, 24, Stance.BOW, 0, "bow_draw"),
    BOW_FIRE_UP("bow_fire_up", "Bow fire, aimed up", false, 18, Stance.BOW, 0, "bow_fire"),
    CROSSBOW_FIRE_UP("crossbow_fire_up", "Crossbow fire, aimed up", false, 30, Stance.CROSSBOW, 0,
            "crossbow_fire"),
    STAFF_ATTACK_UP("staff_attack_up", "Staff cast, aimed up", false, 24, Stance.STAFF, 0, "staff_attack"),
    WAND_ATTACK_UP("wand_attack_up", "Wand cast, aimed up", false, 24, Stance.WAND, 0, "wand_attack"),
    // --- climbing a rock face, hand over hand, with empty hands: a loop the game plays
    // forwards as she climbs, backwards as she climbs down, and holds while she hangs
    CLIMB("climb", "Climb", true, 32, Stance.FREE, 0, "idle");

    /**
     * The frames the roll shares with the dash (the push-off): the game
     * decides by then which of the two it is, and turns a dash into the roll
     * at the frame it has reached.
     */
    public static final int DODGE_SHARED = 5;

    /** The playback rate every sheet is authored at unless its profile says otherwise. */
    public static final double DEFAULT_FPS = 30.0;

    private final String key;
    private final String label;
    private final boolean loops;
    private final int defaultFrames;
    private final Stance stance;
    private final double speed;
    private final String fallbackKey;

    AnimState(String key, String label, boolean loops, int defaultFrames, Stance stance, double speed,
              String fallbackKey) {
        this.key = key;
        this.label = label;
        this.loops = loops;
        this.defaultFrames = defaultFrames;
        this.stance = stance;
        this.speed = speed;
        this.fallbackKey = fallbackKey;
    }

    /** File-name segment: {@code walk_middle_ne.png}. */
    public String key() { return key; }

    public String label() { return label; }

    /** Whether the animation repeats; the attacks, emotes and pick-ups play once and hold. */
    public boolean loops() { return loops; }

    /** Frames in the procedural fallback's sheet for this state, at 30 fps. */
    public int defaultFrames() { return defaultFrames; }

    /** What she holds while it plays ({@link Stance#FREE}: nothing; {@link Stance#ALL}: whatever she holds). */
    public Stance stance() { return stance; }

    /** Whether her hands are empty: nothing carried is drawn. */
    public boolean handsFree() { return stance == Stance.FREE; }

    /** Metres per second the state's feet travel at (the game moves her that fast), else 0. */
    public double speed() { return speed; }

    /**
     * Its aimed-up variant - the bow's draw and loose, the crossbow's shot,
     * the staff's and the wand's casts - or itself when it has none.
     */
    public AnimState up() {
        return switch (this) {
            case BOW_DRAW -> BOW_DRAW_UP;
            case BOW_FIRE -> BOW_FIRE_UP;
            case CROSSBOW_FIRE -> CROSSBOW_FIRE_UP;
            case STAFF_ATTACK -> STAFF_ATTACK_UP;
            case WAND_ATTACK -> WAND_ATTACK_UP;
            default -> this;
        };
    }

    /** Whether it is an aimed-up variant. */
    public boolean aimedUp() {
        return key.endsWith("_up");
    }

    /** The state shown where a character has no sheets for this one; null for the first six. */
    public AnimState fallback() { return fallbackKey == null ? null : byKey(fallbackKey); }

    /** The first-six state at the end of this one's fallbacks (itself for the first six). */
    public AnimState root() {
        AnimState s = this;
        while (s.fallback() != null) s = s.fallback();
        return s;
    }

    /** Parse a {@link #key()} back into a state, or {@code null}. */
    public static AnimState byKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase();
        for (AnimState s : values()) {
            if (s.key.equals(k)) return s;
        }
        return null;
    }

    /**
     * Which frame of a {@code frames}-long sheet to show {@code seconds} into
     * the state at {@code fps}. Looping states wrap; one-shot states hold their
     * last frame.
     */
    public int frameAt(double seconds, double fps, int frames) {
        if (frames <= 1) return 0;
        int raw = (int) Math.floor(Math.max(0, seconds) * fps);
        return loops ? Math.floorMod(raw, frames) : Math.min(raw, frames - 1);
    }

    /** How long one pass of a {@code frames}-long sheet lasts at {@code fps}. */
    public static double duration(int frames, double fps) {
        return frames / (fps > 0 ? fps : DEFAULT_FPS);
    }
}
