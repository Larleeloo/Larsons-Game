package com.larsons.game.actor;

import com.larsons.game.level.HordeRules;
import com.larsons.game.save.SaveGame;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Variants;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Item;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The peoples of the main lands and their foes: what each looks like, and that the sheets for it are there. */
class PeoplesTest {

    private static final Path FEMININE = Path.of("assets", "sprites"), MASCULINE = Path.of("assets", "sprites_masculine");

    @Test
    void thePeoplesSheCanBeAreTheFourOfTheMainLands() {
        assertEquals(SaveGame.PEOPLES, People.playableKeys());
        assertEquals(4, People.playableOnes().size());
        assertFalse(People.BORTZAL.playable());
        assertFalse(People.RISEN.playable());
        for (People p : People.values()) {
            assertSame(p, People.byKey(p.key()));
            assertSame(p, People.byKey(p.label()));
            assertFalse(p.skins().isEmpty() || p.ears().isEmpty(), p.key());
            assertTrue(p.smallest() <= p.tallest(), p.key());
            assertFalse(p.about().isBlank());
            assertFalse(Looks.hairColours(p).isEmpty());
            if (p.playable()) {
                assertTrue(p.smallest() >= SaveGame.SMALLEST && p.tallest() <= SaveGame.TALLEST, p.key());
            }
        }
        assertSame(People.HUMAN, People.byKey("nobody"));
        // only the Khorigale are horned and tailed; a Drurinean's ears are the long ones
        for (People p : People.values()) {
            assertEquals(p == People.KHORIGALE, p.horns(), p.key());
            assertEquals(p == People.KHORIGALE, p.tail(), p.key());
        }
        assertEquals("elven", People.DRURINEAN.ears().get(0));
        assertTrue(People.KHORIGALE.tallest() < People.HUMAN.tallest(), "a little smaller than a human");
        // the Bortzal come in every size
        assertEquals(0.5, People.BORTZAL.smallest());
        assertEquals(1.5, People.BORTZAL.tallest());
    }

    @Test
    void aPersonLooksTheSameEveryTimeAndUnlikeTheNext() {
        for (People p : People.values()) {
            for (boolean masculine : new boolean[]{false, true}) {
                Set<Object> looks = new HashSet<>();
                for (long seed = 0; seed < 40; seed++) {
                    Wardrobe a = Looks.of(p, masculine, Looks.Dress.OWN, seed);
                    Wardrobe b = Looks.of(p, masculine, null, seed);
                    assertEquals(a.toJson(), b.toJson(), p.key() + " " + seed);
                    looks.add(a.toJson());
                    assertEquals(masculine ? "masculine" : "feminine", a.get(Slot.BODY));
                    assertTrue(p.skins().contains(a.colour(Slot.BODY)), p.key());
                    assertTrue(p.ears().contains(a.get(Slot.EARS)), p.key());
                    assertTrue(Looks.hairColours(p).contains(a.colour(Slot.HAIR)), p.key());
                    assertEquals(p.horns() ? People.HORNS : null, a.get(Slot.HORNS), p.key());
                    assertEquals(p.tail() ? People.TAIL : null, a.get(Slot.TAIL), p.key());
                    assertNotNull(a.get(Slot.UNDERWEAR));
                    assertEquals(!masculine, a.get(Slot.BRA) != null);
                }
                assertTrue(looks.size() > 20, p.key() + ": " + looks.size() + " looks in 40");
            }
        }
    }

    @Test
    void everyWayOfDressingClothesHerAndEverythingWornHasItsSheets() {
        Set<String> seen = new HashSet<>();
        for (People p : People.values()) {
            for (Looks.Dress dress : Looks.Dress.values()) {
                for (boolean masculine : new boolean[]{false, true}) {
                    Path root = masculine ? MASCULINE : FEMININE;
                    for (long seed = 0; seed < 60; seed++) {
                        Wardrobe w = Looks.of(p, masculine, dress, seed);
                        Looks.Dress worn = dress == Looks.Dress.OWN ? Looks.usual(p) : dress;
                        String who = p.key() + " " + worn.key() + " " + (masculine ? "m" : "f") + " " + seed;
                        // legs covered - by trousers, mail, or a dress that is all one garment
                        assertTrue(w.get(Slot.PANTS) != null || w.get(Slot.CHAINMAIL_LEGS) != null
                                || w.get(Slot.SHIRT) != null, who + " has bare legs");
                        if (worn != Looks.Dress.RAGGED) {
                            assertNotNull(w.get(Slot.SHOES), who + " is barefoot");
                            assertTrue(w.get(Slot.SHIRT) != null || w.get(Slot.CHESTPLATE) != null, who + " has no top");
                        }
                        for (Slot slot : Slot.values()) {
                            String item = w.get(slot);
                            if (item == null || slot.carried()) continue;
                            Path dir = root.resolve(slot.key()).resolve(Wardrobe.Style.PIXEL_128.folder(item));
                            if (!seen.add(dir + "|" + w.colour(slot))) continue;
                            assertTrue(Files.isDirectory(dir), who + ": no sheets for " + slot.key() + "/" + item);
                            assertTrue(Files.isDirectory(root.resolve(slot.key())
                                    .resolve(Wardrobe.Style.PIXEL_64.folder(item))), who + ": " + item + " at 64");
                            String colour = w.colour(slot);
                            Variants v = Variants.load(dir);
                            if (colour == null || v == null || Variants.isCustom(colour)) continue;
                            String channel = slot == Slot.BODY ? Variants.SKIN : Variants.OWN;
                            if (!v.has(channel)) continue;
                            assertTrue(v.channel(channel).options().containsKey(colour),
                                    who + ": " + item + " does not come in " + colour);
                        }
                    }
                }
            }
        }
    }

