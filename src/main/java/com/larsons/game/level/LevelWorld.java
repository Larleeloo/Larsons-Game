package com.larsons.game.level;

import com.larsons.game.combat.CombatTuning;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.world.Scenery;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Level} made real: its ground as the {@link Terrain} she walks on
 * (the sculpted heights, the vines on them, its named spots), its {@link
 * Scenery}, and everything else in it put into a {@link World} by its kind's
 * {@link Spawner} - she stands at its spawn, kept within its edges, under
 * its sky.
 *
 * <p>Pure data, no GL: the play-test, the editor's live view and the tests
 * all build their worlds here.
 */
public final class LevelWorld {

    /** What a kind of object becomes in the world when its level is played. */
    @FunctionalInterface
    public interface Spawner {
        void spawn(LevelObject o, Build b);
    }

    /** What a {@link Spawner} builds with and into. */
    public static final class Build {
        private final World world;
        private final Terrain terrain;
        private final ScenerySprites sprites;
        private final CombatTuning tuning;
        private final List<Scenery.Placed> scenery = new ArrayList<>();

        private String levelName = "";

        /** The name of the level being built. */
        public String levelName() { return levelName; }

        Build(World world, Terrain terrain, ScenerySprites sprites, CombatTuning tuning) {
            this.world = world;
            this.terrain = terrain;
            this.sprites = sprites;
            this.tuning = tuning;
        }

        public World world() { return world; }

        public Terrain terrain() { return terrain; }

        public CombatTuning tuning() { return tuning; }

        /** Where {@code o} stands: on the ground under it, lifted as it is. */
        public Vec3 onGround(LevelObject o) {
            return new Vec3(o.x(), terrain.height(o.x(), o.z()) + o.lift(), o.z());
        }

        /** Plant {@code o}, a {@link ObjectTypes#SCENERY}, if its sprite is there. */
        public void plant(LevelObject o) {
            Scenery.Placed p = placed(o, terrain, sprites);
            if (p != null) scenery.add(p);
        }

        /** Stand her at {@code o}, facing its way: where she starts, and comes back to when knocked out. */
        public void spawnAt(LevelObject o) {
            Vec3 at = onGround(o);
            world.setSpawnPoint(at);
            world.player().setPosition(at);
            world.player().snapHeading(o.yaw());
        }
    }

    private LevelWorld() {}

    /**
     * Build {@code level} into {@code world} - which should be new: its
     * ground, its scenery from {@code sprites}, its sky, its edges, and every
     * object - with the fighting tuned by {@code tuning}.
     */
    public static void populate(World world, Level level, ScenerySprites sprites, CombatTuning tuning) {
        Terrain terrain = terrain(level);
        stage(world, level, terrain);
        spawnAll(world, level.objects(), level, terrain, sprites, tuning);
        world.equipSelected();
    }

    /** Set {@code world} on {@code level}'s ground ({@code terrain}, made by {@link #terrain}), under its sky, within its edges. */
    public static void stage(World world, Level level, Terrain terrain) {
        world.setTerrain(terrain);
        world.setGround(level.ground());
        world.setWater(level.water());
        world.setSky(level.sky());
        world.setWeather(level.weather());
        world.setCover(level.cover());
        world.setLife(level.life());
        world.ammo().setEnabled(usesAmmo(level));
        world.startDay();
        double h = level.half();
        world.setBounds(-h, h, -h, h);
    }

    /** Whether weapons use up ammunition in {@code level} (its {@code ammo} setting; yes unless it says no). */
    public static boolean usesAmmo(Level level) {
        return !Boolean.FALSE.equals(level.extra().get("ammo"));
    }

    /**
     * Set {@code world} inside a building, on {@code floor}: flat ground, no
     * water, nothing coming by, within the floor's edges - under the sky
     * outside, its clock going on. Returns the floor's ground, to {@link
     * #spawnAll} its objects on.
     */
    public static Terrain stageIndoors(World world, Level floor) {
        return stageIndoors(world, floor, terrain(floor));
    }

    /** {@link #stageIndoors(World, Level)} on {@code terrain}, the floor's ground made already. */
    public static Terrain stageIndoors(World world, Level floor, Terrain terrain) {
        world.setTerrain(terrain);
        world.setGround(com.larsons.game.world.GroundMap.NONE);
        world.setWater(com.larsons.game.world.WaterMap.NONE);
        world.setLife(Interiors.life());
        world.setIndoors(true);
        double h = floor.half();
        world.setBounds(-h, h, -h, h);
        return terrain;
    }

