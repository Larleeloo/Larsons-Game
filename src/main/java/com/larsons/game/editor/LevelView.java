package com.larsons.game.editor;

import com.larsons.game.combat.CombatTuning;
import com.larsons.game.level.HeightField;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.LevelWorld;
import com.larsons.game.level.ObjectType;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The level editor's live view of its {@link Level}: a {@link World} built
 * from it as it would be played ({@link LevelWorld}), kept up to date as it
 * is edited - its ground rebuilt only when the heights or the vines change,
 * its objects re-spawned when they change - with what is about to be placed
 * (the "ghost") standing in it too, under the pointer. She stands at the
 * spawn, idle: the spawn's own mark.
 */
final class LevelView {

    private final Level level;
    private final ScenerySprites sprites;
    private final CombatTuning tuning;
    private final World world;
    private Terrain terrain;
    /** What the ground was built from: the heights (and their version) and the vines and spots as they stood. */
    private HeightField heightsBuilt;
    private int heightsVersion = -1;
    private String featuresKey = "";
    private boolean objectsDirty = true;
    private LevelObject ghost;

    LevelView(Level level, Wardrobe wardrobe, ScenerySprites sprites, CombatTuning tuning) {
        this.level = level;
        this.sprites = sprites;
        this.tuning = tuning;
        this.world = new World(wardrobe);
        world.player().preview(AnimState.IDLE);
        refresh();
    }

    World world() { return world; }

    Terrain terrain() { return terrain; }

    /** The objects have changed (placed, moved, turned, deleted, their props): re-spawn them next refresh. */
    void objectsChanged() {
        objectsDirty = true;
    }

    /** Stand {@code o} in the view too (null: nothing), as if placed - the palette's choice under the pointer. */
    void setGhost(LevelObject o) {
        if (Objects.equals(describe(o), describe(ghost))) return;
        ghost = o == null ? null : o.copy();
        objectsDirty = true;
    }

    private static String describe(LevelObject o) {
        return o == null ? "" : o.type() + o.props() + Math.round(o.x() * 100) + "," + Math.round(o.z() * 100) + ","
                + Math.round(o.yaw() * 1000) + "," + Math.round(o.lift() * 100);
    }

    /** Bring the world up to date with the level: its ground if that changed, its objects if they did. */
    void refresh() {
        String features = features();
        boolean ground = terrain == null || level.heights() != heightsBuilt
                || level.heights().version() != heightsVersion || !features.equals(featuresKey);
        if (ground) {
            terrain = LevelWorld.terrain(level);
            heightsBuilt = level.heights();
            heightsVersion = level.heights().version();
            featuresKey = features;
            objectsDirty = true;
        }
        LevelWorld.stage(world, level, terrain);
        if (!objectsDirty) return;
        objectsDirty = false;
        List<LevelObject> all = level.objects();
        if (ghost != null) {
            all = new ArrayList<>(all);
            withGhost(all, ghost);
        }
        LevelWorld.spawnAll(world, all, level, terrain, sprites, tuning);
        world.player().preview(AnimState.IDLE);
    }

    /** The vines and the named spots, as a key that changes when any of them does (they are in the ground). */
    private String features() {
        StringBuilder sb = new StringBuilder();
        for (LevelObject o : level.objects()) {
            if (o.type().equals(ObjectTypes.VINES) || o.type().equals(ObjectTypes.MARKER)
                    || o.type().equals(ObjectTypes.SPAWN)) {
                sb.append(describe(o)).append(';');
            }
        }
        return sb.toString();
    }

    /** {@code ghost} added to {@code all}: of a kind there is one of, in the one's place. */
    private static void withGhost(List<LevelObject> all, LevelObject ghost) {
        ObjectType t = ObjectTypes.get(ghost.type());
        if (t != null && t.single()) all.removeIf(o -> o.type().equals(ghost.type()));
        all.add(ghost);
    }
}
