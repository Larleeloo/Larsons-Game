package com.larsons.game.world;

import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.fallback.FallbackSprites;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The goods of the crafting and the trade: what is brewed, written, caught,
 * found and bought, stacked in the inventory as the pickings and the
 * supplies are. Each has a {@link Rarity} of its own, a price, a line saying
 * what it is for, and - for what is drunk, read or used - what using it does.
 *
 * <pre>
 *   potions      brewed at a cauldron ({@link Recipes}); drunk for health, mana, stamina or a help that lasts
 *   scrolls      never brewed, stronger: read once
 *   keys         eight mysterious keys, each opening the doors and gates a level ties to it
 *   eggs         a spawner egg hatches its creature where she stands (the dragon's hatches nothing, yet)
 *   trinkets     the crystal ball (shows the foes about), the crystal shield (a ward), the drinking horn
 *   rods         fishing rods, better by rarity: a longer bite, a wider catch, rarer fish
 *   fish         what is caught, eaten or sold - and brewed with
 *   packs        a satchel, a backpack, an explorer's pack: more room in her inventory, for good
 *   makings      bottles, parchment, ink, twine, gears, hooks: what the benches and cauldrons use up
 * </pre>
 *
 * <p>Each is drawn as one of the turning pickups 3D-Modeling's {@code
 * items/item_spin_sprites.py} builds ({@code craft_*}), one part of it
 * painted its own colour.
 */
public final class Goods {

    /** What sort of good it is. */
    public enum Kind {
        POTION("Potion"), SCROLL("Scroll"), KEY("Key"), EGG("Spawner egg"), TRINKET("Trinket"), ROD("Fishing rod"),
        FISH("Fish"), MEAL("Meal"), PACK("Pack"), MAKING("Crafting");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() { return label; }
    }

    /** A help a good gives when used: {@code kind} worth {@code value} for {@code seconds} (charges: 0 none). */
    public record Help(Buffs.Kind kind, double value, double seconds, int charges) {}

    /**
     * A good.
     *
     * @param price   what a shop asks for it, coins
     * @param about   the line under its name
     * @param health  given back at once when used (also mana, stamina)
     * @param helps   what lasts
     * @param arg     an egg's creature, a pack's slots, a rod's reach - what its kind needs to know
     */
    public record Good(ItemDef def, Kind kind, Rarity rarity, int price, String about, double health, double mana,
                       double stamina, List<Help> helps, String arg) {

        public String id() { return def.id(); }

        /** Whether using it uses it up. */
        public boolean spent() {
            return kind == Kind.POTION || kind == Kind.SCROLL || kind == Kind.EGG || kind == Kind.PACK;
        }

        /** Whether it can be used from her hand at all. */
        public boolean usable() {
            return kind == Kind.POTION || kind == Kind.SCROLL || kind == Kind.EGG || kind == Kind.PACK
                    || kind == Kind.TRINKET;
        }
    }

    public static final String P_POTION = "craft_potion", P_KEY = "craft_key", P_EGG = "craft_egg",
            P_ORB = "craft_orb", P_HORN = "craft_horn", P_ROD = "craft_rod", P_FISH = "craft_fish",
            P_PACK = "craft_backpack", P_SCROLL = Supplies.P_SCROLL, P_SHIELD = "armour_shield",
            P_BOTTLE = "dye_bottle", P_INGOT = Supplies.P_INGOT, P_CRYSTAL = Supplies.P_CRYSTAL;

    private static final Map<String, Good> BY_ID = new LinkedHashMap<>();
    private static final List<ItemDef> DEFS = new ArrayList<>();

    private static Good good(String id, String name, Kind kind, Rarity rarity, int price, String about, int stack,
                             String pickup, String part, int rgb, double health, double mana, double stamina,
                             String arg, Help... helps) {
        // (a fish is eaten as a picking is: its Food says what it gives back)
        boolean eaten = kind == Kind.FISH || kind == Kind.MEAL;
        Food f = new Food(kind.label(), eaten ? health : 0, eaten ? stamina : 0, eaten ? mana : 0, stack, pickup,
                part, rgb);
        ItemDef def = new ItemDef(id, name, null, kind == Kind.ROD ? 0.9 : kind == Kind.PACK ? 0.55 : 0.4,
                () -> FallbackSprites.wearableIcon(rgb), Stance.SWORD, null, f);
        Good g = new Good(def, kind, rarity, price, about, health, mana, stamina, List.of(helps), arg);
        BY_ID.put(id, g);
        DEFS.add(def);
        return g;
    }

