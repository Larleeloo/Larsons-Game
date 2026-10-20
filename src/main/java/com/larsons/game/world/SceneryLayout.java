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
 *   <li><b>Trees</b> where the old low-poly ones stood - (6.5, −8), (9.5, −3),
 *       (−10, 2.5), (−5.5, 9), (7, 8) - a small group where the cottage stood
 *       (−8, −11), and more scattered out to {@value #TREES_OUT_TO} m, some
 *       in twos and threes, most on their own.</li>
 *   <li><b>Rocks</b> where the old three lay - (3.6, −3.4), (−3.2, 4.2),
 *       (4.4, 3.1), each a different rock - and a dozen more farther out.</li>
 *   <li><b>Grass tufts</b>, about {@value #TUFTS} of them, the variants mixed
 *       (each clump favouring one): a few round the foot of each tree and
 *       rock, and the rest in clumps of one to fifteen within {@value
 *       #TUFTS_OUT_TO} m, thicker towards the middle.</li>
 * </ul>
 *
 * <p>Nothing is planted in a {@link Clearing} - the spawn, the chest, where
 * the weapons lie - nor inside a trunk or a rock ({@link
 * Scenery.Placed#footprint()}); trees stand at least {@value #TREE_SPACING} m
 * apart, so their crowns do not merge into one, and rocks keep {@value
 * #ROCK_GAP} m from anything solid, so the player can walk between them.
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
    public static final int TUFTS = 620;
    /** The closest two trees stand, in metres between their axes. */
    public static final double TREE_SPACING = 2.8;
    /** The least gap between a rock and a trunk or another rock, beyond their footprints. */
    public static final double ROCK_GAP = 0.6;

    /** The five old trees' spots. */
    static final double[][] TREE_SPOTS = {{6.5, -8}, {9.5, -3}, {-10, 2.5}, {-5.5, 9}, {7, 8}};
    /** Where the cottage stood: now a group of trees. */
    static final double[] COTTAGE = {-8, -11};
    /** The three old rocks' spots. */
    static final double[][] ROCK_SPOTS = {{3.6, -3.4}, {-3.2, 4.2}, {4.4, 3.1}};
    static final int GROUP = 4, SCATTERED_TREES = 32, SCATTERED_ROCKS = 12;
    /** How far apart lone trees stand (a third of them stand closer, in twos and threes). */
    static final double LONE_SPACING = 6.0;

    private SceneryLayout() {}

    /** The demo's scenery from {@code sprites}, clear of {@code clearings}. */
    public static Scenery demo(ScenerySprites sprites, List<Clearing> clearings) {
        return layout(sprites, clearings, SEED);
    }

    /** The demo's layout with another seed (the same seed always gives the same scenery). */
    public static Scenery layout(ScenerySprites sprites, List<Clearing> clearings, long seed) {
        Planter p = new Planter(sprites, clearings, seed);
        p.trees();
        p.rocks();
        p.tufts();
        return new Scenery(p.placed);
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
        final List<Scenery.Placed> placed = new ArrayList<>();
        Random rng;
        int tuftCount;

        Planter(ScenerySprites sprites, List<Clearing> clearings, long seed) {
            trees = sprites.ofKind(Kind.TREE);
            rocks = sprites.ofKind(Kind.ROCK);
            tufts = sprites.ofKind(Kind.TUFT);
            this.clearings = List.copyOf(clearings);
            this.seed = seed;
        }

        /** New dice for planting {@code kind}. */
        void dice(Kind kind) {
            rng = new Random(seed * 31 + kind.ordinal());
        }

        void trees() {
            if (trees.isEmpty()) return;
            dice(Kind.TREE);
            for (double[] s : TREE_SPOTS) plant(pick(trees), s[0], s[1], TREE_SPACING);
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
            Scenery.Placed p = new Scenery.Placed(sprite, x, z, yaw, mirrored,
                    sprite.frames() > 1 ? Scenery.windPhase(x, z, jitter) : 0);
            if (!fits(p, treeSpacing)) return false;
            placed.add(p);
            if (p.kind() == Kind.TUFT) tuftCount++;
            return true;
        }

        boolean fits(Scenery.Placed p, double treeSpacing) {
            for (Clearing c : clearings) {
                if (Math.hypot(p.x() - c.x(), p.z() - c.z()) < c.radius() + p.footprint()) return false;
            }
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
