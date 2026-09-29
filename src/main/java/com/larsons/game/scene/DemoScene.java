package com.larsons.game.scene;

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
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Player;
import com.larsons.game.world.World;

import java.nio.file.Path;
import java.util.StringJoiner;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The demo: a blank 3D void with the character in the middle, a sword lying
 * nearby, and a few 3D props round the edge.
 *
 * <p>It is a viewer as much as a level. Walk, run, sprint, jump and attack to
 * see the states play live; or press 1–6 to loop one in place, {@code , .} or
 * {@code T} to turn the character through its eight directions, and
 * {@code Tab} or a right-drag to swing the camera through the three
 * elevations. The HUD says exactly which sheet of which layer is on screen,
 * and whether it came from {@code assets/sprites} or the fallback.
 */
public final class DemoScene implements Scene, Autopilot.Scriptable {

    private static final int[] STATE_KEYS = {GLFW_KEY_1, GLFW_KEY_2, GLFW_KEY_3, GLFW_KEY_4,
            GLFW_KEY_5, GLFW_KEY_6};

    private final Game game;
    private final World world;
    private final OrbitCamera camera = new OrbitCamera(0.45, Math.toRadians(45), 6.5);
    private final PauseMenu pause;
    private boolean turntable;
    private double turntableTimer;
    private double frameDt;
    private Player.Intent scripted;

    public DemoScene(Game game) {
        this.game = game;
        this.world = new World(game.wardrobe());
        this.pause = new PauseMenu(game);
        // The sword lies a couple of metres ahead, unless it is already in hand.
        Player p = world.player();
        if (game.wardrobe().wearing(ItemDef.SWORD.carrySlot(), ItemDef.SWORD.id())) {
            p.inventory().add(ItemDef.SWORD.id());
        } else {
            world.spawn(ItemDef.SWORD, new Vec3(1.4, 0, -2.2));
        }
        p.snapHeading(SpriteView.headingShowing(Facing.SOUTH_EAST, camera.yaw()));
    }

    @Override
    public String name() { return "Demo"; }

    @Override
    public void enter() {
        camera.snapTo(pivot());
    }

    private Vec3 pivot() {
        return world.player().feet().add(0, 0.9, 0);
    }

    @Override
    public void update(double dt) {
        frameDt = dt;
        Input in = game.window().input();
        if (pause.open()) return;
        if (in.pressed(GLFW_KEY_ESCAPE)) {
            in.consumeKey(GLFW_KEY_ESCAPE);
            pause.open(PauseMenu.Page.MAIN);
            return;
        }
        Player p = world.player();

        // --- camera -----------------------------------------------------------------
        if (in.mouseDown(GLFW_MOUSE_BUTTON_RIGHT) || in.mouseDown(GLFW_MOUSE_BUTTON_MIDDLE)) {
            camera.rotate(-in.mouseDX() * 0.008, in.mouseDY() * 0.006);
        }
        double orbit = 0, tilt = 0;
        if (in.down(GLFW_KEY_LEFT)) orbit += 1;
        if (in.down(GLFW_KEY_RIGHT)) orbit -= 1;
        if (in.down(GLFW_KEY_UP)) tilt += 1;
        if (in.down(GLFW_KEY_DOWN)) tilt -= 1;
        if (orbit != 0 || tilt != 0) camera.rotate(orbit * dt * 1.6, tilt * dt * 1.1);
        camera.zoom(in.scroll());
        if (in.repeated(GLFW_KEY_EQUAL) || in.repeated(GLFW_KEY_KP_ADD)) camera.zoom(1);
        if (in.repeated(GLFW_KEY_MINUS) || in.repeated(GLFW_KEY_KP_SUBTRACT)) camera.zoom(-1);
        if (in.pressed(GLFW_KEY_TAB)) {
            int preset = camera.cyclePreset();
            game.toast("Camera: " + Elevation.values()[preset].label() + " sprites ("
                    + (int) Elevation.values()[preset].renderAngle() + "°)", Theme.HINT);
        }

        // --- viewer keys ------------------------------------------------------------
        for (int i = 0; i < STATE_KEYS.length; i++) {
            if (in.pressed(STATE_KEYS[i])) p.preview(AnimState.values()[i]);
        }
        if (in.pressed(GLFW_KEY_0)) p.preview(null);
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

        // --- items ------------------------------------------------------------------
        if (in.pressed(GLFW_KEY_E)) pickUp();
        if (in.pressed(GLFW_KEY_G)) drop();

        // --- the character ----------------------------------------------------------
        Player.Intent intent = scripted != null ? scripted : intent(in);
        SpriteView view = currentView();
        p.update(dt, intent, s -> LayerStack.duration(game.sprites(), p.wardrobe(), s, view));
        if (scripted != null && (scripted.jump() || scripted.attack())) scripted = null; // one-shots
        world.tick(dt);
        camera.update(dt, pivot());
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
        return new Player.Intent(move.x(), move.z(), shift && !ctrl, ctrl, in.pressed(GLFW_KEY_SPACE), attack);
    }

