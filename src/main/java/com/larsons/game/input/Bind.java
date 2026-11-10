package com.larsons.game.input;

import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;

/**
 * One thing that can be pressed for an {@link Action}: a key, a mouse button,
 * or something on the gamepad - a button, a trigger, or a stick pushed one
 * way ({@link Pad}).
 *
 * @param kind what it is on
 * @param code GLFW's key or mouse button, or the {@link Pad} input
 */
public record Bind(Kind kind, int code) {

    public enum Kind { KEY, MOUSE, PAD }

    public static Bind key(int key) { return new Bind(Kind.KEY, key); }

    public static Bind mouse(int button) { return new Bind(Kind.MOUSE, button); }

    public static Bind pad(int input) { return new Bind(Kind.PAD, input); }

    /** Whether it is on the gamepad (else: the keyboard or the mouse). */
    public boolean onPad() { return kind == Kind.PAD; }

    /** How it is saved: {@code key:69}, {@code mouse:0}, {@code pad:a}. */
    public String save() {
        return switch (kind) {
            case KEY -> "key:" + code;
            case MOUSE -> "mouse:" + code;
            case PAD -> "pad:" + Pad.key(code);
        };
    }

    /** The bind {@link #save} wrote, or null. */
    public static Bind parse(String s) {
        if (s == null) return null;
        int colon = s.indexOf(':');
        if (colon < 0) return null;
        String what = s.substring(0, colon).trim().toLowerCase(Locale.ROOT), v = s.substring(colon + 1).trim();
        try {
            return switch (what) {
                case "key" -> key(Integer.parseInt(v));
                case "mouse" -> mouse(Integer.parseInt(v));
                case "pad" -> {
                    int p = Pad.byKey(v);
                    yield p < 0 ? null : pad(p);
                }
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** What it is called on a prompt or in the Controls page: "E", "Left click", "LB". */
    public String label() {
        return switch (kind) {
            case KEY -> keyName(code);
            case MOUSE -> switch (code) {
                case GLFW_MOUSE_BUTTON_LEFT -> "Left click";
                case GLFW_MOUSE_BUTTON_RIGHT -> "Right click";
                case GLFW_MOUSE_BUTTON_MIDDLE -> "Middle click";
                default -> "Mouse " + (code + 1);
            };
            case PAD -> Pad.label(code);
        };
    }

    /** A key's name as it is printed on it. */
    public static String keyName(int key) {
        if (key >= GLFW_KEY_A && key <= GLFW_KEY_Z) return String.valueOf((char) ('A' + key - GLFW_KEY_A));
        if (key >= GLFW_KEY_0 && key <= GLFW_KEY_9) return String.valueOf((char) ('0' + key - GLFW_KEY_0));
        if (key >= GLFW_KEY_F1 && key <= GLFW_KEY_F25) return "F" + (key - GLFW_KEY_F1 + 1);
        if (key >= GLFW_KEY_KP_0 && key <= GLFW_KEY_KP_9) return "Num " + (key - GLFW_KEY_KP_0);
        return switch (key) {
            case GLFW_KEY_SPACE -> "Space";
            case GLFW_KEY_ESCAPE -> "Esc";
            case GLFW_KEY_ENTER -> "Enter";
            case GLFW_KEY_TAB -> "Tab";
            case GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW_KEY_INSERT -> "Insert";
            case GLFW_KEY_DELETE -> "Delete";
            case GLFW_KEY_RIGHT -> "Right";
            case GLFW_KEY_LEFT -> "Left";
            case GLFW_KEY_DOWN -> "Down";
            case GLFW_KEY_UP -> "Up";
            case GLFW_KEY_PAGE_UP -> "Page Up";
            case GLFW_KEY_PAGE_DOWN -> "Page Down";
            case GLFW_KEY_HOME -> "Home";
            case GLFW_KEY_END -> "End";
            case GLFW_KEY_CAPS_LOCK -> "Caps Lock";
            case GLFW_KEY_LEFT_SHIFT -> "Shift";
            case GLFW_KEY_RIGHT_SHIFT -> "Right Shift";
            case GLFW_KEY_LEFT_CONTROL -> "Ctrl";
            case GLFW_KEY_RIGHT_CONTROL -> "Right Ctrl";
            case GLFW_KEY_LEFT_ALT -> "Alt";
            case GLFW_KEY_RIGHT_ALT -> "Right Alt";
            case GLFW_KEY_LEFT_SUPER -> "Super";
            case GLFW_KEY_RIGHT_SUPER -> "Right Super";
            case GLFW_KEY_APOSTROPHE -> "'";
            case GLFW_KEY_COMMA -> ",";
            case GLFW_KEY_MINUS -> "-";
            case GLFW_KEY_PERIOD -> ".";
            case GLFW_KEY_SLASH -> "/";
            case GLFW_KEY_SEMICOLON -> ";";
            case GLFW_KEY_EQUAL -> "=";
            case GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW_KEY_RIGHT_BRACKET -> "]";
            case GLFW_KEY_BACKSLASH -> "\\";
            case GLFW_KEY_GRAVE_ACCENT -> "`";
            case GLFW_KEY_KP_ADD -> "Num +";
            case GLFW_KEY_KP_SUBTRACT -> "Num -";
            case GLFW_KEY_KP_MULTIPLY -> "Num *";
            case GLFW_KEY_KP_DIVIDE -> "Num /";
            case GLFW_KEY_KP_ENTER -> "Num Enter";
            case GLFW_KEY_KP_DECIMAL -> "Num .";
            default -> "Key " + key;
        };
    }
}
