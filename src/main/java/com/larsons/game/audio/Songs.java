package com.larsons.game.audio;

import java.util.List;

/**
 * The game's songs: twenty of its own, written for it in the manner of a
 * medieval band playing pop songs (none of them copies another song), each a
 * seamless loop in {@code assets/sounds/music/<id>.mp3} made from its MIDI in
 * {@code music/} by {@code tools/music/originals.py}.
 *
 * <p>A song's sound key is {@code music/<id>} ({@link Song#key}), heard like
 * any other sound - the player can swap or mute it in Pause → Sounds → Music.
 * The places that play music have keys of their own that fall back to a
 * song ({@link SoundKeys#MUSIC_PLACES}): the main menu's is Lanterns Over Brasswick until
 * the player chooses another file for it.
 *
 * <p>A level made in Creative Mode picks one of these, or a playlist of
 * several played in turn and round again ({@link #LIST} between their ids),
 * or {@link #SHUFFLE} (all of them, one after another in a random order - a
 * new level's), or {@link #NONE} ({@code Level.music()}).
 */
public final class Songs {

    /** A song: its file name (and key, {@code music/<id>}), its title and what kind of song it is. */
    public record Song(String id, String title, String about) {

        /** Its sound key, e.g. {@code music/copper_hearts}. */
        public String key() {
            return SoundKeys.music(id);
        }

        /** "Copper Hearts (a slow ballad)". */
        public String label() {
            return title + " (" + about + ")";
        }
    }

    /** A level's music choice that plays every song, shuffled (a new level's). */
    public static final String SHUFFLE = "shuffle";
    /** A level's music choice that plays none. */
    public static final String NONE = "none";

    /** Every song there is, in the order they were made. */
    public static final List<Song> ALL = List.of(
            new Song("lanterns_over_brasswick", "Lanterns Over Brasswick", "a tavern anthem"),
            new Song("the_millers_daughter", "The Miller's Daughter", "a country waltz"),
            new Song("copper_hearts", "Copper Hearts", "a slow ballad"),
            new Song("clockwork_heart", "Clockwork Heart", "a clockwork dance"),
            new Song("ride_out_at_dawn", "Ride Out at Dawn", "a galloping rock song"),
            new Song("moonlit_market", "Moonlit Market", "a sly market groove"),
            new Song("ember_and_ash", "Ember and Ash", "a power ballad"),
            new Song("tavern_lights", "Tavern Lights", "a pub singalong"),
            new Song("the_long_road_home", "The Long Road Home", "a country road song"),
            new Song("sky_portals", "Sky Portals", "an epic march"),
            new Song("starlit_courtyard", "Starlit Courtyard", "a starlit lullaby"),
            new Song("iron_and_ivy", "Iron and Ivy", "a punk jig"),
            new Song("dancing_on_the_drawbridge", "Dancing on the Drawbridge", "a dance-floor romp"),
            new Song("golden_fields", "Golden Fields", "a sunny lilt"),
            new Song("the_crystal_cave", "The Crystal Cave", "a crystal-cave mystery"),
            new Song("two_candles", "Two Candles", "a candlelit love song"),
            new Song("steam_and_sails", "Steam and Sails", "a sea-shanty stomp"),
            new Song("heroes_of_the_hollow", "Heroes of the Hollow", "a stadium anthem"),
            new Song("feather_and_flame", "Feather and Flame", "a bright, bouncing pop song"),
            new Song("farewell_fair_moon", "Farewell, Fair Moon", "a farewell song"));

    private Songs() {}

    /** The song called {@code id}, or null. */
    public static Song byId(String id) {
        if (id == null) return null;
        for (Song s : ALL) if (s.id().equals(id.trim())) return s;
        return null;
    }

    /** Between the songs of a playlist, as a level keeps it: {@code "lanterns_over_brasswick,copper_hearts"}. */
    public static final String LIST = ",";

    /**
     * The songs a music choice plays in turn, in its order: one song's id,
     * or a playlist of them ({@link #LIST} between); none for {@link #NONE}
     * or {@link #SHUFFLE}. Songs this game does not have are left out.
     */
    public static List<Song> playlist(String choice) {
        List<Song> out = new java.util.ArrayList<>();
        if (choice == null) return out;
        for (String id : choice.split(LIST)) {
            Song s = byId(id);
            if (s != null) out.add(s);
        }
        return out;
    }

    /** A music choice of {@code songs} in this order: one song's id, or a playlist; {@link #NONE} for none. */
    public static String choice(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return NONE;
        return String.join(LIST, songs.stream().map(Song::id).toList());
    }

    /** Whether {@code choice} is a playlist of two songs or more. */
    public static boolean isPlaylist(String choice) {
        return playlist(choice).size() > 1;
    }

    /**
     * Play a level's music choice ({@code Level.music()}): a song's id, that
     * song looping; a playlist, its songs one after another in its order and
     * round again; {@link #SHUFFLE}, every song shuffled; anything else
     * ({@link #NONE}, or songs this game does not have), the music fades out.
     */
    public static void playChoice(String choice, double volume) {
        String c = choice == null ? "" : choice.trim();
        List<Song> list = playlist(c);
        if (list.size() == 1) Music.play(list.get(0).key(), volume);
        else if (list.size() > 1) Music.playlist(list.stream().map(Song::key).toList(), volume);
        else if (c.equals(SHUFFLE)) Music.shuffle(keys(), volume);
        else Music.stop();
    }

    /** What a level's music choice is called: "None", "Shuffle every song", a song's title, or a playlist's. */
    public static String describe(String choice) {
        String c = choice == null ? "" : choice.trim();
        if (c.isEmpty() || c.equals(NONE)) return "None";
        if (c.equals(SHUFFLE)) return "Shuffle every song";
        List<Song> list = playlist(c);
        if (list.isEmpty()) return c + " (not in this game)";
        if (list.size() == 1) return list.get(0).label();
        return list.size() + " songs in turn: " + String.join(", ", list.stream().map(Song::title).toList());
    }

    /** Every song's sound key, in order. */
    public static List<String> keys() {
        return ALL.stream().map(Song::key).toList();
    }
}
