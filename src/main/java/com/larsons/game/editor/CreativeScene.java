package com.larsons.game.editor;

import com.larsons.game.ambient.AnimalKind;
import com.larsons.game.ambient.CritterKind;
import com.larsons.game.ambient.Life;
import com.larsons.game.audio.MusicPreview;
import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Songs;
import com.larsons.game.audio.Sounds;
import com.larsons.game.building.Building;
import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.building.BuildingLook;
import com.larsons.game.building.BuildingProfile;
import com.larsons.game.building.Room;
import com.larsons.game.building.Walls;
import com.larsons.game.combat.CombatTuning;
import com.larsons.game.combat.FlyingDummy;
import com.larsons.game.core.Autopilot;
import com.larsons.game.core.Game;
import com.larsons.game.core.Scene;
import com.larsons.game.input.Input;
import com.larsons.game.level.Interiors;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelIO;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.LevelWorld;
import com.larsons.game.level.ObjectType;
import com.larsons.game.level.ObjectType.Category;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.level.Templates;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.scene.DemoScene;
import com.larsons.game.scene.MainMenuScene;
import com.larsons.game.gfx.Batch;
import com.larsons.game.sprite.ItemSprites;
import com.larsons.game.sprite.PixelPalette;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Border;
import com.larsons.game.world.Forged;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Rarity;
import com.larsons.game.world.GroundMap;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Atmosphere;
import com.larsons.game.world.Biome;
import com.larsons.game.world.DayCycle;
import com.larsons.game.world.Sky;
import com.larsons.game.world.Weather;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.Tier;
import com.larsons.game.world.WaterMap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringJoiner;

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
 *   <li><b>Level</b> - its name and size; its music (a song, every song
 *       shuffled, or none), with a player to listen to each song first -
 *       play, pause and a timeline to jump about in it; save, play-test,
 *       leave.</li>
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
        TIME("Time"), LEVEL("Level"), WATER("Water"), LIFE("Life"), FORGE("Forge"), CARDS("Cards");

        final String label;

        Tool(String label) {
            this.label = label;
        }
    }

    /** What the sculpting brush does. */
    enum Sculpt { RAISE, LOWER, SMOOTH, FLATTEN }

    /**
     * The paint brush's materials: grass (paints the others away), then
     * {@link GroundMap}'s twelve, then bare rock (takes the paint off the
     * steep faces, so the rock shows again).
     */
    static final String[] MATERIALS = {"Grass", "Dirt", "Forest floor", "Gravel", "Flower meadow", "Sand", "Mud",
            "Snow", "Stone", "Jungle floor", "Dry grass", "Ash", "Crystal", "Bare rock"};
    /** {@link #MATERIALS}' bare rock. */
    static final int BARE_ROCK = 13;

    /** How far the camera can pull back, metres. */
    static final double FAR = 160;
    /** The grid things snap to, metres; the turn they snap to, degrees. */
    static final double GRID = 0.5, TURN = 15;
    /** The panels' widths and the top bar's height, pixels. */
    static final float LEFT_W = 300, RIGHT_W = 304, TOP_H = 46, HELP_H = 30;
    /** How often a held scatter brush sows, seconds. */
    static final double SOW_EVERY = 0.08;
    /** Hours a second the time runs at when it is run in the editor. */
    static final double PREVIEW_HOURS = 0.75;

    private final Game game;
    private final Level level;
    private Path folder;
    /** Editing the inside of a building: the scene editing the level it stands in (null: editing a level). */
    private final CreativeScene outside;
    /** The building whose inside this is (its object's number), its floors, and which floor this is. */
    private final int insideOf;
    private final List<Level> floors;
    private final int floorIndex;
    /** When the top floor's "remove" was last pressed (it takes two presses). */
    private double removeFloorAt = -10;
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
    /** The game's 128 colours (null: no palette file), the buildings' shades are made from. */
    private final PixelPalette pixelPalette;
    /** Which part of a building the Look section colours. */
    private int lookPart;
    /**
     * The colour picker over everything (null: none): a herd's colours, asked
     * for as it is placed, or a tint - and what it is picking them for.
     */
    private ColourPick colourPick;

    /** The colour picker: {@code target}'s prop {@code key}, one colour or ({@code multi}) a herd's several. */
    private record ColourPick(LevelObject target, String key, boolean multi, String title,
                              List<com.larsons.game.ambient.HerdColours.Preset> presets) {}
    private boolean entered;

    private Tool tool = Tool.PLACE;
    // --- place
    private List<ObjectTypes.Entry> palette;
    /** What the selection is saved as, as a template (the Select tool). */
    private String templateName = "My template";
    private Category category = Category.SCENERY;
    private int paletteScroll;
    private LevelObject template;
    private String templateLabel = "";
    private boolean snap;
    // --- the forge: the weapon being made, and which of its parts (or its dice) the swatches colour
    private Forged forging = Forged.fresh("New weapon");
    private CardsTool cards;

    private CardsTool cardsTool() {
        if (cards == null) cards = new CardsTool(game, widgets);
        return cards;
    }
    private int forgeColour;
    /** The Forge's page: 0 the weapon, 1 its dice and ammunition, 2 its upgrade tiers; and the tier being edited. */
    private int forgePage, forgeTier = 1, tierColour;
    // --- select
    private final List<LevelObject> selection = new ArrayList<>();
    private final List<LevelObject> clipboard = new ArrayList<>();
    private boolean moving, boxing, movedYet;
    /**
     * A plain pointer over the tool chosen (a right-click without a drag):
     * click to pick things and drag them about as the Select tool does,
     * without leaving the tool's tab; right-click again to go back to it.
     */
    private boolean pointer;
    /** Where and when the right button went down, and whether it has dragged (turning the view) since. */
    private double rightX, rightY, rightAt;
    private boolean rightDragged;
    private Vec3 dragFrom;
    private final Map<LevelObject, double[]> origins = new LinkedHashMap<>();
    private float boxX, boxY;
    // --- brushes
    private Sculpt sculpt = Sculpt.RAISE;
    private int material = 1;
    /** The Paint tool's page: 0 the ground's materials, 1 the biomes. */
    private int paintTab;
    private Biome biome = Biome.MARSH;
    private boolean sowBiome = true;
    /** Whether the brushes (sculpt, paint, tint) are square rather than round. */
    private boolean squareBrush;
    /** Whether painting the ground paints over the rock of a face too steep to walk, too. */
    private boolean overRock = true;
    /** The tint brush's colour (-1: it takes the tint off) and how much of it, at most. */
    private int tintColour = 0xC89A50;
    private double tintAmount = 0.6;
    // --- water
    private WaterMap.Kind waterKind = WaterMap.Kind.CLEAR;
    private double waterDepth = 0.6, waterSpeed = 0.8;
    /** Whether the stroke's water lies level (a pond, a lake) or follows the ground down (a stream). */
    private boolean waterLevel = true;
    private boolean waterFlows;
    /** The water tool's way: paint it with the brush (0), or fill the hollow clicked to its brim (1) or to a depth (2). */
    private int waterMode = 1;
    /** The most cells a fill floods (a 512 m level's worth of half-metre cells, a quarter of it). */
    static final int FILL_MOST = 260_000;
    private double strokeSurface, flowX, flowZ;
    private Vec3 lastDab;
    private double radius = 3, strength = 0.5, flatHeight;
    /** How soft the sculpting brush's edge is, and the painting brush's (0 hard – 1 soft from the middle out). */
    private double sculptFeather = 1, paintFeather = 0;
    /** Where the brushes work: slopes, heights, what the ground is, water (World Painter's filters). */
    private final BrushFilter filter = new BrushFilter();
    /** The scatter brush being made or changed (Scatter → Your brushes), or null. */
    private ScatterBrushes.Brush making;
    private String makingName = "";
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
    /** Which part of the backdrop chosen the swatches colour: 0 its first (rock, grass…), 1 its second (snow…). */
    private int backdropPart;
    /** The backdrop row pressed (-1: none), where the press was, and whether it is being dragged up or down. */
    private int dragBackdrop = -1;
    private double dragBackdropY;
    private boolean draggingBackdrop;
    // --- time and weather
    /** Which of the Time tool's pages is shown: 0 the time, 1 the planet, 2 the weather. */
    private int timeTab;
    /** Whether the time runs in the editor (a preview), and the hour it has run to. */
    private boolean runTime;
    private double runHour;
    // --- the pointer, and what it is over
    private Vec3 cursor;
    private LevelObject hover;
    private Vec3 scriptedCursor;
    private boolean hideUi;
    private boolean thinFog = true;
    private double time, leaveAt = -10;
    private String propSnapshot;
    /** The Level tool's song player, and the song it is on. */
    private final MusicPreview preview = new MusicPreview();
    private String listening = "";

    /**
     * Edit {@code level}, saved in {@code folder} (null: not yet saved - the
     * first save makes it a folder of its own in {@code assets/levels/}).
     */
    public CreativeScene(Game game, Level level, Path folder) {
        this(game, level, folder, null, -1, null, 0);
    }

    /**
     * Edit floor {@code floor} of {@code floors}, the inside of building
     * {@code buildingId} of the level {@code outside} edits: saved with that
     * level, Esc back out to it.
     */
    private CreativeScene(Game game, CreativeScene outside, int buildingId, List<Level> floors, int floor) {
        this(game, floors.get(floor), outside.folder, outside, buildingId, floors, floor);
    }

    private CreativeScene(Game game, Level level, Path folder, CreativeScene outside, int insideOf,
                          List<Level> floors, int floorIndex) {
        this.game = game;
        this.level = level;
        this.folder = folder;
        this.outside = outside;
        this.insideOf = insideOf;
        this.floors = floors;
        this.floorIndex = floorIndex;
        this.dirty = outside == null && folder == null;
        this.sprites = game.scenerySprites();
        this.view = new LevelView(level, game.wardrobe(), sprites, CombatTuning.load(game.settings().assets));
        this.palette = ObjectTypes.palette();
        this.sets = Scatter.sets(sprites);
        PixelPalette pp = PixelPalette.load(game.settings().spritesDir());
        this.pixelPalette = pp;
        this.colours = pp == null ? fallbackColours() : concat(pp.fills(), pp.lines());
        camera.setMaxDistance(far(level.size()));
        LevelObject spawn = level.spawn();
        if (spawn != null) focus = new Vec3(spawn.x(), 0, spawn.z());
        if (outside != null) {
            view.indoors(outside.level.sky());
            category = Category.INSIDE;
            camera.setDesiredDistance(15);
            camera.setDesiredYaw(0);
        }
        choose(firstOf(category));
    }

    @Override
    public String name() {
        if (outside != null) {
            return "Creative Mode: " + outside.level.name() + " - inside the " + insideLabel().toLowerCase() + ", "
                    + level.name().toLowerCase() + (outside.dirty ? " *" : "");
        }
        return "Creative Mode: " + level.name() + (dirty ? " *" : "");
    }

    /** What the building whose inside this is is called. */
    private String insideLabel() {
        LevelObject b = outside == null ? null : outside.level.byId(insideOf);
        return b == null ? "Building" : ObjectTypes.label(b);
    }

    @Override
    public void enter() {
        if (gizmos == null) gizmos = new Gizmos(game.batch());
        if (!entered) camera.snapTo(focusPoint());
        entered = true;
        game.window().setTitle(Game.TITLE + " — " + name());
    }

    @Override
    public void exit() {
        preview.close();
    }

    /** The level being edited. */
    public Level level() { return level; }

    /** The Level tool's song player. */
    MusicPreview preview() { return preview; }

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
        preview.update(dt);
        Input in = game.window().input();
        if (game.importer().visible()) return;
        double mx = in.mouseX(), my = in.mouseY();
        boolean overUi = !hideUi && overUi(mx, my);
        boolean ctrl = in.down(GLFW_KEY_LEFT_CONTROL) || in.down(GLFW_KEY_RIGHT_CONTROL);
        boolean shift = in.down(GLFW_KEY_LEFT_SHIFT) || in.down(GLFW_KEY_RIGHT_SHIFT);

        if (!widgets.typing()) keys(in, dt, ctrl, shift);
        if (game.scene() != this) return;               // (a key took us to the play-test or the menu)

        // --- a right-click (not a drag, which turns the view): a plain pointer, or back to the tool
        if (in.mousePressed(GLFW_MOUSE_BUTTON_RIGHT)) {
            rightX = mx;
            rightY = my;
            rightAt = time;
            rightDragged = false;
        }
        if (in.mouseDown(GLFW_MOUSE_BUTTON_RIGHT) && Math.hypot(mx - rightX, my - rightY) > 5) rightDragged = true;
        if (in.mouseReleased(GLFW_MOUSE_BUTTON_RIGHT) && !rightDragged && time - rightAt < 0.45 && !overUi) {
            setPointer(!pointer);
        }

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
        hover = overUi || brushTool() && !pointer ? null : pick(ray);

        // --- the tools
        boolean press = in.mousePressed(GLFW_MOUSE_BUTTON_LEFT) && !overUi;
        boolean held = in.mouseDown(GLFW_MOUSE_BUTTON_LEFT);
        switch (pointer ? Tool.SELECT : tool) {
            case PLACE -> {
                if (press && cursor != null && level.contains(cursor.x(), cursor.z())) place(at(cursor));
            }
            case SELECT -> selectTool(press, held, shift, mx, my);
            case SCULPT -> sculptTool(press, held, shift, ctrl, dt);
            case PAINT -> paintTool(press, held, shift, dt);
            case SCATTER -> scatterTool(press, held, shift, dt);
            case WATER -> waterTool(press, held, shift, dt);
            default -> { }
        }
        if (!held) stroking = false;
        boolean ghost = tool == Tool.PLACE && !pointer && !overUi && cursor != null && template != null
                && level.contains(cursor.x(), cursor.z());
        if (ghost) {
            Vec3 at = at(cursor);
            LevelObject g = template.copy().moveTo(at.x(), at.z());
            // vines show where they will hang: on the face under the pointer
            if (g.type().equals(ObjectTypes.VINES) && g.bool("fit", true)) LevelWorld.fitVines(g, level.heights());
            view.setGhost(g);
        } else {
            view.setGhost(null);
        }

        view.refresh();
        if (thinFog) view.world().setSky(level.sky().withFog(Math.max(level.sky().fog(), camera.distance() * 2.6)));
        if (runTime) runHour = (runHour + dt * PREVIEW_HOURS) % 24;
        view.world().clock().setHour(shownHour());
        view.world().setLife(level.life());
        view.world().setFocus(view.terrain().onGround(focus));   // (the little creatures live round the view)
        view.world().idle(dt);
        camera.update(dt, focusPoint());
        camera.keepAbove(view.terrain(), 0.5);
    }

    /** The keys: the editor's own, then the camera's. */
    private void keys(Input in, double dt, boolean ctrl, boolean shift) {
        if (in.pressed(GLFW_KEY_ESCAPE)) {
            in.consumeKey(GLFW_KEY_ESCAPE);
            if (colourPick != null) colourPick = null;
            else if (!selection.isEmpty()) selection.clear();
            else if (pointer) setPointer(false);
            else leave();
            return;
        }
        if (ctrl && in.pressed(GLFW_KEY_S)) save();
        if (ctrl && (in.pressed(GLFW_KEY_Y) || shift && in.pressed(GLFW_KEY_Z))) redo();
        else if (ctrl && in.pressed(GLFW_KEY_Z)) undo();
        if (in.pressed(GLFW_KEY_F5)) {
            // F5: from the player's spawn (where she starts when the level is played) - Shift+F5: from the pointer
            playTest(shift ? (cursor != null ? cursor : focus) : spawnOrPointer());
            return;
        }
        if (!ctrl) {
            int[] toolKeys = {GLFW_KEY_1, GLFW_KEY_2, GLFW_KEY_3, GLFW_KEY_4, GLFW_KEY_5, GLFW_KEY_6, GLFW_KEY_7,
                    GLFW_KEY_8, GLFW_KEY_9, GLFW_KEY_0};
            List<Tool> shown = tools();
            for (int i = 0; i < Math.min(toolKeys.length, shown.size()); i++) {
                if (in.pressed(toolKeys[i])) setTool(shown.get(i));
            }
            if (outside != null && in.pressed(GLFW_KEY_PAGE_UP) && shift) floor(floorIndex + 1);
            if (outside != null && in.pressed(GLFW_KEY_PAGE_DOWN) && shift) floor(floorIndex - 1);
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

    /** The tools along the top: inside a building, only those that work in there. */
    private List<Tool> tools() {
        return outside == null ? List.of(Tool.values()) : List.of(Tool.PLACE, Tool.SELECT, Tool.TIME);
    }

    private void setTool(Tool t) {
        if (t == tool || !tools().contains(t)) return;
        if (tool == Tool.LEVEL) preview.pause();
        tool = t;
        pointer = false;
        moving = boxing = stroking = false;
        widgets.stopTyping();
    }

    /** Whether a right-click can put a plain pointer over the tool: one that works in the world, not Select's own. */
    private boolean pointerFits() {
        return tool != Tool.SELECT && tool != Tool.FORGE && tool != Tool.CARDS;
    }

    /** A plain pointer over the tool ({@code on}), or back to the tool. */
    private void setPointer(boolean on) {
        if (on && !pointerFits() || on == pointer) return;
        pointer = on;
        moving = boxing = stroking = false;
        if (on) {
            game.toast("Pointer: click to pick, drag to move · right-click to go back to " + tool.label, Theme.HINT);
        } else {
            selection.clear();
            game.toast("Back to " + tool.label, Theme.HINT);
        }
        Sounds.play(SoundKeys.ui("select"));
    }

    /** Whether things are being picked and moved: the Select tool, or the plain pointer over another. */
    private boolean selecting() {
        return tool == Tool.SELECT || pointer;
    }

    /** Whether the right panel shows what is being placed (not, with the pointer, what it picked). */
    private boolean placingShown() {
        return tool == Tool.PLACE && template != null && !(pointer && !selection.isEmpty());
    }

    private boolean brushTool() {
        return tool == Tool.SCULPT || tool == Tool.PAINT || tool == Tool.SCATTER || tool == Tool.WATER;
    }

    private static double clampRadius(double r) {
        return Math.max(0.5, Math.min(40, r));
    }

    /** {@code p} snapped to the grid, if snapping is on. */
    private Vec3 at(Vec3 p) {
        if (!snap) return p;
        return new Vec3(Math.round(p.x() / GRID) * GRID, p.y(), Math.round(p.z() / GRID) * GRID);
    }

    /** How far the camera can pull back over a level {@code size} metres a side: further over a big one. */
    static double far(double size) {
        return Math.max(FAR, size * 0.32);
    }

    private void resize(double size) {
        undo.before(level, Undo.Part.ALL, "Resize");
        level.resize(size);
        camera.setMaxDistance(far(size));
        selection.clear();
        changed();
        game.toast("The level is now " + (int) size + " m a side (Ctrl+Z puts it back)", Theme.HINT);
    }

    private void changed() {
        dirty = true;
        if (outside != null) {
            outside.dirty = true;
            outside.view.objectsChanged();
        }
        view.objectsChanged();
        game.window().setTitle(Game.TITLE + " — " + name());
    }

    // --- placing and selecting ------------------------------------------------------------------

    private LevelObject place(Vec3 at) {
        if (template == null) return null;
        undo.before(level, Undo.Part.OBJECTS, "Place " + templateLabel);
        LevelObject o = template.copy(level.nextId()).moveTo(at.x(), at.z());
        if (o.type().equals(ObjectTypes.PREFAB) || o.type().equals(ObjectTypes.TEMPLATE)) {
            // a set, a template: each of its pieces placed as one of its own, all of them selected to move together
            // (a template's buildings with their floors inside, furnished as they were)
            Templates.Template saved = Templates.byId(o.str("template", ""));
            selection.clear();
            LevelObject first = null;
            List<LevelObject> pieces = ObjectTypes.expand(o, level.nextId());
            for (int i = 0; i < pieces.size(); i++) {
                LevelObject added = level.add(pieces.get(i));
                selection.add(added);
                if (first == null) first = added;
                List<Level> floors = saved == null || outside != null ? null
                        : saved.floors(saved.objects().get(i).id());
                if (floors != null) level.setInterior(added.id(), floors);
            }
            if (first == null) return null;
            o = first;
        } else {
            o = level.add(o);
            fitVines(o, true);
            // a herd: what colours are they? (each of them one of those picked, at random)
            if (o.type().equals(ObjectTypes.ANIMALS)) pickHerdColours(o);
        }
        changed();
        Sounds.play(SoundKeys.ui("place"));
        return o;
    }

    /** The object the ray passes into first, or null. */
    private LevelObject pick(Picking.Ray ray) {
        // a room's floor last: what stands on it (a rug, a chair) is what is meant, however thin
        LevelObject best = null, bestFloor = null;
        double bestT = Double.MAX_VALUE, floorT = Double.MAX_VALUE;
        Terrain t = view.terrain();
        for (LevelObject o : level.objects()) {
            Building b = ObjectTypes.builds(o.type()) ? view.world().building(o.id()) : null;
            double r = ObjectTypes.radius(o, sprites);
            double y0 = t.height(o.x(), o.z()) + o.lift();
            double hit = b != null ? b.rayHit(ray.from(), ray.dir())
                    : Picking.cylinder(ray, o.x(), o.z(), r, y0 - 0.1, y0 + height(o));
            if (hit < 0) continue;
            boolean floor = b != null && b.profile().room() != null
                    && b.profile().room().piece() == Room.Piece.FLOOR;
            if (floor) {
                if (hit < floorT) {
                    floorT = hit;
                    bestFloor = o;
                }
            } else if (hit < bestT) {
                bestT = hit;
                best = o;
            }
        }
        // (but not something far beyond the floor the pointer is on)
        return best != null && (bestFloor == null || bestT < floorT + 1.5) ? best : bestFloor;
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
            if (moving && movedYet && selection.size() == 1) fitVines(selection.get(0), false);
            moving = boxing = false;
        }
    }

    /**
     * Vines {@code o} fitted to the rock face nearest them - facing out of
     * it, at its foot, as long as it runs ({@link LevelWorld#fitVines}) -
     * when they are set to fit (anything else, or vines set not to, as it
     * is); {@code tell}: a toast when there is no face there.
     */
    private boolean fitVines(LevelObject o, boolean tell) {
        if (o == null || !o.type().equals(ObjectTypes.VINES) || !o.bool("fit", true)) return false;
        boolean fitted = LevelWorld.fitVines(o, level.heights());
        if (fitted) changed();
        else if (tell) game.toast("No rock face here: vines hang on ground too steep to walk (Sculpt a cliff)",
                Theme.WARNING);
        return fitted;
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
        if (tool == Tool.PLACE && template != null && !pointer) {
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
        if (tool == Tool.PLACE && template != null && !pointer) {
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
        var hf = level.heights().square(squareBrush).feather(sculptFeather).mask(filter.mask(level));
        switch (sculpt) {
            case RAISE, LOWER -> {
                double sign = (sculpt == Sculpt.RAISE) != inverse ? 1 : -1;
                hf.raise(x, z, radius, sign * strength * 3.0 * dt);
            }
            case SMOOTH -> hf.smooth(x, z, radius, Math.min(1, strength * 8 * dt));
            case FLATTEN -> hf.flatten(x, z, radius, flatHeight, Math.min(1, strength * 6 * dt));
        }
        hf.mask(null).feather(1);
        dirty = true;
    }

    private void paintTool(boolean press, boolean held, boolean shift, double dt) {
        if (cursor == null) return;
        boolean biomes = paintTab == 1, tinting = paintTab == 2, sowing = biomes && sowBiome && !shift;
        if (press) {
            undo.before(level, sowing ? Undo.Part.ALL : Undo.Part.GROUND, biomes ? biome.label()
                    : tinting ? "Tint" : "Paint");
            stroking = true;
            sowTimer = 0;
        }
        if (!stroking || !held) return;
        double amount = Math.min(1, strength * 5 * dt);
        var ground = level.ground().square(squareBrush).feather(paintFeather).mask(filter.mask(level));
        if (tinting) {
            ground.tint(cursor.x(), cursor.z(), radius, amount, shift ? -1 : tintColour, tintAmount);
        } else if (!biomes && material == BARE_ROCK) {
            // bare the rock faces again (Shift: paint over them)
            ground.cover(cursor.x(), cursor.z(), radius, amount, shift);
        } else if (shift || !biomes) {
            ground.paint(shift ? -1 : material - 1, cursor.x(), cursor.z(), radius, amount);
            if (overRock && !shift) ground.cover(cursor.x(), cursor.z(), radius, amount, true);
        } else {
            ground.paint(biome::groundAt, cursor.x(), cursor.z(), radius, amount);
            if (overRock) ground.cover(cursor.x(), cursor.z(), radius, amount, true);
        }
        ground.mask(null).feather(0);
        dirty = true;
        if (sowing) {
            sowTimer -= dt;
            if (sowTimer <= 0) {
                sowTimer = SOW_EVERY;
                sowBiome(biome, cursor.x(), cursor.z(), radius, 1);
            }
        }
        brushTick(dt);
    }

    /** Sow {@code b}'s plants, trees and rocks within {@code r} of {@code (x, z)}, {@code times} brush-dabs' worth. */
    private void sowBiome(Biome b, double x, double z, double r, int times) {
        sowBiome(b, x, z, r, times, null);
    }

    /** {@link #sowBiome(Biome, double, double, double, int)}, only {@code only} of them (null: all). */
    private void sowBiome(Biome b, double x, double z, double r, int times, SceneryProfile.Kind only) {
        boolean any = false;
        double area = Math.PI * r * r;
        SceneryProfile.Kind[] kinds = {SceneryProfile.Kind.TUFT, SceneryProfile.Kind.TREE, SceneryProfile.Kind.ROCK};
        double[] thickness = {0.55, 0.6, 0.6};
        for (int k = 0; k < kinds.length; k++) {
            if (only != null && kinds[k] != only) continue;
            Scatter.Set set = Scatter.biomeSet(sprites, b, kinds[k]);
            if (set.sprites().isEmpty()) continue;
            for (int t = 0; t < times; t++) {
                if (!Scatter.sow(level, view.terrain(), sprites, set, x, z, r, thickness[k], area * 0.2, random,
                        filter.mask(level)).isEmpty()) {
                    any = true;
                }
            }
        }
        // and a few of its foraging bushes among the plants
        Scatter.Set bushes = Scatter.bushSet(sprites, b);
        if ((only == null || only == SceneryProfile.Kind.TUFT) && !bushes.sprites().isEmpty()) {
            for (int t = 0; t < times; t++) {
                if (!Scatter.sow(level, view.terrain(), sprites, bushes, x, z, r, 0.6, area * 0.2, random,
                        filter.mask(level)).isEmpty()) {
                    any = true;
                }
            }
        }
        if (any) changed();
    }

    /**
     * The water brush: water of the kind chosen, its surface level over the
     * whole stroke (a pond) or following the ground under the brush (a
     * stream), flowing the way the brush moves if asked; Shift takes it away.
     */
    private void waterTool(boolean press, boolean held, boolean shift, double dt) {
        if (cursor == null) return;
        if (waterMode != 0 && !shift) {
            if (press) fillHollow(cursor.x(), cursor.z(), waterMode == 1);
            return;
        }
        if (press) {
            undo.before(level, Undo.Part.WATER, shift ? "Dry" : waterKind.label());
            stroking = true;
            strokeSurface = cursor.y() + waterDepth;
            lastDab = cursor;
            flowX = flowZ = 0;
        }
        if (!stroking || !held) return;
        if (shift) {
            level.water().erase(cursor.x(), cursor.z(), radius);
        } else {
            double dx = cursor.x() - lastDab.x(), dz = cursor.z() - lastDab.z(), moved = Math.hypot(dx, dz);
            if (moved > 0.05) {
                // the way the brush goes, smoothed, is the way the water runs
                flowX = flowX * 0.6 + dx / moved * 0.4;
                flowZ = flowZ * 0.6 + dz / moved * 0.4;
                lastDab = cursor;
            }
            double len = Math.hypot(flowX, flowZ), speed = waterFlows && len > 0.2 ? waterSpeed / len : 0;
            double surface = waterLevel ? strokeSurface : cursor.y() + waterDepth;
            level.water().paint(waterKind, surface, cursor.x(), cursor.z(), radius, flowX * speed, flowZ * speed);
        }
        dirty = true;
        brushTick(dt);
    }

    /** What the scatter brush can sow: the game's sets, then the brushes made here (★). */
    private List<Scatter.Set> scatterSets() {
        if (customSets == null) {
            customSets = new ArrayList<>(sets);
            for (ScatterBrushes.Brush b : ScatterBrushes.all()) {
                Scatter.Set st = b.set();
                if (!st.sprites().isEmpty()) customSets.add(st);
            }
        }
        return customSets;
    }

    /** {@link #scatterSets()} as last read (null: read again). */
    private List<Scatter.Set> customSets;

    /**
     * Fill the hollow at {@code (x, z)} with the water chosen: up to its brim
     * (where it would spill over its rim), or {@code toBrim} false the depth
     * set over the ground clicked - every bit of the hollow joined to it that
     * is lower, its shore just where the ground comes up out of it.
     */
    void fillHollow(double x, double z, boolean toBrim) {
        var hf = level.heights();
        WaterMap.Ground ground = hf::height;
        double here = hf.height(x, z);
        double surface = toBrim ? level.water().brim(x, z, ground, 40, FILL_MOST) - 0.04 : here + waterDepth;
        if (Double.isNaN(surface) || surface <= here + 0.05) {
            game.toast("Nothing to fill here: no hollow round it. Sculpt one (3, Lower), or fill to a depth instead",
                    Theme.WARNING);
            return;
        }
        undo.before(level, Undo.Part.WATER, "Fill");
        int n = level.water().fill(waterKind, surface, x, z, ground, FILL_MOST);
        double cell = level.water().cell();
        dirty = true;
        game.toast(String.format(java.util.Locale.ROOT, "Filled %,.0f m² with %s water, %.1f m deep at the "
                + "deepest", n * cell * cell, waterKind.label().toLowerCase(java.util.Locale.ROOT),
                surface - lowestUnder(x, z, surface)), Theme.ACCENT);
        Sounds.play(SoundKeys.ui("brush"));
    }

    /** About how low the ground goes in the hollow at {@code (x, z)} under {@code surface} (its bottom, near enough). */
    private double lowestUnder(double x, double z, double surface) {
        double low = level.heights().height(x, z);
        for (int r = 1; r <= 40; r += 3) {
            for (int a = 0; a < 12; a++) {
                double px = x + Math.cos(a * Math.PI / 6) * r, pz = z + Math.sin(a * Math.PI / 6) * r;
                double g = level.heights().height(px, pz);
                if (g < surface) low = Math.min(low, g);
            }
        }
        return low;
    }

    private void scatterTool(boolean press, boolean held, boolean shift, double dt) {
        if (cursor == null || scatterSets().isEmpty()) return;
        if (press) {
            undo.before(level, Undo.Part.OBJECTS, shift ? "Pull up" : "Scatter");
            stroking = true;
            sowTimer = 0;
        }
        if (!stroking || !held) return;
        sowTimer -= dt;
        if (sowTimer > 0) return;
        sowTimer = SOW_EVERY;
        Scatter.Set s = scatterSets().get(Math.floorMod(set, scatterSets().size()));
        if (shift) {
            if (Scatter.clear(level, s, cursor.x(), cursor.z(), radius) > 0) changed();
        } else {
            double area = Math.PI * radius * radius;
            if (!Scatter.sow(level, view.terrain(), sprites, s, cursor.x(), cursor.z(), radius, density, area * 0.2,
                    random, filter.mask(level)).isEmpty()) {
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
        if (outside != null) {
            boolean ok = outside.save();
            game.window().setTitle(Game.TITLE + " — " + name());
            return ok;
        }
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

    /**
     * Where F5 starts her: at the level's (or this floor's) player spawn - null, which {@link #playTest}
     * reads as the spawn - or, with no spawn placed, under the pointer (or where the camera looks).
     */
    private Vec3 spawnOrPointer() {
        if (level.spawn() != null) return null;
        return cursor != null ? cursor : focus;
    }

    /** Play it: from {@code at} (null: its spawn), facing the way the camera looks - F5 comes back. */
    private void playTest(Vec3 at) {
        widgets.stopTyping();
        view.setGhost(null);
        Sounds.play(SoundKeys.ui("playtest"));
        double heading = camera.yaw();
        if (at != null && !level.contains(at.x(), at.z())) at = null;
        if (outside != null) {
            // the level, she inside this building on this floor
            DemoScene play = new DemoScene(game, outside.level, null, heading, () -> {
                Sounds.play(SoundKeys.ui("playtest"));
                game.switchTo(this);
            });
            play.startInside(insideOf, floorIndex, at, heading);
            game.switchTo(play);
            return;
        }
        game.switchTo(new DemoScene(game, level, at, heading, () -> {
            Sounds.play(SoundKeys.ui("playtest"));
            game.switchTo(this);
        }));
    }

    /** Back to the main menu: with unsaved changes, only when asked twice. */
    private void leave() {
        if (outside != null) {
            goOutside();
            return;
        }
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
        java.util.Set<Integer> lit = new java.util.HashSet<>();
        for (LevelObject o : selection) if (ObjectTypes.builds(o.type())) lit.add(o.id());
        game.worldRenderer().highlightBuildings(lit);
        game.worldRenderer().render(view.world(), camera, fw, fh);
        game.worldRenderer().highlightBuildings(null);
        Mat4 vp = camera.viewProjection(fw / (double) Math.max(1, fh));
        marks(vp);

        Ui ui = game.ui();
        ui.begin();
        widgets.beginFrame(ui);
        labels(ui, vp);
        if (!hideUi) {
            topBar(ui);
            leftPanel(ui);
            rightPanel(ui);
            help(ui);
            if (colourPick != null) colourDialog(ui);
            if (boxing) {
                Input in = ui.input();
                float x0 = (float) Math.min(boxX, in.mouseX()), y0 = (float) Math.min(boxY, in.mouseY());
                float bw = (float) Math.abs(in.mouseX() - boxX), bh = (float) Math.abs(in.mouseY() - boxY);
                ui.rect(x0, y0, bw, bh, Theme.withAlpha(Theme.ACCENT, 0.12f));
                ui.outline(x0, y0, bw, bh, 1, Theme.ACCENT);
            }
            widgets.overlay(ui);
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
                case ObjectTypes.LIGHT -> {
                    gizmos.post(foot, o.num("height", 1.2), ObjectTypes.lightColour(o.str("colour", "")), 0.9f, camera);
                    gizmos.ring(o.x(), o.z(), o.num("radius", 5), ObjectTypes.lightColour(o.str("colour", "")), 0.3f);
                }
                case ObjectTypes.RAYS -> {
                    gizmos.post(foot, Math.min(4, o.num("height", 6)), c, 0.9f, camera);
                    gizmos.ring(o.x(), o.z(), o.num("spread", 1.5), c, 0.6f);
                }
                case ObjectTypes.BUILDING -> {
                    if (!footprint(o, 0, c, 0.35f)) gizmos.post(foot, 1.2, c, 0.9f, camera);
                    Building b = view.world().building(o.id());
                    Vec3 door = b == null ? null : b.door();
                    if (door != null && selection.contains(o)) gizmos.ring(door.x(), door.z(), 0.45, 0x8CF08C, 0.9f);
                }
                case ObjectTypes.STAIRS, ObjectTypes.EXIT -> {
                    Building b = view.world().building(o.id());
                    Vec3 door = b == null ? null : b.door();
                    if (door != null && selection.contains(o)) gizmos.ring(door.x(), door.z(), 0.45, 0x8CF08C, 0.9f);
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
        if (hover != null && !selection.contains(hover) && !footprint(hover, 0.1, 0xFFFFFF, 0.55f)) {
            gizmos.ring(hover.x(), hover.z(), ObjectTypes.radius(hover, sprites) + 0.1, 0xFFFFFF, 0.55f);
        }
        for (LevelObject o : selection) {
            if (!footprint(o, 0.15, 0xFFD25A, 0.95f)) {
                gizmos.ring(o.x(), o.z(), ObjectTypes.radius(o, sprites) + 0.15, 0xFFD25A, 0.95f);
            }
            if (selection.size() == 1 && !o.type().equals(ObjectTypes.SCENERY)) arrow(o, 0xFFD25A, 1.0);
        }
        if (cursor != null && !hideUi || cursor != null && scriptedCursor != null) {
            if (pointer) {
                // a plain pointer: no brush, no ghost
            } else if (brushTool()) {
                int bc = tool == Tool.SCULPT ? 0xFFE08A : tool == Tool.PAINT ? 0x9CE07A
                        : tool == Tool.WATER ? 0x5FA8FF : 0x7FD0FF;
                if (squareBrush && (tool == Tool.SCULPT || tool == Tool.PAINT)) {
                    // the square brush: its four sides
                    double x0 = cursor.x() - radius, x1 = cursor.x() + radius;
                    double z0 = cursor.z() - radius, z1 = cursor.z() + radius;
                    gizmos.line(x0, z0, x1, z0, 0.08, bc, 0.9f);
                    gizmos.line(x1, z0, x1, z1, 0.08, bc, 0.9f);
                    gizmos.line(x1, z1, x0, z1, 0.08, bc, 0.9f);
                    gizmos.line(x0, z1, x0, z0, 0.08, bc, 0.9f);
                } else {
                    gizmos.disc(cursor.x(), cursor.z(), radius, bc, 0.12f);
                    gizmos.ring(cursor.x(), cursor.z(), radius, bc, 0.9f);
                }
            } else if (tool == Tool.PLACE && template != null && level.contains(cursor.x(), cursor.z())) {
                Vec3 at = at(cursor);
                gizmos.ring(at.x(), at.z(), ObjectTypes.radius(template, sprites) + 0.1, 0x8CF08C, 0.85f);
                arrow(template.copy().moveTo(at.x(), at.z()), 0x8CF08C, 1.0);
            }
        }
        gizmos.end();
    }

    /**
     * {@code o}'s footprint outlined on the ground, {@code grow} metres out,
     * if it is a building standing in the view; false when it is not one.
     */
    private boolean footprint(LevelObject o, double grow, int rgb, float alpha) {
        if (!ObjectTypes.builds(o.type())) return false;
        Building b = view.world().building(o.id());
        if (b == null) return false;
        Room.Spec room = b.profile().room();
        List<BuildingProfile.Shape> shapes = room == null ? b.profile().footprint()
                : List.of(new BuildingProfile.Shape(false, 0, 0, room.length(), room.depth()));
        for (BuildingProfile.Shape s : shapes) {
            if (s.circle()) {
                Vec3 c = b.toWorld(s.x(), s.y());
                gizmos.ring(c.x(), c.z(), s.w() + grow, rgb, alpha);
                continue;
            }
            double hw = s.w() / 2 + grow, hd = s.d() / 2 + grow;
            Vec3[] k = {b.toWorld(s.x() - hw, s.y() - hd), b.toWorld(s.x() + hw, s.y() - hd),
                    b.toWorld(s.x() + hw, s.y() + hd), b.toWorld(s.x() - hw, s.y() + hd)};
            for (int i = 0; i < 4; i++) {
                Vec3 a = k[i], e = k[(i + 1) % 4];
                gizmos.line(a.x(), a.z(), e.x(), e.z(), 0.1, rgb, alpha);
            }
        }
        return true;
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
                case ObjectTypes.LIGHT -> "Light";
                case ObjectTypes.RAYS -> "Light rays";
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
        if (colourPick != null || widgets.dropped()) return true;
        if (my < TOP_H || my > ui.height() - HELP_H) return true;
        if (mx < LEFT_W + 8) return true;
        return rightPanelShown() && mx > ui.width() - RIGHT_W - 8;
    }

    private boolean rightPanelShown() {
        return placingShown() || selecting() && !selection.isEmpty() || tool == Tool.FORGE || tool == Tool.CARDS;
    }

    private void topBar(Ui ui) {
        ui.rect(0, 0, ui.width(), TOP_H, Theme.withAlpha(Theme.PANEL, 0.96f));
        ui.rect(0, TOP_H - 1, ui.width(), 1, Theme.PANEL_EDGE);
        float x = 8;
        List<Tool> tools = tools();
        String[] right = {"Undo", "Redo", "Save", "Play (F5)", outside != null ? "Outside" : "Menu"};
        // the buttons' padding: as roomy as the bar has room for (eleven tools and five buttons on a small screen)
        float labels = 0;
        for (int i = 0; i < tools.size(); i++) labels += ui.body.width((i < 10 ? "0 " : "") + tools.get(i).label);
        for (String r : right) labels += ui.body.width(r);
        int buttons = tools.size() + right.length;
        float pad = Math.max(8, Math.min(22, (ui.width() - 16 - 60 - labels) / buttons - 4));
        for (int i = 0; i < tools.size(); i++) {
            String label = (i < 10 ? (i + 1) % 10 + " " : "") + tools.get(i).label;
            float bw = ui.body.width(label) + pad;
            if (ui.button(label, x, 6, bw, TOP_H - 12, tools.get(i) == tool, true)) setTool(tools.get(i));
            x += bw + 4;
        }
        if (outside != null) x = floorButtons(ui, x + 8);
        float rx = ui.width() - 8;
        float[] widths = new float[right.length];
        for (int i = right.length - 1; i >= 0; i--) {
            widths[i] = ui.body.width(right[i]) + pad;
            rx -= widths[i] + 4;
        }
        float nameX = x + 10, nameW = rx - nameX - 6;
        String title = outside != null ? "Inside the " + insideLabel().toLowerCase() + (outside.dirty ? " *" : "")
                : level.name() + (dirty ? " *" : "");
        // the level's name: as large as there is room for, cut short only when even the small print is too long
        com.larsons.game.gfx.Font nf = ui.body.width(title) <= nameW ? ui.body : ui.small;
        String shownTitle = Ui.fit(nf, title, nameW);
        if (!shownTitle.equals(title) && ui.hover(nameX, 0, nameW, TOP_H)) widgets.tip(title, nameX, TOP_H + 2);
        if (nameW > 30) ui.text(nf, shownTitle, nameX, (TOP_H - nf.lineHeight()) / 2, Theme.ACCENT);
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
                    case 3 -> playTest(spawnOrPointer());
                    case 4 -> leave();         // (inside: back outside)
                    default -> { }
                }
            }
            bx += widths[i] + 4;
        }
    }

    /**
     * Inside a building: which floor this is, a floor down and up, a new
     * floor on top (its walls as this one's) and this one taken away (the
     * top floor, pressed twice). Returns where they end.
     */
    private float floorButtons(Ui ui, float x) {
        float h = TOP_H - 12;
        if (ui.button("<", x, 6, 30, h, false, floorIndex > 0)) floor(floorIndex - 1);
        x += 34;
        String name = level.name() + " (" + (floorIndex + 1) + "/" + floors.size() + ")";
        float nw = ui.body.width(name) + 16;
        ui.text(ui.body, name, x + 8, (TOP_H - ui.body.lineHeight()) / 2, Theme.TITLE);
        x += nw;
        if (ui.button(">", x, 6, 30, h, false, floorIndex < floors.size() - 1)) floor(floorIndex + 1);
        x += 34;
        float aw = ui.body.width("+ Floor") + 18;
        if (ui.button("+ Floor", x, 6, aw, h, false, true)) addFloor();
        x += aw + 4;
        float dw = ui.body.width("- Floor") + 18;
        if (ui.button("- Floor", x, 6, dw, h, false, floors.size() > 1 && floorIndex == floors.size() - 1)) {
            removeFloor();
        }
        return x + dw + 4;
    }

    /** Edit the inside of building {@code o}: its floors as the level has them, else as its kind comes. */
    private void editInside(LevelObject o) {
        List<Level> fl = level.interior(o.id());
        if (fl == null || fl.isEmpty()) {
            List<Level> made = Interiors.make(o.str("kind", ""));
            if (made.isEmpty()) made = List.of(Interiors.emptyFloor(0));
            level.setInterior(o.id(), made);
            fl = level.interior(o.id());
            changed();
        }
        view.setGhost(null);
        Sounds.play(SoundKeys.ui("playtest"));
        game.switchTo(new CreativeScene(game, this, o.id(), fl, 0));
    }

    /** Floor {@code n} of this building's inside, the camera where it is. */
    private void floor(int n) {
        if (outside == null || n < 0 || n >= floors.size() || n == floorIndex) return;
        CreativeScene next = new CreativeScene(game, outside, insideOf, floors, n);
        next.focus = focus;
        next.camera.setDesiredYaw(camera.yaw());
        next.camera.setDesiredDistance(camera.distance());
        next.tool = tools().contains(tool) ? tool : Tool.PLACE;
        game.switchTo(next);
    }

    /**
     * A new floor on top: its walls and floors as the top one's, and a
     * stairwell down over each staircase up there - then edit it.
     */
    private void addFloor() {
        Level top = floors.get(floors.size() - 1);
        Level f = Interiors.emptyFloor(floors.size());
        boolean stairs = false;
        for (LevelObject o : top.objects()) {
            boolean shell = o.type().equals(ObjectTypes.WALL) || o.type().equals(ObjectTypes.FLOOR);
            boolean up = o.type().equals(ObjectTypes.STAIRS) && !o.str("way", "up").equals("down");
            if (shell) f.add(o.copy(f.nextId()));
            if (up) {
                LevelObject down = o.copy(f.nextId());
                down.set("way", "down");
                f.add(down);
                stairs = true;
            }
        }
        floors.add(f);
        changed();
        game.toast(stairs ? "Added " + f.name().toLowerCase() + ", reached by the stairs below"
                : "Added " + f.name().toLowerCase() + ": put Stairs up on the floor below to reach it", Theme.HINT);
        floor(floors.size() - 1);
    }

    /** Take away this floor - the top one - when pressed twice. */
    private void removeFloor() {
        if (floors.size() < 2 || floorIndex != floors.size() - 1) return;
        if (time - removeFloorAt > 3) {
            removeFloorAt = time;
            game.toast("Press - Floor again to take away " + level.name().toLowerCase() + " and all on it",
                    Theme.WARNING);
            return;
        }
        floors.remove(floorIndex);
        changed();
        CreativeScene below = new CreativeScene(game, outside, insideOf, floors, floorIndex - 1);
        below.focus = focus;
        game.switchTo(below);
    }

    /** Back out to the level the building stands in, the building selected. */
    private void goOutside() {
        if (outside == null) return;
        widgets.stopTyping();
        Sounds.play(SoundKeys.ui("playtest"));
        LevelObject b = outside.level.byId(insideOf);
        outside.selection.clear();
        if (b != null) outside.selection.add(b);
        outside.view.objectsChanged();
        game.switchTo(outside);
    }

    private void help(Ui ui) {
        String h = pointer ? "Pointer: click to pick · Shift+click: add · drag: move · Del: delete · R / Q / E: turn · "
                + "right-click: back to " + tool.label : switch (tool) {
            case PLACE -> "Click: place · R / Shift+R: turn 15° · Q / E: turn 45° · PgUp / PgDn: lift · G: snap to grid";
            case SELECT -> "Click: select · Shift+click: add · drag: move · drag on the ground: box · Del: delete · "
                    + "Ctrl+D: duplicate · Ctrl+C / V: copy, paste · R / Q / E: turn · F: fly to it";
            case SCULPT -> "Hold the left button: sculpt · Shift: the other way · Ctrl+click: take the height to "
                    + "flatten to · [ ]: brush size (or Shift+wheel) · Shift+[ ]: speed";
            case PAINT -> "Hold the left button: paint · Shift: back to grass · [ ]: brush size (or Shift+wheel) · "
                    + "Shift+[ ]: speed";
            case SCATTER -> "Hold the left button: scatter · Shift: pull up · [ ]: brush size (or Shift+wheel)";
            case WATER -> "Hold the left button: water · Shift: dry it up · [ ]: brush size (or Shift+wheel) · "
                    + "the water lies level from where you press, or follows the ground down";
            case SKY -> "Pick what to colour, then click a swatch · the fog and the backdrops show as you change them";
            case TIME -> "The hour the level starts at, how long a day lasts, the planet, clouds, stars, aurora, mist "
                    + "and light rays · Run the time to watch a day go by";
            case LEVEL -> "Name the level, choose its size, save it · F5: play from the spawn · Shift+F5: from the pointer";
            case LIFE -> "How thick the little creatures and the animals live here, how fast the bushes grow back · "
                    + "Place → Life: a swarm of critters, a herd of animals (tame to hostile), a foraging bush";
            case FORGE -> "Make a weapon of your own: its weapon, name and rarity, its dice and their colour, its "
                    + "effects, its parts' colours, more dice, its ammunition, its upgrade tiers · Save it, then Place it";
            case CARDS -> "Every card of every class: its name, words, rarity, price and effects · a class's first deck "
                    + "and its perk tree · Save writes assets/cards/cards.json";
        };
        String all = "   ·   WASD fly · Space / C up, down · right-drag turn · right-click pointer · wheel zoom · "
                + "Ctrl+Z / Y undo, redo · Ctrl+S save · F5 play · H hide · Esc menu";
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

    /** How far each tool's panel is scrolled down, pixels (the wheel over it; a long panel). */
    private final java.util.EnumMap<Tool, Float> leftScroll = new java.util.EnumMap<>(Tool.class);
    private float rightScroll;
    private Object rightFor;

    /**
     * Start a panel that scrolls: what is drawn kept within it, moved up by
     * how far it is scrolled. Returns the top to draw from.
     */
    private float scrollBegin(Ui ui, float x, float y, float w, float h, float scroll) {
        ui.clip(x + 1, y + 1, w - 2, h - 2);
        ui.measure();
        return y + 10 - scroll;
    }

    /**
     * End it: how far it can scroll now (from how low what was drawn went),
     * the wheel over it scrolling it, a bar at its side to say where it is.
     * Returns how far it is scrolled now.
     */
    private float scrollEnd(Ui ui, float x, float y, float w, float h, float scroll) {
        float foot = ui.lowest() + scroll - (y + 10);
        ui.unclip();
        float most = Math.max(0, foot - (h - 24));
        Input in = ui.input();
        if (most > 0 && in.scroll() != 0 && ui.hover(x, y, w, h) && !widgets.dropped()) {
            scroll -= (float) Math.signum(in.scroll()) * 48;
            in.consumeScroll();
        }
        scroll = Math.max(0, Math.min(most, scroll));
        if (most > 0) {
            float th = h - 8, bar = Math.max(24, th * (h / (h + most)));
            float by = y + 4 + (th - bar) * (scroll / most);
            ui.rect(x + w - 6, y + 4, 3, th, Theme.withAlpha(Theme.BUTTON, 0.8f));
            ui.rect(x + w - 6, by, 3, bar, Theme.ACCENT);
        }
        return scroll;
    }

    private void leftPanel(Ui ui) {
        float x = 8, y = TOP_H + 8, w = LEFT_W - 8, h = ui.height() - TOP_H - HELP_H - 16;
        ui.panel(x, y, w, h);
        float scroll = leftScroll.getOrDefault(tool, 0f);
        float px = x + 12, pw = w - 24, py = scrollBegin(ui, x, y, w, h, scroll);
        Tool drawn = tool;
        leftPanelContent(ui, px, py, pw, y + h - scroll);
        leftScroll.put(drawn, scrollEnd(ui, x, y, w, h, scroll));
    }

    private void leftPanelContent(Ui ui, float px, float py, float pw, float bottom) {
        switch (tool) {
            case PLACE -> placePanel(ui, px, py, pw, bottom);
            case SELECT -> selectPanel(ui, px, py, pw);
            case SCULPT -> sculptPanel(ui, px, py, pw);
            case PAINT -> paintPanel(ui, px, py, pw);
            case SCATTER -> scatterPanel(ui, px, py, pw);
            case WATER -> waterPanel(ui, px, py, pw);
            case SKY -> skyPanel(ui, px, py, pw);
            case TIME -> timePanel(ui, px, py, pw);
            case LEVEL -> levelPanel(ui, px, py, pw);
            case LIFE -> lifePanel(ui, px, py, pw);
            case FORGE -> forgePanel(ui, px, py, pw);
            case CARDS -> cardsTool().panel(ui, px, py, pw);
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
            String label = Ui.fit(ui.small, e.label(), w - 16);
            ui.text(ui.small, label, x + 8, ry + (row - 2 - ui.small.lineHeight()) / 2,
                    on ? Theme.ITEM_SELECTED : Theme.ITEM);
            if (hot && !label.equals(e.label())) widgets.tip(e.label(), x + w + 8, ry);
            if (ui.clicked(x, ry, w, row - 2)) choose(i);
        }
        if (entries.size() > rows) {
            ui.text(ui.small, (paletteScroll + 1) + "–" + Math.min(entries.size(), paletteScroll + rows) + " of "
                    + entries.size() + " (wheel)", x, y + rows * row + 4, Theme.HINT);
        }
        float ty = bottom - 44;
        if (category == Category.TEMPLATES && template != null && template.type().equals(ObjectTypes.TEMPLATE)) {
            if (ui.button("Delete this template", x, ty - 38, w, 30)) deleteTemplate();
        }
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
        // keep what is selected to place again, here or in any level: Place -> Templates
        ui.text(ui.small, "Save as a template", x, y, Theme.HINT);
        y += 22;
        templateName = widgets.text(ui, "template-name", "", templateName, x, y, w);
        y += 34;
        if (ui.button("Save template", x, y, w, 30, false, !selection.isEmpty())) saveTemplate();
        y += 46;
        snap = widgets.toggle(ui, "Snap (G)", snap, x, y, w);
        y += 40;
        ui.text(ui.small, things(level.objects().size()) + " in the level", x, y, Theme.ITEM_DISABLED);
    }

    /** Save what is selected as a template called {@link #templateName} (Place -> Templates from now on). */
    private void saveTemplate() {
        if (selection.isEmpty()) return;
        try {
            Templates.Template t = Templates.save(templateName, selection, level);
            palette = ObjectTypes.palette();
            game.toast("Saved the template \"" + t.name() + "\" (" + things(t.objects().size())
                    + ") - it is in Place → Templates", Theme.HINT);
            Sounds.play(SoundKeys.ui("place"));
        } catch (java.io.IOException e) {
            game.toast("Could not save the template: " + e.getMessage(), Theme.WARNING);
        }
    }

    /** Throw away the template being placed. */
    private void deleteTemplate() {
        if (template == null || !template.type().equals(ObjectTypes.TEMPLATE)) return;
        try {
            Templates.delete(template.str("template", ""));
            palette = ObjectTypes.palette();
            game.toast("Deleted the template \"" + templateLabel + "\"", Theme.HINT);
            Sounds.play(SoundKeys.ui("remove"));
            List<Integer> left = entriesOf(Category.TEMPLATES);
            choose(left.isEmpty() ? firstOf(Category.SCENERY) : left.get(0));
        } catch (java.io.IOException e) {
            game.toast("Could not delete it: " + e.getMessage(), Theme.WARNING);
        }
    }

    // --- the forge ---------------------------------------------------------------------------

    /**
     * The Forge: a weapon of Larson's own - one of the weapons, named and
     * given a rarity, its dice (how many, d4 to d20 or d%) in a colour of
     * their own, any of the three effects (Venom, Mystic, Bonded - together if
     * wanted) and each part in a colour of the palette. Saved in {@code
     * assets/forge/} for every level, and placed from Place → Items.
     */
    private void forgePanel(Ui ui, float x, float y, float w) {
        y = heading(ui, "Forge a weapon", x, y);
        List<Forged> all = Forged.all();
        List<String> names = new ArrayList<>();
        names.add("A new weapon");
        int at = 0;
        for (int i = 0; i < all.size(); i++) {
            names.add(all.get(i).name());
            if (all.get(i).id().equals(forging.id())) at = i + 1;
        }
        int pick = widgets.choice(ui, "Working on", names, at, x, y, w, 0.36f);
        if (pick != at) {
            forging = pick == 0 ? Forged.fresh("New weapon") : all.get(pick - 1);
            forgeColour = 0;
        }
        y += 36;
        String[] pages = {"Weapon", "Dice & ammo", "Tiers (" + forging.tiers() + ")"};
        float pw = (w - 8) / 3;
        for (int i = 0; i < pages.length; i++) {
            if (ui.button(pages[i], x + i * (pw + 4), y, pw, 28, forgePage == i, true)) forgePage = i;
        }
        y += 38;
        if (forgePage == 1) {
            forgeDice(ui, x, y, w);
            return;
        }
        if (forgePage == 2) {
            forgeTiers(ui, x, y, w);
            return;
        }
        String name = widgets.text(ui, "forge-name", "Name", forging.name(), x, y, w);
        if (!name.strip().equals(forging.name()) && !name.isBlank()) forging = forging.withName(name);
        y += 36;
        List<String> bases = new ArrayList<>();
        int bi = 0;
        for (int i = 0; i < ItemDef.ALL.size(); i++) {
            bases.add(ItemDef.ALL.get(i).name());
            if (ItemDef.ALL.get(i).id().equals(forging.base())) bi = i;
        }
        int b = widgets.choice(ui, "Weapon", bases, bi, x, y, w, 0.36f);
        if (b != bi) {
            forging = forging.withBase(ItemDef.ALL.get(b).id());
            forgeColour = 0;
        }
        y += 36;
        List<String> rarities = new ArrayList<>();
        for (Rarity r : Rarity.values()) rarities.add(r.label());
        int ri = widgets.choice(ui, "Rarity", rarities, forging.rarity().ordinal(), x, y, w, 0.36f);
        if (ri != forging.rarity().ordinal()) forging = forging.withRarity(Rarity.values()[ri]);
        y += 40;

        // the dice: how many, and of what
        int n = (int) Math.round(widgets.stepper(ui, "Dice", forging.dice(), 1, 1, Forged.MAX_DICE, "%.0f", x, y, w));
        if (n != forging.dice()) forging = forging.withDice(n);
        y += 34;
        int kinds = Forged.SIDES.length;
        float dw = (w - (kinds - 1) * 3) / kinds;
        for (int i = 0; i < kinds; i++) {
            int sides = Forged.SIDES[i];
            if (ui.button(Forged.die(sides), x + i * (dw + 3), y, dw, 28, forging.sides() == sides, true)) {
                forging = forging.withSides(sides);
            }
        }
        y += 38;

        // its effects: any of the three, all three if wanted
        Border[] borders = Border.values();
        float ew = (w - 8) / 3;
        for (int i = 0; i < borders.length; i++) {
            Border e = borders[i];
            boolean on = forging.effects().contains(e);
            float ex = x + i * (ew + 4);
            if (ui.button(e.label(), ex, y, ew, 28, on, true)) forging = forging.withEffect(e, !on);
            ui.rect(ex + 4, y + 24, ew - 8, 3, e.colour(on ? 1f : 0.35f));
        }
        y += 38;

        // the colours: of a part (or all of it), or of the dice
        List<String> parts = game.items().parts(forging.base());
        List<String> targets = new ArrayList<>();
        for (String p : parts) targets.add(p.equals("all") ? "All of it" : ObjectTypes.readable(p));
        targets.add("The dice");
        forgeColour = Math.floorMod(forgeColour, targets.size());
        forgeColour = widgets.choice(ui, "Colour", targets, forgeColour, x, y, w, 0.36f);
        y += 34;
        boolean dice = forgeColour == targets.size() - 1;
        String part = dice ? null : parts.get(forgeColour);
        int current = dice ? forging.diceRgb() : forging.tints().getOrDefault(part, -1);
        int cols = 16;
        float size = Math.min(16, w / cols);
        int picked = widgets.swatches(ui, colours, current, x, y, cols, size);
        if (picked >= 0) forging = dice ? forging.withDiceRgb(picked) : forging.withTint(part, picked);
        y += Widgets.swatchesHeight(colours.length, cols, size) + 6;
        if (ui.button(dice ? "The rarity's colour" : "As it is drawn", x, y, w, 26, false, current >= 0)) {
            forging = dice ? forging.withDiceRgb(-1) : forging.withTint(part, -1);
        }
        y += 34;

        forgeButtons(ui, x, y, w);
    }

    /** The Forge's Save, Place it and Delete buttons, at {@code y}. */
    private void forgeButtons(Ui ui, float x, float y, float w) {
        float bw = (w - 8) / 3;
        if (ui.button("Save", x, y, bw, 30)) saveForged(false);
        if (ui.button("Place it", x + bw + 4, y, bw, 30)) saveForged(true);
        if (ui.button("Delete", x + 2 * (bw + 4), y, bw, 30, false, forging.id() != null)) deleteForged();
    }

    /**
     * The Forge's second page: more dice after its own - each group so many
     * of a kind, adding, taking away, multiplying, dividing or scaling by a
     * percent what came before (the dice are worked out left to right) - and
     * how many swings, shots or spells one of its ammunition lasts.
     */
    private void forgeDice(Ui ui, float x, float y, float w) {
        ui.text(ui.small, "Rolls " + forging.diceText(), x, y, Theme.ACCENT);
        y += 24;
        ui.paragraph(ui.small, "More dice, worked out left to right after its own: + adds, − takes away, × multiplies, "
                + "÷ divides, % adds that percent of the total so far.", x, y, w, Theme.ITEM_DISABLED);
        y += 44;
        List<com.larsons.game.combat.DiceRoll.Group> groups = new ArrayList<>(forging.extra());
        int remove = -1;
        for (int g = 0; g < groups.size(); g++) {
            com.larsons.game.combat.DiceRoll.Group gr = groups.get(g);
            if (ui.button(gr.op().symbol() + " " + gr.op().word(), x, y, 96, 28)) {
                groups.set(g, new com.larsons.game.combat.DiceRoll.Group(gr.count(), gr.sides(), gr.op().next()));
            }
            if (ui.button("-", x + 102, y, 26, 28)) {
                groups.set(g, new com.larsons.game.combat.DiceRoll.Group(gr.count() - 1, gr.sides(), gr.op()));
            }
            ui.textCentered(ui.small, String.valueOf(gr.count()), x + 145, y + 6, Theme.TITLE);
            if (ui.button("+", x + 162, y, 26, 28)) {
                groups.set(g, new com.larsons.game.combat.DiceRoll.Group(gr.count() + 1, gr.sides(), gr.op()));
            }
            int si = 0;
            for (int i = 0; i < Forged.SIDES.length; i++) if (Forged.SIDES[i] == gr.sides()) si = i;
            if (ui.button(Forged.die(gr.sides()), x + 194, y, w - 194 - 34, 28)) {
                groups.set(g, new com.larsons.game.combat.DiceRoll.Group(gr.count(),
                        Forged.SIDES[(si + 1) % Forged.SIDES.length], gr.op()));
            }
            if (ui.button("x", x + w - 28, y, 28, 28)) remove = g;
            y += 34;
        }
        if (remove >= 0) groups.remove(remove);
        if (groups.size() < 4 && ui.button("Add dice", x, y, w, 28)) {
            groups.add(new com.larsons.game.combat.DiceRoll.Group(1, 6, com.larsons.game.combat.DiceRoll.Op.ADD));
        }
        if (!groups.equals(forging.extra())) forging = forging.withExtra(groups);
        y += 44;
        int every = (int) Math.round(widgets.stepper(ui, "Ammo lasts", forging.at(0).ammoEvery(), 1, 1, 200, "%.0f uses",
                x, y, w));
        if (every != forging.at(0).ammoEvery()) forging = forging.withAmmoEvery(every);
        y += 30;
        ItemDef ammo = com.larsons.game.world.Ammo.of(forging.def());
        if (ammo != null) {
            ui.paragraph(ui.small, "One " + ammo.name().toLowerCase(java.util.Locale.ROOT) + " is used up every "
                    + every + (every == 1 ? " use" : " uses") + " (each tier can change it).", x, y, w,
                    Theme.ITEM_DISABLED);
        }
        y += 50;
        forgeButtons(ui, x, y, w);
    }

    /**
     * The Forge's third page: its upgrade tiers, 1 (as forged) to 10 - each
     * after the first with its own name, rarity, dice, colours, ammunition
     * and price in upgrade ingots, and whether it is a mystery (???) in the
     * inventory until it is reached.
     */
    private void forgeTiers(Ui ui, float x, float y, float w) {
        List<Forged.Upgrade> ups = new ArrayList<>(forging.upgrades());
        int n = (int) Math.round(widgets.stepper(ui, "Tiers", ups.size() + 1, 1, 1, Forged.MAX_TIERS, "%.0f", x, y, w));
        while (ups.size() + 1 < n) {
            // a tier more: the one before it, a rarity rarer, a die more, a little dearer
            Forged last = forging.at(ups.size());
            Rarity r = Rarity.values()[Math.min(Rarity.values().length - 1, last.rarity().ordinal() + 1)];
            ups.add(new Forged.Upgrade("", r, Math.min(Forged.MAX_DICE, last.dice() + 1), last.sides(), last.extra(),
                    last.diceRgb(), Map.of(), last.ammoEvery() + com.larsons.game.world.Ammo.EVERY_STEP,
                    com.larsons.game.world.Upgrades.cost(r), false));
        }
        while (ups.size() + 1 > n) ups.remove(ups.size() - 1);
        if (!ups.equals(forging.upgrades())) forging = forging.withUpgrades(ups);
        y += 36;
        if (ups.isEmpty()) {
            ui.paragraph(ui.small, "One tier: it stays as forged. Add tiers to let it be upgraded with upgrade ingots "
                    + "(right-click it in the inventory to see them all).", x, y, w, Theme.ITEM_DISABLED);
            forgeButtons(ui, x, y + 60, w);
            return;
        }
        forgeTier = Math.max(1, Math.min(ups.size(), forgeTier));
        float tw = Math.min(40, (w - (ups.size() - 1) * 3) / ups.size());
        for (int i = 1; i <= ups.size(); i++) {
            if (ui.button(String.valueOf(i + 1), x + (i - 1) * (tw + 3), y, tw, 26, forgeTier == i, true)) forgeTier = i;
        }
        y += 34;
        Forged.Upgrade u = ups.get(forgeTier - 1);
        Forged.Upgrade was = u;
        String tname = widgets.text(ui, "tier-name-" + forgeTier, "Name", u.name().isEmpty()
                ? forging.baseName() + " +" + forgeTier : u.name(), x, y, w);
        if (!tname.strip().equals(forging.baseName() + " +" + forgeTier) && !tname.strip().equals(u.name())) {
            u = u.withName(tname);
        }
        y += 34;
        List<String> rarities = new ArrayList<>();
        for (Rarity r : Rarity.values()) rarities.add(r.label());
        int ri = widgets.choice(ui, "Rarity", rarities, u.rarity().ordinal(), x, y, w, 0.36f);
        if (ri != u.rarity().ordinal()) u = u.withRarity(Rarity.values()[ri]);
        y += 34;
        int dn = (int) Math.round(widgets.stepper(ui, "Dice", u.dice(), 1, 1, Forged.MAX_DICE, "%.0f", x, y, w));
        if (dn != u.dice()) u = u.withDice(dn);
        y += 32;
        int kinds = Forged.SIDES.length;
        float dw = (w - (kinds - 1) * 3) / kinds;
        for (int i = 0; i < kinds; i++) {
            if (ui.button(Forged.die(Forged.SIDES[i]), x + i * (dw + 3), y, dw, 26, u.sides() == Forged.SIDES[i], true)) {
                u = u.withSides(Forged.SIDES[i]);
            }
        }
        y += 32;
        int cost = (int) Math.round(widgets.stepper(ui, "Costs", u.cost(), 1, 0, 99, "%.0f ingots", x, y, w));
        if (cost != u.cost()) u = u.withCost(cost);
        y += 32;
        int every = (int) Math.round(widgets.stepper(ui, "Ammo lasts", u.ammoEvery(), 1, 1, 200, "%.0f uses", x, y, w));
        if (every != u.ammoEvery()) u = u.withAmmoEvery(every);
        y += 32;
        if (widgets.toggle(ui, "Mystery (???)", u.mystery(), x, y, w)) u = u.withMystery(!u.mystery());
        y += 34;
        // its colours: a part (or all of it) recoloured as it reaches this tier, or its dice
        List<String> parts = game.items().parts(forging.base());
        List<String> targets = new ArrayList<>();
        for (String p : parts) targets.add(p.equals("all") ? "All of it" : ObjectTypes.readable(p));
        targets.add("The dice");
        tierColour = Math.floorMod(tierColour, targets.size());
        tierColour = widgets.choice(ui, "Colour", targets, tierColour, x, y, w, 0.36f);
        y += 32;
        boolean dice = tierColour == targets.size() - 1;
        String part = dice ? null : parts.get(tierColour);
        int current = dice ? u.diceRgb() : u.tints().getOrDefault(part, -1);
        int cols = 16;
        float size = Math.min(14, w / cols);
        int picked = widgets.swatches(ui, colours, current, x, y, cols, size);
        if (picked >= 0) u = dice ? u.withDiceRgb(picked) : u.withTint(part, picked);
        y += Widgets.swatchesHeight(colours.length, cols, size) + 4;
        if (ui.button(dice ? "Its rarity's colour" : "As before", x, y, w, 24, false, current >= 0)) {
            u = dice ? u.withDiceRgb(-1) : u.withTint(part, -1);
        }
        y += 32;
        if (!u.equals(was)) {
            ups.set(forgeTier - 1, u);
            forging = forging.withUpgrades(ups);
        }
        forgeButtons(ui, x, y, w);
    }

    /** Keep the weapon being forged; {@code place}: and take up placing it. */
    private void saveForged(boolean place) {
        try {
            forging = Forged.save(forging);
            palette = ObjectTypes.palette();
            Sounds.play(SoundKeys.ui("place"));
            if (place) {
                setTool(Tool.PLACE);
                for (int i = 0; i < palette.size(); i++) {
                    if (forging.key().equals(palette.get(i).props().get("item"))) choose(i);
                }
                game.toast("Placing " + forging.name() + " - click where it lies", Theme.HINT);
            } else {
                game.toast("Saved " + forging.name() + " - it is in Place → Items", Theme.HINT);
            }
        } catch (java.io.IOException e) {
            game.toast("Could not save it: " + e.getMessage(), Theme.WARNING);
        }
    }

    /** Throw away the weapon being forged (one already placed in a level lies there no more). */
    private void deleteForged() {
        if (forging.id() == null) return;
        try {
            Forged.delete(forging.id());
            palette = ObjectTypes.palette();
            game.toast("Deleted " + forging.name(), Theme.HINT);
            Sounds.play(SoundKeys.ui("remove"));
            forging = Forged.fresh("New weapon");
        } catch (java.io.IOException e) {
            game.toast("Could not delete it: " + e.getMessage(), Theme.WARNING);
        }
    }

    /** The weapon being forged as it will look: turning, its name and rarity, its dice as they come up, its effects. */
    private void forgePreview(Ui ui, float x, float y, float w) {
        Forged f = forging;
        y = heading(ui, "As it will be", x, y);
        ItemSprites items = game.items();
        ItemSprites.Spin spin = items.spin(f.base());
        ItemSprites.Icon icon = spin == null ? null : items.icon(f.base(), f.tints());
        float s = w * 0.86f;
        if (icon != null) {
            int frame = (int) Math.floor(view.world().time() / 6.0 * icon.frames());
            float[] uv = icon.uv(frame);
            float ix = x + (w - s) / 2, iy = y;
            Batch b = ui.batch();
            float[] rc = f.rarity().colour(1f);
            b.glowRect(icon.halo(), ix, iy, s, s, uv[0], uv[1], uv[2], uv[3], rc[0], rc[1], rc[2], 0.55f);
            if (!f.effects().isEmpty()) {
                float[] bc = f.effects().iterator().next().colour(1f);
                b.rect(icon.ring(), ix, iy, s, s, uv[0], uv[1], uv[2], uv[3], bc[0], bc[1], bc[2], 0.9f);
            }
            b.rect(icon.sprite(), ix, iy, s, s, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
        }
        y += s + 6;
        ui.text(ui.body, f.name(), x, y, f.rarity().colour(1f));
        y += 28;
        ItemDef base = f.def();
        ui.text(ui.small, f.rarity().label() + " - forged " + base.name().toLowerCase(java.util.Locale.ROOT)
                + " - rolls " + f.diceText(), x, y, Theme.ITEM);
        y += 22;
        for (Border e : f.effects()) {
            y += ui.paragraph(ui.small, e.label() + ": " + e.effect(), x, y, w, e.colour(1f)) + 2;
        }
        y += 8;
        // its dice, come up highest (a d% as its tens and its ones), and any more doing their sums
        com.larsons.game.combat.DiceRoll roll = highest(f);
        int[] pal = com.larsons.game.combat.CombatColours.diePalette(f.rarity(),
                f.effects().isEmpty() ? null : f.effects().iterator().next(), f.diceRgb());
        int across = 0;
        for (int i = 0; i < roll.count(); i++) across += roll.sidesOf(i) == 100 ? 2 : 1;
        float size = Math.min(48, (w - 12) / Math.max(4, Math.min(12, across + 1)));
        com.larsons.game.combat.CombatHud.landed(ui, game.worldRenderer().combat().fx(), roll, pal,
                x + w / 2, y, size, ui.scale());
    }

    /** {@code f}'s dice come up as high as they go (its first six, and each more group's first three). */
    static com.larsons.game.combat.DiceRoll highest(Forged f) {
        List<Integer> faces = new ArrayList<>(), sides = new ArrayList<>();
        List<com.larsons.game.combat.DiceRoll.Op> ops = new ArrayList<>();
        for (int i = 0; i < Math.min(f.dice(), 6); i++) {
            faces.add(Math.max(1, f.sides() - i));
            sides.add(f.sides());
            ops.add(com.larsons.game.combat.DiceRoll.Op.ADD);
        }
        for (com.larsons.game.combat.DiceRoll.Group g : f.extra()) {
            for (int i = 0; i < Math.min(g.count(), 3); i++) {
                faces.add(Math.max(1, g.sides() - i));
                sides.add(g.sides());
                ops.add(g.op());
            }
        }
        int[] fa = new int[faces.size()], si = new int[faces.size()];
        for (int i = 0; i < fa.length; i++) {
            fa[i] = faces.get(i);
            si[i] = sides.get(i);
        }
        return new com.larsons.game.combat.DiceRoll(f.sides(), fa, si,
                ops.toArray(new com.larsons.game.combat.DiceRoll.Op[0]));
    }

    /**
     * The brush's size, its speed (how fast it works), how soft its edge is
     * (feather), and its shape - then the filters, where it works. Returns
     * the foot.
     */
    private float brushSliders(Ui ui, float x, float y, float w, boolean sculpting) {
        radius = widgets.slider(ui, "radius", "Size (m)", radius, 0.5, 40, "%.1f", x, y, w);
        strength = widgets.slider(ui, "strength", "Speed", strength, 0.05, 1, "%.2f", x, y + 40, w);
        if (sculpting) {
            sculptFeather = widgets.slider(ui, "feather", "Feather", sculptFeather, 0, 1, "%.2f", x, y + 80, w);
        } else {
            paintFeather = widgets.slider(ui, "feather-paint", "Feather", paintFeather, 0, 1, "%.2f", x, y + 80, w);
        }
        // the brush's shape: a disc, or a square (a plateau with sides, a square pit, a square of paving)
        float bw = (w - 6) / 2;
        if (ui.button("Round brush", x, y + 112, bw, 24, !squareBrush, true)) squareBrush = false;
        if (ui.button("Square brush", x + bw + 6, y + 112, bw, 24, squareBrush, true)) squareBrush = true;
        y += 146;
        ui.paragraph(ui.small, "Speed: how fast it works. Feather: how soft its edge is (0 a hard edge, 1 soft "
                + "from its middle).", x, y, w, Theme.ITEM_DISABLED);
        y += ui.paragraphHeight(ui.small, "Speed: how fast it works. Feather: how soft its edge is (0 a hard edge, "
                + "1 soft from its middle).", w) + 6;
        return filter.panel(ui, widgets, x, y, w, MATERIALS, BARE_ROCK) + 6;
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
        y = brushSliders(ui, x, y + 10, w, true);
        flatHeight = widgets.stepper(ui, "Flatten to (m)", flatHeight, 0.25, -Terrain.STEP * 200, 100, "%.2f",
                x, y, w);
        y += 44;
        ui.paragraph(ui.small, "Flatten brings the ground to that height; Ctrl+click takes it from the ground. "
                + "Steeper than 45° is rock she cannot walk up - put vines on it (Place, Terrain) to climb it. "
                + "The edge of the level stays at 0, where it meets the ground beyond.", x, y, w, Theme.HINT);
    }

    private void paintPanel(Ui ui, float x, float y, float w) {
        y = heading(ui, "Paint the ground", x, y);
        String[] tabs = {"Ground", "Biome", "Tint"};
        float tw = (w - 8) / 3, bw = (w - 6) / 2;
        for (int i = 0; i < tabs.length; i++) {
            if (ui.button(tabs[i], x + i * (tw + 4), y, tw, 28, paintTab == i, true)) paintTab = i;
        }
        y += 38;
        if (paintTab == 0) {
            for (int i = 0; i < MATERIALS.length; i++) {
                if (ui.button(MATERIALS[i], x + (i % 2) * (bw + 6), y + (i / 2) * 34, bw, 30, material == i, true)) {
                    material = i;
                }
            }
            y += (MATERIALS.length + 1) / 2 * 34;
            overRock = widgets.toggle(ui, "Paint over rock faces", overRock, x, y, w);
            y += 36;
            y = brushSliders(ui, x, y + 10, w, false);
            ui.paragraph(ui.small, "Grass paints the others away; Shift does the same with whatever is chosen. "
                    + "Over rock faces: the paint shows on ground too steep to walk, instead of the rock "
                    + "(Bare rock takes it off again).", x, y, w, Theme.HINT);
            return;
        }
        if (paintTab == 2) {
            ui.paragraph(ui.small, "Tint the ground any colour - autumn's gold, a blighted grey - keeping its "
                    + "pattern. Shift takes the tint off.", x, y, w, Theme.HINT);
            y += 40;
            int cols = 16;
            float size = Math.min(16, w / cols);
            int picked = widgets.swatches(ui, colours, tintColour, x, y, cols, size);
            if (picked >= 0) tintColour = picked;
            y += Widgets.swatchesHeight(colours.length, cols, size) + 8;
            tintAmount = widgets.slider(ui, "tint-amount", "How much", tintAmount, 0.05, 1, "%.2f", x, y + 10, w);
            y += 44;
            brushSliders(ui, x, y + 10, w, false);
            return;
        }
        Biome[] all = Biome.values();
        for (int i = 0; i < all.length; i++) {
            if (ui.button(all[i].label(), x + (i % 2) * (bw + 6), y + (i / 2) * 34, bw, 30, biome == all[i], true)) {
                biome = all[i];
            }
        }
        y += (all.length + 1) / 2 * 34 + 4;
        sowBiome = widgets.toggle(ui, "Sow its plants", sowBiome, x, y, w);
        y += 40;
        y = brushSliders(ui, x, y + 10, w, false);
        ui.paragraph(ui.small, biomeHint(biome) + " Shift paints back to grass (and sows nothing).", x, y, w,
                Theme.HINT);
    }

    /** What the biome brush lays down for {@code b}, in a sentence. */
    private String biomeHint(Biome b) {
        List<String> grows = new ArrayList<>();
        for (SceneryProfile.Kind k : new SceneryProfile.Kind[] {SceneryProfile.Kind.TUFT, SceneryProfile.Kind.TREE,
                SceneryProfile.Kind.ROCK}) {
            for (String n : Scatter.biomeSet(sprites, b, k).sprites()) {
                if (grows.size() < 6) grows.add(ObjectTypes.readable(n).toLowerCase(java.util.Locale.ROOT));
            }
        }
        String ground = b.ground() < 0 ? "grass" : MATERIALS[b.ground() + 1].toLowerCase(java.util.Locale.ROOT);
        String accent = MATERIALS[b.accent() + 1].toLowerCase(java.util.Locale.ROOT);
        return "Paints " + ground + " with patches of " + accent
                + (grows.isEmpty() ? "." : "; sows " + String.join(", ", grows) + ".")
                + " Its water: " + b.water().label().toLowerCase(java.util.Locale.ROOT) + " (the Water tool, 9).";
    }

    private void waterPanel(Ui ui, float x, float y, float w) {
        y = heading(ui, "Water", x, y);
        WaterMap.Kind[] kinds = WaterMap.Kind.values();
        float bw = (w - 6) / 2;
        for (int i = 0; i < kinds.length; i++) {
            if (ui.button(kinds[i].label(), x + (i % 2) * (bw + 6), y + (i / 2) * 34, bw, 30, waterKind == kinds[i],
                    true)) {
                waterKind = kinds[i];
            }
        }
        y += (kinds.length + 1) / 2 * 34 + 4;
        // fill a hollow with a click (to its brim, or a depth), or paint water with the brush
        String[] modes = {"Brush", "Fill to brim", "Fill to depth"};
        float mw = (w - 8) / 3;
        for (int i = 0; i < modes.length; i++) {
            if (ui.button(modes[i], x + i * (mw + 4), y, mw, 28, waterMode == i, true)) waterMode = i;
        }
        y += 36;
        if (waterMode == 1) {
            y += ui.paragraph(ui.small, "Click in a hollow: it fills to just under the lowest point of its rim, its "
                    + "shore where the ground comes up. Shift+drag dries it up.", x, y, w, Theme.HINT) + 6;
        } else if (waterMode == 2) {
            waterDepth = widgets.slider(ui, "w-depth", "Depth (m)", waterDepth, 0.1, 4, "%.2f", x, y + 10, w);
            y += 46;
            y += ui.paragraph(ui.small, "Click: the hollow fills to this depth over the ground clicked (all of it "
                    + "joined to it that is lower).", x, y, w, Theme.HINT) + 6;
        }
        if (waterMode == 0 || waterMode == 1) {
            radius = widgets.slider(ui, "radius", waterMode == 0 ? "Size (m)" : "Dry-up size", radius, 0.5, 40, "%.1f",
                    x, y + 10, w);
            y += 46;
        }
        if (waterMode == 0) {
        waterDepth = widgets.slider(ui, "w-depth", "Depth (m)", waterDepth, 0.1, 4, "%.2f", x, y + 10, w);
        y += 50;
        waterLevel = widgets.toggle(ui, "Lies level", waterLevel, x, y, w);
        y += 38;
        waterFlows = widgets.toggle(ui, "Flows", waterFlows, x, y, w);
        y += 40;
        }
        if (waterMode == 0 && waterFlows) {
            waterSpeed = widgets.slider(ui, "w-speed", "Current (m/s)", waterSpeed, 0.1, WaterMap.MAX_FLOW, "%.1f", x,
                    y + 10, w);
            y += 52;
        }
        // the sea
        WaterMap water = level.water();
        boolean sea = !Double.isNaN(water.seaLevel());
        boolean seaNow = widgets.toggle(ui, "The sea", sea, x, y, w);
        y += 40;
        if (seaNow != sea) {
            undo.before(level, Undo.Part.WATER, seaNow ? "The sea" : "No sea");
            water.setSeaLevel(seaNow ? -0.5 : Double.NaN);
            dirty = true;
        }
        if (seaNow) {
            double at = widgets.stepper(ui, "Sea level (m)", water.seaLevel(), 0.25, -20, 40, "%.2f", x, y, w);
            if (at != water.seaLevel()) {
                undo.before(level, Undo.Part.WATER, "Sea level");
                water.setSeaLevel(at);
                dirty = true;
            }
            y += 44;
        }
        ui.paragraph(ui.small, "Sculpt a hollow (3, Lower), then fill it. The brush paints water level at the depth "
                + "above where you press; turn \"Lies level\" off for a stream down a slope, and \"Flows\" makes it "
                + "run the way the brush moves. The sea fills everything below its level, out to the horizon. She "
                + "wades in the shallows and swims where it is deeper than "
                + String.format(java.util.Locale.ROOT, "%.1f", com.larsons.game.world.Player.SWIM_DEPTH) + " m.",
                x, y, w, Theme.HINT);
    }

    private void scatterPanel(Ui ui, float x, float y, float w) {
        y = heading(ui, "Scatter", x, y);
        List<Scatter.Set> all = scatterSets();
        if (all.isEmpty()) {
            ui.paragraph(ui.small, "No scenery sprites in assets/scenery/.", x, y, w, Theme.WARNING);
            return;
        }
        if (making != null) {
            brushMaker(ui, x, y, w);
            return;
        }
        List<String> names = new ArrayList<>();
        for (Scatter.Set st : all) names.add(st.label());
        set = Math.floorMod(widgets.choice(ui, "Sow", names, set, x, y, w), all.size());
        Scatter.Set current = all.get(set);
        y += 40;
        radius = widgets.slider(ui, "radius", "Size (m)", radius, 0.5, 40, "%.1f", x, y + 10, w);
        density = widgets.slider(ui, "density", "Thickness", density, 0.05, 1, "%.2f", x, y + 50, w);
        y += 90;
        // brushes of your own: which sprites, how often each, their sizes, how far apart
        ui.text(ui.small, "Your brushes (★)", x, y + 4, Theme.ACCENT);
        y += 22;
        float bw = (w - 8) / 3;
        if (ui.button("New", x, y, bw, 26)) {
            List<ScatterBrushes.Entry> first = new ArrayList<>();
            for (String n : current.sprites()) if (first.size() < 3) first.add(new ScatterBrushes.Entry(n, 1));
            startBrush(new ScatterBrushes.Brush(null, "My brush", first, 0.8, 1.2, 0, density));
        }
        ScatterBrushes.Brush mine = null;
        if (current.brush() != null) {
            for (ScatterBrushes.Brush b : ScatterBrushes.all()) if (b.id().equals(current.brush())) mine = b;
        }
        if (ui.button("Change", x + bw + 4, y, bw, 26, false, mine != null) && mine != null) startBrush(mine);
        if (ui.button("Delete", x + 2 * (bw + 4), y, bw, 26, false, mine != null) && mine != null) {
            try {
                ScatterBrushes.delete(mine.id());
                game.toast("Deleted the brush " + mine.name(), Theme.HINT);
            } catch (java.io.IOException e) {
                game.toast("Could not delete it: " + e.getMessage(), Theme.WARNING);
            }
            customSets = null;
            set = 0;
        }
        y += 36;
        y = filter.panel(ui, widgets, x, y, w, MATERIALS, BARE_ROCK) + 6;
        ui.paragraph(ui.small, "Hold the left button to sow; Shift pulls up what you are sowing. Trees keep "
                + "their distance, nothing grows inside a trunk or a rock, and trees and rocks stay off steep ground.",
                x, y, w, Theme.HINT);
    }

    // the brush being made: its own copy of everything, saved as a whole
    private final List<ScatterBrushes.Entry> makingEntries = new ArrayList<>();
    private double makingFrom = 1, makingTo = 1, makingSpacing, makingThick = 0.5;

    private void startBrush(ScatterBrushes.Brush b) {
        making = b;
        makingName = b.name();
        makingEntries.clear();
        makingEntries.addAll(b.entries());
        if (makingEntries.isEmpty() && !sprites.placeable().isEmpty()) {
            makingEntries.add(new ScatterBrushes.Entry(sprites.placeable().get(0).name(), 1));
        }
        makingFrom = b.sizeFrom();
        makingTo = b.sizeTo();
        makingSpacing = b.spacing();
        makingThick = b.thickness();
    }

    /** Making a scatter brush: its name, its sprites and how often each, their sizes, spacing, thickness. */
    private void brushMaker(Ui ui, float x, float y, float w) {
        ui.text(ui.small, making.id() == null ? "A new brush" : "Changing " + making.name(), x, y, Theme.HINT);
        y += 22;
        makingName = widgets.text(ui, "brush-name", "Name", makingName, x, y, w);
        y += Widgets.ROW + 10;
        List<String> spriteNames = new ArrayList<>(), shown = new ArrayList<>();
        for (ScenerySprites.Sprite sp : sprites.placeable()) {
            spriteNames.add(sp.name());
            shown.add(ObjectTypes.readable(sp.name()));
        }
        ui.text(ui.small, "Sprites, and how often each", x, y + 2, Theme.ACCENT);
        y += 22;
        int remove = -1;
        for (int i = 0; i < makingEntries.size(); i++) {
            ScatterBrushes.Entry e = makingEntries.get(i);
            int at = Math.max(0, spriteNames.indexOf(e.sprite()));
            int ni = widgets.choice(ui, "", shown, at, x, y, w - 30);
            if (ui.button("×", x + w - 26, y, 26, Widgets.ROW)) remove = i;
            y += Widgets.ROW + 4;
            double wt = widgets.slider(ui, "brush-w" + i, "How often", e.weight(), 0.1, 5, "%.1f", x, y + 8, w);
            y += 40;
            if (ni != at || wt != e.weight()) {
                makingEntries.set(i, new ScatterBrushes.Entry(spriteNames.isEmpty() ? e.sprite() : spriteNames.get(ni),
                        wt));
            }
        }
        if (remove >= 0) makingEntries.remove(remove);
        if (ui.button("+ Add a sprite", x, y, w, 26, false, !spriteNames.isEmpty() && makingEntries.size() < 12)) {
            makingEntries.add(new ScatterBrushes.Entry(spriteNames.get(0), 1));
        }
        y += 36;
        makingFrom = widgets.slider(ui, "brush-from", "Size from", makingFrom, 0.2, 4, "%.2f×", x, y + 10, w);
        makingTo = widgets.slider(ui, "brush-to", "Size to", makingTo, 0.2, 4, "%.2f×", x, y + 50, w);
        if (makingTo < makingFrom) makingTo = makingFrom;
        makingSpacing = widgets.slider(ui, "brush-space", "Apart (m)", makingSpacing, 0, 10, "%.1f", x, y + 90, w);
        makingThick = widgets.slider(ui, "brush-thick", "Thickness", makingThick, 0.05, 1, "%.2f", x, y + 130, w);
        y += 166;
        float bw = (w - 6) / 2;
        if (ui.button("Save brush", x, y, bw, 30, false, !makingEntries.isEmpty())) {
            try {
                ScatterBrushes.Brush saved = ScatterBrushes.save(new ScatterBrushes.Brush(making.id(), makingName,
                        List.copyOf(makingEntries), makingFrom, makingTo, makingSpacing, makingThick));
                customSets = null;
                List<Scatter.Set> all = scatterSets();
                for (int i = 0; i < all.size(); i++) if (saved.id().equals(all.get(i).brush())) set = i;
                density = makingThick;
                making = null;
                game.toast("Saved the brush " + saved.name() + " (assets/brushes/" + saved.id() + ".json)",
                        Theme.ACCENT);
            } catch (java.io.IOException e) {
                game.toast("Could not save it: " + e.getMessage(), Theme.WARNING);
            }
        }
        if (ui.button("Cancel", x + bw + 6, y, bw, 30)) making = null;
        y += 40;
        ui.paragraph(ui.small, "A brush sows its sprites by how often each is set, each a size between its from "
                + "and to, as far apart as set beyond their own footprints. Saved in assets/brushes/, for every "
                + "level.", x, y, w, Theme.HINT);
    }

    private void skyPanel(Ui ui, float x, float y, float w) {
        y = heading(ui, "Sky and distant backgrounds", x, y);
        // what is over it: the open sky, a roof (a hall, a dungeon), a cave's rock
        List<String> covers = new ArrayList<>();
        for (Sky.Cover c : Sky.Cover.values()) covers.add(c.label());
        int ci = widgets.choice(ui, "Over it", covers, level.cover().ordinal(), x, y, w);
        if (ci != level.cover().ordinal()) {
            level.setCover(Sky.Cover.values()[ci]);
            dirty = true;
        }
        y += 40;
        if (level.cover() != Sky.Cover.OPEN) {
            y += ui.paragraph(ui.small, level.cover() == Sky.Cover.CAVE ? "A cave: dark, cold rock all round and over "
                    + "it - no sky, no sun, no weather; its ground the level's. Light it with lanterns and candles "
                    + "(Place: Lights)." : "Inside: a great hall, a dungeon - dark beyond its walls, no sky, no sun, "
                    + "no weather; its lamps and candles lit all day.", x, y, w, Theme.HINT) + 10;
            Sky sky = level.sky();
            double fog = widgets.slider(ui, "fog", "Dark from (m)", sky.fog(), Sky.MIN_FOG, Sky.MAX_FOG, "%.0f", x,
                    y + 10, w);
            if (fog != sky.fog()) skyEdit(sky.withFog(fog), "Fog");
            y += 48;
            thinFog = widgets.toggle(ui, "Thin fog here", thinFog, x, y, w);
            return;
        }
        Sky sky = level.sky();
        String[] targets = {"Overhead", "Horizon", "Backdrop"};
        float bw = (w - 8) / 3;
        for (int i = 0; i < targets.length; i++) {
            boolean ok = i < 2 || backdrop >= 0 && backdrop < sky.backdrops().size();
            if (ui.button(targets[i], x + i * (bw + 4), y, bw, 28, skyTarget == i, ok)) skyTarget = i;
        }
        y += 36;
        boolean onBackdrop = skyTarget == 2 && backdrop >= 0 && backdrop < sky.backdrops().size();
        if (onBackdrop) {
            // which of its parts: the rock or the snow, the leaves or the trunks …
            Sky.Backdrop b = sky.backdrops().get(backdrop);
            List<String> parts = b.kind().parts();
            boolean own = b.colour2() >= 0;
            float pw = (w - 8) / 3;
            for (int i = 0; i < parts.size(); i++) {
                if (ui.button(parts.get(i), x + i * (pw + 4), y, pw, 26, backdropPart == i, true)) backdropPart = i;
            }
            if (ui.button("As it comes", x + 2 * (pw + 4), y, pw, 26, false, backdropPart == 1 && own)) {
                setBackdrop(b.withColour2(-1));
                sky = level.sky();
            }
            y += 32;
        }
        int current = skyTarget == 0 ? sky.zenith() : skyTarget == 1 ? sky.horizon()
                : !onBackdrop ? -1 : backdropPart == 1 ? partColour(sky.backdrops().get(backdrop))
                : sky.backdrops().get(backdrop).colour();
        int cols = 16;
        float size = Math.min(16, w / cols);
        int picked = widgets.swatches(ui, colours, current, x, y, cols, size);
        if (picked >= 0) {
            undo.before(level, Undo.Part.SKY, "Colour");
            if (skyTarget == 0) level.setSky(sky.withZenith(picked));
            else if (skyTarget == 1) level.setSky(sky.withHorizon(picked));
            else if (onBackdrop) {
                Sky.Backdrop b = sky.backdrops().get(backdrop);
                setBackdrop(backdropPart == 1 ? b.withColour2(picked) : b.withColour(picked));
            }
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
        ui.text(ui.small, "Backdrops (" + sky.backdrops().size() + " of " + Sky.MAX_BACKDROPS + ")", x, y,
                Theme.ITEM);
        y += 18;
        ui.text(ui.tiny, "First is furthest · drag one up or down", x, y, Theme.HINT);
        y += 16;
        List<Sky.Backdrop> list = sky.backdrops();
        float top = y;
        for (int i = 0; i < list.size(); i++) {
            Sky.Backdrop b = list.get(i);
            float rw = w - 34 - 2 * 26;
            String label = (i + 1) + ". " + b.kind().label() + (b.edge() ? "" : " · horizon");
            if (ui.button(label, x, y, rw, 26, i == backdrop, true)) {
                backdrop = i;
                skyTarget = 2;
                dragBackdrop = i;
                dragBackdropY = ui.input().mouseY();
                draggingBackdrop = false;
            }
            if (ui.button("↑", x + rw + 4, y, 22, 26, false, i > 0)) {
                moveBackdrop(i, i - 1);
                return;
            }
            if (ui.button("↓", x + rw + 30, y, 22, 26, false, i < list.size() - 1)) {
                moveBackdrop(i, i + 1);
                return;
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
        if (dragBackdrop >= 0 && dragBackdrop < list.size()) {
            // a row pressed: dragged up or down it moves, let go where it should be
            double my = ui.input().mouseY();
            int to = Math.max(0, Math.min(list.size() - 1, (int) Math.floor((my - top) / 30)));
            if (Math.abs(my - dragBackdropY) > 6) draggingBackdrop = true;
            if (!ui.input().mouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
                if (draggingBackdrop && to != dragBackdrop) moveBackdrop(dragBackdrop, to);
                dragBackdrop = -1;
                draggingBackdrop = false;
                return;
            }
            if (draggingBackdrop) {
                float ly = top + to * 30 + (to > dragBackdrop ? 28 : -3);
                ui.rect(x, ly, w, 3, Theme.ACCENT);
                ui.text(ui.tiny, list.get(dragBackdrop).kind().label(), (float) ui.input().mouseX() + 14, (float) my
                        - 6, Theme.ACCENT);
            }
        } else {
            dragBackdrop = -1;
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
            n = n.withDistance(widgets.slider(ui, "bd-dist", "Shore (m past the edge)", n.distance(), 0, 600, "%.0f",
                    x, y + 10, w));
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
        if (b.kind() != Sky.Kind.MOUNTAINS || n.snow()) {
            n = n.withExtent(widgets.slider(ui, "bd-extent", b.kind().extentLabel(), n.extent(), 0, 1, "%.2f", x,
                    y + 10, w));
            y += 40;
        }
        int at = widgets.choice(ui, "Stands", STANDS, n.edge() ? 0 : 1, x, y, w);
        n = n.withEdge(at == 0);
        y += Widgets.ROW + 6;
        ui.text(ui.small, n.allRound() ? "All the way round" : "From " + (int) n.from() + "° to " + (int) n.to()
                + "° (0 north, 90 east)", x, y + 4, Theme.HINT);
        if (!n.equals(b)) setBackdrop(n);
    }

    /** Where a backdrop can stand. */
    private static final List<String> STANDS = List.of("Round the level's edge", "Far off, at the horizon");

    /** The colour the second part of {@code b} shows: its own, or as it comes. */
    private static int partColour(Sky.Backdrop b) {
        return b.colour2() >= 0 ? b.colour2() : b.kind().colour2();
    }

    /** Move backdrop {@code from} to {@code to} in the order, back to front - the one chosen goes with it. */
    private void moveBackdrop(int from, int to) {
        List<Sky.Backdrop> next = new ArrayList<>(level.sky().backdrops());
        if (from < 0 || from >= next.size() || to < 0 || to >= next.size() || from == to) return;
        next.add(to, next.remove(from));
        undo.before(level, Undo.Part.SKY, "Move a backdrop");
        level.setSky(level.sky().withBackdrops(next));
        dirty = true;
        backdrop = to;
        skyTarget = 2;
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

    /** The hour the editor shows: the level's start, or where the time has run to. */
    private double shownHour() {
        return runTime ? runHour : level.sky().air().startHour();
    }

    private void timePanel(Ui ui, float x, float y, float w) {
        y = heading(ui, "Time and weather", x, y);
        String[] tabs = {"Time", "Planet", "Clouds", "Weather"};
        float bw = (w - 12) / 4;
        for (int i = 0; i < tabs.length; i++) {
            if (ui.button(tabs[i], x + i * (bw + 4), y, bw, 28, timeTab == i, true)) timeTab = i;
        }
        y += 40;
        Atmosphere a = level.sky().air(), n = a;
        switch (timeTab) {
            case 0 -> {
                ui.text(ui.body, "It is " + DayCycle.clock(shownHour()) + (runTime ? ", the time running" : ""), x, y,
                        Theme.TITLE);
                y += 30;
                n = n.withStartHour(widgets.slider(ui, "t-start", "Starts at", a.startHour(), 0, 23.99,
                        "%.1f", x, y + 10, w));
                y += 48;
                float qb = (w - 12) / 4;
                String[] names = {"Dawn", "Noon", "Dusk", "Night"};
                double[] hours = {6.3, 12, 18.0, 0};
                for (int i = 0; i < names.length; i++) {
                    if (ui.button(names[i], x + i * (qb + 4), y, qb, 26)) {
                        n = n.withStartHour(hours[i]);
                        runTime = false;
                    }
                }
                y += 36;
                n = n.withDayMinutes(widgets.slider(ui, "t-day", "Day (min)", a.dayMinutes(), 0,
                        Atmosphere.MAX_DAY_MINUTES, "%.0f", x, y + 10, w));
                y += 48;
                y += ui.paragraph(ui.small, a.timeRuns() ? "A whole day and night, in real minutes. The sun rises "
                        + "at 06:00 and sets at 18:00. Playing, hold N to run the time fast." : "0: the time stands "
                        + "still, always this hour.", x, y, w, Theme.HINT) + 10;
                boolean run = widgets.toggle(ui, "Run it here", runTime, x, y, w);
                if (run && !runTime) runHour = a.startHour();
                runTime = run;
                y += 38;
            }
            case 1 -> {
                n = n.withPlanet(widgets.toggle(ui, "The planet", a.planet(), x, y, w));
                y += 38;
                double az = widgets.slider(ui, "t-paz", "Where (°)", a.planetAzimuth(), 0, 359, "%.0f",
                        x, y + 10, w);
                y += 44;
                double el = widgets.slider(ui, "t-pel", "Height (°)", a.planetElevation(), -10, 80, "%.0f", x,
                        y + 10, w);
                y += 44;
                n = n.withPlanetAt(az, el);
                n = n.withPlanetSize(widgets.slider(ui, "t-psize", "Size (°)", a.planetSize(), 2, 60, "%.0f",
                        x, y + 10, w));
                y += 44;
                n = n.withRings(widgets.toggle(ui, "Rings", a.rings(), x, y, w));
                y += 34;
                n = n.withPlanetSeed((int) widgets.stepper(ui, "Bands", a.planetSeed(), 1, 1, 99, "%.0f", x, y, w));
                y += 38;
                ui.paragraph(ui.small, "Where: degrees round from north (90 east). The world is the planet's moon, so the planet never moves: only its lit side "
                        + "turns with the sun - a crescent by day, full at midnight, lighting the night.", x, y, w,
                        Theme.HINT);
            }
            case 3 -> {
                weatherPage(ui, x, y, w);
                return;
            }
            default -> {
                n = n.withClouds(widgets.slider(ui, "t-clouds", "Clouds", a.clouds(), 0, 1, "%.2f", x, y + 10, w));
                y += 44;
                n = n.withStars(widgets.slider(ui, "t-stars", "Stars", a.stars(), 0, 1, "%.2f", x, y + 10, w));
                y += 44;
                n = n.withAurora(widgets.slider(ui, "t-aurora", "Aurora", a.aurora(), 0, 1, "%.2f", x, y + 10, w));
                y += 44;
                n = n.withMist(widgets.slider(ui, "t-mist", "Mist", a.mist(), 0, 1, "%.2f", x, y + 10, w));
                y += 44;
                n = n.withMistHeight(widgets.slider(ui, "t-misth", "Mist up to (m)", a.mistHeight(), 0.3, 30,
                        "%.1f", x, y + 10, w));
                y += 44;
                n = n.withRays(widgets.slider(ui, "t-rays", "Light rays", a.rays(), 0, 1, "%.2f", x, y + 10, w));
                y += 44;
                ui.paragraph(ui.small, "The mist lies thickest at dawn and by night; the light rays slant through the "
                        + "trees while the sun is low.", x, y, w, Theme.HINT);
            }
        }
        if (!n.equals(a)) skyEdit(level.sky().withAir(n), "Time and weather");
    }

    /**
     * The Time tool's Weather page: none, by biome (each biome's own,
     * coming and going in spells - how often, how heavy) or one weather all
     * the time; and a button to see it here now.
     */
    private void weatherPage(Ui ui, float x, float y, float w) {
        Weather wt = level.weather(), nw = wt;
        List<String> modes = new ArrayList<>();
        for (Weather.Mode m : Weather.Mode.values()) modes.add(m.label());
        int mi = widgets.choice(ui, "Weather", modes, wt.mode().ordinal(), x, y, w);
        nw = nw.withMode(Weather.Mode.values()[mi]);
        y += 40;
        if (nw.mode() == Weather.Mode.FIXED) {
            List<Weather.Kind> kinds = new ArrayList<>(List.of(Weather.Kind.values()));
            kinds.remove(Weather.Kind.CLEAR);
            List<String> names = new ArrayList<>();
            for (Weather.Kind k : kinds) names.add(k.label());
            int ki = widgets.choice(ui, "Always", names, Math.max(0, kinds.indexOf(wt.kind())), x, y, w);
            nw = nw.withKind(kinds.get(ki));
            y += 40;
        }
        if (nw.mode() != Weather.Mode.NONE) {
            nw = nw.withStrength(widgets.slider(ui, "w-strength", "How heavy", wt.strength(), 0.1, 1, "%.2f", x,
                    y + 10, w));
            y += 44;
        }
        if (nw.mode() == Weather.Mode.BIOME) {
            nw = nw.withOften(widgets.slider(ui, "w-often", "How often", wt.often(), 0, 1, "%.2f", x, y + 10, w));
            y += 44;
            y += ui.paragraph(ui.small, "Each biome its own weather, in spells of a few minutes: snow over the "
                    + "mountains and the crystal, sandstorms over the savannah and the waste, rain and thunder over "
                    + "the plains, the jungle and the shore, fog over the marsh and the river, wind anywhere.", x, y,
                    w, Theme.HINT) + 10;
        }
        if (!nw.equals(wt)) {
            level.setWeather(nw);
            view.world().setWeather(nw);
            dirty = true;
        }
        if (level.cover() != Sky.Cover.OPEN) {
            ui.paragraph(ui.small, "Under its roof (the Sky tool: Over it) none of it is seen.", x, y, w,
                    Theme.WARNING);
            return;
        }
        // see each one here, now
        ui.text(ui.small, "See it here now:", x, y, Theme.HINT);
        y += 22;
        Weather.Kind[] shown = {Weather.Kind.CLEAR, Weather.Kind.RAIN, Weather.Kind.THUNDERSTORM, Weather.Kind.SNOW,
                Weather.Kind.FOG, Weather.Kind.WIND, Weather.Kind.SANDSTORM};
        float bw = (w - 8) / 3;
        for (int i = 0; i < shown.length; i++) {
            float bx = x + (i % 3) * (bw + 4), by = y + (i / 3) * 30;
            if (ui.button(shown[i].label(), bx, by, bw, 26)) {
                view.world().weatherNow().set(shown[i], shown[i] == Weather.Kind.CLEAR ? 0 : nw.strength());
            }
        }
    }

    /** Which page of the Life tool shows: the critters and the bushes, or the animals. */
    private boolean lifeAnimals;

    /**
     * The Life tool: how thick each of the little creatures lives in the
     * level (their biomes and hours still say where and when), how many are
     * about round the view now, how fast the foraging bushes grow back and
     * how much a picking gives - and, on its other page, how thick each of
     * the animals that come by on their own lives - kept with the level
     * ({@link Life}).
     */
    private void lifePanel(Ui ui, float x, float y, float w) {
        y = heading(ui, "Life", x, y);
        float hw = (w - 6) / 2;
        if (ui.button("Critters, bushes", x, y, hw, 26, !lifeAnimals, true)) lifeAnimals = false;
        if (ui.button("Animals", x + hw + 6, y, hw, 26, lifeAnimals, true)) lifeAnimals = true;
        y += 34;
        if (lifeAnimals) {
            animalsPage(ui, x, y, w);
            return;
        }
        Life life = level.life();
        y += ui.paragraph(ui.small, "They come and go round the player by their biome and hour. How many of each:",
                x, y, w, Theme.HINT) + 4;
        for (CritterKind k : CritterKind.values()) {
            double d = widgets.slider(ui, "life-" + k.key(), k.label(), life.density(k), 0, Life.MOST, "%.1f×",
                    x, y + 10, w);
            double snapped = Math.round(d * 20) / 20.0;
            if (snapped != life.density(k)) lifeEdit(l -> l.setDensity(k, snapped), k.label());
            y += 40;
        }
        StringJoiner about = new StringJoiner(" · ");
        for (CritterKind k : CritterKind.values()) {
            int n = view.world().critters().count(k);
            if (n > 0) about.add(n + " " + k.label().toLowerCase());
        }
        y += ui.paragraph(ui.small, "About now: " + (about.length() == 0 ? "none (try the night, or water)"
                : about), x, y, w, Theme.ITEM) + 8;
        y = heading(ui, "Foraging", x, y);
        double regrow = widgets.slider(ui, "life-regrow", "Grows back", life.regrow(), 0.05, 4, "%.2f× time",
                x, y + 10, w);
        double rs = Math.round(regrow * 20) / 20.0;
        if (rs != life.regrow()) lifeEdit(l -> l.setRegrow(rs), "Regrow time");
        y += 40;
        double yield = widgets.slider(ui, "life-yield", "Pickings", life.yield(), 0, 5, "%.1f×", x, y + 10, w);
        double ys = Math.round(yield * 10) / 10.0;
        if (ys != life.yield()) lifeEdit(l -> l.setYield(ys), "Pickings");
        y += 40;
        int bushes = 0;
        for (LevelObject o : level.objects()) {
            ScenerySprites.Sprite sp = o.type().equals(ObjectTypes.SCENERY) ? sprites.sprite(o.str("sprite", "")) : null;
            if (sp != null && sp.profile().forages()) bushes++;
        }
        y += ui.paragraph(ui.small, (bushes == 1 ? "1 foraging bush" : bushes + " foraging bushes")
                + " here (Scatter or a biome's paint sows more)", x, y, w, Theme.HINT) + 8;
        if (ui.button("Place a swarm of critters", x, y, w, 28)) placeLife(ObjectTypes.CRITTERS);
        y += 34;
        if (ui.button("Back to as it comes", x, y, w, 28, false, !life.isDefault())) {
            lifeEdit(l -> {
                for (CritterKind k : CritterKind.values()) l.setDensity(k, 1);
                for (AnimalKind k : AnimalKind.values()) l.setDensity(k, 1);
                l.setRegrow(1);
                l.setYield(1);
            }, "Life as it comes");
        }
    }

    /** The Life tool's animals: how thick each kind that comes by lives here, two columns of them. */
    private void animalsPage(Ui ui, float x, float y, float w) {
        Life life = level.life();
        y += ui.paragraph(ui.small, "They come and go round the player by biome. How many of each:",
                x, y, w, Theme.HINT) + 12;
        float cw = (w - 12) / 2;
        AnimalKind[] kinds = AnimalKind.values();
        for (int i = 0; i < kinds.length; i++) {
            AnimalKind k = kinds[i];
            float cx = x + (i % 2) * (cw + 12), cy = y + (i / 2) * 38;
            double d = widgets.slider(ui, "life-" + k.key(), k.label(), life.density(k), 0, Life.MOST, "%.1f×",
                    cx, cy, cw, true);
            double snapped = Math.round(d * 20) / 20.0;
            if (snapped != life.density(k)) lifeEdit(l -> l.setDensity(k, snapped), k.label());
        }
        y += ((kinds.length + 1) / 2) * 38 - 4;
        StringJoiner about = new StringJoiner(" · ");
        for (AnimalKind k : kinds) {
            int n = view.world().animals().count(k);
            if (n > 0) about.add(n + " " + (n == 1 ? k.singular() : k.label()).toLowerCase());
        }
        y += ui.paragraph(ui.small, "About now: " + (about.length() == 0 ? "none here (try another biome)"
                : about), x, y, w, Theme.ITEM) + 8;
        if (ui.button("Place a herd", x, y, (w - 6) / 2, 28)) placeLife(ObjectTypes.ANIMALS);
        if (ui.button("All off", x + (w - 6) / 2 + 6, y, (w - 6) / 2, 28)) {
            lifeEdit(l -> {
                for (AnimalKind k : AnimalKind.values()) l.setDensity(k, 0);
            }, "No animals");
        }
        y += 34;
        boolean asTheyCome = true;
        for (AnimalKind k : kinds) asTheyCome &= life.density(k) == 1;
        if (ui.button("Back to as they come", x, y, w, 28, false, !asTheyCome)) {
            lifeEdit(l -> {
                for (AnimalKind k : AnimalKind.values()) l.setDensity(k, 1);
            }, "Animals as they come");
        }
    }

    /** Off to the Place tool's Life, the first entry of {@code type} chosen. */
    private void placeLife(String type) {
        setTool(Tool.PLACE);
        category = Category.LIFE;
        paletteScroll = 0;
        for (int i : entriesOf(Category.LIFE)) {
            if (palette.get(i).type().equals(type)) {
                choose(i);
                break;
            }
        }
    }

    /** Change the level's life - one undo step for a whole drag of a slider. */
    private void lifeEdit(java.util.function.Consumer<Life> change, String label) {
        String key = "life:" + label;
        if (!widgets.dragging() || !key.equals(propSnapshot)) undo.before(level, Undo.Part.SKY, label);
        propSnapshot = widgets.dragging() ? key : null;
        Life next = level.life().copy();
        change.accept(next);
        level.setLife(next);
        view.world().setLife(next);
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
        y = musicSection(ui, x, y, w);
        ui.paragraph(ui.small, (folder == null ? "Not saved yet" : "Saved in " + game.relative(folder))
                + "\n" + things(level.objects().size()) + " placed", x, y, w, Theme.HINT);
        y += 60;
        if (ui.button("Save (Ctrl+S)", x, y, w, 32)) save();
        y += 40;
        if (ui.button("Play from the spawn (F5)", x, y, w, 32)) playTest(spawnOrPointer());
        y += 40;
        if (ui.button("Back to the menu (Esc)", x, y, w, 32)) leave();
        y += 42;
        // walls as in the Sims: up, cut away between the camera and what it looks at, or all down
        var settings = game.settings();
        List<String> wallLabels = Walls.labels();
        int wi = widgets.choice(ui, "Walls indoors", wallLabels, settings.indoors().ordinal(), x, y, w, 0.5f);
        if (wi != settings.indoors().ordinal()) {
            settings.wallsIndoors = Walls.values()[wi].name();
            game.worldRenderer().setWalls(settings.indoors(), settings.outdoors());
        }
        y += Widgets.ROW + 6;
        int wo = widgets.choice(ui, "Walls outdoors", wallLabels, settings.outdoors().ordinal(), x, y, w, 0.5f);
        if (wo != settings.outdoors().ordinal()) {
            settings.wallsOutdoors = Walls.values()[wo].name();
            game.worldRenderer().setWalls(settings.indoors(), settings.outdoors());
        }
        y += Widgets.ROW + 12;
        ui.paragraph(ui.small, "Everything here is saved in the level's folder: level.json (readable, one thing "
                + "a line), height.png (the ground's heights) and ground.png (its paint).", x, y, w,
                Theme.ITEM_DISABLED);
    }

    /**
     * The level's music - none, every song shuffled, or one song looping -
     * and a player to listen to any song first: play or pause, a timeline to
     * drag to anywhere in it, where it is of how long, and "Use this song".
     * Returns the y below it.
     */
    private float musicSection(Ui ui, float x, float y, float w) {
        List<String> shown = new ArrayList<>(List.of("None", "Shuffle all"));
        for (Songs.Song song : Songs.ALL) shown.add(song.title());
        int cur = musicIndex(level.music());
        int pick = widgets.choice(ui, "Music", shown, cur, x, y, w, MUSIC_LABEL);
        if (pick != cur) setMusic(pick == 0 ? Songs.NONE : pick == 1 ? Songs.SHUFFLE : Songs.ALL.get(pick - 2).id());
        y += 34;
        if (Songs.ALL.isEmpty()) return y + 6;
        if (Songs.byId(listening) == null) {
            listening = Songs.byId(level.music()) != null ? level.music() : Songs.ALL.get(0).id();
        }
        List<String> titles = Songs.ALL.stream().map(Songs.Song::title).toList();
        int li = Songs.ALL.indexOf(Songs.byId(listening));
        int lp = widgets.choice(ui, "Listen to", titles, li, x, y, w, MUSIC_LABEL);
        if (lp != li) listen(Songs.ALL.get(lp).id());
        Songs.Song song = Songs.byId(listening);
        preview.open(song.key());
        y += 34;

        boolean ready = preview.ready();
        if (ui.button(preview.playing() ? "Pause" : "Play", x, y, 64, 28, preview.playing(),
                ready || preview.loading())) {
            preview.toggle();
        }
        double len = preview.length();
        double t = len > 0 ? preview.position() / len : 0;
        double nt = ui.slider(x + 74, y + 4, w - 74, 20, t);
        if (nt != t && len > 0) preview.scrub(nt * len);
        if (preview.scrubbing() && !ui.input().mouseDown(GLFW_MOUSE_BUTTON_LEFT)) preview.release();
        y += 34;

        String clock = preview.loading() ? "Reading the song…" : !ready ? "No sound file for this song"
                : clock(preview.position()) + " / " + clock(len);
        ui.text(ui.small, clock, x, y + 6, ready ? Theme.ITEM : Theme.HINT);
        if (song.id().equals(level.music())) {
            String in = "In this level";
            ui.text(ui.small, in, x + w - ui.small.width(in) - 6, y + 6, Theme.TITLE);
        } else if (ui.button("Use this song", x + w - 120, y, 120, 26)) {
            setMusic(song.id());
        }
        y += 32;
        return y + ui.paragraph(ui.small, song.label(), x, y, w, Theme.ITEM_DISABLED) + 10;
    }

    /** The music rows' labels are short: more of the row for the song's title. */
    private static final float MUSIC_LABEL = 0.3f;

    /** Where {@code music} is in the Music choice: none, shuffle, then the songs (unknown: none). */
    private static int musicIndex(String music) {
        if (Songs.SHUFFLE.equals(music)) return 1;
        Songs.Song song = Songs.byId(music);
        return song == null ? 0 : 2 + Songs.ALL.indexOf(song);
    }

    /** The level's music from now on, as one undo step; a song chosen is the one listened to. */
    private void setMusic(String music) {
        if (music.equals(level.music())) return;
        undo.before(level, Undo.Part.SKY, "Music");
        level.setMusic(music);
        dirty = true;
        if (Songs.byId(music) != null) listen(music);
    }

    /** Listen to {@code id} in the player, from its start (paused, unless it was playing). */
    private void listen(String id) {
        if (id.equals(listening)) return;
        boolean was = preview.playing();
        listening = id;
        Songs.Song song = Songs.byId(id);
        preview.open(song == null ? "" : song.key());
        if (was) preview.play();
    }

    /** {@code seconds} as m:ss. */
    static String clock(double seconds) {
        int s = (int) Math.max(0, Math.floor(seconds));
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    // --- the right panel: what is being placed, or what is selected ------------------------------------

    private void rightPanel(Ui ui) {
        if (!rightPanelShown()) return;
        float w = RIGHT_W - 8, x = ui.width() - RIGHT_W, y = TOP_H + 8, h = ui.height() - TOP_H - HELP_H - 16;
        ui.panel(x, y, w, h);
        // scrolled back to its top for something else
        Object showing = placingShown() ? templateLabel : selecting() && selection.size() == 1 ? selection.get(0)
                : tool;
        if (!java.util.Objects.equals(showing, rightFor)) rightScroll = 0;
        rightFor = showing;
        float py = scrollBegin(ui, x, y, w, h, rightScroll);
        rightPanelContent(ui, x + 12, py, w - 24, y, h);
        rightScroll = scrollEnd(ui, x, y, w, h, rightScroll);
    }

    private void rightPanelContent(Ui ui, float px, float py, float pw, float y, float h) {
        if (tool == Tool.FORGE) {
            forgePreview(ui, px, py, pw);
            return;
        }
        if (tool == Tool.CARDS) {
            cardsTool().preview(ui, px, py, pw);
            return;
        }
        if (placingShown()) {
            py = heading(ui, "Placing", px, py);
            ui.text(ui.body, Ui.fit(ui.body, templateLabel, pw), px, py, Theme.TITLE);
            py += 32;
            properties(ui, template, false, px, py, pw);
            return;
        }
        if (selection.size() > 1) {
            py = heading(ui, selection.size() + " selected", px, py);
            Map<String, Integer> kinds = new LinkedHashMap<>();
            for (LevelObject o : selection) kinds.merge(ObjectTypes.label(o), 1, Integer::sum);
            for (Map.Entry<String, Integer> e : kinds.entrySet()) {
                ui.text(ui.small, Ui.fit(ui.small, e.getValue() + " × " + e.getKey(), pw), px, py, Theme.ITEM);
                py += 20;
            }
            if (copiedLook != null) {
                py += 8;
                if (ui.button("Paste look on them all", px, py, pw, 30)) pasteLook(new ArrayList<>(selection));
                ui.text(ui.tiny, Ui.fit(ui.tiny, "Copied: " + copiedName, pw), px, py + 34, Theme.HINT);
            }
            return;
        }
        LevelObject o = selection.get(0);
        py = heading(ui, Ui.fit(ui.body, ObjectTypes.label(o), pw), px, py);
        ui.text(ui.small, o.type() + " #" + o.id(), px, py - 6, Theme.ITEM_DISABLED);
        py += 18;
        if (hasInside(o)) {
            List<Level> fl = level.interior(o.id());
            String label = fl == null ? "Edit inside (as it comes)" : "Edit inside (" + fl.size()
                    + (fl.size() == 1 ? " floor)" : " floors)");
            if (ui.button(label, px, py, pw, 30, false, true)) editInside(o);
            py += 40;
        }
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
            if (!ObjectTypes.applies(o, p)) continue;
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
                    if (nv != cur) {
                        edit(o, p.label(), () -> o.set(p.key(), nv));
                        if (p.key().equals("fit") && nv) fitVines(o, true);
                    }
                }
                case COLOUR -> {
                    // a swatch of it (or "none"), the picker, and taking it off
                    int cur = ObjectTypes.hex(String.valueOf(v));
                    ui.text(ui.small, p.label(), x, y + 6, Theme.ITEM);
                    float sx = x + w * 0.4f;
                    if (cur >= 0) ui.rect(sx, y + 2, 22, 22, Theme.rgb((cur >> 16) & 255, (cur >> 8) & 255, cur & 255, 1f));
                    else ui.text(ui.small, "none", sx, y + 6, Theme.ITEM_DISABLED);
                    if (ui.button("Pick", sx + 28, y, 58, 26)) {
                        colourPick = new ColourPick(o, p.key(), false, p.label() + ": " + typeLabel(o), List.of());
                    }
                    if (cur >= 0 && ui.button("None", sx + 92, y, 58, 26)) edit(o, p.label(), () -> o.set(p.key(), null));
                }
                case COLOURS -> {
                    // a herd's colours: a chip of each, and the picker
                    List<Integer> cur = ObjectTypes.herdColours(o);
                    ui.text(ui.small, p.label(), x, y + 6, Theme.ITEM);
                    float sx = x + w * 0.4f;
                    if (cur.isEmpty()) ui.text(ui.small, "as drawn", sx, y + 6, Theme.ITEM_DISABLED);
                    for (int i = 0; i < Math.min(5, cur.size()); i++) {
                        int c = cur.get(i);
                        float cx = sx + i * 16;
                        if (c < 0) ui.outline(cx, y + 5, 14, 14, 1, Theme.ITEM);
                        else ui.rect(cx, y + 5, 14, 14, Theme.rgb((c >> 16) & 255, (c >> 8) & 255, c & 255, 1f));
                    }
                    if (ui.button("Choose", x + w - 70, y, 70, 26)) pickHerdColours(o);
                }
            }
            y += r;
        }
        if (ObjectTypes.builds(o.type())) lookPanel(ui, o, x, y + 4, w);
    }

    /** What {@code o} is called in the editor: its kind's name ("Cows"), its sprite's, or its type's. */
    private String typeLabel(LevelObject o) {
        if (o.type().equals(ObjectTypes.ANIMALS)) {
            com.larsons.game.ambient.AnimalKind k = com.larsons.game.ambient.AnimalKind.byKey(o.str("kind", ""));
            if (k != null) return o.bool("single", false) ? k.singular() : k.label();
        }
        if (o.type().equals(ObjectTypes.SCENERY)) return ObjectTypes.readable(o.str("sprite", ""));
        ObjectType t = ObjectTypes.get(o.type());
        return t == null ? o.type() : t.label();
    }

    /** Ask what colours herd {@code o} is: its kind's coats, or any of the 128. */
    private void pickHerdColours(LevelObject o) {
        com.larsons.game.ambient.AnimalKind k = com.larsons.game.ambient.AnimalKind.byKey(o.str("kind", ""));
        if (k == null) return;
        colourPick = new ColourPick(o, "colours", true, "What colours are these " + typeLabel(o).toLowerCase(
                java.util.Locale.ROOT) + "?", com.larsons.game.ambient.HerdColours.of(k));
    }

    /** {@code rgb} as a herd's colour is written: {@code "#rrggbb"}, or {@code "natural"} (-1). */
    private static String colourKey(int rgb) {
        return rgb < 0 ? ObjectTypes.NATURAL : String.format("#%06x", rgb & 0xFFFFFF);
    }

    /**
     * The colour picker, over everything: a herd's colours - its kind's coats
     * to tick, any of the 128 colours to add, those picked to take off again
     * - or one colour (a tint). Done (or Esc) closes it.
     */
    private void colourDialog(Ui ui) {
        ColourPick pick = colourPick;
        LevelObject o = pick.target();
        if (o != template && level.byId(o.id()) == null) {
            colourPick = null;           // (it was taken away under the picker: undo)
            return;
        }
        ui.scrim();
        float w = Math.min(560, ui.width() - 40), x = (ui.width() - w) / 2;
        int cols = 16;
        float size = Math.min(22, (w - 40) / cols);
        float presetRows = pick.multi() ? (pick.presets().size() + 2) / 3 : 0;
        float h = 120 + presetRows * 34 + Widgets.swatchesHeight(colours.length, cols, size) + (pick.multi() ? 70 : 20);
        float y = Math.max(20, (ui.height() - h) / 2);
        ui.panel(x, y, w, h);
        float ix = x + 20, iw = w - 40, cy = y + 16;
        ui.text(ui.body, pick.title(), ix, cy, Theme.TITLE);
        cy += 30;
        List<Integer> chosen = pick.multi() ? new ArrayList<>(ObjectTypes.herdColours(o)) : new ArrayList<>();
        int single = pick.multi() ? -1 : ObjectTypes.hex(o.str(pick.key(), ""));
        if (pick.multi()) {
            ui.paragraph(ui.small, "Tick one or more: each of them is one of these, at random. None ticked: all as "
                    + "drawn.", ix, cy, iw, Theme.HINT);
            cy += 24;
            float bw = (iw - 12) / 3;
            for (int i = 0; i < pick.presets().size(); i++) {
                var pr = pick.presets().get(i);
                float bx = ix + (i % 3) * (bw + 6), by = cy + (i / 3) * 34;
                boolean on = chosen.contains(pr.rgb());
                if (ui.button(pr.name(), bx + 22, by, bw - 22, 28, on, true)) {
                    if (on) chosen.remove(Integer.valueOf(pr.rgb()));
                    else chosen.add(pr.rgb());
                    setColours(o, chosen);
                }
                if (pr.rgb() < 0) ui.outline(bx, by + 6, 16, 16, 1, Theme.ITEM);
                else ui.rect(bx, by + 6, 16, 16, Theme.rgb((pr.rgb() >> 16) & 255, (pr.rgb() >> 8) & 255,
                        pr.rgb() & 255, 1f));
            }
            cy += presetRows * 34 + 6;
            ui.text(ui.small, "Or any colour:", ix, cy, Theme.ITEM);
            cy += 20;
        }
        int picked = widgets.swatches(ui, colours, single, ix, cy, cols, size);
        cy += Widgets.swatchesHeight(colours.length, cols, size) + 10;
        if (picked >= 0) {
            if (pick.multi()) {
                if (!chosen.contains(picked & 0xFFFFFF)) chosen.add(picked & 0xFFFFFF);
                setColours(o, chosen);
            } else {
                edit(o, "Tint", () -> o.set(pick.key(), colourKey(picked)));
            }
        }
        if (pick.multi()) {
            // the colours picked: click one to take it off
            ui.text(ui.small, chosen.isEmpty() ? "Picked: none (as drawn)" : "Picked (click to take off):", ix, cy + 4,
                    Theme.ITEM);
            float px = ix + 190;
            for (int i = 0; i < chosen.size() && i < 14; i++) {
                int c = chosen.get(i);
                float sx = px + i * 24;
                if (c < 0) ui.outline(sx, cy, 20, 20, 2, Theme.ITEM);
                else ui.rect(sx, cy, 20, 20, Theme.rgb((c >> 16) & 255, (c >> 8) & 255, c & 255, 1f));
                if (ui.clicked(sx, cy, 20, 20)) {
                    chosen.remove(i);
                    setColours(o, chosen);
                    break;
                }
            }
            cy += 30;
        }
        if (ui.button("Done", x + w - 120, y + h - 40, 100, 30)) colourPick = null;
        if (!pick.multi() && ui.button("None", x + w - 230, y + h - 40, 100, 30)) {
            edit(o, "Tint", () -> o.set(pick.key(), null));
            colourPick = null;
        }
    }

    /** Herd {@code o}'s colours set to {@code rgbs} (-1: as drawn); none: all as drawn. */
    private void setColours(LevelObject o, List<Integer> rgbs) {
        List<Object> out = new ArrayList<>();
        for (int c : rgbs) out.add(colourKey(c));
        edit(o, "Colours", () -> o.set("colours", out.isEmpty() ? null : out));
    }

    /** Whether {@code o} is a building with an inside to edit. */
    private static boolean hasInside(LevelObject o) {
        if (!o.type().equals(ObjectTypes.BUILDING)) return false;
        BuildingProfile p = BuildingCatalog.get().profile(o.str("kind", ""));
        return p != null && p.interior();
    }

    /** What {@code o} - a building, a piece of furniture, a room's piece - is made of, or null. */
    private static BuildingProfile profileOf(LevelObject o) {
        Room.Spec room = ObjectTypes.roomSpec(o);
        if (room != null) return Room.profile(room);
        return o.type().equals(ObjectTypes.BUILDING) ? BuildingCatalog.get().profile(o.str("kind", "")) : null;
    }

    /**
     * A building's look, part by part, as walls are done in the Sims: the
     * part, any of the 128 colours for it (and the shades it is drawn in),
     * its pattern - or all of it back as it comes.
     */
    private void lookPanel(Ui ui, LevelObject o, float x, float y, float w) {
        BuildingCatalog catalog = BuildingCatalog.get();
        BuildingProfile p = profileOf(o);
        if (p == null || p.parts().isEmpty()) return;
        y = heading(ui, "Look", x, y);
        List<String> names = new ArrayList<>();
        for (BuildingProfile.Part part : p.parts()) names.add(part.label());
        lookPart = Math.max(0, Math.min(names.size() - 1, lookPart));
        lookPart = widgets.choice(ui, "Part", names, lookPart, x, y, w, 0.3f);
        y += Widgets.ROW + 8;
        BuildingProfile.Part part = p.parts().get(lookPart);
        BuildingLook look = BuildingLook.of(o.props().get("look"));
        int colour = look.colour(part);
        int[] shades = pixelPalette == null ? null : BuildingLook.ramp(colour, pixelPalette);
        int cols = 16;
        float size = Math.min(16, w / cols);
        int picked = widgets.swatches(ui, colours, colour, x, y, cols, size);
        if (picked >= 0) edit(o, "Colour", () -> setLook(o, p, look.copy().setColour(part.key(), picked)));
        y += Widgets.swatchesHeight(colours.length, cols, size) + 6;
        if (shades != null) {
            ui.text(ui.small, "Shades", x, y + 2, Theme.ITEM);
            float sx = x + w * 0.3f, sw = (w * 0.7f) / 6;
            for (int i = 0; i < 6; i++) {
                int c = shades[i];
                ui.rect(sx + i * sw, y, sw - 2, 16, Theme.rgb((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, 1f));
            }
            y += 24;
        }
        List<BuildingCatalog.Texture> textures = catalog.textures();
        if (!textures.isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (BuildingCatalog.Texture t : textures) labels.add(t.label());
            int at = catalog.textureIndex(look.texture(part));
            int ni = widgets.choice(ui, "Pattern", labels, at, x, y, w, 0.3f);
            if (ni != at) edit(o, "Pattern", () -> setLook(o, p, look.copy().setTexture(part.key(), textures.get(ni).name())));
            y += Widgets.ROW + 8;
        }
        if (ui.button("As it comes", x, y, w, 28, false, o.props().get("look") != null)) {
            edit(o, "Look", () -> o.set("look", null));
        }
        y += 34;
        // its colours onto other buildings: copy here, paste on another (or on many selected)
        float hw = (w - 4) / 2;
        if (ui.button("Copy look", x, y, hw, 28)) {
            copiedFrom = p;
            copiedLook = look.copy();
            copiedName = ObjectTypes.label(o);
            game.toast("Copied " + copiedName + "'s look: select another building and Paste look", Theme.HINT);
        }
        if (ui.button("Paste look", x + hw + 4, y, hw, 28, false, copiedLook != null)) pasteLook(List.of(o));
        if (copiedLook != null) {
            ui.text(ui.tiny, Ui.fit(ui.tiny, "Copied: " + copiedName, w), x, y + 32, Theme.HINT);
        }
    }

    /** The look copied (Copy look), what it was copied from, and its name - or null: none. */
    private BuildingLook copiedLook;
    private BuildingProfile copiedFrom;
    private String copiedName;

    /** The look copied put on each of {@code targets} that is a building, part matched to part. */
    private void pasteLook(List<LevelObject> targets) {
        if (copiedLook == null) return;
        List<LevelObject> onto = new ArrayList<>();
        for (LevelObject t : targets) {
            BuildingProfile tp = profileOf(t);
            if (tp != null && ObjectTypes.builds(t.type()) && BuildingLook.matches(copiedFrom, tp) > 0) onto.add(t);
        }
        if (onto.isEmpty()) {
            game.toast("Nothing there shares a part with " + copiedName, Theme.WARNING);
            return;
        }
        undo.before(level, Undo.Part.OBJECTS, "Paste look");
        for (LevelObject t : onto) {
            BuildingProfile tp = profileOf(t);
            setLook(t, tp, BuildingLook.transfer(copiedFrom, copiedLook, tp, BuildingLook.of(t.props().get("look"))));
        }
        changed();
        game.toast(onto.size() == 1 ? "Look pasted" : "Look pasted on " + onto.size() + " buildings", Theme.HINT);
    }

    /** {@code o}'s look set to {@code look} (only what differs from how {@code p} comes is kept). */
    private static void setLook(LevelObject o, BuildingProfile p, BuildingLook look) {
        Map<String, Object> props = look.toProps(p);
        o.set("look", props.isEmpty() ? null : props);
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
        if (key.equals("passage")) return Building.Passage.label(option);
        if (key.equals("item")) {
            if (option.startsWith(Forged.PREFIX)) {
                Forged f = Forged.byId(option.substring(Forged.PREFIX.length()));
                return f == null ? option : f.name() + " (forged)";
            }
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
            case "pointer" -> setPointer(!argument.trim().equals("off"));
            case "copylook", "pastelook" -> {
                // copylook <id> | pastelook <id> [id …]: a building's look onto others
                List<LevelObject> found = new ArrayList<>();
                for (String id : a) {
                    for (LevelObject o : level.objects()) if (String.valueOf(o.id()).equals(id)) found.add(o);
                }
                if (found.isEmpty()) return false;
                if (command.equals("pastelook")) {
                    pasteLook(found);
                    return true;
                }
                LevelObject o = found.get(0);
                copiedFrom = profileOf(o);
                if (copiedFrom == null) return false;
                copiedLook = BuildingLook.of(o.props().get("look"));
                copiedName = ObjectTypes.label(o);
            }
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
                if (!pointer) setTool(Tool.SELECT);
            }
            case "delete" -> deleteSelection();
            case "savetemplate" -> {
                templateName = argument.trim();
                saveTemplate();
            }
            case "selectall" -> {
                selection.clear();
                selection.addAll(level.objects());
            }
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
            case "brush" -> squareBrush = argument.trim().equals("square");
            case "tint" -> {
                // tint <#rrggbb|off> x z radius [amount]
                undo.before(level, Undo.Part.GROUND, "Tint");
                int rgb = a[0].equals("off") ? -1 : Integer.parseInt(a[0].replace("#", ""), 16);
                level.ground().square(squareBrush).tint(Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                        Double.parseDouble(a[3]), 1, rgb, a.length > 4 ? Double.parseDouble(a[4]) : 0.6);
                dirty = true;
            }
            case "cover" -> {
                // cover <on|off> x z radius: paint over the rock faces there (or bare them)
                undo.before(level, Undo.Part.GROUND, "Paint over rock");
                level.ground().square(squareBrush).cover(Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                        Double.parseDouble(a[3]), 1, a[0].equals("on"));
                dirty = true;
            }
            case "paint" -> {
                // paint <0 grass .. 12 crystal> x z radius
                undo.before(level, Undo.Part.GROUND, "Paint");
                level.ground().paint(Integer.parseInt(a[0]) - 1, Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                        Double.parseDouble(a[3]), 1);
                dirty = true;
            }
            case "biome" -> {
                // biome <key> x z radius [sow 0|1]
                Biome b = Biome.byKey(a[0]);
                if (b == null) return false;
                double x = Double.parseDouble(a[1]), z = Double.parseDouble(a[2]), r = Double.parseDouble(a[3]);
                boolean sow = a.length <= 4 || !a[4].equals("0");
                undo.before(level, sow ? Undo.Part.ALL : Undo.Part.GROUND, b.label());
                level.ground().paint(b::groundAt, x, z, r, 1);
                if (sow) sowBiome(b, x, z, r, 6);
                dirty = true;
            }
            case "sow" -> {
                // sow <biome> x z radius [dabs] [tuft|tree|rock|bushes]: its plants, trees, rocks and foraging
                // bushes (or only those), the ground as it is
                Biome b = Biome.byKey(a[0]);
                if (b == null) return false;
                undo.before(level, Undo.Part.OBJECTS, "Sow");
                double sx = Double.parseDouble(a[1]), sz = Double.parseDouble(a[2]), sr = Double.parseDouble(a[3]);
                int dabs = a.length > 4 ? Integer.parseInt(a[4]) : 1;
                if (a.length > 5 && a[5].equals("bushes")) {
                    Scatter.Set bushes = Scatter.bushSet(sprites, b);
                    for (int t = 0; t < dabs; t++) {
                        Scatter.sow(level, view.terrain(), sprites, bushes, sx, sz, sr, 0.6, Math.PI * sr * sr * 0.2,
                                random);
                    }
                    changed();
                } else {
                    sowBiome(b, sx, sz, sr, dabs, a.length > 5 ? SceneryProfile.Kind.byKey(a[5]) : null);
                }
            }
            case "waterdepth" -> waterDepth = Double.parseDouble(a[0]);
            case "water" -> {
                // water <clear|swamp|sea|crystal> x z radius [surface|auto] [flowx flowz]
                // water erase x z radius;  water sea <surface|off>
                undo.before(level, Undo.Part.WATER, "Water");
                String k = a[0].toLowerCase(java.util.Locale.ROOT);
                if (k.equals("sea") && a.length == 2) {
                    level.water().setSeaLevel(a[1].equalsIgnoreCase("off") ? Double.NaN : Double.parseDouble(a[1]));
                } else if (k.equals("erase")) {
                    level.water().erase(Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]));
                } else {
                    double x = Double.parseDouble(a[1]), z = Double.parseDouble(a[2]);
                    double surface = a.length > 4 && !a[4].equals("auto") ? Double.parseDouble(a[4])
                            : view.terrain().height(x, z) + waterDepth;
                    level.water().paint(WaterMap.Kind.byKey(k), surface, x, z, Double.parseDouble(a[3]),
                            a.length > 6 ? Double.parseDouble(a[5]) : 0, a.length > 6 ? Double.parseDouble(a[6]) : 0);
                }
                dirty = true;
            }
            case "fill" -> {
                // fill <clear|swamp|sea|crystal> x z [depth]: the hollow there filled to its brim (or that deep)
                WaterMap.Kind k = WaterMap.Kind.byKey(a[0]);
                if (k != null) waterKind = k;
                if (a.length > 3) waterDepth = Double.parseDouble(a[3]);
                fillHollow(Double.parseDouble(a[1]), Double.parseDouble(a[2]), a.length <= 3);
            }
            case "feather" -> sculptFeather = paintFeather = Double.parseDouble(a[0]);
            case "filter" -> {
                // filter off | slope <from> <to> | height <from> <to> | only <material> | not <material> | water <0-2>
                filter.on = !a[0].equals("off");
                switch (a[0]) {
                    case "slope" -> {
                        filter.slopeFrom = Double.parseDouble(a[1]);
                        filter.slopeTo = Double.parseDouble(a[2]);
                    }
                    case "height" -> {
                        filter.heightFrom = Double.parseDouble(a[1]);
                        filter.heightTo = Double.parseDouble(a[2]);
                    }
                    case "only" -> filter.onlyOn = Integer.parseInt(a[1]);
                    case "not" -> filter.notOn = Integer.parseInt(a[1]);
                    case "water" -> filter.water = Integer.parseInt(a[1]);
                    default -> { }
                }
            }
            case "scatter" -> {
                // scatter <set index> x z radius [density]
                Scatter.Set s = scatterSets().get(Integer.parseInt(a[0]));
                double r = Double.parseDouble(a[3]);
                undo.before(level, Undo.Part.OBJECTS, "Scatter");
                for (int i = 0; i < 6; i++) {
                    Scatter.sow(level, view.terrain(), sprites, s, Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                            r, a.length > 4 ? Double.parseDouble(a[4]) : density, Math.PI * r * r * 0.2, random);
                }
                changed();
            }
            case "music" -> setMusic(argument.isBlank() ? Songs.NONE : argument.trim());
            // over open|inside|cave: what is over the level
            case "over" -> {
                level.setCover(Sky.Cover.byKey(argument.trim()));
                dirty = true;
            }
            // weathermode none|biome|fixed [kind] [how heavy]; weathernow <kind> <how heavy>: see it at once
            case "weathermode" -> {
                Weather wt = level.weather().withMode(Weather.Mode.byKey(a[0]));
                if (a.length > 1) wt = wt.withKind(Weather.Kind.byKey(a[1]));
                if (a.length > 2) wt = wt.withStrength(Double.parseDouble(a[2]));
                level.setWeather(wt);
                dirty = true;
            }
            case "weathernow" -> view.world().weatherNow().set(Weather.Kind.byKey(a[0]),
                    a.length > 1 ? Double.parseDouble(a[1]) : 0.9);
            case "timetab" -> timeTab = Integer.parseInt(a[0]);
            case "forge" -> {
                // forge new <name> | name <n> | base <id> | rarity <key> | dice <n> | sides <n> |
                // effect <key> on|off | tint <part> <#rgb|none> | dicecolour <#rgb|none> | colour <n> | save | place
                String what = a[0], rest = argument.trim().substring(what.length()).trim();
                switch (what) {
                    case "new" -> forging = Forged.fresh(rest.isEmpty() ? "New weapon" : rest);
                    case "open" -> {
                        Forged f = Forged.byId(rest);
                        if (f != null) forging = f;
                    }
                    case "name" -> forging = forging.withName(rest);
                    case "base" -> forging = forging.withBase(rest);
                    case "rarity" -> forging = forging.withRarity(Rarity.byKey(rest));
                    case "dice" -> forging = forging.withDice(Integer.parseInt(rest));
                    case "sides" -> forging = forging.withSides(rest.equals("%") ? 100 : Integer.parseInt(rest));
                    case "effect" -> forging = forging.withEffect(Border.byKey(a[1]), a.length < 3 || a[2].equals("on"));
                    case "tint" -> forging = forging.withTint(a[1], a[2].equals("none") ? -1
                            : Integer.parseInt(a[2].replace("#", ""), 16));
                    case "dicecolour" -> forging = forging.withDiceRgb(rest.equals("none") ? -1
                            : Integer.parseInt(rest.replace("#", ""), 16));
                    case "colour" -> forgeColour = Integer.parseInt(rest);
                    case "save" -> saveForged(false);
                    case "place" -> saveForged(true);
                    default -> { }
                }
            }
            case "listen" -> {
                // listen <song> [seconds] [play]: the Level tool's player on a song, at a time, playing
                if (a.length > 0) listen(a[0]);
                preview.open(Songs.byId(listening) == null ? "" : Songs.byId(listening).key());
                long until = System.nanoTime() + 10_000_000_000L;
                while (preview.loading() && System.nanoTime() < until) Thread.onSpinWait();
                if (a.length > 1) preview.seek(Double.parseDouble(a[1]));
                if (a.length > 2 && a[2].equals("play")) preview.play();
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
                // backdrop clear | backdrop <kind> [#colour height haze seed from to] [at=horizon|edge]
                //   [part=#colour] [extent=0.4]
                Sky s = level.sky();
                if (a[0].equals("clear")) {
                    level.setSky(s.withBackdrops(List.of()));
                    return true;
                }
                if (a[0].equals("pick")) {
                    // backdrop pick <n from 0> [part 0|1]: choose it in the Sky tool's list
                    backdrop = Integer.parseInt(a[1]);
                    skyTarget = 2;
                    backdropPart = a.length > 2 ? Integer.parseInt(a[2]) : 0;
                    return true;
                }
                if (a[0].equals("move")) {
                    // backdrop move <from> <to>
                    moveBackdrop(Integer.parseInt(a[1]), Integer.parseInt(a[2]));
                    return true;
                }
                Sky.Kind k = Sky.Kind.byKey(a[0]);
                if (k == null) return false;
                Sky.Backdrop b = Sky.Backdrop.of(k);
                String[] all = a;
                a = java.util.Arrays.stream(all).filter(t -> !t.contains("=")).toArray(String[]::new);
                if (a.length > 1) b = b.withColour(Integer.parseInt(a[1].replace("#", ""), 16));
                if (a.length > 2) b = k == Sky.Kind.OCEAN ? b.withDistance(Double.parseDouble(a[2]))
                        : b.withHeight(Double.parseDouble(a[2]));
                if (a.length > 3) b = b.withHaze(Double.parseDouble(a[3]));
                if (a.length > 4) b = b.withSeed(Integer.parseInt(a[4]));
                if (a.length > 6) b = b.withArc(Double.parseDouble(a[5]), Double.parseDouble(a[6]));
                for (String t : all) {
                    String[] kv = t.split("=", 2);
                    if (kv.length < 2) continue;
                    switch (kv[0]) {
                        case "at" -> b = b.withEdge(!kv[1].equals("horizon"));
                        case "part" -> b = b.withColour2(Integer.parseInt(kv[1].replace("#", ""), 16));
                        case "extent" -> b = b.withExtent(Double.parseDouble(kv[1]));
                        default -> { }
                    }
                }
                List<Sky.Backdrop> next = new ArrayList<>(s.backdrops());
                next.add(b);
                level.setSky(s.withBackdrops(next));
                dirty = true;
            }
            case "time" -> {
                // time <hour> | time day <minutes> | time run on|off
                String[] t = argument.trim().split("\\s+");
                Atmosphere air = level.sky().air();
                switch (t[0]) {
                    case "day" -> level.setSky(level.sky().withAir(air.withDayMinutes(Double.parseDouble(t[1]))));
                    case "run" -> {
                        runTime = t.length < 2 || !t[1].equals("off");
                        runHour = air.startHour();
                    }
                    default -> level.setSky(level.sky().withAir(air.withStartHour(Double.parseDouble(t[0]))));
                }
            }
            case "weather" -> {
                // weather <clouds|stars|aurora|mist|misth|rays|planet|rings|planetat|planetsize> <value...>
                String[] t = argument.trim().split("\\s+");
                Atmosphere w = level.sky().air();
                double v = t.length > 1 && !t[1].equals("on") && !t[1].equals("off") ? Double.parseDouble(t[1]) : 0;
                boolean on = t.length < 2 || !t[1].equals("off");
                w = switch (t[0]) {
                    case "clouds" -> w.withClouds(v);
                    case "stars" -> w.withStars(v);
                    case "aurora" -> w.withAurora(v);
                    case "mist" -> w.withMist(v);
                    case "misth" -> w.withMistHeight(v);
                    case "rays" -> w.withRays(v);
                    case "planet" -> w.withPlanet(on);
                    case "rings" -> w.withRings(on);
                    case "planetat" -> w.withPlanetAt(v, Double.parseDouble(t[2]));
                    case "planetsize" -> w.withPlanetSize(v);
                    default -> w;
                };
                level.setSky(level.sky().withAir(w));
                dirty = true;
            }
            // the level's life: life <firefly|cricket|frog|dragonfly|rabbit|...|iguana|animals> <0-3>,
            // life regrow <x>, life yield <x>, life reset, lifepage animals|critters
            case "life" -> {
                String[] w = argument.trim().split("\\s+");
                if (w[0].equals("reset")) {
                    level.setLife(new Life());
                } else if (w.length >= 2) {
                    Life l = level.life().copy();
                    double v = Double.parseDouble(w[1]);
                    CritterKind k = CritterKind.byKey(w[0]);
                    AnimalKind ak = AnimalKind.byKey(w[0]);
                    if (k != null) l.setDensity(k, v);
                    else if (ak != null) l.setDensity(ak, v);
                    else if (w[0].equals("animals")) {
                        for (AnimalKind each : AnimalKind.values()) l.setDensity(each, v);
                    } else if (w[0].equals("regrow")) l.setRegrow(v);
                    else if (w[0].equals("yield")) l.setYield(v);
                    else return false;
                    level.setLife(l);
                } else {
                    return false;
                }
                view.world().setLife(level.life());
                dirty = true;
            }
            case "lifepage" -> lifeAnimals = argument.trim().equals("animals");
            case "name" -> level.setName(argument);
            case "size" -> resize(Double.parseDouble(argument.trim()));
            case "save" -> save();
            // look <part> <#rrggbb|-> [pattern]: the selected buildings' part (the template's while placing)
            case "dress" -> {
                List<LevelObject> targets = tool == Tool.PLACE && template != null ? List.of(template) : selection;
                for (LevelObject o : targets) {
                    BuildingProfile p = profileOf(o);
                    if (p == null || p.part(a[0]) == null) continue;
                    BuildingLook look = BuildingLook.of(o.props().get("look"));
                    if (a.length > 1 && !a[1].equals("-")) look.setColour(a[0], BuildingLook.parseColour(a[1], 0xC0C0C0));
                    if (a.length > 2) look.setTexture(a[0], a[2]);
                    edit(o, "Look", () -> setLook(o, p, look));
                    for (int i = 0; i < p.parts().size(); i++) if (p.parts().get(i).key().equals(a[0])) lookPart = i;
                }
            }
            case "undo" -> undo();
            case "redo" -> redo();
            case "ui" -> hideUi = argument.trim().equals("off");
            case "fog" -> thinFog = !argument.trim().equals("game");
            case "playtest" -> playTest(a.length >= 2 ? new Vec3(Double.parseDouble(a[0]), 0,
                    Double.parseDouble(a[1])) : null);
            case "leave" -> toMenu();
            // inside: edit the inside of the selected building; floor <n>: that floor (1 the ground); outside: back
            case "inside" -> {
                if (selection.size() == 1 && hasInside(selection.get(0))) editInside(selection.get(0));
                else return false;
            }
            case "floor" -> floor(Integer.parseInt(argument.trim()) - 1);
            case "addfloor" -> addFloor();
            case "outside" -> goOutside();
            default -> {
                return false;
            }
        }
        return true;
    }
}
