package com.larsons.game.gfx;

import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33C.*;

/**
 * A stack of same-sized 2D textures on the GPU, one sampler for them all
 * ({@code sampler2DArray} in GLSL, the layer the third coordinate): how the
 * ground draws its dozen pixel-art tiles, and the maps of where each lies,
 * with one texture unit each rather than one a tile.
 *
 * <p>Two kinds, as {@link Texture#tiled} and {@link Texture#data} are for
 * one texture: {@link #tiled} layers repeat, crisp close up and mipmapped far
 * away; {@link #data} layers are numbers, read with linear filtering and
 * clamped, and can be uploaded again one layer at a time ({@link #update}).
 */
public final class TextureArray implements AutoCloseable {

    private final int id, width, height, layers;
    private boolean closed;

    private TextureArray(int id, int width, int height, int layers) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.layers = layers;
    }

    /**
     * Upload and free {@code tiles} - every one the same size - as layers
     * that tile, as {@link Texture#tiled}. Must be called on the GL thread.
     */
    public static TextureArray tiled(List<PixelData> tiles) {
        int w = tiles.get(0).width(), h = tiles.get(0).height();
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, w, h, tiles.size(), 0, GL_RGBA, GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
        for (int i = 0; i < tiles.size(); i++) {
            PixelData d = tiles.get(i);
            if (d.width() != w || d.height() != h) {
                throw new IllegalArgumentException("tile " + i + " is " + d.width() + "x" + d.height() + ", not "
                        + w + "x" + h);
            }
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, w, h, 1, GL_RGBA, GL_UNSIGNED_BYTE, d.buffer());
            d.free();
        }
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        var caps = org.lwjgl.opengl.GL.getCapabilities();
        if (caps.GL_EXT_texture_filter_anisotropic || caps.GL_ARB_texture_filter_anisotropic) {
            float max = glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            glTexParameterf(GL_TEXTURE_2D_ARRAY, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                    Math.min(16f, Math.max(1f, max)));
        }
        return new TextureArray(id, w, h, tiles.size());
    }

    /**
     * {@code layers} layers of {@code width × height} texels of data, four
     * bytes each, all zero until {@link #update}d: read as they are, with
     * linear filtering and from mipmaps far away, clamped at the edges. Must
     * be called on the GL thread.
     */
    public static TextureArray data(int width, int height, int layers) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);
        ByteBuffer zero = MemoryUtil.memCalloc(width * height * 4);
        try {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, width, height, layers, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                    (ByteBuffer) null);
            for (int i = 0; i < layers; i++) {
                glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, width, height, 1, GL_RGBA, GL_UNSIGNED_BYTE, zero);
            }
        } finally {
            MemoryUtil.memFree(zero);
        }
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        return new TextureArray(id, width, height, layers);
    }

    /**
     * Upload {@code rgba} ({@code width × height × 4} bytes, or null for all
     * zero) as layer {@code layer} of a {@link #data} array, then its mipmaps
     * again. Must be called on the GL thread.
     */
    public void update(int layer, byte[] rgba) {
        ByteBuffer buf = rgba == null ? MemoryUtil.memCalloc(width * height * 4)
                : MemoryUtil.memAlloc(width * height * 4);
        try {
            if (rgba != null) buf.put(rgba, 0, width * height * 4).flip();
            glBindTexture(GL_TEXTURE_2D_ARRAY, id);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, width, height, 1, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    /** Make the mipmaps again, after {@link #update}s. Must be called on the GL thread. */
    public void mipmaps() {
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);
    }

    public int width() { return width; }

    public int height() { return height; }

    public int layers() { return layers; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteTextures(id);
    }
}
