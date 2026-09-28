package com.larsons.game.sprite;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A sprite sheet decoded on the CPU and repacked for the GPU: every frame the
 * same size, laid out in a near-square grid that fits the driver's largest
 * texture.
 *
 * <p><b>Why repack.</b> A 30-frame animation of 512-pixel frames rendered out
 * of Blender as one strip is 15 360 pixels wide — past the 8 192 or 16 384 a
 * lot of GPUs allow for one texture. Repacked six across and five down it is
 * 3 072 × 2 560 and fits anywhere. Frame order is unchanged: left to right,
 * then top to bottom, exactly how the engine's {@code SpriteSheet} slices.
 *
 * <p>Pure CPU and thread-safe to build — the library decodes sheets on worker
 * threads so a 512-pixel sheet never costs the render thread a frame.
 */
public final class SheetImage {

    private final BufferedImage atlas;
    private final int frameWidth;
    private final int frameHeight;
    private final int frameCount;
    private final int columns;
    private final boolean pixelArt;

    private SheetImage(BufferedImage atlas, int frameWidth, int frameHeight, int frameCount,
                       int columns, boolean pixelArt) {
        this.atlas = atlas;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.frameCount = frameCount;
        this.columns = columns;
        this.pixelArt = pixelArt;
    }

    public BufferedImage atlas() { return atlas; }

    public int frameWidth() { return frameWidth; }

    public int frameHeight() { return frameHeight; }

    public int frameCount() { return frameCount; }

    public int columns() { return columns; }

    /** Nearest-neighbour art (the 32-pixel fallback) rather than filtered renders. */
    public boolean pixelArt() { return pixelArt; }

    /**
     * The frame size to slice {@code w × h} pixels into, given the size the
     * profile expects. In order: the expected size if the image divides into
     * it; square frames along a strip; otherwise the whole image is one frame.
     */
    public static int[] frameSize(int w, int h, int expectedW, int expectedH) {
        if (expectedW > 0 && expectedH > 0 && w % expectedW == 0 && h % expectedH == 0) {
            return new int[]{expectedW, expectedH};
        }
        if (h > 0 && w % h == 0) return new int[]{h, h};
        if (w > 0 && h % w == 0) return new int[]{w, w};
        return new int[]{w, h};
    }

    /** Slice {@code sheet} into frames, dropping empty grid cells at the end. */
    public static List<BufferedImage> slice(BufferedImage sheet, int frameW, int frameH) {
        List<BufferedImage> frames = new ArrayList<>();
        int cols = Math.max(1, sheet.getWidth() / frameW);
        int rows = Math.max(1, sheet.getHeight() / frameH);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                frames.add(sheet.getSubimage(c * frameW, r * frameH, frameW, frameH));
            }
        }
        // A grid rarely comes out exactly full: 30 frames in a 6×6 grid leaves
        // six transparent cells, and playing them would blink the character
        // out of existence at the end of every loop.
        while (frames.size() > 1 && isEmpty(frames.get(frames.size() - 1))) {
            frames.remove(frames.size() - 1);
        }
        return frames;
    }

    static boolean isEmpty(BufferedImage frame) {
        int w = frame.getWidth(), h = frame.getHeight();
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            frame.getRGB(0, y, w, 1, row, 0, w);
            for (int p : row) if ((p >>> 24) != 0) return false;
        }
        return true;
    }

    /**
     * Decode a sheet: slice it using the profile's frame size, optionally
     * shrink every frame by {@code scale} (the "sprite resolution" setting),
     * and repack to fit {@code maxTexture}.
     */
    public static SheetImage decode(BufferedImage sheet, int expectedW, int expectedH,
                                    double scale, int maxTexture, boolean pixelArt) {
        int[] size = frameSize(sheet.getWidth(), sheet.getHeight(), expectedW, expectedH);
        List<BufferedImage> frames = slice(sheet, size[0], size[1]);
        return pack(frames, scale, maxTexture, pixelArt);
    }

    /** Pack same-sized frames into a near-square grid, shrinking if they cannot fit. */
    public static SheetImage pack(List<BufferedImage> frames, double scale, int maxTexture,
                                  boolean pixelArt) {
        if (frames.isEmpty()) throw new IllegalArgumentException("a sheet needs at least one frame");
        int n = frames.size();
        int srcW = frames.get(0).getWidth(), srcH = frames.get(0).getHeight();
        double s = Math.max(0.05, Math.min(1, scale));
        int fw, fh, cols, rows;
        while (true) {
            fw = Math.max(1, (int) Math.round(srcW * s));
            fh = Math.max(1, (int) Math.round(srcH * s));
            cols = Math.max(1, (int) Math.ceil(Math.sqrt(n * (double) fh / fw)));
            cols = Math.min(cols, Math.max(1, maxTexture / fw));
            rows = (int) Math.ceil(n / (double) cols);
            if ((long) rows * fh <= maxTexture && (long) cols * fw <= maxTexture) break;
            s *= 0.75; // does not fit even packed: shrink rather than fail
        }
        BufferedImage atlas = new BufferedImage(cols * fw, rows * fh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, pixelArt
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int i = 0; i < n; i++) {
            BufferedImage f = frames.get(i);
            if (fw != srcW || fh != srcH) f = shrink(f, fw, fh, pixelArt);
            g.drawImage(f, (i % cols) * fw, (i / cols) * fh, fw, fh, null);
        }
        g.dispose();
        return new SheetImage(atlas, fw, fh, n, cols, pixelArt);
    }

    /**
     * Downscale by repeated halving and one bilinear step: a single bilinear
     * step from 512 to 128 skips three of every four source pixels and
     * shimmers; halving averages them.
     */
    private static BufferedImage shrink(BufferedImage src, int w, int h, boolean pixelArt) {
        BufferedImage cur = src;
        if (!pixelArt) {
            while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) {
                cur = draw(cur, cur.getWidth() / 2, cur.getHeight() / 2, false);
            }
        }
        return cur.getWidth() == w && cur.getHeight() == h ? cur : draw(cur, w, h, pixelArt);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h, boolean nearest) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, nearest
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** A sheet from frames already at their final size (the fallback generator). */
    public static SheetImage ofFrames(List<BufferedImage> frames, boolean pixelArt, int maxTexture) {
        return pack(frames, 1.0, maxTexture, pixelArt);
    }
}
