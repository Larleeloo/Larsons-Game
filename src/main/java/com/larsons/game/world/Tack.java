package com.larsons.game.world;

import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.fallback.FallbackSprites;

import java.util.List;

/**
 * What a horse wears to be ridden: the saddle - a red pad, the leather seat,
 * the stirrups. Neither held nor worn nor eaten: it lies in the inventory
 * (out of the hotbar's way, as a wearable does) until she saddles a horse
 * with it ({@link World#saddle}), and is found in the chests.
 */
public final class Tack {

    /** Its pad's red: the colour its turning sprite's pad is drawn in ({@link Wearables#pickup}). */
    public static final int SADDLE_RGB = 0x9C1E1E;

    public static final ItemDef SADDLE = new ItemDef("saddle", "Saddle", null, 0.8,
            () -> FallbackSprites.wearableIcon(SADDLE_RGB), Stance.SWORD, null, null);

    public static final List<ItemDef> ALL = List.of(SADDLE);

    private Tack() {}

    /** Whether {@code d} is tack. */
    public static boolean is(ItemDef d) {
        for (ItemDef t : ALL) if (t == d || t.id().equals(d.id())) return true;
        return false;
    }

    /** The tack called {@code id}, or null. */
    public static ItemDef byId(String id) {
        for (ItemDef d : ALL) if (d.id().equals(id)) return d;
        return null;
    }

    /** Add the tack to {@code loot}: the saddle, Uncommon. */
    public static Loot addTo(Loot loot) {
        loot.add(Item.of(SADDLE, Rarity.UNCOMMON));
        return loot;
    }
}
