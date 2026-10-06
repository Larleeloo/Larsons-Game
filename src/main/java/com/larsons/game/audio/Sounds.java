package com.larsons.game.audio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The sound system's front door: play a sound by <em>key</em> and this
 * resolves what should actually be heard, applies the creator's settings and
 * the subtle per-playback pitch drift, and hands it to the mixer.
 *
 * <p>Copied from Larsons-Game-Engine's {@code Sounds}, which resolves a key
 * the same way:
 *
 * <ol>
 *   <li>the creator's explicit file for this key, when the pack is switched
 *       off for it ({@link SoundDef#usePack});</li>
 *   <li>the {@link SoundPack} folder's file for the key — the normal case,
 *       and what a creator gets by dropping an MP3 or WAV in with the right
 *       name;</li>
 *   <li>the creator's explicit file as the pack's fallback;</li>
 *   <li><b>silence</b> — the default for every sound in the game.</li>
 * </ol>
 *
 * <p>(The engine has one more step, its synthesized voices for a few old
 * effects, and music; the game has neither yet.)
 *
 * <p><b>Fresh pitch.</b> Every playback is at a slightly different pitch,
 * drawn from ±{@link SoundPack#pitchVariation()} — the trick Minecraft uses
 * so a run of footsteps never sounds like a stuck record.
 *
 * <p>Everything here is safe to call from the game loop and safe on a machine
 * with no audio device at all: the mixer goes quiet and every call is a no-op.
 * What plays when is decided by the hooks ({@link AnimationSound}).
 */
public final class Sounds {

    private static final Map<String, SoundDef> DEFS = new LinkedHashMap<>();
    private static final SoundMixer MIXER = new SoundMixer();

    private static boolean enabled = true;
    private static boolean pitchVariation = true;
    private static double sfxVolume = 1.0;

    private Sounds() {}

    // --- master switches ---------------------------------------------------------

    /** Master switch. */
    public static synchronized void setEnabled(boolean on) {
        enabled = on;
        if (!on) stopAll();
    }

    public static synchronized boolean isEnabled() {
        return enabled;
    }

    /** Turn the per-playback pitch drift on or off (on by default). */
    public static synchronized void setPitchVariation(boolean on) {
        pitchVariation = on;
    }

    public static synchronized boolean pitchVariation() {
        return pitchVariation;
    }

    /** Overall level of sound effects, 0..1. */
    public static synchronized void setSfxVolume(double v) {
        sfxVolume = clamp(v);
    }

    public static synchronized double sfxVolume() {
        return sfxVolume;
    }

    /** Master level over everything, 0..1. */
    public static void setMasterVolume(double v) {
        MIXER.setMasterVolume(clamp(v));
    }

    public static double masterVolume() {
        return MIXER.masterVolume();
    }

    /** The mixer, for diagnostics. */
    public static SoundMixer mixer() {
        return MIXER;
    }

    private static double clamp(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    // --- playing -----------------------------------------------------------------

    /** Play {@code key} once (or start it looping, if that is what it is). */
    public static SoundMixer.Voice play(String key) {
        return play(key, 1.0, 0.0);
    }

    /** Play {@code key} at a scaled volume — quieter at a distance, say. */
    public static SoundMixer.Voice play(String key, double volumeScale) {
        return play(key, volumeScale, 0.0);
    }

    /**
     * Play {@code key} at a scaled volume and a stereo position.
     *
     * @param pan -1 hard left … 0 centred … +1 hard right
     * @return the voice, or null when the key is silent
     */
    public static SoundMixer.Voice play(String key, double volumeScale, double pan) {
        if (!isEnabled()) return null;
        SoundDef def = definition(key);
        return start(def, resolve(key, def), volumeScale, pan, def.loop());
    }

    /**
     * Start {@code key} looping regardless of its own loop setting, for a
     * sound that lasts exactly as long as the state that started it — a
     * walk, a chest standing open. Returns the voice to stop, or {@code null}
     * when the key is silent.
     */
    public static SoundMixer.Voice playLoop(String key, double volumeScale, double pan) {
        if (!isEnabled()) return null;
        SoundDef def = definition(key);
        return start(def, resolve(key, def), volumeScale, pan, true);
    }

    /**
     * Play the first of {@code keys} that actually has audio, and nothing at
     * all if none of them do. Blank keys are skipped.
     */
    public static SoundMixer.Voice playFirst(double volumeScale, String... keys) {
        if (!isEnabled() || keys == null) return null;
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            SoundDef def = definition(key);
            PcmClip clip = resolve(key, def);
            if (clip.isEmpty()) continue;
            return start(def, clip, volumeScale, 0, def.loop());
        }
        return null;
    }

    /** Hand an already-resolved sound to the mixer. */
    private static SoundMixer.Voice start(SoundDef def, PcmClip clip,
                                          double volumeScale, double pan, boolean loop) {
        if (clip.isEmpty()) return null;
        double volume = def.volume() * volumeScale * sfxVolume();
        if (volume <= 0) return null;
        return MIXER.play(clip, pitchFor(def), volume, pan, loop);
    }

    /**
     * How loud and where a sound at {@code distance} metres from the listener,
     * {@code across} metres to its right (negative: left), is heard - the one
     * distance model in the game, after the engine's {@code playAt}: fully
     * panned {@code halfWidth} metres to the side, and quieter the farther off.
     *
     * @return {volume scale, pan}
     */
    public static double[] placement(double across, double distance, double halfWidth) {
        if (halfWidth <= 0) return new double[]{1, 0};
        double pan = Math.max(-1, Math.min(1, across / halfWidth));
        double d = distance / (halfWidth * 2);
        return new double[]{1.0 / (1.0 + d * d * 3), pan};
    }

    /** Stop every sound. */
    public static void stopAll() {
        MIXER.stopAll();
    }

    /** Release the audio device (game shutdown). */
    public static void dispose() {
        stopAll();
        MIXER.dispose();
    }

    /**
     * The pitch one playback uses: the sound's own pitch, drifted by a random
     * fraction when the fresh-pitch option is on and this sound allows it.
     */
    private static double pitchFor(SoundDef def) {
        double base = def.pitch();
        if (!pitchVariation() || !def.varyPitch()) return base;
        double spread = SoundPack.pitchVariation();
        if (spread <= 0) return base;
        double drift = ThreadLocalRandom.current().nextDouble(-spread, spread);
        return Math.max(0.25, Math.min(4, base * (1 + drift)));
    }

    // --- resolution --------------------------------------------------------------

    /** What {@code key} actually sounds like right now: the creator's file, the pack's file, or silence. */
    public static PcmClip resolve(String key) {
        return resolve(key, definition(key));
    }

    private static PcmClip resolve(String key, SoundDef def) {
        if (!def.usePack() && !def.file().isEmpty()) {
            PcmClip explicit = SoundLoader.load(resolvePath(def.file()));
            if (!explicit.isEmpty()) return explicit;
        }
        if (def.usePack()) {
            Path packFile = SoundPack.fileFor(key);
            if (packFile != null) {
                PcmClip fromPack = SoundLoader.load(packFile);
                if (!fromPack.isEmpty()) return fromPack;
            }
            if (!def.file().isEmpty()) {
                PcmClip explicit = SoundLoader.load(resolvePath(def.file()));
                if (!explicit.isEmpty()) return explicit;
            }
        }
        return PcmClip.SILENCE;
    }

    /** Where a key's audio comes from right now. */
    public enum Source {
        /** A file the creator picked, outside the pack's naming. */
        FILE,
        /** The sound pack folder. */
        PACK,
        /** Nothing — the default. */
        SILENT
    }

    /**
     * Which resolution step {@code key} currently lands on, answered from
     * what files <em>exist</em>, not by decoding them.
     */
    public static Source sourceOf(String key) {
        SoundDef def = definition(key);
        boolean hasFile = !def.file().isEmpty()
                && Files.isRegularFile(resolvePath(def.file()));
        if (!def.usePack() && hasFile) return Source.FILE;
        if (def.usePack()) {
            if (SoundPack.fileFor(key) != null) return Source.PACK;
            if (hasFile) return Source.FILE;
        }
        return Source.SILENT;
    }

    /**
     * Resolve a file path: as given when it exists, else relative to the
     * sound pack folder — so a bare {@code my_jump.mp3} finds the file
     * sitting in the pack.
     */
    public static Path resolvePath(String file) {
        Path direct = Path.of(file);
        if (Files.isRegularFile(direct)) return direct;
        Path inPack = SoundPack.root().resolve(file);
        return Files.isRegularFile(inPack) ? inPack : direct;
    }

    // --- assignments -------------------------------------------------------------

    /** The creator's assignment for {@code key}, or {@code null} if none. */
    public static synchronized SoundDef get(String key) {
        return DEFS.get(key);
    }

    /**
     * The settings {@code key} plays at: the creator's assignment when there
     * is one, else the pack's settings for it. Never {@code null}.
     */
    public static synchronized SoundDef definition(String key) {
        SoundDef def = DEFS.get(key);
        return def != null ? def : SoundDef.packDefault(key);
    }

    public static synchronized void put(SoundDef def) {
        if (def == null || def.key().isEmpty()) return;
        DEFS.put(def.key(), def);
    }

    public static synchronized void remove(String key) {
        DEFS.remove(key);
    }

    /** Every assignment, for saving. */
    public static synchronized List<SoundDef> all() {
        return new ArrayList<>(DEFS.values());
    }

    public static synchronized void clear() {
        DEFS.clear();
    }

    /** Replace the whole assignment set (loading {@code sounds.json}). */
    public static synchronized void setAll(List<SoundDef> defs) {
        DEFS.clear();
        if (defs == null) return;
        for (SoundDef d : defs) {
            if (d != null && !d.key().isEmpty()) DEFS.put(d.key(), d);
        }
    }

    /** Load assignments from the pack's {@code sounds.json} — called once at startup. */
    public static void load() {
        setAll(new SoundStore().load());
    }

    /** Save assignments to the pack's {@code sounds.json}. */
    public static Path save() {
        return new SoundStore().save(all());
    }
}
