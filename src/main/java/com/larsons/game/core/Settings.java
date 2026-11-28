package com.larsons.game.core;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Launch options ({@code -Dlarsons.*}, same prefix as the engine so the Gradle
 * build forwards them) and the player's saved preferences
 * ({@code config/settings.json}).
 *
 * <pre>
 *   -Dlarsons.gpu=required        warn loudly if the driver is a software rasteriser
 *   -Dlarsons.gpu.msaa=4          multisample anti-aliasing samples (0 = off), for this run (else Settings → Display)
 *   -Dlarsons.graphics=lite       Full or Lite graphics for this run, whatever the machine (else Settings → Display)
 *   -Dlarsons.gpu.vsync=on        wait for the display's refresh, for this run (else Pause → Settings → Vsync)
 *   -Dlarsons.fps=120             the frame rate to hold (0 = as fast as it goes; see FramePacer)
 *   -Dlarsons.fps.log=true        print the frame rate and frame times to the console twice a second
 *   -Dlarsons.perf.log=true       print the performance overlay's numbers to the console once a second
 *   -Dlarsons.sprites.scale=1     shrink sprite frames on load (0.5 = half size)
 *   -Dlarsons.sprites.vramMB=1536 video memory the sprite cache may use
 *   -Dlarsons.assets=assets       where the assets are (sprites/, scenery/, sounds/ ...)
 *   -Dlarsons.start=menu          start in "menu", straight in "demo", or "creative" (a new level),
 *                                 "edit:&lt;level&gt;" or "play:&lt;level&gt;" (a folder of assets/levels)
 *   -Dlarsons.inventory.slots=25  the player's inventory slots, the 5 of the hotbar included
 *   -Dlarsons.animations=classic  start with the first version of the weapon animations (or "new")
 *   -Dlarsons.script=...          scripted run for automated screenshots (see Autopilot)
 * </pre>
 */
public final class Settings {

    public final boolean gpuRequired;
    /**
     * Multisampling, samples (0: off): the window is made with it, so a change
     * shows at the next start. {@code -Dlarsons.gpu.msaa} sets it for one run;
     * {@link #setMsaa} is the player's (or the graphics profile's) choice.
     */
    public int msaa;
    private int savedMsaa = 4;
    private final boolean msaaGiven;
    /**
     * Wait for the display's refresh ({@link FramePacer}). {@code
     * -Dlarsons.gpu.vsync} sets it for one run without changing what is
     * saved; {@link #setVsync} is the player's choice (Pause → Settings).
     */
    public boolean vsync;
    private boolean savedVsync = true;
    private final boolean vsyncGiven;
    /** Video memory the sprite cache may use: {@code -Dlarsons.sprites.vramMB}, else the graphics profile's. */
    public long vramBytes;
    /** Whether {@link #vramBytes} was given on the command line (a profile leaves it alone then). */
    public final boolean vramGiven;
    public final Path assets;
    public final Path config;
    /** Where the sprites folders are: {@link #assets}, or the root of its sprite pack (found on first use). */
    private Path spriteRoot;
    public final String start;
    public final String script;
    /** How many slots the player's inventory has, the hotbar's five included. */
    public final int inventorySlots;

