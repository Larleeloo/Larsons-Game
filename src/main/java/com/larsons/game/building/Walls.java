package com.larsons.game.building;

/**
 * How walls are drawn, as walls are in the Sims: all standing, those between
 * the camera and her cut down to a stub (and stood up again as she goes by),
 * or every one down to its stub so the whole of the inside shows. Set apart
 * for rooms' walls (indoors) and for buildings (outdoors) - in Creative Mode
 * and in the pause menu's settings.
 */
public enum Walls {
    UP("Up"),
    CUTAWAY("Cut away"),
    DOWN("Down");

    private final String label;

    Walls(String label) {
        this.label = label;
    }

    public String label() { return label; }

    /** The next one round (a button that steps through them). */
    public Walls next() { return values()[(ordinal() + 1) % values().length]; }

    /** The one named {@code name} (its enum name or its label, any case), or {@code fallback}. */
    public static Walls of(String name, Walls fallback) {
        if (name == null) return fallback;
        for (Walls w : values()) {
            if (w.name().equalsIgnoreCase(name) || w.label.equalsIgnoreCase(name)) return w;
        }
        return fallback;
    }

    /** The labels, in order (a choice's options). */
    public static java.util.List<String> labels() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Walls w : values()) out.add(w.label);
        return out;
    }
}
