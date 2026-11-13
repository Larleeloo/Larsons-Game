package com.larsons.game.tools;

import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.building.BuildingProfile;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.story.Quest;
import com.larsons.game.world.Atmosphere;
import com.larsons.game.world.GroundMap;
import com.larsons.game.world.WaterMap;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.DoubleUnaryOperator;

/**
 * Old London as the steam age would have had it, about 1910 to 1925: a
 * demo level 512 m across, as dense as a city is, with every one of the
 * city buildings in it ({@code buildings/city.py} in the modelling repo):
 *
 * <pre>
 *   ./gradlew makeLondon              # → assets/levels/london/
 *   ./gradlew makeLondon -Pplan       # and build/london-plan.png, the plan from above
 * </pre>
 *
 * <p>The Thames runs east through the south of it between stone
 * embankments, under a clock tower's bridge, an iron bridge and a bascule
 * bridge with its two towers where the river widens into the Pool. North of
 * it the streets run in a grid - the Strand, Holborn and Oxford Street east
 * and west, seven avenues north and south, a lane down the middle of each
 * block - and the districts go as London's did: the park, the station and
 * the airship field in the north; the theatres, the market and the great
 * square in the west; the cathedral and the banks in the City; Westminster
 * by the river; the wharves, the gas works and the breweries on the south
 * bank. Lamps on every pavement, pillar boxes and kiosks, omnibuses and
 * steam carriages at the kerbs, Londoners about their business, a few of
 * them with work for her, and a gang on the wharves.
 *
 * <p>The edges are city too: the level's surroundings ({@code Surroundings}
 * "city") carry the streets on past them, so there is no end to it.
 *
 * <p>East is +x, south +z; a heading is in degrees, 0 facing north (-z).
 */
public final class London {

    static final int SIZE = 512;
    static final double HALF = SIZE / 2.0, EDGE = HALF - 1.0;
    /** The river: its water, the foot of its walls and its bed (metres; the streets are at 0). */
    static final double SURFACE = -2.2, FOOT = -2.9, BED = -5.6;
    /** The main streets east and west (z), the avenues north and south (x), the lanes. */
    static final double OXFORD = -160, HOLBORN = -60, STRAND = 40, SOUTHWARK = 232;
    static final double[] AVENUES = {-185, -120, -60, 10, 80, 150, 215};
    static final double[] SOUTH_AVENUES = {-185, -60, 40, 150};
    static final double[] LANES = {-210, -110, -10};
    /** Half a main street's roadway, its pavement; a lane's; the embankment's. */
    static final double ROAD = 6, WALK = 3, LANE = 3.2, LANE_WALK = 1.6, BANK_ROAD = 5.5, RIVERSIDE = 5;
    /** The bridges: x, and how far either side of it the river's railings stop. */
    static final double WESTMINSTER = -185, IRON = -60, TOWER = 150;

    private static final String HUMAN = "human", DRURINEAN = "drurinean", HALF_DRURINEAN = "half_drurinean",
            KHORIGALE = "khorigale";

    // --- the river -----------------------------------------------------------------------------

    /** The river's middle at {@code x}. */
    static double riverZ(double x) {
        return 140 + 10 * Math.sin(x / 120 - 2.177);
    }

    /** Half its width at {@code x}: wider at the Pool, by the tower bridge. */
    static double riverHalf(double x) {
        double t = (x - TOWER) / 55;
        return 25.5 + 15 * Math.exp(-t * t);
    }

    static double northWall(double x) { return riverZ(x) - riverHalf(x); }

    static double southWall(double x) { return riverZ(x) + riverHalf(x); }

    /** The building line facing the river on the north bank, and the south. */
    static double northFront(double x) { return northWall(x) - RIVERSIDE - 2 * BANK_ROAD - WALK; }

    static double southFront(double x) { return southWall(x) + RIVERSIDE + 2 * BANK_ROAD + WALK; }

    // --- the plan: what is solid, as boxes -----------------------------------------------------

    /** A box on the plan, turned: its middle, its axes (u across its front, n out of it), half its size each way. */
    record Box(double cx, double cz, double ux, double uz, double nx, double nz, double hu, double hn, String kind) {

        boolean overlaps(Box o, double slack) {
            double dx = o.cx - cx, dz = o.cz - cz;
            double[][] axes = {{ux, uz}, {nx, nz}, {o.ux, o.uz}, {o.nx, o.nz}};
            for (double[] a : axes) {
                double d = Math.abs(dx * a[0] + dz * a[1]);
                double ra = hu * Math.abs(ux * a[0] + uz * a[1]) + hn * Math.abs(nx * a[0] + nz * a[1]);
                double rb = o.hu * Math.abs(o.ux * a[0] + o.uz * a[1]) + o.hn * Math.abs(o.nx * a[0] + o.nz * a[1]);
                if (d >= ra + rb - slack) return false;
            }
            return true;
        }

        double[][] corners() {
            double[][] c = new double[4][];
            int i = 0;
            for (int a = -1; a <= 1; a += 2) {
                for (int b = -1; b <= 1; b += 2) {
                    int k = a < 0 ? (b < 0 ? 0 : 1) : (b < 0 ? 3 : 2);
                    c[k] = new double[]{cx + ux * hu * a + nx * hn * b, cz + uz * hu * a + nz * hn * b};
                    i++;
                }
            }
            return c;
        }

        /** A box from (x0, z0) to (x1, z1), square to the map. */
        static Box rect(double x0, double z0, double x1, double z1, String kind) {
            return new Box((x0 + x1) / 2, (z0 + z1) / 2, 1, 0, 0, -1, Math.abs(x1 - x0) / 2, Math.abs(z1 - z0) / 2,
                    kind);
        }

        /** A strip {@code half} wide each side of the segment (a road). */
        static Box strip(double x0, double z0, double x1, double z1, double half, String kind) {
            double len = Math.hypot(x1 - x0, z1 - z0), ux = (x1 - x0) / len, uz = (z1 - z0) / len;
            return new Box((x0 + x1) / 2, (z0 + z1) / 2, ux, uz, -uz, ux, len / 2, half, kind);
        }
    }

    /** Kinds of building to pick from, each so often. */
    static final class Mix {
        final List<String> kinds = new ArrayList<>();
        final List<Double> weights = new ArrayList<>();
        double total;

        Mix(Object... kw) {
            for (int i = 0; i + 1 < kw.length; i += 2) {
                kinds.add((String) kw[i]);
                double w = ((Number) kw[i + 1]).doubleValue();
                weights.add(w);
                total += w;
            }
        }

        String pick(Random rnd) {
            double r = rnd.nextDouble() * total;
            for (int i = 0; i < kinds.size(); i++) {
                r -= weights.get(i);
                if (r <= 0) return kinds.get(i);
            }
            return kinds.get(kinds.size() - 1);
        }
    }

    // the districts, by what lines their streets
    static final Mix WEST = new Mix("stucco_townhouse", 5, "terrace_house", 2.5, "lodging_house", 1, "tea_room", 0.4,
            "apothecary", 0.3);
    static final Mix WEST_END = new Mix("tea_room", 2, "tavern", 2, "apothecary", 1.2, "clockmaker", 0.6,
            "terrace_house", 2, "stucco_townhouse", 1.5, "lodging_house", 1, "shop_house", 1, "corner_shop", 0.2,
            "post_office", 0.3);
    static final Mix CITY = new Mix("bank", 1.2, "stucco_townhouse", 2.5, "clockmaker", 0.5, "tea_room", 1.2,
            "terrace_house", 1.5, "tavern", 1, "post_office", 0.6, "constabulary", 0.5, "lodging_house", 0.8,
            "apothecary", 0.6, "warehouse", 0.4);
    static final Mix BLOOMSBURY = new Mix("terrace_house", 4, "stucco_townhouse", 2, "lodging_house", 2,
            "apothecary", 0.6, "tea_room", 0.6, "clockmaker", 0.4, "chapel", 0.25);
    static final Mix EAST = new Mix("tenement", 3, "lodging_house", 2, "terrace_house", 2, "row_house", 1.5,
            "tavern", 1, "shop_house", 1, "corner_shop", 0.2, "printing_works", 0.4, "warehouse", 0.4,
            "workshop", 0.5, "chapel", 0.2);
    static final Mix LANE_SIDE = new Mix("terrace_house", 3, "lodging_house", 2, "row_house", 1.5, "tavern", 0.7,
            "workshop", 0.8, "corner_shop", 0.15, "clockmaker", 0.4, "apothecary", 0.4, "shop_house", 0.6,
            "cottage", 0.3);
    static final Mix RIVERFRONT = new Mix("stucco_townhouse", 3, "tenement", 1.5, "bank", 0.6, "terrace_house", 1.5,
            "tea_room", 0.8, "tavern", 0.6, "lodging_house", 0.8, "warehouse", 0.5);
    static final Mix DOCKS = new Mix("warehouse", 5, "tavern", 1, "tenement", 1, "workshop", 1, "printing_works",
            0.7, "lodging_house", 0.6, "foundry", 0.4, "brewery", 0.4, "engine_house", 0.3);
    static final Mix BOROUGH = new Mix("terrace_house", 2.5, "tenement", 2.5, "lodging_house", 1.5, "tavern", 1,
            "corner_shop", 0.2, "row_house", 1.2, "shop_house", 0.8, "workshop", 0.6, "chapel", 0.3);
    static final Mix CROSS_STREET = new Mix("corner_shop", 0.3, "clockmaker", 0.5, "apothecary", 1, "tea_room", 1,
            "lodging_house", 1.5, "terrace_house", 1.5, "tavern", 0.8);

