package com.larsons.game.actor;

import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Wearables;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * How the people she meets are dressed: a {@link Wardrobe} for one of a
 * {@link People}, of either body, in one of a few ways of dressing ({@link
 * Dress}) - put together from the tailored clothes ({@link
 * Wearables#tailored()}), the armour and the hair styles, the same way every
 * time for the same {@code seed} (a level's person keeps her look).
 *
 * <p>Nothing here touches the sprites: a folder a body has no sheets for is
 * simply not drawn.
 */
public final class Looks {

    /** A way of dressing. */
    public enum Dress {
        /** As its people usually dress. */
        OWN("own", "As its people do"),
        COMMON("common", "Common"),
        ELEGANT("elegant", "Elegant"),
        WORKSHOP("workshop", "Workshop"),
        TOWN("town", "Townsfolk"),
        GUARD("guard", "Guard"),
        ARMOURED("armoured", "Armoured"),
        RAGGED("ragged", "Ragged");

        private final String key, label;

        Dress(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() { return key; }

        public String label() { return label; }

        public static Dress byKey(String key) {
            if (key != null) {
                String k = key.trim().toLowerCase(Locale.ROOT);
                for (Dress d : values()) if (d.key.equals(k) || d.label.equalsIgnoreCase(key.trim())) return d;
            }
            return OWN;
        }

        public static List<String> keys() {
            return java.util.Arrays.stream(values()).map(Dress::key).toList();
        }
    }

    private static final List<String> EYES = List.of("hazel", "ice_blue", "amber_slit");
    private static final List<String> NOSES = List.of("straight", "button", "aquiline");
    private static final List<String> MOUTHS = List.of("full", "heart", "wide");
    private static final List<String> BROWS = List.of("arched", "soft", "thin");
    private static final List<String> HAIR_LONG = List.of("long_waves", "bob", "ponytail", "braids", "curls", "pixie");
    private static final List<String> HAIR_SHORT = List.of("side_part", "crew_cut", "undercut", "pixie", "curls");

    private Looks() {}

    /** The way {@code people} usually dress. */
    public static Dress usual(People people) {
        return switch (people) {
            case DRURINEAN -> Dress.ELEGANT;
            case HALF_DRURINEAN -> Dress.WORKSHOP;
            case KHORIGALE -> Dress.COMMON;
            case RISEN, BORTZAL -> Dress.RAGGED;
            default -> Dress.TOWN;
        };
    }

    /** The hair colours of {@code people}: colour options of the hair. */
    public static List<String> hairColours(People people) {
        return switch (people) {
            case DRURINEAN -> List.of("platinum", "violet_ombre", "electric_blue", "pink", "honey_blonde");
            case HALF_DRURINEAN -> List.of("platinum", "auburn", "copper", "violet_ombre", "black");
            case KHORIGALE -> List.of("black", "auburn", "cherry_red", "emerald", "brown");
            case RISEN -> List.of("platinum", "black", "mint", "brown");
            default -> List.of("brown", "black", "auburn", "copper", "honey_blonde");
        };
    }

    /**
     * One of {@code people}, {@code masculine} or not, dressed {@code dress}'s
     * way: the same for the same {@code seed}.
     */
    public static Wardrobe of(People people, boolean masculine, Dress dress, long seed) {
        Random r = new Random(seed * 0x9E3779B97F4A7C15L + people.ordinal() * 31L + (masculine ? 7 : 0));
        if (dress == null || dress == Dress.OWN) dress = usual(people);
        Wardrobe w = new Wardrobe();
        w.setStyle(Wardrobe.Style.PIXEL_128);
        w.set(Slot.BODY, masculine ? "masculine" : "feminine");
        w.setColour(Slot.BODY, pick(r, people.skins()));
        w.set(Slot.EARS, pick(r, people.ears()));
        w.set(Slot.EYES, people == People.KHORIGALE ? "amber_slit" : people == People.RISEN ? "ice_blue" : pick(r, EYES));
        w.set(Slot.NOSE, pick(r, NOSES));
        w.set(Slot.MOUTH, pick(r, MOUTHS));
        w.set(Slot.EYEBROWS, pick(r, BROWS));
        w.set(Slot.UNDERWEAR, "cotton_briefs");
        if (!masculine) w.set(Slot.BRA, "leather_bralette");
        if (people.horns()) w.set(Slot.HORNS, People.HORNS);
        if (people.tail()) w.set(Slot.TAIL, People.TAIL);
        String hairColour = pick(r, hairColours(people));
        w.set(Slot.HAIR, pick(r, masculine ? HAIR_SHORT : HAIR_LONG));
        w.setColour(Slot.HAIR, hairColour);
        w.setColour(Slot.EYEBROWS, hairColour);
        boolean dressed = !masculine && r.nextInt(3) == 0;
        switch (dress) {
            case ELEGANT -> {
                if (dressed) wear(w, r, "drurin_court_dress", "moonsilk_gown");
                else {
                    wear(w, r, "scholars_shirt");
                    wear(w, r, "silverthread_trousers");
                }
                wear(w, r, "court_slippers");
                if (r.nextBoolean()) wear(w, r, "starweave_mantle");
                if (r.nextInt(3) == 0) wear(w, r, "observatory_gloves");
                if (r.nextInt(4) == 0) wear(w, r, "astronomers_cap");
                if (r.nextBoolean()) metal(w, Slot.NECKLACE, "gold_pendant", r.nextBoolean() ? "silver" : "gold");
            }
            case WORKSHOP -> {
                wear(w, r, "tinkers_shirt");
                wear(w, r, "oilcloth_trousers");
                wear(w, r, "mud_boots", "bog_boots");
                wear(w, r, "brass_buckle_belt");
                if (r.nextBoolean()) wear(w, r, "engineers_gloves");
                if (r.nextInt(3) == 0) wear(w, r, "boilermans_cap");
                if (r.nextInt(3) == 0) wear(w, r, "soot_mantle");
            }
            case TOWN -> {
                if (dressed) wear(w, r, "festival_dress", "market_dress");
                else {
                    wear(w, r, "millers_shirt", "herders_shirt", "guards_shirt");
                    wear(w, r, "field_trousers", "oilcloth_trousers");
                }
                wear(w, r, "mud_boots", "bog_boots");
                if (r.nextInt(3) == 0) wear(w, r, "foresters_hat", "sunbleached_hat");
                if (r.nextInt(4) == 0) wear(w, r, "foresters_mantle");
                if (r.nextBoolean()) wear(w, r, "rope_belt", "brass_buckle_belt");
            }
            case GUARD -> {
                wear(w, r, "guards_shirt");
                wear(w, r, "oilcloth_trousers");
                wear(w, r, "bog_boots");
                w.set(Slot.CHAINMAIL, "chainmail_shirt");
                w.set(Slot.HAT, "iron_helmet");
                w.set(Slot.HAIR, "crew_cut");
                wear(w, r, "brass_buckle_belt");
            }
            case ARMOURED -> {
                boolean steel = r.nextInt(3) == 0;
                String m = steel ? "steel" : "iron";
                w.set(Slot.CHAINMAIL, "chainmail_shirt");
                w.set(Slot.CHAINMAIL_LEGS, "chainmail_pants");
                w.set(Slot.CHESTPLATE, m + "_chestplate");
                if (r.nextBoolean()) w.set(Slot.LEG_PLATING, m + "_leg_plating");
                w.set(Slot.GLOVES, m + "_gauntlets");
                if (r.nextInt(3) != 0) {
                    w.set(Slot.HAT, m + "_helmet");
                    w.set(Slot.HAIR, "crew_cut");
                }
                wear(w, r, "bog_boots");
            }
            case RAGGED -> {
                if (dressed) wear(w, r, "grave_shroud");
                else {
                    if (r.nextInt(4) != 0) wear(w, r, "mouldering_shirt");
                    wear(w, r, "gravediggers_trousers");
                }
                if (r.nextBoolean()) wear(w, r, "bog_boots");
                if (r.nextInt(3) == 0) wear(w, r, "tattered_mantle");
                if (r.nextInt(5) == 0) wear(w, r, "plague_cap");
            }
            default -> {
                if (dressed) wear(w, r, "market_dress");
                else {
                    wear(w, r, "herders_shirt", "millers_shirt");
                    wear(w, r, "field_trousers", "gravediggers_trousers");
                }
                wear(w, r, "mud_boots");
                if (r.nextInt(3) == 0) wear(w, r, "sunbleached_hat");
                if (r.nextInt(3) == 0) wear(w, r, "herders_mantle");
                if (r.nextBoolean()) wear(w, r, "rope_belt");
            }
        }
        // a beanie goes over its own hair
        String hat = w.get(Slot.HAT);
        if ("beanie".equals(hat)) w.set(Slot.HAIR, masculine ? "beanie_short" : "beanie_long");
        return w;
    }

    private static <T> T pick(Random r, List<T> from) {
        return from.get(r.nextInt(from.size()));
    }

    /** Put one of the tailored garments {@code ids} on {@code w}, in its colour. */
    private static void wear(Wardrobe w, Random r, String... ids) {
        ItemDef d = Wearables.byId(ids[r.nextInt(ids.length)]);
        if (d == null || !d.isWearable()) return;
        Slot slot = d.wearable().slots().get(0);
        w.set(slot, d.wearable().folders().get(0));
        String colour = Wearables.tailoredColour(d);
        if (colour != null) w.setColour(slot, colour);
    }

    private static void metal(Wardrobe w, Slot slot, String folder, String colour) {
        w.set(slot, folder);
        w.setColour(slot, colour);
    }
}
