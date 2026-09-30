package com.larsons.game.sprite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sprite-sheet file names: the one canonical form the game stores, and a
 * forgiving reader for whatever a Blender export happened to be called.
 *
 * <p><b>Canonical:</b> {@code <state>_<elevation>_<direction>.png} inside
 * {@code assets/sprites/<slot>/<item>/} — for example
 * {@code assets/sprites/body/hero/walk_middle_ne.png}.
 *
 * <p><b>Accepted</b> by the drag-and-drop importer (and by the folder scan):
 * any order, any separator ({@code _ - . space /}) or CamelCase, upper or
 * lower case, and common synonyms — {@code Walk-45-NorthEast.png},
 * {@code hero/Run/Top/S.png}, {@code idle_side_front_0007.png} (one frame of a
 * sequence, to be stitched into a sheet). Elevations may be written as
 * {@code side/middle/top}, {@code low/mid/high} or {@code 0/45/90}; directions
 * as {@code n ne e …}, {@code north northeast …}, or {@code front back left
 * right}.
 */
public final class SpriteNames {

    private SpriteNames() {}

    /** What a path said about itself. Any field may be {@code null}. */
    public record Parsed(Slot slot, AnimState state, Elevation elevation, Facing facing,
                         Integer frame, List<String> leftovers) {

        /** Whether the name identifies one sheet (state, elevation and direction all known). */
        public boolean complete() {
            return state != null && elevation != null && facing != null;
        }

        /** The canonical file name, or {@code null} if incomplete. */
        public String canonical() {
            return complete() ? fileName(state, elevation, facing) : null;
        }
    }

    /** {@code walk_middle_ne.png}. */
    public static String fileName(AnimState state, Elevation elevation, Facing facing) {
        return state.key() + "_" + elevation.key() + "_" + facing.key() + ".png";
    }

    private static final Map<String, AnimState> STATES = Map.ofEntries(
            Map.entry("idle", AnimState.IDLE), Map.entry("idling", AnimState.IDLE),
            Map.entry("stand", AnimState.IDLE), Map.entry("standing", AnimState.IDLE),
            Map.entry("walk", AnimState.WALK), Map.entry("walking", AnimState.WALK),
            Map.entry("run", AnimState.RUN), Map.entry("running", AnimState.RUN),
            Map.entry("jog", AnimState.RUN), Map.entry("jogging", AnimState.RUN),
            Map.entry("sprint", AnimState.SPRINT), Map.entry("sprinting", AnimState.SPRINT),
            Map.entry("dash", AnimState.SPRINT),
            Map.entry("jump", AnimState.JUMP), Map.entry("jumping", AnimState.JUMP),
            Map.entry("attack", AnimState.ATTACK), Map.entry("attacking", AnimState.ATTACK),
            Map.entry("atk", AnimState.ATTACK), Map.entry("slash", AnimState.ATTACK),
            Map.entry("swing", AnimState.ATTACK), Map.entry("strike", AnimState.ATTACK));

    private static final Map<String, Elevation> ELEVATIONS = Map.ofEntries(
            Map.entry("side", Elevation.SIDE), Map.entry("low", Elevation.SIDE),
            Map.entry("level", Elevation.SIDE), Map.entry("0deg", Elevation.SIDE),
            Map.entry("deg0", Elevation.SIDE), Map.entry("elev0", Elevation.SIDE),
            Map.entry("middle", Elevation.MIDDLE), Map.entry("mid", Elevation.MIDDLE),
            Map.entry("medium", Elevation.MIDDLE), Map.entry("45deg", Elevation.MIDDLE),
            Map.entry("deg45", Elevation.MIDDLE), Map.entry("elev45", Elevation.MIDDLE),
            Map.entry("threequarter", Elevation.MIDDLE), Map.entry("iso", Elevation.MIDDLE),
            Map.entry("isometric", Elevation.MIDDLE),
            Map.entry("top", Elevation.TOP), Map.entry("high", Elevation.TOP),
            Map.entry("topdown", Elevation.TOP), Map.entry("overhead", Elevation.TOP),
            Map.entry("birdseye", Elevation.TOP), Map.entry("birds", Elevation.TOP),
            Map.entry("90deg", Elevation.TOP), Map.entry("deg90", Elevation.TOP),
            Map.entry("elev90", Elevation.TOP), Map.entry("above", Elevation.TOP));

