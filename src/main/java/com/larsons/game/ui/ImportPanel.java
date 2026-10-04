package com.larsons.game.ui;

import com.larsons.game.core.Game;
import com.larsons.game.importer.SpriteImport;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteNames;
import com.larsons.game.sprite.SpriteProfile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The drag-and-drop importer's window: shows what a drop contained, lets the
 * player choose which layer it is and what to call it, then saves it into
 * {@code assets/sprites/} on a background thread and reloads the library so
 * the new art is on the character straight away.
 *
 * <p>Opens over any scene — the main menu or the demo — whenever files are
 * dropped on the game window.
 */
public final class ImportPanel {

    private enum Phase { HIDDEN, REVIEW, SAVING, DONE }

    private Phase phase = Phase.HIDDEN;
    private SpriteImport.Plan plan;
    private Slot slot;
    private final StringBuilder name = new StringBuilder();
    private boolean editingName;
    private boolean wear = true;
    /** The sprites folder the sheets being saved go into. */
    private Path target;
    private CompletableFuture<SpriteImport.Result> saving;
    private final AtomicInteger progress = new AtomicInteger();
    private SpriteImport.Result result;
    private String error;

    public boolean visible() {
        return phase != Phase.HIDDEN;
    }

    /** Read a drop and show the review. */
    public void open(List<Path> dropped) {
        plan = SpriteImport.plan(dropped);
        slot = plan.slotGuess();
        name.setLength(0);
        name.append(plan.itemGuess());
        editingName = false;
        result = null;
        error = null;
        phase = Phase.REVIEW;
    }

    public void close() {
        phase = Phase.HIDDEN;
        plan = null;
    }

    /**
     * Save the open review as if Save were clicked — for scripted runs.
     * {@code slotAndName} ("hat/red_cap") overrides the guesses when given.
     */
    public void scriptSave(Game game, String slotAndName) {
        if (phase != Phase.REVIEW || plan.empty()) return;
        if (slotAndName != null && slotAndName.contains("/")) {
            String[] parts = slotAndName.split("/", 2);
            Slot s = Slot.byKey(parts[0]);
            if (s != null) slot = s;
            name.setLength(0);
            name.append(parts[1]);
        }
        start(game, SpriteNames.sanitize(name.toString()));
    }

    /** Handle input and draw. Call inside {@code ui.begin()/end()}. */
    public void draw(Ui ui, Game game) {
        if (phase == Phase.HIDDEN) return;
        ui.scrim();
        float w = Math.min(820, ui.width() - 40), h = Math.min(600, ui.height() - 40);
        float x = (ui.width() - w) / 2, y = (ui.height() - h) / 2;
        ui.panel(x, y, w, h);
        float pad = 24, cx = x + pad, cy = y + pad, inner = w - pad * 2;
        ui.text(ui.large, "Import sprite sheets", cx, cy, Theme.TITLE);
        cy += 40;

        switch (phase) {
            case REVIEW -> review(ui, game, x, y, w, h, cx, cy, inner);
            case SAVING -> saving(ui, game, cx, cy, inner);
            case DONE -> done(ui, game, x, y, w, h, cx, cy, inner);
            default -> { }
        }
    }

