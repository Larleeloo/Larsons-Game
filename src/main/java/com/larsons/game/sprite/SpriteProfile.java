package com.larsons.game.sprite;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How a set of sprite sheets was framed when it was rendered, which is what
 * the game needs to stand the picture back up in the 3D world at the right
 * size and in the right place.
 *
 * <p><b>The convention.</b> Each frame is an orthographic render whose camera
 * is aimed at the character's <em>pivot</em> — a point {@link #pivotHeight()}
 * metres above the feet — and whose view is {@link #frameWorldSize()} metres
 * wide. The defaults (a 2.4 m frame aimed 0.9 m up) put a 1.8 m character in
 * the middle of a square frame with 0.3 m to spare above and below in the
 * side view.
 *
 * <p>From that the game knows where the feet are in every picture, without
 * being told: straight below the pivot by {@code pivotHeight × cos(angle)} on
 * screen, which is 87.5 % of the way down a side-view frame, 76.5 % for the
 * 45° set and dead centre from above. The billboard is placed so that point
 * sits on the character's position on the ground, which keeps the feet on
 * the shadow from every camera angle.
 *
 * <p>Renders framed some other way are described with a {@code profile.json}
 * next to the sheets (see {@code assets/sprites/README.md}); anything it leaves
 * out keeps the default.
 *
 * <p><b>Pixel art</b> is drawn with hard pixel edges (nearest-neighbour, no
 * mipmaps) and never shrunk by the sprite-resolution setting. Frames of 64
 * pixels or fewer are taken to be pixel art; {@code "pixelArt": true} says so
 * for bigger ones (the 128-pixel renders), {@code false} for small renders
 * that are not.
 */
public final class SpriteProfile {

    public static final String FILE_NAME = "profile.json";

    private int frameWidth = 512;
    private int frameHeight = 512;
    private double frameWorldSize = 2.4;
    private double pivotHeight = 0.9;
    private double fps = AnimState.DEFAULT_FPS;
    private Boolean pixelArt;                        // null: decided by the frame size
    private final Map<AnimState, Double> stateFps = new EnumMap<>(AnimState.class);
    private final Map<Elevation, double[]> anchors = new EnumMap<>(Elevation.class);

    /** The standard 512×512 framing. */
    public static SpriteProfile defaults() {
        return new SpriteProfile();
    }

    /** The same framing at a different pixel size (the 32×32 fallback uses this). */
    public SpriteProfile withFrameSize(int width, int height) {
        SpriteProfile p = copy();
        p.frameWidth = width;
        p.frameHeight = height;
        return p;
    }

    public SpriteProfile copy() {
        SpriteProfile p = new SpriteProfile();
        p.frameWidth = frameWidth;
        p.frameHeight = frameHeight;
        p.frameWorldSize = frameWorldSize;
        p.pivotHeight = pivotHeight;
        p.fps = fps;
        p.pixelArt = pixelArt;
        p.stateFps.putAll(stateFps);
        anchors.forEach((k, v) -> p.anchors.put(k, v.clone()));
        return p;
    }

    public int frameWidth() { return frameWidth; }

    public int frameHeight() { return frameHeight; }

    /** Whether the sheets are pixel art: hard pixel edges, never shrunk (see the class notes). */
    public boolean pixelArt() {
        return pixelArt != null ? pixelArt : frameWidth <= 64;
    }

    /** Metres of world one frame is wide. */
    public double frameWorldSize() { return frameWorldSize; }

    /** Metres of world one frame is tall. */
    public double frameWorldHeight() {
        return frameWorldSize * frameHeight / (double) Math.max(1, frameWidth);
    }

    /** How far above the feet the render camera was aimed, in metres. */
    public double pivotHeight() { return pivotHeight; }

    /** Playback rate of sheets with no rate of their own state's (a cutscene's clips). */
    public double fps() { return fps; }

    /** Playback rate for one state's sheets. */
    public double fps(AnimState state) {
        return stateFps.getOrDefault(state, fps);
    }

    /**
     * Where the feet are in a frame of the {@code elevation} set, as a
     * fraction of the frame from the top-left corner: {@code {u, v}}.
     */
    public double[] anchor(Elevation elevation) {
        double[] override = anchors.get(elevation);
        if (override != null) return override.clone();
        double below = pivotHeight * Math.cos(Math.toRadians(elevation.renderAngle()));
        return new double[]{0.5, 0.5 + below / frameWorldHeight()};
    }

    /**
     * Read {@code profile.json} from a sheet folder, falling back to
     * {@code base} for anything missing (or entirely, when there is no file).
     */
    public static SpriteProfile load(Path folder, SpriteProfile base) {
        SpriteProfile p = base.copy();
        Path file = folder.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return p;
        try {
            p.apply(Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8))));
        } catch (IOException | RuntimeException e) {
            System.err.println("[sprites] ignoring " + file + ": " + e.getMessage());
        }
        return p;
    }

    void apply(Map<String, Object> json) {
        frameWidth = (int) Json.num(json, "frameWidth", frameWidth);
        frameHeight = (int) Json.num(json, "frameHeight", frameHeight);
        frameWorldSize = Json.num(json, "frameWorldSize", frameWorldSize);
        pivotHeight = Json.num(json, "pivotHeight", pivotHeight);
        fps = Json.num(json, "fps", fps);
        if (json.get("pixelArt") instanceof Boolean b) pixelArt = b;
        Map<String, Object> perState = Json.obj(json, "stateFps");
        if (perState != null) {
            for (AnimState s : AnimState.values()) {
                double v = Json.num(perState, s.key(), -1);
                if (v > 0) stateFps.put(s, v);
            }
        }
        Map<String, Object> anchorJson = Json.obj(json, "anchors");
        if (anchorJson != null) {
            for (Elevation e : Elevation.values()) {
                Object v = anchorJson.get(e.key());
                if (v instanceof java.util.List<?> list && list.size() == 2
                        && list.get(0) instanceof Number u && list.get(1) instanceof Number w) {
                    anchors.put(e, new double[]{u.doubleValue(), w.doubleValue()});
                }
            }
        }
        frameWidth = Math.max(1, frameWidth);
        frameHeight = Math.max(1, frameHeight);
        if (frameWorldSize <= 0) frameWorldSize = 2.4;
    }

    /** This profile as the JSON a {@code profile.json} would hold. */
    public Map<String, Object> toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("frameWidth", frameWidth);
        out.put("frameHeight", frameHeight);
        out.put("frameWorldSize", frameWorldSize);
        out.put("pivotHeight", pivotHeight);
        out.put("fps", fps);
        if (pixelArt != null) out.put("pixelArt", pixelArt);
        return out;
    }
}