    private static Help help(Buffs.Kind k, double value, double seconds) {
        return new Help(k, value, seconds, 0);
    }

    private static Good potion(String id, String name, Rarity r, int price, String about, int rgb, double health,
                               double mana, double stamina, Help... helps) {
        return good(id, name, Kind.POTION, r, price, about, 10, P_POTION, "liquid", rgb, health, mana, stamina, null,
                helps);
    }

    private static Good scroll(String id, String name, Rarity r, int price, String about, int rgb, double health,
                               double mana, String arg, Help... helps) {
        return good(id, name, Kind.SCROLL, r, price, about, 5, P_SCROLL, "ribbon", rgb, health, mana, 0, arg, helps);
    }

    private static Good key(String id, String name, Rarity r, int price, int rgb) {
        return good(id, name, Kind.KEY, r, price, "Opens the doors and gates it was made for", 5, P_KEY, "metal", rgb,
                0, 0, 0, null);
    }

    private static Good egg(String creature, String name, Rarity r, int price, int rgb) {
        return good("egg_" + creature, name + " egg", Kind.EGG, r, price,
                creature.equals("dragon") ? "Warm to the touch. Nothing hatches from it - yet"
                        : "Hatches " + article(name) + " where you stand", 5, P_EGG, "shell", rgb, 0, 0, 0, creature);
    }

    private static String article(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return ("aeiou".indexOf(n.charAt(0)) >= 0 ? "an " : "a ") + n;
    }

    private static Good rod(String id, String name, Rarity r, int price, int rgb, String about) {
        return good(id, name, Kind.ROD, r, price, about, 1, P_ROD, "wood", rgb, 0, 0, 0, null);
    }

    private static Good fish(String id, String name, Rarity r, int price, int rgb, double health, double stamina,
                             double mana) {
        return good(id, name, Kind.FISH, r, price, "Caught with a rod. Eaten, sold, or brewed with", 20, P_FISH,
                "body", rgb, health, mana, stamina, null);
    }

    private static Good making(String id, String name, Rarity r, int price, String about, String pickup, String part,
                               int rgb) {
        return good(id, name, Kind.MAKING, r, price, about, 40, pickup, part, rgb, 0, 0, 0, null);
    }

    // --- potions: brewed at a cauldron ---------------------------------------------------
    public static final Good HEALTH_POTION = potion("health_potion", "Health potion", Rarity.COMMON, 18,
            "+50 health", 0xD8283C, 50, 0, 0);
    public static final Good GREATER_HEALTH_POTION = potion("greater_health_potion", "Greater health potion",
            Rarity.RARE, 60, "+100 health, and 2 a second for a minute", 0xFF4A6A, 100, 0, 0,
            help(Buffs.Kind.REGEN_HEALTH, 2, 60));
    public static final Good MANA_POTION = potion("mana_potion", "Mana potion", Rarity.COMMON, 18, "+50 mana",
            0x3F7BFF, 0, 50, 0);
    public static final Good STAMINA_POTION = potion("stamina_potion", "Stamina potion", Rarity.COMMON, 14,
            "+60 stamina", 0x4AC850, 0, 0, 60);
    public static final Good SWIFTNESS_POTION = potion("swiftness_potion", "Potion of swiftness", Rarity.UNCOMMON,
            30, "25% faster for 90 seconds", 0x8FE8FF, 0, 0, 0, help(Buffs.Kind.SPEED, 1.25, 90));
    public static final Good MIGHT_POTION = potion("might_potion", "Potion of might", Rarity.UNCOMMON, 36,
            "25% more damage for 90 seconds", 0xFF8A2A, 0, 0, 0, help(Buffs.Kind.DAMAGE, 1.25, 90));
    public static final Good STONESKIN_POTION = potion("stoneskin_potion", "Stoneskin potion", Rarity.RARE, 55,
            "A third less damage taken for 90 seconds", 0x9A9590, 0, 0, 0, help(Buffs.Kind.DEFENSE, 0.67, 90));
    public static final Good SHADOW_POTION = potion("shadow_potion", "Shadow draught", Rarity.RARE, 55,
            "Half as easily noticed for 2 minutes", 0x5A4A7A, 0, 0, 0, help(Buffs.Kind.STEALTH, 0.5, 120));
    public static final Good GILLS_POTION = potion("gills_potion", "Gillweed tonic", Rarity.UNCOMMON, 28,
            "Four times the breath under water for 5 minutes", 0x2FB8A0, 0, 0, 0, help(Buffs.Kind.BREATH, 4, 300));
    public static final Good SPRING_POTION = potion("spring_potion", "Springheel tonic", Rarity.UNCOMMON, 28,
            "Jumps half as high again for 2 minutes", 0xC8E84A, 0, 0, 0, help(Buffs.Kind.JUMP, 1.5, 120));
    public static final Good FORTUNE_POTION = potion("fortune_potion", "Draught of fortune", Rarity.EPIC, 120,
            "Half as many coins again for 5 minutes", 0xF0C030, 0, 0, 0, help(Buffs.Kind.COINS, 1.5, 300));
    public static final Good THORN_POTION = potion("thorn_potion", "Thornblood", Rarity.RARE, 60,
            "A third of every blow taken dealt back for 2 minutes", 0x7A2A4A, 0, 0, 0,
            help(Buffs.Kind.THORNS, 0.33, 120));
    public static final Good CRYSTAL_ELIXIR = potion("crystal_elixir", "Crystal elixir", Rarity.LEGENDARY, 300,
            "Health, mana and stamina filled; 3 mana a second for 5 minutes", 0xC86FFF, 200, 200, 200,
            help(Buffs.Kind.REGEN_MANA, 3, 300));

