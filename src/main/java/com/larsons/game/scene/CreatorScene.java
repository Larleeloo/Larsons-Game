package com.larsons.game.scene;

import com.larsons.game.actor.People;
import com.larsons.game.audio.Music;
import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.cards.CardBook;
import com.larsons.game.core.Autopilot;
import com.larsons.game.core.Game;
import com.larsons.game.core.Scene;
import com.larsons.game.editor.Widgets;
import com.larsons.game.input.Action;
import com.larsons.game.input.Input;
import com.larsons.game.overworld.Overworld;
import com.larsons.game.save.SaveGame;
import com.larsons.game.save.Saves;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Variants;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Inventory;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Supplies;
import com.larsons.game.world.Wearables;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * The start of a new playthrough: who you are, in four pages, each laid out
 * the same - what there is to choose on the left, the character as they are
 * so far on the right ({@link Portrait}: turned, seen from above, zoomed in
 * on, or the face close up), the pages along the top and Back and Next along
 * the bottom. Any page can be gone to from any other: by its tab, with Back
 * and Next, with Escape, or with the gamepad's shoulder buttons.
 *
 * <ol>
 *   <li><b>Who</b> - a name, your people ({@link People}: Human, Drurinean,
 *       Half Drurinean, Khorigale), a body, a skin tone (your people's usual
 *       ones, or any colour of the palette), and a height (0.7 to 1.3 of the
 *       usual).</li>
 *   <li><b>Look</b> - the face, the hair, the clothes to set out in and the
 *       colour of each ({@link LookEditor}): the hair, the eyes, the skin and
 *       a Khorigale's horns and tail in any colour of the palette.</li>
 *   <li><b>Class</b> - the nine classes, each with its deck of cards and the
 *       weapon it starts with (the character takes it in hand).</li>
 *   <li><b>Begin</b> - what you set out with, and the road.</li>
 * </ol>
 *
 * Beginning writes a playthrough that carries nothing over from any before it
 * to the save slot chosen in the main menu and opens the overworld map at its
 * start.
 */
public final class CreatorScene implements Scene, Autopilot.Scriptable {

    private enum Page {
        WHO("Who"), LOOK("Look"), CLASS("Class"), BEGIN("Begin");

        final String label;

        Page(String label) {
            this.label = label;
        }
    }

    /** What each class sets out with in hand, and the ammunition for it. */
    private static ItemDef weaponOf(String cls) {
        return switch (cls) {
            case "mage" -> ItemDef.WAND;
            case "warlock", "druid" -> ItemDef.CRYSTAL_STAFF;
            case "warrior" -> ItemDef.BATTLE_AXE;
            case "ranger" -> ItemDef.LONGBOW;
            case "rogue", "bard" -> ItemDef.DAGGER;
            case "cleric" -> ItemDef.MACE;
            default -> ItemDef.SWORD;
        };
    }

    /** The pants a new character sets out in, and their colour: brown. */
    private static final String PANTS = "denim_jeans", PANTS_COLOUR = "brown";

    private final Game game;
    private final int slot;
    private final World world;
    private final LookEditor look;
    private final Portrait portrait;
    private final Widgets widgets = new Widgets();
    private Page page = Page.WHO;
    private String name = "Traveller";
    private People people = People.HUMAN;
    private double size = 1;
    private String heroClass = "";

    /** A new character for save slot {@code slot}. */
    public CreatorScene(Game game, int slot) {
        this.game = game;
        this.slot = slot;
        this.world = new World(starter(game.sprites(), bodies(game.sprites()).get(0), People.HUMAN));
        this.look = new LookEditor(game);
        this.portrait = new Portrait(game);
        // (the body is chosen on the first page; horns and a tail are a people's: only their colour is picked)
        look.setFixed(EnumSet.of(Slot.BODY, Slot.HORNS, Slot.TAIL));
        look.setCompact(true);
        ownStarterClothes();
    }

    @Override
    public String name() { return "New game"; }

