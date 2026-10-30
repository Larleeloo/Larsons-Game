package com.larsons.game.scene;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_0;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_5;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD;

/** The num-pad debug code, and the pause menu's debug item - no window needed. */
class DebugCodeTest {

    /** Type {@code digits} a quarter of a second apart from {@code t}; whether the last completed the code. */
    private static boolean type(DebugCode code, double t, int... digits) {
        boolean done = false;
        for (int d : digits) {
            done = code.type(d, t);
            t += 0.25;
        }
        return done;
    }

    @Test
    void onlyTheNumPadsDigitsCount() {
        assertEquals(0, DebugCode.digit(GLFW_KEY_KP_0));
        assertEquals(5, DebugCode.digit(GLFW_KEY_KP_5));
        assertEquals(-1, DebugCode.digit(GLFW_KEY_1), "the number row is the hotbar's");
        assertEquals(-1, DebugCode.digit(GLFW_KEY_KP_ADD), "num-pad + zooms");
    }

    @Test
    void theCodeTypedInOrderCompletesIt() {
        DebugCode code = new DebugCode();
        assertFalse(type(code, 0, 1, 0, 2));
        assertTrue(code.type(5, 1), "1 0 2 5");
        assertFalse(code.type(1, 2), "and starts over");
        assertTrue(type(code, 2.25, 0, 2, 5), "typed again: completes again (debug off)");
    }

    @Test
    void aWrongDigitOrALongPauseStartsItOver() {
        DebugCode code = new DebugCode();
        assertFalse(type(code, 0, 1, 0, 3, 5));
        assertTrue(type(code, 2, 1, 1, 0, 2, 5), "a 1 too many: the code still starts at the last 1");
        assertFalse(type(code, 10, 1, 0));
        assertFalse(type(code, 10 + DebugCode.GAP + 1, 2, 5), "too long a pause before the 2");
    }

    @Test
    void thePauseMenuOffersClearingTheGroundOnlyWithTheDebugToolsOn() {
        assertFalse(PauseMenu.mainItems(false).contains(PauseMenu.CLEAR_GROUND));
        var items = PauseMenu.mainItems(true);
        assertEquals(items.indexOf("Main menu") - 1, items.indexOf(PauseMenu.CLEAR_GROUND));
        assertEquals("Resume", items.get(0));
    }

    @Test
    void theMainMenusCodeTakesEitherRowOfDigits() {
        assertEquals(1, DebugCode.anyDigit(GLFW_KEY_1), "the number row");
        assertEquals(5, DebugCode.anyDigit(GLFW_KEY_KP_5), "or the num-pad");
        assertEquals(-1, DebugCode.anyDigit(GLFW_KEY_KP_ADD));
    }

    @Test
    void aPlayTestsPauseMenuGoesBackToTheEditor() {
        List<String> items = PauseMenu.mainItems(false, "Back to the editor");
        assertEquals(items.indexOf("Back to the editor") + 1, items.indexOf("Main menu"), "just before the main menu");
        assertFalse(PauseMenu.mainItems(false).contains("Back to the editor"));
    }
}
