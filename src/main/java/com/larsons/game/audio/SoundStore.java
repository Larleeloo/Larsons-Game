package com.larsons.game.audio;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists the creator's sound assignments as {@code sounds.json} in the
 * sound pack folder ({@code assets/sounds/sounds.json}) — copied from
 * Larsons-Game-Engine, which keeps it beside its skins.
 *
 * <p>Only the <em>exceptions</em> live here: which sounds point at a file
 * outside the pack's naming, and any per-sound volume/pitch/loop set for
 * them. The audio itself stays in the {@link SoundPack} folder under the
 * names {@code SOUND_KEYS.txt} lists. The file is optional; without it every
 * sound is the pack's.
 */
public final class SoundStore {

    public static final String FILE_NAME = "sounds.json";

    private final Path dir;

    /** The store in the sound pack folder in use. */
    public SoundStore() {
        this(SoundPack.root());
    }

    public SoundStore(Path dir) {
        this.dir = dir;
    }

    public Path directory() {
        return dir;
    }

    public Path file() {
        return dir.resolve(FILE_NAME);
    }

    /** Load every saved assignment; an absent/unreadable file is just "none". */
    public List<SoundDef> load() {
        List<SoundDef> out = new ArrayList<>();
        if (!Files.isRegularFile(file())) return out;
        try {
            Map<String, Object> root = Json.asObject(Json.parse(Files.readString(file())));
            if (root.get("sounds") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) out.add(SoundDef.fromMap(Json.asObject(m)));
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[sound] unreadable " + file() + " (" + e.getMessage() + ")");
        }
        return out;
    }

    /** Persist the full assignment set (the file is rewritten wholesale). */
    public Path save(List<SoundDef> sounds) {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Object> list = new ArrayList<>(sounds.size());
        for (SoundDef s : sounds) list.add(s.toMap());
        root.put("sounds", list);
        try {
            Files.createDirectories(dir);
            Files.writeString(file(), Json.stringify(root));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file();
    }
}
