package com.larsons.game.gfx;

/**
 * How the world is to look, from Settings, Display: the sun's shadows and
 * how fine they are ({@link #shadows}), and the Vibrant visuals look and how
 * strong it is ({@link #vibrant}, {@link #strength}) - sunlight warm on what
 * it falls on and the shade cool, buildings lit from where the sun is,
 * richer colours, and shafts of light through the trees and the windows
 * worked out from the shadows ({@link #rays}).
 *
 * <p>The game sets {@link #now} from its settings each frame; the world's
 * renderer reads it. Off everything is drawn as it was: flat pixel art, a
 * soft shadow under each thing.
 */
public record Visuals(Shadows shadows, boolean vibrant, double strength, boolean rays) {

    /** How fine the sun's shadows are: the map's size, and how far round the eye they fall. */
    public enum Shadows {
        OFF("Off", 0, 0), LOW("Low", 1024, 34), MEDIUM("Medium", 2048, 48), HIGH("High", 4096, 64);

        public final String label;
        /** The shadow map's pixels a side. */
        public final int size;
        /** How far from the eye's target the shadows fall, metres. */
        public final double reach;

        Shadows(String label, int size, double reach) {
            this.label = label;
            this.size = size;
            this.reach = reach;
        }

        public static Shadows of(String key, Shadows fallback) {
            if (key == null) return fallback;
            for (Shadows s : values()) if (s.name().equalsIgnoreCase(key.trim())) return s;
            return fallback;
        }
    }

    /** As it comes: medium shadows, Vibrant visuals at three quarters, light rays. */
    public static final Visuals DEFAULT = new Visuals(Shadows.MEDIUM, true, 0.75, true);

    /** Flat: no shadows of the sun, no vibrant look (as the game was). */
    public static final Visuals FLAT = new Visuals(Shadows.OFF, false, 0, false);

    /** What the frame being drawn looks like. */
    public static volatile Visuals now = DEFAULT;

    public Visuals {
        if (shadows == null) shadows = Shadows.OFF;
        strength = Math.max(0, Math.min(1, strength));
    }

    /** How strong the vibrant look is: 0 when it is off. */
    public double vibrance() {
        return vibrant ? strength : 0;
    }

    /** Whether light rays are worked out from the shadows (vibrant, with shadows to make them of). */
    public boolean computedRays() {
        return vibrant && rays && shadows != Shadows.OFF && strength > 0;
    }
}
