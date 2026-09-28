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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * on worker threads, uploaded a few per frame, and evicted least-recently-used
 * once the video-memory budget ({@code -Dlarsons.sprites.vramMB}, default
 * 1536) is exceeded. Only what the camera is actually looking at stays
 * loaded. {@code -Dlarsons.sprites.scale=0.5} halves every frame on load for
 * smaller GPUs.
 */
public final class SpriteLibrary implements AutoCloseable {

    /** Folder of world sprites for pickups, beside the slot folders. */
    public static final String ITEMS_FOLDER = "items";

    /** One cosmetic (or body) folder and the sheets found in it. */
    public record Entry(Slot slot, String name, Path folder, SpriteProfile profile,
                        Map<String, Path> sheets, List<String> skipped) {

        public Path sheet(AnimState state, Elevation elevation, Facing facing) {
            return sheets.get(key(state, elevation, facing));
        }

        /** How many of the 144 (6 × 3 × 8) sheets are present. */
        public int count() { return sheets.size(); }
    }

    /** A sheet to draw: the file, and whether it is a mirrored twin standing in. */
    public record Resolved(Path file, boolean mirrored, SpriteProfile profile) {}

    private record Decoded(Path file, int epoch, SheetImage layout, PixelData pixels, String error) {}

    private final Path root;
    private volatile Map<Slot, Map<String, Entry>> index = new EnumMap<>(Slot.class);
    private volatile int generation;

    private final ExecutorService workers;
    private final LinkedHashMap<Path, SheetTexture> textures = new LinkedHashMap<>(64, 0.75f, true);
    private final Map<Path, CompletableFuture<Void>> pending = new HashMap<>();
    private final ConcurrentLinkedQueue<Decoded> decoded = new ConcurrentLinkedQueue<>();
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
        this.root = root;
        this.budgetBytes = budgetBytes;
        this.scale = scale;
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
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
                Collections.unmodifiableMap(sheets), List.copyOf(skipped));
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
        SheetTexture t = textures.get(r.file());
        if (t != null) {
            t.lastUsedFrame = frame;
            return t;
        }
        request(r);
        return null;
    }

    /** Mark a sheet as on screen this frame, so eviction leaves it alone. */
    public void touch(SheetTexture t) {
        t.lastUsedFrame = frame;
    }

    /** Start loading {@code r} in the background if it is not already resident. */
    public void request(Resolved r) {
        if (r == null || textures.containsKey(r.file()) || pending.containsKey(r.file())
                || failed.containsKey(r.file())) {
            return;
        }
        Path file = r.file();
        SpriteProfile profile = r.profile();
        double s = scale;
        int max = maxTexture;
        int ep = epoch;
        pending.put(file, CompletableFuture.runAsync(
                () -> decoded.add(decode(file, ep, profile, s, max)), workers));
    }

    private static Decoded decode(Path file, int epoch, SpriteProfile profile, double scale,
                                  int maxTexture) {
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) return new Decoded(file, epoch, null, null, "not an image ImageIO can read");
            boolean pixelArt = profile.frameWidth() <= 64;
            SheetImage layout = SheetImage.decode(img, profile.frameWidth(), profile.frameHeight(),
                    pixelArt ? 1.0 : scale, maxTexture, pixelArt);
            return new Decoded(file, epoch, layout, PixelData.of(layout.atlas()), null);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return new Decoded(file, epoch, null, null, e.toString());
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
            pending.remove(d.file());
            if (d.error() != null) {
                failed.put(d.file(), d.error());
                System.err.println("[sprites] cannot load " + d.file() + ": " + d.error());
            } else {
                SheetTexture t = new SheetTexture(
                        Texture.upload(d.pixels(), d.layout().pixelArt()), d.layout(),
                        d.file().toString());
                t.lastUsedFrame = frame;
                SheetTexture old = textures.put(d.file(), t);
                if (old != null) {
                    residentBytes -= old.bytes();
                    old.close();
                }
                residentBytes += t.bytes();
            }
            if (System.nanoTime() > deadline) break;
        }
        evict();
    }

    private void evict() {
        if (residentBytes <= budgetBytes) return;
        Iterator<Map.Entry<Path, SheetTexture>> it = textures.entrySet().iterator();
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
