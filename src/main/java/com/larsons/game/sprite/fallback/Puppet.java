package com.larsons.game.sprite.fallback;

import com.larsons.game.math.Mat4;
import com.larsons.game.sprite.AnimState;

import java.util.ArrayList;
import java.util.List;

/**
 * The fallback character: a little box figure, posed for each animation state.
 *
 * <p>It exists so the game has something correct to draw before any Blender
 * render has been dropped in. Rather than hand-drawing 24 views of every
 * frame, it is built like the real thing — a rigged 3D figure — and
 * {@link PuppetRaster} renders it from the same 24 camera positions the
 * Blender sheets are rendered from. So the fallback also serves as a working
 * reference for the sprite conventions: framing, pivot, facing keys, and a
 * sword on its own layer, cut out where the hand and body cover it.
 *
 * <p>Proportions are in metres: 1.8 m to the crown, hips at 0.95 m. The
 * character faces −Z with its right hand on +X; joints swing about X
 * (positive = forward).
 */
final class Puppet {

    // Palette — flat colours, like the engine's box models.
    static final int SKIN = 0xE9BC96;
    static final int SHIRT = 0x4A6EBE;
    static final int SHIRT_DARK = 0x3A5698;
    static final int PANTS = 0x3C3C52;
    static final int SHOES = 0x6E462D;
    static final int HAIR = 0x50321E;
    static final int EYES = 0x191923;
    static final int BLADE = 0xD2DAE6;
    static final int GUARD = 0xD7AA3C;
    static final int GRIP = 0x643C23;
    static final int CAP = 0xC0392B;
    static final int CAP_DARK = 0x922B21;

    private Puppet() {}

    /** Joint angles and root offsets for one frame. Radians and metres. */
    static final class Pose {
        double bob, lean, twist;
        double thighL, thighR, kneeL, kneeR;
        double armL, armR, elbowL, elbowR;
        double spreadL = 0.08, spreadR = 0.08;
        double wrist = 0.35;
        double headNod;
    }

    /**
     * The boxes of the figure in {@code state}, {@code phase} of the way
     * through the animation (0 inclusive to 1 exclusive for looping states,
     * 0 to 1 inclusive for one-shots).
     *
     * @param withSword whether the right hand holds the sword
     */
    static List<Box> build(AnimState state, double phase, boolean withSword) {
        return build(state, phase, withSword, false);
    }

    /** As {@link #build(AnimState, double, boolean)}, optionally wearing a cap (sample set). */
    static List<Box> build(AnimState state, double phase, boolean withSword, boolean withCap) {
        Pose p = pose(state, phase);
        List<Box> out = new ArrayList<>();

        // Root: bob up and down, lean forward (top toward −Z), twist about Y.
        Mat4 root = Mat4.translation(0, p.bob, 0)
                .mul(Mat4.translation(0, 0.95, 0))
                .mul(Mat4.rotationY(p.twist))
                .mul(Mat4.rotationX(-p.lean));

        // Torso, with a darker belt band at the bottom.
        out.add(box(root.mul(Mat4.translation(0, 0.30, 0)), 0.17, 0.24, 0.10, SHIRT));
        out.add(box(root.mul(Mat4.translation(0, 0.05, 0)), 0.175, 0.04, 0.105, SHIRT_DARK));

        // Head on the neck, nodding with the lean.
        Mat4 neck = root.mul(Mat4.translation(0, 0.56, 0)).mul(Mat4.rotationX(-p.headNod));
        Mat4 head = neck.mul(Mat4.translation(0, 0.14, 0));
        out.add(box(head, 0.13, 0.14, 0.13, SKIN));
        // Hair: a cap over the top and down the back, leaving the face bare.
        out.add(box(head.mul(Mat4.translation(0, 0.08, 0.02)), 0.14, 0.08, 0.135, HAIR));
        out.add(box(head.mul(Mat4.translation(0, -0.02, 0.09)), 0.14, 0.10, 0.06, HAIR));
        // Eyes on the front face, so front and back never look alike.
        out.add(box(head.mul(Mat4.translation(-0.06, 0.0, -0.13)), 0.035, 0.035, 0.012, EYES));
        out.add(box(head.mul(Mat4.translation(0.06, 0.0, -0.13)), 0.035, 0.035, 0.012, EYES));
        if (withCap) {
            out.add(new Box(head.mul(Mat4.translation(0, 0.15, 0.01)), 0.15, 0.06, 0.15, CAP, Box.Layer.HAT));
            out.add(new Box(head.mul(Mat4.translation(0, 0.10, -0.16)), 0.13, 0.015, 0.08, CAP_DARK,
                    Box.Layer.HAT));
        }

        // Arms: shoulder → upper arm → elbow → forearm → hand.
        Mat4 handR = arm(out, root, +1, p.armR, p.spreadR, p.elbowR);
        arm(out, root, -1, p.armL, -p.spreadL, p.elbowL);

        // Legs: hip → thigh → knee → shin → foot.
        leg(out, root, +1, p.thighR, p.kneeR);
        leg(out, root, -1, p.thighL, p.kneeL);

        if (withSword) sword(out, handR.mul(Mat4.rotationX(p.wrist)));
        return out;
    }

