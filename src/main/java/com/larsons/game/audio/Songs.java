package com.larsons.game.audio;

import java.util.List;

/**
 * The game's songs: the medieval-style covers of the roadmap's Sound
 * milestone, each a seamless loop in {@code assets/sounds/music/<id>.mp3}
 * made from its MIDI in {@code music/} by {@code tools/music/<id>.py}.
 *
 * <p>A song's sound key is {@code music/<id>} ({@link Song#key}), heard like
 * any other sound - the player can swap or mute it in Pause → Sounds → Music.
 * The places that play music have keys of their own that fall back to a
 * song ({@link SoundKeys#MUSIC_PLACES}): the main menu's is Dream On until
 * the player chooses another file for it.
 *
 * <p>A level made in Creative Mode picks one of these, or {@link #SHUFFLE}
 * (all of them, one after another in a random order - a new level's), or
 * {@link #NONE} ({@code Level.music()}).
 */
public final class Songs {

    /** A song: its file name (and key, {@code music/<id>}), its title and who it is by. */
    public record Song(String id, String title, String artist) {

        /** Its sound key, e.g. {@code music/dream_on}. */
        public String key() {
            return SoundKeys.music(id);
        }

        /** "Dream On (Aerosmith)". */
        public String label() {
            return title + " (" + artist + ")";
        }
    }

    /** A level's music choice that plays every song, shuffled (a new level's). */
    public static final String SHUFFLE = "shuffle";
    /** A level's music choice that plays none. */
    public static final String NONE = "none";

    /** Every song there is, in the order they were made. */
    public static final List<Song> ALL = List.of(
            new Song("dream_on", "Dream On", "Aerosmith"),
            new Song("california_dreamin", "California Dreamin'", "The Mamas & the Papas"),
            new Song("wonderwall", "Wonderwall", "Oasis"),
            new Song("wake_me_up", "Wake Me Up", "Avicii"),
            new Song("dreams", "Dreams", "Fleetwood Mac"),
            new Song("dont_fear_the_reaper", "(Don't Fear) The Reaper", "Blue Öyster Cult"));

    private Songs() {}

    /** The song called {@code id}, or null. */
    public static Song byId(String id) {
        if (id == null) return null;
        for (Song s : ALL) if (s.id().equals(id.trim())) return s;
        return null;
    }

    /**
     * Play a level's music choice ({@code Level.music()}): a song's id, that
     * song looping; {@link #SHUFFLE}, every song shuffled; anything else
     * ({@link #NONE}, or a song this game does not have), the music fades out.
     */
    public static void playChoice(String choice, double volume) {
        String c = choice == null ? "" : choice.trim();
        Song song = byId(c);
        if (song != null) Music.play(song.key(), volume);
        else if (c.equals(SHUFFLE)) Music.shuffle(keys(), volume);
        else Music.stop();
    }

    /** What a level's music choice is called: "None", "Shuffle every song", or the song's {@link Song#label}. */
    public static String describe(String choice) {
        String c = choice == null ? "" : choice.trim();
        if (c.isEmpty() || c.equals(NONE)) return "None";
        if (c.equals(SHUFFLE)) return "Shuffle every song";
        Song song = byId(c);
        return song == null ? c + " (not in this game)" : song.label();
    }

    /** Every song's sound key, in order. */
    public static List<String> keys() {
        return ALL.stream().map(Song::key).toList();
    }
}
