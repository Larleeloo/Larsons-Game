package com.larsons.game.world;

import com.larsons.game.gfx.Shader;
import com.larsons.game.math.Mat4;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * The ground and the sky: an endless meadow of grass fading into a soft sky
 * at the horizon — drawn in one full-screen pass on the GPU.
 *
 * <p>Every pixel casts its own view ray and intersects it with the ground
 * plane {@code y = 0} in the fragment shader, so the ground has no edge, no
 * tessellation and a perfectly straight horizon at any zoom.
 *
 * <p>It writes depth, as the solid earth it is: what lies below {@code y = 0}
 * - the inside of a hollow in the hills, seen from beyond its near rim - is
 * hidden behind the ground in front of it, not seen through it. Its depth is
 * set {@link TerrainRenderer#DEPTH_BACK} m further along the view than it is,
 * as the hills' is, so the toes of a 45° sprite, which dip below its plane,
 * are never cut off. It writes none in the hills' own cells ({@link
 * Terrain#flatCell}): {@link TerrainRenderer} draws the ground there, a
 * hollow's floor below the plane included.
 *
 * <p><b>The grass</b> - and the paths, the groves' forest floor, the rock
 * beds' gravel and the flower meadows on it - is {@link GroundTexture}'s
 * {@code groundColour}: pixel-art tiles in the game's palette ({@code
 * assets/scenery/ground/}), repeated every {@code tileWorldSize} metres
 * (9.6 m puts their pixels at the characters' 1.875 cm), crisp underfoot and
 * mipmapped towards the horizon so they never shimmer far away. Without the
 * grass the ground is the plain studio floor it was before: pale, with a
 * metre grid and the two axes.
 *
 * <p>The fog is the same either way: the ground fades into {@link #HORIZON}
 * with distance, as everything else does.
 */
public final class VoidRenderer implements AutoCloseable {

    /** Colour of the horizon — also the fog colour everything else fades to. */
    public static final float[] HORIZON = {0.74f, 0.79f, 0.86f};

    private static final String VERTEX = """
            #version 330 core
            out vec2 vNdc;
            void main() {
                // One triangle that covers the screen.
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2) * 2.0 - 1.0;
                vNdc = p;
                gl_Position = vec4(p, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec2 vNdc;
            uniform vec3 uEye, uRight, uUp, uForward;
            uniform vec2 uTanHalf;
            uniform vec3 uHorizon;
            uniform float uFogDistance;
            uniform mat4 uViewProj;
            uniform float uDepthBack;
            uniform sampler2D uHills;   // a texel a cell of the hills' grid: 1 where the hills draw the ground
            uniform vec3 uHillsGrid;    // the grid's west and north edges (x, z), and metres a cell spans
            out vec4 fragColor;
            """ + GroundTexture.GLSL + """

            float gridLine(vec2 coord, float width) {
                vec2 d = abs(fract(coord - 0.5) - 0.5) / max(fwidth(coord), vec2(1e-5));
                return 1.0 - clamp(min(d.x, d.y) - width, 0.0, 1.0);
            }

            vec3 studioFloor(vec3 p) {
                // A pale studio floor, a little lighter round the spawn point.
                vec3 floorColor = vec3(0.60, 0.63, 0.68);
                floorColor += 0.05 * exp(-dot(p.xz, p.xz) / 180.0);

                // Metre lines, heavier every five metres, and the two axes.
                float minor = gridLine(p.xz, 0.0);
                float major = gridLine(p.xz / 5.0, 0.4);
                vec3 c = floorColor;
                c = mix(c, floorColor * 0.86, minor * 0.55);
                c = mix(c, floorColor * 0.72, major * 0.75);
                float ax = 1.0 - clamp(abs(p.z) / max(fwidth(p.z), 1e-5) - 0.6, 0.0, 1.0);
                float az = 1.0 - clamp(abs(p.x) / max(fwidth(p.x), 1e-5) - 0.6, 0.0, 1.0);
                c = mix(c, vec3(0.78, 0.40, 0.38), ax * 0.6);
                c = mix(c, vec3(0.38, 0.50, 0.80), az * 0.6);
                return c;
            }

            // whether the ground at (x, z) is the hills' to draw, not the flat ground's
            bool inTheHills(vec2 xz) {
                vec2 g = (xz - uHillsGrid.xy) / uHillsGrid.z;
                if (any(lessThan(g, vec2(0.0))) || any(greaterThanEqual(g, vec2(textureSize(uHills, 0))))) return false;
                return texelFetch(uHills, ivec2(g), 0).r > 0.5;
            }

            void main() {
                gl_FragDepth = 1.0;
                vec3 dir = normalize(uForward + vNdc.x * uTanHalf.x * uRight
                                              + vNdc.y * uTanHalf.y * uUp);
                vec3 zenith = vec3(0.36, 0.47, 0.64);
                vec3 sky = mix(uHorizon, zenith, pow(clamp(dir.y, 0.0, 1.0), 0.6));

                // (the ground is sampled before any early exit: its mip levels need the neighbours' coordinates)
                float t = dir.y < -1e-4 ? -uEye.y / dir.y : 0.0;
                vec3 p = uEye + dir * t;
                vec3 grass = uGroundTile > 0.0 ? groundColour(p.xz) : vec3(0.0);

                if (dir.y >= -1e-4 || uEye.y <= 0.0) {
                    fragColor = vec4(sky, 1.0);
                    return;
                }
                vec3 c = uGroundTile > 0.0 ? grass : studioFloor(p);
                float fog = 1.0 - exp(-pow(t / uFogDistance, 2.0));
                fragColor = vec4(mix(c, uHorizon, fog), 1.0);

                // its depth a little behind itself, as the hills' is - but none where the hills draw the ground
                if (!inTheHills(p.xz)) {
                    vec4 clip = uViewProj * vec4(p + dir * uDepthBack, 1.0);
                    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
                }
            }
            """;

    private final Shader shader;
    private final int vao;
    private final GroundTexture ground;
    /** The hills' cells ({@link #hillCells}) of {@link #hillsOf}, a texture on the GPU; 0 until the first draw. */
    private int hills;
    private Terrain hillsOf;

    /** The ground drawn with {@code ground}, or the studio floor when it has no grass. */
    public VoidRenderer(GroundTexture ground) {
        shader = new Shader("void", VERTEX, FRAGMENT);
        vao = glGenVertexArrays();
        this.ground = ground;
    }

    /** Whether the ground is grass (false: the studio floor). */
    public boolean grass() { return ground.grass(); }

    /**
     * Draw the sky and the flat ground round {@code terrain}'s hills from
     * {@code camera}, writing the ground's depth. Leaves depth testing on.
     */
    public void draw(OrbitCamera camera, double aspect, float fogDistance, Mat4 viewProj, Terrain terrain) {
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_ALWAYS);
        glDepthMask(true);
        glDisable(GL_BLEND);
        shader.use();
        set("uEye", camera.eye());
        set("uRight", camera.right());
        set("uUp", camera.up());
        set("uForward", camera.forward());
        double tanY = Math.tan(camera.fovY() / 2);
        shader.set("uTanHalf", (float) (tanY * aspect), (float) tanY);
        shader.set("uHorizon", HORIZON[0], HORIZON[1], HORIZON[2]);
        shader.set("uFogDistance", fogDistance);
        shader.set("uViewProj", viewProj);
        shader.set("uDepthBack", TerrainRenderer.DEPTH_BACK);
        ground.bind(shader, 0);
        bindHills(terrain, GroundTexture.UNITS);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
        glDepthFunc(GL_LEQUAL);
    }

    /** Bind {@code terrain}'s hill cells to {@code unit} - uploaded the first time it is drawn - and set their grid. */
    private void bindHills(Terrain terrain, int unit) {
        int w = Math.max(0, terrain.columns() - 1), h = Math.max(0, terrain.rows() - 1);
        if (terrain != hillsOf) {
            if (hills == 0) hills = glGenTextures();
            byte[] cells = w * h == 0 ? new byte[1] : hillCells(terrain);
            ByteBuffer buf = BufferUtils.createByteBuffer(cells.length).put(cells).flip();
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, hills);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, Math.max(1, w), Math.max(1, h), 0, GL_RED, GL_UNSIGNED_BYTE, buf);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            hillsOf = terrain;
        }
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, hills);
        shader.set("uHills", unit);
        shader.set("uHillsGrid", (float) terrain.originX(), (float) terrain.originZ(), (float) Terrain.STEP);
    }

    /**
     * A byte a cell of {@code terrain}'s grid, row by row from its north-west
     * corner: 255 where the hills draw the ground, 0 where it is flat ({@link
     * Terrain#flatCell}). Empty for flat ground everywhere.
     */
    static byte[] hillCells(Terrain terrain) {
        int w = Math.max(0, terrain.columns() - 1), h = Math.max(0, terrain.rows() - 1);
        byte[] out = new byte[w * h];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (!terrain.flatCell(i, j)) out[j * w + i] = (byte) 255;
            }
        }
        return out;
    }

    private void set(String name, com.larsons.game.math.Vec3 v) {
        shader.set(name, (float) v.x(), (float) v.y(), (float) v.z());
    }

    @Override
    public void close() {
        shader.close();
        glDeleteVertexArrays(vao);
        if (hills != 0) glDeleteTextures(hills);
    }
}
