package com.larsons.game.world;

import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Texture;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.ObjectSprites;
import com.larsons.game.sprite.ObjectStack;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.SpriteView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws a frame of the world, in the order the 2D/3D mix needs:
 *
 * <ol>
 *   <li><b>The void</b> — sky and endless floor, one full-screen pass, no depth.</li>
 *   <li><b>3D props</b> — real geometry, depth-tested and depth-writing.</li>
 *   <li><b>Shadows</b> — soft blobs on the floor under every sprite and prop,
 *       depth-tested (a trunk hides the shadow behind it) but not writing -
 *       and the warm pool of light an open chest throws round itself.</li>
 *   <li><b>Sprites</b> — every character layer, every item and every chest
 *       (its smoke behind, the chest, its smoke in front), as camera-facing
 *       billboards, far to near so their soft edges blend over what is
 *       behind them; depth-tested against the props so a tree can stand in
 *       front of the player.</li>
 * </ol>
 */
public final class WorldRenderer implements AutoCloseable {

    /** How far the fog reaches, in metres. */
    public static final float FOG = 70f;

    private final VoidRenderer voidRenderer;
    private final Props props;
    private final Batch batch;
    private final SpriteLibrary sprites;
    private final ObjectSprites objects;
    private final Texture shadow;
    private final Texture glow;
    private final Map<Chest, ObjectStack.Memory> chestMemory = new IdentityHashMap<>();

    private LayerStack.Result playerStack;

    public WorldRenderer(VoidRenderer voidRenderer, Props props, Batch batch, SpriteLibrary sprites,
                         ObjectSprites objects) {
        this.voidRenderer = voidRenderer;
        this.props = props;
        this.batch = batch;
        this.sprites = sprites;
        this.objects = objects;
        this.shadow = Texture.upload(shadowPixels(64), false);
        this.glow = Texture.upload(glowPixels(64), false);
    }

