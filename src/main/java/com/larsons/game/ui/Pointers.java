package com.larsons.game.ui;

import com.larsons.game.cards.CardBook;
import com.larsons.game.gfx.Texture;
import com.larsons.game.gfx.Window;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The mouse pointer: her class's (a wand for a Mage, a lute for a Bard, a
 * sword for a Knight, an axe for a Warrior, a gloved hand for a Warlock, a
 * leafy branch for a Druid, a dagger for a Rogue, an arrow for a Ranger, a
 * holy symbol for a Cleric), or one chosen in Pause → Class - any of the
 * nine, a picture of her own ({@code assets/ui/pointers/custom.png}, its
 * hot spot its top-left corner), or the system's arrow.
 *
 * <p>The pointers are 32-pixel pixel art ({@code tools/ui/make_pointers.py}),
 * doubled on a high-density screen ({@code <name>@2x.png}).
 */
public final class Pointers implements AutoCloseable {

    /** The pointers there are, in the order Pause → Class offers them. */
    public static final List<String> NAMES = List.of("wand", "lute", "sword", "axe", "gloved_hand", "leafy_branch",
            "dagger", "arrow", "holy_symbol");
    public static final String CLASS = "class", CUSTOM = "custom", SYSTEM = "system";

    private final Path dir;
    private final Map<String, Long> cursors = new HashMap<>();
    private final Map<String, Texture> icons = new HashMap<>();
    private String shown = "";
    private long customStamp = Long.MIN_VALUE;

    public Pointers(Path assets) {
        this.dir = assets.resolve("ui").resolve("pointers");
    }

    /** What a pointer is called in the menu. */
    public static String label(String name) {
        return switch (name) {
            case CLASS -> "Your class's";
            case CUSTOM -> "Custom (custom.png)";
            case SYSTEM -> "System arrow";
            case "gloved_hand" -> "Gloved hand";
            case "leafy_branch" -> "Leafy branch";
            case "holy_symbol" -> "Holy symbol";
            default -> Character.toUpperCase(name.charAt(0)) + name.substring(1);
        };
    }

    /** The pointer {@code setting} comes to for {@code heroClass}: a name, or {@link #SYSTEM}. */
    public static String resolve(String setting, String heroClass) {
        if (setting == null || setting.isBlank() || setting.equals(CLASS)) {
            CardBook.Hero h = CardBook.get().hero(heroClass);
            return h == null ? SYSTEM : h.pointer();
        }
        return setting;
    }

    /**
     * What the pointer is over in the world this frame - "talk", "attack",
     * "take", "open", "locked", "build", "no" - said by the scene; the
     * interface's own hint (a button, a slider, a text field) comes first.
     */
    private String worldHint = "";

    /** Forget the last frame's hint (each frame starts plain). */
    public void clearHint() {
        worldHint = "";
    }

    /** The scene says what the pointer is over in the world: "talk", "attack", "take", "open", "locked" ... */
    public void hint(String hint) {
        worldHint = hint == null ? "" : hint;
    }

    public String worldHint() { return worldHint; }

    /** Show {@code setting}'s pointer (for {@code heroClass}) over {@code window} - the system's when it has none. */
    public void apply(Window window, String setting, String heroClass) {
        apply(window, setting, heroClass, "", false, false);
    }

    private boolean hidden;
    private final Map<Integer, Long> standard = new HashMap<>();

