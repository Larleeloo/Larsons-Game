package com.larsons.game.world;

import com.larsons.game.level.LevelObject;
import com.larsons.game.level.LevelWorld;
import com.larsons.game.level.ObjectType;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Felling trees with an axe: what it takes, how they fall, what they leave, the stump and the growing back. */
class FellingTest {

    private static final SceneryProfile.Fell OAK = new SceneryProfile.Fell(null, 5, "timber x3", 600);

    private static ScenerySprites.Sprite sprite(String name, SceneryProfile.Kind kind, SceneryProfile.Fell fell) {
        SceneryProfile p = new SceneryProfile(kind, 128, 128, 48, 12, 4.8, kind == SceneryProfile.Kind.TUFT ? 0 : 0.3,
                1.6, kind == SceneryProfile.Kind.ROCK, List.of(), -1, false, null, null, fell);
        return ScenerySprites.describe(name, p, Path.of(name + ".png"), 128 * 48, 128);
    }

    private static Scenery.Placed tree(double x, double z, double yaw, Felling.Own own) {
        return new Scenery.Placed(sprite("oak_tree", SceneryProfile.Kind.TREE, OAK), x, 0, z, yaw, false, 0, 1, 0,
                own);
    }

    private static Scenery.Placed tree(double x, double z) {
        return tree(x, z, 0, null);
    }

    @Test
    void theProfileSaysWhatFellingATreeTakesAndLeaves() {
        SceneryProfile p = SceneryProfile.parse("{\"kind\": \"tree\", \"fell\": {\"stump\": \"pine_stump\", "
                + "\"chops\": 7, \"drops\": \"timber x2-4, resin 30%\", \"regrow\": 300}}", SceneryProfile.Kind.TREE);
        assertEquals(new SceneryProfile.Fell("pine_stump", 7, "timber x2-4, resin 30%", 300), p.fell());
        SceneryProfile none = SceneryProfile.parse("{\"kind\": \"tree\"}", SceneryProfile.Kind.TREE);
        assertNull(none.fell(), "its kind's default then (Felling.DEFAULT)");
        SceneryProfile some = SceneryProfile.parse("{\"kind\": \"tree\", \"fell\": {\"chops\": 9}}",
                SceneryProfile.Kind.TREE);
        assertEquals(9, some.fell().chops());
        assertEquals("timber x2-4", some.fell().drops());
        assertEquals(600, some.fell().regrow(), 1e-9);
    }

    @Test
    void everyTreeKindInTheGameCanBeFelledForTimber() throws Exception {
        ScenerySprites all = ScenerySprites.scan(Path.of("assets/scenery"));
        int trees = 0;
        for (ScenerySprites.Sprite s : all.sprites()) {
            if (s.kind() != SceneryProfile.Kind.TREE) continue;
            trees++;
            assertNotNull(s.profile().fell(), s.name() + " says what felling it takes");
            Felling.Rule r = Felling.rule(new Scenery.Placed(s, 0, 0, 0, false, 0));
            assertNotNull(r, s.name());
            assertTrue(r.chops() >= 1);
            assertFalse(r.drops().lines().isEmpty(), s.name() + " leaves something");
            for (Drops.Line l : r.drops().lines()) assertNotNull(Drops.item(l.item()), s.name() + ": " + l.item());
            if (!s.name().equals("crystal_tree")) {
                assertTrue(r.drops().lines().stream().anyMatch(l -> l.item().equals("timber")), s.name() + ": timber");
            }
        }
        assertTrue(trees >= 14, "the game's trees");
    }

    @Test
    void aTreesOwnSettingsGoOverItsKinds() {
        Felling.Rule kind = Felling.rule(tree(0, 0));
        assertEquals(5, kind.chops());
        assertEquals(600, kind.regrow(), 1e-9);
        assertEquals("timber", kind.drops().lines().get(0).item());
        Felling.Rule own = Felling.rule(tree(0, 0, 0, new Felling.Own(true, 2, "coins x5, any_dye", 0)));
        assertEquals(2, own.chops());
        assertEquals(0, own.regrow(), 1e-9, "never grows back");
        assertEquals(List.of("coins", Drops.ANY_DYE), own.drops().lines().stream().map(Drops.Line::item).toList());
        assertEquals(30 * 60, Felling.rule(tree(0, 0, 0, new Felling.Own(true, 0, "", 30))).regrow(), 1e-9);
        assertNull(Felling.rule(tree(0, 0, 0, new Felling.Own(false, 0, "", -1))), "set not to be felled");
        assertNull(Felling.rule(new Scenery.Placed(sprite("mossy_rock", SceneryProfile.Kind.ROCK, null), 0, 0, 0,
                false, 0)), "a rock is no tree");
        Felling.Rule plain = Felling.rule(new Scenery.Placed(sprite("odd_tree", SceneryProfile.Kind.TREE, null),
                0, 0, 0, false, 0));
        assertEquals(Felling.DEFAULT.chops(), plain.chops(), "no fell block: the default");
    }

