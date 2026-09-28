package com.larsons.game.ui;

import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.Font;
import com.larsons.game.gfx.Texture;
import com.larsons.game.gfx.Window;
import com.larsons.game.input.Input;
import com.larsons.game.math.Mat4;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.opengl.GL33C.*;

/**
 * An immediate-mode UI drawn on the GPU: call {@link #begin()}, describe the
 * frame's panels, text and buttons, call {@link #end()}. A button reports a
 * click the frame it happens; nothing is retained between frames except
 * which widget has keyboard focus, which the menus keep themselves.
 *
 * <p>Coordinates are logical pixels from the top left, like the engine's
 * Java2D menus, so HiDPI displays get sharper text and the same layout.
 */
public final class Ui implements AutoCloseable {

    private final Window window;
    private final Batch batch;
    private final Texture white;
    public final Font small;
    public final Font body;
    public final Font large;
    public final Font title;

    private boolean clickTaken;

    public Ui(Window window, Batch batch) {
        this.window = window;
        this.batch = batch;
        this.white = Texture.white();
        float s = window.scale();
        small = new Font(14, false, s);
        body = new Font(18, false, s);
        large = new Font(24, true, s);
        title = new Font(56, true, s);
    }

    public Batch batch() { return batch; }

    public Texture white() { return white; }

    public Input input() { return window.input(); }

    public int width() { return window.width(); }

    public int height() { return window.height(); }

    /** Start drawing UI on top of whatever is on screen. */
    public void begin() {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        batch.begin(Mat4.orthographic(0, window.width(), window.height(), 0, -1, 1));
        clickTaken = false;
    }

    public void end() {
        batch.end();
        glDepthMask(true);
    }

    // --- primitives ------------------------------------------------------------------

    public void rect(float x, float y, float w, float h, float[] c) {
        batch.rect(white, x, y, w, h, 0, 0, 1, 1, c[0], c[1], c[2], c[3]);
    }

    public void outline(float x, float y, float w, float h, float t, float[] c) {
        rect(x, y, w, t, c);
        rect(x, y + h - t, w, t, c);
        rect(x, y, t, h, c);
        rect(x + w - t, y, t, h, c);
    }

    /** A framed translucent panel. */
    public void panel(float x, float y, float w, float h) {
        rect(x, y, w, h, Theme.PANEL);
        outline(x, y, w, h, 1, Theme.PANEL_EDGE);
    }

    /** Dim everything behind a modal. */
    public void scrim() {
        rect(0, 0, width(), height(), Theme.SCRIM);
    }

    public float text(Font font, String s, float x, float y, float[] c) {
        return font.draw(batch, s, x, y, c[0], c[1], c[2], c[3]);
    }

    public float textShadowed(Font font, String s, float x, float y, float[] c) {
        return font.drawShadowed(batch, s, x, y, c[0], c[1], c[2], c[3]);
    }

    public void textCentered(Font font, String s, float cx, float y, float[] c) {
        text(font, s, cx - font.width(s) / 2, y, c);
    }

    /**
     * Word-wrapped text within {@code width}; returns the height used.
     * Explicit newlines start new paragraphs.
     */
    public float paragraph(Font font, String s, float x, float y, float width, float[] c) {
        float line = font.lineHeight() + 3;
        float cy = y;
        for (String para : s.split("\n", -1)) {
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                String next = cur.isEmpty() ? word : cur + " " + word;
                if (font.width(next) > width && !cur.isEmpty()) {
                    text(font, cur.toString(), x, cy, c);
                    cy += line;
                    cur = new StringBuilder(word);
                } else {
                    cur = new StringBuilder(next);
                }
            }
            text(font, cur.toString(), x, cy, c);
            cy += line;
        }
        return cy - y;
    }

    // --- interaction -----------------------------------------------------------------

    public boolean hover(float x, float y, float w, float h) {
        double mx = input().mouseX(), my = input().mouseY();
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Whether the left button was clicked inside the box this frame (and takes the click). */
    public boolean clicked(float x, float y, float w, float h) {
        if (clickTaken || !input().mousePressed(GLFW_MOUSE_BUTTON_LEFT) || !hover(x, y, w, h)) {
            return false;
        }
        clickTaken = true;
        input().consumeMouse(GLFW_MOUSE_BUTTON_LEFT);
        return true;
    }

    /**
     * A button. {@code focused} draws the keyboard-focus highlight; returns
     * whether it was clicked this frame.
     */
    public boolean button(String label, float x, float y, float w, float h, boolean focused,
                          boolean enabled) {
        boolean hot = enabled && hover(x, y, w, h);
        rect(x, y, w, h, hot || focused ? Theme.BUTTON_HOVER : Theme.BUTTON);
        outline(x, y, w, h, focused ? 2 : 1, focused ? Theme.ACCENT : Theme.PANEL_EDGE);
        float[] c = !enabled ? Theme.ITEM_DISABLED : (hot || focused) ? Theme.ITEM_SELECTED : Theme.ITEM;
        Font f = h >= 44 ? large : body;
        text(f, label, x + (w - f.width(label)) / 2, y + (h - f.lineHeight()) / 2, c);
        return enabled && clicked(x, y, w, h);
    }

    public boolean button(String label, float x, float y, float w, float h) {
        return button(label, x, y, w, h, false, true);
    }

    @Override
    public void close() {
        small.close();
        body.close();
        large.close();
        title.close();
        white.close();
    }
}
