package com.larsons.game.actor;

import com.larsons.game.ambient.Animals;
import com.larsons.game.world.Terrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * How someone on foot gets from one place to another round what is in the
 * way - a wall, a house, a rock, a cliff - and what they can see.
 *
 * <ul>
 *   <li><b>Where a foot can go</b> ({@link #passes}): ground no steeper than
 *       she can walk up ({@link Terrain#STEEP}), a little more going down (a
 *       hop off a low ledge), not deep water, not in anything solid. Nobody
 *       walks up a cliff or a wall.</li>
 *   <li><b>The way round</b> ({@link #find}): an A* search over half-metre
 *       cells between them, the cells looked at only as it comes to them, at
 *       most {@value #MOST_CELLS} - a way round a house, along a wall to its
 *       gap, round a lake. The way found is pulled straight wherever the
 *       straight line is clear, so it walks like someone who knows the way,
 *       not along the grid. No way at all: the way to the nearest it can get.</li>
 *   <li><b>What it sees</b> ({@link #sees}): the line from its eyes to hers,
 *       clear of the ground (a hill between) and of every building taller
 *       than the line where it crosses (a wall, a house): nobody sees through
 *       a wall.</li>
 * </ul>
 *
 * Pure: everything about the place comes in through {@link Animals.Place}.
 */
public final class Paths {

    private Paths() {}

    private static final double SQRT2 = Math.sqrt(2);

    /** The cells' size, metres. */
    public static final double CELL = 0.5;
    /** The steepest ground walked up, rise over run (as hers); down, a little steeper (a hop off a low ledge). */
    public static final double UP = Terrain.STEEP * 1.05, DOWN = 2.4;
    /** The highest it steps up anyway (a doorstep, a kerb) and hops down, metres. */
    public static final double STEP = 0.12, HOP = 0.6;
    /** The most cells a search looks at before it settles for the nearest it got. */
    public static final int MOST_CELLS = 5000;
    /** How far past the two ends a search may wander to get round something, metres. */
    public static final double ROOM = 18;
    /** How deep water it wades into, metres. */
    public static final double DEEP = 0.9;

    /** Whether one of {@code radius} can stand at {@code (x, z)}: in bounds, clear of anything solid, not in deep water. */
    public static boolean stands(double x, double z, double radius, Animals.Place place) {
        double[] b = place.bounds();
        if (b != null && (x < b[0] + 0.5 || x > b[1] - 0.5 || z < b[2] + 0.5 || z > b[3] - 0.5)) return false;
        if (place.blocked(x, z, radius)) return false;
        double g = place.ground(x, z);
        return place.water(x, z, g) < DEEP;
    }

    /** Whether the ground from height {@code fromY} to {@code toY} over {@code run} metres is walked: not a cliff. */
    public static boolean walkable(double fromY, double toY, double run) {
        double rise = toY - fromY;
        if (rise > 0) return rise <= STEP + run * UP;
        return -rise <= HOP + run * DOWN;
    }

    /** Whether one of {@code radius} at {@code (ax, az)}, standing at {@code ay}, can step to {@code (bx, bz)}. */
    public static boolean passes(double ax, double az, double ay, double bx, double bz, double radius,
                                 Animals.Place place) {
        double run = Math.hypot(bx - ax, bz - az);
        if (!walkable(ay, place.ground(bx, bz), run)) return false;
        return stands(bx, bz, radius, place);
    }

    /**
     * Whether one standing at {@code (x, z)}, {@code y} high, takes a step
     * to {@code (nx, nz)} on its way {@code left} metres further on: room to
     * stand there, and the ground ahead - judged over a cell's length, or
     * what is left of the way if less - no cliff ({@link #walkable}). How a
     * foot goes; {@link #clear} judges a way by it, so a way found is a way
     * walked.
     */
    public static boolean steps(double x, double z, double y, double nx, double nz, double left, double radius,
                                Animals.Place place) {
        if (!stands(nx, nz, radius, place)) return false;
        double dx = nx - x, dz = nz - z, d = Math.hypot(dx, dz);
        if (d < 1e-6) return true;
        double look = Math.max(0.15, Math.min(CELL, left));
        double ax = x + dx / d * look, az = z + dz / d * look;
        return walkable(y, place.ground(ax, az), look) && walkable(y, place.ground(nx, nz), Math.max(d, 0.05));
    }

    /** Whether the straight way from {@code (ax, az)} to {@code (bx, bz)} is walked all along (a step every 0.25 m). */
    public static boolean clear(double ax, double az, double bx, double bz, double radius, Animals.Place place) {
        double d = Math.hypot(bx - ax, bz - az);
        int n = Math.max(1, (int) Math.ceil(d / 0.25));
        double px = ax, pz = az, py = place.ground(ax, az);
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n, x = ax + (bx - ax) * t, z = az + (bz - az) * t;
            if (!steps(px, pz, py, x, z, d * (1 - (double) (i - 1) / n), radius, place)) return false;
            px = x;
            pz = z;
            py = place.ground(x, z);
        }
        return true;
    }

    /**
     * Whether eyes at {@code (ax, ay, az)} see {@code (bx, by, bz)}: no
     * ground and no building between (looked at every half metre).
     */
    public static boolean sees(double ax, double ay, double az, double bx, double by, double bz, Animals.Place place) {
        double d = Math.hypot(bx - ax, bz - az);
        int n = (int) Math.ceil(d / 0.5);
        for (int i = 1; i < n; i++) {
            double t = (double) i / n, x = ax + (bx - ax) * t, z = az + (bz - az) * t, y = ay + (by - ay) * t;
            if (place.ground(x, z) > y + 0.05) return false;
            if (place.sightBlocked(x, z, y)) return false;
        }
        return true;
    }

    /**
     * The way from {@code (ax, az)} to {@code (bx, bz)} for one of {@code
     * radius}: the points to walk to in turn ({x0, z0, x1, z1, ...}, the last
     * the end - or, when the end cannot be reached, the nearest to it that
     * can), or null when it cannot get anywhere nearer.
     */
    public static double[] find(double ax, double az, double bx, double bz, double radius, Animals.Place place) {
        double x0 = Math.min(ax, bx) - ROOM, x1 = Math.max(ax, bx) + ROOM;
        double z0 = Math.min(az, bz) - ROOM, z1 = Math.max(az, bz) + ROOM;
        int w = (int) Math.ceil((x1 - x0) / CELL) + 1, h = (int) Math.ceil((z1 - z0) / CELL) + 1;
        if ((long) w * h > 400_000) return null;                    // (too far to find a way on foot at once)
        int n = w * h;
        // what each cell is: 0 not looked at, 1 stood on, 2 not
        byte[] can = new byte[n];
        float[] cost = new float[n];
        Arrays.fill(cost, Float.MAX_VALUE);
        int[] from = new int[n];
        boolean[] done = new boolean[n];
        int start = cell(ax, az, x0, z0, w, h), goal = cell(bx, bz, x0, z0, w, h);
        if (start < 0 || goal < 0) return null;
        can[start] = 1;
        cost[start] = 0;
        from[start] = -1;
        PriorityQueue<double[]> open = new PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
        open.add(new double[]{guess(start, goal, w), start});
        int best = start, looked = 0;
        double bestLeft = guess(start, goal, w);
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        while (!open.isEmpty() && looked < MOST_CELLS) {
            int c = (int) open.poll()[1];
            if (done[c]) continue;
            done[c] = true;
            looked++;
            double left = guess(c, goal, w);
            if (left < bestLeft) {
                bestLeft = left;
                best = c;
            }
            if (c == goal) break;
            int cx = c % w, cz = c / w;
            double fx = x0 + cx * CELL, fz = z0 + cz * CELL, fy = place.ground(fx, fz);
            for (int[] s : steps) {
                int nx = cx + s[0], nz = cz + s[1];
                if (nx < 0 || nz < 0 || nx >= w || nz >= h) continue;
                int m = nz * w + nx;
                if (done[m]) continue;
                double tx = x0 + nx * CELL, tz = z0 + nz * CELL;
                if (can[m] == 0) can[m] = (byte) (stands(tx, tz, radius, place) ? 1 : 2);
                if (can[m] == 2) continue;
                boolean diagonal = s[0] != 0 && s[1] != 0;
                if (diagonal) {
                    // (no cutting a corner: both cells beside the diagonal open too)
                    int a = cz * w + nx, b = nz * w + cx;
                    if (can[a] == 0) can[a] = (byte) (stands(x0 + nx * CELL, z0 + cz * CELL, radius, place) ? 1 : 2);
                    if (can[b] == 0) can[b] = (byte) (stands(x0 + cx * CELL, z0 + nz * CELL, radius, place) ? 1 : 2);
                    if (can[a] == 2 || can[b] == 2) continue;
                }
                double run = diagonal ? CELL * SQRT2 : CELL;
                double ty = place.ground(tx, tz);
                // (judged over a cell's length either way, as a foot judges it a step at a time - a cliff's edge
                // is no easier crossed on the slant - and with room to spare, for a foot a little off the line)
                if (!walkable(fy, ty, CELL * 0.8)) continue;
                // (uphill costs a little more: round a hill rather than over it, when it is no longer)
                float c2 = (float) (cost[c] + run + Math.max(0, ty - fy) * 0.6);
                if (c2 < cost[m]) {
                    cost[m] = c2;
                    from[m] = c;
                    open.add(new double[]{c2 + guess(m, goal, w), m});
                }
            }
        }
        if (best == start) return null;
        // back from the nearest it got, then pulled straight where the straight way is clear
        List<double[]> way = new ArrayList<>();
        for (int c = best; c != -1; c = from[c]) way.add(new double[]{x0 + (c % w) * CELL, z0 + (c / w) * CELL});
        java.util.Collections.reverse(way);
        if (best == goal) way.set(way.size() - 1, new double[]{bx, bz});
        List<double[]> pulled = new ArrayList<>();
        double px = ax, pz = az;
        int i = 1;
        while (i < way.size()) {
            int far = i;
            for (int j = Math.min(way.size() - 1, i + 40); j > i; j--) {
                if (clear(px, pz, way.get(j)[0], way.get(j)[1], radius, place)) {
                    far = j;
                    break;
                }
            }
            pulled.add(way.get(far));
            px = way.get(far)[0];
            pz = way.get(far)[1];
            i = far + 1;
        }
        if (pulled.isEmpty()) return null;
        double[] out = new double[pulled.size() * 2];
        for (int k = 0; k < pulled.size(); k++) {
            out[k * 2] = pulled.get(k)[0];
            out[k * 2 + 1] = pulled.get(k)[1];
        }
        return out;
    }

    private static int cell(double x, double z, double x0, double z0, int w, int h) {
        int cx = (int) Math.round((x - x0) / CELL), cz = (int) Math.round((z - z0) / CELL);
        return cx < 0 || cz < 0 || cx >= w || cz >= h ? -1 : cz * w + cx;
    }

    /** How far from {@code c} to {@code goal} at the least (the eight ways' distance), metres. */
    private static double guess(int c, int goal, int w) {
        int dx = Math.abs(c % w - goal % w), dz = Math.abs(c / w - goal / w);
        return CELL * (Math.max(dx, dz) + (SQRT2 - 1) * Math.min(dx, dz));
    }
}
