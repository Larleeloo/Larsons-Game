package com.larsons.game.gfx;

import com.larsons.game.grade.Grading;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * The colour grade put on the picture ({@link Grading}): what has been drawn
 * so far is copied off the screen (its anti-aliasing resolved) and drawn
 * back with every colour looked up in the grade's cube. One copy and one
 * pass over the screen, and only while a grade is on.
 */
public final class GradeFilter implements AutoCloseable {

    private static final String VERTEX = """
            #version 330 core
            out vec2 vUv;
            void main() {
                // one triangle over the whole screen
                vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                vUv = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            uniform sampler2D uScene;
            uniform sampler3D uLut;
            uniform float uSize;
            uniform int uLinear;
            out vec4 fragColor;
            void main() {
                vec3 c = texture(uScene, vUv).rgb;
                // read between the cube's cells (a smooth grade), or the one cell the colour is in (snapped)
                vec3 p = uLinear == 1 ? c * ((uSize - 1.0) / uSize) + 0.5 / uSize : c;
                fragColor = vec4(texture(uLut, p).rgb, 1.0);
            }
            """;

    private final Shader shader = new Shader("grade", VERTEX, FRAGMENT);
    private final int vao = glGenVertexArrays();
    private int fbo, scene, width, height;
    private int lut, lutSize;
    private boolean lutLinear;
    private long made = Long.MIN_VALUE;
    private Grading madeFor;

    /** Grade what is on the screen ({@code width x height} pixels) by {@code g}; nothing when it changes nothing. */
    public void apply(Grading g, int width, int height) {
        if (g == null || g.identity() || width <= 0 || height <= 0) return;
        target(width, height);
        cube(g);

        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
        glDisable(GL_SCISSOR_TEST);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        glViewport(0, 0, width, height);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_BLEND);
        shader.use();
        shader.set("uScene", 0);
        shader.set("uLut", 1);
        shader.set("uSize", (float) lutSize);
        shader.set("uLinear", lutLinear ? 1 : 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_3D, lut);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, scene);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        com.larsons.game.core.Perf.drew(0);
        glBindVertexArray(0);
        glEnable(GL_BLEND);
        glDepthMask(true);
    }

    /** Somewhere to copy the screen to, its size. */
    private void target(int w, int h) {
        if (fbo != 0 && w == width && h == height) return;
        if (fbo != 0) {
            glDeleteFramebuffers(fbo);
            glDeleteTextures(scene);
        }
        width = w;
        height = h;
        scene = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, scene);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, scene, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[colours] the grade's copy of the screen could not be made");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /** The grade's cube, made again when the grade has changed. */
    private void cube(Grading g) {
        if (lut != 0 && madeFor == g && made == g.version()) return;
        Grading.Lut l = g.lut();
        if (lut == 0) lut = glGenTextures();
        glBindTexture(GL_TEXTURE_3D, lut);
        ByteBuffer buf = MemoryUtil.memAlloc(l.rgb().length);
        buf.put(l.rgb()).flip();
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage3D(GL_TEXTURE_3D, 0, GL_RGB8, l.size(), l.size(), l.size(), 0, GL_RGB, GL_UNSIGNED_BYTE, buf);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        MemoryUtil.memFree(buf);
        int filter = l.linear() ? GL_LINEAR : GL_NEAREST;
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        lutSize = l.size();
        lutLinear = l.linear();
        made = g.version();
        madeFor = g;
    }

    @Override
    public void close() {
        shader.close();
        glDeleteVertexArrays(vao);
        if (fbo != 0) {
            glDeleteFramebuffers(fbo);
            glDeleteTextures(scene);
        }
        if (lut != 0) glDeleteTextures(lut);
    }
}
