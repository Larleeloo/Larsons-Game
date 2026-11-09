package com.larsons.game.level;

import com.larsons.game.ambient.AnimalKind;
import com.larsons.game.ambient.Critters;
import com.larsons.game.ambient.Life;
import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.building.BuildingProfile;
import com.larsons.game.building.Room;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The inside of each kind of building as it comes - its floors, the rooms
 * on each, furnished - until it is changed in Creative Mode (Edit inside):
 * a cottage is one room with a bed by the fire, a house has its kitchen
 * downstairs and its bedrooms up, a keep its great hall, its chambers and
 * its armoury.
 *
 * <p>Laid out as every inside is: its rooms round the middle of the floor,
 * the door out in the middle of their front (+z, the south), so she comes
 * in facing north with the front wall behind her, cut away.
 */
public final class Interiors {

    private Interiors() {}

    /** What floor {@code n} is called (0: the ground floor). */
    public static String floorName(int n) {
        return switch (n) {
            case 0 -> "Ground floor";
            case 1 -> "Second floor";
            case 2 -> "Third floor";
            case 3 -> "Fourth floor";
            default -> "Floor " + (n + 1);
        };
    }

    /** A new floor, empty: the {@code n}th. */
    public static Level emptyFloor(int n) {
        return new Level(floorName(n), Level.INTERIOR_SIZE);
    }

    /** Nothing comes by inside: no crickets, no rabbits - only what is put there. */
    public static Life life() {
        Life l = new Life();
        for (com.larsons.game.ambient.CritterKind k : com.larsons.game.ambient.CritterKind.values()) l.setDensity(k, 0);
        for (AnimalKind k : AnimalKind.values()) l.setDensity(k, 0);
        return l;
    }

    // --- a room made bigger or smaller -------------------------------------------------------

    /** The smallest a room's floor can be, metres a side. */
    public static final double MIN_ROOM = 2;
    /** How near its wall a thing's edge is to move with the wall, metres. */
    static final double AGAINST = 0.5;

    /**
     * Floor {@code floor} (on {@code level}) made {@code width} across and
     * {@code depth} deep, as in the Sims: the walls round it go out (or in)
     * with its edges, and stretch along them - their doorways and windows
     * kept their own size, the door out kept its own gap - its inner walls
     * and what stands in it spread out, what stands against a wall moved with
     * that wall. What stands off the floor is left where it is.
     */
    public static void resize(Level level, LevelObject floor, double width, double depth) {
        Room.Spec spec = ObjectTypes.roomSpec(floor);
        if (spec == null || spec.piece() != Room.Piece.FLOOR) return;
        double most = level.size() - 2;
        width = Math.max(MIN_ROOM, Math.min(most, width));
        depth = Math.max(MIN_ROOM, Math.min(most, depth));
        double t = Room.THICK;
        // the lines its walls stand on, from the middle - now and to be
        double hw = spec.length() / 2 - t / 2, hd = spec.depth() / 2 - t / 2;
        double nhw = width / 2 - t / 2, nhd = depth / 2 - t / 2;
        double c = Math.cos(floor.yaw()), sn = Math.sin(floor.yaw());
        double fx = floor.x(), fz = floor.z();
        // the doors out first: the walls either side of each keep its gap
        List<double[]> exits = new ArrayList<>();   // {u, v, new u, new v}
        for (LevelObject o : level.objects()) {
            if (!o.type().equals(ObjectTypes.EXIT)) continue;
            double[] m = local(o.x() - fx, o.z() - fz, c, sn);
            if (Math.abs(m[0]) > hw + 0.6 || Math.abs(m[1]) > hd + 0.6) continue;
            exits.add(new double[]{m[0], m[1], along(m[0], hw, nhw), along(m[1], hd, nhd)});
        }
        for (LevelObject o : level.objects()) {
            if (o == floor) continue;
            double[] m = local(o.x() - fx, o.z() - fz, c, sn);
            double u = m[0], v = m[1];
            if (Math.abs(u) > hw + 0.6 || Math.abs(v) > hd + 0.6) continue;     // not in this room
            double nu, nv;
            Room.Spec ws = o.type().equals(ObjectTypes.WALL) ? ObjectTypes.roomSpec(o) : null;
            double rel = o.yaw() - floor.yaw();
            boolean acrossU = Math.abs(Math.cos(rel)) > 0.9, acrossV = Math.abs(Math.sin(rel)) > 0.9;
            if (ws != null && (acrossU || acrossV)) {
                // a length of wall: its two ends moved, so it stretches with the room
                double run = acrossU ? u : v, side = acrossU ? v : u;
                double h = acrossU ? hw : hd, nh = acrossU ? nhw : nhd;
                double hs = acrossU ? hd : hw, nhs = acrossU ? nhd : nhw;
                double a = run - ws.length() / 2, b = run + ws.length() / 2;
                double na = end(a, h, nh, side, acrossU, exits), nb = end(b, h, nh, side, acrossU, exits);
                double nside = along(side, hs, nhs);
                double len = Math.max(0.3, nb - na);
                o.set("length", Math.round(len * 1000) / 1000.0);
                double mid = (na + nb) / 2;
                nu = acrossU ? mid : nside;
                nv = acrossU ? nside : mid;
            } else if (o.type().equals(ObjectTypes.EXIT)) {
                nu = along(u, hw, nhw);
                nv = along(v, hd, nhd);
            } else {
                double r = reach(o);
                nu = place(u, r, hw, nhw);
                nv = place(v, r, hd, nhd);
            }
            double[] w = world(nu, nv, c, sn);
            o.moveTo(Math.round((fx + w[0]) * 1000) / 1000.0, Math.round((fz + w[1]) * 1000) / 1000.0);
        }
        floor.set("width", Math.round(width * 1000) / 1000.0);
        floor.set("depth", Math.round(depth * 1000) / 1000.0);
    }

