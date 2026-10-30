package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Game;
import com.larsons.game.gfx.Batch;
import com.larsons.game.gfx.Texture;
import com.larsons.game.sprite.ItemSprites;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Border;
import com.larsons.game.world.Inventory;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Wearables;
import com.larsons.game.world.World;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

/**
 * The inventory screen (I) and the hotbar.
 *
 * <p>The screen is a portrait panel over the middle of the screen, its
 * background the picture at {@code assets/ui/inventory_background.png}
 * (432 × 768, scaled to fit the window's height; a plain dark panel when
 * there is none), and on it every slot of the player's {@link Inventory} as
 * a diamond - a square stood on its corner - with a white border: the rest
 * of the inventory in an interlocking diamond lattice that grows or shrinks
 * to fit however many slots there are ({@link #lattice}), and the five
 * hotbar slots in a row along the bottom.
 *
 * <ul>
 *   <li>Click an item to pick it up on the pointer, click a slot to put it
 *       there (swapping with what is in it); click outside the panel to put
 *       it on the ground.</li>
 *   <li>Right-click (or Shift-click) moves an item between the hotbar and the
 *       rest of the inventory.</li>
 *   <li>1 – 5 over a slot swaps it with that hotbar slot.</li>
 *   <li>"A–Z" and "Rarity", at the top, sort the rest of the inventory by
 *       name or rarest first ({@link Inventory#sort}); the hotbar stays.</li>
 * </ul>
 *
 * <p>With the screen shut the hotbar stands at the bottom of the screen,
 * the selected slot - what is in her hands - larger and in gold, and the
 * item's name shown for a moment after it changes.
 *
 * <p>Every item shows its rarity: its slot tinted and edged in the rarity's
 * colour, the item glowing in it, and its name in that colour; an item with
 * a rare border has a ring of the border's colour round it, pulsing, and the
 * border's effect in its tooltip. An item with turning sprites ({@link
 * ItemSprites#icon}) is drawn from them in its weapon tier's colours, lying
 * corner to corner, and turns while it is under the pointer, selected or
 * carried on the pointer. A wearable is drawn as its pickup ({@link
 * Wearables#pickup}) - the t-shirt on its hanger, the shield, the dye's
 * bottle - upright, in its rarity's colour or its dye's.
 *
 * <p>Where things go, as fractions of the 432 × 768 background, so a
 * background can be drawn round them: the title at the top (to 10 %), the
 * lattice in the box 7 – 93 % across and 11 – 74 % down, the hotbar's row
 * 78 – 96 % down.
 */
final class InventoryPanel {

    /** The background picture, under the assets folder. */
    static final String BACKGROUND = "ui/inventory_background.png";
    static final int BACKGROUND_WIDTH = 432, BACKGROUND_HEIGHT = 768;

    /** Where a slot is drawn: its centre and its half-diagonal. */
    record Spot(int slot, float x, float y, float r) {}

    private static final float[] SLOT_FILL = Theme.rgb(10, 10, 18, 0.62f);
    private static final float[] SLOT_HOVER = Theme.rgb(255, 255, 255, 0.16f);
    private static final float[] BORDER = Theme.rgb(255, 255, 255, 1f);
    private static final float[] SELECTED = Theme.ACCENT;
    /** How much of its rarity's colour a full slot is tinted with, its inner edge, and the glow round the item. */
    private static final float RARITY_FILL = 0.14f, RARITY_EDGE = 0.85f, RARITY_GLOW = 0.5f;
    /** How long a turning icon takes to go once round, in seconds. */
    private static final double ICON_TURN = 3.2;
    /** A wearable's pickup's frame, in half-diagonals of its slot: the pickup a little less than the slot's width. */
    private static final float PICKUP_ICON = 1.75f;

    private final Game game;
    private final World world;
    private boolean open;
    private Item cursor;
    private int cursorFrom = -1;
    private int hovered = -1;
    private Texture background;
    private Path backgroundFile;
    private long backgroundStamp = Long.MIN_VALUE;
    private int shownSelected = -1;
    private Item shownHeld;
    private double nameTimer;

    InventoryPanel(Game game, World world) {
        this.game = game;
        this.world = world;
    }

    boolean open() { return open; }

    void toggle() {
        if (open) close();
        else show();
    }

    void show() {
        if (open) return;
        open = true;
        Sounds.play(SoundKeys.ui("inventory_open"));
    }

