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

import static org.lwjgl.glfw.GLFW.glfwCreateCursor;
import static org.lwjgl.glfw.GLFW.glfwDestroyCursor;
import static org.lwjgl.glfw.GLFW.glfwSetCursor;

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

    /** Show {@code setting}'s pointer (for {@code heroClass}) over {@code window} - the system's when it has none. */
    public void apply(Window window, String setting, String heroClass) {
        String name = resolve(setting, heroClass);
        if (name.equals(CUSTOM)) {
            long stamp = stamp(dir.resolve("custom.png"));
            if (stamp != customStamp) {           // a new picture dropped in: made again
                customStamp = stamp;
                Long old = cursors.remove(CUSTOM);
                if (old != null && old != 0) glfwDestroyCursor(old);
                shown = "";
            }
        }
        String key = name + "@" + (window.scale() >= 1.5f ? 2 : 1);
        if (key.equals(shown)) return;
        shown = key;
        if (name.equals(SYSTEM)) {
            glfwSetCursor(window.handle(), MemoryUtil.NULL);
            return;
        }
        long c = cursors.computeIfAbsent(key, k -> make(name, window.scale() >= 1.5f));
        glfwSetCursor(window.handle(), c);
    }

    private long make(String name, boolean big) {
        BufferedImage img = read(name, big);
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
        for (Texture t : icons.values()) if (t != null) t.close();
        icons.clear();
    }
}
