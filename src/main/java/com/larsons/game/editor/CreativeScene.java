package com.larsons.game.editor;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.combat.CombatTuning;
import com.larsons.game.combat.FlyingDummy;
import com.larsons.game.core.Autopilot;
import com.larsons.game.core.Game;
import com.larsons.game.core.Scene;
import com.larsons.game.input.Input;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelIO;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.LevelWorld;
import com.larsons.game.level.ObjectType;
import com.larsons.game.level.ObjectType.Category;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.scene.DemoScene;
import com.larsons.game.scene.MainMenuScene;
import com.larsons.game.sprite.PixelPalette;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.GroundMap;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Sky;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.Tier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Creative Mode: the level editor, where the game's areas are built - in
 * the spirit of Halo's Forge. The level is shown live, as it will be
 * played ({@link LevelView}); the camera flies over it (WASD, Space and C up
 * and down, a right-drag to turn, the wheel to zoom) and the tools along the
 * top work on what the pointer is over:
 *
 * <ul>
 *   <li><b>Place</b> - anything from the palette (every tree, rock and tuft,
 *       every weapon and wearable, the chest, the dummies, the vines, the
 *       spawn, named spots) stands under the pointer as it will be; a click
 *       puts it down. R and Q / E turn it, G snaps to a half-metre grid.</li>
 *   <li><b>Select</b> - click (Shift to add), drag on the ground to box in,
 *       drag to move, Delete, Ctrl+D to duplicate, Ctrl+C / Ctrl+V; the
 *       properties panel edits where it is, which way it faces and its
 *       kind's props.</li>
 *   <li><b>Sculpt</b> - raise, lower, smooth or flatten the ground under a
 *       round brush.</li>
 *   <li><b>Paint</b> - dirt, forest floor, gravel, flower meadow, or back to
 *       grass.</li>
 *   <li><b>Scatter</b> - sow grass, trees or rocks under the brush (Shift
 *       pulls them up).</li>
 *   <li><b>Sky</b> - the sky's colours, the fog, and the distant backgrounds:
 *       mountains, hills, a tree line, towers, the sea - all in the game's
 *       128 colours.</li>
 *   <li><b>Level</b> - its name and size; save, play-test, leave.</li>
 * </ul>
 *
 * <p>Ctrl+Z undoes and Ctrl+Y redoes ({@link Undo}); Ctrl+S saves it to
 * {@code assets/levels/} ({@link LevelIO}); F5 drops her into the level
 * under the pointer to play it ({@link DemoScene}), and F5 there (or Esc →
 * Back to the editor) comes back to it as it was.
 */
public final class CreativeScene implements Scene, Autopilot.Scriptable {

    /** The tools along the top. */
    enum Tool {
        PLACE("Place"), SELECT("Select"), SCULPT("Sculpt"), PAINT("Paint"), SCATTER("Scatter"), SKY("Sky"),
        LEVEL("Level");

        final String label;

        Tool(String label) {
            this.label = label;
        }
    }

    /** What the sculpting brush does. */
    enum Sculpt { RAISE, LOWER, SMOOTH, FLATTEN }

    /** The paint brush's materials: grass (paints the others away), then {@link GroundMap}'s four. */
    static final String[] MATERIALS = {"Grass", "Dirt", "Forest floor", "Gravel", "Flower meadow"};

    /** How far the camera can pull back, metres. */
    static final double FAR = 160;
    /** The grid things snap to, metres; the turn they snap to, degrees. */
    static final double GRID = 0.5, TURN = 15;
    /** The panels' widths and the top bar's height, pixels. */
    static final float LEFT_W = 278, RIGHT_W = 286, TOP_H = 46, HELP_H = 30;
    /** How often a held scatter brush sows, seconds. */
    static final double SOW_EVERY = 0.08;

    private final Game game;
    private final Level level;
    private Path folder;
    private boolean dirty;
    private final ScenerySprites sprites;
    private final LevelView view;
    private final OrbitCamera camera = new OrbitCamera(0.5, Math.toRadians(50), 26);
    private Vec3 focus = Vec3.ZERO;
    private double focusLift;
    private Gizmos gizmos;
    private final Widgets widgets = new Widgets();
    private final Undo undo = new Undo();
    private final Random random = new Random();
    private final int[] colours;
    private boolean entered;

    private Tool tool = Tool.PLACE;
    // --- place
    private final List<ObjectTypes.Entry> palette;
    private Category category = Category.SCENERY;
    private int paletteScroll;
    private LevelObject template;
    private String templateLabel = "";
    private boolean snap;
    // --- select
    private final List<LevelObject> selection = new ArrayList<>();
    private final List<LevelObject> clipboard = new ArrayList<>();
    private boolean moving, boxing, movedYet;
    private Vec3 dragFrom;
    private final Map<LevelObject, double[]> origins = new LinkedHashMap<>();
    private float boxX, boxY;
    // --- brushes
    private Sculpt sculpt = Sculpt.RAISE;
    private int material = 1;
    private double radius = 3, strength = 0.5, flatHeight;
    private boolean stroking;
    private double sowTimer, brushSound;
    private final List<Scatter.Set> sets;
    private int set;
    private double density = 0.6;
    // --- sky
    /** Which colour the swatches set: 0 the zenith, 1 the horizon, 2 the backdrop chosen. */
    private int skyTarget;
    private int backdrop = -1;
    private int addKind;
    // --- the pointer, and what it is over
    private Vec3 cursor;
    private LevelObject hover;
    private Vec3 scriptedCursor;
    private boolean hideUi;
    private boolean thinFog = true;
    private double time, leaveAt = -10;
    private String propSnapshot;

    /**
     * Edit {@code level}, saved in {@code folder} (null: not yet saved - the
     * first save makes it a folder of its own in {@code assets/levels/}).
     */
    public CreativeScene(Game game, Level level, Path folder) {
        this.game = game;
        this.level = level;
        this.folder = folder;
        this.dirty = folder == null;
        this.sprites = game.scenerySprites();
        this.view = new LevelView(level, game.wardrobe(), sprites, CombatTuning.load(game.settings().assets));
        this.palette = ObjectTypes.palette();
        this.sets = Scatter.sets(sprites);
        PixelPalette pp = PixelPalette.load(game.settings().spritesDir());
        this.colours = pp == null ? fallbackColours() : concat(pp.fills(), pp.lines());
        camera.setMaxDistance(FAR);
        LevelObject spawn = level.spawn();
        if (spawn != null) focus = new Vec3(spawn.x(), 0, spawn.z());
        choose(firstOf(category));
    }

    @Override
    public String name() {
        return "Creative Mode: " + level.name() + (dirty ? " *" : "");
    }

    @Override
    public void enter() {
        if (gizmos == null) gizmos = new Gizmos(game.batch());
        if (!entered) camera.snapTo(focusPoint());
        entered = true;
        game.window().setTitle(Game.TITLE + " — " + name());
    }

    /** The level being edited. */
    public Level level() { return level; }

    // --- the palette ------------------------------------------------------------------------

    private int firstOf(Category c) {
        for (int i = 0; i < palette.size(); i++) if (palette.get(i).category() == c) return i;
        return 0;
    }

