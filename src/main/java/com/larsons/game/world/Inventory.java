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
 * <p>A slot holds one item - or, of a picking from a foraging bush (berries,
 * herbs: {@link ItemDef#stack}), a <b>stack</b> of up to that many alike
 * ({@link #amount}): picking more of it fills its stacks before it takes a
 * new slot.
 *
 * <p>Iterating gives the items carried, in slot order, skipping the empty
 * slots (a stack once). The rest of the inventory can be sorted, by name or by rarity
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
    private int[] amounts;
    private int selected;

    /** An empty inventory of {@code size} slots (at least the hotbar's). */
    public Inventory(int size) {
        slots = new Item[Math.max(HOTBAR, size)];
        amounts = new int[slots.length];
    }

    /** How many slots there are, the hotbar's included. */
    public int size() { return slots.length; }

    /** The item in slot {@code i}, or null when it is empty (or there is no such slot). */
    public Item get(int i) {
        return i >= 0 && i < slots.length ? slots[i] : null;
    }

    /** Put {@code item} (null: nothing) in slot {@code i}, one of it; returns what was there. */
    public Item set(int i, Item item) {
        return set(i, item, 1);
    }

    /** Put {@code amount} of {@code item} (null: nothing) in slot {@code i}; returns what was there. */
    public Item set(int i, Item item, int amount) {
        Item was = slots[i];
        slots[i] = item;
        amounts[i] = item == null ? 0 : Math.max(1, Math.min(amount, item.def().stack()));
        return was;
    }

    /** How many are in slot {@code i}: a stack's size, 1 for anything else, 0 when it is empty. */
    public int amount(int i) {
        return i >= 0 && i < slots.length && slots[i] != null ? amounts[i] : 0;
    }

    /** How many of {@code item} are carried, every stack of it together. */
    public int total(Item item) {
        int n = 0;
        for (int i = 0; i < slots.length; i++) if (Objects.equals(slots[i], item)) n += amounts[i];
        return n;
    }

    /**
     * Take one from slot {@code i} - the slot is emptied when it was the last
     * of its stack - and return it, or null when the slot is empty.
     */
    public Item takeOne(int i) {
        Item item = get(i);
        if (item == null) return null;
        if (--amounts[i] <= 0) set(i, null);
        return item;
    }

    /**
     * Put {@code amount} of {@code item} onto the stack of it in slot {@code
     * i}, as many as it has room for; returns how many did not fit (all of
     * them when the slot holds something else).
     */
    public int merge(int i, Item item, int amount) {
        if (slots[i] == null || !slots[i].equals(item)) return amount;
        int room = item.def().stack() - amounts[i];
        int put = Math.max(0, Math.min(room, amount));
        amounts[i] += put;
        return amount - put;
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
     * Put {@code item} onto a stack of it with room (one of a picking), else
     * in the first empty slot - the hotbar first, then the rest - and return
     * that slot, or -1 when every slot is full.
     */
    public int add(Item item) {
        Objects.requireNonNull(item);
        int onto = stackWithRoom(item);
        if (onto >= 0) {
            amounts[onto]++;
            return onto;
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                set(i, item);
                return i;
            }
        }
        return -1;
    }

    /**
     * Put {@code item} onto a stack of it with room, else in the first empty
     * slot of the rest of the inventory, or in the hotbar's when that is
     * full - out of the way of what is in hand - and return that slot, or -1
     * when every slot is full.
     */
    public int stow(Item item) {
        Objects.requireNonNull(item);
        int onto = stackWithRoom(item);
        if (onto >= 0) {
            amounts[onto]++;
            return onto;
        }
        for (int i = HOTBAR; i < slots.length; i++) {
            if (slots[i] == null) {
                set(i, item);
                return i;
            }
        }
        return add(item);
    }

    /**
     * Put {@code amount} of {@code item} in: onto the stacks of it with room,
     * then into empty slots - the rest of the inventory's first when {@code
     * stowing}, out of the way of what is in hand - and return how many did
     * not fit.
     */
    public int put(Item item, int amount, boolean stowing) {
        Objects.requireNonNull(item);
        int left = amount;
        if (item.def().stack() > 1) {
            for (int i = 0; i < slots.length && left > 0; i++) if (slots[i] != null) left = merge(i, item, left);
        }
        int first = stowing ? HOTBAR : 0;
        for (int k = 0; k < slots.length && left > 0; k++) {
            int i = (first + k) % slots.length;
            if (slots[i] != null) continue;
            int n = Math.min(left, Math.max(1, item.def().stack()));
            set(i, item, n);
            left -= n;
        }
        return left;
    }

    /** Whether one more {@code item} would fit: on a stack of it, or in an empty slot. */
    public boolean hasRoomFor(Item item) {
        return stackWithRoom(item) >= 0 || count() < slots.length;
    }

    /** The first slot holding a stack of {@code item} with room on it, or -1 (and -1 for anything not stacked). */
    private int stackWithRoom(Item item) {
        if (item.def().stack() <= 1) return -1;
        for (int i = 0; i < slots.length; i++) {
            if (item.equals(slots[i]) && amounts[i] < item.def().stack()) return i;
        }
        return -1;
    }

    /**
     * Sort the rest of the inventory by {@code order}, packed into its first
     * slots and the empty ones after; the hotbar - what is to hand - is left
     * as it is.
     */
    public void sort(Order order) {
        List<Integer> rest = new ArrayList<>();
        for (int i = HOTBAR; i < slots.length; i++) if (slots[i] != null) rest.add(i);
        Comparator<Item> by = order.comparator();
        rest.sort((a, b) -> {
            int c = by.compare(slots[a], slots[b]);
            return c != 0 ? c : Integer.compare(amounts[b], amounts[a]);
        });
        Item[] items = new Item[rest.size()];
        int[] counts = new int[rest.size()];
        for (int k = 0; k < rest.size(); k++) {
            items[k] = slots[rest.get(k)];
            counts[k] = amounts[rest.get(k)];
        }
        for (int i = HOTBAR; i < slots.length; i++) {
            int k = i - HOTBAR;
            slots[i] = k < items.length ? items[k] : null;
            amounts[i] = k < items.length ? counts[k] : 0;
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
        set(i, null);
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
        int n = amounts[a];
        amounts[a] = amounts[b];
        amounts[b] = n;
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
        List<Integer> overflowAmounts = new ArrayList<>();
        for (int i = size; i < slots.length; i++) {
            if (slots[i] != null) {
                overflow.add(slots[i]);
                overflowAmounts.add(amounts[i]);
            }
        }
        Item[] next = Arrays.copyOf(slots, size);
        int[] nextAmounts = Arrays.copyOf(amounts, size);
        List<Item> spilled = new ArrayList<>();
        for (int k = 0; k < overflow.size(); k++) {
            boolean placed = false;
            for (int i = HOTBAR; i < size; i++) {
                if (next[i] == null) {
                    next[i] = overflow.get(k);
                    nextAmounts[i] = overflowAmounts.get(k);
                    placed = true;
                    break;
                }
            }
            if (!placed) for (int n = 0; n < overflowAmounts.get(k); n++) spilled.add(overflow.get(k));
        }
        slots = next;
        amounts = nextAmounts;
        return spilled;
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
