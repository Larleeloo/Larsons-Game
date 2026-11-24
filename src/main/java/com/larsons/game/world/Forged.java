package com.larsons.game.world;

import com.larsons.game.combat.DiceRoll;
import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A weapon made in Creative Mode's Forge: one of the weapons ({@link
 * ItemDef#ALL}) - its look, its stance, its moves - with its parts in
 * colours of its own (the blade, the handle, the fittings ... or all of it:
 * the parts {@link com.larsons.game.sprite.TierLook} names), its own name and
 * rarity, the dice it rolls - how many, and of what: d4, d6, d8, d10, d%,
 * d12 or d20 - in a colour of their own, and any of the three borders'
 * effects ({@link Border}: Venom, Mystic, Bloodlust), even all three at once.
 *
 * <p>Items name it {@code forged:<id>} ({@link Item#key}): placed as an item
 * in a level, put in a chest or a barrel, given in a script. One file each in
 * {@code assets/forge/}, named for it, kept for every level:
 *
 * <pre>
 * {"format": 1, "name": "Duskfang", "base": "sword", "rarity": "epic",
 *  "dice": 3, "sides": 8, "diceColour": "#5d86fa", "effects": ["venom", "mystic"],
 *  "tints": {"blade": "#b722e7", "handle": "#231c18"}}
 * </pre>
 *
 * <p>A wand or a staff can be forged to fire <b>full auto</b> ({@code
 * "fullAuto": true, "boltsPerSecond": 6}) and to draw more or less mana
 * ({@code "manaUse": 0.5}); a sword, a mace, an axe or a dagger to cost more
 * or less stamina a swing ({@code "staminaUse": 2}).
 *
 * @param id       the file's name, and how items name it
 * @param name     what it is called
 * @param base     the weapon it is made from, by id ({@code sword}, {@code battle_axe} ...)
 * @param rarity   how rare it is: the colour of its name, of the damage it does, of its dice when they keep theirs
 * @param tints    part to colour ({@code 0xRRGGBB}): {@code all} tints the whole weapon, then each part named
 * @param dice     how many dice a swing rolls (a heavy attack one more)
 * @param sides    of how many sides: 4, 6, 8, 10, 12, 20, or 100 (the d%)
 * @param diceRgb  the dice's colour ({@code 0xRRGGBB}), or -1 for its rarity's
 * @param effects  the borders' effects it has
 * @param handling how it is used: full auto, its mana use, its stamina use ({@link Handling})
 */
public record Forged(String id, String name, String base, Rarity rarity, Map<String, Integer> tints,
                     int dice, int sides, int diceRgb, Set<Border> effects, List<DiceRoll.Group> extra,
                     int ammoEvery, List<Upgrade> upgrades, int level, Map<Gear.Effect, Double> gear,
                     double fireRate, int accuracy, Handling handling) {

    /**
     * How a forged weapon is used, past its dice: whether a wand or a staff
     * fires <b>full auto</b> - bolts of magic streaming out like a machine
     * gun's for as long as the attack is held, standing or on the move - and
     * how many a second ({@code boltRate}); how much of the mana a wand's or a
     * staff's every attack draws ({@code manaUse}: times what it would, 0 none
     * at all), and how much of the stamina a sword's, a mace's, an axe's or a
     * dagger's every swing costs ({@code staminaUse}). A bow and a crossbow
     * cost neither.
     */
    public record Handling(boolean fullAuto, double boltRate, double manaUse, double staminaUse) {
        /** Each kind's own: one spell a press, six bolts a second when full auto, the mana and stamina as they are. */
        public static final Handling STANDARD = new Handling(false, AUTO_RATE, 1, 1);

        public Handling {
            boltRate = Double.isNaN(boltRate) || boltRate <= 0 ? AUTO_RATE
                    : Math.max(MIN_BOLTS, Math.min(MAX_BOLTS, boltRate));
            manaUse = Double.isNaN(manaUse) ? 1 : Math.max(0, Math.min(MAX_USE, manaUse));
            staminaUse = Double.isNaN(staminaUse) ? 1 : Math.max(0, Math.min(MAX_USE, staminaUse));
        }

        public Handling withFullAuto(boolean on) { return new Handling(on, boltRate, manaUse, staminaUse); }

        public Handling withBoltRate(double r) { return new Handling(fullAuto, r, manaUse, staminaUse); }

        public Handling withManaUse(double m) { return new Handling(fullAuto, boltRate, m, staminaUse); }

        public Handling withStaminaUse(double s) { return new Handling(fullAuto, boltRate, manaUse, s); }
    }

    /** Full auto's bolts a second, unless the Forge says otherwise; the fewest and the most it can be set to. */
    public static final double AUTO_RATE = 6, MIN_BOLTS = 1, MAX_BOLTS = 20;
    /** The most of its mana or stamina a forged weapon can be set to use, times its kind's own. */
    public static final double MAX_USE = 4;

    /**
     * One of a forged weapon's upgrade tiers after its first (the weapon as
     * forged): what it is called ("" - its name and the tier's number,
     * "Duskfang +2"), how rare, the dice it rolls then and their colour, its
     * parts' colours (empty: as before), how many swings or shots one of its
     * ammunition lasts ({@link Ammo}), how many {@link Ammo#INGOT}s it costs
     * to reach, whether it is a mystery until it is reached (shown as ???) -
     * and, for a piece of armour or clothing, what it gives at that tier
     * ({@code gear}: every effect it has then, not added to the tiers below;
     * null: as the tier before).
     */
    public record Upgrade(String name, Rarity rarity, int dice, int sides, List<DiceRoll.Group> extra, int diceRgb,
                          Map<String, Integer> tints, int ammoEvery, int cost, boolean mystery,
                          Map<Gear.Effect, Double> gear) {
        public Upgrade {
            name = name == null ? "" : name.strip();
            if (rarity == null) rarity = Rarity.EPIC;
            dice = Math.max(1, Math.min(MAX_DICE, dice));
            if (!known(sides)) sides = 20;
            extra = extra == null ? List.of() : List.copyOf(extra);
            diceRgb = diceRgb < 0 ? -1 : diceRgb & 0xFFFFFF;
            tints = tints == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(tints));
            ammoEvery = Math.max(1, ammoEvery);
            cost = Math.max(0, cost);
            if (gear != null) gear = Gear.clean(gear);
        }

        /** One with what it gives as the tier before (a weapon's, which gives nothing). */
        public Upgrade(String name, Rarity rarity, int dice, int sides, List<DiceRoll.Group> extra, int diceRgb,
                       Map<String, Integer> tints, int ammoEvery, int cost, boolean mystery) {
            this(name, rarity, dice, sides, extra, diceRgb, tints, ammoEvery, cost, mystery, null);
        }

        public Upgrade withName(String n) {
            return new Upgrade(n, rarity, dice, sides, extra, diceRgb, tints, ammoEvery, cost, mystery, gear);
        }

        public Upgrade withRarity(Rarity r) {
            return new Upgrade(name, r, dice, sides, extra, diceRgb, tints, ammoEvery, cost, mystery, gear);
        }

        public Upgrade withDice(int n) {
            return new Upgrade(name, rarity, n, sides, extra, diceRgb, tints, ammoEvery, cost, mystery, gear);
        }

        public Upgrade withSides(int x) {
            return new Upgrade(name, rarity, dice, x, extra, diceRgb, tints, ammoEvery, cost, mystery, gear);
        }

        public Upgrade withExtra(List<DiceRoll.Group> e) {
            return new Upgrade(name, rarity, dice, sides, e, diceRgb, tints, ammoEvery, cost, mystery, gear);
        }

        public Upgrade withDiceRgb(int c) {
            return new Upgrade(name, rarity, dice, sides, extra, c, tints, ammoEvery, cost, mystery, gear);
        }

        public Upgrade withTint(String part, int rgb) {
            Map<String, Integer> t = new LinkedHashMap<>(tints);
            if (rgb < 0) t.remove(part);
            else t.put(part, rgb);
            return new Upgrade(name, rarity, dice, sides, extra, diceRgb, t, ammoEvery, cost, mystery, gear);
        }

        public Upgrade withAmmoEvery(int n) {
            return new Upgrade(name, rarity, dice, sides, extra, diceRgb, tints, n, cost, mystery, gear);
        }

        public Upgrade withCost(int c) {
            return new Upgrade(name, rarity, dice, sides, extra, diceRgb, tints, ammoEvery, c, mystery, gear);
        }

        public Upgrade withMystery(boolean m) {
            return new Upgrade(name, rarity, dice, sides, extra, diceRgb, tints, ammoEvery, cost, m, gear);
        }

        /** Giving {@code g} at this tier (null: as the tier before). */
        public Upgrade withGear(Map<Gear.Effect, Double> g) {
            return new Upgrade(name, rarity, dice, sides, extra, diceRgb, tints, ammoEvery, cost, mystery, g);
        }
    }

    /** The most upgrade tiers a forged weapon has, its first (as forged) among them. */
    public static final int MAX_TIERS = 10;
    /** How many swings or shots one of its ammunition lasts, unless the Forge says otherwise. */
    public static final int AMMO_EVERY = 12;

    /** The dice there are, in the order the Forge offers them. */
    public static final int[] SIDES = {4, 6, 8, 10, 100, 12, 20};
    /** The most dice a forged weapon rolls. */
    public static final int MAX_DICE = 10;
    /** What items call a forged weapon before its id. */
    public static final String PREFIX = "forged:";
    public static final String FOLDER = "forge";
    static final int FORMAT = 1;

    public Forged {
        name = name == null || name.isBlank() ? "Forged weapon" : name.strip();
        ItemDef d = ItemDef.byId(base);
        if (!forgeable(d)) {
            base = ItemDef.SWORD.id();
            d = ItemDef.SWORD;
        }
        // what is worn has the gear's effects, a weapon the borders' (Venom, Mystic, Bloodlust): never the other's
        boolean worn = d.isWearable();
        EnumMap<Gear.Effect, Double> g = new EnumMap<>(Gear.Effect.class);
        if (gear != null && worn) {
            for (Map.Entry<Gear.Effect, Double> e : gear.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) g.put(e.getKey(), e.getKey().clamp(e.getValue()));
            }
        }
        gear = Collections.unmodifiableMap(g);
        if (worn) effects = Set.of();
        if (rarity == null) rarity = Rarity.EPIC;
        Map<String, Integer> t = new LinkedHashMap<>();
        if (tints != null) {
            for (Map.Entry<String, Integer> e : tints.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue() >= 0) {
                    t.put(e.getKey(), e.getValue() & 0xFFFFFF);
                }
            }
        }
        tints = Collections.unmodifiableMap(t);
        dice = Math.max(1, Math.min(MAX_DICE, dice));
        if (!known(sides)) sides = 20;
        diceRgb = diceRgb < 0 ? -1 : diceRgb & 0xFFFFFF;
        EnumSet<Border> e = EnumSet.noneOf(Border.class);
        if (effects != null) for (Border b : effects) if (b != null) e.add(b);
        effects = Collections.unmodifiableSet(e);
        extra = extra == null ? List.of() : List.copyOf(extra);
        ammoEvery = ammoEvery <= 0 ? AMMO_EVERY : ammoEvery;
        upgrades = upgrades == null ? List.of()
                : List.copyOf(upgrades.size() > MAX_TIERS - 1 ? upgrades.subList(0, MAX_TIERS - 1) : upgrades);
        if (!worn) {
            // (a weapon's tiers give none of the gear's effects either)
            List<Upgrade> plain = new ArrayList<>();
            for (Upgrade u : upgrades) plain.add(u.gear() == null ? u : u.withGear(null));
            upgrades = List.copyOf(plain);
        }
        level = Math.max(0, Math.min(upgrades.size(), level));
        fireRate = fireRate <= 0 || Double.isNaN(fireRate) ? 1 : Math.max(MIN_RATE, Math.min(MAX_RATE, fireRate));
        accuracy = accuracy <= 0 ? 100 : Math.max(MIN_ACCURACY, Math.min(100, accuracy));
        if (handling == null || worn) handling = Handling.STANDARD;
    }

    /** One used as its kind is ({@link Handling#STANDARD}). */
    public Forged(String id, String name, String base, Rarity rarity, Map<String, Integer> tints, int dice, int sides,
                  int diceRgb, Set<Border> effects, List<DiceRoll.Group> extra, int ammoEvery, List<Upgrade> upgrades,
                  int level, Map<Gear.Effect, Double> gear, double fireRate, int accuracy) {
        this(id, name, base, rarity, tints, dice, sides, diceRgb, effects, extra, ammoEvery, upgrades, level, gear,
                fireRate, accuracy, Handling.STANDARD);
    }

    /** The slowest and the fastest a forged weapon shoots, of its kind's own speed. */
    public static final double MIN_RATE = 0.25, MAX_RATE = 3;
    /** The least accuracy a forged weapon is made with, percent (100: every shot as aimed). */
    public static final int MIN_ACCURACY = 10;

    /** One with its kind's own speed and every shot as aimed. */
    public Forged(String id, String name, String base, Rarity rarity, Map<String, Integer> tints, int dice, int sides,
                  int diceRgb, Set<Border> effects, List<DiceRoll.Group> extra, int ammoEvery, List<Upgrade> upgrades,
                  int level, Map<Gear.Effect, Double> gear) {
        this(id, name, base, rarity, tints, dice, sides, diceRgb, effects, extra, ammoEvery, upgrades, level, gear, 1,
                100);
    }

    /** Whether it shoots (a bow, a crossbow, a casting staff or a wand): what a rate of fire and accuracy are for. */
    public boolean ranged() {
        ItemDef d = def();
        if (d == null || d.isWearable()) return false;
        String b = base;
        return b.equals("longbow") || b.equals("crossbow") || b.contains("staff") || b.contains("wand");
    }

    /** With a rate of fire of {@code r} times its kind's own. */
    public Forged withFireRate(double r) {
        return new Forged(id, name, base, rarity, tints, dice, sides, diceRgb, effects, extra, ammoEvery, upgrades,
                level, gear, r, accuracy, handling);
    }

    /** With an accuracy of {@code a} percent. */
    public Forged withAccuracy(int a) {
        return new Forged(id, name, base, rarity, tints, dice, sides, diceRgb, effects, extra, ammoEvery, upgrades,
                level, gear, fireRate, a, handling);
    }

    /** Whether it is a wand or a staff: what casts, and what full auto and its mana use are for. */
    public boolean casts() {
        ItemDef d = def();
        return d != null && !d.isWearable() && (base.contains("staff") || base.contains("wand"));
    }

    /** Whether it is a blade, a mace or an axe - swung, not shot: what its stamina use is for. */
    public boolean swung() {
        ItemDef d = def();
        return d != null && !d.isWearable() && !ranged();
    }

    /** Whether it is a wand or a staff that fires full auto: bolts streaming out while the attack is held. */
    public boolean fullAuto() { return casts() && handling.fullAuto(); }

    /** Full auto's bolts a second. */
    public double boltRate() { return handling.boltRate(); }

    /** How much of the mana a wand's or a staff's attacks draw, times its kind's own (0: none). */
    public double manaUse() { return casts() ? handling.manaUse() : 1; }

    /** How much of the stamina a swung weapon's blows cost, times its kind's own (0: none). */
    public double staminaUse() { return swung() ? handling.staminaUse() : 1; }

    /** Used as {@code h} says. */
    public Forged withHandling(Handling h) {
        return new Forged(id, name, base, rarity, tints, dice, sides, diceRgb, effects, extra, ammoEvery, upgrades,
                level, gear, fireRate, accuracy, h);
    }

    /** Firing full auto, or one spell a press. */
    public Forged withFullAuto(boolean on) { return withHandling(handling.withFullAuto(on)); }

    /** Full auto at {@code r} bolts a second. */
    public Forged withBoltRate(double r) { return withHandling(handling.withBoltRate(r)); }

    /** Drawing {@code m} times its kind's mana. */
    public Forged withManaUse(double m) { return withHandling(handling.withManaUse(m)); }

    /** Costing {@code s} times its kind's stamina. */
    public Forged withStaminaUse(double s) { return withHandling(handling.withStaminaUse(s)); }

    /** One as the Forge made them before upgrades: its first tier only. */
    public Forged(String id, String name, String base, Rarity rarity, Map<String, Integer> tints, int dice, int sides,
                  int diceRgb, Set<Border> effects) {
        this(id, name, base, rarity, tints, dice, sides, diceRgb, effects, List.of(), AMMO_EVERY, List.of(), 0,
                Map.of());
    }

    /** One with no special effects of the gear's. */
    public Forged(String id, String name, String base, Rarity rarity, Map<String, Integer> tints, int dice, int sides,
                  int diceRgb, Set<Border> effects, List<DiceRoll.Group> extra, int ammoEvery, List<Upgrade> upgrades,
                  int level) {
        this(id, name, base, rarity, tints, dice, sides, diceRgb, effects, extra, ammoEvery, upgrades, level, Map.of());
    }

    /**
     * Whether the Forge can make something of {@code d}: a weapon, or
     * anything worn as clothes or armour ({@link #wornBase}) - not a dye,
     * a hair style, the eyebrows or the makeup.
     */
    public static boolean forgeable(ItemDef d) {
        return d != null && (ItemDef.ALL.contains(d) || wornBase(d));
    }

    /** Whether {@code d} is clothes or armour the Forge can make a piece of. */
    public static boolean wornBase(ItemDef d) {
        if (d == null || !d.isWearable() || d.wearable().slots().isEmpty() || d.wearable().folders().isEmpty()) {
            return false;
        }
        for (com.larsons.game.sprite.Slot s : d.wearable().slots()) {
            switch (s) {
                case HAIR, EYEBROWS, MAKEUP, MOUTH, BODY, EARS, EYES, NOSE, HORNS, TAIL -> {
                    return false;
                }
                default -> { }
            }
        }
        return true;
    }

    /** Whether it is worn (clothes or armour), not a weapon. */
    public boolean worn() {
        ItemDef d = def();
        return d != null && d.isWearable();
    }

    /** With {@code e} at {@code amount}, or without it (a negative amount, or 0). */
    public Forged withGear(Gear.Effect e, double amount) {
        EnumMap<Gear.Effect, Double> g = new EnumMap<>(Gear.Effect.class);
        g.putAll(gear);
        if (amount <= 0) g.remove(e);
        else g.put(e, amount);
        return new Forged(id, name, base, rarity, tints, dice, sides, diceRgb, effects, extra, ammoEvery, upgrades,
                level, g, fireRate, accuracy, handling);
    }

    /** Its special effects at its tier now, as the inventory says them: "+25 max health", "A second jump in the air". */
    public List<String> gearText() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Gear.Effect, Double> e : gear().entrySet()) out.add(e.getKey().said(e.getValue()));
        return out;
    }

    /**
     * What it gives at its tier now: what its tier was made with, else the
     * tier before's ... down to what it was forged with (its first tier's).
     */
    @Override
    public Map<Gear.Effect, Double> gear() {
        Map<Gear.Effect, Double> g = gear;
        for (int i = 0; i < level; i++) if (upgrades.get(i).gear() != null) g = upgrades.get(i).gear();
        return g;
    }

    /** What it gives as forged, at its first tier (what the Effects page sets). */
    public Map<Gear.Effect, Double> baseGear() { return gear; }

    // --- its tier now (level 0: as forged; n: its n-th upgrade) ----------------------

    /** Its upgrade tier now, or null at its first (as forged). */
    public Upgrade tier() {
        return level == 0 ? null : upgrades.get(level - 1);
    }

    /** How many tiers it has, its first among them. */
    public int tiers() {
        return upgrades.size() + 1;
    }

    /** What it is called at its tier now: the tier's own name, or its name and the tier's number. */
    @Override
    public String name() {
        Upgrade u = tier();
        if (u == null) return name;
        return u.name().isEmpty() ? name + " +" + level : u.name();
    }

    /** Its name as forged (its first tier's). */
    public String baseName() { return name; }

    @Override
    public Rarity rarity() { return tier() == null ? rarity : tier().rarity(); }

    @Override
    public int dice() { return tier() == null ? dice : tier().dice(); }

    @Override
    public int sides() { return tier() == null ? sides : tier().sides(); }

    @Override
    public int diceRgb() { return tier() == null ? diceRgb : tier().diceRgb(); }

    @Override
    public List<DiceRoll.Group> extra() { return tier() == null ? extra : tier().extra(); }

    @Override
    public int ammoEvery() { return tier() == null ? ammoEvery : tier().ammoEvery(); }

    /** Its parts' colours at its tier now: its first tier's, with every upgrade's so far over them. */
    @Override
    public Map<String, Integer> tints() {
        if (level == 0) return tints;
        Map<String, Integer> t = new LinkedHashMap<>(tints);
        for (int i = 0; i < level; i++) t.putAll(upgrades.get(i).tints());
        return Collections.unmodifiableMap(t);
    }

    /** It at tier {@code n} (0: as forged), held within its tiers. */
    public Forged at(int n) {
        return new Forged(id, name, base, rarity, tints, dice, sides, diceRgb, effects, extra, ammoEvery, upgrades, n, gear, fireRate, accuracy, handling);
    }

    /** It one tier up, or null at its last. */
    public Forged upgraded() {
        return level >= upgrades.size() ? null : at(level + 1);
    }

    /** The ingots its next tier costs (-1: it is at its last). */
    public int nextCost() {
        return level >= upgrades.size() ? -1 : upgrades.get(level).cost();
    }

    /** Its dice at its tier now: "2d8 × 1d4". */
    public String diceText() {
        StringBuilder s = new StringBuilder(dice() + die(sides()));
        for (DiceRoll.Group g : extra()) s.append(' ').append(g.notation());
        return s.toString();
    }

    public Forged withExtra(List<DiceRoll.Group> e) {
        return new Forged(id, name, base, rarity, tints, dice, sides, diceRgb, effects, e, ammoEvery, upgrades, 0, gear, fireRate, accuracy, handling);
    }

    public Forged withAmmoEvery(int n) {
        return new Forged(id, name, base, rarity, tints, dice, sides, diceRgb, effects, extra, n, upgrades, 0, gear, fireRate, accuracy, handling);
    }

    public Forged withUpgrades(List<Upgrade> u) {
        return new Forged(id, name, base, rarity, tints, dice, sides, diceRgb, effects, extra, ammoEvery, u, 0, gear,
                fireRate, accuracy, handling);
    }

    /** A new one: an Epic sword rolling one die of a sword's (a d8), in its own colours, with no effects. */
    public static Forged fresh(String name) {
        return new Forged(null, name, ItemDef.SWORD.id(), Rarity.EPIC, Map.of(), 1,
                com.larsons.game.combat.CombatTuning.SIDES.getOrDefault(ItemDef.SWORD.id(), 8), -1, Set.of());
    }

    /** A new piece of armour: an Epic iron helmet blocking with two d8s, in its own colours, giving nothing yet. */
    public static Forged freshWorn(String name) {
        ItemDef d = Wearables.byId("iron_helmet");
        if (d == null) return fresh(name);
        return new Forged(null, name, d.id(), Rarity.EPIC, Map.of(), 2, Math.max(4, Armour.sides(d)), -1, Set.of());
    }

    /** Whether there are dice of {@code sides}. */
    public static boolean known(int sides) {
        for (int s : SIDES) if (s == sides) return true;
        return false;
    }

    /** What a die of {@code sides} is called: "d8", "d%". */
    public static String die(int sides) {
        return sides == 100 ? "d%" : "d" + sides;
    }

    /** "3d8", "2d%". */
    public String notation() {
        return dice + die(sides);
    }

    /** The weapon it is made from. */
    public ItemDef def() {
        return ItemDef.byId(base);
    }

    /** It, as an item. */
    public Item item() {
        return new Item(def(), null, rarity, null, this);
    }

    /** What items call it: {@code forged:<id>}, and its tier past its first: {@code forged:<id>:2}. */
    public String key() {
        return PREFIX + id + (level > 0 ? ":" + level : "");
    }

    /** Its effects, as the inventory says them: "Venom, Mystic". */
    public String effectsText() {
        List<String> out = new ArrayList<>();
        for (Border b : effects) out.add(b.label());
        return String.join(", ", out);
    }

    public Forged withId(String i) { return new Forged(i, name, base, rarity, tints, dice, sides, diceRgb, effects, extra, ammoEvery, upgrades, level, gear, fireRate, accuracy, handling); }

    public Forged withName(String n) { return new Forged(id, n, base, rarity, tints, dice, sides, diceRgb, effects, extra, ammoEvery, upgrades, level, gear, fireRate, accuracy, handling); }

    /** Made from another weapon: the colours of parts it does not have dropped. */
    public Forged withBase(String b) { return new Forged(id, name, b, rarity, tints, dice, sides, diceRgb, effects, extra, ammoEvery, upgrades, level, gear, fireRate, accuracy, handling); }

    public Forged withRarity(Rarity r) { return new Forged(id, name, base, r, tints, dice, sides, diceRgb, effects, extra, ammoEvery, upgrades, level, gear, fireRate, accuracy, handling); }

    public Forged withDice(int n) { return new Forged(id, name, base, rarity, tints, n, sides, diceRgb, effects, extra, ammoEvery, upgrades, level, gear, fireRate, accuracy, handling); }

    public Forged withSides(int s) { return new Forged(id, name, base, rarity, tints, dice, s, diceRgb, effects, extra, ammoEvery, upgrades, level, gear, fireRate, accuracy, handling); }

    public Forged withDiceRgb(int c) { return new Forged(id, name, base, rarity, tints, dice, sides, c, effects, extra, ammoEvery, upgrades, level, gear, fireRate, accuracy, handling); }

    /** {@code part} in {@code rgb} (-1: as it is drawn). */
    public Forged withTint(String part, int rgb) {
        Map<String, Integer> t = new LinkedHashMap<>(tints);
        if (rgb < 0) t.remove(part);
        else t.put(part, rgb);
        return new Forged(id, name, base, rarity, t, dice, sides, diceRgb, effects, extra, ammoEvery, upgrades, level, gear, fireRate, accuracy, handling);
    }

    /** With {@code b}'s effect, or without it. */
    public Forged withEffect(Border b, boolean on) {
        EnumSet<Border> e = effects.isEmpty() ? EnumSet.noneOf(Border.class) : EnumSet.copyOf(effects);
        if (on) e.add(b);
        else e.remove(b);
        return new Forged(id, name, base, rarity, tints, dice, sides, diceRgb, e, extra, ammoEvery, upgrades, level, gear, fireRate, accuracy, handling);
    }

    // --- the forge's files ------------------------------------------------------------

    private static Path dir = Path.of("assets", FOLDER);
    private static List<Forged> all;

    /** Keep them in {@code folder} from now on (tests: a folder of their own). */
    public static synchronized void useDir(Path folder) {
        dir = folder;
        all = null;
    }

    /** Every forged weapon, by name. */
    public static synchronized List<Forged> all() {
        if (all == null) all = readAll(dir);
        return List.copyOf(all);
    }

    /** The one kept as {@code id}, or null. */
    public static synchronized Forged byId(String id) {
        if (id == null) return null;
        for (Forged f : all()) if (f.id().equals(id)) return f;
        return null;
    }

    /** Keep {@code f}: under its id, or a new one from its name. Returns it as kept. */
    public static synchronized Forged save(Forged f) throws IOException {
        Files.createDirectories(dir);
        Forged kept = f.id() == null || f.id().isBlank() ? f.withId(free(slug(f.baseName()))) : f;
        Files.writeString(dir.resolve(kept.id() + ".json"), json(kept), StandardCharsets.UTF_8);
        all = null;
        return kept;
    }

    /** Throw away the one kept as {@code id}. */
    public static synchronized void delete(String id) throws IOException {
        if (id == null) return;
        Files.deleteIfExists(dir.resolve(id + ".json"));
        all = null;
    }

    /** A file name for one called {@code name}: lower case, words joined by underscores. */
    static String slug(String name) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "weapon" : s.length() > 48 ? s.substring(0, 48) : s;
    }

    private static String free(String base) {
        String id = base;
        for (int i = 2; Files.exists(dir.resolve(id + ".json")); i++) id = base + "_" + i;
        return id;
    }

    static String json(Forged f) {
        f = f.at(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", FORMAT);
        m.put("name", f.name());
        m.put("base", f.base());
        m.put("rarity", f.rarity().key());
        m.put("dice", f.dice());
        m.put("sides", f.sides());
        if (f.diceRgb() >= 0) m.put("diceColour", hex(f.diceRgb()));
        if (!f.extra().isEmpty()) m.put("moreDice", groups(f.extra()));
        m.put("ammoEvery", f.ammoEvery());
        if (f.fireRate() != 1) m.put("fireRate", f.fireRate());
        if (f.accuracy() != 100) m.put("accuracy", f.accuracy());
        Handling h = f.handling();
        if (h.fullAuto()) m.put("fullAuto", true);
        if (h.boltRate() != AUTO_RATE) m.put("boltsPerSecond", h.boltRate());
        if (h.manaUse() != 1) m.put("manaUse", h.manaUse());
        if (h.staminaUse() != 1) m.put("staminaUse", h.staminaUse());
        List<Object> e = new ArrayList<>();
        for (Border b : f.effects()) e.add(b.key());
        m.put("effects", e);
        if (!f.gear().isEmpty()) m.put("gear", gearMap(f.gear()));
        m.put("tints", tintMap(f.tints()));
        if (!f.upgrades().isEmpty()) {
            List<Object> ups = new ArrayList<>();
            for (Upgrade u : f.upgrades()) {
                Map<String, Object> um = new LinkedHashMap<>();
                if (!u.name().isEmpty()) um.put("name", u.name());
                um.put("rarity", u.rarity().key());
                um.put("dice", u.dice());
                um.put("sides", u.sides());
                if (!u.extra().isEmpty()) um.put("moreDice", groups(u.extra()));
                if (u.diceRgb() >= 0) um.put("diceColour", hex(u.diceRgb()));
                if (!u.tints().isEmpty()) um.put("tints", tintMap(u.tints()));
                um.put("ammoEvery", u.ammoEvery());
                um.put("cost", u.cost());
                if (u.mystery()) um.put("mystery", true);
                if (u.gear() != null) um.put("gear", gearMap(u.gear()));
                ups.add(um);
            }
            m.put("upgrades", ups);
        }
        return Json.stringify(m) + "\n";
    }

    /** One read back from its file's {@code text}, kept as {@code id}. */
    static Forged parse(String id, String text) {
        Map<String, Object> m = Json.asObject(Json.parse(text));
        Map<String, Integer> tints = new LinkedHashMap<>();
        Map<String, Object> t = Json.obj(m, "tints");
        if (t != null) for (Map.Entry<String, Object> e : t.entrySet()) tints.put(e.getKey(), rgb(e.getValue()));
        EnumSet<Border> effects = EnumSet.noneOf(Border.class);
        Object e = m.get("effects");
        if (e instanceof List<?> l) {
            for (Object o : l) {
                Border b = Border.byKey(String.valueOf(o));
                if (b != null) effects.add(b);
            }
        }
        Map<Gear.Effect, Double> gear = gearOf(Json.obj(m, "gear"));
        if (gear == null) gear = Map.of();
        String dc = Json.str(m, "diceColour", "");
        List<Upgrade> ups = new ArrayList<>();
        if (m.get("upgrades") instanceof List<?> l) {
            for (Object o : l) {
                Map<String, Object> um = Json.asObject(o);
                String udc = Json.str(um, "diceColour", "");
                Map<String, Integer> ut = new LinkedHashMap<>();
                Map<String, Object> utm = Json.obj(um, "tints");
                if (utm != null) for (Map.Entry<String, Object> x : utm.entrySet()) ut.put(x.getKey(), rgb(x.getValue()));
                ups.add(new Upgrade(Json.str(um, "name", ""), Rarity.byKey(Json.str(um, "rarity", "epic")),
                        (int) Json.num(um, "dice", 1), (int) Json.num(um, "sides", 20), groups(um.get("moreDice")),
                        udc.isEmpty() ? -1 : rgb(udc), ut, (int) Json.num(um, "ammoEvery", AMMO_EVERY),
                        (int) Json.num(um, "cost", 1), Boolean.TRUE.equals(um.get("mystery")),
                        gearOf(Json.obj(um, "gear"))));
            }
        }
        return new Forged(id, Json.str(m, "name", id), Json.str(m, "base", "sword"),
                Rarity.byKey(Json.str(m, "rarity", "epic")), tints, (int) Json.num(m, "dice", 1),
                (int) Json.num(m, "sides", 20), dc.isEmpty() ? -1 : rgb(dc), effects, groups(m.get("moreDice")),
                (int) Json.num(m, "ammoEvery", AMMO_EVERY), ups, 0, gear, Json.num(m, "fireRate", 1),
                (int) Json.num(m, "accuracy", 100), new Handling(Boolean.TRUE.equals(m.get("fullAuto")),
                Json.num(m, "boltsPerSecond", AUTO_RATE), Json.num(m, "manaUse", 1), Json.num(m, "staminaUse", 1)));
    }

    private static Map<String, Object> gearMap(Map<Gear.Effect, Double> gear) {
        Map<String, Object> g = new LinkedHashMap<>();
        for (Map.Entry<Gear.Effect, Double> x : gear.entrySet()) g.put(x.getKey().key(), x.getValue());
        return g;
    }

    /** The effects a file's {@code gear} names (null: there is none). */
    private static Map<Gear.Effect, Double> gearOf(Map<String, Object> gm) {
        if (gm == null) return null;
        EnumMap<Gear.Effect, Double> gear = new EnumMap<>(Gear.Effect.class);
        for (Map.Entry<String, Object> x : gm.entrySet()) {
            Gear.Effect ge = Gear.Effect.byKey(x.getKey());
            if (ge != null && x.getValue() instanceof Number n) gear.put(ge, n.doubleValue());
        }
        return gear;
    }

    private static List<Object> groups(List<DiceRoll.Group> gs) {
        List<Object> out = new ArrayList<>();
        for (DiceRoll.Group g : gs) {
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("op", g.op().key());
            gm.put("count", g.count());
            gm.put("sides", g.sides());
            out.add(gm);
        }
        return out;
    }

    private static List<DiceRoll.Group> groups(Object o) {
        List<DiceRoll.Group> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) {
                Map<String, Object> gm = Json.asObject(x);
                int sides = (int) Json.num(gm, "sides", 6);
                if (!known(sides)) continue;
                out.add(new DiceRoll.Group((int) Json.num(gm, "count", 1), sides,
                        DiceRoll.Op.byKey(Json.str(gm, "op", "+"))));
            }
        }
        return out;
    }

    private static Map<String, Object> tintMap(Map<String, Integer> tints) {
        Map<String, Object> t = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> p : tints.entrySet()) t.put(p.getKey(), hex(p.getValue()));
        return t;
    }

    private static List<Forged> readAll(Path folder) {
        List<Forged> out = new ArrayList<>();
        if (!Files.isDirectory(folder)) return out;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path p : (Iterable<Path>) files.sorted()::iterator) {
                String n = p.getFileName().toString();
                if (!n.endsWith(".json")) continue;
                try {
                    out.add(parse(n.substring(0, n.length() - 5), Files.readString(p, StandardCharsets.UTF_8)));
                } catch (IOException | RuntimeException ex) {
                    System.err.println("[forge] ignoring " + p + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            System.err.println("[forge] cannot list " + folder + ": " + ex.getMessage());
        }
        out.sort(Comparator.comparing(f -> f.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    private static String hex(int rgb) {
        return String.format("#%06x", rgb & 0xFFFFFF);
    }

    private static int rgb(Object v) {
        String s = String.valueOf(v).trim();
        try {
            return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
