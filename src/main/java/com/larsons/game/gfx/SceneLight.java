package com.larsons.game.gfx;

/**
 * The light a frame of the world is drawn in, for every shader that draws
 * the world - the ground and the sky, the hills, the scenery, the sprites:
 * one colour everything is lit with (the sun's by day, the planet's and the
 * stars' by night: {@link #ambient}), up to {@value #MAX} lights of their own
 * - an open chest, a lantern, a glowing crystal, fireflies - each lighting
 * what is round it, and the mist lying on the ground.
 *
 * <p>All of it is the GLSL in {@link #GLSL}: {@code vec3 sceneLight(vec3 p)},
 * the light at world point {@code p} to multiply a colour by, and {@code
 * float sceneMist(vec3 p)}, how much of the mist lies between the eye and
 * {@code p}. Both are in steps, as the pixel art is shaded: the lights fall
 * off in bands, worked out at the middle of blocks {@link #texel} metres a
 * side, so their edges are pixel-hard; the mist thickens in bands too.
 *
 * <p>Plain data, filled in by the world's renderer every frame and bound by
 * each shader ({@link #bind}).
 */
public final class SceneLight {

    /** The most lights of their own a frame has. */
    public static final int MAX = 16;

    /** Light as the art is: white, nothing of its own, no mist. */
    public static final SceneLight NONE = new SceneLight();

    /** The colour everything is lit with, red, green, blue: 1, 1, 1 lights it as it was drawn. */
    public final float[] ambient = {1f, 1f, 1f};
    /** Where the eye is (x, y, z). */
    public final float[] camera = new float[3];
    /** The mist's thickness at the ground (per metre; 0: none), how high it reaches (metres), and the time. */
    public float mist, mistHeight = 2.5f, time;
    /** The mist's colour. */
    public final float[] mistColour = {0.8f, 0.84f, 0.9f};
    /** Metres a block of light spans: lights are worked out at block middles, so their bands are pixel-hard. */
    public float texel = 1f / 16f;
    /** The lights: x, y, z, radius each, and r, g, b, 0 (the colour times its strength). */
    final float[] lightPos = new float[MAX * 4], lightCol = new float[MAX * 4];
    private int lights;

    /** Take away every light of its own. */
    public SceneLight clearLights() {
        lights = 0;
        return this;
    }

    /** How many lights of their own there are. */
    public int lights() { return lights; }

    /**
     * Add a light at {@code (x, y, z)} reaching {@code radius} metres, of
     * {@code rgb} ({@code 0xRRGGBB}) times {@code strength}; false when there
     * are {@value #MAX} already.
     */
    public boolean add(double x, double y, double z, double radius, int rgb, double strength) {
        if (lights >= MAX || radius <= 0 || strength <= 0) return false;
        int i = lights * 4;
        lightPos[i] = (float) x;
        lightPos[i + 1] = (float) y;
        lightPos[i + 2] = (float) z;
        lightPos[i + 3] = (float) radius;
        lightCol[i] = (float) (((rgb >> 16) & 255) / 255.0 * strength);
        lightCol[i + 1] = (float) (((rgb >> 8) & 255) / 255.0 * strength);
        lightCol[i + 2] = (float) ((rgb & 255) / 255.0 * strength);
        lightCol[i + 3] = 0;
        lights++;
        return true;
    }

    /** The light at {@code (x, y, z)}, red, green, blue - as the shaders work it out (for tests and the CPU). */
    public float[] at(double x, double y, double z) {
        float[] l = ambient.clone();
        double bx = (Math.floor(x / texel) + 0.5) * texel, by = (Math.floor(y / texel) + 0.5) * texel,
                bz = (Math.floor(z / texel) + 0.5) * texel;
        for (int i = 0; i < lights; i++) {
            double dx = bx - lightPos[i * 4], dy = by - lightPos[i * 4 + 1], dz = bz - lightPos[i * 4 + 2];
            double r = Math.sqrt(dx * dx + dy * dy + dz * dz) / lightPos[i * 4 + 3];
            if (r >= 1) continue;
            double f = (1 - r) * (1 - r);
            f = Math.floor(f * 5 + 0.5) / 5;
            for (int c = 0; c < 3; c++) l[c] += (float) (lightCol[i * 4 + c] * f);
        }
        return l;
    }

