package com.larsons.game.core;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A scripted run, for automated screenshots and smoke tests of the GPU build:
 *
 * <pre>
 *   ./gradlew run -Dlarsons.script="scene demo; wait 1; pitch 45; state walk; wait 0.5; shot a.png; quit"
 * </pre>
 *
 * <p>Commands, separated by {@code ;}: {@code wait <seconds>},
 * {@code scene <menu|demo>}, {@code shot <file.png>}, {@code quit},
 * {@code key <name>} and {@code click <x> <y>} (real input events),
 * {@code drop <path>} (as if dropped on the window), {@code importsave
 * [slot/name]} and {@code importclose}, and
 * whatever the current scene understands through {@link Scriptable} (the demo
 * takes {@code pitch}, {@code yaw}, {@code zoom}, {@code state}, {@code face},
 * {@code move}, {@code pickup}, {@code pause} …). Unknown commands are reported and skipped.
 */
public final class Autopilot {

    /** A scene that takes script commands. */
    public interface Scriptable {
        /** Run {@code command} with {@code argument}; false if it is not one of this scene's. */
        boolean command(String command, String argument);
    }

    private final Deque<String> queue = new ArrayDeque<>();
    private double waiting;
    /** Keys and buttons pressed by the script, released at the start of the next step. */
    private final java.util.List<Runnable> releases = new java.util.ArrayList<>();

    public Autopilot(String script) {
        if (script != null) {
            for (String c : script.split(";")) {
                if (!c.isBlank()) queue.add(c.trim());
            }
        }
    }

    public boolean active() {
        return !queue.isEmpty() || waiting > 0;
    }

    /** A GLFW key code from its name: "escape", "e", "f12", "1". */
    static int glfwKey(String name) {
        String n = name.trim().toUpperCase().replace(' ', '_');
        if (n.equals("ESC")) n = "ESCAPE";
        try {
            return org.lwjgl.glfw.GLFW.class.getField("GLFW_KEY_" + n).getInt(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("no such key: " + name);
        }
    }

    /** Run commands until the next wait. {@code shots} are deferred until after rendering. */
    public void step(double dt, Game game) {
        releases.forEach(Runnable::run);
        releases.clear();
        if (waiting > 0) {
            waiting -= dt;
            if (waiting > 0) return;
        }
        while (!queue.isEmpty()) {
            String line = queue.poll();
            int sp = line.indexOf(' ');
            String cmd = (sp < 0 ? line : line.substring(0, sp)).toLowerCase();
            String arg = sp < 0 ? "" : line.substring(sp + 1).trim();
            switch (cmd) {
                case "wait" -> {
                    waiting = Double.parseDouble(arg);
                    return;
                }
                case "scene" -> game.switchTo(arg);
                case "shot" -> {
                    game.requestScreenshot(Path.of(arg));
                    // Give the frame a chance to render before the next command.
                    waiting = 1e-6;
                    return;
                }
                case "quit" -> game.quit();
                // Real input events, as if typed or clicked: "key escape", "click 640 360".
                case "key" -> {
                    int code = glfwKey(arg);
                    var in = game.window().input();
                    in.keyEvent(code, true);
                    releases.add(() -> in.keyEvent(code, false));
                    waiting = 1e-6;
                    return;
                }
                case "click" -> {
                    String[] xy = arg.split("[ ,]+");
                    var in = game.window().input();
                    in.mouseMoved(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
                    in.mouseButtonEvent(0, true);
                    releases.add(() -> in.mouseButtonEvent(0, false));
                    waiting = 1e-6;
                    return;
                }
                // The drag-and-drop importer, as if files had been dropped and Save clicked.
                case "drop" -> game.importer().open(java.util.List.of(Path.of(arg)));
                case "importsave" -> game.importer().scriptSave(game, arg);
                case "importclose" -> game.importer().close();
                default -> {
                    if (!(game.scene() instanceof Scriptable s) || !s.command(cmd, arg)) {
                        System.err.println("[script] unknown command: " + line);
                    }
                }
            }
        }
    }
}
