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
import java.nio.file.Files;
import java.nio.file.Path;
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
}
