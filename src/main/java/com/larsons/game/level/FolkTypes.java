package com.larsons.game.level;

import com.larsons.game.actor.Breed;
import com.larsons.game.actor.Schedule;
import com.larsons.game.building.Building;
import com.larsons.game.actor.Folk;
import com.larsons.game.actor.Folks;
import com.larsons.game.actor.Looks;
import com.larsons.game.actor.People;
import com.larsons.game.actor.Serpent;
import com.larsons.game.ambient.AnimalKind;
import com.larsons.game.level.ObjectType.Category;
import com.larsons.game.level.ObjectType.Prop;
import com.larsons.game.math.Vec3;
import com.larsons.game.story.Quest;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The folk a level is peopled with, placed and edited in Creative Mode as
 * any other object:
 *
 * <pre>
 *   foe      an enemy of a {@link Breed}: which weapon (any there is, for the humanoid ones), which
 *            body, how big, which skin, a name, how far it roams, whether it fights on sight
 *   person   someone to talk to: of which people, how dressed, what she says ("Hello.|A second page."),
 *            what can be asked ("The mill=It burned down.; Work?=Ask the smith."), what she sells
 *   quest    what someone asks of her ({@link Quest}): who gives it, what is to be done, in what ring
 *            round the quest's spot, what is said, what it pays
 *   summon   a summoning station: a button that calls foes, beasts, Vérnees or dummies to a named spot
 *   portal   a hole in the sky Vérnees come out of when she comes near
 * </pre>
 */
public final class FolkTypes {

    public static final String FOE = "foe", PERSON = "person", QUEST = "quest", SUMMON = "summon", PORTAL = "portal";
    public static final String EITHER = "either", OWN = "own", NONE = "none", ANY = "any", AUTO = "auto";

    private FolkTypes() {}

    /** What a person can hold: nothing, or anything carried in a hand (and what she holds already). */
    static List<String> held(LevelObject o) {
        List<String> out = new ArrayList<>(List.of(""));
        for (ItemDef d : ItemDef.ALL) if (d.carrySlot() != null) out.add(d.id());
        String cur = o == null ? "" : o.str("weapon", "");
        if (!out.contains(cur)) out.add(cur);
        return out;
    }

    /** The weapons a foe can be handed: its kind's own, none, or any there is. */
    static List<String> weapons() {
        List<String> out = new ArrayList<>(List.of(OWN, NONE));
        for (ItemDef d : ItemDef.ALL) out.add(d.id());
        return out;
    }

    static List<String> bodies() {
        return List.of(EITHER, "feminine", "masculine");
    }

    static List<String> skins(LevelObject o) {
        List<String> out = new ArrayList<>(List.of(ANY));
        Breed b = Breed.byKey(o.str("breed", ""));
        People p = b != null ? b.people() : People.byKey(o.str("people", "human"));
        if (p == People.BORTZAL) out.addAll(List.of("goblin_yellow", "ashy_purple"));
        else if (p != null) out.addAll(p.skins());
        return out;
    }

    /** What a station can call: every breed, the beasts that fight, the Vérnees, the dummies. */
    public static List<String> summonable() {
        List<String> out = new ArrayList<>(Breed.keys());
        out.addAll(List.of(AnimalKind.BOAR.key(), AnimalKind.BUFFALO.key(), AnimalKind.BEAR.key(), World.VERNEES,
                World.FLIER, World.DUMMY));
        return out;
    }

    /**
     * What a quest of {@code o}'s kind can be after: for felling, any foe,
     * each kind of foe and beast, and the foes of the level by name; for
     * talking, the people of the level; for bringing, every item there is.
     * What it has already is among them, whatever it is.
     */
    static List<String> targets(LevelObject o) {
        List<String> out = new ArrayList<>();
        Quest.Kind kind = Quest.Kind.byKey(o.str("kind", Quest.Kind.KILL.key()));
        Level l = ObjectTypes.context();
        switch (kind) {
            case KILL -> {
                out.add("");
                if (l != null) {
                    for (LevelObject f : l.ofType(FOE)) {
                        String n = f.str("name", "").strip();
                        if (!n.isEmpty() && !out.contains(n)) out.add(n);
                    }
                }
                for (String k : Breed.keys()) if (!out.contains(k)) out.add(k);
                for (AnimalKind a : AnimalKind.values()) if (!out.contains(a.key())) out.add(a.key());
                out.add(World.VERNEES);
                out.add("risen");
            }
            case TALK -> out.addAll(ObjectTypes.personNames(null, "target", false, ""));
            case FETCH -> out.addAll(ObjectTypes.itemIds());
            default -> out.add("");
        }
        String cur = o.str("target", "");
        if (!out.contains(cur)) out.add(cur);
        return out;
    }