    private static final Map<String, Facing> FACINGS = Map.ofEntries(
            Map.entry("n", Facing.NORTH), Map.entry("north", Facing.NORTH),
            Map.entry("back", Facing.NORTH), Map.entry("away", Facing.NORTH),
            Map.entry("s", Facing.SOUTH), Map.entry("south", Facing.SOUTH),
            Map.entry("front", Facing.SOUTH), Map.entry("toward", Facing.SOUTH),
            Map.entry("e", Facing.EAST), Map.entry("east", Facing.EAST),
            Map.entry("right", Facing.EAST),
            Map.entry("w", Facing.WEST), Map.entry("west", Facing.WEST),
            Map.entry("left", Facing.WEST),
            Map.entry("ne", Facing.NORTH_EAST), Map.entry("northeast", Facing.NORTH_EAST),
            Map.entry("nw", Facing.NORTH_WEST), Map.entry("northwest", Facing.NORTH_WEST),
            Map.entry("se", Facing.SOUTH_EAST), Map.entry("southeast", Facing.SOUTH_EAST),
            Map.entry("sw", Facing.SOUTH_WEST), Map.entry("southwest", Facing.SOUTH_WEST));

    private static final Map<String, Slot> SLOTS = Map.ofEntries(
            Map.entry("body", Slot.BODY), Map.entry("base", Slot.BODY),
            Map.entry("character", Slot.BODY), Map.entry("player", Slot.BODY),
            Map.entry("underwear", Slot.UNDERWEAR), Map.entry("briefs", Slot.UNDERWEAR),
            Map.entry("boxers", Slot.UNDERWEAR),
            Map.entry("bra", Slot.BRA), Map.entry("bras", Slot.BRA),
            Map.entry("pants", Slot.PANTS), Map.entry("trousers", Slot.PANTS),
            Map.entry("jeans", Slot.PANTS), Map.entry("shorts", Slot.PANTS),
            Map.entry("shirt", Slot.SHIRT), Map.entry("shirts", Slot.SHIRT),
            Map.entry("tshirt", Slot.SHIRT), Map.entry("jacket", Slot.SHIRT),
            Map.entry("coat", Slot.SHIRT), Map.entry("tunic", Slot.SHIRT),
            Map.entry("shoes", Slot.SHOES), Map.entry("shoe", Slot.SHOES),
            Map.entry("boots", Slot.SHOES), Map.entry("boot", Slot.SHOES),
            Map.entry("hair", Slot.HAIR), Map.entry("hairs", Slot.HAIR),
            Map.entry("hairstyle", Slot.HAIR),
            Map.entry("nose", Slot.NOSE), Map.entry("noses", Slot.NOSE),
            Map.entry("eyes", Slot.EYES), Map.entry("eye", Slot.EYES),
            Map.entry("mouth", Slot.MOUTH), Map.entry("mouths", Slot.MOUTH),
            Map.entry("lips", Slot.MOUTH),
            Map.entry("ears", Slot.EARS), Map.entry("ear", Slot.EARS),
            Map.entry("earrings", Slot.EARRINGS), Map.entry("earring", Slot.EARRINGS),
            Map.entry("wristwear", Slot.WRISTWEAR), Map.entry("wrist", Slot.WRISTWEAR),
            Map.entry("watch", Slot.WRISTWEAR), Map.entry("bracelet", Slot.WRISTWEAR),
            Map.entry("gloves", Slot.GLOVES), Map.entry("glove", Slot.GLOVES),
            Map.entry("hat", Slot.HAT), Map.entry("hats", Slot.HAT),
            Map.entry("cap", Slot.HAT), Map.entry("helmet", Slot.HAT),
            Map.entry("other", Slot.OTHER), Map.entry("misc", Slot.OTHER),
            Map.entry("accessory", Slot.OTHER),
            Map.entry("belt", Slot.BELT), Map.entry("belts", Slot.BELT),
            Map.entry("necklace", Slot.NECKLACE), Map.entry("necklaces", Slot.NECKLACE),
            Map.entry("pendant", Slot.NECKLACE), Map.entry("chain", Slot.NECKLACE),
            Map.entry("sheath", Slot.SHEATH), Map.entry("sheaths", Slot.SHEATH),
            Map.entry("scabbard", Slot.SHEATH),
            Map.entry("eyebrows", Slot.EYEBROWS), Map.entry("eyebrow", Slot.EYEBROWS),
            Map.entry("brows", Slot.EYEBROWS), Map.entry("brow", Slot.EYEBROWS),
            Map.entry("makeup", Slot.MAKEUP),
            Map.entry("lefthand", Slot.CARRY_LEFT), Map.entry("righthand", Slot.CARRY_RIGHT));

