package com.larsons.game.world;

import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Texture;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.ScenerySprites;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws the {@link Scenery}: every tree and tuft as two cards crossing at
 * right angles through its axis, both showing its frame of the moment, the
 * frame's bottom edge on the ground and {@code frameWorldSize} metres
 * across - and every rock as one such card turned to face the camera
 * ({@link SceneryProfile#facesCamera()}), as the characters and the items
 * lying about are: a low, wide rock drawn as a cross is an X seen from
 * above. A rock's card is tested and written at the depth of an upright card
 * on its spot ({@link Batch#setDepthPlane}), the same depth the characters
 * are tested at, so she is in front of it or behind it as her feet are.
 *
 * <p>The cards are <b>cutouts</b>: drawn through the sprite {@link Batch}
 * with its alpha cut at {@value #CUT}, so every texel of the pixel art is
 * either there or not, and with depth test <em>and</em> depth write on, so
 * they stand in the depth buffer as solid as real geometry - the character
 * (a billboard drawn later, depth-tested) walks behind a tree or stands in a
 * tuft and is hidden by it texel for texel, and their order does not matter.
 * No face culling: the back of a card is the picture mirrored, as Minecraft's
 * plants are. They take the batch's fog, like the sprites.
 *
 * <p>Every sheet is uploaded once, when the game starts (all of them together
 * are a few megabytes), as a {@link Texture#cutout} - nearest-neighbour, the
 * pixel art's hard edges at any distance, but mipmapped so the far grass does
 * not sparkle. All the crosses of one sprite come from its one sheet, so the
 * whole meadow is a draw call per sprite.
 */
public final class SceneryRenderer implements AutoCloseable {

    /** The alpha cut the cards are drawn with: a texel is in or out. */
    public static final float CUT = 0.5f;

    private final Map<String, Texture> sheets = new TreeMap<>();

    /** Upload every sheet of {@code sprites}. Must be made on the GL thread. */
    public SceneryRenderer(ScenerySprites sprites) {
        for (ScenerySprites.Sprite s : sprites.sprites()) {
            try {
                BufferedImage image = ImageIO.read(s.sheet().toFile());
                if (image == null) throw new IOException("not an image");
                sheets.put(s.name(), Texture.cutout(PixelData.of(image), s.profile().frameWidth(),
                        s.profile().frameHeight()));
            } catch (IOException | RuntimeException e) {
                System.err.println("[scenery] cannot load " + s.sheet() + ": " + e.getMessage());
            }
        }
        if (!sheets.isEmpty()) System.out.println("[scenery] " + String.join(", ", sheets.keySet()));
    }

    /** Whether there is any scenery to draw. */
    public boolean isEmpty() { return sheets.isEmpty(); }

    /**
     * Draw everything in {@code scenery} as it stands at world time {@code
     * time}, seen from {@code camera}; {@code uprightNormal} is the way the
     * characters' upright depth planes face (the rocks are tested on the
     * same planes).
     */
    public void draw(Batch batch, Scenery scenery, Mat4 viewProj, float[] fogColor, float fogFar, double time,
                     OrbitCamera camera, Vec3 uprightNormal) {
        if (scenery.isEmpty() || sheets.isEmpty()) return;
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        batch.begin(viewProj, fogColor, 0, fogFar);
        batch.setAlphaCut(CUT);
        batch.setDepthPlane(camera.eye(), uprightNormal);
        Vec3 right = camera.right(), up = camera.up();
        for (Map.Entry<ScenerySprites.Sprite, List<Scenery.Placed>> e : scenery.bySprite().entrySet()) {
            Texture sheet = sheets.get(e.getKey().name());
            if (sheet == null) continue;
            boolean facing = e.getKey().profile().facesCamera();
            for (Scenery.Placed p : e.getValue()) {
                if (facing) {
                    batch.depthAnchor(p.position());
                    facingCamera(batch, sheet, p, time, right, up);
                } else {
                    batch.depthAnchor(null);
                    cross(batch, sheet, p, time);
                }
            }
        }
        batch.depthAnchor(null);
        batch.setAlphaCut(Batch.DEFAULT_ALPHA_CUT);
        batch.end();
    }

    /** One card facing the camera, its bottom edge's middle on the spot, leaning back with the view. */
    private static void facingCamera(Batch batch, Texture sheet, Scenery.Placed p, double time, Vec3 right, Vec3 up) {
        SceneryProfile profile = p.profile();
        float[] uv = p.sprite().uv(p.frame(time));
        float u0 = p.mirrored() ? uv[2] : uv[0], u1 = p.mirrored() ? uv[0] : uv[2];
        double w = profile.frameWorldSize(), h = profile.frameWorldHeight();
        Vec3 topLeft = p.position().sub(right.scale(w / 2)).add(up.scale(h));
        batch.quad(sheet, topLeft, right.scale(w), up.scale(-h), u0, uv[1], u1, uv[3], 1, 1, 1, 1);
    }

    /** One cross: a card along its yaw and one at right angles to it, the same frame on both. */
    private static void cross(Batch batch, Texture sheet, Scenery.Placed p, double time) {
        SceneryProfile profile = p.profile();
        float[] uv = p.sprite().uv(p.frame(time));
        float u0 = p.mirrored() ? uv[2] : uv[0], u1 = p.mirrored() ? uv[0] : uv[2];
        double half = profile.frameWorldSize() / 2;
        float h = (float) profile.frameWorldHeight();
        double c = Math.cos(p.yaw()) * half, s = Math.sin(p.yaw()) * half;
        card(batch, sheet, p.x(), p.z(), c, s, h, u0, uv[1], u1, uv[3]);
        card(batch, sheet, p.x(), p.z(), -s, c, h, u0, uv[1], u1, uv[3]);
    }

    /** A card standing on the ground at {@code (x, z)}, reaching {@code (dx, dz)} either side of it. */
    private static void card(Batch batch, Texture sheet, double x, double z, double dx, double dz, float h,
                             float u0, float v0, float u1, float v1) {
        float lx = (float) (x - dx), lz = (float) (z - dz), rx = (float) (x + dx), rz = (float) (z + dz);
        batch.quad(sheet, lx, h, lz, rx, h, rz, rx, 0, rz, lx, 0, lz, u0, v0, u1, v1, 1, 1, 1, 1);
    }

    @Override
    public void close() {
        for (Texture t : sheets.values()) t.close();
        sheets.clear();
    }
}
