package com.larsons.game.sprite;

import com.larsons.game.gfx.Batch;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.fallback.FallbackSprites;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns "this character, in this state, seen from here" into the stack of
 * sprite-sheet frames to draw, bottom layer first — and draws it.
 *
 * <p><b>The rules.</b>
 * <ol>
 *   <li>The base body comes from {@code assets/sprites/body/<item>/} when the
 *       wardrobe names one and that view has a sheet (or a mirrored twin);
 *       otherwise that view falls back to the 32×32 generated body. The
 *       fallback is per view, so a half-finished set of renders is still
 *       playable.</li>
 *   <li>A state the body has no sheet for from this view is shown as the
 *       first of its {@link AnimState#fallback() fallbacks} it has one for -
 *       a 512-pixel set rendered before the crouch existed crouches as it
 *       stands - and every layer plays that state with it.</li>
 *   <li>What is drawn in the hands is the state's {@link Stance}'s: the
 *       wardrobe's carried items for the sword stance, the stance's own
 *       weapon in its hand (and nothing in the other) for the axe, the bow
 *       and the crossbow, and nothing at all for the emotes and pick-ups
 *       ({@link Stance#carried}).</li>
 *   <li>The body's sheet decides the frame: {@code frame = time × fps},
 *       wrapped (looping states) or held on the last frame (jump, attack).</li>
 *   <li>Every cosmetic plays the <em>same</em> frame index. They are authored
 *       with the body's frame count; if one is not, its frame is scaled
 *       proportionally so it still starts and ends with the body.</li>
 *   <li>A cosmetic with no sheet for this view is not drawn. The one
 *       exception is the sword in the weapon hand, which has a generated
 *       fallback that matches the fallback body frame for frame.</li>
 *   <li>Each layer is drawn in the colours the wardrobe picked for its slot
 *       (and the skin tone, wherever it shows skin): a palette swap of its
 *       pixel-art sheets from the item's {@code variants.json}
 *       ({@link Variants}), made on the GPU as the layer is drawn
 *       ({@link Layer#palette()}), so one set of sheets - one texture - draws
 *       every colour.</li>
 *   <li>A left-handed character draws the {@code _lh} version of every item
 *       (the right-handed art mirrored, from the mirrored direction), and its
 *       hands swap in the draw order ({@link #drawOrder}); where an item has
 *       no {@code _lh} version, its right-handed twin slot's art is mirrored
 *       instead.</li>
 *   <li>While any layer's sheet is still decoding, the whole previous stack is
 *       held, so nothing blinks and no layer is ever out of step with the
 *       body.</li>
 * </ol>
 */
public final class LayerStack {

    /** The item name whose missing right-hand sheets fall back to the generated sword. */
    public static final String FALLBACK_SWORD = "sword";

    /**
     * One layer to draw: a frame of a sheet, possibly mirrored, and the
     * palette its pixel art is coloured from (straight ARGB; null for an RGBA
     * sheet, drawn as it is).
     */
    public record Layer(Slot slot, String item, SheetTexture sheet, int frame, boolean mirrored, int[] palette) {

        public Layer(Slot slot, String item, SheetTexture sheet, int frame, boolean mirrored) {
            this(slot, item, sheet, frame, mirrored, sheet == null ? null : sheet.palette());
        }

        /** This layer in other colours (null: the sheet's own). */
        public Layer withPalette(int[] p) {
            return new Layer(slot, item, sheet, frame, mirrored, p);
        }
    }

    /**
     * The whole stack for one frame.
     *
     * @param layers       bottom first
     * @param framing      how the body's sheets were framed (size and feet anchor)
     * @param fallbackBody whether the body is the generated 32×32 one
     * @param loading      whether this is the previous stack, held while the new one loads
     */
    public record Result(List<Layer> layers, SpriteProfile framing, SpriteView view,
                         AnimState state, AnimState asked, int frame, int frames, double fps,
                         boolean fallbackBody, boolean loading) {

        /** Seconds one pass of this state's animation lasts. */
        public double duration() {
            return AnimState.duration(frames, fps);
        }

        /** Whether the state asked for is shown as one of its fallbacks. */
        public boolean substituted() {
            return state != asked;
        }

        Result held() {
            return new Result(layers, framing, view, state, asked, frame, frames, fps, fallbackBody, true);
        }
    }

    /** Per-character memory: the last stack that was complete. */
    public static final class Memory {
        private Result last;
    }

    private LayerStack() {}

    /** A layer's source and file and, once resident, its texture. */
    private record Want(Slot slot, String item, SpriteLibrary.Source source, SpriteLibrary.Resolved file,
                        SheetTexture sheet) {}

    /**
     * The slots in the order they are drawn: {@link Slot}'s, except that
     * <ul>
     *   <li>a left-handed character's two hands swap - its left hand draws the
     *       right hand's art, mirrored, which was cut against what the other
     *       hand holds;</li>
     *   <li>{@link Slot#OTHER} - a cape, worn on the back - goes under
     *       everything, the body included, while the character faces the
     *       camera ({@link #facesCamera}): it is behind all of it, whatever
     *       is worn. From the side, from behind and from above it goes over
     *       everything worn (it is cut only against the body), under the hair
     *       and hat - long hair falls over it - and the hands.</li>
     * </ul>
     */
    public static Slot[] drawOrder(boolean leftHanded, Elevation elevation, Facing facing) {
        List<Slot> order = new ArrayList<>(List.of(Slot.values()));
        order.remove(Slot.OTHER);
        order.add(facesCamera(elevation, facing) ? 0 : order.indexOf(Slot.HAIR), Slot.OTHER);
        if (leftHanded) {
            int l = order.indexOf(Slot.CARRY_LEFT), r = order.indexOf(Slot.CARRY_RIGHT);
            order.set(l, Slot.CARRY_RIGHT);
            order.set(r, Slot.CARRY_LEFT);
        }
        return order.toArray(new Slot[0]);
    }

    /** Whether the character's front is towards the camera: the south views, from the side or at 45 degrees. */
    static boolean facesCamera(Elevation elevation, Facing facing) {
        return elevation != Elevation.TOP
                && (facing == Facing.SOUTH || facing == Facing.SOUTH_EAST || facing == Facing.SOUTH_WEST);
    }

    /**
     * The state shown for {@code state} from this view: itself where the body
     * ({@code body}, a {@link SpriteLibrary#source}) has a sheet for it, else
     * the first of its fallbacks that has one, else the first six's state at
     * the end of the chain. Without a body (the generated one), the chain's
     * end: the generated body only knows the first six.
     */
    public static AnimState shown(SpriteLibrary lib, SpriteLibrary.Source body, AnimState state,
                                  Elevation elevation, Facing facing) {
        if (body == null) return state.root();
        AnimState s = state;
        while (s.fallback() != null && lib.resolve(body, s, elevation, facing) == null) s = s.fallback();
        return s;
    }

    /**
     * Resolve the stack for one frame.
     *
     * <p><b>Stacks switch atomically.</b> When the view or the state changes
     * and any layer's new sheet is still decoding, the previous complete stack
     * is held (frozen on its last frame) until every layer is resident — a
     * sword must never be drawn in its idle pose over a body that is already
     * swinging. Only when there is no previous stack (the very first frame)
     * does a missing body fall back to the generated one and a missing
     * cosmetic wait undrawn.
     */
    public static Result resolve(SpriteLibrary lib, Wardrobe wardrobe, AnimState state,
                                 double time, SpriteView view, Memory memory) {
        Elevation elev = view.elevation();
        Facing facing = view.facing();
        boolean missing = false;
        boolean left = wardrobe.leftHanded();
        AnimState asked = state;
        String bodyItem = wardrobe.get(Slot.BODY);
        state = shown(lib, lib.source(bodyItem, Slot.BODY, bodyItem, wardrobe.style(), left),
                asked, elev, facing);

        List<Want> wants = new ArrayList<>();
        Slot[] order = drawOrder(left, elev, facing);
        for (Slot slot : order) {
            String item = slot.carried() ? asked.stance().carried(slot, wardrobe) : wardrobe.get(slot);
            if (item == null) continue;
            SpriteLibrary.Source src = lib.source(bodyItem, slot, item, wardrobe.style(), left);
            SpriteLibrary.Resolved file = lib.resolve(src, state, elev, facing);
            if (file != null) file = file.withRecolor(lib.recolor(src, slot, wardrobe));
            SheetTexture sheet = null;
            if (file != null) {
                sheet = lib.sheet(file);
                if (sheet == null) missing = true;
            }
            wants.add(new Want(slot, item, src, file, sheet));
        }
        // Only once what is on screen is all in: until then the decoders work
        // on nothing else.
        if (!missing) {
            for (Want w : wants) {
                if (w.file() != null) prefetch(lib, w.source(), state, asked.stance(), elev, facing);
            }
        }
        if (missing && memory.last != null && usable(memory.last)) {
            for (Layer l : memory.last.layers()) lib.touch(l.sheet());
            return memory.last.held();
        }

        // --- the body ----------------------------------------------------------------
        java.util.Map<Slot, Want> wanted = new java.util.EnumMap<>(Slot.class);
        for (Want w : wants) wanted.put(w.slot(), w);
        Want bodyWant = wanted.get(Slot.BODY);
        SheetTexture body;
        boolean mirrored = false;
        SpriteProfile framing;
        boolean fallbackBody;
        if (bodyWant != null && bodyWant.sheet() != null) {
            body = bodyWant.sheet();
            mirrored = bodyWant.file().mirrored();
            framing = bodyWant.file().profile();
            fallbackBody = false;
        } else {
            // (the left-handed fallback: the right-handed one from the other side, mirrored)
            body = lib.fallback(state.root(), elev, left ? facing.mirrorOf() : facing, false);
            mirrored = left;
            framing = FallbackSprites.PROFILE;
            fallbackBody = true;
        }
        double fps = framing.fps(state);
        int frames = body.frameCount();
        int frame = state.frameAt(time, fps, frames);
        List<Layer> layers = new ArrayList<>();

        // --- every layer in draw order, the body where it falls in it --------------
        for (Slot slot : order) {
            if (slot == Slot.BODY) {
                Layer bodyLayer = new Layer(Slot.BODY, bodyWant == null ? null : bodyWant.item(), body, frame, mirrored);
                if (bodyWant != null && bodyWant.sheet() == body) {
                    bodyLayer = bodyLayer.withPalette(Variants.recolored(body.palette(), bodyWant.file().recolor()));
                }
                layers.add(bodyLayer);
                continue;
            }
            Want w = wanted.get(slot);
            if (w == null) continue;
            if (w.sheet() != null) {
                layers.add(new Layer(w.slot(), w.item(), w.sheet(),
                        mapFrame(frame, frames, w.sheet().frameCount()), w.file().mirrored(),
                        Variants.recolored(w.sheet().palette(), w.file().recolor())));
            } else if (w.file() == null && w.slot() == (left ? Slot.CARRY_LEFT : Slot.CARRY_RIGHT)
                    && fallbackBody && FALLBACK_SWORD.equals(w.item())) {
                layers.add(new Layer(w.slot(), w.item(),
                        lib.fallback(state.root(), elev, left ? facing.mirrorOf() : facing, true), frame, left));
            }
        }
        Result result = new Result(layers, framing, view, state, asked, frame, frames, fps, fallbackBody, missing);
        if (!missing) memory.last = result;
        return result;
    }

    /** A held stack is only drawable while none of its sheets has been released. */
    private static boolean usable(Result r) {
        for (Layer l : r.layers()) if (l.sheet().closed()) return false;
        return true;
    }

    /**
     * How long one pass of {@code state} lasts for this wardrobe from this
     * view — from the body's sheet of the state {@link #shown} for it when
     * that is loaded, otherwise from the fallback's frame count. Does not
     * touch any character's memory.
     */
    public static double duration(SpriteLibrary lib, Wardrobe wardrobe, AnimState state,
                                  SpriteView view) {
        SpriteLibrary.Source body = lib.source(wardrobe.get(Slot.BODY), Slot.BODY, wardrobe.get(Slot.BODY),
                wardrobe.style(), wardrobe.leftHanded());
        state = shown(lib, body, state, view.elevation(), view.facing());
        SpriteLibrary.Resolved r = lib.resolve(body, state, view.elevation(), view.facing());
        if (r != null) r = r.withRecolor(lib.recolor(body, Slot.BODY, wardrobe));
        if (r != null) {
            SheetTexture t = lib.sheet(r);
            if (t != null) return AnimState.duration(t.frameCount(), r.profile().fps(state));
        }
        state = state.root();
        return AnimState.duration(state.defaultFrames(), FallbackSprites.PROFILE.fps(state));
    }

    /**
     * The frame of a {@code layerFrames}-long sheet that goes with frame
     * {@code bodyFrame} of a {@code bodyFrames}-long body sheet. Equal counts
     * (the contract) map one to one.
     */
    public static int mapFrame(int bodyFrame, int bodyFrames, int layerFrames) {
        if (layerFrames <= 1) return 0;
        if (bodyFrames == layerFrames) return bodyFrame;
        return Math.min(layerFrames - 1, (int) ((long) bodyFrame * layerFrames / Math.max(1, bodyFrames)));
    }

    /**
     * Queue what the character is likely to need next: every other state of
     * the same stance from this view (it may start walking or swinging at any
     * moment - though not drop the axe for the bow), and this state from the
     * two neighbouring directions (the camera or the character may turn).
     * Queued loads are cheap to ask for twice, and the library only takes them
     * while the memory budget has room (see {@link SpriteLibrary#prefetch}).
     */
    private static void prefetch(SpriteLibrary lib, SpriteLibrary.Source src,
                                 AnimState state, Stance stance, Elevation elev, Facing facing) {
        for (AnimState other : AnimState.values()) {
            if (other != state && other.stance() == stance) {
                lib.prefetch(lib.resolve(src, other, elev, facing));
            }
        }
        lib.prefetch(lib.resolve(src, state, elev, facing.clockwise()));
        lib.prefetch(lib.resolve(src, state, elev, facing.counterClockwise()));
    }

    // --- drawing ---------------------------------------------------------------------

    /**
     * Draw the stack as a camera-facing billboard in the 3D world, placed so
     * the feet in the picture land on {@code feet} (see {@link SpriteProfile}
     * for why that keeps them on the shadow from any angle). Every layer is
     * the same quad, the whole frame, with the very same corners; drawn in
     * order with a {@code LEQUAL} depth test, each one lands on top of the
     * last. A cropped sheet's texture coordinates are stretched over the whole
     * card so its cell lands where it sat in the frame ({@link #wholeCardUv}),
     * and the rest of the card is clipped away.
     *
     * <p>The layers must not be drawn on quads of their own, however exactly
     * those line up: corners computed separately round differently, the
     * depths of two layers then differ in the last bit, and wherever the
     * upper one comes out a hair deeper it fails the depth test and the layer
     * beneath shows through — flickering as the character moves.
     */
    public static void drawBillboard(Batch batch, Result r, Vec3 feet, Vec3 cameraRight,
                                     Vec3 cameraUp, float alpha) {
        double w = r.framing().frameWorldSize(), h = r.framing().frameWorldHeight();
        double[] anchor = r.framing().anchor(r.view().elevation());
        Vec3 topLeft = feet.sub(cameraRight.scale(anchor[0] * w)).add(cameraUp.scale(anchor[1] * h));
        Vec3 right = cameraRight.scale(w);
        Vec3 down = cameraUp.scale(-h);
        for (Layer l : r.layers()) {
            double[] region = l.sheet().region(l.mirrored());
            if (region[2] <= region[0] || region[3] <= region[1]) continue; // nothing in it
            float[] uv = l.sheet().uv(l.frame(), l.mirrored());
            float[] card = wholeCardUv(region, uv);
            batch.quadClipped(l.sheet().texture(), topLeft, right, down,
                    card[0], card[1], card[2], card[3],
                    Math.min(uv[0], uv[2]), Math.min(uv[1], uv[3]),
                    Math.max(uv[0], uv[2]), Math.max(uv[1], uv[3]),
                    1, 1, 1, alpha, batch.palette(l.sheet().indexed() ? l.palette() : null));
        }
    }

    /**
     * Texture coordinates for the whole card, {@code {u0, v0, u1, v1}} at its
     * top-left and bottom-right corners, that put a frame's cell exactly on
     * the part of the card it covers: {@code region} ({@code {x0, y0, x1,
     * y1}}, fractions of the card, see {@link SheetTexture#region}), with the
     * cell's coordinates {@code uv} ({@link SheetTexture#uv}) at that part's
     * corners. Beyond the cell they run on into the rest of the atlas, which
     * the draw clips away.
     */
    static float[] wholeCardUv(double[] region, float[] uv) {
        double du = (uv[2] - uv[0]) / (region[2] - region[0]);
        double dv = (uv[3] - uv[1]) / (region[3] - region[1]);
        return new float[]{
                (float) (uv[0] - region[0] * du), (float) (uv[1] - region[1] * dv),
                (float) (uv[0] + (1 - region[0]) * du), (float) (uv[1] + (1 - region[1]) * dv)};
    }

    /**
     * Draw the stack flat on the UI, feet at {@code (footX, footY)}, the frame
     * {@code pixelsPerMetre} to the metre — for the wardrobe preview.
     */
    public static void drawFlat(Batch batch, Result r, float footX, float footY, float pixelsPerMetre) {
        float w = (float) (r.framing().frameWorldSize() * pixelsPerMetre);
        float h = (float) (r.framing().frameWorldHeight() * pixelsPerMetre);
        double[] anchor = r.framing().anchor(r.view().elevation());
        float x = footX - (float) anchor[0] * w, y = footY - (float) anchor[1] * h;
        for (Layer l : r.layers()) {
            double[] region = l.sheet().region(l.mirrored());
            if (region[2] <= region[0] || region[3] <= region[1]) continue; // nothing in it
            float[] uv = l.sheet().uv(l.frame(), l.mirrored());
            batch.rect(l.sheet().texture(), x + (float) region[0] * w, y + (float) region[1] * h,
                    (float) (region[2] - region[0]) * w, (float) (region[3] - region[1]) * h,
                    uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1,
                    batch.palette(l.sheet().indexed() ? l.palette() : null));
        }
    }
}
