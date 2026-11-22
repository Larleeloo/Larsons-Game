package com.larsons.game.scene;

import com.larsons.game.level.ObjectType;
import com.larsons.game.level.ObjectTypes;
import com.larsons.game.level.Rows;
import com.larsons.game.math.Vec3;
import com.larsons.game.world.Item;
import com.larsons.game.world.Spot;
import com.larsons.game.world.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** What a mystery box can stop on: picked in the editor as rows of weapons, at one tier or every tier. */
class MysteryBoxPoolTest {

    private static List<Item> pool(String text) {
        return HordeProps.pool(new Spot(1, "mystery_box", "Box", Vec3.ZERO, 0, Map.of("pool", text)));
    }

    @Test
    void theBoxsWeaponsArePickedAsRowsAtOneTierOrEvery() {
        Rows rows = Rows.pool();
        List<List<String>> r = rows.read().apply("sword:*, longbow:perfect");
        assertEquals(List.of("sword", Rows.EVERY_TIER), r.get(0));
        assertEquals("perfect", r.get(1).get(1));
        assertEquals("sword:*, " + r.get(1).get(0) + ":perfect", rows.write().apply(r));

        int swordTiers = Tier.row("sword").tiers().size();
        List<Item> every = pool("sword:*");
        assertEquals(swordTiers, every.size(), "the sword at each of its tiers");
        assertTrue(every.stream().allMatch(i -> i.def().id().equals("sword")));
        assertEquals(1, pool("longbow:perfect").size(), "one tier: that one");
        assertEquals(swordTiers + 1, pool(rows.write().apply(r)).size());
        assertTrue(pool("").size() > swordTiers, "no rows: every weapon at every tier");

        ObjectType.Prop p = ObjectTypes.get("mystery_box").props().stream()
                .filter(q -> q.key().equals("pool")).findFirst().orElseThrow();
        assertEquals(ObjectType.Prop.Kind.ROWS, p.kind());
    }
}
