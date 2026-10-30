package com.larsons.game.world;

import com.larsons.game.combat.Combat;
import com.larsons.game.combat.CombatTuning;
import com.larsons.game.combat.Dummy;
import com.larsons.game.combat.FlyingDummy;
import com.larsons.game.combat.Target;
import com.larsons.game.sprite.Slot;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Everything in the demo void that moves or can be picked up: the player,
 * the items lying around and the chests - and the {@link Scenery} they stand
 * among, whose trees and rocks the player cannot walk into (she walks
 * through the grass).
 *
 * <p>What the player holds is the selected slot of her hotbar ({@link
 * Inventory}), and nothing else: a weapon with a stance of its own (the
 * battle axe, the bow, the crossbow) is taken up in its stance; the sword -
 * worn, in the wardrobe's weapon hand - is the sword-and-shield stance, the
 * wardrobe's own carried items; an empty slot, or one holding something
 * worn, leaves her hands empty ({@link #equipSelected}). A wearable is never
 * in her hands: carried, it is hers to wear from the wardrobe, and dropped
 * (when she has no other) it comes off ({@link Wearables#takeOff}) - as
 * anything taken out of the hotbar and the inventory is no longer equipped.
 *
 * <p>The chests are loot chests: the first time one is opened - and again
 * once everything it gave has been picked up - {@link Loot} draws 3 to 7
 * items - weapons, and the wearables and the armour ({@link
 * Wearables#addTo}) - which pop out of it one after another, fly in an arc and land round
 * its front, each in the beam of light of its rarity ({@link
 * GroundItem#beam}) until it is picked up.
 *
 * <p>The training dummies stand about to be hit, the flying dummies fly
 * their rounds and dive at her, and {@link Combat} plays out every swing
 * and shot: the dice, the damage, the arrows in flight.
 *
 * <p>It all stands on the world's {@link Terrain} - flat until it is given
 * hills ({@link #setTerrain}): she walks and climbs on it, the items lie and
 * the chests stand on it, the shots stick in it. What happens to her - a
 * hard landing, a roll out of a fall, a dive that strikes her - floats up
 * over her as the fighting's numbers do; when her health is gone she is put
 * back where she started ({@link #spawnPoint}).
 */
public final class World {

    /** How close the player has to be to pick something up, in metres. */
    public static final double PICKUP_RANGE = 1.7;
    /**
     * How far round her position the player's feet take up room, in metres:
     * she is kept this much farther than a tree's or a rock's radius from its
     * axis, so her feet stop at the trunk rather than inside it.
     */
    public static final double FEET = 0.22;

    /** How long an item thrown out of a chest is in the air, in seconds. */
    public static final double FLIGHT = 0.9;
    /** How high over the ground a thrown item's arc peaks, in metres. */
    public static final double ARC = 1.5;
    /** How long after the lid starts up the first item comes out, and after that each next one. */
    public static final double FIRST_THROW = 0.35, NEXT_THROW = 0.16;
    /** A lying item's centre floats {@code HOVER} up or more, its lowest point {@code CLEARANCE} off the ground, bobbing {@code BOB} (metres). */
    public static final double HOVER = 0.62, CLEARANCE = 0.2, BOB = 0.09;

    /**
     * An item lying in the world, hovering over its shadow and turning in
     * place - or, when it has just come out of a chest, on its way to where
     * it will lie.
     */
    public static final class GroundItem {
        public final Item item;
        public final ItemDef def;
        /** Where it lies (once it has landed), on the ground. */
        public final Vec3 position;
        private final double phase;
        private final Chest source;
        private final Vec3 from;
        private final double thrownAt;

        GroundItem(Item item, Vec3 position, double phase) {
            this(item, position, phase, null, null, 0);
        }

        GroundItem(Item item, Vec3 position, double phase, Chest source, Vec3 from, double thrownAt) {
            this.item = item;
            this.def = item.def();
            this.position = position;
            this.phase = phase;
            this.source = source;
            this.from = from;
            this.thrownAt = thrownAt;
        }

        /**
         * How high the item's centre floats at time {@code t}: a slow bob,
         * high enough that a long item turning upright (a staff, a bow)
         * clears the ground.
         */
        public double hover(double t) {
            return Math.max(HOVER, def.worldSize() / 2 + CLEARANCE) + BOB * Math.sin(t * 2.1 + phase);
        }

        /** The chest it came out of, or null. */
        public Chest source() { return source; }

        /** Its own offset in time (radians of its bob), so items lying together do not move as one. */
        public double phase() { return phase; }

        /** When it came down where it lies, or minus infinity if it was never thrown. */
        public double landedAt() {
            return from == null ? Double.NEGATIVE_INFINITY : thrownAt + FLIGHT;
        }

        /** Whether it stands in a beam of light: it came out of a chest (and is still lying where it fell). */
        public boolean beam() { return source != null; }

        /** Whether it is out of the chest yet at time {@code t} (an item lying about always is). */
        public boolean out(double t) {
            return from == null || t >= thrownAt;
        }

        /** Whether it has come down where it lies at time {@code t}: only then can it be picked up. */
        public boolean landed(double t) {
            return from == null || t >= thrownAt + FLIGHT;
        }

        /** 0 – 1: how far through its flight it is at {@code t} (1 once landed). */
        public double flight(double t) {
            if (from == null) return 1;
            return Math.max(0, Math.min(1, (t - thrownAt) / FLIGHT));
        }

        /**
         * Where its middle is at time {@code t}: hovering over where it lies,
         * or on its arc out of the chest - up out of the lid, over and down,
         * settling into the hover as it lands.
         */
        public Vec3 centre(double t) {
            double rest = hover(t);
            if (landed(t)) return position.add(0, rest, 0);
            double p = flight(t);
            double x = from.x() + (position.x() - from.x()) * p;
            double z = from.z() + (position.z() - from.z()) * p;
            double y = from.y() + (rest - from.y()) * p + 4 * ARC * p * (1 - p);
            return new Vec3(x, y, z);
        }

        /** How far it has turned at time {@code t}, in turns (it spins in place, faster while in the air). */
        public double spin(double t) {
            double turns = t / 3.2 + phase * 0.37;
            if (from != null && !landed(t)) turns += 1.5 * flight(t);
            else if (from != null) turns += 1.5;
            return turns;
        }
    }

    private final Player player;
    private final List<GroundItem> items = new ArrayList<>();
    private final List<Chest> chests = new ArrayList<>();
    private final Map<Chest, Integer> opensSeen = new IdentityHashMap<>();
    private final Loot loot = Wearables.addTo(new Loot());
    private final List<Dummy> dummies = new ArrayList<>();
    private final List<FlyingDummy> fliers = new ArrayList<>();
    private final Combat combat = new Combat(CombatTuning.defaults());
    private Terrain terrain = Terrain.FLAT;
    private Vec3 spawnPoint = Vec3.ZERO;
    private Random random = new Random();
    private Scenery scenery = Scenery.NONE;
    private double time;

    public World(Wardrobe wardrobe) {
        this(wardrobe, Inventory.DEFAULT_SLOTS);
    }

    /** A world whose player carries an inventory of {@code slots} slots. */
    public World(Wardrobe wardrobe, int slots) {
        player = new Player(wardrobe, slots);
        player.setAimUp(() -> combat.aimsUp(this));
        equipSelected();                // nothing carried yet: her hands are empty
    }

    /** The ground everything stands on. */
    public Terrain terrain() { return terrain; }

    /** Stand everything on {@code t} (null: flat ground) from now on - the player put on it where she is. */
    public void setTerrain(Terrain t) {
        terrain = t == null ? Terrain.FLAT : t;
        player.setTerrain(terrain);
    }

    /** Where she is put back on her feet when her health is gone. */
    public Vec3 spawnPoint() { return spawnPoint; }

    public void setSpawnPoint(Vec3 at) {
        spawnPoint = at == null ? Vec3.ZERO : at;
    }

    public Player player() { return player; }

    public List<GroundItem> items() { return items; }

    public List<Chest> chests() { return chests; }

    public double time() { return time; }

    /** What the chests give. */
    public Loot loot() { return loot; }

    /** Draw the chests' loot - and roll the dice - from this seed from now on (tests and scripted runs). */
    public void seed(long seed) {
        random = new Random(seed);
        combat.seed(seed);
    }

    /** The fighting: the dice, the damage, the shots in flight, her mana. */
    public Combat combat() { return combat; }

    public List<Dummy> dummies() { return dummies; }

    public Dummy addDummy(Dummy dummy) {
        dummies.add(dummy);
        return dummy;
    }

    public List<FlyingDummy> fliers() { return fliers; }

    public FlyingDummy addFlier(FlyingDummy flier) {
        fliers.add(flier);
        return flier;
    }

    /** Everything that can be hit: the dummies, the fliers. */
    public List<Target> targets() {
        List<Target> out = new ArrayList<>(dummies);
        out.addAll(fliers);
        return out;
    }

    /** The trees, grass and rocks standing in the world ({@link Scenery#NONE} until it is given some). */
    public Scenery scenery() { return scenery; }

    /** Plant {@code scenery} (null: none) - drawn from the next frame, in her way from the next tick. */
    public void setScenery(Scenery scenery) {
        this.scenery = scenery == null ? Scenery.NONE : scenery;
    }

    /**
     * Advance the world: the chests' animations ({@code durations}: how long
     * each of a chest's states lasts, from its sheets) and the dummies' - the
     * player kept out of them all (but the dummies while she rolls), and out
     * of the trees and the rocks - and the fighting.
     */
    public void tick(double dt, java.util.function.BiFunction<Chest, Chest.State, Double> durations) {
        time += dt;
        for (Chest c : chests) {
            c.tick(dt, s -> durations == null ? 0 : durations.apply(c, s));
            player.keepOut(c.position(), Chest.RADIUS);
            int opens = c.opens();
            if (opens != opensSeen.getOrDefault(c, 0)) {
                opensSeen.put(c, opens);
                opened(c);
            }
        }
        for (Dummy d : dummies) {
            d.tick(dt);
            if (!player.passesThrough()) player.keepOut(d.position(), Dummy.RADIUS);   // (she rolls through them)
        }
        for (Scenery.Placed s : scenery.solids()) {
            if (Math.abs(s.y() - player.ground().y()) < 2) player.keepOut(s.position(), s.radius() + FEET);
        }
        for (FlyingDummy f : fliers) swooped(f, f.tick(dt, terrain, player));
        combat.tick(dt, this);
        happened();
    }

    /** What a flier's dive did to her, said over her. */
    private void swooped(FlyingDummy f, FlyingDummy.Outcome o) {
        Vec3 at = player.ground();
        switch (o) {
            case WARNED -> combat.say("!", 0xFFD27A, f.position(), FlyingDummy.HEIGHT + 0.3, false);
            case STRUCK -> {
                combat.say("Swooped! -" + Combat.format(FlyingDummy.SWOOP_DAMAGE), HURT_RGB, at, 2.1, false);
                combat.sound("combat/player_hurt", at);
            }
            case BLOCKED -> combat.say("Blocked", 0xC8D2DC, at, 2.1, true);
            case DODGED -> combat.say("Dodged!", 0x9CC4FF, at, 2.1, true);
            case PARRIED -> {
                combat.say("Parried!", 0xFFE08A, at, 2.1, true);
                combat.sound("combat/blow", f.position());
            }
            case NONE -> { }
        }
        if (o == FlyingDummy.Outcome.STRUCK || o == FlyingDummy.Outcome.BLOCKED) combat.sound("combat/blow", at);
    }

    /** The red of her own hurts floating up over her. */
    public static final int HURT_RGB = 0xFF6A5A;

    /** What happened to her this frame, said over her; back at the start when her health is gone. */
    private void happened() {
        Vec3 at = player.ground();
        for (Player.Event e : player.drainEvents()) {
            switch (e.kind()) {
                case FALL -> {
                    combat.say("Hard landing -" + Combat.format(e.amount()), HURT_RGB, at, 2.1, false);
                    combat.sound("combat/hard_landing", at);
                }
                case ROLLED -> {
                    combat.say(e.amount() > 0 ? "Rolled out of the fall" : "Roll", 0x9CC4FF, at, 2.1, true);
                    combat.sound("combat/landing_roll", at);
                }
                case CLIMBED, STEPPED_DOWN -> combat.sound("combat/climb_top", at);
                case LET_GO -> {
                    if (e.amount() > 0) combat.say("Out of stamina", 0xFFD27A, at, 2.1, true);
                }
                case KNOCKED_OUT -> {
                    combat.say("Knocked out", HURT_RGB, at, 2.1, false);
                    player.respawn(spawnPoint);
                    combat.lock(null);
                }
                case HURT -> { }
            }
        }
    }

    /**
     * A chest's lid has started up: if nothing it gave last time still lies
     * about, it gives a new lot - each item thrown out of the lid in turn to
     * a spot in a fan round its front.
     */
    private void opened(Chest c) {
        for (GroundItem g : items) if (g.source == c) return;
        List<Item> lot = loot.roll(random);
        Vec3 lid = c.position().add(0, 0.55, 0);
        int n = lot.size();
        for (int i = 0; i < n; i++) {
            // spread across the chest's front, 140 degrees, nearer and farther by turns
            double spread = Math.toRadians(140);
            double a = c.heading() + (n == 1 ? 0 : -spread / 2 + spread * i / (n - 1))
                    + (random.nextDouble() - 0.5) * 0.15;
            double r = 1.35 + (i % 2) * 0.55 + random.nextDouble() * 0.2;
            Vec3 land = terrain.onGround(c.position().add(SpriteView.headingVector(a).scale(r)));
            items.add(new GroundItem(lot.get(i), land, items.size() * 1.7 + i, c, lid,
                    time + FIRST_THROW + NEXT_THROW * i));
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

    /** Put {@code def}, as it is found lying about ({@link Item#of(ItemDef)}), on the ground at {@code at}. */
    public void spawn(ItemDef def, Vec3 at) {
        spawn(Item.of(def), at);
    }

    public void spawn(Item item, Vec3 at) {
        items.add(new GroundItem(item, terrain.onGround(at), items.size() * 1.7));
    }

    /** The nearest item within reach of the player that has landed, or {@code null}. */
    public GroundItem reachable() {
        GroundItem best = null;
        double bestD = PICKUP_RANGE;
        for (GroundItem g : items) {
            if (!g.landed(time) || Math.abs(g.position.y() - player.ground().y()) > 1.5) continue;
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
     * it lands in, and is drawn there while its slot is the selected one. A
     * wearable goes into no hand and changes nothing she holds: it goes into
     * the rest of the inventory first ({@link Inventory#stow}), out of the
     * hotbar's way, and is not selected. Returns false (and leaves it lying)
     * when every slot is full.
     */
    public boolean pickUp(GroundItem item) {
        int slot = item.def.isWearable() ? player.inventory().stow(item.item) : player.inventory().add(item.item);
        if (slot < 0) return false;
        items.remove(item);
        if (item.def.inHand()) {
            // a left-handed character takes it in the other hand
            Slot hand = player.wardrobe().hand() == Wardrobe.Hand.LEFT ? item.def.carrySlot().twin()
                    : item.def.carrySlot();
            player.wardrobe().set(hand, item.def.id());
        }
        if (Inventory.isHotbar(slot) && !item.def.isWearable()) select(slot);
        return true;
    }

    /** Select hotbar slot {@code slot} and take up what is in it; returns that item, or null. */
    public Item select(int slot) {
        player.inventory().select(slot);
        return equipSelected();
    }

    /**
     * Take up what is in the selected hotbar slot: a weapon with a stance of
     * its own in that stance; the sword in the sword-and-shield stance (what
     * the wardrobe carries - the sword put back in its hand if the wardrobe
     * had let go of it); and with nothing in the slot, or something worn,
     * nothing at all: her hands are empty ({@link Player#handsEmpty}).
     * Returns the item, or null.
     */
    public Item equipSelected() {
        Item held = player.inventory().held();
        boolean weapon = held != null && held.def().held();
        boolean sword = held != null && held.def().inHand();
        player.setStance(weapon ? held.def().stance() : Stance.SWORD);
        player.setHandsEmpty(!weapon && !sword);
        if (sword) {
            Wardrobe w = player.wardrobe();
            ItemDef d = held.def();
            if (!w.wearing(d.carrySlot(), d.id()) && !w.wearing(d.carrySlot().twin(), d.id())) {
                w.set(w.hand() == Wardrobe.Hand.LEFT ? d.carrySlot().twin() : d.carrySlot(), d.id());
            }
        }
        return held;
    }

    /**
     * The item in her hands, whose look they draw and whose dice a swing
     * rolls: the one in the selected slot when that is a weapon - a
     * stance's, or the sword. Null when her hands are empty.
     */
    public Item wielded() {
        Item held = player.inventory().held();
        return held != null && (held.def().held() || held.is(ItemDef.SWORD)) ? held : null;
    }

    /** Put down what she holds - the item in the selected hotbar slot; returns it, or null. */
    public Item dropHeld() {
        return dropSlot(player.inventory().selected());
    }

    /**
     * Take the item out of inventory slot {@code slot} and put it on the
     * ground before her (the sword is taken out of her hand too, a wearable
     * off her - see {@link #dropItem}); returns it, or null when the slot is
     * empty.
     */
    public Item dropSlot(int slot) {
        Item item = player.inventory().get(slot);
        if (item == null) return null;
        player.inventory().take(slot);
        return dropItem(item);
    }

    /**
     * Put down an item she has already taken out of her inventory (one held
     * on the pointer in the inventory screen): on the ground before her, out
     * of her hand if it is the worn sword and she has no other, and off her
     * if it is a wearable she owns no other of ({@link Wearables#takeOff}).
     * Returns it, or null for no item.
     */
    public Item dropItem(Item item) {
        if (item == null) return null;
        ItemDef def = item.def();
        if (def.inHand() && !player.inventory().contains(def)) {
            for (Slot hand : new Slot[]{def.carrySlot(), def.carrySlot().twin()}) {
                if (player.wardrobe().wearing(hand, def.id())) player.wardrobe().clear(hand);
            }
        }
        Wearables.takeOff(player.wardrobe(), player.inventory(), def);
        equipSelected();
        spawnAhead(item);
        return item;
    }

    /**
     * Make what she wears hers: every wearable the wardrobe has on her that
     * she does not own yet goes into her inventory - the rest of it before
     * the hotbar - so that taking it out of the inventory takes it off
     * ({@link #dropItem}). For the start of a game, whose wardrobe was saved
     * and whose inventory was not; what has no room is left on. Returns how
     * many were put in.
     */
    public int ownWhatIsWorn() {
        Wardrobe w = player.wardrobe();
        Inventory inv = player.inventory();
        int added = 0;
        for (Slot slot : Slot.values()) {
            if (slot.carried() || Wearables.free(slot)) continue;
            String folder = w.get(slot);
            if (folder == null) continue;
            ItemDef d = Wearables.wornAs(slot, folder, w.colour(slot));
            if (d == null || Wearables.owned(inv).contains(d)) continue;
            if (inv.stow(Item.of(d)) >= 0) added++;
        }
        return added;
    }

    /**
     * Take every item off the ground - what lay about from the start (the
     * weapons) and what the chests gave - so a chest opened again gives a new
     * lot. Returns how many there were.
     */
    public int clearGround() {
        int n = items.size();
        items.clear();
        return n;
    }

    private void spawnAhead(Item item) {
        Vec3 ahead = SpriteView.headingVector(player.heading()).scale(1.1);
        spawn(item, player.ground().add(ahead));
    }

    /**
     * Take up {@code stance} with a weapon she carries: true if she has it.
     * Its hotbar slot is selected - one in the rest of the inventory is
     * swapped into the selected slot first. The sword stances' weapon is the
     * sword; the free stance (empty hands) selects a hotbar slot with no
     * weapon in it, false when every one holds one.
     */
    public boolean wield(Stance stance) {
        Inventory inv = player.inventory();
        if (stance == Stance.FREE) {
            for (int i = 0; i < Inventory.HOTBAR; i++) {
                Item d = inv.get(i);
                if (d == null || d.def().isWearable()) {
                    select(i);
                    return true;
                }
            }
            return false;
        }
        ItemDef weapon = ItemDef.of(stance);
        if (weapon == null) weapon = ItemDef.SWORD;
        int at = inv.indexOf(weapon);
        if (at < 0) return false;
        if (!Inventory.isHotbar(at)) {
            inv.swap(at, inv.selected());
            at = inv.selected();
        }
        select(at);
        return true;
    }
}
