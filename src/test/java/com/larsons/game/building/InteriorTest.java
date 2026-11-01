package com.larsons.game.building;

import com.larsons.game.combat.CombatTuning;
import com.larsons.game.level.Interiors;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelIO;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.LevelWorld;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Chest;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The insides of buildings: the pieces rooms are built of (a wall's
 * doorway she walks through, the walls cut down between the eye and her),
 * each kind's floors as it comes - a door out, stairs between its floors,
 * the furniture clear of the walls - kept in the level's file, and going in
 * and coming out again to the outdoors as she left it.
 */
class InteriorTest {

    @BeforeAll
    static void catalog() {
        BuildingCatalog.use(BuildingCatalog.load(Path.of("assets", "buildings")));
    }

    private static Building piece(Room.Spec spec, double x, double z, double heading) {
        return new Building(1, Room.profile(spec), new Vec3(x, 0, z), heading, null, null);
    }

    @Test
    void sheWalksThroughADoorwayButNotThroughTheWallOrItsWindow() {
        Building door = piece(Room.wall(4, 3, Room.Opening.DOOR), 0, 0, 0);
        assertFalse(door.blocks(0, 0, 0.25), "the doorway is open");
        assertTrue(door.blocks(1.5, 0, 0.25), "the wall beside it is not");
        assertTrue(piece(Room.wall(4, 3, Room.Opening.NONE), 0, 0, 0).blocks(0, 0, 0.25));
        assertTrue(piece(Room.wall(4, 3, Room.Opening.WINDOW), 0, 0, 0).blocks(0, 0, 0.25), "a window is glazed");
        // a floor is walked on, not into - but is clicked on
        Building floor = piece(Room.floor(4, 4), 0, 0, 0);
        assertFalse(floor.blocks(0, 0, 0.25));
        assertTrue(floor.rayHit(new Vec3(0, 10, 0), new Vec3(0, -1, 0)) > 0);
        // a wall is as high as it is set
        assertEquals(4.5, Room.profile(Room.wall(3, 4.5, Room.Opening.NONE)).height(), 1e-9);
    }

    @Test
    void theWallsOnTheEyesSideOfHerAreCutDownAndTheFarOnesStand() {
        // a room 6 m square round her, each wall facing in
        Building north = piece(Room.wall(6, 3, Room.Opening.NONE), 0, -3, Math.PI);
        Building south = piece(Room.wall(6, 3, Room.Opening.NONE), 0, 3, 0);
        Building west = piece(Room.wall(6, 3, Room.Opening.NONE), -3, 0, -Math.PI / 2);
        Building east = piece(Room.wall(6, 3, Room.Opening.NONE), 3, 0, Math.PI / 2);
        Vec3 her = new Vec3(0, 1, 0);
        // the eye to the south, looking north
        Vec3 eye = new Vec3(0, 5, 8);
        assertTrue(Room.cut(south, eye, her));
        assertFalse(Room.cut(north, eye, her));
        assertFalse(Room.cut(west, eye, her));
        assertFalse(Room.cut(east, eye, her));
        // to the south-east, looking north-west: the south and east walls down
        Vec3 se = new Vec3(6, 5, 6);
        assertTrue(Room.cut(south, se, her));
        assertTrue(Room.cut(east, se, her));
        assertFalse(Room.cut(north, se, her));
        assertFalse(Room.cut(west, se, her));
        // a floor is never cut; a stub is low
        assertFalse(Room.cut(piece(Room.floor(6, 6), 0, 0, 0), eye, her));
        for (Room.Box b : Room.boxes(south.profile().room(), true)) assertTrue(b.z1() <= Room.CUT + 1e-9);
        assertTrue(Room.boxes(south.profile().room(), false).stream().anyMatch(b -> b.z1() > 2.9));
    }

