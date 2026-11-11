package com.larsons.game.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F13;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

/** The controls: every action's keys and gamepad inputs, remapped, saved and read back. */
class BindingsTest {

    @Test
    void everyActionComesWithItsKeysAndPadInputsAndNoTwoShareOne() {
        Bindings b = new Bindings();
        assertTrue(b.isDefault());
        assertEquals(Action.JUMP, b.actionOf(Bind.key(GLFW_KEY_SPACE)));
        assertEquals(Action.JUMP, b.actionOf(Bind.pad(Pad.A)));
        assertEquals(Action.PAUSE, b.actionOf(Bind.pad(Pad.MENU)));
        for (boolean pad : new boolean[]{false, true}) {
            java.util.Set<Bind> seen = new java.util.HashSet<>();
            for (Action a : Action.values()) {
                assertTrue(b.of(a, pad).size() <= Bindings.SLOTS, a + " has too many");
                for (Bind bind : b.of(a, pad)) {
                    assertEquals(pad, bind.onPad(), a.toString());
                    assertTrue(seen.add(bind), bind.label() + " is two actions' (" + a + ")");
                }
            }
        }
    }

    @Test
    void aBindGivenToAnActionIsTakenFromTheOneThatHadIt() {
        Bindings b = new Bindings();
        assertEquals(Action.ATTACK, b.actionOf(Bind.key(GLFW_KEY_F)));
        assertEquals(Action.ATTACK, b.set(Action.JUMP, 0, Bind.key(GLFW_KEY_F)));
        assertEquals(Action.JUMP, b.actionOf(Bind.key(GLFW_KEY_F)));
        assertFalse(b.keys(Action.ATTACK).contains(Bind.key(GLFW_KEY_F)));
        assertNull(b.actionOf(Bind.key(GLFW_KEY_SPACE)), "the slot's old key is free");
        assertFalse(b.isDefault());
        // a second slot, then a third bind goes over the last
        assertNull(b.set(Action.JUMP, 1, Bind.key(GLFW_KEY_F13)));
        assertEquals(List.of(Bind.key(GLFW_KEY_F), Bind.key(GLFW_KEY_F13)), b.keys(Action.JUMP));
        b.set(Action.JUMP, 5, Bind.key(GLFW_KEY_SPACE));
        assertEquals(List.of(Bind.key(GLFW_KEY_F), Bind.key(GLFW_KEY_SPACE)), b.keys(Action.JUMP));
        // the gamepad's are remapped apart from the keyboard's
        assertEquals(Action.JUMP, b.set(Action.ATTACK, 0, Bind.pad(Pad.A)));
        assertEquals(Action.ATTACK, b.actionOf(Bind.pad(Pad.A)));
        b.reset(true);
        assertEquals(Action.JUMP, b.actionOf(Bind.pad(Pad.A)));
        assertEquals(Action.JUMP, b.actionOf(Bind.key(GLFW_KEY_F)), "the keys were left alone");
        b.reset();
        assertTrue(b.isDefault());
    }

    @Test
    void thePauseMenuAlwaysKeepsAKey() {
        Bindings b = new Bindings();
        assertTrue(Action.PAUSE.required());
        assertNull(b.set(Action.JUMP, 0, Bind.key(GLFW_KEY_ESCAPE)), "its only key is not taken");
        assertEquals(Action.PAUSE, b.actionOf(Bind.key(GLFW_KEY_ESCAPE)));
        assertFalse(b.clear(Action.PAUSE, 0, false));
        // with a second key, either can go
        b.set(Action.PAUSE, 1, Bind.key(GLFW_KEY_F13));
        assertEquals(Action.PAUSE, b.set(Action.JUMP, 0, Bind.key(GLFW_KEY_ESCAPE)));
        assertEquals(List.of(Bind.key(GLFW_KEY_F13)), b.keys(Action.PAUSE));
        assertFalse(b.clear(Action.PAUSE, 0, false));
        assertTrue(b.clear(Action.PAUSE, 0, true), "its gamepad button may go");
        assertTrue(b.clear(Action.JUMP, 0, false));
    }

    @Test
    void theControlsAreSavedAndReadBack(@TempDir Path dir) {
        Bindings b = new Bindings();
        b.set(Action.JUMP, 0, Bind.key(GLFW_KEY_F13));
        b.set(Action.ATTACK, 0, Bind.pad(Pad.RT));
        Path file = dir.resolve("controls.json");
        b.save(file);
        Bindings back = Bindings.load(file);
        for (Action a : Action.values()) {
            assertEquals(b.keys(a), back.keys(a), a.toString());
            assertEquals(b.pads(a), back.pads(a), a.toString());
        }
        assertTrue(Bindings.load(dir.resolve("none.json")).isDefault(), "no file: as it comes");
        // every bind is written in a way that reads back
        for (Bind bind : List.of(Bind.key(GLFW_KEY_SPACE), Bind.mouse(1), Bind.pad(Pad.LS_UP), Bind.pad(Pad.DPAD_LEFT))) {
            assertEquals(bind, Bind.parse(bind.save()));
            assertFalse(bind.label().isBlank());
        }
        assertNull(Bind.parse("pad:nonsense"));
        assertNull(Bind.parse("key:x"));
        assertNull(Bind.parse("lever"));
    }

    @Test
    void aGamepadsButtonsTriggersAndSticksAreAllInputs() {
        for (int i = 0; i < Pad.COUNT; i++) {
            assertEquals(i, Pad.byKey(Pad.key(i)));
            assertNotEquals("?", Pad.label(i));
        }
        assertEquals(-1, Pad.byKey("paddle"));
        boolean[] buttons = new boolean[Pad.BUTTONS];
        float[] axes = {0, 0, 0, 0, -1, -1};                      // sticks at rest, triggers let go
        buttons[Pad.A] = true;
        assertEquals(1, Pad.value(Pad.A, buttons, axes));
        assertEquals(0, Pad.value(Pad.B, buttons, axes));
        assertEquals(0, Pad.value(Pad.LT, buttons, axes), 1e-9);
        axes[Pad.AXIS_RT] = 0;
        assertEquals(0.5, Pad.value(Pad.RT, buttons, axes), 1e-9);
        assertTrue(Pad.value(Pad.RT, buttons, axes) > Pad.TRIGGER_PRESS, "half squeezed is pressed");
        // a stick: nothing in its dead middle, one way at a time, all the way at its rim
        axes[Pad.AXIS_LX] = 0.1f;
        assertEquals(0, Pad.value(Pad.LS_RIGHT, buttons, axes));
        axes[Pad.AXIS_LX] = 1;
        assertEquals(1, Pad.value(Pad.LS_RIGHT, buttons, axes), 1e-6);
        assertEquals(0, Pad.value(Pad.LS_LEFT, buttons, axes));
        assertEquals(0, Pad.value(Pad.LS_UP, buttons, axes));
        axes[Pad.AXIS_LX] = 0;
        axes[Pad.AXIS_LY] = -0.8f;                                // up is the axis's negative way
        assertTrue(Pad.value(Pad.LS_UP, buttons, axes) > Pad.STICK_PRESS);
        assertEquals(0, Pad.value(Pad.LS_DOWN, buttons, axes));
        assertTrue(Pad.stick(Pad.RS_LEFT) && !Pad.stick(Pad.LT));
    }
}
