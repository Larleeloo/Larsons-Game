package com.larsons.game.scene;

import com.larsons.game.core.Game;
import com.larsons.game.core.Scene;
import com.larsons.game.cutscene.Cutscene;
import com.larsons.game.cutscene.CutsceneStage;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

/**
 * The main menu's cutscene, full screen and once through: your own character
 * (as the wardrobe has it) and Bryn, talking. Esc, Enter or Space goes back
 * to the menu, as does the end of the scene.
 */
public final class CutsceneScene implements Scene {

    private final Game game;
    private final CutsceneStage stage;
    private Cutscene cutscene;

    public CutsceneScene(Game game) {
        this.game = game;
        this.stage = new CutsceneStage(game.sprites(), game.closeups());
        try {
            cutscene = Cutscene.load(game.menuCutscene(), game::wardrobe);
            cutscene.playOnce();
        } catch (Exception e) {
            System.err.println("[cutscene] " + e.getMessage());
            game.toast("No cutscene: " + e.getMessage(), Theme.WARNING);
        }
    }

    @Override
    public String name() { return "Cutscene"; }

    @Override
    public void update(double dt) {
        var in = game.ui().input();
        if (cutscene == null || cutscene.finished() || in.pressed(GLFW_KEY_ESCAPE)
                || in.pressed(GLFW_KEY_ENTER) || in.pressed(GLFW_KEY_SPACE)) {
            game.switchTo("menu");
            return;
        }
        cutscene.update(dt);
    }

    @Override
    public void render() {
        if (cutscene == null) return;
        Ui ui = game.ui();
        ui.begin();
        stage.draw(ui, cutscene, 0, 0, ui.width(), ui.height());
        ui.text(ui.small, "Esc - back to the menu", 16, 12, Theme.withAlpha(Theme.HINT, 0.6f));
        ui.end();
    }
}
