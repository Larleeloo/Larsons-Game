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

    /**
     * A stand-in world sprite for a stance weapon ({@code battle_axe},
     * {@code longbow}, {@code crossbow}, {@code crystal_staff}, {@code wand},
     * {@code mace}, {@code dagger}) until it has an {@code icon.png}: a few
     * boxes, like the sword's. The one-handed weapons are short, so each is
     * drawn in a frame its own size (its world size) to fill the icon.
     */
    public static BufferedImage weaponIcon(String item) {
        double across = switch (item) {
            case "wand" -> 0.4;
            case "mace" -> 0.75;
            case "dagger" -> 0.5;
            default -> 1.5;
        };
        PuppetRaster raster = new PuppetRaster(SIZE, across, 0);
        List<Box> boxes = new ArrayList<>();
        Mat4 t = Mat4.rotationZ(Math.toRadians(-45)).mul(Mat4.rotationX(Math.toRadians(90)));
        switch (item) {
            case "battle_axe" -> {
                boxes.add(new Box(t, 0.025, 0.025, 0.45, Puppet.GRIP, Box.Layer.SWORD));
                boxes.add(new Box(t.mul(Mat4.translation(0.12, 0, -0.34)), 0.12, 0.015, 0.12, Puppet.BLADE,
                        Box.Layer.SWORD));
                boxes.add(new Box(t.mul(Mat4.translation(-0.08, 0, -0.34)), 0.08, 0.015, 0.09, Puppet.BLADE,
                        Box.Layer.SWORD));
            }
            case "longbow" -> {
                // the limbs as a bent chain of staves on an arc, the string across its tips
                double r = 0.8;
                for (int i = -4; i <= 4; i++) {
                    double a = i * 0.16;
                    boxes.add(new Box(t.mul(Mat4.translation(r * (1 - Math.cos(a)) - 0.08, 0, r * Math.sin(a)))
                            .mul(Mat4.rotationY(-a)), 0.022, 0.022, 0.07, Puppet.GRIP, Box.Layer.SWORD));
                }
                boxes.add(new Box(t.mul(Mat4.translation(r * (1 - Math.cos(0.64)) - 0.08, 0, 0)),
                        0.006, 0.006, r * Math.sin(0.64), Puppet.BLADE, Box.Layer.SWORD));
            }
            case "crystal_staff" -> {
                boxes.add(new Box(t.mul(Mat4.translation(0, 0, 0.08)), 0.02, 0.02, 0.62, Puppet.GRIP,
                        Box.Layer.SWORD));
                boxes.add(new Box(t.mul(Mat4.translation(0, 0, -0.62)), 0.05, 0.04, 0.09, Puppet.BLADE,
                        Box.Layer.SWORD));
            }
            case "wand" -> {
                // 30 cm of carved wood, thin to the tip, the handle a little thicker and darker
                boxes.add(new Box(t, 0.007, 0.007, 0.15, Puppet.GRIP, Box.Layer.SWORD));
                boxes.add(new Box(t.mul(Mat4.translation(0, 0, 0.1)), 0.012, 0.012, 0.05, Puppet.HAIR,
                        Box.Layer.SWORD));
            }
            case "mace" -> {
                // a 50 cm haft, the flanged iron head on top and a knob at the foot: 63 cm
                boxes.add(new Box(t.mul(Mat4.translation(0, 0, 0.06)), 0.014, 0.014, 0.25, Puppet.GRIP,
                        Box.Layer.SWORD));
                boxes.add(new Box(t.mul(Mat4.translation(0, 0, -0.24)), 0.055, 0.045, 0.07, Puppet.BLADE,
                        Box.Layer.SWORD));
                boxes.add(new Box(t.mul(Mat4.translation(0, 0, 0.3)), 0.022, 0.022, 0.015, Puppet.BLADE,
                        Box.Layer.SWORD));
            }
            case "dagger" -> {
                // a short grip, a cross-guard and a 26 cm blade: 40 cm
                boxes.add(new Box(t.mul(Mat4.translation(0, 0, 0.14)), 0.012, 0.012, 0.05, Puppet.GRIP,
                        Box.Layer.SWORD));
                boxes.add(new Box(t.mul(Mat4.translation(0, 0, 0.195)), 0.017, 0.017, 0.012, Puppet.GUARD,
                        Box.Layer.SWORD));
                boxes.add(new Box(t.mul(Mat4.translation(0, 0, 0.08)), 0.045, 0.012, 0.01, Puppet.GUARD,
                        Box.Layer.SWORD));
                boxes.add(new Box(t.mul(Mat4.translation(0, 0, -0.06)), 0.016, 0.005, 0.13, Puppet.BLADE,
                        Box.Layer.SWORD));
            }
            default -> {
                boxes.add(new Box(t, 0.035, 0.03, 0.36, Puppet.GRIP, Box.Layer.SWORD));
                boxes.add(new Box(t.mul(Mat4.translation(0, 0, -0.3)), 0.34, 0.02, 0.025, Puppet.SHOES,
                        Box.Layer.SWORD));
                boxes.add(new Box(t.mul(Mat4.translation(0, 0.035, -0.05)), 0.008, 0.008, 0.25, Puppet.BLADE,
                        Box.Layer.SWORD));
            }
        }
        PuppetRaster.Frame frame = raster.render(boxes, Elevation.SIDE, Facing.NORTH);
        return image(frame.sword());
    }

    /**
     * A stand-in world sprite for something worn (a belt, a dye, a helmet)
     * until it has an {@code icon.png}: a small folded bundle in {@code rgb}
     * (its rarity's colour), outlined dark so a white one shows too.
     */
    public static BufferedImage wearableIcon(int rgb) {
        int[] argb = new int[SIZE * SIZE];
        int fill = 0xFF000000 | rgb, edge = 0xFF000000 | ((rgb >> 2) & 0x3F3F3F);
        int fold = 0xFF000000 | ((rgb >> 1) & 0x7F7F7F);
        int c = SIZE / 2, r = SIZE / 2 - 5;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int d = Math.abs(x - c) + Math.abs(y - c);
                if (d > r) continue;
                argb[y * SIZE + x] = d >= r - 1 ? edge : y == c || x == c ? fold : fill;
            }
        }
        return image(argb);
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
