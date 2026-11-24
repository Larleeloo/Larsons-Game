package com.larsons.game.world;

import com.larsons.game.gfx.Images;
import com.larsons.game.sprite.ScenerySprites;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The colours a tree flies apart in when it is felled: the chips its axe
 * cuts out of it (fresh pale wood and its own bark) and the leaves it sheds
 * when it comes down - read once from its own sheet (the bark at the foot of
 * its first frame, along its axis; the leaves the commonest colours of its
 * crown), so a pine sheds needles, a birch white bark, a blossom tree its
 * blossom. A sheet that cannot be read gives an oak's.
 */
public record TreeColours(int[] chips, int[] leaves) {

    /** Fresh-cut wood: what every chip is partly made of. */
    static final int[] WOOD = {0xD8B47A, 0xC89A5E, 0xE6C890};
    /** An oak's, when the sheet says nothing. */
    static final TreeColours FALLBACK = new TreeColours(
            new int[]{0xD8B47A, 0xC89A5E, 0xE6C890, 0x6A4A2E, 0x584026},
            new int[]{0x4E7E32, 0x5E9038, 0x3E6A2A, 0x6E9A40});

    private static final Map<String, TreeColours> CACHE = new ConcurrentHashMap<>();

    /** {@code p}'s colours (cached by its sprite). */
    public static TreeColours of(Scenery.Placed p) {
        if (p == null || p.sprite() == null) return FALLBACK;
        return CACHE.computeIfAbsent(p.sprite().name(), k -> read(p.sprite()));
    }

    static TreeColours read(ScenerySprites.Sprite s) {
        try {
            if (s.sheet() == null) return FALLBACK;
            BufferedImage image = Images.read(s.sheet());
            if (image == null) return FALLBACK;
            int fw = Math.min(image.getWidth(), s.profile().frameWidth());
            int fh = Math.min(image.getHeight(), s.profile().frameHeight());
            return of(image, fw, fh, s.profile().radius() / Math.max(0.1, s.profile().frameWorldSize()));
        } catch (Exception | Error e) {
            return FALLBACK;
        }
    }

    /**
     * The colours of the tree in {@code image}'s first {@code fw} by {@code
     * fh} frame, its trunk {@code radiusShare} of the frame's width round its
     * middle column.
     */
    static TreeColours of(BufferedImage image, int fw, int fh, double radiusShare) {
        int mid = fw / 2;
        int half = Math.max(1, (int) Math.round(fw * radiusShare * 0.7));
        Map<Integer, Integer> bark = new HashMap<>();
        for (int y = (int) (fh * 0.82); y < fh; y++) {
            for (int x = Math.max(0, mid - half); x <= Math.min(fw - 1, mid + half); x++) count(bark, image.getRGB(x, y));
        }
        Map<Integer, Integer> crown = new HashMap<>();
        for (int y = 0; y < (int) (fh * 0.62); y++) {
            for (int x = 0; x < fw; x++) count(crown, image.getRGB(x, y));
        }
        List<Integer> barkTop = commonest(bark, 2, List.of());
        List<Integer> crownTop = commonest(crown, 4, barkTop);
        int[] chips = new int[WOOD.length + barkTop.size()];
        System.arraycopy(WOOD, 0, chips, 0, WOOD.length);
        for (int i = 0; i < barkTop.size(); i++) chips[WOOD.length + i] = barkTop.get(i);
        int[] leaves = crownTop.isEmpty() ? FALLBACK.leaves() : crownTop.stream().mapToInt(Integer::intValue).toArray();
        if (barkTop.isEmpty()) chips = FALLBACK.chips();
        return new TreeColours(chips, leaves);
    }

    private static void count(Map<Integer, Integer> counts, int argb) {
        if ((argb >>> 24) < 200) return;
        counts.merge(argb & 0xFFFFFF, 1, Integer::sum);
    }

    /** The {@code n} commonest colours of {@code counts}, leaving out {@code not}. */
    private static List<Integer> commonest(Map<Integer, Integer> counts, int n, List<Integer> not) {
        List<Map.Entry<Integer, Integer>> all = new ArrayList<>(counts.entrySet());
        all.sort((a, b) -> b.getValue() != a.getValue().intValue()
                ? Integer.compare(b.getValue(), a.getValue()) : Integer.compare(a.getKey(), b.getKey()));
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : all) {
            if (out.size() >= n) break;
            if (!not.contains(e.getKey())) out.add(e.getKey());
        }
        return out;
    }
}
