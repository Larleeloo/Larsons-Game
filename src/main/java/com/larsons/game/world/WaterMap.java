package com.larsons.game.world;

import java.util.Arrays;

/**
 * Where the water lies, and how it flows: ponds, rivers and streams painted
 * over the ground in cells of {@value #CELL} m, each with its surface's
 * height, its {@link Kind} - clear, swampy, crystal - and the way and speed
 * it flows; and a level's sea, filling everything lower than {@link
 * #seaLevel} all the way to the horizon.
 *
 * <p>Water is only where its surface is above the ground: a pond is painted
 * over a hollow sculpted for it, a river along a channel, and the sea fills
 * whatever is lower than it - so the ground's own shape is the shore.
 *
 * <p>Pure data, as {@link GroundMap} is: the editor paints it, a level's
 * folder keeps it ({@code water.png}), {@code WaterRenderer} draws it and
 * {@link Player} wades through it.
 */
public final class WaterMap {

    /** The kinds of water. */
    public enum Kind {
        /** A clear stream, river or pond: turquoise in the shallows, blue in the deep. */
        CLEAR("clear", "Clear"),
        /** A marsh's still, green-brown water. */
        SWAMP("swamp", "Swamp"),
        /** The sea: deeper blue, waves running up the shore. */
        SEA("sea", "Sea"),
        /** The crystal biome's streams: pale, shining, through violet to mint. */
        CRYSTAL("crystal", "Crystal");

        private final String key, label;

        Kind(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() { return key; }

        public String label() { return label; }

        /** Its number in a level's file and on the GPU: 1 - 4 (0 is no water). */
        public int code() { return ordinal() + 1; }

        public static Kind byCode(int code) {
            return code >= 1 && code <= values().length ? values()[code - 1] : null;
        }

        public static Kind byKey(String key) {
            for (Kind k : values()) if (k.key.equalsIgnoreCase(key == null ? "" : key.trim())) return k;
            return null;
        }
    }

    /** Metres a cell. */
    public static final double CELL = 0.5;
    /** The fastest water flows, metres a second. */
    public static final double MAX_FLOW = 3;
    /** Deeper than this she cannot wade (she does not swim yet). */
    public static final double TOO_DEEP = 1.05;
    /** Deeper than this, she wades. */
    public static final double WADE = 0.1;

    /** No water, and none can be painted. */
    public static final WaterMap NONE = new WaterMap(1);

    private final int cells;
    private final double origin;
    /** Each cell's surface height (NaN: none), kind (0 none), and flow (x, z metres a second). */
    private final float[] surface, flowX, flowZ;
    private final byte[] kind;
    private double seaLevel = Double.NaN;
    private int version;
    private boolean empty = true;

    /** No water over a square {@code size} metres a side, centred on the origin. */
    public WaterMap(double size) {
        cells = Math.max(1, (int) Math.round(size / CELL));
        origin = -cells * CELL / 2;
        surface = new float[cells * cells];
        Arrays.fill(surface, Float.NaN);
        flowX = new float[cells * cells];
        flowZ = new float[cells * cells];
        kind = new byte[cells * cells];
    }

    public int cells() { return cells; }

    public double west() { return origin; }

    public double size() { return cells * CELL; }

    /** How many times it has changed: what is drawn of it is built again when this changes. */
    public int version() { return version; }

    /** Whether there is no water at all - none painted, no sea. */
    public boolean isEmpty() { return empty && Double.isNaN(seaLevel); }

    /** Whether no water has been painted (there may be a sea). */
    public boolean nonePainted() { return empty; }

    /** The sea's surface, metres (NaN: no sea). */
    public double seaLevel() { return seaLevel; }

    /** Let the sea fill everything lower than {@code level} metres (NaN: no sea). */
    public WaterMap setSeaLevel(double level) {
        if (this == NONE) throw new UnsupportedOperationException("WaterMap.NONE stays dry");
        double v = Double.isNaN(level) ? Double.NaN : Math.max(-50, Math.min(50, level));
        if (Double.compare(v, seaLevel) != 0) {
            seaLevel = v;
            version++;
        }
        return this;
    }

