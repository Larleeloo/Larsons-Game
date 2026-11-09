package com.larsons.game.gfx;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reading a PNG from a {@link Path} on any file system: a file in a folder on
 * disk, or one inside the shared copy's {@code assets/sprites.pack}
 * ({@code core/SpritePack}), which {@code Path.toFile()} cannot reach.
 */
public final class Images {

    private Images() {}

    /** The image, or null when ImageIO has no reader for it (as {@link ImageIO#read}). */
    public static BufferedImage read(Path file) throws IOException {
        if (file.getFileSystem() == FileSystems.getDefault()) return ImageIO.read(file.toFile());
        // From memory: ImageIO.read(InputStream) would copy every sheet into a
        // temporary file first (its cache), and sheets are a few kilobytes.
        byte[] bytes = Files.readAllBytes(file);
        return ImageIO.read(new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes)));
    }
}