    @Override
    public void enter() {
        Music.play(MainMenuScene.MUSIC);
    }

    /** The bodies there are sprites for: "feminine", "masculine". */
    static List<String> bodies(SpriteLibrary lib) {
        List<String> out = new ArrayList<>();
        for (String body : List.of("feminine", "masculine")) {
            if (lib.styled(body, Slot.BODY, body, Wardrobe.Style.PIXEL_128) != null
                    && lib.entry(body, Slot.BODY, lib.styled(body, Slot.BODY, body, Wardrobe.Style.PIXEL_128)) != null) {
                out.add(body);
            }
        }
        if (out.isEmpty()) out.add("feminine");
        return out;
    }

    /**
     * A new character's look: {@code body} of {@code people}, its face, its
     * first hair, and plain clothes - a shirt, brown pants and boots.
     */
    static Wardrobe starter(SpriteLibrary lib, String body, People people) {
        Wardrobe w = new Wardrobe();
        w.setStyle(Wardrobe.Style.PIXEL_128);
        w.set(Slot.BODY, body);
        w.setColour(Slot.BODY, people.skins().get(0));
        features(lib, w, people);
        w.set(Slot.HAIR, body.equals("masculine") ? "side_part" : "long_waves");
        w.set(Slot.EYEBROWS, "arched");
        w.set(Slot.UNDERWEAR, "cotton_briefs");
        if (!body.equals("masculine")) w.set(Slot.BRA, "leather_bralette");
        w.set(Slot.SHIRT, "laced_shirt");
        w.set(Slot.PANTS, PANTS);
        w.setColour(Slot.PANTS, PANTS_COLOUR);
        w.unlock(Slot.PANTS, PANTS, PANTS_COLOUR);
        w.set(Slot.SHOES, "leather_ankle_boots");
        return w;
    }

    /** The face and the marks of {@code people} on {@code w}: its ears, its horns and tail; a face if it has none. */
    private static void features(SpriteLibrary lib, Wardrobe w, People people) {
        String body = w.get(Slot.BODY);
        w.set(Slot.EARS, people.ears().get(0));
        for (Slot s : List.of(Slot.EYES, Slot.NOSE, Slot.MOUTH)) {
            if (w.get(s) != null) continue;
            for (String item : lib.items(body, s)) {
                w.set(s, Wardrobe.Style.base(item));
                break;
            }
        }
        w.set(Slot.HORNS, people.horns() ? People.HORNS : null);
        w.set(Slot.TAIL, people.tail() ? People.TAIL : null);
        if (!people.horns()) w.setColour(Slot.HORNS, null);
        if (!people.tail()) w.setColour(Slot.TAIL, null);
        // (a skin picked from the whole palette is kept; one of another people's own becomes this people's)
        if (!Variants.isCustom(w.skin()) && !people.skins().contains(w.skin())) {
            w.setColour(Slot.BODY, people.skins().get(0));
        }
    }

    /** The clothes a new character has on are theirs: in the bag, so the wardrobe offers them. */
    private void ownStarterClothes() {
        Inventory inv = world.player().inventory();
        for (int i = 0; i < inv.size(); i++) inv.set(i, null);
        for (String id : List.of("laced_shirt", "baggy_shirt", "pants", "shoes")) {
            ItemDef d = Wearables.byId(id);
            if (d != null) inv.stow(Item.of(d));
        }
    }

    @Override
    public void update(double dt) {
        Input in = game.window().input();
        in.setUiMode(true);
        if (in.pressed(GLFW_KEY_ESCAPE) && !widgets.typing()) {
            in.consumeKey(GLFW_KEY_ESCAPE);
            back();
        }
        // the gamepad's shoulders turn the pages
        int tab = widgets.typing() ? 0 : in.takeTab();
        if (tab != 0) {
            int to = page.ordinal() + tab;
            if (to >= 0 && to < Page.values().length) show(Page.values()[to]);
        }
    }

    private void back() {
        if (page == Page.WHO) game.switchTo("menu");
        else show(Page.values()[page.ordinal() - 1]);
    }

