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
 */
public final class Wardrobe {

    private final EnumMap<Slot, String> items = new EnumMap<>(Slot.class);

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

    public Wardrobe copy() {
        Wardrobe w = new Wardrobe();
        w.items.putAll(items);
        return w;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Slot, String> e : items.entrySet()) out.put(e.getKey().key(), e.getValue());
        return out;
    }

    public static Wardrobe fromJson(Map<String, Object> json) {
        Wardrobe w = new Wardrobe();
        if (json == null) return w;
        for (Map.Entry<String, Object> e : json.entrySet()) {
            Slot slot = Slot.byKey(e.getKey());
            if (slot != null && e.getValue() instanceof String s) w.set(slot, s);
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
