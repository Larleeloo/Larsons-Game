package com.larsons.game.story;

import com.larsons.game.math.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Something someone asks of her, as a level's Quest object says it: who
 * gives it, what is to be done, where, and what she gets for it.
 *
 * <pre>
 *   kill    fell {@code count} of {@code target} (a kind - "bear", "bortzal", "risen" - or
 *           someone's name, "Old Bluefur"), inside the quest's ring when it has one
 *   clear   leave no foe standing inside the quest's ring
 *   talk    find and talk to the person called {@code target}
 *   fetch   bring {@code count} of the item {@code target} (they are handed over)
 *   reach   get to the quest's ring
 * </pre>
 *
 * Done, she goes back to whoever gave it ({@code turnIn}: someone else) and
 * is paid: {@code reward} ("coins x40, health potion x2, sword:rare") and
 * {@code xp}.
 *
 * @param id     how a save remembers it (one of its own in the whole game)
 * @param at     the middle of its ring
 * @param radius the ring's reach, metres (0: none - anywhere in the level)
 * @param needs  the quest that has to be handed in before this one is offered (blank: none)
 */
public record Quest(String id, String title, Kind kind, String target, int count, Vec3 at, double radius,
                    String giver, String turnIn, String offer, String remind, String thanks, String reward, int xp,
                    String needs) {

    public enum Kind {
        KILL("kill", "Fell something"), CLEAR("clear", "Clear the area"), TALK("talk", "Talk to someone"),
        FETCH("fetch", "Bring something"), REACH("reach", "Get to the area");

        private final String key, label;

        Kind(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() { return key; }

        public String label() { return label; }

        public static Kind byKey(String key) {
            if (key != null) for (Kind k : values()) if (k.key.equalsIgnoreCase(key.strip())) return k;
            return KILL;
        }

        public static List<String> keys() {
            return java.util.Arrays.stream(values()).map(Kind::key).toList();
        }
    }

    /**
     * The property a quest's own id is kept under. (Not "id": that is the
     * number every object of a level has, and a level's file has room for
     * one of them.)
     */
    public static final String ID = "quest";

    /** The quest a level's object {@code objectId} at {@code at} keeps in {@code props}; {@code level}: its level's name. */
    public static Quest of(String level, int objectId, Vec3 at, Map<String, Object> props) {
        String id = str(props, ID, "");
        if (id.isBlank()) id = (level == null || level.isBlank() ? "level" : level) + ":" + objectId;
        Kind kind = Kind.byKey(str(props, "kind", "kill"));
        String giver = str(props, "giver", "");
        String turnIn = str(props, "turnin", "");
        return new Quest(id.strip().toLowerCase(Locale.ROOT).replace(' ', '_'), str(props, "title", "A task"), kind,
                str(props, "target", "").strip(), (int) Math.max(1, Math.round(num(props, "count", 1))), at,
                Math.max(0, num(props, "radius", 0)), giver.strip(), turnIn.isBlank() ? giver.strip() : turnIn.strip(),
                str(props, "offer", ""), str(props, "remind", ""), str(props, "thanks", ""),
                str(props, "reward", ""), (int) Math.max(0, Math.round(num(props, "xp", 0))),
                str(props, "needs", "").strip().toLowerCase(Locale.ROOT).replace(' ', '_'));
    }

    /** Whether {@code p} is inside its ring (always, when it has none). */
    public boolean holds(Vec3 p) {
        return radius <= 0 || p != null && Math.hypot(p.x() - at.x(), p.z() - at.z()) <= radius;
    }

    /** Whether it is about something called {@code name}: its kind's key or label, or its own name. */
    public boolean targets(String kind, String name) {
        if (target.isBlank()) return true;
        String t = norm(target);
        if (name != null && norm(name).equals(t)) return true;
        if (kind == null) return false;
        String k = norm(kind);
        // "risen" is any of the horde; "bear" a bear, "bears" too
        return k.equals(t) || k.startsWith(t + "_") || (t.endsWith("s") && k.equals(t.substring(0, t.length() - 1)));
    }

    private static String norm(String s) {
        return s.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String str(Map<String, Object> p, String key, String fallback) {
        Object v = p == null ? null : p.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    private static double num(Map<String, Object> p, String key, double fallback) {
        Object v = p == null ? null : p.get(key);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return v == null ? fallback : Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
