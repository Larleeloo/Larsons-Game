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
 *   <li>The body's sheet decides the frame: {@code frame = time × fps},
 *       wrapped (looping states) or held on the last frame (jump, attack).</li>
 *   <li>Every cosmetic plays the <em>same</em> frame index. They are authored
 *       with the body's frame count; if one is not, its frame is scaled
 *       proportionally so it still starts and ends with the body.</li>
 *   <li>A cosmetic with no sheet for this view is not drawn. The one
 *       exception is the sword in the right hand, which has a generated
 *       fallback that matches the fallback body frame for frame.</li>
 *   <li>While any layer's sheet is still decoding, the whole previous stack is
 *       held, so nothing blinks and no layer is ever out of step with the
 *       body.</li>
 * </ol>
 */
public final class LayerStack {

    /** The item name whose missing right-hand sheets fall back to the generated sword. */
    public static final String FALLBACK_SWORD = "sword";

    /** One layer to draw: a frame of a sheet, possibly mirrored. */
    public record Layer(Slot slot, String item, SheetTexture sheet, int frame, boolean mirrored) {}

    /**
     * The whole stack for one frame.
     *
     * @param layers       bottom first
     * @param framing      how the body's sheets were framed (size and feet anchor)
     * @param fallbackBody whether the body is the generated 32×32 one
     * @param loading      whether this is the previous stack, held while the new one loads
     */
    public record Result(List<Layer> layers, SpriteProfile framing, SpriteView view,
                         AnimState state, int frame, int frames, double fps,
                         boolean fallbackBody, boolean loading) {

        /** Seconds one pass of this state's animation lasts. */
        public double duration() {
            return AnimState.duration(frames, fps);
        }

        Result held() {
            return new Result(layers, framing, view, state, frame, frames, fps, fallbackBody, true);
        }
    }

    /** Per-character memory: the last stack that was complete. */
    public static final class Memory {
        private Result last;
    }

    private LayerStack() {}

    /** A layer's file and, once resident, its texture. */
    private record Want(Slot slot, String item, SpriteLibrary.Resolved file, SheetTexture sheet) {}

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

        List<Want> wants = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            String item = wardrobe.get(slot);
            if (item == null) continue;
            SpriteLibrary.Resolved file = lib.resolve(slot, item, state, elev, facing);
            SheetTexture sheet = null;
            if (file != null) {
                sheet = lib.sheet(file);
                if (sheet == null) missing = true;
                prefetch(lib, slot, item, state, elev, facing);
            }
            wants.add(new Want(slot, item, file, sheet));
        }
        if (missing && memory.last != null && usable(memory.last)) {
            for (Layer l : memory.last.layers()) lib.touch(l.sheet());
            return memory.last.held();
        }

        // --- the body ----------------------------------------------------------------
        Want bodyWant = wants.isEmpty() || wants.get(0).slot() != Slot.BODY ? null : wants.get(0);
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
            body = lib.fallback(state, elev, facing, false);
            framing = FallbackSprites.PROFILE;
            fallbackBody = true;
        }
        double fps = framing.fps(state);
        int frames = body.frameCount();
        int frame = state.frameAt(time, fps, frames);
        List<Layer> layers = new ArrayList<>();
        layers.add(new Layer(Slot.BODY, bodyWant == null ? null : bodyWant.item(), body, frame, mirrored));

        // --- cosmetics, in slot order ------------------------------------------------
        for (Want w : wants) {
            if (w.slot() == Slot.BODY) continue;
            if (w.sheet() != null) {
                layers.add(new Layer(w.slot(), w.item(), w.sheet(),
                        mapFrame(frame, frames, w.sheet().frameCount()), w.file().mirrored()));
            } else if (w.file() == null && w.slot() == Slot.CARRY_RIGHT && fallbackBody
                    && FALLBACK_SWORD.equals(w.item())) {
                layers.add(new Layer(w.slot(), w.item(), lib.fallback(state, elev, facing, true),
                        frame, false));
            }
        }
        Result result = new Result(layers, framing, view, state, frame, frames, fps, fallbackBody, missing);
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
     * view — from the body's sheet when it is loaded, otherwise from the
     * fallback's frame count. Does not touch any character's memory.
     */
    public static double duration(SpriteLibrary lib, Wardrobe wardrobe, AnimState state,
                                  SpriteView view) {
        SpriteLibrary.Resolved r = lib.resolve(Slot.BODY, wardrobe.get(Slot.BODY), state,
                view.elevation(), view.facing());
        if (r != null) {
            SheetTexture t = lib.sheet(r);
            if (t != null) return AnimState.duration(t.frameCount(), r.profile().fps(state));
        }
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
     * Queue what the character is likely to need next: every other state from
     * this view (it may start walking or swinging at any moment), and this
     * state from the two neighbouring directions (the camera or the character
     * may turn). Queued loads are cheap to ask for twice.
     */
    private static void prefetch(SpriteLibrary lib, Slot slot, String item, AnimState state,
                                 Elevation elev, Facing facing) {
        for (AnimState other : AnimState.values()) {
            if (other != state) lib.request(lib.resolve(slot, item, other, elev, facing));
        }
        lib.request(lib.resolve(slot, item, state, elev, facing.clockwise()));
        lib.request(lib.resolve(slot, item, state, elev, facing.counterClockwise()));
    }

    // --- drawing ---------------------------------------------------------------------

    /**
     * Draw the stack as a camera-facing billboard in the 3D world, placed so
     * the feet in the picture land on {@code feet} (see {@link SpriteProfile}
     * for why that keeps them on the shadow from any angle). Every layer is
     * the same quad; drawn in order with a {@code LEQUAL} depth test, each one
     * lands on top of the last.
     */
    public static void drawBillboard(Batch batch, Result r, Vec3 feet, Vec3 cameraRight,
                                     Vec3 cameraUp, float alpha) {
        double w = r.framing().frameWorldSize(), h = r.framing().frameWorldHeight();
        double[] anchor = r.framing().anchor(r.view().elevation());
        Vec3 topLeft = feet.sub(cameraRight.scale(anchor[0] * w)).add(cameraUp.scale(anchor[1] * h));
        Vec3 right = cameraRight.scale(w);
        Vec3 down = cameraUp.scale(-h);
        for (Layer l : r.layers()) {
            float[] uv = l.sheet().uv(l.frame(), l.mirrored());
            batch.quad(l.sheet().texture(), topLeft, right, down, uv[0], uv[1], uv[2], uv[3],
                    1, 1, 1, alpha);
        }
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
            float[] uv = l.sheet().uv(l.frame(), l.mirrored());
            batch.rect(l.sheet().texture(), x, y, w, h, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
        }
    }
}
