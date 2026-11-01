package com.larsons.game.world;

/**
 * The time and the weather over a world, and what hangs in its sky: the
 * hour it starts at and how long a day lasts ({@link DayCycle}), the giant
 * planet the world - a moon - circles, the clouds, the stars and the
 * aurora, the mist lying on the ground and the light rays slanting through
 * it. {@link Sky} holds it beside its colours and backdrops; a level sets
 * its own in Creative Mode's Time tool.
 *
 * <p>The world is the planet's moon and always turns the same face to it, as
 * our Moon does to the Earth, so the planet never moves in its sky: it hangs
 * where {@link #planetAzimuth} and {@link #planetElevation} put it, day and
 * night, and only its lit part changes as the sun goes round - a thin
 * crescent by day, near the sun, and full at midnight, when it lights the
 * land.
 *
 * <p>Pure data.
 *
 * @param startHour       the hour of the day a world starts at, 0 – 24 (6 is sunrise, 18 sunset)
 * @param dayMinutes      real minutes a whole day and night take; 0: the time stands still
 * @param planet          whether the planet is in the sky
 * @param planetAzimuth   where round the horizon it hangs, degrees from north (0) through east (90)
 * @param planetElevation how high over the horizon its middle is, degrees
 * @param planetSize      degrees of the sky it spans, across
 * @param rings           whether it has rings
 * @param planetSeed      which of the endless patterns of bands and storms it wears
 * @param clouds          how much of the sky the clouds cover, 0 (clear) – 1 (overcast)
 * @param stars           how bright the stars are at night, 0 (none) – 1
 * @param aurora          how strong the aurora is at night, 0 (none) – 1
 * @param mist            how thick the mist lying on the ground is, 0 (none) – 1
 * @param mistHeight      metres up the mist reaches (it thins out above that)
 * @param rays            how strong the light rays slanting down through the mist and the trees are, 0 – 1
 */
public record Atmosphere(double startHour, double dayMinutes, boolean planet, double planetAzimuth,
                         double planetElevation, double planetSize, boolean rings, int planetSeed, double clouds,
                         double stars, double aurora, double mist, double mistHeight, double rays) {

    /** The longest a day can be made to last, real minutes. */
    public static final double MAX_DAY_MINUTES = 240;

    /**
     * The demo's: ten in the morning, a day every 24 minutes (an hour a
     * minute), the planet low in the south-south-west, a few clouds, stars,
     * a faint aurora, a little mist and light rays.
     */
    public static final Atmosphere DEFAULT = new Atmosphere(10, 24, true, 200, 16, 26, false, 1, 0.35, 0.85,
            0.35, 0.25, 2.5, 0.6);

    /** The look the world had before there was time: noon for ever, no planet, no clouds, no mist. */
    public static final Atmosphere STILL = new Atmosphere(12, 0, false, 200, 16, 26, false, 1, 0, 0, 0, 0, 2.5, 0);

    public Atmosphere {
        startHour = ((startHour % 24) + 24) % 24;
        dayMinutes = Math.max(0, Math.min(MAX_DAY_MINUTES, dayMinutes));
        planetAzimuth = ((planetAzimuth % 360) + 360) % 360;
        planetElevation = Math.max(-10, Math.min(80, planetElevation));
        planetSize = Math.max(2, Math.min(60, planetSize));
        clouds = clamp01(clouds);
        stars = clamp01(stars);
        aurora = clamp01(aurora);
        mist = clamp01(mist);
        mistHeight = Math.max(0.3, Math.min(30, mistHeight));
        rays = clamp01(rays);
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }

    /** Whether the time goes by (false: it stands at {@link #startHour}). */
    public boolean timeRuns() { return dayMinutes > 0; }

    public Atmosphere withStartHour(double h) {
        return new Atmosphere(h, dayMinutes, planet, planetAzimuth, planetElevation, planetSize, rings, planetSeed,
                clouds, stars, aurora, mist, mistHeight, rays);
    }

    public Atmosphere withDayMinutes(double m) {
        return new Atmosphere(startHour, m, planet, planetAzimuth, planetElevation, planetSize, rings, planetSeed,
                clouds, stars, aurora, mist, mistHeight, rays);
    }

    public Atmosphere withPlanet(boolean on) {
        return new Atmosphere(startHour, dayMinutes, on, planetAzimuth, planetElevation, planetSize, rings, planetSeed,
                clouds, stars, aurora, mist, mistHeight, rays);
    }

    public Atmosphere withPlanetAt(double azimuth, double elevation) {
        return new Atmosphere(startHour, dayMinutes, planet, azimuth, elevation, planetSize, rings, planetSeed,
                clouds, stars, aurora, mist, mistHeight, rays);
    }

    public Atmosphere withPlanetSize(double degrees) {
        return new Atmosphere(startHour, dayMinutes, planet, planetAzimuth, planetElevation, degrees, rings,
                planetSeed, clouds, stars, aurora, mist, mistHeight, rays);
    }

    public Atmosphere withRings(boolean on) {
        return new Atmosphere(startHour, dayMinutes, planet, planetAzimuth, planetElevation, planetSize, on,
                planetSeed, clouds, stars, aurora, mist, mistHeight, rays);
    }

    public Atmosphere withPlanetSeed(int seed) {
        return new Atmosphere(startHour, dayMinutes, planet, planetAzimuth, planetElevation, planetSize, rings, seed,
                clouds, stars, aurora, mist, mistHeight, rays);
    }

    public Atmosphere withClouds(double c) {
        return new Atmosphere(startHour, dayMinutes, planet, planetAzimuth, planetElevation, planetSize, rings,
                planetSeed, c, stars, aurora, mist, mistHeight, rays);
    }

    public Atmosphere withStars(double s) {
        return new Atmosphere(startHour, dayMinutes, planet, planetAzimuth, planetElevation, planetSize, rings,
                planetSeed, clouds, s, aurora, mist, mistHeight, rays);
    }

    public Atmosphere withAurora(double a) {
        return new Atmosphere(startHour, dayMinutes, planet, planetAzimuth, planetElevation, planetSize, rings,
                planetSeed, clouds, stars, a, mist, mistHeight, rays);
    }

    public Atmosphere withMist(double m) {
        return new Atmosphere(startHour, dayMinutes, planet, planetAzimuth, planetElevation, planetSize, rings,
                planetSeed, clouds, stars, aurora, m, mistHeight, rays);
    }

    public Atmosphere withMistHeight(double h) {
        return new Atmosphere(startHour, dayMinutes, planet, planetAzimuth, planetElevation, planetSize, rings,
                planetSeed, clouds, stars, aurora, mist, h, rays);
    }

    public Atmosphere withRays(double r) {
        return new Atmosphere(startHour, dayMinutes, planet, planetAzimuth, planetElevation, planetSize, rings,
                planetSeed, clouds, stars, aurora, mist, mistHeight, r);
    }
}
