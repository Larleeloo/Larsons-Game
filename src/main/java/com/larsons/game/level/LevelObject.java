package com.larsons.game.level;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One thing placed in a {@link Level}: what it is ({@link #type}, a key of
 * {@link ObjectTypes}), where it stands on the ground ({@link #x}, {@link
 * #z}, and {@link #lift} metres above it), which way it faces ({@link
 * #yaw}) and whatever else its type needs ({@link #props}: a tree's sprite,
 * an item's tier, a marker's name, an NPC's dialogue later on).
 *
 * <p>Its {@link #id} is its own for as long as the level is: a quest or a
 * trigger can name it, and the editor keeps track of it by it. A type the
 * game does not know (a newer file) is kept as it is and written back.
 *
 * <p>Mutable: the editor moves and turns it in place; {@link #copy} for
 * undo.
 */
public final class LevelObject {

    private final int id;
    private final String type;
    private double x, z, lift, yaw;
    private final Map<String, Object> props;

    /**
     * {@code type} at {@code (x, z)}, {@code lift} metres up off the ground
     * there, facing heading {@code yaw} (radians, 0 north, turning towards
     * the west, as {@code SpriteView} has headings), with {@code props}.
     */
    public LevelObject(int id, String type, double x, double z, double lift, double yaw, Map<String, Object> props) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.z = z;
        this.lift = lift;
        this.yaw = yaw;
        this.props = new LinkedHashMap<>(props == null ? Map.of() : props);
    }

    public LevelObject(int id, String type, double x, double z, double yaw, Map<String, Object> props) {
        this(id, type, x, z, 0, yaw, props);
    }

    public int id() { return id; }

    public String type() { return type; }

    public double x() { return x; }

    public double z() { return z; }

    public double lift() { return lift; }

    public double yaw() { return yaw; }

    /** Everything its type keeps beside where it is: read with {@link #str}, {@link #num}, {@link #bool}. */
    public Map<String, Object> props() { return props; }

    public LevelObject moveTo(double x, double z) {
        this.x = x;
        this.z = z;
        return this;
    }

    public LevelObject setLift(double lift) {
        this.lift = lift;
        return this;
    }

    /** Face heading {@code yaw}, radians (kept within one turn). */
    public LevelObject turnTo(double yaw) {
        double turn = Math.PI * 2;
        this.yaw = ((yaw % turn) + turn) % turn;
        return this;
    }

    public LevelObject set(String key, Object value) {
        if (value == null) props.remove(key);
        else props.put(key, value);
        return this;
    }

    public String str(String key, String fallback) {
        Object v = props.get(key);
        return v == null ? fallback : v.toString();
    }

    public double num(String key, double fallback) {
        Object v = props.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    public boolean bool(String key, boolean fallback) {
        Object v = props.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes");
        return fallback;
    }

    /** The same thing, as a separate object (for undo). */
    public LevelObject copy() {
        return copy(id);
    }

    /** The same thing under another id (duplicated, pasted). */
    public LevelObject copy(int newId) {
        return new LevelObject(newId, type, x, z, lift, yaw, deepCopy(props));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> deepCopy(Map<String, Object> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?, ?> sub) v = deepCopy((Map<String, Object>) sub);
            else if (v instanceof java.util.List<?> list) v = new java.util.ArrayList<>(list);
            out.put(e.getKey(), v);
        }
        return out;
    }

    @Override
    public String toString() {
        return type + "#" + id + " at (" + String.format("%.2f, %.2f", x, z) + ")" + (props.isEmpty() ? "" : " " + props);
    }
}
