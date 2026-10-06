package com.larsons.game.world;

import com.larsons.game.sprite.Slot;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything in the demo void that moves or can be picked up: the player,
 * the items lying around and the chests. The static 3D environment lives in
 * {@link Props}.
 *
 * <p>What the player holds is the selected slot of her hotbar ({@link
 * Inventory}): a weapon with a stance of its own (the battle axe, the bow,
 * the crossbow) is taken up in its stance; the sword - worn, in the
 * wardrobe's weapon hand - or an empty slot is the sword-and-shield stance,
 * the wardrobe's own carried items ({@link #equipSelected}).
 */
public final class World {

    /** How close the player has to be to pick something up, in metres. */
    public static final double PICKUP_RANGE = 1.7;

    /** An item lying in the world, hovering over its shadow. */
    public static final class GroundItem {
        public final ItemDef def;
        public final Vec3 position;
        private final double phase;

        GroundItem(ItemDef def, Vec3 position, double phase) {
            this.def = def;
            this.position = new Vec3(position.x(), 0, position.z());
            this.phase = phase;
        }

        /** How high the item's centre floats at time {@code t}: a slow bob. */
        public double hover(double t) {
            return 0.62 + 0.09 * Math.sin(t * 2.1 + phase);
        }
    }

    private final Player player;
    private final List<GroundItem> items = new ArrayList<>();
    private final List<Chest> chests = new ArrayList<>();
    private double time;

    public World(Wardrobe wardrobe) {
        this(wardrobe, Inventory.DEFAULT_SLOTS);
    }

    /** A world whose player carries an inventory of {@code slots} slots. */
    public World(Wardrobe wardrobe, int slots) {
        player = new Player(wardrobe, slots);
    }

    public Player player() { return player; }

    public List<GroundItem> items() { return items; }

    public List<Chest> chests() { return chests; }

    public double time() { return time; }

    /**
     * Advance the world: the chests' animations ({@code durations}: how long
     * each of a chest's states lasts, from its sheets) - and the player kept
     * out of them.
     */
    public void tick(double dt, java.util.function.BiFunction<Chest, Chest.State, Double> durations) {
        time += dt;
        for (Chest c : chests) {
            c.tick(dt, s -> durations == null ? 0 : durations.apply(c, s));
            player.keepOut(c.position(), Chest.RADIUS);
        }
    }

    public void tick(double dt) {
        tick(dt, null);
    }

    public Chest addChest(Chest chest) {
        chests.add(chest);
        return chest;
    }

    /** The nearest chest within the player's reach, or {@code null}. */
    public Chest reachableChest() {
        Chest best = null;
        double bestD = Double.MAX_VALUE;
        for (Chest c : chests) {
            double d = c.position().horizontalDistance(player.ground());
            if (c.inReach(player.ground()) && d < bestD) {
                best = c;
                bestD = d;
            }
        }
        return best;
    }

    public void spawn(ItemDef def, Vec3 at) {
        items.add(new GroundItem(def, at, items.size() * 1.7));
    }

    /** The nearest item within reach of the player, or {@code null}. */
    public GroundItem reachable() {
        GroundItem best = null;
        double bestD = PICKUP_RANGE;
        for (GroundItem g : items) {
            double d = g.position.horizontalDistance(player.ground());
            if (d <= bestD) {
                best = g;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * Pick {@code item} up: into the first free slot - the hotbar first - and,
     * when that is a hotbar slot, straight into her hands (that slot is
     * selected): a weapon with a stance of its own by taking up that stance.
     * The sword is worn: it goes into its hand in the wardrobe whichever slot
     * it lands in. Returns false (and leaves it lying) when every slot is full.
     */
    public boolean pickUp(GroundItem item) {
        int slot = player.inventory().add(item.def.id());
        if (slot < 0) return false;
        items.remove(item);
        if (!item.def.held()) {
            // a left-handed character takes it in the other hand
            Slot hand = player.wardrobe().hand() == Wardrobe.Hand.LEFT ? item.def.carrySlot().twin()
                    : item.def.carrySlot();
            player.wardrobe().set(hand, item.def.id());
        }
        if (Inventory.isHotbar(slot)) select(slot);
        return true;
    }

    /** Select hotbar slot {@code slot} and take up what is in it; returns that item, or null. */
    public ItemDef select(int slot) {
        player.inventory().select(slot);
        return equipSelected();
    }

    /**
     * Take up what is in the selected hotbar slot: a weapon with a stance of
     * its own in that stance; anything else - the sword, or nothing - is the
     * sword-and-shield stance (what the wardrobe carries). Returns the item,
     * or null.
     */
    public ItemDef equipSelected() {
        ItemDef d = ItemDef.byId(player.inventory().held());
        player.setStance(d != null && d.held() ? d.stance() : Stance.SWORD);
        return d;
    }

    /** Put down what she holds - the item in the selected hotbar slot; returns it, or null. */
    public ItemDef dropHeld() {
        return dropSlot(player.inventory().selected());
    }

    /**
     * Take the item out of inventory slot {@code slot} and put it on the
     * ground before her (the sword is taken out of her hand too); returns
     * it, or null when the slot is empty.
     */
    public ItemDef dropSlot(int slot) {
        String id = player.inventory().get(slot);
        if (id == null) return null;
        player.inventory().take(slot);
        return dropItem(id);
    }

    /**
     * Put down an item she has already taken out of her inventory (one held
     * on the pointer in the inventory screen): on the ground before her, out
     * of her hand if it is the worn sword and she has no other. Returns it,
     * or null for an id that is no item.
     */
    public ItemDef dropItem(String id) {
        ItemDef def = ItemDef.byId(id);
        if (def == null) return null;
        if (!def.held() && !player.inventory().contains(id)) {
            for (Slot hand : new Slot[]{def.carrySlot(), def.carrySlot().twin()}) {
                if (player.wardrobe().wearing(hand, id)) player.wardrobe().clear(hand);
            }
        }
        equipSelected();
        spawnAhead(def);
        return def;
    }

    private void spawnAhead(ItemDef def) {
        Vec3 ahead = SpriteView.headingVector(player.heading()).scale(1.1);
        spawn(def, player.ground().add(ahead));
    }

    /**
     * Take up {@code stance} with a weapon she carries: true if she has it.
     * Its hotbar slot is selected - one in the rest of the inventory is
     * swapped into the selected slot first. The sword stance needs nothing
     * (it is the wardrobe's): the sword's slot, else a slot with no weapon of
     * a stance of its own, is selected.
     */
    public boolean wield(Stance stance) {
        Inventory inv = player.inventory();
        ItemDef weapon = ItemDef.of(stance);
        if (weapon == null) {
            int pick = inv.indexOf(ItemDef.SWORD.id());
            if (!Inventory.isHotbar(pick)) {
                pick = -1;
                for (int i = 0; i < Inventory.HOTBAR && pick < 0; i++) {
                    ItemDef d = ItemDef.byId(inv.get(i));
                    if (d == null || !d.held()) pick = i;
                }
            }
            if (pick >= 0) inv.select(pick);
            player.setStance(Stance.SWORD);
            return true;
        }
        int at = inv.indexOf(weapon.id());
        if (at < 0) return false;
        if (!Inventory.isHotbar(at)) {
            inv.swap(at, inv.selected());
            at = inv.selected();
        }
        select(at);
        return true;
    }
}
