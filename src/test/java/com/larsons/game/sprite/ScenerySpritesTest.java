package com.larsons.game.sprite;

import com.larsons.game.sprite.SceneryProfile.Kind;
import com.larsons.game.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The scenery's sprites: their profiles, the catalogue of {@code
 * assets/scenery/}, where each frame is in its sheet - and the real art in the
 * repository keeping to that format.
 */
class ScenerySpritesTest {

    private static final Path ASSETS = Path.of("assets", "scenery");

    @Test
    void aProfileReadsEveryFieldItGives() {
        SceneryProfile p = SceneryProfile.parse("""
                {"kind": "tree", "frameWidth": 128, "frameHeight": 64, "frames": 48, "fps": 10,
                 "frameWorldSize": 4.8, "radius": 0.3, "shadowRadius": 1.6, "facing": "camera"}""", Kind.ROCK);
        assertEquals(Kind.TREE, p.kind());
        assertEquals(128, p.frameWidth());
        assertEquals(64, p.frameHeight());
        assertEquals(48, p.frames());
        assertEquals(10, p.fps(), 1e-9);
        assertEquals(4.8, p.frameWorldSize(), 1e-9);
        assertEquals(2.4, p.frameWorldHeight(), 1e-9, "in the frame's own proportions");
        assertEquals(0.3, p.radius(), 1e-9);
        assertEquals(1.6, p.shadowRadius(), 1e-9);
        assertTrue(p.facesCamera());
        assertTrue(p.solid());
    }

    @Test
    void whatAProfileLeavesOutKeepsItsKindsDefaults() {
        SceneryProfile tuft = SceneryProfile.parse("{\"kind\": \"tuft\", \"frames\": 24}", Kind.TREE);
        assertEquals(Kind.TUFT, tuft.kind());
        assertEquals(32, tuft.frameWidth());
        assertEquals(0.6, tuft.frameWorldSize(), 1e-9);
        assertEquals(24, tuft.frames());
        assertFalse(tuft.solid(), "grass is walked through");
        assertFalse(tuft.facesCamera(), "grass is a cross");
        SceneryProfile rock = SceneryProfile.parse("{\"kind\": \"rock\"}", Kind.TREE);
        assertTrue(rock.facesCamera(), "a rock faces the camera unless its profile says cross");
        assertFalse(SceneryProfile.parse("{\"kind\": \"rock\", \"facing\": \"cross\"}", Kind.TREE).facesCamera());
        assertEquals(Kind.ROCK, SceneryProfile.parse("{\"kind\": \"cloud\"}", Kind.ROCK).kind(),
                "a kind the game does not know: the fallback");
        SceneryProfile odd = SceneryProfile.parse("{\"fps\": -3, \"frameWorldSize\": 0, \"radius\": -1}", Kind.TREE);
        assertEquals(12, odd.fps(), 1e-9);
        assertEquals(4.8, odd.frameWorldSize(), 1e-9);
        assertEquals(0, odd.radius(), 1e-9);
    }

    @Test
    void aFolderWithoutAProfileIsTheKindItsNameSuggests(@TempDir Path dir) throws IOException {
        assertEquals(Kind.TUFT, Kind.guess("grass_tuft_b"));
        assertEquals(Kind.ROCK, Kind.guess("mossy_rock"));
        assertEquals(Kind.ROCK, Kind.guess("stones"));
        assertEquals(Kind.TREE, Kind.guess("oak_tree"));
        Path boulder = Files.createDirectories(dir.resolve("big_boulder"));
        assertEquals(SceneryProfile.defaults(Kind.ROCK), SceneryProfile.load(boulder));
        Files.writeString(boulder.resolve(SceneryProfile.FILE_NAME), "{ not json");
        assertEquals(SceneryProfile.defaults(Kind.ROCK), SceneryProfile.load(boulder), "an unreadable profile too");
    }

    @Test
    void theGridIsTheOneTheArtScriptsWrite() {
        // scenery_common.grid_columns in 3D-Modeling: the fewest empty cells, then the most even shape
        assertEquals(8, ScenerySprites.gridColumns(48));
        assertEquals(6, ScenerySprites.gridColumns(24));
        assertEquals(1, ScenerySprites.gridColumns(1));
        assertEquals(2, ScenerySprites.gridColumns(2));
    }

