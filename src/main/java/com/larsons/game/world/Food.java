package com.larsons.game.world;

/**
 * What a picking from a foraging bush does: eaten (a click with it in hand,
 * or a right-click in the inventory) it gives back some of what she has
 * spent - health, stamina, mana - and it is a potion ingredient for the
 * brewing to come (the Crafting milestone). Carried in stacks of up to
 * {@link #stack}.
 *
 * <p>It is drawn, lying about and in the inventory, as one of four turning
 * pickups (3D-Modeling's {@code items/item_spin_sprites.py}) with its {@code
 * part} painted {@code rgb}: a sprig of berries (red, blue, yellow or
 * orange), a sprig of sage, a round fruit, a crystal bloom.
 *
 * @param kind    what it is called as a sort of thing: "Food", "Herb", "Flower"
 * @param health  health it gives back, eaten
 * @param stamina stamina it gives back
 * @param mana    mana it gives back
 * @param stack   how many go in one inventory slot
 * @param pickup  the turning pickup it is drawn as ({@code items/<pickup>/})
 * @param part    the pickup's part painted in its colour
 * @param rgb     its colour
 */
public record Food(String kind, double health, double stamina, double mana, int stack, String pickup, String part,
                   int rgb) {

    /** The pickups the pickings are drawn as. */
    public static final String BERRIES = "forage_berries", SPRIG = "forage_sprig", FRUIT = "forage_fruit",
            BLOOM = "forage_bloom";

    /** What eating it gives back, as the line under its name in the inventory: "+20 health, +15 stamina". */
    public String effect() {
        StringBuilder s = new StringBuilder();
        if (health > 0) s.append("+").append(Math.round(health)).append(" health");
        if (stamina > 0) s.append(s.isEmpty() ? "" : ", ").append("+").append(Math.round(stamina)).append(" stamina");
        if (mana > 0) s.append(s.isEmpty() ? "" : ", ").append("+").append(Math.round(mana)).append(" mana");
        return s.toString();
    }
}
