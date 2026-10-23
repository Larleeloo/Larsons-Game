package com.larsons.game.combat;

import java.util.Arrays;
import java.util.Random;

/**
 * One swing's damage roll: the face each die came up on, and their sum -
 * the damage before a border's bonus.
 */
public record DiceRoll(int sides, int[] faces) {

    public DiceRoll {
        faces = faces.clone();
    }

    /** Roll {@code count} dice of {@code sides} faces. */
    public static DiceRoll roll(int count, int sides, Random random) {
        int[] f = new int[Math.max(1, count)];
        for (int i = 0; i < f.length; i++) f[i] = 1 + random.nextInt(Math.max(1, sides));
        return new DiceRoll(sides, f);
    }

    public int count() { return faces.length; }

    public int face(int die) { return faces[die]; }

    public int total() {
        int t = 0;
        for (int f : faces) t += f;
        return t;
    }

    /** "3d20". */
    public String notation() {
        return faces.length + "d" + sides;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DiceRoll d && d.sides == sides && Arrays.equals(d.faces, faces);
    }

    @Override
    public int hashCode() {
        return 31 * sides + Arrays.hashCode(faces);
    }

    @Override
    public String toString() {
        return notation() + " " + Arrays.toString(faces) + " = " + total();
    }
}
