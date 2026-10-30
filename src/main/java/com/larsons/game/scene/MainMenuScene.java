package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Game;
import com.larsons.game.core.Scene;
import com.larsons.game.cutscene.Cutscene;
import com.larsons.game.cutscene.CutsceneStage;
import com.larsons.game.editor.CreativeScene;
import com.larsons.game.input.Input;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelIO;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.ui.MenuList;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.World;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The main menu, in tabs along the top of its band:
 *
 * <ul>
 *   <li><b>Play</b> - the demo, and quit. Behind it your own character, as
 *       the wardrobe has her, stands in the demo's meadow on a turntable,
 *       among its trees, grass and rocks, stepping through her eight
 *       directions while the camera drifts round.</li>
 *   <li><b>Cutscene</b> - the main menu's cutscene ({@code
 *       assets/cutscenes/menu.cut}) plays behind it, over and over: your
 *       character talking with Bryn, in close-up layers; "Watch from the
 *       start" plays it full screen, once through.</li>
 *   <li><b>Dev</b> - only once the dev code has been typed here, 1 0 2 5
 *       (the number row or the num-pad; typed again, the tab goes): Creative
 *       Mode. A new level - empty, or the demo copied - and every level
 *       saved in {@code assets/levels/}, each to edit, play, copy or delete.
 *       It stays open between runs ({@code Settings.devUnlocked}).</li>
 * </ul>
 *
 * <p>Left and right (or Q and E) change tab; up, down and Enter choose.
 */
public final class MainMenuScene implements Scene {

    /** The tabs. */
    enum Tab {
        PLAY("Play"), CUTSCENE("Cutscene"), DEV("Dev");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    /** How long a Delete waits for its second click, seconds. */
    static final double CONFIRM = 3;
    /** How many saved levels the Dev tab lists at once (the wheel scrolls). */
    static final int ROWS = 7;

    private final Game game;
    private final World world;
    private final OrbitCamera camera = new OrbitCamera(0.5, Math.toRadians(22), 5.2);
    private final MenuList menu = new MenuList();
    private final CutsceneStage stage;
    private final DebugCode devCode = new DebugCode();
    private Cutscene cutscene;
    private double turn, time;
    private Tab tab = Tab.PLAY;
    private List<LevelIO.Saved> levels = List.of();
    private int scroll;
    /** The level a first click on Delete was for, and when. */
    private Path deleting;
    private double deleteAt;

    public MainMenuScene(Game game) {
        this.game = game;
        this.world = new World(game.wardrobe());
        world.setTerrain(game.terrain());
        world.setGround(game.demoScenery().ground());
        world.setScenery(game.scenery());
        world.player().preview(AnimState.IDLE);
        this.stage = new CutsceneStage(game.sprites(), game.closeups());
        if (!game.closeups().isEmpty()) {
            try {
                cutscene = Cutscene.load(game.menuCutscene(), game::wardrobe);
            } catch (Exception e) {
                System.err.println("[cutscene] " + e.getMessage());
            }
        }
    }

    @Override
    public String name() { return "Main Menu"; }

    @Override
    public void enter() {
        camera.snapTo(follow());
        refreshLevels();
    }

    /** The tabs shown: the Dev tab only once it has been unlocked. */
    List<Tab> tabs() {
        List<Tab> out = new ArrayList<>(List.of(Tab.PLAY, Tab.CUTSCENE));
        if (game.settings().devUnlocked) out.add(Tab.DEV);
        return out;
    }

    /** Aim a little to the left of the character, so it stands clear of the menu. */
    private Vec3 follow() {
        return world.player().feet().add(0, 0.95, 0).sub(camera.right().scale(1.25));
    }

    private void refreshLevels() {
        levels = LevelIO.list(game.settings().levelsDir());
        scroll = Math.max(0, Math.min(scroll, levels.size() - ROWS));
    }

