package com.larsons.game.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * What a loot chest gives: between {@value #MIN} and {@value #MAX} items, at
 * least one of them Rare or better.
 *
 * <p>Each item's rarity is drawn by the drop rates ({@link Rarity#weight}),
 * then the item is one of that rarity's drops, any one as likely as another,
 * and then it may get a rare border ({@link Border#roll}: one chance in a
 * hundred each). When none of a chest's items came out Rare or better, one of
 * them - picked at random - is drawn again from the rarities that are.
 *
 * <p>The drops ({@link #pool}) are every tier of every weapon in Larson's
 * chart ({@link Tier#CHART}) whose {@link ItemDef} exists - a Basic, Sharper,
 * Crystal, Perfect or Mythic Axe; a Magic Staff... - and anything else added
 * with {@link #add}: the wearables and the armour, with their rarities. A
 * rarity nothing drops as is drawn as the next one down that has drops.
 */
public final class Loot {

    /** The fewest and the most items one chest gives. */
    public static final int MIN = 3, MAX = 7;
    /** At least one of a chest's items is this rare or rarer. */
    public static final Rarity GUARANTEED = Rarity.RARE;

    private final Map<Rarity, List<Item>> pool = new EnumMap<>(Rarity.class);

    /** A table of every tier of every weapon in the chart that the game has. */
    public Loot() {
        for (Rarity r : Rarity.values()) pool.put(r, new ArrayList<>());
        for (Tier.Row row : Tier.CHART.values()) {
            ItemDef def = ItemDef.byId(row.item());
            if (def == null) continue;
            for (Tier t : row.tiers()) add(Item.of(def, t));
        }
    }

    /** Add a drop (any border it has is dropped: borders are rolled). */
    public Loot add(Item item) {
        pool.get(item.rarity()).add(item.withBorder(null));
        return this;
    }

    /** The drops of {@code rarity}. */
    public List<Item> pool(Rarity rarity) {
        return Collections.unmodifiableList(pool.get(rarity));
    }

    /** One chest's worth: {@value #MIN} to {@value #MAX} items, one at least {@link #GUARANTEED}. */
    public List<Item> roll(Random random) {
        int n = MIN + random.nextInt(MAX - MIN + 1);
        List<Rarity> rarities = new ArrayList<>();
        for (int i = 0; i < n; i++) rarities.add(Rarity.roll(random));
        if (rarities.stream().noneMatch(r -> r.atLeast(GUARANTEED))) {
            rarities.set(random.nextInt(n), Rarity.roll(random, GUARANTEED));
        }
        List<Item> out = new ArrayList<>();
        for (Rarity r : rarities) {
            Item item = pick(random, r);
            if (item != null) out.add(item.withBorder(Border.roll(random)));
        }
        return out;
    }

    /** One drop of {@code rarity} - or of the next rarity down that has drops - or null with no drops at all. */
    public Item pick(Random random, Rarity rarity) {
        for (int i = rarity.ordinal(); i >= 0; i--) {
            List<Item> p = pool.get(Rarity.values()[i]);
            if (!p.isEmpty()) return p.get(random.nextInt(p.size()));
        }
        for (Rarity r : Rarity.values()) {
            List<Item> p = pool.get(r);
            if (!p.isEmpty()) return p.get(random.nextInt(p.size()));
        }
        return null;
    }
}
