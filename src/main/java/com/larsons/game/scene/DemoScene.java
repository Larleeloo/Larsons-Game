package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.audio.WorldSounds;
import com.larsons.game.combat.CombatHud;
import com.larsons.game.combat.Combat;
import com.larsons.game.combat.CombatTuning;
import com.larsons.game.combat.Dummy;
import com.larsons.game.combat.FlyingDummy;
import com.larsons.game.combat.Target;
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
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Player;
import com.larsons.game.world.SceneryLayout;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.Tier;
import com.larsons.game.world.Wearables;
import com.larsons.game.world.World;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The demo: the character in the middle of a meadow - grass underfoot,
 * tufts blowing in the breeze, oaks and rocks round about ({@link
 * SceneryLayout}) - with a sword, a battle axe, a longbow, a crossbow, a
 * crystal staff, a wand, a mace and a dagger lying about, an ornate treasure
 * chest wreathed in magical smoke and three training dummies to hit.
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
 *
 * <p><b>The highlands</b>, up the north path ({@link Terrain#highlands}): a
 * plateau with a chest on top - up the ramp the long way, or up the vine
 * cliff on its east side (climbing spends stamina; she steps up over its lip,
 * and walking off its top she steps down over it and climbs down), down its
 * west ledge the quick way (a fall that hurts, or a roll that does not); two dummies below
 * the ledge to shoot at from the high ground, two below the cliff, a hill to
 * climb for the view and a hollow. Two <b>flying dummies</b> fly their rounds
 * - one just north of the spawn, one over the hollow by the cliff - and dive
 * at her when she comes near: L locks on to them (or to anything that can be
 * hit), the bow, the crossbow, the staff and the wand shoot up at them from
 * the aimed-up pose. The camera rises on the peaks and drops in the hollow
 * by itself (O).
 *
 * <p>Typing the debug code on the num-pad ({@link DebugCode}) offers every
 * cosmetic in the wardrobe and puts "Clear the ground" in the pause menu.
 */
public final class DemoScene implements Scene, Autopilot.Scriptable {

    /** 1–5: the hotbar's slots. */
    private static final int[] HOTBAR_KEYS = {GLFW_KEY_1, GLFW_KEY_2, GLFW_KEY_3, GLFW_KEY_4, GLFW_KEY_5};
    /** F1–F4: the emotes (the number keys are the hotbar's). */
    private static final int[] EMOTE_KEYS = {GLFW_KEY_F1, GLFW_KEY_F2, GLFW_KEY_F3, GLFW_KEY_F4};
    private static final AnimState[] EMOTES = {AnimState.EMOTE_LAUGH, AnimState.EMOTE_CRY,
            AnimState.EMOTE_SURPRISE, AnimState.EMOTE_ANGRY};
    /**
     * Where the weapons lie at the start (the sword, unless it is already in
     * hand): each in its basic tier, the crystal staff as it is drawn - the
     * Magic Staff.
     */
    private static final Vec3 SWORD_AT = new Vec3(1.4, 0, -2.2);
    private static final Object[][] WEAPONS_AT = {
            {Item.of(ItemDef.BATTLE_AXE), new Vec3(-1.9, 0, -1.7)},
            {Item.of(ItemDef.LONGBOW), new Vec3(2.5, 0, 1.0)},
            {Item.of(ItemDef.CROSSBOW), new Vec3(-2.3, 0, 1.6)},
            {Item.of(ItemDef.CRYSTAL_STAFF, Tier.MAGIC), new Vec3(2.6, 0, -0.9)},
            {Item.of(ItemDef.WAND), new Vec3(0.6, 0, 2.4)},
            {Item.of(ItemDef.MACE), new Vec3(-2.8, 0, -0.2)},
            {Item.of(ItemDef.DAGGER), new Vec3(-1.0, 0, 2.9)}};
    /**
     * The training dummies, each facing where she starts: one a few steps to
     * her right, one farther off behind it, one off to her left - targets
     * for the bow and the crossbow, and three to keep alive for Bonded.
     */
    private static final Vec3[] DUMMIES_AT = {new Vec3(4.2, 0, -1.0), new Vec3(5.6, 0, -4.0), new Vec3(-5.4, 0, 0.4)};
    /**
     * The highlands' dummies, each facing the plateau: two below its west
     * ledge (shot at from its top, they are on the low ground), two below
     * the vine cliff.
     */
    private static final Vec3[] HIGHLAND_DUMMIES_AT = {new Vec3(-3.2, 0, -31.0), new Vec3(-3.6, 0, -27.0),
            new Vec3(16.8, 0, -37.5), new Vec3(17.2, 0, -33.5)};
    /** Where the highlands' dummies look: the plateau's middle. */
    private static final Vec3 PLATEAU_MIDDLE = new Vec3(6, 0, -36);
    /** The plateau's chest: at its north-east corner, looking down the ramp. */
    private static final Vec3 PLATEAU_CHEST_AT = new Vec3(9.5, 0, -39.0);
    /** The middles of the flying dummies' rounds: just north of the spawn, and over the hollow by the cliff. */
    private static final Vec3[] FLIERS_AT = {new Vec3(-2.0, 0, -9.5), new Vec3(16.0, 0, -29.0)};
    /** The chest: behind her at the start, between the axe and the sword, its front three-quarters on. */
    private static final Vec3 CHEST_AT = new Vec3(-0.3, 0, -3.4);
    private static final String CHEST = "ornate_chest";
    /**
     * How much ground round the spawn, the chest, each weapon's spot and each
     * dummy the scenery leaves bare, in metres - round the chest, enough for
     * the fan its loot lands in (up to 2.1 m out, {@code World}) and room to
     * walk; round a dummy, room to circle it and swing.
     */
    private static final double SPAWN_CLEAR = 1.2, CHEST_CLEAR = 2.6, ITEM_CLEAR = 0.6, DUMMY_CLEAR = 1.8;

    private final Game game;
    private final World world;
    private final OrbitCamera camera = new OrbitCamera(0.45, Math.toRadians(45), 6.5);
    private final PauseMenu pause;
    private final InventoryPanel inventory;
    private final WorldSounds sounds = new WorldSounds();
    private final CombatHud combatHud;
    private final DebugCode debugCode = new DebugCode();
    /** When the heavy attack's "ready in" was last said (so held-down presses are not a pile of toasts). */
    private double heavyToast = Double.NEGATIVE_INFINITY;
    private boolean turntable;
    private double turntableTimer;
    private double frameDt;
    private Player.Intent scripted;
    /** Scripted held controls: the attack (the bow's draw) and the block. */
    private boolean scriptDraw, scriptBlock;
    /** A scripted roll: the dodge key held until the dash has turned into the roll. */
    private boolean scriptRoll;

    public DemoScene(Game game) {
        this.game = game;
        this.world = new World(game.wardrobe(), game.settings().inventorySlots);
        world.setTerrain(game.terrain());
        this.pause = new PauseMenu(game, world);
        this.inventory = new InventoryPanel(game, world);
        // The sword lies a couple of metres ahead, unless it is already in
        // hand; the stances' weapons lie round about. What she wears is hers
        // (so it comes off when it leaves her inventory).
        Player p = world.player();
        if (game.wardrobe().wearing(ItemDef.SWORD.carrySlot(), ItemDef.SWORD.id())
                || game.wardrobe().wearing(ItemDef.SWORD.carrySlot().twin(), ItemDef.SWORD.id())) {
            p.inventory().add(Item.of(ItemDef.SWORD));
        } else {
            world.spawn(ItemDef.SWORD, SWORD_AT);
        }
        world.ownWhatIsWorn();
        for (Object[] w : WEAPONS_AT) world.spawn((Item) w[0], (Vec3) w[1]);
        world.addChest(new Chest(CHEST, CHEST_AT, SpriteView.headingShowing(Facing.SOUTH_WEST, camera.yaw())));
        CombatTuning tuning = CombatTuning.load(game.settings().assets);
        world.combat().use(tuning);
        world.combat().setMana(tuning.maxMana);
        p.setHeavyCooldown(tuning.heavyCooldown);
        p.setDodgeCooldown(tuning.dodgeCooldown);
        for (Vec3 at : DUMMIES_AT) addDummy(at);
        for (Vec3 at : HIGHLAND_DUMMIES_AT) {
            world.addDummy(new Dummy(world.terrain().onGround(at),
                    SpriteView.headingOf(PLATEAU_MIDDLE.x() - at.x(), PLATEAU_MIDDLE.z() - at.z()),
                    tuning.dummyHealth, tuning.dummyDownSeconds));
        }
        world.addChest(new Chest(CHEST, world.terrain().onGround(PLATEAU_CHEST_AT),
                SpriteView.headingOf(-0.6, 1)));
        for (int i = 0; i < FLIERS_AT.length; i++) addFlier(FLIERS_AT[i], i * 2.1);
        combatHud = new CombatHud(game.worldRenderer().combat().fx());
        world.equipSelected();
        world.setScenery(game.scenery());
        p.snapHeading(SpriteView.headingShowing(Facing.SOUTH_EAST, camera.yaw()));
    }

    /** A training dummy at {@code at}, facing where she starts. */
    private Dummy addDummy(Vec3 at) {
        CombatTuning t = world.combat().tuning();
        return world.addDummy(new Dummy(world.terrain().onGround(at), SpriteView.headingOf(-at.x(), -at.z()),
                t.dummyHealth, t.dummyDownSeconds));
    }

    /** A flying dummy on its rounds round {@code centre}, starting {@code start} radians round them. */
    private FlyingDummy addFlier(Vec3 centre, double start) {
        CombatTuning t = world.combat().tuning();
        return world.addFlier(new FlyingDummy(centre, start, t.dummyHealth * 0.6, t.dummyDownSeconds,
                world.terrain()));
    }

    /**
     * The ground the scenery must leave bare ({@link SceneryLayout}): where
     * she starts, the chest, the spot each weapon lies on and each dummy.
     */
    public static List<SceneryLayout.Clearing> clearings() {
        List<SceneryLayout.Clearing> clear = new ArrayList<>();
        clear.add(new SceneryLayout.Clearing(0, 0, SPAWN_CLEAR));
        clear.add(new SceneryLayout.Clearing(CHEST_AT.x(), CHEST_AT.z(), CHEST_CLEAR));
        clear.add(new SceneryLayout.Clearing(SWORD_AT.x(), SWORD_AT.z(), ITEM_CLEAR));
        for (Object[] w : WEAPONS_AT) {
            Vec3 at = (Vec3) w[1];
            clear.add(new SceneryLayout.Clearing(at.x(), at.z(), ITEM_CLEAR));
        }
        for (Vec3 at : DUMMIES_AT) clear.add(new SceneryLayout.Clearing(at.x(), at.z(), DUMMY_CLEAR));
        for (Vec3 at : HIGHLAND_DUMMIES_AT) clear.add(new SceneryLayout.Clearing(at.x(), at.z(), DUMMY_CLEAR));
        clear.add(new SceneryLayout.Clearing(PLATEAU_CHEST_AT.x(), PLATEAU_CHEST_AT.z(), CHEST_CLEAR));
        // room to land below the ledge, and to stand at the foot of the cliff
        Terrain t = Terrain.highlands();
        for (String place : new String[]{"cliff", "hollow"}) {
            Terrain.Place p = t.place(place);
            clear.add(new SceneryLayout.Clearing(p.x(), p.z(), 3.0));
        }
        clear.add(new SceneryLayout.Clearing(t.place("ledge").x() - 3, t.place("ledge").z(), 3.0));
        return clear;
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
        world.setScenery(game.scenery());           // (P, or Pause → Settings, may have hidden it)
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
        for (int key = GLFW_KEY_KP_0; key <= GLFW_KEY_KP_9; key++) {
            if (in.pressed(key) && debugCode.type(DebugCode.digit(key), world.time())) toggleDebug();
        }

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
        if (in.pressed(GLFW_KEY_L)) toggleLock();
        if (in.pressed(GLFW_KEY_K)) nextLock();
        if (in.pressed(GLFW_KEY_O)) {
            game.settings().autoCamera = !game.settings().autoCamera;
            game.toast(game.settings().autoCamera ? "Camera height: automatic (higher on peaks, lower in hollows)"
                    : "Camera height: fixed", Theme.HINT);
        }
        if (in.pressed(GLFW_KEY_P)) game.settings().showProps = !game.settings().showProps;
        if (in.pressed(GLFW_KEY_F9)) setClassic(!game.settings().classicAnimations);

        // --- items, the chest -----------------------------------------------------------
        if (in.pressed(GLFW_KEY_E) && !inventory.open()) use();
        if (in.pressed(GLFW_KEY_G) && !inventory.open()) drop();

        // --- the character (still while the inventory is open) ----------------------------
        Player.Intent intent = scripted != null ? scripted : inventory.open() ? Player.Intent.NONE : intent(in);
        if (intent.heavy() && p.heavyWait() > 0 && p.stance().heavy() != null && world.time() - heavyToast > 1.5) {
            heavyToast = world.time();
            game.toast("Heavy attack ready in " + (int) Math.ceil(p.heavyWait()) + " s", Theme.HINT);
        }
        p.update(dt, intent, durations());
        if (scriptRoll && p.action() != AnimState.DASH) scriptRoll = false;   // rolling (or it never started)
        if (scripted != null) {
            // the one-shots happen once; with nothing left moving or held, the keys take over again
            scripted = held(scripted);
            if (scripted.moveX() == 0 && scripted.moveZ() == 0 && !scriptDraw && !scriptBlock && !scriptRoll) {
                scripted = null;
            }
        }
        world.tick(dt, (c, s) -> game.worldRenderer().chestDuration(c, s, camera));
        lockedCamera(dt);
        autoHeight();
        camera.update(dt, follow());
        camera.keepAbove(world.terrain(), 0.8);
        sounds.update(world, camera);
    }

    // --- the lock-on and the camera on the hills -----------------------------------------

    /** L: lock on to what is most nearly in the middle of the view - or let go. */
    private void toggleLock() {
        Combat c = world.combat();
        if (c.locked() != null) {
            c.lock(null);
            c.sound("combat/lock_off", world.player().ground());
            return;
        }
        Target t = Combat.lockCandidate(camera.eye(), camera.forward(), world.player().feet(), world.targets(), null);
        if (t == null) game.toast("Nothing to lock on to within " + (int) Combat.LOCK_RANGE + " m", Theme.HINT);
        c.lock(t);
    }

    /** K: on to the next target round, left to right (or lock on, if nothing is locked). */
    private void nextLock() {
        Combat c = world.combat();
        if (c.locked() == null) {
            toggleLock();
            return;
        }
        Target t = Combat.lockCandidate(camera.eye(), camera.forward(), world.player().feet(), world.targets(),
                c.locked());
        if (t != null) c.lock(t);
    }

    /**
     * Locked on: the camera turns, gently, to look past her at what she is
     * locked on to (so W walks at it), as far as keeps it in view.
     */
    private void lockedCamera(double dt) {
        Target t = world.combat().locked();
        if (t == null) return;
        Vec3 d = t.position().sub(world.player().ground());
        if (d.horizontalLength() < 1.5) return;
        double want = Math.atan2(-d.x(), -d.z());
        double diff = Math.atan2(Math.sin(want - camera.yaw()), Math.cos(want - camera.yaw()));
        camera.setDesiredYaw(camera.yaw() + diff * (1 - Math.exp(-dt * 2.5)));
    }

    /**
     * What the camera follows: her pivot - drawn a third of the way toward
     * what she is locked on to (at most 2.5 m across, 0.6 m up and 1.5 m
     * down), so both stay in view and she stays clear of the hotbar.
     */
    private Vec3 follow() {
        Vec3 pivot = pivot();
        Target t = world.combat().locked();
        if (t == null) return pivot;
        Vec3 d = t.position().add(0, t.height() / 2, 0).sub(pivot).scale(0.33);
        double flat = d.horizontalLength();
        if (flat > 2.5) d = new Vec3(d.x() * 2.5 / flat, d.y(), d.z() * 2.5 / flat);
        // (only a little up toward a flier: she stays clear of the hotbar)
        return pivot.add(d.x(), Math.max(-1.5, Math.min(0.6, d.y())), d.z());
    }

    /** How far the camera rises on a peak (degrees, and zoomed back) and drops in a hollow. */
    static final double PEAK_PITCH = 14, HOLLOW_PITCH = 12, PEAK_ZOOM = 0.35;

    /**
     * The automatic camera height: how far she stands above (or below) the
     * ground round her - a ring 9 m out - raises the camera on a peak, for
     * the view, and lowers it in a hollow.
     */
    private void autoHeight() {
        if (!game.settings().autoCamera) {
            camera.setAutoHeight(0, 1);
            return;
        }
        double k = prominence(world.terrain(), world.player().ground());
        k = k > 0 ? Math.min(1, k / 5) : Math.max(-1, k / 2.5);
        camera.setAutoHeight(Math.toRadians(k > 0 ? k * PEAK_PITCH : k * HOLLOW_PITCH), 1 + PEAK_ZOOM * Math.max(0, k));
    }

    /** How far the ground at {@code at} stands above the average of the ground round it, 9 m out, metres. */
    static double prominence(Terrain t, Vec3 at) {
        if (t.flat()) return 0;
        double sum = 0;
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            sum += t.height(at.x() + 9 * Math.cos(a), at.z() + 9 * Math.sin(a));
        }
        return t.height(at.x(), at.z()) - sum / n;
    }

    /**
     * The debug code was typed: every cosmetic offered in the wardrobe and
     * the pause menu's "Clear the ground" - or, typed again, back to normal.
     */
    private void toggleDebug() {
        var s = game.settings();
        s.debugTools = !s.debugTools;
        Wearables.setEverything(s.debugTools);
        game.toast(s.debugTools ? "Debug on: every cosmetic unlocked · Pause menu → Clear the ground"
                : "Debug off", Theme.ACCENT);
    }

    /** Select hotbar slot {@code i}: what is in it is taken up. */
    private void select(int i) {
        Inventory inv = world.player().inventory();
        if (i == inv.selected()) return;
        Item d = world.select(i);
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
                false, false, false, scriptBlock, false, null, false, scriptRoll);
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
        // the dodge: a tap dashes, held it turns into a roll
        boolean dodge = in.pressed(GLFW_KEY_LEFT_ALT) || in.pressed(GLFW_KEY_Z);
        boolean dodgeHeld = in.down(GLFW_KEY_LEFT_ALT) || in.down(GLFW_KEY_Z);
        return new Player.Intent(move.x(), move.z(), shift && !ctrl, ctrl, in.pressed(GLFW_KEY_SPACE), attack,
                attackHeld, in.pressed(GLFW_KEY_C), in.pressed(GLFW_KEY_X), in.pressed(GLFW_KEY_R),
                in.pressed(GLFW_KEY_Q), in.down(GLFW_KEY_B), in.pressed(GLFW_KEY_V), emote, dodge, dodgeHeld);
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
            if (d.isWearable()) {
                game.toast("Picked up " + g.item.fullName() + " (" + g.item.rarity().label() + ")"
                        + " — wear it from the wardrobe (Esc)", g.item.rarity().colour(1f));
                return;
            }
            var lib = game.sprites();
            var worn = world.player().wardrobe();
            var src = lib.source(worn.get(com.larsons.game.sprite.Slot.BODY), d.carrySlot(), d.id(),
                    worn.style(), false);
            boolean inHand = lib.resolve(src, d.stance().idle(false), Elevation.MIDDLE, Facing.SOUTH) != null
                    || world.player().wardrobe().get(com.larsons.game.sprite.Slot.BODY) == null;
            Inventory inv = world.player().inventory();
            int at = inv.indexOf(g.item);
            String where = Inventory.isHotbar(at) ? " — hotbar " + (at + 1) + (d.held() ? ", in both hands" : "")
                    : " — in your inventory (I)";
            game.toast("Picked up " + g.item.fullName() + " (" + g.item.rarity().label() + ")"
                    + (inHand ? where : " — no in-hand sheets for it yet"), g.item.rarity().colour(1f));
        }, durations());
    }

    private void drop() {
        Item d = world.dropHeld();
        if (d != null) {
            Sounds.play(SoundKeys.item(d.id(), "drop"));
            game.saveWardrobe();
            game.toast("Dropped " + d.fullName(), Theme.HINT);
        }
    }

    // --- drawing ---------------------------------------------------------------------

    @Override
    public void render() {
        var w = game.window();
        game.worldRenderer().render(world, camera, w.framebufferWidth(), w.framebufferHeight());

        Ui ui = game.ui();
        ui.begin();
        combatHud.draw(ui, world, camera, w.scale());
        combatHud.drawLock(ui, world, camera);
        boolean hudShown = game.settings().showHud;
        if (hudShown) hud(ui);
        if (!pause.open()) {
            prompt(ui);
            float hotbar = hudShown ? ui.height() - 14 - 22 * HELP.length - 4 : ui.height() - 10;
            inventory.drawHotbar(ui, frameDt, hotbar);
            if (!inventory.open()) {
                combatHud.drawMana(ui, world.combat(), hotbar - 34);
                combatHud.drawHeavy(ui, world.player(), hotbar - 34);
                combatHud.drawVitals(ui, world.player(), hotbar - 34);
            }
            if (!game.importer().visible()) inventory.draw(ui);
        }
        if (!game.importer().visible()) pause.draw(ui, frameDt, world.player());
        ui.end();
    }

    /** The help bar along the bottom of the HUD. */
    private static final String[] HELP = {
            "WASD move · Shift run · Ctrl sprint · C crouch · Space jump · Alt/Z dash (hold: roll) · "
                    + "Click/F attack (bow: hold to draw) · X heavy · R spin · Q parry · B block · V bash",
            "E pick up / open · G drop · I inventory · 1-5 or wheel hotbar · F1-F4 emote · [ ] preview · "
                    + "Backspace live · , . turn · T turntable",
            "L lock on · K next target · walk into vines: climb (off their top: climb down) · "
                    + "Alt/Z in the air: land in a roll · "
                    + "the hills are up the north path",
            "Tab height · O auto height · Right-drag orbit · +/- zoom · H HUD · F9 classic/new animations · "
                    + "F11 full screen · Esc menu"};

    private void prompt(Ui ui) {
        if (inventory.open()) return;
        String text, name = "";
        float[] edge = Theme.ACCENT, nameColour = Theme.TITLE;
        Chest c = world.reachableChest();
        World.GroundItem g = world.reachable();
        if (c != null && (g == null || c.position().horizontalDistance(world.player().ground())
                < g.position.horizontalDistance(world.player().ground()))) {
            text = "E   " + (c.isOpen() ? "Close the chest" : "Open the chest");
        } else if (g != null) {
            // the item's name in its rarity's colour
            text = "E   Pick up ";
            name = g.item.fullName() + (world.player().crouched() ? " (crouched)" : "");
            edge = nameColour = g.item.rarity().colour(1f);
        } else {
            return;
        }
        float tw = ui.large.width(text + name) + 40;
        float x = (ui.width() - tw) / 2, y = ui.height() * 0.62f;
        ui.rect(x, y, tw, 44, Theme.withAlpha(Theme.PANEL, 0.85f));
        ui.outline(x, y, tw, 44, 1, edge);
        float done = ui.text(ui.large, text, x + 20, y + 9, Theme.TITLE);
        if (!name.isEmpty()) ui.text(ui.large, name, x + 20 + done, y + 9, nameColour);
    }

    /** The A/B switch: the first version of the re-animated states, or the new. */
    private void setClassic(boolean classic) {
        game.settings().classicAnimations = classic;
        game.sprites().setClassic(classic);
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
        var pacer = game.pacer();
        String frames = String.format("%.0f fps  ·  %.1f ms  ·  longest %.1f ms  ·  %s",
                pacer.fps(), pacer.averageMs(), pacer.worstMs(), pacer.describe());
        ui.text(ui.small, frames, x + 646 - ui.small.width(frames), y + 3, Theme.ITEM_DISABLED);
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
        ui.text(ui.small, String.format("Player  %s%s%s  frame %d/%d @ %.0f fps  facing %s   ·   %s animations (F9)",
                        r.asked().label(), r.substituted() ? " (shown as " + r.state().label() + ": no sheets)" : "",
                        p.previewing() != null ? " (preview)" : "",
                        r.frame() + 1, r.frames(), r.fps(), v.facing().key().toUpperCase(),
                        lib.classic() ? "classic (v1)" : "new"),
                x, y, r.substituted() ? Theme.WARNING : Theme.ITEM);
        y += lh;
        StringJoiner carried = new StringJoiner(", ");
        for (Item it : p.inventory()) carried.add(it.fullName());
        Inventory inv = p.inventory();
        ui.text(ui.small, String.format("Stance  %s%s   ·   hotbar %d of %d slots   ·   carrying %s",
                p.handsEmpty() ? "Empty hands" : p.stance().label(), p.crouched() ? ", crouched" : "",
                inv.selected() + 1, inv.size(),
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
     * sword into her hand too; a wearable into no hand - it is hers to wear),
     * and the one lying about gone from the ground.
     */
    private boolean give(Item item) {
        Player p = world.player();
        ItemDef d = item.def();
        if ((d.isWearable() ? p.inventory().stow(item) : p.inventory().add(item)) < 0) return false;
        world.items().stream().filter(g -> g.item.equals(item)).findFirst()
                .or(() -> world.items().stream().filter(g -> g.def == d && g.source() == null).findFirst())
                .ifPresent(world.items()::remove);
        if (d.inHand()) p.wardrobe().set(p.wardrobe().hand() == Wardrobe.Hand.LEFT ? d.carrySlot().twin()
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
            // the dodges, the way she is scripted to move (else the way she faces): dash, roll
            case "dash", "roll" -> {
                scriptRoll = command.equals("roll");
                scripted = (scripted == null ? Player.Intent.NONE : scripted).withDodge(true, scriptRoll);
            }
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
                ItemDef d = s == Stance.SWORD || s == Stance.BLADE ? ItemDef.SWORD : ItemDef.of(s);
                if (d != null && !p.inventory().contains(d)) give(Item.of(d));
                if (!world.wield(s)) return false;
            }
            // the inventory: inventory [open|close], hotbar <1-5>, slots <n>,
            // give <item> [tier|rarity] [border] (battle_axe crystal venom, silver_necklace, dye_pink)
            case "inventory" -> {
                if (argument.equals("close")) inventory.close();
                else if (argument.equals("open")) inventory.show();
                else inventory.toggle();
            }
            case "hotbar" -> select(Integer.parseInt(argument.trim()) - 1);
            case "slots" -> {
                for (Item spilled : p.inventory().resize(Integer.parseInt(argument.trim()))) {
                    world.dropItem(spilled);
                }
                world.equipSelected();
            }
            case "give" -> {
                Item item = Item.parse(argument);
                if (item == null || !give(item)) return false;
                world.equipSelected();
            }
            // the chest's loot and the dice from a seed: seed <n>
            case "seed" -> world.seed(Long.parseLong(argument.trim()));
            // the training dummies: dummy goto [n] [metres] [front|back|left|right] (stand
            // that far from dummy n on that side of it, facing it), dummy add <x> <z>
            case "dummy" -> {
                String[] a = argument.trim().split("\\s+");
                if (a[0].equals("add") && a.length >= 3) {
                    addDummy(new Vec3(Double.parseDouble(a[1]), 0, Double.parseDouble(a[2])));
                } else if (a[0].equals("goto")) {
                    int n = a.length > 1 ? Integer.parseInt(a[1]) - 1 : 0;
                    if (n < 0 || n >= world.dummies().size()) return false;
                    Dummy d = world.dummies().get(n);
                    double r = a.length > 2 ? Double.parseDouble(a[2]) : 1.4;
                    double turn = a.length > 3 ? switch (a[3]) {
                        case "back" -> Math.PI;
                        case "left" -> Math.PI / 2;
                        case "right" -> -Math.PI / 2;
                        default -> 0;
                    } : 0;
                    // a heading a quarter turn up from its face's points out of its left side
                    Vec3 at = d.position().add(SpriteView.headingVector(d.heading() + turn).scale(r));
                    p.setPosition(at);
                    Vec3 to = d.position().sub(at);
                    p.snapHeading(SpriteView.headingOf(to.x(), to.z()));
                    camera.snapTo(pivot());
                } else {
                    return false;
                }
            }
            // her mana: mana <n>
            case "mana" -> world.combat().setMana(Double.parseDouble(argument.trim()));
            // the heavy attack's cooldown: cooldown <seconds> (0: none)
            case "cooldown" -> p.setHeavyCooldown(Double.parseDouble(argument.trim()));
            // the debug code, as if typed: debug [on|off]
            case "debug" -> {
                boolean want = !argument.trim().equals("off");
                if (game.settings().debugTools != want) toggleDebug();
            }
            // the debug tools' "Clear the ground"
            case "clearground" -> world.clearGround();
            // the inventory sorted: sort name|rarity
            case "sort" -> p.inventory().sort(argument.trim().equals("rarity") ? Inventory.Order.RARITY
                    : Inventory.Order.NAME);
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
            // the highlands' spots: goto spawn|hills|ramp|plateau|ledge|cliff|hill|hollow
            case "goto" -> {
                Terrain.Place place = world.terrain().place(argument.trim());
                Vec3 at = argument.trim().equals("spawn") ? Vec3.ZERO
                        : place == null ? null : new Vec3(place.x(), 0, place.z());
                if (at == null) return false;
                p.setPosition(at);
                camera.snapTo(follow());
            }
            // the lock-on: lock [on|off|next]
            case "lock" -> {
                switch (argument.trim()) {
                    case "off" -> world.combat().lock(null);
                    case "next" -> nextLock();
                    default -> {
                        if (world.combat().locked() == null) toggleLock();
                    }
                }
            }
            // the flying dummies: flier swoop [n] (dive at her now), flier under [n] (stand under it),
            // flier lock [n] (lock on to it)
            case "flier" -> {
                String[] a = argument.trim().split("\\s+");
                int n = a.length > 1 ? Integer.parseInt(a[1]) - 1 : 0;
                if (n < 0 || n >= world.fliers().size()) return false;
                FlyingDummy f = world.fliers().get(n);
                if (a[0].equals("swoop")) f.swoopAt(p, world.terrain());
                else if (a[0].equals("lock")) world.combat().lock(f);
                else if (a[0].equals("under")) {
                    p.setPosition(f.position().add(2.5, 0, 2.5));
                    Vec3 to = f.position().sub(p.ground());
                    p.snapHeading(SpriteView.headingOf(to.x(), to.z()));
                    camera.snapTo(follow());
                } else {
                    return false;
                }
            }
            case "health" -> p.setHealth(Double.parseDouble(argument.trim()));
            case "stamina" -> p.setStamina(Double.parseDouble(argument.trim()));
            // fall <metres>: up in the air where she stands, falling (roll: fall 6 roll)
            case "fall" -> {
                String[] a = argument.trim().split("\\s+");
                p.lift(Double.parseDouble(a[0]));
                if (a.length > 1 && a[1].equals("roll")) scripted = Player.Intent.NONE.withDodge(true, false);
            }
            // climb: at the foot of the vine cliff, climbing it (move stop to hang on, idle to let the keys back);
            // climb top: on top of it a step from its lip, walking off it - down over the lip and on down the vines
            case "climb" -> {
                if (world.terrain().walls().isEmpty()) return false;
                Terrain.Wall wall = world.terrain().walls().get(0);
                if (argument.trim().equals("top")) {
                    double along = wall.length() / 2;
                    p.setPosition(wall.at(along, world.terrain().lipOut(wall, along) - Player.LEDGE_BEHIND - 1));
                    p.snapHeading(SpriteView.headingOf(wall.nx(), wall.nz()));
                    scripted = new Player.Intent(wall.nx(), wall.nz(), false, false, false, false);
                } else {
                    p.setPosition(wall.at(wall.length() / 2, Player.CLING + 0.1));
                    p.snapHeading(wall.facing());
                    double way = argument.trim().equals("down") ? -1 : 1;
                    scripted = new Player.Intent(-wall.nx() * way, -wall.nz() * way, false, false, false, false);
                }
                camera.snapTo(follow());
            }
            case "autocamera" -> game.settings().autoCamera = !argument.trim().equals("off");
            case "pickup" -> pickUp();
            case "dropitem" -> drop();
            case "pause" -> pause.open(PauseMenu.Page.valueOf(
                    argument.isBlank() ? "MAIN" : argument.toUpperCase()));
            case "resume" -> pause.close();
            case "hud" -> game.settings().showHud = !argument.equals("off");
            case "animations" -> setClassic(argument.trim().equals("classic") || argument.trim().equals("v1"));
            case "props", "scenery" -> {
                game.settings().showProps = !argument.equals("off");
                world.setScenery(game.scenery());
            }
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
