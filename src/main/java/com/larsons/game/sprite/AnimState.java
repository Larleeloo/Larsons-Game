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
 * Stance#FREE}): nothing carried is drawn. A state can name a {@link
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
    BLADE_PARRY("blade_parry", "Sword parry", false, 18, Stance.BLADE, 0, "parry");

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

    /** What she holds while it plays ({@link Stance#FREE}: nothing). */
    public Stance stance() { return stance; }

    /** Whether her hands are empty: nothing carried is drawn. */
    public boolean handsFree() { return stance == Stance.FREE; }

    /** Metres per second the state's feet travel at (the game moves her that fast), else 0. */
    public double speed() { return speed; }

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
