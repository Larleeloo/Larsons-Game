package com.larsons.game.audio;

import com.larsons.game.scene.MainMenuScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The music player: a song decoded off the game loop, started, faded in, and
 * faded out - back up again if it is asked for while it fades, the next one
 * only once the last is quiet. Runs without an audio device (the mixer goes
 * quiet and the fades are kept all the same).
 */
@Timeout(60)
class MusicTest {

    private static final Path PACK = Path.of("assets", "sounds");
    private static final String MENU = SoundKeys.music("main_menu");
    private static final String OTHER = SoundKeys.music("test_tavern");
    /** Three ten-second songs, for a playlist. */
    private static final String A = SoundKeys.music("test_a"), B = SoundKeys.music("test_b"), C = SoundKeys.music("test_c");
    private static final double SONG = 10;

    @TempDir
    Path dir;

    @BeforeEach
    void pack() throws IOException {
        Music.reset();
        Path music = Files.createDirectories(dir.resolve("music"));
        tone(music.resolve("main_menu.wav"), 220, 0.25);
        tone(music.resolve("test_tavern.wav"), 330, 0.25);
        tone(music.resolve("test_a.wav"), 262, SONG);
        tone(music.resolve("test_b.wav"), 294, SONG);
        tone(music.resolve("test_c.wav"), 330, SONG);
        SoundPack.useDir(dir);
        Sounds.useStore(Files.createDirectories(dir.resolve("config")));
        Sounds.clear();
    }

    @AfterEach
    void restore() {
        Music.reset();
        SoundPack.useDir(PACK);
        Sounds.useStore(null);
        Sounds.clear();
    }

    @Test
    void theMenusSongIsTheMainMenuKey() {
        assertEquals("music/main_menu", MainMenuScene.MUSIC);
    }

    @Test
    void aShuffledPlaylistPlaysEachSongOnceThenShufflesAgainWithoutARepeat() {
        Music.useRandom(new Random(7));
        Music.shuffle(List.of(A, B, C), 1);
        List<String> order = Music.playlist();
        assertEquals(Set.of(A, B, C), new HashSet<>(order));
        assertEquals(order.get(0), Music.wanted());

        List<String> heard = heard(7);
        assertEquals(order, heard.subList(0, 3), "once each, in the order shuffled");
        assertEquals(Set.of(A, B, C), new HashSet<>(heard.subList(3, 6)), "then all three again");
        for (int i = 1; i < heard.size(); i++) assertNotEquals(heard.get(i - 1), heard.get(i), "never one twice running");
    }

    @Test
    void theSongPlayingCarriesOnIntoAPlaylistWithItAndTheSamePlaylistIsNotRestarted() {
        Music.play(B);
        settle();
        Music.update(Music.FADE_IN + 1);
        Music.shuffle(List.of(A, B, C), 1);
        assertEquals(B, Music.playlist().get(0));
        assertEquals(B, Music.current());
        assertEquals(1, Music.level(), 1e-9, "no fade");
        double at = Music.elapsed();
        List<String> order = Music.playlist();
        Music.shuffle(List.of(C, A, B), 1);                     // the same songs: a level entered again
        assertEquals(order, Music.playlist());
        assertEquals(at, Music.elapsed(), 1e-9, "not started over");
    }

    @Test
    void aSilentSongInAPlaylistIsPassedOverAndAllSilenceStopsLooking() {
        String gone = SoundKeys.music("test_gone");
        Music.useRandom(new Random(3));
        Music.shuffle(List.of(A, gone, B), 1);
        List<String> heard = heard(4);
        assertFalse(heard.contains(gone));
        assertEquals(Set.of(A, B), new HashSet<>(heard));

        Music.reset();
        Music.shuffle(List.of(gone, SoundKeys.music("test_lost")), 1);
        for (int i = 0; i < 20; i++) settle();
        assertEquals("", Music.current());
        assertFalse(Music.loading(), "not read over and over");
    }

