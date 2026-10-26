package com.larsons.game.core;

import com.larsons.game.gfx.Window;

/**
 * Holds the game to a steady frame rate - 120 frames a second unless told
 * otherwise ({@code -Dlarsons.fps}, or Pause → Settings → Frame rate) - and
 * keeps the numbers that show whether it does.
 *
 * <p><b>How a frame is paced</b> ({@link #plan}). Vsync is the steadiest
 * clock there is, so it is used wherever it gives the target: on a 120 Hz
 * display every refresh, on a 240 Hz one every other. Where it cannot - a
 * 144 or 165 Hz display, vsync turned off, a driver that does not say its
 * refresh rate - the game waits out the rest of each frame itself, against
 * a fixed cadence, so the frames start exactly 1/120 s apart (on a display
 * with variable refresh - G-Sync, FreeSync, ProMotion - they are then also
 * shown exactly so). A display slower than the target paces the game to its
 * own refresh: 120 frames a second cannot be shown on a 60 Hz screen
 * without tearing, and vsync off is what trades that.
 *
 * <p><b>A display held below what it can do.</b> The refresh rate is the one
 * the system runs the display at, which is often 60 Hz on a 120 or 144 Hz
 * screen (the system's default, a power-saving setting, the cable). The
 * monitor's other modes say how fast it can go ({@link Window#maxRefreshRate});
 * when that is faster, {@link #advice} says how to get it: full screen (F11,
 * which switches the display to its fastest mode while the game has it), the
 * system's display settings, or vsync off (Pause → Settings, saved).
 *
 * <p><b>Waiting precisely.</b> The OS sleeps a thread for at least what it
 * is asked and often a good deal more, so the wait sleeps a millisecond at a
 * time only while more remains than a sleep has been seen to take
 * (measured as it goes: the mean and two deviations), and spins the last
 * stretch. A frame that runs late keeps the cadence; one a whole frame or
 * more behind starts it over rather than racing to catch up.
 *
 * <p><b>Smooth steps.</b> Animation is driven by the measured time between
 * frames, which wobbles by a fraction of a millisecond even when every frame
 * is shown exactly one refresh apart - and that wobble is visible as a
 * stutter in anything that moves. While frames arrive on the cadence, the
 * step is the cadence's own period (as measured over many frames, so game
 * time keeps pace with the clock); a frame that is genuinely late or early
 * steps by what it really took.
 */
public final class FramePacer {

    /** The frame rate the game aims for unless told otherwise. */
    public static final int DEFAULT_FPS = 120;
    /** The longest step the simulation takes, so a hitch is never a teleport. */
    static final double MAX_STEP = 0.05;
    /** How far a frame may be from the cadence and still be stepped by it, as a fraction of the period. */
    static final double SNAP = 0.1;

    /**
     * How frames are paced: wait for {@code interval} refreshes at each swap
     * (0: do not wait), and hold frames to {@code limit} a second (0: no
     * limit of the game's own).
     */
    public record Plan(int interval, int limit) {}

    /**
     * The plan for {@code target} frames a second (0: as many as possible) on
     * a display refreshing {@code refresh} times a second (0: unknown), with
     * or without vsync.
     */
    static Plan plan(int target, boolean vsync, int refresh) {
        if (!vsync) return new Plan(0, Math.max(0, target));
        if (target <= 0) return new Plan(1, 0);
        if (refresh <= 0) return new Plan(1, target);           // vsync to an unknown rate: cap it too
        int k = Math.round(refresh / (float) target);
        if (k >= 1 && Math.abs(refresh - k * target) <= 2) return new Plan(k, 0);   // 120 Hz, 240 Hz: vsync is the cadence
        if (refresh < target) return new Plan(1, 0);            // the display is the cap
        return new Plan(1, target);                             // 144 Hz, 165 Hz ...: vsync, capped at the target
    }

    private final Window window;
    private boolean vsync;
    private boolean log;
    private int target;
    private int refresh, fastest;
    private Plan plan = new Plan(1, 0);

