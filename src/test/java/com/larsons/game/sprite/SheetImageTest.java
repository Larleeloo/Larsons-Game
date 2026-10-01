package com.larsons.game.sprite;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SheetImageTest {

    /** A sheet of {@code cols × rows} cells with the first {@code filled} cells painted. */
    static BufferedImage sheet(int frame, int cols, int rows, int filled) {
        BufferedImage img = new BufferedImage(cols * frame, rows * frame, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int i = 0; i < filled; i++) {
            g.setColor(new Color(10 + i, 100, 200));
            g.fillRect((i % cols) * frame + 2, (i / cols) * frame + 2, frame - 4, frame - 4);
        }
        g.dispose();
        return img;
    }

    @Test
    void aPaletteSheetKeepsItsIndicesAndItsPalette() {
        // entries 2 and 3 are the same colour - two labels that happen to match
        byte[] r = {0, 0, 50, 50, 9}, g = {0, 0, 60, 60, 8}, b = {0, 0, 70, 70, 7};
        byte[] a = {0, 1, (byte) 255, (byte) 255, (byte) 255};
        IndexColorModel cm = new IndexColorModel(8, 5, r, g, b, a);
        BufferedImage img = new BufferedImage(16, 8, BufferedImage.TYPE_BYTE_INDEXED, cm);
        img.getRaster().setSample(3, 3, 0, 2);
        img.getRaster().setSample(4, 3, 0, 3);
        img.getRaster().setSample(8 + 2, 2, 0, 4);
        SheetImage s = SheetImage.decode(img, 8, 8, 1.0, 4096, true);
        assertTrue(s.indexed());
        assertEquals(2, s.frameCount());
        int[] crop = s.crop();
        int w = s.atlas().getWidth();
        byte[] idx = s.indices();
        assertEquals(2, idx[(3 - crop[1]) * w + 3 - crop[0]]);
        assertEquals(3, idx[(3 - crop[1]) * w + 4 - crop[0]], "the same colour, but its own label");
        int f1 = (1 % s.columns()) * s.frameWidth(), r1 = (1 / s.columns()) * s.frameHeight();
        assertEquals(4, idx[(r1 + 2 - crop[1]) * w + f1 + 2 - crop[0]]);
        assertEquals(0xFF323C46, s.palette()[2]);
        assertEquals(0, s.palette()[0] >>> 24, "entry 0 transparent");
        assertFalse(SheetImage.decode(img, 8, 8, 1.0, 4096, false).indexed(),
                "a render that is not pixel art is decoded to colours");
    }

    @Test
    void frameSizeInference() {
        assertArrayEquals(new int[]{512, 512}, SheetImage.frameSize(15360, 512, 512, 512));
        assertArrayEquals(new int[]{512, 512}, SheetImage.frameSize(3072, 2560, 512, 512));
        assertArrayEquals(new int[]{32, 32}, SheetImage.frameSize(960, 32, 512, 512), "a strip of small squares");
        assertArrayEquals(new int[]{100, 70}, SheetImage.frameSize(100, 70, 512, 512), "one odd frame");
    }

    @Test
    void emptyTrailingCellsAreNotFrames() {
        // 30 frames in a 6×6 grid: six empty cells at the end.
        List<BufferedImage> frames = SheetImage.slice(sheet(16, 6, 6, 30), 16, 16);
        assertEquals(30, frames.size());
    }

    @Test
    void packingFitsTheTextureLimitAndKeepsOrder() {
        BufferedImage strip = sheet(64, 30, 1, 30);       // 1920 wide
        SheetImage s = SheetImage.decode(strip, 64, 64, 1.0, 512, false);
        assertEquals(30, s.frameCount());
        assertTrue(s.atlas().getWidth() <= 512 && s.atlas().getHeight() <= 512);
        // Frame 7 is still frame 7: same red channel as painted.
        int col = 7 % s.columns(), row = 7 / s.columns();
        int px = s.atlas().getRGB(col * 64 + 32, row * 64 + 32);
        assertEquals(10 + 7, (px >> 16) & 0xFF);
    }

    @Test
    void resolutionSettingShrinksFrames() {
        SheetImage s = SheetImage.decode(sheet(128, 4, 1, 4), 128, 128, 0.5, 4096, false);
        assertEquals(64, s.frameWidth());
        assertEquals(4, s.frameCount());
    }

    @Test
    void anOversizedSheetShrinksRatherThanFails() {
        SheetImage s = SheetImage.decode(sheet(256, 20, 1, 20), 256, 256, 1.0, 1024, false);
        assertEquals(20, s.frameCount());
        assertTrue(s.atlas().getWidth() <= 1024 && s.atlas().getHeight() <= 1024);
    }

    // --- cropping ----------------------------------------------------------------------

    /**
     * Three 512-pixel frames, the way the renders come: something small in the
     * first and last, the middle one blank but for the 1/255 marker pixel.
     */
    static BufferedImage smallItem() {
        BufferedImage img = new BufferedImage(3 * 512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 30, 40));
        g.fillRect(200, 300, 40, 30);
        g.fillRect(2 * 512 + 220, 310, 40, 30);
        g.dispose();
        img.setRGB(512, 0, 0x01000000);
        return img;
    }

    /** A frame-sized picture with detail everywhere in a box, so a shift of one pixel shows. */
    static BufferedImage detailed(int frame, int frames, int x0, int y0, int x1, int y1) {
        BufferedImage img = new BufferedImage(frames * frame, frame, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < frames; f++) {
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int a = 60 + (x * 7 + y * 13 + f * 31) % 196;
                    int rgb = ((x * 5 + f) & 0xFF) << 16 | ((y * 3) & 0xFF) << 8 | ((x ^ y) & 0xFF);
                    img.setRGB(f * frame + x, y, a << 24 | rgb);
                }
            }
        }
        return img;
    }

    @Test
    void aSheetKeepsOnlyTheBoxItsFramesCover() {
        SheetImage s = SheetImage.decode(smallItem(), 512, 512, 1.0, 4096, false);
        assertEquals(3, s.frameCount(), "the blank middle frame is still a frame");
        // Painted: x 200-260, y 300-340. Plus the gutter, snapped out to 8.
        assertArrayEquals(new int[]{192, 288, 80, 64}, s.crop());
        assertEquals(80, s.frameWidth());
        assertEquals(64, s.frameHeight());
        assertArrayEquals(new double[]{192 / 512.0, 288 / 512.0, 272 / 512.0, 352 / 512.0},
                s.region(false), 1e-12);
        // Frame 0's pixel (205, 305) is at (13, 17) of its cell; frame 1 is empty.
        assertEquals(0xFFC81E28, s.atlas().getRGB(13, 17));
        int c1 = 1 % s.columns() * 80, r1 = 1 / s.columns() * 64;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 80; x++) assertEquals(0, s.atlas().getRGB(c1 + x, r1 + y) >>> 24);
        }
    }

    @Test
    void theGutterIsTransparent() {
        SheetImage s = SheetImage.decode(smallItem(), 512, 512, 1.0, 4096, false);
        int[] c = s.crop();
        for (int i = 0; i < s.frameCount(); i++) {
            int cx = i % s.columns() * s.frameWidth(), cy = i / s.columns() * s.frameHeight();
            for (int y = 0; y < c[3]; y++) {
                for (int x = 0; x < c[2]; x++) {
                    boolean edge = x < SheetImage.GUTTER || y < SheetImage.GUTTER
                            || x >= c[2] - SheetImage.GUTTER || y >= c[3] - SheetImage.GUTTER;
                    if (edge) assertEquals(0, s.atlas().getRGB(cx + x, cy + y) >>> 24, "frame " + i);
                }
            }
        }
    }

    @Test
    void aBlankSheetKeepsItsFramesButNoPixels() {
        BufferedImage img = new BufferedImage(4 * 512, 512, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < 4; f++) img.setRGB(f * 512, 0, 0x01000000); // the renders' marker
        SheetImage s = SheetImage.decode(img, 512, 512, 1.0, 4096, false);
        assertEquals(4, s.frameCount());
        double[] r = s.region(false);
        assertEquals(r[0], r[2], "nothing to draw");
        assertTrue(s.atlas().getWidth() * s.atlas().getHeight() <= 4);
    }

    @Test
    void cropBoxSnapsOutwardsAndStaysInTheFrame() {
        assertArrayEquals(new int[]{0, 0, 24, 512}, SheetImage.cropBox(new int[]{3, 0, 10, 512}, 8, 512, 512));
        assertArrayEquals(new int[]{488, 96, 24, 32}, SheetImage.cropBox(new int[]{500, 104, 511, 120}, 8, 512, 512));
        assertArrayEquals(new int[]{0, 0, 100, 70}, SheetImage.cropBox(new int[]{1, 1, 99, 69}, 8, 100, 70),
                "an odd-sized frame is clamped, not overrun");
    }

    @Test
    void mirroredRegionIsTheBoxFlippedLeftToRight() {
        SheetImage s = SheetImage.decode(smallItem(), 512, 512, 1.0, 4096, false);
        double[] r = s.region(false), m = s.region(true);
        assertEquals(1 - r[2], m[0], 1e-12);
        assertEquals(1 - r[0], m[2], 1e-12);
        assertEquals(r[1], m[1]);
        assertEquals(r[3], m[3]);
    }

    /**
     * At Half and Quarter resolution the cropped cells hold exactly the
     * texels the whole frames would have had there, so a cropped layer lands
     * on the same pixels as before and still lines up with the body.
     */
    @Test
    void shrunkCropsMatchTheShrunkWholeFrames() {
        BufferedImage img = detailed(512, 2, 137, 221, 301, 389);
        for (double scale : new double[]{0.5, 0.25}) {
            SheetImage cropped = SheetImage.decode(img, 512, 512, scale, 4096, false);
            SheetImage whole = SheetImage.pack(SheetImage.slice(img, 512, 512), scale, 4096, false);
            int k = (int) Math.round(1 / scale);
            int[] c = cropped.crop();
            assertEquals(0, c[0] % k);
            assertEquals(0, c[1] % k);
            for (int f = 0; f < 2; f++) {
                int cx = f % cropped.columns() * cropped.frameWidth();
                int cy = f / cropped.columns() * cropped.frameHeight();
                int wx = f % whole.columns() * whole.frameWidth() + c[0] / k;
                int wy = f / whole.columns() * whole.frameHeight() + c[1] / k;
                for (int y = 0; y < cropped.frameHeight(); y++) {
                    for (int x = 0; x < cropped.frameWidth(); x++) {
                        assertEquals(whole.atlas().getRGB(wx + x, wy + y), cropped.atlas().getRGB(cx + x, cy + y),
                                "scale " + scale + " frame " + f + " at " + x + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void theFallbackFramesAreNotCropped() {
        SheetImage s = SheetImage.ofFrames(SheetImage.slice(sheet(32, 4, 1, 4), 32, 32), true, 4096);
        assertEquals(32, s.frameWidth());
        assertArrayEquals(new double[]{0, 0, 1, 1}, s.region(false));
    }
}
