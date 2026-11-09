package com.larsons.game.editor;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The scatter brushes made in the editor, kept to use in any level: each
 * its name, the sprites it sows and how often each (a weight), the sizes
 * they come in (from, to), how far apart they keep beyond their own
 * footprints, and how thickly it sows. One file each, {@code
 * assets/brushes/<id>.json}, readable.
 */
final class ScatterBrushes {

    /** A sprite of a brush and how often it is sown, against the others' weights. */
    record Entry(String sprite, double weight) {}

    /** A brush. */
    record Brush(String id, String name, List<Entry> entries, double sizeFrom, double sizeTo, double spacing,
                 double thickness) {

        /** As a scatter set (what the brush sows). */
        Scatter.Set set() {
            List<String> sprites = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (Entry e : entries) {
                if (e.sprite() == null || e.sprite().isBlank() || e.weight() <= 0) continue;
                sprites.add(e.sprite());
                weights.add(e.weight());
            }
            return new Scatter.Set("★ " + name, sprites, weights, sizeFrom, sizeTo, spacing, id);
        }
    }

    static final String FOLDER = "brushes";
    private static Path dir = Path.of("assets", FOLDER);

    private ScatterBrushes() {}

    /** Keep them in {@code folder} from now on (tests: a folder of their own). */
    static void useDir(Path folder) {
        dir = folder;
    }

    /** Every brush saved, by name. */
    static List<Brush> all() {
        List<Brush> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                try {
                    out.add(read(f));
                } catch (IOException | RuntimeException e) {
                    System.err.println("[brushes] " + f.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[brushes] " + e.getMessage());
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    /** Save {@code b} (one of its id already: saved over); returns it as saved. */
    static Brush save(Brush b) throws IOException {
        String id = b.id() == null || b.id().isBlank() ? idOf(b.name()) : b.id();
        Brush made = new Brush(id, b.name() == null || b.name().isBlank() ? "Brush" : b.name().strip(),
                List.copyOf(b.entries()), b.sizeFrom(), Math.max(b.sizeFrom(), b.sizeTo()), b.spacing(),
                b.thickness());
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(id + ".json"), Json.stringify(toJson(made)), StandardCharsets.UTF_8);
        return made;
    }

    /** Take the brush {@code id} away. */
    static void delete(String id) throws IOException {
        if (id == null || id.isBlank()) return;
        Files.deleteIfExists(dir.resolve(id + ".json"));
    }

    /** A file name for a brush called {@code name}: its letters and digits, a number on the end if taken. */
    static String idOf(String name) {
        String base = (name == null ? "" : name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "brush";
        String id = base;
        for (int n = 2; Files.exists(dir.resolve(id + ".json")); n++) id = base + "_" + n;
        return id;
    }

    static Map<String, Object> toJson(Brush b) {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("name", b.name());
        List<Object> es = new ArrayList<>();
        for (Entry e : b.entries()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sprite", e.sprite());
            m.put("weight", e.weight());
            es.add(m);
        }
        j.put("sprites", es);
        j.put("sizeFrom", b.sizeFrom());
        j.put("sizeTo", b.sizeTo());
        j.put("spacing", b.spacing());
        j.put("thickness", b.thickness());
        return j;
    }

    static Brush read(Path file) throws IOException {
        Map<String, Object> j = Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
        String id = file.getFileName().toString().replaceFirst("\\.json$", "");
        List<Entry> es = new ArrayList<>();
        Object list = j.get("sprites");
        if (list instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    Object s = m.get("sprite"), w = m.get("weight");
                    es.add(new Entry(String.valueOf(s), w instanceof Number n ? n.doubleValue() : 1));
                } else if (o != null) {
                    es.add(new Entry(String.valueOf(o), 1));
                }
            }
        }
        return new Brush(id, Json.str(j, "name", id), es, Json.num(j, "sizeFrom", 1), Json.num(j, "sizeTo", 1),
                Json.num(j, "spacing", 0), Json.num(j, "thickness", 0.5));
    }
}
