package com.larsons.game.audio;

import com.larsons.game.combat.Combat;
import com.larsons.game.combat.Dummy;
import com.larsons.game.combat.FlyingDummy;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.world.Chest;
import com.larsons.game.world.GroundMap;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Player;
import com.larsons.game.world.World;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * The sound hooks of everything in the world, updated once a frame - the
 * engine's {@code SceneSounds}, for the game's animations:
 *
 * <ul>
 *   <li><b>the player</b>: the animation she is playing, in her body's voice
 *       - {@code player/feminine/<state>} or {@code player/masculine/<state>}
 *       for every one of her states (a swing, a shot, a jump, the dash);
 *       a <b>footstep</b> each time a foot comes down in a walk, a run, a
 *       sprint or a crouched walk, on the ground under her ({@code
 *       player/<body>/footstep_grass}, {@code _dirt}, {@code _gravel},
 *       {@code _forest}), quieter the more softly she goes; each hand's grip
 *       as she climbs the vines; crouching and standing up; the heavy attack
 *       coming back;</li>
 *   <li><b>every chest</b>: {@code chest/<id>/idle}, {@code open}, {@code
 *       opened}, {@code close}, quieter the farther it is from the player and
 *       panned to the side of the screen it is on;</li>
 *   <li><b>the loot</b>: each item thrown out of a chest ({@code
 *       world/loot_throw}), landing ({@code world/loot_land}) and, Uncommon
 *       and up, its rarity ringing out ({@code world/loot_rare} ...);</li>
 *   <li><b>every training dummy</b>: {@code combat/attack_dummy/hit}, {@code
 *       fall}, {@code rise}, placed the same way; <b>every flying dummy</b>:
 *       its wings while it flies its rounds, the screech before it dives, the
 *       dive, struck, falling, hitting the ground and beating back up;</li>
 *   <li><b>the fighting</b> and what happens to her ({@link Combat#drainSounds}):
 *       the dice, blows, shots and spells, a landing, a hurt, where they
 *       happen;</li>
 *   <li><b>the breeze</b> over it all ({@code ambient/wind}).</li>
 * </ul>
 *
 * <p>Other one-off events (picking an item up, the inventory, the menus) play
 * where they happen.
 */
public final class WorldSounds {

    /** Metres to the side at which a sound is fully in one ear; twice it, it is a quarter as loud. */
    public static final double HALF_WIDTH = 4.0;
    /** How loud the breeze is under everything. */
    public static final double WIND = 0.55;

    private final AnimationSound player = new AnimationSound();
    private final Map<Chest, AnimationSound> chests = new IdentityHashMap<>();
    private final Map<Dummy, AnimationSound> dummies = new IdentityHashMap<>();
    private final Map<FlyingDummy, SoundEmitter> wings = new IdentityHashMap<>();
    private final Map<FlyingDummy, FlyingDummy.Phase> fliers = new IdentityHashMap<>();
    /** How far each item out of a chest has got: 1 thrown, 2 landed. */
    private Map<World.GroundItem, Integer> loot = new IdentityHashMap<>();
    private final SoundEmitter wind = new SoundEmitter();
    private Map<World.SoundSpot, SoundEmitter> spots = new IdentityHashMap<>();

    /** The state the last footstep was counted in, and which half of its cycle that was. */
    private AnimState stepState;
    private double stepHalf;
    private Boolean crouched;
    private boolean heavyWaiting;

    /** This frame's sounds, with every animation as long as its sprite sheets say ({@code durations}). */
    public void update(World world, OrbitCamera camera, ToDoubleFunction<AnimState> durations) {
        Player p = world.player();
        String body = p.wardrobe().get(Slot.BODY);
        player.update(SoundKeys.player(body, p.state()), p.stateTime(), 1.0, 0.0);
        steps(p, body, durations, world.scenery().ground());
        if (crouched != null && crouched != p.crouched()) {
            Sounds.play(SoundKeys.player(body, p.crouched() ? "crouch" : "stand"), 0.8);
        }
        crouched = p.crouched();
        boolean waiting = p.heavyWait() > 0;
        if (heavyWaiting && !waiting && p.stance().heavy() != null) Sounds.play(SoundKeys.combat("heavy_ready"));
        heavyWaiting = waiting;

        Vec3 listener = p.ground();
        Vec3 right = camera.right();
        for (Chest c : world.chests()) {
            double[] at = placement(c.position(), listener, right);
            chests.computeIfAbsent(c, k -> new AnimationSound())
                    .update(SoundKeys.chest(c.id(), c.state().key()), c.time(), at[0], at[1]);
        }
        loot(world, listener, right);
        for (Dummy d : world.dummies()) {
            double[] at = placement(d.position(), listener, right);
            dummies.computeIfAbsent(d, k -> new AnimationSound())
                    .update(SoundKeys.dummy(Dummy.SPRITES, d.phase().key()), d.time(), at[0], at[1]);
        }
        for (FlyingDummy f : world.fliers()) flier(f, listener, right);
        for (Combat.Sound s : world.combat().drainSounds()) {
            double[] at = placement(s.at(), listener, right);
            Sounds.play(s.key(), at[0] * s.volume(), at[1]);
        }
        wind.update(SoundKeys.ambient("wind"), WIND);
        spots(world, listener, right);
    }

    /** The sounds placed in a level, each where it stands; one taken away falls silent. */
    private void spots(World world, Vec3 listener, Vec3 right) {
        Map<World.SoundSpot, SoundEmitter> now = new IdentityHashMap<>();
        for (World.SoundSpot s : world.soundSpots()) {
            SoundEmitter e = spots.remove(s);
            if (e == null) e = new SoundEmitter();
            e.update(s.key(), s.at(), listener, right);
            now.put(s, e);
        }
        for (SoundEmitter gone : spots.values()) gone.stop();
        spots = now;
    }

    /** {@link #update(World, OrbitCamera, ToDoubleFunction)} with the stand-in's animation lengths. */
    public void update(World world, OrbitCamera camera) {
        update(world, camera, s -> s.defaultFrames() / AnimState.DEFAULT_FPS);
    }

    /**
     * Her footsteps, and her grip on the vines: a gait's cycle (its sheet's
     * length) starts with the right heel coming down and has the left come
     * down halfway, as the Blender gaits are authored - so a step sounds each
     * time the time into it crosses a half. Climbing, each hand takes hold
     * the same way, forwards or backwards.
     */
    private void steps(Player p, String body, ToDoubleFunction<AnimState> durations, GroundMap ground) {
        AnimState s = p.state();
        boolean gait = s.loops() && s.speed() > 0;
        boolean climb = s == AnimState.CLIMB;
        if ((!gait && !climb) || (gait && p.airborne())) {
            stepState = null;
            return;
        }
        double cycle = durations.applyAsDouble(s);
        if (!(cycle > 1e-3)) cycle = s.defaultFrames() / AnimState.DEFAULT_FPS;
        double half = Math.floor(p.stateTime() / cycle * 2);
        if (s != stepState) {
            stepState = s;
            stepHalf = half;
            if (gait) footstep(p, body, s, ground);       // the first step, as the gait starts
            return;
        }
        if (half == stepHalf) return;
        stepHalf = half;
        if (gait) footstep(p, body, s, ground);
        else Sounds.play(SoundKeys.player(body, "climb_grip"), 0.8);
    }

    private static void footstep(Player p, String body, AnimState s, GroundMap ground) {
        Vec3 at = p.ground();
        double v = s.speed() < 1.0 ? 0.4 : s.speed() < 2.5 ? 0.62 : s.speed() < 5.0 ? 0.85 : 1.0;
        Sounds.play(SoundKeys.footstep(body, surface(ground, at.x(), at.z())), v);
    }

    /** The footstep surface at {@code (x, z)}: the ground map's material, grass where it has none. */
    public static String surface(GroundMap ground, double x, double z) {
        return switch (ground.material(x, z)) {
            case GroundMap.DIRT -> "dirt";
            case GroundMap.GRAVEL -> "gravel";
            case GroundMap.FOREST -> "forest";
            default -> "grass";
        };
    }

    /** Each item out of a chest: thrown, landing, its rarity rung out. */
    private void loot(World world, Vec3 listener, Vec3 right) {
        double t = world.time();
        Map<World.GroundItem, Integer> now = new IdentityHashMap<>();
        for (World.GroundItem g : world.items()) {
            if (g.source() == null) continue;
            int was = loot.getOrDefault(g, 0), stage = was;
            if (stage < 1 && g.out(t)) {
                double[] at = placement(g.source().position(), listener, right);
                Sounds.play(SoundKeys.world("loot_throw"), at[0], at[1]);
                stage = 1;
            }
            if (stage < 2 && g.landed(t)) {
                double[] at = placement(g.position, listener, right);
                Sounds.play(SoundKeys.world("loot_land"), at[0], at[1]);
                String rarity = SoundKeys.loot(g.item.rarity());
                if (rarity != null) Sounds.play(rarity, at[0], at[1]);
                stage = 2;
            }
            if (stage > 0) now.put(g, stage);
        }
        loot = now;
    }

    /** A flier: its wings while it flies its rounds; each turn of its dive, a hit, a fall, rising again. */
    private void flier(FlyingDummy f, Vec3 listener, Vec3 right) {
        FlyingDummy.Phase phase = f.phase();
        SoundEmitter w = wings.computeIfAbsent(f, k -> new SoundEmitter());
        if (phase == FlyingDummy.Phase.FLY || phase == FlyingDummy.Phase.WARN) {
            w.update(SoundKeys.dummy(FlyingDummy.SPRITES, "fly"), f.position(), listener, right);
        } else {
            w.stop();
        }
        FlyingDummy.Phase was = fliers.put(f, phase);
        if (was != null && was != phase && phase != FlyingDummy.Phase.FLY) {
            double[] at = placement(f.position(), listener, right);
            Sounds.play(SoundKeys.dummy(FlyingDummy.SPRITES, phase.key()), at[0], at[1]);
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
        for (SoundEmitter e : wings.values()) e.stop();
        wind.stop();
        for (SoundEmitter e : spots.values()) e.stop();
    }
}
