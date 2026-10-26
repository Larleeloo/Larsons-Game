package com.larsons.game.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * What a character carries: a row of slots, each empty or holding one
 * {@link Item}, of any length the game asks for - and the
 * first {@value #HOTBAR} of them are the <b>hotbar</b>, one of which is
 * selected: the item in hand.
 *
 * <pre>
 *   slot  0 1 2 3 4 | 5 6 7 ...                  size()-1
 *         hotbar    | the rest of the inventory (opened with I)
 * </pre>
 *
 * <p>The number of slots is the inventory's own ({@link #Inventory(int)}) and
 * can change while it is in use ({@link #resize}) - a bigger bag, a chest's
 * contents - without the hotbar moving: the screen that shows it lays out
 * however many there are ({@code ui/InventoryPanel}).
 *
 * <p>Iterating gives the items carried, in slot order, skipping the empty
 * slots. The rest of the inventory can be sorted, by name or by rarity
 * ({@link #sort}); the hotbar stays as it is.
 */
public final class Inventory implements Iterable<Item> {

    /** How {@link #sort} orders the rest of the inventory. */
    public enum Order {
        /** A to Z by name ("Basic Axe" before "Belt"); the rarest first where two are named alike. */
        NAME,
        /** The rarest first - Mythic down to Common - and by name within a rarity. */
        RARITY;

        private static final Comparator<Item> BY_NAME =
                Comparator.comparing((Item i) -> i.fullName().toLowerCase(Locale.ROOT));
        private static final Comparator<Item> RAREST_FIRST =
                Comparator.comparing(Item::rarity, Comparator.reverseOrder());

        /** The order as a comparison of two items (ties broken by {@link Item#key()}, so it is total). */
        public Comparator<Item> comparator() {
            Comparator<Item> c = this == NAME ? BY_NAME.thenComparing(RAREST_FIRST) : RAREST_FIRST.thenComparing(BY_NAME);
            return c.thenComparing(Item::key);
        }
    }

    /** Slots in the hotbar: chosen with 1 – 5 or the mouse wheel. */
    public static final int HOTBAR = 5;
    /** The player's slots unless the game says otherwise: the hotbar and four rows of five. */
    public static final int DEFAULT_SLOTS = 25;

    private Item[] slots;
    private int selected;

    /** An empty inventory of {@code size} slots (at least the hotbar's). */
    public Inventory(int size) {
        slots = new Item[Math.max(HOTBAR, size)];
    }

    /** How many slots there are, the hotbar's included. */
    public int size() { return slots.length; }

    /** The item in slot {@code i}, or null when it is empty (or there is no such slot). */
    public Item get(int i) {
        return i >= 0 && i < slots.length ? slots[i] : null;
    }

    /** Put {@code item} (null: nothing) in slot {@code i}; returns what was there. */
    public Item set(int i, Item item) {
        Item was = slots[i];
        slots[i] = item;
        return was;
    }

    public static boolean isHotbar(int i) {
        return i >= 0 && i < HOTBAR;
    }

    // --- the hotbar ------------------------------------------------------------------

    /** The selected hotbar slot, 0 – 4. */
    public int selected() { return selected; }

    /** Select hotbar slot {@code i} (clamped into the hotbar). */
    public void select(int i) {
        selected = Math.max(0, Math.min(HOTBAR - 1, i));
    }

    /**
     * Move the selection by {@code steps} slots along the hotbar, wrapping
     * round: the mouse wheel. Positive moves right.
     */
    public void scroll(int steps) {
        selected = Math.floorMod(selected + steps, HOTBAR);
    }

    /** The item in the selected hotbar slot - the one in hand - or null. */
    public Item held() {
        return slots[selected];
    }

    // --- adding and taking -----------------------------------------------------------

    /**
     * Put {@code item} in the first empty slot - the hotbar first, then the
     * rest - and return that slot, or -1 when every slot is full.
     */
    public int add(Item item) {
        Objects.requireNonNull(item);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                slots[i] = item;
                return i;
            }
        }
        return -1;
    }

    /**
     * Put {@code item} in the first empty slot of the rest of the inventory,
     * or in the hotbar's when that is full - out of the way of what is in
     * hand - and return that slot, or -1 when every slot is full.
     */
    public int stow(Item item) {
        Objects.requireNonNull(item);
        for (int i = HOTBAR; i < slots.length; i++) {
            if (slots[i] == null) {
                slots[i] = item;
                return i;
            }
        }
        return add(item);
    }

    /**
     * Sort the rest of the inventory by {@code order}, packed into its first
     * slots and the empty ones after; the hotbar - what is to hand - is left
     * as it is.
     */
    public void sort(Order order) {
        List<Item> rest = new ArrayList<>();
        for (int i = HOTBAR; i < slots.length; i++) if (slots[i] != null) rest.add(slots[i]);
        rest.sort(order.comparator());
        for (int i = HOTBAR; i < slots.length; i++) {
            int k = i - HOTBAR;
            slots[i] = k < rest.size() ? rest.get(k) : null;
        }
    }

    /** The first slot holding {@code item}, or -1. */
    public int indexOf(Item item) {
        for (int i = 0; i < slots.length; i++) if (Objects.equals(slots[i], item)) return i;
        return -1;
    }

    /** The first slot holding an item of kind {@code def} (in any tier), or -1. */
    public int indexOf(ItemDef def) {
        for (int i = 0; i < slots.length; i++) if (slots[i] != null && slots[i].def() == def) return i;
        return -1;
    }

    public boolean contains(Item item) {
        return item != null && indexOf(item) >= 0;
    }

    /** Whether an item of kind {@code def} is carried, in any tier. */
    public boolean contains(ItemDef def) {
        return def != null && indexOf(def) >= 0;
    }

    /** Take {@code item} out of the first slot holding it; whether it was there. */
    public boolean remove(Item item) {
        int i = indexOf(item);
        if (i < 0 || item == null) return false;
        slots[i] = null;
        return true;
    }

    /** Empty slot {@code i}, returning what was in it. */
    public Item take(int i) {
        return set(i, null);
    }

    /** Swap two slots' contents (moving an item onto an empty slot moves it). */
    public void swap(int a, int b) {
        Item t = slots[a];
        slots[a] = slots[b];
        slots[b] = t;
    }

    /** How many items are carried. */
    public int count() {
        int n = 0;
        for (Item s : slots) if (s != null) n++;
        return n;
    }

    public boolean isFull() {
        return count() == slots.length;
    }

    /**
     * Change the number of slots. Growing adds empty slots at the end;
     * shrinking packs what was in the slots that go into the empty ones that
     * stay, and returns what still does not fit (nothing is lost silently).
     * The hotbar always stays.
     */
    public List<Item> resize(int size) {
        size = Math.max(HOTBAR, size);
        List<Item> overflow = new ArrayList<>();
        for (int i = size; i < slots.length; i++) if (slots[i] != null) overflow.add(slots[i]);
        Item[] next = Arrays.copyOf(slots, size);
        for (Iterator<Item> it = overflow.iterator(); it.hasNext(); ) {
            Item item = it.next();
            for (int i = HOTBAR; i < size; i++) {
                if (next[i] == null) {
                    next[i] = item;
                    it.remove();
                    break;
                }
            }
        }
        slots = next;
        return overflow;
    }

    /** The items carried, in slot order. */
    public List<Item> items() {
        List<Item> out = new ArrayList<>();
        for (Item s : slots) if (s != null) out.add(s);
        return out;
    }

    @Override
    public Iterator<Item> iterator() {
        return items().iterator();
    }
}
