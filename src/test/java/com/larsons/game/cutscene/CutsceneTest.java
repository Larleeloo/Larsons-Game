package com.larsons.game.cutscene;

import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Variants;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Cutscene scripts, their timeline, the actors' colours and the close-up folders. */
class CutsceneTest {

    @TempDir
    Path root;

    static Wardrobe player() {
        Wardrobe w = new Wardrobe();
        w.set(Slot.BODY, "feminine");
        w.set(Slot.HAIR, "long_waves");
        w.setColour(Slot.HAIR, "auburn");
        return w;
    }

    /** The lines a scene speaks in its first {@code seconds}, as "speaker: text". */
    static List<String> lines(Cutscene cs, double seconds) {
        List<String> out = new ArrayList<>();
        Cutscene.Line last = null;
        for (double t = 0; t < seconds; t += 0.05) {
            cs.update(0.05);
            Cutscene.Line l = cs.line();
            if (l != null && l != last) out.add(l.speaker().name() + ": " + l.text());
            last = l;
        }
        return out;
    }

    @Test
    void theMenusSceneReadsAndPlays() throws Exception {
        Path script = Path.of("assets/cutscenes/menu.cut");
        Wardrobe mine = player();
        Cutscene cs = Cutscene.load(script, () -> mine);
        assertTrue(cs.loops());
        assertEquals(List.of("you", "bryn"), cs.actors().stream().map(Actor::id).toList());
        List<String> said = lines(cs, 12);
        assertTrue(said.size() >= 3, "lines in the first twelve seconds: " + said);
        assertTrue(said.get(0).startsWith("Bryn: "), said.toString());
        assertTrue(said.get(1).startsWith("You: "), said.toString());
        assertEquals("auburn", mine.colour(Slot.HAIR), "the player's own wardrobe is never touched");
    }

    @Test
    void windowsLineEndingsReadTheSame() throws Exception {
        // git on Windows checks the scripts out with \r\n line endings
        String script = Files.readString(Path.of("assets/cutscenes/menu.cut"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace("\n", "\r\n");
        Wardrobe mine = player();
        Cutscene cs = Cutscene.parse(script, () -> mine);
        assertEquals(List.of("you", "bryn"), cs.actors().stream().map(Actor::id).toList());
        assertEquals(List.of("colour", "you", "hair", "#3b7fe8", "1.2"),
                Cutscene.tokens("colour you hair #3b7fe8 1.2\r"));
        assertEquals(List.of("tint", "all", "#ffb070"), Cutscene.tokens("tint all #ffb070\r"));
    }

    @Test
    void sayPlaysItsClipForTheLineAndTheOthersListen() {
        Cutscene cs = Cutscene.parse("""
                actor a "Ada" body=feminine
                actor b "Bryn" body=feminine   # a comment after a command
                stand b right
                say a explain "One two three." 2
                say b "Four."
                """, CutsceneTest::player);
        cs.update(0.1);
        cs.update(0.1);
        Actor a = cs.actor("a"), b = cs.actor("b");
        assertEquals("explain", a.clip());
        assertEquals(Actor.IDLE_CLIP, b.clip());
        assertTrue(b.mirrored(), "on the right, facing left");
        assertEquals("One two three.", cs.line().text());
        assertEquals("One", cs.line().shown(cs.now()).substring(0, 3), "it types itself out");
        cs.update(1.9);
        assertEquals("talk", b.clip(), "the next line: Bryn talks");
        assertEquals(Actor.IDLE_CLIP, a.clip(), "and Ada listens again");
    }

    @Test
    void aMistakeNamesItsLine() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Cutscene.parse("actor a \"A\" body=feminine\nsay z \"hi\"\n", CutsceneTest::player));
        assertTrue(e.getMessage().startsWith("line 2:"), e.getMessage());
    }

    @Test
    void wordsQuotesColoursAndComments() {
        assertEquals(List.of("tint", "all", "#ffb070", "0.35"), Cutscene.tokens("tint all #ffb070 0.35  # warm"));
        assertEquals(List.of("say", "a", "\"Hi # there\""), Cutscene.tokens("say a \"Hi # there\""));
        assertEquals(List.of(), Cutscene.tokens("# just a comment"));
    }

