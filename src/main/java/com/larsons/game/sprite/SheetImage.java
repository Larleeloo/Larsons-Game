package com.larsons.game.sprite;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
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
 * <p><b>Why crop.</b> Every layer is rendered with the body's framing, so a
 * hat or an earring is a few dozen pixels in a 512-pixel frame and the rest is
 * transparent. A sheet read from disk keeps only the box its frames actually
 * cover (the same box for every frame, plus a transparent gutter), and
 * remembers where that box sat in the frame, so the layer is drawn on just
 * that part of the card. The 18-layer outfit in {@code assets/sprites/} takes
 * under a tenth of the video memory it did as whole frames.
 *
 * <p>Pure CPU and thread-safe to build — the library decodes sheets on worker
 * threads so a 512-pixel sheet never costs the render thread a frame.
 */
public final class SheetImage {

    /**
     * Transparent texels kept round a cropped frame, so linear filtering and
     * the mip chain never pull in the neighbouring frame of the atlas.
     */
    static final int GUTTER = 8;

    /**
     * Crop edges snap to multiples of this many source pixels. Then halving a
     * cropped frame (the Half and Quarter sprite resolutions) averages exactly
     * the pixels that halving the whole frame would, and the layer lands on
     * the same texels as before cropping.
     */
    static final int ALIGN = 8;

    private final BufferedImage atlas;
    private final int frameWidth;
    private final int frameHeight;
    private final int frameCount;
    private final int columns;
    private final boolean pixelArt;
    private final int sourceWidth, sourceHeight;
    private final int cropX, cropY, cropWidth, cropHeight;

    private SheetImage(BufferedImage atlas, int frameWidth, int frameHeight, int frameCount,
                       int columns, boolean pixelArt, int sourceWidth, int sourceHeight,
                       int[] crop) {
        this.atlas = atlas;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.frameCount = frameCount;
        this.columns = columns;
        this.pixelArt = pixelArt;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.cropX = crop[0];
        this.cropY = crop[1];
        this.cropWidth = crop[2];
        this.cropHeight = crop[3];
    }

    public BufferedImage atlas() { return atlas; }

    /** Width of one frame's cell in the atlas: the cropped frame, after scaling. */
    public int frameWidth() { return frameWidth; }

    /** Height of one frame's cell in the atlas: the cropped frame, after scaling. */
    public int frameHeight() { return frameHeight; }

    public int frameCount() { return frameCount; }

    public int columns() { return columns; }

    /** Nearest-neighbour art (the 32-pixel fallback) rather than filtered renders. */
    public boolean pixelArt() { return pixelArt; }

    /**
     * The part of the whole frame the atlas cells hold, as fractions of the
     * frame from its top-left corner: {@code {x0, y0, x1, y1}}. The whole frame
     * is {@code {0, 0, 1, 1}}; a sheet with nothing in it is empty ({@code x1
     * == x0}). {@code mirrored} gives the box for the frame flipped left to
     * right, the way a borrowed east/west twin is drawn.
     */
    public double[] region(boolean mirrored) {
        double x0 = cropX / (double) sourceWidth, x1 = (cropX + cropWidth) / (double) sourceWidth;
        double y0 = cropY / (double) sourceHeight, y1 = (cropY + cropHeight) / (double) sourceHeight;
        return mirrored ? new double[]{1 - x1, y0, 1 - x0, y1} : new double[]{x0, y0, x1, y1};
    }

    /** The kept box in source pixels of one frame: {@code {x, y, width, height}}. */
    public int[] crop() { return new int[]{cropX, cropY, cropWidth, cropHeight}; }

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

