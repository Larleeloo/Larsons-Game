package com.larsons.game.world;

import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Texture;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.ItemSprites;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.ObjectSprites;
import com.larsons.game.sprite.ObjectStack;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Wardrobe;

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
 *   <li><b>The void</b> — sky and endless grass, one full-screen pass, no depth.</li>
 *   <li><b>Scenery</b> — the trees, grass tufts and rocks: cutout cards, two
 *       crossed for a tree or a tuft, one facing the camera for a rock
 *       ({@link SceneryRenderer}), depth-tested and depth-writing.</li>
 *   <li><b>Shadows</b> — soft blobs on the ground under every sprite, tree
 *       and rock, depth-tested (a trunk hides the shadow behind it) but not
 *       writing - the warm pool of light an open chest throws round itself,
 *       and the pool of its rarity's light under every item.</li>
 *   <li><b>Sprites</b> — every character layer, every item and every chest
 *       (its smoke behind, the chest, its smoke in front), as camera-facing
 *       billboards, far to near so their soft edges blend over what is
 *       behind them; depth-tested against the scenery so a tree can stand in
 *       front of the player, and a tuft hide her feet. An item comes with its halo, its border and
 *       its beam of light ({@link ItemRenderer}); the weapon in her hands is
 *       drawn in its tier's colours ({@link ItemSprites#carriedPalette}).</li>
 * </ol>
 *
 * <p>Sprites test depth but do not write it. Every billboard faces the
 * camera, so each one lies at a single depth and drawing them farthest
 * first by that depth puts the nearer one on top wherever two overlap -
 * what the depth buffer would have decided - while each layer of a stack
 * can be drawn over only its own part of the card ({@link
 * LayerStack#drawBillboard}), not the whole card, with no risk of two
 * layers fighting over the last bit of depth.
 *
 * <p>Against the scenery a billboard is tested as if it stood upright on
 * its spot ({@link Batch#setDepthPlane}): seen from above, a card facing
 * the camera leans back, and its head would sink behind a trunk she stands
 * in front of, or a tuft behind her feet poke through her knees. So a tree
 * or a tuft hides her where it stands nearer the camera than her feet, and
 * never where it stands behind them. From high above, the upright plane
 * leans back a little with the view (from 60° on), so a figure seen from
 * straight overhead still has some height.
 */
public final class WorldRenderer implements AutoCloseable {

    /** How far the fog reaches, in metres. */
    public static final float FOG = 70f;

    private final VoidRenderer voidRenderer;
    private final SceneryRenderer scenery;
    private final Batch batch;
    private final SpriteLibrary sprites;
    private final ObjectSprites objects;
    private final ItemSprites items;
    private final ItemRenderer itemRenderer;
    private final Texture shadow;
    private final Texture glow;
    private final Map<Chest, ObjectStack.Memory> chestMemory = new IdentityHashMap<>();

    private LayerStack.Result playerStack;

    public WorldRenderer(VoidRenderer voidRenderer, SceneryRenderer scenery, Batch batch, SpriteLibrary sprites,
                         ObjectSprites objects, ItemSprites items) {
        this.voidRenderer = voidRenderer;
        this.scenery = scenery;
        this.batch = batch;
        this.sprites = sprites;
        this.objects = objects;
        this.items = items;
        this.itemRenderer = new ItemRenderer(batch, sprites, items);
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

    /** Draw {@code world} - its scenery as {@link World#scenery()} has it - from {@code camera}. */
    public void render(World world, OrbitCamera camera, int fbWidth, int fbHeight) {
        glViewport(0, 0, fbWidth, fbHeight);
        double aspect = fbWidth / (double) Math.max(1, fbHeight);
        Mat4 viewProj = camera.viewProjection(aspect);
        float[] fog = VoidRenderer.HORIZON;
        Vec3 eye = camera.eye();

        voidRenderer.draw(camera, aspect, FOG);
        Vec3 upright = uprightNormal(camera);
        scenery.draw(batch, world.scenery(), viewProj, fog, FOG, world.time(), camera, upright);

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
            if (!g.out(world.time())) continue;
            Vec3 c = g.centre(world.time());
            double h = c.y();
            groundShadow(new Vec3(c.x(), 0, c.z()), 0.30 * (1 - 0.15 * h),
                    (float) Math.max(0, (0.42 - 0.12 * h) * g.flight(world.time())));
            itemRenderer.ground(g, world.time());
        }
        for (Chest c : world.chests()) {
            groundShadow(c.position(), 0.75, 0.45f);
            double light = c.light();
            if (light > 0) groundQuad(glow, c.position(), 1.9, 1f, 0.82f, 0.5f, (float) (0.42 * light));
        }
        for (Scenery.Placed s : world.scenery().shadowed()) {
            groundShadow(s.position(), s.profile().shadowRadius(), 0.35f);
        }
        batch.end();

        // --- sprites, far to near (tested against the scenery, not written) --------
        batch.begin(viewProj, fog, 0, FOG);
        batch.setDepthPlane(eye, upright);
        List<Runnable> draws = new ArrayList<>();
        List<Double> dist = new ArrayList<>();
        Vec3 right = camera.right(), up = camera.up(), forward = camera.forward();

        Vec3 pivot = p.feet().add(0, 0.9, 0);
        SpriteView view = SpriteView.of(eye, pivot, p.heading(), camera.yaw());
        playerStack = inTier(LayerStack.resolve(sprites, p.wardrobe(), p.state(), p.stateTime(), view, p.memory()),
                world.wielded(), items);
        LayerStack.Result stack = playerStack;
        draws.add(() -> {
            batch.depthAnchor(p.ground());
            LayerStack.drawBillboard(batch, stack, p.feet(), right, up, 1f);
        });
        dist.add(p.feet().sub(eye).dot(forward));

        for (Chest c : world.chests()) {
            Vec3 chestPivot = c.position().add(0, objects.profile(c.id()).pivotHeight(), 0);
            SpriteView chestView = SpriteView.of(eye, chestPivot, c.heading(), camera.yaw());
            ObjectStack.Result cs = ObjectStack.resolve(sprites, objects, c.id(), c.state().key(),
                    c.state().loops(), c.time(), world.time(), chestView, c.light(),
                    chestMemory.computeIfAbsent(c, k -> new ObjectStack.Memory()));
            draws.add(() -> {
                batch.depthAnchor(c.position());
                LayerStack.drawBillboard(batch, cs.layers(), cs.framing(), cs.view().elevation(),
                        c.position(), right, up, 1f);
            });
            dist.add(c.position().sub(eye).dot(forward));
        }

        double t = world.time();
        for (World.GroundItem g : world.items()) {
            if (!g.out(t)) continue;
            draws.add(() -> {
                Vec3 c = g.centre(t);
                batch.depthAnchor(new Vec3(c.x(), 0, c.z()));   // (the spot it hovers or flies over)
                itemRenderer.draw(g, t, eye, camera.yaw(), right, up);
            });
            dist.add(g.centre(t).sub(eye).dot(forward));
        }

        Integer[] order = new Integer[draws.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, Comparator.comparingDouble((Integer i) -> dist.get(i)).reversed());
        for (int i : order) draws.get(i).run();
        batch.end();
        glDepthMask(true);
    }

    /**
     * {@code stack} with the weapon in her hands - the carried layers of
     * {@code wielded}, in either hand - in its tier's colours ({@code
     * items}' {@link ItemSprites#carriedPalette}): a Sharper Axe's handle
     * black, a Perfect Sword gold. The stack itself when nothing changes.
     */
    static LayerStack.Result inTier(LayerStack.Result stack, Item wielded, ItemSprites items) {
        if (stack == null || wielded == null || wielded.tier() == null) return stack;
        String id = wielded.id(), tier = wielded.tier().key();
        List<LayerStack.Layer> layers = null;
        for (int i = 0; i < stack.layers().size(); i++) {
            LayerStack.Layer l = stack.layers().get(i);
            if (!isCarried(l, id) || l.palette() == null) continue;
            int[] p = items.carriedPalette(l.palette(), id, tier);
            if (p == l.palette()) continue;
            if (layers == null) layers = new ArrayList<>(stack.layers());
            layers.set(i, l.withPalette(p));
        }
        if (layers == null) return stack;
        return new LayerStack.Result(List.copyOf(layers), stack.framing(), stack.view(), stack.state(),
                stack.asked(), stack.frame(), stack.frames(), stack.fps(), stack.fallbackBody(), stack.loading());
    }

    /** Whether {@code l} is weapon {@code id} in a hand (its left-handed or styled sheets too). */
    static boolean isCarried(LayerStack.Layer l, String id) {
        return (l.slot() == Slot.CARRY_RIGHT || l.slot() == Slot.CARRY_LEFT) && l.item() != null
                && Wardrobe.Style.base(l.item()).equals(id);
    }

    /**
     * The way a billboard's upright depth plane faces: the camera's forward
     * along the ground - a plane standing straight up - until the camera is
     * 60° up, and from there leaning back by as much again as the camera
     * climbs, so it never lies along the view (straight overhead it leans
     * back 30°).
     */
    static Vec3 uprightNormal(OrbitCamera camera) {
        double lean = Math.max(0, camera.pitch() - Math.toRadians(60));
        return camera.groundForward().scale(Math.cos(lean)).add(0, -Math.sin(lean), 0);
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
        itemRenderer.close();
        shadow.close();
        glow.close();
    }
}
