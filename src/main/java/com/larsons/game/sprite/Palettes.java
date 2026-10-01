package com.larsons.game.sprite;

/**
 * Arithmetic on a layer's palette (straight ARGB, entry {@code i} for palette
 * index {@code i}) for effects that change over time: a fade from one set of
 * colours to another and a flash. A pixel-art layer's colours are a palette
 * the GPU looks up ({@link com.larsons.game.gfx.PaletteAtlas}), so these cost
 * a few hundred integer operations a layer and a frame, nothing more.
 */
public final class Palettes {

    private Palettes() {}

    /** {@code a} turning into {@code b}: every entry's colour {@code t} (0..1) of the way, {@code b}'s alpha. */
    public static int[] mix(int[] a, int[] b, double t) {
        if (a == null || t >= 1) return b;
        if (t <= 0) return a;
        int[] out = b.clone();
        for (int i = 0; i < Math.min(a.length, b.length); i++) out[i] = (b[i] & 0xFF000000) | lerp(a[i], b[i], t);
        return out;
    }

    /** {@link #mix} of the given entries only; the rest are {@code b}'s. */
    public static int[] mix(int[] a, int[] b, double t, int[] entries) {
        if (a == null || t >= 1) return b;
        int[] out = b.clone();
        for (int e : entries) {
            if (e >= 0 && e < Math.min(a.length, b.length)) out[e] = (b[e] & 0xFF000000) | lerp(a[e], b[e], t);
        }
        return out;
    }

    /**
     * Every visible entry {@code amount} (0..1) of the way to {@code rgb}:
     * a flash of light, or of a colour. Transparent entries and the
     * empty-frame marker (alpha 1/255 and below) are left alone.
     */
    public static int[] flash(int[] p, int rgb, double amount) {
        if (p == null || amount <= 0) return p;
        int[] out = p.clone();
        for (int i = 0; i < out.length; i++) {
            if ((out[i] >>> 24) <= 1) continue;
            out[i] = (out[i] & 0xFF000000) | lerp(out[i], rgb, Math.min(1, amount));
        }
        return out;
    }

    private static int lerp(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
