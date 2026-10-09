package com.larsons.game.core;

import com.larsons.game.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Launch options ({@code -Dlarsons.*}, same prefix as the engine so the Gradle
 * build forwards them) and the player's saved preferences
 * ({@code config/settings.json}).
 *
 * <pre>
 *   -Dlarsons.gpu=required        warn loudly if the driver is a software rasteriser
 *   -Dlarsons.gpu.msaa=4          multisample anti-aliasing samples (0 = off)
 *   -Dlarsons.gpu.vsync=on        wait for the display's refresh
 *   -Dlarsons.sprites.scale=1     shrink sprite frames on load (0.5 = half size)
 *   -Dlarsons.sprites.vramMB=1536 video memory the sprite cache may use
 *   -Dlarsons.assets=assets       where assets/sprites lives
 *   -Dlarsons.start=menu          start in "menu" or straight in "demo"
 *   -Dlarsons.inventory.slots=25  the player's inventory slots, the 5 of the hotbar included
 *   -Dlarsons.animations=classic  start with the first version of the weapon animations (or "new")
 *   -Dlarsons.script=...          scripted run for automated screenshots (see Autopilot)
 * </pre>
 */
public final class Settings {

    public final boolean gpuRequired;
    public final int msaa;
    public final boolean vsync;
    public final long vramBytes;
    public final Path assets;
    public final Path config;
    public final String start;
    public final String script;
    /** How many slots the player's inventory has, the hotbar's five included. */
    public final int inventorySlots;

    // Saved between runs.
    public double spriteScale;
    public boolean showHud = true;
    public boolean showProps = true;
    /**
     * Draw the first version of the states animated again (the weapon stances'
     * moves, kept in assets/animations_v1/) instead of the new one - to compare.
     */
    public boolean classicAnimations;

    private Settings() {
        gpuRequired = "required".equalsIgnoreCase(System.getProperty("larsons.gpu", ""));
        msaa = intProperty("larsons.gpu.msaa", 4);
        vsync = !isOff(System.getProperty("larsons.gpu.vsync", "on"));
        vramBytes = (long) intProperty("larsons.sprites.vramMB", 1536) << 20;
        assets = resolveAssets();
        config = Path.of("config");
        start = System.getProperty("larsons.start", "menu");
        script = System.getProperty("larsons.script", "");
        inventorySlots = Math.max(com.larsons.game.world.Inventory.HOTBAR,
                intProperty("larsons.inventory.slots", com.larsons.game.world.Inventory.DEFAULT_SLOTS));
        spriteScale = doubleProperty("larsons.sprites.scale", -1);
    }

    public static Settings load() {
        Settings s = new Settings();
        Path file = s.config.resolve("settings.json");
        boolean scaleGiven = s.spriteScale > 0;
        if (Files.isRegularFile(file)) {
            try {
                Map<String, Object> j = Json.asObject(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
                if (!scaleGiven) s.spriteScale = Json.num(j, "spriteScale", 1.0);
                s.showHud = Json.bool(j, "showHud", true);
                s.showProps = Json.bool(j, "showProps", true);
                s.classicAnimations = Json.bool(j, "classicAnimations", false);
            } catch (IOException | RuntimeException e) {
                System.err.println("[settings] ignoring " + file + ": " + e.getMessage());
            }
        }
        if (s.spriteScale <= 0) s.spriteScale = 1.0;
        String anim = System.getProperty("larsons.animations", "").trim().toLowerCase();
        if (anim.equals("classic") || anim.equals("v1")) s.classicAnimations = true;
        else if (anim.equals("new")) s.classicAnimations = false;
        return s;
    }

    public void save() {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("spriteScale", spriteScale);
        j.put("showHud", showHud);
        j.put("showProps", showProps);
        j.put("classicAnimations", classicAnimations);
        try {
            Files.createDirectories(config);
            Files.writeString(config.resolve("settings.json"), Json.stringify(j), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[settings] cannot save: " + e.getMessage());
        }
    }

    /** {@code config/wardrobe.json}. */
    public Path wardrobeFile() {
        return config.resolve("wardrobe.json");
    }

    /** {@code assets/sounds}: the sound pack ({@code audio/SoundPack}). */
    public Path soundsDir() {
        return assets.resolve("sounds");
    }

    /** {@code assets/sprites}. */
    public Path spritesDir() {
        return assets.resolve("sprites");
    }

    /**
     * Where the assets folder is: the property if given, else {@code ./assets}
     * (the repository, under Gradle or IntelliJ), else beside the jar.
     */
    private static Path resolveAssets() {
        String prop = System.getProperty("larsons.assets");
        if (prop != null && !prop.isBlank()) return Path.of(prop).toAbsolutePath();
        Path local = Path.of("assets").toAbsolutePath();
        if (Files.isDirectory(local)) return local;
        try {
            Path jar = Path.of(Settings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path beside = (Files.isDirectory(jar) ? jar : jar.getParent()).resolve("assets");
            if (Files.isDirectory(beside)) return beside;
        } catch (Exception ignored) {
            // fall through to the working directory
        }
        return local;
    }

    private static int intProperty(String name, int fallback) {
        try {
            return Integer.parseInt(System.getProperty(name, "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double doubleProperty(String name, double fallback) {
        try {
            return Double.parseDouble(System.getProperty(name, "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean isOff(String v) {
        String s = v.trim().toLowerCase();
        return s.equals("off") || s.equals("false") || s.equals("0") || s.equals("no");
    }
}
