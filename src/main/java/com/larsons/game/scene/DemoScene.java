package com.larsons.game.scene;

import com.larsons.game.audio.Music;
import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Songs;
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
import com.larsons.game.input.Action;
import com.larsons.game.input.Input;
import com.larsons.game.building.Building;
import com.larsons.game.building.Room;
import com.larsons.game.level.Interiors;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.LevelWorld;
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
import com.larsons.game.world.DayCycle;
import com.larsons.game.world.Inventory;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Player;
import com.larsons.game.world.RideTrack;
import com.larsons.game.world.Scenery;
import com.larsons.game.world.Store;
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
    private static final Action[] HOTBAR_KEYS = {Action.HOTBAR_1, Action.HOTBAR_2, Action.HOTBAR_3, Action.HOTBAR_4,
            Action.HOTBAR_5};
    /** F1–F4: the emotes (the number keys are the hotbar's). */
    private static final Action[] EMOTE_KEYS = {Action.EMOTE_LAUGH, Action.EMOTE_CRY, Action.EMOTE_SURPRISE,
            Action.EMOTE_ANGRY};
    private static final AnimState[] EMOTES = {AnimState.EMOTE_LAUGH, AnimState.EMOTE_CRY,
            AnimState.EMOTE_SURPRISE, AnimState.EMOTE_ANGRY};
    /**
     * Where the weapons lie at the start (the sword, unless it is already in
     * hand): each in its basic tier, the crystal staff as it is drawn - the
     * Magic Staff.
     */
    static final Vec3 SWORD_AT = new Vec3(1.4, 0, -2.2);
    static final Object[][] WEAPONS_AT = {
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
    static final Vec3[] DUMMIES_AT = {new Vec3(4.2, 0, -1.0), new Vec3(5.6, 0, -4.0), new Vec3(-5.4, 0, 0.4)};
    /**
     * The highlands' dummies, each facing the plateau: two below its west
     * ledge (shot at from its top, they are on the low ground), two below
     * the vine cliff.
     */
    static final Vec3[] HIGHLAND_DUMMIES_AT = {new Vec3(-3.2, 0, -31.0), new Vec3(-3.6, 0, -27.0),
            new Vec3(16.8, 0, -37.5), new Vec3(17.2, 0, -33.5)};
    /** Where the highlands' dummies look: the plateau's middle. */
    static final Vec3 PLATEAU_MIDDLE = new Vec3(6, 0, -36);
    /** The plateau's chest: at its north-east corner, looking down the ramp. */
    static final Vec3 PLATEAU_CHEST_AT = new Vec3(9.5, 0, -39.0);
    /** The middles of the flying dummies' rounds: just north of the spawn, and over the hollow by the cliff. */
    static final Vec3[] FLIERS_AT = {new Vec3(-2.0, 0, -9.5), new Vec3(16.0, 0, -29.0)};
    /**
     * The market stall: off to her left at the start, facing where she
     * starts - supplies, upgrade ingots, reshuffle scrolls and her class's
     * cards for her coins.
     */
    static final Vec3 STALL_AT = new Vec3(-6.2, 0, -4.4);
    /** The chest: behind her at the start, between the axe and the sword, its front three-quarters on. */
    static final Vec3 CHEST_AT = new Vec3(-0.3, 0, -3.4);
    static final String CHEST = "ornate_chest";
    /**
     * How much ground round the spawn, the chest, each weapon's spot and each
     * dummy the scenery leaves bare, in metres - round the chest, enough for
     * the fan its loot lands in (up to 2.1 m out, {@code World}) and room to
     * walk; round a dummy, room to circle it and swing.
     */
    private static final double SPAWN_CLEAR = 1.2, CHEST_CLEAR = 2.6, ITEM_CLEAR = 0.6, DUMMY_CLEAR = 1.8,
            STALL_CLEAR = 3.2;

    private final Game game;
    private final World world;
    private final OrbitCamera camera = new OrbitCamera(0.45, Math.toRadians(45), 6.5);
    private final PauseMenu pause;
    private final InventoryPanel inventory;
    private final CardTable table;
    private final WorldSounds sounds = new WorldSounds();
    private final CombatHud combatHud;
    private final DebugCode debugCode = new DebugCode();
    /** The level being played, or null: the demo. */
    private final Level level;
    /** How loud the music plays under the game, of the song's own level (the menu plays it at full). */
    public static final double MUSIC_VOLUME = 0.7;
    /** Back to the editor, or null: the level is not being play-tested. */
    private final Runnable back;
    /** The scenery of where she is - the demo's, the level's, a floor of a building's - as it is when shown. */
    private Scenery scenery;
    /** Where she is inside a building, or null: outdoors. */
    private Inside inside;
    /** The floors of the buildings she has been in, as she left them: by the building, then by the floor. */
    private final java.util.Map<Integer, java.util.Map<Integer, Left>> visited = new java.util.HashMap<>();
    /** The insides made, as each kind comes, for the buildings the level has none set for. */
    private final java.util.Map<Integer, List<Level>> madeInsides = new java.util.HashMap<>();
    /** The fade through black as she goes through a door, 1 – 0. */
    private double fade;
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
    /** A scripted run set the clock's speed (the N key leaves it alone). */
    private boolean scriptedTimeLapse;

    public DemoScene(Game game) {
        this.game = game;
        this.world = new World(game.wardrobe(), game.settings().inventorySlots);
        world.setRideTrack(RideTrack.load(game.objects().root().resolve(RIDE_SHEETS)));
        this.level = null;
        this.back = null;
        this.scenery = game.demoScenery();
        world.setTerrain(game.terrain());
        world.setGround(game.demoScenery().ground());
        world.setWeather(com.larsons.game.world.Weather.DEMO);        // each biome's weather, now and then
        this.pause = new PauseMenu(game, world);
        this.inventory = new InventoryPanel(game, world);
        this.table = new CardTable(game, world);
        inventory.onCards(() -> table.show(CardTable.View.TABLE));
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
        CombatTuning tuning = tune(game, world);
        for (Vec3 at : DUMMIES_AT) addDummy(at);
        for (Vec3 at : HIGHLAND_DUMMIES_AT) {
            world.addDummy(new Dummy(world.terrain().onGround(at),
                    SpriteView.headingOf(PLATEAU_MIDDLE.x() - at.x(), PLATEAU_MIDDLE.z() - at.z()),
                    tuning.dummyHealth, tuning.dummyDownSeconds));
        }
        world.addChest(new Chest(CHEST, world.terrain().onGround(PLATEAU_CHEST_AT),
                SpriteView.headingOf(-0.6, 1)));
        addStall();
        for (int i = 0; i < FLIERS_AT.length; i++) addFlier(FLIERS_AT[i], i * 2.1);
        combatHud = new CombatHud(game.worldRenderer().combat().fx());
        world.equipSelected();
        world.setScenery(shownScenery());
        world.startDay();
        world.ammo().setEnabled(true);
        startHero();
        p.snapHeading(SpriteView.headingShowing(Facing.SOUTH_EAST, camera.yaw()));
    }

    /**
     * Her class, as she chose it before - its deck dealt - or, the first time,
     * the class picker over the game; and the supplies she starts with: some
     * ammunition for every kind of weapon, a reshuffle scroll, an upgrade
     * ingot, a few coins.
     */
    private void startHero() {
        Player p = world.player();
        String cls = game.settings().heroClass;
        if (com.larsons.game.cards.CardBook.get().hero(cls) != null) p.hero().choose(cls, p);
        else choosing = true;
        if (!world.ammo().enabled()) return;
        for (ItemDef a : com.larsons.game.world.Supplies.AMMO) p.inventory().put(Item.of(a), 2, true);
        p.inventory().put(Item.of(com.larsons.game.world.Supplies.RESHUFFLE_SCROLL), 1, true);
        p.inventory().put(Item.of(com.larsons.game.world.Supplies.UPGRADE_INGOT), 1, true);
        p.addCoins(15);
    }

    /** Whether the class picker is up (the game standing still until she chooses). */
    private boolean choosing;

    /**
     * Play {@code level} (Creative Mode's play-test, or a saved level from
     * the dev tab): everything in it as {@link LevelWorld} builds it, and
     * she starts at its spawn - or, given {@code at}, there, facing {@code
     * heading}. {@code back} (null: the main menu) is where Esc → "Back to
     * the editor" and F5 go.
     */
    public DemoScene(Game game, Level level, Vec3 at, double heading, Runnable back) {
        this.game = game;
        this.world = new World(game.wardrobe(), game.settings().inventorySlots);
        world.setRideTrack(RideTrack.load(game.objects().root().resolve(RIDE_SHEETS)));
        this.level = level;
        this.back = back;
        this.pause = new PauseMenu(game, world);
        this.inventory = new InventoryPanel(game, world);
        this.table = new CardTable(game, world);
        inventory.onCards(() -> table.show(CardTable.View.TABLE));
        if (back != null) pause.setBack("Back to the editor", back);
        CombatTuning tuning = tune(game, world);
        LevelWorld.populate(world, level, game.scenerySprites(), tuning);
        this.scenery = world.scenery();
        world.ownWhatIsWorn();
        Player p = world.player();
        if (at != null) {
            p.setPosition(at);
            p.snapHeading(heading);
        }
        combatHud = new CombatHud(game.worldRenderer().combat().fx());
        startHero();
        world.equipSelected();
        camera.setDesiredYaw(p.heading());
        camera.snapTo(pivot());
    }

    /** The fighting tuned as {@code assets/combat/combat.json} has it: her mana full, her cooldowns. */
    private static CombatTuning tune(Game game, World world) {
        CombatTuning tuning = CombatTuning.load(game.settings().assets);
        world.combat().use(tuning);
        world.combat().setMana(tuning.maxMana);
        world.player().setHeavyCooldown(tuning.heavyCooldown);
        world.player().setDodgeCooldown(tuning.dodgeCooldown);
        return tuning;
    }

    /** The scenery as shown: the demo's or the level's - none while it is hidden (P). */
    private Scenery shownScenery() {
        return game.settings().showProps ? scenery : Scenery.NONE;
    }

    /** A training dummy at {@code at}, facing where she starts. */
    private Dummy addDummy(Vec3 at) {
        CombatTuning t = world.combat().tuning();
        return world.addDummy(new Dummy(world.terrain().onGround(at), SpriteView.headingOf(-at.x(), -at.z()),
                t.dummyHealth, t.dummyDownSeconds));
    }

    /** The market stall, facing where she starts (its shop alone, at a counter's height, if it has no model). */
    private void addStall() {
        Vec3 at = world.terrain().onGround(STALL_AT);
        double facing = SpriteView.headingOf(-STALL_AT.x(), -STALL_AT.z());
        var profile = com.larsons.game.building.BuildingCatalog.get().profile("market_stall_awning");
        Vec3 counter = at;
        if (profile != null) {
            var b = world.addBuilding(new com.larsons.game.building.Building(-2, profile, at, facing, null, null));
            counter = b.toWorld(0, com.larsons.game.level.ObjectTypes.front(profile));
        }
        world.addShop(new com.larsons.game.world.Shop(-2, "Market stall", counter, facing, "", true));
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
        clear.add(new SceneryLayout.Clearing(STALL_AT.x(), STALL_AT.z(), STALL_CLEAR));
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
    public String name() { return level == null ? "Demo" : back != null ? "Play-test: " + level.name() : level.name(); }

    @Override
    public void enter() {
        camera.snapTo(pivot());
        Songs.playChoice(level == null ? Songs.SHUFFLE : level.music(), MUSIC_VOLUME);
    }

    @Override
    public void exit() {
        sounds.stop();
        inventory.dispose();
        Music.stop();
    }

    private Vec3 pivot() {
        return world.player().feet().add(0, 0.9, 0);
    }

    @Override
    public void update(double dt) {
        frameDt = dt;
        fade = Math.max(0, fade - dt * 2.8);
        Input in = game.window().input();
        world.setScenery(shownScenery());           // (P, or Pause → Settings, may have hidden it)
        // a menu up: the gamepad moves the pointer; playing: it plays
        in.setUiMode(pause.open() || choosing || inventory.open() || table.open());
        if (pause.open() || choosing) {
            sounds.update(world, camera, durations());
            return;
        }
        if (in.pressed(Action.PAUSE) || in.pressed(GLFW_KEY_ESCAPE)) {
            in.consume(Action.PAUSE);
            in.consumeKey(GLFW_KEY_ESCAPE);
            if (table.open()) {
                table.escape();
            } else if (inventory.open()) {
                inventory.close();
            } else {
                pause.open(PauseMenu.Page.MAIN);
                Sounds.play(SoundKeys.ui("pause"));
            }
            return;
        }
        if (back != null && in.pressed(GLFW_KEY_F5)) {
            back.run();
            return;
        }
        if (in.pressed(Action.INVENTORY)) {
            table.close();
            inventory.toggle();
        }
        if (in.pressed(Action.CARDS)) {
            if (inventory.open()) inventory.close();
            table.toggle();
        }
        // the card table laid: the game stands still behind it
        if (table.open()) {
            camera.update(dt, follow());
            sounds.update(world, camera, durations());
            return;
        }
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
        if (in.pressed(Action.HOTBAR_PREV)) select(Math.floorMod(world.player().inventory().selected() - 1, Inventory.HOTBAR));
        if (in.pressed(Action.HOTBAR_NEXT)) select(Math.floorMod(world.player().inventory().selected() + 1, Inventory.HOTBAR));
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
        // (a stick turns it as far as it is pushed; nothing while the inventory is open)
        double orbit = inventory.open() ? 0 : in.value(Action.CAMERA_LEFT) - in.value(Action.CAMERA_RIGHT);
        double tilt = inventory.open() ? 0 : in.value(Action.CAMERA_UP) - in.value(Action.CAMERA_DOWN);
        if (orbit != 0 || tilt != 0) camera.rotate(orbit * dt * 1.6, tilt * dt * 1.1);
        camera.zoom(in.scroll());                      // the wheel zooms while orbiting
        if (in.repeated(Action.ZOOM_IN)) camera.zoom(1);
        if (in.repeated(Action.ZOOM_OUT)) camera.zoom(-1);
        if (in.pressed(Action.CAMERA_HEIGHT)) {
            toggled();
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
            toggled();
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
        if (in.pressed(Action.HIDE_HUD)) {
            toggled();
            game.settings().showHud = !game.settings().showHud;
        }
        if (in.pressed(Action.LOCK)) toggleLock();
        if (in.pressed(Action.LOCK_NEXT)) nextLock();
        if (in.pressed(GLFW_KEY_O)) {
            toggled();
            game.settings().autoCamera = !game.settings().autoCamera;
            game.toast(game.settings().autoCamera ? "Camera height: automatic (higher on peaks, lower in hollows)"
                    : "Camera height: fixed", Theme.HINT);
        }
        if (in.pressed(GLFW_KEY_P)) {
            toggled();
            game.settings().showProps = !game.settings().showProps;
        }
        if (in.pressed(GLFW_KEY_F3)) {
            toggled();
            game.settings().debugOverlay = !game.settings().debugOverlay;
        }
        if (in.pressed(GLFW_KEY_F9)) {
            toggled();
            setClassic(!game.settings().classicAnimations);
        }

        // --- the time of day: N held runs it fast ---------------------------------------------
        if (!scriptedTimeLapse) world.clock().setSpeed(in.down(Action.TIME_LAPSE) && !inventory.open() ? DayCycle.TIME_LAPSE : 1);
        if (in.pressed(Action.TIME_LAPSE) && !inventory.open() && !world.sky().air().timeRuns()) {
            game.toast("The time stands still here", Theme.HINT);
        }

        // --- items, the chest -----------------------------------------------------------
        keepStoreInReach();
        if (in.pressed(Action.USE) && !inventory.open()) use();
        else if (in.pressed(Action.USE) && (inventory.store() != null || inventory.shop() != null)) inventory.close();
        if (in.pressed(Action.DROP) && !inventory.open()) drop();

        // --- the character (still while the inventory is open) ----------------------------
        Player.Intent intent = scripted != null ? scripted : inventory.open() ? Player.Intent.NONE : intent(in);
        intent = eatInstead(intent);
        if (intent.heavy() && p.heavyWait() > 0 && p.stance().heavy() != null && world.time() - heavyToast > 1.5) {
            heavyToast = world.time();
            Sounds.play(SoundKeys.ui("error"), 0.6);
            game.toast("Heavy attack ready in " + (int) Math.ceil(p.heavyWait()) + " s", Theme.HINT);
        }
        // her own inventory open - her cards, a weapon's tiers - the game stands still
        boolean still = inventory.pausing();
        if (!still) p.update(dt, intent, durations());
        if (scriptRoll && p.action() != AnimState.DASH) scriptRoll = false;   // rolling (or it never started)
        if (scripted != null) {
            // the one-shots happen once; with nothing left moving or held, the keys take over again
            scripted = held(scripted);
            if (scripted.moveX() == 0 && scripted.moveZ() == 0 && !scriptDraw && !scriptBlock && !scriptRoll) {
                scripted = null;
            }
        }
        if (!still) world.tick(dt, (c, s) -> game.worldRenderer().chestDuration(c, s, camera));
        lockedCamera(dt);
        autoHeight();
        camera.update(dt, follow());
        camera.keepAbove(world.terrain(), 0.8);
        sounds.update(world, camera, durations());
    }

    /** A viewer key's switch thrown (the camera height, the HUD, the scenery ...). */
    private static void toggled() {
        Sounds.play(SoundKeys.ui("click"), 0.6);
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
        if (t == null) {
            Sounds.play(SoundKeys.ui("error"), 0.6);
            game.toast("Nothing to lock on to within " + (int) Combat.LOCK_RANGE + " m", Theme.HINT);
        }
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
        Sounds.play(SoundKeys.ui("secret"));
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

    /** What E does where she stands: the nearest of the chest, an item lying by her, a bush to pick, a door. */
    private enum Use { NOTHING, CHEST, STORE, SHOP, ITEM, BUSH, DOOR, PASSAGE, WARDROBE, SADDLE, MOUNT, DISMOUNT }

    /** A storage chest whose lid is up while what it holds is shown beside the inventory, or null. */
    private Chest storeChest;

    /** The saddled horse's sheets, where its {@code ride.json} is. */
    private static final String RIDE_SHEETS = "horse_saddled";

    private Use usable() {
        Player.Riding riding = world.player().riding();
        if (riding == Player.Riding.RIDING) return Use.DISMOUNT;        // on a horse: only getting off it
        if (riding != Player.Riding.NO) return Use.NOTHING;
        Vec3 me = world.player().ground();
        // a horse by her: get on it - or, with a saddle on her, saddle it - before what lies about
        if (world.reachableHorse() != null) return Use.MOUNT;
        if (world.saddleable() != null) return Use.SADDLE;
        Chest c = world.reachableChest();
        Store st = world.reachableStore();
        World.GroundItem g = world.reachable();
        Scenery.Placed b = world.reachableBush();
        Building d = usableDoor();
        double dc = c == null ? Double.MAX_VALUE : c.position().horizontalDistance(me);
        double ds = st == null ? Double.MAX_VALUE : st.position().horizontalDistance(me);
        double dg = g == null ? Double.MAX_VALUE : g.position.horizontalDistance(me);
        double db = b == null ? Double.MAX_VALUE : Math.hypot(b.x() - me.x(), b.z() - me.z());
        double dd = d == null ? Double.MAX_VALUE : d.door().horizontalDistance(me) + 0.3;   // (what lies by her first)
        if (d != null && dd < dc && dd < ds && dd < dg && dd < db) return Use.DOOR;
        // a way through (a wardrobe with a stair at its back, a bookshelf that swings out) - and a wardrobe
        if (g == null && usablePassage() != null) return Use.PASSAGE;
        if (g == null && world.reachableWardrobe() != null) return Use.WARDROBE;
        if (st != null && ds < dc && ds < dg && ds <= db) return Use.STORE;
        com.larsons.game.world.Shop sh = world.reachableShop();
        if (sh != null && g == null) return Use.SHOP;       // (what lies on the counter is picked up first)
        if (c != null && dc < dg && dc <= db) return Use.CHEST;
        if (g != null && dg <= db) return Use.ITEM;
        return b != null ? Use.BUSH : Use.NOTHING;
    }

    /**
     * E: open or shut the chest she stands by, pick up what lies by her, pick a bush, go through a door -
     * whichever is nearest; by a horse, saddle it or get on it; on one, get off.
     */
    private void use() {
        switch (usable()) {
            case CHEST -> {
                Chest c = world.reachableChest();
                if (c.store() != null) lookIn(c.store(), c);
                else toggleChest(c);
            }
            case STORE -> lookIn(world.reachableStore(), null);
            case SHOP -> {
                com.larsons.game.world.Shop sh = world.reachableShop();
                Vec3 d = sh.position().sub(world.player().ground());
                world.player().turnTo(SpriteView.headingOf(d.x(), d.z()));
                inventory.showShop(sh);
            }
            case ITEM -> pickUp();
            case BUSH -> forage(world.reachableBush());
            case DOOR -> goThrough(usableDoor());
            case PASSAGE -> goThroughPassage(usablePassage());
            case WARDROBE -> {
                Sounds.play(SoundKeys.world("door_open"));
                pause.open(PauseMenu.Page.WARDROBE);
            }
            case SADDLE -> saddleHorse();
            case MOUNT -> world.mount(world.reachableHorse());
            case DISMOUNT -> {
                if (!world.dismount()) game.toast("No room to get down here", Theme.WARNING);
            }
            default -> { }
        }
    }

    /** E by a horse with a saddle on her: it is saddled, and hers to ride. */
    private void saddleHorse() {
        if (!world.saddle(world.saddleable())) return;
        game.toast("Saddled the horse — press E to ride it (Shift canter, Ctrl gallop, E to get off)",
                Theme.ACCENT);
    }

    // --- inside buildings ------------------------------------------------------------------

    /** Inside a building: which, its floors, the floor she is on, and the outdoors as she left it. */
    private static final class Inside {
        final Building building;
        final List<Level> floors;
        final World.Place outdoors;
        final Scenery outdoorScenery;
        /** The camera's height outside, put back as she comes out. */
        final double outdoorPitch;
        int floor;

        Inside(Building building, List<Level> floors, World.Place outdoors, Scenery outdoorScenery,
               double outdoorPitch) {
            this.building = building;
            this.floors = floors;
            this.outdoors = outdoors;
            this.outdoorScenery = outdoorScenery;
            this.outdoorPitch = outdoorPitch;
        }
    }

    /** How high the camera looks down from inside a building, radians. */
    static final double INDOOR_PITCH = Math.toRadians(38);

    /** A floor as she left it, and its scenery. */
    private record Left(World.Place place, Scenery scenery) {}

    /** The door she can go through from where she stands - one that leads somewhere - or null. */
    private Building usableDoor() {
        Building d = world.reachableDoor();
        if (d == null) return null;
        Room.Spec r = d.profile().room();
        if (r == null) return level == null || floorsOf(d).isEmpty() ? null : d;
        if (inside == null) return null;
        if (r.piece() == Room.Piece.STAIRS) {
            int next = inside.floor + (r.up() ? 1 : -1);
            return next >= 0 && next < inside.floors.size() ? d : null;
        }
        return d;
    }

    /** The way through ({@link Building.Passage}) she stands at that leads somewhere from here, or null. */
    private Building usablePassage() {
        Building b = world.reachablePassage();
        if (b == null) return null;
        Building.Passage p = b.passage();
        return switch (p.way()) {
            case "spot" -> spotNamed(p.to()) == null ? null : b;
            case "up", "down" -> {
                if (inside == null) yield null;
                int next = inside.floor + (p.way().equals("up") ? 1 : -1);
                yield next >= 0 && next < inside.floors.size() ? b : null;
            }
            case "out" -> inside == null ? null : b;
            default -> null;
        };
    }

    /** The named spot {@code name} where she is (this floor, or outdoors), or null. */
    private Terrain.Place spotNamed(String name) {
        if (name == null || name.isBlank()) return null;
        for (Terrain.Place pl : world.terrain().places()) {
            if (pl.name().equalsIgnoreCase(name.trim())) return pl;
        }
        return null;
    }

    /** Through {@code b}, a way through: to its spot, up or down a floor, or outside. */
    private void goThroughPassage(Building b) {
        if (b == null) return;
        Building.Passage p = b.passage();
        switch (p.way()) {
            case "spot" -> {
                Terrain.Place to = spotNamed(p.to());
                if (to == null) return;
                Sounds.play(SoundKeys.world(p.hidden() ? "door_close" : "door_open"));
                Player pl = world.player();
                pl.setPosition(world.terrain().onGround(new Vec3(to.x(), 0, to.z())));
                arrived();
            }
            case "up", "down" -> climb(p.way().equals("up"), b.position());
            case "out" -> goOut();
            default -> { }
        }
    }

    /** What going through {@code b}, a way through, is called - a hidden one only "look closer". */
    private static String passageText(Building b) {
        Building.Passage p = b.passage();
        String what = b.profile().label().toLowerCase();
        if (p.hidden()) return "Look closer at the " + what;
        return switch (p.way()) {
            case "up" -> "Go up through the " + what;
            case "down" -> "Go down through the " + what;
            case "out" -> "Go outside through the " + what;
            default -> "Go through the " + what;
        };
    }

    /** What going through {@code d} is called. */
    private static String doorText(Building d) {
        Room.Spec r = d.profile().room();
        if (r == null) return "Go into the " + d.profile().label().toLowerCase();
        if (r.piece() == Room.Piece.EXIT) return "Go outside";
        return r.up() ? "Go upstairs" : "Go downstairs";
    }

    /** The floors inside {@code b}: as the level has them, or as its kind comes. */
    private List<Level> floorsOf(Building b) {
        List<Level> f = level == null ? null : level.interior(b.id());
        if (f != null && !f.isEmpty()) return f;
        return madeInsides.computeIfAbsent(b.id(), id -> Interiors.make(b.key()));
    }

    /** Through {@code d}: into its building, out of the one she is in, up or down its stairs. */
    private void goThrough(Building d) {
        if (d == null) return;
        Room.Spec r = d.profile().room();
        if (r == null) goIn(d);
        else if (r.piece() == Room.Piece.EXIT) goOut();
        else if (r.piece() == Room.Piece.STAIRS) climb(r.up(), d.position());
    }

    /** Into {@code b}: the outdoors kept as it is, and she comes in at its ground floor's door out. */
    private void goIn(Building b) {
        List<Level> floors = floorsOf(b);
        if (inside != null || floors.isEmpty()) return;
        Sounds.play(SoundKeys.world("door_open"));
        inside = new Inside(b, floors, world.leave(), scenery, camera.basePitch());
        // looked down into from higher, the walls before her cut away, as a dolls' house is
        camera.setDesiredPitch(Math.max(camera.basePitch(), INDOOR_PITCH));
        showFloor(0, null, false);
    }

    /** Out of the building she is in, at its door, facing away from it - the floor kept as she left it. */
    private void goOut() {
        if (inside == null) return;
        keepFloor();
        world.enter(inside.outdoors);
        scenery = inside.outdoorScenery;
        world.setScenery(shownScenery());
        Building b = inside.building;
        camera.setDesiredPitch(inside.outdoorPitch);
        inside = null;
        Player p = world.player();
        p.setPosition(world.terrain().onGround(b.door()));
        p.snapHeading(b.heading());
        arrived();
        Sounds.play(SoundKeys.world("door_close"));
    }

    /** Up ({@code up}) or down a floor, from the stairs at {@code from}. */
    private void climb(boolean up, Vec3 from) {
        if (inside == null) return;
        int next = inside.floor + (up ? 1 : -1);
        if (next < 0 || next >= inside.floors.size()) return;
        Sounds.play(SoundKeys.world("stairs"));
        keepFloor();
        showFloor(next, from, up);
    }

    /** The floor she is on, kept as she leaves it - what she dropped, the chests she opened. */
    private void keepFloor() {
        visited.computeIfAbsent(inside.building.id(), k -> new java.util.HashMap<>())
                .put(inside.floor, new Left(world.leave(), scenery));
    }

    /**
     * Floor {@code n} of the building she is in - as she left it, or built
     * from its level - and she on it: at the stairs going the other way
     * nearest {@code from} (she came up: at a stairwell's top; down: at a
     * staircase's foot), or - {@code from} null - at its door out.
     */
    private void showFloor(int n, Vec3 from, boolean wentUp) {
        java.util.Map<Integer, Left> kept = visited.get(inside.building.id());
        Left left = kept == null ? null : kept.remove(n);
        Level floor = inside.floors.get(n);
        if (left != null) {
            world.enter(left.place());
            scenery = left.scenery();
        } else {
            Terrain t = LevelWorld.stageIndoors(world, floor);
            LevelWorld.spawnAll(world, floor.objects(), floor, t, game.scenerySprites(), world.combat().tuning());
            scenery = world.scenery();
        }
        world.setScenery(shownScenery());
        inside.floor = n;
        Building by = null;
        double best = Double.MAX_VALUE;
        for (Building b : world.buildings()) {
            Room.Spec r = b.profile().room();
            if (r == null) continue;
            boolean fits = from == null ? r.piece() == Room.Piece.EXIT
                    : r.piece() == Room.Piece.STAIRS && r.up() != wentUp;
            if (!fits) continue;
            double d = from == null ? 0 : b.position().horizontalDistance(from);
            if (d < best) {
                best = d;
                by = b;
            }
        }
        Player p = world.player();
        Vec3 at;
        double heading;
        if (by != null) {
            at = by.door();
            heading = by.heading();
        } else {
            LevelObject s = floor.spawn();
            at = s == null ? Vec3.ZERO : new Vec3(s.x(), 0, s.z());
            heading = s == null ? 0 : s.yaw();
        }
        p.setPosition(world.terrain().onGround(at));
        p.snapHeading(heading);
        world.setSpawnPoint(world.terrain().onGround(at));
        arrived();
    }

    /** She has come through a door: the screen fades in from black, the camera behind her, nothing locked on. */
    private void arrived() {
        fade = 1;
        world.combat().lock(null);
        camera.setDesiredYaw(world.player().heading());
        camera.snapTo(pivot());
    }

    /** Whether she is inside a building. */
    public boolean indoors() { return inside != null; }

    /**
     * Start inside building {@code buildingId}, on floor {@code floor}: at
     * {@code at} (null: where she comes in), facing {@code heading} - the
     * play-test of a building's inside from Creative Mode.
     */
    public void startInside(int buildingId, int floor, Vec3 at, double heading) {
        Building b = world.building(buildingId);
        if (b == null) return;
        goIn(b);
        if (inside == null) return;
        if (floor > 0 && floor < inside.floors.size()) {
            keepFloor();
            showFloor(floor, null, false);
        }
        if (at != null) {
            Player p = world.player();
            p.setPosition(world.terrain().onGround(at));
            p.snapHeading(heading);
            arrived();
        }
        fade = 0;
    }

    /**
     * Pick a foraging bush: she turns to it and reaches in, and as her hand
     * closes what was on it is hers ({@link World#pick}) - onto its stacks in
     * the inventory, the rest at her feet when there is no room.
     */
    private void forage(Scenery.Placed bush) {
        if (bush == null) return;
        Player p = world.player();
        p.turnTo(SpriteView.headingOf(bush.x() - p.ground().x(), bush.z() - p.ground().z()));
        p.pickUp(() -> {
            if (world.picked(bush)) return;
            List<Item> got = world.pick(bush);
            if (!world.picked(bush)) return;
            Vec3 at = new Vec3(bush.x(), bush.y() + 0.5, bush.z());
            world.combat().sound(SoundKeys.world("forage_pick"), at, 1);
            ItemDef d = ItemDef.byId(bush.profile().forage().item());
            if (d == null) return;
            String name = d.name().toLowerCase();
            if (got.isEmpty()) {
                game.toast("Your inventory is full - the " + name + " fell at your feet", Theme.WARNING);
                return;
            }
            world.combat().say("+" + got.size() + " " + name, d.food().rgb(), p.feet(), 2.1, true);
            game.toast("Picked " + got.size() + " " + name + " (" + d.food().effect()
                    + (got.size() > 1 ? " each" : "") + ") - "
                    + "eat from the hotbar (click) or the inventory (right-click)", Theme.ACCENT);
        }, durations());
    }

    /** The last time she ate (a bite takes a moment). */
    private double lastBite = -10;
    /** How long a bite takes, seconds. */
    static final double BITE = 0.6;

    /**
     * With something picked in hand, the attack eats one of it instead of
     * swinging (her hands are empty): what it gives back rises from her, or
     * she is told she is full.
     */
    private Player.Intent eatInstead(Player.Intent intent) {
        if (!intent.attack()) return intent;
        Player p = world.player();
        Item held = p.inventory().held();
        if (held == null || !held.def().edible()) return intent;
        if (world.time() - lastBite >= BITE) {
            lastBite = world.time();
            eatHeld();
        }
        return new Player.Intent(intent.moveX(), intent.moveZ(), intent.run(), intent.sprint(), intent.jump(),
                false, false, intent.crouch(), intent.heavy(), intent.spin(), intent.parry(), intent.block(),
                intent.bash(), intent.emote(), intent.dodge(), intent.dodgeHeld());
    }

    /** Eat one of what is in the selected hotbar slot. */
    private void eatHeld() {
        Player p = world.player();
        World.Eaten e = world.eat(p.inventory().selected());
        if (e == null) return;
        if (!e.ate()) {
            Sounds.play(SoundKeys.ui("error"), 0.6);
            world.combat().say("Already full", 0xC8D2DC, p.feet(), 2.1, true);
            return;
        }
        world.combat().sound(SoundKeys.item(e.item().id(), "eat"), p.feet(), 1);
        world.combat().say(InventoryPanel.gains(e), e.item().def().food().rgb(), p.feet(), 2.1, false);
    }

    private void toggleChest(Chest c) {
        Player p = world.player();
        Vec3 d = c.position().sub(p.ground());
        p.turnTo(SpriteView.headingOf(d.x(), d.z()));
        c.toggle(s -> game.worldRenderer().chestDuration(c, s, camera));
    }

    /**
     * E by a barrel, a crate or a storage chest ({@code chest}, its lid
     * opened): what it holds beside her inventory, to move things in and out.
     */
    private void lookIn(Store s, Chest chest) {
        Player p = world.player();
        Vec3 d = s.position().sub(p.ground());
        p.turnTo(SpriteView.headingOf(d.x(), d.z()));
        if (chest != null && !chest.isOpen()) chest.toggle(st -> game.worldRenderer().chestDuration(chest, st, camera));
        storeChest = chest;
        inventory.showStore(s);
    }

    /** A store shown beside the inventory is shut once she is out of its reach; a chest's lid, once it is shut. */
    private void keepStoreInReach() {
        Store s = inventory.store();
        Vec3 me = world.player().ground();
        if (s != null && !(storeChest != null ? storeChest.inReach(me) : s.inReach(me))) inventory.close();
        if (storeChest != null && inventory.store() == null) {
            Chest c = storeChest;
            if (c.isOpen()) c.toggle(st -> game.worldRenderer().chestDuration(c, st, camera));
            storeChest = null;
        }
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
        // (a key is all the way; a stick as far as it is pushed - and pushed right out, she runs)
        double f = in.value(Action.MOVE_FORWARD) - in.value(Action.MOVE_BACK);
        double r = in.value(Action.MOVE_RIGHT) - in.value(Action.MOVE_LEFT);
        double push = Math.hypot(f, r);
        if (push > 1) {
            f /= push;
            r /= push;
        }
        boolean stick = in.usingPad() && push > 0;
        if (stick && push < 1) {
            // (she has no slower walk than her walk: any push is a whole step that way)
            f /= push;
            r /= push;
        }
        Vec3 move = camera.groundForward().scale(f).add(camera.right().scale(r));
        boolean ctrl = in.down(Action.SPRINT);
        boolean shift = in.down(Action.RUN) || (stick && push > 0.82);
        boolean attack = in.pressed(Action.ATTACK);
        boolean attackHeld = in.down(Action.ATTACK);
        AnimState emote = null;
        for (int i = 0; i < EMOTE_KEYS.length; i++) if (in.pressed(EMOTE_KEYS[i])) emote = EMOTES[i];
        // the dodge: a tap dashes, held it turns into a roll
        boolean dodge = in.pressed(Action.DODGE);
        boolean dodgeHeld = in.down(Action.DODGE);
        return new Player.Intent(move.x(), move.z(), shift && !ctrl, ctrl, in.pressed(Action.JUMP), attack,
                attackHeld, in.pressed(Action.CROUCH), in.pressed(Action.HEAVY), in.pressed(Action.SPIN),
                in.pressed(Action.PARRY), in.down(Action.BLOCK), in.pressed(Action.BASH), emote, dodge, dodgeHeld);
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
            Sounds.play(SoundKeys.ui("error"));
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
            if (d.isTack()) {
                game.toast("Picked up " + g.item.fullName() + " (" + g.item.rarity().label() + ")"
                        + " — walk up to a horse and press E to saddle it", g.item.rarity().colour(1f));
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
        if (world.player().underWater()) {
            // under the water: the world seen through it, darker the deeper she is
            float deep = (float) Math.min(1, world.player().diveDepth() / 6);
            ui.rect(0, 0, ui.width(), ui.height(), new float[]{0.06f, 0.24f + 0.1f * (1 - deep), 0.38f,
                    0.38f + 0.3f * deep});
        }
        if (fade > 0) ui.rect(0, 0, ui.width(), ui.height(), new float[]{0, 0, 0, (float) Math.min(1, fade * 1.4)});
        combatHud.draw(ui, world, camera, w.scale());
        combatHud.drawLock(ui, world, camera);
        boolean hudShown = game.settings().showHud;
        // F3: what a developer wants to know - the sheets on screen, the frame times, every key
        boolean debug = game.settings().debugOverlay;
        if (debug) hud(ui);
        if (!pause.open() && table.open()) {
            table.draw(ui, frameDt);
        } else if (!pause.open()) {
            prompt(ui);
            float hotbar = debug ? ui.height() - 14 - 22 * HELP.length - 4 : ui.height() - 10;
            if (hudShown || inventory.open()) inventory.drawHotbar(ui, frameDt, hotbar);
            if (!inventory.open() && hudShown) {
                combatHud.drawMana(ui, world.combat(), hotbar - 34);
                combatHud.drawHeavy(ui, world.player(), hotbar - 34);
                combatHud.drawVitals(ui, world.player(), hotbar - 34);
                combatHud.drawStealth(ui, world, hotbar - 34);
                combatHud.drawSupplies(ui, world, hotbar - 34);
                combatHud.drawBuffs(ui, world.player(), 16, 120);
            }
            if (!game.importer().visible()) inventory.draw(ui);
        }
        if (!game.importer().visible()) pause.draw(ui, frameDt, world.player());
        if (choosing && !pause.open()) chooseClass(ui);
        ui.end();
    }

    /** The class picker, over the game at its start: the nine classes; a click is hers. */
    private void chooseClass(Ui ui) {
        ui.scrim();
        float w = Math.min(1100, ui.width() - 40), h = Math.min(660, ui.height() - 40);
        float x = (ui.width() - w) / 2, y = (ui.height() - h) / 2;
        ui.panel(x, y, w, h);
        ui.textCentered(ui.large, "Choose your class", x + w / 2, y + 14, Theme.TITLE);
        ui.textCentered(ui.small, "Your class gives you a deck of 8 cards: 4 in hand to start, one more every 5 minutes.",
                x + w / 2, y + 48, Theme.HINT);
        ui.textCentered(ui.small, "J or the inventory's Cards button plays them. Change it any time in Pause → Class.",
                x + w / 2, y + 48 + ui.small.lineHeight() + 3, Theme.HINT);
        String pick = ClassPicker.draw(ui, game.pointers(), null, x + 20, y + 92, w - 40, h - 112);
        if (pick == null) return;
        Player p = world.player();
        p.hero().choose(pick, p);
        game.settings().heroClass = pick;
        game.settings().save();
        choosing = false;
        Sounds.play(SoundKeys.ui("card_shuffle"));
        game.toast("You are a " + com.larsons.game.cards.CardBook.get().hero(pick).name() + " - press J for your cards",
                Theme.ACCENT);
    }

    /** The help bar along the bottom of the HUD. */
    private static final String[] HELP = {
            "WASD move · Shift run · Ctrl sprint · C crouch · Space jump · Alt/Z dash (hold: roll) · "
                    + "Click/F attack (bow: hold to draw) · X heavy · R spin · Q parry · B block · V bash",
            "E pick up / open / shop / go in, out, up, down / ride · G drop · I inventory · J cards · "
                    + "1-5 or wheel hotbar · click with food: eat · F1-F4 emote · [ ] preview · Backspace live",
            "L lock on · K next target · walk into vines: climb (off their top: climb down) · deep water: swim, "
                    + "C dive, Space up · attack in the air: plunge · Alt/Z in the air: land in a roll",
            "Tab height · O auto height · Right-drag orbit · +/- zoom · , . turn · T turntable · N (held) time-lapse · "
                    + "H HUD · F9 classic/new animations · F11 full screen · Esc menu · the hills are up the north path"};

    private void prompt(Ui ui) {
        if (inventory.open()) return;
        String text, name = "";
        float[] edge = Theme.ACCENT, nameColour = Theme.TITLE;
        Use use = usable();
        if (use == Use.DISMOUNT && world.player().speed() > 0.5) return;      // (only once it stands)
        if (use == Use.MOUNT) {
            text = "   Ride the horse";
        } else if (use == Use.SADDLE) {
            text = "   Saddle the horse";
        } else if (use == Use.DISMOUNT) {
            text = "   Get off the horse";
        } else if (use == Use.DOOR) {
            text = "   " + doorText(usableDoor());
        } else if (use == Use.PASSAGE) {
            text = "   " + passageText(usablePassage());
        } else if (use == Use.WARDROBE) {
            text = "   Change clothes at the " + world.reachableWardrobe().profile().label().toLowerCase();
        } else if (use == Use.CHEST) {
            Chest c = world.reachableChest();
            text = "   " + (c.store() != null ? "Look in the chest"
                    : c.isOpen() ? "Close the chest" : "Open the chest");
        } else if (use == Use.SHOP) {
            text = "   Shop at ";
            name = world.reachableShop().name();
            edge = nameColour = Theme.rgb(240, 192, 48, 1f);
        } else if (use == Use.STORE) {
            text = "   Look in the " + world.reachableStore().kind().label().toLowerCase();
        } else if (use == Use.ITEM) {
            // the item's name in its rarity's colour
            World.GroundItem g = world.reachable();
            text = "   Pick up ";
            name = (g.amount() > 1 ? g.amount() + " " : "") + g.item.fullName()
                    + (world.player().crouched() ? " (crouched)" : "");
            edge = nameColour = g.item.rarity().colour(1f);
        } else if (use == Use.BUSH) {
            ItemDef d = ItemDef.byId(world.reachableBush().profile().forage().item());
            text = "   Pick ";
            name = d == null ? "the bush" : d.name().toLowerCase();
            if (d != null) edge = nameColour = Theme.rgb((d.food().rgb() >> 16) & 255, (d.food().rgb() >> 8) & 255,
                    d.food().rgb() & 255, 1f);
        } else {
            return;
        }
        drawPrompt(ui, Action.USE, text.strip(), name, edge, nameColour);
    }

    /**
     * A prompt over the hotbar: what to press for {@code action} - the key's cap, or the gamepad's
     * button - then what it does, {@code name} after it in {@code nameColour}.
     */
    private void drawPrompt(Ui ui, Action action, String text, String name, float[] edge, float[] nameColour) {
        Input in = ui.input();
        float cap = com.larsons.game.ui.Glyphs.width(ui, in, action, 30);
        float tw = cap + 12 + ui.large.width(text + (name.isEmpty() ? "" : " " + name)) + 36;
        float x = (ui.width() - tw) / 2, y = ui.height() * 0.62f;
        ui.plate(x, y, tw, 44, edge);
        com.larsons.game.ui.Glyphs.draw(ui, in, action, x + 16, y + 7, 30);
        float done = ui.text(ui.large, text + (name.isEmpty() ? "" : " "), x + 16 + cap + 12, y + 9, Theme.TITLE);
        if (!name.isEmpty()) ui.text(ui.large, name, x + 16 + cap + 12 + done, y + 9, nameColour);
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
        ui.text(ui.body, level == null ? "Demo void" : name(), x, y, Theme.ACCENT);
        var pacer = game.pacer();
        String frames = String.format("%.0f fps  ·  %.1f ms  ·  longest %.1f ms  ·  %s",
                pacer.fps(), pacer.averageMs(), pacer.worstMs(), pacer.describe());
        ui.text(ui.small, frames, x + 646 - ui.small.width(frames), y + 3, Theme.ITEM_DISABLED);
        y += lh + 4;
        ui.text(ui.small, "GPU  " + gpu.describe(), x, y, gpu.software() && game.settings().gpuRequired
                ? Theme.WARNING : Theme.ITEM);
        y += lh;
        SpriteView v = r.view();
        ui.text(ui.small, String.format("Camera  pitch %.0f°  yaw %.0f°  zoom %.1f m   →  %s sprites (%d°)   ·   %s%s",
                        Math.toDegrees(camera.pitch()), Math.toDegrees(camera.yaw()) % 360, camera.distance(),
                        v.elevation().label(), (int) v.elevation().renderAngle(),
                        DayCycle.clock(world.clock().hour()),
                        world.clock().speed() > 1 ? " (time-lapse, N)" : world.sky().air().timeRuns() ? "" : " (time still)"),
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

    /** "6:30" or "6.5" as an hour of the day (6.5). */
    static double hourOf(String text) {
        int colon = text.indexOf(':');
        if (colon < 0) return Double.parseDouble(text);
        return Integer.parseInt(text.substring(0, colon).trim()) + Integer.parseInt(text.substring(colon + 1).trim()) / 60.0;
    }

    /**
     * A scripted pick-up without walking there: into the inventory (the
     * sword into her hand too; a wearable into no hand - it is hers to wear),
     * and the one lying about gone from the ground.
     */
    private boolean give(Item item) {
        Player p = world.player();
        ItemDef d = item.def();
        if ((d.isWearable() || d.isTack() ? p.inventory().stow(item) : p.inventory().add(item)) < 0) return false;
        world.items().stream().filter(g -> g.item.equals(item)).findFirst()
                .or(() -> world.items().stream().filter(g -> g.def == d && g.source() == null && !g.shown()).findFirst())
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
            // her class: hero <class> (chosen, its deck dealt), hero picker (the class picker up)
            case "hero" -> {
                String a = argument.trim();
                if (a.equals("picker")) choosing = true;
                else if (com.larsons.game.cards.CardBook.get().hero(a) == null) return false;
                else {
                    p.hero().choose(a, p);
                    choosing = false;
                }
            }
            // the card table (J): cards (laid, or cleared), cards hand|deck|draw|discard|perks (that page),
            // cards play <n> (the n-th card of her hand played), cards close
            case "cards" -> {
                String[] a = argument.trim().split("\\s+");
                switch (a[0]) {
                    case "" -> table.toggle();
                    case "close" -> table.close();
                    case "hand" -> table.show(CardTable.View.TABLE);
                    case "deck" -> table.show(CardTable.View.DECK);
                    case "draw" -> table.show(CardTable.View.DRAW);
                    case "discard" -> table.show(CardTable.View.DISCARD);
                    case "perks" -> table.show(CardTable.View.PERKS);
                    case "play" -> {
                        if (!table.play(a.length > 1 ? Integer.parseInt(a[1]) - 1 : 0)) return false;
                    }
                    default -> {
                        return false;
                    }
                }
                if (inventory.open() && table.open()) inventory.close();
            }
            // a weapon's tiers: tiers <slot 1-n>; tiers <slot> up (upgraded once)
            case "tiers" -> {
                String[] a = argument.trim().split("\\s+");
                int slot = Integer.parseInt(a[0]) - 1;
                if (!com.larsons.game.world.Upgrades.has(p.inventory().get(slot))) return false;
                if (a.length > 1 && a[1].equals("up")) com.larsons.game.world.Upgrades.upgrade(p.inventory(), slot);
                else inventory.showTiers(slot);
            }
            // the nearest shop: shop goto (at its counter, facing it), shop (open it, or shut it)
            case "shop" -> {
                if (world.shops().isEmpty()) return false;
                com.larsons.game.world.Shop sh = world.shops().get(0);
                for (com.larsons.game.world.Shop o : world.shops()) {
                    if (o.position().horizontalDistance(p.ground()) < sh.position().horizontalDistance(p.ground())) {
                        sh = o;
                    }
                }
                if (argument.trim().equals("goto")) {
                    Vec3 at = sh.position().add(SpriteView.headingVector(sh.heading()).scale(0.9));
                    p.setPosition(at);
                    p.snapHeading(SpriteView.headingOf(sh.position().x() - at.x(), sh.position().z() - at.z()));
                    camera.snapTo(follow());
                } else if (inventory.shop() == sh) {
                    inventory.close();
                } else {
                    inventory.showShop(sh);
                }
            }
            // a barrel, crate, pot or storage chest (the first, or store <n> ...): goto (beside it), fill <what>,
            // open, hit (break it)
            case "store" -> {
                if (world.stores().isEmpty()) return false;
                String a = argument.trim();
                int n = 0;
                if (a.matches("\\d+.*")) {
                    int sp = a.indexOf(' ');
                    n = Integer.parseInt(sp < 0 ? a : a.substring(0, sp)) - 1;
                    a = sp < 0 ? "" : a.substring(sp + 1).trim();
                }
                if (n < 0 || n >= world.stores().size()) return false;
                Store st = world.stores().get(n);
                if (a.equals("goto")) {
                    Vec3 at = st.position().add(SpriteView.headingVector(st.heading()).scale(st.radius() + 0.8));
                    p.setPosition(at);
                    p.snapHeading(SpriteView.headingOf(st.position().x() - at.x(), st.position().z() - at.z()));
                    camera.snapTo(follow());
                } else if (a.startsWith("fill")) {
                    st.fill(a.substring(4));
                } else if (a.equals("hit")) {
                    st.hurt(st.maxHealth() + 1, p.ground(), true);
                } else {
                    Chest c = null;
                    for (Chest ch : world.chests()) if (ch.store() == st) c = ch;
                    lookIn(st, c);
                }
            }
            // the weather now: weather <clear|rain|thunderstorm|snow|fog|wind|sandstorm> [how heavy]
            case "weather" -> {
                String[] a = argument.trim().split("\\s+");
                com.larsons.game.world.Weather.Kind k = com.larsons.game.world.Weather.Kind.byKey(a[0]);
                if (k == null) return false;
                world.setWeather(com.larsons.game.world.Weather.NONE.withMode(com.larsons.game.world.Weather.Mode.FIXED)
                        .withKind(k).withStrength(a.length > 1 ? Double.parseDouble(a[1]) : 0.9)
                        .withMode(k == com.larsons.game.world.Weather.Kind.CLEAR
                                ? com.larsons.game.world.Weather.Mode.NONE : com.larsons.game.world.Weather.Mode.FIXED));
                world.weatherNow().set(k, a.length > 1 ? Double.parseDouble(a[1]) : 0.9);
            }
            // where she stands, printed (for scripted checks)
            case "where" -> System.out.printf(java.util.Locale.ROOT, "[where] %.2f %.2f %.2f heading %.1f%n",
                    p.feet().x(), p.feet().y(), p.feet().z(), Math.toDegrees(p.heading()));
            case "teleport" -> {
                String[] a = argument.split("[ ,]+");
                p.setPosition(new Vec3(Double.parseDouble(a[0]), 0, Double.parseDouble(a[1])));
                camera.snapTo(pivot());
            }
            // the highlands' spots: goto spawn|hills|ramp|plateau|ledge|cliff|hill|hollow
            case "goto" -> {
                Terrain.Place place = world.terrain().place(argument.trim());
                Vec3 at = argument.trim().equals("spawn") ? world.spawnPoint()
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
            // the animals: animal <kind> [temperament] [metres] (one put in front of her, facing her),
            // animal goto <kind> (her put by the nearest), animal lock <kind> (locked on to the nearest)
            case "animal" -> {
                String[] a = argument.trim().split("\\s+");
                if (a.length >= 2 && (a[0].equals("goto") || a[0].equals("lock"))) {
                    com.larsons.game.ambient.AnimalKind k = com.larsons.game.ambient.AnimalKind.byKey(a[1]);
                    if (k == null) return false;
                    com.larsons.game.ambient.Animal best = null;
                    double bd = Double.MAX_VALUE;
                    for (com.larsons.game.ambient.Animal an : world.animals().live()) {
                        double d = an.position().horizontalDistance(p.ground());
                        if (an.kind() == k && an.alive() && d < bd) {
                            bd = d;
                            best = an;
                        }
                    }
                    if (best == null) return false;
                    if (a[0].equals("lock")) {
                        world.combat().lock(best);
                    } else {
                        Vec3 at = world.terrain().onGround(best.position().add(2.2 + best.radius(), 0, 1.2));
                        p.setPosition(at);
                        p.snapHeading(SpriteView.headingOf(best.position().x() - at.x(), best.position().z() - at.z()));
                        camera.snapTo(follow());
                    }
                } else {
                    com.larsons.game.ambient.AnimalKind k = com.larsons.game.ambient.AnimalKind.byKey(a[0]);
                    if (k == null) return false;
                    com.larsons.game.ambient.Temperament t = a.length > 1
                            ? com.larsons.game.ambient.Temperament.byKey(a[1]) : null;
                    double m = a.length > 2 ? Double.parseDouble(a[2]) : 3;
                    Vec3 at = world.terrain().onGround(p.ground().add(SpriteView.headingVector(p.heading()).scale(m)));
                    com.larsons.game.ambient.Animal an = world.addAnimal(k, at, t);
                    an.faceTo(p.ground());
                }
            }
            case "time" -> world.clock().setHour(hourOf(argument.trim()));
            case "timelapse" -> {
                String a = argument.trim();
                scriptedTimeLapse = !a.equals("off");
                world.clock().setSpeed(a.equals("off") ? 1 : a.equals("on") || a.isEmpty() ? DayCycle.TIME_LAPSE
                        : Double.parseDouble(a));
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
            // riding: a saddled horse put before her ("steed [metres]"), saddling the one by her, on, off
            case "steed" -> {
                double m = argument.isBlank() ? 3 : Double.parseDouble(argument.trim());
                Vec3 at = world.terrain().onGround(p.ground().add(SpriteView.headingVector(p.heading()).scale(m)));
                com.larsons.game.ambient.Animal an = world.addAnimal(com.larsons.game.ambient.AnimalKind.HORSE, at,
                        null);
                an.setSaddled(true);
                an.faceTo(at.add(SpriteView.headingVector(p.heading() + Math.PI / 2)));
            }
            case "saddle" -> saddleHorse();
            case "ride", "mount" -> world.mount(world.reachableHorse());
            case "getoff", "dismount" -> world.dismount();
            case "door" -> goThrough(usableDoor());
            case "enter" -> {
                // into the nearest building with an inside, wherever it is
                Building best = null;
                for (Building b : world.buildings()) {
                    if (b.profile().room() != null || !b.profile().interior() || b.door() == null) continue;
                    if (best == null || b.position().horizontalDistance(p.ground())
                            < best.position().horizontalDistance(p.ground())) best = b;
                }
                if (best != null) goIn(best);
            }
            case "outside" -> goOut();
            case "upstairs", "downstairs" -> {
                boolean up = command.equals("upstairs");
                Building best = null;
                for (Building b : world.buildings()) {
                    Room.Spec r = b.profile().room();
                    if (r == null || r.piece() != Room.Piece.STAIRS || r.up() != up) continue;
                    if (best == null || b.position().horizontalDistance(p.ground())
                            < best.position().horizontalDistance(p.ground())) best = b;
                }
                if (best != null) climb(up, best.position());
            }
            // the foraging bushes: forage (pick the one in reach), forage goto [n] (stand by the nth
            // nearest with something on it), eat [slot] (eat from that slot, else what is in hand)
            case "forage" -> {
                String a = argument.trim();
                if (a.startsWith("goto")) {
                    String[] w = a.split("\\s+");
                    int n = w.length > 1 ? Integer.parseInt(w[1]) - 1 : 0;
                    Vec3 me = p.ground();
                    List<Scenery.Placed> ripe = new ArrayList<>();
                    for (Scenery.Placed b : world.bushes()) if (!world.picked(b)) ripe.add(b);
                    ripe.sort(java.util.Comparator.comparingDouble(b -> Math.hypot(b.x() - me.x(), b.z() - me.z())));
                    if (n < 0 || n >= ripe.size()) return false;
                    Scenery.Placed b = ripe.get(n);
                    Vec3 at = new Vec3(b.x() + 0.9, 0, b.z() + 0.6);
                    p.setPosition(at);
                    p.snapHeading(SpriteView.headingOf(b.x() - at.x(), b.z() - at.z()));
                    camera.snapTo(follow());
                } else {
                    Scenery.Placed b = world.reachableBush();
                    if (b == null) return false;
                    forage(b);
                }
            }
            case "eat" -> {
                if (argument.isBlank()) {
                    eatHeld();
                } else {
                    World.Eaten e = world.eat(Integer.parseInt(argument.trim()) - 1);
                    if (e == null) return false;
                }
            }
            case "dropitem" -> drop();
            case "pause" -> pause.open(PauseMenu.Page.valueOf(
                    argument.isBlank() ? "MAIN" : argument.toUpperCase()));
            case "resume" -> pause.close();
            case "hud" -> game.settings().showHud = !argument.equals("off");
            case "animations" -> setClassic(argument.trim().equals("classic") || argument.trim().equals("v1"));
            case "props", "scenery" -> {
                game.settings().showProps = !argument.equals("off");
                world.setScenery(shownScenery());
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
