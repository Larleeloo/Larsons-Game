package com.larsons.game.scene;

import com.larsons.game.world.Inventory;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Where the inventory screen puts its diamonds, for any number of slots. */
class InventoryPanelTest {

    @Test
    void thePanelIsTheBackgroundsShapeAndFitsTheWindow() {
        float[] p = InventoryPanel.panel(1280, 720);
        assertEquals(432 / 768f, p[2] / p[3], 1e-4);
        assertTrue(p[3] <= 720 - 48 + 1e-3);
        assertEquals(640, p[0] + p[2] / 2, 1e-3, "in the middle");
        float[] big = InventoryPanel.panel(3840, 2160);
        assertEquals(768, big[3], 1e-3, "never bigger than the picture");
        float[] narrow = InventoryPanel.panel(300, 900);
        assertTrue(narrow[2] <= 300 - 32 + 1e-3);
    }

    @Test
    void everySlotHasADiamondInsideThePanelAndNoneOverlap() {
        float[] p = InventoryPanel.panel(1280, 720);
        for (int size : new int[]{5, 6, 12, 25, 40, 64, 101}) {
            List<InventoryPanel.Spot> spots = InventoryPanel.spots(size, p[0], p[1], p[2], p[3]);
            assertEquals(size, spots.size(), size + " slots");
            Set<Integer> slots = new HashSet<>();
            for (InventoryPanel.Spot s : spots) {
                assertTrue(slots.add(s.slot()));
                assertTrue(s.r() > 3, "visible at " + size);
                assertTrue(s.x() - s.r() >= p[0] - 1e-3 && s.x() + s.r() <= p[0] + p[2] + 1e-3, "inside, " + size);
                assertTrue(s.y() - s.r() >= p[1] - 1e-3 && s.y() + s.r() <= p[1] + p[3] + 1e-3, "inside, " + size);
            }
            for (int i = 0; i < spots.size(); i++) {
                for (int j = i + 1; j < spots.size(); j++) {
                    InventoryPanel.Spot a = spots.get(i), b = spots.get(j);
                    // two diamonds overlap when their centres are nearer (in |dx| + |dy|) than their sizes
                    assertTrue(Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) >= a.r() + b.r() - 1e-3,
                            "slots " + a.slot() + " and " + b.slot() + " of " + size);
                }
            }
        }
    }

    @Test
    void theHotbarIsARowOfFiveAlongTheBottom() {
        float[] p = InventoryPanel.panel(1280, 720);
        List<InventoryPanel.Spot> spots = InventoryPanel.spots(25, p[0], p[1], p[2], p[3]);
        List<InventoryPanel.Spot> hotbar = spots.stream().filter(s -> s.slot() < Inventory.HOTBAR).toList();
        assertEquals(5, hotbar.size());
        float y = hotbar.get(0).y();
        for (InventoryPanel.Spot s : hotbar) assertEquals(y, s.y(), 1e-3);
        for (InventoryPanel.Spot s : spots) {
            if (s.slot() >= Inventory.HOTBAR) assertTrue(s.y() < y, "the rest above it");
        }
    }
}
