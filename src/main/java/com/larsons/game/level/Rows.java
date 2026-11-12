package com.larsons.game.level;

import com.larsons.game.actor.Schedule;
import com.larsons.game.cards.Card;
import com.larsons.game.cards.CardBook;
import com.larsons.game.world.Forged;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A list a property keeps as one line of text - a shop's stock ("health
 * potion x5 @20, sword:rare"), a quest's pay, what someone can be asked,
 * someone's day - edited in Creative Mode as rows: each its columns, a thing
 * picked from a list (the items there are, the times of day, the spots of
 * the level), a number, a line of words; a row added with "+ Add", taken
 * off with ×. What is kept is the text the game has always read, so a level
 * made before opens as it was, and what the rows cannot say is kept as it
 * was written.
 *
 * @param columns what each row has, left to right
 * @param add     what the button that adds a row says
 * @param empty   what no rows at all means ("Sells nothing")
 * @param read    the text as rows (each row its columns' values, as text)
 * @param write   rows as the text
 */
public record Rows(List<Column> columns, String add, String empty, Function<String, List<List<String>>> read,
                   Function<List<List<String>>, String> write, String help) {

    public Rows(List<Column> columns, String add, String empty, Function<String, List<List<String>>> read,
                Function<List<List<String>>, String> write) {
        this(columns, add, empty, read, write, "");
    }

    /** What is in a column. */
    public enum Kind { CHOICE, NUMBER, TEXT }

    /**
     * One column of the rows.
     *
     * @param label    its heading
     * @param kind     what is in it
     * @param share    how much of a row's width it takes (the shares of a row add to 1)
     * @param fallback what a new row has in it
     * @param min      a number's least
     * @param max      a number's most
     * @param step     how much a number goes up or down a notch of the wheel
     * @param choices  a choice's options, given the object and the row (a weapon's tiers are its own)
     * @param shown    how an option or a number is shown ("0" as "its worth")
     */
    public record Column(String label, Kind kind, float share, String fallback, double min, double max, double step,
                         BiFunction<LevelObject, List<String>, List<String>> choices, Function<String, String> shown) {

        static Column choice(String label, float share, String fallback,
                             BiFunction<LevelObject, List<String>, List<String>> choices, Function<String, String> shown) {
            return new Column(label, Kind.CHOICE, share, fallback, 0, 0, 0, choices, shown);
        }

        static Column number(String label, float share, String fallback, double min, double max, double step,
                             Function<String, String> shown) {
            return new Column(label, Kind.NUMBER, share, fallback, min, max, step, null, shown);
        }

        static Column text(String label, float share, String fallback) {
            return new Column(label, Kind.TEXT, share, fallback, 0, 0, 0, null, s -> s);
        }

        /** What {@code value} looks like in it. */
        public String show(String value) {
            return shown == null ? value : shown.apply(value);
        }
    }

    /** A new row: each column's fallback. */
    public List<String> fresh() {
        List<String> r = new ArrayList<>();
        for (Column c : columns) r.add(c.fallback());
        return r;
    }

    // --- what a shop sells ---------------------------------------------------------------

    private static final Pattern PRICE = Pattern.compile("^(.*?)\\s*@\\s*(\\d+)$");
    private static final Pattern HOW_MANY = Pattern.compile("^(.*?)\\s*[x×]\\s*(\\d+)$");
    /** Her class's shop cards ("cards x3"). */
    public static final String CARDS = "cards";

    /**
     * A shop's stock: item, tier, how many (0: it never runs out), price (0:
     * what it is worth); {@code empty}: what selling nothing listed means.
     */
    public static Rows stock(String empty) {
        return new Rows(List.of(
                Column.choice("Item", 0.42f, "health_potion", (o, row) -> withCurrent(stockable(), row, 0),
                        Rows::itemLabel),
                Column.choice("Tier", 0.2f, "", (o, row) -> withCurrent(tiers(row.get(0)), row, 1), Rows::tierLabel),
                Column.number("How many", 0.18f, "0", 0, 999, 1, v -> num(v) <= 0 ? "-" : v),
                Column.number("Price", 0.2f, "0", 0, 100000, 5, v -> num(v) <= 0 ? "-" : v)),
                "+ Add something to sell", empty, Rows::readStock, Rows::writeStock,
                "How many: - never runs out. Price: - is what it is worth. Cards for her class are a few of "
                        + "the shop cards of whatever class she plays.");
    }

    static List<List<String>> readStock(String text) {
        List<List<String>> out = new ArrayList<>();
        if (text == null) return out;
        for (String part : text.split(",")) {
            String s = part.strip();
            if (s.isEmpty()) continue;
            String price = "0", count = "0";
            Matcher m = PRICE.matcher(s);
            if (m.matches()) {
                s = m.group(1).strip();
                price = m.group(2);
            }
            m = HOW_MANY.matcher(s);
            if (m.matches()) {
                s = m.group(1).strip();
                count = m.group(2);
            }
            String[] it = item(s);
            out.add(new ArrayList<>(List.of(it[0], it[1], count, price)));
        }
        return out;
    }

    static String writeStock(List<List<String>> rows) {
        StringBuilder s = new StringBuilder();
        for (List<String> r : rows) {
            if (r.get(0).isBlank()) continue;
            if (!s.isEmpty()) s.append(", ");
            s.append(key(r.get(0), r.get(1)));
            if (num(r.get(2)) > 0) s.append(" x").append((long) num(r.get(2)));
            if (num(r.get(3)) > 0) s.append(" @").append((long) num(r.get(3)));
        }
        return s.toString();
    }

    // --- what a quest pays ---------------------------------------------------------------

    /** A quest's pay: item (coins among them), tier, how many. */
    public static Rows reward() {
        return new Rows(List.of(
                Column.choice("Item", 0.52f, "coins", (o, row) -> withCurrent(payable(), row, 0), Rows::itemLabel),
                Column.choice("Tier", 0.24f, "", (o, row) -> withCurrent(tiers(row.get(0)), row, 1), Rows::tierLabel),
                Column.number("How many", 0.24f, "1", 1, 9999, 1, v -> v)),
                "+ Add to the pay", "Pays nothing but experience", Rows::readReward, Rows::writeReward);
    }

    static List<List<String>> readReward(String text) {
        List<List<String>> out = new ArrayList<>();
        if (text == null) return out;
        for (String part : text.split(",")) {
            String s = part.strip();
            if (s.isEmpty()) continue;
            String count = "1";
            Matcher m = HOW_MANY.matcher(s);
            if (m.matches()) {
                s = m.group(1).strip();
                count = m.group(2);
            }
            String[] it = item(s);
            out.add(new ArrayList<>(List.of(it[0], it[1], count)));
        }
        return out;
    }

    static String writeReward(List<List<String>> rows) {
        StringBuilder s = new StringBuilder();
        for (List<String> r : rows) {
            if (r.get(0).isBlank()) continue;
            if (!s.isEmpty()) s.append(", ");
            s.append(key(r.get(0), r.get(1)));
            long n = Math.max(1, (long) num(r.get(2)));
            if (n != 1) s.append(" x").append(n);
        }
        return s.toString();
    }

    // --- what someone can be asked -------------------------------------------------------

    /** What can be asked of someone: the topic, and her answer (| starts a new page). */
    public static Rows topics() {
        return new Rows(List.of(Column.text("Asked about", 0.36f, "Work"),
                Column.text("Answer (| new page)", 0.64f, "Ask at the smithy.")),
                "+ Add a topic", "Nothing to be asked about: she only greets", Rows::readTopics, Rows::writeTopics);
    }

    static List<List<String>> readTopics(String text) {
        List<List<String>> out = new ArrayList<>();
        if (text == null) return out;
        for (String part : text.split(";")) {
            String s = part.strip();
            if (s.isEmpty()) continue;
            int eq = s.indexOf('=');
            out.add(new ArrayList<>(eq < 0 ? List.of(s, "") : List.of(s.substring(0, eq).strip(),
                    s.substring(eq + 1).strip())));
        }
        return out;
    }

    static String writeTopics(List<List<String>> rows) {
        StringBuilder s = new StringBuilder();
        for (List<String> r : rows) {
            // (what would break the line up: a ";" or "=" in the words)
            String t = r.get(0).replace(';', ',').replace('=', '-').strip();
            String a = r.get(1).replace(';', ',').strip();
            if (t.isEmpty()) continue;
            if (!s.isEmpty()) s.append("; ");
            s.append(t).append('=').append(a);
        }
        return s.toString();
    }

    // --- someone's day -------------------------------------------------------------------

    /** Someone's day ({@link Schedule}): from what time, doing what, where. */
    public static Rows schedule() {
        return new Rows(List.of(
                Column.choice("From", 0.22f, "08:00", (o, row) -> withCurrent(times(), row, 0), s -> s),
                Column.choice("Does", 0.36f, Schedule.Doing.STAND.key(),
                        (o, row) -> withCurrent(Schedule.Doing.keys(), row, 1), Rows::doingLabel),
                Column.choice("Where", 0.42f, Schedule.POST, (o, row) -> withCurrent(ObjectTypes.dayPlaces(), row, 2),
                        ObjectTypes::dayPlaceLabel)),
                "+ Add to her day", "Stands at her post all day", Rows::readSchedule, Rows::writeSchedule,
                "Each holds from its time to the next. Work swings what she holds; Kneel, tend kneels at the "
                        + "spot; Keep watch looks about; To and fro walks from her post to the place and back; "
                        + "Go inside and Sleep take her in at the door, to be found inside. Places: her post, "
                        + "her home, a named spot, a building.");
    }

    static List<List<String>> readSchedule(String text) {
        List<List<String>> out = new ArrayList<>();
        for (Schedule.Entry e : Schedule.parse(text).entries()) {
            out.add(new ArrayList<>(List.of(e.time(), e.doing().key(), e.where())));
        }
        return out;
    }

    static String writeSchedule(List<List<String>> rows) {
        StringBuilder s = new StringBuilder();
        List<Schedule.Entry> entries = new ArrayList<>();
        for (List<String> r : rows) {
            int m = Schedule.minute(r.get(0));
            Schedule.Doing d = Schedule.Doing.byKey(r.get(1));
            if (m >= 0 && d != null) entries.add(new Schedule.Entry(m, d, r.get(2)));
        }
        String text = Schedule.of(entries).text();
        // (none at all: a day of standing at her post, not the one most keep - that is a blank line)
        return text.isEmpty() ? "00:00=stand@post" : text;
    }

    /** Every quarter of an hour of the day: "00:00" ... "23:45". */
    static List<String> times() {
        List<String> out = new ArrayList<>();
        for (int m = 0; m < 24 * 60; m += 15) out.add(Schedule.time(m));
        return out;
    }

    static String doingLabel(String key) {
        Schedule.Doing d = Schedule.Doing.byKey(key);
        return d == null ? key : d.label();
    }

    // --- the items ---------------------------------------------------------------------------

    /** What a shop can sell: every item there is, her class's cards, each card by itself. */
    static List<String> stockable() {
        List<String> out = new ArrayList<>(ObjectTypes.itemIds());
        out.add(CARDS);
        try {
            for (Card c : CardBook.get().cards()) out.add("card:" + c.id());
        } catch (RuntimeException e) {
            // (no cards to be had: the items alone)
        }
        return out;
    }

    /** What a quest can pay: coins first, then every item there is. */
    static List<String> payable() {
        List<String> out = new ArrayList<>();
        out.add("coins");
        for (String id : ObjectTypes.itemIds()) if (!id.equals("coins")) out.add(id);
        return out;
    }

    /** A weapon's tiers, or "" alone for what has none. */
    static List<String> tiers(String id) {
        Tier.Row row = id == null ? null : Tier.row(id);
        if (row == null) return List.of("");
        List<String> out = new ArrayList<>();
        for (Tier t : row.tiers()) out.add(t.key());
        return out;
    }

    /** {@code options}, with what row {@code row} has in column {@code col} among them (put last, if it is not). */
    static List<String> withCurrent(List<String> options, List<String> row, int col) {
        String cur = row == null || row.size() <= col ? null : row.get(col);
        if (cur == null || options.contains(cur)) return options;
        List<String> out = new ArrayList<>(options);
        out.add(cur);
        return out;
    }

    /**
     * {@code words} ("health potion", "sword:rare", "forged:my_blade 2",
     * "card:mage_fireball") as an item's id and its tier, or - not an item -
     * as written (and no tier).
     */
    static String[] item(String words) {
        String s = words.strip();
        String low = s.toLowerCase(Locale.ROOT);
        if (low.equals("card") || low.equals(CARDS)) return new String[]{CARDS, ""};
        if (low.startsWith("card:")) return new String[]{"card:" + low.substring(5).strip().replace(' ', '_'), ""};
        if (low.startsWith(Forged.PREFIX)) return new String[]{low.replace(' ', ':'), ""};
        Item it = Item.parse(low.replace(' ', '_'));
        if (it == null) it = Item.parse(low);
        if (it == null) return new String[]{s, ""};
        String key = it.key();
        int c = key.indexOf(':');
        String id = c < 0 ? key : key.substring(0, c), tier = c < 0 ? "" : key.substring(c + 1);
        // (a weapon written without its tier is in its lowest: said so)
        Tier.Row row = Tier.row(id);
        if (tier.isEmpty() && row != null) tier = row.lowest().key();
        return new String[]{id, tier};
    }

    /** An item's id and tier as one word: {@code sword:fine}. */
    static String key(String id, String tier) {
        return tier == null || tier.isBlank() ? id.strip() : id.strip() + ":" + tier.strip();
    }

    /** What {@code id} is called: "Health potion", "My blade (forged)", "Card: Fireball". */
    public static String itemLabel(String id) {
        if (id == null || id.isBlank()) return "(nothing)";
        if (id.equals(CARDS)) return "Cards for her class";
        if (id.startsWith("card:")) {
            Card c = null;
            try {
                c = CardBook.get().card(id.substring(5));
            } catch (RuntimeException ignored) {
                // (no cards: its id)
            }
            return "Card: " + (c == null ? ObjectTypes.readable(id.substring(5)) : c.name());
        }
        if (id.startsWith(Forged.PREFIX)) {
            String rest = id.substring(Forged.PREFIX.length());
            int c = rest.indexOf(':');
            Forged f = Forged.byId(c < 0 ? rest : rest.substring(0, c));
            return f == null ? id : f.name() + " (forged)";
        }
        ItemDef d = ObjectTypes.itemDef(id);
        return d == null ? id : d.name();
    }

    static String tierLabel(String key) {
        if (key == null || key.isBlank()) return "as it comes";
        Tier t = Tier.byKey(key);
        if (t != null) return t.label();
        return ObjectTypes.readable(key);
    }

    /** {@code v} as a number (0: not one). */
    public static double num(String v) {
        try {
            return Double.parseDouble(v.strip());
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
