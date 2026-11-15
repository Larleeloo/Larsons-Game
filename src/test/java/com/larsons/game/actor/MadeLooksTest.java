package com.larsons.game.actor;

import com.larsons.game.level.FolkTypes;
import com.larsons.game.level.LevelObject;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Variants;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** A look made by hand in the editor - and that nobody comes with painted lips on skin as black as soot. */
class MadeLooksTest {

    private static LevelObject person(int id, String people) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("name", "Old Tam");
        props.put("people", people);
        return new LevelObject(id, FolkTypes.PERSON, 4, 5, 0, props);
    }

    @Test
    void aPersonIsAsTheirSeedDrawsThemUntilALookIsMadeForThem() {
        LevelObject tam = person(72, People.HUMAN.key());
        assertNull(FolkTypes.look(tam), "none made");
        Wardrobe drawn = FolkTypes.lookNow(tam);
        assertNotNull(drawn);
        assertEquals(drawn.toJson(), FolkTypes.lookNow(tam).toJson(), "the same every time");

        // made by hand: every layer and its colour, as written
        Wardrobe made = drawn.copy();
        made.set(Slot.HAIR, "curls");
        made.setColour(Slot.HAIR, "#918780");
        made.set(Slot.SHIRT, "laced_shirt");
        made.setColour(Slot.SHIRT, "#1c8a7a");
        made.setColour(Slot.BODY, "#7c3c24");
        tam.set(FolkTypes.WARDROBE, made.toJson());
        Wardrobe now = FolkTypes.lookNow(tam);
        assertEquals(made.toJson(), now.toJson());
        assertEquals("curls", now.get(Slot.HAIR));
        assertEquals("#918780", now.colour(Slot.HAIR));
        assertEquals("#7c3c24", now.skin());
        // it stands for the body, the skin and the dress the props would have drawn
        tam.set("outfit", "elegant");
        tam.set("body", "masculine");
        assertEquals(made.toJson(), FolkTypes.lookNow(tam).toJson());
        // each asking gets a look of its own to change
        now.set(Slot.HAIR, "bob");
        assertEquals("curls", FolkTypes.lookNow(tam).get(Slot.HAIR));
        // and without it they are as they came
        tam.props().remove(FolkTypes.WARDROBE);
        assertEquals("masculine", FolkTypes.lookNow(tam).get(Slot.BODY));
    }

    @Test
    void aDressedFoeTakesAMadeLookAndACreatureHasNoneToMake() {
        Folks.Spec spec = Folks.Spec.ANY;
        Breed dressed = null, creature = null;
        for (Breed b : Breed.values()) {
            if (b.risen() || b.creature()) creature = creature == null ? b : creature;
            else dressed = dressed == null ? b : dressed;
        }
        assertNotNull(dressed);
        assertNotNull(creature);
        assertNull(Folks.foeLook(5, creature, spec), creature + " is a creature of its own sprites");
        Wardrobe own = Folks.foeLook(5, dressed, spec);
        assertNotNull(own);
        Wardrobe made = new Wardrobe();
        made.set(Slot.BODY, "feminine");
        made.set(Slot.HAIR, "braids");
        Wardrobe got = Folks.foeLook(5, dressed, spec.look(made));
        assertEquals(made.toJson(), got.toJson());
        assertNotSame(made, got);
        assertSame(made, spec.look(made).weapon("mace").size(1.2).seed(9).look(), "kept as the rest is changed");
        assertNull(Folks.Spec.ANY.look());
    }

    @Test
    void nobodyComesWithSootBlackSkinAndPaintedLips() {
        // the humans' usual tones are skin's: none is the near-black grey the dye of that name is
        assertFalse(People.HUMAN.skins().contains("black"));
        assertTrue(People.HUMAN.skins().contains(People.DEEP_BROWN));
        assertTrue(Variants.isCustom(People.DEEP_BROWN), "a deep brown of the palette");
        double[] deep = Variants.Oklab.of(Integer.parseInt(People.DEEP_BROWN.substring(1), 16));
        assertTrue(Math.hypot(deep[1], deep[2]) > 0.05, "a brown, not a grey");
        assertTrue(deep[0] > 0.25, "and not black");
        // whoever a seed draws: their lips are not painted (the natural colour of their skin is drawn)
        for (People p : People.values()) {
            for (int id = 1; id <= 60; id++) {
                LevelObject o = person(id, p.key());
                Wardrobe w = FolkTypes.lookNow(o);
                assertNull(w.colour(Slot.MOUTH), p + " " + id + ": lips as they are");
                if (p == People.HUMAN) assertNotEquals("black", w.skin(), "human " + id);
            }
        }
    }
}
