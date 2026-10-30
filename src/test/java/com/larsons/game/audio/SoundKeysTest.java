package com.larsons.game.audio;

import com.larsons.game.sprite.AnimState;
import com.larsons.game.world.GroundMap;
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
        Sounds.useStore(null);
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
        Set<String> catalogued = new HashSet<>();
        for (SoundKeys.Entry e : SoundKeys.all()) assertTrue(catalogued.add(e.key()), "listed twice: " + e.key());
        for (String body : SoundKeys.BODIES) {
            for (AnimState s : AnimState.values()) assertTrue(catalogued.contains(SoundKeys.player(body, s)), s.key());
        }
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
        assertTrue(SoundKeys.isLooping("combat/flying_dummy/fly"), "its wings, on its rounds");
        assertFalse(SoundKeys.isLooping("combat/flying_dummy/swoop"));
        assertTrue(SoundKeys.isLooping("ambient/wind"));
        assertFalse(SoundKeys.isLooping("combat/blow"));
        assertFalse(SoundKeys.isLooping("player/footstep_grass"));
    }

    @Test
    void aMoveFallsBackToTheMoveItIsAVersionOfAndAFootstepToAnyFootstep() {
        List<String> heavy = SoundKeys.chain("player/feminine/axe_heavy_attack");
        assertEquals(List.of("player/feminine/axe_heavy_attack", "player/axe_heavy_attack",
                "player/feminine/axe_attack", "player/axe_attack"), heavy.subList(0, 4));
        assertEquals(List.of("player/masculine/emote_laugh", "player/emote_laugh"),
                SoundKeys.chain("player/masculine/emote_laugh"), "a laugh does not fall back to standing about");
        assertEquals(List.of("player/masculine/footstep_gravel", "player/footstep_gravel",
                "player/masculine/footstep", "player/footstep"), SoundKeys.chain("player/masculine/footstep_gravel"));
        assertEquals(List.of("player/feminine/hurt", "player/hurt"), SoundKeys.chain("player/feminine/hurt"));
        for (AnimState s : AnimState.values()) {
            AnimState f = SoundKeys.soundFallback(s);
            if (f != null) assertEquals(s.loops(), f.loops(), s.key() + " borrows a sound of another kind");
        }
    }

    @Test
    void everySoundPlaysInTheCategoryWhoseTabListsIt() {
        // the mixer's volume groups go by categoryOf; the menu's tabs by the row's category
        for (SoundKeys.Entry e : SoundKeys.all()) {
            assertEquals(e.category(), SoundKeys.categoryOf(e.key()), e.key());
            if (e.listed()) assertTrue(SoundKeys.in(e.category()).contains(e), e.key());
        }
        for (SoundCategory c : SoundCategory.values()) {
            assertFalse(SoundKeys.in(c).isEmpty(), c + " has no sounds");
            assertEquals(c, SoundCategory.byKey(c.key()));
        }
        assertEquals(SoundCategory.WEAPONS, SoundKeys.categoryOf("player/feminine/axe_heavy_attack"));
        assertEquals(SoundCategory.VOICES, SoundKeys.categoryOf("player/masculine/emote_cry"));
        assertEquals(SoundCategory.VOICES, SoundKeys.categoryOf("player/feminine/hurt"));
        assertEquals(SoundCategory.MOVEMENT, SoundKeys.categoryOf("player/feminine/footstep_dirt"));
        assertEquals(SoundCategory.CREATURES, SoundKeys.categoryOf("combat/flying_dummy/swoop"));
        assertEquals(SoundCategory.HITS, SoundKeys.categoryOf("combat/spell_hit"));
        assertEquals(SoundCategory.WEAPONS, SoundKeys.categoryOf("item/sword/equip"));
        assertEquals(SoundCategory.WORLD, SoundKeys.categoryOf("world/loot_rare"));
        assertEquals(SoundCategory.AMBIENT, SoundKeys.categoryOf("ambient/stream"));
        assertEquals(SoundCategory.INTERFACE, SoundKeys.categoryOf("combat/lock_on"));
    }

    @Test
    void aRegisteredSoundGetsARowInItsTabUntilItIsForgotten() {
        try {
            SoundKeys.register(SoundCategory.AMBIENT, "ambient/test_waterfall", "Test waterfall", true);
            assertTrue(SoundKeys.in(SoundCategory.AMBIENT).stream().anyMatch(e -> e.key().equals("ambient/test_waterfall")));
            assertEquals(SoundCategory.AMBIENT, SoundKeys.categoryOf("ambient/test_waterfall"));
            assertTrue(SoundKeys.isLooping("ambient/test_waterfall"));
            assertEquals("ambient/test_waterfall.mp3", SoundKeys.preferredFile("ambient/test_waterfall"));
            SoundKeys.register(SoundCategory.INTERFACE, "ui/test_ping", "Test ping", false);
            assertFalse(SoundKeys.isLooping("ui/test_ping"));
            assertTrue(SoundPack.keysText().contains("ui/test_ping.mp3"));
        } finally {
            SoundKeys.unregister("ambient/test_waterfall");
            SoundKeys.unregister("ui/test_ping");
        }
        assertTrue(SoundKeys.in(SoundCategory.AMBIENT).stream().noneMatch(e -> e.key().equals("ambient/test_waterfall")));
        assertFalse(SoundPack.keysText().contains("ui/test_ping"));
    }

    @Test
    void thePlayersOwnFileBeatsTheGamesAndMutingSilencesWhatSharesIt(@TempDir Path dir) throws IOException {
        Path pack = dir.resolve("pack"), config = dir.resolve("config");
        Files.createDirectories(pack.resolve("player/feminine"));
        Files.createDirectories(config);
        Files.write(pack.resolve("player/attack.mp3"), new byte[]{1});
        Path mine = Files.write(dir.resolve("my_swing.mp3"), new byte[]{1});
        Path hers = Files.write(dir.resolve("her_swing.wav"), new byte[]{1});
        SoundPack.useDir(pack);
        Sounds.useStore(config);

        Sounds.Resolution r = Sounds.describe("player/feminine/attack");
        assertEquals(Sounds.Source.PACK, r.source());
        assertEquals("player/attack", r.level());
        assertTrue(r.shared("player/feminine/attack"));

        Sounds.choose("player/attack", mine);   // the shared swing: both bodies
        assertEquals(Sounds.Source.FILE, Sounds.sourceOf("player/feminine/attack"));
        assertEquals(mine.toAbsolutePath(), Sounds.describe("player/masculine/attack").file());
        Sounds.choose("player/feminine/attack", hers);
        assertEquals(hers.toAbsolutePath(), Sounds.describe("player/feminine/attack").file());
        assertEquals(mine.toAbsolutePath(), Sounds.describe("player/masculine/attack").file());

        Sounds.mute("player/attack");
        assertEquals(Sounds.Source.MUTED, Sounds.sourceOf("player/masculine/attack"));
        assertEquals(Sounds.Source.FILE, Sounds.sourceOf("player/feminine/attack"), "her own still plays");

        assertTrue(Files.isRegularFile(config.resolve(SoundStore.FILE_NAME)), "saved as it was made");
        Sounds.clear();
        Sounds.load();
        assertEquals(Sounds.Source.MUTED, Sounds.sourceOf("player/masculine/attack"), "kept for next time");

        Sounds.reset("player/attack");
        assertEquals(Sounds.Source.PACK, Sounds.sourceOf("player/masculine/attack"));
        Sounds.reset("player/feminine/attack");
        assertNull(Sounds.get("player/feminine/attack"));
        assertEquals(Sounds.Source.PACK, Sounds.sourceOf("player/feminine/attack"));
    }

    @Test
    void aFootstepSoundsOfTheGroundUnderIt() {
        GroundMap ground = new GroundMap()
                .disc(GroundMap.GRAVEL, 10, 0, 2)
                .stroke(GroundMap.DIRT, -10, 0, -4, 0, 1)
                .disc(GroundMap.FOREST, 0, 10, 3)
                .disc(GroundMap.MEADOW, 0, -10, 3);
        assertEquals("gravel", WorldSounds.surface(ground, 10, 0));
        assertEquals("dirt", WorldSounds.surface(ground, -7, 0));
        assertEquals("forest", WorldSounds.surface(ground, 0, 10));
        assertEquals("grass", WorldSounds.surface(ground, 0, -10), "a meadow is grass underfoot");
        assertEquals("grass", WorldSounds.surface(ground, 30, 30));
        for (String surface : SoundKeys.SURFACES) {
            assertEquals("player/feminine/footstep_" + surface, SoundKeys.footstep("feminine_px64", surface));
        }
    }

    @Test
    void theRepositorysSoundsAreThereForEveryListedRowAndDecodeGaplessly() {
        // every listed row but reaching down for something (left to the swing of it) has a placeholder
        for (SoundKeys.Entry e : SoundKeys.all()) {
            if (!e.listed() || e.key().equals("player/pickup")) continue;
            assertEquals(Sounds.Source.PACK, Sounds.sourceOf(e.key()), e.key() + " has no file in assets/sounds");
        }
        // the encoder's delay and padding are trimmed: a loop is exactly as long as it was made
        PcmClip wind = Mp3Decoder.decode(readPack("ambient/wind.mp3"));
        assertEquals(8 * 44100, wind.frames());
        PcmClip click = Mp3Decoder.decode(readPack("ui/click.mp3"));
        assertEquals(Math.round(0.06 * 44100), click.frames());
    }

    private static byte[] readPack(String file) {
        try {
            return Files.readAllBytes(PACK.resolve(file));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
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
