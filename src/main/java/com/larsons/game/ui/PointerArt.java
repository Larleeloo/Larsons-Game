package com.larsons.game.ui;

import java.awt.image.BufferedImage;

/**
 * The pointer changed by what it is over ({@link Pointers}): the class's
 * pointer lit with a gold rim over a button; with a little badge in its
 * corner over the world - a speech bubble (someone to talk to), crossed
 * swords (a foe), a sack (something to take), a door, a padlock, a coin
 * (something to buy), a hammer (somewhere to build), a brass double arrow (a
 * slider), a red ring (not here) - and pressed in, a pixel down and darker,
 * while the button is held. Drawn as pixel art in the pointers' own scale.
 */
final class PointerArt {

    private PointerArt() {}

    // the badges' colours: the game's palette's inks, parchment, brass and iron
    private static final int OUT = 0xFF1A120C, PARCH = 0xFFF0E4C4, PARCH_D = 0xFFC8B48C, STEEL = 0xFFDCDCE4,
            STEEL_D = 0xFF8C8C9C, BRASS = 0xFFD8A040, BRASS_D = 0xFF946420, BRASS_L = 0xFFF8D888, WOOD = 0xFF8A5428,
            WOOD_D = 0xFF5A3418, SACK = 0xFFA87840, SACK_D = 0xFF6E4822, IRON = 0xFF6A6870, RED = 0xFFD83C30,
            RED_D = 0xFF8A1C14, GLOW = 0xFFFFD87A;