    /** {@code (dx, dz)} from a floor's middle in its own metres: across it, and from its back to its front. */
    private static double[] local(double dx, double dz, double c, double s) {
        return new double[]{-dx * c + dz * s, dx * s + dz * c};    // (as a building's own metres are)
    }

    private static double[] world(double u, double v, double c, double s) {
        return new double[]{-u * c + v * s, u * s + v * c};
    }

    /**
     * Where a point {@code p} along a room's {@code h} (its wall's line from
     * the middle) goes when that is {@code nh}: on (or past) the wall it
     * moves with it, inside it spreads out - the ends of the side walls, half
     * a wall in from the line, landing half a wall in from the new one.
     */
    static double along(double p, double h, double nh) {
        double in = h - Room.THICK / 2, nin = nh - Room.THICK / 2;
        if (Math.abs(p) >= in - 1e-6) return p + Math.signum(p) * (nh - h);
        return in <= 0 ? p : p * nin / in;
    }

    /**
     * Where a thing standing at {@code p}, reaching {@code r} out from
     * there, goes: against a wall (within {@link #AGAINST} of it) it keeps
     * to it, else it spreads out with the room.
     */
    static double place(double p, double r, double h, double nh) {
        double face = h - Room.THICK / 2;
        if (Math.abs(p) + r > face - AGAINST) return p + Math.signum(p) * (nh - h);
        return h <= 0 ? p : p * nh / h;
    }

    /** How far {@code o} reaches out from where it stands, metres: a piece of furniture its footprint. */
    static double reach(LevelObject o) {
        BuildingProfile p = o.type().equals(ObjectTypes.BUILDING)
                ? ObjectTypes.sized(o, BuildingCatalog.get().profile(o.str("kind", ""))) : null;
        if (p == null || p.footprint().isEmpty()) return 0.3;
        double r = 0;
        for (BuildingProfile.Shape s : p.footprint()) {
            double hw = s.circle() ? s.w() : s.w() / 2, hd = s.circle() ? s.w() : s.d() / 2;
            r = Math.max(r, Math.max(Math.abs(s.x()) + hw, Math.abs(s.y()) + hd));
        }
        return Math.min(r, 3);
    }

    /** A wall's end at {@code p} along its run: next to a door out it keeps the door's gap, else {@link #along}. */
    private static double end(double p, double h, double nh, double side, boolean acrossU, List<double[]> exits) {
        for (double[] e : exits) {
            double eRun = acrossU ? e[0] : e[1], eSide = acrossU ? e[1] : e[0];
            double nRun = acrossU ? e[2] : e[3];
            if (Math.abs(eSide - side) > 0.3) continue;
            if (Math.abs(p - (eRun - Room.EXIT_W / 2)) < 0.06) return nRun - Room.EXIT_W / 2;
            if (Math.abs(p - (eRun + Room.EXIT_W / 2)) < 0.06) return nRun + Room.EXIT_W / 2;
        }
        return along(p, h, nh);
    }

    /** The headings: facing north, south, east, west. */
    static final double NORTH = 0, SOUTH = Math.PI, EAST = -Math.PI / 2, WEST = Math.PI / 2;

