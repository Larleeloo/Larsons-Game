package com.larsons.game.audio;

import java.util.concurrent.Future;

/**
 * Listening to a song before choosing it - Creative Mode's music picker:
 * play and pause, where it is and how long it is, and a jump to anywhere in
 * it (the timeline slider). It plays beside {@link Music} rather than through
 * it, at once and without fades, and loops.
 *
 * <p>Time is kept by the frames ({@link #update}), so the slider moves the
 * same with or without an audio device; a jump starts the song afresh from
 * there. While the slider is held ({@link #scrub}) it is silent, and plays on
 * from where it was let go ({@link #release}).
 */
public final class MusicPreview {

    private String key = "";
    private Future<Music.Loaded> pending;
    private PcmClip clip = PcmClip.SILENCE;
    private double gain = 1;
    private SoundMixer.Voice voice;
    /** Whether it has been started since it was last silenced (with no audio device, it starts without a voice). */
    private boolean sounding;
    private double position;
    private boolean playing, scrubbing, resume;

    /** Ready {@code key}'s song to listen to (read in the background), from its start, paused. */
    public void open(String key) {
        String k = key == null ? "" : key.trim();
        if (k.equals(this.key)) return;
        close();
        this.key = k;
        if (!k.isEmpty()) pending = Music.load(k);
    }

    /** The song open, or "". */
    public String key() { return key; }

    /** Whether it is still being read. */
    public boolean loading() {
        return pending != null && !pending.isDone();
    }

    /** Whether it is there to listen to (read, and not silent). */
    public boolean ready() {
        take();
        return !clip.isEmpty();
    }

    /** Its length, seconds (0 until read). */
    public double length() {
        take();
        return clip.seconds();
    }

    /** Where it is, seconds from its start. */
    public double position() { return position; }

    public boolean playing() { return playing; }

    /** Play from where it is (once read: asked before, it starts as soon as it is). */
    public void play() {
        playing = true;
        if (ready() && !scrubbing) start();
    }

    /** Stop where it is. */
    public void pause() {
        playing = false;
        silence();
    }

    public void toggle() {
        if (playing) pause();
        else play();
    }

    /** Jump to {@code seconds} and play on from there if it was playing. */
    public void seek(double seconds) {
        position = clamp(seconds);
        if (playing && ready() && !scrubbing) start();
    }

    /** The slider held at {@code seconds}: silent while it moves. */
    public void scrub(double seconds) {
        if (!scrubbing) {
            scrubbing = true;
            resume = playing;
            silence();
        }
        position = clamp(seconds);
    }

    /** The slider let go: on from there, if it was playing. */
    public void release() {
        if (!scrubbing) return;
        scrubbing = false;
        if (resume && ready()) start();
    }

    public boolean scrubbing() { return scrubbing; }

    /** Advance by {@code dt} seconds (every frame it is shown). */
    public void update(double dt) {
        if (playing && !scrubbing && !sounding && ready()) start();   // asked to play before it was read
        if (!playing || scrubbing || clip.isEmpty()) return;
        position += dt;
        double len = clip.seconds();
        if (position >= len) position -= len * Math.floor(position / len);  // it loops
    }

    /** Stop and forget the song. */
    public void close() {
        silence();
        key = "";
        pending = null;
        clip = PcmClip.SILENCE;
        position = 0;
        playing = scrubbing = resume = false;
    }

    private void take() {
        if (pending == null || !pending.isDone()) return;
        Music.Loaded l = Music.result(pending);
        pending = null;
        if (l != null) {
            clip = l.clip();
            gain = l.volume();
        }
    }

    private void start() {
        silence();
        voice = Sounds.startMusic(key, clip, Math.max(1e-4, gain), position);
        sounding = true;
    }

    private void silence() {
        if (voice != null) voice.stop();
        voice = null;
        sounding = false;
    }

    private double clamp(double s) {
        take();
        double len = clip.seconds();
        return len <= 0 ? 0 : Math.max(0, Math.min(len - 1e-3, s));
    }
}