    @Test
    void aWallsSidesTrimAndGlassAreItsParts() {
        BuildingProfile w = Room.profile(Room.wall(4, 3, Room.Opening.WINDOW));
        assertEquals(List.of("front", "back", "trim", "glass"), w.parts().stream().map(BuildingProfile.Part::key).toList());
        assertEquals(BuildingProfile.ROOM, w.group());
        BuildingLook look = new BuildingLook().setColour("front", 0x8A3A3A).setTexture("back", "brick");
        Building b = new Building(1, w, Vec3.ZERO, 0, look, null);
        assertEquals(0x8A3A3A, b.look().colour(w.part("front")));
        assertEquals("brick", b.look().texture(w.part("back")));
        assertEquals("plaster", b.look().texture(w.part("front")));
    }

    @Test
    void theFurnitureIsThereAndTheFireIsAlwaysLit() {
        BuildingCatalog c = BuildingCatalog.get();
        for (String k : List.of("bed", "table", "chair", "bench", "stool", "bookshelf", "cupboard", "wardrobe",
                "fireplace", "counter", "barrel", "crate")) {
            BuildingProfile p = c.profile(k);
            assertNotNull(p, k);
            assertTrue(p.furniture(), k);
            assertFalse(p.interior(), k);
            assertTrue(p.height() < 3.2, k + " fits under a ceiling: " + p.height());
        }
        assertTrue(c.profile("fireplace").part("fire").always());
        assertFalse(c.profile("cottage").furniture());
        // filed with what goes inside
        assertTrue(ObjectTypes.palette().stream().anyMatch(e -> e.category()
                == com.larsons.game.level.ObjectType.Category.INSIDE && e.label().equals("Bed")));
    }

    @Test
    void everyKindWithAnInsideComesFurnishedWithADoorOutAndStairsBetweenItsFloors() {
        BuildingCatalog c = BuildingCatalog.get();
        for (String kind : c.keys()) {
            BuildingProfile p = c.profile(kind);
            List<Level> floors = Interiors.make(kind);
            if (!p.interior()) {
                assertTrue(floors.isEmpty(), kind);
                continue;
            }
            assertFalse(floors.isEmpty(), kind + " has an inside");
            for (int n = 0; n < floors.size(); n++) {
                Level f = floors.get(n);
                String where = kind + ", " + f.name();
                int exits = f.ofType(ObjectTypes.EXIT).size();
                assertEquals(n == 0 ? 1 : 0, exits, where + ": the door out is on the ground floor");
                long up = f.ofType(ObjectTypes.STAIRS).stream().filter(o -> o.str("way", "up").equals("up")).count();
                long down = f.ofType(ObjectTypes.STAIRS).size() - up;
                assertEquals(n < floors.size() - 1, up > 0, where + ": stairs up to the floor above");
                assertEquals(n > 0, down > 0, where + ": stairs down to the floor below");
                World w = new World(new Wardrobe());
                Terrain t = LevelWorld.stageIndoors(w, f);
                LevelWorld.spawnAll(w, f.objects(), f, t, ScenerySprites.NONE, CombatTuning.defaults());
                assertTrue(w.indoors());
                List<Building> walls = new ArrayList<>(), furniture = new ArrayList<>(), doors = new ArrayList<>();
                for (Building b : w.buildings()) {
                    Room.Spec r = b.profile().room();
                    if (r == null) furniture.add(b);
                    else if (r.piece() == Room.Piece.WALL) walls.add(b);
                    if (r != null && (r.piece() == Room.Piece.EXIT || r.piece() == Room.Piece.STAIRS)) doors.add(b);
                }
                // she can stand at each door and stairs: nothing solid there
                for (Building d : doors) {
                    Vec3 at = d.door();
                    for (Building b : w.buildings()) {
                        assertFalse(b.blocks(at.x(), at.z(), 0.2), where + ": " + b.profile().label() + " stands in "
                                + d.profile().label() + "'s way at " + at);
                    }
                }
                // no furniture stands in a wall or another piece of furniture
                for (Building a : furniture) {
                    for (Building b : walls) {
                        assertFalse(b.blocks(a.position().x(), a.position().z(), 0.1),
                                where + ": " + a.key() + " is in a wall at " + a.position());
                    }
                    for (Building b : furniture) {
                        if (a == b) continue;
                        assertFalse(b.blocks(a.position().x(), a.position().z(), 0.05),
                                where + ": " + a.key() + " is in " + b.key() + " at " + a.position());
                    }
                }
            }
        }
    }

