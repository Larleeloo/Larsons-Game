package com.larsons.game.scene;

import com.larsons.game.actor.Folk;
import com.larsons.game.actor.Folks;
import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.combat.Target;
import com.larsons.game.core.Game;
import com.larsons.game.input.Action;
import com.larsons.game.input.Input;
import com.larsons.game.level.FolkTypes;
import com.larsons.game.level.StoryTypes;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.story.Quest;
import com.larsons.game.story.Quests;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Buffs;
import com.larsons.game.world.Goods;
import com.larsons.game.world.Inventory;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Recipes;
import com.larsons.game.world.Shop;
import com.larsons.game.world.Spot;
import com.larsons.game.world.Supplies;
import com.larsons.game.world.Terrain;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

/**
 * The story a level tells, as the scene plays it: the people she talks to
 * (what they say, what can be asked, what they sell), the quests they give
 * and pay for, the journal and the tracker; the workbench and the cauldron
 * (the {@link Recipes} made there); the summoning stations; and what the
 * goods in her hand do when she uses them - a potion drunk, a scroll read,
 * an egg hatched, a pack put on, a trinket used.
 *
 * <p>The scene asks it first: {@link #update} while one of its panels is
 * up, {@link #use} for a spot of its own, {@link #useHeld} for what is in
 * her hand; and draws it last ({@link #draw}).
 */
final class Story {

    /** The most slots packs can take her inventory to. */
    static final int MOST_SLOTS = 60;
    private static final float[] QUEST = Theme.rgb(255, 210, 122, 1f), DONE = Theme.rgb(140, 220, 130, 1f);

    private record Option(String label, float[] colour, Runnable does) {}

    private final Game game;
    private final World world;
    private final InventoryPanel inventory;
    private final Quests quests;
    private final Map<Integer, Shop> keepers = new HashMap<>();
    private final Map<String, Double> readyAt = new HashMap<>();
    private final Map<Integer, Double> stationAt = new HashMap<>();
    private final List<World.Kill> felled = new ArrayList<>();

    // talking
    private Folk with;
    private List<String> pages = List.of();
    private int page;
    private List<Option> options = List.of();
    private List<Option> after = List.of();

    // making
    private Recipes.Station station;
    private Recipes.Recipe picked, making;
    private double makeLeft, makeTotal;
    private float scroll;

    private final Fishing fishing;
    private final HordeProps props;
    /** Marks of someone else's to show with the quests' (the horde's gifts lying about). */
    private java.util.function.Supplier<List<World.Mark>> moreMarks;
    private boolean journal;
    private double iconClock;
    private int swigs = -1;

    Story(Game game, World world, InventoryPanel inventory, Map<String, Object> store, String place) {
        this.game = game;
        this.world = world;
        this.inventory = inventory;
        this.quests = new Quests(store == null ? new HashMap<>() : store, world.quests(), place);
        this.fishing = new Fishing(game, world);
        this.props = new HordeProps(game, world, this);
    }

    HordeProps props() { return props; }

    void setMoreMarks(java.util.function.Supplier<List<World.Mark>> marks) {
        moreMarks = marks;
    }

    Quests quests() { return quests; }

    /** Whether one of its panels is up: the game's keys are its own, and she stands still. */
    boolean modal() {
        return with != null || station != null || journal;
    }

    /** What was felled since this was last asked (the horde mode counts them too). */
    List<World.Kill> drainFelled() {
        List<World.Kill> out = List.copyOf(felled);
        felled.clear();
        return out;
    }

    // --- every frame ---------------------------------------------------------------------

    /**
     * A frame: the quests' progress, the news of them, the marks over the
     * people; and, while a panel is up, its keys. Returns whether a panel
     * took the frame (the scene does nothing else with the keys).
     */
    boolean update(double dt, Input in) {
        for (World.Kill k : world.drainKills()) {
            quests.felled(k);
            felled.add(k);
        }
        quests.tick(dt, world);
        for (Quests.News n : quests.drainNews()) {
            game.toast(n.text(), n.state() == Quests.State.DONE ? Theme.OK : Theme.ACCENT);
            Sounds.play(SoundKeys.ui(n.state() == Quests.State.DONE ? "quest_done" : n.state() == Quests.State.READY
                    ? "quest_ready" : "quest_new"));
        }
        iconClock -= dt;
        if (iconClock <= 0) {
            iconClock = 0.4;
            for (Folk f : world.folks().all()) if (f.role() == Folk.Role.PERSON) f.setIcon(iconFor(f));
        }
        // the marked places: where a quest under way is to be done
        world.marks().clear();
        for (Quest q : quests.all()) {
            if (quests.state(q) == Quests.State.ACTIVE && q.radius() > 0 && quests.tracked(q.id())) {
                world.marks().add(new World.Mark(q.at(), "quest", q.radius()));
            }
        }
        if (moreMarks != null) world.marks().addAll(moreMarks.get());
        props.update(dt);
        if (in.pressed(Action.JOURNAL) && with == null && station == null) {
            journal = !journal;
            Sounds.play(SoundKeys.ui(journal ? "open" : "close"));
            in.consume(Action.JOURNAL);
        }
        if (!modal()) {
            fishing.update(dt, in);
            return false;
        }
        if (in.pressed(GLFW_KEY_ESCAPE) || in.pressed(Action.PAUSE)) {
            in.consumeKey(GLFW_KEY_ESCAPE);
            in.consume(Action.PAUSE);
            close();
            return true;
        }
        if (with != null) {
            // she walked off, or was set on
            if (!with.inReach(world.player().ground().add(0, 0, 0)) && world.player().ground()
                    .horizontalDistance(with.position()) > 3.2) {
                close();
                return true;
            }
            with.faceTo(world.player().ground());
            boolean next = in.pressed(Action.USE) || in.pressed(GLFW_KEY_ENTER) || in.pressed(GLFW_KEY_SPACE);
            if (page < pages.size() - 1) {
                if (next) {
                    page++;
                    Sounds.play(SoundKeys.ui("click"), 0.5);
                }
            } else {
                for (int i = 0; i < Math.min(9, options.size()); i++) {
                    if (in.pressed(GLFW_KEY_1 + i)) {
                        choose(options.get(i));
                        break;
                    }
                }
                if (next && options.size() == 1) choose(options.get(0));
            }
            in.consume(Action.USE);
        } else if (station != null) {
            if (making != null) {
                makeLeft -= dt;
                if (makeLeft <= 0) finishMaking();
            }
            if (in.pressed(Action.USE)) {
                in.consume(Action.USE);
                close();
            }
        }
        return true;
    }

