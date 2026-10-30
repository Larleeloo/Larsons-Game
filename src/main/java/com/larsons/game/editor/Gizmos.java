package com.larsons.game.editor;

import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Texture;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Terrain;

import static org.lwjgl.opengl.GL33C.*;

/**
 * The level editor's marks in the world, over the frame the world renderer
 * drew: rings on the ground (round what is selected, what the pointer is
 * over, the brush), soft discs (what a brush covers), posts standing up
 * from the ground (markers with no sprite of their own), lines along it (a
 * vine wall's foot, the level's edge). Each lies on the ground as it rises
 * and falls, a few centimetres above it, tested against the scenery's depth
 * so a tree in front hides it.
 */
final class Gizmos implements AutoCloseable {

    /** How far above the ground the marks lie, metres. */
    static final double ABOVE = 0.04;

    private final Batch batch;
    private final Texture white, ring, disc;
    private Terrain terrain = Terrain.FLAT;

    Gizmos(Batch batch) {
        this.batch = batch;
        white = Texture.white();
        ring = Texture.upload(ringPixels(128, 0.82), false);
        disc = Texture.upload(discPixels(64), false);
    }

    /** Start drawing the marks over a frame seen through {@code viewProj}, on {@code terrain}. */
    void begin(Mat4 viewProj, Terrain terrain) {
        this.terrain = terrain;
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        batch.begin(viewProj);
    }

    void end() {
        batch.end();
        glDepthMask(true);
    }

    /** A ring of {@code radius} metres round {@code (x, z)} on the ground, in {@code rgb}. */
    void ring(double x, double z, double radius, int rgb, float alpha) {
        onGround(ring, x, z, radius, rgb, alpha);
    }

    /** A soft disc of {@code radius} metres round {@code (x, z)} on the ground. */
    void disc(double x, double z, double radius, int rgb, float alpha) {
        onGround(disc, x, z, radius, rgb, alpha);
    }

    /** {@code tex} laid on the ground over the square round {@code (x, z)}, in cells that follow it. */
    private void onGround(Texture tex, double x, double z, double radius, int rgb, float alpha) {
        float[] c = rgb(rgb);
        int n = Math.max(2, Math.min(24, (int) Math.ceil(radius * 2 / 0.75)));
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double u0 = i / (double) n, u1 = (i + 1) / (double) n, v0 = j / (double) n, v1 = (j + 1) / (double) n;
                double x0 = x - radius + u0 * radius * 2, x1 = x - radius + u1 * radius * 2;
                double z0 = z - radius + v0 * radius * 2, z1 = z - radius + v1 * radius * 2;
                batch.quad(tex, (float) x0, y(x0, z0), (float) z0, (float) x1, y(x1, z0), (float) z0,
                        (float) x1, y(x1, z1), (float) z1, (float) x0, y(x0, z1), (float) z1,
                        (float) u0, (float) v0, (float) u1, (float) v1, c[0], c[1], c[2], alpha);
            }
        }
    }

    private float y(double x, double z) {
        return (float) (terrain.height(x, z) + ABOVE);
    }

    /** A line along the ground from {@code (x0, z0)} to {@code (x1, z1)}, {@code width} metres wide. */
    void line(double x0, double z0, double x1, double z1, double width, int rgb, float alpha) {
        float[] c = rgb(rgb);
        double len = Math.hypot(x1 - x0, z1 - z0);
        if (len < 1e-6) return;
        double nx = -(z1 - z0) / len * width / 2, nz = (x1 - x0) / len * width / 2;
        int n = Math.max(1, (int) Math.ceil(len / 0.75));
        for (int k = 0; k < n; k++) {
            double a = k / (double) n, b = (k + 1) / (double) n;
            double ax = x0 + (x1 - x0) * a, az = z0 + (z1 - z0) * a, bx = x0 + (x1 - x0) * b, bz = z0 + (z1 - z0) * b;
            batch.quad(white, (float) (ax + nx), y(ax + nx, az + nz), (float) (az + nz),
                    (float) (bx + nx), y(bx + nx, bz + nz), (float) (bz + nz),
                    (float) (bx - nx), y(bx - nx, bz - nz), (float) (bz - nz),
                    (float) (ax - nx), y(ax - nx, az - nz), (float) (az - nz), 0, 0, 1, 1, c[0], c[1], c[2], alpha);
        }
    }

    /** A post {@code height} metres tall standing on {@code foot}, facing {@code camera}, with a flag. */
    void post(Vec3 foot, double height, int rgb, float alpha, OrbitCamera camera) {
        float[] c = rgb(rgb);
        Vec3 right = camera.right();
        double w = 0.07;
        Vec3 top = foot.add(0, height, 0);
        batch.quad(white, top.sub(right.scale(w)), right.scale(w * 2), new Vec3(0, -height, 0), 0, 0, 1, 1,
                c[0], c[1], c[2], alpha);
        Vec3 flag = top.add(right.scale(w));
        batch.quad(white, flag, right.scale(0.42), new Vec3(0, -0.28, 0), 0, 0, 1, 1, c[0], c[1], c[2], alpha);
    }

    /** A thin upright line from {@code a} to {@code b} (an arrow's shaft, a flier's height), facing the camera. */
    void upright(Vec3 a, Vec3 b, double width, int rgb, float alpha, OrbitCamera camera) {
        float[] c = rgb(rgb);
        Vec3 right = camera.right().scale(width / 2);
        batch.quad(white, b.sub(right), right.scale(2), a.sub(b), 0, 0, 1, 1, c[0], c[1], c[2], alpha);
    }

    static float[] rgb(int rgb) {
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    /** A white ring, {@code inner} of the way out to its edge, soft at both rims. */
    static PixelData ringPixels(int n, double inner) {
        int[] argb = new int[n * n];
        double soft = 2.5 / n;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r = Math.sqrt(dx * dx + dy * dy);
                double a = clamp((1 - r) / soft) * clamp((r - inner) / soft);
                argb[y * n + x] = ((int) Math.round(a * 255) << 24) | 0xFFFFFF;
            }
        }
        return PixelData.of(argb, n, n);
    }

    /** A white disc, fading out towards its rim. */
    static PixelData discPixels(int n) {
        int[] argb = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r2 = dx * dx + dy * dy;
                double a = r2 >= 1 ? 0 : 0.35 + 0.65 * (1 - r2);
                argb[y * n + x] = ((int) Math.round(clamp(a) * 255) << 24) | 0xFFFFFF;
            }
        }
        return PixelData.of(argb, n, n);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    @Override
    public void close() {
        white.close();
        ring.close();
        disc.close();
    }
}