    /**
     * Show the pointer as it is this frame: {@code setting}'s (for {@code
     * heroClass}), changed by what it is over ({@code uiHint}, else what the
     * scene said: {@link #hint}) - lit over a button, a speech bubble over
     * someone to talk to, swords over a foe ... - pressed in while the
     * button is down; or none while the gamepad moves a focus about a menu.
     */
    public void apply(Window window, String setting, String heroClass, String uiHint, boolean pressed, boolean hide) {
        long h = window.handle();
        if (hide != hidden) {
            hidden = hide;
            glfwSetInputMode(h, GLFW_CURSOR, hide ? GLFW_CURSOR_HIDDEN : GLFW_CURSOR_NORMAL);
        }
        if (hide) return;
        String name = resolve(setting, heroClass);
        String hint = uiHint == null || uiHint.isEmpty() ? worldHint : uiHint;
        if (name.equals(CUSTOM)) {
            long stamp = stamp(dir.resolve("custom.png"));
            if (stamp != customStamp) {           // a new picture dropped in: made again
                customStamp = stamp;
                cursors.entrySet().removeIf(e -> {
                    if (!e.getKey().startsWith(CUSTOM + "@")) return false;
                    if (e.getValue() != null && e.getValue() != 0) glfwDestroyCursor(e.getValue());
                    return true;
                });
                shown = "";
            }
        }
        boolean big = window.scale() >= 1.5f;
        String key = name + "@" + (big ? 2 : 1) + "|" + hint + (pressed ? "|down" : "");
        if (key.equals(shown)) return;
        shown = key;
        if (name.equals(SYSTEM) || hint.equals("text")) {
            // the system's own: an arrow, a hand over a button, a text cursor, a crosshair over a foe
            int shape = switch (hint) {
                case "text" -> GLFW_IBEAM_CURSOR;
                case "click", "talk", "take", "open", "buy" -> name.equals(SYSTEM) ? GLFW_HAND_CURSOR : 0;
                case "slide" -> GLFW_HRESIZE_CURSOR;
                case "attack" -> GLFW_CROSSHAIR_CURSOR;
                default -> 0;
            };
            glfwSetCursor(h, shape == 0 ? MemoryUtil.NULL
                    : standard.computeIfAbsent(shape, org.lwjgl.glfw.GLFW::glfwCreateStandardCursor));
            return;
        }
        long c = cursors.computeIfAbsent(key, k -> make(name, big, hint, pressed));
        glfwSetCursor(h, c);
    }

    /** {@code name}'s picture as the pointer: changed for {@code hint}, pressed in or not. */
    private long make(String name, boolean big, String hint, boolean pressed) {
        BufferedImage img = PointerArt.compose(read(name, big), hint, pressed, big ? 2 : 1);
        if (img == null) return MemoryUtil.NULL;
        int w = img.getWidth(), h = img.getHeight();
        ByteBuffer px = MemoryUtil.memAlloc(w * h * 4);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    px.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
                }
            }
            px.flip();
            GLFWImage g = GLFWImage.malloc(stack);
            g.set(w, h, px);
            int spot = big ? 2 : 1;
            return glfwCreateCursor(g, spot, spot);
        } finally {
            MemoryUtil.memFree(px);
        }
    }

    private final Map<String, Texture> previews = new HashMap<>();

    /** The pointer {@code setting} makes over {@code hint}, drawn at {@code (x, y)} 32 points across - to see. */
    public void preview(Ui ui, String setting, String heroClass, String hint, float x, float y) {
        String name = resolve(setting, heroClass);
        if (name.equals(SYSTEM)) {
            ui.text(ui.tiny, "(system)", x - 8, y + 10, Theme.ITEM_DISABLED);
            return;
        }
        Texture t = previews.computeIfAbsent(name + "|" + hint, k -> {
            BufferedImage img = PointerArt.compose(read(name, true), hint, false, 2);
            return img == null ? null : Texture.of(img, true);
        });
        if (t != null) ui.batch().rect(t, x, y, 32, 32, 0, 0, 1, 1, 1, 1, 1, 1);
    }

    private BufferedImage read(String name, boolean big) {
        Path f = dir.resolve(name + (big ? "@2x" : "") + ".png");
        if (!Files.isRegularFile(f)) f = dir.resolve(name + ".png");
        if (!Files.isRegularFile(f)) return null;
        try {
            return ImageIO.read(f.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    /** {@code name}'s picture, {@code size} points across, at {@code (x, y)} - for the class picker's tiles. */
    public void drawIcon(Ui ui, String name, float x, float y, float size) {
        Texture t = icons.computeIfAbsent(name, n -> {
            BufferedImage img = read(n, true);
            return img == null ? null : Texture.of(img, true);
        });
        if (t != null) ui.batch().rect(t, x, y, size, size, 0, 0, 1, 1, 1, 1, 1, 1);
    }

    private static long stamp(Path f) {
        try {
            return Files.isRegularFile(f) ? Files.getLastModifiedTime(f).toMillis() : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public void close() {
        for (long c : cursors.values()) if (c != 0) glfwDestroyCursor(c);
        cursors.clear();
        for (long c : standard.values()) if (c != 0) glfwDestroyCursor(c);
        standard.clear();
        for (Texture t : previews.values()) if (t != null) t.close();
        previews.clear();
        for (Texture t : icons.values()) if (t != null) t.close();
        icons.clear();
    }
}
