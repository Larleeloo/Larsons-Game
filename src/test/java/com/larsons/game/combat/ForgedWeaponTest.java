package com.larsons.game.combat;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Border;
import com.larsons.game.world.Forged;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Rarity;
import com.larsons.game.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Weapons forged in Creative Mode: kept, read back, named as items, their dice and effects in a fight. */
class ForgedWeaponTest {

    private static final double DT = 1 / 60.0;
    private static final ToDoubleFunction<AnimState> DUR = s -> AnimState.duration(s.defaultFrames(), 30);
    private static final Player.Intent ATTACK = new Player.Intent(0, 0, false, false, false, true);

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

    private static Forged duskfang() {
        return new Forged(null, "Duskfang", "sword", Rarity.EPIC,
                Map.of("blade", 0xB722E7, "handle", 0x231C18), 3, 8, 0x5D86FA,
                Set.of(Border.VENOM, Border.MYSTIC, Border.BONDED));
    }

    @Test
    void itIsKeptReadBackAndNamedAsAnItem() throws Exception {
        Forged kept = Forged.save(duskfang());
        assertEquals("duskfang", kept.id(), "its file is named for it");
        assertTrue(Files.isRegularFile(dir.resolve("duskfang.json")));
        assertEquals("duskfang_2", Forged.save(duskfang()).id(), "another of the same name is another file");
        Forged back = Forged.byId("duskfang");
        assertEquals(kept, back, "read back as it was kept");

        Item item = Item.parse("forged:duskfang");
        assertNotNull(item);
        assertSame(ItemDef.SWORD, item.def(), "a sword: its look, its stance, its moves");
        assertEquals("Duskfang", item.name());
        assertEquals(Rarity.EPIC, item.rarity());
        assertNull(item.tier());
        assertEquals("forged:duskfang", item.key());
        assertEquals(item, Item.parse(item.key()));
        assertTrue(item.has(Border.VENOM) && item.has(Border.MYSTIC) && item.has(Border.BONDED), "all three at once");
        assertEquals("3d8", back.notation());
        assertNull(Item.parse("forged:no_such_thing"));

        Forged.delete("duskfang");
        assertNull(Forged.byId("duskfang"));
    }

    @Test
    void whatItIsGivenIsKeptInBounds() {
        Forged f = new Forged("x", " ", "dye_pink", null, null, 99, 7, -5, null);
        assertEquals("Forged weapon", f.name());
        assertEquals("sword", f.base(), "only a weapon is forged");
        assertEquals(Rarity.EPIC, f.rarity());
        assertEquals(Forged.MAX_DICE, f.dice());
        assertEquals(20, f.sides(), "there is no d7");
        assertEquals(-1, f.diceRgb());
        assertTrue(f.effects().isEmpty() && f.tints().isEmpty());
        assertEquals("2d%", f.withDice(2).withSides(100).notation());
        assertFalse(f.withTint("blade", 0xFF0000).withTint("blade", -1).tints().containsKey("blade"));
    }

