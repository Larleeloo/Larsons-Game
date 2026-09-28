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
 * <p>The enum order <em>is</em> the draw order. Carried items come last
 * because their cut-outs already account for the hand and body.
 */
public enum Slot {

    BODY("body", "Base body"),
    UNDERWEAR("underwear", "Underwear"),
    BRA("bra", "Bra"),
    SHOES("shoes", "Shoes"),
    PANTS("pants", "Pants"),
    SHIRT("shirt", "Shirt"),
    GLOVES("gloves", "Gloves"),
    WRISTWEAR("wristwear", "Wristwear"),
    EARS("ears", "Ears"),
    EARRINGS("earrings", "Earrings"),
    NOSE("nose", "Nose"),
    EYES("eyes", "Eyes"),
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

    public static Slot byKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase();
        for (Slot s : values()) {
            if (s.key.equals(k)) return s;
        }
        return null;
    }
}
