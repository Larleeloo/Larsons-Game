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

    @TempDir
    Path dir;

    @BeforeEach
    void pack() throws IOException {
        Music.reset();
        Path music = Files.createDirectories(dir.resolve("music"));
        tone(music.resolve("main_menu.wav"), 220);
        tone(music.resolve("test_tavern.wav"), 330);
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

    /** A quarter-second stereo sine, as a WAV. */
    private static void tone(Path file, double hz) throws IOException {
        int frames = 11025;
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