    private SpriteView currentView() {
        Player p = world.player();
        return SpriteView.of(camera.eye(), pivot(), p.heading(), camera.yaw());
    }

    private void pickUp() {
        World.GroundItem g = world.reachable();
        if (g == null) return;
        world.pickUp(g);
        game.saveWardrobe();
        boolean inHand = game.sprites().resolve(g.def.carrySlot(), g.def.id(), AnimState.IDLE,
                Elevation.MIDDLE, Facing.SOUTH) != null || world.player().wardrobe().get(
                com.larsons.game.sprite.Slot.BODY) == null;
        game.toast("Picked up " + g.def.name() + (inHand ? " — it's in your "
                + g.def.carrySlot().label().toLowerCase() : " — no in-hand sheets for it yet"), Theme.OK);
    }

    private void drop() {
        ItemDef d = world.dropHeld();
        if (d != null) {
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
        if (game.settings().showHud) hud(ui);
        prompt(ui);
        if (!game.importer().visible()) pause.draw(ui, frameDt, world.player());
        ui.end();
    }

    private void prompt(Ui ui) {
        World.GroundItem g = world.reachable();
        if (g == null || pause.open()) return;
        String text = "E   Pick up " + g.def.name();
        float tw = ui.large.width(text) + 40;
        float x = (ui.width() - tw) / 2, y = ui.height() * 0.72f;
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
        ui.rect(x - 6, y - 6, 660, 170, Theme.withAlpha(Theme.BACKGROUND, 0.62f));
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
        ui.text(ui.small, String.format("Player  %s%s  frame %d/%d @ %.0f fps  facing %s",
                        r.state().label(), p.previewing() != null ? " (preview)" : "",
                        r.frame() + 1, r.frames(), r.fps(), v.facing().key().toUpperCase()),
                x, y, Theme.ITEM);
        y += lh;
        StringJoiner layers = new StringJoiner("  ·  ");
        for (LayerStack.Layer l : r.layers()) {
            String src = l.sheet().source().startsWith("fallback") ? "fallback"
                    : Path.of(l.sheet().source()).getFileName().toString();
            layers.add(l.slot().key() + (l.item() != null ? ":" + l.item() : "") + " ← " + src
                    + (l.mirrored() ? " (mirrored)" : ""));
        }
        if (r.loading()) layers.add("(holding while the next sheets load)");
        ui.paragraph(ui.small, "Layers  " + layers, x, y, 640, Theme.ITEM);
        y += lh * 2;
        ui.text(ui.small, String.format("Sprites  %d sheets resident (%d MB)  ·  %d loading  ·  scale %.2f",
                        lib.residentCount(), lib.residentBytes() >> 20, lib.pendingCount(), lib.scale()),
                x, y, Theme.HINT);

        elevationGauge(ui, ui.width() - 70, 20, v.elevationDegrees());

        String help = "WASD move · Shift run · Ctrl sprint · Space jump · Click/F attack · E pick up · "
                + "G drop · Right-drag orbit · Wheel zoom · Tab height · 1-6 preview · , . turn · "
                + "T turntable · H HUD · Esc menu";
        float hw = Math.min(ui.width() - 28, ui.small.width(help) + 20);
        ui.rect((ui.width() - hw) / 2, ui.height() - 34, hw, 26, Theme.withAlpha(Theme.BACKGROUND, 0.62f));
        ui.textCentered(ui.small, help, ui.width() / 2f, ui.height() - 29, Theme.HINT);
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
            case "idle" -> scripted = null;
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
            default -> {
                return false;
            }
        }
        return true;
    }
}
