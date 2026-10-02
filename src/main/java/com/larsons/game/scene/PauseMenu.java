package com.larsons.game.scene;

import com.larsons.game.core.Game;
import com.larsons.game.ui.MenuList;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Player;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * The pause menu: resume, the wardrobe (cosmetics), settings, the controls,
 * how to import sprite sheets, back to the main menu, quit.
 */
final class PauseMenu {

    enum Page { MAIN, WARDROBE, SETTINGS, CONTROLS, IMPORT }

    private static final String[] MAIN_ITEMS = {
            "Resume", "Wardrobe", "Settings", "Controls", "Import sprite sheets", "Main menu", "Quit"};

    static final String[][] CONTROLS = {
            {"W A S D", "walk (relative to the camera)"},
            {"Shift / Ctrl", "run / sprint while moving"},
            {"C", "crouch / stand (Shift: fast crouch walk)"},
            {"Space", "jump (with the sword)"},
            {"Left click / F", "attack (bow: hold to draw, let go)"},
            {"X / R / Q", "heavy attack / spin attack / parry"},
            {"B (held) / V", "block / shield bash"},
            {"1 – 4", "sword and shield, axe, bow, crossbow"},
            {"5 – 8", "laugh, cry, surprise, anger"},
            {"E / G", "pick up / drop what you hold"},
            {"Right drag / arrows", "orbit the camera"},
            {"Mouse wheel / + -", "zoom"},
            {"Tab", "camera height: side → 45° → top-down"},
            {"[ / ]", "preview the previous / next state"},
            {"0", "stop previewing"},
            {", / .", "turn the character 45°"},
            {"T", "turntable: all 8 directions"},
            {"H / P", "toggle the HUD / the 3D props"},
            {"F12", "screenshot"},
            {"Esc", "this menu"},
    };

    private final Game game;
    private final MenuList menu = new MenuList();
    private final WardrobePanel wardrobe;
    private Page page = Page.MAIN;
    private boolean open;

    PauseMenu(Game game) {
        this.game = game;
        this.wardrobe = new WardrobePanel(game);
    }

    boolean open() { return open; }

    void open(Page p) {
        open = true;
        page = p;
    }

    void close() {
        open = false;
        page = Page.MAIN;
        game.saveWardrobe();
        game.settings().save();
    }

    /** Draw and handle the menu. Returns false once it has closed itself. */
    void draw(Ui ui, double dt, Player player) {
        if (!open) return;
        boolean escape = ui.input().pressed(GLFW_KEY_ESCAPE);
        if (escape) ui.input().consumeKey(GLFW_KEY_ESCAPE);
        ui.scrim();

        if (page == Page.MAIN) {
            if (escape) {
                close();
                return;
            }
            float w = 360, h = 70 + MAIN_ITEMS.length * 58;
            float x = (ui.width() - w) / 2, y = (ui.height() - h) / 2;
            ui.panel(x, y, w, h);
            ui.textCentered(ui.large, "Paused", x + w / 2, y + 18, Theme.TITLE);
            int chosen = menu.show(ui, MAIN_ITEMS, x + 30, y + 64, w - 60, 46, 12);
            switch (chosen) {
                case 0 -> close();
                case 1 -> page = Page.WARDROBE;
                case 2 -> page = Page.SETTINGS;
                case 3 -> page = Page.CONTROLS;
                case 4 -> page = Page.IMPORT;
                case 5 -> {
                    close();
                    game.switchTo("menu");
                }
                case 6 -> game.quit();
                default -> { }
            }
            return;
        }

        float w = Math.min(1100, ui.width() - 40), h = Math.min(640, ui.height() - 40);
        float x = (ui.width() - w) / 2, y = (ui.height() - h) / 2;
        ui.panel(x, y, w, h);
        String title = switch (page) {
            case WARDROBE -> "Wardrobe";
            case SETTINGS -> "Settings";
            case CONTROLS -> "Controls";
            case IMPORT -> "Import sprite sheets";
            default -> "";
        };
        ui.text(ui.large, title, x + 24, y + 18, Theme.TITLE);
        if (ui.button("Back", x + w - 124, y + 14, 100, 34) || escape) {
            page = Page.MAIN;
            game.saveWardrobe();
            return;
        }
        float cx = x + 24, cy = y + 70, cw = w - 48, ch = h - 94;
        switch (page) {
            case WARDROBE -> wardrobe.draw(ui, dt, cx, cy, cw, ch, player);
            case SETTINGS -> settings(ui, cx, cy);
            case CONTROLS -> controls(ui, cx, cy);
            case IMPORT -> importHelp(ui, cx, cy, cw);
            default -> { }
        }
    }