    private void review(Ui ui, Game game, float x, float y, float w, float h,
                        float cx, float cy, float inner) {
        var in = ui.input();
        if (plan.empty()) {
            ui.paragraph(ui.body, "Nothing in that drop looked like a sprite sheet.\n\n"
                    + "Name files <state>_<elevation>_<direction>.png — for example "
                    + "walk_middle_ne.png — or drop a folder of them. Loose frames numbered "
                    + "0001, 0002 … are stitched into a sheet for you.", cx, cy, inner, Theme.ITEM);
            listUnrecognised(ui, cx, y + h - 190, inner);
            if (ui.button("Close", x + w - 164, y + h - 60, 140, 40, true, true)
                    || in.pressed(GLFW_KEY_ESCAPE) || in.pressed(GLFW_KEY_ENTER)) {
                in.consumeKey(GLFW_KEY_ESCAPE);
                close();
            }
            return;
        }

        int files = plan.sheets().stream().mapToInt(s -> s.files().size()).sum()
                + (plan.icon() != null ? 1 : 0);
        ui.text(ui.body, plan.sheets().size() + " sheet" + (plan.sheets().size() == 1 ? "" : "s")
                + " from " + files + " file" + (files == 1 ? "" : "s")
                + (plan.icon() != null ? "  ·  pickup icon" : "")
                + (plan.unrecognised().isEmpty() ? "" : "  ·  " + plan.unrecognised().size()
                + " not recognised"), cx, cy, Theme.HINT);
        cy += 34;

        // Layer (slot) selector.
        if (!plan.sheets().isEmpty()) {
            ui.text(ui.body, "Layer", cx, cy + 8, Theme.ITEM);
            float sx = cx + 110;
            if (ui.button("<", sx, cy, 36, 34) || (!editingName && in.repeated(GLFW_KEY_LEFT))) {
                slot = Slot.values()[Math.floorMod(slot.ordinal() - 1, Slot.values().length)];
            }
            ui.rect(sx + 42, cy, 220, 34, Theme.BUTTON);
            ui.textCentered(ui.body, slot.label() + "  (" + slot.key() + ")", sx + 42 + 110, cy + 8,
                    Theme.ITEM_SELECTED);
            if (ui.button(">", sx + 268, cy, 36, 34) || (!editingName && in.repeated(GLFW_KEY_RIGHT))) {
                slot = Slot.values()[(slot.ordinal() + 1) % Slot.values().length];
            }
            cy += 44;
        }

        // Item name.
        ui.text(ui.body, "Name", cx, cy + 8, Theme.ITEM);
        float nx = cx + 110, nw = 304;
        ui.rect(nx, cy, nw, 34, editingName ? Theme.BUTTON_HOVER : Theme.BUTTON);
        ui.outline(nx, cy, nw, 34, editingName ? 2 : 1, editingName ? Theme.ACCENT : Theme.PANEL_EDGE);
        boolean blink = (System.nanoTime() / 500_000_000L) % 2 == 0;
        ui.text(ui.body, name + (editingName && blink ? "|" : ""), nx + 8, cy + 8, Theme.TITLE);
        if (ui.clicked(nx, cy, nw, 34)) editingName = true;
        else if (in.mousePressed(GLFW_MOUSE_BUTTON_LEFT)) editingName = false;
        if (editingName) {
            name.append(in.typedText());
            if (in.repeated(GLFW_KEY_BACKSPACE) && !name.isEmpty()) name.setLength(name.length() - 1);
        }
        ui.text(ui.small, "click to rename", nx + nw + 10, cy + 10, Theme.ITEM_DISABLED);
        cy += 46;

        String item = SpriteNames.sanitize(name.toString());
        Slot into = slot == null ? Slot.OTHER : slot;
        Path root = sheetsRoot(game, into, item);
        String target = plan.sheets().isEmpty()
                ? game.relative(game.sprites().root()) + "/items/" + item + "/icon.png"
                : game.relative(SpriteImport.target(root, into, item)) + "/";
        int replace = SpriteImport.wouldReplace(plan, root, game.sprites().root(), into, item);
        ui.text(ui.body, "Saves to  " + target, cx, cy, Theme.ACCENT);
        cy += 24;
        if (replace > 0) {
            ui.text(ui.small, "replaces " + replace + " existing file" + (replace == 1 ? "" : "s"),
                    cx, cy, Theme.WARNING);
        }
        cy += 26;

        // What is in it, per state.
        SpriteProfile d = SpriteProfile.defaults();
        Map<AnimState, Integer> views = plan.viewsPerState();
        for (AnimState s : AnimState.values()) {
            Integer n = views.get(s);
            // the first six always (what a set needs); any other only when the drop has it
            if (n == null && s.root() != s) continue;
            String frames = plan.sheets().stream().filter(p -> p.state() == s).findFirst()
                    .map(p -> {
                        int[] fs = p.frameSize(d.frameWidth(), d.frameHeight());
                        return p.frames(d.frameWidth(), d.frameHeight()) + " frames of " + fs[0] + "×" + fs[1];
                    }).orElse("");
            float[] c = n == null ? Theme.ITEM_DISABLED : n == 24 ? Theme.OK : Theme.ITEM;
            ui.text(ui.body, s.label(), cx, cy, c);
            ui.text(ui.body, (n == null ? 0 : n) + " / 24 views", cx + 110, cy, c);
            ui.text(ui.small, frames, cx + 250, cy + 2, Theme.ITEM_DISABLED);
            cy += 24;
        }
        cy += 6;
        for (String warn : plan.warnings().subList(0, Math.min(3, plan.warnings().size()))) {
            cy += ui.paragraph(ui.small, "! " + warn, cx, cy, inner, Theme.WARNING);
        }
        listUnrecognised(ui, cx, cy, inner);

        // Wear toggle and the two buttons.
        float by = y + h - 60;
        if (!plan.sheets().isEmpty()) {
            ui.rect(cx, by + 10, 20, 20, Theme.BUTTON);
            ui.outline(cx, by + 10, 20, 20, 1, Theme.PANEL_EDGE);
            if (wear) ui.rect(cx + 5, by + 15, 10, 10, Theme.ACCENT);
            ui.text(ui.body, "Wear it after importing", cx + 30, by + 11, Theme.ITEM);
            if (ui.clicked(cx, by + 6, 240, 28)) wear = !wear;
        }
        boolean cancel = ui.button("Cancel", x + w - 324, by, 140, 40)
                || in.pressed(GLFW_KEY_ESCAPE);
        boolean save = ui.button("Save", x + w - 164, by, 140, 40, true, true)
                || (in.pressed(GLFW_KEY_ENTER) && !editingName);
        if (in.pressed(GLFW_KEY_ENTER) && editingName) editingName = false;
        in.consumeKey(GLFW_KEY_ESCAPE);
        in.consumeKey(GLFW_KEY_ENTER);
        if (cancel) {
            close();
        } else if (save) {
            start(game, item);
        }
    }

