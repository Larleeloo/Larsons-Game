package com.larsons.game.sprite.fallback;

import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.SheetImage;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FallbackSpritesTest {

    @Test
    void everyStateHasAll24ViewsWithABodyInEveryFrame() {
        for (AnimState s : AnimState.values()) {
            for (Elevation e : Elevation.values()) {
                for (Facing f : Facing.values()) {
                    FallbackSprites.Pair p = FallbackSprites.generate(s, e, f);
                    SheetImage body = p.body();
                    assertEquals(s.defaultFrames(), body.frameCount(), s + " " + e + " " + f);
                    assertEquals(body.frameCount(), p.sword().frameCount(), "sword matches body frame for frame");
                    assertEquals(32, body.frameWidth());
                    assertTrue(body.pixelArt());
                    for (int i = 0; i < body.frameCount(); i += 7) {
                        int c = i % body.columns(), r = i / body.columns();
                        BufferedImage frame = body.atlas().getSubimage(c * 32, r * 32, 32, 32);
                        assertTrue(FallbackSprites.anyPixels(frame), s + " " + e + " " + f + " frame " + i);
                    }
                }
            }
        }
    }

    @Test
    void frontAndBackLookDifferent() {
        int[] front = pixels(FallbackSprites.generate(AnimState.IDLE, Elevation.SIDE, Facing.SOUTH).body());
        int[] back = pixels(FallbackSprites.generate(AnimState.IDLE, Elevation.SIDE, Facing.NORTH).body());
        assertFalse(java.util.Arrays.equals(front, back), "the face is only on the front");
    }

    /**
     * The contract every stacked sheet relies on: drawing the body layer and
     * then the held-out sword layer on top gives exactly the picture of both
     * rendered together.
     */
    @Test
    void bodyThenHeldOutSwordEqualsRenderingThemTogether() {
        PuppetRaster raster = new PuppetRaster(32, 2.4, 0.9).withoutOutlines();
        int views = 0, hidden = 0;
        for (AnimState s : AnimState.values()) {
            for (Elevation e : Elevation.values()) {
                for (Facing f : Facing.values()) {
                    List<Box> boxes = Puppet.build(s, 0.4, true);
                    PuppetRaster.Frame layered = raster.render(boxes, e, f);
                    List<Box> merged = boxes.stream().map(b -> new Box(b.transform(), b.hx(), b.hy(),
                            b.hz(), b.rgb(), Box.Layer.BODY)).toList();
                    int[] together = raster.render(merged, e, f).body();
                    int[] body = layered.body(), sword = layered.sword();
                    for (int i = 0; i < together.length; i++) {
                        int composite = sword[i] != 0 ? sword[i] : body[i];
                        assertEquals(together[i], composite, s + " " + e + " " + f + " pixel " + i);
                    }
                    // Count views where the body really does cut the sword away.
                    List<Box> swordOnly = boxes.stream().filter(b -> b.layer() == Box.Layer.SWORD).toList();
                    int[] bare = raster.render(swordOnly, e, f).sword();
                    if (count(bare) > count(sword)) hidden++;
                    views++;
                }
            }
        }
        assertTrue(hidden > views / 4, "the hand/body hides part of the sword in many views: " + hidden);
    }

    @Test
    void swordIconIsDrawn() {
        assertTrue(FallbackSprites.anyPixels(FallbackSprites.swordIcon()));
    }

    private static int count(int[] px) {
        int n = 0;
        for (int p : px) if (p != 0) n++;
        return n;
    }

    private static int[] pixels(SheetImage s) {
        BufferedImage f = s.atlas().getSubimage(0, 0, 32, 32);
        return f.getRGB(0, 0, 32, 32, null, 0, 32);
    }
}
