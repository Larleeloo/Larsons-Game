package com.larsons.game.cutscene;

import com.larsons.game.gfx.Batch;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.SheetTexture;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.SpriteProfile;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a {@link Cutscene} on the UI: a backdrop, the actors as stacks of
 * close-up layers ({@link CloseupLibrary}) - each in its actor's colours,
 * tint and fade - and the line being spoken in a box along the bottom.
 *
 * <p>An actor is resolved like a game character ({@link LayerStack}): every
 * slot of its wardrobe with a close-up, in the game's draw order for the view
 * the close-ups are rendered from; the body's sheet sets the frame and every
 * layer plays the same one; and while a layer of a new clip is still loading
 * the actor's previous stack is held, so nothing pops in on its own. Pixel
 * art is drawn at a whole number of screen pixels to the pixel.
 */
public final class CutsceneStage {

    /** Where the actors stand, as a fraction of the stage's width from its left edge. */
    private static final float LEFT_X = 0.35f, RIGHT_X = 0.68f;
    /** The dialogue box's margin round it, and its height (at least, and as a share of the stage). */
    private static final float PAD = 22, BOX_MIN = 118, BOX_SHARE = 0.2f;

    private record Drawn(SheetTexture sheet, int frame, boolean mirrored, int[] palette) {}

    private record Stack(List<Drawn> layers, int frameSize) {}

    private final SpriteLibrary sprites;
    private final CloseupLibrary closeups;
    private final Map<String, Stack> held = new HashMap<>();

    public CutsceneStage(SpriteLibrary sprites, CloseupLibrary closeups) {
        this.sprites = sprites;
        this.closeups = closeups;
    }

    public CloseupLibrary closeups() { return closeups; }

    /**
     * Draw the scene filling {@code (x, y, w, h)} of the UI (y down): the
     * actors stand on the dialogue box along the bottom, so it never hides
     * their hands.
     */
    public void draw(Ui ui, Cutscene cs, float x, float y, float w, float h) {
        backdrop(ui, x, y, w, h);
        actors(ui, cs, x, y, w, h - boxHeight(h) - 2 * PAD);
        dialogue(ui, cs, x, y, w, h);
    }

    /** The height the dialogue box of a stage {@code h} tall takes (without its margins). */
    public static float boxHeight(float h) {
        return Math.max(BOX_MIN, h * BOX_SHARE);
    }

    /** The actors, standing on the bottom edge of {@code (x, y, w, h)}, as big as whole pixels let them. */
    public void actors(Ui ui, Cutscene cs, float x, float y, float w, float h) {
        for (Actor a : cs.actors()) {
            Stack s = stack(a);
            if (s == null || s.layers().isEmpty()) continue;
            int zoom = Math.max(1, (int) (h / s.frameSize()));
            float size = s.frameSize() * zoom;
            float cx = x + w * (a.side() == Actor.Side.LEFT ? LEFT_X : RIGHT_X);
            drawActor(ui.batch(), a, s, Math.round(cx - size / 2), Math.round(y + h - size), size);
        }
    }

    /** The line being spoken (if any), in a box along the bottom of {@code (x, y, w, h)}. */
    public void dialogue(Ui ui, Cutscene cs, float x, float y, float w, float h) {
        Cutscene.Line line = cs.line();
        if (line != null) dialogue(ui, line, cs.now(), x, y, w, h);
    }

