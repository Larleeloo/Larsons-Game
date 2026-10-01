package com.larsons.game.gfx;

import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

/**
 * RGBA pixels, <b>premultiplied by alpha</b>, in off-heap memory ready for
 * {@code glTexImage2D} - or, for pixel art in a palette, one byte a texel:
 * the palette index, coloured on the GPU ({@link PaletteAtlas}).
 *
 * <p>Built on a worker thread (decoding a 512×512×30 frame sheet is tens of
 * milliseconds that must not land on the render thread) and consumed exactly
 * once on the GL thread by {@link Texture#upload}, which frees it.
 *
 * <p><b>Why premultiplied.</b> The 512-pixel Blender renders are drawn
 * minified with mipmaps and linear filtering, and filtering straight-alpha
 * pixels drags the colour of fully transparent texels (usually black) into
 * the silhouette's edge — a dark fringe round every character. Premultiplied
 * texels filter correctly, and the blend function {@code (ONE,
 * ONE_MINUS_SRC_ALPHA)} is the matching one.
 */
public final class PixelData {

    private final int width;
    private final int height;
    private final boolean indexed;
    private ByteBuffer pixels;

    private PixelData(int width, int height, ByteBuffer pixels, boolean indexed) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        this.indexed = indexed;
    }

    /** Convert (and premultiply) an image. Safe to call from any thread. */
    public static PixelData of(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        return of(argb, w, h);
    }

    /** Convert (and premultiply) ARGB pixels, row major from the top. */
    public static PixelData of(int[] argb, int w, int h) {
        ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
        for (int p : argb) {
            int a = (p >>> 24) & 0xFF;
            int r = (p >>> 16) & 0xFF;
            int g = (p >>> 8) & 0xFF;
            int b = p & 0xFF;
            if (a < 255) {
                r = (r * a + 127) / 255;
                g = (g * a + 127) / 255;
                b = (b * a + 127) / 255;
            }
            buf.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
        }
        buf.flip();
        return new PixelData(w, h, buf, false);
    }

    /** Palette indices, one a texel, row major from the top. Safe to call from any thread. */
    public static PixelData ofIndices(byte[] indices, int w, int h) {
        ByteBuffer buf = MemoryUtil.memAlloc(w * h);
        buf.put(indices, 0, w * h).flip();
        return new PixelData(w, h, buf, true);
    }

    public int width() { return width; }

    public int height() { return height; }

    /** Whether the texels are palette indices (one byte each) rather than RGBA. */
    public boolean indexed() { return indexed; }

    /** Bytes this will occupy on the GPU (without mipmaps). */
    public long bytes() { return (long) width * height * (indexed ? 1 : 4); }

    ByteBuffer buffer() { return pixels; }

    /** Release the off-heap memory. Idempotent. */
    public void free() {
        if (pixels != null) {
            MemoryUtil.memFree(pixels);
            pixels = null;
        }
    }
}
