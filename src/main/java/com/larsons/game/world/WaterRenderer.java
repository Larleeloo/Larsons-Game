package com.larsons.game.world;

import com.larsons.game.gfx.SceneLight;
import com.larsons.game.gfx.Shader;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;

import java.util.Arrays;

import static org.lwjgl.opengl.GL33C.*;

/**
 * The water: a world's ponds, rivers and streams and its sea ({@link
 * WaterMap}), drawn as pixel art over the ground - turquoise in the
 * shallows, darker with depth in steps, ripples drifting the way it flows,
 * the sky in it at a glancing angle, the sun glittering on it, foam where it
 * laps the shore (waves running up the beach on the sea), lily pads on the
 * marsh's water, and the crystal streams shining through violet and mint.
 *
 * <p>A painted cell is a flat quad at its surface; the sea is a grid over the
 * level wherever the ground is lower than it, and - when it is above the
 * flat ground round the level - out to the horizon. Each corner carries how
 * deep the water is there, so the shore's foam and the depth's colour follow
 * the ground's shape, and the part of a cell that is under the bank is not
 * drawn.
 *
 * <p>It is drawn after everything standing in the world, see-through, so
 * what wades in it - and the ground under the shallows - shows through.
 */
public final class WaterRenderer implements AutoCloseable {

