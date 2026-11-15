package com.larsons.game.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Stance;
import org.junit.jupiter.api.Test;

/** The new game's picture of the character: every pose has sprites to play, whatever the class carries. */
class PortraitTest {

    private static final Stance[] CARRIED = {
        Stance.SWORD, Stance.BLADE, Stance.AXE, Stance.BOW, Stance.CROSSBOW, Stance.STAFF, Stance.WAND, Stance.MACE,
        Stance.DAGGER
    };

    @Test
    void everyPoseHasAStateWhateverIsCarried() {
        for (Portrait.Pose pose : Portrait.Pose.values()) {
            assertNotNull(Portrait.state(pose, null), pose + " with nothing in hand");
            for (Stance carried : CARRIED) {
                assertNotNull(Portrait.state(pose, carried), pose + " with " + carried);
            }
        }
    }

    @Test
    void aMoveTheWeaponHasNoneOfIsTheSwordAndShields() {
        // a knight's lone sword has no guard, and only the sword and shield a bash
        assertEquals(AnimState.SHIELD_READY, Portrait.state(Portrait.Pose.BLOCK, Stance.BLADE));
        assertEquals(AnimState.SHIELD_BASH, Portrait.state(Portrait.Pose.BASH, Stance.BLADE));
        assertEquals(AnimState.SHIELD_BASH, Portrait.state(Portrait.Pose.BASH, Stance.STAFF));
        assertEquals(AnimState.SPIN_ATTACK, Portrait.state(Portrait.Pose.SPIN, Stance.BOW));
        // what the weapon does have stays its own
        assertEquals(AnimState.STAFF_BLOCK, Portrait.state(Portrait.Pose.BLOCK, Stance.STAFF));
        assertEquals(AnimState.BLADE_ATTACK, Portrait.state(Portrait.Pose.ATTACK, Stance.BLADE));
        // and with nothing carried, the armed poses are the sword and shield's
        assertEquals(AnimState.SHIELD_READY, Portrait.state(Portrait.Pose.BLOCK, null));
        assertEquals(AnimState.IDLE, Portrait.state(Portrait.Pose.STAND, null));
    }
}
