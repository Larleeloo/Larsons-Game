package com.larsons.game.combat;

import com.larsons.game.util.Json;
import com.larsons.game.world.Rarity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * The numbers combat runs on, in one place to tune: how many dice a weapon
 * of each rarity rolls and of how many sides each kind of weapon's are, how long a heavy attack and a dodge take to come
 * back, the mana pool, how fast the projectiles fly, how much a dummy takes. Read from {@code assets/combat/combat.json} (every
 * value is optional: one left out keeps its default here); the borders'
 * own numbers - Venom's share, Mystic's multipliers, Bloodlust's step - are
 * the rarity system's ({@link com.larsons.game.world.Border}).
 *
 * <pre>
 *   Common     1 die     Rare       3 dice    Legendary  5 dice
 *   Uncommon   2 dice    Epic       4 dice    Mythic     6 dice
 *   their sides by the weapon: bare hands D4, a dagger D6, a sword, an axe or a mace D8,
 *   a bow or a crossbow D10, a staff or a wand D12 (dice.sidesByWeapon)
 *   a heavy attack rolls one die more; no weapon at all rolls one;
 *   a wand's heavy attack - a blow, no spell in it - rolls one, whatever the wand
 *   a heavy attack can be made once every 10 seconds
 *   a dodge (the dash, the roll) 0.35 seconds after the last one ended
 * </pre>
 */
public final class CombatTuning {

    /** Where the game reads it, under the assets folder. */
    public static final String FILE = "combat/combat.json";

    /** Faces on a die of a weapon {@link #sidesByWeapon} does not name. */
    public final int sides;
    /** Faces on each kind of weapon's dice, by its id ({@code unarmed}: bare hands). */
    private final Map<String, Integer> sidesByWeapon;
    /** The dice each kind of weapon rolls, as the game comes. */
    public static final Map<String, Integer> SIDES = Map.of("unarmed", 4, "dagger", 6, "sword", 8,
            "battle_axe", 8, "mace", 8, "longbow", 10, "crossbow", 10, "crystal_staff", 12, "wand", 12);
    private final Map<Rarity, Integer> dice;
    /** Extra dice a heavy attack rolls. */
    public final int heavyExtra;
    /** Dice a swing with nothing in hand rolls. */
    public final int unarmedDice;
    /** Dice a wand's heavy attack rolls, whatever the wand's rarity: no magic is cast ({@link Combat#plainBlow}). */
    public final int wandHeavyDice;
    /** The least time from one heavy attack to the next, in seconds. */
    public final double heavyCooldown;
    /** The least time from the end of one dodge (the dash, the roll) to the start of the next, in seconds. */
    public final double dodgeCooldown;

    /** The mana pool, and how fast it fills back up (per second). */
    public final double maxMana, manaRegen;
    /** What a spell (a staff's or a wand's bolt) costs. */
    public final double spellCost;
    /**
     * What a Mystic weapon that casts nothing - a sword, an axe, a bow -
     * costs a swing before Mystic doubles it: its +150% is magic, and drawn
     * from the pool like a spell's.
     */
    public final double mysticBaseCost;

    /**
     * The stamina a blow costs ({@code stamina}): a swing, a heavy attack, a
     * spin, a jump attack, a shot (an arrow, a bolt) and a spell; and what a
     * blow does when she has not the stamina for it, times ({@code
     * stamina.tiredDamage}: 0.7, 70%).
     */
    public final double staminaSwing, staminaHeavy, staminaSpin, staminaJumpAttack, staminaShot, staminaSpell,
            tiredDamage;

    /** Projectile speeds, metres a second. */
    public final double arrowSpeed, boltSpeed, spellSpeed;
    /** How far a shot looks for a target and a spell flies, in metres. */
    public final double range;
    /** How far either side of where she faces a shot still finds its target, in degrees. */
    public final double aimAssist;
    /** How long an arrow or bolt that missed stays stuck in the ground, in seconds. */
    public final double stuckSeconds;

    /** A training dummy's health, and how long it lies knocked over before it springs back up. */
    public final double dummyHealth, dummyDownSeconds;

    /**
     * How armour blocks a blow ({@link com.larsons.game.world.Armour}): the
     * dice a piece of each rarity rolls ({@code armour.perRarity}), how often
     * a blow lands on each place ({@code armour.locations}: head, torso, arms,
     * legs, feet), whether every piece rolls at every blow wherever it lands
     * ({@code armour.everyPieceRolls}) and the least a blow still does
     * ({@code armour.minDamage}).
     */
    public final com.larsons.game.world.Armour.Rules armour;

    /** The nearest die there is art for to {@code n} sides: 4, 6, 8, 10, 12 or 20. */
    static int known(int n) {
        int best = 20;
        for (int k : new int[]{4, 6, 8, 10, 12, 20}) if (Math.abs(k - n) < Math.abs(best - n)) best = k;
        return best;
    }

    /** How many sides {@code weapon}'s dice have (null: bare hands): its kind's ({@link #SIDES}). */
    public int sides(com.larsons.game.world.ItemDef weapon) {
        return sidesByWeapon.getOrDefault(weapon == null ? "unarmed" : weapon.id(), sides);
    }

