package com.larsons.game.ui;

/**
 * The menus' colours: stained oak and brass, iron and copper, cream
 * lettering - the game's steampunk-meets-medieval look, which every panel,
 * button and bar is drawn in ({@link Ui}). Straight RGBA, 0–1.
 */
public final class Theme {

    private Theme() {}

    public static final float[] BACKGROUND = rgb(20, 15, 12, 1f);
    /** A panel's stained oak, where the wood itself is not drawn. */
    public static final float[] PANEL = rgb(40, 29, 20, 0.95f);
    /** The brass that frames a panel and a button. */
    public static final float[] PANEL_EDGE = rgb(150, 112, 54, 1f);
    public static final float[] SCRIM = rgb(10, 7, 5, 0.6f);
    public static final float[] TITLE = rgb(248, 234, 204, 1f);
    public static final float[] ITEM = rgb(210, 194, 164, 1f);
    public static final float[] ITEM_SELECTED = rgb(255, 214, 110, 1f);
    public static final float[] ITEM_DISABLED = rgb(134, 118, 98, 1f);
    public static final float[] ACCENT = rgb(244, 198, 96, 1f);
    public static final float[] WARNING = rgb(236, 116, 88, 1f);
    /** Verdigris: what is well. */
    public static final float[] OK = rgb(126, 204, 160, 1f);
    public static final float[] HINT = rgb(234, 224, 202, 1f);
    public static final float[] BUTTON = rgb(54, 41, 30, 1f);
    public static final float[] BUTTON_HOVER = rgb(84, 62, 40, 1f);

    // the metalwork: brass lit from the top left, iron, copper and the ink on parchment
    public static final float[] BRASS = rgb(176, 134, 64, 1f);
    public static final float[] BRASS_LIGHT = rgb(232, 196, 116, 1f);
    public static final float[] BRASS_DARK = rgb(96, 68, 30, 1f);
    public static final float[] COPPER = rgb(190, 110, 70, 1f);
    public static final float[] IRON = rgb(58, 56, 58, 1f);
    public static final float[] SOOT = rgb(14, 10, 8, 1f);
    public static final float[] INK = rgb(52, 34, 20, 1f);
    public static final float[] INK_FADED = rgb(112, 88, 62, 1f);

    public static float[] rgb(int r, int g, int b, float a) {
        return new float[]{r / 255f, g / 255f, b / 255f, a};
    }

    /** The colour {@code 0xRRGGBB}, {@code a} opaque. */
    public static float[] hex(int rgb, float a) {
        return rgb(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, a);
    }

    public static float[] withAlpha(float[] c, float a) {
        return new float[]{c[0], c[1], c[2], a};
    }
}
