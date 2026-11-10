package com.larsons.game.scene;

import com.larsons.game.audio.Music;
import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Songs;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Autopilot;
import com.larsons.game.core.Game;
import com.larsons.game.core.Scene;
import com.larsons.game.input.Action;
import com.larsons.game.input.Input;
import com.larsons.game.input.Pad;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelIO;
import com.larsons.game.overworld.MapView;
import com.larsons.game.overworld.Overworld;
import com.larsons.game.save.SaveGame;
import com.larsons.game.save.Saves;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.ui.MenuList;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The overworld map of a playthrough: she stands on a destination and walks
 * the roads between them, one destination at a time, as on the map of a
 * platform game.
 *
 * <ul>
 *   <li><b>Walking</b>: the move keys, the arrows or the stick take the road
 *       that sets off that way; a click on a destination walks her there by
 *       the roads she may take.</li>
 *   <li><b>Roads</b> onward are shut until the destination she stands on is
 *       cleared (or it is open from the start, or has no level); she may
 *       always go back the way she came.</li>
 *   <li><b>Entering</b> (Use, Jump or Enter; a click on where she stands)
 *       loads the destination's level, at its spawn; a destination that
 *       leads to another map takes her there.</li>
 *   <li><b>Saving</b>: her place is written to her save slot each time she
 *       arrives somewhere, and all of her when she comes back from a level.</li>
 * </ul>
 *
 * The maps are made in the Dev tab's map editor ({@code
 * editor/OverworldEditor}), which also plays one here to try it (nothing is
 * saved then).
 */
public final class OverworldScene implements Scene, Autopilot.Scriptable {

    /** How fast she walks the roads, tiles a second. */
    static final double SPEED = 5.5;

    private final Game game;
    private final SaveGame save;
    /** Back to the map editor, or null: a real playthrough. */
    private final Runnable back;
    private Overworld map;
    private String mapId;
    /** The destination she stands on (or left last, while walking). */
    private String at;
    private final ArrayDeque<String> route = new ArrayDeque<>();
    private List<float[]> walk;
    private String walkingTo;
    private double walked, walkLength;
    private float tx, ty, camX, camY;
    private boolean camSet;
    private Facing facing = Facing.SOUTH;
    private double time, saidShut = -10;
    private boolean menuOpen;
    private final MenuList menu = new MenuList();
    private final LayerStack.Memory memory = new LayerStack.Memory();
    private String hovered;

    /** The map of the playthrough {@code save}, where she was last. */
    public OverworldScene(Game game, SaveGame save) {
        this(game, save, null, null, null);
    }

    /**
     * {@code map} (called {@code id}) played to try it, from the editor
     * ({@code back} returns there); or, {@code map} null, {@code save}'s own.
     */
    public OverworldScene(Game game, SaveGame save, Overworld map, String id, Runnable back) {
        this.game = game;
        this.save = save;
        this.back = back;
        MapView.useAssets(game.settings().assets);
        if (map != null) {
            this.map = map;
            this.mapId = id;
            if (!id.equals(save.map())) save.setPlace(id, "");
        } else {
            open(save.map());
        }
        place();
    }

    private void open(String id) {
        Overworld m = Overworld.byId(game.settings().assets, id);
        if (m == null) {
            id = Overworld.campaignStart(game.settings().assets);
            m = Overworld.byId(game.settings().assets, id);
        }
        if (m == null) {
            m = new Overworld("An empty map", 40, 24);
            id = "";
        }
        map = m;
        mapId = id;
    }

    /** Stand her where the save has her, or at the map's start. */
    private void place() {
        Overworld.Node n = map.node(save.node());
        if (n == null) n = map.start();
        at = n == null ? "" : n.id;
        if (n != null) {
            tx = n.x + 0.5f;
            ty = n.y + 0.5f;
        }
        walk = null;
        route.clear();
        camSet = false;
        save.setPlace(mapId, at);
    }

    @Override
    public String name() { return "Map — " + map.name(); }

