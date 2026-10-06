package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.audio.WorldSounds;
import com.larsons.game.core.Autopilot;
import com.larsons.game.core.Game;
import com.larsons.game.core.Scene;
import com.larsons.game.input.Input;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Chest;
import com.larsons.game.world.Inventory;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Player;
import com.larsons.game.world.World;

import java.nio.file.Path;
import java.util.StringJoiner;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The demo: a blank 3D void with the character in the middle, a sword, a
 * battle axe, a longbow and a crossbow lying about, an ornate treasure chest
 * wreathed in magical smoke, and a few 3D props round the edge.
 *
 * <p>It is a viewer as much as a level. Walk, run, sprint, crouch, jump and
 * attack to see the states play live; pick the weapons up (each plays the
 * pick-up) into the hotbar and switch between them with 1–5 or the mouse
 * wheel to play each one's stance - its idle, walk, run, sprint, crouch,
 * attacks, parry and block; open the inventory with I; open the chest with
 * E; play the emotes with F1–F4. Or step through every state with {@code [ ]}
 * to loop one in place, {@code , .} or {@code T} to turn the character
 * through its eight directions, and {@code Tab} or a right-drag to swing the
 * camera through the three elevations. The HUD says exactly which sheet of
 * which layer is on screen, and whether it came from {@code assets/sprites}
 * or the fallback.
 */
public final class DemoScene implements Scene, Autopilot.Scriptable {

    /** 1–5: the hotbar's slots. */
    private static final int[] HOTBAR_KEYS = {GLFW_KEY_1, GLFW_KEY_2, GLFW_KEY_3, GLFW_KEY_4, GLFW_KEY_5};
    /** F1–F4: the emotes (the number keys are the hotbar's). */
    private static final int[] EMOTE_KEYS = {GLFW_KEY_F1, GLFW_KEY_F2, GLFW_KEY_F3, GLFW_KEY_F4};
    private static final AnimState[] EMOTES = {AnimState.EMOTE_LAUGH, AnimState.EMOTE_CRY,
            AnimState.EMOTE_SURPRISE, AnimState.EMOTE_ANGRY};
    /** Where the weapons lie at the start (the sword, unless it is already in hand). */
    private static final Vec3 SWORD_AT = new Vec3(1.4, 0, -2.2);
    private static final Object[][] WEAPONS_AT = {
            {ItemDef.BATTLE_AXE, new Vec3(-1.9, 0, -1.7)},
            {ItemDef.LONGBOW, new Vec3(2.5, 0, 1.0)},
            {ItemDef.CROSSBOW, new Vec3(-2.3, 0, 1.6)}};
    /** The chest: behind her at the start, between the axe and the sword, its front three-quarters on. */
    private static final Vec3 CHEST_AT = new Vec3(-0.3, 0, -3.4);
    private static final String CHEST = "ornate_chest";

    private final Game game;
    private final World world;
    private final OrbitCamera camera = new OrbitCamera(0.45, Math.toRadians(45), 6.5);
    private final PauseMenu pause;
    private final InventoryPanel inventory;
    private final WorldSounds sounds = new WorldSounds();
    private boolean turntable;
    private double turntableTimer;
    private double frameDt;
    private Player.Intent scripted;
    /** Scripted held controls: the attack (the bow's draw) and the block. */
    private boolean scriptDraw, scriptBlock;

    public DemoScene(Game game) {
        this.game = game;
        this.world = new World(game.wardrobe(), game.settings().inventorySlots);
        this.pause = new PauseMenu(game);
        this.inventory = new InventoryPanel(game, world);
        // The sword lies a couple of metres ahead, unless it is already in
        // hand; the stances' weapons lie round about.
        Player p = world.player();
        if (game.wardrobe().wearing(ItemDef.SWORD.carrySlot(), ItemDef.SWORD.id())
                || game.wardrobe().wearing(ItemDef.SWORD.carrySlot().twin(), ItemDef.SWORD.id())) {
            p.inventory().add(ItemDef.SWORD.id());
        } else {
            world.spawn(ItemDef.SWORD, SWORD_AT);
        }
        for (Object[] w : WEAPONS_AT) world.spawn((ItemDef) w[0], (Vec3) w[1]);
        world.addChest(new Chest(CHEST, CHEST_AT, SpriteView.headingShowing(Facing.SOUTH_WEST, camera.yaw())));
        world.equipSelected();
        p.snapHeading(SpriteView.headingShowing(Facing.SOUTH_EAST, camera.yaw()));
    }

