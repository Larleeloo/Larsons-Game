package com.larsons.game.gfx;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * The colours of the Vibrant visuals look, put on the world once it is
 * drawn: what is dull made a little richer (vibrance: the greyer a colour
 * the more it gains, so what is bright already does not glare), a gentle
 * S-curve to the light, deeper darks and fuller lights, and the brightest
 * glowing faintly into the pixels round them. One copy of the picture and
 * one pass over it, pixel for pixel: the pixel art stays as sharp.
 */
public final class LookFilter implements AutoCloseable {

    private static final String VERTEX = """
            #version 330 core
            out vec2 vUv;
            void main() {
                vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                vUv = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            uniform sampler2D uScene;
            uniform vec2 uPixel;
            uniform float uAmount;
            out vec4 fragColor;
            vec3 bright(vec2 at) {
                vec3 c = texture(uScene, at).rgb;
                return max(c - 0.82, 0.0);
            }
            void main() {
                vec3 c = texture(uScene, vUv).rgb;
                float l = dot(c, vec3(0.299, 0.587, 0.114));
                float sat = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
                vec3 v = mix(vec3(l), c, 1.0 + uAmount * 0.32 * (1.0 - sat) * (1.0 - sat));
                v = clamp(v, 0.0, 1.0);
                v = mix(v, v * v * (3.0 - 2.0 * v), uAmount * 0.22);
                // the brightest, a little into what is round them
                vec3 b = bright(vUv + vec2(uPixel.x * 2.0, 0.0)) + bright(vUv - vec2(uPixel.x * 2.0, 0.0))
                        + bright(vUv + vec2(0.0, uPixel.y * 2.0)) + bright(vUv - vec2(0.0, uPixel.y * 2.0));
                v += b * 0.25 * uAmount;
                fragColor = vec4(clamp(v, 0.0, 1.0), 1.0);
            }
            """;

    private final Shader shader = new Shader("look", VERTEX, FRAGMENT);
    private final int vao = glGenVertexArrays();
    private int fbo, scene, width, height;

    /** Put the look on what is drawn ({@code width x height} pixels), {@code amount} strong (0 - 1). */
    public void apply(float amount, int width, int height) {
        if (amount <= 0 || width <= 0 || height <= 0) return;
        target(width, height);
        int was = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, was);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
        glDisable(GL_SCISSOR_TEST);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, was);

        boolean depthTest = glIsEnabled(GL_DEPTH_TEST), blend = glIsEnabled(GL_BLEND);
        glViewport(0, 0, width, height);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_BLEND);
        shader.use();
        shader.set("uScene", 0);
        shader.set("uPixel", 1f / width, 1f / height);
        shader.set("uAmount", amount);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, scene);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        com.larsons.game.core.Perf.drew(0);
        glBindVertexArray(0);
        if (blend) glEnable(GL_BLEND);
        if (depthTest) glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
    }

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
        int was = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, scene, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[look] the vibrant look's copy of the picture could not be made");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, was);
    }

    @Override
    public void close() {
        shader.close();
        glDeleteVertexArrays(vao);
        if (fbo != 0) {
            glDeleteFramebuffers(fbo);
            glDeleteTextures(scene);
        }
    }
}