    @Test
    void eachFrameHasItsOwnCellOfTheSheet() {
        SceneryProfile p = SceneryProfile.defaults(Kind.TREE).withFrames(48);
        ScenerySprites.Sprite s = ScenerySprites.describe("oak", p, Path.of("oak.png"), 1024, 768);
        assertEquals(8, s.columns());
        assertEquals(6, s.rows());
        float[] uv = s.uv(9);                                   // the second row, the second column
        assertEquals(128 / 1024.0, uv[0], 1e-3);
        assertEquals(128 / 768.0, uv[1], 1e-3);
        assertEquals(256 / 1024.0, uv[2], 1e-3);
        assertEquals(256 / 768.0, uv[3], 1e-3);
        assertTrue(uv[0] > 128 / 1024.0 && uv[2] < 256 / 1024.0, "pulled in a sliver, so no neighbour bleeds in");
        assertArrayEquals(s.uv(1), s.uv(49), "any frame number wraps into the loop");
        assertArrayEquals(s.uv(47), s.uv(-1));
        assertEquals(0, s.frameAt(0));
        assertEquals(12, s.frameAt(1.0), "12 fps");
        assertEquals(0, s.frameAt(4.0), "48 frames at 12 fps: a 4 s loop");
        assertEquals(47, s.frameAt(-0.04), "a clock behind zero is still a frame of the loop");
    }

    @Test
    void aSheetThatIsNotAGridOfItsFramesIsRefused() {
        SceneryProfile p = SceneryProfile.defaults(Kind.TUFT);
        assertThrows(IllegalArgumentException.class, () -> ScenerySprites.describe("t", p, Path.of("t"), 100, 64));
        assertThrows(IllegalArgumentException.class,
                () -> ScenerySprites.describe("t", p.withFrames(24), Path.of("t"), 128, 64), "8 cells, 24 frames");
        assertEquals(8, ScenerySprites.describe("t", p, Path.of("t"), 128, 64).frames(),
                "no count given: every cell is a frame");
    }

    @Test
    void theCatalogueFindsTheSpritesAndTheGround(@TempDir Path scenery) throws IOException {
        sprite(scenery.resolve("oak_tree"), 4, 128, "{\"kind\": \"tree\", \"frames\": 4, \"fps\": 12}");
        sprite(scenery.resolve("grass_tuft_a"), 24, 32, "{\"kind\": \"tuft\", \"frames\": 24}");
        sprite(scenery.resolve("boulder"), 1, 128, null);
        Files.createDirectories(scenery.resolve("empty"));                     // no sheet: skipped
        Path bad = Files.createDirectories(scenery.resolve("bad_rock"));
        ImageIO.write(new BufferedImage(100, 70, BufferedImage.TYPE_INT_ARGB), "png", bad.resolve("sheet.png").toFile());
        Path ground = Files.createDirectories(scenery.resolve("ground"));
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", ground.resolve("grass.png").toFile());
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", ground.resolve("dirt.png").toFile());
        Files.writeString(ground.resolve("profile.json"), "{\"tileWorldSize\": 9.6}");

        ScenerySprites s = ScenerySprites.scan(scenery);
        assertEquals(List.of("boulder", "grass_tuft_a", "oak_tree"), s.sprites().stream().map(ScenerySprites.Sprite::name).toList());
        assertEquals(List.of("oak_tree"), s.ofKind(Kind.TREE).stream().map(ScenerySprites.Sprite::name).toList());
        assertEquals(1, s.sprite("boulder").frames());
        assertEquals(Kind.ROCK, s.sprite("boulder").kind(), "no profile: the kind its name suggests");
        assertEquals(6, s.sprite("grass_tuft_a").columns());
        assertEquals(2, s.sprite("oak_tree").columns());
        assertNull(s.sprite("bad_rock"));
        assertNotNull(s.ground());
        assertEquals(9.6, s.ground().tileWorldSize(), 1e-9);
        assertEquals(ground.resolve("grass.png"), s.ground().texture());
        assertEquals(ground.resolve("dirt.png"), s.ground().tile("dirt"), "the other ground textures, by name");
        assertNull(s.ground().tile("gravel"));
        assertEquals(1, s.ground().tiles().size(), "and not the grass again");
    }

