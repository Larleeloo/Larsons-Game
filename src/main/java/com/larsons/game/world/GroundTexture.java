package com.larsons.game.world;

import com.larsons.game.gfx.PixelData;
import com.larsons.game.gfx.Shader;
import com.larsons.game.gfx.TextureArray;
import com.larsons.game.sprite.ScenerySprites;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    /** The textures it binds, from the first unit {@link #bind} is given: the tiles, then the maps. */
    public static final int UNITS = 2;

    /**
     * The uniforms and the function. {@code groundColour(xz)} is the ground's
     * colour at world {@code (x, z)}, before any fog; only meaningful while
     * {@code uGroundTile > 0} (there is grass).
     */
    public static final String GLSL = """
            uniform sampler2DArray uGroundTiles;   // layer 0 the grass, then each material's tile
            uniform sampler2DArray uGroundMaps;    // the weights, four materials a layer
            uniform float uGroundTile;    // metres a tile spans; 0: no grass
            uniform float uGroundTexel;   // metres a texel of the tiles spans
            uniform vec4 uGroundRect;     // the map's west/north corner (x, z) and its size each way
            uniform float uGroundHas[16]; // 1 for each material that loaded and is painted somewhere
            uniform float uGroundCovered; // 1 when a rock face is painted over somewhere
            uniform float uGroundTinted;  // 1 when the ground is tinted somewhere

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

            vec3 groundColourOn(vec2 xz, vec2 tex);

            vec3 groundColour(vec2 xz) {
                return groundColourOn(xz, xz);
            }

            // the map's layer (0..) at a block's middle, as groundColour reads it
            vec4 groundLayer(vec2 xz, float layer) {
                float blockSize = 2.0 * uGroundTexel;
                vec2 at = (floor(xz / blockSize) + 0.5) * blockSize;
                vec2 m = (at - uGroundRect.xy) / uGroundRect.zw;
                vec2 mx = (xz - uGroundRect.xy) / uGroundRect.zw;
                float inside = step(0.0, m.x) * step(m.x, 1.0) * step(0.0, m.y) * step(m.y, 1.0);
                return textureGrad(uGroundMaps, vec3(m, layer), dFdx(mx), dFdy(mx)) * inside;
            }

            // how much the rock is painted over at xz (0: bare rock where it is steep), ragged at its edge
            float groundCover(vec2 xz) {
                float blockSize = 2.0 * uGroundTexel;
                float c = groundLayer(xz, 4.0).r * uGroundCovered;
                float jag = (groundHash(ivec2(floor(xz / blockSize)) + ivec2(91, 17)) - 0.5) * 0.2;
                return smoothstep(0.45, 0.55, c + jag);
            }

            // the ground's colour c tinted as painted at xz: its lightness kept, in the tint's colour
            vec3 groundTint(vec3 c, vec2 xz) {
                vec4 t = groundLayer(xz, 5.0) * uGroundTinted;
                float l = dot(c, vec3(0.299, 0.587, 0.114));
                float tl = max(dot(t.rgb, vec3(0.299, 0.587, 0.114)), 0.08);
                vec3 tinted = clamp(t.rgb * (l / tl), 0.0, 1.0);
                return mix(c, tinted, clamp(t.a, 0.0, 1.0));
            }

            // the ground's colour by the map at xz, its tiles laid on at tex (a rock face's own plane)
            vec3 groundColourOn(vec2 xz, vec2 tex) {
                vec2 uv = tex / uGroundTile;
                vec2 gx = dFdx(uv), gy = dFdy(uv);
                vec3 c = textureGrad(uGroundTiles, vec3(uv, 0.0), gx, gy).rgb;
                // the maps, read once for every block of 2 x 2 texels (with the true
                // derivatives, so far away they are read from their mipmaps)
                float blockSize = 2.0 * uGroundTexel;
                vec2 block = floor(xz / blockSize);
                vec2 at = (block + 0.5) * blockSize;
                vec2 m = (at - uGroundRect.xy) / uGroundRect.zw;
                vec2 mx = (xz - uGroundRect.xy) / uGroundRect.zw;
                vec2 mgx = dFdx(mx), mgy = dFdy(mx);
                float inside = step(0.0, m.x) * step(m.x, 1.0) * step(0.0, m.y) * step(m.y, 1.0);
                vec4 w0 = textureGrad(uGroundMaps, vec3(m, 0.0), mgx, mgy) * inside;
                vec4 w1 = textureGrad(uGroundMaps, vec3(m, 1.0), mgx, mgy) * inside;
                vec4 w2 = textureGrad(uGroundMaps, vec3(m, 2.0), mgx, mgy) * inside;
                vec4 w3 = textureGrad(uGroundMaps, vec3(m, 3.0), mgx, mgy) * inside;
                float w[16] = float[16](w0.x, w0.y, w0.z, w0.w, w1.x, w1.y, w1.z, w1.w, w2.x, w2.y, w2.z, w2.w,
                                        w3.x, w3.y, w3.z, w3.w);
                vec2 fw = fwidth(xz);
                float far = clamp(max(fw.x, fw.y) / blockSize - 0.5, 0.0, 1.0);
                float jag = (groundHash(ivec2(block)) - 0.5) * 0.22 * (1.0 - far);
                float e = 0.02 + 0.2 * far;
                // the biomes' ground, then the meadow, the forest floor, the gravel, the
                // dirt, and the roads over them all: the paths run through everything
                int order[16] = int[16](9, 8, 4, 5, 10, 7, 6, 11, 3, 1, 2, 0, 15, 14, 13, 12);
                for (int i = 0; i < 16; i++) {
                    int k = order[i];
                    float wk = w[k] * uGroundHas[k];
                    if (wk <= 0.001) continue;
                    float wave = groundNoise(at * 1.7 + vec2(37.0, 11.0) * float(k + 1)) - 0.5;
                    float t = smoothstep(0.5 - e, 0.5 + e, wk + 0.5 * wave + jag);
                    if (t <= 0.0) continue;
                    c = mix(c, textureGrad(uGroundTiles, vec3(uv, float(k + 1)), gx, gy).rgb, t);
                }
                return c;
            }
            """;

    private final TextureArray tiles;
    private final boolean[] loaded = new boolean[GroundMap.MATERIALS];
    /** The map last uploaded ({@link #use}), its groups' versions then, and its texture (null: all grass). */
    private GroundMap mapOf;
    private final int[] mapVersions = new int[GroundMap.LAYERS];
    private final boolean[] mapPainted = new boolean[GroundMap.LAYERS];
    private TextureArray maps;
    private float mapWest = (float) GroundMap.origin(), mapSize = (float) GroundMap.SIZE;
    private final float tile, texel;
    /** Whether its tiles are another's ({@link #GroundTexture(GroundTexture, GroundMap)}): not closed with it. */
    private final boolean shared;

    /**
     * Load {@code ground}'s grass and its other textures and upload {@code
     * map}. Must be made on the GL thread. A null ground (or one whose grass
     * will not load) is no grass.
     */
    public GroundTexture(ScenerySprites.Ground ground, GroundMap map) {
        shared = false;
        PixelData g = ground == null ? null : load(ground.texture());
        if (g == null) {
            tiles = null;
            tile = texel = 0;
            use(map);
            return;
        }
        tile = (float) ground.tileWorldSize();
        texel = tile / g.width();
        int w = g.width(), h = g.height();
        List<PixelData> layers = new ArrayList<>();
        layers.add(g);
        StringBuilder found = new StringBuilder();
        for (int i = 0; i < GroundMap.MATERIALS; i++) {
            Path p = ground.tile(GroundMap.NAMES[i]);
            PixelData t = p == null ? null : load(p);
            if (t != null && (t.width() != w || t.height() != h)) {
                System.err.println("[scenery] " + p + " is " + t.width() + "x" + t.height() + ", not " + w + "x" + h
                        + " as the grass is - left out");
                t.free();
                t = null;
            }
            loaded[i] = t != null;
            if (t != null) found.append(", ").append(GroundMap.NAMES[i]);
            layers.add(t != null ? t : load(ground.texture()));
        }
        tiles = TextureArray.tiled(layers);
        System.out.println("[scenery] ground: " + w + "x" + h + " grass" + found + ", a tile every " + tile + " m");
        use(map);
    }

    /**
     * The same tiles as {@code tilesOf} - not loaded again - with a map of
     * its own, {@code map}: the ground round a level ({@link
     * SurroundRenderer}). Must be made on the GL thread.
     */
    public GroundTexture(GroundTexture tilesOf, GroundMap map) {
        shared = true;
        tiles = tilesOf.tiles;
        tile = tilesOf.tile;
        texel = tilesOf.texel;
        System.arraycopy(tilesOf.loaded, 0, loaded, 0, loaded.length);
        use(map);
    }

    /**
     * Draw the ground as {@code ground} maps it from now on (null: all
     * grass): uploaded again only when it is another map, or a group of it
     * has been painted on since ({@link GroundMap#version(int)}). Must be
     * called on the GL thread.
     */
    public void use(GroundMap ground) {
        if (tiles == null) return;
        boolean other = ground != mapOf;
        if (!other && ground != null) {
            boolean same = true;
            for (int g = 0; g < GroundMap.LAYERS; g++) same &= ground.version(g) == mapVersions[g];
            if (same) return;
        } else if (!other) {
            return;
        }
        if (other && maps != null && (ground == null || maps.width() != ground.cells())) {
            maps.close();
            maps = null;
        }
        mapOf = ground;
        java.util.Arrays.fill(mapPainted, false);
        if (ground == null) return;
        mapWest = (float) ground.west();
        mapSize = (float) ground.size();
        if (maps == null) maps = TextureArray.data(ground.cells(), ground.cells(), GroundMap.LAYERS);
        boolean changed = false;
        for (int g = 0; g < GroundMap.LAYERS; g++) {
            mapPainted[g] = !ground.isEmpty(g);
            if (!other && ground.version(g) == mapVersions[g]) continue;
            mapVersions[g] = ground.version(g);
            maps.update(g, mapPainted[g] ? ground.rgba(g) : null);
            changed = true;
        }
        if (changed) maps.mipmaps();
    }

    private static PixelData load(Path file) {
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) throw new IOException("not an image");
            return PixelData.of(image);
        } catch (IOException | RuntimeException e) {
            System.err.println("[scenery] cannot load " + file + ": " + e.getMessage());
            return null;
        }
    }

    /** Whether there is grass (false: the caller draws its plain floor). */
    public boolean grass() { return tiles != null; }

    /** Metres one tile spans (0 without grass). */
    public float tileWorldSize() { return tile; }

    /**
     * Bind the textures to units {@code firstUnit} and {@code firstUnit + 1}
     * and set {@link #GLSL}'s uniforms on {@code shader}, which must be in
     * use.
     */
    public void bind(Shader shader, int firstUnit) {
        shader.set("uGroundTile", tile);
        shader.set("uGroundTexel", texel);
        shader.set("uGroundTiles", firstUnit);
        shader.set("uGroundMaps", firstUnit + 1);
        if (tiles == null) return;
        tiles.bind(firstUnit);
        if (maps != null) maps.bind(firstUnit + 1);
        else tiles.bind(firstUnit + 1);
        for (int i = 0; i < GroundMap.MATERIALS; i++) {
            shader.set("uGroundHas[" + i + "]", loaded[i] && maps != null && mapPainted[i / 4] ? 1f : 0f);
        }
        shader.set("uGroundRect", mapWest, mapWest, mapSize, mapSize);
        shader.set("uGroundCovered", maps != null && mapPainted[GroundMap.COVER] ? 1f : 0f);
        shader.set("uGroundTinted", maps != null && mapPainted[GroundMap.TINT] ? 1f : 0f);
    }

    @Override
    public void close() {
        if (tiles != null && !shared) tiles.close();
        if (maps != null) maps.close();
    }
}
