package com.larsons.game.world;

import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.fallback.FallbackSprites;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Supplier;

/**
 * Something that can lie in the world and be picked up.
 *
 * <p>Two sets of art, both sprites: the world sprite that hovers over its
 * shadow ({@code assets/sprites/items/<id>/icon.png}, or {@code fallbackIcon}),
 * and the in-hand layer drawn on the character once it is carried — the
 * {@code <carrySlot>/<id>} sheets, frame for frame with the body.
 *
 * @param id          folder name, for both the icon and the carried layer
 * @param name        what the prompt calls it
 * @param carrySlot   which hand it goes in
 * @param worldSize   metres across its world sprite is drawn
 */
public record ItemDef(String id, String name, Slot carrySlot, double worldSize,
                      Supplier<BufferedImage> fallbackIcon) {

    /** The demo's one pick-up-able item. */
    public static final ItemDef SWORD = new ItemDef("sword", "Sword", Slot.CARRY_RIGHT, 1.0,
            FallbackSprites::swordIcon);

    public static final List<ItemDef> ALL = List.of(SWORD);

    public static ItemDef byId(String id) {
        for (ItemDef d : ALL) if (d.id.equals(id)) return d;
        return null;
    }
}
