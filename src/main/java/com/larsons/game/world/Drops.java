package com.larsons.game.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A loot pool of the level's own, set in Creative Mode: what one placed foe
 * or person leaves when felled, what a herd's animals leave when hunted,
 * what a loot chest throws out. Each line is an item, how many of it (from -
 * to) and the chance it comes at all; every line is rolled by itself, so a
 * pool of "1 to 5 coins, always" and "a dye, one time in four" gives coins
 * every time and a dye now and then.
 *
 * <p>Kept as one line of text ({@code drops}): {@code coins x1-5, any_dye
 * 25%, sword:rare 10%}. A line with no count gives one; with no chance it
 * always comes. Two of its items are not items but draws: {@value #ANY_DYE}
 * (one of the dyes, as a chest's is drawn) and {@value #ANY_LOOT} (one of
 * the chests' drops, its rarity rolled). Nothing written: the thing gives
 * what it always has.
 */
public final class Drops {

    /** A dye, drawn as a chest's are ({@link Wearables#randomDye}). */
    public static final String ANY_DYE = "any_dye";
    /** One of the chests' drops - a weapon at some tier, a wearable, a dye - its rarity rolled. */
    public static final String ANY_LOOT = "any_loot";
    /** The most of one line. */
    public static final int MOST = 999;

    /** One line: {@code item} ("coins", "sword:rare", {@link #ANY_DYE}), {@code min} to {@code max} of it, {@code chance} percent. */
    public record Line(String item, int min, int max, double chance) {

        public Line {
            min = Math.max(1, Math.min(MOST, min));
            max = Math.max(min, Math.min(MOST, max));
            chance = Math.max(0, Math.min(100, chance));
        }

        /** As it is written: {@code coins x1-5 25%}. */
        public String text() {
            StringBuilder s = new StringBuilder(item.strip());
            if (min != 1 || max != 1) s.append(" x").append(min).append(max != min ? "-" + max : "");
            if (chance < 100) s.append(' ').append(chance == Math.rint(chance) ? String.valueOf((long) chance)
                    : String.format(Locale.ROOT, "%.1f", chance)).append('%');
            return s.toString();
        }
    }

    /** One thing it gave: the item and how many of it. */
    public record Drop(Item item, int amount) {}

    private static final Pattern CHANCE = Pattern.compile("^(.*?)\\s+(\\d+(?:\\.\\d+)?)\\s*%$");
    private static final Pattern COUNT = Pattern.compile("^(.*?)\\s*[x×]\\s*(\\d+)(?:\\s*-\\s*(\\d+))?$");

    private final List<Line> lines;

    private Drops(List<Line> lines) {
        this.lines = List.copyOf(lines);
    }

    /** No lines: the thing gives what it always has. */
    public static final Drops NONE = new Drops(List.of());

    /** Lines as given. */
    public static Drops of(List<Line> lines) {
        return lines == null || lines.isEmpty() ? NONE : new Drops(lines);
    }

    /** {@code text} ("coins x1-5, any_dye 25%") read; what it cannot read is left out. */
    public static Drops parse(String text) {
        if (text == null || text.isBlank()) return NONE;
        List<Line> out = new ArrayList<>();
        for (String part : text.split(",")) {
            Line l = line(part);
            if (l != null) out.add(l);
        }
        return of(out);
    }

    /** One line read, or null. */
    static Line line(String part) {
        String s = part == null ? "" : part.strip().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        double chance = 100;
        Matcher m = CHANCE.matcher(s);
        if (m.matches()) {
            s = m.group(1).strip();
            chance = Double.parseDouble(m.group(2));
        }
        int min = 1, max = 1;
        m = COUNT.matcher(s);
        if (m.matches()) {
            s = m.group(1).strip();
            min = (int) Math.min(MOST, Long.parseLong(m.group(2)));
            max = m.group(3) == null ? min : (int) Math.min(MOST, Long.parseLong(m.group(3)));
        }
        if (s.isEmpty()) return null;
        return new Line(s.replace(' ', '_'), min, max, chance);
    }

    /** As it is kept: its lines' {@link Line#text}, a comma between. */
    public String text() {
        StringBuilder s = new StringBuilder();
        for (Line l : lines) {
            if (!s.isEmpty()) s.append(", ");
            s.append(l.text());
        }
        return s.toString();
    }

    public List<Line> lines() { return Collections.unmodifiableList(lines); }

    public boolean isEmpty() { return lines.isEmpty(); }

    /**
     * What it gives this time: each line by its chance, between its from and
     * to of it. {@code loot} is what {@value #ANY_LOOT} draws from (a chest's
     * drops); a line whose item is not known gives nothing.
     */
    public List<Drop> roll(Random random, Loot loot) {
        List<Drop> out = new ArrayList<>();
        for (Line l : lines) {
            if (l.chance() < 100 && random.nextDouble() * 100 >= l.chance()) continue;
            int n = l.min() + (l.max() > l.min() ? random.nextInt(l.max() - l.min() + 1) : 0);
            if (n <= 0) continue;
            if (l.item().equals(ANY_DYE) || l.item().equals(ANY_LOOT)) {
                // a draw: each one drawn by itself (two dyes may be two colours)
                for (int i = 0; i < n; i++) {
                    Item it = draw(l.item(), random, loot);
                    if (it != null) out.add(new Drop(it, 1));
                }
                continue;
            }
            Item it = item(l.item());
            if (it != null) out.add(new Drop(it, n));
        }
        return out;
    }

    private static Item draw(String what, Random random, Loot loot) {
        if (what.equals(ANY_DYE)) {
            ItemDef dye = Wearables.randomDye(random);
            return dye == null ? null : Item.of(dye);
        }
        if (loot == null) return null;
        Item drop = loot.pick(random, Rarity.roll(random));
        return drop == null ? null : drop.withBorder(Border.roll(random));
    }

    /**
     * Whether {@code item} lies as one heap of many - coins, ammunition,
     * berries and meat - where a weapon or a piece to wear lies one by one.
     */
    public static boolean heaps(Item item) {
        ItemDef d = item.def();
        return d.stack() > 1 || d == Supplies.COINS || Supplies.isAmmo(d);
    }

    /** The item a line names ("coins", "sword:rare", "forged:my_blade"), or null. */
    public static Item item(String key) {
        if (key == null || key.isBlank()) return null;
        Item it = Item.parse(key.strip().replace(' ', '_'));
        return it != null ? it : Item.parse(key.strip());
    }
}
