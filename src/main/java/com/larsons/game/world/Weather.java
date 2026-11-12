package com.larsons.game.world;

import java.util.List;
import java.util.Locale;

/**
 * A level's weather: none; by biome - each biome the weather its country
 * has, coming and going in spells (snow over the mountains, sandstorms over
 * the savannah and the waste, rain and thunder over the plains and the
 * jungle, fog over the marsh); or fixed - one weather all the time. Set in
 * Creative Mode's Level tool, on the Time page; {@link Now} is the weather
 * as it is over her.
 *
 * <p>Pure data, and the rules.
 *
 * @param mode     none, by biome, or always the same
 * @param kind     the weather when it is fixed
 * @param strength how heavy it is at most, 0.1 – 1 (light rain to a downpour)
 * @param often    by biome: how much of the time there is weather at all, 0 – 1
 */
public record Weather(Mode mode, Kind kind, double strength, double often) {

    /** Whether a level has weather, and how it is chosen. */
    public enum Mode {
        NONE("none", "None: always clear"), BIOME("biome", "By biome, coming and going"), FIXED("fixed",
                "Always the same");

        private final String key, label;

        Mode(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() { return key; }

        public String label() { return label; }

        public static Mode byKey(String k) {
            for (Mode m : values()) if (m.key.equals(k)) return m;
            return null;
        }
    }

    /** What the weather is. */
    public enum Kind {
        CLEAR("clear", "Clear"), RAIN("rain", "Rain"), THUNDERSTORM("thunderstorm", "Thunderstorm"),
        SNOW("snow", "Snow"), FOG("fog", "Fog"), WIND("wind", "Wind"), SANDSTORM("sandstorm", "Sandstorm");

        private final String key, label;

        Kind(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() { return key; }

        public String label() { return label; }

        /** Whether rain falls in it. */
        public boolean wet() { return this == RAIN || this == THUNDERSTORM; }

        public static Kind byKey(String k) {
            if (k == null) return null;
            for (Kind w : values()) if (w.key.equals(k.toLowerCase(Locale.ROOT))) return w;
            return null;
        }
    }

    /** No weather: clear all the time. */
    public static final Weather NONE = new Weather(Mode.NONE, Kind.RAIN, 0.8, 0.4);
    /** The demo's: by biome, now and then. */
    public static final Weather DEMO = new Weather(Mode.BIOME, Kind.RAIN, 0.8, 0.35);

    public Weather {
        if (mode == null) mode = Mode.NONE;
        if (kind == null || kind == Kind.CLEAR) kind = Kind.RAIN;
        strength = Double.isNaN(strength) ? 0.8 : Math.max(0.1, Math.min(1, strength));
        often = Double.isNaN(often) ? 0.4 : Math.max(0, Math.min(1, often));
    }

    public Weather withMode(Mode m) { return new Weather(m, kind, strength, often); }

    public Weather withKind(Kind k) { return new Weather(mode, k, strength, often); }

    public Weather withStrength(double s) { return new Weather(mode, kind, s, often); }

    public Weather withOften(double o) { return new Weather(mode, kind, strength, o); }

    /** Whether it is as it comes (none): nothing to write. */
    public boolean isDefault() { return equals(NONE); }

    /** The weathers {@code b}'s country has, the likeliest first. */
    public static List<Kind> of(Biome b) {
        if (b == null) return List.of(Kind.RAIN, Kind.WIND);
        return switch (b) {
            case PLAINS -> List.of(Kind.RAIN, Kind.WIND, Kind.THUNDERSTORM, Kind.FOG);
            case MARSH -> List.of(Kind.FOG, Kind.RAIN, Kind.THUNDERSTORM);
            case MOUNTAINS -> List.of(Kind.SNOW, Kind.WIND, Kind.FOG);
            case JUNGLE -> List.of(Kind.RAIN, Kind.THUNDERSTORM, Kind.FOG);
            case SAVANNAH -> List.of(Kind.SANDSTORM, Kind.WIND, Kind.THUNDERSTORM);
            case OCEAN -> List.of(Kind.WIND, Kind.RAIN, Kind.THUNDERSTORM, Kind.FOG);
            case RIVER -> List.of(Kind.RAIN, Kind.FOG, Kind.WIND);
            case WASTE -> List.of(Kind.SANDSTORM, Kind.WIND, Kind.FOG);
            case CRYSTAL -> List.of(Kind.SNOW, Kind.FOG, Kind.WIND);
        };
    }

    /** How likely each of a biome's weathers is, in its order. */
    static final double[] LIKELY = {0.5, 0.25, 0.15, 0.1};
    /** How long a spell of weather (or of none) lasts, seconds. */
    public static final double SPELL = 150;
    /** How fast weather comes in and goes, of its full strength a second. */
    public static final double FADE = 1 / 14.0;