    /** Shut the screen; an item still on the pointer goes back where it came from. */
    void close() {
        if (!open) return;
        open = false;
        hovered = -1;
        if (cursor != null) {
            Inventory inv = inventory();
            int back = cursorFrom >= 0 && inv.get(cursorFrom) == null ? cursorFrom : inv.add(cursor);
            if (back >= 0) inv.set(back, cursor);
            else world.dropItem(cursor);
            cursor = null;
            cursorFrom = -1;
            world.equipSelected();
        }
        Sounds.play(SoundKeys.ui("inventory_close"));
    }

    /** The slot under the pointer the last time the screen was drawn, or -1. */
    int hovered() { return open ? hovered : -1; }

    private Inventory inventory() {
        return world.player().inventory();
    }

    /** Swap slot {@code slot} with hotbar slot {@code hotbar} (1 – 5 over a slot). */
    void swapWithHotbar(int slot, int hotbar) {
        if (slot < 0 || slot == hotbar) return;
        inventory().swap(slot, hotbar);
        Sounds.play(SoundKeys.ui("item_place"));
        world.equipSelected();
    }

    // --- layout ----------------------------------------------------------------------

    /**
     * The panel's box: the background's shape, as tall as the window allows
     * (never taller than the picture), in the middle: {@code {x, y, w, h}}.
     */
    static float[] panel(float screenW, float screenH) {
        float h = Math.min(screenH - 48, BACKGROUND_HEIGHT);
        float w = h * BACKGROUND_WIDTH / BACKGROUND_HEIGHT;
        if (w > screenW - 32) {
            w = screenW - 32;
            h = w * BACKGROUND_HEIGHT / BACKGROUND_WIDTH;
        }
        return new float[]{(screenW - w) / 2, (screenH - h) / 2, w, h};
    }

    /**
     * Slots {@code first} ... {@code first + count - 1} in an interlocking
     * diamond lattice inside {@code (x, y, w, h)}: rows of {@code c} and
     * {@code c - 1} diamonds by turns, each row tucked half a diamond into
     * the one above, centred across the box and from its top down, as big as
     * fits (up to {@code maxStep} apart) -
     * trying every row length and keeping the one that gives the biggest
     * diamonds, the longest rows of those.
     */
    static List<Spot> lattice(int first, int count, float x, float y, float w, float h, float maxStep) {
        List<Spot> out = new ArrayList<>();
        if (count <= 0) return out;
        int bestCols = 1;
        float bestStep = 0;
        for (int c = 2; c <= Math.max(2, count); c++) {
            int rows = rowsFor(count, c);
            float step = Math.min(maxStep, Math.min(w / c, h / ((rows - 1) / 2f + 1)));
            // as big as fits - and, of row lengths that fit as big, the longest
            if (step >= bestStep - 1e-3) {
                bestStep = step;
                bestCols = c;
            }
        }
        int c = bestCols;
        int rows = rowsFor(count, c);
        float step = bestStep;
        float r = step / 2 - Math.max(2, step * 0.06f);
        float top = y + step / 2;                         // from the top of the box down
        int slot = first;
        for (int row = 0; row < rows && slot < first + count; row++) {
            int full = row % 2 == 0 ? c : c - 1;
            int inRow = Math.min(full, first + count - slot);
            // a short last row keeps to the lattice, as near the middle as it can
            int skip = (full - inRow) / 2;
            float cx0 = x + w / 2 - (full - 1) * step / 2;
            for (int i = 0; i < inRow; i++, slot++) {
                out.add(new Spot(slot, cx0 + (skip + i) * step, top + row * step / 2, r));
            }
        }
        return out;
    }

    /** Rows a lattice of {@code c}-wide rows (and {@code c - 1} between) needs for {@code count} slots. */
    private static int rowsFor(int count, int c) {
        int rows = 0, placed = 0;
        while (placed < count) {
            placed += rows % 2 == 0 ? c : Math.max(1, c - 1);
            rows++;
        }
        return rows;
    }

