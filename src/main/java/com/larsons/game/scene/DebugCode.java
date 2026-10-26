package com.larsons.game.scene;

import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_0;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_9;

/**
 * The secret debugging code, typed on the num-pad ({@link #CODE}: 1 0 2 5,
 * each digit within {@link #GAP} seconds of the last). Typed once it turns
 * debugging on - every cosmetic offered in the wardrobe, and the pause menu's
 * button that clears the ground - and typed again turns it off.
 *
 * <p>Only the num-pad's digits count (the number row is the hotbar's), and
 * any other num-pad digit starts the code over.
 */
final class DebugCode {

    /** The digits, in order. */
    static final int[] CODE = {1, 0, 2, 5};
    /** The longest pause between two digits of the code, in seconds. */
    static final double GAP = 3.0;

    private final int[] typed = new int[CODE.length];
    private int count;
    private double last = Double.NEGATIVE_INFINITY;

    /** The num-pad digit of GLFW key {@code key}, or -1 when it is not one. */
    static int digit(int key) {
        return key >= GLFW_KEY_KP_0 && key <= GLFW_KEY_KP_9 ? key - GLFW_KEY_KP_0 : -1;
    }

    /** Num-pad digit {@code d} typed at {@code time} (seconds): whether it completes the code. */
    boolean type(int d, double time) {
        if (d < 0) return false;
        if (time - last > GAP) count = 0;
        last = time;
        typed[count++] = d;
        // keep the longest tail of what was typed that the code starts with
        while (count > 0 && !Arrays.equals(typed, 0, count, CODE, 0, count)) {
            System.arraycopy(typed, 1, typed, 0, count - 1);
            count--;
        }
        if (count == CODE.length) {
            count = 0;
            return true;
        }
        return false;
    }
}
