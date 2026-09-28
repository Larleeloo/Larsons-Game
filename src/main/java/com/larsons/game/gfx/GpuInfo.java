package com.larsons.game.gfx;

import java.util.Locale;

import static org.lwjgl.opengl.GL33C.*;

/**
 * What the driver says it is, and whether that is real GPU acceleration.
 *
 * <p>An OpenGL context does not promise a graphics card: Mesa's llvmpipe,
 * Windows' "GDI Generic" and Microsoft's Basic Render Driver all hand out a
 * perfectly valid context that rasterises on the CPU. The game still runs on
 * them, but slowly, so under {@code -Dlarsons.gpu=required} (what the
 * IntelliJ GPU profile sets) it says so loudly instead of leaving the player
 * to wonder why it is slow.
 *
 * @param vendor     {@code GL_VENDOR}
 * @param renderer   {@code GL_RENDERER} — the card's name on real hardware
 * @param version    {@code GL_VERSION}
 * @param maxTexture {@code GL_MAX_TEXTURE_SIZE}, which bounds how big a packed sprite sheet can be
 * @param samples    MSAA samples actually granted
 */
public record GpuInfo(String vendor, String renderer, String version, int maxTexture, int samples) {

    /** Read from the context current on this thread. */
    public static GpuInfo query() {
        return new GpuInfo(
                string(GL_VENDOR), string(GL_RENDERER), string(GL_VERSION),
                glGetInteger(GL_MAX_TEXTURE_SIZE), glGetInteger(GL_SAMPLES));
    }

    /** Whether the driver rasterises on the CPU rather than on a GPU. */
    public boolean software() {
        return isSoftware(renderer) || isSoftware(vendor);
    }

    static boolean isSoftware(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.contains("llvmpipe") || n.contains("softpipe") || n.contains("swrast")
                || n.contains("software rasterizer") || n.contains("gdi generic")
                || n.contains("basic render") || n.contains("lavapipe");
    }

    /** One line for the console and the HUD. */
    public String describe() {
        return renderer + " (" + vendor + ", GL " + version.split(" ")[0] + ")"
                + (software() ? " — SOFTWARE, no GPU acceleration" : " — GPU accelerated");
    }

    private static String string(int name) {
        String s = glGetString(name);
        return s == null ? "unknown" : s;
    }
}
