package com.larsons.game.world;

import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.sprite.fallback.FallbackSprites;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything that can be worn, as items: clothes, armour, jewellery, the
 * dyes, makeup, lipstick, the hair styles and the eyebrows. The wardrobe
 * offers what the player owns - what is in her inventory, picked up or out of
 * a chest, and the underwear, the bra and a starter look (two hair styles,
 * the long waves and the short side part, and the arched eyebrows), which
 * are hers from the start ({@link #OWNED_BY_DEFAULT}).
 *
 * <pre>
 *   Common    underwear, bra (owned by default), shoes, pants, baggy shirt,
 *             straw sun hat, beanie
 *   Uncommon  belt, gloves, sheath, laced shirt, wool mantle,
 *             chainmail shirt, chainmail pants
 *   Rare      laced dress, silver wrist band, silver necklace, silver hoops,
 *             the 14 dyes, makeup (noir, rose, plum), lipstick, the hair
 *             styles, the eyebrows (long waves, side part and arched owned
 *             by default),
 *             the iron armour
 *   Epic      gold wrist band, gold necklace, gold hoops, the steel armour
 * </pre>
 *
 * <p>What is not an item stays free, every folder found for the body: the
 * body itself and the skin tone, the ears, the eyes and their colour, the
 * nose and the mouth ({@link #FREE}). The carried slots are the weapons'.
 *
 * <p>A colour is unlocked with a dye: every item with a colour choice in its
 * {@code variants.json} - clothes, hair, makeup, the lips - offers the 14
 * dyes' colours, and a colour she has not unlocked for that item is put on
 * by using up a dye of it ({@link #useDye}): one red dye makes one shirt or
 * one hair style red for good ({@link Wardrobe#unlocked}). The lipstick
 * unlocks every colour of the lips, and a metal wearable has only its own
 * ({@link #colours}). Lose a wearable and what it gave is taken off
 * ({@link #takeOff}).
 *
 * <p>Lying in the world and in the inventory a wearable is not drawn as
 * itself but as one of three pickups coloured for it ({@link #pickup}): a
 * t-shirt on a coat hanger in its rarity's colour, a shield in it for
 * armour, a bottle of the dye for a dye.
 */
public final class Wearables {

    /** The dyes' colours: the options every item's {@code variants.json} shares for its own colour. */
    public static final List<String> DYE_COLOURS = List.of("brown", "black", "auburn", "copper", "honey_blonde",
            "platinum", "cherry_red", "pink", "electric_blue", "violet_ombre", "emerald", "mint", "green_ombre",
            "royal_blue");
    /**
     * Each dye's colour in its bottle (RGB): the mid shade a shirt takes in
     * it ({@code shirt/plaid_flannel}'s {@code variants.json}), one of the
     * 128 colours.
     */
    public static final Map<String, Integer> DYE_RGB = dyeRgb();
    /** The hair styles: every folder under {@code assets/sprites/hair/}. */
    public static final List<String> HAIR_STYLES = List.of("long_waves", "bob", "ponytail", "pixie", "braids",
            "curls", "crew_cut", "side_part", "undercut", "beanie_long", "beanie_short");

    /**
     * The families that drop as one ({@link Loot#add(List)}): so many dyes and
     * hair styles would otherwise drown out everything else of their rarity.
     */
    public static final String DYE = "dye", HAIR_STYLE = "hair_style", EYEBROWS = "eyebrows", MAKEUP = "makeup";

    /** The layers that are not items: every folder found for the body is offered, colours and all. */
    public static final Set<Slot> FREE = Collections.unmodifiableSet(
            EnumSet.of(Slot.BODY, Slot.EARS, Slot.EYES, Slot.NOSE, Slot.MOUTH));

    public static final ItemDef UNDERWEAR = worn("underwear", "Underwear", Rarity.COMMON, Slot.UNDERWEAR, "cotton_briefs");
    public static final ItemDef BRA = worn("bra", "Bra", Rarity.COMMON, Slot.BRA, "leather_bralette");
    /** Unlocks every colour of the lips (the mouth's colour). */
    public static final ItemDef LIPSTICK = def("lipstick", "Lipstick",
            new Wearable(Rarity.RARE, List.of(), List.of(), null, null, null));

    /** The starter look: a long and a short hair style, and the eyebrows every character has. */
    public static final ItemDef LONG_WAVES = hair("long_waves"), SIDE_PART = hair("side_part"),
            ARCHED_EYEBROWS = eyebrows("arched");

    /**
     * What every character owns without picking anything up: the underwear,
     * the bra and the starter look - the long waves or the short side part,
     * and the arched eyebrows. The other hair styles and eyebrows are Rare
     * finds.
     */
    public static final List<ItemDef> OWNED_BY_DEFAULT = List.of(UNDERWEAR, BRA, LONG_WAVES, SIDE_PART,
            ARCHED_EYEBROWS);

    /** The pickups a wearable is drawn as (3D-Modeling's {@code items/item_spin_sprites.py}): their folders under {@code items/}. */
    public static final String HANGER = "wearable_hanger", SHIELD = "armour_shield", BOTTLE = "dye_bottle";

    /**
     * How a wearable lies in the world and sits in the inventory: the turning
     * sprite of one of the pickups ({@link #HANGER}, {@link #SHIELD}, {@link
     * #BOTTLE}), its {@code part} in {@code rgb} - the shirt's cloth or the
     * shield's face in the rarity's colour, the bottle's liquid in the dye's.
     */
    public record Pickup(String spin, String part, int rgb) {}

    /** Every wearable to choose from and every colour unlocked: the debug code's switch ({@link #setEverything}). */
    private static volatile boolean everything;

    /** Every wearable, by rarity and then as the table lists them. */
    public static final List<ItemDef> ALL = all();

    private Wearables() {}

    private static List<ItemDef> all() {
        List<ItemDef> out = new ArrayList<>();
        // Common
        out.add(UNDERWEAR);
        out.add(BRA);
        out.add(worn("shoes", "Shoes", Rarity.COMMON, Slot.SHOES, "leather_ankle_boots"));
        out.add(worn("pants", "Pants", Rarity.COMMON, Slot.PANTS, "denim_jeans"));
        out.add(worn("baggy_shirt", "Baggy shirt", Rarity.COMMON, Slot.SHIRT, "plaid_flannel"));
        out.add(worn("straw_sun_hat", "Straw sun hat", Rarity.COMMON, Slot.HAT, "straw_sun_hat"));
        out.add(worn("beanie", "Beanie", Rarity.COMMON, Slot.HAT, "beanie"));
        // Uncommon
        out.add(worn("belt", "Belt", Rarity.UNCOMMON, Slot.BELT, "leather_belt"));
        out.add(worn("gloves", "Gloves", Rarity.UNCOMMON, Slot.GLOVES, "black_leather"));
        out.add(def("sheath", "Sheath", new Wearable(Rarity.UNCOMMON, List.of(Slot.SHEATH),
                List.of("sword_left_hip", "sword_right_hip"), null, null, null)));
        out.add(worn("laced_shirt", "Laced shirt", Rarity.UNCOMMON, Slot.SHIRT, "laced_shirt"));
        out.add(worn("wool_mantle", "Wool mantle", Rarity.UNCOMMON, Slot.OTHER, "wool_mantle"));
        out.add(worn("chainmail_shirt", "Chainmail shirt", Rarity.UNCOMMON, Slot.CHAINMAIL, "chainmail_shirt"));
        out.add(worn("chainmail_pants", "Chainmail pants", Rarity.UNCOMMON, Slot.CHAINMAIL_LEGS, "chainmail_pants"));
        // Rare
        out.add(worn("laced_dress", "Laced dress", Rarity.RARE, Slot.SHIRT, "laced_dress"));
        out.add(metal("silver_wrist_band", "Silver wrist band", Rarity.RARE, "silver", "gold_bangle",
                Slot.WRISTWEAR, Slot.WRISTWEAR_LEFT));
        out.add(metal("silver_necklace", "Silver necklace", Rarity.RARE, "silver", "gold_pendant", Slot.NECKLACE));
        out.add(metal("silver_hoops", "Silver hoops", Rarity.RARE, "silver", "gold_hoops", Slot.EARRINGS));
        for (String c : DYE_COLOURS) {
            out.add(def("dye_" + c, readable(c) + " dye", new Wearable(Rarity.RARE, List.of(), List.of(), null, c, DYE)));
        }
        for (String look : List.of("noir", "rose", "plum")) {
            out.add(def("makeup_" + look, readable(look) + " makeup",
                    new Wearable(Rarity.RARE, List.of(Slot.MAKEUP), List.of(look), null, null, MAKEUP)));
        }
        out.add(LIPSTICK);
        for (String style : HAIR_STYLES) {
            out.add(style.equals("long_waves") ? LONG_WAVES : style.equals("side_part") ? SIDE_PART : hair(style));
        }
        for (String brows : List.of("arched", "soft", "thin")) {
            out.add(brows.equals("arched") ? ARCHED_EYEBROWS : eyebrows(brows));
        }
        armour(out, "iron", Rarity.RARE);
        // Epic
        out.add(metal("gold_wrist_band", "Gold wrist band", Rarity.EPIC, "gold", "gold_bangle",
                Slot.WRISTWEAR, Slot.WRISTWEAR_LEFT));
        out.add(metal("gold_necklace", "Gold necklace", Rarity.EPIC, "gold", "gold_pendant", Slot.NECKLACE));
        out.add(metal("gold_hoops", "Gold hoops", Rarity.EPIC, "gold", "gold_hoops", Slot.EARRINGS));
        armour(out, "steel", Rarity.EPIC);
        return List.copyOf(out);
    }

    /** A metal's chestplate, leg plating, gauntlets and helmet (the chainmail is apart: Uncommon). */
    private static void armour(List<ItemDef> out, String metal, Rarity rarity) {
        String m = readable(metal);
        out.add(worn(metal + "_chestplate", m + " chestplate", rarity, Slot.CHESTPLATE, metal + "_chestplate"));
        out.add(worn(metal + "_leg_plating", m + " leg plating", rarity, Slot.LEG_PLATING, metal + "_leg_plating"));
        out.add(worn(metal + "_gauntlets", m + " gauntlets", rarity, Slot.GLOVES, metal + "_gauntlets"));
        out.add(worn(metal + "_helmet", m + " helmet", rarity, Slot.HAT, metal + "_helmet"));
    }

    private static ItemDef hair(String style) {
        return def("hair_" + style, readable(style) + " hairstyle",
                new Wearable(Rarity.RARE, List.of(Slot.HAIR), List.of(style), null, null, HAIR_STYLE));
    }

    private static ItemDef eyebrows(String brows) {
        return def("eyebrows_" + brows, readable(brows) + " eyebrows",
                new Wearable(Rarity.RARE, List.of(Slot.EYEBROWS), List.of(brows), null, null, EYEBROWS));
    }

    private static ItemDef worn(String id, String name, Rarity rarity, Slot slot, String folder) {
        return def(id, name, new Wearable(rarity, List.of(slot), List.of(folder), null, null, null));
    }

    private static ItemDef metal(String id, String name, Rarity rarity, String colour, String folder, Slot... slots) {
        return def(id, name, new Wearable(rarity, List.of(slots), List.of(folder), colour, null, null));
    }

    private static ItemDef def(String id, String name, Wearable w) {
        return new ItemDef(id, name, null, 0.8, () -> FallbackSprites.wearableIcon(w.rarity().rgb()),
                Stance.SWORD, w);
    }

    private static Map<String, Integer> dyeRgb() {
        int[] rgb = {0x7c3c24, 0x231c18, 0xa44f08, 0xd9740d, 0xfab33e, 0xfeedc7, 0xca1525, 0xfd81a5, 0x0b61c5,
                0xb722e7, 0x0d9e44, 0xc3fec0, 0x7ee728, 0x4b52f4};
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < rgb.length; i++) out.put(DYE_COLOURS.get(i), rgb[i]);
        return Collections.unmodifiableMap(out);
    }

    /** "honey_blonde" -> "Honey blonde". */
    static String readable(String key) {
        String s = key.replace('_', ' ');
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    public static ItemDef byId(String id) {
        for (ItemDef d : ALL) if (d.id().equals(id)) return d;
        return null;
    }

    /** The dye of colour option {@code colour}, or null when there is none. */
    public static ItemDef dye(String colour) {
        ItemDef d = byId("dye_" + colour);
        return d != null && colour.equals(d.wearable().dye()) ? d : null;
    }

    /** Whether {@code d} is armour: chainmail, plate, gauntlets or a helmet (drawn as the shield). */
    public static boolean armour(ItemDef d) {
        if (d == null || !d.isWearable()) return false;
        for (Slot s : d.wearable().slots()) {
            if (s == Slot.CHAINMAIL || s == Slot.CHAINMAIL_LEGS || s == Slot.CHESTPLATE || s == Slot.LEG_PLATING) {
                return true;
            }
        }
        for (String f : d.wearable().folders()) {
            if (Wardrobe.GAUNTLETS.contains(f) || f.endsWith("_helmet")) return true;
        }
        return false;
    }

    /**
     * What {@code item} is drawn as lying about and in the inventory: a dye
     * as a bottle of it, armour as a shield in its rarity's colour, anything
     * else worn as a t-shirt on a hanger in it; null for a weapon (drawn as
     * itself).
     */
    public static Pickup pickup(Item item) {
        ItemDef d = item.def();
        if (!d.isWearable()) return null;
        String dye = d.wearable().dye();
        if (dye != null) return new Pickup(BOTTLE, "liquid", DYE_RGB.getOrDefault(dye, item.rarity().rgb()));
        return new Pickup(armour(d) ? SHIELD : HANGER, armour(d) ? "face" : "cloth", item.rarity().rgb());
    }

    // --- the debug code ---------------------------------------------------------------

    /**
     * Offer every wearable in the wardrobe and unlock every colour, whatever
     * she owns (on), or only what she owns (off): the debug code's switch.
     */
    public static void setEverything(boolean on) {
        everything = on;
    }

    /** Whether every wearable and every colour is offered ({@link #setEverything}). */
    public static boolean everything() {
        return everything;
    }

    // --- owning --------------------------------------------------------------------

    /**
     * What the player owns to wear: what is owned by default, then every
     * wearable she carries - every wearable there is while {@link
     * #everything()} is on.
     */
    public static Set<ItemDef> owned(Inventory inventory) {
        Set<ItemDef> out = new LinkedHashSet<>(OWNED_BY_DEFAULT);
        for (Item item : inventory) if (item.def().isWearable()) out.add(item.def());
        if (everything) out.addAll(ALL);
        return out;
    }

    /** Whether {@code slot} is not an item's: every folder found for it is offered. */
    public static boolean free(Slot slot) {
        return FREE.contains(slot);
    }

    /** The wearable {@code folder} worn in {@code slot} in {@code colour} is, or null (a folder no item puts there). */
    public static ItemDef wornAs(Slot slot, String folder, String colour) {
        for (ItemDef d : ALL) if (d.wearable().wornAs(slot, folder, colour)) return d;
        return null;
    }

    /** Whether {@code folder} in {@code slot} is a metal's: its colour is the item's, never picked. */
    public static boolean metal(Slot slot, String folder) {
        for (ItemDef d : ALL) if (d.wearable().colour() != null && d.wearable().puts(slot, folder)) return true;
        return false;
    }

    /**
     * One colour of what is worn in a slot, and whether it can be put on as
     * it is ({@code unlocked}) or only by using up a dye of it.
     */
    public record Colour(String option, boolean unlocked) {}

    /**
     * The colours of {@code folder} worn in {@code slot}, out of {@code all}
     * (its {@code variants.json}'s): every one unlocked for the skin, the eyes
     * and the other free layers and for what is carried; none for a metal (its
     * colour is the item's); otherwise every one, unlocked where a dye of it
     * was used on this item ({@link Wardrobe#unlocked}), for the lips every
     * one with the lipstick owned, and the colour already applied ({@code
     * current}); every one while {@link #everything()} is on.
     */
    public static List<Colour> colours(Slot slot, String folder, List<String> all, String current,
                                       Set<ItemDef> owned, Wardrobe wardrobe) {
        List<Colour> out = new ArrayList<>();
        if (metal(slot, folder)) return out;
        boolean open = everything || slot.carried() || (free(slot) && slot != Slot.MOUTH)
                || (slot == Slot.MOUTH && owned.contains(LIPSTICK));
        for (String c : all) {
            out.add(new Colour(c, open || c.equals(current) || wardrobe.unlocked(slot, folder, c)));
        }
        return out;
    }

    /** The first dye of colour {@code colour} in {@code inventory}, or null. */
    public static Item dyeIn(Inventory inventory, String colour) {
        ItemDef d = dye(colour);
        if (d == null) return null;
        for (Item item : inventory) if (item.def() == d) return item;
        return null;
    }

    /**
     * Use up a dye of {@code colour} from {@code inventory} on what is worn in
     * {@code slot}: the colour is unlocked for that item for good and put on.
     * False (and nothing changes) when nothing is worn there, she has no dye
     * of it, or the colour is already unlocked.
     */
    public static boolean useDye(Wardrobe wardrobe, Inventory inventory, Slot slot, String colour) {
        String folder = wardrobe.get(slot);
        if (folder == null || colour == null || wardrobe.unlocked(slot, folder, colour)) return false;
        Item dye = dyeIn(inventory, colour);
        if (dye == null || !inventory.remove(dye)) return false;
        wardrobe.unlock(slot, folder, colour);
        wardrobe.setColour(slot, colour);
        return true;
    }

    /**
     * After the player has lost {@code lost} (dropped it, or it spilled out
     * of a smaller inventory), take off what it gave her - unless she still
     * owns another that gives the same: what she wears of it, the lips'
     * colour with the lipstick. What is owned by default, the free layers and
     * the colours dyes have unlocked (the dye was used up) are never taken
     * off.
     */
    public static void takeOff(Wardrobe wardrobe, Inventory inventory, ItemDef lost) {
        if (lost == null || !lost.isWearable()) return;
        Set<ItemDef> owned = owned(inventory);
        if (owned.contains(lost)) return;
        Wearable w = lost.wearable();
        for (Slot slot : w.slots()) {
            String folder = wardrobe.get(slot);
            String colour = wardrobe.colour(slot);
            if (folder == null || !w.wornAs(slot, folder, colour)) continue;
            boolean other = false;
            for (ItemDef d : owned) other |= d.wearable().wornAs(slot, folder, colour);
            if (other) continue;
            wardrobe.clear(slot);
            wardrobe.setColour(slot, null);
        }
        if (lost == LIPSTICK) {
            String lips = wardrobe.get(Slot.MOUTH), colour = wardrobe.colour(Slot.MOUTH);
            if (lips == null || colour == null || !wardrobe.unlocked(Slot.MOUTH, lips, colour)) {
                wardrobe.setColour(Slot.MOUTH, null);
            }
        }
    }

    // --- dropping ------------------------------------------------------------------

    /**
     * Add the wearables to {@code loot}, each at its rarity - but not what is
     * owned by default. A family (the dyes, the hair styles, the eyebrows,
     * the makeup) is one drop: one of them, any one as likely as another (the
     * starter look is not among them).
     */
    public static Loot addTo(Loot loot) {
        List<String> families = new ArrayList<>();
        for (ItemDef d : ALL) {
            if (OWNED_BY_DEFAULT.contains(d)) continue;
            String family = d.wearable().family();
            if (family == null) {
                loot.add(Item.of(d));
            } else if (!families.contains(family)) {
                families.add(family);
                List<Item> members = new ArrayList<>();
                for (ItemDef m : ALL) {
                    if (family.equals(m.wearable().family()) && !OWNED_BY_DEFAULT.contains(m)) members.add(Item.of(m));
                }
                loot.add(members);
            }
        }
        return loot;
    }
}
