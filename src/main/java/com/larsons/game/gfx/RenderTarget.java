package com.larsons.game.gfx;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * A picture drawn smaller than the window, then stretched to fill it pixel
 * for pixel ({@link Quality#renderScale}): the world of a low-end machine.
 * A colour texture and a depth-and-stencil buffer of their own; {@link
 * #begin} draws into them, {@link #end} goes back to where drawing was and
 * puts the picture there, every pixel of it a block of the window's (nearest,
 * so the pixel art stays sharp-edged).
 *
 * <p>Single-sampled: the world drawn this way has no multisampling of its
 * own (which pixel art hardly needs, and which a machine this is for cannot
 * spare).
 */
public final class RenderTarget implements AutoCloseable {

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
            uniform sampler2D uPicture;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(texture(uPicture, vUv).rgb, 1.0);
            }
            """;

    private Shader shader;
    private int vao;
    private int fbo, colour, depth, width, height;
    private int framebufferWas;
    /** The driver would not make it at this size: it is not drawn into. */
    private boolean broken;
    private final int[] viewportWas = new int[4];

    /** Its size now, pixels (0: not made yet). */
    public int width() { return width; }

    public int height() { return height; }

    /**
     * Draw into the picture from now on - {@code w x h} pixels, made (again)
     * at that size if it is another - until {@link #end}. The viewport is
     * the whole picture; nothing is cleared (the world clears what it draws).
     * False, with nothing changed, if the driver would not make it: draw
     * straight to the window then.
     */
    public boolean begin(int w, int h) {
        make(Math.max(1, w), Math.max(1, h));
        if (broken) return false;
        framebufferWas = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        glGetIntegerv(GL_VIEWPORT, viewportWas);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, width, height);
        return true;
    }

    /**
     * Back to the framebuffer drawn into before {@link #begin}, and the
     * picture put over the whole of its {@code screenW x screenH} pixels.
     */
    public void end(int screenW, int screenH) {
        glBindFramebuffer(GL_FRAMEBUFFER, framebufferWas);
        glViewport(0, 0, screenW, screenH);
        if (shader == null) {
            shader = new Shader("render-target", VERTEX, FRAGMENT);
            vao = glGenVertexArrays();
        }
        boolean depthTest = glIsEnabled(GL_DEPTH_TEST), blend = glIsEnabled(GL_BLEND),
                scissor = glIsEnabled(GL_SCISSOR_TEST), stencil = glIsEnabled(GL_STENCIL_TEST);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_STENCIL_TEST);
        glDepthMask(false);
        shader.use();
        shader.set("uPicture", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, colour);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        com.larsons.game.core.Perf.drew(0);
        glBindVertexArray(0);
        glDepthMask(true);
        if (depthTest) glEnable(GL_DEPTH_TEST);
        if (blend) glEnable(GL_BLEND);
        if (scissor) glEnable(GL_SCISSOR_TEST);
        if (stencil) glEnable(GL_STENCIL_TEST);
        glViewport(viewportWas[0], viewportWas[1], screenW, screenH);
    }

    private void make(int w, int h) {
        if (fbo != 0 && w == width && h == height) return;
        free();
        width = w;
        height = h;
        colour = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colour);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        depth = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, depth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, w, h);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);
        int was = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colour, 0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depth);
        broken = glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE;
        if (broken) {
            System.err.println("[gpu] the world's smaller picture could not be made (" + w + "x" + h
                    + "): drawing it at the window's full size");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, was);
    }

    private void free() {
        if (fbo != 0) glDeleteFramebuffers(fbo);
        if (colour != 0) glDeleteTextures(colour);
        if (depth != 0) glDeleteRenderbuffers(depth);
        fbo = colour = depth = width = height = 0;
        broken = false;
    }

    @Override
    public void close() {
        free();
        if (shader != null) shader.close();
        if (vao != 0) glDeleteVertexArrays(vao);
        shader = null;
        vao = 0;
    }
}