    @Override
    public void enter() {
        Songs.playChoice(map.music(), DemoScene.MUSIC_VOLUME);
    }

    @Override
    public void exit() {
        Music.stop();
    }

    /** What marks a destination of this map cleared in the save. */
    static String key(String mapId, String node) {
        return mapId + "/" + node;
    }

    /** The destinations of this map she has cleared. */
    private Set<String> cleared() {
        Set<String> out = new HashSet<>();
        String prefix = mapId + "/";
        for (String c : save.cleared()) if (c.startsWith(prefix)) out.add(c.substring(prefix.length()));
        return out;
    }

    private float tile() {
        return 16f * Math.max(2, Math.round(game.ui().height() / 360f));
    }

    // --- each frame ---------------------------------------------------------------------

    @Override
    public void update(double dt) {
        time += dt;
        save.played(dt);
        Input in = game.window().input();
        in.setUiMode(menuOpen);
        if (in.pressed(Action.PAUSE)) {
            menuOpen = !menuOpen;
            menu.setFocus(0);
            Sounds.play(SoundKeys.ui(menuOpen ? "open" : "close"));
            return;
        }
        if (menuOpen) return;
        if (walk != null) {
            step(dt);
            return;
        }
        Overworld.Node here = map.node(at);
        if (here == null) return;
        double dx = (in.down(Action.MOVE_RIGHT) || in.down(GLFW_KEY_RIGHT) || in.padDown(Pad.DPAD_RIGHT) ? 1 : 0)
                - (in.down(Action.MOVE_LEFT) || in.down(GLFW_KEY_LEFT) || in.padDown(Pad.DPAD_LEFT) ? 1 : 0);
        double dy = (in.down(Action.MOVE_BACK) || in.down(GLFW_KEY_DOWN) || in.padDown(Pad.DPAD_DOWN) ? 1 : 0)
                - (in.down(Action.MOVE_FORWARD) || in.down(GLFW_KEY_UP) || in.padDown(Pad.DPAD_UP) ? 1 : 0);
        if (dx != 0 || dy != 0) {
            Overworld.Way way = map.toward(at, dx, dy);
            if (way != null) {
                if (map.walkable(way, at, cleared())) {
                    setOff(way);
                } else if (time - saidShut > 1.5) {
                    saidShut = time;
                    Sounds.play(SoundKeys.ui("error"));
                    game.toast("The road is shut until " + here.name + " is cleared", Theme.WARNING);
                }
            }
            return;
        }
        if (in.pressed(Action.USE) || in.pressed(Action.JUMP) || in.pressed(GLFW_KEY_ENTER)
                || in.pressed(GLFW_KEY_KP_ENTER)) {
            enter(here);
        }
    }

    private void setOff(Overworld.Way way) {
        walk = map.points(way, at);
        walkingTo = way.other(at);
        walked = 0;
        walkLength = 0;
        for (int i = 0; i + 1 < walk.size(); i++) {
            walkLength += Math.hypot(walk.get(i + 1)[0] - walk.get(i)[0], walk.get(i + 1)[1] - walk.get(i)[1]);
        }
        Sounds.play(SoundKeys.ui("map_step"));
    }

    /** Walk on along the road; arriving, write her place down and take the next road of a longer walk. */
    private void step(double dt) {
        walked += SPEED * dt;
        double left = walked;
        for (int i = 0; i + 1 < walk.size(); i++) {
            float[] a = walk.get(i), b = walk.get(i + 1);
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (left <= len || i + 2 == walk.size()) {
                double f = len < 1e-6 ? 1 : Math.min(1, left / len);
                tx = (float) (a[0] + (b[0] - a[0]) * f);
                ty = (float) (a[1] + (b[1] - a[1]) * f);
                facing = Facing.of(b[0] - a[0], b[1] - a[1], facing);
                break;
            }
            left -= len;
        }
        if (walked < walkLength) return;
        at = walkingTo;
        walk = null;
        Overworld.Node n = map.node(at);
        if (n != null) {
            tx = n.x + 0.5f;
            ty = n.y + 0.5f;
        }
        save.setPlace(mapId, at);
        if (back == null) Saves.write();
        Sounds.play(SoundKeys.ui("map_arrive"));
        if (!route.isEmpty()) {
            String next = route.poll();
            Overworld.Way way = map.way(at, next);
            if (way != null && map.walkable(way, at, cleared())) setOff(way);
            else route.clear();
        }
    }

