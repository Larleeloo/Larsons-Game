package com.larsons.game.building;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Every kind of building there is, from {@code assets/buildings/}: a folder
 * each ({@code cottage/}, {@code keep/}, ...) with its {@link
 * BuildingProfile} and its views, and {@code textures/}, the patterns any
 * part can be given (brick, plaster, thatch, slate, floorboards, ...).
 *
 * <p>Plain data, no GL - the editor's palette, the level files, the world's
 * walls and the tests read it; the {@link BuildingRenderer} draws from the
 * same folder.
 */
public final class BuildingCatalog {

    /** A pattern: its name, what the editor calls it, and what it suits. */
    public record Texture(String name, String label, String suits) {}

    /** The catalogue {@code assets/buildings/} holds, read once (empty if it is not there). */
    private static BuildingCatalog shared;

    private final Path root;
    private final Map<String, BuildingProfile> kinds;
    private final List<Texture> textures;

    private BuildingCatalog(Path root, Map<String, BuildingProfile> kinds, List<Texture> textures) {
        this.root = root;
        this.kinds = Collections.unmodifiableMap(kinds);
        this.textures = List.copyOf(textures);
    }

    /** The game's own, from {@code assets/buildings}. */
    public static synchronized BuildingCatalog get() {
        if (shared == null) shared = load(Path.of("assets", "buildings"));
        return shared;
    }

    /** Use {@code catalog} as the game's own (tests, another folder). */
    public static synchronized void use(BuildingCatalog catalog) {
        shared = catalog;
    }

    /** The order the editor lists them in; any others after, by name. */
    static final List<String> ORDER = List.of("cottage", "house", "cabin", "windmill", "watermill", "keep",
            "castle_wall", "gatehouse", "round_tower", "square_tower", "turret", "spire", "waterwheel", "stable",
            "outhouse",
            // the furniture
            "bed", "table", "chair", "bench", "stool", "bookshelf", "cupboard", "wardrobe", "fireplace", "counter",
            "barrel", "crate", "pot", "large_pot", "storage_chest", "shop_counter", "rug", "painting",
            "grandfather_clock", "banner", "potted_plant", "alchemy_table", "boiler", "tesla_coil", "distiller",
            "cauldron", "side_table", "nightstand", "armchair", "settee", "rocking_chair", "footstool", "dresser",
            "desk", "tall_shelves", "wall_shelf", "coat_rack",
            // the kitchen and the bathroom
            "stove", "bread_oven", "kitchen_sink", "prep_table", "pot_rack", "pantry_shelf", "dish_rack", "bathtub",
            "washstand", "privy", "towel_rack", "mirror",
            // on the walls, and to show things off
            "deer_head", "boar_head", "bear_head", "crossed_swords", "weapon_rack", "armor_stand", "vase",
            // plants in pots
            "small_plant", "flower_pot", "cactus_pot", "fern_urn", "potted_tree", "hanging_planter", "window_box",
            // the farm
            "hay_bale", "round_bale", "haystack", "trough", "animal_pen", "wheat_field", "cabbage_patch",
            "pumpkin_patch", "scarecrow", "chicken_coop", "beehive", "hand_cart",
            // clutter
            "book_stack", "tableware", "bottles", "grain_sacks", "apple_basket", "firewood", "bucket", "broom",
            "farm_tools", "small_keg", "gem_pile", "berry_bowl", "meat_board",
            // the town's
            "market_stall", "market_stall_awning", "row_house", "shop_house", "workshop", "clocktower", "foundry",
            "wishing_well", "fountain", "notice_board", "stocks", "market_cross", "planter", "statue",
            // the lights' fittings
            "candlestick", "candle", "candelabra", "standing_candelabra", "chandelier", "wall_sconce", "lantern",
            "lantern_post");

    /** Read {@code root}: each folder with a profile, and the patterns. Never fails: what cannot be read is left out. */
    public static BuildingCatalog load(Path root) {
        Map<String, BuildingProfile> found = new LinkedHashMap<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> dirs = Files.list(root)) {
                for (Path d : dirs.sorted().toList()) {
                    if (!Files.isRegularFile(d.resolve("profile.json"))) continue;
                    try {
                        BuildingProfile p = BuildingProfile.load(d);
                        // furniture stands as tall as its model: its top is what she stands on
                        if (p.furniture()) p.measured(BuildingModel.topOf(d));
                        found.put(d.getFileName().toString(), p);
                    } catch (IOException | RuntimeException e) {
                        System.err.println("[buildings] " + d.getFileName() + ": " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("[buildings] " + e.getMessage());
            }
        }
        Map<String, BuildingProfile> kinds = new LinkedHashMap<>();
        for (String k : ORDER) if (found.containsKey(k)) kinds.put(k, found.get(k));
        for (Map.Entry<String, BuildingProfile> e : found.entrySet()) kinds.putIfAbsent(e.getKey(), e.getValue());
        List<Texture> textures = new ArrayList<>();
        Path tj = root.resolve("textures").resolve("textures.json");
        if (Files.isRegularFile(tj)) {
            try {
                Map<String, Object> o = Json.asObject(Json.parse(Files.readString(tj)));
                if (o.get("textures") instanceof List<?> l) {
                    for (Object x : l) {
                        Map<String, Object> t = Json.asObject(x);
                        textures.add(new Texture(Json.str(t, "name", "smooth"), Json.str(t, "label", "Smooth"),
                                Json.str(t, "suits", "")));
                    }
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("[buildings] textures: " + e.getMessage());
            }
        }
        return new BuildingCatalog(root, kinds, textures);
    }

    /** The folder it was read from. */
    public Path root() { return root; }

    /** Every kind, the editor's order. */
    public List<String> keys() { return List.copyOf(kinds.keySet()); }

    /** The kind called {@code key}, or null. */
    public BuildingProfile profile(String key) { return key == null ? null : kinds.get(key); }

    /** The patterns, in their order (a pattern's layer in the renderer is its index here). */
    public List<Texture> textures() { return textures; }

    /** The index of the pattern called {@code name}, or 0 (the first: smooth). */
    public int textureIndex(String name) {
        for (int i = 0; i < textures.size(); i++) if (textures.get(i).name().equals(name)) return i;
        return 0;
    }

    /** The pattern called {@code name}, or null. */
    public Texture texture(String name) {
        for (Texture t : textures) if (t.name().equals(name)) return t;
        return null;
    }
}