    /** What a quest's target is called in the editor: "Any foe", "Bandit", "Health potion". */
    public static String targetLabel(LevelObject o, String key) {
        Quest.Kind kind = Quest.Kind.byKey(o.str("kind", Quest.Kind.KILL.key()));
        if (key.isBlank()) return switch (kind) {
            case KILL -> "Any foe";
            case CLEAR, REACH -> "(the ring round this spot)";
            default -> "(none picked)";
        };
        if (kind == Quest.Kind.FETCH) return Rows.itemLabel(key);
        if (kind == Quest.Kind.KILL) {
            Breed b = Breed.byKey(key);
            if (b != null) return "Any " + b.label().toLowerCase(java.util.Locale.ROOT);
            AnimalKind a = AnimalKind.byKey(key);
            if (a != null) return "Any " + a.singular().toLowerCase(java.util.Locale.ROOT);
            if (key.equals("risen")) return "Any of the Risen";
            if (key.equals(World.VERNEES)) return "Any Vérnees";
        }
        return key;
    }

    /**
     * Where {@code f}'s day takes her, found in {@code w} now it is all up:
     * her home - the house picked for her, or the nearest with a door
     * within 90 m - and each spot and building it names.
     */
    static void placeDay(Folk f, LevelObject o, World w) {
        if (f.schedule() == null) return;
        String home = o.str("home", AUTO);
        Building house = null;
        if (home.startsWith(Schedule.BUILDING)) house = building(w, home);
        else if (!home.equals(NONE)) {
            double best = 90;
            for (Building b : w.buildings()) {
                if (b.door() == null || !b.profile().interior() || b.profile().room() != null) continue;
                double d = b.door().horizontalDistance(f.position());
                if (d < best) {
                    best = d;
                    house = b;
                }
            }
        }
        if (house != null) door(f, Schedule.HOME, house);
        for (String name : f.schedule().places()) {
            if (name.startsWith(Schedule.BUILDING)) {
                Building b = building(w, name);
                if (b != null) door(f, name, b);
                continue;
            }
            for (com.larsons.game.world.Terrain.Place pl : w.terrain().places()) {
                if (pl.name().equalsIgnoreCase(name)) {
                    f.place(name, pl.x(), pl.z(), Double.NaN, -1);
                    break;
                }
            }
        }
    }

    private static Building building(World w, String key) {
        try {
            int id = Integer.parseInt(key.substring(Schedule.BUILDING.length()).strip());
            for (Building b : w.buildings()) if (b.id() == id && b.door() != null) return b;
        } catch (NumberFormatException ignored) {
            // not a building's number
        }
        return null;
    }

    /** {@code name} is before {@code b}'s door, facing in. */
    private static void door(Folk f, String name, Building b) {
        Vec3 d = b.door();
        f.place(name, d.x(), d.z(), b.heading() + Math.PI, b.id());
    }

    static Folks.Spec spec(LevelObject o, boolean hostile) {
        String skin = o.str("skin", ANY);
        String dress = o.str("outfit", OWN);
        return new Folks.Spec(o.str("body", EITHER), o.str("weapon", OWN), o.num("size", 0),
                Math.round(o.num("look", 0)), o.str("name", ""), ANY.equals(skin) ? null : skin, hostile,
                Looks.Dress.byKey(dress));
    }

