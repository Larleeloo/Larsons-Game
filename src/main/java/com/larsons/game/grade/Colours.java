package com.larsons.game.grade;

/** Colours as hue, saturation and value - what a colour wheel is turned in - and back. */
public final class Colours {

    private Colours() {}

    /** {@code rgb} as hue (0 - 360), saturation and value (0 - 1). */
    public static float[] hsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = 60 * (((g - b) / d) % 6);
        else if (max == g) h = 60 * ((b - r) / d + 2);
        else h = 60 * ((r - g) / d + 4);
        if (h < 0) h += 360;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    /** The colour of hue {@code h} (degrees), saturation {@code s} and value {@code v}, as {@code 0xRRGGBB}. */
    public static int rgb(double h, double s, double v) {
        h = ((h % 360) + 360) % 360;
        s = Math.max(0, Math.min(1, s));
        v = Math.max(0, Math.min(1, v));
        double c = v * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = v - c;
        double r, g, b;
        if (h < 60) {
            r = c;
            g = x;
            b = 0;
        } else if (h < 120) {
            r = x;
            g = c;
            b = 0;
        } else if (h < 180) {
            r = 0;
            g = c;
            b = x;
        } else if (h < 240) {
            r = 0;
            g = x;
            b = c;
        } else if (h < 300) {
            r = x;
            g = 0;
            b = c;
        } else {
            r = c;
            g = 0;
            b = x;
        }
        return ((int) Math.round((r + m) * 255) << 16) | ((int) Math.round((g + m) * 255) << 8)
                | (int) Math.round((b + m) * 255);
    }

    /** {@code 0xRRGGBB} as straight RGBA floats. */
    public static float[] rgba(int rgb, float a) {
        return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a};
    }
}
