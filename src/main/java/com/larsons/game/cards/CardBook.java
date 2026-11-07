package com.larsons.game.cards;

import com.larsons.game.util.Json;
import com.larsons.game.world.Buffs;
import com.larsons.game.world.Rarity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every card there is, the nine classes and their starting decks, and each
 * class's perk tree: {@code assets/cards/cards.json} (first written by
 * {@code tools/cards/make_cards.py}; Creative Mode's Cards tool edits it and
 * saves it again).
 *
 * <pre>
 *   {"format": 1, "drawAtStart": 4, "handMost": 8, "drawEverySeconds": 300,
 *    "classes": {"mage": {"name": "Mage", "colour": "#5d86fa", "pointer": "wand", "about": "...",
 *                         "deck": [{"card": "cast", "copies": 4}, ...]}, ...},
 *    "cards": [{"id": "cast", "name": "Cast", "class": "mage", "rarity": "common",
 *               "text": "Spells cost half the mana for 5 minutes.",
 *               "effects": [{"kind": "mana_cost", "value": 0.5, "seconds": 300}],
 *               "price": 30, "source": "start"}, ...],
 *    "perks": {"mage": [{"id": "mage_perk_arcane_focus", "name": "Arcane Focus", "row": 0, "cost": 1,
 *                        "card": "arcane_focus"},
 *                       {"id": "mage_deep_reserves", "name": "Deep Reserves", "row": 0, "cost": 1,
 *                        "passive": [{"kind": "regen_mana", "value": 0.3}]}, ...]}}
 * </pre>
 */
public final class CardBook {

    /** One of the classes: its name, colour, mouse pointer, what it is about, and its starting deck. */
    public record Hero(String id, String name, int rgb, String pointer, String about, List<Copies> deck) {
        public Hero {
            deck = deck == null ? List.of() : List.copyOf(deck);
        }

        public Hero withDeck(List<Copies> d) { return new Hero(id, name, rgb, pointer, about, d); }
    }

    /** So many copies of a card in a starting deck. */
    public record Copies(String card, int copies) {}

    /**
     * A node of a class's perk tree: what it is called, its row (0 at the
     * top: a node needs one of the row above it first), its price in perk
     * points, and what it gives - a card into her deck, or helps for good.
     */
    public record Perk(String id, String name, String text, int row, int cost, String card,
                       List<Card.Effect> passive) {
        public Perk {
            text = text == null ? "" : text;
            passive = passive == null ? List.of() : List.copyOf(passive);
        }
    }

    public static final String FOLDER = "cards", FILE = "cards.json";

    private static Path file = Path.of("assets", FOLDER, FILE);
    private static CardBook loaded;

    private final Map<String, Hero> heroes = new LinkedHashMap<>();
    private final Map<String, Card> cards = new LinkedHashMap<>();
    private final Map<String, List<Perk>> perks = new LinkedHashMap<>();
    private int drawAtStart = 4, handMost = 8;
    private double drawEvery = 300;

    /** The book, read on first asking (an empty one when the file is missing). */
    public static synchronized CardBook get() {
        if (loaded == null) {
            try {
                loaded = Files.isRegularFile(file) ? parse(Files.readString(file, StandardCharsets.UTF_8))
                        : new CardBook();
            } catch (IOException | RuntimeException e) {
                loaded = new CardBook();
            }
        }
        return loaded;
    }

    /** Read from {@code f} from now on (tests). */
    public static synchronized void useFile(Path f) {
        file = f;
        loaded = null;
    }

