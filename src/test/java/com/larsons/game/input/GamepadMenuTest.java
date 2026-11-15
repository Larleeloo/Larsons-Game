package com.larsons.game.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

/** The gamepad in a menu: the press that opened it does not also shut it, nor click in it. */
class GamepadMenuTest {

    private static final double DT = 1 / 60.0;

    private static void frame(Input in, boolean menu) {
        in.setUiMode(menu);
        in.scriptPadState(DT);
    }

    @Test
    void theButtonThatOpenedAMenuDoesNotShutItUntilItIsPressedAgain() {
        Input in = new Input();
        in.viewSize(1280, 720);
        in.setNavigable(true);
        // playing: Menu goes down - the scene opens its pause menu on it
        in.scriptPad(Pad.MENU, true);
        frame(in, false);
        assertTrue(in.padPressed(Pad.MENU));
        assertFalse(in.pressed(GLFW_KEY_ESCAPE), "playing, the gamepad presses no keys");
        in.endFrame();
        // the menu is up and the button still down, for as many frames as a thumb takes
        for (int i = 0; i < 12; i++) {
            frame(in, true);
            assertFalse(in.pressed(GLFW_KEY_ESCAPE), "held since before the menu: it does not go back");
            assertFalse(in.down(GLFW_KEY_ESCAPE));
            in.endFrame();
        }
        in.scriptPad(Pad.MENU, false);
        frame(in, true);
        assertFalse(in.pressed(GLFW_KEY_ESCAPE), "let go: nothing");
        in.endFrame();
        // pressed again, it is Escape: the menu shuts
        in.scriptPad(Pad.MENU, true);
        frame(in, true);
        assertTrue(in.pressed(GLFW_KEY_ESCAPE));
        in.endFrame();
        in.scriptPad(Pad.MENU, false);
        frame(in, true);
        assertFalse(in.down(GLFW_KEY_ESCAPE));
        in.endFrame();
        // B goes back the same way
        in.scriptPad(Pad.B, true);
        frame(in, true);
        assertTrue(in.pressed(GLFW_KEY_ESCAPE));
    }

    @Test
    void aButtonHeldAsAMenuOpensDoesNotClickInIt() {
        Input in = new Input();
        in.viewSize(1280, 720);
        in.setNavigable(true);
        // A held (a jump) as a menu comes up over the game
        in.scriptPad(Pad.A, true);
        frame(in, false);
        in.endFrame();
        frame(in, true);
        assertFalse(in.mousePressed(GLFW_MOUSE_BUTTON_LEFT), "the jump is not a click");
        assertFalse(in.mouseDown(GLFW_MOUSE_BUTTON_LEFT));
        in.endFrame();
        in.scriptPad(Pad.A, false);
        frame(in, true);
        in.endFrame();
        in.scriptPad(Pad.A, true);
        frame(in, true);
        assertTrue(in.mousePressed(GLFW_MOUSE_BUTTON_LEFT), "pressed in the menu, A clicks what has the focus");
        in.endFrame();
        // the button of an action reads as pressed with a menu up too: the one that opened a panel shuts it
        in.scriptPad(Pad.VIEW, true);
        frame(in, true);
        assertTrue(in.padPressed(Action.INVENTORY), "View shuts the inventory it opened");
        assertFalse(in.pressed(Action.INVENTORY), "though no action is played with a menu up");
    }
}
