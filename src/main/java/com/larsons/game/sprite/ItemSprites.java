package com.larsons.game.sprite;

import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Texture;
import com.larsons.game.util.Json;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The items' turning sprites: each weapon drawn as 128-pixel pixel art that
 * turns once round in place (3D-Modeling's {@code items/item_spin_sprites.py})
 * - how an item lying in the world is drawn, and its icon in the inventory.
 *
 * <pre>
 *   assets/sprites/items/&lt;id&gt;/spin_px128/profile.json     128 x 128 frames, frameWorldSize, fps, length
 *   assets/sprites/items/&lt;id&gt;/spin_px128/spin_side.png    32 frames (8 x 4): upright, turning once round
 *   assets/sprites/items/&lt;id&gt;/spin_px128/spin_middle.png  ... from 45 degrees up
 *   assets/sprites/items/&lt;id&gt;/spin_px128/spin_top.png     ... lying flat, from above
 *   assets/sprites/items/&lt;id&gt;/spin_px128/parts.json       which of its materials is which part
 * </pre>
 *
 * <p>The sheets are palette PNGs like the character's pixel art, loaded
 * through the {@link SpriteLibrary} like any other ({@link #resolve}) and
 * drawn in a weapon tier's colours by a palette swap ({@link #palette},
 * {@link TierLook}). The item's centre is the frame's centre.
 *
 * <p>The inventory's icons ({@link #icon}) are made here instead, on the CPU:
 * an icon is a third or a half of a frame across, and palette indices cannot
 * be filtered, so each frame of the top view - the item lying flat, turning
 * like a record - is reduced as pixel art to {@value #ICON} pixels in its
 * tier's colours, with a soft halo and a crisp ring round its outline beside it
 * for the rarity's glow and a rare border to be drawn in.
 */
public final class ItemSprites implements AutoCloseable {

    /** The folder of an item's turning sprites, under its own. */
    public static final String FOLDER = "spin_px128";
    /** An icon frame's size, in pixels. */
    public static final int ICON = 64;
    /** The frame of the top view an icon rests on: the item lying corner to corner, point or head up and right. */
    public static final int ICON_REST_FRAME = 4;
    /** A material's outer line, of its six labels: what an icon keeps on its edge. */
    private static final int OUTLINE = 5;
    /** Icons kept before the least recently drawn is let go. */
    private static final int ICONS = 48;

    /**
     * One item's turning sprites.
     *
     * @param length how long the item is, in metres (its frame is {@code profile.frameWorldSize()} across)
     * @param parts  which of its sheets' materials is which part ({@link TierLook})
     */
    public record Spin(String id, SpriteProfile profile, Map<Elevation, Path> sheets, Map<String, int[]> parts,
                       double length, int frames) {

        /** The sheet seen from {@code e}, or the nearest height that has one; null with none at all. */
        public Path sheet(Elevation e) {
            Path p = sheets.get(e);
            if (p != null) return p;
            if (e == Elevation.TOP) return sheets.getOrDefault(Elevation.MIDDLE, sheets.get(Elevation.SIDE));
            return sheets.getOrDefault(Elevation.SIDE, sheets.get(Elevation.MIDDLE));
        }
    }

    /**
     * An item's icon in one tier, on the GPU: its frames ({@link #uv}) in
     * three textures laid out alike - the item, a soft white halo round it
     * and a crisp white ring round its outline, to be tinted.
     */
    public record Icon(Texture sprite, Texture halo, Texture ring, int frames, int columns, int rows)
            implements AutoCloseable {

        /** Texture coordinates of {@code frame} (any whole number: it wraps): {@code {u0, v0, u1, v1}}. */
        public float[] uv(int frame) {
            int f = Math.floorMod(frame, Math.max(1, frames));
            float w = columns * ICON, h = rows * ICON;
            int c = f % columns, r = f / columns;
            return new float[]{(c * ICON + 0.5f) / w, (r * ICON + 0.5f) / h,
                    ((c + 1) * ICON - 0.5f) / w, ((r + 1) * ICON - 0.5f) / h};
        }

        @Override
        public void close() {
            sprite.close();
            halo.close();
            ring.close();
        }
    }

    /**
     * An icon's pixels, before upload: straight ARGB atlases of {@code
     * columns} by {@code rows} frames of {@value #ICON} pixels.
     */
    public record IconPixels(int[] sprite, int[] halo, int[] ring, int width, int height, int frames, int columns) {}

    private final Path root;
    private final PixelPalette pixelPalette;
    private final TierLook tiers;
    private volatile Map<String, Spin> spins = Map.of();
    private final Map<String, Icon> icons = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Boolean> noIcon = new java.util.HashMap<>();
    private final Map<int[], int[]> silhouettes = new IdentityHashMap<>();

    /** The items under {@code <sprites>/items/}, in the colours of {@code <sprites>/tiers.json}. */
    public ItemSprites(Path spritesRoot) {
        this.root = spritesRoot.resolve(SpriteLibrary.ITEMS_FOLDER);
        this.pixelPalette = PixelPalette.load(spritesRoot);
        this.tiers = TierLook.load(spritesRoot, pixelPalette);
        rescan();
    }

    /** The weapon tiers' looks. */
    public TierLook tiers() { return tiers; }

    /** The 128 colours, or null when {@code pixel_palette.json} is missing. */
    public PixelPalette pixelPalette() { return pixelPalette; }

    /** Re-read the folder (file names and the small JSON files only) and let the icons go. */
    public synchronized void rescan() {
        Map<String, Spin> next = new TreeMap<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> dirs = Files.list(root)) {
                for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                    Spin s = scan(dir.getFileName().toString(), dir.resolve(FOLDER));
                    if (s != null) next.put(s.id(), s);
                }
            } catch (IOException e) {
                System.err.println("[sprites] cannot list " + root + ": " + e.getMessage());
            }
        }
        spins = Collections.unmodifiableMap(next);
        for (Icon i : icons.values()) if (i != null) i.close();
        icons.clear();
        noIcon.clear();
    }

    static Spin scan(String id, Path dir) {
        if (!Files.isDirectory(dir)) return null;
        Map<Elevation, Path> sheets = new EnumMap<>(Elevation.class);
        for (Elevation e : Elevation.values()) {
            Path p = dir.resolve("spin_" + e.key() + ".png");
            if (Files.isRegularFile(p)) sheets.put(e, p);
        }
        if (sheets.isEmpty()) return null;
        SpriteProfile profile = SpriteProfile.load(dir, SpriteProfile.defaults().withFrameSize(128, 128));
        double length = profile.frameWorldSize() / 1.18;
        int frames = 32;
        Map<String, int[]> parts = Map.of();
        try {
            Path pf = dir.resolve(SpriteProfile.FILE_NAME);
            if (Files.isRegularFile(pf)) {
                Map<String, Object> j = Json.asObject(Json.parse(Files.readString(pf, StandardCharsets.UTF_8)));
                length = Json.num(j, "length", length);
                frames = (int) Json.num(j, "frames", frames);
            }
            Path parts_ = dir.resolve("parts.json");
            if (Files.isRegularFile(parts_)) {
                Map<String, Object> j = Json.asObject(Json.parse(Files.readString(parts_, StandardCharsets.UTF_8)));
                Map<String, Object> p = Json.obj(j, "parts");
                if (p != null) parts = TierLook.parts(p);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[sprites] ignoring part of " + dir + ": " + e.getMessage());
        }
        return new Spin(id, profile, Collections.unmodifiableMap(sheets), parts, length, Math.max(1, frames));
    }

    /** {@code id}'s turning sprites, or null when it has none. */
    public Spin spin(String id) {
        return id == null ? null : spins.get(id);
    }

    /** Every item with turning sprites. */
    public Map<String, Spin> spins() { return spins; }

    /** {@code id}'s sheet seen from {@code e}, to ask the {@link SpriteLibrary} for; null when it has none. */
    public SpriteLibrary.Resolved resolve(String id, Elevation e) {
        Spin s = spin(id);
        Path p = s == null ? null : s.sheet(e);
        return p == null ? null : new SpriteLibrary.Resolved(p, false, s.profile());
    }

    /**
     * The palette {@code id}'s turning sheet {@code sheet} is drawn in for
     * {@code tier} (a {@code Tier} key; null: as drawn): its own, or its parts
     * recoloured ({@link TierLook#palette}).
     */
    public int[] palette(SheetTexture sheet, String id, String tier) {
        Spin s = spin(id);
        return tiers.palette(sheet.sharedPalette(), id, tier, s == null ? null : s.parts());
    }

    /**
     * The palette an in-hand layer of weapon {@code id} (palette {@code base})
     * is drawn in for {@code tier}: its parts by {@code tiers.json}'s {@code
     * carried} map. {@code base} itself when nothing changes.
     */
    public int[] carriedPalette(int[] base, String id, String tier) {
        return tiers.palette(base, id, tier, tiers.carried(id));
    }

    /**
     * {@code base} as a silhouette: every visible entry opaque white, the
     * transparent and the empty-frame marker transparent - a sheet drawn in it
     * is its outline filled, to be tinted (a halo, a ring round it). The same
     * array every time for the same {@code base}.
     */
    public int[] silhouette(int[] base) {
        if (base == null) return null;
        int[] s = silhouettes.get(base);
        if (s != null) return s;
        if (silhouettes.size() > 256) silhouettes.clear();
        s = silhouetteOf(base);
        silhouettes.put(base, s);
        return s;
    }

    static int[] silhouetteOf(int[] base) {
        int[] s = new int[base.length];
        for (int i = 2; i < base.length; i++) s[i] = (base[i] >>> 24) > 1 ? 0xFFFFFFFF : 0;
        return s;
    }

    // --- icons -----------------------------------------------------------------------

    /**
     * {@code id}'s icon in {@code tier} (null: as drawn), made the first time
     * it is asked for (a few milliseconds) and kept while it is drawn; null
     * when the item has no turning sprites. GL thread.
     */
    public Icon icon(String id, String tier) {
        String key = id + "/" + tier;
        Icon i = icons.get(key);
        if (i != null || noIcon.containsKey(key)) return i;
        Spin s = spin(id);
        Path p = s == null ? null : s.sheet(Elevation.TOP);
        IconPixels px = null;
        if (p != null) {
            try {
                BufferedImage img = ImageIO.read(p.toFile());
                if (img != null && img.getColorModel() instanceof IndexColorModel icm) {
                    int[] base = new int[256];
                    icm.getRGBs(base);
                    for (int e = icm.getMapSize(); e < 256; e++) base[e] = 0;
                    int[] pal = tiers.palette(base, id, tier, s.parts());
                    px = iconPixels(img, s.profile().frameWidth(), s.profile().frameHeight(), s.frames(), pal);
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("[sprites] cannot make the icon of " + id + ": " + e.getMessage());
            }
        }
        if (px == null) {
            noIcon.put(key, true);
            return null;
        }
        int rows = px.height() / ICON;
        i = new Icon(Texture.upload(PixelData.of(px.sprite(), px.width(), px.height()), false),
                Texture.upload(PixelData.of(px.halo(), px.width(), px.height()), false),
                Texture.upload(PixelData.of(px.ring(), px.width(), px.height()), false),
                px.frames(), px.columns(), rows);
        icons.put(key, i);
        if (icons.size() > ICONS) {
            Iterator<Map.Entry<String, Icon>> it = icons.entrySet().iterator();
            it.next().getValue().close();
            it.remove();
        }
        return i;
    }

    /**
     * The pixels of an icon from a palette sheet of {@code frames} frames of
     * {@code frameW} by {@code frameH} (left to right, then top to bottom), in
     * {@code palette} (straight ARGB, by index): each frame reduced to
     * {@value #ICON} pixels as pixel art is - every icon pixel one of the
     * sheet's own colours, the one most of its block is, or its outline's
     * where the block is on the item's edge, so the icon stays in the palette
     * and keeps its line art and its thinnest parts - a soft halo of its outline grown a pixel and
     * blurred, and a ring two pixels wide round its outline. Pure; any thread.
     */
    public static IconPixels iconPixels(BufferedImage sheet, int frameW, int frameH, int frames, int[] palette) {
        int sw = sheet.getWidth(), sh = sheet.getHeight();
        int cols = Math.max(1, sw / frameW);
        int count = Math.min(frames, cols * Math.max(1, sh / frameH));
        int rows = (count + cols - 1) / cols;
        int[] idx = sheet.getRaster().getPixels(0, 0, sw, sh, (int[]) null);
        int w = cols * ICON, h = rows * ICON;
        int[] sprite = new int[w * h], halo = new int[w * h], ring = new int[w * h];
        double kx = frameW / (double) ICON, ky = frameH / (double) ICON;
        float[] alpha = new float[ICON * ICON];
        for (int f = 0; f < count; f++) {
            int fx = (f % cols) * frameW, fy = (f / cols) * frameH;
            int ox = (f % cols) * ICON, oy = (f / cols) * ICON;
            for (int y = 0; y < ICON; y++) {
                for (int x = 0; x < ICON; x++) {
                    int x0 = (int) Math.floor(x * kx), x1 = Math.max(x0 + 1, (int) Math.floor((x + 1) * kx));
                    int y0 = (int) Math.floor(y * ky), y1 = Math.max(y0 + 1, (int) Math.floor((y + 1) * ky));
                    int opaque = 0, best = -1, bestCount = 0, line = -1, lineCount = 0;
                    int[] seen = new int[(x1 - x0) * (y1 - y0)], counts = new int[seen.length];
                    int kinds = 0;
                    boolean edge = false;
                    for (int yy = y0; yy < y1; yy++) {
                        for (int xx = x0; xx < x1; xx++) {
                            int sx = fx + xx, sy = fy + yy;
                            int i = sx < sw && sy < sh ? idx[sy * sw + sx] : 0;
                            int c = i < palette.length ? palette[i] : 0;
                            if ((c >>> 24) <= 1) {                  // transparent, or the empty-frame marker
                                edge = true;
                                continue;
                            }
                            opaque++;
                            int k = 0;
                            while (k < kinds && seen[k] != i) k++;
                            if (k == kinds) seen[kinds++] = i;
                            int seenTimes = ++counts[k];
                            if (seenTimes > bestCount) {
                                best = i;
                                bestCount = seenTimes;
                            }
                            if (i >= 2 && (i - 2) % TierLook.LABELS_PER_MATERIAL == OUTLINE && seenTimes > lineCount) {
                                line = i;
                                lineCount = seenTimes;
                            }
                        }
                    }
                    // a block with any of the item in it is item (so a bow's
                    // string a pixel wide stays), its outline if on its edge
                    boolean solid = opaque > 0;
                    alpha[y * ICON + x] = solid ? 1 : 0;
                    if (solid) {
                        int pick = edge && line >= 0 ? line : best;
                        sprite[(oy + y) * w + ox + x] = palette[pick] | 0xFF000000;
                    }
                }
            }
            float[] grown = blur(blur(dilate(alpha, 0.25f, 1), 2), 2);
            boolean[] inside = new boolean[ICON * ICON];
            for (int i = 0; i < inside.length; i++) inside[i] = alpha[i] >= 0.5f;
            float[] ringMask = dilate(alpha, 0.5f, 2);
            for (int y = 0; y < ICON; y++) {
                for (int x = 0; x < ICON; x++) {
                    int i = y * ICON + x, o = (oy + y) * w + ox + x;
                    int ha = Math.min(255, Math.round(grown[i] * 255));
                    halo[o] = (ha << 24) | 0xFFFFFF;
                    if (ringMask[i] > 0 && !inside[i]) ring[o] = 0xFFFFFFFF;
                }
            }
        }
        return new IconPixels(sprite, halo, ring, w, h, count, cols);
    }

    /** 1 wherever a pixel within {@code radius} (a diamond: steps across and up) is at least {@code over}. */
    private static float[] dilate(float[] a, float over, int radius) {
        float[] cur = new float[a.length];
        for (int i = 0; i < a.length; i++) cur[i] = a[i] >= over ? 1 : 0;
        for (int step = 0; step < radius; step++) {
            float[] next = cur.clone();
            for (int y = 0; y < ICON; y++) {
                for (int x = 0; x < ICON; x++) {
                    if (cur[y * ICON + x] > 0) continue;
                    if ((x > 0 && cur[y * ICON + x - 1] > 0) || (x < ICON - 1 && cur[y * ICON + x + 1] > 0)
                            || (y > 0 && cur[(y - 1) * ICON + x] > 0) || (y < ICON - 1 && cur[(y + 1) * ICON + x] > 0)) {
                        next[y * ICON + x] = 1;
                    }
                }
            }
            cur = next;
        }
        return cur;
    }

    /** A box blur of {@code radius}, across then down, within the frame. */
    private static float[] blur(float[] a, int radius) {
        float[] tmp = new float[a.length], out = new float[a.length];
        int n = 2 * radius + 1;
        for (int y = 0; y < ICON; y++) {
            for (int x = 0; x < ICON; x++) {
                float s = 0;
                for (int d = -radius; d <= radius; d++) {
                    int xx = x + d;
                    if (xx >= 0 && xx < ICON) s += a[y * ICON + xx];
                }
                tmp[y * ICON + x] = s / n;
            }
        }
        for (int y = 0; y < ICON; y++) {
            for (int x = 0; x < ICON; x++) {
                float s = 0;
                for (int d = -radius; d <= radius; d++) {
                    int yy = y + d;
                    if (yy >= 0 && yy < ICON) s += tmp[yy * ICON + x];
                }
                out[y * ICON + x] = s / n;
            }
        }
        return out;
    }

    @Override
    public void close() {
        for (Icon i : icons.values()) if (i != null) i.close();
        icons.clear();
    }
}
