package com.larsons.game.world;

import com.larsons.game.ambient.Animal;
import com.larsons.game.ambient.AnimalKind;
import com.larsons.game.ambient.Animals;
import com.larsons.game.ambient.Critters;
import com.larsons.game.ambient.Life;
import com.larsons.game.ambient.Temperament;
import com.larsons.game.audio.SoundKeys;
import com.larsons.game.building.Building;
import com.larsons.game.building.BuildingProfile;
import com.larsons.game.combat.Combat;
import com.larsons.game.combat.CombatTuning;
import com.larsons.game.combat.Dummy;
import com.larsons.game.combat.FlyingDummy;
import com.larsons.game.combat.Target;
import com.larsons.game.sprite.SceneryProfile;
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
 * <p>The chests are loot chests: the first time one is opened - and only
 * then: it is empty after, and a playthrough remembers it - {@link Loot} draws 3 to 7
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
        /** How many of it lie here: a dropped stack of pickings is one heap. */
        int amount = 1;
        /** Set out for show (on a table, a counter, a weapon rack): still, turned one way, not to be picked up. */
        boolean shown;
        /** Which way it is turned, set out for show (radians, as a heading). */
        double heading;
        /** The weapon rack it stands on, or null. */
        Store rack;

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

        /** How many of it lie here (a dropped stack of berries: all of it). */
        public int amount() { return amount; }

        /**
         * How high the item's centre floats at time {@code t}: a slow bob,
         * high enough that a long item turning upright (a staff, a bow)
         * clears the ground.
         */
        public double hover(double t) {
            if (shown) return rack != null ? 0 : def.worldSize() / 2;
            return Math.max(HOVER, def.worldSize() / 2 + CLEARANCE) + BOB * Math.sin(t * 2.1 + phase);
        }

        /** The chest it came out of, or null. */
        public Chest source() { return source; }

        /**
         * Whether it is set out for show - a joint of meat on a counter, a
         * bowl's berries, a gem on a shelf, a sword on a rack: it stands
         * still, turned the one way, without glow or light, and is not
         * picked up.
         */
        public boolean shown() { return shown; }

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
         * settling into the hover over the ground where it lands (up on a
         * hill as on the flat).
         */
        public Vec3 centre(double t) {
            double rest = position.y() + hover(t);
            if (landed(t)) return new Vec3(position.x(), rest, position.z());
            double p = flight(t);
            double x = from.x() + (position.x() - from.x()) * p;
            double z = from.z() + (position.z() - from.z()) * p;
            double y = from.y() + (rest - from.y()) * p + 4 * ARC * p * (1 - p);
            return new Vec3(x, y, z);
        }

        /** How far it has turned at time {@code t}, in turns (it spins in place, faster while in the air). */
        public double spin(double t) {
            if (shown) return -heading / (2 * Math.PI);
            double turns = t / 3.2 + phase * 0.37;
            if (from != null && !landed(t)) turns += 1.5 * flight(t);
            else if (from != null) turns += 1.5;
            return turns;
        }
    }

    private final Player player;
    private final List<GroundItem> items = new ArrayList<>();
    private final List<Chest> chests = new ArrayList<>();
    private final Buildings buildings = new Buildings();

    /** The buildings: a list that counts its changes, so the index of where they stand knows when to be made again. */
    private static final class Buildings extends ArrayList<Building> {
        int version() { return modCount; }
    }

    /** The buildings by the 8 m cells their footprints reach into (made again when the list changes). */
    private final Map<Long, List<Building>> buildingCells = new java.util.HashMap<>();
    private int buildingsIndexed = -1;
    private static final double BUILDING_CELL = 8;

    /** The buildings that may reach within {@code radius} of {@code (x, z)} (a few, of a town's hundreds). */
    private List<Building> buildingsNear(double x, double z, double radius) {
        if (buildingsIndexed != buildings.version()) {
            buildingCells.clear();
            for (Building b : buildings) {
                double r = b.reach() + 0.5;
                long i0 = (long) Math.floor((b.position().x() - r) / BUILDING_CELL);
                long i1 = (long) Math.floor((b.position().x() + r) / BUILDING_CELL);
                long j0 = (long) Math.floor((b.position().z() - r) / BUILDING_CELL);
                long j1 = (long) Math.floor((b.position().z() + r) / BUILDING_CELL);
                for (long i = i0; i <= i1; i++) {
                    for (long j = j0; j <= j1; j++) {
                        buildingCells.computeIfAbsent((i << 32) ^ Math.floorMod(j, 1L << 32), k -> new ArrayList<>())
                                .add(b);
                    }
                }
            }
            buildingsIndexed = buildings.version();
        }
        long i0 = (long) Math.floor((x - radius) / BUILDING_CELL), i1 = (long) Math.floor((x + radius) / BUILDING_CELL);
        long j0 = (long) Math.floor((z - radius) / BUILDING_CELL), j1 = (long) Math.floor((z + radius) / BUILDING_CELL);
        if (i0 == i1 && j0 == j1) {
            List<Building> in = buildingCells.get((i0 << 32) ^ Math.floorMod(j0, 1L << 32));
            return in == null ? List.of() : in;
        }
        List<Building> out = new ArrayList<>();
        for (long i = i0; i <= i1; i++) {
            for (long j = j0; j <= j1; j++) {
                List<Building> in = buildingCells.get((i << 32) ^ Math.floorMod(j, 1L << 32));
                if (in == null) continue;
                for (Building b : in) if (!out.contains(b)) out.add(b);
            }
        }
        return out;
    }
    /** The barrels, crates and storage chests: what each holds, and how near breaking a barrel or a crate is. */
    private final List<Store> stores = new ArrayList<>();
    /** The market stalls and shop counters: what each sells, and what is left of it. */
    private final List<Shop> shops = new ArrayList<>();
    /** The other things she can use: the way back to the map, a cauldron, a locked door ... */
    private final List<Spot> spots = new ArrayList<>();
    /** The pieces what she breaks shatters into. */
    private final Shards shards = new Shards();
    private final Map<Chest, Integer> opensSeen = new IdentityHashMap<>();
    private final Loot loot = Tack.addTo(Wearables.addTo(new Loot()));
    private final List<Dummy> dummies = new ArrayList<>();
    private final List<FlyingDummy> fliers = new ArrayList<>();
    private final List<SoundSpot> soundSpots = new ArrayList<>();
    private final Combat combat = new Combat(CombatTuning.defaults());
    /** How easily she is noticed now, worked out a few times a second. */
    private Stealth stealth = Stealth.OPEN;
    private double stealthClock, lastHidden = Double.NEGATIVE_INFINITY;
    private Terrain terrain = Terrain.FLAT;
    private Vec3 spawnPoint = Vec3.ZERO;
    private Random random = new Random();
    private Scenery scenery = Scenery.NONE;
    private GroundMap ground = GroundMap.NONE;
    private WaterMap water = WaterMap.NONE;
    private Sky sky = Sky.DEFAULT;
    private Weather weather = Weather.NONE;
    private final Weather.Now weatherNow = new Weather.Now(1025);
    /** The biome the weather is for: where she was last out of doors. */
    private Biome weatherBiome = Biome.PLAINS;
    private Sky.Cover cover = Sky.Cover.OPEN;
    private final DayCycle clock = new DayCycle(Sky.DEFAULT.air().startHour());
    private final List<LightSpot> lights = new ArrayList<>();
    private final List<RaySpot> raySpots = new ArrayList<>();
    private Critters critters = new Critters();
    private Animals animals = new Animals();
    /** The people and the foes of the place. */
    private com.larsons.game.actor.Folks folks = new com.larsons.game.actor.Folks();
    private com.larsons.game.actor.Folks.Clock folkClock;
    /** What has been felled since the scene last asked ({@link #drainKills}). */
    private final List<Kill> kills = new ArrayList<>();
    private Animal steed;
    private RideTrack rideTrack = RideTrack.standard();
    /** Whether she is inside a building: no sky over it, its own dim light, nothing comes by. */
    private boolean indoors;
    /** The foraging bushes standing in it, and when each one picked has its pickings again. */
    private List<Scenery.Placed> bushes = List.of();
    private final Map<Scenery.Placed, Double> picked = new IdentityHashMap<>();
    /** Where the little creatures live round when nobody is playing (the editor's view); null: round her. */
    private Vec3 focus;
    /** The edges she is kept within (x from, x to, z from, z to), or null: none. */
    private double[] bounds;
    private double time;

    public World(Wardrobe wardrobe) {
        this(wardrobe, Inventory.DEFAULT_SLOTS);
    }

    /**
     * What there is of one place - its ground and what stands on it, lies on
     * it and lives in it, its sky, its edges - kept while she is somewhere
     * else ({@link #leave}) and found as it was when she comes back ({@link
     * #enter}): the outdoors while she is inside a building, a floor of it
     * while she is on another. She, the fighting, the clock and the chests'
     * loot go with her.
     */
    public static final class Place {
        private final List<GroundItem> items;
        private final List<Chest> chests;
        private final List<Building> buildings, playerWalls;
        private final List<Store> stores;
        private final List<Shop> shops;
        private final List<Spot> spots;
        private final List<Dummy> dummies;
        private final List<FlyingDummy> fliers;
        private final List<SoundSpot> soundSpots;
        private final List<LightSpot> lights;
        private final List<RaySpot> raySpots;
        private final Critters critters;
        private final Animals animals;
        private final com.larsons.game.actor.Folks folks;
        private final Terrain terrain;
        private final Vec3 spawnPoint;
        private final Scenery scenery;
        private final Map<Scenery.Placed, Double> picked;
        private final GroundMap ground;
        private final WaterMap water;
        private final Sky sky;
        private final double[] bounds;
        private final boolean indoors;

        private Place(World w) {
            items = List.copyOf(w.items);
            chests = List.copyOf(w.chests);
            buildings = List.copyOf(w.buildings);
            playerWalls = List.copyOf(w.playerWalls);
            stores = List.copyOf(w.stores);
            shops = List.copyOf(w.shops);
            spots = List.copyOf(w.spots);
            dummies = List.copyOf(w.dummies);
            fliers = List.copyOf(w.fliers);
            soundSpots = List.copyOf(w.soundSpots);
            lights = List.copyOf(w.lights);
            raySpots = List.copyOf(w.raySpots);
            critters = w.critters;
            animals = w.animals;
            folks = w.folks;
            terrain = w.terrain;
            spawnPoint = w.spawnPoint;
            scenery = w.scenery;
            picked = new IdentityHashMap<>(w.picked);
            ground = w.ground;
            water = w.water;
            sky = w.sky;
            bounds = w.bounds;
            indoors = w.indoors;
        }

        /** The buildings standing in it. */
        public List<Building> buildings() { return buildings; }

        /** Whether it is inside a building. */
        public boolean indoors() { return indoors; }

        /** The folk of it. */
        public com.larsons.game.actor.Folks folks() { return folks; }
    }

    /**
     * Leave the place she is in: all of it kept, to {@link #enter} again,
     * and the world left empty of it - flat ground, nothing in it, nothing
     * living in it - for the next place to be built in.
     */
    public Place leave() {
        Place p = new Place(this);
        items.clear();
        chests.clear();
        buildings.clear();
        playerWalls.clear();
        stores.clear();
        shops.clear();
        spots.clear();
        shards.clear();
        dummies.clear();
        fliers.clear();
        soundSpots.clear();
        lights.clear();
        raySpots.clear();
        Life life = critters.life();
        critters = new Critters();
        animals = new Animals();
        folks = new com.larsons.game.actor.Folks();
        folks.setClock(folkClock);
        setLife(life);
        setScenery(Scenery.NONE);
        picked.clear();
        setTerrain(Terrain.FLAT);
        setGround(GroundMap.NONE);
        setWater(WaterMap.NONE);
        bounds = null;
        return p;
    }

    /** Come back to {@code p}, a place she {@link #leave}s, as it was left (she is put where she is put). */
    public void enter(Place p) {
        items.clear();
        items.addAll(p.items);
        chests.clear();
        chests.addAll(p.chests);
        buildings.clear();
        playerWalls.clear();
        buildings.addAll(p.buildings);
        playerWalls.addAll(p.playerWalls);
        stores.clear();
        stores.addAll(p.stores);
        shops.clear();
        shops.addAll(p.shops);
        spots.clear();
        spots.addAll(p.spots);
        dummies.clear();
        dummies.addAll(p.dummies);
        fliers.clear();
        fliers.addAll(p.fliers);
        soundSpots.clear();
        soundSpots.addAll(p.soundSpots);
        lights.clear();
        lights.addAll(p.lights);
        raySpots.clear();
        raySpots.addAll(p.raySpots);
        critters = p.critters;
        animals = p.animals;
        folks = p.folks;
        setTerrain(p.terrain);
        spawnPoint = p.spawnPoint;
        setScenery(p.scenery);
        picked.clear();
        picked.putAll(p.picked);
        setGround(p.ground);
        setWater(p.water);
        sky = p.sky;
        bounds = p.bounds;
        indoors = p.indoors;
    }

    /** Whether she is inside a building: no sky over it - its own dim light - and nothing comes by. */
    public boolean indoors() { return indoors; }

    public void setIndoors(boolean in) {
        indoors = in;
    }

    /** A world whose player carries an inventory of {@code slots} slots. */
    public World(Wardrobe wardrobe, int slots) {
        player = new Player(wardrobe, slots);
        player.setAimUp(() -> combat.aimsUp(this));
        player.setTops(tops);
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

    /** The houses, mills and castle pieces standing in it: solid, she walks round them. */
    public List<Building> buildings() { return buildings; }

    public Building addBuilding(Building b) {
        buildings.add(b);
        return b;
    }

    /** Keep {@code s} in the world: a barrel's, a crate's or a storage chest's hold. */
    public Store addStore(Store s) {
        stores.add(s);
        return s;
    }

    /** The barrels, crates and storage chests. */
    public List<Store> stores() { return stores; }

    /** Keep {@code s} in the world: a market stall or a shop's counter. */
    public Shop addShop(Shop s) {
        shops.add(s);
        return s;
    }

    /** The market stalls and shop counters. */
    public List<Shop> shops() { return shops; }

    /** Keep {@code s} in the world: something she can walk up to and use. */
    public Spot addSpot(Spot s) {
        spots.add(s);
        return s;
    }

    /**
     * What stands in her way and no one else's: a barricade's gap, which the
     * Risen come through once its boards are down and she never goes out by.
     * Not drawn.
     */
    private final List<Building> playerWalls = new ArrayList<>();

    public Building addPlayerWall(Building b) {
        playerWalls.add(b);
        return b;
    }

    public List<Building> playerWalls() { return playerWalls; }

    /** The building with the level's object id {@code id}, taken out of the world (a barricade torn down), or null. */
    public Building takeBuilding(int id) {
        for (int i = 0; i < buildings.size(); i++) {
            if (buildings.get(i).id() == id) return buildings.remove(i);
        }
        return null;
    }

    /**
     * Where a horde map's Risen come from: {@code kind} a breed's key (or
     * "any"), used from round {@code from} on, and - {@code after} not blank
     * - only once the door or gate of that name has been opened.
     */
    public record HordeSpawn(int id, Vec3 at, String kind, int from, String after) {}

    private final List<HordeSpawn> hordeSpawns = new ArrayList<>();

    public List<HordeSpawn> hordeSpawns() { return hordeSpawns; }

    private int knockOuts;

    /** How many times she has been knocked out here (the horde counts her lives by it). */
    public int knockOuts() { return knockOuts; }

    /** The other things she can use. */
    public List<Spot> spots() { return spots; }

    /** The nearest spot she can reach, or null. */
    public Spot reachableSpot() {
        Spot best = null;
        double bestD = Double.MAX_VALUE;
        for (Spot s : spots) {
            if (!s.inReach(player.ground())) continue;
            double d = s.position().horizontalDistance(player.ground());
            if (d < bestD) {
                best = s;
                bestD = d;
            }
        }
        return best;
    }

    /** The nearest shop she can reach, or null. */
    public Shop reachableShop() {
        Shop best = null;
        double bestD = Double.MAX_VALUE;
        for (Shop s : shops) {
            if (!s.inReach(player.ground())) continue;
            double d = s.position().horizontalDistance(player.ground());
            if (d < bestD) {
                best = s;
                bestD = d;
            }
        }
        return best;
    }

    /** The store that is level object {@code id}, or null. */
    public Store store(int id) {
        for (Store s : stores) if (s.id() == id) return s;
        return null;
    }

    /** The chest sprite {@code s} is kept in, or null (a 3D storage chest is a building). */
    Chest chestOf(Store s) {
        for (Chest c : chests) if (c.store() == s) return c;
        return null;
    }

    /**
     * The nearest barrel, crate, pot, weapon rack or 3D storage chest she
     * can reach into, or null (a chest of the old sprite is opened as a chest).
     */
    public Store reachableStore() {
        Store best = null;
        double bestD = Double.MAX_VALUE;
        for (Store s : stores) {
            if (s.kind() == Store.Kind.CHEST && chestOf(s) != null || !s.inReach(player.ground())) continue;
            double d = s.position().horizontalDistance(player.ground());
            if (d < bestD) {
                best = s;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * {@code s} broken open: gone (its barrel or crate taken away), what it
     * held spilled out round where it stood - never through a wall - and
     * the crack of it.
     */
    void smash(Store s) {
        stores.remove(s);
        int[] colours = null;
        for (Building b : buildings) {
            if (b.id() == s.id() && Store.Kind.ofBuilding(b.key()) != null) {
                colours = partColours(b);
                break;
            }
        }
        buildings.removeIf(b -> b.id() == s.id() && Store.Kind.ofBuilding(b.key()) != null);
        chests.removeIf(c -> c.store() == s);
        Vec3 at = s.position();
        shards.burst(at, s.radius() * 0.8, s.height(), colours == null ? CHEST_COLOURS : colours, s.kind().pieces(),
                s.kind().pieceSize(), terrain);
        combat.sound("world/" + s.kind().key() + "_break", at, 1);
        combat.say("The " + s.kind().label().toLowerCase(java.util.Locale.ROOT)
                + (s.kind().clay() ? " shatters" : " breaks"), 0xE8D8A8, at, s.height() + 0.4, true);
        Inventory held = s.items();
        int k = 0;
        for (int i = 0; i < held.size(); i++) {
            Item it = held.get(i);
            if (it == null) continue;
            double ang = s.heading() + k * 2.39996, reach = 0.35 + 0.18 * (k % 3);
            double x = at.x() + Math.cos(ang) * reach, z = at.z() + Math.sin(ang) * reach;
            if (!lootFits(x, z)) {
                x = at.x();
                z = at.z();
            }
            spawn(it, new Vec3(x, 0, z), held.amount(i));
            k++;
        }
    }

    /** A storage chest's pieces: its wood, its darker boards, its brass. */
    private static final int[] CHEST_COLOURS = {0x7A4A2A, 0x7A4A2A, 0x5A3A22, 0xC8A050};

    /** {@code b}'s parts' colours, as it is coloured - its first part (its body) the most of them. */
    private static int[] partColours(Building b) {
        var parts = b.profile().parts();
        if (parts.isEmpty()) return null;
        int[] out = new int[parts.size() + 2];
        for (int i = 0; i < parts.size(); i++) out[i] = b.look().colour(parts.get(i));
        out[parts.size()] = out[0];
        out[parts.size() + 1] = out[0];
        return out;
    }

    /** The pieces flying and lying about from what she has broken. */
    public Shards shards() { return shards; }

    /** The building that is level object {@code id}, or null. */
    public Building building(int id) {
        for (Building b : buildings) if (b.id() == id) return b;
        return null;
    }

    public double time() { return time; }

    /** What the chests give. */
    public Loot loot() { return loot; }

    /** Draw the chests' loot - and roll the dice - from this seed from now on (tests and scripted runs). */
    public void seed(long seed) {
        random = new Random(seed);
        combat.seed(seed);
        critters.seed(seed);
        animals.seed(seed + 1);
    }

    /** The fighting: the dice, the damage, the shots in flight, her mana. */
    public Combat combat() { return combat; }

    // --- a card's Transfigure ------------------------------------------------------

    /** What a Transfigure turns things into: animals that walk the ground. */
    public static final AnimalKind[] TRANSFIGURE_INTO = {AnimalKind.SHEEP, AnimalKind.PIG, AnimalKind.RABBIT,
            AnimalKind.CAT, AnimalKind.DOG, AnimalKind.FOX, AnimalKind.DUCK, AnimalKind.GOOSE, AnimalKind.PHEASANT,
            AnimalKind.IGUANA, AnimalKind.COW, AnimalKind.DEER};

    /** Something turned into an animal: what it was (an animal's kind; null: a dummy, hidden meanwhile), and until when. */
    private record Turned(AnimalKind was, com.larsons.game.ambient.Temperament temper, Animal as, double until) {}

    private final List<Turned> turned = new ArrayList<>();

    /**
     * Turn {@code t} into an animal picked at random for {@code seconds}: a
     * dummy (or a flier) goes for that long and is back when it is over; an
     * animal becomes another, and turns back where that one is then. False
     * when it cannot be (a ridden horse, a barrel).
     */
    public boolean transfigure(Target t, double seconds) {
        Vec3 at = terrain.onGround(t.position());
        AnimalKind into = TRANSFIGURE_INTO[random.nextInt(TRANSFIGURE_INTO.length)];
        AnimalKind was = null;
        com.larsons.game.ambient.Temperament temper = null;
        if (t instanceof Dummy d) {
            d.hide(seconds);
        } else if (t instanceof FlyingDummy f) {
            f.hide(seconds);
        } else if (t instanceof Animal a && !a.ridden() && !a.saddled()) {
            if (a.kind() == into) into = into == AnimalKind.SHEEP ? AnimalKind.PIG : AnimalKind.SHEEP;
            was = a.kind();
            temper = a.temperament();
            animals.remove(a);
        } else {
            return false;
        }
        Animal as = animals.add(into, at, com.larsons.game.ambient.Temperament.CALM, place());
        turned.add(new Turned(was, temper, as, time + seconds));
        return true;
    }

    /** The Transfigures run out: the stand-ins go, the animals turn back. */
    private void turnBack() {
        for (java.util.Iterator<Turned> it = turned.iterator(); it.hasNext(); ) {
            Turned tu = it.next();
            if (time < tu.until()) continue;
            it.remove();
            Vec3 at = tu.as() != null ? tu.as().position() : null;
            if (tu.as() != null) animals.remove(tu.as());
            if (tu.was() != null && at != null) animals.add(tu.was(), terrain.onGround(at), tu.temper(), place());
        }
    }

    /** How easily she is noticed now ({@link Stealth}). */
    public Stealth stealth() { return stealth; }

    /**
     * Whether a blow she starts now is a sneak attack: she was hidden a
     * moment ago ({@link #SNEAK_GRACE} s) - standing up to swing gives her away
     * only once the blow has landed.
     */
    public boolean sneaky() { return time - lastHidden <= SNEAK_GRACE; }

    /** How long after she was last hidden a blow she starts is still a sneak attack, seconds. */
    public static final double SNEAK_GRACE = 0.6;

    /** What the cards she has played are doing for her ({@link Player#buffs}). */
    public Buffs buffs() { return player.buffs(); }

    /** Her weapons' ammunition in use ({@link Ammo}). */
    public Ammo ammo() { return ammo; }

    private final Ammo ammo = new Ammo();

    public List<Dummy> dummies() { return dummies; }

    public Dummy addDummy(Dummy dummy) {
        dummies.add(dummy);
        return dummy;
    }

    public List<FlyingDummy> fliers() { return fliers; }

    /** A sound that keeps playing at a spot of a level - a stream, crickets - heard nearer it. */
    public record SoundSpot(String key, Vec3 at) {}

    /** The sounds placed in the level, played by its {@code WorldSounds}. */
    public List<SoundSpot> soundSpots() { return soundSpots; }

    public FlyingDummy addFlier(FlyingDummy flier) {
        fliers.add(flier);
        return flier;
    }

    /** Everything that can be hit: the dummies, the fliers, the animals about (but the horse she rides). */
    public List<Target> targets() {
        List<Target> out = new ArrayList<>();
        for (Dummy d : dummies) if (!d.hidden()) out.add(d);
        for (FlyingDummy f : fliers) if (!f.hidden()) out.add(f);
        for (Store s : stores) if (s.alive()) out.add(s);
        for (Animal a : animals.live()) if (a.alive() && !a.ridden()) out.add(a);
        for (com.larsons.game.actor.Folk f : folks.all()) if (f.alive()) out.add(f);
        for (com.larsons.game.actor.Serpent sp : folks.serpents()) if (sp.alive()) out.add(sp);
        return out;
    }

    // --- the folk: people to talk to, foes to fight ------------------------------------

    /** Something felled: what kind of thing ("bear", "bortzal", "risen_walker", "dummy"), its name if it had one, where. */
    public record Kill(String kind, String name, Vec3 at, boolean risen) {}

    /** What has been felled since the last call: the quests and the horde count them. */
    public List<Kill> drainKills() {
        List<Kill> out = List.copyOf(kills);
        kills.clear();
        return out;
    }

    /** The people and the foes of the place she is in. */
    public com.larsons.game.actor.Folks folks() { return folks; }

    /** Take the length of the folk's attacks from {@code c} (the sprites' own). */
    public void setFolkClock(com.larsons.game.actor.Folks.Clock c) {
        folkClock = c;
        folks.setClock(c);
    }

    /** The person near enough to talk to, or null. */
    public com.larsons.game.actor.Folk reachablePerson() {
        return player.airborne() || player.mounted() ? null : folks.reachable(player.ground());
    }

    /** What one of the folk did to her (or had done to it), said over her and heard. */
    private void metFolk(com.larsons.game.actor.Folks.Event e) {
        com.larsons.game.actor.Folk f = e.folk();
        Vec3 at = player.ground();
        switch (e.outcome()) {
            case NOTICED -> {
                combat.say("!", 0xFF6A5A, f.position(), f.height() + 0.25, false);
                combat.sound(f.breed() != null && f.breed().risen() ? "foes/risen_groan" : "foes/alert", f.position());
            }
            case STRUCK -> {
                double thorns = player.buffs().plus(Buffs.Kind.THORNS);
                if (thorns > 0 && e.damage() > 0 && f.alive()) f.hurt(e.damage() * thorns, player.ground(), true);
                combat.say("-" + Combat.format(e.damage()), HURT_RGB, at, 2.1, false);
                combat.sound("combat/player_hurt", at);
            }
            case BLOCKED -> {
                combat.say("Blocked", 0xC8D2DC, at, 2.1, true);
                combat.sound("combat/block", at);
            }
            case DODGED -> {
                combat.say("Dodged!", 0x9CC4FF, at, 2.1, true);
                combat.sound("combat/dodge", f.position());
            }
            case PARRIED -> {
                combat.say("Parried!", 0xFFE08A, at, 2.1, true);
                combat.sound("combat/parry", f.position());
            }
            case GUARDED -> {
                combat.say("Guarded", 0xC8D2DC, f.position(), f.height() + 0.2, true);
                combat.sound("combat/block", f.position());
            }
            case FELL -> fellFolk(f);
        }
    }

    /** One of the folk felled: experience, what it carried, and the count kept of it. */
    private void fellFolk(com.larsons.game.actor.Folk f) {
        com.larsons.game.actor.Breed b = f.breed();
        Vec3 at = f.position();
        combat.sound(b != null && b.risen() ? "foes/risen_fall" : "foes/fall", at);
        kills.add(new Kill(b == null ? f.people().key() : b.key(), f.hasName() ? f.name() : null, at,
                b != null && b.risen()));
        if (b == null) return;
        earn(b.xp() * Math.max(0.6, f.size()));
        if (b.risen() && hordeLoot != null && !hordeLoot.test(f)) return;
        Random r = new Random(Double.doubleToLongBits(at.x() * 31 + at.z() + time));
        int coins = 1 + r.nextInt(Math.max(1, b.xp() / 4));
        spawn(Item.of(Supplies.COINS), new Vec3(at.x(), 0, at.z()), coins);
        Item w = f.weapon();
        if (w != null && r.nextInt(6) == 0) spawn(w, new Vec3(at.x() + 0.4, 0, at.z() + 0.2));
        else if (r.nextInt(5) == 0) {
            Item drop = loot.pick(r, Rarity.roll(r));
            if (drop != null) spawn(drop, new Vec3(at.x() + 0.4, 0, at.z() + 0.2));
        }
    }

    /** Whether one of the horde, felled, leaves what a foe leaves (the horde mode pays in points instead); null: yes. */
    private java.util.function.Predicate<com.larsons.game.actor.Folk> hordeLoot;

    public void setHordeLoot(java.util.function.Predicate<com.larsons.game.actor.Folk> leaves) {
        hordeLoot = leaves;
    }

    /** A part of the level with a name: where a quest's quarry is, what it asks her to clear or to reach. */
    public record Area(String name, Vec3 at, double radius) {
        public boolean holds(Vec3 p) {
            return p != null && Math.hypot(p.x() - at.x(), p.z() - at.z()) <= radius;
        }
    }

    private final List<com.larsons.game.story.Quest> quests = new ArrayList<>();

    /** What the level's people ask of her (the level's Quest objects). */
    public List<com.larsons.game.story.Quest> quests() { return quests; }

    /** One of what is in her hand used up. */
    public Item useUpHeld() {
        Item item = player.inventory().takeOne(player.inventory().selected());
        equipSelected();
        return item;
    }

    /** {@code damage} dealt to {@code t} from nowhere - a scroll's lightning - and what its fall leaves, if it falls. */
    public void strike(Target t, double damage) {
        if (t == null || !t.alive()) return;
        t.hurt(damage, null, true);
        combat.say("-" + Combat.format(damage), 0xFFF07A, t.position(), t.height() + 0.2, false);
        if (!t.alive()) felled(t);          // (an animal and one of the folk leave what they leave as they fall)
    }

    /** A locked door opened: it is taken out of her way, and there is nothing left to use. */
    public void unlock(Spot s) {
        buildings.removeIf(b -> b.id() == s.id());
        s.setGone(true);
    }

    /**
     * Her rod, its line and its float, while she fishes: from her hand to the
     * rod's tip, on to the float on the water - the line taut when something
     * is on it - the rod in its colour, the water round the float stirred.
     */
    public record FishingLine(Vec3 hand, Vec3 tip, Vec3 bobber, boolean taut, int rgb, double splash) {}

    private FishingLine fishingLine;

    public FishingLine fishingLine() { return fishingLine; }

    public void setFishingLine(FishingLine line) {
        fishingLine = line;
    }

    /** A place marked for her: an icon standing over it, seen from far off, and its ring on the ground. */
    public record Mark(Vec3 at, String icon, double radius) {}

    private final List<Mark> marks = new ArrayList<>();

    public List<Mark> marks() { return marks; }

    private double revealedUntil = -1;

    /** Mark every foe about for {@code seconds} (the crystal ball). */
    public void reveal(double seconds) {
        revealedUntil = time + seconds;
    }

    public boolean revealed() { return time < revealedUntil; }

    /** Every foe about: the hostile folk, the Vérnees, the beasts that go for her. */
    public List<Target> foes() {
        List<Target> out = new ArrayList<>();
        for (com.larsons.game.actor.Folk f : folks.all()) if (f.alive() && f.hostile()) out.add(f);
        for (com.larsons.game.actor.Serpent sp : folks.serpents()) if (sp.alive()) out.add(sp);
        for (Animal a : animals.live()) if (a.alive() && a.temperament() == Temperament.HOSTILE) out.add(a);
        return out;
    }

    /** What a summoning can call, beside the {@link com.larsons.game.actor.Breed}s and the animals. */
    public static final String VERNEES = "vernees", FLIER = "flier", DUMMY = "dummy";

    /**
     * Call {@code count} of {@code what} - a breed's key, an animal's, {@link
     * #VERNEES} (out of a hole in the sky over the spot), {@link #FLIER}, {@link
     * #DUMMY} - to round {@code at}, facing {@code heading}. Returns how many came.
     */
    public int summon(String what, int count, Vec3 at, double heading, com.larsons.game.actor.Folks.Spec spec) {
        count = Math.max(1, Math.min(40, count));
        Vec3 g = terrain.onGround(at);
        com.larsons.game.actor.Breed breed = com.larsons.game.actor.Breed.byKey(what);
        AnimalKind animal = AnimalKind.byKey(what);
        int came = 0;
        if (VERNEES.equals(what)) {
            com.larsons.game.actor.Serpent.Colour c = spec == null ? null
                    : com.larsons.game.actor.Serpent.Colour.byKey(spec.skin());
            folks.addPortal(new com.larsons.game.actor.Folks.Portal(folks.nextId(), g.add(0, 15, 0), 2.4,
                    0xB060FF, count, 1.6, 0, c, spec == null ? 0 : spec.size()));
            combat.sound("foes/portal", g);
            return count;
        }
        for (int i = 0; i < count; i++) {
            double a = i * 2.399963, r = count == 1 ? 0 : 0.9 + 0.55 * Math.sqrt(i);
            Vec3 p = terrain.onGround(new Vec3(g.x() + Math.cos(a) * r, g.y(), g.z() + Math.sin(a) * r));
            if (breed != null) {
                folks.spawn(breed, p, heading, spec);
            } else if (animal != null) {
                Animal an = animals.add(animal, p, spec != null && !spec.hostile() ? null : Temperament.HOSTILE,
                        place());
                if (an == null) continue;
            } else if (FLIER.equals(what)) {
                addFlier(new FlyingDummy(new Vec3(p.x(), 0, p.z()), heading, 60, combat.tuning().dummyDownSeconds,
                        terrain));
            } else if (DUMMY.equals(what)) {
                addDummy(new Dummy(p, heading, 100, combat.tuning().dummyDownSeconds));
            } else {
                return 0;
            }
            came++;
            combat.say("✦", 0xB060FF, p, 1.2, false);
        }
        if (came > 0) combat.sound("foes/summon", g);
        return came;
    }

    /** How many foes - folk, wild beasts, Vérnees - are still about within {@code area} (null: anywhere). */
    public int foesIn(Area area) {
        int n = 0;
        for (com.larsons.game.actor.Folk f : folks.all()) {
            if (f.alive() && f.hostile() && (area == null || area.holds(f.position()))) n++;
        }
        for (com.larsons.game.actor.Serpent sp : folks.serpents()) {
            if (sp.alive() && (area == null || area.holds(sp.position()))) n++;
        }
        for (com.larsons.game.actor.Folks.Portal p : folks.portals()) {
            if (area == null || area.holds(p.at())) n += p.left();
        }
        for (Animal a : animals.live()) {
            if (a.alive() && a.temperament() == Temperament.HOSTILE && (area == null || area.holds(a.position()))) n++;
        }
        return n;
    }

    /** What a Vérnees' dive did to her, said over her - and heard. */
    private void struckBy(com.larsons.game.actor.Serpent sp, com.larsons.game.actor.Serpent.Outcome o) {
        Vec3 at = player.ground();
        switch (o) {
            case WARNED -> {
                combat.say("!", 0xFFD27A, sp.position(), sp.height() + 0.3, false);
                combat.sound("foes/vernees_hiss", sp.position());
            }
            case STRUCK -> {
                combat.say("Bitten! -" + Combat.format(sp.damage()), HURT_RGB, at, 2.1, false);
                combat.sound("combat/player_hurt", at);
            }
            case BLOCKED -> {
                combat.say("Blocked", 0xC8D2DC, at, 2.1, true);
                combat.sound("combat/block", at);
            }
            case DODGED -> {
                combat.say("Dodged!", 0x9CC4FF, at, 2.1, true);
                combat.sound("combat/dodge", sp.position());
            }
            case PARRIED -> {
                combat.say("Parried!", 0xFFE08A, at, 2.1, true);
                combat.sound("combat/parry", sp.position());
            }
            case FELL -> {
                Vec3 g = sp.position();
                Random r = new Random(Double.doubleToLongBits(g.x() * 31 + g.z() + time));
                spawn(Item.of(Supplies.COINS), new Vec3(g.x(), 0, g.z()), 4 + r.nextInt(8));
                if (r.nextInt(3) == 0) {
                    Item drop = loot.pick(r, Rarity.roll(r));
                    if (drop != null) spawn(drop, new Vec3(g.x() + 0.5, 0, g.z() + 0.3));
                }
            }
            case NONE -> { }
        }
    }

    /** A foe's shot reached her: she may have rolled through it, parried it or blocked it. */
    private void shotAt(Combat.FoeHit hit) {
        Vec3 at = player.ground();
        if (player.invulnerable()) {
            combat.say("Dodged!", 0x9CC4FF, at, 2.1, true);
            combat.sound("combat/dodge", at);
            return;
        }
        Animals.Quarry her = quarry();
        if (her.parrying()) {
            combat.say("Parried!", 0xFFE08A, at, 2.1, true);
            combat.sound("combat/parry", at);
            return;
        }
        double dealt = her.blocking() ? Math.round(hit.damage() / 2) : hit.damage();
        double took = player.hurt(dealt);
        combat.say((her.blocking() ? "Blocked -" : "-") + Combat.format(took), her.blocking() ? 0xC8D2DC : HURT_RGB, at,
                2.1, her.blocking());
        combat.sound(her.blocking() ? "combat/block" : "combat/player_hurt", at);
    }

    /** The solid scenery by {@value #CELL} m square, for the animals to go round quickly. */
    private final Map<Long, List<Scenery.Placed>> solidCells = new java.util.HashMap<>();
    private static final double CELL = 4;

    private static long cell(double x, double z) {
        return ((long) Math.floor(x / CELL) << 32) ^ (Math.floorMod((long) Math.floor(z / CELL), 1L << 32));
    }

    /** Whether a tree's or a rock's trunk stands within {@code radius} of {@code (x, z)}. */
    /**
     * Whether a building stands at {@code (x, z)} taller than {@code y} - a
     * wall, a house, a tower: nobody sees through it (a floor, a rug, a low
     * fence and a candle do not hide anyone).
     */
    public boolean sightBlocked(double x, double z, double y) {
        for (Building b : buildingsNear(x, z, 0)) {
            BuildingProfile p = b.profile();
            if (p.fixture() || p.height() < 1.2) continue;
            if (p.room() != null && p.room().piece() == com.larsons.game.building.Room.Piece.FLOOR) continue;
            if (y < b.position().y() + p.height() && b.blocks(x, z, 0)) return true;
        }
        return false;
    }

    public boolean solidNear(double x, double z, double radius) {
        for (Building b : buildingsNear(x, z, radius)) if (b.blocks(x, z, radius)) return true;
        long cx = (long) Math.floor(x / CELL), cz = (long) Math.floor(z / CELL);
        for (long i = cx - 1; i <= cx + 1; i++) {
            for (long j = cz - 1; j <= cz + 1; j++) {
                List<Scenery.Placed> in = solidCells.get((i << 32) ^ Math.floorMod(j, 1L << 32));
                if (in == null) continue;
                for (Scenery.Placed p : in) {
                    double r = p.radius() + radius, dx = p.x() - x, dz = p.z() - z;
                    if (dx * dx + dz * dz < r * r) return true;
                }
            }
        }
        return false;
    }

    /** The trees, grass and rocks standing in the world ({@link Scenery#NONE} until it is given some). */
    public Scenery scenery() { return scenery; }

    /** Plant {@code scenery} (null: none) - drawn from the next frame, in her way from the next tick. */
    public void setScenery(Scenery scenery) {
        Scenery next = scenery == null ? Scenery.NONE : scenery;
        if (next == this.scenery) return;           // (the demo hands it over every frame)
        this.scenery = next;
        solidCells.clear();
        for (Scenery.Placed p : next.solids()) {
            solidCells.computeIfAbsent(cell(p.x(), p.z()), k -> new ArrayList<>()).add(p);
        }
        List<Scenery.Placed> b = new ArrayList<>();
        for (Scenery.Placed p : next.placed()) if (p.profile().forages()) b.add(p);
        bushes = List.copyOf(b);
        // a bush picked stays picked while the scenery is only hidden (P), and is forgotten with it
        if (next != Scenery.NONE) picked.keySet().retainAll(new java.util.HashSet<>(bushes));
    }

    /**
     * What the ground is made of where - the paths, the forest floor, the
     * gravel, the meadows - as drawn ({@link GroundTexture}); all grass until
     * it is given a map. It stays when the scenery is hidden.
     */
    public GroundMap ground() { return ground; }

    public void setGround(GroundMap g) {
        ground = g == null ? GroundMap.NONE : g;
    }

    /** Its ponds, rivers and streams and its sea ({@link WaterMap}): none until it is given some. */
    public WaterMap water() { return water; }

    public void setWater(WaterMap w) {
        water = w == null ? WaterMap.NONE : w;
        player.setWater(water);
    }

    /** The biome {@code (x, z)} is in (by its ground and its water). */
    public Biome biome(double x, double z) {
        return Biome.at(ground, water, x, z, terrain.height(x, z));
    }

    /** The sky over it and what stands far off round the horizon. */
    public Sky sky() { return sky; }

    /** The level's weather: none, by biome, or fixed. */
    public Weather weather() { return weather; }

    public void setWeather(Weather w) {
        weather = w == null ? Weather.NONE : w;
    }

    /** The weather over her now. */
    public Weather.Now weatherNow() { return weatherNow; }

    /** What is over the level: the open sky, a roof, a cave's rock. */
    public Sky.Cover cover() { return cover; }

    public void setCover(Sky.Cover c) {
        cover = c == null ? Sky.Cover.OPEN : c;
    }

    /** What is over her where she is: a building's roof inside one, else the level's. */
    public Sky.Cover over() { return indoors ? Sky.Cover.INSIDE : cover; }

    /** Whether there is no sky over her: inside a building, under the level's roof or a cave's rock. */
    public boolean roofed() { return over() != Sky.Cover.OPEN; }

    /** The sky as the weather makes it now: greyer, nearer fog, more cloud (as it is, under a roof). */
    public Sky skyNow() {
        return roofed() ? sky : Weathering.sky(sky, weatherNow);
    }

    /** The light now, under the sky as the weather makes it, the lightning's flash in it. */
    public DayCycle.Daylight daylight() {
        DayCycle.Daylight d = clock.daylight(skyNow());
        return roofed() ? d : Weathering.light(d, weatherNow);
    }

    public void setSky(Sky s) {
        sky = s == null ? Sky.DEFAULT : s;
    }

    /** The time of day: what hour it is, and how fast it goes by. */
    public DayCycle clock() { return clock; }

    /** Set the clock to the hour the sky's day starts at. */
    public void startDay() {
        clock.setHour(sky.air().startHour());
    }

    /**
     * A light standing in the world - a lantern, a glowing crystal - that
     * lights what is round it, mostly at night.
     *
     * @param at       where it is
     * @param radius   metres it reaches
     * @param rgb      its colour, {@code 0xRRGGBB}
     * @param strength how bright it is, 0 – 2
     * @param glow     whether it shows as a glowing spot itself (else only its light is seen)
     */
    public record LightSpot(Vec3 at, double radius, int rgb, double strength, boolean glow, boolean flame) {
        public LightSpot(Vec3 at, double radius, int rgb, double strength, boolean glow) {
            this(at, radius, rgb, strength, glow, false);
        }
    }

    /** The lights placed in it ({@code flame}: a candle's or a lantern's - its glow a soft halo round it). */
    public List<LightSpot> lights() { return lights; }

    /**
     * Light rays slanting down at a spot - through a gap in the trees, a
     * window - as the sun is low.
     *
     * @param at       the spot on the ground they light
     * @param count    how many shafts
     * @param spread   metres round the spot they are spread over
     * @param height   metres up they start
     * @param strength how strong they are, 0 – 2
     */
    public record RaySpot(Vec3 at, int count, double spread, double height, double strength) {}

    /** The light rays placed in it (beside those the trees give: {@link LightRays}). */
    public List<RaySpot> raySpots() { return raySpots; }

    /** Keep her within {@code x0 .. x1}, {@code z0 .. z1} from the next tick: a level's edges (null: none). */
    public void setBounds(double x0, double x1, double z0, double z1) {
        bounds = new double[]{x0, x1, z0, z1};
    }

    /** The edges she is kept within, x from, x to, z from, z to - or null. */
    public double[] bounds() { return bounds == null ? null : bounds.clone(); }

    /**
     * Advance the world: the chests' animations ({@code durations}: how long
     * each of a chest's states lasts, from its sheets) and the dummies' - the
     * player kept out of them all (but the dummies while she rolls), and out
     * of the trees and the rocks - and the fighting.
     */
    public void tick(double dt, java.util.function.BiFunction<Chest, Chest.State, Double> durations) {
        time += dt;
        clock.tick(dt, sky.air());
        if (!indoors) weatherBiome = biome(player.ground().x(), player.ground().z());
        weatherNow.tick(dt, time, weather, weatherBiome);
        for (Chest c : chests) {
            c.tick(dt, s -> durations == null ? 0 : durations.apply(c, s));
            player.keepOut(c.position(), Chest.RADIUS);
            int opens = c.opens();
            if (opens != opensSeen.getOrDefault(c, 0)) {
                opensSeen.put(c, opens);
                if (c.store() == null) opened(c);           // (a storage chest throws nothing out)
            }
        }
        for (Store s : List.copyOf(stores)) {
            s.tick(dt);
            if (s.broken()) smash(s);
        }
        showRacks();
        shards.tick(dt, terrain);
        for (Dummy d : dummies) {
            d.tick(dt);
            if (!player.passesThrough()) player.keepOut(d.position(), Dummy.RADIUS);   // (she rolls through them)
        }
        double feet = feet();
        for (Scenery.Placed s : scenery.solids()) {
            if (Math.abs(s.y() - player.ground().y()) < 2) player.keepOut(s.position(), s.radius() + feet);
        }
        keepOutOfBuildings();
        for (FlyingDummy f : fliers) swooped(f, f.tick(dt, terrain, player));
        // the bigger animals are in her way (she rolls through them), as the dummies are - and her horse's
        if (!player.passesThrough()) {
            for (Animal a : animals.live()) {
                if (a.alive() && !a.ridden() && !a.flies() && !a.swimming() && a.radius() >= 0.3
                        && Math.abs(a.position().y() - player.ground().y()) < 1.2) {
                    player.keepOut(a.position(), a.radius() + (player.riding() == Player.Riding.NO ? FEET * 0.5
                            : RIDDEN_RADIUS));
                }
            }
        }
        if (bounds != null) player.keepWithin(bounds[0], bounds[1], bounds[2], bounds[3]);
        stealthClock -= dt;
        if (stealthClock <= 0) {
            stealth = Stealth.of(this);
            stealthClock = 0.2;
        }
        if (stealth.hidden()) lastHidden = time;
        combat.tick(dt, this);
        turnBack();
        happened();
        rideAlong();
        live(dt, player.ground(), player.ground());
        folks.setDay(clock.hour(), weatherNow.stormy());
        for (com.larsons.game.actor.Folks.Event e : folks.tick(dt, quarry(), place())) metFolk(e);
        for (com.larsons.game.actor.Folks.Loosed l : folks.drainLoosed()) {
            combat.foeShot(l.kind(), l.colour(), l.from(), l.to(), l.damage(), l.by());
        }
        for (Combat.FoeHit hit : combat.drainFoeHits()) shotAt(hit);
        if (folks.drainEmerged() > 0) combat.sound("foes/portal", player.ground());
        for (com.larsons.game.actor.Serpent sp : folks.serpents()) struckBy(sp, sp.tick(dt, terrain, player));
        folks.serpents().removeIf(com.larsons.game.actor.Serpent::gone);
        if (!player.passesThrough()) {
            for (com.larsons.game.actor.Folk f : folks.all()) {
                if (f.standing() && Math.abs(f.position().y() - player.ground().y()) < 1.2) {
                    player.keepOut(f.position(), f.radius() + FEET * 0.5);
                }
            }
        }
    }

    /** How far round her she keeps out of things: her feet - riding, her horse's body. */
    private double feet() {
        return player.riding() == Player.Riding.NO ? FEET : RIDDEN_RADIUS;
    }

    // --- riding -------------------------------------------------------------------------

    /** How far round her middle her horse keeps out of the trees, the rocks and the walls, metres. */
    public static final double RIDDEN_RADIUS = 0.5;
    /** How near a horse she must be to saddle it or get on it, metres from its side. */
    public static final double HORSE_REACH = 1.6;

    /** The horse she rides, or is getting on or off; null. */
    public Animal steed() { return steed; }

    /** Where the saddle is on the saddled horse, frame by frame (its sheets' {@code ride.json}). */
    public RideTrack rideTrack() { return rideTrack; }

    public void setRideTrack(RideTrack track) {
        rideTrack = track == null ? RideTrack.standard() : track;
    }

    /** The nearest horse by her that she could do something with (saddled: {@code saddled}), or null. */
    private Animal nearHorse(boolean saddled) {
        if (player.riding() != Player.Riding.NO || indoors) return null;
        Animal best = null;
        double bestD = HORSE_REACH;
        Vec3 me = player.ground();
        for (Animal a : animals.live()) {
            if (a.kind() != AnimalKind.HORSE || a.saddled() != saddled || !a.alive() || a.hostile() || a.ridden()
                    || a.swimming() || Math.abs(a.position().y() - me.y()) > 1.0) {
                continue;
            }
            double d = a.position().horizontalDistance(me) - a.radius();
            if (d < bestD) {
                best = a;
                bestD = d;
            }
        }
        return best;
    }

    /** The saddled horse she could get on from where she stands, or null. */
    public Animal reachableHorse() {
        return nearHorse(true);
    }

    /** A horse by her without a saddle, when she has one to put on it; else null. */
    public Animal saddleable() {
        return saddleSlot() < 0 ? null : nearHorse(false);
    }

    /** The inventory slot her saddle is in, or -1. */
    private int saddleSlot() {
        Inventory inv = player.inventory();
        for (int i = 0; i < inv.size(); i++) {
            Item it = inv.get(i);
            if (it != null && it.def().isTack()) return i;
        }
        return -1;
    }

    /** Saddle {@code horse} with the saddle out of her inventory; false when she has none, or it is no horse. */
    public boolean saddle(Animal horse) {
        int slot = saddleSlot();
        if (horse == null || horse.kind() != AnimalKind.HORSE || horse.saddled() || slot < 0) return false;
        player.inventory().takeOne(slot);
        equipSelected();
        horse.setSaddled(true);
        combat.sound(SoundKeys.world("saddle_on"), horse.position());
        return true;
    }

    /** Get up on the saddled {@code horse}: it stands still for her, and goes where she steers it once she is up. */
    public boolean mount(Animal horse) {
        if (horse == null || !horse.saddled() || horse.ridden() || !horse.alive()) return false;
        if (!player.mount(horse.position(), horse.heading(), rideTrack)) return false;
        steed = horse;
        horse.setRidden(true);
        combat.sound(SoundKeys.world("mount"), horse.position());
        return true;
    }

    /**
     * Get down off the horse, beside it - its left (the way she got on), else
     * its right, else behind it: wherever there is ground to stand on.
     * False when she sits none, or there is nowhere to get down.
     */
    public boolean dismount() {
        if (steed == null || !player.mounted()) return false;
        Vec3 at = steed.position();
        Vec3 f = SpriteView.headingVector(steed.heading());
        Vec3 left = new Vec3(f.z(), 0, -f.x());
        double side = steed.radius() + 0.55;
        for (Vec3 d : new Vec3[]{left, left.scale(-1), f.scale(-1.6), f.scale(1.6)}) {
            Vec3 to = terrain.onGround(at.add(d.scale(side)));
            if (standable(to, at.y())) {
                player.dismount(to);
                combat.sound(SoundKeys.world("dismount"), to);
                return true;
            }
        }
        return false;
    }

    /** Whether she could stand at {@code to}, getting down from a horse standing at height {@code from}. */
    private boolean standable(Vec3 to, double from) {
        if (Math.abs(to.y() - from) > 0.9 || terrain.slopeUnder(to.x(), to.z()) > Player.SLIDE) return false;
        if (water.depth(to.x(), to.z(), to.y()) > WaterMap.TOO_DEEP || solidNear(to.x(), to.z(), FEET + 0.1)) {
            return false;
        }
        for (Building b : buildings) if (b.pushOut(to.x(), to.z(), FEET + 0.05) != null) return false;
        if (bounds != null && (to.x() < bounds[0] || to.x() > bounds[1] || to.z() < bounds[2] || to.z() > bounds[3])) {
            return false;
        }
        return true;
    }

    /**
     * The horse goes where she has steered it, playing what she has it play,
     * on her clock; it stands still while she gets on or off; and when she
     * is off it - got down, or put somewhere else - it is hers no more.
     */
    private void rideAlong() {
        if (steed == null) return;
        boolean there = animals.live().contains(steed);
        if (player.riding() == Player.Riding.NO || !there) {
            if (there) steed.setRidden(false);
            else player.unseat();                   // the horse is gone (a new level): she is on her feet
            steed = null;
            return;
        }
        if (player.mounted()) {
            steed.carry(player.ground(), player.heading(), player.horseSheet(), player.horseTime(),
                    player.horseRate(), player.velocity());
        }
    }

    /** The tallest piece of furniture she stands on and climbs up onto (a table, a crate, a barrel), metres. */
    public static final double CLIMBABLE = 2.0;

    /** Whether she can stand on {@code b} and climb up onto it: a piece of furniture no taller than {@link #CLIMBABLE}. */
    static boolean climbable(Building b) {
        return b.profile().furniture() && b.profile().height() <= CLIMBABLE;
    }

    /** The tops of the furniture, to stand on and climb onto. */
    private final Player.Tops tops = new Player.Tops() {
        @Override
        public double top(double x, double z, double below) {
            double best = Double.NaN;
            for (Building b : buildings) {
                // a building's porch, the step at its door, its stairs: walked up onto
                double st = b.stepTop(x, z, below);
                if (!Double.isNaN(st) && !(st <= best)) best = st;
                if (!climbable(b)) continue;
                double t = b.position().y() + b.profile().height();
                if (t > below || t <= best) continue;
                if (b.blocks(x, z, 1e-6)) best = t;
            }
            return best;
        }

        @Override
        public double[] ahead(double x, double z, double dx, double dz, double reach, double feetY, double lo,
                              double hi) {
            double[] best = null;
            double nearest = Double.MAX_VALUE;
            for (Building b : buildings) {
                if (!climbable(b)) continue;
                double t = b.position().y() + b.profile().height(), h = t - feetY;
                if (h < lo || h > hi) continue;
                double r = b.profile().reach() + reach;
                if (Math.hypot(b.position().x() - x, b.position().z() - z) > r) continue;
                for (double d = 0; d <= reach && d < nearest; d += 0.04) {
                    double px = x + dx * d, pz = z + dz * d;
                    if (!b.blocks(px, pz, 1e-6)) continue;
                    // room on it to stand, a step in from its edge
                    if (b.blocks(px + dx * 0.3, pz + dz * 0.3, 1e-6)) {
                        nearest = d;
                        best = new double[]{px, pz, t};
                    }
                    break;
                }
            }
            return best;
        }
    };

    /** She walks round the buildings - unless she is up above one (on a cliff over it, flying off one). */
    private void keepOutOfBuildings() {
        Vec3 at = player.ground();
        double feet = at.y() + player.height();
        for (Building b : playerWalls) {
            if (feet > b.position().y() + b.profile().height() + 1.5 || feet < b.position().y() - 2) continue;
            Vec3 to = b.pushOut(at.x(), at.z(), feet() + 0.05);
            if (to != null) {
                player.shoveTo(to.x(), to.z());
                at = player.ground();
            }
        }
        for (Building b : buildings) {
            if (feet > b.position().y() + b.profile().height() - 0.3 || feet < b.position().y() - 2) continue;
            Vec3 to = b.pushOut(at.x(), at.z(), feet() + 0.05);
            if (to != null) {
                player.shoveTo(to.x(), to.z());
                at = player.ground();
            }
        }
    }

    /**
     * The life in it: the little creatures round {@code focus} (shy of {@code
     * shy}, her feet, when she is there), the picked bushes growing back.
     */
    private void live(double dt, Vec3 focus, Vec3 shy) {
        picked.values().removeIf(until -> until <= time);
        double day = DayCycle.of(sky, clock.hour()).day();
        Critters.Voice voice = (key, at, volume) -> combat.sound(key, at, volume);
        critters.tick(dt, time, day, focus, shy, place(), voice);
        for (Animals.Event e : animals.tick(dt, time, day, focus, shy == null ? null : quarry(), place(), voice)) {
            met(e);
        }
    }

    /** She, as the animals see her: where she is, whether she rolls, parries or blocks, what a blow takes. */
    private Animals.Quarry quarry() {
        return new Animals.Quarry() {
            @Override
            public Vec3 feet() { return player.feet(); }

            @Override
            public boolean dodging() { return player.invulnerable(); }

            @Override
            public boolean parrying() { return player.action() != null && player.action().key().contains("parry"); }

            @Override
            public boolean blocking() {
                return player.stance().block() != null && player.state() == player.stance().block();
            }

            @Override
            public double hurt(double damage) { return player.hurt(damage); }

            @Override
            public boolean down() { return player.health() <= 0; }

            @Override
            public double conspicuous() { return stealth.score(); }
        };
    }

    /** What an animal did to her (or had done to it), said over her and heard. */
    private void met(Animals.Event e) {
        Animal a = e.animal();
        Vec3 at = player.ground();
        switch (e.outcome()) {
            case WARNED -> combat.say("!", 0xFFD27A, a.position(), a.height() + 0.25, false);
            case STRUCK -> {
                double thorns = player.buffs().plus(Buffs.Kind.THORNS);
                if (thorns > 0 && e.damage() > 0 && a.alive()) a.hurt(e.damage() * thorns, player.ground(), true);
                combat.say(struck(a.kind()) + " -" + Combat.format(e.damage()), HURT_RGB, at, 2.1, false);
                combat.sound("combat/player_hurt", at);
            }
            case BLOCKED -> {
                combat.say("Blocked", 0xC8D2DC, at, 2.1, true);
                combat.sound("combat/block", at);
            }
            case DODGED -> {
                combat.say("Dodged!", 0x9CC4FF, at, 2.1, true);
                combat.sound("combat/dodge", a.position());
            }
            case PARRIED -> {
                combat.say("Parried!", 0xFFE08A, at, 2.1, true);
                combat.sound("combat/parry", a.position());
            }
            case RAN_OFF -> combat.say("The " + a.kind().singular().toLowerCase(java.util.Locale.ROOT)
                    + " runs off", 0xE8D8A8, a.position(), a.height() + 0.4, true);
            case DOWN -> {
                combat.say("The " + a.kind().singular().toLowerCase(java.util.Locale.ROOT) + " falls", 0xE8D8A8,
                        a.position(), a.height() + 0.4, true);
                hunted(a);
            }
        }
    }

    /**
     * {@code t} felled by her blow: experience for it ({@link
     * com.larsons.game.cards.Hero}) and, for a dummy or a flier, a few coins
     * where it stood (an animal leaves what it leaves when it falls: {@link #hunted}).
     */
    public void felled(com.larsons.game.combat.Target t) {
        if (t instanceof Animal || t instanceof com.larsons.game.actor.Folk) return;
        if (t instanceof com.larsons.game.actor.Serpent sp) {
            kills.add(new Kill(com.larsons.game.actor.Serpent.SPRITES, null, sp.position(), false));
            earn(com.larsons.game.actor.Serpent.XP * sp.size());
            combat.sound("foes/vernees_fall", sp.position());
            return;
        }
        kills.add(new Kill(t instanceof FlyingDummy ? "flying_dummy" : "dummy", null, t.position(), false));
        int xp = t instanceof FlyingDummy ? com.larsons.game.cards.Hero.XP_FLIER : com.larsons.game.cards.Hero.XP_FELLED;
        earn(xp);
        int coins = 2 + new Random(Double.doubleToLongBits(t.position().x() * 31 + t.position().z() + time))
                .nextInt(5);
        Vec3 at = t.position();
        spawn(Item.of(Supplies.COINS), new Vec3(at.x(), 0, at.z()), coins);
    }

    /** {@code xp} experience earned; a level up said over her. */
    public void earn(double xp) {
        if (player.hero().gain(xp, player.buffs())) {
            combat.say("Level " + player.hero().level() + "! A perk point", 0xFFD27A, player.ground(), 2.4, false);
            combat.sound("ui/level_up", player.ground());
        }
    }

    /** What {@code a}, hunted down, leaves where it fell: its meat, its hide, its feathers - in heaps, round it. */
    void hunted(Animal a) {
        earn(com.larsons.game.cards.Hero.XP_HUNTED);
        kills.add(new Kill(a.kind().key(), null, a.position(), false));
        java.util.Random r = new java.util.Random(Double.doubleToLongBits(a.position().x() * 31 + a.position().z()
                + time));
        int i = 0;
        for (com.larsons.game.ambient.Hunting.Drop d : com.larsons.game.ambient.Hunting.drops(a.kind())) {
            int n = d.roll(r);
            ItemDef def = Hunted.byId(d.item());
            if (n <= 0 || def == null) continue;
            double ang = a.heading() + Math.PI / 2 + i * 2.1, reach = a.radius() * 0.6 + 0.25;
            double x = a.position().x() + Math.cos(ang) * reach, z = a.position().z() + Math.sin(ang) * reach;
            if (!lootFits(x, z)) {
                x = a.position().x();
                z = a.position().z();
            }
            spawn(Item.of(def), new Vec3(x, 0, z), n);
            i++;
        }
    }

    /** What an animal's blow is called, over her: "Pecked!", "Stung!". */
    static String struck(AnimalKind k) {
        return switch (k) {
            case GOOSE, DUCK, ROBIN, BLUE_JAY, CARDINAL, CROW, OWL, PHEASANT, MACAW, TOUCAN, COCKATOO, FLAMINGO,
                 PEACOCK -> "Pecked!";
            case SWAN -> "Battered!";
            case BEE -> "Stung!";
            case BEAR -> "Mauled!";
            case BOAR -> "Gored!";
            case BUFFALO -> "Trampled!";
            case EAGLE -> "Clawed!";
            case HORSE, COW, SHEEP, DEER -> "Kicked!";
            case CAT -> "Scratched!";
            case DOG, FOX, PIG, RABBIT, SQUIRREL, IGUANA -> "Bitten!";
            case BUTTERFLY, HUMMINGBIRD -> "Tickled!";
        };
    }

    /** What the little creatures and the animals need to know of the place: the ground, the water, the biomes, the trees. */
    /** The place as the folk know it (its ground, its water, what is solid in it), for finding them room. */
    public Animals.Place folkPlace() { return place(); }

    private Animals.Place place() {
        return new Animals.Place() {
            @Override
            public boolean blocked(double x, double z, double radius) {
                return solidNear(x, z, radius);
            }

            @Override
            public double[] bounds() {
                return bounds;
            }

            @Override
            public boolean sightBlocked(double x, double z, double y) {
                return World.this.sightBlocked(x, z, y);
            }

            @Override
            public double ground(double x, double z) {
                return terrain.height(x, z);
            }

            @Override
            public double water(double x, double z, double groundY) {
                return water.depth(x, z, groundY);
            }

            @Override
            public com.larsons.game.world.Biome biome(double x, double z) {
                return World.this.biome(x, z);
            }
        };
    }

    /** What a flier's dive did to her, said over her - and heard. */
    private void swooped(FlyingDummy f, FlyingDummy.Outcome o) {
        Vec3 at = player.ground();
        switch (o) {
            case WARNED -> combat.say("!", 0xFFD27A, f.position(), FlyingDummy.HEIGHT + 0.3, false);
            case STRUCK -> {
                combat.say("Swooped! -" + Combat.format(FlyingDummy.SWOOP_DAMAGE), HURT_RGB, at, 2.1, false);
                combat.sound("combat/player_hurt", at);
            }
            case BLOCKED -> {
                combat.say("Blocked", 0xC8D2DC, at, 2.1, true);
                combat.sound("combat/block", at);
            }
            case DODGED -> {
                combat.say("Dodged!", 0x9CC4FF, at, 2.1, true);
                combat.sound("combat/dodge", f.position());
            }
            case PARRIED -> {
                combat.say("Parried!", 0xFFE08A, at, 2.1, true);
                combat.sound("combat/parry", f.position());
            }
            case NONE -> { }
        }
    }

    /** The red of her own hurts floating up over her. */
    public static final int HURT_RGB = 0xFF6A5A;

    /** A landing from less high than this (metres: a step down a slope) makes no sound. */
    public static final double LAND_SOUND = 0.25;

    /** What happened to her this frame, said over her and heard; back at the start when her health is gone. */
    private void happened() {
        Vec3 at = player.ground();
        String body = player.wardrobe().get(Slot.BODY);
        for (Player.Event e : player.drainEvents()) {
            switch (e.kind()) {
                case FALL -> {
                    combat.say("Hard landing -" + Combat.format(e.amount()), HURT_RGB, at, 2.1, false);
                    combat.sound(SoundKeys.player(body, "hard_landing"), at);
                }
                case LANDED -> {
                    if (e.amount() >= LAND_SOUND) {
                        combat.sound(SoundKeys.player(body, "land"), at, Math.min(1, 0.45 + e.amount() * 0.35));
                    }
                }
                case SURFACED -> {
                    if (e.amount() > 4) combat.sound(SoundKeys.player(body, "gasp"), at);
                    combat.sound("player/surface", at);
                }
                case LEFT_WATER -> combat.sound("player/wade_out", at);
                case PLUNGED -> {
                    combat.sound("combat/plunge", at);
                    combat.slam(at, wielded());
                }
                case ROLLED -> {
                    combat.say(e.amount() > 0 ? "Rolled out of the fall" : "Roll", 0x9CC4FF, at, 2.1, true);
                    combat.sound(SoundKeys.player(body, "landing_roll"), at);
                }
                case CLIMBED, STEPPED_DOWN -> combat.sound(SoundKeys.player(body, "climb_top"), at);
                case LET_GO -> {
                    combat.sound(SoundKeys.player(body, "let_go"), at);
                    if (e.amount() > 0) {
                        combat.say("Out of stamina", 0xFFD27A, at, 2.1, true);
                        combat.sound(SoundKeys.player(body, "out_of_breath"), at);
                    }
                }
                case KNOCKED_OUT -> {
                    combat.say("Knocked out", HURT_RGB, at, 2.1, false);
                    combat.sound(SoundKeys.player(body, "knocked_out"), at);
                    knockOuts++;
                    player.respawn(spawnPoint);
                    combat.lock(null);
                }
                case HURT -> combat.sound(SoundKeys.player(body, "hurt"), at);
            }
        }
    }

    /**
     * A chest's lid has started up: if nothing it gave last time still lies
     * about, it gives a new lot - each item thrown out of the lid in turn to
     * a spot in a fan round its front.
     */
    private void opened(Chest c) {
        if (c.looted()) {
            // (given once: what it held is hers, or lies about still)
            combat.say("Empty", 0xC8BCA8, c.position(), 1.2, true);
            return;
        }
        c.setLooted(true);
        if (onLooted != null) onLooted.accept(c);
        earn(com.larsons.game.cards.Hero.XP_CHEST);
        List<Item> lot = new ArrayList<>(loot.roll(random));
        List<Integer> amounts = new ArrayList<>();
        for (int i = 0; i < lot.size(); i++) amounts.add(1);
        // and coins, and some supplies: ammunition, now and then an upgrade ingot or a reshuffle scroll
        Random extra = new Random(Double.doubleToLongBits(c.position().x() * 131 + c.position().z() * 7 + time));
        lot.add(Item.of(Supplies.COINS));
        amounts.add(5 + extra.nextInt(16));
        double roll = extra.nextDouble();
        ItemDef supply = roll < 0.2 ? Supplies.UPGRADE_INGOT : roll < 0.3 ? Supplies.RESHUFFLE_SCROLL
                : Supplies.AMMO.get(extra.nextInt(Supplies.AMMO.size()));
        lot.add(Item.of(supply));
        amounts.add(Supplies.isAmmo(supply) ? 2 + extra.nextInt(3) : 1);
        Vec3 lid = c.position().add(0, 0.55, 0);
        int n = lot.size();
        for (int i = 0; i < n; i++) {
            // spread across the chest's front, 140 degrees, nearer and farther by turns
            double spread = Math.toRadians(140);
            double a = c.heading() + (n == 1 ? 0 : -spread / 2 + spread * i / (n - 1))
                    + (random.nextDouble() - 0.5) * 0.15;
            double r = 1.35 + (i % 2) * 0.55 + random.nextDouble() * 0.2;
            Vec3 land = terrain.onGround(landing(c, a, r));
            GroundItem g = new GroundItem(lot.get(i), land, items.size() * 1.7 + i, c, lid,
                    time + FIRST_THROW + NEXT_THROW * i);
            g.amount = amounts.get(i);
            items.add(g);
        }
    }

    /** How near a wall (or anything solid) a thrown item may come down, metres. */
    static final double LOOT_CLEAR = 0.35;

    /**
     * Where an item thrown out of chest {@code c} toward {@code heading},
     * {@code r} metres, comes down: there, or - a wall, a building, a tree or
     * a rock in the way - short of it, on the chest's side, or failing that
     * along another way out of the chest. Never through a wall: inside a room
     * the loot stays in the room.
     */
    Vec3 landing(Chest c, double heading, double r) {
        Vec3 from = c.position();
        double best = -1, bestHeading = heading;
        for (double turn : new double[]{0, 0.6, -0.6, 1.2, -1.2, 1.9, -1.9, Math.PI}) {
            Vec3 dir = SpriteView.headingVector(heading + turn);
            double clear = 0;
            for (double step = Chest.RADIUS; ; step = Math.min(r, step + 0.1)) {
                double d = step, x = from.x() + dir.x() * d, z = from.z() + dir.z() * d;
                if (!lootFits(x, z) || Math.abs(terrain.height(x, z) - from.y()) > 1.2 && d > Chest.RADIUS + 0.4) break;
                clear = d;
                if (d >= r) break;
            }
            if (clear >= r - 1e-6) {
                best = r;
                bestHeading = heading + turn;
                break;
            }
            if (clear > best) {
                best = clear;
                bestHeading = heading + turn;
            }
        }
        double d = Math.max(Chest.RADIUS, best);
        return from.add(SpriteView.headingVector(bestHeading).scale(d));
    }

    /** Whether loot can come down at {@code (x, z)}: inside the level, clear of walls, buildings, trunks and rocks. */
    private boolean lootFits(double x, double z) {
        if (bounds != null && (x < bounds[0] || x > bounds[1] || z < bounds[2] || z > bounds[3])) return false;
        for (Building b : buildings) {
            if (b.profile().room() != null && b.profile().room().piece() == com.larsons.game.building.Room.Piece.FLOOR) {
                continue;
            }
            if (b.blocks(x, z, LOOT_CLEAR)) return false;
        }
        return !solidNear(x, z, LOOT_CLEAR * 0.5);
    }

    public void tick(double dt) {
        tick(dt, null);
    }

    /**
     * Advance the world's clock - the wind in the scenery, the chests' and
     * the dummies' idle loops - and nothing else: no fighting, nothing in
     * her way, the fliers still. The level editor's view of a level.
     */
    public void idle(double dt) {
        time += dt;
        for (Chest c : chests) c.tick(dt, s -> 0);
        for (Dummy d : dummies) d.tick(dt);
        live(dt, focus != null ? focus : player.ground(), null);
        folks.tick(dt, null, place());
    }

    // --- life: the little creatures, the foraging bushes, eating ------------------

    /** The little creatures living in it: crickets, frogs, fireflies, dragonflies. */
    public Critters critters() { return critters; }

    /** The level's say over its life: how thick each kind lives, how fast the bushes grow back. */
    public Life life() { return critters.life(); }

    public void setLife(Life life) {
        critters.setLife(life);
        animals.setLife(life);
    }

    /** The animals living in it: those that come by, and the herds placed in it. */
    public Animals animals() { return animals; }

    /** Put an animal in at {@code at}, now: of its own nature, or {@code temperament}'s (a script, a test). */
    public Animal addAnimal(AnimalKind kind, Vec3 at, com.larsons.game.ambient.Temperament temperament) {
        return animals.add(kind, at, temperament, place());
    }

    /** Where the little creatures live round when nobody is playing - the editor's view (null: round her). */
    public void setFocus(Vec3 at) {
        focus = at;
    }

    /** How near she must be to a foraging bush to pick it, metres. */
    public static final double FORAGE_REACH = 1.4;

    /** The foraging bushes standing in it. */
    public List<Scenery.Placed> bushes() { return bushes; }

    /** Whether {@code bush} has been picked and has not grown its pickings back yet. */
    public boolean picked(Scenery.Placed bush) {
        Double until = picked.get(bush);
        return until != null && until > time;
    }

    /** Seconds until {@code bush} has its pickings again (0: it has them). */
    public double regrowsIn(Scenery.Placed bush) {
        Double until = picked.get(bush);
        return until == null ? 0 : Math.max(0, until - time);
    }

    /** The nearest bush within her reach with something on it, or null. */
    public Scenery.Placed reachableBush() {
        Scenery.Placed best = null;
        double bestD = FORAGE_REACH;
        Vec3 at = player.ground();
        for (Scenery.Placed b : bushes) {
            if (Math.abs(b.y() - at.y()) > 1.5 || picked(b)) continue;
            double d = Math.hypot(b.x() - at.x(), b.z() - at.z()) - 0.3 * b.profile().frameWorldSize() / 1.2;
            if (d <= bestD) {
                best = b;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * Pick {@code bush}: what is on it - its picking, between as few and as
     * many as its profile says, times the level's yield - into her inventory
     * (onto the stacks there first, out of the hotbar's way), and it is bare
     * until it grows them back. Returns what she got (nothing from a bush
     * already picked); what had no room is put on the ground at her feet.
     */
    public List<Item> pick(Scenery.Placed bush) {
        if (bush == null || !bush.profile().forages() || picked(bush)) return List.of();
        SceneryProfile.Forage f = bush.profile().forage();
        ItemDef def = ItemDef.byId(f.item());
        if (def == null) return List.of();
        int n = f.least() + (f.most() > f.least() ? random.nextInt(f.most() - f.least() + 1) : 0);
        n = Math.max(1, (int) Math.round(n * life().yield()));
        List<Item> got = new ArrayList<>();
        Item item = Item.of(def);
        int left = 0;
        for (int i = 0; i < n; i++) {
            if (player.inventory().stow(item) >= 0) got.add(item);
            else left++;
        }
        if (left > 0) spawn(item, player.ground(), left);
        picked.put(bush, time + f.regrow() * life().regrow());
        return got;
    }

    /** What eating something gave back: health, stamina, mana (all 0: she had no need of it, and did not eat it). */
    public record Eaten(Item item, double health, double stamina, double mana) {
        public boolean ate() {
            return health > 0 || stamina > 0 || mana > 0;
        }
    }

    /**
     * Eat one of what is in inventory slot {@code slot}: what it gives back
     * that she has spent - her health, her stamina, her mana - and one fewer
     * in the slot. Nothing is eaten when she is full of all it would give.
     * Null when the slot holds nothing to eat.
     */
    public Eaten eat(int slot) {
        Item item = player.inventory().get(slot);
        if (item == null || !item.def().edible()) return null;
        Food f = item.def().food();
        double h = Math.min(f.health(), Player.MAX_HEALTH - player.health());
        double s = Math.min(f.stamina(), Player.MAX_STAMINA - player.stamina());
        double m = Math.min(f.mana(), combat.maxMana() - combat.mana());
        Eaten e = new Eaten(item, Math.max(0, h), Math.max(0, s), Math.max(0, m));
        if (!e.ate()) return e;
        player.inventory().takeOne(slot);
        if (player.health() > 0) player.setHealth(player.health() + e.health());
        player.setStamina(player.stamina() + e.stamina());
        combat.setMana(combat.mana() + e.mana());
        equipSelected();
        return e;
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

    /** How near the spot before a door she must be to go through it, metres. */
    public static final double DOOR_REACH = 1.4;

    /**
     * The nearest door within her reach: a building's with an inside to go
     * into, the door out of a building she is in, a staircase's foot or a
     * stairwell's top - or null.
     */
    public Building reachableDoor() {
        Building best = null;
        double bestD = DOOR_REACH;
        Vec3 me = player.ground();
        for (Building b : buildings) {
            com.larsons.game.building.Room.Spec room = b.profile().room();
            boolean opens = room == null ? b.profile().interior()
                    : room.piece() == com.larsons.game.building.Room.Piece.EXIT
                    || room.piece() == com.larsons.game.building.Room.Piece.STAIRS;
            Vec3 d = opens ? b.door() : null;
            if (d == null || Math.abs(player.feet().y() - b.position().y()) > 1.5) continue;
            double dist = Math.hypot(d.x() - me.x(), d.z() - me.z());
            if (dist < bestD) {
                best = b;
                bestD = dist;
            }
        }
        return best;
    }

    /** How near a way through ({@link Building.Passage}) she is to use it, metres from it; a hidden one, nearer. */
    public static final double PASSAGE_REACH = 0.9, HIDDEN_REACH = 0.45;

    /** The way through (a building made a {@link Building.Passage}) she stands at - the nearest - or null. */
    public Building reachablePassage() {
        Building best = null;
        double bestD = Double.MAX_VALUE;
        Vec3 me = player.ground();
        for (Building b : buildings) {
            Building.Passage p = b.passage();
            if (p == null || Math.abs(player.feet().y() - b.position().y()) > 1.5) continue;
            double reach = p.hidden() ? HIDDEN_REACH : PASSAGE_REACH;
            if (!b.blocks(me.x(), me.z(), feet() + reach)) continue;
            double d = b.position().horizontalDistance(me);
            if (d < bestD) {
                best = b;
                bestD = d;
            }
        }
        return best;
    }

    /** How near a wardrobe she is to open it, metres from it. */
    public static final double WARDROBE_REACH = 0.8;

    /** A wardrobe (the furniture) she stands at - what she wears is changed at it - or null. */
    public Building reachableWardrobe() {
        Vec3 me = player.ground();
        Building best = null;
        double bestD = Double.MAX_VALUE;
        for (Building b : buildings) {
            if (!isWardrobe(b.key()) || b.passage() != null) continue;
            if (Math.abs(player.feet().y() - b.position().y()) > 1.5) continue;
            if (!b.blocks(me.x(), me.z(), feet() + WARDROBE_REACH)) continue;
            double d = b.position().horizontalDistance(me);
            if (d < bestD) {
                best = b;
                bestD = d;
            }
        }
        return best;
    }

    /** Whether a building of kind {@code key} is one she changes her clothes at (a wardrobe, a dressing screen). */
    public static boolean isWardrobe(String key) {
        return key != null && (key.equals("wardrobe") || key.startsWith("wardrobe_") || key.equals("dressing_screen")
                || key.equals("standing_mirror"));
    }

    /** Put {@code def}, as it is found lying about ({@link Item#of(ItemDef)}), on the ground at {@code at}. */
    public void spawn(ItemDef def, Vec3 at) {
        spawn(Item.of(def), at);
    }

    public void spawn(Item item, Vec3 at) {
        spawn(item, at, 1);
    }

    /** Put {@code amount} of {@code item} - a heap of berries is one - on the ground at {@code at}. */
    public GroundItem spawn(Item item, Vec3 at, int amount) {
        GroundItem g = new GroundItem(item, terrain.onGround(at), items.size() * 1.7);
        g.amount = Math.max(1, amount);
        items.add(g);
        return g;
    }

    /**
     * Set {@code item} out for show with its foot at {@code at} - on the
     * ground, a table, a counter, wherever that is - turned to {@code
     * heading}: it stands still, without glow, and is never picked up.
     */
    public GroundItem show(Item item, Vec3 at, double heading) {
        GroundItem g = new GroundItem(item, at, 0);
        g.shown = true;
        g.heading = heading;
        items.add(g);
        return g;
    }

    /** Where a weapon rack's pegs are, across it (its own metres), and how far in front of its middle they stand. */
    static final double[] RACK_PEGS = {-0.45, -0.15, 0.15, 0.45};
    static final double RACK_FRONT = -0.1, RACK_FOOT = 0.1, RACK_TOP = 1.3;

    /**
     * What is on each weapon rack, stood up on its pegs to be seen: set out
     * again whenever what it holds changes (she hangs a sword up, takes a
     * bow down), gone when the rack is.
     */
    public void showRacks() {
        for (Store s : stores) {
            if (s.kind() != Store.Kind.RACK) continue;
            StringBuilder sig = new StringBuilder();
            for (int i = 0; i < s.items().size(); i++) sig.append(s.items().get(i)).append(';');
            if (sig.toString().equals(s.shownAs)) continue;
            s.shownAs = sig.toString();
            items.removeIf(g -> g.rack == s);
            Building rack = null;
            for (Building b : buildings) if (b.id() == s.id() && Store.Kind.ofBuilding(b.key()) != null) rack = b;
            if (rack == null) continue;
            int peg = 0;
            for (int i = 0; i < s.items().size() && peg < RACK_PEGS.length; i++) {
                Item it = s.items().get(i);
                if (it == null) continue;
                double length = Math.min(RACK_TOP - RACK_FOOT, it.def().worldSize());
                Vec3 foot = rack.toWorld(RACK_PEGS[peg++], RACK_FRONT);
                GroundItem g = show(it, new Vec3(foot.x(), rack.position().y() + RACK_FOOT + length / 2, foot.z()),
                        rack.heading() + Math.PI / 2);
                g.rack = s;
            }
        }
        items.removeIf(g -> g.rack != null && !stores.contains(g.rack));
    }

    /** The nearest item within reach of the player that has landed, or {@code null}. */
    public GroundItem reachable() {
        GroundItem best = null;
        double bestD = PICKUP_RANGE;
        for (GroundItem g : items) {
            if (g.shown || !g.landed(time) || Math.abs(g.position.y() - player.ground().y()) > 1.5) continue;
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
        if (item.def == Supplies.COINS) {
            // coins go into her purse, never a slot (a card may make them more)
            player.addCoins((int) Math.round(item.amount * player.buffs().times(Buffs.Kind.COINS)));
            items.remove(item);
            return true;
        }
        if (item.def.isFood()) {
            // a heap of pickings: as many as there is room for, onto their stacks; the rest stays
            int took = 0;
            while (item.amount > 0 && player.inventory().stow(item.item) >= 0) {
                item.amount--;
                took++;
            }
            if (item.amount <= 0) items.remove(item);
            return took > 0;
        }
        boolean aside = item.def.isWearable() || item.def.isTack();       // not for her hands
        int slot = aside ? player.inventory().stow(item.item) : player.inventory().add(item.item);
        if (slot < 0) return false;
        items.remove(item);
        if (item.def.inHand()) {
            // a left-handed character takes it in the other hand
            Slot hand = player.wardrobe().hand() == Wardrobe.Hand.LEFT ? item.def.carrySlot().twin()
                    : item.def.carrySlot();
            player.wardrobe().set(hand, item.def.id());
        }
        if (Inventory.isHotbar(slot) && !aside) select(slot);
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
        int amount = player.inventory().amount(slot);
        player.inventory().take(slot);
        return dropItem(item, amount);
    }

    /**
     * Put down an item she has already taken out of her inventory (one held
     * on the pointer in the inventory screen): on the ground before her, out
     * of her hand if it is the worn sword and she has no other, and off her
     * if it is a wearable she owns no other of ({@link Wearables#takeOff}).
     * Returns it, or null for no item.
     */
    public Item dropItem(Item item) {
        return dropItem(item, 1);
    }

    /** {@link #dropItem(Item)}, {@code amount} of it - a stack of pickings - lying as one heap. */
    public Item dropItem(Item item, int amount) {
        if (item == null) return null;
        ItemDef def = item.def();
        if (def.inHand() && !player.inventory().contains(def)) {
            for (Slot hand : new Slot[]{def.carrySlot(), def.carrySlot().twin()}) {
                if (player.wardrobe().wearing(hand, def.id())) player.wardrobe().clear(hand);
            }
        }
        Wearables.takeOff(player.wardrobe(), player.inventory(), def);
        equipSelected();
        spawnAhead(item, amount);
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
     * weapons) and what the chests gave - and fill the chests again, so a
     * chest opened again gives a new lot (the debug tools'). Returns how many
     * there were.
     */
    public int clearGround() {
        int n = items.size();
        items.clear();
        for (Chest c : chests) c.setLooted(false);      // (the debug tool's: every chest full again)
        return n;
    }

    /** Told of each chest as it gives its loot (a playthrough writes it down), or null. */
    private java.util.function.Consumer<Chest> onLooted;

    public void onLooted(java.util.function.Consumer<Chest> c) {
        onLooted = c;
    }

    private void spawnAhead(Item item, int amount) {
        Vec3 ahead = SpriteView.headingVector(player.heading()).scale(1.1);
        spawn(item, player.ground().add(ahead), amount);
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
