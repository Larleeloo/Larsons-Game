package com.larsons.game.audio;

import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.ItemDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Every place the game makes a sound, and the file a {@link SoundPack} expects
 * for it — the engine's {@code SoundKeys}, with the game's own catalogue.
 *
 * <p>A sound key names an <em>object</em> and an <em>action state</em>, and
 * every animation is one: a body's 56 states, the chest's four, what is
 * done with an item, the inventory.
 *
 * <pre>
 *   player/feminine/walk           -&gt; player/feminine/walk.mp3   (her walk)
 *   player/masculine/axe_attack    -&gt; player/masculine/axe_attack.mp3
 *   chest/ornate_chest/open        -&gt; chests/ornate_chest/open.mp3
 *   item/battle_axe/pickup         -&gt; items/battle_axe/pickup.mp3
 *   ui/inventory_open              -&gt; ui/inventory_open.mp3
 * </pre>
 *
 * <p>Keys fall back from the specific to the general, as the engine's do:
 * {@code player/masculine/walk} is {@code player/masculine/walk.mp3} if there
 * is one, else {@code player/walk.mp3} - one file for both bodies' walks,
 * until a body is given its own. A chest's sound falls back to every chest's
 * ({@code chests/open.mp3}), an item's to every item's ({@code
 * items/pickup.mp3}). Where the engine flattens a key into one file name per
 * family folder ({@code mobs/slime_attack.wav}), the game gives an object a
 * folder of its own - a body has 56 states - so the files of one body or
 * chest sit together.
 *
 * <p>Every one of these defaults to <b>silence</b>: nothing makes a sound
 * until a WAV or MP3 with its name is dropped into the pack.
 */
public final class SoundKeys {

    // Folder names inside the pack, one per family of sounds.
    public static final String PLAYER = "player";
    public static final String CHESTS = "chests";
    public static final String ITEMS = "items";
    public static final String UI = "ui";
    /** Where a key from an unrecognised namespace lands. */
    public static final String OTHER = "other";

    /** The bodies a player can be drawn as - each has a folder of its own for its animations' sounds. */
    public static final List<String> BODIES = List.of("feminine", "masculine");
    /** The body a character without a drawn body (the generated stand-in) sounds as. */
    public static final String DEFAULT_BODY = "feminine";

    /** The chests in the world (their sprite folder names under {@code assets/sprites/objects/}). */
    public static final List<String> CHESTS_IN_GAME = List.of("ornate_chest");

    /**
     * A chest's states: shut with its smoke swirling ({@code idle}, held),
     * the lid thrown open ({@code open}), standing open and lit ({@code
     * opened}, held), the lid slammed ({@code close}).
     */
    public static final List<String> CHEST_STATES = List.of("idle", "open", "opened", "close");

    /** What can be done with an item: picked up, put down, taken in hand from the hotbar. */
    public static final List<String> ITEM_STATES = List.of("pickup", "drop", "equip");

    /** The inventory: opened and closed with I, and a hotbar slot chosen. */
    public static final List<String> UI_SOUNDS = List.of("inventory_open", "inventory_close", "hotbar_select");

    /**
     * One catalogue row: the object, the pack folder its sounds belong in,
     * the sound key, the file to name the audio (relative to the pack, no
     * extension), and the action state the key is for. {@code category}
     * groups rows in the generated key list.
     */
    public record Entry(String category, String folder, String key, String file,
                        String name, String state) {}

    private SoundKeys() {}

    /** Every pack folder, in the order the key list documents them. */
    public static List<String> folders() {
        List<String> out = new ArrayList<>();
        for (String body : BODIES) out.add(PLAYER + "/" + body);
        for (String chest : CHESTS_IN_GAME) out.add(CHESTS + "/" + chest);
        for (ItemDef d : ItemDef.ALL) out.add(ITEMS + "/" + d.id());
        out.add(UI);
        return out;
    }

