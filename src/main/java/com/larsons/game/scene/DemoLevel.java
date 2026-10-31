package com.larsons.game.scene;

import com.larsons.game.level.HeightField;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Scenery;
import com.larsons.game.world.Terrain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The demo as a {@link Level}: the meadow and the highlands - the same
 * ground, the same paths and groves, every tree, rock and tuft where the
 * demo plants it ({@link com.larsons.game.world.SceneryLayout}) - with the
 * weapons, the two chests, the seven training dummies and the two flying
 * dummies where {@link DemoScene} puts them, the vine cliff and the demo's
 * named spots. Creative Mode starts a new level from it ("New level from
 * the demo"), to change it without touching the demo.
 */
public final class DemoLevel {

    /** The demo camera's yaw at the start, which the chest and the player's first heading are set by. */
    static final double START_YAW = 0.45;

    private DemoLevel() {}

    /** The demo as a level called {@code name}, with {@code scenery} - the demo's - standing in it. */
    public static Level build(String name, Scenery scenery) {
        Level level = new Level(name, Level.DEFAULT_SIZE);
        Terrain t = Terrain.highlands();
        HeightField hf = level.heights();
        int n = hf.points();
        float[] h = new float[n * n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                h[j * n + i] = (float) t.height(hf.west() + i * Terrain.STEP, hf.west() + j * Terrain.STEP);
            }
        }
        hf.setHeights(h);
        level.ground().copyFrom(scenery.ground());

        level.add(ObjectTypes.SPAWN, 0, 0, SpriteView.headingShowing(Facing.SOUTH_EAST, START_YAW), Map.of());
        for (Scenery.Placed p : scenery.placed()) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("sprite", p.sprite().name());
            if (p.mirrored()) props.put("mirrored", true);
            level.add(ObjectTypes.SCENERY, p.x(), p.z(), p.yaw(), props);
        }
        item(level, Item.of(ItemDef.SWORD), DemoScene.SWORD_AT);
        for (Object[] w : DemoScene.WEAPONS_AT) item(level, (Item) w[0], (Vec3) w[1]);
        level.add(ObjectTypes.CHEST, DemoScene.CHEST_AT.x(), DemoScene.CHEST_AT.z(),
                SpriteView.headingShowing(Facing.SOUTH_WEST, START_YAW), Map.of("sprite", DemoScene.CHEST));
        level.add(ObjectTypes.CHEST, DemoScene.PLATEAU_CHEST_AT.x(), DemoScene.PLATEAU_CHEST_AT.z(),
                SpriteView.headingOf(-0.6, 1), Map.of("sprite", DemoScene.CHEST));
        for (Vec3 at : DemoScene.DUMMIES_AT) {
            level.add(ObjectTypes.DUMMY, at.x(), at.z(), SpriteView.headingOf(-at.x(), -at.z()), Map.of("health", 100));
        }
        Vec3 mid = DemoScene.PLATEAU_MIDDLE;
        for (Vec3 at : DemoScene.HIGHLAND_DUMMIES_AT) {
            level.add(ObjectTypes.DUMMY, at.x(), at.z(), SpriteView.headingOf(mid.x() - at.x(), mid.z() - at.z()),
                    Map.of("health", 100));
        }
        for (int i = 0; i < DemoScene.FLIERS_AT.length; i++) {
            Vec3 at = DemoScene.FLIERS_AT[i];
            level.add(ObjectTypes.FLIER, at.x(), at.z(), i * 2.1, Map.of("health", 60));
        }
        for (Terrain.Wall w : t.walls()) {
            level.add(ObjectTypes.VINES, (w.x0() + w.x1()) / 2, (w.z0() + w.z1()) / 2, w.facing() + Math.PI,
                    Map.of("length", w.length()));
        }
        for (Terrain.Place p : t.places()) level.add(ObjectTypes.MARKER, p.x(), p.z(), 0, Map.of("name", p.name()));
        level.setMusic(com.larsons.game.audio.Songs.SHUFFLE);      // as the demo plays it
        return level;
    }

    private static LevelObject item(Level level, Item item, Vec3 at) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("item", item.id());
        if (item.tier() != null) props.put("tier", item.tier().key());
        return level.add(ObjectTypes.ITEM, at.x(), at.z(), 0, props);
    }

    /** The demo's dummies and fliers and items, counted, for the tests. */
    static List<Integer> counts(Level level) {
        return List.of(level.ofType(ObjectTypes.ITEM).size(), level.ofType(ObjectTypes.CHEST).size(),
                level.ofType(ObjectTypes.DUMMY).size(), level.ofType(ObjectTypes.FLIER).size());
    }
}
