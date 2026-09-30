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
 * sheets draws every colour.
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

    static Variants parse(String text) {
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
            int[] cols = e.getValue().options().get(opt);
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
