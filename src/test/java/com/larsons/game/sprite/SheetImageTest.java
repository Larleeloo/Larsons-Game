package com.larsons.game.sprite;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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
}
