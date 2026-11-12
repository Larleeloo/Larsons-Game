package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Game;
import com.larsons.game.gfx.Window;
import com.larsons.game.input.Action;
import com.larsons.game.ui.Pointers;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * Every setting of the game in one menu, over whatever screen is up - the
 * main menu, the game, the editors, the map, the character creator, a
 * cutscene: F10 anywhere (rebindable), a Settings button on each screen,
 * Pause, Settings in the game. Its tabs: Display (how the window is shown,
 * the frame rate, vsync, the sprites' resolution), Game (the HUD, the camera,
 * walls, scenery, picking things up, the tutorial's tips), Pointer, Sounds,
 * Controls and Colours. LB / RB or Ctrl+Tab change tabs; Esc, B or Back
 * closes it. While it is up the screen under it stands still and hears
 * nothing ({@code core/Game}).
 */
public final class SettingsMenu {

    public enum Tab {
        DISPLAY("Display"), GAME("Game"), POINTER("Pointer"), SOUNDS("Sounds"), CONTROLS("Controls"),
        COLOURS("Colours");

        final String label;

        Tab(String label) { this.label = label; }
    }

    private final Game game;
    private final SoundsPanel sounds;
    private final ControlsPanel controls;
    private final ColoursPanel colours;
    private boolean open;
    private Tab tab = Tab.DISPLAY;
    private float scroll;

    public SettingsMenu(Game game) {
        this.game = game;
        this.sounds = new SoundsPanel(game);
        this.controls = new ControlsPanel(game);
        this.colours = new ColoursPanel(game);
    }

    public boolean open() { return open; }

    public void open(Tab t) {
        if (!open) Sounds.play(SoundKeys.ui("open"));
        open = true;
        show(t == null ? tab : t);
    }

    public void close() {
        if (!open) return;
        leave();
        open = false;
        game.settings().save();
        Sounds.play(SoundKeys.ui("back"));
    }

    /** Let go of what the pages hold on the graphics card (the game is closing). */
    public void dispose() {
        colours.close();
    }

    private void show(Tab t) {
        if (t != tab) leave();
        tab = t;
        scroll = 0;
        if (tab == Tab.SOUNDS) game.setDropTarget(sounds::drop);
    }

    /** Leaving a tab: the Sounds tab stops its sound and stops taking dropped files. */
    private void leave() {
        if (tab == Tab.SOUNDS) {
            sounds.leave();
            game.setDropTarget(null);
        }
        if (tab == Tab.CONTROLS) controls.leave();
        if (tab == Tab.COLOURS) colours.leave();
    }

    /** Draw and handle it, over everything else (the game calls it last). */
    public void draw(Ui ui) {
        if (!open) return;
        var in = ui.input();
        boolean capturing = tab == Tab.CONTROLS && controls.capturing();
        boolean escape = !capturing && (in.pressed(GLFW_KEY_ESCAPE) || in.pressed(Action.SETTINGS));
        if (escape) {
            in.consumeKey(GLFW_KEY_ESCAPE);
            close();
            return;
        }
        int step = capturing ? 0 : in.takeTab();
        if (step != 0) {
            Tab[] all = Tab.values();
            show(all[Math.floorMod(tab.ordinal() + step, all.length)]);
            Sounds.play(SoundKeys.ui("page"));
        }
        float x, y, w, h;
        if (tab == Tab.COLOURS) {
            // (the screen stays in view beside it, to see the grade on)
            float[] b = ColoursPanel.box(ui);
            x = b[0];
            y = b[1] - 0;
            w = b[2];
            h = b[3];
        } else {
            ui.scrim();
            w = Math.min(1120, ui.width() - 40);
            h = Math.min(700, ui.height() - 40);
            x = (ui.width() - w) / 2;
            y = (ui.height() - h) / 2;
        }
        ui.panel(x, y, w, h);
        ui.text(ui.large, "Settings", x + 24, y + 16, Theme.TITLE);
        if (ui.button("Close", x + w - 112, y + 14, 92, 34)) {
            close();
            return;
        }
        // the tabs: a row of brass plates, LB and RB (or Ctrl+Tab) step along them
        Tab[] all = Tab.values();
        float tx = x + 24, ty = y + 58, tw = Math.min(150, (w - 48 - (all.length - 1) * 6) / all.length);
        for (Tab t : all) {
            if (ui.button(t.label, tx, ty, tw, 34, t == tab, true) && t != tab) show(t);
            tx += tw + 6;
        }
        if (in.usingPad()) {
            ui.text(ui.tiny, "LB / RB: tabs", x + w - 120, ty + 40, Theme.ITEM_DISABLED);
        } else {
            ui.text(ui.tiny, "Ctrl+Tab: next tab  ·  " + in.label(Action.SETTINGS) + " or Esc: close",
                    x + w - 260, ty + 40, Theme.ITEM_DISABLED);
        }
        float cx = x + 24, cy = ty + 56, cw = w - 48, ch = h - (cy - y) - 16;
        switch (tab) {
            case DISPLAY -> scrolled(ui, cx, cy, cw, ch, () -> display(ui, cx, cy - scroll, cw));
            case GAME -> scrolled(ui, cx, cy, cw, ch, () -> gameplay(ui, cx, cy - scroll, cw));
            case POINTER -> pointer(ui, cx, cy, cw);
            case SOUNDS -> sounds.draw(ui, cx, cy, cw, ch);
            case CONTROLS -> controls.draw(ui, cx, cy, cw, ch);
            case COLOURS -> colours.draw(ui, cx, cy - 10, cw, ch + 10);
        }
    }

    /** A page taller than the window scrolls (the wheel, the right stick, the triggers). */
    private void scrolled(Ui ui, float x, float y, float w, float h, Runnable page) {
        ui.clip(x - 8, y - 4, w + 16, h + 4);
        ui.measure();
        page.run();
        float content = ui.lowest() + scroll - y;
        ui.unclip();
        float most = Math.max(0, content - h + 12);
        if (ui.hover(x - 8, y - 4, w + 16, h + 4)) scroll -= (float) ui.input().scroll() * 40;
        scroll = Math.max(0, Math.min(most, scroll));
        if (most > 0) {
            float bar = h * h / (h + most), at = y + (h - bar) * scroll / most;
            ui.rect(x + w + 2, at, 4, bar, Theme.withAlpha(Theme.BRASS, 0.7f));
        }
    }

    /** A row of choices; returns the one picked this frame, or -1. */
    private static int choices(Ui ui, String[] labels, int chosen, float x, float y, float bw) {
        int picked = -1;
        for (int i = 0; i < labels.length; i++) {
            if (ui.button(labels[i], x + i * (bw + 8), y, bw, 34, i == chosen, true)) picked = i;
        }
        return picked;
    }

    private static void name(Ui ui, String label, float x, float y) {
        ui.text(ui.body, label, x, y + 7, Theme.ITEM);
    }

    private static void note(Ui ui, String text, float x, float y, float w) {
        ui.paragraph(ui.small, text, x, y, w, Theme.ITEM_DISABLED);
    }

    // --- Display -----------------------------------------------------------------------

    private void display(Ui ui, float x, float y, float w) {
        var s = game.settings();
        var window = game.window();
        float c = x + 230;
        name(ui, "Window", x, y);
        Window.Mode[] modes = Window.Mode.values();
        String[] labels = {"Windowed", "Borderless full screen", "Exclusive full screen"};
        int picked = choices(ui, labels, window.mode().ordinal(), c, y, 230);
        if (picked >= 0) game.setDisplayMode(modes[picked]);
        y += 42;
        note(ui, "Borderless covers the screen without switching its mode: instant to alt-tab, and what most "
                + "displays do best. Exclusive gives the monitor to the game at its fastest refresh rate. "
                + in(ui, Action.FULLSCREEN) + " or Alt+Enter switches between the window and "
                + (Window.Mode.of(s.fullscreenKind, Window.Mode.BORDERLESS) == Window.Mode.FULLSCREEN
                ? "exclusive" : "borderless") + " full screen anywhere.", x, y, w);
        y += 56;

        var pacer = game.pacer();
        name(ui, "Frame rate", x, y);
        int[] rates = {30, 60, 120, 144, 0};
        String[] rl = {"30", "60", "120", "144", "Uncapped"};
        int at = -1;
        for (int i = 0; i < rates.length; i++) if (pacer.target() == rates[i]) at = i;
        int r = choices(ui, rl, at, c, y, 110);
        if (r >= 0) {
            s.setFps(rates[r]);
            pacer.setTarget(rates[r]);
        }
        y += 42;
        note(ui, String.format("Now %.0f fps, %.1f ms a frame, the longest %.1f ms  ·  %s", pacer.fps(),
                pacer.averageMs(), pacer.worstMs(), pacer.describe()), x, y, w);
        y += 34;
        name(ui, "Vsync", x, y);
        int v = choices(ui, new String[]{"On", "Off"}, pacer.vsync() ? 0 : 1, c, y, 110);
        if (v >= 0) {
            s.setVsync(v == 0);
            pacer.setVsync(v == 0);
        }
        y += 42;
        boolean held = pacer.advice() != null;
        note(ui, held ? String.format("The display runs at %d Hz but can do %d: exclusive full screen plays at %d Hz"
                        + " - or raise it in the system's display settings, or turn vsync off.",
                pacer.refresh(), pacer.fastest(), pacer.fastest())
                : "On: frames wait for the display (no tearing). Off: the game holds the frame rate itself.",
                x, y, w);
        y += 40;

        var lib = game.sprites();
        name(ui, "Sprite resolution", x, y);
        double[] scales = {1.0, 0.5, 0.25};
        int sc = -1;
        for (int i = 0; i < scales.length; i++) if (lib.scale() == scales[i]) sc = i;
        int sp = choices(ui, new String[]{"Full", "Half", "Quarter"}, sc, c, y, 110);
        if (sp >= 0) {
            s.spriteScale = scales[sp];
            lib.setScale(scales[sp]);
        }
        y += 42;
        note(ui, "Shrinks the characters' frames as they load, for graphics cards with less memory.", x, y, w);
        y += 34;

        name(ui, "Performance overlay", x, y);
        if (ui.button(s.perfOverlay ? "On" : "Off", c, y, 110, 34, s.perfOverlay, true)) {
            s.perfOverlay = !s.perfOverlay;
        }
        ui.text(ui.small, in(ui, Action.PERF) + " anywhere", c + 124, y + 9, Theme.ITEM_DISABLED);
        y += 50;

        var gpu = window.gpu();
        name(ui, "Renderer", x, y);
        ui.text(ui.body, gpu.describe(), c, y + 7, gpu.software() ? Theme.WARNING : Theme.OK);
        ui.text(ui.small, "OpenGL " + gpu.version() + " · MSAA " + gpu.samples() + "x · max texture "
                        + gpu.maxTexture() + " px · " + (pacer.refresh() <= 0 ? "display rate unknown"
                        : pacer.fastest() > pacer.refresh() ? "display " + pacer.refresh() + " Hz (can do "
                        + pacer.fastest() + ")" : "display " + pacer.refresh() + " Hz"),
                c, y + 34, Theme.ITEM_DISABLED);
        ui.text(ui.small, "Sprite cache: " + lib.residentCount() + " sheets, " + (lib.residentBytes() >> 20)
                + " MB of " + (s.vramBytes >> 20) + " MB", c, y + 54, Theme.ITEM_DISABLED);
    }

    // --- Game --------------------------------------------------------------------------

    private void gameplay(Ui ui, float x, float y, float w) {
        var s = game.settings();
        var lib = game.sprites();
        float c = x + 230;
        name(ui, "HUD", x, y);
        if (ui.button(s.showHud ? "Shown" : "Hidden", c, y, 110, 34, s.showHud, true)) s.showHud = !s.showHud;
        name(ui, "Missions on the HUD", x + 400, y);
        if (ui.button(s.missionTracker ? "Shown" : "Hidden", c + 400, y, 110, 34, s.missionTracker, true)) {
            s.missionTracker = !s.missionTracker;
        }
        y += 50;
        name(ui, "Tutorial tips", x, y);
        if (ui.button(s.tutorialTips ? "On" : "Off", c, y, 110, 34, s.tutorialTips, true)) {
            s.tutorialTips = !s.tutorialTips;
        }
        if (ui.button("Show them all again", c + 124, y, 200, 34)) {
            s.tipsSeen.clear();
            game.toast("The tips will come up again as you play", Theme.OK);
        }
        y += 42;
        note(ui, "A tip the first time something happens: the first foe, the first chest, swimming, nightfall. "
                + "How to play, on the main menu and in the pause menu, explains everything.", x, y, w);
        y += 40;

        name(ui, "Pick up by walking over", x, y);
        if (ui.button(s.autoPickup ? "On" : "Off", c, y, 110, 34, s.autoPickup, true)) s.autoPickup = !s.autoPickup;
        ui.text(ui.small, "Range", c + 130, y + 9, Theme.ITEM);
        double range = ui.slider(c + 190, y + 6, 220, 22, (s.pickupRange - 0.5) / 5.5);
        s.pickupRange = Math.round((0.5 + range * 5.5) * 4) / 4.0;
        ui.text(ui.small, String.format("%.2f m", s.pickupRange), c + 424, y + 9, Theme.ITEM);
        y += 42;
        note(ui, "Coins, ammunition and keys come to you by themselves when you are this near, and go "
                + "in your purse and quiver - never your bag.", x, y, w);
        y += 40;

        name(ui, "Camera height", x, y);
        if (ui.button(s.autoCamera ? "Follows the ground" : "Fixed", c, y, 200, 34, s.autoCamera, true)) {
            s.autoCamera = !s.autoCamera;
        }
        name(ui, "Scenery", x + 470, y);
        if (ui.button(s.showProps ? "Shown" : "Hidden", c + 400, y, 110, 34, s.showProps, true)) {
            s.showProps = !s.showProps;
        }
        y += 50;
        name(ui, "Walls indoors", x, y);
        if (ui.button(s.indoors().label(), c, y, 140, 34, s.indoors() != com.larsons.game.building.Walls.UP, true)) {
            s.wallsIndoors = s.indoors().next().name();
            game.worldRenderer().setWalls(s.indoors(), s.outdoors());
        }
        name(ui, "Outdoors", x + 470, y);
        if (ui.button(s.outdoors().label(), c + 400, y, 140, 34, s.outdoors() != com.larsons.game.building.Walls.UP,
                true)) {
            s.wallsOutdoors = s.outdoors().next().name();
            game.worldRenderer().setWalls(s.indoors(), s.outdoors());
        }
        y += 42;
        note(ui, "Up: every wall stands. Cut away: walls between the camera and you drop to a stub. "
                + "Down: all of them do.", x, y, w);
        y += 40;
        name(ui, "Animations", x, y);
        int a = choices(ui, new String[]{"New", "Classic (v1)"}, s.classicAnimations ? 1 : 0, c, y, 140);
        if (a >= 0) {
            s.classicAnimations = a == 1;
            lib.setClassic(a == 1);
        }
        y += 42;
        note(ui, "The weapons' moves were animated again; Classic shows the first version, to compare.", x, y, w);
    }

    // --- Pointer -----------------------------------------------------------------------

    private void pointer(Ui ui, float x, float y, float w) {
        var s = game.settings();
        name(ui, "Pointer", x, y);
        List<String> options = new ArrayList<>();
        options.add(Pointers.CLASS);
        options.addAll(Pointers.NAMES);
        options.add(Pointers.CUSTOM);
        options.add(Pointers.SYSTEM);
        float bw = (w - (options.size() - 1) * 4) / options.size();
        y += 40;
        for (int i = 0; i < options.size(); i++) {
            String o = options.get(i);
            if (ui.button(Pointers.label(o), x + i * (bw + 4), y, bw, 34, o.equals(s.pointer), true)) {
                s.pointer = o;
            }
        }
        y += 44;
        note(ui, "Your class's pointer follows your class (a wand for a Mage, a lute for a Bard). Custom: "
                + "drop a picture in assets/ui/pointers/custom.png (its top-left corner points).", x, y, w);
        y += 56;
        name(ui, "Changes with what it is over", x, y);
        if (ui.button(s.dynamicPointer ? "On" : "Off", x + 300, y, 110, 34, s.dynamicPointer, true)) {
            s.dynamicPointer = !s.dynamicPointer;
        }
        y += 44;
        note(ui, "Over a button it lights up; over someone to talk to it shows a speech bubble; over a foe, "
                + "swords; over a chest or something to take, a sack; over a door, a door; over a lock, a "
                + "padlock; over text, a text cursor; while pressed, it presses in.", x, y, w);
        y += 56;
        // the pointers it makes, to see
        String[] hints = {"", "click", "talk", "attack", "take", "open", "locked", "buy", "build", "slide", "no"};
        float px = x;
        for (String hint : hints) {
            float bx = px, by = y;
            ui.rect(bx, by, 74, 74, Theme.withAlpha(Theme.SOOT, 0.5f));
            ui.outline(bx, by, 74, 74, 1, Theme.BRASS_DARK);
            game.pointers().preview(ui, s.pointer, s.heroClass, hint, bx + 22, by + 14);
            ui.textCentered(ui.tiny, hint.isEmpty() ? "plain" : hint, bx + 37, by + 56, Theme.ITEM_DISABLED);
            px += 82;
            if (px + 74 > x + w) break;
        }
    }

    private static String in(Ui ui, Action a) {
        return ui.input().label(a);
    }
}
