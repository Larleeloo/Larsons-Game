package com.larsons.game.actor;

import java.util.List;
import java.util.Locale;

/**
 * The peoples of the moon: who a character can be of - the player's own, the
 * people she meets and the foes she fights. Each has its skin tones (the
 * body's colour options, {@code variants.json}), its ears, how tall its folk
 * stand beside a human, and whether it grows horns and a tail.
 *
 * <ul>
 *   <li><b>Human</b> - the townsfolk of the main lands.</li>
 *   <li><b>Drurinean</b> - tall, long-eared scholars and magic users in
 *       elegant clothes; their skin runs from a human's to light purple.</li>
 *   <li><b>Half Drurinean</b> - of both: a human's build, the ears a little
 *       pointed.</li>
 *   <li><b>Khorigale</b> - horned, long-tailed and a little smaller, yellow,
 *       green or pink of skin, in common clothes.</li>
 *   <li><b>Bortzal</b> - goblin yellow or ashy purple, from half her height
 *       to half as tall again, in rags, with clubs and rough bows. Foes only.</li>
 *   <li><b>The risen</b> - the horde mode's dead. Foes only.</li>
 * </ul>
 */
public enum People {

    HUMAN("human", "Human", "The townsfolk of the main lands: farmers, smiths and soldiers.",
            List.of("copper", "honey_blonde", "platinum", "auburn", "brown", "black"), List.of("round"),
            0.9, 1.1, false, false, true),
    DRURINEAN("drurinean", "Drurinean", "Tall, long-eared scholars: magic users and scientists in elegant clothes.",
            List.of("platinum", "honey_blonde", "pink", "violet_ombre"), List.of("elven"),
            1.0, 1.15, false, false, true),
    HALF_DRURINEAN("half_drurinean", "Half Drurinean", "Of both peoples: a human's build, the ears a little pointed.",
            List.of("copper", "honey_blonde", "platinum", "auburn", "pink"), List.of("pointed"),
            0.92, 1.1, false, false, true),
    KHORIGALE("khorigale", "Khorigale", "Horned and long-tailed, a little smaller than a human, in common clothes.",
            List.of("honey_blonde", "emerald", "mint", "pink"), List.of("pointed"),
            0.8, 0.95, true, true, true),
    BORTZAL("bortzal", "Bortzal", "Goblin yellow or ashy purple, of every size, in rags, with clubs and rough bows.",
            List.of("honey_blonde", "violet_ombre"), List.of("pointed", "elven"),
            0.5, 1.5, false, false, false),
    RISEN("risen", "Risen", "The dead of the horde.",
            List.of("mint", "green_ombre", "platinum", "emerald"), List.of("round", "pointed"),
            0.85, 1.15, false, false, false);

    /** The horns and the tail a Khorigale wears: the items of the {@code horns} and {@code tail} layers. */
    public static final String HORNS = "khorigale_horns", TAIL = "khorigale_tail";

    private final String key, label, about;
    private final List<String> skins, ears;
    private final double smallest, tallest;
    private final boolean horns, tail, playable;

    People(String key, String label, String about, List<String> skins, List<String> ears, double smallest,
           double tallest, boolean horns, boolean tail, boolean playable) {
        this.key = key;
        this.label = label;
        this.about = about;
        this.skins = skins;
        this.ears = ears;
        this.smallest = smallest;
        this.tallest = tallest;
        this.horns = horns;
        this.tail = tail;
        this.playable = playable;
    }

    public String key() { return key; }

    public String label() { return label; }

    public String about() { return about; }

    /** Its skin tones: colour options of the body, its most usual first. */
    public List<String> skins() { return skins; }

    /** The ears its folk have: items of the ears' layer, the usual first. */
    public List<String> ears() { return ears; }

    /** How tall its folk usually stand, times a human's height: the least and the most. */
    public double smallest() { return smallest; }

    public double tallest() { return tallest; }

    public boolean horns() { return horns; }

    public boolean tail() { return tail; }

    /** Whether the player can be one. */
    public boolean playable() { return playable; }

    /** The keys of the peoples she can be one of - and the people of a town are. */
    public static java.util.List<String> playableKeys() {
        return java.util.Arrays.stream(values()).filter(People::playable).map(People::key).toList();
    }

    public static People byKey(String key) {
        if (key != null) {
            String k = key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            for (People p : values()) if (p.key.equals(k)) return p;
        }
        return HUMAN;
    }

    /** The peoples the player can be. */
    public static List<People> playableOnes() {
        return java.util.Arrays.stream(values()).filter(People::playable).toList();
    }
}