    /** The floors inside {@code kind} as it comes (none: it has no inside). */
    public static List<Level> make(String kind) {
        List<Level> floors = new ArrayList<>();
        switch (kind == null ? "" : kind) {
            case "cottage" -> {
                Plan g = new Plan(0, 8, 6, Looks.COTTAGE);
                g.front(-1.2, "window", 2.4);
                g.back("window", -1.0);
                g.west("window", 0);
                g.east();
                g.against(Side.NORTH, -2.6, "bed");
                g.against(Side.NORTH, 1.4, "cupboard");
                g.against(Side.EAST, -0.3, "fireplace");
                g.at("table", -0.6, 0.6, NORTH);
                g.chairsAt(-0.6, 0.6, true);
                g.at("barrel", 3.45, 2.45, NORTH);
                g.at("barrel", 2.85, 2.55, NORTH);
                g.against(Side.WEST, 1.9, "crate");
                g.light("candle", -0.6, 0.6, 1.05, 4.5);
                g.herd(AnimalKind.CAT, 1.5, 1.2, 1, 2.0);
                floors.add(g.floor);
            }
            case "cabin" -> {
                Plan g = new Plan(0, 7, 5.5, Looks.CABIN);
                g.front(-1.4, "window", 1.9);
                g.back();
                g.west("window", -0.4);
                g.east();
                g.against(Side.NORTH, -2.3, "bed");
                g.against(Side.EAST, -0.6, "fireplace");
                g.at("table", 0.2, 0.3, NORTH);
                g.at("bench", 0.2, -0.45, SOUTH);
                g.at("stool", 1.35, 0.35, WEST);
                g.at("barrel", 3.05, 2.2, NORTH);
                g.against(Side.NORTH, 1.2, "crate");
                g.against(Side.WEST, 1.6, "barrel");
                g.light("candle", 0.2, 0.3, 1.05, 4.5);
                g.herd(AnimalKind.DOG, 1.4, 1.4, 1, 2.0);
                floors.add(g.floor);
            }
            case "house" -> {
                Plan g = new Plan(0, 9.5, 7, Looks.HOUSE);
                g.front(0.8, "window", -2.6, "window", 3.4);
                g.back("window", 1.5);
                g.west();
                g.east("window", -1.6);
                g.stairs(true, -3.75, -1.6);
                g.against(Side.NORTH, 0.0, "counter");
                g.against(Side.NORTH, 2.6, "cupboard");
                g.against(Side.EAST, 1.6, "fireplace");
                g.at("table", 0.6, 0.9, NORTH);
                g.chairsAt(0.6, 0.9, false);
                g.against(Side.EAST, -3.0, "barrel");
                g.against(Side.NORTH, -2.0, "crate");
                g.light("lantern", 0.6, 0.9, 1.1, 5.5);
                floors.add(g.floor);
                Plan u = new Plan(1, 9.5, 7, Looks.HOUSE);
                u.front("window", -2.6, "window", 2.6);
                u.back("window", -1.6, "window", 2.8);
                u.west("window", 1.0);
                u.east("window", 0);
                u.stairs(false, -3.75, -1.6);
                u.inner(0.6, -3.5, 0.6, 3.5, "door", EAST);   // the bedroom's wall, its door halfway
                u.against(Side.NORTH, 2.0, "bed");
                u.at("bed", 3.45, 2.0, WEST);
                u.against(Side.SOUTH, 3.4, "wardrobe");
                u.against(Side.NORTH, -1.4, "bookshelf");
                u.at("stool", -1.0, 1.4, NORTH);
                u.chest(-2.2, 2.7, NORTH);
                u.light("candle", 2.6, 0.8, 0.9, 4.0);
                u.light("candle", -1.6, 1.0, 1.0, 3.5);
                floors.add(u.floor);
            }
            case "windmill" -> {
                Plan g = new Plan(0, 5.5, 5.5, Looks.MILL);
                g.front(0);
                g.back("window", 0.8);
                g.west();
                g.east("window", 0);
                g.stairs(true, -1.95, -0.6);
                g.against(Side.NORTH, 1.2, "counter");
                g.at("crate", 1.9, 1.85, NORTH);
                g.at("crate", 1.75, 1.15, EAST);
                g.at("barrel", -0.6, -2.2, NORTH);
                g.light("lantern", 0.4, 0.4, 2.0, 4.5);
                floors.add(g.floor);
                Plan u = new Plan(1, 5.5, 5.5, Looks.MILL);
                u.front("window", 0.6);
                u.back();
                u.west("window", 0.6);
                u.east("window", 0);
                u.stairs(false, -1.95, -0.6);
                u.against(Side.NORTH, 1.0, "table");
                u.at("stool", 1.0, -1.1, SOUTH);
                u.against(Side.EAST, 1.7, "crate");
                u.light("candle", 1.0, -1.7, 1.0, 4.0);
                floors.add(u.floor);
            }
            case "watermill" -> {
                Plan g = new Plan(0, 8, 6.5, Looks.MILL);
                g.front(-1.6, "window", 1.8);
                g.back("window", 0);
                g.west("window", 0);
                g.east("window", 0);
                g.against(Side.NORTH, -2.3, "counter");
                g.at("table", 1.4, 0.2, NORTH);
                g.at("bench", 1.4, -0.55, SOUTH);
                g.at("crate", 3.4, 2.65, NORTH);
                g.at("crate", 2.65, 2.7, NORTH);
                g.at("crate", 3.4, 1.9, EAST);
                g.at("barrel", -3.4, 2.6, NORTH);
                g.at("barrel", -3.4, 1.85, NORTH);
                g.light("lantern", 1.4, 0.2, 1.1, 5.0);
                floors.add(g.floor);
            }
            case "keep" -> {
                Plan g = new Plan(0, 12, 12, Looks.CASTLE);
                g.front(0, "window", -3.6, "window", 3.6);
                g.back("window", -3, "window", 3);
                g.west("window", 2.5);
                g.east("window", 2.5);
                g.stairs(true, -5.15, -3.4);
                g.at("table", -0.95, -0.4, NORTH);
                g.at("table", 0.95, -0.4, NORTH);
                g.at("bench", -0.95, -1.25, SOUTH);
                g.at("bench", 0.95, -1.25, SOUTH);
                g.at("bench", -0.95, 0.45, NORTH);
                g.at("bench", 0.95, 0.45, NORTH);
                g.against(Side.NORTH, 0, "fireplace");
                g.against(Side.EAST, -3.8, "bookshelf");
                g.against(Side.EAST, -2.6, "bookshelf");
                g.against(Side.NORTH, 3.8, "cupboard");
                g.at("barrel", 5.25, 5.2, NORTH);
                g.at("barrel", 4.55, 5.25, NORTH);
                g.light("lantern", -2.6, 2.6, 2.2, 6.5);
                g.light("lantern", 2.6, 2.6, 2.2, 6.5);
                g.herd(AnimalKind.DOG, 2.8, 1.5, 2, 3.0);
                floors.add(g.floor);
                Plan c = new Plan(1, 12, 12, Looks.CASTLE);
                c.front("window", -3.6, "window", 3.6);
                c.back("window", -3, "window", 3);
                c.west("window", 2.5);
                c.east("window", 2.5);
                c.stairs(false, -5.15, -3.4);
                c.stairs(true, 5.15, -3.4);
                c.inner(0, -6, 0, 0.8, "door", EAST);           // two chambers at the back
                c.against(Side.NORTH, -2.6, "bed");
                c.against(Side.NORTH, 2.6, "bed");
                c.against(Side.WEST, 1.2, "wardrobe");
                c.against(Side.EAST, 1.2, "wardrobe");
                c.at("fireplace", -0.52, -4.8, WEST);
                c.at("table", 2.4, 2.8, NORTH);
                c.chairsAt(2.4, 2.8, true);
                c.chest(-4.6, 4.6, NORTH);
                c.light("candle", 2.4, 2.8, 1.05, 5.0);
                c.light("candle", 2.6, -3.0, 1.0, 4.5);
                floors.add(c.floor);
                Plan a = new Plan(2, 12, 12, Looks.CASTLE);
                a.front("window", -3.6, "window", 3.6);
                a.back("window", -3, "window", 3);
                a.west("window", 0);
                a.east("window", 0);
                a.stairs(false, 5.15, -3.4);
                for (int i = 0; i < 4; i++) a.against(Side.NORTH, -4.4 + i * 0.8, "crate");
                for (int i = 0; i < 3; i++) a.against(Side.WEST, -1.0 + i * 0.8, "barrel");
                a.chest(-1.0, -4.6, SOUTH);
                a.chest(1.2, -4.6, SOUTH);
                a.at("table", 0, 1.2, NORTH);
                a.light("lantern", 0, 1.2, 1.2, 6.0);
                floors.add(a.floor);
            }
            case "gatehouse" -> {
                Plan g = new Plan(0, 5, 5, Looks.CASTLE);
                g.front(0);
                g.back("window", 0);
                g.west();
                g.east();
                g.at("table", 0, -0.6, NORTH);
                g.at("stool", -0.7, 0.1, NORTH);
                g.at("stool", 0.7, 0.1, NORTH);
                g.at("barrel", 1.95, -1.95, NORTH);
                g.at("crate", -1.9, -1.9, NORTH);
                g.light("lantern", 0, -0.6, 1.1, 4.5);
                floors.add(g.floor);
            }
            case "round_tower", "square_tower" -> {
                Plan g = new Plan(0, 5.2, 5.2, Looks.CASTLE);
                g.front(0);
                g.back("window", 0.8);
                g.west();
                g.east("window", 0);
                g.stairs(true, -1.75, -0.45);
                g.at("barrel", 1.9, -1.9, NORTH);
                g.at("barrel", 1.2, -1.95, NORTH);
                g.at("crate", 1.85, -1.15, NORTH);
                g.light("lantern", 0.4, 0.6, 1.8, 4.0);
                floors.add(g.floor);
                Plan u = new Plan(1, 5.2, 5.2, Looks.CASTLE);
                u.front("window", 0.6);
                u.back("window", 0.8);
                u.west();
                u.east("window", 0);
                u.stairs(false, -1.75, -0.45);
                u.at("table", 1.2, 0.8, NORTH);
                u.at("stool", 1.2, 1.55, NORTH);
                u.chest(1.4, -1.85, SOUTH);
                u.light("candle", 1.2, 0.8, 1.0, 4.0);
                floors.add(u.floor);
            }
            case "spire" -> {
                Plan g = new Plan(0, 4.2, 4.2, Looks.TOWER);
                g.front(0);
                g.back("window", 0);
                g.west("window", 0);
                g.east("window", 0);
                g.against(Side.NORTH, -1.1, "bookshelf");
                g.against(Side.NORTH, 0.6, "bookshelf");
                g.at("table", 0.4, -0.3, NORTH);
                g.at("stool", 0.4, 0.4, NORTH);
                g.light("candle", 0.4, -0.3, 1.0, 4.0);
                floors.add(g.floor);
            }
            case "turret" -> {
                Plan g = new Plan(0, 3.4, 3.4, Looks.TOWER);
                g.front(0);
                g.back("window", 0);
                g.west();
                g.east();
                g.at("barrel", -1.1, -1.1, NORTH);
                g.at("crate", 1.05, -1.05, NORTH);
                g.at("stool", 0.9, 0.5, NORTH);
                g.light("lantern", 0, -0.4, 1.8, 3.5);
                floors.add(g.floor);
            }
            case "row_house" -> {
                // a narrow house of three floors: the kitchen, a bedroom over it, the attic under the roof
                Plan g = new Plan(0, 6, 7.2, Looks.HOUSE);
                g.front(-1.4, "window", 1.4);
                g.back("window", 1.0);
                g.west();
                g.east();
                g.stairs(true, -2.0, -1.7);
                g.against(Side.EAST, -1.2, "fireplace");
                g.against(Side.NORTH, 1.6, "cupboard");
                g.at("table", 0.8, 1.2, NORTH);
                g.at("chair", 0.35, 0.42, SOUTH);
                g.at("chair", 1.25, 1.98, NORTH);
                g.at("pot", 2.55, 3.0, NORTH);
                g.at("large_pot", -2.45, 3.0, NORTH);
                g.at("barrel", 0.0, -2.9, NORTH);
                g.light("candle", 0.8, 1.2, 1.05, 4.5);
                floors.add(g.floor);
                Plan u = new Plan(1, 6, 7.2, Looks.HOUSE);
                u.front("window", -1.3, "window", 1.3);
                u.back("window", 1.3);
                u.west();
                u.east("window", 0);
                u.stairs(false, -2.0, -1.7);
                u.stairs(true, 2.0, -1.7);
                u.against(Side.WEST, 1.8, "bed");
                u.against(Side.SOUTH, 1.6, "wardrobe");
                u.at("rug", 0.4, 0.9, NORTH);
                u.onWall(Side.EAST, 1.6, "painting");
                u.at("potted_plant", 2.5, 0.6, NORTH);
                u.light("candle", 0.0, -1.0, 0.9, 4.0);
                floors.add(u.floor);
                Plan a = new Plan(2, 6, 7.2, Looks.HOUSE);
                a.front("window", 0);
                a.back();
                a.west("window", 0);
                a.east("window", 0);
                a.stairs(false, 2.0, -1.7);
                a.against(Side.NORTH, -1.2, "bed");
                a.chest(1.9, 2.8, NORTH);
                a.at("crate", -2.4, 2.9, NORTH);
                a.at("pot", -1.6, 3.0, NORTH);
                a.light("candle", 0.6, 0.6, 0.9, 4.0);
                floors.add(a.floor);
            }
            case "shop_house" -> {
                // the shop downstairs - a counter, its keeper's spot, what is for sale - and the family's rooms up
                Plan g = new Plan(0, 8.5, 7.2, Looks.SHOP);
                g.front(0, "window", -2.6, "window", 2.6);
                g.back();
                g.west();
                g.east("window", 0.5);
                g.stairs(true, -3.25, -1.7);
                g.against(Side.NORTH, 1.0, "shop_counter");
                g.shop("Shop", 1.0, -0.95, "");
                g.at("barrel", 3.5, 2.9, NORTH);
                g.at("crate", 3.45, 2.1, NORTH);
                g.at("large_pot", -3.6, 3.0, NORTH);
                g.at("pot", -3.0, 3.05, NORTH);
                g.at("rug", -0.8, 1.6, NORTH);
                g.onWall(Side.WEST, 1.2, "banner");
                g.light("lantern", 1.0, 0.6, 2.2, 5.5);
                floors.add(g.floor);
                Plan u = new Plan(1, 8.5, 7.2, Looks.SHOP);
                u.front("window", -2.4, "window", 0, "window", 2.4);
                u.back("window", 1.6);
                u.west("window", 1.0);
                u.east("window", 0);
                u.stairs(false, -3.25, -1.7);
                u.against(Side.NORTH, 2.4, "bed");
                u.against(Side.EAST, -1.2, "fireplace");
                u.at("table", 0.4, 1.0, NORTH);
                u.chairsAt(0.4, 1.0, false);
                u.against(Side.SOUTH, -2.8, "bookshelf");
                u.against(Side.SOUTH, 3.2, "grandfather_clock");
                u.onWall(Side.NORTH, -0.4, "painting");
                u.at("potted_plant", -3.6, 0.6, NORTH);
                u.light("candle", 0.4, 1.0, 1.05, 4.5);
                floors.add(u.floor);
            }
            case "workshop" -> {
                // a tinkerer's workshop: the boiler and its pipes, a lightning coil, an alchemist's bench, stores
                Plan g = new Plan(0, 9.6, 7.2, Looks.WORKS);
                g.front(0, "window", -3.6, "window", 3.6);
                g.back("window", -3.0, "window", 0, "window", 3.0);
                g.west("window", 0);
                g.east();
                g.at("boiler", 3.6, -2.4, NORTH);
                g.at("tesla_coil", -3.7, -2.6, NORTH);
                g.against(Side.NORTH, -0.6, "alchemy_table");
                g.at("table", 0.6, 1.0, NORTH);
                g.at("stool", 0.6, 1.7, NORTH);
                g.against(Side.WEST, 2.0, "bookshelf");
                g.at("crate", 4.1, 2.9, NORTH);
                g.at("crate", 3.3, 3.0, NORTH);
                g.at("barrel", 4.2, 2.1, NORTH);
                g.at("pot", -4.2, 0.9, NORTH);
                g.light("lantern", 0.6, 1.0, 2.2, 6.0);
                g.light("lantern", -1.0, -2.0, 2.2, 5.0);
                floors.add(g.floor);
            }
            case "clocktower" -> {
                Plan g = new Plan(0, 5.2, 5.2, Looks.CASTLE);
                g.front(0);
                g.back("window", 0.8);
                g.west();
                g.east("window", 0);
                g.stairs(true, -1.75, -0.45);
                g.at("barrel", 1.9, -1.9, NORTH);
                g.at("crate", 1.85, -1.15, NORTH);
                g.light("lantern", 0.4, 0.6, 1.8, 4.0);
                floors.add(g.floor);
                Plan u = new Plan(1, 5.2, 5.2, Looks.CASTLE);
                u.front("window", 0.6);
                u.back("window", 0.8);
                u.west();
                u.east("window", 0);
                u.stairs(false, -1.75, -0.45);
                u.against(Side.NORTH, 1.3, "grandfather_clock");
                u.at("table", 1.2, 1.2, NORTH);
                u.at("stool", 1.2, 1.95, NORTH);
                u.light("candle", 1.2, 1.2, 1.0, 4.0);
                floors.add(u.floor);
            }
            case "foundry" -> {
                // the furnaces' hall: cauldrons over their fires, boilers, the still, stores of ore and ingots
                Plan g = new Plan(0, 12, 8.5, Looks.WORKS);
                g.front(0, "window", -4.0, "window", 4.0);
                g.back("window", -3.4, "window", -1.2, "window", 1.2, "window", 3.4);
                g.west();
                g.east();
                g.at("cauldron", -3.0, -1.6, NORTH);
                g.at("cauldron", -1.4, -1.6, NORTH);
                g.at("boiler", 4.9, -3.3, NORTH);
                g.at("boiler", 3.3, -3.3, NORTH);
                g.at("distiller", 1.0, -2.8, NORTH);
                g.at("table", -2.2, 1.8, NORTH);
                g.at("crate", 5.3, 3.5, NORTH);
                g.at("crate", 4.5, 3.6, NORTH);
                g.at("crate", 5.3, 2.7, NORTH);
                g.at("barrel", -5.4, 3.5, NORTH);
                g.at("barrel", -5.4, 2.8, NORTH);
                g.at("large_pot", -4.6, 3.6, NORTH);
                g.light("lantern", -2.2, 1.8, 2.4, 6.5);
                g.light("lantern", 3.0, -1.0, 2.4, 6.5);
                floors.add(g.floor);
            }
            default -> { }
        }
        return floors;
    }

