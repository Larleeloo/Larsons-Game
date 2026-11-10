package com.larsons.game.tools;

import com.larsons.game.ambient.AnimalKind;
import com.larsons.game.ambient.CritterKind;
import com.larsons.game.level.HeightField;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelIO;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.world.Atmosphere;
import com.larsons.game.world.Item;
import com.larsons.game.world.Sky;
import com.larsons.game.world.WaterMap;
import com.larsons.game.world.Weather;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * A level being laid out in code, as {@link BrassVale} lays out the first
 * lands: the ground rolled, raised and levelled, painted and flooded; then
 * what stands on it - buildings (the ground levelled under each), scenery
 * scattered clear of the roads, the water and whatever was put down first,
 * people, quests, foes, herds, lights. Saved, it is a level as Creative Mode
 * saves one, and opens there to be changed.
 *
 * <p>East is +x, south +z; a heading is in degrees, 0 facing north (-z), 180
 * south.
 */
final class Land {

    final Level level;
    final String id;
    final Random rnd;
    final double half;
    /** How much of a levelling's rim eases into the ground round it: the rest of it is level. */
    private static final double FEATHER = 0.4;
    /** Nothing is scattered on ground higher than this (a mine's rock, over its galleries). */
    double onlyBelow = Double.MAX_VALUE;
    /** Discs (x, z, radius) nothing is scattered into: a road, a house, a pool. */
    private final List<double[]> open = new ArrayList<>();
    private final List<String> faults = new ArrayList<>();

    Land(String id, String name, int size, long seed) {
        this.id = id;
        level = Level.blank(name, size);
        level.setWeather(Weather.DEMO);
        level.heights().feather(FEATHER);
        rnd = new Random(seed);
        half = size / 2.0;
    }

    // --- the sky -------------------------------------------------------------------------

    /** Its sky: the colours overhead and at the horizon, how far she sees, and the air. */
    Land sky(int zenith, int horizon, double fog, Atmosphere air) {
        level.setSky(new Sky(zenith, horizon, fog, List.of(), air));
        return this;
    }

    /**
     * The animals that come by on their own here: only these kinds (none
     * named: none at all). The herds put down ({@link #herd}) stand all the same.
     */
    Land wild(String... kinds) {
        for (AnimalKind k : AnimalKind.values()) level.life().setDensity(k, 0);
        for (String key : kinds) {
            AnimalKind k = AnimalKind.byKey(key);
            if (k == null) faults.add("no such animal: " + key);
            else level.life().setDensity(k, 1);
        }
        return this;
    }

    /** No crickets, frogs or fireflies of their own (a mine). */
    Land noCritters() {
        for (CritterKind k : CritterKind.values()) level.life().setDensity(k, 0);
        return this;
    }

    // --- the ground ----------------------------------------------------------------------

    /** Rolling ground: {@code metres} up and down, in swells about {@code across} metres wide. */
    Land roll(double metres, double across) {
        HeightField h = level.heights();
        long seed = rnd.nextLong();
        for (int j = 0; j < h.points(); j++) {
            for (int i = 0; i < h.points(); i++) {
                double x = h.west() + i * h.step(), z = h.west() + j * h.step();
                double v = noise(x / across, z / across, seed) * 0.65
                        + noise(x / across * 2.3, z / across * 2.3, seed + 7) * 0.35;
                h.set(i, j, h.at(i, j) + (v * 2 - 1) * metres);
            }
        }
        return this;
    }

    /** The whole ground by a rule: {@code rule.at(x, z, height now)} is its new height. */
    Land shape(Shape rule) {
        HeightField h = level.heights();
        for (int j = 0; j < h.points(); j++) {
            for (int i = 0; i < h.points(); i++) {
                h.set(i, j, rule.at(h.west() + i * h.step(), h.west() + j * h.step(), h.at(i, j)));
            }
        }
        return this;
    }

    interface Shape {
        double at(double x, double z, double was);
    }

    private static double hash(long x, long z, long seed) {
        long n = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ seed;
        n ^= n >>> 29;
        n *= 0xBF58476D1CE4E5B9L;
        n ^= n >>> 32;
        return (n & 0xFFFFFF) / (double) 0x1000000;
    }

