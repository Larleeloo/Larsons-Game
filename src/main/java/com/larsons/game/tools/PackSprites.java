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
 *
 * <pre>
 *   PackSprites &lt;assets folder&gt; &lt;pack file&gt; &lt;part size in MB&gt;
 * </pre>
 *
 * With a part size the pack is written in parts of that size instead -
 * {@code sprites.pack.001}, {@code .002} ... and {@code sprites.pack.parts},
 * the list of them - which the game joins into {@code sprites.pack} the
 * first time it starts ({@link SpritePack#joinParts}). A 4 GB file inside a
 * zip is more than Windows' own unzip can be trusted with; 1 GB parts are not.
 */
public final class PackSprites {

    private PackSprites() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2 && args.length != 3) {
            System.err.println("usage: PackSprites <assets folder> <pack file> [part size in MB]");
            System.exit(2);
        }
        Path assets = Path.of(args[0]);
        Path out = Path.of(args[1]);
        long partBytes = args.length == 3 ? Long.parseLong(args[2].trim()) * 1_000_000L : 0;
        long t0 = System.nanoTime();
        Stats s = pack(assets, out, partBytes);
        System.out.printf("[pack] %,d sheets and files (%.2f GB) in %s%s - %.2f GB, %.0f s%n",
                s.files(), s.bytes() / 1e9, out, s.parts() > 0 ? " (" + s.parts() + " parts)" : "",
                s.packBytes() / 1e9, (System.nanoTime() - t0) / 1e9);
    }

    /** What was packed: files and their bytes; the pack's size, and the parts it is in (0: one file). */
    public record Stats(long files, long bytes, long packBytes, int parts) {}

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

    /** The pack as one file. */
    public static Stats pack(Path assets, Path out) throws IOException {
        return pack(assets, out, 0);
    }

    /** The pack as one file ({@code partBytes} 0), or in parts of {@code partBytes} each. */
    public static Stats pack(Path assets, Path out, long partBytes) throws IOException {
        List<Path> folders = folders(assets);
        if (folders.stream().noneMatch(p -> p.getFileName().toString().equals("sprites"))) {
            throw new IOException("no sprites folder in " + assets.toAbsolutePath());
        }
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        removeOld(out);
        Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        long files = 0, bytes = 0;
        CRC32 crc = new CRC32();
        Parts parts = partBytes > 0 ? new Parts(out, partBytes) : null;
        try (OutputStream os = new BufferedOutputStream(parts != null ? parts : Files.newOutputStream(tmp), 1 << 20);
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
        if (parts != null) {
            parts.writeList();
            return new Stats(files, bytes, parts.total(), parts.sizes.size());
        }
        Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new Stats(files, bytes, Files.size(out), 0);
    }

    /** A pack (and its parts and their list) from an earlier run. */
    private static void removeOld(Path out) throws IOException {
        Path dir = out.toAbsolutePath().getParent();
        String name = out.getFileName().toString();
        try (Stream<Path> list = Files.list(dir)) {
            for (Path p : list.toList()) {
                String n = p.getFileName().toString();
                if (n.equals(name) || n.startsWith(name + ".")) Files.delete(p);
            }
        }
    }

    /**
     * Writes one stream as {@code <pack>.001}, {@code .002} ... of {@code size}
     * bytes each (the last one shorter), and lists them in {@code
     * <pack>.parts} ({@link SpritePack#PARTS_SUFFIX}).
     */
    private static final class Parts extends OutputStream {
        private final Path pack;
        private final long size;
        private final List<Long> sizes = new ArrayList<>();
        private OutputStream out;
        private long written;

        Parts(Path pack, long size) {
            this.pack = pack;
            this.size = size;
        }

        private Path part(int i) {
            return SpritePack.part(pack, i);
        }

        private void next() throws IOException {
            if (out != null) {
                out.close();
                sizes.add(written);
            }
            out = Files.newOutputStream(part(sizes.size() + 1));
            written = 0;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                if (out == null || written == size) next();
                int n = (int) Math.min(len, size - written);
                out.write(b, off, n);
                written += n;
                off += n;
                len -= n;
            }
        }

        @Override
        public void close() throws IOException {
            if (out != null) {
                out.close();
                sizes.add(written);
                out = null;
            }
        }

        long total() {
            return sizes.stream().mapToLong(Long::longValue).sum();
        }

        void writeList() throws IOException {
            StringBuilder sb = new StringBuilder("# The parts of " + pack.getFileName()
                    + ", joined into it in this order the first time the game starts: file, bytes.\n");
            for (int i = 0; i < sizes.size(); i++) {
                sb.append(part(i + 1).getFileName()).append(' ').append(sizes.get(i)).append('\n');
            }
            Files.writeString(SpritePack.partsList(pack), sb.toString());
        }
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
