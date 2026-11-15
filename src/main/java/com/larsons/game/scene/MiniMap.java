package com.larsons.game.scene;

import com.larsons.game.building.Building;
import com.larsons.game.gfx.Texture;
import com.larsons.game.math.Vec3;
import com.larsons.game.ui.CardArt;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.GroundMap;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.WaterMap;
import com.larsons.game.world.World;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The small map in the top corner: the level from above, north up, round
 * where she stands - and only what she has seen of it. The level is cut
 * into cells {@link #CELL_PX} pixels a side; a cell is seen once she has
 * come within {@link #SIGHT} metres of it, and is drawn then (the ground it
 * is made of, its water, its hills shaded, the buildings on it); the rest
 * stays dark. What she has seen is kept ({@link #encode}) - in a
 * playthrough's save, by place - so a place she comes back to is still
 * drawn as far as she knew it.
 *
 * <p>On it: she herself, the way she faces; and the places worth finding
 * ({@link Waypoint}) - those off the map pinned to its edge, the way they lie.
 */
final class MiniMap implements AutoCloseable {

    /** A place worth finding: a mission's ring, someone to talk to, a shop, the way out. */
    record Waypoint(Vec3 at, String icon, String label, boolean mission) {}

    /** How far round her she has seen, metres. */
    static final double SIGHT = 36;
    /** Pixels a side of a cell of what is seen. */
    static final int CELL_PX = 4;
    /** The most pixels a side the whole map is drawn with (a bigger level: more metres a pixel). */
    static final int MOST_PIXELS = 512;
    /** How many metres across the corner's window shows. */
    static final double VIEW = 120;
    /** How many cells are drawn a frame, at most (a place seen before is drawn again over a few frames). */
    private static final int CELLS_A_FRAME = 48;

    private static final int UNSEEN = 0x1E1812;
    /** The grass, then each ground material ({@link GroundMap#NAMES}). */
    private static final int GRASS = 0x6E9A4A;
    private static final int[] MATERIAL = {0x9A7A52, 0x5C6E3A, 0x9A968C, 0x86A858, 0xD8C890, 0x6A5A44, 0xECF0F4,
            0x8A8C90, 0x4E7A3E, 0xB8A860, 0x5A5856, 0xA890D8, 0xA8A29A, 0xB4B0A8, 0xA86A50, 0xB09468};
    private static final int ROOF = 0x4A3A34;

    private final World world;
    private final Terrain terrain;
    private final double west, size, metres;
    private final int pixels, cells;
    private final byte[] rgba;
    private final BitSet seen;
    private final ArrayDeque<Integer> toDraw = new ArrayDeque<>();
    private final List<Building>[] buildings;
    private Texture texture;
    private boolean stale = true, changed;
    private double clock;
    private int lastCell = -1;

    /** {@code world}'s map; {@code seenBefore} what {@link #encode} gave when she was last here (null: nothing). */
    @SuppressWarnings("unchecked")
    MiniMap(World world, String seenBefore) {
        this.world = world;
        this.terrain = world.terrain();
        GroundMap g = world.ground();
        this.west = g.west();
        this.size = Math.max(16, g.size());
        this.metres = Math.max(2, size / MOST_PIXELS);
        this.cells = Math.max(1, (int) Math.ceil(size / (metres * CELL_PX)));
        this.pixels = cells * CELL_PX;
        this.rgba = new byte[pixels * pixels * 4];
        for (int i = 0; i < pixels * pixels; i++) put(i, UNSEEN);
        this.seen = decode(seenBefore, cells);
        for (int c = seen.nextSetBit(0); c >= 0; c = seen.nextSetBit(c + 1)) toDraw.add(c);
        // the buildings by cell: only those on a cell are looked at when it is drawn
        this.buildings = new List[cells * cells];
        for (Building b : world.buildings()) {
            if (b.profile().furniture() || b.profile().spans() || b.profile().height() < 1.5) continue;
            double r = b.reach() + metres;
            int c0 = cellOf(b.position().x() - r), c1 = cellOf(b.position().x() + r);
            int r0 = cellOf(b.position().z() - r), r1 = cellOf(b.position().z() + r);
            for (int cz = r0; cz <= r1; cz++) {
                for (int cx = c0; cx <= c1; cx++) {
                    int i = cz * cells + cx;
                    if (buildings[i] == null) buildings[i] = new ArrayList<>(2);
                    buildings[i].add(b);
                }
            }
        }
    }

    private int cellOf(double v) {
        return Math.max(0, Math.min(cells - 1, (int) Math.floor((v - west) / (metres * CELL_PX))));
    }

    private void put(int pixel, int rgb) {
        rgba[pixel * 4] = (byte) (rgb >> 16);
        rgba[pixel * 4 + 1] = (byte) (rgb >> 8);
        rgba[pixel * 4 + 2] = (byte) rgb;
        rgba[pixel * 4 + 3] = (byte) 255;
    }

    /** How much of the level she has seen, 0 - 1. */
    double seenShare() {
        return seen.cardinality() / (double) (cells * cells);
    }

    /** Whether the cell {@code (x, z)} is in has been seen. */
    boolean seen(double x, double z) {
        if (x < west || z < west || x >= west + size || z >= west + size) return false;
        return seen.get(cellOf(z) * cells + cellOf(x));
    }

    /** She stands at {@code at}: the cells within {@link #SIGHT} of her are seen. Whether any is new. */
    boolean discover(Vec3 at) {
        int here = cellOf(at.z()) * cells + cellOf(at.x());
        if (here == lastCell) return false;
        lastCell = here;
        double cell = metres * CELL_PX;
        int reach = (int) Math.ceil(SIGHT / cell);
        int cx = cellOf(at.x()), cz = cellOf(at.z());
        boolean any = false;
        for (int dz = -reach; dz <= reach; dz++) {
            for (int dx = -reach; dx <= reach; dx++) {
                int x = cx + dx, z = cz + dz;
                if (x < 0 || z < 0 || x >= cells || z >= cells) continue;
                if (Math.hypot(dx, dz) * cell > SIGHT + cell * 0.5) continue;
                int i = z * cells + x;
                if (seen.get(i)) continue;
                seen.set(i);
                toDraw.add(i);
                any = true;
            }
        }
        changed |= any;
        return any;
    }

    /** Whether more has been seen since {@link #encode} was last asked. */
    boolean changed() { return changed; }

    /** Draw the cells waiting to be drawn, a few a frame. */
    private void drawCells() {
        for (int n = 0; n < CELLS_A_FRAME && !toDraw.isEmpty(); n++) {
            int cell = toDraw.poll();
            int cx = cell % cells, cz = cell / cells;
            List<Building> on = buildings[cell];
            for (int j = 0; j < CELL_PX; j++) {
                for (int i = 0; i < CELL_PX; i++) {
                    int px = cx * CELL_PX + i, pz = cz * CELL_PX + j;
                    put(pz * pixels + px, colour(west + (px + 0.5) * metres, west + (pz + 0.5) * metres, on));
                }
            }
            stale = true;
        }
    }

    /** The map's colour at {@code (x, z)}: a roof, water, or the ground there with its hill's shade. */
    private int colour(double x, double z, List<Building> on) {
        if (on != null) for (Building b : on) if (b.blocks(x, z, 0)) return ROOF;
        double h = terrain.height(x, z);
        WaterMap water = world.water();
        WaterMap.Kind wet = water.kindAt(x, z, h);
        if (wet != null && water.depth(x, z, h) > 0.08) {
            double deep = Math.min(1, water.depth(x, z, h) / 3);
            int shallow = switch (wet) {
                case SWAMP -> 0x5E8062;
                case CRYSTAL -> 0x9E86E0;
                default -> 0x5C9AD0;
            };
            return mix(shallow, wet == WaterMap.Kind.SWAMP ? 0x3E5A48 : 0x2E5E9C, deep);
        }
        int m = world.ground().material(x, z);
        int base = m < 0 || m >= MATERIAL.length ? GRASS : MATERIAL[m];
        // the hills, lit from the north-west
        double d = metres;
        double slope = (terrain.height(x + d, z) - terrain.height(x - d, z)
                + terrain.height(x, z + d) - terrain.height(x, z - d)) / (2 * d);
        double light = Math.max(-0.45, Math.min(0.3, -slope * 0.5));
        return light >= 0 ? mix(base, 0xFFFFFF, light) : mix(base, 0x000000, -light);
    }

    private static int mix(int a, int b, double t) {
        double k = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /** The texture as it now stands (GL thread). */
    private Texture texture(double dt) {
        drawCells();
        clock -= dt;
        if (texture == null || stale && clock <= 0) {
            if (texture != null) texture.close();
            texture = Texture.data(rgba, pixels, pixels);
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, texture.id());
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_NEAREST);
            stale = false;
            clock = 0.25;
        }
        return texture;
    }

    /**
     * The map in the square {@code s} across at {@code (x, y)}: round {@code
     * me}, who faces {@code heading}; {@code places} marked on it.
     */
    void draw(Ui ui, double dt, Vec3 me, double heading, float x, float y, float s, List<Waypoint> places) {
        Texture t = texture(dt);
        ui.plate(x - 5, y - 5, s + 10, s + 10, Theme.BRASS);
        ui.rect(x, y, s, s, Theme.rgb(UNSEEN >> 16, (UNSEEN >> 8) & 255, UNSEEN & 255, 1f));
        // the window round her, and the part of it the level covers
        double x0 = me.x() - VIEW / 2, z0 = me.z() - VIEW / 2, scale = s / VIEW;
        double lx0 = Math.max(x0, west), lz0 = Math.max(z0, west);
        double lx1 = Math.min(x0 + VIEW, west + size), lz1 = Math.min(z0 + VIEW, west + size);
        if (lx1 > lx0 && lz1 > lz0) {
            ui.image(t, x + (float) ((lx0 - x0) * scale), y + (float) ((lz0 - z0) * scale),
                    (float) ((lx1 - lx0) * scale), (float) ((lz1 - lz0) * scale),
                    (float) ((lx0 - west) / size), (float) ((lz0 - west) / size),
                    (float) ((lx1 - west) / size), (float) ((lz1 - west) / size), new float[]{1, 1, 1, 1});
        }
        // the places worth finding: on the map, or pinned to its edge the way they lie
        float half = s / 2, cx = x + half, cy = y + half;
        for (Waypoint w : places) {
            double dx = (w.at().x() - me.x()) * scale, dz = (w.at().z() - me.z()) * scale;
            double far = Math.max(Math.abs(dx), Math.abs(dz)), edge = half - 9;
            boolean off = far > edge;
            if (off) {
                dx *= edge / far;
                dz *= edge / far;
            }
            float size = w.mission() ? 18 : 14;
            if (!CardArt.icon(ui, w.icon(), cx + (float) dx - size / 2, cy + (float) dz - size / 2, size,
                    off ? 0.75f : 1f)) {
                ui.disc(cx + (float) dx, cy + (float) dz, 4, Theme.ACCENT);
            }
        }
        // she herself, and the way she faces
        float nx = (float) -Math.sin(heading), nz = (float) -Math.cos(heading);
        ui.disc(cx, cy, 5.5f, Theme.SOOT);
        ui.disc(cx, cy, 4, Theme.TITLE);
        ui.disc(cx + nx * 7, cy + nz * 7, 2.5f, Theme.SOOT);
        ui.disc(cx + nx * 7, cy + nz * 7, 1.6f, Theme.TITLE);
        ui.textCentered(ui.small, "N", cx, y + 1, Theme.BRASS_LIGHT);
    }

    // --- kept ---------------------------------------------------------------------------

    /** What she has seen, to keep: the cells a side, then the cells themselves, packed. */
    String encode() {
        changed = false;
        byte[] raw = seen.toByteArray();
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return cells + ":" + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /** What {@link #encode} gave, for a map {@code cells} a side (anything else: nothing seen). */
    static BitSet decode(String kept, int cells) {
        BitSet none = new BitSet(cells * cells);
        if (kept == null || kept.isBlank()) return none;
        int colon = kept.indexOf(':');
        if (colon <= 0) return none;
        try {
            if (Integer.parseInt(kept.substring(0, colon)) != cells) return none;
            Inflater in = new Inflater();
            in.setInput(Base64.getDecoder().decode(kept.substring(colon + 1)));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!in.finished()) {
                int n = in.inflate(buf);
                if (n == 0 && (in.needsInput() || in.needsDictionary())) break;
                out.write(buf, 0, n);
            }
            in.end();
            BitSet b = BitSet.valueOf(out.toByteArray());
            if (b.length() > cells * cells) return none;
            return b;
        } catch (IllegalArgumentException | DataFormatException e) {
            return none;
        }
    }

    @Override
    public void close() {
        if (texture != null) texture.close();
        texture = null;
    }
}
