package com.larsons.game.scene;

import com.larsons.game.audio.Music;
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
import com.larsons.game.save.SaveGame;
import com.larsons.game.save.Saves;
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
 *   <li><b>Horde</b> - the horde maps ({@code assets/horde/}): each with
 *       the best round reached on it, to play. With the Dev tab unlocked, a
 *       new horde map, and each to edit, copy or delete: the horde editor
 *       ({@link CreativeScene#horde}).</li>
 *   <li><b>Cutscene</b> - in dev mode only: the main menu's cutscene
 *       ({@code assets/cutscenes/menu.cut}) plays behind it, over and over:
 *       your character talking with Bryn, in close-up layers; "Watch from
 *       the start" plays it full screen, once through.</li>
 *   <li><b>Dev</b> - in dev mode only: Creative Mode. A new level - empty,
 *       or the demo copied - and every level saved in {@code
 *       assets/levels/}, each to edit, play, copy or delete; the world map
 *       and horde map editors.</li>
 * </ul>
 *
 * <p>Dev mode is turned on on the Play tab's <b>Codes</b> page: the dev code
 * 1 0 2 5, typed (the number row or the num-pad) or tapped on its keypad
 * (the gamepad's way), shows the Dev and Cutscene tabs and unlocks every
 * cosmetic; the same page turns it off. It stays on between runs ({@code
 * Settings.devUnlocked}).
 *
 * <p>Left and right (or Q and E) change tab; up, down and Enter choose.
 *
 * <p>The menu's own song ({@link #MUSIC}: "Dream On" for lute, recorder and
 * harp, {@code assets/sounds/music/main_menu.mp3}) loops behind every tab,
 * fading in as the menu opens and out as it is left ({@link Music}).
 */
public final class MainMenuScene implements Scene {

    /** The tabs. */
    enum Tab {
        PLAY("Play"), HORDE("Horde"), CUTSCENE("Cutscene"), DEV("Dev");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    /** How long a Delete waits for its second click, seconds. */
    static final double CONFIRM = 3;
    /** How many saved levels the Dev tab lists at once (the wheel scrolls). */
    static final int ROWS = 7;
    /** The song that plays on the menu. */
    public static final String MUSIC = SoundKeys.music("main_menu");

    private final Game game;
    private final World world;
    private final OrbitCamera camera = new OrbitCamera(0.5, Math.toRadians(22), 5.2);
    private final MenuList menu = new MenuList();
    private final CutsceneStage stage;
    private final HowToPlay how;
    private Cutscene cutscene;
    private double time;
    /** The tab last shown: the menu opens on it again (coming back from the editor, on the Dev tab). */
    private static Tab lastTab = Tab.PLAY;
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
        world.player().preview(AnimState.REST);
        if (lastTab != Tab.DEV && lastTab != Tab.CUTSCENE || game.settings().devUnlocked) tab = lastTab;
        this.stage = new CutsceneStage(game.sprites(), game.closeups());
        this.how = new HowToPlay(game);
        how.offer("Practise in the training yard");
        if (!game.closeups().isEmpty()) {
            try {
                cutscene = Cutscene.load(game.menuCutscene(), game::wardrobe);
            } catch (Exception e) {
                System.err.println("[cutscene] " + e.getMessage());
            }
        }
    }

    /** Open the menu on its Dev tab next time (leaving the level editor). */
    public static void openOnDevTab() {
        lastTab = Tab.DEV;
    }

    /** Open the menu on its Horde tab next time (leaving a horde game, or the horde editor). */
    public static void openOnHordeTab() {
        lastTab = Tab.HORDE;
    }

    private List<LevelIO.Saved> hordes = List.of();
    private int hordeScroll;
    /** How many horde maps the Horde tab lists at once. */
    static final int HORDE_ROWS = 5;

    private void refreshHordes() {
        hordes = LevelIO.list(game.settings().hordeDir());
        hordeScroll = Math.max(0, Math.min(hordeScroll, hordes.size() - HORDE_ROWS));
    }

    /** The Horde tab: every horde map, its best round, Play - and, in dev mode, the horde editor on each. */
    private void hordeTab(Ui ui, float x, float y, float width) {
        boolean dev = game.settings().devUnlocked;
        ui.text(ui.large, "The Horde", x, y, Theme.TITLE);
        ui.paragraph(ui.small, "The Risen come in rounds, more and tougher each. Blows and kills pay points: buy the "
                + "weapons chalked on the boards, try the mystery box, nail the barricades back up. How long can you hold?",
                x, y + 38, width, Theme.HINT);
        float ly = y + 104;
        if (dev) {
            if (ui.button("New horde map", x, ly, 200, 40)) {
                Level level = Level.blank(LevelIO.freeName(game.settings().hordeDir(), "New horde map"), 128);
                game.switchTo(CreativeScene.horde(game, level, null));
            }
            ui.text(ui.small, "Dev: the horde editor. Maps are saved in " + game.relative(game.settings().hordeDir()),
                    x + 212, ly + 12, Theme.ITEM_DISABLED);
            ly += 52;
        }
        if (hordes.isEmpty()) {
            ui.text(ui.body, "No horde maps yet", x, ly, Theme.ITEM_DISABLED);
            return;
        }
        int end = Math.min(hordes.size(), hordeScroll + HORDE_ROWS);
        for (int i = hordeScroll; i < end; i++) {
            LevelIO.Saved s = hordes.get(i);
            float rh = 58;
            ui.plate(x - 8, ly - 4, width + 8, rh, Theme.BRASS_DARK);
            ui.text(ui.body, Ui.fit(ui.body, s.name(), width - (dev ? 320 : 130)), x + 4, ly + 2, Theme.TITLE);
            int best = HordeGame.best(game, s.id());
            ui.text(ui.small, best > 0 ? "Best: round " + best : "Not yet played", x + 4, ly + 28,
                    best > 0 ? Theme.ACCENT : Theme.ITEM_DISABLED);
            float bx = x + width - (dev ? 4 * 72 : 96) - 6;
            if (ui.button("Play", bx, ly + 6, dev ? 66 : 96, 38)) game.switchTo("horde:" + s.id());
            if (dev) {
                if (ui.button("Edit", bx + 72, ly + 6, 66, 38)) game.switchTo("hordeedit:" + s.id());
                if (ui.button("Copy", bx + 144, ly + 6, 66, 38)) copy(s, game.settings().hordeDir());
                boolean sure = s.folder().equals(deleting);
                if (ui.button(sure ? "Sure?" : "Delete", bx + 216, ly + 6, 66, 38, sure, true)) delete(s);
            }
            ly += rh + 8;
        }
        if (hordes.size() > HORDE_ROWS) {
            ui.text(ui.small, (hordeScroll + 1) + "–" + end + " of " + hordes.size() + " (the wheel scrolls)", x, ly + 2,
                    Theme.HINT);
        }
    }

    @Override
    public String name() { return "Main Menu"; }

    @Override
    public void enter() {
        camera.snapTo(follow());
        refreshLevels();
        refreshHordes();
        Saves.stop();
        refreshSlots();
        Music.play(MUSIC);
    }

    @Override
    public void exit() {
        Music.stop();
    }

    @Override
    public String loaded() {
        String saved = levels.isEmpty() ? "no levels yet" : levels.size() + " level" + (levels.size() == 1 ? "" : "s");
        return "Creative Mode: " + saved + (cutscene != null ? ", the cutscene" : "");
    }

    /** The tabs shown: the Cutscene and Dev tabs only in dev mode. */
    List<Tab> tabs() {
        List<Tab> out = new ArrayList<>(List.of(Tab.PLAY, Tab.HORDE));
        if (game.settings().devUnlocked) {
            out.add(Tab.CUTSCENE);
            out.add(Tab.DEV);
        }
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
            if (tab == Tab.PLAY && playPage == PlayPage.CODES) {
                // the Codes page: digits typed go in its box, Backspace takes one off, Enter tries it
                for (int key = GLFW_KEY_0; key <= GLFW_KEY_KP_9; key++) {
                    int d = DebugCode.anyDigit(key);
                    if (d >= 0 && in.pressed(key)) typeDigit(d);
                }
                if (in.repeated(GLFW_KEY_BACKSPACE) && !code.isEmpty()) code = code.substring(0, code.length() - 1);
                if (in.pressed(GLFW_KEY_ENTER) || in.pressed(GLFW_KEY_KP_ENTER)) {
                    in.consumeKey(GLFW_KEY_ENTER);
                    tryCode();
                }
            }
            List<Tab> tabs = tabs();
            int at = Math.max(0, tabs.indexOf(tab));
            boolean reading = tab == Tab.PLAY && playPage == PlayPage.HOW;
            int step = reading ? 0 : in.takeTab();
            if (!reading && (in.pressed(GLFW_KEY_RIGHT) || in.pressed(GLFW_KEY_E)) || step > 0) {
                show(tabs.get((at + 1) % tabs.size()));
            }
            if (!reading && (in.pressed(GLFW_KEY_LEFT) || in.pressed(GLFW_KEY_Q)) || step < 0) {
                show(tabs.get((at + tabs.size() - 1) % tabs.size()));
            }
            if (tab == Tab.DEV && in.scroll() != 0) {
                scroll = Math.max(0, Math.min(levels.size() - ROWS, scroll - (int) Math.signum(in.scroll())));
            }
            if (tab == Tab.HORDE && in.scroll() != 0) {
                hordeScroll = Math.max(0, Math.min(hordes.size() - HORDE_ROWS,
                        hordeScroll - (int) Math.signum(in.scroll())));
            }
        }
        if (deleting != null && time - deleteAt > CONFIRM) deleting = null;
        if (tab == Tab.CUTSCENE && cutscene != null) {
            cutscene.update(dt);
            return;
        }
        world.setScenery(game.scenery());
        world.tick(dt);
        pose(dt);
        world.player().update(dt, com.larsons.game.world.Player.Intent.NONE,
                s -> s.defaultFrames() / 30.0);
        camera.rotate(dt * 0.05, 0);
        camera.update(dt, follow());
    }

    /** The weapons' stances she now and then stands in on the menu, between spells at rest. */
    private static final AnimState[] WEAPON_IDLES = {AnimState.BLADE_IDLE, AnimState.STAFF_IDLE, AnimState.BOW_IDLE,
            AnimState.MACE_IDLE, AnimState.DAGGER_IDLE, AnimState.WAND_IDLE, AnimState.AXE_IDLE, AnimState.IDLE,
            AnimState.CROSSBOW_IDLE};
    private final java.util.Random poses = new java.util.Random();
    private double poseLeft = 16;
    private int poseCount;
    private AnimState lastWeapon;

    /**
     * Her stance on the menu: at rest, empty-handed, most of the time - and
     * now and then one weapon's or another's - turning a step to one side or
     * the other as she changes (not round and round).
     */
    private void pose(double dt) {
        poseLeft -= dt;
        if (poseLeft > 0) return;
        poseCount++;
        boolean rest = poseCount % 2 == 0;
        AnimState next = AnimState.REST;
        if (!rest) {
            do next = WEAPON_IDLES[poses.nextInt(WEAPON_IDLES.length)]; while (next == lastWeapon);
            lastWeapon = next;
        }
        world.player().preview(next);
        if (poses.nextInt(3) > 0) world.player().turnSteps(poses.nextBoolean() ? 1 : -1);
        poseLeft = rest ? 14 + poses.nextDouble() * 8 : 7 + poses.nextDouble() * 4;
    }

    private void show(Tab t) {
        if (t == tab) return;
        tab = t;
        lastTab = t;
        menu.setFocus(0);
        if (t == Tab.DEV) refreshLevels();
        if (t == Tab.HORDE) refreshHordes();
    }

    /**
     * Dev mode on or off: the Dev and Cutscene tabs shown and every cosmetic
     * unlocked - or hidden and locked again (what was unlocked by dyes stays).
     */
    private void setDev(boolean on) {
        var s = game.settings();
        s.devUnlocked = on;
        s.debugTools = false;
        com.larsons.game.world.Wearables.setEverything(on);
        s.save();
        Sounds.play(SoundKeys.ui(on ? "secret" : "back"));
        if (on) {
            game.toast("Dev mode on: the Dev and Cutscene tabs, and every cosmetic unlocked", Theme.ACCENT);
        } else {
            if (tab == Tab.DEV || tab == Tab.CUTSCENE) show(Tab.PLAY);
            game.toast("Dev mode off", Theme.HINT);
        }
    }

    // --- the Codes page ------------------------------------------------------------------

    /** What has been typed on the Codes page, what it said of the last code tried and when. */
    private String code = "", codeSaid = "";
    private double codeAt = -10;
    /** The longest code there is. */
    static final int CODE_LENGTH = 8;

    private void typeDigit(int d) {
        if (code.length() < CODE_LENGTH) {
            code += d;
            Sounds.play(SoundKeys.ui("click"));
        }
    }

    /** Try what has been typed: the dev code turns dev mode on (or, on already, says so). */
    private void tryCode() {
        String typed = code;
        code = "";
        codeAt = time;
        if (typed.equals(DEV_CODE)) {
            if (game.settings().devUnlocked) {
                codeSaid = "Dev mode is already on.";
                Sounds.play(SoundKeys.ui("click"));
            } else {
                setDev(true);
                codeSaid = "Dev mode on: the Dev and Cutscene tabs are along the top, every cosmetic yours.";
            }
        } else {
            codeSaid = typed.isEmpty() ? "Type a code first." : "Nothing happens.";
            Sounds.play(SoundKeys.ui("error"));
        }
    }

    /** The dev code: dev mode (the Dev and Cutscene tabs, every cosmetic). */
    static final String DEV_CODE = "1025";

    /**
     * The Codes page: a box the code goes in - typed on the number row or the
     * num-pad, or tapped on the keypad under it (the gamepad's way) - Enter to
     * try it; and, in dev mode, what it has unlocked and the way to turn it off.
     */
    private void codesPage(Ui ui, float x, float y) {
        boolean dev = game.settings().devUnlocked;
        ui.text(ui.large, "Codes", x, y, Theme.TITLE);
        ui.paragraph(ui.small, "Secret codes, for those who know them. Type one on the number keys or tap it on the "
                + "keypad, then Enter.", x, y + 40, 420, Theme.HINT);
        float by = y + 88, bw = 300, bh = 44;
        // the box: what has been typed, a dot for each digit still to come
        ui.plate(x - 6, by - 6, bw + 12, bh + 12, Theme.BRASS_DARK);
        ui.rect(x, by, bw, bh, Theme.withAlpha(Theme.SOOT, 0.85f));
        StringBuilder shown = new StringBuilder();
        for (int i = 0; i < Math.max(4, code.length()); i++) {
            if (i > 0) shown.append(' ');
            shown.append(i < code.length() ? code.charAt(i) : '·');
        }
        boolean blink = (int) (time * 2) % 2 == 0;
        ui.textCentered(ui.large, shown + (blink && code.length() < CODE_LENGTH ? "_" : " "), x + bw / 2,
                by + (bh - ui.large.lineHeight()) / 2, code.isEmpty() ? Theme.ITEM_DISABLED : Theme.TITLE);
        // the keypad: 1-9, then clear, 0, enter
        float ky = by + bh + 14, kw = (bw - 12) / 3, kh = 36;
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "Clear", "0", "Enter"};
        for (int i = 0; i < keys.length; i++) {
            float kx = x + (i % 3) * (kw + 6), kyy = ky + (i / 3) * (kh + 6);
            String k = keys[i];
            boolean enter = k.equals("Enter");
            if (ui.button(k, kx, kyy, kw, kh, enter, true)) {
                if (enter) tryCode();
                else if (k.equals("Clear")) code = "";
                else typeDigit(k.charAt(0) - '0');
            }
        }
        // what the last code did - or, in dev mode, what it has unlocked
        float ny = ky + 4 * (kh + 6) + 4;
        boolean said = !codeSaid.isEmpty() && time - codeAt < 8;
        float fy = ny;
        if (said) {
            boolean bad = codeSaid.startsWith("Nothing") || codeSaid.startsWith("Type");
            fy += ui.paragraph(ui.small, codeSaid, x, ny, 420, bad ? Theme.WARNING : Theme.OK) + 8;
        } else if (dev) {
            fy += ui.paragraph(ui.small, "Dev mode is on: the Dev and Cutscene tabs are along the top, and every "
                    + "cosmetic is unlocked.", x, ny, 420, Theme.ACCENT) + 8;
        }
        boolean back = ui.button("Back", x, fy, 140, 38);
        if (dev && ui.button("Turn dev mode off", x + 150, fy, 220, 38)) {
            setDev(false);
            codeSaid = "Dev mode off.";
            codeAt = time;
        }
        if (back || ui.input().pressed(GLFW_KEY_ESCAPE)) {
            ui.input().consumeKey(GLFW_KEY_ESCAPE);
            playPage = PlayPage.MAIN;
            code = "";
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
        if (tab == Tab.PLAY && playPage == PlayPage.HOW && !game.importer().visible()) {
            howToPlay(ui);
            ui.end();
            return;
        }
        // A soft band behind the menu for legibility over the void.
        float band = tab == Tab.DEV || tab == Tab.HORDE ? 620 : 520;
        ui.rect(0, 0, band, ui.height(), Theme.withAlpha(Theme.BACKGROUND, 0.78f));
        float x = 64, y = ui.height() * 0.12f;
        ui.textShadowed(ui.title, "Larson's Game", x, y, Theme.TITLE);
        ui.text(ui.body, "A moon of oak, brass and old magic", x + 4, y + 72, Theme.ACCENT);

        // (while it asks whether to quit, what is under the question hears nothing)
        boolean asking = quitting, deaf = ui.input().muted();
        if (asking && !deaf) ui.input().setMuted(true);
        // The importer, when open, is modal: it draws over this and takes the keys.
        if (!game.importer().visible()) {
            float ty = y + 116;
            drawTabs(ui, x, ty);
            float cy = ty + 58;
            switch (tab) {
                case PLAY -> playTab(ui, x, cy);
                case HORDE -> hordeTab(ui, x, cy, band - x - 24);
                case CUTSCENE -> cutsceneTab(ui, x, cy);
                case DEV -> devTab(ui, x, cy, band - x - 24);
            }
            if (ui.input().pressed(GLFW_KEY_ESCAPE)) {
                ui.input().consumeKey(GLFW_KEY_ESCAPE);
                if (quitting) quitting = false;
                else if (tab == Tab.PLAY && playPage != PlayPage.MAIN) playPage = PlayPage.MAIN;
                else quitting = true;
            }
        }
        // the settings, from every tab (and F10)
        if (ui.button("Settings", ui.width() - 170, 20, 150, 40)) game.openSettings();
        if (asking) {
            if (!deaf) ui.input().setMuted(false);
            if (ui.input().pressed(GLFW_KEY_ESCAPE)) {
                ui.input().consumeKey(GLFW_KEY_ESCAPE);
                quitting = false;
            } else {
                askQuit(ui);
            }
        }

        float fy = ui.height() - 96;
        ui.text(ui.small, "GPU: " + game.window().gpu().describe(), x, fy + 44,
                game.window().gpu().software() ? Theme.WARNING : Theme.ITEM_DISABLED);
        ui.end();
    }

    /** How to play, over the menu's world: every chapter, Back (or Esc) to the menu. */
    private void howToPlay(Ui ui) {
        ui.rect(0, 0, ui.width(), ui.height(), Theme.withAlpha(Theme.BACKGROUND, 0.55f));
        float w = Math.min(1500, ui.width() - 48), h = Math.min(980, ui.height() - 40);
        float x = (ui.width() - w) / 2, y = (ui.height() - h) / 2;
        ui.panel(x, y, w, h);
        ui.text(ui.large, "How to play", x + 26, y + 18, Theme.TITLE);
        ui.text(ui.small, ui.input().usingPad() ? "LB and RB turn the chapters" : "Up and down turn the chapters",
                x + 30 + ui.large.width("How to play") + 16, y + 30, Theme.ITEM_DISABLED);
        boolean escape = ui.input().pressed(GLFW_KEY_ESCAPE);
        if (escape) ui.input().consumeKey(GLFW_KEY_ESCAPE);
        if (ui.button("Back", x + w - 126, y + 14, 100, 36) || escape || ui.input().pressed(GLFW_KEY_BACKSPACE)) {
            playPage = PlayPage.MAIN;
            Sounds.play(SoundKeys.ui("back"));
            return;
        }
        if (how.draw(ui, x + 26, y + 66, w - 52, h - 86)) game.switchTo("demo");
    }

    /** Esc on the menu asks before it closes the game. */
    private boolean quitting;

    private void askQuit(Ui ui) {
        ui.scrim();
        float w = 420, h = 170, x = (ui.width() - w) / 2, y = (ui.height() - h) / 2;
        ui.panel(x, y, w, h);
        ui.textCentered(ui.large, "Quit Larson's Game?", x + w / 2, y + 26, Theme.TITLE);
        if (ui.button("Quit", x + 40, y + 96, 160, 44)) game.quit();
        if (ui.button("Stay", x + w - 200, y + 96, 160, 44, true, true)
                || ui.input().pressed(GLFW_KEY_ENTER) && !ui.input().usingPad()) {
            quitting = false;
        }
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

    /** The Play tab's pages: its list, the slots to begin a new game in, the slots to load, how to play, codes. */
    private enum PlayPage { MAIN, NEW, LOAD, HOW, CODES }

    private PlayPage playPage = PlayPage.MAIN;
    private final SaveGame[] slots = new SaveGame[Saves.SLOTS];
    private int sureSlot = -1;
    private double sureAt;

    /** The world maps there are, and the one each slot is to be played on (a new game's: {@link #newMap}). */
    private List<com.larsons.game.overworld.Overworld.Saved> worldMaps = List.of();
    private final String[] slotMap = new String[Saves.SLOTS];
    private String newMap = "";

    private void refreshSlots() {
        for (int i = 0; i < Saves.SLOTS; i++) {
            slots[i] = Saves.peek(i);
            slotMap[i] = slots[i] == null ? "" : slots[i].map();
        }
        worldMaps = com.larsons.game.overworld.Overworld.list(game.settings().assets);
        String usual = com.larsons.game.overworld.Overworld.campaignStart(game.settings().assets);
        if (newMap.isEmpty() || worldMaps.stream().noneMatch(m -> m.id().equals(newMap))) newMap = usual;
    }

    /** What world map {@code id} is called ("Main story: The Brass Vale"; a missing one: its id). */
    private String mapName(String id) {
        for (var m : worldMaps) if (m.id().equals(id)) return m.shown();
        return id == null || id.isBlank() ? "the usual map" : id + " (missing)";
    }

    /** The world map after (or before) {@code id} in the list. */
    private String nextMap(String id, int step) {
        if (worldMaps.isEmpty()) return id;
        int at = 0;
        for (int i = 0; i < worldMaps.size(); i++) if (worldMaps.get(i).id().equals(id)) at = i;
        return worldMaps.get(Math.floorMod(at + step, worldMaps.size())).id();
    }

    /** "<  Main story: The Brass Vale  >": a world map to step through; returns the one now chosen. */
    private String mapPicker(Ui ui, String id, float x, float y, float w, float h) {
        return mapPicker(ui, id, x, y, w, h, Theme.TITLE);
    }

    private String mapPicker(Ui ui, String id, float x, float y, float w, float h, float[] colour) {
        boolean many = worldMaps.size() > 1;
        float aw = Math.min(36, h + 6);
        if (ui.button("<", x, y, aw, h, false, many)) id = nextMap(id, -1);
        ui.plate(x + aw + 4, y, w - 2 * aw - 8, h, Theme.BRASS_DARK);
        ui.textCentered(ui.small, Ui.fit(ui.small, mapName(id), w - 2 * aw - 16), x + w / 2, y + (h - 16) / 2, colour);
        if (ui.button(">", x + w - aw, y, aw, h, false, many)) id = nextMap(id, 1);
        return id;
    }

    private void playTab(Ui ui, float x, float y) {
        if (playPage == PlayPage.CODES) {
            codesPage(ui, x, y);
            return;
        }
        if (playPage != PlayPage.MAIN) {
            slotsPage(ui, x, y);
            return;
        }
        int latest = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && (latest < 0 || slots[i].saved().compareTo(slots[latest].saved()) > 0)) latest = i;
        }
        List<String> items = new ArrayList<>();
        if (latest >= 0) items.add("Continue");
        items.add("New Game");
        if (latest >= 0) items.add("Load Game");
        items.add("How to play");
        items.add("Demo");
        items.add("Codes");
        items.add("Settings");
        items.add("Quit");
        // (eight of them - a saved game to go on with, the Codes - are a little shorter, to fit a 720-high window)
        float itemH = Math.max(40, Math.min(50, (ui.height() - y - 24) / items.size() - 10));
        int chosen = quitting ? -1 : menu.show(ui, items.toArray(String[]::new), x, y, 300, itemH, 10);
        if (latest >= 0) {
            SaveGame s = slots[latest];
            ui.text(ui.small, s.name() + " · " + describe(s), x + 314, y + 18, Theme.HINT);
        }
        switch (chosen < 0 ? "" : items.get(chosen)) {
            case "Continue" -> load(latest);
            case "New Game" -> {
                playPage = PlayPage.NEW;
                sureSlot = -1;
            }
            case "Load Game" -> {
                playPage = PlayPage.LOAD;
                sureSlot = -1;
            }
            case "How to play" -> {
                playPage = PlayPage.HOW;
                how.open();
            }
            case "Demo" -> game.switchTo("demo");
            case "Codes" -> {
                playPage = PlayPage.CODES;
                code = "";
            }
            case "Settings" -> game.openSettings();
            case "Quit" -> quitting = true;
            default -> { }
        }
    }

    /** "Mage, level 3 · 1 h 20 min". */
    private static String describe(SaveGame s) {
        var hero = com.larsons.game.cards.CardBook.get().hero(s.heroClass());
        return (hero == null ? "" : hero.name() + ", level " + s.level() + " · ") + s.playedFor();
    }

    /** The three save slots: to begin a new game in one, or to load (or delete) one. */
    private void slotsPage(Ui ui, float x, float y) {
        boolean fresh = playPage == PlayPage.NEW;
        ui.text(ui.large, fresh ? "New game: choose a slot" : "Load a game", x, y, Theme.TITLE);
        // (a saved game's row holds the world map it is played on as well)
        float ry = y + 44, rw = 430, rh = fresh ? 84 : 92;
        if (fresh) {
            // which world map the new playthrough sets out on: the main story's, or one made in the map editor
            ui.text(ui.small, "World map", x, ry + 8, Theme.ACCENT);
            newMap = mapPicker(ui, newMap, x + 90, ry, rw - 90, 32);
            ry += 44;
        }
        if (sureSlot >= 0 && time - sureAt > CONFIRM) sureSlot = -1;
        for (int i = 0; i < Saves.SLOTS; i++) {
            SaveGame s = slots[i];
            ui.plate(x - 6, ry, rw + 12, rh, Theme.BRASS_DARK);
            ui.text(ui.small, "Slot " + (i + 1), x + 6, ry + 6, Theme.ACCENT);
            float bx = x + rw - 104;
            if (s == null) {
                ui.text(ui.body, "Empty", x + 6, ry + 28, Theme.ITEM_DISABLED);
                if (fresh && ui.button("Start here", bx, ry + 22, 100, 38)) create(i);
            } else if (!fresh) {
                // its world map along the top (another can be chosen: she goes where she last was on
                // it), then its name and how far it has come, Load and Delete beside them
                if (!worldMaps.isEmpty()) {
                    boolean changed = !slotMap[i].equals(s.map());
                    slotMap[i] = mapPicker(ui, slotMap[i], x + 70, ry + 4, rw - 76, 24,
                            changed ? Theme.ACCENT : Theme.TITLE);
                }
                ui.text(ui.body, Ui.fit(ui.body, s.name(), rw - 124), x + 6, ry + 34, Theme.TITLE);
                ui.text(ui.small, Ui.fit(ui.small, describe(s), rw - 124), x + 6, ry + 64, Theme.HINT);
                boolean sure = sureSlot == i;
                if (ui.button("Load", bx, ry + 32, 100, 28)) load(i);
                if (ui.button(sure ? "Sure?" : "Delete", bx, ry + 62, 100, 26, sure, true)) {
                    if (sure) {
                        Saves.delete(i);
                        refreshSlots();
                        sureSlot = -1;
                        game.toast("Deleted slot " + (i + 1), Theme.HINT);
                    } else {
                        confirm(i);
                    }
                }
            } else {
                ui.text(ui.body, Ui.fit(ui.body, s.name(), rw - 230), x + 6, ry + 26, Theme.TITLE);
                ui.text(ui.small, describe(s), x + 6, ry + 54, Theme.HINT);
                boolean sure = sureSlot == i;
                if (ui.button(sure ? "Sure?" : "Overwrite", bx, ry + 22, 100, 38, sure, true)) {
                    if (sure) create(i);
                    else confirm(i);
                }
            }
            ry += rh + 10;
        }
        if (ui.button("Back", x, ry + 6, 140, 40) || ui.input().pressed(GLFW_KEY_BACKSPACE)) playPage = PlayPage.MAIN;
    }

    private void confirm(int slot) {
        sureSlot = slot;
        sureAt = time;
    }

    /** Begin a new game in {@code slot}: the character creator, for the world map chosen. */
    private void create(int slot) {
        game.switchTo(new CreatorScene(game, slot, newMap));
    }

    /** Take up the playthrough in {@code slot}: its map, where she was. */
    private void load(int slot) {
        SaveGame s = Saves.peek(slot);
        if (s == null) {
            game.toast("Slot " + (slot + 1) + " cannot be read", Theme.WARNING);
            refreshSlots();
            return;
        }
        // another world map chosen for it: she goes to where she last stood on that one (or its start)
        String chosen = slotMap[slot];
        if (chosen != null && !chosen.isBlank() && !chosen.equals(s.map())) s.goToMap(chosen);
        Saves.play(s, slot);
        game.switchTo(new OverworldScene(game, s));
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
        String[] items = {"New level", "Copy the demo"};
        int chosen = menu.show(ui, items, x, by, 300, 46, 10);
        if (chosen == 0) newLevel(false);
        if (chosen == 1) newLevel(true);
        // the overworld's maps: where a playthrough's destinations are set, each to the level it loads
        if (ui.button("World map editor", x + 316, by, 220, 46)) {
            game.switchTo(new com.larsons.game.editor.OverworldEditor(game, null));
        }
        // the horde editor: a new horde map (the saved ones are on the Horde tab, each to edit)
        if (ui.button("Horde map editor", x + 316, by + 56, 220, 46)) {
            Level level = Level.blank(LevelIO.freeName(game.settings().hordeDir(), "New horde map"), 128);
            game.switchTo(CreativeScene.horde(game, level, null));
        }

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
        copy(s, game.settings().levelsDir());
    }

    private void copy(LevelIO.Saved s, Path root) {
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
        refreshHordes();
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
        refreshHordes();
    }
}
