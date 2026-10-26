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
    /** Where the window was, and how big, before it went full screen: x, y, width, height. */
    private final int[] windowed = new int[4];

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

    /** How many refreshes each swap waits for: 0 = none (vsync off), 1 = every refresh, 2 = every other. */
    public void swapInterval(int interval) {
        glfwSwapInterval(Math.max(0, interval));
    }

    /**
     * The refresh rate, in Hz, of the monitor the window is on - the one its
     * centre is over (the primary where that cannot be told, as on Wayland) -
     * or 0 if GLFW does not know.
     */
    public int refreshRate() {
        long monitor = monitor();
        if (monitor == MemoryUtil.NULL) return 0;
        GLFWVidMode mode = glfwGetVideoMode(monitor);
        return mode == null ? 0 : Math.max(0, mode.refreshRate());
    }

    /**
     * The fastest refresh rate, in Hz, the monitor the window is on can be
     * set to at the resolution it runs at - from its list of video modes -
     * or 0 if GLFW does not know. Above {@link #refreshRate} when the system
     * runs a fast display slower than it can go (60 Hz on a 144 Hz screen).
     */
    public int maxRefreshRate() {
        long monitor = monitor();
        if (monitor == MemoryUtil.NULL) return 0;
        int fastest = fastestRate(monitor);
        return fastest > 0 ? fastest : refreshRate();
    }

    /** The monitor's fastest refresh rate at the resolution it runs at now, in Hz, or 0. */
    private static int fastestRate(long monitor) {
        GLFWVidMode now = glfwGetVideoMode(monitor);
        GLFWVidMode.Buffer modes = glfwGetVideoModes(monitor);
        if (now == null || modes == null) return 0;
        int best = 0;
        for (int i = 0; i < modes.limit(); i++) {
            GLFWVidMode m = modes.get(i);
            if (m.width() == now.width() && m.height() == now.height()) best = Math.max(best, m.refreshRate());
        }
        return best;
    }

    /** Whether the window has its monitor to itself, full screen ({@link #setFullscreen}). */
    public boolean fullscreen() {
        return glfwGetWindowMonitor(handle) != MemoryUtil.NULL;
    }

    /**
     * Go full screen on the monitor the window is on - at the resolution the
     * monitor runs at, switched to its fastest refresh rate there, so a
     * 144 Hz display the system keeps at 60 shows the game at 144 - or back
     * into the window it was. Returns the refresh rate asked for (0: back in
     * a window).
     */
    public int setFullscreen(boolean on) {
        if (on == fullscreen()) return on ? refreshRate() : 0;
        if (!on) {
            glfwSetWindowMonitor(handle, MemoryUtil.NULL, windowed[0], windowed[1], windowed[2], windowed[3],
                    GLFW_DONT_CARE);
            measure();
            return 0;
        }
        long monitor = monitor();
        if (monitor == MemoryUtil.NULL) return 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.mallocInt(1), y = stack.mallocInt(1);
            glfwGetWindowPos(handle, x, y);
            windowed[0] = x.get(0);
            windowed[1] = y.get(0);
        }
        windowed[2] = width;
        windowed[3] = height;
        GLFWVidMode now = glfwGetVideoMode(monitor);
        if (now == null) return 0;
        int hz = Math.max(now.refreshRate(), fastestRate(monitor));
        glfwSetWindowMonitor(handle, monitor, 0, 0, now.width(), now.height(), hz);
        measure();
        return hz;
    }

    /** The monitor the window is on: its own when full screen, else the one under its centre, else the primary. */
    private long monitor() {
        long monitor = glfwGetWindowMonitor(handle);
        if (monitor == MemoryUtil.NULL) monitor = monitorUnderCentre();
        if (monitor == MemoryUtil.NULL) monitor = glfwGetPrimaryMonitor();
        return monitor;
    }

    private long monitorUnderCentre() {
        var monitors = glfwGetMonitors();
        if (monitors == null) return MemoryUtil.NULL;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.mallocInt(1), y = stack.mallocInt(1);
            glfwGetWindowPos(handle, x, y);
            int cx = x.get(0) + width / 2, cy = y.get(0) + height / 2;
            for (int i = 0; i < monitors.limit(); i++) {
                long m = monitors.get(i);
                GLFWVidMode mode = glfwGetVideoMode(m);
                if (mode == null) continue;
                glfwGetMonitorPos(m, x, y);
                if (cx >= x.get(0) && cx < x.get(0) + mode.width() && cy >= y.get(0) && cy < y.get(0) + mode.height()) {
                    return m;
                }
            }
        }
        return MemoryUtil.NULL;
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