    // Saved between runs.
    public double spriteScale;
    /**
     * The frame rate the game holds to ({@link FramePacer}); 0: no cap but the
     * display's. {@code -Dlarsons.fps} overrides it for one run without
     * changing what is saved; {@link #setFps} is the player's choice.
     */
    public int fps;
    private int savedFps = FramePacer.DEFAULT_FPS;
    /** Print the frame numbers to the console ({@code -Dlarsons.fps.log}). */
    public final boolean logFrames;
    /** Print the performance overlay's numbers to the console once a second ({@code -Dlarsons.perf.log}). */
    public final boolean logPerf;
    public boolean showHud = true;
    /**
     * F7: the developer's overlay over the game - which sheets are on
     * screen, the frame times, the entity counts, and every key's help.
     */
    public boolean debugOverlay;
    /** The performance overlay (F8): frame times, draw calls, what the scene is running. */
    public boolean perfOverlay;
    /** Her class (Mage, Bard ... Cleric: {@link com.larsons.game.cards.CardBook}), "" before she has chosen one. */
    public String heroClass = "";
    /**
     * The mouse pointer: "class" (her class's: a wand for a Mage, a lute for
     * a Bard ...), one of the pointers by name ({@code assets/ui/pointers/<name>.png}),
     * "custom" ({@code assets/ui/pointers/custom.png}, any picture dropped in) or "system".
     */
    public String pointer = "class";
    /**
     * Show the scenery - the trees, grass tufts and rocks (P). Saved as
     * {@code showProps}, the name it had when they were 3D props.
     */
    public boolean showProps = true;
    /**
     * Let the camera's height follow the ground (O): higher and farther back
     * on a peak, lower in a hollow.
     */
    public boolean autoCamera = true;
    /**
     * How walls stand, as in the Sims ({@link com.larsons.game.building.Walls}):
     * up, cut away (those between the camera and her down to a stub) or all
     * down - rooms' walls indoors, buildings outdoors. Buildings stand by
     * default; rooms' walls are cut away.
     */
    public String wallsIndoors = "CUTAWAY";
    public String wallsOutdoors = "UP";

    public com.larsons.game.building.Walls indoors() {
        return com.larsons.game.building.Walls.of(wallsIndoors, com.larsons.game.building.Walls.CUTAWAY);
    }

    public com.larsons.game.building.Walls outdoors() {
        return com.larsons.game.building.Walls.of(wallsOutdoors, com.larsons.game.building.Walls.UP);
    }

    /**
     * Draw the first version of the states animated again (the weapon stances'
     * moves, kept in assets/animations_v1/) instead of the new one - to compare.
     */
    public boolean classicAnimations;
    /** How loud everything is, 0..1 (Pause → Sounds). */
    public double masterVolume = 1.0;
    /**
     * How loud each category of sounds is, 0..1, by the category's key
     * ({@code audio/SoundCategory}: movement, voices, weapons ...); one not
     * here plays at 1.
     */
    public final Map<String, Double> soundVolumes = new LinkedHashMap<>();
    /**
     * The debugging tools are on - every cosmetic offered, the pause menu's
     * "Clear the ground" - after the num-pad code ({@code scene/DebugCode}).
     * Not saved.
     */
    public boolean debugTools;
    /**
     * Dev mode: the main menu's Dev tab (Creative Mode, the levels, the map
     * and horde editors) and its Cutscene tab are shown, and every cosmetic
     * is unlocked - turned on with the dev code (1 0 2 5) on the main menu's
     * Codes page, and kept between runs until it is turned off there.
     */
    public boolean devUnlocked;
    /**
     * How the window is shown ({@code gfx/Window.Mode}): "windowed",
     * "borderless" (full screen without a mode switch) or "fullscreen" (the
     * monitor to itself) - put back at the next start.
     */
    public String displayMode = "windowed";
    /** The full screen F11 goes to: "borderless" or "fullscreen". */
    public String fullscreenKind = "borderless";
    /** Coins, ammunition and keys she walks over come to her by themselves. */
    public boolean autoPickup = true;
    /** How near (metres) they must be to come to her. */
    public double pickupRange = 2.5;
    /** The tutorial's tips come up in the game the first time each thing happens. */
    public boolean tutorialTips = true;
    /** The small map in the top corner, and the compass along the top. */
    public boolean miniMap = true, compass = true;
    /** The tips already shown, so each comes once ({@code scene/Tips}). */
    public final java.util.Set<String> tipsSeen = new java.util.LinkedHashSet<>();
    /** The pointer changes with what it is over (a button, someone to talk to, a foe); off: always the same. */
    public boolean dynamicPointer = true;
    /** The missions being followed are listed on the HUD. */
    public boolean missionTracker = true;
    /** As she sneaks, each foe's sight is drawn on the ground before it ({@code actor/Sight}). */
    public boolean foeSight = true;
    /** How fine the sun's shadows are ({@code gfx/Visuals.Shadows}: off, low, medium, high). */
    public String shadows = com.larsons.game.gfx.Visuals.DEFAULT.shadows().name();
    /** The Vibrant visuals look (warm sun, cool shade, richer colours, light rays), and how strong it is, 0 - 1. */
    public boolean vibrant = true;
    public double vibrantStrength = com.larsons.game.gfx.Visuals.DEFAULT.strength();
    /** With Vibrant visuals: shafts of light worked out from the sun and the shadows. */
    public boolean lightRays = true;
    private com.larsons.game.gfx.Visuals visuals;
    /**
     * The graphics ({@link GraphicsProfile}): "auto" - Lite on a low-end
     * machine, Full otherwise ({@link Hardware#verdict}) - or "full" or "lite"
     * whatever the machine. {@code -Dlarsons.graphics} sets it for one run.
     */
    public String graphics = "auto";
    /** Set for this run only by {@code -Dlarsons.graphics} (not saved). */
    private String graphicsGiven = "";
    /**
     * The profile "auto" last put into these settings ("full", "lite"; "" not
     * yet): it is put in once for a machine, and the player's changes after
     * that are kept - until the machine is found to be another kind.
     */
    public String graphicsApplied = "";
    /** The world's resolution, as a share of the window's pixels ({@code gfx/Quality#renderScale}): 1, 0.75, 0.5. */
    public double renderScale = 1;
    /** How far the world is seen through the haze, as a share of the level's own (1, 0.7, 0.5). */
    public double viewDistance = 1;
    /** How many critters, wandering animals and raindrops there are, as a share of what the place has (1, 0.5, 0.25). */
    public double wildlife = 1;
    private com.larsons.game.gfx.Quality quality;

