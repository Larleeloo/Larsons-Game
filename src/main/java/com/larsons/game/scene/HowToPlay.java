package com.larsons.game.scene;

import com.larsons.game.core.Game;
import com.larsons.game.input.Action;
import com.larsons.game.input.Input;
import com.larsons.game.ui.Glyphs;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.ItemDef;

import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * How to play: the whole game explained a chapter at a time - getting about,
 * fighting, dodging, sneaking, ammunition, the inventory, cards, people and
 * missions, shops, crafting, fishing, swimming and climbing, horses, the day
 * and the weather, doors and keys, the map and saving, the horde, Creative
 * Mode, the settings. The chapters down the left, the page on parchment to
 * the right with the keys it talks about drawn as they are on what was last
 * touched (a key's cap, or the gamepad's button), so it reads right for the
 * keyboard and the controller alike, and after any rebinding.
 *
 * <p>On the main menu (Play, How to play) and in the pause menu. Up and down
 * (or LB and RB) turn the chapters, the wheel or the right stick scrolls a
 * long page.
 */
final class HowToPlay {

    /**
     * A chapter: its title, an item whose picture heads it (null: a brass
     * cog), the actions its keys table shows, and its paragraphs. In the
     * text, {@code {USE}} is what to press for that {@link Action}, and
     * {@code {MOVE}}, {@code {CAMERA}}, {@code {HOTBAR}} and {@code {CLICK}}
     * are said as the keyboard or the controller has them.
     */
    record Chapter(String title, String icon, List<Action> keys, List<String> text) {}