    /**
     * Put {@code objects} of {@code level} into {@code world} on {@code
     * terrain} - the items, chests, dummies and fliers it had taken away
     * first, its scenery replaced - and her at the spawn (in the middle if
     * there is none).
     */
    public static void spawnAll(World world, Iterable<LevelObject> objects, Level level, Terrain terrain,
                                ScenerySprites sprites, CombatTuning tuning) {
        world.items().clear();
        world.chests().clear();
        world.stores().clear();
        world.buildings().clear();
        world.dummies().clear();
        world.fliers().clear();
        world.soundSpots().clear();
        world.lights().clear();
        world.raySpots().clear();
        world.critters().spots().clear();          // (those about stay while their spot is the same)
        world.animals().herds().clear();           // (and a herd's, while it is the same)
        world.spots().clear();
        world.folks().clear();
        world.quests().clear();
        world.hordeSpawns().clear();
        Build b = new Build(world, terrain, sprites, tuning);
        b.levelName = level.name() == null ? "" : level.name();
        world.setSpawnPoint(terrain.onGround(Vec3.ZERO));
        world.player().setPosition(Vec3.ZERO);
        for (LevelObject o : objects) {
            ObjectType t = ObjectTypes.get(o.type());
            if (t != null && t.spawner() != null) t.spawner().spawn(o, b);
        }
        world.showRacks();                         // (what the weapon racks hold, up on their pegs)
        world.setScenery(new Scenery(b.scenery, level.ground()));
    }

    /** {@code level}'s ground: its heights, its vines' walls, its named spots. */
    public static Terrain terrain(Level level) {
        HeightField hf = level.heights();
        List<Terrain.Wall> walls = new ArrayList<>();
        for (LevelObject o : level.ofType(ObjectTypes.VINES)) walls.add(wall(o, hf));
        List<Terrain.Place> places = new ArrayList<>();
        for (LevelObject o : level.ofType(ObjectTypes.MARKER)) {
            places.add(new Terrain.Place(o.str("name", "spot"), o.x(), o.z()));
        }
        LevelObject spawn = level.spawn();
        if (spawn != null) places.add(new Terrain.Place("spawn", spawn.x(), spawn.z()));
        return hf.terrain(walls, places);
    }

    /** How far in from the vines' foot their face's top is looked for, metres. */
    static final double FACE_DEPTH = 4;

    /**
     * The rock face vines {@code o} hang on: its foot through the vines'
     * spot, {@code length} long, facing out the way they face; from the
     * ground just out from it up to the highest ground just in from it.
     */
    public static Terrain.Wall wall(LevelObject o, HeightField hf) {
        Vec3 out = SpriteView.headingVector(o.yaw());
        double len = Math.max(1, o.num("length", 8));
        double ax = -out.z(), az = out.x();               // along the foot
        double x0 = o.x() - ax * len / 2, z0 = o.z() - az * len / 2;
        double x1 = o.x() + ax * len / 2, z1 = o.z() + az * len / 2;
        double bottom = Double.MAX_VALUE, top = -Double.MAX_VALUE;
        for (int k = 0; k <= 8; k++) {
            double f = k / 8.0;
            double fx = x0 + (x1 - x0) * f, fz = z0 + (z1 - z0) * f;
            bottom = Math.min(bottom, hf.height(fx + out.x() * 0.6, fz + out.z() * 0.6));
            for (double in = 0; in <= FACE_DEPTH; in += Terrain.STEP / 2) {
                top = Math.max(top, hf.height(fx - out.x() * in, fz - out.z() * in));
            }
        }
        if (top < bottom + 0.5) top = bottom + 0.5;
        return new Terrain.Wall(x0, z0, x1, z1, out.x(), out.z(), bottom, top);
    }

    /** How far round the pointer a rock face for vines is looked for, metres. */
    static final double FIT_REACH = 4;
    /** The longest a fitted run of vines is, metres. */
    static final double FIT_LONGEST = 40;

