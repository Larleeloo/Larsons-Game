package com.larsons.game.world;

import com.larsons.game.sprite.PixelPalette;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Variants;
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
 *             the dyes (the 14 named ones and one of every other colour
 *             of the palette), makeup (noir, rose, plum), lipstick, the hair
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
    /**
     * Every colour a thing can be: the palette's 100 fills ({@link
     * PixelPalette#FILLS}), each as {@code #rrggbb} - a colour a slot takes
     * like one of its own options ({@link Variants#isCustom}) - the greys
     * first, then round the colour wheel from the browns, each hue from dark
     * to light. A skin, a head of hair, eyes and horns take any of them; what
     * is worn takes one with a dye of it ({@link #dye}).
     */
    public static final List<String> PALETTE = palette();
    /** Each of {@link #PALETTE}'s colours by name: "Dark brown 3". */
    private static final Map<String, String> PALETTE_NAMES = paletteNames();
    /** The hair styles: every folder under {@code assets/sprites/hair/}. */
    public static final List<String> HAIR_STYLES = List.of("long_waves", "bob", "ponytail", "pixie", "braids",
            "curls", "crew_cut", "side_part", "undercut", "beanie_long", "beanie_short");

    /**
     * The families that drop as one ({@link Loot#add(List)}): so many dyes and
     * hair styles would otherwise drown out everything else of their rarity.
     */
    public static final String DYE = "dye", PALETTE_DYE = "palette_dye", HAIR_STYLE = "hair_style",
            EYEBROWS = "eyebrows", MAKEUP = "makeup";

    /** The layers that are not items: every folder found for the body is offered, colours and all. */
    public static final Set<Slot> FREE = Collections.unmodifiableSet(
            EnumSet.of(Slot.BODY, Slot.EARS, Slot.EYES, Slot.NOSE, Slot.MOUTH, Slot.HORNS, Slot.TAIL));

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

    /**
     * How many drops of their rarity the dyes' two families count as in a
     * chest ({@link Loot#add(List, int)}): the 14 named dyes (Uncommon) and
     * the rest of the palette's (Rare) each come up as often as this many
     * other things of their rarity - and a chest gives a dye or two besides
     * ({@link #randomDye}), a breakable now and then one ({@link
     * Store#DYES_IN}), a foe felled now and then one, and a shop sells some
     * ({@link Shop}'s {@code dyes x4}).
     */
    public static final int DYE_CHANCES = 6;

    /**
     * A dye drawn at random: one of the 14 named ones seven times in ten,
     * else one of the rest of the palette's.
     */
    public static ItemDef randomDye(java.util.Random random) {
        if (random.nextInt(10) < 7) return byId("dye_" + DYE_COLOURS.get(random.nextInt(DYE_COLOURS.size())));
        List<ItemDef> rest = new ArrayList<>();
        for (ItemDef d : ALL) if (PALETTE_DYE.equals(d.wearable().family())) rest.add(d);
        return rest.isEmpty() ? byId("dye_" + DYE_COLOURS.get(0)) : rest.get(random.nextInt(rest.size()));
    }

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

    /**
     * The tailored clothes: a garment the game already has, cut in a colour of
     * its own and named for who wears it - a Drurinean court dress, a herder's
     * shirt, a boilerman's cap. Owning one is owning the garment and its colour
     * (no dye is used up); the peoples she meets are dressed from them ({@code
     * actor/Looks}). Its family in the loot is {@link #TAILORED} and its rarity.
     */
    public static final String TAILORED = "tailored";
    /** Each tailored garment's colour: an option of its {@code variants.json}. */
    private static final Map<String, String> TAILORED_COLOUR = new LinkedHashMap<>();

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
        outfits(out, Rarity.COMMON);
        // Uncommon
        out.add(worn("belt", "Belt", Rarity.UNCOMMON, Slot.BELT, "leather_belt"));
        out.add(worn("gloves", "Gloves", Rarity.UNCOMMON, Slot.GLOVES, "black_leather"));
        out.add(def("sheath", "Sheath", new Wearable(Rarity.UNCOMMON, List.of(Slot.SHEATH),
                List.of("sword_left_hip", "sword_right_hip"), null, null, null)));
        out.add(worn("laced_shirt", "Laced shirt", Rarity.UNCOMMON, Slot.SHIRT, "laced_shirt"));
        out.add(worn("wool_mantle", "Wool mantle", Rarity.UNCOMMON, Slot.OTHER, "wool_mantle"));
        out.add(worn("chainmail_shirt", "Chainmail shirt", Rarity.UNCOMMON, Slot.CHAINMAIL, "chainmail_shirt"));
        out.add(worn("chainmail_pants", "Chainmail pants", Rarity.UNCOMMON, Slot.CHAINMAIL_LEGS, "chainmail_pants"));
        outfits(out, Rarity.UNCOMMON);
        // the 14 named dyes: Uncommon, so they turn up often (the rest of the palette's are Rare)
        for (String c : DYE_COLOURS) {
            out.add(def("dye_" + c, readable(c) + " dye", new Wearable(Rarity.UNCOMMON, List.of(), List.of(), null, c,
                    DYE)));
        }
        // Rare
        out.add(worn("laced_dress", "Laced dress", Rarity.RARE, Slot.SHIRT, "laced_dress"));
        out.add(metal("silver_wrist_band", "Silver wrist band", Rarity.RARE, "silver", "gold_bangle",
                Slot.WRISTWEAR, Slot.WRISTWEAR_LEFT));
        out.add(metal("silver_necklace", "Silver necklace", Rarity.RARE, "silver", "gold_pendant", Slot.NECKLACE));
        out.add(metal("silver_hoops", "Silver hoops", Rarity.RARE, "silver", "gold_hoops", Slot.EARRINGS));
        // a dye of every other colour of the palette (the 14 named ones are 14 of its fills)
        for (String c : PALETTE) {
            if (DYE_RGB.containsValue(rgb(c))) continue;
            out.add(def("dye_" + c.substring(1), PALETTE_NAMES.get(c) + " dye",
                    new Wearable(Rarity.RARE, List.of(), List.of(), null, c, PALETTE_DYE)));
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
        outfits(out, Rarity.RARE);
        // Epic
        out.add(metal("gold_wrist_band", "Gold wrist band", Rarity.EPIC, "gold", "gold_bangle",
                Slot.WRISTWEAR, Slot.WRISTWEAR_LEFT));
        out.add(metal("gold_necklace", "Gold necklace", Rarity.EPIC, "gold", "gold_pendant", Slot.NECKLACE));
        out.add(metal("gold_hoops", "Gold hoops", Rarity.EPIC, "gold", "gold_hoops", Slot.EARRINGS));
        armour(out, "steel", Rarity.EPIC);
        outfits(out, Rarity.EPIC);
        outfits(out, Rarity.LEGENDARY);
        tailored(out);
        // the shields, carried on the off-hand arm ({@link Shields}): each drops at its own rarity
        out.addAll(Shields.defs());
        return List.copyOf(out);
    }

    /**
     * One of the clothes, armour and jewellery of 3D-Modeling's {@code
     * *_model_outfits.py}: its id and name, how rare, where it goes and its
     * folder there - and, for one drawn from another's art, the look it is
     * worn in ({@link #TINTS}: a bronze helmet is the iron helmet in bronze).
     */
    private record Outfit(String id, String name, Rarity rarity, String folder, String look, List<Slot> slots) {}

    private static Outfit o(String id, String name, Rarity rarity, String folder, Slot... slots) {
        return new Outfit(id, name, rarity, folder, null, List.of(slots));
    }

    private static Outfit o(String id, String name, Rarity rarity, String folder, String look, Slot... slots) {
        return new Outfit(id, name, rarity, folder, look, List.of(slots));
    }

    /**
     * The looks a piece drawn from another's art is worn in, with no colour of
     * its own to change ({@code variants.json} has none for its metal): the
     * colour its metal or leather is tinted, as the forge tints a part (RGB).
     * (Bronze is a brassy, yellow bronze: an orange one came out the colour of
     * skin, and bronze leg plating looked like bare legs.)
     */
    public static final Map<String, Integer> TINTS = Map.of(
            "bronze", 0xB5893E, "gilded", 0xF9C36D, "blackened", 0x3E3631, "silvered", 0xD8DCE4);

    /**
     * The 54 new wearables: 43 pieces of their own (a new eyewear slot among
     * them), and 11 more drawn from theirs and the old armour's art in another
     * metal. The armour among them rolls dice to block a blow ({@link Armour}).
     */
    private static List<Outfit> outfitTable() {
        return List.of(
            // hats and helms
            o("great_helm", "Great helm", Rarity.EPIC, "great_helm", Slot.HAT),
            o("kettle_hat", "Kettle hat", Rarity.UNCOMMON, "kettle_hat", Slot.HAT),
            o("horned_helm", "Horned helm", Rarity.RARE, "horned_helm", Slot.HAT),
            o("chain_coif", "Chain coif", Rarity.UNCOMMON, "chain_coif", Slot.HAT),
            o("leather_cap", "Leather cap", Rarity.COMMON, "leather_cap", Slot.HAT),
            o("wizard_hat", "Wizard's hat", Rarity.UNCOMMON, "wizard_hat", Slot.HAT),
            o("top_hat", "Top hat", Rarity.UNCOMMON, "top_hat", Slot.HAT),
            o("tricorn", "Tricorn", Rarity.UNCOMMON, "tricorn", Slot.HAT),
            o("hood", "Hood", Rarity.COMMON, "hood", Slot.HAT),
            o("crown", "Crown", Rarity.EPIC, "crown", Slot.HAT),
            o("circlet", "Circlet", Rarity.RARE, "circlet", Slot.HAT),
            o("flower_crown", "Flower crown", Rarity.COMMON, "flower_crown", Slot.HAT),
            // eyewear
            o("brass_goggles", "Brass goggles", Rarity.RARE, "brass_goggles", Slot.EYEWEAR),
            o("eyepatch", "Eyepatch", Rarity.COMMON, "eyepatch", Slot.EYEWEAR),
            o("masquerade_mask", "Masquerade mask", Rarity.RARE, "masquerade_mask", Slot.EYEWEAR),
            o("monocle", "Monocle", Rarity.RARE, "monocle", Slot.EYEWEAR),
            // round the neck, and the ears
            o("gorget", "Steel gorget", Rarity.RARE, "gorget", Slot.NECKLACE),
            o("silver_amulet", "Silver amulet", Rarity.RARE, "amulet", "silver", Slot.NECKLACE),
            o("gold_amulet", "Gold amulet", Rarity.EPIC, "amulet", "gold", Slot.NECKLACE),
            o("choker", "Cameo choker", Rarity.UNCOMMON, "choker", Slot.NECKLACE),
            o("scarf", "Striped scarf", Rarity.COMMON, "scarf", Slot.NECKLACE),
            o("gem_studs", "Gem studs", Rarity.RARE, "gem_studs", Slot.EARRINGS),
            o("drop_earrings", "Drop earrings", Rarity.RARE, "drop_earrings", Slot.EARRINGS),
            // the body
            o("gambeson", "Gambeson", Rarity.UNCOMMON, "gambeson", Slot.SHIRT),
            o("waistcoat", "Waistcoat", Rarity.UNCOMMON, "waistcoat", Slot.SHIRT),
            o("robe", "Robe", Rarity.UNCOMMON, "robe", Slot.SHIRT),
            o("frock_coat", "Frock coat", Rarity.RARE, "frock_coat", Slot.SHIRT),
            o("breeches", "Breeches", Rarity.COMMON, "breeches", Slot.PANTS),
            o("pleated_skirt", "Pleated skirt", Rarity.COMMON, "pleated_skirt", Slot.PANTS),
            o("riding_boots", "Riding boots", Rarity.UNCOMMON, "riding_boots", Slot.SHOES),
            o("sandals", "Sandals", Rarity.COMMON, "sandals", Slot.SHOES),
            o("sabatons", "Sabatons", Rarity.RARE, "sabatons", Slot.SHOES),
            o("fingerless_gloves", "Fingerless gloves", Rarity.COMMON, "fingerless_gloves", Slot.GLOVES),
            o("leather_bracers", "Leather bracers", Rarity.UNCOMMON, "leather_bracers", Slot.WRISTWEAR,
                    Slot.WRISTWEAR_LEFT),
            o("gear_cuff", "Gear cuff", Rarity.RARE, "gear_cuff", Slot.WRISTWEAR, Slot.WRISTWEAR_LEFT),
            o("corset", "Corset", Rarity.UNCOMMON, "corset", Slot.BELT),
            o("sash", "Silk sash", Rarity.COMMON, "sash", Slot.BELT),
            o("pouch_belt", "Pouch belt", Rarity.UNCOMMON, "pouch_belt", Slot.BELT),
            o("scale_mail", "Scale mail", Rarity.RARE, "scale_mail", Slot.CHAINMAIL),
            o("leather_jerkin", "Leather jerkin", Rarity.UNCOMMON, "leather_jerkin", Slot.CHESTPLATE),
            o("leather_greaves", "Leather greaves", Rarity.UNCOMMON, "leather_greaves", Slot.LEG_PLATING),
            o("leather_backpack", "Leather backpack", Rarity.UNCOMMON, "backpack", Slot.OTHER),
            o("clockwork_wings", "Clockwork wings", Rarity.EPIC, "clockwork_wings", Slot.OTHER),
            // the same art in another metal
            o("bronze_helmet", "Bronze helmet", Rarity.UNCOMMON, "iron_helmet", "bronze", Slot.HAT),
            o("bronze_chestplate", "Bronze chestplate", Rarity.UNCOMMON, "iron_chestplate", "bronze", Slot.CHESTPLATE),
            o("bronze_leg_plating", "Bronze leg plating", Rarity.UNCOMMON, "iron_leg_plating", "bronze",
                    Slot.LEG_PLATING),
            o("bronze_gauntlets", "Bronze gauntlets", Rarity.UNCOMMON, "iron_gauntlets", "bronze", Slot.GLOVES),
            o("bronze_kettle_hat", "Bronze kettle hat", Rarity.COMMON, "kettle_hat", "bronze", Slot.HAT),
            o("silvered_monocle", "Silvered monocle", Rarity.UNCOMMON, "monocle", "silvered", Slot.EYEWEAR),
            o("blackened_jerkin", "Blackened jerkin", Rarity.RARE, "leather_jerkin", "blackened", Slot.CHESTPLATE),
            o("blackened_sabatons", "Blackened sabatons", Rarity.EPIC, "sabatons", "blackened", Slot.SHOES),
            o("gilded_scale_mail", "Gilded scale mail", Rarity.EPIC, "scale_mail", "gilded", Slot.CHAINMAIL),
            o("gilded_great_helm", "Gilded great helm", Rarity.LEGENDARY, "great_helm", "gilded", Slot.HAT),
            o("gilded_wings", "Gilded clockwork wings", Rarity.LEGENDARY, "clockwork_wings", "gilded", Slot.OTHER));
    }

    /** The new wearables of {@code rarity} ({@code outfitTable}). */
    private static void outfits(List<ItemDef> out, Rarity rarity) {
        for (Outfit f : outfitTable()) {      // (a method: ALL is made before the class's later fields)
            if (f.rarity() != rarity) continue;
            out.add(def(f.id(), f.name(), new Wearable(f.rarity(), f.slots(), List.of(f.folder()), f.look(), null,
                    null)));
        }
    }

    /** How many wearables {@code outfitTable} added (54). */
    public static int outfitCount() {
        return outfitTable().size();
    }

    /** Whether {@code colour} worn in a slot is a tint of {@link #TINTS} (a bronze helmet's), not a colour option. */
    public static boolean tint(String colour) {
        return colour != null && TINTS.containsKey(colour);
    }

    /** The tailored clothes, people by people. */
    private static void tailored(List<ItemDef> out) {
        // the Drurinean: elegant
        out.add(cut("drurin_court_dress", "Drurinean court dress", Rarity.RARE, Slot.SHIRT, "laced_dress", "violet_ombre"));
        out.add(cut("moonsilk_gown", "Moonsilk gown", Rarity.EPIC, Slot.SHIRT, "laced_dress", "platinum"));
        out.add(cut("scholars_shirt", "Scholar's laced shirt", Rarity.UNCOMMON, Slot.SHIRT, "laced_shirt", "royal_blue"));
        out.add(cut("starweave_mantle", "Starweave mantle", Rarity.RARE, Slot.OTHER, "wool_mantle", "electric_blue"));
        out.add(cut("observatory_gloves", "Observatory gloves", Rarity.UNCOMMON, Slot.GLOVES, "black_leather", "platinum"));
        out.add(cut("silverthread_trousers", "Silverthread trousers", Rarity.UNCOMMON, Slot.PANTS, "denim_jeans", "platinum"));
        out.add(cut("court_slippers", "Court slippers", Rarity.UNCOMMON, Slot.SHOES, "leather_ankle_boots", "violet_ombre"));
        out.add(cut("astronomers_cap", "Astronomer's cap", Rarity.UNCOMMON, Slot.HAT, "beanie", "royal_blue"));
        // the Khorigale: common
        out.add(cut("herders_shirt", "Herder's shirt", Rarity.COMMON, Slot.SHIRT, "plaid_flannel", "copper"));
        out.add(cut("field_trousers", "Field trousers", Rarity.COMMON, Slot.PANTS, "denim_jeans", "brown"));
        out.add(cut("herders_mantle", "Herder's mantle", Rarity.COMMON, Slot.OTHER, "wool_mantle", "auburn"));
        out.add(cut("sunbleached_hat", "Sun-bleached straw hat", Rarity.COMMON, Slot.HAT, "straw_sun_hat", "honey_blonde"));
        out.add(cut("mud_boots", "Mud boots", Rarity.COMMON, Slot.SHOES, "leather_ankle_boots", "brown"));
        out.add(cut("market_dress", "Market dress", Rarity.UNCOMMON, Slot.SHIRT, "laced_dress", "copper"));
        out.add(cut("rope_belt", "Rope belt", Rarity.COMMON, Slot.BELT, "leather_belt", "honey_blonde"));
        // the Half Drurinean and the workshops: brass and soot
        out.add(cut("tinkers_shirt", "Tinker's shirt", Rarity.UNCOMMON, Slot.SHIRT, "laced_shirt", "copper"));
        out.add(cut("boilermans_cap", "Boilerman's cap", Rarity.UNCOMMON, Slot.HAT, "beanie", "copper"));
        out.add(cut("engineers_gloves", "Engineer's gloves", Rarity.UNCOMMON, Slot.GLOVES, "black_leather", "copper"));
        out.add(cut("brass_buckle_belt", "Brass-buckle belt", Rarity.UNCOMMON, Slot.BELT, "leather_belt", "copper"));
        out.add(cut("oilcloth_trousers", "Oilcloth trousers", Rarity.UNCOMMON, Slot.PANTS, "denim_jeans", "black"));
        out.add(cut("soot_mantle", "Soot mantle", Rarity.UNCOMMON, Slot.OTHER, "wool_mantle", "black"));
        // the townsfolk and the guard
        out.add(cut("guards_shirt", "Town guard's shirt", Rarity.UNCOMMON, Slot.SHIRT, "laced_shirt", "cherry_red"));
        out.add(cut("millers_shirt", "Miller's shirt", Rarity.COMMON, Slot.SHIRT, "plaid_flannel", "platinum"));
        out.add(cut("festival_dress", "Festival dress", Rarity.RARE, Slot.SHIRT, "laced_dress", "cherry_red"));
        out.add(cut("foresters_hat", "Forester's hat", Rarity.COMMON, Slot.HAT, "straw_sun_hat", "emerald"));
        out.add(cut("foresters_mantle", "Forester's mantle", Rarity.UNCOMMON, Slot.OTHER, "wool_mantle", "emerald"));
        // the risen: what they were buried in
        out.add(cut("grave_shroud", "Grave shroud", Rarity.UNCOMMON, Slot.SHIRT, "laced_dress", "mint"));
        out.add(cut("mouldering_shirt", "Mouldering shirt", Rarity.COMMON, Slot.SHIRT, "plaid_flannel", "green_ombre"));
        out.add(cut("gravediggers_trousers", "Gravedigger's trousers", Rarity.COMMON, Slot.PANTS, "denim_jeans", "auburn"));
        out.add(cut("tattered_mantle", "Tattered mantle", Rarity.COMMON, Slot.OTHER, "wool_mantle", "brown"));
        out.add(cut("bog_boots", "Bog boots", Rarity.COMMON, Slot.SHOES, "leather_ankle_boots", "black"));
        out.add(cut("plague_cap", "Plague cap", Rarity.COMMON, Slot.HAT, "beanie", "mint"));
    }

    private static ItemDef cut(String id, String name, Rarity rarity, Slot slot, String folder, String colour) {
        TAILORED_COLOUR.put(id, colour);
        return def(id, name, new Wearable(rarity, List.of(slot), List.of(folder), null, null,
                TAILORED + "_" + rarity.key()));
    }

    /** The colour {@code d}, a tailored garment, is cut in; null for anything else. */
    public static String tailoredColour(ItemDef d) {
        return d == null ? null : TAILORED_COLOUR.get(d.id());
    }

    /** The tailored garments: every one there is. */
    public static List<ItemDef> tailored() {
        List<ItemDef> out = new ArrayList<>();
        for (ItemDef d : ALL) if (TAILORED_COLOUR.containsKey(d.id())) out.add(d);
        return out;
    }

    /** Whether some tailored garment is {@code folder} in {@code slot} cut in {@code colour}. */
    public static boolean tailoredCut(Slot slot, String folder, String colour) {
        if (folder == null || colour == null) return false;
        for (ItemDef d : ALL) {
            String c = TAILORED_COLOUR.get(d.id());
            if (c != null && c.equalsIgnoreCase(colour) && d.wearable().puts(slot, folder)) return true;
        }
        return false;
    }

    /** Whether one of {@code owned} is {@code folder} in {@code slot} tailored in {@code colour}. */
    private static boolean tailoredIn(Set<ItemDef> owned, Slot slot, String folder, String colour) {
        for (ItemDef d : owned) {
            if (colour.equals(TAILORED_COLOUR.get(d.id())) && d.wearable().puts(slot, folder)) return true;
        }
        return false;
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

    // --- the palette's colours ---------------------------------------------------------

    /** A {@code #rrggbb} colour's RGB. */
    public static int rgb(String custom) {
        return Integer.parseInt(custom.substring(1), 16) & 0xFFFFFF;
    }

    /** What a colour is called: an option's name ("Royal blue"), a palette colour's ("Dark brown 3"), or as written. */
    public static String colourName(String colour) {
        if (colour == null) return "Own colours";
        if (Variants.isCustom(colour)) {
            String n = PALETTE_NAMES.get(colour.toLowerCase(Locale.ROOT));
            return n != null ? n : colour;
        }
        return readable(colour);
    }

    /** The colour a dye is in its bottle (RGB), or {@code otherwise}. */
    static int dyeRgb(String dye, int otherwise) {
        if (Variants.isCustom(dye)) return rgb(dye);
        return DYE_RGB.getOrDefault(dye, otherwise);
    }

    /** Where a colour sits among the hues - the greys 0, then round the wheel from the browns - and its name there. */
    private record Hue(int order, String name, double lightness) {}

    private static Hue hue(int rgb) {
        double[] lab = Variants.Oklab.of(rgb);
        double L = lab[0], c = Math.hypot(lab[1], lab[2]);
        double h = Math.toDegrees(Math.atan2(lab[2], lab[1]));
        if (h < 0) h += 360;
        if (c < 0.025) {
            return new Hue(0, L > 0.93 ? "White" : L < 0.2 ? "Black" : L < 0.42 ? "Dark grey" : L < 0.68 ? "Grey"
                    : "Light grey", L);
        }
        String[] names = {"Rose", "Red", "Orange", "Amber", "Yellow", "Lime", "Green", "Teal", "Cyan", "Blue",
                "Indigo", "Violet", "Magenta", "Rose"};
        double[] upTo = {18, 40, 62, 88, 108, 128, 160, 190, 228, 262, 290, 318, 345, 360};
        int at = 0;
        while (at < upTo.length - 1 && h > upTo[at]) at++;
        String name = names[at];
        int order = at == upTo.length - 1 ? 15 : at + 3;
        boolean warm = name.equals("Red") || name.equals("Orange") || name.equals("Amber");
        if (warm && L < 0.62 && c < 0.16) {
            name = L > 0.28 ? "Brown" : "Umber";
            order = 1;
        } else if ((warm || name.equals("Yellow")) && !name.equals("Red") && L > 0.8 && c < 0.1) {
            name = "Cream";
            order = 2;
        } else if ((name.equals("Rose") || name.equals("Red")) && L > 0.75) {
            name = "Pink";
        }
        String shade = L < 0.3 ? "Deep" : L < 0.48 ? "Dark" : L < 0.7 ? "" : L < 0.86 ? "Light" : "Pale";
        boolean plain = name.equals("Cream") || name.equals("Brown") || name.equals("Umber");
        if (c < 0.06 && !plain) shade = shade.isEmpty() ? "Dusty" : shade + " dusty";
        return new Hue(order, shade.isEmpty() ? name : shade + " " + name.toLowerCase(Locale.ROOT), L);
    }

    private static List<String> palette() {
        List<Integer> fills = new ArrayList<>();
        for (int c : PixelPalette.FILLS) if (!fills.contains(c)) fills.add(c);
        fills.sort((a, b) -> {
            Hue x = hue(a), y = hue(b);
            return x.order() != y.order() ? Integer.compare(x.order(), y.order())
                    : Double.compare(x.lightness(), y.lightness());
        });
        List<String> out = new ArrayList<>();
        for (int c : fills) out.add(String.format(Locale.ROOT, "#%06x", c));
        return List.copyOf(out);
    }

    private static Map<String, String> paletteNames() {
        // (colours of one name are numbered from the darkest)
        Map<String, List<String>> named = new LinkedHashMap<>();
        for (String c : PALETTE) named.computeIfAbsent(hue(rgb(c)).name(), k -> new ArrayList<>()).add(c);
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : named.entrySet()) {
            List<String> of = new ArrayList<>(e.getValue());
            of.sort((a, b) -> Double.compare(hue(rgb(a)).lightness(), hue(rgb(b)).lightness()));
            for (int i = 0; i < of.size(); i++) {
                out.put(of.get(i), of.size() == 1 ? e.getKey() : e.getKey() + " " + (i + 1));
            }
        }
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

    /**
     * The dye of colour {@code colour} - an option ("royal_blue") or one of
     * the palette's ({@code #rrggbb}; one of the 14 options' own colours is
     * that option's dye) - or null when there is none.
     */
    public static ItemDef dye(String colour) {
        if (colour == null) return null;
        if (Variants.isCustom(colour)) {
            String c = colour.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Integer> e : DYE_RGB.entrySet()) {
                if (e.getValue() == rgb(c)) return byId("dye_" + e.getKey());
            }
            ItemDef d = byId("dye_" + c.substring(1));
            return d != null && c.equals(d.wearable().dye()) ? d : null;
        }
        ItemDef d = byId("dye_" + colour);
        return d != null && colour.equals(d.wearable().dye()) ? d : null;
    }

    /**
     * Whether {@code d} is armour - chainmail, plate, gauntlets, a helmet, a
     * gambeson, sabatons, bracers ({@link Armour#sides}) - which rolls dice to
     * block a blow and is drawn as the shield.
     */
    public static boolean armour(ItemDef d) {
        return Armour.sides(d) > 0;
    }

    /**
     * What {@code item} is drawn as lying about and in the inventory: a dye
     * as a bottle of it, armour as a shield in its rarity's colour, anything
     * else worn as a t-shirt on a hanger in it; a picking as its sprig, fruit
     * or flower in its colour ({@link Food}); null for a weapon (drawn as
     * itself).
     */
    public static Pickup pickup(Item item) {
        ItemDef d = item.def();
        if (d.isFood()) return new Pickup(d.food().pickup(), d.food().part(), d.food().rgb());
        if (d.isTack()) return new Pickup(d.id(), "blanket", Tack.SADDLE_RGB);
        if (!d.isWearable()) return null;
        String dye = d.wearable().dye();
        if (dye != null) return new Pickup(BOTTLE, "liquid", dyeRgb(dye, item.rarity().rgb()));
        String cut = TAILORED_COLOUR.get(d.id());
        if (cut != null) return new Pickup(HANGER, "cloth", DYE_RGB.getOrDefault(cut, item.rarity().rgb()));
        // a shield: itself, turning, in its own colours
        if (Shields.is(d)) return new Pickup(d.id(), null, item.rarity().rgb());
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
        // (the one of that colour first: a bronze helmet is the iron helmet's folder, in bronze)
        if (colour != null) {
            for (ItemDef d : ALL) if (colour.equals(d.wearable().colour()) && d.wearable().puts(slot, folder)) return d;
            // (a tailored garment in its cut: the laced dress in platinum is the Moonsilk gown)
            for (ItemDef d : ALL) {
                if (colour.equalsIgnoreCase(TAILORED_COLOUR.get(d.id())) && d.wearable().puts(slot, folder)) return d;
            }
        }
        for (ItemDef d : ALL) if (d.wearable().wornAs(slot, folder, colour)) return d;
        return null;
    }

    /** Whether {@code folder} in {@code slot} is a metal's: its colour is the item's, never picked. */
    public static boolean metal(Slot slot, String folder) {
        for (ItemDef d : ALL) {
            String c = d.wearable().colour();
            if (c != null && !tint(c) && d.wearable().puts(slot, folder)) return true;
        }
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
     * colour is the item's); every one for the hair and the eyebrows, which
     * are hers to colour from the start; otherwise every one, unlocked where
     * a dye of it was used on this item ({@link Wardrobe#unlocked}), for the
     * lips every one with the lipstick owned, and the colour already applied
     * ({@code current}); every one while {@link #everything()} is on. After
     * the item's own options come the palette's colours ({@link #PALETTE}),
     * unlocked the same way.
     */
    public static List<Colour> colours(Slot slot, String folder, List<String> all, String current,
                                       Set<ItemDef> owned, Wardrobe wardrobe) {
        return colours(slot, folder, all, current, owned, wardrobe, false);
    }

    /**
     * {@link #colours(Slot, String, List, String, Set, Wardrobe)}; a forged
     * piece ({@code forged}) is dyed like any other, even one of a metal - the
     * Forge's look is not the metal's.
     */
    public static List<Colour> colours(Slot slot, String folder, List<String> all, String current,
                                       Set<ItemDef> owned, Wardrobe wardrobe, boolean forged) {
        List<Colour> out = new ArrayList<>();
        if (metal(slot, folder) && !forged) return out;
        boolean open = everything || slot.carried() || (free(slot) && slot != Slot.MOUTH)
                || (slot == Slot.MOUTH && owned.contains(LIPSTICK)) || slot == Slot.HAIR || slot == Slot.EYEBROWS;
        for (String c : all) {
            out.add(new Colour(c, open || c.equals(current) || wardrobe.unlocked(slot, folder, c)
                    || tailoredIn(owned, slot, folder, c)));
        }
        // and any of the palette's colours (an item with no colour of its own to change takes none - but armour
        // is dyed all over, as the Forge tints it)
        if (!all.isEmpty() || Wardrobe.isArmour(folder)) {
            for (String c : PALETTE) {
                out.add(new Colour(c, open || c.equalsIgnoreCase(current) || wardrobe.unlocked(slot, folder, c)));
            }
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
        return useDye(wardrobe, inventory, slot, Wardrobe.isArmour(wardrobe.get(slot)), colour);
    }

    /**
     * {@link #useDye(Wardrobe, Inventory, Slot, String)} on the armour worn in
     * {@code slot} ({@code armour}) or the clothes ({@link Wardrobe#get(Slot, boolean)}):
     * a necklace under a gorget is dyed as well as the gorget.
     */
    public static boolean useDye(Wardrobe wardrobe, Inventory inventory, Slot slot, boolean armour, String colour) {
        String folder = wardrobe.get(slot, armour);
        if (folder == null || colour == null || wardrobe.unlocked(slot, folder, colour)) return false;
        Item dye = dyeIn(inventory, colour);
        if (dye == null || !inventory.remove(dye)) return false;
        wardrobe.unlock(slot, folder, colour);
        wardrobe.setColour(slot, armour, colour);
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
            // (the armour there, and the clothes under it)
            for (boolean armour : new boolean[]{true, false}) {
                String folder = wardrobe.get(slot, armour);
                String colour = wardrobe.colour(slot, armour), id = wardrobe.id(slot, armour);
                if (folder == null) continue;
                boolean it = id != null ? id.equals(lost.id()) : w.wornAs(slot, folder, colour);
                if (!it) continue;
                boolean other = false;
                for (ItemDef d : owned) {
                    other |= id != null ? d.id().equals(id) && d != lost : d.wearable().wornAs(slot, folder, colour);
                }
                if (other) continue;
                wardrobe.setColour(slot, armour, null);
                wardrobe.wear(slot, armour, null);
            }
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
        // (the dyes are given more chances than one: they turn up often)
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
                loot.add(members, family.equals(DYE) || family.equals(PALETTE_DYE) ? DYE_CHANCES : 1);
            }
        }
        return loot;
    }
}
