package com.larsons.game.level;

import com.larsons.game.building.Building;
import com.larsons.game.building.BuildingCatalog;
import com.larsons.game.building.BuildingLook;
import com.larsons.game.building.BuildingProfile;
import com.larsons.game.level.ObjectType.Category;
import com.larsons.game.level.ObjectType.Prop;
import com.larsons.game.math.Vec3;
import com.larsons.game.world.Spot;

import java.util.List;

/**
 * The kinds of level object a playthrough is made of, each a {@link Spot} she
 * walks up to and uses: the <b>way back to the map</b> (reaching it clears a
 * destination whose rule is "exit"), and the rest as they are added - people
 * to talk to, cauldrons, locked doors, fishing spots, summon stations ...
 * Registered with {@link ObjectTypes} (which calls {@link #registerAll}), so
 * each is placed and its properties edited in Creative Mode like any other.
 */
public final class StoryTypes {

    public static final String WAY_OUT = "wayout", WORKBENCH = "workbench", CAULDRON = "cauldron", LOCK = "lock";

    private StoryTypes() {}

    private static final java.util.Map<String, String> STAND_INS = java.util.Map.of("workbench", "prep_table",
            "signpost", "lantern_post", "summon_station", "tesla_coil", "locked_door", "gatehouse",
            "mystery_box", "storage_chest", "wall_buy", "weapon_rack", "barricade", "crate");

    /**
     * Register a kind of spot: {@code model} (a building's key; null: none)
     * stands where it is placed, and {@code icon} floats over it.
     */
    static void spot(String key, String label, Category category, List<Prop> props, String model, String icon,
                     double radius, double height, int colour) {
        ObjectTypes.register(new ObjectType(key, label, category, props, radius, height, colour, false, (o, b) -> {
            Vec3 at = b.onGround(o);
            String m = o.str("model", model == null ? "none" : model);
            BuildingProfile p = m.equals("none") ? null : BuildingCatalog.get().profile(m);
            // (its own model not made yet: what stands in for it)
            if (p == null && !m.equals("none")) p = BuildingCatalog.get().profile(STAND_INS.getOrDefault(m, "none"));
            // (made as wide and as tall as it is set to: a double door, a low gate)
            p = ObjectTypes.sized(o, p);
            if (p != null) {
                b.world().addBuilding(new Building(o.id(), p, at, o.yaw(), BuildingLook.of(o.props().get("look")),
                        null));
            }
            b.world().addSpot(new Spot(o.id(), key, o.str("name", label), at, o.yaw(), o.props()).icon(icon)
                    .markerHeight(p == null ? 1.6 : Math.max(1.8, height + 0.5)));
        }));
    }

    static void registerAll() {
        spot(WAY_OUT, "Way back to the map", Category.QUESTS,
                List.of(Prop.text("name", "Name", "The road on"),
                        Prop.toggle("clears", "Reaching it clears the destination", true)),
                "signpost", "wayout", 0.6, 2.4, 0x7FE8FF);
        // where things are made: a workbench (the makings, rods, packs, keys, trinkets) and a cauldron (potions, meals)
        spot(WORKBENCH, "Workbench", Category.CRAFT,
                List.of(Prop.text("name", "Name", "Workbench"),
                        Prop.choice("model", "Looks like", "workbench",
                                o -> List.of("workbench", "prep_table", "desk", "alchemy_table", "none"))),
                "workbench", "hammer", 0.8, 1.0, 0xD6AA5C);
        spot(CAULDRON, "Cauldron (brewing)", Category.CRAFT,
                List.of(Prop.text("name", "Name", "Cauldron"),
                        Prop.choice("model", "Looks like", "cauldron",
                                o -> List.of("cauldron", "large_pot", "alchemy_table", "distiller", "none"))),
                "cauldron", "cauldron", 0.6, 1.0, 0x60BE78);
        // a locked door or gate, in the way until she has its key (the skeleton key opens any)
        spot(LOCK, "Locked door", Category.QUESTS,
                List.of(Prop.text("name", "Name", "The door"),
                        Prop.choice("key", "Opened by", "iron_key", o -> com.larsons.game.world.Goods.KEYS.stream()
                                .map(com.larsons.game.world.Goods.Good::id).toList()),
                        Prop.toggle("consumes", "Uses the key up", true),
                        Prop.number("cost", "Or opens for (coins or points; 0: the key only)", 0, 0, 100000, 50),
                        Prop.choice("model", "Looks like", "locked_door",
                                o -> List.of("locked_door", "iron_gate", "none")),
                        Prop.number("width", "Width ×", 1, 0.5, 3, 0.05),
                        Prop.number("height", "Height ×", 1, 0.5, 2, 0.05)),
                "locked_door", "lock", 1.1, 2.4, 0xD8DCE4);
        FolkTypes.registerAll();
        HordeTypes.registerAll();
    }
}