    private static final Pattern SPLIT = Pattern.compile("[\\\\/._\\-\\s]+");
    private static final Pattern CAMEL = Pattern.compile(
            "(?<=[a-z])(?=[A-Z])|(?<=[A-Za-z])(?=[0-9])|(?<=[0-9])(?=[A-Za-z])");

    /** Lower-case tokens of a path: separators and CamelCase both split. */
    static List<String> tokens(String path) {
        List<String> out = new ArrayList<>();
        String noExt = path.replaceAll("(?i)\\.(png|jpe?g|gif|bmp)$", "");
        for (String part : SPLIT.split(noExt)) {
            if (part.isEmpty()) continue;
            for (String t : CAMEL.split(part)) {
                if (!t.isEmpty()) out.add(t.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** Read whatever a path says about the sheet it holds. */
    public static Parsed parse(String path) {
        List<String> t = tokens(path);
        Slot slot = null;
        AnimState state = null;
        Elevation elevation = null;
        Facing facing = null;
        Integer frame = null;
        List<String> leftovers = new ArrayList<>();
        List<String> numbers = new ArrayList<>();

        for (int i = 0; i < t.size(); i++) {
            String a = t.get(i);
            String b = i + 1 < t.size() ? t.get(i + 1) : "";

            // Two-token phrases first, so "left hand" is a slot and not a direction.
            Slot handSlot = hand(a, b);
            if (handSlot != null) {
                slot = handSlot;
                i++;
                continue;
            }
            if (a.equals("birds") && b.equals("eye")) {
                elevation = Elevation.TOP;
                i++;
                continue;
            }
            if (a.equals("three") && b.equals("quarter")) {
                elevation = Elevation.MIDDLE;
                i++;
                continue;
            }
            if (a.equals("top") && b.equals("down")) {
                elevation = Elevation.TOP;
                i++;
                continue;
            }
            Facing diagonal = diagonal(a, b);
            if (diagonal != null && facing == null) {
                facing = diagonal;
                i++;
                continue;
            }

            if (a.chars().allMatch(Character::isDigit)) {
                numbers.add(a);
            } else if (STATES.containsKey(a) && state == null) {
                state = STATES.get(a);
            } else if (ELEVATIONS.containsKey(a) && elevation == null) {
                elevation = ELEVATIONS.get(a);
            } else if (FACINGS.containsKey(a) && facing == null) {
                facing = FACINGS.get(a);
            } else if (SLOTS.containsKey(a) && slot == null) {
                slot = SLOTS.get(a);
            } else if (a.equals("deg") || a.equals("frame") || a.equals("f")) {
                // noise between an angle or frame number and its unit
            } else {
                leftovers.add(a);
            }
        }

        // Numbers last: a bare 0/45/90 is the elevation if nothing else named
        // it; anything else (0001, 12) is a frame of a sequence.
        for (String num : numbers) {
            int v;
            try {
                v = Integer.parseInt(num);
            } catch (NumberFormatException e) {
                continue;
            }
            if (elevation == null && num.length() <= 2 && (v == 0 || v == 45 || v == 90)) {
                elevation = v == 0 ? Elevation.SIDE : v == 45 ? Elevation.MIDDLE : Elevation.TOP;
            } else {
                frame = v;
            }
        }
        return new Parsed(slot, state, elevation, facing, frame, leftovers);
    }

    private static Slot hand(String a, String b) {
        boolean carry = a.equals("carry") || a.equals("held") || a.equals("hand");
        if (carry && b.equals("left")) return Slot.CARRY_LEFT;
        if (carry && b.equals("right")) return Slot.CARRY_RIGHT;
        if (b.equals("hand") && a.equals("left")) return Slot.CARRY_LEFT;
        if (b.equals("hand") && a.equals("right")) return Slot.CARRY_RIGHT;
        return null;
    }

    /** "north east", "front left", "e n" and the like, as one diagonal. */
    private static Facing diagonal(String a, String b) {
        Facing fa = FACINGS.get(a), fb = FACINGS.get(b);
        if (fa == null || fb == null) return null;
        int dx = fa.dx() + fb.dx(), dy = fa.dy() + fb.dy();
        boolean one = (fa.dx() == 0) != (fb.dx() == 0);
        if (!one || Math.abs(dx) != 1 || Math.abs(dy) != 1) return null;
        return Facing.of(dx, dy, null);
    }

    /**
     * A folder-safe item name: lower case, words joined by underscores, only
     * {@code a-z 0-9 _ -}. Blank becomes {@code "item"}.
     */
    public static String sanitize(String name) {
        String s = name == null ? "" : name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]+", "_").replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return s.isEmpty() ? "item" : s;
    }
}
