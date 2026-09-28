package com.larsons.game;

import com.larsons.game.core.Game;
import com.larsons.game.core.MacLauncher;
import com.larsons.game.core.Settings;

/**
 * Entry point.
 *
 * <pre>
 *   ./gradlew runGpu          # what the IntelliJ "Run Game (GPU)" profile runs
 *   ./gradlew run             # the same game, OS picks the GPU
 *   ./gradlew gameJar && java -jar build/libs/larsons-game.jar
 * </pre>
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        // AWT is only ever used off-screen here (PNG decoding, the font atlas,
        // the fallback sprites); GLFW owns the window. Headless keeps AWT off
        // the first thread on macOS, which GLFW needs, and must be settled
        // before anything touches a BufferedImage.
        System.setProperty("java.awt.headless", "true");
        if (MacLauncher.relaunchIfNeeded(args)) return;

        Settings settings = Settings.load();
        try (Game game = new Game(settings)) {
            game.run();
        }
    }
}
