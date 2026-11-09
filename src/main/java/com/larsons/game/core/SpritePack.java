package com.larsons.game.core;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The sprite sheets of a shared copy of the game, in one file:
 * {@code assets/sprites.pack}.
 *
 * <p>The sprites folders hold some two million small PNGs, which no one wants
 * to unzip (Windows' own unzip would take hours over them). So the copy made
 * for sharing ({@code ./gradlew shareZip}) keeps {@code sprites/}, every
 * body's {@code sprites_<body>/} and {@code animations_v1/} in a single
 * uncompressed zip - the files as they are, under the same names
 * ({@code tools/PackSprites}) - and the game reads them out of it in place,
 * through Java's zip file system: a sheet in the pack is an ordinary {@link
 * Path} ({@code /sprites/hair/bob_px128/walk_middle_ne.png}) that {@code
 * Files} lists and reads like any other.
 *
 * <p>A real {@code assets/sprites/} folder always wins, so the repository
 * (and a copy someone unpacked by hand) is read exactly as before. The pack
 * is read-only by use: nothing writes into it (dropped sprite sheets are not
 * imported while it is in use), and it is never closed, which is the one
 * thing that would make the zip file system write it back.
 */
public final class SpritePack {

    /** The pack's file name, in the assets folder. */
    public static final String FILE = "sprites.pack";

    /** Every pack opened, by its file: opened once, kept open. */
    private static final Map<Path, FileSystem> OPEN = new HashMap<>();

    private SpritePack() {}

    /**
     * The folder the sprites folders are in: {@code assets} itself when it has
     * a {@code sprites/} folder (or no pack), else the root of the pack in it.
     */
    public static synchronized Path rootFor(Path assets) {
        Path pack = assets.resolve(FILE);
        if (Files.isDirectory(assets.resolve("sprites")) || !Files.isRegularFile(pack)) return assets;
        try {
            Path key = pack.toAbsolutePath().normalize();
            FileSystem fs = OPEN.get(key);
            if (fs == null) {
                long t0 = System.nanoTime();
                fs = FileSystems.newFileSystem(key, Map.of());
                OPEN.put(key, fs);
                System.out.printf("[sprites] reading the sprite sheets from %s (%.1f GB, opened in %.1f s)%n",
                        key, Files.size(key) / 1e9, (System.nanoTime() - t0) / 1e9);
            }
            return fs.getPath("/");
        } catch (IOException | RuntimeException e) {
            System.err.println("[sprites] cannot open " + pack + ": " + e);
            return assets;
        }
    }

    /** Whether {@code p} is inside the pack (or any other file system than the disk's). */
    public static boolean isPacked(Path p) {
        return p != null && p.getFileSystem() != FileSystems.getDefault();
    }
}
