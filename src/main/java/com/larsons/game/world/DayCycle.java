package com.larsons.game.world;

import com.larsons.game.math.Vec3;

/**
 * The time of day in a world, and what it makes of the light: where the sun
 * is, how bright the day is and what colour, the sky's colours from dawn
 * through noon and sunset to the planet-lit night, how much of the planet is
 * lit, how bright the stars are and how thick the mist lies.
 *
 * <p><b>The sun</b> rises in the east at 06:00, stands {@value #NOON_ELEVATION}°
 * up in the south at noon and sets in the west at 18:00 - every day the
 * same, as on a world without seasons - and is as far below the northern
 * horizon at midnight. <b>The planet</b> never moves ({@link Atmosphere}); the
 * sun lights its side towards it, so it is a crescent near the sun by day and
 * full when the sun is opposite it - and a full planet lights the night
 * ({@link Daylight#ambient}).
 *
 * <p>The clock runs at {@link Atmosphere#dayMinutes} real minutes a day, times
 * a speed ({@link #setSpeed}: the demo's time-lapse); with a day of 0 minutes
 * it stands still. Pure data: {@link #daylight} is what every renderer is
 * given.
 */
public final class DayCycle {

    /** Sunrise and sunset, hours. */
    public static final double SUNRISE = 6, SUNSET = 18;
    /** How far the sun climbs at noon, degrees (the world's latitude is 90 minus this). */
    public static final double NOON_ELEVATION = 55;
    /** How much faster than the day's own pace the time-lapse runs the clock. */
    public static final double TIME_LAPSE = 60;

    private double hour;
    private double speed = 1;

    public DayCycle(double hour) {
        setHour(hour);
    }

    /** The hour, 0 – 24 (fractions are minutes: 6.5 is half past six). */
    public double hour() { return hour; }

    public void setHour(double h) {
        hour = ((h % 24) + 24) % 24;
    }

    /** How many times its own pace the clock runs (1: as the atmosphere says; {@link #TIME_LAPSE}: fast). */
    public double speed() { return speed; }

    public void setSpeed(double s) {
        speed = Math.max(0, s);
    }

    /** Move the clock on {@code dt} seconds of real time under {@code air} (not at all if its time stands still). */
    public void tick(double dt, Atmosphere air) {
        if (air == null || !air.timeRuns()) return;
        setHour(hour + dt * speed * 24 / (air.dayMinutes() * 60));
    }

    /** "06:30". */
    public static String clock(double hour) {
        int minutes = (int) Math.floor((((hour % 24) + 24) % 24) * 60 + 1e-6);
        return String.format("%02d:%02d", minutes / 60 % 24, minutes % 60);
    }

    /** The way to the sun at {@code hour} (a unit vector: x east, y up, z south). */
    public static Vec3 sun(double hour) {
        double h = (hour - 12) / 24 * 2 * Math.PI;
        double lat = Math.toRadians(90 - NOON_ELEVATION);
        double east = -Math.sin(h), north = -Math.cos(h) * Math.sin(lat), up = Math.cos(h) * Math.cos(lat);
        return new Vec3(east, up, -north).normalize();
    }

    /** The way to the planet's middle as {@code air} hangs it (x east, y up, z south). */
    public static Vec3 planet(Atmosphere air) {
        double a = Math.toRadians(air.planetAzimuth()), e = Math.toRadians(air.planetElevation());
        return new Vec3(Math.sin(a) * Math.cos(e), Math.sin(e), -Math.cos(a) * Math.cos(e)).normalize();
    }

