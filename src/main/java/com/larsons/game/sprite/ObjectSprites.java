package com.larsons.game.sprite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The sprite sheets of things in the world that are not characters - a
 * treasure chest - drawn like a character, from the same 24 views, as a
 * stack of layers, but with states of their own rather than {@link
 * AnimState}s:
 *
 * <pre>
 *   assets/sprites/objects/&lt;object&gt;/profile.json                       framing, fps
 *   assets/sprites/objects/&lt;object&gt;/&lt;layer&gt;/&lt;state&gt;_&lt;elevation&gt;_&lt;direction&gt;.png
 *   assets/sprites/objects/ornate_chest/chest/open_middle_se.png
 *   assets/sprites/objects/ornate_chest/smoke_front/swirl_top_n.png
 * </pre>
 *
 * <p>A state is any name ({@code idle}, {@code open}, {@code swirl}); the
 * elevation and direction are the character's ({@link Elevation}, {@link
 * Facing}). As with the characters, a missing west-facing sheet borrows its
 * east-facing twin, mirrored. The sheets load through the {@link
 * SpriteLibrary} like any other ({@link SpriteLibrary#sheet}), within its
 * memory budget.
 */
public final class ObjectSprites {

    /** The folder under the sprites folder. */
    public static final String FOLDER = "objects";

    /** One object: its framing and, per layer, its sheets by {@code <state>_<elevation>_<direction>}. */
    public record Entry(String name, SpriteProfile profile, Map<String, Map<String, Path>> layers) {}

    private final Path root;
    private volatile Map<String, Entry> objects = Map.of();

    /** The objects under {@code <sprites>/objects/}. */
    public ObjectSprites(Path spritesRoot) {
        this.root = spritesRoot.resolve(FOLDER);
        rescan();
    }

    public Path root() { return root; }

    /** Re-read the folder: file names only, no pixels. */
    public synchronized void rescan() {
        Map<String, Entry> next = new TreeMap<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> dirs = Files.list(root)) {
                for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                    next.put(dir.getFileName().toString(), scan(dir));
                }
            } catch (IOException e) {
                System.err.println("[sprites] cannot list " + root + ": " + e.getMessage());
            }
        }
        objects = Collections.unmodifiableMap(next);
    }

    private static Entry scan(Path dir) {
        SpriteProfile profile = SpriteProfile.load(dir, SpriteProfile.defaults());
        Map<String, Map<String, Path>> layers = new TreeMap<>();
        try (Stream<Path> subs = Files.list(dir)) {
            for (Path layer : subs.filter(Files::isDirectory).sorted().toList()) {
                Map<String, Path> sheets = new TreeMap<>();
                try (Stream<Path> files = Files.list(layer)) {
                    for (Path f : files.filter(Files::isRegularFile).sorted().toList()) {
                        String key = sheetKey(f.getFileName().toString());
                        if (key != null) sheets.put(key, f);
                    }
                }
                layers.put(layer.getFileName().toString(), Collections.unmodifiableMap(sheets));
            }
        } catch (IOException e) {
            System.err.println("[sprites] cannot list " + dir + ": " + e.getMessage());
        }
        return new Entry(dir.getFileName().toString(), profile, Collections.unmodifiableMap(layers));
    }

    /**
     * {@code <state>_<elevation>_<direction>} for a canonical sheet name
     * ({@code open_middle_se.png}), or null for anything else.
     */
    static String sheetKey(String fileName) {
        String lower = fileName.toLowerCase();
        if (!lower.endsWith(".png")) return null;
        String[] parts = lower.substring(0, lower.length() - 4).split("_");
        if (parts.length < 3) return null;
        Facing f = Facing.byKey(parts[parts.length - 1]);
        Elevation e = Elevation.byKey(parts[parts.length - 2]);
        if (f == null || e == null) return null;
        String state = String.join("_", java.util.Arrays.copyOf(parts, parts.length - 2));
        return key(state, e, f);
    }

    static String key(String state, Elevation e, Facing f) {
        return state + "_" + e.key() + "_" + f.key();
    }

    /** The object folder {@code name}, or null. */
    public Entry entry(String name) {
        return objects.get(name);
    }

    /** Every object found, by name. */
    public List<String> names() {
        return List.copyOf(objects.keySet());
    }

    /** An object's framing (the default framing for an unknown one). */
    public SpriteProfile profile(String object) {
        Entry e = entry(object);
        return e == null ? SpriteProfile.defaults() : e.profile();
    }

    /**
     * The sheet for one view of one layer of an object in a state, or null:
     * its own, else its mirrored twin's, flipped.
     */
    public SpriteLibrary.Resolved resolve(String object, String layer, String state, Elevation elevation,
                                          Facing facing) {
        Entry e = entry(object);
        if (e == null) return null;
        Map<String, Path> sheets = e.layers().get(layer);
        if (sheets == null) return null;
        Path direct = sheets.get(key(state, elevation, facing));
        if (direct != null) return new SpriteLibrary.Resolved(direct, false, e.profile());
        if (facing.hasMirror()) {
            Path twin = sheets.get(key(state, elevation, facing.mirrorOf()));
            if (twin != null) return new SpriteLibrary.Resolved(twin, true, e.profile());
        }
        return null;
    }
}
