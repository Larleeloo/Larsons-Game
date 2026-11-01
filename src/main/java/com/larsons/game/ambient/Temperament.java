package com.larsons.game.ambient;

import java.util.Locale;

/**
 * An animal's nature: what it does when the player comes near, and when she
 * hits it. Every kind has its own ({@link AnimalKind#temperament}); a herd
 * placed in Creative Mode can be given another - tame geese, a hostile bear.
 * Whatever its nature, an animal beaten (its health gone) runs off.
 */
public enum Temperament {

    /** Never runs, never fights: a farm's own. */
    TAME("tame", "Tame", "never runs off, never fights"),
    /** Minds its own business; runs when she hits it. */
    CALM("calm", "Calm", "minds its own business, runs when hit"),
    /** Runs from her when she comes near. */
    SKITTISH("skittish", "Skittish", "runs when she comes near"),
    /** Comes up to her and keeps by her a while; runs when hit. */
    FRIENDLY("friendly", "Friendly", "comes up to her, follows a while"),
    /** Warns her off when she comes near, goes for her when she stays - or hits it. */
    DEFENSIVE("defensive", "Defensive", "warns her off, fights back"),
    /** Goes for her on sight. */
    HOSTILE("hostile", "Hostile", "goes for her on sight");

    private final String key, label, about;

    Temperament(String key, String label, String about) {
        this.key = key;
        this.label = label;
        this.about = about;
    }

    /** One word for it, in level files: {@code defensive}. */
    public String key() { return key; }

    public String label() { return label; }

    /** What it does, in a few words (the editor says it). */
    public String about() { return about; }

    /** Whether hitting it makes it fight back. */
    public boolean fightsBack() {
        return this == DEFENSIVE || this == HOSTILE;
    }

    /** The temperament written {@code key}, or null (an animal's own). */
    public static Temperament byKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (Temperament t : values()) if (t.key.equals(k)) return t;
        return null;
    }
}
