package com.larsons.game.sprite;

import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Texture;
import com.larsons.game.sprite.fallback.FallbackSprites;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Every sprite sheet the game can draw: the ones on disk under
 * {@code assets/sprites/}, and the generated fallback character.
 *
 * <h2>On disk</h2>
 * <pre>
 *   assets/sprites/&lt;slot&gt;/&lt;item&gt;/&lt;state&gt;_&lt;elevation&gt;_&lt;direction&gt;.png
 *   assets/sprites/&lt;slot&gt;/&lt;item&gt;/profile.json          (optional framing)
 *   assets/sprites/items/&lt;item id&gt;/icon.png                (a pickup lying in the world)
 * </pre>
 * {@code <slot>} is a {@link Slot#key()} ({@code body}, {@code shirt},
 * {@code carry_right} …) and {@code <item>} any name: {@code body/hero},
 * {@code hat/straw_hat}, {@code carry_right/sword}. The folder is scanned at
 * start and after every import; names are read with {@link SpriteNames}, so a
 * file dropped in by hand does not have to be in the canonical form.
 *
 * <h2>Bodies</h2>
 * Every item has to be drawn for the body that wears it, so a body can have a
 * sprites folder of its own beside this one, named after it:
 * {@code assets/sprites_masculine/} holds {@code body/masculine/} and every
 * item drawn on that body, under the same slot and item names as here. The
 * body a wardrobe wears decides which folder all of its items are looked up
 * in ({@link #rootOf}); a body that has no folder of its own (the ones in this
 * folder's {@code body/}, or none) uses this one. The bodies of every folder
 * are listed together under {@link Slot#BODY}, so a wardrobe can switch body.
 *
 * <h2>On the GPU</h2>
 * A full character is 6 states × 24 views of 512-pixel frames — gigabytes if it
 * were all resident at once. So sheets are loaded <b>on demand</b>, decoded
 * on worker threads, cropped to the part of the frame they use ({@link
 * SheetImage}), uploaded within a couple of milliseconds a frame (so loading
 * never costs a 120 fps frame its deadline), and evicted least-recently-used
 * once the video-memory budget ({@code -Dlarsons.sprites.vramMB}, default
 * 1536) is exceeded. What the camera is looking at always loads; what it may
 * look at next is {@linkplain #prefetch prefetched} only into room the budget
 * has spare. {@code -Dlarsons.sprites.scale=0.5} halves every frame on load
 * for smaller GPUs.
 */
public final class SpriteLibrary implements AutoCloseable {

    /** Folder of world sprites for pickups, beside the slot folders. */
    public static final String ITEMS_FOLDER = "items";

    /** The key of the default sprites folder among the bodies' folders ({@link #rootOf}). */
    public static final String DEFAULT_ROOT = "";

    /**
     * Beside the sprites folders: the first version of the sheets of the states
     * that were animated again ({@code assets/animations_v1/sprites/...},
     * {@code assets/animations_v1/sprites_masculine/...}, the same slot and item
     * folders), kept to compare the two ({@link #setClassic}).
     */
    public static final String CLASSIC_FOLDER = "animations_v1";

    /**
     * One cosmetic (or body) folder and the sheets found in it, and the colours it
     * can take (or null); {@code classic}: the sheets of its first version kept
     * in the archive ({@link #CLASSIC_FOLDER}), for the states drawn again since.
     */
    public record Entry(Slot slot, String name, Path folder, SpriteProfile profile,
                        Map<String, Path> sheets, List<String> skipped, Variants variants,
                        Map<String, Path> classic) {

        public Entry(Slot slot, String name, Path folder, SpriteProfile profile,
                     Map<String, Path> sheets, List<String> skipped, Variants variants) {
            this(slot, name, folder, profile, sheets, skipped, variants, Map.of());
        }

        public Path sheet(AnimState state, Elevation elevation, Facing facing) {
            return sheets.get(key(state, elevation, facing));
        }

        /**
         * The sheet in one animation version: with {@code classic} the first
         * version's where it was kept, else the current one; either way the
         * other where that one has none (a state not drawn again keeps its only
         * sheets, and a state being drawn again shows its first version until
         * the new one is in).
         */
        public Path sheet(AnimState state, Elevation elevation, Facing facing, boolean classic) {
            String k = key(state, elevation, facing);
            Path first = classic ? this.classic.get(k) : sheets.get(k);
            return first != null ? first : (classic ? sheets.get(k) : this.classic.get(k));
        }

        /** How many of the 144 (6 × 3 × 8) sheets are present. */
        public int count() { return sheets.size(); }
    }

    /**
     * A sheet to draw: the file, whether it is drawn mirrored (a twin standing
     * in, or the left-handed character), and the palette swap it is drawn with
     * (null: its own colours). The colours are applied when the layer is drawn
     * ({@link Variants#recolored}), so every colour of a sheet is one texture.
     * A sheet worn under another may be cut by it ({@code cut}: that one's
     * file, or null) - what pokes out from under it is taken out as it loads
     * ({@link SheetImage#cutUnder}).
     */
    public record Resolved(Path file, boolean mirrored, SpriteProfile profile, Variants.Recolor recolor, Path cut) {

        public Resolved(Path file, boolean mirrored, SpriteProfile profile) {
            this(file, mirrored, profile, null, null);
        }

        public Resolved(Path file, boolean mirrored, SpriteProfile profile, Variants.Recolor recolor) {
            this(file, mirrored, profile, recolor, null);
        }

        /** What the library keeps it under: the file (whatever its colours), and what cuts it. */
        public Key key() {
            return new Key(file, cut);
        }

        public Resolved withRecolor(Variants.Recolor r) {
            return new Resolved(file, mirrored, profile, r, cut);
        }

        /** This sheet with what pokes out from under {@code over}'s sheet taken out (null: as it is). */
        public Resolved cutBy(Path over) {
            return new Resolved(file, mirrored, profile, recolor, over);
        }

        public Resolved flipped() {
            return new Resolved(file, !mirrored, profile, recolor, cut);
        }
    }

    /** A decoded sheet's identity: one file, and the file of the sheet it is cut by (null: none). */
    public record Key(Path file, Path cut) {

        public Key(Path file) {
            this(file, null);
        }
    }

    private record Decoded(Key key, int epoch, SheetImage layout, PixelData pixels, String error) {}

    /** Puts a decoded sheet on the GPU. Tests, which have no GL, pass their own. */
    @FunctionalInterface
    interface Uploader {
        SheetTexture upload(SheetImage layout, PixelData pixels, String source);
    }

    private static final Uploader GL = (layout, pixels, source) ->
            new SheetTexture(Texture.upload(pixels, layout.pixelArt()), layout, source);

    /**
     * Uploading a sheet to the GPU is the one thing loading does on the render
     * thread; a frame spends at most this long on it (a frame at 120 fps is
     * 8.3 ms). The first upload of a frame always goes ahead, so loading never
     * stalls; the others only if they are expected to fit ({@link #pump}).
     */
    static final long UPLOAD_BUDGET_NS = 2_000_000L;

    /** The GPU memory a decoded sheet will take. */
    static long bytesOf(SheetImage layout) {
        BufferedImage atlas = layout.atlas();
        return Texture.bytesFor(atlas.getWidth(), atlas.getHeight(), layout.pixelArt(), layout.indexed());
    }

    private final Path root;

    /** The index of one sprites folder: slot -> item folder -> entry. */
    private record Index(Path dir, Map<Slot, Map<String, Entry>> slots) {
        Map<String, Entry> of(Slot slot) { return slots.getOrDefault(slot, Map.of()); }
    }

    /** Every sprites folder (by {@link #rootOf} key) and which folder each body is in. */
    private record Indexes(Map<String, Index> roots, Map<String, String> bodyRoot) {}

    private volatile Indexes index = new Indexes(Map.of(), Map.of());
    private volatile int generation;

    private final ExecutorService workers;
    private final int threads;
    private final Uploader uploader;
    private final LinkedHashMap<Key, SheetTexture> textures = new LinkedHashMap<>(64, 0.75f, true);
    private final Map<Key, CompletableFuture<Void>> pending = new HashMap<>();
    /** The pending loads that are prefetches nobody has asked to draw yet. */
    private final Set<Key> prefetching = new HashSet<>();
    /** Video memory each sheet took the last time it was decoded. */
    private final Map<Key, Long> sizes = new HashMap<>();
    private long sizesTotal;
    private final ConcurrentLinkedQueue<Decoded> decoded = new ConcurrentLinkedQueue<>();
    /** What an upload has been taking, in nanoseconds a byte of video memory (learnt as they happen). */
    private double uploadNsPerByte;
    /** Files that would not decode (in any colours). */
    private final Map<Path, String> failed = new HashMap<>();
    private final Map<String, SheetTexture> fallback = new HashMap<>();
    private final Map<String, CompletableFuture<FallbackSprites.Pair>> fallbackJobs =
            new ConcurrentHashMap<>();
    private final Map<String, Texture> icons = new HashMap<>();

    private long budgetBytes;
    private volatile double scale;
    /** Whether the first version of the re-animated states is drawn ({@link #CLASSIC_FOLDER}). */
    private volatile boolean classic;
    /**
     * Whether {@link #CLASSIC_FOLDER} is indexed: only from the first time
     * classic is switched on - it is as big as the sprites folders themselves,
     * and every start would pay for listing it.
     */
    private volatile boolean indexClassic;
    private volatile CompletableFuture<Void> classicIndexed = CompletableFuture.completedFuture(null);
    private int maxTexture = 4096;
    private long frame;
    private long residentBytes;
    /** Bumped by {@link #reloadAll()}; decodes started before it are thrown away. */
    private volatile int epoch;

    public SpriteLibrary(Path root, long budgetBytes, double scale) {
        this(root, budgetBytes, scale, GL);
    }

    /**
     * The library, its folders indexed before this returns, telling {@code
     * progress} how far it has got (the loading screen); {@code classic}: the
     * first version of the re-animated states is drawn, and so indexed in the
     * same pass ({@link #setClassic}).
     */
    public SpriteLibrary(Path root, long budgetBytes, double scale, boolean classic, ScanProgress progress) {
        this(root, budgetBytes, scale, GL, classic, progress);
    }

    SpriteLibrary(Path root, long budgetBytes, double scale, Uploader uploader) {
        this(root, budgetBytes, scale, uploader, false, ScanProgress.NONE);
    }

    private SpriteLibrary(Path root, long budgetBytes, double scale, Uploader uploader, boolean classic,
                          ScanProgress progress) {
        this.root = root;
        this.budgetBytes = budgetBytes;
        this.scale = scale;
        this.uploader = uploader;
        this.classic = classic;
        this.indexClassic = classic;
        threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        workers = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "sprite-decoder");
            t.setDaemon(true);
            // below the render thread, so decoding never takes its core when cores are short
            // (Windows and macOS honour it; Linux schedules threads alike)
            t.setPriority(Thread.NORM_PRIORITY - 2);
            return t;
        });
        rescan(progress);
    }

    public Path root() { return root; }

    /**
     * Draw the first version of the states that were animated again (the
     * "classic" animations, kept in {@link #CLASSIC_FOLDER}) instead of the
     * current one - an A/B switch; a state with only one version shows it.
     * The first time it is switched on the archive is indexed, in the
     * background (a few seconds); until then the current version is drawn.
     */
    public void setClassic(boolean classic) {
        this.classic = classic;
        if (classic && !indexClassic) {
            indexClassic = true;
            classicIndexed = CompletableFuture.runAsync(this::rescan);
        }
    }

    /** Done once the archive {@link #setClassic} asked for is indexed. */
    CompletableFuture<Void> classicIndexed() { return classicIndexed; }

    public boolean classic() { return classic; }

    /** The sprites folder of a {@link #rootOf} key (the default folder for an unknown one). */
    public Path root(String key) {
        Index i = index.roots().get(key);
        return i == null ? root : i.dir();
    }

    /**
     * The key of the sprites folder a body's items are in: the body's own
     * folder ({@code sprites_<body>}) when it has one, else the default
     * ({@link #DEFAULT_ROOT}); {@code body} may be a style version of the body's
     * folder ({@code masculine_px64_lh}) or null (the generated body).
     */
    public String rootOf(String body) {
        if (body == null) return DEFAULT_ROOT;
        return index.bodyRoot().getOrDefault(Wardrobe.Style.base(body), DEFAULT_ROOT);
    }

    /** The {@link #rootOf} keys of every sprites folder found, the default's first. */
    public List<String> roots() {
        List<String> keys = new ArrayList<>(index.roots().keySet());
        Collections.sort(keys);
        return keys;
    }

    /** Bumped by every {@link #rescan()}, so menus know to refresh their lists. */
    public int generation() { return generation; }

    /** The driver's largest texture edge; sheets are packed to fit it. */
    public void setMaxTexture(int maxTexture) {
        this.maxTexture = Math.max(1024, maxTexture);
    }

    public static String key(AnimState state, Elevation elevation, Facing facing) {
        return state.key() + "_" + elevation.key() + "_" + facing.key();
    }

    // --- the folder index ------------------------------------------------------------

    /**
     * What {@link #rescan(ScanProgress)} reports as it goes, after each
     * cosmetic (or body) folder: how many of the folders it has indexed, of
     * how many, the one it has just done, and the sheets found so far. On
     * the thread that is indexing.
     */
    @FunctionalInterface
    public interface ScanProgress {
        ScanProgress NONE = (done, of, folder, sheets) -> {};

        void indexed(int done, int of, Path folder, int sheets);
    }

    /**
     * Re-read {@code assets/sprites/} and every body's folder beside it
     * ({@code assets/sprites_<body>/}). Cheap: file names only, no pixels.
     */
    public void rescan() {
        rescan(ScanProgress.NONE);
    }

    /** {@link #rescan()}, telling {@code progress} how far it has got. */
    public synchronized void rescan(ScanProgress progress) {
        Map<String, Path> dirs = new LinkedHashMap<>();
        dirs.put(DEFAULT_ROOT, root);
        Path parent = root.toAbsolutePath().getParent();
        String prefix = root.getFileName() + "_";
        if (parent != null && Files.isDirectory(parent)) {
            try (Stream<Path> list = Files.list(parent)) {
                for (Path dir : list.filter(Files::isDirectory).sorted().toList()) {
                    String name = dir.getFileName().toString();
                    if (name.startsWith(prefix) && name.length() > prefix.length()) {
                        dirs.put(name.substring(prefix.length()), dir);
                    }
                }
            } catch (IOException e) {
                System.err.println("[sprites] cannot list " + parent + ": " + e.getMessage());
            }
        }
        // every item folder of every sprites folder first, so the progress has a total
        Map<String, Map<Slot, List<Path>>> items = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, Path> d : dirs.entrySet()) {
            Map<Slot, List<Path>> slots = itemFolders(d.getValue());
            for (List<Path> l : slots.values()) total += l.size();
            items.put(d.getKey(), slots);
        }
        int[] done = {0, 0};
        Map<String, Index> roots = new LinkedHashMap<>();
        for (Map.Entry<String, Path> d : dirs.entrySet()) {
            roots.put(d.getKey(), scanRoot(d.getValue(), items.get(d.getKey()), progress, done, total));
        }
        // a body is in the folder whose body/ has it; the default's first, a
        // body's own folder over it
        Map<String, String> bodyRoot = new HashMap<>();
        for (Map.Entry<String, Index> r : roots.entrySet()) {
            for (String folder : r.getValue().of(Slot.BODY).keySet()) {
                String body = Wardrobe.Style.base(folder);
                if (r.getKey().equals(DEFAULT_ROOT)) bodyRoot.putIfAbsent(body, DEFAULT_ROOT);
                else bodyRoot.put(body, r.getKey());
            }
        }
        index = new Indexes(Collections.unmodifiableMap(roots), Collections.unmodifiableMap(bodyRoot));
        generation++;
    }

    /** What the index holds: how many sprites folders, cosmetics (bodies included) and sheets. */
    public record Census(int folders, int bodies, int items, int sheets) {}

    /** How much the last {@link #rescan} found. */
    public Census census() {
        int items = 0, sheets = 0;
        for (Index i : index.roots().values()) {
            for (Map<String, Entry> slot : i.slots().values()) {
                items += slot.size();
                for (Entry e : slot.values()) sheets += e.count();
            }
        }
        return new Census(index.roots().size(), index.bodyRoot().size(), items, sheets);
    }

    /** Where a sprites folder's first-version sheets are kept ({@link #CLASSIC_FOLDER}). */
    static Path classicRoot(Path base) {
        Path abs = base.toAbsolutePath();
        Path parent = abs.getParent();
        return parent == null ? null : parent.resolve(CLASSIC_FOLDER).resolve(abs.getFileName());
    }

    /** The item folders of each slot of a sprites folder, sorted. */
    private static Map<Slot, List<Path>> itemFolders(Path base) {
        Map<Slot, List<Path>> out = new EnumMap<>(Slot.class);
        for (Slot slot : Slot.values()) {
            Path slotDir = base.resolve(slot.key());
            if (!Files.isDirectory(slotDir)) continue;
            try (Stream<Path> dirs = Files.list(slotDir)) {
                out.put(slot, dirs.filter(Files::isDirectory).sorted().toList());
            } catch (IOException e) {
                System.err.println("[sprites] cannot list " + slotDir + ": " + e.getMessage());
            }
        }
        return out;
    }

    private Index scanRoot(Path base, Map<Slot, List<Path>> folders, ScanProgress progress, int[] done,
                           int total) {
        Map<Slot, Map<String, Entry>> next = new EnumMap<>(Slot.class);
        Path archive = indexClassic ? classicRoot(base) : null;
        for (Slot slot : Slot.values()) {
            Map<String, Entry> items = new TreeMap<>();
            for (Path dir : folders.getOrDefault(slot, List.of())) {
                Path old = archive == null ? null
                        : archive.resolve(slot.key()).resolve(dir.getFileName().toString());
                Entry e = scanItem(slot, dir, old);
                items.put(e.name(), e);
                done[1] += e.count();
                progress.indexed(++done[0], total, dir, done[1]);
            }
            next.put(slot, Collections.unmodifiableMap(items));
        }
        return new Index(base, next);
    }

    private Entry scanItem(Slot slot, Path dir, Path classicDir) {
        SpriteProfile profile = SpriteProfile.load(dir, SpriteProfile.defaults());
        Map<String, Path> sheets = new TreeMap<>();
        List<String> skipped = new ArrayList<>();
        scanSheets(dir, sheets, skipped);
        Map<String, Path> classic = new TreeMap<>();
        if (classicDir != null && Files.isDirectory(classicDir)) scanSheets(classicDir, classic, new ArrayList<>());
        return new Entry(slot, dir.getFileName().toString(), dir, profile,
                Collections.unmodifiableMap(sheets), List.copyOf(skipped), Variants.load(dir),
                Collections.unmodifiableMap(classic));
    }

    /** The key of every canonical sheet name ({@code walk_middle_ne.png}), read without the parser. */
    private static final Map<String, String> CANONICAL = canonicalNames();

    private static Map<String, String> canonicalNames() {
        Map<String, String> out = new HashMap<>();
        for (AnimState s : AnimState.values()) {
            for (Elevation e : Elevation.values()) {
                for (Facing f : Facing.values()) out.put(SpriteNames.fileName(s, e, f), key(s, e, f));
            }
        }
        return out;
    }

    /**
     * The sheets in an item folder (and in its folders, four deep), by key.
     * A folder holds thousands of them and there are hundreds of folders, so
     * this is the start's longest wait (the loading screen shows it): the
     * names are listed without asking the disk about each file (a {@code
     * .png} is taken to be a file - only what may be a folder is looked at),
     * and a name in the canonical form is looked up rather than parsed. The
     * same sheets come out as from walking the folder and parsing every name:
     * the files in path order, the first of a key kept.
     */
    static void scanSheets(Path dir, Map<String, Path> sheets, List<String> skipped) {
        List<Path> files = new ArrayList<>();
        try {
            listPngs(dir, 4, files);
        } catch (IOException e) {
            skipped.add("(unreadable: " + e.getMessage() + ")");
            return;
        }
        Collections.sort(files);
        for (Path f : files) {
            String rel = dir.relativize(f).toString();
            String known = CANONICAL.get(rel);
            if (known != null) {
                sheets.putIfAbsent(known, f);
                continue;
            }
            SpriteNames.Parsed p = SpriteNames.parse(rel);
            if (!p.complete() || p.frame() != null) {
                skipped.add(rel);
                continue;
            }
            sheets.putIfAbsent(key(p.state(), p.elevation(), p.facing()), f);
        }
    }

    private static void listPngs(Path dir, int depth, List<Path> out) throws IOException {
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path p : entries) {
                String name = p.getFileName().toString();
                if (name.regionMatches(true, name.length() - 4, ".png", 0, 4)) out.add(p);
                else if (depth > 1 && Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    listPngs(p, depth - 1, out);
                }
            }
        }
    }

    /** Item names in a slot of the default folder, sorted (the bodies: every folder's). */
    public List<String> items(Slot slot) {
        return items(null, slot);
    }

    /**
     * Item names in a slot for a body, sorted: those of the body's folder. Under
     * {@link Slot#BODY}, the bodies of every folder.
     */
    public List<String> items(String body, Slot slot) {
        if (slot == Slot.BODY) {
            java.util.TreeSet<String> all = new java.util.TreeSet<>();
            for (Index i : index.roots().values()) all.addAll(i.of(Slot.BODY).keySet());
            return List.copyOf(all);
        }
        return List.copyOf(indexFor(rootOf(body)).of(slot).keySet());
    }

    private Index indexFor(String key) {
        Index i = index.roots().get(key);
        if (i == null) i = index.roots().get(DEFAULT_ROOT);
        return i == null ? new Index(root, Map.of()) : i;
    }

    /** An item folder of the default folder (a body: of its own folder), or null. */
    public Entry entry(Slot slot, String item) {
        return entry(null, slot, item);
    }

    /** An item folder as worn on {@code body}: in the body's folder (a body: in its own). */
    public Entry entry(String body, Slot slot, String item) {
        if (item == null) return null;
        return entryIn(slot == Slot.BODY ? rootOf(item) : rootOf(body), slot, item);
    }

    private Entry entryIn(String key, Slot slot, String item) {
        if (item == null) return null;
        return indexFor(key).of(slot).get(item);
    }

    /**
     * The folder to draw {@code item} from in {@code style}: its version in
     * that style when there is one ({@code straw_sun_hat_px64}), else itself.
     * An item that exists only as pixel art ({@code beanie_px128} but no
     * {@code beanie}) is drawn from its 128-pixel version in the rendered
     * style.
     */
    public String styled(Slot slot, String item, Wardrobe.Style style) {
        return styled(null, slot, item, style);
    }

    /** {@link #styled(Slot, String, Wardrobe.Style)} for an item worn on {@code body}. */
    public String styled(String body, Slot slot, String item, Wardrobe.Style style) {
        if (item == null) return null;
        style = drawnIn(body, slot, item, style);
        if (style == Wardrobe.Style.RENDERED) return item;
        String version = style.folder(item);
        return entry(body, slot, version) != null ? version : item;
    }

    /** The style {@code item} is drawn in when {@code style} is asked for (see {@link #styled}). */
    private Wardrobe.Style drawnIn(String body, Slot slot, String item, Wardrobe.Style style) {
        if (style == Wardrobe.Style.RENDERED && entry(body, slot, item) == null
                && entry(body, slot, Wardrobe.Style.PIXEL_128.folder(item)) != null) {
            return Wardrobe.Style.PIXEL_128;
        }
        return style;
    }

    /**
     * Where a worn item's sheets come from: a slot's folder, drawn mirrored or
     * not. For the left-handed character ({@link Wardrobe#leftHanded()}) that
     * is, among the renders, the item's 512-pixel version in the slot's {@link
     * Slot#twin() twin}, mirrored - the left-hand sword is the right-hand
     * sword seen in a mirror (and from the mirrored direction); else the
     * style's left-handed version ({@code sword_px128_lh}) when there is one,
     * drawn as it is; otherwise the twin's, mirrored.
     */
    public record Source(Slot slot, String folder, boolean mirrored, String root) {

        /** A source in the default sprites folder. */
        public Source(Slot slot, String folder, boolean mirrored) {
            this(slot, folder, mirrored, DEFAULT_ROOT);
        }
    }

    public Source source(Slot slot, String item, Wardrobe.Style style, boolean leftHanded) {
        return source(null, slot, item, style, leftHanded);
    }

    /** {@link #source(Slot, String, Wardrobe.Style, boolean)} for an item worn on {@code body}. */
    public Source source(String body, Slot slot, String item, Wardrobe.Style style, boolean leftHanded) {
        if (item == null) return null;
        String key = slot == Slot.BODY ? rootOf(item) : rootOf(body);
        if (slot == Slot.BODY) body = item;
        if (!leftHanded) return new Source(slot, styled(body, slot, item, style), false, key);
        Slot twin = slot.twin();
        // among the renders, the 512-pixel twin in a mirror before any pixel art
        if (style == Wardrobe.Style.RENDERED && entry(body, twin, item) != null) return new Source(twin, item, true, key);
        Wardrobe.Style drawn = drawnIn(body, slot, item, style);
        if (drawn != Wardrobe.Style.RENDERED && entry(body, slot, drawn.leftFolder(item)) != null) {
            return new Source(slot, drawn.leftFolder(item), false, key);
        }
        String folder = styled(body, twin, item, style);
        if (entry(body, twin, folder) != null) return new Source(twin, folder, true, key);
        return new Source(slot, styled(body, slot, item, style), true, key);
    }

    /** One view of a {@link #source}: a mirrored source is its mirrored view, flipped. */
    public Resolved resolve(Source src, AnimState state, Elevation elevation, Facing facing) {
        if (src == null) return null;
        Entry e = entryIn(src.root(), src.slot(), src.folder());
        if (!src.mirrored()) return resolve(e, state, elevation, facing);
        Resolved r = resolve(e, state, elevation, facing.mirrorOf());
        return r == null ? null : r.flipped();
    }

    /**
     * The palette swap that draws a source in the colours picked for the slot
     * it is worn in ({@link Wardrobe#colour}) and the skin tone ({@link
     * Wardrobe#skin}), or null for its own colours.
     */
    public Variants.Recolor recolor(Source src, Slot wornIn, Wardrobe wardrobe) {
        if (src == null) return null;
        Entry e = entryIn(src.root(), src.slot(), src.folder());
        if (e == null || e.variants() == null) return null;
        Map<String, String> choice = new HashMap<>();
        String own = wardrobe.colour(wornIn);
        if (own != null && wornIn != Slot.BODY) choice.put(Variants.OWN, own);
        String skin = wardrobe.skin();
        if (skin != null) choice.put(Variants.SKIN, skin);
        return e.variants().recolor(choice);
    }

    /** The colour options the item worn in {@code slot} has for its own colour, in this style (may be empty). */
    public List<String> colourOptions(Slot slot, String item, Wardrobe.Style style) {
        return colourOptions(null, slot, item, style);
    }

    /** {@link #colourOptions(Slot, String, Wardrobe.Style)} for an item worn on {@code body}. */
    public List<String> colourOptions(String body, Slot slot, String item, Wardrobe.Style style) {
        Entry e = entry(body, slot, styled(body, slot, item, style));
        if (e == null || e.variants() == null) return List.of();
        Variants.Channel c = e.variants().channel(slot == Slot.BODY ? Variants.SKIN : Variants.OWN);
        return c == null ? List.of() : c.names();
    }

    /** The colour an option shows as (a swatch, RGB), or -1. */
    public int swatch(Slot slot, String item, Wardrobe.Style style, String option) {
        return swatch(null, slot, item, style, option);
    }

    /** {@link #swatch(Slot, String, Wardrobe.Style, String)} for an item worn on {@code body}. */
    public int swatch(String body, Slot slot, String item, Wardrobe.Style style, String option) {
        Entry e = entry(body, slot, styled(body, slot, item, style));
        if (e == null || e.variants() == null) return -1;
        Variants.Channel c = e.variants().channel(slot == Slot.BODY ? Variants.SKIN : Variants.OWN);
        Integer rgb = c == null ? null : c.swatch().get(option);
        return rgb == null ? -1 : rgb;
    }

    /**
     * The file to draw for one view of one item, or {@code null} if there is
     * none. A missing west-facing sheet borrows its east-facing twin flipped
     * (and vice versa), the engine's rule for directional art.
     */
    public Resolved resolve(Slot slot, String item, AnimState state, Elevation elevation,
                            Facing facing) {
        return resolve(entry(slot, item), state, elevation, facing);
    }

    private Resolved resolve(Entry e, AnimState state, Elevation elevation, Facing facing) {
        if (e == null) return null;
        Path direct = e.sheet(state, elevation, facing, classic);
        if (direct != null && !failed.containsKey(direct)) {
            return new Resolved(direct, false, e.profile());
        }
        if (facing.hasMirror()) {
            Path twin = e.sheet(state, elevation, facing.mirrorOf(), classic);
            if (twin != null && !failed.containsKey(twin)) {
                return new Resolved(twin, true, e.profile());
            }
        }
        return null;
    }

    /** A pickup's world sprite on disk, or {@code null}. */
    public Path itemIconFile(String itemId) {
        Path p = root.resolve(ITEMS_FOLDER).resolve(itemId).resolve("icon.png");
        return Files.isRegularFile(p) ? p : null;
    }

    // --- GPU residency ---------------------------------------------------------------

    /** Call once per frame, before anything asks for a sheet. */
    public void beginFrame() {
        frame++;
    }

    /**
     * The texture for {@code r}, or {@code null} while it is still loading
     * (the load is started if it was not). Callers keep drawing whatever they
     * drew last until it arrives.
     */
    public SheetTexture sheet(Resolved r) {
        if (r == null) return null;
        SheetTexture t = textures.get(r.key());
        if (t != null) {
            t.lastUsedFrame = frame;
            return t;
        }
        prefetching.remove(r.key()); // wanted on screen now: it loads whatever the budget
        request(r);
        return null;
    }

    /** Mark a sheet as on screen this frame, so eviction leaves it alone. */
    public void touch(SheetTexture t) {
        t.lastUsedFrame = frame;
    }

    /**
     * Start loading {@code r} ahead of need, into room the budget has spare —
     * a prefetch never pushes the library over its budget. If it did, nothing
     * would hold it: it is not on screen, so it would be evicted as soon as it
     * arrived, asked for again the next frame, and so on for ever (with an
     * 18-layer outfit of uncropped sheets, that was every prefetch, all the
     * time). So a prefetch is queued only while its size (known once it has
     * been decoded; the average sheet's until then) fits beside what is
     * resident and what is already on the way, and no more than one per
     * decoder thread is on the way at a time; and one that arrives to find no
     * room after all is dropped instead of uploaded, so it is not asked for
     * again until room appears.
     */
    public void prefetch(Resolved r) {
        if (r == null || !loadable(r.key()) || prefetching.size() >= threads) return;
        long typical = sizes.isEmpty() ? 0 : sizesTotal / sizes.size();
        long need = sizes.getOrDefault(r.key(), typical);
        for (Key p : prefetching) need += sizes.getOrDefault(p, typical);
        if (residentBytes + need > budgetBytes) return;
        prefetching.add(r.key());
        request(r);
    }

    private boolean loadable(Key key) {
        return !textures.containsKey(key) && !pending.containsKey(key) && !failed.containsKey(key.file());
    }

    /** Start loading {@code r} in the background if it is not already resident. */
    private void request(Resolved r) {
        if (r == null || !loadable(r.key())) return;
        Key key = r.key();
        SpriteProfile profile = r.profile();
        double s = scale;
        int max = maxTexture;
        int ep = epoch;
        pending.put(key, CompletableFuture.runAsync(
                () -> decoded.add(decode(key, ep, profile, s, max)), workers));
    }

    private static Decoded decode(Key key, int epoch, SpriteProfile profile, double scale, int maxTexture) {
        try {
            BufferedImage img = ImageIO.read(key.file().toFile());
            if (img == null) return new Decoded(key, epoch, null, null, "not an image ImageIO can read");
            if (key.cut() != null) {
                BufferedImage over = ImageIO.read(key.cut().toFile());
                if (over != null) img = SheetImage.cutUnder(img, over, profile.frameWidth(), profile.frameHeight());
            }
            boolean pixelArt = profile.pixelArt();
            SheetImage layout = SheetImage.decode(img, profile.frameWidth(), profile.frameHeight(),
                    pixelArt ? 1.0 : scale, maxTexture, pixelArt);
            BufferedImage atlas = layout.atlas();
            PixelData pixels = layout.indexed()
                    ? PixelData.ofIndices(layout.indices(), atlas.getWidth(), atlas.getHeight())
                    : PixelData.of(atlas);
            return new Decoded(key, epoch, layout, pixels, null);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return new Decoded(key, epoch, null, null, e.toString());
        }
    }

    /**
     * Upload finished decodes - within {@link #UPLOAD_BUDGET_NS} a frame, so
     * turning the camera never costs a frame its deadline - and evict over
     * budget. Each upload's cost is predicted from its size and what uploads
     * have taken so far; one that would not fit waits for the next frame (the
     * stacks on screen hold their last complete frame meanwhile). GL thread
     * only.
     */
    public void pump() {
        pump(UPLOAD_BUDGET_NS);
    }

    /** Upload every finished decode, whatever it takes. For tests. */
    void pumpAll() {
        pump(Long.MAX_VALUE);
    }

    /**
     * {@link #pump()} with {@code budgetNs} to spend on uploads this frame:
     * more while the loading screen is up and nothing else needs the frame.
     */
    public void pump(long budgetNs) {
        long start = System.nanoTime();
        boolean uploaded = false;
        Decoded d;
        while ((d = decoded.peek()) != null) {
            if (uploaded && d.epoch() == epoch && d.error() == null) {
                double predicted = bytesOf(d.layout()) * uploadNsPerByte;
                if (System.nanoTime() - start + predicted > budgetNs) break;
            }
            decoded.poll();
            if (d.epoch() != epoch) {
                if (d.pixels() != null) d.pixels().free();
                continue;
            }
            pending.remove(d.key());
            boolean ahead = prefetching.remove(d.key());
            if (d.error() != null) {
                failed.put(d.key().file(), d.error());
                System.err.println("[sprites] cannot load " + d.key().file() + ": " + d.error());
            } else {
                long bytes = bytesOf(d.layout());
                Long before = sizes.put(d.key(), bytes);
                sizesTotal += bytes - (before == null ? 0 : before);
                if (ahead && residentBytes + bytes > budgetBytes) {
                    d.pixels().free(); // a prefetch with no room left for it
                } else {
                    long t0 = System.nanoTime();
                    SheetTexture t = uploader.upload(d.layout(), d.pixels(), d.key().file().toString());
                    // learnt from sheets big enough for their size, not the call, to be the cost
                    if (bytes >= 256 << 10) {
                        double cost = (System.nanoTime() - t0) / (double) bytes;
                        uploadNsPerByte = uploadNsPerByte == 0 ? cost : uploadNsPerByte + (cost - uploadNsPerByte) * 0.25;
                    }
                    uploaded = true;
                    t.lastUsedFrame = frame;
                    SheetTexture old = textures.put(d.key(), t);
                    if (old != null) {
                        residentBytes -= old.bytes();
                        old.close();
                    }
                    residentBytes += t.bytes();
                }
            }
        }
        evict();
    }

    private void evict() {
        if (residentBytes <= budgetBytes) return;
        Iterator<Map.Entry<Key, SheetTexture>> it = textures.entrySet().iterator();
        while (residentBytes > budgetBytes && it.hasNext()) {
            SheetTexture t = it.next().getValue();
            if (t.lastUsedFrame >= frame - 1) continue; // on screen right now
            residentBytes -= t.bytes();
            t.close();
            it.remove();
        }
    }

    /** Whether anything is still decoding. */
    public boolean busy() {
        return !pending.isEmpty() || !decoded.isEmpty();
    }

    /** Block until every queued decode has finished; {@link #pump()} still uploads them. For tests. */
    void awaitDecodes() {
        CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new)).join();
    }

    /**
     * Forget every loaded sheet and every failure, so the next draw re-reads
     * the files. After an import has written new sheets, or the resolution
     * setting changed.
     */
    public void reloadAll() {
        for (SheetTexture t : textures.values()) t.close();
        textures.clear();
        residentBytes = 0;
        failed.clear();
        pending.clear();
        prefetching.clear();
        sizes.clear();
        sizesTotal = 0;
        epoch++;
        rescan();
    }

    public void setScale(double scale) {
        double s = Math.max(0.125, Math.min(1, scale));
        if (s != this.scale) {
            this.scale = s;
            reloadAll();
        }
    }

    public double scale() { return scale; }

    public void setBudgetBytes(long bytes) {
        budgetBytes = Math.max(64L << 20, bytes);
    }

    // --- the fallback character ------------------------------------------------------

    private static String fallbackKey(AnimState s, Elevation e, Facing f) {
        return s.key() + "_" + e.key() + "_" + f.key();
    }

    /**
     * Generate every fallback view in the background, so none costs a frame
     * later: those of the first six - every other state is shown as one of
     * them on the generated body ({@link AnimState#root()}).
     */
    public void warmFallbacks() {
        for (AnimState s : AnimState.values()) {
            if (s.root() != s) continue;
            for (Elevation e : Elevation.values()) {
                for (Facing f : Facing.values()) fallbackJob(s, e, f);
            }
        }
    }

    private CompletableFuture<FallbackSprites.Pair> fallbackJob(AnimState s, Elevation e, Facing f) {
        return fallbackJobs.computeIfAbsent(fallbackKey(s, e, f),
                k -> CompletableFuture.supplyAsync(() -> FallbackSprites.generate(s, e, f), workers));
    }

    /**
     * One view of the fallback body ({@code sword == false}) or of the sword
     * it holds. Always returns a texture: if the background job has not
     * finished, this waits for it (a few milliseconds of rasterising 32-pixel
     * frames), because the fallback is what is drawn when nothing else is.
     */
    public SheetTexture fallback(AnimState s, Elevation e, Facing f, boolean sword) {
        String key = fallbackKey(s, e, f) + (sword ? ":sword" : ":body");
        SheetTexture t = fallback.get(key);
        if (t != null) return t;
        FallbackSprites.Pair pair = fallbackJob(s, e, f).join();
        String base = fallbackKey(s, e, f);
        fallback.put(base + ":body", new SheetTexture(
                Texture.of(pair.body().atlas(), true), pair.body(), "fallback 32px"));
        fallback.put(base + ":sword", new SheetTexture(
                Texture.of(pair.sword().atlas(), true), pair.sword(), "fallback 32px"));
        return fallback.get(key);
    }

    /** How many fallback views have been generated so far (for the loading screen). */
    public int fallbackReady() {
        int n = 0;
        for (CompletableFuture<FallbackSprites.Pair> f : fallbackJobs.values()) if (f.isDone()) n++;
        return n;
    }

    public static int fallbackTotal() {
        int roots = 0;
        for (AnimState s : AnimState.values()) if (s.root() == s) roots++;
        return roots * Elevation.values().length * Facing.values().length;
    }

    // --- pickups ---------------------------------------------------------------------

    /**
     * A pickup's world sprite: {@code items/<id>/icon.png} if present,
     * otherwise {@code generated}. Small, so loaded synchronously and kept.
     */
    public Texture icon(String itemId, java.util.function.Supplier<BufferedImage> generated) {
        Texture t = icons.get(itemId);
        if (t != null) return t;
        BufferedImage img = null;
        Path file = itemIconFile(itemId);
        if (file != null) {
            try {
                img = ImageIO.read(file.toFile());
            } catch (IOException e) {
                System.err.println("[sprites] cannot read " + file + ": " + e.getMessage());
            }
        }
        boolean pixelArt = img == null || img.getWidth() <= 64;
        if (img == null) img = generated.get();
        t = Texture.of(img, pixelArt);
        icons.put(itemId, t);
        return t;
    }

    /** Drop cached pickup icons (after an import). */
    public void reloadIcons() {
        for (Texture t : icons.values()) t.close();
        icons.clear();
    }

    // --- stats -----------------------------------------------------------------------

    public int residentCount() { return textures.size(); }

    public long residentBytes() { return residentBytes; }

    public int pendingCount() { return pending.size(); }

    public Map<Path, String> failures() { return Collections.unmodifiableMap(failed); }

    @Override
    public void close() {
        workers.shutdownNow();
        for (SheetTexture t : textures.values()) t.close();
        for (SheetTexture t : fallback.values()) t.close();
        for (Texture t : icons.values()) t.close();
        Decoded d;
        while ((d = decoded.poll()) != null) if (d.pixels() != null) d.pixels().free();
    }
}