    private static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in float aDepth;
            layout(location = 2) in vec2 aFlow;
            layout(location = 3) in float aKind;
            uniform mat4 uViewProj;
            out vec3 vPos;
            out float vDepth;
            out vec2 vFlow;
            flat out int vKind;
            void main() {
                vPos = aPos;
                vDepth = aDepth;
                vFlow = aFlow;
                vKind = int(aKind + 0.5);
                gl_Position = uViewProj * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec3 vPos;
            in float vDepth;
            in vec2 vFlow;
            flat in int vKind;
            uniform vec3 uEye;
            uniform vec3 uHorizon;
            uniform vec3 uSky;
            uniform vec3 uSunDir;
            uniform float uDay;
            uniform float uFogDistance;
            uniform float uTime;
            out vec4 fragColor;
            """ + SceneLight.GLSL + """

            void main() {
                if (vDepth < 0.0) discard;                       // under the bank
                float texel = 1.0 / 16.0;
                vec2 b = (floor(vPos.xz / texel) + 0.5) * texel;     // pixel-art blocks
                float depth = vDepth;
                vec3 shallow, deep;
                if (vKind == 2) { shallow = vec3(0.27, 0.33, 0.15); deep = vec3(0.10, 0.14, 0.08); }
                else if (vKind == 3) { shallow = vec3(0.26, 0.68, 0.72); deep = vec3(0.04, 0.19, 0.42); }
                else if (vKind == 4) { shallow = vec3(0.42, 0.86, 0.96); deep = vec3(0.30, 0.20, 0.80); }
                else { shallow = vec3(0.30, 0.74, 0.76); deep = vec3(0.07, 0.30, 0.52); }
                float d = clamp(depth / 1.6, 0.0, 1.0);
                d = floor(d * 4.0 + 0.5) / 4.0;
                vec3 c = mix(shallow, deep, d);

                // ripples drifting with the flow (and a slow swell where it is still)
                vec2 q = b - vFlow * uTime;
                float r1 = slNoise(q * 2.2 + vec2(uTime * 0.21, uTime * 0.13));
                float r2 = slNoise(q * 4.7 - vec2(uTime * 0.31, -uTime * 0.17) + 7.0);
                float ripple = r1 * 0.6 + r2 * 0.4;
                c = mix(c, mix(c, vec3(1.0), 0.32), step(0.70, ripple));
                c *= 1.0 - 0.14 * step(ripple, 0.27);
                if (vKind == 4) {
                    // the crystal water's sheen, sweeping through violet and mint
                    float h1 = slNoise(b * 0.55 + vec2(uTime * 0.12, -uTime * 0.07));
                    float h2 = slNoise(b * 0.8 - vec2(uTime * 0.09, uTime * 0.11) + 3.0);
                    c = mix(c, vec3(0.93, 0.70, 0.98), step(0.64, h1) * 0.55);
                    c = mix(c, vec3(0.74, 1.00, 0.80), step(0.70, h2) * 0.55);
                }

                // the sky in it, more at a glancing angle
                vec3 v = normalize(uEye - vPos);
                float fres = pow(1.0 - clamp(v.y, 0.0, 1.0), 3.0);
                c = mix(c, uSky, floor(fres * 4.0 + 0.5) / 4.0 * (vKind == 2 ? 0.25 : 0.5));
                // the sun glittering on the ripples
                vec3 rd = reflect(-v, vec3(0.0, 1.0, 0.0));
                float glit = pow(max(dot(rd, uSunDir), 0.0), 40.0) * step(0.6, r2) * uDay;
                c += vec3(1.0, 0.95, 0.80) * step(0.25, glit) * 0.55;

                // lily pads on the marsh's water: round, notched, two greens
                float pad = 0.0;
                if (vKind == 2 && depth > 0.15) {
                    vec2 cell = floor(vPos.xz / 0.9);
                    float h = slHash(cell + 41.0);
                    if (h > 0.72) {
                        vec2 centre = (cell + 0.25 + 0.5 * vec2(slHash(cell + 3.0), slHash(cell + 9.0))) * 0.9;
                        vec2 o = b - centre;
                        float r = 0.16 + 0.12 * slHash(cell + 17.0);
                        float a = atan(o.y, o.x) - h * 40.0;
                        float notch = step(0.25, abs(mod(a + 3.14159, 6.28318) - 3.14159));
                        pad = step(length(o), r) * notch;
                        vec3 green = mix(vec3(0.21, 0.42, 0.13), vec3(0.47, 0.64, 0.27), step(0.0, -o.x - o.y));
                        c = mix(c, green, pad);
                        if (h > 0.93 && length(o) < 0.05) c = vec3(1.0, 0.78, 0.86);    // a flower
                    }
                }

                // foam where it laps the shore; on the sea, waves running up the beach
                // (a thin ragged line on a pond, a lighter band just inside it)
                float edge = 0.02 + 0.035 * slNoise(b * 3.0 + uTime * 0.5);
                if (vKind == 3) edge += 0.08 * (0.5 + 0.5 * sin(uTime * 1.2 - (vPos.x + vPos.z) * 0.18));
                float foam = step(depth, edge) * (1.0 - pad);
                float wet = step(depth, edge * 1.7) * (1.0 - foam) * (1.0 - pad);
                c = mix(c, vec3(0.92, 0.96, 0.98), foam * (vKind == 3 ? 0.85 : 0.6) + wet * 0.12);

                c *= sceneLight(vPos);
                c = mix(c, uMistColour, sceneMist(vPos));
                float alpha = vKind == 2 ? clamp(0.72 + depth * 0.6, 0.72, 0.95) : clamp(0.5 + depth * 0.9, 0.5, 0.92);
                alpha = max(alpha, max(foam * (vKind == 3 ? 0.95 : 0.75), pad * 0.95));
                alpha = floor(alpha * 6.0 + 0.5) / 6.0;
                float dist = length(vPos - uEye);
                float fog = 1.0 - exp(-pow(dist / uFogDistance, 2.0));
                fragColor = vec4(mix(c, uHorizon, fog), alpha);
            }
            """;

    /** Floats a vertex: x, y, z, depth, flow x, flow z, kind. */
    private static final int STRIDE = 7;
    /** Metres a cell of the sea's grid. */
    static final double SEA_CELL = 1.0;
    /** How far the sea runs on past the level, metres. */
    static final double SEA_REACH = 3000;

    private final Shader shader;
    private final int vao, vbo;
    private WaterMap water;
    private Terrain terrain;
    private int version = -1, count;
    private float[] buf = new float[0];

    /** Must be made on the GL thread. */
    public WaterRenderer() {
        shader = new Shader("water", VERTEX, FRAGMENT);
        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int stride = STRIDE * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 1, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 4L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(3);
        glBindVertexArray(0);
    }

    /** The triangles' corners for {@code w} over {@code t}: {@link #STRIDE} floats each, two triangles a quad. */
    /** How far below its rim (metres) painted water is taken to end where it stops short of a bank. */
    static final double RIM = 0.15;

    static float[] mesh(WaterMap w, Terrain t) {
        Mesh m = new Mesh();
        int n = w.cells();
        // the painted cells that show (not under the sea), and each corner's surface: the mean of the cells
        // round it that have water, so the surface runs on without a step from cell to cell
        boolean[] wet = new boolean[n * n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                float s = w.surfaceAt(c, r);
                wet[r * n + c] = !Float.isNaN(s) && w.kindOf(c, r) != null
                        && (Double.isNaN(w.seaLevel()) || w.seaLevel() < s);
            }
        }
        int k1 = n + 1;
        float[] cy = new float[k1 * k1], cd = new float[k1 * k1];
        for (int j = 0; j <= n; j++) {
            for (int i = 0; i <= n; i++) {
                double sum = 0;
                int count = 0;
                for (int dr = -1; dr <= 0; dr++) {
                    for (int dc = -1; dc <= 0; dc++) {
                        int c = i + dc, r = j + dr;
                        if (c < 0 || r < 0 || c >= n || r >= n || !wet[r * n + c]) continue;
                        sum += w.surfaceAt(c, r);
                        count++;
                    }
                }
                if (count == 0) continue;
                double y = sum / count, x = w.west() + i * w.cell(), z = w.west() + j * w.cell();
                double d = y - t.height(x, z);
                // where the painted water ends short of a bank, it ends in a slant across the cell, not a step
                if (count < 4) d = Math.min(d, -RIM);
                cy[j * k1 + i] = (float) y;
                cd[j * k1 + i] = (float) d;
            }
        }
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (!wet[r * n + c]) continue;
                int a = r * k1 + c, b = a + 1, e = a + k1, f = e + 1;
                if (Math.max(Math.max(cd[a], cd[b]), Math.max(cd[e], cd[f])) <= 0) continue;     // all under the bank
                double x0 = w.west() + c * w.cell(), z0 = w.west() + r * w.cell();
                m.quad(x0, z0, x0 + w.cell(), z0 + w.cell(), cy[a], cy[b], cy[e], cy[f], cd[a], cd[b], cd[e],
                        cd[f], w.flowX(c, r), w.flowZ(c, r), w.kindOf(c, r).code());
            }
        }
        double sea = w.seaLevel();
        if (!Double.isNaN(sea)) {
            double half = w.size() / 2;
            int cells = (int) Math.ceil(w.size() / SEA_CELL);
            int code = WaterMap.Kind.SEA.code();
            for (int r = 0; r < cells; r++) {
                for (int c = 0; c < cells; c++) {
                    double x0 = -half + c * SEA_CELL, z0 = -half + r * SEA_CELL;
                    double x1 = x0 + SEA_CELL, z1 = z0 + SEA_CELL;
                    double d00 = sea - t.height(x0, z0), d10 = sea - t.height(x1, z0);
                    double d01 = sea - t.height(x0, z1), d11 = sea - t.height(x1, z1);
                    if (Math.max(Math.max(d00, d10), Math.max(d01, d11)) <= 0) continue;
                    // a pool painted above the sea's level hides the sea under it
                    double xc = (x0 + x1) / 2, zc = (z0 + z1) / 2;
                    if (w.kindAt(xc, zc) != null && w.surface(xc, zc, t.height(xc, zc)) > sea + 1e-3) continue;
                    m.quad(x0, z0, x1, z1, sea, sea, sea, sea, d00, d10, d01, d11, 0, 0, code);
                }
            }
            if (sea > 0) {
                // beyond the level the ground is flat at 0: the sea runs on to the horizon
                double R = SEA_REACH, d = sea;
                m.quad(-R, -R, R, -half, sea, sea, sea, sea, d, d, d, d, 0, 0, code);
                m.quad(-R, half, R, R, sea, sea, sea, sea, d, d, d, d, 0, 0, code);
                m.quad(-R, -half, -half, half, sea, sea, sea, sea, d, d, d, d, 0, 0, code);
                m.quad(half, -half, R, half, sea, sea, sea, sea, d, d, d, d, 0, 0, code);
            }
        }
        return m.floats();
    }

    private static final class Mesh {
        float[] f = new float[STRIDE * 6 * 256];
        int n;

        void quad(double x0, double z0, double x1, double z1, double y00, double y10, double y01, double y11,
                  double d00, double d10, double d01, double d11, double fx, double fz, int kind) {
            vertex(x0, y00, z0, d00, fx, fz, kind);
            vertex(x0, y01, z1, d01, fx, fz, kind);
            vertex(x1, y10, z0, d10, fx, fz, kind);
            vertex(x1, y11, z1, d11, fx, fz, kind);
            vertex(x1, y10, z0, d10, fx, fz, kind);
            vertex(x0, y01, z1, d01, fx, fz, kind);
        }

        void vertex(double x, double y, double z, double depth, double fx, double fz, int kind) {
            if (n + STRIDE > f.length) f = Arrays.copyOf(f, f.length * 2);
            f[n++] = (float) x;
            f[n++] = (float) y;
            f[n++] = (float) z;
            f[n++] = (float) depth;
            f[n++] = (float) fx;
            f[n++] = (float) fz;
            f[n++] = kind;
        }

        float[] floats() {
            return Arrays.copyOf(f, n);
        }
    }

    /**
     * Draw {@code w} over {@code t} - built again first if either changed -
     * from {@code eye}, lit by {@code scene} under {@code light}, fading into
     * {@code fog} as the ground does. Blends over what is drawn; writes no
     * depth.
     */
    public void draw(WaterMap w, Terrain t, Vec3 eye, Mat4 viewProj, float[] fog, float fogDistance,
                     DayCycle.Daylight light, SceneLight scene, double time) {
        if (w == null || w.isEmpty()) return;
        if (w != water || t != terrain || w.version() != version) {
            water = w;
            terrain = t;
            version = w.version();
            buf = mesh(w, t);
            count = buf.length / STRIDE;
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, buf, GL_DYNAMIC_DRAW);
        }
        if (count == 0) return;
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
        shader.use();
        shader.set("uViewProj", viewProj);
        shader.set("uEye", (float) eye.x(), (float) eye.y(), (float) eye.z());
        shader.set("uHorizon", fog[0], fog[1], fog[2]);
        float[] sky = light.horizon(), top = light.zenith();
        shader.set("uSky", (sky[0] + top[0]) / 2, (sky[1] + top[1]) / 2, (sky[2] + top[2]) / 2);
        Vec3 sun = light.sun();
        shader.set("uSunDir", (float) sun.x(), (float) sun.y(), (float) sun.z());
        shader.set("uDay", (float) light.day());
        shader.set("uFogDistance", fogDistance);
        shader.set("uTime", (float) (time % 10000));
        scene.bind(shader);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, count);
        glBindVertexArray(0);
        glDepthMask(true);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void close() {
        shader.close();
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