    /** Start of the frame being worked on, and of the frame slot the cadence is in (nanoseconds). */
    private long frameStart, slot;
    private boolean started, onCadence;
    private double smoothPeriod;

    /** What one 1 ms sleep has been seen to take: mean and variance (nanoseconds). */
    private double sleepMean = 1.25e6, sleepVar = 0.25e12;

    // the last half second's numbers, and the half second being measured
    private double fps, averageMs, worstMs;
    private double windowTime, windowWorst;
    private int windowFrames;
    private double sinceCheck;

    public FramePacer(Window window, int target, boolean vsync) {
        this.window = window;
        this.vsync = vsync;
        this.target = Math.max(0, target);
        refresh = window.refreshRate();
        fastest = window.maxRefreshRate();
        apply();
    }

    private void apply() {
        plan = plan(target, vsync, refresh);
        window.swapInterval(plan.interval());
        onCadence = false;
        smoothPeriod = 0;
    }

    /** Aim for {@code fps} frames a second (0: as many as the display or the GPU allows). */
    public void setTarget(int fps) {
        fps = Math.max(0, fps);
        if (fps == target) return;
        target = fps;
        apply();
    }

    public int target() { return target; }

    /** Wait for the display's refresh, or not (then the game holds the target itself). */
    public void setVsync(boolean on) {
        if (on == vsync) return;
        vsync = on;
        apply();
    }

    public boolean vsync() { return vsync; }

    /**
     * Read the display's refresh rate again now - the window went full screen
     * or back - rather than at the next of the once-a-second checks.
     */
    public void recheck() {
        sinceCheck = 0;
        refresh = window.refreshRate();
        fastest = window.maxRefreshRate();
        apply();
    }

    /** The refresh rate the display runs at now, in Hz (0: unknown). */
    public int refresh() { return refresh; }

    /** The fastest refresh rate the display can be set to at its resolution, in Hz (0: unknown). */
    public int fastest() { return fastest; }

    /** Print the numbers to the console every half second. */
    public void setLog(boolean log) {
        this.log = log;
    }

    /**
     * The frame starts: returns the step, in seconds, to advance the game by
     * - the time since the last frame started, on the cadence's period while
     * frames keep to it, and never more than {@value #MAX_STEP}.
     */
    public double beginFrame() {
        long now = System.nanoTime();
        if (!started) {
            started = true;
            frameStart = now;
            return 0;
        }
        double raw = (now - frameStart) / 1e9;
        frameStart = now;
        measure(raw);
        return Math.min(MAX_STEP, step(raw));
    }

    /** The step for a frame that really took {@code raw} seconds (see the class notes). */
    private double step(double raw) {
        double period = period();
        if (period <= 0 || Math.abs(raw - period) > SNAP * period) return raw;
        smoothPeriod = smoothPeriod == 0 ? period : smoothPeriod + (raw - smoothPeriod) * 0.02;
        return smoothPeriod;
    }

    /** Seconds a frame lasts on the cadence, or 0 when nothing paces the frames. */
    double period() {
        if (plan.limit() > 0) return 1.0 / plan.limit();
        if (plan.interval() > 0 && refresh > 0) return plan.interval() / (double) refresh;
        return 0;
    }

    /** The frame is on screen: wait until the next one is due. */
    public void endFrame() {
        if (plan.limit() <= 0) return;
        long period = Math.round(1e9 / plan.limit());
        long now = System.nanoTime();
        long next = slot + period;
        if (!onCadence || now - next > period) {
            slot = now;                                         // the first frame, or a whole frame behind: start over
            onCadence = true;
            return;
        }
        waitUntil(next);
        slot = next;
    }

    private void waitUntil(long deadline) {
        while (true) {
            double remaining = deadline - System.nanoTime();
            double sleep = sleepMean + 2 * Math.sqrt(sleepVar);
            if (remaining <= sleep) break;
            long t0 = System.nanoTime();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            double took = System.nanoTime() - t0;
            double d = took - sleepMean;
            sleepMean += d * 0.05;
            sleepVar = Math.max(1e9, sleepVar + (d * d - sleepVar) * 0.05);
        }
        while (System.nanoTime() < deadline) Thread.onSpinWait();
    }