    @Test
    void theHornsAndTheTailHaveASheetForEveryOneOfTheBodys() throws IOException {
        for (Path root : new Path[]{FEMININE, MASCULINE}) {
            String body = root == FEMININE ? "feminine" : "masculine";
            for (Wardrobe.Style style : new Wardrobe.Style[]{Wardrobe.Style.PIXEL_128, Wardrobe.Style.PIXEL_64}) {
                Set<String> sheets = pngs(root.resolve("body").resolve(style.folder(body)));
                assertFalse(sheets.isEmpty());
                for (String[] layer : new String[][]{{"horns", People.HORNS}, {"tail", People.TAIL}}) {
                    Path dir = root.resolve(layer[0]).resolve(style.folder(layer[1]));
                    assertEquals(sheets, pngs(dir), dir.toString());
                    assertTrue(Files.isRegularFile(dir.resolve("profile.json")), dir.toString());
                    Variants v = Variants.load(dir);
                    assertNotNull(v, dir.toString());
                    if (layer[0].equals("tail")) {
                        // a tail is her skin's colour, whatever people's skin it is
                        for (People p : People.values()) {
                            for (String skin : p.skins()) {
                                assertTrue(v.channel(Variants.SKIN).options().containsKey(skin), dir + ": " + skin);
                            }
                        }
                    } else {
                        assertTrue(v.channel(Variants.OWN).options().keySet()
                                .containsAll(Set.of("bone", "ebony", "amber")), dir.toString());
                    }
                }
            }
        }
    }

    private static Set<String> pngs(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            Set<String> out = new HashSet<>();
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".png")).forEach(out::add);
            return out;
        }
    }

    @Test
    void everyFoeHasItsPeopleItsWeaponsAndItsPlaceAmongTheLivingOrTheRisen() {
        Set<String> keys = new HashSet<>();
        for (Breed b : Breed.values()) {
            assertTrue(keys.add(b.key()), b.key());
            assertSame(b, Breed.byKey(b.key()));
            assertSame(b, Breed.byKey(b.label()));
            assertNotNull(b.people());
            assertTrue(b.health() > 0 && b.damage() > 0 && b.walk() > 0 && b.run() >= b.walk(), b.key());
            assertTrue(b.sight() > 0 && b.every() > 0 && b.xp() > 0, b.key());
            assertTrue(b.guard() >= 0 && b.guard() < 1, b.key());
            assertTrue(b.smallest() > 0 && b.smallest() <= b.tallest(), b.key());
            for (String weapon : b.weapons()) assertNotNull(Item.parse(weapon), b.key() + " carries " + weapon);
            assertEquals(b.risen(), Breed.horde().contains(b));
            assertEquals(!b.risen(), Breed.living().contains(b));
        }
        assertNull(Breed.byKey("ghoul"));
        assertEquals(Breed.horde(), HordeRules.risen());
        // each people of the main lands that fights is there, and the horde has its kinds
        for (People p : new People[]{People.BORTZAL, People.DRURINEAN, People.HALF_DRURINEAN, People.KHORIGALE}) {
            assertTrue(Breed.living().stream().anyMatch(b -> b.people() == p), p.key());
        }
        assertTrue(Breed.horde().size() >= 5);
        assertTrue(Breed.RISEN_BRUTE.steady() && Breed.RISEN_CRAWLER.crawls());
        // the Risen are creatures of their own, and fight with their hands alone
        for (Breed b : Breed.horde()) {
            assertTrue(b.creature(), b.key());
            assertFalse(b.spits(), b.key());
            assertTrue(b.weapons().isEmpty(), b.key());
            for (boolean masc : new boolean[]{true, false}) {
                assertFalse(b.risenSprites(masc).isEmpty(), b.key());
                for (String sheet : b.risenSprites(masc)) {
                    assertTrue(java.nio.file.Files.isRegularFile(java.nio.file.Path.of("assets/sprites/objects", sheet,
                            "profile.json")), sheet);
                }
            }
        }
        assertTrue(Breed.RISEN_RUNNER.run() > Breed.RISEN_WALKER.run());
        assertTrue(Breed.RISEN_BRUTE.health() > 3 * Breed.RISEN_WALKER.health());
        // the Bortzal are of every size: half a person's height to half as tall again
        assertTrue(Breed.BORTZAL.creature());
        assertEquals(0.5, Breed.BORTZAL.smallest());
        assertEquals(1.5, Breed.BORTZAL.tallest());
    }
}
