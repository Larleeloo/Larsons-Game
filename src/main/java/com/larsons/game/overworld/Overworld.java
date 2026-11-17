package com.larsons.game.overworld;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * An overworld map: the land she travels over between the places the game
 * is played in. It is a grid of terrain tiles (grassland, forest, mountains,
 * water ...) with <b>destinations</b> on it joined by <b>paths</b>; she walks
 * the paths from destination to destination, as on a Mario map, and choosing
 * a destination loads the level the map's maker gave it.
 *
 * <ul>
 *   <li>A destination ({@link Node}) has a name, a kind (the icon it is
 *       drawn as: a town, a castle, a cave ...), the level it loads (a folder
 *       of {@code assets/levels/}; none: somewhere to pass through), a line
 *       about it, and whether it is <b>open</b>: an open one can be walked on
 *       past at once, any other must be cleared first (its level's exit
 *       reached).</li>
 *   <li>A path joins two destinations, straight or by way of the tiles it
 *       was bent through.</li>
 *   <li>She starts at the map's start.</li>
 * </ul>
 *
 * Maps are made in the overworld editor (main menu, Dev) and kept in {@code
 * assets/overworld/maps/<id>.json}. The <b>main story</b>'s map is the
 * game's own, kept apart in {@code assets/overworld/story/}: the editor
 * shows it but will not change or delete it (a copy of it can be made and
 * changed instead), so the story can always be played.
 *
 * <p>A map can lie over a picture of one's own instead of (or as well as)
 * its tiles - a PNG kept in {@code assets/overworld/backgrounds/}, stretched
 * over the whole map - with its destinations and roads put on the picture;
 * and its destinations can be the painted icons or the plain marks of an
 * old map ({@link #MARKERS}: filled once found, hollow till then).
 *
 * <pre>
 * {"name": "The Main Lands", "width": 40, "height": 24, "start": "harbour",
 *  "tiles": ["wwwwggggffff...", ...],
 *  "nodes": [{"id": "harbour", "name": "Brasswick Harbour", "kind": "port", "x": 5, "y": 14,
 *             "level": "brasswick", "open": true, "about": "..."}],
 *  "paths": [{"from": "harbour", "to": "mill", "via": [[8, 14], [8, 10]]}]}
 * </pre>
 */
public final class Overworld {

    /** A kind of ground: its letter in the file, its row in {@code tiles.png}. */
    public record Terrain(char key, String id, String name, boolean water) {}

    /** The terrains, in the order of {@code tiles.png}'s rows ({@code tools/overworld/make_tiles.py}). */
    public static final List<Terrain> TERRAINS = List.of(
            new Terrain('g', "grass", "Grassland", false), new Terrain('f', "forest", "Forest", false),
            new Terrain('p', "pines", "Pine forest", false), new Terrain('j', "jungle", "Jungle", false),
            new Terrain('h', "hills", "Hills", false), new Terrain('m', "mountains", "Mountains", false),
            new Terrain('s', "snow", "Snow", false), new Terrain('b', "sand", "Sand", false),
            new Terrain('y', "savannah", "Savannah", false), new Terrain('x', "marsh", "Marsh", false),
            new Terrain('a', "waste", "Waste", false), new Terrain('c', "crystal", "Crystal", false),
            new Terrain('w', "water", "Water", true), new Terrain('d', "deep", "Deep water", true));

    /** The kinds of destination, in the order of {@code icons.png}; after them come its marks. */
    public static final List<String> KINDS = List.of("town", "village", "castle", "camp", "cave", "tower", "port",
            "ruins", "mine", "workshop", "shrine", "arena", "crypt", "portal");
    /**
     * The plain map marks a destination can be instead, in the order of
     * {@code markers.png} ({@code tools/overworld/make_markers.py}): flat
     * silhouettes, as on a traveller's map - filled once she can get there,
     * hollow while she cannot.
     */
    public static final List<String> MARKERS = List.of("city", "hamlet", "fort", "keep", "watchtower", "lighthouse",
            "farm", "mill", "lumber_camp", "docks", "bridge", "inn", "temple", "standing_stone", "grove", "cavern",
            "mine_shaft", "barrow", "ruined_tower", "lair", "camp_tents", "gateway", "workshop_gears", "landmark");
    /** What each mark is called in the editor. */
    public static String markerName(String kind) {
        String k = kind.replace('_', ' ');
        return switch (kind) {
            case "workshop_gears" -> "steam works";
            case "camp_tents" -> "tents";
            case "mine_shaft" -> "mine shaft";
            default -> k;
        };
    }

    /** Whether {@code kind} is a destination's kind at all: an icon or a mark. */
    public static boolean isKind(String kind) {
        return KINDS.contains(kind) || MARKERS.contains(kind);
    }

    /** The ways a road can be drawn. */
    public static final List<String> ROAD_STYLES = List.of("dotted", "ink", "dashed", "track");

    /**
     * What clears a destination: going there at all, reaching its level's
     * way out, felling every foe in its level, or finishing a quest.
     */
    public static final List<String> CLEARS = List.of("visit", "exit", "foes", "quest");

    public static final int ICON_CLEARED = KINDS.size(), ICON_LOCKED = KINDS.size() + 1, ICON_HERE = KINDS.size() + 2;

    public static final int SMALLEST = 12, WIDEST = 160, TALLEST = 120;
    public static final String FOLDER = "overworld", MAPS = "maps", STORY = "story", BACKGROUNDS = "backgrounds";

    /** A destination. */
    public static final class Node {
        public final String id;
        public String name, kind = "town", level = "", about = "";
        /** What clears it ({@link #CLEARS}); the quest that does, for "quest"; another map it leads to. */
        public String clear = "exit", quest = "", toMap = "";
        public int x, y;
        public boolean open;

        public Node(String id, String name, int x, int y) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
        }

        /** Its icon's place in {@code icons.png} (a mark: the town's). */
        public int icon() {
            return Math.max(0, KINDS.indexOf(kind));
        }

        /** Its mark's place in {@code markers.png}; -1: it is drawn as an icon. */
        public int marker() {
            return MARKERS.indexOf(kind);
        }

        /** A copy of it, called {@code id}, at {@code (x, y)}. */
        public Node copy(String id, int x, int y) {
            Node n = new Node(id, name, x, y);
            n.kind = kind;
            n.level = level;
            n.about = about;
            n.clear = clear;
            n.quest = quest;
            n.toMap = toMap;
            n.open = open;
            return n;
        }

        /** Whether choosing it loads a level. */
        public boolean playable() { return level != null && !level.isBlank(); }

        /** Whether choosing it leads to another map. */
        public boolean travels() { return toMap != null && !toMap.isBlank(); }
    }

    /** A way between two destinations. */
    public static final class Way {
        public final String from, to;
        public final List<int[]> via = new ArrayList<>();

        public Way(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public boolean joins(String a, String b) {
            return from.equals(a) && to.equals(b) || from.equals(b) && to.equals(a);
        }

        /** The destination at its other end from {@code id} (null: it does not touch it). */
        public String other(String id) {
            return from.equals(id) ? to : to.equals(id) ? from : null;
        }
    }

    private String name;
    private int width, height;
    private char[][] tiles;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Way> ways = new ArrayList<>();
    private String start = "";
    /** The music that plays over it: a song's id, "shuffle" or "none" ({@code audio/Songs}). */
    private String music = "shuffle";
    /** The picture it lies over (a file of {@code backgrounds/}), "" for none; whether its tiles show too. */
    private String background = "";
    private boolean tilesShown = true;
    /** How its roads are drawn ({@link #ROAD_STYLES}), and whether they bend smoothly through their bends. */
    private String roadStyle = "dotted";
    private boolean smoothRoads;

    /** Whether this is the main story's own map (read from {@code story/}): never changed by the editor. */
    private boolean story;

    public boolean story() { return story; }

    public Overworld(String name, int width, int height) {
        this.name = name;
        this.width = clampW(width);
        this.height = clampH(height);
        tiles = new char[this.height][this.width];
        for (char[] row : tiles) Arrays.fill(row, 'w');
    }

    private static int clampW(int w) { return Math.max(SMALLEST, Math.min(WIDEST, w)); }

    private static int clampH(int h) { return Math.max(SMALLEST, Math.min(TALLEST, h)); }

    public String name() { return name; }

    public void setName(String n) {
        name = n == null || n.isBlank() ? "A map" : n.strip();
    }

    public String music() { return music; }

    public void setMusic(String m) {
        music = m == null || m.isBlank() ? "shuffle" : m;
    }

    public String background() { return background; }

    public void setBackground(String file) {
        background = file == null ? "" : file.strip();
    }

    /** Whether its terrain tiles are drawn (always, with no picture under it). */
    public boolean tilesShown() { return tilesShown || background.isEmpty(); }

    public void setTilesShown(boolean on) { tilesShown = on; }

    public String roadStyle() { return roadStyle; }

    public void setRoadStyle(String s) {
        roadStyle = ROAD_STYLES.contains(s) ? s : "dotted";
    }

    public boolean smoothRoads() { return smoothRoads; }

    public void setSmoothRoads(boolean on) { smoothRoads = on; }

    public int width() { return width; }

    public int height() { return height; }

    public List<Node> nodes() { return nodes; }

    public List<Way> ways() { return ways; }

    /** The destination she starts at (the first, when none is marked; null on a map with none). */
    public Node start() {
        Node s = node(start);
        return s != null ? s : nodes.isEmpty() ? null : nodes.get(0);
    }

    public void setStart(String id) {
        start = id == null ? "" : id;
    }

    public boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** The terrain at a tile (off the map: deep water). */
    public Terrain terrain(int x, int y) {
        return inside(x, y) ? terrain(tiles[y][x]) : TERRAINS.get(TERRAINS.size() - 1);
    }

    public static Terrain terrain(char key) {
        for (Terrain t : TERRAINS) if (t.key() == key) return t;
        return TERRAINS.get(0);
    }

    /** A terrain's row in {@code tiles.png}. */
    public static int row(Terrain t) {
        return TERRAINS.indexOf(t);
    }

    public void paint(int x, int y, Terrain t) {
        if (inside(x, y)) tiles[y][x] = t.key();
    }

    /** Every tile joined to {@code (x, y)} of its terrain made {@code t}. */
    public void flood(int x, int y, Terrain t) {
        if (!inside(x, y)) return;
        char from = tiles[y][x];
        if (from == t.key()) return;
        java.util.ArrayDeque<int[]> open = new java.util.ArrayDeque<>();
        open.add(new int[]{x, y});
        while (!open.isEmpty()) {
            int[] p = open.poll();
            if (!inside(p[0], p[1]) || tiles[p[1]][p[0]] != from) continue;
            tiles[p[1]][p[0]] = t.key();
            open.add(new int[]{p[0] + 1, p[1]});
            open.add(new int[]{p[0] - 1, p[1]});
            open.add(new int[]{p[0], p[1] + 1});
            open.add(new int[]{p[0], p[1] - 1});
        }
    }

    /** The map made {@code w} by {@code h}: what was on it kept where it fits, the new ground water. */
    public void resize(int w, int h) {
        w = clampW(w);
        h = clampH(h);
        char[][] to = new char[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) to[y][x] = y < height && x < width ? tiles[y][x] : 'w';
        }
        tiles = to;
        width = w;
        height = h;
        for (Node n : nodes) {
            n.x = Math.min(n.x, w - 1);
            n.y = Math.min(n.y, h - 1);
        }
        for (Way p : ways) p.via.removeIf(v -> v[0] >= width || v[1] >= height);
    }

    public Node node(String id) {
        if (id == null) return null;
        for (Node n : nodes) if (n.id.equals(id)) return n;
        return null;
    }

    public Node nodeAt(int x, int y) {
        for (Node n : nodes) if (n.x == x && n.y == y) return n;
        return null;
    }

    /** A new destination at a tile, called {@code name}: its id made from its name, unlike any other's. */
    public Node add(String name, int x, int y) {
        Node n = new Node(freeNodeId(name), name, x, y);
        nodes.add(n);
        if (nodes.size() == 1) start = n.id;
        return n;
    }

    /** A copy of {@code n} at {@code (x, y)}, with an id of its own ("... 2"). */
    public Node addCopy(Node n, int x, int y) {
        Node c = n.copy(freeNodeId(n.name), x, y);
        nodes.add(c);
        if (nodes.size() == 1) start = c.id;
        return c;
    }

    private String freeNodeId(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "place";
        String id = base;
        for (int i = 2; node(id) != null; i++) id = base + "_" + i;
        return id;
    }

    /** Everything on it moved {@code (dx, dy)} tiles (what would leave the map stays at its edge). */
    public void shift(int dx, int dy) {
        char[][] to = new char[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int fx = x - dx, fy = y - dy;
                to[y][x] = inside(fx, fy) ? tiles[fy][fx] : 'w';
            }
        }
        tiles = to;
        for (Node n : nodes) {
            n.x = Math.max(0, Math.min(width - 1, n.x + dx));
            n.y = Math.max(0, Math.min(height - 1, n.y + dy));
        }
        for (Way p : ways) {
            for (int[] v : p.via) {
                v[0] = Math.max(0, Math.min(width - 1, v[0] + dx));
                v[1] = Math.max(0, Math.min(height - 1, v[1] + dy));
            }
        }
    }

    /** Every tile from {@code (x0, y0)} to {@code (x1, y1)} (either way round) made {@code t}. */
    public void fillRect(int x0, int y0, int x1, int y1, Terrain t) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) paint(x, y, t);
        }
    }

    /** A straight stroke of {@code t} from one tile to another, {@code size} tiles wide. */
    public void line(int x0, int y0, int x1, int y1, int size, Terrain t) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        int r = size / 2;
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(x0 + (x1 - x0) * (steps == 0 ? 0f : i / (float) steps));
            int y = Math.round(y0 + (y1 - y0) * (steps == 0 ? 0f : i / (float) steps));
            for (int yy = y - r; yy <= y + r; yy++) for (int xx = x - r; xx <= x + r; xx++) paint(xx, yy, t);
        }
    }

    /** A copy of the whole map, for undoing back to. */
    public Overworld copy() {
        Overworld o = parse(json());
        o.story = story;
        return o;
    }

    /** A destination taken off the map, and its paths with it. */
    public void remove(Node n) {
        nodes.remove(n);
        ways.removeIf(p -> p.from.equals(n.id) || p.to.equals(n.id));
        if (n.id.equals(start)) start = nodes.isEmpty() ? "" : nodes.get(0).id;
    }

    public Way way(String a, String b) {
        for (Way p : ways) if (p.joins(a, b)) return p;
        return null;
    }

    /** The paths that touch destination {@code id}. */
    public List<Way> waysAt(String id) {
        List<Way> out = new ArrayList<>();
        for (Way p : ways) if (p.other(id) != null) out.add(p);
        return out;
    }

    /** Join two destinations (by way of {@code via}, from the first), or - already joined - part them. */
    public Way link(String a, String b, List<int[]> via) {
        if (a.equals(b) || node(a) == null || node(b) == null) return null;
        Way had = way(a, b);
        if (had != null) {
            ways.remove(had);
            return null;
        }
        Way p = new Way(a, b);
        if (via != null) for (int[] v : via) p.via.add(v.clone());
        ways.add(p);
        return p;
    }

    /**
     * The points a path goes through, in tiles (a tile's middle), from the
     * destination {@code from}: its own middle, each bend, the other end's.
     */
    public List<float[]> points(Way p, String from) {
        List<float[]> out = new ArrayList<>();
        Node a = node(p.from), b = node(p.to);
        if (a == null || b == null) return out;
        out.add(new float[]{a.x + 0.5f, a.y + 0.5f});
        for (int[] v : p.via) out.add(new float[]{v[0] + 0.5f, v[1] + 0.5f});
        out.add(new float[]{b.x + 0.5f, b.y + 0.5f});
        if (p.to.equals(from)) java.util.Collections.reverse(out);
        return out;
    }

    // --- what she may do ---------------------------------------------------------------

    /** Whether she may walk on from {@code n}: it is open, or she has cleared it. */
    public boolean passable(Node n, Set<String> cleared) {
        return n != null && (n.open || !n.playable() || cleared.contains(n.id));
    }

    /**
     * Whether she may walk {@code p} from {@code from}: onward from a place
     * she may pass - or, from one she may not pass yet, only back towards
     * where she could have come from (a place she can reach without going
     * through this one).
     */
    public boolean walkable(Way p, String from, Set<String> cleared) {
        Node a = node(from), b = node(p.other(from));
        if (a == null || b == null) return false;
        return passable(a, cleared) || reachable(b, cleared, a.id);
    }

    /** Whether she can get to {@code n} at all yet: from the start, by paths she may walk. */
    public boolean reachable(Node n, Set<String> cleared) {
        return reachable(n, cleared, null);
    }

    /** {@link #reachable(Node, Set)} without going through the destination {@code avoid}. */
    private boolean reachable(Node n, Set<String> cleared, String avoid) {
        Node s = start();
        if (s == null || n == null || s.id.equals(avoid)) return false;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<String> open = new java.util.ArrayDeque<>();
        open.add(s.id);
        while (!open.isEmpty()) {
            String at = open.poll();
            if (at.equals(avoid) || !seen.add(at)) continue;
            if (at.equals(n.id)) return true;
            if (!passable(node(at), cleared)) continue;         // (she can stand here, but not go on)
            for (Way p : waysAt(at)) open.add(p.other(at));
        }
        return false;
    }

    /**
     * The path she takes from {@code from} when she pushes the way {@code
     * (dx, dy)} (right and down positive): the one that sets off most nearly
     * that way, or null when none goes anything like it.
     */
    public Way toward(String from, double dx, double dy) {
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) return null;
        Way best = null;
        double bestDot = 0.45;
        for (Way p : waysAt(from)) {
            List<float[]> pts = points(p, from);
            if (pts.size() < 2) continue;
            double px = pts.get(1)[0] - pts.get(0)[0], py = pts.get(1)[1] - pts.get(0)[1];
            double pl = Math.hypot(px, py);
            if (pl < 1e-6) continue;
            double dot = (px * dx + py * dy) / (pl * len);
            if (dot > bestDot) {
                bestDot = dot;
                best = p;
            }
        }
        return best;
    }

    /**
     * The destinations she walks through from {@code from} to {@code to}, by
     * ways she may walk (the fewest of them), {@code from} first; empty when
     * she cannot get there.
     */
    public List<String> route(String from, String to, Set<String> cleared) {
        Map<String, String> came = new LinkedHashMap<>();
        java.util.ArrayDeque<String> open = new java.util.ArrayDeque<>();
        open.add(from);
        came.put(from, "");
        while (!open.isEmpty()) {
            String at = open.poll();
            if (at.equals(to)) break;
            for (Way p : waysAt(at)) {
                String next = p.other(at);
                if (came.containsKey(next) || !walkable(p, at, cleared)) continue;
                came.put(next, at);
                open.add(next);
            }
        }
        List<String> out = new ArrayList<>();
        if (!came.containsKey(to)) return out;
        for (String at = to; !at.isEmpty(); at = came.get(at)) out.add(0, at);
        return out;
    }

    // --- the file ----------------------------------------------------------------------

    public Map<String, Object> json() {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("name", name);
        j.put("width", (double) width);
        j.put("height", (double) height);
        j.put("start", start);
        j.put("music", music);
        if (!background.isEmpty()) {
            j.put("background", background);
            j.put("tilesShown", tilesShown);
        }
        if (!roadStyle.equals("dotted")) j.put("roadStyle", roadStyle);
        if (smoothRoads) j.put("smoothRoads", true);
        List<Object> rows = new ArrayList<>();
        for (char[] row : tiles) rows.add(new String(row));
        j.put("tiles", rows);
        List<Object> ns = new ArrayList<>();
        for (Node n : nodes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.id);
            m.put("name", n.name);
            m.put("kind", n.kind);
            m.put("x", (double) n.x);
            m.put("y", (double) n.y);
            m.put("level", n.level);
            m.put("open", n.open);
            m.put("about", n.about);
            m.put("clear", n.clear);
            if (!n.quest.isBlank()) m.put("quest", n.quest);
            if (!n.toMap.isBlank()) m.put("toMap", n.toMap);
            ns.add(m);
        }
        j.put("nodes", ns);
        List<Object> ps = new ArrayList<>();
        for (Way p : ways) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", p.from);
            m.put("to", p.to);
            List<Object> via = new ArrayList<>();
            for (int[] v : p.via) via.add(List.of((double) v[0], (double) v[1]));
            m.put("via", via);
            ps.add(m);
        }
        j.put("paths", ps);
        return j;
    }

    @SuppressWarnings("unchecked")
    public static Overworld parse(Map<String, Object> j) {
        Overworld o = new Overworld(Json.str(j, "name", "A map"), (int) Json.num(j, "width", 40),
                (int) Json.num(j, "height", 24));
        if (j.get("tiles") instanceof List<?> rows) {
            for (int y = 0; y < Math.min(rows.size(), o.height); y++) {
                String row = String.valueOf(rows.get(y));
                for (int x = 0; x < Math.min(row.length(), o.width); x++) o.tiles[y][x] = terrain(row.charAt(x)).key();
            }
        }
        if (j.get("nodes") instanceof List<?> ns) {
            for (Object x : ns) {
                if (!(x instanceof Map<?, ?> mm)) continue;
                Map<String, Object> m = (Map<String, Object>) mm;
                String id = Json.str(m, "id", "");
                if (id.isEmpty() || o.node(id) != null) continue;
                Node n = new Node(id, Json.str(m, "name", id), (int) Json.num(m, "x", 0), (int) Json.num(m, "y", 0));
                n.kind = isKind(Json.str(m, "kind", "town")) ? Json.str(m, "kind", "town") : "town";
                n.level = Json.str(m, "level", "");
                n.open = Json.bool(m, "open", false);
                n.about = Json.str(m, "about", "");
                n.clear = CLEARS.contains(Json.str(m, "clear", "exit")) ? Json.str(m, "clear", "exit") : "exit";
                n.quest = Json.str(m, "quest", "");
                n.toMap = Json.str(m, "toMap", "");
                n.x = Math.max(0, Math.min(o.width - 1, n.x));
                n.y = Math.max(0, Math.min(o.height - 1, n.y));
                o.nodes.add(n);
            }
        }
        if (j.get("paths") instanceof List<?> ps) {
            for (Object x : ps) {
                if (!(x instanceof Map<?, ?> mm)) continue;
                Map<String, Object> m = (Map<String, Object>) mm;
                String a = Json.str(m, "from", ""), b = Json.str(m, "to", "");
                if (o.node(a) == null || o.node(b) == null || a.equals(b) || o.way(a, b) != null) continue;
                Way p = new Way(a, b);
                if (m.get("via") instanceof List<?> via) {
                    for (Object v : via) {
                        if (v instanceof List<?> xy && xy.size() >= 2 && xy.get(0) instanceof Number vx
                                && xy.get(1) instanceof Number vy) {
                            p.via.add(new int[]{vx.intValue(), vy.intValue()});
                        }
                    }
                }
                o.ways.add(p);
            }
        }
        o.start = Json.str(j, "start", "");
        o.setMusic(Json.str(j, "music", "shuffle"));
        o.setBackground(Json.str(j, "background", ""));
        o.tilesShown = Json.bool(j, "tilesShown", o.background.isEmpty());
        o.setRoadStyle(Json.str(j, "roadStyle", "dotted"));
        o.smoothRoads = Json.bool(j, "smoothRoads", false);
        return o;
    }

    /** Where the maps are kept, under {@code assets}. */
    public static Path folder(Path assets) {
        return assets.resolve(FOLDER).resolve(MAPS);
    }

    /** Where the main story's own map is kept, under {@code assets}: read, never written by the game. */
    public static Path storyFolder(Path assets) {
        return assets.resolve(FOLDER).resolve(STORY);
    }

    /** Where the pictures maps lie over are kept, under {@code assets}. */
    public static Path backgrounds(Path assets) {
        return assets.resolve(FOLDER).resolve(BACKGROUNDS);
    }

    /** Whether map {@code id} is the main story's own (kept in {@code story/}). */
    public static boolean isStory(Path assets, String id) {
        return id != null && !id.isBlank() && Files.isRegularFile(storyFolder(assets).resolve(id + ".json"));
    }

    /** The file map {@code id} is read from: the story's own first, then those made in the editor. */
    public static Path file(Path assets, String id) {
        Path story = storyFolder(assets).resolve(id + ".json");
        return Files.isRegularFile(story) ? story : folder(assets).resolve(id + ".json");
    }

    public static Overworld load(Path file) throws IOException {
        return parse(Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8))));
    }

    /** The map called {@code id} under {@code assets}, or null (none, or it cannot be read). */
    public static Overworld byId(Path assets, String id) {
        if (id == null || id.isBlank()) return null;
        Path f = file(assets, id);
        if (!Files.isRegularFile(f)) return null;
        try {
            Overworld o = load(f);
            o.story = isStory(assets, id);
            return o;
        } catch (IOException | RuntimeException e) {
            System.err.println("[overworld] cannot read " + f + ": " + e.getMessage());
            return null;
        }
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, Json.stringify(json()) + "\n", StandardCharsets.UTF_8);
    }

    /** The file that says which map a new game starts on. */
    public static Path campaignFile(Path assets) {
        return assets.resolve(FOLDER).resolve("campaign.json");
    }

    /**
     * The map a new game starts on unless another is chosen: the one {@code
     * campaign.json} names, else the main story's, else the first there is;
     * "" when there is none.
     */
    public static String campaignStart(Path assets) {
        try {
            Path f = campaignFile(assets);
            if (Files.isRegularFile(f)) {
                String id = Json.str(Json.asObject(Json.parse(Files.readString(f, StandardCharsets.UTF_8))), "start", "");
                if (!id.isBlank() && Files.isRegularFile(file(assets, id))) return id;
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[overworld] campaign.json: " + e.getMessage());
        }
        List<Saved> all = list(assets);
        return all.isEmpty() ? "" : all.get(0).id();
    }

    /** A new game starts on map {@code id} from now on. */
    public static void setCampaignStart(Path assets, String id) throws IOException {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("start", id);
        Files.createDirectories(campaignFile(assets).getParent());
        Files.writeString(campaignFile(assets), Json.stringify(j) + "\n", StandardCharsets.UTF_8);
    }

    /** A map on file: its id (the file's name), what it is called, and whether it is the main story's. */
    public record Saved(String id, String name, Path file, boolean story) {
        /** What it is called in a list: "Main story: ..." for the story's own. */
        public String shown() { return story ? "Main story: " + name : name; }
    }

    /** Every map under {@code assets}: the main story's first, then the rest by file name. */
    public static List<Saved> list(Path assets) {
        List<Saved> out = new ArrayList<>();
        listInto(out, storyFolder(assets), true);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Saved s : out) ids.add(s.id());
        List<Saved> made = new ArrayList<>();
        listInto(made, folder(assets), false);
        for (Saved s : made) if (!ids.contains(s.id())) out.add(s);
        return out;
    }

    private static void listInto(List<Saved> out, Path dir, boolean story) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                try {
                    Map<String, Object> j = Json.asObject(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
                    String file = f.getFileName().toString();
                    out.add(new Saved(file.substring(0, file.length() - 5), Json.str(j, "name", file), f, story));
                } catch (IOException | RuntimeException e) {
                    System.err.println("[overworld] skipping " + f + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[overworld] cannot list " + dir + ": " + e.getMessage());
        }
    }

    /** An id for a map called {@code name} that no map under {@code assets} has (the story's included). */
    public static String freeId(Path assets, String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "map";
        String id = base;
        for (int i = 2; Files.exists(folder(assets).resolve(id + ".json"))
                || Files.exists(storyFolder(assets).resolve(id + ".json")); i++) {
            id = base + "_" + i;
        }
        return id;
    }
}
