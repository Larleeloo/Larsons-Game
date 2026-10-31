package com.larsons.game.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** The loading screen's wording: counts, times, text cut to fit, what the start scene is called. */
class LoadingScreenTest {

    /** Every character one pixel wide. */
    private static final ToDoubleFunction<String> CHARS = String::length;

    @Test
    void textThatFitsIsLeftAlone() {
        assertEquals("oak tree", LoadingScreen.fit("oak tree", 8, CHARS, "…", false));
        assertEquals("", LoadingScreen.fit(null, 8, CHARS, "…", false));
    }

    @Test
    void textTooLongIsCutKeepingItsStartOrForAPathItsEnd() {
        String path = "sprites_masculine/gloves/steel_gauntlets_px64";
        String start = LoadingScreen.fit(path, 20, CHARS, "…", false);
        String end = LoadingScreen.fit(path, 20, CHARS, "…", true);
        assertEquals("sprites_masculine/g…", start);
        assertEquals("…teel_gauntlets_px64", end);
        assertEquals(20, start.length());
        assertEquals(20, end.length());
        assertEquals("", LoadingScreen.fit(path, 0, CHARS, "…", false), "no room at all: nothing");
        assertEquals("two…", LoadingScreen.fit("two words", 5, CHARS, "…", false), "no space left before the dots");
    }

    @Test
    void countsSayWhatTheyCount() {
        Boot boot = new Boot(Map.of());
        boot.run(Boot.Step.SPRITE_INDEX, r -> {
            r.progress(211, 1859, "folders");
            assertEquals("211 of 1,859 folders", LoadingScreen.count(boot.status(Boot.Step.SPRITE_INDEX)));
            r.progress(3, 7);
            assertEquals("3 of 7", LoadingScreen.count(boot.status(Boot.Step.SPRITE_INDEX)));
            return null;
        });
    }

    @Test
    void timesKeepTwoFiguresAfterThePointUnderTenSeconds() {
        assertEquals("0.42 s", LoadingScreen.seconds(0.4213));
        assertEquals("12.4 s", LoadingScreen.seconds(12.38));
    }

    @Test
    void aDetailsFirstLineIsWhatTheListShows() {
        assertEquals("sprites/body/feminine", LoadingScreen.firstLine("sprites/body/feminine\n3,096 sheets so far"));
        assertEquals("main_menu.mp3", LoadingScreen.firstLine("main_menu.mp3"));
    }

    @Test
    void pluralsAndTheStartSceneReadAsEnglish() {
        assertEquals("1 rock face", Game.plural(1, "rock face"));
        assertEquals("1,476,768 sheets", Game.plural(1_476_768, "sheet"));
        assertEquals("0 things", Game.plural(0, "thing"));
        assertEquals("the main menu", Game.startName("menu"));
        assertEquals("the demo", Game.startName("demo"));
        assertEquals("Creative Mode", Game.startName("creative"));
        assertEquals("the level lakeside", Game.startName("play:lakeside"));
        assertEquals("the level lakeside in Creative Mode", Game.startName("edit: lakeside"));
    }
}
