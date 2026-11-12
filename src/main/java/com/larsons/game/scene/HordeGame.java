package com.larsons.game.scene;

import com.larsons.game.actor.Breed;
import com.larsons.game.actor.Folk;
import com.larsons.game.actor.Folks;
import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.ui.CardArt;
import com.larsons.game.core.Game;
import com.larsons.game.input.Input;
import com.larsons.game.level.HordeRules;
import com.larsons.game.level.HordeTypes;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.story.Quests;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.util.Json;
import com.larsons.game.world.Ammo;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Spot;
import com.larsons.game.world.Supplies;
import com.larsons.game.world.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * A horde game: the Risen come in rounds, more and tougher each round, out
 * of the map's spawns and through its barricades, until she is knocked out.
 *
 * <ul>
 *   <li><b>Points</b> are her purse: 10 a blow that lands, 60 a Risen
 *       felled, {@value #BOARD} a board nailed back on. They buy the weapons chalked on the boards, a turn of the
 *       mystery box, and open the gates.</li>
 *   <li><b>Rounds</b>: {@link HordeRules} says how many come and how tough;
 *       walkers at first, then crawlers, runners, spitters, brutes and the
 *       Risen Bortzal. A rest between rounds, to nail boards and buy.</li>
 *   <li><b>Gifts</b> the felled leave now and then, for a while: every
 *       weapon fed (max ammo), every blow a kill (insta-kill), points
 *       doubled, everything up felled at once (kaboom), every barricade
 *       mended (carpenter).</li>
 * </ul>
 *
 * <p>The best round on each map is kept in {@code config/horde.json}.
 */
final class HordeGame {

    enum Phase { READY, ROUND, REST, OVER }

    /** What the felled leave, now and then. */
    enum Gift {
        MAX_AMMO("Max ammo", "box", 0x7FE8FF), INSTA_KILL("Insta-kill", "skull", 0xFF6A5A),
        DOUBLE_POINTS("Double points", "coins", 0xF0C030), KABOOM("Kaboom", "star", 0xFFB060),
        CARPENTER("Carpenter", "hammer", 0xD6AA5C);

        final String label, icon;
        final int rgb;

        Gift(String label, String icon, int rgb) {
            this.label = label;
            this.icon = icon;
            this.rgb = rgb;
        }
    }

    private record Drop(Gift gift, Vec3 at, double until) {}

    static final int HIT = HordeRules.HIT, KILL = HordeRules.KILL, BOARD = 10, KABOOM = 400, CARPENTER = 200;
    static final double READY_FOR = 4, GIFT_LIES = 22, GIFT_LASTS = 30, GIFT_CHANCE = 0.045;
    static final int GIFTS_A_ROUND = 3;
    /** How far from a spawn a barricade is still its barricade. */
    static final double BARRICADE_NEAR = 16;

    private final Game game;
    private final World world;
    private final Story story;
    private final HordeProps props;
    private final HordeRules rules;
    private String map;
    private final String mapName;
    private final Runnable again, leave;
    private final Random random = new Random();

    private Phase phase = Phase.READY;
    private int round, toCome, kills, earned, livesLeft, seenKnockOuts, gifts, best;
    private double clock = READY_FOR, spawnClock, banner, instaUntil, doubleUntil;
    private boolean newBest;
    private final List<Folk> up = new ArrayList<>();
    private final Map<Folk, Double> healthSeen = new IdentityHashMap<>();
    private final Map<Folk, Spot> through = new IdentityHashMap<>();
    private final Map<Integer, Double> tearing = new LinkedHashMap<>();
    private final List<Drop> drops = new ArrayList<>();

    /**
     * @param map   the map's id (its folder's name), for its best round
     * @param again start the same map again
     * @param leave back to where it was started from
     */
    HordeGame(Game game, World world, Story story, HordeRules rules, String map, String mapName, Runnable again,
              Runnable leave) {
        this.game = game;
        this.world = world;
        this.story = story;
        this.props = story.props();
        this.rules = rules;
        this.map = map == null ? "" : map;
        this.mapName = mapName == null ? "" : mapName;
        this.again = again;
        this.leave = leave;
        this.livesLeft = rules.lives();
        this.best = best(game, this.map);
        world.setHordeLoot(f -> false);                 // (the Risen pay in points, not in what they drop)
        story.setPurse("points");
        story.setMoreMarks(this::marks);
        seenKnockOuts = world.knockOuts();
    }

    /** Her purse and her kit, as a horde game starts: nothing but the map's points and what its rules hand her. */
    void outfit() {
        Player p = world.player();
        for (int i = 0; i < p.inventory().size(); i++) p.inventory().set(i, null);
        p.addCoins(rules.points() - p.coins());
        for (Map.Entry<Item, Integer> e : Quests.rewards(rules.kit())) {
            story.give(e.getKey(), e.getValue());
            ItemDef ammo = Ammo.of(e.getKey().def());
            if (ammo != null) story.give(Item.of(ammo), HordeProps.FEED);
        }
        world.equipSelected();
    }

    /** The map's id among the saved ones: its best round is kept under it. */
    void named(String id) {
        map = id == null ? "" : id;
        best = best(game, map);
    }

    Phase phase() { return phase; }

    int round() { return round; }

    int kills() { return kills; }

    boolean over() { return phase == Phase.OVER; }

    // --- a frame -------------------------------------------------------------------------

    void update(double dt, Input in) {
        if (phase == Phase.OVER) {
            if (in.pressed(GLFW_KEY_ENTER)) again.run();
            else if (in.pressed(GLFW_KEY_ESCAPE)) {
                in.consumeKey(GLFW_KEY_ESCAPE);
                leave.run();
            }
            return;
        }
        double now = world.time();
        banner = Math.max(0, banner - dt);
        if (world.knockOuts() > seenKnockOuts) {
            seenKnockOuts = world.knockOuts();
            livesLeft--;
            if (livesLeft <= 0) {
                end();
                return;
            }
            game.toast("Knocked out: " + livesLeft + (livesLeft == 1 ? " life" : " lives") + " left", Theme.WARNING);
        }
        earn(props.drainEarned(), false);
        switch (phase) {
            case READY -> {
                clock -= dt;
                if (clock <= 0) begin(1);
            }
            case REST -> {
                clock -= dt;
                if (clock <= 0) begin(round + 1);
            }
            case ROUND -> {
                spawnClock -= dt;
                if (toCome > 0 && spawnClock <= 0 && alive() < rules.upAtOnce(round)) {
                    if (rise()) toCome--;
                    spawnClock = rules.every(round);
                }
            }
            default -> { }
        }
        // what her blows did: points for each that landed; a Risen felled, more - and now and then a gift
        for (Folk f : up) {
            double seen = healthSeen.getOrDefault(f, f.health());
            if (f.health() < seen - 0.01) {
                earn(HIT, true);
                if (now < instaUntil && f.alive()) world.strike(f, 1e6);
            }
            healthSeen.put(f, f.health());
        }
        for (World.Kill k : story.drainFelled()) {
            if (!k.risen()) continue;
            kills++;
            earn(KILL, true);
            if (gifts < GIFTS_A_ROUND && random.nextDouble() < GIFT_CHANCE + 0.002 * round) {
                gifts++;
                Gift g = Gift.values()[random.nextInt(Gift.values().length)];
                if (g == Gift.CARPENTER && props.barricades().isEmpty()) g = Gift.MAX_AMMO;
                drops.add(new Drop(g, world.terrain().onGround(k.at()), now + GIFT_LIES));
                world.combat().sound("horde/gift_drop", k.at());
            }
        }
        up.removeIf(f -> {
            if (f.alive()) return false;
            healthSeen.remove(f);
            through.remove(f);
            return true;
        });
        barricades(dt);
        keepIn();
        gifts(now);
        if (phase == Phase.ROUND && toCome == 0 && up.isEmpty()) {
            phase = Phase.REST;
            clock = rules.rest();
            game.toast("Round " + round + " survived", Theme.OK);
            Sounds.play(SoundKeys.ui("round_end"));
        }
    }

    /** How far from where she starts she may go: short of where the Risen rise (an open map's edge); 0 not worked out. */
    private double arena;
    private boolean warned;

    /**
     * She stays in the map: the barricades' gaps are walls to her
     * ({@link World#addPlayerWall}), and on a map open on every side she
     * goes no farther out than a little short of the nearest place the
     * Risen rise - never out where they come from.
     */
    private void keepIn() {
        Vec3 c = world.spawnPoint();
        if (c == null || world.hordeSpawns().isEmpty()) return;
        if (arena <= 0) {
            double nearest = Double.MAX_VALUE;
            for (World.HordeSpawn s : world.hordeSpawns()) nearest = Math.min(nearest, s.at().horizontalDistance(c));
            arena = Math.max(8, nearest - 2.5);
        }
        Vec3 her = world.player().ground();
        double dx = her.x() - c.x(), dz = her.z() - c.z(), d = Math.hypot(dx, dz);
        if (d <= arena) return;
        world.player().shoveTo(c.x() + dx / d * arena, c.z() + dz / d * arena);
        if (!warned) {
            warned = true;
            game.toast("No way out: the dead hold the ground beyond", Theme.WARNING);
        }
    }

    private int alive() {
        int n = 0;
        for (Folk f : up) if (f.alive()) n++;
        return n;
    }

    private void earn(int points, boolean doubled) {
        if (points <= 0) return;
        int n = doubled && world.time() < doubleUntil ? points * 2 : points;
        world.player().addCoins(n);
        earned += n;
    }

    private void begin(int n) {
        round = n;
        phase = Phase.ROUND;
        toCome = rules.count(n);
        spawnClock = 1.2;
        gifts = 0;
        banner = 3.5;
        props.pay(BOARD, 4 + 2 * props.barricades().size());
        Sounds.play(SoundKeys.ui("round_start"));
    }

    /** Round {@code n} begins now, whatever round it was (a script's: to see a late round without playing to it). */
    void skipTo(int n) {
        if (phase != Phase.OVER) begin(Math.max(1, n));
    }

    private void end() {
        phase = Phase.OVER;
        newBest = round > best;
        if (newBest) {
            best = round;
            saveBest(game, map, round);
        }
        Sounds.play(SoundKeys.ui("game_over"));
    }

    // --- the Risen -----------------------------------------------------------------------

    /** What comes next in round {@code n}: walkers at first; the rest come in as the rounds go by. */
    Breed next(int n, String wanted) {
        Breed asked = HordeTypes.ANY.equals(wanted) ? null : Breed.byKey(wanted);
        if (asked != null && asked.risen()) return asked;
        List<Breed> kinds = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        int brutes = 0;
        for (Folk f : up) if (f.breed() == Breed.RISEN_BRUTE && f.alive()) brutes++;
        for (Breed b : HordeRules.risen()) {
            if (!rules.comes(b)) continue;
            double w = switch (b) {
                case RISEN_WALKER -> 10;
                case RISEN_CRAWLER -> n >= 2 ? 3 : 0;
                case RISEN_RUNNER -> n >= 3 ? Math.min(9, 2.0 * (n - 2)) : 0;
                case RISEN_SPITTER -> n >= 4 ? 3 : 0;
                case RISEN_BRUTE -> n >= 5 && brutes < 1 + n / 5 ? 1.5 : 0;
                case RISEN_BORTZAL -> n >= 6 ? 3 : 0;
                default -> 0;
            };
            if (w > 0) {
                kinds.add(b);
                weights.add(w);
            }
        }
        if (kinds.isEmpty()) return Breed.RISEN_WALKER;
        double total = 0;
        for (double w : weights) total += w;
        double at = random.nextDouble() * total;
        for (int i = 0; i < kinds.size(); i++) {
            at -= weights.get(i);
            if (at <= 0) return kinds.get(i);
        }
        return kinds.get(kinds.size() - 1);
    }

    /** One more out of the ground, at one of the spawns near her (or, on a map with none, somewhere round her). */
    private boolean rise() {
        Vec3 her = world.player().ground();
        List<World.HordeSpawn> open = new ArrayList<>();
        for (World.HordeSpawn s : world.hordeSpawns()) {
            if (s.from() > round) continue;
            if (!s.after().isBlank() && !story.opened(s.after())) continue;
            open.add(s);
        }
        Vec3 at;
        String kind = HordeTypes.ANY;
        if (open.isEmpty()) {
            double a = random.nextDouble() * Math.PI * 2, d = 20 + random.nextDouble() * 10;
            at = world.terrain().onGround(new Vec3(her.x() + Math.cos(a) * d, 0, her.z() + Math.sin(a) * d));
        } else {
            // one of the four nearest her that she is not standing on
            open.sort((a, b) -> Double.compare(a.at().horizontalDistance(her), b.at().horizontalDistance(her)));
            List<World.HordeSpawn> near = new ArrayList<>();
            for (World.HordeSpawn s : open) {
                if (s.at().horizontalDistance(her) > 5 && near.size() < 4) near.add(s);
            }
            if (near.isEmpty()) near.add(open.get(open.size() - 1));
            World.HordeSpawn s = near.get(random.nextInt(near.size()));
            double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * 1.2;
            at = world.terrain().onGround(new Vec3(s.at().x() + Math.cos(a) * d, 0, s.at().z() + Math.sin(a) * d));
            kind = s.kind();
        }
        Breed breed = next(round, kind);
        Vec3 to = her.sub(at);
        Folk f = world.folks().spawn(breed, at, SpriteView.headingOf(to.x(), to.z()), Folks.Spec.ANY);
        if (f == null) return false;
        f.relentless().tougher(rules.health(round));
        Spot bar = null;
        double nearest = BARRICADE_NEAR;
        for (Spot s : props.barricades()) {
            double d = s.position().horizontalDistance(at);
            if (d < nearest) {
                nearest = d;
                bar = s;
            }
        }
        if (bar != null) {
            f.via(HordeProps.outside(bar, at), HordeProps.planks(bar) > 0);
            through.put(f, bar);
        }
        up.add(f);
        healthSeen.put(f, f.health());
        world.combat().say("✦", 0x7CC050, at, 1.0, true);
        world.combat().sound("horde/rise", at);
        return true;
    }

    /** The boards: those at a barricade tear at it, a board every so often; broken, they come through. */
    private void barricades(double dt) {
        Map<Spot, Integer> at = new IdentityHashMap<>();
        for (Map.Entry<Folk, Spot> e : through.entrySet()) {
            Folk f = e.getKey();
            Spot s = e.getValue();
            if (!f.hasVia()) continue;
            boolean barred = HordeProps.planks(s) > 0;
            f.via(HordeProps.outside(s, f.position()), barred);
            if (barred && f.atBar()) at.merge(s, 1, Integer::sum);
        }
        for (Map.Entry<Spot, Integer> e : at.entrySet()) {
            Spot s = e.getKey();
            double t = tearing.getOrDefault(s.id(), 0.0) + dt * Math.min(3, e.getValue());
            if (t >= 2.0) {
                t = 0;
                props.tear(s);
            }
            tearing.put(s.id(), t);
        }
    }

    // --- the gifts -----------------------------------------------------------------------

    private List<World.Mark> marks() {
        List<World.Mark> out = new ArrayList<>();
        for (Drop d : drops) out.add(new World.Mark(d.at().add(0, 0.2, 0), d.gift().icon, 0));
        return out;
    }

    private void gifts(double now) {
        Vec3 her = world.player().ground();
        for (var it = drops.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            if (now > d.until()) {
                it.remove();
                continue;
            }
            if (d.at().horizontalDistance(her) > 1.4 || Math.abs(d.at().y() - her.y()) > 2.5) continue;
            it.remove();
            take(d.gift(), now);
        }
    }

    private void take(Gift g, double now) {
        Player p = world.player();
        game.toast(g.label + "!", Theme.ACCENT);
        world.combat().say(g.label + "!", g.rgb, p.feet(), 2.3, false);
        world.combat().sound("horde/gift_take", p.ground());
        switch (g) {
            case MAX_AMMO -> {
                java.util.Set<ItemDef> fed = new java.util.HashSet<>();
                for (Item i : p.inventory().items()) {
                    ItemDef a = Ammo.of(i.def());
                    if (a != null && fed.add(a)) story.give(Item.of(a), 3);
                }
                if (fed.isEmpty()) story.give(Item.of(Supplies.WHETSTONE), 2);
            }
            case INSTA_KILL -> instaUntil = now + GIFT_LASTS;
            case DOUBLE_POINTS -> doubleUntil = now + GIFT_LASTS;
            case KABOOM -> {
                for (Folk f : new ArrayList<>(up)) if (f.alive()) world.strike(f, 1e6);
                earn(KABOOM, false);
            }
            case CARPENTER -> {
                props.mendAll();
                earn(CARPENTER, false);
            }
        }
    }

    // --- what she sees -------------------------------------------------------------------

    void draw(Ui ui) {
        double now = world.time();
        // the round, bottom left: a brass plate, the number in blood red
        float px = 24, py = ui.height() - 150, pw = 150, ph = 122;
        ui.plate(px, py, pw, ph, Theme.BRASS_DARK);
        ui.textCentered(ui.small, phase == Phase.REST ? "Next round in " + (int) Math.ceil(clock) + " s"
                : phase == Phase.READY ? "They are coming" : "Round", px + pw / 2, py + 10, Theme.BRASS_LIGHT);
        String n = phase == Phase.READY ? String.valueOf((int) Math.ceil(clock)) : String.valueOf(round);
        ui.textCentered(ui.title, n, px + pw / 2, py + 26, Theme.rgb(200, 40, 30, 1f));
        int left = toCome + alive();
        ui.textCentered(ui.small, phase == Phase.ROUND ? left + (left == 1 ? " Risen left" : " Risen left")
                : mapName, px + pw / 2, py + ph - 26, Theme.ITEM);
        // her points, over the plate
        String pts = world.player().coins() + " points";
        float w = ui.large.width(pts) + 28;
        ui.plate(px, py - 46, Math.max(pw, w), 40, Theme.BRASS_DARK);
        ui.text(ui.large, pts, px + 14, py - 42, Theme.rgb(240, 192, 48, 1f));
        if (rules.lives() > 1) {
            ui.text(ui.small, livesLeft + (livesLeft == 1 ? " life" : " lives"), px + Math.max(pw, w) + 10, py - 34,
                    Theme.HINT);
        }
        // the gifts that last
        float gy = 132;
        gy = lasting(ui, "Insta-kill", "skull", instaUntil - now, gy);
        lasting(ui, "Double points", "coins", doubleUntil - now, gy);
        // the round's banner
        if (banner > 0 && phase == Phase.ROUND) {
            float a = (float) Math.min(1, banner / 0.8);
            String text = "Round " + round;
            float tw = ui.title.width(text);
            ui.plate(ui.width() / 2 - tw / 2 - 40, ui.height() * 0.2f - 10, tw + 80, 86, Theme.withAlpha(Theme.BRASS_DARK, a));
            ui.textCentered(ui.title, text, ui.width() / 2, ui.height() * 0.2f, Theme.rgb(200, 40, 30, a));
        }
        if (phase == Phase.OVER) over(ui);
    }

    private float lasting(Ui ui, String label, String icon, double left, float y) {
        if (left <= 0) return y;
        String text = label + "  " + (int) Math.ceil(left) + " s";
        float w = ui.body.width(text) + 62, x = ui.width() / 2 - w / 2;
        ui.plate(x, y, w, 36, Theme.BRASS_DARK);
        float[] uv = CardArt.iconUv(icon);
        if (uv != null) ui.image(CardArt.iconTexture(), x + 10, y + 6, 24, 24, uv[0], uv[1], uv[2], uv[3], Theme.TITLE);
        ui.text(ui.body, text, x + 44, y + 6, Theme.TITLE);
        return y + 42;
    }

    private void over(Ui ui) {
        ui.rect(0, 0, ui.width(), ui.height(), Theme.withAlpha(Theme.SCRIM, 0.7f));
        float w = 680, h = 330, x = ui.width() / 2 - w / 2, y = ui.height() / 2 - h / 2;
        ui.panel(x, y, w, h);
        ui.textCentered(ui.title, "The horde took you", x + w / 2, y + 22, Theme.rgb(200, 40, 30, 1f));
        ui.textCentered(ui.large, mapName + ": round " + round, x + w / 2, y + 100, Theme.TITLE);
        ui.textCentered(ui.body, kills + " Risen felled  ·  " + earned + " points earned", x + w / 2, y + 144, Theme.ITEM);
        ui.textCentered(ui.body, newBest ? "Your best yet on this map" : "Your best here: round " + best, x + w / 2,
                y + 176, newBest ? Theme.OK : Theme.HINT);
        if (ui.button("Again (Enter)", x + 40, y + h - 76, 200, 46)) again.run();
        if (ui.button("Leave (Esc)", x + w - 240, y + h - 76, 200, 46)) leave.run();
    }

    // --- the best rounds -----------------------------------------------------------------

    private static Path file(Game game) {
        return game.settings().config.resolve("horde.json");
    }

    /** The best round reached on {@code map}, or 0. */
    static int best(Game game, String map) {
        try {
            Path f = file(game);
            if (!Files.isRegularFile(f)) return 0;
            Map<String, Object> j = Json.asObject(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
            return (int) Json.num(j, map, 0);
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private static void saveBest(Game game, String map, int round) {
        if (map.isBlank()) return;
        try {
            Path f = file(game);
            Map<String, Object> j = new LinkedHashMap<>();
            if (Files.isRegularFile(f)) j.putAll(Json.asObject(Json.parse(Files.readString(f, StandardCharsets.UTF_8))));
            j.put(map, round);
            Files.createDirectories(f.getParent());
            Files.writeString(f, Json.stringify(j), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            System.err.println("[horde] cannot keep the best round: " + e.getMessage());
        }
    }
}
