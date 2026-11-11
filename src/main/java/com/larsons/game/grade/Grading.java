package com.larsons.game.grade;

import com.larsons.game.sprite.Variants.Oklab;
import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The colour grade: what the art's 128 colours are shown as. The game's
 * pixel art is drawn in a palette of 128 ({@code pixel_palette.json}); here
 * each of them can be given another colour, all of them shifted together
 * (hue, saturation, brightness, contrast, or all made one hue), and the 128
 * brought down to as few as two.
 *
 * <ul>
 *   <li>{@link #set} changes one colour; {@link #apply} puts a {@link
 *       Preset} on (Gameboy, N64, one colour ...);</li>
 *   <li>{@link #shown} is what each of the 128 comes to: its own colour,
 *       shifted with the rest, and - when fewer than 128 are asked for -
 *       merged with its nearest neighbours until that many are left;</li>
 *   <li>{@link #lut} is the whole grade as a cube of colours the picture is
 *       looked up in ({@code gfx/GradeFilter}): each colour on the screen
 *       becomes what its nearest palette colour is shown as (<b>snapped</b>:
 *       the picture drawn in the palette and nothing else), or is moved the
 *       way its nearest palette colours were moved (<b>smooth</b>: the
 *       light and the mist keep their shades).</li>
 * </ul>
 *
 * Nothing changed is {@link #identity}: the picture is shown as drawn, at no
 * cost. Kept in {@code config/colours.json}.
 */
public final class Grading {

    /** The fewest colours the palette can be brought down to. */
    public static final int FEWEST = 2;

    private final int[] original;
    private final double[][] originalLab;
    private final int[] edited;
    private int count;
    private boolean snap, colourise;
    private double hue, saturation = 1, brightness = 1, contrast = 1;
    private String preset = Preset.ORIGINAL.key();
    private long version;
    private int[] shown;
    private long shownAt = -1;
    /** The nearest palette colour to each cell of a cube {@code nearestSize} a side (snapped). */
    private byte[] nearest;
    private int nearestSize;
    /** The four nearest and how much each counts, for a cube {@code softSize} a side (smooth). */
    private byte[] softIndex;
    private float[] softWeight;
    private int softSize;

    public Grading(int[] palette) {
        original = palette.clone();
        edited = palette.clone();
        count = palette.length;
        originalLab = new double[palette.length][];
        for (int i = 0; i < palette.length; i++) originalLab[i] = Oklab.of(palette[i]);
    }

    /** How many colours the palette has (128). */
    public int size() { return original.length; }

    public int original(int i) { return original[i]; }

    /** What the player made colour {@code i} (before the shifts and the merging). */
    public int edited(int i) { return edited[i]; }

    public void set(int i, int rgb) {
        if (edited[i] == (rgb & 0xFFFFFF)) return;
        edited[i] = rgb & 0xFFFFFF;
        preset = "custom";
        version++;
    }

    public void reset(int i) {
        set(i, original[i]);
    }

    public int count() { return count; }

    /** Bring the palette down to {@code n} colours ({@link #FEWEST} to all of them). */
    public void setCount(int n) {
        n = Math.max(FEWEST, Math.min(original.length, n));
        if (n == count) return;
        count = n;
        version++;
    }

    /** Whether every pixel is drawn in the palette's colours and no other (always, with fewer than all of them). */
    public boolean snap() { return snap || count < original.length; }

    public void setSnap(boolean on) {
        if (snap == on) return;
        snap = on;
        version++;
    }

    public double hue() { return hue; }

    public double saturation() { return saturation; }

    public double brightness() { return brightness; }

    public double contrast() { return contrast; }

    public boolean colourise() { return colourise; }

    /**
     * Shift every colour together: its hue turned {@code hue} degrees (or,
     * {@code colourise}, every colour given that hue), its saturation,
     * brightness and contrast times these (1: as it is).
     */
    public void shift(double hue, double saturation, double brightness, double contrast, boolean colourise) {
        if (hue == this.hue && saturation == this.saturation && brightness == this.brightness
                && contrast == this.contrast && colourise == this.colourise) return;
        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
        this.contrast = contrast;
        this.colourise = colourise;
        version++;
    }

    /** The preset that is on ({@link Preset#key}), or "custom" once anything of it was changed. */
    public String preset() { return preset; }

    /** Counts up with every change: when the cube is to be made again. */
    public long version() { return version; }

    /** Everything as the art was drawn. */
    public void resetAll() {
        System.arraycopy(original, 0, edited, 0, original.length);
        count = original.length;
        snap = colourise = false;
        hue = 0;
        saturation = brightness = contrast = 1;
        preset = Preset.ORIGINAL.key();
        version++;
    }

    /** Put {@code p} on, in place of whatever was. */
    public void apply(Preset p) {
        resetAll();
        p.apply(this);
        preset = p.key();
        version++;
    }

    /** The palette as given, for a preset to work from. */
    int[] originals() { return original; }

    /** For a preset: colour {@code i} without unmarking the preset. */
    void put(int i, int rgb) {
        edited[i] = rgb & 0xFFFFFF;
    }

    void putSnap(boolean on) { snap = on; }

    void putCount(int n) { count = Math.max(FEWEST, Math.min(original.length, n)); }

    void putShift(double hue, double saturation, double brightness, double contrast, boolean colourise) {
        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
        this.contrast = contrast;
        this.colourise = colourise;
    }

    /** Whether the picture is shown as it was drawn (nothing to do). */
    public boolean identity() {
        if (count < original.length || snap || colourise) return false;
        if (hue != 0 || saturation != 1 || brightness != 1 || contrast != 1) return false;
        for (int i = 0; i < original.length; i++) if (edited[i] != original[i]) return false;
        return true;
    }

    // --- what each colour comes to ------------------------------------------------------

    /** What each of the 128 is shown as: edited, shifted with the rest, merged down to {@link #count}. */
    public int[] shown() {
        if (shown != null && shownAt == version) return shown;
        int n = original.length;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = shifted(edited[i]);
        if (count < n) out = merged(out, count);
        shown = out;
        shownAt = version;
        return out;
    }

    /** {@code rgb} with the shifts every colour gets. */
    public int shifted(int rgb) {
        if (hue == 0 && saturation == 1 && brightness == 1 && contrast == 1 && !colourise) return rgb;
        float[] hsv = Colours.hsv(rgb);
        double h = colourise ? ((hue % 360) + 360) % 360 : (((hsv[0] + hue) % 360) + 360) % 360;
        double s = colourise ? Math.min(1, 0.55 * saturation) : Math.min(1, hsv[1] * saturation);
        double v = hsv[2];
        if (colourise) {
            // every colour the one hue, as light as it was (by its lightness, not its brightest channel)
            double light = Oklab.of(rgb)[0];
            v = Math.min(1, light * light * (0.6 + 0.6 * light) + 0.25 * light);
        }
        v = Math.max(0, Math.min(1, v * brightness));
        int c = Colours.rgb(h, s, v);
        if (contrast != 1) {
            int r = con((c >> 16) & 255), g = con((c >> 8) & 255), b = con(c & 255);
            c = (r << 16) | (g << 8) | b;
        }
        return c;
    }

    private int con(int v) {
        return (int) Math.max(0, Math.min(255, Math.round((v - 127.5) * contrast + 127.5)));
    }

    /**
     * {@code colours} brought down to {@code n}: the two nearest (in OKLab)
     * merged into their mean, again and again, until {@code n} are left;
     * each colour then shown as the mean of the group it ended in.
     */
    static int[] merged(int[] colours, int n) {
        int size = colours.length;
        double[][] lab = new double[size][];
        double[] weight = new double[size];
        int[] group = new int[size];
        boolean[] gone = new boolean[size];
        for (int i = 0; i < size; i++) {
            lab[i] = Oklab.of(colours[i]);
            weight[i] = 1;
            group[i] = i;
        }
        // (colours that are already the same are one group to begin with)
        int left = size;
        for (int i = 0; i < size; i++) {
            if (gone[i]) continue;
            for (int j = i + 1; j < size; j++) {
                if (!gone[j] && colours[j] == colours[i]) {
                    gone[j] = true;
                    weight[i] += 1;
                    for (int k = 0; k < size; k++) if (group[k] == j) group[k] = i;
                    left--;
                }
            }
        }
        while (left > n) {
            int a = -1, b = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < size; i++) {
                if (gone[i]) continue;
                for (int j = i + 1; j < size; j++) {
                    if (gone[j]) continue;
                    double dl = lab[i][0] - lab[j][0], da = lab[i][1] - lab[j][1], db = lab[i][2] - lab[j][2];
                    // (a small group is merged sooner than a large one, so no colour swallows the palette)
                    double d = (dl * dl + da * da + db * db) * (weight[i] * weight[j]) / (weight[i] + weight[j]);
                    if (d < best) {
                        best = d;
                        a = i;
                        b = j;
                    }
                }
            }
            double w = weight[a] + weight[b];
            for (int k = 0; k < 3; k++) lab[a][k] = (lab[a][k] * weight[a] + lab[b][k] * weight[b]) / w;
            weight[a] = w;
            gone[b] = true;
            for (int k = 0; k < size; k++) if (group[k] == b) group[k] = a;
            left--;
        }
        int[] out = new int[size];
        for (int i = 0; i < size; i++) {
            double[] l = lab[group[i]];
            out[i] = Oklab.rgb(l[0], l[1], l[2]);
        }
        return out;
    }

    /** How many different colours {@link #shown} has. */
    public int distinct() {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int c : shown()) seen.add(c);
        return seen.size();
    }

    // --- the cube --------------------------------------------------------------------

    /**
     * The grade as a cube: {@code size} cells a side, red fastest then green
     * then blue, three bytes a cell. {@code linear}: read between its cells.
     */
    public record Lut(int size, boolean linear, byte[] rgb) {}

    /** The side of the cube when the picture is snapped to the palette, and when it is moved smoothly. */
    public static final int SNAP_SIZE = 64, SMOOTH_SIZE = 32;

    public Lut lut() {
        return snap() ? snapped(SNAP_SIZE) : smooth(SMOOTH_SIZE);
    }

    /** Each cell: what its nearest palette colour is shown as. */
    Lut snapped(int size) {
        int[] to = shown();
        if (nearest == null || nearestSize != size) {
            nearest = new byte[size * size * size];
            nearestSize = size;
            double[][] cell = cells(size, false);
            for (int c = 0; c < nearest.length; c++) nearest[c] = (byte) nearestTo(cell[c]);
            // (a palette colour itself is always its own cell's)
            for (int i = 0; i < original.length; i++) nearest[cellOf(original[i], size)] = (byte) i;
        }
        byte[] rgb = new byte[size * size * size * 3];
        for (int c = 0; c < nearest.length; c++) {
            int v = to[nearest[c] & 0xFF];
            rgb[c * 3] = (byte) (v >> 16);
            rgb[c * 3 + 1] = (byte) (v >> 8);
            rgb[c * 3 + 2] = (byte) v;
        }
        return new Lut(size, false, rgb);
    }

    /**
     * Each cell: its own colour, moved as its four nearest palette colours
     * were each changed (the nearer, the more), then shifted as every colour is.
     */
    Lut smooth(int size) {
        int[] to = edited;
        int cells = size * size * size;
        if (softIndex == null || softSize != size) {
            softIndex = new byte[cells * 4];
            softWeight = new float[cells * 4];
            softSize = size;
            double[][] cell = cells(size, true);
            for (int c = 0; c < cells; c++) {
                int[] best = {-1, -1, -1, -1};
                double[] dist = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
                double[] l = cell[c];
                for (int i = 0; i < originalLab.length; i++) {
                    double[] p = originalLab[i];
                    double d = sq(l[0] - p[0]) + sq(l[1] - p[1]) + sq(l[2] - p[2]);
                    for (int k = 0; k < 4; k++) {
                        if (d < dist[k]) {
                            for (int m = 3; m > k; m--) {
                                dist[m] = dist[m - 1];
                                best[m] = best[m - 1];
                            }
                            dist[k] = d;
                            best[k] = i;
                            break;
                        }
                    }
                }
                double sum = 0;
                double[] w = new double[4];
                for (int k = 0; k < 4; k++) {
                    w[k] = 1 / (dist[k] * dist[k] + 1e-7);
                    sum += w[k];
                }
                for (int k = 0; k < 4; k++) {
                    softIndex[c * 4 + k] = (byte) best[k];
                    softWeight[c * 4 + k] = (float) (w[k] / sum);
                }
            }
        }
        byte[] rgb = new byte[cells * 3];
        int c = 0;
        for (int b = 0; b < size; b++) {
            for (int g = 0; g < size; g++) {
                for (int r = 0; r < size; r++, c++) {
                    double dr = 0, dg = 0, db = 0;
                    for (int k = 0; k < 4; k++) {
                        int i = softIndex[c * 4 + k] & 0xFF;
                        float w = softWeight[c * 4 + k];
                        int from = original[i], now = to[i];
                        dr += w * (((now >> 16) & 255) - ((from >> 16) & 255));
                        dg += w * (((now >> 8) & 255) - ((from >> 8) & 255));
                        db += w * ((now & 255) - (from & 255));
                    }
                    int v = shifted(((clamp(r * 255.0 / (size - 1) + dr) & 255) << 16)
                            | ((clamp(g * 255.0 / (size - 1) + dg) & 255) << 8)
                            | (clamp(b * 255.0 / (size - 1) + db) & 255));
                    rgb[c * 3] = (byte) (v >> 16);
                    rgb[c * 3 + 1] = (byte) (v >> 8);
                    rgb[c * 3 + 2] = (byte) v;
                }
            }
        }
        return new Lut(size, true, rgb);
    }

    private static byte clamp(double v) {
        return (byte) Math.max(0, Math.min(255, Math.round(v)));
    }

    private static double sq(double v) {
        return v * v;
    }

    private int nearestTo(double[] lab) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < originalLab.length; i++) {
            double[] p = originalLab[i];
            double d = sq(lab[0] - p[0]) + sq(lab[1] - p[1]) + sq(lab[2] - p[2]);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** The cell of a cube {@code size} a side that {@code rgb} is read from (not between cells). */
    static int cellOf(int rgb, int size) {
        int r = Math.min(size - 1, ((rgb >> 16) & 255) * size / 256);
        int g = Math.min(size - 1, ((rgb >> 8) & 255) * size / 256);
        int b = Math.min(size - 1, (rgb & 255) * size / 256);
        return (b * size + g) * size + r;
    }

    /** Each cell's colour in OKLab: its middle (read as it is) or its corner value (read between). */
    private static double[][] cells(int size, boolean linear) {
        double[][] out = new double[size * size * size][];
        int c = 0;
        for (int b = 0; b < size; b++) {
            for (int g = 0; g < size; g++) {
                for (int r = 0; r < size; r++, c++) {
                    int R = level(r, size, linear), G = level(g, size, linear), B = level(b, size, linear);
                    out[c] = Oklab.of((R << 16) | (G << 8) | B);
                }
            }
        }
        return out;
    }

    private static int level(int i, int size, boolean linear) {
        return (int) Math.round(linear ? i * 255.0 / (size - 1) : (i + 0.5) * 255.0 / size);
    }

    /** What a colour on the screen becomes (as the cube has it): for the menu's preview, and the tests. */
    public int graded(int rgb) {
        Lut l = lut();
        if (!l.linear()) {
            int c = cellOf(rgb, l.size()) * 3;
            return ((l.rgb()[c] & 255) << 16) | ((l.rgb()[c + 1] & 255) << 8) | (l.rgb()[c + 2] & 255);
        }
        int s = l.size();
        int r = Math.round(((rgb >> 16) & 255) * (s - 1) / 255f), g = Math.round(((rgb >> 8) & 255) * (s - 1) / 255f),
                b = Math.round((rgb & 255) * (s - 1) / 255f);
        int c = ((b * s + g) * s + r) * 3;
        return ((l.rgb()[c] & 255) << 16) | ((l.rgb()[c + 1] & 255) << 8) | (l.rgb()[c + 2] & 255);
    }

    // --- the file ----------------------------------------------------------------------

    public Map<String, Object> json() {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("preset", preset);
        j.put("count", (double) count);
        j.put("snap", snap);
        j.put("hue", hue);
        j.put("saturation", saturation);
        j.put("brightness", brightness);
        j.put("contrast", contrast);
        j.put("colourise", colourise);
        Map<String, Object> changed = new LinkedHashMap<>();
        for (int i = 0; i < original.length; i++) {
            if (edited[i] != original[i]) changed.put(String.valueOf(i), String.format(Locale.ROOT, "#%06x", edited[i]));
        }
        j.put("colours", changed);
        return j;
    }

    /** Take up what {@link #json} wrote. */
    public void read(Map<String, Object> j) {
        resetAll();
        if (j == null) return;
        count = Math.max(FEWEST, Math.min(original.length, (int) Json.num(j, "count", original.length)));
        snap = Json.bool(j, "snap", false);
        hue = Json.num(j, "hue", 0);
        saturation = Json.num(j, "saturation", 1);
        brightness = Json.num(j, "brightness", 1);
        contrast = Json.num(j, "contrast", 1);
        colourise = Json.bool(j, "colourise", false);
        Map<String, Object> changed = Json.obj(j, "colours");
        if (changed != null) {
            for (Map.Entry<String, Object> e : changed.entrySet()) {
                try {
                    int i = Integer.parseInt(e.getKey());
                    String v = String.valueOf(e.getValue());
                    if (i >= 0 && i < original.length) edited[i] = Integer.parseInt(v.replace("#", ""), 16) & 0xFFFFFF;
                } catch (NumberFormatException ignored) {
                    // (a line that is not a colour is not taken)
                }
            }
        }
        preset = Json.str(j, "preset", "custom");
        version++;
    }

    public void load(Path file) {
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            read(Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8))));
        } catch (IOException | RuntimeException e) {
            System.err.println("[colours] ignoring " + file + ": " + e.getMessage());
        }
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, Json.stringify(json()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[colours] cannot save: " + e.getMessage());
        }
    }

    /** The palette's colours in the order the menu shows them: by hue, the greys first, each hue dark to light. */
    public List<Integer> order() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < original.length; i++) out.add(i);
        out.sort((a, b) -> {
            float[] x = Colours.hsv(original[a]), y = Colours.hsv(original[b]);
            int bx = x[1] < 0.16f ? -1 : (int) (x[0] / 30), by = y[1] < 0.16f ? -1 : (int) (y[0] / 30);
            if (bx != by) return Integer.compare(bx, by);
            return Double.compare(originalLab[a][0], originalLab[b][0]);
        });
        return out;
    }
}
