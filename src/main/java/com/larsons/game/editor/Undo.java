package com.larsons.game.editor;

import com.larsons.game.ambient.Life;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelObject;
import com.larsons.game.world.Sky;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The level editor's undo and redo: before each change, the part of the
 * level it touches is kept ({@link #before}) - the objects, the sculpted
 * heights, the painted ground, the sky, or all of it for a change of size -
 * and undoing puts it back, keeping what it replaced to redo.
 *
 * <p>At most {@link #DEPTH} steps back; a new change after an undo drops
 * what could have been redone.
 */
final class Undo {

    /** How many steps back it goes. */
    static final int DEPTH = 60;
    /**
     * The most its steps keep, bytes: a big level's ground is megabytes a
     * step, so on one of those it goes fewer steps back rather than filling
     * the memory.
     */
    static final long BUDGET = 384L << 20;

    /** What a change touches. */
    enum Part { OBJECTS, HEIGHTS, GROUND, WATER, SKY, ALL }

    /** What a part of the level was, before a change: what is not kept is null. */
    private record State(String label, double size, List<LevelObject> objects, float[] heights, byte[][] ground,
                         byte[] water, double sea, Sky sky, String name, String music, Life life,
                         com.larsons.game.world.Surroundings surroundings) {}

    private final Deque<State> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    /** Keep {@code part} of {@code level} as it is, before {@code label} changes it. */
    void before(Level level, Part part, String label) {
        undo.push(capture(level, part, label));
        redo.clear();
        while (undo.size() > DEPTH || undo.size() > 1 && bytes(undo) > BUDGET) undo.removeLast();
    }

    /** About how many bytes {@code states} keep. */
    private static long bytes(Deque<State> states) {
        long n = 0;
        for (State s : states) {
            if (s.heights() != null) n += s.heights().length * 4L;
            if (s.ground() != null) for (byte[] g : s.ground()) if (g != null) n += g.length;
            if (s.water() != null) n += s.water().length;
        }
        return n;
    }

    private static State capture(Level level, Part part, String label) {
        boolean all = part == Part.ALL;
        return new State(label, level.size(),
                all || part == Part.OBJECTS ? level.copyObjects() : null,
                all || part == Part.HEIGHTS ? level.heights().heights() : null,
                all || part == Part.GROUND ? level.ground().snapshot() : null,
                all || part == Part.WATER ? level.water().bytes() : null, level.water().seaLevel(),
                all || part == Part.SKY ? level.sky() : null,
                all ? level.name() : null,
                all || part == Part.SKY ? level.music() : null,
                all || part == Part.SKY ? level.life().copy() : null,
                all || part == Part.SKY ? level.surroundings() : null);
    }

    boolean canUndo() { return !undo.isEmpty(); }

    boolean canRedo() { return !redo.isEmpty(); }

    /** What undoing would undo, or null. */
    String nextUndo() { return undo.isEmpty() ? null : undo.peek().label(); }

    String nextRedo() { return redo.isEmpty() ? null : redo.peek().label(); }

    /** Undo the last change to {@code level}: what it undid, or null when there was nothing. */
    String undo(Level level) {
        return step(level, undo, redo);
    }

    /** Do again what was last undone: what it did, or null. */
    String redo(Level level) {
        return step(level, redo, undo);
    }

    private static String step(Level level, Deque<State> from, Deque<State> to) {
        if (from.isEmpty()) return null;
        State s = from.pop();
        to.push(capture(level, s.heights() != null && s.ground() != null && s.objects() != null ? Part.ALL
                : s.objects() != null ? Part.OBJECTS : s.heights() != null ? Part.HEIGHTS
                : s.ground() != null ? Part.GROUND : s.water() != null ? Part.WATER : Part.SKY, s.label()));
        if (s.size() != level.size()) level.resetSize(s.size());
        if (s.objects() != null) level.restoreObjects(s.objects());
        if (s.heights() != null) level.heights().setHeights(s.heights());
        if (s.ground() != null) level.ground().restore(s.ground());
        if (s.water() != null) {
            level.water().setBytes(s.water());
            level.water().setSeaLevel(s.sea());
        }
        if (s.sky() != null) level.setSky(s.sky());
        if (s.name() != null) level.setName(s.name());
        if (s.music() != null) level.setMusic(s.music());
        if (s.life() != null) level.setLife(s.life().copy());
        if (s.surroundings() != null) level.setSurroundings(s.surroundings());
        return s.label();
    }
}
