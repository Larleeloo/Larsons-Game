package com.larsons.game.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The music: one song at a time - looping, or a shuffled playlist one song
 * after another - for as long as the place it belongs to is on screen, faded
 * in and out.
 *
 * <p>A scene says what it wants as it is entered ({@link #play} one song,
 * {@link #shuffle} a playlist) and that it is done with it as it is left
 * ({@link #stop}); the game loop calls {@link #update} every frame, which
 * does the rest:
 *
 * <ul>
 *   <li>A song is a sound key ({@link SoundKeys#music}, e.g. {@code
 *       music/main_menu}) and is heard like any other - the player's own file
 *       for it (Pause → Sounds → Music), else the pack's, else silence.</li>
 *   <li>It is decoded on a thread of its own, not the game loop: a two-minute
 *       MP3 takes a moment, and the game should not stutter for it. It
 *       starts when it is ready (a playlist's next song is read ahead).</li>
 *   <li>It fades in over {@link #FADE_IN} seconds and out over {@link
 *       #FADE_OUT}. A song asked for while another is fading out waits for the
 *       quiet; asked for again while it is itself fading out (one menu
 *       straight into another), it comes back up from where it was rather
 *       than starting over.</li>
 *   <li>One song loops seamlessly (a song file is made as one loop, {@code
 *       tools/music/}); a playlist's song plays through once, fades as it
 *       ends, and the next one starts.</li>
 * </ul>
 *
 * <p>Like the rest of the sound system it is safe with no audio device: it
 * goes through the motions in silence, keeping time by the frames.
 */
public final class Music {

    /** Seconds a song takes to fade in. */
    public static final double FADE_IN = 2.5;
    /** Seconds a song takes to fade out. */
    public static final double FADE_OUT = 1.5;
    /** How long before a playlist's song ends that the next is read, seconds. */
    static final double READ_AHEAD = 8;

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "music-loader");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** A song read from disk: its audio (empty: silent or muted) and the volume it plays at. */
    record Loaded(String key, PcmClip clip, double volume) {}

    /** The song asked for, "" for none. */
    private static String wanted = "";
    /** The song sounding (or fading), "" for none. */
    private static String current = "";
    private static SoundMixer.Voice voice;
    /** How far faded in the song sounding is, 0..1; its own volume; the scene's level for it. */
    private static double fade, gain, scale = 1;
    /** How long the song sounding has played, and how long it is, seconds. */
    private static double elapsed, length;
    /** The song being decoded, and for which key. */
    private static Future<Loaded> pending;
    private static String pendingKey = "";
    /** A song found to be silent (no file, or muted), not looked for again until asked for anew. */
    private static String silent = "";
    /** A playlist being played (empty: one song, looping), and where in it. */
    private static final List<String> playlist = new ArrayList<>();
    private static int at;
    /** Silent songs skipped in a row: a playlist all of silence stops looking. */
    private static int silentRun;
    private static Random random = new Random();

    private Music() {}

    /** Play the song {@code key} from now on, looping (the one playing fades out first). */
    public static void play(String key) {
        play(key, 1);
    }

    /** {@link #play(String)} at {@code volume} of the song's own level (the menu 1, a level quieter). */
    public static synchronized void play(String key, double volume) {
        playlist.clear();
        asked();
        want(key);
        setScale(volume);
    }

    /**
     * Play {@code keys} one after another in a random order, each once
     * through, then shuffled again - at {@code volume}. Already playing one of
     * them, it carries on with that.
     */
    public static synchronized void shuffle(List<String> keys, double volume) {
        List<String> order = new ArrayList<>(keys == null ? List.of() : keys);
        order.removeIf(k -> k == null || k.isBlank());
        setScale(volume);
        if (order.isEmpty()) {
            stop();
            return;
        }
        if (new ArrayList<>(playlist).stream().sorted().toList().equals(order.stream().sorted().toList())
                && playlist.contains(wanted)) {
            return;                                     // the same playlist: go on as it was
        }
        asked();
        Collections.shuffle(order, random);
        if (order.contains(current)) {                  // carry on with the song playing
            order.remove(current);
            order.add(0, current);
        }
        playlist.clear();
        playlist.addAll(order);
        at = 0;
        want(playlist.get(0));
    }

    /** Fade the music out. */
    public static synchronized void stop() {
        playlist.clear();
        wanted = "";
        silent = "";
    }

    private static void want(String key) {
        String k = key == null ? "" : key.trim();
        if (!k.equals(wanted)) silent = "";
        wanted = k;
    }

    /** A new request from a scene: a silent playlist is looked through again. */
    private static void asked() {
        silentRun = 0;
    }

    private static void setScale(double volume) {
        scale = Math.max(0, Math.min(1, volume));
        apply();
    }

    /** The song asked for, or "" when the music is stopped. */
    public static synchronized String wanted() {
        return wanted;
    }

    /** The song sounding now (fading in or out included), or "". */
    public static synchronized String current() {
        return current;
    }

    /** The playlist being played, in its order (empty: one song, looping). */
    public static synchronized List<String> playlist() {
        return List.copyOf(playlist);
    }

    /** How far the song sounding is faded in, 0..1 (0 with none). */
    public static synchronized double level() {
        return current.isEmpty() ? 0 : fade;
    }

    /** How loud the song sounding is now: its own volume, the scene's level for it, and the fade. */
    public static synchronized double volume() {
        return current.isEmpty() ? 0 : gain * scale * curve(fade);
    }

    /** Seconds the song sounding has played (and, looping, played again). */
    public static synchronized double elapsed() {
        return current.isEmpty() ? 0 : elapsed;
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
        if (!current.isEmpty()) elapsed += dt;
        if (!playlist.isEmpty() && !current.isEmpty() && current.equals(wanted) && length > 0) {
            String next = playlist.get((at + 1) % playlist.size());
            if (elapsed >= length - READ_AHEAD && pending == null && !next.equals(current)) {
                read(next);                                         // ready for when this one ends
            }
            if (elapsed >= length - FADE_OUT) {                     // this one is ending: on to the next
                at = (at + 1) % playlist.size();
                if (at == 0) reshuffle();
                want(playlist.get(at));
                if (wanted.equals(current)) elapsed = 0;           // a playlist of one: it just goes round
            }
        }
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
        if (wanted.equals(silent)) {
            if (playlist.size() > 1 && silentRun < playlist.size()) {  // a silent song in a playlist: the next
                silentRun++;
                skip();
            }
            return;
        }
        if (pending == null || !pendingKey.equals(wanted)) read(wanted);
        if (!pending.isDone()) return;
        Loaded song = result(pending);
        pending = null;
        pendingKey = "";
        if (song == null || song.clip().isEmpty() || song.volume() <= 0) {
            silent = wanted;
            return;
        }
        gain = song.volume();
        length = song.clip().seconds();
        elapsed = 0;
        fade = Math.min(1, dt / FADE_IN);
        voice = Sounds.startMusic(wanted, song.clip(), Math.max(1e-4, gain * scale * curve(fade)));
        if (voice == null) {                                        // sound is switched off
            silent = wanted;
            return;
        }
        current = wanted;
        silentRun = 0;
    }

    /** A silent song in the playlist: go on to the next (no fade - nothing was sounding). */
    private static void skip() {
        at = (at + 1) % playlist.size();
        if (at == 0) reshuffle();
        want(playlist.get(at));
    }

    /** Round again: a new order, not starting with the song that just played. */
    private static void reshuffle() {
        String last = playlist.get(playlist.size() - 1);
        Collections.shuffle(playlist, random);
        if (playlist.size() > 1 && playlist.get(0).equals(last)) Collections.swap(playlist, 0, playlist.size() - 1);
    }

    /** Forget everything at once, with no fade (tests; the game shutting down). */
    public static synchronized void reset() {
        if (voice != null) voice.stop();
        voice = null;
        wanted = current = pendingKey = silent = "";
        pending = null;
        playlist.clear();
        at = silentRun = 0;
        fade = gain = elapsed = length = 0;
        scale = 1;
    }

    /** Shuffle with this random source from now on (tests: a known order). */
    static synchronized void useRandom(Random r) {
        random = r;
    }

    private static void apply() {
        if (voice != null) voice.setVolume(gain * scale * curve(fade));
    }

    /** A fade's loudness at {@code f} of the way: slow to start, so a fade-in creeps up and a fade-out tails off. */
    static double curve(double f) {
        double x = Math.max(0, Math.min(1, f));
        return x * x;
    }

    private static void read(String key) {
        pendingKey = key;
        pending = load(key);
    }

    /** Read {@code key}'s song as {@link Sounds} would play it, on the music's own thread. */
    static Future<Loaded> load(String key) {
        return LOADER.submit(() -> {
            Sounds.Resolution r = Sounds.describe(key);
            if (r.file() == null) return new Loaded(key, PcmClip.SILENCE, 0);
            return new Loaded(key, SoundLoader.load(r.file()), r.def().volume());
        });
    }

    static Loaded result(Future<Loaded> f) {
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
