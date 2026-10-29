package com.larsons.game.sprite;

import com.larsons.game.util.Json;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The scenery's sprites - the trees, the grass tufts, the rocks - and the
 * grass the ground is drawn with, found under {@code assets/scenery/}:
 *
 * <pre>
 *   assets/scenery/ground/grass.png        512 × 512, opaque, seamless when tiled
 *   assets/scenery/ground/&lt;name&gt;.png       the other ground textures, the same size: dirt,
 *                                         forest_floor, gravel, meadow (world/GroundTexture)
 *   assets/scenery/ground/profile.json     {"tileWorldSize": 9.6} - the metres one tile spans
 *   assets/scenery/&lt;sprite&gt;/sheet.png      the frames, left to right then top to bottom
 *   assets/scenery/&lt;sprite&gt;/profile.json   kind, frame size, frames, fps, size, radius, shadow
 *                                         ({@link SceneryProfile})
 * </pre>
 *
 * <p>Every piece of scenery is one picture from one side view - no
 * directions, no heights: a tree or a tuft stood up twice, as two cards
 * crossing at right angles (the way Minecraft draws its flowers), a rock on
 * one card facing the camera ({@link SceneryProfile#facesCamera()}). So each
 * sprite is a single sheet of frames. The sheets are drawn by 3D-Modeling's {@code scenery/} scripts in
 * the game's 128-colour palette, the frames in the near-square grid {@link
 * #gridColumns} gives (48 frames: 8 across, 6 down).
 *
 * <p>This only reads names, the profiles and each sheet's size from its PNG
 * header - no pixels - so it needs no GPU and is quick enough to run at
 * startup ({@code world/SceneryRenderer} uploads the sheets). A folder
 * without a sheet, or whose sheet does not hold its profile's frames, is
 * skipped with a message; no {@code assets/scenery} at all is simply no
 * scenery.
 */
public final class ScenerySprites {

    /** The folder under the assets folder. */
    public static final String FOLDER = "scenery";
    /** The ground's folder (not a sprite). */
    public static final String GROUND = "ground";
    /** A sprite's sheet, in its folder. */
    public static final String SHEET = "sheet.png";
    /** The ground's texture, in {@link #GROUND}. */
    public static final String GRASS = "grass.png";
    /** Metres one ground tile spans when its profile does not say: 512 pixels at the characters' 1.875 cm. */
    public static final double DEFAULT_TILE = 9.6;

    /** No scenery at all. */
    public static final ScenerySprites NONE = new ScenerySprites(null, List.of(), null);

    /**
     * One sprite: its profile (with its frame count settled) and where its
     * frames are in its sheet.
     *
     * @param columns frames across the sheet (its width over the frame's)
     */
    public record Sprite(String name, SceneryProfile profile, Path sheet, int sheetWidth, int sheetHeight,
                         int columns) {

        public SceneryProfile.Kind kind() { return profile.kind(); }

        public int frames() { return profile.frames(); }

        /** Rows of frames down the sheet. */
        public int rows() { return sheetHeight / profile.frameHeight(); }

        /**
         * Frame {@code frame}'s texture coordinates in the sheet, {@code {u0,
         * v0, u1, v1}} from its top-left corner to its bottom-right, pulled in
         * by a sliver of a texel so nearest-neighbour sampling at the card's
         * very edge never picks up the next frame. Any frame number is wrapped
         * into the loop.
         */
        public float[] uv(int frame) {
            int f = Math.floorMod(frame, Math.max(1, frames()));
            int col = f % columns, row = f / columns;
            double fw = profile.frameWidth(), fh = profile.frameHeight();
            double inset = 1 / 64.0;
            return new float[]{
                    (float) ((col * fw + inset) / sheetWidth), (float) ((row * fh + inset) / sheetHeight),
                    (float) (((col + 1) * fw - inset) / sheetWidth), (float) (((row + 1) * fh - inset) / sheetHeight)};
        }

        /** The frame showing {@code seconds} into its loop. */
        public int frameAt(double seconds) {
            if (frames() <= 1) return 0;
            return Math.floorMod((long) Math.floor(seconds * profile.fps()), frames());
        }
    }

    /**
     * The grass the ground is drawn with, the metres one tile of it spans,
     * and the other ground textures beside it ({@code ground/<name>.png}), by
     * name.
     */
    public record Ground(Path texture, double tileWorldSize, Map<String, Path> tiles) {

        public Ground {
            tiles = Map.copyOf(tiles);
        }

        /** Just grass. */
        public Ground(Path texture, double tileWorldSize) {
            this(texture, tileWorldSize, Map.of());
        }

        /** The ground texture called {@code name} ({@code dirt}, {@code forest_floor} ...), or null. */
        public Path tile(String name) { return tiles.get(name); }
    }

    private final Path root;
    private final List<Sprite> sprites;
    private final Ground ground;

    private ScenerySprites(Path root, List<Sprite> sprites, Ground ground) {
        this.root = root;
        this.sprites = List.copyOf(sprites);
        this.ground = ground;
    }

    /** A catalogue of the given sprites and ground (for tests, and for a layout without files). */
    public static ScenerySprites of(List<Sprite> sprites, Ground ground) {
        return new ScenerySprites(null, sprites, ground);
    }

    /** Everything under {@code folder} ({@code assets/scenery}); {@link #NONE} when it does not exist. */
    public static ScenerySprites scan(Path folder) {
        if (!Files.isDirectory(folder)) return NONE;
        List<Sprite> found = new ArrayList<>();
        Ground ground = null;
        try (Stream<Path> dirs = Files.list(folder)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                String name = dir.getFileName().toString();
                if (name.equals(GROUND)) {
                    ground = groundIn(dir);
                    continue;
                }
                Path sheet = dir.resolve(SHEET);
                if (!Files.isRegularFile(sheet)) {
                    System.err.println("[scenery] " + name + ": no " + SHEET + ", skipped");
                    continue;
                }
                int[] size = imageSize(sheet);
                if (size == null) {
                    System.err.println("[scenery] " + name + ": cannot read " + sheet + ", skipped");
                    continue;
                }
                try {
                    found.add(describe(name, SceneryProfile.load(dir), sheet, size[0], size[1]));
                } catch (IllegalArgumentException e) {
                    System.err.println("[scenery] " + name + ": " + e.getMessage() + ", skipped");
                }
            }
        } catch (IOException e) {
            System.err.println("[scenery] cannot list " + folder + ": " + e.getMessage());
        }
        return new ScenerySprites(folder, found, ground);
    }

    /**
     * A sprite from its profile and its sheet's size: how many frames across
     * the sheet holds, and - when the profile gives no count - that every
     * cell of the grid is a frame. Throws when the sheet is not a whole number
     * of frames each way or holds fewer than the profile's.
     */
    public static Sprite describe(String name, SceneryProfile profile, Path sheet, int sheetWidth, int sheetHeight) {
        int fw = profile.frameWidth(), fh = profile.frameHeight();
        if (sheetWidth < fw || sheetHeight < fh || sheetWidth % fw != 0 || sheetHeight % fh != 0) {
            throw new IllegalArgumentException("the sheet is " + sheetWidth + "x" + sheetHeight
                    + ", not a grid of " + fw + "x" + fh + " frames");
        }
        int columns = sheetWidth / fw, rows = sheetHeight / fh;
        int frames = profile.frames() > 0 ? profile.frames() : columns * rows;
        if (frames > columns * rows) {
            throw new IllegalArgumentException("the sheet holds " + columns * rows + " frames, the profile says "
                    + frames);
        }
        if (columns != gridColumns(frames)) {
            System.err.println("[scenery] " + name + ": " + frames + " frames " + columns + " across, not the "
                    + gridColumns(frames) + " of the usual grid - read as they are");
        }
        return new Sprite(name, profile.withFrames(frames), sheet, sheetWidth, sheetHeight, columns);
    }

    /**
     * Columns of the near-square grid a sheet of {@code n} frames is laid out
     * in: the fewest empty cells, then the shape closest to 1.4 rows' worth of
     * columns a row - exactly {@code grid_columns} of 3D-Modeling's {@code
     * scenery_common.py}, which writes the sheets (48 → 8, 24 → 6, 1 → 1).
     */
    public static int gridColumns(int n) {
        int best = 1;
        long bestWaste = Long.MAX_VALUE;
        double bestShape = Double.MAX_VALUE;
        for (int cols = 1; cols <= Math.max(1, n); cols++) {
            int rows = (n + cols - 1) / cols;
            long waste = (long) cols * rows - n;
            double shape = Math.abs(cols - rows * 1.4);
            if (waste < bestWaste || (waste == bestWaste && shape < bestShape)) {
                best = cols;
                bestWaste = waste;
                bestShape = shape;
            }
        }
        return best;
    }

    private static Ground groundIn(Path dir) {
        Path texture = dir.resolve(GRASS);
        if (!Files.isRegularFile(texture)) {
            System.err.println("[scenery] ground: no " + GRASS + " - the plain floor instead");
            return null;
        }
        double tile = DEFAULT_TILE;
        Path profile = dir.resolve(SceneryProfile.FILE_NAME);
        if (Files.isRegularFile(profile)) {
            try {
                tile = Json.num(Json.asObject(Json.parse(Files.readString(profile, StandardCharsets.UTF_8))),
                        "tileWorldSize", DEFAULT_TILE);
            } catch (IOException | RuntimeException e) {
                System.err.println("[scenery] ignoring " + profile + ": " + e.getMessage());
            }
        }
        Map<String, Path> tiles = new TreeMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                String n = f.getFileName().toString();
                if (n.endsWith(".png") && !n.equals(GRASS)) tiles.put(n.substring(0, n.length() - 4), f);
            }
        } catch (IOException e) {
            System.err.println("[scenery] cannot list " + dir + ": " + e.getMessage());
        }
        return new Ground(texture, tile > 0 ? tile : DEFAULT_TILE, tiles);
    }

    /** Width and height from an image's header, without decoding its pixels; null if it cannot be read. */
    static int[] imageSize(Path file) {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader r = readers.next();
            try {
                r.setInput(in);
                return new int[]{r.getWidth(0), r.getHeight(0)};
            } finally {
                r.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /** The folder this was read from (null when built in memory). */
    public Path root() { return root; }

    /** Every sprite found, by folder name. */
    public List<Sprite> sprites() { return sprites; }

    /** The sprite in folder {@code name}, or null. */
    public Sprite sprite(String name) {
        for (Sprite s : sprites) if (s.name().equals(name)) return s;
        return null;
    }

    /** The sprites of one kind, by folder name. */
    public List<Sprite> ofKind(SceneryProfile.Kind kind) {
        List<Sprite> out = new ArrayList<>();
        for (Sprite s : sprites) if (s.kind() == kind) out.add(s);
        return Collections.unmodifiableList(out);
    }

    /** The ground's grass, or null for the plain floor. */
    public Ground ground() { return ground; }

    public boolean isEmpty() { return sprites.isEmpty() && ground == null; }
}
