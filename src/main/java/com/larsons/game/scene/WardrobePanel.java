package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Game;
import com.larsons.game.cutscene.CloseupLibrary;
import com.larsons.game.gfx.Font;
import com.larsons.game.input.Input;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.SheetTexture;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteLibrary;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Variants;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.ui.Medieval;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Armour;
import com.larsons.game.world.Forged;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Wearables;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

/**
 * The cosmetics editor, from the pause menu, drawn as an oak armoire standing
 * open ({@link Medieval}): what she wears, section by section, each layer a
 * list to drop down and pick from, and her reflection in a gilded mirror,
 * which can be turned through the eight directions, tipped through the three
 * elevations, played in every state - or brought close, waist up and at
 * rest, from any side.
 *
 * <p>The layers are in three sections, a tab each (LB and RB step through
 * them on the gamepad): <b>Looks</b> - the body and its skin, the hair, the
 * eyes, the ears, the nose, the mouth, the makeup; <b>Clothing</b>; and
 * <b>Armour</b>, plated in iron so it is never taken for the clothes: the
 * helmet, the gorget, the gambeson, the mail, the chestplate, the gauntlets,
 * the bracers, the leg armour and the sabatons - each with the dice it rolls
 * to block a blow ({@link Armour}). A layer armour shares with clothes (a
 * helmet is worn where a hat is) is a row in each section, offering only its
 * own kind. The armour section has the switch that shows the clothes under
 * the armour anyway ({@link Wardrobe#underShown}).
 *
 * <p>What is worn is items ({@link Wearables}): a layer offers the wearables
 * in her inventory - picked up, or out of a chest - and the underwear, the
 * bra and the starter look, which are always hers, each by its name ("Silver
 * wrist band"). What she wears that she does not own (an outfit saved before)
 * stays on and is listed too; once taken off it is offered again only when
 * she owns it. The body, the ears, the eyes, the nose and the mouth are not
 * items: every folder found for the body under {@code assets/sprites/<slot>/}
 * is offered. What is in her hands - the sword, the shield - is not the
 * wardrobe's: the hotbar puts it there ({@link #SLOTS}).
 *
 * <p>On the gamepad, the d-pad moves between the lists, left and right step
 * through a list's choices without opening it, A opens it (and picks), B
 * shuts it.
 *
 * <p>An item with a colour choice shows a gem beside it: click the gem (or
 * the layer's name) and its colours are set out on the dye shelf - its own
 * few as gems, then every colour of the palette under them. For the base
 * body that is the skin tone, which every item showing skin follows. The
 * colours are unlocked with dyes ({@link Wearables#colours}): a colour not
 * yet unlocked for the item is shown locked, and with a dye of it in her bag,
 * clicking it offers to use the dye up and unlock that colour on that item
 * for good ({@link Wearables#useDye}).
 *
 * <p>A style row switches the whole character between the sizes of the
 * pixel art ({@link Wardrobe.Style}); a hand row makes her left- or
 * right-handed. Changes apply to the character immediately and are saved to
 * {@code config/wardrobe.json} when the menu closes.
 *
 * <p>The editor dresses the folk of a level in it too ({@link #drawLook}):
 * then every layer offers everything there is art for - the armour made in
 * the Forge among it - in every colour, and the Looks section has the horns
 * and the tail as well.
 */
public final class WardrobePanel {

    /** The layers the wardrobe dresses: every one but the hands, which hold what the hotbar gives them. */
    static final List<Slot> SLOTS = slots();

    /** The wardrobe's sections, a tab each. */
    enum Section {
        LOOKS("Looks"), CLOTHING("Clothing"), ARMOUR("Armour");

        final String label;

        Section(String label) {
            this.label = label;
        }
    }

    /** Which of a slot's choices a row offers: all of them, the clothes alone, or the armour alone. */
    enum Kind { ALL, CLOTHES, ARMOUR }

    /** One row of a section: its slot, what it is called there, and which choices it offers. */
    record Row(Slot slot, String label, Kind kind) {}

    static final List<Row> LOOKS = List.of(new Row(Slot.BODY, "Body · skin", Kind.ALL),
            new Row(Slot.HAIR, "Hair", Kind.ALL), new Row(Slot.EYES, "Eyes", Kind.ALL),
            new Row(Slot.EYEBROWS, "Eyebrows", Kind.ALL), new Row(Slot.EARS, "Ears", Kind.ALL),
            new Row(Slot.NOSE, "Nose", Kind.ALL), new Row(Slot.MOUTH, "Mouth", Kind.ALL),
            new Row(Slot.MAKEUP, "Makeup", Kind.ALL));
    static final List<Row> CLOTHING = List.of(new Row(Slot.SHIRT, "Shirt · dress", Kind.CLOTHES),
            new Row(Slot.PANTS, "Pants", Kind.ALL), new Row(Slot.SHOES, "Shoes", Kind.CLOTHES),
            new Row(Slot.HAT, "Hat", Kind.CLOTHES), new Row(Slot.OTHER, "Cape · other", Kind.ALL),
            new Row(Slot.BELT, "Belt", Kind.ALL), new Row(Slot.GLOVES, "Gloves", Kind.CLOTHES),
            new Row(Slot.UNDERWEAR, "Underwear", Kind.ALL), new Row(Slot.BRA, "Bra", Kind.ALL),
            new Row(Slot.NECKLACE, "Necklace", Kind.CLOTHES), new Row(Slot.EARRINGS, "Earrings", Kind.ALL),
            new Row(Slot.EYEWEAR, "Eyewear", Kind.ALL), new Row(Slot.WRISTWEAR, "Right wrist", Kind.CLOTHES),
            new Row(Slot.WRISTWEAR_LEFT, "Left wrist", Kind.CLOTHES), new Row(Slot.SHEATH, "Sheath", Kind.ALL));
    static final List<Row> ARMOUR = List.of(new Row(Slot.HAT, "Helmet", Kind.ARMOUR),
            new Row(Slot.NECKLACE, "Gorget", Kind.ARMOUR), new Row(Slot.SHIRT, "Gambeson · jerkin", Kind.ARMOUR),
            new Row(Slot.CHAINMAIL, "Mail shirt", Kind.ALL), new Row(Slot.CHESTPLATE, "Chestplate", Kind.ALL),
            new Row(Slot.GLOVES, "Gauntlets", Kind.ARMOUR), new Row(Slot.WRISTWEAR, "Right bracer", Kind.ARMOUR),
            new Row(Slot.WRISTWEAR_LEFT, "Left bracer", Kind.ARMOUR),
            new Row(Slot.CHAINMAIL_LEGS, "Mail leggings", Kind.ALL),
            new Row(Slot.LEG_PLATING, "Leg plating", Kind.ALL), new Row(Slot.SHOES, "Sabatons", Kind.ARMOUR));

