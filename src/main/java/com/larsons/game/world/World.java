package com.larsons.game.world;

import com.larsons.game.sprite.Slot;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything in the demo void that moves or can be picked up: the player and
 * the items lying around. The static 3D environment lives in {@link Props}.
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
    private double time;

    public World(Wardrobe wardrobe) {
        player = new Player(wardrobe);
    }

    public Player player() { return player; }

    public List<GroundItem> items() { return items; }

    public double time() { return time; }

    public void tick(double dt) {
        time += dt;
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
     * Pick {@code item} up: into the inventory and straight into her hands -
     * the sword into its hand in the wardrobe (and she takes up the sword
     * stance), a weapon with a stance of its own by taking up that stance.
     */
    public void pickUp(GroundItem item) {
        items.remove(item);
        player.inventory().add(item.def.id());
        if (!item.def.held()) {
            // a left-handed character takes it in the other hand
            Slot slot = player.wardrobe().hand() == Wardrobe.Hand.LEFT ? item.def.carrySlot().twin()
                    : item.def.carrySlot();
            player.wardrobe().set(slot, item.def.id());
        }
        player.setStance(item.def.stance());
    }

    /**
     * Put down what she holds: the weapon of her stance (she goes back to the
     * sword stance), else a worn item in the hand it goes in (then the other
     * hand); returns it, or null.
     */
    public ItemDef dropHeld() {
        ItemDef weapon = ItemDef.of(player.stance());
        if (weapon != null && player.inventory().remove(weapon.id())) {
            player.setStance(Stance.SWORD);
            spawnAhead(weapon);
            return weapon;
        }
        for (ItemDef def : ItemDef.ALL) {
            if (def.held()) continue;
            for (Slot slot : new Slot[]{def.carrySlot(), def.carrySlot().twin()}) {
                if (player.inventory().contains(def.id()) && player.wardrobe().wearing(slot, def.id())) {
                    player.inventory().remove(def.id());
                    player.wardrobe().clear(slot);
                    spawnAhead(def);
                    return def;
                }
            }
        }
        return null;
    }

    private void spawnAhead(ItemDef def) {
        Vec3 ahead = SpriteView.headingVector(player.heading()).scale(1.1);
        spawn(def, player.ground().add(ahead));
    }

    /**
     * Take up {@code stance} with a weapon she carries: true if she has it
     * (the sword stance needs nothing - it is the wardrobe's).
     */
    public boolean wield(Stance stance) {
        ItemDef weapon = ItemDef.of(stance);
        if (weapon != null && !player.inventory().contains(weapon.id())) return false;
        player.setStance(stance);
        return true;
    }
}
