package com.larsons.game.input;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which keys, mouse buttons and gamepad inputs each {@link Action} is: up to
 * {@link #SLOTS} on the keyboard and mouse and as many on the gamepad, the
 * defaults until the player changes them (Pause, Controls), kept in {@code
 * config/controls.json}.
 *
 * <p>One input does one thing: binding it to an action takes it away from
 * whatever had it, on the same device.
 */
public final class Bindings {

    /** How many binds an action has on each device. */
    public static final int SLOTS = 2;

    private final Map<Action, List<Bind>> keys = new EnumMap<>(Action.class);
    private final Map<Action, List<Bind>> pads = new EnumMap<>(Action.class);

    public Bindings() {
        reset();
    }

    /** Every action back to the keys and buttons it comes with. */
    public void reset() {
        for (Action a : Action.values()) {
            keys.put(a, new ArrayList<>(a.defaultKeys()));
            pads.put(a, new ArrayList<>(a.defaultPads()));
        }
    }

    /** One device's binds back to what they come with. */
    public void reset(boolean pad) {
        for (Action a : Action.values()) {
            (pad ? pads : keys).put(a, new ArrayList<>(pad ? a.defaultPads() : a.defaultKeys()));
        }
    }

    /** The keys and mouse buttons of {@code a}. */
    public List<Bind> keys(Action a) { return keys.get(a); }

    /** The gamepad inputs of {@code a}. */
    public List<Bind> pads(Action a) { return pads.get(a); }

    /** {@code a}'s binds on the gamepad ({@code pad}) or the keyboard and mouse. */
    public List<Bind> of(Action a, boolean pad) { return pad ? pads.get(a) : keys.get(a); }

    /** The action {@code bind} is, or null. */
    public Action actionOf(Bind bind) {
        if (bind == null) return null;
        Map<Action, List<Bind>> map = bind.onPad() ? pads : keys;
        for (Map.Entry<Action, List<Bind>> e : map.entrySet()) if (e.getValue().contains(bind)) return e.getKey();
        return null;
    }

    /**
     * Make {@code bind} slot {@code slot} of {@code a}: it is taken from
     * whatever action had it. Returns the action it was taken from (null:
     * none, or {@code a} itself - or it was not taken at all: the only key
     * of an action that must have one).
     */
    public Action set(Action a, int slot, Bind bind) {
        if (bind == null) return null;
        Map<Action, List<Bind>> map = bind.onPad() ? pads : keys;
        // the last key of an action that must have one stays its, unless it has another to fall back to
        Action holder = actionOf(bind);
        if (holder != null && holder != a && holder.required() && !bind.onPad() && map.get(holder).size() == 1) {
            boolean spare = false;
            for (Bind d : holder.defaultKeys()) spare |= !d.equals(bind) && actionOf(d) == null;
            if (!spare) return null;
        }
        Action had = null;
        for (Map.Entry<Action, List<Bind>> e : map.entrySet()) {
            if (e.getValue().remove(bind) && e.getKey() != a) had = e.getKey();
        }
        List<Bind> list = map.get(a);
        if (slot >= 0 && slot < list.size()) list.set(slot, bind);
        else if (list.size() < SLOTS) list.add(bind);
        else list.set(SLOTS - 1, bind);
        // an action that must have a key gets its own back when its last was taken
        if (had != null && had.required() && !bind.onPad() && map.get(had).isEmpty()) {
            for (Bind d : had.defaultKeys()) if (actionOf(d) == null) map.get(had).add(d);
        }
        return had;
    }

    /** Take slot {@code slot} of {@code a} away ({@code pad}: on the gamepad); false when it must keep it. */
    public boolean clear(Action a, int slot, boolean pad) {
        List<Bind> list = of(a, pad);
        if (slot < 0 || slot >= list.size()) return false;
        if (!pad && a.required() && list.size() == 1) return false;
        list.remove(slot);
        return true;
    }

    /** Whether everything is as it comes. */
    public boolean isDefault() {
        for (Action a : Action.values()) {
            if (!keys.get(a).equals(a.defaultKeys()) || !pads.get(a).equals(a.defaultPads())) return false;
        }
        return true;
    }

    // --- the file ----------------------------------------------------------------------

    /** As JSON: each action's binds, by its key. */
    public Map<String, Object> json() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Action a : Action.values()) {
            List<Object> all = new ArrayList<>();
            for (Bind b : keys.get(a)) all.add(b.save());
            for (Bind b : pads.get(a)) all.add(b.save());
            out.put(a.key(), all);
        }
        return out;
    }

    /** Take up what {@link #json} wrote: an action not in it keeps what it comes with. */
    public void read(Map<String, Object> j) {
        reset();
        if (j == null) return;
        for (Action a : Action.values()) {
            if (!(j.get(a.key()) instanceof List<?> list)) continue;
            List<Bind> k = new ArrayList<>(), p = new ArrayList<>();
            for (Object o : list) {
                Bind b = Bind.parse(String.valueOf(o));
                if (b == null) continue;
                List<Bind> into = b.onPad() ? p : k;
                if (into.size() < SLOTS && !into.contains(b)) into.add(b);
            }
            if (k.isEmpty() && a.required()) k.addAll(a.defaultKeys());
            keys.put(a, k);
            pads.put(a, p);
        }
    }

    /** The bindings in {@code file}, or the defaults when there is none (or it cannot be read). */
    public static Bindings load(Path file) {
        Bindings b = new Bindings();
        if (file != null && Files.isRegularFile(file)) {
            try {
                b.read(Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8))));
            } catch (IOException | RuntimeException e) {
                System.err.println("[controls] ignoring " + file + ": " + e.getMessage());
            }
        }
        return b;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, Json.stringify(json()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[controls] cannot save: " + e.getMessage());
        }
    }
}
