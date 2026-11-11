package com.larsons.game.grade;

import com.larsons.game.sprite.PixelPalette;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The colour grade: the 128 colours edited, shifted, merged down to a few, and the cube the picture is read in. */
class GradingTest {

    private static Grading fresh() {
        PixelPalette p = PixelPalette.load(Path.of("assets", "sprites"));
        assertNotNull(p, "the art's palette");
        int[] f = p.fills(), l = p.lines(), all = new int[f.length + l.length];
        System.arraycopy(f, 0, all, 0, f.length);
        System.arraycopy(l, 0, all, f.length, l.length);
        return new Grading(all);
    }

    @Test
    void nothingChangedIsNoGradeAtAll() {
        Grading g = fresh();
        assertEquals(128, g.size());
        assertTrue(g.identity());
        assertFalse(g.snap());
        assertArrayEquals(g.shown(), java.util.stream.IntStream.range(0, 128).map(g::original).toArray());
        // (read through the smooth cube, a colour comes back as it went in, to the cube's grain)
        int c = g.original(7), back = g.graded(c);
        for (int shift = 0; shift <= 16; shift += 8) {
            assertTrue(Math.abs(((c >> shift) & 255) - ((back >> shift) & 255)) <= 5);
        }
    }

    @Test
    void oneColourEditedMovesItAndLeavesTheRest() {
        Grading g = fresh();
        g.set(10, 0xFF00FF);
        assertFalse(g.identity());
        assertEquals("custom", g.preset());
        assertEquals(0xFF00FF, g.shown()[10]);
        assertEquals(g.original(11), g.shown()[11]);
        g.setSnap(true);
        assertEquals(0xFF00FF, g.graded(g.original(10)), "snapped: the palette colour itself becomes its new colour");
        assertEquals(g.original(11), g.graded(g.original(11)));
        g.reset(10);
        g.setSnap(false);
        assertTrue(g.identity());
    }

    @Test
    void thePaletteComesDownToAsFewAsTwoColours() {
        Grading g = fresh();
        for (int n : new int[]{64, 16, 8, 4, 2}) {
            g.setCount(n);
            assertEquals(n, g.distinct(), n + " colours left");
            assertTrue(g.snap(), "fewer than all of them: every pixel is one of them");
            Set<Integer> on = new HashSet<>();
            for (int rgb : new int[]{0x000000, 0xFFFFFF, 0x808080, 0x3366CC, 0xCC6633, 0x22AA44}) on.add(g.graded(rgb));
            Set<Integer> shown = new HashSet<>();
            for (int c : g.shown()) shown.add(c);
            assertTrue(shown.containsAll(on), "what is on the screen is of the " + n);
        }
        g.setCount(1);
        assertEquals(Grading.FEWEST, g.count());
        g.setCount(500);
        assertEquals(128, g.count());
    }

    @Test
    void thePresetsDoWhatTheySay() {
        Grading g = fresh();
        g.apply(Preset.GAMEBOY);
        assertEquals("gameboy", g.preset());
        assertEquals(4, g.distinct());
        assertTrue(g.snap());
        Set<Integer> greens = Set.of(0x0F380F, 0x306230, 0x8BAC0F, 0x9BBC0F);
        for (int c : g.shown()) assertTrue(greens.contains(c));
        assertTrue(greens.contains(g.graded(0x4477AA)));

        g.apply(Preset.TWO_COLOURS);
        assertEquals(2, g.distinct());

        g.apply(Preset.GREYSCALE);
        for (int c : g.shown()) {
            assertTrue(Math.abs(((c >> 16) & 255) - (c & 255)) <= 2 && Math.abs(((c >> 8) & 255) - (c & 255)) <= 2);
        }

        g.apply(Preset.ONE_COLOUR);
        assertTrue(g.colourise());
        float hue = Colours.hsv(g.graded(0x3355DD))[0];
        assertTrue(Math.abs(hue - 38) < 12, "a blue comes out the one hue, was " + hue);

        g.apply(Preset.ORIGINAL);
        assertTrue(g.identity());
        for (Preset p : Preset.values()) {
            g.apply(p);
            assertEquals(p.key(), g.preset());
            assertEquals(p == Preset.ORIGINAL, g.identity(), p.label());
            assertEquals(p, Preset.byKey(p.key()));
        }
    }

    @Test
    void aGradeIsSavedAndReadBack() throws Exception {
        Grading g = fresh();
        g.apply(Preset.N64);
        g.set(3, 0x123456);
        g.setCount(40);
        Path dir = Files.createTempDirectory("colours");
        Path file = dir.resolve("colours.json");
        g.save(file);
        Grading h = fresh();
        h.load(file);
        assertArrayEquals(g.shown(), h.shown());
        assertEquals(40, h.count());
        assertEquals(g.saturation(), h.saturation());
        assertEquals("custom", h.preset());
    }

    @Test
    void huesTurnAndComeBack() {
        for (int rgb : new int[]{0xFF0000, 0x00FF00, 0x0000FF, 0x808080, 0x123456, 0xFFFFFF, 0x000000}) {
            float[] hsv = Colours.hsv(rgb);
            assertEquals(rgb, Colours.rgb(hsv[0], hsv[1], hsv[2]));
        }
        Grading g = fresh();
        g.shift(120, 1, 1, 1, false);
        float h = Colours.hsv(g.shifted(0xFF0000))[0];
        assertEquals(120, h, 0.5);
    }
}