    /**
     * The uniforms and the two functions. Paste into a fragment shader; a
     * shader using them calls {@link #bind} before drawing.
     */
    public static final String GLSL = """
            uniform vec3 uAmbient;
            uniform vec3 uCamera;
            uniform vec4 uMist;            // thickness at the ground, 1 / its height, the time, 0
            uniform vec3 uMistColour;
            uniform float uLightTexel;
            uniform int uLights;
            uniform vec4 uLightPos[16];    // x, y, z, radius
            uniform vec4 uLightCol[16];    // r, g, b (times strength), 0

            float slHash(vec2 p) {
                uvec2 q = uvec2(ivec2(floor(p)));
                uint h = q.x * 374761393u + q.y * 668265263u;
                h = (h ^ (h >> 13u)) * 1274126177u;
                return float((h ^ (h >> 16u)) & 0xffffu) / 65535.0;
            }

            float slNoise(vec2 p) {
                vec2 i = floor(p), f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(slHash(i), slHash(i + vec2(1.0, 0.0)), f.x),
                           mix(slHash(i + vec2(0.0, 1.0)), slHash(i + vec2(1.0, 1.0)), f.x), f.y);
            }

            // the light at world point p: the ambient light and every light round it, in bands
            vec3 sceneLight(vec3 p) {
                vec3 l = uAmbient;
                vec3 q = uLightTexel > 0.0 ? (floor(p / uLightTexel) + 0.5) * uLightTexel : p;
                for (int i = 0; i < 16; i++) {
                    if (i >= uLights) break;
                    float r = length(q - uLightPos[i].xyz) / uLightPos[i].w;
                    if (r >= 1.0) continue;
                    float f = (1.0 - r) * (1.0 - r);
                    l += uLightCol[i].rgb * (floor(f * 5.0 + 0.5) / 5.0);
                }
                return l;
            }

            // how much of the mist lies between the eye and p, 0 - 1: thickest on the ground, thinning
            // out with height, drifting in banks; in bands, dithered on the ground in blocks
            float sceneMist(vec3 p) {
                if (uMist.x <= 0.0) return 0.0;
                vec3 ro = uCamera;
                vec3 rd = p - ro;
                float t = length(rd);
                if (t < 1e-3) return 0.0;
                rd /= t;
                float b = uMist.y;
                float m = abs(rd.y) < 1e-3 ? uMist.x * exp(-b * ro.y) * t
                        : uMist.x / b * exp(-b * ro.y) * (1.0 - exp(-t * rd.y * b)) / rd.y;
                vec2 drift = vec2(uMist.z * 0.35, uMist.z * 0.11);
                float bank = slNoise(p.xz * 0.055 + drift) * 0.7 + slNoise(p.xz * 0.17 - drift * 1.7) * 0.3;
                m *= 0.25 + 1.5 * bank * bank;
                float a = 1.0 - exp(-m);
                float dither = slHash(floor(p.xz / 0.12) + floor(p.y / 0.12) * 17.0) - 0.5;
                return clamp(floor(a * 8.0 + 0.5 + dither * 0.3) / 8.0, 0.0, 0.92);
            }
            """;

    /** Set {@code s}'s uniforms - those of {@link #GLSL} - to this light. */
    public void bind(Shader s) {
        s.set("uAmbient", ambient[0], ambient[1], ambient[2]);
        s.set("uCamera", camera[0], camera[1], camera[2]);
        s.set("uMist", mist, 1f / Math.max(0.3f, mistHeight), time % 2000f, 0f);
        s.set("uMistColour", mistColour[0], mistColour[1], mistColour[2]);
        s.set("uLightTexel", texel);
        s.set("uLights", lights);
        for (int i = 0; i < lights; i++) {
            s.set("uLightPos[" + i + "]", lightPos[i * 4], lightPos[i * 4 + 1], lightPos[i * 4 + 2], lightPos[i * 4 + 3]);
            s.set("uLightCol[" + i + "]", lightCol[i * 4], lightCol[i * 4 + 1], lightCol[i * 4 + 2], 0f);
        }
    }
}