    @Test
    void aBetterAxeTakesFewerBlows() {
        assertTrue(Felling.axe(Item.of(ItemDef.BATTLE_AXE)));
        assertFalse(Felling.axe(Item.of(ItemDef.SWORD)));
        assertFalse(Felling.axe(null));
        assertEquals(1, Felling.power(Item.of(ItemDef.BATTLE_AXE, Rarity.COMMON)), 1e-9);
        assertEquals(2.5, Felling.power(Item.of(ItemDef.BATTLE_AXE, Rarity.MYTHIC)), 1e-9);
        assertEquals(5, Felling.blows(5, 1));
        assertEquals(4, Felling.blows(5, 1.25));
        assertEquals(3, Felling.blows(5, 2));
        assertEquals(2, Felling.blows(5, 2.5));
        double prev = 0;
        for (Rarity r : Rarity.values()) {
            double p = Felling.power(Item.of(ItemDef.BATTLE_AXE, r));
            assertTrue(p > prev, r + " more than the one below");
            prev = p;
        }
    }

    @Test
    void itFallsAlongOneOfItsCardsAwayFromHer() {
        Scenery.Placed t = tree(0, 0, 0.3, null);
        Vec3 her = new Vec3(-2, 0, -0.4);
        double h = Felling.fallHeading(t, her);
        Vec3 d = SpriteView.headingVector(h);
        // along a card: (cos yaw, sin yaw) or (-sin yaw, cos yaw), either way
        double c = Math.cos(0.3), s = Math.sin(0.3);
        double along = Math.max(Math.abs(d.x() * c + d.z() * s), Math.abs(-d.x() * s + d.z() * c));
        assertEquals(1, along, 1e-6, "along a card");
        assertTrue(d.x() * (t.x() - her.x()) + d.z() * (t.z() - her.z()) > 0, "away from her");
    }

    @Test
    void itComesDownSlowlyThenFasterBouncesAndLies() {
        double seconds = 1.7, land = Math.PI / 2;
        assertEquals(0, Felling.fallAngle(0, seconds, land), 1e-9);
        double prev = 0, prevStep = 0;
        for (int i = 1; i <= 17; i++) {
            double a = Felling.fallAngle(i * 0.1, seconds, land);
            assertTrue(a > prev, "always further over");
            assertTrue(a - prev >= prevStep - 1e-9, "faster and faster");
            prevStep = a - prev;
            prev = a;
        }
        assertEquals(land, Felling.fallAngle(seconds, seconds, land), 1e-6, "on the ground");
        assertTrue(Felling.fallAngle(seconds + Felling.BOUNCE_SECONDS / 2, seconds, land) < land, "the bounce");
        assertEquals(land, Felling.fallAngle(seconds + 5, seconds, land), 1e-9, "lying");
        assertTrue(Felling.fallSeconds(tree(0, 0)) > 0);
    }

    @Test
    void sheStandsWhereHerAxeMeetsTheBark() {
        Scenery.Placed t = tree(3, 4);
        double[] st = Felling.stand(t, new Vec3(0, 0, 0));
        Vec3 f = SpriteView.headingVector(st[2]);
        Vec3 left = new Vec3(f.z(), 0, -f.x());
        double bx = st[0] + f.x() * Felling.BITE_AHEAD + left.x() * Felling.BITE_LEFT;
        double bz = st[1] + f.z() * Felling.BITE_AHEAD + left.z() * Felling.BITE_LEFT;
        double inTrunk = Math.hypot(bx - t.x(), bz - t.z());
        assertEquals(t.radius() * (1 - Felling.BITE_DEPTH), inTrunk, 1e-6, "the bit in the bark");
        double from = Math.hypot(st[0] - t.x(), st[1] - t.z());
        assertTrue(from > t.radius() + 0.4, "clear of the trunk");
    }