    @Test
    void aBuildingsInsideIsKeptInTheLevelFileWithIt(@TempDir Path dir) throws IOException {
        Level level = Level.blank("Village", 64);
        LevelObject cottage = level.add(ObjectTypes.BUILDING, 4, 2, 0, Map.of("kind", "cottage"));
        LevelObject gone = level.add(ObjectTypes.BUILDING, -8, 2, 0, Map.of("kind", "cabin"));
        level.setInterior(cottage.id(), Interiors.make("cottage"));
        level.setInterior(gone.id(), Interiors.make("cabin"));
        level.remove(gone);
        Level ground = level.interior(cottage.id()).get(0);
        LevelObject crate = ground.add(ObjectTypes.BUILDING, 1.5, -1, 0.5, Map.of("kind", "crate"));
        crate.set("look", Map.of("wood", Map.of("colour", "#a84a30")));
        level.interior(cottage.id()).add(Interiors.emptyFloor(1));
        LevelIO.save(level, dir.resolve("village"));
        Level back = LevelIO.load(dir.resolve("village"));
        List<Level> floors = back.interior(cottage.id());
        assertNotNull(floors);
        assertEquals(2, floors.size());
        assertEquals("Ground floor", floors.get(0).name());
        assertEquals(ground.objects().size(), floors.get(0).objects().size());
        LevelObject read = floors.get(0).byId(crate.id());
        assertEquals("crate", read.str("kind", ""));
        assertEquals(0.5, read.yaw(), 1e-3);
        assertNotNull(read.props().get("look"));
        assertNull(back.interior(gone.id()), "the inside of a building taken away is not kept");
        assertTrue(floors.get(1).objects().isEmpty());
        // and a level with no insides says nothing of them
        assertFalse(LevelIO.json(Level.blank("Plain", 64)).contains("interiors"));
    }

    @Test
    void sheGoesInAndComesOutToTheOutdoorsAsSheLeftIt() {
        Level level = Level.blank("Village", 64);
        LevelObject cottage = level.add(ObjectTypes.BUILDING, 4, 2, 0, Map.of("kind", "cottage"));
        level.add(ObjectTypes.CHEST, -6, -6, 0, Map.of("sprite", ObjectTypes.CHEST_SPRITE));
        World world = new World(new Wardrobe());
        LevelWorld.populate(world, level, ScenerySprites.NONE, CombatTuning.defaults());
        Building b = world.building(cottage.id());
        Chest chest = world.chests().get(0);
        // at its door, it is the door she can go through
        world.player().setPosition(b.door());
        assertSame(b, world.reachableDoor());
        World.Place outdoors = world.leave();
        assertTrue(world.buildings().isEmpty());
        assertTrue(world.chests().isEmpty());
        Level f = Interiors.make("cottage").get(0);
        Terrain t = LevelWorld.stageIndoors(world, f);
        LevelWorld.spawnAll(world, f.objects(), f, t, ScenerySprites.NONE, CombatTuning.defaults());
        assertTrue(world.indoors());
        Building exit = world.buildings().stream().filter(x -> x.profile().room() != null
                && x.profile().room().piece() == Room.Piece.EXIT).findFirst().orElseThrow();
        world.player().setPosition(exit.door());
        assertSame(exit, world.reachableDoor());
        // nothing comes by inside
        for (int i = 0; i < 600; i++) world.tick(1 / 30.0);
        assertTrue(world.critters().live().isEmpty());
        assertTrue(world.animals().live().stream().allMatch(a -> a.kind() == com.larsons.game.ambient.AnimalKind.CAT));
        world.enter(outdoors);
        assertFalse(world.indoors());
        assertSame(b, world.building(cottage.id()));
        assertSame(chest, world.chests().get(0));
        // the door faces out the way the cottage does
        Vec3 out = b.door().sub(b.position());
        Vec3 face = SpriteView.headingVector(b.heading());
        assertTrue(out.x() * face.x() + out.z() * face.z() > 2);
    }
}
