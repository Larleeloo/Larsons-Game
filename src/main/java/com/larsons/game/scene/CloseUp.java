package com.larsons.game.scene;

import com.larsons.game.core.Game;
import com.larsons.game.cutscene.CloseupLibrary;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.SheetTexture;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.Variants;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Inventory;
import com.larsons.game.world.WorldRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * A character close up: from the waist up, at rest, from any of the eight
 * sides, playing on ({@code assets/wardrobe_closeups/}) - every item worn a
 * layer of its own, stacked in the game's draw order and recoloured as it is
 * worn: its colour, the skin, a tinted or forged piece's look. The
 * wardrobe's mirror shows it ({@link WardrobePanel}), and so does the new
 * game's portrait ({@link Portrait}).
 *
 * <p>A side not loaded yet is not drawn half-dressed: until all its layers
 * are in, the last whole picture stays up, and the next side round is
 * fetched ahead.
 */
final class CloseUp {

    /** One layer drawn: its sheet (asked for anew each frame: it may have been let go) and its palette. */
    private record Drawn(SpriteLibrary.Resolved sheet, int[] palette) {}

    private final Game game;
    /** The last picture drawn with every layer in. */
    private List<Drawn> last = List.of();

    CloseUp(Game game) {
        this.game = game;
    }

    /** Whether there are close-ups of the body {@code wd} wears (else the caller shows something else). */
    boolean has(Wardrobe wd) {
        CloseupLibrary closeups = game.wardrobeCloseups();
        if (closeups == null || wd.get(Slot.BODY) == null) return false;
        SpriteLibrary lib = game.sprites();
        CloseupLibrary.Entry e = closeups.entryIn(lib.rootOf(wd.get(Slot.BODY)), Slot.BODY, wd.get(Slot.BODY),
                wd.style());
        return e != null && !e.clips().isEmpty();
    }

    /**
     * Draw {@code wd}'s close-up from {@code facing}, {@code time} seconds
     * into the rest, filling {@code (x, y, w, h)} - {@code zoom} (1 and up)
     * times closer, closing on the face. {@code inventory} says which piece
     * of armour is worn (its tier, a forged one's colours); null: as found.
     * Returns the frame shown, or -1 when there are no close-ups of this
     * body (nothing is drawn).
     */
    int draw(Ui ui, float x, float y, float w, float h, Wardrobe wd, Inventory inventory, Facing facing, double time,
             double zoom) {
        if (!has(wd)) return -1;
        SpriteLibrary lib = game.sprites();
        CloseupLibrary closeups = game.wardrobeCloseups();
        String root = lib.rootOf(wd.get(Slot.BODY));
        String clip = clip(facing), next = clip(facing.counterClockwise());
        List<Drawn> layers = new ArrayList<>();
        boolean waiting = false;
        for (Slot slot : LayerStack.drawOrder(wd.leftHanded(), Elevation.SIDE, facing)) {
            if (slot.carried() || wd.hidden(slot)) continue;
            String item = wd.get(slot);
            CloseupLibrary.Entry e = closeups.entryIn(root, slot, item, wd.style());
            SpriteLibrary.Resolved r = closeups.resolve(e, clip, null, false);
            if (r == null) continue;
            // (the next side round, fetched ahead: the mirror turns that way)
            SpriteLibrary.Resolved ahead = closeups.resolve(e, next, null, false);
            if (ahead != null) lib.prefetch(ahead);
            SheetTexture t = lib.sheet(r);
            if (t == null) {
                waiting = true;
                continue;
            }
            int[] base = t.sharedPalette();
            int[] palette = base == null || e.variants() == null ? base : Variants.recolored(base,
                    e.variants().recolor(CloseupLibrary.choice(slot, wd.colour(slot), wd.skin())));
            palette = WorldRenderer.wornPalette(palette, slot, item, "closeup:" + e.folder(), wd, inventory,
                    game.items(), lib);
            layers.add(new Drawn(r, palette));
        }
        if (waiting && !last.isEmpty()) layers = last;
        else if (!waiting) last = layers;
        List<SheetTexture> sheets = new ArrayList<>();
        List<int[]> palettes = new ArrayList<>();
        for (Drawn d : layers) {
            SheetTexture t = lib.sheet(d.sheet());
            if (t == null) continue;
            sheets.add(t);
            palettes.add(d.palette());
        }
        if (sheets.isEmpty()) return 0;
        int frames = Integer.MAX_VALUE;
        for (SheetTexture t : sheets) frames = Math.min(frames, Math.max(1, t.frameCount()));
        // (drawn every other frame of the rest: played so a loop lasts as long as the rest's)
        double fps = frames * AnimState.DEFAULT_FPS / (double) Math.max(1, AnimState.REST.defaultFrames());
        int frame = (int) (Math.max(0, time) * fps) % frames;
        double closer = Math.max(0, zoom - 1);
        float size = (float) (Math.min(w, h * 1.05f) * (1 + 0.6 * closer));
        float fx = x + w / 2 - size / 2;
        // (closer, the face - a third of the way down the frame - comes to the middle)
        float fy = (float) (y + h / 2 - size * (0.5 - 0.17 * Math.min(1, closer)));
        for (int i = 0; i < sheets.size(); i++) {
            SheetTexture t = sheets.get(i);
            double[] region = t.region(false);
            if (region[2] <= region[0] || region[3] <= region[1]) continue;
            float[] uv = t.uv(Math.min(frame, Math.max(0, t.frameCount() - 1)), false);
            ui.batch().rect(t.texture(), fx + (float) region[0] * size, fy + (float) region[1] * size,
                    (float) (region[2] - region[0]) * size, (float) (region[3] - region[1]) * size,
                    uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1,
                    ui.batch().palette(t.indexed() ? palettes.get(i) : null));
        }
        return frame;
    }

    private static String clip(Facing facing) {
        return "rest_side_" + facing.key();
    }
}