    /** The sun's height over the horizon at {@code hour}, degrees (below it: negative). */
    public static double sunElevation(double hour) {
        return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sun(hour).y()))));
    }

    /**
     * Everything the light is at an hour under a sky: what the renderers
     * draw by.
     *
     * @param sun       the way to the sun
     * @param elevation the sun's height, degrees
     * @param day       how much it is day, 0 (night) – 1 (the sun well up)
     * @param twilight  how much of a dawn or a sunset there is, 0 – 1 (most with the sun on the horizon)
     * @param ambient   the colour everything is lit with, red, green, blue (1, 1, 1 at noon)
     * @param zenith    the sky overhead
     * @param horizon   the sky at the horizon, and the fog
     * @param glow      the colour of the sunset's glow round the sun, low in the sky
     * @param sunColour the sun's disc
     * @param cloudLit  the clouds' lit side; their shaded side is darker
     * @param cloudShade the clouds' side away from the sun
     * @param stars     how bright the stars are, 0 – 1
     * @param planet    the way to the planet
     * @param planetLit how much of the planet's face is lit, 0 (new) – 1 (full)
     * @param mist      how thick the mist lies now, 0 – 1
     * @param rays      how strong the light rays are now, 0 – 1
     * @param hour      the hour it is
     */
    public record Daylight(Vec3 sun, double elevation, double day, double twilight, float[] ambient, float[] zenith,
                           float[] horizon, float[] glow, float[] sunColour, float[] cloudLit, float[] cloudShade,
                           double stars, Vec3 planet, double planetLit, double mist, double rays, double hour) {

        /** The light the scene had before there was time: noon, white light, the sky as it is coloured. */
        public static Daylight noon(Sky sky) {
            return of(sky.withAir(Atmosphere.STILL), 12);
        }
    }

    /** The light at this clock's hour under {@code sky}. */
    public Daylight daylight(Sky sky) {
        return of(sky, hour);
    }

    /** The light at {@code hour} under {@code sky}. */
    public static Daylight of(Sky sky, double hour) {
        Atmosphere air = sky.air();
        Vec3 sun = sun(hour);
        double el = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sun.y()))));
        double day = smooth(-6, 8, el);
        double twilight = Math.exp(-Math.pow(el / 7.0, 2));
        Vec3 planet = planet(air);
        boolean shown = air.planet();
        double lit = (1 - sun.dot(planet)) / 2;
        // the planet lights the night only as far as it is up and lit
        double shine = shown ? lit * smooth(-8, 6, air.planetElevation()) : 0;

        float[] dayZenith = Sky.rgb(sky.zenith()), dayHorizon = Sky.rgb(sky.horizon());
        float[] nightZenith = add(new float[]{0.035f, 0.05f, 0.14f}, scale(new float[]{0.10f, 0.07f, 0.05f}, shine));
        float[] nightHorizon = add(new float[]{0.10f, 0.13f, 0.27f}, scale(new float[]{0.16f, 0.11f, 0.07f}, shine));
        float[] zenith = mix(nightZenith, dayZenith, day);
        float[] horizon = mix(nightHorizon, dayHorizon, day);
        zenith = mix(zenith, new float[]{0.30f, 0.25f, 0.50f}, twilight * 0.4);
        horizon = mix(horizon, new float[]{0.93f, 0.58f, 0.45f}, twilight * 0.55);

        // the light: white by day, gold low in the sky, rose at the sunset, blue by night and warmer under a full planet
        float[] night = add(new float[]{0.27f, 0.30f, 0.46f}, scale(new float[]{0.22f, 0.16f, 0.10f}, shine));
        float[] daylight = mix(new float[]{1f, 1f, 1f}, new float[]{1.0f, 0.86f, 0.70f}, smooth(18, 3, el));
        daylight = mix(daylight, new float[]{0.86f, 0.66f, 0.70f}, smooth(4, -3, el));
        float[] ambient = mix(night, daylight, day);
        if (!air.timeRuns() && air.startHour() == 12 && !air.planet()) ambient = new float[]{1f, 1f, 1f};

        float[] glow = new float[]{1.0f, 0.52f, 0.28f};
        float[] sunColour = mix(new float[]{1.0f, 0.55f, 0.30f}, new float[]{1.0f, 0.97f, 0.86f}, smooth(0, 20, el));
        float[] cloudLit = mix(add(new float[]{0.22f, 0.24f, 0.36f}, scale(new float[]{0.18f, 0.13f, 0.09f}, shine)),
                new float[]{0.98f, 0.98f, 0.97f}, day);
        cloudLit = mix(cloudLit, new float[]{1.0f, 0.70f, 0.60f}, twilight * 0.7);
        float[] cloudShade = mul(cloudLit, new float[]{0.70f, 0.72f, 0.82f});
        double stars = air.stars() * clamp(1 - day * 1.6);
        double dawn = Math.exp(-Math.pow((hour - 6.5) / 2.0, 2));
        double mist = clamp(air.mist() * (0.65 + 0.9 * dawn + 0.35 * (1 - day)));
        double rays = air.rays() * smooth(0.5, 6, el) * (1 - smooth(24, 48, el));
        return new Daylight(sun, el, day, twilight, ambient, zenith, horizon, glow, sunColour, cloudLit, cloudShade,
                stars, planet, shown ? lit : 0, mist, rays, ((hour % 24) + 24) % 24);
    }

    static double smooth(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0));
        return t * t * (3 - 2 * t);
    }

    static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    static float[] mix(float[] a, float[] b, double t) {
        float k = (float) clamp(t);
        return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    static float[] add(float[] a, float[] b) {
        return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    static float[] scale(float[] a, double k) {
        return new float[]{(float) (a[0] * k), (float) (a[1] * k), (float) (a[2] * k)};
    }

    static float[] mul(float[] a, float[] b) {
        return new float[]{a[0] * b[0], a[1] * b[1], a[2] * b[2]};
    }
}