    /** Which side of a room. */
    enum Side { NORTH, SOUTH, EAST, WEST }

    /** The colours and patterns a kind's rooms are done in. */
    record Looks(String wall, String wallTex, String trim, String trimTex, String floor, String floorTex) {
        static final Looks COTTAGE = new Looks("#d8c8a0", "plaster", "#6a4a30", "timber", "#8a6038", "floorboards");
        static final Looks CABIN = new Looks("#8a5a34", "logs", "#5a3a22", "timber", "#7a5030", "floorboards");
        static final Looks HOUSE = new Looks("#e0d4b4", "plaster", "#5a3a22", "timber", "#9a6a40", "floorboards");
        static final Looks MILL = new Looks("#c8b890", "plaster", "#6a4a30", "timber", "#8a7a60", "cobble");
        static final Looks CASTLE = new Looks("#8a867e", "stone_blocks", "#5a5650", "stone_blocks", "#7a766e",
                "flagstones");
        static final Looks TOWER = new Looks("#9a948a", "fieldstone", "#5a3a22", "timber", "#8a6038", "floorboards");
        static final Looks SHOP = new Looks("#e4d8b8", "plaster", "#2e4a3a", "timber", "#8a6038", "floorboards");
        static final Looks WORKS = new Looks("#9a4a36", "brick", "#3a3a40", "metal", "#6e6a64", "flagstones");

