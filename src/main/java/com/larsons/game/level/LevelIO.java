package com.larsons.game.level;

import com.larsons.game.util.Json;
import com.larsons.game.world.GroundMap;
import com.larsons.game.world.Sky;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Levels on disk: a folder each under {@code assets/levels/}, named for the
 * level when it was made ({@link #slug}), holding
 *
 * <pre>
 *   level.json   its name, size, sky and backdrops, and every object - one a line
 *   height.png   its sculpted ground, 16-bit grey: a height a pixel, every 0.5 m
 *                (value / 256 - 128 metres), the north-west corner first
 *   ground.png   what its ground is made of, a pixel every 0.25 m: red dirt, green
 *                forest floor, blue gravel, alpha flower meadow (none: grass)
 * </pre>
 *
 * <p>The images are left out while there is nothing on them (flat ground,
 * all grass). {@code level.json} is meant to be read - and diffed in git:
 *
 * <pre>
 * {
 *   "format": 1,
 *   "name": "The old mill",
 *   "size": 128,
 *   "sky": {"zenith": "#5C78A3", "horizon": "#BDC9DB", "fog": 70, "backdrops": [...]},
 *   "objects": [
 *     {"id": 1, "type": "spawn", "x": 0, "z": 0, "yaw": 0},
 *     {"id": 2, "type": "scenery", "x": 6.5, "z": -8, "yaw": 37.5, "sprite": "oak_tree"},
 *     {"id": 3, "type": "item", "x": 1.4, "z": -2.2, "yaw": 0, "item": "sword", "tier": "basic"}
 *   ]
 * }
 * </pre>
 *
 * <p>An object's {@code yaw} is in degrees; whatever else it has is its
 * kind's props. Keys and kinds this game does not know are read and written
 * back as they were.
 */
public final class LevelIO {

    public static final String FILE = "level.json", HEIGHTS = "height.png", GROUND = "ground.png";
    /** A height's 16-bit value is {@code (metres + OFFSET) * SCALE}. */
    static final double OFFSET = 128, SCALE = 256;

    private static final Set<String> KNOWN = Set.of("format", "name", "size", "sky", "objects");
    private static final Set<String> OBJECT_KEYS = Set.of("id", "type", "x", "z", "lift", "yaw");

    private LevelIO() {}

    // --- the folder of levels -------------------------------------------------------------

    /** A saved level: its folder and its name. */
    public record Saved(Path folder, String name, double size, long modified) {

        /** The folder's name: the level's id among the others. */
        public String id() { return folder.getFileName().toString(); }
    }

    /** Every level in {@code root} (a folder with a {@code level.json} in it), the last changed first. */
    public static List<Saved> list(Path root) {
        List<Saved> out = new ArrayList<>();
        if (!Files.isDirectory(root)) return out;
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path d : dirs.filter(Files::isDirectory).toList()) {
                Path f = d.resolve(FILE);
                if (!Files.isRegularFile(f)) continue;
                try {
                    Map<String, Object> j = Json.asObject(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
                    out.add(new Saved(d, Json.str(j, "name", d.getFileName().toString()),
                            Json.num(j, "size", Level.DEFAULT_SIZE), Files.getLastModifiedTime(f).toMillis()));
                } catch (IOException | RuntimeException e) {
                    System.err.println("[levels] skipping " + f + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[levels] cannot list " + root + ": " + e.getMessage());
        }
        out.sort(Comparator.comparingLong(Saved::modified).reversed());
        return out;
    }

    /** A folder name for a level called {@code name}: lower case, words joined by underscores. */
    public static String slug(String name) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "level" : s.length() > 48 ? s.substring(0, 48) : s;
    }

    /** A folder in {@code root} not yet taken, for a new level called {@code name}. */
    public static Path freeFolder(Path root, String name) {
        String base = slug(name);
        Path p = root.resolve(base);
        for (int i = 2; Files.exists(p); i++) p = root.resolve(base + "_" + i);
        return p;
    }

    /** A name not yet taken in {@code root}: {@code base}, or "base 2", "base 3"... */
    public static String freeName(Path root, String base) {
        List<Saved> all = list(root);
        String name = base;
        for (int i = 2; ; i++) {
            String n = name;
            if (all.stream().noneMatch(s -> s.name().equalsIgnoreCase(n))) return name;
            name = base + " " + i;
        }
    }

    /** Delete the level in {@code folder}: its files, then the folder if nothing else is in it. */
    public static void delete(Path folder) throws IOException {
        for (String f : List.of(FILE, HEIGHTS, GROUND)) Files.deleteIfExists(folder.resolve(f));
        try (Stream<Path> rest = Files.list(folder)) {
            if (rest.findAny().isEmpty()) Files.delete(folder);
        }
    }

    // --- reading ---------------------------------------------------------------------------

    /** The level saved in {@code folder}. */
    public static Level load(Path folder) throws IOException {
        Map<String, Object> j = Json.asObject(Json.parse(Files.readString(folder.resolve(FILE),
                StandardCharsets.UTF_8)));
        double size = sizeOf(Json.num(j, "size", Level.DEFAULT_SIZE));
        Level level = new Level(Json.str(j, "name", folder.getFileName().toString()), size);
        Object sky = j.get("sky");
        if (sky instanceof Map<?, ?>) level.setSky(sky(Json.asObject(sky)));
        int highest = 0;
        Object objs = j.get("objects");
        if (objs instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?>)) continue;
                LevelObject o = object(Json.asObject(item));
                if (o == null) continue;
                level.objects().add(o);
                highest = Math.max(highest, o.id());
            }
        }
        level.setNextId(highest + 1);
        for (Map.Entry<String, Object> e : j.entrySet()) {
            if (!KNOWN.contains(e.getKey())) level.extra().put(e.getKey(), e.getValue());
        }
        Path hp = folder.resolve(HEIGHTS);
        if (Files.isRegularFile(hp)) readHeights(hp, level.heights());
        Path gp = folder.resolve(GROUND);
        if (Files.isRegularFile(gp)) readGround(gp, level.ground());
        return level;
    }

    /** The nearest size a level can be to {@code s}. */
    static double sizeOf(double s) {
        int best = Level.DEFAULT_SIZE;
        for (int v : Level.SIZES) if (Math.abs(v - s) < Math.abs(best - s)) best = v;
        return best;
    }

    static Sky sky(Map<String, Object> j) {
        List<Sky.Backdrop> backdrops = new ArrayList<>();
        Object list = j.get("backdrops");
        if (list instanceof List<?> l) {
            for (Object o : l) {
                if (!(o instanceof Map<?, ?>)) continue;
                Map<String, Object> b = Json.asObject(o);
                Sky.Kind kind = Sky.Kind.byKey(Json.str(b, "kind", ""));
                if (kind == null) continue;
                Sky.Backdrop d = Sky.Backdrop.of(kind);
                backdrops.add(new Sky.Backdrop(kind, colour(Json.str(b, "colour", ""), d.colour()),
                        Json.num(b, "height", d.height()), Json.num(b, "haze", d.haze()),
                        (int) Json.num(b, "seed", d.seed()), Json.num(b, "from", 0), Json.num(b, "to", 0),
                        Json.num(b, "distance", d.distance()), Json.bool(b, "snow", d.snow())));
            }
        }
        return new Sky(colour(Json.str(j, "zenith", ""), Sky.DEFAULT.zenith()),
                colour(Json.str(j, "horizon", ""), Sky.DEFAULT.horizon()), Json.num(j, "fog", Sky.DEFAULT.fog()),
                backdrops);
    }

    /** {@code "#RRGGBB"} as a colour, or {@code fallback}. */
    static int colour(String hex, int fallback) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return h.length() == 6 ? Integer.parseInt(h, 16) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static LevelObject object(Map<String, Object> j) {
        String type = Json.str(j, "type", "");
        if (type.isEmpty()) return null;
        Map<String, Object> props = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : j.entrySet()) {
            if (!OBJECT_KEYS.contains(e.getKey())) props.put(e.getKey(), e.getValue());
        }
        return new LevelObject((int) Json.num(j, "id", 0), type, Json.num(j, "x", 0), Json.num(j, "z", 0),
                Json.num(j, "lift", 0), Math.toRadians(Json.num(j, "yaw", 0)), props);
    }

    static void readHeights(Path file, HeightField into) throws IOException {
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) throw new IOException("not an image: " + file);
        int n = into.points();
        float[] h = new float[n * n];
        var raster = img.getRaster();
        for (int j = 0; j < Math.min(n, img.getHeight()); j++) {
            for (int i = 0; i < Math.min(n, img.getWidth()); i++) {
                int v = raster.getNumBands() > 0 ? raster.getSample(i, j, 0) : 0;
                if (img.getSampleModel().getSampleSize(0) == 8) v = v << 8 | v;
                h[j * n + i] = (float) (v / SCALE - OFFSET);
            }
        }
        into.setHeights(h);
    }

    static void readGround(Path file, GroundMap into) throws IOException {
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) throw new IOException("not an image: " + file);
        int n = into.cells();
        byte[] rgba = new byte[n * n * 4];
        var raster = img.getRaster();
        int bands = raster.getNumBands();
        for (int r = 0; r < Math.min(n, img.getHeight()); r++) {
            for (int c = 0; c < Math.min(n, img.getWidth()); c++) {
                int k = (r * n + c) * 4;
                for (int b = 0; b < 4; b++) rgba[k + b] = (byte) (b < bands ? raster.getSample(c, r, b) : 0);
            }
        }
        into.setRgba(rgba);
    }

    // --- writing ---------------------------------------------------------------------------

    /** Save {@code level} into {@code folder} (made if need be), replacing what was there. */
    public static void save(Level level, Path folder) throws IOException {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(FILE), json(level), StandardCharsets.UTF_8);
        Path hp = folder.resolve(HEIGHTS);
        if (level.heights().flat()) Files.deleteIfExists(hp);
        else writeHeights(level.heights(), hp);
        Path gp = folder.resolve(GROUND);
        if (level.ground().isEmpty()) Files.deleteIfExists(gp);
        else writeGround(level.ground(), gp);
    }

    /** {@code level.json}'s text for {@code level}: the header pretty, each object on a line of its own. */
    public static String json(Level level) {
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("format", Level.FORMAT);
        head.put("name", level.name());
        head.put("size", round(level.size()));
        head.put("sky", sky(level.sky()));
        for (Map.Entry<String, Object> e : level.extra().entrySet()) head.putIfAbsent(e.getKey(), tidy(e.getValue()));
        String text = Json.stringify(head);
        StringBuilder sb = new StringBuilder(text.substring(0, text.lastIndexOf('}')).stripTrailing());
        sb.append(",\n  \"objects\": [");
        List<LevelObject> objs = level.objects();
        for (int i = 0; i < objs.size(); i++) {
            sb.append(i == 0 ? "\n    " : ",\n    ").append(line(object(objs.get(i))));
        }
        sb.append(objs.isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
        return sb.toString();
    }

    /** {@code j} as JSON on one line, a space after each colon and comma. */
    static String line(Map<String, Object> j) {
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, Object> e : j.entrySet()) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(Json.stringifyCompact(e.getKey())).append(": ").append(Json.stringifyCompact(e.getValue()));
        }
        return sb.append('}').toString();
    }

    static Map<String, Object> sky(Sky s) {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("zenith", hex(s.zenith()));
        j.put("horizon", hex(s.horizon()));
        j.put("fog", round(s.fog()));
        List<Object> list = new ArrayList<>();
        for (Sky.Backdrop b : s.backdrops()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", b.kind().key());
            m.put("colour", hex(b.colour()));
            m.put("height", round(b.height()));
            m.put("haze", round(b.haze()));
            m.put("seed", b.seed());
            m.put("from", round(b.from()));
            m.put("to", round(b.to()));
            if (b.kind() == Sky.Kind.OCEAN) m.put("distance", round(b.distance()));
            if (b.kind() == Sky.Kind.MOUNTAINS) m.put("snow", b.snow());
            list.add(m);
        }
        j.put("backdrops", list);
        return j;
    }

    static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    static Map<String, Object> object(LevelObject o) {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("id", o.id());
        j.put("type", o.type());
        j.put("x", round(o.x()));
        j.put("z", round(o.z()));
        if (o.lift() != 0) j.put("lift", round(o.lift()));
        j.put("yaw", round(Math.toDegrees(o.yaw()), 100));
        for (Map.Entry<String, Object> e : o.props().entrySet()) j.put(e.getKey(), tidy(e.getValue()));
        return j;
    }

    /** {@code v} as written: its numbers - in lists and objects too - to the millimetre, whole ones without ".0". */
    static Object tidy(Object v) {
        if (v instanceof Double d) return round(d);
        if (v instanceof Float f) return round(f);
        if (v instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object x : list) out.add(tidy(x));
            return out;
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), tidy(e.getValue()));
            return out;
        }
        return v;
    }

    /** {@code v} to the millimetre - a whole number when it is one. */
    static Number round(double v) {
        return round(v, 1000);
    }

    static Number round(double v, double per) {
        double r = Math.round(v * per) / per;
        if (r == Math.rint(r) && Math.abs(r) < 1e15) return (long) r;
        return r;
    }

    static void writeHeights(HeightField h, Path file) throws IOException {
        int n = h.points();
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_USHORT_GRAY);
        short[] data = ((DataBufferUShort) img.getRaster().getDataBuffer()).getData();
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                long v = Math.round((h.at(i, j) + OFFSET) * SCALE);
                data[j * n + i] = (short) Math.max(0, Math.min(65535, v));
            }
        }
        write(img, file);
    }

    static void writeGround(GroundMap g, Path file) throws IOException {
        int n = g.cells();
        byte[] rgba = g.rgba();
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_4BYTE_ABGR);
        var raster = img.getRaster();
        int[] px = new int[4];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                int k = (r * n + c) * 4;
                for (int b = 0; b < 4; b++) px[b] = rgba[k + b] & 0xFF;
                raster.setPixel(c, r, px);
            }
        }
        write(img, file);
    }

    private static void write(BufferedImage img, Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        if (!ImageIO.write(img, "png", tmp.toFile())) throw new IOException("no PNG writer");
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** {@link #load}, for code that cannot throw. */
    public static Level loadUnchecked(Path folder) {
        try {
            return load(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
