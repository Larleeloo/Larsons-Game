package com.larsons.game.building;

import com.larsons.game.level.Level;
import com.larsons.game.level.LevelIO;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.LevelWorld;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.combat.CombatTuning;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.PixelPalette;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The buildings: their kinds as read from {@code assets/buildings}, a
 * building's footprint (she is pushed out of it, the animals go round it),
 * its own metres turned to the world's, the shades each part's colour is
 * drawn in, a building's look kept in its level, and the small castle set.
 */
class BuildingTest {

    private static final String PROFILE = """
            {"key": "hut", "label": "Hut", "pixel": 0.0375, "texel": 0.0375,
             "elevations": {"side": {"width": 100, "height": 120, "anchorX": 50, "anchorY": 110, "angle": 0},
                            "middle": {"width": 100, "height": 140, "anchorX": 50, "anchorY": 100, "angle": 45}},
             "depth": {"min": -5, "step": 0.04},
             "parts": [{"key": "walls", "label": "Walls", "colour": "#d8c8a0", "texture": "plaster", "glow": false},
                       {"key": "windows", "label": "Windows", "colour": "#8aa8c0", "texture": "panes", "glow": true}],
             "footprint": [["box", 0, 0, 4, 2], ["circle", 3, 0, 0.5]],
             "door": [0, -1.8], "interior": true,
             "anim": {"frames": 8, "degrees": 90, "speed": 40}}
            """;

    @Test
    void aProfileIsReadWithItsPartsViewsFootprintAndDoor() {
        BuildingProfile p = BuildingProfile.parse(PROFILE);
        assertEquals("hut", p.key());
        assertEquals(2, p.parts().size());
        assertTrue(p.part("windows").glow());
        assertEquals(0xD8C8A0, p.part("walls").colour());
        assertEquals(100, p.frame(Elevation.SIDE).width());
        assertNull(p.frame(Elevation.TOP));
        assertEquals(2, p.footprint().size());
        assertTrue(p.footprint().get(1).circle());
        assertArrayEquals(new double[]{0, -1.8}, p.door(), 1e-9);
        assertEquals(8, p.anim().frames());
        assertEquals(110 * 0.0375, p.height(), 1e-9);
        // the circle 3 m out with its half metre reaches furthest
        assertEquals(3.5, p.reach(), 1e-9);
    }

    @Test
    void itsOwnMetresTurnWithItsHeadingAndBack() {
        BuildingProfile p = BuildingProfile.parse(PROFILE);
        for (double h : new double[]{0, 0.7, Math.PI / 2, Math.PI, 4}) {
            Building b = new Building(1, p, new Vec3(5, 0, -3), h, null, null);
            // its front (model -y) is the way it faces
            Vec3 front = b.toWorld(0, -1).sub(b.position());
            Vec3 face = SpriteView.headingVector(h);
            assertEquals(face.x(), front.x(), 1e-9);
            assertEquals(face.z(), front.z(), 1e-9);
            double[] m = b.toModel(b.toWorld(1.5, -0.75).x(), b.toWorld(1.5, -0.75).z());
            assertEquals(1.5, m[0], 1e-9);
            assertEquals(-0.75, m[1], 1e-9);
        }
    }

    @Test
    void sheIsPushedOutOfItsWallsTheNearestWay() {
        BuildingProfile p = BuildingProfile.parse(PROFILE);
        Building b = new Building(1, p, Vec3.ZERO, 0, null, null);
        assertTrue(b.blocks(0, 0, 0.2));
        assertFalse(b.blocks(0, 3, 0.2));
        // just inside its front wall: out through the front
        Vec3 at = b.toWorld(0.5, -0.9);
        Vec3 out = b.pushOut(at.x(), at.z(), 0.25);
        assertNotNull(out);
        double[] m = b.toModel(out.x(), out.z());
        assertEquals(-1.25, m[1], 1e-6);
        assertFalse(b.blocks(out.x(), out.z(), 0.24));
        // into the round piece: out round it
        Vec3 c = b.toWorld(3.3, 0.1);
        Vec3 o2 = b.pushOut(c.x(), c.z(), 0.2);
        assertNotNull(o2);
        double[] m2 = b.toModel(o2.x(), o2.z());
        assertTrue(Math.hypot(m2[0] - 3, m2[1]) >= 0.7 - 1e-6 || Math.abs(m2[1]) >= 1.2 - 1e-6);
        // clear of it: left as she is
        assertNull(b.pushOut(0, 6, 0.25));
    }

