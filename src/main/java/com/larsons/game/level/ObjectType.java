package com.larsons.game.level;

import java.util.List;
import java.util.function.Function;

/**
 * A kind of thing a {@link Level} can hold ({@link LevelObject#type()}):
 * what the editor calls it and files it under, what it keeps beside where it
 * stands ({@link Prop}s, edited in the editor's properties panel), how big it
 * is to click on, and what it becomes in the world when the level is played
 * ({@link LevelWorld.Spawner}).
 *
 * <p>Every kind is registered in {@link ObjectTypes}; a new system - NPCs
 * and their dialogue, quest givers, enemies and their summoning stations,
 * sound emitters, biome zones - plugs into the editor and the level files by
 * registering its kind there, with its props and its spawner.
 *
 * @param key      how it is written in a level's file
 * @param label    what the editor calls it
 * @param category where the editor's palette files it
 * @param props    what it keeps beside where it stands, in the order the editor shows them
 * @param radius   metres round its spot it takes up, to click on and to ring when selected
 * @param height   metres it stands, to click on
 * @param colour   its marker's colour in the editor ({@code 0xRRGGBB}), for kinds with no sprite of their own
 * @param single   whether a level has at most one (placing another moves it)
 * @param spawner  what it becomes in the world when the level is played; null: nothing (an editor's mark)
 */
public record ObjectType(String key, String label, Category category, List<Prop> props, double radius, double height,
                         int colour, boolean single, LevelWorld.Spawner spawner) {

    /** The prop called {@code key}, or null. */
    public Prop prop(String key) {
        for (Prop p : props) if (p.key().equals(key)) return p;
        return null;
    }

    /** Where the editor's palette files a kind. */
    public enum Category {
        SCENERY("Scenery"),
        ITEMS("Weapons"),
        WEARABLES("Wearables"),
        LOOT("Chests"),
        COMBAT("Training"),
        TERRAIN("Terrain"),
        MARKERS("Markers");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() { return label; }
    }

    /**
     * Something a kind keeps beside where it stands: a number, a line of
     * text, one of a list of choices, or on/off.
     *
     * @param key      how it is written in the file
     * @param label    what the properties panel calls it
     * @param kind     what sort of value it is
     * @param fallback its value until it is set
     * @param min      a number's least
     * @param max      a number's most
     * @param step     how much a number goes up or down a click
     * @param choices  a choice's options, given the object it is for (an item's tiers depend on the item)
     */
    public record Prop(String key, String label, Kind kind, Object fallback, double min, double max, double step,
                       Function<LevelObject, List<String>> choices) {

        public enum Kind { NUMBER, TEXT, CHOICE, TOGGLE }

        public static Prop number(String key, String label, double fallback, double min, double max, double step) {
            return new Prop(key, label, Kind.NUMBER, fallback, min, max, step, null);
        }

        public static Prop text(String key, String label, String fallback) {
            return new Prop(key, label, Kind.TEXT, fallback, 0, 0, 0, null);
        }

        public static Prop choice(String key, String label, String fallback, Function<LevelObject, List<String>> c) {
            return new Prop(key, label, Kind.CHOICE, fallback, 0, 0, 0, c);
        }

        public static Prop toggle(String key, String label, boolean fallback) {
            return new Prop(key, label, Kind.TOGGLE, fallback, 0, 0, 0, null);
        }

        /** {@code o}'s value of it, or its fallback. */
        public Object value(LevelObject o) {
            Object v = o.props().get(key);
            return v == null ? fallback : v;
        }
    }
}
