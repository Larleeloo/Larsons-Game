package com.larsons.game.gfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The palette rows a batch draws from (the bookkeeping; the texture needs GL). */
class PaletteAtlasTest {

    @Test
    void identicalPalettesShareARowAndAFullAtlasSaysSo() {
        PaletteAtlas.Rows rows = new PaletteAtlas.Rows(2);
        int a = rows.add(new int[]{0, 0x01000000, 0xFF102030});
        assertEquals(a, rows.add(new int[]{0, 0x01000000, 0xFF102030}), "the same colours: the same row");
        int[] other = {0, 0x01000000, 0xFF405060};
        int b = rows.add(other);
        assertNotEquals(a, b);
        other[2] = 0xFFFFFFFF;
        assertEquals(0xFF405060, rows.get(b)[2], "a row keeps the colours it was given");
        assertEquals(-1, rows.add(new int[]{0xFF000000}), "full: every row is drawn in this batch");
        rows.clear();
        assertEquals(0, rows.add(new int[]{0xFF000000}), "cleared, the rows start over");
    }

    @Test
    void rowsOutliveTheBatchSoUnchangedColoursAreNotSentAgain() {
        PaletteAtlas.Rows rows = new PaletteAtlas.Rows(4);
        int a = rows.add(new int[]{0, 0xFF102030});
        int b = rows.add(new int[]{0, 0xFF405060});
        assertArrayEquals(new int[]{a, b}, rows.takeDirty(), "new rows are uploaded");
        rows.newBatch();
        assertEquals(a, rows.add(new int[]{0, 0xFF102030}), "the next frame finds the same row");
        assertEquals(b, rows.add(new int[]{0, 0xFF405060}));
        assertEquals(0, rows.takeDirty().length, "and nothing is uploaded again");
    }

    @Test
    void aFullAtlasReusesTheRowLongestUnused() {
        PaletteAtlas.Rows rows = new PaletteAtlas.Rows(2);
        int a = rows.add(new int[]{0, 0xFF000001});
        rows.newBatch();
        int b = rows.add(new int[]{0, 0xFF000002});
        rows.newBatch();
        rows.add(new int[]{0, 0xFF000002});               // b drawn again: a is the oldest
        rows.takeDirty();
        int c = rows.add(new int[]{0, 0xFF000003});
        assertEquals(a, c, "the row unused longest is given to the new colours");
        assertArrayEquals(new int[]{c}, rows.takeDirty());
        assertEquals(0xFF000003, rows.get(c)[1]);
        assertEquals(-1, rows.add(new int[]{0, 0xFF000004}), "both rows are drawn in this batch");
        rows.newBatch();
        rows.add(new int[]{0, 0xFF000003});
        int again = rows.add(new int[]{0, 0xFF000001});
        assertEquals(b, again, "colours that lost their row get one back: the row this batch does not draw");
    }
}