    static void registerAll() {
        ObjectTypes.register(new ObjectType(FOE, "Enemy", Category.FOES,
                List.of(Prop.choice("breed", "Kind", Breed.BANDIT.key(), o -> Breed.keys()),
                        Prop.text("name", "Name (blank: its kind's)", ""),
                        Prop.choice("weapon", "Weapon", OWN, o -> weapons()),
                        Prop.choice("body", "Body", EITHER, o -> bodies()),
                        Prop.number("size", "Size (0: as its kind comes)", 0, 0, 1.8, 0.05),
                        Prop.choice("skin", "Skin", ANY, FolkTypes::skins),
                        Prop.choice("outfit", "Dressed", OWN, o -> Looks.Dress.keys()),
                        Prop.number("look", "Look (0: any)", 0, 0, 9999, 1),
                        Prop.number("roam", "Roams within (m)", 4, 0, 60, 0.5),
                        Prop.toggle("hostile", "Fights on sight", true),
                        Prop.number("health", "Health × ", 1, 0.1, 20, 0.1)),
                0.45, 1.8, 0xE0503A, false, (o, b) -> {
            Breed breed = Breed.byKey(o.str("breed", Breed.BANDIT.key()));
            if (breed == null) return;
            Folk f = b.world().folks().spawn(o.id(), breed, b.onGround(o), o.yaw(), spec(o, o.bool("hostile", true)));
            f.roaming(o.num("roam", 4)).with(o.props());
            double times = o.num("health", 1);
            if (times != 1) f.tougher(times);
        }));

        ObjectTypes.register(new ObjectType(PERSON, "Person", Category.STORY,
                List.of(Prop.text("name", "Name", "Villager"),
                        Prop.choice("people", "People", People.HUMAN.key(), o -> People.playableKeys()),
                        Prop.choice("body", "Body", EITHER, o -> bodies()),
                        Prop.choice("outfit", "Dressed", OWN, o -> Looks.Dress.keys()),
                        Prop.number("look", "Look (0: any)", 0, 0, 9999, 1),
                        Prop.number("size", "Size (0: as her people come)", 0, 0, 1.3, 0.05),
                        Prop.choice("skin", "Skin", ANY, FolkTypes::skins),
                        Prop.choice("weapon", "Holds", "", FolkTypes::held),
                        Prop.number("roam", "Wanders within (m)", 0, 0, 30, 0.5),
                        Prop.text("greeting", "Says (| starts a new page)", "Well met, traveller."),
                        Prop.rows("topics", "Asked about", "", Rows.topics()),
                        Prop.rows("stock", "Sells", "", Rows.stock("Sells nothing")),
                        Prop.toggle("buys", "Buys what she sells (half its worth)", false),
                        Prop.choice("home", "Lives in", AUTO, ObjectTypes::homes),
                        Prop.rows("schedule", "Her day", Schedule.DEFAULT, Rows.schedule()),
                        Prop.toggle("shelter", "Goes in out of storms", true)),
                0.4, 1.8, 0x7FE8FF, false, (o, b) -> {
            People people = People.byKey(o.str("people", People.HUMAN.key()));
            if (people == null) people = People.HUMAN;
            Folks.Spec s = spec(o, false);
            String held = o.str("weapon", "").strip();
            Folk f = b.world().folks().person(o.id(), people, b.onGround(o), o.yaw(),
                    s.weapon(held.isEmpty() ? NONE : held.toLowerCase(java.util.Locale.ROOT).replace(' ', '_')));
            f.roaming(o.num("roam", 0)).with(o.props());
            f.keeping(Schedule.parse(o.str("schedule", Schedule.DEFAULT))).sheltering(o.bool("shelter", true));
            // (where her day takes her: once every building and spot of the level is up)
            b.later(() -> placeDay(f, o, b.world()));
        }));

        ObjectTypes.register(new ObjectType(QUEST, "Quest", Category.QUESTS,
                List.of(Prop.text("title", "Title", "A task"),
                        Prop.choice("giver", "Given by", "Villager", o -> ObjectTypes.personNames(o, "giver", false, "Villager")),
                        Prop.choice("kind", "To do", Quest.Kind.KILL.key(), o -> Quest.Kind.keys()),
                        Prop.choice("target", "What or whom", "", FolkTypes::targets),
                        Prop.number("count", "How many", 1, 1, 99, 1),
                        Prop.number("radius", "Within (m) of this spot (0: anywhere)", 0, 0, 400, 1),
                        Prop.text("offer", "Asking for it", "Would you do something for me?"),
                        Prop.text("remind", "While it is not done", "Not done yet?"),
                        Prop.text("thanks", "When it is done", "Thank you. Take this."),
                        Prop.rows("reward", "Pays", "coins x25", Rows.reward()),
                        Prop.number("xp", "Experience", 50, 0, 5000, 5),
                        Prop.choice("turnin", "Handed in to", "", o -> ObjectTypes.personNames(o, "turnin", true, "")),
                        Prop.choice("needs", "Only after the quest", "", ObjectTypes::questIds),
                        Prop.text(Quest.ID, "Id (blank: one of its own)", "")),
                0.6, 1.2, 0xFFD27A, false,
                (o, b) -> b.world().quests().add(Quest.of(b.levelName(), o.id(), b.onGround(o), o.props()))));

        StoryTypes.spot(SUMMON, "Summoning station", Category.FOES,
                List.of(Prop.text("name", "Name", "Summoning station"),
                        Prop.choice("summons", "Calls", Breed.BANDIT.key(), o -> summonable()),
                        Prop.number("count", "How many", 1, 1, 20, 1),
                        Prop.text("at", "At the spot named (blank: in front of it)", ""),
                        Prop.number("distance", "In front of it by (m)", 6, 1, 60, 0.5),
                        Prop.choice("weapon", "Weapon", OWN, o -> weapons()),
                        Prop.number("size", "Size (0: as they come)", 0, 0, 1.8, 0.05),
                        Prop.toggle("hostile", "They fight on sight", true),
                        Prop.number("cost", "Costs (coins)", 0, 0, 10000, 5),
                        Prop.number("wait", "Seconds before it can be used again", 2, 0, 600, 1)),
                "summon_station", "skull", 0.6, 1.3, 0xB060FF);

        ObjectTypes.register(new ObjectType(PORTAL, "Sky portal (Vérnees)", Category.FOES,
                List.of(Prop.number("count", "How many come out", 2, 0, 30, 1),
                        Prop.number("every", "One every (s)", 6, 0.5, 120, 0.5),
                        Prop.number("height", "Height over the ground (m)", 16, 6, 60, 1),
                        Prop.number("radius", "Width (m)", 2.6, 1, 10, 0.2),
                        Prop.number("range", "Opens when she is within (m)", 30, 0, 300, 1),
                        Prop.choice("colour", "Their colour", ANY, o -> {
                            List<String> out = new ArrayList<>(List.of(ANY));
                            out.addAll(Serpent.Colour.keys());
                            return out;
                        }),
                        Prop.number("size", "Their size (0: as they come)", 0, 0, 2, 0.05),
                        Prop.colour("light", "Its light")),
                1.2, 3, 0xB060FF, false, (o, b) -> {
            Vec3 g = b.onGround(o);
            int rgb = 0xB060FF;
            String light = o.str("light", "");
            if (light.startsWith("#") && light.length() == 7) {
                try {
                    rgb = Integer.parseInt(light.substring(1), 16);
                } catch (NumberFormatException ignored) {
                    // not a colour: its own violet
                }
            }
            b.world().folks().addPortal(new Folks.Portal(o.id(), g.add(0, o.num("height", 16), 0),
                    o.num("radius", 2.6), rgb, (int) Math.round(o.num("count", 2)), o.num("every", 6),
                    o.num("range", 30), Serpent.Colour.byKey(o.str("colour", ANY)), o.num("size", 0)));
        }));
    }