    /** Smooth noise, 0 to 1. */
    static double noise(double x, double z, long seed) {
        long xi = (long) Math.floor(x), zi = (long) Math.floor(z);
        double fx = x - xi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(xi, zi, seed), b = hash(xi + 1, zi, seed), c = hash(xi, zi + 1, seed),
                d = hash(xi + 1, zi + 1, seed);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    double y(double x, double z) {
        return level.heights().height(x, z);
    }

    /** A hill (or, {@code metres} below nought, a hollow). */
    Land hill(double x, double z, double radius, double metres) {
        level.heights().feather(1).raise(x, z, radius, metres);
        level.heights().feather(FEATHER);
        return this;
    }

    /** 0 below 0, 1 above 1, smoothly between. */
    static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** The ground within {@code radius} levelled, at the height it has in the middle. */
    Land flat(double x, double z, double radius) {
        return flat(x, z, radius, y(x, z));
    }

    Land flat(double x, double z, double radius, double height) {
        level.heights().flatten(x, z, radius, height, 1);
        return this;
    }

    Land smooth(double x, double z, double radius) {
        for (int i = 0; i < 3; i++) level.heights().smooth(x, z, radius, 1);
        return this;
    }

    /** Ground of a {@code material} ({@link com.larsons.game.world.GroundMap}) within {@code radius}. */
    Land paint(int material, double x, double z, double radius) {
        level.ground().disc(material, x, z, radius);
        return this;
    }

    /** Patches of {@code material} within {@code radius}: {@code count} of them, each {@code lo} to {@code hi} across. */
    Land patches(int material, int count, double x, double z, double radius, double lo, double hi) {
        for (int i = 0; i < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * radius;
            level.ground().disc(material, x + Math.cos(a) * d, z + Math.sin(a) * d, lo + rnd.nextDouble() * (hi - lo));
        }
        return this;
    }

    /** A road of {@code material}, {@code halfWidth} to a side, from point to point ({@code x, z, x, z ...}). */
    Land road(int material, double halfWidth, double... xz) {
        for (int i = 0; i + 3 < xz.length; i += 2) {
            double x0 = xz[i], z0 = xz[i + 1], x1 = xz[i + 2], z1 = xz[i + 3];
            level.ground().stroke(material, x0, z0, x1, z1, halfWidth);
            double len = Math.hypot(x1 - x0, z1 - z0);
            for (double d = 0; d <= len; d += Math.max(1.5, halfWidth)) {
                double t = len < 1e-6 ? 0 : d / len;
                keepOpen(x0 + (x1 - x0) * t, z0 + (z1 - z0) * t, halfWidth + 0.8);
            }
        }
        return this;
    }

    /** Nothing is scattered within {@code radius} of here. */
    Land keepOpen(double x, double z, double radius) {
        open.add(new double[]{x, z, radius});
        return this;
    }

    // --- water ---------------------------------------------------------------------------

    /** A pool: the ground dug {@code depth} deep, water to just under its brim. */
    Land pool(WaterMap.Kind kind, double x, double z, double radius, double depth) {
        double brim = y(x, z);
        level.heights().flatten(x, z, radius * 1.7, brim, 1);
        level.heights().flatten(x, z, radius * 1.25, brim - depth, 1);
        level.water().paint(kind, brim - 0.22, x, z, radius * 1.25, 0, 0);
        keepOpen(x, z, radius * 1.15);
        return this;
    }

    /**
     * A river along {@code x, z, x, z ...}: its bed dug {@code depth} under
     * {@code surface + 0.3}, {@code halfWidth} to a side, flowing the way the
     * points go.
     */
    Land river(WaterMap.Kind kind, double surface, double depth, double halfWidth, double... xz) {
        double bank = surface + 0.3;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i + 3 < xz.length; i += 2) {
                double x0 = xz[i], z0 = xz[i + 1], x1 = xz[i + 2], z1 = xz[i + 3];
                double len = Math.hypot(x1 - x0, z1 - z0);
                double ux = (x1 - x0) / len, uz = (z1 - z0) / len;
                for (double d = 0; d <= len; d += halfWidth * 0.5) {
                    double x = x0 + ux * d, z = z0 + uz * d;
                    if (pass == 0) {
                        level.heights().flatten(x, z, halfWidth * 3.2, bank, 1);
                    } else {
                        level.heights().flatten(x, z, halfWidth * 1.7, bank - depth, 1);
                        level.water().paint(kind, surface, x, z, halfWidth * 1.9, ux * 1.2, uz * 1.2);
                        keepOpen(x, z, halfWidth * 1.5);
                    }
                }
            }
        }
        return this;
    }

    boolean wet(double x, double z) {
        return level.water().depth(x, z, y(x, z)) > 0.04;
    }

    // --- what stands on it ---------------------------------------------------------------

    /** The heading (degrees) that faces {@code (tx, tz)} from {@code (x, z)}. */
    static double facing(double x, double z, double tx, double tz) {
        return Math.toDegrees(SpriteView.headingOf(tx - x, tz - z));
    }

    /** An object of {@code type}, with its properties as {@code key, value, key, value ...}. */
    LevelObject put(String type, double x, double z, double heading, Object... kv) {
        Map<String, Object> p = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) p.put((String) kv[i], kv[i + 1]);
        if (ObjectTypes.get(type) == null) faults.add("no such kind of object: " + type);
        return level.add(type, x, z, Math.toRadians(heading), p);
    }

    Land spawn(double x, double z, double heading) {
        level.spawn().moveTo(x, z).turnTo(Math.toRadians(heading));
        keepOpen(x, z, 4);
        return this;
    }

    /** A building with the ground levelled under it, its door towards {@code (faceX, faceZ)} (to the nearest 45°). */
    LevelObject home(String kind, double x, double z, double faceX, double faceZ) {
        return house(kind, x, z, Math.round(facing(x, z, faceX, faceZ) / 45) * 45.0, 9);
    }

    /** A building facing {@code heading}, the ground levelled {@code room} metres round it. */
    LevelObject house(String kind, double x, double z, double heading, double room) {
        flat(x, z, room + 2);
        keepOpen(x, z, room);
        return building(kind, x, z, heading);
    }

    /** Something small that stands where it is put: a crate, a cart, a well. */
    LevelObject prop(String kind, double x, double z, double heading) {
        keepOpen(x, z, 1.6);
        return building(kind, x, z, heading);
    }

    private LevelObject building(String kind, double x, double z, double heading) {
        if (!Files.isDirectory(Path.of("assets", "buildings", kind))) faults.add("no such building: " + kind);
        return put(ObjectTypes.BUILDING, x, z, heading, "kind", kind);
    }

    /** One piece of scenery. */
    LevelObject scenery(String sprite, double x, double z, double scale) {
        if (!Files.isDirectory(Path.of("assets", "scenery", sprite))) faults.add("no such scenery: " + sprite);
        keepOpen(x, z, 0.8);
        return put(ObjectTypes.SCENERY, x, z, 0, "sprite", sprite, "mirrored", rnd.nextBoolean(), "scale",
                Math.round(scale * 100) / 100.0);
    }

    /**
     * {@code count} of {@code sprites} scattered within {@code radius} of
     * {@code (x, z)}, {@code lo} to {@code hi} their size - not on a road,
     * in water, or over what was put down before them.
     */
    Land scatter(int count, double x, double z, double radius, double lo, double hi, String... sprites) {
        for (String s : sprites) {
            if (!Files.isDirectory(Path.of("assets", "scenery", s))) faults.add("no such scenery: " + s);
        }
        int placed = 0;
        for (int tries = 0; tries < count * 8 && placed < count; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * radius;
            double px = x + Math.cos(a) * d, pz = z + Math.sin(a) * d;
            if (!free(px, pz)) continue;
            String sprite = sprites[rnd.nextInt(sprites.length)];
            put(ObjectTypes.SCENERY, Math.round(px * 100) / 100.0, Math.round(pz * 100) / 100.0, 0, "sprite", sprite,
                    "mirrored", rnd.nextBoolean(), "scale", Math.round((lo + rnd.nextDouble() * (hi - lo)) * 100) / 100.0);
            placed++;
        }
        return this;
    }

    /** {@code count} of {@code sprites} scattered over the whole level. */
    Land everywhere(int count, double lo, double hi, String... sprites) {
        return scatter(count, 0, 0, half * 1.4, lo, hi, sprites);
    }

    /** {@code count} of {@code sprites} along the level's edges, {@code width} deep: what she does not see past. */
    Land border(int count, double width, double lo, double hi, String... sprites) {
        int placed = 0;
        for (int tries = 0; tries < count * 8 && placed < count; tries++) {
            double along = (rnd.nextDouble() * 2 - 1) * (half - 2), in = half - 2 - rnd.nextDouble() * width;
            int side = rnd.nextInt(4);
            double px = side == 0 ? along : side == 1 ? along : side == 2 ? in : -in;
            double pz = side == 0 ? in : side == 1 ? -in : along;
            if (!free(px, pz)) continue;
            put(ObjectTypes.SCENERY, Math.round(px * 100) / 100.0, Math.round(pz * 100) / 100.0, 0, "sprite",
                    sprites[rnd.nextInt(sprites.length)], "mirrored", rnd.nextBoolean(), "scale",
                    Math.round((lo + rnd.nextDouble() * (hi - lo)) * 100) / 100.0);
            placed++;
        }
        return this;
    }

    private boolean free(double x, double z) {
        if (Math.abs(x) > half - 2 || Math.abs(z) > half - 2 || y(x, z) > onlyBelow) return false;
        return clearOf(x, z, 0) && !wet(x, z);
    }

    /** Whether nothing kept open is within {@code margin} of {@code (x, z)}. */
    boolean clearOf(double x, double z, double margin) {
        for (double[] o : open) if (Math.hypot(x - o[0], z - o[1]) < o[2] + margin) return false;
        return true;
    }

    /** A road round {@code (x, z)}, {@code radius} out. */
    Land ringRoad(int material, double halfWidth, double x, double z, double radius) {
        double[] xz = new double[34];
        for (int i = 0; i <= 16; i++) {
            xz[i * 2] = x + Math.cos(i * Math.PI / 8) * radius;
            xz[i * 2 + 1] = z + Math.sin(i * Math.PI / 8) * radius;
        }
        return road(material, halfWidth, xz);
    }

    /** Someone to talk to. */
    LevelObject person(String name, String people, String outfit, double x, double z, double heading,
                       String greeting) {
        keepOpen(x, z, 1.5);
        return put("person", x, z, heading, "name", name, "people", people, "outfit", outfit, "greeting", greeting);
    }

    /** A quest: what {@code giver} asks, done within {@code radius} of here (0: anywhere). */
    LevelObject quest(double x, double z, String title, String giver, String kind, String target, int count,
                      double radius, String offer, String remind, String thanks, String reward, int xp) {
        return put("quest", x, z, 0, "title", title, "giver", giver, "kind", kind, "target", target, "count", count,
                "radius", radius, "offer", offer, "remind", remind, "thanks", thanks, "reward", reward, "xp", xp);
    }

    LevelObject foe(String breed, double x, double z, double heading) {
        keepOpen(x, z, 1.2);
        return put("foe", x, z, heading, "breed", breed);
    }

    /** {@code count} foes of a {@code breed} standing about within {@code radius}. */
    Land band(String breed, int count, double x, double z, double radius, double roam) {
        for (int i = 0; i < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * radius;
            foe(breed, x + Math.cos(a) * d, z + Math.sin(a) * d, rnd.nextInt(360)).set("roam", roam);
        }
        return this;
    }

    LevelObject herd(String kind, int count, double x, double z, double radius) {
        return put(ObjectTypes.ANIMALS, x, z, 0, "kind", kind, "count", count, "radius", radius);
    }

    /** One animal, just here. */
    LevelObject animal(String kind, double x, double z) {
        return put(ObjectTypes.ANIMALS, x, z, rnd.nextInt(360), "kind", kind, "single", true, "radius", 4.0);
    }

    LevelObject critters(String kind, int count, double x, double z, double radius) {
        return put(ObjectTypes.CRITTERS, x, z, 0, "kind", kind, "count", count, "radius", radius);
    }

    /** A lantern on a post. */
    LevelObject lamp(double x, double z) {
        keepOpen(x, z, 1.2);
        return put(ObjectTypes.LIGHT, x, z, 0, "colour", "lantern", "radius", 9.0, "strength", 1.0, "height", 1.9,
                "fixture", "lantern_post");
    }

    /** A light with no fitting: a fire's, a crystal's, the moon's through a gap. */
    LevelObject glow(String colour, double x, double z, double radius, double height) {
        return put(ObjectTypes.LIGHT, x, z, 0, "colour", colour, "radius", radius, "strength", 1.0, "height", height,
                "fixture", "glow");
    }

    /** A camp fire: firewood, and its light. */
    Land fire(double x, double z) {
        prop("firewood", x, z, 0);
        glow("fire", x, z, 9, 0.6);
        return this;
    }

    LevelObject stall(String name, double x, double z, double heading, String stock) {
        keepOpen(x, z, 3.2);
        return put(ObjectTypes.SHOP, x, z, heading, "name", name, "stall", "market_stall_awning", "stock", stock,
                "buys", true);
    }

    LevelObject chest(double x, double z, double heading, String contents) {
        keepOpen(x, z, 1.5);
        return put(ObjectTypes.CHEST, x, z, heading, "loot", true, "contents", contents);
    }

    LevelObject item(String item, int count, double x, double z) {
        return put(ObjectTypes.ITEM, x, z, 0, "item", item, "count", count);
    }

    /** The signpost she leaves by; {@code clears}: reaching it clears the place (a map's "exit" rule). */
    LevelObject wayOut(String name, double x, double z, double heading, boolean clears) {
        keepOpen(x, z, 2.5);
        return put("wayout", x, z, heading, "name", name, "clears", clears);
    }

    /** A wall of castle stone from {@code (x0, z0)} to {@code (x1, z1)}, in lengths of 7.5 m. */
    Land rampart(double x0, double z0, double x1, double z1) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        int n = Math.max(1, (int) Math.ceil(len / 7.5 - 0.05));
        double heading = Math.toDegrees(Math.atan2(-(x1 - x0), -(z1 - z0))) + 90;
        for (int i = 0; i < n; i++) {
            double t = (i + 0.5) / n;
            prop("castle_wall", x0 + (x1 - x0) * t, z0 + (z1 - z0) * t, heading);
        }
        return this;
    }

    // --- saved ---------------------------------------------------------------------------

    /** Write it to {@code root/<id>}; what is wrong with it (a thing that does not exist) is said, and fails. */
    void save(Path root) throws IOException {
        for (LevelObject o : level.objects()) {
            for (String key : List.of("stock", "reward", "contents")) {
                Object v = o.props().get(key);
                if (v != null) checkItems(o, key, v.toString());
            }
        }
        if (!faults.isEmpty()) {
            throw new IllegalStateException(id + ": " + String.join("; ", faults.stream().distinct().toList()));
        }
        LevelIO.save(level, root.resolve(id));
        System.out.println(id + ": " + level.objects().size() + " objects, " + (int) level.size() + " m");
    }

    private void checkItems(LevelObject o, String key, String text) {
        for (String part : text.split(",")) {
            String s = part.strip().toLowerCase(Locale.ROOT).replaceAll("\\s*@\\s*\\d+$", "")
                    .replaceAll("\\s*[x×*]\\s*\\d+$", "").strip();
            if (s.isEmpty() || s.equals("cards") || s.startsWith("card:")) continue;
            Item item = Item.parse(s.replace(' ', '_'));
            if (item == null) item = Item.parse(s);
            if (item == null) faults.add(o.type() + " " + o.str("name", o.str("title", "")) + ": no such item \"" + s + "\"");
        }
    }
}