    /** Walk to {@code id} by the roads she may take; false when there is no way yet. */
    private boolean walkTo(String id) {
        List<String> r = map.route(at, id, cleared());
        if (r.size() < 2) return false;
        route.clear();
        route.addAll(r.subList(2, r.size()));
        Overworld.Way way = map.way(at, r.get(1));
        if (way == null) return false;
        setOff(way);
        return true;
    }

    /** Go into the destination she stands on: its level, or the map it leads to. */
    private void enter(Overworld.Node n) {
        if (n.travels()) {
            Overworld next = Overworld.byId(game.settings().assets, n.toMap);
            if (next == null) {
                game.toast("The way to " + n.toMap + " is not made yet", Theme.WARNING);
                return;
            }
            save.cleared().add(key(mapId, n.id));
            map = next;
            mapId = n.toMap;
            save.setPlace(mapId, "");
            place();
            if (back == null) Saves.write();
            Songs.playChoice(map.music(), DemoScene.MUSIC_VOLUME);
            Sounds.play(SoundKeys.ui("map_enter"));
            game.toast(map.name(), Theme.ACCENT);
            return;
        }
        if (!n.playable()) {
            game.toast("Nothing to do at " + n.name + " yet", Theme.HINT);
            return;
        }
        Level level;
        try {
            level = LevelIO.load(game.settings().levelsDir().resolve(n.level));
        } catch (java.io.IOException | RuntimeException e) {
            Sounds.play(SoundKeys.ui("error"));
            game.toast("Cannot open " + n.name + ": the level \"" + n.level + "\" is missing", Theme.WARNING);
            return;
        }
        String key = key(mapId, n.id);
        if (n.clear.equals("visit")) save.cleared().add(key);
        if (back == null) Saves.write();
        Sounds.play(SoundKeys.ui("map_enter"));
        Overworld testing = back == null ? null : map;
        String id = mapId;
        Runnable back = this.back;
        game.switchTo(new DemoScene(game, level, new DemoScene.Campaign(save, key, n.name, n.clear, n.quest,
                () -> game.switchTo(new OverworldScene(game, save, testing, id, back)))));
    }

    // --- drawing -----------------------------------------------------------------------

    @Override
    public void render() {
        Ui ui = game.ui();
        ui.begin();
        float s = tile();
        float w = ui.width(), h = ui.height();
        float mw = map.width() * s, mh = map.height() * s;
        float wantX = mw <= w ? mw / 2 : Math.max(w / 2, Math.min(mw - w / 2, tx * s));
        float wantY = mh <= h ? mh / 2 : Math.max(h / 2, Math.min(mh - h / 2, ty * s));
        if (!camSet) {
            camX = wantX;
            camY = wantY;
            camSet = true;
        } else {
            camX += (wantX - camX) * 0.12f;
            camY += (wantY - camY) * 0.12f;
        }
        float ox = Math.round(w / 2 - camX), oy = Math.round(h / 2 - camY);

        ui.rect(0, 0, w, h, Theme.hex(0x0E1A26, 1f));
        MapView.terrain(ui, map, ox, oy, s, time);
        ui.frame(ox - 6, oy - 6, mw + 12, mh + 12, 6);
        Set<String> cleared = cleared();
        // a road is open once she may walk it from an end she can get to
        MapView.ways(ui, map, ox, oy, s, p -> map.reachable(map.node(p.from), cleared)
                && map.walkable(p, p.from, cleared) || map.reachable(map.node(p.to), cleared)
                && map.walkable(p, p.to, cleared));

        Input in = ui.input();
        hovered = null;
        for (Overworld.Node n : map.nodes()) {
            boolean done = cleared.contains(n.id), reach = map.reachable(n, cleared);
            MapView.node(ui, n, ox, oy, s, done && n.playable(), !reach);
            float nx = ox + n.x * s, ny = oy + n.y * s;
            if (!menuOpen && ui.hover(nx - s * 0.25f, ny - s * 0.6f, s * 1.5f, s * 1.6f)) hovered = n.id;
        }
        token(ui, ox, oy, s);
        for (Overworld.Node n : map.nodes()) {
            boolean here = n.id.equals(at) && walk == null;
            if (!here && !n.id.equals(hovered)) continue;
            label(ui, n.name, ox + (n.x + 0.5f) * s, oy + (n.y + 1.15f) * s, here);
        }
        if (!menuOpen && hovered != null && in.mousePressed(GLFW_MOUSE_BUTTON_LEFT) && walk == null) {
            in.consumeMouse(GLFW_MOUSE_BUTTON_LEFT);
            Overworld.Node n = map.node(hovered);
            if (hovered.equals(at)) enter(n);
            else if (!walkTo(hovered)) {
                Sounds.play(SoundKeys.ui("error"));
                game.toast("There is no way to " + n.name + " yet", Theme.WARNING);
            }
        }
        hud(ui, cleared);
        if (menuOpen) menu(ui);
        ui.end();
    }