    void close() {
        if (with != null) with.setTalking(false);
        if (with != null || station != null || journal) Sounds.play(SoundKeys.ui("close"), 0.6);
        with = null;
        station = null;
        making = null;
        journal = false;
    }

    /** The mark over {@code f}: a task done to hand in, a task to give, something to sell - or none. */
    private String iconFor(Folk f) {
        String name = f.name();
        Inventory inv = world.player().inventory();
        for (Quest q : quests.all()) {
            if (q.turnIn().equalsIgnoreCase(name) && quests.canHandIn(q, inv)) return "question";
        }
        for (Quest q : quests.all()) {
            if (q.kind() == Quest.Kind.TALK && quests.state(q) == Quests.State.ACTIVE
                    && q.target().equalsIgnoreCase(name)) return "talk";
        }
        for (Quest q : quests.all()) if (q.giver().equalsIgnoreCase(name) && quests.offered(q)) return "exclaim";
        return f.str("stock", "").isBlank() ? null : "coins";
    }

    // --- talking -------------------------------------------------------------------------

    /** The person she could talk to now, or null. */
    Folk person() {
        return modal() ? null : world.reachablePerson();
    }

    void talk(Folk f) {
        if (f == null) return;
        with = f;
        f.setTalking(true);
        f.faceTo(world.player().ground());
        Vec3 d = f.position().sub(world.player().ground());
        world.player().turnTo(SpriteView.headingOf(d.x(), d.z()));
        Sounds.play(SoundKeys.ui("talk"));
        quests.talked(f.name());
        say(f.str("greeting", "..."), menu());
    }

    private void say(String text, List<Option> then) {
        List<String> p = new ArrayList<>();
        for (String s : (text == null || text.isBlank() ? "..." : text).split("\\|")) if (!s.isBlank()) p.add(s.strip());
        pages = p.isEmpty() ? List.of("...") : p;
        page = 0;
        options = then;
    }

    private void choose(Option o) {
        Sounds.play(SoundKeys.ui("click"), 0.6);
        o.does().run();
    }

    /** What can be said to whoever she is talking to. */
    private List<Option> menu() {
        List<Option> out = new ArrayList<>();
        if (with == null) return out;
        String name = with.name();
        Inventory inv = world.player().inventory();
        for (Quest q : quests.all()) {
            if (q.turnIn().equalsIgnoreCase(name) && quests.canHandIn(q, inv)) {
                out.add(new Option(q.title() + "  (done)", DONE, () -> handIn(q)));
            }
        }
        for (Quest q : quests.all()) {
            if (q.giver().equalsIgnoreCase(name) && quests.offered(q)) {
                out.add(new Option(q.title() + "  (a task)", QUEST, () -> offer(q)));
            } else if (q.turnIn().equalsIgnoreCase(name) && quests.state(q) == Quests.State.ACTIVE
                    && !quests.canHandIn(q, inv)) {
                out.add(new Option(q.title() + "  (under way)", Theme.HINT,
                        () -> say((q.remind().isBlank() ? "Not done yet?" : q.remind()) + "|" + quests.goal(q) + ".",
                                menu())));
            }
        }
        for (String[] t : topics(with.str("topics", ""))) {
            out.add(new Option(t[0], Theme.ITEM, () -> say(t[1], menu())));
        }
        if (!with.str("stock", "").isBlank()) out.add(new Option("Trade", Theme.rgb(240, 192, 48, 1f), this::trade));
        out.add(new Option("Goodbye", Theme.ITEM, this::close));
        return out;
    }

