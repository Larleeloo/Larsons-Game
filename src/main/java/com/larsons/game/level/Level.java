package com.larsons.game.level;

import com.larsons.game.audio.Songs;
import com.larsons.game.world.GroundMap;
import com.larsons.game.world.Sky;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One area of the game as Creative Mode builds it: a square of ground
 * {@link #size} metres a side round the origin - sculpted ({@link
 * #heights}) and painted ({@link #ground}) - the sky over it with what stands
 * far off round the horizon ({@link #sky}), the music played in it
 * ({@link #music}), and everything placed in it
 * ({@link #objects}): the player's spawn, the trees, rocks and grass, the
 * weapons and wearables lying about, the chests, the dummies, the vines,
 * named spots - and, as the game grows, its NPCs, quests, enemies, sounds.
 *
 * <p>Saved as a folder of {@code assets/levels/} ({@link LevelIO}): readable
 * JSON for everything but the two maps, which are images. Whatever a newer
 * game wrote that this one does not know is kept ({@link #extra}) and
 * written back, so an older editor never loses it.
 *
 * <p>Mutable - the editor works on it in place.
 */
public final class Level {

    /** The version of the file format this game writes. */
    public static final int FORMAT = 1;
    /** The sizes a level can be, metres a side. */
    public static final int[] SIZES = {64, 128, 192, 256};
    public static final int DEFAULT_SIZE = 128;

    private String name;
    private double size;
    private Sky sky = Sky.DEFAULT;
    private String music = Songs.SHUFFLE;
    private HeightField heights;
    private GroundMap ground;
    private final List<LevelObject> objects = new ArrayList<>();
    private final Map<String, Object> extra = new LinkedHashMap<>();
    private int nextId = 1;

    /** An empty level called {@code name}, {@code size} metres a side: flat grass under the demo's sky. */
    public Level(String name, double size) {
        this.name = name;
        this.size = size;
        heights = new HeightField(size);
        ground = new GroundMap(size);
    }

    /** A new level as the editor starts one: empty but for the player's spawn in the middle. */
    public static Level blank(String name, double size) {
        Level l = new Level(name, size);
        l.add(ObjectTypes.SPAWN, 0, 0, 0, Map.of());
        return l;
    }

    public String name() { return name; }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? "Untitled" : name.trim();
    }

    /** Metres a side. */
    public double size() { return size; }

    /** Half its size: it reaches this far from the origin each way. */
    public double half() { return size / 2; }

    /**
     * Make it {@code newSize} metres a side: the ground and its paint kept
     * round the middle (cut off where it no longer reaches), whatever stood
     * beyond the new edge taken away.
     */
    public void resize(double newSize) {
        if (newSize == size) return;
        HeightField h = new HeightField(newSize).copyFrom(heights);
        GroundMap g = new GroundMap(newSize).copyFrom(ground);
        size = newSize;
        heights = h;
        ground = g;
        double half = newSize / 2;
        objects.removeIf(o -> Math.abs(o.x()) > half || Math.abs(o.z()) > half);
    }

    /** Make it {@code newSize} metres a side with flat, bare ground (undo puts what was there back after). */
    public void resetSize(double newSize) {
        size = newSize;
        heights = new HeightField(newSize);
        ground = new GroundMap(newSize);
    }

    public Sky sky() { return sky; }

    public void setSky(Sky sky) {
        this.sky = sky == null ? Sky.DEFAULT : sky;
    }

    /**
     * The music played in the level: a song's id ({@link Songs}, e.g.
     * {@code dream_on}), looping; {@link Songs#SHUFFLE} for all of them one
     * after another (a new level's); or {@link Songs#NONE}.
     */
    public String music() { return music; }

    /** The level's music ({@link #music}); blank is none. */
    public void setMusic(String music) {
        this.music = music == null || music.isBlank() ? Songs.NONE : music.trim();
    }

    public HeightField heights() { return heights; }

    public void setHeights(HeightField h) {
        heights = h;
    }

    public GroundMap ground() { return ground; }

    public void setGround(GroundMap g) {
        ground = g;
    }

    /** Everything placed in it, in the order it was placed. */
    public List<LevelObject> objects() { return objects; }

    /** What a newer game wrote at the top of the file that this one does not know, kept to be written back. */
    public Map<String, Object> extra() { return extra; }

    /** The number the next object placed gets. */
    public int nextId() { return nextId; }

    /** Number objects from {@code id} on (when reading a file: one past the highest there). */
    void setNextId(int id) {
        nextId = Math.max(nextId, id);
    }

    /** A new object of {@code type} at {@code (x, z)} facing {@code yaw}, with {@code props}: added, and returned. */
    public LevelObject add(String type, double x, double z, double yaw, Map<String, Object> props) {
        return add(new LevelObject(nextId, type, x, z, yaw, props));
    }

    /**
     * Add {@code o} - numbered past everything there, if its number is taken -
     * and return it as added. Of a kind a level has one of ({@link
     * ObjectType#single}), the one there already is taken away.
     */
    public LevelObject add(LevelObject o) {
        ObjectType t = ObjectTypes.get(o.type());
        if (t != null && t.single()) objects.removeIf(x -> x.type().equals(o.type()));
        LevelObject placed = byId(o.id()) != null || o.id() <= 0 ? o.copy(nextId) : o;
        objects.add(placed);
        nextId = Math.max(nextId, placed.id() + 1);
        return placed;
    }

    /** The object numbered {@code id}, or null. */
    public LevelObject byId(int id) {
        for (LevelObject o : objects) if (o.id() == id) return o;
        return null;
    }

    public boolean remove(LevelObject o) {
        return objects.remove(o);
    }

    /** Every object of {@code type}. */
    public List<LevelObject> ofType(String type) {
        List<LevelObject> out = new ArrayList<>();
        for (LevelObject o : objects) if (o.type().equals(type)) out.add(o);
        return out;
    }

    /** The player's spawn, or null if it has none (she starts in the middle). */
    public LevelObject spawn() {
        for (LevelObject o : objects) if (o.type().equals(ObjectTypes.SPAWN)) return o;
        return null;
    }

    /** Whether {@code (x, z)} is on it. */
    public boolean contains(double x, double z) {
        return Math.abs(x) <= half() && Math.abs(z) <= half();
    }

    /** A copy of every object, for undo. */
    public List<LevelObject> copyObjects() {
        List<LevelObject> out = new ArrayList<>();
        for (LevelObject o : objects) out.add(o.copy());
        return out;
    }

    /** Put {@code copies} (from {@link #copyObjects}) in place of every object. */
    public void restoreObjects(List<LevelObject> copies) {
        objects.clear();
        for (LevelObject o : copies) {
            objects.add(o.copy());
            nextId = Math.max(nextId, o.id() + 1);
        }
    }
}
