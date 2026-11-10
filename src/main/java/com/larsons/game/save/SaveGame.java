package com.larsons.game.save;

import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.util.Json;
import com.larsons.game.world.Inventory;
import com.larsons.game.world.Item;
import com.larsons.game.world.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One playthrough, kept in one of the save slots ({@link Saves}): who she
 * is (her name, people, size, how she looks, her class), what she has (her
 * inventory and how many slots it has, her coins, her cards, experience and
 * perks), <b>where she is on the overworld map</b> and which of its
 * destinations she has cleared, how her quests stand, and whatever else the
 * world has to remember of her (doors unlocked, chests opened: {@link
 * #flags}).
 *
 * <p>The game is saved as a Mario game is: on the map. Coming back to the
 * map from a destination writes her down as she is; choosing a destination
 * loads its level at its spawn.
 *
 * <pre>
 * {"version": 1, "name": "Larson", "people": "human", "size": 1.0, "created": "...", "saved": "...",
 *  "seconds": 5400, "wardrobe": {...}, "hero": {"class": "mage", "xp": 40, ...},
 *  "inventory": {"size": 25, "selected": 0, "slots": [{"at": 0, "item": "sword:crystal", "amount": 1}]},
 *  "coins": 15, "health": 100, "map": "main_lands", "node": "harbour", "cleared": ["harbour"],
 *  "quests": {...}, "flags": {...}}
 * </pre>
 */
public final class SaveGame {

    public static final int VERSION = 1;

    /** The peoples she can be of ({@code enemy/People} has what each looks like). */
    public static final List<String> PEOPLES = List.of("human", "drurinean", "half_drurinean", "khorigale");
    /** How small and how tall she can be, times a person's usual height. */
    public static final double SMALLEST = 0.7, TALLEST = 1.3;

    private String name = "Traveller";
    private String people = "human";
    private double size = 1;
    private String created = Instant.now().toString(), saved = created;
    private double seconds;
    private Wardrobe wardrobe = new Wardrobe();
    private Map<String, Object> hero = new LinkedHashMap<>();
    private int inventorySize = Inventory.DEFAULT_SLOTS, selected;
    private final List<Stack> items = new ArrayList<>();
    private int coins;
    private double health = Player.MAX_HEALTH;
    private String map = "", node = "";
    private final Set<String> cleared = new LinkedHashSet<>();
    private Map<String, Object> quests = new LinkedHashMap<>();
    private final Map<String, String> flags = new LinkedHashMap<>();

    /** A slot of her inventory: what is in it and how many. */
    public record Stack(int at, String item, int amount) {}

    public String name() { return name; }

    public void setName(String n) {
        name = n == null || n.isBlank() ? "Traveller" : n.strip();
    }

    /** Her people: one of {@link #PEOPLES}. */
    public String people() { return people; }

    public void setPeople(String p) {
        people = PEOPLES.contains(p) ? p : "human";
    }

    /** Her height, times a person's usual ({@link #SMALLEST} to {@link #TALLEST}). */
    public double size() { return size; }

    public void setSize(double s) {
        size = Math.max(SMALLEST, Math.min(TALLEST, s));
    }

    public String created() { return created; }

    public String saved() { return saved; }

    /** How long she has been played, seconds. */
    public double seconds() { return seconds; }

    public void played(double dt) {
        seconds += Math.max(0, dt);
    }

    public Wardrobe wardrobe() { return wardrobe; }

    public void setWardrobe(Wardrobe w) {
        wardrobe = w == null ? new Wardrobe() : w;
    }

    /** Her class's id ("" before one is chosen). */
    public String heroClass() {
        return String.valueOf(hero.getOrDefault("class", ""));
    }

    /** Her level, as last written. */
    public int level() {
        return hero.get("level") instanceof Number n ? n.intValue() : 1;
    }

    public void setHeroClass(String cls) {
        hero = new LinkedHashMap<>();
        hero.put("class", cls == null ? "" : cls);
    }

    public int coins() { return coins; }

    public void setCoins(int n) {
        coins = Math.max(0, n);
    }

    public int inventorySize() { return inventorySize; }

    public void setInventorySize(int n) {
        inventorySize = Math.max(Inventory.HOTBAR, n);
    }

    public List<Stack> items() { return items; }

    /** The overworld map she is on, and the destination she stands at ("" : its start). */
    public String map() { return map; }

    public String node() { return node; }

    public void setPlace(String map, String node) {
        this.map = map == null ? "" : map;
        this.node = node == null ? "" : node;
    }

    /** The destinations she has cleared (ids, of whatever map). */
    public Set<String> cleared() { return cleared; }

    public Map<String, Object> quests() { return quests; }

    public void setQuests(Map<String, Object> q) {
        quests = q == null ? new LinkedHashMap<>() : q;
    }

    /** What the world remembers of her: a door unlocked, a chest opened, a thing said. */
    public Map<String, String> flags() { return flags; }

    // --- her, to and from the world ----------------------------------------------------

    /** Write {@code p} down as she is: what she carries, her purse, her health, her cards. */
    public void take(Player p) {
        Inventory inv = p.inventory();
        inventorySize = inv.size();
        selected = inv.selected();
        items.clear();
        for (int i = 0; i < inv.size(); i++) {
            Item it = inv.get(i);
            if (it != null) items.add(new Stack(i, it.key(), Math.max(1, inv.amount(i))));
        }
        coins = p.coins();
        health = p.health() <= 0 ? Player.MAX_HEALTH : p.health();
        hero = p.hero().json();
    }

    /**
     * Make {@code p} her: her inventory as it was (an item the game no
     * longer has is left out), her purse, her health, her class and cards.
     * {@code p}'s inventory must already have {@link #inventorySize} slots.
     */
    public void give(Player p) {
        Inventory inv = p.inventory();
        for (int i = 0; i < inv.size(); i++) inv.set(i, null);
        for (Stack s : items) {
            Item it = Item.parse(s.item());
            if (it == null) continue;
            if (s.at() >= 0 && s.at() < inv.size() && inv.get(s.at()) == null) inv.set(s.at(), it, s.amount());
            else inv.put(it, s.amount(), true);
        }
        inv.select(Math.max(0, Math.min(Inventory.HOTBAR - 1, selected)));
        p.setCoins(coins);
        p.setHealth(health <= 0 ? Player.MAX_HEALTH : health);
        p.hero().read(hero, p);
    }

    // --- the file ----------------------------------------------------------------------

    public Map<String, Object> json() {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("version", (double) VERSION);
        j.put("name", name);
        j.put("people", people);
        j.put("size", size);
        j.put("created", created);
        j.put("saved", saved);
        j.put("seconds", seconds);
        j.put("wardrobe", wardrobe.toJson());
        j.put("hero", hero);
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("size", (double) inventorySize);
        inv.put("selected", (double) selected);
        List<Object> slots = new ArrayList<>();
        for (Stack s : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", (double) s.at());
            m.put("item", s.item());
            m.put("amount", (double) s.amount());
            slots.add(m);
        }
        inv.put("slots", slots);
        j.put("inventory", inv);
        j.put("coins", (double) coins);
        j.put("health", health);
        j.put("map", map);
        j.put("node", node);
        j.put("cleared", new ArrayList<Object>(cleared));
        j.put("quests", quests);
        j.put("flags", new LinkedHashMap<String, Object>(flags));
        return j;
    }

    @SuppressWarnings("unchecked")
    public static SaveGame parse(Map<String, Object> j) {
        SaveGame s = new SaveGame();
        if (j == null) return s;
        s.setName(Json.str(j, "name", "Traveller"));
        s.setPeople(Json.str(j, "people", "human"));
        s.setSize(Json.num(j, "size", 1));
        s.created = Json.str(j, "created", s.created);
        s.saved = Json.str(j, "saved", s.created);
        s.seconds = Json.num(j, "seconds", 0);
        if (j.get("wardrobe") instanceof Map<?, ?> w) s.wardrobe = Wardrobe.fromJson((Map<String, Object>) w);
        if (j.get("hero") instanceof Map<?, ?> h) s.hero = new LinkedHashMap<>((Map<String, Object>) h);
        if (j.get("inventory") instanceof Map<?, ?> m) {
            Map<String, Object> inv = (Map<String, Object>) m;
            s.setInventorySize((int) Json.num(inv, "size", Inventory.DEFAULT_SLOTS));
            s.selected = (int) Json.num(inv, "selected", 0);
            if (inv.get("slots") instanceof List<?> slots) {
                for (Object o : slots) {
                    if (!(o instanceof Map<?, ?> sm)) continue;
                    Map<String, Object> slot = (Map<String, Object>) sm;
                    String item = Json.str(slot, "item", "");
                    if (!item.isEmpty()) {
                        s.items.add(new Stack((int) Json.num(slot, "at", -1), item,
                                Math.max(1, (int) Json.num(slot, "amount", 1))));
                    }
                }
            }
        }
        s.coins = Math.max(0, (int) Json.num(j, "coins", 0));
        s.health = Json.num(j, "health", Player.MAX_HEALTH);
        s.map = Json.str(j, "map", "");
        s.node = Json.str(j, "node", "");
        if (j.get("cleared") instanceof List<?> c) for (Object o : c) s.cleared.add(String.valueOf(o));
        if (j.get("quests") instanceof Map<?, ?> q) s.quests = new LinkedHashMap<>((Map<String, Object>) q);
        if (j.get("flags") instanceof Map<?, ?> f) {
            for (Map.Entry<?, ?> e : f.entrySet()) s.flags.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        return s;
    }

    public static SaveGame load(Path file) throws IOException {
        return parse(Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8))));
    }

    /** Write her to {@code file} (through a file beside it, so a save cut short never spoils the one before). */
    public void save(Path file) throws IOException {
        saved = Instant.now().toString();
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path part = file.resolveSibling(file.getFileName() + ".part");
        Files.writeString(part, Json.stringify(json()), StandardCharsets.UTF_8);
        Files.move(part, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** "1 h 30 min", "12 min". */
    public String playedFor() {
        long m = Math.round(seconds / 60);
        return m >= 60 ? m / 60 + " h " + m % 60 + " min" : m + " min";
    }
}
