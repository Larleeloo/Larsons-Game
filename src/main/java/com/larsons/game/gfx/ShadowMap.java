package com.larsons.game.gfx;

import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * The sun's shadow map: how near the sun the nearest thing is, seen from the
 * sun, over a square {@link Visuals.Shadows#reach} metres every way round
 * the eye's target. What casts a shadow is drawn into it first ({@link
 * #begin} ... {@link #end}); every shader of the world then looks a point up
 * in it ({@link SceneLight#GLSL}'s {@code sceneShadow}) - in shadow when
 * something lies between it and the sun.
 *
 * <p>The square moves with the target in steps of one of its pixels, so the
 * shadows' edges stand still as she walks instead of crawling. The sun is
 * never taken lower than {@link #LOWEST} over the ground: at sunrise a tree's
 * shadow would reach the edge of the map.
 */
public final class ShadowMap implements AutoCloseable {

    /** The lowest the sun is taken to stand for its shadows, as the sine of its height over the ground. */
    public static final double LOWEST = 0.42;

    private int fbo, depth, size;
    /** A depth texture of one pixel, nothing in it: bound in the map's place while there are no shadows. */
    private static int blank;
    private Mat4 viewProj = Mat4.identity();
    private Vec3 centre = Vec3.ZERO, towardSun = Vec3.UP;
    private double reach;
    private final int[] viewport = new int[4];
    private int framebufferWas;

    /** The way the shadows are cast for a sun towards {@code sun}: never lower than {@link #LOWEST}. */
    public static Vec3 castFrom(Vec3 sun) {
        double h = Math.hypot(sun.x(), sun.z());
        double y = Math.max(LOWEST, sun.y());
        if (h < 1e-4) return new Vec3(0.08, 1, 0.05).normalize();
        double flat = Math.sqrt(Math.max(0, 1 - y * y));
        return new Vec3(sun.x() / h * flat, y, sun.z() / h * flat).normalize();
    }

    /**
     * Start drawing what casts shadows: a map {@code px} pixels a side,
     * {@code reach} metres every way round {@code centre}, the sun towards
     * {@code sun} (as {@link #castFrom} has it). Only depth is drawn, until
     * {@link #end}. Returns the sun's view and projection to draw through.
     */
    public Mat4 begin(int px, Vec3 centre, Vec3 sun, double reach) {
        make(px);
        towardSun = sun;
        this.reach = reach;
        Vec3 forward = sun.scale(-1);
        Vec3 right = Math.abs(forward.y()) > 0.99 ? new Vec3(1, 0, 0) : forward.cross(Vec3.UP).normalize();
        Vec3 up = right.cross(forward).normalize();
        // the square in steps of its pixels, across the sun's view
        double texel = 2 * reach / px;
        double cr = right.dot(centre), cu = up.dot(centre);
        double sr = Math.floor(cr / texel) * texel - cr, su = Math.floor(cu / texel) * texel - cu;
        this.centre = centre.add(right.scale(sr)).add(up.scale(su));
        double back = reach + 90;
        Vec3 eye = this.centre.add(sun.scale(back));
        viewProj = Mat4.orthographic(-reach, reach, -reach, reach, 1, back + reach + 90)
                .mul(Mat4.view(eye, right, up, forward));

        glGetIntegerv(GL_VIEWPORT, viewport);
        framebufferWas = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, size, size);
        glDisable(GL_SCISSOR_TEST);
        glDepthMask(true);
        glClearDepth(1.0);
        glClear(GL_DEPTH_BUFFER_BIT);
        glColorMask(false, false, false, false);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1.6f, 3f);
        return viewProj;
    }

    /** Done drawing into it: back to the frame being drawn, as it was. */
    public void end() {
        glDisable(GL_POLYGON_OFFSET_FILL);
        glColorMask(true, true, true, true);
        glBindFramebuffer(GL_FRAMEBUFFER, framebufferWas);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glEnable(GL_BLEND);
    }

    /** The sun's view and projection the map was drawn through. */
    public Mat4 viewProj() { return viewProj; }

    /** Where the square's middle is. */
    public Vec3 centre() { return centre; }

    /** The way to the sun it was drawn for. */
    public Vec3 towardSun() { return towardSun; }

    /** How far round its middle it reaches, metres. */
    public double reach() { return reach; }

    /** Its pixels a side (0: not made). */
    public int size() { return size; }

    /** The depth texture (0: not made). */
    public int texture() { return depth; }

    /** Make (or make again, at another size) the texture and its framebuffer. */
    private void make(int px) {
        if (fbo != 0 && px == size) return;
        free();
        size = px;
        depth = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depth);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, px, px, 0, GL_DEPTH_COMPONENT, GL_FLOAT,
                (FloatBuffer) null);
        compare();
        fbo = glGenFramebuffers();
        int was = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[shadows] the sun's shadow map could not be made");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, was);
    }

    /** The bound texture compares depths (a {@code sampler2DShadow}), smoothly between its pixels. */
    private static void compare() {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
    }

    /** A depth texture of one pixel at the far end, for a {@code sampler2DShadow} with no map to read. */
    public static int blank() {
        if (blank != 0) return blank;
        blank = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, blank);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, 1, 1, 0, GL_DEPTH_COMPONENT, GL_FLOAT,
                new float[]{1f});
        compare();
        return blank;
    }

    private void free() {
        if (fbo != 0) glDeleteFramebuffers(fbo);
        if (depth != 0) glDeleteTextures(depth);
        fbo = depth = size = 0;
    }

    @Override
    public void close() {
        free();
    }
}