    /** A soft round blob, white, alpha falling off to nothing at the rim: tinted, a pool of light. */
    private static PixelData glowPixels(int n) {
        int[] argb = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r2 = dx * dx + dy * dy;
                double a = r2 >= 1 ? 0 : Math.pow(1 - r2, 2.2);
                argb[y * n + x] = ((int) Math.round(a * 255) << 24) | 0xFFFFFF;
            }
        }
        return PixelData.of(argb, n, n);
    }

    /** A soft round blob, black, alpha falling off to nothing at the rim. */
    private static PixelData shadowPixels(int n) {
        int[] argb = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r2 = dx * dx + dy * dy;
                double a = r2 >= 1 ? 0 : Math.pow(1 - r2, 1.6);
                argb[y * n + x] = ((int) Math.round(a * 255) << 24);
            }
        }
        return PixelData.of(argb, n, n);
    }

    /** The player's layer stack as last drawn — for the HUD. */
    public LayerStack.Result playerStack() { return playerStack; }

    public void render(World world, OrbitCamera camera, int fbWidth, int fbHeight, boolean showProps) {
        glViewport(0, 0, fbWidth, fbHeight);
        double aspect = fbWidth / (double) Math.max(1, fbHeight);
        Mat4 viewProj = camera.viewProjection(aspect);
        float[] fog = VoidRenderer.HORIZON;
        Vec3 eye = camera.eye();

        voidRenderer.draw(camera, aspect, FOG);
        if (showProps) props.draw(viewProj, fog, FOG);

        // --- shadows -------------------------------------------------------------
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        batch.begin(viewProj, fog, 0, FOG);
        Player p = world.player();
        double lift = Math.min(1, p.height() / 1.5);
        groundShadow(p.ground(), 0.42 * (1 - 0.3 * lift), 0.5f * (float) (1 - 0.45 * lift));
        for (World.GroundItem g : world.items()) {
            double h = g.hover(world.time());
            groundShadow(g.position, 0.30 * (1 - 0.15 * h), (float) (0.42 - 0.12 * h));
        }
        for (Chest c : world.chests()) {
            groundShadow(c.position(), 0.75, 0.45f);
            double light = c.light();
            if (light > 0) groundQuad(glow, c.position(), 1.9, 1f, 0.82f, 0.5f, (float) (0.42 * light));
        }
        if (showProps) {
            for (Props.Placed pl : props.placed()) groundShadow(pl.position(), pl.shadowRadius(), 0.35f);
        }
        batch.end();

        // --- sprites, far to near ------------------------------------------------
        glDepthMask(true);
        batch.begin(viewProj, fog, 0, FOG);
        List<Runnable> draws = new ArrayList<>();
        List<Double> dist = new ArrayList<>();
        Vec3 right = camera.right(), up = camera.up();

        Vec3 pivot = p.feet().add(0, 0.9, 0);
        SpriteView view = SpriteView.of(eye, pivot, p.heading(), camera.yaw());
        playerStack = LayerStack.resolve(sprites, p.wardrobe(), p.state(), p.stateTime(), view, p.memory());
        LayerStack.Result stack = playerStack;
        draws.add(() -> LayerStack.drawBillboard(batch, stack, p.feet(), right, up, 1f));
        dist.add(eye.distance(pivot));

        for (Chest c : world.chests()) {
            Vec3 chestPivot = c.position().add(0, objects.profile(c.id()).pivotHeight(), 0);
            SpriteView chestView = SpriteView.of(eye, chestPivot, c.heading(), camera.yaw());
            ObjectStack.Result cs = ObjectStack.resolve(sprites, objects, c.id(), c.state().key(),
                    c.state().loops(), c.time(), world.time(), chestView, c.light(),
                    chestMemory.computeIfAbsent(c, k -> new ObjectStack.Memory()));
            draws.add(() -> LayerStack.drawBillboard(batch, cs.layers(), cs.framing(), cs.view().elevation(),
                    c.position(), right, up, 1f));
            dist.add(eye.distance(chestPivot));
        }

        for (World.GroundItem g : world.items()) {
            Texture icon = sprites.icon(g.def.id(), g.def.fallbackIcon());
            Vec3 centre = g.position.add(0, g.hover(world.time()), 0);
            double s = g.def.worldSize();
            draws.add(() -> batch.quad(icon,
                    centre.sub(right.scale(s / 2)).add(up.scale(s / 2)), right.scale(s), up.scale(-s),
                    0, 0, 1, 1, 1, 1, 1, 1));
            dist.add(eye.distance(centre));
        }

        Integer[] order = new Integer[draws.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, Comparator.comparingDouble((Integer i) -> dist.get(i)).reversed());
        for (int i : order) draws.get(i).run();
        batch.end();
    }

    private void groundShadow(Vec3 at, double radius, float alpha) {
        groundQuad(shadow, at, radius, 0, 0, 0, alpha);
    }

    private void groundQuad(Texture tex, Vec3 at, double radius, float r, float g, float b, float alpha) {
        float y = 0.004f;
        float x0 = (float) (at.x() - radius), x1 = (float) (at.x() + radius);
        float z0 = (float) (at.z() - radius), z1 = (float) (at.z() + radius);
        batch.quad(tex, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, 0, 0, 1, 1, r, g, b, alpha);
    }

    /**
     * How long {@code chest}'s {@code state} lasts as seen from {@code camera}
     * (from its sheet; 0 while that is still loading).
     */
    public double chestDuration(Chest chest, Chest.State state, OrbitCamera camera) {
        Vec3 pivot = chest.position().add(0, objects.profile(chest.id()).pivotHeight(), 0);
        SpriteView view = SpriteView.of(camera.eye(), pivot, chest.heading(), camera.yaw());
        return ObjectStack.duration(sprites, objects, chest.id(), state.key(), view);
    }

    @Override
    public void close() {
        shadow.close();
        glow.close();
    }
}