    // --- drawing -----------------------------------------------------------------------

    @Override
    public void render() {
        Ui ui = game.ui();
        ui.begin();
        widgets.beginFrame(ui);
        ui.rect(0, 0, ui.width(), ui.height(), Theme.BACKGROUND);
        ui.wood(0, 0, ui.width(), ui.height(), Theme.withAlpha(Theme.PANEL, 0.6f));
        float w = Math.min(1180, ui.width() - 40), h = Math.min(680, ui.height() - 40);
        float x = (ui.width() - w) / 2, y = (ui.height() - h) / 2;
        ui.panel(x, y, w, h);
        ui.text(ui.large, "A new playthrough", x + 24, y + 14, Theme.TITLE);
        // the pages, along the top: every one can be gone to from every other
        float tx = x + w - 24;
        for (int i = Page.values().length - 1; i >= 0; i--) {
            Page p = Page.values()[i];
            float tw = ui.body.width((i + 1) + ". " + p.label) + 30;
            tx -= tw;
            if (ui.button((i + 1) + ". " + p.label, tx, y + 12, tw - 6, 36, p == page, true)) show(p);
        }
        ui.rect(x + 20, y + 56, w - 40, 1, Theme.PANEL_EDGE);
        // every page the same: what there is to choose on the left, the character on the right
        float bx = x + 20, by = y + 68, bw = w - 40, bh = h - 68 - 64;
        float pw = Math.min(330, bw * 0.34f), cw = bw - pw - 24;
        switch (page) {
            case WHO -> who(ui, bx, by, cw, bh);
            case LOOK -> look(ui, bx, by, cw, bh);
            case CLASS -> classes(ui, bx, by, cw, bh);
            case BEGIN -> begin(ui, bx, by, cw, bh);
        }
        portrait.hold(heroClass.isEmpty() ? null : stanceOf(weaponOf(heroClass)));
        String who = (name.isBlank() ? "Nameless" : name.trim()) + ", " + people.label()
                + (heroClass.isEmpty() ? "" : " " + CardBook.get().hero(heroClass).name());
        portrait.draw(ui, 0.016, bx + cw + 24, by, pw, bh, world.player().wardrobe(), size, SaveGame.TALLEST, who);
        // back and next, along the bottom
        float fy = y + h - 52;
        if (ui.button(page == Page.WHO ? "Back to the menu" : "Back", x + 24, fy, 190, 38)) back();
        if (ui.button("Settings", x + 24 + 200, fy, 130, 38)) game.openSettings();
        if (page != Page.BEGIN) {
            if (ui.button("Next", x + w - 24 - 190, fy, 190, 38)) show(Page.values()[page.ordinal() + 1]);
        } else {
            boolean can = ready() && Overworld.byId(game.settings().assets,
                    Overworld.campaignStart(game.settings().assets)) != null;
            if (ui.button("Begin the journey", x + w - 24 - 260, fy - 6, 260, 46, true, can)) begin();
        }
        if (game.window().input().padFocusing()) {
            ui.text(ui.small, "LB / RB: the pages    B: back", x + 24 + 200 + 146, fy + 10, Theme.ITEM_DISABLED);
        }
        widgets.overlay(ui);
        ui.end();
    }

    /** The stance a class's weapon is held in: a sword alone is the blade's (no shield comes with it). */
    private static Stance stanceOf(ItemDef weapon) {
        return weapon == ItemDef.SWORD ? Stance.BLADE : weapon.stance();
    }

    private void show(Page p) {
        if (p == page) return;
        widgets.stopTyping();
        page = p;
        Sounds.play(SoundKeys.ui("tab"));
    }

    private boolean ready() {
        return !heroClass.isEmpty() && !name.isBlank();
    }

