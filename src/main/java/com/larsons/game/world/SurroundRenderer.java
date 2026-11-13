package com.larsons.game.world;

import com.larsons.game.building.Building;
import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.gfx.SceneLight;
import com.larsons.game.gfx.Shader;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.ScenerySprites;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws what lies round a level ({@link Surroundings}), as {@link
 * SurroundGen} makes it:
 *
 * <ul>
 *   <li><b>The far ring</b> first, right after the sky ({@link
 *       VoidRenderer#SKY}) and before anything else, with a projection of its
 *       own that reaches out to the horizon (the world's stops at 600 m); then
 *       the depth is cleared, so everything nearer is drawn over it. Its faces
 *       are shaded flat in steps, as the hills are; its windows light up at
 *       night; low down it fades into the level's fog exactly as the ground
 *       does, and its heights - a mountain's peaks, a city's towers, a
 *       forest's canopy - stand out of the fog in the haze.</li>
 *   <li><b>The near ring</b> with the level: its ground through a {@link
 *       TerrainRenderer} of its own (its own {@link GroundTexture} map,
 *       sharing the level's tiles - the same grass, paths and cobbles), its
 *       water through a {@link WaterRenderer} of its own, its trees and houses
 *       handed to the world's renderer to draw with the level's ({@link
 *       #scenery}, {@link #buildings}).</li>
 * </ul>
 *
 * <p>The surroundings are made again when the level's settings or ground
 * change (in Creative Mode, as it is sculpted and painted) - off the GL
 * thread, a moment after the last change, the old ones drawn meanwhile; a
 * level's first are made at once.
 */
public final class SurroundRenderer implements AutoCloseable {

    private static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aCol;
            layout(location = 2) in float aMat;
            uniform mat4 uViewProj;
            out vec3 vPos;
            out vec3 vCol;
            out float vMat;
            void main() {
                vPos = aPos;
                vCol = aCol;
                vMat = aMat;
                gl_Position = uViewProj * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec3 vPos;
            in vec3 vCol;
            in float vMat;
            uniform vec3 uEye;
            uniform vec3 uHorizon;
            uniform vec3 uSun;
            uniform vec3 uAmbient;
            uniform float uFog;          // the level's fog: 63 % of the ground hidden this far out
            uniform vec3 uHaze;          // the haze its heights are seen through: least, metres it doubles over, radius
            uniform vec2 uLift;          // from what height its heights stand out of the fog, and over how many metres
            uniform float uNight;
            uniform float uTime;
            out vec4 fragColor;

            float fh(vec2 p) {
                uvec2 q = uvec2(ivec2(floor(p)) + 65536);
                uint h = q.x * 374761393u + q.y * 668265263u;
                h = (h ^ (h >> 13u)) * 1274126177u;
                return float((h ^ (h >> 16u)) & 0xffffu) / 65535.0;
            }

            void main() {
                // flat faces, as the low-poly shapes are: the normal from the face itself
                vec3 n = normalize(cross(dFdx(vPos), dFdy(vPos)));
                if (n.y < 0.0 && abs(n.y) > 0.2) n = -n;
                int mat = int(vMat + 0.5);
                vec3 c = vCol;
                float shade = 0.55 + 0.45 * dot(n, uSun) / uSun.y;
                shade = clamp(floor(shade / 0.08 + 0.5) * 0.08, 0.4, 1.2);
                vec3 glow = vec3(0.0);
                if (mat == 1) {
                    // water: flat, glinting now and then
                    float g = fh(floor(vPos.xz / vec2(3.4, 1.3)) + vec2(floor(uTime * 1.3), 0.0));
                    c *= g > 0.94 ? 1.35 : g > 0.75 ? 1.08 : 1.0;
                    shade = 1.0;
                } else if (mat == 2) {
                    // a canopy: round crowns, lit on top, dark in the gaps between them
                    vec2 q = vPos.xz / 5.5;
                    vec2 i = floor(q);
                    float best = 9.0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            vec2 o = i + vec2(dx, dy);
                            vec2 cc = o + 0.2 + 0.6 * vec2(fh(o), fh(o + 17.0));
                            best = min(best, length(q - cc));
                        }
                    }
                    c *= best < 0.32 ? 1.14 : best < 0.58 ? 0.98 : 0.74;
                } else if (mat == 3 && abs(n.y) < 0.3) {
                    // a wall: rows of windows, some lit at night
                    vec3 t = normalize(vec3(-n.z, 0.0, n.x));
                    float u = dot(vPos, t) / 2.6, v = vPos.y / 3.3;
                    vec2 w = fract(vec2(u, v));
                    if (w.x > 0.3 && w.x < 0.7 && w.y > 0.32 && w.y < 0.74) {
                        float on = step(0.55, fh(floor(vec2(u, v)) + floor(vPos.xz / 40.0) * 7.0));
                        c *= 0.5;
                        glow = vec3(1.0, 0.78, 0.42) * on * uNight;
                    }
                } else if (mat == 4) {
                    c *= fract(vPos.y * 2.2) < 0.18 ? 0.88 : 1.0;      // a roof's courses
                } else if (mat == 6) {
                    c *= 1.08;
                    glow = vCol * 0.45 * uNight;
                }
                vec3 lit = mat == 5 ? c * (1.0 + 0.15 * sin(uTime * 2.0 + vPos.x * 0.05)) : c * shade * uAmbient + glow;

                // the fog: low down as the ground has it, its heights out of it, seen through the haze
                float d = length(vPos - uEye);
                float ground = 1.0 - exp(-pow(d / uFog, 2.0));
                float haze = 1.0 - (1.0 - uHaze.x) * exp(-d / uHaze.y);
                float lift = smoothstep(uLift.x, uLift.x + uLift.y, vPos.y);
                float f = mix(ground, min(ground, haze), lift);
                if (mat == 5) f = min(f, haze * 0.8);                 // a volcano's fire shows through
                f = max(f, smoothstep(uHaze.z * 0.78, uHaze.z, length(vPos.xz)));
                f = floor(f * 20.0 + 0.5) / 20.0;
                fragColor = vec4(mix(lit, uHorizon, f), 1.0);
            }
            """;

    private final Shader shader;
    private final int vao, vbo, ebo;
    private int count;
    private final GroundTexture ground;
    private final TerrainRenderer terrain;
    private final WaterRenderer water = new WaterRenderer();
    private final ScenerySprites sprites;
    private final float[][] tileColours;
    private final ExecutorService maker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "surroundings");
        t.setDaemon(true);
        return t;
    });

    /** What the surroundings are made from: when any of it changes, they are made again. */
    private record Key(Surroundings settings, double size, Terrain terrain, GroundMap ground, int groundVersion,
                       WaterMap water, int waterVersion, double sea, int things) {}

    private SurroundGen.Result shown;
    private Key shownKey, wanted, pendingKey;
    private long wantedAt;
    private Future<SurroundGen.Result> pending;
    private boolean active;

    /** Drawing the ground round a level with {@code tilesOf}'s tiles, its trees from {@code sprites}. GL thread. */
    public SurroundRenderer(GroundTexture tilesOf, ScenerySprites sprites) {
        this.sprites = sprites;
        this.tileColours = tileColours(sprites == null ? null : sprites.ground());
        ground = new GroundTexture(tilesOf, GroundMap.NONE);
        terrain = new TerrainRenderer(ground, Terrain.FLAT, new double[]{0, 0, 0, 0});
        shader = new Shader("surroundings", VERTEX, FRAGMENT);
        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int stride = SurroundGen.Far.STRIDE * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);
        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBindVertexArray(0);
    }

    /** Each ground texture's average colour - the grass, then {@link GroundMap}'s materials - for the far ring. */
    static float[][] tileColours(ScenerySprites.Ground g) {
        float[][] out = new float[GroundMap.MATERIALS + 1][];
        if (g == null) return out;
        out[0] = average(g.texture());
        for (int m = 0; m < GroundMap.MATERIALS; m++) out[m + 1] = average(g.tile(GroundMap.NAMES[m]));
        return out;
    }

    private static float[] average(Path p) {
        if (p == null) return null;
        try {
            BufferedImage im = ImageIO.read(p.toFile());
            if (im == null) return null;
            double r = 0, gr = 0, b = 0;
            int n = 0;
            for (int y = 0; y < im.getHeight(); y += 2) {
                for (int x = 0; x < im.getWidth(); x += 2) {
                    int c = im.getRGB(x, y);
                    r += (c >> 16) & 255;
                    gr += (c >> 8) & 255;
                    b += c & 255;
                    n++;
                }
            }
            // (sRGB averaged as it is: near enough for a colour seen from afar)
            return new float[]{(float) (r / n / 255), (float) (gr / n / 255), (float) (b / n / 255)};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Bring the surroundings up to date for {@code world} - made at once for a
     * level that has none yet, else again off the GL thread a moment after the
     * last change - and say whether there are any to draw. The world is told
     * the ground round it ({@link World#setSurroundGround}).
     */
    public boolean prepare(World world) {
        double[] b = world.bounds();
        Surroundings s = world.surroundings();
        boolean on = s != null && s.any() && b != null && !world.indoors() && world.cover() == Sky.Cover.OPEN
                && b[1] > b[0];
        if (!on) {
            active = false;
            world.setSurroundGround(null);
            return false;
        }
        double size = b[1] - b[0];
        int things = s.theme() == Surroundings.Theme.AUTO
                ? world.scenery().placed().size() * 31 + world.buildings().size() : 0;
        Key key = new Key(s, size, world.terrain(), world.ground(), world.ground().version(), world.water(),
                world.water().version(), world.water().seaLevel(), things);
        long now = System.nanoTime();
        if (!key.equals(wanted)) {
            wanted = key;
            wantedAt = now;
        }
        if (pending != null && pending.isDone()) {
            try {
                show(pending.get(), pendingKey);
            } catch (Exception e) {
                System.err.println("[surroundings] could not make them: " + e);
                shownKey = pendingKey;
            }
            pending = null;
        }
        if (pending == null && !key.equals(shownKey)) {
            boolean another = shownKey == null || shownKey.size() != size || shownKey.ground() != key.ground();
            if (another) {
                long t0 = System.nanoTime();
                show(SurroundGen.generate(input(world, key)), key);
                System.out.println("[surroundings] " + shown.theme().key() + " round " + (int) size + " m: "
                        + shown.scenery().placed().size() + " trees and rocks, " + shown.buildings().size()
                        + " buildings, " + shown.far().triangles() + " far triangles, "
                        + (System.nanoTime() - t0) / 1_000_000 + " ms");
            } else if (now - wantedAt > 350_000_000L) {
                SurroundGen.Input in = input(world, key);
                pendingKey = key;
                pending = maker.submit(() -> SurroundGen.generate(in));
            }
        }
        active = shown != null && shownKey != null && shownKey.size() == size;
        world.setSurroundGround(active ? shown.ground() : null);
        return active;
    }

    /** What to make them from: the level's edge sampled now, on the GL thread. */
    private SurroundGen.Input input(World world, Key key) {
        double half = key.size() / 2;
        SurroundGen.Edge edge = SurroundGen.Edge.of(world.terrain(), world.ground(), world.water(), half,
                Math.max(0.25, key.size() / 1024));
        edge.countNear(world.scenery().placed(), world.buildings(), 24);
        return new SurroundGen.Input(key.settings(), key.size(), edge, world.terrain(), sprites, BuildingCatalog.get(),
                tileColours);
    }

    private void show(SurroundGen.Result r, Key key) {
        shown = r;
        shownKey = key;
        SurroundGen.Far far = r.far();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, far.vertices(), GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, far.indices(), GL_STATIC_DRAW);
        glBindVertexArray(0);
        count = far.indices().length;
        terrain.setHole(r.hole());
        water.setReach(false, r.hole(), Math.max(1, r.ground().step()));
    }

    /** Whether there are surroundings to draw this frame ({@link #prepare}). */
    public boolean active() { return active; }

    /** The surroundings drawn, or null. */
    public SurroundGen.Result shown() { return active ? shown : null; }

    /** The near ring's trees, rocks and grass (none while there are no surroundings). */
    public Scenery scenery() { return active ? shown.scenery() : Scenery.NONE; }

    /** The near ring's houses and haystacks (none while there are no surroundings). */
    public List<Building> buildings() { return active ? shown.buildings() : List.of(); }

    /** The near ring's ground (null while there are no surroundings). */
    public Terrain ground() { return active ? shown.ground() : null; }

    /** The level's square, which the near ring goes round (null while there are no surroundings). */
    public double[] hole() { return active ? shown.hole() : null; }

    /**
     * Draw the far ring from {@code camera} in its own projection, out to the
     * horizon, fading as {@code fog} is ({@code fogDistance} m) and lit as
     * {@code light} has it; then clear the depth for the rest of the world.
     */
    public void drawFar(OrbitCamera camera, double aspect, float[] fog, float fogDistance, DayCycle.Daylight light,
                        SceneLight scene, double time) {
        if (!active || count == 0) return;
        SurroundGen.Far far = shown.far();
        Vec3 eye = camera.eye();
        double reach = far.radius() * 1.5 + Math.abs(eye.y()) + Math.hypot(eye.x(), eye.z());
        Mat4 vp = Mat4.perspective(camera.fovY(), aspect, 1.5, reach).mul(camera.view());
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        shader.use();
        shader.set("uViewProj", vp);
        shader.set("uEye", (float) eye.x(), (float) eye.y(), (float) eye.z());
        shader.set("uHorizon", fog[0], fog[1], fog[2]);
        Vec3 sun = TerrainRenderer.SUN;
        shader.set("uSun", (float) sun.x(), (float) sun.y(), (float) sun.z());
        shader.set("uAmbient", scene.ambient[0], scene.ambient[1], scene.ambient[2]);
        shader.set("uFog", fogDistance);
        Surroundings s = shownKey.settings();
        shader.set("uHaze", (float) s.haze(), (float) Math.max(300, s.far() * 0.8), (float) far.radius());
        shader.set("uLift", (float) far.liftFrom(), (float) far.liftRange());
        shader.set("uNight", (float) Math.max(0, Math.min(1, (0.55 - light.day()) / 0.4)));
        shader.set("uTime", (float) (time % 3600));
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_INT, 0);
        com.larsons.game.core.Perf.drew(0);
        glBindVertexArray(0);
        glClear(GL_DEPTH_BUFFER_BIT);
    }

    /** Draw the near ring's ground, with the level's: fading into {@code fog} as it does. */
    public void drawGround(OrbitCamera camera, Mat4 viewProj, float[] fog, float fogDistance, SceneLight light) {
        if (!active) return;
        ground.use(shown.map());
        terrain.draw(shown.ground(), camera, viewProj, fog, fogDistance, light);
    }

    /** Draw the near ring's water (and the sea round the level), after everything standing, as the level's is. */
    public void drawWater(Vec3 eye, Mat4 viewProj, float[] fog, float fogDistance, DayCycle.Daylight light,
                          SceneLight scene, double time) {
        if (!active) return;
        water.draw(shown.water(), shown.ground(), eye, viewProj, fog, fogDistance, light, scene, time);
    }

    @Override
    public void close() {
        maker.shutdownNow();
        shader.close();
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
        terrain.close();
        water.close();
        ground.close();
    }
}
