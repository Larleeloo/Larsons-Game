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
 * <h2>On the GPU</h2>
 * A full character is 6 states × 24 views of 512-pixel frames — gigabytes if it
 * were all resident at once. So sheets are loaded <b>on demand</b>, decoded
 * on worker threads, cropped to the part of the frame they use ({@link
 * SheetImage}), uploaded a few per frame, and evicted least-recently-used
 * once the video-memory budget ({@code -Dlarsons.sprites.vramMB}, default
 * 1536) is exceeded. What the camera is looking at always loads; what it may
 * look at next is {@linkplain #prefetch prefetched} only into room the budget
 * has spare. {@code -Dlarsons.sprites.scale=0.5} halves every frame on load
 * for smaller GPUs.
 */
public final class SpriteLibrary implements AutoCloseable {

    /** Folder of world sprites for pickups, beside the slot folders. */
    public static final String ITEMS_FOLDER = "items";

    /** One cosmetic (or body) folder and the sheets found in it, and the colours it can take (or null). */
    public record Entry(Slot slot, String name, Path folder, SpriteProfile profile,
                        Map<String, Path> sheets, List<String> skipped, Variants variants) {

        public Path sheet(AnimState state, Elevation elevation, Facing facing) {
            return sheets.get(key(state, elevation, facing));
        }

        /** How many of the 144 (6 × 3 × 8) sheets are present. */
        public int count() { return sheets.size(); }
    }

    /**
     * A sheet to draw: the file, whether it is drawn mirrored (a twin standing
     * in, or the left-handed character), and the palette swap it is drawn with
     * (null: its own colours).
     */
    public record Resolved(Path file, boolean mirrored, SpriteProfile profile, Variants.Recolor recolor) {

        public Resolved(Path file, boolean mirrored, SpriteProfile profile) {
            this(file, mirrored, profile, null);
        }

        /** What the library keeps it under: the file, in these colours. */
        public Key key() {
            return new Key(file, recolor == null ? "" : recolor.key());
        }

        public Resolved withRecolor(Variants.Recolor r) {
            return new Resolved(file, mirrored, profile, r);
        }

        public Resolved flipped() {
            return new Resolved(file, !mirrored, profile, recolor);
        }
    }

    /** A decoded sheet's identity: one file, in one set of colours. */
    public record Key(Path file, String colours) {}

    private record Decoded(Key key, int epoch, SheetImage layout, PixelData pixels, String error) {}

    /** Puts a decoded sheet on the GPU. Tests, which have no GL, pass their own. */
    @FunctionalInterface
    interface Uploader {
        SheetTexture upload(SheetImage layout, PixelData pixels, String source);
    }

    private static final Uploader GL = (layout, pixels, source) ->
            new SheetTexture(Texture.upload(pixels, layout.pixelArt()), layout, source);

    private final Path root;
    private volatile Map<Slot, Map<String, Entry>> index = new EnumMap<>(Slot.class);
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
    /** Files that would not decode (in any colours). */
    private final Map<Path, String> failed = new HashMap<>();
    private final Map<String, SheetTexture> fallback = new HashMap<>();
    private final Map<String, CompletableFuture<FallbackSprites.Pair>> fallbackJobs =
            new ConcurrentHashMap<>();
    private final Map<String, Texture> icons = new HashMap<>();

    private long budgetBytes;
    private volatile double scale;
    private int maxTexture = 4096;
    private long frame;
    private long residentBytes;
    /** Bumped by {@link #reloadAll()}; decodes started before it are thrown away. */
    private volatile int epoch;

    public SpriteLibrary(Path root, long budgetBytes, double scale) {
        this(root, budgetBytes, scale, GL);
    }

    SpriteLibrary(Path root, long budgetBytes, double scale, Uploader uploader) {
        this.root = root;
        this.budgetBytes = budgetBytes;
        this.scale = scale;
        this.uploader = uploader;
        threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        workers = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "sprite-decoder");
            t.setDaemon(true);
            return t;
        });
        rescan();
    }

    public Path root() { return root; }

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

    /** Re-read {@code assets/sprites/}. Cheap: file names only, no pixels. */
    public synchronized void rescan() {
        Map<Slot, Map<String, Entry>> next = new EnumMap<>(Slot.class);
        for (Slot slot : Slot.values()) {
            Map<String, Entry> items = new TreeMap<>();
            Path slotDir = root.resolve(slot.key());
            if (Files.isDirectory(slotDir)) {
                try (Stream<Path> dirs = Files.list(slotDir)) {
                    for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                        Entry e = scanItem(slot, dir);
                        items.put(e.name(), e);
                    }
                } catch (IOException e) {
                    System.err.println("[sprites] cannot list " + slotDir + ": " + e.getMessage());
                }
            }
            next.put(slot, Collections.unmodifiableMap(items));
        }
        index = next;
        generation++;
    }

    private Entry scanItem(Slot slot, Path dir) {
        SpriteProfile profile = SpriteProfile.load(dir, SpriteProfile.defaults());
        Map<String, Path> sheets = new TreeMap<>();
        List<String> skipped = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir, 4)) {
            for (Path f : files.filter(Files::isRegularFile).sorted().toList()) {
                String rel = dir.relativize(f).toString();
                if (!rel.toLowerCase().endsWith(".png")) continue;
                SpriteNames.Parsed p = SpriteNames.parse(rel);
                if (!p.complete() || p.frame() != null) {
                    skipped.add(rel);
                    continue;
                }
                sheets.putIfAbsent(key(p.state(), p.elevation(), p.facing()), f);
            }
        } catch (IOException e) {
            skipped.add("(unreadable: " + e.getMessage() + ")");
        }
        return new Entry(slot, dir.getFileName().toString(), dir, profile,
                Collections.unmodifiableMap(sheets), List.copyOf(skipped), Variants.load(dir));
    }

    /** Item names in a slot, sorted. */
    public List<String> items(Slot slot) {
        return List.copyOf(index.getOrDefault(slot, Map.of()).keySet());
    }

    public Entry entry(Slot slot, String item) {
        if (item == null) return null;
        return index.getOrDefault(slot, Map.of()).get(item);
    }

    /**
     * The folder to draw {@code item} from in {@code style}: its version in
     * that style when there is one ({@code straw_sun_hat_px64}), else itself.
     */
    public String styled(Slot slot, String item, Wardrobe.Style style) {
        if (item == null || style == Wardrobe.Style.RENDERED) return item;
        String version = style.folder(item);
        return entry(slot, version) != null ? version : item;
    }

    /**
     * Where a worn item's sheets come from: a slot's folder, drawn mirrored or
     * not. For the left-handed character ({@link Wardrobe#leftHanded()}) that
     * is the style's left-handed version ({@code sword_px128_lh}) when there is
     * one, drawn as it is; otherwise the same item in the slot's {@link
     * Slot#twin() twin}, mirrored - the left-hand sword is the right-hand
     * sword seen in a mirror (and from the mirrored direction).
     */
    public record Source(Slot slot, String folder, boolean mirrored) {}

    public Source source(Slot slot, String item, Wardrobe.Style style, boolean leftHanded) {
        if (item == null) return null;
        if (!leftHanded) return new Source(slot, styled(slot, item, style), false);
        if (style != Wardrobe.Style.RENDERED && entry(slot, style.leftFolder(item)) != null) {
            return new Source(slot, style.leftFolder(item), false);
        }
        Slot twin = slot.twin();
        String folder = styled(twin, item, style);
        if (entry(twin, folder) != null) return new Source(twin, folder, true);
        return new Source(slot, styled(slot, item, style), true);
    }

    /** One view of a {@link #source}: a mirrored source is its mirrored view, flipped. */
    public Resolved resolve(Source src, AnimState state, Elevation elevation, Facing facing) {
        if (src == null) return null;
        if (!src.mirrored()) return resolve(src.slot(), src.folder(), state, elevation, facing);
        Resolved r = resolve(src.slot(), src.folder(), state, elevation, facing.mirrorOf());
        return r == null ? null : r.flipped();
    }

    /**
     * The palette swap that draws a source in the colours picked for the slot
     * it is worn in ({@link Wardrobe#colour}) and the skin tone ({@link
     * Wardrobe#skin}), or null for its own colours.
     */
    public Variants.Recolor recolor(Source src, Slot wornIn, Wardrobe wardrobe) {
        if (src == null) return null;
        Entry e = entry(src.slot(), src.folder());
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
        Entry e = entry(slot, styled(slot, item, style));
        if (e == null || e.variants() == null) return List.of();
        Variants.Channel c = e.variants().channel(slot == Slot.BODY ? Variants.SKIN : Variants.OWN);
        return c == null ? List.of() : c.names();
    }

    /** The colour an option shows as (a swatch, RGB), or -1. */
    public int swatch(Slot slot, String item, Wardrobe.Style style, String option) {
        Entry e = entry(slot, styled(slot, item, style));
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
        Entry e = entry(slot, item);
        if (e == null) return null;
        Path direct = e.sheet(state, elevation, facing);
        if (direct != null && !failed.containsKey(direct)) {
            return new Resolved(direct, false, e.profile());
        }
        if (facing.hasMirror()) {
            Path twin = e.sheet(state, elevation, facing.mirrorOf());
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
        Variants.Recolor recolor = r.recolor();
        SpriteProfile profile = r.profile();
        double s = scale;
        int max = maxTexture;
        int ep = epoch;
        pending.put(key, CompletableFuture.runAsync(
                () -> decoded.add(decode(key, recolor, ep, profile, s, max)), workers));
    }

    private static Decoded decode(Key key, Variants.Recolor recolor, int epoch, SpriteProfile profile,
                                  double scale, int maxTexture) {
        try {
            BufferedImage img = ImageIO.read(key.file().toFile());
            if (img == null) return new Decoded(key, epoch, null, null, "not an image ImageIO can read");
            img = Variants.apply(img, recolor);
            boolean pixelArt = profile.pixelArt();
            SheetImage layout = SheetImage.decode(img, profile.frameWidth(), profile.frameHeight(),
                    pixelArt ? 1.0 : scale, maxTexture, pixelArt);
            return new Decoded(key, epoch, layout, PixelData.of(layout.atlas()), null);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return new Decoded(key, epoch, null, null, e.toString());
        }
    }

    /**
     * Upload finished decodes (at most a few milliseconds' worth per frame, so
     * turning the camera never stalls) and evict over budget. GL thread only.
     */
    public void pump() {
        long deadline = System.nanoTime() + 4_000_000L;
        Decoded d;
        while ((d = decoded.poll()) != null) {
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
                BufferedImage atlas = d.layout().atlas();
                long bytes = Texture.bytesFor(atlas.getWidth(), atlas.getHeight(), d.layout().pixelArt());
                Long before = sizes.put(d.key(), bytes);
                sizesTotal += bytes - (before == null ? 0 : before);
                if (ahead && residentBytes + bytes > budgetBytes) {
                    d.pixels().free(); // a prefetch with no room left for it
                } else {
                    String source = d.key().file() + (d.key().colours().isEmpty() ? "" : " [" + d.key().colours() + "]");
                    SheetTexture t = uploader.upload(d.layout(), d.pixels(), source);
                    t.lastUsedFrame = frame;
                    SheetTexture old = textures.put(d.key(), t);
                    if (old != null) {
                        residentBytes -= old.bytes();
                        old.close();
                    }
                    residentBytes += t.bytes();
                }
            }
            if (System.nanoTime() > deadline) break;
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

    /** Generate every fallback view in the background, so none costs a frame later. */
    public void warmFallbacks() {
        for (AnimState s : AnimState.values()) {
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
        return AnimState.values().length * Elevation.values().length * Facing.values().length;
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