    @Test
    void noSceneryFolderIsNoScenery(@TempDir Path dir) {
        ScenerySprites s = ScenerySprites.scan(dir.resolve("nowhere"));
        assertTrue(s.isEmpty());
        assertNull(s.ground());
        assertTrue(ScenerySprites.scan(dir).sprites().isEmpty());
    }

    /** A sheet of {@code frames} square frames of {@code size} pixels in the usual grid, and its profile. */
    private static void sprite(Path dir, int frames, int size, String profile) throws IOException {
        Files.createDirectories(dir);
        int cols = ScenerySprites.gridColumns(frames), rows = (frames + cols - 1) / cols;
        ImageIO.write(new BufferedImage(cols * size, rows * size, BufferedImage.TYPE_INT_ARGB), "png",
                dir.resolve("sheet.png").toFile());
        if (profile != null) Files.writeString(dir.resolve("profile.json"), profile);
    }

    // ------------------------------------------------------------------ the real art

    @Test
    void everySheetInTheRepositoryHoldsItsProfilesFramesInThePixelArtsTwoAlphas() throws IOException {
        assumeTrue(Files.isDirectory(ASSETS), "no assets/scenery here");
        ScenerySprites s = ScenerySprites.scan(ASSETS);
        List<Path> folders;
        try (Stream<Path> list = Files.list(ASSETS)) {
            folders = list.filter(Files::isDirectory).filter(d -> !d.getFileName().toString().equals("ground")).toList();
        }
        assertFalse(folders.isEmpty());
        for (Path dir : folders) {
            String name = dir.getFileName().toString();
            ScenerySprites.Sprite sprite = s.sprite(name);
            assertNotNull(sprite, name + " is read");
            Map<String, Object> j = Json.asObject(Json.parse(Files.readString(dir.resolve("profile.json"))));
            assertTrue(j.containsKey("kind") && j.containsKey("frames") && j.containsKey("frameWorldSize"),
                    name + ": its profile says what it is");
            BufferedImage sheet = ImageIO.read(dir.resolve("sheet.png").toFile());
            SceneryProfile p = sprite.profile();
            int cols = ScenerySprites.gridColumns(p.frames()), rows = (p.frames() + cols - 1) / cols;
            assertEquals(cols * p.frameWidth(), sheet.getWidth(), name + ": the usual grid across");
            assertEquals(rows * p.frameHeight(), sheet.getHeight(), name + ": and down");
            for (int y = 0; y < sheet.getHeight(); y++) {
                for (int x = 0; x < sheet.getWidth(); x++) {
                    int a = sheet.getRGB(x, y) >>> 24;
                    if (a != 0 && a != 255) fail(name + ": a half-transparent pixel at " + x + "," + y);
                }
            }
            if (p.frames() > 1) assertTrue(p.fps() > 0, name + ": a loop has a speed");
        }
    }

    @Test
    void theGroundIsAnOpaqueTileOf512() throws IOException {
        Path grass = ASSETS.resolve("ground").resolve("grass.png");
        assumeTrue(Files.isRegularFile(grass), "no ground here");
        List<Path> tiles = new java.util.ArrayList<>(List.of(grass));
        for (String n : List.of("dirt", "forest_floor", "gravel", "meadow")) {
            Path t = ScenerySprites.scan(ASSETS).ground().tile(n);
            assertNotNull(t, "the " + n + " beside the grass");
            tiles.add(t);
        }
        for (Path tile : tiles) {
            BufferedImage image = ImageIO.read(tile.toFile());
            assertEquals(512, image.getWidth(), tile + "");
            assertEquals(512, image.getHeight(), tile + "");
            for (int y = 0; y < 512; y++) {
                for (int x = 0; x < 512; x++) {
                    if ((image.getRGB(x, y) >>> 24) != 255) fail(tile + ": a see-through pixel at " + x + "," + y);
                }
            }
        }
        assertEquals(9.6, ScenerySprites.scan(ASSETS).ground().tileWorldSize(), 1e-9,
                "512 pixels at the characters' 1.875 cm");
    }
}
