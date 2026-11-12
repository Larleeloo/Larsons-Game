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
 * The pause menu: resume, the wardrobe (cosmetics), her class, the
 * compendium of every card ({@link Compendium}), how to play
 * ({@link HowToPlay}), the settings (the game's
 * one {@link SettingsMenu}: display, game, pointer, sounds, controls,
 * colours - the same over every screen), how to import sprite sheets, back
 * to the main menu, quit - and, once the debug code has been typed
 * ({@link DebugCode}), a button that clears every item off the ground
 * ({@link World#clearGround}).
 */
final class PauseMenu {

    enum Page { MAIN, WARDROBE, CLASS, COMPENDIUM, HOW, IMPORT, SETTINGS, COLOURS, SOUNDS, CONTROLS }

    private static final String[] MAIN_ITEMS = {
            "Resume", "Wardrobe", "Class", "Compendium", "How to play", "Settings", "Import sprite sheets",
            "Main menu", "Quit"};
    /** The debug tools' item, before "Main menu". */
    static final String CLEAR_GROUND = "Clear the ground (debug)";

    private final Game game;
    private final World world;
    private final MenuList menu = new MenuList();
    private final WardrobePanel wardrobe;
    private final Compendium compendium;
    private final HowToPlay how;
    private Page page = Page.MAIN;
    private boolean open;
    private String backLabel;
    private Runnable back, leaving;
    private final java.util.Set<String> hidden = new java.util.HashSet<>();

    PauseMenu(Game game, World world) {
        this.game = game;
        this.world = world;
        this.wardrobe = new WardrobePanel(game);
        this.compendium = new Compendium(world);
        this.how = new HowToPlay(game);
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
    }

    void open(Page p) {
        open = true;
        page = p;
        // (the settings' own tabs are the one menu's, over every screen)
        SettingsMenu.Tab tab = switch (p) {
            case SETTINGS -> SettingsMenu.Tab.DISPLAY;
            case COLOURS -> SettingsMenu.Tab.COLOURS;
            case SOUNDS -> SettingsMenu.Tab.SOUNDS;
            case CONTROLS -> SettingsMenu.Tab.CONTROLS;
            default -> null;
        };
        if (tab != null) {
            page = Page.MAIN;
            game.openSettings(tab);
        }
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

    private void leave() {
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
                case "How to play" -> {
                    page = Page.HOW;
                    how.open();
                }
                case "Settings" -> game.openSettings();
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
        if (page == Page.COMPENDIUM || page == Page.HOW) {
            w = Math.min(1500, ui.width() - 40);
            h = Math.min(1000, ui.height() - 30);
        }
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
            case CLASS -> "Class";
            case COMPENDIUM -> "Compendium";
            case HOW -> "How to play";
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
            case HOW -> how.draw(ui, cx, cy - 8, cw, ch + 8);
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
        String pick = ClassPicker.draw(ui, game.pointers(), hero.heroClass(), x, y + 18, w, h - 50);
        if (pick != null && !pick.equals(hero.heroClass())) {
            hero.choose(pick, world.player());
            s.heroClass = pick;
            s.save();
            Sounds.play(SoundKeys.ui("card_shuffle"));
            game.toast("You are a " + com.larsons.game.cards.CardBook.get().hero(pick).name()
                    + ": a fresh deck of 8, a hand of " + hero.deck().hand().size(), Theme.ACCENT);
        }
        ui.text(ui.small, "The mouse pointer is in Settings, Pointer.", x, y + h - 20, Theme.ITEM_DISABLED);
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
