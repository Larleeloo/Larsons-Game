package com.larsons.game.audio;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The game's sound assets: one folder of audio files, sorted into subfolders
 * named after the thing they belong to, that gives the game its voice
 * <em>by file name</em> — no code or menu visit required.
 *
 * <pre>
 *   assets/sounds/
 *   ├── soundpack.json        volume, pitch variation (+ per-sound overrides)
 *   ├── SOUND_KEYS.txt        every sound the game can make and the file to name it
 *   ├── README.md
 *   ├── player/feminine/      walk.mp3, run.mp3, axe_attack.mp3 … (her 56 animations)
 *   ├── player/masculine/     … and his
 *   ├── chests/ornate_chest/  idle.mp3, open.mp3, opened.mp3, close.mp3
 *   ├── items/&lt;item&gt;/        pickup.mp3, drop.mp3, equip.mp3
 *   └── ui/                   inventory_open.mp3, inventory_close.mp3, hotbar_select.mp3
 * </pre>
 *
 * <p>Copied from Larsons-Game-Engine's {@code SoundPack}, which keeps its pack
 * beside the jar; the game's lives in {@code assets/sounds/} beside the
 * sprites ({@link #useDir}, set from {@code -Dlarsons.assets}), so it is in
 * the repository, and an object's sounds have a folder of their own
 * ({@link SoundKeys}).
 *
 * <p><b>WAV and MP3.</b> {@code .mp3}, {@code .wav}, {@code .aif(f)} and
 * {@code .au} all load — MP3 through the game's own {@link Mp3Decoder},
 * everything else through the JDK. A sound key with no file in the pack is
 * silent, so a pack can be one file or a hundred.
 *
 * <p>Each sound may carry its own volume, pitch and loop setting, saved into
 * the pack's {@code soundpack.json} so the exception travels with the folder.
 */
public final class SoundPack {

    /** The pack's folder under the assets folder. */
    public static final String DIR_NAME = "sounds";

    public static final String CONFIG_FILE = "soundpack.json";
    public static final String KEYS_FILE = "SOUND_KEYS.txt";
    public static final String README_FILE = "README.md";

    /** Pack-wide default playback volume. */
    public static final double DEFAULT_VOLUME = 1.0;
    /** Pack-wide default playback pitch (1 = as recorded). */
    public static final double DEFAULT_PITCH = 1.0;
    /**
     * How far a sound's pitch drifts either way, as a fraction, when the
     * "fresh pitch" option is on. Minecraft-ish: enough that a run of
     * footsteps never sounds like the same click twice, small enough that
     * nothing sounds broken.
     */
    public static final double DEFAULT_PITCH_VARIATION = 0.08;
    /** Ceiling on the pitch-variation setting. */
    public static final double MAX_PITCH_VARIATION = 0.5;

    /** Audio types a pack file may use, in lookup order. */
    private static final String[] EXTENSIONS = {".mp3", ".wav", ".aiff", ".aif", ".au"};

    /** How one sound plays: its level, its pitch, and whether it repeats. */
    public record Playback(double volume, double pitch, boolean loop, boolean varyPitch) {
        public Playback {
            volume = clamp(volume, 0, 4);
            pitch = clamp(pitch, 0.25, 4);
        }
    }

    /** The pack's defaults plus its per-sound exceptions. */
    private record Config(Playback defaults, double pitchVariation,
                          Map<String, Playback> overrides) {}

    private static volatile Path dir = Path.of("assets", DIR_NAME);
    private static Config config;
    private static Path configRoot;
    /** Resolved audio file per sound key ({@code null} = none in the pack). */
    private static final Map<String, Path> FILES = new HashMap<>();

    private SoundPack() {}

    /** Clamp used across the audio package's settings records. */
    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    // --- location -------------------------------------------------------------------

    /**
     * Point the pack at a folder ({@code <assets>/sounds}). Changing it drops
     * the resolved sounds so the new pack applies live.
     */
    public static void useDir(Path folder) {
        if (folder == null || folder.equals(dir)) return;
        dir = folder;
        reload();
    }

    /** The folder currently searched for audio (it need not exist yet). */
    public static Path root() {
        return dir;
    }

    /** True when the pack folder is actually there to read sounds from. */
    public static boolean exists() {
        return Files.isDirectory(root());
    }

    /**
     * Re-read the config and re-scan for audio — what picks up files added to
     * the folder while the game is running. The decoded-clip cache is dropped
     * too, so a replaced file is heard rather than remembered.
     */
    public static void reload() {
        forgetFiles();
        SoundLoader.clearCache();
    }

    private static synchronized void forgetFiles() {
        config = null;
        configRoot = null;
        FILES.clear();
    }

    // --- lookup ---------------------------------------------------------------------

    /** The pack file backing {@code key}, or {@code null} when there is none. */
    public static synchronized Path fileFor(String key) {
        settings();          // a moved pack drops both caches together
        Path root = configRoot;
        if (FILES.containsKey(key)) return FILES.get(key);
        Path found = null;
        if (Files.isDirectory(root)) {
            search:
            for (String rel : SoundKeys.paths(key)) {
                for (String ext : EXTENSIONS) {
                    Path candidate = root.resolve(rel + ext);
                    if (Files.isRegularFile(candidate)) {
                        found = candidate;
                        break search;
                    }
                }
            }
        }
        FILES.put(key, found);
        return found;
    }

    /** The file name a creator should give {@code key}, e.g. {@code player/feminine/walk.mp3}. */
    public static String fileNameFor(String key) {
        return SoundKeys.preferredFile(key);
    }

    // --- settings -------------------------------------------------------------------

    /**
     * How far pitch drifts either way when the fresh-pitch option is on, as a
     * fraction of the sound's own pitch. Saved with the pack so the chosen
     * feel travels with the folder.
     */
    public static synchronized double pitchVariation() {
        return settings().pitchVariation();
    }

    /** Set the pack's pitch spread; {@code 0} makes every playback identical. */
    public static void setPitchVariation(double amount) {
        synchronized (SoundPack.class) {
            double v = clamp(amount, 0, MAX_PITCH_VARIATION);
            Config c = settings();
            if (v == c.pitchVariation()) return;
            config = new Config(c.defaults(), v, c.overrides());
            writeConfig();
        }
    }

    /** The settings {@code key} plays at: its own override, else the defaults. */
    public static synchronized Playback playbackFor(String key) {
        Config c = settings();
        Playback own = c.overrides().get(key);
        if (own != null) return own;
        // A held animation repeats for as long as it holds; everything else fires once.
        Playback d = c.defaults();
        return SoundKeys.isLooping(key) ? new Playback(d.volume(), d.pitch(), true, d.varyPitch()) : d;
    }

    /** Whether {@code key} departs from the pack's defaults. */
    public static synchronized boolean hasOverride(String key) {
        return settings().overrides().containsKey(key);
    }

    /**
     * Give one sound its own volume/pitch/loop, saved into the pack's
     * {@code soundpack.json} so the exception travels with the folder.
     * Settings equal to what the key would play at anyway clear the override.
     */
    public static synchronized void setOverride(String key, double volume, double pitch,
                                                boolean loop, boolean varyPitch) {
        Config c = settings();
        Playback p = new Playback(volume, pitch, loop, varyPitch);
        c.overrides().remove(key);
        if (p.equals(playbackFor(key))) {   // back to the inherited setting
            writeConfig();
            return;
        }
        c.overrides().put(key, p);
        writeConfig();
    }

    /** Put {@code key} back on the pack's defaults. */
    public static synchronized void clearOverride(String key) {
        Config c = settings();
        if (c.overrides().remove(key) != null) writeConfig();
    }

    private static Config settings() {
        Path root = root();
        if (config == null || !root.equals(configRoot)) {
            FILES.clear();
            configRoot = root;
            config = readConfig(root);
        }
        return config;
    }

    private static Config readConfig(Path root) {
        Playback defaults = new Playback(DEFAULT_VOLUME, DEFAULT_PITCH, false, true);
        double variation = DEFAULT_PITCH_VARIATION;
        Map<String, Playback> overrides = new LinkedHashMap<>();
        Path file = root.resolve(CONFIG_FILE);
        if (!Files.isRegularFile(file)) return new Config(defaults, variation, overrides);
        try {
            Map<String, Object> m = Json.asObject(Json.parse(Files.readString(file)));
            defaults = playback(m, defaults);
            if (m.get("pitchVariation") instanceof Number n) variation = n.doubleValue();
            if (m.get("overrides") instanceof Map<?, ?> raw) {
                for (Map.Entry<?, ?> e : raw.entrySet()) {
                    if (e.getValue() instanceof Map<?, ?> v) {
                        overrides.put(String.valueOf(e.getKey()),
                                playback(Json.asObject(v), defaults));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[sound] unreadable " + file + " (" + e.getMessage()
                    + ") — using the pack defaults");
        }
        return new Config(defaults, clamp(variation, 0, MAX_PITCH_VARIATION), overrides);
    }

    /** Read one playback spec, inheriting anything the map leaves out. */
    private static Playback playback(Map<String, Object> m, Playback inherit) {
        return new Playback(
                m.get("volume") instanceof Number n ? n.doubleValue() : inherit.volume(),
                m.get("pitch") instanceof Number n ? n.doubleValue() : inherit.pitch(),
                m.get("loop") instanceof Boolean b ? b : inherit.loop(),
                m.get("varyPitch") instanceof Boolean b ? b : inherit.varyPitch());
    }

    private static void writeConfig() {
        Path root = root();
        try {
            Files.createDirectories(root);
            Files.writeString(root.resolve(CONFIG_FILE), configJson(settings()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String configJson(Config c) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("volume", c.defaults().volume());
        root.put("pitch", c.defaults().pitch());
        root.put("varyPitch", c.defaults().varyPitch());
        root.put("pitchVariation", c.pitchVariation());
        Map<String, Object> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, Playback> e : c.overrides().entrySet()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("volume", e.getValue().volume());
            p.put("pitch", e.getValue().pitch());
            p.put("loop", e.getValue().loop());
            p.put("varyPitch", e.getValue().varyPitch());
            overrides.put(e.getKey(), p);
        }
        root.put("overrides", overrides);
        return Json.stringify(root);
    }

    // --- scaffolding ----------------------------------------------------------------

    /**
     * Create (or bring up to date) the pack's storage at {@code root}: one
     * folder per object, a README, and a {@code SOUND_KEYS.txt} listing every
     * sound the game can make and the file name to give it. The README and
     * key list are rewritten only when what they say has changed, so the key
     * list tracks the game's catalogue without touching the repository on
     * every launch; an existing {@code soundpack.json} is left alone so
     * nobody's settings are clobbered. The game calls it at start-up.
     */
    public static Path scaffold(Path root) throws IOException {
        Files.createDirectories(root);
        for (String folder : SoundKeys.folders()) {
            Files.createDirectories(root.resolve(folder));
        }
        writeIfChanged(root.resolve(README_FILE), readmeText());
        writeIfChanged(root.resolve(KEYS_FILE), keysText());
        Path cfg = root.resolve(CONFIG_FILE);
        if (!Files.exists(cfg)) {
            Files.writeString(cfg, configJson(new Config(
                    new Playback(DEFAULT_VOLUME, DEFAULT_PITCH, false, true),
                    DEFAULT_PITCH_VARIATION, new LinkedHashMap<>())));
        }
        return root;
    }

    private static void writeIfChanged(Path file, String text) throws IOException {
        if (Files.isRegularFile(file) && Files.readString(file).equals(text)) return;
        Files.writeString(file, text);
    }

    /** The README the pack folder carries. */
    static String readmeText() {
        return """
                # Sounds

                Drop audio files in this folder and the game plays them. Every
                animation in the game is a sound slot - both bodies' 56 states,
                the treasure chest's four, what is done with an item, the
                inventory - and **every one is silent until a file with its
                name is here**. MP3 or WAV (also AIFF and AU); MP3 is read by the
                game's own decoder, so no codec is needed.

                ## Naming

                Each folder is an object, and each file is named after the
                action it plays on:

                    player/feminine/walk.mp3          her walk (held: repeats while she walks)
                    player/feminine/attack.mp3        her sword swing (once, as it starts)
                    player/masculine/axe_attack.mp3   his battle-axe chop
                    chests/ornate_chest/idle.mp3      the chest shut, its smoke swirling (held)
                    chests/ornate_chest/open.mp3      the lid thrown open (once)
                    chests/ornate_chest/opened.mp3    the chest standing open and lit (held)
                    chests/ornate_chest/close.mp3     the lid slammed (once)
                    items/battle_axe/pickup.mp3       picking the battle axe up
                    ui/inventory_open.mp3             the inventory opening (I)

                A file one folder up covers every object of its kind until an
                object has its own: `player/walk.mp3` is both bodies' walk,
                `chests/open.mp3` every chest's opening, `items/pickup.mp3`
                picking anything up.

                A held animation's sound (a walk, an idle, the chest standing
                open) loops for as long as the animation holds; anything else
                plays once as its animation starts - so record a walk as one
                seamless loop, and an attack from its first frame.

                `%s` lists the exact name of every sound in the game.

                ## Fresh pitch

                Every sound plays at a slightly different pitch each time - the
                trick Minecraft uses so a run of footsteps never sounds like a
                stuck record. The spread is `pitchVariation` in `%s`
                (%.2f = plus or minus %.0f%%); set it to 0 to play every sound
                exactly as recorded.

                ## Settings

                `%s` holds the volume and pitch the whole pack plays at, and an
                `overrides` block for single sounds:

                    "overrides": {
                      "chest/ornate_chest/idle": { "volume": 0.4 },
                      "player/feminine/run":     { "pitch": 1.1, "varyPitch": false }
                    }

                The game picks up new files the next time it starts.
                """.formatted(KEYS_FILE, CONFIG_FILE, DEFAULT_PITCH_VARIATION,
                DEFAULT_PITCH_VARIATION * 100, CONFIG_FILE);
    }

    /** The generated key list: every sound, its file name, and its sound key. */
    static String keysText() {
        StringBuilder sb = new StringBuilder();
        sb.append("LARSON'S GAME — SOUND KEYS\n");
        sb.append("==========================\n\n");
        sb.append("Every sound the game can make, the file to name your audio, and the\n");
        sb.append("sound key behind it. Drop an MP3 (or WAV) at the listed path in this\n");
        sb.append("folder and the game plays it from its next launch. Anything you do\n");
        sb.append("not supply is SILENT. (held) sounds loop for as long as their\n");
        sb.append("animation holds; the rest play once as it starts.\n");
        sb.append("Generated by the game from its catalogue (audio/SoundKeys.java).\n");

        Map<String, List<SoundKeys.Entry>> byCategory = new LinkedHashMap<>();
        for (SoundKeys.Entry e : SoundKeys.all()) {
            byCategory.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<String, List<SoundKeys.Entry>> group : byCategory.entrySet()) {
            List<SoundKeys.Entry> entries = group.getValue();
            sb.append("\n\n").append(group.getKey().toUpperCase())
                    .append("  ->  ").append(entries.get(0).folder()).append("/\n\n");
            for (SoundKeys.Entry e : entries) {
                String name = e.name() + (SoundKeys.isLooping(e.key()) ? " (held)" : "");
                sb.append(String.format("  %-44s %-30s %s%n", e.file() + ".mp3", name, e.key()));
            }
        }
        return sb.toString();
    }
}
