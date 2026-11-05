package com.larsons.game.level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Templates: what is selected saved under a name - a building with the
 * floors inside it, what stands round it - and placed again, in any level,
 * turned any way, every piece where it stood round the others.
 */
class TemplatesTest {

    @AfterEach
    void back() {
        Templates.useDir(Path.of("assets", Templates.FOLDER));
    }

    @Test
    void aBuildingWithItsRoomsComesBackWhereverItIsPlacedAndTurned(@TempDir Path dir) throws IOException {
        Templates.useDir(dir);
        Level l = Level.blank("Mill", 64);
        LevelObject house = l.add(ObjectTypes.BUILDING, 10, 4, 0, Map.of("kind", "cottage"));
        LevelObject tree = l.add(ObjectTypes.SCENERY, 14, 4, Math.PI / 2, Map.of("sprite", "oak_tree"));
        Level ground = new Level("Ground floor", Level.INTERIOR_SIZE);
        ground.add(ObjectTypes.BUILDING, 1, 2, 0, Map.of("kind", "table"));
        l.setInterior(house.id(), List.of(ground));

        Templates.Template t = Templates.save("Miller's cottage", List.of(house, tree, l.spawn()), l);
        assertTrue(Files.isRegularFile(dir.resolve("miller_s_cottage.json")));
        assertEquals(2, t.objects().size(), "not the spawn: a level has one");
        assertEquals(-2, t.objects().get(0).x(), 1e-9, "from its middle");
        assertEquals(1, Templates.all().size());

        // read back from the file
        Templates.useDir(dir);
        Templates.Template back = Templates.byId("miller_s_cottage");
        assertNotNull(back);
        assertEquals("Miller's cottage", back.name());
        List<Level> floors = back.floors(back.objects().get(0).id());
        assertNotNull(floors, "the cottage's floor inside, with it");
        assertEquals(1, floors.get(0).objects().size());
        assertEquals("table", floors.get(0).objects().get(0).str("kind", ""));
        assertNotSame(floors.get(0), back.floors(back.objects().get(0).id()).get(0), "fresh each time");

        // placed as it was saved
        LevelObject placed = new LevelObject(0, ObjectTypes.TEMPLATE, 30, 30, 0, Map.of("template", back.id()));
        List<LevelObject> pieces = ObjectTypes.expand(placed, 100);
        assertEquals(2, pieces.size());
        assertEquals(28, pieces.get(0).x(), 1e-9);
        assertEquals(32, pieces.get(1).x(), 1e-9);
        assertEquals(100, pieces.get(0).id());
        assertEquals(Math.PI / 2, pieces.get(1).yaw(), 1e-9);

        // turned a quarter: the pieces round its middle, and each turned with it
        placed.turnTo(Math.PI / 2);
        pieces = ObjectTypes.expand(placed, 100);
        assertEquals(30, pieces.get(0).x(), 1e-9);
        assertEquals(32, pieces.get(0).z(), 1e-9);
        assertEquals(28, pieces.get(1).z(), 1e-9);
        assertEquals(Math.PI, pieces.get(1).yaw(), 1e-9);
        assertEquals("Miller's cottage", ObjectTypes.label(placed));
        assertTrue(ObjectTypes.radius(placed, null) >= 2);

        // in the palette, and gone when deleted
        assertTrue(ObjectTypes.templateEntries().stream().anyMatch(e -> e.label().equals("Miller's cottage")));
        Templates.delete(back.id());
        assertTrue(Templates.all().isEmpty());
        assertTrue(ObjectTypes.expand(placed, 100).isEmpty());
    }

    @Test
    void savingUnderTheSameNameSavesOverIt(@TempDir Path dir) throws IOException {
        Templates.useDir(dir);
        Level l = Level.blank("Camp", 64);
        LevelObject a = l.add(ObjectTypes.SCENERY, 0, 0, 0, Map.of("sprite", "oak_tree"));
        LevelObject b = l.add(ObjectTypes.SCENERY, 2, 0, 0, Map.of("sprite", "oak_tree"));
        Templates.save("Camp", List.of(a), l);
        Templates.save("camp", List.of(a, b), l);
        assertEquals(1, Templates.all().size());
        assertEquals(2, Templates.all().get(0).objects().size());
        assertThrows(IOException.class, () -> Templates.save("Nothing", List.of(), l));
    }
}
