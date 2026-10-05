package com.larsons.game.cutscene;

import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.SpriteProfile;
import com.larsons.game.sprite.Variants;
import com.larsons.game.sprite.Wardrobe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The cutscene close-ups on disk: the same wardrobe items as the game sprites,
 * each a layer of its own, rendered waist-up and close for conversation clips.
 *
 * <pre>
 *   assets/closeups/&lt;slot&gt;/&lt;item&gt;_px128/&lt;clip&gt;_side_se.png    (and _px64)
 *   assets/closeups/&lt;slot&gt;/&lt;item&gt;_px128/profile.json            (the close framing)
 *   assets/closeups/&lt;slot&gt;/&lt;item&gt;_px128/variants.json           (its colours)
 * </pre>
 *
 * A clip ({@code listen}, {@code talk}, {@code explain}, {@code laugh},
 * {@code surprise}) is one looping sheet per item, from the one view a
 * conversation needs: the front-left three-quarter view, the character
 * turned a little to her left as if to someone right of the camera. A
 * character on the right of a two-shot is drawn mirrored. The sheets are
 * loaded and kept on the GPU by the game's {@link SpriteLibrary}, so a
 * cutscene's layers share its memory budget and its palettes.
 *
 * <p>As with the game sprites, a body can have a folder of its own beside
 * this one, {@code assets/closeups_<body>/} ({@code closeups_masculine/}),
 * with every item drawn close up on that body; the body a character wears
 * picks the folder its items are found in.
 */
public final class CloseupLibrary {

    /** The one view the close-ups are rendered from. */
    public static final String VIEW = "side_se";
    private static final Pattern SHEET = Pattern.compile("([a-z0-9]+(?:_[a-z0-9]+)*)_" + VIEW + "\\.png");

    /** One item folder: its clips' sheets, framing and colours. */
    public record Entry(Slot slot, String folder, SpriteProfile profile, Map<String, Path> clips,
                        Variants variants) {}

    private final Path root;
    /** Every folder's index (the default's under ""), and which folder each body is in. */
    private volatile Map<String, Map<Slot, Map<String, Entry>>> roots = Map.of();
    private volatile Map<String, String> bodyRoot = Map.of();

    public CloseupLibrary(Path root) {
        this.root = root;
        rescan();
    }

    public Path root() { return root; }

    /** Re-read the folder and every body's folder beside it (file names only). */
    public synchronized void rescan() {
        Map<String, Map<Slot, Map<String, Entry>>> next = new java.util.LinkedHashMap<>();
        next.put(SpriteLibrary.DEFAULT_ROOT, scanRoot(root));
        Path parent = root.toAbsolutePath().getParent();
        String prefix = root.getFileName() + "_";
        if (parent != null && Files.isDirectory(parent)) {
            try (Stream<Path> dirs = Files.list(parent)) {
                for (Path d : dirs.filter(Files::isDirectory).sorted().toList()) {
                    String name = d.getFileName().toString();
                    if (name.startsWith(prefix) && name.length() > prefix.length()) {
                        next.put(name.substring(prefix.length()), scanRoot(d));
                    }
                }
            } catch (IOException e) {
                System.err.println("[closeups] cannot list " + parent + ": " + e.getMessage());
            }
        }
        Map<String, String> bodies = new HashMap<>();
        for (Map.Entry<String, Map<Slot, Map<String, Entry>>> r : next.entrySet()) {
            for (String folder : r.getValue().getOrDefault(Slot.BODY, Map.of()).keySet()) {
                String body = Wardrobe.Style.base(folder);
                if (r.getKey().equals(SpriteLibrary.DEFAULT_ROOT)) bodies.putIfAbsent(body, r.getKey());
                else bodies.put(body, r.getKey());
            }
        }
        roots = Collections.unmodifiableMap(next);
        bodyRoot = Collections.unmodifiableMap(bodies);
    }

    private static Map<Slot, Map<String, Entry>> scanRoot(Path base) {
        Map<Slot, Map<String, Entry>> next = new EnumMap<>(Slot.class);
        for (Slot slot : Slot.values()) {
            Map<String, Entry> items = new TreeMap<>();
            Path dir = base.resolve(slot.key());
            if (Files.isDirectory(dir)) {
                try (Stream<Path> dirs = Files.list(dir)) {
                    for (Path d : dirs.filter(Files::isDirectory).sorted().toList()) {
                        Entry e = scan(slot, d);
                        if (!e.clips().isEmpty()) items.put(e.folder(), e);
                    }
                } catch (IOException e) {
                    System.err.println("[closeups] cannot list " + dir + ": " + e.getMessage());
                }
            }
            next.put(slot, Collections.unmodifiableMap(items));
        }
        return next;
    }

    /** The folder key (as {@link SpriteLibrary#rootOf}) a body's items are found in. */
    public String rootOf(String body) {
        if (body == null) return SpriteLibrary.DEFAULT_ROOT;
        return bodyRoot.getOrDefault(Wardrobe.Style.base(body), SpriteLibrary.DEFAULT_ROOT);
    }

