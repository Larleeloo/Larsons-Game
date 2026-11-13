package com.larsons.game.world;

import com.larsons.game.building.Building;
import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.building.BuildingLook;
import com.larsons.game.building.BuildingProfile;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.world.Surroundings.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Makes what lies round a level ({@link Surroundings}): the near ring - its
 * ground's heights, what the ground is made of, its water, its trees, rocks
 * and grass, its houses - and the far ring's mesh out to the horizon.
 *
 * <p><b>Seamless at the edge.</b> Everything starts from the level's own
 * edge ({@link Edge}, sampled all the way round it): just past the edge the
 * ground is the level's, carried straight on - its height, its paths and
 * streets, its rivers - and over the first tens of metres it turns into the
 * country of the theme, a little more each metre, ragged with noise. A
 * street that runs off the level runs on into the ring; a river runs on to
 * the horizon in its channel; a hill cut by the edge carries on and only
 * then falls or rises into the land round it. The near ring's mesh overlaps
 * the level by a cell (sunk under its ground), so there is no crack to see
 * through.
 *
 * <p><b>The far ring</b> is one mesh - a grid fine near the level and
 * coarser outwards, round the level's square hole - in plain colours (the
 * ground textures' own average colours where its ground is theirs, so the
 * near ring's ground and the far ring's meet in the same colour), with the
 * shapes of the country: a forest's canopy raised over the ground, the
 * mountains' snow, the sea and rivers flat at their surface, a city's
 * streets of houses and its towers, a desert's mesas, the crystal lands'
 * prisms, a volcano. Under the near ring it is tucked a little below it.
 *
 * <p>Pure: no GL here - {@link SurroundRenderer} draws what it makes, and it
 * can be made off the GL thread (the level's maps are sampled first, {@link
 * Edge#of}). The same settings on the same level make the same surroundings.
 */
public final class SurroundGen {

    private SurroundGen() {}

    // =================================================================================================
    // the level's edge
    // =================================================================================================

    /**
     * The level's edge, sampled all the way round just inside it: the
     * ground's height, how much of each material, its water. Sides 0 to 3
     * are north ({@code z = -half}), south, west ({@code x = -half}) and
     * east, each sampled from {@code -half} to {@code +half} along it.
     */
    public static final class Edge {
        /**
         * How far in from the edge it is sampled, metres, at least: a grid
         * step of the level's heights in (their very edge is kept at 0, where
         * the level met the endless plain).
         */
        static final double INSET = 0.3;
        /** How far either way along the edge water is felt (its banks), metres. */
        static final double BANK = 8;

        public final double half, spacing;
        final int n;
        final float[][] h = new float[4][], wet = new float[4][], surface = new float[4][], fx = new float[4][],
                fz = new float[4][];
        final byte[][] kind = new byte[4][];
        final float[][][] w = new float[4][GroundMap.MATERIALS][];
        /** The level's sea (NaN: none). */
        public final double sea;
        /** The mean height of the edge where it is dry. */
        public final double baseY;
        /** How much of the edge each material shows on (the last: grass), and how much of it is under the sea. */
        final double[] share = new double[GroundMap.MATERIALS + 1];
        double underSea, waterShare, highest, lowest;
        /** What stands near the edge, per 100 m of it: trees, how many of them pines, palms; houses, city houses. */
        double trees, pines, palms, houses, cityHouses;

        private Edge(double half, double spacing, double sea) {
            this.half = half;
            this.spacing = spacing;
            this.n = Math.max(2, (int) Math.round(2 * half / spacing));
            this.sea = sea;
            for (int s = 0; s < 4; s++) {
                h[s] = new float[n + 1];
                wet[s] = new float[n + 1];
                surface[s] = new float[n + 1];
                fx[s] = new float[n + 1];
                fz[s] = new float[n + 1];
                kind[s] = new byte[n + 1];
                for (int m = 0; m < GroundMap.MATERIALS; m++) w[s][m] = new float[n + 1];
            }
            baseY = 0;
        }

        private Edge(Edge e, double baseY) {
            half = e.half;
            spacing = e.spacing;
            n = e.n;
            sea = e.sea;
            System.arraycopy(e.h, 0, h, 0, 4);
            System.arraycopy(e.wet, 0, wet, 0, 4);
            System.arraycopy(e.surface, 0, surface, 0, 4);
            System.arraycopy(e.fx, 0, fx, 0, 4);
            System.arraycopy(e.fz, 0, fz, 0, 4);
            System.arraycopy(e.kind, 0, kind, 0, 4);
            System.arraycopy(e.w, 0, w, 0, 4);
            System.arraycopy(e.share, 0, share, 0, share.length);
            underSea = e.underSea;
            waterShare = e.waterShare;
            highest = e.highest;
            lowest = e.lowest;
            this.baseY = baseY;
        }

        /** A level {@code 2 * half} metres a side with no ground of its own: flat grass, dry. */
        public static Edge flat(double half) {
            return of(Terrain.FLAT, GroundMap.NONE, WaterMap.NONE, half, Math.max(0.5, half / 256));
        }

        /** The edge of the level whose ground is {@code t}, {@code g} and {@code water}, sampled {@code spacing} apart. */
        public static Edge of(Terrain t, GroundMap g, WaterMap water, double half, double spacing) {
            Edge e = new Edge(half, spacing, water == null ? Double.NaN : water.seaLevel());
            double dry = 0, drySum = 0, hi = -1e9, lo = 1e9;
            double inset = Math.max(INSET, t.flat() ? 0 : t.step() + 0.05);
            int samples = 0;
            for (int s = 0; s < 4; s++) {
                boolean[] isWet = new boolean[e.n + 1];
                for (int i = 0; i <= e.n; i++) {
                    double u = -half + i * e.spacingOf();
                    double[] p = point(s, u, half - inset);
                    double y = t.height(p[0], p[1]);
                    e.h[s][i] = (float) y;
                    for (int m = 0; m < GroundMap.MATERIALS; m++) {
                        e.w[s][m][i] = g == null || g == GroundMap.NONE ? 0 : (float) g.weight(m, p[0], p[1]);
                    }
                    e.surface[s][i] = Float.NaN;
                    WaterMap.Kind k = water == null ? null : water.kindAt(p[0], p[1]);
                    if (k != null) {
                        double sf = water.surface(p[0], p[1], y);
                        if (!Double.isNaN(sf) && sf > y + 0.02) {
                            e.surface[s][i] = (float) sf;
                            e.kind[s][i] = (byte) k.code();
                            double[] f = water.flowAt(p[0], p[1]);
                            e.fx[s][i] = (float) f[0];
                            e.fz[s][i] = (float) f[1];
                            isWet[i] = true;
                        }
                    }
                    // what shows there
                    int shown = -1;
                    for (int m : GroundMap.ORDER) if (e.w[s][m][i] > 0.5) shown = m;
                    e.share[shown < 0 ? GroundMap.MATERIALS : shown]++;
                    boolean underSea = !Double.isNaN(e.sea) && e.sea > y;
                    if (underSea) e.underSea++;
                    if (isWet[i]) e.waterShare++;
                    if (!isWet[i] && !underSea) {
                        dry++;
                        drySum += y;
                    }
                    hi = Math.max(hi, y);
                    lo = Math.min(lo, y);
                    samples++;
                }
                // the water felt along the edge: a river's banks too
                int reach = Math.max(1, (int) Math.round(BANK / e.spacingOf()));
                int[] sum = new int[e.n + 2];
                for (int i = 0; i <= e.n; i++) sum[i + 1] = sum[i] + (isWet[i] ? 1 : 0);
                for (int i = 0; i <= e.n; i++) {
                    int a = Math.max(0, i - reach), b = Math.min(e.n, i + reach);
                    double f = (sum[b + 1] - sum[a]) / (double) (b - a + 1);
                    e.wet[s][i] = (float) (isWet[i] ? 1 : Math.min(1, f * 2));
                }
                // a dry sample at a river's bank takes the river's water (its banks are the river's)
                for (int i = 0; i <= e.n; i++) {
                    if (isWet[i] || e.wet[s][i] < 0.5) continue;
                    for (int d = 1; d <= reach; d++) {
                        int j = i - d < 0 || !isWet[i - d] ? (i + d <= e.n && isWet[i + d] ? i + d : -1) : i - d;
                        if (j < 0) continue;
                        e.surface[s][i] = e.surface[s][j];
                        e.kind[s][i] = e.kind[s][j];
                        e.fx[s][i] = e.fx[s][j];
                        e.fz[s][i] = e.fz[s][j];
                        break;
                    }
                }
            }
            for (int m = 0; m < e.share.length; m++) e.share[m] /= Math.max(1, samples);
            e.underSea /= Math.max(1, samples);
            e.waterShare /= Math.max(1, samples);
            e.highest = hi;
            e.lowest = lo;
            Edge out = new Edge(e, dry > 0 ? drySum / dry : 0);
            return out;
        }

        private double spacingOf() { return 2 * half / n; }

        /**
         * Count what stands near the edge - within {@code reach} metres of it -
         * for {@link Theme#AUTO}: the trees (and which are pines, palms) and the
         * houses ({@code city}: how many of them are a city's).
         */
        public Edge countNear(List<Scenery.Placed> scenery, List<Building> buildings, double reach) {
            double len = 8 * half / 100;
            for (Scenery.Placed p : scenery) {
                if (p.kind() != SceneryProfile.Kind.TREE || !near(p.x(), p.z(), reach)) continue;
                trees++;
                String name = p.sprite().name();
                if (name.contains("pine")) pines++;
                if (name.contains("palm")) palms++;
            }
            for (Building b : buildings) {
                BuildingProfile pr = b.profile();
                if (pr.furniture() || pr.fixture() || pr.room() != null || !near(b.position().x(), b.position().z(),
                        reach)) {
                    continue;
                }
                houses++;
                if (pr.city()) cityHouses++;
            }
            trees /= len;
            pines /= len;
            palms /= len;
            houses /= len;
            cityHouses /= len;
            return this;
        }

        private boolean near(double x, double z, double reach) {
            return Math.max(Math.abs(x), Math.abs(z)) > half - reach;
        }

        /** Where along side {@code s} a point {@code u} of the way along it is, {@code in} from the middle. */
        static double[] point(int s, double u, double in) {
            return switch (s) {
                case 0 -> new double[]{u, -in};
                case 1 -> new double[]{u, in};
                case 2 -> new double[]{-in, u};
                default -> new double[]{in, u};
            };
        }

        /** The side a point past the edge carries on from: north or south while it is past those, else west or east. */
        static int side(double x, double z, double half) {
            if (Math.abs(z) > half || Math.abs(z) >= Math.abs(x)) return z < 0 ? 0 : 1;
            return x < 0 ? 2 : 3;
        }

        /** The sample's place (fractional index) along side {@code s} for a point at {@code u} along it. */
        double index(double u) {
            return Math.max(0, Math.min(n, (u + half) / spacingOf()));
        }

        /** The ground's height carried on to {@code (x, z)}. */
        public double height(double x, double z) {
            int s = side(x, z, half);
            return lerp(h[s], index(s < 2 ? x : z));
        }

        /** How much water there is about the edge carried on to {@code (x, z)} (a river's bed and banks), 0 - 1. */
        double wetness(double x, double z) {
            int s = side(x, z, half);
            return lerp(wet[s], index(s < 2 ? x : z));
        }

        /** Material {@code m}'s weight carried on to {@code (x, z)}. */
        double weight(int m, double x, double z) {
            int s = side(x, z, half);
            return lerp(w[s][m], index(s < 2 ? x : z));
        }

        /** The water carried on to {@code (x, z)}: its surface (NaN: none), kind code, flow x, flow z. */
        double[] water(double x, double z) {
            int s = side(x, z, half);
            int i = (int) Math.round(index(s < 2 ? x : z));
            return new double[]{surface[s][i], kind[s][i], fx[s][i], fz[s][i]};
        }

        static double lerp(float[] a, double f) {
            int i = Math.min(a.length - 2, (int) Math.floor(f));
            double t = f - i;
            return a[i] + (a[i + 1] - a[i]) * t;
        }

        /** The runs along side {@code s} where materials {@code road} show: {from, to} metres along it. */
        List<double[]> runs(int s, int... road) {
            List<double[]> out = new ArrayList<>();
            double sp = spacingOf();
            int start = -1;
            for (int i = 0; i <= n + 1; i++) {
                boolean on = false;
                if (i <= n) for (int m : road) on |= w[s][m][i] > 0.5;
                if (on && start < 0) start = i;
                if (!on && start >= 0) {
                    out.add(new double[]{-half + start * sp, -half + (i - 1) * sp});
                    start = -1;
                }
            }
            return out;
        }
    }

    /** The theme {@code s} has for a level whose edge is {@code e}: its own, or worked out from the edge (AUTO). */
    public static Theme resolve(Surroundings s, Edge e) {
        if (s.theme() != Theme.AUTO) return s.theme();
        double[] sh = e.share;
        double streets = sh[GroundMap.COBBLES] + sh[GroundMap.FLAGSTONES] + sh[GroundMap.BRICKS];
        if (streets > 0.15 || e.cityHouses > 1.2) return Theme.CITY;
        if (e.underSea > 0.2 || (!Double.isNaN(e.sea) && sh[GroundMap.SAND] > 0.2)) return Theme.COAST;
        if (sh[GroundMap.CRYSTAL] > 0.2) return Theme.CRYSTAL;
        if (sh[GroundMap.ASH] > 0.2) return Theme.WASTE;
        if (sh[GroundMap.SNOW] > 0.25) return Theme.SNOW;
        if (sh[GroundMap.SAND] > 0.3) return Theme.DESERT;
        if (sh[GroundMap.MUD] > 0.2) return Theme.SWAMP;
        if (sh[GroundMap.JUNGLE] > 0.2 || e.palms > 0.6 && e.trees > 1.5) return Theme.JUNGLE;
        if (sh[GroundMap.DRY_GRASS] > 0.3) return Theme.SAVANNAH;
        if (sh[GroundMap.STONE] > 0.25 || e.highest - e.baseY > 9) return Theme.MOUNTAINS;
        if (e.houses > 0.8 || streets + sh[GroundMap.PACKED] > 0.12) return Theme.TOWN;
        if (sh[GroundMap.FOREST] > 0.25 || e.trees > 2.5) return e.pines > e.trees * 0.5 ? Theme.PINES : Theme.FOREST;
        if (e.highest - e.lowest > 5) return Theme.HILLS;
        return Theme.MEADOW;
    }

    // =================================================================================================
    // what goes in, what comes out
    // =================================================================================================

    /**
     * What surroundings are made from.
     *
     * @param settings    the level's settings
     * @param size        the level's size, metres a side
     * @param edge        its edge, sampled ({@link Edge#of})
     * @param level       its ground (the near ring's grid takes its heights inside the level)
     * @param sprites     the scenery's sprites (null: no trees, rocks or grass)
     * @param catalog     the buildings (null: no houses)
     * @param tileColours each ground texture's average colour, grass first then {@link GroundMap}'s materials
     *                    (r, g, b 0 - 1; null: the plain colours the far ring has without them)
     */
    public record Input(Surroundings settings, double size, Edge edge, Terrain level, ScenerySprites sprites,
                        BuildingCatalog catalog, float[][] tileColours) {}

    /**
     * The far ring's mesh: {@link #STRIDE} floats a corner - x, y, z; red,
     * green, blue; what it is ({@code MAT_*}) - and its triangles.
     *
     * @param radius    how far out it reaches from the middle, metres
     * @param liftFrom  from what height its heights begin to stand out of the fog
     * @param liftRange over how many metres up they come all the way out of it
     */
    public record Far(float[] vertices, int[] indices, double radius, double liftFrom, double liftRange) {
        public static final int STRIDE = 7;

        public int triangles() { return indices.length / 3; }
    }

    /** What the far ring's faces are: ground, water, a canopy, a wall, a roof, glowing, crystal. */
    public static final int MAT_GROUND = 0, MAT_WATER = 1, MAT_CANOPY = 2, MAT_WALL = 3, MAT_ROOF = 4, MAT_GLOW = 5,
            MAT_CRYSTAL = 6;

    /**
     * The surroundings made.
     *
     * @param theme     the theme they were made as (AUTO worked out)
     * @param half      the level's half size
     * @param width     the near ring's width, metres
     * @param ground    the near ring's ground: a grid over the level and the ring (the level's own heights inside it)
     * @param map       what the near ring's ground is made of, a map over the same square
     * @param water     the near ring's water (and the sea, if any)
     * @param scenery   its trees, rocks and grass
     * @param buildings its houses, haystacks, lamps
     * @param far       the far ring
     * @param baseY     the height the country round lies at
     */
    public record Result(Theme theme, double half, double width, Terrain ground, GroundMap map, WaterMap water,
                         Scenery scenery, List<Building> buildings, Far far, double baseY) {
        /** How far out the near ring reaches from the middle, metres. */
        public double outer() { return half + width; }

        /** The level's square, which the near ring goes round: x from, x to, z from, z to. */
        public double[] hole() { return new double[]{-half, half, -half, half}; }
    }

    /** Make the surroundings {@code in} asks for. */
    public static Result generate(Input in) {
        return new Gen(in).run();
    }

    // =================================================================================================
    // noise
    // =================================================================================================

    /** Smooth value noise and its sums, the same for the same seed. */
    static final class Noise {
        private final int seed;

        Noise(int seed) {
            this.seed = seed * 0x9E3779B1 + 0x7F4A7C15;
        }

        double hash(int x, int z, int salt) {
            int h = x * 374761393 + z * 668265263 + salt * 1442695041 + seed;
            h = (h ^ (h >>> 13)) * 1274126177;
            h ^= h >>> 16;
            return (h & 0xFFFFFF) / (double) 0x1000000;
        }

        double value(double x, double z, int salt) {
            int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
            double fx = x - xi, fz = z - zi;
            fx = fx * fx * (3 - 2 * fx);
            fz = fz * fz * (3 - 2 * fz);
            double a = hash(xi, zi, salt), b = hash(xi + 1, zi, salt);
            double c = hash(xi, zi + 1, salt), d = hash(xi + 1, zi + 1, salt);
            return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
        }

        /** Four octaves, 0 - 1 (most often near the middle). */
        double fbm(double x, double z, int salt) {
            double s = 0, a = 0.5, t = 0;
            for (int i = 0; i < 4; i++) {
                s += a * value(x, z, salt + i * 31);
                t += a;
                x = x * 2.03 + 17.1;
                z = z * 2.03 + 9.3;
                a *= 0.5;
            }
            return s / t;
        }

        /** Ridges: sharp crests where the noise crosses its middle, 0 - 1. */
        double ridged(double x, double z, int salt) {
            double s = 0, a = 0.5, t = 0, w = 1;
            for (int i = 0; i < 5; i++) {
                double v = 1 - Math.abs(2 * value(x, z, salt + i * 37) - 1);
                v = v * v * w;
                w = Math.min(1, v * 1.6 + 0.25);
                s += a * v;
                t += a;
                x = x * 2.1 + 3.7;
                z = z * 2.1 + 11.9;
                a *= 0.5;
            }
            return s / t;
        }
    }

    static double smooth(double e0, double e1, double v) {
        double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    // =================================================================================================
    // the making
    // =================================================================================================

    /** The materials the roads and paths are made of. */
    static final int[] STREETS = {GroundMap.COBBLES, GroundMap.FLAGSTONES, GroundMap.BRICKS};
    static final int[] PATHS = {GroundMap.COBBLES, GroundMap.FLAGSTONES, GroundMap.BRICKS, GroundMap.PACKED,
            GroundMap.DIRT, GroundMap.GRAVEL};

    static boolean isPath(int m) {
        for (int p : PATHS) if (p == m) return true;
        return false;
    }

    /** A city's ring streets: one every {@value #LOOP} m out from the edge, {@value #LOOP_WIDTH} m wide. */
    static final double LOOP = 42, LOOP_WIDTH = 10, WALK = 2;

    private static final class Gen {
        final Input in;
        final Surroundings s;
        final Theme theme;
        final Edge edge;
        final Noise noise;
        final double H, W, E, rs, gc, k, B, baseY, sea;
        final boolean carry;
        final ScenerySprites sprites;
        final BuildingCatalog catalog;
        // the volcano, if any: x, z, height, radius
        double[] volcano;
        // a city's streets running out from each side: {centre, half width of the carriageway, half width in all}
        final List<List<double[]>> radials = new ArrayList<>();
        Terrain ring;
        int cells;
        byte[][] weights;
        WaterMap water;
        boolean[] taken;
        int takenCells;
        double takenCell;
        final List<Building> buildings = new ArrayList<>();
        final List<Scenery.Placed> placed = new ArrayList<>();

        Gen(Input in) {
            this.in = in;
            this.s = in.settings();
            this.edge = in.edge();
            this.theme = resolve(s, edge);
            this.noise = new Noise(s.seed() * 7919 + theme.ordinal() * 104729);
            this.H = in.size() / 2;
            this.rs = in.size() <= 256 ? 1 : in.size() <= 1024 ? 2 : 4;
            double w = Math.round(s.nearFor(in.size()) / 8) * 8.0;
            this.W = Math.max(Surroundings.MIN_NEAR, Math.round(w / rs) * rs);
            this.E = H + W;
            double cell = 0.25;
            while (2 * E / cell > 768 || cell < GroundMap.cellFor(in.size())) cell *= 2;
            this.gc = cell;
            this.k = s.height();
            this.B = theme == Theme.CITY || theme == Theme.TOWN ? 16 : Math.max(12, Math.min(36, W * 0.45));
            this.baseY = edge.baseY;
            this.sea = theme == Theme.COAST && Double.isNaN(edge.sea) ? baseY - 1.2 : edge.sea;
            this.carry = s.carry();
            this.sprites = in.sprites();
            this.catalog = in.catalog();
            if (theme == Theme.WASTE) {
                double a = noise.hash(3, 7, 11) * Math.PI * 2;
                double dist = E + Math.max(260, Math.min(900, s.far() * 0.45));
                double hv = Math.max(90, 260 * k);
                volcano = new double[]{Math.sin(a) * dist, -Math.cos(a) * dist, hv, hv * 1.8};
            }
        }

        // --- heights --------------------------------------------------------------------------

        double f(double x, double z, double scale) {
            return noise.fbm(x / scale, z / scale, (int) scale);
        }

        double c(double x, double z, double scale) {
            return f(x, z, scale) * 2 - 1;
        }

        double rid(double x, double z, double scale) {
            return noise.ridged(x / scale, z / scale, (int) scale + 5);
        }

        /** How far past the level's edge {@code (x, z)} is, metres (round at the corners); 0 inside. */
        double out(double x, double z) {
            double ex = Math.max(0, Math.abs(x) - H), ez = Math.max(0, Math.abs(z) - H);
            return Math.hypot(ex, ez);
        }

        /** The theme's own ground at {@code (x, z)}, {@code d} past the edge, over {@link #baseY}. */
        double theme(double x, double z, double d) {
            return switch (theme) {
                case NONE, AUTO, MEADOW -> k * (1.4 * c(x, z, 60) + 10 * smooth(60, 500, d) * Math.pow(f(x, z, 220), 1.5)
                        + 34 * smooth(300, 1400, d) * Math.pow(f(x, z, 520), 2));
                case FOREST -> k * (1.8 * c(x, z, 50) + 14 * smooth(50, 500, d) * Math.pow(f(x, z, 200), 1.3)
                        + 48 * smooth(300, 1400, d) * Math.pow(f(x, z, 450), 2));
                case PINES -> k * (2.6 * c(x, z, 45) + 24 * smooth(40, 500, d) * Math.pow(f(x, z, 180), 1.4)
                        + 170 * smooth(250, 1500, d) * Math.pow(rid(x, z, 600), 2));
                case JUNGLE -> k * (2.2 * c(x, z, 40) + 10 * smooth(50, 400, d) * f(x, z, 160)
                        + smooth(90, 300, d) * towers(x, z, 150, 22, 46, 45, 125, 1, 1.0));
                case HILLS -> k * (3.6 * c(x, z, 55) + 22 * smooth(30, 400, d) * Math.pow(f(x, z, 160), 1.2)
                        + 75 * smooth(200, 1200, d) * Math.pow(f(x, z, 400), 1.5));
                case MOUNTAINS -> k * (3.5 * c(x, z, 40) + 32 * smooth(8, 260, d) * rid(x, z, 160)
                        + 330 * smooth(150, 1300, d) * Math.pow(rid(x, z, 520), 1.6));
                case SNOW -> k * (2.2 * c(x, z, 50) + 18 * smooth(20, 300, d) * f(x, z, 150)
                        + 270 * smooth(200, 1400, d) * Math.pow(rid(x, z, 520), 1.6));
                case DESERT -> k * (dunes(x, z) * smooth(4, 50, d) + smooth(70, 260, d) * mesas(x, z)
                        + 22 * smooth(300, 1200, d) * f(x, z, 500));
                case SAVANNAH -> k * (1.1 * c(x, z, 70) + smooth(60, 300, d) * towers(x, z, 190, 16, 30, 7, 17, 2, 0.6)
                        + 60 * smooth(400, 1500, d) * Math.pow(f(x, z, 500), 2));
                case SWAMP -> k * (0.55 * c(x, z, 35) + 0.25 * c(x, z, 12) + 9 * smooth(500, 1500, d) * f(x, z, 400))
                        - 0.35 * smooth(6, 30, d) * smooth(0.45, 0.62, f(x, z, 28));
                case COAST -> (sea - baseY) + 2.6 - 9.5 * smooth(8, 120, d) + 0.7 * c(x, z, 30)
                        + k * smooth(350, 900, d) * towers(x, z, 520, 60, 150, 18, 70, 3, 0.35);
                case WASTE -> k * (3 * c(x, z, 40) + 26 * smooth(20, 300, d) * rid(x, z, 140)
                        + 230 * smooth(200, 1300, d) * Math.pow(rid(x, z, 420), 1.8)) + volcanoAt(x, z);
                case CRYSTAL -> k * (2.4 * c(x, z, 45) + 18 * smooth(40, 400, d) * f(x, z, 170)
                        + 95 * smooth(300, 1400, d) * Math.pow(rid(x, z, 500), 1.5));
                case TOWN -> k * (1.0 * c(x, z, 80) * smooth(30, 120, d) + 8 * smooth(100, 600, d) * f(x, z, 260)
                        + 36 * smooth(400, 1500, d) * Math.pow(f(x, z, 500), 1.5));
                case CITY -> k * (26 * smooth(700, 1800, d) * Math.pow(f(x, z, 500), 1.5));
            };
        }

        /** Dunes: long ridges across the wind, a gentle back and a steep slip face, wandering. */
        double dunes(double x, double z) {
            double u = (x * 0.94 + z * 0.34) / 38 + 2.4 * c(x, z, 90);
            double t = u - Math.floor(u);
            double profile = t < 0.78 ? t / 0.78 : (1 - t) / 0.22;
            profile = profile * profile * (3 - 2 * profile);
            return 4.2 * profile * (0.55 + 0.45 * f(x, z, 70));
        }

        /** Flat-topped mesas and buttes with sheer sides and a skirt of scree. */
        double mesas(double x, double z) {
            double cell = 230, best = 0;
            int ci = (int) Math.floor(x / cell), cj = (int) Math.floor(z / cell);
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    int i = ci + di, j = cj + dj;
                    if (noise.hash(i, j, 41) < 0.4) continue;
                    double cx = (i + 0.2 + 0.6 * noise.hash(i, j, 42)) * cell;
                    double cz = (j + 0.2 + 0.6 * noise.hash(i, j, 43)) * cell;
                    double r = 35 + 75 * noise.hash(i, j, 44), ht = 22 + 40 * noise.hash(i, j, 45);
                    double dist = Math.hypot(x - cx, z - cz) * (1 + 0.35 * c(x, z, 45));
                    double top = ht * (1 - smooth(r * 0.86, r, dist));
                    double skirt = ht * 0.32 * (1 - smooth(r, r * 1.45, dist));
                    best = Math.max(best, Math.max(top, skirt));
                }
            }
            return Math.floor(best / 3.5) * 3.5 * 0.35 + best * 0.65;      // in strata
        }

        /**
         * Steep rounded towers of rock (or, {@code round} low, kopjes): one in
         * a cell now and then, {@code r0} - {@code r1} m wide, {@code h0} -
         * {@code h1} m tall.
         */
        double towers(double x, double z, double cell, double r0, double r1, double h0, double h1, int salt,
                      double round) {
            double best = 0;
            int ci = (int) Math.floor(x / cell), cj = (int) Math.floor(z / cell);
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    int i = ci + di, j = cj + dj;
                    if (noise.hash(i, j, 50 + salt) < 0.45) continue;
                    double cx = (i + 0.2 + 0.6 * noise.hash(i, j, 51 + salt)) * cell;
                    double cz = (j + 0.2 + 0.6 * noise.hash(i, j, 52 + salt)) * cell;
                    double r = r0 + (r1 - r0) * noise.hash(i, j, 53 + salt);
                    double ht = h0 + (h1 - h0) * noise.hash(i, j, 54 + salt);
                    double dist = Math.hypot(x - cx, z - cz) * (1 + 0.2 * c(x, z, 30));
                    if (dist >= r) continue;
                    double t = dist / r;
                    double v = round >= 1 ? ht * (1 - smooth(0.55, 1, t)) + ht * 0.12 * Math.max(0, 1 - t * t / 0.3)
                            : ht * Math.pow(1 - t * t, 1 + round);
                    best = Math.max(best, v);
                }
            }
            return best;
        }

        double volcanoAt(double x, double z) {
            if (volcano == null) return 0;
            double dist = Math.hypot(x - volcano[0], z - volcano[1]), hv = volcano[2], rv = volcano[3];
            if (dist >= rv) return 0;
            double rc = rv * 0.11;
            double rim = hv * Math.pow(1 - rc / rv, 1.3);
            if (dist < rc) return rim - (rc - dist) / rc * hv * 0.14;
            return hv * Math.pow(1 - dist / rv, 1.3) * (1 + 0.06 * c(x, z, 25));
        }

        /** The ground's height at {@code (x, z)} past the level's edge: the edge carried on, turning into the theme's. */
        double heightAt(double x, double z) {
            double d = out(x, z);
            double e = edge.height(x, z);
            double t = baseY + theme(x, z, d);
            double h = e + (t - e) * smooth(0, B, d);
            if (carry) {
                double wet = edge.wetness(x, z);
                if (wet > 0) {
                    double bed = e;
                    double[] wtr = edge.water(x, z);
                    if (!Double.isNaN(wtr[0])) bed = Math.min(e, wtr[0] - 0.9);
                    h += (bed - h) * wet;
                }
            }
            return h;
        }

        // --- the ground's materials -----------------------------------------------------------

        /** The theme's own materials at {@code (x, z)} ({@code h} high, {@code slope} steep): weights into {@code w}. */
        void themeGround(double x, double z, double d, double h, double slope, float[] w) {
            Arrays.fill(w, 0);
            double above = h - baseY;
            switch (theme) {
                case MEADOW, NONE, AUTO -> {
                    fields(x, z, d, w, 0.38);
                    w[GroundMap.MEADOW] = (float) Math.max(w[GroundMap.MEADOW], smooth(0.58, 0.66, f(x, z, 24)));
                }
                case TOWN -> {
                    fields(x, z, d, w, 0.55);
                    w[GroundMap.MEADOW] = (float) Math.max(w[GroundMap.MEADOW], 0.8 * smooth(0.62, 0.7, f(x, z, 24)));
                }
                case FOREST -> {
                    w[GroundMap.FOREST] = (float) smooth(0.36, 0.46, f(x, z, 30));
                    w[GroundMap.MEADOW] = (float) smooth(0.7, 0.76, f(x, z, 21));
                }
                case PINES -> {
                    w[GroundMap.FOREST] = (float) smooth(0.32, 0.42, f(x, z, 34));
                    w[GroundMap.GRAVEL] = (float) smooth(0.5, 0.65, slope);
                    w[GroundMap.SNOW] = (float) smooth(55, 62, above + 6 * c(x, z, 20));
                }
                case JUNGLE -> {
                    w[GroundMap.JUNGLE] = (float) smooth(0.25, 0.35, f(x, z, 26));
                    w[GroundMap.MUD] = (float) smooth(0.7, 0.76, f(x, z, 19));
                }
                case HILLS -> {
                    w[GroundMap.STONE] = (float) Math.max(smooth(0.55, 0.75, slope), smooth(0.74, 0.8, f(x, z, 18)));
                    w[GroundMap.GRAVEL] = (float) smooth(0.68, 0.74, f(x, z, 15));
                    w[GroundMap.MEADOW] = (float) smooth(0.64, 0.7, f(x, z, 27));
                }
                case MOUNTAINS -> {
                    w[GroundMap.STONE] = (float) Math.max(smooth(0.42, 0.6, slope), smooth(0.6, 0.66, f(x, z, 22)));
                    w[GroundMap.GRAVEL] = (float) smooth(0.55, 0.62, f(x, z, 17));
                    w[GroundMap.SNOW] = (float) smooth(38, 46, above + 8 * c(x, z, 20));
                }
                case SNOW -> {
                    w[GroundMap.SNOW] = 1;
                    w[GroundMap.STONE] = (float) smooth(0.6, 0.8, slope);
                }
                case DESERT -> {
                    w[GroundMap.SAND] = 1;
                    w[GroundMap.GRAVEL] = (float) smooth(0.77, 0.81, f(x, z, 22));   // (stony here and there)
                    // the dunes' steep lee sides stay sand; the mesas' cliffs are warm rock (grey stone reads as holes)
                    w[GroundMap.DIRT] = (float) smooth(0.95, 1.25, slope);
                    w[GroundMap.DRY_GRASS] = (float) smooth(0.78, 0.82, f(x, z, 14));
                }
                case SAVANNAH -> {
                    w[GroundMap.DRY_GRASS] = 1;
                    w[GroundMap.DIRT] = (float) Math.max(smooth(0.72, 0.78, f(x, z, 25)), 0.8 * smooth(0.7, 0.95, slope));
                    w[GroundMap.STONE] = (float) smooth(1.0, 1.3, slope);
                }
                case SWAMP -> {
                    w[GroundMap.MUD] = (float) smooth(0.42, 0.5, f(x, z, 22));
                    w[GroundMap.MEADOW] = (float) (0.6 * smooth(0.7, 0.75, f(x, z, 17)));
                }
                case COAST -> {
                    double beach = h - sea;
                    w[GroundMap.SAND] = (float) (1 - smooth(1.2, 2.2, beach + 0.8 * c(x, z, 14)));
                    w[GroundMap.GRAVEL] = (float) (w[GroundMap.SAND] * smooth(0.74, 0.8, f(x, z, 20)));
                    w[GroundMap.MEADOW] = (float) ((1 - w[GroundMap.SAND]) * smooth(0.62, 0.7, f(x, z, 24)));
                }
                case WASTE -> {
                    w[GroundMap.ASH] = (float) smooth(0.28, 0.36, f(x, z, 28));
                    w[GroundMap.STONE] = (float) Math.max(smooth(0.5, 0.7, slope), smooth(0.66, 0.72, f(x, z, 19)));
                }
                case CRYSTAL -> {
                    w[GroundMap.CRYSTAL] = (float) smooth(0.28, 0.36, f(x, z, 30));
                    w[GroundMap.STONE] = (float) Math.max(smooth(0.55, 0.75, slope), smooth(0.7, 0.75, f(x, z, 18)));
                }
                case CITY -> w[GroundMap.PACKED] = (float) smooth(0.3, 0.4, f(x, z, 12));
            }
            if (theme == Theme.CITY) {
                int road = cityRoad(x, z);
                if (road >= 0) {
                    Arrays.fill(w, 0);
                    w[road] = 1;
                }
            }
        }

        /** Fields in a patchwork, {@code share} of them ploughed, wheat or flowers, hedged round. */
        void fields(double x, double z, double d, float[] w, double share) {
            if (d < 26) return;
            double fw = 46, fd = 31;
            int i = (int) Math.floor(x / fw), j = (int) Math.floor(z / fd);
            double lx = x - i * fw, lz = z - j * fd;
            if (lx < 2.5 || lz < 2.5) return;                   // the hedgerow's strip
            double hh = noise.hash(i, j, 61);
            if (hh > share) return;
            int m = hh < share * 0.45 ? GroundMap.PACKED : hh < share * 0.85 ? GroundMap.DRY_GRASS : GroundMap.MEADOW;
            w[m] = 1;
        }

        /** In a city: the street material at {@code (x, z)} - a ring street's or one running out - or -1. */
        int cityRoad(double x, double z) {
            double v = Math.max(Math.abs(x), Math.abs(z)) - H;
            if (v < 0) return -1;
            // the ring streets
            double loop = v - Math.floor(v / LOOP) * LOOP;
            if (loop < LOOP_WIDTH && Math.floor(v / LOOP) * LOOP + LOOP_WIDTH <= W + 0.01) {
                return loop < WALK || loop > LOOP_WIDTH - WALK ? GroundMap.FLAGSTONES : GroundMap.COBBLES;
            }
            // those running out
            int side = Edge.side(x, z, H);
            double u = side < 2 ? x : z, outV = side < 2 ? Math.abs(z) - H : Math.abs(x) - H;
            for (double[] r : radials.get(side)) {
                if (outV < r[3]) continue;
                double a = Math.abs(u - r[0]);
                if (a < r[1]) return GroundMap.COBBLES;
                if (a < r[2]) return GroundMap.FLAGSTONES;
            }
            return -1;
        }

        /** The streets of a city that run out from each side: those the level's streets carry on, and more between. */
        void planRadials() {
            for (int s = 0; s < 4; s++) {
                List<double[]> list = new ArrayList<>();
                double lo = s < 2 ? -E : -H, hi = s < 2 ? E : H;
                List<double[]> carried = carry ? edge.runs(s, STREETS) : List.of();
                List<double[]> busy = new ArrayList<>();
                for (double[] r : carried) {
                    if (r[1] - r[0] < 3) continue;
                    busy.add(new double[]{r[0] - 3, r[1] + 3});
                }
                busy.add(new double[]{hi + 1, hi + 2});
                busy.sort((a, b) -> Double.compare(a[0], b[0]));
                double at = lo;
                for (double[] b : busy) {
                    double gap = b[0] - at;
                    int m = (int) Math.floor(gap / 74);
                    for (int q = 1; q <= m; q++) {
                        double cu = at + gap * q / (m + 1);
                        // {centre, half carriageway, half in all, starts at (metres out)}
                        list.add(new double[]{cu, 3, 5, LOOP_WIDTH});
                    }
                    at = Math.max(at, b[1]);
                }
                radials.add(list);
            }
        }

        // --- the near ring ---------------------------------------------------------------------

        Result run() {
            if (theme == Theme.CITY) planRadials();
            else for (int i = 0; i < 4; i++) radials.add(List.of());
            buildGround();
            buildMap();
            buildWater();
            taken();
            if (catalog != null && s.density() > 0) {
                if (theme == Theme.CITY) cityHouses();
                if (theme == Theme.TOWN) townHouses();
                if (theme == Theme.TOWN || theme == Theme.MEADOW) farmThings();
            }
            if (sprites != null && s.density() > 0) sow();
            Far far = buildFar();
            GroundMap map = new GroundMap(2 * E, gc).setWeights(weights);
            return new Result(theme, H, W, ring, map, water, new Scenery(placed, map), List.copyOf(buildings), far,
                    baseY);
        }

        void buildGround() {
            int n = (int) Math.round(2 * E / rs) + 1;
            float[] hs = new float[n * n];
            Terrain level = in.level();
            for (int j = 0; j < n; j++) {
                double z = -E + j * rs;
                for (int i = 0; i < n; i++) {
                    double x = -E + i * rs;
                    double ax = Math.abs(x), az = Math.abs(z);
                    double y;
                    if (ax < H - 1e-6 && az < H - 1e-6) {
                        // inside the level: its own heights, sunk a little under its ground where the ring overlaps it
                        y = level.height(x, z) - 0.08;
                    } else if (ax <= H + 1e-6 && az <= H + 1e-6) {
                        // on the edge: the level's ground as it is just inside it (its very edge is kept at 0)
                        y = edge.height(x, z);
                    } else {
                        y = heightAt(x, z);
                    }
                    hs[j * n + i] = (float) y;
                }
            }
            ring = Terrain.of(-E, -E, n, n, rs, hs, List.of(), List.of());
        }

        void buildMap() {
            cells = (int) Math.round(2 * E / gc);
            weights = new byte[GroundMap.MATERIALS][];
            float[] tw = new float[GroundMap.MATERIALS];
            boolean city = theme == Theme.CITY, town = theme == Theme.TOWN;
            for (int r = 0; r < cells; r++) {
                double z = -E + (r + 0.5) * gc;
                for (int c = 0; c < cells; c++) {
                    double x = -E + (c + 0.5) * gc;
                    double d = out(x, z);
                    if (d <= 0) continue;
                    double h = ring.height(x, z);
                    double slope = ring.slope(x, z);
                    themeGround(x, z, d, h, slope, tw);
                    // the level's own ground giving way to the country's, raggedly, over more of a big ring
                    double span = city || town ? 14 : Math.max(14, B * 0.8);
                    double fade = smooth(2, span, d + span * (0.5 * c(x, z, 9 + span * 0.3) + 0.3 * c(x, z, 4)));
                    double keepRoad = !carry ? 1 - fade : city ? 1 : town ? 1 - smooth(60, 120, d + 14 * c(x, z, 13))
                            : 1 - smooth(16, 48, d + 12 * c(x, z, 13));
                    int i = r * cells + c;
                    for (int m = 0; m < GroundMap.MATERIALS; m++) {
                        double e = edge.weight(m, x, z);
                        double v = isPath(m) ? Math.max(e * keepRoad, tw[m]) : e * (1 - fade) + tw[m] * fade;
                        if (city && tw[GroundMap.COBBLES] + tw[GroundMap.FLAGSTONES] > 0.5 && !isPath(m)) v = 0;
                        if (v <= 0.004) continue;
                        if (weights[m] == null) weights[m] = new byte[cells * cells];
                        weights[m][i] = (byte) Math.round(Math.min(1, v) * 255);
                    }
                }
            }
        }

        /** Material {@code m}'s weight at {@code (x, z)} in the ring's map (its nearest cell), 0 - 1. */
        double weight(int m, double x, double z) {
            if (weights[m] == null) return 0;
            int c = (int) Math.floor((x + E) / gc), r = (int) Math.floor((z + E) / gc);
            if (c < 0 || r < 0 || c >= cells || r >= cells) return 0;
            return (weights[m][r * cells + c] & 0xFF) / 255.0;
        }

        /** Whether a road or path ({@code streets}: only the paved ones) runs at {@code (x, z)}. */
        boolean road(double x, double z, boolean paved) {
            for (int m : paved ? STREETS : PATHS) if (weight(m, x, z) > 0.42) return true;
            return false;
        }

        void buildWater() {
            double wc = Math.max(0.5, gc);
            water = new WaterMap(2 * E, wc);
            int n = water.cells();
            for (int r = 0; r < n; r++) {
                double z = water.west() + (r + 0.5) * wc;
                for (int c = 0; c < n; c++) {
                    double x = water.west() + (c + 0.5) * wc;
                    double d = out(x, z);
                    if (d <= 0) continue;
                    double h = ring.height(x, z);
                    if (carry && edge.wetness(x, z) > 0.35) {
                        double[] wt = edge.water(x, z);
                        if (!Double.isNaN(wt[0]) && wt[0] > h + 0.03) {
                            water.setCell(c, r, WaterMap.Kind.byCode((int) wt[1]), wt[0], wt[2], wt[3]);
                            continue;
                        }
                    }
                    if (theme == Theme.SWAMP && d > 5) {
                        double pool = baseY - 0.12;
                        if (h < pool - 0.03) water.setCell(c, r, WaterMap.Kind.SWAMP, pool, 0, 0);
                    }
                }
            }
            if (!Double.isNaN(sea)) water.setSeaLevel(sea);
        }

        /** The water's surface at {@code (x, z)} in the ring (NaN: dry). */
        double wetAt(double x, double z) {
            return water.surface(x, z, ring.height(x, z));
        }

        // --- what stands in it ------------------------------------------------------------------

        void taken() {
            takenCell = Math.max(1, gc);
            takenCells = (int) Math.ceil(2 * E / takenCell);
            taken = new boolean[takenCells * takenCells];
        }

        boolean isTaken(double x, double z) {
            int c = (int) Math.floor((x + E) / takenCell), r = (int) Math.floor((z + E) / takenCell);
            if (c < 0 || r < 0 || c >= takenCells || r >= takenCells) return true;
            return taken[r * takenCells + c];
        }

        void take(double[][] corners) {
            double x0 = 1e9, x1 = -1e9, z0 = 1e9, z1 = -1e9;
            for (double[] p : corners) {
                x0 = Math.min(x0, p[0]);
                x1 = Math.max(x1, p[0]);
                z0 = Math.min(z0, p[1]);
                z1 = Math.max(z1, p[1]);
            }
            for (double z = z0; z <= z1 + 1e-6; z += takenCell * 0.5) {
                for (double x = x0; x <= x1 + 1e-6; x += takenCell * 0.5) {
                    if (!inside(corners, x, z)) continue;
                    int c = (int) Math.floor((x + E) / takenCell), r = (int) Math.floor((z + E) / takenCell);
                    if (c >= 0 && r >= 0 && c < takenCells && r < takenCells) taken[r * takenCells + c] = true;
                }
            }
        }

        static boolean inside(double[][] q, double x, double z) {
            boolean in = false;
            for (int i = 0, j = q.length - 1; i < q.length; j = i++) {
                if ((q[i][1] > z) != (q[j][1] > z)
                        && x < (q[j][0] - q[i][0]) * (z - q[i][1]) / (q[j][1] - q[i][1]) + q[i][0]) {
                    in = !in;
                }
            }
            return in;
        }

        /** A building's extent, model metres: x from, x to, y from (its front), y to. */
        double[] extents(BuildingProfile p) {
            double x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9;
            for (BuildingProfile.Shape sh : p.footprint()) {
                double rx = sh.circle() ? sh.w() : sh.w() / 2, ry = sh.circle() ? sh.w() : sh.d() / 2;
                x0 = Math.min(x0, sh.x() - rx);
                x1 = Math.max(x1, sh.x() + rx);
                y0 = Math.min(y0, sh.y() - ry);
                y1 = Math.max(y1, sh.y() + ry);
            }
            for (BuildingProfile.Step st : p.steps()) {
                x0 = Math.min(x0, st.x() - st.w() / 2);
                x1 = Math.max(x1, st.x() + st.w() / 2);
                y0 = Math.min(y0, st.y() - st.d() / 2);
                y1 = Math.max(y1, st.y() + st.d() / 2);
            }
            if (x0 > x1) return new double[]{-1.5, 1.5, -1.5, 1.5};
            return new double[]{x0, x1, y0, y1};
        }

        /**
         * Try to stand {@code kind} with the middle of its front at {@code (fx,
         * fz)}, facing {@code (nx, nz)} (a unit vector); its footprint must be
         * clear of roads ({@code paved}: only the paved ones), water, what is
         * there already and the level. Returns its width (0: it did not fit).
         */
        double stand(String kind, double fx, double fz, double nx, double nz, double deepest, boolean paved) {
            BuildingProfile p = catalog.profile(kind);
            if (p == null) return 0;
            double[] e = extents(p);
            double wide = e[1] - e[0], deep = e[3] - e[2];
            if (deep > deepest) return 0;
            double ux = nz, uz = -nx;                         // its own x, to its right as it faces out
            double cm = (e[0] + e[1]) / 2;
            // its origin: the front's middle back by its front's y, and over by its middle
            double px = fx + nx * e[2] - ux * cm, pz = fz + nz * e[2] - uz * cm;
            double[][] q = new double[4][];
            double[][] mm = {{e[0], e[2]}, {e[1], e[2]}, {e[1], e[3]}, {e[0], e[3]}};
            double lift = 1e9, top = -1e9;
            for (int i = 0; i < 4; i++) {
                double wx = px + mm[i][0] * ux - mm[i][1] * nx, wz = pz + mm[i][0] * uz - mm[i][1] * nz;
                q[i] = new double[]{wx, wz};
                if (out(wx, wz) < 0.5 || Math.max(Math.abs(wx), Math.abs(wz)) > E - 0.5) return 0;
                double y = ring.height(wx, wz);
                lift = Math.min(lift, y);
                top = Math.max(top, y);
            }
            if (top - lift > 2.5) return 0;
            // clear underfoot: every metre of it
            int nu = Math.max(2, (int) Math.ceil(wide)), nv = Math.max(2, (int) Math.ceil(deep));
            for (int a = 0; a <= nu; a++) {
                for (int b = 0; b <= nv; b++) {
                    double mx = e[0] + 0.25 + (wide - 0.5) * a / nu, my = e[2] + 0.25 + (deep - 0.5) * b / nv;
                    double wx = px + mx * ux - my * nx, wz = pz + mx * uz - my * nz;
                    if (road(wx, wz, paved) || isTaken(wx, wz) || !Double.isNaN(wetAt(wx, wz))) return 0;
                }
            }
            take(q);
            double heading = Math.toDegrees(Math.atan2(-nx, -nz));
            buildings.add(new Building(-1, p, new Vec3(px, lift, pz), heading, new BuildingLook(), null));
            return wide;
        }

        /** Pick one of {@code kinds} ({kind, weight, kind, weight, ...}) by {@code r} 0 - 1. */
        static String pick(Object[] kinds, double r) {
            double total = 0;
            for (int i = 1; i < kinds.length; i += 2) total += ((Number) kinds[i]).doubleValue();
            double at = r * total;
            for (int i = 0; i < kinds.length; i += 2) {
                at -= ((Number) kinds[i + 1]).doubleValue();
                if (at <= 0) return (String) kinds[i];
            }
            return (String) kinds[kinds.length - 2];
        }

        static final Object[] CITY_ROW = {"terrace_house", 3.0, "stucco_townhouse", 2.0, "lodging_house", 2.0,
                "tenement", 1.6, "row_house", 1.4, "tavern", 0.8, "tea_room", 0.6, "apothecary", 0.5, "clockmaker", 0.5,
                "corner_shop", 0.4, "warehouse", 0.7, "workshop", 0.5, "shop_house", 0.5, "post_office", 0.12,
                "bank", 0.12, "chapel", 0.1};
        static final Object[] TOWN_ROW = {"cottage", 3.0, "house", 2.0, "row_house", 0.8, "shop_house", 0.8,
                "workshop", 0.4, "stable", 0.5, "cabin", 0.4};

        /** A city's houses along its ring streets, row on row, and its lamps. */
        void cityHouses() {
            long count = 0;
            for (int loop = 0; loop * LOOP + LOOP_WIDTH <= W + 0.01; loop++) {
                double a = loop * LOOP, b = a + LOOP_WIDTH;
                double deepest = (LOOP - LOOP_WIDTH) / 2 - 0.6;
                // the row facing in, across the street from the level (or the last row), and the row facing out
                frontage(b, +1, Math.min(deepest, W - b - 0.5), count++);
                if (loop > 0) frontage(a, -1, deepest, count++);
                lamps(a, b);
            }
        }

        /**
         * One frontage all round at {@code v} metres out: houses with their
         * fronts on it, their backs {@code way} (+1: outwards, facing in; -1:
         * inwards, facing out), at most {@code deepest} deep.
         */
        void frontage(double v, int way, double deepest, long salt) {
            if (deepest < 4) return;
            double R = H + v;
            for (int side = 0; side < 4; side++) {
                // the side's line, and which way is in
                double run = side < 2 ? R : H;           // west and east stop at the north and south rows
                double sx = side < 2 ? 1 : 0, sz = side < 2 ? 0 : 1;
                double ox = side == 2 ? -R : side == 3 ? R : 0, oz = side == 0 ? -R : side == 1 ? R : 0;
                double inX = side == 2 ? 1 : side == 3 ? -1 : 0, inZ = side == 0 ? 1 : side == 1 ? -1 : 0;
                double nx = inX * way, nz = inZ * way;            // the way the houses face
                double u = -run + 1;
                int tries = 0;
                while (u < run - 1) {
                    double r = noise.hash((int) Math.floor(u * 7), side * 31 + (int) salt, 71);
                    String kind = pick(CITY_ROW, r);
                    BuildingProfile p = catalog.profile(kind);
                    if (p == null) {
                        u += 2;
                        continue;
                    }
                    double[] e = extents(p);
                    double half = (e[1] - e[0]) / 2;
                    double cu = u + half;
                    double fx = ox + sx * cu, fz = oz + sz * cu;
                    double wide = stand(kind, fx, fz, nx, nz, deepest, true);
                    if (wide > 0) {
                        u += wide + 0.15 + 0.5 * noise.hash((int) Math.floor(u * 3), side, 72);
                        tries = 0;
                    } else if (++tries > 3) {
                        u += 1.5;
                        tries = 0;
                    }
                }
            }
        }

        /** Gas lamps along a ring street's two pavements, staggered. */
        void lamps(double a, double b) {
            if (catalog.profile("gas_lamp") == null) return;
            for (int side = 0; side < 4; side++) {
                for (int edgeOf = 0; edgeOf < 2; edgeOf++) {
                    double v = edgeOf == 0 ? a + 1.0 : b - 1.0;
                    if (edgeOf == 0 && a == 0) continue;         // (by the level's own edge: its lamps)
                    double R = H + v, run = side < 2 ? R : H - 6;
                    for (double u = -run + 12 + edgeOf * 13; u < run - 6; u += 26) {
                        double x = side < 2 ? u : side == 2 ? -R : R, z = side < 2 ? (side == 0 ? -R : R) : u;
                        if (road(x, z, true) && cityRoad(x, z) != GroundMap.FLAGSTONES) continue;
                        if (isTaken(x, z)) continue;
                        BuildingProfile p = catalog.profile("gas_lamp");
                        buildings.add(new Building(-1, p, new Vec3(x, ring.height(x, z), z), 0, new BuildingLook(),
                                null));
                    }
                }
            }
        }

        /** A town's cottages and houses along the lanes that run on out of it. */
        void townHouses() {
            for (int side = 0; side < 4; side++) {
                List<double[]> lanes = edge.runs(side, PATHS);
                double inX = side == 2 ? 1 : side == 3 ? -1 : 0, inZ = side == 0 ? 1 : side == 1 ? -1 : 0;
                for (double[] lane : lanes) {
                    double wide = lane[1] - lane[0];
                    if (wide < 1.5 || wide > 16) continue;
                    double mid = (lane[0] + lane[1]) / 2, hw = wide / 2 + 1.6;
                    for (int bank = -1; bank <= 1; bank += 2) {
                        double v = 7 + 6 * noise.hash((int) mid, side * 2 + bank, 81);
                        int n = 0;
                        while (v < Math.min(W - 8, 64) && n < 6) {
                            String kind = pick(TOWN_ROW, noise.hash((int) (v * 3), (int) mid + bank, 82));
                            BuildingProfile p = catalog.profile(kind);
                            if (p == null) {
                                v += 4;
                                continue;
                            }
                            double[] e = extents(p);
                            double halfW = (e[1] - e[0]) / 2;
                            // its front to the lane, along it: facing across (-bank along the edge)
                            double cu = mid + bank * hw, cv = v + halfW;
                            double fx = side < 2 ? cu : (side == 2 ? -(H + cv) : H + cv);
                            double fz = side < 2 ? (side == 0 ? -(H + cv) : H + cv) : cu;
                            double nx = side < 2 ? -bank : 0, nz = side < 2 ? 0 : -bank;
                            double got = stand(kind, fx, fz, nx, nz, 14, false);
                            if (got > 0) {
                                n++;
                                v += got + 3 + 7 * noise.hash((int) (v * 5), side, 83);
                            } else {
                                v += 2;
                            }
                        }
                    }
                }
            }
        }

        /** The farms out past a town or over the meadows: haystacks, bales, wheat, a scarecrow, a windmill now and then. */
        void farmThings() {
            double step = 23;
            Object[] things = theme == Theme.TOWN
                    ? new Object[]{"haystack", 2.0, "round_bale", 2.0, "wheat_field", 2.5, "hay_bale", 1.0, "scarecrow",
                    0.4, "cabbage_patch", 0.8, "pumpkin_patch", 0.5}
                    : new Object[]{"haystack", 2.0, "round_bale", 2.5, "hay_bale", 1.0, "wheat_field", 1.2,
                    "scarecrow", 0.3};
            double chance = (theme == Theme.TOWN ? 0.16 : 0.07) * s.density();
            int windmills = 0;
            for (double z = -E + step / 2; z < E; z += step) {
                for (double x = -E + step / 2; x < E; x += step) {
                    double d = out(x, z);
                    if (d < 22 || d > W - 4) continue;
                    int i = (int) Math.floor(x / step), j = (int) Math.floor(z / step);
                    double hh = noise.hash(i, j, 91);
                    double px = x + (noise.hash(i, j, 92) - 0.5) * step * 0.6;
                    double pz = z + (noise.hash(i, j, 93) - 0.5) * step * 0.6;
                    if (windmills < 4 && hh > 0.997 - 0.002 * s.density() && d > 40) {
                        if (stand("windmill", px, pz, 0, 1, 12, false) > 0) windmills++;
                        continue;
                    }
                    if (hh > chance) continue;
                    String kind = pick(things, noise.hash(i, j, 94));
                    double a = noise.hash(i, j, 95) * Math.PI * 2;
                    stand(kind, px, pz, Math.sin(a), Math.cos(a), 12, false);
                }
            }
        }

        // --- trees, rocks, grass ---------------------------------------------------------------

        /**
         * One layer of what grows: these sprites ({name, weight, ...}), one
         * chance in a cell {@code cell} m a side, out to {@code reach} m past
         * the edge, on ground no steeper than {@code steepest}.
         */
        record Layer(Object[] sprites, double cell, double chance, double reach, double steepest, double scale0,
                     double scale1, int salt) {}

        List<Layer> layers() {
            List<Layer> out = new ArrayList<>();
            Object[] tufts = {"grass_tuft_a", 3.0, "grass_tuft_b", 3.0, "grass_tuft_c", 3.0, "wildflowers", 2.0,
                    "tall_grass_a", 1.2, "tall_grass_b", 1.2};
            switch (theme) {
                case MEADOW, NONE, AUTO -> {
                    out.add(new Layer(new Object[]{"oak_tree", 3.0, "birch_tree", 2.0, "great_oak", 0.4, "autumn_tree",
                            1.0, "tall_birch", 0.6}, 5, 0.05, W, 0.6, 0.85, 1.2, 1));
                    out.add(new Layer(new Object[]{"berry_bush", 2.0, "sage_bush", 1.0}, 4, 0.05, W, 0.7, 0.8, 1.2, 2));
                    out.add(new Layer(new Object[]{"boulder", 1.0, "stones", 2.0, "flat_rock", 1.0, "mossy_rock",
                            1.0}, 9, 0.04, W, 0.8, 0.7, 1.2, 3));
                    out.add(new Layer(tufts, 1.6, 0.35, 30, 0.9, 0.8, 1.2, 4));
                }
                case TOWN -> {
                    out.add(new Layer(new Object[]{"oak_tree", 3.0, "birch_tree", 1.5, "autumn_tree", 1.0,
                            "great_oak", 0.3}, 6, 0.04, W, 0.6, 0.85, 1.2, 1));
                    out.add(new Layer(new Object[]{"berry_bush", 2.0, "sage_bush", 1.0}, 4, 0.04, W, 0.7, 0.8, 1.2, 2));
                    out.add(new Layer(tufts, 1.8, 0.28, 26, 0.9, 0.8, 1.2, 4));
                }
                case FOREST -> {
                    out.add(new Layer(new Object[]{"oak_tree", 4.0, "birch_tree", 2.0, "great_oak", 0.9, "tall_birch",
                            1.4, "autumn_tree", 1.0, "pine_tree", 0.8}, 3.6, 0.55, W, 0.7, 0.85, 1.3, 1));
                    out.add(new Layer(new Object[]{"fern", 3.0, "berry_bush", 0.6, "bilberry_bush", 0.4}, 2.6, 0.25,
                            W, 0.8, 0.8, 1.2, 2));
                    out.add(new Layer(new Object[]{"mossy_rock", 2.0, "mossy_log", 2.0, "boulder", 1.0}, 8, 0.12, W,
                            0.8, 0.8, 1.2, 3));
                    out.add(new Layer(tufts, 1.8, 0.25, 26, 0.9, 0.8, 1.2, 4));
                }
                case PINES -> {
                    out.add(new Layer(new Object[]{"pine_tree", 4.0, "tall_pine", 2.5, "birch_tree", 0.4,
                            "snowy_pine", 0.0}, 3.6, 0.5, W, 0.75, 0.85, 1.3, 1));
                    out.add(new Layer(new Object[]{"bilberry_bush", 1.0, "fern", 0.6}, 3, 0.18, W, 0.8, 0.8, 1.2, 2));
                    out.add(new Layer(new Object[]{"boulder", 2.0, "stones", 2.0, "mossy_rock", 1.0}, 8, 0.12, W, 0.9,
                            0.8, 1.2, 3));
                    out.add(new Layer(new Object[]{"grass_tuft_a", 2.0, "grass_tuft_b", 2.0, "grass_tuft_c", 2.0},
                            2.0, 0.2, 24, 0.9, 0.8, 1.2, 4));
                }
                case JUNGLE -> {
                    out.add(new Layer(new Object[]{"jungle_tree", 4.0, "palm_tree", 2.0}, 3.4, 0.55, W, 0.8, 0.85,
                            1.35, 1));
                    out.add(new Layer(new Object[]{"fern", 4.0, "sunfruit_bush", 0.7, "tall_grass_a", 1.0,
                            "wildflowers", 1.0}, 2.2, 0.45, W, 0.9, 0.8, 1.3, 2));
                    out.add(new Layer(new Object[]{"mossy_rock", 1.0, "mossy_log", 1.0}, 9, 0.08, W, 0.9, 0.8, 1.2,
                            3));
                }
                case HILLS -> {
                    out.add(new Layer(new Object[]{"oak_tree", 1.0, "birch_tree", 1.0, "pine_tree", 0.7}, 7, 0.05, W,
                            0.5, 0.8, 1.2, 1));
                    out.add(new Layer(new Object[]{"boulder", 2.0, "standing_stone", 0.25, "stones", 2.0,
                            "flat_rock", 1.0, "mossy_rock", 1.0}, 6, 0.1, W, 0.9, 0.7, 1.3, 3));
                    out.add(new Layer(new Object[]{"bilberry_bush", 1.0, "tall_grass_a", 1.0, "tall_grass_b", 1.0},
                            3, 0.12, W, 0.9, 0.8, 1.2, 2));
                    out.add(new Layer(tufts, 1.6, 0.35, 30, 0.95, 0.8, 1.2, 4));
                }
                case MOUNTAINS -> {
                    out.add(new Layer(new Object[]{"pine_tree", 2.0, "tall_pine", 1.0, "snowy_pine", 0.0}, 5, 0.16,
                            W, 0.55, 0.8, 1.2, 1));
                    out.add(new Layer(new Object[]{"boulder", 3.0, "stones", 2.0, "flat_rock", 1.0, "snow_boulder",
                            0.0}, 4.5, 0.16, W, 1.1, 0.7, 1.4, 3));
                    out.add(new Layer(new Object[]{"grass_tuft_a", 1.0, "bilberry_bush", 0.6}, 2.4, 0.15, 24, 0.8,
                            0.8, 1.2, 4));
                }
                case SNOW -> {
                    out.add(new Layer(new Object[]{"snowy_pine", 4.0}, 4.6, 0.3, W, 0.7, 0.85, 1.3, 1));
                    out.add(new Layer(new Object[]{"snow_boulder", 3.0}, 7, 0.1, W, 1.0, 0.7, 1.3, 3));
                }
                case DESERT -> {
                    out.add(new Layer(new Object[]{"dead_tree", 1.0, "acacia_tree", 0.3}, 14, 0.06, W, 0.5, 0.8, 1.2,
                            1));
                    out.add(new Layer(new Object[]{"dry_grass_tuft", 2.0, "thorn_bush", 0.6, "tall_dry_grass", 0.4},
                            3, 0.08, W, 0.6, 0.8, 1.2, 2));
                    out.add(new Layer(new Object[]{"sandstone_rock", 3.0, "stones", 1.0, "flat_rock", 0.5}, 7, 0.1, W,
                            1.2, 0.7, 1.5, 3));
                }
                case SAVANNAH -> {
                    out.add(new Layer(new Object[]{"acacia_tree", 3.0, "dead_tree", 0.2}, 11, 0.12, W, 0.5, 0.85, 1.3,
                            1));
                    out.add(new Layer(new Object[]{"dry_grass_tuft", 3.0, "tall_dry_grass", 3.0, "sage_bush", 0.6,
                            "thornberry_bush", 0.4}, 1.8, 0.3, 30, 0.9, 0.8, 1.25, 2));
                    out.add(new Layer(new Object[]{"termite_mound", 1.2, "sandstone_rock", 1.0, "boulder", 0.5}, 10,
                            0.07, W, 0.9, 0.8, 1.3, 3));
                }
                case SWAMP -> {
                    out.add(new Layer(new Object[]{"willow_tree", 3.0, "dead_tree", 1.0}, 6, 0.22, W, 0.6, 0.85, 1.25,
                            1));
                    out.add(new Layer(new Object[]{"reeds", 3.0, "wildflowers", 0.6, "bilberry_bush", 0.3}, 2.0, 0.3,
                            W, 0.9, 0.8, 1.2, 2));
                    out.add(new Layer(new Object[]{"mossy_log", 2.0, "river_stones", 1.0, "mossy_rock", 1.0}, 9, 0.09,
                            W, 0.9, 0.8, 1.2, 3));
                    out.add(new Layer(tufts, 1.8, 0.3, 26, 0.9, 0.8, 1.2, 4));
                }
                case COAST -> {
                    out.add(new Layer(new Object[]{"palm_tree", 2.0, "oak_tree", 0.6}, 9, 0.1, W, 0.5, 0.85, 1.25, 1));
                    out.add(new Layer(new Object[]{"beach_grass", 3.0, "sea_buckthorn", 0.6}, 2.4, 0.22, W, 0.9, 0.8,
                            1.2, 2));
                    out.add(new Layer(new Object[]{"beach_rock", 3.0, "stones", 1.0}, 7, 0.08, W, 1.0, 0.7, 1.3, 3));
                }
                case WASTE -> {
                    out.add(new Layer(new Object[]{"dead_tree", 2.0}, 9, 0.12, W, 0.6, 0.8, 1.3, 1));
                    out.add(new Layer(new Object[]{"thorn_bush", 2.0, "thornberry_bush", 0.3}, 3, 0.1, W, 0.8, 0.8,
                            1.2, 2));
                    out.add(new Layer(new Object[]{"ash_spire", 3.0, "boulder", 1.0, "stones", 1.0}, 6, 0.14, W, 1.1,
                            0.7, 1.5, 3));
                }
                case CRYSTAL -> {
                    out.add(new Layer(new Object[]{"crystal_tree", 3.0}, 5, 0.2, W, 0.7, 0.85, 1.3, 1));
                    out.add(new Layer(new Object[]{"crystal_grass", 4.0, "crystal_bloom", 1.0}, 2.0, 0.3, W, 0.9, 0.8,
                            1.2, 2));
                    out.add(new Layer(new Object[]{"crystal_cluster", 2.0, "crystal_spire", 2.0, "iridescent_rock",
                            1.0}, 6, 0.14, W, 1.0, 0.7, 1.5, 3));
                }
                case CITY -> out.add(new Layer(new Object[]{"oak_tree", 1.0, "birch_tree", 1.0}, 12, 0.05, W, 0.4,
                        0.8, 1.1, 1));
            }
            return out;
        }

        /** The most trees and rocks, and the most tufts of grass, the ring is given. */
        static final int MOST_SOLID = 12000, MOST_TUFTS = 9000;

        void sow() {
            List<Scenery.Placed> solid = new ArrayList<>(), soft = new ArrayList<>();
            for (Layer layer : layers()) {
                boolean tufts = layer.salt() == 4 || layer.salt() == 2;
                double reachOut = Math.min(W, layer.reach());
                double cellsN = (8 * H * reachOut + 4 * reachOut * reachOut) / (layer.cell() * layer.cell());
                double chance = layer.chance() * s.density();
                double most = tufts ? MOST_TUFTS * 0.6 : MOST_SOLID * 0.5;
                if (cellsN * chance > most) chance = most / cellsN;
                double reach = Math.min(W, layer.reach());
                int n = (int) Math.ceil(2 * E / layer.cell());
                // (the cells wholly inside the level are skipped: a row through it has only its two ends)
                int skip0 = (int) Math.ceil((E - H + 1) / layer.cell()), skip1 = (int) Math.floor((E + H - 1)
                        / layer.cell()) - 1;
                for (int j = 0; j < n; j++) {
                    double z0 = -E + j * layer.cell();
                    boolean through = j >= skip0 && j <= skip1;
                    for (int i = 0; i < n; i++) {
                        double x0 = -E + i * layer.cell();
                        if (through && i >= skip0 && i <= skip1) {
                            i = skip1;
                            continue;
                        }
                        double hh = noise.hash(i, j, 100 + layer.salt());
                        if (hh > chance * 3) continue;
                        double x = x0 + noise.hash(i, j, 110 + layer.salt()) * layer.cell();
                        double z = z0 + noise.hash(i, j, 120 + layer.salt()) * layer.cell();
                        double d = out(x, z);
                        if (d < 0.8 || d > reach) continue;
                        double cluster = clump(x, z, layer.salt());
                        double p = chance * cluster * smooth(0.8, 6, d);
                        if (hh > p) continue;
                        if (ring.slope(x, z) > layer.steepest()) continue;
                        double y = ring.height(x, z);
                        double wet = water.surface(x, z, y);
                        boolean reedy = layer.salt() == 2 && theme == Theme.SWAMP;
                        if (!Double.isNaN(wet) && !(reedy && wet - y < 0.35)) continue;
                        if (road(x, z, false) || isTaken(x, z)) continue;
                        String name = pickSprite(layer, x, z, y, noise.hash(i, j, 130 + layer.salt()));
                        ScenerySprites.Sprite sp = name == null ? null : sprites.sprite(name);
                        if (sp == null) continue;
                        double sc = layer.scale0() + (layer.scale1() - layer.scale0())
                                * noise.hash(i, j, 140 + layer.salt());
                        double yaw = noise.hash(i, j, 150 + layer.salt()) * Math.PI * 2;
                        boolean mirror = noise.hash(i, j, 160 + layer.salt()) > 0.5;
                        Scenery.Placed pl = new Scenery.Placed(sp, x, y, z, yaw, mirror, Scenery.windPhase(x, z,
                                noise.hash(i, j, 170)), sc, 0);
                        (tufts ? soft : solid).add(pl);
                    }
                }
            }
            placed.addAll(thin(solid, MOST_SOLID));
            placed.addAll(thin(soft, MOST_TUFTS));
        }

        /** How thick a layer grows at {@code (x, z)}: in clumps and glades, 0 - 2. */
        double clump(double x, double z, int salt) {
            double v = f(x + salt * 101, z - salt * 57, 38);
            return switch (theme) {
                case FOREST, PINES, JUNGLE -> salt == 1 ? 0.3 + 1.6 * smooth(0.3, 0.55, v) : 1;
                case MEADOW, TOWN, HILLS, SAVANNAH -> salt == 1 ? 2.2 * smooth(0.5, 0.72, v) : 0.4 + 1.2 * v;
                default -> 0.4 + 1.2 * v;
            };
        }

        /** Which sprite of {@code layer} grows at {@code (x, z)}, {@code y} high: the snowy ones up in the snow. */
        String pickSprite(Layer layer, double x, double z, double y, double r) {
            boolean snowy = weight(GroundMap.SNOW, x, z) > 0.5 || theme == Theme.SNOW;
            if (snowy && layer.salt() == 1 && (theme == Theme.PINES || theme == Theme.MOUNTAINS)) return "snowy_pine";
            if (snowy && layer.salt() == 3 && theme == Theme.MOUNTAINS) return "snow_boulder";
            if (layer.salt() == 2 && theme == Theme.SWAMP) {
                boolean shore = !Double.isNaN(water.surface(x + 1.5, z, y)) || !Double.isNaN(water.surface(x - 1.5, z,
                        y)) || !Double.isNaN(water.surface(x, z + 1.5, y)) || !Double.isNaN(water.surface(x, z - 1.5, y));
                if (shore) return "reeds";
            }
            return pick(layer.sprites(), r);
        }

        /** At most {@code most} of {@code list}, evenly thinned. */
        static List<Scenery.Placed> thin(List<Scenery.Placed> list, int most) {
            if (list.size() <= most) return list;
            List<Scenery.Placed> out = new ArrayList<>(most);
            double step = list.size() / (double) most;
            for (double i = 0; i < list.size() && out.size() < most; i += step) out.add(list.get((int) i));
            return out;
        }

        // --- the far ring ----------------------------------------------------------------------

        /** The grid's lines one way: across the level coarsely, then finer at its edge, growing outwards. */
        double[] axis(double R) {
            List<Double> out = new ArrayList<>();
            double inner = Math.max(8, Math.min(32, H / 24));
            int m = Math.max(2, (int) Math.round(2 * H / inner));
            for (int i = 0; i <= m; i++) out.add(-H + 2 * H * i / m);
            double near = Math.max(6, W / 8);
            List<Double> outer = new ArrayList<>();
            for (double v = near; v < W - 1e-6; v += near) outer.add(H + v);
            outer.add(H + W);
            double step = near, v = W;
            while (H + v < R - 1e-6) {
                step = Math.min(step * 1.075 + 0.5, 140);
                v = Math.min(R - H, v + step);
                outer.add(H + v);
            }
            for (double o : outer) {
                out.add(o);
                out.add(-o);
            }
            double[] a = out.stream().mapToDouble(Double::doubleValue).distinct().sorted().toArray();
            return a;
        }

        /** Its colour by the ground textures' average colours, where its ground would be theirs. */
        float[] tile(int material) {
            float[][] t = in.tileColours();
            if (t != null && t.length > material + 1 && t[material + 1] != null) return t[material + 1];
            return switch (material) {
                case -1 -> new float[]{0.42f, 0.6f, 0.27f};
                case GroundMap.DIRT, GroundMap.PACKED -> new float[]{0.55f, 0.43f, 0.3f};
                case GroundMap.FOREST -> new float[]{0.33f, 0.36f, 0.2f};
                case GroundMap.GRAVEL -> new float[]{0.55f, 0.53f, 0.5f};
                case GroundMap.MEADOW -> new float[]{0.47f, 0.62f, 0.3f};
                case GroundMap.SAND -> new float[]{0.84f, 0.74f, 0.52f};
                case GroundMap.MUD -> new float[]{0.36f, 0.3f, 0.21f};
                case GroundMap.SNOW -> new float[]{0.9f, 0.93f, 0.97f};
                case GroundMap.STONE -> new float[]{0.5f, 0.49f, 0.47f};
                case GroundMap.JUNGLE -> new float[]{0.3f, 0.45f, 0.2f};
                case GroundMap.DRY_GRASS -> new float[]{0.74f, 0.66f, 0.38f};
                case GroundMap.ASH -> new float[]{0.3f, 0.28f, 0.29f};
                case GroundMap.CRYSTAL -> new float[]{0.62f, 0.55f, 0.8f};
                default -> new float[]{0.5f, 0.47f, 0.43f};
            };
        }

        /** The far ground's colour at {@code (x, z)}: the theme's materials' colours, as the near ring has them. */
        private static final float[] SANDSTONE = {0.78f, 0.6f, 0.42f};

        float[] groundColour(double x, double z, double d, double h, double slope, float[] tw) {
            themeGround(x, z, d, h, slope, tw);
            float[] col = tile(-1).clone();
            // (sandstone in the dry lands: grey cliffs read as holes in the dunes)
            boolean dry = theme == Theme.DESERT || theme == Theme.SAVANNAH;
            for (int m : GroundMap.ORDER) {
                double v = tw[m];
                if (v <= 0.02) continue;
                float[] t = dry && m == GroundMap.STONE ? SANDSTONE : tile(m);
                double a = smooth(0.35, 0.65, v);
                for (int i = 0; i < 3; i++) col[i] += (float) ((t[i] - col[i]) * a);
            }
            // rock where it is steep, as the hills' faces are
            double rock = smooth(0.85, 1.25, slope);
            if (rock > 0) {
                float[] st = dry ? SANDSTONE : tile(GroundMap.STONE);
                for (int i = 0; i < 3; i++) col[i] += (float) ((st[i] * 0.85f - col[i]) * rock);
            }
            // a little light and shade over it, so its folds read from afar
            double var = 0.9 + 0.2 * f(x, z, 70);
            for (int i = 0; i < 3; i++) col[i] *= (float) var;
            return col;
        }

        Far buildFar() {
            double R = E + s.far();
            double[] xs = axis(R);
            int nx = xs.length;
            Mesh mesh = new Mesh();
            float[] tw = new float[GroundMap.MATERIALS];
            double[] hs = new double[nx * nx];
            int[] index = new int[nx * nx];
            Arrays.fill(index, -1);
            for (int j = 0; j < nx; j++) {
                for (int i = 0; i < nx; i++) {
                    double x = xs[i], z = xs[j];
                    hs[j * nx + i] = out(x, z) > 0 ? heightAt(x, z) : edge.height(x, z) - 1.5;
                }
            }
            double liftFrom = baseY + 5;
            for (int j = 0; j < nx; j++) {
                for (int i = 0; i < nx; i++) {
                    double x = xs[i], z = xs[j];
                    // only the corners of cells that are out past the level
                    boolean used = false;
                    for (int dj = -1; dj <= 0 && !used; dj++) {
                        for (int di = -1; di <= 0 && !used; di++) {
                            int ci = i + di, cj = j + dj;
                            if (ci < 0 || cj < 0 || ci >= nx - 1 || cj >= nx - 1) continue;
                            double mx = (xs[ci] + xs[ci + 1]) / 2, mz = (xs[cj] + xs[cj + 1]) / 2;
                            if (Math.abs(mx) > H || Math.abs(mz) > H) used = true;
                        }
                    }
                    if (!used) continue;
                    double d = out(x, z);
                    double h = hs[j * nx + i];
                    double gx = (hs[j * nx + Math.min(nx - 1, i + 1)] - hs[j * nx + Math.max(0, i - 1)])
                            / Math.max(1e-3, xs[Math.min(nx - 1, i + 1)] - xs[Math.max(0, i - 1)]);
                    double gz = (hs[Math.min(nx - 1, j + 1) * nx + i] - hs[Math.max(0, j - 1) * nx + i])
                            / Math.max(1e-3, xs[Math.min(nx - 1, j + 1)] - xs[Math.max(0, j - 1)]);
                    double slope = Math.hypot(gx, gz);
                    // under the near ring: tucked below it, rising to meet it at its outer edge
                    double tuck = d < W ? 1.6 * (1 - smooth(W * 0.55, W, d)) : 0;
                    float[] col;
                    int mat = MAT_GROUND;
                    double y = h - tuck;
                    double wet = farWater(x, z, h, d);
                    if (!Double.isNaN(wet)) {
                        y = wet - tuck;
                        col = waterColour(x, z, d);
                        mat = MAT_WATER;
                    } else {
                        col = groundColour(x, z, d, h, slope, tw);
                        double canopy = d > W * 0.85 ? canopy(x, z, d, h, slope) : 0;
                        if (canopy > 0.05) {
                            y += canopy;
                            col = canopyColour(x, z, h);
                            mat = MAT_CANOPY;
                        }
                        if (theme == Theme.WASTE && volcano != null) {
                            double vd = Math.hypot(x - volcano[0], z - volcano[1]);
                            if (vd < volcano[3] * 0.13) {
                                col = new float[]{1f, 0.42f, 0.12f};
                                mat = MAT_GLOW;
                            } else if (vd < volcano[3] * 0.8 && slope > 0.3) {
                                // old flows of black rock down its flanks (no glow: lit patches read as blocks this far off)
                                double flow = smooth(0.55, 0.75, rid(x, z, 90)) * (1 - smooth(0.4, 0.8, vd / volcano[3]));
                                for (int ci = 0; ci < 3; ci++) col[ci] = (float) (col[ci] * (1 - 0.55 * flow));
                            }
                        }
                    }
                    index[j * nx + i] = mesh.vertex(x, y, z, col, mat);
                }
            }
            for (int j = 0; j + 1 < nx; j++) {
                for (int i = 0; i + 1 < nx; i++) {
                    double mx = (xs[i] + xs[i + 1]) / 2, mz = (xs[j] + xs[j + 1]) / 2;
                    if (Math.abs(mx) < H && Math.abs(mz) < H) continue;
                    int a = index[j * nx + i], b = index[j * nx + i + 1], c = index[(j + 1) * nx + i],
                            d = index[(j + 1) * nx + i + 1];
                    if (a < 0 || b < 0 || c < 0 || d < 0) continue;
                    mesh.tri(a, c, b);
                    mesh.tri(d, b, c);
                }
            }
            switch (theme) {
                case CITY -> skyline(mesh, R);
                case TOWN, MEADOW -> villages(mesh, R);
                case CRYSTAL -> prisms(mesh, R);
                default -> { }
            }
            return new Far(mesh.vertices(), mesh.indices(), R, liftFrom, Math.max(25, 45 * k));
        }

        /** The water's surface at a far point (the river carried on, the swamp's pools, the sea), or NaN. */
        double farWater(double x, double z, double h, double d) {
            double best = Double.NaN;
            if (carry && edge.wetness(x, z) > 0.45) {
                double[] wt = edge.water(x, z);
                if (!Double.isNaN(wt[0]) && wt[0] > h) best = wt[0];
            }
            if (theme == Theme.SWAMP && d > 5 && h < baseY - 0.15) best = Double.isNaN(best) ? baseY - 0.12 : best;
            if (!Double.isNaN(sea) && sea > h && (Double.isNaN(best) || sea > best)) best = sea;
            return best;
        }

        float[] waterColour(double x, double z, double d) {
            if (theme == Theme.SWAMP && Double.isNaN(sea)) return new float[]{0.3f, 0.36f, 0.22f};
            if (theme == Theme.CRYSTAL) return new float[]{0.55f, 0.82f, 0.86f};
            if (!Double.isNaN(sea)) return new float[]{0.17f, 0.36f, 0.55f};
            return new float[]{0.22f, 0.43f, 0.6f};
        }

        /** How high a forest's canopy stands over the far ground at {@code (x, z)} (0: none there). */
        double canopy(double x, double z, double d, double h, double slope) {
            double above = h - baseY, m, top;
            switch (theme) {
                case FOREST -> {
                    m = smooth(0.34, 0.42, f(x, z, 120));
                    top = 15;
                }
                case PINES -> {
                    m = smooth(0.3, 0.38, f(x, z, 150)) * (1 - smooth(95 * k, 120 * k, above));
                    top = 16;
                }
                case JUNGLE -> {
                    m = smooth(0.2, 0.28, f(x, z, 90)) * (1 - smooth(1.4, 2.2, slope));
                    top = 21;
                }
                case MOUNTAINS -> {
                    m = smooth(0.42, 0.5, f(x, z, 120)) * (1 - smooth(70 * k, 90 * k, above)) * (1 - smooth(0.7, 1.0,
                            slope));
                    top = 14;
                }
                case SWAMP -> {
                    m = smooth(0.58, 0.64, f(x, z, 60));
                    top = 10;
                }
                case MEADOW, TOWN -> {
                    m = smooth(0.66, 0.72, f(x, z, 160));
                    top = 12;
                }
                case HILLS -> {
                    m = smooth(0.7, 0.76, f(x, z, 140)) * (1 - smooth(0.6, 0.9, slope));
                    top = 10;
                }
                case SAVANNAH -> {
                    m = smooth(0.8, 0.83, f(x, z, 45));
                    top = 7;
                }
                default -> {
                    return 0;
                }
            }
            if (m <= 0) return 0;
            // rising gently out of the near ring's own trees, not a wall of them at its edge
            double onset = smooth(W * 0.85, W * 0.85 + Math.max(30, W * 0.6), d);
            return m * onset * top * (0.75 + 0.5 * f(x, z, 20));
        }

        float[] canopyColour(double x, double z, double h) {
            double v = 0.88 + 0.24 * f(x, z, 45);
            float[] c = switch (theme) {
                case PINES, MOUNTAINS -> new float[]{0.2f, 0.36f, 0.27f};
                case JUNGLE -> new float[]{0.27f, 0.52f, 0.22f};
                case SWAMP -> new float[]{0.3f, 0.38f, 0.2f};
                case SAVANNAH -> new float[]{0.4f, 0.48f, 0.22f};
                default -> new float[]{0.25f, 0.42f, 0.2f};
            };
            for (int i = 0; i < 3; i++) c[i] *= (float) v;
            return c;
        }

        // --- the far ring's buildings ----------------------------------------------------------

        static final float[][] BRICK = {{0.6f, 0.3f, 0.22f}, {0.5f, 0.25f, 0.2f}, {0.66f, 0.38f, 0.27f},
                {0.82f, 0.77f, 0.66f}, {0.74f, 0.7f, 0.62f}, {0.42f, 0.36f, 0.32f}, {0.55f, 0.48f, 0.4f}};
        static final float[][] ROOF = {{0.28f, 0.3f, 0.36f}, {0.34f, 0.35f, 0.4f}, {0.55f, 0.27f, 0.2f},
                {0.24f, 0.25f, 0.29f}};

        /** A city on to the horizon: its ring streets and the streets out, the blocks of houses between, its towers. */
        void skyline(Mesh mesh, double R) {
            double reach = Math.min(R - E - 40, Math.max(260, s.far() * 0.4));
            int ring = (int) Math.ceil((W + 1) / LOOP);
            for (; ring * LOOP < W + reach; ring++) {
                double a = ring * LOOP + LOOP_WIDTH, b = (ring + 1) * LOOP;      // the block between two ring streets
                if (b <= W + 2) continue;
                double outer = a - W;
                double thinning = smooth(reach * 0.55, reach, outer);
                for (int side = 0; side < 4; side++) {
                    double run = side < 2 ? H + b : H + a;
                    // blocks between the streets running out, every 60 - 90 m
                    double u = -run;
                    int blk = 0;
                    while (u < run) {
                        double len = 60 + 30 * noise.hash(ring, side * 997 + blk, 201);
                        double u1 = Math.min(run, u + len);
                        if (u1 - u > 14) block(mesh, side, Math.max(a, W + 2), b, u + 4, u1 - 4, ring, blk, thinning,
                                outer);
                        u = u1;
                        blk++;
                    }
                }
            }
        }

        /** One block, {@code v0 - v1} out from the edge, {@code u0 - u1} along it: houses round it, a yard inside. */
        void block(Mesh mesh, int side, double v0, double v1, double u0, double u1, int ring, int blk, double thinning,
                   double outer) {
            double depth = (v1 - v0) / 2 - 1;
            if (depth < 4) return;
            boolean lod = outer > 260;
            for (int row = 0; row < 2; row++) {
                double rv0 = row == 0 ? v0 : v0 + depth + 2, rv1 = rv0 + depth;
                double u = u0;
                int lot = 0;
                while (u < u1 - 3) {
                    double hh = noise.hash(ring * 131 + blk, side * 17 + row * 7 + lot, 211);
                    double wide = lod ? u1 - u0 : 7 + 7 * hh;
                    double ue = Math.min(u1, u + wide);
                    if (noise.hash(ring + blk * 7, lot + row * 31 + side * 5, 212) < thinning * 0.8) {
                        u = ue + 0.4;
                        lot++;
                        continue;
                    }
                    int floors = 2 + (int) Math.floor(noise.hash(ring, blk * 13 + lot + row, 213) * 3.2);
                    double tall = floors * 3.3 + 1.2;
                    double rowDepth = depth - (lod ? 0 : 3 * noise.hash(lot, blk + row, 214));
                    double[] box = place(side, rv0 + (row == 0 ? 0 : depth - rowDepth), rv0 + (row == 0 ? rowDepth
                            : depth), u, ue);
                    double y0 = Math.min(Math.min(heightAt(box[0], box[2]), heightAt(box[1], box[3])),
                            Math.min(heightAt(box[0], box[3]), heightAt(box[1], box[2]))) - 0.5;
                    float[] wall = BRICK[(int) (noise.hash(lot, blk, 215) * BRICK.length)];
                    float[] roof = ROOF[(int) (noise.hash(lot, blk, 216) * ROOF.length)];
                    boolean gable = !lod && noise.hash(lot, ring, 217) < 0.65;
                    mesh.house(box[0], box[2], box[1], box[3], y0, y0 + tall + 0.5, wall, roof, gable, side < 2);
                    if (!lod && noise.hash(lot, blk, 218) < 0.5) {
                        double cx = box[0] + (box[1] - box[0]) * 0.3, cz = box[2] + (box[3] - box[2]) * 0.3;
                        mesh.box(cx - 0.5, cz - 0.4, cx + 0.5, cz + 0.4, y0 + tall, y0 + tall + 2.4,
                                new float[]{0.38f, 0.26f, 0.22f}, new float[]{0.2f, 0.18f, 0.17f}, MAT_WALL);
                    }
                    u = ue + (lod ? 0 : 0.2);
                    lot++;
                }
            }
            // now and then a landmark over the roofs: a spire, a clock tower, a works' chimney, a gasometer
            double lm = noise.hash(ring * 7 + side, blk, 220);
            if (lm < 0.07 && outer < 600) {
                double[] c = place(side, (v0 + v1) / 2 - 3, (v0 + v1) / 2 + 3, (u0 + u1) / 2 - 3, (u0 + u1) / 2 + 3);
                double y0 = heightAt((c[0] + c[1]) / 2, (c[2] + c[3]) / 2) - 0.5;
                double kind = noise.hash(ring, blk, 221);
                if (kind < 0.3) {
                    mesh.tower(c[0], c[2], c[1], c[3], y0, y0 + 26 + 20 * lm * 10, new float[]{0.72f, 0.66f, 0.55f},
                            new float[]{0.3f, 0.33f, 0.38f}, 9);
                } else if (kind < 0.55) {
                    mesh.cylinder((c[0] + c[1]) / 2, (c[2] + c[3]) / 2, 1.3, y0, y0 + 30 + 15 * kind, 8,
                            new float[]{0.5f, 0.27f, 0.2f}, MAT_WALL);
                } else if (kind < 0.8) {
                    mesh.cylinder((c[0] + c[1]) / 2, (c[2] + c[3]) / 2, 11, y0, y0 + 18, 12,
                            new float[]{0.45f, 0.5f, 0.48f}, MAT_ROOF);
                } else {
                    mesh.tower(c[0], c[2], c[1], c[3], y0, y0 + 34, new float[]{0.8f, 0.74f, 0.6f},
                            new float[]{0.58f, 0.5f, 0.24f}, 5);
                }
            }
        }

        /** The box {@code v0 - v1} out from the edge of side {@code side}, {@code u0 - u1} along it: x0, x1, z0, z1. */
        double[] place(int side, double v0, double v1, double u0, double u1) {
            return switch (side) {
                case 0 -> new double[]{u0, u1, -(H + v1), -(H + v0)};
                case 1 -> new double[]{u0, u1, H + v0, H + v1};
                case 2 -> new double[]{-(H + v1), -(H + v0), u0, u1};
                default -> new double[]{H + v0, H + v1, u0, u1};
            };
        }

        /** Villages and farms over the far fields: clusters of cottages round a church spire. */
        void villages(Mesh mesh, double R) {
            double cell = theme == Theme.TOWN ? 360 : 520;
            int n = (int) Math.ceil(R / cell);
            for (int j = -n; j <= n; j++) {
                for (int i = -n; i <= n; i++) {
                    if (noise.hash(i, j, 301) > (theme == Theme.TOWN ? 0.6 : 0.4)) continue;
                    double cx = (i + 0.2 + 0.6 * noise.hash(i, j, 302)) * cell;
                    double cz = (j + 0.2 + 0.6 * noise.hash(i, j, 303)) * cell;
                    double d = out(cx, cz);
                    if (d < W + 60 || Math.hypot(cx, cz) > R * 0.8) continue;
                    int houses = 5 + (int) (noise.hash(i, j, 304) * 10);
                    for (int q = 0; q < houses; q++) {
                        double a = noise.hash(q, i * 31 + j, 305) * Math.PI * 2, r = 8 + 40 * noise.hash(q, j, 306);
                        double hx = cx + Math.cos(a) * r, hz = cz + Math.sin(a) * r;
                        if (out(hx, hz) < W + 20) continue;
                        double y0 = heightAt(hx, hz) - 0.5;
                        double w = 4 + 3 * noise.hash(q, i, 307), dd = 3.5 + 2 * noise.hash(q, j, 308);
                        boolean along = noise.hash(q, i + j, 309) < 0.5;
                        float[] wall = noise.hash(q, i, 310) < 0.6 ? new float[]{0.86f, 0.8f, 0.68f}
                                : new float[]{0.62f, 0.5f, 0.38f};
                        float[] roof = noise.hash(q, j, 311) < 0.5 ? new float[]{0.6f, 0.3f, 0.22f}
                                : new float[]{0.62f, 0.52f, 0.3f};
                        mesh.house(hx - (along ? w : dd), hz - (along ? dd : w), hx + (along ? w : dd),
                                hz + (along ? dd : w), y0, y0 + 4.5, wall, roof, true, along);
                    }
                    double y0 = heightAt(cx, cz) - 0.5;
                    mesh.tower(cx - 2.5, cz - 2.5, cx + 2.5, cz + 2.5, y0, y0 + 16, new float[]{0.7f, 0.68f, 0.62f},
                            new float[]{0.3f, 0.32f, 0.38f}, 9);
                }
            }
        }

        /** The crystal lands' great prisms, in clusters, glowing faintly. */
        void prisms(Mesh mesh, double R) {
            double cell = 170;
            int n = (int) Math.ceil(R / cell);
            float[][] cols = {{0.72f, 0.6f, 0.95f}, {0.5f, 0.86f, 0.82f}, {0.95f, 0.62f, 0.85f}, {0.62f, 0.76f, 1f}};
            for (int j = -n; j <= n; j++) {
                for (int i = -n; i <= n; i++) {
                    if (noise.hash(i, j, 401) > 0.45) continue;
                    double cx = (i + 0.5 * noise.hash(i, j, 402) + 0.25) * cell;
                    double cz = (j + 0.5 * noise.hash(i, j, 403) + 0.25) * cell;
                    if (out(cx, cz) < W + 30 || Math.hypot(cx, cz) > R * 0.85) continue;
                    int many = 1 + (int) (noise.hash(i, j, 404) * 5);
                    for (int q = 0; q < many; q++) {
                        double a = noise.hash(q, i, 405) * Math.PI * 2, r = q == 0 ? 0 : 6 + 14 * noise.hash(q, j, 406);
                        double px = cx + Math.cos(a) * r, pz = cz + Math.sin(a) * r;
                        double y0 = heightAt(px, pz) - 1;
                        double tall = (q == 0 ? 40 : 14) + 50 * noise.hash(q, i + j, 407) * (q == 0 ? 1 : 0.5);
                        double rad = tall * (0.12 + 0.05 * noise.hash(q, i, 408));
                        float[] col = cols[(int) (noise.hash(q, j, 409) * cols.length)];
                        mesh.prism(px, pz, rad, y0, y0 + tall, 6, col, noise.hash(q, i, 410) * Math.PI);
                    }
                }
            }
        }
    }

    // =================================================================================================
    // the far ring's mesh
    // =================================================================================================

    /** A growing mesh: corners and triangles, and the shapes of the far ring's buildings. */
    static final class Mesh {
        float[] v = new float[Far.STRIDE * 4096];
        int[] t = new int[3 * 8192];
        int nv, nt;

        int vertex(double x, double y, double z, float[] c, int mat) {
            if ((nv + 1) * Far.STRIDE > v.length) v = Arrays.copyOf(v, v.length * 2);
            int o = nv * Far.STRIDE;
            v[o] = (float) x;
            v[o + 1] = (float) y;
            v[o + 2] = (float) z;
            v[o + 3] = c[0];
            v[o + 4] = c[1];
            v[o + 5] = c[2];
            v[o + 6] = mat;
            return nv++;
        }

        void tri(int a, int b, int c) {
            if (nt + 3 > t.length) t = Arrays.copyOf(t, t.length * 2);
            t[nt++] = a;
            t[nt++] = b;
            t[nt++] = c;
        }

        void quad(double[] a, double[] b, double[] c, double[] d, float[] col, int mat) {
            int i = vertex(a[0], a[1], a[2], col, mat), j = vertex(b[0], b[1], b[2], col, mat);
            int k = vertex(c[0], c[1], c[2], col, mat), l = vertex(d[0], d[1], d[2], col, mat);
            tri(i, j, k);
            tri(i, k, l);
        }

        /** A box's four walls and its flat top. */
        void box(double x0, double z0, double x1, double z1, double y0, double y1, float[] wall, float[] top,
                 int wallMat) {
            quad(new double[]{x0, y0, z0}, new double[]{x1, y0, z0}, new double[]{x1, y1, z0},
                    new double[]{x0, y1, z0}, wall, wallMat);
            quad(new double[]{x1, y0, z0}, new double[]{x1, y0, z1}, new double[]{x1, y1, z1},
                    new double[]{x1, y1, z0}, shade(wall, 0.86f), wallMat);
            quad(new double[]{x1, y0, z1}, new double[]{x0, y0, z1}, new double[]{x0, y1, z1},
                    new double[]{x1, y1, z1}, wall, wallMat);
            quad(new double[]{x0, y0, z1}, new double[]{x0, y0, z0}, new double[]{x0, y1, z0},
                    new double[]{x0, y1, z1}, shade(wall, 0.86f), wallMat);
            quad(new double[]{x0, y1, z0}, new double[]{x1, y1, z0}, new double[]{x1, y1, z1},
                    new double[]{x0, y1, z1}, top, MAT_ROOF);
        }

        /** A house: walls up to {@code y1}, and a gable roof along x ({@code alongX}) or z, or a flat one. */
        void house(double x0, double z0, double x1, double z1, double y0, double y1, float[] wall, float[] roof,
                   boolean gable, boolean alongX) {
            box(x0, z0, x1, z1, y0, y1, wall, roof, MAT_WALL);
            if (!gable) return;
            double span = alongX ? z1 - z0 : x1 - x0, rise = Math.min(4.5, span * 0.45);
            double over = 0.25;
            if (alongX) {
                double zm = (z0 + z1) / 2;
                quad(new double[]{x0 - over, y1, z0 - over}, new double[]{x1 + over, y1, z0 - over},
                        new double[]{x1 + over, y1 + rise, zm}, new double[]{x0 - over, y1 + rise, zm}, roof, MAT_ROOF);
                quad(new double[]{x1 + over, y1, z1 + over}, new double[]{x0 - over, y1, z1 + over},
                        new double[]{x0 - over, y1 + rise, zm}, new double[]{x1 + over, y1 + rise, zm},
                        shade(roof, 0.8f), MAT_ROOF);
                int a = vertex(x0, y1, z0, wall, MAT_WALL), b = vertex(x0, y1, z1, wall, MAT_WALL),
                        c = vertex(x0, y1 + rise, zm, wall, MAT_WALL);
                tri(a, b, c);
                a = vertex(x1, y1, z0, wall, MAT_WALL);
                b = vertex(x1, y1, z1, wall, MAT_WALL);
                c = vertex(x1, y1 + rise, zm, wall, MAT_WALL);
                tri(a, c, b);
            } else {
                double xm = (x0 + x1) / 2;
                quad(new double[]{x0 - over, y1, z1 + over}, new double[]{x0 - over, y1, z0 - over},
                        new double[]{xm, y1 + rise, z0 - over}, new double[]{xm, y1 + rise, z1 + over}, roof, MAT_ROOF);
                quad(new double[]{x1 + over, y1, z0 - over}, new double[]{x1 + over, y1, z1 + over},
                        new double[]{xm, y1 + rise, z1 + over}, new double[]{xm, y1 + rise, z0 - over},
                        shade(roof, 0.8f), MAT_ROOF);
                int a = vertex(x0, y1, z0, wall, MAT_WALL), b = vertex(x1, y1, z0, wall, MAT_WALL),
                        c = vertex(xm, y1 + rise, z0, wall, MAT_WALL);
                tri(a, b, c);
                a = vertex(x0, y1, z1, wall, MAT_WALL);
                b = vertex(x1, y1, z1, wall, MAT_WALL);
                c = vertex(xm, y1 + rise, z1, wall, MAT_WALL);
                tri(a, c, b);
            }
        }

        /** A tower and its pointed cap {@code cap} metres high. */
        void tower(double x0, double z0, double x1, double z1, double y0, double y1, float[] wall, float[] roof,
                   double cap) {
            box(x0, z0, x1, z1, y0, y1, wall, roof, MAT_WALL);
            double xm = (x0 + x1) / 2, zm = (z0 + z1) / 2;
            int top = vertex(xm, y1 + cap, zm, roof, MAT_ROOF);
            double[][] c = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
            for (int i = 0; i < 4; i++) {
                double[] a = c[i], b = c[(i + 1) % 4];
                float[] col = i % 2 == 0 ? roof : shade(roof, 0.8f);
                int ia = vertex(a[0], y1, a[1], col, MAT_ROOF), ib = vertex(b[0], y1, b[1], col, MAT_ROOF);
                int it = vertex(xm, y1 + cap, zm, col, MAT_ROOF);
                tri(ia, ib, it);
            }
            if (top < 0) throw new IllegalStateException();
        }

        /** An upright cylinder of {@code sides} sides, closed on top. */
        void cylinder(double cx, double cz, double r, double y0, double y1, int sides, float[] col, int mat) {
            int topMid = vertex(cx, y1, cz, shade(col, 1.1f), mat);
            for (int i = 0; i < sides; i++) {
                double a0 = i * 2 * Math.PI / sides, a1 = (i + 1) * 2 * Math.PI / sides;
                double[] p0 = {cx + Math.cos(a0) * r, cz + Math.sin(a0) * r}, p1 = {cx + Math.cos(a1) * r,
                        cz + Math.sin(a1) * r};
                quad(new double[]{p0[0], y0, p0[1]}, new double[]{p1[0], y0, p1[1]}, new double[]{p1[0], y1, p1[1]},
                        new double[]{p0[0], y1, p0[1]}, col, mat);
                int a = vertex(p0[0], y1, p0[1], shade(col, 1.1f), mat), b = vertex(p1[0], y1, p1[1], shade(col, 1.1f),
                        mat);
                tri(topMid, a, b);
            }
        }

        /** A crystal: an upright prism of {@code sides} sides with a pointed top, turned {@code turn} radians. */
        void prism(double cx, double cz, double r, double y0, double y1, int sides, float[] col, double turn) {
            double tip = y1 + r * 1.6;
            for (int i = 0; i < sides; i++) {
                double a0 = turn + i * 2 * Math.PI / sides, a1 = turn + (i + 1) * 2 * Math.PI / sides;
                double[] p0 = {cx + Math.cos(a0) * r, cz + Math.sin(a0) * r}, p1 = {cx + Math.cos(a1) * r,
                        cz + Math.sin(a1) * r};
                float[] c = shade(col, i % 2 == 0 ? 1f : 0.84f);
                quad(new double[]{p0[0], y0, p0[1]}, new double[]{p1[0], y0, p1[1]}, new double[]{p1[0], y1, p1[1]},
                        new double[]{p0[0], y1, p0[1]}, c, MAT_CRYSTAL);
                int a = vertex(p0[0], y1, p0[1], c, MAT_CRYSTAL), b = vertex(p1[0], y1, p1[1], c, MAT_CRYSTAL);
                int t = vertex(cx, tip, cz, shade(col, 1.15f), MAT_CRYSTAL);
                tri(a, b, t);
            }
        }

        static float[] shade(float[] c, float k) {
            return new float[]{Math.min(1, c[0] * k), Math.min(1, c[1] * k), Math.min(1, c[2] * k)};
        }

        float[] vertices() { return Arrays.copyOf(v, nv * Far.STRIDE); }

        int[] indices() { return Arrays.copyOf(t, nt); }
    }
}
