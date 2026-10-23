package com.larsons.game.gfx;

import org.lwjgl.opengl.EXTTextureFilterAnisotropic;

import java.awt.image.BufferedImage;

import static org.lwjgl.opengl.GL33C.*;

/**
 * A 2D texture on the GPU, always premultiplied alpha (see {@link PixelData}).
 *
 * <p>Two filtering modes, because this game draws two kinds of art: the
 * 512-pixel Blender renders are minified, so they get trilinear mipmaps; the
 * 32-pixel fallback sprites and item icons are magnified many times over, so
 * they get nearest-neighbour sampling and keep their hard pixel edges.
 *
 * <p>A third kind holds pixel art as palette indices, one byte a texel
 * ({@link PixelData#indexed()}): always nearest-neighbour (an index between
 * two others means nothing), coloured by the sprite shader from a palette
 * ({@link PaletteAtlas}).
 *
 * <p>And two kinds for the scenery's pixel art: the ground's grass is a
 * {@link #tiled} texture, repeated across the whole floor, nearest-neighbour
 * close up and mipmapped far away; the trees', tufts' and rocks' sheets are
 * {@link #cutout} textures, every texel in or out, nearest-neighbour at
 * every distance but drawn far away from smaller copies of themselves.
 */
public final class Texture implements AutoCloseable {

    private final int id;
    private final int width;
    private final int height;
    private final long bytes;
    private final boolean indexed;
    private boolean closed;

    private Texture(int id, int width, int height, long bytes, boolean indexed) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.bytes = bytes;
        this.indexed = indexed;
    }

    /** Upload and free {@code data}. Must be called on the GL thread. */
    public static Texture upload(PixelData data, boolean pixelArt) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        if (data.indexed()) {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, data.width(), data.height(), 0,
                    GL_RED, GL_UNSIGNED_BYTE, data.buffer());
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        } else {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, data.width(), data.height(), 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, data.buffer());
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        if (pixelArt || data.indexed()) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        } else {
            glGenerateMipmap(GL_TEXTURE_2D);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        }
        int w = data.width(), h = data.height();
        boolean indexed = data.indexed();
        data.free();
        return new Texture(id, w, h, bytesFor(w, h, pixelArt, indexed), indexed);
    }

    /**
     * Upload and free {@code data} as a texture that tiles: repeated in both
     * directions, its texels crisp close up (nearest-neighbour magnification,
     * the pixel art's hard edges) and averaged far away (trilinear mipmaps,
     * anisotropic where the driver has it), so a floor of it neither blurs
     * underfoot nor shimmers at the horizon. Must be called on the GL thread.
     */
    public static Texture tiled(PixelData data) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, data.width(), data.height(), 0,
                GL_RGBA, GL_UNSIGNED_BYTE, data.buffer());
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        var caps = org.lwjgl.opengl.GL.getCapabilities();
        if (caps.GL_EXT_texture_filter_anisotropic || caps.GL_ARB_texture_filter_anisotropic) {
            float max = glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            glTexParameterf(GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                    Math.min(16f, Math.max(1f, max)));
        }
        int w = data.width(), h = data.height();
        data.free();
        return new Texture(id, w, h, bytesFor(w, h, false), false);
    }

    /**
     * Upload and free {@code data}, a sheet of pixel-art frames {@code
     * frameWidth × frameHeight} each, as a cutout texture: nearest-neighbour
     * at every distance - the nearest texel of the nearest mipmap level, so
     * its pixels stay hard-edged - but with mipmaps, so a tuft thirty metres
     * off is drawn from a copy of itself its own size rather than from every
     * other texel of the full one, which sparkles as it moves. The mipmaps
     * stop while a frame is still a few texels across, so no level mixes one
     * frame into the next. Must be called on the GL thread.
     */
    public static Texture cutout(PixelData data, int frameWidth, int frameHeight) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, data.width(), data.height(), 0,
                GL_RGBA, GL_UNSIGNED_BYTE, data.buffer());
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, cutoutLevels(frameWidth, frameHeight));
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        int w = data.width(), h = data.height();
        data.free();
        return new Texture(id, w, h, bytesFor(w, h, false), false);
    }

    /**
     * The last mipmap level a sheet of {@code frameWidth × frameHeight}
     * frames gets: halved while both sides still divide evenly and a frame
     * keeps at least 4 texels across (128 → 5, 32 → 3; 0 for a size that
     * does not halve).
     */
    public static int cutoutLevels(int frameWidth, int frameHeight) {
        int levels = 0;
        while (frameWidth % 2 == 0 && frameHeight % 2 == 0 && frameWidth >= 8 && frameHeight >= 8) {
            frameWidth /= 2;
            frameHeight /= 2;
            levels++;
        }
        return levels;
    }

    /**
     * The video memory a {@code width × height} texture takes once uploaded:
     * four bytes a texel, plus a third for the mip chain unless it is pixel art.
     */
    public static long bytesFor(int width, int height, boolean pixelArt) {
        return bytesFor(width, height, pixelArt, false);
    }

    /** {@link #bytesFor(int, int, boolean)}, or one byte a texel for palette indices. */
    public static long bytesFor(int width, int height, boolean pixelArt, boolean indexed) {
        long bytes = (long) width * height * (indexed ? 1 : 4);
        return pixelArt || indexed ? bytes : bytes * 4 / 3;
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

    /** Whether the texels are palette indices, drawn through a palette ({@link Batch}). */
    public boolean indexed() { return indexed; }

    public int width() { return width; }

    public int height() { return height; }

    /** Approximate video memory held. */
    public long bytes() { return bytes; }

    /** Whether the texture has been released. */
    public boolean closed() { return closed; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        glDeleteTextures(id);
    }
}
