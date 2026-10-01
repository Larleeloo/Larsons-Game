package com.larsons.game.cutscene;

import com.larsons.game.sprite.Palettes;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Variants;
import com.larsons.game.sprite.Wardrobe;

import java.util.EnumMap;
import java.util.Map;

/**
 * One character in a cutscene: a {@link Wardrobe} - any items in any colours,
 * the player's own character included - playing close-up clips, and what the
 * scene does to how it looks: colours fading from one to another, a flash,
 * a tint and a fade in or out.
 *
 * <p>Everything here is per actor and per layer: two actors can wear the same
 * sheets in different colours, and a fade on one layer (the hair, say) leaves
 * the others alone. None of it touches a texture - a layer's colours are its
 * palette, worked out each frame ({@link #palette}) and looked up on the GPU.
 */
public final class Actor {

    /** Where the actor stands in a two-shot: on the left facing right, or on the right facing left. */
    public enum Side { LEFT, RIGHT }

    /** The clip an actor plays when it is doing nothing else. */
    public static final String IDLE_CLIP = "listen";

    private record Fade(String from, double seconds, double[] elapsed) {
        double t() { return seconds <= 0 ? 1 : Math.min(1, elapsed[0] / seconds); }
    }

    private final String id;
    private final String name;
    private final Wardrobe wardrobe;
    private Side side = Side.LEFT;
    private String clip = IDLE_CLIP;
    private double clipTime;
    private double clipUntil = Double.POSITIVE_INFINITY;
    private final Map<Slot, Fade> fades = new EnumMap<>(Slot.class);
    private int flashRgb;
    private double flashSeconds, flashLeft;
    private final float[] tint = {1, 1, 1}, tintFrom = {1, 1, 1}, tintTo = {1, 1, 1};
    private double tintSeconds, tintElapsed;
    private float alpha = 1, alphaFrom = 1, alphaTo = 1;
    private double alphaSeconds, alphaElapsed;

    /** {@code wardrobe} is the actor's own: changes to it in the scene stay in the scene. */
    public Actor(String id, String name, Wardrobe wardrobe) {
        this.id = id;
        this.name = name;
        this.wardrobe = wardrobe;
    }

    public String id() { return id; }

    public String name() { return name; }

    public Wardrobe wardrobe() { return wardrobe; }

    public Side side() { return side; }

    public void stand(Side side) { this.side = side; }

    /** Whether the actor is drawn mirrored (it stands on the right, facing left). */
    public boolean mirrored() { return side == Side.RIGHT; }

    public String clip() { return clip; }

    /** Seconds into the current clip. */
    public double clipTime() { return clipTime; }

    /** Play {@code clip} from its start (or carry on if it is already playing), until told otherwise. */
    public void play(String clip) {
        play(clip, Double.POSITIVE_INFINITY);
    }

    /** Play {@code clip} for {@code seconds}, then go back to {@link #IDLE_CLIP}. */
    public void play(String clip, double seconds) {
        if (!clip.equals(this.clip)) {
            this.clip = clip;
            clipTime = 0;
        }
        clipUntil = seconds;
    }

    /** Change a slot's colour (an option, {@code #rrggbb} or null: the item's own), fading over {@code seconds}. */
    public void colour(Slot slot, String colour, double seconds) {
        String from = wardrobe.colour(slot);
        wardrobe.setColour(slot, colour);
        fades.put(slot, new Fade(from, seconds, new double[1]));
    }

    /** A flash of {@code rgb} over every layer, fading out over {@code seconds}. */
    public void flash(int rgb, double seconds) {
        flashRgb = rgb;
        flashSeconds = Math.max(0.01, seconds);
        flashLeft = flashSeconds;
    }

    /** Tint the actor towards {@code rgb} by {@code amount} (0: none), over {@code seconds}. */
    public void tint(int rgb, double amount, double seconds) {
        System.arraycopy(tint, 0, tintFrom, 0, 3);
        for (int i = 0; i < 3; i++) {
            float c = ((rgb >> (16 - 8 * i)) & 0xFF) / 255f;
            tintTo[i] = (float) (1 + (c - 1) * Math.max(0, Math.min(1, amount)));
        }
        tintSeconds = seconds;
        tintElapsed = 0;
        if (seconds <= 0) System.arraycopy(tintTo, 0, tint, 0, 3);
    }

    /** Fade the whole actor to {@code alpha} over {@code seconds}. */
    public void fade(float alpha, double seconds) {
        alphaFrom = this.alpha;
        alphaTo = alpha;
        alphaSeconds = seconds;
        alphaElapsed = 0;
        if (seconds <= 0) this.alpha = alpha;
    }

    /** RGB the actor's layers are multiplied by. */
    public float[] tint() { return tint.clone(); }

    public float alpha() { return alpha; }

    public void update(double dt) {
        clipTime += dt;
        clipUntil -= dt;
        if (clipUntil <= 0) {
            clip = IDLE_CLIP;
            clipTime = 0;
            clipUntil = Double.POSITIVE_INFINITY;
        }
        fades.values().removeIf(f -> {
            f.elapsed()[0] += dt;
            return f.elapsed()[0] >= f.seconds();
        });
        flashLeft = Math.max(0, flashLeft - dt);
        if (tintElapsed < tintSeconds) {
            tintElapsed = Math.min(tintSeconds, tintElapsed + dt);
            double t = smooth(tintElapsed / tintSeconds);
            for (int i = 0; i < 3; i++) tint[i] = (float) (tintFrom[i] + (tintTo[i] - tintFrom[i]) * t);
        }
        if (alphaElapsed < alphaSeconds) {
            alphaElapsed = Math.min(alphaSeconds, alphaElapsed + dt);
            alpha = (float) (alphaFrom + (alphaTo - alphaFrom) * smooth(alphaElapsed / alphaSeconds));
        }
    }

    /**
     * The palette a layer of the item worn in {@code slot} is drawn in: its
     * sheet's own palette ({@code base}) in the wardrobe's colours for the slot
     * and the skin, mid-fade mixed with the colours it is fading from, then
     * the flash. Null for a layer that is not pixel art in a palette.
     */
    public int[] palette(Slot slot, int[] base, Variants variants) {
        if (base == null) return null;
        if (variants == null) return Palettes.flash(base, flashRgb, flashLeft / Math.max(0.01, flashSeconds));
        String own = wardrobe.colour(slot), skin = wardrobe.skin();
        int[] p = Variants.recolored(base, variants.recolor(CloseupLibrary.choice(slot, own, skin)));
        Fade fo = slot == Slot.BODY ? null : fades.get(slot), fs = fades.get(Slot.BODY);
        if (fo != null || fs != null) {
            String ownFrom = fo != null ? fo.from() : own, skinFrom = fs != null ? fs.from() : skin;
            int[] q = Variants.recolored(base, variants.recolor(CloseupLibrary.choice(slot, ownFrom, skinFrom)));
            if (fo != null) p = Palettes.mix(q, p, smooth(fo.t()), variants.entries(Variants.OWN));
            if (fs != null) p = Palettes.mix(q, p, smooth(fs.t()), variants.entries(Variants.SKIN));
        }
        return Palettes.flash(p, flashRgb, flashLeft / Math.max(0.01, flashSeconds));
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