    private Map<Slot, Map<String, Entry>> index(String key) {
        Map<Slot, Map<String, Entry>> i = roots.get(key);
        if (i == null) i = roots.get(SpriteLibrary.DEFAULT_ROOT);
        return i == null ? Map.of() : i;
    }

    private static Entry scan(Slot slot, Path dir) {
        Map<String, Path> clips = new TreeMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.sorted().toList()) {
                Matcher m = SHEET.matcher(f.getFileName().toString());
                if (m.matches()) clips.put(m.group(1), f);
            }
        } catch (IOException e) {
            System.err.println("[closeups] cannot list " + dir + ": " + e.getMessage());
        }
        SpriteProfile profile = SpriteProfile.load(dir, SpriteProfile.defaults().withFrameSize(128, 128));
        return new Entry(slot, dir.getFileName().toString(), profile, Collections.unmodifiableMap(clips),
                Variants.load(dir));
    }

    /**
     * The folder an item worn in {@code style} is drawn from close up: its
     * version in that pixel-art size, else the other one; the rendered style
     * has no close-ups of its own and is drawn from the 128-pixel art.
     */
    public Entry entry(Slot slot, String item, Wardrobe.Style style) {
        return entry(null, slot, item, style);
    }

    /** {@link #entry(Slot, String, Wardrobe.Style)} for an item worn on {@code body}. */
    public Entry entry(String body, Slot slot, String item, Wardrobe.Style style) {
        if (item == null) return null;
        String key = slot == Slot.BODY ? rootOf(item) : rootOf(body);
        Map<String, Entry> items = index(key).getOrDefault(slot, Map.of());
        Wardrobe.Style first = style == Wardrobe.Style.PIXEL_64 ? Wardrobe.Style.PIXEL_64 : Wardrobe.Style.PIXEL_128;
        Wardrobe.Style second = first == Wardrobe.Style.PIXEL_64 ? Wardrobe.Style.PIXEL_128 : Wardrobe.Style.PIXEL_64;
        Entry e = items.get(first.folder(item));
        return e != null ? e : items.get(second.folder(item));
    }

    /**
     * The close-up of an item worn by a body whose game sprites are in the
     * folder {@code key} ({@link SpriteLibrary#rootOf}): it comes from that
     * folder's close-ups ({@code closeups_<key>/}, the default's for the default
     * folder), or from nowhere - a body whose folder has no close-ups (yet) has
     * nothing of another body's drawn on it.
     */
    public Entry entryIn(String key, Slot slot, String item, Wardrobe.Style style) {
        if (item == null) return null;
        Map<Slot, Map<String, Entry>> idx = roots.get(key == null ? SpriteLibrary.DEFAULT_ROOT : key);
        if (idx == null) return null;
        Map<String, Entry> items = idx.getOrDefault(slot, Map.of());
        Wardrobe.Style first = style == Wardrobe.Style.PIXEL_64 ? Wardrobe.Style.PIXEL_64 : Wardrobe.Style.PIXEL_128;
        Wardrobe.Style second = first == Wardrobe.Style.PIXEL_64 ? Wardrobe.Style.PIXEL_128 : Wardrobe.Style.PIXEL_64;
        Entry e = items.get(first.folder(item));
        return e != null ? e : items.get(second.folder(item));
    }

    /** Every clip some item has, sorted. */
    public List<String> clips() {
        TreeSet<String> all = new TreeSet<>();
        for (Map<Slot, Map<String, Entry>> index : roots.values()) {
            for (Map<String, Entry> items : index.values()) {
                for (Entry e : items.values()) all.addAll(e.clips().keySet());
            }
        }
        return List.copyOf(all);
    }

    /** Whether there is anything to draw at all. */
    public boolean isEmpty() {
        for (Map<Slot, Map<String, Entry>> index : roots.values()) {
            for (Map<String, Entry> items : index.values()) if (!items.isEmpty()) return false;
        }
        return true;
    }

    /** The sheet of {@code clip} (or, if the item lacks it, its {@code fallback} clip) to load and draw. */
    public SpriteLibrary.Resolved resolve(Entry e, String clip, String fallback, boolean mirrored) {
        if (e == null) return null;
        Path p = e.clips().get(clip);
        if (p == null && fallback != null) p = e.clips().get(fallback);
        return p == null ? null : new SpriteLibrary.Resolved(p, mirrored, e.profile());
    }

    /**
     * The colour choice for a layer of item {@code e} worn in {@code slot}:
     * the slot's own colour (an option or {@code #rrggbb}) and the skin tone,
     * as {@link Variants#recolor} takes them.
     */
    public static Map<String, String> choice(Slot slot, String own, String skin) {
        Map<String, String> c = new HashMap<>();
        if (own != null && slot != Slot.BODY) c.put(Variants.OWN, own);
        if (skin != null) c.put(Variants.SKIN, skin);
        return c;
    }
}