    /** The cell {@code (x, z)} is in, or -1 off the map. */
    private int index(double x, double z) {
        int c = (int) Math.floor((x - origin) / CELL), r = (int) Math.floor((z - origin) / CELL);
        if (c < 0 || r < 0 || c >= cells || r >= cells) return -1;
        return r * cells + c;
    }

    /** The painted water's surface over cell {@code (c, r)} (NaN: none). */
    public float surfaceAt(int c, int r) {
        return surface[r * cells + c];
    }

    /** The painted water's kind over cell {@code (c, r)} (null: none). */
    public Kind kindOf(int c, int r) {
        return Kind.byCode(kind[r * cells + c]);
    }

    /** Cell {@code (c, r)}'s flow: x, z metres a second. */
    public float flowX(int c, int r) { return flowX[r * cells + c]; }

    public float flowZ(int c, int r) { return flowZ[r * cells + c]; }

    /**
     * The water's surface at {@code (x, z)} over ground {@code groundY} high:
     * the painted water's or the sea's, whichever is there and higher - NaN
     * where the ground is above both (dry).
     */
    public double surface(double x, double z, double groundY) {
        int i = index(x, z);
        double s = i >= 0 ? surface[i] : Double.NaN;
        if (!Double.isNaN(seaLevel) && (Double.isNaN(s) || seaLevel > s)) s = seaLevel;
        return Double.isNaN(s) || s <= groundY ? Double.NaN : s;
    }

    /** How deep the water is at {@code (x, z)} over ground {@code groundY} high: 0 where it is dry. */
    public double depth(double x, double z, double groundY) {
        double s = surface(x, z, groundY);
        return Double.isNaN(s) ? 0 : s - groundY;
    }

    /** The kind of water at {@code (x, z)} over ground {@code groundY} high, or null where it is dry. */
    public Kind kindAt(double x, double z, double groundY) {
        int i = index(x, z);
        double s = i >= 0 ? surface[i] : Double.NaN;
        if (!Double.isNaN(s) && s > groundY && (Double.isNaN(seaLevel) || s >= seaLevel)) return Kind.byCode(kind[i]);
        if (!Double.isNaN(seaLevel) && seaLevel > groundY) return Kind.SEA;
        return null;
    }

    /** The painted water's kind at {@code (x, z)}, whatever the ground (null: none painted there). */
    public Kind kindAt(double x, double z) {
        int i = index(x, z);
        return i < 0 ? null : Kind.byCode(kind[i]);
    }

    /**
     * Paint water of {@code kind} with its surface {@code level} metres high
     * over the cells within {@code radius} of {@code (x, z)}, flowing {@code
     * (fx, fz)} metres a second.
     */
    public WaterMap paint(Kind kind, double level, double x, double z, double radius, double fx, double fz) {
        if (this == NONE) throw new UnsupportedOperationException("WaterMap.NONE stays dry");
        double speed = Math.hypot(fx, fz);
        if (speed > MAX_FLOW) {
            fx *= MAX_FLOW / speed;
            fz *= MAX_FLOW / speed;
        }
        double fxx = fx, fzz = fz;
        forCells(x, z, radius, i -> {
            surface[i] = (float) level;
            this.kind[i] = (byte) kind.code();
            flowX[i] = (float) fxx;
            flowZ[i] = (float) fzz;
        });
        empty = false;
        return this;
    }

    /** Take the painted water away within {@code radius} of {@code (x, z)}. */
    public WaterMap erase(double x, double z, double radius) {
        if (this == NONE) throw new UnsupportedOperationException("WaterMap.NONE stays dry");
        forCells(x, z, radius, i -> {
            surface[i] = Float.NaN;
            kind[i] = 0;
            flowX[i] = flowZ[i] = 0;
        });
        recount();
        return this;
    }

    private void forCells(double x, double z, double radius, java.util.function.IntConsumer cell) {
        int c0 = (int) Math.floor((x - radius - origin) / CELL), c1 = (int) Math.floor((x + radius - origin) / CELL);
        int r0 = (int) Math.floor((z - radius - origin) / CELL), r1 = (int) Math.floor((z + radius - origin) / CELL);
        for (int r = Math.max(0, r0); r <= Math.min(cells - 1, r1); r++) {
            for (int c = Math.max(0, c0); c <= Math.min(cells - 1, c1); c++) {
                double px = origin + (c + 0.5) * CELL, pz = origin + (r + 0.5) * CELL;
                if (Math.hypot(px - x, pz - z) <= radius) cell.accept(r * cells + c);
            }
        }
        version++;
    }

