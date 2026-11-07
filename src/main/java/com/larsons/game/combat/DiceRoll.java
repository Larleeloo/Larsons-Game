package com.larsons.game.combat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * One swing's damage roll: the dice, the face each came up on, and what each
 * does with the dice before it ({@link Op}) - added, taken away, multiplied
 * by, divided by, or a % die scaling the damage by its face's percent.
 *
 * <p>The dice are worked out left to right, as they lie over her head: the
 * total so far, then each die doing its sum to it - {@code 2d8 + 1d6 × 1d4}
 * rolled 5, 3, 4, 2 is ((5 + 3) + 4) × 2 = 24. A % die adds its face's
 * percent of the total so far (a d% come up 40: +40%). Rounded to a whole
 * number at the end, never below 0.
 */
public record DiceRoll(int sides, int[] faces, int[] dieSides, Op[] ops) {

    /** What a die does with the total of the dice before it. */
    public enum Op {
        ADD("+", "add"), SUB("−", "subtract"), MUL("×", "multiply"), DIV("÷", "divide"), PCT("%", "scale");

        private final String symbol, word;

        Op(String symbol, String word) {
            this.symbol = symbol;
            this.word = word;
        }

        /** How the dice say it: + − × ÷ %. */
        public String symbol() { return symbol; }

        /** Its name in the Forge: add, subtract, multiply, divide, scale. */
        public String word() { return word; }

        /** The key a forged weapon's file writes: + - x / %. */
        public String key() {
            return switch (this) {
                case ADD -> "+";
                case SUB -> "-";
                case MUL -> "x";
                case DIV -> "/";
                case PCT -> "%";
            };
        }

        public static Op byKey(String key) {
            if (key == null) return ADD;
            return switch (key.trim().toLowerCase(Locale.ROOT)) {
                case "-", "−", "sub", "subtract" -> SUB;
                case "x", "*", "×", "mul", "multiply" -> MUL;
                case "/", "÷", "div", "divide" -> DIV;
                case "%", "pct", "scale" -> PCT;
                default -> ADD;
            };
        }

        /** The next one round (the Forge's button). */
        public Op next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** A group of dice of one kind doing one sum: {@code count} d{@code sides}, {@code op}. */
    public record Group(int count, int sides, Op op) {
        public Group {
            count = Math.max(1, Math.min(10, count));
            if (sides < 2) sides = 2;
            if (op == null) op = Op.ADD;
        }

        /** "× 1d4", "+ 2d%". */
        public String notation() {
            return op.symbol() + " " + count + (sides == 100 ? "d%" : "d" + sides);
        }
    }

    public DiceRoll {
        faces = faces.clone();
        if (dieSides == null) {
            dieSides = new int[faces.length];
            Arrays.fill(dieSides, sides);
        } else {
            dieSides = dieSides.clone();
        }
        if (ops == null) {
            ops = new Op[faces.length];
            Arrays.fill(ops, Op.ADD);
        } else {
            ops = ops.clone();
        }
    }

    /** {@code faces} of dice of {@code sides}, all added up. */
    public DiceRoll(int sides, int[] faces) {
        this(sides, faces, null, null);
    }

    /** Roll {@code count} dice of {@code sides} faces, to be added up. */
    public static DiceRoll roll(int count, int sides, Random random) {
        int[] f = new int[Math.max(1, count)];
        for (int i = 0; i < f.length; i++) f[i] = 1 + random.nextInt(Math.max(1, sides));
        return new DiceRoll(sides, f);
    }

    /**
     * Roll {@code count} dice of {@code sides} added up, then each of {@code
     * more}'s groups in turn, doing its sum.
     */
    public static DiceRoll roll(int count, int sides, List<Group> more, Random random) {
        List<Group> all = new ArrayList<>();
        all.add(new Group(Math.max(1, count), sides, Op.ADD));
        if (more != null) all.addAll(more);
        int n = 0;
        for (Group g : all) n += g.count();
        int[] f = new int[n], s = new int[n];
        Op[] o = new Op[n];
        int i = 0;
        for (Group g : all) {
            for (int k = 0; k < g.count(); k++, i++) {
                s[i] = g.sides();
                o[i] = g.op();
                f[i] = 1 + random.nextInt(Math.max(1, g.sides()));
            }
        }
        return new DiceRoll(sides, f, s, o);
    }

    public int count() { return faces.length; }

    public int face(int die) { return faces[die]; }

    /** How many sides die {@code die} has (100: a d%). */
    public int sidesOf(int die) { return dieSides[die]; }

    /** What die {@code die} does with the dice before it. */
    public Op op(int die) { return ops[die]; }

    /** Whether every die is added (a plain roll). */
    public boolean plain() {
        for (Op o : ops) if (o != Op.ADD) return false;
        for (int s : dieSides) if (s != sides) return false;
        return true;
    }

    /** The damage: the dice worked out left to right (see the class comment). */
    public int total() {
        double t = 0;
        for (int i = 0; i < faces.length; i++) {
            double f = faces[i];
            switch (ops[i]) {
                case ADD -> t += f;
                case SUB -> t -= f;
                case MUL -> t *= f;
                case DIV -> t /= Math.max(1, f);
                case PCT -> t += t * f / 100.0;
            }
        }
        return (int) Math.max(0, Math.round(t));
    }

    /** "3d20" ("2d%" for the percentile dice); a mixed roll "2d8 + 1d6 × 1d4". */
    public String notation() {
        if (plain()) return faces.length + (sides == 100 ? "d%" : "d" + sides);
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < faces.length) {
            int j = i;
            while (j < faces.length && dieSides[j] == dieSides[i] && ops[j] == ops[i]) j++;
            if (i > 0) out.append(' ').append(ops[i].symbol()).append(' ');
            else if (ops[i] != Op.ADD) out.append(ops[i].symbol());
            out.append(j - i).append(dieSides[i] == 100 ? "d%" : "d" + dieSides[i]);
            i = j;
        }
        return out.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DiceRoll d && d.sides == sides && Arrays.equals(d.faces, faces)
                && Arrays.equals(d.dieSides, dieSides) && Arrays.equals(d.ops, ops);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * sides + Arrays.hashCode(faces)) + Arrays.hashCode(ops);
    }

    @Override
    public String toString() {
        return notation() + " " + Arrays.toString(faces) + " = " + total();
    }
}
