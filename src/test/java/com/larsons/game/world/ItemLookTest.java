package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.ItemSprites;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.Slot;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How an item lying in the world looks (its beam, halo and border motes, the
 * frame it turns at, how high it floats) and the weapon in her hands drawn in
 * its tier - the parts that run without a GPU.
 */
class ItemLookTest {

    static World.GroundItem thrown(Item item, double thrownAt) {
        Chest chest = new Chest("ornate_treasure_chest", new Vec3(0, 0, 0), 0);
        return new World.GroundItem(item, new Vec3(2, 0, 0), 0.4, chest, new Vec3(0, 0.55, 0), thrownAt);
    }

    @Test
    void aBeamRisesOnlyOnceChestLootHasLandedAndPulsesWithoutGoingOut() {
        World.GroundItem g = thrown(Item.of(ItemDef.BATTLE_AXE, Tier.CRYSTAL), 1.0);
        assertEquals(0, ItemRenderer.beamStrength(g, 1.2), "still in the air");
        double landed = g.landedAt();
        assertEquals(1.0 + World.FLIGHT, landed, 1e-9);
        assertEquals(0, ItemRenderer.beamStrength(g, landed), 1e-9);
        double half = ItemRenderer.beamStrength(g, landed + ItemRenderer.BEAM_RISE / 2);
        assertTrue(half > 0.4 && half < 0.6, "rising: " + half);
        assertEquals(1, ItemRenderer.beamStrength(g, landed + 5), 1e-9);

        World.GroundItem lying = new World.GroundItem(Item.of(ItemDef.SWORD), new Vec3(0, 0, 0), 0);
        assertEquals(0, ItemRenderer.beamStrength(lying, 100), "an item lying about has no beam");

        for (double t = 0; t < 5; t += 0.01) {
            double p = ItemRenderer.pulse(t, 0.3);
            assertTrue(p >= 0.5 - 1e-9 && p <= 1 + 1e-9, "pulse " + p);
        }
    }

    @Test
    void theFrameFollowsTheSpinAndWrapsRound() {
        assertEquals(0, ItemRenderer.frame(0, 32));
        assertEquals(8, ItemRenderer.frame(0.25, 32));
        assertEquals(31, ItemRenderer.frame(0.999, 32));
        assertEquals(0, ItemRenderer.frame(3.0, 32));
        assertEquals(31, ItemRenderer.frame(-0.01, 32), "before the start it wraps back");
        assertEquals(0, ItemRenderer.frame(0.7, 0), "no frames: frame 0");
    }

    @Test
    void sparksStayInTheBeamAndMotesStayRoundTheItem() {
        for (int i = 0; i < ItemRenderer.SPARKS; i++) {
            for (double t = 0; t < 20; t += 0.37) {
                ItemRenderer.Mote m = ItemRenderer.spark(i, t, 1.1);
                assertTrue(Math.abs(m.x()) < ItemRenderer.BEAM_WIDTH / 2, "across " + m.x());
                assertTrue(m.y() >= 0 && m.y() <= ItemRenderer.BEAM_HEIGHT, "up " + m.y());
                assertTrue(m.alpha() >= 0 && m.alpha() <= 1, "alpha " + m.alpha());
            }
        }
        double length = 1.5;
        for (Border b : Border.values()) {
            double lowest = 0, highest = 0;
            for (int i = 0; i < ItemRenderer.MOTES; i++) {
                for (double t = 0; t < 20; t += 0.13) {
                    ItemRenderer.Mote m = ItemRenderer.mote(b, i, t, 0.7, length);
                    assertTrue(m.alpha() >= 0 && m.alpha() <= 1, b + " alpha " + m.alpha());
                    assertTrue(Math.abs(m.x()) < length, b + " across " + m.x());
                    assertTrue(m.size() > 0 && m.size() < 0.5, b + " size " + m.size());
                    lowest = Math.min(lowest, m.y());
                    highest = Math.max(highest, m.y());
                }
            }
            if (b == Border.VENOM) assertTrue(lowest < -length / 2, "Venom drips fall below the item");
            else assertTrue(highest > length / 4, b + " rises over the item");
        }
    }

