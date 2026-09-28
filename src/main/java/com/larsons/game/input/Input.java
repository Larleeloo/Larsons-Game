package com.larsons.game.input;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_LAST;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LAST;

/**
 * Keyboard and mouse state for one frame, fed by the window's GLFW callbacks.
 *
 * <p>The same latching the engine's {@code InputManager} does, trimmed to what
 * this game needs: held state, a "pressed this frame" edge (so a tap shorter
 * than a frame is never lost), key repeats for text fields and menus, typed
 * characters, the wheel, and the pointer. {@link #endFrame()} clears the
 * edges once the frame has been simulated.
 *
 * <p>Key and button codes are GLFW's ({@code GLFW_KEY_*}).
 */
public final class Input {

    private final boolean[] down = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] pressed = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] repeated = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] released = new boolean[GLFW_KEY_LAST + 1];

    private final boolean[] mouseDown = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final boolean[] mousePressed = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final boolean[] mouseReleased = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];

    private double mouseX, mouseY, lastMouseX, lastMouseY;
    private boolean mouseSeen;
    private double scroll;
    private final StringBuilder typed = new StringBuilder();
    private final List<List<String>> drops = new ArrayList<>();

    // --- fed by the window -----------------------------------------------------------

    /** A key went down ({@code isDown}), auto-repeated, or came up. */
    public void keyEvent(int key, boolean isDown) {
        if (key < 0 || key > GLFW_KEY_LAST) return;
        if (isDown) {
            if (!down[key]) pressed[key] = true;
            repeated[key] = true;
            down[key] = true;
        } else {
            down[key] = false;
            released[key] = true;
        }
    }

    public void mouseButtonEvent(int button, boolean isDown) {
        if (button < 0 || button > GLFW_MOUSE_BUTTON_LAST) return;
        if (isDown) {
            if (!mouseDown[button]) mousePressed[button] = true;
            mouseDown[button] = true;
        } else {
            mouseDown[button] = false;
            mouseReleased[button] = true;
        }
    }

    public void mouseMoved(double x, double y) {
        if (!mouseSeen) {
            lastMouseX = x;
            lastMouseY = y;
            mouseSeen = true;
        }
        mouseX = x;
        mouseY = y;
    }

    public void scrolled(double dy) {
        scroll += dy;
    }

    public void typed(int codepoint) {
        if (codepoint >= 32 && codepoint != 127) typed.appendCodePoint(codepoint);
    }

    /** Paths dropped onto the window (one list per drop). */
    public void dropped(List<String> paths) {
        drops.add(List.copyOf(paths));
    }

    /** Release everything held, e.g. when the window loses focus. */
    public void releaseAll() {
        for (int i = 0; i < down.length; i++) {
            if (down[i]) released[i] = true;
            down[i] = false;
        }
        for (int i = 0; i < mouseDown.length; i++) {
            if (mouseDown[i]) mouseReleased[i] = true;
            mouseDown[i] = false;
        }
    }

    // --- read by scenes --------------------------------------------------------------

    public boolean down(int key) { return key >= 0 && key < down.length && down[key]; }

    /** First frame the key went down. */
    public boolean pressed(int key) { return key >= 0 && key < pressed.length && pressed[key]; }

    /** Went down or auto-repeated this frame — for menus and text fields. */
    public boolean repeated(int key) { return key >= 0 && key < repeated.length && repeated[key]; }

    public boolean released(int key) { return key >= 0 && key < released.length && released[key]; }

    public boolean mouseDown(int button) { return mouseDown[button]; }

    public boolean mousePressed(int button) { return mousePressed[button]; }

    public boolean mouseReleased(int button) { return mouseReleased[button]; }

    /** Pointer position in logical window pixels, (0, 0) top left. */
    public double mouseX() { return mouseX; }

    public double mouseY() { return mouseY; }

    /** Pointer movement since the last frame, in logical pixels. */
    public double mouseDX() { return mouseX - lastMouseX; }

    public double mouseDY() { return mouseY - lastMouseY; }

    /** Wheel notches this frame, positive away from the player. */
    public double scroll() { return scroll; }

    /** Characters typed this frame. */
    public String typedText() { return typed.toString(); }

    /** Take the pending drag-and-drop batches (each a list of paths). */
    public List<List<String>> takeDrops() {
        List<List<String>> out = new ArrayList<>(drops);
        drops.clear();
        return out;
    }

    /** Forget a click so nothing else this frame acts on it (a UI took it). */
    public void consumeMouse(int button) {
        mousePressed[button] = false;
    }

    /** Forget a key press so nothing else this frame acts on it. */
    public void consumeKey(int key) {
        if (key >= 0 && key < pressed.length) {
            pressed[key] = false;
            repeated[key] = false;
        }
    }

    public void consumeScroll() {
        scroll = 0;
    }

    /** Clear the per-frame edges. Called once per frame after the scene updated. */
    public void endFrame() {
        java.util.Arrays.fill(pressed, false);
        java.util.Arrays.fill(repeated, false);
        java.util.Arrays.fill(released, false);
        java.util.Arrays.fill(mousePressed, false);
        java.util.Arrays.fill(mouseReleased, false);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        scroll = 0;
        typed.setLength(0);
    }
}
