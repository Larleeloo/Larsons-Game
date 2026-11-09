package com.larsons.game.editor;

import com.larsons.game.level.Level;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.LevelWorld;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.world.Biome;
import com.larsons.game.world.Scenery;
import com.larsons.game.world.SceneryLayout;
import com.larsons.game.world.Terrain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The scatter brush: grass, trees or rocks sown over the ground under the
 * brush as it is held down - each at a turn of its own, half of them
 * mirrored, never inside another's footprint, trees {@link
 * SceneryLayout#TREE_SPACING} m apart as the demo has them, nothing on
 * ground too steep for it nor in water (but reeds) - or, held with Shift,
 * pulled up again.
 *
 * <p>What it sows is a {@link Set}: one sprite, or every sprite of a kind.
 */
final class Scatter {

    /**
     * What the brush sows: its name, its sprites and how often each is
     * chosen (its weight against the others'; empty: evenly), the sizes they
     * come in (from, to: times as drawn), how far apart they keep beyond
     * their footprints (metres), and - one made in the editor - its brush's
     * id (null: one of the game's).
     */
    record Set(String label, List<String> sprites, List<Double> weights, double sizeFrom, double sizeTo,
               double spacing, String brush) {

        Set(String label, List<String> sprites) {
            this(label, sprites, List.of(), 1, 1, 0, null);
        }

        /** One of its sprites, by their weights. */
        String pick(Random random) {
            if (weights.size() != sprites.size()) return sprites.get(random.nextInt(sprites.size()));
            double total = 0;
            for (double w : weights) total += Math.max(0, w);
            double r = random.nextDouble() * total;
            for (int i = 0; i < sprites.size(); i++) {
                r -= Math.max(0, weights.get(i));
                if (r <= 0) return sprites.get(i);
            }
            return sprites.get(sprites.size() - 1);
        }

        /** A size for one, between its from and to. */
        double size(Random random) {
            double lo = Math.min(sizeFrom, sizeTo), hi = Math.max(sizeFrom, sizeTo);
            return lo + (hi - lo) * random.nextDouble();
        }
    }

    private Scatter() {}

    /**
     * The sets there are: the meadow's grass, trees and rocks mixed (the
     * sprites that name no biome), then each biome's, then each sprite alone.
     */
    static List<Set> sets(ScenerySprites sprites) {
        List<Set> out = new ArrayList<>();
        Map<SceneryProfile.Kind, String> mixes = new LinkedHashMap<>();
        mixes.put(SceneryProfile.Kind.TUFT, "Grass (mixed)");
        mixes.put(SceneryProfile.Kind.TREE, "Trees (mixed)");
        mixes.put(SceneryProfile.Kind.ROCK, "Rocks (mixed)");
        for (Map.Entry<SceneryProfile.Kind, String> m : mixes.entrySet()) {
            List<String> names = new ArrayList<>();
            for (ScenerySprites.Sprite s : sprites.meadow(m.getKey())) {
                if (!s.profile().forages()) names.add(s.name());
            }
            if (!names.isEmpty()) out.add(new Set(m.getValue(), names));
        }
        List<String> bushes = new ArrayList<>();
        for (ScenerySprites.Sprite s : sprites.forage()) bushes.add(s.name());
        if (!bushes.isEmpty()) out.add(new Set("Foraging bushes (mixed)", bushes));
        for (Biome b : Biome.values()) {
            for (SceneryProfile.Kind k : mixes.keySet()) {
                Set s = biomeSet(sprites, b, k);
                if (!s.sprites().isEmpty()) out.add(s);
            }
            Set bs = bushSet(sprites, b);
            if (!bs.sprites().isEmpty()) out.add(bs);
        }
        for (ScenerySprites.Sprite s : sprites.placeable()) {
            out.add(new Set(ObjectTypes.readable(s.name()), List.of(s.name())));
        }
        return out;
    }

    /**
     * What grows in {@code biome} of {@code kind}: the sprites whose profiles
     * name it - and, on the plains, the meadow's own, which name none.
     */
    static Set biomeSet(ScenerySprites sprites, Biome biome, SceneryProfile.Kind kind) {
        List<String> names = new ArrayList<>();
        for (ScenerySprites.Sprite s : sprites.ofKind(kind)) {
            if (s.profile().forages()) continue;         // (sown far thinner: bushSet)
            if (s.profile().grows(biome.key()) || biome == Biome.PLAINS && s.profile().biomes().isEmpty()) {
                names.add(s.name());
            }
        }
        String what = switch (kind) {
            case TUFT -> "plants";
            case TREE -> "trees";
            case ROCK -> "rocks";
        };
        return new Set(biome.label() + ": " + what, names);
    }

    /** The foraging bushes that grow in {@code biome}: "Plains: bushes". */
    static Set bushSet(ScenerySprites sprites, Biome biome) {
        List<String> names = new ArrayList<>();
        for (ScenerySprites.Sprite s : sprites.forage()) if (s.profile().grows(biome.key())) names.add(s.name());
        return new Set(biome.label() + ": bushes", names);
    }

    /** How many foraging bushes a square metre is sown with at a density of 1: one every 30 m² or so. */
    static final double BUSHES_PER_SQUARE_METRE = 0.035;

    /** How many a square metre {@code s} is sown at, a density of 1: its kind's, a foraging bush's own. */
    static double perSquareMetre(ScenerySprites.Sprite s) {
        return s.profile().forages() ? BUSHES_PER_SQUARE_METRE : perSquareMetre(s.kind());
    }

