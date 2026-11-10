package com.larsons.game.input;

import java.util.Locale;

/**
 * The gamepad's inputs as the game numbers them: the fifteen buttons of an
 * Xbox controller in GLFW's order, then the two triggers and the two sticks'
 * four ways each, so that a stick pushed up or a trigger squeezed can be
 * bound just as a button is.
 */
public final class Pad {

    private Pad() {}

    public static final int A = 0, B = 1, X = 2, Y = 3, LB = 4, RB = 5, VIEW = 6, MENU = 7, GUIDE = 8,
            LS = 9, RS = 10, DPAD_UP = 11, DPAD_RIGHT = 12, DPAD_DOWN = 13, DPAD_LEFT = 14,
            LT = 15, RT = 16,
            LS_UP = 17, LS_DOWN = 18, LS_LEFT = 19, LS_RIGHT = 20,
            RS_UP = 21, RS_DOWN = 22, RS_LEFT = 23, RS_RIGHT = 24;

    /** How many inputs there are. */
    public static final int COUNT = 25;
    /** How many of them are GLFW's buttons (the rest come from the axes). */
    public static final int BUTTONS = 15;

    /** GLFW's axes: the left stick, the right stick, the two triggers. */
    public static final int AXIS_LX = 0, AXIS_LY = 1, AXIS_RX = 2, AXIS_RY = 3, AXIS_LT = 4, AXIS_RT = 5;

    /** How far a stick is pushed before it counts as pressed that way; a trigger, squeezed. */
    public static final double STICK_PRESS = 0.5, TRIGGER_PRESS = 0.35;
    /** A stick this near its middle is at rest. */
    public static final double DEAD = 0.18;

    private static final String[] KEYS = {"a", "b", "x", "y", "lb", "rb", "view", "menu", "guide", "ls", "rs",
            "dpad_up", "dpad_right", "dpad_down", "dpad_left", "lt", "rt",
            "ls_up", "ls_down", "ls_left", "ls_right", "rs_up", "rs_down", "rs_left", "rs_right"};
    private static final String[] LABELS = {"A", "B", "X", "Y", "LB", "RB", "View", "Menu", "Guide",
            "LS click", "RS click", "D-pad up", "D-pad right", "D-pad down", "D-pad left", "LT", "RT",
            "Left stick up", "Left stick down", "Left stick left", "Left stick right",
            "Right stick up", "Right stick down", "Right stick left", "Right stick right"};

    /** The input's name in the saved bindings: {@code a}, {@code lt}, {@code ls_up}. */
    public static String key(int input) {
        return input >= 0 && input < COUNT ? KEYS[input] : "a";
    }

    /** The input named {@code key}, or -1. */
    public static int byKey(String key) {
        if (key == null) return -1;
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < COUNT; i++) if (KEYS[i].equals(k)) return i;
        return -1;
    }

    /** What it is called: "A", "LB", "Left stick up". */
    public static String label(int input) {
        return input >= 0 && input < COUNT ? LABELS[input] : "?";
    }

    /** Whether it is one of a stick's four ways. */
    public static boolean stick(int input) {
        return input >= LS_UP && input <= RS_RIGHT;
    }

    /**
     * How far {@code input} is pressed, 0 to 1, given the buttons held and
     * the axes: a button all or nothing, a trigger how far it is squeezed, a
     * stick how far it is pushed that way (past its dead middle).
     */
    public static double value(int input, boolean[] buttons, float[] axes) {
        if (input < 0 || input >= COUNT) return 0;
        if (input < BUTTONS) return buttons[input] ? 1 : 0;
        return switch (input) {
            case LT -> (axes[AXIS_LT] + 1) / 2;
            case RT -> (axes[AXIS_RT] + 1) / 2;
            case LS_UP -> stick(-axes[AXIS_LY], axes[AXIS_LX]);
            case LS_DOWN -> stick(axes[AXIS_LY], axes[AXIS_LX]);
            case LS_LEFT -> stick(-axes[AXIS_LX], axes[AXIS_LY]);
            case LS_RIGHT -> stick(axes[AXIS_LX], axes[AXIS_LY]);
            case RS_UP -> stick(-axes[AXIS_RY], axes[AXIS_RX]);
            case RS_DOWN -> stick(axes[AXIS_RY], axes[AXIS_RX]);
            case RS_LEFT -> stick(-axes[AXIS_RX], axes[AXIS_RY]);
            case RS_RIGHT -> stick(axes[AXIS_RX], axes[AXIS_RY]);
            default -> 0;
        };
    }

    /** A stick's push one way, 0 to 1: nothing inside its round dead middle, then rising to 1 at its rim. */
    private static double stick(double along, double across) {
        double length = Math.hypot(along, across);
        if (length < DEAD || along <= 0) return 0;
        double scaled = Math.min(1, (length - DEAD) / (1 - DEAD));
        return Math.max(0, Math.min(1, along / length * scaled));
    }
}
