package com.larsons.game.sprite;

/**
 * The three vertical rendering zones a character is pre-rendered from.
 *
 * <p>The camera can sit at any height, but a character is only rendered from
 * three: the zone the camera's elevation above the character falls in picks
 * which set of sprites is drawn.
 *
 * <pre>
 *   zone     rendered at   used while the camera is
 *   side          0°       0° – 33° above the character
 *   middle       45°       34° – 75°
 *   top          90°       75° – 90° (birds-eye)
 * </pre>
 *
 * <p>The zone boundaries sit at 33.5° and 75°, so the README's "0 to 33" and
 * "34 to 75" are both honoured for whole degrees and 75° itself is top-down.
 */
public enum Elevation {

    SIDE("side", "Side", 0, 33.5),
    MIDDLE("middle", "Middle", 45, 75),
    TOP("top", "Top-down", 90, 90.0001);

    private final String key;
    private final String label;
    /** The angle above the horizon the Blender camera rendered this set from. */
    private final double renderAngle;
    /** Camera elevations below this (degrees) belong to this zone or a lower one. */
    private final double upperBound;

    Elevation(String key, String label, double renderAngle, double upperBound) {
        this.key = key;
        this.label = label;
        this.renderAngle = renderAngle;
        this.upperBound = upperBound;
    }

    /** File-name segment: {@code walk_middle_ne.png}. */
    public String key() { return key; }

    public String label() { return label; }

    /** Degrees above the horizon this zone's sprites were rendered from. */
    public double renderAngle() { return renderAngle; }

    /** The zone for a camera {@code degrees} above the character's pivot. */
    public static Elevation forAngle(double degrees) {
        if (degrees < SIDE.upperBound) return SIDE;
        if (degrees < MIDDLE.upperBound) return MIDDLE;
        return TOP;
    }

    /** Parse a {@link #key()} back into a zone, or {@code null}. */
    public static Elevation byKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase();
        for (Elevation e : values()) {
            if (e.key.equals(k)) return e;
        }
        return null;
    }
}