    @Override
    public void update(double dt) {
        time += dt;
        Input in = game.window().input();
        if (!game.importer().visible()) {
            for (int key = GLFW_KEY_0; key <= GLFW_KEY_KP_9; key++) {
                int d = DebugCode.anyDigit(key);
                if (d >= 0 && in.pressed(key) && devCode.type(d, time)) toggleDev();
            }
            List<Tab> tabs = tabs();
            int at = Math.max(0, tabs.indexOf(tab));
            if (in.pressed(GLFW_KEY_RIGHT) || in.pressed(GLFW_KEY_E)) show(tabs.get((at + 1) % tabs.size()));
            if (in.pressed(GLFW_KEY_LEFT) || in.pressed(GLFW_KEY_Q)) {
                show(tabs.get((at + tabs.size() - 1) % tabs.size()));
            }
            if (tab == Tab.DEV && in.scroll() != 0) {
                scroll = Math.max(0, Math.min(levels.size() - ROWS, scroll - (int) Math.signum(in.scroll())));
            }
        }
        if (deleting != null && time - deleteAt > CONFIRM) deleting = null;
        if (tab == Tab.CUTSCENE && cutscene != null) {
            cutscene.update(dt);
            return;
        }
        world.setScenery(game.scenery());
        world.tick(dt);
        turn += dt;
        if (turn > 1.1) {
            turn = 0;
            world.player().turnSteps(1);
        }
        world.player().update(dt, com.larsons.game.world.Player.Intent.NONE,
                s -> s.defaultFrames() / 30.0);
        camera.rotate(dt * 0.12, 0);
        camera.update(dt, follow());
    }

    private void show(Tab t) {
        if (t == tab) return;
        tab = t;
        menu.setFocus(0);
        if (t == Tab.DEV) refreshLevels();
    }

    /** The dev code was typed: the Dev tab shown (and gone to) - or, typed again, hidden. */
    private void toggleDev() {
        var s = game.settings();
        s.devUnlocked = !s.devUnlocked;
        s.save();
        Sounds.play(SoundKeys.ui("secret"));
        if (s.devUnlocked) {
            show(Tab.DEV);
            game.toast("Dev tab unlocked: Creative Mode (type 1025 again to hide it)", Theme.ACCENT);
        } else {
            if (tab == Tab.DEV) show(Tab.PLAY);
            game.toast("Dev tab hidden", Theme.HINT);
        }
    }

    @Override
    public void render() {
        Ui ui = game.ui();
        if (tab == Tab.CUTSCENE && cutscene != null) {
            ui.begin();
            float h = ui.height(), stand = h - CutsceneStage.boxHeight(h) - 44;
            CutsceneStage.backdrop(ui, 0, 0, ui.width(), h);
            stage.actors(ui, cutscene, 400, 0, ui.width() - 400, stand);
            stage.dialogue(ui, cutscene, 540, 0, ui.width() - 540, h);
        } else {
            var w = game.window();
            game.worldRenderer().render(world, camera, w.framebufferWidth(), w.framebufferHeight());
            ui.begin();
        }
        // A soft band behind the menu for legibility over the void.
        float band = tab == Tab.DEV ? 620 : 520;
        ui.rect(0, 0, band, ui.height(), Theme.withAlpha(Theme.BACKGROUND, 0.78f));
        float x = 64, y = ui.height() * 0.12f;
        ui.textShadowed(ui.title, "Larson's Game", x, y, Theme.TITLE);
        ui.text(ui.body, "Task 1 — the sprite-stack demo", x + 4, y + 72, Theme.ACCENT);

        // The importer, when open, is modal: it draws over this and takes the keys.
        if (!game.importer().visible()) {
            float ty = y + 116;
            drawTabs(ui, x, ty);
            float cy = ty + 58;
            switch (tab) {
                case PLAY -> playTab(ui, x, cy);
                case CUTSCENE -> cutsceneTab(ui, x, cy);
                case DEV -> devTab(ui, x, cy, band - x - 24);
            }
            if (ui.input().pressed(GLFW_KEY_ESCAPE)) game.quit();
        }

        float fy = ui.height() - 96;
        ui.paragraph(ui.small, "Drop sprite sheets (or a folder of them) on this window to add "
                + "them to assets/sprites/.", x, fy, 420, Theme.HINT);
        ui.text(ui.small, "GPU: " + game.window().gpu().describe(), x, fy + 44,
                game.window().gpu().software() ? Theme.WARNING : Theme.ITEM_DISABLED);
        ui.end();
    }

    private void drawTabs(Ui ui, float x, float y) {
        float tx = x;
        for (Tab t : tabs()) {
            float w = ui.body.width(t.label) + 36;
            boolean on = t == tab;
            if (ui.button(t.label, tx, y, w, 40, on, true)) show(t);
            if (on) ui.rect(tx, y + 38, w, 3, Theme.ACCENT);
            tx += w + 8;
        }
        ui.rect(x, y + 44, Math.max(300, tx - x - 8), 1, Theme.PANEL_EDGE);
    }

    private void playTab(Ui ui, float x, float y) {
        String[] items = {"Demo", "Quit"};
        int chosen = menu.show(ui, items, x, y, 300, 54, 14);
        if (chosen == 0) game.switchTo("demo");
        if (chosen == 1) game.quit();
    }