    @Test
    void aSceneSetsHowLoudItsMusicIs() {
        Music.play(A, 0.5);
        settle();
        Music.update(Music.FADE_IN);
        double half = Music.volume();
        assertTrue(half > 0);
        Music.play(A, 1);                                       // the same song, louder: no restart
        assertEquals(A, Music.current());
        assertEquals(2 * half, Music.volume(), 1e-9);
        Music.play(A, 7);
        assertEquals(2 * half, Music.volume(), 1e-9, "never above the song's own level");
    }

    @Test
    void aLevelsChoicePlaysOneSongEverySongOrNone() {
        Songs.playChoice("dream_on", 0.7);
        assertEquals("music/dream_on", Music.wanted());
        assertTrue(Music.playlist().isEmpty(), "one song, looping");
        Songs.playChoice(Songs.SHUFFLE, 0.7);
        assertEquals(new HashSet<>(Songs.keys()), new HashSet<>(Music.playlist()));
        Songs.playChoice(Songs.NONE, 0.7);
        assertEquals("", Music.wanted());
        Songs.playChoice("dream_on", 0.7);
        Songs.playChoice("a_song_from_a_later_game", 0.7);
        assertEquals("", Music.wanted());
    }

    @Test
    void thePreviewPlaysPausesJumpsAndLoops() {
        MusicPreview p = new MusicPreview();
        p.open(A);
        assertEquals(A, p.key());
        p.play();                                               // asked before it is read: plays once it is
        waitFor(p);
        assertTrue(p.ready());
        assertEquals(SONG, p.length(), 1e-3);
        p.update(1.5);
        assertEquals(1.5, p.position(), 1e-9);
        p.pause();
        p.update(1);
        assertEquals(1.5, p.position(), 1e-9, "paused");

        p.seek(9);
        p.play();
        p.update(2);
        assertEquals(1, p.position(), 1e-9, "round from the end to the start");

        p.scrub(3);                                             // the timeline held ...
        assertTrue(p.scrubbing());
        p.update(1);
        assertEquals(3, p.position(), 1e-9, "... still while it is held");
        p.scrub(4);
        p.release();                                            // ... and let go
        assertFalse(p.scrubbing());
        p.update(0.5);
        assertEquals(4.5, p.position(), 1e-9);

        p.seek(-5);
        assertEquals(0, p.position(), 1e-9);
        p.seek(99);
        assertTrue(p.position() < SONG && p.position() > SONG - 0.01);

        p.open(B);                                              // another song: from its start, paused
        assertEquals(0, p.position(), 1e-9);
        assertFalse(p.playing());
        waitFor(p);
        p.seek(4);                                              // a jump as soon as it is read
        assertEquals(4, p.position(), 1e-9);
        p.open(SoundKeys.music("test_gone"));
        waitFor(p);
        assertFalse(p.ready(), "no file: nothing to play");
        assertEquals(0, p.length(), 1e-9);
        p.close();
        assertEquals("", p.key());
    }

    @Test
    void aSongStartsOnceReadThenFadesInAndOut() {
        Music.play(MENU);
        assertEquals(MENU, Music.wanted());
        assertEquals("", Music.current(), "nothing sounds before it is read");
        settle();
        assertEquals(MENU, Music.current());
        assertEquals(0, Music.level(), 1e-9);

        Music.update(Music.FADE_IN / 2);
        assertEquals(0.5, Music.level(), 1e-9);
        Music.update(Music.FADE_IN);
        assertEquals(1, Music.level(), 1e-9);

        Music.stop();
        Music.update(Music.FADE_OUT / 2);
        assertEquals(MENU, Music.current(), "still fading out");
        assertEquals(0.5, Music.level(), 1e-9);
        Music.update(Music.FADE_OUT);
        assertEquals("", Music.current());
        assertEquals(0, Music.level(), 1e-9);
    }