    /** One arm; returns the hand's frame (at the grip, forearm axis along −Y). */
    private static Mat4 arm(List<Box> out, Mat4 root, int side, double swing, double spread,
                            double elbow) {
        Mat4 shoulder = root.mul(Mat4.translation(side * 0.225, 0.50, 0))
                .mul(Mat4.rotationZ(spread))
                .mul(Mat4.rotationX(swing));
        out.add(box(shoulder.mul(Mat4.translation(0, -0.14, 0)), 0.06, 0.15, 0.06, SHIRT));
        Mat4 forearm = shoulder.mul(Mat4.translation(0, -0.28, 0)).mul(Mat4.rotationX(elbow));
        out.add(box(forearm.mul(Mat4.translation(0, -0.12, 0)), 0.05, 0.13, 0.05, SKIN));
        Mat4 hand = forearm.mul(Mat4.translation(0, -0.27, 0));
        out.add(box(hand, 0.055, 0.05, 0.055, SKIN));
        return hand;
    }

    private static void leg(List<Box> out, Mat4 root, int side, double swing, double knee) {
        Mat4 hip = root.mul(Mat4.translation(side * 0.09, -0.02, 0)).mul(Mat4.rotationX(swing));
        out.add(box(hip.mul(Mat4.translation(0, -0.21, 0)), 0.075, 0.22, 0.075, PANTS));
        Mat4 shin = hip.mul(Mat4.translation(0, -0.42, 0)).mul(Mat4.rotationX(-knee));
        out.add(box(shin.mul(Mat4.translation(0, -0.20, 0)), 0.068, 0.21, 0.068, PANTS));
        Mat4 ankle = shin.mul(Mat4.translation(0, -0.43, 0));
        out.add(box(ankle.mul(Mat4.translation(0, -0.01, -0.04)), 0.07, 0.045, 0.12, SHOES));
    }

    /**
     * The sword, gripped in the hand: blade along the hand's −Z. Its own
     * layer, so the raster can cut it out wherever the body covers it — the
     * same thing a Blender holdout does to the real sword sheets.
     */
    private static void sword(List<Box> out, Mat4 grip) {
        out.add(new Box(grip.mul(Mat4.translation(0, 0, 0.05)), 0.035, 0.035, 0.08, GRIP,
                Box.Layer.SWORD));
        out.add(new Box(grip.mul(Mat4.translation(0, 0, -0.05)), 0.12, 0.035, 0.035, GUARD,
                Box.Layer.SWORD));
        out.add(new Box(grip.mul(Mat4.translation(0, 0, -0.45)), 0.04, 0.035, 0.37, BLADE,
                Box.Layer.SWORD));
    }

    private static Box box(Mat4 t, double hx, double hy, double hz, int rgb) {
        return new Box(t, hx, hy, hz, rgb, Box.Layer.BODY);
    }

    // --- poses ----------------------------------------------------------------------

    static Pose pose(AnimState state, double phase) {
        return switch (state) {
            case IDLE -> idle(phase);
            case WALK -> gait(phase, 0.45, 0.10, 0.45, 0.40, 0.25, 0.0, 0.035);
            case RUN -> gait(phase, 0.80, 0.30, 1.10, 0.85, 1.25, 0.14, 0.06);
            case SPRINT -> gait(phase, 0.95, 0.35, 1.60, 1.15, 1.50, 0.30, 0.07);
            case JUMP -> jump(phase);
            case ATTACK -> attack(phase);
        };
    }

    private static Pose idle(double phase) {
        Pose p = new Pose();
        double breath = Math.sin(phase * Math.PI * 2);
        // Exaggerated for 32 pixels: at 7.5 cm a pixel, real breathing would
        // not move a single one.
        p.bob = 0.03 * breath;
        p.armL = 0.10 * breath;
        p.armR = -0.10 * breath;
        p.elbowL = p.elbowR = 0.15;
        p.spreadL = p.spreadR = 0.10 + 0.04 * breath;
        p.headNod = 0.06 * breath;
        return p;
    }

