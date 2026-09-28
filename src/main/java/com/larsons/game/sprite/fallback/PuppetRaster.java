package com.larsons.game.sprite.fallback;

import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;

import java.util.EnumMap;
import java.util.List;

/**
 * A tiny z-buffered software rasteriser that renders the {@link Puppet} the
 * way Blender renders the real sprite sheets: an orthographic camera aimed at
 * the character's pivot from one of 24 view angles, one image per layer.
 *
 * <p><b>Why layers need a depth test between them.</b> The sword is rendered
 * into a layer of its own, and a pixel of it is kept only where it is nearer
 * the camera than the body. That is exactly what a holdout does in Blender:
 * the sword is "cut out where the player's hand is located", and where the
 * body passes in front of the blade. Drawing the body layer and then the sword
 * layer on top therefore gives the same picture as rendering the two together
 * — which is the contract every stacked sprite sheet in this game relies on.
 *
 * <p>Only runs at startup and on demand, at 32×32 — the fallback art is meant
 * to be tiny and scaled up, with crisp pixel edges.
 */
final class PuppetRaster {

    /** The framing every fallback frame uses (see {@code SpriteProfile}). */
    private final int size;
    private final double metresPerPixel;
    private final double pivotHeight;

    PuppetRaster(int size, double frameWorldSize, double pivotHeight) {
        this.size = size;
        this.metresPerPixel = frameWorldSize / size;
        this.pivotHeight = pivotHeight;
    }

    /** The layers of one rendered frame, as ARGB pixels, row major. */
    record Frame(EnumMap<Box.Layer, int[]> layers) {
        int[] body() { return layer(Box.Layer.BODY); }

        int[] sword() { return layer(Box.Layer.SWORD); }

        int[] layer(Box.Layer l) {
            int[] px = layers.get(l);
            return px != null ? px : new int[0];
        }
    }

    /** Whether silhouettes get a one-pixel dark outline (pixel art yes, HD renders no). */
    private boolean outlines = true;

    PuppetRaster withoutOutlines() {
        outlines = false;
        return this;
    }

    /** Render {@code boxes} seen from {@code elevation} with the character showing {@code facing}. */
    Frame render(List<Box> boxes, Elevation elevation, Facing facing) {
        int n = size * size;
        EnumMap<Box.Layer, int[]> color = new EnumMap<>(Box.Layer.class);
        EnumMap<Box.Layer, float[]> depth = new EnumMap<>(Box.Layer.class);
        for (Box.Layer l : Box.Layer.values()) {
            color.put(l, new int[n]);
            float[] d = new float[n];
            java.util.Arrays.fill(d, Float.NEGATIVE_INFINITY);
            depth.put(l, d);
        }

        View view = new View(elevation.renderAngle(), facing);
        for (Box b : boxes) drawBox(b, view, color.get(b.layer()), depth.get(b.layer()));

        // The holdout: every layer above the body keeps a pixel only where it
        // is in front of the body — the sword disappears into the fist and
        // behind the back, the cap's brim behind the head.
        int[] body = color.get(Box.Layer.BODY);
        float[] bodyDepth = depth.get(Box.Layer.BODY);
        for (Box.Layer l : Box.Layer.values()) {
            if (l == Box.Layer.BODY) continue;
            int[] c = color.get(l);
            float[] d = depth.get(l);
            for (int i = 0; i < n; i++) {
                if (c[i] != 0 && body[i] != 0 && d[i] <= bodyDepth[i]) c[i] = 0;
            }
        }
        if (outlines) {
            outline(body, null, 0xFF1E181E);
            for (Box.Layer l : Box.Layer.values()) {
                if (l != Box.Layer.BODY) outline(color.get(l), body, 0xFF26242C);
            }
        }
        return new Frame(color);
    }

    /**
     * The orthographic camera: character turned so its forward shows as
     * {@code facing}, camera {@code angle} degrees above the horizon on the +Z
     * side looking at the pivot.
     */
    private final class View {
        final double cosE, sinE;
        final Mat4 turn;
        final double pivotY;

        View(double angleDegrees, Facing facing) {
            double e = Math.toRadians(angleDegrees);
            cosE = Math.cos(e);
            sinE = Math.sin(e);
            // The puppet faces −Z. A facing's screen vector (dx right, dy toward
            // the viewer) is world (dx, 0, dy) for a camera on the +Z side.
            double len = Math.hypot(facing.dx(), facing.dy());
            double fx = facing.dx() / len, fz = facing.dy() / len;
            turn = Mat4.rotationY(Math.atan2(-fx, -fz));
            // Where the pivot lands on the camera's up axis.
            pivotY = pivotHeight * cosE;
        }

