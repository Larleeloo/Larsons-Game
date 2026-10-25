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
 * of a weapon with a rare border has its rim in the border's colour. The
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
     * The palette to draw a D20 sheet with (slot 0 clear), for a weapon of
     * {@code rarity} with {@code border} - the same array each time, for
     * the GPU's palette rows.
     */
    public static int[] diePalette(Rarity rarity, Border border) {
        String key = (rarity == null ? Rarity.COMMON : rarity).key() + "/" + (border == null ? "" : border.key());
        return diePalettes.computeIfAbsent(key, k -> {
            int[] c = die(rarity, border);
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