    /** Write the book back to its file. */
    public static synchronized void save(CardBook book) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, Json.stringify(book.json()) + "\n", StandardCharsets.UTF_8);
        loaded = book;
    }

    public int drawAtStart() { return drawAtStart; }

    public int handMost() { return handMost; }

    public double drawEvery() { return drawEvery; }

    public List<Hero> heroes() { return List.copyOf(heroes.values()); }

    public Hero hero(String id) { return id == null ? null : heroes.get(id); }

    public List<Card> cards() { return List.copyOf(cards.values()); }

    public Card card(String id) { return id == null ? null : cards.get(id); }

    /** {@code hero}'s cards (null: those of no class), in the book's order. */
    public List<Card> cardsOf(String hero) {
        List<Card> out = new ArrayList<>();
        for (Card c : cards.values()) if (java.util.Objects.equals(c.hero(), hero)) out.add(c);
        return out;
    }

    /** {@code hero}'s perk tree, top row first. */
    public List<Perk> perks(String hero) {
        return perks.getOrDefault(hero, List.of());
    }

    public Perk perk(String hero, String id) {
        for (Perk p : perks(hero)) if (p.id().equals(id)) return p;
        return null;
    }

    /** {@code hero}'s starting deck, a card id for each copy. */
    public List<String> startingDeck(String hero) {
        List<String> out = new ArrayList<>();
        Hero h = hero(hero);
        if (h == null) return out;
        for (Copies c : h.deck()) for (int i = 0; i < c.copies(); i++) if (cards.containsKey(c.card())) out.add(c.card());
        return out;
    }

    // --- editing (Creative Mode's Cards tool) ---------------------------------------

    /** {@code c} in place of the card of its id, or added at the end. */
    public void put(Card c) {
        cards.put(c.id(), c);
    }

    public void remove(String id) {
        cards.remove(id);
        for (Map.Entry<String, Hero> e : heroes.entrySet()) {
            List<Copies> d = new ArrayList<>(e.getValue().deck());
            d.removeIf(k -> k.card().equals(id));
            e.setValue(e.getValue().withDeck(d));
        }
    }

    public void putHero(Hero h) {
        heroes.put(h.id(), h);
    }

    public void putPerks(String hero, List<Perk> tree) {
        perks.put(hero, List.copyOf(tree));
    }

    /** A free id for a card called {@code name}. */
    public String freeId(String name) {
        String base = name == null ? "card" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "card";
        String id = base;
        for (int i = 2; cards.containsKey(id); i++) id = base + "_" + i;
        return id;
    }

    // --- the file ------------------------------------------------------------------

    static CardBook parse(String text) {
        CardBook b = new CardBook();
        Map<String, Object> m = Json.asObject(Json.parse(text));
        b.drawAtStart = (int) Json.num(m, "drawAtStart", 4);
        b.handMost = (int) Json.num(m, "handMost", 8);
        b.drawEvery = Json.num(m, "drawEverySeconds", 300);
        Map<String, Object> cls = Json.obj(m, "classes");
        if (cls != null) {
            for (Map.Entry<String, Object> e : cls.entrySet()) {
                Map<String, Object> h = Json.asObject(e.getValue());
                List<Copies> deck = new ArrayList<>();
                if (h.get("deck") instanceof List<?> l) {
                    for (Object o : l) {
                        Map<String, Object> d = Json.asObject(o);
                        deck.add(new Copies(Json.str(d, "card", ""), (int) Json.num(d, "copies", 1)));
                    }
                }
                b.heroes.put(e.getKey(), new Hero(e.getKey(), Json.str(h, "name", e.getKey()),
                        rgb(Json.str(h, "colour", "#ffffff")), Json.str(h, "pointer", "sword"),
                        Json.str(h, "about", ""), deck));
            }
        }
        if (m.get("cards") instanceof List<?> l) {
            for (Object o : l) {
                Map<String, Object> c = Json.asObject(o);
                Rarity r = Rarity.byKey(Json.str(c, "rarity", "common"));
                if (r == null) r = Rarity.COMMON;
                Card card = new Card(Json.str(c, "id", ""), Json.str(c, "name", ""),
                        c.get("class") instanceof String s ? s : null, r, Json.str(c, "text", ""),
                        effects(c.get("effects")), (int) Json.num(c, "price", Card.price(r)),
                        Card.Source.byKey(Json.str(c, "source", "shop")));
                if (!card.id().isEmpty()) b.cards.put(card.id(), card);
            }
        }
        Map<String, Object> pk = Json.obj(m, "perks");
        if (pk != null) {
            for (Map.Entry<String, Object> e : pk.entrySet()) {
                List<Perk> tree = new ArrayList<>();
                if (e.getValue() instanceof List<?> l) {
                    for (Object o : l) {
                        Map<String, Object> p = Json.asObject(o);
                        tree.add(new Perk(Json.str(p, "id", ""), Json.str(p, "name", ""), Json.str(p, "text", ""),
                                (int) Json.num(p, "row", 0), (int) Json.num(p, "cost", 1),
                                p.get("card") instanceof String s ? s : null, effects(p.get("passive"))));
                    }
                }
                b.perks.put(e.getKey(), Collections.unmodifiableList(tree));
            }
        }
        return b;
    }

    static List<Card.Effect> effects(Object o) {
        List<Card.Effect> out = new ArrayList<>();
        if (!(o instanceof List<?> l)) return out;
        for (Object x : l) {
            Map<String, Object> e = Json.asObject(x);
            out.add(new Card.Effect(Json.str(e, "kind", "heal"), Json.num(e, "value", 0), Json.num(e, "seconds", 0),
                    (int) Json.num(e, "charges", 0), Buffs.Only.byKey(Json.str(e, "only", "any"))));
        }
        return out;
    }

    Map<String, Object> json() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", 1.0);
        m.put("drawAtStart", (double) drawAtStart);
        m.put("handMost", (double) handMost);
        m.put("drawEverySeconds", drawEvery);
        Map<String, Object> cls = new LinkedHashMap<>();
        for (Hero h : heroes.values()) {
            Map<String, Object> hm = new LinkedHashMap<>();
            hm.put("name", h.name());
            hm.put("colour", String.format(Locale.ROOT, "#%06x", h.rgb()));
            hm.put("pointer", h.pointer());
            hm.put("about", h.about());
            List<Object> deck = new ArrayList<>();
            for (Copies c : h.deck()) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("card", c.card());
                d.put("copies", (double) c.copies());
                deck.add(d);
            }
            hm.put("deck", deck);
            cls.put(h.id(), hm);
        }
        m.put("classes", cls);
        List<Object> cs = new ArrayList<>();
        for (Card c : cards.values()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", c.id());
            cm.put("name", c.name());
            cm.put("class", c.hero());
            cm.put("rarity", c.rarity().key());
            cm.put("text", c.text());
            List<Object> fx = new ArrayList<>();
            for (Card.Effect e : c.effects()) fx.add(e.json());
            cm.put("effects", fx);
            cm.put("price", (double) c.price());
            cm.put("source", c.source().key());
            cs.add(cm);
        }
        m.put("cards", cs);
        Map<String, Object> pk = new LinkedHashMap<>();
        for (Map.Entry<String, List<Perk>> e : perks.entrySet()) {
            List<Object> tree = new ArrayList<>();
            for (Perk p : e.getValue()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("id", p.id());
                pm.put("name", p.name());
                if (!p.text().isEmpty()) pm.put("text", p.text());
                pm.put("row", (double) p.row());
                pm.put("cost", (double) p.cost());
                if (p.card() != null) pm.put("card", p.card());
                if (!p.passive().isEmpty()) {
                    List<Object> fx = new ArrayList<>();
                    for (Card.Effect x : p.passive()) fx.add(x.json());
                    pm.put("passive", fx);
                }
                tree.add(pm);
            }
            pk.put(e.getKey(), tree);
        }
        m.put("perks", pk);
        return m;
    }

    private static int rgb(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }
}
