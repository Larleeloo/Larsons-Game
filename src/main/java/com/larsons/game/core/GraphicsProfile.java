package com.larsons.game.core;

/**
 * The two ways the game can be drawn, each a set of the Display settings:
 * <b>Full</b>, as it comes, and <b>Lite</b>, for a low-end machine - a
 * MacBook Air, a laptop with the graphics built into its processor, a
 * machine with 8 GB of memory ({@link Hardware#verdict}) - aiming for a
 * steady 60 frames a second.
 *
 * <p>Lite turns off what costs a low-end graphics chip the most: the sun's
 * shadows, the Vibrant visuals look and its computed light rays (together
 * well over ten times the drawing of the flat look), and multisampling; it
 * draws the world at one pixel per screen point on a Retina or other
 * high-density screen ({@code gfx/Quality#renderScale}), sees less far
 * through the haze, keeps half the critters and weather particles, holds 60
 * frames a second instead of 120, and gives the sprite sheets half the
 * video memory (which a MacBook shares with everything else).
 *
 * <p>Settings, Display, Graphics: Auto (Lite on a low-end machine, Full
 * otherwise - set once for the machine, then the player's own changes are
 * kept), Full or Lite. Each setting can still be changed one by one after.
 */
public enum GraphicsProfile {

    FULL("Full", "MEDIUM", true, true, FramePacer.DEFAULT_FPS, 4, 1.0, 1.0, 1.0, 1536),
    LITE("Lite", "OFF", false, false, 60, 0, 0.5, 0.7, 0.5, 768);

    public final String label;
    public final String shadows;
    public final boolean vibrant, lightRays;
    public final int fps, msaa;
    /** The world's resolution on a high-density screen (on others it is drawn at the full size). */
    public final double renderScale;
    public final double viewDistance, wildlife;
    /** Video memory the sprite sheets may use, MB. */
    public final int vramMB;

    GraphicsProfile(String label, String shadows, boolean vibrant, boolean lightRays, int fps, int msaa,
                    double renderScale, double viewDistance, double wildlife, int vramMB) {
        this.label = label;
        this.shadows = shadows;
        this.vibrant = vibrant;
        this.lightRays = lightRays;
        this.fps = fps;
        this.msaa = msaa;
        this.renderScale = renderScale;
        this.viewDistance = viewDistance;
        this.wildlife = wildlife;
        this.vramMB = vramMB;
    }

    /** As saved: "full", "lite". */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The profile saved as {@code key}, or {@code fallback}. */
    public static GraphicsProfile of(String key, GraphicsProfile fallback) {
        if (key != null) for (GraphicsProfile p : values()) if (p.key().equalsIgnoreCase(key.trim())) return p;
        return fallback;
    }

    /** The world's resolution on a screen of {@code density} pixels a point (1 on most, 2 on a Retina). */
    public double renderScale(double density) {
        return density >= 1.5 ? renderScale : 1.0;
    }

    /** Video memory the sprite sheets may use, bytes. */
    public long vramBytes() {
        return (long) vramMB << 20;
    }

    /**
     * Put this profile's settings into {@code s} for a screen of {@code
     * density} pixels a point. Multisampling is the window's and changes at
     * the next start; the frame rate and the sprites' memory the caller
     * passes on to what holds them.
     */
    public void apply(Settings s, double density) {
        s.shadows = shadows;
        s.vibrant = vibrant;
        if (vibrant && s.vibrantStrength <= 0) s.vibrantStrength = com.larsons.game.gfx.Visuals.DEFAULT.strength();
        s.lightRays = lightRays;
        s.setFps(fps);
        s.setMsaa(msaa);
        s.renderScale = renderScale(density);
        s.viewDistance = viewDistance;
        s.wildlife = wildlife;
        if (!s.vramGiven) s.vramBytes = vramBytes();
    }

    /** Whether {@code s} is drawn as this profile draws (the Vibrant strength aside) - else it is "custom". */
    public boolean matches(Settings s, double density) {
        return com.larsons.game.gfx.Visuals.Shadows.of(s.shadows, null)
                == com.larsons.game.gfx.Visuals.Shadows.of(shadows, null)
                && s.vibrant == vibrant && (s.lightRays == lightRays || !vibrant) && s.savedFps() == fps
                && s.savedMsaa() == msaa && Math.abs(s.renderScale - renderScale(density)) < 1e-3
                && Math.abs(s.viewDistance - viewDistance) < 1e-3 && Math.abs(s.wildlife - wildlife) < 1e-3;
    }
}
