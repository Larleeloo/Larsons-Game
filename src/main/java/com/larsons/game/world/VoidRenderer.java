package com.larsons.game.world;

import com.larsons.game.gfx.Shader;
import com.larsons.game.math.Mat4;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

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
 * <p>The fog is the same either way: the ground fades into the {@link Sky}'s
 * horizon with distance, as everything else does - {@link #HORIZON} under
 * the demo's sky.
 *
 * <p><b>The distant backgrounds</b> ({@link Sky.Backdrop}) stand round the
 * horizon in the same pass, at no distance - only which way you look
 * changes them: ranges of mountains (their lit faces, their shadowed ones,
 * snow on their peaks), rolling hills, a tree line, towers and spires, each
 * a profile along the horizon made from noise that wraps all the way round,
 * drawn in chunky pixels of a few screen pixels each and faded into the
 * horizon's colour by its haze; and the sea, which takes the place of the
 * flat ground beyond its shore, glinting.
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
            uniform vec3 uZenith;
            uniform float uTime;
            uniform float uBdPixel;     // degrees a backdrop's pixel spans
            uniform int uBackdrops;
            uniform vec4 uBdA[8];       // kind, height (degrees), haze, seed
            uniform vec4 uBdB[8];       // colour (rgb), snow
            uniform vec4 uBdC[8];       // from, to (degrees from north; equal: all round), the sea's shore (m), 0
            out vec4 fragColor;
            """ + GroundTexture.GLSL + """

            // --- the distant backgrounds ---------------------------------------------------
            float bdHash(float n) {
                return fract(sin(n * 12.9898 + 4.1414) * 43758.5453);
            }

            // value noise round the horizon: cells cells all the way round, so it wraps
            float bdNoise(float az, float cells, float seed) {
                float x = az / 360.0 * cells;
                float i = floor(x), f = fract(x);
                f = f * f * (3.0 - 2.0 * f);
                return mix(bdHash(mod(i, cells) + seed * 101.0), bdHash(mod(i + 1.0, cells) + seed * 101.0), f);
            }

            // 1 inside the arc from..to (degrees), tapering to 0 over its last 8 degrees; all round when equal
            float bdArc(float az, float from, float to) {
                if (from == to) return 1.0;
                float len = mod(to - from, 360.0), at = mod(az - from, 360.0);
                if (at > len) return 0.0;
                return smoothstep(0.0, 8.0, at) * smoothstep(0.0, 8.0, len - at);
            }

            // a backdrop's profile at azimuth az: how high its top stands, 0 to 1 of its height
            float bdProfile(int kind, float az, float seed) {
                if (kind == 0) {            // mountains: ridged, sharp peaks
                    float h = 0.0, amp = 0.55;
                    float cells = 7.0;
                    for (int o = 0; o < 5; o++) {
                        float n = bdNoise(az, cells, seed + float(o) * 7.0);
                        h += amp * (1.0 - abs(2.0 * n - 1.0));
                        amp *= 0.5;
                        cells = cells * 2.0 + 1.0;
                    }
                    return clamp(h * 1.05 - 0.15, 0.05, 1.0);
                }
                if (kind == 1) {            // rolling hills: smooth and low
                    float h = 0.6 * bdNoise(az, 9.0, seed) + 0.3 * bdNoise(az, 23.0, seed + 3.0)
                            + 0.1 * bdNoise(az, 61.0, seed + 5.0);
                    return clamp(h, 0.1, 1.0);
                }
                if (kind == 2) {            // a tree line: a crown every cell, pointed or round
                    float cells = 900.0;
                    float x = az / 360.0 * cells;
                    float i = floor(x), f = fract(x) - 0.5;
                    float tall = 0.55 + 0.45 * bdHash(mod(i, cells) + seed * 31.0);
                    float roll = 0.7 + 0.3 * bdNoise(az, 17.0, seed + 9.0);
                    float pine = bdHash(mod(i, cells) + seed * 57.0) > 0.5 ? 1.0 : 0.0;
                    float crown = pine > 0.5 ? 1.0 - abs(f) * 2.0 : sqrt(max(0.0, 1.0 - f * f * 4.0));
                    return tall * roll * (0.55 + 0.45 * crown);
                }
                if (kind == 3) {            // towers and spires over a low town
                    float cells = 140.0;
                    float x = az / 360.0 * cells;
                    float i = floor(x), f = fract(x) - 0.5;
                    float town = 0.12 + 0.1 * floor(bdHash(floor(az / 360.0 * 700.0) + seed * 13.0) * 3.0) / 2.0;
                    float has = bdHash(mod(i, cells) + seed * 19.0);
                    if (has < 0.45) return town;
                    float w = 0.12 + 0.18 * bdHash(mod(i, cells) + seed * 23.0);
                    if (abs(f) > w) return town;
                    float tall = 0.45 + 0.55 * bdHash(mod(i, cells) + seed * 29.0);
                    // a pointed cap on the top fifth
                    float cap = 1.0 - abs(f) / w;
                    return max(town, tall * (0.82 + 0.18 * cap));
                }
                return 0.0;
            }

            // the backdrops over the sky (or the far ground) seen along dir; a = 0 where there are none
            vec4 backdrops(vec3 dir, vec3 horizon, bool ground) {
                vec4 c = vec4(0.0);
                if (uBackdrops == 0) return c;
                float az = degrees(atan(dir.x, -dir.z));
                if (az < 0.0) az += 360.0;
                float el = degrees(asin(clamp(dir.y, -1.0, 1.0)));
                float px = max(uBdPixel, 0.02);
                float qaz = (floor(az / px) + 0.5) * px, qel = (floor(el / px) + 0.5) * px;
                for (int i = 0; i < 8; i++) {
                    if (i >= uBackdrops) break;
                    int kind = int(uBdA[i].x + 0.5);
                    if (kind == 4) continue;                 // the sea is drawn on the ground
                    float arc = bdArc(qaz, uBdC[i].x, uBdC[i].y);
                    if (arc <= 0.0) continue;
                    float top = uBdA[i].y * bdProfile(kind, qaz, uBdA[i].w) * arc;
                    if (qel > top || qel < -1.5) continue;
                    // the lit side faces the sun (west), the other in shadow; in steps, as the art is shaded
                    float left = uBdA[i].y * bdProfile(kind, qaz - px * 2.0, uBdA[i].w);
                    float right = uBdA[i].y * bdProfile(kind, qaz + px * 2.0, uBdA[i].w);
                    vec3 col = uBdB[i].rgb;
                    if (kind != 2 && right > left + px * 0.4) col *= 0.74;
                    if (kind == 2 && bdHash(floor(qaz / px) + floor(qel / px) * 57.0) > 0.7) col *= 0.82;
                    if (kind == 0 && uBdB[i].w > 0.5 && qel > top - uBdA[i].y * 0.22 && top > uBdA[i].y * 0.55) {
                        col = mix(col, vec3(0.93, 0.95, 0.98), 0.85);
                    }
                    // hazier towards its foot, where it meets the horizon
                    float foot = clamp(1.0 - qel / max(uBdA[i].y, 0.5), 0.0, 1.0);
                    float haze = clamp(uBdA[i].z + (1.0 - uBdA[i].z) * 0.35 * foot * foot, 0.0, 1.0);
                    haze = floor(haze * 8.0 + 0.5) / 8.0;
                    c = vec4(mix(col, horizon, haze), 1.0);
                }
                return c;
            }

            // the sea, where it stands in for the ground at p (t metres out): a = 0 where there is none
            vec4 sea(vec3 dir, vec3 p, float t, vec3 horizon) {
                if (uBackdrops == 0) return vec4(0.0);
                float az = degrees(atan(dir.x, -dir.z));
                if (az < 0.0) az += 360.0;
                for (int i = 0; i < 8; i++) {
                    if (i >= uBackdrops) break;
                    if (int(uBdA[i].x + 0.5) != 4) continue;
                    if (bdArc(az, uBdC[i].x, uBdC[i].y) <= 0.5) continue;
                    if (length(p.xz) < uBdC[i].z) continue;
                    vec2 cell = floor(p.xz / vec2(1.6, 0.6));
                    float wave = bdHash(cell.x * 7.0 + cell.y * 131.0 + floor(uTime * 1.5 + bdHash(cell.y) * 9.0));
                    vec3 col = uBdB[i].rgb * (wave > 0.93 ? 1.45 : wave > 0.7 ? 1.12 : 1.0);
                    float haze = clamp(uBdA[i].z + (1.0 - uBdA[i].z) * (1.0 - exp(-t / 900.0)), 0.0, 1.0);
                    return vec4(mix(col, horizon, haze), 1.0);
                }
                return vec4(0.0);
            }

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
                vec3 sky = mix(uHorizon, uZenith, pow(clamp(dir.y, 0.0, 1.0), 0.6));

                // (the ground is sampled before any early exit: its mip levels need the neighbours' coordinates)
                float t = dir.y < -1e-4 ? -uEye.y / dir.y : 0.0;
                vec3 p = uEye + dir * t;
                vec3 grass = uGroundTile > 0.0 ? groundColour(p.xz) : vec3(0.0);

                if (dir.y >= -1e-4 || uEye.y <= 0.0) {
                    vec4 far = backdrops(dir, uHorizon, false);
                    fragColor = vec4(mix(sky, far.rgb, far.a), 1.0);
                    return;
                }
                vec3 c = uGroundTile > 0.0 ? grass : studioFloor(p);
                float fog = 1.0 - exp(-pow(t / uFogDistance, 2.0));
                c = mix(c, uHorizon, fog);
                vec4 water = sea(dir, p, t, uHorizon);
                c = mix(c, water.rgb, water.a);
                // a backdrop's foot, just under the horizon, over the ground faded into it
                vec4 far = backdrops(dir, uHorizon, true);
                c = mix(c, far.rgb, far.a * fog);
                fragColor = vec4(c, 1.0);

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

    /** Draw the ground as {@code map} has it from now on ({@link GroundTexture#use}). */
    public void useGround(GroundMap map) {
        ground.use(map);
    }

    /** Whether the ground is grass (false: the studio floor). */
    public boolean grass() { return ground.grass(); }

    /**
     * Draw the sky and the flat ground round {@code terrain}'s hills from
     * {@code camera}, writing the ground's depth. Leaves depth testing on.
     */
    public void draw(OrbitCamera camera, double aspect, float fogDistance, Mat4 viewProj, Terrain terrain) {
        draw(camera, aspect, fogDistance, viewProj, terrain, Sky.DEFAULT, 0, 0.2f);
    }

    /**
     * Draw {@code sky} - its colours, its distant backgrounds, {@code
     * pixelDegrees} of the view a backdrop's pixel - and the flat ground
     * round {@code terrain}'s hills from {@code camera} at world time {@code
     * time} (the sea glints with it), writing the ground's depth. Leaves
     * depth testing on.
     */
    public void draw(OrbitCamera camera, double aspect, float fogDistance, Mat4 viewProj, Terrain terrain, Sky sky,
                     double time, float pixelDegrees) {
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
        float[] horizon = sky == Sky.DEFAULT ? HORIZON : sky.horizonColour(), zenith = Sky.rgb(sky.zenith());
        shader.set("uHorizon", horizon[0], horizon[1], horizon[2]);
        shader.set("uZenith", zenith[0], zenith[1], zenith[2]);
        shader.set("uFogDistance", fogDistance);
        shader.set("uTime", (float) (time % 3600));
        shader.set("uBdPixel", pixelDegrees);
        List<Sky.Backdrop> far = sky.backdrops();
        int n = Math.min(Sky.MAX_BACKDROPS, far.size());
        for (int i = 0; i < n; i++) {
            Sky.Backdrop b = far.get(i);
            float[] c = Sky.rgb(b.colour());
            shader.set("uBdA[" + i + "]", b.kind().ordinal(), (float) b.height(), (float) b.haze(), b.seed());
            shader.set("uBdB[" + i + "]", c[0], c[1], c[2], b.snow() ? 1 : 0);
            shader.set("uBdC[" + i + "]", (float) b.from(), (float) b.to(), (float) b.distance(), 0);
        }
        shader.set("uBackdrops", n);
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
