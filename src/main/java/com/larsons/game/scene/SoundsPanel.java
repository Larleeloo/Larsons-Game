package com.larsons.game.scene;

import com.larsons.game.audio.SoundCategory;
import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.SoundPack;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Game;
import com.larsons.game.gfx.Font;
import com.larsons.game.ui.FilePicker;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Pause → Sounds: every sound in the game, a tab for each {@link
 * SoundCategory} rather than one long list, and a volume for each tab and
 * over them all.
 *
 * <p>Each row is one sound ({@link SoundKeys#in}): its play button, its
 * name, where it comes from now - the game's file in {@code assets/sounds},
 * a file it shares with others like it, the player's own file, muted, or
 * silent - and {@code Choose…} (the system's open-file dialog: any MP3 or
 * WAV on the computer), {@code Mute} and {@code Reset} (back to the game's
 * own). A file dropped on the window over a row is that row's. Choices are
 * the player's alone, saved as they are made ({@code config/sounds.json}).
 */
public final class SoundsPanel {

    private static final float TAB_W = 244, ROW_H = 32;
    private static final String[] AUDIO = {"*.mp3", "*.wav", "*.aif", "*.aiff", "*.au"};

    private final Game game;
    private SoundCategory tab = SoundCategory.MOVEMENT;
    /** The first row shown, scrolled down to with the wheel. */
    private int scroll;
    /** The row under the pointer when last drawn, and the last row played or chosen for (a dropped file's row). */
    private String hovered, picked;
    private Path lastFolder;

    public SoundsPanel(Game game) {
        this.game = game;
    }

    /** Leaving the page: stop what it was playing. */
    public void leave() {
        Sounds.stopPreview();
        hovered = null;
    }

    /**
     * Files dropped on the window while the page is up: the first sound file
     * among them becomes the sound of the row under the pointer (or of the
     * last row played). Returns whether it took them.
     */
    public boolean drop(List<Path> files) {
        String key = hovered != null ? hovered : picked;
        if (key == null) return false;
        for (Path f : files) {
            if (Files.isRegularFile(f) && isAudio(f)) {
                take(key, f);
                return true;
            }
        }
        return false;
    }

    private static boolean isAudio(Path f) {
        String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String a : AUDIO) if (n.endsWith(a.substring(1))) return true;
        return false;
    }

    private void take(String key, Path file) {
        Sounds.choose(key, file);
        lastFolder = file.toAbsolutePath().getParent();
        picked = key;
        if (Sounds.resolve(key).isEmpty()) {
            game.toast("Could not read " + file.getFileName() + " as a sound - use an MP3 or WAV", Theme.WARNING);
            Sounds.play(SoundKeys.ui("error"));
        } else {
            game.toast(name(key) + ": " + file.getFileName(), Theme.OK);
            Sounds.preview(key);
        }
    }

    private static String name(String key) {
        for (SoundKeys.Entry e : SoundKeys.all()) if (e.key().equals(key)) return e.name();
        return key;
    }

    /** Draw the page into {@code (x, y, w, h)} and handle it. */
    public void draw(Ui ui, float x, float y, float w, float h) {
        var settings = game.settings();
        // --- the master volume, and a rescan of assets/sounds
        ui.text(ui.body, "Master volume", x, y + 6, Theme.ITEM);
        double master = ui.slider(x + 150, y + 4, 240, 24, Sounds.masterVolume());
        if (master != Sounds.masterVolume()) {
            Sounds.setMasterVolume(master);
            settings.masterVolume = master;
        }
        ui.text(ui.body, percent(master), x + 404, y + 6, Theme.ITEM_SELECTED);
        if (ui.button("Rescan sound files", x + w - 200, y, 200, 32)) {
            SoundPack.reload();
            Sounds.preload();
            game.toast("Sound files read again from " + game.relative(SoundPack.root()), Theme.HINT);
        }

        // --- the categories
        float ty = y + 48;
        for (SoundCategory c : SoundCategory.values()) {
            String label = c.label() + "  (" + SoundKeys.in(c).size() + ")";
            if (ui.button(label, x, ty, TAB_W, 34, c == tab, true) && c != tab) {
                tab = c;
                scroll = 0;
                Sounds.stopPreview();
            }
            ty += 40;
        }

        // --- the tab: its volume, what is in it, its rows
        float rx = x + TAB_W + 20, rw = w - TAB_W - 20, ry = y + 48;
        ui.text(ui.large, tab.label(), rx, ry, Theme.TITLE);
        ui.text(ui.body, "Volume", rx + rw - 330, ry + 4, Theme.ITEM);
        double v = ui.slider(rx + rw - 260, ry + 2, 200, 24, Sounds.categoryVolume(tab));
        if (v != Sounds.categoryVolume(tab)) {
            Sounds.setCategoryVolume(tab, v);
            settings.soundVolumes.put(tab.key(), v);
        }
        ui.text(ui.body, percent(v), rx + rw - 50, ry + 4, Theme.ITEM_SELECTED);
        ui.text(ui.small, fit(ui.small, tab.blurb(), rw), rx, ry + 34, Theme.ITEM_DISABLED);

        List<SoundKeys.Entry> rows = SoundKeys.in(tab);
        float listY = ry + 60, listH = y + h - 26 - listY;
        int fits = Math.max(1, (int) (listH / ROW_H));
        var in = ui.input();
        if (in.scroll() != 0 && ui.hover(rx, listY, rw, listH)) {
            scroll -= (int) Math.signum(in.scroll());
            in.consumeScroll();
        }
        scroll = Math.max(0, Math.min(scroll, rows.size() - fits));
        hovered = null;
        for (int i = scroll; i < Math.min(rows.size(), scroll + fits); i++) {
            row(ui, rows.get(i), rx, listY + (i - scroll) * ROW_H, rw - 10);
        }
        if (rows.size() > fits) {      // where in the list the rows shown are
            float barH = listH * fits / rows.size(), barY = listY + (listH - barH) * scroll / (rows.size() - fits);
            ui.rect(rx + rw - 4, listY, 4, listH, Theme.withAlpha(Theme.PANEL_EDGE, 0.5f));
            ui.rect(rx + rw - 4, barY, 4, barH, Theme.ACCENT);
        }
        ui.text(ui.small, "Drop an MP3 or WAV on a row to use it  ·  the wheel scrolls  ·  your sounds are kept in "
                + game.relative(settings.config.resolve("sounds.json")) + ", the game's in "
                + game.relative(SoundPack.root()) + "/", x, y + h - 18, Theme.HINT);
    }

    /** One sound: play, its name, where it comes from, Choose… Mute Reset. */
    private void row(Ui ui, SoundKeys.Entry e, float x, float y, float w) {
        String key = e.key();
        if (ui.hover(x, y, w, ROW_H)) {
            hovered = key;
            ui.rect(x - 4, y, w + 8, ROW_H - 2, Theme.withAlpha(Theme.BUTTON_HOVER, 0.5f));
        }
        Sounds.Resolution r = Sounds.describe(key);
        boolean audible = r.source() == Sounds.Source.FILE || r.source() == Sounds.Source.PACK;
        if (ui.button("▶", x, y + 2, 30, ROW_H - 6, false, audible)) {
            picked = key;
            Sounds.preview(key);
        }
        float buttons = 90 + 70 + 70 + 12, sourceW = Math.min(250, w * 0.3f);
        float nameW = w - 40 - sourceW - buttons - 16;
        ui.text(ui.body, fit(ui.body, e.name(), nameW), x + 40, y + (ROW_H - ui.body.lineHeight()) / 2 - 1,
                key.equals(picked) ? Theme.ITEM_SELECTED : Theme.ITEM);
        float sx = x + w - buttons - sourceW - 8;
        ui.text(ui.small, fit(ui.small, source(key, r), sourceW), sx, y + (ROW_H - ui.small.lineHeight()) / 2,
                colour(r.source()));
        float bx = x + w - buttons;
        if (ui.button("Choose…", bx, y + 2, 90, ROW_H - 6)) choose(key, e.name());
        boolean muted = r.source() == Sounds.Source.MUTED;
        if (ui.button(muted ? "Unmute" : "Mute", bx + 96, y + 2, 70, ROW_H - 6, false, audible || muted)) {
            if (muted) Sounds.reset(key);
            else Sounds.mute(key);
            Sounds.stopPreview();
        }
        if (ui.button("Reset", bx + 172, y + 2, 70, ROW_H - 6, false, Sounds.get(key) != null)) {
            Sounds.reset(key);
            game.toast(e.name() + ": the game's own sound again", Theme.HINT);
        }
    }

    private void choose(String key, String name) {
        picked = key;
        if (!FilePicker.available()) {
            game.toast("No file dialog here (install zenity) - drop the file on the row instead", Theme.WARNING);
            return;
        }
        Path f = FilePicker.open("A sound for: " + name, lastFolder, "Sound files (MP3, WAV)", AUDIO);
        if (f != null) take(key, f);
    }

    /** Where a row's sound comes from, in words. */
    private String source(String key, Sounds.Resolution r) {
        return switch (r.source()) {
            case FILE -> (r.shared(key) ? "Yours (shared): " : "Yours: ") + r.file().getFileName();
            case PACK -> (r.shared(key) ? "Shared: " : "Game: ") + packPath(r.file());
            case MUTED -> r.shared(key) ? "Muted (shared)" : "Muted";
            case SILENT -> "Silent - no file yet";
        };
    }

    private static String packPath(Path file) {
        Path root = SoundPack.root().toAbsolutePath().normalize(), f = file.toAbsolutePath().normalize();
        return f.startsWith(root) ? root.relativize(f).toString().replace('\\', '/') : f.getFileName().toString();
    }

    private static float[] colour(Sounds.Source s) {
        return switch (s) {
            case FILE -> Theme.OK;
            case PACK -> Theme.withAlpha(Theme.HINT, 0.7f);
            case MUTED -> Theme.WARNING;
            case SILENT -> Theme.ITEM_DISABLED;
        };
    }

    private static String percent(double v) {
        return Math.round(v * 100) + "%";
    }

    /** {@code s}, cut short with an ellipsis to fit {@code width}. */
    private static String fit(Font f, String s, float width) {
        if (f.width(s) <= width) return s;
        String cut = s;
        while (cut.length() > 1 && f.width(cut + "…") > width) cut = cut.substring(0, cut.length() - 1);
        return cut + "…";
    }
}
