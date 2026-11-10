package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.building.Building;
import com.larsons.game.core.Game;
import com.larsons.game.level.HordeTypes;
import com.larsons.game.math.Vec3;
import com.larsons.game.ui.Theme;
import com.larsons.game.world.Ammo;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Rarity;
import com.larsons.game.world.Spot;
import com.larsons.game.world.Tier;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * What a horde map is fought round ({@link HordeTypes}), used: the weapon
 * boards (pay, and the weapon chalked on it is hers - pay again, and it is
 * whetted and fed), the mystery box (pay, it rattles through every weapon
 * there is and stops on one, hers to take while the lid is up) and the
 * barricades (their boards, torn off and nailed back on).
 *
 * <p>All of it is paid for out of her purse - coins, which a horde game
 * calls points ({@link Story#purse}).
 */
final class HordeProps {

    /** A box's turn: rattling until {@code ready}, open until {@code shuts}; what it stopped on. */
    private record Roll(double started, double ready, double shuts, Item item) {}

    static final double RATTLES = 3.0, STAYS_OPEN = 9.0, RATTLE_EVERY = 0.22;
    /** How many of a weapon's ammunition come with it, and again each time it is fed. */
    static final int FEED = 2;

    private final Game game;
    private final World world;
    private final Story story;
    private final Map<Integer, Roll> rolls = new HashMap<>();
    private final Map<Integer, Building> down = new HashMap<>();
    private final Random random = new Random();
    private double rattle;
    /** What a board nailed back on pays, and how many more are paid for this round (the horde sets both). */
    private int boardPay, boardsPaid;

    HordeProps(Game game, World world, Story story) {
        this.game = game;
        this.world = world;
        this.story = story;
    }

    boolean use(Spot s) {
        switch (s.kind()) {
            case HordeTypes.WALL_BUY -> buy(s);
            case HordeTypes.BOX -> box(s);
            case HordeTypes.BARRICADE -> board(s);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** What the prompt says E does at {@code s}, or null when it is none of these. */
    String text(Spot s) {
        return switch (s.kind()) {
            case HordeTypes.WALL_BUY -> {
                Item item = sold(s);
                if (item == null) yield "Nothing chalked here";
                boolean has = owns(item);
                yield (has ? "Whet and feed the " + item.name() : "Buy the " + item.fullName())
                        + "  (" + (has ? again(s) : cost(s)) + " " + story.purse() + ")";
            }
            case HordeTypes.BOX -> {
                Roll r = rolls.get(s.id());
                if (r == null) yield "Try the " + s.name().toLowerCase(Locale.ROOT) + "  (" + cost(s) + " " + story.purse() + ")";
                yield world.time() < r.ready() ? "It rattles..." : "Take the " + r.item().fullName();
            }
            case HordeTypes.BARRICADE -> {
                int n = planks(s), most = most(s);
                yield n >= most ? "The boards hold  (" + n + "/" + most + ")" : "Nail a board on  (" + n + "/" + most + ")";
            }
            default -> null;
        };
    }

    // --- the weapon boards ---------------------------------------------------------------

    private static int cost(Spot s) {
        return (int) Math.round(s.num("cost", 0));
    }

    private static int again(Spot s) {
        int a = (int) Math.round(s.num("again", 0));
        return a > 0 ? a : Math.max(1, cost(s) / 2);
    }

    /** What the board {@code s} sells, or null when that is nothing there is. */
    static Item sold(Spot s) {
        String tier = s.str("tier", HordeTypes.LOWEST);
        return Item.parse(s.str("item", "sword") + (HordeTypes.LOWEST.equals(tier) ? "" : " " + tier));
    }

    /** Whether she has the weapon {@code item} (one of that tier or better): the board then feeds it. */
    private boolean owns(Item item) {
        if (Tier.row(item.id()) == null) return false;       // (a potion: another is another)
        for (Item i : world.player().inventory().items()) {
            if (i.def() == item.def() && (i.tier() == null || item.tier() == null
                    || i.tier().ordinal() >= item.tier().ordinal())) return true;
        }
        return false;
    }

    private void buy(Spot s) {
        Item item = sold(s);
        if (item == null) return;
        Player p = world.player();
        boolean has = owns(item);
        int price = has ? again(s) : cost(s);
        if (!p.spend(price)) {
            Sounds.play(SoundKeys.ui("error"), 0.6);
            game.toast("Not enough " + story.purse() + " (" + price + ")", Theme.WARNING);
            return;
        }
        ItemDef ammo = Ammo.of(item.def());
        if (!has) {
            story.give(item, 1);
            world.equipSelected();
        }
        if (ammo != null) story.give(Item.of(ammo), FEED);
        Sounds.play(SoundKeys.ui("buy"));
        world.combat().say(has ? "Whetted and fed" : item.fullName(), rarity(item).rgb(), s.position(), 2.3, false);
    }

    static Rarity rarity(Item item) {
        return item.tier() != null ? item.tier().rarity() : item.rarity() != null ? item.rarity() : Rarity.COMMON;
    }

    // --- the mystery box -----------------------------------------------------------------

    /** Every weapon the box {@code s} can stop on, at every tier it comes in. */
    static List<Item> pool(Spot s) {
        List<Item> out = new ArrayList<>();
        String text = s.str("pool", "").strip();
        if (!text.isEmpty()) {
            for (String part : text.split(",")) {
                Item i = Item.parse(part.strip().toLowerCase(Locale.ROOT).replace(' ', '_'));
                if (i == null) i = Item.parse(part.strip().toLowerCase(Locale.ROOT));
                if (i != null) out.add(i);
            }
        }
        if (!out.isEmpty()) return out;
        for (ItemDef d : ItemDef.ALL) {
            Tier.Row row = Tier.row(d.id());
            if (row == null) out.add(Item.of(d));
            else for (Tier t : row.tiers()) out.add(Item.of(d, t));
        }
        return out;
    }

    /** One of {@code pool}, the rarer the less often (a Mythic about one turn in forty). */
    static Item pick(List<Item> pool, Random random) {
        double total = 0;
        double[] w = new double[pool.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = switch (rarity(pool.get(i))) {
                case COMMON -> 10;
                case UNCOMMON -> 9;
                case RARE -> 7;
                case EPIC -> 5;
                case LEGENDARY -> 2.5;
                case MYTHIC -> 1;
            };
            total += w[i];
        }
        double at = random.nextDouble() * total;
        for (int i = 0; i < w.length; i++) {
            at -= w[i];
            if (at <= 0) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    private void box(Spot s) {
        double now = world.time();
        Roll r = rolls.get(s.id());
        if (r != null) {
            if (now < r.ready()) return;
            rolls.remove(s.id());
            story.give(r.item(), 1);
            ItemDef ammo = Ammo.of(r.item().def());
            if (ammo != null) story.give(Item.of(ammo), FEED);
            world.equipSelected();
            Sounds.play(SoundKeys.ui("buy"));
            return;
        }
        int price = cost(s);
        if (!world.player().spend(price)) {
            Sounds.play(SoundKeys.ui("error"), 0.6);
            game.toast("Not enough " + story.purse() + " (" + price + ")", Theme.WARNING);
            return;
        }
        rolls.put(s.id(), new Roll(now, now + RATTLES, now + RATTLES + STAYS_OPEN, pick(pool(s), random)));
        world.combat().sound("horde/box_open", s.position());
    }

    /** A frame: the boxes that rattle show what they pass; the ones left open shut. */
    void update(double dt) {
        if (rolls.isEmpty()) return;
        double now = world.time();
        rattle -= dt;
        boolean show = rattle <= 0;
        if (show) rattle = RATTLE_EVERY;
        for (var it = rolls.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Roll r = e.getValue();
            Spot s = spot(e.getKey());
            if (s == null || now > r.shuts()) {
                if (s != null) {
                    world.combat().say("The lid shuts", 0xC8D2DC, s.position(), 1.6, true);
                    world.combat().sound("horde/box_shut", s.position());
                }
                it.remove();
                continue;
            }
            if (now < r.ready()) {
                if (show) {
                    List<Item> pool = pool(s);
                    Item passing = pool.get(random.nextInt(pool.size()));
                    world.combat().say(passing.name(), rarity(passing).rgb(), s.position(), 1.5, true);
                    world.combat().sound("horde/box_rattle", s.position());
                }
            } else if (now - dt < r.ready()) {
                world.combat().say(r.item().fullName() + "!", rarity(r.item()).rgb(), s.position(), 1.9, false);
                world.combat().sound("horde/box_stop", s.position());
            }
        }
    }

    private Spot spot(int id) {
        for (Spot s : world.spots()) if (s.id() == id) return s;
        return null;
    }

    // --- the barricades ------------------------------------------------------------------

    static int most(Spot s) {
        return Math.max(1, (int) Math.round(s.num("planks", 6)));
    }

    /** How many boards {@code s} still has. */
    static int planks(Spot s) {
        return s.state().get("planks") instanceof Number n ? n.intValue() : most(s);
    }

    /** Every barricade here. */
    List<Spot> barricades() {
        List<Spot> out = new ArrayList<>();
        for (Spot s : world.spots()) if (s.kind().equals(HordeTypes.BARRICADE)) out.add(s);
        return out;
    }

    /** A board of {@code s} torn off: with the last, the way is open. */
    void tear(Spot s) {
        int n = planks(s);
        if (n <= 0) return;
        s.state().put("planks", n - 1);
        world.combat().sound("horde/board_torn", s.position());
        world.combat().say(n - 1 == 0 ? "Broken through!" : "-1 board", 0xD98A5A, s.position(), 1.9, n - 1 > 0);
        if (n - 1 == 0) {
            Building b = world.takeBuilding(s.id());
            if (b != null) down.put(s.id(), b);
        }
    }

    /** A board nailed back on {@code s}: whether there was room for one. */
    boolean nail(Spot s) {
        int n = planks(s);
        if (n >= most(s)) return false;
        s.state().put("planks", n + 1);
        if (n == 0) {
            Building b = down.remove(s.id());
            if (b != null) world.addBuilding(b);
        }
        return true;
    }

    private void board(Spot s) {
        if (!nail(s)) {
            Sounds.play(SoundKeys.ui("error"), 0.5);
            return;
        }
        world.combat().sound("horde/board_nailed", s.position());
        if (boardPay > 0 && boardsPaid > 0) {
            boardsPaid--;
            world.player().addCoins(boardPay);
            earned += boardPay;
            world.combat().say("+" + boardPay, 0xF0C030, s.position(), 2.0, true);
        }
    }

    private int earned;

    /** The points nailing boards has paid since this was last asked. */
    int drainEarned() {
        int e = earned;
        earned = 0;
        return e;
    }

    /** A board nailed on pays {@code pay}, for the next {@code boards} of them (a round's worth). */
    void pay(int pay, int boards) {
        boardPay = pay;
        boardsPaid = boards;
    }

    /** Every barricade whole again (the carpenter's gift). */
    void mendAll() {
        for (Spot s : barricades()) while (nail(s)) { }
    }

    /** Where {@code s}'s boards are, for something coming at them from {@code from}. */
    static Vec3 outside(Spot s, Vec3 from) {
        return HordeTypes.outside(from, s.position());
    }
}
