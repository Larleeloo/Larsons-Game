package com.larsons.game.gfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** How much less the world is drawn: the haze brought nearer, the wildlife thinned, the picture made smaller. */
class QualityTest {

    @Test
    void theHazeClosesInByTheViewDistanceButNeverNearerThanTheLeast() {
        Quality q = new Quality(1, 0.7, 1);
        assertEquals(56, q.fogFar(80), 1e-4);
        assertEquals(Quality.NEAREST_VIEW, new Quality(1, 0.5, 1).fogFar(30), 1e-4);
        assertEquals(20, q.fogFar(20), 1e-4, "a level whose haze is already near keeps it");
        assertEquals(80, Quality.FULL.fogFar(80), 1e-4);
    }

    @Test
    void theWildlifeIsThinnedByItsShare() {
        assertEquals(5, new Quality(1, 1, 0.5).some(10));
        assertEquals(0, new Quality(1, 1, 0).some(10));
        assertEquals(10, Quality.FULL.some(10));
    }

    @Test
    void theWorldIsDrawnSmallerOnlyWhenItsScaleIsUnderOne() {
        assertTrue(new Quality(0.5, 1, 1).scaled());
        assertFalse(Quality.FULL.scaled());
    }

    @Test
    void whatIsOutOfRangeIsHeldInIt() {
        Quality q = new Quality(0.1, 0.1, 2);
        assertEquals(0.25, q.renderScale());
        assertEquals(0.3, q.viewDistance());
        assertEquals(1, q.life());
        assertEquals(Quality.FULL, new Quality(Double.NaN, Double.NaN, Double.NaN));
    }
}
