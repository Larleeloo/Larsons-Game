package com.larsons.game.audio;

import com.larsons.game.sprite.AnimState;
import com.larsons.game.world.ItemDef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The game's sound catalogue, the pack's storage and the animation hooks. */
class SoundKeysTest {

    /** The repository's sound pack (the tests run in the project folder). */
    private static final Path PACK = Path.of("assets", "sounds");

    @AfterEach
    void restore() {
        SoundPack.useDir(PACK);
        Sounds.clear();
    }

    @Test
    void everyAnimationOfBothBodiesHasASoundAndAFileOfItsOwn() {
        Set<String> files = new HashSet<>();
        for (String body : SoundKeys.BODIES) {
            for (AnimState s : AnimState.values()) {
                String key = SoundKeys.player(body, s);
                assertEquals("player/" + body + "/" + s.key(), key);
                assertEquals("player/" + body + "/" + s.key() + ".mp3", SoundKeys.preferredFile(key));
                assertTrue(files.add(SoundKeys.preferredFile(key)), key);
            }
        }
        assertEquals(2 * AnimState.values().length, files.size());
        long rows = SoundKeys.all().stream().filter(e -> e.key().startsWith("player/")).count();
        assertEquals(2 * AnimState.values().length, rows);
    }

    @Test
    void aBodyIsKnownByItsFolderInAnyStyle() {
        assertEquals("player/masculine/walk", SoundKeys.player("masculine_px128_lh", AnimState.WALK));
        assertEquals("player/feminine/walk", SoundKeys.player("feminine_px64", AnimState.WALK));
        assertEquals("player/feminine/walk", SoundKeys.player(null, AnimState.WALK),
                "the generated stand-in body sounds as the default body");
    }

    @Test
    void theChestsAndItemsAndTheInventoryHaveTheirSounds() {
        List<String> keys = SoundKeys.all().stream().map(SoundKeys.Entry::key).toList();
        for (String s : SoundKeys.CHEST_STATES) assertTrue(keys.contains("chest/ornate_chest/" + s), s);
        for (ItemDef d : ItemDef.ALL) assertTrue(keys.contains("item/" + d.id() + "/pickup"), d.id());
        assertTrue(keys.contains("ui/inventory_open"));
        assertEquals("chests/ornate_chest/open.mp3", SoundKeys.preferredFile("chest/ornate_chest/open"));
    }

    @Test
    void keysFallBackFromTheObjectToItsKind() {
        assertEquals(List.of("player/masculine/walk", "player/walk"), SoundKeys.paths("player/masculine/walk"));
        assertEquals(List.of("chests/ornate_chest/idle", "chests/idle"), SoundKeys.paths("chest/ornate_chest/idle"));
        assertEquals(List.of("ui/inventory_open"), SoundKeys.paths("ui/inventory_open"));
        assertEquals(List.of("other/what_ever"), SoundKeys.paths("what/ever"));
    }

    @Test
    void heldAnimationsLoopAndOneShotsDoNot() {
        assertTrue(SoundKeys.isLooping("player/feminine/walk"));
        assertTrue(SoundKeys.isLooping("player/masculine/axe_idle"));
        assertFalse(SoundKeys.isLooping("player/feminine/attack"));
        assertFalse(SoundKeys.isLooping("player/feminine/emote_laugh"));
        assertTrue(SoundKeys.isLooping("chest/ornate_chest/idle"));
        assertTrue(SoundKeys.isLooping("chest/ornate_chest/opened"));
        assertFalse(SoundKeys.isLooping("chest/ornate_chest/open"));
        assertFalse(SoundKeys.isLooping("ui/inventory_open"));
        assertTrue(SoundDef.packDefault("player/feminine/run").loop());
    }

