package com.larsons.game.editor;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Songs;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Autopilot;
import com.larsons.game.core.Game;
import com.larsons.game.core.Scene;
import com.larsons.game.input.Input;
import com.larsons.game.level.LevelIO;
import com.larsons.game.overworld.MapView;
import com.larsons.game.overworld.Overworld;
import com.larsons.game.save.SaveGame;
import com.larsons.game.scene.MainMenuScene;
import com.larsons.game.scene.OverworldScene;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The overworld map editor, from the Dev tab: where the maps a playthrough
 * is walked on are made ({@code assets/overworld/maps/<id>.json}).
 *
 * <ul>
 *   <li><b>Terrain</b> - paint the fourteen terrains with a brush of 1, 3 or
 *       5 tiles, or fill an area.</li>
 *   <li><b>Destinations</b> - click to place one, drag to move it; each has
 *       a name, a kind (its icon), the <b>level it loads</b> (any level saved
 *       in Creative Mode), what clears it (going there, reaching the
 *       level's way out, felling its foes, a quest), whether the roads on
 *       from it are open from the start, another map it leads to, and a line
 *       about it. One is where a new game starts.</li>
 *   <li><b>Roads</b> - click a destination, then the tiles the road bends
 *       at, then the destination it leads to; the same two again takes the
 *       road away.</li>
 *   <li><b>Map</b> - its name, its size, its music, whether a new game starts
 *       on it; a new map, another map to open, and this one's deletion.</li>
 * </ul>
 *
 * Right-drag (or WASD and the arrows) moves the view, the wheel zooms.
 * "Try it" walks the map as a playthrough would, with nothing saved.
 */
public final class OverworldEditor implements Scene, Autopilot.Scriptable {

    private enum Tool {
        TERRAIN("Terrain"), NODES("Destinations"), WAYS("Roads"), MAP("Map");

        final String label;

        Tool(String label) {
            this.label = label;
        }
    }

    private static final float PANEL = 320, BAR = 52;

    private final Game game;
    private final Widgets widgets = new Widgets();
    private Overworld map;
    private String id;
    private boolean dirty;
    private Tool tool = Tool.TERRAIN;
    private float scale = 32, camX, camY;
    private double time;
    // terrain
    private Overworld.Terrain brush = Overworld.TERRAINS.get(0);
    private int brushSize = 1;
    private boolean fill;
    // destinations
    private String selected;
    private boolean draggingNode;
    // roads
    private String from;
    private final List<int[]> bends = new ArrayList<>();
    // map
    private double sureDelete = -10;
    private List<LevelIO.Saved> levels = List.of();
    private List<Overworld.Saved> maps = List.of();

    /** Edit the map called {@code id}; null: the campaign's first, or a new one when there is none. */
    public OverworldEditor(Game game, String id) {
        this.game = game;
        MapView.useAssets(game.settings().assets);
        if (id == null) id = Overworld.campaignStart(game.settings().assets);
        Overworld m = Overworld.byId(game.settings().assets, id);
        if (m == null) {
            m = new Overworld("New map", 40, 24);
            id = Overworld.freeId(game.settings().assets, m.name());
            dirty = true;
        }
        this.map = m;
        this.id = id;
        centre();
    }

    private void centre() {
        camX = map.width() / 2f;
        camY = map.height() / 2f;
    }

    @Override
    public String name() { return "Map editor — " + map.name(); }

    @Override
    public void enter() {
        refresh();
    }

    private void refresh() {
        levels = LevelIO.list(game.settings().levelsDir());
        maps = Overworld.list(game.settings().assets);
    }

    // --- the view ----------------------------------------------------------------------

    private float ox(Ui ui) {
        return PANEL + (ui.width() - PANEL) / 2f - camX * scale;
    }

    private float oy(Ui ui) {
        return BAR + (ui.height() - BAR) / 2f - camY * scale;
    }

    private boolean overCanvas(Ui ui) {
        Input in = ui.input();
        return in.mouseX() > PANEL && in.mouseY() > BAR && !widgets.overDrop(in.mouseX(), in.mouseY());
    }

    @Override
    public void update(double dt) {
        time += dt;
        Input in = game.window().input();
        in.setUiMode(true);
        if (widgets.typing()) return;
        double pan = 14 * dt * 32 / scale;
        if (in.down(GLFW_KEY_A) || in.down(GLFW_KEY_LEFT)) camX -= pan;
        if (in.down(GLFW_KEY_D) || in.down(GLFW_KEY_RIGHT)) camX += pan;
        if (in.down(GLFW_KEY_W) || in.down(GLFW_KEY_UP)) camY -= pan;
        if (in.down(GLFW_KEY_S) && !in.down(GLFW_KEY_LEFT_CONTROL) || in.down(GLFW_KEY_DOWN)) camY += pan;
        if (in.down(GLFW_KEY_LEFT_CONTROL) && in.pressed(GLFW_KEY_S)) save();
        if (in.pressed(GLFW_KEY_F5)) tryIt();
        if (in.pressed(GLFW_KEY_ESCAPE)) {
            if (from != null) {
                from = null;
                bends.clear();
            } else if (selected != null) {
                selected = null;
            }
        }
        for (int i = 0; i < Tool.values().length; i++) {
            if (in.pressed(GLFW_KEY_1 + i)) tool = Tool.values()[i];
        }
        camX = Math.max(0, Math.min(map.width(), camX));
        camY = Math.max(0, Math.min(map.height(), camY));
    }

    @Override
    public void render() {
        Ui ui = game.ui();
        ui.begin();
        widgets.beginFrame(ui);
        Input in = ui.input();
        ui.rect(0, 0, ui.width(), ui.height(), Theme.hex(0x0E1A26, 1f));

        // the wheel zooms about the pointer; right-drag pans
        if (overCanvas(ui) && in.scroll() != 0) {
            float before = scale;
            scale = Math.max(12, Math.min(72, scale * (in.scroll() > 0 ? 1.15f : 1 / 1.15f)));
            float mx = (float) in.mouseX() - (PANEL + (ui.width() - PANEL) / 2f);
            float my = (float) in.mouseY() - (BAR + (ui.height() - BAR) / 2f);
            camX += mx / before - mx / scale;
            camY += my / before - my / scale;
            in.consumeScroll();
        }
        if (in.mouseDown(GLFW_MOUSE_BUTTON_RIGHT) || in.mouseDown(GLFW_MOUSE_BUTTON_MIDDLE)) {
            camX -= (float) in.mouseDX() / scale;
            camY -= (float) in.mouseDY() / scale;
        }
        float ox = Math.round(ox(ui)), oy = Math.round(oy(ui)), s = scale;
        ui.clip(PANEL, BAR, ui.width() - PANEL, ui.height() - BAR);
        MapView.terrain(ui, map, ox, oy, s, time);
        ui.outline(ox - 2, oy - 2, map.width() * s + 4, map.height() * s + 4, 2, Theme.BRASS);
        MapView.ways(ui, map, ox, oy, s, null);
        Overworld.Node start = map.start();
        for (Overworld.Node n : map.nodes()) {
            MapView.node(ui, n, ox, oy, s, false, false);
            float cx = ox + (n.x + 0.5f) * s, cy = oy + (n.y + 0.5f) * s;
            boolean sel = n.id.equals(selected) || n.id.equals(from);
            if (sel) ui.outline(ox + n.x * s - 2, oy + n.y * s - 2, s + 4, s + 4, 2, Theme.ACCENT);
            if (n == start) MapView.icon(ui, Overworld.ICON_HERE, cx, cy - s * 1.25f, s * 0.7f, 1f);
            if (s >= 24 || sel) {
                String label = n.name + (n.playable() ? "" : n.travels() ? "  →" : "  (no level)");
                float tw = ui.tiny.width(label) + 8;
                ui.rect(cx - tw / 2, cy + s * 0.62f, tw, 15, Theme.withAlpha(Theme.SOOT, 0.72f));
                ui.textCentered(ui.tiny, label, cx, cy + s * 0.62f + 1, n.playable() || n.travels() ? Theme.TITLE
                        : Theme.WARNING);
            }
        }
        int tx = (int) Math.floor((in.mouseX() - ox) / s), ty = (int) Math.floor((in.mouseY() - oy) / s);
        boolean over = overCanvas(ui) && map.inside(tx, ty);
        if (over) canvas(ui, in, tx, ty, ox, oy, s);
        if (!in.mouseDown(GLFW_MOUSE_BUTTON_LEFT)) draggingNode = false;
        // the road being drawn
        if (tool == Tool.WAYS && from != null && map.node(from) != null) {
            List<float[]> pts = new ArrayList<>();
            Overworld.Node a = map.node(from);
            pts.add(new float[]{a.x + 0.5f, a.y + 0.5f});
            for (int[] b : bends) pts.add(new float[]{b[0] + 0.5f, b[1] + 0.5f});
            if (over) pts.add(new float[]{tx + 0.5f, ty + 0.5f});
            MapView.way(ui, pts, ox, oy, s, false);
        }
        ui.unclip();

        bar(ui);
        panel(ui, over ? tx : -1, ty);
        widgets.overlay(ui);
        ui.end();
    }

    /** What the pointer does on the map, by the tool. */
    private void canvas(Ui ui, Input in, int tx, int ty, float ox, float oy, float s) {
        boolean down = in.mouseDown(GLFW_MOUSE_BUTTON_LEFT), pressed = in.mousePressed(GLFW_MOUSE_BUTTON_LEFT);
        Overworld.Node at = map.nodeAt(tx, ty);
        switch (tool) {
            case TERRAIN -> {
                int r = brushSize / 2;
                ui.outline(ox + (tx - r) * s, oy + (ty - r) * s, brushSize * s, brushSize * s, 2,
                        Theme.withAlpha(Theme.TITLE, 0.8f));
                if (fill ? pressed : down) {
                    if (fill) {
                        map.flood(tx, ty, brush);
                    } else {
                        for (int y = ty - r; y <= ty + r; y++) {
                            for (int x = tx - r; x <= tx + r; x++) map.paint(x, y, brush);
                        }
                    }
                    dirty = true;
                }
                if (in.pressed(GLFW_KEY_Q)) brush = map.terrain(tx, ty);      // pick up what is under the pointer
            }
            case NODES -> {
                ui.outline(ox + tx * s, oy + ty * s, s, s, 1, Theme.withAlpha(Theme.TITLE, 0.7f));
                if (pressed) {
                    if (at != null) {
                        selected = at.id;
                        draggingNode = true;
                    } else {
                        Overworld.Node n = map.add("Destination " + (map.nodes().size() + 1), tx, ty);
                        selected = n.id;
                        dirty = true;
                        Sounds.play(SoundKeys.ui("place"));
                    }
                } else if (down && draggingNode && selected != null && at == null) {
                    Overworld.Node n = map.node(selected);
                    if (n != null && (n.x != tx || n.y != ty)) {
                        n.x = tx;
                        n.y = ty;
                        dirty = true;
                    }
                }
            }
            case WAYS -> {
                ui.outline(ox + tx * s, oy + ty * s, s, s, 1, Theme.withAlpha(Theme.TITLE, 0.7f));
                if (!pressed) return;
                if (at == null) {
                    if (from != null) bends.add(new int[]{tx, ty});
                } else if (from == null) {
                    from = at.id;
                    bends.clear();
                } else if (!at.id.equals(from)) {
                    if (map.way(from, at.id) != null && !bends.isEmpty()) map.link(from, at.id, List.of());
                    boolean made = map.link(from, at.id, new ArrayList<>(bends)) != null;
                    Sounds.play(SoundKeys.ui(made ? "place" : "remove"));
                    from = null;
                    bends.clear();
                    dirty = true;
                } else {
                    from = null;
                    bends.clear();
                }
            }
            default -> { }
        }
    }

    // --- the bar along the top ---------------------------------------------------------

    private void bar(Ui ui) {
        ui.wood(0, 0, ui.width(), BAR, Theme.PANEL);
        ui.rect(0, BAR - 2, ui.width(), 2, Theme.BRASS_DARK);
        float x = 12;
        for (Tool t : Tool.values()) {
            float w = ui.body.width(t.label) + 28;
            if (ui.button(t.label, x, 8, w, 36, t == tool, true)) {
                tool = t;
                widgets.stopTyping();
            }
            x += w + 6;
        }
        ui.text(ui.body, map.name() + (dirty ? " *" : ""), x + 16, 15, dirty ? Theme.ACCENT : Theme.TITLE);
        float bx = ui.width() - 12;
        bx -= 116;
        if (ui.button("Back", bx, 8, 110, 36)) leave();
        bx -= 116;
        if (ui.button("Try it  F5", bx, 8, 110, 36)) tryIt();
        bx -= 116;
        if (ui.button("Save", bx, 8, 110, 36, dirty, true)) save();
        bx -= 116;
        if (ui.button("Settings", bx, 8, 110, 36)) game.openSettings();
    }

    private void leave() {
        if (dirty) save();
        MainMenuScene.openOnDevTab();
        game.switchTo("menu");
    }

    private boolean save() {
        try {
            map.save(Overworld.folder(game.settings().assets).resolve(id + ".json"));
            dirty = false;
            refresh();
            game.toast("Saved the map \"" + map.name() + "\"", Theme.OK);
            return true;
        } catch (IOException e) {
            game.toast("Cannot save the map: " + e.getMessage(), Theme.WARNING);
            return false;
        }
    }

    /** Walk the map as a playthrough would, nothing saved; back here from its menu. */
    private void tryIt() {
        if (map.nodes().isEmpty()) {
            game.toast("Place a destination first (the Destinations tool)", Theme.WARNING);
            return;
        }
        if (dirty) save();
        SaveGame trial = new SaveGame();
        trial.setName("Map test");
        trial.setWardrobe(game.wardrobe().copy());
        String cls = game.settings().heroClass;
        trial.setHeroClass(cls == null || cls.isBlank() ? "knight" : cls);
        OverworldEditor me = this;
        game.switchTo(new OverworldScene(game, trial, map, id, () -> game.switchTo(me)));
    }

    // --- the panel down the left -------------------------------------------------------

    private void panel(Ui ui, int tx, int ty) {
        float h = ui.height() - BAR;
        ui.wood(0, BAR, PANEL, h, Theme.PANEL);
        ui.rect(PANEL - 2, BAR, 2, h, Theme.BRASS_DARK);
        float x = 14, y = BAR + 12, w = PANEL - 28;
        ui.text(ui.large, tool.label, x, y, Theme.TITLE);
        y += 40;
        switch (tool) {
            case TERRAIN -> terrainPanel(ui, x, y, w);
            case NODES -> nodePanel(ui, x, y, w);
            case WAYS -> waysPanel(ui, x, y, w);
            case MAP -> mapPanel(ui, x, y, w);
        }
        String where = tx < 0 ? "" : "tile " + tx + ", " + ty + " · " + map.terrain(tx, ty).name() + " · ";
        ui.text(ui.tiny, where + "right-drag pans · wheel zooms · Ctrl+S saves", x, ui.height() - 20, Theme.ITEM_DISABLED);
    }

    private void terrainPanel(Ui ui, float x, float y, float w) {
        float cell = (w - 8) / 2, th = 34;
        for (int i = 0; i < Overworld.TERRAINS.size(); i++) {
            Overworld.Terrain t = Overworld.TERRAINS.get(i);
            float tx = x + (i % 2) * (cell + 8), ty = y + (i / 2) * (th + 6);
            boolean on = t == brush;
            ui.rect(tx, ty, cell, th, on ? Theme.BUTTON_HOVER : Theme.BUTTON);
            ui.outline(tx, ty, cell, th, on ? 2 : 1, on ? Theme.ACCENT : Theme.PANEL_EDGE);
            MapView.tile(ui, t, 0, tx + 4, ty + 4, th - 8);
            ui.text(ui.small, Ui.fit(ui.small, t.name(), cell - th - 6), tx + th + 2, ty + 8, on ? Theme.TITLE : Theme.ITEM);
            if (ui.clicked(tx, ty, cell, th)) brush = t;
        }
        y += ((Overworld.TERRAINS.size() + 1) / 2) * (th + 6) + 12;
        ui.text(ui.small, "Brush", x, y + 8, Theme.ITEM);
        int[] sizes = {1, 3, 5};
        for (int i = 0; i < sizes.length; i++) {
            if (ui.button(sizes[i] + "", x + 70 + i * 50, y, 44, 32, brushSize == sizes[i] && !fill, true)) {
                brushSize = sizes[i];
                fill = false;
            }
        }
        if (ui.button("Fill", x + 70 + 3 * 50, y, 60, 32, fill, true)) fill = !fill;
        y += 46;
        ui.paragraph(ui.small, "Drag to paint. Q picks up the terrain under the pointer. Fill floods every joined tile "
                + "of the same terrain.", x, y, w, Theme.HINT);
    }

    private void nodePanel(Ui ui, float x, float y, float w) {
        Overworld.Node n = map.node(selected);
        if (n == null) {
            ui.paragraph(ui.small, "Click an empty tile to place a destination; click one to choose it, drag it to move "
                    + "it.\n\nEach destination loads a level saved in Creative Mode when she goes in, at the level's "
                    + "spawn.", x, y, w, Theme.HINT);
            return;
        }
        String was = n.name + n.kind + n.level + n.clear + n.quest + n.open + n.toMap + n.about;
        n.name = widgets.text(ui, "node.name." + n.id, "Name", n.name, x, y, w);
        y += 36;
        int k = widgets.choice(ui, "Kind", Overworld.KINDS, Math.max(0, Overworld.KINDS.indexOf(n.kind)), x, y, w);
        n.kind = Overworld.KINDS.get(k);
        y += 36;
        List<String> shown = new ArrayList<>(List.of("(none)"));
        List<String> ids = new ArrayList<>(List.of(""));
        for (LevelIO.Saved l : levels) {
            shown.add(l.name());
            ids.add(l.id());
        }
        if (!n.level.isBlank() && !ids.contains(n.level)) {
            shown.add(n.level + " (missing)");
            ids.add(n.level);
        }
        int li = widgets.choice(ui, "Level it loads", shown, Math.max(0, ids.indexOf(n.level)), x, y, w);
        n.level = ids.get(li);
        y += 36;
        List<String> clears = List.of("Going there", "Reaching its way out", "Felling its foes", "A quest");
        int ci = widgets.choice(ui, "Cleared by", clears, Math.max(0, Overworld.CLEARS.indexOf(n.clear)), x, y, w);
        n.clear = Overworld.CLEARS.get(ci);
        y += 36;
        if (n.clear.equals("quest")) {
            n.quest = widgets.text(ui, "node.quest." + n.id, "Quest's id", n.quest, x, y, w);
            y += 36;
        }
        n.open = widgets.toggle(ui, "Roads on are open from the start", n.open, x, y, w);
        y += 36;
        List<String> mapShown = new ArrayList<>(List.of("(nowhere)"));
        List<String> mapIds = new ArrayList<>(List.of(""));
        for (Overworld.Saved m : maps) {
            if (m.id().equals(id)) continue;
            mapShown.add(m.name());
            mapIds.add(m.id());
        }
        int mi = widgets.choice(ui, "Leads to the map", mapShown, Math.max(0, mapIds.indexOf(n.toMap)), x, y, w);
        n.toMap = mapIds.get(mi);
        y += 36;
        n.about = widgets.text(ui, "node.about." + n.id, "About it", n.about, x, y, w);
        y += 44;
        boolean isStart = map.start() == n;
        if (ui.button(isStart ? "A new game starts here" : "Start a new game here", x, y, w, 34, isStart, !isStart)) {
            map.setStart(n.id);
            dirty = true;
        }
        y += 42;
        if (ui.button("Delete this destination", x, y, w, 34) || ui.input().pressed(GLFW_KEY_DELETE)
                && !widgets.typing()) {
            map.remove(n);
            selected = null;
            dirty = true;
            Sounds.play(SoundKeys.ui("remove"));
            return;
        }
        y += 46;
        int roads = map.waysAt(n.id).size();
        ui.paragraph(ui.small, roads == 0 ? "No road leads here yet (the Roads tool)."
                : roads + (roads == 1 ? " road" : " roads") + " at " + n.x + ", " + n.y + ".", x, y, w,
                roads == 0 ? Theme.WARNING : Theme.HINT);
        if (!was.equals(n.name + n.kind + n.level + n.clear + n.quest + n.open + n.toMap + n.about)) dirty = true;
    }

    private void waysPanel(Ui ui, float x, float y, float w) {
        Overworld.Node a = map.node(from);
        String now = a == null ? "Click the destination the road starts at."
                : "From " + a.name + ": click the tiles it bends at (" + bends.size() + " so far), then the destination "
                + "it leads to. Esc lets go.";
        y += ui.paragraph(ui.small, now, x, y, w, a == null ? Theme.HINT : Theme.ACCENT) + 14;
        y += ui.paragraph(ui.small, "The same two destinations again takes their road away.\n\nShe walks a road from "
                + "one end to the other; a road on from a destination is shut until it is cleared, unless its roads "
                + "are open from the start.", x, y, w, Theme.HINT) + 14;
        ui.text(ui.small, map.ways().size() + " roads, " + map.nodes().size() + " destinations", x, y, Theme.ITEM);
        y += 26;
        Set<String> none = Set.of();
        int unreachable = 0;
        for (Overworld.Node n : map.nodes()) if (!allReachable(n)) unreachable++;
        if (unreachable > 0) {
            ui.paragraph(ui.small, unreachable + " cannot be reached from the start by any road.", x, y, w,
                    Theme.WARNING);
        }
    }

    /** Whether {@code n} could be reached once everything is cleared. */
    private boolean allReachable(Overworld.Node n) {
        Set<String> all = new java.util.HashSet<>();
        for (Overworld.Node m : map.nodes()) all.add(m.id);
        return map.reachable(n, all);
    }

    private void mapPanel(Ui ui, float x, float y, float w) {
        String name = widgets.text(ui, "map.name", "Name", map.name(), x, y, w);
        if (!name.equals(map.name())) {
            map.setName(name);
            dirty = true;
        }
        y += 36;
        double mw = widgets.stepper(ui, "Width (tiles)", map.width(), 4, Overworld.SMALLEST, Overworld.WIDEST, "%.0f",
                x, y, w);
        y += 36;
        double mh = widgets.stepper(ui, "Height (tiles)", map.height(), 4, Overworld.SMALLEST, Overworld.TALLEST,
                "%.0f", x, y, w);
        y += 36;
        if ((int) mw != map.width() || (int) mh != map.height()) {
            map.resize((int) mw, (int) mh);
            dirty = true;
        }
        List<String> shown = new ArrayList<>(List.of("None", "Shuffle the songs"));
        for (Songs.Song song : Songs.ALL) shown.add(song.title());
        int cur = map.music().equals(Songs.NONE) ? 0 : map.music().equals(Songs.SHUFFLE) ? 1
                : Math.max(1, 2 + Songs.ALL.indexOf(Songs.byId(map.music())));
        int pick = widgets.choice(ui, "Music", shown, cur, x, y, w);
        if (pick != cur) {
            map.setMusic(pick == 0 ? Songs.NONE : pick == 1 ? Songs.SHUFFLE : Songs.ALL.get(pick - 2).id());
            dirty = true;
        }
        y += 36;
        boolean first = id.equals(Overworld.campaignStart(game.settings().assets));
        boolean want = widgets.toggle(ui, "A new game starts on this map", first, x, y, w);
        if (want && !first) {
            try {
                if (dirty) save();
                Overworld.setCampaignStart(game.settings().assets, id);
                game.toast("A new game now starts on \"" + map.name() + "\"", Theme.OK);
            } catch (IOException e) {
                game.toast("Cannot write campaign.json: " + e.getMessage(), Theme.WARNING);
            }
        }
        y += 44;
        ui.text(ui.body, "Maps", x, y, Theme.ACCENT);
        y += 30;
        for (Overworld.Saved m : maps) {
            boolean mine = m.id().equals(id);
            if (ui.button(Ui.fit(ui.small, m.name(), w - 20), x, y, w, 30, mine, !mine)) open(m.id());
            y += 34;
        }
        if (ui.button("+ A new map", x, y, w, 32)) {
            if (dirty) save();
            map = new Overworld("New map", 40, 24);
            id = Overworld.freeId(game.settings().assets, map.name());
            dirty = true;
            selected = from = null;
            centre();
        }
        y += 44;
        boolean sure = time - sureDelete < 3;
        if (ui.button(sure ? "Sure? Click again to delete it" : "Delete this map", x, y, w, 32, sure, true)) {
            if (sure) {
                try {
                    Files.deleteIfExists(Overworld.folder(game.settings().assets).resolve(id + ".json"));
                } catch (IOException e) {
                    game.toast("Cannot delete: " + e.getMessage(), Theme.WARNING);
                }
                refresh();
                dirty = false;
                open(maps.isEmpty() ? null : maps.get(0).id());
                sureDelete = -10;
            } else {
                sureDelete = time;
            }
        }
    }

    private void open(String other) {
        if (dirty) save();
        Overworld m = other == null ? null : Overworld.byId(game.settings().assets, other);
        if (m == null) {
            m = new Overworld("New map", 40, 24);
            other = Overworld.freeId(game.settings().assets, m.name());
            dirty = true;
        }
        map = m;
        id = other;
        selected = from = null;
        bends.clear();
        centre();
    }

    /** Script commands: {@code maptool terrain|destinations|roads|map}, {@code select <destination>}, {@code zoom n}. */
    @Override
    public boolean command(String command, String argument) {
        switch (command) {
            case "maptool" -> {
                for (Tool t : Tool.values()) {
                    if (t.label.equalsIgnoreCase(argument.trim()) || t.name().equalsIgnoreCase(argument.trim())) tool = t;
                }
            }
            case "select" -> {
                selected = null;
                for (Overworld.Node n : map.nodes()) {
                    if (n.id.equals(argument.trim()) || n.name.equalsIgnoreCase(argument.trim())) {
                        selected = n.id;
                        camX = n.x + 0.5f;
                        camY = n.y + 0.5f;
                    }
                }
            }
            case "zoom" -> scale = Math.max(12, Math.min(72, Float.parseFloat(argument.trim())));
            case "tryit" -> tryIt();
            default -> {
                return false;
            }
        }
        return true;
    }
}