    /**
     * Walk, run and sprint share one cycle with different amplitudes: legs in
     * opposition, the knee folding as the leg swings through, arms opposite
     * the legs, the body highest as the legs pass.
     */
    private static Pose gait(double phase, double legSwing, double kneeBase, double kneeFold,
                             double armSwing, double elbow, double lean, double bob) {
        Pose p = new Pose();
        double a = phase * Math.PI * 2;
        double s = Math.sin(a);
        // A leg reaches further forward than back, and its knee folds on the
        // way through and is still bent at the front — which is what keeps a
        // sprint from looking like a leap into the splits.
        p.thighL = thigh(legSwing, s);
        p.thighR = thigh(legSwing, -s);
        p.kneeL = kneeBase + kneeFold * Math.max(0, Math.cos(a - 0.6));
        p.kneeR = kneeBase + kneeFold * Math.max(0, Math.cos(a + Math.PI - 0.6));
        p.armL = -armSwing * s;
        p.armR = armSwing * s;
        p.elbowL = p.elbowR = elbow;
        p.lean = lean;
        p.bob = bob * (Math.abs(Math.cos(a)) - 0.5);
        p.headNod = -lean * 0.5;
        return p;
    }

    private static double thigh(double swing, double s) {
        return s > 0 ? swing * s : swing * 0.65 * s;
    }

    /** Crouch, launch with the arms thrown up, tuck in the air, land. */
    private static Pose jump(double phase) {
        //                    t     bob    lean  thigh  knee  arm   elbow
        double[][] keys = {
                {0.00, 0.00, 0.00, 0.00, 0.10, 0.10, 0.20},
                {0.20, -0.18, 0.25, 0.70, 1.40, -0.70, 0.30},
                {0.35, 0.02, -0.05, -0.05, 0.05, 2.70, 0.10},
                {0.55, 0.00, 0.05, 0.95, 1.50, 1.90, 0.60},
                {0.80, 0.00, 0.05, 0.60, 0.90, 1.00, 0.40},
                {0.90, -0.12, 0.20, 0.55, 1.10, -0.20, 0.30},
                {1.00, 0.00, 0.00, 0.00, 0.10, 0.10, 0.20},
        };
        double[] k = sample(keys, phase);
        Pose p = new Pose();
        p.bob = k[1];
        p.lean = k[2];
        p.thighL = p.thighR = k[3];
        p.kneeL = p.kneeR = k[4];
        p.armL = p.armR = k[5];
        p.elbowL = p.elbowR = k[6];
        p.spreadL = p.spreadR = 0.22;
        p.headNod = -k[2] * 0.4;
        return p;
    }

    /** An overhead sword swing: wind up behind the head, strike forward and down, recover. */
    private static Pose attack(double phase) {
        //                    t     armR  elbowR twist  lean  armL  thighR thighL wrist
        double[][] keys = {
                {0.00, 0.20, 0.30, 0.00, 0.00, 0.10, 0.00, 0.00, 0.35},
                {0.35, 2.90, 0.50, -0.45, -0.08, -0.40, 0.15, -0.10, 0.60},
                {0.50, 0.25, 0.05, 0.40, 0.25, 0.50, 0.45, -0.30, 0.10},
                {0.65, 0.05, 0.10, 0.35, 0.22, 0.35, 0.40, -0.25, 0.05},
                {1.00, 0.20, 0.30, 0.00, 0.00, 0.10, 0.00, 0.00, 0.35},
        };
        double[] k = sample(keys, phase);
        Pose p = new Pose();
        p.armR = k[1];
        p.elbowR = k[2];
        p.twist = k[3];
        p.lean = k[4];
        p.armL = k[5];
        p.elbowL = 0.4;
        p.thighR = k[6];
        p.thighL = k[7];
        p.kneeR = 0.25 + Math.max(0, k[6]) * 0.6;
        p.kneeL = 0.15;
        p.wrist = k[8];
        p.spreadR = 0.12;
        p.headNod = -k[4] * 0.3;
        return p;
    }

    /** Smoothstep interpolation through keyframe rows {@code {t, values…}}. */
    static double[] sample(double[][] keys, double t) {
        t = Math.max(0, Math.min(1, t));
        for (int i = 0; i < keys.length - 1; i++) {
            double[] a = keys[i], b = keys[i + 1];
            if (t <= b[0]) {
                double u = (t - a[0]) / Math.max(1e-9, b[0] - a[0]);
                u = u * u * (3 - 2 * u);
                double[] out = new double[a.length];
                for (int j = 0; j < a.length; j++) out[j] = a[j] + (b[j] - a[j]) * u;
                return out;
            }
        }
        return keys[keys.length - 1].clone();
    }
}
