package com.larsons.game.scene;

import com.larsons.game.core.Game;
import com.larsons.game.core.Scene;
import com.larsons.game.cutscene.Cutscene;
import com.larsons.game.cutscene.CutsceneStage;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.ui.MenuList;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.World;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * The main menu. It loads one thing — the demo void — as the design asks.
 *
 * <p>Behind it plays the main menu's cutscene ({@code assets/cutscenes/menu.cut}):
 * your own character, as the wardrobe has it, talking with Bryn - close-up
 * layers in any colours, over and over. Without the close-ups the character
 * stands in the void on a turntable instead, stepping through its eight
 * directions while the camera drifts round.
 */
public final class MainMenuScene implements Scene {

    private static final String[] ITEMS = {"Demo", "Cutscene", "Quit"};
    private static final String[] ITEMS_NO_CUTSCENE = {"Demo", "Quit"};

    private final Game game;
    private final World world;
    private final OrbitCamera camera = new OrbitCamera(0.5, Math.toRadians(22), 5.2);
    private final MenuList menu = new MenuList();
    private final CutsceneStage stage;
    private Cutscene cutscene;
    private double turn;

    public MainMenuScene(Game game) {
        this.game = game;
        this.world = new World(game.wardrobe());
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
    }

    /** Aim a little to the left of the character, so it stands clear of the menu. */
    private Vec3 follow() {
        return world.player().feet().add(0, 0.95, 0).sub(camera.right().scale(1.25));
    }

    @Override
    public void update(double dt) {
        if (cutscene != null) {
            cutscene.update(dt);
            return;
        }
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

    @Override
    public void render() {
        Ui ui = game.ui();
        if (cutscene != null) {
            ui.begin();
            float h = ui.height(), stand = h - CutsceneStage.boxHeight(h) - 44;
            CutsceneStage.backdrop(ui, 0, 0, ui.width(), h);
            stage.actors(ui, cutscene, 400, 0, ui.width() - 400, stand);
            stage.dialogue(ui, cutscene, 540, 0, ui.width() - 540, h);
        } else {
            var w = game.window();
            game.worldRenderer().render(world, camera, w.framebufferWidth(), w.framebufferHeight(),
                    game.settings().showProps);
            ui.begin();
        }
        // A soft band behind the menu for legibility over the void.
        ui.rect(0, 0, 520, ui.height(), Theme.withAlpha(Theme.BACKGROUND, 0.72f));
        float x = 64, y = ui.height() * 0.2f;
        ui.textShadowed(ui.title, "Larson's Game", x, y, Theme.TITLE);
        ui.text(ui.body, "Task 1 — the sprite-stack demo", x + 4, y + 72, Theme.ACCENT);

        // The importer, when open, is modal: it draws over this and takes the keys.
        if (!game.importer().visible()) {
            String[] items = cutscene != null ? ITEMS : ITEMS_NO_CUTSCENE;
            int chosen = menu.show(ui, items, x, y + 140, 300, 54, 14);
            String pick = chosen >= 0 ? items[chosen] : null;
            if ("Demo".equals(pick)) game.switchTo("demo");
            if ("Cutscene".equals(pick)) game.switchTo("cutscene");
            if ("Quit".equals(pick) || ui.input().pressed(GLFW_KEY_ESCAPE)) game.quit();
        }

        float fy = ui.height() - 96;
        ui.paragraph(ui.small, "Drop sprite sheets (or a folder of them) on this window to add "
                + "them to assets/sprites/.", x, fy, 420, Theme.HINT);
        ui.text(ui.small, "GPU: " + game.window().gpu().describe(), x, fy + 44,
                game.window().gpu().software() ? Theme.WARNING : Theme.ITEM_DISABLED);
        ui.end();
    }
}
