package com.larsons.game.core;

import com.larsons.game.gfx.Images;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.tools.PackSprites;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** The shared copy's sprites in one file: packed, then read in place as if they were folders. */
class SpritePackTest {

    @TempDir
    Path repo;
    @TempDir
    Path shared;

    private void png(Path root, String rel, int w, int h) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", p.toFile());
    }

    @Test
    void packsTheSpritesFoldersOnlyStoredUnderTheirOwnPaths() throws Exception {
        png(repo, "sprites/body/hero/walk_side_e.png", 64, 32);
        png(repo, "sprites_masculine/body/masculine/walk_side_e.png", 64, 32);
        png(repo, "animations_v1/sprites/body/hero/walk_side_e.png", 64, 32);
        png(repo, "scenery/tree.png", 8, 8);
        Files.writeString(repo.resolve("sprites/.gitkeep"), "");

        Path pack = shared.resolve(SpritePack.FILE);
        PackSprites.Stats s = PackSprites.pack(repo, pack);
        assertEquals(3, s.files());
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            List<String> names = zip.stream().map(ZipEntry::getName).filter(n -> !n.endsWith("/")).sorted().toList();
            assertEquals(List.of("animations_v1/sprites/body/hero/walk_side_e.png",
                    "sprites/body/hero/walk_side_e.png",
                    "sprites_masculine/body/masculine/walk_side_e.png"), names);
            assertTrue(zip.stream().allMatch(e -> e.getMethod() == ZipEntry.STORED), "stored, not compressed");
        }
    }

    @Test
    void aSpritesFolderWinsOverThePack() throws Exception {
        png(repo, "sprites/body/hero/walk_side_e.png", 64, 32);
        PackSprites.pack(repo, repo.resolve(SpritePack.FILE));
        assertEquals(repo, SpritePack.rootFor(repo));
        assertEquals(shared, SpritePack.rootFor(shared), "neither: the folder itself");
    }

    @Test
    void theLibraryIndexesAndReadsSheetsInsideThePack() throws Exception {
        png(repo, "sprites/body/hero/walk_side_e.png", 64, 32);
        png(repo, "sprites/hat/cap/walk_side_e.png", 64, 32);
        png(repo, "sprites_masculine/body/masculine/walk_side_s.png", 48, 24);
        Path assets = Files.createDirectories(shared.resolve("assets"));
        PackSprites.pack(repo, assets.resolve(SpritePack.FILE));

        Path root = SpritePack.rootFor(assets);
        assertTrue(SpritePack.isPacked(root));
        Path sprites = root.resolve("sprites");
        try (SpriteLibrary lib = new SpriteLibrary(sprites, 1L << 30, 1.0, false, SpriteLibrary.ScanProgress.NONE)) {
            assertEquals(List.of("hero", "masculine"), lib.items(Slot.BODY), "the body's own folder beside it too");
            assertEquals(List.of("cap"), lib.items(Slot.HAT));
            SpriteLibrary.Resolved r = lib.resolve(Slot.BODY, "hero", AnimState.WALK, Elevation.SIDE, Facing.EAST);
            assertNotNull(r);
            assertTrue(SpritePack.isPacked(r.file()));
            BufferedImage img = Images.read(r.file());
            assertEquals(64, img.getWidth());
            assertEquals(32, img.getHeight());
        }
    }

    @Test
    void imagesReadsFromDiskAsBefore() throws Exception {
        png(repo, "a.png", 5, 7);
        BufferedImage img = Images.read(repo.resolve("a.png"));
        assertEquals(5, img.getWidth());
        assertFalse(SpritePack.isPacked(repo.resolve("a.png")));
    }

    /** A few sheets, packed whole into {@code whole} and in parts of {@code partBytes} into {@code assets}. */
    private byte[] packBothWays(Path assets, long partBytes) throws Exception {
        for (int i = 0; i < 6; i++) png(repo, "sprites/body/hero/walk_side_" + i + ".png", 40 + i, 20);
        png(repo, "sprites_masculine/body/masculine/walk_side_e.png", 48, 24);
        Path whole = Files.createDirectories(repo.resolve("whole")).resolve(SpritePack.FILE);
        PackSprites.pack(repo, whole);
        Files.createDirectories(assets);
        PackSprites.Stats s = PackSprites.pack(repo, assets.resolve(SpritePack.FILE), partBytes);
        byte[] bytes = Files.readAllBytes(whole);
        assertEquals(bytes.length, s.packBytes());
        assertEquals((bytes.length + partBytes - 1) / partBytes, s.parts());
        return bytes;
    }

    private static String join(Path assets, boolean ok) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(ok, SpritePack.joinParts(assets, new PrintStream(out, true, StandardCharsets.UTF_8)));
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Path part(Path assets, int i) {
        return SpritePack.part(assets.resolve(SpritePack.FILE), i);
    }

    @Test
    void thePartsJoinIntoTheVeryPackAndGo() throws Exception {
        Path assets = shared.resolve("assets");
        byte[] whole = packBothWays(assets, 700);
        Path pack = assets.resolve(SpritePack.FILE);
        assertFalse(Files.exists(pack));
        assertTrue(Files.exists(part(assets, 1)));
        assertTrue(Files.readString(SpritePack.partsList(pack)).contains("sprites.pack.001 700"));

        String said = join(assets, true);
        assertTrue(said.contains("only the first time"), said);
        assertArrayEquals(whole, Files.readAllBytes(pack));
        try (var left = Files.list(assets)) {
            assertEquals(List.of(SpritePack.FILE), left.map(p -> p.getFileName().toString()).toList(),
                    "the parts and their list are gone");
        }
        assertEquals("", join(assets, true), "once only");
    }

    @Test
    void theGameJoinsThePartsWhenItFirstLooksForItsSprites() throws Exception {
        Path assets = shared.resolve("assets");
        packBothWays(assets, 900);
        Path root = SpritePack.rootFor(assets);
        assertTrue(SpritePack.isPacked(root));
        assertTrue(Files.isRegularFile(root.resolve("sprites/body/hero/walk_side_3.png")));
        assertTrue(Files.isRegularFile(root.resolve("sprites_masculine/body/masculine/walk_side_e.png")));
    }

    @Test
    void joiningCarriesOnWhereItWasStopped() throws Exception {
        Path assets = shared.resolve("assets");
        byte[] whole = packBothWays(assets, 500);
        Path joining = assets.resolve(SpritePack.FILE + SpritePack.JOINING_SUFFIX);
        // stopped half way through appending part 2: part 1 renamed, part 2 half copied and still there
        Files.move(part(assets, 1), joining);
        byte[] two = Files.readAllBytes(part(assets, 2));
        Files.write(joining, Arrays.copyOf(two, two.length / 2), StandardOpenOption.APPEND);
        join(assets, true);
        assertArrayEquals(whole, Files.readAllBytes(assets.resolve(SpritePack.FILE)));
    }

    @Test
    void joiningCarriesOnAfterAPartWasAppendedButNotYetDeleted() throws Exception {
        Path assets = shared.resolve("assets");
        byte[] whole = packBothWays(assets, 500);
        Path joining = assets.resolve(SpritePack.FILE + SpritePack.JOINING_SUFFIX);
        Files.move(part(assets, 1), joining);
        Files.write(joining, Files.readAllBytes(part(assets, 2)), StandardOpenOption.APPEND);
        Files.delete(part(assets, 2));
        // and part 3 appended, but the game stopped before deleting it
        Files.write(joining, Files.readAllBytes(part(assets, 3)), StandardOpenOption.APPEND);
        join(assets, true);
        assertArrayEquals(whole, Files.readAllBytes(assets.resolve(SpritePack.FILE)));
    }

    @Test
    void aNewerCopyUnzippedOverAnOlderOneBringsItsOwnPack() throws Exception {
        Path assets = shared.resolve("assets");
        byte[] whole = packBothWays(assets, 600);
        Files.writeString(assets.resolve(SpritePack.FILE), "the old copy's pack");
        Files.writeString(assets.resolve(SpritePack.FILE + SpritePack.JOINING_SUFFIX), "an old half join");
        join(assets, true);
        assertArrayEquals(whole, Files.readAllBytes(assets.resolve(SpritePack.FILE)));
    }

    @Test
    void aPartThatDidNotUnzipIsSaidAndTheGameRunsWithoutThePack() throws Exception {
        Path assets = shared.resolve("assets");
        packBothWays(assets, 500);
        Files.write(part(assets, 2), new byte[10]);
        String said = join(assets, false);
        assertTrue(said.contains("sprites.pack.002") && said.contains("unzip"), said);
        assertFalse(Files.exists(assets.resolve(SpritePack.FILE)));

        Files.delete(part(assets, 2));
        said = join(assets, false);
        assertTrue(said.contains("sprites.pack.002 is missing"), said);
        assertEquals(assets, SpritePack.rootFor(assets), "no sprites, but the game still starts");
    }
}
