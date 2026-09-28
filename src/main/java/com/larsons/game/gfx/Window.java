package com.larsons.game.gfx;

import com.larsons.game.input.Input;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * The game's only window: GLFW, with an OpenGL 3.3 core context.
 *
 * <p>Adapted from the engine's {@code GlContext} + {@code GlWindow}. The engine
 * pumps events on one thread and renders on another, because its game loop
 * predates the GL backend; this game was GPU-first, so it does the usual
 * LWJGL thing instead and keeps GLFW events, simulation and rendering on the
 * main thread. That is also the thread macOS insists on
 * ({@code -XstartOnFirstThread}, which the Gradle tasks add).
 *
 * <p>Input arrives through GLFW callbacks and is latched into an
 * {@link Input}, including files dragged onto the window from the desktop —
 * which is what the sprite importer is built on.
 */
public final class Window implements AutoCloseable {

    private final long handle;
    private final Input input = new Input();
    private final GpuInfo gpu;

    private int width, height;           // logical (window) pixels
    private int fbWidth, fbHeight;       // device (framebuffer) pixels
    private boolean focused = true;

    public Window(String title, int width, int height, int samples, boolean vsync) {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW could not initialise (no display?)");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, Math.max(0, samples));
        glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_TRUE);

        handle = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (handle == MemoryUtil.NULL) {
            glfwTerminate();
            throw new IllegalStateException("could not create an OpenGL 3.3 core window — "
                    + "update the graphics driver (GPU acceleration needs GL 3.3)");
        }

        GLFWVidMode mode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (mode != null) {
            glfwSetWindowPos(handle, Math.max(0, (mode.width() - width) / 2),
                    Math.max(0, (mode.height() - height) / 2));
        }

        glfwMakeContextCurrent(handle);
        GL.createCapabilities();
        glfwSwapInterval(vsync ? 1 : 0);
        gpu = GpuInfo.query();

        // Core profile draws nothing without a bound VAO; each mesh binds its
        // own, this one keeps the default state valid in between.
        glBindVertexArray(glGenVertexArrays());

        measure();
        installCallbacks();
    }

    private void installCallbacks() {
        glfwSetKeyCallback(handle, (w, key, scancode, action, mods) ->
                input.keyEvent(key, action != GLFW_RELEASE));
        glfwSetCharCallback(handle, (w, codepoint) -> input.typed(codepoint));
        glfwSetMouseButtonCallback(handle, (w, button, action, mods) ->
                input.mouseButtonEvent(button, action == GLFW_PRESS));
        glfwSetCursorPosCallback(handle, (w, x, y) -> input.mouseMoved(x, y));
        glfwSetScrollCallback(handle, (w, dx, dy) -> input.scrolled(dy));
        glfwSetWindowSizeCallback(handle, (w, wi, he) -> measure());
        glfwSetFramebufferSizeCallback(handle, (w, wi, he) -> measure());
        glfwSetWindowFocusCallback(handle, (w, f) -> {
            focused = f;
            if (!f) input.releaseAll();
        });
        glfwSetDropCallback(handle, (w, count, names) -> {
            List<String> paths = new ArrayList<>(count);
            for (int i = 0; i < count; i++) paths.add(GLFWDropCallback.getName(names, i));
            input.dropped(paths);
        });
    }

    private void measure() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1);
            glfwGetWindowSize(handle, w, h);
            width = Math.max(1, w.get(0));
            height = Math.max(1, h.get(0));
            glfwGetFramebufferSize(handle, w, h);
            fbWidth = Math.max(1, w.get(0));
            fbHeight = Math.max(1, h.get(0));
        }
    }

    public void show() {
        glfwShowWindow(handle);
        glfwFocusWindow(handle);
        measure();
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public void swapBuffers() {
        glfwSwapBuffers(handle);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public void requestClose() {
        glfwSetWindowShouldClose(handle, true);
    }

    public void setTitle(String title) {
        glfwSetWindowTitle(handle, title);
    }

    public Input input() { return input; }

    public GpuInfo gpu() { return gpu; }

    public boolean focused() { return focused; }

    /** Logical width — what UI coordinates are measured in. */
    public int width() { return width; }

    public int height() { return height; }

    /** Framebuffer width in device pixels (larger than {@link #width()} on HiDPI). */
    public int framebufferWidth() { return fbWidth; }

    public int framebufferHeight() { return fbHeight; }

    /** Device pixels per logical pixel. */
    public float scale() {
        return fbWidth / (float) width;
    }

    @Override
    public void close() {
        Callbacks.glfwFreeCallbacks(handle);
        glfwDestroyWindow(handle);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }
}
