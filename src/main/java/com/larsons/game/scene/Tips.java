package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Settings;
import com.larsons.game.input.Input;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The tutorial's tips in the game: the first time something happens - her
 * first steps, a foe near, someone to talk to, a chest, deep water, the first
 * night, a storm, being hidden - a few words on what to do, on a strip of
 * parchment at the top of the screen, one at a time, for a few seconds. Each
 * comes once in all ({@link Settings#tipsSeen}); none come while Settings,
 * Game, Tutorial tips is off. The whole of it is How to play
 * ({@link HowToPlay}), on the main menu and in the pause menu.
 */
final class Tips {

    /** How long a tip stays up, and how long it takes to come and go, seconds. */
    static final double SHOW = 9, FADE = 0.45;

    /** Every tip, by its id; the keys in them as {@link HowToPlay#fill} says them. */
    static final Map<String, String> TEXT = new LinkedHashMap<>();

    static {
        TEXT.put("move", "{MOVE} walks, {RUN} runs and {JUMP} jumps. {PAUSE} pauses: How to play is there, "
                + "and explains everything.");
        TEXT.put("foe", "A foe! {ATTACK} strikes and {BLOCK} blocks. Tap {DODGE} to dash, hold it to roll. "
                + "{LOCK} locks on.");
        TEXT.put("talk", "{USE} talks to people. A mark over someone's head means they have work for you.");
        TEXT.put("item", "{USE} picks up what lies at your feet. Coins and ammunition come to you as you walk "
                + "over them.");
        TEXT.put("chest", "{USE} opens the chest. Its loot is yours once.");
        TEXT.put("shop", "{USE} opens the shop beside your inventory. Click what you want to buy.");
        TEXT.put("horse", "A horse. With a saddle in your inventory {USE} saddles it; then {USE} rides it.");
        TEXT.put("door", "{USE} goes in and out at doors, and up and down stairs.");
        TEXT.put("quest", "A new mission. {JOURNAL} opens your missions: follow up to four at once.");
        TEXT.put("hurt", "Badly hurt. Keep out of reach for a few seconds and your health comes back, or drink a "
                + "health potion.");
        TEXT.put("ammo", "This weapon is out of ammunition. Find more in chests, buy it in shops, or make it at "
                + "a workbench.");
        TEXT.put("hidden", "You are hidden: the eye over the stamina bar is shut. Your blows are sneak attacks, "
                + "for twice the damage.");
        TEXT.put("swim", "Swimming. {CROUCH} dives and {JUMP} comes up; come up before your breath runs out.");
        TEXT.put("climb", "Climbing. Forward goes up and back goes down; it costs stamina. {JUMP} lets go.");
        TEXT.put("night", "Night falls, and people go home. Hold {TIME_LAPSE} to run time fast.");
        TEXT.put("storm", "A storm: people go indoors until it passes. The rain and the dark help you sneak.");
        TEXT.put("cards", "Your class's cards: {CARDS} lays them out. The game stands still while you choose.");
        TEXT.put("horde", "The Risen come in rounds. Blows and kills pay points: {USE} buys the weapons on the "
                + "boards and nails boards back on the barricades.");
    }

    private final Settings settings;
    private final ArrayDeque<String> waiting = new ArrayDeque<>();
    private String showing;
    private double age;

    Tips(Settings settings) {
        this.settings = settings;
    }

    /** Show the tip {@code id} - once in all, after any already waiting - unless the tips are off. */
    void offer(String id) {
        if (!settings.tutorialTips || !TEXT.containsKey(id) || settings.tipsSeen.contains(id)) return;
        if (id.equals(showing) || waiting.contains(id)) return;
        waiting.add(id);
    }

    /** The tip up now, or null. */
    String showing() { return showing; }

    void update(double dt) {
        if (!settings.tutorialTips) {
            showing = null;
            waiting.clear();
            return;
        }
        if (showing != null) {
            age += dt;
            if (age >= SHOW) showing = null;
        }
        if (showing == null && !waiting.isEmpty()) {
            showing = waiting.poll();
            age = 0;
            settings.tipsSeen.add(showing);
            settings.save();
            Sounds.play(SoundKeys.ui("tab"), 0.5);
        }
    }

    /** Put the tip away now (it has been read). */
    void dismiss() {
        if (showing != null && age < SHOW - FADE) age = SHOW - FADE;
    }

    /** The tip up now, on parchment at the top middle of the screen. */
    void draw(Ui ui) {
        if (showing == null) return;
        Input in = ui.input();
        String text = HowToPlay.fill(TEXT.get(showing), in);
        float a = (float) Math.min(1, Math.min(age / FADE, (SHOW - age) / FADE));
        float w = Math.min(640, ui.width() - 40), tw = w - 84;
        float h = Math.max(58, ui.paragraphHeight(ui.body, text, tw) + 40);
        float x = (ui.width() - w) / 2, y = 14 - (1 - a) * 24;
        if (a < 0.98f) {
            // (coming or going: a plate that fades, the parchment only once it is fully there)
            ui.rect(x, y, w, h, Theme.withAlpha(Theme.rgb(232, 214, 176, 1f), 0.9f * a));
        } else {
            ui.parchment(x, y, w, h);
        }
        ui.outline(x - 1, y - 1, w + 2, h + 2, 1, Theme.withAlpha(Theme.BRASS, a));
        ui.cog(x + 32, y + h / 2, 17, age * 0.6, Theme.withAlpha(Theme.BRASS, a));
        ui.disc(x + 32, y + h / 2, 7, Theme.withAlpha(Theme.BRASS_DARK, a));
        ui.text(ui.tiny, "TIP", x + 62, y + 7, Theme.withAlpha(Theme.INK_FADED, a));
        ui.paragraph(ui.body, text, x + 62, y + 20, tw, Theme.withAlpha(Theme.INK, a));
        // how long it has left: a thin brass line along its foot
        ui.rect(x + 4, y + h - 4, (float) ((w - 8) * Math.max(0, 1 - age / SHOW)), 2, Theme.withAlpha(Theme.BRASS, a));
    }
}
