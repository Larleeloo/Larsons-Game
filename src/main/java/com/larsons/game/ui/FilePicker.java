package com.larsons.game.ui;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The system's own "open a file" dialog (LWJGL's tinyfd: Windows' and
 * macOS's native ones, zenity or kdialog on Linux), for picking a file from
 * anywhere on the computer - the Sounds menu's "Choose…". The game waits
 * while it is open.
 */
public final class FilePicker {

    private FilePicker() {}

    /**
     * Whether a dialog can be shown here: always on Windows and macOS; on
     * Linux only with zenity or kdialog installed (without either, tinyfd
     * would ask on the console the game was started from, and the game
     * would hang waiting for it).
     */
    public static boolean available() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") || os.contains("mac")) return true;
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank()) continue;
            if (Files.isExecutable(Path.of(dir, "zenity")) || Files.isExecutable(Path.of(dir, "kdialog"))) return true;
        }
        return false;
    }

    /**
     * Ask for one file of the kinds {@code patterns} name ({@code "*.mp3"}),
     * described as {@code description}, starting in {@code start} (or
     * wherever the system likes, if null). Returns the file, or null when the
     * dialog was cancelled or cannot be shown.
     */
    public static Path open(String title, Path start, String description, String... patterns) {
        if (!available()) return null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(patterns.length);
            for (String p : patterns) filters.put(stack.UTF8(p));
            filters.flip();
            String from = start == null ? null : start.toAbsolutePath() + File.separator;
            String chosen = TinyFileDialogs.tinyfd_openFileDialog(title, from, filters, description, false);
            return chosen == null || chosen.isBlank() ? null : Path.of(chosen);
        } catch (Throwable e) {          // no native library, no display: no dialog
            System.err.println("[files] no file dialog: " + e);
            return null;
        }
    }
}
