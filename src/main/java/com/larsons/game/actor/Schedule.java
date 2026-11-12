package com.larsons.game.actor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Someone's day: what she does from what time, and where - written as a
 * level keeps it, one line:
 *
 * <pre>
 *   06:30=stand@post; 12:00=wander@square; 13:00=work@post; 21:00=sleep@home
 * </pre>
 *
 * Each entry holds from its time until the next one's (the last until the
 * first, round midnight). Where is {@link #POST} (where she was put in the
 * editor), {@link #HOME} (her house: the one picked for her, or the nearest
 * with a door), a named spot, or a building ({@code building:12}). Edited in
 * Creative Mode as rows (time, what, where); a person without one keeps
 * {@link #DEFAULT}.
 */
public final class Schedule {

    /** Where she was put, facing as she was put. */
    public static final String POST = "post";
    /** Her house. */
    public static final String HOME = "home";
    /** A building, by its object's id: {@code building:12}. */
    public static final String BUILDING = "building:";

    /** What someone does in the hours of an entry. */
    public enum Doing {
        STAND("stand", "Stand"),
        WORK("work", "Work"),
        TEND("tend", "Kneel, tend"),
        WATCH("watch", "Keep watch"),
        WANDER("wander", "Stroll"),
        PATROL("patrol", "To and fro"),
        CHAT("chat", "Chat"),
        INSIDE("inside", "Go inside"),
        SLEEP("sleep", "Sleep");

        private final String key, label;

        Doing(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() { return key; }

        public String label() { return label; }

        /** Whether she is out of sight in it: indoors. */
        public boolean indoors() { return this == INSIDE || this == SLEEP; }

        public static Doing byKey(String k) {
            if (k == null) return null;
            String s = k.strip().toLowerCase(Locale.ROOT);
            for (Doing d : values()) if (d.key.equals(s)) return d;
            return switch (s) {
                case "idle", "post" -> STAND;
                case "home", "in" -> INSIDE;
                case "walk", "stroll" -> WANDER;
                case "guard" -> WATCH;
                default -> null;
            };
        }

        public static List<String> keys() {
            return Arrays.stream(values()).map(Doing::key).toList();
        }
    }

    /** From {@code minute} of the day (0 – 1439): {@code doing} at {@code where}. */
    public record Entry(int minute, Doing doing, String where) {

        public Entry {
            minute = Math.floorMod(minute, 24 * 60);
            if (doing == null) doing = Doing.STAND;
            where = where == null || where.isBlank() ? (doing.indoors() ? HOME : POST) : where.strip();
        }

        /** Its time as it is written: "06:30". */
        public String time() { return Schedule.time(minute); }

        @Override
        public String toString() {
            return time() + "=" + doing.key() + "@" + where;
        }
    }

    /** A day as most keep it: about her post by day, a stroll at noon, home to bed at night. */
    public static final String DEFAULT = "06:30=stand@post; 12:00=wander@post; 13:00=stand@post; 21:00=sleep@home";

    private final List<Entry> entries;

    private Schedule(List<Entry> entries) {
        List<Entry> e = new ArrayList<>(entries);
        e.sort((a, b) -> Integer.compare(a.minute(), b.minute()));
        this.entries = List.copyOf(e);
    }

    public static Schedule of(List<Entry> entries) {
        return new Schedule(entries == null ? List.of() : entries);
    }

    /** The day {@code text} says; what is not an entry is passed over. Blank: {@link #DEFAULT}. */
    public static Schedule parse(String text) {
        if (text == null || text.isBlank()) text = DEFAULT;
        List<Entry> out = new ArrayList<>();
        for (String part : text.split(";")) {
            Entry e = entry(part);
            if (e != null) out.add(e);
        }
        return new Schedule(out);
    }

    /** One entry ("21:00=sleep@home"), or null. */
    static Entry entry(String part) {
        String s = part.strip();
        int eq = s.indexOf('=');
        if (eq < 0) return null;
        int minute = minute(s.substring(0, eq));
        if (minute < 0) return null;
        String rest = s.substring(eq + 1).strip();
        int at = rest.indexOf('@');
        Doing d = Doing.byKey(at < 0 ? rest : rest.substring(0, at));
        if (d == null) return null;
        return new Entry(minute, d, at < 0 ? "" : rest.substring(at + 1));
    }

    /** "06:30" (or "6:30", "6") as minutes of the day, or -1. */
    public static int minute(String time) {
        String t = time.strip();
        try {
            int colon = t.indexOf(':');
            int h = Integer.parseInt(colon < 0 ? t : t.substring(0, colon).strip());
            int m = colon < 0 ? 0 : Integer.parseInt(t.substring(colon + 1).strip());
            if (h < 0 || h > 24 || m < 0 || m > 59) return -1;
            return (h * 60 + m) % (24 * 60);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Minutes of the day as a clock says it: "06:30". */
    public static String time(int minute) {
        int m = Math.floorMod(minute, 24 * 60);
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    public List<Entry> entries() { return entries; }

    public boolean isEmpty() { return entries.isEmpty(); }

    /** The entry that holds at {@code hour} (0 – 24): the last begun - yesterday's last before the first. Null: none. */
    public Entry at(double hour) {
        if (entries.isEmpty()) return null;
        int now = (int) Math.floorMod((long) Math.floor(hour * 60), 24 * 60);
        Entry held = entries.get(entries.size() - 1);
        for (Entry e : entries) {
            if (e.minute() <= now) held = e;
            else break;
        }
        return held;
    }

    /** Every place it names but her post and her home: the spots and buildings to find. */
    public List<String> places() {
        List<String> out = new ArrayList<>();
        for (Entry e : entries) {
            if (!e.where().equals(POST) && !e.where().equals(HOME) && !out.contains(e.where())) out.add(e.where());
        }
        return out;
    }

    /** As a level keeps it. */
    public String text() {
        StringBuilder s = new StringBuilder();
        for (Entry e : entries) {
            if (!s.isEmpty()) s.append("; ");
            s.append(e);
        }
        return s.toString();
    }

    @Override
    public String toString() { return text(); }
}
