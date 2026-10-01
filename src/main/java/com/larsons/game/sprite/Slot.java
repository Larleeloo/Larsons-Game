package com.larsons.game.sprite;

/**
 * One layer of a character's sprite stack, in the order the layers are drawn.
 *
 * <p>A character is not one sprite sheet but a stack of them: the base body
 * first, then every cosmetic on top, each one a sheet with the same frame
 * count as the body for the same animation state. Every layer was rendered in
 * Blender from the same camera as the body with <b>whatever is in front of it
 * held out</b> — the sword is cut away where the hand wraps round the grip and
 * where the body passes in front of the blade — so drawing the layers in this
 * fixed order gives the right occlusion from all 24 view angles without the
 * game knowing anything about depth.
 *
 * <p>The enum order <em>is</em> the draw order, with one exception: the cape
 * ({@link #OTHER}) hangs behind her, so it draws first when she faces the
 * camera and just under the hair otherwise (see
 * {@link LayerStack#drawOrder}). Carried items come last because their
 * cut-outs already account for the hand and body.
 */
public enum Slot {

    BODY("body", "Base body"),
    UNDERWEAR("underwear", "Underwear"),
    BRA("bra", "Bra"),
    SHOES("shoes", "Shoes"),
    PANTS("pants", "Pants"),
    SHIRT("shirt", "Shirt"),
    BELT("belt", "Belt"),
    NECKLACE("necklace", "Necklace"),
    GLOVES("gloves", "Gloves"),
    WRISTWEAR("wristwear", "Right wrist"),
    WRISTWEAR_LEFT("wristwear_left", "Left wrist"),
    SHEATH("sheath", "Sheath"),
    EARS("ears", "Ears"),
    EARRINGS("earrings", "Earrings"),
    NOSE("nose", "Nose"),
    EYES("eyes", "Eyes"),
    EYEBROWS("eyebrows", "Eyebrows"),
    MAKEUP("makeup", "Makeup"),
    MOUTH("mouth", "Mouth"),
    HAIR("hair", "Hair"),
    HAT("hat", "Hat"),
    OTHER("other", "Other"),
    CARRY_LEFT("carry_left", "Left hand"),
    CARRY_RIGHT("carry_right", "Right hand");

    private final String key;
    private final String label;

    Slot(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** Folder name under {@code assets/sprites/}. */
    public String key() { return key; }

    public String label() { return label; }

    /** Whether this is a cosmetic layer (everything but the base body). */
    public boolean cosmetic() { return this != BODY; }

    /** Whether this slot holds a carried item rather than something worn. */
    public boolean carried() { return this == CARRY_LEFT || this == CARRY_RIGHT; }

    /**
     * The same place on the other side of the body: the other hand, the other
     * wrist; every other slot is its own twin. A left-handed character is the
     * right-handed one in a mirror, so what it has in a slot is drawn as the
     * mirror image of the same item in that slot's twin (the sword in the left
     * hand is the right-hand sword, mirrored).
     */
    public Slot twin() {
        return switch (this) {
            case CARRY_LEFT -> CARRY_RIGHT;
            case CARRY_RIGHT -> CARRY_LEFT;
            case WRISTWEAR -> WRISTWEAR_LEFT;
            case WRISTWEAR_LEFT -> WRISTWEAR;
            default -> this;
        };
    }

    public static Slot byKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase();
        for (Slot s : values()) {
            if (s.key.equals(k)) return s;
        }
        return null;
    }
}
