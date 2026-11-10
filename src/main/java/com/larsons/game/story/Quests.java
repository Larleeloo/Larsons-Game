package com.larsons.game.story;

import com.larsons.game.world.Inventory;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Recipes;
import com.larsons.game.world.Supplies;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How far along each quest is: not taken, under way (and how many of its
 * quarry are felled), done and waiting to be handed in, or handed in. The
 * level's {@link Quest}s say what there is to do; what has been done is
 * kept in a map a playthrough saves ({@code SaveGame.quests()}), so the
 * journal still lists a quest taken three levels ago.
 *
 * <p>The scene tells it what happens - something felled, someone talked to,
 * a frame gone by - and asks it what a person has to say for herself.
 */
public final class Quests {

    public enum State { NONE, ACTIVE, READY, DONE }

    /** A line of the journal. */
    public record Entry(String id, String title, String goal, String giver, String place, State state) {}

    /** Something that happened to a quest, for the scene to say. */
    public record News(Quest quest, State state, String text) {}

    private static final Pattern HOW_MANY = Pattern.compile("(.+?)\\s*x\\s*(\\d+)");

    private final Map<String, Object> store;
    private final List<Quest> quests;
    private final String place;
    private final List<News> news = new ArrayList<>();
    private double clock;

    /**
     * @param store  where progress is kept (a save's quests; a map of the scene's own outside a playthrough)
     * @param quests the level's quests
     * @param place  what the journal calls this level
     */
    public Quests(Map<String, Object> store, List<Quest> quests, String place) {
        this.store = store;
        this.quests = quests;
        this.place = place == null ? "" : place;
    }

    public List<Quest> all() { return quests; }