    /** The Looks of someone dressed in the editor: her people's horns and tail are chosen here too. */
    static final List<Row> LOOKS_EVERYTHING = everythingLooks();

    private static List<Row> everythingLooks() {
        List<Row> out = new ArrayList<>(LOOKS);
        out.add(new Row(Slot.HORNS, "Horns", Kind.ALL));
        out.add(new Row(Slot.TAIL, "Tail", Kind.ALL));
        return List.copyOf(out);
    }

    static List<Row> rows(Section s) {
        return switch (s) {
            case LOOKS -> LOOKS;
            case CLOTHING -> CLOTHING;
            case ARMOUR -> ARMOUR;
        };
    }

    /** The rows of section {@code s} for whom it dresses now. */
    private List<Row> rowsNow(Section s) {
        return s == Section.LOOKS && everything != null ? LOOKS_EVERYTHING : rows(s);
    }

    /** What the button that closes it says ("Back"; the character creator's: "Next"). */
    private String doneLabel = "Back";

    public void setDoneLabel(String label) {
        doneLabel = label;
    }

    private static final float[] STEEL = Theme.rgb(62, 66, 74, 1f), STEEL_LIGHT = Theme.rgb(150, 158, 170, 1f),
            STEEL_TEXT = Theme.rgb(226, 232, 240, 1f);

    private final Game game;
    private final LayerStack.Memory previewMemory = new LayerStack.Memory();
    private Medieval skin;
    private Facing facing = Facing.SOUTH_EAST;
    private Elevation elevation = Elevation.MIDDLE;
    private AnimState state = AnimState.REST;
    private boolean spin = true, closeup;
    private double time, spinTimer;
    private Section section = Section.CLOTHING;
    /** The layer whose colours the dye shelf shows. */
    private Slot focus = Slot.HAIR;
    /** A locked colour of the focused layer she has clicked and has a dye of: offered to be used up, or null. */
    private String offered;
    // the list dropped down: its row, its choices, where it hangs, how far it is scrolled
    private Row listRow;
    private List<Option> listOptions = List.of();
    private float listX, listY, listW;
    private int listScroll;
    private static final int LIST_ROWS = 10;
    private static final float LIST_ROW_H = 26;
    // whom it dresses now: her, from what she owns - or, in the editor, anyone, from everything there is
    private Wardrobe wd;
    private Player player;
    private LookEditor everything;
    private boolean changed;

    public WardrobePanel(Game game) {
        this.game = game;
    }

    /** Shut the list dropped down or the close-up (Esc, the gamepad's B); whether there was one to shut. */
    public boolean back() {
        if (listRow != null) {
            listRow = null;
            return true;
        }
        if (closeup) {
            closeup = false;
            return true;
        }
        return false;
    }

    private static List<Slot> slots() {
        List<Slot> out = new ArrayList<>();
        // (horns and a tail are her people's: the character creator's, not the wardrobe's)
        for (Slot s : Slot.values()) if (!s.carried() && s != Slot.HORNS && s != Slot.TAIL) out.add(s);
        return List.copyOf(out);
    }

    /**
     * One choice for a slot: the item folder it puts there (null: nothing),
     * and the wearable that is (null for a free layer's folder, or a folder
     * no wearable puts there).
     */
    record Option(String item, ItemDef wearable, Forged forged) {

        Option(String item, ItemDef wearable) {
            this(item, wearable, null);
        }

        /** What the row says: the wearable's name ("Silver wrist band"), a forged piece's, else the folder's. */
        String label(Slot slot) {
            if (forged != null) return forged.name() + " (forged)";
            if (item == null || wearable == null) return WardrobePanel.label(slot, item);
            List<String> folders = wearable.wearable().folders();
            return folders.size() == 1 ? wearable.name() : wearable.name() + " (" + item.replace('_', ' ') + ")";
        }

        /** The colour it is always worn in (a metal's, a tint's, a forged piece's look), or null. */
        String colour() {
            if (forged != null) return Armour.forgedLook(forged);
            return wearable == null ? null : wearable.wearable().colour();
        }
    }

    /**
     * The choices for a slot: nothing (or the fallback body), then
     * <ul>
     *   <li>for a free layer (the body, the ears, the eyes, the nose, the
     *       mouth: {@link Wearables#FREE}), every item folder found for the
     *       body worn;</li>
     *   <li>for any other layer, the wearables she owns that go there
     *       ({@link Wearables#owned}): each of their folders (a sheath on
     *       either hip; a wrist band on either wrist) - and what she wears
     *       there now, owned or not.</li>
     * </ul>
     * The hands are not the wardrobe's: they offer nothing.
     */
    static List<Option> options(SpriteLibrary lib, Slot slot, Player player) {
        List<Option> out = new ArrayList<>();
        out.add(new Option(null, null));
        if (slot.carried()) return out;
        Wardrobe wardrobe = player.wardrobe();
        if (Wearables.free(slot)) {
            // the items drawn for the body worn (every body under the body itself)
            for (String item : lib.items(wardrobe.get(Slot.BODY), slot)) {
                String base = Wardrobe.Style.base(item);
                if (Stance.ofItem(base) != null) continue;
                Option o = new Option(base, null);
                if (!out.contains(o)) out.add(o);        // (an item with only pixel art is listed too)
            }
            return out;
        }
        for (ItemDef d : Wearables.owned(player.inventory())) {
            if (!d.wearable().slots().contains(slot)) continue;
            for (String folder : d.wearable().folders()) {
                Option o = new Option(folder, d);
                if (!out.contains(o)) out.add(o);
            }
        }
        // armour forged in the Forge: each piece its own choice, in its own colours
        for (Item i : player.inventory()) {
            if (i == null || i.forged() == null || !i.def().isWearable() || !i.def().wearable().slots().contains(slot)) {
                continue;
            }
            Forged f = i.forged().at(0);
            boolean listed = false;
            for (Option o : out) listed |= o.forged() != null && o.forged().id().equals(f.id());
            if (!listed) out.add(new Option(i.def().wearable().folders().get(0), i.def(), f));
        }
        // what she wears and no longer owns stays on until it is taken off
        String worn = wardrobe.get(slot);
        if (worn != null && current(out, slot, wardrobe) < 0) {
            String forgedId = Armour.forgedId(wardrobe.colour(slot));
            Forged f = forgedId == null ? null : Forged.byId(forgedId);
            out.add(f != null ? new Option(worn, f.def(), f)
                    : new Option(worn, Wearables.wornAs(slot, worn, forgedId != null ? null : wardrobe.colour(slot))));
        }
        return out;
    }

