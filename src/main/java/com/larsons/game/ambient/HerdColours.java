package com.larsons.game.ambient;

import java.util.ArrayList;
import java.util.List;

/**
 * The colours the editor offers a herd of each kind: the animal as drawn,
 * and the coats it comes in - a horse bay, chestnut, black, grey, white or
 * palomino; a sheep white, black, brown or grey. A herd given several has
 * each of its animals in one of them, at random ({@link Animals.Herd}); any
 * of the game's 128 colours can be added besides.
 */
public final class HerdColours {

    /** A colour for a herd: what it is called, and its colour ({@code 0xRRGGBB}; -1: as drawn). */
    public record Preset(String name, int rgb) {}

    private HerdColours() {}

    /** The colours {@code k} comes in, as drawn first. */
    public static List<Preset> of(AnimalKind k) {
        List<Preset> out = new ArrayList<>();
        out.add(new Preset(drawn(k) + " (as drawn)", -1));
        switch (k) {
            case HORSE -> add(out, "Bay", 0x7A4A24, "Chestnut", 0xA0522D, "Black", 0x2A2420, "Grey", 0xA8A8A8,
                    "White", 0xECE8E0, "Palomino", 0xD8B060, "Dun", 0xB89868);
            case COW -> add(out, "Brown", 0x8A5A30, "Black", 0x2A2420, "Red", 0xA04028, "Tan", 0xC8A070,
                    "Cream", 0xE8DCC0);
            case SHEEP -> add(out, "Black", 0x302A28, "Brown", 0x6A4A30, "Grey", 0x9A9690, "Cream", 0xE8DCC0);
            case PIG -> add(out, "Black", 0x302A28, "Brown", 0x7A5038, "Tan", 0xC89870, "Grey", 0x8A8480);
            case DOG -> add(out, "Brown", 0x7A4A24, "Black", 0x2A2420, "Golden", 0xD8A050, "White", 0xECE8E0,
                    "Grey", 0x8A8A8A);
            case CAT -> add(out, "Ginger", 0xD07830, "Black", 0x2A2420, "Grey", 0x8A8A90, "White", 0xECE8E0,
                    "Brown", 0x7A5A3A);
            case BEAR -> add(out, "Black", 0x2A2420, "Cinnamon", 0x9A5A2A, "Polar white", 0xF0ECE0, "Grizzly",
                    0x8A6A48);
            case FOX -> add(out, "Silver", 0x9A9AA0, "Arctic white", 0xF0F0EC, "Dark", 0x4A3020);
            case DEER -> add(out, "Red", 0xA0522D, "Grey-brown", 0x8A7660, "Pale", 0xD8C0A0, "White", 0xECE8E0);
            case RABBIT, SQUIRREL -> add(out, "Grey", 0x9A9690, "White", 0xECE8E0, "Black", 0x302A28, "Red",
                    0xB0602A);
            case DUCK, GOOSE, SWAN -> add(out, "White", 0xECE8E0, "Grey", 0x9A9690, "Black", 0x2A2420, "Brown",
                    0x8A6A48);
            default -> add(out, "Blue", 0x3060C0, "Red", 0xC02020, "Green", 0x30A040, "Yellow", 0xE0C020,
                    "White", 0xECE8E0, "Black", 0x2A2420);
        }
        return out;
    }

    /** What {@code k} is as drawn, in a word or two: a cow's "Black and white". */
    static String drawn(AnimalKind k) {
        return switch (k) {
            case HORSE -> "Bay";
            case COW -> "Black and white";
            case SHEEP, SWAN, COCKATOO -> "White";
            case PIG, FLAMINGO -> "Pink";
            case DOG, BEAR -> "Brown";
            case FOX -> "Red";
            case CAT -> "Tabby";
            case DEER -> "Fawn";
            case RABBIT -> "Brown";
            case SQUIRREL -> "Red";
            case GOOSE -> "Grey";
            case DUCK -> "Mallard";
            case CROW -> "Black";
            default -> "Natural";
        };
    }

    private static void add(List<Preset> out, Object... nameRgb) {
        for (int i = 0; i + 1 < nameRgb.length; i += 2) out.add(new Preset((String) nameRgb[i], (Integer) nameRgb[i + 1]));
    }
}