    /** "The mill=It burned down.; Work?=Ask the smith." as pairs. */
    static List<String[]> topics(String text) {
        List<String[]> out = new ArrayList<>();
        if (text == null) return out;
        for (String part : text.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0 || eq >= part.length() - 1) continue;
            out.add(new String[]{part.substring(0, eq).strip(), part.substring(eq + 1).strip()});
        }
        return out;
    }

    private void offer(Quest q) {
        String pays = Quests.rewardText(q.reward());
        String xp = q.xp() > 0 ? (pays.isEmpty() ? "" : ", ") + q.xp() + " experience" : "";
        say(q.offer() + "|" + quests.goal(q) + "." + (pays.isEmpty() && xp.isEmpty() ? "" : "  It pays " + pays + xp + "."),
                List.of(new Option("I'll do it", DONE, () -> {
                    quests.accept(q);
                    say("Good. Come back when it is done.", menu());
                }), new Option("Not now", Theme.ITEM, () -> say(with.str("greeting", "..."), menu()))));
    }

    private void handIn(Quest q) {
        Player p = world.player();
        List<Map.Entry<Item, Integer>> pay = quests.handIn(q, p.inventory());
        for (Map.Entry<Item, Integer> e : pay) give(e.getKey(), e.getValue());
        if (q.xp() > 0) world.earn(q.xp());
        String pays = Quests.rewardText(q.reward());
        say((q.thanks().isBlank() ? "Thank you." : q.thanks())
                + (pays.isEmpty() ? "" : "|You are given " + pays + "."), menu());
    }

    /** {@code n} of {@code item} to her: coins to her purse, the rest to her inventory - or at her feet. */
    void give(Item item, int n) {
        Player p = world.player();
        if (item.def() == Supplies.COINS) {
            p.addCoins(n);
            return;
        }
        if (item.def().stack() <= 1 && !item.def().isWearable() && !item.def().isTack()) {
            // a weapon, a rod, a trinket: to her hotbar if there is room there, as one picked up goes
            for (int i = 0; i < n; i++) {
                if (p.inventory().add(item) < 0) world.spawn(item, p.ground(), 1);
            }
            return;
        }
        int left = p.inventory().put(item, n, true);
        if (left > 0) world.spawn(item, p.ground(), left);
    }

    private void trade() {
        Folk f = with;
        Shop shop = keepers.computeIfAbsent(f.id(), id -> new Shop(id, f.name(), f.position(), f.heading(),
                f.str("stock", ""), !"false".equalsIgnoreCase(String.valueOf(f.props().get("buys")))));
        close();
        inventory.showWares(shop);
    }

    // --- spots ---------------------------------------------------------------------------

    /** E at {@code s}: whether it was one of the story's. */
    boolean use(Spot s) {
        switch (s.kind()) {
            case FolkTypes.SUMMON -> summon(s);
            case StoryTypes.WORKBENCH -> open(Recipes.Station.BENCH);
            case StoryTypes.CAULDRON -> open(Recipes.Station.CAULDRON);
            case StoryTypes.LOCK -> unlock(s);
            default -> {
                return props.use(s);
            }
        }
        return true;
    }

    /** What a summoning station calls what it summons: "Vérnees", "wild boar", "bortzal". */
    static String summoned(String what) {
        if (World.VERNEES.equals(what)) return "Vérnees";
        if (World.FLIER.equals(what)) return "flying dummy";
        if (World.DUMMY.equals(what)) return "strike dummy";
        com.larsons.game.ambient.AnimalKind k = com.larsons.game.ambient.AnimalKind.byKey(what);
        if (k != null) return k.label().toLowerCase(java.util.Locale.ROOT);
        return com.larsons.game.level.ObjectTypes.readable(what);
    }

    /** "The east gate" as it reads in the middle of a sentence: "the east gate". */
    static String inSentence(String name) {
        return name.startsWith("The ") ? "the " + name.substring(4) : name;
    }

    /** What the prompt says E does at {@code s}, or null when it is not one of the story's. */
    String text(Spot s) {
        return switch (s.kind()) {
            case FolkTypes.SUMMON -> {
                int cost = (int) s.num("cost", 0);
                int n = (int) s.num("count", 1);
                yield "Summon " + (n > 1 ? n + " × " : "") + summoned(s.str("summons", "?"))
                        + (cost > 0 ? "  (" + cost + " coins)" : "");
            }
            case StoryTypes.WORKBENCH -> "Use the workbench";
            case StoryTypes.CAULDRON -> "Brew at the cauldron";
            case StoryTypes.LOCK -> {
                Goods.Good key = Goods.get(s.str("key", "iron_key"));
                int cost = (int) s.num("cost", 0);
                if (cost > 0) yield "Open " + inSentence(s.name()) + "  (" + cost + " " + purse + ")";
                yield "Unlock " + s.name() + "  (" + (key == null ? "a key" : key.def().name().toLowerCase()) + ")";
            }
            default -> props.text(s);
        };
    }

    private void summon(Spot s) {
        double now = world.time();
        if (now < stationAt.getOrDefault(s.id(), 0.0)) {
            Sounds.play(SoundKeys.ui("error"), 0.6);
            return;
        }
        int cost = (int) s.num("cost", 0);
        Player p = world.player();
        if (cost > 0 && !p.spend(cost)) {
            Sounds.play(SoundKeys.ui("error"), 0.6);
            game.toast("Not enough coins (" + cost + ")", Theme.WARNING);
            return;
        }
        Vec3 at = null;
        String named = s.str("at", "").strip();
        if (!named.isEmpty()) {
            for (Terrain.Place place : world.terrain().places()) {
                if (place.name().equalsIgnoreCase(named)) at = new Vec3(place.x(), 0, place.z());
            }
        }
        if (at == null) at = s.position().add(SpriteView.headingVector(s.heading()).scale(s.num("distance", 6)));
        Folks.Spec spec = new Folks.Spec(FolkTypes.EITHER, s.str("weapon", FolkTypes.OWN), s.num("size", 0), 0, null,
                null, s.bool("hostile", true), null);
        int came = world.summon(s.str("summons", "bandit"), (int) Math.round(s.num("count", 1)), at,
                s.heading() + Math.PI, spec);
        stationAt.put(s.id(), now + s.num("wait", 2));
        if (came == 0) game.toast("Nothing answers", Theme.HINT);
        else Sounds.play(SoundKeys.ui("summon"));
    }

    private void unlock(Spot s) {
        Inventory inv = world.player().inventory();
        String want = s.str("key", "iron_key");
        boolean master = Recipes.count(inv, Goods.SKELETON_KEY.id()) > 0;
        int cost = (int) s.num("cost", 0);
        if (cost > 0 && !master && Recipes.count(inv, want) == 0) {
            // a door that opens for a price (the horde's, for points)
            if (!world.player().spend(cost)) {
                Sounds.play(SoundKeys.world("locked"));
                game.toast("It opens for " + cost + " " + purse(), Theme.WARNING);
                return;
            }
            Sounds.play(SoundKeys.world("unlock"));
            world.unlock(s);
            opened.add(s.name().strip().toLowerCase(java.util.Locale.ROOT));
            game.toast(s.name() + " opened", Theme.OK);
            return;
        }
        if (!master && Recipes.count(inv, want) == 0) {
            Goods.Good key = Goods.get(want);
            Sounds.play(SoundKeys.world("locked"));
            game.toast("Locked. It wants " + (key == null ? "a key" : "the " + key.def().name().toLowerCase()),
                    Theme.WARNING);
            return;
        }
        if (!master && s.bool("consumes", true)) Recipes.take(inv, want, 1);
        Sounds.play(SoundKeys.world("unlock"));
        world.unlock(s);
        opened.add(s.name().strip().toLowerCase(java.util.Locale.ROOT));
        game.toast(s.name() + " unlocked", Theme.OK);
    }

    private final java.util.Set<String> opened = new java.util.HashSet<>();

    /** Whether the door or gate called {@code name} has been opened this visit. */
    boolean opened(String name) {
        return name != null && opened.contains(name.strip().toLowerCase(java.util.Locale.ROOT));
    }

    /** What her purse is counted in: coins - points, in a horde game. */
    private String purse = "coins";

    String purse() { return purse; }

    void setPurse(String word) {
        purse = word;
    }

    private void open(Recipes.Station st) {
        station = st;
        picked = null;
        making = null;
        scroll = 0;
        Sounds.play(SoundKeys.ui("open"));
    }

    private void finishMaking() {
        Recipes.Recipe r = making;
        making = null;
        List<Item> over = Recipes.make(r, world.player().inventory());
        if (over == null) return;
        for (Item i : over) world.spawn(i, world.player().ground());
        Sounds.play(SoundKeys.ui(r.station() == Recipes.Station.CAULDRON ? "brewed" : "crafted"));
        game.toast((r.station() == Recipes.Station.CAULDRON ? "Brewed " : "Made ")
                + (r.makes() > 1 ? r.makes() + " × " : "") + r.name(), Theme.OK);
    }

    // --- what is in her hand ---------------------------------------------------------------

    /** Whether what she holds is used with a click rather than swung. */
    boolean usesHeld() {
        Goods.Good g = Goods.of(world.player().inventory().held());
        return g != null && (g.usable() || g.kind() == Goods.Kind.ROD);
    }

    /** For a scripted check: fishing done by itself (cast, strike, reel) while {@code on}. */
    void autoFish(boolean on) {
        fishing.setAuto(on);
    }

    /** Use what she holds: a potion drunk, a scroll read, an egg hatched, a pack put on, a trinket used. */
    void useHeld() {
        Player p = world.player();
        Item held = p.inventory().held();
        Goods.Good g = Goods.of(held);
        if (g == null || !g.usable()) return;
        Vec3 at = p.ground();
        switch (g.kind()) {
            case POTION, SCROLL -> {
                if (!quaff(g)) return;
                world.useUpHeld();
                world.combat().sound(g.kind() == Goods.Kind.POTION ? "item/potion_drink" : "item/scroll_read", at);
                world.combat().say(g.about(), g.rarity().rgb(), p.feet(), 2.2, false);
            }
            case EGG -> {
                if ("dragon".equals(g.arg())) {
                    game.toast("The egg is warm, and something turns inside it. Nothing hatches - yet.", Theme.HINT);
                    Sounds.play(SoundKeys.ui("error"), 0.4);
                    return;
                }
                Vec3 front = at.add(SpriteView.headingVector(p.heading()).scale(2.2));
                boolean beast = com.larsons.game.ambient.AnimalKind.byKey(g.arg()) != null;
                Folks.Spec spec = new Folks.Spec(FolkTypes.EITHER, FolkTypes.OWN, 0, 0, null, null, !beast, null);
                if (world.summon(g.arg(), 1, front, p.heading() + Math.PI, spec) == 0) {
                    game.toast("Nothing hatches here", Theme.HINT);
                    return;
                }
                world.useUpHeld();
                world.combat().sound("item/egg_hatch", front);
            }
            case PACK -> {
                int more = Integer.parseInt(g.arg()), size = p.inventory().size();
                if (size >= MOST_SLOTS) {
                    game.toast("You can carry no more than this", Theme.HINT);
                    return;
                }
                world.useUpHeld();
                p.inventory().resize(Math.min(MOST_SLOTS, size + more));
                Sounds.play(SoundKeys.ui("equip"));
                game.toast("Inventory: " + p.inventory().size() + " slots", Theme.OK);
            }
            case TRINKET -> trinket(g);
            default -> { }
        }
    }

    /** Drink or read {@code g}; false when it would do nothing just now (she is full of all it gives). */
    private boolean quaff(Goods.Good g) {
        Player p = world.player();
        boolean only = g.helps().isEmpty() && g.arg() == null;
        double h = Math.min(g.health(), Player.MAX_HEALTH - p.health());
        double s = Math.min(g.stamina(), Player.MAX_STAMINA - p.stamina());
        double m = Math.min(g.mana(), world.combat().maxMana() - world.combat().mana());
        if (only && h <= 0 && s <= 0 && m <= 0) {
            Sounds.play(SoundKeys.ui("error"), 0.6);
            world.combat().say("No need", 0xC8D2DC, p.feet(), 2.1, true);
            return false;
        }
        if (h > 0 && p.health() > 0) p.setHealth(p.health() + h);
        if (s > 0) p.setStamina(p.stamina() + s);
        if (m > 0) world.combat().setMana(world.combat().mana() + m);
        for (Goods.Help help : g.helps()) {
            p.buffs().add(new Buffs.Active(g.def().name(), help.kind(), help.value(), help.seconds(), help.charges(),
                    null));
        }
        if (g.arg() != null) {
            Vec3 at = p.ground();
            switch (g.arg()) {
                case "frost" -> {
                    for (Target t : world.foes()) if (t.position().distance(at) <= 14) t.freeze(8);
                    world.combat().sound("combat/freeze", at);
                }
                case "storm" -> {
                    for (Target t : world.foes()) {
                        if (t.position().distance(at) > 16) continue;
                        world.strike(t, 60);
                    }
                    world.combat().sound("item/storm", at);
                }
                case "return" -> {
                    p.setPosition(world.spawnPoint());
                    world.combat().sound("item/return", world.spawnPoint());
                }
                default -> { }
            }
        }
        return true;
    }

    private void trinket(Goods.Good g) {
        Player p = world.player();
        double now = world.time();
        if (g == Goods.DRINKING_HORN || g == Goods.GILDED_HORN) {
            int most = g == Goods.GILDED_HORN ? 5 : 3;
            if (swigs < 0) swigs = most;
            Vec3 at = p.ground();
            boolean water = false;
            for (double[] o : new double[][]{{0, 0}, {1.5, 0}, {-1.5, 0}, {0, 1.5}, {0, -1.5}}) {
                double x = at.x() + o[0], z = at.z() + o[1];
                water |= world.water().depth(x, z, world.terrain().height(x, z)) > 0.05;
            }
            if (swigs < most && water) {
                swigs = most;
                world.combat().say("Horn filled", 0x6FB8FF, p.feet(), 2.1, true);
                world.combat().sound("item/horn_fill", at);
                return;
            }
            if (swigs <= 0) {
                Sounds.play(SoundKeys.ui("error"), 0.6);
                world.combat().say("Empty - fill it at any water", 0xC8D2DC, p.feet(), 2.1, true);
                return;
            }
            swigs--;
            if (p.health() > 0) p.setHealth(Math.min(Player.MAX_HEALTH, p.health() + g.health()));
            p.setStamina(Math.min(Player.MAX_STAMINA, p.stamina() + g.stamina()));
            world.combat().say("+" + (int) g.stamina() + " stamina  (" + swigs + " left)", 0x4AC850, p.feet(), 2.1,
                    false);
            world.combat().sound("item/horn_drink", at);
            return;
        }
        double ready = readyAt.getOrDefault(g.id(), 0.0);
        if (now < ready) {
            Sounds.play(SoundKeys.ui("error"), 0.6);
            world.combat().say("Ready in " + (int) Math.ceil(ready - now) + " s", 0xC8D2DC, p.feet(), 2.1, true);
            return;
        }
        if (g == Goods.CRYSTAL_BALL) {
            int foes = world.foes().size();
            world.reveal(60);
            readyAt.put(g.id(), now + 30);
            world.combat().sound("item/crystal_ball", p.ground());
            game.toast(foes == 0 ? "The crystal shows no foe about" : "The crystal shows " + foes
                    + (foes == 1 ? " foe" : " foes") + " about", Theme.ACCENT);
        } else if (g == Goods.CRYSTAL_SHIELD) {
            p.buffs().add(new Buffs.Active(g.def().name(), Buffs.Kind.WARD, 80, 0, 1, null));
            readyAt.put(g.id(), now + 60);
            world.combat().sound("item/crystal_shield", p.ground());
            world.combat().say("Warded: 80", 0x7FE8FF, p.feet(), 2.2, false);
        }
    }

    /** A scripted run's hand on the panels: "say 2" picks the second answer (or turns the page). */
    void script(String what, String arg) {
        switch (what) {
            case "say" -> {
                if (with == null) return;
                if (page < pages.size() - 1) page++;
                else {
                    int i = arg.isBlank() ? 0 : Integer.parseInt(arg) - 1;
                    if (i >= 0 && i < options.size()) choose(options.get(i));
                }
            }
            case "station" -> open(arg.startsWith("c") ? Recipes.Station.CAULDRON : Recipes.Station.BENCH);
            case "journal" -> journal = !journal;
            default -> { }
        }
    }

    // --- drawing -------------------------------------------------------------------------

    void draw(Ui ui, boolean hud) {
        if (hud && !modal() && game.settings().missionTracker) tracker(ui);
        if (!modal()) fishing.draw(ui);
        if (with != null) drawTalk(ui);
        else if (station != null) drawStation(ui);
        else if (journal) drawJournal(ui);
    }

    /** The quests under way, top right: each its title and what is still to do. */
    private void tracker(Ui ui) {
        List<Quest> list = quests.tracked();
        if (list.isEmpty()) return;
        float w = 300, x = ui.width() - w - 14, y = 96;
        for (Quest q : list.subList(0, Math.min(4, list.size()))) {
            boolean ready = quests.state(q) == Quests.State.READY;
            String goal = ready ? "Done - go back to " + (q.turnIn().isBlank() ? "whoever asked" : q.turnIn())
                    : quests.goal(q);
            float gh = ui.paragraphHeight(ui.small, goal, w - 44);
            float h = 10 + ui.body.lineHeight() + gh + 8;
            ui.plate(x, y, w, h, ready ? DONE : Theme.BRASS);
            float[] uv = com.larsons.game.ui.CardArt.iconUv(ready ? "question" : "quest");
            if (uv != null) ui.image(com.larsons.game.ui.CardArt.iconTexture(), x + 8, y + 9, 24, 24, uv[0], uv[1],
                    uv[2], uv[3], new float[]{1, 1, 1, 1});
            ui.text(ui.body, Ui.fit(ui.body, q.title(), w - 48), x + 38, y + 6, ready ? DONE : QUEST);
            ui.paragraph(ui.small, goal, x + 38, y + 8 + ui.body.lineHeight(), w - 44, Theme.ITEM);
            y += h + 6;
        }
    }

    private void drawTalk(Ui ui) {
        float w = Math.min(860, ui.width() - 40), x = (ui.width() - w) / 2;
        String text = pages.get(Math.min(page, pages.size() - 1));
        boolean last = page >= pages.size() - 1;
        float textH = ui.paragraphHeight(ui.body, text, w - 56);
        int n = last ? options.size() : 0;
        float optH = 34, h = 58 + Math.max(textH, 44) + (last ? n * (optH + 6) + 8 : 30);
        float y = ui.height() - h - 124;
        ui.rect(0, 0, ui.width(), ui.height(), Theme.withAlpha(Theme.SOOT, 0.25f));
        ui.panel(x, y, w, h);
        // her name on a plate let into the frame
        String name = with.name();
        float nw = ui.large.width(name) + 36;
        ui.plate(x + 22, y - 16, nw, 36, Theme.BRASS_LIGHT);
        ui.text(ui.large, name, x + 40, y - 16 + (36 - ui.large.lineHeight()) / 2, Theme.TITLE);
        String people = with.people().label();
        ui.text(ui.small, people, x + 30 + nw, y + 2, Theme.HINT);
        ui.paragraph(ui.body, text, x + 28, y + 34, w - 56, Theme.ITEM);
        float oy = y + 44 + Math.max(textH, 44);
        if (!last) {
            String more = "(" + ui.input().label(Action.USE) + ")  more…   " + (page + 1) + "/" + pages.size();
            ui.text(ui.small, more, x + w - 28 - ui.small.width(more), oy, Theme.HINT);
            if (ui.clicked(x, y, w, h)) page++;
            return;
        }
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            float bx = x + 28, bw = w - 56;
            boolean hot = ui.hover(bx, oy, bw, optH);
            ui.rect(bx, oy, bw, optH, hot ? Theme.BUTTON_HOVER : Theme.BUTTON);
            ui.outline(bx, oy, bw, optH, 1, hot ? Theme.BRASS_LIGHT : Theme.BRASS_DARK);
            ui.text(ui.small, String.valueOf(i + 1), bx + 10, oy + (optH - ui.small.lineHeight()) / 2, Theme.HINT);
            ui.text(ui.body, Ui.fit(ui.body, o.label(), bw - 50), bx + 34, oy + (optH - ui.body.lineHeight()) / 2,
                    o.colour());
            if (ui.clicked(bx, oy, bw, optH)) {
                choose(o);
                return;
            }
            oy += optH + 6;
        }
    }

    private void drawStation(Ui ui) {
        boolean brew = station == Recipes.Station.CAULDRON;
        Inventory inv = world.player().inventory();
        float w = Math.min(980, ui.width() - 40), h = Math.min(600, ui.height() - 60);
        float x = (ui.width() - w) / 2, y = (ui.height() - h) / 2;
        ui.scrim();
        ui.panel(x, y, w, h);
        ui.textCentered(ui.large, brew ? "The Cauldron" : "The Workbench", x + w / 2, y + 14, Theme.TITLE);
        ui.textCentered(ui.small, brew ? "Potions want an empty bottle and what grows, swims and runs about."
                : "What the hunt, the fields and the forge give you, made into what you need.", x + w / 2, y + 48,
                Theme.HINT);
        List<Recipes.Recipe> list = Recipes.at(station);
        if (picked == null && !list.isEmpty()) {
            picked = list.get(0);
            for (Recipes.Recipe r : list) {
                if (Recipes.can(r, inv)) {
                    picked = r;
                    break;
                }
            }
        }
        // the recipes, down the left: those she can make lit
        float lx = x + 20, ly = y + 78, lw = w * 0.44f, lh = h - 98, row = 30;
        ui.rect(lx, ly, lw, lh, Theme.withAlpha(Theme.SOOT, 0.35f));
        ui.outline(lx, ly, lw, lh, 1, Theme.BRASS_DARK);
        float most = Math.max(0, list.size() * row - lh + 8);
        if (ui.hover(lx, ly, lw, lh)) scroll -= (float) ui.input().scroll() * row * 2;
        scroll = Math.max(0, Math.min(most, scroll));
        ui.clip(lx, ly, lw, lh);
        float ry = ly + 4 - scroll;
        for (Recipes.Recipe r : list) {
            if (ry > ly - row && ry < ly + lh) {
                Item out = r.item();
                boolean can = Recipes.can(r, inv), sel = r == picked, hot = ui.hover(lx + 2, ry, lw - 4, row - 2);
                if (sel || hot) ui.rect(lx + 2, ry, lw - 4, row - 2, sel ? Theme.BUTTON_HOVER : Theme.BUTTON);
                float[] c = out == null ? Theme.ITEM : out.rarity().colour(can ? 1f : 0.45f);
                ui.rect(lx + 8, ry + 6, 5, row - 14, c);
                ui.text(ui.body, Ui.fit(ui.body, (r.makes() > 1 ? r.makes() + " × " : "") + r.name(), lw - 80), lx + 22,
                        ry + (row - 2 - ui.body.lineHeight()) / 2, c);
                int times = Recipes.times(r, inv);
                if (times > 0) {
                    String t = "×" + times;
                    ui.text(ui.small, t, lx + lw - 14 - ui.small.width(t), ry + (row - 2 - ui.small.lineHeight()) / 2,
                            Theme.OK);
                }
                if (making == null && ui.clicked(lx + 2, ry, lw - 4, row - 2)) {
                    picked = r;
                    Sounds.play(SoundKeys.ui("click"), 0.5);
                }
            }
            ry += row;
        }
        ui.unclip();
        // the one picked, on the right: what it is, what it wants, the button
        float px = lx + lw + 18, pw = x + w - 20 - px, py = ly;
        if (picked == null) return;
        Item out = picked.item();
        ui.parchment(px, py, pw, lh);
        float[] ink = Theme.INK;
        ui.text(ui.large, Ui.fit(ui.large, picked.name(), pw - 40), px + 20, py + 16, ink);
        float ty = py + 20 + ui.large.lineHeight();
        if (out != null) {
            Goods.Good good = Goods.of(out);
            String kind = out.rarity().label() + (good != null ? " " + good.kind().label().toLowerCase() : "");
            ui.text(ui.small, kind, px + 20, ty, Theme.INK_FADED);
            ty += ui.small.lineHeight() + 6;
            String about = good != null ? good.about() : out.def().isFood() && out.def().edible()
                    ? out.def().food().effect() : "";
            if (!about.isBlank()) ty += ui.paragraph(ui.body, about, px + 20, ty, pw - 40, ink) + 8;
        }
        ui.rect(px + 20, ty, pw - 40, 1, Theme.INK_FADED);
        ty += 10;
        ui.text(ui.small, brew ? "Into the pot:" : "It takes:", px + 20, ty, Theme.INK_FADED);
        ty += ui.small.lineHeight() + 6;
        for (Recipes.Part p : picked.needs()) {
            int have = Recipes.count(inv, p.item());
            boolean ok = have >= p.count();
            ui.text(ui.body, p.count() + " × " + p.name(), px + 28, ty, ok ? ink : Theme.rgb(150, 40, 30, 1f));
            String hv = "have " + have;
            ui.text(ui.small, hv, px + pw - 24 - ui.small.width(hv), ty + 3, ok ? Theme.rgb(40, 110, 50, 1f)
                    : Theme.rgb(150, 40, 30, 1f));
            ty += ui.body.lineHeight() + 4;
        }
        float by = py + lh - 62, bw = pw - 40;
        if (making != null) {
            // the pot bubbling, the bench at work
            double done = 1 - makeLeft / makeTotal;
            ui.bar(px + 20, by + 8, bw, 22, done, brew ? Theme.rgb(96, 190, 120, 1f) : Theme.BRASS);
            ui.textCentered(ui.small, brew ? "Bubbling…" : "Working…", px + pw / 2, by + 34, Theme.INK_FADED);
            if (brew) {
                for (int i = 0; i < 7; i++) {
                    double t = world.time() * (1.1 + i * 0.13) + i * 1.7, f = t - Math.floor(t);
                    float bx = px + 30 + (float) ((i * 0.147 + 0.07) % 1 * (bw - 20));
                    ui.disc(bx, by - (float) (f * 46), 3 + (i % 3), Theme.withAlpha(Theme.rgb(120, 200, 140, 1f),
                            (float) (0.7 * (1 - f))));
                }
            }
            return;
        }
        boolean can = Recipes.can(picked, inv);
        if (ui.button(station.verb() + (can ? "" : "  (something is missing)"), px + 20, by, bw, 44, false, can)) {
            making = picked;
            makeTotal = makeLeft = picked.seconds();
            Sounds.play(SoundKeys.ui(brew ? "brew" : "craft"));
        }
    }

    /** Open the missions (or close them): from the inventory's button, as J does. */
    void toggleMissions() {
        journal = !journal;
        Sounds.play(SoundKeys.ui(journal ? "open" : "close"));
    }

    /** Which of her missions are shown: those under way, or those done. */
    private boolean showDone;
    private float journalScroll;
    /** Seconds the note that no more can be followed stays up. */
    private double fullNote;

    /**
     * Her missions (J, or the inventory's Missions): those under way and
     * those done, each what it asks, for whom, where and what it pays - and
     * those under way to follow or not: up to {@link Quests#MOST_TRACKED}
     * followed at once, shown at the top right with their places ringed on
     * the ground.
     */
    private void drawJournal(Ui ui) {
        float w = Math.min(780, ui.width() - 40), h = Math.min(600, ui.height() - 60);
        float x = (ui.width() - w) / 2, y = (ui.height() - h) / 2;
        ui.scrim();
        ui.panel(x, y, w, h);
        ui.textCentered(ui.large, "Missions", x + w / 2, y + 14, Theme.TITLE);
        List<Quests.Entry> all = quests.journal();
        int going = 0, done = 0;
        for (Quests.Entry e : all) {
            if (e.state() == Quests.State.DONE) done++;
            else going++;
        }
        float tw = Math.min(190, (w - 60) / 2);
        if (ui.button("Under way (" + going + ")", x + w / 2 - tw - 4, y + 52, tw, 30, !showDone, true)) {
            showDone = false;
            journalScroll = 0;
        }
        if (ui.button("Done (" + done + ")", x + w / 2 + 4, y + 52, tw, 30, showDone, true)) {
            showDone = true;
            journalScroll = 0;
        }
        float top = y + 92, bottom = y + h - 48, lx = x + 18, lw = w - 36;
        ui.parchment(lx, top, lw, bottom - top);
        List<Quests.Entry> list = new ArrayList<>();
        for (Quests.Entry e : all) if ((e.state() == Quests.State.DONE) == showDone) list.add(e);
        String foot = showDone ? "The missions you have seen through." : "Followed missions show at the top right"
                + (game.settings().missionTracker ? "" : " (turned off in Settings)") + ", their places ringed on "
                + "the ground - up to " + Quests.MOST_TRACKED + " at a time.";
        fullNote = Math.max(0, fullNote - 1 / 60.0);
        if (fullNote > 0) foot = "You follow " + Quests.MOST_TRACKED + " already - stop following one first.";
        ui.textCentered(ui.small, foot, x + w / 2, bottom + 14, fullNote > 0 ? Theme.WARNING : Theme.HINT);
        if (list.isEmpty()) {
            ui.textCentered(ui.body, showDone ? "Nothing seen through yet." : "Nothing has been asked of you yet.",
                    x + w / 2, top + 30, Theme.INK_FADED);
            if (!showDone) {
                ui.textCentered(ui.small, "Talk to the people you meet: those with a task have a mark over their "
                        + "heads.", x + w / 2, top + 60, Theme.INK_FADED);
            }
            return;
        }
        ui.clip(lx, top + 2, lw, bottom - top - 4);
        ui.measure();
        float ty = top + 14 - journalScroll;
        float bw = 118;
        for (Quests.Entry e : list) {
            boolean ready = e.state() == Quests.State.READY, isDone = e.state() == Quests.State.DONE;
            float[] c = isDone ? Theme.INK_FADED : Theme.INK;
            // its mark: a diamond, hollow while under way, filled gold when it is to be handed in, faded when done
            float dx = lx + 26, dy = ty + ui.body.lineHeight() / 2f;
            if (ready) ui.diamond(dx, dy, 6, DONE);
            else if (isDone) ui.diamond(dx, dy, 6, Theme.INK_FADED);
            else ui.diamondOutline(dx, dy, 6, 1.5f, Theme.INK);
            ui.text(ui.body, Ui.fit(ui.body, e.title(), lw - bw - 160), lx + 40, ty, c);
            String where = e.place().isBlank() ? "" : e.place();
            if (!isDone) {
                boolean on = e.tracked();
                if (ui.button(on ? "Following" : "Follow", lx + lw - bw - 16, ty - 3, bw, 28, on, true)) {
                    if (!quests.setTracked(e.id(), !on) && !on) {
                        fullNote = 3;
                        Sounds.play(SoundKeys.ui("error"));
                    } else {
                        Sounds.play(SoundKeys.ui("click"));
                    }
                }
                ui.text(ui.small, where, lx + lw - bw - 28 - ui.small.width(where), ty + 3, Theme.INK_FADED);
            } else {
                ui.text(ui.small, where, lx + lw - 18 - ui.small.width(where), ty + 3, Theme.INK_FADED);
            }
            ty += ui.body.lineHeight() + 4;
            String line = isDone ? "Done." : ready ? "Done - go back to " + (e.giver().isBlank() ? "whoever asked"
                    : e.giver()) + "." : e.goal() + (e.giver().isBlank() ? "" : "  (for " + e.giver() + ")");
            ty += ui.paragraph(ui.small, line, lx + 52, ty, lw - 72 - bw, Theme.INK_FADED) + 2;
            Quest q = quests.byId(e.id());
            if (q != null && !isDone && !q.reward().isBlank()) {
                String pays = "Pays " + Quests.rewardText(q.reward()) + (q.xp() > 0 ? " and " + q.xp() + " experience"
                        : "");
                ty += ui.paragraph(ui.tiny, pays, lx + 52, ty, lw - 72 - bw, Theme.INK_FADED);
            }
            ty += 14;
        }
        float most = Math.max(0, ui.lowest() + journalScroll - (bottom - 10));
        ui.unclip();
        var in = ui.input();
        if (most > 0 && in.scroll() != 0 && ui.hover(lx, top, lw, bottom - top)) {
            journalScroll -= (float) Math.signum(in.scroll()) * 48;
            in.consumeScroll();
        }
        journalScroll = Math.max(0, Math.min(most, journalScroll));
    }

}
