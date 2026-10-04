package com.larsons.game.importer;

import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.SheetImage;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.SpriteNames;
import com.larsons.game.sprite.SpriteProfile;
import com.larsons.game.util.Json;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

/**
 * The drag-and-drop file system: turns whatever was dropped on the window —
 * sprite sheets, whole folders of them, or loose numbered frames straight out
 * of a Blender render — into canonical sheets saved in the repository under
 * {@code assets/sprites/<slot>/<item>/}.
 *
 * <p>Two steps, so the player sees what will happen before anything is
 * written: {@link #plan} reads names and image headers only (fast, no
 * pixels), and {@link #save} does the copying and stitching (slow, run off
 * the render thread).
 *
 * <ul>
 *   <li>A file whose name says state, elevation and direction is one sheet,
 *       copied as it is.</li>
 *   <li>Several files with the same state, elevation and direction and a
 *       frame number ({@code walk_side_n_0001.png …}) are one animation's
 *       frames, stitched into a sheet in frame-number order.</li>
 *   <li>{@code icon.png} is the world sprite of a pickup, saved to
 *       {@code items/<item>/icon.png}.</li>
 *   <li>{@code profile.json} (the framing) is carried along.</li>
 *   <li>Anything else is listed as not recognised and left alone.</li>
 * </ul>
 */
public final class SpriteImport {

    private SpriteImport() {}

    /** One output sheet: the view it is, and the file(s) it is made from. */
    public record SheetPlan(AnimState state, Elevation elevation, Facing facing,
                            List<Path> files, int width, int height) {

        public String fileName() {
            return SpriteNames.fileName(state, elevation, facing);
        }

        /** Loose frames to stitch, rather than one sheet to copy. */
        public boolean sequence() {
            return files.size() > 1;
        }

        /** Frames this sheet holds, given the expected frame size. */
        public int frames(int frameW, int frameH) {
            if (sequence()) return files.size();
            int[] f = SheetImage.frameSize(width, height, frameW, frameH);
            return Math.max(1, (width / Math.max(1, f[0])) * (height / Math.max(1, f[1])));
        }

        /** Frame size in pixels. */
        public int[] frameSize(int frameW, int frameH) {
            if (sequence()) return new int[]{width, height};
            return SheetImage.frameSize(width, height, frameW, frameH);
        }
    }

    /**
     * What a drop contained.
     *
     * @param slotGuess    the slot the names suggested (the player can change it)
     * @param itemGuess    the item name the names suggested
     * @param sheets       one entry per output sheet, sorted state → elevation → direction
     * @param icon         a pickup world sprite, or null
     * @param profile      a {@code profile.json}, or null
     * @param unrecognised files whose names could not be read, relative to the drop
     * @param warnings     anything the player should know before saving
     */
    public record Plan(Slot slotGuess, String itemGuess, List<SheetPlan> sheets, Path icon,
                       Path profile, List<String> unrecognised, List<String> warnings) {

        public boolean empty() {
            return sheets.isEmpty() && icon == null;
        }

        /** How many of the 144 views each state has, for the summary. */
        public Map<AnimState, Integer> viewsPerState() {
            Map<AnimState, Integer> out = new TreeMap<>();
            for (SheetPlan s : sheets) out.merge(s.state(), 1, Integer::sum);
            return out;
        }
    }

    /** What a save wrote. */
    public record Result(Path folder, int written, int replaced, List<String> problems) {}

    // --- planning --------------------------------------------------------------------