    /** The palette's entries for the folk: one for each breed, each people, each beast a station calls. */
    static List<ObjectTypes.Entry> entries() {
        List<ObjectTypes.Entry> out = new ArrayList<>();
        for (Breed breed : Breed.values()) {
            Map<String, Object> p = defaults(FOE);
            p.put("breed", breed.key());
            out.add(new ObjectTypes.Entry(breed.label(), breed.risen() ? Category.ZOMBIES : Category.FOES, FOE, p));
        }
        for (People people : People.values()) {
            if (!people.playable()) continue;
            Map<String, Object> p = defaults(PERSON);
            p.put("people", people.key());
            p.put("name", people == People.HUMAN ? "Villager" : people.label());
            out.add(new ObjectTypes.Entry("Person: " + people.label(), Category.STORY, PERSON, p));
        }
        Object[][] keepers = {{"Shopkeeper: potions", "Apothecary", "workshop",
                "health potion x6, mana potion x6, stamina potion x6, swiftness potion x2, empty bottle x20"},
                {"Shopkeeper: smith", "Smith", "workshop",
                        "sword, sword:sharper @60, sword:crystal @220, dagger, mace, battle axe, upgrade ingot x6, iron bar x12"},
                {"Shopkeeper: outfitter", "Outfitter", "town",
                        "satchel x1, backpack x1, explorers pack x1, willow rod x2, reed rod x1, twine x20, "
                                + "fish hook x20, leather x10, cloth x10"},
                {"Shopkeeper: curios", "Curio seller", "elegant",
                        "scroll of fury x2, scroll of warding x2, scroll of return x4, crystal ball x1, "
                                + "brass key x1, egg cow x2, egg horse x1, parchment x10, ink x6"}};
        for (Object[] k : keepers) {
            Map<String, Object> p = defaults(PERSON);
            p.put("name", k[1]);
            p.put("outfit", k[2]);
            p.put("stock", k[3]);
            p.put("buys", true);
            p.put("greeting", "Have a look. Everything has its price.");
            out.add(new ObjectTypes.Entry((String) k[0], Category.SHOPS, PERSON, p));
        }
        for (String what : List.of(AnimalKind.BOAR.key(), AnimalKind.BUFFALO.key(), World.VERNEES, World.FLIER,
                Breed.BORTZAL.key(), Breed.DRURINEAN.key(), Breed.HALF_DRURINEAN.key(), Breed.KHORIGALE.key())) {
            Map<String, Object> p = defaults(SUMMON);
            p.put("summons", what);
            p.put("name", "Summon: " + ObjectTypes.readable(what));
            out.add(new ObjectTypes.Entry("Summoning station: " + ObjectTypes.readable(what).toLowerCase(),
                    Category.FOES, SUMMON, p));
        }
        return out;
    }

    private static Map<String, Object> defaults(String type) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (Prop p : ObjectTypes.get(type).props()) props.put(p.key(), p.fallback());
        return props;
    }
}