    @Test
    void aClickFindsItsWallsAndMissesBesideThem() {
        Building b = new Building(1, BuildingProfile.parse(PROFILE), Vec3.ZERO, 0, null, null);
        // looking down on it from above, and past its side
        assertTrue(b.rayHit(new Vec3(0, 20, 0), new Vec3(0, -1, 0)) > 0);
        assertEquals(-1, b.rayHit(new Vec3(0, 20, 6), new Vec3(0, -1, 0)));
        // level with it, from the north, through its middle
        double t = b.rayHit(new Vec3(0, 1, -10), new Vec3(0, 0, 1));
        assertEquals(9, t, 1e-6);
    }

    @Test
    void eachColourIsDrawnInSixShadesOfThePaletteDarkToLight() {
        int[] fills = new int[100], lines = new int[28];
        for (int i = 0; i < fills.length; i++) {
            int g = 30 + i * 2;
            fills[i] = (g << 16) | ((g * 9 / 10) << 8) | (g * 7 / 10);
        }
        for (int i = 0; i < lines.length; i++) lines[i] = ((10 + i) << 16) | ((8 + i) << 8) | (6 + i);
        PixelPalette palette = new PixelPalette(fills, lines);
        for (int rgb : new int[]{0xD8C8A0, 0x7A4A2A, 0x8A867E, 0xC8A048}) {
            int[] r = BuildingLook.ramp(rgb, palette);
            assertEquals(6, r.length);
            double prev = -1;
            for (int i = 0; i < 6; i++) {
                double l = com.larsons.game.sprite.Variants.Oklab.of(r[i])[0];
                assertTrue(i == 0 || l >= prev, "shades go dark to light");
                prev = l;
            }
            // the line is one of the line colours, the rest the fills
            assertTrue(java.util.Arrays.stream(lines).anyMatch(c -> c == r[0]));
            for (int i = 1; i < 6; i++) {
                int c = r[i];
                assertTrue(java.util.Arrays.stream(fills).anyMatch(f -> f == c));
            }
        }
    }

    @Test
    void aLookKeepsOnlyWhatDiffersFromHowItComes() {
        BuildingProfile p = BuildingProfile.parse(PROFILE);
        BuildingLook look = new BuildingLook().setColour("walls", 0xA84A30).setTexture("walls", "brick")
                .setColour("windows", 0x8AA8C0);           // (as it comes)
        Map<String, Object> props = look.toProps(p);
        assertEquals(1, props.size());
        BuildingLook back = BuildingLook.of(props);
        assertEquals(0xA84A30, back.colour(p.part("walls")));
        assertEquals("brick", back.texture(p.part("walls")));
        assertEquals(0x8AA8C0, back.colour(p.part("windows")));
        assertEquals("panes", back.texture(p.part("windows")));
    }

    @Test
    void oneBuildingsLookGoesOntoAnotherPartByPart() {
        BuildingCatalog c = BuildingCatalog.load(Path.of("assets", "buildings"));
        BuildingProfile cottage = c.profile("cottage"), tower = c.profile("round_tower");
        assertNotNull(cottage);
        assertNotNull(tower);
        BuildingLook mine = new BuildingLook().setColour("walls", 0x7A3B2E).setColour("roof", 0x2E4A7A)
                .setTexture("walls", "brick");
        BuildingLook onto = BuildingLook.transfer(cottage, mine, tower, new BuildingLook());
        // the same parts take its colours and patterns, the parts the cottage has none of keep their own
        assertEquals(0x7A3B2E, onto.colour(tower.part("walls")));
        assertEquals("brick", onto.texture(tower.part("walls")));
        for (BuildingProfile.Part t : tower.parts()) {
            BuildingProfile.Part from = BuildingLook.match(cottage, t);
            if (from == null) assertEquals(t.colour(), onto.colour(t), t.key() + " kept");
            else assertEquals(mine.colour(from), onto.colour(t), t.key() + " from " + from.key());
        }
        assertTrue(BuildingLook.matches(cottage, tower) >= 3);
        // parts named differently but doing the same: side walls take the walls', an awning the roof's
        assertEquals("walls", BuildingLook.role("side_walls"));
        assertEquals("roof", BuildingLook.role("awning2"));
    }