    /** {@code base} (32 or 64 pixels) changed for {@code hint}; {@code s} is its scale (1, or 2 for @2x). */
    static BufferedImage compose(BufferedImage base, String hint, boolean pressed, int s) {
        if (base == null) return null;
        int w = base.getWidth(), h = base.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        String b = hint == null ? "" : hint;
        int[] px = base.getRGB(0, 0, w, h, null, 0, w);
        boolean lit = !b.isEmpty() && !b.equals("no");
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = px[y * w + x];
                if ((c >>> 24) == 0) continue;
                out.setRGB(x, y, lit ? shade(c, 1.12f) : c);
            }
        }
        if (lit) {
            // a gold rim round it: every clear pixel beside its edge
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((px[y * w + x] >>> 24) != 0) continue;
                    if (solid(px, w, h, x - 1, y) || solid(px, w, h, x + 1, y) || solid(px, w, h, x, y - 1)
                            || solid(px, w, h, x, y + 1)) {
                        out.setRGB(x, y, (0xB4 << 24) | (GLOW & 0xFFFFFF));
                    }
                }
            }
        }
        BufferedImage badge = badge(b);
        if (badge != null) {
            // in the bottom right corner, clear of the point
            int bx = w - badge.getWidth() * s, by = h - badge.getHeight() * s;
            for (int y = 0; y < badge.getHeight(); y++) {
                for (int x = 0; x < badge.getWidth(); x++) {
                    int c = badge.getRGB(x, y);
                    if ((c >>> 24) == 0) continue;
                    for (int dy = 0; dy < s; dy++) for (int dx = 0; dx < s; dx++) out.setRGB(bx + x * s + dx, by + y * s + dy, c);
                }
            }
        }
        if (pressed) {
            // pressed in: a pixel down and right, a shade darker
            BufferedImage moved = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = h - 1; y >= s; y--) {
                for (int x = w - 1; x >= s; x--) {
                    int c = out.getRGB(x - s, y - s);
                    if ((c >>> 24) != 0) moved.setRGB(x, y, shade(c, 0.82f));
                }
            }
            out = moved;
        }
        return out;
    }

    private static boolean solid(int[] px, int w, int h, int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && (px[y * w + x] >>> 24) > 0x80;
    }

    private static int shade(int argb, float f) {
        int r = Math.min(255, Math.round(((argb >> 16) & 255) * f));
        int g = Math.min(255, Math.round(((argb >> 8) & 255) * f));
        int b = Math.min(255, Math.round((argb & 255) * f));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** The 15-pixel badge for {@code hint}, or none. */
    static BufferedImage badge(String hint) {
        String[] art = switch (hint) {
            case "talk" -> new String[]{
                    "...............",
                    "..ooooooooooo..",
                    ".oPPPPPPPPPPPo.",
                    ".oPPPPPPPPPPPo.",
                    ".oPPoPPoPPoPPo.",
                    ".oPPoPPoPPoPPo.",
                    ".oPPPPPPPPPPPo.",
                    ".odPPPPPPPPPdo.",
                    "..ooodPPoooo...",
                    "....odPo.......",
                    "....odo........",
                    "....oo.........",
                    "...............",
                    "...............",
                    "..............."};
            case "attack" -> new String[]{
                    "oo...........oo",
                    "oSo.........oSo",
                    ".oSo.......oSo.",
                    "..oSo.....oSo..",
                    "...oSo...oSo...",
                    "....oSo.oSo....",
                    ".....oSoSo.....",
                    "......oSo......",
                    ".....oSoSo.....",
                    "..oo.oo.oo.oo..",
                    "..oBBo...oBBo..",
                    "...oWo...oWo...",
                    "..oWo.....oWo..",
                    ".oBo.......oBo.",
                    ".oo.........oo."};
            case "take" -> new String[]{
                    "...............",
                    ".....ooooo.....",
                    "....oKKKKKo....",
                    ".....oKKKo.....",
                    "....ooBBBoo....",
                    "...oKKKKKKKo...",
                    "..oKKKKKKKKKo..",
                    ".oKKKKKKKKKKKo.",
                    ".oKKKKKLKKKKKo.",
                    ".oKKKKLLLKKKKo.",
                    ".oKKKKKLKKKKKo.",
                    ".oKKKKKKKKKKKo.",
                    "..okKKKKKKKKko.",
                    "...okkkkkkkko..",
                    "....ooooooooo.."};
            case "open" -> new String[]{
                    "....ooooooo....",
                    "...oWWWWWWWo...",
                    "..oWWwWWwWWWo..",
                    "..oWWwWWwWWWo..",
                    "..oWWwWWwWWWo..",
                    "..oWWwWWwWWWo..",
                    "..oWWwWWwWLLo..",
                    "..oWWwWWwWLBo..",
                    "..oWWwWWwWWWo..",
                    "..oWWwWWwWWWo..",
                    "..oWWwWWwWWWo..",
                    "..oWWwWWwWWWo..",
                    "..oWWwWWwWWWo..",
                    "..ooooooooooo..",
                    "..............."};
            case "locked" -> new String[]{
                    "...............",
                    ".....ooooo.....",
                    "....oIIIIIo....",
                    "...oIo...oIo...",
                    "...oIo...oIo...",
                    "...oIo...oIo...",
                    "..ooooooooooo..",
                    "..oLLLLLLLLLo..",
                    "..oBBBBoBBBBo..",
                    "..oBBBoooBBBo..",
                    "..oBBBBoBBBBo..",
                    "..oBBBBoBBBBo..",
                    "..odddddddddo..",
                    "..ooooooooooo..",
                    "..............."};
            case "buy" -> new String[]{
                    "...............",
                    ".....ooooo.....",
                    "...ooLLLLLoo...",
                    "..oLLBBBBBLdo..",
                    "..oLBBdddBBdo..",
                    ".oLBBdBBBBBBdo.",
                    ".oLBdBBBBBBBdo.",
                    ".oLBdBBBBBBBdo.",
                    ".oLBdBBBBBBBdo.",
                    ".oLBBdBBBBBBdo.",
                    "..oLBBdddBBdo..",
                    "..oLdBBBBBddo..",
                    "...oodddddoo...",
                    ".....ooooo.....",
                    "..............."};
            case "build" -> new String[]{
                    "...............",
                    "..oooooooo.....",
                    ".oIIIIIIIIo....",
                    ".oIIIIIIIIIo...",
                    ".oiiiiiiiiio...",
                    "..ooooWWoooo...",
                    ".....oWWo......",
                    ".....oWWo......",
                    ".....oWWo......",
                    ".....oWWo......",
                    ".....oWWo......",
                    ".....owWo......",
                    ".....owWo......",
                    ".....oooo......",
                    "..............."};
            case "slide" -> new String[]{
                    "...............",
                    "...............",
                    "...............",
                    "...o.......o...",
                    "..oLo.....oLo..",
                    ".oLBoooooooBLo.",
                    "oLBBBBBBBBBBBLo",
                    ".oBdoooooooBdo.",
                    "..odo.....odo..",
                    "...o.......o...",
                    "...............",
                    "...............",
                    "...............",
                    "...............",
                    "..............."};
            case "no" -> new String[]{
                    "...............",
                    ".....ooooo.....",
                    "...ooRRRRRoo...",
                    "..oRRoooooRRo..",
                    "..oRRRo...oRo..",
                    ".oRooRRo...oRo.",
                    ".oRo.oRRo..oRo.",
                    ".oRo..oRRo.oRo.",
                    ".oRo...oRRooRo.",
                    ".oRo....oRRRRo.",
                    "..oRo....oRRo..",
                    "..oRRoooooRRo..",
                    "...ooRRRRRoo...",
                    ".....ooooo.....",
                    "..............."};
            default -> null;
        };
        if (art == null) return null;
        BufferedImage img = new BufferedImage(15, 15, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                int c = switch (art[y].charAt(x)) {
                    case 'o' -> OUT;
                    case 'P' -> PARCH;
                    case 'S' -> STEEL;
                    case 'B' -> BRASS;
                    case 'L' -> BRASS_L;
                    case 'd' -> hint.equals("talk") ? PARCH_D : BRASS_D;
                    case 'W' -> WOOD;
                    case 'w' -> WOOD_D;
                    case 'K' -> SACK;
                    case 'k' -> SACK_D;
                    case 'I' -> IRON;
                    case 'i' -> STEEL_D;
                    case 'R' -> RED;
                    case 'r' -> RED_D;
                    default -> 0;
                };
                if (c != 0) img.setRGB(x, y, c);
            }
        }
        return img;
    }
}