    // --- scrolls: found and bought, never brewed -----------------------------------------
    public static final Good SCROLL_FURY = scroll("scroll_of_fury", "Scroll of fury", Rarity.RARE, 90,
            "60% more damage for a minute", 0xE83A2A, 0, 0, null, help(Buffs.Kind.DAMAGE, 1.6, 60));
    public static final Good SCROLL_WARDING = scroll("scroll_of_warding", "Scroll of warding", Rarity.RARE, 90,
            "A ward that soaks up the next 120 damage", 0x6FB8FF, 0, 0, null, new Help(Buffs.Kind.WARD, 120, 0, 1));
    public static final Good SCROLL_FROST = scroll("scroll_of_frost", "Scroll of frost", Rarity.EPIC, 150,
            "Every foe within 14 m stands frozen for 8 seconds", 0xBFEFFF, 0, 0, "frost");
    public static final Good SCROLL_HASTE = scroll("scroll_of_haste", "Scroll of haste", Rarity.RARE, 80,
            "Half as fast again, at half the stamina, for a minute", 0x8FE8FF, 0, 0, null,
            help(Buffs.Kind.SPEED, 1.5, 60), help(Buffs.Kind.STAMINA_COST, 0.5, 60));
    public static final Good SCROLL_RENEWAL = scroll("scroll_of_renewal", "Scroll of renewal", Rarity.EPIC, 160,
            "Health and mana filled; 4 health a second for a minute", 0x4AC850, 200, 200, null,
            help(Buffs.Kind.REGEN_HEALTH, 4, 60));
    public static final Good SCROLL_VENOM = scroll("scroll_of_venom", "Scroll of venom", Rarity.RARE, 85,
            "Every blow poisons for half as much again, for 2 minutes", 0x7EE728, 0, 0, null,
            help(Buffs.Kind.POISON, 0.5, 120));
    public static final Good SCROLL_INSIGHT = scroll("scroll_of_insight", "Scroll of insight", Rarity.EPIC, 140,
            "Twice the experience for 10 minutes", 0xFFD27A, 0, 0, null, help(Buffs.Kind.XP, 2, 600));
    public static final Good SCROLL_STORM = scroll("scroll_of_the_storm", "Scroll of the storm", Rarity.LEGENDARY,
            320, "Lightning strikes every foe within 16 m for 60", 0xFFF07A, 0, 0, "storm");
    public static final Good SCROLL_RETURN = scroll("scroll_of_return", "Scroll of return", Rarity.UNCOMMON, 40,
            "Takes you back to where you came into this place", 0xB060FF, 0, 0, "return");

