package com.larsons.game.level;

import com.larsons.game.building.Building;
import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.combat.CombatTuning;
import com.larsons.game.sprite.ScenerySprites;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Shop;
import com.larsons.game.world.Store;
import com.larsons.game.world.World;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The sample town ({@code assets/levels/town}): its buildings, its two stalls to shop at, its pots and barrels. */
class TownLevelTest {

    @Test
    void theTownHasItsBuildingsItsStallsAndWhatBreaks() throws IOException {
        BuildingCatalog.use(BuildingCatalog.load(Path.of("assets", "buildings")));
        Level level = LevelIO.load(Path.of("assets", "levels", "town"));
        World world = new World(new Wardrobe());
        LevelWorld.populate(world, level, ScenerySprites.NONE, CombatTuning.defaults());
        Set<String> kinds = new HashSet<>();
        for (Building b : world.buildings()) kinds.add(b.profile().key());
        for (String k : new String[]{"row_house", "shop_house", "workshop", "clocktower", "foundry", "market_stall",
                "market_stall_awning", "lantern_post"}) {
            assertTrue(kinds.contains(k), k);
        }
        assertEquals(2, world.shops().size());
        assertEquals(9, world.stores().size());
        assertTrue(world.stores().stream().anyMatch(s -> s.kind() == Store.Kind.POT));
        // nothing stands in the way of a stall's counter, a pot or the spawn
        for (Shop s : world.shops()) {
            for (Building b : world.buildings()) {
                if (b.id() == s.id()) continue;
                assertFalse(b.blocks(s.position().x(), s.position().z(), 0.3), s.name() + " behind " + b.profile().key());
            }
        }
        for (Building b : world.buildings()) {
            if (b.profile().furniture()) continue;
            for (Store s : world.stores()) {
                assertFalse(b.blocks(s.position().x(), s.position().z(), s.radius()),
                        s.kind() + " inside " + b.profile().key());
            }
            assertFalse(b.blocks(world.spawnPoint().x(), world.spawnPoint().z(), 0.5), b.profile().key());
        }
    }
}