        Map<String, Object> walls() {
            Map<String, Object> look = new LinkedHashMap<>();
            look.put("front", Map.of("colour", wall, "texture", wallTex));
            look.put("back", Map.of("colour", wall, "texture", wallTex));
            look.put("trim", Map.of("colour", trim, "texture", trimTex));
            return look;
        }
    }

    /**
     * One floor being laid out: a room {@code w} by {@code d} round its
     * middle, its walls side by side (each side its openings), its door out
     * in the front on the ground floor, its stairs, its furniture.
     */
    static final class Plan {
        final Level floor;
        final double x0, x1, z0, z1;
        final Looks looks;
        final int n;

        Plan(int n, double w, double d, Looks looks) {
            this.n = n;
            this.floor = emptyFloor(n);
            this.looks = looks;
            x0 = -w / 2;
            x1 = w / 2;
            z0 = -d / 2;
            z1 = d / 2;
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("width", w + Room.THICK);
            props.put("depth", d + Room.THICK);
            props.put("look", Map.of("floor", Map.of("colour", looks.floor, "texture", looks.floorTex)));
            put(ObjectTypes.FLOOR, 0, 0, NORTH, props);
        }

        LevelObject put(String type, double x, double z, double heading, Map<String, Object> props) {
            return floor.add(type, round(x), round(z), heading, new LinkedHashMap<>(props));
        }