    private void cutsceneTab(Ui ui, float x, float y) {
        if (cutscene == null) {
            ui.paragraph(ui.body, "No cutscene: its close-ups (assets/closeups/) or its script "
                    + "(assets/cutscenes/menu.cut) are missing.", x, y, 380, Theme.WARNING);
            return;
        }
        ui.paragraph(ui.small, "Playing behind this menu: " + game.relative(game.menuCutscene()), x, y, 380,
                Theme.HINT);
        String[] items = {"Watch from the start", "Quit"};
        int chosen = menu.show(ui, items, x, y + 34, 300, 54, 14);
        if (chosen == 0) game.switchTo("cutscene");
        if (chosen == 1) game.quit();
    }

    private void devTab(Ui ui, float x, float y, float width) {
        ui.text(ui.large, "Creative Mode", x, y, Theme.TITLE);
        ui.text(ui.small, "Levels are saved in " + game.relative(game.settings().levelsDir()), x, y + 38, Theme.HINT);
        float by = y + 70;
        String[] items = {"New level", "New level from the demo"};
        int chosen = menu.show(ui, items, x, by, 300, 46, 10);
        if (chosen == 0) newLevel(false);
        if (chosen == 1) newLevel(true);

        float ly = by + 2 * 56 + 16;
        ui.text(ui.body, levels.isEmpty() ? "No saved levels yet" : "Saved levels (" + levels.size() + ")", x, ly,
                Theme.ACCENT);
        ly += 32;
        int end = Math.min(levels.size(), scroll + ROWS);
        for (int i = scroll; i < end; i++) {
            LevelIO.Saved s = levels.get(i);
            float rh = 46;
            ui.rect(x - 8, ly - 4, width + 8, rh, Theme.withAlpha(Theme.PANEL, 0.7f));
            ui.text(ui.body, s.name(), x, ly + 2, Theme.TITLE);
            ui.text(ui.small, (int) s.size() + " m  ·  " + s.id(), x, ly + 22, Theme.ITEM_DISABLED);
            float bx = x + width - 4 * 70;
            if (ui.button("Edit", bx, ly + 2, 64, 34)) edit(s);
            if (ui.button("Play", bx + 70, ly + 2, 64, 34)) play(s);
            if (ui.button("Copy", bx + 140, ly + 2, 64, 34)) copy(s);
            boolean sure = s.folder().equals(deleting);
            if (ui.button(sure ? "Sure?" : "Delete", bx + 210, ly + 2, 64, 34, sure, true)) delete(s);
            ly += rh + 6;
        }
        if (levels.size() > ROWS) {
            ui.text(ui.small, (scroll + 1) + "–" + end + " of " + levels.size() + " (the wheel scrolls)", x, ly + 4,
                    Theme.HINT);
        }
    }

    private void newLevel(boolean fromDemo) {
        Path root = game.settings().levelsDir();
        String name = LevelIO.freeName(root, fromDemo ? "Demo copy" : "New level");
        Level level = fromDemo ? DemoLevel.build(name, game.demoScenery()) : Level.blank(name, Level.DEFAULT_SIZE);
        game.switchTo(new CreativeScene(game, level, null));
    }

    private void edit(LevelIO.Saved s) {
        game.switchTo("edit:" + s.id());
    }

    private void play(LevelIO.Saved s) {
        game.switchTo("play:" + s.id());
    }

    private void copy(LevelIO.Saved s) {
        Path root = game.settings().levelsDir();
        try {
            Level level = LevelIO.load(s.folder());
            level.setName(LevelIO.freeName(root, s.name() + " copy"));
            Path to = LevelIO.freeFolder(root, level.name());
            LevelIO.save(level, to);
            game.toast("Copied to \"" + level.name() + "\"", Theme.OK);
        } catch (IOException | RuntimeException e) {
            game.toast("Cannot copy " + s.name() + ": " + e.getMessage(), Theme.WARNING);
        }
        refreshLevels();
    }

    private void delete(LevelIO.Saved s) {
        if (!s.folder().equals(deleting)) {
            deleting = s.folder();
            deleteAt = time;
            return;
        }
        deleting = null;
        try {
            LevelIO.delete(s.folder());
            game.toast("Deleted \"" + s.name() + "\"", Theme.HINT);
        } catch (IOException e) {
            game.toast("Cannot delete " + s.name() + ": " + e.getMessage(), Theme.WARNING);
        }
        refreshLevels();
    }
}
