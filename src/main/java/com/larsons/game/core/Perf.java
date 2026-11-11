package com.larsons.game.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a frame costs: how long its update and its drawing take, how many
 * draw calls and quads it sends, and how many of each kind of thing the
 * scene is running - the numbers of the performance overlay (F8, in the game
 * and in the editors) and of {@code -Dlarsons.perf.log=true}.
 *
 * <p>Everything here is counted on the game's one thread. A renderer says
 * {@link #drew} for each draw call; a scene says {@link #count} for what it
 * has ("folk", "animals", "scenery drawn"); the game says {@link #endFrame}
 * once a frame, which turns the counting over.
 */
public final class Perf {

    /** How quickly the smoothed times follow the frames (a fifth of a second or so at 60 fps). */
    private static final double FOLLOW = 0.08;

    private static int calls, quads, lastCalls, lastQuads;
    private static double updateMs, renderMs, worstUpdate, worstRender, sinceWorst;
    private static final Map<String, Integer> counting = new LinkedHashMap<>();
    private static Map<String, Integer> counted = new LinkedHashMap<>();
    private static double logClock;

    private Perf() {}

    /** A draw call of {@code quadCount} quads (0: a mesh) was sent. */
    public static void drew(int quadCount) {
        calls++;
        quads += quadCount;
    }

    /** The scene has {@code n} of {@code what} this frame (said again: added up). */
    public static void count(String what, int n) {
        counting.merge(what, n, Integer::sum);
    }

    /**
     * The frame is over: its update took {@code updateNs} and its drawing
     * {@code renderNs}; {@code dt} seconds went by. What was counted becomes
     * what is shown.
     */
    static void endFrame(long updateNs, long renderNs, double dt, boolean log) {
        double u = updateNs / 1e6, r = renderNs / 1e6;
        updateMs += (u - updateMs) * FOLLOW;
        renderMs += (r - renderMs) * FOLLOW;
        sinceWorst += dt;
        if (sinceWorst > 2) {                                   // the worst of the last two seconds or so
            sinceWorst = 0;
            worstUpdate = worstRender = 0;
        }
        worstUpdate = Math.max(worstUpdate, u);
        worstRender = Math.max(worstRender, r);
        lastCalls = calls;
        lastQuads = quads;
        calls = quads = 0;
        counted = new LinkedHashMap<>(counting);
        counting.clear();
        if (log) {
            logClock += dt;
            if (logClock >= 1) {
                logClock = 0;
                System.out.println("[perf] " + String.join("  ·  ", lines()));
            }
        }
    }

    /** The update's time, smoothed, milliseconds. */
    public static double updateMs() { return updateMs; }

    /** The drawing's time (the scene's, its grade and the overlays; not the wait for the display), smoothed. */
    public static double renderMs() { return renderMs; }

    /** Draw calls and quads of the frame before this one. */
    public static int drawCalls() { return lastCalls; }

    public static int quads() { return lastQuads; }

    /** What the scene counted in the frame before this one. */
    public static Map<String, Integer> counts() { return counted; }

    /** The overlay's lines, the frame rate aside: times, draw calls, then what the scene has. */
    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(String.format("update %.1f ms (worst %.1f)", updateMs, worstUpdate));
        out.add(String.format("draw %.1f ms (worst %.1f)", renderMs, worstRender));
        out.add(lastCalls + " draw calls, " + lastQuads + " quads");
        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, Integer> e : counted.entrySet()) {
            if (s.length() > 44) {
                out.add(s.toString());
                s.setLength(0);
            }
            if (s.length() > 0) s.append(", ");
            s.append(e.getValue()).append(' ').append(e.getKey());
        }
        if (s.length() > 0) out.add(s.toString());
        return out;
    }

    /** Forget everything (a test's clean start). */
    static void reset() {
        calls = quads = lastCalls = lastQuads = 0;
        updateMs = renderMs = worstUpdate = worstRender = sinceWorst = logClock = 0;
        counting.clear();
        counted = new LinkedHashMap<>();
    }
}
