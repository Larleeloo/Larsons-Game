package com.larsons.game.world;

import com.larsons.game.gfx.Mesh;
import com.larsons.game.gfx.MeshBuilder;
import com.larsons.game.gfx.Shader;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33C.*;

/**
 * The environment: trees, rocks and a house, modelled and drawn in real 3D —
 * unlike the characters and items, which are sprites. Low-poly and
 * flat-shaded, placed around the edge of the void so the middle stays clear
 * for looking at the character.
 *
 * <p>They are here to show the two worlds meeting: sprites stand behind and in
 * front of real geometry and are occluded by it through the depth buffer.
 */
public final class Props implements AutoCloseable {

    /** One placed object: its mesh, where it stands, and how big its ground shadow is. */
    public record Placed(Mesh mesh, Vec3 position, double rotation, double shadowRadius) {}

    private static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec3 aColor;
            uniform mat4 uViewProj;
            uniform mat4 uModel;
            out vec3 vNormal;
            out vec3 vColor;
            out float vDepth;
            void main() {
                gl_Position = uViewProj * uModel * vec4(aPos, 1.0);
                vNormal = mat3(uModel) * aNormal;
                vColor = aColor;
                vDepth = gl_Position.w;
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec3 vNormal;
            in vec3 vColor;
            in float vDepth;
            uniform vec3 uSun;
            uniform vec3 uFogColor;
            uniform float uFogDistance;
            out vec4 fragColor;
            void main() {
                vec3 n = normalize(vNormal);
                vec3 sky = vec3(0.62, 0.68, 0.78), ground = vec3(0.40, 0.38, 0.36);
                vec3 ambient = mix(ground, sky, n.y * 0.5 + 0.5) * 0.62;
                vec3 sun = vec3(1.0, 0.95, 0.86) * max(dot(n, uSun), 0.0) * 0.62;
                vec3 c = vColor * (ambient + sun);
                float fog = 1.0 - exp(-pow(vDepth / uFogDistance, 2.0));
                fragColor = vec4(mix(c, uFogColor, fog), 1.0);
            }
            """;

    private final Shader shader = new Shader("props", VERTEX, FRAGMENT);
    private final List<Placed> placed = new ArrayList<>();
    private final List<Mesh> meshes = new ArrayList<>();

    public Props() {
        Mesh tree = keep(tree(0x5A3C28, 0x3F7A45, 11));
        Mesh pine = keep(pine(0x5A3C28, 0x2F6446));
        Mesh rock = keep(new MeshBuilder()
                .blob(new Vec3(0, 0.45, 0), 0.9, 0.62, 0.75, 0.22, 7, 0x8A8C90).build());
        Mesh boulder = keep(new MeshBuilder()
                .blob(new Vec3(0, 0.25, 0), 0.45, 0.35, 0.4, 0.25, 3, 0x7D7F86).build());
        Mesh house = keep(house());

        placed.add(new Placed(house, new Vec3(-8, 0, -11), Math.toRadians(25), 3.6));
        placed.add(new Placed(tree, new Vec3(6.5, 0, -8), 0, 1.4));
        placed.add(new Placed(pine, new Vec3(9.5, 0, -3), 0.7, 1.2));
        placed.add(new Placed(tree, new Vec3(-10, 0, 2.5), 1.9, 1.4));
        placed.add(new Placed(pine, new Vec3(-5.5, 0, 9), 2.4, 1.2));
        placed.add(new Placed(tree, new Vec3(7, 0, 8), 3.0, 1.4));
        placed.add(new Placed(rock, new Vec3(3.6, 0, -3.4), 0.4, 1.0));
        placed.add(new Placed(boulder, new Vec3(-3.2, 0, 4.2), 1.1, 0.55));
        placed.add(new Placed(boulder, new Vec3(4.4, 0, 3.1), 2.2, 0.55));
    }

    private Mesh keep(Mesh m) {
        meshes.add(m);
        return m;
    }

    public List<Placed> placed() { return placed; }

    /** A broadleaf tree: a tapering trunk under three lumpy canopy blobs. */
    private static Mesh tree(int bark, int leaves, long seed) {
        return new MeshBuilder()
                .cylinder(Vec3.ZERO, 0.22, 0.14, 2.2, 7, bark)
                .blob(new Vec3(0, 2.9, 0), 1.35, 1.1, 1.35, 0.18, seed, leaves)
                .blob(new Vec3(0.6, 2.4, 0.3), 0.8, 0.7, 0.8, 0.2, seed + 1, leaves - 0x061006)
                .blob(new Vec3(-0.5, 2.5, -0.4), 0.85, 0.7, 0.8, 0.2, seed + 2, leaves + 0x040A04)
                .build();
    }

    /** A pine: a trunk and three stacked cones. */
    private static Mesh pine(int bark, int needles) {
        return new MeshBuilder()
                .cylinder(Vec3.ZERO, 0.16, 0.12, 1.2, 6, bark)
                .cylinder(new Vec3(0, 0.9, 0), 1.3, 0, 1.6, 8, needles)
                .cylinder(new Vec3(0, 1.8, 0), 1.0, 0, 1.4, 8, needles + 0x050A05)
                .cylinder(new Vec3(0, 2.6, 0), 0.7, 0, 1.3, 8, needles + 0x0A140A)
                .build();
    }

    /** A cottage: walls, a pitched roof, a door, windows and a chimney. */
    private static Mesh house() {
        MeshBuilder b = new MeshBuilder();
        int wall = 0xD8CBB0, trim = 0x6B4A32, roof = 0xA0463A, glass = 0x46607A;
        b.box(Mat4.translation(0, 1.3, 0), 2.6, 1.3, 2.0, wall);
        b.box(Mat4.translation(0, 0.08, 0), 2.7, 0.08, 2.1, 0x8E8578);           // footing
        b.roof(Mat4.translation(0, 2.6, 0), 3.0, 1.6, 2.35, roof, wall);
        b.box(Mat4.translation(1.4, 3.6, -0.6), 0.28, 0.75, 0.28, 0x8C5A4A);     // chimney
        b.box(Mat4.translation(0, 0.95, 2.02), 0.5, 0.95, 0.04, trim);           // door
        b.box(Mat4.translation(0.3, 0.95, 2.07), 0.05, 0.05, 0.02, 0xD7AA3C);    // door knob
        for (int side : new int[]{-1, 1}) {
            b.box(Mat4.translation(side * 1.6, 1.5, 2.02), 0.45, 0.4, 0.04, trim);
            b.box(Mat4.translation(side * 1.6, 1.5, 2.05), 0.36, 0.31, 0.02, glass);
            b.box(Mat4.translation(2.62, 1.5, side * 0.9), 0.04, 0.4, 0.45, trim);
            b.box(Mat4.translation(2.65, 1.5, side * 0.9), 0.02, 0.31, 0.36, glass);
        }
        return b.build();
    }

    /** Draw every prop with depth test and write on, back faces culled. */
    public void draw(com.larsons.game.math.Mat4 viewProj, float[] fogColor, float fogDistance) {
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDepthFunc(GL_LEQUAL);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        shader.use();
        shader.set("uViewProj", viewProj);
        Vec3 sun = new Vec3(-0.45, 0.8, 0.4).normalize();
        shader.set("uSun", (float) sun.x(), (float) sun.y(), (float) sun.z());
        shader.set("uFogColor", fogColor[0], fogColor[1], fogColor[2]);
        shader.set("uFogDistance", fogDistance);
        for (Placed p : placed) {
            shader.set("uModel", Mat4.translation(p.position().x(), p.position().y(), p.position().z())
                    .mul(Mat4.rotationY(p.rotation())));
            p.mesh().draw();
        }
        glDisable(GL_CULL_FACE);
    }

    @Override
    public void close() {
        for (Mesh m : meshes) m.close();
        shader.close();
    }
}
