package com.larsons.game.sprite;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * How one piece of scenery - a tree, a grass tuft, a rock - was drawn, and
 * how it stands in the world: the {@code profile.json} beside its sheet
 * ({@code assets/scenery/<sprite>/profile.json}, see {@link ScenerySprites}).
 *
 * <pre>
 *   {"kind": "tree", "frameWidth": 128, "frameHeight": 128, "frames": 48, "fps": 12,
 *    "frameWorldSize": 4.8, "radius": 0.3, "shadowRadius": 1.6, "facing": "cross"}
 * </pre>
 *
 * <ul>
 *   <li>{@code kind} - {@code tree}, {@code tuft} or {@code rock}: what the
 *       demo's layout plants where ({@code world/SceneryLayout}).</li>
 *   <li>{@code frameWidth}, {@code frameHeight} - one frame's pixels; the
 *       sheet holds the frames left to right, then top to bottom.</li>
 *   <li>{@code frames}, {@code fps} - the loop (a rock has one frame). 0
 *       frames, or none given: every cell of the sheet's grid.</li>
 *   <li>{@code frameWorldSize} - the metres one frame spans across. The
 *       frame's bottom edge is the ground and its middle column the plant's
 *       axis, which is how the art is rendered (from the side, orthographic,
 *       the ground on the bottom edge).</li>
 *   <li>{@code radius} - metres round the axis the player cannot walk into
 *       (a trunk, a rock); 0 for grass, which she walks through.</li>
 *   <li>{@code shadowRadius} - a soft round shadow on the ground under it; 0
 *       for none.</li>
 *   <li>{@code facing} - {@code cross}: two cards crossing at right angles
 *       (the trees and the grass, like Minecraft's plants); {@code camera}:
 *       one card turned to face the camera, as the characters and the items
 *       lying about are (the rocks: a low, wide rock drawn as a cross is an
 *       X from above). Unset: {@code camera} for a rock, {@code cross} for
 *       the rest.</li>
 * </ul>
 *
 * <p>Anything the file leaves out keeps its kind's default (the sizes the
 * 3D-Modeling scripts draw at); a file with no {@code kind} is taken to be
 * the kind its folder's name suggests ({@code oak_tree}, {@code grass_tuft_a},
 * {@code mossy_rock}). Pure data: nothing here touches the GPU.
 */
public record SceneryProfile(Kind kind, int frameWidth, int frameHeight, int frames, double fps,
                             double frameWorldSize, double radius, double shadowRadius, boolean facesCamera) {

    public static final String FILE_NAME = "profile.json";

    /** What a piece of scenery is - which decides where the layout puts it. */
    public enum Kind {
        TREE("tree"), TUFT("tuft"), ROCK("rock");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        /** The name in {@code profile.json}: {@code tree}, {@code tuft}, {@code rock}. */
        public String key() { return key; }

        /** The kind called {@code key} (any case), or null. */
        public static Kind byKey(String key) {
            if (key == null) return null;
            String k = key.trim().toLowerCase(Locale.ROOT);
            for (Kind kind : values()) if (kind.key.equals(k)) return kind;
            return null;
        }

        /**
         * The kind a folder's name suggests: a tree, grass, or a rock, stone
         * or boulder - and a tree when it says nothing.
         */
        public static Kind guess(String folderName) {
            String n = folderName.toLowerCase(Locale.ROOT);
            if (n.contains("tuft") || n.contains("grass") || n.contains("fern") || n.contains("flower")) return TUFT;
            if (n.contains("rock") || n.contains("stone") || n.contains("boulder") || n.contains("pebble")) return ROCK;
            return TREE;
        }
    }

    /** What a {@code kind} is drawn at unless its profile says otherwise. */
    public static SceneryProfile defaults(Kind kind) {
        return switch (kind) {
            case TREE -> new SceneryProfile(Kind.TREE, 128, 128, 0, 12, 4.8, 0.3, 1.6, false);
            case TUFT -> new SceneryProfile(Kind.TUFT, 32, 32, 0, 12, 0.6, 0, 0, false);
            case ROCK -> new SceneryProfile(Kind.ROCK, 128, 128, 1, 12, 2.4, 0.5, 0.9, true);
        };
    }

    /**
     * Read {@code profile.json} from a sprite's folder; anything missing (the
     * whole file, if there is none or it cannot be read) keeps the defaults of
     * the kind it names or its folder's name suggests.
     */
    public static SceneryProfile load(Path folder) {
        String name = folder.getFileName() == null ? "" : folder.getFileName().toString();
        Path file = folder.resolve(FILE_NAME);
        if (Files.isRegularFile(file)) {
            try {
                return parse(Files.readString(file, StandardCharsets.UTF_8), Kind.guess(name));
            } catch (IOException | RuntimeException e) {
                System.err.println("[scenery] ignoring " + file + ": " + e.getMessage());
            }
        }
        return defaults(Kind.guess(name));
    }

    /** A profile from its JSON; {@code fallbackKind} when it names no kind (or one this game does not know). */
    public static SceneryProfile parse(String json, Kind fallbackKind) {
        Map<String, Object> j = Json.asObject(Json.parse(json));
        Kind kind = Kind.byKey(Json.str(j, "kind", null));
        if (kind == null) kind = fallbackKind;
        SceneryProfile d = defaults(kind);
        int fw = Math.max(1, (int) Json.num(j, "frameWidth", d.frameWidth));
        int fh = Math.max(1, (int) Json.num(j, "frameHeight", d.frameHeight));
        int frames = Math.max(0, (int) Json.num(j, "frames", d.frames));
        double fps = Json.num(j, "fps", d.fps);
        double size = Json.num(j, "frameWorldSize", d.frameWorldSize);
        double radius = Math.max(0, Json.num(j, "radius", d.radius));
        double shadow = Math.max(0, Json.num(j, "shadowRadius", d.shadowRadius));
        String facing = Json.str(j, "facing", "").trim().toLowerCase(Locale.ROOT);
        boolean facesCamera = facing.equals("camera") || (!facing.equals("cross") && d.facesCamera);
        return new SceneryProfile(kind, fw, fh, frames, fps > 0 ? fps : d.fps, size > 0 ? size : d.frameWorldSize,
                radius, shadow, facesCamera);
    }

    /** The same profile with {@code frames} frames (the catalogue fills in "every cell of the sheet"). */
    public SceneryProfile withFrames(int frames) {
        return new SceneryProfile(kind, frameWidth, frameHeight, frames, fps, frameWorldSize, radius, shadowRadius,
                facesCamera);
    }

    /** Metres one frame stands tall: {@link #frameWorldSize()} across, in the frame's own proportions. */
    public double frameWorldHeight() {
        return frameWorldSize * frameHeight / (double) frameWidth;
    }

    /** Whether the player is kept out of it (a trunk, a rock) rather than walking through it (grass). */
    public boolean solid() {
        return radius > 0;
    }
}
