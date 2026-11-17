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
import com.larsons.game.ui.FilePicker;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The overworld map editor, from the Dev tab: where the maps a playthrough
 * is walked on are made ({@code assets/overworld/maps/<id>.json}).
 *
 * <ul>
 *   <li><b>Terrain</b> - paint the fourteen terrains with a brush of 1, 3, 5
 *       or 9 tiles, draw a box of one, or fill an area; Shift+click paints a
 *       straight line from the last tile painted.</li>
 *   <li><b>Destinations</b> - click to place one, drag (or the arrow keys)
 *       to move it; each has a name, a <b>look</b> (a painted icon, or a
 *       plain mark as on a traveller's map: filled once she can get there,
 *       hollow till then), the <b>level it loads</b>, what clears it, whether
 *       the roads on from it are open from the start, another map it leads
 *       to, and a line about it. Ctrl+C / Ctrl+V / Ctrl+D copy, paste and
 *       duplicate one; Delete takes it away.</li>
 *   <li><b>Roads</b> - click a destination, then the tiles the road bends
 *       at, then the destination it leads to; the same two again takes the
 *       road away. Roads can be dotted, ink, dashed or a track, and bend
 *       smoothly.</li>
 *   <li><b>Map</b> - its name, size and music; a <b>picture of one's own</b>
 *       (a PNG chosen, or dropped on the window) for it to lie over, with or
 *       without its tiles; moving everything on it; another map to open, a
 *       copy of this one, a new one, and this one's deletion.</li>
 * </ul>
 *
 * Ctrl+Z undoes and Ctrl+Y (or Ctrl+Shift+Z) redoes, as in the level
 * editor; Ctrl+S saves, F5 tries the map, G shows the grid, 1-4 pick the
 * tools. Right-drag (or WASD) moves the view, the wheel zooms.
 *
 * <p>The <b>main story's</b> map is shown but never changed: it can be
 * looked at and tried, and copied to make one's own from.
 */
public final class OverworldEditor implements Scene, Autopilot.Scriptable {

    private enum Tool {
        TERRAIN("Terrain"), NODES("Destinations"), WAYS("Roads"), MAP("Map");

        final String label;

        Tool(String label) {
            this.label = label;
        }
    }

    private static final float PANEL = 330, BAR = 52;
    /** How many steps back undo goes. */
    static final int UNDO_DEPTH = 100;

    private final Game game;
    private final Widgets widgets = new Widgets();
    private Overworld map;
    private String id;
    private boolean dirty;
    private Tool tool = Tool.TERRAIN;
    private float scale = 32, camX, camY;
    private double time;
    private boolean grid;
    private float panelScroll;
    // terrain
    private Overworld.Terrain brush = Overworld.TERRAINS.get(0);
    private int brushSize = 1;
    private boolean fill, box;
    private int[] boxFrom, lastPainted;
    // destinations
    private String selected;
    private boolean draggingNode;
    private Overworld.Node clipboard;
    // roads
    private String from;
    private final List<int[]> bends = new ArrayList<>();
    // map
    private double sureDelete = -10, surePromote = -10;
    private List<LevelIO.Saved> levels = List.of();
    private List<Overworld.Saved> maps = List.of();
    // undo
    private record Step(String label, Overworld map) {}

    private final Deque<Step> undos = new ArrayDeque<>(), redos = new ArrayDeque<>();
    /** The map as it was after the last change: what the next change's undo goes back to. */
    private Overworld shadow;
    private String lastLabel;
    private double lastAt;

    /** Edit the map called {@code id}; null: the campaign's first, or a new one when there is none. */
    public OverworldEditor(Game game, String id) {
        this.game = game;
        MapView.useAssets(game.settings().assets);
        if (id == null) id = firstEditable();
        Overworld m = Overworld.byId(game.settings().assets, id);
        if (m == null) {
            m = new Overworld("New map", 40, 24);
            id = Overworld.freeId(game.settings().assets, m.name());
            dirty = true;
        }
        this.map = m;
        this.id = id;
        resetUndo();
        centre();
    }

    /** The map the editor opens on: the first one made in it, else the main story's (to look at). */
    private String firstEditable() {
        for (Overworld.Saved s : Overworld.list(game.settings().assets)) if (!s.story()) return s.id();
        return Overworld.campaignStart(game.settings().assets);
    }

    private void centre() {
        camX = map.width() / 2f;
        camY = map.height() / 2f;
        scale = Math.max(8, Math.min(32, 1100f / Math.max(map.width(), map.height() * 1.6f)));
    }

    /** Whether this map can be changed: any but the main story's. */
    private boolean editable() {
        return !map.story();
    }

    @Override
    public String name() { return "Map editor — " + map.name(); }

    @Override
    public void enter() {
        refresh();
        game.setDropTarget(this::dropped);
    }

    @Override
    public void exit() {
        game.setDropTarget(null);
    }

    private void refresh() {
        levels = LevelIO.list(game.settings().levelsDir());
        maps = Overworld.list(game.settings().assets);
    }

    // --- undo ----------------------------------------------------------------------------

    private void resetUndo() {
        undos.clear();
        redos.clear();
        shadow = map.copy();
        lastLabel = null;
    }

    /**
     * Something changed: the map as it was goes on the undo list - one step
     * for a run of the same change (a stroke of the brush, a name typed).
     */
    private void changed(String label) {
        dirty = true;
        boolean merge = label.equals(lastLabel) && time - lastAt < 1.2;
        if (!merge) {
            undos.push(new Step(label, shadow));
            while (undos.size() > UNDO_DEPTH) undos.removeLast();
        }
        redos.clear();
        shadow = map.copy();
        lastLabel = label;
        lastAt = time;
    }

    /** The next change is a step of its own, whatever it is (a new stroke). */
    private void breakRun() {
        lastLabel = null;
    }

    private void undo() {
        if (undos.isEmpty()) return;
        Step s = undos.pop();
        redos.push(new Step(s.label(), map.copy()));
        restore(s.map());
        game.toast("Undid: " + s.label(), Theme.HINT);
        Sounds.play(SoundKeys.ui("undo"));
    }

