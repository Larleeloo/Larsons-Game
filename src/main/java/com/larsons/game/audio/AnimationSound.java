package com.larsons.game.audio;

/**
 * The sound hook of one animated thing - a character, a chest: told every
 * frame which animation it is playing, it plays that animation's sound.
 *
 * <ul>
 *   <li>A held animation's sound ({@link SoundKeys#isLooping}: a walk, an
 *       idle, the chest standing open) starts with the animation and loops
 *       until the animation changes.</li>
 *   <li>A one-shot's sound (a swing, the lid thrown open) plays once as the
 *       animation starts - and again each time it starts over, a second
 *       swing straight after the first - and rings out to its end.</li>
 *   <li>Where the thing is relative to the listener can change while it
 *       plays ({@link #update}'s volume and pan): a chest's hum follows the
 *       camera round.</li>
 * </ul>
 *
 * <p>The engine wires each trigger where it happens; here every animation of
 * every character and object goes through one of these, so giving an
 * animation a sound is only ever dropping a file into {@code assets/sounds/}.
 * A key with no file is silent and costs one lookup when the animation
 * starts.
 */
public final class AnimationSound {

    private String key = "";
    private double lastTime;
    private SoundMixer.Voice voice;
    private double startVolume, startScale;

    /**
     * This frame's animation.
     *
     * @param key         its sound key ({@link SoundKeys#player}, {@link SoundKeys#chest})
     * @param time        seconds into it (going back to 0 means it started over)
     * @param volumeScale how loud, for where it is (1: right here)
     * @param pan         where, -1 left … +1 right
     */
    public void update(String key, double time, double volumeScale, double pan) {
        boolean restarted = key.equals(this.key) && time + 1e-6 < lastTime;
        lastTime = time;
        if (!key.equals(this.key) || restarted) {
            if (voice != null && SoundKeys.isLooping(this.key)) voice.stop();
            this.key = key;
            voice = Sounds.play(key, volumeScale, pan);
            startScale = volumeScale;
            startVolume = voice == null ? 0 : voice.volume();
            return;
        }
        if (voice != null && voice.isPlaying()) {
            if (startScale > 1e-6) voice.setVolume(startVolume * volumeScale / startScale);
            voice.setPan(pan);
        }
    }

    /** The key of the animation last played. */
    public String key() { return key; }

    /** Stop whatever is sounding (leaving the scene) and forget the animation. */
    public void stop() {
        if (voice != null) voice.stop();
        voice = null;
        key = "";
        lastTime = 0;
    }
}
