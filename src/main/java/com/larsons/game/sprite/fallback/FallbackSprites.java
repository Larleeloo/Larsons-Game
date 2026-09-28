package com.larsons.game.sprite.fallback;

import com.larsons.game.math.Mat4;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.SheetImage;
import com.larsons.game.sprite.SpriteProfile;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The fallback profile: a complete 32×32 character — every animation state,
 * all eight directions, all three elevations — generated from the
 * {@link Puppet} when no base-body sheets have been provided, plus the sword
 * that goes with it.
 *
 * <p>Tiny on purpose and scaled up with nearest-neighbour filtering: pixel art
 * that is plainly a stand-in, but moves, turns and holds a sword exactly the
 * way the real 512-pixel sheets will.
 *
 * <p>Frame counts per state are {@link AnimState#defaultFrames()} at 30 fps —
 * the rate the Blender renders use — so the fallback and a real sheet time
 * the same.
 */
public final class FallbackSprites {

    /** Pixel size of a fallback frame. */
    public static final int SIZE = 32;

    /** The framing the fallback is rendered with: the default profile at 32 px. */
    public static final SpriteProfile PROFILE = SpriteProfile.defaults().withFrameSize(SIZE, SIZE);

    private FallbackSprites() {}

    /** The body and sword sheets of one view, generated together so they cut out correctly. */
    public record Pair(SheetImage body, SheetImage sword) {}

    /** Generate one view's sheets. Pure CPU; safe on any thread. */
    public static Pair generate(AnimState state, Elevation elevation, Facing facing) {
        PuppetRaster raster = new PuppetRaster(SIZE, PROFILE.frameWorldSize(), PROFILE.pivotHeight());
        int frames = state.defaultFrames();
        List<BufferedImage> body = new ArrayList<>(frames);
        List<BufferedImage> sword = new ArrayList<>(frames);
        for (int f = 0; f < frames; f++) {
            // Looping states end one frame short of the start, so the loop is
            // seamless; one-shots run all the way to their final pose.
            double phase = state.loops() ? f / (double) frames : f / (double) (frames - 1);
            PuppetRaster.Frame frame = raster.render(Puppet.build(state, phase, true), elevation, facing);
            body.add(image(frame.body()));
            sword.add(image(frame.sword()));
        }
        return new Pair(SheetImage.ofFrames(body, true, 4096), SheetImage.ofFrames(sword, true, 4096));
    }

    /** Every frame of one view of the sample set, one list per layer. */
    public record Layers(List<BufferedImage> body, List<BufferedImage> hat, List<BufferedImage> sword) {}

    /**
     * Render one view of the puppet at {@code size} pixels, wearing a cap and
     * holding the sword, each on its own held-out layer — the same kind of
     * output a Blender render of a layered character gives. Used by the
     * {@code sampleSprites} tool to make a stand-in set for testing the real
     * sheet pipeline and the importer.
     */
    public static Layers renderSample(AnimState state, Elevation elevation, Facing facing, int size) {
        PuppetRaster raster = new PuppetRaster(size, PROFILE.frameWorldSize(), PROFILE.pivotHeight());
        if (size > 64) raster.withoutOutlines();
        int frames = state.defaultFrames();
        List<BufferedImage> body = new ArrayList<>(), hat = new ArrayList<>(), sword = new ArrayList<>();
        for (int f = 0; f < frames; f++) {
            double phase = state.loops() ? f / (double) frames : f / (double) (frames - 1);
            PuppetRaster.Frame frame = raster.render(Puppet.build(state, phase, true, true), elevation, facing);
            body.add(image(frame.body(), size));
            hat.add(image(frame.layer(Box.Layer.HAT), size));
            sword.add(image(frame.sword(), size));
        }
        return new Layers(body, hat, sword);
    }

    /**
     * The world sprite for a sword lying on the ground: the same sword model,
     * laid diagonally across a 32×32 frame. Used when
     * {@code assets/sprites/items/sword/icon.png} is not provided.
     */
    public static BufferedImage swordIcon() {
        PuppetRaster raster = new PuppetRaster(SIZE, 1.3, 0);
        List<Box> boxes = new ArrayList<>();
        // Tip up and to the right, seen flat: centre the sword on the origin,
        // stand the blade (along −Z) up into the camera's plane, tilt it 45°.
        Mat4 t = Mat4.rotationZ(Math.toRadians(-45))
                .mul(Mat4.rotationX(Math.toRadians(90)))
                .mul(Mat4.translation(0, 0, 0.36));
        boxes.add(new Box(t.mul(Mat4.translation(0, 0, 0.05)), 0.035, 0.035, 0.08, Puppet.GRIP, Box.Layer.SWORD));
        boxes.add(new Box(t.mul(Mat4.translation(0, 0, -0.05)), 0.12, 0.035, 0.035, Puppet.GUARD, Box.Layer.SWORD));
        boxes.add(new Box(t.mul(Mat4.translation(0, 0, -0.45)), 0.045, 0.02, 0.37, Puppet.BLADE, Box.Layer.SWORD));
        PuppetRaster.Frame frame = raster.render(boxes, Elevation.SIDE, Facing.NORTH);
        return image(frame.sword());
    }

    private static BufferedImage image(int[] argb) {
        return image(argb, SIZE);
    }

    private static BufferedImage image(int[] argb, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, size, size, argb, 0, size);
        return img;
    }

    /** Whether a generated frame has any pixels at all (tests). */
    static boolean anyPixels(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) return true;
            }
        }
        return false;
    }
}
