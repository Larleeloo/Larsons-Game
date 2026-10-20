package com.larsons.game.sprite;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The pixel art's 128 colours ({@code assets/sprites/pixel_palette.json},
 * the 3D-Modeling repository's {@code generic_feminine_model/pixel_palette.json}):
 * 100 for fills - a material's shadow, mid, light and glint - and 28 for
 * lines. Every colour the game puts on a pixel-art item in the engine (a
 * weapon's tier, a rarity) is snapped to it ({@link #snap}), so the art never
 * leaves the palette.
 *
 * <p>Snapping is to the nearest colour in OKLab with a colour's hue and
 * chroma counting double against its lightness - as the art itself is drawn
 * (3D-Modeling's {@code items/item_spin_sprites.py}) - so a warm grey's light
 * shade lands on a lighter warm grey, not on a cold blue-grey that happens to
 * be nearer in lightness.
 *
 * <pre>
 * {"fills": [[128, 72, 2], ...], "lines": [[23, 22, 24], ...], "families": {...}}
 * </pre>
 */
public final class PixelPalette {

    public static final String FILE_NAME = "pixel_palette.json";
    /** How much more a colour's hue and chroma count than its lightness when snapping. */
    static final double HUE_WEIGHT = 2.0;

    private final int[] fills, lines;
    private final double[][] fillLab, lineLab;

    public PixelPalette(int[] fills, int[] lines) {
        this.fills = fills.clone();
        this.lines = lines.clone();
        this.fillLab = lab(fills);
        this.lineLab = lab(lines);
    }

    /** {@code dir/pixel_palette.json}, or null if there is none (or it cannot be read). */
    public static PixelPalette load(Path dir) {
        Path f = dir.resolve(FILE_NAME);
        if (!Files.isRegularFile(f)) return null;
        try {
            return parse(Files.readString(f, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            System.err.println("[sprites] ignoring " + f + ": " + e.getMessage());
            return null;
        }
    }

    public static PixelPalette parse(String text) {
        Map<String, Object> json = Json.asObject(Json.parse(text));
        return new PixelPalette(colours(Json.asArray(json.get("fills"))), colours(Json.asArray(json.get("lines"))));
    }

    private static int[] colours(List<Object> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            List<Object> c = Json.asArray(list.get(i));
            out[i] = (((Number) c.get(0)).intValue() << 16) | (((Number) c.get(1)).intValue() << 8)
                    | ((Number) c.get(2)).intValue();
        }
        return out;
    }

    private static double[][] lab(int[] rgb) {
        double[][] out = new double[rgb.length][];
        for (int i = 0; i < rgb.length; i++) out[i] = Variants.Oklab.of(rgb[i]);
        return out;
    }

    /** The fill colours (RGB). */
    public int[] fills() { return fills.clone(); }

    /** The line colours (RGB). */
    public int[] lines() { return lines.clone(); }

    /** Whether {@code rgb} is one of the 128. */
    public boolean contains(int rgb) {
        rgb &= 0xFFFFFF;
        for (int c : fills) if (c == rgb) return true;
        for (int c : lines) if (c == rgb) return true;
        return false;
    }

    /** The palette's nearest fill ({@code line} false) or line colour (RGB) to {@code rgb}. */
    public int snap(int rgb, boolean line) {
        return snap(Variants.Oklab.of(rgb & 0xFFFFFF), line);
    }

    /** The palette's nearest fill or line colour (RGB) to an OKLab colour {@code {L, a, b}}. */
    public int snap(double[] lab, boolean line) {
        double[][] from = line ? lineLab : fillLab;
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < from.length; i++) {
            double dl = lab[0] - from[i][0], da = lab[1] - from[i][1], db = lab[2] - from[i][2];
            double d = dl * dl + HUE_WEIGHT * (da * da + db * db);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return (line ? lines : fills)[best];
    }
}
