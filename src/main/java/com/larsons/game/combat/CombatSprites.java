package com.larsons.game.combat;

import com.larsons.game.sprite.SheetTexture;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.SpriteProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The fighting's own sprites, under {@code assets/sprites/combat/}: one
 * folder each, with a {@code profile.json} (frame size, how big it is in
 * the world, its frame rate) and its sheets.
 *
 * <pre>
 *   d20_px64/, d20_px128/  roll.png (12 frames, loops), land_01 … land_20.png (8 frames:
 *                          the die tumbles down and comes up that number)
 *   arrow/, bolt/          fly.png: an arrow, a crossbow bolt, the head to the right
 *   magic_bolt/            fly.png: a spell in flight, 8 frames, the head to the right
 *   magic_burst/           burst.png: a spell bursting, 8 frames
 *   hit_spark/             burst.png: a blow landing, 6 frames
 * </pre>
 *
 * <p>Loaded through the sprite library like any sheet, in the background:
 * {@link #sheet} is null until it is in.
 */
public final class CombatSprites {

    /** The folder, under the sprites root. */
    public static final String FOLDER = "combat";

    private final SpriteLibrary lib;
    private final Path root;
    private final Map<String, SpriteProfile> profiles = new HashMap<>();
    private final Map<String, Path> files = new HashMap<>();

    public CombatSprites(SpriteLibrary lib) {
        this.lib = lib;
        this.root = lib.root().resolve(FOLDER);
    }

    /** {@code folder}'s framing (the defaults for one with no profile). */
    public SpriteProfile profile(String folder) {
        return profiles.computeIfAbsent(folder, f -> SpriteProfile.load(root.resolve(f), SpriteProfile.defaults()));
    }

    /** Sheet {@code name} of {@code folder}, or null while it loads (or if there is none). */
    public SheetTexture sheet(String folder, String name) {
        Path file = files.computeIfAbsent(folder + "/" + name, k -> {
            Path f = root.resolve(folder).resolve(name + ".png");
            return Files.isRegularFile(f) ? f : null;
        });
        if (file == null) return null;
        return lib.sheet(new SpriteLibrary.Resolved(file, false, profile(folder)));
    }
}
