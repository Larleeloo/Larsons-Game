package com.larsons.game.world;

import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.fallback.FallbackSprites;

import java.util.List;

/**
 * What the foraging bushes give (the Ambient milestone's foraging): berries,
 * herbs, fruit and flowers - each {@link Food} to eat, and an ingredient for
 * the potions to come. A bush's {@code profile.json} names the one it gives
 * ({@code assets/scenery/<bush>/}, {@code "forage"}).
 *
 * <pre>
 *   wild_berries    berry bush         plains, river      +20 health
 *   bilberries      bilberry bush      mountains, marsh   +12 health, +15 stamina
 *   sage            sage bush          plains, savannah   +30 stamina
 *   sunfruit        sunfruit bush      jungle             +35 health
 *   thornberries    thornberry bush    savannah, waste    +10 health, +10 stamina
 *   crystal_bloom   crystal bloom      crystal            +40 mana
 *   sea_buckthorn   sea buckthorn      ocean              +10 health, +15 mana
 * </pre>
 */
public final class Pickings {

    /** How many of a picking go in one inventory slot. */
    public static final int STACK = 20;

    public static final ItemDef WILD_BERRIES = picking("wild_berries", "Wild berries",
            new Food("Food", 20, 0, 0, STACK, Food.BERRIES, "fruit", 0xD8283C));
    public static final ItemDef BILBERRIES = picking("bilberries", "Bilberries",
            new Food("Food", 12, 15, 0, STACK, Food.BERRIES, "fruit", 0x3A4FC8));
    public static final ItemDef SAGE = picking("sage", "Sage",
            new Food("Herb", 0, 30, 0, STACK, Food.SPRIG, "flower", 0x9B59D0));
    public static final ItemDef SUNFRUIT = picking("sunfruit", "Sunfruit",
            new Food("Food", 35, 0, 0, STACK, Food.FRUIT, "fruit", 0xFB8C1C));
    public static final ItemDef THORNBERRIES = picking("thornberries", "Thornberries",
            new Food("Food", 10, 10, 0, STACK, Food.BERRIES, "fruit", 0xE6C01F));
    public static final ItemDef CRYSTAL_BLOOM = picking("crystal_bloom", "Crystal bloom",
            new Food("Flower", 0, 0, 40, STACK, Food.BLOOM, "petals", 0xFF6FC0));
    public static final ItemDef SEA_BUCKTHORN = picking("sea_buckthorn", "Sea buckthorn",
            new Food("Food", 10, 0, 15, STACK, Food.BERRIES, "fruit", 0xFF8A1F));

    public static final List<ItemDef> ALL = List.of(WILD_BERRIES, BILBERRIES, SAGE, SUNFRUIT, THORNBERRIES,
            CRYSTAL_BLOOM, SEA_BUCKTHORN);

    private Pickings() {}

    private static ItemDef picking(String id, String name, Food food) {
        return new ItemDef(id, name, null, 0.4, () -> FallbackSprites.wearableIcon(food.rgb()), Stance.SWORD, null,
                food);
    }

    /** The picking called {@code id}, or null. */
    public static ItemDef byId(String id) {
        for (ItemDef d : ALL) if (d.id().equals(id)) return d;
        return null;
    }
}
