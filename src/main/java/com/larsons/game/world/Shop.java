package com.larsons.game.world;

import com.larsons.game.cards.Card;
import com.larsons.game.cards.CardBook;
import com.larsons.game.cards.Hero;
import com.larsons.game.math.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Somewhere to buy things with coins, and sell them: a market stall, a
 * shop's counter. What it sells is its <b>stock</b>, written in Creative
 * Mode as a line of offers separated by commas - an item, a card, or so
 * many of her class's shop cards:
 *
 * <pre>
 *   whetstone x20 @3, arrow bundle, upgrade ingot x5, longbow rare @90,
 *   card:mage_fireball @60, cards x4
 * </pre>
 *
 * "x20" is how many it has (none written: it never runs out - a card, one);
 * "@3" its price in coins (none written: what it is {@linkplain #worth
 * worth}, a card its own price). "cards x4" is four of the shop cards of
 * whoever is shopping, chosen once for each class. A shop that {@link #buys}
 * takes what she sells for half what it is worth.
 */
public final class Shop {

    /** What it sells when nothing is written. */
    public static final String DEFAULT_STOCK = "whetstone, iron wedges, mace studs, small crystal, large crystal, "
            + "arrow bundle, bolt bundle, upgrade ingot x6, reshuffle scroll x4, cards x3";

    /** How far from its counter's front she can reach it, metres. */
    public static final double REACH = 1.7;

    /** "x6" after an offer: how many; "@12": its price. */
    private static final Pattern PRICE = Pattern.compile("^(.*?)\\s*@\\s*(\\d+)$");
    private static final Pattern HOW_MANY = Pattern.compile("^(.*?)\\s*[x×*]\\s*(\\d+)$");

    /**
     * One thing on sale: an item (or, with {@code card}, a card), its
     * price, and how many are left ({@code -1}: it never runs out).
     */
    public static final class Offer {
        private final Item item;
        private final String card;
        private final int price;
        private int left;

        Offer(Item item, String card, int price, int left) {
            this.item = item;
            this.card = card;
            this.price = Math.max(0, price);
            this.left = left;
        }

        public Item item() { return item; }

        /** The card it is, or null (an item). */
        public String card() { return card; }

        public int price() { return price; }

        /** How many are left ({@code -1}: never runs out). */
        public int left() { return left; }

        public boolean soldOut() { return left == 0; }

        /** What it is called. */
        public String name() {
            if (card != null) {
                Card c = CardBook.get().card(card);
                return c == null ? card : c.name();
            }
            return item.fullName();
        }

        public Rarity rarity() {
            if (card != null) {
                Card c = CardBook.get().card(card);
                return c == null ? Rarity.COMMON : c.rarity();
            }
            return item.rarity();
        }
    }

    /** How buying or selling went. */
    public enum Deal { DONE, POOR, SOLD_OUT, NO_ROOM, WONT_BUY, NOTHING }

    private final int id;
    private final String name;
    private final Vec3 position;
    private final double heading;
    private final boolean buys;
    private final List<Offer> items = new ArrayList<>();
    /** "cards x4": how many of her class's shop cards. */
    private final int classCards;
    /** The cards offered to each class, chosen the first time she shops as it. */
    private final java.util.Map<String, List<Offer>> cardsFor = new java.util.HashMap<>();
    /** Cards named in the stock ("card:id"). */
    private final List<Offer> named = new ArrayList<>();

    /**
     * A shop: the level's object {@code id}, called {@code name}, at
     * {@code position} facing {@code heading}, selling {@code stock} (blank:
     * {@link #DEFAULT_STOCK}) - and buying, when {@code buys}.
     */
    public Shop(int id, String name, Vec3 position, double heading, String stock, boolean buys) {
        this.id = id;
        this.name = name == null || name.isBlank() ? "Shop" : name.strip();
        this.position = position;
        this.heading = heading;
        this.buys = buys;
        int cards = 0;
        String text = stock == null || stock.isBlank() ? DEFAULT_STOCK : stock;
        for (String part : text.split(",")) {
            String s = part.strip().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) continue;
            int price = -1, count = -1;
            Matcher m = PRICE.matcher(s);
            if (m.matches()) {
                s = m.group(1).strip();
                price = Math.min(100000, Integer.parseInt(m.group(2)));
            }
            m = HOW_MANY.matcher(s);
            if (m.matches()) {
                s = m.group(1).strip();
                count = Math.min(999, Integer.parseInt(m.group(2)));
            }
            if (s.equals("cards") || s.equals("card")) {
                cards += count < 0 ? 3 : count;
                continue;
            }
            if (s.startsWith("card:")) {
                String cid = s.substring(5).strip().replace(' ', '_');
                Card c = CardBook.get().card(cid);
                if (c == null) c = byName(s.substring(5).strip());
                if (c != null) named.add(new Offer(null, c.id(), price >= 0 ? price : c.price(), count < 0 ? 1 : count));
                continue;
            }
            Item item = Item.parse(s.replace(' ', '_'));
            if (item == null) item = Item.parse(s);
            if (item == null) continue;
            items.add(new Offer(item, null, price >= 0 ? price : worth(item), count));
        }
        this.classCards = Math.min(12, cards);
    }

    private static Card byName(String name) {
        for (Card c : CardBook.get().cards()) if (c.name().equalsIgnoreCase(name)) return c;
        return null;
    }

    public int id() { return id; }

    public String name() { return name; }

    public Vec3 position() { return position; }

    public double heading() { return heading; }

    /** Whether it takes what she sells. */
    public boolean buys() { return buys; }

    /** Whether she, standing at {@code at}, can reach it. */
    public boolean inReach(Vec3 at) {
        return position.horizontalDistance(at) <= REACH && Math.abs(at.y() - position.y()) <= 1.6;
    }

    /**
     * What it sells to someone of {@code heroClass} (null: of none): its
     * items, then its cards - the named ones, then that class's.
     */
    public List<Offer> stock(String heroClass) {
        List<Offer> out = new ArrayList<>(items);
        out.addAll(named);
        out.addAll(cardsOf(heroClass));
        return out;
    }

    private List<Offer> cardsOf(String heroClass) {
        if (classCards <= 0 || heroClass == null || heroClass.isBlank()) return List.of();     // (no class: no deck)
        String key = heroClass;
        return cardsFor.computeIfAbsent(key, k -> {
            List<Card> pool = new ArrayList<>();
            CardBook book = CardBook.get();
            for (Card c : book.cardsOf(k)) if (c.source() == Card.Source.SHOP) pool.add(c);
            for (Card c : book.cardsOf(null)) if (c.source() == Card.Source.SHOP) pool.add(c);
            // the same few each time she comes as this class: chosen from the shop's own number
            Collections.shuffle(pool, new Random(id * 31L + k.hashCode()));
            List<Offer> out = new ArrayList<>();
            for (int i = 0; i < Math.min(classCards, pool.size()); i++) {
                Card c = pool.get(i);
                out.add(new Offer(null, c.id(), c.price(), 1));
            }
            return out;
        });
    }

    /**
     * Buy {@code offer} for {@code player}: its price from her purse, the
     * item into her inventory (a card onto her discard pile).
     */
    public Deal buy(Offer offer, Player player) {
        if (offer == null) return Deal.NOTHING;
        if (offer.soldOut()) return Deal.SOLD_OUT;
        if (player.coins() < offer.price) return Deal.POOR;
        if (offer.card != null) {
            Hero hero = player.hero();
            if (hero == null || hero.heroClass() == null) return Deal.NOTHING;
            player.spend(offer.price);
            hero.gainCard(offer.card);
        } else {
            Inventory inv = player.inventory();
            if (!inv.hasRoomFor(offer.item)) return Deal.NO_ROOM;
            player.spend(offer.price);
            inv.put(offer.item, 1, true);
        }
        if (offer.left > 0) offer.left--;
        return Deal.DONE;
    }

    /** What it gives for {@code item} ({@code 0}: it does not take it). */
    public int offerFor(Item item) {
        if (!buys || item == null || item.def() == Supplies.COINS) return 0;
        return Math.max(1, worth(item) / 2);
    }

    /** Sell one of what is in {@code player}'s slot {@code slot}: it goes, its coins come. */
    public Deal sell(Player player, int slot) {
        Inventory inv = player.inventory();
        Item item = inv.get(slot);
        if (item == null) return Deal.NOTHING;
        int pay = offerFor(item);
        if (pay <= 0) return Deal.WONT_BUY;
        inv.takeOne(slot);
        player.addCoins(pay);
        return Deal.DONE;
    }

    /**
     * What {@code item} is worth in coins: a supply its set price; a
     * picking a coin or two; anything else by its rarity - a weapon with
     * tiers more for each tier it has been taken up.
     */
    public static int worth(Item item) {
        if (item == null) return 0;
        ItemDef d = item.def();
        Goods.Good good = Goods.of(d);
        if (good != null) return good.price();
        if (d == Supplies.UPGRADE_INGOT) return 40;
        if (d == Supplies.RESHUFFLE_SCROLL) return 25;
        if (d == Supplies.LARGE_CRYSTAL) return 6;
        if (d == Supplies.COINS) return 1;
        if (Supplies.isAmmo(d)) return 3;
        if (d.isFood()) {
            // a picking, a cut, a hide: a coin or two, more the rarer it is
            int base = d.edible() ? 2 : 4;
            return base * switch (item.rarity()) {
                case COMMON -> 1;
                case UNCOMMON -> 2;
                case RARE -> 5;
                default -> 10;
            };
        }
        // a weapon, a garment, a piece of tack: by how rare it is (each step a little over twice the last, as
        // the potions, rods and packs are priced), half again with a border (venom, mystic, bonded)
        int base = switch (item.rarity()) {
            case COMMON -> 40;
            case UNCOMMON -> 90;
            case RARE -> 180;
            case EPIC -> 380;
            case LEGENDARY -> 800;
            case MYTHIC -> 2000;
        };
        if (item.ring() != null) base = base * 3 / 2;
        if (d.isWearable()) base = base * 3 / 4;
        if (d.isTack()) base = Math.max(base, 30);
        if (item.forged() != null) base += 20 * item.forged().level();
        return base;
    }
}
