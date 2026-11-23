package com.larsons.game.combat;

import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Stance;
import com.larsons.game.world.Border;
import com.larsons.game.world.Forged;
import com.larsons.game.world.Item;
import com.larsons.game.world.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * A weapon's damage per second, roughly: what its dice come to on average
 * ({@link #mean}) times what its borders add, over how long one of its
 * attacks takes - a swing, a thrust, a cast, a bow drawn to the loose, a
 * crossbow fired - at its rate of fire ({@link Forged#fireRate}). Plain
 * attacks only, every one landing: no heavy attacks, no cards, no armour in
 * the way. Shown in the inventory over a weapon.
 */
public final class WeaponStats {

    private WeaponStats() {}

    /**
     * A weapon's numbers.
     *
     * @param perHit    the damage an attack does on average, its borders' included
     * @param cycle     seconds an attack takes, start to start
     * @param perSecond {@code perHit / cycle}
     * @param ranged    whether it shoots
     * @param accuracy  how true it shoots, percent
     */
    public record Dps(double perHit, double cycle, double perSecond, boolean ranged, int accuracy) {

        /** In a line: "≈ 12.4 damage a second (4.5 a hit, a swing every 0.36 s)". */
        public String line() {
            return "≈ " + num(perSecond) + " damage a second (" + num(perHit) + " a hit, "
                    + (ranged ? "a shot" : "a swing") + " every " + num(cycle) + " s)";
        }
    }

    /** The average of {@code count} dice of {@code sides} (a d% is 1 - 100). */
    public static double mean(int count, int sides) {
        return count * (Math.max(1, sides) + 1) / 2.0;
    }

    /**
     * What a roll of {@code count} dice of {@code sides}, then each of {@code
     * more} done to the total in turn (+, −, ×, ÷, %), comes to on average.
     */
    public static double mean(int count, int sides, List<DiceRoll.Group> more) {
        double t = mean(count, sides);
        for (DiceRoll.Group g : more) {
            for (int i = 0; i < g.count(); i++) {
                double f = mean(1, g.sides());
                switch (g.op()) {
                    case ADD -> t += f;
                    case SUB -> t -= f;
                    case MUL -> t *= f;
                    case DIV -> t *= inverseMean(g.sides());
                    case PCT -> t += t * f / 100.0;
                }
            }
        }
        return Math.max(0, t);
    }

    /** The average of 1 / a die of {@code sides}. */
    private static double inverseMean(int sides) {
        double sum = 0;
        for (int k = 1; k <= sides; k++) sum += 1.0 / k;
        return sum / Math.max(1, sides);
    }

    /** {@link #of(Item, CombatTuning, ToDoubleFunction)} with the stand-in's animation lengths. */
    public static Dps of(Item weapon, CombatTuning tuning) {
        return of(weapon, tuning, s -> s.defaultFrames() / AnimState.DEFAULT_FPS);
    }

    /**
     * {@code weapon}'s numbers (null: bare hands), its attacks as long as
     * {@code durations} says each animation is.
     */
    public static Dps of(Item weapon, CombatTuning tuning, ToDoubleFunction<AnimState> durations) {
        Forged f = weapon == null ? null : weapon.forged();
        int dice = weapon == null ? tuning.unarmedDice : f != null ? f.dice() : tuning.dice(weapon.rarity());
        int sides = f != null ? f.sides() : tuning.sides(weapon == null ? null : weapon.def());
        double hit = mean(dice, sides, f != null ? f.extra() : List.of());
        if (weapon != null && weapon.has(Border.MYSTIC)) hit *= Border.MYSTIC_DAMAGE;
        if (weapon != null && weapon.has(Border.VENOM)) hit *= 1 + Border.VENOM_SHARE;
        Stance st = weapon == null ? Stance.SWORD : weapon.def().stance();
        AnimState attack = st == null ? null : st.attack(false);
        if (attack == null) attack = AnimState.ATTACK;
        double cycle = durations.applyAsDouble(attack);
        boolean ranged = shoots(weapon, st);
        if (st != null && st.drawn()) {
            // the bow: drawn till it is ready, then loosed
            cycle = cycle * Player.DRAW_READY + durations.applyAsDouble(st.release(false));
        }
        double rate = f != null && ranged ? f.fireRate() : 1;
        cycle = Math.max(0.05, cycle / rate);
        return new Dps(hit, cycle, hit / cycle, ranged, ranged ? Combat.accuracy(weapon) : 100);
    }

    /** Whether {@code weapon} (of stance {@code st}) shoots: a bow, a crossbow, a casting staff, a wand. */
    static boolean shoots(Item weapon, Stance st) {
        if (weapon == null || st == null) return false;
        return switch (st) {
            case BOW, CROSSBOW, WAND -> true;
            case STAFF -> Combat.casts(weapon);
            default -> false;
        };
    }

    public static String num(double v) {
        return v >= 100 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }
}
