package com.larsons.game.gfx;

import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Textured, tinted quads, batched into as few draw calls as the textures
 * allow — used for everything flat in the game: the billboarded character
 * layers and items in the 3D world, their ground shadows, and the whole UI.
 *
 * <p>Quads are appended to a CPU buffer and sent to the GPU in one draw per
 * run of same-texture quads. Colours are straight alpha on the way in and
 * premultiplied here, to match the premultiplied textures (see
 * {@link PixelData}).
 */
public final class Batch implements AutoCloseable {

    private static final int FLOATS_PER_VERTEX = 13;      // xyz uv rgba clip
    private static final int MAX_QUADS = 8192;
    /** A clip rectangle nothing falls outside of: the whole quad is drawn. */
    private static final float NO_CLIP = 1e9f;

    private static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in vec4 aColor;
            layout(location = 3) in vec4 aClip;
            uniform mat4 uProj;
            out vec2 vUv;
            out vec4 vColor;
            out float vDepth;
            flat out vec4 vClip;
            void main() {
                gl_Position = uProj * vec4(aPos, 1.0);
                vUv = aUv;
                vColor = aColor;
                vDepth = gl_Position.w;
                vClip = aClip;
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            in vec4 vColor;
            in float vDepth;
            flat in vec4 vClip;       // u0 v0 u1 v1: texture coordinates outside it are not drawn
            uniform sampler2D uTex;
            uniform vec3 uFogColor;
            uniform vec2 uFog;          // near, far; far <= 0 disables fog
            uniform float uAlphaCut;
            out vec4 fragColor;
            void main() {
                // Sample before any discard: mip selection needs the neighbours' coordinates.
                vec4 c = texture(uTex, vUv) * vColor;
                if (any(lessThan(vUv, vClip.xy)) || any(greaterThan(vUv, vClip.zw))) discard;
                if (c.a <= uAlphaCut) discard;
                if (uFog.y > 0.0) {
                    float f = clamp((vDepth - uFog.x) / (uFog.y - uFog.x), 0.0, 1.0);
                    c.rgb = mix(c.rgb, uFogColor * c.a, f * f);
                }
                fragColor = c;
            }
            """;

    private final Shader shader;
    private final int vao, vbo, ebo;
    private final FloatBuffer data;
    private int quads;
    private Texture texture;
    private boolean drawing;
    private int drawCalls;

    public Batch() {
        shader = new Shader("batch", VERTEX, FRAGMENT);
        data = MemoryUtil.memAllocFloat(MAX_QUADS * 4 * FLOATS_PER_VERTEX);

        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) MAX_QUADS * 4 * FLOATS_PER_VERTEX * Float.BYTES,
                GL_DYNAMIC_DRAW);
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 4, GL_FLOAT, false, stride, 9L * Float.BYTES);
        glEnableVertexAttribArray(3);

        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        int[] indices = new int[MAX_QUADS * 6];
        for (int q = 0, v = 0; q < indices.length; q += 6, v += 4) {
            indices[q] = v;
            indices[q + 1] = v + 1;
            indices[q + 2] = v + 2;
            indices[q + 3] = v + 2;
            indices[q + 4] = v + 3;
            indices[q + 5] = v;
        }
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        glBindVertexArray(0);
    }

    /**
     * Start a batch drawn through {@code projection}.
     *
     * @param fogColor straight RGB the far end fades to, or {@code null} for no fog (the UI)
     */
    public void begin(Mat4 projection, float[] fogColor, float fogNear, float fogFar) {
        if (drawing) throw new IllegalStateException("batch already begun");
        drawing = true;
        drawCalls = 0;
        shader.use();
        shader.set("uProj", projection);
        shader.set("uTex", 0);
        shader.set("uAlphaCut", 0.003f);
        if (fogColor != null) {
            shader.set("uFogColor", fogColor[0], fogColor[1], fogColor[2]);
            shader.set("uFog", fogNear, fogFar);
        } else {
            shader.set("uFog", 0f, 0f);
        }
        glBindVertexArray(vao);
    }

    /** For UI and shadows: no fog. */
    public void begin(Mat4 projection) {
        begin(projection, null, 0, 0);
    }

    /**
     * One quad. Corners go top-left, top-right, bottom-right, bottom-left;
     * {@code (u0, v0)} is the texture's top-left of the region, {@code (u1, v1)}
     * its bottom-right. Colour is straight (not premultiplied) RGBA.
     */
    public void quad(Texture tex,
                     float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3,
                     float u0, float v0, float u1, float v1,
                     float r, float g, float b, float a) {
        quad(tex, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, u0, v0, u1, v1,
                -NO_CLIP, -NO_CLIP, NO_CLIP, NO_CLIP, r, g, b, a);
    }

    private void quad(Texture tex,
                      float x0, float y0, float z0, float x1, float y1, float z1,
                      float x2, float y2, float z2, float x3, float y3, float z3,
                      float u0, float v0, float u1, float v1,
                      float cu0, float cv0, float cu1, float cv1,
                      float r, float g, float b, float a) {
        if (!drawing) throw new IllegalStateException("batch not begun");
        if (tex != texture || quads == MAX_QUADS) {
            flush();
            texture = tex;
        }
        float pr = r * a, pg = g * a, pb = b * a;
        put(x0, y0, z0, u0, v0, pr, pg, pb, a, cu0, cv0, cu1, cv1);
        put(x1, y1, z1, u1, v0, pr, pg, pb, a, cu0, cv0, cu1, cv1);
        put(x2, y2, z2, u1, v1, pr, pg, pb, a, cu0, cv0, cu1, cv1);
        put(x3, y3, z3, u0, v1, pr, pg, pb, a, cu0, cv0, cu1, cv1);
        quads++;
    }

    /** A quad in 3D, from its top-left corner and two edge vectors. */
    public void quad(Texture tex, Vec3 topLeft, Vec3 right, Vec3 down,
                     float u0, float v0, float u1, float v1,
                     float r, float g, float b, float a) {
        quadClipped(tex, topLeft, right, down, u0, v0, u1, v1,
                -NO_CLIP, -NO_CLIP, NO_CLIP, NO_CLIP, r, g, b, a);
    }

    /**
     * A quad in 3D whose texture coordinates run from {@code (u0, v0)} to
     * {@code (u1, v1)} across it, drawn only where they fall inside {@code
     * (cu0, cv0)}–{@code (cu1, cv1)}: a texture region that covers just part
     * of the quad. The rest of the quad is left out, colour and depth alike.
     */
    public void quadClipped(Texture tex, Vec3 topLeft, Vec3 right, Vec3 down,
                            float u0, float v0, float u1, float v1,
                            float cu0, float cv0, float cu1, float cv1,
                            float r, float g, float b, float a) {
        Vec3 tr = topLeft.add(right);
        Vec3 br = tr.add(down);
        Vec3 bl = topLeft.add(down);
        quad(tex,
                (float) topLeft.x(), (float) topLeft.y(), (float) topLeft.z(),
                (float) tr.x(), (float) tr.y(), (float) tr.z(),
                (float) br.x(), (float) br.y(), (float) br.z(),
                (float) bl.x(), (float) bl.y(), (float) bl.z(),
                u0, v0, u1, v1, cu0, cv0, cu1, cv1, r, g, b, a);
    }

    /** An axis-aligned rectangle on the UI plane (y down). */
    public void rect(Texture tex, float x, float y, float w, float h,
                     float u0, float v0, float u1, float v1,
                     float r, float g, float b, float a) {
        quad(tex, x, y, 0, x + w, y, 0, x + w, y + h, 0, x, y + h, 0,
                u0, v0, u1, v1, r, g, b, a);
    }

    private void put(float x, float y, float z, float u, float v,
                     float r, float g, float b, float a,
                     float cu0, float cv0, float cu1, float cv1) {
        data.put(x).put(y).put(z).put(u).put(v).put(r).put(g).put(b).put(a)
                .put(cu0).put(cv0).put(cu1).put(cv1);
    }

    public void flush() {
        if (quads == 0 || texture == null) {
            quads = 0;
            data.clear();
            return;
        }
        data.flip();
        texture.bind(0);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, data);
        glDrawElements(GL_TRIANGLES, quads * 6, GL_UNSIGNED_INT, 0);
        drawCalls++;
        data.clear();
        quads = 0;
    }

    public void end() {
        flush();
        texture = null;
        drawing = false;
        glBindVertexArray(0);
    }

    /** Draw calls the last (or current) batch issued. */
    public int drawCalls() { return drawCalls; }

    @Override
    public void close() {
        MemoryUtil.memFree(data);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
        shader.close();
    }
}
