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
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.SpriteView;
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
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * The start of a new playthrough: who she is, in four pages.
 *
 * <ol>
 *   <li><b>Who</b> - her name, her people ({@link People}: Human, Drurinean,
 *       Half Drurinean, Khorigale), her body, her skin tone (her people's),
 *       and how tall she stands (0.7 to 1.3 of the usual height).</li>
 *   <li><b>Look</b> - the wardrobe, with everything a new character owns:
 *       her face, her hair, the clothes she sets out in.</li>
 *   <li><b>Class</b> - the nine classes, each with its deck of cards and the
 *       weapon it starts with.</li>
 *   <li><b>Begin</b> - what she sets out with, and the road.</li>
 * </ol>
 *
 * Beginning writes her to the save slot chosen in the main menu and opens
 * the overworld map at its start.
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

    private final Game game;
    private final int slot;
    private final World world;
    private final WardrobePanel wardrobe;
    private final Widgets widgets = new Widgets();
    private final LayerStack.Memory memory = new LayerStack.Memory();
    private Page page = Page.WHO;
    private String name = "Traveller";
    private People people = People.HUMAN;
    private double size = 1;
    private String heroClass = "";
    private double time;
    private Facing facing = Facing.SOUTH;

    /** A new character for save slot {@code slot}. */
    public CreatorScene(Game game, int slot) {
        this.game = game;
        this.slot = slot;
        this.world = new World(starter(game.sprites(), bodies(game.sprites()).get(0), People.HUMAN));
        this.wardrobe = new WardrobePanel(game);
        wardrobe.setDoneLabel("Next");
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

    /** A new character's look: {@code body} of {@code people}, its face, its first hair, plain clothes. */
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
        w.set(Slot.PANTS, "denim_jeans");
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
        if (!people.skins().contains(w.skin())) w.setColour(Slot.BODY, people.skins().get(0));
    }

    /** The clothes a new character has on are hers: in her bag, so the wardrobe offers them. */
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
        time += dt;
        Input in = game.window().input();
        in.setUiMode(true);
        if (in.pressed(GLFW_KEY_ESCAPE) && !widgets.typing()) {
            in.consumeKey(GLFW_KEY_ESCAPE);
            if (page == Page.WHO) game.switchTo("menu");
            else page = Page.values()[page.ordinal() - 1];
        }
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
        if (page == Page.LOOK) {
            // the wardrobe is an armoire of its own, across the whole window: its button goes on to the next page
            float ww = Math.min(1100, ui.width() - 40), wh = Math.min(640, ui.height() - 40);
            if (wardrobe.draw(ui, 0.016, (ui.width() - ww) / 2, (ui.height() - wh) / 2, ww, wh, world.player())) {
                show(Page.CLASS);
            }
            ui.text(ui.small, "2. Look: her face, her hair, what she sets out in", 16, 8, Theme.HINT);
            widgets.overlay(ui);
            ui.end();
            return;
        }
        ui.panel(x, y, w, h);
        ui.text(ui.large, "A new playthrough", x + 24, y + 14, Theme.TITLE);
        // the pages, along the top
        float tx = x + w - 24;
        for (int i = Page.values().length - 1; i >= 0; i--) {
            Page p = Page.values()[i];
            float tw = ui.body.width((i + 1) + ". " + p.label) + 30;
            tx -= tw;
            boolean on = p == page;
            if (ui.button((i + 1) + ". " + p.label, tx, y + 12, tw - 6, 36, on, p != Page.BEGIN || ready())) show(p);
        }
        ui.rect(x + 20, y + 56, w - 40, 1, Theme.PANEL_EDGE);
        float bx = x + 20, by = y + 68, bw = w - 40, bh = h - 68 - 64;
        switch (page) {
            case WHO -> who(ui, bx, by, bw, bh);
            case LOOK -> { }
            case CLASS -> classes(ui, bx, by, bw, bh);
            case BEGIN -> begin(ui, bx, by, bw, bh);
        }
        // back and next, along the bottom
        float fy = y + h - 52;
        if (ui.button(page == Page.WHO ? "Back to the menu" : "Back", x + 24, fy, 190, 38)) {
            if (page == Page.WHO) game.switchTo("menu");
            else show(Page.values()[page.ordinal() - 1]);
        }
        if (ui.button("Settings", x + 24 + 200, fy, 130, 38)) game.openSettings();
        if (page != Page.BEGIN) {
            boolean can = page != Page.CLASS || !heroClass.isEmpty();
            if (ui.button("Next", x + w - 24 - 190, fy, 190, 38, false, can)) show(Page.values()[page.ordinal() + 1]);
            if (!can) ui.text(ui.small, "Choose a class first", x + w - 24 - 190 - 170, fy + 10, Theme.WARNING);
        }
        widgets.overlay(ui);
        ui.end();
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

    private void who(Ui ui, float x, float y, float w, float h) {
        SpriteLibrary lib = game.sprites();
        Wardrobe wd = world.player().wardrobe();
        float cw = Math.min(600, w * 0.56f);
        float cy = y + 6;
        name = widgets.text(ui, "creator.name", "Her name", name, x, cy, cw);
        if (name.length() > 24) name = name.substring(0, 24);
        cy += 44;

        ui.text(ui.body, "People", x, cy, Theme.ACCENT);
        cy += 28;
        List<People> all = People.playableOnes();
        float pw = (cw - (all.size() - 1) * 8) / all.size();
        for (int i = 0; i < all.size(); i++) {
            People p = all.get(i);
            if (ui.button(p.label(), x + i * (pw + 8), cy, pw, 38, p == people, true) && p != people) {
                people = p;
                features(lib, wd, p);
                size = Math.max(p.smallest(), Math.min(p.tallest(), size));
                Sounds.play(SoundKeys.ui("wear"));
            }
        }
        cy += 46;
        cy += ui.paragraph(ui.small, people.about(), x, cy, cw, Theme.HINT) + 14;

        ui.text(ui.body, "Body", x, cy, Theme.ACCENT);
        cy += 28;
        List<String> bodies = bodies(lib);
        for (int i = 0; i < bodies.size(); i++) {
            String b = bodies.get(i);
            boolean on = b.equals(wd.get(Slot.BODY));
            if (ui.button(capital(b), x + i * 158, cy, 150, 36, on, true) && !on) {
                String skin = wd.skin();
                Wardrobe fresh = starter(lib, b, people);
                fresh.setColour(Slot.BODY, skin);
                for (Slot s : Slot.values()) {
                    wd.set(s, fresh.get(s));
                    if (s != Slot.BODY) wd.setColour(s, null);
                }
                Sounds.play(SoundKeys.ui("wear"));
            }
        }
        cy += 50;

        ui.text(ui.body, "Skin", x, cy, Theme.ACCENT);
        cy += 28;
        String body = wd.get(Slot.BODY);
        List<String> tones = people.skins();
        for (int i = 0; i < tones.size(); i++) {
            String tone = tones.get(i);
            int rgb = lib.swatch(body, Slot.BODY, body, wd.style(), tone);
            float sx = x + i * 54;
            boolean on = tone.equals(wd.skin());
            ui.rect(sx, cy, 46, 34, Theme.SOOT);
            ui.rect(sx + 2, cy + 2, 42, 30, rgb < 0 ? Theme.IRON : Theme.hex(rgb & 0xFFFFFF, 1f));
            ui.outline(sx, cy, 46, 34, on ? 3 : 1, on ? Theme.ACCENT : Theme.PANEL_EDGE);
            if (ui.clicked(sx, cy, 46, 34)) {
                wd.setColour(Slot.BODY, tone);
                Sounds.play(SoundKeys.ui("wear"));
            }
        }
        cy += 50;

        size = widgets.slider(ui, "creator.size", "Height", size, SaveGame.SMALLEST, SaveGame.TALLEST, "%.2f ×",
                x, cy, cw);
        cy += 34;
        ui.paragraph(ui.small, people.label() + " folk usually stand " + String.format(Locale.ROOT, "%.2f - %.2f",
                people.smallest(), people.tallest()) + " of a human's height; she can be any height from "
                + SaveGame.SMALLEST + " to " + SaveGame.TALLEST + ".", x, cy, cw, Theme.ITEM_DISABLED);

        preview(ui, x + cw + 30, y, w - cw - 30, h, true);
    }

    /** Her, as she is so far: turning slowly, as tall as she has been made. */
    private void preview(Ui ui, float x, float y, float w, float h, boolean turning) {
        ui.parchment(x, y, w, h);
        if (turning && (int) (time / 1.4) != (int) ((time - 0.016) / 1.4)) facing = facing.counterClockwise();
        Wardrobe wd = world.player().wardrobe();
        try {
            SpriteView view = new SpriteView(Elevation.SIDE, facing, Elevation.SIDE.renderAngle());
            LayerStack.Result r = LayerStack.resolve(game.sprites(), wd, AnimState.IDLE, time, view, memory);
            float ppm = (float) (Math.min((h - 70) / (r.framing().frameWorldHeight() * SaveGame.TALLEST),
                    (w - 20) / (r.framing().frameWorldSize() * SaveGame.TALLEST)) * size);
            float footY = y + h - 44;
            ui.batch().rect(ui.white(), x + w / 2 - 46, footY - 5, 92, 10, 0, 0, 1, 1, 0, 0, 0, 0.22f);
            // the usual height, marked on the wall behind her
            float usual = (float) (ppm / size * 1.78);
            ui.rect(x + 14, footY - usual, 26, 2, Theme.INK_FADED);
            ui.text(ui.tiny, "1.00", x + 14, footY - usual - 14, Theme.INK_FADED);
            LayerStack.drawFlat(ui.batch(), r, x + w / 2, footY, ppm);
        } catch (RuntimeException e) {
            ui.textCentered(ui.small, "(no sprites for this look)", x + w / 2, y + h / 2, Theme.INK_FADED);
        }
        String who = (name.isBlank() ? "Nameless" : name) + ", " + people.label()
                + (heroClass.isEmpty() ? "" : " " + CardBook.get().hero(heroClass).name());
        ui.textCentered(ui.body, Ui.fit(ui.body, who, w - 16), x + w / 2, y + h - 30, Theme.INK);
    }

    private void classes(Ui ui, float x, float y, float w, float h) {
        ui.text(ui.small, "Your class gives you a deck of cards (J plays them), a weapon to set out with, and a tree "
                + "of perks to earn. It is yours for the whole playthrough.", x, y, Theme.HINT);
        String pick = ClassPicker.draw(ui, game.pointers(), heroClass.isEmpty() ? null : heroClass, x, y + 28, w,
                h - 28);
        if (pick != null) {
            heroClass = pick;
            Sounds.play(SoundKeys.ui("card_shuffle"));
        }
    }

    private void begin(Ui ui, float x, float y, float w, float h) {
        float cw = Math.min(600, w * 0.56f);
        CardBook.Hero hero = CardBook.get().hero(heroClass);
        float cy = y + 6;
        ui.text(ui.large, name.isBlank() ? "Nameless" : name, x, cy, Theme.TITLE);
        cy += 40;
        ui.text(ui.body, people.label() + (hero == null ? "" : "  ·  " + hero.name()), x, cy, Theme.ACCENT);
        cy += 34;
        if (hero != null) cy += ui.paragraph(ui.small, hero.about(), x, cy, cw, Theme.HINT) + 16;
        ui.text(ui.body, "She sets out with", x, cy, Theme.ACCENT);
        cy += 28;
        for (String line : kitLines()) {
            ui.text(ui.small, "·  " + line, x + 6, cy, Theme.ITEM);
            cy += 22;
        }
        cy += 14;
        String map = Overworld.campaignStart(game.settings().assets);
        Overworld o = Overworld.byId(game.settings().assets, map);
        ui.paragraph(ui.small, o == null ? "There is no map to set out on yet: make one in the Dev tab's map editor."
                : "The road begins in " + o.name() + (o.start() == null ? "" : ", at " + o.start().name) + ". Her place "
                + "on the map is saved every time she arrives somewhere.", x, cy, cw, o == null ? Theme.WARNING
                : Theme.HINT);
        boolean can = ready() && o != null;
        if (ui.button("Begin the journey", x, y + h - 54, 300, 50, true, can)) begin();
        if (name.isBlank()) ui.text(ui.small, "She needs a name (page 1)", x + 316, y + h - 38, Theme.WARNING);
        preview(ui, x + cw + 30, y, w - cw - 30, h, true);
    }

    private List<String> kitLines() {
        ItemDef weapon = weaponOf(heroClass);
        List<String> out = new ArrayList<>();
        out.add("a " + weapon.name().toLowerCase(Locale.ROOT) + (weapon == ItemDef.SWORD ? "" : " and a sword"));
        out.add("the clothes she stands in");
        out.add("ammunition for her weapons, a reshuffle scroll and an upgrade ingot");
        out.add("25 coins, and her class's deck of 8 cards");
        return out;
    }

    /** Write her to the slot and open the map. */
    private void begin() {
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
        Saves.play(save, slot);
        Saves.write();
        Sounds.play(SoundKeys.ui("map_enter"));
        game.switchTo(new OverworldScene(game, save));
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
    }

    /** Script commands: {@code page who|look|class|begin}, {@code name ...}, {@code people ...}, {@code class ...}, {@code size n}, {@code begin}. */
    @Override
    public boolean command(String command, String argument) {
        switch (command) {
            case "page" -> {
                for (Page p : Page.values()) if (p.label.equalsIgnoreCase(argument.trim())) page = p;
            }
            case "name" -> name = argument.trim();
            case "people" -> {
                people = People.byKey(argument);
                features(game.sprites(), world.player().wardrobe(), people);
            }
            case "class" -> {
                if (CardBook.get().hero(argument.trim()) != null) heroClass = argument.trim();
            }
            case "size" -> size = Math.max(SaveGame.SMALLEST, Math.min(SaveGame.TALLEST, Double.parseDouble(argument)));
            case "skin" -> world.player().wardrobe().setColour(Slot.BODY, argument.trim());
            case "begin" -> begin();
            default -> {
                return false;
            }
        }
        return true;
    }
}
