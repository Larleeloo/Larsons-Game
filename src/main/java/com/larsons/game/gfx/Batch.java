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
 *
 * <p>A quad can name a palette ({@link #palette}): its texture then holds
 * palette indices ({@link Texture#indexed()}) and the shader colours each
 * texel from that palette's row of the {@link PaletteAtlas} - the pixel-art
 * layers, which so take any colours without being uploaded again. Quads of
 * different palettes on the same texture still share a draw call.
 */
public final class Batch implements AutoCloseable {

    private static final int FLOATS_PER_VERTEX = 14;      // xyz uv rgba clip palette
    private static final int MAX_QUADS = 8192;
    /** A clip rectangle nothing falls outside of: the whole quad is drawn. */
    private static final float NO_CLIP = 1e9f;

    private static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in vec4 aColor;
            layout(location = 3) in vec4 aClip;
            layout(location = 4) in float aPalette;
            uniform mat4 uProj;
            out vec2 vUv;
            out vec4 vColor;
            out float vDepth;
            flat out vec4 vClip;
            flat out int vPalette;
            void main() {
                gl_Position = uProj * vec4(aPos, 1.0);
                vUv = aUv;
                vColor = aColor;
                vDepth = gl_Position.w;
                vClip = aClip;
                vPalette = int(aPalette + 0.5) - 1;
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            in vec4 vColor;
            in float vDepth;
            flat in vec4 vClip;       // u0 v0 u1 v1: texture coordinates outside it are not drawn
            flat in int vPalette;     // the palette row an index texture is coloured from; -1: RGBA texture
            uniform sampler2D uTex;
            uniform sampler2D uPalettes;
            uniform vec3 uFogColor;
            uniform vec2 uFog;          // near, far; far <= 0 disables fog
            uniform float uAlphaCut;
            out vec4 fragColor;
            void main() {
                // Sample before any discard: mip selection needs the neighbours' coordinates.
                vec4 t = texture(uTex, vUv);
                vec4 c;
                if (vPalette >= 0) {
                    vec4 p = texelFetch(uPalettes, ivec2(int(t.r * 255.0 + 0.5), vPalette), 0);
                    c = vec4(p.rgb * p.a, p.a) * vColor;
                } else {
                    c = t * vColor;
                }
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
    private final PaletteAtlas palettes = new PaletteAtlas();
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
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 13L * Float.BYTES);
        glEnableVertexAttribArray(4);

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
        shader.set("uPalettes", 1);
        shader.set("uAlphaCut", 0.003f);
        palettes.reset();
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

    /** No palette: the texture is drawn as it is (RGBA). */
    public static final int NO_PALETTE = -1;

    /**
     * The palette handle for quads coloured from {@code argb} (straight ARGB,
     * up to {@value PaletteAtlas#SIZE} entries, entry {@code i} for index
     * {@code i}), to pass to the quads that draw an index texture; {@link
     * #NO_PALETTE} for {@code null}. Valid until {@link #end()}.
     */
    public int palette(int[] argb) {
        if (argb == null) return NO_PALETTE;
        int row = palettes.row(argb);
        if (row < 0) {
            // every row taken: draw what is queued, and start the rows over
            flush();
            palettes.reset();
            row = palettes.row(argb);
        }
        return row;
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
                -NO_CLIP, -NO_CLIP, NO_CLIP, NO_CLIP, r, g, b, a, NO_PALETTE);
    }

    private void quad(Texture tex,
                      float x0, float y0, float z0, float x1, float y1, float z1,
                      float x2, float y2, float z2, float x3, float y3, float z3,
                      float u0, float v0, float u1, float v1,
                      float cu0, float cv0, float cu1, float cv1,
                      float r, float g, float b, float a, int palette) {
        if (!drawing) throw new IllegalStateException("batch not begun");
        if (tex != texture || quads == MAX_QUADS) {
            flush();
            texture = tex;
        }
        float pr = r * a, pg = g * a, pb = b * a;
        float pal = palette + 1;           // 0 in the buffer is "no palette"
        put(x0, y0, z0, u0, v0, pr, pg, pb, a, cu0, cv0, cu1, cv1, pal);
        put(x1, y1, z1, u1, v0, pr, pg, pb, a, cu0, cv0, cu1, cv1, pal);
        put(x2, y2, z2, u1, v1, pr, pg, pb, a, cu0, cv0, cu1, cv1, pal);
        put(x3, y3, z3, u0, v1, pr, pg, pb, a, cu0, cv0, cu1, cv1, pal);
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
        quadClipped(tex, topLeft, right, down, u0, v0, u1, v1, cu0, cv0, cu1, cv1, r, g, b, a, NO_PALETTE);
    }

    /** {@link #quadClipped}, an index texture coloured from {@code palette} ({@link #palette}). */
    public void quadClipped(Texture tex, Vec3 topLeft, Vec3 right, Vec3 down,
                            float u0, float v0, float u1, float v1,
                            float cu0, float cv0, float cu1, float cv1,
                            float r, float g, float b, float a, int palette) {
        Vec3 tr = topLeft.add(right);
        Vec3 br = tr.add(down);
        Vec3 bl = topLeft.add(down);
        quad(tex,
                (float) topLeft.x(), (float) topLeft.y(), (float) topLeft.z(),
                (float) tr.x(), (float) tr.y(), (float) tr.z(),
                (float) br.x(), (float) br.y(), (float) br.z(),
                (float) bl.x(), (float) bl.y(), (float) bl.z(),
                u0, v0, u1, v1, cu0, cv0, cu1, cv1, r, g, b, a, palette);
    }

    /** An axis-aligned rectangle on the UI plane (y down). */
    public void rect(Texture tex, float x, float y, float w, float h,
                     float u0, float v0, float u1, float v1,
                     float r, float g, float b, float a) {
        rect(tex, x, y, w, h, u0, v0, u1, v1, r, g, b, a, NO_PALETTE);
    }

    /** {@link #rect}, an index texture coloured from {@code palette} ({@link #palette}). */
    public void rect(Texture tex, float x, float y, float w, float h,
                     float u0, float v0, float u1, float v1,
                     float r, float g, float b, float a, int palette) {
        quad(tex, x, y, 0, x + w, y, 0, x + w, y + h, 0, x, y + h, 0,
                u0, v0, u1, v1, -NO_CLIP, -NO_CLIP, NO_CLIP, NO_CLIP, r, g, b, a, palette);
    }

    private void put(float x, float y, float z, float u, float v,
                     float r, float g, float b, float a,
                     float cu0, float cv0, float cu1, float cv1, float palette) {
        data.put(x).put(y).put(z).put(u).put(v).put(r).put(g).put(b).put(a)
                .put(cu0).put(cv0).put(cu1).put(cv1).put(palette);
    }

    public void flush() {
        if (quads == 0 || texture == null) {
            quads = 0;
            data.clear();
            return;
        }
        data.flip();
        palettes.sync();
        palettes.bind(1);
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
        palettes.close();
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
        shader.close();
    }
}
