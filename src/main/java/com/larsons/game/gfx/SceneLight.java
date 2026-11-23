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

    /**
     * The sun's shadows ({@link ShadowMap}): the depth texture (0: none),
     * the sun's view and projection it was drawn through, how much a shadow
     * darkens what is in it (0: no shadows), how far its middle {@link
     * #shadowCentre} they reach (metres), a pixel of it (in its own 0 - 1),
     * and how far towards the sun a point is looked up from (metres: what
     * draws a thing that casts no shadow of its own, a sprite, looks further,
     * so it is not shadowed by itself).
     */
    public int shadowTexture;
    public com.larsons.game.math.Mat4 shadowViewProj = com.larsons.game.math.Mat4.identity();
    public float shadowDark, shadowReach = 1, shadowTexel = 1f / 2048, shadowBias = 0.1f;
    public final float[] shadowCentre = new float[3];
    /** The way to the sun (shadows are cast away from it). */
    public final float[] towardSun = {0f, 1f, 0f};
    /** What the sunlight is tinted, and the shade (Vibrant visuals); 1, 1, 1: neither. */
    public final float[] sunTint = {1f, 1f, 1f}, shadeTint = {1f, 1f, 1f};
    /** The shafts of light through the air ({@code sceneRays}): how bright (0: none), how far from the eye, metres. */
    public float rays, raysReach = 40f;
    /** The colour of the light in the shafts. */
    public final float[] rayColour = {1f, 0.9f, 0.7f};
    /** How strong the vibrant look is, 0 - 1 (buildings lit from the sun, not as their views were). */
    public float vibrance;
    /** How far the sun is up to light things from where it is, 0 (down: night) - 1. */
    public float sunUp;

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

    /** No shadows of the sun, no vibrant look, no shafts of light. */
    public SceneLight flat() {
        shadowTexture = 0;
        shadowDark = 0;
        rays = 0;
        vibrance = 0;
        sunUp = 0;
        java.util.Arrays.fill(sunTint, 1f);
        java.util.Arrays.fill(shadeTint, 1f);
        return this;
    }

    /** The light at {@code (x, y, z)}, red, green, blue - as the shaders work it out (for tests and the CPU), in the sun. */
    public float[] at(double x, double y, double z) {
        float[] l = ambient.clone();
        for (int c = 0; c < 3; c++) l[c] *= sunTint[c];
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
            uniform sampler2DShadow uShadowMap;
            uniform mat4 uShadowVP;
            uniform vec4 uShadow;          // how dark a shadow is (0: none), how far they reach, a pixel of the map, the bias
            uniform vec3 uShadowCentre;
            uniform vec3 uSceneSun;          // towards the sun
            uniform vec3 uSunTint;         // the sunlight's tint, and the shade's (vibrant)
            uniform vec3 uShadeTint;
            uniform vec4 uRays;            // how bright the shafts of light are (0: none), how far they reach, 0, 0
            uniform vec3 uRayColour;

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

            // whether the sun is hidden from world point p, 0 - 1, read off the map smoothly (no bands)
            float sceneShadowAt(vec3 p, float bias) {
                vec4 s = uShadowVP * vec4(p + uSceneSun * bias, 1.0);
                vec3 c = s.xyz / s.w * 0.5 + 0.5;
                if (c.x <= 0.0 || c.y <= 0.0 || c.x >= 1.0 || c.y >= 1.0 || c.z >= 1.0) return 0.0;
                return 1.0 - texture(uShadowMap, c);
            }

            // how much in the sun's shadow world point p is, 0 - 1: on the pixels' grid, softened a little at
            // the edges, and in bands as the art is shaded; fading out towards the edge of the map
            float sceneShadow(vec3 p) {
                if (uShadow.x <= 0.0) return 0.0;
                float d = length(p.xz - uShadowCentre.xz) / uShadow.y;
                if (d >= 1.0) return 0.0;
                float block = uLightTexel * 0.5;
                vec3 q = block > 0.0 ? (floor(p / block) + 0.5) * block : p;
                vec4 s = uShadowVP * vec4(q + uSceneSun * uShadow.w, 1.0);
                vec3 c = s.xyz / s.w * 0.5 + 0.5;
                if (c.x <= 0.0 || c.y <= 0.0 || c.x >= 1.0 || c.y >= 1.0 || c.z >= 1.0) return 0.0;
                // nine looks round it, a pixel and a half of the map apart: the leaves' gaps soften into dapples
                float t = uShadow.z * 1.5;
                float lit = texture(uShadowMap, c) * 0.2;
                for (int i = 0; i < 8; i++) {
                    float a = float(i) * 0.785398;
                    lit += texture(uShadowMap, c + vec3(cos(a) * t, sin(a) * t, 0.0)) * 0.1;
                }
                float sh = (1.0 - lit) * (1.0 - smoothstep(0.82, 1.0, d));
                return floor(sh * 4.0 + 0.5) / 4.0;
            }

            // the light at world point p, sh in the sun's shadow (0 - 1), a shadow darkening it by dark
            vec3 sceneLightShaded(vec3 p, float sh, float dark) {
                vec3 l = uAmbient * mix(uSunTint, uShadeTint * (1.0 - dark), sh);
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

            // the light at world point p: the ambient light (less in the sun's shadow) and every light round it,
            // in bands
            vec3 sceneLight(vec3 p) {
                return sceneLightShaded(p, sceneShadow(p), uShadow.x);
            }

            // the light the air between the eye and p scatters towards the eye where the sun reaches it: shafts
            // through the leaves and past the walls, brightest looking towards the sun, thickest low down
            vec3 sceneRays(vec3 p) {
                if (uRays.x <= 0.0 || uShadow.x <= 0.0) return vec3(0.0);
                vec3 rd = p - uCamera;
                float t = min(length(rd), uRays.y);
                if (t < 0.2) return vec3(0.0);
                rd = normalize(rd);
                float toward = max(dot(rd, uSceneSun), 0.0);
                float phase = 0.25 + 1.6 * pow(toward, 5.0);
                float j = slHash(gl_FragCoord.xy + vec2(0.0, floor(uMist.z * 7.0))) ;
                float lit = 0.0, shade = 0.0;
                for (int i = 0; i < 10; i++) {
                    vec3 x = uCamera + rd * (t * (float(i) + j) / 10.0);
                    float air = exp(-max(x.y - uShadowCentre.y, 0.0) * 0.12);
                    float sun = 1.0 - sceneShadowAt(x, 0.0);
                    lit += sun * air;
                    shade += (1.0 - sun) * air;
                }
                // the shafts, not the haze: light in the air only as far as some of it is in shadow too
                float shafts = min(lit, shade * 1.5) / 10.0;
                float a = uRays.x * phase * shafts * (t / uRays.y);
                return uRayColour * floor(a * 12.0 + 0.5) / 12.0;
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

    /** The texture unit the shadow map is bound to, for every shader of the world. */
    public static final int SHADOW_UNIT = 7;

    /** Set {@code s}'s uniforms - those of {@link #GLSL} - to this light. */
    public void bind(Shader s) {
        bind(s, -1);
    }

    /**
     * {@link #bind}, a point looked up in the shadows {@code bias} metres
     * towards the sun from where it is (below 0: {@link #shadowBias}).
     */
    public void bind(Shader s, float bias) {
        s.set("uAmbient", ambient[0], ambient[1], ambient[2]);
        s.set("uCamera", camera[0], camera[1], camera[2]);
        s.set("uMist", mist, 1f / Math.max(0.3f, mistHeight), time % 2000f, 0f);
        s.set("uMistColour", mistColour[0], mistColour[1], mistColour[2]);
        s.set("uLightTexel", texel);
        s.set("uLights", lights);
        s.set("uShadowVP", shadowViewProj);
        boolean shadows = shadowTexture != 0 && shadowDark > 0;
        s.set("uShadow", shadows ? shadowDark : 0f, shadowReach, shadowTexel, bias < 0 ? shadowBias : bias);
        s.set("uShadowCentre", shadowCentre[0], shadowCentre[1], shadowCentre[2]);
        s.set("uSceneSun", towardSun[0], towardSun[1], towardSun[2]);
        s.set("uSunTint", sunTint[0], sunTint[1], sunTint[2]);
        s.set("uShadeTint", shadeTint[0], shadeTint[1], shadeTint[2]);
        s.set("uRays", shadows ? rays : 0f, raysReach, 0f, 0f);
        s.set("uRayColour", rayColour[0], rayColour[1], rayColour[2]);
        s.set("uShadowMap", SHADOW_UNIT);
        int active = org.lwjgl.opengl.GL33C.glGetInteger(org.lwjgl.opengl.GL33C.GL_ACTIVE_TEXTURE);
        org.lwjgl.opengl.GL33C.glActiveTexture(org.lwjgl.opengl.GL33C.GL_TEXTURE0 + SHADOW_UNIT);
        org.lwjgl.opengl.GL33C.glBindTexture(org.lwjgl.opengl.GL33C.GL_TEXTURE_2D,
                shadows ? shadowTexture : ShadowMap.blank());
        org.lwjgl.opengl.GL33C.glActiveTexture(active);
        for (int i = 0; i < lights; i++) {
            s.set("uLightPos[" + i + "]", lightPos[i * 4], lightPos[i * 4 + 1], lightPos[i * 4 + 2], lightPos[i * 4 + 3]);
            s.set("uLightCol[" + i + "]", lightCol[i * 4], lightCol[i * 4 + 1], lightCol[i * 4 + 2], 0f);
        }
    }
}