    // --- keys ------------------------------------------------------------------------------
    public static final Good RUSTY_KEY = key("rusty_key", "Rusty key", Rarity.COMMON, 10, 0x8A5A33);
    public static final Good IRON_KEY = key("iron_key", "Iron key", Rarity.COMMON, 20, 0x6E7480);
    public static final Good BRASS_KEY = key("brass_key", "Brass key", Rarity.UNCOMMON, 40, 0xD6AA5C);
    public static final Good SILVER_KEY = key("silver_key", "Silver key", Rarity.RARE, 80, 0xD8DCE4);
    public static final Good GOLD_KEY = key("gold_key", "Golden key", Rarity.EPIC, 160, 0xF0C030);
    public static final Good BONE_KEY = key("bone_key", "Bone key", Rarity.RARE, 80, 0xE8E0C8);
    public static final Good CRYSTAL_KEY = key("crystal_key", "Crystal key", Rarity.LEGENDARY, 400, 0x7FE8FF);
    public static final Good SKELETON_KEY = good("skeleton_key", "Skeleton key", Kind.KEY, Rarity.MYTHIC, 1500,
            "Opens any lock there is, and is never used up", 1, P_KEY, "metal", 0x439F9D, 0, 0, 0, null);

    /** The keys, in the order a door's property lists them. */
    public static final List<Good> KEYS = List.of(RUSTY_KEY, IRON_KEY, BRASS_KEY, SILVER_KEY, GOLD_KEY, BONE_KEY,
            CRYSTAL_KEY);

    // --- spawner eggs ------------------------------------------------------------------------
    public static final List<Good> EGGS = eggs();

    private static List<Good> eggs() {
        Object[][] all = {
                {"cow", "Cow", Rarity.COMMON, 30, 0xD8D0C8}, {"sheep", "Sheep", Rarity.COMMON, 25, 0xF0EEE8},
                {"pig", "Pig", Rarity.COMMON, 25, 0xE8A0A8}, {"horse", "Horse", Rarity.UNCOMMON, 120, 0x8A5A33},
                {"dog", "Dog", Rarity.UNCOMMON, 60, 0xB08A5A}, {"cat", "Cat", Rarity.UNCOMMON, 60, 0xE0903A},
                {"rabbit", "Rabbit", Rarity.COMMON, 15, 0xC8B49A}, {"fox", "Fox", Rarity.UNCOMMON, 40, 0xC0601A},
                {"deer", "Deer", Rarity.UNCOMMON, 45, 0xA0663A}, {"duck", "Duck", Rarity.COMMON, 15, 0x3A7A4A},
                {"goose", "Goose", Rarity.COMMON, 20, 0xE8E8E0}, {"owl", "Owl", Rarity.UNCOMMON, 45, 0x8A7A5A},
                {"eagle", "Eagle", Rarity.RARE, 90, 0x5A4030}, {"peacock", "Peacock", Rarity.RARE, 90, 0x2A6AC8},
                {"bear", "Bear", Rarity.RARE, 110, 0x4A3020}, {"boar", "Wild boar", Rarity.RARE, 90, 0x4D2A14},
                {"buffalo", "Buffalo", Rarity.RARE, 130, 0x2D1A0E},
                {"bortzal", "Bortzal", Rarity.RARE, 140, 0xC8B428},
                {"khorigale", "Khorigale", Rarity.EPIC, 180, 0xD86A8A},
                {"drurinean", "Drurinean", Rarity.EPIC, 180, 0xB89AE0},
                {"risen_walker", "Risen", Rarity.RARE, 100, 0x7A9A6A},
                {"vernees", "Vérnees", Rarity.LEGENDARY, 450, 0xC0281E},
                {"dragon", "Dragon", Rarity.MYTHIC, 5000, 0x439F9D}};
        List<Good> out = new ArrayList<>();
        for (Object[] e : all) out.add(egg((String) e[0], (String) e[1], (Rarity) e[2], (int) e[3], (int) e[4]));
        return List.copyOf(out);
    }

    // --- trinkets ----------------------------------------------------------------------------
    public static final Good CRYSTAL_BALL = good("crystal_ball", "Crystal ball", Kind.TRINKET, Rarity.EPIC, 220,
            "Gaze into it: every foe about is marked for a minute", 1, P_ORB, "crystal", 0x9B7BFF, 0, 0, 0, null);
    public static final Good CRYSTAL_SHIELD = good("crystal_shield", "Crystal shield", Kind.TRINKET, Rarity.EPIC,
            260, "Raise it: a ward that soaks up 80 damage. Mends itself in a minute", 1, P_SHIELD, "face", 0x7FE8FF,
            0, 0, 0, null);
    public static final Good DRINKING_HORN = good("drinking_horn", "Drinking horn", Kind.TRINKET, Rarity.UNCOMMON,
            45, "Three swigs: +35 stamina, +10 health each. Fills itself at any water", 1, P_HORN, "metal", 0xD6AA5C,
            10, 0, 35, null);
    public static final Good GILDED_HORN = good("gilded_horn", "Gilded horn of plenty", Kind.TRINKET,
            Rarity.LEGENDARY, 600, "Five swigs: +60 stamina, +30 health each. Fills itself at any water", 1, P_HORN,
            "metal", 0xF0C030, 30, 0, 60, null);

