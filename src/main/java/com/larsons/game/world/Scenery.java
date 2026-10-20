package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.ScenerySprites;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The scenery standing in a world: trees, grass tufts and rocks, each a
 * sprite ({@link ScenerySprites}) planted at a spot on the ground.
 *
 * <p>Each is drawn as <b>two cards crossing at right angles</b> through its
 * axis - Minecraft's flowers - both showing the same frame, the frame's
 * bottom edge on the ground: from any side it has some width, it stands in
 * the depth buffer like real geometry (the character walks behind a tree and
 * stands in the grass), and it needs no views but the one it was drawn from.
 * Each has a turn of its own about its axis ({@link Placed#yaw()}) and may
 * be mirrored, so a field of three tufts does not look like one.
 *
 * <p><b>Wind.</b> The trees and the grass play their loop on the world's
 * clock, each a little behind or ahead of the next: by how far along {@link
 * #WIND} it stands over how fast a gust travels ({@link #GUST_SPEED}), plus
 * a small jitter of its own. So a gust is seen to roll across the field
 * rather than every blade bending at once. Rocks have one frame.
 *
 * <p><b>In the way.</b> Trees and rocks (a profile radius above 0) are
 * {@link #solids()}: {@link World} keeps the player out of them as it does
 * the chest. Grass is walked through.
 *
 * <p>Pure data - which spot holds what. {@link SceneryRenderer} draws it;
 * {@link SceneryLayout} plants the demo's.
 */
public final class Scenery {

    /** The way the wind blows, along the ground (a unit vector; from the west-south-west). */
    public static final Vec3 WIND = new Vec3(1, 0, 0.35).normalize();
    /** How fast a gust crosses the ground, in metres a second. */
    public static final double GUST_SPEED = 3.0;

    /** None at all - and what a world has until it is given some. */
    public static final Scenery NONE = new Scenery(List.of());

    /**
     * One piece of scenery planted in the world.
     *
     * @param yaw      the turn of its cross about its axis, radians
     * @param mirrored whether its frames are drawn flipped left to right
     * @param phase    seconds its loop runs ahead of the world's clock ({@link #windPhase})
     */
    public record Placed(ScenerySprites.Sprite sprite, double x, double z, double yaw, boolean mirrored,
                         double phase) {

        public SceneryProfile profile() { return sprite.profile(); }

        public SceneryProfile.Kind kind() { return sprite.kind(); }

        /** Where its axis meets the ground. */
        public Vec3 position() { return new Vec3(x, 0, z); }

        /** Metres round its axis the player is kept out of; 0: she walks through it. */
        public double radius() { return sprite.profile().radius(); }

        /**
         * The round patch of ground it stands on, in metres from its axis:
         * a trunk (its radius), a rock (its radius, and at least most of its
         * shadow), the root of a tuft (a quarter of its frame). Nothing else is
         * planted inside it.
         */
        public double footprint() {
            SceneryProfile p = sprite.profile();
            return switch (p.kind()) {
                case TREE -> p.radius();
                case ROCK -> Math.max(p.radius(), 0.7 * p.shadowRadius());
                case TUFT -> Math.max(p.radius(), 0.25 * p.frameWorldSize());
            };
        }

        /** The frame it shows at world time {@code time}: its loop, run ahead by its phase. */
        public int frame(double time) {
            return sprite.frameAt(time + phase);
        }
    }

    private final List<Placed> placed;
    private final List<Placed> solids;
    private final List<Placed> shadowed;
    private final Map<ScenerySprites.Sprite, List<Placed>> bySprite;

    public Scenery(List<Placed> placed) {
        this.placed = List.copyOf(placed);
        List<Placed> solid = new ArrayList<>(), shade = new ArrayList<>();
        Map<ScenerySprites.Sprite, List<Placed>> groups = new LinkedHashMap<>();
        for (Placed p : this.placed) {
            if (p.profile().solid()) solid.add(p);
            if (p.profile().shadowRadius() > 0) shade.add(p);
            groups.computeIfAbsent(p.sprite(), k -> new ArrayList<>()).add(p);
        }
        groups.replaceAll((k, v) -> Collections.unmodifiableList(v));
        this.solids = Collections.unmodifiableList(solid);
        this.shadowed = Collections.unmodifiableList(shade);
        this.bySprite = Collections.unmodifiableMap(groups);
    }

    /** Everything planted, in the order it was planted. */
    public List<Placed> placed() { return placed; }

    /** What the player cannot walk into: the trees and the rocks. */
    public List<Placed> solids() { return solids; }

    /** What casts a shadow on the ground. */
    public List<Placed> shadowed() { return shadowed; }

    /** Everything planted, by sprite - each sprite's crosses drawn together, from its one sheet. */
    public Map<ScenerySprites.Sprite, List<Placed>> bySprite() { return bySprite; }

    public boolean isEmpty() { return placed.isEmpty(); }

    /**
     * How far ahead of the world's clock the loop of something standing at
     * {@code (x, z)} runs: behind by the time a gust takes to come that far
     * along {@link #WIND}, plus {@code jitter} seconds of its own.
     */
    public static double windPhase(double x, double z, double jitter) {
        return -(x * WIND.x() + z * WIND.z()) / GUST_SPEED + jitter;
    }
}