    /** How much the machine is asked to draw, as Display has it. */
    public com.larsons.game.gfx.Quality quality() {
        if (quality == null || quality.renderScale() != clampScale(renderScale)
                || quality.viewDistance() != viewDistance || quality.life() != wildlife) {
            quality = new com.larsons.game.gfx.Quality(clampScale(renderScale), viewDistance, wildlife);
        }
        return quality;
    }

    private static double clampScale(double s) {
        return Math.max(0.25, Math.min(1, s));
    }

    /** The graphics chosen ("auto", "full" or "lite"): this run's {@code -Dlarsons.graphics}, else the saved choice. */
    public String graphicsChoice() {
        return graphicsGiven.isEmpty() ? graphics : graphicsGiven;
    }

    /** The profile to draw with on a machine {@code detected} suits: the one chosen, else the machine's. */
    public GraphicsProfile profile(GraphicsProfile detected) {
        return GraphicsProfile.of(graphicsChoice(), detected);
    }

    /** Whether {@code -Dlarsons.graphics=full} or {@code lite} picked the graphics for this run. */
    public boolean graphicsForced() {
        return GraphicsProfile.of(graphicsGiven, null) != null;
    }

    /** Whether the graphics are left to the machine ({@link #graphics} "auto"). */
    public boolean autoGraphics() {
        return GraphicsProfile.of(graphicsChoice(), null) == null;
    }

    /** The frame rate saved (what the player or the profile chose), whatever this run's {@code -Dlarsons.fps}. */
    public int savedFps() {
        return savedFps;
    }

    /** The multisampling saved, whatever this run's {@code -Dlarsons.gpu.msaa}. */
    public int savedMsaa() {
        return savedMsaa;
    }

    /** The player (or a profile) picks multisampling: saved, and used from the next start (the window has its own). */
    public void setMsaa(int samples) {
        savedMsaa = Math.max(0, Math.min(16, samples));
        if (!msaaGiven) msaa = savedMsaa;
    }

    /** Whether the multisampling was given on the command line for this run. */
    public boolean msaaGiven() {
        return msaaGiven;
    }

    /** How the world looks, as Display has it. */
    public com.larsons.game.gfx.Visuals visuals() {
        com.larsons.game.gfx.Visuals.Shadows sh = com.larsons.game.gfx.Visuals.Shadows.of(shadows,
                com.larsons.game.gfx.Visuals.Shadows.MEDIUM);
        if (visuals == null || visuals.shadows() != sh || visuals.vibrant() != vibrant
                || visuals.strength() != vibrantStrength || visuals.rays() != lightRays) {
            visuals = new com.larsons.game.gfx.Visuals(sh, vibrant, vibrantStrength, lightRays);
        }
        return visuals;
    }

