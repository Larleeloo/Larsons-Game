package com.larsons.game.audio;

import com.larsons.game.math.Vec3;

/**
 * Something in the world that keeps making one sound - a stream, crickets, a
 * chest's hum - for as long as it is told to every frame: the sound starts
 * with the first {@link #update}, a loop loops ({@link SoundKeys#isLooping}),
 * and it is quieter the farther the listener is and panned to its side
 * ({@link Sounds#placement}), following them round. Changing the key swaps
 * the sound; {@link #stop} silences it (leaving the scene, the thing taken
 * away).
 *
 * <p>For a sound object placed in a level, or any ambience:
 *
 * <pre>
 *   emitter.update("ambient/stream", at, listener, camera.right());   // each frame
 *   emitter.stop();                                                    // on leaving
 * </pre>
 */
public final class SoundEmitter {

    private final AnimationSound sound = new AnimationSound();

    /**
     * This frame: {@code key} sounding at {@code at}, heard from {@code
     * listener} with {@code right} the listener's right (the camera's).
     */
    public void update(String key, Vec3 at, Vec3 listener, Vec3 right) {
        Vec3 d = at.sub(listener);
        double across = d.x() * right.x() + d.z() * right.z();
        double[] p = Sounds.placement(across, d.horizontalLength(), WorldSounds.HALF_WIDTH);
        sound.update(key, 0, p[0], p[1]);
    }

    /** This frame: {@code key} sounding everywhere at once (an ambience bed), at {@code volume}. */
    public void update(String key, double volume) {
        sound.update(key, 0, volume, 0);
    }

    /** The key last sounded, or "" once stopped. */
    public String key() {
        return sound.key();
    }

    /** Silence it. */
    public void stop() {
        sound.stop();
    }
}
