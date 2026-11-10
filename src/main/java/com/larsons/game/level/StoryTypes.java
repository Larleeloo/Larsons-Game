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

    public static final String WAY_OUT = "wayout";

    private StoryTypes() {}

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
            if (p != null) {
                b.world().addBuilding(new Building(o.id(), p, at, o.yaw(), BuildingLook.of(o.props().get("look")),
                        null));
            }
            b.world().addSpot(new Spot(o.id(), key, o.str("name", label), at, o.yaw(), o.props()).icon(icon)
                    .markerHeight(p == null ? 1.6 : Math.max(1.8, height + 0.5)));
        }));
    }

    static void registerAll() {
        spot(WAY_OUT, "Way back to the map", Category.STORY,
                List.of(Prop.text("name", "Name", "The road on"),
                        Prop.toggle("clears", "Reaching it clears the destination", true)),
                "signpost", "wayout", 0.6, 2.4, 0x7FE8FF);
    }
}
