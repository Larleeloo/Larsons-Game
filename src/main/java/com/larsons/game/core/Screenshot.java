package com.larsons.game.core;

import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.lwjgl.opengl.GL33C.*;

/** Reads the back buffer and writes it as a PNG (F12, and the scripted runs). */
public final class Screenshot {

    private Screenshot() {}

    /** {@code screenshots/<timestamp>.png}. */
    public static Path defaultPath() {
        return Path.of("screenshots",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")) + ".png");
    }

    /** Capture the current framebuffer. Call after rendering, before swapping. */
    public static Path capture(int width, int height, Path file) {
        ByteBuffer buf = MemoryUtil.memAlloc(width * height * 4);
        try {
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glReadBuffer(GL_BACK);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = ((height - 1 - y) * width + x) * 4;
                    int r = buf.get(i) & 0xFF, g = buf.get(i + 1) & 0xFF, b = buf.get(i + 2) & 0xFF;
                    img.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            ImageIO.write(img, "png", file.toFile());
            return file;
        } catch (IOException e) {
            System.err.println("[screenshot] " + e.getMessage());
            return null;
        } finally {
            MemoryUtil.memFree(buf);
        }
    }
}
