package com.larsons.game.core;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * <p>The shared zip carries the pack in parts of about 1 GB ({@code
 * sprites.pack.001}, {@code .002} ... listed in {@code sprites.pack.parts}):
 * a single multi-gigabyte file is the one thing Windows' own unzip cannot be
 * trusted with. The first start puts them back together ({@link #joinParts})
 * - part 1 renamed, the others appended to it and deleted one by one, so it
 * takes no more than a part's worth of extra disk and carries on where it
 * left off if it was stopped.
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

    /** The list of a pack's parts: {@code sprites.pack.parts}. */
    public static final String PARTS_SUFFIX = ".parts";

    /** The pack being put together from its parts. */
    static final String JOINING_SUFFIX = ".joining";

    /** Every pack opened, by its file: opened once, kept open. */
    private static final Map<Path, FileSystem> OPEN = new HashMap<>();

    private SpritePack() {}

    /** Part {@code i} (from 1) of {@code pack}: {@code sprites.pack.001}. */
    public static Path part(Path pack, int i) {
        return pack.resolveSibling(String.format("%s.%03d", pack.getFileName(), i));
    }

    /** The list of {@code pack}'s parts: {@code sprites.pack.parts}. */
    public static Path partsList(Path pack) {
        return pack.resolveSibling(pack.getFileName() + PARTS_SUFFIX);
    }

    /**
     * The folder the sprites folders are in: {@code assets} itself when it has
     * a {@code sprites/} folder (or no pack), else the root of the pack in it -
     * put together from its parts first, if that has not happened yet.
     */
    public static synchronized Path rootFor(Path assets) {
        Path pack = assets.resolve(FILE);
        if (Files.isDirectory(assets.resolve("sprites"))) return assets;
        Path key = pack.toAbsolutePath().normalize();
        if (!OPEN.containsKey(key) && Files.isRegularFile(partsList(pack))) joinParts(assets, System.out);
        if (!Files.isRegularFile(pack)) return assets;
        try {
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

    /** One part as its list gives it: file name and length. */
    record Part(String name, long bytes) {}

    /** The parts {@code list} names, in order. */
    static List<Part> readList(Path list) throws IOException {
        List<Part> parts = new ArrayList<>();
        for (String line : Files.readAllLines(list)) {
            String l = line.strip();
            if (l.isEmpty() || l.startsWith("#")) continue;
            int space = l.lastIndexOf(' ');
            if (space < 0) throw new IOException("not a part: \"" + l + "\" in " + list);
            parts.add(new Part(l.substring(0, space).strip(), Long.parseLong(l.substring(space + 1))));
        }
        if (parts.isEmpty()) throw new IOException("no parts listed in " + list);
        return parts;
    }

    /**
     * Puts {@code assets/sprites.pack} together from the parts its list names,
     * when there is a list (it goes once they are joined); says what it does
     * on {@code out}. Parts win over a pack already there - a newer copy of
     * the game unzipped over an older one. True when the pack is there
     * afterwards.
     */
    public static synchronized boolean joinParts(Path assets, PrintStream out) {
        Path pack = assets.resolve(FILE);
        Path list = partsList(pack);
        if (!Files.isRegularFile(list)) return Files.isRegularFile(pack);
        Path joining = pack.resolveSibling(FILE + JOINING_SUFFIX);
        try {
            List<Part> parts = readList(list);
            Path first = pack.resolveSibling(parts.get(0).name());
            if (!Files.exists(first) && !Files.exists(joining) && Files.isRegularFile(pack)) {
                Files.delete(list); // joined before, all but deleting the list
                return true;
            }
            long total = parts.stream().mapToLong(Part::bytes).sum();
            long t0 = System.nanoTime();
            out.printf("[sprites] putting the sprite sheets together from %d parts (%.1f GB) - only the first time"
                    + " the game starts, and it may take a minute%n", parts.size(), total / 1e9);
            if (Files.exists(first)) {
                // new parts: whatever was there before goes
                Files.deleteIfExists(pack);
                Files.deleteIfExists(joining);
                need(first, parts.get(0).bytes());
                // part 1 becomes the pack: a rename, however big it is
                Files.move(first, joining);
            } else if (!Files.exists(joining)) {
                need(first, parts.get(0).bytes()); // says it is missing
            }
            long offset = parts.get(0).bytes();
            try (FileChannel to = FileChannel.open(joining, StandardOpenOption.WRITE)) {
                for (int i = 1; i < parts.size(); i++) {
                    Part p = parts.get(i);
                    Path from = pack.resolveSibling(p.name());
                    if (Files.exists(from)) {
                        need(from, p.bytes());
                        // the part may have been half appended when the game was stopped
                        to.truncate(offset);
                        to.position(offset);
                        try (FileChannel in = FileChannel.open(from, StandardOpenOption.READ)) {
                            long done = 0;
                            while (done < p.bytes()) done += in.transferTo(done, p.bytes() - done, to);
                        }
                        to.force(true);
                        Files.delete(from);
                    } else if (to.size() < offset + p.bytes()) {
                        throw new IOException(p.name() + " is missing (the game's folder is not complete:"
                                + " unzip it again)");
                    }
                    offset += p.bytes();
                    out.printf("[sprites]   %d of %d parts%n", i + 1, parts.size());
                }
                to.truncate(total);
            }
            if (Files.size(joining) != total) {
                throw new IOException(joining + " is " + Files.size(joining) + " bytes, not " + total);
            }
            Files.move(joining, pack, StandardCopyOption.ATOMIC_MOVE);
            Files.delete(list);
            out.printf("[sprites] the sprite sheets are ready (%.1f s)%n", (System.nanoTime() - t0) / 1e9);
            return true;
        } catch (IOException | RuntimeException e) {
            out.println("[sprites] could not put the sprite sheets together: " + e.getMessage());
            long free = freeBytes(assets);
            if (free >= 0 && free < 2_000_000_000L) {
                out.printf("[sprites] the disk has only %.1f GB free; free up some space and start the game again%n",
                        free / 1e9);
            }
            return false;
        }
    }

    /** {@code part} is there, as long as its list says. */
    private static void need(Path part, long bytes) throws IOException {
        if (!Files.isRegularFile(part)) {
            throw new IOException(part.getFileName() + " is missing (the game's folder is not complete: unzip it again)");
        }
        long size = Files.size(part);
        if (size != bytes) {
            throw new IOException(part.getFileName() + " is " + size + " bytes, not " + bytes
                    + " (it did not unzip completely: unzip the game again)");
        }
    }

    private static long freeBytes(Path dir) {
        try {
            return Files.getFileStore(dir.toAbsolutePath()).getUsableSpace();
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }
}
