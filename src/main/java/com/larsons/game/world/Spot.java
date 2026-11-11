package com.larsons.game.world;

import com.larsons.game.math.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Something in the world she can walk up to and use (E) that is not a chest,
 * a shop or a door: the way back to the overworld map, a cauldron to brew
 * at, a locked door, a fishing spot, a summon station, a wall-buy, a mystery
 * box ... Each is a level object of its own kind ({@code level/ObjectTypes});
 * what using one does is the scene's to say, by its {@link #kind}.
 *
 * <p>A spot keeps the properties it was placed with ({@link #str}, {@link
 * #num}, {@link #bool}) and whatever it has to remember while the level is
 * played ({@link #state}: a door unlocked, a box's weapon waiting).
 */
public final class Spot {

    /** How near she must stand to use one, metres, unless it says otherwise. */
    public static final double REACH = 1.9;

    private final int id;
    private final String kind;
    private String name;
    private Vec3 position;
    private final double heading;
    private final Map<String, Object> props;
    private final Map<String, Object> state = new HashMap<>();
    private double reach = REACH;
    private String icon;
    private boolean gone;
    /** Its marker's height over the ground, metres. */
    private double markerHeight = 2.3;

    public Spot(int id, String kind, String name, Vec3 position, double heading, Map<String, Object> props) {
        this.id = id;
        this.kind = kind;
        this.name = name == null ? "" : name;
        this.position = position;
        this.heading = heading;
        this.props = props == null ? Map.of() : props;
    }

    public int id() { return id; }

    public String kind() { return kind; }

    public String name() { return name; }

    public void setName(String n) {
        name = n == null ? "" : n;
    }

    public Vec3 position() { return position; }

    public void moveTo(Vec3 at) {
        position = at;
    }

    public double heading() { return heading; }

    public Map<String, Object> props() { return props; }

    /** What it remembers while the level is played. */
    public Map<String, Object> state() { return state; }

    public String str(String key, String fallback) {
        Object v = props.get(key);
        return v == null ? fallback : String.valueOf(v);
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
        return v instanceof Boolean b ? b : fallback;
    }

    public double reach() { return reach; }

    public Spot reach(double metres) {
        reach = metres;
        return this;
    }

    /** The icon ({@code assets/cards/icons.png}) that floats over it, or null: none. */
    public String icon() { return gone ? null : icon; }

    public Spot icon(String key) {
        icon = key;
        return this;
    }

    public double markerHeight() { return markerHeight; }

    public Spot markerHeight(double metres) {
        markerHeight = metres;
        return this;
    }

    /** Whether it is used up, or taken away: nothing to use any more. */
    public boolean gone() { return gone; }

    public void setGone(boolean g) {
        gone = g;
    }

    public boolean inReach(Vec3 at) {
        return !gone && position.horizontalDistance(at) <= reach && Math.abs(position.y() - at.y()) < 2.5;
    }
}