    @Override
    public String name() { return "Demo"; }

    @Override
    public void enter() {
        camera.snapTo(pivot());
    }

    @Override
    public void exit() {
        sounds.stop();
        inventory.dispose();
    }

    private Vec3 pivot() {
        return world.player().feet().add(0, 0.9, 0);
    }

    @Override
    public void update(double dt) {
        frameDt = dt;
        Input in = game.window().input();
        if (pause.open()) {
            sounds.update(world, camera);
            return;
        }
        if (in.pressed(GLFW_KEY_ESCAPE)) {
            in.consumeKey(GLFW_KEY_ESCAPE);
            if (inventory.open()) inventory.close();
            else pause.open(PauseMenu.Page.MAIN);
            return;
        }
        if (in.pressed(GLFW_KEY_I)) inventory.toggle();
        Player p = world.player();

        // --- the hotbar: 1-5, the wheel (over a slot in the inventory, 1-5 swaps it there) ---
        for (int i = 0; i < HOTBAR_KEYS.length; i++) {
            if (!in.pressed(HOTBAR_KEYS[i])) continue;
            if (inventory.hovered() >= 0) inventory.swapWithHotbar(inventory.hovered(), i);
            else select(i);
        }
        boolean orbiting = !inventory.open()
                && (in.mouseDown(GLFW_MOUSE_BUTTON_RIGHT) || in.mouseDown(GLFW_MOUSE_BUTTON_MIDDLE));
        if (in.scroll() != 0 && !orbiting) {
            // a wheel notch away from you moves the selection left, as in most games
            int steps = (int) -Math.signum(in.scroll());
            select(Math.floorMod(p.inventory().selected() + steps, Inventory.HOTBAR));
            in.consumeScroll();
        }

        // --- camera -----------------------------------------------------------------
        if (orbiting) camera.rotate(-in.mouseDX() * 0.008, in.mouseDY() * 0.006);
        double orbit = 0, tilt = 0;
        if (in.down(GLFW_KEY_LEFT)) orbit += 1;
        if (in.down(GLFW_KEY_RIGHT)) orbit -= 1;
        if (in.down(GLFW_KEY_UP)) tilt += 1;
        if (in.down(GLFW_KEY_DOWN)) tilt -= 1;
        if (orbit != 0 || tilt != 0) camera.rotate(orbit * dt * 1.6, tilt * dt * 1.1);
        camera.zoom(in.scroll());                      // the wheel zooms while orbiting
        if (in.repeated(GLFW_KEY_EQUAL) || in.repeated(GLFW_KEY_KP_ADD)) camera.zoom(1);
        if (in.repeated(GLFW_KEY_MINUS) || in.repeated(GLFW_KEY_KP_SUBTRACT)) camera.zoom(-1);
        if (in.pressed(GLFW_KEY_TAB)) {
            int preset = camera.cyclePreset();
            game.toast("Camera: " + Elevation.values()[preset].label() + " sprites ("
                    + (int) Elevation.values()[preset].renderAngle() + "°)", Theme.HINT);
        }

        // --- viewer keys ------------------------------------------------------------
        if (in.repeated(GLFW_KEY_LEFT_BRACKET)) stepPreview(-1);
        if (in.repeated(GLFW_KEY_RIGHT_BRACKET)) stepPreview(1);
        if (in.pressed(GLFW_KEY_BACKSPACE)) p.preview(null);
        if (in.repeated(GLFW_KEY_COMMA)) p.turnSteps(1);
        if (in.repeated(GLFW_KEY_PERIOD)) p.turnSteps(-1);
        if (in.pressed(GLFW_KEY_T)) {
            turntable = !turntable;
            game.toast(turntable ? "Turntable on" : "Turntable off", Theme.HINT);
        }
        if (turntable) {
            turntableTimer += dt;
            if (turntableTimer > 0.8) {
                turntableTimer = 0;
                p.turnSteps(1);
            }
        }
        if (in.pressed(GLFW_KEY_H)) game.settings().showHud = !game.settings().showHud;
        if (in.pressed(GLFW_KEY_P)) game.settings().showProps = !game.settings().showProps;

        // --- items, the chest -----------------------------------------------------------
        if (in.pressed(GLFW_KEY_E) && !inventory.open()) use();
        if (in.pressed(GLFW_KEY_G) && !inventory.open()) drop();

        // --- the character (still while the inventory is open) ----------------------------
        Player.Intent intent = scripted != null ? scripted : inventory.open() ? Player.Intent.NONE : intent(in);
        p.update(dt, intent, durations());
        if (scripted != null) {
            // the one-shots happen once; with nothing left moving or held, the keys take over again
            scripted = held(scripted);
            if (scripted.moveX() == 0 && scripted.moveZ() == 0 && !scriptDraw && !scriptBlock) scripted = null;
        }
        world.tick(dt, (c, s) -> game.worldRenderer().chestDuration(c, s, camera));
        camera.update(dt, pivot());
        sounds.update(world, camera);
    }

