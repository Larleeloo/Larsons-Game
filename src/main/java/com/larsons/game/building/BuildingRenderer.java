package com.larsons.game.building;

import com.larsons.game.gfx.SceneLight;
import com.larsons.game.gfx.Shader;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.PixelPalette;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.world.OrbitCamera;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws buildings. Each of a building's 24 views is not a picture but what
 * the picture is made of, pixel by pixel ({@link BuildingProfile}): which
 * part, how it is shaded, where on the part, how deep. Here every pixel is
 * coloured from its part's {@link BuildingLook#ramp} with the part's pattern
 * laid over it, lit by the scene's light (the windows glowing at night), and
 * written into the depth buffer at the depth of the point of the building it
 * shows - so she is hidden by the corner of a house she walks behind, and
 * walks in front of the rest of it, as with something solid.
 *
 * <p>A view is read from {@code assets/buildings/<kind>/} the first time it
 * is drawn, and kept.
 */
public final class BuildingRenderer implements AutoCloseable {

    /** The most parts a building has (a pixel's part is four bits). */
    public static final int MAX_PARTS = 15;

    private static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec2 aUv;
            uniform mat4 uViewProj;
            out vec2 vUv;
            out float vW;
            uniform vec2 uShift;           // nudged on the screen (an outline)
            void main() {
                gl_Position = uViewProj * vec4(aPos, 1.0);
                vUv = aUv;
                vW = gl_Position.w;
                """ + Outline.SHIFT + """
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            in float vW;
            uniform mat4 uViewProj;
            uniform sampler2D uView;
            uniform sampler2DArray uPatterns;
            uniform ivec2 uFrame;          // the view's size, pixels
            uniform int uFrameX;           // where its frame starts in its strip
            uniform vec2 uAnchorPx;        // its ground point's pixel
            uniform float uPixel;          // metres a pixel
            uniform vec2 uDepth;           // the depth an alpha of 1 is, metres a step
            uniform vec3 uAnchor;          // its ground point in the world
            uniform vec3 uVRight, uVUp, uVFwd;   // the view's camera, in the world
            uniform vec3 uRamp[90];        // each part's six colours
            uniform int uLayer[15];        // each part's pattern
            uniform float uGlow[15];       // how lit from inside (a window at night); 2: always (a fire)
            uniform vec3 uFogColor;
            uniform vec2 uFog;
            uniform vec4 uSolid;           // one colour (an outline), or 0: as it is
            out vec4 fragColor;
            """ + SceneLight.GLSL + """
            void main() {
                ivec2 p = clamp(ivec2(floor(vUv * vec2(uFrame))), ivec2(0), uFrame - 1);
                vec4 g = texelFetch(uView, ivec2(p.x + uFrameX, p.y), 0);
                int r = int(g.r * 255.0 + 0.5);
                int part = r >> 4;
                int shade = r & 15;
                if (part == 0) discard;
                int u = int(g.g * 255.0 + 0.5), v = int(g.b * 255.0 + 0.5), a = int(g.a * 255.0 + 0.5);
                int k = part - 1;
                int tone = int(texelFetch(uPatterns, ivec3(u & 63, v & 63, uLayer[k]), 0).r * 3.0 + 0.5);
                int idx;
                if (shade == 1) idx = 0;
                else if (shade == 2) idx = 1;
                else idx = clamp((shade == 3 ? 2 : shade == 4 ? 3 : 4) + tone - 2, 1, 5);
                vec3 c = uRamp[k * 6 + idx];
                // the point of the building this pixel shows
                vec2 m = vec2((float(p.x) + 0.5 - uAnchorPx.x) * uPixel, (uAnchorPx.y - float(p.y) - 0.5) * uPixel);
                float d = uDepth.x + float(max(a, 1) - 1) * uDepth.y;
                vec3 P = uAnchor + uVRight * m.x + uVUp * m.y + uVFwd * d;
                // what is under its ground (the footings) is not drawn: it stands on the ground in every view
                if (P.y < uAnchor.y - 0.06) discard;
                vec4 clip = uViewProj * vec4(P, 1.0);
                gl_FragDepth = clip.w > 1e-4 ? clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0) : 0.0;
                vec3 lit = c * sceneLight(P);
                float glow = min(uGlow[k], 1.0);
                if (uGlow[k] > 1.5) {
                    lit = max(lit, c * 1.12);
                    glow = 0.0;
                } else if (glow > 0.0 && shade > 2) {
                    vec3 warm = vec3(1.0, 0.8, 0.46) * (0.75 + 0.25 * float(idx) / 5.0);
                    lit = mix(lit, warm, glow);
                }
                float mist = sceneMist(P);
                lit = mix(lit, uMistColour, mist * (1.0 - glow * 0.7));
                if (uFog.y > 0.0) {
                    float f = clamp((vW - uFog.x) / (uFog.y - uFog.x), 0.0, 1.0);
                    lit = mix(lit, uFogColor, f * f);
                }
                fragColor = uSolid.a > 0.0 ? vec4(uSolid.rgb, 1.0) : vec4(lit, 1.0);
            }
            """;

    /** A view uploaded: its texture, its size. */
    private record View(int texture, int width, int height) {}

    private static final View MISSING = new View(0, 0, 0);

    private final BuildingCatalog catalog;
    private final PixelPalette palette;
    private final Shader shader;
    private final int vao, vbo, patterns;
    private final Map<String, View> views = new HashMap<>();
    private final FloatBuffer quad = MemoryUtil.memAllocFloat(4 * 5);
    private final float[] ramp = new float[MAX_PARTS * 6 * 3];
    private final int[] layers = new int[MAX_PARTS];
    private final float[] glow = new float[MAX_PARTS];
    private final RoomRenderer rooms;
    private final ModelRenderer models;
    private float night;

    /** Drawn from {@code catalog}'s folder, in {@code palette}'s colours. Must be made on the GL thread. */
    public BuildingRenderer(BuildingCatalog catalog, PixelPalette palette) {
        this.catalog = catalog;
        this.palette = palette;
        this.shader = new Shader("buildings", VERTEX, FRAGMENT);
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 4L * 5 * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);
        patterns = uploadPatterns(catalog);
        rooms = new RoomRenderer(catalog, palette, patterns);
        models = new ModelRenderer(catalog, palette, patterns);
    }

    /** The patterns, a layer each, their tone in red: nearest, repeating. */
    private static int uploadPatterns(BuildingCatalog catalog) {
        List<BuildingCatalog.Texture> list = catalog.textures();
        int n = Math.max(1, list.size()), size = 64;
        ByteBuffer buf = MemoryUtil.memCalloc(size * size * 4 * n);
        try {
            for (int i = 0; i < list.size(); i++) {
                Path f = catalog.root().resolve("textures").resolve(list.get(i).name() + ".png");
                try {
                    BufferedImage img = ImageIO.read(f.toFile());
                    if (img == null || img.getWidth() != size || img.getHeight() != size) continue;
                    Raster r = img.getRaster();
                    for (int y = 0; y < size; y++) {
                        for (int x = 0; x < size; x++) {
                            int v = r.getSample(x, y, 0);
                            int o = ((i * size + y) * size + x) * 4;
                            buf.put(o, (byte) v).put(o + 1, (byte) v).put(o + 2, (byte) v).put(o + 3, (byte) 255);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("[buildings] " + f + ": " + e.getMessage());
                }
            }
            int id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D_ARRAY, id);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, size, size, n, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);
            return id;
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    /** A view of {@code key}, uploaded the first time it is asked for; null when there is none. */
    private View view(String key, Elevation e, Facing f, boolean anim) {
        String name = key + "/" + e.key() + "_" + f.key() + (anim ? "_anim" : "");
        View v = views.get(name);
        if (v == null) {
            v = load(catalog.root().resolve(key).resolve(e.key() + "_" + f.key() + (anim ? "_anim" : "") + ".png"));
            views.put(name, v);
        }
        return v == MISSING ? null : v;
    }

    /** Read a view as it is - its four channels are numbers, not a colour - and upload it. */
    private static View load(Path file) {
        if (!Files.isRegularFile(file)) return MISSING;
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) return MISSING;
            int w = img.getWidth(), h = img.getHeight();
            Raster r = img.getRaster();
            int bands = r.getNumBands();
            ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
            try {
                int[] px = new int[bands];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        r.getPixel(x, y, px);
                        buf.put((byte) px[0]).put((byte) (bands > 1 ? px[1] : 0)).put((byte) (bands > 2 ? px[2] : 0))
                                .put((byte) (bands > 3 ? px[3] : 255));
                    }
                }
                buf.flip();
                int id = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, id);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                return new View(id, w, h);
            } finally {
                MemoryUtil.memFree(buf);
            }
        } catch (IOException e) {
            System.err.println("[buildings] " + file + ": " + e.getMessage());
            return MISSING;
        }
    }

    /** Read every view of the kinds in {@code keys} now, rather than as they are first seen. */
    public void preload(Iterable<String> keys, Elevation e) {
        for (String k : keys) {
            BuildingProfile p = catalog.profile(k);
            if (p == null) continue;
            for (Facing f : Facing.values()) {
                view(k, e, f, false);
                if (p.anim() != null) view(k, e, f, true);
            }
        }
    }

    /** The directions' angles, as the Blender camera was turned for them (degrees). */
    static double directionDegrees(Facing f) {
        return switch (f) {
            case SOUTH -> 0;
            case SOUTH_EAST -> 45;
            case EAST -> 90;
            case NORTH_EAST -> 135;
            case NORTH -> 180;
            case NORTH_WEST -> -135;
            case WEST -> -90;
            case SOUTH_WEST -> -45;
        };
    }

    /**
     * The view's camera in the building's own metres - right, up, forward -
     * as {@code buildings.py} turned it: about the up axis by the direction,
     * then tipped down by the elevation.
     */
    static double[][] viewBasis(Elevation e, Facing f) {
        double a = Math.toRadians(-directionDegrees(f)), el = Math.toRadians(e.renderAngle());
        double ca = Math.cos(a), sa = Math.sin(a), ce = Math.cos(el), se = Math.sin(el);
        // the camera's turn is Rz(a) Rx(90 - el); its columns are its right, up and back
        double[] right = {ca, sa, 0};
        double[] up = {-sa * se, ca * se, ce};
        double[] back = {sa * ce, -ca * ce, se};
        return new double[][]{right, up, {-back[0], -back[1], -back[2]}};
    }

    /** How rooms' walls ({@code indoors}) and buildings' ({@code outdoors}) stand: up, cut away, or down. */
    public void setWalls(Walls indoors, Walls outdoors) {
        rooms.walls = indoors == null ? Walls.CUTAWAY : indoors;
        models.walls = outdoors == null ? Walls.UP : outdoors;
    }

    /**
     * Draw {@code buildings} seen by {@code camera}, opaque, writing depth -
     * after the ground, before what stands among them. {@code night} 0 – 1
     * lights their windows; {@code time} turns the sails and wheels;
     * {@code highlight} (or null) is drawn lit up (the editor's selection).
     * The pieces of rooms among them are drawn as they are ({@link
     * RoomRenderer}): those between the eye and {@code focus} cut down.
     */
    public void draw(List<Building> buildings, OrbitCamera camera, Vec3 focus, Mat4 viewProj, float[] fog,
                     float fogFar, SceneLight light, double night, double time, Set<Integer> highlight) {
        if (buildings.isEmpty()) return;
        rooms.draw(buildings, camera, focus, viewProj, fog, fogFar, light, 1 - night, highlight);
        // those with a 3D model as it; the rest (a barrel, a crate - and any kind with no model) from their views
        int modelled = models.draw(buildings, camera, focus, viewProj, fog, fogFar, light, night, time, highlight);
        if (modelled == buildings.size()) return;
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        shader.use();
        shader.set("uViewProj", viewProj);
        shader.set("uView", 0);
        shader.set("uPatterns", 1);
        this.night = (float) Math.max(0, Math.min(1, night));
        shader.set("uFogColor", fog[0], fog[1], fog[2]);
        shader.set("uFog", fogFar * 0.35f, fogFar);
        light.bind(shader);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D_ARRAY, patterns);
        glBindVertexArray(vao);
        Vec3 eye = camera.eye(), right = camera.right(), up = camera.up();
        double reachSq = Math.pow(fogFar + 30, 2);
        List<Building> picked = new ArrayList<>();
        for (Building b : buildings) {
            BuildingProfile p = b.profile();
            if (p.room() != null || models.has(p)) continue;
            Vec3 at = b.position();
            if (at.sub(eye).horizontalLength() > Math.sqrt(reachSq)) continue;
            if (drawView(b, eye, right, up, camera, time) && highlight != null && highlight.contains(b.id())) {
                picked.add(b);
            }
        }
        Outline.draw(shader, picked, b -> drawView(b, eye, right, up, camera, time));
        glBindVertexArray(0);
    }

    /** {@code b} drawn from its view, its turning part too; false: it has none. */
    private boolean drawView(Building b, Vec3 eye, Vec3 right, Vec3 up, OrbitCamera camera, double time) {
        BuildingProfile p = b.profile();
        Vec3 at = b.position();
        SpriteView sv = SpriteView.of(eye, at.add(0, Math.min(3, p.height() * 0.4), 0), b.heading(), camera.yaw());
        Elevation e = sv.elevation();
        BuildingProfile.Frame fr = p.frame(e);
        if (fr == null) return false;
        View body = view(p.key(), e, sv.facing(), false);
        if (body == null) return false;
        setLook(b);
        setView(b, e, sv.facing(), fr);
        drawQuad(body, fr, p.pixel(), 0, at, right, up);
        BuildingProfile.Anim anim = p.anim();
        if (anim != null && anim.frames() > 0) {
            View strip = view(p.key(), e, sv.facing(), true);
            if (strip != null) {
                double turned = (time * anim.speed()) % anim.degrees();
                int f = (int) Math.floor(turned / anim.degrees() * anim.frames()) % anim.frames();
                drawQuad(strip, fr, p.pixel(), f * fr.width(), at, right, up);
            }
        }
        return true;
    }

    private void setLook(Building b) {
        List<BuildingProfile.Part> parts = b.profile().parts();
        int[][] ramps = b.ramps(palette);
        for (int i = 0; i < MAX_PARTS; i++) {
            int[] rr = i < ramps.length ? ramps[i] : ramps[ramps.length - 1];
            for (int k = 0; k < 6; k++) {
                int c = rr[k];
                int o = (i * 6 + k) * 3;
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

    private void setView(Building b, Elevation e, Facing f, BuildingProfile.Frame fr) {
        double[][] v = viewBasis(e, f);
        Vec3 ax = b.axisX(), ay = b.axisY();
        Vec3[] w = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            w[i] = ax.scale(v[i][0]).add(ay.scale(v[i][1])).add(0, v[i][2], 0);
        }
        BuildingProfile p = b.profile();
        Vec3 at = b.position();
        shader.set("uAnchor", (float) at.x(), (float) at.y(), (float) at.z());
        shader.set("uVRight", (float) w[0].x(), (float) w[0].y(), (float) w[0].z());
        shader.set("uVUp", (float) w[1].x(), (float) w[1].y(), (float) w[1].z());
        shader.set("uVFwd", (float) w[2].x(), (float) w[2].y(), (float) w[2].z());
        shader.set("uPixel", (float) p.pixel());
        shader.set("uAnchorPx", (float) fr.anchorX(), (float) fr.anchorY());
        shader.set("uDepth", (float) p.depthMin(), (float) p.depthStep());
        shader.set2i("uFrame", fr.width(), fr.height());
    }

    private void drawQuad(View v, BuildingProfile.Frame fr, double pixel, int frameX, Vec3 at, Vec3 right, Vec3 up) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, v.texture());
        shader.set("uFrameX", frameX);
        double x0 = -fr.anchorX() * pixel, x1 = (fr.width() - fr.anchorX()) * pixel;
        double y0 = (fr.anchorY() - fr.height()) * pixel, y1 = fr.anchorY() * pixel;
        quad.clear();
        put(at, right, up, x0, y1, 0, 0);
        put(at, right, up, x1, y1, 1, 0);
        put(at, right, up, x0, y0, 0, 1);
        put(at, right, up, x1, y0, 1, 1);
        quad.flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, quad);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        com.larsons.game.core.Perf.drew(0);
    }

    private void put(Vec3 at, Vec3 right, Vec3 up, double x, double y, float u, float v) {
        quad.put((float) (at.x() + right.x() * x + up.x() * y))
                .put((float) (at.y() + right.y() * x + up.y() * y))
                .put((float) (at.z() + right.z() * x + up.z() * y))
                .put(u).put(v);
    }

    @Override
    public void close() {
        rooms.close();
        models.close();
        for (View v : views.values()) if (v != MISSING) glDeleteTextures(v.texture());
        views.clear();
        glDeleteTextures(patterns);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        shader.close();
        MemoryUtil.memFree(quad);
    }
}
