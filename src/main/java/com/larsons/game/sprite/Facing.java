package com.larsons.game.sprite;

/**
 * Which of the eight pre-rendered directions a character sprite shows — the
 * direction the character is facing <em>as seen in the picture</em>.
 *
 * <p>Copied from Larsons-Game-Engine ({@code graphics.Facing}) and kept
 * compatible with it: the same eight keys, the same counter-clockwise order
 * from east, the same "west-facing art may be mirrored from its eastern twin"
 * rule. In this game the keys mean, for the Blender renders:
 *
 * <pre>
 *   s  — facing the camera            n  — facing away from the camera
 *   e  — facing the viewer's right    w  — facing the viewer's left
 *   se, sw, ne, nw — the three-quarter views between them
 * </pre>
 *
 * <p>The facing stored on a character is a world direction; which of these
 * eight keys gets drawn depends on where the camera is, and is worked out per
 * character every frame by {@link SpriteView}.
 */
public enum Facing {

    EAST("e", "East", 1, 0),
    NORTH_EAST("ne", "North-East", 1, -1),
    NORTH("n", "North", 0, -1),
    NORTH_WEST("nw", "North-West", -1, -1),
    WEST("w", "West", -1, 0),
    SOUTH_WEST("sw", "South-West", -1, 1),
    SOUTH("s", "South", 0, 1),
    SOUTH_EAST("se", "South-East", 1, 1);

    /** File-name segment: {@code walk_middle_ne.png}. */
    private final String key;
    private final String label;
    /** Unit-ish step in screen space (+x right, +y toward the viewer / down). */
    private final int dx, dy;

    Facing(String key, String label, int dx, int dy) {
        this.key = key;
        this.label = label;
        this.dx = dx;
        this.dy = dy;
    }

    public String key() { return key; }

    public String label() { return label; }

    public int dx() { return dx; }

    public int dy() { return dy; }

    /** Whether this direction points left (its art can be a mirrored east one). */
    public boolean facingLeft() {
        return dx < 0;
    }

    /**
     * The direction whose art this one can mirror: east-facing and pure
     * north/south directions are their own answer, the west-facing ones name
     * their eastern twin (and the eastern ones their western twin, so a set
     * rendered only facing left still fills in the right).
     */
    public Facing mirrorOf() {
        return switch (this) {
            case WEST -> EAST;
            case NORTH_WEST -> NORTH_EAST;
            case SOUTH_WEST -> SOUTH_EAST;
            case EAST -> WEST;
            case NORTH_EAST -> NORTH_WEST;
            case SOUTH_EAST -> SOUTH_WEST;
            default -> this;
        };
    }

    /** Whether a mirrored twin exists at all (north and south have none). */
    public boolean hasMirror() {
        return mirrorOf() != this;
    }

    /** The next direction clockwise on screen (east → south-east → south …). */
    public Facing clockwise() {
        return values()[(ordinal() + 7) % 8];
    }

    /** The next direction counter-clockwise on screen. */
    public Facing counterClockwise() {
        return values()[(ordinal() + 1) % 8];
    }

    /**
     * The compass point a screen-space vector points at (+x right, +y toward
     * the viewer), snapped to the nearest of the eight. A zero-length vector
     * keeps {@code fallback}.
     */
    public static Facing of(double dx, double dy, Facing fallback) {
        if (Math.abs(dx) < 1e-9 && Math.abs(dy) < 1e-9) return fallback;
        double turns = Math.atan2(dy, dx) / (Math.PI / 4.0);
        int octant = (int) Math.round(turns);
        // +y is toward the viewer ("south") and the enum runs counter-clockwise
        // from east, so a positive octant walks the list backwards.
        int idx = ((-octant) % 8 + 8) % 8;
        return values()[idx];
    }

    /** Parse a {@link #key()} back into a direction, or {@code null}. */
    public static Facing byKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase();
        for (Facing f : values()) {
            if (f.key.equals(k)) return f;
        }
        return null;
    }
}