    /** Which of {@code options} is worn in {@code slot}: its folder, in its colour if it has one; or -1. */
    static int current(List<Option> options, Slot slot, Wardrobe wardrobe) {
        String item = wardrobe.get(slot), colour = wardrobe.colour(slot);
        // (the one in that very look first: a bronze helmet is the iron helmet's folder, in bronze)
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            if (java.util.Objects.equals(o.item(), item) && o.colour() != null && o.colour().equals(colour)) return i;
        }
        boolean look = Armour.look(colour);
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            if (java.util.Objects.equals(o.item(), item) && o.colour() == null && !look) return i;
        }
        return -1;
    }

    /**
     * Wear {@code option} in {@code slot}: its folder, and a metal's colour
     * with it (taking a metal off takes its colour off too).
     */
    static void choose(Wardrobe wardrobe, Slot slot, Option option) {
        String was = wardrobe.get(slot);
        wardrobe.set(slot, option.item());
        if (option.colour() != null) wardrobe.setColour(slot, option.colour());
        else if (was != null && (Wearables.metal(slot, was) || Armour.look(wardrobe.colour(slot)))) {
            wardrobe.setColour(slot, null);
        }
    }

    /**
     * The colours of what is worn in {@code slot}: its {@code variants.json}'s,
     * each unlocked or not ({@link Wearables#colours}).
     */
    static List<Wearables.Colour> colours(SpriteLibrary lib, Slot slot, Player player) {
        Wardrobe wardrobe = player.wardrobe();
        String item = wardrobe.get(slot);
        if (item == null) return List.of();
        List<String> all = lib.colourOptions(wardrobe.get(Slot.BODY), slot, item, wardrobe.style());
        return Wearables.colours(slot, item, all, wardrobe.colour(slot), Wearables.owned(player.inventory()),
                wardrobe);
    }

    /**
     * Click colour {@code c} of what is worn in {@code slot}: put it on if it
     * is unlocked; otherwise, with a dye of it in her bag, offer it (the
     * caller asks before the dye is used up); otherwise nothing. Returns the
     * colour now offered, or null.
     */
    static String pick(Wardrobe wardrobe, Player player, Slot slot, Wearables.Colour c) {
        if (c.unlocked()) {
            wardrobe.setColour(slot, c.option());
            return null;
        }
        return Wearables.dyeIn(player.inventory(), c.option()) != null ? c.option() : null;
    }

    /** What an item folder is called on its row: "gold_pendant" -> "Gold pendant". */
    static String label(Slot slot, String item) {
        if (item == null) return slot == Slot.BODY ? "Fallback (32 px)" : "None";
        // (the long ears are the Drurineans')
        String s = (slot == Slot.EARS && item.startsWith("elven") ? "drurinean" + item.substring(5) : item)
                .replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "royal_blue" -> "Royal blue". */
    static String colourName(String option) {
        return Wearables.colourName(option);
    }

    /** The colour {@code option} of {@code item} shows as (RGB; a palette colour is itself), or -1. */
    private static int swatch(SpriteLibrary lib, String body, Slot slot, String item, Wardrobe.Style style,
                              String option) {
        if (option == null) return -1;
        if (Variants.isCustom(option)) return Wearables.rgb(option);
        return lib.swatch(body, slot, item, style, option);
    }

    /** How many dyes of {@code colour} she carries. */
    private static int dyes(Player player, String colour) {
        ItemDef d = Wearables.dye(colour);
        int n = 0;
        for (Item item : player.inventory()) if (item.def() == d) n++;
        return n;
    }


    // --- a row's choices ---------------------------------------------------------------

    /** Whether {@code o} is armour: a piece that rolls dice to block, or one forged. */
    static boolean armour(Option o) {
        return o.forged() != null || o.wearable() != null && Wearables.armour(o.wearable());
    }

    /** The choices of {@code row}: "nothing" and those of its kind. */
    static List<Option> rowOptions(SpriteLibrary lib, Row row, Player player) {
        return rowOptions(options(lib, row.slot(), player), row);
    }

    /** Of {@code all} a slot's choices, those of {@code row}: "nothing" and those of its kind. */
    static List<Option> rowOptions(List<Option> all, Row row) {
        if (row.kind() == Kind.ALL) return all;
        List<Option> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Option o = all.get(i);
            if (i == 0 || armour(o) == (row.kind() == Kind.ARMOUR)) out.add(o);
        }
        return out;
    }

    /**
     * What is worn in {@code row}'s slot that is of the other kind (a helmet
     * where the hat row is, a hat where the helmet row is), by name; null when
     * it is the row's own kind, or nothing.
     */
    static String otherWorn(SpriteLibrary lib, Row row, Player player) {
        return otherWorn(options(lib, row.slot(), player), row, player.wardrobe());
    }

    /** {@link #otherWorn(SpriteLibrary, Row, Player)}, of {@code all} the slot's choices and {@code wardrobe}. */
    static String otherWorn(List<Option> all, Row row, Wardrobe wardrobe) {
        if (row.kind() == Kind.ALL || wardrobe.get(row.slot()) == null) return null;
        int at = current(all, row.slot(), wardrobe);
        if (at <= 0) return null;
        Option o = all.get(at);
        return armour(o) == (row.kind() == Kind.ARMOUR) ? null : o.label(row.slot());
    }

    /** Wear {@code o} by {@code row}: "nothing" in a row of one kind leaves the other kind's on. */
    static void chooseIn(Wardrobe wardrobe, Row row, Option o, String otherWorn) {
        if (o.item() == null && otherWorn != null) return;
        choose(wardrobe, row.slot(), o);
    }

    // --- whom it dresses now ------------------------------------------------------------

    /** The choices for {@code slot}: what she owns, or (the editor) everything there is. */
    private List<Option> opts(Slot slot) {
        return player != null ? options(game.sprites(), slot, player) : everything.options(slot, wd, null);
    }

    private List<Option> rowOpts(Row row) {
        return rowOptions(opts(row.slot()), row);
    }

    private String other(Row row) {
        return otherWorn(opts(row.slot()), row, wd);
    }

    /** The colours of what is worn in {@code slot}: hers, each unlocked or not; or (the editor) every one. */
    private List<Wearables.Colour> cols(Slot slot) {
        return player != null ? colours(game.sprites(), slot, player) : everything.colours(slot, wd, null);
    }

    private int dyesOf(String colour) {
        return player == null ? 0 : dyes(player, colour);
    }

    private String pickColour(Slot slot, Wearables.Colour c) {
        if (player != null) return pick(wd, player, slot, c);
        wd.setColour(slot, c.option());
        return null;
    }

    /**
     * Dress {@code wardrobe} in the editor (a level's foe or person), over
     * {@code x, y, w, h}: from everything there is art for, in every colour,
     * the Forge's armour too. Returns whether its done button was clicked;
     * {@link #takeChanged} says whether anything was changed.
     */
    public boolean drawLook(Ui ui, double dt, float x, float y, float w, float h, Wardrobe wardrobe, String title) {
        if (everything == null) {
            everything = new LookEditor(game);
            everything.setEverything(true);
        }
        Wardrobe was = wd;
        if (was != wardrobe) {
            listRow = null;
            closeup = false;
            offered = null;
        }
        wd = wardrobe;
        player = null;
        var before = wardrobe.toJson();
        boolean done = page(ui, dt, x, y, w, h, title);
        if (!before.equals(wardrobe.toJson())) changed = true;
        return done;
    }

    /** Whether the look was changed since this was last asked. */
    public boolean takeChanged() {
        boolean c = changed;
        changed = false;
        return c;
    }

    // --- drawing ---------------------------------------------------------------------------

    /**
     * Draw the whole page - the armoire filling {@code x, y, w, h}, its
     * title on its cornice - and handle it. Returns whether its Back button
     * was clicked.
     */
    boolean draw(Ui ui, double dt, float x, float y, float w, float h, Player player) {
        wd = player.wardrobe();
        this.player = player;
        return page(ui, dt, x, y, w, h, "Wardrobe");
    }

    private boolean page(Ui ui, double dt, float x, float y, float w, float h, String title) {
        if (skin == null) skin = new Medieval(ui);
        Medieval m = skin;
        time += dt;
        if (spin) {
            spinTimer += dt;
            if (spinTimer > (closeup ? 2.4 : 0.9)) {
                spinTimer = 0;
                facing = facing.counterClockwise();
            }
        }
        int tab = ui.input().takeTab();
        if (tab != 0) {
            listRow = null;
            section = Section.values()[Math.floorMod(section.ordinal() + tab, Section.values().length)];
            Sounds.play(SoundKeys.ui("tab"));
        }
        // a list dropped down takes the click first, and the gamepad's focus stays in it
        if (listRow != null) {
            listClick(ui);
            ui.targetsOff(listRow != null);
        }

        // --- the armoire: its doors open, its boards, its cornice and its iron --------
        float door = Math.min(84, (ui.width() - w) / 2 - 4);
        if (door > 30) {
            m.door(x, y + 30, h - 40, door, -1);
            m.door(x + w, y + 30, h - 40, door, 1);
        }
        m.shadow(x, y, w, h, 12);
        m.boards(x, y, w, h, 96, 0.92f);
        m.bevel(x, y, w, h, 4, 1f);
        float corniceH = 66;
        m.moulding(x - 6, y - 6, w + 12, 16, 0.85f);
        m.moulding(x + 4, y + 10, w - 8, 12, 0.72f);
        m.moulding(x + 10, y + corniceH - 14, w - 20, 10, 0.78f);
        float pw = Math.min(w - 300, Math.max(300, m.title.width(title) + 60)), ph = 44;
        String shownTitle = fit(m.title, title, pw - 24);
        m.plaque(x + (w - pw) / 2, y + 14, pw, ph);
        m.carvedCentred(m.title, shownTitle, x + w / 2, y + 14 + (ph - m.title.lineHeight()) / 2, Medieval.GOLD);
        boolean back = m.button(doneLabel, x + w - 128, y + 22, 100, 34, false, true);
        m.bracket(x, y, 54, 1, 1);
        m.bracket(x + w, y, 54, -1, 1);
        m.bracket(x, y + h, 54, 1, -1);
        m.bracket(x + w, y + h, 54, -1, -1);

        // --- the open cabinet: a recess with the shelves on the left, the mirror on the right
        float ix = x + 20, iy = y + corniceH + 6, iw = w - 40, ih = h - corniceH - 26;
        m.recess(ix, iy, iw, ih, 14);
        float cx = ix + 22, cy = iy + 16, cw = iw - 44, ch = ih - 28;
        float mirrorW = Math.min(320, cw * 0.33f);
        float listW = cw - mirrorW - 22;
        shelves(ui, m, cx, cy, listW);
        mirror(ui, m, cx + cw - mirrorW, cy - 2, mirrorW, ch + 6);
        ui.targetsOff(false);
        if (listRow != null) list(ui, m);
        return back;
    }

    /** The section's tabs, its rows of lists, the dye shelf under them, the style and the hand. */
    private void shelves(Ui ui, Medieval m, float x, float y, float w) {
        Wardrobe wardrobe = wd;
        SpriteLibrary lib = game.sprites();
        Wardrobe.Style style = wardrobe.style();
        String body = wardrobe.get(Slot.BODY);

        // --- the tabs: Looks, Clothing, and the Armour's, plated in iron --------------------
        float tw = (w - 16) / 3, th = 34;
        for (int i = 0; i < Section.values().length; i++) {
            Section s = Section.values()[i];
            float tx = x + i * (tw + 8);
            String label = s.label;
            if (s == Section.ARMOUR) {
                int worn = 0;
                for (Row r : ARMOUR) {
                    List<Option> o = rowOpts(r);
                    int at = current(o, r.slot(), wardrobe);
                    if (at > 0) worn++;
                }
                label = "Armour" + (worn > 0 ? " (" + worn + " worn)" : "");
                if (ironTab(ui, m, label, tx, y, tw, th, section == s)) show(s);
            } else if (m.button(label, tx, y, tw, th, section == s, true)) {
                show(s);
            }
        }
        if (ui.input().padFocusing()) {
            ui.text(m.small, "LB / RB", x + w - m.small.width("LB / RB"), y - 13, Theme.withAlpha(Medieval.CREAM, 0.6f));
        }
        float ry = y + th + 12;

        // --- the armour's own header: what it does, and whether the clothes show under it ----
        if (section == Section.ARMOUR) {
            ui.rect(x - 4, ry - 4, w + 8, 34, Theme.withAlpha(STEEL, 0.85f));
            ui.outline(x - 4, ry - 4, w + 8, 34, 1, STEEL_LIGHT);
            ui.text(m.small, fit(m.small, "Armour rolls its dice to block a blow where it lands.", w - 250), x + 4,
                    ry + 5, STEEL_TEXT);
            boolean under = wardrobe.underShown();
            if (m.button(under ? "Clothes under it: shown" : "Clothes under it: hidden", x + w - 236, ry - 1, 236, 28,
                    under, true)) {
                wardrobe.setUnderShown(!under);
                Sounds.play(SoundKeys.ui("wear"));
            }
            ry += 40;
        }

        // --- the rows: three across, each a list to drop down -------------------------------
        List<Row> rows = rowsNow(section);
        int columns = 3;
        float gap = 12, colW = (w - (columns - 1) * gap) / columns, rowH = 48, boxH = 26;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float sx = x + (i % columns) * (colW + gap);
            float sy = ry + (i / columns) * rowH;
            row(ui, m, row, sx, sy, colW, boxH, lib, body, style);
        }
        float shelfY = ry + ((rows.size() + columns - 1) / columns) * rowH + 2;
        m.shelf(x - 8, shelfY, w + 16);

        // --- the dye shelf: the focused layer's colours ----------------------------------
        float dy = shelfY + 22;
        String fItem = wardrobe.get(focus);
        List<Wearables.Colour> fOpts = cols(focus);
        boolean anyInStyle = fItem != null && !lib.colourOptions(body, focus, fItem, style).isEmpty();
        String what = focus == Slot.BODY ? "Skin" : focus.label();
        String picked = fItem == null ? "nothing worn"
                : !fOpts.isEmpty() ? colourName(wardrobe.colour(focus))
                : Wearables.metal(focus, fItem) ? colourName(wardrobe.colour(focus)) + " (the metal's own)"
                : !anyInStyle ? "none in this style"
                : "own colours";
        if (offered != null && fOpts.stream().noneMatch(o -> o.option().equals(offered) && !o.unlocked())) {
            offered = null;
        }
        m.carved(m.heading, "Colour · " + what + ": " + picked, x, dy, fOpts.isEmpty() ? Medieval.BRASS : Medieval.GOLD);
        float gy = dy + 34, r = 12, step = 2 * r + 8;
        // its own options as gems (and "-": none picked), then every colour of the palette under them
        List<Wearables.Colour> named = new ArrayList<>(), palette = new ArrayList<>();
        List<Integer> shown = new ArrayList<>();
        for (Wearables.Colour o : fOpts) {
            if (Variants.isCustom(o.option())) continue;
            named.add(o);
            shown.add(swatch(lib, body, focus, fItem, style, o.option()));
        }
        for (Wearables.Colour o : fOpts) {
            // (a palette colour that is one of the item's own options is set out once, as the gem)
            if (Variants.isCustom(o.option()) && !shown.contains(Wearables.rgb(o.option()))) palette.add(o);
        }
        if (!fOpts.isEmpty()) {
            if (m.knob("-", x, gy - 10, 20, wardrobe.colour(focus) != null)) wardrobe.setColour(focus, null);
            for (int i = 0; i < named.size(); i++) {
                float gx = x + 40 + i * step;
                if (gx + r > x + w) break;
                Wearables.Colour o = named.get(i);
                int rgb = swatch(lib, body, focus, fItem, style, o.option());
                int carried = o.unlocked() ? 0 : dyesOf(o.option());
                m.gem(gx, gy, r, rgb, o.unlocked() || carried > 0);
                boolean on = o.option().equals(wardrobe.colour(focus)) || o.option().equals(offered);
                if (on) ui.outline(gx - r - 3, gy - r - 3, 2 * r + 6, 2 * r + 6, 2, Medieval.GOLD);
                else if (ui.hover(gx - r, gy - r, 2 * r, 2 * r)) ui.outline(gx - r - 2, gy - r - 2, 2 * r + 4, 2 * r + 4, 1, Medieval.CREAM);
                if (!o.unlocked()) {
                    if (carried > 0) {
                        m.bottle(gx + r - 4, gy + r + 2, rgb >= 0 ? rgb : 0xffffff);
                        if (carried > 1) ui.text(m.small, "x" + carried, gx + r - 2, gy - r - 8, Medieval.CREAM);
                    } else {
                        m.padlock(gx + r - 5, gy + r - 6);
                    }
                }
                if (ui.clicked(gx - r, gy - r, 2 * r, 2 * r)) {
                    offered = pickColour(focus, o);
                    if (o.unlocked()) Sounds.play(SoundKeys.ui("wear"));
                }
            }
        }
        float py = gy + r + 10;
        int across = 25;
        float pw = (float) Math.floor(Math.min(28, (w + 2) / across)), ph = 14;
        for (int i = 0; i < palette.size(); i++) {
            Wearables.Colour o = palette.get(i);
            float px = x + (i % across) * pw, cy = py + (i / across) * (ph + 2), cw = pw - 2;
            int rgb = Wearables.rgb(o.option());
            int carried = o.unlocked() ? 0 : dyesOf(o.option());
            ui.rect(px, cy, cw, ph, Medieval.INK);
            ui.rect(px + 1, cy + 1, cw - 2, ph - 2, Theme.hex(rgb & 0xFFFFFF, 1f));
            if (!o.unlocked()) {
                ui.rect(px + 1, cy + 1, cw - 2, ph - 2, Theme.withAlpha(Medieval.INK, carried > 0 ? 0.25f : 0.55f));
                if (carried > 0) m.bottle(px + cw / 2 - 3, cy + ph - 3, rgb);
                else m.padlock(px + cw / 2, cy + ph / 2 + 1);
            }
            boolean on = o.option().equalsIgnoreCase(wardrobe.colour(focus)) || o.option().equals(offered);
            if (on) ui.outline(px - 2, cy - 2, cw + 4, ph + 4, 2, Medieval.GOLD);
            else if (ui.hover(px, cy, cw, ph)) ui.outline(px - 1, cy - 1, cw + 2, ph + 2, 1, Medieval.CREAM);
            if (ui.clicked(px, cy, cw, ph)) {
                offered = pickColour(focus, o);
                if (o.unlocked()) Sounds.play(SoundKeys.ui("wear"));
            }
        }
        int paletteRows = (palette.size() + across - 1) / across;
        float ny = paletteRows == 0 ? gy + r + 8 : py + paletteRows * (ph + 2) + 2;
        if (offered != null && player != null) {
            String dye = colourName(offered);
            String on = focus == Slot.BODY ? "the skin" : current(focus);
            String ask = "Use up a " + dye + " dye to make " + on + " " + dye.toLowerCase(Locale.ROOT) + " for good?";
            ui.text(m.small, fit(m.small, ask, w - 214), x, ny + 6, Medieval.CREAM);
            if (m.button("Use dye", x + w - 204, ny - 2, 100, 30, false, true)) {
                if (Wearables.useDye(wardrobe, player.inventory(), focus, offered)) {
                    Sounds.play(SoundKeys.ui("secret"));
                    game.toast(dye + " unlocked for " + on, com.larsons.game.world.Rarity.RARE.colour(1f));
                    game.saveWardrobe();
                }
                offered = null;
            }
            if (m.button("Keep it", x + w - 100, ny - 2, 100, 30, false, true)) offered = null;
        } else if (!fOpts.isEmpty() && player == null) {
            ui.text(m.small, "Any colour of the palette, or the item's own. The Forge's armour is in the Armour section.",
                    x, ny + 6, Theme.withAlpha(Medieval.CREAM, 0.75f));
        } else if (!fOpts.isEmpty()) {
            ui.text(m.small, "Locked colours take a dye of their colour: one dye unlocks it on this item for good.",
                    x, ny + 6, Theme.withAlpha(Medieval.CREAM, 0.75f));
        }

        // --- the style and the hand, side by side ----------------------------------------
        float stY = ny + 30;
        float bw = Math.min(92, (w - 130) / 6 - 4);
        m.carved(m.heading, "Style", x, stY + 4, Medieval.BRASS);
        Wardrobe.Style[] styles = Wardrobe.Style.values();
        for (int i = 0; i < styles.length; i++) {
            if (m.button(styles[i].label(), x + 58 + i * (bw + 4), stY, bw, 30, style == styles[i], true)) {
                wardrobe.setStyle(styles[i]);
            }
        }
        float hx = x + 58 + styles.length * (bw + 4) + 14;
        m.carved(m.heading, "Hand", hx, stY + 4, Medieval.BRASS);
        hx += m.heading.width("Hand") + 8 - 54;
        Wardrobe.Hand[] hands = Wardrobe.Hand.values();
        for (int i = 0; i < hands.length; i++) {
            String l = hands[i] == Wardrobe.Hand.AUTO ? "Auto (" + (wardrobe.leftHanded() ? "L" : "R") + ")"
                    : hands[i].label();
            if (m.button(l, hx + 54 + i * (bw + 4), stY, bw, 30, wardrobe.hand() == hands[i], true)) {
                wardrobe.setHand(hands[i]);
            }
        }
    }

    private void show(Section s) {
        if (s == section) return;
        section = s;
        listRow = null;
        Sounds.play(SoundKeys.ui("tab"));
    }

    /** The Armour tab: an iron plate with rivets, its lettering steel-white; whether it was clicked. */
    private boolean ironTab(Ui ui, Medieval m, String label, float x, float y, float w, float h, boolean on) {
        boolean hot = ui.hover(x, y, w, h);
        m.shadow(x, y, w, h, 3);
        ui.rect(x, y, w, h, on ? Theme.rgb(44, 48, 56, 1f) : hot ? Theme.rgb(84, 90, 100, 1f) : STEEL);
        ui.outline(x, y, w, h, 2, on ? Medieval.GOLD : STEEL_LIGHT);
        for (float rx : new float[]{x + 7, x + w - 7}) {
            m.rivet(rx, y + 7, 2.5f);
            m.rivet(rx, y + h - 7, 2.5f);
        }
        // a small shield, so the section is known at a glance
        float sx = x + 18, sy = y + h / 2;
        ui.rect(sx - 6, sy - 8, 12, 10, STEEL_TEXT);
        ui.batch().quad(ui.white(), sx - 6, sy + 2, 0, sx + 6, sy + 2, 0, sx, sy + 9, 0, sx, sy + 9, 0, 0, 0, 1, 1,
                STEEL_TEXT[0], STEEL_TEXT[1], STEEL_TEXT[2], 1);
        ui.rect(sx - 1, sy - 6, 2, 12, Theme.rgb(150, 40, 30, 1f));
        Font f = m.body.width(label) <= w - 40 ? m.body : m.small;
        ui.text(f, label, x + 30 + (w - 34 - f.width(label)) / 2, y + (h - f.lineHeight()) / 2, on ? Medieval.GOLD
                : STEEL_TEXT);
        return ui.clicked(x, y, w, h);
    }

    /** One row: its name, its list (dropped down by a click; stepped with the gamepad's left and right), its gem. */
    private void row(Ui ui, Medieval m, Row row, float sx, float sy, float colW, float boxH,
                     SpriteLibrary lib, String body, Wardrobe.Style style) {
        Wardrobe wardrobe = wd;
        Slot slot = row.slot();
        List<Option> opts = rowOpts(row);
        String other = other(row);
        String current = wardrobe.get(slot);
        int at = other != null ? 0 : current(opts, slot, wardrobe);
        boolean missing = current != null && at < 0;
        String folder = current == null ? null : lib.styled(body, slot, current, style);
        boolean hasArt = current == null || other != null || lib.entry(body, slot, folder) != null;
        boolean iron = row.kind() == Kind.ARMOUR || section == Section.ARMOUR;

        // the name, and an armour piece's dice
        String name = row.label();
        if (iron && at > 0 && opts.get(at).wearable() != null) {
            int sides = Armour.sides(opts.get(at).wearable());
            if (sides > 0) name += " · d" + sides;
        }
        if (ui.clicked(sx, sy, colW, 14)) focusOn(slot);
        m.carved(m.small, name.toUpperCase(Locale.ROOT), sx + 2, sy, focus == slot ? Medieval.GOLD
                : iron ? STEEL_TEXT : Medieval.BRASS);
        List<Wearables.Colour> colours = other != null ? List.of() : cols(slot);
        float gemW = colours.isEmpty() ? 0 : 26;
        float bx = sx, by = sy + 16, bw = colW - gemW;
        boolean open = row.equals(listRow);
        boolean hot = open || ui.hover(bx, by, bw, boxH);
        if (iron) {
            ui.rect(bx, by, bw, boxH, hot ? Theme.rgb(84, 90, 100, 1f) : STEEL);
            ui.outline(bx, by, bw, boxH, 1, open ? Medieval.GOLD : STEEL_LIGHT);
        } else {
            m.parchment(bx, by, bw, boxH);
            if (hot) ui.outline(bx, by, bw, boxH, 1, open ? Medieval.GOLD : Medieval.BRASS);
        }
        String shown = other != null ? "None  (" + other + ")" : at < 0 ? label(slot, current) : opts.get(at).label(slot);
        String text = missing ? current + " (missing)" : !hasArt ? shown + " (no art)" : shown;
        float[] c = missing || !hasArt ? Medieval.EMBER
                : iron ? (at <= 0 ? Theme.withAlpha(STEEL_TEXT, 0.55f) : STEEL_TEXT)
                : at <= 0 && current == null ? Medieval.INK_FADED : Medieval.INK;
        if (at > 0 && opts.get(at).wearable() != null) {
            // a dot of its rarity's colour
            ui.disc(bx + 9, by + boxH / 2, 3.5f, opts.get(at).wearable().wearable().rarity().colour(1f));
        }
        ui.text(m.body, fit(m.body, text, bw - 34), bx + 17, by + (boxH - m.body.lineHeight()) / 2, c);
        // the mark of a list that drops down
        float mx = bx + bw - 12, my = by + boxH / 2;
        float[] mc = iron ? STEEL_TEXT : Medieval.INK;
        ui.rect(mx - 5, my - 2, 10, 2, mc);
        ui.rect(mx - 3, my, 6, 2, mc);
        ui.rect(mx - 1, my + 2, 2, 2, mc);
        // the gamepad's left and right step through it without opening it
        ui.target(bx, by, bw, boxH, Ui.Kind.CHOICE);
        Input in = ui.input();
        if (in.padFocusing() && in.navAdjust() != 0 && ui.hover(bx, by, bw, boxH) && listRow == null && opts.size() > 1) {
            int i = Math.floorMod(Math.max(0, at) + in.navAdjust(), opts.size());
            in.adjust(0);
            chooseIn(wardrobe, row, opts.get(i), other);
            Sounds.play(SoundKeys.ui("wear"));
            focusOn(slot);
        }
        if (ui.clicked(bx, by, bw, boxH)) {
            focusOn(slot);
            openList(ui, row, opts, Math.max(0, at), bx, by + boxH + 2, Math.max(bw, 240));
        }
        if (!colours.isEmpty()) {
            // the colour picked for it (its own colours: an empty bezel)
            float gx = sx + colW - 11, gy = by + boxH / 2;
            String picked = wardrobe.colour(slot);
            int sw = swatch(lib, body, slot, current, style, picked);
            m.gem(gx, gy, 10, sw, true);
            if (focus == slot) ui.outline(gx - 12, gy - 12, 24, 24, 1, Medieval.GOLD);
            if (ui.clicked(gx - 10, gy - 10, 20, 20)) focusOn(slot);
        }
    }

    private void openList(Ui ui, Row row, List<Option> opts, int at, float x, float y, float w) {
        listRow = row;
        listOptions = List.copyOf(opts);
        // (it hangs under the row - or over it, if there is no room below)
        int shown = Math.min(LIST_ROWS, opts.size());
        float h = shown * LIST_ROW_H + 8;
        listX = Math.min(x, ui.width() - w - 8);
        listY = y + h > ui.height() - 8 ? Math.max(8, y - h - 34) : y;
        listW = w;
        listScroll = Math.max(0, Math.min(opts.size() - shown, at - shown / 2));
        if (ui.input().usingPad()) {
            // the focus onto the choice worn now
            ui.input().warp(listX + listW / 2, listY + 4 + (at - listScroll) * LIST_ROW_H + LIST_ROW_H / 2);
        }
    }

    /** A click while the list is down: a choice in it is worn; anywhere else shuts it. */
    private void listClick(Ui ui) {
        Input in = ui.input();
        int shown = Math.min(LIST_ROWS, listOptions.size());
        float h = shown * LIST_ROW_H + 8;
        if (in.scroll() != 0 && ui.hover(listX, listY, listW, h)) {
            listScroll = Math.max(0, Math.min(listOptions.size() - shown, listScroll - (int) Math.signum(in.scroll())));
            in.consumeScroll();
        }
        if (!in.mousePressed(GLFW_MOUSE_BUTTON_LEFT)) return;
        in.consumeMouse(GLFW_MOUSE_BUTTON_LEFT);
        if (ui.hover(listX, listY + 4, listW, shown * LIST_ROW_H)) {
            int i = listScroll + (int) ((in.mouseY() - listY - 4) / LIST_ROW_H);
            if (i >= 0 && i < listOptions.size()) {
                chooseIn(wd, listRow, listOptions.get(i), other(listRow));
                Sounds.play(SoundKeys.ui("wear"));
            }
        }
        listRow = null;
    }

    /** The list dropped down, over everything. */
    private void list(Ui ui, Medieval m) {
        int n = listOptions.size(), shown = Math.min(LIST_ROWS, n);
        float h = shown * LIST_ROW_H + 8;
        boolean iron = listRow.kind() == Kind.ARMOUR || ARMOUR.contains(listRow) && section == Section.ARMOUR;
        m.shadow(listX, listY, listW, h, 6);
        if (iron) {
            ui.rect(listX, listY, listW, h, Theme.rgb(40, 44, 50, 1f));
            ui.outline(listX, listY, listW, h, 2, STEEL_LIGHT);
        } else {
            m.parchment(listX, listY, listW, h);
            ui.outline(listX, listY, listW, h, 2, Medieval.BRASS);
        }
        String other = other(listRow);
        int at = other != null ? 0 : current(listOptions, listRow.slot(), wd);
        for (int k = 0; k < shown; k++) {
            int i = listScroll + k;
            if (i >= n) break;
            Option o = listOptions.get(i);
            float ry = listY + 4 + k * LIST_ROW_H;
            boolean hot = ui.hover(listX, ry, listW, LIST_ROW_H);
            if (hot) ui.rect(listX + 3, ry, listW - 6, LIST_ROW_H, iron ? Theme.rgb(84, 90, 100, 1f)
                    : Theme.withAlpha(Medieval.BRASS, 0.35f));
            if (i == at) ui.outline(listX + 3, ry, listW - 6, LIST_ROW_H, 1, Medieval.GOLD);
            if (o.wearable() != null) ui.disc(listX + 13, ry + LIST_ROW_H / 2, 4, o.wearable().wearable().rarity().colour(1f));
            String label = o.item() == null ? "None" : o.label(listRow.slot());
            if (iron && o.wearable() != null && Armour.sides(o.wearable()) > 0) label += "   d" + Armour.sides(o.wearable());
            ui.text(m.body, fit(m.body, label, listW - 36), listX + 24, ry + (LIST_ROW_H - m.body.lineHeight()) / 2,
                    iron ? STEEL_TEXT : o.item() == null ? Medieval.INK_FADED : Medieval.INK);
            ui.target(listX + 3, ry, listW - 6, LIST_ROW_H, Ui.Kind.BUTTON);
        }
        if (n > shown) {
            // how far down the list is scrolled
            float bar = h * shown / n, top = listY + (h - bar) * listScroll / Math.max(1, n - shown);
            ui.rect(listX + listW - 5, top, 3, bar, iron ? STEEL_LIGHT : Medieval.BRASS);
        }
    }

    /** What the focused layer wears, by name: "the Baggy shirt". */
    private String current(Slot slot) {
        List<Option> opts = opts(slot);
        int at = current(opts, slot, wd);
        return "the " + (at < 0 ? label(slot, wd.get(slot)) : opts.get(at).label(slot));
    }

    private void focusOn(Slot slot) {
        if (focus != slot) offered = null;
        focus = slot;
    }

    /** Her reflection in the mirror - or, brought close, her face and shoulders - and the controls that turn it. */
    private void mirror(Ui ui, Medieval m, float x, float y, float w, float h) {
        Wardrobe wardrobe = wd;
        SpriteLibrary lib = game.sprites();
        float controls = 80;
        float[] glass = m.mirror(x, y, w, h - controls);
        float gx = glass[0], gy = glass[1], gw = glass[2], gh = glass[3];
        String caption;
        if (closeup) {
            ui.clip(gx, gy, gw, gh);
            int frame = closeUp(ui, gx, gy, gw, gh, wardrobe);
            ui.unclip();
            caption = facing.key().toUpperCase() + " · close up, at rest" + (frame >= 0 ? " · frame " + (frame + 1) : "");
        } else {
            SpriteView view = new SpriteView(elevation, facing, elevation.renderAngle());
            double duration = LayerStack.duration(lib, wardrobe, state, view);
            double t = state.loops() ? time : time % (duration + 0.4);
            LayerStack.Result r = com.larsons.game.world.WorldRenderer.inLooks(
                    LayerStack.resolve(lib, wardrobe, state, t, view, previewMemory), wardrobe,
                    player == null ? null : player.inventory(),
                    game.items(), lib);
            float ppm = (float) Math.min((gh - 40) / r.framing().frameWorldHeight(),
                    (gw - 10) / r.framing().frameWorldSize());
            double[] anchor = r.framing().anchor(elevation);
            float frameH = (float) (r.framing().frameWorldHeight() * ppm);
            float footY = gy + 10 + (gh - 30 - frameH) / 2 + (float) anchor[1] * frameH;
            // her shadow on the floor the mirror shows
            ui.batch().rect(ui.white(), gx + gw / 2 - 40, footY - 4, 80, 8, 0, 0, 1, 1, 0, 0, 0, 0.25f);
            LayerStack.drawFlat(ui.batch(), r, gx + gw / 2, footY, ppm);
            caption = facing.key().toUpperCase() + " · " + elevation.label() + " · frame " + (r.frame() + 1)
                    + "/" + r.frames();
        }
        ui.text(m.small, caption, gx + (gw - m.small.width(caption)) / 2, gy + gh - 22, Theme.withAlpha(Medieval.CREAM, 0.7f));

        // the controls, on the mirror's stand
        float by = y + h - controls + 10;
        if (m.button("<", x, by, 36, 30, false, true)) {
            facing = facing.clockwise();
            spin = false;
        }
        if (m.button(spin ? "Stop" : "Spin", x + 40, by, 66, 30, false, true)) spin = !spin;
        if (m.button(">", x + 110, by, 36, 30, false, true)) {
            facing = facing.counterClockwise();
            spin = false;
        }
        float ew = (w - 154) / Elevation.values().length;
        for (int i = 0; i < Elevation.values().length; i++) {
            Elevation e = Elevation.values()[i];
            if (m.button(e.key(), x + 154 + i * ew, by, ew - 4, 30, e == elevation && !closeup, !closeup)) elevation = e;
        }
        by += 36;
        // close up, or the state: one of many, stepped through
        float cw = 92;
        if (m.button(closeup ? "Whole" : "Close up", x, by, cw, 30, closeup, true)) {
            closeup = !closeup;
            time = 0;
        }
        float sx = x + cw + 6, sw = w - cw - 6;
        AnimState[] states = AnimState.values();
        int at = state.ordinal(), step = 0;
        if (m.button("<", sx, by, 30, 30, false, !closeup)) step = -1;
        if (m.button(">", sx + sw - 30, by, 30, 30, false, !closeup)) step = 1;
        if (step != 0) {
            state = states[Math.floorMod(at + step, states.length)];
            time = 0;
        }
        m.parchment(sx + 34, by, sw - 68, 30);
        String s = closeup ? "At rest" : state.label() + "  (" + (state.ordinal() + 1) + " / " + states.length + ")";
        ui.text(m.body, fit(m.body, s, sw - 76), sx + sw / 2 - Math.min(m.body.width(s), sw - 76) / 2,
                by + (30 - m.body.lineHeight()) / 2, Medieval.INK);
    }

    /**
     * Her close up, waist up, at rest, from the side the mirror is turned to
     * ({@code assets/wardrobe_closeups/}: every item, layer on layer, playing
     * on); where those are not drawn yet, her sprite brought close instead.
     * Returns the frame shown, or -1 for the sprite.
     */
    private int closeUp(Ui ui, float x, float y, float w, float h, Wardrobe wd) {
        SpriteLibrary lib = game.sprites();
        CloseupLibrary closeups = game.wardrobeCloseups();
        String root = lib.rootOf(wd.get(Slot.BODY));
        String clip = "rest_side_" + facing.key();
        record Drawn(SheetTexture sheet, int[] palette) {}
        List<Drawn> layers = new ArrayList<>();
        boolean bodyDrawn = false;
        for (Slot slot : LayerStack.drawOrder(wd.leftHanded(), Elevation.SIDE, facing)) {
            if (slot.carried() || wd.hidden(slot)) continue;
            CloseupLibrary.Entry e = closeups.entryIn(root, slot, wd.get(slot), wd.style());
            SpriteLibrary.Resolved r = closeups.resolve(e, clip, null, false);
            if (r == null) continue;
            SheetTexture t = lib.sheet(r);
            if (t == null) continue;
            if (slot == Slot.BODY) bodyDrawn = true;
            int[] base = t.sharedPalette();
            int[] palette = base == null || e.variants() == null ? base : Variants.recolored(base,
                    e.variants().recolor(CloseupLibrary.choice(slot, wd.colour(slot), wd.skin())));
            layers.add(new Drawn(t, palette));
        }
        if (!bodyDrawn) {
            // no close-ups of this body yet: the sprite, brought close on the head and shoulders
            SpriteView view = new SpriteView(Elevation.SIDE, facing, Elevation.SIDE.renderAngle());
            LayerStack.Result r = LayerStack.resolve(lib, wd, AnimState.REST, time, view, previewMemory);
            // (about the top 0.85 m of her - head to waist - fills the glass)
            float ppm = h / 0.85f;
            float footY = y + h * 0.06f + 1.78f * ppm;
            LayerStack.drawFlat(ui.batch(), r, x + w / 2, footY, ppm);
            return -1;
        }
        int frames = Integer.MAX_VALUE;
        for (Drawn d : layers) frames = Math.min(frames, Math.max(1, d.sheet().frameCount()));
        // (drawn every other frame of the rest: played so a loop lasts as long as the rest's)
        double fps = frames * AnimState.DEFAULT_FPS / (double) Math.max(1, AnimState.REST.defaultFrames());
        int frame = (int) (time * fps) % frames;
        float size = Math.min(w, h * 1.05f);
        float fx = x + w / 2 - size / 2, fy = y + h / 2 - size * 0.5f;
        for (Drawn d : layers) {
            double[] region = d.sheet().region(false);
            if (region[2] <= region[0] || region[3] <= region[1]) continue;
            float[] uv = d.sheet().uv(frame, false);
            ui.batch().rect(d.sheet().texture(), fx + (float) region[0] * size, fy + (float) region[1] * size,
                    (float) (region[2] - region[0]) * size, (float) (region[3] - region[1]) * size,
                    uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1,
                    ui.batch().palette(d.sheet().indexed() ? d.palette() : null));
        }
        return frame;
    }

    private static String fit(Font font, String s, float width) {
        if (font.width(s) <= width) return s;
        while (s.length() > 1 && font.width(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
