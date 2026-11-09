package com.larsons.game.building;

import com.larsons.game.sprite.PixelPalette;
import com.larsons.game.sprite.Variants;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How one building looks: a colour and a pattern for each of its parts - the
 * walls cream plaster, the side walls red brick, the roof thatch, the trim
 * dark timber - any colour with any pattern, part by part, as walls are
 * done in the Sims. What it does not say, its {@link BuildingProfile} does.
 *
 * <p>In a level file it is the building's {@code look}: {@code {"roof":
 * {"colour": "#a84a30", "texture": "slate"}, ...}}.
 *
 * <p>Each colour is drawn as a {@link #ramp} of six of the game's 128
 * colours, dark to light, so a building is shaded as the rest of the pixel
 * art is.
 */
public final class BuildingLook {

    /** A part's colour ({@code 0xRRGGBB}) and pattern. */
    public record Choice(int colour, String texture) {}

    private final Map<String, Choice> parts = new LinkedHashMap<>();

    public BuildingLook() {}

    /** The look written in a level file ({@code look}), or none. */
    public static BuildingLook of(Object look) {
        BuildingLook out = new BuildingLook();
        if (look instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> p)) continue;
                Object c = p.get("colour"), t = p.get("texture");
                out.parts.put(String.valueOf(e.getKey()), new Choice(
                        c == null ? -1 : parseColour(String.valueOf(c), -1), t == null ? null : String.valueOf(t)));
            }
        }
        return out;
    }

    /** As written in a level file: only the parts changed from {@code profile}'s own. */
    public Map<String, Object> toProps(BuildingProfile profile) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Choice> e : parts.entrySet()) {
            BuildingProfile.Part p = profile == null ? null : profile.part(e.getKey());
            Choice c = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            if (c.colour >= 0 && (p == null || c.colour != p.colour())) m.put("colour", hex(c.colour));
            if (c.texture != null && (p == null || !c.texture.equals(p.texture()))) m.put("texture", c.texture);
            if (!m.isEmpty()) out.put(e.getKey(), m);
        }
        return out;
    }

    /** Part {@code key}'s colour: its own here, else its profile's. */
    public int colour(BuildingProfile.Part p) {
        Choice c = parts.get(p.key());
        return c != null && c.colour >= 0 ? c.colour : p.colour();
    }

    /** Part {@code key}'s pattern: its own here, else its profile's. */
    public String texture(BuildingProfile.Part p) {
        Choice c = parts.get(p.key());
        return c != null && c.texture != null ? c.texture : p.texture();
    }

    public BuildingLook setColour(String part, int rgb) {
        Choice c = parts.get(part);
        parts.put(part, new Choice(rgb & 0xFFFFFF, c == null ? null : c.texture));
        return this;
    }

    public BuildingLook setTexture(String part, String texture) {
        Choice c = parts.get(part);
        parts.put(part, new Choice(c == null ? -1 : c.colour, texture));
        return this;
    }

    /** Nothing of its own: as the building comes. */
    public boolean isEmpty() { return parts.isEmpty(); }

    /** A copy. */
    public BuildingLook copy() {
        BuildingLook out = new BuildingLook();
        out.parts.putAll(parts);
        return out;
    }

    // --- one building's look onto another --------------------------------------------------

    /** Parts that play the same role in different buildings: walls with walls, roofs with roofs, … */
    private static final String[][] ROLES = {
            {"walls", "side_walls", "lower_walls", "body", "case", "fronts"},
            {"roof", "awning", "top", "canvas"},
            {"trim", "beams", "frame", "band", "posts", "post", "porch", "mantel"},
            {"foundation", "base", "stone", "bricks"},
            {"door", "portcullis"},
            {"windows", "glass", "slits"},
            {"shutters"},
            {"wood", "logs", "staves", "legs", "seat", "shelves", "counter"},
            {"metal", "iron", "brass", "copper", "hoops", "pipes", "gears"},
            {"chimney", "stack", "stacks"},
            {"battlements"},
            {"flame", "fire", "spark"},
    };

    /** What {@code key} does in a building - the first of its row in {@link #ROLES} - or itself. */
    static String role(String key) {
        String k = key.replaceAll("\\d+$", "");
        for (String[] row : ROLES) for (String r : row) if (r.equals(k)) return row[0];
        return k;
    }

    /**
     * Which part of {@code from} {@code to}'s part {@code t} takes its look
     * from: the one of the same name, else of the same label, else the first
     * that plays the same role (walls, roof, trim …) - or null: none.
     */
    public static BuildingProfile.Part match(BuildingProfile from, BuildingProfile.Part t) {
        BuildingProfile.Part same = from.part(t.key());
        if (same != null) return same;
        for (BuildingProfile.Part p : from.parts()) if (p.label().equalsIgnoreCase(t.label())) return p;
        String role = role(t.key());
        for (BuildingProfile.Part p : from.parts()) if (role(p.key()).equals(role)) return p;
        return null;
    }

    /**
     * {@code toLook} on a {@code to} with {@code fromLook}'s colours and
     * patterns (on a {@code from}) carried over part by part, as
     * {@link #match} pairs them; the parts with no match keep theirs.
     */
    public static BuildingLook transfer(BuildingProfile from, BuildingLook fromLook, BuildingProfile to,
                                        BuildingLook toLook) {
        BuildingLook out = toLook.copy();
        for (BuildingProfile.Part t : to.parts()) {
            BuildingProfile.Part s = match(from, t);
            if (s == null) continue;
            out.setColour(t.key(), fromLook.colour(s));
            String tex = fromLook.texture(s);
            if (tex != null) out.setTexture(t.key(), tex);
        }
        return out;
    }

    /** How many of {@code to}'s parts {@link #transfer} gives a look from {@code from}. */
    public static int matches(BuildingProfile from, BuildingProfile to) {
        int n = 0;
        for (BuildingProfile.Part t : to.parts()) if (match(from, t) != null) n++;
        return n;
    }

    // --- the ramp ----------------------------------------------------------------------

    /** Steps of lightness (OKLab L) from a colour: line, deep shadow, shadow, the colour, light, highlight. */
    static final double[] RAMP_L = {-0.36, -0.22, -0.11, 0.0, 0.08, 0.15};
    /** Shadows a touch bluer, lights a touch warmer (OKLab b). */
    static final double[] RAMP_B = {-0.015, -0.012, -0.006, 0.0, 0.006, 0.01};
    /** Each step at least this much darker (or lighter) than the one before it. */
    static final double RAMP_GAP = 0.025;
    private static final double HUE_WEIGHT = 2.0;

    /**
     * Six of {@code palette}'s colours for {@code rgb}, dark to light - its
     * line, deep shadow, shadow, itself, light and highlight - each the
     * nearest to a step of lightness from it, every one darker (or lighter)
     * than the one before it, so no two are the same. As 3D-Modeling's
     * {@code buildings.py} previews them.
     */
    public static int[] ramp(int rgb, PixelPalette palette) {
        int[] fills = palette.fills(), lines = palette.lines();
        double[][] F = new double[fills.length][], L = new double[lines.length][];
        for (int i = 0; i < fills.length; i++) F[i] = Variants.Oklab.of(fills[i]);
        for (int i = 0; i < lines.length; i++) L[i] = Variants.Oklab.of(lines[i]);
        double[] base = Variants.Oklab.of(rgb & 0xFFFFFF);
        int[] out = new int[6];
        double[] lab = new double[6];
        // the colour itself in the middle - just the one picked, if it is one of the palette's (a line dark
        // too), so what is picked is what is drawn; any other, the nearest of the fills
        int exact = indexOf(fills, rgb);
        int exactLine = exact >= 0 ? -1 : indexOf(lines, rgb);
        if (exact >= 0 || exactLine >= 0) {
            out[3] = rgb & 0xFFFFFF;
            lab[3] = exact >= 0 ? F[exact][0] : L[exactLine][0];
        } else {
            int m = nearest(target(base, 3), F, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            out[3] = fills[m];
            lab[3] = F[m][0];
        }
        for (int i = 2; i >= 1; i--) {
            // darker fills - or, for a colour as dark as the fills go, darker lines
            int k = nearest(target(base, i), F, Double.NEGATIVE_INFINITY, lab[i + 1] - RAMP_GAP);
            if (k >= 0) {
                out[i] = fills[k];
                lab[i] = F[k][0];
            } else {
                int j = nearest(target(base, i), L, Double.NEGATIVE_INFINITY, lab[i + 1] - RAMP_GAP);
                out[i] = j < 0 ? out[i + 1] : lines[j];
                lab[i] = j < 0 ? lab[i + 1] : L[j][0];
            }
        }
        int k = nearest(target(base, 0), L, Double.NEGATIVE_INFINITY, lab[1] - RAMP_GAP);
        if (k < 0) k = nearest(target(base, 0), L, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        out[0] = lines[k];
        lab[0] = L[k][0];
        for (int i = 4; i <= 5; i++) {
            int j = nearest(target(base, i), F, lab[i - 1] + RAMP_GAP, Double.POSITIVE_INFINITY);
            out[i] = j < 0 ? out[i - 1] : fills[j];
            lab[i] = j < 0 ? lab[i - 1] : F[j][0];
        }
        return out;
    }

    private static int indexOf(int[] colours, int rgb) {
        for (int i = 0; i < colours.length; i++) if ((colours[i] & 0xFFFFFF) == (rgb & 0xFFFFFF)) return i;
        return -1;
    }

    private static double[] target(double[] base, int i) {
        return new double[]{Math.min(0.97, Math.max(0.1, base[0] + RAMP_L[i])), base[1], base[2] + RAMP_B[i]};
    }

    /** The nearest of {@code from} to {@code lab} with its lightness above {@code lo} and below {@code hi}; -1: none. */
    private static int nearest(double[] lab, double[][] from, double lo, double hi) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < from.length; i++) {
            if (from[i][0] <= lo || from[i][0] >= hi) continue;
            double dl = lab[0] - from[i][0], da = lab[1] - from[i][1], db = lab[2] - from[i][2];
            double d = dl * dl + HUE_WEIGHT * (da * da + db * db);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    // --- colours as text ---------------------------------------------------------------

    /** {@code #rrggbb} (or {@code rrggbb}) as {@code 0xRRGGBB}; {@code fallback} when it is not one. */
    public static int parseColour(String s, int fallback) {
        if (s == null) return fallback;
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) return fallback;
        try {
            return Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }
}