    @Test
    void askedForAgainWhileFadingOutItComesBackUpRatherThanStartingOver() {
        Music.play(MENU);
        settle();
        Music.update(Music.FADE_IN);
        Music.stop();                               // leaving the menu ...
        Music.update(Music.FADE_OUT * 0.4);
        Music.play(MENU);                           // ... straight into another menu
        assertEquals(MENU, Music.current());
        assertFalse(Music.loading(), "not read again");
        double from = Music.level();
        Music.update(0.1);
        assertTrue(Music.level() > from, "fading back in");
        assertEquals(MENU, Music.current());
    }

    @Test
    void theNextSongWaitsForTheLastToFadeOut() {
        Music.play(MENU);
        settle();
        Music.update(Music.FADE_IN);
        Music.play(OTHER);
        Music.update(Music.FADE_OUT / 2);
        assertEquals(MENU, Music.current(), "the first one fades out first");
        Music.update(Music.FADE_OUT);
        assertEquals("", Music.current());
        settle();
        assertEquals(OTHER, Music.current());
        Music.update(Music.FADE_IN);
        assertEquals(1, Music.level(), 1e-9);
    }

    @Test
    void aSongWithNoFileOrMutedIsSilentAndNotLookedForEveryFrame() {
        Music.play(SoundKeys.music("nowhere"));
        settle();
        assertEquals("", Music.current());
        Music.update(0.1);
        assertFalse(Music.loading(), "a silent song is not read again every frame");

        Sounds.mute(MENU);
        Music.play(MENU);
        settle();
        assertEquals("", Music.current(), "muted in the Sounds menu");
        Sounds.reset(MENU);
        Music.stop();
        Music.play(MENU);                           // the menu entered again: the choice is looked at anew
        settle();
        assertEquals(MENU, Music.current());
    }

    @Test
    void theFadeCreepsUpAndTailsOff() {
        assertEquals(0, Music.curve(0), 1e-9);
        assertEquals(1, Music.curve(1), 1e-9);
        assertEquals(0.25, Music.curve(0.5), 1e-9);
        assertEquals(1, Music.curve(3), 1e-9);
        for (double f = 0; f < 1; f += 0.05) assertTrue(Music.curve(f + 0.05) > Music.curve(f));
    }

    /** Play on in tenths of a second until {@code n} songs have been heard (the songs, in order). */
    private static List<String> heard(int n) {
        List<String> out = new ArrayList<>();
        long end = System.nanoTime() + 40_000_000_000L;
        while (out.size() < n && System.nanoTime() < end) {
            Music.update(0.1);
            String c = Music.current();
            if (!c.isEmpty() && (out.isEmpty() || !out.get(out.size() - 1).equals(c))) out.add(c);
            if (Music.loading()) nap();
        }
        assertEquals(n, out.size(), "songs heard: " + out);
        return out;
    }

    private static void waitFor(MusicPreview p) {
        long end = System.nanoTime() + 20_000_000_000L;
        while (p.loading() && System.nanoTime() < end) nap();
    }

    private static void nap() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Update until the song asked for has been read (or found silent). */
    private static void settle() {
        long end = System.nanoTime() + 20_000_000_000L;
        do {
            Music.update(0);
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        } while (Music.loading() && System.nanoTime() < end);
        Music.update(0);
    }

    /** A stereo sine {@code seconds} long, as a WAV. */
    private static void tone(Path file, double hz, double seconds) throws IOException {
        int frames = (int) Math.round(seconds * 44100);
        byte[] pcm = new byte[frames * 4];
        for (int i = 0; i < frames; i++) {
            short s = (short) (Math.sin(2 * Math.PI * hz * i / 44100) * 3000);
            for (int c = 0; c < 2; c++) {
                pcm[i * 4 + c * 2] = (byte) s;
                pcm[i * 4 + c * 2 + 1] = (byte) (s >> 8);
            }
        }
        AudioFormat f = new AudioFormat(44100, 16, 2, true, false);
        try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(pcm), f, frames)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, file.toFile());
        }
    }
}
