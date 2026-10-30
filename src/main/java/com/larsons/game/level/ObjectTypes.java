package com.larsons.game.level;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.combat.Dummy;
import com.larsons.game.combat.FlyingDummy;
import com.larsons.game.level.ObjectType.Category;
import com.larsons.game.level.ObjectType.Prop;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.world.Chest;
import com.larsons.game.world.Item;
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
            CHEST = "chest", DUMMY = "dummy", FLIER = "flier", VINES = "vines", SOUND = "sound";

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
                        Prop.toggle("mirrored", "Mirrored", false)), 0.6, 2.0, 0x7FBF6A, false,
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
        register(new ObjectType(VINES, "Climbing vines", Category.TERRAIN,
                List.of(Prop.number("length", "Length (m)", 8, 1, 60, 0.5)), 0.6, 3, 0x5F9E3A, false, null));
        register(new ObjectType(SOUND, "Sound", Category.SOUNDS,
                List.of(Prop.choice("sound", "Sound", SoundKeys.ambient("stream"), o -> soundKeys())), 0.5, 1.2,
                0xB48CFF, false,
                (o, b) -> b.world().soundSpots().add(new World.SoundSpot(o.str("sound", SoundKeys.ambient("stream")),
                        b.onGround(o).add(0, 1, 0)))));
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
        for (ScenerySprites.Sprite s : scenerySprites.sprites()) out.add(s.name());
        return out;
    }

    static List<String> itemIds() {
        List<String> out = new ArrayList<>();
        for (ItemDef d : ItemDef.ALL) out.add(d.id());
        for (ItemDef d : Wearables.ALL) out.add(d.id());
        return out;
    }

    /** The weapon or wearable called {@code id}, or null. */
    public static ItemDef itemDef(String id) {
        for (ItemDef d : ItemDef.ALL) if (d.id().equals(id)) return d;
        return Wearables.byId(id);
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
                out.add(new Entry(readable(s.name()), Category.SCENERY, SCENERY, Map.of("sprite", s.name())));
            }
        }
        for (ItemDef d : ItemDef.ALL) {
            Tier.Row row = Tier.row(d.id());
            out.add(new Entry(Item.of(d).name(), Category.ITEMS, ITEM,
                    row == null ? Map.of("item", d.id()) : Map.of("item", d.id(), "tier", row.lowest().key())));
        }
        for (ItemDef d : Wearables.ALL) {
            out.add(new Entry(d.name(), Category.WEARABLES, ITEM, Map.of("item", d.id())));
        }
        for (String key : soundKeys()) {
            out.add(new Entry(readable(key), Category.SOUNDS, SOUND, Map.of("sound", key)));
        }
        for (ObjectType t : TYPES.values()) {
            if (t.key().equals(SCENERY) || t.key().equals(ITEM) || t.key().equals(SOUND)) continue;
            Map<String, Object> props = new LinkedHashMap<>();
            for (Prop p : t.props()) props.put(p.key(), p.fallback());
            out.add(new Entry(t.label(), t.category(), t.key(), props));
        }
        return Collections.unmodifiableList(out);
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
            case SOUND -> "Sound: " + readable(o.str("sound", "?"));
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
        ObjectType t = get(o.type());
        return t == null ? 0.5 : t.radius();
    }
}