    private void who(Ui ui, float x, float y, float cw, float h) {
        SpriteLibrary lib = game.sprites();
        Wardrobe wd = world.player().wardrobe();
        float cy = y + 2;
        name = widgets.text(ui, "creator.name", "Name", name, x, cy, Math.min(cw, 560));
        if (name.length() > 24) name = name.substring(0, 24);
        cy += 40;

        ui.text(ui.body, "Your People", x, cy, Theme.ACCENT);
        cy += 26;
        List<People> all = People.playableOnes();
        float pw = (cw - (all.size() - 1) * 8) / all.size();
        for (int i = 0; i < all.size(); i++) {
            People p = all.get(i);
            if (ui.button(p.label(), x + i * (pw + 8), cy, pw, 36, p == people, true) && p != people) {
                people = p;
                features(lib, wd, p);
                size = Math.max(p.smallest(), Math.min(p.tallest(), size));
                Sounds.play(SoundKeys.ui("wear"));
            }
        }
        cy += 42;
        cy += ui.paragraph(ui.small, people.about(), x, cy, cw, Theme.HINT) + 10;

        ui.text(ui.body, "Body", x, cy, Theme.ACCENT);
        List<String> bodies = bodies(lib);
        for (int i = 0; i < bodies.size(); i++) {
            String b = bodies.get(i);
            boolean on = b.equals(wd.get(Slot.BODY));
            if (ui.button(capital(b), x + 80 + i * 158, cy - 4, 150, 32, on, true) && !on) {
                // the same look on the other body: its own first hair, and what the body has no sheets for left off
                Wardrobe fresh = starter(lib, b, people);
                for (Slot s : Slot.values()) {
                    if (s == Slot.BODY || s == Slot.HAIR || s == Slot.BRA || wd.get(s) == null) {
                        wd.set(s, fresh.get(s));
                    }
                }
                Sounds.play(SoundKeys.ui("wear"));
            }
        }
        cy += 40;

        // the skin: the people's usual tones, then every colour there is
        String body = wd.get(Slot.BODY);
        ui.text(ui.body, "Skin", x, cy, Theme.ACCENT);
        String tone = wd.skin();
        ui.text(ui.small, tone == null ? "" : Wearables.colourName(tone), x + 80, cy + 3, Theme.ITEM);
        cy += 26;
        List<String> tones = people.skins();
        for (int i = 0; i < tones.size(); i++) {
            String t = tones.get(i);
            int rgb = Variants.isCustom(t) ? Wearables.rgb(t) : lib.swatch(body, Slot.BODY, body, wd.style(), t);
            if (swatch(ui, x + i * 50, cy, 44, 28, rgb, t.equalsIgnoreCase(wd.skin()))) {
                wd.setColour(Slot.BODY, t);
                Sounds.play(SoundKeys.ui("wear"));
            }
        }
        ui.text(ui.small, "usual for " + people.label() + " folk", x + tones.size() * 50 + 8, cy + 6, Theme.ITEM_DISABLED);
        cy += 36;
        int columns = 25;
        float sw = (float) Math.floor((cw + 2) / columns);
        List<String> any = Wearables.PALETTE;
        for (int i = 0; i < any.size(); i++) {
            String c = any.get(i);
            if (swatch(ui, x + (i % columns) * sw, cy + (i / columns) * 22, sw - 2, 20, Wearables.rgb(c),
                    c.equalsIgnoreCase(wd.skin()))) {
                wd.setColour(Slot.BODY, c);
                Sounds.play(SoundKeys.ui("wear"));
            }
        }
        cy += ((any.size() + columns - 1) / columns) * 22 + 12;

        size = widgets.slider(ui, "creator.size", "Height", size, SaveGame.SMALLEST, SaveGame.TALLEST, "%.2f ×",
                x, cy, Math.min(cw, 560));
        cy += 32;
        ui.paragraph(ui.small, people.label() + " folk usually stand " + String.format(Locale.ROOT, "%.2f - %.2f",
                people.smallest(), people.tallest()) + " of a human's height; you can be any height from "
                + SaveGame.SMALLEST + " to " + SaveGame.TALLEST + ".", x, cy, cw, Theme.ITEM_DISABLED);
    }

