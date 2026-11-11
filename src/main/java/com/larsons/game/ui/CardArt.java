package com.larsons.game.ui;

import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Texture;
import com.larsons.game.util.Json;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The cards' pictures and the little icons of what they do, both drawn in
 * the art's 128 colours by {@code tools/cards/make_card_art.py}:
 *
 * <ul>
 *   <li>{@code assets/cards/art.png} - a 96 x 64 picture for every card (a
 *       scene of its class's - the Mage's observatory, the Warrior's forge -
 *       with what the card does in the middle of it), found by the card's id
 *       in {@code art.json};</li>
 *   <li>{@code assets/cards/icons.png} - a 24 x 24 icon for every kind of
 *       help and every thing a card does at once (by its key: {@code
 *       spell_damage}, {@code heal}), and a few more the HUD uses ({@code
 *       quest}, {@code key}, {@code skull} ...), found in {@code icons.json}.</li>
 * </ul>
 *
 * Read the first time one is drawn; a card or an icon with no picture is
 * drawn without (the caller is told, and draws something plain).
 */
public final class CardArt {

    private static final float[] WHITE = {1, 1, 1, 1};

    private static Path folder = Path.of("assets", "cards");
    private static boolean tried;
    private static Texture art, icons;
    private static int artW = 96, artH = 64, artColumns = 16, iconSize = 24, iconColumns = 8;
    private static final Map<String, Integer> artCells = new HashMap<>(), iconCells = new HashMap<>();

    private CardArt() {}

    /** A card picture's width to its height. */
    public static final float WIDE = 1.5f;

    private static void load() {
        if (tried) return;
        tried = true;
        try {
            Map<String, Object> a = Json.asObject(Json.parse(Files.readString(folder.resolve("art.json"),
                    StandardCharsets.UTF_8)));
            var cell = Json.asArray(a.get("cell"));
            artW = ((Number) cell.get(0)).intValue();
            artH = ((Number) cell.get(1)).intValue();
            artColumns = (int) Json.num(a, "columns", 16);
            for (var e : Json.obj(a, "cards").entrySet()) artCells.put(e.getKey(), ((Number) e.getValue()).intValue());
            art = read(folder.resolve("art.png"));
        } catch (IOException | RuntimeException e) {
            System.err.println("[cards] no card pictures: " + e.getMessage());
            art = null;
        }
        try {
            Map<String, Object> i = Json.asObject(Json.parse(Files.readString(folder.resolve("icons.json"),
                    StandardCharsets.UTF_8)));
            iconSize = (int) Json.num(i, "cell", 24);
            iconColumns = (int) Json.num(i, "columns", 8);
            for (var e : Json.obj(i, "icons").entrySet()) iconCells.put(e.getKey(), ((Number) e.getValue()).intValue());
            icons = read(folder.resolve("icons.png"));
        } catch (IOException | RuntimeException e) {
            System.err.println("[cards] no effect icons: " + e.getMessage());
            icons = null;
        }
    }

    private static Texture read(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("cannot read " + file);
        return Texture.upload(PixelData.of(image), true);
    }

    /** Whether card {@code id} has a picture. */
    public static boolean has(String id) {
        load();
        return art != null && artCells.containsKey(id);
    }

    /** Whether there is an icon called {@code key}. */
    public static boolean hasIcon(String key) {
        load();
        return icons != null && key != null && iconCells.containsKey(key);
    }

    /** Card {@code id}'s picture over {@code (x, y, w, h)}; false (and nothing drawn) when it has none. */
    public static boolean draw(Ui ui, String id, float x, float y, float w, float h, float alpha) {
        if (!has(id)) return false;
        int cell = artCells.get(id);
        float tw = art.width(), th = art.height();
        float u0 = (cell % artColumns) * artW / tw, v0 = (cell / artColumns) * artH / th;
        ui.image(art, x, y, w, h, u0, v0, u0 + artW / tw, v0 + artH / th, alpha >= 1 ? WHITE
                : new float[]{1, 1, 1, alpha});
        return true;
    }

    /** The icon {@code key} in the square {@code size} across at {@code (x, y)}; false when there is none. */
    public static boolean icon(Ui ui, String key, float x, float y, float size, float alpha) {
        if (!hasIcon(key)) return false;
        int cell = iconCells.get(key);
        float tw = icons.width(), th = icons.height();
        float u0 = (cell % iconColumns) * iconSize / tw, v0 = (cell / iconColumns) * iconSize / th;
        ui.image(icons, x, y, size, size, u0, v0, u0 + iconSize / tw, v0 + iconSize / th, alpha >= 1 ? WHITE
                : new float[]{1, 1, 1, alpha});
        return true;
    }

    /** The icons' sheet (null: there is none), for an icon drawn in the world. */
    public static Texture iconTexture() {
        load();
        return icons;
    }

    /** Where the icon {@code key} is on {@link #iconTexture}: {@code {u0, v0, u1, v1}}, or null. */
    public static float[] iconUv(String key) {
        if (!hasIcon(key)) return null;
        int cell = iconCells.get(key);
        float tw = icons.width(), th = icons.height();
        float u0 = (cell % iconColumns) * iconSize / tw, v0 = (cell / iconColumns) * iconSize / th;
        return new float[]{u0, v0, u0 + iconSize / tw, v0 + iconSize / th};
    }

    /** Let the pictures go (the game is closing, or they are to be read again). */
    public static void dispose() {
        if (art != null) art.close();
        if (icons != null) icons.close();
        art = icons = null;
        artCells.clear();
        iconCells.clear();
        tried = false;
    }
}