    @Test
    void theLightsTexturesHaveTheirShapes() {
        int[] beam = ItemRenderer.beamPixels(32, 128);
        int mid = 16, edge = 0;
        assertTrue(a(beam[100 * 32 + mid]) > a(beam[20 * 32 + mid]), "brighter low than high");
        assertTrue(a(beam[100 * 32 + mid]) > a(beam[100 * 32 + 6]), "brighter in the middle");
        assertTrue(a(beam[100 * 32 + edge]) < 8, "nothing at its edge");
        assertTrue(a(beam[mid]) < 8, "nothing at its top");
        int[] dot = ItemRenderer.dotPixels(32);
        assertEquals(0, a(dot[0]));
        assertTrue(a(dot[16 * 32 + 16]) > 240);
        int[] heart = ItemRenderer.heartPixels(32);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(a(heart[y * 32 + x]), a(heart[y * 32 + 31 - x]), "a heart is the same both sides");
            }
        }
        assertTrue(a(heart[18 * 32 + 16]) == 255, "filled in the middle");
        assertEquals(0, a(heart[3 * 32 + 16]), "the notch at its top");
        int[] star = ItemRenderer.starPixels(32);
        assertTrue(a(star[16 * 32 + 3]) > a(star[3 * 32 + 3]), "a ray, not a disc");
    }

    static int a(int argb) {
        return argb >>> 24;
    }

    @Test
    void aLongItemFloatsClearOfTheGround() {
        for (ItemDef d : ItemDef.ALL) {
            World.GroundItem g = new World.GroundItem(Item.of(d), new Vec3(0, 0, 0), 0);
            for (double t = 0; t < 10; t += 0.05) {
                double lowest = g.hover(t) - d.worldSize() / 2;
                assertTrue(lowest >= World.CLEARANCE - World.BOB - 1e-9, d.id() + " at " + t + ": " + lowest);
                assertTrue(g.hover(t) >= World.HOVER - World.BOB - 1e-9);
            }
        }
    }

    @Test
    void theWeaponInHerHandsIsDrawnInItsTier() {
        ItemSprites items = new ItemSprites(Path.of("assets", "sprites"));
        int[] axe = new int[256], shirt = new int[256];
        for (int i = 2; i < 2 + 12; i++) axe[i] = 0xFF000000 | (0x6B4A2A + i);  // a brown handle, then a blade
        axe[0] = 0;
        LayerStack.Layer hand = new LayerStack.Layer(Slot.CARRY_RIGHT, "battle_axe", null, 0, false, axe);
        LayerStack.Layer leftHand = new LayerStack.Layer(Slot.CARRY_LEFT, "battle_axe_lh", null, 0, false, axe);
        LayerStack.Layer worn = new LayerStack.Layer(Slot.SHIRT, "battle_axe", null, 0, false, shirt);
        LayerStack.Layer sword = new LayerStack.Layer(Slot.CARRY_RIGHT, "sword", null, 0, false, axe);
        LayerStack.Result stack = new LayerStack.Result(List.of(worn, hand, leftHand, sword), null, null,
                null, null, 0, 1, 10, false, false);

        LayerStack.Result sharper = WorldRenderer.inTier(stack, Item.of(ItemDef.BATTLE_AXE, Tier.SHARPER), items);
        assertNotSame(stack, sharper);
        assertSame(shirt, sharper.layers().get(0).palette(), "not a carried slot");
        assertNotSame(axe, sharper.layers().get(1).palette(), "the axe in her right hand");
        assertSame(sharper.layers().get(1).palette(), sharper.layers().get(2).palette(), "and in her left");
        assertSame(axe, sharper.layers().get(3).palette(), "another weapon");
        assertTrue(Integer.compareUnsigned(sharper.layers().get(1).palette()[3] & 0xFFFFFF,
                axe[3] & 0xFFFFFF) != 0, "the handle recoloured");

        assertSame(stack, WorldRenderer.inTier(stack, Item.of(ItemDef.BATTLE_AXE), items), "Basic: as drawn");
        assertSame(stack, WorldRenderer.inTier(stack, null, items), "nothing wielded");
        assertNull(WorldRenderer.inTier(null, Item.of(ItemDef.BATTLE_AXE, Tier.PERFECT), items));
        assertTrue(WorldRenderer.isCarried(hand, "battle_axe"));
        assertTrue(WorldRenderer.isCarried(leftHand, "battle_axe"));
        assertFalse(WorldRenderer.isCarried(worn, "battle_axe"));
    }
}
