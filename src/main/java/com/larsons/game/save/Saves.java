package com.larsons.game.save;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The save slots ({@code config/saves/slot1.json} ...) and the playthrough
 * being played now: {@link #active} is set when a game is begun or loaded
 * from the main menu and cleared when she goes back to it; while it is set,
 * the levels she enters are played as her ({@code scene/DemoScene}) and what
 * she does there is written back when she returns to the map.
 */
public final class Saves {

    /** How many slots there are. */
    public static final int SLOTS = 3;

    private static Path folder = Path.of("config", "saves");
    private static SaveGame active;
    private static int activeSlot = -1;

    private Saves() {}

    /** Keep the slots in {@code dir} from now on (the game's config folder; tests). */
    public static void useFolder(Path dir) {
        folder = dir;
    }

    public static Path file(int slot) {
        return folder.resolve("slot" + (slot + 1) + ".json");
    }

    public static boolean exists(int slot) {
        return Files.isRegularFile(file(slot));
    }

    /** The playthrough in {@code slot}, or null (empty, or it cannot be read). */
    public static SaveGame peek(int slot) {
        if (!exists(slot)) return null;
        try {
            return SaveGame.load(file(slot));
        } catch (IOException | RuntimeException e) {
            System.err.println("[save] slot " + (slot + 1) + " cannot be read: " + e.getMessage());
            return null;
        }
    }

    /** The slot saved to last, or -1 when all are empty. */
    public static int latest() {
        int best = -1;
        String when = "";
        for (int i = 0; i < SLOTS; i++) {
            SaveGame s = peek(i);
            if (s != null && s.saved().compareTo(when) > 0) {
                when = s.saved();
                best = i;
            }
        }
        return best;
    }

    /** The first empty slot, or -1. */
    public static int free() {
        for (int i = 0; i < SLOTS; i++) if (!exists(i)) return i;
        return -1;
    }

    /** The playthrough being played, or null (the demo, a play-test, a level from the Dev tab). */
    public static SaveGame active() { return active; }

    public static int activeSlot() { return activeSlot; }

    /** Play {@code save}, kept in {@code slot}. */
    public static void play(SaveGame save, int slot) {
        active = save;
        activeSlot = slot;
    }

    /** No playthrough is being played any more (back at the main menu). */
    public static void stop() {
        active = null;
        activeSlot = -1;
    }

    /** Write the playthrough being played to its slot; whether it was written. */
    public static boolean write() {
        if (active == null || activeSlot < 0) return false;
        try {
            active.save(file(activeSlot));
            return true;
        } catch (IOException e) {
            System.err.println("[save] cannot save: " + e.getMessage());
            return false;
        }
    }

    public static void delete(int slot) {
        try {
            Files.deleteIfExists(file(slot));
        } catch (IOException e) {
            System.err.println("[save] cannot delete slot " + (slot + 1) + ": " + e.getMessage());
        }
        if (slot == activeSlot) stop();
    }
}
