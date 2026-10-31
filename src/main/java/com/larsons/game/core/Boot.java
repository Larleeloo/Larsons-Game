package com.larsons.game.core;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Everything the game loads as it starts, step by step, and how far it has
 * got - what the loading screen ({@link LoadingScreen}) shows.
 *
 * <p>Two kinds of step ({@link Step}). Most run one after another on the main
 * thread, where the GPU work has to happen ({@link #run}): a step that takes
 * a while tells its {@link Report} what it is on and how far through it is,
 * and the screen is drawn again as it goes - at most {@link #FPS} times a
 * second, so drawing never holds the loading up. The others are work going
 * on in the background - the sound effects decoding, the stand-in character
 * being drawn, the first scene's sprite sheets on their way to the GPU - and
 * are asked how they are getting on once a frame ({@link #watch}, {@link
 * #poll}) until they are done.
 *
 * <p>The bar is weighted by how long each step took the last time the game
 * started ({@code config/boot.json}, {@link #load}; a guess the first time),
 * so it moves at an even pace instead of racing through the quick steps and
 * stalling on the long one.
 *
 * <p>Main thread only. Nothing here touches OpenGL, so it is tested without
 * a GPU; the {@link Display} draws.
 */
public final class Boot {

    /** How often, at most, the screen is drawn again while a step works. */
    public static final int FPS = 30;
    private static final long FRAME_NS = 1_000_000_000L / FPS;
    /** A step expected to take this long draws a frame as it starts, so it is on screen while it works. */
    private static final double SHOW_SECONDS = 0.08;
    /** How many lines the list of what has just loaded keeps. */
    static final int LOG_LINES = 64;

    /** The parts of the game the steps load: the screen's headings. */
    public enum Group {
        GRAPHICS("Graphics"), SPRITES("Sprites"), WORLD("The world"), SOUND("Sound"), START("Starting");

        public final String label;

        Group(String label) {
            this.label = label;
        }
    }

    /** Everything that loads, in the order it starts. */
    public enum Step {
        WINDOW(Group.GRAPHICS, "Opening the window and OpenGL", 0.3, false),
        INTERFACE(Group.GRAPHICS, "Building the fonts and the sprite shader", 0.4, false),
        SPRITE_INDEX(Group.SPRITES, "Indexing the character sprite sheets", 8, false),
        STAND_IN(Group.SPRITES, "Drawing the stand-in character", 0.5, true),
        OBJECTS(Group.SPRITES, "Indexing the chest and the dummies", 0.05, false),
        ITEMS(Group.SPRITES, "Reading the palette, items and tiers", 0.1, false),
        CLOSEUPS(Group.SPRITES, "Indexing the cutscene close-ups", 0.2, false),
        SCENERY(Group.WORLD, "Finding the trees, grass and rocks", 0.1, false),
        TERRAIN(Group.WORLD, "Raising the highlands", 0.05, false),
        PLANTING(Group.WORLD, "Planting the trees, grass and rocks", 0.3, false),
        SCENERY_SHEETS(Group.WORLD, "Uploading the scenery sheets", 0.5, false),
        GROUND(Group.WORLD, "Loading the ground textures", 0.1, false),
        RENDERERS(Group.WORLD, "Building the hills and world shaders", 0.1, false),
        SOUND_PACK(Group.SOUND, "Setting up the sound pack", 0.05, false),
        SOUND_EFFECTS(Group.SOUND, "Decoding the sound effects", 1, true),
        WARDROBE(Group.START, "Dressing your character", 0.02, false),
        SCENE(Group.START, "Opening the main menu", 0.1, false),
        MUSIC(Group.START, "Decoding the music", 0.5, true),
        SCENE_SHEETS(Group.START, "Uploading its sprite sheets", 1, true);

        public final Group group;
        public final String title;
        /** Seconds it is taken to need before it has ever been timed. */
        final double guess;
        /** Whether it is background work, watched ({@link #watch}), rather than run ({@link #run}). */
        public final boolean background;

        Step(Group group, String title, double guess, boolean background) {
            this.group = group;
            this.title = title;
            this.guess = guess;
            this.background = background;
        }
    }

    /** Where a step is. */
    public enum Phase { WAITING, RUNNING, DONE, FAILED }

    /** What a step tells the screen as it works. */
    public interface Report {
        /** What it is on now: a folder, a file, a name (a second line after a newline). */
        void detail(String text);

        /** How far through it is: {@code done} of {@code total} {@code unit} ({@code total} 0: it cannot say). */
        void progress(long done, long total, String unit);

        /** {@link #progress(long, long, String)} counting nothing in particular. */
        default void progress(long done, long total) {
            progress(done, total, "");
        }

        /** What it found, shown beside it once it is done. */
        void summary(String text);

        /** A line for the list of what has just loaded. */
        void log(String line);
    }

    /** A step's work, on the main thread. */
    @FunctionalInterface
    public interface Work<T> {
        T run(Report report) throws Exception;
    }

    /** A background step's check on how it is getting on: brings the report up to date, and true once done. */
    @FunctionalInterface
    public interface Poll {
        boolean poll(Report report);
    }

    /** Where the boot is shown. */
    public interface Display {
        /** Draw a frame now (and handle the window's events: {@link Closed} if it was closed). */
        void frame();

        /** A step failed: keep it on screen until the player has read it. */
        void failed(Status step, Throwable error);
    }

    /** The window was closed while the game was loading. */
    public static final class Closed extends RuntimeException {
        public Closed() {
            super("the window was closed while the game was loading", null, false, false);
        }
    }

    /** A step failed (the cause is what went wrong). */
    public static final class Failed extends RuntimeException {
        public final Step step;

        Failed(Step step, Throwable cause) {
            super(step.title + ": " + message(cause), cause);
            this.step = step;
        }
    }

    /** One step, where it is and what it has said. */
    public final class Status implements Report {
        public final Step step;
        private Phase phase = Phase.WAITING;
        private String title;
        private String detail = "";
        private String summary = "";
        private String error = "";
        private long done, total;
        private String unit = "";
        private long startNs, endNs;
        private Poll poll;

        Status(Step step) {
            this.step = step;
            this.title = step.title;
        }

        public Phase phase() { return phase; }

        public String title() { return title; }

        public String detail() { return detail; }

        public String summary() { return summary; }

        public String error() { return error; }

        public long done() { return done; }

        public long total() { return total; }

        /** What {@link #done} counts ("folders"), or "". */
        public String unit() { return unit; }

        /** Seconds it has been running, or took. */
        public double seconds() {
            if (phase == Phase.WAITING) return 0;
            long end = phase == Phase.RUNNING ? clock.getAsLong() : endNs;
            return (end - startNs) / 1e9;
        }

        /** How far through it is, 0..1: its own count if it keeps one, else its time against last time's. */
        public double fraction() {
            return switch (phase) {
                case WAITING -> 0;
                case DONE, FAILED -> 1;
                case RUNNING -> total > 0 ? Math.min(1, done / (double) total)
                        : Math.min(0.9, seconds() / expected(step));
            };
        }

        @Override
        public void detail(String text) {
            detail = text == null ? "" : text;
            tick();
        }

        @Override
        public void progress(long done, long total, String unit) {
            this.done = done;
            this.total = total;
            this.unit = unit == null ? "" : unit;
            tick();
        }

        @Override
        public void summary(String text) {
            summary = text == null ? "" : text;
        }

        @Override
        public void log(String line) {
            Boot.this.log(line);
            tick();
        }

        /** Draw again, if this is the step the main thread is busy with and a frame is due. */
        private void tick() {
            if (running == this) frame(false);
        }
    }

    private final Map<Step, Status> steps = new EnumMap<>(Step.class);
    private final Map<Step, Double> last;
    private final LongSupplier clock;
    private final long startNs;
    private final Deque<String> log = new ArrayDeque<>();
    private Display display;
    private Status running;
    private long lastFrame = Long.MIN_VALUE / 2;
    private long finishedNs;

    /** A boot weighted by {@code last} (seconds each step took last time; empty the first time). */
    public Boot(Map<Step, Double> last) {
        this(last, System::nanoTime);
    }

    Boot(Map<Step, Double> last, LongSupplier clock) {
        this.last = new EnumMap<>(Step.class);
        this.last.putAll(last);
        this.clock = clock;
        this.startNs = clock.getAsLong();
        for (Step s : Step.values()) steps.put(s, new Status(s));
    }

    /** Draw on {@code display} from now on. */
    public void setDisplay(Display display) {
        this.display = display;
    }

    /** Call a step something else this time ("Opening the demo"). */
    public void retitle(Step step, String title) {
        steps.get(step).title = title;
    }

    /**
     * A step that ran before there was a screen to show it on (the window,
     * the fonts the screen is written in): {@code nanos} long, and what it
     * did.
     */
    public void done(Step step, long nanos, String summary) {
        Status s = steps.get(step);
        long now = clock.getAsLong();
        s.startNs = now - nanos;
        s.endNs = now;
        s.summary = summary;
        s.phase = Phase.DONE;
    }

    /**
     * Run a step on this thread and return what it made. The screen shows it
     * as it starts (when it is expected to take long enough to see) and as it
     * reports; a step that fails is held on screen ({@link Display#failed})
     * and then thrown as {@link Failed}.
     */
    public <T> T run(Step step, Work<T> work) {
        Status s = steps.get(step);
        s.phase = Phase.RUNNING;
        s.startNs = clock.getAsLong();
        running = s;
        frame(expected(step) >= SHOW_SECONDS);
        try {
            T made = work.run(s);
            s.endNs = clock.getAsLong();
            s.phase = Phase.DONE;
            return made;
        } catch (Closed e) {
            throw e;
        } catch (Throwable e) {
            s.endNs = clock.getAsLong();
            s.phase = Phase.FAILED;
            s.error = message(e);
            log("Could not finish: " + s.error);
            if (display != null) display.failed(s, e);
            throw new Failed(step, e);
        } finally {
            running = null;
        }
    }

    /**
     * Start watching a background step: from now on it is asked how it is
     * getting on every frame ({@link #poll}) until it says it is done.
     */
    public void watch(Step step, Poll poll) {
        Status s = steps.get(step);
        s.phase = Phase.RUNNING;
        s.startNs = clock.getAsLong();
        s.poll = poll;
        check(s);
    }

    /** Ask every background step how it is getting on; true once every step is done. */
    public boolean poll() {
        for (Status s : steps.values()) check(s);
        if (finished() && finishedNs == 0) finishedNs = clock.getAsLong();
        return finished();
    }

    private void check(Status s) {
        if (s.phase != Phase.RUNNING || s.poll == null) return;
        boolean done;
        try {
            done = s.poll.poll(s);
        } catch (RuntimeException e) {
            // background work that cannot say how it is doing is not worth stopping the game for
            s.error = message(e);
            log(s.title + ": " + s.error);
            done = true;
        }
        if (done) {
            s.endNs = clock.getAsLong();
            s.phase = Phase.DONE;
        }
    }

    /** Whether every step has finished (or failed). */
    public boolean finished() {
        for (Status s : steps.values()) {
            if (s.phase == Phase.WAITING || s.phase == Phase.RUNNING) return false;
        }
        return true;
    }

    /** Draw a frame if one is due ({@code force}: now), polling the background steps first. */
    private void frame(boolean force) {
        if (display == null) return;
        long now = clock.getAsLong();
        if (!force && now - lastFrame < FRAME_NS) return;
        lastFrame = now;
        for (Status s : steps.values()) check(s);
        display.frame();
    }

    void log(String line) {
        if (line == null || line.isBlank()) return;
        log.addLast(line);
        while (log.size() > LOG_LINES) log.removeFirst();
    }

    // --- what the screen reads ---------------------------------------------------------

    /** Every step, in order. */
    public List<Status> steps() {
        return List.copyOf(steps.values());
    }

    public Status status(Step step) {
        return steps.get(step);
    }

    /** The step the main thread is busy with, or else the first background step still going (null: none). */
    public Status current() {
        if (running != null) return running;
        for (Status s : steps.values()) if (s.phase == Phase.RUNNING) return s;
        return null;
    }

    /** The background steps still going, but for {@code except}. */
    public List<Status> alsoRunning(Status except) {
        List<Status> out = new ArrayList<>();
        for (Status s : steps.values()) {
            if (s != except && s.phase == Phase.RUNNING && s.step.background) out.add(s);
        }
        return out;
    }

    /** The lines of what has just loaded, oldest first. */
    public List<String> log() {
        return List.copyOf(log);
    }

    /** How many steps are done (failed ones too). */
    public int doneCount() {
        int n = 0;
        for (Status s : steps.values()) if (s.phase == Phase.DONE || s.phase == Phase.FAILED) n++;
        return n;
    }

    /** The step that failed, or null. */
    public Status failure() {
        for (Status s : steps.values()) if (s.phase == Phase.FAILED) return s;
        return null;
    }

    /** Seconds a step took the last time the game started, or -1 if that is not known. */
    public double lastSeconds(Step step) {
        return last.getOrDefault(step, -1.0);
    }

    /** Seconds a step is expected to take: what it took last time, or the guess. */
    double expected(Step step) {
        return Math.max(0.01, last.getOrDefault(step, step.guess));
    }

    /** How far the whole boot is, 0..1: each step's own fraction, weighted by how long it is expected to take. */
    public double fraction() {
        double sum = 0, of = 0;
        for (Status s : steps.values()) {
            double w = expected(s.step);
            sum += w * s.fraction();
            of += w;
        }
        return of <= 0 ? 1 : sum / of;
    }

    /** Seconds since the boot began (until it finished). */
    public double seconds() {
        long end = finishedNs != 0 ? finishedNs : clock.getAsLong();
        return (end - startNs) / 1e9;
    }

    /** Seconds each finished step took, to weigh the bar by next time. */
    public Map<Step, Double> timings() {
        Map<Step, Double> out = new EnumMap<>(Step.class);
        for (Status s : steps.values()) if (s.phase == Phase.DONE) out.put(s.step, s.seconds());
        return out;
    }

    /** One line for the console: how long it all took and the slowest steps. */
    public String report() {
        List<Status> slow = new ArrayList<>();
        for (Status s : steps.values()) if (s.phase == Phase.DONE) slow.add(s);
        slow.sort((a, b) -> Double.compare(b.seconds(), a.seconds()));
        StringBuilder sb = new StringBuilder(String.format("ready in %.2f s", seconds()));
        for (int i = 0; i < Math.min(4, slow.size()); i++) {
            Status s = slow.get(i);
            sb.append(i == 0 ? " - " : ", ").append(s.title()).append(String.format(" %.2f s", s.seconds()));
        }
        return sb.toString();
    }

    // --- last time's timings -----------------------------------------------------------

    /** The timings saved by {@link #save} (empty if there are none, or they cannot be read). */
    public static Map<Step, Double> load(Path file) {
        Map<Step, Double> out = new EnumMap<>(Step.class);
        if (!Files.isRegularFile(file)) return out;
        try {
            Map<String, Object> j = Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
            Map<String, Object> secs = Json.obj(j, "seconds");
            for (Step s : Step.values()) {
                double v = secs == null ? -1 : Json.num(secs, s.name().toLowerCase(), -1);
                if (v >= 0) out.put(s, v);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[boot] cannot read " + file + ": " + e.getMessage());
        }
        return out;
    }

    /** Keep {@code timings} for next time's bar. */
    public static void save(Path file, Map<Step, Double> timings) {
        Map<String, Object> secs = new LinkedHashMap<>();
        for (Map.Entry<Step, Double> e : timings.entrySet()) {
            secs.put(e.getKey().name().toLowerCase(), Math.round(e.getValue() * 1000) / 1000.0);
        }
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("about", "How long each loading step took the last time the game started; "
                + "the loading screen's bar is weighted by it. Safe to delete.");
        j.put("seconds", secs);
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, Json.stringify(j), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            System.err.println("[boot] cannot write " + file + ": " + e.getMessage());
        }
    }

    static String message(Throwable e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