    private void label(Ui ui, String text, float cx, float y, boolean here) {
        float tw = ui.small.width(text) + 16;
        ui.plate(cx - tw / 2, y, tw, 22, here ? Theme.ACCENT : Theme.BRASS_DARK);
        ui.textCentered(ui.small, text, cx, y + 3, here ? Theme.TITLE : Theme.ITEM);
    }

    /** Her, on the map: her own sprite, walking the road or standing where she is. */
    private void token(Ui ui, float ox, float oy, float s) {
        float fx = ox + tx * s, fy = oy + ty * s + s * 0.2f;
        float bob = (float) (Math.sin(time * 5) * s * 0.04);
        MapView.icon(ui, Overworld.ICON_HERE, fx, fy - s * (float) (2.9 * save.size()) + bob, s * 0.75f, 1f);
        ui.batch().rect(ui.white(), fx - s * 0.32f, fy - s * 0.09f, s * 0.64f, s * 0.18f, 0, 0, 1, 1, 0, 0, 0, 0.3f);
        try {
            AnimState state = walk != null ? AnimState.WALK : AnimState.IDLE;
            SpriteView view = new SpriteView(Elevation.MIDDLE, facing, Elevation.MIDDLE.renderAngle());
            LayerStack.Result r = LayerStack.resolve(game.sprites(), save.wardrobe(), state, time, view, memory);
            LayerStack.drawFlat(ui.batch(), r, fx, fy, (float) (s * 1.3 * save.size()));
        } catch (RuntimeException e) {
            ui.disc(fx, fy - s * 0.6f, s * 0.35f, Theme.ACCENT);
        }
    }