    private Settings() {
        gpuRequired = "required".equalsIgnoreCase(System.getProperty("larsons.gpu", ""));
        msaa = intProperty("larsons.gpu.msaa", -1);
        msaaGiven = msaa >= 0;
        if (!msaaGiven) msaa = savedMsaa;
        String v = System.getProperty("larsons.gpu.vsync");
        vsyncGiven = v != null && !v.isBlank();
        vsync = !vsyncGiven || !isOff(v);
        int vramMB = intProperty("larsons.sprites.vramMB", -1);
        vramGiven = vramMB > 0;
        vramBytes = (long) (vramGiven ? vramMB : GraphicsProfile.FULL.vramMB) << 20;
        String g = System.getProperty("larsons.graphics", "").trim().toLowerCase(java.util.Locale.ROOT);
        graphicsGiven = g.equals("auto") || GraphicsProfile.of(g, null) != null ? g : "";
        assets = resolveAssets();
        config = Path.of("config");
        start = System.getProperty("larsons.start", "menu");
        script = System.getProperty("larsons.script", "");
        inventorySlots = Math.max(com.larsons.game.world.Inventory.HOTBAR,
                intProperty("larsons.inventory.slots", com.larsons.game.world.Inventory.DEFAULT_SLOTS));
        spriteScale = doubleProperty("larsons.sprites.scale", -1);
        fps = intProperty("larsons.fps", -1);
        logFrames = Boolean.getBoolean("larsons.fps.log");
        logPerf = Boolean.getBoolean("larsons.perf.log");
    }

    /** The settings a first start has, before any {@code settings.json} (the tests' own). */
    static Settings defaults() {
        return new Settings();
    }

    public static Settings load() {
        Settings s = new Settings();
        Path file = s.config.resolve("settings.json");
        boolean scaleGiven = s.spriteScale > 0;
        boolean fpsGiven = s.fps >= 0;
        if (Files.isRegularFile(file)) {
            try {
                Map<String, Object> j = Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
                if (!scaleGiven) s.spriteScale = Json.num(j, "spriteScale", 1.0);
                s.savedFps = Math.max(0, (int) Json.num(j, "fps", FramePacer.DEFAULT_FPS));
                s.showHud = Json.bool(j, "showHud", true);
                s.debugOverlay = Json.bool(j, "debugOverlay", false);
                s.perfOverlay = Json.bool(j, "perfOverlay", false);
                s.heroClass = Json.str(j, "heroClass", "");
                s.pointer = Json.str(j, "pointer", "class");
                s.showProps = Json.bool(j, "showProps", true);
                s.autoCamera = Json.bool(j, "autoCamera", true);
                // (before: one switch, "seeThrough" - off kept every wall whole)
                boolean see = Json.bool(j, "seeThrough", true);
                s.wallsIndoors = Json.str(j, "wallsIndoors", see ? "CUTAWAY" : "UP");
                s.wallsOutdoors = Json.str(j, "wallsOutdoors", "UP");
                s.classicAnimations = Json.bool(j, "classicAnimations", false);
                s.savedVsync = Json.bool(j, "vsync", true);
                s.masterVolume = Math.max(0, Math.min(1, Json.num(j, "masterVolume", 1.0)));
                if (j.get("soundVolumes") instanceof Map<?, ?> volumes) {
                    for (Map.Entry<?, ?> e : volumes.entrySet()) {
                        if (e.getValue() instanceof Number n) {
                            s.soundVolumes.put(String.valueOf(e.getKey()), Math.max(0, Math.min(1, n.doubleValue())));
                        }
                    }
                }
                s.devUnlocked = Json.bool(j, "devUnlocked", false);
                s.displayMode = Json.str(j, "displayMode", "windowed");
                s.fullscreenKind = Json.str(j, "fullscreenKind", "borderless");
                s.autoPickup = Json.bool(j, "autoPickup", true);
                s.pickupRange = Math.max(0.5, Math.min(8, Json.num(j, "pickupRange", 2.5)));
                s.tutorialTips = Json.bool(j, "tutorialTips", true);
                s.miniMap = Json.bool(j, "miniMap", true);
                s.compass = Json.bool(j, "compass", true);
                if (j.get("tipsSeen") instanceof java.util.List<?> seen) {
                    for (Object o : seen) s.tipsSeen.add(String.valueOf(o));
                }
                s.dynamicPointer = Json.bool(j, "dynamicPointer", true);
                s.missionTracker = Json.bool(j, "missionTracker", true);
                s.foeSight = Json.bool(j, "foeSight", true);
                s.shadows = Json.str(j, "shadows", s.shadows);
                s.vibrant = Json.bool(j, "vibrant", true);
                s.vibrantStrength = Math.max(0, Math.min(1, Json.num(j, "vibrantStrength", s.vibrantStrength)));
                s.lightRays = Json.bool(j, "lightRays", true);
                s.savedMsaa = Math.max(0, Math.min(16, (int) Json.num(j, "msaa", 4)));
                s.graphics = Json.str(j, "graphics", "auto");
                if (!s.graphics.equals("auto") && GraphicsProfile.of(s.graphics, null) == null) s.graphics = "auto";
                s.graphicsApplied = Json.str(j, "graphicsApplied", "");
                s.renderScale = clampScale(Json.num(j, "renderScale", 1));
                s.viewDistance = Math.max(0.3, Math.min(1, Json.num(j, "viewDistance", 1)));
                s.wildlife = Math.max(0, Math.min(1, Json.num(j, "wildlife", 1)));
            } catch (IOException | RuntimeException e) {
                System.err.println("[settings] ignoring " + file + ": " + e.getMessage());
            }
        }
        if (s.spriteScale <= 0) s.spriteScale = 1.0;
        if (!fpsGiven) s.fps = s.savedFps;
        if (!s.vsyncGiven) s.vsync = s.savedVsync;
        if (!s.msaaGiven) s.msaa = s.savedMsaa;
        String anim = System.getProperty("larsons.animations", "").trim().toLowerCase();
        if (anim.equals("classic") || anim.equals("v1")) s.classicAnimations = true;
        else if (anim.equals("new")) s.classicAnimations = false;
        return s;
    }

