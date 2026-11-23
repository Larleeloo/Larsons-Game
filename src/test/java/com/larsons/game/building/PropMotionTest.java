package com.larsons.game.building;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** What moves on the things that stand about, read off their models' parts. */
class PropMotionTest {

    private static BuildingCatalog catalog;

    @BeforeAll
    static void load() {
        catalog = BuildingCatalog.load(Path.of("assets", "buildings"));
    }

    private static List<PropMotion.Source> of(String key) throws Exception {
        BuildingProfile p = catalog.profile(key);
        assertNotNull(p, key);
        return PropMotion.of(p, BuildingModel.load(catalog.root().resolve(key)));
    }

    private static Set<PropMotion.Kind> kinds(String key) throws Exception {
        return of(key).stream().map(PropMotion.Source::kind).collect(Collectors.toSet());
    }

    @Test
    void aCauldronBubblesOverAnOpenFire() throws Exception {
        assertEquals(Set.of(PropMotion.Kind.BUBBLES, PropMotion.Kind.FIRE, PropMotion.Kind.EMBERS), kinds("cauldron"));
        PropMotion.Source brew = of("cauldron").stream().filter(s -> s.kind() == PropMotion.Kind.BUBBLES)
                .findFirst().orElseThrow();
        PropMotion.Source fire = of("cauldron").stream().filter(s -> s.kind() == PropMotion.Kind.FIRE)
                .findFirst().orElseThrow();
        assertTrue(brew.z() > fire.z(), "the brew is over the fire");
    }

    @Test
    void theLightningCoilSparksFromItsCrown() throws Exception {
        PropMotion.Source spark = of("tesla_coil").stream().filter(s -> s.kind() == PropMotion.Kind.ARCS)
                .findFirst().orElseThrow();
        assertTrue(spark.z() > catalog.profile("tesla_coil").height() * 0.7, "at its top: " + spark.z());
    }

    @Test
    void aStoveSmokesAndABoilerSteamsButNeitherSparks() throws Exception {
        assertTrue(kinds("stove").contains(PropMotion.Kind.SMOKE));
        assertFalse(kinds("stove").contains(PropMotion.Kind.EMBERS), "its fire burns behind a door");
        assertTrue(kinds("boiler").contains(PropMotion.Kind.STEAM));
    }

    @Test
    void eachChimneySmokesByItself() throws Exception {
        long smokes = of("foundry").stream().filter(s -> s.kind() == PropMotion.Kind.SMOKE).count();
        assertTrue(smokes >= 1 && smokes <= PropMotion.MOST_CHIMNEYS, "stacks: " + smokes);
        long flames = of("candelabra").stream().filter(s -> s.kind() == PropMotion.Kind.FLICKER).count();
        assertTrue(flames >= 3, "a candelabra's candles each flicker: " + flames);
    }

    @Test
    void aFountainLeapsAndACrystalGlints() throws Exception {
        assertTrue(kinds("fountain").contains(PropMotion.Kind.SPRAY));
        PropMotion.Source jet = of("fountain").stream().filter(s -> s.kind() == PropMotion.Kind.SPRAY)
                .findFirst().orElseThrow();
        assertTrue(jet.z() > jet.low() + 0.2, "it leaps from its spout, above the water: " + jet);
        assertFalse(kinds("wishing_well").contains(PropMotion.Kind.SPRAY), "a well's water lies still");
        assertTrue(kinds("crystal_cluster").contains(PropMotion.Kind.GLINTS));
        assertTrue(kinds("beehive").contains(PropMotion.Kind.BEES));
    }

    @Test
    void whatHasNothingMovingIsLeftAlone() throws Exception {
        assertFalse(PropMotion.moves(catalog.profile("chair")));
        assertTrue(of("chair").isEmpty());
    }

    @Test
    void pointsCloseTogetherAreOneGroup() {
        List<double[]> pts = List.of(new double[]{0, 0, 1}, new double[]{0.2, 0, 1}, new double[]{5, 5, 1},
                new double[]{5.3, 5, 1});
        assertEquals(2, PropMotion.clusters(pts, 0.9).size());
    }
}
