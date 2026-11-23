package com.larsons.game.world;

import com.larsons.game.sprite.Slot;
import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The Tailor: what every piece of clothing and armour gives at each of its
 * tiers ({@link Tier#armour}, {@link Tier#clothing}) - more health, mana or
 * stamina, each filling faster, a share of each blow drawn back, a higher
 * jump, a second jump, a sooner heavy attack, more speed ({@link
 * Gear.Effect}) - so that upgrading a shirt or a helmet with ingots makes it
 * give more. Set piece by piece and tier by tier in Creative Mode's
 * <b>Tailor</b>, kept for every level, one file a piece in {@code
 * assets/tailor/}:
 *
 * <pre>
 * {"format": 1, "item": "iron_helmet",
 *  "tiers": [{"tier": "crafted", "effects": {}},
 *            {"tier": "honed", "name": "Blued iron helmet", "effects": {"health": 30}}, ...]}
 * </pre>
 *
 * <p>A piece the Tailor never touched gives what its kind does by default
 * ({@link #theme}): nothing as it is found, and one effect that grows with
 * each tier's rarity - a helmet more health, a hat more mana, boots more
 * speed, gloves a sooner heavy attack (a Mythic tier five times what an
 * Uncommon one gives). Each tier says all it gives; it is not added to the
 * tiers below. Pieces made in the Forge's Tailor ({@link Forged}) have tiers
 * and effects of their own instead.
 */
public final class Tailor {

    private Tailor() {}

    public static final String FOLDER = "tailor";
    static final int FORMAT = 1;

    /** One tier of a piece: its own name ("": the tier's, "Fine baggy shirt"), and what it gives. */
    public record Rung(String name, Map<Gear.Effect, Double> gear) {

        public static final Rung NONE = new Rung("", Map.of());

        public Rung {
            name = name == null ? "" : name.strip();
            gear = clean(gear);
        }

        public Rung withName(String n) {
            return new Rung(n, gear);
        }

        /** With {@code e} at {@code amount} (0 or less: without it). */
        public Rung withGear(Gear.Effect e, double amount) {
            EnumMap<Gear.Effect, Double> g = new EnumMap<>(Gear.Effect.class);
            g.putAll(gear);
            if (amount <= 0) g.remove(e);
            else g.put(e, amount);
            return new Rung(name, g);
        }

        public Rung withGear(Map<Gear.Effect, Double> g) {
            return new Rung(name, g);
        }

        /** What it gives, in short: "+20 max health, +1/s stamina" ("" for nothing). */
        public String shortText() {
            List<String> out = new ArrayList<>();
            for (Map.Entry<Gear.Effect, Double> e : gear.entrySet()) out.add(Gear.shortly(e.getKey(), e.getValue()));
            return String.join(", ", out);
        }
    }

    /** What a kind of piece gives more of, a tier up, by default: the effect, and how much a rarity. */
    public record Theme(Gear.Effect effect, double each) {}

    /** {@code g} with every amount kept within its effect's least and most, and none at 0. */
    static Map<Gear.Effect, Double> clean(Map<Gear.Effect, Double> g) {
        EnumMap<Gear.Effect, Double> out = new EnumMap<>(Gear.Effect.class);
        if (g != null) {
            for (Map.Entry<Gear.Effect, Double> e : g.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) continue;
                out.put(e.getKey(), e.getKey().onOff() ? 1.0 : e.getKey().clamp(e.getValue()));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    // --- the pieces -------------------------------------------------------------------

    /** Every piece the Tailor sets: the armour, then the clothes - each with its tiers. */
    public static List<ItemDef> pieces() {
        List<ItemDef> out = new ArrayList<>();
        for (String id : Tier.armour().keySet()) out.add(Wearables.byId(id));
        for (String id : Tier.clothing().keySet()) out.add(Wearables.byId(id));
        return List.copyOf(out);
    }

    /** Whether the Tailor sets {@code d}: a piece of armour or clothing, with tiers. */
    public static boolean tailors(ItemDef d) {
        return d != null && (Tier.armour().containsKey(d.id()) || Tier.clothing().containsKey(d.id()));
    }

    /** {@code d}'s tiers, lowest first (none: it is not the Tailor's). */
    public static List<Tier> tiers(ItemDef d) {
        if (!tailors(d)) return List.of();
        return Tier.row(d.id()).tiers();
    }

    /** What {@code d} gives at tier {@code t}: as the Tailor set it, else its kind's default. */
    public static Map<Gear.Effect, Double> gear(ItemDef d, Tier t) {
        return rung(d, t).gear();
    }

    /**
     * {@code d}'s tier {@code t}: its name and what it gives - as the Tailor
     * set it, else its kind's default. A piece with no tier ({@code t} null)
     * is taken at the tier of its rarity, else its lowest.
     */
    public static synchronized Rung rung(ItemDef d, Tier t) {
        if (!tailors(d)) return Rung.NONE;
        List<Tier> tiers = tiers(d);
        if (t == null || !tiers.contains(t)) return Rung.NONE;
        Map<Tier, Rung> own = edited().get(d.id());
        if (own != null) {
            Rung r = own.get(t);
            return r != null ? r : Rung.NONE;
        }
        return new Rung("", defaults(d, t));
    }

    /** {@code d}'s tiers, lowest first, each as {@link #rung}. */
    public static List<Rung> rungs(ItemDef d) {
        List<Rung> out = new ArrayList<>();
        for (Tier t : tiers(d)) out.add(rung(d, t));
        return out;
    }

    /** The name the Tailor gave {@code item}'s tier {@code t}, or null (none given: the tier's own). */
    public static synchronized String name(String item, Tier t) {
        if (item == null || t == null) return null;
        Map<Tier, Rung> own = edited().get(item);
        if (own == null) return null;
        Rung r = own.get(t);
        return r == null || r.name().isEmpty() ? null : r.name();
    }

    /** Whether {@code d} was set in the Tailor (not its kind's default). */
    public static synchronized boolean edited(ItemDef d) {
        return d != null && edited().containsKey(d.id());
    }

    // --- the defaults -----------------------------------------------------------------

    private static final Map<String, Map<Gear.Effect, Double>> DEFAULTS = new ConcurrentHashMap<>();

    /**
     * What {@code d} gives at tier {@code t} unless the Tailor says
     * otherwise: nothing at the tier it is found in; above it, its {@link
     * #theme}'s effect, as much as the tier's rarity says (Uncommon once,
     * Rare twice ... Mythic five times).
     */
    public static Map<Gear.Effect, Double> defaults(ItemDef d, Tier t) {
        if (!tailors(d) || t == null) return Map.of();
        return DEFAULTS.computeIfAbsent(d.id() + ":" + t.key(), k -> {
            List<Tier> tiers = tiers(d);
            Theme th = theme(d);
            if (th == null || tiers.isEmpty() || tiers.get(0) == t || !tiers.contains(t)) return Map.of();
            return clean(Map.of(th.effect(), th.each() * t.rarity().ordinal()));
        });
    }

    /**
     * What a kind of piece gives more of by default, a tier up: the armour
     * more health (the torso's most), gauntlets and bracers a share of each
     * blow back as health, sabatons more stamina; hats more mana, eyewear and
     * necklaces mana back faster, earrings mana from each blow; shirts and
     * belts more stamina, trousers stamina back faster, shoes more speed,
     * gloves and the sheath a sooner heavy attack, wrist bands stamina from
     * each blow, underwear and mantles health back faster - and the wings a
     * higher jump. Null for what the Tailor does not set.
     */
    public static Theme theme(ItemDef d) {
        if (!tailors(d)) return null;
        switch (d.id()) {
            case "robe" -> { return new Theme(Gear.Effect.MANA, 10); }
            case "clockwork_wings", "gilded_wings" -> { return new Theme(Gear.Effect.JUMP, 10); }
            case "leather_backpack" -> { return new Theme(Gear.Effect.STAMINA, 10); }
            case "scarf" -> { return new Theme(Gear.Effect.STAMINA_REGEN, 1); }
            default -> { }
        }
        List<Slot> slots = d.wearable().slots();
        Slot slot = slots.isEmpty() ? Slot.OTHER : slots.get(0);
        if (Armour.sides(d) > 0) {
            Armour.Location at = Armour.location(slot);
            if (at == null) return new Theme(Gear.Effect.HEALTH, 10);
            return switch (at) {
                case HEAD, LEGS -> new Theme(Gear.Effect.HEALTH, 10);
                case TORSO -> new Theme(Gear.Effect.HEALTH, 15);
                case ARMS -> new Theme(Gear.Effect.HEALTH_ABSORB, 2);
                case FEET -> new Theme(Gear.Effect.STAMINA, 10);
            };
        }
        return switch (slot) {
            case HAT -> new Theme(Gear.Effect.MANA, 10);
            case EYEWEAR, NECKLACE -> new Theme(Gear.Effect.MANA_REGEN, 0.5);
            case EARRINGS -> new Theme(Gear.Effect.MANA_ABSORB, 2);
            case SHIRT, BELT -> new Theme(Gear.Effect.STAMINA, 10);
            case PANTS -> new Theme(Gear.Effect.STAMINA_REGEN, 1);
            case SHOES -> new Theme(Gear.Effect.SPEED, 5);
            case GLOVES, SHEATH -> new Theme(Gear.Effect.HEAVY_COOLDOWN, 5);
            case WRISTWEAR, WRISTWEAR_LEFT -> new Theme(Gear.Effect.STAMINA_ABSORB, 2);
            default -> new Theme(Gear.Effect.HEALTH_REGEN, 0.5);
        };
    }

    // --- helpers for the editor ---------------------------------------------------------

    /**
     * {@code rungs} (a piece's, lowest first) with every tier above {@code
     * from} giving what {@code from} gives, grown ({@code grow}) as the
     * tiers climb - as much again of each for every tier, counted from the
     * first (its second tier's +10 is +15 at the third, +20 at the fourth;
     * its first tier's +10 is +20 at the second) - or the same ({@code grow}
     * false). Their names are kept.
     */
    public static List<Rung> upTheTiers(List<Rung> rungs, int from, boolean grow) {
        List<Rung> out = new ArrayList<>(rungs);
        if (from < 0 || from >= rungs.size()) return out;
        Map<Gear.Effect, Double> base = rungs.get(from).gear();
        for (int j = from + 1; j < rungs.size(); j++) {
            EnumMap<Gear.Effect, Double> g = new EnumMap<>(Gear.Effect.class);
            for (Map.Entry<Gear.Effect, Double> e : base.entrySet()) {
                Gear.Effect fx = e.getKey();
                double v = e.getValue();
                if (grow && !fx.onOff()) v = from == 0 ? v * (j + 1) : v * j / from;
                g.put(fx, snap(fx, v));
            }
            out.set(j, rungs.get(j).withGear(g));
        }
        return out;
    }

    /** {@code v} to {@code e}'s step, within its least and most. */
    static double snap(Gear.Effect e, double v) {
        double s = e.step();
        double r = s > 0 ? Math.round(v / s) * s : v;
        return e.clamp(Math.max(e.min(), r));
    }

    // --- the files --------------------------------------------------------------------

    private static Path dir = Path.of("assets", FOLDER);
    private static Map<String, Map<Tier, Rung>> edited;

    /** Keep them in {@code folder} from now on (tests: a folder of their own). */
    public static synchronized void useDir(Path folder) {
        dir = folder;
        edited = null;
    }

    private static Map<String, Map<Tier, Rung>> edited() {
        if (edited == null) edited = readAll(dir);
        return edited;
    }

    /** Read the files again (another copy of the game may have written them). */
    public static synchronized void reload() {
        edited = null;
    }

    /** Keep {@code d}'s tiers as {@code rungs} (lowest first, one a tier). */
    public static synchronized void save(ItemDef d, List<Rung> rungs) throws IOException {
        if (!tailors(d)) return;
        List<Tier> tiers = tiers(d);
        Map<Tier, Rung> own = new LinkedHashMap<>();
        for (int i = 0; i < tiers.size(); i++) own.put(tiers.get(i), i < rungs.size() ? rungs.get(i) : Rung.NONE);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(d.id() + ".json"), json(d.id(), own), StandardCharsets.UTF_8);
        edited().put(d.id(), Collections.unmodifiableMap(own));
    }

    /** Keep {@code d}'s tier {@code t} as {@code r}, its other tiers as they are now. */
    public static synchronized void save(ItemDef d, Tier t, Rung r) throws IOException {
        List<Tier> tiers = tiers(d);
        List<Rung> rungs = new ArrayList<>(rungs(d));
        int i = tiers.indexOf(t);
        if (i < 0) return;
        rungs.set(i, r);
        save(d, rungs);
    }

    /** Throw away what the Tailor set for {@code d}: it gives its kind's default again. */
    public static synchronized void reset(ItemDef d) throws IOException {
        if (d == null) return;
        Files.deleteIfExists(dir.resolve(d.id() + ".json"));
        edited().remove(d.id());
    }

    static String json(String item, Map<Tier, Rung> rungs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", FORMAT);
        m.put("item", item);
        List<Object> tiers = new ArrayList<>();
        for (Map.Entry<Tier, Rung> e : rungs.entrySet()) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("tier", e.getKey().key());
            if (!e.getValue().name().isEmpty()) tm.put("name", e.getValue().name());
            Map<String, Object> g = new LinkedHashMap<>();
            for (Map.Entry<Gear.Effect, Double> x : e.getValue().gear().entrySet()) g.put(x.getKey().key(), x.getValue());
            tm.put("effects", g);
            tiers.add(tm);
        }
        m.put("tiers", tiers);
        return Json.stringify(m) + "\n";
    }

    /** A piece's tiers read back from its file's {@code text}. */
    static Map<Tier, Rung> parse(String text) {
        Map<String, Object> m = Json.asObject(Json.parse(text));
        Map<Tier, Rung> out = new LinkedHashMap<>();
        if (m.get("tiers") instanceof List<?> l) {
            for (Object o : l) {
                Map<String, Object> tm = Json.asObject(o);
                Tier t = Tier.byKey(Json.str(tm, "tier", ""));
                if (t == null) continue;
                EnumMap<Gear.Effect, Double> g = new EnumMap<>(Gear.Effect.class);
                Map<String, Object> gm = Json.obj(tm, "effects");
                if (gm != null) {
                    for (Map.Entry<String, Object> x : gm.entrySet()) {
                        Gear.Effect e = Gear.Effect.byKey(x.getKey());
                        if (e != null && x.getValue() instanceof Number n) g.put(e, n.doubleValue());
                    }
                }
                out.put(t, new Rung(Json.str(tm, "name", ""), g));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, Map<Tier, Rung>> readAll(Path folder) {
        Map<String, Map<Tier, Rung>> out = new ConcurrentHashMap<>();
        if (!Files.isDirectory(folder)) return out;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path p : (Iterable<Path>) files.sorted()::iterator) {
                String n = p.getFileName().toString();
                if (!n.endsWith(".json")) continue;
                try {
                    String text = Files.readString(p, StandardCharsets.UTF_8);
                    String item = Json.str(Json.asObject(Json.parse(text)), "item", n.substring(0, n.length() - 5));
                    if (tailors(Wearables.byId(item))) out.put(item, parse(text));
                } catch (IOException | RuntimeException ex) {
                    System.err.println("[tailor] ignoring " + p + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            System.err.println("[tailor] cannot list " + folder + ": " + ex.getMessage());
        }
        return out;
    }
}
