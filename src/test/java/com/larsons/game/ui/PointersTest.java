package com.larsons.game.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The mouse pointer: an arrow made of gears, but a class's own in a story played with one. */
class PointersTest {

    @Test
    void theDefaultIsTheGearArrowOutsideAStoryWithAClass() {
        assertEquals(Pointers.GEAR, Pointers.resolve(Pointers.CLASS, ""), "the menus, the demo, the editor");
        assertEquals(Pointers.GEAR, Pointers.resolve(Pointers.CLASS, null));
        assertEquals(Pointers.GEAR, Pointers.resolve(null, ""));
        assertEquals(Pointers.GEAR, Pointers.resolve(Pointers.CLASS, "no_such_class"));
        assertEquals("wand", Pointers.resolve(Pointers.CLASS, "mage"), "a Mage's story: her wand");
        assertEquals("sword", Pointers.resolve("sword", ""), "one chosen is that one everywhere");
        assertEquals(Pointers.GEAR, Pointers.resolve(Pointers.GEAR, "mage"));
        assertEquals("Gear arrow", Pointers.label(Pointers.GEAR));
        assertTrue(Pointers.NAMES.contains(Pointers.GEAR), "it can be chosen");
        for (String f : new String[]{"gear_arrow.png", "gear_arrow@2x.png"}) {
            assertTrue(Files.isRegularFile(Path.of("assets/ui/pointers", f)), f);
        }
    }
}
