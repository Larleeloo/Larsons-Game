package com.larsons.game.world;

import com.larsons.game.util.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the saddle is on the saddled horse, frame by frame - its {@code
 * ride.json}, written beside its sheets by the 3D-Modeling repository's
 * {@code creatures/animals.py mount}:
 *
 * <pre>
 *   {"seat": [0, -0.08, 1.583],                    the seat standing: (x, y, z) metres, -y ahead
 *    "stirrups": [[0.32, -0.14, 0.92], ...],
 *    "track": {"walk": [[y, z], ...], ...}}         the seat in each frame of each sheet
 * </pre>
 *
 * <p>The rider's sheets ({@code ride}, {@code ride_run}) are rendered sitting
 * the seat with her sprite's ground {@link #SEAT_Z} below it and her middle
 * over it; the game draws her there over the horse's own sprite, so she
 * rises and falls with its back as it goes.
 */
public final class RideTrack {

    /** Metres from the seat down to her sprite's ground (feminine_model_ride.SEAT_Z). */
    public static final double SEAT_Z = 0.92;
    /** The horse's seat standing, metres: ahead of its middle, and up. */
    public static final double SEAT_AHEAD = 0.08, SEAT_UP = 1.583;
    /** Its sheets' rate, frames a second (the animals' sheets are all played at it). */
    public static final double FPS = 10;

    /** The seat standing: as the file has it, else as the horse was made. */
    private final double ahead, up;
    /** Each sheet's frames: the seat's (ahead, up) in each. */
    private final Map<String, double[][]> track;

    private RideTrack(double ahead, double up, Map<String, double[][]> track) {
        this.ahead = ahead;
        this.up = up;
        this.track = track;
    }

    /** The seat where the horse was made with it, still in every frame: no file (the tests, a missing sheet). */
    public static RideTrack standard() {
        return new RideTrack(SEAT_AHEAD, SEAT_UP, Map.of());
    }

    /** {@code dir/ride.json}, or {@link #standard()} where it is missing or unreadable. */
    public static RideTrack load(Path dir) {
        Path f = dir == null ? null : dir.resolve("ride.json");
        if (f == null || !Files.isRegularFile(f)) return standard();
        try {
            return parse(Files.readString(f, StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[ride] cannot read " + f + ": " + e.getMessage());
            return standard();
        }
    }

    /** A {@code ride.json}'s text. */
    public static RideTrack parse(String text) {
        Map<String, Object> j = Json.asObject(Json.parse(text));
        double ahead = SEAT_AHEAD, up = SEAT_UP;
        if (j.get("seat") instanceof List<?> seat && seat.size() >= 3) {
            ahead = -((Number) seat.get(1)).doubleValue();
            up = ((Number) seat.get(2)).doubleValue();
        }
        Map<String, double[][]> track = new LinkedHashMap<>();
        if (j.get("track") instanceof Map<?, ?> t) {
            for (Map.Entry<?, ?> e : t.entrySet()) {
                List<double[]> frames = new ArrayList<>();
                for (Object f : Json.asArray(e.getValue())) {
                    List<Object> yz = Json.asArray(f);
                    frames.add(new double[]{-((Number) yz.get(0)).doubleValue(), ((Number) yz.get(1)).doubleValue()});
                }
                if (!frames.isEmpty()) track.put(String.valueOf(e.getKey()), frames.toArray(double[][]::new));
            }
        }
        return new RideTrack(ahead, up, track);
    }

    /** How high the seat is standing, metres. */
    public double seatUp() { return up; }

    /** How far ahead of the horse's middle the seat is standing, metres. */
    public double seatAhead() { return ahead; }

    /** How many frames {@code sheet} has here (0: none - the seat stands still in it). */
    public int frames(String sheet) {
        double[][] f = track.get(sheet);
        return f == null ? 0 : f.length;
    }

    /**
     * Where the seat is {@code time} seconds (at its sheet's own rate: the
     * animal's {@code sheetTime}) into {@code sheet}: (ahead, up) metres.
     */
    public double[] seat(String sheet, double time) {
        double[][] f = sheet == null ? null : track.get(sheet);
        if (f == null || f.length == 0) return new double[]{ahead, up};
        int i = Math.floorMod((int) Math.floor(Math.max(0, time) * FPS), f.length);
        return f[i].clone();
    }

    /** How far up off the ground her sprite's ground is, sitting the seat {@code time} into {@code sheet}. */
    public double lift(String sheet, double time) {
        return seat(sheet, time)[1] - SEAT_Z;
    }
}
