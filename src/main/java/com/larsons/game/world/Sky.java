package com.larsons.game.world;

import java.util.List;

/**
 * The sky over a world and what stands far off under it: the colour
 * overhead ({@link #zenith}), the colour at the horizon - which is also
 * the fog everything fades into with distance ({@link #horizon}, {@link
 * #fog}) - and the distant backgrounds ({@link Backdrop}): mountain ranges,
 * hills, a tree line, towers, the sea, drawn round the horizon by {@link
 * VoidRenderer} at no distance at all, so they never come nearer.
 *
 * <p>Pure data. A level sets its own ({@code level/Level}); the demo and the
 * menu stand under {@link #DEFAULT}, the soft blue they always had.
 *
 * @param zenith    the sky straight overhead, {@code 0xRRGGBB}
 * @param horizon   the sky at the horizon and the fog, {@code 0xRRGGBB}
 * @param fog       metres out the fog has hidden most of the ground (63 % of it)
 * @param backdrops the distant backgrounds, farthest first (each nearer one drawn over it)
 */
public record Sky(int zenith, int horizon, double fog, List<Backdrop> backdrops) {

    /** The most backdrops {@link VoidRenderer} draws. */
    public static final int MAX_BACKDROPS = 8;
    /** The nearest and farthest the fog may be, metres. */
    public static final double MIN_FOG = 20, MAX_FOG = 400;

    /** The demo's sky: a soft blue, paler at the horizon, the fog 70 m out. */
    public static final Sky DEFAULT = new Sky(0x5C78A3, 0xBDC9DB, 70, List.of());

    public Sky {
        fog = Math.max(MIN_FOG, Math.min(MAX_FOG, fog));
        backdrops = List.copyOf(backdrops.size() > MAX_BACKDROPS ? backdrops.subList(0, MAX_BACKDROPS) : backdrops);
    }

    /** {@code rgb} as the GPU takes a colour: red, green, blue from 0 to 1. */
    public static float[] rgb(int rgb) {
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    /** The horizon's colour, the fog's, for the GPU. */
    public float[] horizonColour() { return rgb(horizon); }

    public Sky withZenith(int c) { return new Sky(c, horizon, fog, backdrops); }

    public Sky withHorizon(int c) { return new Sky(zenith, c, fog, backdrops); }

    public Sky withFog(double metres) { return new Sky(zenith, horizon, metres, backdrops); }

    public Sky withBackdrops(List<Backdrop> b) { return new Sky(zenith, horizon, fog, b); }

    /**
     * Something far off round the horizon: a band of one kind of thing, in
     * one colour of the palette, drawn in chunky pixels as the art is.
     *
     * @param kind     what it is
     * @param colour   its colour where it is lit, {@code 0xRRGGBB} (its shadowed side and any snow follow from it)
     * @param height   how high its tops rise over the horizon, in degrees of the view
     * @param haze     how far into the horizon's colour it fades, 0 (none: near) to 1 (gone)
     * @param seed     which of the endless ranges of its kind it is
     * @param from     where round the horizon it starts, degrees from north (0) through east (90)
     * @param to       where it ends; {@code from == to}: all the way round
     * @param distance the sea: metres out its shore lies (the other kinds are at no distance)
     * @param snow     mountains: whether their peaks are white
     */
    public record Backdrop(Kind kind, int colour, double height, double haze, int seed, double from, double to,
                           double distance, boolean snow) {

        public Backdrop {
            height = Math.max(0, Math.min(40, height));
            haze = Math.max(0, Math.min(1, haze));
            distance = Math.max(10, distance);
        }

        /** One of {@code kind}, all the way round, in its usual colour and size. */
        public static Backdrop of(Kind kind) {
            return new Backdrop(kind, kind.colour, kind.height, kind.haze, 1, 0, 0, 90, kind == Kind.MOUNTAINS);
        }

        /** Whether it goes all the way round. */
        public boolean allRound() { return from == to; }

        public Backdrop withKind(Kind k) { return new Backdrop(k, colour, height, haze, seed, from, to, distance, snow); }

        public Backdrop withColour(int c) { return new Backdrop(kind, c, height, haze, seed, from, to, distance, snow); }

        public Backdrop withHeight(double h) { return new Backdrop(kind, colour, h, haze, seed, from, to, distance, snow); }

        public Backdrop withHaze(double h) { return new Backdrop(kind, colour, height, h, seed, from, to, distance, snow); }

        public Backdrop withSeed(int s) { return new Backdrop(kind, colour, height, haze, s, from, to, distance, snow); }

        public Backdrop withArc(double f, double t) {
            return new Backdrop(kind, colour, height, haze, seed, f, t, distance, snow);
        }

        public Backdrop withDistance(double d) {
            return new Backdrop(kind, colour, height, haze, seed, from, to, d, snow);
        }

        public Backdrop withSnow(boolean s) { return new Backdrop(kind, colour, height, haze, seed, from, to, distance, s); }
    }

    /** What a backdrop can be - and, for each, the colour, height and haze it starts with. */
    public enum Kind {
        MOUNTAINS("mountains", "Mountains", 0x6D7A8F, 9, 0.45),
        HILLS("hills", "Rolling hills", 0x5F7F4F, 4, 0.3),
        TREES("trees", "Tree line", 0x35553A, 2.5, 0.2),
        SPIRES("spires", "Towers and spires", 0x5B5560, 7, 0.4),
        OCEAN("ocean", "Ocean", 0x2E6E9E, 0, 0.25);

        private final String key, label;
        final int colour;
        final double height, haze;

        Kind(String key, String label, int colour, double height, double haze) {
            this.key = key;
            this.label = label;
            this.colour = colour;
            this.height = height;
            this.haze = haze;
        }

        /** How it is written in a level's file. */
        public String key() { return key; }

        public String label() { return label; }

        /** The kind written {@code key}, or null. */
        public static Kind byKey(String key) {
            for (Kind k : values()) if (k.key.equalsIgnoreCase(key)) return k;
            return null;
        }
    }
}