    // --- fishing -----------------------------------------------------------------------------
    public static final Good WILLOW_ROD = rod("willow_rod", "Willow rod", Rarity.COMMON, 25, 0xA0825A,
            "A fishing rod: cast it into any water");
    public static final Good REED_ROD = rod("reed_rod", "Reedcane rod", Rarity.UNCOMMON, 70, 0x9AB85A,
            "A fishing rod: the fish take a little longer over the bait");
    public static final Good BRASS_ROD = rod("brass_rod", "Brass-reel rod", Rarity.RARE, 180, 0xD6AA5C,
            "A fishing rod with a reel: an easier catch, better fish");
    public static final Good CLOCKWORK_ROD = rod("clockwork_rod", "Clockwork rod", Rarity.EPIC, 420, 0xB07A3A,
            "A fishing rod that winds itself: quick bites, rare fish");
    public static final Good CRYSTAL_ROD = rod("crystal_rod", "Crystal rod", Rarity.LEGENDARY, 1100, 0x7FE8FF,
            "A fishing rod the fish cannot help but come to");

    public static final List<Good> RODS = List.of(WILLOW_ROD, REED_ROD, BRASS_ROD, CLOCKWORK_ROD, CRYSTAL_ROD);

    public static final Good MINNOW = fish("minnow", "Minnow", Rarity.COMMON, 3, 0xB8C4CC, 6, 4, 0);
    public static final Good TROUT = fish("trout", "Brook trout", Rarity.COMMON, 6, 0x8A9A6A, 14, 8, 0);
    public static final Good PERCH = fish("perch", "Marsh perch", Rarity.UNCOMMON, 12, 0x9AA83A, 18, 10, 0);
    public static final Good PIKE = fish("pike", "Pike", Rarity.RARE, 30, 0x5A7A4A, 28, 16, 0);
    public static final Good SEA_BASS = fish("sea_bass", "Sea bass", Rarity.UNCOMMON, 14, 0x6A8AA8, 20, 12, 0);
    public static final Good GOLDSCALE = fish("goldscale", "Goldscale carp", Rarity.EPIC, 90, 0xF0C030, 30, 20, 20);
    public static final Good EMBERFIN = fish("emberfin", "Emberfin", Rarity.RARE, 40, 0xE8582A, 20, 30, 10);
    public static final Good CRYSTAL_EEL = fish("crystal_eel", "Crystal eel", Rarity.LEGENDARY, 260, 0xC86FFF, 40,
            30, 60);

    public static final List<Good> FISH = List.of(MINNOW, TROUT, PERCH, PIKE, SEA_BASS, GOLDSCALE, EMBERFIN,
            CRYSTAL_EEL);

    // --- meals: cooked at a cauldron ---------------------------------------------------------
    private static Good meal(String id, String name, Rarity r, int price, String pickup, String part, int rgb,
                             double health, double stamina, double mana) {
        return good(id, name, Kind.MEAL, r, price, "Cooked at a cauldron", 10, pickup, part, rgb, health, mana,
                stamina, null);
    }

    public static final Good ROAST_MEAT = meal("roast_meat", "Roast meat", Rarity.COMMON, 8, "hunt_meat", "meat",
            0x8A4A2A, 40, 15, 0);
    public static final Good GRILLED_FISH = meal("grilled_fish", "Grilled fish", Rarity.COMMON, 9, P_FISH, "body",
            0xC8A070, 35, 25, 0);
    public static final Good HUNTERS_STEW = meal("hunters_stew", "Hunter's stew", Rarity.UNCOMMON, 22, P_POTION,
            "liquid", 0xA8602A, 70, 45, 0);
    public static final Good BERRY_TART = meal("berry_tart", "Berry tart", Rarity.UNCOMMON, 16, Food.BERRIES, "fruit",
            0xB83A6A, 25, 35, 15);

