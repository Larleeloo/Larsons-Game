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
        assertEquals(-1, rows.add(new int[]{0xFF000000}), "full");
        rows.clear();
        assertEquals(0, rows.add(new int[]{0xFF000000}), "a new batch starts the rows over");
    }
}
