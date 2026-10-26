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
 * <p>The wardrobe also has a {@link Style}: the whole character drawn from the
 * 512-pixel renders, or from the pixel art made of them; a colour per slot
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
        RENDERED("rendered", "", "512 px"),
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
    public static final Set<String> DRESSES = Set.of("laced_dress");

    private final EnumMap<Slot, String> items = new EnumMap<>(Slot.class);
    private final EnumMap<Slot, String> colours = new EnumMap<>(Slot.class);
    /** The colours dyes have unlocked: {@code "<slot>/<item>"} to its colour options. */
    private final Map<String, Set<String>> unlocked = new TreeMap<>();
    private Style style = Style.RENDERED;
    private Hand hand = Hand.AUTO;

    public String get(Slot slot) {
        return items.get(slot);
    }

    public void set(Slot slot, String item) {
        if (item == null || item.isBlank()) items.remove(slot);
        else items.put(slot, item);
    }

    public void clear(Slot slot) {
        items.remove(slot);
    }

    public boolean wearing(Slot slot, String item) {
        return item != null && item.equals(items.get(slot));
    }

    /**
     * Whether {@code slot}'s layer is left out because what is worn covers
     * it: the wrists under gauntlets, the necklace under a chestplate, the
     * underwear and the pants under mail leggings, the bra and a shirt (not a
     * dress) under a mail shirt. What is in the slot stays in the wardrobe -
     * it shows again once the armour comes off.
     */
    public boolean hidden(Slot slot) {
        return switch (slot) {
            case WRISTWEAR, WRISTWEAR_LEFT -> {
                String gloves = items.get(Slot.GLOVES);
                yield gloves != null && GAUNTLETS.contains(gloves);
            }
            case NECKLACE -> items.containsKey(Slot.CHESTPLATE);
            case UNDERWEAR, PANTS -> items.containsKey(Slot.CHAINMAIL_LEGS);
            case BRA -> items.containsKey(Slot.CHAINMAIL);
            case SHIRT -> items.containsKey(Slot.CHAINMAIL) && !DRESSES.contains(items.get(Slot.SHIRT));
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
        if (slot == Slot.SHIRT && DRESSES.contains(items.get(Slot.SHIRT)) && items.containsKey(Slot.CHAINMAIL)) {
            return Slot.CHAINMAIL;
        }
        return null;
    }

    public Style style() { return style; }

    public void setStyle(Style style) {
        this.style = style == null ? Style.RENDERED : style;
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
        for (Map.Entry<String, Set<String>> e : unlocked.entrySet()) w.unlocked.put(e.getKey(), new TreeSet<>(e.getValue()));
        w.style = style;
        w.hand = hand;
        return w;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Slot, String> e : items.entrySet()) out.put(e.getKey().key(), e.getValue());
        if (style != Style.RENDERED) out.put("style", style.key());
        if (hand != Hand.AUTO) out.put("hand", hand.key());
        if (!colours.isEmpty()) {
            Map<String, Object> c = new LinkedHashMap<>();
            for (Map.Entry<Slot, String> e : colours.entrySet()) c.put(e.getKey().key(), e.getValue());
            out.put("colours", c);
        }
        if (!unlocked.isEmpty()) {
            Map<String, Object> u = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> e : unlocked.entrySet()) u.put(e.getKey(), new ArrayList<>(e.getValue()));
            out.put("unlocked", u);
        }
        return out;
    }

    public static Wardrobe fromJson(Map<String, Object> json) {
        Wardrobe w = new Wardrobe();
        if (json == null) return w;
        for (Map.Entry<String, Object> e : json.entrySet()) {
            Slot slot = Slot.byKey(e.getKey());
            if (slot != null && e.getValue() instanceof String s) w.set(slot, s);
        }
        if (json.get("style") instanceof String s) w.setStyle(Style.byKey(s));
        if (json.get("hand") instanceof String s) w.setHand(Hand.byKey(s));
        if (json.get("colours") instanceof Map<?, ?> c) {
            for (Map.Entry<?, ?> e : c.entrySet()) {
                Slot slot = Slot.byKey(String.valueOf(e.getKey()));
                if (slot != null && e.getValue() instanceof String s) w.setColour(slot, s);
            }
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