    private void listUnrecognised(Ui ui, float cx, float cy, float inner) {
        List<String> u = plan.unrecognised();
        if (u.isEmpty()) return;
        ui.text(ui.small, "Not recognised (left alone):", cx, cy, Theme.ITEM_DISABLED);
        for (int i = 0; i < Math.min(3, u.size()); i++) {
            ui.text(ui.small, "  " + u.get(i), cx, cy + 18 * (i + 1), Theme.ITEM_DISABLED);
        }
        if (u.size() > 3) {
            ui.text(ui.small, "  … and " + (u.size() - 3) + " more", cx, cy + 72, Theme.ITEM_DISABLED);
        }
    }

    /**
     * The sprites folder an import's sheets go into: the one the body worn
     * reads its items from (assets/sprites_<body>/ for a body with a folder of
     * its own), so what is imported can be worn straight away; a body's own
     * sheets into the folder that body is in (a new body: the default one).
     * Pickup icons always go into the default folder.
     */
    static Path sheetsRoot(Game game, Slot slot, String item) {
        var lib = game.sprites();
        String key = slot == Slot.BODY ? lib.rootOf(item) : lib.rootOf(game.wardrobe().get(Slot.BODY));
        return lib.root(key);
    }

    private void start(Game game, String item) {
        Slot s = slot == null ? Slot.OTHER : slot;
        SpriteImport.Plan p = plan;
        Path root = sheetsRoot(game, s, item);
        Path icons = game.sprites().root();
        target = root;
        progress.set(0);
        phase = Phase.SAVING;
        saving = CompletableFuture.supplyAsync(() -> SpriteImport.save(p, root, icons, s, item, progress::set));
    }

    private void saving(Ui ui, Game game, float cx, float cy, float inner) {
        int total = Math.max(1, plan.sheets().size());
        ui.text(ui.body, "Writing sheets into " + game.relative(target) + " …  " + progress.get() + " / " + total,
                cx, cy, Theme.ITEM);
        ui.rect(cx, cy + 34, inner, 14, Theme.BUTTON);
        ui.rect(cx, cy + 34, inner * progress.get() / total, 14, Theme.ACCENT);
        if (saving.isDone()) {
            try {
                result = saving.join();
            } catch (RuntimeException e) {
                error = e.toString();
            }
            phase = Phase.DONE;
            game.sprites().reloadAll();
            game.sprites().reloadIcons();
            if (result != null && wear && !plan.sheets().isEmpty() && result.written() > 0) {
                game.wardrobe().set(slot, result.folder().getFileName().toString());
                game.saveWardrobe();
            }
            if (result != null) {
                game.toast("Imported " + result.written() + " file" + (result.written() == 1 ? "" : "s")
                        + " into " + game.relative(result.folder()), Theme.OK);
            }
        }
    }

    private void done(Ui ui, Game game, float x, float y, float w, float h,
                      float cx, float cy, float inner) {
        if (error != null) {
            ui.paragraph(ui.body, "The import failed: " + error, cx, cy, inner, Theme.WARNING);
        } else {
            cy += ui.paragraph(ui.body, "Saved " + result.written() + " file"
                    + (result.written() == 1 ? "" : "s") + " to " + game.relative(result.folder())
                    + (result.replaced() > 0 ? " (" + result.replaced() + " replaced)" : "")
                    + ". They are in the repository now — commit them to keep them.",
                    cx, cy, inner, Theme.OK);
            cy += 10;
            for (String p : result.problems().subList(0, Math.min(6, result.problems().size()))) {
                cy += ui.paragraph(ui.small, "! " + p, cx, cy, inner, Theme.WARNING);
            }
        }
        var in = ui.input();
        if (ui.button("OK", x + w - 164, y + h - 60, 140, 40, true, true)
                || in.pressed(GLFW_KEY_ENTER) || in.pressed(GLFW_KEY_ESCAPE)) {
            in.consumeKey(GLFW_KEY_ENTER);
            in.consumeKey(GLFW_KEY_ESCAPE);
            close();
        }
    }
}
