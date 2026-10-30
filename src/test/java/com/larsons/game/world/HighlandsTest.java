package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Her on the hills: slopes, the rock she cannot walk up, falls and the roll out of them, climbing - up
 * over the lip and down over it - and stamina.
 */
class HighlandsTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);

    /** A world on the highlands, her standing at {@code (x, z)}. */
    private static World at(double x, double z) {
        Wardrobe w = new Wardrobe();
        w.set(Slot.CARRY_RIGHT, "sword");
        World world = new World(w);
        world.setTerrain(Terrain.highlands());
        world.player().setPosition(new Vec3(x, 0, z));
        return world;
    }

    private static Player.Intent walk(double x, double z) {
        return new Player.Intent(x, z, false, false, false, false);
    }

    private static Player.Intent run(double x, double z) {
        return new Player.Intent(x, z, true, false, false, false);
    }

    /** {@code seconds} of {@code i}, the world following. */
    private static void play(World w, Player.Intent i, double seconds) {
        for (int f = 0; f < Math.round(seconds / DT); f++) {
            w.player().update(DT, i, DUR);
            w.tick(DT);
        }
    }

    @Test
    void sheStandsOnTheGroundAndWalksUpTheRampSlower() {
        World flat = at(-15, 10);
        play(flat, run(0, -1), 2);
        double onFlat = flat.player().speed();

        World w = at(3, -20);
        Player p = w.player();
        assertEquals(Terrain.highlands().height(3, -20), p.ground().y(), 1e-9);
        play(w, run(0, -1), 2);
        assertTrue(p.ground().y() > 0.8, "she has climbed: " + p.ground().y());
        assertEquals(0, p.height(), 1e-9, "on the ground all the way");
        assertTrue(p.speed() < onFlat * 0.8, "slower uphill: " + p.speed() + " against " + onFlat);
        for (int f = 0; f < 600 && p.ground().z() > -33; f++) {
            p.update(DT, run(0, -1), DUR);
            w.tick(DT);
        }
        assertEquals(Terrain.PLATEAU_HEIGHT, p.ground().y(), 0.05, "up on the plateau");
    }

    @Test
    void rockTooSteepToWalkStopsHer() {
        World w = at(6, -47);
        Player p = w.player();
        play(w, run(0, 1), 4);                 // south, at the plateau's north side
        assertTrue(p.ground().z() < -42, "she does not get onto the plateau: z " + p.ground().z());
        assertTrue(p.ground().y() < 2.5, "nor far up its side: " + p.ground().y());
    }

    @Test
    void offTheLedgeSheFallsAndALongFallHurts() {
        World w = at(1.5, -36);
        Player p = w.player();
        assertEquals(Terrain.PLATEAU_HEIGHT, p.ground().y(), 1e-6);
        boolean fell = false, said = false;
        AnimState pose = null;
        for (int f = 0; f < 240; f++) {
            p.update(DT, f < 60 ? walk(-1, 0) : Player.Intent.NONE, DUR);
            w.tick(DT);
            if (p.height() > 1) {
                fell = true;
                pose = p.state();
            }
            said |= w.combat().popups().stream().anyMatch(pp -> pp.text().startsWith("Hard landing"));
        }
        assertTrue(fell, "off the edge into the air");
        assertEquals(AnimState.BLADE_JUMP, pose, "the stance's jump, held as she falls");
        assertTrue(p.ground().x() < 0, "below the ledge");
        assertEquals(0, p.height(), 1e-9, "landed");
        double drop = Terrain.PLATEAU_HEIGHT - p.ground().y();
        double hurt = Math.round(Player.FALL_DAMAGE * (drop - Player.SAFE_FALL));
        assertEquals(Player.MAX_HEALTH - hurt, p.health(), 1.0, "a fall of " + drop + " m");
        assertTrue(hurt > 10);
        assertTrue(said, "said over her");
    }

    @Test
    void theDodgeKeyInTheAirLandsHerInARollUnhurt() {
        World w = at(1.5, -36);
        Player p = w.player();
        boolean rolled = false;
        for (int f = 0; f < 240; f++) {
            Player.Intent i = f < 60 ? walk(-1, 0) : Player.Intent.NONE;
            if (p.height() > 2 && !p.rollPrimed()) i = i.withDodge(true, false);
            p.update(DT, i, DUR);
            w.tick(DT);
            rolled |= p.state() == AnimState.ROLL;
        }
        assertTrue(rolled, "she lands in the roll");
        assertEquals(Player.MAX_HEALTH, p.health(), 1e-9, "and nothing hurts");
    }

    @Test
    void aShortDropDoesNotHurt() {
        World w = at(-15, 10);
        Player p = w.player();
        p.lift(Player.SAFE_FALL - 0.2);
        play(w, Player.Intent.NONE, 2);
        assertEquals(Player.MAX_HEALTH, p.health(), 1e-9);
    }

    @Test
    void sheClimbsTheVinesToTheTopAndStepsUpOverTheLip() {
        Terrain t = Terrain.highlands();
        Terrain.Wall wall = t.walls().get(0);
        World w = at(wall.x0() + 1.5, -36);
        Player p = w.player();
        for (int f = 0; f < 120 && p.climbing() == null; f++) {      // into the wall: she takes hold
            p.update(DT, walk(-1, 0), DUR);
            w.tick(DT);
        }
        assertNotNull(p.climbing());
        assertEquals(AnimState.CLIMB, p.state());
        assertTrue(p.state().handsFree(), "her hands are empty on the wall");
        double t0 = p.stateTime(), y0 = p.feet().y();
        play(w, walk(-1, 0), 2);
        assertEquals(y0 + 2 * Player.CLIMB_SPEED, p.feet().y(), 0.05, "up the face at the climb's speed");
        assertTrue(p.stateTime() > t0, "the climb plays");
        assertTrue(p.stamina() < Player.MAX_STAMINA - 10, "climbing tires her");

        double hang = p.stateTime(), y = p.feet().y();
        play(w, Player.Intent.NONE, 1);
        assertEquals(hang, p.stateTime(), 1e-9, "hanging still, the frame holds");
        assertEquals(y, p.feet().y(), 1e-9);

        double lip = t.lipOut(wall, wall.along(p.ground().x(), p.ground().z()));
        assertEquals(wall.x0() - 0.5, wall.x0() + lip, 0.02, "the lip: the plateau's edge, the face leaning back");
        boolean stepped = false;
        Vec3 last = p.feet();
        for (int f = 0; f < 60 * 8 && p.climbing() != null; f++) {
            p.update(DT, walk(-1, 0), DUR);
            w.tick(DT);
            if (p.state() == AnimState.LEDGE_UP) {
                if (!stepped) {
                    assertEquals(Terrain.PLATEAU_HEIGHT - Player.LEDGE_RISE, p.feet().y(), 0.02, "her hands at the lip");
                    assertEquals(lip + Player.CLING, wall.out(p.ground().x(), p.ground().z()), 0.02);
                }
                stepped = true;
                assertTrue(p.climbing() != null || p.height() < 1e-9, "on the wall until she stands on the top");
                assertEquals(wall.facing(), p.heading(), 1e-9, "facing the rock");
            }
            assertTrue(p.feet().distance(last) < 0.1, "carried along, not jumped: " + p.feet().distance(last));
            last = p.feet();
        }
        assertTrue(stepped, "she steps up over the lip");
        assertNull(p.climbing(), "over the top");
        assertEquals(Terrain.PLATEAU_HEIGHT, p.ground().y(), 1e-3);
        assertEquals(0, p.height(), 1e-9);
        assertEquals(lip - Player.LEDGE_BEHIND, wall.out(p.ground().x(), p.ground().z()), 0.02, "standing behind the lip");
    }

    @Test
    void walkingOffTheTopOfTheVinesSheStepsDownOntoThemAndClimbsDown() {
        Terrain.Wall wall = Terrain.highlands().walls().get(0);
        World w = at(wall.x0() - 2.5, -36);                          // on the plateau, 2 m in from its edge
        Player p = w.player();
        assertEquals(Terrain.PLATEAU_HEIGHT, p.ground().y(), 1e-6);
        boolean stepped = false;
        Vec3 last = p.feet();
        for (int f = 0; f < 60 * 4 && !(stepped && p.state() == AnimState.CLIMB); f++) {
            p.update(DT, walk(1, 0), DUR);                            // out towards the drop
            w.tick(DT);
            if (p.state() == AnimState.LEDGE_DOWN) {
                stepped = true;
                assertEquals(wall.facing(), p.heading(), 1e-9, "turned to face the rock, her back to the drop");
                assertTrue(p.feet().distance(last) < 0.2, "carried along, not jumped: " + p.feet().distance(last));
            }
            last = p.feet();
        }
        assertTrue(stepped, "she steps down over the lip");
        assertNotNull(p.climbing(), "and hangs on the vines");
        assertEquals(Terrain.PLATEAU_HEIGHT - Player.LEDGE_RISE, p.feet().y(), 0.02, "by her hands from the lip");
        assertEquals(0, w.combat().popups().stream().filter(pp -> pp.text().startsWith("Hard landing")).count());

        // still walking out: down the face to its foot, unhurt
        play(w, walk(1, 0), 8);
        assertNull(p.climbing(), "down at the foot");
        assertTrue(p.ground().y() < 0.5 && p.height() < 1e-6, "standing at the foot: " + p.ground());
        assertEquals(Player.MAX_HEALTH, p.health(), 1e-9, "no fall");
    }

    @Test
    void tooTiredToClimbDownSheStopsAtTheLipButCanStillJumpOff() {
        Terrain.Wall wall = Terrain.highlands().walls().get(0);
        World w = at(wall.x0() - 2.5, -36);
        Player p = w.player();
        for (int f = 0; f < 60 * 3; f++) {
            p.setStamina(0);
            p.update(DT, run(1, 0), DUR);
            w.tick(DT);
        }
        assertNull(p.climbing());
        assertEquals(Terrain.PLATEAU_HEIGHT, p.ground().y(), 1e-6, "still on top");
        assertTrue(p.ground().x() < wall.x0() - 0.5, "short of the lip: x " + p.ground().x());

        play(w, Player.Intent.NONE, 3);                                // rested, then a running jump off
        for (int f = 0; f < 60 * 3 && p.ground().y() > 1; f++) {
            p.update(DT, new Player.Intent(1, 0, true, false, f < 3, false), DUR);
            w.tick(DT);
        }
        assertNull(p.climbing(), "a jump goes over the edge, not onto the vines");
        assertTrue(p.ground().y() < 1, "down off the plateau");
    }

    @Test
    void theStepOverTheLipFollowsTheSheetsPath() {
        // the values of feminine_model_ledge's PATH between its keys (feminine_model_armed.pchip)
        double[][] at = {{2, 0.3, -1.615}, {10.5, 0.250409029, -1.234563675}, {20, 0.111008660, -0.883819221},
                {29.25, -0.217661133, -0.193304384}};
        for (double[] a : at) {
            assertEquals(a[1], com.larsons.game.math.Pchip.at(Player.LEDGE_PATH, 1, a[0]), 1e-8);
            assertEquals(a[2], com.larsons.game.math.Pchip.at(Player.LEDGE_PATH, 2, a[0]), 1e-8);
        }
        assertEquals(1.60, Player.LEDGE_RISE, 1e-9);
        assertEquals(0.35, Player.LEDGE_BEHIND, 1e-9);
        assertEquals(Player.LEDGE_PATH[Player.LEDGE_PATH.length - 1][0] + 1, AnimState.LEDGE_UP.defaultFrames());
        assertEquals(AnimState.LEDGE_UP.defaultFrames(), AnimState.LEDGE_DOWN.defaultFrames());
    }

    @Test
    void outOfStaminaSheLetsGoAndAKeyLetsGoToo() {
        Terrain.Wall wall = Terrain.highlands().walls().get(0);
        World w = at(wall.x0() + 1.0, -36);
        Player p = w.player();
        p.setStamina(Player.CLIMB_MIN + 2);
        boolean climbed = false, said = false;
        for (int f = 0; f < 240; f++) {
            p.update(DT, walk(-1, 0), DUR);
            w.tick(DT);
            climbed |= p.climbing() != null;
            said |= w.combat().popups().stream().anyMatch(pp -> pp.text().equals("Out of stamina"));
        }
        assertTrue(climbed);
        assertNull(p.climbing(), "too tired to hold on");
        assertTrue(said);
        assertEquals(0, p.height(), 1e-9, "she dropped to the foot");

        play(w, Player.Intent.NONE, 6);              // rested
        play(w, walk(-1, 0), 1.5);
        assertNotNull(p.climbing());
        play(w, new Player.Intent(0, 0, false, false, true, false), DT);
        assertNull(p.climbing(), "the jump key lets go");
    }

    @Test
    void sheCannotWalkUpTheCliffWithoutTakingHold() {
        Terrain.Wall wall = Terrain.highlands().walls().get(0);
        World w = at(wall.x0() + 1.5, -36);
        Player p = w.player();
        for (int f = 0; f < 120; f++) {
            p.setStamina(0);                          // too tired to climb
            p.update(DT, walk(-1, 0), DUR);
            w.tick(DT);
        }
        assertNull(p.climbing());
        assertTrue(p.ground().x() > wall.x0() - 0.05, "stopped at its foot: x " + p.ground().x());
        assertTrue(p.ground().y() < 0.5);
    }

    @Test
    void knockedOutSheIsBackAtTheStart() {
        World w = at(1.5, -36);
        w.setSpawnPoint(new Vec3(0, 0, 0));
        Player p = w.player();
        p.setHealth(5);
        boolean said = false;
        for (int f = 0; f < 240; f++) {
            p.update(DT, f < 60 ? walk(-1, 0) : Player.Intent.NONE, DUR);
            w.tick(DT);
            said |= w.combat().popups().stream().anyMatch(pp -> pp.text().equals("Knocked out"));
        }
        assertTrue(said);
        assertEquals(0, p.ground().horizontalLength(), 1e-9, "back where she started");
        assertEquals(Player.MAX_HEALTH, p.health(), 1e-9);
    }

    @Test
    void theCameraRisesOverACliffThatWouldHideHer() {
        Terrain t = Terrain.highlands();
        Terrain.Wall wall = t.walls().get(0);
        Vec3 chest = wall.at(wall.length() / 2, 1.0).add(0, 0.9, 0);    // at the cliff's foot, outside it
        OrbitCamera cam = new OrbitCamera(Math.toRadians(270), Math.toRadians(17), 16);   // the plateau behind her
        cam.snapTo(chest);
        for (int i = 0; i < 120; i++) cam.keepAbove(t, 0.8);
        assertTrue(cam.pitch() > Math.toRadians(45), "up over the plateau: " + Math.toDegrees(cam.pitch()));
        Vec3 eye = cam.eye();
        for (double f = 0.05; f <= 1; f += 0.05) {
            Vec3 at = chest.add(eye.sub(chest).scale(f));
            assertTrue(at.y() >= t.height(at.x(), at.z()), "nothing between it and her, " + f + " of the way");
        }
        OrbitCamera open = new OrbitCamera(Math.toRadians(90), Math.toRadians(17), 16);  // the open meadow behind
        open.snapTo(chest);
        for (int i = 0; i < 120; i++) open.keepAbove(t, 0.8);
        assertEquals(Math.toRadians(17), open.pitch(), 1e-3, "nothing in the way: left where it is");
    }

    @Test
    void itemsAndChestsStandOnTheGround() {
        World w = at(0, 0);
        w.spawn(ItemDef.WAND, new Vec3(6, 0, -36));
        assertEquals(Terrain.PLATEAU_HEIGHT, w.items().get(0).position.y(), 1e-6);
        assertTrue(w.items().get(0).centre(0).y() > Terrain.PLATEAU_HEIGHT, "hovering over the plateau");
        w.player().setPosition(new Vec3(6, 0, -36.5));
        assertNotNull(w.reachable());
        w.player().setPosition(new Vec3(-0.5, 0, -36));      // below the ledge, as near across
        w.items().clear();
        w.spawn(ItemDef.WAND, new Vec3(0.6, 0, -36));
        assertNull(w.reachable(), "out of reach up on the plateau");
    }
}