    public Quest byId(String id) {
        for (Quest q : quests) if (q.id().equals(id)) return q;
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> entry(String id, boolean make) {
        Object v = store.get(id);
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        if (!make) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        store.put(id, m);
        return m;
    }

    public State state(String id) {
        Map<String, Object> e = entry(id, false);
        if (e == null) return State.NONE;
        try {
            return State.valueOf(String.valueOf(e.get("state")).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException x) {
            return State.NONE;
        }
    }

    public State state(Quest q) {
        return state(q.id());
    }

    public int count(Quest q) {
        Map<String, Object> e = entry(q.id(), false);
        return e != null && e.get("count") instanceof Number n ? n.intValue() : 0;
    }

    private void set(Quest q, State s, int count) {
        Map<String, Object> e = entry(q.id(), true);
        e.put("state", s.name().toLowerCase(Locale.ROOT));
        e.put("count", count);
        e.put("title", q.title());
        e.put("giver", q.turnIn());
        e.put("place", place);
        e.put("goal", goal(q, count));
    }

    /** Whether {@code q} can be offered: not taken yet, and what it needs handed in. */
    public boolean offered(Quest q) {
        return state(q) == State.NONE && (q.needs().isBlank() || state(q.needs()) == State.DONE);
    }

    /** Take {@code q}. */
    public void accept(Quest q) {
        set(q, State.ACTIVE, 0);
        news.add(new News(q, State.ACTIVE, "New quest: " + q.title()));
    }

    /** What {@code q} asks, in a line: "Fell bears 1/3 (in the ring)". */
    public String goal(Quest q) {
        return goal(q, count(q));
    }

    private static String goal(Quest q, int count) {
        String where = q.radius() > 0 ? " at the marked place" : "";
        return switch (q.kind()) {
            case KILL -> "Fell " + (q.target().isBlank() ? "foes" : q.target()) + where
                    + (q.count() > 1 ? "  " + Math.min(count, q.count()) + "/" + q.count() : "");
            case CLEAR -> "Clear every foe" + (q.radius() > 0 ? " from the marked place" : " from here");
            case TALK -> "Find and talk to " + q.target();
            case FETCH -> "Bring " + (q.count() > 1 ? q.count() + " " : "") + name(q.target());
            case REACH -> "Get to the marked place";
        };
    }

    private static String name(String itemText) {
        Item i = parse(itemText);
        return i == null ? itemText : i.name().toLowerCase(Locale.ROOT);
    }

    private static Item parse(String text) {
        if (text == null || text.isBlank()) return null;
        String s = text.strip().toLowerCase(Locale.ROOT);
        Item i = Item.parse(s.replace(' ', '_'));
        return i != null ? i : Item.parse(s);
    }

    /** The quests that are under way or waiting to be handed in, for the tracker. */
    public List<Quest> tracked() {
        List<Quest> out = new ArrayList<>();
        for (Quest q : quests) {
            State s = state(q);
            if (s == State.ACTIVE || s == State.READY) out.add(q);
        }
        return out;
    }

    /** Every quest the store knows, this level's and others': the journal. */
    public List<Entry> journal() {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : store.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> m)) continue;
            State s = state(e.getKey());
            if (s == State.NONE) continue;
            Quest q = byId(e.getKey());
            out.add(new Entry(e.getKey(), text(m, "title"), q != null ? goal(q) : text(m, "goal"),
                    text(m, "giver"), text(m, "place"), s));
        }
        out.sort((a, b) -> a.state().compareTo(b.state()));
        return out;
    }

    private static String text(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }

    /** What has happened since this was last asked. */
    public List<News> drainNews() {
        List<News> out = List.copyOf(news);
        news.clear();
        return out;
    }

    private void ready(Quest q, int count) {
        set(q, State.READY, count);
        news.add(new News(q, State.READY, q.title() + ": done - go back to "
                + (q.turnIn().isBlank() ? "whoever asked" : q.turnIn())));
    }

    // --- what happens ------------------------------------------------------------------

    /** Something was felled. */
    public void felled(World.Kill k) {
        for (Quest q : quests) {
            if (q.kind() != Quest.Kind.KILL || state(q) != State.ACTIVE) continue;
            if (!q.holds(k.at()) || !q.targets(k.kind(), k.name())) continue;
            int n = count(q) + 1;
            if (n >= q.count()) ready(q, n);
            else {
                set(q, State.ACTIVE, n);
                news.add(new News(q, State.ACTIVE, q.title() + ": " + n + "/" + q.count()));
            }
        }
    }

    /** She talked to the person called {@code name}. */
    public void talked(String name) {
        for (Quest q : quests) {
            if (q.kind() == Quest.Kind.TALK && state(q) == State.ACTIVE && name != null
                    && name.strip().equalsIgnoreCase(q.target())) {
                ready(q, 1);
            }
        }
    }

    /** A frame: the rings she has reached, the places she has cleared. */
    public void tick(double dt, World world) {
        clock -= dt;
        if (clock > 0) return;
        clock = 0.5;
        for (Quest q : quests) {
            if (state(q) != State.ACTIVE) continue;
            if (q.kind() == Quest.Kind.REACH && q.holds(world.player().ground())) ready(q, 1);
            if (q.kind() == Quest.Kind.CLEAR) {
                World.Area ring = q.radius() > 0 ? new World.Area(q.title(), q.at(), q.radius()) : null;
                if (world.foesIn(ring) == 0) ready(q, 1);
            }
        }
    }

    /** Whether {@code q} can be handed in now: done - or, for a fetch, she has what was asked for. */
    public boolean canHandIn(Quest q, Inventory inv) {
        State s = state(q);
        if (q.kind() == Quest.Kind.FETCH) {
            Item want = parse(q.target());
            return s == State.ACTIVE && want != null && Recipes.count(inv, want.def().id()) >= q.count();
        }
        return s == State.READY;
    }

    /**
     * Hand {@code q} in: what was fetched goes; she is paid. Returns what
     * she is paid in - each an item and how many (coins among them) - for
     * the scene to give her.
     */
    public List<Map.Entry<Item, Integer>> handIn(Quest q, Inventory inv) {
        if (q.kind() == Quest.Kind.FETCH) {
            Item want = parse(q.target());
            if (want != null) Recipes.take(inv, want.def().id(), q.count());
        }
        set(q, State.DONE, Math.max(count(q), q.count()));
        news.add(new News(q, State.DONE, "Quest complete: " + q.title()));
        return rewards(q.reward());
    }

    /** "coins x40, health potion x2, sword:rare" as items and how many of each. */
    public static List<Map.Entry<Item, Integer>> rewards(String text) {
        List<Map.Entry<Item, Integer>> out = new ArrayList<>();
        if (text == null) return out;
        for (String part : text.split(",")) {
            String s = part.strip().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) continue;
            int n = 1;
            Matcher m = HOW_MANY.matcher(s);
            if (m.matches()) {
                s = m.group(1).strip();
                n = Math.min(9999, Integer.parseInt(m.group(2)));
            }
            Item item = parse(s);
            if (item != null) out.add(Map.entry(item, n));
        }
        return out;
    }

    /** {@link #rewards} in words: "40 coins, 2 health potions". */
    public static String rewardText(String text) {
        StringBuilder s = new StringBuilder();
        for (Map.Entry<Item, Integer> e : rewards(text)) {
            if (!s.isEmpty()) s.append(", ");
            ItemDef d = e.getKey().def();
            if (d == Supplies.COINS) s.append(e.getValue()).append(" coins");
            else s.append(e.getValue() > 1 ? e.getValue() + " × " : "").append(e.getKey().fullName());
        }
        return s.toString();
    }

    /** Whether every quest of this level is handed in (and there is one). */
    public boolean allDone() {
        if (quests.isEmpty()) return false;
        for (Quest q : quests) if (state(q) != State.DONE) return false;
        return true;
    }
}
