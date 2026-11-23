package com.larsons.game.sprite;

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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What a character is wearing and carrying: one item name (a folder under
 * {@code assets/sprites/<slot>/}) per {@link Slot}, or nothing.
 *
 * <p>An empty {@link Slot#BODY} means "the fallback body". An empty cosmetic
 * slot means that layer is not drawn at all — cosmetics without sheets are
 * left blank, as the design asks.
 *
 * <p>The wardrobe also has a {@link Style}: the whole character drawn in the
 * 128-pixel pixel art or the 64-pixel; a colour per slot
 * (an option of the item's {@code variants.json} - the body's colour is the
 * skin tone of every layer); and a {@link Hand}: right- or left-handed, or
 * whichever hand the sword is in.
 *
 * <p>Armour covers some of what is drawn over it, so those layers are left
 * out while it is worn ({@link #hidden}): gauntlets hide the wrists - a bangle
 * would be drawn over the gauntlet's cuff - and a chestplate the necklace.
 * Mail is drawn close to the body, so what is worn under it would poke out
 * round it: mail leggings hide the underwear and the pants, a mail shirt the
 * bra and the shirt - but not a dress, whose skirt hangs below the mail; the
 * dress is cut at the mail's edge instead ({@link #cutBy}).
 *
 * <p>The armour can be put out of sight ({@link #armourHidden}, the
 * wardrobe's switch): it is still worn - it rolls its dice, it is listed -
 * but none of it is drawn, and so nothing it would cover is hidden either.
 *
 * <p>And it remembers the colours dyes have unlocked, item by item ({@link
 * #unlocked}): a dye is used up to unlock its colour on one item for good.
 */
public final class Wardrobe {

    /**
     * How the items are drawn. A style is an ending of the item's folder name:
     * {@code hat/straw_sun_hat_px64} is the 64-pixel pixel art of {@code
     * hat/straw_sun_hat}. An item without a version in the style is drawn as
     * it is, and the items themselves keep their names (the sword in hand is
     * still {@code sword}), so a style is one switch for the whole character.
     */
    public enum Style {
        PIXEL_128("px128", "_px128", "Pixel 128"),
        PIXEL_64("px64", "_px64", "Pixel 64");

        private final String key, suffix, label;

        Style(String key, String suffix, String label) {
            this.key = key;
            this.suffix = suffix;
            this.label = label;
        }

        public String key() { return key; }

        public String label() { return label; }

        /** The folder name of {@code item} in this style. */
        public String folder(String item) {
            return item + suffix;
        }

        /** ... for the left-handed character: {@code sword_px128_lh}. */
        public String leftFolder(String item) {
            return item + suffix + LEFT_SUFFIX;
        }

        /** The style called {@code key}; null for one there is none of (the 512-pixel renders are gone). */
        public static Style byKey(String key) {
            for (Style s : values()) if (s.key.equalsIgnoreCase(key)) return s;
            return null;
        }

        /** Whether {@code item} is some item's version in a style (listed under that item). */
        public static boolean isVersion(String item) {
            if (item.endsWith(LEFT_SUFFIX)) return true;
            for (Style s : values()) if (!s.suffix.isEmpty() && item.endsWith(s.suffix)) return true;
            return false;
        }

        /** The item a style version belongs to: {@code sword_px128_lh} is {@code sword}'s. */
        public static String base(String item) {
            String s = item.endsWith(LEFT_SUFFIX) ? item.substring(0, item.length() - LEFT_SUFFIX.length()) : item;
            for (Style st : values()) {
                if (!st.suffix.isEmpty() && s.endsWith(st.suffix)) return s.substring(0, s.length() - st.suffix.length());
            }
            return s;
        }
    }

    /** The ending of a style version drawn for the left-handed character. */
    public static final String LEFT_SUFFIX = "_lh";

    /**
     * Which hand leads. A left-handed character is drawn as the right-handed
     * one in a mirror (its attack swings with the left arm); {@code AUTO} is
     * left-handed while the sword is in the left hand and not in the right.
     */
    public enum Hand {
        AUTO("auto", "Auto"), RIGHT("right", "Right"), LEFT("left", "Left");

        private final String key, label;

        Hand(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() { return key; }

        public String label() { return label; }

        public static Hand byKey(String key) {
            for (Hand h : values()) if (h.key.equalsIgnoreCase(key)) return h;
            return null;
        }
    }

    /** The item that makes an {@link Hand#AUTO} character left-handed when it is in the left hand. */
    public static final String WEAPON = "sword";

    /** The gloves that are gauntlets: worn, nothing is drawn on the wrists. */
    public static final Set<String> GAUNTLETS = Set.of("iron_gauntlets", "steel_gauntlets");
    /** The shirts that are dresses: under a mail shirt the skirt still shows ({@link #cutBy}). */
    public static final Set<String> DRESSES = Set.of("laced_dress", "robe", "frock_coat");
    /** The necklaces that lie on the chest, under a chestplate; a gorget, a choker or a scarf goes over it. */
    public static final Set<String> LOW_NECKLACES = Set.of("gold_pendant", "amulet");
    /**
     * The armour's folders, in whatever slot they are worn (world/Armour's
     * dice, by folder): out of sight while the armour is hidden ({@link #armourHidden}).
     */
    public static final Set<String> ARMOUR = Set.of(
            "leather_cap", "chain_coif", "kettle_hat", "horned_helm", "iron_helmet", "steel_helmet", "great_helm",
            "gorget", "gambeson", "leather_jerkin", "chainmail_shirt", "scale_mail", "iron_chestplate",
            "steel_chestplate", "leather_bracers", "iron_gauntlets", "steel_gauntlets", "leather_greaves",
            "chainmail_pants", "iron_leg_plating", "steel_leg_plating", "sabatons");

    private final EnumMap<Slot, String> items = new EnumMap<>(Slot.class);
    private final EnumMap<Slot, String> colours = new EnumMap<>(Slot.class);
    /**
     * What each slot's folder is worn as, where its folder and colour alone
     * would not say: the wearable's id ({@code bronze_helmet}, {@code
     * moonsilk_gown}) or a forged piece's ({@code forged:<id>}) - so a dye
     * can colour it and it stays what it is ({@link #id}).
     */
    private final EnumMap<Slot, String> ids = new EnumMap<>(Slot.class);
    /**
     * The clothes worn under the armour in a slot the two share (a hat under
     * a helmet, a necklace under a gorget, a shirt under a gambeson ...):
     * worn - what they do still counts - but not drawn while the armour is
     * ({@link #get(Slot, boolean)}), and drawn when it is hidden ({@link #drawn}).
     */
    private final EnumMap<Slot, String> under = new EnumMap<>(Slot.class);
    private final EnumMap<Slot, String> underColours = new EnumMap<>(Slot.class);
    private final EnumMap<Slot, String> underIds = new EnumMap<>(Slot.class);
    /** Goes up with every change: what is drawn from it is made again ({@link #drawn}). */
    private int version;
    private Wardrobe drawn;
    private int drawnVersion = -1;
    /** The colours dyes have unlocked: {@code "<slot>/<item>"} to its colour options. */
    private final Map<String, Set<String>> unlocked = new TreeMap<>();
    private Style style = Style.PIXEL_128;
    private Hand hand = Hand.AUTO;
    /** Whether the armour worn is left undrawn ({@link #hidden}): the wardrobe's switch. Still worn all the same. */
    private boolean armourHidden;

    public String get(Slot slot) {
        return items.get(slot);
    }

    public void set(Slot slot, String item) {
        if (item != null && item.isBlank()) item = null;
        // (another folder is another thing: what it was worn as goes)
        if (!java.util.Objects.equals(items.get(slot), item)) ids.remove(slot);
        if (item == null) items.remove(slot);
        else items.put(slot, item);
        version++;
    }

    public void clear(Slot slot) {
        set(slot, null);
    }

    // --- armour over clothes -------------------------------------------------------------

    /** Whether {@code folder} is armour ({@link #ARMOUR}): it goes over the clothes of its slot. */
    public static boolean isArmour(String folder) {
        return in(ARMOUR, folder);
    }

    /**
     * What is worn in {@code slot} as armour ({@code armour}) - its top layer
     * when that is armour - or as clothes: the top layer when it is not
     * armour, else what is worn under the armour. Null: nothing.
     */
    public String get(Slot slot, boolean armour) {
        String top = items.get(slot);
        boolean plated = isArmour(top);
        if (armour) return plated ? top : null;
        return plated ? under.get(slot) : top;
    }

    /** The colour of what {@link #get(Slot, boolean)} gives. */
    public String colour(Slot slot, boolean armour) {
        boolean plated = isArmour(items.get(slot));
        if (armour) return plated ? colours.get(slot) : null;
        return plated ? underColours.get(slot) : colours.get(slot);
    }

    /** What {@link #get(Slot, boolean)} gives is worn as ({@link #id}), or null. */
    public String id(Slot slot, boolean armour) {
        boolean plated = isArmour(items.get(slot));
        if (armour) return plated ? ids.get(slot) : null;
        return plated ? underIds.get(slot) : ids.get(slot);
    }

    /**
     * Wear {@code folder} in {@code slot} as armour or as clothes, the other
     * left on: armour goes over the clothes there (they are kept under it),
     * clothes go under the armour there. Null takes that one off - the
     * armour's clothes come back on top. Its colour is kept for the same
     * folder, and what it is worn as goes for another.
     */
    public void wear(Slot slot, boolean armour, String folder) {
        if (folder != null && folder.isBlank()) folder = null;
        String top = items.get(slot);
        boolean plated = isArmour(top);
        if (armour) {
            if (folder == null) {
                if (!plated) return;
                // off with the armour: the clothes under it come back up
                String u = under.remove(slot), uc = underColours.remove(slot), ui = underIds.remove(slot);
                items.remove(slot);
                colours.remove(slot);
                ids.remove(slot);
                if (u != null) {
                    items.put(slot, u);
                    if (uc != null) colours.put(slot, uc);
                    if (ui != null) ids.put(slot, ui);
                }
            } else if (plated) {
                set(slot, folder);
            } else {
                // the clothes there go under it
                if (top != null) {
                    under.put(slot, top);
                    put(underColours, slot, colours.get(slot));
                    put(underIds, slot, ids.get(slot));
                }
                items.put(slot, folder);
                colours.remove(slot);
                ids.remove(slot);
            }
        } else if (plated) {
            if (!java.util.Objects.equals(under.get(slot), folder)) underIds.remove(slot);
            if (folder == null) {
                under.remove(slot);
                underColours.remove(slot);
            } else {
                under.put(slot, folder);
            }
        } else {
            set(slot, folder);
        }
        version++;
    }

    /** Put {@code colour} on what {@link #get(Slot, boolean)} gives (null: its own colours). */
    public void setColour(Slot slot, boolean armour, String colour) {
        boolean plated = isArmour(items.get(slot));
        if (armour && !plated) return;
        if (!armour && plated) put(underColours, slot, colour);
        else setColour(slot, colour);
        version++;
    }

    /** Say what {@link #get(Slot, boolean)} gives is worn as ({@link #id}; null: by its folder and colour). */
    public void setId(Slot slot, boolean armour, String id) {
        boolean plated = isArmour(items.get(slot));
        if (armour && !plated) return;
        if (!armour && plated) put(underIds, slot, id);
        else put(ids, slot, id);
        version++;
    }

    /** What the top layer of {@code slot} is worn as: a wearable's id, {@code forged:<id>}, or null. */
    public String id(Slot slot) {
        return ids.get(slot);
    }

    /** Say what the top layer of {@code slot} is worn as (null: by its folder and colour). */
    public void setId(Slot slot, String id) {
        put(ids, slot, id);
        version++;
    }

    private static void put(EnumMap<Slot, String> map, Slot slot, String v) {
        if (v == null || v.isBlank()) map.remove(slot);
        else map.put(slot, v);
    }

    /**
     * What is drawn of it: itself while the armour shows; with the armour
     * hidden ({@link #armourHidden}), a wardrobe with the clothes under the
     * armour where the armour was, and no armour - so nothing is covered.
     * The same object until it changes.
     */
    public Wardrobe drawn() {
        if (!armourHidden) return this;
        if (drawn != null && drawnVersion == version) return drawn;
        Wardrobe w = copy();
        w.armourHidden = false;
        w.under.clear();
        w.underColours.clear();
        w.underIds.clear();
        for (Slot s : Slot.values()) {
            if (!isArmour(items.get(s))) continue;
            w.items.remove(s);
            w.colours.remove(s);
            w.ids.remove(s);
            String u = under.get(s);
            if (u == null) continue;
            w.items.put(s, u);
            put(w.colours, s, underColours.get(s));
            put(w.ids, s, underIds.get(s));
        }
        drawn = w;
        drawnVersion = version;
        return w;
    }

    /** How many times it has been changed: what is worked out from it is worked out again when this moves on. */
    public int version() {
        return version;
    }

    public boolean wearing(Slot slot, String item) {
        return item != null && item.equals(items.get(slot));
    }

    /**
     * Whether {@code slot}'s layer is left out because what is worn covers
     * it: the wrists under gauntlets, a pendant or an amulet under a chestplate, the
     * underwear and the pants under mail leggings, the bra and a shirt (not a
     * dress) under a mail shirt. What is in the slot stays in the wardrobe -
     * it shows again once the armour comes off. With the armour hidden
     * ({@link #armourHidden}) it is the armour that is left out, and nothing
     * else: what it covered is drawn.
     */
    public boolean hidden(Slot slot) {
        if (armourHidden) return in(ARMOUR, items.get(slot));
        return switch (slot) {
            case WRISTWEAR, WRISTWEAR_LEFT -> {
                String gloves = items.get(Slot.GLOVES);
                yield gloves != null && GAUNTLETS.contains(gloves);
            }
            // (the sets are Set.of: asked about a slot with nothing in it, they would throw - nothing is never covered)
            case NECKLACE -> items.containsKey(Slot.CHESTPLATE) && in(LOW_NECKLACES, items.get(Slot.NECKLACE));
            case UNDERWEAR, PANTS -> items.containsKey(Slot.CHAINMAIL_LEGS);
            case BRA -> items.containsKey(Slot.CHAINMAIL);
            case SHIRT -> items.containsKey(Slot.CHAINMAIL) && items.containsKey(Slot.SHIRT)
                    && !in(DRESSES, items.get(Slot.SHIRT));
            default -> false;
        };
    }

    /**
     * The slot whose layer {@code slot}'s is cut by, or null: a dress under a
     * mail shirt loses what pokes out from under the mail - its collar, its
     * bodice past the mail's sides - and keeps its skirt
     * ({@link SheetImage#cutUnder}).
     */
    public Slot cutBy(Slot slot) {
        if (armourHidden) return null;
        if (slot == Slot.SHIRT && in(DRESSES, items.get(Slot.SHIRT)) && items.containsKey(Slot.CHAINMAIL)) {
            return Slot.CHAINMAIL;
        }
        return null;
    }

    /** Whether {@code item} is one of {@code set}: never when it is nothing (a {@code Set.of} throws if asked). */
    private static boolean in(Set<String> set, String item) {
        return item != null && set.contains(item);
    }

    /** Long hair, and the short cut it is gathered to while swimming ({@link #keptTo}). */
    private static final Map<String, String> GATHERED = Map.of(
            "long_waves", "bob", "braids", "bob", "curls", "bob", "beanie_long", "beanie_short");

    /**
     * The item whose sheet {@code slot}'s layer is kept near in {@code
     * state}, or null: swimming a stroke - flat on the water, head up - long
     * hair, which hangs straight down from the head, would hang through the
     * chest and out under it; it is kept to where a short cut has hair
     * ({@link SheetImage#keepNear}), gathered at the head.
     */
    public String keptTo(Slot slot, AnimState state) {
        if (slot != Slot.HAIR || state != AnimState.SWIM) return null;
        String hair = items.get(Slot.HAIR);
        return hair == null ? null : GATHERED.get(hair);
    }

    public Style style() { return style; }

    public void setStyle(Style style) {
        this.style = style == null ? Style.PIXEL_128 : style;
        version++;
    }

    /**
     * The colour picked for a slot's item (an option of its {@code variants.json},
     * such as {@code royal_blue}), or {@code null} for the item's own colours.
     * The body's colour is the skin tone: it colours the skin of every item
     * that shows skin (ears, nose, the mouth round the lips).
     */
    public String colour(Slot slot) {
        return colours.get(slot);
    }

    public void setColour(Slot slot, String colour) {
        if (colour == null || colour.isBlank()) colours.remove(slot);
        else colours.put(slot, colour);
        version++;
    }

    /** The skin tone: the body's colour. */
    public String skin() {
        return colours.get(Slot.BODY);
    }

    /** Whether a dye has unlocked colour option {@code colour} for {@code item} worn in {@code slot}. */
    public boolean unlocked(Slot slot, String item, String colour) {
        Set<String> s = item == null ? null : unlocked.get(slot.key() + "/" + item);
        return s != null && s.contains(colour);
    }

    /** Unlock colour option {@code colour} for {@code item} worn in {@code slot}, for good. */
    public void unlock(Slot slot, String item, String colour) {
        if (item == null || colour == null || colour.isBlank()) return;
        unlocked.computeIfAbsent(slot.key() + "/" + item, k -> new TreeSet<>()).add(colour);
        version++;
    }

    /** Every colour dyes have unlocked: {@code "<slot>/<item>"} to its colours. */
    public Map<String, Set<String>> unlocks() {
        Map<String, Set<String>> out = new TreeMap<>();
        for (Map.Entry<String, Set<String>> e : unlocked.entrySet()) {
            out.put(e.getKey(), Collections.unmodifiableSet(new TreeSet<>(e.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }

    public Hand hand() { return hand; }

    public void setHand(Hand hand) {
        this.hand = hand == null ? Hand.AUTO : hand;
        version++;
    }

    /** Whether the character is drawn left-handed (see {@link Hand}). */
    public boolean leftHanded() {
        return switch (hand) {
            case LEFT -> true;
            case RIGHT -> false;
            case AUTO -> WEAPON.equals(items.get(Slot.CARRY_LEFT)) && !WEAPON.equals(items.get(Slot.CARRY_RIGHT));
        };
    }

    public Wardrobe copy() {
        Wardrobe w = new Wardrobe();
        w.items.putAll(items);
        w.colours.putAll(colours);
        w.ids.putAll(ids);
        w.under.putAll(under);
        w.underColours.putAll(underColours);
        w.underIds.putAll(underIds);
        for (Map.Entry<String, Set<String>> e : unlocked.entrySet()) w.unlocked.put(e.getKey(), new TreeSet<>(e.getValue()));
        w.style = style;
        w.hand = hand;
        w.armourHidden = armourHidden;
        return w;
    }

    /** Whether the armour worn is out of sight: worn, rolling its dice, but not drawn (nor hiding anything). */
    public boolean armourHidden() { return armourHidden; }

    public void setArmourHidden(boolean on) {
        armourHidden = on;
        version++;
    }

    /**
     * Wear what {@code other} wears, in its colours, its style and its hand -
     * but keep what is in the hands (the carried slots are the weapons').
     */
    public void become(Wardrobe other) {
        for (Slot s : Slot.values()) {
            if (s.carried()) continue;
            set(s, other.get(s));
            setColour(s, other.colour(s));
            put(ids, s, other.ids.get(s));
            put(under, s, other.under.get(s));
            put(underColours, s, other.underColours.get(s));
            put(underIds, s, other.underIds.get(s));
        }
        style = other.style;
        hand = other.hand;
        armourHidden = other.armourHidden;
        version++;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Slot, String> e : items.entrySet()) out.put(e.getKey().key(), e.getValue());
        out.put("style", style.key());
        if (hand != Hand.AUTO) out.put("hand", hand.key());
        if (armourHidden) out.put("armourHidden", true);
        if (!colours.isEmpty()) {
            Map<String, Object> c = new LinkedHashMap<>();
            for (Map.Entry<Slot, String> e : colours.entrySet()) c.put(e.getKey().key(), e.getValue());
            out.put("colours", c);
        }
        putMap(out, "ids", ids);
        putMap(out, "under", under);
        putMap(out, "underColours", underColours);
        putMap(out, "underIds", underIds);
        if (!unlocked.isEmpty()) {
            Map<String, Object> u = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> e : unlocked.entrySet()) u.put(e.getKey(), new ArrayList<>(e.getValue()));
            out.put("unlocked", u);
        }
        return out;
    }

    private static void putMap(Map<String, Object> out, String key, EnumMap<Slot, String> map) {
        if (map.isEmpty()) return;
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<Slot, String> e : map.entrySet()) m.put(e.getKey().key(), e.getValue());
        out.put(key, m);
    }

    private static void readMap(Map<String, Object> json, String key, EnumMap<Slot, String> into) {
        if (!(json.get(key) instanceof Map<?, ?> c)) return;
        for (Map.Entry<?, ?> e : c.entrySet()) {
            Slot slot = Slot.byKey(String.valueOf(e.getKey()));
            if (slot != null && e.getValue() instanceof String s && !s.isBlank()) into.put(slot, s);
        }
    }

    /** What a forged piece was worn in before it had an id of its own: its look, {@code forged:<id>}, as its colour. */
    private static final String FORGED = "forged:";

    public static Wardrobe fromJson(Map<String, Object> json) {
        Wardrobe w = new Wardrobe();
        if (json == null) return w;
        for (Map.Entry<String, Object> e : json.entrySet()) {
            Slot slot = Slot.byKey(e.getKey());
            if (slot != null && e.getValue() instanceof String s) w.set(slot, s);
        }
        if (json.get("style") instanceof String s) w.setStyle(Style.byKey(s));
        if (json.get("hand") instanceof String s) w.setHand(Hand.byKey(s));
        if (json.get("armourHidden") instanceof Boolean b) w.armourHidden = b;
        if (json.get("colours") instanceof Map<?, ?> c) {
            for (Map.Entry<?, ?> e : c.entrySet()) {
                Slot slot = Slot.byKey(String.valueOf(e.getKey()));
                if (slot != null && e.getValue() instanceof String s) w.setColour(slot, s);
            }
        }
        readMap(json, "ids", w.ids);
        readMap(json, "under", w.under);
        readMap(json, "underColours", w.underColours);
        readMap(json, "underIds", w.underIds);
        // (a forged piece saved before: its look was its colour - now what it is worn as, and its colour free to dye)
        for (Slot s : Slot.values()) {
            String c = w.colours.get(s);
            if (c != null && c.startsWith(FORGED)) {
                w.colours.remove(s);
                w.ids.putIfAbsent(s, c);
            }
        }
        // (clothes under something that is not armour: the clothes are the top)
        for (Slot s : Slot.values()) {
            if (!w.under.containsKey(s) || isArmour(w.items.get(s))) continue;
            if (!w.items.containsKey(s)) {
                w.items.put(s, w.under.get(s));
                put(w.colours, s, w.underColours.get(s));
                put(w.ids, s, w.underIds.get(s));
            }
            w.under.remove(s);
            w.underColours.remove(s);
            w.underIds.remove(s);
        }
        if (json.get("unlocked") instanceof Map<?, ?> u) {
            for (Map.Entry<?, ?> e : u.entrySet()) {
                String[] key = String.valueOf(e.getKey()).split("/", 2);
                Slot slot = key.length == 2 ? Slot.byKey(key[0]) : null;
                if (slot == null || !(e.getValue() instanceof List<?> l)) continue;
                for (Object c : l) if (c instanceof String s) w.unlock(slot, key[1], s);
            }
        }
        return w;
    }

    /** Read {@code file}, or an empty wardrobe if it is missing or unreadable. */
    public static Wardrobe load(Path file) {
        if (!Files.isRegularFile(file)) return new Wardrobe();
        try {
            return fromJson(Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8))));
        } catch (IOException | RuntimeException e) {
            System.err.println("[wardrobe] ignoring " + file + ": " + e.getMessage());
            return new Wardrobe();
        }
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.stringify(toJson()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[wardrobe] cannot save " + file + ": " + e.getMessage());
        }
    }
}