    /** Read the dropped paths (files and folders) into a plan. Names and headers only. */
    public static Plan plan(List<Path> dropped) {
        record Found(Path file, String rel, SpriteNames.Parsed parsed) {}
        List<Found> found = new ArrayList<>();
        Path icon = null, profile = null;
        List<String> unrecognised = new ArrayList<>();
        Map<String, Integer> itemVotes = new HashMap<>();
        Map<Slot, Integer> slotVotes = new HashMap<>();

        for (Path root : dropped) {
            List<Path> files = new ArrayList<>();
            Path base;
            if (Files.isDirectory(root)) {
                base = root.getParent() == null ? root : root.getParent();
                // A dropped folder is usually one item ("red_cap/"), and often
                // sits in a folder named for its layer ("hat/red_cap/",
                // "carry_right/sword/") — both are strong hints.
                String folder = root.getFileName() == null ? "" : root.getFileName().toString();
                SpriteNames.Parsed named = SpriteNames.parse(folder);
                if (named.state() == null && named.elevation() == null && named.facing() == null
                        && !(named.slot() != null && named.leftovers().isEmpty())) {
                    itemVotes.merge(SpriteNames.sanitize(folder), 1000, Integer::sum);
                }
                if (named.slot() != null) slotVotes.merge(named.slot(), 500, Integer::sum);
                Slot parentSlot = Slot.byKey(base.getFileName() == null ? ""
                        : base.getFileName().toString());
                if (parentSlot == null && base.getFileName() != null) {
                    parentSlot = SpriteNames.parse(base.getFileName().toString()).slot();
                }
                if (parentSlot != null) slotVotes.merge(parentSlot, 1000, Integer::sum);
                try (Stream<Path> walk = Files.walk(root, 6)) {
                    walk.filter(Files::isRegularFile).sorted().forEach(files::add);
                } catch (IOException e) {
                    unrecognised.add(root.getFileName() + " (unreadable: " + e.getMessage() + ")");
                }
            } else {
                base = root.getParent();
                files.add(root);
            }
            for (Path f : files) {
                String name = f.getFileName().toString();
                String lower = name.toLowerCase();
                // Relative to the drop, including the dropped folder's own name,
                // which is usually the item's name ("hero/walk_side_n.png").
                String rel = base == null ? name : base.relativize(f).toString();
                if (lower.equals(SpriteProfile.FILE_NAME)) {
                    profile = f;
                    continue;
                }
                if (!lower.endsWith(".png")) {
                    if (!lower.startsWith(".")) unrecognised.add(rel + " (not a .png)");
                    continue;
                }
                if (lower.equals("icon.png") || lower.startsWith("icon_") || lower.startsWith("icon.")) {
                    icon = f;
                    vote(itemVotes, SpriteNames.parse(parentPart(rel)).leftovers());
                    continue;
                }
                SpriteNames.Parsed p = SpriteNames.parse(rel);
                if (!p.complete()) {
                    unrecognised.add(rel + missing(p));
                    continue;
                }
                found.add(new Found(f, rel, p));
                vote(itemVotes, p.leftovers());
                if (p.slot() != null) slotVotes.merge(p.slot(), 1, Integer::sum);
            }
        }

        // Group into sheets: a view with numbered frames is a sequence.
        Map<String, List<Found>> byView = new LinkedHashMap<>();
        for (Found f : found) {
            String key = SpriteLibrary.key(f.parsed.state(), f.parsed.elevation(), f.parsed.facing());
            byView.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }
        List<SheetPlan> sheets = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (List<Found> group : byView.values()) {
            SpriteNames.Parsed p = group.get(0).parsed;
            List<Found> ordered = new ArrayList<>(group);
            if (group.size() > 1) {
                ordered.sort(Comparator.comparing((Found f) -> f.parsed.frame() == null ? -1 : f.parsed.frame())
                        .thenComparing(f -> f.rel));
                long unnumbered = group.stream().filter(f -> f.parsed.frame() == null).count();
                if (unnumbered > 0) {
                    warnings.add(SpriteNames.fileName(p.state(), p.elevation(), p.facing())
                            + ": " + group.size() + " files for one view and " + unnumbered
                            + " have no frame number — treated as a sequence in name order");
                }
            }
            int[] size = imageSize(ordered.get(0).file);
            if (size == null) {
                unrecognised.add(ordered.get(0).rel + " (not a readable PNG)");
                continue;
            }
            sheets.add(new SheetPlan(p.state(), p.elevation(), p.facing(),
                    ordered.stream().map(Found::file).toList(), size[0], size[1]));
        }
        sheets.sort(Comparator.comparing(SheetPlan::state).thenComparing(SheetPlan::elevation)
                .thenComparing(SheetPlan::facing));

        String item = itemVotes.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(dropped.isEmpty() ? "item"
                        : SpriteNames.sanitize(stripExtension(dropped.get(0).getFileName().toString())));
        // A pickup's name says which hand it goes in ("sword" → right hand).
        com.larsons.game.world.ItemDef known = com.larsons.game.world.ItemDef.byId(SpriteNames.sanitize(item));
        if (known != null) slotVotes.merge(known.carrySlot(), 100, Integer::sum);
        Slot slot = slotVotes.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(icon != null && sheets.isEmpty() ? null : Slot.BODY);
        checkFrameCounts(sheets, warnings);
        return new Plan(slot, SpriteNames.sanitize(item), sheets, icon, profile,
                unrecognised, warnings);
    }

