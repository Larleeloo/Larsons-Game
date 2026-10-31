package com.larsons.game;

import com.larsons.game.core.Boot;
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
        Game game;
        try {
            game = new Game(settings);
        } catch (Boot.Closed e) {
            // closed on the loading screen: nothing has changed, so there is nothing to save
            System.out.println("[boot] " + e.getMessage());
            System.exit(0);
            return;
        } catch (Boot.Failed e) {
            // the loading screen showed it until a key was pressed; the stack trace is on the console
            System.err.println("[boot] could not start: " + e.getMessage());
            System.exit(1);
            return;
        }
        try (game) {
            game.run();
        }
    }
}
