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
 *   ├── ui/                   inventory_open.mp3, inventory_close.mp3, hotbar_select.mp3
 *   ├── combat/attack_dummy/  hit.mp3, fall.mp3, rise.mp3
 *   └── combat/               dice_roll.mp3, blow.mp3, arrow_hit.mp3, spell_cast.mp3 …
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
    /** Counts the times the pack was moved or rescanned, so what was resolved from it can be forgotten. */
    private static volatile int generation;

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
        generation++;
    }

    /** Changes each time the pack is moved or rescanned ({@link #reload}). */
    public static int generation() {
        return generation;
    }

    // --- lookup ---------------------------------------------------------------------

    /**
     * The pack file backing {@code key} - its own, or the first of what it
     * falls back to ({@link SoundKeys#chain}) - or {@code null} when there is
     * none.
     */
    public static Path fileFor(String key) {
        for (String k : SoundKeys.chain(key)) {
            Path p = fileAt(k);
            if (p != null) return p;
        }
        return null;
    }

    /** The pack file that is {@code key}'s own ({@link SoundKeys#file}), fallbacks aside, or {@code null}. */
    public static synchronized Path fileAt(String key) {
        settings();          // a moved pack drops both caches together
        Path root = configRoot;
        if (FILES.containsKey(key)) return FILES.get(key);
        Path found = null;
        if (Files.isDirectory(root)) {
            String rel = SoundKeys.file(key);
            for (String ext : EXTENSIONS) {
                Path candidate = root.resolve(rel + ext);
                if (Files.isRegularFile(candidate)) {
                    found = candidate;
                    break;
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

                Every sound the game makes is a file in this folder, named after
                what it is the sound of. MP3 or WAV (also AIFF and AU); MP3 is
                read by the game's own decoder, so no codec is needed.

                The files here now are **placeholders**, made in code by
                `tools/sounds/make_placeholders.py` - replace any of them with a
                real recording of the same name. You can also pick your own file
                for any sound in the game without touching this folder: Pause →
                Sounds, one tab per category (footsteps and movement, voices,
                weapons, hits and magic, creatures, world and loot, ambient,
                interface), each sound with a play button, Choose…, Mute and
                Reset. Those choices are yours alone (`config/sounds.json`).

                ## Naming

                Each folder is an object, and each file is named after the
                action it plays on:

                    player/footstep_grass.mp3         a footstep on grass (each foot, as it comes down)
                    player/attack.mp3                 a sword swing (once, as it starts)
                    player/axe_attack.mp3             the battle-axe chop
                    player/feminine/emote_laugh.mp3   her laugh
                    player/masculine/hurt.mp3         his grunt when he is hurt
                    chests/open.mp3                   a chest's lid thrown open
                    chests/opened.mp3                 a chest standing open and lit (held)
                    items/sword/equip.mp3             taking the sword in hand
                    combat/attack_dummy/hit.mp3       a training dummy struck
                    combat/dice_roll.mp3              the damage dice tumbling over her head
                    world/loot_legendary.mp3          a Legendary item landing out of a chest
                    ambient/wind.mp3                  the breeze over the meadow (held)
                    ui/click.mp3                      any button clicked

                A file in a body's or an object's own folder is that one's alone
                and comes first; one folder up covers every one of its kind:
                `player/masculine/attack.mp3` is his sword swing,
                `player/attack.mp3` both bodies'; `chests/ornate_chest/open.mp3`
                that chest's, `chests/open.mp3` every chest's.

                A move without a file of its own sounds like the move it is a
                version of: the axe's heavy attack like the axe's attack, a bow
                shot from the crouch like the standing one.

                A held sound (a chest standing open, the breeze, a flier
                flapping) loops for as long as what it is the sound of lasts;
                anything else plays once as it starts - so record a loop as one
                seamless loop, and an attack from its first frame.

                `%s` lists the exact name of every sound in the game, by
                category.

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

                Each category's volume, and the master volume, are in Pause →
                Sounds. The game picks up new files here the next time it starts
                (or at once, with Rescan in the Sounds menu).
                """.formatted(KEYS_FILE, CONFIG_FILE, DEFAULT_PITCH_VARIATION,
                DEFAULT_PITCH_VARIATION * 100, CONFIG_FILE);
    }

    /** The generated key list: every sound by category, its file name, and its sound key. */
    static String keysText() {
        StringBuilder sb = new StringBuilder();
        sb.append("LARSON'S GAME — SOUND KEYS\n");
        sb.append("==========================\n\n");
        sb.append("Every sound the game can make, the file to name your audio, and the\n");
        sb.append("sound key behind it, by category (the tabs of Pause → Sounds). Drop an\n");
        sb.append("MP3 (or WAV) at the listed path in this folder and the game plays it from\n");
        sb.append("its next launch. The shared sounds come with placeholders; a body's or an\n");
        sb.append("object's own file, when there is one, plays instead of the shared one.\n");
        sb.append("(held) sounds loop for as long as what they are the sound of lasts; the\n");
        sb.append("rest play once as it starts.\n");
        sb.append("Generated by the game from its catalogue (audio/SoundKeys.java).\n");

        Map<SoundCategory, List<SoundKeys.Entry>> shared = new LinkedHashMap<>(), own = new LinkedHashMap<>();
        for (SoundCategory c : SoundCategory.values()) {
            shared.put(c, new ArrayList<>());
            own.put(c, new ArrayList<>());
        }
        for (SoundKeys.Entry e : SoundKeys.all()) (e.listed() ? shared : own).get(e.category()).add(e);
        for (SoundCategory c : SoundCategory.values()) {
            sb.append("\n\n").append(c.label().toUpperCase()).append("\n");
            sb.append("-".repeat(c.label().length())).append("\n");
            sb.append(c.blurb()).append("\n\n");
            for (SoundKeys.Entry e : shared.get(c)) line(sb, e);
            if (!own.get(c).isEmpty()) {
                sb.append("\n  One body's or one object's own (silent until given a file):\n\n");
                for (SoundKeys.Entry e : own.get(c)) line(sb, e);
            }
        }
        return sb.toString();
    }

    private static void line(StringBuilder sb, SoundKeys.Entry e) {
        String name = e.name() + (e.looping() ? " (held)" : "");
        sb.append(String.format("  %-46s %-48s %s", e.file() + ".mp3", name, e.key())).append('\n');
    }
}
