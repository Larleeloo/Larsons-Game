package com.larsons.game.audio;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The music: one song at a time, looping for as long as the place it belongs
 * to is on screen, faded in and out.
 *
 * <p>A scene says which song it wants as it is entered ({@link #play}) and
 * that it is done with it as it is left ({@link #stop}); the game loop calls
 * {@link #update} every frame, which does the rest:
 *
 * <ul>
 *   <li>A song is a sound key ({@link SoundKeys#music}, e.g. {@code
 *       music/main_menu}) and is heard like any other - the player's own file
 *       for it (Pause → Sounds → Music), else the pack's ({@code
 *       assets/sounds/music/main_menu.mp3}), else silence.</li>
 *   <li>It is decoded on a thread of its own, not the game loop: a two-minute
 *       MP3 takes a moment, and the menu should not stutter for it. It starts
 *       when it is ready.</li>
 *   <li>It fades in over {@link #FADE_IN} seconds and out over {@link
 *       #FADE_OUT}. A song asked for while another is fading out waits for the
 *       quiet; asked for again while it is itself fading out (one menu
 *       straight into another), it comes back up from where it was rather
 *       than starting over.</li>
 *   <li>It loops seamlessly: a song file is made as one loop
 *       ({@code tools/music/}).</li>
 * </ul>
 *
 * <p>Like the rest of the sound system it is safe with no audio device: it
 * goes through the motions in silence.
 */
public final class Music {

    /** Seconds a song takes to fade in. */
    public static final double FADE_IN = 2.5;
    /** Seconds a song takes to fade out. */
    public static final double FADE_OUT = 1.5;

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "music-loader");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** A song read from disk: its audio (empty: silent or muted) and the volume it plays at. */
    private record Loaded(String key, PcmClip clip, double volume) {}

    /** The song asked for, "" for none. */
    private static String wanted = "";
    /** The song sounding (or fading), "" for none. */
    private static String current = "";
    private static SoundMixer.Voice voice;
    /** How far faded in the song sounding is, 0..1, and its own volume. */
    private static double fade, gain;
    /** The song being decoded, and for which key. */
    private static Future<Loaded> pending;
    private static String pendingKey = "";
    /** A song found to be silent (no file, or muted), not looked for again until asked for anew. */
    private static String silent = "";

    private Music() {}

    /** Play the song {@code key} from now on, looping (the one playing fades out first). */
    public static synchronized void play(String key) {
        String k = key == null ? "" : key.trim();
        if (!k.equals(wanted)) silent = "";
        wanted = k;
    }

    /** Fade the music out. */
    public static synchronized void stop() {
        wanted = "";
        silent = "";
    }

    /** The song asked for, or "" when the music is stopped. */
    public static synchronized String wanted() {
        return wanted;
    }

    /** The song sounding now (fading in or out included), or "". */
    public static synchronized String current() {
        return current;
    }

    /** How far the song sounding is faded in, 0..1 (0 with none). */
    public static synchronized double level() {
        return current.isEmpty() ? 0 : fade;
    }

    /** Whether the song asked for is still being read from disk. */
    public static synchronized boolean loading() {
        return pending != null && !pending.isDone() && pendingKey.equals(wanted);
    }

    /**
     * Whether there is nothing left to read for the song asked for: it is
     * sounding (fading in included), it was found to be silent, or none is
     * asked for. The loading screen waits for this.
     */
    public static synchronized boolean settled() {
        return wanted.isEmpty() || current.equals(wanted) || wanted.equals(silent);
    }

    /** Advance the fades by {@code dt} seconds and start the song asked for once it is decoded. Every frame. */
    public static synchronized void update(double dt) {
        if (!current.isEmpty() && !current.equals(wanted)) {      // what is no longer wanted fades out first
            fade = Math.max(0, fade - dt / FADE_OUT);
            if (fade <= 0) {
                if (voice != null) voice.stop();
                voice = null;
                current = "";
            } else {
                apply();
            }
            return;
        }
        if (wanted.isEmpty()) return;
        if (current.equals(wanted)) {                               // playing: fade (back) in
            if (fade < 1) {
                fade = Math.min(1, fade + dt / FADE_IN);
                apply();
            }
            return;
        }
        if (wanted.equals(silent)) return;
        if (pending == null || !pendingKey.equals(wanted)) {        // read it, off the game loop
            String key = wanted;
            pendingKey = key;
            pending = LOADER.submit(() -> load(key));
        }
        if (!pending.isDone()) return;
        Loaded song = result(pending);
        pending = null;
        pendingKey = "";
        if (song == null || song.clip().isEmpty() || song.volume() <= 0) {
            silent = wanted;
            return;
        }
        gain = song.volume();
        fade = Math.min(1, dt / FADE_IN);
        voice = Sounds.startMusic(wanted, song.clip(), Math.max(1e-4, gain * curve(fade)));
        if (voice == null) {                                        // sound is switched off
            silent = wanted;
            return;
        }
        current = wanted;
    }

    /** Forget everything at once, with no fade (tests; the game shutting down). */
    public static synchronized void reset() {
        if (voice != null) voice.stop();
        voice = null;
        wanted = current = pendingKey = silent = "";
        pending = null;
        fade = gain = 0;
    }

    private static void apply() {
        if (voice != null) voice.setVolume(gain * curve(fade));
    }

    /** A fade's loudness at {@code f} of the way: slow to start, so a fade-in creeps up and a fade-out tails off. */
    static double curve(double f) {
        double x = Math.max(0, Math.min(1, f));
        return x * x;
    }

    /** Read {@code key}'s song as {@link Sounds} would play it: the player's file, else the pack's (else silence). */
    private static Loaded load(String key) {
        Sounds.Resolution r = Sounds.describe(key);
        if (r.file() == null) return new Loaded(key, PcmClip.SILENCE, 0);
        return new Loaded(key, SoundLoader.load(r.file()), r.def().volume());
    }

    private static Loaded result(Future<Loaded> f) {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            System.err.println("[sound] cannot read the music: " + e.getCause());
            return null;
        }
    }
}
