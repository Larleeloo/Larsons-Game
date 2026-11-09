package com.larsons.game.tools;

import com.larsons.game.core.SpritePack;
import com.larsons.game.sprite.SpriteLibrary;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Puts the sprites folders of an assets folder into one {@code sprites.pack}
 * for a shared copy of the game ({@link SpritePack}; {@code ./gradlew shareZip}
 * runs it):
 *
 * <pre>
 *   PackSprites &lt;assets folder&gt; &lt;pack file&gt;
 * </pre>
 *
 * It takes {@code sprites/}, every {@code sprites_<body>/} and the classic
 * animations ({@code animations_v1/}), and stores every file as it is, under
 * the same path - uncompressed, since PNGs are compressed already and a
 * stored file is read straight out of the pack. Git's {@code .gitkeep}
 * placeholders and the desktop's own files are left out.
 */
public final class PackSprites {

    private PackSprites() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: PackSprites <assets folder> <pack file>");
            System.exit(2);
        }
        Path assets = Path.of(args[0]);
        Path out = Path.of(args[1]);
        long t0 = System.nanoTime();
        Stats s = pack(assets, out);
        System.out.printf("[pack] %,d sheets and files (%.2f GB) in %s - %.2f GB, %.0f s%n",
                s.files(), s.bytes() / 1e9, out, Files.size(out) / 1e9, (System.nanoTime() - t0) / 1e9);
    }

    public record Stats(long files, long bytes) {}

    /** The folders of {@code assets} that go in the pack, sorted. */
    public static List<Path> folders(Path assets) throws IOException {
        List<Path> folders = new ArrayList<>();
        try (Stream<Path> list = Files.list(assets)) {
            for (Path p : list.sorted().toList()) {
                String name = p.getFileName().toString();
                boolean sprites = name.equals("sprites") || name.startsWith("sprites_");
                if (Files.isDirectory(p) && (sprites || name.equals(SpriteLibrary.CLASSIC_FOLDER))) folders.add(p);
            }
        }
        return folders;
    }

    public static Stats pack(Path assets, Path out) throws IOException {
        List<Path> folders = folders(assets);
        if (folders.stream().noneMatch(p -> p.getFileName().toString().equals("sprites"))) {
            throw new IOException("no sprites folder in " + assets.toAbsolutePath());
        }
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        long files = 0, bytes = 0;
        CRC32 crc = new CRC32();
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 20);
             ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.setMethod(ZipOutputStream.STORED);
            for (Path folder : folders) {
                List<Path> all;
                try (Stream<Path> walk = Files.walk(folder)) {
                    all = walk.sorted().toList();
                }
                for (Path p : all) {
                    String name = assets.relativize(p).toString().replace('\\', '/');
                    String file = p.getFileName().toString();
                    if (Files.isDirectory(p)) {
                        zip.putNextEntry(stored(name + "/", 0, 0));
                        zip.closeEntry();
                        continue;
                    }
                    if (file.equals(".gitkeep") || file.equals(".DS_Store") || file.equals("Thumbs.db")) continue;
                    byte[] data = Files.readAllBytes(p);
                    crc.reset();
                    crc.update(data);
                    zip.putNextEntry(stored(name, data.length, crc.getValue()));
                    zip.write(data);
                    zip.closeEntry();
                    files++;
                    bytes += data.length;
                    if (files % 250_000 == 0) System.out.printf("[pack] %,d files ...%n", files);
                }
            }
        }
        Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new Stats(files, bytes);
    }

    private static ZipEntry stored(String name, long size, long crc) {
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.STORED);
        e.setSize(size);
        e.setCompressedSize(size);
        e.setCrc(crc);
        e.setTime(0);
        return e;
    }
}
