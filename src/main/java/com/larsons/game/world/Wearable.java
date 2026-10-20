package com.larsons.game.world;

import com.larsons.game.sprite.Slot;

import java.util.List;

/**
 * How a wearable item is worn: which layers of the sprite stack it goes in,
 * which item folders it puts there, and how rare it is. What makes an {@link
 * ItemDef} a wearable rather than a weapon; the wearables themselves are
 * {@link Wearables#ALL}.
 *
 * <p>Most wearables are one folder in one slot (the belt is {@code
 * belt/leather_belt}). A wrist band can go on either wrist, a sheath on either
 * hip ({@code sword_left_hip}, {@code sword_right_hip}). The metal ones share
 * their art and differ in colour only: the silver and the gold wrist band are
 * both {@code gold_bangle}, worn in its {@code silver} or {@code gold} option
 * ({@link #colour}). A dye is worn nowhere: owning it unlocks its colour for
 * everything worn ({@link #dye}).
 *
 * @param rarity  how rare it is: what it drops as
 * @param slots   the layers it can be worn in; none for a dye or the lipstick
 * @param folders the item folders it puts there, under {@code assets/sprites/<slot>/}
 * @param colour  the colour it is always worn in (an option of its {@code variants.json}), or null
 * @param dye     the colour option a dye unlocks, or null
 * @param family  the family it drops as one of ({@link Wearables#DYE}, ...), or null
 */
public record Wearable(Rarity rarity, List<Slot> slots, List<String> folders, String colour, String dye,
                       String family) {

    public Wearable {
        slots = List.copyOf(slots);
        folders = List.copyOf(folders);
    }

    /** Whether it puts {@code folder} in {@code slot}. */
    public boolean puts(Slot slot, String folder) {
        return slots.contains(slot) && folders.contains(folder);
    }

    /** Whether {@code folder} worn in {@code slot} in {@code colour} is this: its folder there, in its colour if it has one. */
    public boolean wornAs(Slot slot, String folder, String colour) {
        return puts(slot, folder) && (this.colour == null || this.colour.equals(colour));
    }
}
