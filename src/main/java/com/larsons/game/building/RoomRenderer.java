package com.larsons.game.building;

import com.larsons.game.gfx.SceneLight;
import com.larsons.game.gfx.Shader;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.PixelPalette;
import com.larsons.game.world.OrbitCamera;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws the rooms inside a building - their walls, floors, stairs and the
 * door out ({@link Room}) - box by box, as the buildings' pixels are
 * coloured: each face of a box in its part's {@link BuildingLook#ramp}, its
 * pattern laid over it a texel at a time, its tops light, the sides turned
 * from the light in shadow, a line of the part's darkest colour round each
 * face. A wall between the eye and her is drawn cut down ({@link Room#cut}).
 */
final class RoomRenderer implements AutoCloseable {

    /** Floats a vertex: its point, its face's normal, where on its face it is and the face's size, where on the pattern, its part, how dark. */
    private static final int FLOATS = 3 + 3 + 4 + 2 + 2;

    private static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec4 aFace;
            layout(location = 3) in vec2 aTex;
            layout(location = 4) in vec2 aPart;
            uniform mat4 uViewProj;
            out vec3 vPos;
            flat out vec3 vNormal;
            out vec4 vFace;
            out vec2 vTex;
            flat out vec2 vPart;
            out float vW;
            uniform vec2 uShift;           // nudged on the screen (an outline)
            void main() {
                gl_Position = uViewProj * vec4(aPos, 1.0);
                vPos = aPos;
                vNormal = aNormal;
                vFace = aFace;
                vTex = aTex;
                vPart = aPart;
                vW = gl_Position.w;
                """ + Outline.SHIFT + """
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec3 vPos;
            flat in vec3 vNormal;
            in vec4 vFace;
            in vec2 vTex;
            flat in vec2 vPart;
            in float vW;
            uniform sampler2DArray uPatterns;
            uniform float uTexel;
            uniform vec3 uLightDir;        // the light, along the ground
            uniform vec3 uRamp[90];
            uniform int uLayer[15];
            uniform float uDay;
            uniform int uGlass;            // the part that is glass, or -1
            uniform vec3 uFogColor;
            uniform vec2 uFog;
            uniform vec4 uSolid;           // one colour (an outline), or 0: as it is
            out vec4 fragColor;
            """ + SceneLight.GLSL + """
            void main() {
                int k = int(vPart.x + 0.5);
                float dark = vPart.y;
                ivec2 t = ivec2(floor(vTex / uTexel));
                int tone = int(texelFetch(uPatterns, ivec3(t.x & 63, t.y & 63, uLayer[k]), 0).r * 3.0 + 0.5);
                int shade;
                if (vNormal.y > 0.5) shade = 5;
                else shade = dot(vNormal, uLightDir) > 0.25 ? 4 : 3;
                // a line round each face, a texel wide, on the texels' grid
                vec2 q = (floor(vFace.xy / uTexel) + 0.5) * uTexel;
                float edge = min(min(q.x, vFace.z - q.x), min(q.y, vFace.w - q.y));
                int idx = edge < uTexel ? 0 : clamp(shade + tone - 2, 1, 5);
                if (k == uGlass) idx = edge < uTexel ? 0 : (uDay > 0.5 ? 5 : 2);   // glass: bright by day
                if (dark > 0.0) idx = max(edge < uTexel ? 0 : 1, idx - int(min(dark, 3.0)));
                vec3 c = uRamp[k * 6 + idx];
                if (dark > 0.0) c *= max(0.08, 1.0 - 0.15 * dark);
                vec3 lit = c * sceneLight(vPos);
                lit = mix(lit, uMistColour, sceneMist(vPos));
                if (uFog.y > 0.0) {
                    float f = clamp((vW - uFog.x) / (uFog.y - uFog.x), 0.0, 1.0);
                    lit = mix(lit, uFogColor, f * f);
                }
                fragColor = uSolid.a > 0.0 ? vec4(uSolid.rgb, 1.0) : vec4(lit, 1.0);
            }
            """;

    /** A piece's vertices in the world, whole and cut down, worked out once. */
    private record Mesh(float[] whole, float[] cut) {}

    private final BuildingCatalog catalog;
    private final PixelPalette palette;
    private final Shader shader;
    private final int vao, vbo, patterns;
    private int capacity = 6 * 6 * 64 * FLOATS;
    private FloatBuffer buffer = MemoryUtil.memAllocFloat(capacity);
    private Map<Building, Mesh> meshes = new IdentityHashMap<>();
    private final float[] ramp = new float[BuildingRenderer.MAX_PARTS * 6 * 3];
    private final int[] layers = new int[BuildingRenderer.MAX_PARTS];

    /** Drawn with {@code patterns}, the buildings' pattern array. Must be made on the GL thread. */
    RoomRenderer(BuildingCatalog catalog, PixelPalette palette, int patterns) {
        this.catalog = catalog;
        this.palette = palette;
        this.patterns = patterns;
        this.shader = new Shader("rooms", VERTEX, FRAGMENT);
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) capacity * Float.BYTES, GL_DYNAMIC_DRAW);
        int stride = FLOATS * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glVertexAttribPointer(3, 2, GL_FLOAT, false, stride, 10L * Float.BYTES);
        glVertexAttribPointer(4, 2, GL_FLOAT, false, stride, 12L * Float.BYTES);
        for (int i = 0; i < 5; i++) glEnableVertexAttribArray(i);
        glBindVertexArray(0);
    }

    /**
     * Draw the room pieces among {@code buildings} seen by {@code camera} -
     * those between the eye and {@code focus} cut down - opaque, writing
     * depth. {@code day} 0 – 1 lights the windows' glass.
     */
    /** How the walls stand: whole, cut down between the eye and her, or all down. */
    Walls walls = Walls.CUTAWAY;

    void draw(List<Building> buildings, OrbitCamera camera, Vec3 focus, Mat4 viewProj, float[] fog, float fogFar,
              SceneLight light, double day, Set<Integer> highlight) {
        boolean any = false;
        for (Building b : buildings) any |= b.profile().room() != null;
        if (!any) {
            meshes.clear();
            return;
        }
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        shader.use();
        shader.set("uViewProj", viewProj);
        shader.set("uPatterns", 1);
        shader.set("uTexel", (float) Room.TEXEL);
        shader.set("uDay", (float) day);
        shader.set("uFogColor", fog[0], fog[1], fog[2]);
        shader.set("uFog", fogFar * 0.35f, fogFar);
        // the light from the eye's left and behind it, as the buildings' views are lit
        Vec3 r = camera.right(), f = camera.forward();
        double lx = -r.x() - 0.5 * f.x(), lz = -r.z() - 0.5 * f.z(), ll = Math.max(1e-6, Math.hypot(lx, lz));
        shader.set("uLightDir", (float) (lx / ll), 0f, (float) (lz / ll));
        light.bind(shader);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D_ARRAY, patterns);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        Vec3 eye = camera.eye();
        Map<Building, Mesh> seen = new IdentityHashMap<>();
        List<Building> picked = new ArrayList<>();
        for (Building b : buildings) {
            Room.Spec spec = b.profile().room();
            if (spec == null) continue;
            Mesh m = meshes.get(b);
            if (m == null) m = new Mesh(mesh(b, false), Room.cuts(spec) ? mesh(b, true) : null);
            seen.put(b, m);
            float[] v = vertices(b, m, eye, focus);
            if (v.length == 0) continue;
            setLook(b);
            upload(v);
            glDrawArrays(GL_TRIANGLES, 0, v.length / FLOATS);
            if (highlight != null && highlight.contains(b.id())) picked.add(b);
        }
        meshes = seen;
        Outline.draw(shader, picked, b -> {
            float[] v = vertices(b, seen.get(b), eye, focus);
            setLook(b);
            upload(v);
            glDrawArrays(GL_TRIANGLES, 0, v.length / FLOATS);
        });
        glBindVertexArray(0);
    }

    /** {@code b}'s vertices as it is drawn now: whole, or cut down (all down, or cut away between eye and her). */
    private float[] vertices(Building b, Mesh m, Vec3 eye, Vec3 focus) {
        boolean down = m.cut() != null && switch (walls) {
            case UP -> false;
            case CUTAWAY -> Room.cut(b, eye, focus);
            case DOWN -> true;
        };
        return down ? m.cut() : m.whole();
    }

    private void upload(float[] v) {
        if (v.length > capacity) {
            capacity = Integer.highestOneBit(v.length) * 2;
            MemoryUtil.memFree(buffer);
            buffer = MemoryUtil.memAllocFloat(capacity);
            glBufferData(GL_ARRAY_BUFFER, (long) capacity * Float.BYTES, GL_DYNAMIC_DRAW);
        }
        buffer.clear();
        buffer.put(v).flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
    }

    private void setLook(Building b) {
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
        }
        Room.Spec spec = b.profile().room();
        shader.set("uGlass", spec.piece() == Room.Piece.WALL && spec.opening() == Room.Opening.WINDOW ? 3 : -1);
        shader.set3v("uRamp", ramp);
        shader.set1iv("uLayer", layers);
    }

    /** {@code b}'s boxes as triangles in the world (no bottoms: nothing is seen from under the floor). */
    static float[] mesh(Building b, boolean cut) {
        List<Room.Box> boxes = Room.boxes(b.profile().room(), cut);
        float[] out = new float[boxes.size() * 5 * 6 * FLOATS];
        int[] n = {0};
        Vec3 ax = b.axisX(), ay = b.axisY(), at = b.position();
        for (Room.Box x : boxes) {
            double w = x.x1() - x.x0(), d = x.y1() - x.y0(), h = x.z1() - x.z0();
            // front (-y) and back (+y): across x, up z
            face(out, n, b, at, x.x0(), x.y0(), x.z0(), 1, 0, 0, 0, 0, 1, w, h, ay.scale(-1), x.front(), x.dark(), true);
            face(out, n, b, at, x.x1(), x.y1(), x.z0(), -1, 0, 0, 0, 0, 1, w, h, ay, x.back(), x.dark(), true);
            // its ends: along y, up z
            face(out, n, b, at, x.x0(), x.y1(), x.z0(), 0, -1, 0, 0, 0, 1, d, h, ax.scale(-1), x.part(), x.dark(), true);
            face(out, n, b, at, x.x1(), x.y0(), x.z0(), 0, 1, 0, 0, 0, 1, d, h, ax, x.part(), x.dark(), true);
            // its top: across x, back along y
            face(out, n, b, at, x.x0(), x.y0(), x.z1(), 1, 0, 0, 0, 1, 0, w, d, new Vec3(0, 1, 0), x.part(), x.dark(),
                    false);
        }
        return java.util.Arrays.copyOf(out, n[0]);
    }

    /**
     * One face: from its corner {@code (x, y, z)} (its own metres), {@code
     * sw} along {@code (ux, uy, uz)} and {@code th} along {@code (vx, vy,
     * vz)}; facing {@code normal} in the world.
     */
    private static void face(float[] out, int[] n, Building b, Vec3 at, double x, double y, double z,
                             double ux, double uy, double uz, double vx, double vy, double vz, double sw, double th,
                             Vec3 normal, int part, int dark, boolean upright) {
        if (sw < 1e-4 || th < 1e-4) return;
        double[][] corners = {{0, 0}, {sw, 0}, {sw, th}, {0, 0}, {sw, th}, {0, th}};
        for (double[] c : corners) {
            double mx = x + ux * c[0] + vx * c[1], my = y + uy * c[0] + vy * c[1], mz = z + uz * c[0] + vz * c[1];
            Vec3 p = b.toWorld(mx, my).add(0, mz, 0);
            int o = n[0];
            out[o] = (float) p.x();
            out[o + 1] = (float) p.y();
            out[o + 2] = (float) p.z();
            out[o + 3] = (float) normal.x();
            out[o + 4] = (float) normal.y();
            out[o + 5] = (float) normal.z();
            out[o + 6] = (float) c[0];
            out[o + 7] = (float) c[1];
            out[o + 8] = (float) sw;
            out[o + 9] = (float) th;
            // the pattern: an upright face's across its own length, down from its top; a top's across and back
            if (upright) {
                out[o + 10] = (float) (Math.abs(ux) > 0.5 ? mx : my);
                out[o + 11] = (float) (8 - mz);
            } else {
                out[o + 10] = (float) mx;
                out[o + 11] = (float) my;
            }
            out[o + 12] = part;
            out[o + 13] = dark;
            n[0] += FLOATS;
        }
    }

    @Override
    public void close() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        shader.close();
        MemoryUtil.memFree(buffer);
    }
}
