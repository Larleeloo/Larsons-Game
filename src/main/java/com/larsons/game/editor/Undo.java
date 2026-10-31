package com.larsons.game.editor;

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

    /** What a change touches. */
    enum Part { OBJECTS, HEIGHTS, GROUND, SKY, ALL }

    /** What a part of the level was, before a change: what is not kept is null. */
    private record State(String label, double size, List<LevelObject> objects, float[] heights, byte[] ground,
                         Sky sky, String name, String music) {}

    private final Deque<State> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    /** Keep {@code part} of {@code level} as it is, before {@code label} changes it. */
    void before(Level level, Part part, String label) {
        undo.push(capture(level, part, label));
        while (undo.size() > DEPTH) undo.removeLast();
        redo.clear();
    }

    private static State capture(Level level, Part part, String label) {
        boolean all = part == Part.ALL;
        return new State(label, level.size(),
                all || part == Part.OBJECTS ? level.copyObjects() : null,
                all || part == Part.HEIGHTS ? level.heights().heights() : null,
                all || part == Part.GROUND ? level.ground().rgba() : null,
                all || part == Part.SKY ? level.sky() : null,
                all ? level.name() : null,
                all || part == Part.SKY ? level.music() : null);
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
                : s.ground() != null ? Part.GROUND : Part.SKY, s.label()));
        if (s.size() != level.size()) level.resetSize(s.size());
        if (s.objects() != null) level.restoreObjects(s.objects());
        if (s.heights() != null) level.heights().setHeights(s.heights());
        if (s.ground() != null) level.ground().setRgba(s.ground());
        if (s.sky() != null) level.setSky(s.sky());
        if (s.name() != null) level.setName(s.name());
        if (s.music() != null) level.setMusic(s.music());
        return s.label();
    }
}
