package com.larsons.game.sprite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Turns "this object, in this state, seen from here" into the stack of
 * sprite-sheet frames to draw, bottom layer first - {@link LayerStack} for
 * the things in {@link ObjectSprites} rather than for characters.
 *
 * <p><b>The rules.</b>
 * <ol>
 *   <li>The layers draw in name order, except that a layer whose name ends
 *       in {@code _back} goes under the rest and one ending in {@code
 *       _front} over them: the chest's {@code smoke_back}, {@code chest},
 *       {@code smoke_front} - the smoke behind the chest, the chest, the
 *       smoke in front of it.</li>
 *   <li>A layer with sheets for the object's state plays it, on the state's
 *       clock - looping, or held on its last frame once it has played.</li>
 *   <li>A layer without - an <em>ambient</em> layer, like the smoke, with a
 *       loop of its own ({@code swirl}) - plays that loop on the world's
 *       clock whatever the object is doing, so it never jumps when the
 *       state changes; and it is lit by the object's light ({@link
 *       #lit}), the chest's smoke glowing as the lid opens.</li>
 *   <li>While any layer's sheet is still loading, the last complete stack
 *       is held.</li>
 * </ol>
 */
public final class ObjectStack {

    /** The colour light from inside an object tints its ambient layers towards. */
    private static final int[] LIGHT = {255, 196, 96};
    /** How far toward it they go in full light. */
    private static final double LIGHT_MIX = 0.38;

    /** One frame's stack: the layers, how they were framed, and the view. */
    public record Result(List<LayerStack.Layer> layers, SpriteProfile framing, SpriteView view, boolean loading) {}

    /** Per-object memory: the last complete stack. */
    public static final class Memory {
        private Result last;
    }

    private ObjectStack() {}

    /** The object's layers in the order they are drawn. */
    public static List<String> drawOrder(ObjectSprites.Entry e) {
        List<String> names = new ArrayList<>(e.layers().keySet());
        names.sort(Comparator.comparingInt((String n) -> n.endsWith("_back") ? 0 : n.endsWith("_front") ? 2 : 1)
                .thenComparing(n -> n));
        return names;
    }

    /**
     * Resolve the stack for one frame.
     *
     * @param state      the object's state (a sheet name: {@code idle}, {@code open})
     * @param loops      whether that state's animation repeats
     * @param stateTime  seconds into the state
     * @param worldTime  seconds on the world's clock, for the ambient layers
     * @param light      how lit the object is inside, 0 – 1
     */
    public static Result resolve(SpriteLibrary lib, ObjectSprites objects, String object, String state,
                                 boolean loops, double stateTime, double worldTime, SpriteView view,
                                 double light, Memory memory) {
        ObjectSprites.Entry e = objects.entry(object);
        if (e == null) return new Result(List.of(), SpriteProfile.defaults(), view, false);
        SpriteProfile framing = e.profile();
        List<LayerStack.Layer> layers = new ArrayList<>();
        boolean missing = false;
        for (String layer : drawOrder(e)) {
            String playing = state;
            boolean ambient = false;
            SpriteLibrary.Resolved r = objects.resolve(object, layer, state, view.elevation(), view.facing());
            if (r == null) {
                playing = ambientState(e.layers().get(layer));
                if (playing == null) continue;
                ambient = true;
                r = objects.resolve(object, layer, playing, view.elevation(), view.facing());
                if (r == null) continue;
            }
            SheetTexture sheet = lib.sheet(r);
            if (sheet == null) {
                missing = true;
                continue;
            }
            double fps = framing.fps();
            int frame = ambient ? frameAt(worldTime, fps, sheet.frameCount(), true)
                    : frameAt(stateTime, fps, sheet.frameCount(), loops);
            int[] palette = sheet.palette();
            if (ambient && palette != null && light > 0) palette = lit(palette, light);
            layers.add(new LayerStack.Layer(null, layer, sheet, frame, r.mirrored(), palette));
        }
        if (missing && memory != null && memory.last != null && usable(memory.last)) {
            for (LayerStack.Layer l : memory.last.layers()) lib.touch(l.sheet());
            return new Result(memory.last.layers(), memory.last.framing(), memory.last.view(), true);
        }
        Result result = new Result(layers, framing, view, missing);
        if (!missing) {
            if (memory != null) memory.last = result;
            prefetch(lib, objects, object, e, view);
        }
        return result;
    }

    /**
     * Queue the object's other states from this view - the lid may be opened
     * at any moment - into room the library's budget has spare, once what is
     * on screen is all in.
     */
    private static void prefetch(SpriteLibrary lib, ObjectSprites objects, String object, ObjectSprites.Entry e,
                                 SpriteView view) {
        String suffix = "_" + view.elevation().key() + "_" + view.facing().key();
        String twin = view.facing().hasMirror() ? "_" + view.elevation().key() + "_" + view.facing().mirrorOf().key()
                : suffix;
        for (Map.Entry<String, Map<String, java.nio.file.Path>> layer : e.layers().entrySet()) {
            for (String key : layer.getValue().keySet()) {
                String end = key.endsWith(suffix) ? suffix : key.endsWith(twin) ? twin : null;
                if (end == null) continue;
                String state = key.substring(0, key.length() - end.length());
                lib.prefetch(objects.resolve(object, layer.getKey(), state, view.elevation(), view.facing()));
            }
        }
    }

    /** The state an ambient layer loops: the first it has sheets for. */
    private static String ambientState(Map<String, java.nio.file.Path> sheets) {
        if (sheets == null || sheets.isEmpty()) return null;
        String key = sheets.keySet().iterator().next();
        String[] parts = key.split("_");
        return String.join("_", java.util.Arrays.copyOf(parts, parts.length - 2));
    }

    private static boolean usable(Result r) {
        for (LayerStack.Layer l : r.layers()) if (l.sheet().closed()) return false;
        return true;
    }

    /** The frame of a {@code frames}-long sheet {@code seconds} in: wrapped when it loops, else held at the end. */
    public static int frameAt(double seconds, double fps, int frames, boolean loops) {
        if (frames <= 1) return 0;
        int raw = (int) Math.floor(Math.max(0, seconds) * fps);
        return loops ? Math.floorMod(raw, frames) : Math.min(raw, frames - 1);
    }

    /**
     * How long one pass of {@code state} lasts for an object from this view,
     * from its sheet once loaded; 0 while it is not (or there is none).
     */
    public static double duration(SpriteLibrary lib, ObjectSprites objects, String object, String state,
                                  SpriteView view) {
        ObjectSprites.Entry e = objects.entry(object);
        if (e == null) return 0;
        for (String layer : drawOrder(e)) {
            SpriteLibrary.Resolved r = objects.resolve(object, layer, state, view.elevation(), view.facing());
            if (r == null) continue;
            SheetTexture t = lib.sheet(r);
            return t == null ? 0 : t.frameCount() / Math.max(1e-6, e.profile().fps());
        }
        return 0;
    }

    /**
     * A palette lit from inside: every colour moved {@code light} of the way
     * to its warm-lit version (alpha kept), so smoke glows gold as the light
     * comes up - a different palette, the same texture.
     */
    public static int[] lit(int[] argb, double light) {
        double k = Math.max(0, Math.min(1, light)) * LIGHT_MIX;
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            int a = c >>> 24;
            int r = (int) Math.round(((c >> 16) & 255) * (1 - k) + LIGHT[0] * k);
            int g = (int) Math.round(((c >> 8) & 255) * (1 - k) + LIGHT[1] * k);
            int b = (int) Math.round((c & 255) * (1 - k) + LIGHT[2] * k);
            out[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return out;
    }
}
