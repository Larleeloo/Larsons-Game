package com.larsons.game.tools;

import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.SpriteNames;
import com.larsons.game.sprite.fallback.FallbackSprites;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes a complete stand-in sprite set — what a finished Blender pipeline
 * would hand over — for testing the real-sheet path and the importer before
 * the real renders exist:
 *
 * <pre>
 *   ./gradlew sampleSprites                 # → build/sample-sprites/
 *   ./gradlew sampleSprites -Psize=512      # full-size frames (slow, large)
 * </pre>
 *
 * <p>Output, ready to drag onto the game window one folder at a time:
 * <pre>
 *   body/puppet_hd/        the puppet body, 144 sheets at the chosen size
 *   hat/red_cap/           a cap, rendered with the body held out
 *   carry_right/sword/     the sword, rendered with the body held out
 *   loose-frames/          one animation as numbered frames, to see stitching
 * </pre>
 * Each folder has a {@code profile.json} giving its frame size.
 */
public final class SampleSprites {

    private SampleSprites() {}

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "build/sample-sprites");
        int size = args.length > 1 ? Integer.parseInt(args[1]) : 256;
        Path body = out.resolve("body/puppet_hd"), hat = out.resolve("hat/red_cap"),
                sword = out.resolve("carry_right/sword"), loose = out.resolve("loose-frames/puppet_hd");
        for (Path p : List.of(body, hat, sword, loose)) Files.createDirectories(p);

        int total = AnimState.values().length * Elevation.values().length * Facing.values().length, done = 0;
        for (AnimState s : AnimState.values()) {
            System.out.printf("  %-6s  (%d / %d sheets)%n", s.key(), done, total);
            for (Elevation e : Elevation.values()) {
                for (Facing f : Facing.values()) {
                    FallbackSprites.Layers l = FallbackSprites.renderSample(s, e, f, size);
                    String name = SpriteNames.fileName(s, e, f);
                    ImageIO.write(strip(l.body()), "png", body.resolve(name).toFile());
                    ImageIO.write(strip(l.hat()), "png", hat.resolve(name).toFile());
                    ImageIO.write(strip(l.sword()), "png", sword.resolve(name).toFile());
                    if (s == AnimState.WALK && e == Elevation.MIDDLE && f == Facing.EAST) {
                        for (int i = 0; i < l.body().size(); i++) {
                            ImageIO.write(l.body().get(i), "png", loose.resolve(
                                    String.format("Walk-45-East_%04d.png", i + 1)).toFile());
                        }
                    }
                    done++;
                }
            }
        }
        String profile = "{\n  \"frameWidth\": " + size + ",\n  \"frameHeight\": " + size
                + ",\n  \"frameWorldSize\": 2.4,\n  \"pivotHeight\": 0.9,\n  \"fps\": 30\n}\n";
        for (Path p : List.of(body, hat, sword, loose)) {
            Files.writeString(p.resolve("profile.json"), profile, StandardCharsets.UTF_8);
        }
        System.out.println("  sample set written to " + out.toAbsolutePath());
    }

    private static BufferedImage strip(List<BufferedImage> frames) {
        int w = frames.get(0).getWidth(), h = frames.get(0).getHeight();
        BufferedImage img = new BufferedImage(w * frames.size(), h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int i = 0; i < frames.size(); i++) g.drawImage(frames.get(i), i * w, 0, null);
        g.dispose();
        return img;
    }
}
