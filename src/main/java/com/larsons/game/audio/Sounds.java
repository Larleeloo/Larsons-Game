package com.larsons.game.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * The sound system's front door: play a sound by <em>key</em> and this
 * resolves what should actually be heard, applies the settings and the
 * subtle per-playback pitch drift, and hands it to the mixer.
 *
 * <p>Copied from Larsons-Game-Engine's {@code Sounds}, which resolves a key
 * the same way. For each key of the sound's {@linkplain SoundKeys#chain
 * fallbacks}, most specific first:
 *
 * <ol>
 *   <li>the player's own file for that key - chosen in the Sounds menu and
 *       saved in {@code config/sounds.json} ({@link SoundDef}) - or silence,
 *       if they muted it;</li>
 *   <li>the {@link SoundPack} folder's file for that key - the game's own
 *       sound (a placeholder until a real one replaces it), and what a
 *       creator gets by dropping an MP3 or WAV in with the right name;</li>
 * </ol>
 *
 * <p>and when none of them has anything, <b>silence</b>. So a choice made for
 * a shared sound ({@code player/attack}, every sword swing) is heard for both
 * bodies, and one made for a single body's ({@code player/masculine/attack})
 * for that body alone.
 *
 * <p><b>Volume.</b> A sound plays at its own volume, times its {@link
 * SoundCategory}'s, times the effects' and the master volume - the category
 * applied in the mixer, so moving its slider moves what is already playing.
 *
 * <p><b>Fresh pitch.</b> Every playback is at a slightly different pitch,
 * drawn from ±{@link SoundPack#pitchVariation()} — the trick Minecraft uses
 * so a run of footsteps never sounds like a stuck record. A song
 * ({@link SoundCategory#MUSIC}) always plays at exactly its own pitch.
 *
 * <p><b>Music.</b> The songs go through {@link Music}, which decodes one off
 * the game loop, starts it looping and fades it in and out.
 *
 * <p>Everything here is safe to call from the game loop and safe on a machine
 * with no audio device at all: the mixer goes quiet and every call is a no-op.
 * What plays when is decided by the hooks ({@link AnimationSound}, {@link
 * WorldSounds}, and calls where things happen).
 */
public final class Sounds {

    private static final Map<String, SoundDef> DEFS = new LinkedHashMap<>();
    private static final SoundMixer MIXER = new SoundMixer();
    /** What each key played as, until a choice or the pack changes ({@link #forget}). */
    private static final Map<String, Resolution> RESOLVED = new HashMap<>();
    private static final double[] CATEGORY = new double[SoundCategory.values().length];

    static {
        Arrays.fill(CATEGORY, 1.0);
    }

    private static boolean enabled = true;
    private static boolean pitchVariation = true;
    private static double sfxVolume = 1.0;
    /** Where the player's own choices are kept (null: the pack folder, as the engine keeps them). */
    private static Path storeDir;
    private static int packGeneration = -1;
    private static SoundMixer.Voice preview;

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

    /** How loud {@code c}'s sounds play, 0..1 - those playing now too. */
    public static synchronized void setCategoryVolume(SoundCategory c, double v) {
        CATEGORY[c.ordinal()] = clamp(v);
        MIXER.setGroupVolume(group(c), CATEGORY[c.ordinal()]);
    }

    public static synchronized double categoryVolume(SoundCategory c) {
        return CATEGORY[c.ordinal()];
    }

    /** The mixer's volume group of a category's voices. */
    private static int group(SoundCategory c) {
        return c.ordinal() + 1;
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
        if (!isEnabled() || key == null) return null;
        Resolution r = resolved(key);
        return start(key, r, volumeScale, pan, r.def().loop());
    }

    /**
     * Start {@code key} looping regardless of its own loop setting, for a
     * sound that lasts exactly as long as the state that started it — a
     * walk, a chest standing open. Returns the voice to stop, or {@code null}
     * when the key is silent.
     */
    public static SoundMixer.Voice playLoop(String key, double volumeScale, double pan) {
        if (!isEnabled() || key == null) return null;
        return start(key, resolved(key), volumeScale, pan, true);
    }

    /**
     * Play the first of {@code keys} that actually has audio, and nothing at
     * all if none of them do. Blank keys are skipped.
     */
    public static SoundMixer.Voice playFirst(double volumeScale, String... keys) {
        if (!isEnabled() || keys == null) return null;
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            Resolution r = resolved(key);
            if (r.clip().isEmpty()) continue;
            return start(key, r, volumeScale, 0, r.def().loop());
        }
        return null;
    }

    /**
     * Play {@code key} once in the middle, as the Sounds menu's play button
     * does - stopping the last one it played, a loop included, so a row can
     * be listened to over and over. Returns null when it is silent.
     */
    public static SoundMixer.Voice preview(String key) {
        stopPreview();
        if (!isEnabled() || key == null) return null;
        SoundMixer.Voice v = start(key, resolved(key), 1, 0, false);
        synchronized (Sounds.class) {
            preview = v;
        }
        return v;
    }

    /** Stop what {@link #preview} last played. */
    public static void stopPreview() {
        SoundMixer.Voice v;
        synchronized (Sounds.class) {
            v = preview;
            preview = null;
        }
        if (v != null) v.stop();
    }

    /** Hand an already-resolved sound to the mixer (a song at exactly its own pitch: no drift). */
    private static SoundMixer.Voice start(String key, Resolution r, double volumeScale, double pan, boolean loop) {
        if (r.clip().isEmpty()) return null;
        SoundDef def = r.def();
        SoundCategory category = SoundKeys.categoryOf(key);
        double volume = def.volume() * volumeScale * (category == SoundCategory.MUSIC ? 1 : sfxVolume());
        if (volume <= 0) return null;
        double pitch = category == SoundCategory.MUSIC ? def.pitch() : pitchFor(def);
        return MIXER.play(r.clip(), pitch, volume, pan, loop, group(category));
    }

    /**
     * Start a song {@link Music} has decoded for {@code key}: looping, in the
     * middle, at exactly its own pitch, at {@code volume} - in its category's
     * volume group, so the Music slider moves it. Null when sound is off or
     * the clip is empty.
     */
    static SoundMixer.Voice startMusic(String key, PcmClip clip, double volume) {
        return startMusic(key, clip, volume, 0);
    }

    /** {@link #startMusic(String, PcmClip, double)} from {@code seconds} into the song (a preview's jump). */
    static SoundMixer.Voice startMusic(String key, PcmClip clip, double volume, double seconds) {
        if (!isEnabled() || clip == null || clip.isEmpty() || volume <= 0) return null;
        return MIXER.play(clip, 1.0, volume, 0, true, group(SoundKeys.categoryOf(key)), seconds);
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

    /** Where a key's audio comes from right now. */
    public enum Source {
        /** A file the player chose for it. */
        FILE,
        /** The sound pack folder: the game's own sound. */
        PACK,
        /** The player muted it. */
        MUTED,
        /** Nothing — the default for anything without a file. */
        SILENT
    }

    /**
     * What a key is heard as: the audio (null when only asked which file),
     * where it came from, the key of its {@linkplain SoundKeys#chain
     * fallbacks} that supplied it (null when silent), that file, and the
     * settings it plays at.
     */
    public record Resolution(PcmClip clip, Source source, String level, Path file, SoundDef def) {

        /** Whether it came from a key other than the one asked for - a sound it shares. */
        public boolean shared(String key) {
            return level != null && !level.equals(key);
        }
    }

    /** What {@code key} actually sounds like right now: the player's file, the pack's file, or silence. */
    public static PcmClip resolve(String key) {
        return resolved(key).clip();
    }

    private static synchronized Resolution resolved(String key) {
        if (packGeneration != SoundPack.generation()) {
            packGeneration = SoundPack.generation();
            RESOLVED.clear();
        }
        Resolution r = RESOLVED.get(key);
        if (r == null) {
            r = find(key, true);
            RESOLVED.put(key, r);
        }
        return r;
    }

    /**
     * Where {@code key}'s sound comes from, answered from what files
     * <em>exist</em> without decoding them - what the Sounds menu shows.
     */
    public static synchronized Resolution describe(String key) {
        return find(key, false);
    }

    /** Which resolution step {@code key} currently lands on ({@link #describe}). */
    public static Source sourceOf(String key) {
        return describe(key).source();
    }

    private static Resolution find(String key, boolean decode) {
        for (String k : SoundKeys.chain(key)) {
            SoundDef own = DEFS.get(k);
            if (own != null) {
                if (!own.file().isEmpty()) {
                    Path f = resolvePath(own.file());
                    if (Files.isRegularFile(f)) {
                        PcmClip clip = decode ? SoundLoader.load(f) : null;
                        if (!decode || !clip.isEmpty()) return new Resolution(clip, Source.FILE, k, f, own);
                    }
                }
                if (!own.usePack()) return new Resolution(PcmClip.SILENCE, Source.MUTED, k, null, own);
            }
            Path p = SoundPack.fileAt(k);
            if (p != null) {
                PcmClip clip = decode ? SoundLoader.load(p) : null;
                if (!decode || !clip.isEmpty()) return new Resolution(clip, Source.PACK, k, p, packDef(key, k));
            }
        }
        return new Resolution(PcmClip.SILENCE, Source.SILENT, null, null, SoundDef.packDefault(key));
    }

    /**
     * The pack's settings for {@code key} heard through {@code level}: its own
     * override, else the shared sound's, else the pack's defaults - looping
     * as {@code key} does.
     */
    private static SoundDef packDef(String key, String level) {
        if (!SoundPack.hasOverride(key) && SoundPack.hasOverride(level)) {
            SoundPack.Playback p = SoundPack.playbackFor(level);
            return new SoundDef(key, "", p.volume(), p.pitch(), SoundKeys.isLooping(key), p.varyPitch(), true);
        }
        return SoundDef.packDefault(key);
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

    /**
     * Decode every file in the pack now, on a background thread, so the first
     * footstep or swing does not wait for its MP3 to be read. The songs are
     * left to {@link Music}, which decodes one when it is about to play it.
     */
    public static void preload() {
        Path root = SoundPack.root();
        Path music = root.resolve(SoundKeys.MUSIC);
        Thread t = new Thread(() -> {
            if (!Files.isDirectory(root)) return;
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(f -> {
                    String n = f.getFileName().toString().toLowerCase();
                    return (n.endsWith(".mp3") || n.endsWith(".wav")) && !f.startsWith(music);
                }).forEach(SoundLoader::load);
            } catch (IOException | RuntimeException e) {
                System.err.println("[sound] preloading " + root + ": " + e.getMessage());
            }
        }, "sound-preload");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    // --- the player's own choices ------------------------------------------------

    /**
     * Play {@code file} for {@code key} from now on - the Sounds menu's
     * "Choose…" - and save the choice. A shared key ({@code player/attack})
     * takes it for everything that falls back to it.
     */
    public static void choose(String key, Path file) {
        put(new SoundDef(key, file.toAbsolutePath().toString(), 1, 1, SoundKeys.isLooping(key), true, false));
        save();
    }

    /** Silence {@code key} (and what falls back to it), and save that. */
    public static void mute(String key) {
        put(new SoundDef(key, "", 1, 1, SoundKeys.isLooping(key), true, false));
        save();
    }

    /** Back to the game's own sound for {@code key}, and save that. */
    public static void reset(String key) {
        remove(key);
        save();
    }

    /** The player's assignment for {@code key}, or {@code null} if none. */
    public static synchronized SoundDef get(String key) {
        return DEFS.get(key);
    }

    /**
     * The settings {@code key} plays at: the player's assignment when there
     * is one, else the pack's settings for it. Never {@code null}.
     */
    public static synchronized SoundDef definition(String key) {
        SoundDef def = DEFS.get(key);
        return def != null ? def : SoundDef.packDefault(key);
    }

    public static synchronized void put(SoundDef def) {
        if (def == null || def.key().isEmpty()) return;
        DEFS.put(def.key(), def);
        RESOLVED.clear();
    }

    public static synchronized void remove(String key) {
        DEFS.remove(key);
        RESOLVED.clear();
    }

    /** Every assignment, for saving. */
    public static synchronized List<SoundDef> all() {
        return new ArrayList<>(DEFS.values());
    }

    public static synchronized void clear() {
        DEFS.clear();
        RESOLVED.clear();
    }

    /** Replace the whole assignment set (loading {@code sounds.json}). */
    public static synchronized void setAll(List<SoundDef> defs) {
        DEFS.clear();
        RESOLVED.clear();
        if (defs == null) return;
        for (SoundDef d : defs) {
            if (d != null && !d.key().isEmpty()) DEFS.put(d.key(), d);
        }
    }

    /** Keep the player's choices in {@code dir} ({@code config/}) rather than the pack folder. */
    public static synchronized void useStore(Path dir) {
        storeDir = dir;
    }

    private static synchronized SoundStore store() {
        return storeDir != null ? new SoundStore(storeDir) : new SoundStore();
    }

    /** Load the player's choices from {@code sounds.json} — called once at startup. */
    public static void load() {
        setAll(store().load());
    }

    /** Save the player's choices to {@code sounds.json}; false (and a line on the console) if it cannot be written. */
    public static boolean save() {
        try {
            store().save(all());
            return true;
        } catch (RuntimeException e) {
            System.err.println("[sound] cannot save your sounds: " + e.getMessage());
            return false;
        }
    }
}
