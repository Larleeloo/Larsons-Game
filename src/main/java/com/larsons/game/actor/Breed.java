package com.larsons.game.actor;

import java.util.List;
import java.util.Locale;

/**
 * The kinds of folk she fights: what each is, how it is dressed, how much it
 * takes and gives, how fast it comes and how far it sees - and the weapons
 * its kind favours. Any of the humanoid ones can be handed any weapon in
 * Creative Mode (a Drurinean with a battle axe, a Khorigale with a wand);
 * the Bortzal, which are not people, keep to their clubs and rough bows.
 *
 * <pre>
 *   Bortzal          a mad scientist's brutes: half her height to half as tall again
 *   Drurinean        magic users: wands and staffs, from a distance
 *   Half Drurinean   tinkerers: crossbows, wands, a blade
 *   Khorigale        raiders: maces, daggers, axes, bows; they guard
 *   Bandit           the roads' own: swords, daggers, bows
 *   Risen walker     the horde: slow, many
 *   Risen runner     the horde: fast, frail
 *   Risen brute      the horde: huge, in a scrap of plate, never staggered
 *   Risen crawler    the horde: dragging itself on its hands, hard to see in the grass
 *   Risen ghoul      the horde: a shrouded one that lunges, quick to claw again
 *   Risen Bortzal    the horde: a dead brute, its club gone, beating with its fists
 * </pre>
 *
 * <p>The Risen are creatures of their own (3D-Modeling creatures/risen.py:
 * {@code risen_m1} .. {@code risen_f3}, {@code risen_brute}, the crawlers,
 * the dead Bortzal), never in the character's sheets, and nothing in their
 * hands but their hands: no bows, no spit, no weapons.
 *
 * @param health   what it takes
 * @param walk     how fast it goes about, metres a second
 * @param run      how fast it comes for her
 * @param damage   what its blow takes, before its weapon's and its size's say
 * @param sight    how far off it notices her in the open, metres
 * @param every    seconds between its blows, before its weapon's say
 * @param guard    how likely it is to put up a guard after a blow (0 - 1)
 * @param weapons  the weapons its kind favours (item ids; none: bare hands)
 * @param xp       experience for felling one
 */
public enum Breed {
    BORTZAL("bortzal", "Bortzal", People.BORTZAL, Folk.Role.FOE, Looks.Dress.RAGGED, 45, 1.2, 3.4, 9, 16, 1.7, 0,
            List.of(), 25, 0.5, 1.5),
    DRURINEAN("drurinean", "Drurinean", People.DRURINEAN, Folk.Role.FOE, Looks.Dress.ELEGANT, 40, 1.5, 3.4, 8, 20,
            2.0, 0.15, List.of("wand", "crystal_staff", "wand", "dagger"), 30, 0, 0),
    HALF_DRURINEAN("half_drurinean", "Half Drurinean", People.HALF_DRURINEAN, Folk.Role.FOE, Looks.Dress.WORKSHOP,
            45, 1.6, 3.8, 9, 18, 1.8, 0.2, List.of("crossbow", "wand", "sword", "mace"), 30, 0, 0),
    KHORIGALE("khorigale", "Khorigale", People.KHORIGALE, Folk.Role.FOE, Looks.Dress.COMMON, 50, 1.6, 4.0, 10, 16,
            1.5, 0.3, List.of("mace", "dagger", "battle_axe", "longbow", "sword"), 28, 0, 0),
    BANDIT("bandit", "Bandit", People.HUMAN, Folk.Role.FOE, Looks.Dress.COMMON, 45, 1.6, 3.9, 9, 17, 1.6, 0.2,
            List.of("sword", "dagger", "longbow", "battle_axe", "crossbow"), 25, 0, 0),
    RISEN_WALKER("risen_walker", "Risen walker", People.RISEN, Folk.Role.RISEN, Looks.Dress.RAGGED, 30, 0.8, 1.5, 8,
            40, 1.5, 0, List.of(), 10, 0, 0),
    RISEN_RUNNER("risen_runner", "Risen runner", People.RISEN, Folk.Role.RISEN, Looks.Dress.RAGGED, 22, 1.4, 4.6, 7,
            40, 1.2, 0, List.of(), 12, 0.85, 1.0),
    RISEN_BRUTE("risen_brute", "Risen brute", People.RISEN, Folk.Role.RISEN, Looks.Dress.ARMOURED, 140, 0.8, 2.0, 18,
            40, 2.2, 0, List.of(), 40, 1.25, 1.4),
    RISEN_CRAWLER("risen_crawler", "Risen crawler", People.RISEN, Folk.Role.RISEN, Looks.Dress.RAGGED, 18, 0.9, 1.9, 6,
            40, 1.1, 0, List.of(), 10, 0.8, 1.0),
    RISEN_SPITTER("risen_spitter", "Risen ghoul", People.RISEN, Folk.Role.RISEN, Looks.Dress.RAGGED, 26, 1.0, 3.2,
            8, 40, 1.2, 0, List.of(), 16, 0.9, 1.05),
    RISEN_BORTZAL("risen_bortzal", "Risen Bortzal", People.BORTZAL, Folk.Role.RISEN, Looks.Dress.RAGGED, 70, 0.9, 2.4,
            13, 40, 1.9, 0, List.of(), 24, 0.9, 1.5);