        private static double round(double v) {
            return Math.round(v * 1000) / 1000.0;
        }

        /**
         * The front wall (south): with the door out at {@code exitAt} on the
         * ground floor, and the openings after it - pairs of what ({@code
         * "window"}, {@code "door"}, {@code "arch"}) and where along it. On
         * a floor above, {@code exitAt} is the first opening's kind instead.
         */
        void front(Object... spec) {
            List<Object[]> gaps = new ArrayList<>();
            int i = 0;
            boolean ground = n == 0 && spec.length > 0 && spec[0] instanceof Number;
            if (ground) {
                gaps.add(new Object[]{"exit", ((Number) spec[0]).doubleValue()});
                i = 1;
            }
            for (; i + 1 < spec.length; i += 2) {
                if (spec[i] instanceof String kind && spec[i + 1] instanceof Number at) {
                    gaps.add(new Object[]{kind, at.doubleValue()});
                }
            }
            side(Side.SOUTH, gaps);
        }

        void back(Object... spec) { side(Side.NORTH, pairs(spec)); }

        void west(Object... spec) { side(Side.WEST, pairs(spec)); }

        void east(Object... spec) { side(Side.EAST, pairs(spec)); }

        private static List<Object[]> pairs(Object... spec) {
            List<Object[]> out = new ArrayList<>();
            for (int i = 0; i + 1 < spec.length; i += 2) {
                out.add(new Object[]{spec[i], ((Number) spec[i + 1]).doubleValue()});
            }
            return out;
        }