    @Test
    void blowsBringItDownItBreaksUpLeavesTimberAndGrowsBackOutOfSight() {
        Fellings f = new Fellings();
        Scenery.Placed t = tree(0, 0);
        Vec3 her = new Vec3(-1.5, 0, 0);
        for (int i = 0; i < 4; i++) {
            Fellings.Blow b = f.chop(t, 1, her, i, null);
            assertFalse(b.felled());
            assertEquals(i + 1, b.struck(), 1e-9);
            assertTrue(f.standing(t));
        }
        assertEquals(List.of(Fellings.Event.Kind.CHOPPED, Fellings.Event.Kind.CHOPPED, Fellings.Event.Kind.CHOPPED,
                Fellings.Event.Kind.CHOPPED), f.drainEvents().stream().map(Fellings.Event::kind).toList());
        Fellings.Blow last = f.chop(t, 1, her, 4, null);
        assertTrue(last.felled());
        assertFalse(f.standing(t));
        assertEquals(Fellings.Stage.FALLING, f.of(t).stage());
        assertNull(f.chop(t, 1, her, 4.2, null), "down already");
        assertEquals(List.of(Fellings.Event.Kind.CHOPPED, Fellings.Event.Kind.FELLED),
                f.drainEvents().stream().map(Fellings.Event::kind).toList());
        Vec3 d = SpriteView.headingVector(f.of(t).heading());
        assertTrue(d.x() > 0.99, "away from her");

        Random random = new Random(3);
        Loot loot = new Loot();
        double fall = Felling.fallSeconds(t);
        f.tick(4 + fall + 0.01, null, random, loot);
        assertEquals(Fellings.Event.Kind.CRASHED, f.drainEvents().get(0).kind());
        f.tick(4 + fall + Felling.BOUNCE_SECONDS + 0.01, null, random, loot);
        assertEquals(Fellings.Stage.DOWN, f.of(t).stage());
        f.tick(4 + fall + Felling.BOUNCE_SECONDS + Felling.LIE_SECONDS + 0.01, null, random, loot);
        assertEquals(Fellings.Stage.STUMP, f.of(t).stage());
        List<Fellings.Event> broke = f.drainEvents();
        assertEquals(Fellings.Event.Kind.BROKE, broke.get(0).kind());
        assertEquals(1, broke.get(0).drops().size());
        assertEquals("timber", broke.get(0).drops().get(0).item().def().id());
        assertEquals(3, broke.get(0).drops().get(0).amount());
        Vec3 crown = Fellings.crown(f.of(t), 1);
        assertTrue(crown.x() > 2, "its crown lies away from her, where it fell");

        // its time come: it grows back only once she has gone off
        double back = 4 + fall + 2 + 601;
        f.tick(back, new Vec3(3, 0, 0), random, loot);
        assertNotNull(f.of(t), "she is by it");
        f.tick(back + 1, new Vec3(Fellings.REGROW_AWAY + 5, 0, 0), random, loot);
        assertNull(f.of(t), "grown back");
        assertTrue(f.standing(t));
        assertEquals(Fellings.Event.Kind.REGREW, f.drainEvents().get(0).kind());
    }

    @Test
    void aTreeLeftChoppedHealsAndOneSetNeverToGrowBackDoesNot() {
        Fellings f = new Fellings();
        Scenery.Placed t = tree(0, 0);
        f.chop(t, 2, new Vec3(-1.5, 0, 0), 0, null);
        f.tick(60, null, new Random(1), new Loot());
        assertEquals(2, f.of(t).struck(), 1e-9);
        f.tick(121, null, new Random(1), new Loot());
        assertNull(f.of(t), "the cuts healed");

        Scenery.Placed never = tree(5, 0, 0, new Felling.Own(true, 1, "", 0));
        assertTrue(f.chop(never, 1, new Vec3(3.5, 0, 0), 0, null).felled(), "one blow");
        f.tick(10, null, new Random(1), new Loot());
        f.tick(1e7, new Vec3(1000, 0, 1000), new Random(1), new Loot());
        assertEquals(Fellings.Stage.STUMP, f.of(never).stage(), "a stump for good");
    }

