package com.larsons.game.audio;

import com.larsons.game.combat.Combat;
import com.larsons.game.combat.Dummy;
import com.larsons.game.combat.FlyingDummy;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.world.Biome;
import com.larsons.game.world.Chest;
import com.larsons.game.world.GroundMap;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Player;
import com.larsons.game.world.RideTrack;
import com.larsons.game.world.World;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
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
 *       {@code _forest}, {@code _sand}, {@code _mud}, {@code _snow}, {@code
 *       _stone}, {@code _crystal}, and {@code _water} wading), quieter the
 *       more softly she goes; each hand's grip
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
 *   <li><b>the breeze</b> over it all ({@code ambient/wind}), and <b>the
 *       biome's own</b> by day and by night ({@code ambient/plains} and
 *       {@code ambient/crickets}, {@code ambient/marsh} and {@code
 *       ambient/frogs} ... world/Biome), heard as much as the country round
 *       her is that biome, so one fades into the next across its edge.</li>
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
    /** How loud a biome's own sound is where it is all round her. */
    public static final double AMBIENCE = 0.6;
    /** How far round her (metres) the country is listened to for its biome. */
    public static final double BIOME_REACH = 7;
    /** How fast one biome's sound fades into the next, of its full volume a second. */
    public static final double BIOME_FADE = 0.4;

    private final AnimationSound player = new AnimationSound();
    private final Map<Chest, AnimationSound> chests = new IdentityHashMap<>();
    private final Map<Dummy, AnimationSound> dummies = new IdentityHashMap<>();
    private final Map<FlyingDummy, SoundEmitter> wings = new IdentityHashMap<>();
    private final Map<FlyingDummy, FlyingDummy.Phase> fliers = new IdentityHashMap<>();
    /** How far each item out of a chest has got: 1 thrown, 2 landed. */
    private Map<World.GroundItem, Integer> loot = new IdentityHashMap<>();
    private final SoundEmitter wind = new SoundEmitter();
    private Map<World.SoundSpot, SoundEmitter> spots = new IdentityHashMap<>();
    /** Each biome's sound playing and how far it has faded in, 0 - 1. */
    private final Map<String, SoundEmitter> beds = new HashMap<>();
    private final Map<String, Double> bedLevels = new HashMap<>();
    private double lastTime = Double.NaN;

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
        steps(p, body, durations, world);
        hooves(p);
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
        double dt = Double.isNaN(lastTime) ? 1 : Math.max(0, Math.min(1, world.time() - lastTime));
        lastTime = world.time();
        biomes(world, listener, dt);
        weather(world);
        spots(world, listener, right);
    }

    /** How loud the weather is at its heaviest; under a building's roof, how much of it is heard. */
    public static final double WEATHER = 0.75, MUFFLED = 0.35;

    /**
     * The weather: rain on everything, a gale (and a thunderstorm's), a
     * sandstorm, the hush of snow - each as loud as it is heavy, muffled
     * inside a building and not heard under a level's roof or in a cave -
     * and the thunder after each bolt of lightning.
     */
    private void weather(World world) {
        com.larsons.game.world.Weather.Now now = world.weatherNow();
        double heard = world.indoors() ? MUFFLED : world.roofed() ? 0 : 1;
        double rain = now.of(com.larsons.game.world.Weather.Kind.RAIN)
                + now.of(com.larsons.game.world.Weather.Kind.THUNDERSTORM);
        double gale = now.of(com.larsons.game.world.Weather.Kind.WIND)
                + 0.7 * now.of(com.larsons.game.world.Weather.Kind.THUNDERSTORM);
        double[] levels = {rain, gale, now.of(com.larsons.game.world.Weather.Kind.SANDSTORM),
                now.of(com.larsons.game.world.Weather.Kind.SNOW)};
        for (int i = 0; i < WEATHER_SOUNDS.length; i++) {
            SoundEmitter e = weatherBeds[i];
            if (levels[i] * heard <= 1e-3) e.stop();
            else e.update(SoundKeys.ambient(WEATHER_SOUNDS[i]), WEATHER * levels[i] * heard);
        }
        for (double loud : now.drainThunder()) {
            if (heard > 0) Sounds.play(SoundKeys.ambient("thunder"), Math.min(1, loud) * Math.max(heard, 0.6));
        }
    }

    /** The weather's held sounds, and what plays each. */
    private static final String[] WEATHER_SOUNDS = {"rain", "gale", "sandstorm", "snowfall"};
    private final SoundEmitter[] weatherBeds = {new SoundEmitter(), new SoundEmitter(), new SoundEmitter(),
            new SoundEmitter()};

    /**
     * How much of each biome's sound to hear at {@code at}, by day {@code day}
     * (0 night - 1 noon): the share of the country round it that is each
     * biome - the spot itself and eight round it - its day sound by day and
     * its night sound by night.
     */
    public static Map<String, Double> biomeMix(World world, Vec3 at, double day) {
        Map<String, Double> mix = new HashMap<>();
        int n = 9;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / (n - 1), r = i == 0 ? 0 : BIOME_REACH;
            Biome b = world.biome(at.x() + Math.cos(a) * r, at.z() + Math.sin(a) * r);
            mix.merge(SoundKeys.ambient(b.ambient()), day / n, Double::sum);
            mix.merge(SoundKeys.ambient(b.ambientNight()), (1 - day) / n, Double::sum);
        }
        return mix;
    }

    /** The biomes' sounds, each faded towards how much of it there is round her. */
    private void biomes(World world, Vec3 at, double dt) {
        double day = world.clock().daylight(world.sky()).day();
        Map<String, Double> want = biomeMix(world, at, day);
        for (String k : want.keySet()) bedLevels.putIfAbsent(k, 0.0);
        for (Iterator<Map.Entry<String, Double>> it = bedLevels.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Double> e = it.next();
            double target = want.getOrDefault(e.getKey(), 0.0), now = e.getValue();
            now += Math.max(-BIOME_FADE * dt, Math.min(BIOME_FADE * dt, target - now));
            SoundEmitter emitter = beds.computeIfAbsent(e.getKey(), k -> new SoundEmitter());
            if (now <= 1e-3 && target <= 1e-3) {
                emitter.stop();
                beds.remove(e.getKey());
                it.remove();
                continue;
            }
            e.setValue(now);
            emitter.update(e.getKey(), AMBIENCE * now);
        }
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
    private void steps(Player p, String body, ToDoubleFunction<AnimState> durations, World world) {
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
            if (gait) footstep(p, body, s, world);       // the first step, as the gait starts
            return;
        }
        if (half == stepHalf) return;
        stepHalf = half;
        if (gait) footstep(p, body, s, world);
        else Sounds.play(SoundKeys.player(body, "climb_grip"), 0.8);
    }

    /**
     * The hooves of the horse she rides: two steps a stride of its walk
     * sheet, one a stride of its run sheet (a canter's, a gallop's beat) -
     * counted on its clock as her footsteps are on hers.
     */
    private void hooves(Player p) {
        String sheet = p.mounted() ? p.horseSheet() : "idle";
        if (sheet.equals("idle")) {
            hoofSheet = null;
            return;
        }
        boolean run = sheet.equals("run");
        int frames = p.rideTrack().frames(sheet);
        double stride = frames > 0 ? frames / RideTrack.FPS : run ? 0.6 : 0.8;
        double beat = Math.floor(p.horseTime() / stride * (run ? 1 : 2));
        if (sheet.equals(hoofSheet) && beat == hoofBeat) return;
        hoofSheet = sheet;
        hoofBeat = beat;
        Sounds.play(SoundKeys.creature("horse", run ? "gallop" : "hooves"), run ? 0.9 : 0.65);
    }

    /** The horse's sheet the last hoof beat was counted in, and which beat. */
    private String hoofSheet;
    private double hoofBeat;

    private static void footstep(Player p, String body, AnimState s, World world) {
        Vec3 at = p.ground();
        double v = s.speed() < 1.0 ? 0.4 : s.speed() < 2.5 ? 0.62 : s.speed() < 5.0 ? 0.85 : 1.0;
        String surface = world.water().depth(at.x(), at.z(), at.y()) > WADING ? "water"
                : surface(world.scenery().ground(), at.x(), at.z());
        Sounds.play(SoundKeys.footstep(body, surface), v);
    }

    /** How deep (metres) the water must be for a step to splash rather than fall on the ground under it. */
    public static final double WADING = 0.04;

    /**
     * The footstep surface at {@code (x, z)}: the ground map's material -
     * the jungle's floor sounds as the forest's, ash as sand, dry grass as
     * grass - grass where it has none.
     */
    public static String surface(GroundMap ground, double x, double z) {
        return switch (ground.material(x, z)) {
            case GroundMap.DIRT -> "dirt";
            case GroundMap.GRAVEL -> "gravel";
            case GroundMap.FOREST, GroundMap.JUNGLE -> "forest";
            case GroundMap.SAND, GroundMap.ASH -> "sand";
            case GroundMap.MUD -> "mud";
            case GroundMap.SNOW -> "snow";
            case GroundMap.STONE -> "stone";
            case GroundMap.CRYSTAL -> "crystal";
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
