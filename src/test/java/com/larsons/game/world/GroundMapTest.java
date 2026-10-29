package com.larsons.game.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The map of what the ground is made of: round patches and strokes of path
 * whose edge is where the weight crosses a half, the paths over everything,
 * grass off the grid, and the bytes the GPU is given.
 */
class GroundMapTest {

    @Test
    void aDiscCoversItsRoundAndEndsAtItsEdge() {
        GroundMap m = new GroundMap().disc(GroundMap.GRAVEL, 10, -5, 3);
        assertEquals(1, m.weight(GroundMap.GRAVEL, 10, -5), 1e-6, "the middle");
        assertEquals(0.5, m.weight(GroundMap.GRAVEL, 13, -5), 0.06, "the edge");
        assertEquals(0, m.weight(GroundMap.GRAVEL, 14, -5), 1e-6, "beyond it");
        assertEquals(0, m.weight(GroundMap.DIRT, 10, -5), 1e-6, "only gravel");
        assertEquals(GroundMap.GRAVEL, m.material(11, -4));
        assertEquals(-1, m.material(20, 20), "grass elsewhere");
        assertFalse(m.isEmpty());
        assertTrue(new GroundMap().isEmpty());
    }

    @Test
    void aStrokeIsAPathWithRoundEnds() {
        GroundMap m = new GroundMap().stroke(GroundMap.DIRT, 0, 0, 8, 0, 0.65);
        for (double x = 0; x <= 8; x += 0.5) assertEquals(GroundMap.DIRT, m.material(x, 0), "along it at " + x);
        assertEquals(GroundMap.DIRT, m.material(4, 0.5), "across it");
        assertEquals(-1, m.material(4, 1.2), "beside it");
        assertEquals(-1, m.material(9.2, 0), "past its end");
    }

    @Test
    void thePathsRunOverEverythingElse() {
        GroundMap m = new GroundMap().disc(GroundMap.FOREST, 0, 0, 5).disc(GroundMap.MEADOW, 0, 0, 5)
                .stroke(GroundMap.DIRT, -6, 0, 6, 0, 0.6);
        assertEquals(GroundMap.DIRT, m.material(0, 0));
        assertEquals(GroundMap.FOREST, m.material(0, 3), "the forest floor over the meadow");
    }

    @Test
    void offTheGridItIsGrassAndTheShapesAreClipped() {
        double edge = GroundMap.origin() + GroundMap.SIZE;
        GroundMap m = new GroundMap().disc(GroundMap.MEADOW, edge - 1, 0, 4);
        assertEquals(GroundMap.MEADOW, m.material(edge - 1, 0));
        assertEquals(0, m.weight(GroundMap.MEADOW, edge + 2, 0), 1e-9);
        assertThrows(UnsupportedOperationException.class, () -> GroundMap.NONE.disc(GroundMap.DIRT, 0, 0, 1));
    }

    @Test
    void theGpuGetsFourBytesACellRowByRowFromTheNorthWest() {
        GroundMap m = new GroundMap().disc(GroundMap.FOREST, 0, 0, 2);
        byte[] b = m.rgba();
        assertEquals(GroundMap.CELLS * GroundMap.CELLS * 4, b.length);
        int c = (int) Math.floor((0.1 - GroundMap.origin()) / GroundMap.CELL);
        int r = (int) Math.floor((0.1 - GroundMap.origin()) / GroundMap.CELL);
        int i = (r * GroundMap.CELLS + c) * 4;
        assertEquals(0, b[i + GroundMap.DIRT]);
        assertEquals((byte) 255, b[i + GroundMap.FOREST]);
        assertEquals(0, b[0 + GroundMap.FOREST], "the far corner is grass");
    }
}