    /**
     * Fit vines {@code o} to the rock face nearest its spot (within {@value
     * #FIT_REACH} m): turned to face out of the rock, moved to the face's
     * foot, as long as the face runs on (at most {@value #FIT_LONGEST} m) -
     * so they go where they are clicked on a cliff, however it is turned.
     * False (and {@code o} as it was) when there is no face that near.
     */
    public static boolean fitVines(LevelObject o, HeightField hf) {
        double step = Math.max(0.25, hf.step() / 2);
        // the steepest ground round the spot
        double bx = o.x(), bz = o.z(), best = slope(hf, bx, bz)[2];
        for (double r = step; r <= FIT_REACH; r += step) {
            int n = Math.max(8, (int) Math.round(2 * Math.PI * r / step));
            for (int k = 0; k < n; k++) {
                double a = 2 * Math.PI * k / n, x = o.x() + Math.cos(a) * r, z = o.z() + Math.sin(a) * r;
                double sl = slope(hf, x, z)[2] - r * 0.02;            // (the nearer of two as steep)
                if (sl > best) {
                    best = sl;
                    bx = x;
                    bz = z;
                }
            }
        }
        if (best < Terrain.STEEP * 0.85) return false;
        double[] g = slope(hf, bx, bz);
        double len = Math.hypot(g[0], g[1]);
        double ox = -g[0] / len, oz = -g[1] / len;                    // out of the rock: downhill
        // down the face to its foot
        double fx = bx, fz = bz;
        for (int i = 0; i < 200 && slope(hf, fx, fz)[2] >= Terrain.STEEP * 0.85; i++) {
            fx += ox * step;
            fz += oz * step;
        }
        // along the face, each way, as long as the rock goes on just in from the foot
        double ax = -oz, az = ox, left = 0, right = 0;
        while (right < FIT_LONGEST / 2 && steepIn(hf, fx + ax * (right + step), fz + az * (right + step), ox, oz)) {
            right += step;
        }
        while (left < FIT_LONGEST / 2 && steepIn(hf, fx - ax * (left + step), fz - az * (left + step), ox, oz)) {
            left += step;
        }
        double length = Math.max(1, Math.round((left + right) * 2) / 2.0);
        double mid = (right - left) / 2;
        o.moveTo(fx + ax * mid, fz + az * mid);
        o.turnTo(SpriteView.headingOf(ox, oz));
        o.set("length", length);
        return true;
    }

    /** Whether the ground just in from foot {@code (x, z)} - into the rock, against {@code (ox, oz)} - is a face. */
    private static boolean steepIn(HeightField hf, double x, double z, double ox, double oz) {
        for (double in = 0.5; in <= 1.5; in += 0.5) {
            if (slope(hf, x - ox * in, z - oz * in)[2] >= Terrain.STEEP * 0.85) return true;
        }
        return false;
    }

    /** The ground's slope at {@code (x, z)}: its rise each way (x, z), and how steep it is (rise over run). */
    private static double[] slope(HeightField hf, double x, double z) {
        double d = Math.max(0.25, hf.step() / 2);
        double gx = (hf.height(x + d, z) - hf.height(x - d, z)) / (2 * d);
        double gz = (hf.height(x, z + d) - hf.height(x, z - d)) / (2 * d);
        return new double[]{gx, gz, Math.hypot(gx, gz)};
    }

    /** {@code o} as a piece of scenery standing on {@code terrain}, or null when its sprite is not there. */
    public static Scenery.Placed placed(LevelObject o, Terrain terrain, ScenerySprites sprites) {
        ScenerySprites.Sprite s = sprites.sprite(o.str("sprite", ""));
        if (s == null) return null;
        // its loop a little off its neighbours', always the same for the same object
        double jitter = ((o.id() * 2654435761L) & 0xFFFF) / 65535.0 * 0.6;
        return new Scenery.Placed(s, o.x(), terrain.height(o.x(), o.z()) + o.lift(), o.z(), o.yaw(),
                o.bool("mirrored", false), Scenery.windPhase(o.x(), o.z(), jitter), o.num("scale", 1),
                ObjectTypes.tint(o));
    }

    /** {@code level}'s scenery standing on {@code terrain}, from {@code sprites}. */
    public static Scenery scenery(Level level, Terrain terrain, ScenerySprites sprites) {
        List<Scenery.Placed> out = new ArrayList<>();
        for (LevelObject o : level.objects()) {
            if (!o.type().equals(ObjectTypes.SCENERY)) continue;
            Scenery.Placed p = placed(o, terrain, sprites);
            if (p != null) out.add(p);
        }
        return new Scenery(out, level.ground());
    }
}
