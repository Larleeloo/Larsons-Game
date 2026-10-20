package com.larsons.game.combat;

import com.larsons.game.util.Json;
import com.larsons.game.world.Rarity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * The numbers combat runs on, in one place to tune: how many D20s a weapon
 * of each rarity rolls, the mana pool, how fast the projectiles fly, how
 * much a dummy takes. Read from {@code assets/combat/combat.json} (every
 * value is optional: one left out keeps its default here); the borders'
 * own numbers - Venom's share, Mystic's multipliers, Bonded's step - are
 * the rarity system's ({@link com.larsons.game.world.Border}).
 *
 * <pre>
 *   Common     1d20      Rare       3d20      Legendary  5d20
 *   Uncommon   2d20      Epic       4d20      Mythic     6d20
 *   a heavy attack rolls one die more; no weapon at all rolls one
 * </pre>
 */
public final class CombatTuning {

    /** Where the game reads it, under the assets folder. */
    public static final String FILE = "combat/combat.json";

    /** Faces on a die. */
    public final int sides;
    private final Map<Rarity, Integer> dice;
    /** Extra dice a heavy attack rolls. */
    public final int heavyExtra;
    /** Dice a swing with nothing in hand rolls. */
    public final int unarmedDice;

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

    private CombatTuning(Map<String, Object> json) {
        Map<String, Object> d = Json.obj(json, "dice");
        sides = Math.max(2, Math.min(20, (int) Json.num(d, "sides", 20)));   // the dice are drawn with 20 faces
        Map<String, Object> per = Json.obj(d, "perRarity");
        int[] standard = {1, 2, 3, 4, 5, 6};
        dice = new EnumMap<>(Rarity.class);
        for (Rarity r : Rarity.values()) {
            dice.put(r, Math.max(1, (int) Json.num(per, r.key(), standard[r.ordinal()])));
        }
        heavyExtra = Math.max(0, (int) Json.num(d, "heavyExtra", 1));
        unarmedDice = Math.max(1, (int) Json.num(d, "unarmed", 1));

        Map<String, Object> m = Json.obj(json, "mana");
        maxMana = Math.max(0, Json.num(m, "max", 100));
        manaRegen = Math.max(0, Json.num(m, "regenPerSecond", 5));
        spellCost = Math.max(0, Json.num(m, "spellCost", 10));
        mysticBaseCost = Math.max(0, Json.num(m, "mysticBaseCost", 5));

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