    /** Select hotbar slot {@code i}: what is in it is taken up. */
    private void select(int i) {
        Inventory inv = world.player().inventory();
        if (i == inv.selected()) return;
        ItemDef d = world.select(i);
        Sounds.play(SoundKeys.ui("hotbar_select"));
        if (d != null) Sounds.play(SoundKeys.item(d.id(), "equip"));
    }

    /** E: open or shut the chest she stands by, else pick up what lies by her - whichever is nearer. */
    private void use() {
        Chest c = world.reachableChest();
        World.GroundItem g = world.reachable();
        if (c != null && (g == null || c.position().horizontalDistance(world.player().ground())
                < g.position.horizontalDistance(world.player().ground()))) {
            toggleChest(c);
        } else {
            pickUp();
        }
    }

    private void toggleChest(Chest c) {
        Player p = world.player();
        Vec3 d = c.position().sub(p.ground());
        p.turnTo(SpriteView.headingOf(d.x(), d.z()));
        c.toggle(s -> game.worldRenderer().chestDuration(c, s, camera));
    }

    /** How long each state lasts for the player as seen from here (from the sheets). */
    private java.util.function.ToDoubleFunction<AnimState> durations() {
        Player p = world.player();
        SpriteView view = currentView();
        return s -> LayerStack.duration(game.sprites(), p.wardrobe(), s, view);
    }

    /** A scripted intent after its frame: still moving and holding, nothing pressed. */
    private Player.Intent held(Player.Intent i) {
        return new Player.Intent(i.moveX(), i.moveZ(), i.run(), i.sprint(), false, false, scriptDraw, false,
                false, false, false, scriptBlock, false, null);
    }

    /** Loop the state {@code step} after (or before) the one on screen, through all of them. */
    private void stepPreview(int step) {
        Player p = world.player();
        AnimState[] all = AnimState.values();
        AnimState from = p.previewing() != null ? p.previewing() : p.state();
        AnimState s = all[Math.floorMod(from.ordinal() + step, all.length)];
        p.preview(s);
        game.toast("Preview: " + s.label() + "  (" + (s.ordinal() + 1) + " / " + all.length + ")", Theme.HINT);
    }

    private Player.Intent intent(Input in) {
        double f = 0, r = 0;
        if (in.down(GLFW_KEY_W)) f += 1;
        if (in.down(GLFW_KEY_S)) f -= 1;
        if (in.down(GLFW_KEY_D)) r += 1;
        if (in.down(GLFW_KEY_A)) r -= 1;
        Vec3 move = camera.groundForward().scale(f).add(camera.right().scale(r));
        boolean ctrl = in.down(GLFW_KEY_LEFT_CONTROL) || in.down(GLFW_KEY_RIGHT_CONTROL);
        boolean shift = in.down(GLFW_KEY_LEFT_SHIFT) || in.down(GLFW_KEY_RIGHT_SHIFT);
        boolean attack = in.pressed(GLFW_KEY_F) || in.mousePressed(GLFW_MOUSE_BUTTON_LEFT);
        boolean attackHeld = in.down(GLFW_KEY_F) || in.mouseDown(GLFW_MOUSE_BUTTON_LEFT);
        AnimState emote = null;
        for (int i = 0; i < EMOTE_KEYS.length; i++) if (in.pressed(EMOTE_KEYS[i])) emote = EMOTES[i];
        return new Player.Intent(move.x(), move.z(), shift && !ctrl, ctrl, in.pressed(GLFW_KEY_SPACE), attack,
                attackHeld, in.pressed(GLFW_KEY_C), in.pressed(GLFW_KEY_X), in.pressed(GLFW_KEY_R),
                in.pressed(GLFW_KEY_Q), in.down(GLFW_KEY_B), in.pressed(GLFW_KEY_V), emote);
    }

