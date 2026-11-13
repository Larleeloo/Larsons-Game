package com.larsons.game.world;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What lies round a level, past its edges: the country it is a piece of,
 * drawn in 3D so the level never ends at a line on the grass. Two rings of
 * it ({@link SurroundGen} makes them, {@link SurroundRenderer} draws them):
 *
 * <ul>
 *   <li><b>The near ring</b> - {@link #near} metres wide all round - real
 *       ground: the level's own ground carried on past its edge (its hills,
 *       its paths and streets, its rivers) and turning, over the first few
 *       tens of metres, into the country of the {@link Theme}, with its trees,
 *       rocks and grass, and in a town or a city its houses along the
 *       streets.</li>
 *   <li><b>The far ring</b> - out to {@link #far} metres - the country on to
 *       the horizon, in simple shapes: the forest's canopy, the mountains
 *       and their snow, the dunes and mesas, the sea, the city's roofs and
 *       towers; low down it fades into the level's fog as everything does,
 *       its heights stand out of it in the {@link #haze}.</li>
 * </ul>
 *
 * <p>Pure data, saved with a level ({@code "surroundings"} in its {@code
 * level.json}) and set in Creative Mode's Sky tool.
 *
 * @param theme   the country round the level ({@link Theme#AUTO}: worked out from what its edges are)
 * @param near    how wide the near ring is, metres (0: as fits the level's size)
 * @param far     how far out the far ring goes past the near one, metres
 * @param density how thick its trees, rocks and houses stand: 0 (none) to 2 (twice as thick)
 * @param height  how high its hills and mountains rise: 0 (flat) to 3 (three times as high)
 * @param seed    which of the endless countries of its theme it is
 * @param haze    how far its distant heights fade into the horizon: 0 (crisp) to 1 (gone)
 * @param carry   whether the level's paths, streets and rivers run on out through it (else it closes over them)
 */
public record Surroundings(Theme theme, double near, double far, double density, double height, int seed,
                           double haze, boolean carry) {

    /** The country round a level. */
    public enum Theme {
        NONE("none", "None: the open plain", "The old look: the flat plain past the edges, fading into the fog."),
        AUTO("auto", "Auto: from the edges", "Worked out from what lies along the level's edges: its ground, its "
                + "trees, its houses, its sea."),
        MEADOW("meadow", "Meadows and farmland", "Rolling grass, flowers and hedgerows, haystacks and fields, "
                + "a few trees; green hills far off."),
        FOREST("forest", "Forest", "Oak, birch and great oaks crowding in, the forest's canopy rolling away over "
                + "the hills."),
        PINES("pines", "Pine forest", "Pines and tall pines on rising ground, dark pointed crowns climbing to "
                + "snowy peaks."),
        JUNGLE("jungle", "Jungle", "Palms, jungle trees and ferns, a bright canopy and steep green towers of "
                + "rock far off in the haze."),
        HILLS("hills", "Highland hills", "Rolling grass hills, rock and standing stones, a few trees in the "
                + "hollows; higher hills behind."),
        MOUNTAINS("mountains", "Mountains", "Rock and scree rising steeply all round, pines low down, great "
                + "peaks with snow on them."),
        SNOW("snow", "Snowfields", "Deep snow, snowy pines and boulders, white mountains."),
        DESERT("desert", "Desert", "Sand dunes, red sandstone mesas and buttes, dead trees and dry grass."),
        SAVANNAH("savannah", "Savannah", "Dry grass plains, acacias and termite mounds, low hills far off."),
        SWAMP("swamp", "Swamp", "Mud and still green pools, willows, reeds and mossy logs, mist over the flats."),
        COAST("coast", "Coast and sea", "The land falls away to a beach and the sea, out to the horizon."),
        WASTE("waste", "Ash waste", "Ash, black spires and dead trees, jagged dark peaks and a smoking "
                + "volcano."),
        CRYSTAL("crystal", "Crystal lands", "Crystal ground, crystal trees and spires, great glowing prisms far "
                + "off."),
        TOWN("town", "Town and fields", "The town's lanes run on between cottages and houses, then fields, "
                + "haystacks and windmills."),
        CITY("city", "City streets", "The city goes on: streets carried on past the edge, rows of houses, shops "
                + "and works along them, roofs and towers to the horizon.");

        private final String key, label, blurb;

        Theme(String key, String label, String blurb) {
            this.key = key;
            this.label = label;
            this.blurb = blurb;
        }

        public String key() { return key; }

        public String label() { return label; }

        /** What it looks like, in a sentence: for the editor. */
        public String blurb() { return blurb; }

        /** Whether it is a country of its own (not none, not worked out). */
        public boolean real() { return this != NONE && this != AUTO; }

        public static Theme byKey(String k) {
            if (k == null) return null;
            String t = k.trim().toLowerCase(Locale.ROOT);
            for (Theme th : values()) if (th.key.equals(t)) return th;
            return null;
        }
    }

    /** The narrowest and widest the near ring may be, metres. */
    public static final double MIN_NEAR = 32, MAX_NEAR = 256;
    /** The nearest and farthest the far ring may reach, metres. */
    public static final double MIN_FAR = 300, MAX_FAR = 4000;

    /** A level's surroundings until it says otherwise: worked out from its edges. */
    public static final Surroundings DEFAULT = new Surroundings(Theme.AUTO, 0, 1600, 1, 1, 1, 0.35, true);
    /** None: the open plain, as levels had before. */
    public static final Surroundings NONE = DEFAULT.withTheme(Theme.NONE);

    public Surroundings {
        if (theme == null) theme = Theme.AUTO;
        near = near <= 0 || Double.isNaN(near) ? 0 : Math.max(MIN_NEAR, Math.min(MAX_NEAR, near));
        far = Double.isNaN(far) ? DEFAULT_FAR : Math.max(MIN_FAR, Math.min(MAX_FAR, far));
        density = Double.isNaN(density) ? 1 : Math.max(0, Math.min(2, density));
        height = Double.isNaN(height) ? 1 : Math.max(0, Math.min(3, height));
        haze = Double.isNaN(haze) ? 0.35 : Math.max(0, Math.min(1, haze));
    }

    private static final double DEFAULT_FAR = 1600;

    /** Whether there is anything round the level (not {@link Theme#NONE}). */
    public boolean any() { return theme != Theme.NONE; }

    /** Whether it is as a level has it when it says nothing. */
    public boolean isDefault() { return equals(DEFAULT); }

    /** The near ring's width round a level {@code size} metres a side: as set, or as fits it (a quarter of it, 48 - 128 m). */
    public double nearFor(double size) {
        if (near > 0) return near;
        return Math.max(48, Math.min(128, Math.round(size * 0.25 / 16) * 16.0));
    }

    public Surroundings withTheme(Theme t) { return new Surroundings(t, near, far, density, height, seed, haze, carry); }

    public Surroundings withNear(double m) { return new Surroundings(theme, m, far, density, height, seed, haze, carry); }

    public Surroundings withFar(double m) { return new Surroundings(theme, near, m, density, height, seed, haze, carry); }

    public Surroundings withDensity(double d) {
        return new Surroundings(theme, near, far, d, height, seed, haze, carry);
    }

    public Surroundings withHeight(double h) {
        return new Surroundings(theme, near, far, density, h, seed, haze, carry);
    }

    public Surroundings withSeed(int s) { return new Surroundings(theme, near, far, density, height, s, haze, carry); }

    public Surroundings withHaze(double h) {
        return new Surroundings(theme, near, far, density, height, seed, h, carry);
    }

    public Surroundings withCarry(boolean c) {
        return new Surroundings(theme, near, far, density, height, seed, haze, c);
    }

    /** As a level's file has it: {@code {"theme": "city", "near": 96, "far": 1600, ...}}. */
    public Map<String, Object> toJson() {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("theme", theme.key());
        if (near > 0) j.put("near", round(near));
        j.put("far", round(far));
        j.put("density", round(density));
        j.put("height", round(height));
        j.put("seed", seed);
        j.put("haze", round(haze));
        if (!carry) j.put("carry", false);
        return j;
    }

    /** Read back from {@link #toJson()}'s map; anything missing as {@link #DEFAULT} has it. */
    public static Surroundings fromJson(Object o) {
        if (o instanceof String s) {
            Theme t = Theme.byKey(s);
            return t == null ? DEFAULT : DEFAULT.withTheme(t);
        }
        if (!(o instanceof Map<?, ?> m)) return DEFAULT;
        Theme t = Theme.byKey(m.get("theme") == null ? null : String.valueOf(m.get("theme")));
        return new Surroundings(t == null ? Theme.AUTO : t, num(m, "near", 0), num(m, "far", DEFAULT_FAR),
                num(m, "density", 1), num(m, "height", 1), (int) num(m, "seed", 1), num(m, "haze", 0.35),
                !Boolean.FALSE.equals(m.get("carry")));
    }

    /**
     * As a script says it: {@code <theme> [near=96] [far=2000] [density=1.2]
     * [height=1.5] [seed=4] [haze=0.3] [carry=false]}, over {@code base};
     * null if there is no such theme.
     */
    public static Surroundings parse(String text, Surroundings base) {
        String[] a = text.trim().split("\\s+");
        Theme th = Theme.byKey(a[0]);
        if (th == null) return null;
        Surroundings s = (base == null ? DEFAULT : base).withTheme(th);
        for (int i = 1; i < a.length; i++) {
            String[] kv = a[i].split("=");
            if (kv.length != 2) continue;
            try {
                switch (kv[0]) {
                    case "near" -> s = s.withNear(Double.parseDouble(kv[1]));
                    case "far" -> s = s.withFar(Double.parseDouble(kv[1]));
                    case "density" -> s = s.withDensity(Double.parseDouble(kv[1]));
                    case "height" -> s = s.withHeight(Double.parseDouble(kv[1]));
                    case "seed" -> s = s.withSeed(Integer.parseInt(kv[1]));
                    case "haze" -> s = s.withHaze(Double.parseDouble(kv[1]));
                    case "carry" -> s = s.withCarry(Boolean.parseBoolean(kv[1]));
                    default -> { }
                }
            } catch (NumberFormatException e) {
                // a word that is not a number: left as it was
            }
        }
        return s;
    }

    private static double num(Map<?, ?> m, String k, double dflt) {
        return m.get(k) instanceof Number n ? n.doubleValue() : dflt;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
