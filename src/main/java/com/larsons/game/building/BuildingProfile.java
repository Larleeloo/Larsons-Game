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

    /**
     * One part, as the building comes: its name, what the editor calls it,
     * its colour and its pattern; {@code glow}: lit from inside at night (a
     * window), {@code always}: lit day and night (a fire).
     */
    public record Part(String key, String label, int colour, String texture, boolean glow, boolean always) {
        public Part(String key, String label, int colour, String texture, boolean glow) {
            this(key, label, colour, texture, glow, false);
        }
    }

    /** What kind of thing it is: a building, a piece of furniture, or a piece of a room ({@link Room}). */
    public static final String BUILDING = "building", FURNITURE = "furniture", ROOM = "room", FIXTURE = "fixture",
            TOWN = "town";

    /** A view's size in pixels and the pixel its ground point is at. */
    public record Frame(int width, int height, double anchorX, double anchorY, double angle) {}

    /** A solid piece of the footprint: a box {@code w × d} round {@code (x, y)}, or a circle. */
    public record Shape(boolean circle, double x, double y, double w, double d) {
        /** How far from the building's middle it reaches. */
        public double reach() {
            return circle ? Math.hypot(x, y) + w : Math.hypot(Math.abs(x) + w / 2, Math.abs(y) + d / 2);
        }
    }

    /**
     * Something of it she walks up onto - a porch's deck, the step at a
     * door, a flight of stairs a step at a time: a box {@code w × d} round
     * {@code (x, y)}, its top {@code top} metres up.
     */
    public record Step(double x, double y, double w, double d, double top) {
        /** Whether {@code (mx, my)} (its own metres) is over it. */
        public boolean over(double mx, double my) {
            return Math.abs(mx - x) <= w / 2 && Math.abs(my - y) <= d / 2;
        }
    }

    /** The turning part: frames in its strip, degrees over them, degrees a second. */
    public record Anim(int frames, double degrees, double speed) {}

    private final String key, label, group;
    private final Room.Spec room;
    private final double pixel, texel, depthMin, depthStep;
    private final Map<Elevation, Frame> frames;
    private final List<Part> parts;
    private final List<Shape> footprint;
    private final double[] door;
    private final boolean interior;
    private final Anim anim;
    /** A model-only kind's height (no views to measure), or NaN. */
    private double modelHeight = Double.NaN;
    /** Whether it is only ever a 3D model: no views of it were drawn. */
    private boolean modelOnly;
    /** A piece of furniture's shelf in the editor (kitchen, decor, plants, farm, clutter), or null: furniture. */
    private String shelf;
    /** A light's fitting: where its flame is (its own metres: x, y, z up), or null. */
    private double[] flame;
    /** A light's fitting that stands on the ground (not hung up or set on something). */
    private boolean stands;
    /** What she walks up onto: its porch, its steps, its stairs. */
    private List<Step> steps = List.of();
    /** How much wider, deeper and taller than it comes it is made (a placed one's size). */
    private double sx = 1, sy = 1, sz = 1;

    public BuildingProfile(String key, String label, double pixel, double texel, double depthMin, double depthStep,
                           Map<Elevation, Frame> frames, List<Part> parts, List<Shape> footprint, double[] door,
                           boolean interior, Anim anim) {
        this(key, label, BUILDING, null, pixel, texel, depthMin, depthStep, frames, parts, footprint, door, interior,
                anim);
    }

    /** As the other, of {@code group} ({@link #BUILDING}, {@link #FURNITURE}, {@link #ROOM}); {@code room}: a room's piece's shape, or null. */
    public BuildingProfile(String key, String label, String group, Room.Spec room, double pixel, double texel,
                           double depthMin, double depthStep, Map<Elevation, Frame> frames, List<Part> parts,
                           List<Shape> footprint, double[] door, boolean interior, Anim anim) {
        this.key = key;
        this.group = group == null ? BUILDING : group;
        this.room = room;
        this.label = label;
        this.pixel = pixel;
        this.texel = texel;
        this.depthMin = depthMin;
        this.depthStep = depthStep;
        // (an EnumMap of its own: copying an empty map that is not one - a model-only
        // profile's, scaled when its width is changed - would throw)
        Map<Elevation, Frame> own = new EnumMap<>(Elevation.class);
        own.putAll(frames);
        this.frames = Collections.unmodifiableMap(own);
        this.parts = List.copyOf(parts);
        this.footprint = List.copyOf(footprint);
        this.door = door;
        this.interior = interior;
        this.anim = anim;
    }

    public String key() { return key; }

    public String label() { return label; }

    /** {@link #BUILDING}, {@link #FURNITURE} or {@link #ROOM}. */
    public String group() { return group; }

    /** Whether it is a piece of furniture (it goes inside, and is filed with what goes inside). */
    public boolean furniture() { return group.equals(FURNITURE); }

    /** A room's piece's shape - drawn as it is, not from views - or null. */
    public Room.Spec room() { return room; }

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

    /** What of it she walks up onto - a porch, a step, stairs - in its own metres. */
    public List<Step> steps() { return steps; }

    /** As it comes, with {@code steps} to walk up onto. */
    public BuildingProfile withSteps(List<Step> steps) {
        this.steps = steps == null ? List.of() : List.copyOf(steps);
        return this;
    }

    /** Where she stands to go in, in its own metres ({@code x}, {@code y}), or null. */
    public double[] door() { return door == null ? null : door.clone(); }

    /** Whether it has an inside to go into. */
    public boolean interior() { return interior; }

    /** Its turning part, or null. */
    public Anim anim() { return anim; }

    /** How far from its middle its footprint reaches. */
    public double reach() {
        double r = room == null ? 0.5 : Math.hypot(room.length() / 2, room.depth() / 2);
        for (Shape s : footprint) r = Math.max(r, s.reach());
        return r;
    }

    /** A light's fitting: where its flame is, its own metres ({x, y, z} with z up), or null. */
    public double[] flame() { return flame == null ? null : flame.clone(); }

    /** Whether it is a light's fitting that stands on the ground (a standing candelabra, a lantern post). */
    public boolean stands() { return stands; }

    /** Whether it is a light's fitting (filed with the lights, not placed on its own). */
    public boolean fixture() { return group.equals(FIXTURE); }

    /** Whether it is only ever drawn as its 3D model (it has no views). */
    public boolean modelOnly() { return modelOnly; }

    /** Where the editor files a piece of furniture: "kitchen", "decor", "plants", "farm", "clutter" - or null. */
    public String shelf() { return shelf; }

    /** Whether it is the town's: a market stall, a town house, a workshop, a clock tower, a foundry. */
    public boolean town() { return group.equals(TOWN); }

    /** Measure it by its model: {@code top} metres tall (when it has no height of its own written). */
    void measured(double top) {
        if (Double.isNaN(modelHeight) && top > 0) modelHeight = top;
    }

    /** How tall it stands, about: the side view's top (a room's piece: its own height). */
    public double height() {
        if (room != null) return room.height();
        if (!Double.isNaN(modelHeight)) return modelHeight;
        Frame f = frames.get(Elevation.SIDE);
        return f == null ? 4 : f.anchorY() * pixel;
    }

    /** How much wider (its own x), deeper (y) and taller (up) than it comes: {sx, sy, sz}. */
    public double[] scale() { return new double[]{sx, sy, sz}; }

    /** Whether it is made a size other than it comes. */
    public boolean resized() { return sx != 1 || sy != 1 || sz != 1; }

    /**
     * It made {@code wide} times as wide (its own x), {@code deep} times as
     * deep and {@code tall} times as tall: its footprint, its steps, its door
     * and flame, its height - its model drawn so ({@link #scale}), its views
     * (which turn with the eye) by the mean of its width and height. The same
     * kind (key), so the same model and views. As it comes: itself.
     */
    public BuildingProfile scaled(double wide, double deep, double tall) {
        wide = clampScale(wide);
        deep = clampScale(deep);
        tall = clampScale(tall);
        if (wide == 1 && deep == 1 && tall == 1) return this;
        double u = Math.sqrt(wide * tall);
        List<Shape> fp = new ArrayList<>();
        for (Shape sh : footprint) {
            fp.add(sh.circle() ? new Shape(true, sh.x() * wide, sh.y() * deep, sh.w() * (wide + deep) / 2, sh.d())
                    : new Shape(false, sh.x() * wide, sh.y() * deep, sh.w() * wide, sh.d() * deep));
        }
        double[] dr = door == null ? null : new double[]{door[0] * wide, door[1] * deep};
        double tallNow = height();
        BuildingProfile q = new BuildingProfile(key, label, group, room, pixel * u, texel, depthMin * u,
                depthStep * u, frames, parts, fp, dr, interior, anim);
        q.modelHeight = tallNow * tall;
        q.modelOnly = modelOnly;
        q.shelf = shelf;
        q.stands = stands;
        q.flame = flame == null ? null : new double[]{flame[0] * wide, flame[1] * deep, flame[2] * tall};
        List<Step> st = new ArrayList<>();
        for (Step s : steps) st.add(new Step(s.x() * wide, s.y() * deep, s.w() * wide, s.d() * deep, s.top() * tall));
        q.steps = List.copyOf(st);
        q.sx = sx * wide;
        q.sy = sy * deep;
        q.sz = sz * tall;
        return q;
    }

    private static double clampScale(double v) {
        return Double.isNaN(v) ? 1 : Math.max(0.1, Math.min(10, v));
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
                        Json.str(p, "texture", "smooth"), Json.bool(p, "glow", false),
                        "always".equals(p.get("glow"))));
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
        BuildingProfile p = new BuildingProfile(Json.str(o, "key", "building"), Json.str(o, "label", "Building"),
                Json.str(o, "group", BUILDING), null, Json.num(o, "pixel", 0.0375), Json.num(o, "texel", 0.0375),
                Json.num(depth, "min", -10), Json.num(depth, "step", 0.08), frames, parts, shapes, door,
                Json.bool(o, "interior", false), anim);
        if (o.get("height") instanceof Number h) p.modelHeight = h.doubleValue();
        p.modelOnly = Json.bool(o, "model", false);
        p.shelf = o.get("shelf") instanceof String sh ? sh : null;
        if (o.get("flame") instanceof List<?> f && f.size() >= 3) {
            p.flame = new double[]{((Number) f.get(0)).doubleValue(), ((Number) f.get(1)).doubleValue(),
                    ((Number) f.get(2)).doubleValue()};
        }
        p.stands = Json.bool(o, "stands", false);
        List<Step> steps = new ArrayList<>();
        if (o.get("steps") instanceof List<?> sl) {
            for (Object x : sl) {
                if (!(x instanceof List<?> st) || st.size() < 6) continue;
                steps.add(new Step(((Number) st.get(1)).doubleValue(), ((Number) st.get(2)).doubleValue(),
                        ((Number) st.get(3)).doubleValue(), ((Number) st.get(4)).doubleValue(),
                        ((Number) st.get(5)).doubleValue()));
            }
        }
        p.steps = List.copyOf(steps);
        return p;
    }
}
