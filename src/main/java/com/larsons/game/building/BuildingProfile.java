package com.larsons.game.building;

import com.larsons.game.sprite.Elevation;
import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What a kind of building is, from its {@code profile.json} (3D-Modeling's
 * {@code buildings/buildings.py} writes it beside the building's views): its
 * parts - each coloured and patterned by the building's {@link BuildingLook}
 * - how big each of its 24 views is and where its ground point is in them,
 * how deep its pixels can be, what of it is solid, where its door is, whether
 * it has an inside, and its turning part (a windmill's sails, a water wheel).
 *
 * <p>Measured as the model is: metres, its front towards model -y, which
 * {@link Building} turns to face its heading.
 */
public final class BuildingProfile {

    /** One part, as the building comes: its name, what the editor calls it, its colour and its pattern. */
    public record Part(String key, String label, int colour, String texture, boolean glow) {}

    /** A view's size in pixels and the pixel its ground point is at. */
    public record Frame(int width, int height, double anchorX, double anchorY, double angle) {}

    /** A solid piece of the footprint: a box {@code w × d} round {@code (x, y)}, or a circle. */
    public record Shape(boolean circle, double x, double y, double w, double d) {
        /** How far from the building's middle it reaches. */
        public double reach() {
            return circle ? Math.hypot(x, y) + w : Math.hypot(Math.abs(x) + w / 2, Math.abs(y) + d / 2);
        }
    }

    /** The turning part: frames in its strip, degrees over them, degrees a second. */
    public record Anim(int frames, double degrees, double speed) {}

    private final String key, label;
    private final double pixel, texel, depthMin, depthStep;
    private final Map<Elevation, Frame> frames;
    private final List<Part> parts;
    private final List<Shape> footprint;
    private final double[] door;
    private final boolean interior;
    private final Anim anim;

    public BuildingProfile(String key, String label, double pixel, double texel, double depthMin, double depthStep,
                           Map<Elevation, Frame> frames, List<Part> parts, List<Shape> footprint, double[] door,
                           boolean interior, Anim anim) {
        this.key = key;
        this.label = label;
        this.pixel = pixel;
        this.texel = texel;
        this.depthMin = depthMin;
        this.depthStep = depthStep;
        this.frames = Collections.unmodifiableMap(new EnumMap<>(frames));
        this.parts = List.copyOf(parts);
        this.footprint = List.copyOf(footprint);
        this.door = door;
        this.interior = interior;
        this.anim = anim;
    }

    public String key() { return key; }

    public String label() { return label; }

    /** Metres a pixel of its views spans. */
    public double pixel() { return pixel; }

    /** Metres a texel of a pattern spans. */
    public double texel() { return texel; }

    /** The depth a pixel's alpha of 1 stands for (metres from its ground point's plane, + away). */
    public double depthMin() { return depthMin; }

    /** Metres a step of a pixel's alpha. */
    public double depthStep() { return depthStep; }

    public Frame frame(Elevation e) { return frames.get(e); }

    public List<Part> parts() { return parts; }

    /** Its part called {@code key}, or null. */
    public Part part(String key) {
        for (Part p : parts) if (p.key.equals(key)) return p;
        return null;
    }

    /** What of it is solid, in its own metres. */
    public List<Shape> footprint() { return footprint; }

    /** Where she stands to go in, in its own metres ({@code x}, {@code y}), or null. */
    public double[] door() { return door == null ? null : door.clone(); }

    /** Whether it has an inside to go into. */
    public boolean interior() { return interior; }

    /** Its turning part, or null. */
    public Anim anim() { return anim; }

    /** How far from its middle its footprint reaches. */
    public double reach() {
        double r = 0.5;
        for (Shape s : footprint) r = Math.max(r, s.reach());
        return r;
    }

    /** How tall it stands, about: the side view's top. */
    public double height() {
        Frame f = frames.get(Elevation.SIDE);
        return f == null ? 4 : f.anchorY() * pixel;
    }

    /** Read {@code dir/profile.json}. */
    public static BuildingProfile load(Path dir) throws IOException {
        return parse(Files.readString(dir.resolve("profile.json")));
    }

    @SuppressWarnings("unchecked")
    public static BuildingProfile parse(String text) {
        Map<String, Object> o = Json.asObject(Json.parse(text));
        Map<Elevation, Frame> frames = new EnumMap<>(Elevation.class);
        Map<String, Object> el = Json.obj(o, "elevations");
        if (el != null) {
            for (Map.Entry<String, Object> e : el.entrySet()) {
                Elevation k = Elevation.byKey(e.getKey());
                Map<String, Object> f = Json.asObject(e.getValue());
                if (k != null) {
                    frames.put(k, new Frame((int) Json.num(f, "width", 0), (int) Json.num(f, "height", 0),
                            Json.num(f, "anchorX", 0), Json.num(f, "anchorY", 0), Json.num(f, "angle", 0)));
                }
            }
        }
        List<Part> parts = new ArrayList<>();
        Object pa = o.get("parts");
        if (pa instanceof List<?> l) {
            for (Object x : l) {
                Map<String, Object> p = Json.asObject(x);
                parts.add(new Part(Json.str(p, "key", "part"), Json.str(p, "label", "Part"),
                        BuildingLook.parseColour(Json.str(p, "colour", "#c0c0c0"), 0xC0C0C0),
                        Json.str(p, "texture", "smooth"), Json.bool(p, "glow", false)));
            }
        }
        List<Shape> shapes = new ArrayList<>();
        Object fp = o.get("footprint");
        if (fp instanceof List<?> l) {
            for (Object x : l) {
                List<Object> s = (List<Object>) x;
                String kind = String.valueOf(s.get(0));
                double a = ((Number) s.get(1)).doubleValue(), b = ((Number) s.get(2)).doubleValue();
                if (kind.equals("circle")) {
                    shapes.add(new Shape(true, a, b, ((Number) s.get(3)).doubleValue(), 0));
                } else {
                    shapes.add(new Shape(false, a, b, ((Number) s.get(3)).doubleValue(),
                            ((Number) s.get(4)).doubleValue()));
                }
            }
        }
        double[] door = null;
        if (o.get("door") instanceof List<?> d && d.size() >= 2) {
            door = new double[]{((Number) d.get(0)).doubleValue(), ((Number) d.get(1)).doubleValue()};
        }
        Anim anim = null;
        Map<String, Object> an = Json.obj(o, "anim");
        if (an != null) {
            anim = new Anim((int) Json.num(an, "frames", 1), Json.num(an, "degrees", 90), Json.num(an, "speed", 30));
        }
        Map<String, Object> depth = Json.obj(o, "depth");
        return new BuildingProfile(Json.str(o, "key", "building"), Json.str(o, "label", "Building"),
                Json.num(o, "pixel", 0.0375), Json.num(o, "texel", 0.0375), Json.num(depth, "min", -10),
                Json.num(depth, "step", 0.08), frames, parts, shapes, door, Json.bool(o, "interior", false), anim);
    }
}
