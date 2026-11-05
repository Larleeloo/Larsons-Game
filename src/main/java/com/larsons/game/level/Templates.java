package com.larsons.game.level;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Templates: anything built in Creative Mode kept to place again, in this
 * level or any other - a building with its rooms and its furniture, a
 * market stall and its barrels, a camp, a fenced paddock with its horses.
 * Select it, name it and save it (the Select tool); it is in Place →
 * Templates from then on, placed - turned with R / Q / E - like anything
 * else, every piece where it stood round the others.
 *
 * <p>One file each in {@code assets/templates/}, named for it ({@link
 * LevelIO#slug}):
 *
 * <pre>
 * {
 *   "format": 1,
 *   "name": "Miller's cottage",
 *   "objects": [ ...each as in a level.json, x and z from the template's middle... ],
 *   "interiors": { "12": [ ...building 12's floors, as in a level.json... ] }
 * }
 * </pre>
 */
public final class Templates {

    public static final String FOLDER = "templates";
    static final int FORMAT = 1;

    /**
     * A saved template: its id (the file's name), its name, its pieces - each
     * where it stands from the middle, facing as it did - and the floors
     * inside its buildings, by the piece's number, as written.
     */
    public record Template(String id, String name, List<LevelObject> objects,
                           Map<Integer, List<Map<String, Object>>> interiors) {

        /** How far from its middle its furthest piece reaches, metres. */
        public double reach() {
            double r = 1;
            for (LevelObject o : objects) {
                double own = o.type().equals(ObjectTypes.SCENERY) ? 1 : ObjectTypes.radius(o, null);
                r = Math.max(r, Math.hypot(o.x(), o.z()) + own);
            }
            return r;
        }

        /** Fresh floors for the building that is piece {@code id} (null: it has none of its own). */
        public List<Level> floors(int id) {
            List<Map<String, Object>> written = interiors.get(id);
            if (written == null) return null;
            List<Level> out = new ArrayList<>();
            for (Map<String, Object> f : written) out.add(LevelIO.floor(f, out.size()));
            return out;
        }
    }

    private static Path dir = Path.of("assets", FOLDER);
    private static List<Template> all;

    private Templates() {}

    /** Keep the templates in {@code folder} from now on (tests: a folder of their own). */
    public static synchronized void useDir(Path folder) {
        dir = folder;
        all = null;
    }

    /** The folder they are kept in. */
    public static synchronized Path dir() { return dir; }

    /** Every saved template, by name. */
    public static synchronized List<Template> all() {
        if (all == null) all = readAll(dir);
        return List.copyOf(all);
    }

    /** The template saved as {@code id}, or null. */
    public static synchronized Template byId(String id) {
        for (Template t : all()) if (t.id().equals(id)) return t;
        return null;
    }

    /**
     * Save {@code objects} of {@code level} as a template called {@code name}
     * (one of that name already: saved over), the floors inside its
     * buildings with it. Returns it.
     */
    public static synchronized Template save(String name, Collection<LevelObject> objects, Level level)
            throws IOException {
        if (objects.isEmpty()) throw new IOException("nothing to save");
        String title = name == null || name.isBlank() ? "Template" : name.strip();
        List<LevelObject> kept = new ArrayList<>();
        for (LevelObject o : objects) {
            ObjectType t = ObjectTypes.get(o.type());
            if (t == null || !t.single()) kept.add(o);              // (not the spawn: a level has one)
        }
        double cx = 0, cz = 0;
        for (LevelObject o : kept) {
            cx += o.x() / kept.size();
            cz += o.z() / kept.size();
        }
        List<LevelObject> pieces = new ArrayList<>();
        Map<Integer, List<Map<String, Object>>> interiors = new LinkedHashMap<>();
        for (LevelObject o : kept) {
            pieces.add(o.copy().moveTo(o.x() - cx, o.z() - cz));
            List<Level> floors = level == null ? null : level.interior(o.id());
            if (floors != null && !floors.isEmpty()) interiors.put(o.id(), written(floors));
        }
        if (pieces.isEmpty()) throw new IOException("nothing that can be placed again");
        Files.createDirectories(dir);
        Template old = null;
        for (Template t : all()) if (t.name().equalsIgnoreCase(title)) old = t;
        String id = old != null ? old.id() : free(LevelIO.slug(title));
        Template made = new Template(id, title, List.copyOf(pieces), interiors);
        Files.writeString(dir.resolve(id + ".json"), json(made), StandardCharsets.UTF_8);
        all = null;
        return made;
    }

    /** Throw away the template saved as {@code id}. */
    public static synchronized void delete(String id) throws IOException {
        Files.deleteIfExists(dir.resolve(id + ".json"));
        all = null;
    }

    /**
     * What placing {@code placed} (a {@link ObjectTypes#TEMPLATE}) puts in
     * the level: the template's pieces, turned as it is turned round where
     * it stands, numbered from {@code firstId} in the template's order.
     */
    public static List<LevelObject> expand(LevelObject placed, int firstId) {
        Template t = byId(placed.str("template", ""));
        if (t == null) return List.of();
        double c = Math.cos(placed.yaw()), s = Math.sin(placed.yaw());
        List<LevelObject> out = new ArrayList<>();
        int id = firstId;
        for (LevelObject p : t.objects()) {
            // turned as headings turn (SpriteView#headingVector): at yaw 0 just as it was saved
            double x = placed.x() + p.x() * c + p.z() * s, z = placed.z() - p.x() * s + p.z() * c;
            LevelObject piece = p.copy(id++).moveTo(x, z).setLift(p.lift() + placed.lift());
            out.add(piece.turnTo(p.yaw() + placed.yaw()));
        }
        return out;
    }

    // --- on disk -------------------------------------------------------------------------------------

    static String json(Template t) {
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("format", FORMAT);
        head.put("name", t.name());
        if (!t.interiors().isEmpty()) {
            Map<String, Object> ins = new LinkedHashMap<>();
            for (Map.Entry<Integer, List<Map<String, Object>>> e : t.interiors().entrySet()) {
                ins.put(String.valueOf(e.getKey()), e.getValue());
            }
            head.put("interiors", ins);
        }
        String text = Json.stringify(head);
        StringBuilder sb = new StringBuilder(text.substring(0, text.lastIndexOf('}')).stripTrailing());
        sb.append(",\n  \"objects\": [");
        for (int i = 0; i < t.objects().size(); i++) {
            sb.append(i == 0 ? "\n    " : ",\n    ").append(LevelIO.line(LevelIO.object(t.objects().get(i))));
        }
        return sb.append("\n  ]\n}\n").toString();
    }

    private static List<Map<String, Object>> written(List<Level> floors) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Level f : floors) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.name());
            List<Object> objs = new ArrayList<>();
            for (LevelObject o : f.objects()) objs.add(LevelIO.object(o));
            m.put("objects", objs);
            out.add(m);
        }
        return out;
    }

    static Template read(Path file) throws IOException {
        Map<String, Object> j = Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
        String id = file.getFileName().toString().replaceFirst("\\.json$", "");
        List<LevelObject> objects = new ArrayList<>();
        if (j.get("objects") instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?>)) continue;
                LevelObject o = LevelIO.object(Json.asObject(item));
                if (o != null) objects.add(o);
            }
        }
        Map<Integer, List<Map<String, Object>>> interiors = new LinkedHashMap<>();
        if (j.get("interiors") instanceof Map<?, ?> ins) {
            for (Map.Entry<?, ?> e : ins.entrySet()) {
                if (!(e.getValue() instanceof List<?> floors)) continue;
                List<Map<String, Object>> fs = new ArrayList<>();
                for (Object f : floors) if (f instanceof Map<?, ?>) fs.add(Json.asObject(f));
                try {
                    interiors.put(Integer.parseInt(String.valueOf(e.getKey())), fs);
                } catch (NumberFormatException x) {
                    // (not a building's number: left out)
                }
            }
        }
        return new Template(id, Json.str(j, "name", id), List.copyOf(objects), interiors);
    }

    private static List<Template> readAll(Path folder) {
        List<Template> out = new ArrayList<>();
        if (!Files.isDirectory(folder)) return out;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                try {
                    Template t = read(f);
                    if (!t.objects().isEmpty()) out.add(t);
                } catch (IOException | RuntimeException e) {
                    System.err.println("[templates] skipping " + f + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[templates] cannot list " + folder + ": " + e.getMessage());
        }
        out.sort(Comparator.comparing(t -> t.name().toLowerCase()));
        return out;
    }

    private static String free(String base) {
        String id = base;
        for (int i = 2; Files.exists(dir.resolve(id + ".json")); i++) id = base + "_" + i;
        return id;
    }
}
