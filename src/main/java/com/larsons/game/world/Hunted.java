package com.larsons.game.world;

import com.larsons.game.ambient.Hunting;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.fallback.FallbackSprites;

import java.util.List;

/**
 * What a hunted animal leaves where it falls ({@link Hunting#drops}): meat
 * to eat - raw, for now, giving back a little health (and cooking, with the
 * crafting to come) - and the ingredients for that crafting: hides, pelts,
 * wool, antlers and feathers, which are not eaten. All of them go in the
 * inventory in stacks, as the foraged pickings do ({@link Pickings}).
 *
 * <p>Each is drawn, lying about and in the inventory, as one of six turning
 * pickups (3D-Modeling's {@code items/item_spin_sprites.py}) painted its own
 * colour: a cut of meat, a drumstick, a folded hide, a feather, an antler, a
 * tuft of wool.
 */
public final class Hunted {

    /** How many of one go in an inventory slot. */
    public static final int STACK = 20;

    /** The pickups they are drawn as ({@code assets/sprites/items/<pickup>/}). */
    public static final String MEAT = "hunt_meat", DRUMSTICK = "hunt_drumstick", HIDE = "hunt_hide",
            PLUME = "hunt_feather", HORN = "hunt_antler", TUFT = "hunt_wool";

    public static final ItemDef RAW_BEEF = meat(Hunting.RAW_BEEF, "Raw beef", 12, 4, MEAT, 0xB8323A);
    public static final ItemDef RAW_VENISON = meat(Hunting.RAW_VENISON, "Raw venison", 14, 8, MEAT, 0x8C2430);
    public static final ItemDef RAW_PORK = meat(Hunting.RAW_PORK, "Raw pork", 12, 0, MEAT, 0xE07A80);
    public static final ItemDef RAW_MUTTON = meat(Hunting.RAW_MUTTON, "Raw mutton", 10, 4, MEAT, 0xC04850);
    public static final ItemDef BEAR_MEAT = meat(Hunting.BEAR_MEAT, "Bear meat", 18, 6, MEAT, 0x8A2A2A);
    public static final ItemDef RAW_GAME = meat(Hunting.RAW_GAME, "Raw game", 8, 4, DRUMSTICK, 0xA8404A);
    public static final ItemDef RAW_FOWL = meat(Hunting.RAW_FOWL, "Raw fowl", 8, 2, DRUMSTICK, 0xE8A898);

    public static final ItemDef COW_HIDE = part(Hunting.COW_HIDE, "Cow hide", "Hide", HIDE, "fur", 0xD8D0C8);
    public static final ItemDef DEER_HIDE = part(Hunting.DEER_HIDE, "Deer hide", "Hide", HIDE, "fur", 0xA0663A);
    public static final ItemDef BEAR_PELT = part(Hunting.BEAR_PELT, "Bear pelt", "Pelt", HIDE, "fur", 0x4A3020);
    public static final ItemDef FOX_PELT = part(Hunting.FOX_PELT, "Fox pelt", "Pelt", HIDE, "fur", 0xC0601A);
    public static final ItemDef RABBIT_PELT = part(Hunting.RABBIT_PELT, "Rabbit pelt", "Pelt", HIDE, "fur",
            0xA89070);
    public static final ItemDef WOOL = part(Hunting.WOOL, "Wool", "Wool", TUFT, "wool", 0xF0EEE8);
    public static final ItemDef ANTLER = part(Hunting.ANTLER, "Antler", "Antler", HORN, "antler", 0xDCC090);
    public static final ItemDef FEATHER = part(Hunting.FEATHER, "Feather", "Feather", PLUME, "vane",
            0xD8D0C0);
    public static final ItemDef EAGLE_FEATHER = part(Hunting.EAGLE_FEATHER, "Eagle feather", "Feather",
            PLUME, "vane", 0x6A3C18);
    public static final ItemDef PEACOCK_FEATHER = part(Hunting.PEACOCK_FEATHER, "Peacock feather", "Feather",
            PLUME, "vane", 0x2F8F7F);
    public static final ItemDef MACAW_FEATHER = part(Hunting.MACAW_FEATHER, "Macaw feather", "Feather",
            PLUME, "vane", 0xD82030);
    public static final ItemDef FLAMINGO_FEATHER = part(Hunting.FLAMINGO_FEATHER, "Flamingo feather", "Feather",
            PLUME, "vane", 0xF08AA8);

    public static final List<ItemDef> ALL = List.of(RAW_BEEF, RAW_VENISON, RAW_PORK, RAW_MUTTON, BEAR_MEAT,
            RAW_GAME, RAW_FOWL, COW_HIDE, DEER_HIDE, BEAR_PELT, FOX_PELT, RABBIT_PELT, WOOL, ANTLER, FEATHER,
            EAGLE_FEATHER, PEACOCK_FEATHER, MACAW_FEATHER, FLAMINGO_FEATHER);

    private Hunted() {}

    private static ItemDef meat(String id, String name, double health, double stamina, String pickup, int rgb) {
        Food f = new Food("Raw meat", health, stamina, 0, STACK, pickup, "meat", rgb);
        return new ItemDef(id, name, null, 0.4, () -> FallbackSprites.wearableIcon(rgb), Stance.SWORD, null, f);
    }

    /** An ingredient: stacked as food is, but not eaten (it gives back nothing). */
    private static ItemDef part(String id, String name, String kind, String pickup, String part, int rgb) {
        Food f = new Food(kind, 0, 0, 0, STACK, pickup, part, rgb);
        return new ItemDef(id, name, null, 0.4, () -> FallbackSprites.wearableIcon(rgb), Stance.SWORD, null, f);
    }

    /** The hunted item called {@code id}, or null. */
    public static ItemDef byId(String id) {
        for (ItemDef d : ALL) if (d.id().equals(id)) return d;
        return null;
    }
}