    private void hud(Ui ui, Set<String> cleared) {
        Input in = ui.input();
        // who she is, top left
        String cls = save.heroClass().isEmpty() ? "" : capital(save.heroClass()) + " · level " + save.level();
        float pw = Math.max(260, Math.max(ui.large.width(save.name()), ui.small.width(cls)) + 36);
        ui.panel(16, 16, pw, 78);
        ui.text(ui.large, save.name(), 32, 24, Theme.TITLE);
        ui.text(ui.small, cls + (cls.isEmpty() ? "" : "  ·  ") + save.coins() + " coins", 32, 60, Theme.ACCENT);
        // the map's name, top middle
        float nw = ui.large.width(map.name()) + 56;
        ui.plate(ui.width() / 2f - nw / 2, 16, nw, 44, Theme.BRASS);
        ui.textCentered(ui.large, map.name(), ui.width() / 2f, 22, Theme.TITLE);
        if (back != null) {
            ui.textCentered(ui.small, "Trying the map: nothing is saved", ui.width() / 2f, 66, Theme.WARNING);
        }
        // where she stands, bottom middle
        Overworld.Node n = map.node(walk == null ? at : walkingTo);
        if (n == null) {
            ui.textCentered(ui.body, "This map has no destinations yet", ui.width() / 2f, ui.height() - 80,
                    Theme.WARNING);
            return;
        }
        float bw = Math.min(720, ui.width() - 40), bh = 112;
        float bx = ui.width() / 2f - bw / 2, by = ui.height() - bh - 16;
        ui.panel(bx, by, bw, bh);
        MapView.icon(ui, n.icon(), bx + 52, by + bh / 2, 72, 1f);
        boolean done = cleared.contains(n.id) && n.playable();
        ui.text(ui.large, n.name, bx + 100, by + 10, Theme.TITLE);
        String kind = capital(n.kind) + (done ? "  ·  cleared" : n.travels() ? "  ·  the way to another land" : "");
        ui.text(ui.small, kind, bx + 100, by + 44, done ? Theme.OK : Theme.ACCENT);
        ui.paragraph(ui.small, n.about, bx + 100, by + 64, bw - 330, Theme.HINT);
        if (walk == null) {
            String what = n.travels() ? "Travel on" : n.playable() ? (done ? "Go in again" : "Go in") : null;
            if (what != null) {
                String keys = in.usingPad() ? in.label(Action.JUMP) : in.label(Action.USE) + " / Enter";
                if (ui.button(what + "  (" + keys + ")", bx + bw - 216, by + 30, 200, 44)) enter(n);
            } else {
                ui.text(ui.small, "Nothing to do here yet", bx + bw - 200, by + 44, Theme.ITEM_DISABLED);
            }
        }
        String moves = in.usingPad() ? "Stick: walk the roads" : "WASD / arrows / click: walk the roads";
        String hint = moves + "   ·   " + in.label(Action.PAUSE) + ": menu";
        ui.rect(12, ui.height() - 28, ui.small.width(hint) + 16, 22, Theme.withAlpha(Theme.SOOT, 0.6f));
        ui.text(ui.small, hint, 20, ui.height() - 25, Theme.HINT);
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
    }

    private void menu(Ui ui) {
        ui.scrim();
        float w = 380, h = 300, x = ui.width() / 2f - w / 2, y = ui.height() / 2f - h / 2;
        ui.panel(x, y, w, h);
        ui.textCentered(ui.large, back != null ? "Trying the map" : save.name(), ui.width() / 2f, y + 16, Theme.TITLE);
        ui.textCentered(ui.small, back != null ? "Nothing is saved" : "Played " + save.playedFor()
                + "  ·  saved as you go", ui.width() / 2f, y + 54, Theme.HINT);
        String[] items = back != null ? new String[]{"Back to the map", "Back to the editor"}
                : new String[]{"Back to the map", "Save and quit to the main menu", "Save and quit the game"};
        int pick = menu.show(ui, items, x + 30, y + 92, w - 60, 48, 12);
        if (pick == 0) menuOpen = false;
        if (pick == 1) {
            if (back != null) {
                back.run();
            } else {
                Saves.write();
                Saves.stop();
                game.switchTo("menu");
            }
        }
        if (pick == 2) {
            Saves.write();
            game.quit();
        }
    }

    // --- scripts ------------------------------------------------------------------------

    /** Script commands: {@code go <destination>}, {@code enter}, {@code mapmenu}. */
    @Override
    public boolean command(String command, String argument) {
        switch (command) {
            case "go" -> {
                Overworld.Node n = map.node(argument);
                if (n == null) {
                    for (Overworld.Node m : map.nodes()) if (m.name.equalsIgnoreCase(argument)) n = m;
                }
                if (n == null || !walkTo(n.id)) System.err.println("[script] no way to " + argument);
            }
            case "enter" -> {
                Overworld.Node n = map.node(at);
                if (n != null) enter(n);
            }
            case "mapmenu" -> menuOpen = !menuOpen;
            default -> {
                return false;
            }
        }
        return true;
    }
}
