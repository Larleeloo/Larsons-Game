package com.larsons.game.importer;

import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.Slot;
import com.larsons.game.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpriteImportTest {

    @TempDir
    Path tmp;

    private Path png(Path file, int w, int h) throws Exception {
        Files.createDirectories(file.getParent());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFFFF0000);
        ImageIO.write(img, "png", file.toFile());
        return file;
    }

    @Test
    void aFolderOfSheetsIsOneItemInTheLayerItsParentNames() throws Exception {
        Path drop = tmp.resolve("renders/hat/straw_boater");
        png(drop.resolve("walk_side_n.png"), 512 * 30, 512);
        png(drop.resolve("Walk-45-NE.png"), 512 * 30, 512);
        Files.writeString(drop.resolve("notes.txt"), "not a sprite");
        png(drop.resolve("walk_side.png"), 512, 512);   // no direction

        SpriteImport.Plan plan = SpriteImport.plan(List.of(drop));
        assertEquals(Slot.HAT, plan.slotGuess());
        assertEquals("straw_boater", plan.itemGuess());
        assertEquals(2, plan.sheets().size());
        assertEquals(30, plan.sheets().get(0).frames(512, 512));
        assertEquals(2, plan.unrecognised().size());
    }

    @Test
    void savingCopiesSheetsUnderCanonicalNames() throws Exception {
        Path drop = tmp.resolve("hero");
        png(drop.resolve("Run-Top-S.png"), 512 * 20, 512);
        SpriteImport.Plan plan = SpriteImport.plan(List.of(drop));
        Path sprites = tmp.resolve("assets/sprites");
        assertEquals(0, SpriteImport.wouldReplace(plan, sprites, Slot.BODY, "hero"));

        SpriteImport.Result r = SpriteImport.save(plan, sprites, Slot.BODY, "hero", null);
        assertEquals(1, r.written());
        assertTrue(r.problems().isEmpty(), r.problems().toString());
        assertTrue(Files.exists(sprites.resolve("body/hero/run_top_s.png")));
        assertFalse(Files.exists(sprites.resolve("body/hero/profile.json")), "512 frames need no profile");
        assertEquals(1, SpriteImport.wouldReplace(plan, sprites, Slot.BODY, "hero"));
    }

    @Test
    void looseNumberedFramesAreStitchedInOrderWithTheirFrameSize() throws Exception {
        Path drop = tmp.resolve("frames");
        // Written out of order on purpose.
        for (int i : new int[]{3, 1, 2, 4}) {
            BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, 0xFF000000 | i);
            Files.createDirectories(drop);
            ImageIO.write(img, "png", drop.resolve(String.format("hero_idle_side_e_%04d.png", i)).toFile());
        }
        SpriteImport.Plan plan = SpriteImport.plan(List.of(drop));
        assertEquals(1, plan.sheets().size());
        SpriteImport.SheetPlan s = plan.sheets().get(0);
        assertTrue(s.sequence());
        assertEquals(AnimState.IDLE, s.state());
        assertEquals(Elevation.SIDE, s.elevation());
        assertEquals(Facing.EAST, s.facing());

        Path sprites = tmp.resolve("assets/sprites");
        SpriteImport.save(plan, sprites, Slot.BODY, "hero", null);
        BufferedImage sheet = ImageIO.read(sprites.resolve("body/hero/idle_side_e.png").toFile());
        assertEquals(64 * 4, sheet.getWidth());
        assertEquals(64, sheet.getHeight());
        for (int i = 0; i < 4; i++) assertEquals(i + 1, sheet.getRGB(i * 64, 0) & 0xFF, "frame order");
        Map<String, Object> profile = Json.asObject(Json.parse(
                Files.readString(sprites.resolve("body/hero/profile.json"))));
        assertEquals(64.0, profile.get("frameWidth"));
    }

    @Test
    void anIconIsAPickupSprite() throws Exception {
        Path drop = tmp.resolve("sword");
        png(drop.resolve("icon.png"), 32, 32);
        png(drop.resolve("idle_middle_s.png"), 512, 512);
        SpriteImport.Plan plan = SpriteImport.plan(List.of(drop));
        assertEquals(Slot.CARRY_RIGHT, plan.slotGuess(), "a sword goes in the right hand");
        assertNotNull(plan.icon());
        Path sprites = tmp.resolve("assets/sprites");
        SpriteImport.save(plan, sprites, Slot.CARRY_RIGHT, "sword", null);
        assertTrue(Files.exists(sprites.resolve("items/sword/icon.png")));
        assertTrue(Files.exists(sprites.resolve("carry_right/sword/idle_middle_s.png")));
    }

    @Test
    void viewsThatDisagreeOnFrameCountAreFlagged() throws Exception {
        Path drop = tmp.resolve("hero");
        png(drop.resolve("walk_side_n.png"), 512 * 30, 512);
        png(drop.resolve("walk_side_s.png"), 512 * 24, 512);
        SpriteImport.Plan plan = SpriteImport.plan(List.of(drop));
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("frame count")), plan.warnings().toString());
    }
}