    static final List<Chapter> CHAPTERS = List.of(
            new Chapter("Getting about", "shoes",
                    List.of(Action.MOVE_FORWARD, Action.MOVE_LEFT, Action.MOVE_BACK, Action.MOVE_RIGHT, Action.RUN,
                            Action.SPRINT, Action.CROUCH, Action.JUMP, Action.CAMERA_LEFT, Action.CAMERA_RIGHT,
                            Action.ZOOM_IN, Action.ZOOM_OUT, Action.CAMERA_HEIGHT, Action.HIDE_HUD),
                    List.of("{MOVE} walks, the way the camera looks. Hold {RUN} to run and {SPRINT} to sprint: "
                                    + "sprinting and climbing spend stamina, the green bar, which fills again as you "
                                    + "slow down.",
                            "{CROUCH} crouches, quieter and lower. {JUMP} jumps, and climbs up onto anything "
                                    + "about as high as you are: a table, a crate, a low wall, a cart.",
                            "{CAMERA} turns the camera round you, and {ZOOM_IN} and {ZOOM_OUT} zoom in and out. "
                                    + "{CAMERA_HEIGHT} steps it from side on to overhead. {HIDE_HUD} hides the HUD "
                                    + "for a clear view.")),
            new Chapter("Fighting", "sword",
                    List.of(Action.ATTACK, Action.HEAVY, Action.SPIN, Action.BLOCK, Action.PARRY, Action.BASH,
                            Action.LOCK, Action.LOCK_NEXT),
                    List.of("{ATTACK} strikes with what is in your hands. Every blow rolls dice over your head in "
                                    + "the colour of the weapon's rarity, and their total is the damage: a Common "
                                    + "weapon rolls one D20, a Mythic one six. A forged weapon can roll its own dice, "
                                    + "even adding or multiplying them.",
                            "{HEAVY} is a heavy attack, one die more, once every ten seconds: the orange bar by the "
                                    + "hotbar fills again. {SPIN} spins and strikes all round you.",
                            "Strike in the air for a jump attack: you come down on everything round you with a die "
                                    + "more than a swing.",
                            "Hold {BLOCK} to raise your shield, or your weapon across you. {PARRY} just as a blow "
                                    + "lands turns it aside, and {BASH} bashes with the shield.",
                            "{LOCK} locks on to the foe nearest the middle of the view: the camera keeps it in sight "
                                    + "and your blows turn to it. {LOCK_NEXT} moves to the next.",
                            "What is in your hands is chosen from the hotbar with {HOTBAR}: a sword and shield, an "
                                    + "axe, a bow, a crossbow, a staff, a wand, a mace or a dagger, each with its own "
                                    + "moves.")),
            new Chapter("Dodging and falls", "feather",
                    List.of(Action.DODGE, Action.JUMP),
                    List.of("Tap {DODGE} to dash: a low burst of three and a half metres, the way you are moving.",
                            "Hold {DODGE} and the dash becomes a combat roll, nearly five metres. From the dive "
                                    + "until you are up again nothing can hurt you, and you roll straight through "
                                    + "your foes.",
                            "Falls hurt, the further the worse. Press {DODGE} in the air and you land in a roll, "
                                    + "unhurt.")),
            new Chapter("Sneaking and foes", "dagger",
                    List.of(Action.CROUCH, Action.ATTACK),
                    List.of("Crouch, keep still, keep to tall grass, the dark, fog or rain, and you are harder to "
                                    + "see. Running, sprinting, riding and fighting make you easier to see.",
                            "When the eye over the stamina bar shuts you are hidden: animals let you come close, and "
                                    + "your blows are sneak attacks, for twice the damage.",
                            "Foes see only what is in their sight, never through walls, rocks or houses. One that "
                                    + "loses you goes to where it last saw you and searches about, then gives up and "
                                    + "goes back. They find their way round walls and cliffs, but cannot climb a "
                                    + "slope too steep to walk up, so the high ground is yours to take.")),
            new Chapter("Weapons and ammo", "arrow_bundle",
                    List.of(Action.ATTACK, Action.INVENTORY),
                    List.of("Every weapon wears something out: the sword and the dagger whetstones, the battle axe "
                                    + "iron wedges, the mace studs, the longbow arrow bundles, the crossbow bolt "
                                    + "bundles, the wand small crystals and the staff large crystals. One is used up "
                                    + "every dozen blows or so, and \"Reloaded\" shows when the next is taken.",
                            "With none left a blade or a mace strikes dull, for half; a bow, a crossbow, a wand or "
                                    + "a staff will not shoot at all. The HUD shows what the weapon in your hand uses "
                                    + "and how many you carry.",
                            "Ammunition goes in your pouch, beside your purse. It never takes an inventory slot, "
                                    + "and you pick it up just by walking over it.",
                            "Forged weapons have tiers. Right-click one in the inventory to see them all, and "
                                    + "upgrade it with upgrade ingots. A tier marked ??? is a mystery until it is "
                                    + "reached.")),
            new Chapter("Inventory and pouch", "satchel",
                    List.of(Action.INVENTORY, Action.USE, Action.DROP, Action.HOTBAR_1, Action.HOTBAR_5),
                    List.of("{INVENTORY} opens the inventory. Click an item to take it up and a slot to put it "
                                    + "down; right-click moves it to or from the hotbar; click outside to drop it. "
                                    + "A-Z and Rarity sort it. A satchel, a backpack or an explorer's pack adds slots "
                                    + "for good.",
                            "{USE} picks up what you stand by, and {DROP} drops what you hold. Coins and ammunition "
                                    + "are never left behind: they come to you as you walk over them (Settings, "
                                    + "Game sets how near), and go in your purse and your pouch, not a slot.",
                            "Everything has a rarity, from Common in white to Mythic in cyan, shown in its glow. "
                                    + "Some weapons carry a rare border: Venom poisons, Mystic strikes harder for "
                                    + "mana, Bonded grows with every foe it has struck.")),
            new Chapter("Classes and cards", "reshuffle_scroll",
                    List.of(Action.CARDS, Action.INVENTORY),
                    List.of("Your class gives you a deck of cards: four in hand to start, and one more every few "
                                    + "minutes. {CARDS} lays them out on the card table, and the game stands still "
                                    + "while it is open. Click a card, or drag it up off the board, to play it.",
                            "What your cards are doing for you stands down the left of the screen, with the time "
                                    + "each has left.",
                            "Felling foes and finishing missions earns experience. Each level brings a perk point "
                                    + "for the perk tree, at the top of the card table. Shops sell more cards.",
                            "Pause, then Compendium, shows every card there is, by class and by rarity, and where "
                                    + "each is found. Pause, then Class, changes your class.")),
            new Chapter("People and missions", "parchment",
                    List.of(Action.USE, Action.JOURNAL),
                    List.of("{USE} talks to whoever you stand by. Choose what to ask about; someone with a mark "
                                    + "over their head has work for you.",
                            "Quests you take are your missions. {JOURNAL} opens the Missions page (it is also a "
                                    + "button in the inventory): what each asks, where, and what it pays. Follow up "
                                    + "to four at once: those are listed at the side of the screen and marked in the "
                                    + "world. The rest wait in the list until you follow them.",
                            "People keep their days: they work at their posts, stroll at noon, and go home at "
                                    + "night and out of storms, coming back out in the morning. Go in after them "
                                    + "and you will find them at home.")),
            new Chapter("Shops and coins", "coins",
                    List.of(Action.USE),
                    List.of("Market stalls, shop counters and some people sell things. {USE} by one opens its "
                                    + "goods beside your inventory: click to buy. A shop that buys pays half what a "
                                    + "thing is worth.",
                            "Coins go straight into your purse, picked up as you walk over them. The purse is shown "
                                    + "in the inventory and on the HUD.")),
            new Chapter("Crafting and brewing", "health_potion",
                    List.of(Action.USE),
                    List.of("At a workbench you make things: the makings, fishing rods, packs, keys, ammunition "
                                    + "and trinkets. At a cauldron you brew potions and cook meals. {USE} by one opens "
                                    + "it: each recipe shows what it takes, and what you have of it.",
                            "Potions are drunk from the hand: health, mana, stamina, swiftness, might, stoneskin, "
                                    + "shadow, gills and more. Scrolls are stronger, are read the same way, and are "
                                    + "never made, only found or bought.")),
            new Chapter("Fishing and hunting", "willow_rod",
                    List.of(Action.ATTACK, Action.USE),
                    List.of("With a rod in your hand and water before you, {CLICK} casts. When the float dips, "
                                    + "strike in time to hook it, then hold and let go of {ATTACK} to keep the fish "
                                    + "inside the catch on the gauge until the line is in. Better rods give you "
                                    + "longer to strike and rarer fish.",
                            "{USE} by a bush picks its berries, fruit or herbs. Food is eaten from the hand.",
                            "Animals roam where they belong. Some run, some fight back; what you fell leaves meat, "
                                    + "hide, wool, feathers or antlers.")),
            new Chapter("Swimming and climbing", "gills_potion",
                    List.of(Action.CROUCH, Action.JUMP, Action.DODGE),
                    List.of("In deep water you swim. {CROUCH} dives under and {JUMP} comes up. Under the water a "
                                    + "breath bar shows; come up before it runs out.",
                            "Walk into vines on a cliff to climb them: forward goes up, back goes down, and left and "
                                    + "right go along. It costs stamina; {JUMP} lets go. Walk off the top of the vines "
                                    + "to climb down them.")),
            new Chapter("Horses", "saddle",
                    List.of(Action.USE, Action.RUN, Action.SPRINT),
                    List.of("With a saddle in your inventory, {USE} by a horse saddles it; {USE} again rides it. "
                                    + "{MOVE} steers, {RUN} canters and {SPRINT} gallops. {USE} gets off once it "
                                    + "stands.")),
            new Chapter("Day, night and weather", null,
                    List.of(Action.TIME_LAPSE),
                    List.of("The day turns: dawn, noon, dusk and night, with the moon's great planet across the "
                                    + "sky. Hold {TIME_LAPSE} to run time fast.",
                            "The weather changes as it will: rain, snow, fog, sandstorms and thunderstorms. In a "
                                    + "storm, people go indoors, and come out again when it passes. It is easier "
                                    + "to sneak about in the dark and the rain.")),
            new Chapter("Doors, keys and chests", "rusty_key",
                    List.of(Action.USE),
                    List.of("{USE} opens doors, goes in and out of buildings, and up and down their stairs.",
                            "A locked door or gate says what opens it: a key (rusty to crystal, and the skeleton "
                                    + "key that opens anything), or a price. Some keys are used up in the lock.",
                            "{USE} opens a chest. Its loot is yours once; a chest you have emptied stays empty. "
                                    + "Barrels, crates and storage chests can be looked in, and things put in and "
                                    + "taken out. Barrels, crates and pots can be smashed.")),
            new Chapter("The map and saving", null,
                    List.of(Action.USE, Action.JUMP, Action.PAUSE),
                    List.of("New Game opens the character creator: who you are, how you look, and your class. "
                                    + "Then the map of the Brass Vale.",
                            "On the map, {MOVE} takes the road that sets off that way, or click a place to walk "
                                    + "there. {USE} goes in. Roads on from a place open once it is cleared: by "
                                    + "reaching it, by finding its way out, by felling its foes, or by finishing "
                                    + "its quest, as the map says.",
                            "The game saves by itself: your place on the map when you arrive, and everything you "
                                    + "carry and have done when you come back from a level. Three slots; Continue "
                                    + "takes up the last.")),
            new Chapter("The horde", "mace",
                    List.of(Action.USE),
                    List.of("The Horde tab: the Risen come in rounds, more and tougher each, through their "
                                    + "entrances and the barricades. How long can you hold?",
                            "Blows and kills pay points. Spend them on the weapons chalked on the boards ({USE} "
                                    + "buys one, or feeds the one you have), a turn of the mystery box, and the gates "
                                    + "to the rest of the map.",
                            "Between rounds, {USE} by a barricade nails a board back on, board by board, for "
                                    + "points. The Risen cannot climb out of their own entrances, and nor can you. "
                                    + "The felled now and then leave a gift: max ammo, insta-kill, double points, "
                                    + "kaboom, carpenter.")),
            new Chapter("Creative Mode", "upgrade_ingot",
                    List.of(),
                    List.of("Type 1 0 2 5 on the main menu and the Dev tab opens: Creative Mode, where the game's "
                                    + "levels are built. Place the land, buildings, furniture, people, foes, animals, "
                                    + "chests, shops, quests and doors; play a level from where you stand; and save "
                                    + "it for the map.",
                            "Ctrl+F searches everything there is to place. Click something placed to change it: its "
                                    + "numbers have sliders and boxes to type in, a shop's stock and a quest's "
                                    + "reward are rows to pick from, and a person's day is set hour by hour.",
                            "The Forge makes weapons of your own: their look, colours, dice, effects and tiers. The "
                                    + "map editor draws the overworld, and the horde editor the horde maps.")),
            new Chapter("Settings and controls", null,
                    List.of(Action.SETTINGS, Action.FULLSCREEN, Action.PAUSE),
                    List.of("{SETTINGS} opens the settings from anywhere, and so does the Settings button: the "
                                    + "display, the game, the pointer, sounds, controls and colours.",
                            "{FULLSCREEN} switches between full screen and the window.",
                            "Every key can be changed on the Controls page, and an Xbox controller works "
                                    + "everywhere: it is used as soon as you touch it. In menus, the d-pad or the "
                                    + "stick moves between buttons, A presses, B goes back, LB and RB change tabs, "
                                    + "and the right stick scrolls.",
                            "The Colours page grades the whole game: presets from Gameboy to N64, or each of the "
                                    + "128 colours your own way. The Sounds page lets you choose your own sounds and "
                                    + "music.")));

