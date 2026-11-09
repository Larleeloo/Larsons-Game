package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Game;
import com.larsons.game.ui.MenuList;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Player;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * The pause menu: resume, the wardrobe (cosmetics), settings, the sounds
 * ({@link SoundsPanel}: a tab for each category, a volume for each, and the
 * player's own file for any sound), the controls,
 * how to import sprite sheets, back to the main menu, quit - and, once the
 * debug code has been typed ({@link DebugCode}), a button that clears every
 * item off the ground ({@link World#clearGround}).
 */
final class PauseMenu {

    enum Page { MAIN, WARDROBE, CLASS, SETTINGS, SOUNDS, CONTROLS, IMPORT }

    private static final String[] MAIN_ITEMS = {
            "Resume", "Wardrobe", "Class", "Settings", "Sounds", "Controls", "Import sprite sheets", "Main menu", "Quit"};
    /** The debug tools' item, before "Main menu". */
    static final String CLEAR_GROUND = "Clear the ground (debug)";

    static final String[][] CONTROLS = {
            {"W A S D", "walk (relative to the camera)"},
            {"Shift / Ctrl", "run / sprint while moving"},
            {"C", "crouch / stand (Shift: fast crouch walk)"},
            {"Space", "jump"},
            {"Left Alt / Z", "dash (tap) / combat roll (hold); in the air: land in a roll"},
            {"Left click / F", "attack (bow: hold to draw, let go)"},
            {"X / R / Q", "heavy attack / spin attack / parry"},
            {"B (held) / V", "block / shield bash"},
            {"L / K", "lock on to a target (L again: let go) / the next one"},
            {"Walk into vines", "climb: W up, S down, A D along; Space lets go (uses stamina)"},
            {"1 – 5 / mouse wheel", "choose a hotbar slot: what is in it is in hand"},
            {"I", "the inventory (click to move items, right-click: hotbar)"},
            {"F1 – F4", "laugh, cry, surprise, anger"},
            {"E / G", "pick up, or open / close the chest / drop what you hold"},
            {"E by a horse", "saddle / ride / get off; riding: Shift canter, Ctrl gallop"},
            {"Right drag / arrows", "orbit the camera (wheel while dragging: zoom)"},
            {"+ -", "zoom"},
            {"Tab / O", "camera height: side → 45° → top-down / automatic on hills, or fixed"},
            {"[ / ]", "preview the previous / next state"},
            {"Backspace", "stop previewing"},
            {", / .", "turn the character 45°"},
            {"T", "turntable: all 8 directions"},
            {"H / P", "toggle the HUD / the scenery (trees, grass, rocks)"},
            {"F9", "animations: new / classic (the first version, to compare)"},
            {"F11", "full screen at the display's fastest refresh rate / back to the window"},
            {"F12", "screenshot"},
            {"Esc", "this menu"},
    };

    private final Game game;
    private final World world;
    private final MenuList menu = new MenuList();
    private final WardrobePanel wardrobe;
    private final SoundsPanel sounds;
    private Page page = Page.MAIN;
    private boolean open;
    private String backLabel;
    private Runnable back;

    PauseMenu(Game game, World world) {
        this.game = game;
        this.world = world;
        this.wardrobe = new WardrobePanel(game);
        this.sounds = new SoundsPanel(game);
    }

    /** The main page's items: {@link #MAIN_ITEMS}, with the debug tools' among them while they are on. */
    static List<String> mainItems(boolean debug) {
        return mainItems(debug, null);
    }

    /** {@link #mainItems(boolean)}, with {@code back} before "Main menu" when there is one. */
    static List<String> mainItems(boolean debug, String back) {
        List<String> items = new ArrayList<>(List.of(MAIN_ITEMS));
        if (debug) items.add(items.indexOf("Main menu"), CLEAR_GROUND);
        if (back != null) items.add(items.indexOf("Main menu"), back);
        return items;
    }

    /**
     * Offer {@code label} before "Main menu", which runs {@code action}:
     * the play-test's way back to the level editor.
     */
    void setBack(String label, Runnable action) {
        backLabel = label;
        back = action;
    }

    boolean open() { return open; }

    void open(Page p) {
        open = true;
        page = p;
    }

    void close() {
        open = false;
        leave();
        page = Page.MAIN;
        game.saveWardrobe();
        game.settings().save();
        Sounds.play(SoundKeys.ui("resume"));
    }

    /** Back to the main page from another (Esc, or Back). */
    private void back(boolean escape) {
        leave();
        page = Page.MAIN;
        game.saveWardrobe();
        if (escape) Sounds.play(SoundKeys.ui("back"));
    }

    /** Leaving whatever page is up: the Sounds page stops its sound and stops taking dropped files. */
    private void leave() {
        if (page == Page.SOUNDS) {
            sounds.leave();
            game.setDropTarget(null);
        }
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
            List<String> items = mainItems(game.settings().debugTools, backLabel);
            float w = 360, h = 70 + items.size() * 58;
            float x = (ui.width() - w) / 2, y = (ui.height() - h) / 2;
            ui.panel(x, y, w, h);
            ui.textCentered(ui.large, "Paused", x + w / 2, y + 18, Theme.TITLE);
            int chosen = menu.show(ui, items.toArray(String[]::new), x + 30, y + 64, w - 60, 46, 12);
            String pick = chosen < 0 ? "" : items.get(chosen);
            if (back != null && pick.equals(backLabel)) {
                close();
                back.run();
                return;
            }
            switch (pick) {
                case "Resume" -> close();
                case "Wardrobe" -> page = Page.WARDROBE;
                case "Class" -> page = Page.CLASS;
                case "Settings" -> page = Page.SETTINGS;
                case "Sounds" -> {
                    page = Page.SOUNDS;
                    game.setDropTarget(sounds::drop);
                }
                case "Controls" -> page = Page.CONTROLS;
                case "Import sprite sheets" -> page = Page.IMPORT;
                case CLEAR_GROUND -> {
                    int n = world.clearGround();
                    game.toast("Debug: cleared " + n + (n == 1 ? " item" : " items") + " off the ground", Theme.HINT);
                }
                case "Main menu" -> {
                    close();
                    game.switchTo("menu");
                }
                case "Quit" -> game.quit();
                default -> { }
            }
            return;
        }

        float w = Math.min(1100, ui.width() - 40), h = Math.min(640, ui.height() - 40);
        float x = (ui.width() - w) / 2, y = (ui.height() - h) / 2;
        if (page == Page.WARDROBE) {
            // the wardrobe draws its own armoire, title and Back button and all
            boolean done = wardrobe.draw(ui, dt, x, y, w, h, player);
            if (done || escape) back(escape);
            return;
        }
        ui.panel(x, y, w, h);
        String title = switch (page) {
            case WARDROBE -> "Wardrobe";
            case CLASS -> "Class and pointer";
            case SETTINGS -> "Settings";
            case SOUNDS -> "Sounds";
            case CONTROLS -> "Controls";
            case IMPORT -> "Import sprite sheets";
            default -> "";
        };
        ui.text(ui.large, title, x + 24, y + 18, Theme.TITLE);
        if (ui.button("Back", x + w - 124, y + 14, 100, 34) || escape) {
            back(escape);
            return;
        }
        float cx = x + 24, cy = y + 70, cw = w - 48, ch = h - 94;
        switch (page) {
            case CLASS -> heroClass(ui, cx, cy, cw, ch);
            case SETTINGS -> settings(ui, cx, cy);
            case SOUNDS -> sounds.draw(ui, cx, cy, cw, ch);
            case CONTROLS -> controls(ui, cx, cy, ch);
            case IMPORT -> importHelp(ui, cx, cy, cw);
            default -> { }
        }
    }

    /**
     * Her class - the nine, as tiles; a new one starts her deck afresh - and
     * under them the mouse pointer: her class's, any of the nine, a custom
     * picture, or the system's arrow.
     */
    private void heroClass(Ui ui, float x, float y, float w, float h) {
        var s = game.settings();
        var hero = world.player().hero();
        ui.text(ui.small, "A new class starts your deck afresh with its 8 cards (your level and perk points are kept).",
                x, y - 4, Theme.ITEM_DISABLED);
        String pick = ClassPicker.draw(ui, game.pointers(), hero.heroClass(), x, y + 18, w, h - 110);
        if (pick != null && !pick.equals(hero.heroClass())) {
            hero.choose(pick, world.player());
            s.heroClass = pick;
            s.save();
            Sounds.play(SoundKeys.ui("card_shuffle"));
            game.toast("You are a " + com.larsons.game.cards.CardBook.get().hero(pick).name()
                    + ": a fresh deck of 8, a hand of " + hero.deck().hand().size(), Theme.ACCENT);
        }
        float py = y + h - 80;
        ui.text(ui.body, "Mouse pointer", x, py, Theme.ITEM);
        List<String> options = new ArrayList<>();
        options.add(com.larsons.game.ui.Pointers.CLASS);
        options.addAll(com.larsons.game.ui.Pointers.NAMES);
        options.add(com.larsons.game.ui.Pointers.CUSTOM);
        options.add(com.larsons.game.ui.Pointers.SYSTEM);
        float bw = (w - (options.size() - 1) * 4) / options.size();
        for (int i = 0; i < options.size(); i++) {
            String o = options.get(i);
            float bx = x + i * (bw + 4), by = py + 26;
            if (ui.button(com.larsons.game.ui.Pointers.label(o), bx, by, bw, 30, o.equals(s.pointer), true)) {
                s.pointer = o;
                s.save();
            }
        }
        ui.text(ui.small, "Custom: drop a picture in assets/ui/pointers/custom.png (its top-left corner points).",
                x, py + 62, Theme.ITEM_DISABLED);
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
        ui.text(ui.body, "Scenery", x, y + 8, Theme.ITEM);
        if (ui.button(s.showProps ? "On" : "Off", x + 220, y, 140, 34, s.showProps, true)) {
            s.showProps = !s.showProps;
        }
        ui.text(ui.body, "Camera height", x + 400, y + 8, Theme.ITEM);
        if (ui.button(s.autoCamera ? "Automatic" : "Fixed", x + 570, y, 140, 34, s.autoCamera, true)) {
            s.autoCamera = !s.autoCamera;
        }
        y += 50;
        ui.text(ui.body, "Walls indoors", x, y + 8, Theme.ITEM);
        if (ui.button(s.indoors().label(), x + 220, y, 140, 34, s.indoors() != com.larsons.game.building.Walls.UP,
                true)) {
            s.wallsIndoors = s.indoors().next().name();
            game.worldRenderer().setWalls(s.indoors(), s.outdoors());
        }
        ui.text(ui.body, "Outdoors", x + 400, y + 8, Theme.ITEM);
        if (ui.button(s.outdoors().label(), x + 570, y, 140, 34, s.outdoors() != com.larsons.game.building.Walls.UP,
                true)) {
            s.wallsOutdoors = s.outdoors().next().name();
            game.worldRenderer().setWalls(s.indoors(), s.outdoors());
        }
        y += 40;
        ui.text(ui.small, "Up: every wall stands. Cut away: walls between the camera and her drop to a stub. "
                + "Down: all of them do.", x, y + 4, Theme.ITEM_DISABLED);
        y += 50;
        ui.text(ui.body, "Animations", x, y + 8, Theme.ITEM);
        if (ui.button("New", x + 220, y, 140, 34, !s.classicAnimations, true)) {
            s.classicAnimations = false;
            lib.setClassic(false);
        }
        if (ui.button("Classic (v1)", x + 370, y, 140, 34, s.classicAnimations, true)) {
            s.classicAnimations = true;
            lib.setClassic(true);
        }
        ui.text(ui.small, "The weapons' moves were animated again; Classic shows the first version, to compare "
                + "(F9 while playing).", x, y + 44, Theme.ITEM_DISABLED);
        y += 90;
        var pacer = game.pacer();
        ui.text(ui.body, "Frame rate", x, y + 8, Theme.ITEM);
        int[] rates = {60, 120, 144, 0};
        for (int i = 0; i < rates.length; i++) {
            String label = rates[i] == 0 ? "Uncapped" : String.valueOf(rates[i]);
            if (ui.button(label, x + 220 + i * 120, y, 110, 34, pacer.target() == rates[i], true)) {
                s.setFps(rates[i]);
                pacer.setTarget(rates[i]);
            }
        }
        ui.text(ui.small, String.format("Now %.0f fps, %.1f ms a frame, the longest %.1f ms  ·  %s",
                pacer.fps(), pacer.averageMs(), pacer.worstMs(), pacer.describe()), x, y + 44, Theme.ITEM_DISABLED);
        y += 90;
        ui.text(ui.body, "Vsync", x, y + 8, Theme.ITEM);
        for (boolean on : new boolean[]{true, false}) {
            if (ui.button(on ? "On" : "Off", x + 220 + (on ? 0 : 120), y, 110, 34, pacer.vsync() == on, true)) {
                s.setVsync(on);
                pacer.setVsync(on);
            }
        }
        if (ui.button(game.window().fullscreen() ? "Windowed (F11)" : "Full screen (F11)", x + 480, y, 190, 34)) {
            game.toggleFullscreen();
        }
        boolean held = pacer.advice() != null;
        ui.text(ui.small, held ? String.format("The display runs at %d Hz but can do %d: F11 plays full screen at %d Hz"
                        + " - or raise it in the system's display settings, or turn vsync off.",
                        pacer.refresh(), pacer.fastest(), pacer.fastest())
                        : "On: frames wait for the display (no tearing). Off: the game holds the frame rate itself.",
                x, y + 44, held ? Theme.WARNING : Theme.ITEM_DISABLED);
        y += 90;
        var gpu = game.window().gpu();
        ui.text(ui.body, "Renderer", x, y, Theme.ITEM);
        ui.text(ui.body, gpu.describe(), x + 220, y, gpu.software() ? Theme.WARNING : Theme.OK);
        ui.text(ui.small, "OpenGL " + gpu.version() + " · MSAA " + gpu.samples() + "x · max texture "
                + gpu.maxTexture() + " px · " + (pacer.refresh() <= 0 ? "display rate unknown"
                : pacer.fastest() > pacer.refresh() ? "display " + pacer.refresh() + " Hz (can do " + pacer.fastest() + ")"
                : "display " + pacer.refresh() + " Hz"),
                x + 220, y + 26, Theme.ITEM_DISABLED);
        ui.text(ui.small, "Sprite cache: " + lib.residentCount() + " sheets, "
                + (lib.residentBytes() >> 20) + " MB of " + (s.vramBytes >> 20) + " MB budget",
                x + 220, y + 46, Theme.ITEM_DISABLED);
    }

    private void controls(Ui ui, float x, float y, float h) {
        float step = Math.min(26, h / CONTROLS.length);      // (closer together where the window is short)
        for (String[] row : CONTROLS) {
            ui.text(ui.body, row[0], x, y, Theme.ITEM_SELECTED);
            ui.text(ui.body, row[1], x + 240, y, Theme.ITEM);
            y += step;
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