    private static String parentPart(String rel) {
        int i = Math.max(rel.lastIndexOf('/'), rel.lastIndexOf('\\'));
        return i < 0 ? "" : rel.substring(0, i);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static void vote(Map<String, Integer> votes, List<String> leftovers) {
        if (leftovers.isEmpty()) return;
        votes.merge(String.join("_", leftovers), 1, Integer::sum);
    }

    private static String missing(SpriteNames.Parsed p) {
        List<String> m = new ArrayList<>();
        if (p.state() == null) m.add("state");
        if (p.elevation() == null) m.add("elevation");
        if (p.facing() == null) m.add("direction");
        return " (no " + String.join(", ", m) + " in the name)";
    }

    /** Every view of one state should have the same frame count. */
    private static void checkFrameCounts(List<SheetPlan> sheets, List<String> warnings) {
        Map<AnimState, Map<Integer, Integer>> counts = new TreeMap<>();
        SpriteProfile d = SpriteProfile.defaults();
        for (SheetPlan s : sheets) {
            counts.computeIfAbsent(s.state(), k -> new TreeMap<>())
                    .merge(s.frames(d.frameWidth(), d.frameHeight()), 1, Integer::sum);
        }
        counts.forEach((state, byCount) -> {
            if (byCount.size() > 1) {
                warnings.add(state.key() + ": views disagree on frame count " + byCount.keySet()
                        + " — every layer must match the body frame for frame");
            }
        });
    }

    /** Width and height from the PNG header, without decoding pixels. */
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

    // --- saving ----------------------------------------------------------------------

    /** Where a plan saved as {@code slot/item} goes. */
    public static Path target(Path spritesRoot, Slot slot, String item) {
        return spritesRoot.resolve(slot.key()).resolve(SpriteNames.sanitize(item));
    }

    /** How many existing files a save would overwrite. */
    public static int wouldReplace(Plan plan, Path spritesRoot, Slot slot, String item) {
        return wouldReplace(plan, spritesRoot, spritesRoot, slot, item);
    }

    /** {@link #wouldReplace(Plan, Path, Slot, String)} with the sheets and the pickup icon in different folders. */
    public static int wouldReplace(Plan plan, Path sheetsRoot, Path iconRoot, Slot slot, String item) {
        Path dir = target(sheetsRoot, slot, item);
        int n = 0;
        for (SheetPlan s : plan.sheets()) if (Files.exists(dir.resolve(s.fileName()))) n++;
        if (plan.icon() != null && Files.exists(iconTarget(iconRoot, item))) n++;
        return n;
    }

    static Path iconTarget(Path spritesRoot, String item) {
        return spritesRoot.resolve(SpriteLibrary.ITEMS_FOLDER).resolve(SpriteNames.sanitize(item))
                .resolve("icon.png");
    }

    /**
     * Write the plan into {@code assets/sprites/<slot>/<item>/}. Sheets are
     * copied byte for byte; sequences are stitched into one sheet, a strip if
     * it fits in 16 384 pixels and a grid otherwise. When the frames are not
     * the default 512×512, a {@code profile.json} recording their size is
     * written (or updated) so the game slices them right.
     *
     * @param progress told the index of each sheet as it finishes
     */
    public static Result save(Plan plan, Path spritesRoot, Slot slot, String item,
                              IntConsumer progress) {
        return save(plan, spritesRoot, spritesRoot, slot, item, progress);
    }

    /**
     * {@link #save(Plan, Path, Slot, String, IntConsumer)} with the sheets
     * going into {@code sheetsRoot} (a body's own sprites folder, for an item
     * worn on it) and a pickup icon into {@code iconRoot} (always the default
     * folder: pickups are looked up there).
     */
    public static Result save(Plan plan, Path sheetsRoot, Path iconRoot, Slot slot, String item,
                              IntConsumer progress) {
        Path dir = target(sheetsRoot, slot, item);
        List<String> problems = new ArrayList<>();
        int written = 0, replaced = 0;
        int[] frameSize = null;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return new Result(dir, 0, 0, List.of("cannot create " + dir + ": " + e.getMessage()));
        }
        SpriteProfile d = SpriteProfile.defaults();
        for (int i = 0; i < plan.sheets().size(); i++) {
            SheetPlan s = plan.sheets().get(i);
            Path out = dir.resolve(s.fileName());
            try {
                if (Files.exists(out)) replaced++;
                if (s.sequence()) {
                    stitch(s.files(), out);
                } else {
                    Files.copy(s.files().get(0), out, StandardCopyOption.REPLACE_EXISTING);
                }
                written++;
                if (frameSize == null) frameSize = s.frameSize(d.frameWidth(), d.frameHeight());
            } catch (IOException | RuntimeException e) {
                problems.add(s.fileName() + ": " + e.getMessage());
            }
            if (progress != null) progress.accept(i + 1);
        }
        if (plan.icon() != null) {
            Path out = iconTarget(iconRoot, item);
            try {
                Files.createDirectories(out.getParent());
                if (Files.exists(out)) replaced++;
                Files.copy(plan.icon(), out, StandardCopyOption.REPLACE_EXISTING);
                written++;
            } catch (IOException e) {
                problems.add("icon.png: " + e.getMessage());
            }
        }
        try {
            writeProfile(plan, dir, frameSize);
        } catch (IOException | RuntimeException e) {
            problems.add(SpriteProfile.FILE_NAME + ": " + e.getMessage());
        }
        return new Result(dir, written, replaced, problems);
    }

