package com.larsons.game.world;

import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraAndPlayerTest {

    /** The wardrobe's sword in the right hand and the shield on the left arm: the sword-and-shield stance. */
    private static Wardrobe swordAndShield() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        w.set(Slot.CARRY_LEFT, "round_shield");
        return w;
    }

    @Test
    void theCameraBasisStaysOrthonormalAllTheWayToBirdsEye() {
        for (double pitchDeg : new double[]{0, 12, 45, 80, 90}) {
            OrbitCamera c = new OrbitCamera(0.7, Math.toRadians(pitchDeg), 6);
            c.snapTo(new Vec3(0, 0.9, 0));
            Vec3 r = c.right(), u = c.up(), f = c.forward();
            assertEquals(1, r.length(), 1e-9);
            assertEquals(1, u.length(), 1e-9);
            assertEquals(1, f.length(), 1e-9);
            assertEquals(0, r.dot(u), 1e-9);
            assertEquals(0, r.dot(f), 1e-9);
            assertEquals(0, u.dot(f), 1e-9);
            assertEquals(6, c.eye().distance(c.target()), 1e-9);
            // The target projects to the centre of the screen.
            Vec3 ndc = c.viewProjection(16 / 9.0).transformPoint(c.target());
            assertEquals(0, ndc.x(), 1e-6);
            assertEquals(0, ndc.y(), 1e-6);
        }
    }

    @Test
    void presetsCycleThroughTheThreeZones() {
        OrbitCamera c = new OrbitCamera(0, 0, 6);
        assertEquals(0, c.cyclePreset());
        c.snapTo(Vec3.ZERO);
        assertEquals(Elevation.SIDE, c.elevation());
        c.cyclePreset();
        c.snapTo(Vec3.ZERO);
        assertEquals(Elevation.MIDDLE, c.elevation());
        c.cyclePreset();
        c.snapTo(Vec3.ZERO);
        assertEquals(Elevation.TOP, c.elevation());
    }

    @Test
    void screenUpPointsAwayWhenLookingStraightDown() {
        OrbitCamera c = new OrbitCamera(0, Math.PI / 2, 6);
        c.snapTo(Vec3.ZERO);
        assertEquals(-1, c.up().z(), 1e-9);
        Mat4 v = c.view();
        assertFalse(Double.isNaN(v.at(0, 0)));
    }

    private static final java.util.function.ToDoubleFunction<AnimState> DUR =
            s -> AnimState.duration(s.defaultFrames(), 30);

    @Test
    void movementPicksWalkRunAndSprint() {
        Player p = new Player(swordAndShield());
        for (int i = 0; i < 30; i++) p.update(1 / 60.0, new Player.Intent(1, 0, false, false, false, false), DUR);
        assertEquals(AnimState.WALK, p.state());
        for (int i = 0; i < 30; i++) p.update(1 / 60.0, new Player.Intent(1, 0, true, false, false, false), DUR);
        assertEquals(AnimState.RUN, p.state());
        for (int i = 0; i < 30; i++) p.update(1 / 60.0, new Player.Intent(1, 0, false, true, false, false), DUR);
        assertEquals(AnimState.SPRINT, p.state());
        assertTrue(p.speed() > Player.RUN_SPEED);
        for (int i = 0; i < 60; i++) p.update(1 / 60.0, Player.Intent.NONE, DUR);
        assertEquals(AnimState.IDLE, p.state());
    }

    @Test
    void aJumpCrouchesLeavesTheGroundAndLands() {
        Player p = new Player(swordAndShield());
        p.update(1 / 60.0, new Player.Intent(0, 0, false, false, true, false), DUR);
        assertEquals(AnimState.JUMP, p.state());
        assertEquals(0, p.height(), 1e-9, "still crouching");
        double peak = 0;
        for (int i = 0; i < 120; i++) {
            p.update(1 / 60.0, Player.Intent.NONE, DUR);
            peak = Math.max(peak, p.height());
        }
        assertTrue(peak > 0.2, "left the ground: " + peak);
        assertEquals(0, p.height(), 1e-9);
        assertEquals(AnimState.IDLE, p.state());
    }

    @Test
    void anAttackRootsThePlayerForItsDuration() {
        Player p = new Player(swordAndShield());
        p.update(1 / 60.0, new Player.Intent(0, 0, false, false, false, true), DUR);
        assertEquals(AnimState.ATTACK, p.state());
        Vec3 start = p.ground();
        for (int i = 0; i < 20; i++) p.update(1 / 60.0, new Player.Intent(1, 0, false, false, false, false), DUR);
        assertEquals(AnimState.ATTACK, p.state());
        assertEquals(0, start.distance(p.ground()), 1e-3, "no sliding mid-swing");
        for (int i = 0; i < 40; i++) p.update(1 / 60.0, Player.Intent.NONE, DUR);
        assertEquals(AnimState.IDLE, p.state());
    }

    @Test
    void previewLoopsAStateInPlaceUntilThePlayerMoves() {
        Player p = new Player(swordAndShield());
        p.preview(AnimState.ATTACK);
        for (int i = 0; i < 200; i++) p.update(1 / 60.0, Player.Intent.NONE, DUR);
        assertEquals(AnimState.ATTACK, p.state());
        p.update(1 / 60.0, new Player.Intent(1, 0, false, false, false, false), DUR);
        assertNull(p.previewing());
    }

    @Test
    void pickingUpTheSwordPutsItInTheRightHandAndDroppingItPutsItBack() {
        World w = new World(swordAndShield());
        w.spawn(ItemDef.SWORD, new Vec3(1, 0, 0));
        World.GroundItem g = w.reachable();
        assertNotNull(g);
        w.pickUp(g);
        assertTrue(w.player().wardrobe().wearing(ItemDef.SWORD.carrySlot(), "sword"));
        assertTrue(w.items().isEmpty());
        assertEquals(ItemDef.SWORD, w.dropHeld());
        assertNull(w.player().wardrobe().get(ItemDef.SWORD.carrySlot()));
        assertEquals(1, w.items().size());
    }
}
