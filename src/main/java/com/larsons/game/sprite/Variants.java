package com.larsons.game.sprite;

import com.larsons.game.util.Json;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The colours an item's sheets can be drawn in, from the {@code variants.json}
 * beside them.
 *
 * <p>The pixel-art sheets are palette PNGs whose palette entries are the item's
 * colour <em>labels</em> — one per material and shade, one per material and
 * line — coloured with the item's own colours (entry 0 is transparent, 1 the
 * empty-frame marker, label {@code i} is entry {@code i + 2}). Each label
 * belongs to one of the item's colour channels or to none (teeth, brass: never
 * recoloured):
 * <ul>
 *   <li>{@code own} — what the item's slot colour picks: a shirt's cloth, the
 *       lips of a mouth, hair;</li>
 *   <li>{@code skin} — the skin tone, wherever an item shows skin (the body,
 *       ears, nose, the mouth round the lips): it follows the body's colour.</li>
 * </ul>
 * For every option of a channel ({@code royal_blue}, {@code silver} …) the
 * file lists the channel's labels' colours; recolouring an item is swapping
 * those entries of its sheets' palette ({@link #recolor}), so one set of
 * sheets draws every colour. The swap is made on the GPU as the layer is
 * drawn ({@link #recolored}, {@link com.larsons.game.gfx.PaletteAtlas}): a
 * sheet is uploaded once, whatever colours it is worn in.
 *
 * <p>A colour can also be any colour at all, written {@code #rrggbb} instead
 * of an option's name ({@link #isCustom}): the channel's labels are then
 * shaded like its first option, round that colour ({@link #custom}).
 *
 * <pre>
 * {"labels": 36, "default": {"own": "brown"},
 *  "channels": {"own": {"labels": [0, 1, …], "options": {"black": ["#1c1a19", …], …},
 *                       "swatch": {"black": "#2a2624", …}}, …}}
 * </pre>
 */
public final class Variants {

    /** One channel: which labels it colours, and their colours in each option. */
    public record Channel(int[] labels, Map<String, int[]> options, Map<String, Integer> swatch) {

        /** The options in file order. */
        public List<String> names() { return new ArrayList<>(options.keySet()); }
    }

    /** A palette swap: these entries of a sheet's palette get these colours (RGB). */
    public record Recolor(String key, int[] entries, int[] rgb) {}

    public static final String OWN = "own", SKIN = "skin";

    private final Map<String, Channel> channels;
    private final Map<String, String> defaults;

    Variants(Map<String, Channel> channels, Map<String, String> defaults) {
        this.channels = channels;
        this.defaults = defaults;
    }

    /** Read {@code dir/variants.json}, or {@code null} if there is none (or it cannot be read). */
    public static Variants load(Path dir) {
        Path f = dir.resolve("variants.json");
        if (!Files.isRegularFile(f)) return null;
        try {
            return parse(Files.readString(f, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            System.err.println("[sprites] ignoring " + f + ": " + e.getMessage());
            return null;
        }
    }

    /** Read a {@code variants.json}'s text. */
    public static Variants parse(String text) {
        Map<String, Object> json = Json.asObject(Json.parse(text));
        Map<String, Channel> channels = new LinkedHashMap<>();
        Map<String, Object> chans = Json.obj(json, "channels");
        if (chans != null) {
            for (Map.Entry<String, Object> e : chans.entrySet()) {
                Map<String, Object> c = Json.asObject(e.getValue());
                List<Object> ls = Json.asArray(c.get("labels"));
                int[] labels = new int[ls.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = ((Number) ls.get(i)).intValue();
                Map<String, int[]> options = new LinkedHashMap<>();
                Map<String, Object> opts = Json.obj(c, "options");
                if (opts != null) {
                    for (Map.Entry<String, Object> o : opts.entrySet()) {
                        List<Object> cols = Json.asArray(o.getValue());
                        int[] rgb = new int[cols.size()];
                        for (int i = 0; i < rgb.length; i++) rgb[i] = hex(String.valueOf(cols.get(i)));
                        options.put(o.getKey(), rgb);
                    }
                }
                Map<String, Integer> swatch = new LinkedHashMap<>();
                Map<String, Object> sw = Json.obj(c, "swatch");
                if (sw != null) for (Map.Entry<String, Object> o : sw.entrySet()) {
                    swatch.put(o.getKey(), hex(String.valueOf(o.getValue())));
                }
                channels.put(e.getKey(), new Channel(labels, Collections.unmodifiableMap(options),
                        Collections.unmodifiableMap(swatch)));
            }
        }
        Map<String, String> defaults = new TreeMap<>();
        Map<String, Object> def = Json.obj(json, "default");
        if (def != null) for (Map.Entry<String, Object> e : def.entrySet()) defaults.put(e.getKey(), String.valueOf(e.getValue()));
        return new Variants(Collections.unmodifiableMap(channels), Collections.unmodifiableMap(defaults));
    }

    private static int hex(String s) {
        String h = s.startsWith("#") ? s.substring(1) : s;
        return Integer.parseInt(h, 16) & 0xFFFFFF;
    }

    public Channel channel(String name) {
        return channels.get(name);
    }

    public boolean has(String channel) {
        return channels.containsKey(channel);
    }

    /** The option the sheets are drawn in when nothing is picked ({@code brown} hair), or null. */
    public String defaultOption(String channel) {
        return defaults.get(channel);
    }

    /**
     * The palette swap for a choice of colours ({@code {own: royal_blue, skin:
     * copper}}); channels not chosen, and options the item does not have, keep
     * their own colours. {@code null} if nothing changes.
     */
    public Recolor recolor(Map<String, String> choice) {
        List<int[]> parts = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        for (Map.Entry<String, Channel> e : channels.entrySet()) {
            String opt = choice.get(e.getKey());
            if (opt == null || opt.equals(defaults.get(e.getKey()))) continue;
            int[] cols = isCustom(opt) ? custom(e.getValue(), hex(opt)) : e.getValue().options().get(opt);
            if (cols == null) continue;
            int[] labels = e.getValue().labels();
            int n = Math.min(labels.length, cols.length);
            for (int i = 0; i < n; i++) parts.add(new int[]{labels[i] + 2, cols[i]});
            key.append(key.isEmpty() ? "" : ",").append(e.getKey()).append('=').append(opt);
        }
        if (parts.isEmpty()) return null;
        int[] entries = new int[parts.size()], rgb = new int[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            entries[i] = parts.get(i)[0];
            rgb[i] = parts.get(i)[1];
        }
        return new Recolor(key.toString(), entries, rgb);
    }

    /** Whether a colour is {@code #rrggbb} - any colour - rather than an option's name. */
    public static boolean isCustom(String colour) {
        return colour != null && colour.length() == 7 && colour.charAt(0) == '#'
                && colour.substring(1).chars().allMatch(ch -> Character.digit(ch, 16) >= 0);
    }

    /**
     * The colours of a channel's labels drawn in {@code rgb}: each label as
     * much lighter or darker, more or less saturated and turned in hue from
     * the {@code rgb} as it is from the swatch in the channel's first option
     * (in OKLab) - so a shirt in any colour keeps its shading, lines and
     * highlights. Null if the channel has no option to take the shading from.
     */
    public static int[] custom(Channel c, int rgb) {
        if (c.options().isEmpty()) return null;
        String ref = c.options().keySet().iterator().next();
        int[] cols = c.options().get(ref);
        Integer sw = c.swatch().get(ref);
        double[] s = Oklab.of(sw != null ? sw : cols[cols.length / 2]);
        double[] t = Oklab.of(rgb);
        double sc = Math.hypot(s[1], s[2]), tc = Math.hypot(t[1], t[2]);
        double sh = Math.atan2(s[2], s[1]), th = Math.atan2(t[2], t[1]);
        int[] out = new int[cols.length];
        for (int i = 0; i < cols.length; i++) {
            double[] l = Oklab.of(cols[i]);
            double lc = Math.hypot(l[1], l[2]), lh = Math.atan2(l[2], l[1]);
            double L = Math.max(0, Math.min(1, t[0] + (l[0] - s[0])));
            double C = Math.min(0.32, sc > 1e-4 ? tc * lc / sc : tc);
            double h = sc > 1e-4 ? th + (lh - sh) : th;
            out[i] = Oklab.rgb(L, C * Math.cos(h), C * Math.sin(h));
        }
        return out;
    }

    /** The palette entries a channel colours (its labels' entries, label + 2). */
    public int[] entries(String channel) {
        Channel c = channels.get(channel);
        if (c == null) return new int[0];
        int[] e = new int[c.labels().length];
        for (int i = 0; i < e.length; i++) e[i] = c.labels()[i] + 2;
        return e;
    }

    /** OKLab (Björn Ottosson's) from and to sRGB, for {@link #custom}. */
    static final class Oklab {
        private Oklab() {}

        static double[] of(int rgb) {
            double r = lin((rgb >> 16) & 0xFF), g = lin((rgb >> 8) & 0xFF), b = lin(rgb & 0xFF);
            double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
            double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
            double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
            return new double[]{0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                    1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                    0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
        }

        static int rgb(double L, double a, double b) {
            double l = L + 0.3963377774 * a + 0.2158037573 * b;
            double m = L - 0.1055613458 * a - 0.0638541728 * b;
            double s = L - 0.0894841775 * a - 1.2914855480 * b;
            l = l * l * l;
            m = m * m * m;
            s = s * s * s;
            int r = srgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s);
            int g = srgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s);
            int bl = srgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
            return (r << 16) | (g << 8) | bl;
        }

        private static double lin(int c) {
            double v = c / 255.0;
            return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }

        private static int srgb(double v) {
            v = Math.max(0, Math.min(1, v));
            v = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
            return (int) Math.round(v * 255);
        }
    }

    /**
     * A sheet's palette ({@code base}, straight ARGB) in {@code r}'s colours:
     * a copy with {@code r}'s entries swapped, their alpha kept; {@code base}
     * itself when {@code r} is null. What a layer is drawn in.
     */
    public static int[] recolored(int[] base, Recolor r) {
        if (base == null || r == null) return base;
        int[] out = base.clone();
        for (int i = 0; i < r.entries().length; i++) {
            int e = r.entries()[i];
            if (e >= 0 && e < out.length) out[e] = (out[e] & 0xFF000000) | r.rgb()[i];
        }
        return out;
    }

    /**
     * {@code img} with {@code r}'s entries of its palette swapped (the pixels are
     * shared, not copied); {@code img} itself if it has no palette to swap.
     */
    public static BufferedImage apply(BufferedImage img, Recolor r) {
        if (r == null || !(img.getColorModel() instanceof IndexColorModel icm)) return img;
        int size = icm.getMapSize();
        int[] argb = new int[size];
        icm.getRGBs(argb);
        for (int i = 0; i < r.entries().length; i++) {
            int e = r.entries()[i];
            if (e >= 0 && e < size) argb[e] = (argb[e] & 0xFF000000) | r.rgb()[i];
        }
        IndexColorModel swapped = new IndexColorModel(icm.getPixelSize(), size, argb, 0, true, -1,
                img.getRaster().getDataBuffer().getDataType());
        return new BufferedImage(swapped, img.getRaster(), img.isAlphaPremultiplied(), null);
    }
}
