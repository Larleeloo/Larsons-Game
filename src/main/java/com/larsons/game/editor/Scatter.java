package com.larsons.game.editor;

import com.larsons.game.level.Level;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.LevelWorld;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.ScenerySprites;
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
 * ground too steep for it - or, held with Shift, pulled up again.
 *
 * <p>What it sows is a {@link Set}: one sprite, or every sprite of a kind.
 */
final class Scatter {

    /** What the brush sows: its name and its sprites (chosen among evenly). */
    record Set(String label, List<String> sprites) {}

    private Scatter() {}

    /** The sets there are: each kind's sprites mixed, then each sprite alone. */
    static List<Set> sets(ScenerySprites sprites) {
        List<Set> out = new ArrayList<>();
        Map<SceneryProfile.Kind, String> mixes = new LinkedHashMap<>();
        mixes.put(SceneryProfile.Kind.TUFT, "Grass (mixed)");
        mixes.put(SceneryProfile.Kind.TREE, "Trees (mixed)");
        mixes.put(SceneryProfile.Kind.ROCK, "Rocks (mixed)");
        for (Map.Entry<SceneryProfile.Kind, String> m : mixes.entrySet()) {
            List<String> names = new ArrayList<>();
            for (ScenerySprites.Sprite s : sprites.ofKind(m.getKey())) names.add(s.name());
            if (!names.isEmpty()) out.add(new Set(m.getValue(), names));
        }
        for (ScenerySprites.Sprite s : sprites.sprites()) {
            out.add(new Set(ObjectTypes.readable(s.name()), List.of(s.name())));
        }
        return out;
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
        double want = perSquareMetre(first.kind()) * density * amount;
        int tries = (int) Math.floor(want) + (random.nextDouble() < want - Math.floor(want) ? 1 : 0);
        for (int i = 0; i < tries * 3 && added.size() < tries; i++) {
            ScenerySprites.Sprite s = sprites.sprite(set.sprites().get(random.nextInt(set.sprites().size())));
            if (s == null) continue;
            double a = random.nextDouble() * Math.PI * 2, r = radius * Math.sqrt(random.nextDouble());
            double px = x + Math.cos(a) * r, pz = z + Math.sin(a) * r;
            if (!level.contains(px, pz)) continue;
            double steepest = s.kind() == SceneryProfile.Kind.TUFT ? SceneryLayout.STEEPEST * 1.5 : SceneryLayout.STEEPEST;
            if (terrain.slope(px, pz) > steepest) continue;
            Scenery.Placed cand = new Scenery.Placed(s, px, terrain.height(px, pz), pz, random.nextDouble() * Math.PI,
                    random.nextBoolean(), 0);
            if (!fits(cand, near)) continue;
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("sprite", s.name());
            if (cand.mirrored()) props.put("mirrored", true);
            LevelObject o = level.add(ObjectTypes.SCENERY, px, pz, cand.yaw(), props);
            added.add(o);
            near.add(cand);
        }
        return added;
    }

    /** Whether {@code c} stands clear of everything in {@code near}: footprints apart, trees spaced. */
    static boolean fits(Scenery.Placed c, List<Scenery.Placed> near) {
        for (Scenery.Placed p : near) {
            double d = Math.hypot(p.x() - c.x(), p.z() - c.z());
            boolean trees = p.kind() == SceneryProfile.Kind.TREE && c.kind() == SceneryProfile.Kind.TREE;
            double apart = trees ? SceneryLayout.TREE_SPACING
                    : c.kind() == SceneryProfile.Kind.TUFT && p.kind() == SceneryProfile.Kind.TUFT
                    ? 0.5 * (c.footprint() + p.footprint())
                    : c.footprint() + p.footprint() + (c.kind() == SceneryProfile.Kind.TUFT ? 0 : SceneryLayout.ROCK_GAP);
            if (d < apart) return false;
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
