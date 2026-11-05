package com.larsons.game.sprite;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * An animal recoloured: a herd's cows brown, its horses grey and chestnut,
 * its sheep black. An animal's sheets are palette PNGs, and its profile
 * lists the colours of its coat ({@link SpriteProfile#tint}); recolouring it
 * is giving those palette entries new colours - nothing is drawn again and no
 * sheet is copied - so its eyes, hooves, bill and spots stay as they are.
 *
 * <p>The coat's colours all take the new colour's hue and saturation, each
 * as much lighter or darker than the coat's middle as it was (its shading,
 * its lines), round the new colour's own lightness - and every one snapped to
 * the game's 128 colours ({@link PixelPalette}), so the art never leaves the
 * palette. The same array comes back for the same palette and colour, so the
 * GPU's palette rows are shared from frame to frame.
 */
public final class CoatTint {

    /** Memoised palettes kept before the memo starts over. */
    private static final int MEMO = 1024;

    private final PixelPalette palette;
    private final Map<int[], Map<Long, int[]>> memo = new IdentityHashMap<>();
    private int memoSize;

    /** Recolouring snapped to {@code palette} (null: not snapped). */
    public CoatTint(PixelPalette palette) {
        this.palette = palette;
    }

    /**
     * {@code base} (a sheet's ARGB palette) with every entry that is one of
     * {@code coat}'s colours in {@code rgb} ({@code -1}: {@code base} as it is).
     */
    public int[] tinted(int[] base, int[] coat, int rgb) {
        if (base == null || coat == null || coat.length == 0 || rgb < 0) return base;
        long key = ((long) (rgb & 0xFFFFFF) << 20) ^ System.identityHashCode(coat);
        Map<Long, int[]> forBase = memo.get(base);
        if (forBase != null) {
            int[] hit = forBase.get(key);
            if (hit != null) return hit;
        }
        int[] out = recolour(base, coat, rgb & 0xFFFFFF, palette);
        if (memoSize >= MEMO) {
            memo.clear();
            memoSize = 0;
        }
        memo.computeIfAbsent(base, b -> new HashMap<>()).put(key, out);
        memoSize++;
        return out;
    }

    /** {@link #tinted}, unmemoised. */
    public static int[] recolour(int[] base, int[] coat, int rgb, PixelPalette palette) {
        Set<Integer> in = new HashSet<>();
        for (int c : coat) in.add(c & 0xFFFFFF);
        // the coat's middle: the mean lightness of its colours as they are in this sheet
        double mid = 0;
        int n = 0;
        for (int c : base) {
            if (!in.contains(c & 0xFFFFFF) || (c >>> 24) == 0) continue;
            mid += Variants.Oklab.of(c & 0xFFFFFF)[0];
            n++;
        }
        if (n == 0) return base;
        mid /= n;
        double[] t = Variants.Oklab.of(rgb);
        double cT = Math.hypot(t[1], t[2]), hT = Math.atan2(t[2], t[1]);
        int[] out = base.clone();
        for (int i = 0; i < base.length; i++) {
            int c = base[i];
            if (!in.contains(c & 0xFFFFFF) || (c >>> 24) == 0) continue;
            double[] l = Variants.Oklab.of(c & 0xFFFFFF);
            double L = clamp(t[0] + (l[0] - mid) * 0.9, 0.08, 0.98);
            // the darkest shades (its lines) a little less saturated, as drawn
            double k = L < 0.3 ? 0.7 : 1.0;
            double[] lab = {L, cT * k * Math.cos(hT), cT * k * Math.sin(hT)};
            int snapped = palette != null ? palette.snap(lab, L < 0.3 && l[0] < mid - 0.25)
                    : Variants.Oklab.rgb(lab[0], lab[1], lab[2]);
            out[i] = (c & 0xFF000000) | snapped;
        }
        return out;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
