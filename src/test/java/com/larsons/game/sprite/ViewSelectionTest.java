package com.larsons.game.sprite;

import com.larsons.game.math.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Which of the 24 views gets drawn: the eight directions and the three elevation zones. */
class ViewSelectionTest {

    @Test
    void elevationZonesFollowTheDesign() {
        // side 0–33°, middle 34–75°, top 75–90°
        assertEquals(Elevation.SIDE, Elevation.forAngle(0));
        assertEquals(Elevation.SIDE, Elevation.forAngle(33));
        assertEquals(Elevation.MIDDLE, Elevation.forAngle(34));
        assertEquals(Elevation.MIDDLE, Elevation.forAngle(45));
        assertEquals(Elevation.MIDDLE, Elevation.forAngle(74.9));
        assertEquals(Elevation.TOP, Elevation.forAngle(75));
        assertEquals(Elevation.TOP, Elevation.forAngle(90));
        assertEquals(0, Elevation.SIDE.renderAngle());
        assertEquals(45, Elevation.MIDDLE.renderAngle());
        assertEquals(90, Elevation.TOP.renderAngle());
    }

    @Test
    void facingSnapsToTheNearestOfEight() {
        assertEquals(Facing.EAST, Facing.of(1, 0, null));
        assertEquals(Facing.SOUTH, Facing.of(0, 1, null));
        assertEquals(Facing.NORTH, Facing.of(0, -1, null));
        assertEquals(Facing.SOUTH_WEST, Facing.of(-1, 1, null));
        assertEquals(Facing.NORTH_EAST, Facing.of(0.9, -1.1, null));
        assertEquals(Facing.WEST, Facing.of(0, 0, Facing.WEST), "no movement keeps the old facing");
    }

    @Test
    void mirrorTwinsPairEastAndWest() {
        assertEquals(Facing.EAST, Facing.WEST.mirrorOf());
        assertEquals(Facing.NORTH_WEST, Facing.NORTH_EAST.mirrorOf());
        assertFalse(Facing.NORTH.hasMirror());
        assertFalse(Facing.SOUTH.hasMirror());
        for (Facing f : Facing.values()) assertEquals(f, f.clockwise().counterClockwise());
    }

    @Test
    void characterFacingAwayFromTheCameraShowsItsBack() {
        Vec3 pivot = new Vec3(0, 0.9, 0);
        Vec3 eye = new Vec3(0, 0.9, 5);           // camera on +Z, level, yaw 0
        assertEquals(Facing.NORTH, SpriteView.of(eye, pivot, 0, 0).facing());
        assertEquals(Facing.SOUTH, SpriteView.of(eye, pivot, Math.PI, 0).facing());
        // heading π/2 faces −X: the viewer's left
        assertEquals(Facing.WEST, SpriteView.of(eye, pivot, Math.PI / 2, 0).facing());
        assertEquals(Facing.EAST, SpriteView.of(eye, pivot, -Math.PI / 2, 0).facing());
    }

    @Test
    void headingShowingIsTheInverseOfTheViewForEveryDirectionAndYaw() {
        for (double yaw : new double[]{0, 0.3, 1.2, -2.5, 3.1}) {
            Vec3 pivot = new Vec3(0, 0.9, 0);
            Vec3 eye = pivot.add(Math.sin(yaw) * 6, 2, Math.cos(yaw) * 6);
            for (Facing f : Facing.values()) {
                double h = SpriteView.headingShowing(f, yaw);
                assertEquals(f, SpriteView.of(eye, pivot, h, yaw).facing(), "yaw " + yaw + " " + f);
            }
        }
    }

    @Test
    void elevationIsMeasuredFromThePivot() {
        Vec3 pivot = new Vec3(0, 0.9, 0);
        assertEquals(Elevation.SIDE, SpriteView.of(new Vec3(0, 0.9, 5), pivot, 0, 0).elevation());
        assertEquals(Elevation.MIDDLE, SpriteView.of(new Vec3(0, 5.9, 5), pivot, 0, 0).elevation());
        SpriteView overhead = SpriteView.of(new Vec3(0, 8, 0), pivot, 0, 0);
        assertEquals(Elevation.TOP, overhead.elevation());
        assertEquals(90, overhead.elevationDegrees(), 1e-9);
        // Straight overhead the camera's yaw decides which way is "up the screen".
        assertEquals(Facing.NORTH, overhead.facing());
        assertEquals(Facing.SOUTH, SpriteView.of(new Vec3(0, 8, 0), pivot, 0, Math.PI).facing());
    }

    @Test
    void framesWrapOrHold() {
        assertEquals(0, AnimState.WALK.frameAt(0, 30, 30));
        assertEquals(15, AnimState.WALK.frameAt(0.5, 30, 30));
        assertEquals(0, AnimState.WALK.frameAt(1.0, 30, 30), "looping states wrap");
        assertEquals(17, AnimState.ATTACK.frameAt(5.0, 30, 18), "one-shots hold the last frame");
        assertEquals(0.6, AnimState.duration(18, 30), 1e-9);
    }

    @Test
    void layersPlayTheBodysFrame() {
        assertEquals(7, LayerStack.mapFrame(7, 30, 30));
        assertEquals(3, LayerStack.mapFrame(7, 30, 15));
        assertEquals(14, LayerStack.mapFrame(29, 30, 15));
        assertEquals(0, LayerStack.mapFrame(12, 30, 1));
    }
}