    @Test
    void itRollsItsOwnDiceAndEveryEffectLands() {
        Wardrobe wardrobe = new Wardrobe();
        wardrobe.set(Slot.CARRY_RIGHT, "sword");
        World w = new World(wardrobe);
        Item weapon = duskfang().withId("duskfang").item();
        w.player().inventory().add(weapon);
        w.equipSelected();
        w.seed(42);
        w.animals().setEnabled(false);
        Dummy d = w.addDummy(new Dummy(new Vec3(0, 0, -1.2), SpriteView.headingOf(0, 1), 1000, 6));
        double mana = w.combat().mana();
        for (int i = 0; i < 2; i++) {
            w.player().update(DT, i == 0 ? ATTACK : Player.Intent.NONE, DUR);
            w.tick(DT);
        }
        Combat.Show show = w.combat().show();
        assertNotNull(show);
        DiceRoll roll = show.strike().roll();
        assertEquals(3, roll.count(), "three dice");
        assertEquals(8, roll.sides(), "of eight sides");
        for (int i = 0; i < roll.count(); i++) assertTrue(roll.face(i) >= 1 && roll.face(i) <= 8);
        assertEquals("3d8", roll.notation());
        assertEquals(0x5D86FA, show.strike().diceRgb(), "in their own colour");
        assertTrue(w.combat().mana() < mana, "Mystic draws its mana");
        List<String> notes = show.notes().stream().map(Combat.Note::text).toList();
        assertEquals(3, notes.size(), "a line for each effect: " + notes);
        for (int i = 0; i < 30; i++) {
            w.player().update(DT, Player.Intent.NONE, DUR);
            w.tick(DT);
        }
        long hit = Math.round(roll.total() * Border.MYSTIC_DAMAGE * (1 + Border.BONDED_PER_HIT));
        assertEquals(1000 - hit, d.health(), 1e-9, "Mystic ×2.5 and Bonded +10% together");
        for (int i = 0; i < 12 * 60; i++) w.tick(DT);
        assertEquals(1000 - hit * (1 + Border.VENOM_SHARE), d.health(), 0.01, "and Venom's poison after it");
    }

    @Test
    void aPercentileRollShowsOnItsTensDieAndADTen() {
        assertArrayEquals(new String[]{"dpct", "land_40"}, CombatHud.face(100, 47, 0));
        assertArrayEquals(new String[]{"d10", "land_07"}, CombatHud.face(100, 47, 1));
        assertArrayEquals(new String[]{"dpct", "land_00"}, CombatHud.face(100, 100, 0), "00 and 0 is a hundred");
        assertArrayEquals(new String[]{"d10", "land_10"}, CombatHud.face(100, 100, 1));
        assertArrayEquals(new String[]{"dpct", "land_10"}, CombatHud.face(100, 10, 0));
        assertArrayEquals(new String[]{"dpct", "land_00"}, CombatHud.face(100, 5, 0));
        assertArrayEquals(new String[]{"d6", "land_04"}, CombatHud.face(6, 4, 0));
        assertEquals("2d%", new DiceRoll(100, new int[]{47, 100}).notation());
    }

    @Test
    void everyFaceOfEveryDieHasItsSheets() {
        Path combat = Path.of("assets", "sprites", "combat");
        for (int sides : Forged.SIDES) {
            for (int v = 1; v <= sides; v++) {
                for (int half = 0; half < (sides == 100 ? 2 : 1); half++) {
                    String[] f = CombatHud.face(sides, v, half);
                    for (String px : new String[]{"_px64", "_px128"}) {
                        assertTrue(Files.isRegularFile(combat.resolve(f[0] + px).resolve(f[1] + ".png")),
                                f[0] + px + "/" + f[1]);
                        assertTrue(Files.isRegularFile(combat.resolve(f[0] + px).resolve("roll.png")));
                    }
                }
            }
        }
    }

    @Test
    void diceInAColourOfTheirOwn() {
        int[] blue = CombatColours.die(0x5D86FA, null);
        assertEquals(0x5D86FA, blue[2], "the colour is their faces' mid shade");
        assertEquals(0xFFFFFF, blue[5], "white numbers on a darker die");
        assertEquals(0x231C18, CombatColours.die(0xFCDD9E, null)[5], "dark numbers on a light one");
        assertEquals(Border.VENOM.rgb(), CombatColours.die(0x5D86FA, Border.VENOM)[0], "the rim in its effect's colour");
        assertNotSame(CombatColours.diePalette(Rarity.EPIC, null), CombatColours.diePalette(Rarity.EPIC, null, 0x5D86FA));
        assertSame(CombatColours.diePalette(Rarity.EPIC, null, 0x5D86FA),
                CombatColours.diePalette(Rarity.EPIC, null, 0x5D86FA), "one array for the GPU's palette rows");
    }
}
