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
 * assets/overworld/maps/<id>.json}:
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
    /** The marks' places in {@code icons.png}. */
    public static final int ICON_CLEARED = KINDS.size(), ICON_LOCKED = KINDS.size() + 1, ICON_HERE = KINDS.size() + 2;

    public static final int SMALLEST = 12, WIDEST = 96, TALLEST = 64;
    public static final String FOLDER = "overworld", MAPS = "maps";

    /** A destination. */
    public static final class Node {
        public final String id;
        public String name, kind = "town", level = "", about = "";
        public int x, y;
        public boolean open;

        public Node(String id, String name, int x, int y) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
        }

        /** Its icon's place in {@code icons.png}. */
        public int icon() {
            return Math.max(0, KINDS.indexOf(kind));
        }

        /** Whether choosing it loads anything. */
        public boolean playable() { return level != null && !level.isBlank(); }
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
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "place";
        String id = base;
        for (int i = 2; node(id) != null; i++) id = base + "_" + i;
        Node n = new Node(id, name, x, y);
        nodes.add(n);
        if (nodes.size() == 1) start = id;
        return n;
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
     * she may pass, or back to one she may.
     */
    public boolean walkable(Way p, String from, Set<String> cleared) {
        Node a = node(from), b = node(p.other(from));
        return a != null && b != null && (passable(a, cleared) || passable(b, cleared));
    }

    /** Whether she can get to {@code n} at all yet: from the start, by paths she may walk. */
    public boolean reachable(Node n, Set<String> cleared) {
        Node s = start();
        if (s == null || n == null) return false;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<String> open = new java.util.ArrayDeque<>();
        open.add(s.id);
        while (!open.isEmpty()) {
            String at = open.poll();
            if (!seen.add(at)) continue;
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

    // --- the file ----------------------------------------------------------------------

    public Map<String, Object> json() {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("name", name);
        j.put("width", (double) width);
        j.put("height", (double) height);
        j.put("start", start);
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
                n.kind = KINDS.contains(Json.str(m, "kind", "town")) ? Json.str(m, "kind", "town") : "town";
                n.level = Json.str(m, "level", "");
                n.open = Json.bool(m, "open", false);
                n.about = Json.str(m, "about", "");
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
        return o;
    }

    /** Where the maps are kept, under {@code assets}. */
    public static Path folder(Path assets) {
        return assets.resolve(FOLDER).resolve(MAPS);
    }

    public static Overworld load(Path file) throws IOException {
        return parse(Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8))));
    }

    /** The map called {@code id} under {@code assets}, or null (none, or it cannot be read). */
    public static Overworld byId(Path assets, String id) {
        Path f = folder(assets).resolve(id + ".json");
        if (id == null || id.isBlank() || !Files.isRegularFile(f)) return null;
        try {
            return load(f);
        } catch (IOException | RuntimeException e) {
            System.err.println("[overworld] cannot read " + f + ": " + e.getMessage());
            return null;
        }
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, Json.stringify(json()) + "\n", StandardCharsets.UTF_8);
    }

    /** A map on file: its id (the file's name) and what it is called. */
    public record Saved(String id, String name, Path file) {}

    /** Every map under {@code assets}, by name. */
    public static List<Saved> list(Path assets) {
        List<Saved> out = new ArrayList<>();
        Path dir = folder(assets);
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                try {
                    Map<String, Object> j = Json.asObject(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
                    String file = f.getFileName().toString();
                    out.add(new Saved(file.substring(0, file.length() - 5), Json.str(j, "name", file), f));
                } catch (IOException | RuntimeException e) {
                    System.err.println("[overworld] skipping " + f + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[overworld] cannot list " + dir + ": " + e.getMessage());
        }
        return out;
    }

    /** An id for a map called {@code name} that no map under {@code assets} has. */
    public static String freeId(Path assets, String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "map";
        String id = base;
        for (int i = 2; Files.exists(folder(assets).resolve(id + ".json")); i++) id = base + "_" + i;
        return id;
    }
}
