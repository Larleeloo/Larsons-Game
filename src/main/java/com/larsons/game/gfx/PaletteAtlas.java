package com.larsons.game.gfx;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
 * uploaded again. Rows are handed out per batch and identical palettes share
 * one; the rows a batch added are sent to the GPU before each of its draw
 * calls, a kilobyte each.
 *
 * <p>Colours are straight (not premultiplied) ARGB, entry 0 transparent by
 * convention; the shader premultiplies.
 */
public final class PaletteAtlas implements AutoCloseable {

    /** Entries a palette has room for. */
    public static final int SIZE = 256;
    /** Palettes one batch can draw before it has to start over. */
    public static final int ROWS = 512;

    private final int id;
    private final Rows rows = new Rows(ROWS);
    private final ByteBuffer upload = MemoryUtil.memAlloc(SIZE * 4);
    private int synced;

    public PaletteAtlas() {
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SIZE, ROWS, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    /** The row {@code argb} is drawn from, or -1 when the atlas is full (see {@link #full()}). */
    int row(int[] argb) {
        return rows.add(argb);
    }

    boolean full() { return rows.size() >= ROWS; }

    /** Forget every row (a new batch, or a full one flushed). */
    void reset() {
        rows.clear();
        synced = 0;
    }

    /** Send the rows added since the last call to the GPU. GL thread. */
    void sync() {
        if (synced == rows.size()) return;
        glBindTexture(GL_TEXTURE_2D, id);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        for (int r = synced; r < rows.size(); r++) {
            int[] p = rows.get(r);
            upload.clear();
            for (int i = 0; i < SIZE; i++) {
                int c = i < p.length ? p[i] : 0;
                upload.put((byte) (c >>> 16)).put((byte) (c >>> 8)).put((byte) c).put((byte) (c >>> 24));
            }
            upload.flip();
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, r, SIZE, 1, GL_RGBA, GL_UNSIGNED_BYTE, upload);
        }
        synced = rows.size();
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
        private final int capacity;
        private final List<int[]> rows = new ArrayList<>();
        private final Map<Palette, Integer> index = new HashMap<>();

        Rows(int capacity) {
            this.capacity = capacity;
        }

        /** The row holding these colours, added if none does yet; -1 when full. */
        int add(int[] argb) {
            Palette key = new Palette(argb);
            Integer r = index.get(key);
            if (r != null) return r;
            if (rows.size() >= capacity) return -1;
            int[] copy = Arrays.copyOf(argb, Math.min(argb.length, SIZE));
            rows.add(copy);
            index.put(new Palette(copy), rows.size() - 1);
            return rows.size() - 1;
        }

        int size() { return rows.size(); }

        int[] get(int row) { return rows.get(row); }

        void clear() {
            rows.clear();
            index.clear();
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
