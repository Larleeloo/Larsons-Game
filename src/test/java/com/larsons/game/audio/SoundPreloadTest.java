package com.larsons.game.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The sound effects decoded ahead as the game starts, counted for the loading screen. */
@Timeout(60)
class SoundPreloadTest {

    @TempDir
    Path dir;

    @AfterEach
    void restore() {
        SoundPack.useDir(Path.of("assets", "sounds"));
    }

    @Test
    void preloadingCountsTheFilesLeavesTheSongsAndSaysWhenItIsDone() throws Exception {
        tone(dir.resolve("player/jump.wav"));
        tone(dir.resolve("ui/click.wav"));
        tone(dir.resolve("music/main_menu.wav"));       // a song: Music reads it when it is about to play
        SoundPack.useDir(dir);
        Sounds.preload();
        assertFalse(Sounds.preloadProgress().finished(), "not done the moment it starts");
        long end = System.nanoTime() + 30_000_000_000L;
        while (!Sounds.preloadProgress().finished() && System.nanoTime() < end) Thread.sleep(5);
        Sounds.Preload p = Sounds.preloadProgress();
        assertTrue(p.finished());
        assertEquals(2, p.total());
        assertEquals(2, p.done());
        assertNull(p.current());
    }

    @Test
    void anEmptyPackIsDoneAtOnce() throws Exception {
        SoundPack.useDir(dir.resolve("nothing here"));
        Sounds.preload();
        long end = System.nanoTime() + 30_000_000_000L;
        while (!Sounds.preloadProgress().finished() && System.nanoTime() < end) Thread.sleep(5);
        assertEquals(0, Sounds.preloadProgress().total());
    }

    /** A tenth of a second of quiet stereo sine, as a WAV. */
    private static void tone(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        int frames = 4410;
        byte[] pcm = new byte[frames * 4];
        for (int i = 0; i < frames; i++) {
            short s = (short) (Math.sin(2 * Math.PI * 440 * i / 44100) * 2000);
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