    private void settings(Ui ui, float x, float y) {
        var s = game.settings();
        var lib = game.sprites();
        ui.text(ui.body, "Sprite resolution", x, y + 8, Theme.ITEM);
        double[] scales = {1.0, 0.5, 0.25};
        String[] names = {"Full (512)", "Half (256)", "Quarter (128)"};
        for (int i = 0; i < scales.length; i++) {
            if (ui.button(names[i], x + 220 + i * 150, y, 140, 34, lib.scale() == scales[i], true)) {
                s.spriteScale = scales[i];
                lib.setScale(scales[i]);
            }
        }
        ui.text(ui.small, "Shrinks 512-pixel Blender frames as they load, for GPUs with less memory. "
                + "The 32-pixel fallback is never scaled.", x, y + 44, Theme.ITEM_DISABLED);

        y += 90;
        ui.text(ui.body, "HUD", x, y + 8, Theme.ITEM);
        if (ui.button(s.showHud ? "On" : "Off", x + 220, y, 140, 34, s.showHud, true)) s.showHud = !s.showHud;
        y += 50;
        ui.text(ui.body, "3D props", x, y + 8, Theme.ITEM);
        if (ui.button(s.showProps ? "On" : "Off", x + 220, y, 140, 34, s.showProps, true)) {
            s.showProps = !s.showProps;
        }
        y += 70;
        var gpu = game.window().gpu();
        ui.text(ui.body, "Renderer", x, y, Theme.ITEM);
        ui.text(ui.body, gpu.describe(), x + 220, y, gpu.software() ? Theme.WARNING : Theme.OK);
        ui.text(ui.small, "OpenGL " + gpu.version() + " · MSAA " + gpu.samples() + "x · max texture "
                + gpu.maxTexture() + " px · vsync " + (s.vsync ? "on" : "off"), x + 220, y + 26,
                Theme.ITEM_DISABLED);
        ui.text(ui.small, "Sprite cache: " + lib.residentCount() + " sheets, "
                + (lib.residentBytes() >> 20) + " MB of " + (s.vramBytes >> 20) + " MB budget",
                x + 220, y + 46, Theme.ITEM_DISABLED);
    }

    private void controls(Ui ui, float x, float y) {
        for (String[] row : CONTROLS) {
            ui.text(ui.body, row[0], x, y, Theme.ITEM_SELECTED);
            ui.text(ui.body, row[1], x + 240, y, Theme.ITEM);
            y += 26;
        }
    }

    private void importHelp(Ui ui, float x, float y, float w) {
        String root = game.relative(game.sprites().root());
        float used = ui.paragraph(ui.body,
                "Drag sprite sheets from your file manager and drop them anywhere on this window — "
                        + "single sheets, a whole folder, or the loose numbered frames Blender writes. "
                        + "You choose the layer and a name, and they are saved into the repository at:",
                x, y, w, Theme.ITEM);
        y += used + 8;
        y += ui.paragraph(ui.large, root + "/", x, y, w, Theme.ACCENT);
        ui.text(ui.large, "    <layer>/<name>/<state>_<elevation>_<direction>.png", x, y, Theme.ACCENT);
        y += 44;
        used = ui.paragraph(ui.body,
                "state: idle walk run sprint jump attack (and crouch_idle, axe_walk, bow_draw ... - see "
                        + "assets/sprites/README.md)    elevation: side middle top (or 0 45 90)\n"
                        + "direction: n ne e se s sw w nw — the way the character faces in the picture "
                        + "(s = toward the camera, e = to the viewer's right)\n\n"
                        + "Frames are 512×512 at 30 fps, left to right then top to bottom. Every layer "
                        + "has the same frame count as the body for the same state, and is rendered "
                        + "with what is in front of it held out — the sword cut away where the hand "
                        + "holds it. A missing west view borrows the east one mirrored.\n\n"
                        + "Layers: " + String.join(", ", java.util.Arrays.stream(
                        com.larsons.game.sprite.Slot.values()).map(s -> s.key()).toList()) + ".\n"
                        + "A pickup's world sprite is items/<name>/icon.png.",
                x, y, w, Theme.ITEM);
        y += used + 8;
        ui.text(ui.small, "The full contract is in assets/sprites/README.md.", x, y, Theme.ITEM_DISABLED);
    }
}
