package com.larsons.game.combat;

import com.larsons.game.gfx.Batch;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.ObjectSprites;
import com.larsons.game.sprite.ObjectStack;
import com.larsons.game.sprite.SheetTexture;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.SpriteProfile;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the fighting into the world, for {@code WorldRenderer}: the dummies
 * and the flying dummies (2D sprites from 24 views, like the chest, flashing
 * white as they are struck), the arrows, bolts and spells in flight - each turned along its
 * path as it crosses the screen - the arrows stuck in the ground, and the
 * sparks and bursts where things hit. The numbers and the dice are the
 * HUD's ({@link CombatHud}).
 */
public final class CombatRenderer {

    /** Somewhere to put a shadow on the ground. */
    public interface Shadows {
        void at(Vec3 ground, double radius, float alpha);
    }

    /** Somewhere to queue a sprite to draw, by its distance along the camera's view. */
    public interface Draws {
        void add(double distance, Runnable draw);
    }

    private final SpriteLibrary sprites;
    private final ObjectSprites objects;
    private final CombatSprites fx;
    private final Map<Object, ObjectStack.Memory> memory = new IdentityHashMap<>();

    public CombatRenderer(SpriteLibrary sprites, ObjectSprites objects) {
        this.sprites = sprites;
        this.objects = objects;
        this.fx = new CombatSprites(sprites);
    }

    public CombatSprites fx() { return fx; }

    /**
     * The dummies' shadows, and on the ground under each flier and each shot
     * in flight its own - smaller and fainter the higher it is, which is how
     * its height is read.
     */
    public void shadows(World world, Shadows out) {
        Terrain ground = world.terrain();
        for (Dummy d : world.dummies()) out.at(d.position(), 0.5, 0.42f);
        for (FlyingDummy f : world.fliers()) {
            Vec3 at = ground.onGround(f.position());
            double h = Math.max(0, f.position().y() - at.y());
            double k = Math.min(1, h / 8);
            out.at(at, 0.65 * (1 - 0.35 * k), (float) (0.56 * (1 - 0.5 * k)));
        }
        for (Projectile p : world.combat().projectiles()) {
            if (p.stuck()) continue;
            Vec3 at = ground.onGround(p.position());
            double h = Math.max(0, p.position().y() - at.y());
            out.at(at, 0.14, (float) (0.28 * Math.max(0.25, 1 - h / 6)));
        }
    }

