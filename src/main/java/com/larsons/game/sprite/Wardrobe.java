package com.larsons.game.sprite;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a character is wearing and carrying: one item name (a folder under
 * {@code assets/sprites/<slot>/}) per {@link Slot}, or nothing.
 *
 * <p>An empty {@link Slot#BODY} means "the fallback body". An empty cosmetic
 * slot means that layer is not drawn at all — cosmetics without sheets are
 * left blank, as the design asks.
 *
 * <p>The wardrobe also has a {@link Style}: the whole character drawn from the
 * 512-pixel renders, or from the pixel art made of them.
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

        public static Style byKey(String key) {
            for (Style s : values()) if (s.key.equalsIgnoreCase(key)) return s;
            return null;
        }

        /** Whether {@code item} is some item's version in a style (listed under that item). */
        public static boolean isVersion(String item) {
            for (Style s : values()) if (!s.suffix.isEmpty() && item.endsWith(s.suffix)) return true;
            return false;
        }
    }

    private final EnumMap<Slot, String> items = new EnumMap<>(Slot.class);
    private Style style = Style.RENDERED;

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

    public Style style() { return style; }

    public void setStyle(Style style) {
        this.style = style == null ? Style.RENDERED : style;
    }

    public Wardrobe copy() {
        Wardrobe w = new Wardrobe();
        w.items.putAll(items);
        w.style = style;
        return w;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Slot, String> e : items.entrySet()) out.put(e.getKey().key(), e.getValue());
        if (style != Style.RENDERED) out.put("style", style.key());
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
