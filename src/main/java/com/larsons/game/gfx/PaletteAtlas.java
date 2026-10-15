package com.larsons.game.gfx;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/**
 * The palettes pixel-art layers are drawn in: one row each of a {@value
 * #SIZE}-wide texture that the sprite shader ({@link Batch}) looks a layer's
 * colours up in, by the palette index its texture holds.
 *
 * <p>This is what makes recolouring free. A pixel-art sheet is uploaded once,
 * as palette indices ({@link Texture#indexed()}); what colours it is drawn in
 * is a row here, filled for the draw - so a layer can take any colours, a
 * different set for every character wearing it, and change them every frame
 * (a fade from one to another, a flash) without a texture being decoded or
 * uploaded again. Identical palettes share one row, and rows are kept from
 * one batch (and one frame) to the next: an outfit drawn in the same colours
 * as last frame sends nothing to the GPU, and a row is only written - a
 * kilobyte - for colours not already in the atlas, over the row longest
 * unused once the atlas is full.
 *
 * <p>Colours are straight (not premultiplied) ARGB, entry 0 transparent by
 * convention; the shader premultiplies.
 */
public final class PaletteAtlas implements AutoCloseable {

    /** Entries a palette has room for. */
    public static final int SIZE = 256;
    /** Palettes the atlas holds; one batch can draw this many before it has to start over. */
    public static final int ROWS = 512;

    private final int id;
    private final Rows rows = new Rows(ROWS);
    private final ByteBuffer upload = MemoryUtil.memAlloc(SIZE * 4);

    public PaletteAtlas() {
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SIZE, ROWS, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    /** The row {@code argb} is drawn from, or -1 when every row is taken by this batch. */
    int row(int[] argb) {
        return rows.add(argb);
    }

    /** A new batch: the rows stay, but those it does not use may be given to other colours. */
    void newBatch() {
        rows.newBatch();
    }

    /** Forget every row (a full batch, flushed, starting over). */
    void reset() {
        rows.clear();
    }

    /** Send the rows written since the last call to the GPU. GL thread. */
    void sync() {
        int[] dirty = rows.takeDirty();
        if (dirty.length == 0) return;
        glBindTexture(GL_TEXTURE_2D, id);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        for (int r : dirty) {
            int[] p = rows.get(r);
            upload.clear();
            for (int i = 0; i < SIZE; i++) {
                int c = i < p.length ? p[i] : 0;
                upload.put((byte) (c >>> 16)).put((byte) (c >>> 8)).put((byte) c).put((byte) (c >>> 24));
            }
            upload.flip();
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, r, SIZE, 1, GL_RGBA, GL_UNSIGNED_BYTE, upload);
        }
    }

    void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, id);
        glActiveTexture(GL_TEXTURE0);
    }

    @Override
    public void close() {
        MemoryUtil.memFree(upload);
        glDeleteTextures(id);
    }

    /** The row bookkeeping, apart from GL (so it can be tested without a context). */
    static final class Rows {
        private static final int[] NONE = {};
        private final int[][] rows;
        /** The batch each row was last drawn in. */
        private final long[] usedIn;
        private final boolean[] dirty;
        private final int[] dirtyList;
        private int dirtyCount;
        private int size;
        private long batch;
        private final Map<Palette, Integer> index = new HashMap<>();

        Rows(int capacity) {
            rows = new int[capacity][];
            usedIn = new long[capacity];
            dirty = new boolean[capacity];
            dirtyList = new int[capacity];
        }

        /**
         * The row holding these colours - added if none does yet, in a free
         * row or else the one least recently drawn in an earlier batch; -1
         * when every row is drawn in this batch.
         */
        int add(int[] argb) {
            Integer found = index.get(new Palette(argb));
            if (found != null) {
                usedIn[found] = batch;
                return found;
            }
            int r = size < rows.length ? size++ : leastRecentlyUsed();
            if (r < 0) return -1;
            if (rows[r] != null) index.remove(new Palette(rows[r]));
            int[] copy = Arrays.copyOf(argb, Math.min(argb.length, SIZE));
            rows[r] = copy;
            index.put(new Palette(copy), r);
            usedIn[r] = batch;
            if (!dirty[r]) {
                dirty[r] = true;
                dirtyList[dirtyCount++] = r;
            }
            return r;
        }

        private int leastRecentlyUsed() {
            int best = -1;
            for (int r = 0; r < size; r++) {
                if (usedIn[r] < batch && (best < 0 || usedIn[r] < usedIn[best])) best = r;
            }
            return best;
        }

        /** Start a new batch: every row is kept, and those it does not draw may be reused. */
        void newBatch() {
            batch++;
        }

        /** The rows written since the last call, to upload. */
        int[] takeDirty() {
            if (dirtyCount == 0) return NONE;
            int[] out = Arrays.copyOf(dirtyList, dirtyCount);
            for (int r : out) dirty[r] = false;
            dirtyCount = 0;
            return out;
        }

        int size() { return size; }

        int[] get(int row) { return rows[row]; }

        /** Forget every row. */
        void clear() {
            Arrays.fill(rows, 0, size, null);
            Arrays.fill(dirty, false);
            dirtyCount = 0;
            size = 0;
            index.clear();
            batch++;
        }

        private record Palette(int[] argb) {
            @Override
            public boolean equals(Object o) {
                return o instanceof Palette p && Arrays.equals(argb, p.argb);
            }

            @Override
            public int hashCode() { return Arrays.hashCode(argb); }
        }
    }
}