    /**
     * Queue the dummies, the shots and the hits' effects among the world's
     * sprites, each tested at the depth plane through its spot on the ground
     * ({@link Batch#depthAnchor}), as the player is - so a tree in front of a
     * dummy hides it, and one behind it does not.
     */
    public void sprites(World world, Batch batch, OrbitCamera camera, Draws out) {
        Vec3 eye = camera.eye(), right = camera.right(), up = camera.up(), forward = camera.forward();
        SpriteProfile framing = objects.profile(Dummy.SPRITES);
        for (Dummy d : world.dummies()) {
            Vec3 pivot = d.position().add(0, framing.pivotHeight(), 0);
            SpriteView view = SpriteView.of(eye, pivot, d.heading(), camera.yaw());
            ObjectStack.Result r = ObjectStack.resolve(sprites, objects, Dummy.SPRITES, d.sheet(), d.loops(),
                    d.time(), world.time(), view, 0, memory.computeIfAbsent(d, k -> new ObjectStack.Memory()));
            List<LayerStack.Layer> layers = r.layers();
            double flash = d.flash();
            if (flash > 0) {
                List<LayerStack.Layer> lit = new ArrayList<>(layers.size());
                for (LayerStack.Layer l : layers) lit.add(l.withPalette(CombatColours.flashed(l.palette(), flash)));
                layers = lit;
            }
            List<LayerStack.Layer> draw = layers;
            out.add(d.position().sub(eye).dot(forward), () -> {
                batch.depthAnchor(d.position());
                LayerStack.drawBillboard(batch, draw, r.framing(), r.view().elevation(), d.position(), right, up, 1f);
            });
        }

        SpriteProfile flying = objects.profile(FlyingDummy.SPRITES);
        for (FlyingDummy f : world.fliers()) {
            Vec3 pivot = f.position().add(0, flying.pivotHeight(), 0);
            SpriteView view = SpriteView.of(eye, pivot, f.heading(), camera.yaw());
            ObjectStack.Result r = ObjectStack.resolve(sprites, objects, FlyingDummy.SPRITES, f.sheet(), f.loops(),
                    f.sheetTime(), world.time(), view, 0, memory.computeIfAbsent(f, k -> new ObjectStack.Memory()));
            List<LayerStack.Layer> layers = r.layers();
            double flash = f.flash();
            if (flash > 0) {
                List<LayerStack.Layer> lit = new ArrayList<>(layers.size());
                for (LayerStack.Layer l : layers) lit.add(l.withPalette(CombatColours.flashed(l.palette(), flash)));
                layers = lit;
            }
            List<LayerStack.Layer> draw = layers;
            Vec3 at = f.position();
            out.add(at.sub(eye).dot(forward), () -> {
                batch.depthAnchor(at);
                LayerStack.drawBillboard(batch, draw, r.framing(), r.view().elevation(), at, right, up, 1f);
            });
        }

        Combat combat = world.combat();
        Terrain ground = world.terrain();
        for (Projectile p : combat.projectiles()) {
            String folder = switch (p.kind()) {
                case ARROW -> "arrow";
                case BOLT -> "bolt";
                default -> "magic_bolt";
            };
            SheetTexture sheet = fx.sheet(folder, "fly");
            if (sheet == null) continue;
            SpriteProfile prof = fx.profile(folder);
            boolean spell = p.kind() == Move.Kind.SPELL;
            int frame = spell ? (int) Math.floor(p.age() * prof.fps()) : 0;
            int[] palette = spell ? CombatColours.spellPalette(sheet.sharedPalette(), p.colour()) : sheet.sharedPalette();
            Vec3 dir = p.velocity().normalize(new Vec3(0, 0, -1));
            Vec3 at = p.position();
            float alpha = 1f;
            if (p.stuck()) {
                // the head in the grass, the shaft standing out of it
                at = at.sub(dir.scale(prof.frameWorldSize() * 0.38));
                alpha = (float) Math.max(0, Math.min(1, combat.tuning().stuckSeconds - p.stuckFor()));
            }
            Vec3 centre = at;
            float a = alpha;
            out.add(centre.sub(eye).dot(forward), () -> {
                batch.depthAnchor(ground.onGround(centre));   // (the spot it flies over)
                along(batch, sheet, frame, palette, centre, dir, prof.frameWorldSize(), forward, right, a);
            });
        }

        for (Combat.Effect e : combat.effects()) {
            boolean burst = e.kind() == Combat.Effect.Kind.BURST;
            String folder = burst ? "magic_burst" : "hit_spark";
            SheetTexture sheet = fx.sheet(folder, "burst");
            if (sheet == null) continue;
            SpriteProfile prof = fx.profile(folder);
            int frame = (int) Math.floor((world.time() - e.born()) * prof.fps());
            if (frame >= sheet.frameCount()) continue;
            int[] palette = burst ? CombatColours.spellPalette(sheet.sharedPalette(), e.colour()) : sheet.sharedPalette();
            double s = prof.frameWorldSize();
            Vec3 tl = e.at().sub(right.scale(s / 2)).add(up.scale(s / 2));
            // just in front of what it struck
            out.add(e.at().sub(eye).dot(forward) - 0.5, () -> {
                batch.depthAnchor(ground.onGround(e.at()));
                region(batch, sheet, frame, palette, tl, right.scale(s), up.scale(-s), 1f);
            });
        }
    }

    /**
     * A sheet drawn head-first along {@code dir} as it crosses the screen:
     * the card turned in the screen's plane to point the way the shot flies,
     * and shortened as it flies toward or away from the camera.
     */
    private static void along(Batch batch, SheetTexture sheet, int frame, int[] palette, Vec3 centre, Vec3 dir,
                              double size, Vec3 forward, Vec3 cameraRight, float alpha) {
        Vec3 flat = dir.sub(forward.scale(dir.dot(forward)));
        double len = flat.length();
        Vec3 r = len > 1e-4 ? flat.scale(1 / len) : cameraRight;
        Vec3 down = forward.cross(r).normalize(Vec3.ZERO);
        double stretch = Math.max(0.3, Math.min(1, len));
        Vec3 right = r.scale(size * stretch), d = down.scale(size);
        Vec3 tl = centre.sub(right.scale(0.5)).sub(d.scale(0.5));
        region(batch, sheet, frame, palette, tl, right, d, alpha);
    }

    /** One frame of {@code sheet} on the card {@code topLeft} + {@code right} × {@code down} (its cells are cropped). */
    static void region(Batch batch, SheetTexture sheet, int frame, int[] palette, Vec3 topLeft, Vec3 right, Vec3 down,
                       float alpha) {
        double[] reg = sheet.region(false);
        if (reg[2] <= reg[0] || reg[3] <= reg[1]) return;
        float[] uv = sheet.uv(frame, false);
        Vec3 tl = topLeft.add(right.scale(reg[0])).add(down.scale(reg[1]));
        batch.quadClipped(sheet.texture(), tl, right.scale(reg[2] - reg[0]), down.scale(reg[3] - reg[1]),
                uv[0], uv[1], uv[2], uv[3], uv[0], uv[1], uv[2], uv[3], 1, 1, 1, alpha,
                batch.palette(sheet.indexed() ? palette : null));
    }
}
