package com.larsons.game.sprite;

import com.larsons.game.math.Vec3;

/**
 * Which of a character's 24 pre-rendered views (8 directions × 3 elevations)
 * the camera is looking at it from.
 *
 * <p>Worked out per character, not per camera: a character off to the side of
 * the screen is seen from a slightly different angle than the one in the
 * middle, and a character standing on something is seen from lower down. For
 * the player at the centre of the orbit camera the answer is simply the
 * camera's own pitch and yaw.
 *
 * <p><b>Heading convention.</b> A heading of {@code h} radians faces the
 * character along {@code (−sin h, 0, −cos h)} — the same convention as the
 * camera's yaw — so a character whose heading equals the camera's yaw is
 * facing away from the viewer and shows its {@link Facing#NORTH} sprite.
 *
 * @param elevation        the zone to draw
 * @param facing           the direction to draw
 * @param elevationDegrees the exact angle of the camera above the pivot (for the HUD)
 */
public record SpriteView(Elevation elevation, Facing facing, double elevationDegrees) {

    /** The unit vector a heading faces along. */
    public static Vec3 headingVector(double heading) {
        return new Vec3(-Math.sin(heading), 0, -Math.cos(heading));
    }

    /** The heading that faces along a horizontal vector. */
    public static double headingOf(double dx, double dz) {
        return Math.atan2(-dx, -dz);
    }

    /**
     * The view of a character with its pivot at {@code pivot}, facing
     * {@code heading}, from a camera at {@code eye} whose own yaw is
     * {@code cameraYaw} (used only when the camera is directly overhead and the
     * direction to the character is undefined).
     */
    public static SpriteView of(Vec3 eye, Vec3 pivot, double heading, double cameraYaw) {
        Vec3 toCharacter = pivot.sub(eye);
        double horizontal = toCharacter.horizontalLength();

        double degrees = Math.toDegrees(Math.atan2(-toCharacter.y(), horizontal));
        degrees = Math.max(0, Math.min(90, degrees));
        Elevation elevation = Elevation.forAngle(degrees);

        // The viewing direction flattened onto the ground: "away from the
        // viewer" in the picture. Straight overhead there is none, and the
        // camera's yaw decides which way is up the screen.
        double fx, fz;
        if (horizontal > 1e-6) {
            fx = toCharacter.x() / horizontal;
            fz = toCharacter.z() / horizontal;
        } else {
            Vec3 f = headingVector(cameraYaw);
            fx = f.x();
            fz = f.z();
        }
        // Screen right, for a camera looking along (fx, 0, fz) with +Y up.
        double rx = -fz, rz = fx;

        Vec3 h = headingVector(heading);
        double right = h.x() * rx + h.z() * rz;
        double away = h.x() * fx + h.z() * fz;
        Facing facing = Facing.of(right, -away, Facing.SOUTH);
        return new SpriteView(elevation, facing, degrees);
    }

    /**
     * The heading that shows {@code facing} to a camera at yaw
     * {@code cameraYaw} — the inverse of {@link #of} for the orbit centre.
     * Used by the preview turntable and the wardrobe.
     */
    public static double headingShowing(Facing facing, double cameraYaw) {
        // Facing.NORTH (away) is heading == yaw; each step counter-clockwise on
        // screen (N → NW → W) turns the character by +45°.
        int steps = Math.floorMod(facing.ordinal() - Facing.NORTH.ordinal(), 8);
        return cameraYaw + steps * Math.PI / 4.0;
    }
}
