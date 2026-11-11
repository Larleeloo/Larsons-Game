package com.larsons.game.overworld;

import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Texture;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * An overworld map drawn: its tiles ({@code assets/overworld/tiles.png}, a
 * row a terrain, four looks of each - the water's four are its waves), the
 * shore where land meets water, the ways as dotted roads and the
 * destinations' icons ({@code icons.png}). The map scene and the map editor
 * both draw with it: a tile at {@code (tx, ty)} lies at {@code (ox + tx * s,
 * oy + ty * s)} on the screen, {@code s} pixels across.
 */
public final class MapView {

    /** A tile's side and an icon's side in the pictures, pixels; how many looks a terrain has. */
    public static final int TILE = 16, ICON = 24, VARIANTS = 4;

    private static final float[] WHITE = {1, 1, 1, 1};
    private static final float[] FOAM = Theme.hex(0xDDEBF0, 0.55f), SHORE = Theme.hex(0x10202A, 0.35f);
    private static final float[] ROAD = Theme.hex(0xE8D3A0, 1f), ROAD_EDGE = Theme.hex(0x3A2A1A, 0.9f);
    private static final float[] ROAD_SHUT = Theme.hex(0x8A8A8A, 0.75f);

    private static Path folder = Path.of("assets", "overworld");
    private static Texture tiles, icons;
    private static boolean tried;

    private MapView() {}

    /** Read the pictures from {@code assets/overworld} under {@code assets} from now on. */
    public static void useAssets(Path assets) {
        folder = assets.resolve(Overworld.FOLDER);
    }

    private static void load() {
        if (tried) return;
        tried = true;
        try {
            tiles = read(folder.resolve("tiles.png"));
            icons = read(folder.resolve("icons.png"));
        } catch (IOException | RuntimeException e) {
            System.err.println("[overworld] no map pictures: " + e.getMessage());
        }
    }

    private static Texture read(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("cannot read " + file);
        return Texture.upload(PixelData.of(image), false);
    }

    /** Which of a terrain's looks the tile at {@code (x, y)} has. */
    static int variant(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h ^= h >>> 13;
        return Math.floorMod(h * 0x5bd1e995, VARIANTS);
    }

    /** One tile of {@code t}, look {@code variant}, over the square at {@code (x, y)}. */
    public static void tile(Ui ui, Overworld.Terrain t, int variant, float x, float y, float size) {
        load();
        if (tiles == null) {
            ui.rect(x, y, size, size, t.water() ? Theme.hex(0x2A5A8A, 1f) : Theme.hex(0x4A7A3A, 1f));
            return;
        }
        float tw = tiles.width(), th = tiles.height();
        // a hair inside the tile, so a neighbour's colour never bleeds in at the seams
        float e = 0.02f;
        float u0 = (variant * TILE + e) / tw, v0 = (Overworld.row(t) * TILE + e) / th;
        float u1 = ((variant + 1) * TILE - e) / tw, v1 = ((Overworld.row(t) + 1) * TILE - e) / th;
        ui.image(tiles, x, y, size, size, u0, v0, u1, v1, WHITE);
    }

    /** The map's tiles, those on screen, with foam and a shadow where land meets water. */
    public static void terrain(Ui ui, Overworld map, float ox, float oy, float s, double time) {
        int x0 = Math.max(0, (int) Math.floor(-ox / s)), y0 = Math.max(0, (int) Math.floor(-oy / s));
        int x1 = Math.min(map.width() - 1, (int) Math.floor((ui.width() - ox) / s));
        int y1 = Math.min(map.height() - 1, (int) Math.floor((ui.height() - oy) / s));
        int wave = (int) Math.floor(time * 1.6);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                Overworld.Terrain t = map.terrain(x, y);
                int v = t.water() ? Math.floorMod(variant(x, y) + wave, VARIANTS) : variant(x, y);
                // one pixel wider than the tile: no gaps between tiles at any scale
                tile(ui, t, v, ox + x * s, oy + y * s, s + 0.5f);
            }
        }
        float edge = Math.max(2, s / 8f);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                if (!map.terrain(x, y).water()) continue;
                float px = ox + x * s, py = oy + y * s;
                if (land(map, x, y - 1)) {
                    ui.rect(px, py, s, edge * 1.6f, SHORE);
                    ui.rect(px, py + edge * 1.6f, s, edge * 0.6f, FOAM);
                }
                if (land(map, x, y + 1)) ui.rect(px, py + s - edge * 0.7f, s, edge * 0.7f, FOAM);
                if (land(map, x - 1, y)) ui.rect(px, py, edge * 0.7f, s, FOAM);
                if (land(map, x + 1, y)) ui.rect(px + s - edge * 0.7f, py, edge * 0.7f, s, FOAM);
            }
        }
    }

    private static boolean land(Overworld map, int x, int y) {
        return map.inside(x, y) && !map.terrain(x, y).water();
    }

    /** Every way, as a dotted road: sandy where {@code open} says she may walk it, grey where not. */
    public static void ways(Ui ui, Overworld map, float ox, float oy, float s, Predicate<Overworld.Way> open) {
        for (Overworld.Way p : map.ways()) way(ui, map.points(p, p.from), ox, oy, s, open == null || open.test(p));
    }

    /** One way through {@code points} (tiles), dotted. */
    public static void way(Ui ui, List<float[]> points, float ox, float oy, float s, boolean open) {
        float dot = Math.max(4, s * 0.2f), step = s * 0.42f;
        float carry = 0;
        for (int i = 0; i + 1 < points.size(); i++) {
            float ax = ox + points.get(i)[0] * s, ay = oy + points.get(i)[1] * s;
            float bx = ox + points.get(i + 1)[0] * s, by = oy + points.get(i + 1)[1] * s;
            float len = (float) Math.hypot(bx - ax, by - ay);
            if (len < 1e-3f) continue;
            float d = carry;
            for (; d <= len; d += step) {
                float x = ax + (bx - ax) * d / len, y = ay + (by - ay) * d / len;
                ui.rect(x - dot / 2 - 1, y - dot / 2 - 1, dot + 2, dot + 2, ROAD_EDGE);
                ui.rect(x - dot / 2, y - dot / 2, dot, dot, open ? ROAD : ROAD_SHUT);
            }
            carry = d - len;
        }
    }

    /** Icon {@code index} of {@code icons.png}, {@code size} across, its middle at {@code (cx, cy)}. */
    public static void icon(Ui ui, int index, float cx, float cy, float size, float alpha) {
        load();
        if (icons == null) {
            ui.disc(cx, cy, size / 3, Theme.withAlpha(Theme.BRASS, alpha));
            return;
        }
        float tw = icons.width();
        ui.image(icons, cx - size / 2, cy - size / 2, size, size, index * ICON / tw, 0, (index + 1) * ICON / tw, 1,
                alpha >= 1 ? WHITE : new float[]{1, 1, 1, alpha});
    }

    /**
     * A destination's icon on its tile, a little above the tile's middle (it
     * stands on the road), with a mark over it: cleared, or locked.
     */
    public static void node(Ui ui, Overworld.Node n, float ox, float oy, float s, boolean cleared, boolean locked) {
        float cx = ox + (n.x + 0.5f) * s, cy = oy + (n.y + 0.5f) * s;
        float size = s * 1.5f;
        ui.disc(cx, cy + s * 0.18f, s * 0.42f, Theme.hex(0x000000, 0.28f));
        icon(ui, n.icon(), cx, cy - s * 0.18f, size, locked ? 0.7f : 1f);
        if (cleared) icon(ui, Overworld.ICON_CLEARED, cx + s * 0.5f, cy - s * 0.7f, s * 0.75f, 1f);
        else if (locked) icon(ui, Overworld.ICON_LOCKED, cx + s * 0.5f, cy - s * 0.7f, s * 0.75f, 1f);
    }

    /** Let the pictures go. */
    public static void dispose() {
        if (tiles != null) tiles.close();
        if (icons != null) icons.close();
        tiles = icons = null;
        tried = false;
    }
}
