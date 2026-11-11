package com.larsons.game.input;

import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Everything the player does with a key or a button, by what it does rather
 * than what is pressed for it: the game asks {@link Input#pressed(Action)},
 * and {@link Bindings} says which keys, mouse buttons and gamepad inputs
 * that is - the defaults here, or the player's own (Pause, Controls).
 */
public enum Action {

    // --- moving ---------------------------------------------------------------------
    MOVE_FORWARD("Walk forward", Group.MOVING, keys(GLFW_KEY_W), pads(Pad.LS_UP)),
    MOVE_BACK("Walk back", Group.MOVING, keys(GLFW_KEY_S), pads(Pad.LS_DOWN)),
    MOVE_LEFT("Walk left", Group.MOVING, keys(GLFW_KEY_A), pads(Pad.LS_LEFT)),
    MOVE_RIGHT("Walk right", Group.MOVING, keys(GLFW_KEY_D), pads(Pad.LS_RIGHT)),
    RUN("Run", Group.MOVING, keys(GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT), pads()),
    SPRINT("Sprint", Group.MOVING, keys(GLFW_KEY_LEFT_CONTROL, GLFW_KEY_RIGHT_CONTROL), pads(Pad.LS)),
    CROUCH("Crouch / dive", Group.MOVING, keys(GLFW_KEY_C), pads(Pad.DPAD_DOWN)),
    JUMP("Jump / climb up", Group.MOVING, keys(GLFW_KEY_SPACE), pads(Pad.A)),
    DODGE("Dash (hold: roll)", Group.MOVING, keys(GLFW_KEY_LEFT_ALT, GLFW_KEY_Z), pads(Pad.B)),

    // --- fighting -------------------------------------------------------------------
    ATTACK("Attack", Group.FIGHTING, List.of(Bind.mouse(GLFW_MOUSE_BUTTON_LEFT), Bind.key(GLFW_KEY_F)),
            pads(Pad.RB)),
    HEAVY("Heavy attack", Group.FIGHTING, keys(GLFW_KEY_X), pads(Pad.RT)),
    SPIN("Spin attack", Group.FIGHTING, keys(GLFW_KEY_R), pads(Pad.Y)),
    PARRY("Parry", Group.FIGHTING, keys(GLFW_KEY_Q), pads(Pad.LT)),
    BLOCK("Block", Group.FIGHTING, keys(GLFW_KEY_B), pads(Pad.LB)),
    BASH("Shield bash", Group.FIGHTING, keys(GLFW_KEY_V), pads()),
    LOCK("Lock on", Group.FIGHTING, keys(GLFW_KEY_L), pads(Pad.RS)),
    LOCK_NEXT("Next target", Group.FIGHTING, keys(GLFW_KEY_K), pads()),

    // --- things ---------------------------------------------------------------------
    USE("Use / pick up / talk", Group.THINGS, keys(GLFW_KEY_E), pads(Pad.X)),
    DROP("Drop what you hold", Group.THINGS, keys(GLFW_KEY_G), pads()),
    INVENTORY("Inventory", Group.THINGS, keys(GLFW_KEY_I), pads(Pad.VIEW)),
    CARDS("Cards", Group.THINGS, keys(GLFW_KEY_J), pads(Pad.DPAD_UP)),
    JOURNAL("Quest journal", Group.THINGS, keys(GLFW_KEY_U), pads()),
    HOTBAR_1("Hotbar slot 1", Group.THINGS, keys(GLFW_KEY_1), pads()),
    HOTBAR_2("Hotbar slot 2", Group.THINGS, keys(GLFW_KEY_2), pads()),
    HOTBAR_3("Hotbar slot 3", Group.THINGS, keys(GLFW_KEY_3), pads()),
    HOTBAR_4("Hotbar slot 4", Group.THINGS, keys(GLFW_KEY_4), pads()),
    HOTBAR_5("Hotbar slot 5", Group.THINGS, keys(GLFW_KEY_5), pads()),
    HOTBAR_PREV("Hotbar: one left", Group.THINGS, keys(), pads(Pad.DPAD_LEFT)),
    HOTBAR_NEXT("Hotbar: one right", Group.THINGS, keys(), pads(Pad.DPAD_RIGHT)),

    // --- the camera -----------------------------------------------------------------
    CAMERA_LEFT("Camera left", Group.CAMERA, keys(GLFW_KEY_LEFT), pads(Pad.RS_LEFT)),
    CAMERA_RIGHT("Camera right", Group.CAMERA, keys(GLFW_KEY_RIGHT), pads(Pad.RS_RIGHT)),
    CAMERA_UP("Camera up", Group.CAMERA, keys(GLFW_KEY_UP), pads(Pad.RS_UP)),
    CAMERA_DOWN("Camera down", Group.CAMERA, keys(GLFW_KEY_DOWN), pads(Pad.RS_DOWN)),
    ZOOM_IN("Zoom in", Group.CAMERA, keys(GLFW_KEY_EQUAL, GLFW_KEY_KP_ADD), pads()),
    ZOOM_OUT("Zoom out", Group.CAMERA, keys(GLFW_KEY_MINUS, GLFW_KEY_KP_SUBTRACT), pads()),
    CAMERA_HEIGHT("Camera height", Group.CAMERA, keys(GLFW_KEY_TAB), pads()),

    // --- the rest -------------------------------------------------------------------
    EMOTE_LAUGH("Laugh", Group.OTHER, keys(GLFW_KEY_F1), pads()),
    EMOTE_CRY("Cry", Group.OTHER, keys(GLFW_KEY_F2), pads()),
    EMOTE_SURPRISE("Surprise", Group.OTHER, keys(GLFW_KEY_F3), pads()),
    EMOTE_ANGRY("Anger", Group.OTHER, keys(GLFW_KEY_F4), pads()),
    TIME_LAPSE("Time-lapse (held)", Group.OTHER, keys(GLFW_KEY_N), pads()),
    HIDE_HUD("Hide the HUD", Group.OTHER, keys(GLFW_KEY_H), pads()),
    DEBUG("Developer's readout", Group.OTHER, keys(GLFW_KEY_F7), pads()),
    PERF("Performance overlay", Group.OTHER, keys(GLFW_KEY_F8), pads()),
    PAUSE("Pause menu", Group.OTHER, keys(GLFW_KEY_ESCAPE), pads(Pad.MENU));

    /** The Controls page's sections. */
    public enum Group {
        MOVING("Moving"), FIGHTING("Fighting"), THINGS("Things"), CAMERA("Camera"), OTHER("Other");

        private final String label;

        Group(String label) { this.label = label; }

        public String label() { return label; }
    }

    private final String label;
    private final Group group;
    private final List<Bind> keys, pads;

    Action(String label, Group group, List<Bind> keys, List<Bind> pads) {
        this.label = label;
        this.group = group;
        this.keys = keys;
        this.pads = pads;
    }

    private static List<Bind> keys(int... keys) {
        return java.util.Arrays.stream(keys).mapToObj(Bind::key).toList();
    }

    private static List<Bind> pads(int... inputs) {
        return java.util.Arrays.stream(inputs).mapToObj(Bind::pad).toList();
    }

    /** What the Controls page calls it. */
    public String label() { return label; }

    public Group group() { return group; }

    /** The keys and mouse buttons it comes with. */
    public List<Bind> defaultKeys() { return keys; }

    /** The gamepad inputs it comes with. */
    public List<Bind> defaultPads() { return pads; }

    /** How it is saved: its name in lower case. */
    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }

    /** Whether it must always have a key (the pause menu: there would be no way back to rebind it). */
    public boolean required() { return this == PAUSE; }
}
