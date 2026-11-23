package com.larsons.game.world;

import com.larsons.game.combat.DiceRoll;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Wardrobe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Armour rolls dice to block a blow.
 *
 * <p>Every blow that lands on her - a foe's swing, an arrow, a bite, a
 * flier's dive - strikes one place: her head, her torso, her arms, her legs
 * or her feet, drawn by how big a mark each is ({@link Rules#weights}: the
 * torso most often). Every piece of armour she wears there rolls its dice,
 * and what they come to is taken off the blow; what is left hurts her
 * ({@link Player#struck}). A blow fully blocked does nothing. The roll shows
 * just above the hotbar ({@code CombatHud.drawArmour}). With {@link
 * Rules#everyPiece} (combat.json's {@code armour.everyPieceRolls}) every piece
 * she wears rolls at every blow, wherever it lands.
 *
 * <p>A piece rolls as many dice as its rarity says ({@link Rules#dice}: a
 * Rare helmet one, an Epic one two), each with as many sides as the piece is
 * stout ({@link #sides}): leather a d4, mail a d6, iron plate a d8, steel a
 * d10, a great helm a d10. An upgrade tier takes it up a rarity - more dice;
 * a piece forged in the Forge rolls its own dice, whatever they are.
 *
 * <pre>
 *   head    helmets, helms, the coif, the leather cap, the gorget
 *   torso   gambeson, mail, scale mail, the jerkin, chestplates
 *   arms    gauntlets, bracers (a pair rolls once)
 *   legs    mail leggings, leg plating, greaves
 *   feet    sabatons
 * </pre>
 */
public final class Armour {

    private Armour() {}

    /** Where a blow lands. */
    public enum Location {
        HEAD("head", "Head", 15), TORSO("torso", "Torso", 40), ARMS("arms", "Arms", 15), LEGS("legs", "Legs", 20),
        FEET("feet", "Feet", 10);

        private final String key, label;
        private final int weight;

        Location(String key, String label, int weight) {
            this.key = key;
            this.label = label;
            this.weight = weight;
        }

        public String key() { return key; }

        public String label() { return label; }

        /** How often a blow lands here, out of the five's sum (its default; combat.json can change it). */
        public int weight() { return weight; }

        public static Location byKey(String key) {
            for (Location l : values()) if (l.key.equalsIgnoreCase(key)) return l;
            return null;
        }
    }

    /** How many sides each piece's dice have, by its folder: how stout it is. */
    private static final Map<String, Integer> SIDES = Map.ofEntries(
            // head
            Map.entry("leather_cap", 4), Map.entry("chain_coif", 6), Map.entry("kettle_hat", 6),
            Map.entry("horned_helm", 8), Map.entry("iron_helmet", 8), Map.entry("steel_helmet", 10),
            Map.entry("great_helm", 10), Map.entry("gorget", 6),
            // torso
            Map.entry("gambeson", 4), Map.entry("leather_jerkin", 6), Map.entry("chainmail_shirt", 6),
            Map.entry("scale_mail", 8), Map.entry("iron_chestplate", 8), Map.entry("steel_chestplate", 10),
            // arms
            Map.entry("leather_bracers", 4), Map.entry("iron_gauntlets", 8), Map.entry("steel_gauntlets", 10),
            // legs
            Map.entry("leather_greaves", 4), Map.entry("chainmail_pants", 6), Map.entry("iron_leg_plating", 8),
            Map.entry("steel_leg_plating", 10),
            // feet
            Map.entry("sabatons", 8));
    /** The pieces drawn from another's art in another metal, by id: bronze is softer than iron, gilding finer. */
    private static final Map<String, Integer> SIDES_OF = Map.ofEntries(
            Map.entry("bronze_helmet", 6), Map.entry("bronze_chestplate", 6), Map.entry("bronze_leg_plating", 6),
            Map.entry("bronze_gauntlets", 6), Map.entry("bronze_kettle_hat", 4), Map.entry("blackened_jerkin", 8),
            Map.entry("blackened_sabatons", 10), Map.entry("gilded_scale_mail", 10),
            Map.entry("gilded_great_helm", 12));

    /** How many sides {@code d}'s dice have: 0 when it is not armour. */
    public static int sides(ItemDef d) {
        if (d == null || !d.isWearable()) return 0;
        Integer own = SIDES_OF.get(d.id());
        if (own != null) return own;
        for (String f : d.wearable().folders()) {
            Integer s = SIDES.get(f);
            if (s != null) return s;
        }
        return 0;
    }

    /** Where what is worn in {@code slot} guards: null for a slot no armour goes in. */
    public static Location location(Slot slot) {
        return switch (slot) {
            case HAT, NECKLACE -> Location.HEAD;
            case SHIRT, CHAINMAIL, CHESTPLATE -> Location.TORSO;
            case GLOVES, WRISTWEAR, WRISTWEAR_LEFT -> Location.ARMS;
            case PANTS, CHAINMAIL_LEGS, LEG_PLATING -> Location.LEGS;
            case SHOES -> Location.FEET;
            default -> null;
        };
    }

    /**
     * combat.json's {@code armour}: the dice a piece of each rarity rolls,
     * how often a blow lands where, whether every piece rolls at every blow,
     * and the least a blow that lands still does.
     */
    public record Rules(Map<Rarity, Integer> dice, Map<Location, Integer> weights, boolean everyPiece,
                        double minDamage) {

        public Rules {
            dice = Collections.unmodifiableMap(new EnumMap<>(dice));
            weights = Collections.unmodifiableMap(new EnumMap<>(weights));
            minDamage = Math.max(0, minDamage);
        }

        /** The defaults: one die a piece up to Rare, two at Epic and Legendary, three at Mythic. */
        public static Rules defaults() {
            Map<Rarity, Integer> d = new EnumMap<>(Rarity.class);
            int[] standard = {1, 1, 1, 2, 2, 3};
            for (Rarity r : Rarity.values()) d.put(r, standard[r.ordinal()]);
            Map<Location, Integer> w = new EnumMap<>(Location.class);
            for (Location l : Location.values()) w.put(l, l.weight());
            return new Rules(d, w, false, 0);
        }

        /** These rules with every blow landing at {@code at} (null: as they were). */
        public Rules only(Location at) {
            if (at == null) return this;
            Map<Location, Integer> w = new EnumMap<>(Location.class);
            for (Location l : Location.values()) w.put(l, l == at ? 1 : 0);
            return new Rules(dice, w, everyPiece, minDamage);
        }

        /** These rules with every piece rolling at every blow, or not. */
        public Rules everyPiece(boolean on) {
            return new Rules(dice, weights, on, minDamage);
        }

        /** How many dice a piece of {@code rarity} rolls. */
        public int dice(Rarity rarity) {
            return Math.max(1, dice.getOrDefault(rarity == null ? Rarity.COMMON : rarity, 1));
        }

        /** Where a blow lands: by the weights (none weighted: the torso). */
        public Location land(Random random) {
            int sum = 0;
            for (Location l : Location.values()) sum += Math.max(0, weights.getOrDefault(l, 0));
            if (sum <= 0) return Location.TORSO;
            int at = random.nextInt(sum);
            for (Location l : Location.values()) {
                at -= Math.max(0, weights.getOrDefault(l, 0));
                if (at < 0) return l;
            }
            return Location.TORSO;
        }
    }

    /** One piece she wears: where it is worn, what it is (its tier, or what it was forged as), where it guards. */
    public record Piece(Slot slot, Item item, Location at) {

        public ItemDef def() { return item.def(); }

        /** The dice it rolls under {@code rules}: its own if it was forged, else so many of its sides. */
        public String notation(Rules rules) {
            Forged f = item.forged();
            if (f != null) return f.diceText();
            return rules.dice(item.rarity()) + Forged.die(sides(item.def()));
        }

        /** Roll its dice. */
        public DiceRoll roll(Rules rules, Random random) {
            Forged f = item.forged();
            if (f != null) return DiceRoll.roll(f.dice(), f.sides(), f.extra(), random);
            return DiceRoll.roll(rules.dice(item.rarity()), Math.max(2, sides(item.def())), random);
        }
    }

    /**
     * What a forged piece is worn as ({@link Wardrobe#id} of its slot; in an
     * older save, its colour): {@code forged:<id>}, the id of what the Forge made.
     */
    public static String forgedLook(Forged f) {
        return Forged.PREFIX + f.id();
    }

    /** The forged piece's id a slot's colour names ({@link #forgedLook}), or null. */
    public static String forgedId(String colour) {
        if (colour == null || !colour.startsWith(Forged.PREFIX)) return null;
        String id = colour.substring(Forged.PREFIX.length());
        int c = id.indexOf(':');
        return c < 0 ? id : id.substring(0, c);
    }

    /**
     * What a slot's colour says the piece is worn as, beyond its folder: a
     * tint ({@link Wearables#TINTS}) or a forged piece's look - neither is a
     * colour option of the art.
     */
    public static boolean look(String colour) {
        return Wearables.tint(colour) || forgedId(colour) != null;
    }

    /**
     * The armour {@code wardrobe} wears, piece by piece, as the items in
     * {@code inventory} (null: none - what the folk wear, as found): a forged
     * piece the one forged (its tier now, the highest she carries), any other
     * the best tier of it she carries, else as it is found. A pair of bracers
     * on both wrists is one piece.
     */
    public static List<Piece> worn(Wardrobe wardrobe, Inventory inventory) {
        List<Piece> out = new ArrayList<>();
        if (wardrobe == null) return out;
        for (Slot slot : Slot.values()) {
            Location at = location(slot);
            if (at == null) continue;
            Item item = item(wardrobe, inventory, slot);
            if (item == null || sides(item.def()) <= 0) continue;
            if (slot == Slot.WRISTWEAR_LEFT) {
                boolean pair = false;
                for (Piece p : out) pair |= p.slot() == Slot.WRISTWEAR && p.def() == item.def();
                if (pair) continue;
            }
            out.add(new Piece(slot, item, at));
        }
        return out;
    }

    /** The item worn on top in {@code slot} (see {@link #worn}), or null when nothing is worn there or no item is it. */
    public static Item item(Wardrobe wardrobe, Inventory inventory, Slot slot) {
        return item(inventory, slot, wardrobe.get(slot), wardrobe.colour(slot), wardrobe.id(slot));
    }

    /**
     * The item worn in {@code slot} as its armour ({@code armour}) or as its
     * clothes ({@link Wardrobe#get(Slot, boolean)}: a necklace under a gorget
     * is worn too), or null.
     */
    public static Item item(Wardrobe wardrobe, Inventory inventory, Slot slot, boolean armour) {
        return item(inventory, slot, wardrobe.get(slot, armour), wardrobe.colour(slot, armour),
                wardrobe.id(slot, armour));
    }

    /**
     * The item {@code folder} worn in {@code slot} in {@code colour} is - as
     * {@code id} says it is worn (a wearable's id, {@code forged:<id>}), else
     * by its folder and colour - as the best of it in {@code inventory}.
     */
    public static Item item(Inventory inventory, Slot slot, String folder, String colour, String id) {
        if (folder == null) return null;
        String forged = forgedId(id != null ? id : colour);
        if (forged == null && id != null) {
            // worn as a wearable of its own (a bronze helmet dyed red is still the bronze helmet)
            ItemDef own = Wearables.byId(id);
            if (own != null && own.wearable().puts(slot, folder)) return best(inventory, own);
        }
        if (forged != null) {
            Item best = null;
            if (inventory != null) {
                for (Item i : inventory) {
                    if (i == null || i.forged() == null || !i.forged().id().equals(forged)) continue;
                    if (best == null || i.forged().level() > best.forged().level()) best = i;
                }
            }
            if (best != null) return best;
            Forged f = Forged.byId(forged);
            if (f != null && f.def() != null && f.def().wearable() != null && f.def().wearable().puts(slot, folder)) {
                return f.item();
            }
        }
        ItemDef d = Wearables.wornAs(slot, folder, forged != null ? null : colour);
        if (d == null) return null;
        return best(inventory, d);
    }

    /** The best of {@code d} (not forged) in {@code inventory}: its highest tier, else rarity - or as it is found. */
    private static Item best(Inventory inventory, ItemDef d) {
        Item best = null;
        if (inventory != null) {
            for (Item i : inventory) {
                if (i == null || i.forged() != null || i.def() != d) continue;
                if (best == null || rank(i) > rank(best)) best = i;
            }
        }
        return best != null ? best : Item.of(d);
    }

    private static int rank(Item i) {
        return i.tier() != null ? 100 + i.tier().ordinal() : i.rarity().ordinal();
    }

    /** One piece's roll: the piece, and what its dice came up. */
    public record Block(Piece piece, DiceRoll roll) {

        public int blocked() { return Math.max(0, roll.total()); }
    }

    /**
     * One blow's armour roll: where it landed, what it was, each piece's
     * dice there, what they blocked between them and what got through.
     */
    public record Roll(Location at, double incoming, List<Block> blocks, double blocked, double taken) {

        public Roll {
            blocks = List.copyOf(blocks);
        }

        /** Whether nothing got through. */
        public boolean full() { return taken <= 0 && incoming > 0; }

        /** Whether there was armour where it landed. */
        public boolean armoured() { return !blocks.isEmpty(); }

        /** What the HUD says under the dice: "Blocked 7 of 9", "Fully blocked!", "No armour there". */
        public String summary() {
            if (blocks.isEmpty()) return "No armour there: -" + whole(taken);
            if (full()) return "Fully blocked! (" + whole(blocked) + " vs " + whole(incoming) + ")";
            return "Blocked " + whole(Math.min(blocked, incoming)) + " of " + whole(incoming) + ": -" + whole(taken);
        }
    }

    /**
     * Roll {@code pieces}' armour against a blow of {@code damage}: where it
     * lands ({@link Rules#land}) and every piece there rolls (with {@link
     * Rules#everyPiece}, every piece) - what they come to between them is
     * taken off, down to {@link Rules#minDamage}.
     */
    public static Roll roll(double damage, List<Piece> pieces, Rules rules, Random random) {
        Location at = rules.land(random);
        List<Block> blocks = new ArrayList<>();
        double blocked = 0;
        for (Piece p : pieces) {
            if (!rules.everyPiece() && p.at() != at) continue;
            Block b = new Block(p, p.roll(rules, random));
            blocks.add(b);
            blocked += b.blocked();
        }
        double taken = damage <= 0 ? 0 : Math.max(Math.min(damage, rules.minDamage()), damage - blocked);
        return new Roll(at, damage, blocks, blocked, Math.max(0, taken));
    }

    /** What {@code d} guards and rolls, for a tooltip: "Armour (torso): rolls 1d8 to block". */
    public static String describe(Item item, Rules rules) {
        if (item == null || sides(item.def()) <= 0) return null;
        Location at = null;
        for (Slot s : item.def().wearable().slots()) if (at == null) at = location(s);
        String dice = item.forged() != null ? item.forged().diceText()
                : rules.dice(item.rarity()) + Forged.die(sides(item.def()));
        return "Armour (" + (at == null ? "worn" : at.key()) + "): rolls " + dice + " to block a blow";
    }

    static String whole(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