    /**
     * What she walks over or what hangs on a wall, or is small enough to sit
     * on a table: nothing in her way.
     */
    private static final java.util.Set<String> FLAT_OR_HUNG = java.util.Set.of("rug", "painting", "banner",
            "wall_shelf", "pot_rack", "dish_rack", "mirror", "deer_head", "boar_head", "bear_head", "crossed_swords",
            "hanging_planter", "window_box", "wheat_field", "cabbage_patch", "pumpkin_patch", "broom", "farm_tools",
            "book_stack", "tableware", "bottles", "berry_bowl");

    @Test
    void theGamesBuildingsAreAllThereWithTheirPatterns() {
        BuildingCatalog c = BuildingCatalog.load(Path.of("assets", "buildings"));
        assertEquals(BuildingCatalog.ORDER, c.keys());
        assertTrue(c.textures().size() >= 20);
        assertEquals(0, c.textureIndex("no such pattern"));
        for (String k : c.keys()) {
            BuildingProfile p = c.profile(k);
            assertFalse(p.parts().isEmpty(), k);
            assertTrue(p.parts().size() <= BuildingRenderer.MAX_PARTS, k);
            // the lights' fittings: 3D models only, out of her way but those standing on the floor
            if (p.fixture()) {
                assertNotNull(p.flame(), k + "'s flame");
                assertEquals(p.stands(), !p.footprint().isEmpty(), k);
                assertTrue(p.height() > p.flame()[2], k);
            } else {
                // solid, but for what lies flat underfoot or hangs on a wall
                assertEquals(!FLAT_OR_HUNG.contains(k), !p.footprint().isEmpty(), k);
                // its views, unless it is only ever a 3D model
                if (!p.modelOnly()) for (Elevation e : Elevation.values()) assertNotNull(p.frame(e), k + " " + e);
            }
            for (BuildingProfile.Part part : p.parts()) {
                assertNotNull(c.texture(part.texture()), k + " " + part.key() + ": " + part.texture());
            }
            // she can stand at its door, outside its walls
            if (p.door() != null && !k.equals("gatehouse")) {
                Building b = new Building(1, p, Vec3.ZERO, 0, null, null);
                Vec3 d = b.door();
                assertFalse(b.blocks(d.x(), d.z(), 0.2), k + "'s door is in its wall");
            }
        }
        assertNotNull(c.profile("windmill").anim());
        assertNotNull(c.profile("waterwheel").anim(), "the waterwheel turns");
    }

    @Test
    void everyBuildingIsA3DModel() throws IOException {
        BuildingCatalog c = BuildingCatalog.load(Path.of("assets", "buildings"));
        for (String k : c.keys()) {
            BuildingModel m = BuildingModel.load(c.root().resolve(k));
            // the barrels, crates and pots too (she opens them, fills them and breaks them as before)
            assertNotNull(m, k);
            assertTrue(m.fixedCorners() > 0 && m.fixedCorners() % 3 == 0, k);
            assertEquals(m.fixedCorners() + m.turningCorners(), m.vertices().length / BuildingModel.FLOATS, k);
            BuildingProfile p = c.profile(k);
            assertEquals(p.anim() != null, m.turningCorners() > 0, k + ": its turning part");
            assertTrue(Math.abs(m.top() - p.height()) < Math.max(0.6, p.height() * 0.25), k + " " + m.top() + " "
                    + p.height());
            // every corner's part is one of its parts; its normal a unit
            float[] v = m.vertices();
            for (int i = 0; i < v.length; i += BuildingModel.FLOATS) {
                assertTrue(v[i + 14] >= 0 && v[i + 14] < p.parts().size(), k);
                assertEquals(1, Math.sqrt(v[i + 3] * v[i + 3] + v[i + 4] * v[i + 4] + v[i + 5] * v[i + 5]), 1e-3, k);
            }
        }
        // a box's edges are lines; where a round tower's faces meet, nearly flat, they are not
        BuildingModel crate = BuildingModel.parse(boxModel());
        float[] v = crate.vertices();
        int lines = 0;
        for (int i = 0; i < v.length; i += 3 * BuildingModel.FLOATS) lines += Integer.bitCount((int) v[i + 15]);
        assertEquals(12 * 2, lines, "each of a box's 12 edges, on both faces that meet there");
    }