    /** Stitch loose frames into one sheet, in the order given. */
    static void stitch(List<Path> frames, Path out) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        int fw = -1, fh = -1;
        for (Path f : frames) {
            BufferedImage img = ImageIO.read(f.toFile());
            if (img == null) throw new IOException(f.getFileName() + " is not a readable image");
            if (fw < 0) {
                fw = img.getWidth();
                fh = img.getHeight();
            }
            images.add(img);
        }
        int n = images.size();
        int cols = (long) n * fw <= 16384 ? n : Math.max(1, (int) Math.ceil(Math.sqrt(n)));
        int rows = (int) Math.ceil(n / (double) cols);
        BufferedImage sheet = new BufferedImage(cols * fw, rows * fh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < n; i++) {
            g.drawImage(images.get(i), (i % cols) * fw, (i / cols) * fh, fw, fh, null);
        }
        g.dispose();
        ImageIO.write(sheet, "png", out.toFile());
    }

    /**
     * Carry a dropped profile over, and record a non-default frame size so a
     * 256-pixel grid is not mistaken for 512-pixel frames.
     */
    private static void writeProfile(Plan plan, Path dir, int[] frameSize) throws IOException {
        Path out = dir.resolve(SpriteProfile.FILE_NAME);
        if (plan.profile() != null) {
            Files.copy(plan.profile(), out, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        SpriteProfile d = SpriteProfile.defaults();
        if (frameSize == null || (frameSize[0] == d.frameWidth() && frameSize[1] == d.frameHeight())) {
            return;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        if (Files.isRegularFile(out)) {
            json.putAll(Json.asObject(Json.parse(Files.readString(out, StandardCharsets.UTF_8))));
        }
        json.put("frameWidth", frameSize[0]);
        json.put("frameHeight", frameSize[1]);
        Files.writeString(out, Json.stringify(json), StandardCharsets.UTF_8);
    }
}
