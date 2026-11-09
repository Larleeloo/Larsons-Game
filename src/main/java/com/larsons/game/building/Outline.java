package com.larsons.game.building;

import com.larsons.game.gfx.Shader;

import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * The editor's selection drawn as a line round each building picked - not
 * tinted - as an outliner does: every one picked marked in the stencil, then
 * drawn again in one colour, nudged a couple of pixels each of eight ways,
 * only where it was not marked - a ring round its outline, seen through
 * whatever stands in front of it. Each shader drawn with has a {@code
 * uShift} (clip-space nudge, in its vertex shader) and a {@code uSolid}
 * (rgb and 1 for one colour; 0 to draw as it is) its fragment shader ends
 * on.
 */
final class Outline {

    /** The line's colour: a warm white, plain on grass, stone and sky alike. */
    static final float R = 1.0f, G = 0.9f, B = 0.45f;

    /** The GLSL its vertex shader nudges by, after {@code gl_Position} is set. */
    static final String SHIFT = "gl_Position.xy += uShift * gl_Position.w;";

    private Outline() {}

    /** Ring {@code picked} round, drawing each with {@code draw} ({@code shader} in use, its state set). */
    static void draw(Shader shader, List<Building> picked, Consumer<Building> draw) {
        if (picked.isEmpty()) return;
        int[] vp = new int[4];
        glGetIntegerv(GL_VIEWPORT, vp);
        if (vp[2] <= 0 || vp[3] <= 0) return;
        float px = Math.max(2f, vp[3] / 400f);
        glEnable(GL_STENCIL_TEST);
        glStencilMask(0xFF);
        glClearStencil(0);
        glClear(GL_STENCIL_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        // the shapes themselves, marked
        glColorMask(false, false, false, false);
        glStencilFunc(GL_ALWAYS, 1, 0xFF);
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
        shader.set("uShift", 0f, 0f);
        shader.set("uSolid", 0f, 0f, 0f, 0f);
        for (Building b : picked) draw.accept(b);
        // and round them
        glColorMask(true, true, true, true);
        glStencilFunc(GL_NOTEQUAL, 1, 0xFF);
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
        shader.set("uSolid", R, G, B, 1f);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            shader.set("uShift", (float) (Math.cos(a) * px * 2 / vp[2]), (float) (Math.sin(a) * px * 2 / vp[3]));
            for (Building b : picked) draw.accept(b);
        }
        shader.set("uSolid", 0f, 0f, 0f, 0f);
        shader.set("uShift", 0f, 0f);
        glDisable(GL_STENCIL_TEST);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
    }
}