    @Test
    void loopingStartsEveryoneOverAsTheyBegan() {
        Cutscene cs = Cutscene.parse("""
                actor a "Ada" body=feminine colour.hair=black
                wait 0.2
                colour a hair pink
                wait 1
                loop
                """, CutsceneTest::player);
        cs.update(0.3);
        cs.update(0.3);
        assertEquals("pink", cs.actor("a").wardrobe().colour(Slot.HAIR));
        cs.update(1.1);
        assertEquals("black", cs.actor("a").wardrobe().colour(Slot.HAIR), "the loop starts over");
    }

    /** Own labels 0 and 1, skin label 2. */
    static final String JSON = """
            {"labels": 3, "default": {},
             "channels": {
              "own": {"labels": [0, 1], "options": {"black": ["#000000", "#101010"], "white": ["#f0f0f0", "#ffffff"]},
                      "swatch": {"black": "#080808", "white": "#f8f8f8"}},
              "skin": {"labels": [2], "options": {"mint": ["#80e0a0"]}, "swatch": {"mint": "#80e0a0"}}}}
            """;

    @Test
    void aColourFadesOnTheLayersPaletteAndAFlashFadesOut() {
        Variants v = Variants.parse(JSON);
        int[] base = {0, 0x01000000, 0xFF404040, 0xFF505050, 0xFFC08060};
        Wardrobe w = new Wardrobe();
        w.setColour(Slot.HAIR, "black");
        Actor a = new Actor("a", "A", w);
        assertEquals(0xFF000000, a.palette(Slot.HAIR, base, v)[2]);
        a.colour(Slot.HAIR, "white", 1.0);
        a.update(0.5);
        int mid = a.palette(Slot.HAIR, base, v)[2] & 0xFF;
        assertTrue(mid > 0x40 && mid < 0xC0, "half way through the fade: " + Integer.toHexString(mid));
        assertEquals(0xFFC08060, a.palette(Slot.HAIR, base, v)[4], "the skin is not fading");
        a.update(0.6);
        assertEquals(0xFFF0F0F0, a.palette(Slot.HAIR, base, v)[2]);
        a.flash(0xFF0000, 0.4);
        assertEquals(0xFFFF0000, a.palette(Slot.HAIR, base, v)[2], "a flash starts at full strength");
        assertEquals(0x01000000, a.palette(Slot.HAIR, base, v)[1], "the empty-frame marker is left alone");
        a.update(0.5);
        assertEquals(0xFFF0F0F0, a.palette(Slot.HAIR, base, v)[2], "and is gone");
    }

    @Test
    void anyColourKeepsTheItemsShading() {
        Variants v = Variants.parse(JSON);
        Variants.Recolor r = v.recolor(java.util.Map.of("own", "#3b7fe8"));
        assertNotNull(r);
        int dark = r.rgb()[0], light = r.rgb()[1];
        assertTrue(Variants.isCustom("#3b7fe8") && !Variants.isCustom("royal_blue"));
        assertTrue(((dark & 0xFF) < (light & 0xFF)), "the darker label stays darker");
        assertTrue((light & 0xFF) > ((light >> 16) & 0xFF), "and both are blue");
    }

    @Test
    void closeUpsAreFoundByItemStyleAndClip() throws Exception {
        for (String f : List.of("hair/bob_px128/talk_side_se.png", "hair/bob_px128/listen_side_se.png",
                "body/feminine_px64/listen_side_se.png", "body/feminine_px64/notes.txt")) {
            Path p = root.resolve(f);
            Files.createDirectories(p.getParent());
            Files.writeString(p, "x", StandardCharsets.UTF_8);
        }
        CloseupLibrary lib = new CloseupLibrary(root);
        assertFalse(lib.isEmpty());
        assertEquals(List.of("listen", "talk"), lib.clips());
        CloseupLibrary.Entry hair = lib.entry(Slot.HAIR, "bob", Wardrobe.Style.PIXEL_64);
        assertEquals("bob_px128", hair.folder(), "no 64-pixel close-ups: the 128-pixel ones");
        assertEquals("body/feminine_px64", root.relativize(lib.entry(Slot.BODY, "feminine",
                Wardrobe.Style.PIXEL_64).clips().get("listen").getParent()).toString().replace('\\', '/'));
        assertTrue(lib.resolve(hair, "laugh", "listen", true).file().endsWith("listen_side_se.png"),
                "a clip the item lacks: its fallback");
        assertTrue(lib.resolve(hair, "talk", null, true).mirrored());
        assertNull(lib.entry(Slot.HAT, "beanie", Wardrobe.Style.PIXEL_128));
    }
}