    private SpriteView currentView() {
        Player p = world.player();
        return SpriteView.of(camera.eye(), pivot(), p.heading(), camera.yaw());
    }

    /**
     * Reach for the nearest item: the pick-up plays, and it is hers as her
     * hand closes on it - into the first free slot, the hotbar first.
     */
    private void pickUp() {
        World.GroundItem g = world.reachable();
        if (g == null) return;
        if (world.player().inventory().isFull()) {
            game.toast("Your inventory is full — drop something first (G, or from the inventory: I)",
                    Theme.WARNING);
            return;
        }
        world.player().pickUp(() -> {
            if (!world.items().contains(g) || !world.pickUp(g)) return;
            game.saveWardrobe();
            ItemDef d = g.def;
            Sounds.play(SoundKeys.item(d.id(), "pickup"));
            var lib = game.sprites();
            var worn = world.player().wardrobe();
            var src = lib.source(worn.get(com.larsons.game.sprite.Slot.BODY), d.carrySlot(), d.id(),
                    worn.style(), false);
            boolean inHand = lib.resolve(src, d.stance().idle(false), Elevation.MIDDLE, Facing.SOUTH) != null
                    || world.player().wardrobe().get(com.larsons.game.sprite.Slot.BODY) == null;
            Inventory inv = world.player().inventory();
            int at = inv.indexOf(d.id());
            String where = Inventory.isHotbar(at) ? " — hotbar " + (at + 1) + (d.held() ? ", in both hands" : "")
                    : " — in your inventory (I)";
            game.toast("Picked up " + d.name() + (inHand ? where : " — no in-hand sheets for it yet"), Theme.OK);
        }, durations());
    }

    private void drop() {
        ItemDef d = world.dropHeld();
        if (d != null) {
            Sounds.play(SoundKeys.item(d.id(), "drop"));
            game.saveWardrobe();
            game.toast("Dropped " + d.name(), Theme.HINT);
        }
    }

    // --- drawing ---------------------------------------------------------------------

    @Override
    public void render() {
        var w = game.window();
        game.worldRenderer().render(world, camera, w.framebufferWidth(), w.framebufferHeight(),
                game.settings().showProps);

        Ui ui = game.ui();
        ui.begin();
        boolean hudShown = game.settings().showHud;
        if (hudShown) hud(ui);
        if (!pause.open()) {
            prompt(ui);
            inventory.drawHotbar(ui, frameDt, hudShown ? ui.height() - 14 - 22 * HELP.length - 4 : ui.height() - 10);
            if (!game.importer().visible()) inventory.draw(ui);
        }
        if (!game.importer().visible()) pause.draw(ui, frameDt, world.player());
        ui.end();
    }

    /** The help bar along the bottom of the HUD. */
    private static final String[] HELP = {
            "WASD move · Shift run · Ctrl sprint · C crouch · Space jump · Click/F attack (bow: hold to draw) · "
                    + "X heavy · R spin · Q parry · B block · V bash",
            "E pick up / open · G drop · I inventory · 1-5 or wheel hotbar · F1-F4 emote · [ ] preview · "
                    + "Backspace live · , . turn · T turntable · Tab height · Right-drag orbit · +/- zoom · "
                    + "H HUD · Esc menu"};

    private void prompt(Ui ui) {
        if (inventory.open()) return;
        String text;
        Chest c = world.reachableChest();
        World.GroundItem g = world.reachable();
        if (c != null && (g == null || c.position().horizontalDistance(world.player().ground())
                < g.position.horizontalDistance(world.player().ground()))) {
            text = "E   " + (c.isOpen() ? "Close the chest" : "Open the chest");
        } else if (g != null) {
            text = "E   Pick up " + g.def.name() + (world.player().crouched() ? " (crouched)" : "");
        } else {
            return;
        }
        float tw = ui.large.width(text) + 40;
        float x = (ui.width() - tw) / 2, y = ui.height() * 0.62f;
        ui.rect(x, y, tw, 44, Theme.withAlpha(Theme.PANEL, 0.85f));
        ui.outline(x, y, tw, 44, 1, Theme.ACCENT);
        ui.text(ui.large, text, x + 20, y + 9, Theme.TITLE);
    }

