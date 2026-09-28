package com.larsons.game.sprite;

/**
 * The animation states every character sprite set is rendered in.
 *
 * <p>Each state is its own set of sheets (8 directions × 3 elevations). The
 * frame count of a sheet is whatever the Blender render produced — it varies by
 * state — and every layer stacked on a character plays the same frame index as
 * the base body, so a held sword stays in the hand.
 *
 * <p>{@link #defaultFrames()} is only used by the procedural fallback
 * character; a real sheet's frame count always comes from the sheet.
 */
public enum AnimState {

    IDLE("idle", "Idle", true, 60),
    WALK("walk", "Walk", true, 30),
    RUN("run", "Run", true, 20),
    SPRINT("sprint", "Sprint", true, 16),
    JUMP("jump", "Jump", false, 24),
    ATTACK("attack", "Attack", false, 18);

    /** The playback rate every sheet is authored at unless its profile says otherwise. */
    public static final double DEFAULT_FPS = 30.0;

    private final String key;
    private final String label;
    private final boolean loops;
    private final int defaultFrames;

    AnimState(String key, String label, boolean loops, int defaultFrames) {
        this.key = key;
        this.label = label;
        this.loops = loops;
        this.defaultFrames = defaultFrames;
    }

    /** File-name segment: {@code walk_middle_ne.png}. */
    public String key() { return key; }

    public String label() { return label; }

    /** Whether the animation repeats; jump and attack play once and hold. */
    public boolean loops() { return loops; }

    /** Frames in the procedural fallback's sheet for this state, at 30 fps. */
    public int defaultFrames() { return defaultFrames; }

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
