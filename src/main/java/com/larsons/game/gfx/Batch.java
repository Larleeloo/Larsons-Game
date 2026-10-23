package com.larsons.game.gfx;

import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Textured, tinted quads, batched into as few draw calls as the textures
 * allow — used for everything flat in the game: the billboarded character
 * layers and items in the 3D world, the scenery's crossed cards, the ground
 * shadows, and the whole UI.
 *
 * <p>Quads are appended to a CPU buffer and drawn in one draw call per run of
 * same-texture quads. Colours are straight alpha on the way in and
 * premultiplied here, to match the premultiplied textures (see
 * {@link PixelData}).
 *
 * <p><b>Streaming.</b> The runs are only recorded as they are added; the
 * whole buffer goes to the GPU in <em>one</em> upload when the batch ends (or
 * fills), and then every run is drawn from it. The upload lands in a part of
 * a ring buffer no queued draw is reading, mapped unsynchronized, and the
 * buffer is orphaned (given fresh storage) only when the ring wraps - so the
 * CPU never waits for the GPU to finish with last frame's vertices, which a
 * rewrite of the same bytes for every draw call made it do on many drivers.
 *
 * <p>A quad can name a palette ({@link #palette}): its texture then holds
 * palette indices ({@link Texture#indexed()}) and the shader colours each
 * texel from that palette's row of the {@link PaletteAtlas} - the pixel-art
 * layers, which so take any colours without being uploaded again. Quads of
 * different palettes on the same texture still share a draw call.
 *
 * <p>A quad can also be <em>light</em> ({@link #glow}): its colour goes in
 * premultiplied with no alpha, so the blend the batch is drawn with
 * ({@code ONE, ONE_MINUS_SRC_ALPHA}) adds it to what is behind instead of
 * covering it - the glow of a halo, a beam of light - in the same draw calls
 * as everything else.
 *
 * <p><b>Upright depth.</b> A billboard faces the camera, so from above it
 * leans back with the view, and its depth says the character's head is
 * behind her feet: a tree's card half a metre behind her would cut her off
 * at the shoulders. Within a {@link #setDepthPlane depth plane}, the quads
 * of each {@link #depthAnchor anchored} billboard are drawn where they are
 * but tested at the depth of an upright figure standing on the anchor - the
 * point where each pixel's view ray meets a plane through the anchor that
 * stands up from the ground and faces the camera - so whatever stands
 * behind her feet is behind all of her, and whatever stands in front of
 * them in front.
 */
public final class Batch implements AutoCloseable {

    private static final int FLOATS_PER_VERTEX = 15;      // xyz uv rgba clip palette plane
    private static final int QUAD_BYTES = 4 * FLOATS_PER_VERTEX * Float.BYTES;
    /** Quads one upload holds (the CPU buffer, and the shared index buffer). */
    private static final int MAX_QUADS = 8192;
    /** Quads the GPU's vertex buffer holds before it is orphaned and filled from the start again. */
    private static final int RING_QUADS = MAX_QUADS * 4;
    /** A clip rectangle nothing falls outside of: the whole quad is drawn. */
    private static final float NO_CLIP = 1e9f;
    /**
     * The alpha at or below which a fragment is discarded, as every batch
     * begins: only what is all but invisible, so soft edges still blend.
     */
    public static final float DEFAULT_ALPHA_CUT = 0.003f;

    private static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in vec4 aColor;
            layout(location = 3) in vec4 aClip;
            layout(location = 4) in float aPalette;
            layout(location = 5) in float aPlane;     // the depth plane's distance from uEye along uPlaneNormal; 0: none
            uniform mat4 uProj;
            uniform vec3 uEye;
            uniform vec3 uPlaneNormal;
            out vec2 vUv;
            out vec4 vColor;
            out float vDepth;
            flat out vec4 vClip;
            flat out int vPalette;
            void main() {
                vec4 clip = uProj * vec4(aPos, 1.0);
                float along = dot(aPos - uEye, uPlaneNormal);
                if (aPlane > 0.0 && along > 1e-4) {
                    // The same pixel, at the depth of the point along its view ray on the plane.
                    float s = clamp(aPlane / along, 0.02, 50.0);
                    vec4 upright = uProj * vec4(uEye + (aPos - uEye) * s, 1.0);
                    clip.z = clamp(upright.z / upright.w, -1.0, 1.0) * clip.w;
                }
                gl_Position = clip;
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
                // (light - a glow quad - has colour but no alpha: it is kept, and adds)
                if (max(c.a, max(c.r, max(c.g, c.b))) <= uAlphaCut) discard;
                // A cutout (a cut of a half or more) is opaque wherever it is drawn: a texel its
                // mipmap left partly covered keeps its own colour rather than a darkened one.
                if (uAlphaCut >= 0.5) {
                    if (c.a <= uAlphaCut) discard;
                    c = vec4(c.rgb / c.a, 1.0);
                }
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
    /** The runs of same-texture quads waiting in {@link #data}: run {@code i} starts at quad {@code runFirst[i]}. */
    private final Texture[] runTexture = new Texture[MAX_QUADS];
    private final int[] runFirst = new int[MAX_QUADS];
    private int runs;
    /** Where the next upload goes in the GPU's vertex buffer, in quads. */
    private int ringQuads;
    private boolean drawing;
    private int drawCalls;
    /** The depth plane's eye and normal ({@link #setDepthPlane}); null: none this batch. */
    private Vec3 planeEye, planeNormal;
    /** The plane the quads queued now are tested at ({@link #depthAnchor}); 0: their own depth. */
    private float plane;

    public Batch() {
        shader = new Shader("batch", VERTEX, FRAGMENT);
        data = MemoryUtil.memAllocFloat(MAX_QUADS * 4 * FLOATS_PER_VERTEX);

        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) RING_QUADS * QUAD_BYTES, GL_STREAM_DRAW);
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
        glVertexAttribPointer(5, 1, GL_FLOAT, false, stride, 14L * Float.BYTES);
        glEnableVertexAttribArray(5);

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
        shader.set("uAlphaCut", DEFAULT_ALPHA_CUT);
        shader.set("uEye", 0f, 0f, 0f);
        shader.set("uPlaneNormal", 0f, 0f, 0f);
        planeEye = planeNormal = null;
        plane = 0;
        palettes.newBatch();
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
     * Discard fragments whose alpha is at or below {@code cut} from here to
     * the end of this batch (or the next call). A cut of a half or more draws
     * a <b>cutout</b>: every texel is in or out, nothing half-blended, and
     * what is in is drawn opaque (its colour divided by its alpha, so the
     * edge of a mipmapped cutout is not darkened) - which can write depth
     * like solid geometry, as the scenery does ({@code world/SceneryRenderer}).
     * What is already queued is drawn first, with the cut it was queued
     * under. {@link #begin} starts every batch at {@link #DEFAULT_ALPHA_CUT};
     * pass that to restore it mid-batch.
     */
    public void setAlphaCut(float cut) {
        if (!drawing) throw new IllegalStateException("batch not begun");
        flush();
        shader.use();
        shader.set("uAlphaCut", cut);
    }

    /**
     * Test the billboards of this batch at an upright figure's depth (see the
     * class notes): the camera's {@code eye}, and the unit {@code normal} the
     * figures' depth planes face away from it with - the camera's forward
     * along the ground, for an upright plane. Each billboard then names its
     * anchor ({@link #depthAnchor}). What is already queued is drawn first.
     */
    public void setDepthPlane(Vec3 eye, Vec3 normal) {
        if (!drawing) throw new IllegalStateException("batch not begun");
        flush();
        planeEye = eye;
        planeNormal = normal;
        shader.use();
        shader.set("uEye", (float) eye.x(), (float) eye.y(), (float) eye.z());
        shader.set("uPlaneNormal", (float) normal.x(), (float) normal.y(), (float) normal.z());
    }

    /**
     * The quads queued from here on are one billboard standing on {@code
     * anchor} (its feet, or the spot on the ground under it): tested at the
     * depth of the {@link #setDepthPlane depth plane} through it. {@code
     * null}, or no depth plane this batch: at their own depth.
     */
    public void depthAnchor(Vec3 anchor) {
        if (anchor == null || planeEye == null) {
            plane = 0;
            return;
        }
        double d = anchor.sub(planeEye).dot(planeNormal);
        plane = d > 1e-3 ? (float) d : 0;            // (a plane through the eye or behind it: no plane at all)
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
        emit(tex, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, u0, v0, u1, v1, cu0, cv0, cu1, cv1,
                r * a, g * a, b * a, a, palette);
    }

    /** One quad, its colour already premultiplied ({@code a} 0: light, added to what is behind). */
    private void emit(Texture tex,
                      float x0, float y0, float z0, float x1, float y1, float z1,
                      float x2, float y2, float z2, float x3, float y3, float z3,
                      float u0, float v0, float u1, float v1,
                      float cu0, float cv0, float cu1, float cv1,
                      float pr, float pg, float pb, float a, int palette) {
        if (!drawing) throw new IllegalStateException("batch not begun");
        if (quads == MAX_QUADS) flush();
        if (runs == 0 || runTexture[runs - 1] != tex) {
            runTexture[runs] = tex;
            runFirst[runs] = quads;
            runs++;
        }
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

    /**
     * A quad of light, from its top-left corner and two edge vectors: {@code
     * tex}'s colour times {@code (r, g, b)} times {@code intensity}, added to
     * whatever is behind it (a texture's alpha is how much of it there is -
     * a soft white blob is a soft glow). {@code palette}: an index texture's
     * palette ({@link #palette}), or {@link #NO_PALETTE}.
     */
    public void glow(Texture tex, Vec3 topLeft, Vec3 right, Vec3 down,
                     float u0, float v0, float u1, float v1,
                     float cu0, float cv0, float cu1, float cv1,
                     float r, float g, float b, float intensity, int palette) {
        Vec3 tr = topLeft.add(right);
        Vec3 br = tr.add(down);
        Vec3 bl = topLeft.add(down);
        emit(tex,
                (float) topLeft.x(), (float) topLeft.y(), (float) topLeft.z(),
                (float) tr.x(), (float) tr.y(), (float) tr.z(),
                (float) br.x(), (float) br.y(), (float) br.z(),
                (float) bl.x(), (float) bl.y(), (float) bl.z(),
                u0, v0, u1, v1, cu0, cv0, cu1, cv1, r * intensity, g * intensity, b * intensity, 0, palette);
    }

    /** {@link #glow} of a whole texture, no palette. */
    public void glow(Texture tex, Vec3 topLeft, Vec3 right, Vec3 down,
                     float r, float g, float b, float intensity) {
        glow(tex, topLeft, right, down, 0, 0, 1, 1, -NO_CLIP, -NO_CLIP, NO_CLIP, NO_CLIP, r, g, b, intensity,
                NO_PALETTE);
    }

    /** {@link #glow} on the UI plane (y down): an axis-aligned rectangle of light. */
    public void glowRect(Texture tex, float x, float y, float w, float h,
                         float u0, float v0, float u1, float v1,
                         float r, float g, float b, float intensity) {
        emit(tex, x, y, 0, x + w, y, 0, x + w, y + h, 0, x, y + h, 0,
                u0, v0, u1, v1, -NO_CLIP, -NO_CLIP, NO_CLIP, NO_CLIP,
                r * intensity, g * intensity, b * intensity, 0, NO_PALETTE);
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
                .put(cu0).put(cv0).put(cu1).put(cv1).put(palette).put(plane);
    }

    /** Upload what is queued and draw it: one upload, one draw call per run of the same texture. */
    public void flush() {
        if (quads == 0) {
            clearQueue();
            return;
        }
        data.flip();
        int baseVertex = upload();
        palettes.sync();
        palettes.bind(1);
        for (int r = 0; r < runs; r++) {
            // (a texture released since its quads were queued - a reload mid-frame - is skipped, not drawn deleted)
            if (runTexture[r].closed()) continue;
            int first = runFirst[r];
            int count = (r + 1 < runs ? runFirst[r + 1] : quads) - first;
            runTexture[r].bind(0);
            glDrawElementsBaseVertex(GL_TRIANGLES, count * 6, GL_UNSIGNED_INT, (long) first * 6 * Integer.BYTES,
                    baseVertex);
            drawCalls++;
        }
        clearQueue();
    }

    /**
     * Copy the queued vertices into the next unused stretch of the vertex
     * buffer, orphaning it first if they no longer fit; returns the index of
     * the first vertex written. No draw already queued reads that stretch, so
     * it is mapped without waiting for the GPU.
     */
    private int upload() {
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        if (ringQuads + quads > RING_QUADS) {
            glBufferData(GL_ARRAY_BUFFER, (long) RING_QUADS * QUAD_BYTES, GL_STREAM_DRAW);
            ringQuads = 0;
        }
        long offset = (long) ringQuads * QUAD_BYTES, size = (long) quads * QUAD_BYTES;
        ByteBuffer dst = glMapBufferRange(GL_ARRAY_BUFFER, offset, size,
                GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_RANGE_BIT | GL_MAP_UNSYNCHRONIZED_BIT);
        if (dst != null) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(data), MemoryUtil.memAddress(dst), size);
            glUnmapBuffer(GL_ARRAY_BUFFER);
        } else {
            glBufferSubData(GL_ARRAY_BUFFER, offset, data);
        }
        int base = ringQuads * 4;
        ringQuads += quads;
        return base;
    }

    private void clearQueue() {
        java.util.Arrays.fill(runTexture, 0, runs, null);
        runs = 0;
        quads = 0;
        data.clear();
    }

    public void end() {
        flush();
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
