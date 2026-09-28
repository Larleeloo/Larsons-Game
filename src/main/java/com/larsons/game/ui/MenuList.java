package com.larsons.game.ui;

import com.larsons.game.input.Input;

import static org.lwjgl.glfw.GLFW.*;

/**
 * A vertical column of buttons driven by mouse or keyboard: ↑/↓ (or W/S) move
 * the focus, Enter or Space activates, hovering with the mouse moves the
 * focus to what is under it — the engine's {@code Menu} behaviour on the GPU
 * UI.
 */
public final class MenuList {

    private int focus;

    public int focus() { return focus; }

    public void setFocus(int focus) { this.focus = focus; }

    /**
     * Draw {@code labels} as a column and return the index activated this
     * frame, or -1.
     */
    public int show(Ui ui, String[] labels, float x, float y, float w, float h, float gap) {
        Input in = ui.input();
        int n = labels.length;
        focus = Math.floorMod(focus, n);
        if (in.repeated(GLFW_KEY_DOWN) || in.repeated(GLFW_KEY_S)) focus = (focus + 1) % n;
        if (in.repeated(GLFW_KEY_UP) || in.repeated(GLFW_KEY_W)) focus = (focus + n - 1) % n;

        int activated = -1;
        for (int i = 0; i < n; i++) {
            float by = y + i * (h + gap);
            if (ui.hover(x, by, w, h) && (in.mouseDX() != 0 || in.mouseDY() != 0)) focus = i;
            if (ui.button(labels[i], x, by, w, h, i == focus, true)) activated = i;
        }
        if (activated < 0 && (in.pressed(GLFW_KEY_ENTER) || in.pressed(GLFW_KEY_KP_ENTER)
                || in.pressed(GLFW_KEY_SPACE))) {
            activated = focus;
            in.consumeKey(GLFW_KEY_ENTER);
            in.consumeKey(GLFW_KEY_SPACE);
        }
        return activated;
    }
}
