package com.larsons.game.core;

import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.GpuInfo;
import com.larsons.game.gfx.Window;
import com.larsons.game.input.Input;
import com.larsons.game.scene.DemoScene;
import com.larsons.game.scene.MainMenuScene;
import com.larsons.game.cutscene.CloseupLibrary;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.ui.ImportPanel;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Props;
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
 * swap. Frame time is measured and capped at 50 ms, so animation stays
 * delta-driven (the engine's rule: the same speed at 60 or 144 Hz) without a
 * hitch turning into a teleport. Sprite sheets decode on worker threads and
 * are uploaded here, a few per frame.
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
    private final CloseupLibrary closeups;
    private final VoidRenderer voidRenderer;
    private final Props props;
    private final WorldRenderer worldRenderer;
    private final Wardrobe wardrobe;
    private final ImportPanel importer = new ImportPanel();
    private final Autopilot autopilot;

    private Scene scene;
    private boolean quit;
    private Path pendingShot;
    private double fps, fpsTimer;
    private int fpsFrames;

    private record Toast(String text, float[] color, double[] life) {}

    private final List<Toast> toasts = new ArrayList<>();

    public Game(Settings settings) {
        this.settings = settings;
        window = new Window(TITLE, 1280, 720, settings.msaa, settings.vsync);
        GpuInfo gpu = window.gpu();
        System.out.println("[gpu] " + gpu.describe().replace('—', '-'));
        if (gpu.software()) {
            String msg = "[gpu] WARNING: OpenGL is running on a software rasteriser, not a GPU. "
                    + "Update or install the graphics driver"
                    + (System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? " and set java.exe to 'High performance' in Windows graphics settings" : "")
                    + ".";
            if (settings.gpuRequired) System.err.println(msg);
            else System.out.println(msg);
        }

        batch = new Batch();
        ui = new Ui(window, batch);
        sprites = new SpriteLibrary(settings.spritesDir(), settings.vramBytes, settings.spriteScale);
        sprites.setMaxTexture(Math.min(gpu.maxTexture(), 16384));
        sprites.warmFallbacks();
        closeups = new CloseupLibrary(settings.assets.resolve("closeups"));
        voidRenderer = new VoidRenderer();
        props = new Props();
        worldRenderer = new WorldRenderer(voidRenderer, props, batch, sprites);
        wardrobe = Wardrobe.load(settings.wardrobeFile());
        autopilot = new Autopilot(settings.script);

        glEnable(GL_MULTISAMPLE);
        switchTo(settings.start);
        window.show();
    }

    /** The loop. Returns when the window closes or a scene quits. */
    public void run() {
        long last = System.nanoTime();
        while (!window.shouldClose() && !quit) {
            window.pollEvents();
            long now = System.nanoTime();
            double dt = Math.min(0.05, (now - last) / 1e9);
            last = now;
            tickFps(dt);

            Input in = window.input();
            for (List<String> drop : in.takeDrops()) {
                importer.open(drop.stream().map(Path::of).toList());
            }
            if (in.pressed(GLFW_KEY_F12)) pendingShot = Screenshot.defaultPath();
            autopilot.step(dt, this);

            sprites.beginFrame();
            sprites.pump();
            if (!importer.visible()) scene.update(dt);

            glViewport(0, 0, window.framebufferWidth(), window.framebufferHeight());
            glClearColor(Theme.BACKGROUND[0], Theme.BACKGROUND[1], Theme.BACKGROUND[2], 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            scene.render();
            drawOverlays(dt);

            if (pendingShot != null) {
                Path saved = Screenshot.capture(window.framebufferWidth(), window.framebufferHeight(),
                        pendingShot);
                if (saved != null) {
                    System.out.println("[screenshot] " + saved.toAbsolutePath());
                    if (!autopilot.active()) toast("Screenshot saved: " + saved, Theme.HINT);
                }
                pendingShot = null;
            }
            window.swapBuffers();
            in.endFrame();
        }
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

    private void tickFps(double dt) {
        fpsTimer += dt;
        fpsFrames++;
        if (fpsTimer >= 0.5) {
            fps = fpsFrames / fpsTimer;
            fpsTimer = 0;
            fpsFrames = 0;
        }
    }

    // --- scenes ----------------------------------------------------------------------

    /** Switch to "menu" or "demo". */
    public void switchTo(String name) {
        Scene next = switch (name.trim().toLowerCase()) {
            case "demo" -> new DemoScene(this);
            case "cutscene" -> new com.larsons.game.scene.CutsceneScene(this);
            default -> new MainMenuScene(this);
        };
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

    /** The cutscene close-ups (assets/closeups/), drawn through {@link #sprites()}. */
    public CloseupLibrary closeups() { return closeups; }

    /** The main menu's cutscene script. */
    public java.nio.file.Path menuCutscene() { return settings.assets.resolve("cutscenes").resolve("menu.cut"); }

    public WorldRenderer worldRenderer() { return worldRenderer; }

    public Wardrobe wardrobe() { return wardrobe; }

    public ImportPanel importer() { return importer; }

    public double fps() { return fps; }

    @Override
    public void close() {
        if (scene != null) scene.exit();
        saveWardrobe();
        settings.save();
        worldRenderer.close();
        props.close();
        voidRenderer.close();
        sprites.close();
        ui.close();
        batch.close();
        window.close();
    }
}
