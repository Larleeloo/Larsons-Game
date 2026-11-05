package com.larsons.game.world;

/**
 * What the weather ({@link Weather.Now}) does to the sky and the light:
 * rain and snow draw cloud over it, grey it and bring the fog in; fog
 * brings it in close and lies thick on the ground; a sandstorm turns it the
 * colour of the sand; under any of them the light is dimmer, the light rays
 * and the stars gone - and the lightning lights it all for a moment.
 *
 * <p>Pure functions.
 */
public final class Weathering {

    private Weathering() {}

    /** Grey rain, white snow, tawny sand, pale fog: the colours the sky turns towards. */
    static final int RAIN_SKY = 0x5E6874, RAIN_HORIZON = 0x8C96A0, SNOW_HORIZON = 0xD8DEE6, FOG_HORIZON = 0xB4B9BE,
            SAND = 0xC8A065, SAND_SKY = 0xB08A58;

    /** {@code sky} under the weather {@code now}. */
    public static Sky sky(Sky sky, Weather.Now now) {
        double rain = now.of(Weather.Kind.RAIN) + now.of(Weather.Kind.THUNDERSTORM);
        double snow = now.of(Weather.Kind.SNOW), fog = now.of(Weather.Kind.FOG), sand = now.of(Weather.Kind.SANDSTORM);
        if (rain + snow + fog + sand + now.of(Weather.Kind.WIND) <= 0) return sky;
        double storm = now.of(Weather.Kind.THUNDERSTORM);
        double cover = Math.max(Math.max(rain * 0.95, snow * 0.85), Math.max(sand * 0.5, fog * 0.6));
        Atmosphere a = sky.air();
        a = a.withClouds(a.clouds() + (1 - a.clouds()) * cover)
                .withRays(a.rays() * (1 - Math.min(1, rain + snow + sand + fog)))
                .withStars(a.stars() * (1 - cover))
                .withAurora(a.aurora() * (1 - cover))
                .withMist(Math.min(1, a.mist() + fog * 0.75 + rain * 0.3 + snow * 0.2 + sand * 0.3))
                .withMistHeight(Math.max(a.mistHeight(), 2.5 + fog * 5));
        double far = sky.fog() * (1 - 0.45 * rain) * (1 - 0.5 * snow) * (1 - 0.72 * fog) * (1 - 0.8 * sand);
        int zenith = mix(sky.zenith(), RAIN_SKY, rain * 0.65 + storm * 0.2);
        zenith = mix(zenith, SAND_SKY, sand * 0.8);
        zenith = mix(zenith, FOG_HORIZON, fog * 0.4 + snow * 0.35);
        int horizon = mix(sky.horizon(), RAIN_HORIZON, rain * 0.55);
        horizon = mix(horizon, SNOW_HORIZON, snow * 0.6);
        horizon = mix(horizon, FOG_HORIZON, fog * 0.7);
        horizon = mix(horizon, SAND, sand * 0.85);
        return new Sky(zenith, horizon, far, sky.backdrops(), a);
    }

    /** {@code d} under the weather {@code now}: dimmer under cloud, warm in sand, lit by the lightning. */
    public static DayCycle.Daylight light(DayCycle.Daylight d, Weather.Now now) {
        double rain = now.of(Weather.Kind.RAIN), storm = now.of(Weather.Kind.THUNDERSTORM);
        double snow = now.of(Weather.Kind.SNOW), fog = now.of(Weather.Kind.FOG), sand = now.of(Weather.Kind.SANDSTORM);
        double flash = now.flash();
        if (rain + storm + snow + fog + sand <= 0 && flash <= 0) return d;
        double dim = 1 - 0.33 * rain - 0.48 * storm - 0.12 * snow - 0.12 * fog - 0.18 * sand;
        float[] ambient = scale(d.ambient(), dim);
        double wet = Math.min(1, rain + storm);
        ambient = new float[]{(float) (ambient[0] * (1 - 0.08 * wet)), (float) (ambient[1] * (1 - 0.07 * sand - 0.04 * wet)),
                (float) (ambient[2] * (1 - 0.22 * sand))};
        float[] zenith = d.zenith(), horizon = d.horizon(), cloudLit = d.cloudLit(), cloudShade = d.cloudShade();
        double grey = Math.min(1, rain * 0.5 + storm * 0.8);
        cloudLit = DayCycle.mix(cloudLit, scale(new float[]{0.58f, 0.61f, 0.66f}, 0.4 + 0.6 * d.day()), grey);
        cloudShade = DayCycle.mix(cloudShade, scale(new float[]{0.34f, 0.37f, 0.43f}, 0.4 + 0.6 * d.day()), grey);
        if (flash > 0) {
            float[] bolt = {0.75f, 0.8f, 1.0f};
            ambient = DayCycle.add(ambient, scale(bolt, flash * 0.9));
            zenith = DayCycle.add(zenith, scale(bolt, flash * 0.45));
            horizon = DayCycle.add(horizon, scale(bolt, flash * 0.35));
            cloudLit = DayCycle.add(cloudLit, scale(bolt, flash * 0.6));
        }
        double clear = 1 - Math.min(1, rain + storm + snow + sand + fog * 0.6);
        return new DayCycle.Daylight(d.sun(), d.elevation(), d.day(), d.twilight() * clear, ambient, zenith, horizon,
                d.glow(), d.sunColour(), cloudLit, cloudShade, d.stars() * clear, d.planet(), d.planetLit() * clear,
                d.mist(), d.rays() * clear, d.hour());
    }

    private static float[] scale(float[] a, double k) {
        return new float[]{(float) (a[0] * k), (float) (a[1] * k), (float) (a[2] * k)};
    }

    /** A colour for the GPU (red, green, blue 0 – 1) as {@code 0xRRGGBB}. */
    static int rgb(float[] c) {
        int r = Math.round(Math.max(0, Math.min(1, c[0])) * 255), g = Math.round(Math.max(0, Math.min(1, c[1])) * 255),
                b = Math.round(Math.max(0, Math.min(1, c[2])) * 255);
        return (r << 16) | (g << 8) | b;
    }

    /** {@code a} {@code t} of the way to {@code b}, both {@code 0xRRGGBB}. */
    static int mix(int a, int b, double t) {
        double k = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        return (r << 16) | (g << 8) | bl;
    }
}
