package com.larsons.game.actor;

import com.larsons.game.level.FolkTypes;
import com.larsons.game.level.LevelObject;
import com.larsons.game.level.ObjectType;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Forged;
import com.larsons.game.world.Rarity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fighting of the folk, changed in the editor: health, the blows, how
 * often, how fast they go, how far they see, the guard, the experience - a
 * forged weapon in their hands, and armour that rolls its dice to block.
 */
class FolkStatsTest {

    @TempDir
    Path dir;

    @BeforeEach
    void forgeHere() {
        Forged.useDir(dir);
    }

    @AfterEach
    void forgeBack() {
        Forged.useDir(Path.of("assets", Forged.FOLDER));
    }

    private static LevelObject foe(Map<String, Object> more) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("breed", Breed.BANDIT.key());
        props.put("size", 1.0);
        props.put("weapon", "sword");
        props.putAll(more);
        return new LevelObject(31, FolkTypes.FOE, 0, 0, 0, props);
    }

    private static Folk spawn(LevelObject o) {
        Folks folks = new Folks();
        Folk f = folks.spawn(o.id(), Breed.BANDIT, Vec3.ZERO, 0, spec(o));
        ObjectTypes.get(FolkTypes.FOE);                 // (the kinds are all registered)
        return statsOf(f, o);
    }

    // the same as a level stands a foe up with (FolkTypes keeps it to itself: through its line of stats)
    private static Folks.Spec spec(LevelObject o) {
        return new Folks.Spec(o.str("body", "either"), o.str("weapon", "own"), o.num("size", 0), 0,
                o.str("name", ""), null, true, null, FolkTypes.look(o));
    }

    private static Folk statsOf(Folk f, LevelObject o) {
        return FolkTypes.stats(f, o);
    }

    @Test
    void everyStatTheEditorChangesChangesHowItFights() {
        Folk plain = spawn(foe(Map.of()));
        Folk changed = spawn(foe(Map.of("health", 3.0, "damage", 2.0, "pace", 2.0, "speed", 1.5, "sight", 30.0,
                "guard", 0.0, "xp", 2.5)));
        assertEquals(plain.maxHealth() * 3, changed.maxHealth(), 1);
        assertEquals(plain.blowDamage() * 2, changed.blowDamage(), 1);
        assertEquals(plain.attackEvery() / 2, changed.attackEvery(), 1e-6);
        assertEquals(plain.walkSpeed() * 1.5, changed.walkSpeed(), 1e-6);
        assertEquals(plain.runSpeed() * 1.5, changed.runSpeed(), 1e-6);
        assertEquals(30, changed.sight(), 1e-6);
        assertEquals(Breed.BANDIT.sight(), plain.sight(), 1e-6, "0: its kind's");
        assertEquals(0, changed.guardChance(), 1e-9);
        assertEquals(2.5, changed.xpTimes(), 1e-9);
        assertEquals(1, plain.xpTimes(), 1e-9);
        // and the editor says so, under the props
        String line = FolkTypes.statsLine(foe(Map.of("health", 3.0)));
        assertNotNull(line);
        assertTrue(line.contains(Math.round(spawn(foe(Map.of("health", 3.0))).maxHealth()) + " health"), line);
        assertTrue(line.contains("experience"), line);
    }

    @Test
    void theStatsAreAmongTheEnemysAndThePersonsProps() {
        for (String type : new String[]{FolkTypes.FOE, FolkTypes.PERSON}) {
            ObjectType t = ObjectTypes.get(type);
            for (String key : new String[]{"health", "damage", "pace", "speed", "armour"}) {
                assertTrue(t.props().stream().anyMatch(p -> p.key().equals(key)), type + " has " + key);
            }
        }
        ObjectType foe = ObjectTypes.get(FolkTypes.FOE);
        for (String key : new String[]{"sight", "guard", "xp"}) {
            assertTrue(foe.props().stream().anyMatch(p -> p.key().equals(key)), "a foe has " + key);
        }
    }

    @Test
    void theLookIsTheWardrobesNotANumberOnASlider() {
        LevelObject o = foe(Map.of());
        ObjectType t = ObjectTypes.get(FolkTypes.FOE);
        ObjectType.Prop look = t.props().stream().filter(p -> p.key().equals("look")).findFirst().orElseThrow();
        ObjectType.Prop body = t.props().stream().filter(p -> p.key().equals("body")).findFirst().orElseThrow();
        assertFalse(ObjectTypes.applies(o, look), "no slider");
        assertTrue(ObjectTypes.applies(o, body), "as it comes: its body can be picked");
        Wardrobe made = FolkTypes.lookNow(o).copy();
        made.set(Slot.HAIR, "braids");
        o.set(FolkTypes.WARDROBE, made.toJson());
        assertFalse(ObjectTypes.applies(o, body), "dressed by hand: the wardrobe has its body");
        assertTrue(FolkTypes.dressed(o));
    }

    @Test
    void aForgedWeaponIsOfferedHeldAndHitsAsItsDiceRoll() throws Exception {
        Forged kept = Forged.save(new Forged(null, "Gloamcleaver", "battle_axe", Rarity.LEGENDARY, Map.of(), 4, 10,
                -1, Set.of()));
        String key = kept.key();
        assertTrue(FolkTypes.weapons().contains(key), "a foe can be handed it");
        LevelObject o = foe(Map.of("weapon", key));
        Folk f = spawn(o);
        assertNotNull(f.weapon());
        assertNotNull(f.weapon().forged(), "it holds the forged one");
        assertEquals(kept.id(), f.weapon().forged().id());
        // 4d10 come up 22 on average: its blow is about that (its size is 1)
        assertEquals(22, f.blowDamage(), 4);
        // twice as hard, twice that
        assertEquals(f.blowDamage() * 2, spawn(foe(Map.of("weapon", key, "damage", 2.0))).blowDamage(), 2);
    }

    @Test
    void armourTheEditorMakesBlockRollsItsDice() {
        Map<String, Object> more = new LinkedHashMap<>(Map.of("armour", true));
        LevelObject o = foe(more);
        Wardrobe made = FolkTypes.lookNow(o).copy();
        made.set(Slot.CHESTPLATE, "iron_chestplate");
        made.set(Slot.HAT, "iron_helmet");
        made.set(Slot.SHOES, "sabatons");
        made.set(Slot.CHAINMAIL_LEGS, "chainmail_pants");
        made.set(Slot.GLOVES, "iron_gauntlets");
        o.set(FolkTypes.WARDROBE, made.toJson());
        Folk armoured = spawn(o);
        assertTrue(armoured.armoured());
        double through = 0;
        int blocked = 0;
        for (int i = 0; i < 200; i++) {
            double t = armoured.armour(10);
            assertTrue(t >= 0 && t <= 10, "never more than the blow: " + t);
            through += t;
            if (t < 10) blocked++;
            assertNotNull(armoured.armourRoll());
        }
        assertTrue(blocked > 100, "it mostly blocks some: " + blocked);
        assertTrue(through < 200 * 10);

        // without the switch, the same armour is only worn
        o.set("armour", false);
        Folk worn = spawn(o);
        assertEquals(10, worn.armour(10), 1e-9);
        assertNull(worn.armourRoll());
        // and a creature, drawn in sheets of its own, wears none to roll
        Folk creature = new Folks().spawn(40, Breed.BORTZAL, Vec3.ZERO, 0, Folks.Spec.ANY).armoured(true);
        assertEquals(10, creature.armour(10), 1e-9);
    }
}