    private final Game game;
    private int at;
    private float listScroll, pageScroll;
    private String extra;
    private boolean revealChapter;

    HowToPlay(Game game) {
        this.game = game;
    }

    /** A button under the page, beside Previous and Next ("Practise in the training yard"): {@link #draw} says it was pressed. */
    void offer(String label) {
        extra = label;
    }

    /** Opened afresh: at the top of the chapter it was left on. */
    void open() {
        pageScroll = 0;
        revealChapter = true;
    }

    int chapter() { return at; }

    void show(int chapter) {
        int c = Math.floorMod(chapter, CHAPTERS.size());
        if (c == at) return;
        at = c;
        pageScroll = 0;
        revealChapter = true;
    }

    /** {@code text} with its keys said as {@code in} has them now. */
    static String fill(String text, Input in) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int open = text.indexOf('{', i);
            int close = open < 0 ? -1 : text.indexOf('}', open);
            if (close < 0) {
                out.append(text, i, text.length());
                break;
            }
            out.append(text, i, open);
            out.append(say(text.substring(open + 1, close), in));
            i = close + 1;
        }
        return out.toString();
    }

    private static String say(String token, Input in) {
        boolean pad = in.usingPad();
        return switch (token) {
            case "MOVE" -> pad ? "The left stick" : in.labels(Action.MOVE_FORWARD, Action.MOVE_LEFT, Action.MOVE_BACK,
                    Action.MOVE_RIGHT).replace("/", " ");
            case "CAMERA" -> pad ? "The right stick" : "Dragging with the right mouse button (or the arrow keys)";
            case "HOTBAR" -> pad ? "the d-pad's left and right" : "1 to 5 or the mouse wheel";
            case "CLICK" -> pad ? in.label(Action.ATTACK) : "a click";
            default -> {
                try {
                    yield in.label(Action.valueOf(token));
                } catch (IllegalArgumentException e) {
                    yield token;
                }
            }
        };
    }

    /**
     * The chapters down the left of {@code (x, y, w, h)} and the page on the
     * right, Previous and Next under it. True: the {@link #offer}ed button
     * was pressed.
     */
    boolean draw(Ui ui, float x, float y, float w, float h) {
        Input in = ui.input();
        // turning the chapters: up and down, Page Up and Down - or LB and RB
        if (!in.usingPad()) {
            if (in.repeated(GLFW_KEY_DOWN)) show(at + 1);
            if (in.repeated(GLFW_KEY_UP)) show(at - 1);
        }
        int tab = in.takeTab();
        if (tab != 0) show(at + tab);

        float lw = Math.min(290, Math.max(200, w * 0.24f));
        chapters(ui, x, y, lw, h);
        float px = x + lw + 18, pw = w - lw - 18, ph = h - 50;
        page(ui, px, y, pw, ph);

        // under the page: where it is, Previous, Next, and what it offers
        float by = y + h - 40;
        ui.text(ui.small, "Chapter " + (at + 1) + " of " + CHAPTERS.size(), px, by + 11, Theme.HINT);
        if (ui.button("Next", px + pw - 130, by, 130, 40, false, at < CHAPTERS.size() - 1)) show(at + 1);
        if (ui.button("Previous", px + pw - 270, by, 130, 40, false, at > 0)) show(at - 1);
        boolean pressed = false;
        if (extra != null) {
            float ew = ui.body.width(extra) + 40;
            pressed = ui.button(extra, px + pw - 290 - ew, by, ew, 40);
        }
        revealChapter = false;
        return pressed;
    }

    private void chapters(Ui ui, float x, float y, float w, float h) {
        float row = 36, gap = 4;
        ui.clip(x, y, w, h);
        float ty = y - listScroll;
        for (int i = 0; i < CHAPTERS.size(); i++) {
            Chapter c = CHAPTERS.get(i);
            String label = Ui.fit(ui.body, c.title(), w - 46);
            if (ui.button("", x, ty, w, row, i == at, true)) show(i);
            ui.text(ui.small, String.valueOf(i + 1), x + 10, ty + 10, i == at ? Theme.ACCENT : Theme.ITEM_DISABLED);
            ui.text(ui.body, label, x + 36, ty + 7, i == at ? Theme.ITEM_SELECTED : Theme.ITEM);
            ty += row + gap;
        }
        ui.unclip();
        float all = CHAPTERS.size() * (row + gap) - gap, most = Math.max(0, all - h);
        Input in = ui.input();
        if (most > 0 && in.scroll() != 0 && ui.hover(x, y, w, h)) {
            listScroll -= (float) Math.signum(in.scroll()) * 48;
            in.consumeScroll();
        }
        if (revealChapter) {
            float top = at * (row + gap);
            if (top < listScroll) listScroll = top;
            if (top + row > listScroll + h) listScroll = top + row - h;
        }
        listScroll = Math.max(0, Math.min(most, listScroll));
        if (most > 0) {
            // how far down the list is: a thin brass rail
            float rail = h * h / all;
            ui.rect(x + w + 5, y + (h - rail) * listScroll / most, 3, rail, Theme.withAlpha(Theme.BRASS, 0.7f));
        }
    }

    private void page(Ui ui, float x, float y, float w, float h) {
        Input in = ui.input();
        Chapter c = CHAPTERS.get(at);
        ui.parchment(x, y, w, h);
        ui.clip(x + 2, y + 2, w - 4, h - 4);
        ui.measure();
        float pad = Math.min(44, w * 0.05f), tx = x + pad, tw = w - 2 * pad;
        float ty = y + 22 - pageScroll;
        // its heading: the picture, the title, a rule
        icon(ui, c.icon(), tx + 30, ty + 30, 30);
        ui.text(ui.large, c.title(), tx + 76, ty + 6, Theme.INK);
        ui.text(ui.small, (in.usingPad() ? "Controller" : "Keyboard and mouse") + " · keys as you have them set",
                tx + 78, ty + 42, Theme.INK_FADED);
        ty += 72;
        ui.rect(tx, ty, tw, 2, Theme.withAlpha(Theme.INK_FADED, 0.6f));
        ui.diamond(tx + tw / 2, ty + 1, 5, Theme.INK_FADED);
        ty += 18;
        boolean keys = !c.keys().isEmpty() && tw > 640;
        float textW = keys ? tw - 300 : tw;
        float top = ty;
        for (String p : c.text()) {
            ty += ui.paragraph(ui.body, fill(p, in), tx, ty, textW, Theme.INK) + 12;
        }
        if (!c.keys().isEmpty()) {
            // its keys: beside the text, or under it on a narrow page
            float kx = keys ? tx + textW + 28 : tx, ky = keys ? top : ty + 6, kw = keys ? tw - textW - 28 : tw;
            ui.text(ui.label, "Keys", kx, ky, Theme.INK_FADED);
            ky += 26;
            int cols = keys ? 1 : Math.max(1, (int) (kw / 280));
            float colW = kw / cols;
            for (int i = 0; i < c.keys().size(); i++) {
                Action a = c.keys().get(i);
                float cx = kx + (i % cols) * colW, cy = ky + (i / cols) * 34;
                float gw = Glyphs.draw(ui, in, a, cx, cy, 26);
                ui.text(ui.small, Ui.fit(ui.small, a.label(), colW - Math.max(gw, 60) - 18),
                        cx + Math.max(gw, 60) + 10, cy + 5, Theme.INK);
            }
            if (!keys) ty = ky + ((c.keys().size() + cols - 1) / cols) * 34;
        }
        float most = Math.max(0, ui.lowest() + pageScroll - (y + h - 18));
        ui.unclip();
        if (most > 0) {
            float rail = Math.max(30, h * h / (h + most));
            ui.rect(x + w - 7, y + 6 + (h - 12 - rail) * pageScroll / most, 3, rail, Theme.withAlpha(Theme.INK_FADED, 0.8f));
        }
        if (most > 0 && in.scroll() != 0 && (ui.hover(x, y, w, h) || in.usingPad())) {
            pageScroll -= (float) Math.signum(in.scroll()) * 48;
            in.consumeScroll();
        }
        if (in.repeated(GLFW_KEY_PAGE_DOWN)) pageScroll += h * 0.8f;
        if (in.repeated(GLFW_KEY_PAGE_UP)) pageScroll -= h * 0.8f;
        pageScroll = Math.max(0, Math.min(most, pageScroll));
    }

    /** An item's picture, {@code r} from its middle - or, for none, a brass cog. */
    private void icon(Ui ui, String id, float cx, float cy, float r) {
        ItemDef d = id == null ? null : ItemDef.byId(id);
        ui.disc(cx, cy, r, Theme.withAlpha(Theme.INK, 0.12f));
        if (d == null) {
            ui.cog(cx, cy, r * 0.8f, System.nanoTime() / 4e9, Theme.BRASS);
            return;
        }
        InventoryPanel.icon(ui, game, com.larsons.game.world.Item.of(d), cx, cy, r * 1.25f, 1f, System.nanoTime() / 1e9);
    }
}