        /**
         * A side's wall: plain lengths between its openings, each opening
         * {@value #GAP} m of wall with it in (the door out its own width).
         * Along a side is east-going on the north and south, south-going on
         * the east and west; the north and south walls reach over the corners.
         */
        private void side(Side s, List<Object[]> gaps) {
            boolean ns = s == Side.NORTH || s == Side.SOUTH;
            double from = ns ? x0 - Room.THICK / 2 : z0 + Room.THICK / 2;
            double to = ns ? x1 + Room.THICK / 2 : z1 - Room.THICK / 2;
            gaps.sort((a, b) -> Double.compare((Double) a[1], (Double) b[1]));
            double at = from;
            for (Object[] g : gaps) {
                String kind = (String) g[0];
                double width = kind.equals("exit") ? Room.EXIT_W : GAP;
                double a = Math.max(at, (Double) g[1] - width / 2), b = Math.min(to, a + width);
                if (a - at > 0.05) wallPiece(s, at, a, "none");
                if (kind.equals("exit")) {
                    double mid = (a + b) / 2;
                    put(ObjectTypes.EXIT, ns ? mid : sideLine(s), ns ? sideLine(s) : mid, inward(s), Map.of());
                } else {
                    wallPiece(s, a, b, kind);
                }
                at = b;
            }
            if (to - at > 0.05) wallPiece(s, at, to, "none");
        }

        /** How much wall an opening is set in, metres. */
        static final double GAP = 1.8;

        private double sideLine(Side s) {
            return switch (s) {
                case NORTH -> z0;
                case SOUTH -> z1;
                case WEST -> x0;
                case EAST -> x1;
            };
        }

        /** The way a side's wall faces: into the room. */
        private static double inward(Side s) {
            return switch (s) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
            };
        }