    /** How many a square metre each kind is sown at a density of 1 (grass thick, trees far apart). */
    static double perSquareMetre(SceneryProfile.Kind kind) {
        return switch (kind) {
            case TUFT -> 1.2;
            case ROCK -> 0.06;
            case TREE -> 0.04;
        };
    }

    /**
     * Sow {@code set} round {@code (x, z)} within {@code radius} metres of
     * {@code level} standing on {@code terrain}, as much as {@code amount}
     * square metres' worth at {@code density} (1: as thick as the demo's
     * thickest): what was added.
     */
    static List<LevelObject> sow(Level level, Terrain terrain, ScenerySprites sprites, Set set, double x, double z,
                                 double radius, double density, double amount, Random random) {
        return sow(level, terrain, sprites, set, x, z, radius, density, amount, random, null);
    }

    /** {@link #sow(Level, Terrain, ScenerySprites, Set, double, double, double, double, double, Random)}, only where {@code mask} lets it (null: anywhere). */
    static List<LevelObject> sow(Level level, Terrain terrain, ScenerySprites sprites, Set set, double x, double z,
                                 double radius, double density, double amount, Random random,
                                 com.larsons.game.world.BrushMask mask) {
        List<LevelObject> added = new ArrayList<>();
        if (set.sprites().isEmpty()) return added;
        List<Scenery.Placed> near = new ArrayList<>();
        for (LevelObject o : level.objects()) {
            if (!o.type().equals(ObjectTypes.SCENERY)) continue;
            if (Math.hypot(o.x() - x, o.z() - z) > radius + 6) continue;
            Scenery.Placed p = LevelWorld.placed(o, terrain, sprites);
            if (p != null) near.add(p);
        }
        ScenerySprites.Sprite first = sprites.sprite(set.sprites().get(0));
        if (first == null) return added;
        double want = perSquareMetre(first) * density * amount;
        int tries = (int) Math.floor(want) + (random.nextDouble() < want - Math.floor(want) ? 1 : 0);
        for (int i = 0; i < tries * 3 && added.size() < tries; i++) {
            ScenerySprites.Sprite s = sprites.sprite(set.pick(random));
            if (s == null) continue;
            double a = random.nextDouble() * Math.PI * 2, r = radius * Math.sqrt(random.nextDouble());
            double px = x + Math.cos(a) * r, pz = z + Math.sin(a) * r;
            if (!level.contains(px, pz)) continue;
            if (mask != null && random.nextDouble() >= mask.at(px, pz)) continue;
            double steepest = s.kind() == SceneryProfile.Kind.TUFT ? SceneryLayout.STEEPEST * 1.5 : SceneryLayout.STEEPEST;
            if (terrain.slope(px, pz) > steepest) continue;
            if (level.water().depth(px, pz, terrain.height(px, pz)) > wadeDepth(s)) continue;
            double size = set.size(random);
            Scenery.Placed cand = new Scenery.Placed(s, px, terrain.height(px, pz), pz, random.nextDouble() * Math.PI,
                    random.nextBoolean(), 0, size, 0);
            if (!fits(cand, near, set.spacing())) continue;
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("sprite", s.name());
            if (cand.mirrored()) props.put("mirrored", true);
            if (Math.abs(size - 1) > 1e-3) props.put("scale", Math.round(size * 100) / 100.0);
            LevelObject o = level.add(ObjectTypes.SCENERY, px, pz, cand.yaw(), props);
            added.add(o);
            near.add(cand);
        }
        return added;
    }

    /**
     * How deep in water {@code s} may stand: reeds and the marsh's and the
     * river's other plants knee-deep, the rest only on dry ground.
     */
    static double wadeDepth(ScenerySprites.Sprite s) {
        boolean wet = s.profile().grows(Biome.MARSH.key()) || s.profile().grows(Biome.RIVER.key());
        return s.kind() == SceneryProfile.Kind.TUFT && wet ? 0.45 : 0.02;
    }

    /** Whether {@code c} stands clear of everything in {@code near}: footprints apart, trees spaced. */
    static boolean fits(Scenery.Placed c, List<Scenery.Placed> near) {
        return fits(c, near, 0);
    }

    /** {@link #fits(Scenery.Placed, List)}, {@code spacing} metres further apart still. */
    static boolean fits(Scenery.Placed c, List<Scenery.Placed> near, double spacing) {
        for (Scenery.Placed p : near) {
            double d = Math.hypot(p.x() - c.x(), p.z() - c.z());
            boolean trees = p.kind() == SceneryProfile.Kind.TREE && c.kind() == SceneryProfile.Kind.TREE;
            double apart = trees ? SceneryLayout.TREE_SPACING
                    : c.kind() == SceneryProfile.Kind.TUFT && p.kind() == SceneryProfile.Kind.TUFT
                    ? 0.5 * (c.footprint() + p.footprint())
                    : c.footprint() + p.footprint() + (c.kind() == SceneryProfile.Kind.TUFT ? 0 : SceneryLayout.ROCK_GAP);
            if (d < apart + spacing) return false;
        }
        return true;
    }

    /** Pull up whatever of {@code set} stands within {@code radius} of {@code (x, z)}: how many. */
    static int clear(Level level, Set set, double x, double z, double radius) {
        int before = level.objects().size();
        level.objects().removeIf(o -> o.type().equals(ObjectTypes.SCENERY)
                && set.sprites().contains(o.str("sprite", "")) && Math.hypot(o.x() - x, o.z() - z) <= radius);
        return before - level.objects().size();
    }
}
