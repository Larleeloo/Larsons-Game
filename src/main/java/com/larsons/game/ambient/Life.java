package com.larsons.game.ambient;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A level's own say over the life in it, set in Creative Mode (the Level
 * tool's Life page) and kept in its file:
 *
 * <pre>
 *   "life": {"critters": {"firefly": 1.5, "cricket": 1, "frog": 0, "dragonfly": 1},
 *            "regrow": 1, "yield": 1}
 * </pre>
 *
 * <ul>
 *   <li>{@code critters} - how thick each kind lives in it, 0 (none) to
 *       {@value #MOST} (three times as many): their habitat and their hour
 *       still decide where and when.</li>
 *   <li>{@code regrow} - how long the foraging bushes take to grow their
 *       pickings back, times their own time (0.25 quick, 4 slow).</li>
 *   <li>{@code yield} - how much a picking gives, times its own.</li>
 * </ul>
 *
 * Anything left out is 1.
 */
public final class Life {

    /** The most of a kind a level can ask for, times the usual. */
    public static final double MOST = 3;

    private final EnumMap<CritterKind, Double> density = new EnumMap<>(CritterKind.class);
    private double regrow = 1, yield = 1;

    /** Every kind at its usual number, the bushes at their own times. */
    public Life() {
        for (CritterKind k : CritterKind.values()) density.put(k, 1.0);
    }

    public double density(CritterKind kind) {
        return density.getOrDefault(kind, 1.0);
    }

    public void setDensity(CritterKind kind, double d) {
        density.put(kind, clamp(d, 0, MOST));
    }

    public double regrow() { return regrow; }

    public void setRegrow(double r) {
        regrow = clamp(r, 0.05, 10);
    }

    public double yield() { return yield; }

    public void setYield(double y) {
        yield = clamp(y, 0, 5);
    }

    /** Whether it is all as it comes (nothing to write in the file). */
    public boolean isDefault() {
        for (double d : density.values()) if (d != 1) return false;
        return regrow == 1 && yield == 1;
    }

    /** A copy of it (the editor's undo). */
    public Life copy() {
        Life l = new Life();
        l.density.putAll(density);
        l.regrow = regrow;
        l.yield = yield;
        return l;
    }

    /** As the level file has it. */
    public Map<String, Object> toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> c = new LinkedHashMap<>();
        for (CritterKind k : CritterKind.values()) c.put(k.key(), density(k));
        out.put("critters", c);
        out.put("regrow", regrow);
        out.put("yield", yield);
        return out;
    }

    /** From the level file's {@code "life"} (anything missing as it comes). */
    public static Life fromJson(Object o) {
        Life l = new Life();
        if (!(o instanceof Map<?, ?> m)) return l;
        if (m.get("critters") instanceof Map<?, ?> c) {
            for (Map.Entry<?, ?> e : c.entrySet()) {
                CritterKind k = CritterKind.byKey(String.valueOf(e.getKey()));
                if (k != null && e.getValue() instanceof Number n) l.setDensity(k, n.doubleValue());
            }
        }
        if (m.get("regrow") instanceof Number n) l.setRegrow(n.doubleValue());
        if (m.get("yield") instanceof Number n) l.setYield(n.doubleValue());
        return l;
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? 1 : Math.max(lo, Math.min(hi, v));
    }
}
