package com.larsons.game.world;

import com.larsons.game.combat.DiceRoll;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.sprite.fallback.FallbackSprites;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The shields: twelve, each carried on the off-hand arm - the left arm of a
 * right-handed character - with any one-handed weapon in the other hand (the
 * sword, the mace, the wand, the dagger). With a shield on her arm she fights
 * in the sword-and-shield moves, whatever one-handed weapon she holds
 * ({@link Player#stance}); a two-handed weapon (the battle axe, the staff, the
 * bow, the crossbow) puts it away while she holds it.
 *
 * <p><b>Raised</b> (block: right mouse), a shield rolls its <b>block
 * dice</b> against every blow that comes at her <b>from the front</b> -
 * within {@link Shield#arc} degrees either side of the way she faces -
 * before her armour rolls its own ({@link Player#struck(double, com.larsons.game.math.Vec3, boolean)}):
 * extra guard on top of the block's half, from in front of her only. A blow
 * from the side or from behind gets past it. Each blow it holds costs
 * stamina ({@link #STAMINA_PER_POINT} for each point blocked); out of
 * stamina, the guard breaks and the shield does not roll. Its bash (V, or
 * block + attack) rolls its own <b>bash dice</b>, not the weapon's.
 *
 * <p>Each but the round shield has a gift of its own ({@link Trait}).
 * Shields are worn things ({@link Wearable}: the hands' slots), so they come
 * out of chests at their rarity, are carried in the bag, put on and off from
 * the inventory (right-click) and the wardrobe, and the Tailor makes shields
 * of one's own from them - with their own block dice, tiers, special
 * effects and colours ({@link Forged}).
 *
 * <p>Their art is 3D-Modeling's {@code shield_shapes.py}, held where the
 * round shield is ({@code feminine_model_shields.py}).
 */
public final class Shields {

    private Shields() {}

    /** What a shield does past its dice. */
    public enum Trait {
        NONE("", ""),
        LIGHT("Light", "its blocks cost no stamina"),
        QUICK("Quick", "a blow met in the moment it is raised is parried outright"),
        STUDDED("Studded", "its bash rolls two more dice"),
        STEADY("Steady", "its blocks cost half the stamina"),
        ARROW_WARD("Arrow ward", "a shot from the front is stopped whole"),
        THORNS("Thorns", "a blow it blocks is dealt back to whoever struck it"),
        BULWARK("Bulwark", "it covers her sides as well as her front"),
        CLOCKWORK("Clockwork", "its bash strikes all round her"),
        LANTERN("Lantern", "it lights the dark round her, as the brass lantern does"),
        MIRROR("Mirror", "a shot or a spell it blocks flies back at whoever loosed it"),
        AEGIS("Aegis", "half of what it blocks comes back as mana");

        private final String label, said;

        Trait(String label, String said) {
            this.label = label;
            this.said = said;
        }

        public String label() { return label; }

        /** What it does, as the tooltip says it: "its blocks cost no stamina". */
        public String said() { return said; }
    }

    /**
     * One kind of shield.
     *
     * @param id        its id and its folder ({@code carry_left/<id>})
     * @param rarity    how rare it is: what it drops as
     * @param dice      how many block dice it rolls
     * @param sides     of how many sides
     * @param arc       how far round from the way she faces it covers, degrees either side
     * @param bashDice  how many dice its bash rolls
     * @param bashSides of how many sides
     * @param trait     its gift, or {@link Trait#NONE}
     * @param about     a line on what it is
     */
    public record Shield(String id, String name, Rarity rarity, int dice, int sides, int arc, int bashDice,
                         int bashSides, Trait trait, String about) {

        /** Its block dice: "2d8". */
        public String notation() {
            return dice + Forged.die(sides);
        }

        /** Its bash dice: "1d6" (a studded one's two more). */
        public String bashNotation() {
            return (bashDice + (trait == Trait.STUDDED ? STUDDED_DICE : 0)) + Forged.die(bashSides);
        }
    }

    /** How long after it is raised a Quick shield parries what it meets, in seconds. */
    public static final double QUICK_WINDOW = 0.3;
    /** The stamina a blow held off by a shield costs, for each point it blocked. */
    public static final double STAMINA_PER_POINT = 0.5;
    /** A Studded shield's bash: this many more dice. */
    public static final int STUDDED_DICE = 2;
    /** How much of what an Aegis blocks comes back as mana. */
    public static final double AEGIS_MANA = 0.5;

    private static final Map<String, Shield> BY_ID = new LinkedHashMap<>();
    private static final Map<String, ItemDef> DEFS = new LinkedHashMap<>();

    static {
        add("round_shield", "Round shield", Rarity.COMMON, 2, 6, 60, 1, 6, Trait.NONE,
                "Oak planks, an iron rim and boss: the shield she always had.");
        add("wicker_shield", "Wicker shield", Rarity.COMMON, 1, 6, 60, 1, 4, Trait.LIGHT,
                "Willow woven on a hoop, a wooden boss.");
        add("buckler", "Buckler", Rarity.COMMON, 1, 8, 45, 1, 6, Trait.QUICK,
                "A small steel dish with a brass rim, held up to meet a blow.");
        add("targe", "Targe", Rarity.UNCOMMON, 2, 6, 50, 1, 6, Trait.STUDDED,
                "Leather over wood, studded with brass in rings round a spiked boss.");
        add("heater_shield", "Heater shield", Rarity.UNCOMMON, 2, 8, 60, 1, 8, Trait.STEADY,
                "The knight's shield: painted, rimmed in steel, a chevron on its face.");
        add("kite_shield", "Kite shield", Rarity.RARE, 2, 8, 70, 1, 8, Trait.ARROW_WARD,
                "Tall and pointed, a cross on its face: long enough to stand behind.");
        add("spiked_shield", "Spiked shield", Rarity.RARE, 2, 8, 60, 2, 8, Trait.THORNS,
                "Iron-banded planks bristling with spikes.");
        add("tower_shield", "Tower shield", Rarity.EPIC, 3, 10, 95, 1, 10, Trait.BULWARK,
                "A wall of planks, curved round her, its brass boss the size of a plate.");
        add("gear_shield", "Gear shield", Rarity.EPIC, 3, 8, 60, 1, 10, Trait.CLOCKWORK,
                "A great brass gear with copper cogs turning in it.");
        add("lantern_shield", "Lantern shield", Rarity.EPIC, 2, 10, 60, 1, 8, Trait.LANTERN,
                "A steel shield with a lantern on its face, lit.");
        add("mirror_shield", "Mirror shield", Rarity.LEGENDARY, 3, 10, 60, 1, 8, Trait.MIRROR,
                "Polished silver in a gilded rim: what is loosed at it goes back.");
        add("crystal_aegis", "Crystal aegis", Rarity.LEGENDARY, 4, 8, 65, 1, 10, Trait.AEGIS,
                "Facets of glowing crystal set in silver.");
    }

    private static void add(String id, String name, Rarity rarity, int dice, int sides, int arc, int bashDice,
                            int bashSides, Trait trait, String about) {
        Shield s = new Shield(id, name, rarity, dice, sides, arc, bashDice, bashSides, trait, about);
        BY_ID.put(id, s);
        // worn in a hand: the off hand, which is the left of a right-handed character, the right of a left-handed one
        Wearable w = new Wearable(rarity, List.of(Slot.CARRY_LEFT, Slot.CARRY_RIGHT), List.of(id), null, null, null);
        DEFS.put(id, new ItemDef(id, name, null, 0.9, () -> FallbackSprites.wearableIcon(rarity.rgb()),
                Stance.SWORD, w));
    }

    /** Every shield, by rarity. */
    public static List<Shield> all() {
        return List.copyOf(BY_ID.values());
    }

    /** Every shield's item, by rarity ({@link Wearables#ALL} has them all). */
    public static List<ItemDef> defs() {
        return List.copyOf(DEFS.values());
    }

    /** The shield {@code id} (its folder, styled or not), or null. */
    public static Shield byId(String id) {
        return id == null ? null : BY_ID.get(Wardrobe.Style.base(id));
    }

    /** The item of shield {@code id}, or null. */
    public static ItemDef def(String id) {
        return id == null ? null : DEFS.get(Wardrobe.Style.base(id));
    }

    /** The round shield's item. */
    public static ItemDef round() {
        return DEFS.get("round_shield");
    }

    /** Whether {@code folder} (styled or not) is a shield's. */
    public static boolean isFolder(String folder) {
        return folder != null && BY_ID.containsKey(Wardrobe.Style.base(folder));
    }

    /** Whether {@code d} is a shield. */
    public static boolean is(ItemDef d) {
        return d != null && DEFS.get(d.id()) == d;
    }

    /** Whether {@code item} is a shield, or one the Tailor made from one. */
    public static boolean is(Item item) {
        return item != null && is(item.def());
    }

    /** What {@code item} is made as (its base for one the Tailor made), or null when it is no shield. */
    public static Shield of(Item item) {
        return item == null || !is(item.def()) ? null : BY_ID.get(item.def().id());
    }

    // --- carrying it ---------------------------------------------------------------------

    /** The hand a shield is carried in: the off hand - the left of a right-handed character, the right of a left-handed one. */
    public static Slot hand(Wardrobe wardrobe) {
        return wardrobe != null && wardrobe.leftHanded() ? Slot.CARRY_RIGHT : Slot.CARRY_LEFT;
    }

    /** The folder of the shield {@code wardrobe} carries (either hand), or null. */
    public static String carriedFolder(Wardrobe wardrobe) {
        if (wardrobe == null) return null;
        for (Slot s : new Slot[]{hand(wardrobe), Slot.CARRY_LEFT, Slot.CARRY_RIGHT}) {
            String f = wardrobe.get(s);
            if (isFolder(f)) return f;
        }
        return null;
    }

    /** The hand {@code wardrobe}'s shield is in, or null when it carries none. */
    public static Slot carriedIn(Wardrobe wardrobe) {
        if (wardrobe == null) return null;
        for (Slot s : new Slot[]{hand(wardrobe), Slot.CARRY_LEFT, Slot.CARRY_RIGHT}) {
            if (isFolder(wardrobe.get(s))) return s;
        }
        return null;
    }

    /**
     * The shield {@code wardrobe} carries, as the item in {@code inventory}
     * it is (one the Tailor made, the best of the kind), else as it is
     * found; null when it carries none.
     */
    public static Item carried(Wardrobe wardrobe, Inventory inventory) {
        Slot s = carriedIn(wardrobe);
        if (s == null) return null;
        Item it = Armour.item(wardrobe, inventory, s);
        if (it != null && is(it.def())) return it;
        ItemDef d = def(wardrobe.get(s));
        return d == null ? null : Item.of(d);
    }

    /**
     * Put {@code item} on her off-hand arm (null: take off whatever shield is
     * there): its folder in the off hand, worn as the item it is (a Tailor's
     * shield as itself). Anything else in the off hand - but a sword - makes
     * way. Returns whether anything changed.
     */
    public static boolean carry(Wardrobe wardrobe, Item item) {
        if (wardrobe == null) return false;
        Slot was = carriedIn(wardrobe);
        if (item == null) {
            if (was == null) return false;
            wardrobe.clear(was);
            return true;
        }
        if (!is(item.def())) return false;
        Slot hand = hand(wardrobe);
        if (was != null && was != hand) wardrobe.clear(was);
        String id = item.forged() != null ? Armour.forgedLook(item.forged()) : item.def().id();
        if (wardrobe.wearing(hand, item.def().id()) && id.equals(wardrobe.id(hand))) return false;
        wardrobe.set(hand, item.def().id());
        wardrobe.setId(hand, id);
        return true;
    }

    /** Whether {@code item} - this very one, or another forged the same - is the shield {@code wardrobe} carries. */
    public static boolean carrying(Wardrobe wardrobe, Item item) {
        if (item == null || !is(item.def())) return false;
        Slot s = carriedIn(wardrobe);
        if (s == null || !wardrobe.wearing(s, item.def().id())) return false;
        String id = wardrobe.id(s);
        if (item.forged() != null) return Armour.forgedLook(item.forged()).equals(id);
        return id == null || id.equals(item.def().id());
    }

    /** The weapons a shield is used with: the one-handed ones (or none: empty hands). */
    public static final String ONE_HANDED = "a sword, mace, wand or dagger";

    /** What is said of a shield put away: how to have it out again. */
    public static final String USE_IT = "Take up " + ONE_HANDED + ", or empty your hands, to use it";

    /**
     * Why the shield {@code p} carries is put away - not drawn, not to be
     * raised - or null when it is up to be used (with a one-handed weapon,
     * or nothing, in her hand), or she carries none: what is in her hands
     * takes both (the battle axe, the staff, the bow, the crossbow). Said as
     * the end of a sentence ("the battle axe takes both hands").
     */
    public static String awayBecause(Player p) {
        if (p == null || !Stance.carriesShield(p.wardrobe()) || p.stance() == Stance.SWORD) return null;
        return "the " + p.weaponStance().label().toLowerCase(Locale.ROOT) + " takes both hands";
    }

    // --- what it does ---------------------------------------------------------------------

    /** Its trait: its kind's (a Tailor's shield has what it was made from has). */
    public static Trait trait(Item item) {
        Shield s = of(item);
        return s == null ? Trait.NONE : s.trait();
    }

    /** How far round from the way she faces it covers, degrees either side. */
    public static double arc(Item item) {
        Shield s = of(item);
        return s == null ? 0 : s.arc();
    }

    /** Its block dice: its kind's, or a Tailor's shield's own at its tier ("2d8", "3d10 + 1d4"). */
    public static String guardText(Item item) {
        if (item == null) return "";
        if (item.forged() != null) return item.forged().diceText();
        Shield s = of(item);
        return s == null ? "" : s.notation();
    }

    /** Roll its block dice. */
    public static DiceRoll roll(Item item, Random random) {
        if (item.forged() != null) {
            Forged f = item.forged();
            return DiceRoll.roll(f.dice(), f.sides(), f.extra(), random);
        }
        Shield s = of(item);
        return DiceRoll.roll(s == null ? 1 : s.dice(), s == null ? 4 : s.sides(), random);
    }

    /** How many dice its bash rolls (a Studded one's two more), and of how many sides: {n, sides}. */
    public static int[] bash(Item item) {
        Shield s = of(item);
        if (s == null) return new int[]{1, 4};
        return new int[]{s.bashDice() + (s.trait() == Trait.STUDDED ? STUDDED_DICE : 0), s.bashSides()};
    }

    /**
     * Whether a blow from {@code from} comes at someone at {@code feet} facing
     * {@code heading} from inside {@code arc} degrees either side of the way
     * they face. Null: from nowhere in particular - not from the front.
     */
    public static boolean inFront(com.larsons.game.math.Vec3 feet, double heading, com.larsons.game.math.Vec3 from,
                                  double arc) {
        if (from == null || feet == null) return false;
        double dx = from.x() - feet.x(), dz = from.z() - feet.z(), d = Math.hypot(dx, dz);
        if (d < 1e-6) return true;
        com.larsons.game.math.Vec3 f = com.larsons.game.sprite.SpriteView.headingVector(heading);
        double cos = (dx * f.x() + dz * f.z()) / d;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos)))) <= arc + 1e-9;
    }

    /** The stamina a blow it held off {@code blocked} costs (a Light shield's none, a Steady one's half). */
    public static double staminaFor(Item item, double blocked) {
        return switch (trait(item)) {
            case LIGHT -> 0;
            case STEADY -> blocked * STAMINA_PER_POINT / 2;
            default -> blocked * STAMINA_PER_POINT;
        };
    }

    /** What it is, for its tooltip, a line each: its dice, its cover, its bash, its gift. */
    public static List<String> describe(Item item) {
        List<String> out = new ArrayList<>();
        Shield s = of(item);
        if (s == null) return out;
        out.add("Shield (off hand): raised, rolls " + guardText(item) + " to block a blow from the front");
        out.add(String.format(Locale.ROOT, "Covers %d° either side · bash %s", s.arc(), s.bashNotation()));
        if (s.trait() != Trait.NONE) out.add(s.trait().label() + ": " + s.trait().said());
        return out;
    }

    /** "Kite shield, Rare: 2d8, ±70°, Arrow ward" - for the How to play page. */
    public static String line(Shield s) {
        return s.name() + " (" + s.rarity().label() + "): " + s.notation() + ", ±" + s.arc() + "°"
                + (s.trait() == Trait.NONE ? "" : ", " + s.trait().label() + " - " + s.trait().said());
    }
}
