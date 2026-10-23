package com.larsons.game.world;

import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Shader;
import com.larsons.game.gfx.Texture;
import com.larsons.game.sprite.ScenerySprites;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.lwjgl.opengl.GL33C.*;

/**
 * The ground and the sky: an endless meadow of grass fading into a soft sky
 * at the horizon — drawn in one full-screen pass on the GPU.
 *
 * <p>Every pixel casts its own view ray and intersects it with the ground
 * plane {@code y = 0} in the fragment shader, so the ground has no edge, no
 * tessellation and a perfectly straight horizon at any zoom. It writes no
 * depth: it is the background, and nothing should ever be hidden behind it
 * (the toes of a 45° sprite dip below its plane).
 *
 * <p><b>The grass</b> is {@code assets/scenery/ground/grass.png}, a 512 ×
 * 512 pixel-art tile in the game's palette, repeated every {@code
 * tileWorldSize} metres ({@code ground/profile.json}; 9.6 m puts its pixels
 * at the characters' 1.875 cm). It is a {@link Texture#tiled} texture -
 * crisp pixels underfoot, mipmapped (and anisotropic, where the driver has
 * it) towards the horizon, so it never shimmers far away. Without the file
 * the ground is the plain studio floor it was before: pale, with a metre
 * grid and the two axes.
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
            uniform sampler2D uGrass;
            uniform float uTile;          // metres a grass tile spans; 0: no grass, the studio floor
            out vec4 fragColor;

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

            void main() {
                vec3 dir = normalize(uForward + vNdc.x * uTanHalf.x * uRight
                                              + vNdc.y * uTanHalf.y * uUp);
                vec3 zenith = vec3(0.36, 0.47, 0.64);
                vec3 sky = mix(uHorizon, zenith, pow(clamp(dir.y, 0.0, 1.0), 0.6));

                // (the grass is sampled before any early exit: its mip level needs the neighbours' coordinates)
                float t = dir.y < -1e-4 ? -uEye.y / dir.y : 0.0;
                vec3 p = uEye + dir * t;
                vec3 grass = uTile > 0.0 ? texture(uGrass, p.xz / uTile).rgb : vec3(0.0);

                if (dir.y >= -1e-4 || uEye.y <= 0.0) {
                    fragColor = vec4(sky, 1.0);
                    return;
                }
                vec3 c = uTile > 0.0 ? grass : studioFloor(p);
                float fog = 1.0 - exp(-pow(t / uFogDistance, 2.0));
                fragColor = vec4(mix(c, uHorizon, fog), 1.0);
            }
            """;

    private final Shader shader;
    private final int vao;
    private final Texture grass;
    private final float tile;

    /** The ground drawn with {@code ground}'s grass, or the studio floor when it is null or will not load. */
    public VoidRenderer(ScenerySprites.Ground ground) {
        shader = new Shader("void", VERTEX, FRAGMENT);
        vao = glGenVertexArrays();
        Texture loaded = null;
        if (ground != null) {
            try {
                BufferedImage image = ImageIO.read(ground.texture().toFile());
                if (image == null) throw new IOException("not an image");
                loaded = Texture.tiled(PixelData.of(image));
                System.out.println("[scenery] ground: " + image.getWidth() + "x" + image.getHeight()
                        + " grass, a tile every " + ground.tileWorldSize() + " m");
            } catch (IOException | RuntimeException e) {
                System.err.println("[scenery] cannot load " + ground.texture() + ": " + e.getMessage()
                        + " - the plain floor instead");
            }
        }
        grass = loaded;
        tile = loaded == null ? 0 : (float) ground.tileWorldSize();
    }

    /** Whether the ground is grass (false: the studio floor). */
    public boolean grass() { return grass != null; }

    public void draw(OrbitCamera camera, double aspect, float fogDistance) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
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
        shader.set("uGrass", 0);
        shader.set("uTile", tile);
        if (grass != null) grass.bind(0);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
        glDepthMask(true);
    }

    private void set(String name, com.larsons.game.math.Vec3 v) {
        shader.set(name, (float) v.x(), (float) v.y(), (float) v.z());
    }

    @Override
    public void close() {
        shader.close();
        if (grass != null) grass.close();
        glDeleteVertexArrays(vao);
    }
}
