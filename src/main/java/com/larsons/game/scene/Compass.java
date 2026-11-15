package com.larsons.game.scene;

import com.larsons.game.math.Vec3;
import com.larsons.game.ui.CardArt;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.util.List;

/**
 * The compass along the top of the screen: the way the camera looks in the
 * middle, north, east, south and west sliding by as it turns, and on it the
 * places worth finding near her ({@link MiniMap.Waypoint}) - each where it
 * lies; one behind her pinned to the end of the bar on its side. The one
 * she looks most nearly at is named under the bar, with how far it is.
 */
final class Compass {

    private Compass() {}

    /** How much of the round the bar shows either side of its middle, radians. */
    static final double ARC = Math.toRadians(100);
    private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /** The way {@code (dx, dz)} lies as a bearing: 0 north, a quarter turn east (x east, z south). */
    static double bearing(double dx, double dz) {
        return Math.atan2(dx, -dz);
    }

    /** The bearing a camera at {@code yaw} looks along. */
    static double looking(double cameraYaw) {
        return -cameraYaw;
    }

    /** {@code a} as an angle from -π to π. */
    static double wrap(double a) {
        double w = a % (2 * Math.PI);
        if (w > Math.PI) w -= 2 * Math.PI;
        if (w < -Math.PI) w += 2 * Math.PI;
        return w;
    }

    /**
     * How far along the bar - -1 its left end, 1 its right - something
     * {@code bearing} lies for a camera looking along {@code looking};
     * beyond the ends when it is behind.
     */
    static double along(double bearing, double looking) {
        return wrap(bearing - looking) / ARC;
    }

    /** The bar {@code w} wide, its middle at {@code cx}, its top at {@code y}. */
    static void draw(Ui ui, double cameraYaw, Vec3 me, List<MiniMap.Waypoint> places, float cx, float y, float w) {
        float h = 30, half = w / 2;
        ui.plate(cx - half, y, w, h, Theme.BRASS);
        double look = looking(cameraYaw);
        // the ticks, every 15 degrees; the points of the compass named
        for (int deg = 0; deg < 360; deg += 15) {
            double a = along(Math.toRadians(deg), look);
            if (Math.abs(a) > 0.97) continue;
            float x = cx + (float) (a * (half - 8));
            float fade = (float) Math.min(1, (1 - Math.abs(a)) * 4);
            if (deg % 45 == 0) {
                String p = POINTS[deg / 45];
                boolean main = deg % 90 == 0;
                ui.textCentered(main ? ui.body : ui.small, p, x, y + (main ? 3 : 7),
                        Theme.withAlpha(deg == 0 ? Theme.ACCENT : main ? Theme.TITLE : Theme.HINT, fade));
            } else {
                ui.rect(x, y + 11, 1, 8, Theme.withAlpha(Theme.BRASS_LIGHT, 0.5f * fade));
            }
        }
        // the middle: the way she looks
        ui.rect(cx - 0.5f, y + h - 6, 1, 6, Theme.BRASS_LIGHT);
        MiniMap.Waypoint nearest = null;
        double best = 0.12;
        for (MiniMap.Waypoint p : places) {
            double dx = p.at().x() - me.x(), dz = p.at().z() - me.z();
            if (Math.hypot(dx, dz) < 1.5) continue;
            double a = along(bearing(dx, dz), look);
            boolean behind = Math.abs(a) > 1;
            float x = cx + (float) (Math.max(-1, Math.min(1, a)) * (half - 14));
            float size = p.mission() ? 24 : 18;
            if (!CardArt.icon(ui, p.icon(), x - size / 2, y + h - 4 - size / 2 + (p.mission() ? 0 : 2), size,
                    behind ? 0.5f : 1f)) {
                ui.disc(x, y + h - 2, 4, Theme.ACCENT);
            }
            if (!behind && Math.abs(a) < best) {
                best = Math.abs(a);
                nearest = p;
            }
        }
        if (nearest != null) {
            double d = Math.hypot(nearest.at().x() - me.x(), nearest.at().z() - me.z());
            String text = nearest.label() + "  ·  " + Math.round(d) + " m";
            float tw = ui.small.width(text) + 16;
            ui.rect(cx - tw / 2, y + h + 12, tw, ui.small.lineHeight() + 4, Theme.withAlpha(Theme.SOOT, 0.6f));
            ui.textCentered(ui.small, text, cx, y + h + 13, Theme.TITLE);
        }
    }
}
