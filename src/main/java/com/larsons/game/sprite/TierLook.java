package com.larsons.game.sprite;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a weapon of each tier of Larson's chart looks, from {@code
 * assets/sprites/tiers.json}: a Sharper weapon's handle black, a Crafted
 * one's body black, a Honed one's wood darker, a Crystal one's blade (or
 * tip) pink-purple, a Perfect one all gold, a Mythic one all smoky purple -
 * a Basic or Magic one as it is drawn. The item descriptions' words, made in
 * the engine as a palette swap ({@link #palette}): the pixel art's sheets are
 * palette PNGs whose entries are a material's six labels (shadow, mid, light,
 * glint, inner line, outer line), so a part is recoloured by giving its
 * materials' labels new colours, every one snapped to the 128-colour palette
 * ({@link PixelPalette}). Nothing is drawn again and no sheet is copied.
 *
 * <p>What is recoloured is named by <em>part</em> - {@code handle}, {@code
 * blade} (an edge, a point, the staff's crystal, the mace's head), {@code
 * body} (a bow's limbs, a crossbow's stock), {@code prod}, {@code wood}
 * (a staff's or a wand's shaft), {@code fittings} (guard, pommel, bands),
 * {@code tip}, {@code string}; {@code all} is every part but the string. A
 * sheet says which of its materials is which part: the turning item sprites
 * in the {@code parts.json} beside them ({@link ItemSprites}), the in-hand
 * sheets in this file's {@code carried} map for each weapon.
 *
 * <pre>
 * {"colours": {"black": "#231c18", "gold": "#f9c36d", ...},
 *  "tiers": {"sharper": {"handle": "black"}, "honed": {"wood": "darker"},
 *            "perfect": {"all": "gold"}, ...},
 *  "items": {"battle_axe": {"carried": {"handle": [0], "blade": [1]}},
 *            "crystal_staff": {"carried": {...}, "tiers": {"basic": {"blade": "dull"}}}, ...}}
 * </pre>
 *
 * <p>A part given a colour takes it as its mid shade, its other shades and
 * lines kept as much lighter or darker, more or less saturated and turned in
 * hue from it as they were from its own mid ({@link #shift}); {@code darker}
 * darkens a part's every shade ({@link #darken}); and {@code all} tints the
 * whole weapon - its parts keep how light or dark each is next to the others,
 * all in the colour's hue ({@link #tint}). An item's own {@code tiers} entry
 * adds to (or overrides) the tier's look for it: the Basic Staff's crystal is
 * a dull grey, the crystal staff as drawn being the Magic Staff.
 */
public final class TierLook {

    public static final String FILE_NAME = "tiers.json";
    /** The look's part that means the whole weapon (but its string). */
    public static final String ALL = "all";
    /** The part no tier recolours. */
    public static final String STRING = "string";
    /** The look that darkens a part rather than colouring it. */
    public static final String DARKER = "darker";

    static final int LABELS_PER_MATERIAL = 6;
    /** {@link #DARKER}: how much of its lightness a shade keeps (a line keeps more). */
    static final double DARKER_FILL = 0.72, DARKER_LINE = 0.85;
    /** {@link #ALL}: how much of the lightness between its parts a tinted weapon keeps. */
    static final double TINT_CONTRAST = 0.8;
    /** Memoised palettes kept before the memo starts over. */
    private static final int MEMO = 512;

    private final Map<String, Integer> colours;
    private final Map<String, Map<String, String>> tiers;
    private final Map<String, Map<String, Map<String, String>>> itemTiers;
    private final Map<String, Map<String, int[]>> carried;
    private final PixelPalette palette;
    private final Map<int[], Map<String, int[]>> memo = new IdentityHashMap<>();
    private int memoSize;

    TierLook(Map<String, Integer> colours, Map<String, Map<String, String>> tiers,
             Map<String, Map<String, Map<String, String>>> itemTiers, Map<String, Map<String, int[]>> carried,
             PixelPalette palette) {
        this.colours = colours;
        this.tiers = tiers;
        this.itemTiers = itemTiers;
        this.carried = carried;
        this.palette = palette;
    }

    /** No looks: every tier as it is drawn. */
    public static TierLook none() {
        return new TierLook(Map.of(), Map.of(), Map.of(), Map.of(), null);
    }

    /** {@code dir/tiers.json}, snapped to {@code palette} (null: not snapped); {@link #none()} without it. */
    public static TierLook load(Path dir, PixelPalette palette) {
        Path f = dir.resolve(FILE_NAME);
        if (!Files.isRegularFile(f)) return none();
        try {
            return parse(Files.readString(f, StandardCharsets.UTF_8), palette);
        } catch (IOException | RuntimeException e) {
            System.err.println("[sprites] ignoring " + f + ": " + e.getMessage());
            return none();
        }
    }

    public static TierLook parse(String text, PixelPalette palette) {
        Map<String, Object> json = Json.asObject(Json.parse(text));
        Map<String, Integer> colours = new HashMap<>();
        Map<String, Object> cj = Json.obj(json, "colours");
        if (cj != null) for (Map.Entry<String, Object> e : cj.entrySet()) colours.put(e.getKey(), hex(e.getValue()));
        Map<String, Map<String, Map<String, String>>> itemTiers = new HashMap<>();
        Map<String, Map<String, int[]>> carried = new HashMap<>();
        Map<String, Object> items = Json.obj(json, "items");
        if (items != null) {
            for (Map.Entry<String, Object> e : items.entrySet()) {
                Map<String, Object> item = Json.asObject(e.getValue());
                Map<String, Object> parts = Json.obj(item, "carried");
                if (parts != null) carried.put(e.getKey(), parts(parts));
                itemTiers.put(e.getKey(), looks(Json.obj(item, "tiers")));
            }
        }
        return new TierLook(colours, looks(Json.obj(json, "tiers")), itemTiers, carried, palette);
    }

    private static Map<String, Map<String, String>> looks(Map<String, Object> json) {
        Map<String, Map<String, String>> out = new HashMap<>();
        if (json == null) return out;
        for (Map.Entry<String, Object> e : json.entrySet()) {
            Map<String, String> look = new LinkedHashMap<>();
            for (Map.Entry<String, Object> p : Json.asObject(e.getValue()).entrySet()) {
                look.put(p.getKey(), String.valueOf(p.getValue()));
            }
            out.put(e.getKey(), Collections.unmodifiableMap(look));
        }
        return out;
    }

    /** A part map: {@code {"handle": [0], "blade": [1, 2]}} (materials by index). */
    public static Map<String, int[]> parts(Map<String, Object> json) {
        Map<String, int[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : json.entrySet()) {
            List<Object> l = Json.asArray(e.getValue());
            int[] m = new int[l.size()];
            for (int i = 0; i < m.length; i++) m[i] = ((Number) l.get(i)).intValue();
            out.put(e.getKey(), m);
        }
        return Collections.unmodifiableMap(out);
    }

    private static int hex(Object v) {
        String s = String.valueOf(v);
        return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16) & 0xFFFFFF;
    }

    /**
     * What {@code tier} looks like on {@code item}: part to colour name (or
     * {@link #DARKER}), the tier's look with the item's own over it - empty
     * when it is drawn as it is.
     */
    public Map<String, String> look(String item, String tier) {
        Map<String, String> out = new LinkedHashMap<>(tiers.getOrDefault(tier, Map.of()));
        Map<String, Map<String, String>> own = itemTiers.get(item);
        if (own != null && own.containsKey(tier)) out.putAll(own.get(tier));
        return out;
    }

    /** Which materials of {@code item}'s in-hand sheets are which part, or null when the file does not say. */
    public Map<String, int[]> carried(String item) {
        return carried.get(item);
    }

    /** The colour a look names (RGB), or -1 for {@link #DARKER} or a name the file does not have. */
    public int colour(String name) {
        Integer c = colours.get(name);
        return c == null ? -1 : c;
    }

    /**
     * A sheet's palette ({@code base}: straight ARGB, entry {@code i} for
     * index {@code i}) of {@code item} drawn in {@code tier}'s look, {@code
     * parts} saying which of the sheet's materials is which part - {@code
     * base} itself when the tier changes nothing (or the sheet has no parts).
     * The same array every time for the same sheet, item and tier, so the
     * GPU's palette rows ({@link com.larsons.game.gfx.PaletteAtlas}) are shared
     * frame after frame.
     */
    public int[] palette(int[] base, String item, String tier, Map<String, int[]> parts) {
        if (base == null || tier == null || parts == null || parts.isEmpty()) return base;
        Map<String, int[]> forBase = memo.get(base);
        String key = item + "/" + tier;
        if (forBase != null) {
            int[] hit = forBase.get(key);
            if (hit != null) return hit;
        }
        Map<String, String> look = look(item, tier);
        int[] out = look.isEmpty() ? base : recolour(base, parts, look);
        if (memoSize >= MEMO) {
            memo.clear();
            memoSize = 0;
        }
        memo.computeIfAbsent(base, b -> new HashMap<>()).put(key, out);
        memoSize++;
        return out;
    }

    /**
     * A sheet's palette ({@code base}) of {@code item} with its {@code part}
     * in {@code rgb} ({@link #shift}) - a wearable's pickup, its shirt in the
     * rarity's colour or its bottle in the dye's - {@code base} itself when
     * the sheet has no such part. Kept like {@link #palette}'s.
     */
    public int[] paint(int[] base, String item, Map<String, int[]> parts, String part, int rgb) {
        if (base == null || parts == null || !parts.containsKey(part)) return base;
        Map<String, int[]> forBase = memo.get(base);
        String key = item + "/" + part + "#" + Integer.toHexString(rgb & 0xFFFFFF);
        if (forBase != null) {
            int[] hit = forBase.get(key);
            if (hit != null) return hit;
        }
        int[] out = base.clone();
        for (int m : parts.get(part)) shift(base, out, m, rgb & 0xFFFFFF);
        if (memoSize >= MEMO) {
            memo.clear();
            memoSize = 0;
        }
        memo.computeIfAbsent(base, b -> new HashMap<>()).put(key, out);
        memoSize++;
        return out;
    }

    /** {@code base} with {@code look} made on {@code parts}: the whole weapon tinted first, then each named part. */
    int[] recolour(int[] base, Map<String, int[]> parts, Map<String, String> look) {
        int[] out = base.clone();
        String all = look.get(ALL);
        if (all != null && colour(all) >= 0) {
            List<Integer> mats = new ArrayList<>();
            for (Map.Entry<String, int[]> p : parts.entrySet()) {
                if (p.getKey().equals(STRING)) continue;
                for (int m : p.getValue()) if (!mats.contains(m)) mats.add(m);
            }
            tint(base, out, mats, colour(all));
        }
        for (Map.Entry<String, String> l : look.entrySet()) {
            if (l.getKey().equals(ALL)) continue;
            int[] mats = parts.get(l.getKey());
            if (mats == null) continue;
            for (int m : mats) {
                if (l.getValue().equals(DARKER)) darken(base, out, m);
                else if (colour(l.getValue()) >= 0) shift(base, out, m, colour(l.getValue()));
            }
        }
        return out;
    }

    /** Palette entry of material {@code m}'s label {@code j}. */
    private static int entry(int m, int j) {
        return 2 + m * LABELS_PER_MATERIAL + j;
    }

    private static boolean line(int j) {
        return j >= 4;
    }

    /** Write OKLab colour {@code lab} as material {@code m}'s label {@code j}, snapped, keeping its alpha. */
    private void put(int[] base, int[] out, int m, int j, double[] lab) {
        int e = entry(m, j);
        int rgb = palette != null ? palette.snap(lab, line(j)) : Variants.Oklab.rgb(lab[0], lab[1], lab[2]);
        out[e] = (base[e] & 0xFF000000) | rgb;
    }

    private static boolean has(int[] base, int m) {
        return entry(m, LABELS_PER_MATERIAL - 1) < base.length;
    }

    /**
     * Material {@code m} in {@code rgb}: its mid shade becomes the colour,
     * every other label as much lighter or darker, more or less saturated and
     * turned in hue from it as it was from the mid (a grey's labels take the
     * colour's hue, a little more saturated the more saturated they were) -
     * the character art's recolouring of an item (3D-Modeling's
     * {@code feminine_model_pixel_art.recolour}).
     */
    void shift(int[] base, int[] out, int m, int rgb) {
        if (!has(base, m)) return;
        double[] ref = Variants.Oklab.of(base[entry(m, 1)] & 0xFFFFFF);
        double[] t = Variants.Oklab.of(rgb);
        double cRef = Math.hypot(ref[1], ref[2]), cT = Math.hypot(t[1], t[2]);
        double hRef = Math.atan2(ref[2], ref[1]), hT = Math.atan2(t[2], t[1]);
        for (int j = 0; j < LABELS_PER_MATERIAL; j++) {
            double[] l = Variants.Oklab.of(base[entry(m, j)] & 0xFFFFFF);
            double cM = Math.hypot(l[1], l[2]), hM = Math.atan2(l[2], l[1]);
            double ratio, dh = 0;
            if (cRef > 0.02) {
                ratio = clamp(cM / cRef, 0.3, 1.6);
                if (cM > 0.02) dh = wrap(hM - hRef);
            } else {
                ratio = clamp(0.4 + cM / 0.05, 0.4, 1.4);
            }
            double L = clamp(t[0] + (l[0] - ref[0]), 0.12, 0.97);
            double c = cT * ratio, h = hT + dh;
            put(base, out, m, j, new double[]{L, c * Math.cos(h), c * Math.sin(h)});
        }
    }

    /** Material {@code m}, every label darker ({@value #DARKER_FILL} of its lightness; a line's {@value #DARKER_LINE}). */
    void darken(int[] base, int[] out, int m) {
        if (!has(base, m)) return;
        for (int j = 0; j < LABELS_PER_MATERIAL; j++) {
            double[] l = Variants.Oklab.of(base[entry(m, j)] & 0xFFFFFF);
            double k = line(j) ? DARKER_LINE : DARKER_FILL;
            put(base, out, m, j, new double[]{l[0] * k, l[1] * 0.9, l[2] * 0.9});
        }
    }

    /**
     * Materials {@code mats} all in {@code rgb}'s hue and chroma, each shade
     * as much lighter or darker than the materials' average mid as it was
     * ({@value #TINT_CONTRAST} of it) round the colour's own lightness - so a
     * Perfect sword is gold from point to pommel, its grip a darker gold than
     * its blade - and its lines a dark shade of the colour.
     */
    void tint(int[] base, int[] out, List<Integer> mats, int rgb) {
        double[] t = Variants.Oklab.of(rgb);
        double cT = Math.hypot(t[1], t[2]), hT = Math.atan2(t[2], t[1]);
        double avg = 0;
        int n = 0;
        for (int m : mats) {
            if (!has(base, m)) continue;
            avg += Variants.Oklab.of(base[entry(m, 1)] & 0xFFFFFF)[0];
            n++;
        }
        if (n == 0) return;
        avg /= n;
        for (int m : mats) {
            if (!has(base, m)) continue;
            for (int j = 0; j < LABELS_PER_MATERIAL; j++) {
                double[] l = Variants.Oklab.of(base[entry(m, j)] & 0xFFFFFF);
                double L, c;
                if (line(j)) {
                    L = clamp(l[0] + (t[0] - avg) * 0.5, 0.12, 0.5);
                    c = cT * 0.8;
                } else {
                    L = clamp(t[0] + (l[0] - avg) * TINT_CONTRAST, 0.12, 0.97);
                    c = cT;
                }
                put(base, out, m, j, new double[]{L, c * Math.cos(hT), c * Math.sin(hT)});
            }
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double wrap(double a) {
        return Math.atan2(Math.sin(a), Math.cos(a));
    }
}