    public void save() {
        Map<String, Object> j = toJson();
        try {
            Files.createDirectories(config);
            Files.writeString(config.resolve("settings.json"), Json.stringify(j), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[settings] cannot save: " + e.getMessage());
        }
    }

    /** What {@link #save} writes. */
    Map<String, Object> toJson() {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("spriteScale", spriteScale);
        j.put("fps", savedFps);
        j.put("showHud", showHud);
        j.put("debugOverlay", debugOverlay);
        j.put("perfOverlay", perfOverlay);
        j.put("heroClass", heroClass);
        j.put("pointer", pointer);
        j.put("showProps", showProps);
        j.put("autoCamera", autoCamera);
        j.put("wallsIndoors", indoors().name());
        j.put("wallsOutdoors", outdoors().name());
        j.put("classicAnimations", classicAnimations);
        j.put("vsync", savedVsync);
        j.put("masterVolume", masterVolume);
        j.put("soundVolumes", new LinkedHashMap<>(soundVolumes));
        j.put("devUnlocked", devUnlocked);
        j.put("displayMode", displayMode);
        j.put("fullscreenKind", fullscreenKind);
        j.put("autoPickup", autoPickup);
        j.put("pickupRange", pickupRange);
        j.put("tutorialTips", tutorialTips);
        j.put("miniMap", miniMap);
        j.put("compass", compass);
        j.put("tipsSeen", new java.util.ArrayList<>(tipsSeen));
        j.put("dynamicPointer", dynamicPointer);
        j.put("missionTracker", missionTracker);
        j.put("foeSight", foeSight);
        j.put("shadows", shadows);
        j.put("vibrant", vibrant);
        j.put("vibrantStrength", vibrantStrength);
        j.put("lightRays", lightRays);
        j.put("msaa", savedMsaa);
        j.put("graphics", graphics);
        j.put("graphicsApplied", graphicsApplied);
        j.put("renderScale", renderScale);
        j.put("viewDistance", viewDistance);
        j.put("wildlife", wildlife);
        if (unforced != null) {
            // -Dlarsons.graphics was for this run: what it put in is saved as it was before (what the player
            // changed since is theirs)
            for (Map.Entry<String, Object> e : graphicsValues().entrySet()) {
                if (java.util.Objects.equals(e.getValue(), forced.get(e.getKey()))) {
                    j.put(e.getKey(), unforced.get(e.getKey()));
                }
            }
        }
        return j;
    }

    /** The graphics settings before {@code -Dlarsons.graphics} put its profile's in, and what it put in. */
    private Map<String, Object> unforced, forced;

    /**
     * Draw with {@code p}'s graphics for this run only ({@code
     * -Dlarsons.graphics}): put in now, but not saved - the next start
     * without it has the settings as they were.
     */
    public void force(GraphicsProfile p, double density) {
        Map<String, Object> before = graphicsValues();
        p.apply(this, density);
        if (unforced == null) unforced = before;
        forced = graphicsValues();
    }

    /** The player chose the graphics themselves (Settings, Display): saved from now on, whatever this run was given. */
    public void keepGraphics() {
        unforced = forced = null;
    }

    private Map<String, Object> graphicsValues() {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("shadows", shadows);
        j.put("vibrant", vibrant);
        j.put("vibrantStrength", vibrantStrength);
        j.put("lightRays", lightRays);
        j.put("fps", savedFps);
        j.put("msaa", savedMsaa);
        j.put("renderScale", renderScale);
        j.put("viewDistance", viewDistance);
        j.put("wildlife", wildlife);
        return j;
    }

    /** The player picks a frame rate (0: uncapped): used now, and saved. */
    public void setFps(int fps) {
        this.fps = this.savedFps = Math.max(0, fps);
    }

    /** The player turns vsync on or off: used now, and saved. */
    public void setVsync(boolean on) {
        vsync = savedVsync = on;
    }

    /** {@code config/wardrobe.json}. */
    public Path wardrobeFile() {
        return config.resolve("wardrobe.json");
    }

    /** {@code assets/sounds}: the sound pack ({@code audio/SoundPack}). */
    public Path soundsDir() {
        return assets.resolve("sounds");
    }

    /** {@code assets/levels}: the levels Creative Mode makes, a folder each ({@code level/LevelIO}). */
    public Path levelsDir() {
        return assets.resolve("levels");
    }

    /** {@code assets/horde}: the horde maps, each a level with the horde's rules in it, a folder each. */
    public Path hordeDir() {
        return assets.resolve("horde");
    }

    /**
     * {@code assets/sprites} - or, in a shared copy of the game, {@code
     * /sprites} inside {@code assets/sprites.pack} ({@link SpritePack}), with
     * the bodies' folders and the classic animations beside it there.
     */
    public synchronized Path spritesDir() {
        if (spriteRoot == null) spriteRoot = SpritePack.rootFor(assets);
        return spriteRoot.resolve("sprites");
    }

    /** {@code assets/scenery}: the trees, grass tufts and rocks, and the ground's grass. */
    public Path sceneryDir() {
        return assets.resolve(com.larsons.game.sprite.ScenerySprites.FOLDER);
    }

    /**
     * Where the assets folder is: the property if given, else {@code ./assets}
     * (the repository, under Gradle or IntelliJ), else beside the jar.
     */
    private static Path resolveAssets() {
        String prop = System.getProperty("larsons.assets");
        if (prop != null && !prop.isBlank()) return Path.of(prop).toAbsolutePath();
        Path local = Path.of("assets").toAbsolutePath();
        if (Files.isDirectory(local)) return local;
        try {
            Path jar = Path.of(Settings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path beside = (Files.isDirectory(jar) ? jar : jar.getParent()).resolve("assets");
            if (Files.isDirectory(beside)) return beside;
        } catch (Exception ignored) {
            // fall through to the working directory
        }
        return local;
    }

    private static int intProperty(String name, int fallback) {
        try {
            return Integer.parseInt(System.getProperty(name, "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double doubleProperty(String name, double fallback) {
        try {
            return Double.parseDouble(System.getProperty(name, "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean isOff(String v) {
        String s = v.trim().toLowerCase();
        return s.equals("off") || s.equals("false") || s.equals("0") || s.equals("no");
    }
}