    /**
     * The weather over her as it is now, and as it changes: its kind and how
     * heavy it is, fading in and out; the lightning in a thunderstorm and the
     * thunder after it; the wind's clock, the grass swaying faster in a gale.
     */
    public static final class Now {
        private Kind kind = Kind.CLEAR;
        private double level, flash, nextBolt = 6, windClock;
        private double thunderIn = -1, thunderLoud;
        private int bolts;
        private final java.util.Random random;
        private final List<Double> thunder = new java.util.ArrayList<>();

        public Now(long seed) {
            random = new java.util.Random(seed);
        }

        /** What is falling or blowing now (clear: nothing). */
        public Kind kind() { return level > 0.005 ? kind : Kind.CLEAR; }

        /** How heavy it is now, 0 – 1. */
        public double level() { return level; }

        /** How heavy {@code k} is now (0 when it is some other weather). */
        public double of(Kind k) { return kind == k ? level : 0; }

        /**
         * Whether it is a storm now - thunder, a sandstorm, a downpour, a
         * blizzard - the weather folk go in out of.
         */
        public boolean stormy() {
            return switch (kind()) {
                case THUNDERSTORM, SANDSTORM -> level > 0.25;
                case RAIN, SNOW -> level > 0.75;
                default -> false;
            };
        }

        /** The lightning's light now, 0 – 1. */
        public double flash() { return flash; }

        /** How many bolts of lightning there have been. */
        public int bolts() { return bolts; }

        /** Seconds on the grass's clock: faster than the world's in the wind. */
        public double windClock() { return windClock; }

        /** The thunder heard since last asked, each as loud as it is (0 – 1). */
        public List<Double> drainThunder() {
            List<Double> out = List.copyOf(thunder);
            thunder.clear();
            return out;
        }

        /** Put it at once as it would be (scripts, tests): {@code kind} at {@code level}. */
        public void set(Kind k, double l) {
            kind = k == null ? Kind.CLEAR : k;
            level = Math.max(0, Math.min(1, l));
        }

        /**
         * Move on {@code dt} seconds at world time {@code time} under {@code
         * w}, she in biome {@code here}: towards the weather wanted - one kind
         * fading out before the next fades in.
         */
        public void tick(double dt, double time, Weather w, Biome here) {
            Kind want = Kind.CLEAR;
            double wantLevel = 0;
            if (w.mode() == Mode.FIXED) {
                want = w.kind();
                wantLevel = w.strength();
            } else if (w.mode() == Mode.BIOME) {
                double[] spell = spell(time);
                if (spell[0] < w.often()) {
                    List<Kind> kinds = Weather.of(here);
                    double pick = spell[1], sum = 0;
                    want = kinds.get(kinds.size() - 1);
                    for (int i = 0; i < kinds.size(); i++) {
                        sum += LIKELY[Math.min(i, LIKELY.length - 1)];
                        if (pick * sumOf(kinds.size()) < sum) {
                            want = kinds.get(i);
                            break;
                        }
                    }
                    wantLevel = w.strength() * (0.45 + 0.55 * spell[2]);
                }
            }
            double step = FADE * dt;
            if (want != kind && level > 0) {
                level = Math.max(0, level - step);                 // the last weather goes first
            } else {
                if (want != Kind.CLEAR) kind = want;
                level += Math.max(-step, Math.min(step, wantLevel - level));
                if (want == Kind.CLEAR && level <= 0) kind = Kind.CLEAR;
            }
            windClock += dt * (1 + 1.6 * (of(Kind.WIND) + of(Kind.SANDSTORM) + 0.6 * of(Kind.THUNDERSTORM)));
            lightning(dt);
        }

        private static double sumOf(int n) {
            double s = 0;
            for (int i = 0; i < n; i++) s += LIKELY[Math.min(i, LIKELY.length - 1)];
            return s;
        }

        /** The spell {@code time} is in: whether it has weather, which, how heavy - three numbers 0 – 1. */
        static double[] spell(double time) {
            long n = (long) Math.floor(time / SPELL);
            java.util.Random r = new java.util.Random(n * 0x9E3779B97F4A7C15L + 1025);
            return new double[]{r.nextDouble(), r.nextDouble(), r.nextDouble()};
        }

        /** In a thunderstorm, now and then a bolt - its light at once, its thunder a moment after. */
        private void lightning(double dt) {
            flash = Math.max(0, flash - dt * 4.5);
            if (thunderIn >= 0) {
                thunderIn -= dt;
                if (thunderIn < 0 && thunder.size() < 8) thunder.add(thunderLoud);
            }
            double storm = of(Kind.THUNDERSTORM);
            if (storm < 0.3) return;
            nextBolt -= dt;
            if (nextBolt > 0) return;
            nextBolt = 4 + random.nextDouble() * 12 / storm;
            flash = 0.7 + 0.3 * random.nextDouble();
            bolts++;
            thunderIn = 0.4 + random.nextDouble() * 2.6;              // the further off, the later
            thunderLoud = storm * (1.1 - thunderIn / 4);
        }
    }
}
