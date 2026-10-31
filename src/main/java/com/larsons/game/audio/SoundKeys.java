package com.larsons.game.audio;

import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Rarity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every place the game makes a sound, and the file a {@link SoundPack} expects
 * for it — the engine's {@code SoundKeys}, with the game's own catalogue.
 *
 * <p>A sound key names an <em>object</em> and an <em>action state</em>, and
 * every animation is one: a body's states, the chest's four, what is done
 * with an item, the inventory; and the moments between them - a footstep, a
 * landing, a blow, a click.
 *
 * <pre>
 *   player/feminine/walk           -&gt; player/feminine/walk.mp3   (her walk)
 *   player/masculine/axe_attack    -&gt; player/masculine/axe_attack.mp3
 *   player/feminine/footstep_grass -&gt; player/feminine/footstep_grass.mp3
 *   chest/ornate_chest/open        -&gt; chests/ornate_chest/open.mp3
 *   item/battle_axe/pickup         -&gt; items/battle_axe/pickup.mp3
 *   ui/inventory_open              -&gt; ui/inventory_open.mp3
 *   combat/attack_dummy/hit        -&gt; combat/attack_dummy/hit.mp3
 *   combat/dice_roll               -&gt; combat/dice_roll.mp3
 *   world/loot_rare                -&gt; world/loot_rare.mp3
 *   ambient/wind                   -&gt; ambient/wind.mp3
 *   music/dream_on                 -&gt; music/dream_on.mp3       (a song)
 *   music/main_menu                -&gt; music/main_menu.mp3, else music/dream_on.mp3
 * </pre>
 *
 * <p>Keys fall back from the specific to the general ({@link #chain}), as the
 * engine's do: {@code player/masculine/walk} is {@code player/masculine/walk.mp3}
 * if there is one, else {@code player/walk.mp3} - one file for both bodies'
 * walks, until a body is given its own. A chest's sound falls back to every
 * chest's ({@code chests/open.mp3}), an item's to every item's ({@code
 * items/pickup.mp3}). A move falls back to the move it is a version of, as
 * its animation does: the axe's heavy attack to the axe's attack, that to
 * the sword's, a crouched bow shot to the standing one - so one file can
 * voice a whole family of moves until one of them is given its own.
 *
 * <p>Every sound belongs to a {@link SoundCategory} ({@link #categoryOf}),
 * and the {@linkplain Entry#listed() listed} ones - the shared sounds, and
 * each body's voice - are the rows of the Sounds menu. The game ships a
 * placeholder for every listed sound (made by {@code tools/sounds/}); the
 * rest stay silent until a file with their name is dropped in.
 */
public final class SoundKeys {

    // Folder names inside the pack, one per family of sounds.
    public static final String PLAYER = "player";
    public static final String CHESTS = "chests";
    public static final String ITEMS = "items";
    public static final String UI = "ui";
    public static final String COMBAT = "combat";
    public static final String WORLD = "world";
    public static final String AMBIENT = "ambient";
    public static final String MUSIC = "music";
    /** Where a key from an unrecognised namespace lands. */
    public static final String OTHER = "other";

    /** The bodies a player can be drawn as - each has a folder of its own for its animations' sounds. */
    public static final List<String> BODIES = List.of("feminine", "masculine");
    /** The body a character without a drawn body (the generated stand-in) sounds as. */
    public static final String DEFAULT_BODY = "feminine";

    /** The kinds of ground a footstep can fall on (after {@code world/GroundMap}'s materials, grass for the rest). */
    public static final List<String> SURFACES = List.of("grass", "dirt", "gravel", "forest");

    /**
     * A body's sounds that are not animations: the footsteps on each
     * surface, landing from a jump, crouching and standing, each hand's grip
     * on the vines, the end of a step over a ledge and letting go of the
     * wall; and its voice - hurt, knocked out, out of breath.
     */
    public static final List<String> PLAYER_SOUNDS = List.of("footstep_grass", "footstep_dirt", "footstep_gravel",
            "footstep_forest", "land", "hard_landing", "landing_roll", "crouch", "stand", "climb_grip",
            "climb_top", "let_go", "hurt", "knocked_out", "out_of_breath");

    /** The voice among {@link #PLAYER_SOUNDS} (the rest are movement). */
    public static final List<String> VOICE_SOUNDS = List.of("hurt", "knocked_out", "out_of_breath");

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

    /**
     * The interface: a button clicked, the keyboard's focus moved, backing
     * out of a page, pausing and resuming; the inventory opened and closed,
     * a hotbar slot chosen, an item lifted onto the pointer and put down, a
     * sort; something put on in the wardrobe; a warning; a screenshot; the
     * debug code; a cutscene's next line; and the level editor's - an object
     * selected, placed, removed, turned, a brush stroke, an undo, a save, and
     * dropping into (or out of) a play-test.
     */
    public static final List<String> UI_SOUNDS = List.of("click", "hover", "back", "pause", "resume",
            "inventory_open", "inventory_close", "hotbar_select", "item_pick", "item_place", "sort", "wear",
            "error", "screenshot", "secret", "dialogue",
            "select", "place", "remove", "rotate", "brush", "undo", "save", "playtest");

    /** The training dummies in the world (their sprite folders under {@code assets/sprites/objects/}). */
    public static final List<String> DUMMIES_IN_GAME = List.of("attack_dummy");

    /** A dummy's reactions: struck and rocking, knocked over, springing back up. */
    public static final List<String> DUMMY_STATES = List.of("hit", "fall", "rise");

    /** The flying dummies in the world (their sprite folders under {@code assets/sprites/objects/}). */
    public static final List<String> FLIERS_IN_GAME = List.of("flying_dummy");

    /**
     * A flying dummy's: flapping on its rounds (held), the screech before it
     * dives, the dive, struck, knocked out of the air, hitting the ground,
     * beating back up.
     */
    public static final List<String> FLIER_STATES = List.of("fly", "warn", "swoop", "hit", "fall", "down", "rise");

    /**
     * The fighting: the dice tumbling, a swing's blow landing, a shot
     * striking or sticking in the ground, a spell cast, striking, bursting
     * on the ground; a swing with no mana for it, poison's tick; her being
     * struck, a dive blocked, parried or dodged; the heavy attack ready
     * again; locking on to something and letting it go.
     */
    public static final List<String> COMBAT_SOUNDS = List.of("dice_roll", "blow", "arrow_hit", "bolt_hit",
            "arrow_stick", "spell_cast", "spell_hit", "spell_burst", "fizzle", "venom_tick", "player_hurt",
            "block", "parry", "dodge", "heavy_ready", "lock_on", "lock_off");

    /**
     * The loot: an item thrown out of a chest, landing, and - as it lands -
     * its rarity, Uncommon and up, rung out.
     */
    public static final List<String> WORLD_SOUNDS = List.of("loot_throw", "loot_land", "loot_uncommon", "loot_rare",
            "loot_epic", "loot_legendary", "loot_mythic");

    /** The place itself, each a loop: the meadow's breeze, a stream, crickets (for sound objects in a level). */
    public static final List<String> AMBIENT_SOUNDS = List.of("wind", "stream", "crickets");

    /**
     * The places that play a song of their own, and the song each plays until
     * the player gives the place a file of its own ({@link #chain}): the main
     * menu, Dream On. The songs themselves are {@link Songs#ALL}.
     */
    public static final Map<String, String> MUSIC_PLACES = Map.of("main_menu", "dream_on");

    /** The moves each weapon has a sound of its own for, at its stance (the others fall back to these). */
    static final List<AnimState> WEAPON_MOVES = List.of(AnimState.ATTACK, AnimState.SPIN_ATTACK, AnimState.PARRY,
            AnimState.SHIELD_BASH, AnimState.AXE_ATTACK, AnimState.AXE_HEAVY_ATTACK, AnimState.AXE_SPIN_ATTACK,
            AnimState.BOW_DRAW, AnimState.BOW_FIRE, AnimState.CROSSBOW_FIRE, AnimState.STAFF_ATTACK,
            AnimState.STAFF_HEAVY_ATTACK, AnimState.WAND_ATTACK, AnimState.WAND_HEAVY_ATTACK, AnimState.MACE_ATTACK,
            AnimState.MACE_HEAVY_ATTACK, AnimState.DAGGER_ATTACK, AnimState.DAGGER_HEAVY_ATTACK);

    /** The animations of a body's moving about with a sound of their own, shared by both bodies. */
    static final List<AnimState> MOVEMENT_MOVES = List.of(AnimState.JUMP, AnimState.DASH, AnimState.ROLL,
            AnimState.LEDGE_UP, AnimState.LEDGE_DOWN, AnimState.PICKUP);

    /**
     * One catalogue row: what it belongs to, the pack folder its file goes
     * in, the sound key, the file to name the audio (relative to the pack, no
     * extension), what it is called, the state the key is for, and whether
     * it is a row of the Sounds menu ({@code listed}: a shared sound or a
     * body's voice; the rest are one body's or one object's own, found by
     * file name in {@code SOUND_KEYS.txt}).
     */
    public record Entry(SoundCategory category, String folder, String key, String file,
                        String name, String state, boolean listed) {

        /** Whether it loops for as long as what started it lasts ({@link #isLooping}). */
        public boolean looping() { return isLooping(key); }
    }

    /** Rows added by the code that makes the sounds (a creative-mode object), and which of them loop. */
    private static final Map<String, Entry> REGISTERED = new LinkedHashMap<>();
    private static final Map<String, Boolean> REGISTERED_LOOPS = new LinkedHashMap<>();

    private SoundKeys() {}

    /**
     * Add a sound the catalogue does not know - something defined outside
     * this class, a creative-mode object - as a listed row of {@code
     * category}: it gets a tab in the Sounds menu, a volume, a line in the
     * key list, and resolves and falls back like any other key. Registering
     * a key again replaces it.
     */
    public static synchronized void register(SoundCategory category, String key, String name, boolean looping) {
        if (key == null || key.isBlank() || category == null) return;
        String k = key.trim();
        List<String> files = paths(k);
        String file = files.get(0);
        int slash = file.lastIndexOf('/');
        REGISTERED.put(k, new Entry(category, slash < 0 ? OTHER : file.substring(0, slash), k, file,
                name == null || name.isBlank() ? k : name, k.substring(k.lastIndexOf('/') + 1), true));
        REGISTERED_LOOPS.put(k, looping);
    }

    /** A registered {@link Entry}, as {@link #register(SoundCategory, String, String, boolean)} takes it. */
    public static void register(Entry e) {
        if (e != null) register(e.category(), e.key(), e.name(), e.looping());
    }

    /** Forget a registered sound (the object it was for is gone); the catalogue's own keys stay. */
    public static synchronized void unregister(String key) {
        if (key == null) return;
        REGISTERED.remove(key.trim());
        REGISTERED_LOOPS.remove(key.trim());
    }

    /** Every pack folder, in the order the key list documents them. */
    public static List<String> folders() {
        List<String> out = new ArrayList<>();
        out.add(PLAYER);
        for (String body : BODIES) out.add(PLAYER + "/" + body);
        out.add(CHESTS);
        for (String chest : CHESTS_IN_GAME) out.add(CHESTS + "/" + chest);
        out.add(ITEMS);
        for (ItemDef d : ItemDef.ALL) out.add(ITEMS + "/" + d.id());
        out.add(UI);
        for (String dummy : DUMMIES_IN_GAME) out.add(COMBAT + "/" + dummy);
        for (String flier : FLIERS_IN_GAME) out.add(COMBAT + "/" + flier);
        out.add(COMBAT);
        out.add(WORLD);
        out.add(AMBIENT);
        out.add(MUSIC);
        return out;
    }

    // --- resolution -------------------------------------------------------------------

    /**
     * The keys {@code key} is heard as, most specific first: itself, then
     * what it falls back to - the family's own ({@code player/feminine/walk}
     * before {@code player/walk}, {@code chest/ornate_chest/open} before
     * {@code chest/open}), and for a body's move, the move it is a version
     * of ({@link #soundFallback}), each body's before the shared:
     *
     * <pre>
     *   player/feminine/axe_heavy_attack, player/axe_heavy_attack,
     *   player/feminine/axe_attack, player/axe_attack,
     *   player/feminine/attack, player/attack
     * </pre>
     *
     * A footstep on one surface falls back to any footstep ({@code
     * player/footstep}).
     */
    public static List<String> chain(String key) {
        if (key == null || key.isBlank()) return List.of();
        String[] parts = key.split("/");
        List<String> out = new ArrayList<>();
        if (parts.length == 3 && parts[0].equals("player")) {
            String body = parts[1], state = parts[2];
            AnimState s = AnimState.byKey(state);
            if (s != null) {
                for (; s != null; s = soundFallback(s)) {
                    out.add("player/" + body + "/" + s.key());
                    out.add("player/" + s.key());
                }
                return out;
            }
            out.add(key);
            out.add("player/" + state);
            if (state.startsWith("footstep_")) {
                out.add("player/" + body + "/footstep");
                out.add("player/footstep");
            }
            return out;
        }
        out.add(key);
        if (parts.length >= 3 && family(parts[0]) != null) out.add(parts[0] + "/" + parts[parts.length - 1]);
        if (parts.length == 2 && parts[0].equals("music") && MUSIC_PLACES.containsKey(parts[1])) {
            out.add(music(MUSIC_PLACES.get(parts[1])));        // a place's song: the song it plays
        }
        return out;
    }

    /**
     * The move a body's {@code state} borrows its sound from when it has none
     * of its own: the animation it falls back to ({@link AnimState#fallback})
     * while that is the same kind - a one-shot for a one-shot, a held one for
     * a held one - else the same move standing up for a crouched one (a
     * crouched bow shot sounds like a shot, not like crouching); null when
     * there is none (a laugh does not fall back to standing about).
     */
    public static AnimState soundFallback(AnimState state) {
        AnimState f = state.fallback();
        if (f != null && f.loops() == state.loops()) return f;
        if (!state.loops() && state.key().contains("crouch_")) {
            AnimState standing = AnimState.byKey(state.key().replace("crouch_", ""));
            if (standing != null && !standing.loops() && standing != state) return standing;
        }
        return null;
    }

    /**
     * The relative pack paths (no extension) a sound key accepts, most
     * specific first: one file for each key of its {@link #chain}.
     */
    public static List<String> paths(String key) {
        List<String> out = new ArrayList<>();
        for (String k : chain(key)) out.add(file(k));
        if (out.isEmpty() && key != null && !key.isBlank()) out.add(file(key));
        return out;
    }

    /** The one file (relative to the pack, no extension) that is {@code key}'s own, fallbacks aside. */
    public static String file(String key) {
        String[] parts = key.split("/");
        String family = family(parts[0]);
        if (family == null || parts.length < 2) return OTHER + "/" + key.replace('/', '_');
        if (parts.length >= 3) {
            return family + "/" + join(parts, 1, parts.length - 1, "/") + "/" + parts[parts.length - 1];
        }
        return family + "/" + parts[1];
    }

    /** The pack folder of a key's first segment, or null for an unknown one. */
    private static String family(String first) {
        return switch (first) {
            case "player" -> PLAYER;
            case "chest" -> CHESTS;
            case "item" -> ITEMS;
            case "ui" -> UI;
            case "combat" -> COMBAT;
            case "world" -> WORLD;
            case "ambient" -> AMBIENT;
            case "music" -> MUSIC;
            default -> null;
        };
    }

    /** The file a creator should drop in for {@code key} (the preferred path), e.g. {@code player/feminine/walk.mp3}. */
    public static String preferredFile(String key) {
        List<String> paths = paths(key);
        return paths.isEmpty() ? "" : paths.get(0) + ".mp3";
    }

    // --- categories -------------------------------------------------------------------

    /**
     * The category {@code key} belongs to: a registered key's own; a body's
     * animation by what it is ({@link #categoryOf(AnimState)}), its other
     * sounds movement or voice; anything of a chest, the loot and picking
     * things up the world, taking an item in hand a weapon's; a dummy's a
     * creature's; the lock-on the interface's; the rest of the fighting hits
     * and magic.
     */
    public static SoundCategory categoryOf(String key) {
        if (key == null || key.isBlank()) return SoundCategory.INTERFACE;
        Entry reg;
        synchronized (SoundKeys.class) {
            reg = REGISTERED.get(key);
        }
        if (reg != null) return reg.category();
        String[] parts = key.split("/");
        String last = parts[parts.length - 1];
        return switch (parts[0]) {
            case "player" -> {
                AnimState s = AnimState.byKey(last);
                if (s != null) yield categoryOf(s);
                yield VOICE_SOUNDS.contains(last) ? SoundCategory.VOICES : SoundCategory.MOVEMENT;
            }
            case "chest", "world" -> SoundCategory.WORLD;
            case "item" -> last.equals("equip") ? SoundCategory.WEAPONS : SoundCategory.WORLD;
            case "ui" -> SoundCategory.INTERFACE;
            case "ambient" -> SoundCategory.AMBIENT;
            case "music" -> SoundCategory.MUSIC;
            case "combat" -> {
                if (parts.length >= 3) yield SoundCategory.CREATURES;
                if (last.startsWith("lock_")) yield SoundCategory.INTERFACE;
                if (last.equals("heavy_ready")) yield SoundCategory.WEAPONS;
                yield SoundCategory.HITS;
            }
            default -> SoundCategory.WORLD;
        };
    }

    /** A body's animation's category: the emotes are its voice, every swing, shot, parry and guard a weapon's, the rest movement. */
    public static SoundCategory categoryOf(AnimState s) {
        String k = s.key();
        if (k.startsWith("emote_")) return SoundCategory.VOICES;
        if (k.contains("attack") || k.contains("parry") || k.contains("block") || k.contains("shield")
                || k.contains("draw") || k.contains("fire")) {
            return SoundCategory.WEAPONS;
        }
        return SoundCategory.MOVEMENT;
    }

    // --- the catalogue ----------------------------------------------------------------

    /** The Sounds menu's rows for {@code category}: its listed entries, registered ones included. */
    public static List<Entry> in(SoundCategory category) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : all()) if (e.listed() && e.category() == category) out.add(e);
        return out;
    }

    /**
     * The whole catalogue: the listed rows (the shared sounds, each body's
     * voice, anything registered) and, after them, every body's, chest's,
     * item's and dummy's own - ordered the way the key list documents it.
     */
    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        // --- the menu's rows
        for (String s : List.of("footstep_grass", "footstep_dirt", "footstep_gravel", "footstep_forest")) {
            out.add(row(SoundCategory.MOVEMENT, "player/" + s, playerName(s), s));
        }
        out.add(row(SoundCategory.MOVEMENT, "player/jump", "Jump", "jump"));
        for (String s : List.of("land", "hard_landing", "landing_roll", "crouch", "stand")) {
            out.add(row(SoundCategory.MOVEMENT, "player/" + s, playerName(s), s));
        }
        for (AnimState s : MOVEMENT_MOVES) {
            if (s == AnimState.JUMP) continue;
            out.add(row(SoundCategory.MOVEMENT, "player/" + s.key(), moveName(s), s.key()));
        }
        for (String s : List.of("climb_grip", "climb_top", "let_go")) {
            out.add(row(SoundCategory.MOVEMENT, "player/" + s, playerName(s), s));
        }
        for (String body : BODIES) {
            String who = Character.toUpperCase(body.charAt(0)) + body.substring(1) + ": ";
            for (AnimState s : List.of(AnimState.EMOTE_LAUGH, AnimState.EMOTE_CRY, AnimState.EMOTE_SURPRISE,
                    AnimState.EMOTE_ANGRY)) {
                out.add(row(SoundCategory.VOICES, player(body, s), who + s.label().toLowerCase(), s.key()));
            }
            for (String s : VOICE_SOUNDS) {
                out.add(row(SoundCategory.VOICES, "player/" + body + "/" + s, who + playerName(s).toLowerCase(), s));
            }
        }
        for (AnimState s : WEAPON_MOVES) {
            out.add(row(SoundCategory.WEAPONS, "player/" + s.key(), moveName(s), s.key()));
        }
        for (ItemDef d : ItemDef.ALL) {
            out.add(row(SoundCategory.WEAPONS, item(d.id(), "equip"), "Taking the " + d.name().toLowerCase()
                    + " in hand", "equip"));
        }
        out.add(row(SoundCategory.WEAPONS, combat("heavy_ready"), "Heavy attack ready again", "heavy_ready"));
        for (String s : COMBAT_SOUNDS) {
            if (s.equals("heavy_ready") || s.startsWith("lock_")) continue;
            out.add(row(SoundCategory.HITS, combat(s), combatName(s), s));
        }
        for (String dummy : DUMMIES_IN_GAME) {
            for (String state : DUMMY_STATES) {
                out.add(row(SoundCategory.CREATURES, dummy(dummy, state), "Training dummy: " + creatureName(state),
                        state));
            }
        }
        for (String flier : FLIERS_IN_GAME) {
            for (String state : FLIER_STATES) {
                out.add(row(SoundCategory.CREATURES, dummy(flier, state), "Flying dummy: " + creatureName(state),
                        state));
            }
        }
        for (String state : CHEST_STATES) {
            out.add(row(SoundCategory.WORLD, "chest/" + state, "Chest: " + chestName(state), state));
        }
        for (String s : WORLD_SOUNDS) out.add(row(SoundCategory.WORLD, world(s), worldName(s), s));
        out.add(row(SoundCategory.WORLD, "item/pickup", "Picking something up", "pickup"));
        out.add(row(SoundCategory.WORLD, "item/drop", "Dropping something", "drop"));
        for (String s : AMBIENT_SOUNDS) out.add(row(SoundCategory.AMBIENT, ambient(s), ambientName(s), s));
        for (String s : MUSIC_PLACES.keySet().stream().sorted().toList()) {
            out.add(row(SoundCategory.MUSIC, music(s), musicName(s), s));
        }
        for (Songs.Song song : Songs.ALL) out.add(row(SoundCategory.MUSIC, song.key(), song.label(), song.id()));
        for (String s : UI_SOUNDS) out.add(row(SoundCategory.INTERFACE, ui(s), uiName(s), s));
        out.add(row(SoundCategory.INTERFACE, combat("lock_on"), "Locking on to a target", "lock_on"));
        out.add(row(SoundCategory.INTERFACE, combat("lock_off"), "Letting the target go", "lock_off"));
        synchronized (SoundKeys.class) {
            for (Entry e : REGISTERED.values()) {
                if (out.stream().noneMatch(o -> o.key().equals(e.key()))) out.add(e);
            }
        }

        // --- each body's, chest's, item's own (by file name)
        for (String body : BODIES) {
            for (AnimState s : AnimState.values()) {
                String key = player(body, s);
                out.add(own(key, body + " " + s.label().toLowerCase(), s.key()));
            }
            for (String s : PLAYER_SOUNDS) {
                if (VOICE_SOUNDS.contains(s)) continue;     // listed above
                out.add(own("player/" + body + "/" + s, body + " " + playerName(s).toLowerCase(), s));
            }
        }
        for (String chest : CHESTS_IN_GAME) {
            String name = chest.substring(0, 1).toUpperCase() + chest.substring(1).replace('_', ' ');
            for (String state : CHEST_STATES) out.add(own(chest(chest, state), name + ": " + chestName(state), state));
        }
        for (ItemDef d : ItemDef.ALL) {
            for (String state : List.of("pickup", "drop")) {
                out.add(own(item(d.id(), state), d.name() + " " + (state.equals("pickup") ? "picked up" : "dropped"),
                        state));
            }
        }
        // each body's voice is listed above: its own row would be the same row twice
        Set<String> seen = new HashSet<>();
        out.removeIf(e -> !seen.add(e.key()));
        return out;
    }

    private static Entry row(SoundCategory c, String key, String name, String state) {
        String file = file(key);
        return new Entry(c, file.substring(0, file.lastIndexOf('/')), key, file, name, state, true);
    }

    private static Entry own(String key, String name, String state) {
        String file = file(key);
        return new Entry(categoryOf(key), file.substring(0, file.lastIndexOf('/')), key, file, name, state, false);
    }

    private static String moveName(AnimState s) {
        return switch (s) {
            case ATTACK -> "Sword swing";
            case SPIN_ATTACK -> "Sword spin attack";
            case PARRY -> "Parry (every weapon's, until it has its own)";
            case SHIELD_BASH -> "Shield bash";
            case AXE_ATTACK -> "Battle axe chop";
            case AXE_HEAVY_ATTACK -> "Battle axe heavy attack";
            case AXE_SPIN_ATTACK -> "Battle axe spin attack";
            case BOW_DRAW -> "Bow drawn";
            case BOW_FIRE -> "Bow loosed";
            case CROSSBOW_FIRE -> "Crossbow shot";
            case STAFF_ATTACK -> "Crystal staff cast";
            case STAFF_HEAVY_ATTACK -> "Crystal staff slam";
            case WAND_ATTACK -> "Wand cast";
            case WAND_HEAVY_ATTACK -> "Wand great cast";
            case MACE_ATTACK -> "Mace smash";
            case MACE_HEAVY_ATTACK -> "Mace ground smash";
            case DAGGER_ATTACK -> "Dagger thrust";
            case DAGGER_HEAVY_ATTACK -> "Dagger slash and lunge";
            case JUMP -> "Jump";
            case DASH -> "Dash";
            case ROLL -> "Combat roll";
            case LEDGE_UP -> "Stepping up over a ledge";
            case LEDGE_DOWN -> "Stepping down over a ledge";
            case PICKUP -> "Reaching down for something";
            default -> s.label();
        };
    }

    private static String playerName(String s) {
        return switch (s) {
            case "footstep_grass" -> "Footstep on grass";
            case "footstep_dirt" -> "Footstep on a dirt path";
            case "footstep_gravel" -> "Footstep on gravel";
            case "footstep_forest" -> "Footstep on the forest floor";
            case "land" -> "Landing";
            case "hard_landing" -> "Hard landing (hurt)";
            case "landing_roll" -> "Rolling out of a fall";
            case "crouch" -> "Crouching down";
            case "stand" -> "Standing up";
            case "climb_grip" -> "Climbing: a hand on the vines";
            case "climb_top" -> "Over the ledge: feet down";
            case "let_go" -> "Letting go of the vines";
            case "hurt" -> "Hurt";
            case "knocked_out" -> "Knocked out";
            case "out_of_breath" -> "Out of breath";
            default -> s;
        };
    }

    private static String combatName(String s) {
        return switch (s) {
            case "dice_roll" -> "The damage dice tumbling";
            case "blow" -> "A blow landing";
            case "arrow_hit" -> "An arrow striking";
            case "bolt_hit" -> "A crossbow bolt striking";
            case "arrow_stick" -> "A shot sticking in the ground";
            case "spell_cast" -> "A spell cast";
            case "spell_hit" -> "A spell striking";
            case "spell_burst" -> "A spell bursting on the ground";
            case "fizzle" -> "Not enough mana";
            case "venom_tick" -> "Venom's poison";
            case "player_hurt" -> "Struck by a dive";
            case "block" -> "A dive blocked";
            case "parry" -> "A dive parried";
            case "dodge" -> "A dive dodged";
            default -> s;
        };
    }

    private static String creatureName(String state) {
        return switch (state) {
            case "hit" -> "struck";
            case "fall" -> "knocked down";
            case "rise" -> "back up";
            case "fly" -> "flapping (held)";
            case "warn" -> "screech before a dive";
            case "swoop" -> "diving";
            case "down" -> "hitting the ground";
            default -> state;
        };
    }

    private static String chestName(String state) {
        return switch (state) {
            case "idle" -> "shut, smoke swirling (held)";
            case "open" -> "lid thrown open";
            case "opened" -> "standing open, aglow (held)";
            case "close" -> "lid slammed";
            default -> state;
        };
    }

    private static String worldName(String s) {
        return switch (s) {
            case "loot_throw" -> "Loot thrown out of a chest";
            case "loot_land" -> "Loot landing";
            default -> {
                Rarity r = Rarity.byKey(s.substring("loot_".length()));
                yield r == null ? s : "Loot: " + (r == Rarity.UNCOMMON || r == Rarity.EPIC ? "an " : "a ")
                        + r.label() + " find";
            }
        };
    }

    private static String ambientName(String s) {
        return switch (s) {
            case "wind" -> "Breeze over the meadow (held)";
            case "stream" -> "A stream (held; for sound objects)";
            case "crickets" -> "Crickets (held; for sound objects)";
            default -> s;
        };
    }

    private static String musicName(String s) {
        return switch (s) {
            case "main_menu" -> "On the main menu";
            default -> s;
        };
    }

    private static String uiName(String s) {
        return switch (s) {
            case "click" -> "Button clicked";
            case "hover" -> "Menu: moving between items";
            case "back" -> "Backing out of a page";
            case "pause" -> "Pausing";
            case "resume" -> "Back to the game";
            case "inventory_open" -> "Inventory opened";
            case "inventory_close" -> "Inventory closed";
            case "hotbar_select" -> "Hotbar slot chosen";
            case "item_pick" -> "Inventory: an item lifted";
            case "item_place" -> "Inventory: an item put down";
            case "sort" -> "Inventory sorted";
            case "wear" -> "Wardrobe: something put on";
            case "error" -> "Can't do that";
            case "screenshot" -> "Screenshot";
            case "secret" -> "A secret code typed";
            case "dialogue" -> "Cutscene: the next line";
            case "select" -> "Editor: an object selected";
            case "place" -> "Editor: an object placed";
            case "remove" -> "Editor: an object removed";
            case "rotate" -> "Editor: an object turned";
            case "brush" -> "Editor: sculpting or painting";
            case "undo" -> "Editor: undo";
            case "save" -> "Saved";
            case "playtest" -> "Editor: into or out of a play-test";
            default -> s;
        };
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

    /** A body's other sound ({@link #PLAYER_SOUNDS}), e.g. {@code player/feminine/footstep_grass}. */
    public static String player(String body, String sound) {
        return "player/" + bodyOf(body) + "/" + sound;
    }

    /** A footstep of {@code body} on {@code surface} ({@link #SURFACES}). */
    public static String footstep(String body, String surface) {
        return player(body, "footstep_" + (SURFACES.contains(surface) ? surface : "grass"));
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

    /** A training dummy's reaction, e.g. {@code combat/attack_dummy/hit}. */
    public static String dummy(String dummy, String state) {
        return "combat/" + dummy + "/" + state;
    }

    /** A sound of the fighting, e.g. {@code combat/dice_roll}. */
    public static String combat(String name) {
        return "combat/" + name;
    }

    /** A sound of the world, e.g. {@code world/loot_rare}. */
    public static String world(String name) {
        return "world/" + name;
    }

    /** The rarity ringing out as loot of {@code r} lands, or null for a Common one (it just lands). */
    public static String loot(Rarity r) {
        return r == null || r == Rarity.COMMON ? null : world("loot_" + r.key());
    }

    /** A sound of the place, e.g. {@code ambient/wind}. */
    public static String ambient(String name) {
        return "ambient/" + name;
    }

    /** A song ({@link Songs}) or a place that plays one ({@link #MUSIC_PLACES}), e.g. {@code music/dream_on}. */
    public static String music(String name) {
        return "music/" + name;
    }

    /**
     * Whether a key names a sound that plays for as long as its state holds,
     * rather than once: a looping animation (a walk, an idle), a chest
     * standing shut or open, a flier flapping on its rounds, anything
     * ambient, a song. These loop until stopped.
     */
    public static boolean isLooping(String key) {
        if (key == null) return false;
        synchronized (SoundKeys.class) {
            Boolean reg = REGISTERED_LOOPS.get(key);
            if (reg != null) return reg;
        }
        String[] parts = key.split("/");
        String state = parts[parts.length - 1];
        return switch (parts[0]) {
            case "player" -> {
                AnimState s = AnimState.byKey(state);
                yield s != null && s.loops();
            }
            case "chest" -> state.equals("idle") || state.equals("opened");
            case "combat" -> parts.length >= 3 && state.equals("fly");
            case "ambient", "music" -> true;
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
