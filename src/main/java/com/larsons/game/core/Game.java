package com.larsons.game.core;

import com.larsons.game.audio.Music;
import com.larsons.game.audio.SoundCategory;
import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.SoundPack;
import com.larsons.game.audio.Sounds;
import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.GpuInfo;
import com.larsons.game.gfx.Window;
import com.larsons.game.input.Input;
import com.larsons.game.scene.DemoScene;
import com.larsons.game.scene.MainMenuScene;
import com.larsons.game.cutscene.CloseupLibrary;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.ItemSprites;
import com.larsons.game.sprite.ObjectSprites;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.ui.ImportPanel;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.GroundTexture;
import com.larsons.game.world.Scenery;
import com.larsons.game.world.SceneryLayout;
import com.larsons.game.world.SceneryRenderer;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.TerrainRenderer;
import com.larsons.game.world.VoidRenderer;
import com.larsons.game.world.WorldRenderer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * The game: owns the window, the GPU resources every scene shares, and the
 * loop.
 *
 * <p><b>One thread.</b> Events, simulation and rendering all run on the main
 * thread, one frame at a time: poll events → update the scene → render →
 * swap → wait for the next frame's turn. The {@link FramePacer} holds the
 * frames to a steady 120 a second (vsync where the display allows it, its
 * own precise wait where not) and hands each frame its step: animation stays
 * delta-driven (the engine's rule: the same speed at 60 or 144 Hz), capped
 * at 50 ms so a hitch never turns into a teleport. Sprite sheets decode on
 * worker threads and are uploaded here, a few milliseconds' worth a frame.
 *
 * <p><b>Overlays.</b> Two things sit above whatever scene is running: the
 * sprite importer, which opens whenever files are dropped on the window, and
 * short notifications ("toasts").
 */
public final class Game implements AutoCloseable {

    public static final String TITLE = "Larson's Game";

    private final Settings settings;
    private final Window window;
    private final Batch batch;
    private final Ui ui;
    private final SpriteLibrary sprites;
    private final ObjectSprites objects;
    private final ItemSprites items;
    private final CloseupLibrary closeups;
    private final VoidRenderer voidRenderer;
    private final GroundTexture groundTexture;
    private final ScenerySprites scenerySprites;
    private final Scenery scenery;
    private final SceneryRenderer sceneryRenderer;
    private final Terrain terrain;
    private final TerrainRenderer terrainRenderer;
    private final WorldRenderer worldRenderer;
    private final Wardrobe wardrobe;
    private final ImportPanel importer = new ImportPanel();
    private final Autopilot autopilot;
    private final FramePacer pacer;
    /** What loads as the game starts, and the screen that shows it (null once it has faded away). */
    private final Boot boot;
    private LoadingScreen loading;
    /** Whether everything has loaded (the loading screen may still be fading away). */
    private boolean booted;
    /** How opaque the loading screen still is once everything is in (1 until then). */
    private double fade = 1;
    /** Frames of the first scene drawn under the loading screen, asking for their sprite sheets. */
    private int bootRenders;

    private Scene scene;
    private boolean quit;
    private Path pendingShot;
    /** Where dropped files go before the importer ({@link #setDropTarget}), or null. */
    private java.util.function.Predicate<List<Path>> dropTarget;

    private record Toast(String text, float[] color, double[] life) {}

    private final List<Toast> toasts = new ArrayList<>();

    public Game(Settings settings) {
        this.settings = settings;
        long t0 = System.nanoTime();
        window = new Window(TITLE, 1280, 720, settings.msaa, settings.vsync);
        pacer = new FramePacer(window, settings.fps, settings.vsync);
        pacer.setLog(settings.logFrames);
        GpuInfo gpu = window.gpu();
        System.out.println("[gpu] " + gpu.describe().replace('—', '-'));
        System.out.println("[gpu] frame pacing: " + pacer.describe());
        if (pacer.advice() != null) System.out.println("[gpu] " + pacer.advice());
        if (gpu.software()) {
            String msg = "[gpu] WARNING: OpenGL is running on a software rasteriser, not a GPU. "
                    + "Update or install the graphics driver"
                    + (System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? " and set java.exe to 'High performance' in Windows graphics settings" : "")
                    + ".";
            if (settings.gpuRequired) System.err.println(msg);
            else System.out.println(msg);
        }
        long windowNs = System.nanoTime() - t0;

        long t1 = System.nanoTime();
        batch = new Batch();
        ui = new Ui(window, batch);
        long uiNs = System.nanoTime() - t1;

        // From here on the loading screen is up, showing each step as it runs.
        boot = new Boot(Boot.load(bootFile()));
        boot.done(Boot.Step.WINDOW, windowNs, gpu.renderer().split(" \\(")[0] + ", OpenGL "
                + gpu.version().split(" ")[0] + (gpu.software() ? " (software)" : ""));
        boot.done(Boot.Step.INTERFACE, uiNs, String.format("4 fonts at %.2gx", ui.scale()));
        loading = new LoadingScreen(ui, boot, gpu);
        boot.setDisplay(new BootDisplay());
        boot.retitle(Boot.Step.SCENE, "Opening " + startName(settings.start));
        // it draws when there is something new to show, never waiting on the display between steps
        window.swapInterval(0);
        window.show();

        sprites = boot.run(Boot.Step.SPRITE_INDEX, r -> indexSprites(r, gpu));
        sprites.warmFallbacks();
        boot.watch(Boot.Step.STAND_IN, r -> {
            int ready = sprites.fallbackReady(), of = SpriteLibrary.fallbackTotal();
            r.progress(ready, of, "views");
            r.detail("32-pixel views to draw while a sheet is missing or loading");
            int views = Elevation.values().length * Facing.values().length;
            r.summary(of + " views: " + of / views + " states, " + views + " ways round");
            return ready >= of;
        });
        objects = boot.run(Boot.Step.OBJECTS, r -> {
            ObjectSprites o = new ObjectSprites(settings.spritesDir());
            r.summary(o.names().isEmpty() ? "none found" : plural(o.names().size(), "object") + ": "
                    + String.join(", ", o.names()).replace('_', ' '));
            return o;
        });
        items = boot.run(Boot.Step.ITEMS, r -> {
            ItemSprites it = new ItemSprites(settings.spritesDir());
            var p = it.pixelPalette();
            r.summary((p == null ? "no palette" : (p.fills().length + p.lines().length) + " colours") + ", "
                    + it.spins().size() + " items, " + it.tiers().tierCount() + " tiers");
            return it;
        });
        closeups = boot.run(Boot.Step.CLOSEUPS, r -> {
            CloseupLibrary c = new CloseupLibrary(settings.assets.resolve("closeups"));
            r.summary(c.isEmpty() ? "none found" : plural(c.clips().size(), "clip"));
            return c;
        });
        scenerySprites = boot.run(Boot.Step.SCENERY, r -> {
            ScenerySprites s = ScenerySprites.scan(settings.sceneryDir());
            com.larsons.game.level.ObjectTypes.useScenery(s);
            r.summary(plural(s.ofKind(SceneryProfile.Kind.TREE).size(), "tree") + ", "
                    + plural(s.ofKind(SceneryProfile.Kind.TUFT).size(), "tuft") + ", "
                    + plural(s.ofKind(SceneryProfile.Kind.ROCK).size(), "rock")
                    + (s.ground() == null ? "" : ", " + plural(1 + s.ground().tiles().size(), "ground")));
            return s;
        });
        terrain = boot.run(Boot.Step.TERRAIN, r -> {
            Terrain t = Terrain.highlands();
            r.summary(plural(t.columns() * t.rows(), "height") + ", " + plural(t.walls().size(), "rock face"));
            return t;
        });
        scenery = boot.run(Boot.Step.PLANTING, r -> {
            Scenery placed = SceneryLayout.demo(scenerySprites, DemoScene.clearings(), terrain);
            int[] kinds = new int[SceneryProfile.Kind.values().length];
            for (Scenery.Placed p : placed.placed()) kinds[p.kind().ordinal()]++;
            r.summary(plural(kinds[SceneryProfile.Kind.TREE.ordinal()], "tree") + ", "
                    + plural(kinds[SceneryProfile.Kind.TUFT.ordinal()], "tuft") + ", "
                    + plural(kinds[SceneryProfile.Kind.ROCK.ordinal()], "rock"));
            return placed;
        });
        sceneryRenderer = boot.run(Boot.Step.SCENERY_SHEETS, r -> {
            int of = scenerySprites.sprites().size();
            SceneryRenderer sr = new SceneryRenderer(scenerySprites, (name, done) -> {
                r.progress(done, of, "sheets");
                r.detail(name.replace('_', ' '));
                r.log("Scenery: " + name.replace('_', ' '));
            });
            r.progress(of, of, "sheets");
            r.summary(plural(of, "sheet"));
            return sr;
        });
        groundTexture = boot.run(Boot.Step.GROUND, r -> {
            ScenerySprites.Ground g = scenerySprites.ground();
            GroundTexture t = new GroundTexture(g, scenery.ground());
            r.summary(g == null ? "none found" : "grass and " + g.tiles().size() + " more: "
                    + String.join(", ", g.tiles().keySet()).replace('_', ' '));
            return t;
        });
        Renderers renderers = boot.run(Boot.Step.RENDERERS, r -> {
            VoidRenderer v = new VoidRenderer(groundTexture);
            r.detail("the hills: " + String.format("%,d", terrain.columns() * terrain.rows()) + " heights");
            TerrainRenderer t = new TerrainRenderer(groundTexture, terrain);
            WorldRenderer w = new WorldRenderer(v, t, sceneryRenderer, batch, sprites, objects, items);
            r.summary(plural(t.triangles(), "triangle") + " of hills");
            return new Renderers(v, t, w);
        });
        voidRenderer = renderers.voidRenderer();
        terrainRenderer = renderers.terrain();
        worldRenderer = renderers.world();
        boot.run(Boot.Step.SOUND_PACK, r -> {
            startSound(r);
            return null;
        });
        watchSoundEffects();
        wardrobe = boot.run(Boot.Step.WARDROBE, r -> {
            Wardrobe wd = Wardrobe.load(settings.wardrobeFile());
            int worn = 0;
            for (Slot slot : Slot.values()) if (slot != Slot.BODY && wd.get(slot) != null) worn++;
            String body = wd.get(Slot.BODY);
            r.summary((body == null ? "the stand-in body" : "the " + Wardrobe.Style.base(body).replace('_', ' ')
                    + " body") + ", " + plural(worn, "thing") + " worn");
            return wd;
        });
        autopilot = new Autopilot(settings.script);

        glEnable(GL_MULTISAMPLE);
        boot.run(Boot.Step.SCENE, r -> {
            switchTo(settings.start);
            r.summary(scene.loaded());
            return null;
        });
        watchMusic();
        boot.watch(Boot.Step.SCENE_SHEETS, r -> {
            // what the first frames draw asks for its sheets; done once they are all on the GPU
            if (bootRenders < 2) {
                r.detail("seeing what the first frame shows");
                return false;
            }
            int resident = sprites.residentCount(), pending = sprites.pendingCount();
            r.progress(resident, resident + pending, "sheets");
            r.detail(pending == 0 ? "uploading the last of them" : pending + " decoding on "
                    + "the worker threads");
            r.summary(plural(resident, "sheet") + String.format(", %d MB of video memory",
                    sprites.residentBytes() >> 20));
            if (!sprites.busy()) return true;
            if (boot.status(Boot.Step.SCENE_SHEETS).seconds() > SHEETS_WAIT) {
                r.summary(pending + " still on the way (they show as they arrive)");
                return true;
            }
            return false;
        });
        // the game's own pace from here: the frame pacer's vsync and frame rate
        pacer.recheck();
    }

    /** The voids, the hills and the world renderer that draws on them, made together. */
    private record Renderers(VoidRenderer voidRenderer, TerrainRenderer terrain, WorldRenderer world) {}

    /** Seconds the loading screen waits for the first scene's sprite sheets before letting them pop in. */
    static final double SHEETS_WAIT = 20;
    /** What sheets may spend a frame uploading while the loading screen is up (nothing else needs the frame). */
    private static final long BOOT_UPLOAD_NS = 12_000_000L;
    /** Seconds the loading screen takes to fade away once everything is in. */
    private static final double BOOT_FADE = 0.45;

    /** Where how long each loading step took is kept, to weigh the next start's bar by. */
    private Path bootFile() {
        return settings.config.resolve("boot.json");
    }

    /** "1 tree", "1,204 trees". */
    static String plural(long n, String word) {
        return String.format("%,d ", n) + word + (n == 1 ? "" : word.endsWith("s") ? "es" : "s");
    }

    /** What the loading screen calls the scene the game starts in. */
    static String startName(String start) {
        String n = start == null ? "" : start.trim();
        String lower = n.toLowerCase();
        if (lower.startsWith("edit:")) return "the level " + n.substring(5).trim() + " in Creative Mode";
        if (lower.startsWith("play:")) return "the level " + n.substring(5).trim();
        return switch (lower) {
            case "demo" -> "the demo";
            case "cutscene" -> "the cutscene";
            case "creative" -> "Creative Mode";
            default -> "the main menu";
        };
    }

    /**
     * Index every sprites folder (the longest step: hundreds of folders of
     * thousands of sheets), reporting each folder as it is done.
     */
    private SpriteLibrary indexSprites(Boot.Report r, GpuInfo gpu) {
        Path assets = settings.spritesDir().toAbsolutePath().getParent();
        String own = settings.spritesDir().getFileName() + "/", bodies = settings.spritesDir().getFileName() + "_";
        int[] before = {0};
        SpriteLibrary lib = new SpriteLibrary(settings.spritesDir(), settings.vramBytes, settings.spriteScale,
                settings.classicAnimations, (done, of, folder, sheets) -> {
            String where = assets == null ? folder.toString()
                    : assets.relativize(folder.toAbsolutePath()).toString().replace('\\', '/');
            r.progress(done, of, "folders");
            r.detail(where + "\n" + String.format("%,d sheets so far", sheets));
            // "gloves/black_leather_px64", "masculine/gloves/black_leather_px64"
            String item = where.startsWith(own) ? where.substring(own.length())
                    : where.startsWith(bodies) ? where.substring(bodies.length()) : where;
            r.log(String.format("%,d sheets  %s", sheets - before[0], item));
            before[0] = sheets;
        });
        lib.setMaxTexture(Math.min(gpu.maxTexture(), 16384));
        SpriteLibrary.Census c = lib.census();
        r.summary(plural(c.sheets(), "sheet") + ", " + plural(c.items(), "item")
                + (settings.classicAnimations ? ", first animations too" : ""));
        return lib;
    }

    /**
     * The sound pack in {@code assets/sounds}: its folders and key list kept
     * up to date (a read-only assets folder is fine - there is just nothing
     * to scaffold), decoded ahead in the background; the player's own sounds
     * ({@code config/sounds.json}) loaded, and their volumes set. Nothing
     * here needs an audio device; without one every sound is a no-op.
     */
    private void startSound(Boot.Report r) {
        SoundPack.useDir(settings.soundsDir());
        r.detail(relative(settings.soundsDir()));
        try {
            SoundPack.scaffold(settings.soundsDir());
        } catch (java.io.IOException | RuntimeException e) {
            System.err.println("[sound] cannot set up " + settings.soundsDir() + ": " + e.getMessage());
        }
        Sounds.useStore(settings.config);
        Sounds.load();
        Sounds.setMasterVolume(settings.masterVolume);
        for (SoundCategory c : SoundCategory.values()) {
            Sounds.setCategoryVolume(c, settings.soundVolumes.getOrDefault(c.key(), 1.0));
        }
        int own = Sounds.all().size();
        r.summary(plural(SoundKeys.all().size(), "sound") + (own > 0 ? ", " + own + " of your own" : ""));
        Sounds.preload();
    }

    /** The sound effects decoding in the background ({@link Sounds#preload}), on the loading screen. */
    private void watchSoundEffects() {
        Path root = settings.soundsDir();
        Path[] seen = {null};
        boot.watch(Boot.Step.SOUND_EFFECTS, r -> {
            Sounds.Preload p = Sounds.preloadProgress();
            if (p.total() < 0) {
                r.detail("finding the files in " + relative(root));
                return false;
            }
            r.progress(p.done(), p.total(), "files");
            if (p.current() != null && !p.current().equals(seen[0])) {
                seen[0] = p.current();
                String file = root.relativize(p.current()).toString().replace('\\', '/');
                r.detail(file);
                r.log("Sound: " + file);
            }
            r.summary(plural(p.total(), "file"));
            return p.finished();
        });
    }

    /** The first scene's song decoding ({@link Music}), on the loading screen. */
    private void watchMusic() {
        String[] file = {null};
        boot.watch(Boot.Step.MUSIC, r -> {
            String song = Music.wanted();
            if (song.isEmpty()) {
                r.summary("none here");
                return true;
            }
            if (file[0] == null) {
                Path f = Sounds.describe(song).file();
                file[0] = f == null ? song + " (no file)" : f.getFileName().toString();
            }
            r.detail(file[0]);
            if (!Music.settled()) return false;
            r.summary(Music.current().equals(song) ? file[0] + ", playing" : file[0] + ", silent");
            r.log("Music: " + file[0]);
            return true;
        });
    }

    /**
     * How the loading screen is drawn while a step runs on the main thread:
     * a frame of it alone (the window's events handled, so it can be moved,
     * and closed), and a failure held on screen.
     */
    private final class BootDisplay implements Boot.Display {

        @Override
        public void frame() {
            Input in = window.input();
            window.pollEvents();
            if (window.shouldClose()) throw new Boot.Closed();
            if (in.pressed(GLFW_KEY_F12)) pendingShot = Screenshot.defaultPath();
            drawBoot();
            in.endFrame();
        }

        @Override
        public void failed(Boot.Status step, Throwable error) {
            error.printStackTrace();
            Input in = window.input();
            in.endFrame();
            while (!window.shouldClose()) {
                window.pollEvents();
                if (in.anyPressed()) break;
                boot.poll();   // what was already going on in the background still finishes on screen
                drawBoot();
                in.endFrame();
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private void drawBoot() {
            glViewport(0, 0, window.framebufferWidth(), window.framebufferHeight());
            glClearColor(Theme.BACKGROUND[0], Theme.BACKGROUND[1], Theme.BACKGROUND[2], 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            ui.begin();
            loading.draw(1);
            ui.end();
            takeShot(false);
            window.swapBuffers();
        }
    }

    /** The loop. Returns when the window closes or a scene quits. */
    public void run() {
        while (!window.shouldClose() && !quit) {
            window.pollEvents();
            double dt = pacer.beginFrame();

            Input in = window.input();
            // while the loading screen covers the scene, the scene stands still and hears nothing
            boolean booting = !booted;
            if (!booting) for (List<String> drop : in.takeDrops()) drop(drop.stream().map(Path::of).toList());
            if (in.pressed(GLFW_KEY_F12)) {
                pendingShot = Screenshot.defaultPath();
                Sounds.play(SoundKeys.ui("screenshot"));
            }
            if (in.pressed(GLFW_KEY_F11)) toggleFullscreen();
            if (!booting) autopilot.step(dt, this);
            Music.update(dt);

            sprites.beginFrame();
            if (booting) {
                sprites.pump(BOOT_UPLOAD_NS);
                in.endFrame();
            } else {
                sprites.pump();
                if (!importer.visible()) scene.update(dt);
            }

            glViewport(0, 0, window.framebufferWidth(), window.framebufferHeight());
            glClearColor(Theme.BACKGROUND[0], Theme.BACKGROUND[1], Theme.BACKGROUND[2], 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            scene.render();
            if (booting) bootRenders++;
            if (loading != null) drawLoading(dt);
            drawOverlays(dt);

            takeShot(!autopilot.active());
            window.swapBuffers();
            in.endFrame();
            pacer.endFrame();
        }
    }

    /**
     * The loading screen over the first scene while its last background work
     * finishes, then fading away to show it.
     */
    private void drawLoading(double dt) {
        if (!booted && boot.poll()) {
            booted = true;
            System.out.println("[boot] " + boot.report());
            Boot.save(bootFile(), boot.timings());
        }
        if (booted) fade -= dt / BOOT_FADE;
        if (fade <= 0) {
            loading = null;
            return;
        }
        ui.begin();
        loading.draw((float) Math.min(1, fade));
        ui.end();
    }

    private void takeShot(boolean toast) {
        if (pendingShot == null) return;
        Path saved = Screenshot.capture(window.framebufferWidth(), window.framebufferHeight(), pendingShot);
        if (saved != null) {
            System.out.println("[screenshot] " + saved.toAbsolutePath());
            if (toast) toast("Screenshot saved: " + saved, Theme.HINT);
        }
        pendingShot = null;
    }

    /**
     * F11: full screen on the monitor the window is on, switched to its
     * fastest refresh rate ({@link Window#setFullscreen}) - or back into the
     * window.
     */
    public void toggleFullscreen() {
        int hz = window.setFullscreen(!window.fullscreen());
        pacer.recheck();
        System.out.println("[gpu] " + (window.fullscreen() ? "full screen at " + hz + " Hz" : "windowed")
                + " - frame pacing: " + pacer.describe());
        toast(window.fullscreen() ? "Full screen, " + hz + " Hz (F11: back to the window)" : "Windowed",
                Theme.HINT);
    }

    private void drawOverlays(double dt) {
        ui.begin();
        // Bottom centre, stacking upward, clear of the HUD and the help bar.
        float y = ui.height() - 84;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            t.life()[0] -= dt;
            if (t.life()[0] <= 0) {
                toasts.remove(i);
                continue;
            }
            float a = (float) Math.min(1, t.life()[0] / 0.4);
            float w = ui.body.width(t.text()) + 28;
            float x = (ui.width() - w) / 2;
            ui.rect(x, y, w, 32, Theme.withAlpha(Theme.PANEL, 0.92f * a));
            ui.text(ui.body, t.text(), x + 14, y + 7, Theme.withAlpha(t.color(), a));
            y -= 38;
        }
        // The importer is modal: it draws over everything, toasts included.
        importer.draw(ui, this);
        ui.end();
    }

    // --- scenes ----------------------------------------------------------------------

    /**
     * Switch to "menu", "demo", "cutscene", "creative" (Creative Mode on a
     * new level), "edit:&lt;level&gt;" or "play:&lt;level&gt;" (a level's folder
     * in {@code assets/levels}).
     */
    public void switchTo(String name) {
        String n = name.trim();
        String lower = n.toLowerCase();
        Scene next;
        if (lower.startsWith("edit:") || lower.startsWith("play:")) {
            Path folder = settings.levelsDir().resolve(n.substring(5).trim());
            try {
                com.larsons.game.level.Level level = com.larsons.game.level.LevelIO.load(folder);
                next = lower.startsWith("edit:") ? new com.larsons.game.editor.CreativeScene(this, level, folder)
                        : new DemoScene(this, level, null, 0, null);
            } catch (java.io.IOException | RuntimeException e) {
                System.err.println("[levels] cannot open " + folder + ": " + e.getMessage());
                toast("Cannot open " + relative(folder) + ": " + e.getMessage(), Theme.WARNING);
                next = new MainMenuScene(this);
            }
        } else {
            next = switch (lower) {
                case "demo" -> new DemoScene(this);
                case "cutscene" -> new com.larsons.game.scene.CutsceneScene(this);
                case "creative" -> new com.larsons.game.editor.CreativeScene(this,
                        com.larsons.game.level.Level.blank("New level", com.larsons.game.level.Level.DEFAULT_SIZE),
                        null);
                default -> new MainMenuScene(this);
            };
        }
        switchTo(next);
    }

    /** Switch to {@code next}, as it is (the level editor coming back from its play-test keeps its state). */
    public void switchTo(Scene next) {
        if (scene != null) scene.exit();
        scene = next;
        scene.enter();
        window.setTitle(TITLE + " — " + scene.name());
    }

    public Scene scene() { return scene; }

    public void quit() {
        quit = true;
    }

    // --- shared services -------------------------------------------------------------

    public void toast(String text, float[] color) {
        toasts.add(new Toast(text, color, new double[]{3.2}));
        if (toasts.size() > 4) toasts.remove(0);
    }

    /**
     * Files dropped on the window go to {@code target} first while it is set
     * (the Sounds menu takes an MP3 dropped on a row): it says whether it
     * took them; what it does not take goes to the sprite importer. Null:
     * everything goes to the importer.
     */
    public void setDropTarget(java.util.function.Predicate<List<Path>> target) {
        dropTarget = target;
    }

    /** Files dropped on the window (or by a script): the drop target's if it takes them, else the importer's. */
    public void drop(List<Path> files) {
        if (dropTarget == null || !dropTarget.test(files)) importer.open(files);
    }

    public void requestScreenshot(Path file) {
        pendingShot = file;
    }

    /** A path relative to the working directory (the repository), for messages. */
    public String relative(Path p) {
        Path abs = p.toAbsolutePath().normalize();
        Path cwd = Path.of("").toAbsolutePath();
        return abs.startsWith(cwd) ? cwd.relativize(abs).toString().replace('\\', '/') : abs.toString();
    }

    public void saveWardrobe() {
        wardrobe.save(settings.wardrobeFile());
    }

    public Settings settings() { return settings; }

    public Window window() { return window; }

    public Ui ui() { return ui; }

    public Batch batch() { return batch; }

    public SpriteLibrary sprites() { return sprites; }

    /** The sprite sheets of things in the world that are not characters (assets/sprites/objects/). */
    public ObjectSprites objects() { return objects; }

    /** The items' turning sprites, their weapon tiers' colours and their inventory icons (assets/sprites/items/). */
    public ItemSprites items() { return items; }

    /** The cutscene close-ups (assets/closeups/), drawn through {@link #sprites()}. */
    public CloseupLibrary closeups() { return closeups; }

    /** The main menu's cutscene script. */
    public java.nio.file.Path menuCutscene() { return settings.assets.resolve("cutscenes").resolve("menu.cut"); }

    public WorldRenderer worldRenderer() { return worldRenderer; }

    /**
     * The scenery the demo and the menu stand in - the trees, grass and rocks
     * of {@code assets/scenery/}, planted by {@link SceneryLayout} - or none
     * while it is hidden (P, Pause → Settings → Scenery): hidden, it is not
     * in her way either.
     */
    public Scenery scenery() { return settings.showProps ? scenery : Scenery.NONE; }

    /** The demo's scenery and the ground it lays out, shown or not. */
    public Scenery demoScenery() { return scenery; }

    /** The ground the demo and the menu stand on: the highlands north of the spawn ({@link Terrain#highlands}). */
    public Terrain terrain() { return terrain; }

    /** The scenery's sprites and the ground's grass, as found in {@code assets/scenery/}. */
    public ScenerySprites scenerySprites() { return scenerySprites; }

    public Wardrobe wardrobe() { return wardrobe; }

    public ImportPanel importer() { return importer; }

    public double fps() { return pacer.fps(); }

    /** What holds the frame rate steady, and its numbers (frame times, the worst of them). */
    public FramePacer pacer() { return pacer; }

    @Override
    public void close() {
        if (scene != null) scene.exit();
        saveWardrobe();
        settings.save();
        Music.reset();
        Sounds.dispose();
        worldRenderer.close();
        items.close();
        sceneryRenderer.close();
        terrainRenderer.close();
        voidRenderer.close();
        groundTexture.close();
        sprites.close();
        ui.close();
        batch.close();
        window.close();
    }
}
