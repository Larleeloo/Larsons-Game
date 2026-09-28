package com.larsons.game.ui;

/**
 * Menu colours, taken from Larsons-Game-Engine's {@code MenuTheme.dark()} so
 * the two projects' menus look like one family. Straight RGBA, 0–1.
 */
public final class Theme {

    private Theme() {}

    public static final float[] BACKGROUND = rgb(18, 18, 28, 1f);
    public static final float[] PANEL = rgb(24, 25, 38, 0.94f);
    public static final float[] PANEL_EDGE = rgb(70, 74, 100, 1f);
    public static final float[] SCRIM = rgb(8, 8, 14, 0.55f);
    public static final float[] TITLE = rgb(245, 245, 255, 1f);
    public static final float[] ITEM = rgb(180, 185, 200, 1f);
    public static final float[] ITEM_SELECTED = rgb(255, 210, 90, 1f);
    public static final float[] ITEM_DISABLED = rgb(90, 90, 100, 1f);
    public static final float[] ACCENT = rgb(255, 210, 90, 1f);
    public static final float[] WARNING = rgb(235, 120, 110, 1f);
    public static final float[] OK = rgb(130, 210, 140, 1f);
    public static final float[] HINT = rgb(225, 228, 240, 1f);
    public static final float[] BUTTON = rgb(38, 40, 58, 1f);
    public static final float[] BUTTON_HOVER = rgb(54, 57, 82, 1f);

    public static float[] rgb(int r, int g, int b, float a) {
        return new float[]{r / 255f, g / 255f, b / 255f, a};
    }

    public static float[] withAlpha(float[] c, float a) {
        return new float[]{c[0], c[1], c[2], a};
    }
}