    private List<Integer> entriesOf(Category c) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) if (palette.get(i).category() == c) out.add(i);
        return out;
    }

    /** Place palette entry {@code i} from now on (keeping the turn it was being placed at). */
    private void choose(int i) {
        if (palette.isEmpty()) return;
        ObjectTypes.Entry e = palette.get(Math.max(0, Math.min(palette.size() - 1, i)));
        double yaw = template == null ? 0 : template.yaw();
        template = e.make(0, 0, 0, yaw);
        templateLabel = e.label();
        category = e.category();
    }

    // --- the loop -----------------------------------------------------------------------------

    @Override
    public void update(double dt) {
        time += dt;
        Input in = game.window().input();
        if (game.importer().visible()) return;
        double mx = in.mouseX(), my = in.mouseY();
        boolean overUi = !hideUi && overUi(mx, my);
        boolean ctrl = in.down(GLFW_KEY_LEFT_CONTROL) || in.down(GLFW_KEY_RIGHT_CONTROL);
        boolean shift = in.down(GLFW_KEY_LEFT_SHIFT) || in.down(GLFW_KEY_RIGHT_SHIFT);

        if (!widgets.typing()) keys(in, dt, ctrl, shift);
        if (game.scene() != this) return;               // (a key took us to the play-test or the menu)

        // --- the camera
        boolean orbiting = in.mouseDown(GLFW_MOUSE_BUTTON_RIGHT) || in.mouseDown(GLFW_MOUSE_BUTTON_MIDDLE);
        if (orbiting) camera.rotate(-in.mouseDX() * 0.008, in.mouseDY() * 0.006);
        if (in.scroll() != 0 && !overUi) {
            if (shift && brushTool()) {
                radius = clampRadius(radius * Math.pow(1.12, Math.signum(in.scroll())));
            } else {
                camera.zoom(in.scroll());
            }
            in.consumeScroll();
        }

        // --- what the pointer is over
        Picking.Ray ray = Picking.ray(camera, mx, my, game.ui().width(), game.ui().height());
        cursor = scriptedCursor != null ? view.terrain().onGround(scriptedCursor) : Picking.ground(ray, view.terrain());
        hover = overUi || brushTool() ? null : pick(ray);

        // --- the tools
        boolean press = in.mousePressed(GLFW_MOUSE_BUTTON_LEFT) && !overUi;
        boolean held = in.mouseDown(GLFW_MOUSE_BUTTON_LEFT);
        switch (tool) {
            case PLACE -> {
                if (press && cursor != null && level.contains(cursor.x(), cursor.z())) place(at(cursor));
            }
            case SELECT -> selectTool(press, held, shift, mx, my);
            case SCULPT -> sculptTool(press, held, shift, ctrl, dt);
            case PAINT -> paintTool(press, held, shift, dt);
            case SCATTER -> scatterTool(press, held, shift, dt);
            default -> { }
        }
        if (!held) stroking = false;
        boolean ghost = tool == Tool.PLACE && !overUi && cursor != null && template != null
                && level.contains(cursor.x(), cursor.z());
        if (ghost) {
            Vec3 at = at(cursor);
            view.setGhost(template.copy().moveTo(at.x(), at.z()));
        } else {
            view.setGhost(null);
        }

        view.refresh();
        if (thinFog) view.world().setSky(level.sky().withFog(Math.max(level.sky().fog(), camera.distance() * 2.6)));
        view.world().idle(dt);
        camera.update(dt, focusPoint());
        camera.keepAbove(view.terrain(), 0.5);
    }

    /** The keys: the editor's own, then the camera's. */
    private void keys(Input in, double dt, boolean ctrl, boolean shift) {
        if (in.pressed(GLFW_KEY_ESCAPE)) {
            in.consumeKey(GLFW_KEY_ESCAPE);
            if (!selection.isEmpty()) selection.clear();
            else leave();
            return;
        }
        if (ctrl && in.pressed(GLFW_KEY_S)) save();
        if (ctrl && (in.pressed(GLFW_KEY_Y) || shift && in.pressed(GLFW_KEY_Z))) redo();
        else if (ctrl && in.pressed(GLFW_KEY_Z)) undo();
        if (in.pressed(GLFW_KEY_F5)) {
            playTest(shift ? null : cursor != null ? cursor : focus);
            return;
        }
        if (!ctrl) {
            int[] toolKeys = {GLFW_KEY_1, GLFW_KEY_2, GLFW_KEY_3, GLFW_KEY_4, GLFW_KEY_5, GLFW_KEY_6, GLFW_KEY_7};
            for (int i = 0; i < toolKeys.length; i++) if (in.pressed(toolKeys[i])) setTool(Tool.values()[i]);
            if (in.pressed(GLFW_KEY_H)) hideUi = !hideUi;
            if (in.pressed(GLFW_KEY_G)) {
                snap = !snap;
                game.toast(snap ? "Snap to a " + GRID + " m grid" : "Snap off", Theme.HINT);
            }
            if (in.pressed(GLFW_KEY_F)) frameSelection();
            if (in.pressed(GLFW_KEY_TAB)) camera.cyclePreset();
            double turn = (in.repeated(GLFW_KEY_R) ? (shift ? -TURN : TURN) : 0) + (in.pressed(GLFW_KEY_Q) ? 45 : 0)
                    - (in.pressed(GLFW_KEY_E) ? 45 : 0);
            if (turn != 0) turn(Math.toRadians(turn));
            if (in.repeated(GLFW_KEY_PAGE_UP)) lift(0.25);
            if (in.repeated(GLFW_KEY_PAGE_DOWN)) lift(-0.25);
            if (in.pressed(GLFW_KEY_DELETE) || in.pressed(GLFW_KEY_BACKSPACE)) deleteSelection();
            if (in.repeated(GLFW_KEY_LEFT_BRACKET)) {
                if (shift) strength = Math.max(0.05, strength - 0.05);
                else radius = clampRadius(radius / 1.15);
            }
            if (in.repeated(GLFW_KEY_RIGHT_BRACKET)) {
                if (shift) strength = Math.min(1, strength + 0.05);
                else radius = clampRadius(radius * 1.15);
            }
        } else {
            if (in.pressed(GLFW_KEY_D)) duplicate();
            if (in.pressed(GLFW_KEY_C)) copy();
            if (in.pressed(GLFW_KEY_V)) paste();
            if (in.pressed(GLFW_KEY_A)) {
                selection.clear();
                selection.addAll(level.objects());
                setTool(Tool.SELECT);
            }
        }
        // the camera flies: WASD over the ground, Space and C up and down
        if (!ctrl) {
            double f = 0, r = 0, u = 0;
            if (in.down(GLFW_KEY_W)) f += 1;
            if (in.down(GLFW_KEY_S)) f -= 1;
            if (in.down(GLFW_KEY_D)) r += 1;
            if (in.down(GLFW_KEY_A)) r -= 1;
            if (in.down(GLFW_KEY_SPACE)) u += 1;
            if (in.down(GLFW_KEY_C)) u -= 1;
            double speed = (4 + camera.distance() * 0.9) * (shift ? 2.5 : 1);
            if (f != 0 || r != 0) {
                Vec3 move = camera.groundForward().scale(f).add(camera.right().scale(r)).normalize().scale(speed * dt);
                double h = level.half() + 20;
                focus = new Vec3(Math.max(-h, Math.min(h, focus.x() + move.x())), 0,
                        Math.max(-h, Math.min(h, focus.z() + move.z())));
            }
            if (u != 0) focusLift = Math.max(0, Math.min(80, focusLift + u * speed * 0.6 * dt));
        }
        double orbit = 0, tilt = 0;
        if (in.down(GLFW_KEY_LEFT)) orbit += 1;
        if (in.down(GLFW_KEY_RIGHT)) orbit -= 1;
        if (in.down(GLFW_KEY_UP)) tilt += 1;
        if (in.down(GLFW_KEY_DOWN)) tilt -= 1;
        if (orbit != 0 || tilt != 0) camera.rotate(orbit * dt * 1.6, tilt * dt * 1.1);
        if (in.repeated(GLFW_KEY_EQUAL) || in.repeated(GLFW_KEY_KP_ADD)) camera.zoom(1);
        if (in.repeated(GLFW_KEY_MINUS) || in.repeated(GLFW_KEY_KP_SUBTRACT)) camera.zoom(-1);
    }

    /** What the camera looks at: the spot it flies over, on the ground there, lifted. */
    private Vec3 focusPoint() {
        Terrain t = view.terrain();
        return new Vec3(focus.x(), t.height(focus.x(), focus.z()) + 0.8 + focusLift, focus.z());
    }

    private void setTool(Tool t) {
        if (t == tool) return;
        tool = t;
        moving = boxing = stroking = false;
        widgets.stopTyping();
    }

    private boolean brushTool() {
        return tool == Tool.SCULPT || tool == Tool.PAINT || tool == Tool.SCATTER;
    }

    private static double clampRadius(double r) {
        return Math.max(0.5, Math.min(40, r));
    }

    /** {@code p} snapped to the grid, if snapping is on. */
    private Vec3 at(Vec3 p) {
        if (!snap) return p;
        return new Vec3(Math.round(p.x() / GRID) * GRID, p.y(), Math.round(p.z() / GRID) * GRID);
    }

    private void resize(double size) {
        undo.before(level, Undo.Part.ALL, "Resize");
        level.resize(size);
        selection.clear();
        changed();
        game.toast("The level is now " + (int) size + " m a side (Ctrl+Z puts it back)", Theme.HINT);
    }

    private void changed() {
        dirty = true;
        view.objectsChanged();
        game.window().setTitle(Game.TITLE + " — " + name());
    }

    // --- placing and selecting ------------------------------------------------------------------

    private LevelObject place(Vec3 at) {
        if (template == null) return null;
        undo.before(level, Undo.Part.OBJECTS, "Place " + templateLabel);
        LevelObject o = level.add(template.copy(level.nextId()).moveTo(at.x(), at.z()));
        changed();
        Sounds.play(SoundKeys.ui("place"));
        return o;
    }

    /** The object the ray passes into first, or null. */
    private LevelObject pick(Picking.Ray ray) {
        LevelObject best = null;
        double bestT = Double.MAX_VALUE;
        Terrain t = view.terrain();
        for (LevelObject o : level.objects()) {
            double r = ObjectTypes.radius(o, sprites);
            double y0 = t.height(o.x(), o.z()) + o.lift();
            double hit = Picking.cylinder(ray, o.x(), o.z(), r, y0 - 0.1, y0 + height(o));
            if (hit >= 0 && hit < bestT) {
                bestT = hit;
                best = o;
            }
        }
        return best;
    }

    /** How tall {@code o} stands, to click on: a scenery sprite's own height, else its kind's. */
    private double height(LevelObject o) {
        if (o.type().equals(ObjectTypes.SCENERY)) {
            ScenerySprites.Sprite s = sprites.sprite(o.str("sprite", ""));
            if (s != null) return Math.max(0.3, s.profile().frameWorldHeight() * 0.9);
        }
        ObjectType type = ObjectTypes.get(o.type());
        return type == null ? 1 : type.height();
    }

    private void selectTool(boolean press, boolean held, boolean shift, double mx, double my) {
        if (press) {
            if (hover != null) {
                if (shift) {
                    if (!selection.remove(hover)) selection.add(hover);
                } else if (!selection.contains(hover)) {
                    selection.clear();
                    selection.add(hover);
                }
                Sounds.play(SoundKeys.ui("select"));
                if (selection.contains(hover) && cursor != null) {
                    moving = true;
                    movedYet = false;
                    dragFrom = cursor;
                    origins.clear();
                    for (LevelObject o : selection) origins.put(o, new double[]{o.x(), o.z()});
                }
            } else {
                if (!shift) selection.clear();
                boxing = true;
                boxX = (float) mx;
                boxY = (float) my;
            }
        }
        if (moving && held && cursor != null && dragFrom != null) {
            double dx = cursor.x() - dragFrom.x(), dz = cursor.z() - dragFrom.z();
            if (!movedYet && Math.hypot(dx, dz) > 0.05) {
                undo.before(level, Undo.Part.OBJECTS, "Move");
                movedYet = true;
            }
            if (movedYet) {
                for (Map.Entry<LevelObject, double[]> e : origins.entrySet()) {
                    Vec3 to = at(new Vec3(e.getValue()[0] + dx, 0, e.getValue()[1] + dz));
                    double h = level.half();
                    e.getKey().moveTo(Math.max(-h, Math.min(h, to.x())), Math.max(-h, Math.min(h, to.z())));
                }
                changed();
            }
        }
        if (!held) {
            if (boxing) boxSelect(mx, my);
            moving = boxing = false;
        }
    }

    /** Select everything whose spot is inside the box dragged out on the screen. */
    private void boxSelect(double mx, double my) {
        double x0 = Math.min(boxX, mx), x1 = Math.max(boxX, mx), y0 = Math.min(boxY, my), y1 = Math.max(boxY, my);
        if (x1 - x0 < 4 && y1 - y0 < 4) return;
        Mat4 vp = camera.viewProjection(game.ui().width() / (double) Math.max(1, game.ui().height()));
        Terrain t = view.terrain();
        for (LevelObject o : level.objects()) {
            Vec3 s = Picking.screen(vp, new Vec3(o.x(), t.height(o.x(), o.z()) + o.lift(), o.z()),
                    game.ui().width(), game.ui().height());
            if (s.z() > 0 && s.x() >= x0 && s.x() <= x1 && s.y() >= y0 && s.y() <= y1 && !selection.contains(o)) {
                selection.add(o);
            }
        }
    }

    private void turn(double radians) {
        if (tool == Tool.PLACE && template != null) {
            template.turnTo(snapTurn(template.yaw() + radians));
            Sounds.play(SoundKeys.ui("rotate"));
            return;
        }
        if (selection.isEmpty()) return;
        undo.before(level, Undo.Part.OBJECTS, "Turn");
        for (LevelObject o : selection) o.turnTo(snapTurn(o.yaw() + radians));
        changed();
        Sounds.play(SoundKeys.ui("rotate"));
    }

    private static double snapTurn(double yaw) {
        double step = Math.toRadians(TURN);
        return Math.round(yaw / step) * step;
    }

    private void lift(double metres) {
        if (tool == Tool.PLACE && template != null) {
            template.setLift(Math.max(0, template.lift() + metres));
            return;
        }
        if (selection.isEmpty()) return;
        undo.before(level, Undo.Part.OBJECTS, "Lift");
        for (LevelObject o : selection) o.setLift(Math.max(0, o.lift() + metres));
        changed();
    }

    private void deleteSelection() {
        if (selection.isEmpty()) return;
        undo.before(level, Undo.Part.OBJECTS, "Delete " + selection.size());
        for (LevelObject o : selection) level.remove(o);
        game.toast("Deleted " + selection.size() + (selection.size() == 1 ? " thing" : " things"), Theme.HINT);
        selection.clear();
        changed();
        Sounds.play(SoundKeys.ui("remove"));
    }

    private void duplicate() {
        if (selection.isEmpty()) return;
        undo.before(level, Undo.Part.OBJECTS, "Duplicate");
        List<LevelObject> made = new ArrayList<>();
        for (LevelObject o : selection) {
            ObjectType t = ObjectTypes.get(o.type());
            if (t != null && t.single()) continue;
            made.add(level.add(o.copy(level.nextId()).moveTo(o.x() + 1, o.z() + 1)));
        }
        selection.clear();
        selection.addAll(made);
        changed();
        Sounds.play(SoundKeys.ui("place"));
    }

    private void copy() {
        if (selection.isEmpty()) return;
        clipboard.clear();
        for (LevelObject o : selection) clipboard.add(o.copy());
        game.toast("Copied " + clipboard.size(), Theme.HINT);
    }

    /** Paste what was copied round the pointer, as it stood round its middle. */
    private void paste() {
        if (clipboard.isEmpty()) return;
        Vec3 at = cursor != null ? cursor : focus;
        double cx = 0, cz = 0;
        for (LevelObject o : clipboard) {
            cx += o.x() / clipboard.size();
            cz += o.z() / clipboard.size();
        }
        undo.before(level, Undo.Part.OBJECTS, "Paste");
        selection.clear();
        for (LevelObject o : clipboard) {
            Vec3 p = at(new Vec3(at.x() + o.x() - cx, 0, at.z() + o.z() - cz));
            if (!level.contains(p.x(), p.z())) continue;
            selection.add(level.add(o.copy(level.nextId()).moveTo(p.x(), p.z())));
        }
        setTool(Tool.SELECT);
        changed();
        Sounds.play(SoundKeys.ui("place"));
    }

    /** F: fly to what is selected (or the spawn). */
    private void frameSelection() {
        List<LevelObject> what = selection.isEmpty() && level.spawn() != null ? List.of(level.spawn()) : selection;
        if (what.isEmpty()) return;
        double x = 0, z = 0;
        for (LevelObject o : what) {
            x += o.x() / what.size();
            z += o.z() / what.size();
        }
        focus = new Vec3(x, 0, z);
        focusLift = 0;
    }

    // --- the brushes --------------------------------------------------------------------------

    private void sculptTool(boolean press, boolean held, boolean shift, boolean ctrl, double dt) {
        if (cursor == null) return;
        if (press && ctrl) {
            flatHeight = cursor.y();
            sculpt = Sculpt.FLATTEN;
            game.toast(String.format("Flatten to %.2f m", flatHeight), Theme.HINT);
            return;
        }
        if (press) {
            undo.before(level, Undo.Part.HEIGHTS, "Sculpt");
            stroking = true;
            if (sculpt == Sculpt.FLATTEN && flatHeight == 0 && cursor.y() != 0) flatHeight = cursor.y();
        }
        if (!stroking || !held) return;
        applySculpt(cursor.x(), cursor.z(), shift, dt);
        brushTick(dt);
    }

    /** One frame of the sculpting brush at {@code (x, z)}. */
    void applySculpt(double x, double z, boolean inverse, double dt) {
        var hf = level.heights();
        switch (sculpt) {
            case RAISE, LOWER -> {
                double sign = (sculpt == Sculpt.RAISE) != inverse ? 1 : -1;
                hf.raise(x, z, radius, sign * strength * 3.0 * dt);
            }
            case SMOOTH -> hf.smooth(x, z, radius, Math.min(1, strength * 8 * dt));
            case FLATTEN -> hf.flatten(x, z, radius, flatHeight, Math.min(1, strength * 6 * dt));
        }
        dirty = true;
    }

    private void paintTool(boolean press, boolean held, boolean shift, double dt) {
        if (cursor == null) return;
        if (press) {
            undo.before(level, Undo.Part.GROUND, "Paint");
            stroking = true;
        }
        if (!stroking || !held) return;
        level.ground().paint(shift ? -1 : material - 1, cursor.x(), cursor.z(), radius, Math.min(1, strength * 5 * dt));
        dirty = true;
        brushTick(dt);
    }

    private void scatterTool(boolean press, boolean held, boolean shift, double dt) {
        if (cursor == null || sets.isEmpty()) return;
        if (press) {
            undo.before(level, Undo.Part.OBJECTS, shift ? "Pull up" : "Scatter");
            stroking = true;
            sowTimer = 0;
        }
        if (!stroking || !held) return;
        sowTimer -= dt;
        if (sowTimer > 0) return;
        sowTimer = SOW_EVERY;
        Scatter.Set s = sets.get(Math.floorMod(set, sets.size()));
        if (shift) {
            if (Scatter.clear(level, s, cursor.x(), cursor.z(), radius) > 0) changed();
        } else {
            double area = Math.PI * radius * radius;
            if (!Scatter.sow(level, view.terrain(), sprites, s, cursor.x(), cursor.z(), radius, density, area * 0.2,
                    random).isEmpty()) {
                changed();
            }
        }
        brushTick(SOW_EVERY);
    }

    private void brushTick(double dt) {
        brushSound -= dt;
        if (brushSound <= 0) {
            brushSound = 0.25;
            Sounds.play(SoundKeys.ui("brush"));
        }
    }

    // --- undo, saving, play-testing, leaving ------------------------------------------------------

    private void undo() {
        String what = undo.undo(level);
        if (what == null) return;
        selection.clear();
        changed();
        Sounds.play(SoundKeys.ui("undo"));
        game.toast("Undid: " + what, Theme.HINT);
    }

    private void redo() {
        String what = undo.redo(level);
        if (what == null) return;
        selection.clear();
        changed();
        Sounds.play(SoundKeys.ui("undo"));
        game.toast("Redid: " + what, Theme.HINT);
    }

    /** Save it to its folder in {@code assets/levels/} (a new one the first time). */
    boolean save() {
        Path root = game.settings().levelsDir();
        try {
            if (folder == null) folder = LevelIO.freeFolder(root, level.name());
            LevelIO.save(level, folder);
            dirty = false;
            game.window().setTitle(Game.TITLE + " — " + name());
            Sounds.play(SoundKeys.ui("save"));
            game.toast("Saved \"" + level.name() + "\" to " + game.relative(folder), Theme.OK);
            return true;
        } catch (IOException | RuntimeException e) {
            System.err.println("[levels] cannot save " + level.name() + ": " + e.getMessage());
            game.toast("Cannot save: " + e.getMessage(), Theme.WARNING);
            return false;
        }
    }

    /** Play it: from {@code at} (null: its spawn), facing the way the camera looks - F5 comes back. */
    private void playTest(Vec3 at) {
        widgets.stopTyping();
        view.setGhost(null);
        Sounds.play(SoundKeys.ui("playtest"));
        double heading = camera.yaw();
        if (at != null && !level.contains(at.x(), at.z())) at = null;
        game.switchTo(new DemoScene(game, level, at, heading, () -> {
            Sounds.play(SoundKeys.ui("playtest"));
            game.switchTo(this);
        }));
    }

    /** Back to the main menu: with unsaved changes, only when asked twice. */
    private void leave() {
        if (dirty && time - leaveAt > 3) {
            leaveAt = time;
            game.toast("Unsaved changes: Ctrl+S saves - Esc again leaves without saving", Theme.WARNING);
            return;
        }
        toMenu();
    }

    /** The main menu, on its Dev tab - where the level is listed. */
    private void toMenu() {
        MainMenuScene.openOnDevTab();
        game.switchTo("menu");
    }

    private static String things(int n) {
        return n == 1 ? "1 thing" : n + " things";
    }

    // --- drawing ------------------------------------------------------------------------------

    @Override
    public void render() {
        var w = game.window();
        int fw = w.framebufferWidth(), fh = w.framebufferHeight();
        game.worldRenderer().render(view.world(), camera, fw, fh);
        Mat4 vp = camera.viewProjection(fw / (double) Math.max(1, fh));
        marks(vp);

        Ui ui = game.ui();
        ui.begin();
        labels(ui, vp);
        if (!hideUi) {
            topBar(ui);
            leftPanel(ui);
            rightPanel(ui);
            help(ui);
            if (boxing) {
                Input in = ui.input();
                float x0 = (float) Math.min(boxX, in.mouseX()), y0 = (float) Math.min(boxY, in.mouseY());
                float bw = (float) Math.abs(in.mouseX() - boxX), bh = (float) Math.abs(in.mouseY() - boxY);
                ui.rect(x0, y0, bw, bh, Theme.withAlpha(Theme.ACCENT, 0.12f));
                ui.outline(x0, y0, bw, bh, 1, Theme.ACCENT);
            }
        }
        ui.end();
    }

    /** The marks in the world: the level's edge, what has no sprite, what is selected and hovered, the brush. */
    private void marks(Mat4 vp) {
        Terrain t = view.terrain();
        gizmos.begin(vp, t);
        double h = level.half();
        int edge = 0xE0705A;
        gizmos.line(-h, -h, h, -h, 0.12, edge, 0.8f);
        gizmos.line(h, -h, h, h, 0.12, edge, 0.8f);
        gizmos.line(h, h, -h, h, 0.12, edge, 0.8f);
        gizmos.line(-h, h, -h, -h, 0.12, edge, 0.8f);
        for (LevelObject o : level.objects()) {
            ObjectType type = ObjectTypes.get(o.type());
            int c = type == null ? 0xFF00FF : type.colour();
            Vec3 foot = new Vec3(o.x(), t.height(o.x(), o.z()) + o.lift(), o.z());
            switch (o.type()) {
                case ObjectTypes.SPAWN -> {
                    gizmos.ring(o.x(), o.z(), 0.7, c, 0.85f);
                    arrow(o, c, 1.4);
                }
                case ObjectTypes.MARKER -> {
                    gizmos.post(foot, 1.6, c, 0.9f, camera);
                    gizmos.ring(o.x(), o.z(), 0.4, c, 0.7f);
                }
                case ObjectTypes.VINES -> {
                    Terrain.Wall wall = LevelWorld.wall(o, level.heights());
                    gizmos.line(wall.x0(), wall.z0(), wall.x1(), wall.z1(), 0.18, c, 0.9f);
                    arrow(o, c, 1.2);
                }
                case ObjectTypes.SOUND -> {
                    gizmos.post(foot, 1.0, c, 0.9f, camera);
                    gizmos.ring(o.x(), o.z(), 0.5, c, 0.8f);
                    gizmos.ring(o.x(), o.z(), 1.0, c, 0.4f);
                }
                case ObjectTypes.FLIER -> {
                    gizmos.ring(o.x(), o.z(), FlyingDummy.RADIUS_OF_ROUNDS, c, 0.35f);
                    gizmos.ring(o.x(), o.z(), 0.5, c, 0.8f);
                    gizmos.upright(foot, foot.add(0, FlyingDummy.ALTITUDE, 0), 0.05, c, 0.6f, camera);
                }
                default -> {
                    if (type == null) gizmos.post(foot, 1.2, c, 0.9f, camera);
                }
            }
        }
        if (hover != null && !selection.contains(hover)) {
            gizmos.ring(hover.x(), hover.z(), ObjectTypes.radius(hover, sprites) + 0.1, 0xFFFFFF, 0.55f);
        }
        for (LevelObject o : selection) {
            gizmos.ring(o.x(), o.z(), ObjectTypes.radius(o, sprites) + 0.15, 0xFFD25A, 0.95f);
            if (selection.size() == 1 && !o.type().equals(ObjectTypes.SCENERY)) arrow(o, 0xFFD25A, 1.0);
        }
        if (cursor != null && !hideUi || cursor != null && scriptedCursor != null) {
            if (brushTool()) {
                int bc = tool == Tool.SCULPT ? 0xFFE08A : tool == Tool.PAINT ? 0x9CE07A : 0x7FD0FF;
                gizmos.disc(cursor.x(), cursor.z(), radius, bc, 0.12f);
                gizmos.ring(cursor.x(), cursor.z(), radius, bc, 0.9f);
            } else if (tool == Tool.PLACE && template != null && level.contains(cursor.x(), cursor.z())) {
                Vec3 at = at(cursor);
                gizmos.ring(at.x(), at.z(), ObjectTypes.radius(template, sprites) + 0.1, 0x8CF08C, 0.85f);
                arrow(template.copy().moveTo(at.x(), at.z()), 0x8CF08C, 1.0);
            }
        }
        gizmos.end();
    }

    /** A short line along the ground the way {@code o} faces. */
    private void arrow(LevelObject o, int rgb, double length) {
        Vec3 d = com.larsons.game.sprite.SpriteView.headingVector(o.yaw());
        gizmos.line(o.x(), o.z(), o.x() + d.x() * length, o.z() + d.z() * length, 0.08, rgb, 0.9f);
    }

    /** The names over the markers, the spawn and anything the game does not know. */
    private void labels(Ui ui, Mat4 vp) {
        Terrain t = view.terrain();
        for (LevelObject o : level.objects()) {
            String text = switch (o.type()) {
                case ObjectTypes.SPAWN -> "Spawn";
                case ObjectTypes.MARKER -> o.str("name", "spot");
                case ObjectTypes.VINES -> "Vines";
                case ObjectTypes.SOUND -> "Sound: " + ObjectTypes.readable(o.str("sound", "?"));
                default -> ObjectTypes.get(o.type()) == null ? o.type() + "?" : null;
            };
            if (text == null) continue;
            Vec3 s = Picking.screen(vp, new Vec3(o.x(), t.height(o.x(), o.z()) + o.lift() + 2.0, o.z()), ui.width(),
                    ui.height());
            if (s.z() <= 0) continue;
            float tw = ui.small.width(text);
            ui.rect((float) s.x() - tw / 2 - 5, (float) s.y() - 2, tw + 10, 20, Theme.withAlpha(Theme.BACKGROUND, 0.7f));
            ui.text(ui.small, text, (float) s.x() - tw / 2, (float) s.y(), Theme.TITLE);
        }
    }

    /** The rectangles the panels take up, as drawn: the pointer over one is not over the world. */
    private boolean overUi(double mx, double my) {
        Ui ui = game.ui();
        if (my < TOP_H || my > ui.height() - HELP_H) return true;
        if (mx < LEFT_W + 8) return true;
        return rightPanelShown() && mx > ui.width() - RIGHT_W - 8;
    }

    private boolean rightPanelShown() {
        return tool == Tool.PLACE && template != null || tool == Tool.SELECT && !selection.isEmpty();
    }

    private void topBar(Ui ui) {
        ui.rect(0, 0, ui.width(), TOP_H, Theme.withAlpha(Theme.PANEL, 0.96f));
        ui.rect(0, TOP_H - 1, ui.width(), 1, Theme.PANEL_EDGE);
        float x = 8;
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            String label = (i + 1) + " " + tools[i].label;
            float bw = ui.body.width(label) + 22;
            if (ui.button(label, x, 6, bw, TOP_H - 12, tools[i] == tool, true)) setTool(tools[i]);
            x += bw + 4;
        }
        String[] right = {"Undo", "Redo", "Save", "Play (F5)", "Menu"};
        float rx = ui.width() - 8;
        float[] widths = new float[right.length];
        for (int i = right.length - 1; i >= 0; i--) {
            widths[i] = ui.body.width(right[i]) + 22;
            rx -= widths[i] + 4;
        }
        float nameX = x + 10, nameW = rx - nameX - 6;
        String title = level.name() + (dirty ? " *" : "");
        while (title.length() > 4 && ui.body.width(title) > nameW) title = title.substring(0, title.length() - 3) + "…";
        if (nameW > 40) ui.text(ui.body, title, nameX, (TOP_H - ui.body.lineHeight()) / 2, Theme.ACCENT);
        float bx = rx + 4;
        for (int i = 0; i < right.length; i++) {
            boolean enabled = switch (i) {
                case 0 -> undo.canUndo();
                case 1 -> undo.canRedo();
                default -> true;
            };
            if (ui.button(right[i], bx, 6, widths[i], TOP_H - 12, false, enabled)) {
                switch (i) {
                    case 0 -> undo();
                    case 1 -> redo();
                    case 2 -> save();
                    case 3 -> playTest(cursor != null ? cursor : focus);
                    case 4 -> leave();
                    default -> { }
                }
            }
            bx += widths[i] + 4;
        }
    }

    private void help(Ui ui) {
        String h = switch (tool) {
            case PLACE -> "Click: place · R / Shift+R: turn 15° · Q / E: turn 45° · PgUp / PgDn: lift · G: snap to grid";
            case SELECT -> "Click: select · Shift+click: add · drag: move · drag on the ground: box · Del: delete · "
                    + "Ctrl+D: duplicate · Ctrl+C / V: copy, paste · R / Q / E: turn · F: fly to it";
            case SCULPT -> "Hold the left button: sculpt · Shift: the other way · Ctrl+click: take the height to "
                    + "flatten to · [ ]: brush size (or Shift+wheel) · Shift+[ ]: strength";
            case PAINT -> "Hold the left button: paint · Shift: back to grass · [ ]: brush size (or Shift+wheel) · "
                    + "Shift+[ ]: strength";
            case SCATTER -> "Hold the left button: scatter · Shift: pull up · [ ]: brush size (or Shift+wheel)";
            case SKY -> "Pick what to colour, then click a swatch · the fog and the backdrops show as you change them";
            case LEVEL -> "Name the level, choose its size, save it · Shift+F5: play from the spawn";
        };
        String all = "   ·   WASD fly · Space / C up, down · right-drag turn · wheel zoom · Ctrl+Z / Y undo, redo · "
                + "Ctrl+S save · F5 play · H hide · Esc menu";
        float y = ui.height() - HELP_H;
        ui.rect(0, y, ui.width(), HELP_H, Theme.withAlpha(Theme.PANEL, 0.92f));
        // whole hints dropped from the end until it fits
        String text = h + all;
        while (ui.small.width(text) > ui.width() - 20 && text.lastIndexOf(" · ") > 0) {
            text = text.substring(0, text.lastIndexOf(" · ")).stripTrailing();
        }
        ui.text(ui.small, text, 10, y + (HELP_H - ui.small.lineHeight()) / 2, Theme.HINT);
    }

    // --- the left panel: the tool's own -------------------------------------------------------------

    private void leftPanel(Ui ui) {
        float x = 8, y = TOP_H + 8, w = LEFT_W - 8, h = ui.height() - TOP_H - HELP_H - 16;
        ui.panel(x, y, w, h);
        float px = x + 12, pw = w - 24, py = y + 10;
        switch (tool) {
            case PLACE -> placePanel(ui, px, py, pw, y + h);
            case SELECT -> selectPanel(ui, px, py, pw);
            case SCULPT -> sculptPanel(ui, px, py, pw);
            case PAINT -> paintPanel(ui, px, py, pw);
            case SCATTER -> scatterPanel(ui, px, py, pw);
            case SKY -> skyPanel(ui, px, py, pw);
            case LEVEL -> levelPanel(ui, px, py, pw);
        }
    }

    private float heading(Ui ui, String text, float x, float y) {
        ui.text(ui.body, text, x, y, Theme.ACCENT);
        return y + 30;
    }

    private void placePanel(Ui ui, float x, float y, float w, float bottom) {
        y = heading(ui, "Place", x, y);
        Category[] cats = Category.values();
        float cw = (w - 6) / 2;
        for (int i = 0; i < cats.length; i++) {
            float cx = x + (i % 2) * (cw + 6), cy = y + (i / 2) * 32;
            if (ui.button(cats[i].label(), cx, cy, cw, 28, cats[i] == category, !entriesOf(cats[i]).isEmpty())) {
                category = cats[i];
                paletteScroll = 0;
                choose(firstOf(cats[i]));
            }
        }
        y += ((cats.length + 1) / 2) * 32 + 8;
        List<Integer> entries = entriesOf(category);
        float row = 26;
        int rows = Math.max(1, (int) ((bottom - y - 60) / row));
        Input in = ui.input();
        if (ui.hover(x, y, w, rows * row) && in.scroll() != 0) {
            paletteScroll -= (int) Math.signum(in.scroll()) * 3;
            in.consumeScroll();
        }
        paletteScroll = Math.max(0, Math.min(Math.max(0, entries.size() - rows), paletteScroll));
        for (int k = 0; k < rows && k + paletteScroll < entries.size(); k++) {
            int i = entries.get(k + paletteScroll);
            ObjectTypes.Entry e = palette.get(i);
            boolean on = template != null && e.label().equals(templateLabel) && e.type().equals(template.type());
            float ry = y + k * row;
            boolean hot = ui.hover(x, ry, w, row - 2);
            ui.rect(x, ry, w, row - 2, on ? Theme.BUTTON_HOVER : hot ? Theme.withAlpha(Theme.BUTTON_HOVER, 0.6f)
                    : Theme.withAlpha(Theme.BUTTON, 0.5f));
            ui.text(ui.small, e.label(), x + 8, ry + (row - 2 - ui.small.lineHeight()) / 2,
                    on ? Theme.ITEM_SELECTED : Theme.ITEM);
            if (ui.clicked(x, ry, w, row - 2)) choose(i);
        }
        if (entries.size() > rows) {
            ui.text(ui.small, (paletteScroll + 1) + "–" + Math.min(entries.size(), paletteScroll + rows) + " of "
                    + entries.size() + " (wheel)", x, y + rows * row + 4, Theme.HINT);
        }
        float ty = bottom - 44;
        snap = widgets.toggle(ui, "Snap (G)", snap, x, ty, w);
    }

    private void selectPanel(Ui ui, float x, float y, float w) {
        y = heading(ui, "Select", x, y);
        ui.paragraph(ui.small, "Click something to select it; Shift+click adds to the selection. Drag on the "
                + "ground to box things in. Drag what is selected to move it.", x, y, w, Theme.HINT);
        y += 96;
        ui.text(ui.body, selection.isEmpty() ? "Nothing selected" : selection.size() + " selected", x, y, Theme.TITLE);
        y += 34;
        if (ui.button("Select all (Ctrl+A)", x, y, w, 30)) {
            selection.clear();
            selection.addAll(level.objects());
        }
        y += 38;
        if (ui.button("Duplicate (Ctrl+D)", x, y, w, 30, false, !selection.isEmpty())) duplicate();
        y += 38;
        if (ui.button("Delete (Del)", x, y, w, 30, false, !selection.isEmpty())) deleteSelection();
        y += 38;
        if (ui.button("Down onto the ground", x, y, w, 30, false, !selection.isEmpty())) {
            undo.before(level, Undo.Part.OBJECTS, "Down onto the ground");
            for (LevelObject o : selection) o.setLift(0);
            changed();
        }
        y += 46;
        snap = widgets.toggle(ui, "Snap (G)", snap, x, y, w);
        y += 40;
        ui.text(ui.small, things(level.objects().size()) + " in the level", x, y, Theme.ITEM_DISABLED);
    }

    private void brushSliders(Ui ui, float x, float y, float w, String strengthLabel) {
        radius = widgets.slider(ui, "radius", "Size (m)", radius, 0.5, 40, "%.1f", x, y, w);
        strength = widgets.slider(ui, "strength", strengthLabel, strength, 0.05, 1, "%.2f", x, y + 40, w);
    }

    private void sculptPanel(Ui ui, float x, float y, float w) {
        y = heading(ui, "Sculpt the ground", x, y);
        String[] names = {"Raise", "Lower", "Smooth", "Flatten"};
        float bw = (w - 6) / 2;
        for (int i = 0; i < names.length; i++) {
            if (ui.button(names[i], x + (i % 2) * (bw + 6), y + (i / 2) * 34, bw, 30, sculpt.ordinal() == i, true)) {
                sculpt = Sculpt.values()[i];
            }
        }
        y += 80;
        brushSliders(ui, x, y + 10, w, "Strength");
        y += 100;
        flatHeight = widgets.stepper(ui, "Flatten to (m)", flatHeight, 0.25, -Terrain.STEP * 200, 100, "%.2f",
                x, y, w);
        y += 44;
        ui.paragraph(ui.small, "Flatten brings the ground to that height; Ctrl+click takes it from the ground. "
                + "Steeper than 45° is rock she cannot walk up - put vines on it (Place, Terrain) to climb it. "
                + "The edge of the level stays at 0, where it meets the ground beyond.", x, y, w, Theme.HINT);
    }

    private void paintPanel(Ui ui, float x, float y, float w) {
        y = heading(ui, "Paint the ground", x, y);
        for (int i = 0; i < MATERIALS.length; i++) {
            if (ui.button(MATERIALS[i], x, y, w, 30, material == i, true)) material = i;
            y += 36;
        }
        brushSliders(ui, x, y + 14, w, "Strength");
        y += 104;
        ui.paragraph(ui.small, "Grass paints the others away; Shift does the same with whatever is chosen.",
                x, y, w, Theme.HINT);
    }

    private void scatterPanel(Ui ui, float x, float y, float w) {
        y = heading(ui, "Scatter", x, y);
        if (sets.isEmpty()) {
            ui.paragraph(ui.small, "No scenery sprites in assets/scenery/.", x, y, w, Theme.WARNING);
            return;
        }
        List<String> names = new ArrayList<>();
        for (Scatter.Set s : sets) names.add(s.label());
        set = widgets.choice(ui, "Sow", names, set, x, y, w);
        y += 44;
        radius = widgets.slider(ui, "radius", "Size (m)", radius, 0.5, 40, "%.1f", x, y + 10, w);
        density = widgets.slider(ui, "density", "Thickness", density, 0.05, 1, "%.2f", x, y + 50, w);
        y += 100;
        ui.paragraph(ui.small, "Hold the left button to sow; Shift pulls up what you are sowing. Trees keep "
                + "their distance, nothing grows inside a trunk or a rock, and trees and rocks stay off steep ground.",
                x, y, w, Theme.HINT);
    }

    private void skyPanel(Ui ui, float x, float y, float w) {
        y = heading(ui, "Sky and distant backgrounds", x, y);
        Sky sky = level.sky();
        String[] targets = {"Overhead", "Horizon", "Backdrop"};
        float bw = (w - 8) / 3;
        for (int i = 0; i < targets.length; i++) {
            boolean ok = i < 2 || backdrop >= 0 && backdrop < sky.backdrops().size();
            if (ui.button(targets[i], x + i * (bw + 4), y, bw, 28, skyTarget == i, ok)) skyTarget = i;
        }
        y += 36;
        int current = skyTarget == 0 ? sky.zenith() : skyTarget == 1 ? sky.horizon()
                : backdrop >= 0 && backdrop < sky.backdrops().size() ? sky.backdrops().get(backdrop).colour() : -1;
        int cols = 16;
        float size = Math.min(16, w / cols);
        int picked = widgets.swatches(ui, colours, current, x, y, cols, size);
        if (picked >= 0) {
            undo.before(level, Undo.Part.SKY, "Colour");
            if (skyTarget == 0) level.setSky(sky.withZenith(picked));
            else if (skyTarget == 1) level.setSky(sky.withHorizon(picked));
            else if (backdrop >= 0 && backdrop < sky.backdrops().size()) setBackdrop(sky.backdrops().get(backdrop)
                    .withColour(picked));
            dirty = true;
            sky = level.sky();
        }
        y += Widgets.swatchesHeight(colours.length, cols, size) + 8;
        double fog = widgets.slider(ui, "fog", "Fog (m)", sky.fog(), Sky.MIN_FOG, Sky.MAX_FOG, "%.0f", x, y + 10, w);
        if (fog != sky.fog()) skyEdit(sky.withFog(fog), "Fog");
        y += 48;
        thinFog = widgets.toggle(ui, "Thin fog here", thinFog, x, y, w);
        y += 38;

        // the backdrops
        ui.text(ui.small, "Backdrops (" + sky.backdrops().size() + " of " + Sky.MAX_BACKDROPS + ", far to near)", x, y,
                Theme.ITEM);
        y += 22;
        List<Sky.Backdrop> list = sky.backdrops();
        for (int i = 0; i < list.size(); i++) {
            Sky.Backdrop b = list.get(i);
            if (ui.button((i + 1) + ". " + b.kind().label(), x, y, w - 34, 26, i == backdrop, true)) {
                backdrop = i;
                skyTarget = 2;
            }
            if (ui.button("x", x + w - 30, y, 30, 26)) {
                List<Sky.Backdrop> next = new ArrayList<>(list);
                next.remove(i);
                skyEdit(sky.withBackdrops(next), "Remove a backdrop");
                if (backdrop >= next.size()) backdrop = next.size() - 1;
                return;
            }
            y += 30;
        }
        List<String> kinds = new ArrayList<>();
        for (Sky.Kind k : Sky.Kind.values()) kinds.add(k.label());
        addKind = widgets.choice(ui, "Add", kinds, addKind, x, y, w - 60);
        if (ui.button("Add", x + w - 56, y, 56, Widgets.ROW, false, list.size() < Sky.MAX_BACKDROPS)) {
            List<Sky.Backdrop> next = new ArrayList<>(list);
            Sky.Backdrop b = Sky.Backdrop.of(Sky.Kind.values()[addKind]).withSeed(1 + random.nextInt(999));
            next.add(b);
            skyEdit(sky.withBackdrops(next), "Add " + b.kind().label());
            backdrop = next.size() - 1;
            skyTarget = 2;
        }
        y += 38;
        if (backdrop >= 0 && backdrop < list.size()) backdropEditor(ui, list.get(backdrop), x, y, w);
    }

    private void backdropEditor(Ui ui, Sky.Backdrop b, float x, float y, float w) {
        Sky.Backdrop n = b;
        if (b.kind() != Sky.Kind.OCEAN) {
            n = n.withHeight(widgets.slider(ui, "bd-height", "Height (°)", n.height(), 0.5, 30, "%.1f", x, y + 10, w));
            y += 40;
        } else {
            n = n.withDistance(widgets.slider(ui, "bd-dist", "Shore (m)", n.distance(), 10, 600, "%.0f", x, y + 10, w));
            y += 40;
        }
        n = n.withHaze(widgets.slider(ui, "bd-haze", "Haze", n.haze(), 0, 1, "%.2f", x, y + 10, w));
        y += 40;
        double from = widgets.slider(ui, "bd-from", "From (°)", n.from(), 0, 360, "%.0f", x, y + 10, w);
        y += 40;
        double to = widgets.slider(ui, "bd-to", "To (°)", n.to(), 0, 360, "%.0f", x, y + 10, w);
        y += 40;
        n = n.withArc(from, to);
        n = n.withSeed((int) widgets.stepper(ui, "Shape", n.seed(), 1, 1, 9999, "%.0f", x, y, w));
        y += 34;
        if (b.kind() == Sky.Kind.MOUNTAINS) {
            n = n.withSnow(widgets.toggle(ui, "Snow", n.snow(), x, y, w));
            y += 34;
        }
        ui.text(ui.small, n.allRound() ? "All the way round" : "From " + (int) n.from() + "° to " + (int) n.to()
                + "° (0 north, 90 east)", x, y + 4, Theme.HINT);
        if (!n.equals(b)) setBackdrop(n);
    }

    private void setBackdrop(Sky.Backdrop b) {
        List<Sky.Backdrop> next = new ArrayList<>(level.sky().backdrops());
        if (backdrop < 0 || backdrop >= next.size()) return;
        next.set(backdrop, b);
        skyEdit(level.sky().withBackdrops(next), "Backdrop");
    }

    /** Change the sky to {@code s} - one undo step for a whole drag of a slider. */
    private void skyEdit(Sky s, String label) {
        String key = "sky:" + label;
        if (!widgets.dragging() || !key.equals(propSnapshot)) undo.before(level, Undo.Part.SKY, label);
        propSnapshot = widgets.dragging() ? key : null;
        level.setSky(s);
        dirty = true;
    }

    private void levelPanel(Ui ui, float x, float y, float w) {
        y = heading(ui, "Level", x, y);
        String name = widgets.text(ui, "level-name", "Name", level.name(), x, y, w);
        if (!name.equals(level.name())) {
            if (!"level-name".equals(propSnapshot)) undo.before(level, Undo.Part.ALL, "Rename");
            propSnapshot = "level-name";
            level.setName(name);
            dirty = true;
        }
        y += 40;
        List<String> sizes = new ArrayList<>();
        int at = 0;
        for (int i = 0; i < Level.SIZES.length; i++) {
            sizes.add(Level.SIZES[i] + " × " + Level.SIZES[i] + " m");
            if (Level.SIZES[i] == (int) level.size()) at = i;
        }
        int pick = widgets.choice(ui, "Size", sizes, at, x, y, w);
        if (pick != at) resize(Level.SIZES[pick]);
        y += 40;
        ui.paragraph(ui.small, (folder == null ? "Not saved yet" : "Saved in " + game.relative(folder))
                + "\n" + things(level.objects().size()) + " placed", x, y, w, Theme.HINT);
        y += 60;
        if (ui.button("Save (Ctrl+S)", x, y, w, 32)) save();
        y += 40;
        if (ui.button("Play from the spawn (Shift+F5)", x, y, w, 32)) playTest(null);
        y += 40;
        if (ui.button("Back to the menu (Esc)", x, y, w, 32)) leave();
        y += 50;
        ui.paragraph(ui.small, "Everything here is saved in the level's folder: level.json (readable, one thing "
                + "a line), height.png (the ground's heights) and ground.png (its paint).", x, y, w,
                Theme.ITEM_DISABLED);
    }

    // --- the right panel: what is being placed, or what is selected ------------------------------------

    private void rightPanel(Ui ui) {
        if (!rightPanelShown()) return;
        float w = RIGHT_W - 8, x = ui.width() - RIGHT_W, y = TOP_H + 8, h = ui.height() - TOP_H - HELP_H - 16;
        ui.panel(x, y, w, h);
        float px = x + 12, pw = w - 24, py = y + 10;
        if (tool == Tool.PLACE) {
            py = heading(ui, "Placing", px, py);
            ui.text(ui.body, templateLabel, px, py, Theme.TITLE);
            py += 32;
            properties(ui, template, false, px, py, pw);
            return;
        }
        if (selection.size() > 1) {
            py = heading(ui, selection.size() + " selected", px, py);
            Map<String, Integer> kinds = new LinkedHashMap<>();
            for (LevelObject o : selection) kinds.merge(ObjectTypes.label(o), 1, Integer::sum);
            for (Map.Entry<String, Integer> e : kinds.entrySet()) {
                if (py > y + h - 40) break;
                ui.text(ui.small, e.getValue() + " × " + e.getKey(), px, py, Theme.ITEM);
                py += 20;
            }
            return;
        }
        LevelObject o = selection.get(0);
        py = heading(ui, ObjectTypes.label(o), px, py);
        ui.text(ui.small, o.type() + " #" + o.id(), px, py - 6, Theme.ITEM_DISABLED);
        py += 18;
        properties(ui, o, true, px, py, pw);
    }

    /**
     * The properties of {@code o}: where it stands (when it is placed), its
     * turn, its lift, then its kind's props - each changed as one undo step.
     */
    private void properties(Ui ui, LevelObject o, boolean placed, float x, float y, float w) {
        double r = Widgets.ROW + 8;
        if (placed) {
            double nx = widgets.stepper(ui, "East (x)", o.x(), GRID, -level.half(), level.half(), "%.2f", x, y, w);
            y += r;
            double nz = widgets.stepper(ui, "South (z)", o.z(), GRID, -level.half(), level.half(), "%.2f", x, y, w);
            y += r;
            if (nx != o.x() || nz != o.z()) edit(o, "Move", () -> o.moveTo(nx, nz));
        }
        double deg = Math.toDegrees(o.yaw());
        double nd = widgets.stepper(ui, "Facing (°)", deg, TURN, -720, 720, "%.0f", x, y, w);
        y += r;
        if (nd != deg) edit(o, "Turn", () -> o.turnTo(Math.toRadians(nd)));
        double nl = widgets.stepper(ui, "Lift (m)", o.lift(), 0.25, 0, 50, "%.2f", x, y, w);
        y += r;
        if (nl != o.lift()) edit(o, "Lift", () -> o.setLift(nl));
        ObjectType type = ObjectTypes.get(o.type());
        if (type == null) {
            ui.paragraph(ui.small, "This kind (\"" + o.type() + "\") is not known to this version of the game. It is "
                    + "kept as it is and saved again.", x, y, w, Theme.WARNING);
            return;
        }
        for (ObjectType.Prop p : type.props()) {
            Object v = p.value(o);
            switch (p.kind()) {
                case NUMBER -> {
                    double cur = v instanceof Number n ? n.doubleValue() : 0;
                    double nv = widgets.stepper(ui, p.label(), cur, p.step(), p.min(), p.max(),
                            p.step() < 1 ? "%.2f" : "%.0f", x, y, w);
                    if (nv != cur) edit(o, p.label(), () -> o.set(p.key(), nv));
                }
                case TEXT -> {
                    String cur = String.valueOf(v);
                    String nv = widgets.text(ui, "prop:" + o.id() + ":" + p.key(), p.label(), cur, x, y, w);
                    if (!nv.equals(cur)) edit(o, p.label(), () -> o.set(p.key(), nv));
                }
                case CHOICE -> {
                    List<String> options = p.choices() == null ? List.of() : p.choices().apply(o);
                    if (options.isEmpty() || options.size() == 1 && options.get(0).isEmpty()) {
                        if (p.key().equals("tier")) {
                            ui.text(ui.small, "Tier: none (" + itemRarity(o) + ")", x, y + 6, Theme.ITEM_DISABLED);
                        }
                        break;
                    }
                    int at = Math.max(0, options.indexOf(String.valueOf(v)));
                    List<String> shown = new ArrayList<>();
                    for (String opt : options) shown.add(shownChoice(p.key(), opt));
                    int ni = widgets.choice(ui, p.label(), shown, at, x, y, w);
                    if (ni != at || !options.get(at).equals(String.valueOf(v))) {
                        String nv = options.get(ni);
                        edit(o, p.label(), () -> {
                            o.set(p.key(), nv);
                            if (p.key().equals("item")) fixTier(o);
                        });
                    }
                }
                case TOGGLE -> {
                    boolean cur = v instanceof Boolean b && b;
                    boolean nv = widgets.toggle(ui, p.label(), cur, x, y, w);
                    if (nv != cur) edit(o, p.label(), () -> o.set(p.key(), nv));
                }
            }
            y += r;
        }
    }

    private String itemRarity(LevelObject o) {
        var item = ObjectTypes.item(o);
        return item == null ? "unknown item" : item.rarity().label();
    }

    /** An item changed: its tier one it has (its lowest), or none. */
    private static void fixTier(LevelObject o) {
        Tier.Row row = Tier.row(o.str("item", ""));
        if (row == null) o.set("tier", null);
        else if (Tier.byKey(o.str("tier", "")) == null || !row.names().containsKey(Tier.byKey(o.str("tier", "")))) {
            o.set("tier", row.lowest().key());
        }
    }

    private static String shownChoice(String key, String option) {
        if (key.equals("item")) {
            var d = ObjectTypes.itemDef(option);
            return d == null ? option : d.name();
        }
        if (key.equals("tier")) {
            Tier t = Tier.byKey(option);
            return t == null ? "none" : t.label();
        }
        return ObjectTypes.readable(option);
    }

    /** Change {@code o} - the template being placed, or a placed object (one undo step, typing included). */
    private void edit(LevelObject o, String label, Runnable change) {
        if (o == template) {
            change.run();
            return;
        }
        String key = o.id() + ":" + label;
        if (!(widgets.typing() && key.equals(propSnapshot))) undo.before(level, Undo.Part.OBJECTS, label);
        propSnapshot = widgets.typing() ? key : null;
        change.run();
        changed();
    }

    // --- colours -----------------------------------------------------------------------------------

    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** Without the palette file: a spread of 128 colours of every hue and lightness. */
    private static int[] fallbackColours() {
        int[] out = new int[128];
        for (int i = 0; i < 128; i++) {
            float hue = (i % 16) / 16f, light = 0.2f + 0.6f * (i / 16) / 7f;
            out[i] = java.awt.Color.HSBtoRGB(hue, i % 16 == 15 ? 0 : 0.55f, light) & 0xFFFFFF;
        }
        return out;
    }

    // --- scripting -----------------------------------------------------------------------------------

    @Override
    public boolean command(String command, String argument) {
        String[] a = argument.isBlank() ? new String[0] : argument.trim().split("\\s+");
        switch (command) {
            case "tool" -> setTool(Tool.valueOf(argument.trim().toUpperCase()));
            case "entry" -> {
                for (int i = 0; i < palette.size(); i++) {
                    ObjectTypes.Entry e = palette.get(i);
                    if (e.label().equalsIgnoreCase(argument.trim()) || argument.trim().equals(e.props().get("sprite"))
                            || argument.trim().equals(e.props().get("item")) || argument.trim().equals(e.type())) {
                        choose(i);
                        return true;
                    }
                }
                return false;
            }
            case "place" -> {
                if (a.length >= 3) template.turnTo(Math.toRadians(Double.parseDouble(a[2])));
                place(new Vec3(Double.parseDouble(a[0]), 0, Double.parseDouble(a[1])));
            }
            case "look" -> {
                focus = new Vec3(Double.parseDouble(a[0]), 0, Double.parseDouble(a[1]));
                if (a.length > 2) camera.setDesiredDistance(Double.parseDouble(a[2]));
                if (a.length > 3) camera.setDesiredPitch(Math.toRadians(Double.parseDouble(a[3])));
                if (a.length > 4) camera.setDesiredYaw(Math.toRadians(Double.parseDouble(a[4])));
                camera.snapTo(focusPoint());
            }
            case "cursor" -> scriptedCursor = a.length < 2 ? null
                    : new Vec3(Double.parseDouble(a[0]), 0, Double.parseDouble(a[1]));
            case "select" -> {
                selection.clear();
                if (a.length >= 2) {
                    double x = Double.parseDouble(a[0]), z = Double.parseDouble(a[1]);
                    LevelObject best = null;
                    for (LevelObject o : level.objects()) {
                        if (best == null || Math.hypot(o.x() - x, o.z() - z) < Math.hypot(best.x() - x, best.z() - z)) {
                            best = o;
                        }
                    }
                    if (best != null) selection.add(best);
                }
                setTool(Tool.SELECT);
            }
            case "delete" -> deleteSelection();
            case "duplicate" -> duplicate();
            case "turn" -> turn(Math.toRadians(Double.parseDouble(argument.trim())));
            case "sculpt" -> {
                // sculpt raise|lower|smooth|flatten x z radius strength [seconds] [height]
                sculpt = Sculpt.valueOf(a[0].toUpperCase());
                radius = Double.parseDouble(a[3]);
                strength = Double.parseDouble(a[4]);
                double seconds = a.length > 5 ? Double.parseDouble(a[5]) : 1;
                if (a.length > 6) flatHeight = Double.parseDouble(a[6]);
                undo.before(level, Undo.Part.HEIGHTS, "Sculpt");
                for (double t = 0; t < seconds; t += 1 / 60.0) {
                    applySculpt(Double.parseDouble(a[1]), Double.parseDouble(a[2]), false, 1 / 60.0);
                }
            }
            case "paint" -> {
                // paint <0 grass .. 4 meadow> x z radius
                undo.before(level, Undo.Part.GROUND, "Paint");
                level.ground().paint(Integer.parseInt(a[0]) - 1, Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                        Double.parseDouble(a[3]), 1);
                dirty = true;
            }
            case "scatter" -> {
                // scatter <set index> x z radius [density]
                Scatter.Set s = sets.get(Integer.parseInt(a[0]));
                double r = Double.parseDouble(a[3]);
                undo.before(level, Undo.Part.OBJECTS, "Scatter");
                for (int i = 0; i < 6; i++) {
                    Scatter.sow(level, view.terrain(), sprites, s, Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                            r, a.length > 4 ? Double.parseDouble(a[4]) : density, Math.PI * r * r * 0.2, random);
                }
                changed();
            }
            case "sky" -> {
                // sky zenith|horizon #RRGGBB, sky fog <m>
                Sky s = level.sky();
                if (a[0].equals("fog")) level.setSky(s.withFog(Double.parseDouble(a[1])));
                else if (a[0].equals("zenith")) level.setSky(s.withZenith(Integer.parseInt(a[1].replace("#", ""), 16)));
                else if (a[0].equals("horizon")) {
                    level.setSky(s.withHorizon(Integer.parseInt(a[1].replace("#", ""), 16)));
                }
                dirty = true;
            }
            case "backdrop" -> {
                // backdrop clear | backdrop <kind> [#colour height haze seed from to]
                Sky s = level.sky();
                if (a[0].equals("clear")) {
                    level.setSky(s.withBackdrops(List.of()));
                    return true;
                }
                Sky.Kind k = Sky.Kind.byKey(a[0]);
                if (k == null) return false;
                Sky.Backdrop b = Sky.Backdrop.of(k);
                if (a.length > 1) b = b.withColour(Integer.parseInt(a[1].replace("#", ""), 16));
                if (a.length > 2) b = k == Sky.Kind.OCEAN ? b.withDistance(Double.parseDouble(a[2]))
                        : b.withHeight(Double.parseDouble(a[2]));
                if (a.length > 3) b = b.withHaze(Double.parseDouble(a[3]));
                if (a.length > 4) b = b.withSeed(Integer.parseInt(a[4]));
                if (a.length > 6) b = b.withArc(Double.parseDouble(a[5]), Double.parseDouble(a[6]));
                List<Sky.Backdrop> next = new ArrayList<>(s.backdrops());
                next.add(b);
                level.setSky(s.withBackdrops(next));
                dirty = true;
            }
            case "name" -> level.setName(argument);
            case "size" -> resize(Double.parseDouble(argument.trim()));
            case "save" -> save();
            case "undo" -> undo();
            case "redo" -> redo();
            case "ui" -> hideUi = argument.trim().equals("off");
            case "fog" -> thinFog = !argument.trim().equals("game");
            case "playtest" -> playTest(a.length >= 2 ? new Vec3(Double.parseDouble(a[0]), 0,
                    Double.parseDouble(a[1])) : null);
            case "leave" -> toMenu();
            default -> {
                return false;
            }
        }
        return true;
    }
}
