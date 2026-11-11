package com.larsons.game.level;

import com.larsons.game.level.ObjectType.Category;
import com.larsons.game.level.ObjectType.Prop;
import com.larsons.game.math.Vec3;
import com.larsons.game.world.Goods;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Supplies;
import com.larsons.game.world.Tier;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a horde map is built of, all under the palette's Zombies shelf with
 * the Risen themselves:
 *
 * <ul>
 *   <li><b>Risen spawn</b> ({@link #SPAWN}) - where the horde comes out of
 *       the ground: any kind or one, from a round on, and - named a door -
 *       only once that door is open.</li>
 *   <li><b>Barricade</b> ({@link #BARRICADE}) - boards across a gap. The
 *       Risen that come from the spawns near it come by way of it, and stop
 *       to tear its boards off; she nails them back on (and is paid for
 *       each).</li>
 *   <li><b>Weapon board</b> ({@link #WALL_BUY}) - a weapon chalked on a
 *       board, for a price: hers when she pays; paid again, it is whetted
 *       and fed.</li>
 *   <li><b>Mystery box</b> ({@link #BOX}) - pay, and it turns up a weapon of
 *       any kind and tier, the rarer the less often.</li>
 * </ul>
 *
 * <p>A locked door ({@link StoryTypes#LOCK}) with a price is the horde's
 * door. They all take coins - which a horde game calls points - so they
 * work in any level.
 */
public final class HordeTypes {

    public static final String SPAWN = "horde_spawn", BARRICADE = "barricade", WALL_BUY = "wall_buy", BOX = "mystery_box";
    public static final String ANY = "any", LOWEST = "its first";

    /** The kinds of thing here: the palette lists them first on the Zombies shelf ({@link #entries}). */
    static final List<String> OWN = List.of(SPAWN, BARRICADE, BOX, WALL_BUY);

    private HordeTypes() {}

    /** What a board can sell: every weapon, the ammunition, every potion and scroll. */
    public static List<String> sold() {
        List<String> out = new ArrayList<>();
        for (ItemDef d : ItemDef.ALL) out.add(d.id());
        for (ItemDef d : Supplies.AMMO) out.add(d.id());
        for (Goods.Good g : Goods.ofKind(Goods.Kind.POTION)) out.add(g.id());
        for (Goods.Good g : Goods.ofKind(Goods.Kind.SCROLL)) out.add(g.id());
        return out;
    }

    static List<String> tiers() {
        List<String> out = new ArrayList<>(List.of(LOWEST));
        for (Tier t : Tier.values()) out.add(t.key());
        return out;
    }

    static List<String> kinds() {
        List<String> out = new ArrayList<>(List.of(ANY));
        for (com.larsons.game.actor.Breed b : HordeRules.risen()) out.add(b.key());
        return out;
    }

    static void registerAll() {
        ObjectTypes.register(new ObjectType(SPAWN, "Risen spawn", Category.ZOMBIES,
                List.of(Prop.choice("kind", "What comes", ANY, o -> kinds()),
                        Prop.number("from", "From round", 1, 1, 99, 1),
                        Prop.text("after", "Only once this door is open (its name; blank: always)", "")),
                0.7, 0.3, 0x7CC050, false, (o, b) -> b.world().hordeSpawns().add(new World.HordeSpawn(o.id(),
                        b.onGround(o), o.str("kind", ANY), (int) Math.round(o.num("from", 1)), o.str("after", "")))));

        StoryTypes.spot(BARRICADE, "Barricade", Category.ZOMBIES,
                List.of(Prop.text("name", "Name", "Barricade"),
                        Prop.number("planks", "Boards", 6, 1, 12, 1)),
                "barricade", "hammer", 1.1, 1.65, 0x8A5A30);

        StoryTypes.spot(WALL_BUY, "Weapon board", Category.ZOMBIES,
                List.of(Prop.text("name", "Name (blank: what it sells)", ""),
                        Prop.choice("item", "Sells", "sword", o -> sold()),
                        Prop.choice("tier", "Tier (a weapon's)", LOWEST, o -> tiers()),
                        Prop.number("cost", "Price (coins or points)", 500, 0, 100000, 50),
                        Prop.number("again", "Price to whet and feed it again (0: half)", 0, 0, 100000, 50)),
                "wall_buy", "coins", 1.0, 2.0, 0xD6AA5C);

        StoryTypes.spot(BOX, "Mystery box", Category.ZOMBIES,
                List.of(Prop.text("name", "Name", "Mystery box"),
                        Prop.number("cost", "Price (coins or points)", 950, 0, 100000, 50),
                        Prop.text("pool", "What can come (blank: every weapon)", "")),
                "mystery_box", "box", 0.9, 0.8, 0xB060FF);
    }

    /** The palette's ready-made boards: each weapon at its first tier and its price. */
    static List<ObjectTypes.Entry> entries() {
        List<ObjectTypes.Entry> out = new ArrayList<>();
        for (String key : List.of(SPAWN, BARRICADE, BOX)) {
            ObjectType t = ObjectTypes.get(key);
            Map<String, Object> p = new LinkedHashMap<>();
            for (Prop prop : t.props()) p.put(prop.key(), prop.fallback());
            out.add(new ObjectTypes.Entry(t.label(), Category.ZOMBIES, key, p));
        }
        Object[][] boards = {{"dagger", 250}, {"sword", 500}, {"mace", 600}, {"longbow", 750}, {"crossbow", 900},
                {"battle_axe", 1000}, {"wand", 1200}, {"crystal_staff", 1500}, {"health_potion", 150},
                {"stoneskin_potion", 1500}, {"might_potion", 2000}, {"swiftness_potion", 1500}};
        for (Object[] row : boards) {
            Map<String, Object> p = new LinkedHashMap<>();
            for (Prop prop : ObjectTypes.get(WALL_BUY).props()) p.put(prop.key(), prop.fallback());
            p.put("item", row[0]);
            p.put("cost", row[1]);
            out.add(new ObjectTypes.Entry("Board: " + ObjectTypes.readable((String) row[0]).toLowerCase(),
                    Category.ZOMBIES, WALL_BUY, p));
        }
        Map<String, Object> door = new LinkedHashMap<>();
        for (Prop prop : ObjectTypes.get(StoryTypes.LOCK).props()) door.put(prop.key(), prop.fallback());
        door.put("name", "The gate");
        door.put("cost", 750);
        door.put("model", "iron_gate");
        out.add(3, new ObjectTypes.Entry("Gate that opens for points", Category.ZOMBIES, StoryTypes.LOCK, door));
        return out;
    }

    /** Where a Risen from {@code spawn} goes first: just outside the nearest barricade within 16 m of it, or null. */
    public static Vec3 outside(Vec3 spawn, Vec3 barricade) {
        Vec3 d = spawn.sub(barricade);
        double len = Math.hypot(d.x(), d.z());
        if (len < 1e-6) return barricade;
        return new Vec3(barricade.x() + d.x() / len * 0.95, barricade.y(), barricade.z() + d.z() / len * 0.95);
    }
}
