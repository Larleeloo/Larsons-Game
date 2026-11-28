package com.larsons.game.gfx;

/**
 * How much the machine is asked to draw, beyond the look itself ({@link
 * Visuals}): what the Lite graphics turn down on a low-end machine (Settings,
 * Display; {@code core/GraphicsProfile}).
 *
 * <ul>
 *   <li>{@link #renderScale}: the world drawn at this fraction of the
 *       window's pixels, then stretched pixel for pixel to fill it ({@link
 *       RenderTarget}) - on a Retina or other high-density screen, 0.5 draws
 *       one pixel for every screen point, a quarter of the work, and the
 *       pixel art looks the same. The menus and the HUD stay sharp.</li>
 *   <li>{@link #viewDistance}: how far the world is seen before the haze
 *       closes in, as a share of what the level asks for; what stands past
 *       the haze is not drawn at all.</li>
 *   <li>{@link #life}: how many of the little things that come and go by
 *       themselves there are - the critters, the animals that wander by, the
 *       raindrops and the snowflakes - as a share of what the place has.</li>
 * </ul>
 *
 * <p>The game sets {@link #now} from its settings each frame; the world's
 * renderer and the wildlife read it. Plain data, no OpenGL.
 */
public record Quality(double renderScale, double viewDistance, double life) {

    /** Everything as it comes: the full window, the whole view, all the wildlife. */
    public static final Quality FULL = new Quality(1, 1, 1);

    /** The least the view is ever cut to, metres: the haze never closes in nearer than this. */
    public static final double NEAREST_VIEW = 24;

    /** What the frame being drawn asks for. */
    public static volatile Quality now = FULL;

    public Quality {
        renderScale = Double.isFinite(renderScale) ? Math.max(0.25, Math.min(1, renderScale)) : 1;
        viewDistance = Double.isFinite(viewDistance) ? Math.max(0.3, Math.min(1, viewDistance)) : 1;
        life = Double.isFinite(life) ? Math.max(0, Math.min(1, life)) : 1;
    }

    /** Whether the world is drawn smaller than the window and stretched to fill it. */
    public boolean scaled() {
        return renderScale < 0.999;
    }

    /** How far the haze closes in, metres, for a level whose own haze is at {@code far}. */
    public float fogFar(float far) {
        if (viewDistance >= 0.999 || far <= NEAREST_VIEW) return far;
        return (float) Math.max(NEAREST_VIEW, far * viewDistance);
    }

    /** {@code n} things the place would have, as many as {@link #life} keeps (never fewer than none). */
    public int some(int n) {
        return (int) Math.round(n * life);
    }
}
