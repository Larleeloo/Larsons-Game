package com.larsons.game.audio;

/**
 * The groups every sound in the game belongs to - each a tab of the Sounds
 * menu (Pause → Sounds) with a volume of its own, so the footsteps can be
 * turned down without the swords going quiet, and a creator choosing their
 * own files finds them among a dozen or two rather than one long list.
 *
 * <p>A sound's category follows from its key ({@link SoundKeys#categoryOf}):
 * a body's walk is {@link #MOVEMENT}, its axe chop {@link #WEAPONS}, its
 * laugh {@link #VOICES}; a chest's anything {@link #WORLD}; a dummy's
 * {@link #CREATURES}; a song {@link #MUSIC}. Things defined elsewhere (a creative-mode object) name
 * theirs when they {@link SoundKeys#register} their sounds.
 */
public enum SoundCategory {

    MOVEMENT("movement", "Footsteps & movement",
            "Footsteps on each kind of ground, jumping and landing, crouching, the dash and the roll, climbing."),
    VOICES("voices", "Voices",
            "Each body's own: the emotes, being hurt, knocked out or out of breath."),
    WEAPONS("weapons", "Weapons",
            "Every weapon's swings, shots and casts, taking each one in hand, the heavy attack coming back."),
    HITS("hits", "Hits & magic",
            "The damage dice, blows landing, arrows and bolts striking, spells, poison, blocks and parries."),
    CREATURES("creatures", "Creatures",
            "The training dummies and the flying dummies - struck, knocked down, back up, diving at her."),
    WORLD("world", "World & loot",
            "The chests, the loot thrown out of them and its rarity, picking things up and dropping them."),
    AMBIENT("ambient", "Ambient",
            "Sounds of the place itself, looping quietly: the breeze over the meadow, and more to come."),
    MUSIC("music", "Music",
            "The songs, each looping for as long as you are where it plays: the main menu's, and more to come."),
    INTERFACE("interface", "Interface",
            "Menus, the inventory and the wardrobe, the lock-on, screenshots, cutscene lines, the level editor.");

    private final String key;
    private final String label;
    private final String blurb;

    SoundCategory(String key, String label, String blurb) {
        this.key = key;
        this.label = label;
        this.blurb = blurb;
    }

    /** Its name in saved settings: {@code movement}, {@code voices} ... */
    public String key() { return key; }

    /** Its tab's title. */
    public String label() { return label; }

    /** One line on what is in it, under the tab's title. */
    public String blurb() { return blurb; }

    /** The category saved as {@code key}, or null. */
    public static SoundCategory byKey(String key) {
        if (key == null) return null;
        for (SoundCategory c : values()) if (c.key.equalsIgnoreCase(key.trim())) return c;
        return null;
    }
}
