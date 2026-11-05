package com.larsons.game.combat;

import com.larsons.game.world.Border;
import com.larsons.game.world.Rarity;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The colours the fighting is drawn in, all from the game's 128-colour
 * palette: the dice in their weapon's rarity, the spells in their tier's
 * colour, a struck dummy's flash.
 *
 * <p>The D20 sheets ({@code assets/sprites/combat/d20_px*}) are drawn in
 * fixed palette slots - 1 its rim, 2 – 5 its faces from shadow to
 * highlight, 6 the numbers - so one set of sheets is every rarity's die:
 * white, green, blue, purple, orange and cyan, swapped on the GPU. A die
 * of a weapon with a rare border has its rim in the border's colour; a
 * forged weapon's dice may be any colour ({@link #die(int, Border)}). The
 * spell sheets ({@code magic_bolt}, {@code magic_burst}) are slots 1 – 5
 * likewise, from the dark edge to the white core.
 */
public final class CombatColours {

    private CombatColours() {}

    /** Each rarity's die: rim, shadow, mid, light, highlight, numbers. */
    private static final Map<Rarity, int[]> DICE = new EnumMap<>(Rarity.class);

    static {
        DICE.put(Rarity.COMMON, new int[]{0x5D5D62, 0xB8C0C5, 0xF1F1F8, 0xFFFFFF, 0xFFFFFF, 0x231C18});
        DICE.put(Rarity.UNCOMMON, new int[]{0x011C10, 0x017647, 0x0D9E44, 0x24C835, 0x75F676, 0xFFFFFF});
        DICE.put(Rarity.RARE, new int[]{0x1A024E, 0x140B7D, 0x0B61C5, 0x5D86FA, 0x92B9FF, 0xFFFFFF});
        DICE.put(Rarity.EPIC, new int[]{0x27042A, 0x4A0340, 0x691398, 0xB722E7, 0xE750FB, 0xFFFFFF});
        DICE.put(Rarity.LEGENDARY, new int[]{0x320505, 0xAE4D02, 0xD9740D, 0xFB992C, 0xF9C36D, 0xFFF0DB});
        DICE.put(Rarity.MYTHIC, new int[]{0x001F21, 0x1A7961, 0x439F9D, 0x55C77B, 0xC3FEC0, 0xFFFFFF});
    }

    /** The Magic tier's spells: dark edge, outer glow, glow, bright, core. */
    public static final int[] BLUE = {0x140B7D, 0x302AE5, 0x5D86FA, 0x92B9FF, 0xFFFFFF};
    /** The Honed tier's. */
    public static final int[] YELLOW = {0x9A5E00, 0xD18806, 0xFAB33E, 0xFCDD9E, 0xFFFFFF};

    private static final Map<String, int[]> diePalettes = new HashMap<>();
    private static final Map<Long, int[]> spellPalettes = new HashMap<>();

    /** A die's six colours for a weapon of {@code rarity} with {@code border} (null: none). */
    public static int[] die(Rarity rarity, Border border) {
        int[] c = DICE.get(rarity == null ? Rarity.COMMON : rarity).clone();
        if (border != null) c[0] = border.rgb();
        return c;
    }

    /**
     * A die's six colours from one ({@code rgb}, a forged weapon's): it as the
     * faces' mid shade, the shadow and the lights as much darker and lighter
     * as a white die's are, the rim a dark shade of it (or {@code border}'s),
     * the numbers white on a dark die and near black on a light one.
     */
    public static int[] die(int rgb, Border border) {
        double[] t = com.larsons.game.sprite.Variants.Oklab.of(rgb & 0xFFFFFF);
        double L = t[0], c = Math.hypot(t[1], t[2]), h = Math.atan2(t[2], t[1]);
        int[] out = new int[6];
        out[0] = border != null ? border.rgb() : shade(Math.max(0.16, L * 0.38), c * 0.75, h);
        out[1] = shade(L - 0.16, c * 1.05, h);
        out[2] = rgb & 0xFFFFFF;
        out[3] = shade(L + 0.08, c * 0.9, h);
        out[4] = shade(L + 0.16, c * 0.7, h);
        out[5] = L > 0.68 ? 0x231C18 : 0xFFFFFF;
        return out;
    }

    private static int shade(double L, double c, double h) {
        return com.larsons.game.sprite.Variants.Oklab.rgb(Math.max(0.08, Math.min(0.99, L)), c * Math.cos(h),
                c * Math.sin(h));
    }

    /**
     * The palette to draw a die's sheet with (slot 0 clear), for a weapon of
     * {@code rarity} with {@code border} - the same array each time, for
     * the GPU's palette rows.
     */
    public static int[] diePalette(Rarity rarity, Border border) {
        return diePalette(rarity, border, -1);
    }

    /** {@link #diePalette(Rarity, Border)} - or, {@code rgb} not -1, in that colour ({@link #die(int, Border)}). */
    public static int[] diePalette(Rarity rarity, Border border, int rgb) {
        String key = (rarity == null ? Rarity.COMMON : rarity).key() + "/" + (border == null ? "" : border.key())
                + (rgb < 0 ? "" : "#" + Integer.toHexString(rgb & 0xFFFFFF));
        return diePalettes.computeIfAbsent(key, k -> {
            int[] c = rgb < 0 ? die(rarity, border) : die(rgb, border);
            int[] p = new int[256];
            for (int i = 0; i < c.length; i++) p[i + 1] = 0xFF000000 | c[i];
            return p;
        });
    }

    /** A spell's five colours for one of {@code rgb}: blue, yellow, or a rarity's own. */
    public static int[] spell(int rgb) {
        if (rgb == 0x5D86FA) return BLUE;
        if (rgb == 0xF9C36D || rgb == 0xFAB33E) return YELLOW;
        for (Rarity r : Rarity.values()) {
            if (r.rgb() == rgb) {
                int[] d = DICE.get(r);
                return new int[]{d[0], d[1], d[2], d[3], 0xFFFFFF};
            }
        }
        return BLUE;
    }

    /**
     * {@code sheet} - a spell sheet's own palette - in the colours of {@code
     * rgb} ({@link #spell}), each slot keeping its alpha (the outer glow is
     * see-through).
     */
    public static int[] spellPalette(int[] sheet, int rgb) {
        if (sheet == null) return null;
        long key = ((long) System.identityHashCode(sheet) << 32) | (rgb & 0xFFFFFFFFL);
        return spellPalettes.computeIfAbsent(key, k -> {
            int[] c = spell(rgb);
            int[] p = sheet.clone();
            for (int i = 0; i < c.length && i + 1 < p.length; i++) {
                p[i + 1] = (p[i + 1] & 0xFF000000) | (c[i] & 0xFFFFFF);
            }
            return p;
        });
    }

    /** {@code argb} moved {@code k} of the way to white, alpha kept: a struck thing's flash. */
    public static int[] flashed(int[] argb, double k) {
        if (argb == null || k <= 0) return argb;
        double m = Math.min(1, k) * 0.75;
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            int r = (int) Math.round(((c >> 16) & 255) * (1 - m) + 255 * m);
            int g = (int) Math.round(((c >> 8) & 255) * (1 - m) + 255 * m);
            int b = (int) Math.round((c & 255) * (1 - m) + 255 * m);
            out[i] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
        }
        return out;
    }

    /** {@code rgb} as straight RGBA floats at alpha {@code a}, for the UI. */
    public static float[] rgba(int rgb, float a) {
        return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a};
    }
}