    private void recount() {
        empty = true;
        for (byte k : kind) {
            if (k != 0) {
                empty = false;
                return;
            }
        }
    }

    // --- in a level's file: four bytes a cell -------------------------------------------------

    /** The surface's offset and scale in a file: a byte pair of (height + 128) * 256. */
    private static final double OFFSET = 128, SCALE = 256;

    /**
     * Every cell as four bytes, row by row from the north-west: the surface
     * (two bytes, high first: (height + 128) × 256; 0 none), the kind and
     * flow packed in one (kind 0-4 in the low three bits, the flow's
     * direction in sixteenths of a turn above them), and the flow's speed
     * (0-255 for 0 - {@link #MAX_FLOW} m/s).
     */
    public byte[] bytes() {
        byte[] out = new byte[cells * cells * 4];
        for (int i = 0; i < cells * cells; i++) {
            if (kind[i] == 0 || Float.isNaN(surface[i])) continue;
            int v = (int) Math.max(1, Math.min(65535, Math.round((surface[i] + OFFSET) * SCALE)));
            double speed = Math.hypot(flowX[i], flowZ[i]);
            int dir = speed < 1e-6 ? 0 : (int) Math.round(Math.atan2(flowZ[i], flowX[i]) / (2 * Math.PI) * 16) & 15;
            out[i * 4] = (byte) (v >> 8);
            out[i * 4 + 1] = (byte) v;
            out[i * 4 + 2] = (byte) (kind[i] | dir << 3);
            out[i * 4 + 3] = (byte) Math.round(Math.min(1, speed / MAX_FLOW) * 255);
        }
        return out;
    }

    /** Every cell from {@link #bytes()}. */
    public WaterMap setBytes(byte[] b) {
        if (b.length != cells * cells * 4) throw new IllegalArgumentException(b.length + " bytes for " + cells * cells * 4);
        for (int i = 0; i < cells * cells; i++) {
            int v = (b[i * 4] & 0xFF) << 8 | (b[i * 4 + 1] & 0xFF);
            int k = b[i * 4 + 2] & 7;
            if (v == 0 || k == 0) {
                surface[i] = Float.NaN;
                kind[i] = 0;
                flowX[i] = flowZ[i] = 0;
                continue;
            }
            surface[i] = (float) (v / SCALE - OFFSET);
            kind[i] = (byte) k;
            double a = ((b[i * 4 + 2] & 0xFF) >> 3) / 16.0 * 2 * Math.PI;
            double speed = (b[i * 4 + 3] & 0xFF) / 255.0 * MAX_FLOW;
            flowX[i] = (float) (Math.cos(a) * speed);
            flowZ[i] = (float) (Math.sin(a) * speed);
        }
        recount();
        version++;
        return this;
    }

    /** A copy to paint on without changing this one. */
    public WaterMap copy() {
        WaterMap w = new WaterMap(size());
        w.setBytes(bytes());
        w.seaLevel = seaLevel;
        return w;
    }

    /** {@code other}'s water, put over the middle of this map, and its sea. */
    public WaterMap copyFrom(WaterMap other) {
        Arrays.fill(surface, Float.NaN);
        Arrays.fill(kind, (byte) 0);
        Arrays.fill(flowX, 0);
        Arrays.fill(flowZ, 0);
        int shift = (int) Math.round((other.origin - origin) / CELL);
        for (int r = 0; r < other.cells; r++) {
            int rr = r + shift;
            if (rr < 0 || rr >= cells) continue;
            for (int c = 0; c < other.cells; c++) {
                int cc = c + shift;
                if (cc < 0 || cc >= cells) continue;
                int i = rr * cells + cc, j = r * other.cells + c;
                surface[i] = other.surface[j];
                kind[i] = other.kind[j];
                flowX[i] = other.flowX[j];
                flowZ[i] = other.flowZ[j];
            }
        }
        seaLevel = other.seaLevel;
        recount();
        version++;
        return this;
    }
}