    private void measure(double raw) {
        windowTime += raw;
        windowFrames++;
        windowWorst = Math.max(windowWorst, raw);
        if (windowTime >= 0.5) {
            fps = windowFrames / windowTime;
            averageMs = windowTime * 1000 / windowFrames;
            worstMs = windowWorst * 1000;
            windowTime = 0;
            windowFrames = 0;
            windowWorst = 0;
            if (log) {
                System.out.printf(java.util.Locale.ROOT, "[frames] %.1f fps  %.2f ms  longest %.2f ms  (%s)%n",
                        fps, averageMs, worstMs, describe());
            }
            // Vsync asked for, and frames come faster than the display refreshes: the
            // driver is not waiting (a control-panel override, some compositors). Cap them.
            if (plan.limit() == 0 && plan.interval() > 0 && refresh > 0 && target > 0
                    && fps > 1.25 * refresh / plan.interval()) {
                plan = new Plan(plan.interval(), target);
                onCadence = false;
                smoothPeriod = 0;
            }
        }
        // the window may have moved to another display
        sinceCheck += raw;
        if (sinceCheck >= 1) {
            sinceCheck = 0;
            int r = window.refreshRate();
            if (r != refresh) {
                refresh = r;
                fastest = window.maxRefreshRate();
                apply();
            }
        }
    }

    /** Frames a second over the last half second. */
    public double fps() { return fps; }

    /** The average frame over the last half second, in milliseconds. */
    public double averageMs() { return averageMs; }

    /** The longest frame of the last half second, in milliseconds - a hitch shows here. */
    public double worstMs() { return worstMs; }

    /** How frames are paced, in a few words: "vsync 120 Hz", "capped at 120 (144 Hz display)". */
    public String describe() {
        return describe(plan, target, refresh, fastest);
    }

    /**
     * How {@code plan} paces frames for {@code target} on a display running at
     * {@code refresh} Hz that can go up to {@code fastest} (0s: unknown).
     */
    static String describe(Plan plan, int target, int refresh, int fastest) {
        String display = refresh > 0 ? refresh + " Hz display" : "display rate unknown";
        if (plan.interval() == 0) {
            return plan.limit() > 0 ? "vsync off, capped at " + plan.limit() + " fps" : "vsync off, uncapped";
        }
        if (plan.limit() > 0) return "vsync, capped at " + plan.limit() + " fps (" + display + ")";
        if (plan.interval() > 1) return "vsync every " + plan.interval() + " refreshes (" + display + ")";
        if (refresh <= 0) return "vsync (" + display + ")";
        if (held(target, refresh, fastest)) {
            return "vsync " + refresh + " Hz (the display is set to " + refresh + " Hz but can do " + fastest + ")";
        }
        if (target > 0 && refresh < target - 2) return "vsync " + refresh + " Hz (the display is below " + target + ")";
        return "vsync " + refresh + " Hz";
    }

    /** Whether vsync holds the game below the target only because the display is set slower than it can go. */
    static boolean held(int target, int refresh, int fastest) {
        int want = target > 0 ? target : fastest;
        return refresh > 0 && fastest > refresh + 2 && refresh < want - 2;
    }

    /**
     * What to do when vsync holds the game to a display set slower than it
     * can go (see the class notes), or null when nothing holds it back.
     */
    public String advice() {
        if (!vsync || !held(target, refresh, fastest)) return null;
        return advice(refresh, fastest);
    }

    static String advice(int refresh, int fastest) {
        return "the display runs at " + refresh + " Hz but can do " + fastest + " Hz: press F11 for full screen at "
                + fastest + " Hz, raise its refresh rate in the system's display settings (Windows: Settings > "
                + "System > Display > Advanced display), or turn vsync off in Pause > Settings";
    }
}