    @Test
    void aPlaceKeepsItsFellingWhenSheComesBack() {
        Fellings f = new Fellings();
        Scenery.Placed t = tree(0, 0);
        f.chop(t, 9, new Vec3(-1.5, 0, 0), 0, null);
        var saved = f.copy();
        f.clear();
        assertTrue(f.standing(t));
        f.restore(saved);
        assertFalse(f.standing(t));
        f.keepOnly(java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        assertTrue(f.standing(t), "its scenery replaced");
    }

    @Test
    void theWorldFellsTheTreeSheIsByAndLaysItsTimberAlongIt() {
        World w = new World(new Wardrobe());
        Scenery.Placed t = tree(2, 0);
        w.setScenery(new Scenery(List.of(t)));
        w.player().setPosition(new Vec3(0.5, 0, 0));
        assertSame(t, w.choppable(), "in reach");
        Item axe = Item.of(ItemDef.BATTLE_AXE, Rarity.MYTHIC);
        assertFalse(w.chop(t, axe, false).felled());
        assertTrue(w.chop(t, axe, false).felled(), "a Mythic axe: two blows of the five");
        assertNull(w.choppable(), "down");
        for (int i = 0; i < 120; i++) w.tick(0.05);
        assertEquals(Fellings.Stage.STUMP, w.felled(t).stage());
        int timber = 0;
        for (World.GroundItem g : w.items()) {
            if (g.item.def().id().equals("timber")) {
                timber += g.amount();
                assertTrue(g.position.x() > t.x(), "along where it fell, away from her");
            }
        }
        assertEquals(3, timber);
        // a weaker blow, out of breath
        Scenery.Placed u = tree(-2, 0);
        w.setScenery(new Scenery(List.of(t, u)));
        assertEquals(Felling.TIRED, w.chop(u, Item.of(ItemDef.BATTLE_AXE), true).struck(), 1e-9);
        assertNotNull(w.felled(t), "a tree kept through the scenery set again");
    }

    @Test
    void inAThickWoodSheKeepsAtTheTreeSheStartedOn() {
        World w = new World(new Wardrobe());
        Scenery.Placed oak = tree(0, 0);
        Scenery.Placed big = new Scenery.Placed(sprite("tall_pine", SceneryProfile.Kind.TREE, OAK), -1.0, 0, 1.5, 0,
                false, 0, 2, 0, null);
        w.setScenery(new Scenery(List.of(oak, big)));
        double[] st = Felling.stand(oak, new Vec3(-2, 0, 0));
        w.player().setPosition(new Vec3(st[0], 0, st[1]));
        w.player().snapHeading(st[2]);
        assertSame(oak, w.choppable(), "the one she faces");
        w.chop(oak, Item.of(ItemDef.BATTLE_AXE), false);
        w.player().snapHeading(st[2] + 1.2);       // (turned towards the other, a little)
        assertSame(oak, w.choppable(), "still the one she struck");
        w.chop(oak, Item.of(ItemDef.BATTLE_AXE, Rarity.MYTHIC), false);
        w.chop(oak, Item.of(ItemDef.BATTLE_AXE, Rarity.MYTHIC), false);
        assertFalse(w.fellings().standing(oak));
        assertSame(big, w.choppable(), "down: the next");
    }

    @Test
    void theChopStandsHerAtTheTreeAndTheBlowLandsAtTheBite() {
        Player p = new Player(new Wardrobe());
        p.setStance(Stance.AXE);
        boolean[] bit = {false};
        double[] stand = {0.6, 0.2, 1.0};
        assertTrue(p.chop(stand, () -> bit[0] = true, s -> 28 / 30.0));
        assertEquals(AnimState.AXE_CHOP, p.action());
        assertTrue(p.chopping());
        assertFalse(p.chop(stand, () -> { }, s -> 1), "busy");
        double t = 0;
        while (!bit[0] && t < 2) {
            p.update(1 / 60.0, Player.Intent.NONE, s -> 28 / 30.0);
            t += 1 / 60.0;
        }
        assertTrue(bit[0]);
        assertEquals(Felling.BITE_AT * 28 / 30.0, t, 0.03, "as the bit strikes");
        assertEquals(0.6, p.ground().x(), 1e-3, "stood where the axe meets the bark");
        assertEquals(0.2, p.ground().z(), 1e-3);
        for (int i = 0; i < 60; i++) p.update(1 / 60.0, Player.Intent.NONE, s -> 28 / 30.0);
        assertNull(p.action(), "over");
        assertEquals(1.0, Math.atan2(Math.sin(p.heading()), Math.cos(p.heading())), 0.05, "turned to the tree");
    }

    @Test
    void theChopIsItsOwnMoveAndNoAttack() {
        assertEquals(Stance.AXE, AnimState.AXE_CHOP.stance());
        assertFalse(AnimState.AXE_CHOP.loops());
        assertNull(com.larsons.game.combat.Move.of(AnimState.AXE_CHOP, Item.of(ItemDef.BATTLE_AXE)),
                "it hits no foe");
        assertTrue(Felling.staminaCost(null, Item.of(ItemDef.BATTLE_AXE)) > 0);
    }

    @Test
    void theLevelSetsOneTreesOwnFelling() {
        ScenerySprites sprites = ScenerySprites.of(List.of(sprite("oak_tree", SceneryProfile.Kind.TREE, OAK),
                sprite("mossy_rock", SceneryProfile.Kind.ROCK, null)), null);
        ObjectTypes.useScenery(sprites);
        try {
            LevelObject o = new LevelObject(1, ObjectTypes.SCENERY, 0, 0, 0, new java.util.HashMap<>()).set("sprite", "oak_tree");
            assertNull(ObjectTypes.felling(o), "as its kind is");
            assertTrue(ObjectTypes.tree(o));
            assertEquals("timber x3", ObjectTypes.kindDrops(o));
            ObjectType type = ObjectTypes.get(ObjectTypes.SCENERY);
            for (String k : List.of("fells", "chops", "regrow", "drops")) {
                ObjectType.Prop p = type.props().stream().filter(x -> x.key().equals(k)).findFirst().orElseThrow();
                assertTrue(ObjectTypes.applies(o, p), k + " on a tree");
            }
            o.set("chops", 3.0).set("drops", "timber x1, coins x2-4 50%").set("regrow", 0.0);
            Felling.Own own = ObjectTypes.felling(o);
            assertEquals(new Felling.Own(true, 3, "timber x1, coins x2-4 50%", 0), own);
            Scenery.Placed placed = LevelWorld.placed(o, flat(), sprites);
            assertEquals(own, placed.felling());
            assertEquals(3, Felling.rule(placed).chops());
            o.set("fells", false);
            assertNull(Felling.rule(LevelWorld.placed(o, flat(), sprites)));
            ObjectType.Prop chops = type.props().stream().filter(x -> x.key().equals("chops")).findFirst().orElseThrow();
            assertFalse(ObjectTypes.applies(o, chops), "not while it cannot be felled");
            LevelObject rock = new LevelObject(2, ObjectTypes.SCENERY, 0, 0, 0, new java.util.HashMap<>()).set("sprite", "mossy_rock");
            ObjectType.Prop fells = type.props().stream().filter(x -> x.key().equals("fells")).findFirst().orElseThrow();
            assertFalse(ObjectTypes.applies(rock, fells), "a rock");
            assertFalse(ObjectTypes.tree(rock));
        } finally {
            ObjectTypes.useScenery(null);
        }
    }

    @Test
    void aTreesChipsAndLeavesAreItsOwnColours() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 9; y++) for (int x = 2; x < 14; x++) img.setRGB(x, y, 0xFF3A7A2A);    // the crown
        for (int y = 9; y < 16; y++) for (int x = 7; x < 9; x++) img.setRGB(x, y, 0xFF6A4A2E);   // the trunk
        TreeColours c = TreeColours.of(img, 16, 16, 0.06);
        assertEquals(0x3A7A2A, c.leaves()[0]);
        assertTrue(java.util.Arrays.stream(c.chips()).anyMatch(v -> v == 0x6A4A2E), "its bark");
        assertTrue(java.util.Arrays.stream(c.chips()).anyMatch(v -> v == TreeColours.WOOD[0]), "fresh wood");
        assertSame(TreeColours.FALLBACK, TreeColours.of(tree(0, 0)), "no sheet to read");
    }

    private static Terrain flat() {
        return Terrain.of(-50, -50, 2, 2, 100, new float[4], List.of(), List.of());
    }
}
