package com.larsons.game.world;

import com.larsons.game.building.Building;
import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.building.BuildingProfile;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Up onto the furniture: a table, a crate, a barrel - stood on, jumped onto, climbed up onto, walked off. */
class ClimbOntoTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);

    private static Player.Intent walk(double x, double z, boolean jump) {
        return new Player.Intent(x, z, false, false, jump, false);
    }

    private static void play(World w, Player.Intent i, double seconds) {
        for (int f = 0; f < Math.round(seconds / DT); f++) {
            w.player().update(DT, i, DUR);
            w.tick(DT);
        }
    }

    /** A world with a {@code kind} standing 2 m north of her, its long side to her. */
    private static World with(String kind) {
        World w = new World(new Wardrobe());
        BuildingProfile p = BuildingCatalog.get().profile(kind);
        assertNotNull(p, kind);
        w.addBuilding(new Building(1, p, new Vec3(0, 0, -2), 0, null, null));
        w.player().setPosition(Vec3.ZERO);
        return w;
    }

    @Test
    void sheWalksUpOntoTheCabinsPorchAndRoundItsPosts() {
        World w = new World(new Wardrobe());
        BuildingProfile p = BuildingCatalog.get().profile("cabin");
        assertFalse(p.steps().isEmpty(), "the cabin's porch is walked on");
        // the cabin facing her (heading 0: its front towards -z ... its back away), its middle 8 m north
        Building cabin = new Building(1, p, new Vec3(0, 0, -8), Math.PI, null, null);
        w.addBuilding(cabin);
        Vec3 door = cabin.door();
        // from in front of the porch, walking straight at the door: up onto the deck
        Vec3 front = cabin.toWorld(-1.1, -6.5);
        w.player().setPosition(new Vec3(front.x(), 0, front.z()));
        Vec3 d0 = door.sub(w.player().ground());
        Vec3 to = new Vec3(d0.x(), 0, d0.z()).normalize();
        play(w, walk(to.x(), to.z(), false), 1.6);
        double deck = 0.3 * 1.25;
        assertEquals(deck, w.player().ground().y(), 0.02, "up on the porch, not walking through it");
        assertTrue(w.player().ground().horizontalDistance(door) < 1.5, "at the door: "
                + w.player().ground().horizontalDistance(door));
        // a porch post is solid
        BuildingProfile.Shape post = p.footprint().get(p.footprint().size() - 1);
        Vec3 at = cabin.toWorld(post.x(), post.y());
        assertTrue(cabin.blocks(at.x(), at.z(), 0.01), "a post stands there");
    }

    @Test
    void sheClimbsTheKeepsStepsAStepAtATime() {
        World w = new World(new Wardrobe());
        BuildingProfile p = BuildingCatalog.get().profile("keep");
        Building keep = new Building(1, p, new Vec3(0, 0, 0), 0, null, null);
        w.addBuilding(keep);
        Vec3 below = keep.toWorld(0, -9.5);
        w.player().setPosition(below);
        Vec3 dir = keep.axisY();                       // towards its back: up the steps to its door
        double highest = 0;
        for (int f = 0; f < 120; f++) {
            w.player().update(DT, walk(dir.x(), dir.z(), false), DUR);
            w.tick(DT);
            highest = Math.max(highest, w.player().ground().y());
        }
        assertTrue(highest > 0.6, "up the steps: " + highest);
        assertTrue(highest < 0.75, "no higher than the top step: " + highest);
        assertFalse(keep.blocks(w.player().ground().x(), w.player().ground().z(), 0.2), "and at its wall, not in it");
    }

    @Test
    void furnitureIsAsTallAsItsModel() {
        double table = BuildingCatalog.get().profile("table").height();
        assertTrue(table > 0.6 && table < 1.0, "a table's top: " + table);
        assertTrue(World.climbable(new Building(1, BuildingCatalog.get().profile("crate"), Vec3.ZERO, 0, null, null)));
        assertFalse(World.climbable(new Building(1, BuildingCatalog.get().profile("cottage"), Vec3.ZERO, 0, null,
                null)), "not a house");
    }

    @Test
    void walkingIntoATableSheStopsAndSpaceClimbsHerUpOntoIt() {
        World w = with("table");
        Player p = w.player();
        double top = BuildingCatalog.get().profile("table").height();
        play(w, walk(0, -1, false), 1.5);
        assertEquals(0, p.ground().y(), 1e-6, "kept off it");
        assertTrue(p.ground().z() > -2 + 0.49, "stopped at its edge: " + p.ground().z());
        play(w, walk(0, -1, true), DT);
        assertTrue(p.climbingOnto(), "Space by it: up she goes");
        assertEquals(AnimState.LEDGE_UP, p.state());
        play(w, Player.Intent.NONE, 2);
        assertFalse(p.climbingOnto());
        assertEquals(top, p.ground().y(), 1e-6, "standing on it");
        assertFalse(p.airborne());
        // across it and off the far side: down she falls, onto the floor
        play(w, walk(0, -1, false), 2);
        assertTrue(p.ground().z() < -2.6, "off it: " + p.ground().z());
        assertEquals(0, p.ground().y(), 1e-6);
    }

    @Test
    void inTheAirSheTakesHoldOfACratesEdgeAndStandsOnABarrel() {
        World w = with("crate");
        Player p = w.player();
        p.setPosition(new Vec3(0, 0, -1.0));
        p.lift(0.3);                                      // in the air, her feet below its top
        play(w, walk(0, -1, false), DT);
        assertTrue(p.climbingOnto(), "coming up against its edge, she takes hold");
        play(w, Player.Intent.NONE, 1.5);
        double top = BuildingCatalog.get().profile("crate").height();
        assertEquals(top, p.ground().y(), 1e-6, "on the crate");

        World b = with("barrel");
        b.player().setPosition(new Vec3(0, 0, -2));
        b.player().lift(2);
        play(b, Player.Intent.NONE, 1.5);
        assertEquals(BuildingCatalog.get().profile("barrel").height(), b.player().ground().y(), 1e-6,
                "dropped onto it from above, she stands on it");
    }

    @Test
    void notUpOntoWhatIsTallerThanSheIs() {
        World w = with("cottage");
        Player p = w.player();
        p.setPosition(new Vec3(0, 0, 4));
        play(w, walk(0, -1, false), 2);
        play(w, walk(0, -1, true), DT);
        assertFalse(p.climbingOnto());
    }
}