    // --- the level ---------------------------------------------------------------------------------

    final Land l;
    final Random rnd;
    final BuildingCatalog catalog = BuildingCatalog.get();
    /** What stands: the buildings, and the roads (kept clear). */
    final List<Box> solid = new ArrayList<>();
    final List<Box> roads = new ArrayList<>();
    /** The street furniture and the carriages, kept from each other. */
    final List<Box> furniture = new ArrayList<>();
    final Map<String, Integer> tally = new TreeMap<>();
    /** Where people stand about: on pavements, at doors. */
    final List<double[]> pavements = new ArrayList<>();

    London(long seed) {
        l = new Land("london", "Old London", SIZE, seed);
        rnd = l.rnd;
    }

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : "assets/levels");
        London london = new London(1912);
        london.build();
        london.l.save(root);
        london.report();
        if (args.length > 1) london.plan(Path.of(args[1]));
    }

    void build() {
        sky();
        ground();
        streets();
        landmarks();
        districts();
        southBank();
        embankments();
        streetFurniture();
        traffic();
        folk();
    }

    void report() {
        List<String> missing = new ArrayList<>();
        for (String k : catalog.keys()) {
            BuildingProfile p = catalog.profile(k);
            if (p != null && p.city() && !tally.containsKey(k)) missing.add(k);
        }
        int n = 0;
        for (int v : tally.values()) n += v;
        System.out.println("london: " + n + " buildings and props, " + tally);
        if (!missing.isEmpty()) throw new IllegalStateException("city buildings London has none of: " + missing);
    }

    // --- sky and ground --------------------------------------------------------------------------

    void sky() {
        // a smoky autumn evening: the sky brown-grey over the chimneys, the river mist rising
        l.sky(0x5D6779, 0xC4B49A, 150, Atmosphere.DEFAULT.withStartHour(17.2).withClouds(0.7).withMist(0.25)
                .withMistHeight(3.0).withRays(0.15));
        l.wild("crow", "cat", "dog", "robin", "duck", "swan");
    }

    void ground() {
        // flat streets, the river cut down between upright walls, its bed deepening to the middle
        l.shape((x, z, was) -> {
            double d = Math.abs(z - riverZ(x)), hw = riverHalf(x);
            if (d >= hw) return 0;
            return FOOT + (BED - FOOT) * Land.ease((hw - d) / 12);
        });
        for (double x = -HALF - 8; x <= HALF + 8; x += 3) {
            l.level.water().paint(WaterMap.Kind.CLEAR, SURFACE, x, riverZ(x), riverHalf(x) + 0.8, 1.1, 0);
            l.level.ground().stroke(GroundMap.MUD, x, riverZ(x), x + 3, riverZ(x + 3), riverHalf(x) - 1);
            l.keepOpen(x, riverZ(x), riverHalf(x) + 1);
        }
        // the yards and mews behind the fronts: packed earth under everything that is not street or green
        fill(GroundMap.PACKED, -HALF, -HALF, HALF, HALF);
    }

    /** {@code material} over the rectangle, in strokes. */
    void fill(int material, double x0, double z0, double x1, double z1) {
        double h = 0.6;
        for (double z = Math.min(z0, z1) + h; z <= Math.max(z0, z1) - h + 1e-6; z += h * 1.6) {
            l.level.ground().stroke(material, Math.min(x0, x1) + h, z, Math.max(x0, x1) - h, z, h);
        }
    }

    /** A street from point to point: cobbled roadway {@code road} to a side, flagged pavement {@code walk} past it. */
    void street(double road, double walk, double... xz) {
        for (int i = 0; i + 3 < xz.length; i += 2) {
            l.level.ground().stroke(GroundMap.FLAGSTONES, xz[i], xz[i + 1], xz[i + 2], xz[i + 3], road + walk);
            l.level.ground().stroke(GroundMap.COBBLES, xz[i], xz[i + 1], xz[i + 2], xz[i + 3], road);
            roads.add(Box.strip(xz[i], xz[i + 1], xz[i + 2], xz[i + 3], road + walk, "road"));
            double len = Math.hypot(xz[i + 2] - xz[i], xz[i + 3] - xz[i + 1]);
            for (double d = 0; d <= len; d += 3) {
                double t = d / len;
                l.keepOpen(xz[i] + (xz[i + 2] - xz[i]) * t, xz[i + 1] + (xz[i + 3] - xz[i + 1]) * t, road + walk + 0.5);
            }
        }
    }

    /** Nothing more is built on the rectangle: a square, a park, a yard kept open. */
    void reserve(double x0, double z0, double x1, double z1) {
        solid.add(Box.rect(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), "open"));
    }

    void streets() {
        // east and west
        for (double z : new double[]{OXFORD, HOLBORN, STRAND}) street(ROAD, WALK, -HALF - 4, z, HALF + 4, z);
        // the avenues: north and south from the river (some only so far)
        for (double x : AVENUES) {
            double north = (x == WESTMINSTER || x == 215) ? OXFORD : -HALF - 4;
            street(ROAD, WALK, x, north, x, northWall(x) - RIVERSIDE - BANK_ROAD);
        }
        // the lanes, block by block (not where something big has the block)
        for (double z : LANES) {
            for (int c = 0; c <= AVENUES.length; c++) {
                double x0 = c == 0 ? -HALF - 4 : AVENUES[c - 1], x1 = c == AVENUES.length ? HALF + 4 : AVENUES[c];
                if (laneSkipped(z, (x0 + x1) / 2)) continue;
                street(LANE, LANE_WALK, x0, z, x1, z);
            }
        }
        // the embankments, along the river walls, and the south bank's streets
        street(BANK_ROAD, 0, curve(x -> northWall(x) - RIVERSIDE - BANK_ROAD, -HALF - 4, HALF + 4));
        curveWalk(x -> northWall(x) - RIVERSIDE - 2 * BANK_ROAD - WALK / 2, WALK / 2);
        curveWalk(x -> northWall(x) - RIVERSIDE / 2, RIVERSIDE / 2);
        street(BANK_ROAD, 0, curve(x -> southWall(x) + RIVERSIDE + BANK_ROAD, -HALF - 4, HALF + 4));
        curveWalk(x -> southWall(x) + RIVERSIDE + 2 * BANK_ROAD + WALK / 2, WALK / 2);
        curveWalk(x -> southWall(x) + RIVERSIDE / 2, RIVERSIDE / 2);
        street(5, 2.5, -HALF - 4, SOUTHWARK, HALF + 4, SOUTHWARK);
        for (double x : SOUTH_AVENUES) street(5, 2.5, x, southWall(x) + RIVERSIDE + BANK_ROAD, x, SOUTHWARK);
    }

    /** Points along {@code z(x)} from x0 to x1, every 8 m. */
    static double[] curve(DoubleUnaryOperator z, double x0, double x1) {
        int n = (int) Math.ceil((x1 - x0) / 8);
        double[] xz = new double[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double x = x0 + (x1 - x0) * i / n;
            xz[i * 2] = x;
            xz[i * 2 + 1] = z.applyAsDouble(x);
        }
        return xz;
    }

    /** A pavement {@code half} to a side of {@code z(x)}, the whole way across. */
    void curveWalk(DoubleUnaryOperator z, double half) {
        double[] xz = curve(z, -HALF - 4, HALF + 4);
        for (int i = 0; i + 3 < xz.length; i += 2) {
            l.level.ground().stroke(GroundMap.FLAGSTONES, xz[i], xz[i + 1], xz[i + 2], xz[i + 3], half);
            roads.add(Box.strip(xz[i], xz[i + 1], xz[i + 2], xz[i + 3], half, "walk"));
        }
    }

    /** Whether the lane at {@code z} has no stretch round {@code x}: a block given over to something big. */
    static boolean laneSkipped(double z, double x) {
        if (z == -210) return x < -120 || (x > 10 && x < 150) || x > 150;      // the park, the station, the yard, the field
        if (z == -110) return x > -60 && x < 10;                               // the museum's
        return x > 10 && x < 80;                                               // the cathedral's
    }

    // --- placing ---------------------------------------------------------------------------------

    BuildingProfile profile(String kind) {
        BuildingProfile p = catalog.profile(kind);
        if (p == null) throw new IllegalArgumentException("no such building: " + kind);
        return p;
    }

    /** Its extent, model metres: x from, x to, y from (its front), y to. */
    double[] extents(String kind) {
        BuildingProfile p = profile(kind);
        double x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9;
        for (BuildingProfile.Shape s : p.footprint()) {
            double rx = s.circle() ? s.w() : s.w() / 2, ry = s.circle() ? s.w() : s.d() / 2;
            x0 = Math.min(x0, s.x() - rx);
            x1 = Math.max(x1, s.x() + rx);
            y0 = Math.min(y0, s.y() - ry);
            y1 = Math.max(y1, s.y() + ry);
        }
        for (BuildingProfile.Step s : p.steps()) {
            x0 = Math.min(x0, s.x() - s.w() / 2);
            x1 = Math.max(x1, s.x() + s.w() / 2);
            y0 = Math.min(y0, s.y() - s.d() / 2);
            y1 = Math.max(y1, s.y() + s.d() / 2);
        }
        if (x0 > x1) return new double[]{-0.6, 0.6, -0.6, 0.6};
        return new double[]{x0, x1, y0, y1};
    }

    static double[] facingOf(double heading) {
        double h = Math.toRadians(heading);
        return new double[]{-Math.sin(h), -Math.cos(h)};
    }

    static double headingOf(double nx, double nz) {
        return Math.toDegrees(Math.atan2(-nx, -nz));
    }

    /** Its box on the plan, standing at {@code (x, z)} facing {@code heading}, {@code grow} larger each way. */
    Box box(String kind, double x, double z, double heading, double grow) {
        double[] e = extents(kind), n = facingOf(heading);
        double ux = n[1], uz = -n[0];
        double cm = (e[0] + e[1]) / 2, cy = (e[2] + e[3]) / 2;
        return new Box(x + ux * cm - n[0] * cy, z + uz * cm - n[1] * cy, ux, uz, n[0], n[1],
                (e[1] - e[0]) / 2 + grow, (e[3] - e[2]) / 2 + grow, kind);
    }

    boolean inside(Box b, double margin) {
        for (double[] c : b.corners()) {
            if (Math.abs(c[0]) > EDGE - margin || Math.abs(c[1]) > EDGE - margin) return false;
        }
        return true;
    }

    boolean wet(Box b) {
        for (double[] c : b.corners()) {
            double d = Math.abs(c[1] - riverZ(c[0]));
            if (d < riverHalf(c[0]) + 0.5) return true;
        }
        return Math.abs(b.cz - riverZ(b.cx)) < riverHalf(b.cx) + 0.5;
    }

    /** Whether {@code b} is clear of what stands, of the streets, of the river, and in the level. */
    boolean free(Box b) {
        if (!inside(b, 0) || wet(b)) return false;
        for (Box o : roads) if (o.overlaps(b, 0.05)) return false;
        for (Box o : solid) if (o.overlaps(b, 0.05)) return false;
        return true;
    }

    /** Put it up: no questions asked. */
    LevelObject raise(String kind, double x, double z, double heading) {
        Box b = box(kind, x, z, heading, 0);
        solid.add(b);
        tally.merge(kind, 1, Integer::sum);
        LevelObject o = l.prop(kind, round(x), round(z), round(heading));
        l.keepOpen(b.cx, b.cz, Math.max(b.hu, b.hn));
        double[] door = profile(kind).door();
        if (door != null) {
            double[] n = facingOf(heading);
            double ux = n[1], uz = -n[0];
            pavements.add(new double[]{x + ux * door[0] - n[0] * door[1], z + uz * door[0] - n[1] * door[1]});
        }
        return o;
    }

    /** Put it up if it fits. */
    boolean fit(String kind, double x, double z, double heading) {
        if (!free(box(kind, x, z, heading, 0))) return false;
        raise(kind, x, z, heading);
        return true;
    }

    static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /**
     * A building with the front of it on the line through {@code (ax, az)}
     * along {@code (dx, dz)}, facing {@code (nx, nz)}, the near end of it
     * {@code t} metres along: where it stands, and how wide it is (along).
     */
    double[] onLine(String kind, double ax, double az, double dx, double dz, double nx, double nz, double t) {
        double[] e = extents(kind);
        double ux = nz, uz = -nx, s = Math.signum(ux * dx + uz * dz);
        double along = s >= 0 ? t - e[0] : t + e[1];
        double lx = ax + dx * along, lz = az + dz * along;
        return new double[]{lx + e[2] * nx, lz + e[2] * nz, e[1] - e[0]};
    }

    /** Buildings shoulder to shoulder along the line from a to b, facing (nx, nz), picked from {@code mix}. */
    void frontage(double ax, double az, double bx, double bz, double nx, double nz, Mix mix) {
        double len = Math.hypot(bx - ax, bz - az), dx = (bx - ax) / len, dz = (bz - az) / len;
        double heading = headingOf(nx, nz);
        double t = 0;
        while (t < len - 3) {
            boolean placed = false;
            for (int tries = 0; tries < 6 && !placed; tries++) {
                String kind = mix.pick(rnd);
                double[] at = onLine(kind, ax, az, dx, dz, nx, nz, t);
                if (t + at[2] > len + 0.2) continue;
                if (fit(kind, at[0], at[1], heading)) {
                    t += at[2] + (rnd.nextDouble() < 0.12 ? 1.5 + rnd.nextDouble() * 2 : 0.0);
                    placed = true;
                }
            }
            if (!placed) t += 1;
        }
    }

    /** Buildings along the curving line {@code z(x)} from x0 to x1, facing south ({@code south}) or north. */
    void curvedFrontage(DoubleUnaryOperator line, double x0, double x1, boolean south, Mix mix) {
        double x = x0;
        while (x < x1 - 3) {
            boolean placed = false;
            for (int tries = 0; tries < 6 && !placed; tries++) {
                String kind = mix.pick(rnd);
                double[] e = extents(kind);
                double w = e[1] - e[0];
                if (x + w > x1 + 0.2) continue;
                double az = line.applyAsDouble(x), bz = line.applyAsDouble(x + w);
                double len = Math.hypot(w, bz - az), dx = w / len, dz = (bz - az) / len;
                // the face to the street: square to the chord, out to the river (south) or the street (north)
                double nx = south ? -dz : dz, nz = south ? dx : -dx;
                double[] at = onLine(kind, x, az, dx, dz, nx, nz, 0);
                if (fit(kind, at[0], at[1], headingOf(nx, nz))) {
                    x += w * dx + (rnd.nextDouble() < 0.1 ? 1.5 + rnd.nextDouble() * 2 : 0.0);
                    placed = true;
                }
            }
            if (!placed) x += 1;
        }
    }

    /**
     * A corner building on the corner {@code (cx, cz)} of a block, its own
     * corner (the cut one, with the door) out to the crossing that way:
     * {@code (dx, dz)}, each ±1.
     */
    boolean corner(String kind, double cx, double cz, double dx, double dz) {
        double[] e = extents(kind);
        for (int k = 0; k < 4; k++) {
            double heading = k * 90.0;
            double[] n = facingOf(heading);
            double ux = n[1], uz = -n[0];
            if (Math.signum(Math.round(ux + n[0])) != dx || Math.signum(Math.round(uz + n[1])) != dz) continue;
            // its model corner (x to, y from) on the block's corner
            double px = cx - ux * e[1] + n[0] * e[2], pz = cz - uz * e[1] + n[1] * e[2];
            return fit(kind, px, pz, heading);
        }
        return false;
    }

    /** One big building, its front's middle at {@code (x, z)}. */
    LevelObject landmark(String kind, double x, double z, double heading) {
        double[] e = extents(kind), n = facingOf(heading);
        double ux = n[1], uz = -n[0], cm = (e[0] + e[1]) / 2;
        // (x, z) is the middle of its front: back from it by the front's distance, across by its middle
        double px = x - ux * cm + n[0] * e[2], pz = z - uz * cm + n[1] * e[2];
        Box b = box(kind, px, pz, heading, 0);
        if (!inside(b, 0)) throw new IllegalStateException(kind + " does not fit in the level at " + x + ", " + z);
        for (Box o : solid) {
            if (o.overlaps(b, 0.05)) throw new IllegalStateException(kind + " overlaps " + o.kind() + " at " + x + ", " + z);
        }
        return raise(kind, px, pz, heading);
    }

    // --- the north bank's blocks -----------------------------------------------------------------

    /** The building lines of the columns between the avenues: west, east. */
    static double[][] columns() {
        double[][] c = new double[AVENUES.length + 1][];
        for (int i = 0; i <= AVENUES.length; i++) {
            double w = i == 0 ? -EDGE : AVENUES[i - 1] + ROAD + WALK;
            double e = i == AVENUES.length ? EDGE : AVENUES[i] - ROAD - WALK;
            c[i] = new double[]{w, e};
        }
        return c;
    }

    void landmarks() {
        double[][] c = columns();
        double sb = STRAND + ROAD + WALK, sn = STRAND - ROAD - WALK;          // the Strand's south and north lines
        double hb = HOLBORN + ROAD + WALK, hn = HOLBORN - ROAD - WALK;
        double ob = OXFORD + ROAD + WALK, on = OXFORD - ROAD - WALK;

        // Westminster: the clock tower by its bridge, the hall beside it, the square before them
        landmark("great_clock_tower", -199, 86, 0);
        landmark("guildhall", -226, 86, 0);
        fill(GroundMap.FLAGSTONES, -254, sb, -194, 84);
        green(-244, sb + 6, -204, 76);
        l.level.ground().stroke(GroundMap.FLAGSTONES, -244, sb + 6, -204, 76, 1.4);
        l.level.ground().stroke(GroundMap.FLAGSTONES, -244, 76, -204, sb + 6, 1.4);
        for (int i = 0; i < 3; i++) l.prop("statue", -239 + i * 15, sb + 4, 180);
        l.scatter(10, -224, 66, 16, 0.9, 1.2, "oak_tree", "great_oak");
        for (int i = 0; i < 4; i++) l.prop("bench", -240 + i * 10, 79, 180);
        reserve(-EDGE, sb, c[0][1], northFront(c[0][1]));

        // Brass Square: the fountains, the admiral on his column, the gallery to the north of it
        double sq0 = LANES[2] + LANE + LANE_WALK;
        fill(GroundMap.FLAGSTONES, -254, sq0, -194, sn);
        l.prop("statue", -224, 13, 180).set("width", 1.5).set("depth", 1.5).set("height", 2.6);
        l.prop("fountain", -238, 18, 0);
        l.prop("fountain", -210, 18, 0);
        for (double[] p : new double[][]{{-236, 3}, {-212, 3}, {-236, 26}, {-212, 26}}) l.prop("planter", p[0], p[1], 0);
        for (int i = 0; i < 5; i++) {
            lamp("gas_lamp_double", -250 + i * 13, sq0 + 1.2);
            lamp("gas_lamp_double", -250 + i * 13, sn - 1.2);
        }
        l.keepOpen(-224, 13, 30);
        reserve(-EDGE, sq0, c[0][1], sn);
        l.prop("cab_shelter", -246, 27, 0);
        tally.merge("cab_shelter", 1, Integer::sum);

        // the theatre on the Strand, the department store on Oxford Street, the hotel on the river
        landmark("theatre", -150, sn, 180);
        landmark("department_store", -152, ob, 0);
        landmark("grand_hotel", -26, northFront(-26), 180);
        landmark("constabulary", -165, northFront(-165), 180);

        // the market, its piazza
        fill(GroundMap.FLAGSTONES, c[2][0], sq0, c[2][1], sn);
        LevelObject market = landmark("covered_market", -90, 12.4 - 0.0, 90);
        market.set("name", "Covent Market");
        String[] wares = {"health potion x6, stamina potion x4, empty bottle x20, wild berries x12, sage x8",
                "roast meat x6, grilled fish x6, berry tart x10, hunters stew x4",
                "twine x20, fish hook x20, cloth x12, leather x10, parchment x10, ink x6",
                "scroll of haste x2, scroll of return x4, reshuffle scroll x2, cards x4"};
        String[] stallNames = {"Mrs Pruett's Remedies", "The Hot Pie Barrow", "Haberdashery and Notions",
                "The Conjuror's Barrow"};
        for (int i = 0; i < 4; i++) {
            double x = -104 + i * 10;
            l.stall(stallNames[i], x, i % 2 == 0 ? -0.5 : 25.5, i % 2 == 0 ? 180 : 0, wares[i]);
            furniture.add(Box.rect(x - 2, (i % 2 == 0 ? -0.5 : 25.5) - 2, x + 2, (i % 2 == 0 ? -0.5 : 25.5) + 2, "stall"));
        }
        reserve(c[2][0], sq0, c[2][1], sn);

        // the museum, set back behind its forecourt
        landmark("museum", -25, hn - 7, 180);
        fill(GroundMap.FLAGSTONES, c[3][0], hn - 8, c[3][1], hn);
        for (int i = 0; i < 6; i++) l.prop("iron_railing", -48 + i * 5 + (i > 2 ? 15 : 0), hn - 0.4, 180);
        reserve(c[3][0] + 9, hn - 8, c[3][1] - 9, hn);

        // the cathedral on its close, its churchyard behind
        landmark("cathedral", 45, sn - 9, 180);
        fill(GroundMap.FLAGSTONES, c[4][0], sn - 10, c[4][1], sn);
        l.scatter(14, 45, -42, 22, 0.9, 1.3, "oak_tree", "great_oak", "birch_tree");
        l.prop("statue", 45, sn - 5, 180);
        l.prop("cab_shelter", 26, sn - 4, 180);
        tally.merge("cab_shelter", 1, Integer::sum);
        reserve(c[4][0], hb, c[4][1], sn);

        // the City: the bank on its corner, the post office, the guild's own clock
        landmark("bank", 184, sn, 180);
        landmark("post_office", 100, sn, 180);
        landmark("fire_station", 180, hb, 0);
        landmark("chapel", -90, ob + 9.6, 0);
        reserve(-100, ob, -80, ob + 9.6);
        // the old gate over the Strand
        LevelObject gate = landmark("city_gate", 115 - 6.3, STRAND, 90);
        gate.set("name", "Temple Gate");

        // the station: its hall on Oxford Street, the shed behind, the lines out to the north
        landmark("station_hall", 45, on, 180);
        double hallBack = on - (extents("station_hall")[3] - extents("station_hall")[2]);
        LevelObject shed = landmark("train_shed", 45, hallBack - 50.0 - 0.5, 0);
        double shedZ = shed.z();
        for (double x : new double[]{45 - 12.375, 45, 45 + 12.375}) {
            l.prop("rail_track", x, round(shedZ - 25 - 7.5 - 7.5), 0);
            tally.merge("rail_track", 1, Integer::sum);
        }
        LevelObject engine = l.prop("steam_locomotive", 45 + 12.375, round(shedZ + 2), 0);
        tally.merge("steam_locomotive", 1, Integer::sum);
        solid.add(box("steam_locomotive", engine.x(), engine.z(), 0, 0));
        fill(GroundMap.FLAGSTONES, c[4][0], on - 1, c[4][1], on);
        reserve(c[4][0], -EDGE, c[4][1], on);

        // the railway's yard beside it: lines, a water tower, the engine shed
        fill(GroundMap.GRAVEL, c[5][0], -EDGE, c[5][1], on);
        for (double x : new double[]{96, 104, 112}) {
            for (double z = -EDGE + 7.5; z < on - 12; z += 15) {
                l.prop("rail_track", x, z, 0);
                tally.merge("rail_track", 1, Integer::sum);
            }
            roads.add(Box.rect(x - 2, -EDGE, x + 2, on - 5, "rails"));
        }
        LevelObject goods = l.prop("steam_locomotive", 104, -214, 180);
        tally.merge("steam_locomotive", 1, Integer::sum);
        solid.add(box("steam_locomotive", 104, -214, 180, 0));
        landmark("water_tower", 120, -206, 90);
        landmark("engine_house", 118, -238, 90);
        for (int i = 0; i < 6; i++) l.prop(i % 2 == 0 ? "crate" : "barrel", 121 + (i % 3) * 2.2, -196 - (i / 3) * 2.2, 0);
        reserve(c[5][0], -EDGE, c[5][1], on - 22);

        // the airship field: the mooring mast, its sheds
        green(c[6][0], -EDGE, EDGE, on);
        landmark("airship_tower", 205, -214, 200);
        l.paint(GroundMap.PACKED, 205, -214, 14);
        l.road(GroundMap.PACKED, 2.5, 205, on, 205, -200);
        landmark("warehouse", 238, -232, 270).set("name", "Hangar No. 1");
        landmark("warehouse", 238, -208, 270).set("name", "Hangar No. 2");
        for (int i = 0; i < 8; i++) {
            String k = i % 3 == 0 ? "barrel" : i % 3 == 1 ? "crate" : "grain_sacks";
            l.prop(k, 222 + (i % 4) * 2.4, -186 - (i / 4) * 2.4, i * 37);
        }
        l.prop("boiler", 190, -196, 90);
        l.house("workshop", 170, -245, 0, 1);
        tally.merge("workshop", 1, Integer::sum);
        l.prop("boiler", 182, -238, 0);
        l.prop("tesla_coil", 190, -240, 0);
        l.prop("tesla_coil", 220, -240, 0);
        for (int i = 0; i < 7; i++) l.prop("iron_railing", 162 + i * 5 + 2.5, on - 1, 0);
        for (int i = 0; i < 6; i++) l.prop("iron_railing", 224 + i * 5 + 2.5, on - 1, 0);
        l.scatter(8, 235, -235, 15, 0.9, 1.2, "oak_tree", "birch_tree");
        reserve(c[6][0], -EDGE, EDGE, on);

        // the park: the palm house, the bandstand, the pond, the paths
        park(c[0][0], -EDGE, c[1][1], on);
        reserve(c[0][0], -EDGE, c[1][1], on);
    }

    /** Grass over the rectangle: whatever was painted there taken back off it. */
    void green(double x0, double z0, double x1, double z1) {
        l.level.ground().square(true);
        for (double x = x0 + 1.5; x < x1; x += 2.5) {
            for (double z = z0 + 1.5; z < z1; z += 2.5) {
                l.level.ground().paint(GroundMap.MEADOW, Math.min(x, x1 - 1.5), Math.min(z, z1 - 1.5), 1.5, 1);
            }
        }
        l.level.ground().square(false);
    }

    void park(double x0, double z0, double x1, double z1) {
        green(x0, z0, x1, z1);
        double mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
        l.road(GroundMap.GRAVEL, 2, x0, mz + 6, x1, mz + 6);
        l.road(GroundMap.GRAVEL, 2, mx + 10, z1, mx + 10, z0);
        l.road(GroundMap.GRAVEL, 1.6, x0 + 6, z1, mx - 30, mz - 12, mx - 50, z0);
        landmark("palm_house", mx + 10, z0 + 34, 180);
        landmark("bandstand", mx - 30, mz + 20, 0);
        l.pool(WaterMap.Kind.CLEAR, x1 - 24, mz + 22, 9, 1.2);
        l.herd("duck", 4, x1 - 24, mz + 22, 6);
        l.herd("swan", 2, x1 - 24, mz + 22, 6);
        for (int i = 0; i < 8; i++) l.prop("bench", x0 + 12 + i * 14, mz + 9.2, 180);
        l.prop("statue", mx + 10, mz + 6, 0);
        for (int i = 0; i < 6; i++) lamp("gas_lamp", x0 + 8 + i * 22, mz + 3.6);
        for (int i = 0; i < 4; i++) lamp("gas_lamp", mx + 12.6, z1 - 8 - i * 18);
        l.scatter(70, mx, mz, 70, 0.9, 1.4, "oak_tree", "great_oak", "birch_tree", "autumn_tree", "willow_tree",
                "tall_birch");
        l.scatter(40, mx, mz, 70, 0.8, 1.2, "berry_bush", "wildflowers", "fern");
        for (int i = 0; i < 20; i++) l.prop("iron_railing", x0 + 2.5 + i * 5, z1 - 0.4, 0);
        for (int i = 0; i < 4; i++) l.prop("iron_railing", x0 + 107.5 + i * 5, z1 - 0.4, 0);
    }

    /** Every other block of the north bank, by its district. */
    void districts() {
        double[][] c = columns();
        double[] laneN = new double[LANES.length], laneS = new double[LANES.length];
        for (int i = 0; i < LANES.length; i++) {
            laneN[i] = LANES[i] - LANE - LANE_WALK;
            laneS[i] = LANES[i] + LANE + LANE_WALK;
        }
        double on = OXFORD - ROAD - WALK, ob = OXFORD + ROAD + WALK, hn = HOLBORN - ROAD - WALK,
                hb = HOLBORN + ROAD + WALK, sn = STRAND - ROAD - WALK, sb = STRAND + ROAD + WALK;
        // rows: north line, south line, of each band of blocks between the streets, with a lane between
        double[][] rows = {{-EDGE, laneN[0], laneS[0], on}, {ob, laneN[1], laneS[1], hn}, {hb, laneN[2], laneS[2], sn}};
        for (int col = 0; col < c.length; col++) {
            double x0 = c[col][0], x1 = c[col][1], xm = (x0 + x1) / 2;
            for (int r = 0; r < rows.length; r++) {
                Mix mix = mixAt(col, r);
                double top = rows[r][0], bottom = rows[r][3];
                boolean lane = !laneSkipped(LANES[r], xm);
                // corners first, on the crossings of real streets
                if (r > 0 || true) cornersOf(x0, top, x1, bottom, col, r, lane ? rows[r][1] : Double.NaN,
                        lane ? rows[r][2] : Double.NaN);
                // the main streets' fronts: north side of the band faces north (r > 0), south side faces south
                if (r > 0) frontage(x0, top, x1, top, 0, -1, mix);
                frontage(x0, bottom, x1, bottom, 0, 1, mix);
                if (lane) {
                    frontage(x0, rows[r][1], x1, rows[r][1], 0, 1, LANE_SIDE);
                    frontage(x0, rows[r][2], x1, rows[r][2], 0, -1, LANE_SIDE);
                }
                // the cross streets' fronts
                if (col > 0) frontage(x0, top, x0, bottom, -1, 0, CROSS_STREET);
                if (col < c.length - 1) frontage(x1, top, x1, bottom, 1, 0, CROSS_STREET);
                // and what is left of a deep block: more behind
                yard(x0, top, x1, bottom);
            }
            // the riverside band: the Strand to the north of it, the embankment to the south
            Mix front = col <= 1 ? WEST_END : col >= 5 ? CITY : RIVERFRONT;
            frontage(x0, sb, x1, sb, 0, -1, col <= 2 ? WEST_END : col >= 5 ? CITY : WEST_END);
            curvedFrontage(London::northFront, x0, x1, true, front);
            if (col > 0) frontage(x0, sb, x0, northFront(x0), -1, 0, CROSS_STREET);
            if (col < c.length - 1) frontage(x1, sb, x1, northFront(x1), 1, 0, CROSS_STREET);
            yard(x0, sb, x1, northFront(xm));
        }
    }

    Mix mixAt(int col, int row) {
        if (col >= 6) return row == 0 ? EAST : CITY;
        if (col >= 4) return row == 0 ? EAST : CITY;
        if (col == 3) return row == 2 ? WEST_END : BLOOMSBURY;
        if (col == 2) return row == 0 ? WEST : WEST_END;
        return row == 2 ? WEST_END : WEST;
    }

    void cornersOf(double x0, double top, double x1, double bottom, int col, int row, double laneTop,
                   double laneBottom) {
        int last = AVENUES.length;
        String big = row == 0 || col <= 1 || rnd.nextDouble() < 0.7 ? "corner_mansion" : "corner_shop";
        // (each corner where a street meets a street: the west/east edge of the map has none)
        if (col > 0) {
            if (row > 0) corner(big, x0, top, -1, -1);
            corner(rnd.nextDouble() < 0.3 ? "corner_shop" : big, x0, bottom, -1, 1);
            if (!Double.isNaN(laneTop) && rnd.nextDouble() < 0.3) corner("corner_shop", x0, laneTop, -1, 1);
            if (!Double.isNaN(laneTop) && rnd.nextDouble() < 0.3) corner("corner_shop", x0, laneBottom, -1, -1);
        }
        if (col < last) {
            if (row > 0) corner(big, x1, top, 1, -1);
            corner(rnd.nextDouble() < 0.3 ? "corner_shop" : big, x1, bottom, 1, 1);
            if (!Double.isNaN(laneTop) && rnd.nextDouble() < 0.3) corner("corner_shop", x1, laneTop, 1, 1);
            if (!Double.isNaN(laneTop) && rnd.nextDouble() < 0.3) corner("corner_shop", x1, laneBottom, 1, -1);
        }
    }

    /** What is left inside a block once its fronts are up: a few more back to back, and the yards' clutter. */
    void yard(double x0, double z0, double x1, double z1) {
        String[] small = {"workshop", "row_house", "lodging_house", "cottage", "terrace_house"};
        for (int i = 0; i < 6; i++) {
            String kind = small[rnd.nextInt(small.length)];
            double x = x0 + 6 + rnd.nextDouble() * Math.max(1, x1 - x0 - 12);
            double z = z0 + 6 + rnd.nextDouble() * Math.max(1, z1 - z0 - 12);
            fit(kind, x, z, rnd.nextInt(4) * 90.0);
        }
        String[] clutter = {"crate", "barrel", "hand_cart", "grain_sacks", "barrel", "crate", "potted_tree", "bench"};
        for (int i = 0; i < 10; i++) {
            String kind = clutter[rnd.nextInt(clutter.length)];
            double x = x0 + 2 + rnd.nextDouble() * Math.max(1, x1 - x0 - 4);
            double z = Math.min(z0, z1) + 2 + rnd.nextDouble() * Math.max(1, Math.abs(z1 - z0) - 4);
            Box b = box(kind, x, z, rnd.nextInt(360), 0.4);
            if (!free(b)) continue;
            boolean clear = true;
            for (Box f : furniture) if (f.overlaps(b, 0)) clear = false;
            if (!clear) continue;
            furniture.add(b);
            l.prop(kind, round(x), round(z), rnd.nextInt(360));
        }
    }

    // --- the south bank ----------------------------------------------------------------------------

    void southBank() {
        double sline = SOUTHWARK - 5 - 2.5;
        // the gas works east of the tower bridge, the breweries and foundries west of it
        landmark("gasometer", 196, sline, 180);
        landmark("gasometer", 236, sline, 180);
        landmark("ironworks", 100, sline, 180);
        landmark("brewery", -20, sline, 180);
        landmark("printing_works", -120, sline, 180);
        // the wharf the gang holds, west: crates and barrels in the open, behind a wall
        double wx = -228, wz = southFront(-228) + 10;
        fill(GroundMap.PACKED, -250, wz - 8, -200, wz + 20);
        String[] stacks = {"crate", "crate", "barrel", "grain_sacks", "hand_cart"};
        for (int i = 0; i < 16; i++) {
            double x = -246 + (i % 8) * 6 + rnd.nextDouble(), z = wz - 4 + (i / 8) * 14 + rnd.nextDouble() * 2;
            String k = stacks[rnd.nextInt(stacks.length)];
            l.prop(k, round(x), round(z), rnd.nextInt(360));
            solid.add(box(k, x, z, 0, 0.4));
        }
        solid.add(Box.rect(-252, wz - 9, -198, wz + 21, "wharf"));
        l.fire(-224, wz + 4);
        l.band("bandit", 6, -224, wz + 6, 12, 10);
        l.chest(-247, wz + 14, 90, "coins x120, health potion x2, scroll of fury x1, brass key x1");

        // the fronts: on the river, on Southwark Street, on the cross streets
        double[] xs = {-EDGE, SOUTH_AVENUES[0], SOUTH_AVENUES[1], SOUTH_AVENUES[2], SOUTH_AVENUES[3], EDGE};
        for (int i = 0; i + 1 < xs.length; i++) {
            double x0 = i == 0 ? xs[0] : xs[i] + 7.5, x1 = i == xs.length - 2 ? xs[i + 1] : xs[i + 1] - 7.5;
            curvedFrontage(London::southFront, x0, x1, false, DOCKS);
            frontage(x0, sline, x1, sline, 0, 1, BOROUGH);
            if (i > 0) frontage(x0, sline, x0, southFront(x0), -1, 0, CROSS_STREET);
            if (i < xs.length - 2) frontage(x1, sline, x1, southFront(x1), 1, 0, CROSS_STREET);
            yard(x0, southFront((x0 + x1) / 2), x1, sline);
        }
        // and across Southwark Street, a last row of houses to the edge
        frontage(-EDGE, SOUTHWARK + 7.5, EDGE, SOUTHWARK + 7.5, 0, -1, BOROUGH);
    }

    // --- the embankments -----------------------------------------------------------------------------

    void embankments() {
        for (int side = -1; side <= 1; side += 2) {
            for (double x = -HALF + 2.5; x < HALF; x += 5) {
                double bridge = Math.min(Math.abs(x - WESTMINSTER), Math.min(Math.abs(x - IRON), Math.abs(x - TOWER)));
                double wall = side < 0 ? northWall(x) : southWall(x);
                double slope = ((side < 0 ? northWall(x + 1) : southWall(x + 1)) - wall);
                double len = Math.hypot(1, slope), tx = 1 / len, tz = slope / len;
                // facing the river: the railing's length along the wall, 0.4 m back from its edge
                double nx = side < 0 ? -tz : tz, nz = side < 0 ? tx : -tx;
                // the granite wall: on the edge, down to the river's bed and a parapet over the street
                if (bridge > 7.5) {
                    l.prop("river_wall", round(x), round(wall), round(headingOf(nx, nz)));
                    tally.merge("river_wall", 1, Integer::sum);
                }
                double k = (x + HALF) / 5;
                if (bridge > 10 && Math.abs(k % 4 - 1.5) < 0.01) lamp("gas_lamp_double", x - nx * 1.4, wall - nz * 1.4);
                if (bridge > 12 && Math.abs(k % 4 - 3.5) < 0.01) {
                    l.scenery("oak_tree", round(x - nx * 3.2), round(wall - nz * 3.2), 1.0 + rnd.nextDouble() * 0.3);
                }
                if (bridge > 12 && Math.abs(k % 16 - 7.5) < 0.01) {
                    l.prop("bench", round(x - nx * 2.6), round(wall - nz * 2.6), round(headingOf(nx, nz)));
                }
            }
        }
        // the bridges: on the banks, spanning
        l.prop("iron_bridge", WESTMINSTER, round(riverZ(WESTMINSTER)), 0).set("name", "Westminster Bridge");
        l.prop("iron_bridge", IRON, round(riverZ(IRON)), 0).set("name", "Blackfriars Bridge");
        l.prop("tower_bridge", TOWER, round(riverZ(TOWER)), 0).set("name", "Tower Bridge");
        tally.merge("iron_bridge", 2, Integer::sum);
        tally.merge("tower_bridge", 1, Integer::sum);
        for (double x : new double[]{WESTMINSTER, IRON, TOWER}) {
            double half = x == TOWER ? 45 : 27.5;
            solid.add(Box.rect(x - 7, riverZ(x) - half, x + 7, riverZ(x) + half, "bridge"));
        }
    }

    // --- the streets' furniture ----------------------------------------------------------------------

    void lamp(String kind, double x, double z) {
        double height = kind.equals("gas_lamp_double") ? 4.7 : 4.2;
        l.put(ObjectTypes.LIGHT, round(x), round(z), 0, "colour", "lantern", "radius", kind.equals("gas_lamp_double")
                ? 12.0 : 10.0, "strength", 1.0, "height", height, "fixture", kind);
        furniture.add(Box.rect(x - 0.4, z - 0.4, x + 0.4, z + 0.4, kind));
        l.keepOpen(x, z, 0.8);
        tally.merge(kind, 1, Integer::sum);
    }

    /** Whether anything is in the way of a thing of {@code kind} at {@code (x, z)} (the road it stands by aside). */
    boolean blocked(Box b) {
        if (!inside(b, 1) || wet(b)) return true;
        for (Box o : solid) if (o.overlaps(b, 0)) return true;
        for (Box o : furniture) if (o.overlaps(b, 0)) return true;
        return false;
    }

    /** Whether {@code (x, z)} is on a crossing: on a roadway other than its own. */
    boolean crossing(double x, double z, Box own) {
        Box dot = Box.rect(x - 0.5, z - 0.5, x + 0.5, z + 0.5, "dot");
        for (Box r : roads) {
            if (r == own || r.kind().equals("walk")) continue;
            Box narrowed = new Box(r.cx(), r.cz(), r.ux(), r.uz(), r.nx(), r.nz(), r.hu(), r.hn() + 2, r.kind());
            if (narrowed.overlaps(dot, 0)) return true;
        }
        return false;
    }

    void streetFurniture() {
        String[] kerb = {"pillar_box", "phone_kiosk", "bollard", "newsstand", "advertising_column", "street_clock",
                "police_box", "drinking_fountain", "bench", "planter", "cab_shelter", "potted_tree"};
        double[] often = {3, 2.5, 2, 1.5, 1.5, 1, 0.6, 0.8, 1.5, 1, 0.5, 0.8};
        double total = 0;
        for (double o : often) total += o;
        for (Box r : List.copyOf(roads)) {
            if (!r.kind().equals("road")) continue;
            boolean lane = r.hn() < ROAD + WALK - 0.5;
            if (r.hn() < 3) continue;                                           // (the embankment's own pavements)
            double kerbAt = r.hn() - (lane ? LANE_WALK : r.hn() >= ROAD + WALK - 0.1 ? WALK : 2.5) + 0.6;
            if (r.hn() <= BANK_ROAD + 0.1) continue;
            double spacing = lane ? 30 : 24;
            for (int side = -1; side <= 1; side += 2) {
                double off = side < 0 ? 0 : spacing / 2;
                for (double t = -r.hu() + off; t <= r.hu(); t += spacing / 4) {
                    double x = r.cx() + r.ux() * t + r.nx() * kerbAt * side, z = r.cz() + r.uz() * t + r.nz() * kerbAt * side;
                    if (crossing(x, z, r)) continue;
                    boolean lampHere = Math.abs(((t + r.hu() - off) / spacing) % 1) < 0.01;
                    if (lampHere) {
                        Box b = Box.rect(x - 0.4, z - 0.4, x + 0.4, z + 0.4, "lamp");
                        if (!blocked(b)) lamp(lane ? "gas_lamp" : rnd.nextDouble() < 0.3 ? "gas_lamp_double" : "gas_lamp", x, z);
                        continue;
                    }
                    if (rnd.nextDouble() > (lane ? 0.15 : 0.32)) continue;
                    double pick = rnd.nextDouble() * total;
                    int k = 0;
                    while (k < often.length - 1 && (pick -= often[k]) > 0) k++;
                    String kind = kerb[k];
                    if (lane && (kind.equals("cab_shelter") || kind.equals("advertising_column"))) continue;
                    // along the kerb: facing the pavement (away from the road), long side along it
                    double nx = r.nx() * side, nz = r.nz() * side;
                    double heading = headingOf(-nx, -nz);
                    if (kind.equals("bench") || kind.equals("newsstand") || kind.equals("cab_shelter")) heading = headingOf(nx, nz);
                    double depth = (extents(kind)[3] - extents(kind)[2]) / 2;
                    double px = x + nx * (depth - 0.3), pz = z + nz * (depth - 0.3);
                    Box b = box(kind, px, pz, heading, 0.3);
                    if (blocked(b)) continue;
                    furniture.add(b);
                    l.prop(kind, round(px), round(pz), round(heading));
                    tally.merge(kind, 1, Integer::sum);
                    pavements.add(new double[]{x + nx * 1.6, z + nz * 1.6});
                }
            }
        }
    }

    /** Omnibuses and steam carriages at the kerbs, and going by. */
    void traffic() {
        String[] kinds = {"steam_carriage", "steam_carriage", "steam_omnibus", "hand_cart"};
        for (Box r : List.copyOf(roads)) {
            if (!r.kind().equals("road") || r.hn() < ROAD + WALK - 0.1) continue;
            for (int side = -1; side <= 1; side += 2) {
                for (double t = -r.hu() + 10; t < r.hu() - 10; t += 9) {
                    if (rnd.nextDouble() > 0.11) continue;
                    double off = (ROAD - 1.7) * side;
                    double x = r.cx() + r.ux() * t + r.nx() * off, z = r.cz() + r.uz() * t + r.nz() * off;
                    if (crossing(x, z, r)) continue;
                    String kind = kinds[rnd.nextInt(kinds.length)];
                    // driving on the left: heading along the road, the way the left-hand side goes
                    double dx = r.ux() * -side, dz = r.uz() * -side;
                    double heading = headingOf(dx, dz);
                    Box b = box(kind, x, z, heading, 0.4);
                    if (blocked(b)) continue;
                    furniture.add(b);
                    l.prop(kind, round(x), round(z), round(heading));
                    tally.merge(kind, 1, Integer::sum);
                }
            }
        }
    }

    // --- Londoners -----------------------------------------------------------------------------------

    void folk() {
        double bank = northWall(-170) - RIVERSIDE / 2;
        l.spawn(-170, bank, 90);
        l.wayOut("The road out of London", -EDGE + 4, STRAND, 270, false);

        person("Constable Briggs", HUMAN, "guard", "masculine", -176, bank - 1, 270, 6,
                "Evening. Mind how you go - the fog comes up off the river quick of a night.|Westminster's the clock "
                        + "behind me. The bridges are east: the iron one, then the Tower Bridge at the Pool.")
                .set("weapon", "sword").set("topics", "The Yard=The Constabulary's just along the Embankment. Inspector "
                        + "Hale runs it, when he's not chasing the Wharf gang.; The city=Strand runs east to the City. "
                        + "Oxford Street's north, past Holborn. You'll find the station and the airship field up there.");
        person("Inspector Hale", HUMAN, "elegant", "masculine", -165, northFront(-165) + 4, 180, 2,
                "Inspector Hale. You have the look of someone who can handle themselves.");
        l.quest(-165, northFront(-165) + 4, "The Wharf gang", "Inspector Hale", "kill", "bandit", 6, 50,
                        "A gang has the old wharf on the south bank, west of the bridges. Cargo goes missing, "
                                + "watchmen go in the river. Six of them. Clear them out and the Yard will see you right.",
                        "Over the Westminster bridge and west along the Bankside. Mind the fires.",
                        "Six. The wharf's quiet for the first time in a month. Here - with the Yard's thanks.",
                        "coins x150, greater health potion x2, scroll of warding x1", 220)
                .set(Quest.ID, "london_wharf");
        person("Stationmaster Pell", HUMAN, "town", "masculine", 45, OXFORD + ROAD + WALK - 18.5 + 0.0, 180, 3,
                "The 7:40 to the north leaves on time or I'll know why.|Mind the gap between the platform and the train.");
        person("Ada Lovelock", HUMAN, "elegant", "feminine", 20, -40, 90, 4,
                "Have you seen the dome from the inside? The whispers carry right round the gallery.")
                .set("topics", "The museum=They have an orrery that runs by steam. You must see it.");
        l.quest(20, -40, "A letter for the station", "Ada Lovelock", "talk", "Stationmaster Pell", 1, 0,
                        "Would you take a letter to the Stationmaster at the station on Oxford Street? My brother "
                                + "comes in on the night train and I want a cab waiting.",
                        "The station is north, up the avenue past the museum.",
                        "He has it? You are a dear. Take this for your trouble.", "coins x40, swiftness potion x1", 60)
                .set(Quest.ID, "london_letter");
        person("Mr Tolliver", HUMAN, "town", "masculine", -112, 12, 270, 3,
                "Fresh from the country this morning! Well - yesterday.")
                .set("buys", true).set("stock", "berry tart x12, hunters stew x6, roast meat x6, grilled fish x6, "
                        + "wild berries x10");
        person("Old Nell", HUMAN, "ragged", "feminine", -100, 30, 180, 3,
                "Violets, lovely violets! A penny a bunch, and luck with every one.");
        person("Captain Ferris", HALF_DRURINEAN, "workshop", "masculine", 200, -200, 200, 6,
                "She's the Albion, and she flies at dawn if the wind holds.|Seen the city from up there? The fog "
                        + "lies like a sea, the spires stick out of it like masts.");
        person("Dr Quillon", DRURINEAN, "elegant", "feminine", -38, -95, 180, 4,
                "The museum closes at dusk, but the curators never seem to leave.")
                .set("buys", true).set("stock", "scroll of insight x2, scroll of return x4, parchment x10, ink x6, "
                        + "crystal ball x1");
        person("Wiggins", HUMAN, "ragged", "masculine", -60, STRAND + 7, 90, 10,
                "Paper! Paper! Read all about it - airship spotted over the Pool!");
        person("Bertha Crumb", HUMAN, "common", "feminine", 150, northFront(150) + 3, 180, 5,
                "They raise the road for the tall ships. Stand well back when the bell goes.");
        person("Gaffer Moles", KHORIGALE, "workshop", "masculine", 100, southFront(100) - 3, 0, 5,
                "Ironworks don't sleep. Neither do I, much.")
                .set("buys", true).set("weapon", "mace").set("stock", "iron bar x10, iron wedges x20, mace studs x20, "
                        + "upgrade ingot x3, whetstone x20");
        person("Brother Ambrose", HUMAN, "common", "masculine", 45, STRAND - ROAD - WALK - 12, 180, 3,
                "Peace be with you. The bells ring the hours here, as they have for three hundred years.");
        person("Mrs Haversham", HUMAN, "elegant", "feminine", -152, STRAND - 7, 180, 4,
                "Tonight's performance is sold out, dear - but there is always tomorrow.");
        person("Watchman Dunn", HUMAN, "guard", "masculine", 184, STRAND - 9, 180, 3,
                "Nobody goes near the vaults without a letter from the Governor.").set("weapon", "crossbow");
        // and the crowd, about the pavements, doing as they do all day
        String[] outfits = {"common", "town", "elegant", "workshop", "town", "common", "ragged"};
        String[] people = {HUMAN, HUMAN, HUMAN, HUMAN, DRURINEAN, HALF_DRURINEAN, KHORIGALE};
        String[] says = {"Lovely evening for it, if you like fog.", "Have you a light? No? Never mind.",
                "Mind your pockets on the Strand.", "The omnibus is late again.",
                "They say there's a cure for everything at the apothecary's. Except the fog.",
                "Good evening.", "Can't stop - late for the theatre.", "Steam's the future, mark my words.",
                "My cousin flies on the Albion. Says you can see France.", "Penny for the guy?"};
        String[] names = {"Clerk", "Shopgirl", "Gentleman", "Lady", "Porter", "Navvy", "Seamstress", "Lamplighter",
                "Messenger", "Costermonger", "Governess", "Errand boy", "Cabman", "Typist", "Bootblack"};
        int placed = 0;
        for (int tries = 0; tries < 400 && placed < 46; tries++) {
            double[] p = pavements.get(rnd.nextInt(pavements.size()));
            Box b = Box.rect(p[0] - 0.6, p[1] - 0.6, p[0] + 0.6, p[1] + 0.6, "person");
            if (blocked(b)) continue;
            furniture.add(b);
            String outfit = outfits[rnd.nextInt(outfits.length)];
            person(names[rnd.nextInt(names.length)], people[rnd.nextInt(people.length)], outfit,
                    rnd.nextBoolean() ? "feminine" : "masculine", p[0], p[1], rnd.nextInt(8) * 45.0,
                    3 + rnd.nextInt(8), says[rnd.nextInt(says.length)]);
            placed++;
        }
        l.critters("firefly", 10, -170, -205, 40);
    }

    LevelObject person(String name, String people, String outfit, String body, double x, double z, double heading,
                       double roam, String greeting) {
        return l.person(name, people, outfit, round(x), round(z), heading, greeting).set("body", body)
                .set("roam", roam);
    }

    // --- the plan ----------------------------------------------------------------------------------

    /** The plan from above, a pixel half a metre: the ground, the water, what stands, the folk. */
    void plan(Path out) throws IOException {
        int px = SIZE * 2;
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        Map<Integer, Integer> colours = new LinkedHashMap<>();
        colours.put(GroundMap.PACKED, 0x8a7a62);
        colours.put(GroundMap.FLAGSTONES, 0xb8b2a6);
        colours.put(GroundMap.COBBLES, 0x6a6660);
        colours.put(GroundMap.GRAVEL, 0x9a9488);
        colours.put(GroundMap.MEADOW, 0x5e8a46);
        colours.put(GroundMap.MUD, 0x4a3a2a);
        for (int j = 0; j < px; j++) {
            for (int i = 0; i < px; i++) {
                double x = -HALF + (i + 0.5) / 2, z = -HALF + (j + 0.5) / 2;
                int m = l.level.ground().material(x, z);
                int rgb = colours.getOrDefault(m, 0x6a9a4a);
                if (l.level.water().depth(x, z, l.level.heights().height(x, z)) > 0.05) rgb = 0x3a6a8a;
                img.setRGB(i, j, rgb);
            }
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(px / 2.0, px / 2.0);
        g.scale(2, 2);
        g.setStroke(new BasicStroke(0.4f));
        for (LevelObject o : l.level.objects()) {
            String kind = o.str("kind", "");
            if (!o.type().equals(ObjectTypes.BUILDING) || catalog.profile(kind) == null) continue;
            Box b = box(kind, o.x(), o.z(), Math.toDegrees(o.yaw()), 0);
            BuildingProfile p = profile(kind);
            double[][] c = b.corners();
            Path2D poly = new Path2D.Double();
            poly.moveTo(c[0][0], c[0][1]);
            for (int k = 1; k < 4; k++) poly.lineTo(c[k][0], c[k][1]);
            poly.closePath();
            float shade = (float) Math.min(1, p.height() / 40);
            g.setColor(p.city() ? new Color(0.75f - shade * 0.4f, 0.45f - shade * 0.2f, 0.35f - shade * 0.15f)
                    : new Color(0.55f, 0.5f, 0.45f));
            g.fill(poly);
            g.setColor(Color.BLACK);
            g.draw(poly);
            // its front: a short tick out of the middle of it
            double[] n = facingOf(Math.toDegrees(o.yaw()));
            double fx = b.cx() + b.nx() * b.hn(), fz = b.cz() + b.nz() * b.hn();
            g.setColor(Color.YELLOW);
            g.draw(new java.awt.geom.Line2D.Double(fx, fz, fx + n[0] * 1.5, fz + n[1] * 1.5));
        }
        for (LevelObject o : l.level.objects()) {
            Color c = switch (o.type()) {
                case "person" -> Color.RED;
                case "foe" -> Color.MAGENTA;
                case ObjectTypes.LIGHT -> Color.ORANGE;
                case ObjectTypes.SCENERY -> new Color(0x2a5a1a);
                case "spawn" -> Color.CYAN;
                default -> null;
            };
            if (c == null) continue;
            g.setColor(c);
            double r = o.type().equals(ObjectTypes.SCENERY) ? 1.6 : 0.9;
            g.fill(new java.awt.geom.Ellipse2D.Double(o.x() - r, o.z() - r, r * 2, r * 2));
        }
        g.dispose();
        Files.createDirectories(out.toAbsolutePath().getParent());
        ImageIO.write(img, "png", out.toFile());
        System.out.println("plan: " + out);
    }
}