    /** A colour to click, outlined when it is the one picked; whether it was clicked. */
    private static boolean swatch(Ui ui, float x, float y, float w, float h, int rgb, boolean on) {
        ui.rect(x, y, w, h, Theme.SOOT);
        ui.rect(x + 1, y + 1, w - 2, h - 2, rgb < 0 ? Theme.IRON : Theme.hex(rgb & 0xFFFFFF, 1f));
        if (on) ui.outline(x - 2, y - 2, w + 4, h + 4, 2, Theme.ACCENT);
        else if (ui.hover(x, y, w, h)) ui.outline(x - 1, y - 1, w + 2, h + 2, 1, Theme.TITLE);
        return ui.clicked(x, y, w, h);
    }

    private void look(Ui ui, float x, float y, float cw, float h) {
        Wardrobe wd = world.player().wardrobe();
        ui.text(ui.small, Ui.fit(ui.small, "Your face, your hair and what you set out in. Pick a row, then its colour "
                + "below.", cw), x, y, Theme.HINT);
        look.draw(ui, x, y + 28, cw, h - 28 - 40, wd, world.player());
        // which hand leads
        float hy = y + h - 32;
        ui.text(ui.body, "Hand", x, hy + 4, Theme.ACCENT);
        Wardrobe.Hand[] hands = Wardrobe.Hand.values();
        for (int i = 0; i < hands.length; i++) {
            String l = hands[i] == Wardrobe.Hand.AUTO ? "Either" : hands[i].label();
            if (ui.button(l, x + 80 + i * 108, hy, 100, 30, wd.hand() == hands[i], true)) wd.setHand(hands[i]);
        }
    }

    private void classes(Ui ui, float x, float y, float cw, float h) {
        ui.text(ui.small, Ui.fit(ui.small, "Your class: a deck of cards (J plays them), a weapon to set out with and "
                + "perks to earn, for the whole playthrough.", cw), x, y, Theme.HINT);
        String pick = ClassPicker.draw(ui, game.pointers(), heroClass.isEmpty() ? null : heroClass, x, y + 28, cw,
                h - 28);
        if (pick != null) {
            heroClass = pick;
            Sounds.play(SoundKeys.ui("card_shuffle"));
        }
    }

    private void begin(Ui ui, float x, float y, float cw, float h) {
        CardBook.Hero hero = CardBook.get().hero(heroClass);
        float cy = y + 2;
        ui.text(ui.large, name.isBlank() ? "Nameless" : name.trim(), x, cy, Theme.TITLE);
        cy += 40;
        ui.text(ui.body, people.label() + (hero == null ? "" : "  ·  " + hero.name()), x, cy, Theme.ACCENT);
        cy += 34;
        if (hero != null) cy += ui.paragraph(ui.small, hero.about(), x, cy, cw, Theme.HINT) + 16;
        ui.text(ui.body, "You set out with", x, cy, Theme.ACCENT);
        cy += 28;
        for (String line : kitLines()) {
            ui.text(ui.small, "·  " + line, x + 6, cy, Theme.ITEM);
            cy += 22;
        }
        cy += 14;
        String map = Overworld.campaignStart(game.settings().assets);
        Overworld o = Overworld.byId(game.settings().assets, map);
        cy += ui.paragraph(ui.small, o == null ? "There is no map to set out on yet: make one in the Dev tab's map "
                + "editor." : "The road begins in " + o.name() + (o.start() == null ? "" : ", at " + o.start().name)
                + ". Your place on the map is saved every time you arrive somewhere.", x, cy, cw,
                o == null ? Theme.WARNING : Theme.HINT) + 14;
        cy += ui.paragraph(ui.small, "A new playthrough starts from nothing: its own day and hour, missions, map, "
                + "bag and tips. Nothing is carried over from another.", x, cy, cw, Theme.ITEM_DISABLED) + 14;
        if (name.isBlank()) {
            ui.text(ui.small, "You need a name (page 1, Who)", x, cy, Theme.WARNING);
            cy += 22;
        }
        if (heroClass.isEmpty()) ui.text(ui.small, "Choose a class first (page 3, Class)", x, cy, Theme.WARNING);
    }

