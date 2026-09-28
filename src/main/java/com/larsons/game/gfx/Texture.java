package com.larsons.game.gfx;

import java.awt.image.BufferedImage;

import static org.lwjgl.opengl.GL33C.*;

/**
 * A 2D texture on the GPU, always premultiplied alpha (see {@link PixelData}).
 *
 * <p>Two filtering modes, because this game draws two kinds of art: the
 * 512-pixel Blender renders are minified, so they get trilinear mipmaps; the
 * 32-pixel fallback sprites and item icons are magnified many times over, so
 * they get nearest-neighbour sampling and keep their hard pixel edges.
 */
public final class Texture implements AutoCloseable {

    private final int id;
    private final int width;
    private final int height;
    private final long bytes;
    private boolean closed;

    private Texture(int id, int width, int height, long bytes) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.bytes = bytes;
    }

    /** Upload and free {@code data}. Must be called on the GL thread. */
    public static Texture upload(PixelData data, boolean pixelArt) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, data.width(), data.height(), 0,
                GL_RGBA, GL_UNSIGNED_BYTE, data.buffer());
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        if (pixelArt) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        } else {
            glGenerateMipmap(GL_TEXTURE_2D);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        }
        int w = data.width(), h = data.height();
        data.free();
        return new Texture(id, w, h, bytesFor(w, h, pixelArt));
    }

    /**
     * The video memory a {@code width × height} texture takes once uploaded:
     * four bytes a texel, plus a third for the mip chain unless it is pixel art.
     */
    public static long bytesFor(int width, int height, boolean pixelArt) {
        long bytes = (long) width * height * 4;
        return pixelArt ? bytes : bytes * 4 / 3;
    }

    /** Convenience for small images made on the GL thread. */
    public static Texture of(BufferedImage image, boolean pixelArt) {
        return upload(PixelData.of(image), pixelArt);
    }

    /** A 1×1 opaque white texture, for untextured quads through the sprite shader. */
    public static Texture white() {
        return upload(PixelData.of(new int[]{0xFFFFFFFF}, 1, 1), true);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public int id() { return id; }

    public int width() { return width; }

    public int height() { return height; }

    /** Approximate video memory held. */
    public long bytes() { return bytes; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteTextures(id);
    }
}
