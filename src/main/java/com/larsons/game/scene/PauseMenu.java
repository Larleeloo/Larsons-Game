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
 * player's own file for any sound), the controls, the compendium of every
 * card ({@link Compendium}),
 * how to import sprite sheets, back to the main menu, quit - and, once the
 * debug code has been typed ({@link DebugCode}), a button that clears every
 * item off the ground ({@link World#clearGround}).
 */
final class PauseMenu {

    enum Page { MAIN, WARDROBE, CLASS, COMPENDIUM, SETTINGS, COLOURS, SOUNDS, CONTROLS, IMPORT }

    private static final String[] MAIN_ITEMS = {
            "Resume", "Wardrobe", "Class", "Compendium", "Settings", "Colours", "Sounds", "Controls",
            "Import sprite sheets",
            "Main menu", "Quit"};
    /** The debug tools' item, before "Main menu". */
    static final String CLEAR_GROUND = "Clear the ground (debug)";

    private final Game game;
    private final World world;
    private final MenuList menu = new MenuList();
    private final WardrobePanel wardrobe;
    private final SoundsPanel sounds;
    private final ControlsPanel controls;
    private final Compendium compendium;
    private final ColoursPanel colours;
    private Page page = Page.MAIN;
    private boolean open;
    private String backLabel;
    private Runnable back, leaving;
    private final java.util.Set<String> hidden = new java.util.HashSet<>();

    PauseMenu(Game game, World world) {
        this.game = game;
        this.world = world;
        this.wardrobe = new WardrobePanel(game);
        this.sounds = new SoundsPanel(game);
        this.controls = new ControlsPanel(game);
        this.compendium = new Compendium(world);
        this.colours = new ColoursPanel(game);
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

    /** Leave {@code item} off the main page (a playthrough's class is not changed from here). */
    void hide(String item) {
        hidden.add(item);
    }

    /** Run {@code r} before "Main menu" and "Quit" leave the game (a playthrough saves itself). */
    void onLeave(Runnable r) {
        leaving = r;
    }

    boolean open() { return open; }

    /** Let go of what the pages hold on the graphics card (the scene is closing). */
    void dispose() {
        colours.close();
    }

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
        if (page == Page.CONTROLS) controls.leave();
        if (page == Page.COLOURS) colours.leave();
    }

    /** Draw and handle the menu. Returns false once it has closed itself. */
    void draw(Ui ui, double dt, Player player) {
        if (!open) return;
        // (while the Controls page waits for a key, Esc is its to take)
        boolean escape = ui.input().pressed(GLFW_KEY_ESCAPE) && !(page == Page.CONTROLS && controls.capturing());
        if (escape) ui.input().consumeKey(GLFW_KEY_ESCAPE);
        // (the Colours page leaves the game in view beside it, to see the grade on)
        if (page != Page.COLOURS) ui.scrim();

        if (page == Page.MAIN) {
            if (escape) {
                close();
                return;
            }
            List<String> items = mainItems(game.settings().debugTools, backLabel);
            items.removeAll(hidden);
            // (as many items as there are, on whatever height the window has)
            float step = Math.max(38, Math.min(58, (ui.height() - 110f) / items.size()));
            float w = 360, h = 70 + items.size() * step;
            float x = (ui.width() - w) / 2, y = (ui.height() - h) / 2;
            ui.panel(x, y, w, h);
            ui.textCentered(ui.large, "Paused", x + w / 2, y + 18, Theme.TITLE);
            int chosen = menu.show(ui, items.toArray(String[]::new), x + 30, y + 64, w - 60, step - 10, 10);
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
                case "Compendium" -> {
                    page = Page.COMPENDIUM;
                    compendium.open();
                }
                case "Settings" -> page = Page.SETTINGS;
                case "Colours" -> page = Page.COLOURS;
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
                    if (leaving != null) leaving.run();
                    game.switchTo("menu");
                }
                case "Quit" -> {
                    if (leaving != null) leaving.run();
                    game.quit();
                }
                default -> { }
            }
            return;
        }

        float w = Math.min(1100, ui.width() - 40), h = Math.min(640, ui.height() - 40);
        if (page == Page.COMPENDIUM) {
            w = Math.min(1500, ui.width() - 40);
            h = Math.min(1000, ui.height() - 30);
        }
        float x = (ui.width() - w) / 2, y = (ui.height() - h) / 2;
        if (page == Page.COLOURS) {
            float[] b = ColoursPanel.box(ui);
            x = b[0];
            y = b[1];
            w = b[2];
            h = b[3];
        }
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
            case COMPENDIUM -> "Compendium";
            case SETTINGS -> "Settings";
            case COLOURS -> "Colours";
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
            case COMPENDIUM -> compendium.draw(ui, cx, cy - 12, cw, ch + 12);
            case SETTINGS -> settings(ui, cx, cy);
            case COLOURS -> colours.draw(ui, cx, cy - 10, cw, ch + 10);
            case SOUNDS -> sounds.draw(ui, cx, cy, cw, ch);
            case CONTROLS -> controls.draw(ui, cx, cy, cw, ch);
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