    private void hud(Ui ui) {
        Player p = world.player();
        LayerStack.Result r = game.worldRenderer().playerStack();
        if (r == null) return;
        var gpu = game.window().gpu();
        var lib = game.sprites();

        float x = 14, y = 12, lh = 20;
        // the layers: every one plays the same sheet as the body, so its name
        // is given once and only what differs is said per layer
        String sheet = r.layers().isEmpty() ? "" : sheetName(r.layers().get(0));
        for (LayerStack.Layer l : r.layers()) {
            if (l.slot() == com.larsons.game.sprite.Slot.BODY) sheet = sheetName(l);
        }
        StringJoiner layers = new StringJoiner("  ·  ");
        for (LayerStack.Layer l : r.layers()) {
            String src = sheetName(l);
            layers.add(l.slot().key() + (l.item() != null ? ":" + l.item() : "")
                    + (src.equals(sheet) ? "" : " ← " + src) + (l.mirrored() ? " (mirrored)" : ""));
        }
        if (r.loading()) layers.add("(holding while the next sheets load)");
        String layerText = "Layers  (" + sheet + ")  " + layers;
        float layerH = ui.paragraphHeight(ui.small, layerText, 640);
        ui.rect(x - 6, y - 6, 660, 128 + layerH + lh, Theme.withAlpha(Theme.BACKGROUND, 0.62f));
        ui.text(ui.body, "Demo void", x, y, Theme.ACCENT);
        ui.text(ui.small, String.format("%.0f fps", game.fps()), x + 590, y + 3, Theme.ITEM_DISABLED);
        y += lh + 4;
        ui.text(ui.small, "GPU  " + gpu.describe(), x, y, gpu.software() && game.settings().gpuRequired
                ? Theme.WARNING : Theme.ITEM);
        y += lh;
        SpriteView v = r.view();
        ui.text(ui.small, String.format("Camera  pitch %.0f°  yaw %.0f°  zoom %.1f m   →  %s sprites (%d°)",
                        Math.toDegrees(camera.pitch()), Math.toDegrees(camera.yaw()) % 360, camera.distance(),
                        v.elevation().label(), (int) v.elevation().renderAngle()),
                x, y, Theme.ITEM);
        y += lh;
        ui.text(ui.small, String.format("Player  %s%s%s  frame %d/%d @ %.0f fps  facing %s",
                        r.asked().label(), r.substituted() ? " (shown as " + r.state().label() + ": no sheets)" : "",
                        p.previewing() != null ? " (preview)" : "",
                        r.frame() + 1, r.frames(), r.fps(), v.facing().key().toUpperCase()),
                x, y, r.substituted() ? Theme.WARNING : Theme.ITEM);
        y += lh;
        StringJoiner carried = new StringJoiner(", ");
        for (String id : p.inventory()) {
            ItemDef d = ItemDef.byId(id);
            carried.add(d == null ? id : d.name());
        }
        Inventory inv = p.inventory();
        ui.text(ui.small, String.format("Stance  %s%s   ·   hotbar %d of %d slots   ·   carrying %s",
                p.stance().label(), p.crouched() ? ", crouched" : "", inv.selected() + 1, inv.size(),
                carried.length() == 0 ? "nothing" : carried), x, y, Theme.ITEM);
        y += lh;
        y += ui.paragraph(ui.small, layerText, x, y, 640, Theme.ITEM) + 2;
        ui.text(ui.small, String.format("Sprites  %d sheets resident (%d MB)  ·  %d loading  ·  scale %.2f",
                        lib.residentCount(), lib.residentBytes() >> 20, lib.pendingCount(), lib.scale()),
                x, y, Theme.HINT);

        elevationGauge(ui, ui.width() - 70, 20, v.elevationDegrees());

        if (inventory.open()) return;                 // (the inventory has its own line of help)
        String[] help = HELP;
        float hw = 0;
        for (String h : help) hw = Math.max(hw, ui.small.width(h) + 20);
        hw = Math.min(ui.width() - 28, hw);
        float top = ui.height() - 14 - 22 * help.length;
        ui.rect((ui.width() - hw) / 2, top, hw, 22 * help.length + 6, Theme.withAlpha(Theme.BACKGROUND, 0.62f));
        for (int i = 0; i < help.length; i++) {
            ui.textCentered(ui.small, help[i], ui.width() / 2f, top + 5 + 22 * i, Theme.HINT);
        }
    }

