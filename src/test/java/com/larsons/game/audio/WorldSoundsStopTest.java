package com.larsons.game.audio;

import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Weather;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Leaving a scene - back to the editor from a play-test, to the menu, to the
 * map - silences every sound the world was holding: the breeze, the biome's
 * bed (the plains' wind in the grass), the birdsong and the weather. Before,
 * the biome beds and the weather were never stopped and played on under the
 * editor and the menus.
 */
class WorldSoundsStopTest {

    @Test
    void leavingSilencesTheWindTheBirdsAndTheWeather() {
        World world = new World(new com.larsons.game.sprite.Wardrobe());
        world.clock().setHour(12);                                   // noon: the birds sing
        world.weatherNow().set(Weather.Kind.RAIN, 1);                 // and it pours
        OrbitCamera camera = new OrbitCamera(0.45, Math.toRadians(45), 6.5);
        WorldSounds sounds = new WorldSounds();
        for (int i = 0; i < 30; i++) {
            world.tick(1 / 30.0);
            world.weatherNow().set(Weather.Kind.RAIN, 1);
            sounds.update(world, camera);
        }
        List<String> held = sounds.held();
        assertTrue(held.contains("ambient/wind"), "the breeze: " + held);
        assertTrue(held.contains("ambient/plains"), "the plains' wind in the grass: " + held);
        assertTrue(held.contains("ambient/birdsong"), "the birds by day: " + held);
        assertTrue(held.contains("ambient/rain"), "the rain: " + held);

        sounds.stop();
        assertEquals(List.of(), sounds.held(), "nothing sounds on after leaving");

        // come back (the editor's play-test again): it all starts afresh, fading in
        sounds.update(world, camera);
        assertTrue(sounds.held().contains("ambient/plains"));
        sounds.stop();
        assertEquals(List.of(), sounds.held());
    }

    @Test
    void thunderRingsOutOnceAndTheHeldSoundsStillLoop() {
        assertFalse(SoundKeys.isLooping(SoundKeys.ambient("thunder")), "a clap nobody holds must not loop for ever");
        for (String held : List.of("wind", "plains", "birdsong", "rain", "gale", "stream", "crickets")) {
            assertTrue(SoundKeys.isLooping(SoundKeys.ambient(held)), held);
        }
    }
}
