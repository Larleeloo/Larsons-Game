package com.larsons.game.audio;

import com.larsons.game.combat.Combat;
import com.larsons.game.combat.Dummy;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Slot;
import com.larsons.game.world.Chest;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Player;
import com.larsons.game.world.World;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The sound hooks of everything animated in the world, updated once a frame
 * - the engine's {@code SceneSounds}, for the game's animations:
 *
 * <ul>
 *   <li><b>the player</b>: the animation she is playing, in her body's voice
 *       - {@code player/feminine/<state>} or {@code player/masculine/<state>}
 *       for every one of the 56 states;</li>
 *   <li><b>every chest</b>: {@code chest/<id>/idle}, {@code open}, {@code
 *       opened}, {@code close}, quieter the farther it is from the player and
 *       panned to the side of the screen it is on;</li>
 *   <li><b>every training dummy</b>: {@code combat/attack_dummy/hit}, {@code
 *       fall}, {@code rise}, placed the same way;</li>
 *   <li><b>the fighting</b>: the dice, blows, shots and spells ({@code
 *       combat/...}), where they happen.</li>
 * </ul>
 *
 * <p>Other one-off events (picking an item up, the inventory) play where
 * they happen.
 */
public final class WorldSounds {

    /** Metres to the side at which a sound is fully in one ear; twice it, it is a quarter as loud. */
    public static final double HALF_WIDTH = 4.0;

    private final AnimationSound player = new AnimationSound();
    private final Map<Chest, AnimationSound> chests = new IdentityHashMap<>();
    private final Map<Dummy, AnimationSound> dummies = new IdentityHashMap<>();

    public void update(World world, OrbitCamera camera) {
        Player p = world.player();
        String body = p.wardrobe().get(Slot.BODY);
        player.update(SoundKeys.player(body, p.state()), p.stateTime(), 1.0, 0.0);

        Vec3 listener = p.ground();
        Vec3 right = camera.right();
        for (Chest c : world.chests()) {
            Vec3 d = c.position().sub(listener);
            double across = d.x() * right.x() + d.z() * right.z();
            double[] at = Sounds.placement(across, d.horizontalLength(), HALF_WIDTH);
            chests.computeIfAbsent(c, k -> new AnimationSound())
                    .update(SoundKeys.chest(c.id(), c.state().key()), c.time(), at[0], at[1]);
        }
        for (Dummy d : world.dummies()) {
            double[] at = placement(d.position(), listener, right);
            dummies.computeIfAbsent(d, k -> new AnimationSound())
                    .update(SoundKeys.dummy(Dummy.SPRITES, d.phase().key()), d.time(), at[0], at[1]);
        }
        for (Combat.Sound s : world.combat().drainSounds()) {
            double[] at = placement(s.at(), listener, right);
            Sounds.play(s.key(), at[0], at[1]);
        }
    }

    private static double[] placement(Vec3 at, Vec3 listener, Vec3 right) {
        Vec3 d = at.sub(listener);
        double across = d.x() * right.x() + d.z() * right.z();
        return Sounds.placement(across, d.horizontalLength(), HALF_WIDTH);
    }

    /** Silence everything this started (leaving the scene). */
    public void stop() {
        player.stop();
        for (AnimationSound s : chests.values()) s.stop();
        for (AnimationSound s : dummies.values()) s.stop();
    }
}
