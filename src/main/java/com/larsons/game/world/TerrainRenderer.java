package com.larsons.game.world;

import com.larsons.game.gfx.Shader;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;

import java.util.List;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws the {@link Terrain}'s hills: its grid as a mesh of triangles - two a
 * cell, as {@link Terrain#height} has them - over the endless flat ground
 * ({@link VoidRenderer}), which it covers wherever the ground is not flat.
 *
 * <ul>
 *   <li><b>The ground</b> is {@link GroundTexture}'s {@code groundColour}, the
 *       same grass, paths and meadows the flat ground has, texel for texel:
 *       where a hill rises out of the meadow there is no seam. A slope is lit
 *       by the sun - the flat exactly as bright as the flat ground round it -
 *       in steps, as the pixel art is shaded.</li>
 *   <li><b>Rock</b> on whatever is too steep to walk ({@link Terrain#STEEP}):
 *       the gravel tile in banded strata, laid on the face from the side so
 *       it does not smear down a cliff, its edge with the grass ragged.</li>
 *   <li><b>Vines</b> hang on the walls she can climb ({@link Terrain#walls()}):
 *       strands and leaves in pixel-sized blocks.</li>
 * </ul>
 *
 * <p>It writes depth, so a hill hides what stands behind it - but its depth
 * is set {@value #DEPTH_BACK} m further along the view than it is, so a
 * character standing on it is never cut off at the toes (her card stands
 * upright on her spot, and the ground just in front of her feet would
 * otherwise be in front of them). Cells that are flat at {@code y = 0} are
 * left to the flat ground, which writes its depth the same way: so the earth
 * in front of the hollow hides its inside, below the ground, from the side.
 */
public final class TerrainRenderer implements AutoCloseable {

    /** How far behind itself (metres along the view) the terrain's depth is written. */
    public static final float DEPTH_BACK = 0.35f;
    /** The most climbable walls the shader paints vines on. */
    public static final int MAX_WALLS = 16;

    private static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            uniform mat4 uViewProj;
            out vec3 vPos;
            out vec3 vNormal;
            void main() {
                vPos = aPos;
                vNormal = aNormal;
                gl_Position = uViewProj * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec3 vPos;
            in vec3 vNormal;
            uniform mat4 uViewProj;
            uniform vec3 uEye;
            uniform vec3 uHorizon;
            uniform float uFogDistance;
            uniform float uDepthBack;
            uniform vec3 uSun;
            uniform vec4 uWall[16];     // a climbable wall's foot: from (x, z), to (x, z)
            uniform vec4 uWallOut[16];  // its outward normal (x, z), its bottom and top
            uniform int uWalls;
            out vec4 fragColor;
            """ + GroundTexture.GLSL + """

            // the vines on a climbable wall, in blocks of four texels (they read from afar):
            // strands that wander down the face, leaves along them; alpha 0 where there are none
            vec4 vines(vec3 p, vec3 n) {
                float block = 4.0 * max(uGroundTexel, 0.01);
                vec4 c = vec4(0.0);
                for (int i = 0; i < 16; i++) {
                    if (i >= uWalls) break;
                    vec2 a = uWall[i].xy, b = uWall[i].zw, o = uWallOut[i].xy;
                    float len = max(length(b - a), 1e-4);
                    vec2 dir = (b - a) / len;
                    float outward = dot(p.xz - a, o), along = dot(p.xz - a, dir);
                    if (outward < -0.8 || outward > 0.15 || along < 0.0 || along > len) continue;
                    if (p.y < uWallOut[i].z || p.y > uWallOut[i].w + 0.2 || n.y > 0.6) continue;
                    vec2 q = floor(vec2(along, p.y) / block) * block;    // pixel blocks on the face
                    float s = q.x / 0.34;
                    float strand = floor(s);
                    float wob = 0.18 * sin(q.y * 2.1 + strand * 1.7) + 0.08 * sin(q.y * 5.3 + strand);
                    float x = fract(s + wob) - 0.5;
                    float h = groundHash(ivec2(q / block));
                    if (abs(x) < 0.11) c = h > 0.7 ? vec4(0.36, 0.56, 0.22, 1.0) : vec4(0.25, 0.40, 0.17, 1.0);
                    else if (abs(x) < 0.38 && h > 0.45) c = h > 0.8 ? vec4(0.56, 0.76, 0.31, 1.0)
                                                                  : vec4(0.38, 0.61, 0.24, 1.0);
                }
                return c;
            }

            void main() {
                vec3 n = normalize(vNormal);
                // (every texture read before anything branches: their mip levels need the neighbours)
                vec3 grass = uGroundTile > 0.0 ? groundColour(vPos.xz) : vec3(0.60, 0.63, 0.68);
                float tile = max(uGroundTile, 1.0);
                vec3 rx = texture(uGroundGravel, vPos.zy / tile).rgb;
                vec3 rz = texture(uGroundGravel, vPos.xy / tile).rgb;
                vec3 rtop = texture(uGroundGravel, vPos.xz / tile).rgb;

                // rock: the gravel seen from the side on a face, from above on a ledge, in strata
                float side = abs(n.x) / max(1e-4, abs(n.x) + abs(n.z));
                vec3 rock = mix(mix(rz, rx, side), rtop, smoothstep(0.75, 0.95, n.y));
                float block = 2.0 * max(uGroundTexel, 0.01);
                float band = groundNoise(vec2(floor(vPos.y / block) * block * 1.7, (vPos.x + vPos.z) * 0.12));
                rock *= vec3(0.80, 0.81, 0.88) * (0.84 + 0.24 * floor(band * 4.0) / 4.0);
                // where it is too steep to walk (45 degrees: n.y 0.71), ragged at the edge
                float jag = (groundHash(ivec2(floor(vPos.xz / block))) - 0.5) * 0.07;
                float rocky = smoothstep(0.27, 0.30, 1.0 - n.y + jag);
                vec3 c = mix(grass, rock, rocky);
                vec4 v = vines(vPos, n);
                c = mix(c, v.rgb, v.a);

                // the sun, in steps; the flat as bright as the flat ground round it
                float shade = 0.55 + 0.45 * dot(n, uSun) / uSun.y;
                shade = clamp(floor(shade / 0.06 + 0.5) * 0.06, 0.42, 1.2);
                c *= mix(shade, max(shade, 0.9), v.a);          // the vines catch the light out on the face

                float dist = length(vPos - uEye);
                float fog = 1.0 - exp(-pow(dist / uFogDistance, 2.0));
                fragColor = vec4(mix(c, uHorizon, fog), 1.0);

                // its depth a little behind itself, so what stands on it is never cut off at the feet
                vec4 clip = uViewProj * vec4(vPos + normalize(vPos - uEye) * uDepthBack, 1.0);
                gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
            }
            """;

    /** Where the sun is: high, from the south-west (the light the sprites are rendered in, near enough). */
    static final Vec3 SUN = new Vec3(-0.35, 0.82, 0.45).normalize();

    private final Shader shader;
    private final GroundTexture ground;
    private final int vao, vbo, ebo;
    /** The terrain whose mesh is on the GPU, and how many indices it has. */
    private Terrain terrain;
    private int count;

    /**
     * Drawing {@code terrain}'s mesh, with {@code ground}'s textures - or,
     * from the first time it is handed another ({@link #draw}), that one's.
     * Must be made on the GL thread.
     */
    /** How many triangles the hills are drawn with. */
    public int triangles() {
        return count / 3;
    }

    public TerrainRenderer(GroundTexture ground, Terrain terrain) {
        this.ground = ground;
        shader = new Shader("terrain", VERTEX, FRAGMENT);
        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int stride = 6 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBindVertexArray(0);
        upload(terrain);
        if (count > 0) System.out.println("[terrain] " + count / 3 + " triangles");
    }

    /** Put {@code t}'s mesh on the GPU in place of the last one's (a level's, or its ground just sculpted). */
    private void upload(Terrain t) {
        terrain = t;
        float[] verts = vertices(t);
        int[] indices = indices(t);
        count = indices.length;
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_DYNAMIC_DRAW);
        glBindVertexArray(0);
    }

    /** The grid's points, each its position and its normal (x, y, z, nx, ny, nz). */
    static float[] vertices(Terrain t) {
        int nx = t.columns(), nz = t.rows();
        float[] out = new float[nx * nz * 6];
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                int k = (j * nx + i) * 6;
                double x = t.originX() + i * Terrain.STEP, z = t.originZ() + j * Terrain.STEP;
                double hl = t.at(Math.max(0, i - 1), j), hr = t.at(Math.min(nx - 1, i + 1), j);
                double hn = t.at(i, Math.max(0, j - 1)), hs = t.at(i, Math.min(nz - 1, j + 1));
                double sx = (Math.min(nx - 1, i + 1) - Math.max(0, i - 1)) * Terrain.STEP;
                double sz = (Math.min(nz - 1, j + 1) - Math.max(0, j - 1)) * Terrain.STEP;
                Vec3 n = new Vec3(-(hr - hl) / sx, 1, -(hs - hn) / sz).normalize(Vec3.UP);
                out[k] = (float) x;
                out[k + 1] = t.at(i, j);
                out[k + 2] = (float) z;
                out[k + 3] = (float) n.x();
                out[k + 4] = (float) n.y();
                out[k + 5] = (float) n.z();
            }
        }
        return out;
    }

    /**
     * Two triangles a cell - split from its north-east corner to its
     * south-west, as {@link Terrain#height} is - leaving out the cells flat
     * at {@code y = 0} (the flat ground's own).
     */
    static int[] indices(Terrain t) {
        int nx = t.columns(), nz = t.rows();
        int[] out = new int[Math.max(0, nx - 1) * Math.max(0, nz - 1) * 6];
        int k = 0;
        for (int j = 0; j + 1 < nz; j++) {
            for (int i = 0; i + 1 < nx; i++) {
                if (t.flatCell(i, j)) continue;
                int a = j * nx + i, b = a + 1, c = a + nx, d = c + 1;
                out[k++] = a;
                out[k++] = c;
                out[k++] = b;
                out[k++] = d;
                out[k++] = b;
                out[k++] = c;
            }
        }
        return java.util.Arrays.copyOf(out, k);
    }

    /**
     * Draw {@code t} from {@code camera} - its mesh put on the GPU first if
     * it is not the one there (flat ground has nothing to draw) - fading into
     * the fog as the flat ground does. Leaves depth testing on.
     */
    public void draw(Terrain t, OrbitCamera camera, Mat4 viewProj, float[] fog, float fogDistance) {
        if (t != terrain) upload(t);
        if (count == 0) return;
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        shader.use();
        shader.set("uViewProj", viewProj);
        Vec3 eye = camera.eye();
        shader.set("uEye", (float) eye.x(), (float) eye.y(), (float) eye.z());
        shader.set("uHorizon", fog[0], fog[1], fog[2]);
        shader.set("uFogDistance", fogDistance);
        shader.set("uDepthBack", DEPTH_BACK);
        shader.set("uSun", (float) SUN.x(), (float) SUN.y(), (float) SUN.z());
        List<Terrain.Wall> walls = t.walls();
        int n = Math.min(MAX_WALLS, walls.size());
        for (int i = 0; i < n; i++) {
            Terrain.Wall w = walls.get(i);
            shader.set("uWall[" + i + "]", (float) w.x0(), (float) w.z0(), (float) w.x1(), (float) w.z1());
            shader.set("uWallOut[" + i + "]", (float) w.nx(), (float) w.nz(), (float) w.bottom(), (float) w.top());
        }
        shader.set("uWalls", n);
        ground.bind(shader, 0);
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    @Override
    public void close() {
        shader.close();
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
    }
}
