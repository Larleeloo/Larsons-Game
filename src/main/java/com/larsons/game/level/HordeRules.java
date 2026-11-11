package com.larsons.game.level;

import com.larsons.game.actor.Breed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A horde map's rules: what she starts with, how the rounds grow, which of
 * the Risen come. A level is a horde map when it has them ({@code "horde":
 * {...}} in its {@code level.json}, kept in {@link Level#extra()}); the
 * horde editor's Horde panel sets every one.
 *
 * <pre>
 *   round n:   first + more × (n - 1) + n²/6 Risen, atOnce (+ 1 a round, to 40) of them up at a time,
 *              each with (1 + growth × (n - 1)) × its health
 * </pre>
 *
 * @param points  the points she starts with
 * @param first   how many Risen come in the first round
 * @param more    how many more each round after
 * @param atOnce  how many are up at once, in the first round
 * @param growth  how much more health they have each round (0.12: 12%)
 * @param rest    seconds between rounds
 * @param lives   how many times she can be knocked out before it is over (1: once)
 * @param kit     what she starts with ("dagger, health potion x2")
 * @param kinds   the Risen that come (their {@link Breed} keys); empty: all of them
 */
public record HordeRules(int points, int first, int more, int atOnce, double growth, double rest, int lives,
                         String kit, List<String> kinds) {

    public static final String KEY = "horde";
    /** What a blow that lands pays, and a Risen felled (the horde game's, said in the editor too). */
    public static final int HIT = 10, KILL = 60;

    /** The rules a new horde map starts with. */
    public static final HordeRules DEFAULT = new HordeRules(500, 6, 3, 12, 0.12, 8, 1, "dagger, health potion", List.of());

    public HordeRules {
        points = Math.max(0, Math.min(100000, points));
        first = Math.max(1, Math.min(200, first));
        more = Math.max(0, Math.min(50, more));
        atOnce = Math.max(1, Math.min(40, atOnce));
        growth = Math.max(0, Math.min(1, growth));
        rest = Math.max(1, Math.min(60, rest));
        lives = Math.max(1, Math.min(9, lives));
        kit = kit == null ? "" : kit;
        kinds = kinds == null ? List.of() : List.copyOf(kinds);
    }

    /** Every Risen there is, as the horde can be made of. */
    public static List<Breed> risen() {
        List<Breed> out = new ArrayList<>();
        for (Breed b : Breed.values()) if (b.risen()) out.add(b);
        return out;
    }

    /** Whether {@code breed} comes on this map. */
    public boolean comes(Breed breed) {
        return kinds.isEmpty() || kinds.contains(breed.key());
    }

    /** How many come in round {@code n} (1 the first). */
    public int count(int n) {
        return (int) Math.min(400, first + (long) more * (n - 1) + (long) n * n / 6);
    }

    /** How many are up at once in round {@code n}. */
    public int upAtOnce(int n) {
        return Math.min(40, atOnce + (n - 1));
    }

    /** Their health, times, in round {@code n}. */
    public double health(int n) {
        return 1 + growth * (n - 1);
    }

    /** Seconds between one coming and the next in round {@code n}. */
    public double every(int n) {
        return Math.max(0.35, 2.2 - 0.16 * (n - 1));
    }

    /** Whether {@code level} is a horde map. */
    public static boolean has(Level level) {
        return level != null && level.extra().get(KEY) instanceof Map<?, ?>;
    }

    /** {@code level}'s rules, or null when it is no horde map. */
    public static HordeRules of(Level level) {
        if (!has(level)) return null;
        Map<?, ?> m = (Map<?, ?>) level.extra().get(KEY);
        HordeRules d = DEFAULT;
        List<String> kinds = new ArrayList<>();
        if (m.get("kinds") instanceof List<?> l) {
            for (Object o : l) if (o != null && Breed.byKey(o.toString()) != null) kinds.add(o.toString());
        }
        return new HordeRules(whole(m, "points", d.points), whole(m, "first", d.first), whole(m, "more", d.more),
                whole(m, "atOnce", d.atOnce), number(m, "growth", d.growth), number(m, "rest", d.rest),
                whole(m, "lives", d.lives), m.get("kit") == null ? d.kit : m.get("kit").toString(), kinds);
    }

    /** Make {@code level} a horde map with these rules. */
    public void put(Level level) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("points", points);
        m.put("first", first);
        m.put("more", more);
        m.put("atOnce", atOnce);
        m.put("growth", Math.round(growth * 1000) / 1000.0);
        m.put("rest", Math.round(rest * 10) / 10.0);
        m.put("lives", lives);
        m.put("kit", kit);
        m.put("kinds", new ArrayList<>(kinds));
        level.extra().put(KEY, m);
    }

    private static double number(Map<?, ?> m, String key, double fallback) {
        return m.get(key) instanceof Number n ? n.doubleValue() : fallback;
    }

    private static int whole(Map<?, ?> m, String key, int fallback) {
        return m.get(key) instanceof Number n ? (int) Math.round(n.doubleValue()) : fallback;
    }

    public HordeRules withPoints(int v) { return new HordeRules(v, first, more, atOnce, growth, rest, lives, kit, kinds); }

    public HordeRules withFirst(int v) { return new HordeRules(points, v, more, atOnce, growth, rest, lives, kit, kinds); }

    public HordeRules withMore(int v) { return new HordeRules(points, first, v, atOnce, growth, rest, lives, kit, kinds); }

    public HordeRules withAtOnce(int v) { return new HordeRules(points, first, more, v, growth, rest, lives, kit, kinds); }

    public HordeRules withGrowth(double v) { return new HordeRules(points, first, more, atOnce, v, rest, lives, kit, kinds); }

    public HordeRules withRest(double v) { return new HordeRules(points, first, more, atOnce, growth, v, lives, kit, kinds); }

    public HordeRules withLives(int v) { return new HordeRules(points, first, more, atOnce, growth, rest, v, kit, kinds); }

    public HordeRules withKit(String v) { return new HordeRules(points, first, more, atOnce, growth, rest, lives, v, kinds); }

    public HordeRules withKinds(List<String> v) { return new HordeRules(points, first, more, atOnce, growth, rest, lives, kit, v); }
}