    /** Every slot's spot on the panel at {@code (px, py, pw, ph)}: the hotbar's along the bottom, the rest above. */
    static List<Spot> spots(int size, float px, float py, float pw, float ph) {
        float step = pw * 0.86f / Inventory.HOTBAR;
        // the inventory's diamonds no bigger than the hotbar's
        List<Spot> out = new ArrayList<>(lattice(Inventory.HOTBAR, size - Inventory.HOTBAR,
                px + pw * 0.07f, py + ph * 0.11f, pw * 0.86f, ph * 0.63f, step));
        float r = Math.min(step / 2 - 4, ph * 0.08f);
        float cy = py + ph * 0.87f;
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            out.add(new Spot(i, px + pw * 0.07f + step * (i + 0.5f), cy, r));
        }
        return out;
    }

    // --- drawing ---------------------------------------------------------------------

    /** The inventory screen, when open. Handles its clicks. */
    void draw(Ui ui) {
        if (!open) return;
        Inventory inv = inventory();
        float[] p = panel(ui.width(), ui.height());
        float px = p[0], py = p[1], pw = p[2], ph = p[3];
        ui.rect(0, 0, ui.width(), ui.height(), Theme.withAlpha(Theme.SCRIM, 0.35f));
        Texture bg = background();
        if (bg != null) {
            ui.image(bg, px, py, pw, ph, Theme.rgb(255, 255, 255, 1f));
        } else {
            ui.rect(px, py, pw, ph, Theme.withAlpha(Theme.PANEL, 0.97f));
            ui.outline(px, py, pw, ph, 2, Theme.rgb(255, 255, 255, 0.85f));
            ui.rect(px + 6, py + ph * 0.765f, pw - 12, 1, Theme.rgb(255, 255, 255, 0.35f));
        }
        ui.textCentered(ui.large, "Inventory", px + pw / 2, py + ph * 0.035f, Theme.TITLE);
        ui.textCentered(ui.small, inv.count() + " / " + inv.size(), px + pw / 2, py + ph * 0.035f + 30,
                Theme.ITEM_DISABLED);
        sortButtons(ui, inv, px, py, pw, ph);

        List<Spot> spots = spots(inv.size(), px, py, pw, ph);
        hovered = -1;
        for (Spot s : spots) {
            boolean hot = ui.hoverDiamond(s.x(), s.y(), s.r());
            if (hot) hovered = s.slot();
            boolean sel = s.slot() == inv.selected();
            slot(ui, s.x(), s.y(), s.r(), inv.get(s.slot()), hot, sel);
            if (Inventory.isHotbar(s.slot())) {
                ui.textCentered(ui.small, String.valueOf(s.slot() + 1), s.x(), s.y() + s.r() + 3,
                        sel ? SELECTED : Theme.HINT);
            }
        }

        // --- the pointer: pick up, put down, move, drop ------------------------------
        var in = ui.input();
        boolean shift = in.down(GLFW_KEY_LEFT_SHIFT) || in.down(GLFW_KEY_RIGHT_SHIFT);
        boolean inside = ui.hover(px, py, pw, ph);
        if (in.mousePressed(GLFW_MOUSE_BUTTON_LEFT)) {
            in.consumeMouse(GLFW_MOUSE_BUTTON_LEFT);
            if (hovered >= 0 && (shift && cursor == null)) {
                quickMove(hovered);
            } else if (hovered >= 0) {
                if (cursor == null) {
                    if (inv.get(hovered) != null) {
                        cursor = inv.take(hovered);
                        cursorFrom = hovered;
                        Sounds.play(SoundKeys.ui("item_pick"));
                    }
                } else {
                    cursor = inv.set(hovered, cursor);
                    cursorFrom = cursor == null ? -1 : hovered;
                    Sounds.play(SoundKeys.ui("item_place"));
                }
                world.equipSelected();
            } else if (!inside && cursor != null) {
                Item d = world.dropItem(cursor);
                if (d != null) {
                    Sounds.play(SoundKeys.item(d.id(), "drop"));
                    game.saveWardrobe();
                    game.toast("Dropped " + d.fullName(), Theme.HINT);
                }
                cursor = null;
                cursorFrom = -1;
            }
        }
        if (in.mousePressed(GLFW_MOUSE_BUTTON_RIGHT)) {
            in.consumeMouse(GLFW_MOUSE_BUTTON_RIGHT);
            if (hovered >= 0 && cursor == null) quickMove(hovered);
        }

        // --- what is under the pointer, and what is on it ----------------------------
        if (hovered >= 0 && inv.get(hovered) != null && cursor == null) {
            String hint = Inventory.isHotbar(hovered) ? "hotbar " + (hovered + 1) : "1-5: to the hotbar";
            tooltip(ui, inv.get(hovered), hint, (float) in.mouseX() + 16, (float) in.mouseY() + 12);
        }
        if (cursor != null) {
            float r = spots.get(0).r() * 0.9f;
            icon(ui, cursor, (float) in.mouseX(), (float) in.mouseY(), r, 1f, true);
        }
        ui.textCentered(ui.small, "Click: pick up / put down · Right-click: to / from the hotbar · "
                + "Click outside: drop · A–Z / Rarity: sort · I or Esc: close", ui.width() / 2f, py + ph + 10, Theme.HINT);
    }

    /** Where the sort buttons go: in the title's band, either side of it, as {@code {x, y, w, h}} of the left one. */
    static float[] sortButton(float px, float py, float pw, float ph) {
        float w = Math.max(64, pw * 0.2f), h = Math.max(24, ph * 0.04f);
        return new float[]{px + pw * 0.05f, py + ph * 0.035f + 2, w, h};
    }

    /** "A–Z" (left of the title) and "Rarity" (right of it): sort the rest of the inventory. */
    private void sortButtons(Ui ui, Inventory inv, float px, float py, float pw, float ph) {
        float[] b = sortButton(px, py, pw, ph);
        Inventory.Order order = null;
        if (ui.button("A–Z", b[0], b[1], b[2], b[3])) order = Inventory.Order.NAME;
        if (ui.button("Rarity", px + pw - (b[0] - px) - b[2], b[1], b[2], b[3])) order = Inventory.Order.RARITY;
        if (order == null) return;
        inv.sort(order);
        Sounds.play(SoundKeys.ui("sort"));
    }

    /** Right-click: the hotbar's item into the rest of the inventory, or the other way. */
    private void quickMove(int from) {
        Inventory inv = inventory();
        if (inv.get(from) == null) return;
        int lo = Inventory.isHotbar(from) ? Inventory.HOTBAR : 0;
        int hi = Inventory.isHotbar(from) ? inv.size() : Inventory.HOTBAR;
        for (int i = lo; i < hi; i++) {
            if (inv.get(i) == null) {
                inv.swap(from, i);
                Sounds.play(SoundKeys.ui("item_place"));
                world.equipSelected();
                return;
            }
        }
    }

    /** The hotbar along the bottom of the screen, when the inventory is shut. */
    void drawHotbar(Ui ui, double dt, float bottom) {
        if (open) return;
        Inventory inv = inventory();
        Item held = inv.held();
        if (inv.selected() != shownSelected || !java.util.Objects.equals(held, shownHeld)) {
            if (shownSelected >= 0) nameTimer = 2.0;
            shownSelected = inv.selected();
            shownHeld = held;
        }
        nameTimer = Math.max(0, nameTimer - dt);
        float r = 28, step = 64;
        float cx0 = ui.width() / 2f - step * (Inventory.HOTBAR - 1) / 2f;
        float cy = bottom - r - 6;
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            boolean sel = i == inv.selected();
            float rr = sel ? r + 4 : r;
            slot(ui, cx0 + i * step, cy, rr, inv.get(i), false, sel);
            ui.textCentered(ui.small, String.valueOf(i + 1), cx0 + i * step, cy + rr * 0.38f,
                    sel ? SELECTED : Theme.withAlpha(Theme.HINT, 0.9f));
        }
        if (nameTimer > 0) {
            String name = held == null ? "Empty hands" : held.fullName();
            float a = (float) Math.min(1, nameTimer / 0.4);
            float w = ui.body.width(name) + 24;
            ui.rect(ui.width() / 2f - w / 2, cy - r - 44, w, 28, Theme.withAlpha(Theme.PANEL, 0.75f * a));
            float[] c = held == null ? Theme.TITLE : held.rarity().colour(1f);
            ui.textCentered(ui.body, name, ui.width() / 2f, cy - r - 40, Theme.withAlpha(c, a));
        }
    }

    /**
     * One slot: a dark diamond - tinted and edged in its item's rarity - its
     * white border (gold when selected), the item in it, turning while the
     * pointer is on it or it is selected.
     */
    private void slot(Ui ui, float x, float y, float r, Item item, boolean hot, boolean selected) {
        ui.diamond(x, y, r, SLOT_FILL);
        if (item != null) {
            ui.diamond(x, y, r, item.rarity().colour(RARITY_FILL));
            ui.diamondOutline(x, y, r - 4, 1.5f, item.rarity().colour(RARITY_EDGE));
        }
        if (hot) ui.diamond(x, y, r, SLOT_HOVER);
        if (item != null) icon(ui, item, x, y, r, 1f, hot || selected);
        ui.diamondOutline(x, y, r, selected ? 3 : 2, selected ? SELECTED : BORDER);
    }

    /**
     * An item fitted inside a diamond of half-diagonal {@code r}: its turning
     * sprite in its tier's colours ({@link ItemSprites#icon}) - at rest
     * corner to corner, or turning - glowing in its rarity's colour, ringed
     * in its border's; else its world sprite.
     */
    private void icon(Ui ui, Item item, float x, float y, float r, float alpha, boolean turning) {
        ItemDef d = item.def();
        ItemSprites items = game.items();
        Wearables.Pickup pickup = Wearables.pickup(item);
        String id = pickup == null ? d.id() : pickup.spin();
        ItemSprites.Spin spin = items.spin(id);
        ItemSprites.Icon icon = spin == null ? null
                : pickup != null ? items.icon(id, pickup.part(), pickup.rgb())
                : items.icon(id, item.tier() == null ? null : item.tier().key());
        if (icon == null) {
            Texture t = game.sprites().icon(d.id(), d.fallbackIcon());
            float s = r * 1.2f;
            ui.image(t, x - s / 2, y - s / 2, s, s, Theme.rgb(255, 255, 255, alpha));
            return;
        }
        // a weapon as long as the diamond's side, lying along it at rest; a pickup upright, inside it
        float s = pickup != null ? r * PICKUP_ICON
                : (float) (r * 1.3 * spin.profile().frameWorldSize() / spin.length());
        int frame = pickup != null ? 0 : ItemSprites.ICON_REST_FRAME;
        if (turning) frame += (int) Math.floor(world.time() / ICON_TURN * icon.frames());
        float[] uv = icon.uv(frame);
        float x0 = x - s / 2, y0 = y - s / 2;
        Batch b = ui.batch();
        float[] rc = item.rarity().colour(1f);
        b.glowRect(icon.halo(), x0, y0, s, s, uv[0], uv[1], uv[2], uv[3], rc[0], rc[1], rc[2], RARITY_GLOW * alpha);
        Border border = item.border();
        if (border != null) {
            float[] bc = border.colour(1f);
            float k = (float) (0.75 + 0.25 * Math.sin(world.time() * 2 * Math.PI * 0.8));
            b.rect(icon.ring(), x0, y0, s, s, uv[0], uv[1], uv[2], uv[3], bc[0], bc[1], bc[2], k * alpha);
        }
        b.rect(icon.sprite(), x0, y0, s, s, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, alpha);
    }

    /** The item's name in its rarity's colour, its rarity (and tier), its border's effect, and {@code hint}. */
    private void tooltip(Ui ui, Item item, String hint, float x, float y) {
        String title = item.fullName();
        String rarity = item.rarity().label() + (item.tier() == null ? "" : " - " + item.tier().label() + " tier");
        Border border = item.border();
        String effect = border == null ? null : border.label() + " border: " + border.effect();
        float w = Math.max(Math.max(ui.body.width(title), ui.small.width(hint)), ui.small.width(rarity));
        if (effect != null) w = Math.max(w, ui.small.width(effect));
        w += 20;
        float h = effect == null ? 70 : 92;
        x = Math.min(x, ui.width() - w - 4);
        y = Math.min(y, ui.height() - h - 4);
        ui.rect(x, y, w, h, Theme.withAlpha(Theme.PANEL, 0.95f));
        ui.outline(x, y, w, h, 1, item.rarity().colour(0.9f));
        ui.text(ui.body, title, x + 10, y + 4, item.rarity().colour(1f));
        ui.text(ui.small, rarity, x + 10, y + 27, Theme.withAlpha(item.rarity().colour(1f), 0.8f));
        float hy = y + 49;
        if (effect != null) {
            ui.text(ui.small, effect, x + 10, hy, border.colour(1f));
            hy += 22;
        }
        ui.text(ui.small, hint, x + 10, hy, Theme.ITEM_DISABLED);
    }

    /**
     * The background picture, read from the assets folder the first time the
     * screen opens - and again if the file changes, so a new background can
     * be dropped in while the game runs. Null when there is none.
     */
    private Texture background() {
        if (backgroundFile == null) backgroundFile = game.settings().assets.resolve(BACKGROUND);
        long stamp;
        try {
            stamp = Files.isRegularFile(backgroundFile) ? Files.getLastModifiedTime(backgroundFile).toMillis() : -1;
        } catch (IOException e) {
            stamp = -1;
        }
        if (stamp == backgroundStamp) return background;
        backgroundStamp = stamp;
        if (background != null) background.close();
        background = null;
        if (stamp < 0) return null;
        try {
            BufferedImage img = ImageIO.read(backgroundFile.toFile());
            if (img != null) background = Texture.of(img, false);
        } catch (IOException e) {
            System.err.println("[inventory] cannot read " + backgroundFile + ": " + e.getMessage());
        }
        return background;
    }

    void dispose() {
        if (background != null) background.close();
        background = null;
    }
}
