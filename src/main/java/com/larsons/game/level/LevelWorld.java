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
        world.setSky(level.sky());
        double h = level.half();
        world.setBounds(-h, h, -h, h);
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
        world.dummies().clear();
        world.fliers().clear();
        world.soundSpots().clear();
        Build b = new Build(world, terrain, sprites, tuning);
        world.setSpawnPoint(terrain.onGround(Vec3.ZERO));
        world.player().setPosition(Vec3.ZERO);
        for (LevelObject o : objects) {
            ObjectType t = ObjectTypes.get(o.type());
            if (t != null && t.spawner() != null) t.spawner().spawn(o, b);
        }
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

    /** {@code o} as a piece of scenery standing on {@code terrain}, or null when its sprite is not there. */
    public static Scenery.Placed placed(LevelObject o, Terrain terrain, ScenerySprites sprites) {
        ScenerySprites.Sprite s = sprites.sprite(o.str("sprite", ""));
        if (s == null) return null;
        // its loop a little off its neighbours', always the same for the same object
        double jitter = ((o.id() * 2654435761L) & 0xFFFF) / 65535.0 * 0.6;
        return new Scenery.Placed(s, o.x(), terrain.height(o.x(), o.z()) + o.lift(), o.z(), o.yaw(),
                o.bool("mirrored", false), Scenery.windPhase(o.x(), o.z(), jitter));
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