    @Test
    void aFileInThePackIsFoundTheBodysOwnBeforeTheShared(@TempDir Path dir) throws IOException {
        SoundPack.useDir(dir);
        assertNull(SoundPack.fileFor("player/masculine/walk"), "silent until a file is there");
        assertEquals(Sounds.Source.SILENT, Sounds.sourceOf("player/masculine/walk"));
        Files.createDirectories(dir.resolve("player/masculine"));
        Files.write(dir.resolve("player/walk.mp3"), new byte[]{1});
        SoundPack.reload();
        assertEquals(dir.resolve("player/walk.mp3"), SoundPack.fileFor("player/masculine/walk"),
                "one file for both bodies' walks");
        Files.write(dir.resolve("player/masculine/walk.wav"), new byte[]{1});
        SoundPack.reload();
        assertEquals(dir.resolve("player/masculine/walk.wav"), SoundPack.fileFor("player/masculine/walk"));
        assertEquals(dir.resolve("player/walk.mp3"), SoundPack.fileFor("player/feminine/walk"));
        assertEquals(Sounds.Source.PACK, Sounds.sourceOf("player/feminine/walk"));
    }

    @Test
    void anOverrideInThePacksSettingsIsKept(@TempDir Path dir) throws IOException {
        SoundPack.useDir(dir);
        SoundPack.scaffold(dir);
        SoundPack.setOverride("chest/ornate_chest/idle", 0.4, 1.0, true, true);
        SoundPack.reload();
        assertEquals(0.4, SoundPack.playbackFor("chest/ornate_chest/idle").volume(), 1e-9);
        assertTrue(Files.readString(dir.resolve(SoundPack.CONFIG_FILE)).contains("chest/ornate_chest/idle"));
    }

    @Test
    void scaffoldingMakesAFolderForEveryObject(@TempDir Path dir) throws IOException {
        SoundPack.scaffold(dir);
        for (String folder : SoundKeys.folders()) assertTrue(Files.isDirectory(dir.resolve(folder)), folder);
        assertTrue(Files.isDirectory(dir.resolve("player/feminine")));
        assertTrue(Files.isDirectory(dir.resolve("player/masculine")));
        assertTrue(Files.isDirectory(dir.resolve("chests/ornate_chest")));
        assertTrue(Files.readString(dir.resolve(SoundPack.KEYS_FILE)).contains("player/masculine/axe_heavy_attack.mp3"));
    }

    @Test
    void theRepositorysKeyListAndReadmeAreUpToDate() throws IOException {
        // the game rewrites them when they change; this says when that is due
        assertEquals(SoundPack.keysText(), Files.readString(PACK.resolve(SoundPack.KEYS_FILE)),
                "assets/sounds/SOUND_KEYS.txt is stale: run the game once, or the test's scaffold");
        assertEquals(SoundPack.readmeText(), Files.readString(PACK.resolve(SoundPack.README_FILE)));
        for (String folder : SoundKeys.folders()) assertTrue(Files.isDirectory(PACK.resolve(folder)), folder);
    }

    @Test
    void anAnimationsSoundIsSafeWithNoAudioAndNoFiles(@TempDir Path dir) {
        SoundPack.useDir(dir);
        AnimationSound s = new AnimationSound();
        s.update("player/feminine/walk", 0.0, 1, 0);
        s.update("player/feminine/walk", 0.5, 0.5, -0.5);
        assertEquals("player/feminine/walk", s.key());
        s.update("player/feminine/attack", 0.0, 1, 0);
        s.update("player/feminine/attack", 0.2, 1, 0);
        s.update("player/feminine/attack", 0.0, 1, 0);   // a second swing
        assertEquals("player/feminine/attack", s.key());
        s.stop();
        assertEquals("", s.key());
    }

    @Test
    void aSoundIsQuieterFartherOffAndPannedToItsSide() {
        double[] here = Sounds.placement(0, 0, 4);
        assertEquals(1, here[0], 1e-9);
        assertEquals(0, here[1], 1e-9);
        double[] right = Sounds.placement(3, 3, 4);
        double[] far = Sounds.placement(-8, 10, 4);
        assertTrue(right[1] > 0.5);
        assertEquals(-1, far[1], 1e-9);
        assertTrue(far[0] < right[0] && right[0] < 1);
    }
}