    private void redo() {
        if (redos.isEmpty()) return;
        Step s = redos.pop();
        undos.push(new Step(s.label(), map.copy()));
        restore(s.map());
        game.toast("Redid: " + s.label(), Theme.HINT);
        Sounds.play(SoundKeys.ui("undo"));
    }

    private void restore(Overworld to) {
        map = to.copy();
        shadow = map.copy();
        dirty = true;
        lastLabel = null;
        if (map.node(selected) == null) selected = null;
        if (map.node(from) == null) {
            from = null;
            bends.clear();
        }
        widgets.stopTyping();
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
        boolean ctrl = in.down(GLFW_KEY_LEFT_CONTROL) || in.down(GLFW_KEY_RIGHT_CONTROL);
        boolean shift = in.down(GLFW_KEY_LEFT_SHIFT) || in.down(GLFW_KEY_RIGHT_SHIFT);
        if (ctrl && (in.pressed(GLFW_KEY_Y) || shift && in.pressed(GLFW_KEY_Z))) {
            redo();
            return;
        }
        if (ctrl && in.pressed(GLFW_KEY_Z)) {
            undo();
            return;
        }
        if (ctrl && in.pressed(GLFW_KEY_S)) save();
        Overworld.Node sel = map.node(selected);
        if (ctrl && sel != null && tool == Tool.NODES) {
            if (in.pressed(GLFW_KEY_C)) {
                clipboard = sel.copy(sel.id, sel.x, sel.y);
                game.toast("Copied " + sel.name, Theme.HINT);
            }
            if (in.pressed(GLFW_KEY_D)) duplicate(sel);
        }
        if (ctrl && in.pressed(GLFW_KEY_V) && clipboard != null) paste();
        if (!ctrl) {
            double pan = 14 * dt * 32 / scale;
            // the arrows move the destination chosen a tile at a time; with none, they move the view
            boolean nudge = tool == Tool.NODES && sel != null && editable();
            if (in.down(GLFW_KEY_A) || !nudge && in.down(GLFW_KEY_LEFT)) camX -= pan;
            if (in.down(GLFW_KEY_D) || !nudge && in.down(GLFW_KEY_RIGHT)) camX += pan;
            if (in.down(GLFW_KEY_W) || !nudge && in.down(GLFW_KEY_UP)) camY -= pan;
            if (in.down(GLFW_KEY_S) || !nudge && in.down(GLFW_KEY_DOWN)) camY += pan;
            if (nudge) {
                int dx = (in.repeated(GLFW_KEY_RIGHT) ? 1 : 0) - (in.repeated(GLFW_KEY_LEFT) ? 1 : 0);
                int dy = (in.repeated(GLFW_KEY_DOWN) ? 1 : 0) - (in.repeated(GLFW_KEY_UP) ? 1 : 0);
                if ((dx != 0 || dy != 0) && map.inside(sel.x + dx, sel.y + dy) && map.nodeAt(sel.x + dx, sel.y + dy) == null) {
                    sel.x += dx;
                    sel.y += dy;
                    changed("Move " + sel.name);
                }
            }
            if (in.pressed(GLFW_KEY_G)) grid = !grid;
            if (in.pressed(GLFW_KEY_F5)) tryIt();
            if (in.pressed(GLFW_KEY_DELETE) && sel != null && editable()) deleteNode(sel);
            for (int i = 0; i < Tool.values().length; i++) {
                if (in.pressed(GLFW_KEY_1 + i)) setTool(Tool.values()[i]);
            }
        }
        if (in.pressed(GLFW_KEY_ESCAPE)) {
            if (from != null) {
                from = null;
                bends.clear();
            } else if (boxFrom != null) {
                boxFrom = null;
            } else if (selected != null) {
                selected = null;
            }
        }
        camX = Math.max(0, Math.min(map.width(), camX));
        camY = Math.max(0, Math.min(map.height(), camY));
    }

