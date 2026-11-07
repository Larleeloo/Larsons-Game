package com.larsons.game.combat;

import com.larsons.game.world.Border;
import com.larsons.game.world.Forged;
import com.larsons.game.world.Item;
import com.larsons.game.world.Rarity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.larsons.game.combat.DiceRoll.Op.*;
import static org.junit.jupiter.api.Assertions.*;

/** Dice that add, take away, multiply, divide or scale by a percent, and a forged weapon's upgrade tiers. */
class DiceOperatorsTest {

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

    private static DiceRoll roll(int[] faces, int[] sides, DiceRoll.Op... ops) {
        return new DiceRoll(sides[0], faces, sides, ops);
    }

    @Test
    void theDiceAreWorkedOutLeftToRight() {
        // 2d8 + 1d6 × 1d4 rolled 5, 3, 4, 2: ((5 + 3) + 4) × 2
        DiceRoll r = roll(new int[]{5, 3, 4, 2}, new int[]{8, 8, 6, 4}, ADD, ADD, ADD, MUL);
        assertEquals(24, r.total());
        assertEquals("2d8 + 1d6 × 1d4", r.notation());
        assertFalse(r.plain());

        assertEquals(7, roll(new int[]{10, 3}, new int[]{10, 4}, ADD, SUB).total(), "take away");
        assertEquals(0, roll(new int[]{2, 6}, new int[]{4, 6}, ADD, SUB).total(), "never below nothing");
        assertEquals(4, roll(new int[]{12, 3}, new int[]{12, 4}, ADD, DIV).total(), "divide");
        assertEquals(3, roll(new int[]{10, 3}, new int[]{10, 4}, ADD, DIV).total(), "rounded: 3.33");
        // a d% come up 40 scales what came before by +40%
        assertEquals(14, roll(new int[]{10, 40}, new int[]{10, 100}, ADD, PCT).total());
        assertEquals("1d10 % 1d%", roll(new int[]{10, 40}, new int[]{10, 100}, ADD, PCT).notation());

        DiceRoll plain = new DiceRoll(20, new int[]{4, 17});
        assertTrue(plain.plain());
        assertEquals("2d20", plain.notation());
        assertEquals(21, plain.total());
    }

    @Test
    void aMixedRollRollsEachGroupsOwnDice() {
        Random random = new Random(3);
        for (int k = 0; k < 50; k++) {
            DiceRoll r = DiceRoll.roll(2, 8, List.of(new DiceRoll.Group(1, 4, MUL), new DiceRoll.Group(1, 100, PCT)),
                    random);
            assertEquals(4, r.count());
            assertEquals(8, r.sidesOf(1));
            assertEquals(4, r.sidesOf(2));
            assertEquals(MUL, r.op(2));
            assertEquals(100, r.sidesOf(3));
            for (int i = 0; i < r.count(); i++) {
                assertTrue(r.face(i) >= 1 && r.face(i) <= r.sidesOf(i));
            }
            double expect = (r.face(0) + r.face(1)) * r.face(2);
            expect += expect * r.face(3) / 100.0;
            assertEquals(Math.round(expect), r.total());
        }
    }

    @Test
    void theHudPutsTheSumsBetweenTheDice() {
        DiceRoll r = roll(new int[]{5, 3, 4, 2, 40}, new int[]{8, 8, 6, 4, 100}, ADD, ADD, ADD, MUL, PCT);
        List<CombatHud.Slot> slots = CombatHud.slots(r);
        StringBuilder s = new StringBuilder();
        for (CombatHud.Slot sl : slots) s.append(sl.glyph() ? sl.op() : "d").append(' ');
        // two d8s, + a d6 (another kind), × a d4, % a d% (two dice: tens and ones)
        assertEquals("d d + d × d % d d ", s.toString());
        for (List<CombatHud.Slot> row : CombatHud.rows(slots, CombatHud.DIE, CombatHud.ROW)) {
            assertFalse(row.get(row.size() - 1).glyph(), "a sum is never left at a row's end");
        }
    }

    @Test
    void aForgedWeaponRollsItsMoreDiceAndClimbsItsTiers() throws Exception {
        Forged f = new Forged(null, "Duskfang", "sword", Rarity.UNCOMMON, Map.of("blade", 0x444444), 2, 8, -1,
                Set.of(Border.VENOM))
                .withExtra(List.of(new DiceRoll.Group(1, 4, MUL)))
                .withAmmoEvery(10)
                .withUpgrades(List.of(
                        new Forged.Upgrade("", Rarity.RARE, 3, 8, List.of(new DiceRoll.Group(1, 4, MUL)), -1,
                                Map.of("blade", 0x2266CC), 14, 2, false),
                        new Forged.Upgrade("Dusk Eternal", Rarity.LEGENDARY, 4, 10, List.of(), 0xFFD27A,
                                Map.of(), 20, 5, true)));
        assertEquals(3, f.tiers());
        assertEquals("2d8 × 1d4", f.diceText());
        assertEquals(10, f.ammoEvery());
        assertEquals(2, f.nextCost());

        Forged two = f.upgraded();
        assertEquals("Duskfang +1", two.name());
        assertEquals(Rarity.RARE, two.rarity());
        assertEquals(3, two.dice());
        assertEquals(0x2266CC, two.tints().get("blade"), "upgraded: a new colour");
        assertEquals(14, two.ammoEvery());
        Forged three = two.upgraded();
        assertEquals("Dusk Eternal", three.name());
        assertEquals(0x2266CC, three.tints().get("blade"), "the colours so far kept");
        assertNull(three.upgraded(), "the last");
        assertEquals(-1, three.nextCost());
        assertTrue(three.tier().mystery());

        Forged kept = Forged.save(f);
        Forged back = Forged.byId(kept.id());
        assertEquals(kept, back, "its tiers kept and read back");
        Item item = Item.parse("forged:" + kept.id() + ":2");
        assertNotNull(item);
        assertEquals("Dusk Eternal", item.name());
        assertEquals(Rarity.LEGENDARY, item.rarity());
        assertEquals("forged:" + kept.id() + ":2", item.key());
        assertEquals(item, Item.parse(item.key()));
        assertEquals(10, Forged.save(three).at(0).ammoEvery(), "saved as forged, whatever tier it was at");
    }
}