    /** A 1 m box as a model file: six faces of one part. */
    private static byte[] boxModel() {
        double[][][] faces = {
                {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}}, {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
                {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}}, {{1, 1, 0}, {0, 1, 0}, {0, 1, 1}, {1, 1, 1}},
                {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}}, {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}}};
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(4 + 4 + 6 + 6 * (1 + 4 * 20) + 1)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("LGM1".getBytes()).putInt(1).put((byte) 0).put((byte) 0).putInt(6);
        for (double[][] f : faces) {
            b.put((byte) 4);
            for (double[] p : f) b.putFloat((float) p[0]).putFloat((float) p[1]).putFloat((float) p[2]).putFloat(0)
                    .putFloat(0);
        }
        b.put((byte) 0);
        return b.array();
    }

    @Test
    void aBuildingAndItsLookArePlayedSavedAndReadBack(@TempDir Path dir) throws IOException {
        BuildingCatalog.use(BuildingCatalog.load(Path.of("assets", "buildings")));
        Level level = Level.blank("Village", 64);
        LevelObject o = level.add(ObjectTypes.BUILDING, 6, -4, Math.PI, Map.of("kind", "cottage"));
        o.set("look", Map.of("roof", Map.of("colour", "#a84a30", "texture", "slate")));
        LevelIO.save(level, dir.resolve("village"));
        Level back = LevelIO.load(dir.resolve("village"));
        LevelObject b = back.byId(o.id());
        assertEquals("cottage", b.str("kind", ""));
        World world = new World(new Wardrobe());
        LevelWorld.populate(world, back, ScenerySprites.NONE, CombatTuning.defaults());
        assertEquals(1, world.buildings().size());
        Building built = world.building(o.id());
        BuildingProfile.Part roof = built.profile().part("roof");
        assertEquals(0xA84A30, built.look().colour(roof));
        assertEquals("slate", built.look().texture(roof));
        // she cannot walk into it, and the animals go round it
        assertTrue(world.solidNear(6, -4, 0.3));
        world.player().setPosition(new Vec3(6, 0, -4));
        world.tick(1 / 60.0, null);
        assertFalse(built.blocks(world.player().ground().x(), world.player().ground().z(), 0.2));
    }

    @Test
    void theSmallCastleIsPlacedAsItsBuildingsTurnedWithIt() {
        BuildingCatalog.use(BuildingCatalog.load(Path.of("assets", "buildings")));
        LevelObject set = new LevelObject(0, ObjectTypes.PREFAB, 10, 20, Math.PI / 2, Map.of("set", "small_castle"));
        List<LevelObject> pieces = ObjectTypes.expand(set, 50);
        assertEquals(Prefab.byKey("small_castle").pieces().size(), pieces.size());
        assertEquals(50, pieces.get(0).id());
        assertTrue(pieces.stream().allMatch(p -> p.type().equals(ObjectTypes.BUILDING)));
        assertEquals(1, pieces.stream().filter(p -> p.str("kind", "").equals("gatehouse")).count());
        assertEquals(4, pieces.stream().filter(p -> p.str("kind", "").equals("round_tower")).count());
        // the keep faces the way the set does, and its gatehouse is out in front of it
        LevelObject keep = pieces.get(0), gate = pieces.stream()
                .filter(p -> p.str("kind", "").equals("gatehouse")).findFirst().orElseThrow();
        assertEquals(Math.PI / 2, keep.yaw(), 1e-9);
        Vec3 face = SpriteView.headingVector(Math.PI / 2);
        double ahead = (gate.x() - keep.x()) * face.x() + (gate.z() - keep.z()) * face.z();
        assertTrue(ahead > 10, "the gate is in front of the keep: " + ahead);
        // anything else is placed as it is
        LevelObject tree = new LevelObject(3, ObjectTypes.SCENERY, 0, 0, 0, Map.of("sprite", "oak_tree"));
        assertEquals(List.of(tree), ObjectTypes.expand(tree, 9));
    }
}
