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
import com.larsons.game.world.Forged;
import com.larsons.game.world.Inventory;
import com.larsons.game.world.Item;
import com.larsons.game.world.Shop;
import com.larsons.game.world.Store;
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
import java.util.Locale;

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
 *       rest of the inventory; right-clicking something picked from a bush
 *       eats one of it ({@link World#eat}).</li>
 *   <li>Pickings stack ({@link Inventory#amount}): the count is drawn on
 *       the slot, a whole stack is lifted at once, and putting it on a
 *       stack of the same tops that up.</li>
 *   <li>1 – 5 over a slot swaps it with that hotbar slot.</li>
 *   <li>"A–Z" and "Rarity", at the top, sort the rest of the inventory by
 *       name or rarest first ({@link Inventory#sort}); the hotbar stays.</li>
 * </ul>
 *
 * <p>By a barrel, a crate or a storage chest, E opens the screen with it
 * beside the inventory ({@link #showStore}): its slots on a panel of their
 * own on the right, the pointer carrying things between the two, Shift-click
 * or right-click moving a whole stack across, "Take all" and "Put all in"
 * (all but the hotbar) under it.
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
    private static final float[] BORDER = Theme.BRASS_LIGHT;
    private static final float[] SELECTED = Theme.ACCENT;
    private static final float[] COIN = Theme.rgb(240, 192, 48, 1f);
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
    private int cursorAmount;
    private int cursorFrom = -1;
    /** Whether what is on the pointer came out of the store. */
    private boolean cursorInStore;
    private int hovered = -1, hoveredStore = -1;
    /** The barrel, crate or chest open beside the inventory, or null. */
    private Store store;
    private Texture background;
    private Path backgroundFile;
    private long backgroundStamp = Long.MIN_VALUE;
    private int shownSelected = -1;
    private Item shownHeld;
    private double nameTimer;
    /** Beside the inventory instead of a store: the tiers of the weapon in this slot (-1: none). */
    private int tiersOf = -1;
    private final SidePanels side;
    /** The shop open beside the inventory, or null; how far down its stock is scrolled. */
    private Shop shop;
    private boolean carried;
    private int shopScroll;

    InventoryPanel(Game game, World world) {
        this.game = game;
        this.world = world;
        this.side = new SidePanels(game, world);
    }

    /** What the Cards button does: the card table laid ({@link DemoScene}). */
    private Runnable cardTable;

    void onCards(Runnable open) {
        cardTable = open;
    }

    /** Open the screen with the tiers of the weapon in {@code slot} beside it. */
    void showTiers(int slot) {
        if (open && (store != null || shop != null)) close();
        show();
        tiersOf = slot;
    }

    /**
     * Whether the game stands still: her own inventory is open - her cards,
     * a weapon's tiers - and no barrel, crate or chest beside it.
     */
    boolean pausing() {
        return open && store == null;
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

    /** Open the screen with {@code s} - a barrel, a crate, a chest - beside the inventory, to move things in and out. */
    void showStore(Store s) {
        if (open && store == s) return;
        if (open) close();
        store = s;
        show();
    }

    /** The barrel, crate or chest open beside the inventory, or null. */
    Store store() { return open ? store : null; }

    /** Open the screen with {@code s}'s stock beside the inventory, to buy and sell. */
    void showShop(Shop s) {
        if (open && shop == s) return;
        if (open) close();
        shop = s;
        carried = false;
        shopScroll = 0;
        show();
        Sounds.play(SoundKeys.ui("shop_open"));
    }

    /** {@link #showShop} for what a person carries to sell: a shop that is no stall of the world's. */
    void showWares(Shop s) {
        showShop(s);
        carried = true;
    }

    /** The shop open beside the inventory, or null. */
    Shop shop() { return open ? shop : null; }

    /**
     * Shut the screen (and the store beside it); an item still on the
     * pointer goes back where it came from - else into her inventory, else
     * the store's, else on the ground.
     */
    void close() {
        if (!open) return;
        open = false;
        hovered = -1;
        hoveredStore = -1;
        if (cursor != null) {
            Inventory back = cursorInStore && store != null ? store.items() : inventory();
            if (cursorFrom >= 0 && back.get(cursorFrom) == null) {
                back.set(cursorFrom, cursor, cursorAmount);
            } else {
                int left = inventory().put(cursor, cursorAmount, true);
                if (left > 0 && store != null) left = store.items().put(cursor, left, false);
                if (left > 0) world.dropItem(cursor, left);
            }
            cursor = null;
            cursorAmount = 0;
            cursorFrom = -1;
            cursorInStore = false;
            world.equipSelected();
        }
        store = null;
        shop = null;
        tiersOf = -1;
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

    /**
     * With a store open, the two panels side by side - hers on the left, the
     * store's on the right - as tall as the window allows: {@code {{x, y, w,
     * h}, {x, y, w, h}}}.
     */
    static float[][] panels(float screenW, float screenH) {
        float gap = 20;
        float h = Math.min(screenH - 48, BACKGROUND_HEIGHT);
        float w = h * BACKGROUND_WIDTH / BACKGROUND_HEIGHT;
        if (2 * w + gap > screenW - 32) {
            w = (screenW - 32 - gap) / 2;
            h = w * BACKGROUND_HEIGHT / BACKGROUND_WIDTH;
        }
        float x = (screenW - 2 * w - gap) / 2, y = (screenH - h) / 2;
        return new float[][]{{x, y, w, h}, {x + w + gap, y, w, h}};
    }

    /** A store's slots on its panel at {@code (px, py, pw, ph)}: in the lattice's box, no bigger than a hotbar slot. */
    static List<Spot> storeSpots(int size, float px, float py, float pw, float ph) {
        float step = pw * 0.86f / Inventory.HOTBAR;
        return lattice(0, size, px + pw * 0.07f, py + ph * 0.11f, pw * 0.86f, ph * 0.63f, step);
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

    /** The inventory screen, when open - and the store beside it, when one is open. Handles its clicks. */
    void draw(Ui ui) {
        if (!open) return;
        if (store != null && !world.stores().contains(store)) {        // (broken while open)
            close();
            return;
        }
        Inventory inv = inventory();
        if (tiersOf >= 0 && !com.larsons.game.world.Upgrades.has(inv.get(tiersOf))) tiersOf = -1;
        if (shop != null && !carried && !world.shops().contains(shop)) shop = null;
        boolean beside = store != null || shop != null || tiersOf >= 0;
        float[][] both = beside ? panels(ui.width(), ui.height()) : null;
        float[] p = both == null ? panel(ui.width(), ui.height()) : both[0];
        float px = p[0], py = p[1], pw = p[2], ph = p[3];
        ui.rect(0, 0, ui.width(), ui.height(), Theme.withAlpha(Theme.SCRIM, 0.35f));
        backdrop(ui, px, py, pw, ph, true);
        ui.textCentered(ui.large, "Inventory", px + pw / 2, py + ph * 0.035f, Theme.TITLE);
        ui.textCentered(ui.small, inv.count() + " / " + inv.size(), px + pw / 2, py + ph * 0.035f + 30,
                Theme.ITEM_DISABLED);
        sortButtons(ui, inv, px, py, pw, ph);

        List<Spot> spots = spots(inv.size(), px, py, pw, ph);
        hovered = -1;
        hoveredStore = -1;
        for (Spot s : spots) {
            boolean hot = ui.hoverDiamond(s.x(), s.y(), s.r());
            if (hot) hovered = s.slot();
            boolean sel = s.slot() == inv.selected();
            slot(ui, s.x(), s.y(), s.r(), inv.get(s.slot()), inv.amount(s.slot()), hot, sel);
            if (Inventory.isHotbar(s.slot())) {
                ui.textCentered(ui.small, String.valueOf(s.slot() + 1), s.x(), s.y() + s.r() + 3,
                        sel ? SELECTED : Theme.HINT);
            }
        }
        boolean inside = ui.hover(px, py, pw, ph);
        Inventory held = store == null ? null : store.items();
        if (held != null) {
            float qx = both[1][0], qy = both[1][1], qw = both[1][2], qh = both[1][3];
            backdrop(ui, qx, qy, qw, qh, false);
            ui.textCentered(ui.large, store.kind().label(), qx + qw / 2, qy + qh * 0.035f, Theme.TITLE);
            ui.textCentered(ui.small, held.count() + " / " + held.size(), qx + qw / 2, qy + qh * 0.035f + 30,
                    Theme.ITEM_DISABLED);
            for (Spot s : storeSpots(held.size(), qx, qy, qw, qh)) {
                boolean hot = ui.hoverDiamond(s.x(), s.y(), s.r());
                if (hot) hoveredStore = s.slot();
                slot(ui, s.x(), s.y(), s.r(), held.get(s.slot()), held.amount(s.slot()), hot, false);
            }
            float bw = Math.max(110, qw * 0.36f), bh = Math.max(28, qh * 0.05f);
            if (ui.button("Take all", qx + qw / 2 - bw - 6, qy + qh * 0.84f, bw, bh)) takeAll();
            if (ui.button("Put all in", qx + qw / 2 + 6, qy + qh * 0.84f, bw, bh)) putAll();
            inside |= ui.hover(qx, qy, qw, qh);
        } else if (shop != null) {
            float[] q = both[1];
            if (!shopPanel(ui, q[0], q[1], q[2], q[3])) {
                close();
                return;
            }
            inside |= ui.hover(q[0], q[1], q[2], q[3]);
        } else if (tiersOf >= 0) {
            float[] q = both[1];
            if (!side.tiers(ui, inv, tiersOf, q[0], q[1], q[2], q[3])) tiersOf = -1;
            inside |= ui.hover(q[0], q[1], q[2], q[3]);
        }
        if (store == null && shop == null) {
            // her cards, beside the inventory (J)
            float bw = 96, bh = 30;
            float bx = px >= bw + 16 ? px - bw - 8 : px + pw / 2 - bw / 2;
            float by = px >= bw + 16 ? py + 8 : py + ph * 0.035f + 50;
            if (ui.button("Cards", bx, by, bw, bh) && cardTable != null) {
                close();
                cardTable.run();
                return;
            }
            inside |= ui.hover(bx, by, bw, bh);
        }

        // --- the pointer: pick up, put down, move, drop ------------------------------
        var in = ui.input();
        boolean shift = in.down(GLFW_KEY_LEFT_SHIFT) || in.down(GLFW_KEY_RIGHT_SHIFT);
        if (in.mousePressed(GLFW_MOUSE_BUTTON_LEFT)) {
            in.consumeMouse(GLFW_MOUSE_BUTTON_LEFT);
            if (hovered >= 0 && shop != null && shift && cursor == null) {
                sell(hovered);
            } else if (hovered >= 0) {
                click(inv, hovered, false, shift);
            } else if (hoveredStore >= 0) {
                click(held, hoveredStore, true, shift);
            } else if (!inside && cursor != null) {
                Item d = world.dropItem(cursor, cursorAmount);
                if (d != null) {
                    Sounds.play(SoundKeys.item(d.id(), "drop"));
                    game.saveWardrobe();
                    game.toast("Dropped " + (cursorAmount > 1 ? cursorAmount + " " : "") + d.fullName(), Theme.HINT);
                }
                cursor = null;
                cursorAmount = 0;
                cursorFrom = -1;
                cursorInStore = false;
            }
        }
        if (in.mousePressed(GLFW_MOUSE_BUTTON_RIGHT)) {
            in.consumeMouse(GLFW_MOUSE_BUTTON_RIGHT);
            if (hovered >= 0 && cursor == null) {
                Item there = inv.get(hovered);
                if (shop != null && there != null) sell(hovered);
                else if (there != null && there.def().edible() && !shift) eat(hovered);
                else if (held == null && !shift && com.larsons.game.world.Upgrades.has(there)) {
                    // a weapon with tiers: them, beside the inventory
                    tiersOf = tiersOf == hovered ? -1 : hovered;
                    Sounds.play(SoundKeys.ui("click"));
                } else if (held != null) transfer(inv, hovered, held, false);
                else quickMove(hovered);
            } else if (hoveredStore >= 0 && cursor == null) {
                transfer(held, hoveredStore, inv, true);
            }
        }

        // --- what is under the pointer, and what is on it ----------------------------
        String into = store == null ? null : "the " + store.kind().label().toLowerCase(java.util.Locale.ROOT);
        if (hovered >= 0 && inv.get(hovered) != null && cursor == null) {
            Item there = inv.get(hovered);
            int pay = shop == null ? 0 : shop.offerFor(there);
            String hint = shop != null ? (pay > 0 ? "Right-click or Shift-click: sell one for " + coins(pay)
                    : shop.buys() ? "The shop will not take this" : "This shop buys nothing")
                    : into != null ? (there.def().edible() ? "Right-click: eat one · " : "")
                    + "Shift-click: put in " + into
                    : there.def().edible() ? "Right-click: eat one · Shift-click: move"
                    : com.larsons.game.world.Upgrades.has(there) ? "Right-click: its upgrade tiers · Shift-click: move"
                    : Inventory.isHotbar(hovered) ? "hotbar " + (hovered + 1) : "1-5: to the hotbar";
            tooltip(ui, there, inv.amount(hovered), hint, (float) in.mouseX() + 16, (float) in.mouseY() + 12);
        } else if (hoveredStore >= 0 && held.get(hoveredStore) != null && cursor == null) {
            tooltip(ui, held.get(hoveredStore), held.amount(hoveredStore), "Shift-click or right-click: take",
                    (float) in.mouseX() + 16, (float) in.mouseY() + 12);
        }
        if (cursor != null) {
            float r = spots.get(0).r() * 0.9f;
            icon(ui, cursor, (float) in.mouseX(), (float) in.mouseY(), r, 1f, true);
            count(ui, cursorAmount, (float) in.mouseX(), (float) in.mouseY(), r);
        }
        String help = shop != null
                ? "Buy: its button · Right-click or Shift-click an item of yours: sell one · E, I or Esc: close"
                : store != null
                ? "Click: pick up / put down · Shift-click or right-click: move between them · Click outside: drop · "
                + "E, I or Esc: close"
                : "Click: pick up / put down · Right-click: eat, or a weapon's tiers · Shift-click: to / from the hotbar · "
                + "Click outside: drop · J: cards · I or Esc: close";
        ui.textCentered(ui.small, help, ui.width() / 2f, py + ph + 10, Theme.HINT);
    }

    /** A panel's background: the picture, or a plain dark panel (with the hotbar's band on hers). */
    private void backdrop(Ui ui, float px, float py, float pw, float ph, boolean hotbar) {
        Texture bg = background();
        if (bg != null) {
            ui.image(bg, px, py, pw, ph, Theme.rgb(255, 255, 255, 1f));
            return;
        }
        ui.rect(px, py, pw, ph, Theme.withAlpha(Theme.PANEL, 0.97f));
        ui.outline(px, py, pw, ph, 2, Theme.rgb(255, 255, 255, 0.85f));
        if (hotbar) ui.rect(px + 6, py + ph * 0.765f, pw - 12, 1, Theme.rgb(255, 255, 255, 0.35f));
    }

    /**
     * A click on slot {@code slot} of {@code inv} (the store's when {@code
     * inStore}): Shift moves it across (or, with no store open, to or from
     * the hotbar); else pick it up, put down what is on the pointer - onto a
     * stack of the same, or swapping with what is there.
     */
    private void click(Inventory inv, int slot, boolean inStore, boolean shift) {
        if (shift && cursor == null) {
            if (store == null) quickMove(slot);
            else if (inStore) transfer(inv, slot, inventory(), true);
            else transfer(inv, slot, store.items(), false);
            return;
        }
        if (cursor == null) {
            if (inv.get(slot) != null) {
                cursorAmount = inv.amount(slot);
                cursor = inv.take(slot);
                cursorFrom = slot;
                cursorInStore = inStore;
                Sounds.play(SoundKeys.ui("item_pick"));
            }
        } else if (cursor.equals(inv.get(slot)) && cursor.def().stack() > 1) {
            // onto a stack of the same: topped up, what does not fit stays on the pointer
            cursorAmount = inv.merge(slot, cursor, cursorAmount);
            if (cursorAmount <= 0) {
                cursor = null;
                cursorFrom = -1;
            }
            Sounds.play(SoundKeys.ui("item_place"));
        } else {
            int wasAmount = inv.amount(slot);
            cursor = inv.set(slot, cursor, cursorAmount);
            cursorAmount = cursor == null ? 0 : wasAmount;
            cursorFrom = cursor == null ? -1 : slot;
            cursorInStore = inStore;
            Sounds.play(SoundKeys.ui("item_place"));
        }
        world.equipSelected();
    }

    /**
     * Move the whole of slot {@code slot} of {@code from} into {@code to} -
     * onto its stacks, into empty slots ({@code toHer}: hers, the rest of
     * her inventory first) - as much as fits.
     */
    private boolean transfer(Inventory from, int slot, Inventory to, boolean toHer) {
        Item it = from.get(slot);
        if (it == null) return false;
        int n = from.amount(slot);
        int left = to.put(it, n, toHer);
        if (left == n) {
            Sounds.play(SoundKeys.ui("error"));
            game.toast(toHer ? "No room in your inventory"
                    : "The " + store.kind().label().toLowerCase(java.util.Locale.ROOT) + " is full", Theme.WARNING);
            return false;
        }
        if (left == 0) from.take(slot);
        else from.set(slot, it, left);
        Sounds.play(SoundKeys.ui("item_place"));
        world.equipSelected();
        return true;
    }

    /** "Take all": everything in the store into her inventory, as much as fits. */
    private void takeAll() {
        Inventory held = store.items(), inv = inventory();
        boolean any = false;
        for (int i = 0; i < held.size(); i++) {
            Item it = held.get(i);
            if (it == null) continue;
            int n = held.amount(i), left = inv.put(it, n, true);
            if (left == n) continue;
            if (left == 0) held.take(i);
            else held.set(i, it, left);
            any = true;
        }
        Sounds.play(SoundKeys.ui(any ? "item_place" : "error"));
        if (held.count() > 0) game.toast(any ? "Not everything fits in your inventory" : "No room in your inventory",
                Theme.WARNING);
        world.equipSelected();
    }

    /** "Put all in": everything but the hotbar's - what is to hand - into the store, as much as fits. */
    private void putAll() {
        Inventory held = store.items(), inv = inventory();
        boolean any = false;
        for (int i = Inventory.HOTBAR; i < inv.size(); i++) {
            Item it = inv.get(i);
            if (it == null) continue;
            int n = inv.amount(i), left = held.put(it, n, false);
            if (left == n) continue;
            if (left == 0) inv.take(i);
            else inv.set(i, it, left);
            any = true;
        }
        Sounds.play(SoundKeys.ui(any ? "item_place" : "error"));
    }

    /**
     * Right-click on something picked: eat one of it ({@link World#eat}) -
     * what it gives back shown in a toast, or that she has no need of it.
     */
    private void eat(int slot) {
        World.Eaten e = world.eat(slot);
        if (e == null) return;
        if (!e.ate()) {
            Sounds.play(SoundKeys.ui("error"));
            game.toast("You are already full - no need for " + e.item().def().name().toLowerCase(), Theme.HINT);
            return;
        }
        Sounds.play(SoundKeys.item(e.item().id(), "eat"));
        game.toast("Ate " + e.item().def().name().toLowerCase() + ": " + gains(e), Theme.ACCENT);
    }

    /** What eating gave back, as "+20 health, +15 stamina". */
    static String gains(World.Eaten e) {
        List<String> parts = new ArrayList<>();
        if (e.health() > 0) parts.add("+" + Math.round(e.health()) + " health");
        if (e.stamina() > 0) parts.add("+" + Math.round(e.stamina()) + " stamina");
        if (e.mana() > 0) parts.add("+" + Math.round(e.mana()) + " mana");
        return String.join(", ", parts);
    }

    /** A stack's count in the lower corner of its diamond (nothing for one). */
    private void count(Ui ui, int amount, float x, float y, float r) {
        if (amount <= 1) return;
        String n = String.valueOf(amount);
        float tx = x + r * 0.32f, ty = y + r * 0.18f;
        float w = ui.small.width(n);
        ui.text(ui.small, n, tx - w / 2 + 1, ty + 1, Theme.rgb(0, 0, 0, 0.85f));
        ui.text(ui.small, n, tx - w / 2, ty, Theme.TITLE);
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
        // the rail the slots hang on, a cog turning at each of its ends
        float reach = step * (Inventory.HOTBAR - 1) / 2f + r + 20, mid = ui.width() / 2f;
        ui.rect(mid - reach, cy - 4, 2 * reach, 8, Theme.SOOT);
        ui.rect(mid - reach, cy - 3, 2 * reach, 6, Theme.BRASS_DARK);
        ui.rect(mid - reach, cy - 3, 2 * reach, 1, Theme.BRASS_LIGHT);
        ui.rect(mid - reach, cy - 2, 2 * reach, 2, Theme.BRASS);
        double turn = world.time() * 0.5;
        for (int side = -1; side <= 1; side += 2) {
            float gx = mid + side * (reach - 8);
            ui.cog(gx, cy, 12, side * turn, Theme.SOOT);
            ui.cog(gx, cy, 11, side * turn, Theme.BRASS);
            ui.disc(gx, cy, 4, Theme.SOOT);
            ui.disc(gx, cy, 2.5f, Theme.BRASS_LIGHT);
        }
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            boolean sel = i == inv.selected();
            float rr = sel ? r + 4 : r;
            slot(ui, cx0 + i * step, cy, rr, inv.get(i), inv.amount(i), false, sel);
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
    private void slot(Ui ui, float x, float y, float r, Item item, int amount, boolean hot, boolean selected) {
        ui.diamond(x, y, r, SLOT_FILL);
        if (item != null) {
            ui.diamond(x, y, r, item.rarity().colour(RARITY_FILL));
            ui.diamondOutline(x, y, r - 4, 1.5f, item.rarity().colour(RARITY_EDGE));
        }
        if (hot) ui.diamond(x, y, r, SLOT_HOVER);
        if (item != null) icon(ui, item, x, y, r, 1f, hot || selected);
        ui.diamondOutline(x, y, r + 1.5f, 1.5f, Theme.SOOT);
        ui.diamondOutline(x, y, r, selected ? 3 : 2, selected ? SELECTED : BORDER);
        if (!selected) ui.diamondOutline(x, y, r - 2.8f, 0.8f, Theme.withAlpha(Theme.BRASS_DARK, 0.9f));
        if (item != null) count(ui, amount, x, y, r);
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
                : item.forged() != null ? items.icon(id, item.forged().tints())
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
        Border border = item.ring();
        if (border != null) {
            float[] bc = border.colour(1f);
            float k = (float) (0.75 + 0.25 * Math.sin(world.time() * 2 * Math.PI * 0.8));
            b.rect(icon.ring(), x0, y0, s, s, uv[0], uv[1], uv[2], uv[3], bc[0], bc[1], bc[2], k * alpha);
        }
        b.rect(icon.sprite(), x0, y0, s, s, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, alpha);
    }

    /**
     * A forged weapon's effects: one's in full ("Venom: +50% of damage as
     * ..."), two or three named and in short ("Venom, Mystic: poison, ×2.5 for
     * double mana").
     */
    private static String effects(Forged f) {
        if (f.effects().size() == 1) {
            Border b = f.effects().iterator().next();
            return b.label() + ": " + b.effect();
        }
        List<String> out = new ArrayList<>();
        for (Border b : f.effects()) {
            out.add(switch (b) {
                case VENOM -> "poison";
                case MYSTIC -> "×2.5 for double mana";
                case BONDED -> "+10% a living thing hit";
            });
        }
        return f.effectsText() + ": " + String.join(", ", out);
    }

    // --- a shop -----------------------------------------------------------------------

    static String coins(int n) {
        return n + (n == 1 ? " coin" : " coins");
    }

    /**
     * The shop's stock beside the inventory, a row each: what it is (an
     * item's picture, a card's colours), how many are left, its price and
     * the Buy button - her purse above, what the shop buys below. Returns
     * false when it is to close.
     */
    private boolean shopPanel(Ui ui, float x, float y, float w, float h) {
        var player = world.player();
        String heroClass = player.hero() == null ? null : player.hero().heroClass();
        List<Shop.Offer> stock = shop.stock(heroClass);
        ui.rect(x, y, w, h, Theme.withAlpha(Theme.PANEL, 0.96f));
        ui.outline(x, y, w, h, 1, Theme.PANEL_EDGE);
        ui.textCentered(ui.large, shop.name(), x + w / 2, y + 12, Theme.TITLE);
        ui.textCentered(ui.body, "Your purse: " + coins(player.coins()), x + w / 2, y + 48, COIN);
        float top = y + 82, rowH = 50, listH = h - 82 - 118;
        int rows = Math.max(1, (int) (listH / rowH));
        var in = ui.input();
        if (ui.hover(x, top, w, rows * rowH) && in.scroll() != 0) {
            shopScroll -= (int) Math.signum(in.scroll());
            in.consumeScroll();
        }
        shopScroll = Math.max(0, Math.min(Math.max(0, stock.size() - rows), shopScroll));
        Shop.Offer hot = null;
        float hotY = 0;
        for (int k = 0; k < rows && k + shopScroll < stock.size(); k++) {
            Shop.Offer o = stock.get(k + shopScroll);
            float ry = top + k * rowH, rx = x + 12, rw = w - 24;
            boolean over = ui.hover(rx, ry, rw, rowH - 4);
            if (over) {
                hot = o;
                hotY = ry;
            }
            float[] rc = o.rarity().colour(1f);
            ui.rect(rx, ry, rw, rowH - 4, Theme.withAlpha(over ? Theme.BUTTON_HOVER : Theme.BUTTON, 0.9f));
            ui.outline(rx, ry, rw, rowH - 4, 1, o.soldOut() ? Theme.PANEL_EDGE : o.rarity().colour(0.7f));
            float ix = rx + 24, iy = ry + (rowH - 4) / 2;
            if (o.card() != null) {
                // a card: a little card in its rarity's colour
                ui.rect(ix - 11, iy - 16, 22, 32, o.rarity().colour(0.85f));
                ui.outline(ix - 11, iy - 16, 22, 32, 1, Theme.TITLE);
                ui.rect(ix - 7, iy - 11, 14, 9, Theme.rgb(250, 240, 210, 0.9f));
            } else {
                ui.diamond(ix, iy, 19, o.rarity().colour(RARITY_FILL));
                icon(ui, o.item(), ix, iy, 19, o.soldOut() ? 0.4f : 1f, over);
            }
            String name = (o.card() != null ? "Card: " : "") + o.name();
            ui.text(ui.body, name, rx + 52, ry + 3, o.soldOut() ? Theme.ITEM_DISABLED : rc);
            String left = o.soldOut() ? "Sold out" : o.left() < 0 ? "Plenty" : o.left() + " left";
            ui.text(ui.small, left + " · " + coins(o.price()), rx + 52, ry + 3 + ui.body.lineHeight(),
                    o.soldOut() ? Theme.ITEM_DISABLED : Theme.HINT);
            boolean can = !o.soldOut() && player.coins() >= o.price()
                    && (o.card() == null || heroClass != null);
            float bw = 64, bx = rx + rw - bw - 6;
            if (ui.button("Buy", bx, ry + 8, bw, rowH - 20, false, can)) buy(o);
        }
        if (stock.isEmpty()) {
            ui.textCentered(ui.body, "Nothing for sale", x + w / 2, top + 20, Theme.HINT);
        }
        if (stock.size() > rows) {
            ui.textCentered(ui.small, (shopScroll + 1) + "-" + Math.min(stock.size(), shopScroll + rows) + " of "
                    + stock.size() + " · scroll for more", x + w / 2, top + rows * rowH + 2, Theme.ITEM_DISABLED);
        }
        float fy = y + h - 112;
        String buys = shop.buys() ? "Buys what you sell for half its worth: right-click an item of yours"
                : "Buys nothing";
        ui.paragraph(ui.small, buys, x + 20, fy, w - 40, Theme.HINT);
        if (hot != null && hot.card() != null) {
            com.larsons.game.cards.Card c = com.larsons.game.cards.CardBook.get().card(hot.card());
            if (c != null) {
                String text = c.name() + " (" + c.rarity().label() + "): " + c.text()
                        + (heroClass == null ? " · choose a class to buy cards" : " · goes onto your discard pile");
                float tw = Math.min(w + 40, 360), tx = Math.min(x + w + 8, ui.width() - tw - 4);
                float th = ui.small.lineHeight() * 4 + 12;
                ui.rect(tx, hotY, tw, th, Theme.withAlpha(Theme.PANEL, 0.97f));
                ui.outline(tx, hotY, tw, th, 1, c.rarity().colour(0.9f));
                ui.paragraph(ui.small, text, tx + 8, hotY + 6, tw - 16, Theme.TITLE);
            }
        } else if (hot != null && cursor == null) {
            tooltip(ui, hot.item(), 1, coins(hot.price()) + (hot.left() > 0 ? " · " + hot.left() + " left" : ""),
                    (float) in.mouseX() + 16, (float) in.mouseY() + 12);
        }
        return !ui.button("Close", x + w / 2 - 60, y + h - 44, 120, 30);
    }

    private void buy(Shop.Offer o) {
        Shop.Deal d = shop.buy(o, world.player());
        switch (d) {
            case DONE -> {
                Sounds.play(SoundKeys.ui("shop_buy"));
                game.toast("Bought " + o.name() + (o.card() != null ? ": it is on your discard pile" : "")
                        + " for " + coins(o.price()), o.rarity().colour(1f));
                world.equipSelected();
            }
            case POOR -> game.toast("Not enough coins", Theme.WARNING);
            case NO_ROOM -> game.toast("Your inventory is full", Theme.WARNING);
            case SOLD_OUT -> game.toast("Sold out", Theme.WARNING);
            default -> { }
        }
    }

    /** Sell one of what is in her slot {@code slot} to the shop that is open. */
    private void sell(int slot) {
        Item it = inventory().get(slot);
        int pay = shop.offerFor(it);
        Shop.Deal d = shop.sell(world.player(), slot);
        if (d == Shop.Deal.DONE) {
            Sounds.play(SoundKeys.ui("shop_sell"));
            game.toast("Sold " + it.fullName() + " for " + coins(pay), COIN);
            world.equipSelected();
            game.saveWardrobe();
        } else if (d == Shop.Deal.WONT_BUY) {
            game.toast(shop.buys() ? "The shop will not take that" : "This shop buys nothing", Theme.WARNING);
        }
    }

    /**
     * The item's name in its rarity's colour (and how many, for a stack), its
     * rarity (and tier), its border's effect - or what eating it gives - and
     * {@code hint}.
     */
    private void tooltip(Ui ui, Item item, int amount, String hint, float x, float y) {
        String title = item.fullName() + (amount > 1 ? "  x" + amount : "");
        Forged forged = item.forged();
        String rarity = item.def().isFood() ? item.def().food().kind() + " - " + item.rarity().label()
                : forged != null ? item.rarity().label() + " - forged " + item.def().name().toLowerCase(Locale.ROOT)
                        + ", rolls " + forged.notation()
                : item.rarity().label() + (item.tier() == null ? "" : " - " + item.tier().label() + " tier");
        Border border = item.border();
        String effect = item.def().edible() ? "Eat: " + item.def().food().effect()
                : item.def().isFood() ? "An ingredient, for the crafting to come"
                : item.def().isTack() ? "Saddles a horse: walk up to one and press E"
                : forged != null ? (forged.effects().isEmpty() ? null : effects(forged))
                : border == null ? null : border.label() + " border: " + border.effect();
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
            ui.text(ui.small, effect, x + 10, hy, border == null ? Theme.ACCENT : border.colour(1f));
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
