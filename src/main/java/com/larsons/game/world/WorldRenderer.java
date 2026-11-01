package com.larsons.game.world;

import com.larsons.game.combat.CombatRenderer;
import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.SceneLight;
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
 *   <li><b>The void</b> — sky and endless grass, one full-screen pass, writing
 *       the ground's depth so the earth hides what lies below it (not in the
 *       hills' cells, which are theirs).</li>
 *   <li><b>The hills</b> — the {@link Terrain}'s mesh over it, where the ground
 *       is not flat ({@link TerrainRenderer}), depth-writing.</li>
 *   <li><b>Scenery</b> — the trees, grass tufts and rocks: cutout cards, two
 *       crossed for a tree or a tuft, one facing the camera for a rock
 *       ({@link SceneryRenderer}), depth-tested and depth-writing.</li>
 *   <li><b>Shadows</b> — soft blobs on the ground under every sprite, tree
 *       and rock - and under every flier and every shot in flight, smaller
 *       and fainter the higher it is, which is how its height is read -
 *       laid over the hills' slopes, depth-tested (a trunk hides the shadow
 *       behind it) but not writing - the warm pool of light an open chest
 *       throws round itself, and the pool of its rarity's light under every
 *       item.</li>
 *   <li><b>Sprites</b> — every character layer, every item and every chest
 *       (its smoke behind, the chest, its smoke in front), the training
 *       dummies and whatever is flying ({@link CombatRenderer}), as camera-facing
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
    /** A dash's afterimages: one left behind every so many seconds, each fading out over so many. */
    static final double GHOST_EVERY = 0.035, GHOST_LIFE = 0.2;
    /** How opaque an afterimage starts, and the one cool colour it is drawn in (a silhouette). */
    static final float GHOST_ALPHA = 0.4f;
    static final int GHOST_RGB = 0x9CC4FF;

    private final VoidRenderer voidRenderer;
    private final TerrainRenderer terrain;
    private final WaterRenderer water;
    private final SceneryRenderer scenery;
    private final Batch batch;
    private final SpriteLibrary sprites;
    private final ObjectSprites objects;
    private final ItemSprites items;
    private final ItemRenderer itemRenderer;
    private final Texture shadow;
    private final Texture glow;
    private final Texture ray;
    /** The light the frame being drawn is lit with, made afresh every frame. */
    private final SceneLight sceneLight = new SceneLight();
    /** The time of day's light the last frame was drawn in. */
    private DayCycle.Daylight daylight;
    private final Map<Chest, ObjectStack.Memory> chestMemory = new IdentityHashMap<>();
    private final CombatRenderer combat;

    private LayerStack.Result playerStack;
    /** A frame of the player left behind by a dodge, fading. */
    private record Ghost(LayerStack.Result stack, Vec3 feet, Vec3 ground, double born) { }
    private final java.util.ArrayDeque<Ghost> ghosts = new java.util.ArrayDeque<>();
    private double lastGhost = Double.NEGATIVE_INFINITY;
    /** Each layer palette's silhouette ({@link #silhouette}), made once. */
    private final Map<int[], int[]> silhouettes = new IdentityHashMap<>();
    /** The ground the shadows of the frame being drawn lie on. */
    private Terrain ground = Terrain.FLAT;

    public WorldRenderer(VoidRenderer voidRenderer, TerrainRenderer terrain, SceneryRenderer scenery, Batch batch,
                         SpriteLibrary sprites, ObjectSprites objects, ItemSprites items) {
        this.voidRenderer = voidRenderer;
        this.terrain = terrain;
        this.scenery = scenery;
        this.batch = batch;
        this.sprites = sprites;
        this.objects = objects;
        this.items = items;
        this.itemRenderer = new ItemRenderer(batch, sprites, items);
        this.shadow = Texture.upload(shadowPixels(64), false);
        this.glow = Texture.upload(glowPixels(64), false);
        this.ray = Texture.upload(LightRays.pixels(), true);
        this.water = new WaterRenderer();
        this.combat = new CombatRenderer(sprites, objects);
    }

    /** The fighting's drawing (its sprites, for the HUD's dice too). */
    public CombatRenderer combat() { return combat; }

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
        Sky sky = world.sky();
        DayCycle.Daylight light = world.clock().daylight(sky);
        daylight = light;
        float[] fog = light.horizon();
        float fogFar = (float) sky.fog();
        Vec3 eye = camera.eye();

        ground = world.terrain();
        lightUp(world, camera, light);
        voidRenderer.useGround(world.ground());
        // a backdrop's pixel: three of the screen's, as the sprites' pixels are about
        float pixel = (float) Math.toDegrees(camera.fovY()) / Math.max(1, fbHeight) * 3;
        voidRenderer.draw(camera, aspect, fogFar, viewProj, ground, sky, light, sceneLight, world.time(), pixel);
        if (terrain != null) terrain.draw(ground, camera, viewProj, fog, fogFar, sceneLight);
        batch.useSceneLight(sceneLight);
        Vec3 upright = uprightNormal(camera);
        scenery.bendAwayFrom(walkers(world));
        scenery.draw(batch, world.scenery(), viewProj, fog, fogFar, world.time(), camera, upright);

        // --- shadows -------------------------------------------------------------
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        batch.begin(viewProj, fog, 0, fogFar);
        Player p = world.player();
        double lift = Math.min(1, p.height() / 1.5);
        shadowStrength = (float) (0.55 + 0.45 * light.day());
        groundShadow(p.ground(), 0.42 * (1 - 0.3 * lift), 0.5f * (float) (1 - 0.45 * lift));
        for (World.GroundItem g : world.items()) {
            if (!g.out(world.time())) continue;
            Vec3 c = g.centre(world.time());
            double h = Math.max(0, c.y() - ground.height(c.x(), c.z()));
            groundShadow(new Vec3(c.x(), 0, c.z()), 0.30 * (1 - 0.15 * Math.min(2, h)),
                    (float) Math.max(0, (0.42 - 0.12 * h) * g.flight(world.time())));
            itemRenderer.ground(g, world.time(), this::groundGlow);
        }
        for (Chest c : world.chests()) {
            groundShadow(c.position(), 0.75, 0.45f);
            double lit = c.light();
            if (lit > 0) groundQuad(glow, c.position(), 1.9, 1f, 0.82f, 0.5f, (float) (0.42 * lit));
        }
        combat.shadows(world, this::groundShadow);
        for (Scenery.Placed s : world.scenery().shadowed()) {
            groundShadow(s.position(), s.profile().shadowRadius(), 0.35f);
        }
        batch.end();

        // --- sprites, far to near (tested against the scenery, not written) --------
        batch.begin(viewProj, fog, 0, fogFar);
        batch.setDepthPlane(eye, upright);
        List<Runnable> draws = new ArrayList<>();
        List<Double> dist = new ArrayList<>();
        Vec3 right = camera.right(), up = camera.up(), forward = camera.forward();

        Vec3 pivot = p.feet().add(0, 0.9, 0);
        SpriteView view = SpriteView.of(eye, pivot, p.heading(), camera.yaw());
        playerStack = inTier(LayerStack.resolve(sprites, p.wardrobe(), p.state(), p.stateTime(), view, p.memory(),
                p.handsEmpty(), p.stance()), world.wielded(), items);
        LayerStack.Result stack = playerStack;
        draws.add(() -> {
            batch.depthAnchor(p.ground());
            LayerStack.drawBillboard(batch, stack, p.feet(), right, up, 1f);
        });
        dist.add(p.feet().sub(eye).dot(forward));

        // the dash's afterimages: the frames she leaves behind, fading where they were
        double now = world.time();
        if (p.afterimages() && now - lastGhost >= GHOST_EVERY && !stack.layers().isEmpty()) {
            ghosts.addLast(new Ghost(stack, p.feet(), p.ground(), now));
            lastGhost = now;
        }
        while (!ghosts.isEmpty() && now - ghosts.peekFirst().born() > GHOST_LIFE) ghosts.removeFirst();
        if (silhouettes.size() > 256) silhouettes.clear();
        for (Ghost g : ghosts) {
            // (not the one left this frame, where she still stands)
            if (g.born() >= now || g.stack().layers().stream().anyMatch(l -> l.sheet().closed())) continue;
            float a = GHOST_ALPHA * (float) (1 - (now - g.born()) / GHOST_LIFE);
            if (a <= 0.01f) continue;
            List<LayerStack.Layer> flat = new ArrayList<>();
            for (LayerStack.Layer l : g.stack().layers()) {
                flat.add(l.palette() == null ? l : l.withPalette(silhouettes.computeIfAbsent(l.palette(),
                        WorldRenderer::silhouette)));
            }
            draws.add(() -> {
                batch.depthAnchor(g.ground());
                // (a pixel-art layer in the silhouette's colour; an RGBA one - the rendered style's - in its own)
                LayerStack.drawBillboard(batch, flat, g.stack().framing(), g.stack().view().elevation(), g.feet(),
                        right, up, a);
            });
            dist.add(g.feet().sub(eye).dot(forward));
        }

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
                batch.depthAnchor(ground.onGround(c));   // (the spot it hovers or flies over)
                itemRenderer.draw(g, t, eye, camera.yaw(), right, up);
            });
            dist.add(g.centre(t).sub(eye).dot(forward));
        }

        double shine = 1 - light.day();
        if (shine > 0.05) {
            for (Scenery.Placed gp : glowing(world, eye, GLOW_REACH)) {
                draws.add(() -> {
                    batch.depthAnchor(gp.position());
                    sceneryGlow(gp, right, up, shine);
                });
                dist.add(glowCentre(gp).sub(eye).dot(forward));
            }
        }

        for (World.LightSpot ls : world.lights()) {
            if (!ls.glow()) continue;
            draws.add(() -> {
                batch.depthAnchor(ground.onGround(ls.at()));
                lightGlow(ls, right, up);
            });
            dist.add(ls.at().sub(eye).dot(forward));
        }

        combat.sprites(world, batch, camera, (d, draw) -> {
            draws.add(draw);
            dist.add(d);
        });

        Integer[] order = new Integer[draws.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, Comparator.comparingDouble((Integer i) -> dist.get(i)).reversed());
        for (int i : order) draws.get(i).run();
        batch.end();

        // --- the water, over what stands and wades in it ------------------------------
        water.draw(world.water(), ground, eye, viewProj, fog, fogFar, light, sceneLight, world.time());

        // --- light rays, added over it all (tested against the scenery and the ground) ----
        List<LightRays.Shaft> shafts = LightRays.shafts(world, light, eye, world.time());
        if (!shafts.isEmpty()) {
            batch.begin(viewProj, fog, 0, fogFar);
            float[] sun = light.sunColour();
            for (LightRays.Shaft s : shafts) rayQuad(s, eye, sun);
            batch.end();
        }
        batch.useSceneLight(null);
        glDepthMask(true);
    }

    /** The time of day's light the last frame was drawn in (null before the first). */
    public DayCycle.Daylight daylight() { return daylight; }

    /** The light this frame is drawn in: the hour's, and the lights round the camera. */
    public SceneLight sceneLight() { return sceneLight; }

    /**
     * Make this frame's {@link SceneLight}: lit as the hour is, in the mist
     * as thick as it lies now, with the lights nearest the camera's target -
     * an open chest's glow, the loot's beams, the lights placed in the world -
     * which mostly show at night.
     */
    private void lightUp(World world, OrbitCamera camera, DayCycle.Daylight light) {
        SceneLight l = sceneLight.clearLights();
        float[] a = light.ambient();
        System.arraycopy(a, 0, l.ambient, 0, 3);
        Vec3 eye = camera.eye();
        l.camera[0] = (float) eye.x();
        l.camera[1] = (float) eye.y();
        l.camera[2] = (float) eye.z();
        Atmosphere air = world.sky().air();
        l.mist = (float) (light.mist() * 0.32);
        l.mistHeight = (float) air.mistHeight();
        l.time = (float) world.time();
        float[] h = light.horizon();
        for (int i = 0; i < 3; i++) l.mistColour[i] = 0.62f * a[i] * 0.92f + 0.38f * h[i];
        double night = 1 - 0.75 * light.day();
        record Lamp(Vec3 at, double radius, int rgb, double strength) {}
        List<Lamp> lamps = new ArrayList<>();
        for (Chest c : world.chests()) {
            if (c.light() > 0) lamps.add(new Lamp(c.position().add(0, 0.7, 0), 3.6, 0xFFC468, 0.95 * c.light() * night));
        }
        for (World.GroundItem g : world.items()) {
            if (!g.beam() || !g.landed(world.time())) continue;
            lamps.add(new Lamp(g.position.add(0, 0.5, 0), 1.4, g.item.rarity().rgb(), 0.35 * night));
        }
        for (World.LightSpot s : world.lights()) lamps.add(new Lamp(s.at(), s.radius(), s.rgb(), s.strength() * night));
        // the crystals' glow, and anything else of the scenery's that shines at night
        double shine = 1 - light.day();
        if (shine > 0.05) {
            for (Scenery.Placed p : glowing(world, camera.target(), GLOW_REACH)) {
                boolean tree = p.profile().kind() == com.larsons.game.sprite.SceneryProfile.Kind.TREE;
                lamps.add(new Lamp(glowCentre(p), tree ? 4.0 : 2.4, p.profile().glow(), (tree ? 0.8 : 0.65) * shine));
            }
        }
        Vec3 target = camera.target();
        lamps.sort(Comparator.comparingDouble(m -> m.at().sub(target).length() - m.radius()));
        for (Lamp m : lamps) {
            if (!l.add(m.at().x(), m.at().y(), m.at().z(), m.radius(), m.rgb(), m.strength())) break;
        }
    }

    /** How near the camera's target scenery must be for its night glow to light the ground, metres. */
    static final double GLOW_REACH = 45;

    /** Everything of {@code world}'s scenery that glows, within {@code reach} of {@code near}. */
    private static List<Scenery.Placed> glowing(World world, Vec3 near, double reach) {
        List<Scenery.Placed> out = new ArrayList<>();
        for (Map.Entry<com.larsons.game.sprite.ScenerySprites.Sprite, List<Scenery.Placed>> e
                : world.scenery().bySprite().entrySet()) {
            if (e.getKey().profile().glow() < 0) continue;
            for (Scenery.Placed p : e.getValue()) {
                if (Math.hypot(p.x() - near.x(), p.z() - near.z()) < reach) out.add(p);
            }
        }
        return out;
    }

    /** Where a glowing piece of scenery's light comes from: low in it, as a crystal's heart. */
    private static Vec3 glowCentre(Scenery.Placed p) {
        return p.position().add(0, p.profile().frameWorldHeight() * 0.3, 0);
    }

    /** The feet of everyone walking about, whom the tall grass leans away from. */
    private static List<Vec3> walkers(World world) {
        List<Vec3> out = new ArrayList<>();
        out.add(world.player().feet());
        for (com.larsons.game.combat.Dummy d : world.dummies()) out.add(d.position());
        return out;
    }

    /** A glowing crystal's halo at night: a soft wash of its colour round it. */
    private void sceneryGlow(Scenery.Placed p, Vec3 right, Vec3 up, double shine) {
        int rgb = p.profile().glow();
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        double size = p.profile().frameWorldHeight() * 0.9;
        Vec3 tl = glowCentre(p).sub(right.scale(size / 2)).add(up.scale(size / 2));
        batch.glow(glow, tl, right.scale(size), up.scale(-size), r, g, b, (float) (0.32 * shine));
    }

    /** A placed light's own glow: a small bright spot where it is, and a soft halo round it. */
    private void lightGlow(World.LightSpot ls, Vec3 right, Vec3 up) {
        float r = ((ls.rgb() >> 16) & 255) / 255f, g = ((ls.rgb() >> 8) & 255) / 255f, b = (ls.rgb() & 255) / 255f;
        double night = daylight == null ? 1 : 1 - 0.6 * daylight.day();
        for (double[] k : new double[][]{{0.55, 0.35}, {0.16, 1.0}}) {
            double size = k[0];
            Vec3 tl = ls.at().sub(right.scale(size / 2)).add(up.scale(size / 2));
            batch.glow(glow, tl, right.scale(size), up.scale(-size), r, g, b,
                    (float) (k[1] * Math.min(1.5, ls.strength()) * night));
        }
    }

    /** One shaft of light: a ribbon down its length, turned to face the eye. */
    private void rayQuad(LightRays.Shaft s, Vec3 eye, float[] sun) {
        Vec3 along = s.bottom().sub(s.top());
        Vec3 mid = s.top().add(along.scale(0.5));
        Vec3 side = along.cross(eye.sub(mid));
        if (side.length() < 1e-6) return;
        side = side.normalize().scale(s.width() / 2);
        Vec3 tl = s.top().sub(side), tr = s.top().add(side), br = s.bottom().add(side), bl = s.bottom().sub(side);
        float k = (float) Math.min(1.2, s.strength());
        batch.glow(ray, (float) tl.x(), (float) tl.y(), (float) tl.z(), (float) tr.x(), (float) tr.y(), (float) tr.z(),
                (float) br.x(), (float) br.y(), (float) br.z(), (float) bl.x(), (float) bl.y(), (float) bl.z(),
                0, 0, 1, 1, sun[0], sun[1] * 0.96f, sun[2] * 0.85f, 0.35f * k);
    }

    /** {@code palette} with every colour {@link #GHOST_RGB}, each entry's alpha kept: a layer as a flat silhouette. */
    static int[] silhouette(int[] palette) {
        int[] out = new int[palette.length];
        for (int i = 0; i < palette.length; i++) out[i] = (palette[i] & 0xFF000000) | GHOST_RGB;
        return out;
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

    /** How dark the shadows are this frame: fainter by night, when the light is weaker. */
    private float shadowStrength = 1f;

    private void groundShadow(Vec3 at, double radius, float alpha) {
        groundQuad(shadow, at, radius, 0, 0, 0, alpha * shadowStrength);
    }

    /** Pieces a side a shadow on a slope is laid in, and how far over the ground they lie, metres. */
    static final int SLOPE_PIECES = 4;
    static final float SLOPE_LIFT = 0.04f;

    /**
     * A square of {@code tex} {@code radius} round {@code at} lying on the
     * ground: flat on the flat ground; on a hill in {@value #SLOPE_PIECES} ×
     * {@value #SLOPE_PIECES} pieces, each corner on the ground, so it follows
     * the slope.
     */
    private void groundQuad(Texture tex, Vec3 at, double radius, float r, float g, float b, float alpha) {
        groundQuad(tex, at, radius, r, g, b, alpha, false);
    }

    /**
     * {@link #groundQuad} as light ({@link Batch#glow}), added to the ground
     * rather than laid over it: an item's pool of its rarity's light.
     */
    private void groundGlow(Texture tex, Vec3 at, double radius, float r, float g, float b, float intensity) {
        groundQuad(tex, at, radius, r, g, b, intensity, true);
    }

    private void groundQuad(Texture tex, Vec3 at, double radius, float r, float g, float b, float alpha,
                            boolean light) {
        double x0 = at.x() - radius, x1 = at.x() + radius, z0 = at.z() - radius, z1 = at.z() + radius;
        boolean flat = ground.flat() || Math.abs(ground.height(x0, z0)) + Math.abs(ground.height(x1, z0))
                + Math.abs(ground.height(x0, z1)) + Math.abs(ground.height(x1, z1))
                + Math.abs(ground.height(at.x(), at.z())) < 1e-4;
        if (flat) {
            float y = 0.004f;
            piece(tex, (float) x0, y, (float) z0, (float) x1, y, (float) z0, (float) x1, y, (float) z1,
                    (float) x0, y, (float) z1, 0, 0, 1, 1, r, g, b, alpha, light);
            return;
        }
        int n = SLOPE_PIECES;
        double step = 2 * radius / n;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double ax = x0 + i * step, bx = ax + step, az = z0 + j * step, bz = az + step;
                float u0 = (float) i / n, u1 = (float) (i + 1) / n, v0 = (float) j / n, v1 = (float) (j + 1) / n;
                piece(tex, (float) ax, lift(ax, az), (float) az, (float) bx, lift(bx, az), (float) az,
                        (float) bx, lift(bx, bz), (float) bz, (float) ax, lift(ax, bz), (float) bz,
                        u0, v0, u1, v1, r, g, b, alpha, light);
            }
        }
    }

    /** One piece of a {@link #groundQuad}: laid over the ground, or as light. */
    private void piece(Texture tex, float x0, float y0, float z0, float x1, float y1, float z1,
                       float x2, float y2, float z2, float x3, float y3, float z3,
                       float u0, float v0, float u1, float v1, float r, float g, float b, float alpha,
                       boolean light) {
        if (light) {
            batch.glow(tex, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, u0, v0, u1, v1, r, g, b, alpha);
        } else {
            batch.quad(tex, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, u0, v0, u1, v1, r, g, b, alpha);
        }
    }

    private float lift(double x, double z) {
        return (float) ground.height(x, z) + SLOPE_LIFT;
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
        ray.close();
        water.close();
    }
}
