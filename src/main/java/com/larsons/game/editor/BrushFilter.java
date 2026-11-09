package com.larsons.game.editor;

import com.larsons.game.level.HeightField;
import com.larsons.game.level.Level;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.BrushMask;
import com.larsons.game.world.GroundMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the brushes work, as World Painter's filters have it: only on ground
 * between two slopes, only between two heights, only on (or not on) what
 * the ground is painted, only on dry ground or only under water. One set of
 * them for the sculpting, painting and scattering brushes alike, off until
 * turned on. Each edge is a little soft, so a brush's work fades out over a
 * degree or two (a hand's breadth of height) rather than stopping in a step.
 */
final class BrushFilter {

    /** What the water filter can be. */
    static final List<String> WATER = List.of("Wet or dry", "Dry ground only", "Under water only");

    boolean on;
    double slopeFrom = 0, slopeTo = 90;
    double heightFrom = -60, heightTo = 200;
    /** Only on this (index into the editor's materials: 0 grass, 1 dirt …), or -1: anything. */
    int onlyOn = -1;
    /** Not on this, or -1: nothing kept off. */
    int notOn = -1;
    int water;

    /** Whether it keeps a brush from anywhere at all. */
    boolean active() {
        return on && (slopeFrom > 0 || slopeTo < 90 || heightFrom > -60 || heightTo < 200 || onlyOn >= 0
                || notOn >= 0 || water != 0);
    }

    /** How much a brush works at a spot of {@code level}, 0 – 1 - or null: everywhere (off). */
    BrushMask mask(Level level) {
        if (!active()) return null;
        HeightField hf = level.heights();
        GroundMap ground = level.ground();
        return (x, z) -> at(level, hf, ground, x, z);
    }

    /** How much a brush works at {@code (x, z)}, 0 – 1. */
    double at(Level level, HeightField hf, GroundMap ground, double x, double z) {
        double w = 1;
        if (slopeFrom > 0 || slopeTo < 90) {
            double d = 0.5;
            double gx = (hf.height(x + d, z) - hf.height(x - d, z)) / (2 * d);
            double gz = (hf.height(x, z + d) - hf.height(x, z - d)) / (2 * d);
            double deg = Math.toDegrees(Math.atan(Math.hypot(gx, gz)));
            w *= band(deg, slopeFrom, slopeTo, 2.0);
        }
        double h = hf.height(x, z);
        if (w > 0 && (heightFrom > -60 || heightTo < 200)) w *= band(h, heightFrom, heightTo, 0.25);
        if (w > 0 && onlyOn >= 0) w *= cover(ground, onlyOn, x, z);
        if (w > 0 && notOn >= 0) w *= 1 - cover(ground, notOn, x, z);
        if (w > 0 && water != 0) {
            boolean wet = level.water().depth(x, z, h) > 0.05;
            if ((water == 1) == wet) w = 0;
        }
        return Math.max(0, Math.min(1, w));
    }

    /** 1 between {@code lo} and {@code hi}, fading to 0 over {@code soft} outside them. */
    static double band(double v, double lo, double hi, double soft) {
        if (v < lo) return Math.max(0, 1 - (lo - v) / soft);
        if (v > hi) return Math.max(0, 1 - (v - hi) / soft);
        return 1;
    }

    /** How much material {@code m} (0 grass, then the ground map's materials, one up) covers {@code (x, z)}. */
    static double cover(GroundMap ground, int m, double x, double z) {
        if (m == 0) {
            double most = 0;
            for (int i = 0; i < GroundMap.MATERIALS; i++) most = Math.max(most, ground.weight(i, x, z));
            return 1 - most;
        }
        int i = m - 1;
        if (i >= GroundMap.MATERIALS) return 0;
        return Math.min(1, ground.weight(i, x, z) * 1.6);
    }

    /**
     * Its controls, at {@code (x, y)} {@code w} wide: on or off, and - on -
     * each filter. {@code materials}: what the ground can be painted, grass
     * first. Returns the foot.
     */
    float panel(Ui ui, Widgets widgets, float x, float y, float w, String[] materials, int paintable) {
        ui.text(ui.small, "Filters", x, y + 4, Theme.ACCENT);
        y += 22;
        on = widgets.toggle(ui, "Only where…", on, x, y, w);
        y += Widgets.ROW + 6;
        if (!on) return y;
        slopeFrom = widgets.slider(ui, "f-slope-from", "Slope from (°)", slopeFrom, 0, 90, "%.0f", x, y + 10, w);
        slopeTo = widgets.slider(ui, "f-slope-to", "Slope to (°)", slopeTo, 0, 90, "%.0f", x, y + 46, w);
        if (slopeTo < slopeFrom) slopeTo = slopeFrom;
        y += 84;
        heightFrom = widgets.slider(ui, "f-height-from", "Height from (m)", heightFrom, -60, 200, "%.1f", x, y + 10,
                w);
        heightTo = widgets.slider(ui, "f-height-to", "Height to (m)", heightTo, -60, 200, "%.1f", x, y + 46, w);
        if (heightTo < heightFrom) heightTo = heightFrom;
        y += 84;
        List<String> only = new ArrayList<>();
        only.add("Anything");
        for (int i = 0; i < paintable; i++) only.add(materials[i]);
        onlyOn = widgets.choice(ui, "Only on", only, onlyOn + 1, x, y, w) - 1;
        y += Widgets.ROW + 6;
        List<String> not = new ArrayList<>();
        not.add("Nothing");
        for (int i = 0; i < paintable; i++) not.add(materials[i]);
        notOn = widgets.choice(ui, "Not on", not, notOn + 1, x, y, w) - 1;
        y += Widgets.ROW + 6;
        water = widgets.choice(ui, "Water", WATER, water, x, y, w);
        y += Widgets.ROW + 6;
        if (ui.button("Clear the filters", x, y, w, 26)) {
            slopeFrom = 0;
            slopeTo = 90;
            heightFrom = -60;
            heightTo = 200;
            onlyOn = notOn = -1;
            water = 0;
        }
        return y + 34;
    }
}