    private CombatTuning(Map<String, Object> json) {
        Map<String, Object> d = Json.obj(json, "dice");
        sides = known((int) Json.num(d, "sides", 8));
        Map<String, Object> by = Json.obj(d, "sidesByWeapon");
        Map<String, Integer> sb = new java.util.HashMap<>();
        for (Map.Entry<String, Integer> e : SIDES.entrySet()) {
            sb.put(e.getKey(), known((int) Json.num(by, e.getKey(), e.getValue())));
        }
        if (by != null) for (String k : by.keySet()) if (!sb.containsKey(k)) sb.put(k, known((int) Json.num(by, k, sides)));
        sidesByWeapon = Map.copyOf(sb);
        Map<String, Object> per = Json.obj(d, "perRarity");
        int[] standard = {1, 2, 3, 4, 5, 6};
        dice = new EnumMap<>(Rarity.class);
        for (Rarity r : Rarity.values()) {
            dice.put(r, Math.max(1, (int) Json.num(per, r.key(), standard[r.ordinal()])));
        }
        heavyExtra = Math.max(0, (int) Json.num(d, "heavyExtra", 1));
        unarmedDice = Math.max(1, (int) Json.num(d, "unarmed", 1));
        wandHeavyDice = Math.max(1, (int) Json.num(d, "wandHeavy", 1));

        Map<String, Object> cool = Json.obj(json, "cooldowns");
        heavyCooldown = Math.max(0, Json.num(cool, "heavySeconds", 10));
        dodgeCooldown = Math.max(0, Json.num(cool, "dodgeSeconds", 0.35));

        Map<String, Object> m = Json.obj(json, "mana");
        maxMana = Math.max(0, Json.num(m, "max", 100));
        manaRegen = Math.max(0, Json.num(m, "regenPerSecond", 5));
        spellCost = Math.max(0, Json.num(m, "spellCost", 10));
        mysticBaseCost = Math.max(0, Json.num(m, "mysticBaseCost", 5));

        Map<String, Object> st = Json.obj(json, "stamina");
        staminaSwing = Math.max(0, Json.num(st, "swing", 6));
        staminaHeavy = Math.max(0, Json.num(st, "heavy", 14));
        staminaSpin = Math.max(0, Json.num(st, "spin", 12));
        staminaJumpAttack = Math.max(0, Json.num(st, "jumpAttack", 10));
        staminaShot = Math.max(0, Json.num(st, "shot", 4));
        staminaSpell = Math.max(0, Json.num(st, "spell", 3));
        tiredDamage = Math.max(0, Math.min(1, Json.num(st, "tiredDamage", 0.7)));

        Map<String, Object> p = Json.obj(json, "projectiles");
        arrowSpeed = Math.max(1, Json.num(p, "arrowSpeed", 30));
        boltSpeed = Math.max(1, Json.num(p, "boltSpeed", 38));
        spellSpeed = Math.max(1, Json.num(p, "spellSpeed", 16));
        range = Math.max(1, Json.num(p, "range", 25));
        aimAssist = Math.max(0, Math.min(90, Json.num(p, "aimAssistDegrees", 25)));
        stuckSeconds = Math.max(0, Json.num(p, "stuckSeconds", 8));

        Map<String, Object> t = Json.obj(json, "dummy");
        dummyHealth = Math.max(1, Json.num(t, "health", 100));
        dummyDownSeconds = Math.max(0, Json.num(t, "downSeconds", 6));

        armour = armour(Json.obj(json, "armour"));
    }

    private static com.larsons.game.world.Armour.Rules armour(Map<String, Object> a) {
        var standard = com.larsons.game.world.Armour.Rules.defaults();
        Map<String, Object> per = Json.obj(a, "perRarity");
        Map<Rarity, Integer> dice = new EnumMap<>(Rarity.class);
        for (Rarity r : Rarity.values()) {
            dice.put(r, Math.max(1, Math.min(10, (int) Json.num(per, r.key(), standard.dice(r)))));
        }
        Map<String, Object> at = Json.obj(a, "locations");
        Map<com.larsons.game.world.Armour.Location, Integer> weights =
                new EnumMap<>(com.larsons.game.world.Armour.Location.class);
        for (var l : com.larsons.game.world.Armour.Location.values()) {
            weights.put(l, Math.max(0, (int) Json.num(at, l.key(), l.weight())));
        }
        boolean every = a != null && Boolean.TRUE.equals(a.get("everyPieceRolls"));
        return new com.larsons.game.world.Armour.Rules(dice, weights, every, Math.max(0, Json.num(a, "minDamage", 0)));
    }

    /** Every number at its default. */
    public static CombatTuning defaults() {
        return new CombatTuning(Map.of());
    }

    /** From JSON text; anything missing (or the whole text, if it is not JSON) keeps its default. */
    public static CombatTuning parse(String text) {
        try {
            Object o = Json.parse(text);
            return new CombatTuning(o instanceof Map<?, ?> ? Json.asObject(o) : Map.of());
        } catch (RuntimeException e) {
            System.err.println("[combat] " + FILE + " is not valid JSON, using the defaults: " + e.getMessage());
            return defaults();
        }
    }

    /** {@link #FILE} under {@code assets}, or the defaults when there is none. */
    public static CombatTuning load(Path assets) {
        Path file = assets.resolve(FILE);
        if (!Files.isRegularFile(file)) return defaults();
        try {
            return parse(Files.readString(file));
        } catch (IOException e) {
            System.err.println("[combat] cannot read " + file + ": " + e.getMessage());
            return defaults();
        }
    }

    /** How many dice a weapon of {@code rarity} rolls. */
    public int dice(Rarity rarity) {
        return dice.get(rarity == null ? Rarity.COMMON : rarity);
    }
}
