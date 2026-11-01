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