        private void wallPiece(Side s, double a, double b, String opening) {
            boolean ns = s == Side.NORTH || s == Side.SOUTH;
            double mid = (a + b) / 2;
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("length", round(b - a));
            props.put("height", Room.STOREY);
            props.put("opening", opening);
            props.put("look", looks.walls());
            put(ObjectTypes.WALL, ns ? mid : sideLine(s), ns ? sideLine(s) : mid, inward(s), props);
        }

        /** A wall inside, from {@code (ax, az)} to {@code (bx, bz)}, with {@code opening} halfway, its front facing {@code heading}. */
        void inner(double ax, double az, double bx, double bz, String opening, double heading) {
            double len = Math.hypot(bx - ax, bz - az);
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("length", round(len));
            props.put("height", Room.STOREY);
            props.put("opening", opening);
            props.put("look", looks.walls());
            put(ObjectTypes.WALL, (ax + bx) / 2, (az + bz) / 2, heading, props);
        }

        /** A staircase up ({@code up}) or a stairwell down, its foot to the south, at {@code (x, z)}. */
        void stairs(boolean up, double x, double z) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("way", up ? "up" : "down");
            props.put("look", Map.of("steps", Map.of("colour", looks.floor, "texture", looks.floorTex.equals("cobble")
                    || looks.floorTex.equals("flagstones") ? looks.floorTex : "floorboards")));
            put(ObjectTypes.STAIRS, x, z, SOUTH, props);
        }

        /** A piece of furniture at {@code (x, z)}, its front facing {@code heading}. */
        void at(String kind, double x, double z, double heading) {
            put(ObjectTypes.BUILDING, x, z, heading, Map.of("kind", kind));
        }

        /** A piece of furniture with its back to side {@code s}'s wall, {@code along} it, facing into the room. */
        void against(Side s, double along, String kind) {
            double depth = depthOf(kind), off = Room.THICK / 2 + depth / 2 + 0.03;
            switch (s) {
                case NORTH -> at(kind, along, z0 + off, SOUTH);
                case SOUTH -> at(kind, along, z1 - off, NORTH);
                case WEST -> at(kind, x0 + off, along, EAST);
                case EAST -> at(kind, x1 - off, along, WEST);
            }
        }

        /** How deep a piece of furniture is, front to back. */
        static double depthOf(String kind) {
            BuildingProfile p = BuildingCatalog.get().profile(kind);
            if (p == null || p.footprint().isEmpty()) return 0.6;
            BuildingProfile.Shape s = p.footprint().get(0);
            return s.circle() ? s.w() * 2 : s.d();
        }

        /** Something hung on side {@code s}'s wall (a painting, a banner), {@code along} it, facing into the room. */
        void onWall(Side s, double along, String kind) {
            double off = Room.THICK / 2 + 0.01;
            switch (s) {
                case NORTH -> at(kind, along, z0 + off, SOUTH);
                case SOUTH -> at(kind, along, z1 - off, NORTH);
                case WEST -> at(kind, x0 + off, along, EAST);
                case EAST -> at(kind, x1 - off, along, WEST);
            }
        }

        /** A shop's keeper's spot at {@code (x, z)} - at a counter already there - called {@code name}, selling {@code stock}. */
        void shop(String name, double x, double z, String stock) {
            put(ObjectTypes.SHOP, x, z, SOUTH, Map.of("name", name, "stall", "none", "stock", stock, "buys", true));
        }

        /** Chairs round a table at {@code (x, z)}: one each side of it, and ({@code ends}) one at each end. */
        void chairsAt(double x, double z, boolean ends) {
            at("chair", x - 0.45, z - 0.78, SOUTH);
            at("chair", x + 0.45, z + 0.78, NORTH);
            if (ends) {
                at("chair", x - 1.18, z, EAST);
                at("chair", x + 1.18, z, WEST);
            } else {
                at("chair", x + 0.45, z - 0.78, SOUTH);
                at("chair", x - 0.45, z + 0.78, NORTH);
            }
        }

        /** A treasure chest at {@code (x, z)}. */
        void chest(double x, double z, double heading) {
            put(ObjectTypes.CHEST, x, z, heading, Map.of("sprite", ObjectTypes.CHEST_SPRITE));
        }

        /** A light of {@code colour} over {@code (x, z)}, {@code height} up, reaching {@code radius}. */
        void light(String colour, double x, double z, double height, double radius) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("colour", colour);
            props.put("radius", radius);
            props.put("strength", 1.0);
            props.put("height", height);
            props.put("glow", true);
            put(ObjectTypes.LIGHT, x, z, NORTH, props);
        }

        /** {@code count} of {@code kind} living round {@code (x, z)}, within {@code radius}. */
        void herd(AnimalKind kind, double x, double z, int count, double radius) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("kind", kind.key());
            props.put("count", (double) count);
            props.put("radius", radius);
            props.put("temperament", ObjectTypes.OWN);
            props.put("when", Critters.When.ALWAYS.key());
            put(ObjectTypes.ANIMALS, x, z, NORTH, props);
        }
    }
}
