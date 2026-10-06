package com.larsons.game.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * What a character carries: a row of slots, each empty or holding one item
 * (its {@link ItemDef#id()}), of any length the game asks for - and the
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
 * slots.
 */
public final class Inventory implements Iterable<String> {

    /** Slots in the hotbar: chosen with 1 – 5 or the mouse wheel. */
    public static final int HOTBAR = 5;
    /** The player's slots unless the game says otherwise: the hotbar and four rows of five. */
    public static final int DEFAULT_SLOTS = 25;

    private String[] slots;
    private int selected;

    /** An empty inventory of {@code size} slots (at least the hotbar's). */
    public Inventory(int size) {
        slots = new String[Math.max(HOTBAR, size)];
    }

    /** How many slots there are, the hotbar's included. */
    public int size() { return slots.length; }

    /** The item in slot {@code i}, or null when it is empty (or there is no such slot). */
    public String get(int i) {
        return i >= 0 && i < slots.length ? slots[i] : null;
    }

    /** Put {@code item} (null: nothing) in slot {@code i}; returns what was there. */
    public String set(int i, String item) {
        String was = slots[i];
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
    public String held() {
        return slots[selected];
    }

    // --- adding and taking -----------------------------------------------------------

    /**
     * Put {@code item} in the first empty slot - the hotbar first, then the
     * rest - and return that slot, or -1 when every slot is full.
     */
    public int add(String item) {
        Objects.requireNonNull(item);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                slots[i] = item;
                return i;
            }
        }
        return -1;
    }

    /** The first slot holding {@code item}, or -1. */
    public int indexOf(String item) {
        for (int i = 0; i < slots.length; i++) if (Objects.equals(slots[i], item)) return i;
        return -1;
    }

    public boolean contains(String item) {
        return item != null && indexOf(item) >= 0;
    }

    /** Take {@code item} out of the first slot holding it; whether it was there. */
    public boolean remove(String item) {
        int i = indexOf(item);
        if (i < 0 || item == null) return false;
        slots[i] = null;
        return true;
    }

    /** Empty slot {@code i}, returning what was in it. */
    public String take(int i) {
        return set(i, null);
    }

    /** Swap two slots' contents (moving an item onto an empty slot moves it). */
    public void swap(int a, int b) {
        String t = slots[a];
        slots[a] = slots[b];
        slots[b] = t;
    }

    /** How many items are carried. */
    public int count() {
        int n = 0;
        for (String s : slots) if (s != null) n++;
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
    public List<String> resize(int size) {
        size = Math.max(HOTBAR, size);
        List<String> overflow = new ArrayList<>();
        for (int i = size; i < slots.length; i++) if (slots[i] != null) overflow.add(slots[i]);
        String[] next = Arrays.copyOf(slots, size);
        for (Iterator<String> it = overflow.iterator(); it.hasNext(); ) {
            String item = it.next();
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
    public List<String> items() {
        List<String> out = new ArrayList<>();
        for (String s : slots) if (s != null) out.add(s);
        return out;
    }

    @Override
    public Iterator<String> iterator() {
        return items().iterator();
    }
}
