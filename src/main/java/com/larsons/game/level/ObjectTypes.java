package com.larsons.game.level;

import com.larsons.game.ambient.AnimalKind;
import com.larsons.game.ambient.Animals;
import com.larsons.game.ambient.CritterKind;
import com.larsons.game.ambient.Critters;
import com.larsons.game.ambient.Temperament;
import com.larsons.game.audio.SoundKeys;
import com.larsons.game.building.Building;
import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.building.BuildingLook;
import com.larsons.game.building.BuildingProfile;
import com.larsons.game.building.Prefab;
import com.larsons.game.building.Room;
import com.larsons.game.combat.Dummy;
import com.larsons.game.combat.FlyingDummy;
import com.larsons.game.level.ObjectType.Category;
import com.larsons.game.level.ObjectType.Prop;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.world.Chest;
import com.larsons.game.world.Item;
import com.larsons.game.world.Pickings;
import com.larsons.game.world.Tack;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Scenery;
import com.larsons.game.world.Tier;
import com.larsons.game.world.Wearables;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every kind of thing a level can hold, by key - and the editor's palette of
 * them ({@link #palette}): each tree, rock and tuft of {@code assets/scenery},
 * every weapon and every wearable, the chest, the training dummy and the
 * flying dummy, the vines she climbs, sounds that keep playing where they
 * are put, the player's spawn and named spots.
 *
 * <p>A new kind is one more {@link #register} call: its props are edited in
 * the editor and kept in the level's file with no more work, and its
 * spawner says what it is in the world.
 */
public final class ObjectTypes {

    public static final String SPAWN = "spawn", MARKER = "marker", SCENERY = "scenery", ITEM = "item",
            CHEST = "chest", DUMMY = "dummy", FLIER = "flier", VINES = "vines", SOUND = "sound", LIGHT = "light",
            RAYS = "rays", CRITTERS = "critters", ANIMALS = "animals", BUILDING = "building", PREFAB = "prefab",
            WALL = "wall", FLOOR = "floor", STAIRS = "stairs", EXIT = "exit", TEMPLATE = "template";

    /** The colours a placed light can be, by name, and what they are. */
    public static final Map<String, Integer> LIGHT_COLOURS = lightColours();

    private static Map<String, Integer> lightColours() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("lantern", 0xFFC468);
        m.put("candle", 0xFFA448);
        m.put("fire", 0xFF7A30);
        m.put("moonlight", 0x9CB8FF);
        m.put("crystal", 0x7FE8FF);
        m.put("magic", 0xC890FF);
        m.put("fairy", 0x9CFF8A);
        m.put("rose", 0xFF8AB0);
        m.put("white", 0xFFFFFF);
        return Collections.unmodifiableMap(m);
    }

    /** What {@code o}'s {@code "tint"} and {@code "tintAmount"} make: {@code 0xAARRGGBB} (alpha: how much), or 0: none. */
    public static int tint(LevelObject o) {
        int rgb = hex(o.str("tint", ""));
        if (rgb < 0) return 0;
        int a = (int) Math.round(Math.max(0, Math.min(1, o.num("tintAmount", 0.6))) * 255);
        return a << 24 | rgb;
    }

    /** {@code "#rrggbb"} (or {@code "rrggbb"}) as {@code 0xRRGGBB}, or -1. */
    public static int hex(String s) {
        if (s == null) return -1;
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) return -1;
        try {
            return Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** What a herd's colours are called in its file: {@code "natural"} for one as drawn. */
    public static final String NATURAL = "natural";

    /** A herd's colours ({@code 0xRRGGBB}, -1 for one as drawn); none: all as drawn. */
    public static List<Integer> herdColours(LevelObject o) {
        List<Integer> out = new ArrayList<>();
        if (o.props().get("colours") instanceof List<?> list) {
            for (Object v : list) {
                String s = String.valueOf(v);
                int c = s.equals(NATURAL) ? -1 : hex(s);
                if (c >= 0 || s.equals(NATURAL)) out.add(c);
            }
        }
        return out;
    }

    /** The colour named {@code name} ({@link #LIGHT_COLOURS}), a {@code #RRGGBB}, or the lantern's. */
    public static int lightColour(String name) {
        Integer c = LIGHT_COLOURS.get(name);
        if (c != null) return c;
        String h = name == null ? "" : name.startsWith("#") ? name.substring(1) : name;
        try {
            return h.length() == 6 ? Integer.parseInt(h, 16) : 0xFFC468;
        } catch (NumberFormatException e) {
            return 0xFFC468;
        }
    }

    /** The chest's sprites (assets/sprites/objects/). */
    public static final String CHEST_SPRITE = "ornate_chest";

    private static final Map<String, ObjectType> TYPES = new LinkedHashMap<>();

    static {
        register(new ObjectType(SPAWN, "Player spawn", Category.MARKERS, List.of(), 0.5, 1.7, 0x6FC3FF, true,
                (o, b) -> b.spawnAt(o)));
        register(new ObjectType(MARKER, "Named spot", Category.MARKERS,
                List.of(Prop.text("name", "Name", "spot")), 0.4, 1.2, 0xFFD27A, false, null));
        register(new ObjectType(SCENERY, "Scenery", Category.SCENERY,
                List.of(Prop.choice("sprite", "Sprite", "oak_tree", o -> sceneryNames()),
                        Prop.toggle("mirrored", "Mirrored", false),
                        Prop.number("scale", "Size", 1, 0.2, 5, 0.05),
                        Prop.colour("tint", "Tint"),
                        Prop.number("tintAmount", "Tint strength", 0.6, 0.05, 1, 0.05)), 0.6, 2.0, 0x7FBF6A, false,
                (o, b) -> b.plant(o)));
        register(new ObjectType(ITEM, "Item", Category.ITEMS,
                List.of(Prop.choice("item", "Item", "sword", o -> itemIds()),
                        Prop.choice("tier", "Tier", "", ObjectTypes::tiersOf)), 0.45, 1.2, 0xE0C080, false,
                (o, b) -> {
                    Item item = item(o);
                    if (item != null) b.world().spawn(item, b.onGround(o));
                }));
        register(new ObjectType(CHEST, "Treasure chest", Category.LOOT,
                List.of(Prop.choice("sprite", "Sprite", CHEST_SPRITE, o -> List.of(CHEST_SPRITE))), Chest.RADIUS, 0.9,
                0xC89A50, false,
                (o, b) -> b.world().addChest(new Chest(o.str("sprite", CHEST_SPRITE), b.onGround(o), o.yaw()))));
        register(new ObjectType(DUMMY, "Training dummy", Category.COMBAT,
                List.of(Prop.number("health", "Health", 100, 1, 10000, 10)), Dummy.RADIUS, Dummy.HEIGHT, 0xC08A60,
                false, (o, b) -> b.world().addDummy(new Dummy(b.onGround(o), o.yaw(), o.num("health", 100),
                        b.tuning().dummyDownSeconds))));
        register(new ObjectType(FLIER, "Flying dummy", Category.COMBAT,
                List.of(Prop.number("health", "Health", 60, 1, 10000, 10)), 0.8, 1.2, 0x9CC4FF, false,
                (o, b) -> b.world().addFlier(new FlyingDummy(new Vec3(o.x(), 0, o.z()), o.yaw(),
                        o.num("health", 60), b.tuning().dummyDownSeconds, b.terrain()))));
        // vines on a rock face she climbs: placed on a cliff, they fit themselves to it - facing out of it, at
        // its foot, as long as it runs - unless set not to (then they hang where they are put, as turned)
        register(new ObjectType(VINES, "Climbing vines", Category.TERRAIN,
                List.of(Prop.number("length", "Length (m)", 8, 1, 60, 0.5),
                        Prop.toggle("fit", "Fit to the rock face", true)), 0.6, 3, 0x5F9E3A, false, null));
        register(new ObjectType(SOUND, "Sound", Category.SOUNDS,
                List.of(Prop.choice("sound", "Sound", SoundKeys.ambient("stream"), o -> soundKeys())), 0.5, 1.2,
                0xB48CFF, false,
                (o, b) -> b.world().soundSpots().add(new World.SoundSpot(o.str("sound", SoundKeys.ambient("stream")),
                        b.onGround(o).add(0, 1, 0)))));
        register(new ObjectType(LIGHT, "Light", Category.LIGHTS,
                List.of(Prop.choice("colour", "Colour", "lantern", o -> List.copyOf(LIGHT_COLOURS.keySet())),
                        Prop.number("radius", "Reach (m)", 5, 0.5, 30, 0.5),
                        Prop.number("strength", "Strength", 1, 0.1, 2, 0.1),
                        Prop.number("height", "Height (m)", 1.2, 0, 12, 0.1),
                        Prop.toggle("glow", "Shows a glow", true),
                        Prop.choice("fixture", "Fitting", "candlestick", o -> fixtures())), 0.4, 1.4, 0xFFC468, false,
                ObjectTypes::spawnLight));
        register(new ObjectType(RAYS, "Light rays", Category.LIGHTS,
                List.of(Prop.number("count", "Shafts", 4, 1, 16, 1),
                        Prop.number("spread", "Spread (m)", 1.5, 0.2, 8, 0.1),
                        Prop.number("height", "From (m up)", 6, 1, 30, 0.5),
                        Prop.number("strength", "Strength", 1, 0.1, 2, 0.1)), 0.6, 2.0, 0xFFF0B0, false,
                (o, b) -> b.world().raySpots().add(new World.RaySpot(b.onGround(o), (int) o.num("count", 4),
                        o.num("spread", 1.5), o.num("height", 6), o.num("strength", 1)))));
        // a swarm of the little creatures living round a spot - fireflies over a pond, frogs at its edge -
        // as well as those that come by on their own (the Level tool's Life page says how many of those)
        register(new ObjectType(CRITTERS, "Critters", Category.LIFE,
                List.of(Prop.choice("kind", "Kind", CritterKind.FIREFLY.key(), o -> critterKinds()),
                        Prop.number("count", "How many", 8, 1, 40, 1),
                        Prop.number("radius", "Within (m)", 4, 0.5, 30, 0.5),
                        Prop.choice("when", "When", Critters.When.ITS_TIME.key(), o -> critterTimes())),
                0.5, 0.8, 0xD8FF5A, false,
                (o, b) -> {
                    CritterKind k = CritterKind.byKey(o.str("kind", CritterKind.FIREFLY.key()));
                    if (k == null) return;
                    b.world().critters().spots().add(new Critters.Spot(k, b.onGround(o),
                            (int) Math.round(o.num("count", 8)), o.num("radius", 4),
                            Critters.When.byKey(o.str("when", Critters.When.ITS_TIME.key()))));
                }));
    }

    /** What a herd's temperament can be: "own" (its kind's), or one of them all. */
    public static final String OWN = "own";

    static {
        // a herd of animals living round a spot - cows in a field, geese by a pond, a bear's den - as well as
        // those that come by on their own (the Level tool's Life page says how many of those)
        register(new ObjectType(ANIMALS, "Animals", Category.LIFE,
                List.of(Prop.choice("kind", "Kind", AnimalKind.COW.key(), o -> animalKinds()),
                        Prop.number("count", "How many", 3, 1, 30, 1),
                        Prop.number("radius", "Within (m)", 8, 1, 60, 0.5),
                        Prop.choice("temperament", "Nature", OWN, o -> temperaments()),
                        Prop.choice("when", "When", Critters.When.ITS_TIME.key(), o -> critterTimes()),
                        Prop.toggle("saddled", "Saddled", false),
                        Prop.toggle("single", "One animal, just here", false),
                        Prop.colours("colours", "Colours"),
                        Prop.number("size", "Size", 1, 0.3, 3, 0.05),
                        Prop.number("vary", "Sizes vary (±)", 0, 0, 0.6, 0.05)),
                0.6, 1.2, 0xE8B070, false,
                (o, b) -> {
                    AnimalKind k = AnimalKind.byKey(o.str("kind", AnimalKind.COW.key()));
                    if (k == null) return;
                    boolean single = o.bool("single", false);
                    double size = o.num("size", 1), vary = o.num("vary", 0);
                    b.world().animals().herds().add(new Animals.Herd(k, b.onGround(o),
                            single ? 1 : (int) Math.round(o.num("count", 3)), o.num("radius", single ? 3 : 8),
                            Temperament.byKey(o.str("temperament", OWN)),
                            Critters.When.byKey(o.str("when", Critters.When.ITS_TIME.key())),
                            k == AnimalKind.HORSE && o.bool("saddled", false), herdColours(o),
                            size * (1 - vary), size * (1 + vary), single, o.yaw()));
                }));
    }

    static {
        // a house, a mill, a castle's keep, a length of its wall: coloured and patterned part by part (its look)
        register(new ObjectType(BUILDING, "Building", Category.BUILDINGS,
                List.of(Prop.choice("kind", "Building", "cottage", o -> BuildingCatalog.get().keys())),
                3, 5, 0xC8A27A, false,
                (o, b) -> {
                    Building building = building(o, b.terrain());
                    if (building != null) b.world().addBuilding(building);
                }));
        // a set of buildings placed together (a small castle): each piece is a building of its own once placed
        register(new ObjectType(PREFAB, "Set of buildings", Category.BUILDINGS,
                List.of(Prop.choice("set", "Set", "small_castle", o -> prefabKeys())), 12, 10, 0xC8A27A, false, null));
        // a saved template (Templates): its pieces, each one of its own once placed
        register(new ObjectType(TEMPLATE, "Template", Category.TEMPLATES,
                List.of(Prop.choice("template", "Template", "", o -> templateIds())), 4, 3, 0xB8A0E0, false, null));
    }

    /**
     * What a light is: a candle in a candlestick (set on a table, a shelf),
     * a lone candle, a candelabra, a standing candelabra (on the floor), a
     * chandelier (hung from the beams), a sconce on the wall, a lantern, a
     * lantern on its post (outdoors) - or a glow on its own (magic).
     */
    static List<String> fixtures() {
        return List.of("candlestick", "candle", "candelabra", "standing_candelabra", "chandelier", "wall_sconce",
                "lantern", "lantern_post", "glow");
    }

    /**
     * A light: its fitting, its flame at the light's height (one standing on
     * the floor stands on the ground, the light at its flame), and the light
     * itself.
     */
    static void spawnLight(LevelObject o, LevelWorld.Build b) {
        Vec3 at = b.onGround(o).add(0, o.num("height", 1.2), 0);
        BuildingProfile fit = BuildingCatalog.get().profile(o.str("fixture", "candlestick"));
        boolean flame = fit != null && fit.flame() != null;
        if (flame) {
            double[] f = fit.flame();
            Building probe = new Building(o.id(), fit, Vec3.ZERO, o.yaw(), null, null);
            Vec3 off = probe.toWorld(f[0], f[1]);            // where its flame is from where it stands
            Vec3 stand = fit.stands() ? b.onGround(o) : at.sub(new Vec3(off.x(), f[2], off.z()));
            b.world().addBuilding(new Building(o.id(), fit, stand, o.yaw(), BuildingLook.of(o.props().get("look")),
                    null));
            at = stand.add(off.x(), f[2], off.z());
        }
        b.world().lights().add(new World.LightSpot(at, o.num("radius", 5), lightColour(o.str("colour", "lantern")),
                o.num("strength", 1), o.bool("glow", true), flame));
    }

    static List<String> templateIds() {
        List<String> out = new ArrayList<>();
        for (Templates.Template t : Templates.all()) out.add(t.id());
        return out;
    }

    static {
        // what rooms are built of, inside a building: walls, floors, stairs, the door out (Room)
        register(new ObjectType(WALL, "Wall", Category.INSIDE,
                List.of(Prop.number("length", "Length (m)", 4, 0.5, 24, 0.25),
                        Prop.number("height", "Height (m)", Room.STOREY, 1, 8, 0.25),
                        Prop.choice("opening", "Opening", "none", o -> openings())), 1, Room.STOREY, 0xD8C8A0, false,
                ObjectTypes::spawnRoom));
        register(new ObjectType(FLOOR, "Floor", Category.INSIDE,
                List.of(Prop.number("width", "Width (m)", 4, 0.5, 40, 0.5),
                        Prop.number("depth", "Depth (m)", 4, 0.5, 40, 0.5)), 1, 0.1, 0x9A6A40, false,
                ObjectTypes::spawnRoom));
        register(new ObjectType(STAIRS, "Stairs", Category.INSIDE,
                List.of(Prop.choice("way", "Going", "up", o -> List.of("up", "down"))), 1.6, Room.STOREY, 0xB08050,
                false, ObjectTypes::spawnRoom));
        register(new ObjectType(EXIT, "Door out", Category.INSIDE, List.of(), 0.9, Room.EXIT_H, 0x7AA0D0, false,
                ObjectTypes::spawnRoom));
    }

    /** Whether objects of {@code type} stand in the world as {@link Building}s: buildings, furniture, the rooms' pieces. */
    public static boolean builds(String type) {
        return type.equals(BUILDING) || type.equals(WALL) || type.equals(FLOOR) || type.equals(STAIRS)
                || type.equals(EXIT);
    }

    static List<String> openings() {
        List<String> out = new ArrayList<>();
        for (Room.Opening o : Room.Opening.values()) out.add(o.key());
        return out;
    }

    private static void spawnRoom(LevelObject o, LevelWorld.Build b) {
        b.world().addBuilding(room(o, b.terrain()));
    }

    /** The shape of a room's piece {@code o} ({@link #WALL}, {@link #FLOOR}, {@link #STAIRS}, {@link #EXIT}), or null. */
    public static Room.Spec roomSpec(LevelObject o) {
        return switch (o.type()) {
            case WALL -> Room.wall(o.num("length", 4), o.num("height", Room.STOREY),
                    Room.Opening.byKey(o.str("opening", "none")));
            case FLOOR -> Room.floor(o.num("width", 4), o.num("depth", 4));
            case STAIRS -> Room.stairs(!o.str("way", "up").equals("down"));
            case EXIT -> Room.exit();
            default -> null;
        };
    }

    /** A room's piece as it stands on {@code terrain}. */
    public static Building room(LevelObject o, com.larsons.game.world.Terrain terrain) {
        Room.Spec spec = roomSpec(o);
        return new Building(o.id(), Room.profile(spec), new Vec3(o.x(), terrain.height(o.x(), o.z()) + o.lift(), o.z()),
                o.yaw(), BuildingLook.of(o.props().get("look")), null);
    }

    /** What {@code o} stands as in the world, if it {@link #builds}: a building, a piece of furniture, a room's piece. */
    public static Building built(LevelObject o, com.larsons.game.world.Terrain terrain) {
        if (o.type().equals(BUILDING)) return building(o, terrain);
        return roomSpec(o) == null ? null : room(o, terrain);
    }

    /** A {@link #BUILDING} as it stands on {@code terrain}, or null when its kind is not there. */
    public static Building building(LevelObject o, com.larsons.game.world.Terrain terrain) {
        BuildingProfile p = BuildingCatalog.get().profile(o.str("kind", ""));
        if (p == null) return null;
        // on a slope it is set down to the lowest of its corners - dug into the hill, never standing off it
        Building at = new Building(o.id(), p, new Vec3(o.x(), 0, o.z()), o.yaw(), null, null);
        double y = terrain.height(o.x(), o.z());
        for (BuildingProfile.Shape s : p.footprint()) {
            double hw = s.circle() ? s.w() * 0.7 : s.w() / 2, hd = s.circle() ? s.w() * 0.7 : s.d() / 2;
            for (int i = 0; i < 4; i++) {
                Vec3 c = at.toWorld(s.x() + (i % 2 == 0 ? -hw : hw), s.y() + (i < 2 ? -hd : hd));
                y = Math.min(y, terrain.height(c.x(), c.z()));
            }
        }
        y = Math.max(y, terrain.height(o.x(), o.z()) - 1.5);
        return new Building(o.id(), p, new Vec3(o.x(), y + o.lift(), o.z()), o.yaw(),
                BuildingLook.of(o.props().get("look")), o.str("interior", null));
    }

    static List<String> prefabKeys() {
        List<String> out = new ArrayList<>();
        for (Prefab p : Prefab.ALL) out.add(p.key());
        return out;
    }

    /**
     * What placing {@code o} puts in the level: {@code o} itself, or - a
     * {@link #PREFAB} - its buildings, each a {@link #BUILDING} of its own
     * where it stands in the set, numbered from {@code firstId}.
     */
    public static List<LevelObject> expand(LevelObject o, int firstId) {
        if (o.type().equals(TEMPLATE)) return Templates.expand(o, firstId);
        if (!o.type().equals(PREFAB)) return List.of(o);
        Prefab set = Prefab.byKey(o.str("set", ""));
        if (set == null) return List.of();
        Vec3 h = com.larsons.game.sprite.SpriteView.headingVector(o.yaw());
        double hx = h.x(), hz = h.z();
        List<LevelObject> out = new ArrayList<>();
        int id = firstId;
        for (Prefab.Piece p : set.pieces()) {
            // the set's own metres to the world's, as a building's are (Building#toWorld)
            double x = o.x() + p.x() * hz - p.y() * hx, z = o.z() - p.x() * hx - p.y() * hz;
            double fx = p.faceX() * hz - p.faceY() * hx, fz = -p.faceX() * hx - p.faceY() * hz;
            LevelObject piece = new LevelObject(id++, BUILDING, x, z, o.lift(),
                    com.larsons.game.sprite.SpriteView.headingOf(fx, fz), Map.of("kind", p.kind()));
            out.add(piece.turnTo(piece.yaw()));
        }
        return out;
    }

    static List<String> animalKinds() {
        List<String> out = new ArrayList<>();
        for (AnimalKind k : AnimalKind.values()) out.add(k.key());
        return out;
    }

    static List<String> temperaments() {
        List<String> out = new ArrayList<>();
        out.add(OWN);
        for (Temperament t : Temperament.values()) out.add(t.key());
        return out;
    }

    static List<String> critterKinds() {
        List<String> out = new ArrayList<>();
        for (CritterKind k : CritterKind.values()) out.add(k.key());
        return out;
    }

    static List<String> critterTimes() {
        List<String> out = new ArrayList<>();
        for (Critters.When w : Critters.When.values()) out.add(w.key());
        return out;
    }

    private static ScenerySprites scenerySprites = ScenerySprites.NONE;

    private ObjectTypes() {}

    /** Add (or replace) a kind. */
    public static void register(ObjectType type) {
        TYPES.put(type.key(), type);
    }

    /** The kind written {@code key}, or null for one the game does not know. */
    public static ObjectType get(String key) {
        return TYPES.get(key);
    }

    /** Every kind, in the order registered. */
    public static List<ObjectType> all() {
        return List.copyOf(TYPES.values());
    }

    /** The scenery sprites the scenery's choices are made from (set once their folder has been read). */
    public static void useScenery(ScenerySprites sprites) {
        scenerySprites = sprites == null ? ScenerySprites.NONE : sprites;
    }

    static List<String> sceneryNames() {
        List<String> out = new ArrayList<>();
        for (ScenerySprites.Sprite s : scenerySprites.placeable()) out.add(s.name());
        return out;
    }

    static List<String> itemIds() {
        List<String> out = new ArrayList<>();
        for (ItemDef d : ItemDef.ALL) out.add(d.id());
        for (ItemDef d : Wearables.ALL) out.add(d.id());
        for (ItemDef d : Pickings.ALL) out.add(d.id());
        for (ItemDef d : com.larsons.game.world.Hunted.ALL) out.add(d.id());
        for (ItemDef d : Tack.ALL) out.add(d.id());
        return out;
    }

    /** The weapon, wearable or picking called {@code id}, or null. */
    public static ItemDef itemDef(String id) {
        return ItemDef.byId(id);
    }

    static List<String> tiersOf(LevelObject o) {
        Tier.Row row = Tier.row(o.str("item", ""));
        if (row == null) return List.of("");
        List<String> out = new ArrayList<>();
        for (Tier t : row.tiers()) out.add(t.key());
        return out;
    }

    /** The item an {@link #ITEM} object lies as: its tier if it names one it has, else as it is found. */
    public static Item item(LevelObject o) {
        ItemDef d = itemDef(o.str("item", ""));
        if (d == null) return null;
        Tier tier = Tier.byKey(o.str("tier", ""));
        Tier.Row row = Tier.row(d.id());
        if (tier != null && row != null && row.names().containsKey(tier)) return Item.of(d, tier);
        return Item.of(d);
    }

    /** One entry of the editor's palette: what it is called, where it is filed, and what it places. */
    public record Entry(String label, Category category, String type, Map<String, Object> props) {

        /** A new object of it, numbered {@code id}, at {@code (x, z)}, facing {@code yaw}. */
        public LevelObject make(int id, double x, double z, double yaw) {
            return new LevelObject(id, type, x, z, yaw, LevelObject.deepCopy(props));
        }
    }

    /**
     * The editor's palette: every kind there is, the scenery and the items
     * one entry each, filed by {@link Category}.
     */
    public static List<Entry> palette() {
        List<Entry> out = new ArrayList<>();
        for (SceneryProfile.Kind k : List.of(SceneryProfile.Kind.TREE, SceneryProfile.Kind.ROCK,
                SceneryProfile.Kind.TUFT)) {
            for (ScenerySprites.Sprite s : scenerySprites.ofKind(k)) {
                if (s.profile().forages()) continue;        // (with the life)
                out.add(new Entry(readable(s.name()), Category.SCENERY, SCENERY, Map.of("sprite", s.name())));
            }
        }
        // the life: the foraging bushes, what they give, the little creatures
        for (ScenerySprites.Sprite s : scenerySprites.forage()) {
            out.add(new Entry(readable(s.name()), Category.LIFE, SCENERY, Map.of("sprite", s.name())));
        }
        for (ItemDef d : ItemDef.ALL) {
            Tier.Row row = Tier.row(d.id());
            out.add(new Entry(Item.of(d).name(), Category.ITEMS, ITEM,
                    row == null ? Map.of("item", d.id()) : Map.of("item", d.id(), "tier", row.lowest().key())));
        }
        for (ItemDef d : Wearables.ALL) {
            out.add(new Entry(d.name(), Category.WEARABLES, ITEM, Map.of("item", d.id())));
        }
        for (ItemDef d : Pickings.ALL) {
            out.add(new Entry(d.name(), Category.LIFE, ITEM, Map.of("item", d.id())));
        }
        for (ItemDef d : com.larsons.game.world.Hunted.ALL) {
            out.add(new Entry(d.name(), Category.LIFE, ITEM, Map.of("item", d.id())));
        }
        for (CritterKind k : CritterKind.values()) {
            out.add(new Entry(k.label(), Category.LIFE, CRITTERS, Map.of("kind", k.key(), "count",
                    (double) (k == CritterKind.FIREFLY ? 14 : 6), "radius", 4.0, "when", Critters.When.ITS_TIME.key())));
        }
        for (AnimalKind k : AnimalKind.values()) {
            int n = Math.max(1, Math.min(4, k.herd()));
            out.add(new Entry(k.label(), Category.LIFE, ANIMALS, Map.of("kind", k.key(), "count", (double) n,
                    "radius", k.flies() ? 4.0 : 8.0, "temperament", OWN, "when", Critters.When.ITS_TIME.key())));
        }
        // a horse waiting with its saddle on, to ride; and the saddle, for her to saddle one with
        out.add(new Entry("Saddled horse", Category.LIFE, ANIMALS, Map.of("kind", AnimalKind.HORSE.key(), "count",
                1.0, "radius", 2.0, "temperament", OWN, "when", Critters.When.ALWAYS.key(), "saddled", true)));
        for (ItemDef d : Tack.ALL) out.add(new Entry(d.name(), Category.LIFE, ITEM, Map.of("item", d.id())));
        // the rooms' pieces: walls with each opening, a floor, stairs up and down, the door out
        for (Room.Opening op : Room.Opening.values()) {
            out.add(new Entry(op == Room.Opening.NONE ? "Wall" : "Wall: " + op.label().toLowerCase(), Category.INSIDE,
                    WALL, Map.of("length", 4.0, "height", Room.STOREY, "opening", op.key())));
        }
        out.add(new Entry("Floor", Category.INSIDE, FLOOR, Map.of("width", 4.0, "depth", 4.0)));
        out.add(new Entry("Stairs up", Category.INSIDE, STAIRS, Map.of("way", "up")));
        out.add(new Entry("Stairs down", Category.INSIDE, STAIRS, Map.of("way", "down")));
        out.add(new Entry("Door out", Category.INSIDE, EXIT, Map.of()));
        BuildingCatalog buildings = BuildingCatalog.get();
        for (String key : buildings.keys()) {
            BuildingProfile p = buildings.profile(key);
            if (p.fixture()) continue;                  // (a light's fitting: placed as a light)
            out.add(new Entry(p.label(), p.furniture() ? Category.INSIDE : Category.BUILDINGS, BUILDING,
                    Map.of("kind", key)));
        }
        for (Prefab p : Prefab.ALL) {
            boolean all = true;
            for (Prefab.Piece piece : p.pieces()) all &= buildings.profile(piece.kind()) != null;
            if (all) out.add(new Entry(p.label(), Category.BUILDINGS, PREFAB, Map.of("set", p.key())));
        }
        for (String key : soundKeys()) {
            out.add(new Entry(readable(key), Category.SOUNDS, SOUND, Map.of("sound", key)));
        }
        // the lights: each fitting, at the height it is usually at, in the light it gives
        Object[][] lights = {{"Candlestick (on a table)", "candlestick", 1.2, "candle", 4.0},
                {"Candle", "candle", 0.9, "candle", 3.0}, {"Candelabra (on a table)", "candelabra", 1.25, "candle", 5.0},
                {"Standing candelabra", "standing_candelabra", 1.6, "candle", 5.0},
                {"Chandelier", "chandelier", 2.7, "candle", 7.0}, {"Wall sconce", "wall_sconce", 1.7, "candle", 4.5},
                {"Lantern", "lantern", 1.0, "lantern", 5.0}, {"Lantern post", "lantern_post", 1.9, "lantern", 7.0},
                {"Glow (magic)", "glow", 1.2, "magic", 5.0}};
        for (Object[] l : lights) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("colour", l[3]);
            props.put("radius", l[4]);
            props.put("strength", 1.0);
            props.put("height", l[2]);
            props.put("glow", true);
            props.put("fixture", l[1]);
            out.add(new Entry((String) l[0], Category.LIGHTS, LIGHT, props));
        }
        out.addAll(templateEntries());
        for (ObjectType t : TYPES.values()) {
            if (t.key().equals(SCENERY) || t.key().equals(ITEM) || t.key().equals(SOUND)
                    || t.key().equals(CRITTERS) || t.key().equals(ANIMALS) || t.key().equals(BUILDING)
                    || t.key().equals(PREFAB) || t.key().equals(WALL) || t.key().equals(FLOOR)
                    || t.key().equals(STAIRS) || t.key().equals(EXIT) || t.key().equals(TEMPLATE)
                    || t.key().equals(LIGHT)) continue;
            Map<String, Object> props = new LinkedHashMap<>();
            for (Prop p : t.props()) props.put(p.key(), p.fallback());
            out.add(new Entry(t.label(), t.category(), t.key(), props));
        }
        return Collections.unmodifiableList(out);
    }

    /** The saved templates, to place: one entry each. */
    public static List<Entry> templateEntries() {
        List<Entry> out = new ArrayList<>();
        for (Templates.Template t : Templates.all()) {
            out.add(new Entry(t.name(), Category.TEMPLATES, TEMPLATE, Map.of("template", t.id())));
        }
        return out;
    }

    /** The sounds a sound spot can play: the held ambient ones (a stream, crickets, the breeze). */
    static List<String> soundKeys() {
        List<String> out = new ArrayList<>();
        for (String s : SoundKeys.AMBIENT_SOUNDS) out.add(SoundKeys.ambient(s));
        return out;
    }

    /** What the editor calls {@code o}: its sprite's or item's name, a spot's own name, or its kind's. */
    public static String label(LevelObject o) {
        ObjectType t = get(o.type());
        if (t == null) return o.type() + " (unknown)";
        return switch (o.type()) {
            case SCENERY -> readable(o.str("sprite", "scenery"));
            case ITEM -> {
                Item item = item(o);
                yield item == null ? "Item " + o.str("item", "?") + " (unknown)" : item.name();
            }
            case MARKER -> "Spot \"" + o.str("name", "spot") + "\"";
            case BUILDING -> {
                BuildingProfile p = BuildingCatalog.get().profile(o.str("kind", ""));
                yield p == null ? "Building " + o.str("kind", "?") + " (missing)" : p.label();
            }
            case PREFAB -> {
                Prefab p = Prefab.byKey(o.str("set", ""));
                yield p == null ? "Set " + o.str("set", "?") + " (unknown)" : p.label();
            }
            case TEMPLATE -> {
                Templates.Template p = Templates.byId(o.str("template", ""));
                yield p == null ? "Template " + o.str("template", "?") + " (missing)" : p.name();
            }
            case WALL, FLOOR, STAIRS -> Room.profile(roomSpec(o)).label();
            case SOUND -> "Sound: " + readable(o.str("sound", "?"));
            case CRITTERS -> {
                CritterKind k = CritterKind.byKey(o.str("kind", ""));
                yield (k == null ? "Critters" : k.label()) + " (" + (int) Math.round(o.num("count", 8)) + ")";
            }
            case ANIMALS -> {
                AnimalKind k = AnimalKind.byKey(o.str("kind", ""));
                Temperament n = Temperament.byKey(o.str("temperament", OWN));
                yield (k == null ? "Animals" : k.label()) + " (" + (int) Math.round(o.num("count", 3)) + ")"
                        + (n == null || k != null && n == k.temperament() ? "" : ", " + n.label().toLowerCase())
                        + (k == AnimalKind.HORSE && o.bool("saddled", false) ? ", saddled" : "");
            }
            default -> t.label();
        };
    }

    /** "oak_tree" → "Oak tree", "ambient/stream" → "Stream". */
    public static String readable(String key) {
        if (key == null) return "";
        String s = key.substring(key.lastIndexOf('/') + 1).replace('_', ' ');
        if (s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** How far round its spot {@code o} takes up room: a tree's or rock's own footprint. */
    public static double radius(LevelObject o, ScenerySprites sprites) {
        if (o.type().equals(SCENERY)) {
            ScenerySprites.Sprite s = sprites.sprite(o.str("sprite", ""));
            if (s != null) {
                return new Scenery.Placed(s, 0, 0, 0, false, 0).footprint() + 0.15;
            }
        }
        if (o.type().equals(BUILDING)) {
            BuildingProfile p = BuildingCatalog.get().profile(o.str("kind", ""));
            if (p != null) return p.reach();
        }
        if (o.type().equals(PREFAB)) {
            Prefab p = Prefab.byKey(o.str("set", ""));
            if (p != null) return p.reach(BuildingCatalog.get());
        }
        if (o.type().equals(TEMPLATE)) {
            Templates.Template p = Templates.byId(o.str("template", ""));
            if (p != null) return p.reach();
        }
        Room.Spec room = roomSpec(o);
        if (room != null) return Room.profile(room).reach();
        ObjectType t = get(o.type());
        return t == null ? 0.5 : t.radius();
    }

    /** How tall {@code o} stands, about, if it is a building (else -1). */
    public static double buildingHeight(LevelObject o) {
        if (o.type().equals(BUILDING)) {
            BuildingProfile p = BuildingCatalog.get().profile(o.str("kind", ""));
            return p == null ? -1 : p.height();
        }
        Room.Spec room = roomSpec(o);
        if (room != null) return room.height();
        return o.type().equals(PREFAB) ? 12 : -1;
    }
}
