package com.larsons.game.world;

import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Stance;
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
 * <p>The sword is worn: picked up, it goes into the wardrobe's weapon hand.
 * A weapon with a {@link Stance} of its own - the battle axe, the bow, the
 * crossbow, the crystal staff, the wand, the mace, the dagger - is held
 * instead: picked up, she takes up its stance, and its states draw it
 * ({@link Stance#carried}).
 *
 * <p>A wearable - clothes, armour, jewellery, a dye, a hair style - is never
 * in her hands: it has a {@link Wearable} saying where it is worn, no carry
 * slot, and the sword stance (picked up, nothing changes but that it is
 * hers to wear from the wardrobe). They are listed apart, in {@link
 * Wearables#ALL}; {@link #ALL} is the weapons.
 *
 * @param id          folder name, for both the icon and the carried layer
 * @param name        what the prompt calls it
 * @param carrySlot   which hand it goes in (a right-handed character's); null for a wearable
 * @param worldSize   metres across its world sprite is drawn
 * @param stance      the stance it is held in ({@link Stance#SWORD}: worn in the wardrobe)
 * @param wearable    where and how it is worn, or null for a weapon
 */
public record ItemDef(String id, String name, Slot carrySlot, double worldSize,
                      Supplier<BufferedImage> fallbackIcon, Stance stance, Wearable wearable) {

    /** A weapon (or anything else that is not a wearable). */
    public ItemDef(String id, String name, Slot carrySlot, double worldSize,
                   Supplier<BufferedImage> fallbackIcon, Stance stance) {
        this(id, name, carrySlot, worldSize, fallbackIcon, stance, null);
    }

    public static final ItemDef SWORD = new ItemDef("sword", "Sword", Slot.CARRY_RIGHT, 1.0,
            FallbackSprites::swordIcon, Stance.SWORD);
    public static final ItemDef BATTLE_AXE = new ItemDef("battle_axe", "Battle axe", Slot.CARRY_RIGHT, 1.1,
            () -> FallbackSprites.weaponIcon("battle_axe"), Stance.AXE);
    public static final ItemDef LONGBOW = new ItemDef("longbow", "Longbow", Slot.CARRY_LEFT, 1.3,
            () -> FallbackSprites.weaponIcon("longbow"), Stance.BOW);
    public static final ItemDef CROSSBOW = new ItemDef("crossbow", "Crossbow", Slot.CARRY_RIGHT, 1.0,
            () -> FallbackSprites.weaponIcon("crossbow"), Stance.CROSSBOW);

    public static final ItemDef CRYSTAL_STAFF = new ItemDef("crystal_staff", "Crystal staff", Slot.CARRY_RIGHT, 1.5,
            () -> FallbackSprites.weaponIcon("crystal_staff"), Stance.STAFF);

    public static final ItemDef WAND = new ItemDef("wand", "Wand", Slot.CARRY_RIGHT, 0.4,
            () -> FallbackSprites.weaponIcon("wand"), Stance.WAND);
    public static final ItemDef MACE = new ItemDef("mace", "Mace", Slot.CARRY_RIGHT, 0.75,
            () -> FallbackSprites.weaponIcon("mace"), Stance.MACE);
    public static final ItemDef DAGGER = new ItemDef("dagger", "Dagger", Slot.CARRY_RIGHT, 0.5,
            () -> FallbackSprites.weaponIcon("dagger"), Stance.DAGGER);

    public static final List<ItemDef> ALL = List.of(SWORD, BATTLE_AXE, LONGBOW, CROSSBOW, CRYSTAL_STAFF,
            WAND, MACE, DAGGER);

    /** Whether it is held in a stance of its own rather than worn. */
    public boolean held() {
        return stance != Stance.SWORD;
    }

    /** Whether it is worn rather than held: clothes, armour, a dye, a hair style ({@link #wearable}). */
    public boolean isWearable() {
        return wearable != null;
    }

    /**
     * Whether, picked up, it goes into the wardrobe's hand ({@link #carrySlot}):
     * the sword - no stance of its own, and not a wearable.
     */
    public boolean inHand() {
        return !held() && !isWearable();
    }

    /** The weapon of {@code stance}, or null (the sword stance's are worn, the free one has none). */
    public static ItemDef of(Stance stance) {
        for (ItemDef d : ALL) if (d.stance == stance && d.held()) return d;
        return null;
    }

    /** A weapon or a wearable by its id, or null. */
    public static ItemDef byId(String id) {
        for (ItemDef d : ALL) if (d.id.equals(id)) return d;
        return Wearables.byId(id);
    }
}