    /**
     * The box every frame's visible pixels fit in, {@code {x0, y0, x1, y1}}
     * with the far edges exclusive, or {@code null} if no frame shows
     * anything. Alpha of 1/255 and below counts as nothing: the renders mark
     * an otherwise blank frame with one such pixel so it is not dropped as an
     * empty trailing cell.
     */
    static int[] contentBox(List<BufferedImage> frames) {
        int w = frames.get(0).getWidth(), h = frames.get(0).getHeight();
        int x0 = w, y0 = h, x1 = 0, y1 = 0;
        int[] row = new int[w];
        for (BufferedImage f : frames) {
            WritableRaster alpha = f.getAlphaRaster();
            if (alpha == null && !f.getColorModel().hasAlpha()) return new int[]{0, 0, w, h};
            for (int y = 0; y < h; y++) {
                if (alpha != null) {
                    alpha.getSamples(0, y, w, 1, 0, row);
                } else {
                    f.getRGB(0, y, w, 1, row, 0, w);
                    for (int x = 0; x < w; x++) row[x] >>>= 24;
                }
                int first = 0;
                while (first < w && row[first] <= 1) first++;
                if (first == w) continue;
                int last = w - 1;
                while (row[last] <= 1) last--;
                x0 = Math.min(x0, first);
                x1 = Math.max(x1, last + 1);
                y0 = Math.min(y0, y);
                y1 = Math.max(y1, y + 1);
            }
        }
        return x1 == 0 ? null : new int[]{x0, y0, x1, y1};
    }

    /**
     * {@link #contentBox} grown by {@code gutter} source pixels on every side,
     * snapped outwards to {@link #ALIGN} and kept inside the {@code w × h}
     * frame: {@code {x, y, width, height}}.
     */
    static int[] cropBox(int[] content, int gutter, int w, int h) {
        int x0 = Math.max(0, Math.floorDiv(content[0] - gutter, ALIGN) * ALIGN);
        int y0 = Math.max(0, Math.floorDiv(content[1] - gutter, ALIGN) * ALIGN);
        int x1 = Math.min(w, -Math.floorDiv(-(content[2] + gutter), ALIGN) * ALIGN);
        int y1 = Math.min(h, -Math.floorDiv(-(content[3] + gutter), ALIGN) * ALIGN);
        return new int[]{x0, y0, x1 - x0, y1 - y0};
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
     * Decode a sheet: slice it using the profile's frame size, crop every
     * frame to the box they all fit in, optionally shrink them by {@code
     * scale} (the "sprite resolution" setting), and repack to fit {@code
     * maxTexture}. A sheet with nothing visible in any frame keeps its frame
     * count but shrinks to one texel a frame.
     */
    public static SheetImage decode(BufferedImage sheet, int expectedW, int expectedH,
                                    double scale, int maxTexture, boolean pixelArt) {
        int[] size = frameSize(sheet.getWidth(), sheet.getHeight(), expectedW, expectedH);
        List<BufferedImage> frames = slice(sheet, size[0], size[1]);
        int[] content = contentBox(frames);
        int[] crop;
        List<BufferedImage> kept = new ArrayList<>(frames.size());
        if (content == null) {
            crop = new int[]{0, 0, 0, 0};
            BufferedImage blank = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < frames.size(); i++) kept.add(blank);
        } else {
            double s = Math.max(0.05, Math.min(1, scale));
            crop = cropBox(content, (int) Math.ceil(GUTTER / s), size[0], size[1]);
            for (BufferedImage f : frames) kept.add(f.getSubimage(crop[0], crop[1], crop[2], crop[3]));
        }
        return pack(kept, scale, maxTexture, pixelArt, size[0], size[1], crop);
    }

    /** Pack same-sized frames into a near-square grid, shrinking if they cannot fit. */
    public static SheetImage pack(List<BufferedImage> frames, double scale, int maxTexture,
                                  boolean pixelArt) {
        if (frames.isEmpty()) throw new IllegalArgumentException("a sheet needs at least one frame");
        int w = frames.get(0).getWidth(), h = frames.get(0).getHeight();
        return pack(frames, scale, maxTexture, pixelArt, w, h, new int[]{0, 0, w, h});
    }

    /** {@link #pack}, for frames that are the {@code crop} part of {@code sourceW × sourceH} ones. */
    private static SheetImage pack(List<BufferedImage> frames, double scale, int maxTexture,
                                   boolean pixelArt, int sourceW, int sourceH, int[] crop) {
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
        return new SheetImage(atlas, fw, fh, n, cols, pixelArt, sourceW, sourceH, crop);
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