        /** Character space → {x pixels, y pixels, depth (bigger = nearer)}. */
        double[] project(Vec3 p) {
            Vec3 w = turn.transformPoint(p);
            double sx = w.x();
            double sy = w.y() * cosE - w.z() * sinE;
            double depth = w.y() * sinE + w.z() * cosE;
            return new double[]{
                    size / 2.0 + sx / metresPerPixel,
                    size / 2.0 - (sy - pivotY) / metresPerPixel,
                    depth};
        }

        /** A normal in character space → camera space (x right, y up, z toward the camera). */
        Vec3 normal(Vec3 n) {
            Vec3 w = turn.transformDirection(n);
            return new Vec3(w.x(), w.y() * cosE - w.z() * sinE, w.y() * sinE + w.z() * cosE);
        }
    }

    /** Light from over the camera's left shoulder, in camera space. */
    private static final Vec3 LIGHT = new Vec3(-0.45, 0.75, 0.55).normalize();

    private static final int[][] FACES = {
            // corner indices (of the 8 below), counter-clockwise seen from outside
            {1, 5, 7, 3}, // +x
            {4, 0, 2, 6}, // -x
            {2, 3, 7, 6}, // +y
            {4, 5, 1, 0}, // -y
            {5, 4, 6, 7}, // +z
            {0, 1, 3, 2}, // -z
    };
    private static final Vec3[] NORMALS = {
            new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 1, 0),
            new Vec3(0, -1, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1),
    };

    private void drawBox(Box b, View view, int[] color, float[] depth) {
        double[][] corners = new double[8][];
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) != 0 ? b.hx() : -b.hx();
            double y = (i & 2) != 0 ? b.hy() : -b.hy();
            double z = (i & 4) != 0 ? b.hz() : -b.hz();
            corners[i] = view.project(b.transform().transformPoint(new Vec3(x, y, z)));
        }
        for (int f = 0; f < 6; f++) {
            Vec3 n = view.normal(b.transform().transformDirection(NORMALS[f]).normalize());
            if (n.z() <= 1e-6) continue; // facing away from the camera
            double light = 0.58 + 0.42 * Math.max(0, n.dot(LIGHT));
            int rgb = 0xFF000000 | shade(b.rgb(), light);
            int[] q = FACES[f];
            triangle(corners[q[0]], corners[q[1]], corners[q[2]], rgb, color, depth);
            triangle(corners[q[0]], corners[q[2]], corners[q[3]], rgb, color, depth);
        }
    }

    private void triangle(double[] a, double[] b, double[] c, int rgb, int[] color, float[] depth) {
        double area = edge(a, b, c[0], c[1]);
        if (Math.abs(area) < 1e-12) return;
        int minX = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int maxX = Math.min(size - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int maxY = Math.min(size - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        for (int y = minY; y <= maxY; y++) {
            double py = y + 0.5;
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5;
                double w0 = edge(b, c, px, py) / area;
                double w1 = edge(c, a, px, py) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < -1e-9 || w1 < -1e-9 || w2 < -1e-9) continue;
                float z = (float) (w0 * a[2] + w1 * b[2] + w2 * c[2]);
                int i = y * size + x;
                if (z > depth[i]) {
                    depth[i] = z;
                    color[i] = rgb;
                }
            }
        }
    }

    private static double edge(double[] a, double[] b, double px, double py) {
        return (b[0] - a[0]) * (py - a[1]) - (b[1] - a[1]) * (px - a[0]);
    }

    /**
     * A one-pixel dark outline around a layer's silhouette, which is what
     * keeps a 32-pixel figure readable once it is scaled up twenty times.
     * {@code avoid}, when given, is a layer the outline must not paint over.
     */
    private void outline(int[] layer, int[] avoid, int ink) {
        int[] copy = layer.clone();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                if (copy[i] != 0) continue;
                if (avoid != null && avoid[i] != 0) continue;
                boolean edge = (x > 0 && copy[i - 1] != 0) || (x < size - 1 && copy[i + 1] != 0)
                        || (y > 0 && copy[i - size] != 0) || (y < size - 1 && copy[i + size] != 0);
                if (edge) layer[i] = ink;
            }
        }
    }

    private static int shade(int rgb, double f) {
        int r = clamp(((rgb >> 16) & 0xFF) * f);
        int g = clamp(((rgb >> 8) & 0xFF) * f);
        int b = clamp((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}