    /**
     * The relative pack paths (no extension) a sound key accepts, most
     * specific first: the object's own file, then the family's file for the
     * state - {@code player/feminine/walk} before {@code player/walk}.
     */
    public static List<String> paths(String key) {
        if (key == null || key.isBlank()) return List.of();
        String[] parts = key.split("/");
        String family = switch (parts[0]) {
            case "player" -> PLAYER;
            case "chest" -> CHESTS;
            case "item" -> ITEMS;
            case "ui" -> UI;
            default -> null;
        };
        if (family == null) return List.of(OTHER + "/" + key.replace('/', '_'));
        if (parts.length >= 3) {
            String object = join(parts, 1, parts.length - 1, "/");
            String state = parts[parts.length - 1];
            return List.of(family + "/" + object + "/" + state, family + "/" + state);
        }
        return List.of(family + "/" + join(parts, 1, parts.length, "_"));
    }

    /** The file a creator should drop in for {@code key} (the preferred path), e.g. {@code player/feminine/walk.mp3}. */
    public static String preferredFile(String key) {
        List<String> paths = paths(key);
        return paths.isEmpty() ? "" : paths.get(0) + ".mp3";
    }

    /** The whole catalogue, grouped by category and ordered the way the key list documents it. */
    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        for (String body : BODIES) {
            String category = "Player (" + body + ")";
            for (AnimState s : AnimState.values()) {
                String key = player(body, s);
                out.add(new Entry(category, PLAYER + "/" + body, key, paths(key).get(0), s.label(), s.key()));
            }
        }
        for (String chest : CHESTS_IN_GAME) {
            for (String state : CHEST_STATES) {
                String key = chest(chest, state);
                String name = chest.substring(0, 1).toUpperCase() + chest.substring(1).replace('_', ' ');
                out.add(new Entry("Chests", CHESTS + "/" + chest, key, paths(key).get(0), name, state));
            }
        }
        for (ItemDef d : ItemDef.ALL) {
            for (String state : ITEM_STATES) {
                String key = item(d.id(), state);
                out.add(new Entry("Items", ITEMS + "/" + d.id(), key, paths(key).get(0), d.name(), state));
            }
        }
        for (String s : UI_SOUNDS) {
            out.add(new Entry("Interface", UI, ui(s), paths(ui(s)).get(0), "Interface", s));
        }
        return out;
    }

    // --- key builders (so callers never hand-splice strings) --------------------

    /**
     * The sound of a body's animation state, e.g. {@code player/masculine/walk}.
     * {@code body} is the wardrobe's body item - any style of it ({@code
     * masculine_px128_lh} is the masculine body) - or null for the default.
     */
    public static String player(String body, AnimState state) {
        return "player/" + bodyOf(body) + "/" + state.key();
    }

    /** The body a wardrobe body item sounds as: its base name ({@link Wardrobe.Style#base}), or the default. */
    public static String bodyOf(String bodyItem) {
        if (bodyItem == null || bodyItem.isBlank()) return DEFAULT_BODY;
        return Wardrobe.Style.base(bodyItem.trim());
    }

    public static String chest(String chest, String state) {
        return "chest/" + chest + "/" + state;
    }

    public static String item(String itemId, String state) {
        return "item/" + itemId + "/" + state;
    }

    public static String ui(String name) {
        return "ui/" + name;
    }

    /**
     * Whether a key names a sound that plays for as long as its state holds,
     * rather than once: a looping animation (a walk, an idle) or a chest
     * standing shut or open. These loop until stopped.
     */
    public static boolean isLooping(String key) {
        if (key == null) return false;
        String[] parts = key.split("/");
        String state = parts[parts.length - 1];
        return switch (parts[0]) {
            case "player" -> {
                AnimState s = AnimState.byKey(state);
                yield s != null && s.loops();
            }
            case "chest" -> state.equals("idle") || state.equals("opened");
            default -> false;
        };
    }

    /** Join key segments {@code [from, to)} with {@code sep}. */
    private static String join(String[] parts, int from, int to, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < Math.min(to, parts.length); i++) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
