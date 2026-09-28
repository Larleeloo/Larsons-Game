package com.larsons.game.sprite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpriteNamesTest {

    private static void expect(String path, AnimState s, Elevation e, Facing f) {
        SpriteNames.Parsed p = SpriteNames.parse(path);
        assertEquals(s, p.state(), path);
        assertEquals(e, p.elevation(), path);
        assertEquals(f, p.facing(), path);
    }

    @Test
    void canonicalNamesRoundTrip() {
        for (AnimState s : AnimState.values()) {
            for (Elevation e : Elevation.values()) {
                for (Facing f : Facing.values()) {
                    String name = SpriteNames.fileName(s, e, f);
                    expect(name, s, e, f);
                    assertNull(SpriteNames.parse(name).frame());
                }
            }
        }
        assertEquals("walk_middle_ne.png", SpriteNames.fileName(AnimState.WALK, Elevation.MIDDLE, Facing.NORTH_EAST));
    }

    @Test
    void readsWhateverBlenderWasToldToCallIt() {
        expect("Walk-45-NorthEast.png", AnimState.WALK, Elevation.MIDDLE, Facing.NORTH_EAST);
        expect("hero/Run/Top/S.png", AnimState.RUN, Elevation.TOP, Facing.SOUTH);
        expect("WalkMiddleNE.png", AnimState.WALK, Elevation.MIDDLE, Facing.NORTH_EAST);
        expect("attack_high_front_left.png", AnimState.ATTACK, Elevation.TOP, Facing.SOUTH_WEST);
        expect("idle 0 back.png", AnimState.IDLE, Elevation.SIDE, Facing.NORTH);
        expect("sprinting_birds_eye_w.png", AnimState.SPRINT, Elevation.TOP, Facing.WEST);
        expect("jump_90deg_se.png", AnimState.JUMP, Elevation.TOP, Facing.SOUTH_EAST);
    }

    @Test
    void numberedFramesAreASequence() {
        SpriteNames.Parsed p = SpriteNames.parse("idle_side_front_0007.png");
        assertTrue(p.complete());
        assertEquals(7, p.frame());
        // A bare 45 is the elevation when nothing else names one; 12 is a frame.
        SpriteNames.Parsed q = SpriteNames.parse("walk_45_ne_12.png");
        assertEquals(Elevation.MIDDLE, q.elevation());
        assertEquals(12, q.frame());
    }

    @Test
    void handsAreSlotsNotDirections() {
        SpriteNames.Parsed p = SpriteNames.parse("left_hand/torch/walk_side_e.png");
        assertEquals(Slot.CARRY_LEFT, p.slot());
        assertEquals(Facing.EAST, p.facing());
        assertEquals(Slot.CARRY_RIGHT, SpriteNames.parse("carry_right_idle_top_n.png").slot());
    }

    @Test
    void leftoverWordsNameTheItem() {
        SpriteNames.Parsed p = SpriteNames.parse("hat_straw_boater_walk_side_n.png");
        assertEquals(Slot.HAT, p.slot());
        assertEquals(java.util.List.of("straw", "boater"), p.leftovers());
        assertFalse(SpriteNames.parse("walk_side.png").complete());
    }

    @Test
    void sanitizeMakesAFolderName() {
        assertEquals("straw_boater", SpriteNames.sanitize("  Straw Boater! "));
        assertEquals("item", SpriteNames.sanitize("???"));
        assertEquals("red-cap_2", SpriteNames.sanitize("Red-Cap 2"));
    }
}
