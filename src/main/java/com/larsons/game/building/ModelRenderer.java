package com.larsons.game.building;

import com.larsons.game.gfx.SceneLight;
import com.larsons.game.gfx.Shader;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.PixelPalette;
import com.larsons.game.world.OrbitCamera;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws buildings and furniture as 3D models ({@link BuildingModel}),
 * coloured as their views are: each face in its part's {@link
 * BuildingLook#ramp}, banded light, middle or shadow by how it faces the
 * light (from the eye's upper left, as the views were lit), its part's
 * pattern laid over it a texel at a time on the model's own grid, and a
 * line of the part's darkest colour along every edge where a face turns a
 * corner or meets another part - pixel art, in the round. The windows glow
 * at night, a fire always; a mill's sails and its wheel turn.
 *
 * <p>Its walls stand, or come down to a stub ({@link Walls}): cut away
 * when it stands between the eye and her (the house she walks behind never
 * hides her), or all down. Furniture always stands. The editor's
 * selection is ringed round ({@link Outline}).
 */
final class ModelRenderer implements AutoCloseable {

    private static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec2 aTex;
            layout(location = 3) in vec3 aCorner;
            layout(location = 4) in vec3 aHeight;
            layout(location = 5) in vec2 aPart;
            uniform mat4 uViewProj;
            uniform vec3 uAt, uAx, uAy;         // where it stands, its own x and y in the world
            uniform vec3 uPivot, uAxis;         // what the turning part turns about (its own metres)
            uniform float uTurn;                // how far it has turned (radians; 0: the fixed parts)
            uniform vec2 uShift;                // nudged on the screen (an outline)
            uniform vec3 uScale;                // made wider, deeper, taller than it comes (1, 1, 1: as it comes)
            out vec3 vPos;
            flat out vec3 vNormal;
            out vec2 vTex;
            out vec3 vEdge;
            flat out vec2 vPart;
            out float vW;
            vec3 turned(vec3 p) {
                float c = cos(uTurn), s = sin(uTurn);
                return p * c + cross(uAxis, p) * s + uAxis * dot(uAxis, p) * (1.0 - c);
            }
            void main() {
                vec3 p = aPos, n = aNormal;
                if (uTurn != 0.0) {
                    p = uPivot + turned(p - uPivot);
                    n = turned(n);
                }
                p *= uScale;
                n /= uScale;
                vec3 w = uAt + uAx * p.x + uAy * p.y + vec3(0.0, p.z, 0.0);
                vNormal = normalize(uAx * n.x + uAy * n.y + vec3(0.0, n.z, 0.0));
                gl_Position = uViewProj * vec4(w, 1.0);
                """ + Outline.SHIFT + """
                vPos = w;
                vTex = aTex;
                vEdge = aCorner * aHeight;      // metres from each of its triangle's edges
                vPart = aPart;
                vW = gl_Position.w;
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec3 vPos;
            flat in vec3 vNormal;
            in vec2 vTex;
            in vec3 vEdge;
            flat in vec2 vPart;
            in float vW;
            uniform sampler2DArray uPatterns;
            uniform float uTexel;
            uniform vec3 uLight;               // towards the light, in the world
            uniform vec3 uRamp[90];
            uniform int uLayer[15];
            uniform float uGlow[15];
            uniform vec3 uFogColor;
            uniform vec2 uFog;
            uniform float uClip;               // walls down: nothing above this height (world y) is drawn
            uniform vec4 uSolid;               // one colour (an outline), or 0: as it is
            out vec4 fragColor;
            """ + SceneLight.GLSL + """
            void main() {
                if (vPos.y > uClip) discard;
                int k = int(vPart.x + 0.5);
                int mask = int(vPart.y + 0.5);
                // the line along its face's edges, a texel wide, on the texels' grid (near); a pixel or so (far)
                float px = max(length(dFdx(vPos)), length(dFdy(vPos)));
                float lineW = max(uTexel, px * 1.1);
                vec2 dt = (floor(vTex / uTexel) + 0.5) * uTexel - vTex;
                mat2 J = mat2(dFdx(vTex), dFdy(vTex));
                bool snap = px < uTexel && abs(determinant(J)) > 1e-14;
                mat2 Ji = snap ? inverse(J) : mat2(1.0);
                float edge = 1e9;
                for (int i = 0; i < 3; i++) {
                    if (((mask >> i) & 1) == 0) continue;
                    float e = vEdge[i];
                    if (snap) e += dot(vec2(dFdx(vEdge[i]), dFdy(vEdge[i])) * Ji, dt);
                    edge = min(edge, e);
                }
                ivec2 t = ivec2(floor(vTex.x / uTexel), floor(-vTex.y / uTexel));
                int tone = int(texelFetch(uPatterns, ivec3(t.x & 63, t.y & 63, uLayer[k]), 0).r * 3.0 + 0.5);
                float lit = dot(vNormal, uLight);
                int base = lit > 0.6 ? 4 : lit > 0.2 ? 3 : 2;
                bool line = edge < lineW;
                int idx = line ? 0 : clamp(base + tone - 2, 1, 5);
                vec3 c = uRamp[k * 6 + idx];
                vec3 col = c * sceneLight(vPos);
                float glow = min(uGlow[k], 1.0);
                if (uGlow[k] > 1.5) {
                    col = max(col, c * 1.12);
                    glow = 0.0;
                } else if (glow > 0.0 && !line) {
                    vec3 warm = vec3(1.0, 0.8, 0.46) * (0.75 + 0.25 * float(idx) / 5.0);
                    col = mix(col, warm, glow);
                }
                col = mix(col, uMistColour, sceneMist(vPos) * (1.0 - glow * 0.7));
                if (uFog.y > 0.0) {
                    float f = clamp((vW - uFog.x) / (uFog.y - uFog.x), 0.0, 1.0);
                    col = mix(col, uFogColor, f * f);
                }
                fragColor = uSolid.a > 0.0 ? vec4(uSolid.rgb, 1.0) : vec4(col, 1.0);
            }
            """;

    /** A model on the GPU: its corners, the fixed parts' first. */
    private record Gpu(BuildingModel model, int vao, int vbo) {}

    private static final Gpu NONE = new Gpu(null, 0, 0);

    /** How high a building's walls stand cut down, metres over its foot. */
    static final double STUB = 0.6;
    /** Buildings no taller than this (a well, a cart) always stand, cut away or not. */
    static final double LOW = 2.2;

    private final BuildingCatalog catalog;
    private final PixelPalette palette;
    private final Shader shader;
    private final int patterns;
    private final Map<String, Gpu> models = new HashMap<>();
    private final float[] ramp = new float[BuildingRenderer.MAX_PARTS * 6 * 3];
    private final int[] layers = new int[BuildingRenderer.MAX_PARTS];
    private final float[] glow = new float[BuildingRenderer.MAX_PARTS];
    Walls walls = Walls.UP;

    ModelRenderer(BuildingCatalog catalog, PixelPalette palette, int patterns) {
        this.catalog = catalog;
        this.palette = palette;
        this.patterns = patterns;
        this.shader = new Shader("models", VERTEX, FRAGMENT);
    }

    /** Whether {@code p} is drawn as a model (it has one). Reads it the first time. */
    boolean has(BuildingProfile p) {
        return gpu(p.key()) != NONE;
    }

    private Gpu gpu(String key) {
        Gpu g = models.get(key);
        if (g != null) return g;
        g = NONE;
        try {
            BuildingModel m = BuildingModel.load(catalog.root().resolve(key));
            if (m != null && m.vertices().length > 0) g = upload(m);
        } catch (IOException | RuntimeException e) {
            System.err.println("[buildings] " + key + " model: " + e.getMessage());
        }
        models.put(key, g);
        return g;
    }

    private static Gpu upload(BuildingModel m) {
        int vao = glGenVertexArrays(), vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(m.vertices().length);
        try {
            buf.put(m.vertices()).flip();
            glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(buf);
        }
        int stride = BuildingModel.FLOATS * Float.BYTES;
        int[] sizes = {3, 3, 2, 3, 3, 2};
        long at = 0;
        for (int i = 0; i < sizes.length; i++) {
            glVertexAttribPointer(i, sizes[i], GL_FLOAT, false, stride, at * Float.BYTES);
            glEnableVertexAttribArray(i);
            at += sizes[i];
        }
        glBindVertexArray(0);
        return new Gpu(m, vao, vbo);
    }

    /**
     * Draw those of {@code buildings} that have a model (not rooms' pieces),
     * opaque, writing depth. Returns how many it drew.
     */
    int draw(List<Building> buildings, OrbitCamera camera, Vec3 focus, Mat4 viewProj, float[] fog, float fogFar,
             SceneLight light, double night, double time, Set<Integer> highlight) {
        boolean any = false;
        for (Building b : buildings) any |= b.profile().room() == null && has(b.profile());
        if (!any) return 0;
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        shader.use();
        shader.set("uViewProj", viewProj);
        shader.set("uPatterns", 1);
        shader.set("uFogColor", fog[0], fog[1], fog[2]);
        shader.set("uFog", fogFar * 0.35f, fogFar);
        // the light as the views were lit: from the eye's upper left, and in front of it
        Vec3 r = camera.right(), u = camera.up(), f = camera.forward();
        double lx = -0.52 * r.x() + 0.62 * u.x() - 0.58 * f.x(), ly = -0.52 * r.y() + 0.62 * u.y() - 0.58 * f.y(),
                lz = -0.52 * r.z() + 0.62 * u.z() - 0.58 * f.z(), ll = Math.max(1e-6, Math.sqrt(lx * lx + ly * ly
                + lz * lz));
        shader.set("uLight", (float) (lx / ll), (float) (ly / ll), (float) (lz / ll));
        Vec3 eye = camera.eye();
        Vec3 her = focus.add(0, 0.9, 0);
        light.bind(shader);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D_ARRAY, patterns);
        double reach = fogFar + 30;
        float n = (float) Math.max(0, Math.min(1, night));
        int drawn = 0;
        List<Building> picked = new ArrayList<>();
        for (Building b : buildings) {
            BuildingProfile p = b.profile();
            if (p.room() != null) continue;
            Gpu g = gpu(p.key());
            if (g == NONE) continue;
            Vec3 at = b.position();
            if (at.sub(eye).horizontalLength() > reach + p.reach()) continue;
            shader.set("uClip", (float) clip(b, eye, her));
            drawOne(b, g, n, time);
            if (highlight != null && highlight.contains(b.id())) picked.add(b);
            drawn++;
        }
        Outline.draw(shader, picked, b -> {
            shader.set("uClip", (float) clip(b, eye, her));
            drawOne(b, gpu(b.profile().key()), n, time);
        });
        glBindVertexArray(0);
        return drawn;
    }

    /** {@code b} drawn, its look and place set first. */
    private void drawOne(Building b, Gpu g, float night, double time) {
        BuildingProfile p = b.profile();
        Vec3 at = b.position();
        setLook(b, night);
        Vec3 ax = b.axisX(), ay = b.axisY();
        shader.set("uAt", (float) at.x(), (float) at.y(), (float) at.z());
        shader.set("uAx", (float) ax.x(), (float) ax.y(), (float) ax.z());
        shader.set("uAy", (float) ay.x(), (float) ay.y(), (float) ay.z());
        shader.set("uTexel", (float) p.texel());
        shader.set("uTurn", 0f);
        double[] sc = p.scale();
        shader.set("uScale", (float) sc[0], (float) sc[1], (float) sc[2]);
        glBindVertexArray(g.vao());
        BuildingModel m = g.model();
        if (m.fixedCorners() > 0) glDrawArrays(GL_TRIANGLES, 0, m.fixedCorners());
        if (m.turningCorners() > 0 && m.pivot() != null) {
            BuildingProfile.Anim anim = p.anim();
            double speed = anim == null ? 30 : anim.speed();
            double[] pv = m.pivot();
            shader.set("uPivot", (float) pv[0], (float) pv[1], (float) pv[2]);
            shader.set("uAxis", m.axis() == 'x' ? 1f : 0f, m.axis() == 'y' ? 1f : 0f, m.axis() == 'z' ? 1f : 0f);
            // (a whole turn every 360 / speed seconds; never 0, which the shader reads as fixed)
            double turn = Math.toRadians((time * speed) % 360.0);
            shader.set("uTurn", (float) (turn == 0 ? 1e-6 : turn));
            glDrawArrays(GL_TRIANGLES, m.fixedCorners(), m.turningCorners());
        }
    }

    /**
     * The height above which {@code b} is not drawn: none (it stands), or a
     * stub over its foot - all down, or cut away as it stands between {@code
     * eye} and {@code her}. Furniture, and what is low, always stands.
     */
    double clip(Building b, Vec3 eye, Vec3 her) {
        return clip(b, eye, her, walls);
    }

    static double clip(Building b, Vec3 eye, Vec3 her, Walls walls) {
        BuildingProfile p = b.profile();
        if (walls == Walls.UP || p.furniture() || p.height() <= LOW) return 1e9;
        double stub = b.position().y() + STUB;
        if (walls == Walls.DOWN) return stub;
        return hides(b, eye, her) ? stub : 1e9;
    }

    /** Whether {@code b} stands between {@code eye} and {@code her} - or she is in it, or right behind it. */
    static boolean hides(Building b, Vec3 eye, Vec3 her) {
        if (b.blocks(her.x(), her.z(), 0.6)) return true;
        double hit = b.rayHit(eye, her.sub(eye));
        return hit >= 0 && hit < 0.98;
    }

    private void setLook(Building b, float night) {
        List<BuildingProfile.Part> parts = b.profile().parts();
        int[][] ramps = b.ramps(palette);
        for (int i = 0; i < BuildingRenderer.MAX_PARTS; i++) {
            int[] rr = i < ramps.length ? ramps[i] : ramps[ramps.length - 1];
            for (int k = 0; k < 6; k++) {
                int c = rr[k], o = (i * 6 + k) * 3;
                ramp[o] = ((c >> 16) & 255) / 255f;
                ramp[o + 1] = ((c >> 8) & 255) / 255f;
                ramp[o + 2] = (c & 255) / 255f;
            }
            BuildingProfile.Part part = i < parts.size() ? parts.get(i) : null;
            layers[i] = part == null ? 0 : catalog.textureIndex(b.look().texture(part));
            glow[i] = part == null ? 0f : part.always() ? 2f : part.glow() ? night : 0f;
        }
        shader.set3v("uRamp", ramp);
        shader.set1iv("uLayer", layers);
        shader.set1fv("uGlow", glow);
    }

    @Override
    public void close() {
        for (Gpu g : models.values()) {
            if (g == NONE) continue;
            glDeleteBuffers(g.vbo());
            glDeleteVertexArrays(g.vao());
        }
        models.clear();
        shader.close();
    }
}
