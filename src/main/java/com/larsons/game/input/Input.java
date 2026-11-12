package com.larsons.game.input;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

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
        if (isDown && !synthetic) device = Device.KEYBOARD;
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
        if (isDown && !synthetic) device = Device.KEYBOARD;
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
        // (the pointer the gamepad moved comes back as a mouse event: only the mouse itself switches back)
        if (!synthetic && Math.hypot(x - mouseX, y - mouseY) > 3 && !(Math.hypot(x - warpX, y - warpY) < 2)) {
            device = Device.KEYBOARD;
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


    // --- the gamepad -------------------------------------------------------------------

    /** What was last touched: the keyboard and mouse, or the gamepad. The prompts follow it. */
    public enum Device { KEYBOARD, GAMEPAD }

    private Device device = Device.KEYBOARD;
    private Bindings bindings = new Bindings();
    private final boolean[] padDown = new boolean[Pad.COUNT], padPressed = new boolean[Pad.COUNT],
            padReleased = new boolean[Pad.COUNT];
    private final boolean[] padButtons = new boolean[Pad.BUTTONS];
    private final float[] padAxes = {0, 0, 0, 0, -1, -1};
    private boolean padPresent;
    private String padName = "";
    /** A menu is up: the gamepad moves the pointer and clicks instead of doing the game's actions. */
    private boolean uiMode = true;
    /** The Controls page is waiting for an input to bind: the gamepad does nothing else meanwhile. */
    private boolean capturing;
    /** Events the gamepad makes (the pointer, a click, Esc) do not count as the keyboard's. */
    private boolean synthetic;
    /** In a menu, the d-pad was used since the stick: A chooses what is focused rather than clicking. */
    private boolean dpadNavigating;
    private double repeatTimer;
    private int repeatKey = -1;
    /** Where the gamepad has put the pointer, for the window to move the real one to (NaN: nowhere). */
    private double warpX = Double.NaN, warpY = Double.NaN;
    private boolean warpPending;
    private double viewW = 1280, viewH = 720;

    /** How fast a stick pushed all the way moves the pointer in a menu, logical pixels a second. */
    public static final double POINTER_SPEED = 950;

    /** The window's size in logical pixels: what the gamepad's pointer is kept inside. */
    public void viewSize(double w, double h) {
        viewW = Math.max(1, w);
        viewH = Math.max(1, h);
    }

    /**
     * The gamepad this frame: its fifteen buttons and six axes as GLFW gives
     * them (an Xbox layout), or {@code buttons} null: none is plugged in.
     * Fed once a frame, after the window's events, with the frame's length.
     */
    public void padState(String name, boolean[] buttons, float[] axes, double dt) {
        if (buttons == null || axes == null) {
            if (padPresent) {
                for (int i = 0; i < Pad.COUNT; i++) {
                    if (padDown[i]) padReleased[i] = true;
                    padDown[i] = false;
                }
                java.util.Arrays.fill(padButtons, false);
                padAxes[0] = padAxes[1] = padAxes[2] = padAxes[3] = 0;
                padAxes[4] = padAxes[5] = -1;
                releaseSynthetic();
            }
            padPresent = false;
            return;
        }
        padPresent = true;
        padName = name == null ? "Gamepad" : name;
        System.arraycopy(buttons, 0, padButtons, 0, Math.min(buttons.length, Pad.BUTTONS));
        System.arraycopy(axes, 0, padAxes, 0, Math.min(axes.length, padAxes.length));
        boolean touched = false;
        for (int i = 0; i < Pad.COUNT; i++) {
            double v = Pad.value(i, padButtons, padAxes);
            boolean on = i < Pad.BUTTONS ? v > 0 : v > (Pad.stick(i) ? Pad.STICK_PRESS : Pad.TRIGGER_PRESS);
            if (on && !padDown[i]) {
                padPressed[i] = true;
                touched = true;
            }
            if (!on && padDown[i]) padReleased[i] = true;
            padDown[i] = on;
            if (Pad.stick(i) && v > 0.3) touched = true;
        }
        if (touched) device = Device.GAMEPAD;
        if (device == Device.GAMEPAD && uiMode && !capturing) menuWithPad(dt);
        else releaseSynthetic();
    }

    /** The keys and the mouse button the gamepad holds down in a menu. */
    private boolean synthLeft, synthRight, synthEscape, synthEnter;

    // --- moving through a menu with the gamepad (read by ui/Ui once a frame) ---

    /** Which way the focus moves this frame: {@link #NAV_UP} ... or 0. */
    private int navStep;
    /** A slider or a choice that has the focus is turned this way (-1, +1), not left. */
    private int navAdjust;
    /** LB / RB this frame: the tab before (-1) or after (+1). */
    private int tabStep;
    /** Whether the interface on screen has things the focus can move between (it says so a frame late). */
    private boolean navigable;
    /** The left stick moves a free pointer (L3 turns it on and off, or the screen asks: a map, the editor's view). */
    private boolean freePointer, screenWantsPointer;
    /** The way the left stick was last flicked, and when it repeats. */
    private int stickWay;
    private double stickTimer;
    private double triggerTimer;

    public static final int NAV_UP = 1, NAV_DOWN = 2, NAV_LEFT = 3, NAV_RIGHT = 4;

    /**
     * In a menu the gamepad moves a focus from one thing to the next as a
     * console's menus do: the d-pad and the left stick go to the nearest
     * button, slider or field that way ({@link #takeNav}), A presses it, B
     * and Menu go back (Esc), X is the right button, LB and RB change tabs
     * ({@link #takeTab}), the triggers and the right stick scroll. Where
     * there is nothing to move between - or with L3 clicked, or on a screen
     * that wants a pointer (a map, the editor's view) - the left stick moves
     * a pointer instead and the d-pad is the arrow keys.
     */
    private void menuWithPad(double dt) {
        synthetic = true;
        try {
            if (padPressed[Pad.LS]) freePointer = !freePointer;
            boolean pointer = !keyboard && (freePointer || screenWantsPointer || !navigable);
            double lx = Pad.value(Pad.LS_RIGHT, padButtons, padAxes) - Pad.value(Pad.LS_LEFT, padButtons, padAxes);
            double ly = Pad.value(Pad.LS_DOWN, padButtons, padAxes) - Pad.value(Pad.LS_UP, padButtons, padAxes);
            if (pointer) {
                stickWay = 0;
                if (lx != 0 || ly != 0) {
                    // slow near the middle, for the small things; fast at the rim
                    double len = Math.hypot(lx, ly), gain = POINTER_SPEED * (0.25 + 0.75 * len * len) * dt;
                    double nx = Math.max(0, Math.min(viewW - 1, mouseX + lx * gain));
                    double ny = Math.max(0, Math.min(viewH - 1, mouseY + ly * gain));
                    mouseMoved(nx, ny);
                    warpX = nx;
                    warpY = ny;
                    warpPending = true;
                    dpadNavigating = false;
                }
            } else {
                // flicked: one step; held: steps repeating, as the d-pad's
                int way = Math.hypot(lx, ly) < 0.5 ? 0 : Math.abs(lx) > Math.abs(ly) ? (lx > 0 ? NAV_RIGHT : NAV_LEFT)
                        : (ly > 0 ? NAV_DOWN : NAV_UP);
                if (way != stickWay) {
                    stickWay = way;
                    stickTimer = 0.38;
                    if (way != 0) step(way);
                } else if (way != 0) {
                    stickTimer -= dt;
                    if (stickTimer <= 0) {
                        stickTimer = 0.11;
                        step(way);
                    }
                }
            }
            double ry = Pad.value(Pad.RS_UP, padButtons, padAxes) - Pad.value(Pad.RS_DOWN, padButtons, padAxes);
            if (ry != 0) scroll += ry * dt * 14;
            // the triggers: a page at a time, repeating while held
            int page = padDown[Pad.LT] ? 1 : padDown[Pad.RT] ? -1 : 0;
            if (padPressed[Pad.LT] || padPressed[Pad.RT]) {
                scroll += page * 5;
                triggerTimer = 0.4;
            } else if (page != 0) {
                triggerTimer -= dt;
                if (triggerTimer <= 0) {
                    triggerTimer = 0.12;
                    scroll += page * 5;
                }
            }
            if (padPressed[Pad.LB]) tabStep = -1;
            if (padPressed[Pad.RB]) tabStep = 1;

            if (!keyboard) {
                boolean a = padDown[Pad.A];
                if (dpadNavigating && !navigable) {
                    if (a != synthEnter) keyEvent(GLFW_KEY_ENTER, synthEnter = a);
                    if (synthLeft) mouseButtonEvent(GLFW_MOUSE_BUTTON_LEFT, synthLeft = false);
                } else {
                    if (a != synthLeft) mouseButtonEvent(GLFW_MOUSE_BUTTON_LEFT, synthLeft = a);
                    if (synthEnter) keyEvent(GLFW_KEY_ENTER, synthEnter = false);
                }
                if (padDown[Pad.X] != synthRight) mouseButtonEvent(GLFW_MOUSE_BUTTON_RIGHT, synthRight = padDown[Pad.X]);
                boolean esc = padDown[Pad.B] || padDown[Pad.MENU];
                if (esc != synthEscape) keyEvent(GLFW_KEY_ESCAPE, synthEscape = esc);
            }

            // the d-pad: the focus's steps where there is a focus, else the arrow keys; repeating while held
            int held = padDown[Pad.DPAD_UP] ? GLFW_KEY_UP : padDown[Pad.DPAD_DOWN] ? GLFW_KEY_DOWN
                    : padDown[Pad.DPAD_LEFT] ? GLFW_KEY_LEFT : padDown[Pad.DPAD_RIGHT] ? GLFW_KEY_RIGHT : -1;
            boolean steps = keyboard || navigable && !freePointer;
            if (held != repeatKey) {
                if (repeatKey >= 0 && down[repeatKey]) keyEvent(repeatKey, false);
                repeatKey = held;
                repeatTimer = 0.38;
                if (held >= 0) {
                    if (steps) step(way(held));
                    else keyEvent(held, true);
                    dpadNavigating = true;
                }
            } else if (held >= 0) {
                repeatTimer -= dt;
                if (repeatTimer <= 0) {
                    repeatTimer = 0.09;
                    if (steps) step(way(held));
                    else repeated[held] = true;
                }
            }
        } finally {
            synthetic = false;
        }
    }

    private static int way(int arrow) {
        return arrow == GLFW_KEY_UP ? NAV_UP : arrow == GLFW_KEY_DOWN ? NAV_DOWN
                : arrow == GLFW_KEY_LEFT ? NAV_LEFT : NAV_RIGHT;
    }

    private void step(int way) {
        navStep = way;
    }

    /**
     * While muted - a menu over the whole screen, the settings - the screen
     * under it reads nothing: no keys, no buttons, the pointer nowhere.
     */
    private boolean muted;

    public void setMuted(boolean on) {
        muted = on;
    }

    public boolean muted() { return muted; }

    /** The way the gamepad moved the focus this frame ({@link #NAV_UP} ...), once; 0: it did not. */
    public int takeNav() {
        int n = navStep;
        navStep = 0;
        return n;
    }

    /** Turn a focused slider or choice by -1 or +1 (the d-pad's left or right, taken from the focus's steps). */
    public void adjust(int by) {
        navAdjust = by;
    }

    /** How a focused slider or choice is turned this frame: -1, +1, or 0. */
    public int navAdjust() { return navAdjust; }

    /** LB (-1) or RB (+1) pressed in a menu this frame, once - and Ctrl+Tab, Ctrl+Shift+Tab; 0: neither. */
    public int takeTab() {
        if (muted) return 0;
        int t = tabStep;
        tabStep = 0;
        if (t == 0 && pressed(GLFW_KEY_TAB) && (down(GLFW_KEY_LEFT_CONTROL) || down(GLFW_KEY_RIGHT_CONTROL))) {
            t = down(GLFW_KEY_LEFT_SHIFT) || down(GLFW_KEY_RIGHT_SHIFT) ? -1 : 1;
            consumeKey(GLFW_KEY_TAB);
        }
        return t;
    }

    /** The on-screen keyboard is up: the gamepad's A, B and X are its, not a click or Esc. */
    private boolean keyboard;

    public void setKeyboard(boolean on) {
        keyboard = on;
        if (on) releaseSynthetic();
    }

    /** Whether the screen has things to move the focus between (the interface says so each frame). */
    public void setNavigable(boolean on) {
        navigable = on;
    }

    public boolean navigable() { return navigable; }

    /** The screen wants a free pointer for the gamepad's left stick this frame (a map, the editor's view). */
    public void setWantsPointer(boolean on) {
        screenWantsPointer = on;
    }

    /** Whether the gamepad is moving a focus about a menu (not a pointer, not playing). */
    public boolean padFocusing() {
        return device == Device.GAMEPAD && uiMode && navigable && !capturing;
    }

    /** Whether the gamepad's left stick moves a free pointer (L3, or the screen wants one). */
    public boolean padPointer() {
        return freePointer || screenWantsPointer;
    }

    /** Put the pointer at {@code (x, y)}, as the gamepad's focus does, and the real one with it. */
    public void warp(double x, double y) {
        synthetic = true;
        try {
            mouseMoved(x, y);
        } finally {
            synthetic = false;
        }
        warpX = x;
        warpY = y;
        warpPending = true;
    }

    /** Scroll as the wheel does, {@code notches} up (positive) or down. */
    public void addScroll(double notches) {
        scroll += notches;
    }

    /** Let go of what the gamepad was holding down for a menu (the menu shut, the pad was unplugged). */
    private void releaseSynthetic() {
        synthetic = true;
        try {
            if (synthLeft) mouseButtonEvent(GLFW_MOUSE_BUTTON_LEFT, synthLeft = false);
            if (synthRight) mouseButtonEvent(GLFW_MOUSE_BUTTON_RIGHT, synthRight = false);
            // (let go without a "released" edge acting on anything: these keys only ever act on the press)
            if (synthEscape) down[GLFW_KEY_ESCAPE] = synthEscape = false;
            if (synthEnter) down[GLFW_KEY_ENTER] = synthEnter = false;
            if (repeatKey >= 0) down[repeatKey] = false;
            repeatKey = -1;
        } finally {
            synthetic = false;
        }
    }

    /**
     * Where the window should put the real pointer - the gamepad moved it -
     * as {x, y}, once; null when it has not moved.
     */
    public double[] takeWarp() {
        if (!warpPending) return null;
        warpPending = false;
        return new double[]{warpX, warpY};
    }

    /**
     * Whether a menu is up: the gamepad then moves the pointer and clicks,
     * and its actions wait. A scene says so every frame; it is so until one
     * says otherwise.
     */
    public void setUiMode(boolean ui) {
        uiMode = ui;
    }

    public boolean uiMode() { return uiMode; }

    /** While the Controls page waits for an input to bind, the gamepad drives nothing. */
    public void setCapturing(boolean on) {
        capturing = on;
        if (on) releaseSynthetic();
    }

    public Device device() { return device; }

    /** Whether the gamepad was touched last (the prompts show its buttons). */
    public boolean usingPad() { return device == Device.GAMEPAD; }

    public boolean padPresent() { return padPresent; }

    /** The gamepad's name as the system gives it, "" with none plugged in. */
    public String padName() { return padPresent ? padName : ""; }

    public Bindings bindings() { return bindings; }

    public void setBindings(Bindings b) {
        bindings = b == null ? new Bindings() : b;
    }

    public boolean padDown(int input) { return !muted && input >= 0 && input < Pad.COUNT && padDown[input]; }

    public boolean padPressed(int input) { return !muted && input >= 0 && input < Pad.COUNT && padPressed[input]; }

    /** How far a gamepad input is pressed, 0 to 1 (a stick's push, a trigger's squeeze). */
    public double padValue(int input) { return padPresent && !muted ? Pad.value(input, padButtons, padAxes) : 0; }

    // --- actions -----------------------------------------------------------------------

    private boolean bindDown(Bind b) {
        return switch (b.kind()) {
            case KEY -> down(b.code());
            case MOUSE -> !muted && b.code() >= 0 && b.code() < mouseDown.length && mouseDown[b.code()];
            case PAD -> !uiMode && padDown(b.code());
        };
    }

    private boolean bindPressed(Bind b) {
        return switch (b.kind()) {
            case KEY -> pressed(b.code());
            case MOUSE -> !muted && b.code() >= 0 && b.code() < mousePressed.length && mousePressed[b.code()];
            case PAD -> !uiMode && padPressed(b.code());
        };
    }

    /** Whether any of {@code a}'s keys or buttons is held. */
    public boolean down(Action a) {
        for (Bind b : bindings.keys(a)) if (bindDown(b)) return true;
        for (Bind b : bindings.pads(a)) if (bindDown(b)) return true;
        return false;
    }

    /** Whether any of {@code a}'s keys or buttons went down this frame. */
    public boolean pressed(Action a) {
        for (Bind b : bindings.keys(a)) if (bindPressed(b)) return true;
        for (Bind b : bindings.pads(a)) if (bindPressed(b)) return true;
        return false;
    }

    /** Went down or auto-repeated this frame (a held key; on the gamepad, the press alone). */
    public boolean repeated(Action a) {
        for (Bind b : bindings.keys(a)) {
            if (b.kind() == Bind.Kind.KEY ? repeated(b.code()) : bindPressed(b)) return true;
        }
        for (Bind b : bindings.pads(a)) if (bindPressed(b)) return true;
        return false;
    }

    /** How far {@code a} is pressed, 0 to 1: a key all the way, a stick as far as it is pushed. */
    public double value(Action a) {
        double v = 0;
        for (Bind b : bindings.keys(a)) if (bindDown(b)) return 1;
        if (!uiMode) for (Bind b : bindings.pads(a)) v = Math.max(v, padValue(b.code()));
        return v;
    }

    /** Forget {@code a}'s press so nothing else this frame acts on it. */
    public void consume(Action a) {
        for (Bind b : bindings.keys(a)) {
            if (b.kind() == Bind.Kind.KEY) consumeKey(b.code());
            else if (b.code() >= 0 && b.code() < mousePressed.length) mousePressed[b.code()] = false;
        }
        for (Bind b : bindings.pads(a)) if (b.code() >= 0 && b.code() < Pad.COUNT) padPressed[b.code()] = false;
    }

    /**
     * What to press for {@code a} on what was last touched - "E", "Left
     * click", "X" - or, when it has nothing there, on the other; "unbound"
     * with nothing at all.
     */
    public String label(Action a) {
        List<Bind> first = bindings.of(a, usingPad()), second = bindings.of(a, !usingPad());
        if (!first.isEmpty()) return first.get(0).label();
        return second.isEmpty() ? "unbound" : second.get(0).label();
    }

    /** {@link #label} of each of {@code actions}, joined with "/" ("W/A/S/D"). */
    public String labels(Action... actions) {
        StringBuilder s = new StringBuilder();
        for (Action a : actions) s.append(s.isEmpty() ? "" : "/").append(label(a));
        return s.toString();
    }

    /**
     * The first key or mouse button ({@code pad} false) or gamepad input
     * ({@code pad}) pressed this frame, for the Controls page to bind; null
     * when nothing was.
     */
    public Bind firstPressed(boolean pad) {
        if (pad) {
            for (int i = 0; i < Pad.COUNT; i++) if (padPressed[i] && i != Pad.GUIDE) return Bind.pad(i);
            return null;
        }
        for (int k = 0; k < pressed.length; k++) if (pressed[k]) return Bind.key(k);
        for (int b = 0; b < mousePressed.length; b++) if (mousePressed[b]) return Bind.mouse(b);
        return null;
    }

    // --- read by scenes --------------------------------------------------------------

    public boolean down(int key) { return !muted && key >= 0 && key < down.length && down[key]; }

    /** Whether any key or mouse button went down this frame. */
    public boolean anyPressed() {
        for (boolean p : pressed) if (p) return true;
        for (boolean p : mousePressed) if (p) return true;
        for (boolean p : padPressed) if (p) return true;
        return false;
    }

    /** First frame the key went down. */
    public boolean pressed(int key) { return !muted && key >= 0 && key < pressed.length && pressed[key]; }

    /** Went down or auto-repeated this frame — for menus and text fields. */
    public boolean repeated(int key) { return !muted && key >= 0 && key < repeated.length && repeated[key]; }

    public boolean released(int key) { return !muted && key >= 0 && key < released.length && released[key]; }

    public boolean mouseDown(int button) { return !muted && mouseDown[button]; }

    public boolean mousePressed(int button) { return !muted && mousePressed[button]; }

    public boolean mouseReleased(int button) { return !muted && mouseReleased[button]; }

    /** Pointer position in logical window pixels, (0, 0) top left. */
    public double mouseX() { return muted ? -10000 : mouseX; }

    public double mouseY() { return muted ? -10000 : mouseY; }

    /** Pointer movement since the last frame, in logical pixels. */
    public double mouseDX() { return muted ? 0 : mouseX - lastMouseX; }

    public double mouseDY() { return muted ? 0 : mouseY - lastMouseY; }

    /** Wheel notches this frame, positive away from the player. */
    public double scroll() { return muted ? 0 : scroll; }

    /** Characters typed this frame. */
    public String typedText() { return muted ? "" : typed.toString(); }

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
        navAdjust = 0;
        java.util.Arrays.fill(pressed, false);
        java.util.Arrays.fill(repeated, false);
        java.util.Arrays.fill(released, false);
        java.util.Arrays.fill(mousePressed, false);
        java.util.Arrays.fill(mouseReleased, false);
        java.util.Arrays.fill(padPressed, false);
        java.util.Arrays.fill(padReleased, false);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        scroll = 0;
        typed.setLength(0);
    }
}