    /** The actor's layers this frame, or its last complete stack while a new one loads; null before any. */
    private Stack stack(Actor a) {
        Wardrobe w = a.wardrobe();
        List<SheetTexture> sheets = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        List<CloseupLibrary.Entry> entries = new ArrayList<>();
        boolean missing = false;
        SheetTexture body = null;
        for (Slot slot : LayerStack.drawOrder(false, Elevation.SIDE, Facing.SOUTH_EAST)) {
            CloseupLibrary.Entry e = closeups.entry(slot, w.get(slot), w.style());
            SpriteLibrary.Resolved r = closeups.resolve(e, a.clip(), Actor.IDLE_CLIP, a.mirrored());
            if (r == null) continue;               // nothing of this slot shows close up
            SheetTexture t = sprites.sheet(r);
            if (t == null) {
                missing = true;
                continue;
            }
            if (slot == Slot.BODY) body = t;
            sheets.add(t);
            slots.add(slot);
            entries.add(e);
        }
        if (!missing) {
            // the actor's other clips, ahead of the cut to them
            for (CloseupLibrary.Entry e : entries) {
                for (String clip : e.clips().keySet()) {
                    if (!clip.equals(a.clip())) sprites.prefetch(closeups.resolve(e, clip, null, a.mirrored()));
                }
            }
        }
        Stack last = held.get(a.id());
        if (missing && last != null && usable(last)) {
            for (Drawn d : last.layers()) sprites.touch(d.sheet());
            return last;
        }
        if (sheets.isEmpty()) return null;
        SheetTexture lead = body != null ? body : sheets.get(0);
        SpriteProfile profile = entries.get(sheets.indexOf(lead)).profile();
        int frames = Math.max(1, lead.frameCount());
        int frame = (int) Math.floor(a.clipTime() * profile.fps()) % frames;
        List<Drawn> layers = new ArrayList<>();
        for (int i = 0; i < sheets.size(); i++) {
            SheetTexture t = sheets.get(i);
            CloseupLibrary.Entry e = entries.get(i);
            layers.add(new Drawn(t, LayerStack.mapFrame(frame, frames, t.frameCount()), a.mirrored(),
                    a.palette(slots.get(i), t.palette(), e.variants())));
        }
        Stack s = new Stack(layers, profile.frameWidth());
        if (!missing) held.put(a.id(), s);
        return s;
    }

    private static boolean usable(Stack s) {
        for (Drawn d : s.layers()) if (d.sheet().closed()) return false;
        return true;
    }

    private static void drawActor(Batch batch, Actor a, Stack s, float x, float y, float size) {
        float[] tint = a.tint();
        float alpha = a.alpha();
        for (Drawn d : s.layers()) {
            double[] region = d.sheet().region(d.mirrored());
            if (region[2] <= region[0] || region[3] <= region[1]) continue;
            float[] uv = d.sheet().uv(d.frame(), d.mirrored());
            batch.rect(d.sheet().texture(), x + (float) region[0] * size, y + (float) region[1] * size,
                    (float) (region[2] - region[0]) * size, (float) (region[3] - region[1]) * size,
                    uv[0], uv[1], uv[2], uv[3], tint[0], tint[1], tint[2], alpha,
                    batch.palette(d.sheet().indexed() ? d.palette() : null));
        }
    }

    /**
     * A dusky room: a gradient from the ceiling's shadow down to a warm wall,
     * lighter in the middle where the actors stand (a lamp between them) and
     * darker towards the sides.
     */
    public static void backdrop(Ui ui, float x, float y, float w, float h) {
        int rows = 48, cols = 32;
        float cw = w / cols, rh = h / rows;
        for (int j = 0; j < rows; j++) {
            float t = j / (float) (rows - 1);
            for (int i = 0; i < cols; i++) {
                float u = (i + 0.5f) / cols - 0.5f, v = t - 0.45f;
                float glow = (float) Math.exp(-(u * u * 5.5f + v * v * 4.0f));
                float[] c = {mix(0.09f, 0.30f, t) + 0.16f * glow, mix(0.09f, 0.22f, t) + 0.11f * glow,
                        mix(0.15f, 0.22f, t) + 0.05f * glow, 1};
                ui.rect(x + i * cw, y + j * rh, cw + 1, rh + 1, c);
            }
        }
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    /** The line: a box along the bottom, the speaker's name on a tab, the text typing itself out. */
    private static void dialogue(Ui ui, Cutscene.Line line, double now, float x, float y, float w, float h) {
        float pad = PAD, bh = boxHeight(h);
        float bx = x + pad, by = y + h - bh - pad, bw = w - 2 * pad;
        ui.rect(bx, by, bw, bh, new float[]{0.06f, 0.06f, 0.10f, 0.86f});
        ui.outline(bx, by, bw, bh, 2, Theme.withAlpha(Theme.ACCENT, 0.55f));
        String name = line.speaker().name();
        float nw = ui.large.width(name) + 28;
        float nx = line.speaker().side() == Actor.Side.LEFT ? bx + 18 : bx + bw - nw - 18;
        ui.rect(nx, by - 30, nw, 34, new float[]{0.10f, 0.09f, 0.16f, 0.95f});
        ui.outline(nx, by - 30, nw, 34, 2, Theme.withAlpha(Theme.ACCENT, 0.7f));
        ui.text(ui.large, name, nx + 14, by - 26, Theme.ACCENT);
        ui.paragraph(ui.large, line.shown(now), bx + 24, by + 22, bw - 48, Theme.TITLE);
    }
}