    private static String sheetName(LayerStack.Layer l) {
        return l.sheet().source().startsWith("fallback") ? "fallback"
                : Path.of(l.sheet().source()).getFileName().toString();
    }

    /**
     * The three vertical rendering zones as a gauge: 0° at the bottom, 90° at
     * the top, the camera's angle as a marker.
     */
    private void elevationGauge(Ui ui, float x, float y, double degrees) {
        float h = 180, w = 18;
        float[][] colors = {Theme.rgb(90, 140, 200, 0.85f), Theme.rgb(200, 170, 80, 0.85f),
                Theme.rgb(170, 110, 190, 0.85f)};
        double[][] bands = {{0, 33.5}, {33.5, 75}, {75, 90}};
        for (int i = 0; i < 3; i++) {
            float y0 = y + h - (float) (bands[i][1] / 90 * h), y1 = y + h - (float) (bands[i][0] / 90 * h);
            ui.rect(x, y0, w, y1 - y0, colors[i]);
            Elevation e = Elevation.values()[i];
            ui.text(ui.small, e.key(), x - 50, (y0 + y1) / 2 - 8,
                    Elevation.forAngle(degrees) == e ? Theme.ITEM_SELECTED : Theme.ITEM_DISABLED);
        }
        float my = y + h - (float) (degrees / 90 * h);
        ui.rect(x - 6, my - 1.5f, w + 12, 3, Theme.TITLE);
        ui.text(ui.small, String.format("%.0f°", degrees), x - 6, y + h + 6, Theme.HINT);
    }

    // --- scripting -------------------------------------------------------------------

    /** A scripted frame pressing these (and holding whatever is held). */
    private Player.Intent press(boolean attack, boolean crouch, boolean heavy, boolean spin, boolean parry,
                                AnimState emote) {
        return new Player.Intent(0, 0, false, false, false, attack, attack || scriptDraw, crouch, heavy, spin,
                parry, scriptBlock, false, emote);
    }

    /**
     * A scripted pick-up without walking there: into the inventory (the
     * sword into her hand too), and the one lying about gone from the ground.
     */
    private boolean give(ItemDef d) {
        Player p = world.player();
        if (p.inventory().add(d.id()) < 0) return false;
        world.items().stream().filter(g -> g.def == d).findFirst().ifPresent(world.items()::remove);
        if (!d.held()) p.wardrobe().set(p.wardrobe().hand() == Wardrobe.Hand.LEFT ? d.carrySlot().twin()
                : d.carrySlot(), d.id());
        return true;
    }

