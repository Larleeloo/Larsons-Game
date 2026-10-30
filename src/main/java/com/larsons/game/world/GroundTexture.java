package com.larsons.game.world;

import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Shader;
import com.larsons.game.gfx.Texture;
import com.larsons.game.sprite.ScenerySprites;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * What the ground looks like at any spot: the grass of {@code
 * assets/scenery/ground/grass.png} and, where the {@link GroundMap} puts them,
 * the other ground textures beside it - {@code dirt.png} on the paths, {@code
 * forest_floor.png} under the groves, {@code gravel.png} round the rock beds,
 * {@code meadow.png} (grass thick with flowers) in open patches - every one a
 * 512 × 512 pixel-art tile repeated every {@code tileWorldSize} metres.
 *
 * <p>All of it is one GLSL function, {@code vec3 groundColour(vec2 xz)}, in
 * {@link #GLSL}: a shader that draws ground pastes that in and calls {@link
 * #bind} before drawing, so every ground drawn - the endless flat floor
 * ({@link VoidRenderer}), and anything else lying on it - matches texel for
 * texel. It must be called where the control flow is the same for the whole
 * screen (its textures' mipmap levels need the neighbouring pixels).
 *
 * <p><b>The edges.</b> The map is coarse - a weight a quarter metre - and
 * smooth; the shader reads it in blocks of two texels and adds two kinds of
 * noise before cutting it at 0.5: a slow wobble, a different one for each
 * texture, so an edge wanders, and a hash a block, so it is ragged. So a
 * path's edge is pixel-hard and frayed like the pixel art round it, never a
 * smooth blend. Far away, where a block is smaller than a pixel, the ragged
 * hash fades out and the edge softens, so it does not sparkle.
 *
 * <p>A texture whose file is missing simply is not drawn (that patch stays
 * grass); with no grass at all {@link #grass()} is false and the caller draws
 * its plain floor instead.
 */
public final class GroundTexture implements AutoCloseable {

    /** The textures it binds, from the first unit {@link #bind} is given: grass, the four, the map. */
    public static final int UNITS = 6;

    /**
     * The uniforms and the function. {@code groundColour(xz)} is the ground's
     * colour at world {@code (x, z)}, before any fog; only meaningful while
     * {@code uGroundTile > 0} (there is grass).
     */
    public static final String GLSL = """
            uniform sampler2D uGroundGrass;
            uniform sampler2D uGroundDirt, uGroundForest, uGroundGravel, uGroundMeadow;
            uniform sampler2D uGroundMap;
            uniform float uGroundTile;    // metres a tile spans; 0: no grass
            uniform float uGroundTexel;   // metres a texel of the tiles spans
            uniform vec4 uGroundRect;     // the map's west/north corner (x, z) and its size each way
            uniform vec4 uGroundHas;      // 1 for each of dirt, forest floor, gravel, meadow that loaded

            float groundHash(ivec2 p) {
                uint h = uint(p.x) * 374761393u + uint(p.y) * 668265263u;
                h = (h ^ (h >> 13u)) * 1274126177u;
                return float((h ^ (h >> 16u)) & 0xffffu) / 65535.0;
            }

            float groundNoise(vec2 p) {
                vec2 i = floor(p), f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                ivec2 c = ivec2(i);
                return mix(mix(groundHash(c), groundHash(c + ivec2(1, 0)), f.x),
                           mix(groundHash(c + ivec2(0, 1)), groundHash(c + ivec2(1, 1)), f.x), f.y);
            }

            vec3 groundColour(vec2 xz) {
                vec2 uv = xz / uGroundTile;
                vec3 c = texture(uGroundGrass, uv).rgb;
                vec3 tiles[4] = vec3[4](texture(uGroundDirt, uv).rgb, texture(uGroundForest, uv).rgb,
                                        texture(uGroundGravel, uv).rgb, texture(uGroundMeadow, uv).rgb);
                // the map, read once for every block of 2 x 2 texels (with the true
                // derivatives, so far away it is read from its mipmaps)
                float blockSize = 2.0 * uGroundTexel;
                vec2 block = floor(xz / blockSize);
                vec2 at = (block + 0.5) * blockSize;
                vec2 m = (at - uGroundRect.xy) / uGroundRect.zw;
                vec2 mx = (xz - uGroundRect.xy) / uGroundRect.zw;
                vec4 w = textureGrad(uGroundMap, m, dFdx(mx), dFdy(mx)) * uGroundHas;
                w *= step(0.0, m.x) * step(m.x, 1.0) * step(0.0, m.y) * step(m.y, 1.0);
                vec2 fw = fwidth(xz);
                float far = clamp(max(fw.x, fw.y) / blockSize - 0.5, 0.0, 1.0);
                float jag = (groundHash(ivec2(block)) - 0.5) * 0.22 * (1.0 - far);
                float e = 0.02 + 0.2 * far;
                // meadow, forest floor, gravel, then dirt over them all: the paths run through everything
                int order[4] = int[4](3, 1, 2, 0);
                for (int i = 0; i < 4; i++) {
                    int k = order[i];
                    float wave = groundNoise(at * 1.7 + vec2(37.0, 11.0) * float(k + 1)) - 0.5;
                    c = mix(c, tiles[k], smoothstep(0.5 - e, 0.5 + e, w[k] + 0.5 * wave + jag));
                }
                return c;
            }
            """;

    private final Texture grass;
    private final Texture[] tiles = new Texture[4];
    /** The map last uploaded ({@link #use}), its version then, and its texture (null: all grass). */
    private GroundMap mapOf;
    private int mapVersion;
    private Texture map;
    private float mapWest = (float) GroundMap.origin(), mapSize = (float) GroundMap.SIZE;
    private final float tile, texel;

    /**
     * Load {@code ground}'s grass and its other textures and upload {@code
     * map}. Must be made on the GL thread. A null ground (or one whose grass
     * will not load) is no grass.
     */
    public GroundTexture(ScenerySprites.Ground ground, GroundMap map) {
        Texture g = ground == null ? null : load(ground.texture());
        grass = g;
        tile = g == null ? 0 : (float) ground.tileWorldSize();
        texel = g == null ? 0 : tile / g.width();
        StringBuilder found = new StringBuilder();
        if (g != null) {
            for (int i = 0; i < tiles.length; i++) {
                Path p = ground.tile(GroundMap.NAMES[i]);
                tiles[i] = p == null ? null : load(p);
                if (tiles[i] != null) found.append(", ").append(GroundMap.NAMES[i]);
            }
            System.out.println("[scenery] ground: " + g.width() + "x" + g.height() + " grass" + found
                    + ", a tile every " + tile + " m");
        }
        use(map);
    }

    /**
     * Draw the ground as {@code ground} maps it from now on (null: all
     * grass): uploaded again only when it is another map, or has been
     * painted on since ({@link GroundMap#version}). Must be called on the GL
     * thread.
     */
    public void use(GroundMap ground) {
        if (ground == mapOf && (ground == null || ground.version() == mapVersion)) return;
        mapOf = ground;
        mapVersion = ground == null ? 0 : ground.version();
        if (map != null) map.close();
        map = grass == null || ground == null || ground.isEmpty() ? null
                : Texture.data(ground.rgba(), ground.cells(), ground.cells());
        if (ground != null) {
            mapWest = (float) ground.west();
            mapSize = (float) ground.size();
        }
    }

    private static Texture load(Path file) {
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) throw new IOException("not an image");
            return Texture.tiled(PixelData.of(image));
        } catch (IOException | RuntimeException e) {
            System.err.println("[scenery] cannot load " + file + ": " + e.getMessage());
            return null;
        }
    }

    /** Whether there is grass (false: the caller draws its plain floor). */
    public boolean grass() { return grass != null; }

    /** Metres one tile spans (0 without grass). */
    public float tileWorldSize() { return tile; }

    /**
     * Bind the textures to units {@code firstUnit} to {@code firstUnit + }
     * {@value #UNITS}{@code - 1} and set {@link #GLSL}'s uniforms on {@code
     * shader}, which must be in use.
     */
    public void bind(Shader shader, int firstUnit) {
        shader.set("uGroundTile", tile);
        shader.set("uGroundTexel", texel);
        String[] names = {"uGroundDirt", "uGroundForest", "uGroundGravel", "uGroundMeadow"};
        shader.set("uGroundGrass", firstUnit);
        for (int i = 0; i < 4; i++) shader.set(names[i], firstUnit + 1 + i);
        shader.set("uGroundMap", firstUnit + 5);
        if (grass == null) return;
        grass.bind(firstUnit);
        float[] has = new float[4];
        for (int i = 0; i < 4; i++) {
            Texture t = tiles[i] != null && map != null ? tiles[i] : grass;
            has[i] = tiles[i] != null && map != null ? 1 : 0;
            t.bind(firstUnit + 1 + i);
        }
        (map != null ? map : grass).bind(firstUnit + 5);
        shader.set("uGroundHas", has[0], has[1], has[2], has[3]);
        shader.set("uGroundRect", mapWest, mapWest, mapSize, mapSize);
    }

    @Override
    public void close() {
        if (grass != null) grass.close();
        for (Texture t : tiles) if (t != null) t.close();
        if (map != null) map.close();
    }
}