    private List<String> kitLines() {
        List<String> out = new ArrayList<>();
        if (!heroClass.isEmpty()) {
            ItemDef weapon = weaponOf(heroClass);
            out.add("a " + weapon.name().toLowerCase(Locale.ROOT) + (weapon == ItemDef.SWORD ? "" : " and a sword"));
        }
        out.add("the clothes you stand in: a shirt, brown pants and boots");
        out.add("ammunition for your weapons, a reshuffle scroll and an upgrade ingot");
        out.add("25 coins, and your class's deck of 8 cards");
        return out;
    }

    /** Write the character to the slot - a playthrough with nothing of any other in it - and open the map. */
    private void begin() {
        if (heroClass.isEmpty() || name.isBlank()) return;
        Player p = world.player();
        Inventory inv = p.inventory();
        ItemDef weapon = weaponOf(heroClass);
        inv.put(Item.of(weapon), 1, false);
        if (weapon != ItemDef.SWORD) inv.put(Item.of(ItemDef.SWORD), 1, false);
        for (ItemDef a : Supplies.AMMO) inv.put(Item.of(a), 3, true);
        inv.put(Item.of(Supplies.RESHUFFLE_SCROLL), 1, true);
        inv.put(Item.of(Supplies.UPGRADE_INGOT), 1, true);
        p.setCoins(25);
        p.hero().choose(heroClass, p);

        SaveGame save = new SaveGame();
        save.setName(name.trim());
        save.setPeople(people.key());
        save.setSize(size);
        save.setWardrobe(p.wardrobe().copy());
        save.take(p);
        save.setPlace(Overworld.campaignStart(game.settings().assets), "");
        game.freshPlaythrough();
        Saves.play(save, slot);
        Saves.write();
        Sounds.play(SoundKeys.ui("map_enter"));
        game.switchTo(new OverworldScene(game, save));
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
    }

    /**
     * Script commands: {@code page who|look|class|begin}, {@code name ...},
     * {@code people ...}, {@code body ...}, {@code class ...}, {@code size n},
     * {@code skin ...}, {@code colour <slot> <colour>}, {@code layer <slot>},
     * {@code begin}; and the portrait's ({@link Portrait#command}).
     */
    @Override
    public boolean command(String command, String argument) {
        Wardrobe wd = world.player().wardrobe();
        switch (command) {
            case "page" -> {
                for (Page p : Page.values()) if (p.label.equalsIgnoreCase(argument.trim())) page = p;
            }
            case "name" -> name = argument.trim();
            case "people" -> {
                people = People.byKey(argument);
                features(game.sprites(), wd, people);
            }
            case "body" -> {
                Wardrobe fresh = starter(game.sprites(), argument.trim(), people);
                for (Slot s : Slot.values()) {
                    if (s == Slot.BODY || s == Slot.HAIR || s == Slot.BRA || wd.get(s) == null) wd.set(s, fresh.get(s));
                }
            }
            case "class" -> {
                if (CardBook.get().hero(argument.trim()) != null) heroClass = argument.trim();
            }
            case "size" -> size = Math.max(SaveGame.SMALLEST, Math.min(SaveGame.TALLEST, Double.parseDouble(argument)));
            case "skin" -> wd.setColour(Slot.BODY, argument.trim());
            case "colour" -> {
                String[] a = argument.trim().split("\\s+", 2);
                Slot s = Slot.byKey(a[0]);
                if (s != null) wd.setColour(s, a.length > 1 ? a[1] : null);
            }
            case "layer" -> {
                Slot s = Slot.byKey(argument.trim());
                if (s != null) look.focusOn(s);
            }
            case "begin" -> begin();
            default -> {
                return portrait.command(command, argument);
            }
        }
        return true;
    }
}