    // --- packs -------------------------------------------------------------------------------
    public static final Good SATCHEL = good("satchel", "Satchel", Kind.PACK, Rarity.UNCOMMON, 150,
            "5 more inventory slots, for good", 1, P_PACK, "cloth", 0x8A6A3A, 0, 0, 0, "5");
    public static final Good BACKPACK = good("backpack", "Backpack", Kind.PACK, Rarity.RARE, 400,
            "10 more inventory slots, for good", 1, P_PACK, "cloth", 0x3A6A8A, 0, 0, 0, "10");
    public static final Good EXPLORERS_PACK = good("explorers_pack", "Explorer's pack", Kind.PACK, Rarity.EPIC,
            1000, "15 more inventory slots, for good", 1, P_PACK, "cloth", 0x8A3A6A, 0, 0, 0, "15");

    public static final List<Good> PACKS = List.of(SATCHEL, BACKPACK, EXPLORERS_PACK);

    // --- makings -----------------------------------------------------------------------------
    public static final Good EMPTY_BOTTLE = making("empty_bottle", "Empty bottle", Rarity.COMMON, 4,
            "Every potion wants one", P_POTION, "liquid", 0xDDE8EE);
    public static final Good PARCHMENT = making("parchment", "Parchment", Rarity.COMMON, 5,
            "For scrolls and maps", P_SCROLL, "ribbon", 0xE8D8A8);
    public static final Good INK = making("ink", "Gall ink", Rarity.UNCOMMON, 12, "For scrolls", P_BOTTLE, "liquid",
            0x1E1E2E);
    public static final Good TWINE = making("twine", "Twine", Rarity.COMMON, 3, "Line, lashings, stitching", P_ROD,
            "wood", 0xC8B48A);
    public static final Good FISH_HOOK = making("fish_hook", "Fish hook", Rarity.COMMON, 4, "For a rod", P_KEY,
            "metal", 0x9A9590);
    public static final Good BRASS_GEAR = making("brass_gear", "Brass gear", Rarity.UNCOMMON, 14,
            "Clockwork's first need", P_INGOT, "metal", 0xD6AA5C);
    public static final Good IRON_BAR = making("iron_bar", "Iron bar", Rarity.COMMON, 10, "Keys, hooks, fittings",
            P_INGOT, "metal", 0x6E7480);
    public static final Good CRYSTAL_DUST = making("crystal_dust", "Crystal dust", Rarity.RARE, 30,
            "Ground crystal: the rarest brews and makings", P_CRYSTAL, "crystal", 0xC86FFF);
    public static final Good LEATHER = making("leather", "Leather", Rarity.COMMON, 8, "Tanned hide: packs and straps",
            P_PACK, "cloth", 0x8A5A33);
    public static final Good CLOTH = making("cloth", "Cloth", Rarity.COMMON, 6, "Woven wool: packs and wrappings",
            P_PACK, "cloth", 0xD8D0C8);

    private Goods() {}

    /** Every good there is. */
    public static List<Good> all() { return List.copyOf(BY_ID.values()); }

    public static List<ItemDef> defs() { return List.copyOf(DEFS); }

    public static List<Good> ofKind(Kind k) {
        return BY_ID.values().stream().filter(g -> g.kind() == k).toList();
    }

    /** The good called {@code id}, or null. */
    public static Good get(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static Good of(ItemDef def) {
        return def == null ? null : BY_ID.get(def.id());
    }

    public static Good of(Item item) {
        return item == null ? null : of(item.def());
    }

    public static ItemDef byId(String id) {
        Good g = get(id);
        return g == null ? null : g.def();
    }

    public static boolean is(ItemDef def) {
        return of(def) != null;
    }

    /** One of {@code g}, as it is found. */
    public static Item item(Good g) {
        return Item.of(g.def());
    }

    /**
     * How rare anything with no tiers and no rarity of its own is: a good's
     * own; the rarer pickings, pelts and feathers; the ingot and the scroll -
     * everything else Common.
     */
    public static Rarity rarityOf(ItemDef def) {
        Good g = of(def);
        if (g != null) return g.rarity();
        return switch (def.id()) {
            case "crystal_bloom", "upgrade_ingot", "peacock_feather", "eagle_feather" -> Rarity.RARE;
            case "sunfruit", "sea_buckthorn", "thornberries", "bear_pelt", "bear_meat", "antler", "fox_pelt",
                 "macaw_feather", "flamingo_feather", "large_crystal", "reshuffle_scroll", "raw_venison" ->
                    Rarity.UNCOMMON;
            default -> Rarity.COMMON;
        };
    }
}