    private final String key, label;
    private final People people;
    private final Folk.Role role;
    private final Looks.Dress dress;
    private final double health, walk, run, damage, sight, every, guard, smallest, tallest;
    private final List<String> weapons;
    private final int xp;

    Breed(String key, String label, People people, Folk.Role role, Looks.Dress dress, double health, double walk,
          double run, double damage, double sight, double every, double guard, List<String> weapons, int xp,
          double smallest, double tallest) {
        this.key = key;
        this.label = label;
        this.people = people;
        this.role = role;
        this.dress = dress;
        this.health = health;
        this.walk = walk;
        this.run = run;
        this.damage = damage;
        this.sight = sight;
        this.every = every;
        this.guard = guard;
        this.weapons = weapons;
        this.xp = xp;
        this.smallest = smallest > 0 ? smallest : people.smallest();
        this.tallest = tallest > 0 ? tallest : people.tallest();
    }

    public String key() { return key; }

    public String label() { return label; }

    public People people() { return people; }

    public Folk.Role role() { return role; }

    public Looks.Dress dress() { return dress; }

    public double health() { return health; }

    public double walk() { return walk; }

    public double run() { return run; }

    public double damage() { return damage; }

    public double sight() { return sight; }

    public double every() { return every; }

    public double guard() { return guard; }

    public List<String> weapons() { return weapons; }

    public int xp() { return xp; }

    /** How small and how tall its kind comes, times a person's usual height. */
    public double smallest() { return smallest; }

    public double tallest() { return tallest; }

    /** Whether it is drawn as a creature of its own (the Bortzal, the Risen) rather than in the character's sheets. */
    public boolean creature() { return people == People.BORTZAL || role == Folk.Role.RISEN; }

    /**
     * The creature sheets a Risen of this breed is drawn with ({@code
     * assets/sprites/objects/<name>}), one picked for each; empty for the rest.
     */
    public List<String> risenSprites(boolean masculine) {
        return switch (this) {
            case RISEN_WALKER, RISEN_RUNNER -> masculine ? List.of("risen_m1", "risen_m3", "risen_m2")
                    : List.of("risen_f1", "risen_f3", "risen_f2");
            case RISEN_SPITTER -> List.of(masculine ? "risen_m2" : "risen_f2");
            case RISEN_BRUTE -> List.of("risen_brute");
            case RISEN_CRAWLER -> List.of(masculine ? "risen_crawler_m" : "risen_crawler_f");
            case RISEN_BORTZAL -> List.of(masculine ? "risen_bortzal_m" : "risen_bortzal_f");
            default -> List.of();
        };
    }

    /** Whether it is one of the horde. */
    public boolean risen() { return role == Folk.Role.RISEN; }

    /** Whether it never rocks from a blow. */
    public boolean steady() { return this == RISEN_BRUTE; }

    /** Whether it goes on all fours. */
    public boolean crawls() { return this == RISEN_CRAWLER; }

    /** Whether it spits from a distance: none does any more (the Risen fight with their hands alone). */
    public boolean spits() { return false; }

    public static Breed byKey(String key) {
        if (key != null) {
            String k = key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            for (Breed b : values()) if (b.key.equals(k) || b.label.equalsIgnoreCase(key.trim())) return b;
        }
        return null;
    }

    public static List<String> keys() {
        return java.util.Arrays.stream(values()).map(Breed::key).toList();
    }

    /** The living ones: what a summoning station and a level's foes are chosen from. */
    public static List<Breed> living() {
        return java.util.Arrays.stream(values()).filter(b -> !b.risen()).toList();
    }

    /** The horde's. */
    public static List<Breed> horde() {
        return java.util.Arrays.stream(values()).filter(Breed::risen).toList();
    }
}