    @Override
    public boolean command(String command, String argument) {
        Player p = world.player();
        switch (command) {
            case "pitch" -> {
                camera.setDesiredPitch(Math.toRadians(Double.parseDouble(argument)));
                camera.snapTo(pivot());
            }
            case "yaw" -> {
                camera.setDesiredYaw(Math.toRadians(Double.parseDouble(argument)));
                camera.snapTo(pivot());
            }
            case "zoom" -> {
                camera.setDesiredDistance(Double.parseDouble(argument));
                camera.snapTo(pivot());
            }
            case "state" -> p.preview(argument.equals("live") ? null : AnimState.byKey(argument));
            case "face" -> {
                Facing f = Facing.byKey(argument);
                if (f != null) p.snapHeading(SpriteView.headingShowing(f, camera.yaw()));
            }
            case "move" -> {
                if (argument.isBlank() || argument.equals("stop")) {
                    scripted = null;
                } else {
                    String[] a = argument.split("[ ,]+");
                    boolean run = a.length > 2 && a[2].equals("run");
                    boolean sprint = a.length > 2 && a[2].equals("sprint");
                    Vec3 m = camera.groundForward().scale(Double.parseDouble(a[1]))
                            .add(camera.right().scale(Double.parseDouble(a[0])));
                    scripted = new Player.Intent(m.x(), m.z(), run, sprint, false, false);
                }
            }
            case "jump" -> scripted = new Player.Intent(0, 0, false, false, true, false);
            case "attack" -> scripted = new Player.Intent(0, 0, false, false, false, true);
            case "idle" -> {
                scripted = null;
                scriptDraw = scriptBlock = false;
            }
            // draw: the bow's draw, held until "loose"; block on|off: held
            case "draw" -> {
                scriptDraw = true;
                scripted = press(true, false, false, false, false, null);
            }
            case "loose" -> {
                scriptDraw = false;
                scripted = held(scripted == null ? Player.Intent.NONE : scripted);
            }
            case "block" -> {
                scriptBlock = !argument.equals("off");
                scripted = held(scripted == null ? Player.Intent.NONE : scripted);
            }
            case "crouch" -> {
                boolean want = !argument.equals("off");
                if (argument.isBlank() || p.crouched() != want) scripted = press(false, true, false, false, false, null);
            }
            case "heavy" -> scripted = press(false, false, true, false, false, null);
            case "spin" -> scripted = press(false, false, false, true, false, null);
            case "parry" -> scripted = press(false, false, false, false, true, null);
            case "bash" -> scripted = new Player.Intent(0, 0, false, false, false, false, false, false, false,
                    false, false, false, true, null);
            case "emote" -> {
                AnimState e = AnimState.byKey(argument.startsWith("emote_") ? argument : "emote_" + argument);
                if (e == null) return false;
                scripted = press(false, false, false, false, false, e);
            }
            case "stance" -> {
                Stance s = Stance.byKey(argument);
                if (s == null) return false;
                // (scripts may take up a stance without the weapon lying about first)
                ItemDef d = ItemDef.of(s);
                if (d != null && !p.inventory().contains(d.id())) give(d);
                world.wield(s);
            }
            // the inventory: inventory [open|close], hotbar <1-5>, slots <n>, give <item>
            case "inventory" -> {
                if (argument.equals("close")) inventory.close();
                else if (argument.equals("open")) inventory.show();
                else inventory.toggle();
            }
            case "hotbar" -> select(Integer.parseInt(argument.trim()) - 1);
            case "slots" -> {
                for (String spilled : p.inventory().resize(Integer.parseInt(argument.trim()))) {
                    world.dropItem(spilled);
                }
                world.equipSelected();
            }
            case "give" -> {
                ItemDef d = ItemDef.byId(argument.trim());
                if (d == null || !give(d)) return false;
                world.equipSelected();
            }
            // the chest: chest (open it, or shut it), and where she stands to: chest goto
            case "chest" -> {
                if (world.chests().isEmpty()) return false;
                Chest c = world.chests().get(0);
                if (argument.equals("goto")) {
                    p.setPosition(c.position().add(SpriteView.headingVector(c.heading()).scale(1.3)));
                    camera.snapTo(pivot());
                } else {
                    toggleChest(c);
                }
            }
            case "teleport" -> {
                String[] a = argument.split("[ ,]+");
                p.setPosition(new Vec3(Double.parseDouble(a[0]), 0, Double.parseDouble(a[1])));
                camera.snapTo(pivot());
            }
            case "pickup" -> pickUp();
            case "dropitem" -> drop();
            case "pause" -> pause.open(PauseMenu.Page.valueOf(
                    argument.isBlank() ? "MAIN" : argument.toUpperCase()));
            case "resume" -> pause.close();
            case "hud" -> game.settings().showHud = !argument.equals("off");
            case "props" -> game.settings().showProps = !argument.equals("off");
            case "style" -> p.wardrobe().setStyle(Wardrobe.Style.byKey(argument));
            case "hand" -> p.wardrobe().setHand(Wardrobe.Hand.byKey(argument));
            case "wear" -> {
                // wear <slot> [item]: put an item on, or take the slot's off
                String[] a = argument.trim().split("\\s+", 2);
                com.larsons.game.sprite.Slot slot = com.larsons.game.sprite.Slot.byKey(a[0]);
                if (slot == null) return false;
                p.wardrobe().set(slot, a.length > 1 ? a[1] : null);
            }
            case "colour", "color" -> {
                // colour <slot> [option]: the slot's colour (body: the skin), or its own
                String[] a = argument.trim().split("\\s+", 2);
                com.larsons.game.sprite.Slot slot = com.larsons.game.sprite.Slot.byKey(a[0]);
                if (slot == null) return false;
                p.wardrobe().setColour(slot, a.length > 1 ? a[1] : null);
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
