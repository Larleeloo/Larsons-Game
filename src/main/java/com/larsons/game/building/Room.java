package com.larsons.game.building;

import com.larsons.game.math.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the rooms inside a building are built of: a length of wall (plain,
 * or with a doorway, a window or an archway in it), a floor, a staircase up
 * or a stairwell down, and the door back outside. Each is a {@link
 * Building} of its own in the floor's level - placed, turned, coloured and
 * patterned part by part as any other (a wall's two sides each their own
 * colour, as in the Sims) - but drawn as it is, box by box ({@code
 * RoomRenderer}), not from views: a wall is as long and as high as it is
 * set to be.
 *
 * <p>Measured as a building is: metres, its front towards its own -y, its
 * length along its own x. A wall between the eye and her is drawn cut down
 * to a stub ({@link #cut}), so the room she is in is seen into.
 */
public final class Room {

    /** What a piece is. */
    public enum Piece { WALL, FLOOR, STAIRS, EXIT }

    /** What is in a length of wall. */
    public enum Opening {
        NONE("none", "Plain"), DOOR("door", "Doorway"), WINDOW("window", "Window"), ARCH("arch", "Archway");

        private final String key, label;

        Opening(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() { return key; }

        public String label() { return label; }

        /** The opening written {@code key}; none for anything else. */
        public static Opening byKey(String key) {
            for (Opening o : values()) if (o.key.equals(key)) return o;
            return NONE;
        }
    }

    /**
     * A piece's shape: what it is; how long (its own x), deep (its own y)
     * and high it is; the opening in a wall; whether a staircase goes up
     * (else it is a stairwell going down).
     */
    public record Spec(Piece piece, double length, double depth, double height, Opening opening, boolean up) {}

    /**
     * A box of a piece, in its own metres (x across, y towards its back, z
     * up): the part its ends and top are, the part its front (-y) and back
     * (+y) faces are, and how much darker than its part it is drawn (a
     * stairwell's steps, going down into the dark).
     */
    public record Box(double x0, double x1, double y0, double y1, double z0, double z1, int part, int front, int back,
                      int dark) {
        Box(double x0, double x1, double y0, double y1, double z0, double z1, int part) {
            this(x0, x1, y0, y1, z0, z1, part, part, part, 0);
        }
    }

    /** How thick a wall is, metres. */
    public static final double THICK = 0.2;
    /** How high a wall cut away stands, metres. */
    public static final double CUT = 0.4;
    /** How high a floor is above the one below: a storey, metres - and how high a wall stands unless set. */
    public static final double STOREY = 3.0;
    /** The openings: a doorway's width and height, an archway's, a window's width, sill and top. */
    static final double DOOR_W = 1.2, DOOR_H = 2.2, ARCH_W = 1.8, ARCH_H = 2.55, WINDOW_W = 1.2, SILL = 0.9,
            WINDOW_TOP = 2.1;
    /** A staircase's width and its run; the steps in it. */
    public static final double STAIR_W = 1.3, STAIR_D = 3.0;
    static final int STEPS = 12, WELL_STEPS = 6;
    /** The door out: its width, height and depth. */
    public static final double EXIT_W = 1.6, EXIT_H = 2.5, EXIT_D = 0.3;
    /** Metres a texel of its patterns spans. */
    public static final double TEXEL = 0.025;

    private static final Map<Spec, BuildingProfile> PROFILES = new ConcurrentHashMap<>();

    private Room() {}

    /** A length of wall {@code length} long, {@code height} high, with {@code opening} in its middle. */
    public static Spec wall(double length, double height, Opening opening) {
        return new Spec(Piece.WALL, Math.max(0.3, length), THICK, Math.max(1, height),
                opening == null ? Opening.NONE : opening, false);
    }

    /** A floor {@code width} across and {@code depth} deep. */
    public static Spec floor(double width, double depth) {
        return new Spec(Piece.FLOOR, Math.max(0.5, width), Math.max(0.5, depth), 0.06, Opening.NONE, false);
    }

    /** A staircase up to the floor above, or ({@code up} false) a stairwell down to the one below. */
    public static Spec stairs(boolean up) {
        return new Spec(Piece.STAIRS, STAIR_W, STAIR_D, up ? STOREY : 1.0, Opening.NONE, up);
    }

    /** The door back outside. */
    public static Spec exit() {
        return new Spec(Piece.EXIT, EXIT_W, EXIT_D, EXIT_H, Opening.NONE, false);
    }

    /** {@code spec} as a kind of building: its parts, what of it is solid, where she stands to use it. */
    public static BuildingProfile profile(Spec spec) {
        return PROFILES.computeIfAbsent(spec, Room::make);
    }

    private static BuildingProfile make(Spec s) {
        List<BuildingProfile.Part> parts = new ArrayList<>();
        List<BuildingProfile.Shape> solid = new ArrayList<>();
        double[] door = null;
        String key, label;
        double l = s.length(), d = s.depth();
        switch (s.piece()) {
            case WALL -> {
                key = "wall";
                label = s.opening() == Opening.NONE ? "Wall" : "Wall: " + s.opening().label().toLowerCase();
                parts.add(new BuildingProfile.Part("front", "Front side", 0xD8C8A0, "plaster", false));
                parts.add(new BuildingProfile.Part("back", "Back side", 0xD8C8A0, "plaster", false));
                parts.add(new BuildingProfile.Part("trim", "Trim", 0x6A4A30, "timber", false));
                if (s.opening() == Opening.WINDOW) {
                    parts.add(new BuildingProfile.Part("glass", "Glass", 0x8AA8C0, "panes", false));
                }
                double w = openingWidth(s);
                if (w > 0) {
                    double a = (l - w) / 2;
                    if (a > 0.01) {
                        solid.add(new BuildingProfile.Shape(false, -l / 2 + a / 2, 0, a, d));
                        solid.add(new BuildingProfile.Shape(false, l / 2 - a / 2, 0, a, d));
                    }
                } else {
                    solid.add(new BuildingProfile.Shape(false, 0, 0, l, d));
                }
            }
            case FLOOR -> {
                key = "floor";
                label = "Floor";
                parts.add(new BuildingProfile.Part("floor", "Floor", 0x8A6038, "floorboards", false));
            }
            case STAIRS -> {
                key = s.up() ? "stairs_up" : "stairs_down";
                label = s.up() ? "Stairs up" : "Stairs down";
                parts.add(new BuildingProfile.Part("steps", "Steps", 0x9A6A40, "floorboards", false));
                parts.add(new BuildingProfile.Part("sides", "Sides", 0x5A3A22, "timber", false));
                if (s.up()) {
                    solid.add(new BuildingProfile.Shape(false, 0, 0, l, d));
                    door = new double[]{0, -d / 2 - 0.55};
                } else {
                    // its rails, on three sides: in from the front
                    solid.add(new BuildingProfile.Shape(false, -l / 2 - 0.04, 0, 0.08, d));
                    solid.add(new BuildingProfile.Shape(false, l / 2 + 0.04, 0, 0.08, d));
                    solid.add(new BuildingProfile.Shape(false, 0, d / 2 + 0.04, l + 0.16, 0.08));
                    door = new double[]{0, -d / 2 + 0.45};
                }
            }
            default -> {
                key = "exit";
                label = "Door out";
                parts.add(new BuildingProfile.Part("door", "Door", 0x7A5030, "boards", false));
                parts.add(new BuildingProfile.Part("frame", "Frame", 0x8A867E, "stone_blocks", false));
                parts.add(new BuildingProfile.Part("mat", "Mat", 0xA8763A, "woven", false));
                solid.add(new BuildingProfile.Shape(false, 0, 0, l, d));
                door = new double[]{0, -d / 2 - 0.55};
            }
        }
        return new BuildingProfile(key, label, BuildingProfile.ROOM, s, TEXEL, TEXEL, 0, 1,
                new EnumMap<>(com.larsons.game.sprite.Elevation.class), parts, solid, door, false, null);
    }

    /** How wide the gap in a wall is (0: none - a window is glazed). */
    static double openingWidth(Spec s) {
        return switch (s.opening()) {
            case DOOR -> Math.min(DOOR_W, s.length() - 0.2);
            case ARCH -> Math.min(ARCH_W, s.length() - 0.2);
            default -> 0;
        };
    }

    /** Whether a piece is cut down when it stands between the eye and her (a wall, the door out, stairs up). */
    public static boolean cuts(Spec s) {
        return s.piece() == Piece.WALL || s.piece() == Piece.EXIT || s.piece() == Piece.STAIRS && s.up();
    }

    /**
     * Whether {@code b}, a room's piece, is drawn cut down to a stub: it is
     * nearer the eye than {@code focus} (her, or what the editor looks at)
     * - so of the room she is in, the walls on the eye's side are down and
     * the far ones stand - or it stands across the line from the eye to her.
     */
    public static boolean cut(Building b, Vec3 eye, Vec3 focus) {
        Spec s = b.profile().room();
        if (s == null || !cuts(s)) return false;
        double fx = focus.x() - eye.x(), fz = focus.z() - eye.z(), fl = Math.hypot(fx, fz);
        double hl = s.length() / 2, hd = s.depth() / 2;
        double[] m = b.toModel(focus.x(), focus.z());
        if (fl > 1e-6) {
            fx /= fl;
            fz /= fl;
            // the nearest of it to her: is it on the eye's side of her?
            Vec3 c = b.toWorld(Math.max(-hl, Math.min(hl, m[0])), Math.max(-hd, Math.min(hd, m[1])));
            double ahead = (c.x() - focus.x()) * fx + (c.z() - focus.z()) * fz;
            if (ahead < -0.2) return true;
        }
        // across the line from the eye to her
        double[] e = b.toModel(eye.x(), eye.z());
        double dx = m[0] - e[0], dy = m[1] - e[1], t0 = 0, t1 = 0.97;
        double[] org = {e[0], e[1]}, dir = {dx, dy}, lo = {-hl, -hd}, hi = {hl, hd};
        for (int k = 0; k < 2; k++) {
            if (Math.abs(dir[k]) < 1e-9) {
                if (org[k] < lo[k] || org[k] > hi[k]) return false;
            } else {
                double ta = (lo[k] - org[k]) / dir[k], tb = (hi[k] - org[k]) / dir[k];
                t0 = Math.max(t0, Math.min(ta, tb));
                t1 = Math.min(t1, Math.max(ta, tb));
            }
        }
        return t0 <= t1;
    }

    /** {@code s}'s boxes, in its own metres - cut down to a stub ({@link #CUT} high) if {@code cut}. */
    public static List<Box> boxes(Spec s, boolean cut) {
        List<Box> out = new ArrayList<>();
        double l = s.length(), d = s.depth(), h = s.height();
        switch (s.piece()) {
            case WALL -> wall(s, out);
            case FLOOR -> out.add(new Box(-l / 2, l / 2, -d / 2, d / 2, -0.1, 0, 0));
            case STAIRS -> {
                if (s.up()) {
                    double run = d / STEPS, rise = h / STEPS, side = 0.07;
                    for (int i = 0; i < STEPS; i++) {
                        double y0 = -d / 2 + i * run, top = (i + 1) * rise;
                        out.add(new Box(-l / 2 + side, l / 2 - side, y0, y0 + run, 0, top, 0));
                        out.add(new Box(-l / 2, -l / 2 + side, y0, y0 + run, 0, top + 0.14, 1));
                        out.add(new Box(l / 2 - side, l / 2, y0, y0 + run, 0, top + 0.14, 1));
                    }
                } else {
                    // the steps going down into the dark, and a rail round three sides
                    double run = d / WELL_STEPS;
                    for (int i = 0; i < WELL_STEPS; i++) {
                        double y0 = -d / 2 + i * run;
                        out.add(new Box(-l / 2, l / 2, y0, y0 + run, -0.1, 0.012, 0, 0, 0, 1 + i));
                    }
                    double r = 0.08, rh = 0.95;
                    out.add(new Box(-l / 2 - r, -l / 2, -d / 2, d / 2, 0, rh, 1));
                    out.add(new Box(l / 2, l / 2 + r, -d / 2, d / 2, 0, rh, 1));
                    out.add(new Box(-l / 2 - r, l / 2 + r, d / 2, d / 2 + r, 0, rh, 1));
                }
            }
            case EXIT -> {
                double hw = l / 2, hd = d / 2, post = 0.2;
                out.add(new Box(-hw, -hw + post, -hd, hd, 0, h, 1));
                out.add(new Box(hw - post, hw, -hd, hd, 0, h, 1));
                out.add(new Box(-hw + post, hw - post, -hd, hd, h - 0.25, h, 1));
                out.add(new Box(-hw + post, hw - post, -0.05, 0.05, 0, h - 0.25, 0));
                out.add(new Box(hw - post - 0.24, hw - post - 0.14, -0.1, -0.05, 1.0, 1.1, 0, 0, 0, 2));
                out.add(new Box(-0.6, 0.6, -hd - 0.8, -hd - 0.05, 0, 0.02, 2));
            }
        }
        if (!cut) return out;
        List<Box> stub = new ArrayList<>();
        for (Box b : out) {
            if (b.z0() >= CUT) continue;
            stub.add(new Box(b.x0(), b.x1(), b.y0(), b.y1(), b.z0(), Math.min(b.z1(), CUT), b.part(), b.front(),
                    b.back(), b.dark()));
        }
        return stub;
    }

    /** A wall's boxes: the wall either side of its opening and over it, its glass, its skirting. */
    private static void wall(Spec s, List<Box> out) {
        double l = s.length(), h = s.height(), t = THICK / 2;
        int front = 0, back = 1, trim = 2, glass = 3;
        List<double[]> skirting = new ArrayList<>();
        switch (s.opening()) {
            case DOOR, ARCH -> {
                double w = openingWidth(s), top = Math.min(s.opening() == Opening.DOOR ? DOOR_H : ARCH_H, h - 0.1);
                out.add(new Box(-l / 2, -w / 2, -t, t, 0, h, trim, front, back, 0));
                out.add(new Box(w / 2, l / 2, -t, t, 0, h, trim, front, back, 0));
                out.add(new Box(-w / 2, w / 2, -t, t, top, h, trim, front, back, 0));
                skirting.add(new double[]{-l / 2, -w / 2});
                skirting.add(new double[]{w / 2, l / 2});
            }
            case WINDOW -> {
                double w = Math.min(WINDOW_W, l - 0.2), sill = Math.min(SILL, h - 0.6), top = Math.min(WINDOW_TOP, h - 0.15);
                double mid = (sill + top) / 2;
                out.add(new Box(-l / 2, -w / 2, -t, t, 0, h, trim, front, back, 0));
                out.add(new Box(w / 2, l / 2, -t, t, 0, h, trim, front, back, 0));
                out.add(new Box(-w / 2, w / 2, -t, t, 0, sill, trim, front, back, 0));
                out.add(new Box(-w / 2, w / 2, -t, t, top, h, trim, front, back, 0));
                out.add(new Box(-w / 2, w / 2, -0.02, 0.02, sill, top, glass));
                out.add(new Box(-0.03, 0.03, -0.045, 0.045, sill, top, trim));
                out.add(new Box(-w / 2, w / 2, -0.045, 0.045, mid - 0.03, mid + 0.03, trim));
                out.add(new Box(-w / 2 - 0.06, w / 2 + 0.06, -t - 0.07, t + 0.07, sill - 0.05, sill, trim));
                skirting.add(new double[]{-l / 2, l / 2});
            }
            default -> {
                out.add(new Box(-l / 2, l / 2, -t, t, 0, h, trim, front, back, 0));
                skirting.add(new double[]{-l / 2, l / 2});
            }
        }
        for (double[] k : skirting) {
            if (k[1] - k[0] < 0.02) continue;
            out.add(new Box(k[0], k[1], -t - 0.03, -t, 0, 0.14, trim));
            out.add(new Box(k[0], k[1], t, t + 0.03, 0, 0.14, trim));
        }
    }
}
