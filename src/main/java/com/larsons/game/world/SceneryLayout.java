package com.larsons.game.world;

import com.larsons.game.sprite.SceneryProfile.Kind;
import com.larsons.game.sprite.ScenerySprites;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where the demo's scenery stands: a meadow round the spawn, planted the
 * same way every run (a fixed seed), from whatever sprites {@code
 * assets/scenery/} has.
 *
 * <ul>
 *   <li><b>Paths</b> of trodden earth, {@value #PATH_WIDTH} m wide, running
 *       straight out from the spawn between the old trees and rocks, then
 *       winding: east, south and west for about forty metres, and north to
 *       the foot of the {@link #HILLS}.</li>
 *   <li><b>Trees</b> where the old low-poly ones stood - (6.5, −8), (9.5, −3),
 *       (−10, 2.5), (−5.5, 9), (7, 8), each species in turn, so every kind of
 *       tree stands near the spawn - a small group where the cottage stood
 *       (−8, −11), more scattered out to {@value #TREES_OUT_TO} m, some in twos
 *       and threes, most on their own; and {@value #GROVES} <b>groves</b>
 *       farther out, each of one species (a few others among them), every
 *       species in turn, standing on forest floor.</li>
 *   <li><b>Rocks</b> where the old three lay - (3.6, −3.4), (−3.2, 4.2),
 *       (4.4, 3.1), each a different rock - a dozen more farther out, and
 *       {@value #ROCK_BEDS} <b>rock beds</b>: two to four rocks together on a
 *       bed of gravel.</li>
 *   <li><b>Meadows</b>: {@value #MEADOWS} open patches of grass thick with
 *       flowers, away from the groves.</li>
 *   <li><b>Grass tufts</b>, about {@value #TUFTS} of them, the variants mixed
 *       (each clump favouring one): a few round the foot of each tree and
 *       rock, and the rest in clumps of one to fifteen within {@value
 *       #TUFTS_OUT_TO} m, thicker towards the middle.</li>
 * </ul>
 *
 * <p>The paths, the forest floor, the gravel and the meadows are the
 * scenery's {@link GroundMap} ({@link Scenery#ground()}), the ground drawn
 * under it.
 *
 * <p>Nothing is planted in a {@link Clearing} - the spawn, the chest, where
 * the weapons lie - nor inside a trunk or a rock ({@link
 * Scenery.Placed#footprint()}), nor on a path (a tuft may grow at its edge);
 * trees stand at least {@value #TREE_SPACING} m apart, so their crowns do not
 * merge into one, and rocks keep {@value #ROCK_GAP} m from anything solid, so
 * the player can walk between them. The groves, rock beds, meadows and paths
 * keep out of the {@link #HILLS}.
 *
 * <p>Everything stands on the {@link Terrain}'s ground ({@link
 * Scenery.Placed#y()}): in the hills, the trees and rocks only where it is
 * gentle enough to stand ({@value #STEEPEST} rise over run), the grass a
 * little steeper, none on the rock faces; and the north path goes on up the
 * ramp onto the plateau ({@link Terrain#trail()}).
 *
 * <p>A kind with no sprites is left out (no rocks folder: no rocks), and a
 * spot a sprite does not fit in is skipped; nothing else moves.
 */
public final class SceneryLayout {

    /** A round patch of ground kept clear of scenery. */
    public record Clearing(double x, double z, double radius) {}

    /** The demo's seed. */
    public static final long SEED = 0x5CE7E2L;
    /** How far out the trees are scattered, in metres. */
    public static final double TREES_OUT_TO = 45;
    /** How far out the grass grows, in metres. */
    public static final double TUFTS_OUT_TO = 30;
    /** About how many grass tufts. */
    public static final int TUFTS = 760;
    /** The closest two trees stand, in metres between their axes. */
    public static final double TREE_SPACING = 2.8;
    /** The least gap between a rock and a trunk or another rock, beyond their footprints. */
    public static final double ROCK_GAP = 0.6;
    /** How wide the paths are, in metres. */
    public static final double PATH_WIDTH = 1.3;
    /**
     * The hills north of the spawn - x from −20 to 22, z from −55 to −18,
     * where the vertical demos' terrain rises - which the groves, rock beds,
     * meadows and paths keep out of: {x from, x to, z from, z to}.
     */
    public static final double[] HILLS = {-20, 22, -55, -18};
    /** The steepest ground a tree or a rock is planted on (rise over run); grass half as steep again. */
    public static final double STEEPEST = 0.6;
    /** How many groves, rock beds and flower meadows. */
    public static final int GROVES = 8, ROCK_BEDS = 5, MEADOWS = 6;

    /** The five old trees' spots. */
    static final double[][] TREE_SPOTS = {{6.5, -8}, {9.5, -3}, {-10, 2.5}, {-5.5, 9}, {7, 8}};
    /** Where the cottage stood: now a group of trees. */
    static final double[] COTTAGE = {-8, -11};
    /** The three old rocks' spots. */
    static final double[][] ROCK_SPOTS = {{3.6, -3.4}, {-3.2, 4.2}, {4.4, 3.1}};
    static final int GROUP = 4, SCATTERED_TREES = 32, SCATTERED_ROCKS = 12;
    /** How far apart lone trees stand (a third of them stand closer, in twos and threes). */
    static final double LONE_SPACING = 6.0;
    /**
     * The paths: the way each sets out (degrees from east, +x, towards south,
     * +z) and how far it runs, metres - the first north until the hills.
     */
    static final double[][] PATHS = {{-78, 60}, {12, 42}, {95, 40}, {-170, 40}};
    /**
     * Where a path starts, metres from the spawn, the length of each of its
     * straight steps, and how far it runs straight before it starts to wind
     * (between the old trees and rocks round the spawn).
     */
    static final double PATH_START = 1.6, PATH_STEP = 0.8, PATH_STRAIGHT = 10;

    private SceneryLayout() {}

    /** The demo's scenery from {@code sprites}, clear of {@code clearings}, on flat ground. */
    public static Scenery demo(ScenerySprites sprites, List<Clearing> clearings) {
        return layout(sprites, clearings, SEED, Terrain.FLAT);
    }

    /** The demo's scenery from {@code sprites}, clear of {@code clearings}, standing on {@code terrain}. */
    public static Scenery demo(ScenerySprites sprites, List<Clearing> clearings, Terrain terrain) {
        return layout(sprites, clearings, SEED, terrain);
    }

    /** The demo's layout with another seed (the same seed always gives the same scenery). */
    public static Scenery layout(ScenerySprites sprites, List<Clearing> clearings, long seed) {
        return layout(sprites, clearings, seed, Terrain.FLAT);
    }

    /** The demo's layout with another seed, on {@code terrain}. */
    public static Scenery layout(ScenerySprites sprites, List<Clearing> clearings, long seed, Terrain terrain) {
        Planter p = new Planter(sprites, clearings, seed, terrain);
        p.paths();
        p.trail();
        p.trees();
        p.groves();
        p.rocks();
        p.meadows();
        p.tufts();
        return new Scenery(p.placed, p.ground);
    }

    /** Whether {@code (x, z)} is within {@code margin} metres of the {@link #HILLS}. */
    public static boolean inHills(double x, double z, double margin) {
        return x > HILLS[0] - margin && x < HILLS[1] + margin && z > HILLS[2] - margin && z < HILLS[3] + margin;
    }

    /**
     * One run of planting: what is planted so far, and the dice - a fresh
     * set for each kind, so the trees stand where they stand whether or not
     * there are rocks, and the rocks whether or not there is grass.
     */
    private static final class Planter {
        final List<ScenerySprites.Sprite> trees, rocks, tufts;
        final List<Clearing> clearings;
        final long seed;
        final Terrain terrain;
        final List<Scenery.Placed> placed = new ArrayList<>();
        final GroundMap ground = new GroundMap();
        /** Each path's points, {x, z}, a step apart. */
        final List<List<double[]>> paths = new ArrayList<>();
        /** Each grove's middle and radius, {x, z, r}. */
        final List<double[]> groves = new ArrayList<>();
        Random rng;
        int tuftCount;

        Planter(ScenerySprites sprites, List<Clearing> clearings, long seed, Terrain terrain) {
            trees = sprites.ofKind(Kind.TREE);
            rocks = sprites.ofKind(Kind.ROCK);
            tufts = sprites.ofKind(Kind.TUFT);
            this.clearings = List.copyOf(clearings);
            this.seed = seed;
            this.terrain = terrain;
        }

        /** New dice for planting {@code kind}. */
        void dice(Kind kind) {
            rng = new Random(seed * 31 + kind.ordinal());
        }

        /** New dice for the {@code n}th of the things that are not one kind (the paths, the groves, the meadows). */
        void dice(int n) {
            rng = new Random(seed * 31 + 100 + n);
        }

        /**
         * The paths: from just outside the spawn, each its own way, its
         * heading wandering in slow curves, a step at a time - stopping
         * short of the hills - and drawn on the ground in dirt.
         */
        void paths() {
            dice(0);
            for (double[] spec : PATHS) {
                double heading = Math.toRadians(spec[0]);
                double p1 = rng.nextDouble() * Math.PI * 2, p2 = rng.nextDouble() * Math.PI * 2;
                double x = PATH_START * Math.cos(heading), z = PATH_START * Math.sin(heading);
                List<double[]> pts = new ArrayList<>();
                pts.add(new double[]{x, z});
                for (double s = 0; s < spec[1]; s += PATH_STEP) {
                    double wind = Math.min(1, s / PATH_STRAIGHT);
                    double h = heading + wind * (0.42 * Math.sin(s / 7.0 + p1) + 0.18 * Math.sin(s / 3.1 + p2));
                    double nx = x + PATH_STEP * Math.cos(h), nz = z + PATH_STEP * Math.sin(h);
                    if (inHills(nx, nz, PATH_WIDTH)) break;
                    ground.stroke(GroundMap.DIRT, x, z, nx, nz, PATH_WIDTH / 2);
                    x = nx;
                    z = nz;
                    pts.add(new double[]{x, z});
                }
                paths.add(pts);
            }
        }

        /**
         * The north path on up the terrain's trail - from where it stopped,
         * short of the hills, to the ramp's foot and up it onto the plateau.
         */
        void trail() {
            List<double[]> trail = terrain.trail();
            if (trail.isEmpty() || paths.isEmpty()) return;
            List<double[]> north = paths.get(0);
            List<double[]> pts = new ArrayList<>();
            pts.add(north.get(north.size() - 1));
            pts.addAll(trail);
            for (int i = 1; i < pts.size(); i++) {
                double[] a = pts.get(i - 1), b = pts.get(i);
                ground.stroke(GroundMap.DIRT, a[0], a[1], b[0], b[1], PATH_WIDTH / 2);
            }
            paths.add(pts);
        }

        /** Metres from {@code (x, z)} to the middle of the nearest path. */
        double toPath(double x, double z) {
            double best = Double.MAX_VALUE;
            for (List<double[]> path : paths) {
                for (int i = 1; i < path.size(); i++) {
                    double[] a = path.get(i - 1), b = path.get(i);
                    double dx = b[0] - a[0], dz = b[1] - a[1], len2 = dx * dx + dz * dz;
                    double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[1]) * dz) / len2));
                    best = Math.min(best, Math.hypot(x - a[0] - t * dx, z - a[1] - t * dz));
                }
            }
            return best;
        }

        void trees() {
            if (trees.isEmpty()) return;
            dice(Kind.TREE);
            for (int i = 0; i < TREE_SPOTS.length; i++) {
                pick(trees);           // (the dice the old spots threw, so the trees after them stand where they stood)
                plant(trees.get(i % trees.size()), TREE_SPOTS[i][0], TREE_SPOTS[i][1], TREE_SPACING);
            }
            int group = 0;
            for (int i = 0; i < 300 && group < GROUP; i++) {
                double a = rng.nextDouble() * Math.PI * 2, r = 3.4 * Math.sqrt(rng.nextDouble());
                if (plant(pick(trees), COTTAGE[0] + r * Math.cos(a), COTTAGE[1] + r * Math.sin(a), TREE_SPACING)) {
                    group++;
                }
            }
            int scattered = 0;
            for (int i = 0; i < 6000 && scattered < SCATTERED_TREES; i++) {
                double[] at = ring(10, TREES_OUT_TO);
                double spacing = rng.nextDouble() < 0.35 ? TREE_SPACING : LONE_SPACING;
                if (plant(pick(trees), at[0], at[1], spacing)) scattered++;
            }
        }

        /**
         * The groves: each round a middle between {@code 14} and {@code
         * TREES_OUT_TO - 6} m out, clear of the hills, the clearings and the
         * other groves, 4.5 to 7 m across; each species in turn the grove's
         * own, most of its trees that one; forest floor under every tree and
         * the grove's heart.
         */
        void groves() {
            if (trees.isEmpty()) return;
            dice(1);
            for (int attempt = 0; attempt < 600 && groves.size() < GROVES; attempt++) {
                double[] c = ring(14, TREES_OUT_TO - 6);
                double r = 4.5 + 2.5 * rng.nextDouble();
                if (inHills(c[0], c[1], r + 2) || !clearOfGroves(c[0], c[1], r + 4)) continue;
                boolean inClearing = false;
                for (Clearing cl : clearings) inClearing |= Math.hypot(c[0] - cl.x(), c[1] - cl.z()) < cl.radius() + r;
                if (inClearing) continue;
                ScenerySprites.Sprite own = trees.get(groves.size() % trees.size());
                int want = (int) Math.round(0.2 * r * r) + 2, got = 0;
                for (int k = 0; k < want * 30 && got < want; k++) {
                    double a = rng.nextDouble() * Math.PI * 2, d = r * Math.sqrt(rng.nextDouble());
                    ScenerySprites.Sprite s = rng.nextDouble() < 0.85 ? own : pick(trees);
                    double spacing = TREE_SPACING + 0.7 * rng.nextDouble();
                    double x = c[0] + d * Math.cos(a), z = c[1] + d * Math.sin(a);
                    if (Math.hypot(x, z) > TREES_OUT_TO || inHills(x, z, 1)) continue;
                    if (plant(s, x, z, spacing)) {
                        got++;
                        ground.disc(GroundMap.FOREST, x, z, 2.2 + 0.5 * rng.nextDouble());
                    }
                }
                if (got == 0) continue;
                ground.disc(GroundMap.FOREST, c[0], c[1], 0.75 * r);
                groves.add(new double[]{c[0], c[1], r});
            }
        }

        boolean clearOfGroves(double x, double z, double gap) {
            for (double[] g : groves) if (Math.hypot(x - g[0], z - g[1]) < g[2] + gap) return false;
            return true;
        }

        void rocks() {
            if (rocks.isEmpty()) return;
            dice(Kind.ROCK);
            for (int i = 0; i < ROCK_SPOTS.length; i++) {
                plant(rocks.get(i % rocks.size()), ROCK_SPOTS[i][0], ROCK_SPOTS[i][1], 0);
            }
            int scattered = 0;
            for (int i = 0; i < 4000 && scattered < SCATTERED_ROCKS; i++) {
                double[] at = ring(7, TREES_OUT_TO - 5);
                if (plant(pick(rocks), at[0], at[1], 0)) scattered++;
            }
            // the rock beds: two to four rocks together, half of them the bed's own, on gravel
            int beds = 0;
            for (int attempt = 0; attempt < 600 && beds < ROCK_BEDS; attempt++) {
                double[] c = ring(9, TREES_OUT_TO - 8);
                if (inHills(c[0], c[1], 4)) continue;
                ScenerySprites.Sprite own = pick(rocks);
                int want = 2 + rng.nextInt(3), got = 0;
                List<Scenery.Placed> bed = new ArrayList<>();
                for (int k = 0; k < 80 && got < want; k++) {
                    double a = rng.nextDouble() * Math.PI * 2, d = 2.2 * Math.sqrt(rng.nextDouble());
                    ScenerySprites.Sprite s = rng.nextDouble() < 0.5 ? own : pick(rocks);
                    if (plant(s, c[0] + d * Math.cos(a), c[1] + d * Math.sin(a), 0)) {
                        got++;
                        bed.add(placed.get(placed.size() - 1));
                    }
                }
                if (got < 2) {
                    placed.removeAll(bed);     // a lone rock is not a bed
                    continue;
                }
                for (Scenery.Placed p : bed) ground.disc(GroundMap.GRAVEL, p.x(), p.z(), p.footprint() + 0.8);
                ground.disc(GroundMap.GRAVEL, c[0], c[1], 1.4);
                beds++;
            }
        }

        /** The flower meadows: open patches, each a few overlapping rounds, away from the groves and the hills. */
        void meadows() {
            dice(2);
            int made = 0;
            for (int attempt = 0; attempt < 400 && made < MEADOWS; attempt++) {
                double[] c = ring(6, TREES_OUT_TO - 6);
                double r = 2.5 + 3.0 * rng.nextDouble();
                if (inHills(c[0], c[1], r + 2) || !clearOfGroves(c[0], c[1], r + 1)) continue;
                ground.disc(GroundMap.MEADOW, c[0], c[1], r);
                for (int k = 0; k < 3; k++) {
                    double a = rng.nextDouble() * Math.PI * 2;
                    ground.disc(GroundMap.MEADOW, c[0] + r * 0.8 * Math.cos(a), c[1] + r * 0.8 * Math.sin(a),
                            r * (0.35 + 0.3 * rng.nextDouble()));
                }
                made++;
            }
        }

        void tufts() {
            if (tufts.isEmpty()) return;
            dice(Kind.TUFT);
            // a few round the foot of each tree and rock
            for (Scenery.Placed s : List.copyOf(placed)) {
                if (s.position().horizontalLength() > TUFTS_OUT_TO) continue;
                int n = 1 + rng.nextInt(3);
                for (int k = 0; k < n; k++) {
                    double a = rng.nextDouble() * Math.PI * 2;
                    double r = s.footprint() + 0.2 + 0.5 * rng.nextDouble();
                    plant(pick(tufts), s.x() + r * Math.cos(a), s.z() + r * Math.sin(a), 0);
                }
            }
            // clumps, each favouring one variant, thicker towards the middle (where the camera
            // mostly looks), from lone tufts to patches of fifteen, until the meadow is full
            for (int clump = 0; clump < 1000 && tuftCount < TUFTS; clump++) {
                double a = rng.nextDouble() * Math.PI * 2, r = TUFTS_OUT_TO * Math.pow(rng.nextDouble(), 0.8);
                double[] c = {r * Math.cos(a), r * Math.sin(a)};
                int size = 1 + rng.nextInt(15);
                double spread = 0.35 + 0.85 * rng.nextDouble();
                ScenerySprites.Sprite favourite = pick(tufts);
                for (int k = 0; k < size && tuftCount < TUFTS; k++) {
                    double x = c[0] + rng.nextGaussian() * spread, z = c[1] + rng.nextGaussian() * spread;
                    if (Math.hypot(x, z) > TUFTS_OUT_TO) continue;
                    plant(rng.nextDouble() < 0.7 ? favourite : pick(tufts), x, z, 0);
                }
            }
        }

        /** A point evenly spread over the ring between {@code inner} and {@code outer} metres out. */
        double[] ring(double inner, double outer) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(inner * inner + rng.nextDouble() * (outer * outer - inner * inner));
            return new double[]{r * Math.cos(a), r * Math.sin(a)};
        }

        ScenerySprites.Sprite pick(List<ScenerySprites.Sprite> from) {
            return from.get(rng.nextInt(from.size()));
        }

        /**
         * Plant {@code sprite} at {@code (x, z)} if it fits there - its own
         * turn, mirroring and wind jitter - and say whether it did. A tree
         * keeps {@code treeSpacing} from every other tree.
         */
        boolean plant(ScenerySprites.Sprite sprite, double x, double z, double treeSpacing) {
            // (its dice are thrown before it is tried, as many whether it fits or not)
            double yaw = rng.nextDouble() * Math.PI * 2;
            boolean mirrored = rng.nextBoolean();
            double jitter = rng.nextDouble() * (sprite.kind() == Kind.TREE ? 0.4 : 0.25);
            Scenery.Placed p = new Scenery.Placed(sprite, x, terrain.height(x, z), z, yaw, mirrored,
                    sprite.frames() > 1 ? Scenery.windPhase(x, z, jitter) : 0);
            double steepest = sprite.kind() == Kind.TUFT ? STEEPEST * 1.5 : STEEPEST;
            if (!terrain.flat() && terrain.slope(x, z) > steepest) return false;
            if (!fits(p, treeSpacing)) return false;
            placed.add(p);
            if (p.kind() == Kind.TUFT) tuftCount++;
            return true;
        }

        boolean fits(Scenery.Placed p, double treeSpacing) {
            for (Clearing c : clearings) {
                if (Math.hypot(p.x() - c.x(), p.z() - c.z()) < c.radius() + p.footprint()) return false;
            }
            // off the paths: a tree or a rock clear of the edge, a tuft at most on it
            double offPath = p.kind() == Kind.TUFT ? PATH_WIDTH / 2 - 0.15 : PATH_WIDTH / 2 + p.footprint() + 0.3;
            if (!paths.isEmpty() && toPath(p.x(), p.z()) < offPath) return false;
            for (Scenery.Placed q : placed) {
                double d = Math.hypot(p.x() - q.x(), p.z() - q.z());
                if (d < apart(p, q, treeSpacing)) return false;
            }
            return true;
        }
    }

    /**
     * How close {@code a} may stand to {@code b}, axis to axis: two trees
     * {@code treeSpacing} (at least {@link #TREE_SPACING}), a rock {@link
     * #ROCK_GAP} clear of a trunk or another rock, grass outside both, and two
     * tufts not on top of one another.
     */
    static double apart(Scenery.Placed a, Scenery.Placed b, double treeSpacing) {
        Kind ka = a.kind(), kb = b.kind();
        double touching = a.footprint() + b.footprint();
        if (ka == Kind.TREE && kb == Kind.TREE) return Math.max(Math.max(TREE_SPACING, treeSpacing), touching);
        if (ka == Kind.TUFT && kb == Kind.TUFT) return 0.12;
        if (ka == Kind.TUFT || kb == Kind.TUFT) return touching;
        return touching + ROCK_GAP;
    }
}