    private void setTool(Tool t) {
        tool = t;
        panelScroll = 0;
        boxFrom = null;
        widgets.stopTyping();
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
            scale = Math.max(4, Math.min(72, scale * (in.scroll() > 0 ? 1.15f : 1 / 1.15f)));
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
        MapView.ground(ui, map, ox, oy, s, time);
        if (grid) drawGrid(ui, ox, oy, s);
        ui.outline(ox - 2, oy - 2, map.width() * s + 4, map.height() * s + 4, 2, Theme.BRASS);
        MapView.ways(ui, map, ox, oy, s, null);
        Overworld.Node start = map.start();
        for (Overworld.Node n : map.nodes()) {
            MapView.node(ui, n, ox, oy, s, false, false);
            float cx = ox + (n.x + 0.5f) * s, cy = oy + (n.y + 0.5f) * s;
            boolean sel = n.id.equals(selected) || n.id.equals(from);
            if (sel) ui.outline(ox + n.x * s - 2, oy + n.y * s - 2, s + 4, s + 4, 2, Theme.ACCENT);
            if (n == start) MapView.icon(ui, Overworld.ICON_HERE, cx, cy - s * 1.25f, Math.max(16, s * 0.7f), 1f);
            if (s >= 20 || sel) {
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
            MapView.way(ui, map, pts, ox, oy, s, false);
        }
        if (!editable()) storyBanner(ui);
        ui.unclip();

        bar(ui);
        panel(ui, over ? tx : -1, ty);
        widgets.overlay(ui);
        ui.end();
    }

    private void drawGrid(Ui ui, float ox, float oy, float s) {
        float[] c = Theme.withAlpha(Theme.TITLE, s >= 16 ? 0.16f : 0.08f);
        for (int x = 0; x <= map.width(); x++) ui.rect(ox + x * s, oy, 1, map.height() * s, c);
        for (int y = 0; y <= map.height(); y++) ui.rect(ox, oy + y * s, map.width() * s, 1, c);
    }

    /** Over the main story's map: it is the game's own, to be looked at and copied. */
    private void storyBanner(Ui ui) {
        float w = Math.min(640, ui.width() - PANEL - 40), x = PANEL + (ui.width() - PANEL - w) / 2, y = BAR + 12;
        ui.plate(x, y, w, 64, Theme.ACCENT);
        ui.text(ui.body, "The main story's map", x + 14, y + 8, Theme.TITLE);
        ui.text(ui.small, Ui.fit(ui.small, "It cannot be changed or deleted here. Copy it to make a map of your own.",
                w - 200), x + 14, y + 36, Theme.HINT);
        if (ui.button("Make a copy", x + w - 168, y + 14, 154, 36, true, true)) copyMap();
    }

    /** What the pointer does on the map, by the tool. */
    private void canvas(Ui ui, Input in, int tx, int ty, float ox, float oy, float s) {
        boolean down = in.mouseDown(GLFW_MOUSE_BUTTON_LEFT), pressed = in.mousePressed(GLFW_MOUSE_BUTTON_LEFT);
        boolean shift = in.down(GLFW_KEY_LEFT_SHIFT) || in.down(GLFW_KEY_RIGHT_SHIFT);
        boolean alt = in.down(GLFW_KEY_LEFT_ALT) || in.down(GLFW_KEY_RIGHT_ALT);
        Overworld.Node at = map.nodeAt(tx, ty);
        if (pressed) breakRun();
        switch (tool) {
            case TERRAIN -> {
                int r = brushSize / 2;
                if (box && boxFrom != null) {
                    int x0 = Math.min(boxFrom[0], tx), y0 = Math.min(boxFrom[1], ty);
                    ui.outline(ox + x0 * s, oy + y0 * s, (Math.abs(tx - boxFrom[0]) + 1) * s,
                            (Math.abs(ty - boxFrom[1]) + 1) * s, 2, Theme.ACCENT);
                } else if (shift && !fill && !box && lastPainted != null) {
                    ui.outline(ox + (lastPainted[0] - r) * s, oy + (lastPainted[1] - r) * s, brushSize * s, brushSize * s,
                            1, Theme.withAlpha(Theme.ACCENT, 0.7f));
                }
                ui.outline(ox + (tx - r) * s, oy + (ty - r) * s, brushSize * s, brushSize * s, 2,
                        Theme.withAlpha(Theme.TITLE, 0.8f));
                if (in.pressed(GLFW_KEY_Q) || alt && pressed) {      // pick up what is under the pointer
                    brush = map.terrain(tx, ty);
                    return;
                }
                if (!editable()) return;
                if (box) {
                    if (pressed) boxFrom = new int[]{tx, ty};
                    if (!down && boxFrom != null) {
                        map.fillRect(boxFrom[0], boxFrom[1], tx, ty, brush);
                        boxFrom = null;
                        changed("Box of " + brush.name());
                    }
                } else if (fill) {
                    if (pressed) {
                        map.flood(tx, ty, brush);
                        changed("Fill with " + brush.name());
                    }
                } else if (shift && pressed && lastPainted != null) {
                    map.line(lastPainted[0], lastPainted[1], tx, ty, brushSize, brush);
                    lastPainted = new int[]{tx, ty};
                    changed("Line of " + brush.name());
                } else if (down) {
                    if (lastPainted != null && !pressed) map.line(lastPainted[0], lastPainted[1], tx, ty, brushSize, brush);
                    else map.line(tx, ty, tx, ty, brushSize, brush);
                    lastPainted = new int[]{tx, ty};
                    changed("Paint " + brush.name());
                }
            }
            case NODES -> {
                ui.outline(ox + tx * s, oy + ty * s, s, s, 1, Theme.withAlpha(Theme.TITLE, 0.7f));
                if (pressed) {
                    if (at != null) {
                        selected = at.id;
                        draggingNode = editable();
                    } else if (editable()) {
                        Overworld.Node n = map.add("Destination " + (map.nodes().size() + 1), tx, ty);
                        if (!map.background().isEmpty()) n.kind = "city";       // (a picture's map: marks, not icons)
                        selected = n.id;
                        changed("Place " + n.name);
                        Sounds.play(SoundKeys.ui("place"));
                    } else {
                        selected = null;
                    }
                } else if (down && draggingNode && selected != null && at == null) {
                    Overworld.Node n = map.node(selected);
                    if (n != null && (n.x != tx || n.y != ty)) {
                        n.x = tx;
                        n.y = ty;
                        changed("Move " + n.name);
                    }
                }
            }
            case WAYS -> {
                ui.outline(ox + tx * s, oy + ty * s, s, s, 1, Theme.withAlpha(Theme.TITLE, 0.7f));
                if (!pressed || !editable()) return;
                if (at == null) {
                    if (from != null) bends.add(new int[]{tx, ty});
                } else if (from == null) {
                    from = at.id;
                    bends.clear();
                } else if (!at.id.equals(from)) {
                    if (map.way(from, at.id) != null && !bends.isEmpty()) map.link(from, at.id, List.of());
                    boolean made = map.link(from, at.id, new ArrayList<>(bends)) != null;
                    Sounds.play(SoundKeys.ui(made ? "place" : "remove"));
                    changed(made ? "Road" : "Take a road away");
                    breakRun();
                    from = null;
                    bends.clear();
                } else {
                    from = null;
                    bends.clear();
                }
            }
            default -> { }
        }
    }

    // --- destinations: copy, paste, duplicate, delete -------------------------------------

    /** A free tile at or near {@code (x, y)}, or null. */
    private int[] freeNear(int x, int y) {
        for (int r = 0; r < 6; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (map.inside(x + dx, y + dy) && map.nodeAt(x + dx, y + dy) == null) return new int[]{x + dx, y + dy};
                }
            }
        }
        return null;
    }

    private void duplicate(Overworld.Node n) {
        if (!editable()) return;
        int[] p = freeNear(n.x + 1, n.y);
        if (p == null) return;
        Overworld.Node c = map.addCopy(n, p[0], p[1]);
        selected = c.id;
        breakRun();
        changed("Duplicate " + n.name);
        Sounds.play(SoundKeys.ui("place"));
    }

    private void paste() {
        if (!editable() || clipboard == null) return;
        Ui ui = game.ui();
        Input in = ui.input();
        float s = scale;
        int tx = (int) Math.floor((in.mouseX() - Math.round(ox(ui))) / s);
        int ty = (int) Math.floor((in.mouseY() - Math.round(oy(ui))) / s);
        if (!overCanvas(ui) || !map.inside(tx, ty)) {
            tx = Math.round(camX);
            ty = Math.round(camY);
        }
        int[] p = freeNear(tx, ty);
        if (p == null) return;
        Overworld.Node c = map.addCopy(clipboard, p[0], p[1]);
        if (map.node(c.toMap) != null) c.toMap = "";
        selected = c.id;
        setTool(Tool.NODES);
        breakRun();
        changed("Paste " + c.name);
        Sounds.play(SoundKeys.ui("place"));
    }

    private void deleteNode(Overworld.Node n) {
        map.remove(n);
        selected = null;
        breakRun();
        changed("Delete " + n.name);
        Sounds.play(SoundKeys.ui("remove"));
    }

    // --- the bar along the top ---------------------------------------------------------

    private void bar(Ui ui) {
        ui.wood(0, 0, ui.width(), BAR, Theme.PANEL);
        ui.rect(0, BAR - 2, ui.width(), 2, Theme.BRASS_DARK);
        float x = 12;
        for (int i = 0; i < Tool.values().length; i++) {
            Tool t = Tool.values()[i];
            String label = (i + 1) + " " + t.label;
            float w = ui.body.width(label) + 24;
            if (ui.button(label, x, 8, w, 36, t == tool, true)) setTool(t);
            x += w + 6;
        }
        float bx = ui.width() - 12;
        String[] right = {"Back", "Try it  F5", "Save", "Settings", "Redo", "Undo"};
        float[] widths = {90, 104, 80, 96, 74, 74};
        for (int i = 0; i < right.length; i++) {
            bx -= widths[i] + 6;
            boolean enabled = switch (i) {
                case 2 -> editable();
                case 4 -> !redos.isEmpty();
                case 5 -> !undos.isEmpty();
                default -> true;
            };
            if (ui.button(right[i], bx, 8, widths[i], 36, i == 2 && dirty, enabled)) {
                switch (i) {
                    case 0 -> leave();
                    case 1 -> tryIt();
                    case 2 -> save();
                    case 3 -> game.openSettings();
                    case 4 -> redo();
                    case 5 -> undo();
                    default -> { }
                }
            }
            if (i >= 4 && ui.hover(bx, 8, widths[i], 36)) {
                Deque<Step> list = i == 4 ? redos : undos;
                widgets.tip(i == 4 ? "Redo (Ctrl+Y)" + (list.isEmpty() ? "" : ": " + list.peek().label())
                        : "Undo (Ctrl+Z)" + (list.isEmpty() ? "" : ": " + list.peek().label()), bx - 120, BAR + 4);
            }
        }
        String title = (editable() ? "" : "Main story: ") + map.name() + (dirty ? " *" : "");
        float tw = bx - x - 28;
        if (tw > 40) ui.text(ui.body, Ui.fit(ui.body, title, tw), x + 16, 15, dirty ? Theme.ACCENT : Theme.TITLE);
    }

    private void leave() {
        if (dirty && editable()) save();
        MainMenuScene.openOnDevTab();
        game.switchTo("menu");
    }

    private boolean save() {
        if (!editable()) {
            game.toast("The main story's map is not changed here: make a copy (Map, Make a copy)", Theme.WARNING);
            return false;
        }
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
        if (dirty && editable()) save();
        SaveGame trial = new SaveGame();
        trial.setName("Map test");
        trial.setWardrobe(game.wardrobe().copy());
        String cls = game.settings().heroClass;
        trial.setHeroClass(cls == null || cls.isBlank() ? "knight" : cls);
        OverworldEditor me = this;
        game.switchTo(new OverworldScene(game, trial, map.copy(), id, () -> game.switchTo(me)));
    }

    // --- the panel down the left -------------------------------------------------------

    private void panel(Ui ui, int tx, int ty) {
        float h = ui.height() - BAR;
        ui.wood(0, BAR, PANEL, h, Theme.PANEL);
        ui.rect(PANEL - 2, BAR, 2, h, Theme.BRASS_DARK);
        float x = 14, top = BAR + 12, w = PANEL - 28, foot = ui.height() - 50;
        Input in = ui.input();
        if (in.mouseX() < PANEL && in.mouseY() > BAR && in.scroll() != 0 && !widgets.dropped()) {
            panelScroll = Math.max(0, panelScroll - (float) in.scroll() * 40);
            in.consumeScroll();
        }
        ui.clip(0, BAR, PANEL - 2, foot - BAR);
        float y = top - panelScroll;
        ui.text(ui.large, tool.label, x, y, Theme.TITLE);
        y += 40;
        float end = switch (tool) {
            case TERRAIN -> terrainPanel(ui, x, y, w);
            case NODES -> nodePanel(ui, x, y, w);
            case WAYS -> waysPanel(ui, x, y, w);
            case MAP -> mapPanel(ui, x, y, w);
        };
        ui.unclip();
        float content = end + panelScroll - top;
        panelScroll = Math.max(0, Math.min(panelScroll, content - (foot - top) + 20));
        String where = tx < 0 ? "" : "tile " + tx + ", " + ty + (map.tilesShown() ? " · " + map.terrain(tx, ty).name() : "");
        ui.text(ui.tiny, where, x, foot + 4, Theme.ITEM);
        ui.text(ui.tiny, "Ctrl+Z/Y undo, redo · Ctrl+S save · G grid · wheel zooms · right-drag pans", x, foot + 22,
                Theme.ITEM_DISABLED);
    }

    private float readOnlyNote(Ui ui, float x, float y, float w) {
        return y + ui.paragraph(ui.small, "The main story's map cannot be changed. Make a copy of it (the banner, or "
                + "the Map tool) to change it as you like.", x, y, w, Theme.WARNING) + 12;
    }

    private float terrainPanel(Ui ui, float x, float y, float w) {
        if (!editable()) y = readOnlyNote(ui, x, y, w);
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
        int[] sizes = {1, 3, 5, 9};
        for (int i = 0; i < sizes.length; i++) {
            if (ui.button(sizes[i] + "", x + 56 + i * 42, y, 38, 32, brushSize == sizes[i] && !fill && !box, true)) {
                brushSize = sizes[i];
                fill = box = false;
            }
        }
        y += 38;
        if (ui.button("Box", x + 56, y, 80, 32, box, true)) {
            box = !box;
            fill = false;
        }
        if (ui.button("Fill", x + 142, y, 80, 32, fill, true)) {
            fill = !fill;
            box = false;
        }
        y += 44;
        boolean tiles = map.tilesShown();
        if (!map.background().isEmpty()) {
            boolean want = widgets.toggle(ui, "Tiles over the picture", tiles, x, y, w);
            if (want != tiles && editable()) {
                map.setTilesShown(want);
                breakRun();
                changed(want ? "Show the tiles" : "Hide the tiles");
            }
            y += 36;
        }
        y += ui.paragraph(ui.small, "Drag to paint; Shift+click paints a straight line from the last tile painted. "
                + "Box: drag a box of it. Fill floods every joined tile of the same terrain. Q (or Alt+click) picks up "
                + "the terrain under the pointer.", x, y, w, Theme.HINT);
        return y;
    }

    /** The kinds a destination can look like, as pictures to click: painted icons, then map marks. */
    private float kindPicker(Ui ui, Overworld.Node n, float x, float y, float w) {
        float cell = 38;
        int cols = (int) Math.floor((w + 4) / (cell + 4));
        for (int group = 0; group < 2; group++) {
            List<String> kinds = group == 0 ? Overworld.KINDS : Overworld.MARKERS;
            ui.text(ui.small, group == 0 ? "Painted icons" : "Map marks (filled once she can get there)", x, y,
                    Theme.ITEM);
            y += 22;
            for (int i = 0; i < kinds.size(); i++) {
                String k = kinds.get(i);
                float cx = x + (i % cols) * (cell + 4), cy = y + (i / cols) * (cell + 4);
                boolean on = k.equals(n.kind), hot = ui.hover(cx, cy, cell, cell);
                ui.rect(cx, cy, cell, cell, on ? Theme.BUTTON_HOVER : hot ? Theme.BUTTON_HOVER : Theme.BUTTON);
                ui.outline(cx, cy, cell, cell, on ? 2 : 1, on ? Theme.ACCENT : Theme.PANEL_EDGE);
                MapView.kind(ui, k, cx + cell / 2, cy + cell / 2, cell - 6);
                if (hot) widgets.tip(group == 0 ? capital(k) : capital(Overworld.markerName(k)), cx, cy + cell + 4);
                if (ui.clicked(cx, cy, cell, cell) && editable() && !on) {
                    n.kind = k;
                    breakRun();
                    changed("Look of " + n.name);
                }
            }
            y += ((kinds.size() + cols - 1) / cols) * (cell + 4) + 8;
        }
        return y;
    }

    private float nodePanel(Ui ui, float x, float y, float w) {
        Overworld.Node n = map.node(selected);
        if (n == null) {
            if (!editable()) y = readOnlyNote(ui, x, y, w);
            return y + ui.paragraph(ui.small, "Click an empty tile to place a destination; click one to choose it, drag "
                    + "it (or use the arrow keys) to move it.\n\nCtrl+C, Ctrl+V and Ctrl+D copy, paste and duplicate the "
                    + "one chosen; Delete takes it away.\n\nEach destination loads a level saved in Creative Mode when the "
                    + "player goes in, at the level's spawn.", x, y, w, Theme.HINT);
        }
        if (!editable()) {
            // the story's own: what it is, not changed
            y = readOnlyNote(ui, x, y, w);
            MapView.kind(ui, n.kind, x + 22, y + 20, 40);
            ui.text(ui.body, Ui.fit(ui.body, n.name, w - 54), x + 50, y + 8, Theme.TITLE);
            y += 50;
            for (String line : List.of("Loads: " + (n.level.isBlank() ? "(nothing)" : n.level),
                    "Cleared by: " + n.clear, n.open ? "Its roads on are open from the start" : "Its roads on open once "
                            + "it is cleared", n.travels() ? "Leads to the map " + n.toMap : "")) {
                if (line.isEmpty()) continue;
                y += ui.paragraph(ui.small, line, x, y, w, Theme.ITEM) + 4;
            }
            if (!n.about.isBlank()) y += ui.paragraph(ui.small, n.about, x, y + 6, w, Theme.HINT) + 10;
            return y;
        }
        String was = n.name + n.level + n.clear + n.quest + n.open + n.toMap + n.about;
        n.name = widgets.text(ui, "node.name." + n.id, "Name", n.name, x, y, w);
        y += 36;
        y = kindPicker(ui, n, x, y, w);
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
            mapShown.add(m.shown());
            mapIds.add(m.id());
        }
        int mi = widgets.choice(ui, "Leads to the map", mapShown, Math.max(0, mapIds.indexOf(n.toMap)), x, y, w);
        n.toMap = mapIds.get(mi);
        y += 36;
        n.about = widgets.text(ui, "node.about." + n.id, "About it", n.about, x, y, w);
        y += 44;
        if (!was.equals(n.name + n.level + n.clear + n.quest + n.open + n.toMap + n.about)) changed("Change " + n.name);
        boolean isStart = map.start() == n;
        if (ui.button(isStart ? "A new game starts here" : "Start a new game here", x, y, w, 34, isStart, !isStart)) {
            map.setStart(n.id);
            breakRun();
            changed("Start at " + n.name);
        }
        y += 42;
        float hw = (w - 8) / 2;
        if (ui.button("Duplicate  Ctrl+D", x, y, hw, 34)) duplicate(n);
        if (ui.button("Delete  Del", x + hw + 8, y, hw, 34)) {
            deleteNode(n);
            return y + 40;
        }
        y += 46;
        int roads = map.waysAt(n.id).size();
        y += ui.paragraph(ui.small, roads == 0 ? "No road leads here yet (the Roads tool)."
                : roads + (roads == 1 ? " road" : " roads") + " at " + n.x + ", " + n.y + ".", x, y, w,
                roads == 0 ? Theme.WARNING : Theme.HINT);
        return y;
    }

    private float waysPanel(Ui ui, float x, float y, float w) {
        if (!editable()) y = readOnlyNote(ui, x, y, w);
        Overworld.Node a = map.node(from);
        String now = a == null ? "Click the destination the road starts at."
                : "From " + a.name + ": click the tiles it bends at (" + bends.size() + " so far), then the destination "
                + "it leads to. Esc lets go.";
        y += ui.paragraph(ui.small, now, x, y, w, a == null ? Theme.HINT : Theme.ACCENT) + 14;
        y += ui.paragraph(ui.small, "The same two destinations again takes their road away.\n\nShe walks a road from "
                + "one end to the other; a road on from a destination is shut until it is cleared, unless its roads "
                + "are open from the start.", x, y, w, Theme.HINT) + 14;
        // how the roads are drawn
        List<String> looks = List.of("Dotted", "Ink line", "Dashed (red ink)", "Dirt track");
        int at = Math.max(0, Overworld.ROAD_STYLES.indexOf(map.roadStyle()));
        int pick = widgets.choice(ui, "Roads look", looks, at, x, y, w);
        if (pick != at && editable()) {
            map.setRoadStyle(Overworld.ROAD_STYLES.get(pick));
            breakRun();
            changed("Roads look");
        }
        y += 36;
        boolean smooth = widgets.toggle(ui, "Bend smoothly", map.smoothRoads(), x, y, w);
        if (smooth != map.smoothRoads() && editable()) {
            map.setSmoothRoads(smooth);
            breakRun();
            changed(smooth ? "Smooth roads" : "Straight roads");
        }
        y += 40;
        ui.text(ui.small, map.ways().size() + " roads, " + map.nodes().size() + " destinations", x, y, Theme.ITEM);
        y += 26;
        int unreachable = 0;
        for (Overworld.Node n : map.nodes()) if (!allReachable(n)) unreachable++;
        if (unreachable > 0) {
            y += ui.paragraph(ui.small, unreachable + " cannot be reached from the start by any road.", x, y, w,
                    Theme.WARNING);
        }
        return y;
    }

    /** Whether {@code n} could be reached once everything is cleared. */
    private boolean allReachable(Overworld.Node n) {
        Set<String> all = new java.util.HashSet<>();
        for (Overworld.Node m : map.nodes()) all.add(m.id);
        return map.reachable(n, all);
    }

    private float mapPanel(Ui ui, float x, float y, float w) {
        if (editable()) {
            String name = widgets.text(ui, "map.name", "Name", map.name(), x, y, w);
            if (!name.equals(map.name())) {
                map.setName(name);
                changed("Name");
            }
            y += 36;
            double mw = widgets.stepper(ui, "Width (tiles)", map.width(), 4, Overworld.SMALLEST, Overworld.WIDEST,
                    "%.0f", x, y, w);
            y += 36;
            double mh = widgets.stepper(ui, "Height (tiles)", map.height(), 4, Overworld.SMALLEST, Overworld.TALLEST,
                    "%.0f", x, y, w);
            y += 36;
            if ((int) mw != map.width() || (int) mh != map.height()) {
                map.resize((int) mw, (int) mh);
                changed("Size");
            }
            List<String> shown = new ArrayList<>(List.of("None", "Shuffle the songs"));
            for (Songs.Song song : Songs.ALL) shown.add(song.title());
            int cur = map.music().equals(Songs.NONE) ? 0 : map.music().equals(Songs.SHUFFLE) ? 1
                    : Math.max(1, 2 + Songs.ALL.indexOf(Songs.byId(map.music())));
            int pick = widgets.choice(ui, "Music", shown, cur, x, y, w);
            if (pick != cur) {
                map.setMusic(pick == 0 ? Songs.NONE : pick == 1 ? Songs.SHUFFLE : Songs.ALL.get(pick - 2).id());
                breakRun();
                changed("Music");
            }
            y += 40;
            y = backgroundPanel(ui, x, y, w);
            y = movePanel(ui, x, y, w);
            y = looksPanel(ui, x, y, w);
        } else {
            y = readOnlyNote(ui, x, y, w);
            ui.text(ui.body, Ui.fit(ui.body, map.name(), w), x, y, Theme.TITLE);
            y += 30;
            ui.text(ui.small, map.width() + " × " + map.height() + " tiles, " + map.nodes().size() + " destinations",
                    x, y, Theme.ITEM);
            y += 32;
        }
        boolean first = id.equals(Overworld.campaignStart(game.settings().assets));
        boolean want = widgets.toggle(ui, "Chosen first for a new game", first, x, y, w);
        if (want && !first) {
            try {
                if (dirty && editable()) save();
                Overworld.setCampaignStart(game.settings().assets, id);
                game.toast("A new game now sets out on \"" + map.name() + "\" unless another map is chosen", Theme.OK);
            } catch (IOException e) {
                game.toast("Cannot write campaign.json: " + e.getMessage(), Theme.WARNING);
            }
        }
        y += 44;
        ui.text(ui.body, "Maps", x, y, Theme.ACCENT);
        y += 30;
        for (Overworld.Saved m : maps) {
            boolean mine = m.id().equals(id);
            if (ui.button(Ui.fit(ui.small, m.shown(), w - 20), x, y, w, 30, mine, !mine)) open(m.id());
            y += 34;
        }
        float hw = (w - 8) / 2;
        if (ui.button("+ A new map", x, y, hw, 32)) {
            if (dirty && editable()) save();
            map = new Overworld("New map", 40, 24);
            id = Overworld.freeId(game.settings().assets, map.name());
            dirty = true;
            selected = from = null;
            resetUndo();
            centre();
        }
        if (ui.button("Make a copy", x + hw + 8, y, hw, 32)) copyMap();
        y += 44;
        if (editable()) {
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
                    open(firstEditable());
                    sureDelete = -10;
                } else {
                    sureDelete = time;
                }
            }
            y += 40;
            y = promotePanel(ui, x, y, w);
        } else {
            y += ui.paragraph(ui.small, "The main story's map cannot be deleted.", x, y, w, Theme.ITEM_DISABLED) + 8;
        }
        return y;
    }

    /** The picture the map lies over: choose one, drop one on the window, take it away, fit the map to it. */
    private float backgroundPanel(Ui ui, float x, float y, float w) {
        ui.text(ui.body, "Picture", x, y, Theme.ACCENT);
        y += 28;
        String bg = map.background();
        y += ui.paragraph(ui.small, bg.isEmpty() ? "A picture of your own (a PNG) for the map to lie over - choose one, "
                + "or drop it on the window. Destinations and roads go on top of it."
                : "Lies over " + bg + ".", x, y, w, Theme.HINT) + 8;
        float hw = (w - 8) / 2;
        if (ui.button(bg.isEmpty() ? "Choose a picture…" : "Another picture…", x, y, hw, 32)) choosePicture();
        if (ui.button("Take it away", x + hw + 8, y, hw, 32, false, !bg.isEmpty())) {
            map.setBackground("");
            map.setTilesShown(true);
            breakRun();
            changed("Take the picture away");
        }
        y += 38;
        if (!bg.isEmpty()) {
            boolean tiles = widgets.toggle(ui, "Tiles over it", map.tilesShown(), x, y, w);
            if (tiles != map.tilesShown()) {
                map.setTilesShown(tiles);
                breakRun();
                changed(tiles ? "Show the tiles" : "Hide the tiles");
            }
            y += 36;
            if (ui.button("Fit the map's shape to the picture", x, y, w, 32)) {
                fitToPicture();
            }
            y += 38;
            boolean marks = map.nodes().stream().allMatch(n -> n.marker() >= 0);
            if (!map.nodes().isEmpty() && !marks && ui.button("Show every destination as a map mark", x, y, w, 32)) {
                allMarks(true);
            }
            if (!map.nodes().isEmpty() && !marks) y += 38;
        }
        return y + 10;
    }

    private float movePanel(Ui ui, float x, float y, float w) {
        ui.text(ui.small, "Move everything", x, y + 8, Theme.ITEM);
        float bx = x + w - 4 * 40;
        String[] arrows = {"←", "↑", "↓", "→"};
        int[][] by = {{-1, 0}, {0, -1}, {0, 1}, {1, 0}};
        for (int i = 0; i < 4; i++) {
            if (ui.button(arrows[i], bx + i * 40, y, 36, 30)) {
                map.shift(by[i][0], by[i][1]);
                changed("Move everything");
            }
        }
        return y + 40;
    }

    /** Every destination as a map mark, or as a painted icon - the nearest of the other kind. */
    private float looksPanel(Ui ui, float x, float y, float w) {
        if (map.nodes().isEmpty()) return y;
        float hw = (w - 8) / 2;
        if (ui.button("All as marks", x, y, hw, 30)) allMarks(true);
        if (ui.button("All as icons", x + hw + 8, y, hw, 30)) allMarks(false);
        return y + 40;
    }

    /** The painted icons and the marks that stand for the same kind of place. */
    private static final Map<String, String> MARK_OF = Map.ofEntries(Map.entry("town", "city"),
            Map.entry("village", "hamlet"), Map.entry("castle", "keep"), Map.entry("camp", "camp_tents"),
            Map.entry("cave", "cavern"), Map.entry("tower", "watchtower"), Map.entry("port", "docks"),
            Map.entry("ruins", "ruined_tower"), Map.entry("mine", "mine_shaft"), Map.entry("workshop", "workshop_gears"),
            Map.entry("shrine", "temple"), Map.entry("arena", "fort"), Map.entry("crypt", "barrow"),
            Map.entry("portal", "gateway"));

    /** The mark for icon {@code kind} (or the icon for mark {@code kind}). */
    static String counterpart(String kind, boolean toMark) {
        if (toMark) return Overworld.MARKERS.contains(kind) ? kind : MARK_OF.getOrDefault(kind, "landmark");
        if (Overworld.KINDS.contains(kind)) return kind;
        for (Map.Entry<String, String> e : MARK_OF.entrySet()) if (e.getValue().equals(kind)) return e.getKey();
        return "town";
    }

    private void allMarks(boolean marks) {
        for (Overworld.Node n : map.nodes()) n.kind = counterpart(n.kind, marks);
        breakRun();
        changed(marks ? "Every destination a mark" : "Every destination an icon");
    }

    /** A map made in the editor made the main story's own (two clicks; the story's last one kept aside). */
    private float promotePanel(Ui ui, float x, float y, float w) {
        if (!game.settings().devUnlocked) return y;
        boolean sure = time - surePromote < 4;
        if (ui.button(sure ? "Sure? The story's map is replaced" : "Make this the main story's map", x, y, w, 32,
                sure, map.nodes().stream().anyMatch(Overworld.Node::playable))) {
            if (!sure) {
                surePromote = time;
            } else {
                promote();
                surePromote = -10;
            }
        }
        return y + 40;
    }

    private void promote() {
        Path assets = game.settings().assets;
        String storyId = null;
        for (Overworld.Saved s : Overworld.list(assets)) if (s.story()) storyId = storyId == null ? s.id() : storyId;
        if (storyId == null) storyId = "brass_vale";
        try {
            Path story = Overworld.storyFolder(assets).resolve(storyId + ".json");
            if (Files.isRegularFile(story)) {
                Path old = Overworld.storyFolder(assets).resolve("old");
                Files.createDirectories(old);
                Files.copy(story, old.resolve(storyId + "-" + System.currentTimeMillis() + ".json"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            map.save(story);
            game.toast("\"" + map.name() + "\" is now the main story's map (its last one is kept in story/old)",
                    Theme.OK);
            refresh();
        } catch (IOException e) {
            game.toast("Cannot write the story's map: " + e.getMessage(), Theme.WARNING);
        }
    }

    // --- pictures ------------------------------------------------------------------------

    private void choosePicture() {
        if (!editable()) return;
        if (!FilePicker.available()) {
            game.toast("No file dialog here (install zenity) - drop the PNG on the window instead", Theme.WARNING);
            return;
        }
        Path f = FilePicker.open("A picture for the map", null, "Pictures (PNG, JPG)", "*.png", "*.jpg", "*.jpeg");
        if (f != null) usePicture(f);
    }

    /** Files dropped on the window: the first picture is the map's. */
    private boolean dropped(List<Path> files) {
        for (Path f : files) {
            String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
            if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")) {
                if (!editable()) {
                    game.toast("Make a copy of the main story's map to put a picture under it", Theme.WARNING);
                    return true;
                }
                usePicture(f);
                return true;
            }
        }
        return false;
    }

    /** Copy picture {@code f} into the backgrounds folder and lay the map over it, its shape fitted to it. */
    private void usePicture(Path f) {
        try {
            BufferedImage image = ImageIO.read(f.toFile());
            if (image == null) throw new IOException("not a picture this game can read");
            Path dir = Overworld.backgrounds(game.settings().assets);
            Files.createDirectories(dir);
            String name = f.getFileName().toString().replaceAll("[^A-Za-z0-9._-]+", "_");
            Path to = dir.resolve(name);
            if (Files.exists(to) && Files.mismatch(f, to) != -1L) {
                String base = name.replaceAll("\\.[^.]*$", ""), ext = name.substring(base.length());
                for (int i = 2; Files.exists(to) && Files.mismatch(f, to) != -1L; i++) to = dir.resolve(base + "_" + i + ext);
            }
            if (!Files.exists(to)) Files.copy(f, to);
            MapView.forget(to.getFileName().toString());
            map.setBackground(to.getFileName().toString());
            map.setTilesShown(false);
            fit(image.getWidth(), image.getHeight());
            breakRun();
            changed("Picture");
            centre();
            game.toast("The map lies over " + to.getFileName() + " (kept in assets/overworld/backgrounds)", Theme.OK);
        } catch (IOException | RuntimeException e) {
            game.toast("Cannot use that picture: " + e.getMessage(), Theme.WARNING);
        }
    }

    private void fitToPicture() {
        var tex = MapView.background(map.background());
        if (tex == null) return;
        fit(tex.width(), tex.height());
        breakRun();
        changed("Fit to the picture");
    }

    /** The map's height made to match a picture {@code iw} by {@code ih} at its width (its tiles square). */
    private void fit(int iw, int ih) {
        int w = map.width();
        int h = Math.round(w * ih / (float) Math.max(1, iw));
        if (h > Overworld.TALLEST) {
            h = Overworld.TALLEST;
            w = Math.round(h * iw / (float) Math.max(1, ih));
        }
        if (h < Overworld.SMALLEST) {
            h = Overworld.SMALLEST;
            w = Math.round(h * iw / (float) Math.max(1, ih));
        }
        map.resize(Math.max(Overworld.SMALLEST, Math.min(Overworld.WIDEST, w)), h);
    }

    // --- maps ----------------------------------------------------------------------------

    /** A copy of this map, saved as one of one's own, and opened to change. */
    private void copyMap() {
        if (dirty && editable()) save();
        Overworld c = map.copy();
        c.setName(map.name() + " (copy)");
        String newId = Overworld.freeId(game.settings().assets, c.name());
        try {
            // (a copy is one's own: it is saved among the maps made in the editor)
            Overworld own = Overworld.parse(c.json());
            own.save(Overworld.folder(game.settings().assets).resolve(newId + ".json"));
            refresh();
            open(newId);
            game.toast("Made \"" + c.name() + "\" - yours to change", Theme.OK);
        } catch (IOException e) {
            game.toast("Cannot copy the map: " + e.getMessage(), Theme.WARNING);
        }
    }

    private void open(String other) {
        if (dirty && editable()) save();
        Overworld m = other == null ? null : Overworld.byId(game.settings().assets, other);
        if (m == null) {
            m = new Overworld("New map", 40, 24);
            other = Overworld.freeId(game.settings().assets, m.name());
            dirty = true;
        } else {
            dirty = false;
        }
        map = m;
        id = other;
        selected = from = null;
        bends.clear();
        panelScroll = 0;
        resetUndo();
        centre();
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
    }

    /**
     * Script commands: {@code maptool terrain|destinations|roads|map},
     * {@code select <destination>}, {@code zoom n}, {@code tryit}, {@code
     * open <map>}, {@code undo}, {@code redo}, {@code grid}, {@code picture
     * <file>}, {@code kind <kind>}, {@code roads <style>}.
     */
    @Override
    public boolean command(String command, String argument) {
        switch (command) {
            case "maptool" -> {
                for (Tool t : Tool.values()) {
                    if (t.label.equalsIgnoreCase(argument.trim()) || t.name().equalsIgnoreCase(argument.trim())) setTool(t);
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
            case "zoom" -> scale = Math.max(4, Math.min(72, Float.parseFloat(argument.trim())));
            case "tryit" -> tryIt();
            case "open" -> open(argument.trim());
            case "undo" -> undo();
            case "redo" -> redo();
            case "grid" -> grid = !grid;
            case "picture" -> usePicture(Path.of(argument.trim()));
            case "kind" -> {
                Overworld.Node n = map.node(selected);
                if (n != null && editable() && Overworld.isKind(argument.trim())) {
                    n.kind = argument.trim();
                    breakRun();
                    changed("Look of " + n.name);
                }
            }
            case "roads" -> {
                if (editable()) {
                    map.setRoadStyle(argument.trim());
                    breakRun();
                    changed("Roads look");
                }
            }
            case "smooth" -> {
                if (editable()) {
                    map.setSmoothRoads(!map.smoothRoads());
                    breakRun();
                    changed("Smooth roads");
                }
            }
            case "allmarks" -> allMarks(true);
            default -> {
                return false;
            }
        }
        return true;
    }
}
