package com.larsons.game.ambient;

import java.util.List;
import java.util.Random;

/**
 * Hunting: which animals are game - hunted down, they fall and leave what
 * they give - and what each gives, by the ids of the items it leaves
 * (world/Hunted): meat to eat, and hides, pelts, wool, antlers and feathers,
 * the ingredients of the crafting to come.
 *
 * <p>Game is every animal with a fall of its own ({@link AnimalKind#falls})
 * but the tame ones - the horse, the dog and the cat, beaten, run off as
 * they always did - and the iguana and the insects, which have no fall.
 *
 * <pre>
 *   cow        raw beef 2-3, cow hide
 *   deer       raw venison 2-3, deer hide, antlers 0-2
 *   pig        raw pork 2-3
 *   sheep      raw mutton 1-2, wool 1-3
 *   bear       bear meat 2-4, bear pelt
 *   fox        raw game, fox pelt
 *   rabbit     raw game, rabbit pelt
 *   squirrel   raw game
 *   the birds  raw fowl (the bigger ones) and feathers - an eagle's, a
 *              peacock's, a macaw's and a flamingo's their own
 * </pre>
 */
public final class Hunting {

    /** One thing an animal leaves: {@code min} to {@code max} of the item {@code item}. */
    public record Drop(String item, int min, int max) {

        /** How many it leaves this time. */
        public int roll(Random random) {
            return min + (max > min ? random.nextInt(max - min + 1) : 0);
        }
    }

    // the items' ids (world/Hunted)
    public static final String RAW_BEEF = "raw_beef", RAW_VENISON = "raw_venison", RAW_PORK = "raw_pork",
            RAW_MUTTON = "raw_mutton", BEAR_MEAT = "bear_meat", RAW_GAME = "raw_game", RAW_FOWL = "raw_fowl",
            COW_HIDE = "cow_hide", DEER_HIDE = "deer_hide", BEAR_PELT = "bear_pelt", FOX_PELT = "fox_pelt",
            RABBIT_PELT = "rabbit_pelt", WOOL = "wool", ANTLER = "antler", FEATHER = "feather",
            EAGLE_FEATHER = "eagle_feather", PEACOCK_FEATHER = "peacock_feather", MACAW_FEATHER = "macaw_feather",
            FLAMINGO_FEATHER = "flamingo_feather";

    private Hunting() {}

    /** Whether {@code k} is game: hunted down, it falls and leaves its {@link #drops}. */
    public static boolean game(AnimalKind k) {
        return k.falls() && k != AnimalKind.HORSE && k != AnimalKind.DOG && k != AnimalKind.CAT;
    }

    /** What {@code k} leaves when it is hunted down (nothing when it is not game). */
    public static List<Drop> drops(AnimalKind k) {
        if (!game(k)) return List.of();
        return switch (k) {
            case COW -> List.of(new Drop(RAW_BEEF, 2, 3), new Drop(COW_HIDE, 1, 1));
            case DEER -> List.of(new Drop(RAW_VENISON, 2, 3), new Drop(DEER_HIDE, 1, 1), new Drop(ANTLER, 0, 2));
            case PIG -> List.of(new Drop(RAW_PORK, 2, 3));
            case SHEEP -> List.of(new Drop(RAW_MUTTON, 1, 2), new Drop(WOOL, 1, 3));
            case BEAR -> List.of(new Drop(BEAR_MEAT, 2, 4), new Drop(BEAR_PELT, 1, 1));
            case FOX -> List.of(new Drop(RAW_GAME, 1, 1), new Drop(FOX_PELT, 1, 1));
            case RABBIT -> List.of(new Drop(RAW_GAME, 1, 1), new Drop(RABBIT_PELT, 1, 1));
            case SQUIRREL -> List.of(new Drop(RAW_GAME, 1, 1));
            case DUCK, PHEASANT -> List.of(new Drop(RAW_FOWL, 1, 2), new Drop(FEATHER, 1, 3));
            case GOOSE -> List.of(new Drop(RAW_FOWL, 2, 2), new Drop(FEATHER, 1, 3));
            case SWAN -> List.of(new Drop(RAW_FOWL, 2, 3), new Drop(FEATHER, 2, 4));
            case EAGLE -> List.of(new Drop(RAW_FOWL, 1, 1), new Drop(EAGLE_FEATHER, 1, 3));
            case PEACOCK -> List.of(new Drop(RAW_FOWL, 1, 1), new Drop(PEACOCK_FEATHER, 2, 4));
            case MACAW -> List.of(new Drop(MACAW_FEATHER, 1, 3));
            case FLAMINGO -> List.of(new Drop(RAW_FOWL, 1, 1), new Drop(FLAMINGO_FEATHER, 1, 3));
            case CROW, OWL -> List.of(new Drop(FEATHER, 1, 3));
            default -> List.of(new Drop(FEATHER, 1, 2));        // the small birds
        };
    }
}
