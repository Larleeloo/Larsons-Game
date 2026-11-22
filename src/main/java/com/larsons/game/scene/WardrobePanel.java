package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Game;
import com.larsons.game.gfx.Font;
import com.larsons.game.input.Input;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Elevation;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.LayerStack;
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
 * own kind. The armour section has the switch that puts the armour out of
 * sight - still worn and rolling its dice, but not drawn, in the game or the
 * mirror ({@link Wardrobe#armourHidden}).
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
    record Row(Slot slot, String label, Kind kind) {

        /**
         * The layer of its slot it dresses: the armour (true), the clothes -
         * worn under the armour, if there is armour there (false) - or
         * whatever is worn there (null).
         */
        Boolean layer() {
            return kind == Kind.ARMOUR ? Boolean.TRUE : kind == Kind.CLOTHES ? Boolean.FALSE : null;
        }
    }

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
    /** The armour's rows, by the part of the body each guards ({@link Armour#location}): head, torso, arms, legs, feet. */
    static final List<Row> ARMOUR = List.of(new Row(Slot.HAT, "Helmet", Kind.ARMOUR),
            new Row(Slot.NECKLACE, "Gorget", Kind.ARMOUR), new Row(Slot.SHIRT, "Gambeson · jerkin", Kind.ARMOUR),
            new Row(Slot.CHAINMAIL, "Mail shirt", Kind.ARMOUR), new Row(Slot.CHESTPLATE, "Chestplate", Kind.ARMOUR),
            new Row(Slot.GLOVES, "Gauntlets", Kind.ARMOUR), new Row(Slot.WRISTWEAR, "Right bracer", Kind.ARMOUR),
            new Row(Slot.WRISTWEAR_LEFT, "Left bracer", Kind.ARMOUR),
            new Row(Slot.CHAINMAIL_LEGS, "Mail leggings", Kind.ARMOUR),
            new Row(Slot.LEG_PLATING, "Leg plating", Kind.ARMOUR), new Row(Slot.SHOES, "Sabatons", Kind.ARMOUR));

    /** The armour rows guarding {@code at}, in order. */
    static List<Row> armourOf(Armour.Location at) {
        List<Row> out = new ArrayList<>();
        for (Row r : ARMOUR) if (Armour.location(r.slot()) == at) out.add(r);
        return out;
    }

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
    private CloseUp closeUps;
    private Facing facing = Facing.SOUTH_EAST;
    private Elevation elevation = Elevation.MIDDLE;
    private AnimState state = AnimState.REST;
    private boolean spin = true, closeup;
    /** Whether the list of what everything worn gives is open (the cornice's Gifts button). */
    private boolean giftsOpen;
    private double time, spinTimer;
    private Section section = Section.CLOTHING;
    /** The layer whose colours the dye shelf shows: its slot, and which of the slot's ({@link Row#layer}). */
    private Slot focus = Slot.HAIR;
    private Boolean focusLayer;
    /** A locked colour of the focused layer she has clicked and has a dye of: offered to be used up, or null. */
    private String offered;
    // the list dropped down: its row, its choices, where it hangs, how far it is scrolled
    private Row listRow;
    private List<Option> listOptions = List.of();
    private float listX, listY, listW;
    private int listScroll, listShown;
    /** Whether the list's scroll bar is being dragged. */
    private boolean listDragging;
    /** The most rows a list shows at once (fewer where the window has no room). */
    private static final int LIST_ROWS = 14;
    /** The width of a list's scroll bar, along its right edge. */
    private static final float LIST_BAR = 12;
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

        /** The colour it is always worn in (a metal's, a tint's - a forged piece's as its own), or null. */
        String colour() {
            return wearable == null ? null : wearable.wearable().colour();
        }

        /**
         * What it is worn as ({@link Wardrobe#id}): a forged piece's {@code
         * forged:<id>}, a wearable's id - so a dye can colour it and it stays
         * itself - or null for a folder no wearable is.
         */
        String id() {
            if (forged != null) return Armour.forgedLook(forged);
            return wearable == null ? null : wearable.id();
        }

        /**
         * The colour it is put on in: its own ({@link #colour}), or the one a
         * tailored garment is cut in (the Moonsilk gown is the laced dress in
         * platinum); null for anything worn in whatever colour was picked.
         */
        String cut() {
            String c = colour();
            return c != null ? c : Wearables.tailoredColour(wearable);
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
        // what she wears and no longer owns stays on until it is taken off (the armour, and the clothes under it)
        addWorn(out, slot, wardrobe);
        return out;
    }

    /** To {@code out}, what {@code wardrobe} wears in {@code slot} - its armour, its clothes - that none of it is. */
    static void addWorn(List<Option> out, Slot slot, Wardrobe wardrobe) {
        for (boolean armour : new boolean[]{true, false}) {
            String worn = wardrobe.get(slot, armour);
            if (worn == null || current(out, slot, wardrobe, armour) >= 0) continue;
            String id = wardrobe.id(slot, armour), colour = wardrobe.colour(slot, armour);
            String forgedId = Armour.forgedId(id != null ? id : colour);
            Forged f = forgedId == null ? null : Forged.byId(forgedId);
            ItemDef own = id != null && forgedId == null ? Wearables.byId(id) : null;
            out.add(f != null ? new Option(worn, f.def(), f)
                    : own != null && own.wearable().puts(slot, worn) ? new Option(worn, own)
                    : new Option(worn, Wearables.wornAs(slot, worn, forgedId != null ? null : colour)));
        }
    }

    // --- a slot's layers: the armour, and the clothes under it ------------------------------

    /** What is worn in {@code slot}'s {@code layer} ({@link Row#layer}; null: whatever is on top). */
    static String worn(Wardrobe w, Slot slot, Boolean layer) {
        return layer == null ? w.get(slot) : w.get(slot, layer);
    }

    /** Its colour. */
    static String wornColour(Wardrobe w, Slot slot, Boolean layer) {
        return layer == null ? w.colour(slot) : w.colour(slot, layer);
    }

    /** What it is worn as ({@link Wardrobe#id}). */
    static String wornId(Wardrobe w, Slot slot, Boolean layer) {
        return layer == null ? w.id(slot) : w.id(slot, layer);
    }

    /** Put {@code colour} on what is worn in {@code slot}'s {@code layer}. */
    static void setWornColour(Wardrobe w, Slot slot, Boolean layer, String colour) {
        if (layer == null) w.setColour(slot, colour);
        else w.setColour(slot, layer, colour);
    }

    /** The layer what is on top in {@code slot} is: the armour, or the clothes. */
    private static boolean top(Wardrobe w, Slot slot) {
        return Wardrobe.isArmour(w.get(slot));
    }

    /**
     * Which of {@code options} is worn in {@code slot}, or -1: the one of its
     * folder put on in its very colour (a bronze helmet, the silver wrist
     * band, a tailored garment in its cut); else the plain one of its folder,
     * whatever colour it was dyed; else a tailored garment of its folder dyed
     * another colour.
     */
    static int current(List<Option> options, Slot slot, Wardrobe wardrobe) {
        return current(options, slot, wardrobe, null);
    }

    /**
     * {@link #current(List, Slot, Wardrobe)} of {@code slot}'s {@code layer}
     * (the armour, the clothes under it; null: what is on top): what it is
     * worn as first ({@link Wardrobe#id}: a dyed bronze helmet is the bronze
     * helmet still), else by its folder and colour.
     */
    static int current(List<Option> options, Slot slot, Wardrobe wardrobe, Boolean layer) {
        String item = worn(wardrobe, slot, layer), colour = wornColour(wardrobe, slot, layer);
        String id = item == null ? null : wornId(wardrobe, slot, layer);
        if (id != null) {
            for (int i = 0; i < options.size(); i++) {
                Option o = options.get(i);
                if (java.util.Objects.equals(o.item(), item) && id.equals(o.id())) return i;
            }
            // (a forged piece is that piece or none of these)
            if (Armour.forgedId(id) != null) return -1;
        }
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            if (java.util.Objects.equals(o.item(), item) && o.cut() != null && o.cut().equalsIgnoreCase(colour)) return i;
        }
        boolean look = Armour.look(colour);
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            if (java.util.Objects.equals(o.item(), item) && o.cut() == null && !look) return i;
        }
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            if (item != null && item.equals(o.item()) && o.colour() == null && !look) return i;
        }
        return -1;
    }

    /**
     * Wear {@code option} in {@code slot}: its folder, and the colour it is
     * put on in ({@link Option#cut}: a metal's, a tint, a tailored garment's
     * cut). Taking off one put on in its own colour takes the colour off too,
     * so what is worn next is not mistaken for it (a plain laced dress after
     * the platinum Moonsilk gown).
     */
    static void choose(Wardrobe wardrobe, Slot slot, Option option) {
        choose(wardrobe, slot, null, option);
    }

    /**
     * {@link #choose(Wardrobe, Slot, Option)} in {@code slot}'s {@code layer}:
     * armour goes over the clothes there and the clothes under the armour,
     * the other left on ({@link Wardrobe#wear}); null: as armour if it is
     * armour, else as clothes. What it is worn as goes with it ({@link
     * Wardrobe#id}); a forged piece is put on in its own look.
     */
    static void choose(Wardrobe wardrobe, Slot slot, Boolean layer, Option option) {
        boolean armour = layer != null ? layer
                : option.item() != null ? Wardrobe.isArmour(option.item()) : top(wardrobe, slot);
        String was = wardrobe.get(slot, armour), wasColour = wardrobe.colour(slot, armour);
        String wasId = wardrobe.id(slot, armour);
        // (the very piece worn already: it stays as it is, dyed or not)
        if (option.item() != null && option.item().equals(was) && option.id() != null && option.id().equals(wasId)) {
            return;
        }
        wardrobe.wear(slot, armour, option.item());
        if (option.item() != null) wardrobe.setId(slot, armour, option.id());
        boolean wasOwn = wasId != null && (Armour.forgedId(wasId) != null || ownColour(wasId) != null);
        if (option.cut() != null) {
            wardrobe.setColour(slot, armour, option.cut());
        } else if (option.forged() != null || was != null && (Wearables.metal(slot, was) || Armour.look(wasColour)
                || Wearables.tailoredCut(slot, was, wasColour) || wasOwn)) {
            wardrobe.setColour(slot, armour, null);
        }
    }

    /** The colour the wearable {@code id} is always worn in, or null. */
    private static String ownColour(String id) {
        ItemDef d = Wearables.byId(id);
        return d == null ? null : d.wearable().colour() != null ? d.wearable().colour() : Wearables.tailoredColour(d);
    }

    /**
     * The colours of what is worn in {@code slot}: its {@code variants.json}'s,
     * each unlocked or not ({@link Wearables#colours}).
     */
    static List<Wearables.Colour> colours(SpriteLibrary lib, Slot slot, Player player) {
        return colours(lib, slot, player, null);
    }

    /**
     * The colours of what is worn in {@code slot}'s {@code layer}: a forged
     * piece is dyed like any other, even one of a metal.
     */
    static List<Wearables.Colour> colours(SpriteLibrary lib, Slot slot, Player player, Boolean layer) {
        Wardrobe wardrobe = player.wardrobe();
        String item = worn(wardrobe, slot, layer);
        if (item == null) return List.of();
        List<String> all = lib.colourOptions(wardrobe.get(Slot.BODY), slot, item, wardrobe.style());
        boolean forged = Armour.forgedId(wornId(wardrobe, slot, layer)) != null;
        return Wearables.colours(slot, item, all, wornColour(wardrobe, slot, layer),
                Wearables.owned(player.inventory()), wardrobe, forged);
    }

    /**
     * Click colour {@code c} of what is worn in {@code slot}: put it on if it
     * is unlocked; otherwise, with a dye of it in her bag, offer it (the
     * caller asks before the dye is used up); otherwise nothing. Returns the
     * colour now offered, or null.
     */
    static String pick(Wardrobe wardrobe, Player player, Slot slot, Wearables.Colour c) {
        return pick(wardrobe, player, slot, null, c);
    }

    /** {@link #pick(Wardrobe, Player, Slot, Wearables.Colour)} on {@code slot}'s {@code layer}. */
    static String pick(Wardrobe wardrobe, Player player, Slot slot, Boolean layer, Wearables.Colour c) {
        if (c.unlocked()) {
            setWornColour(wardrobe, slot, layer, c.option());
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


    /** One cell of the colour grid: the colour it puts on (an item's own option, or {@code #rrggbb}) and how it shows. */
    record Cell(Wearables.Colour colour, int rgb) {}

    /**
     * The colours of a layer ({@code cols}: its own options, then the
     * palette's) set out as one grid in the palette's order: each of the
     * item's own options takes the place of the palette colour it shows as
     * ({@code swatch}: RGB, or -1), and one that shows as none of them sits
     * beside the nearest. A cell is unlocked if either of the two is; a locked
     * own option whose palette colour is unlocked puts the palette colour on.
     */
    static List<Cell> grid(List<Wearables.Colour> cols, java.util.function.ToIntFunction<String> swatch) {
        List<Wearables.Colour> own = new ArrayList<>();
        List<Integer> ownRgb = new ArrayList<>();
        List<Cell> cells = new ArrayList<>();
        for (Wearables.Colour c : cols) {
            if (Variants.isCustom(c.option())) {
                cells.add(new Cell(c, Wearables.rgb(c.option())));
            } else {
                own.add(c);
                ownRgb.add(swatch.applyAsInt(c.option()));
            }
        }
        boolean[] placed = new boolean[own.size()];
        for (int i = 0; i < cells.size(); i++) {
            Cell p = cells.get(i);
            for (int k = 0; k < own.size(); k++) {
                int rgb = ownRgb.get(k);
                if (placed[k] || rgb < 0 || (rgb & 0xFFFFFF) != p.rgb()) continue;
                placed[k] = true;
                Wearables.Colour o = own.get(k);
                // (the own option - its shading drawn by hand - unless only the palette's is unlocked)
                cells.set(i, new Cell(!o.unlocked() && p.colour().unlocked() ? p.colour() : o, p.rgb()));
                break;
            }
        }
        // the item's own colours the palette has none of: each beside the nearest (or at the end)
        for (int k = 0; k < own.size(); k++) {
            if (placed[k]) continue;
            int rgb = ownRgb.get(k);
            int at = cells.size();
            if (rgb >= 0) {
                double best = Double.MAX_VALUE;
                double[] l = Variants.Oklab.of(rgb & 0xFFFFFF);
                for (int i = 0; i < cells.size(); i++) {
                    double[] m = Variants.Oklab.of(cells.get(i).rgb() & 0xFFFFFF);
                    double d = Math.pow(l[0] - m[0], 2) + Math.pow(l[1] - m[1], 2) + Math.pow(l[2] - m[2], 2);
                    if (d < best) {
                        best = d;
                        at = i + 1;
                    }
                }
            }
            cells.add(at, new Cell(own.get(k), rgb));
        }
        return cells;
    }

    /** Whether {@code cell} is the colour worn: its option, or the palette colour it shows as. */
    static boolean worn(Cell cell, String colour) {
        if (colour == null) return false;
        if (cell.colour().option().equalsIgnoreCase(colour)) return true;
        return Variants.isCustom(colour) && cell.rgb() >= 0 && Wearables.rgb(colour) == (cell.rgb() & 0xFFFFFF);
    }

    // --- a row's choices ---------------------------------------------------------------

    /** Whether {@code o} is armour: a piece that rolls dice to block (a forged one too), worn over the clothes. */
    static boolean armour(Option o) {
        if (o.item() != null && Wardrobe.isArmour(o.item())) return true;
        return o.wearable() != null && Wearables.armour(o.wearable());
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

    /** Wear {@code o} by {@code row}: in its layer, the other layer left on (a necklace stays under a gorget). */
    static void chooseIn(Wardrobe wardrobe, Row row, Option o) {
        choose(wardrobe, row.slot(), row.layer(), o);
    }

    /**
     * What covers {@code row}'s clothes: the armour worn over them in its
     * slot ("Iron helmet"), by name - the clothes still worn, and counting -
     * or null.
     */
    static String over(List<Option> all, Row row, Wardrobe wardrobe) {
        if (row.layer() != Boolean.FALSE || wardrobe.get(row.slot(), true) == null) return null;
        int at = current(all, row.slot(), wardrobe, true);
        return at > 0 ? all.get(at).label(row.slot()) : label(row.slot(), wardrobe.get(row.slot(), true));
    }

    // --- whom it dresses now ------------------------------------------------------------

    /** The choices for {@code slot}: what she owns, or (the editor) everything there is. */
    private List<Option> opts(Slot slot) {
        return player != null ? options(game.sprites(), slot, player) : everything.options(slot, wd, null);
    }

    private List<Option> rowOpts(Row row) {
        return rowOptions(opts(row.slot()), row);
    }

    /** The armour over {@code row}'s clothes, by name, or null. */
    private String over(Row row) {
        return over(opts(row.slot()), row, wd);
    }

    /** The colours of what is worn in {@code slot}: hers, each unlocked or not; or (the editor) every one. */
    private List<Wearables.Colour> cols(Slot slot, Boolean layer) {
        return player != null ? colours(game.sprites(), slot, player, layer) : everything.colours(slot, wd, null, layer);
    }

    private int dyesOf(String colour) {
        return player == null ? 0 : dyes(player, colour);
    }

    private String pickColour(Slot slot, Boolean layer, Wearables.Colour c) {
        keepId(slot, layer);
        if (player != null) return pick(wd, player, slot, layer, c);
        setWornColour(wd, slot, layer, c.option());
        return null;
    }

    /**
     * Before what is worn in {@code slot}'s {@code layer} is dyed: say what it
     * is worn as, if nothing says it yet (an outfit saved before), so a dyed
     * bronze helmet is not taken for an iron one.
     */
    private void keepId(Slot slot, Boolean layer) {
        if (worn(wd, slot, layer) == null || wornId(wd, slot, layer) != null) return;
        List<Option> o = opts(slot);
        int at = current(o, slot, wd, layer);
        if (at <= 0 || o.get(at).id() == null) return;
        boolean armour = layer != null ? layer : top(wd, slot);
        wd.setId(slot, armour, o.get(at).id());
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
        gifts(ui, m, x + w - 128 - 168, y + 22, x + w - 28);
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
                    int at = current(o, r.slot(), wardrobe, r.layer());
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

        // --- the armour's own header: what it does, and whether it is drawn at all ----------
        if (section == Section.ARMOUR) {
            ui.rect(x - 4, ry - 4, w + 8, 34, Theme.withAlpha(STEEL, 0.85f));
            ui.outline(x - 4, ry - 4, w + 8, 34, 1, STEEL_LIGHT);
            boolean hidden = wardrobe.armourHidden();
            ui.text(m.small, fit(m.small, hidden ? "Out of sight, the armour still rolls its dice to block."
                    : "Armour rolls its dice to block a blow where it lands.", w - 250), x + 4, ry + 5, STEEL_TEXT);
            if (m.button(hidden ? "Armour: hidden" : "Armour: shown", x + w - 236, ry - 1, 236, 28, hidden, true)) {
                wardrobe.setArmourHidden(!hidden);
                Sounds.play(SoundKeys.ui("wear"));
            }
            ry += 40;
        }

        // --- the rows: three across, each a list to drop down -------------------------------
        List<Row> rows = rowsNow(section);
        int columns = 3;
        float gap = 12, rowH = 48, boxH = 26;
        float shelfY;
        if (section == Section.ARMOUR) {
            // the armour by the part of the body it guards: a band each, its name and how often a blow lands there
            float gutter = Math.min(96, w * 0.14f);
            float colW = (w - gutter - (columns - 1) * gap) / columns;
            int line = 0;
            for (Armour.Location at : Armour.Location.values()) {
                List<Row> part = armourOf(at);
                if (part.isEmpty()) continue;
                float ly = ry + line * rowH;
                ui.rect(x - 4, ly - 4, w + 8, rowH - 4, Theme.withAlpha(STEEL, line % 2 == 0 ? 0.42f : 0.26f));
                ui.rect(x - 4, ly - 4, 3, rowH - 4, STEEL_LIGHT);
                m.carved(m.heading, at.label(), x + 6, ly + 2, STEEL_TEXT);
                // what it rolls (or that it is bare), and how often a blow lands there
                String share = at.weight() + "%";
                ui.text(m.small, fit(m.small, armourDice(at), gutter - 14 - m.small.width(share)), x + 6, ly + 24,
                        Theme.withAlpha(STEEL_TEXT, 0.8f));
                ui.text(m.small, share, x + gutter - 6 - m.small.width(share), ly + 24, Theme.withAlpha(STEEL_TEXT, 0.5f));
                for (int i = 0; i < part.size(); i++) {
                    row(ui, m, part.get(i), x + gutter + i * (colW + gap), ly, colW, boxH, lib, body, style);
                }
                line++;
            }
            shelfY = ry + line * rowH + 2;
        } else {
            float colW = (w - (columns - 1) * gap) / columns;
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                float sx = x + (i % columns) * (colW + gap);
                float sy = ry + (i / columns) * rowH;
                row(ui, m, row, sx, sy, colW, boxH, lib, body, style);
            }
            shelfY = ry + ((rows.size() + columns - 1) / columns) * rowH + 2;
        }
        m.shelf(x - 8, shelfY, w + 16);

        // --- the dye shelf: the focused layer's colours ----------------------------------
        float dy = shelfY + 22;
        Boolean fl = focusLayer;
        String fItem = worn(wardrobe, focus, fl), fColour = wornColour(wardrobe, focus, fl);
        List<Wearables.Colour> fOpts = cols(focus, fl);
        boolean anyInStyle = fItem != null && !lib.colourOptions(body, focus, fItem, style).isEmpty();
        String what = focus == Slot.BODY ? "Skin" : focusName();
        String picked = fItem == null ? "nothing worn"
                : !fOpts.isEmpty() ? colourName(fColour)
                : Wearables.metal(focus, fItem) ? colourName(fColour) + " (the metal's own)"
                : !anyInStyle ? "none in this style"
                : "own colours";
        if (offered != null && fOpts.stream().noneMatch(o -> o.option().equals(offered) && !o.unlocked())) {
            offered = null;
        }
        // every colour it can be, as one grid in the palette's order: its own shades among them
        List<Cell> cells = grid(fOpts, o -> swatch(lib, body, focus, fItem, style, o));
        int across = 25;
        float pw = (float) Math.floor(Math.min(28, (w + 2) / across)), ph = 16;
        float gy = dy + 30;
        String hovered = null;
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            Wearables.Colour o = cell.colour();
            float px = x + (i % across) * pw, cy = gy + (i / across) * (ph + 2), cw = pw - 2;
            int rgb = cell.rgb();
            int carried = o.unlocked() ? 0 : dyesOf(o.option());
            ui.rect(px, cy, cw, ph, Medieval.INK);
            ui.rect(px + 1, cy + 1, cw - 2, ph - 2, rgb < 0 ? Medieval.INK_FADED : Theme.hex(rgb & 0xFFFFFF, 1f));
            if (!o.unlocked()) {
                ui.rect(px + 1, cy + 1, cw - 2, ph - 2, Theme.withAlpha(Medieval.INK, carried > 0 ? 0.25f : 0.55f));
                if (carried > 0) m.bottle(px + cw / 2 - 3, cy + ph - 3, rgb >= 0 ? rgb : 0xffffff);
                else m.padlock(px + cw / 2, cy + ph / 2 + 1);
            }
            boolean on = worn(cell, fColour) || o.option().equals(offered);
            if (on) ui.outline(px - 2, cy - 2, cw + 4, ph + 4, 2, Medieval.GOLD);
            else if (ui.hover(px, cy, cw, ph)) ui.outline(px - 1, cy - 1, cw + 2, ph + 2, 1, Medieval.CREAM);
            if (ui.hover(px, cy, cw, ph)) hovered = colourName(o.option());
            if (ui.clicked(px, cy, cw, ph)) {
                offered = pickColour(focus, fl, o);
                if (o.unlocked()) Sounds.play(SoundKeys.ui("wear"));
            }
        }
        // its own colours, none picked: the button at the end of the heading
        String ownLabel = focus == Slot.MOUTH ? "Natural" : "Own colours";
        float ow = m.small.width(ownLabel) + 24;
        String heading = "Colour · " + what + ": " + picked + (hovered != null ? "  ›  " + hovered : "");
        m.carved(m.heading, fit(m.heading, heading, w - (fOpts.isEmpty() ? 0 : ow + 10)), x, dy,
                fOpts.isEmpty() ? Medieval.BRASS : Medieval.GOLD);
        if (!fOpts.isEmpty() && m.button(ownLabel, x + w - ow, dy - 2, ow, 26, fColour == null, fColour != null)) {
            setWornColour(wardrobe, focus, fl, null);
            Sounds.play(SoundKeys.ui("wear"));
        }
        int paletteRows = (cells.size() + across - 1) / across;
        float ny = paletteRows == 0 ? gy - 2 : gy + paletteRows * (ph + 2) + 2;
        if (offered != null && player != null) {
            String dye = colourName(offered);
            String on = focus == Slot.BODY ? "the skin" : current(focus);
            String ask = "Use up a " + dye + " dye to make " + on + " " + dye.toLowerCase(Locale.ROOT) + " for good?";
            ui.text(m.small, fit(m.small, ask, w - 214), x, ny + 6, Medieval.CREAM);
            if (m.button("Use dye", x + w - 204, ny - 2, 100, 30, false, true)) {
                keepId(focus, fl);
                boolean armourLayer = fl != null ? fl : top(wardrobe, focus);
                if (Wearables.useDye(wardrobe, player.inventory(), focus, armourLayer, offered)) {
                    Sounds.play(SoundKeys.ui("secret"));
                    game.toast(dye + " unlocked for " + on, com.larsons.game.world.Rarity.RARE.colour(1f));
                    game.saveWardrobe();
                }
                offered = null;
            }
            if (m.button("Keep it", x + w - 100, ny - 2, 100, 30, false, true)) offered = null;
        } else if (!fOpts.isEmpty() && player == null) {
            ui.text(m.small, "Any colour of the palette (the item's own shades among them). The Forge's armour is in the Armour section.",
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
        Boolean layer = row.layer();
        List<Option> opts = rowOpts(row);
        String over = over(row);
        String current = worn(wardrobe, slot, layer);
        int at = current(opts, slot, wardrobe, layer);
        boolean missing = current != null && at < 0;
        String folder = current == null ? null : lib.styled(body, slot, current, style);
        boolean hasArt = current == null || lib.entry(body, slot, folder) != null;
        boolean iron = row.kind() == Kind.ARMOUR || section == Section.ARMOUR;
        boolean focused = focus == slot && java.util.Objects.equals(focusLayer, layer);

        // the name, and an armour piece's dice
        String name = row.label();
        if (iron && at > 0 && opts.get(at).forged() != null) {
            name += " · d" + opts.get(at).forged().sides();               // the Forge's own die
        } else if (iron && at > 0 && opts.get(at).wearable() != null) {
            int sides = Armour.sides(opts.get(at).wearable());
            if (sides > 0) name += " · d" + sides;
        }
        if (ui.clicked(sx, sy, colW, 14)) focusOn(slot, layer);
        m.carved(m.small, name.toUpperCase(Locale.ROOT), sx + 2, sy, focused ? Medieval.GOLD
                : iron ? STEEL_TEXT : Medieval.BRASS);
        if (over != null && current != null) {
            // (worn under the armour: still worn, and what it does still counts - only not seen)
            String under = "under " + over.toLowerCase(Locale.ROOT);
            float nx = sx + 8 + m.small.width(name.toUpperCase(Locale.ROOT));
            ui.text(m.small, fit(m.small, under, sx + colW - nx), nx, sy, Theme.withAlpha(Medieval.CREAM, 0.6f));
        }
        List<Wearables.Colour> colours = cols(slot, layer);
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
        String shown = at < 0 ? label(slot, current) : opts.get(at).label(slot);
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
            chooseIn(wardrobe, row, opts.get(i));
            Sounds.play(SoundKeys.ui("wear"));
            focusOn(slot, layer);
        }
        if (ui.clicked(bx, by, bw, boxH)) {
            focusOn(slot, layer);
            openList(ui, row, opts, Math.max(0, at), bx, by + boxH + 2, Math.max(bw, 240));
        }
        if (!colours.isEmpty()) {
            // the colour picked for it (its own colours: an empty bezel)
            float gx = sx + colW - 11, gy = by + boxH / 2;
            String picked = wornColour(wardrobe, slot, layer);
            int sw = swatch(lib, body, slot, current, style, picked);
            m.gem(gx, gy, 10, sw, true);
            if (focused) ui.outline(gx - 12, gy - 12, 24, 24, 1, Medieval.GOLD);
            if (ui.clicked(gx - 10, gy - 10, 20, 20)) focusOn(slot, layer);
        }
    }

    private void openList(Ui ui, Row row, List<Option> opts, int at, float x, float y, float w) {
        listRow = row;
        listOptions = List.copyOf(opts);
        listDragging = false;
        // as many rows as the window has room for (up to LIST_ROWS); the rest are scrolled to
        int room = (int) Math.max(3, (ui.height() - 24) / LIST_ROW_H);
        int shown = Math.min(Math.min(LIST_ROWS, room), opts.size());
        listShown = shown;
        float h = shown * LIST_ROW_H + 8;
        listX = Math.min(x, ui.width() - w - 8);
        // (it hangs under the row - or over it, if there is no room below - and never off the window)
        float above = y - h - 34;
        listY = y + h <= ui.height() - 8 ? y : above >= 8 ? above : Math.max(8, ui.height() - 8 - h);
        listW = w;
        listScroll = Math.max(0, Math.min(opts.size() - shown, at - shown / 2));
        if (ui.input().usingPad()) {
            // the focus onto the choice worn now
            ui.input().warp(listX + listW / 2, listY + 4 + (at - listScroll) * LIST_ROW_H + LIST_ROW_H / 2);
        }
    }

    /** Scroll the list dropped down to {@code to} (kept in range). */
    private void scrollList(int to) {
        listScroll = Math.max(0, Math.min(listOptions.size() - listShown, to));
    }

    /**
     * The list's input, before anything else has it: the wheel (anywhere: the
     * list is all there is while it is down) and the gamepad's scrolling go
     * through it; its scroll bar is dragged or clicked a page up or down; a
     * click on a choice wears it; anywhere else shuts it.
     */
    private void listClick(Ui ui) {
        Input in = ui.input();
        int n = listOptions.size(), shown = listShown;
        float h = shown * LIST_ROW_H + 8;
        if (in.scroll() != 0) {
            int notches = (int) Math.signum(in.scroll()) * Math.max(1, (int) Math.round(Math.abs(in.scroll())));
            scrollList(listScroll - notches);
            in.consumeScroll();
        }
        boolean bar = n > shown;
        float bx = listX + listW - LIST_BAR - 3, top = listY + 4, track = shown * LIST_ROW_H;
        if (listDragging) {
            if (!in.mouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
                listDragging = false;
            } else {
                float thumb = Math.max(18, track * shown / n);
                double f = (in.mouseY() - top - thumb / 2) / Math.max(1, track - thumb);
                scrollList((int) Math.round(f * (n - shown)));
            }
            in.consumeMouse(GLFW_MOUSE_BUTTON_LEFT);
            return;
        }
        if (!in.mousePressed(GLFW_MOUSE_BUTTON_LEFT)) return;
        in.consumeMouse(GLFW_MOUSE_BUTTON_LEFT);
        if (bar && ui.hover(bx - 2, top, LIST_BAR + 5, track)) {
            // the scroll bar: its thumb is dragged; above or below it, a page up or down
            float thumb = Math.max(18, track * shown / n);
            float thumbY = top + (track - thumb) * listScroll / Math.max(1, n - shown);
            if (in.mouseY() < thumbY) scrollList(listScroll - shown);
            else if (in.mouseY() > thumbY + thumb) scrollList(listScroll + shown);
            else listDragging = true;
            return;
        }
        if (ui.hover(listX, listY + 4, listW, shown * LIST_ROW_H)) {
            int i = listScroll + (int) ((in.mouseY() - listY - 4) / LIST_ROW_H);
            if (i >= 0 && i < listOptions.size()) {
                chooseIn(wd, listRow, listOptions.get(i));
                Sounds.play(SoundKeys.ui("wear"));
            }
        } else if (ui.hover(listX, listY, listW, h)) {
            return;                                     // (its rim: nothing)
        }
        listRow = null;
    }

    /**
     * The list dropped down, over everything: the rows in sight, and every
     * row a place for the gamepad's focus - those out of sight scroll into it
     * as the focus goes on past the end - and the scroll bar.
     */
    private void list(Ui ui, Medieval m) {
        int n = listOptions.size(), shown = listShown;
        float h = shown * LIST_ROW_H + 8;
        boolean iron = listRow.kind() == Kind.ARMOUR || ARMOUR.contains(listRow) && section == Section.ARMOUR;
        boolean bar = n > shown;
        float rowW = listW - (bar ? LIST_BAR + 6 : 0);
        m.shadow(listX, listY, listW, h, 6);
        if (iron) {
            ui.rect(listX, listY, listW, h, Theme.rgb(40, 44, 50, 1f));
            ui.outline(listX, listY, listW, h, 2, STEEL_LIGHT);
        } else {
            m.parchment(listX, listY, listW, h);
            ui.outline(listX, listY, listW, h, 2, Medieval.BRASS);
        }
        int at = current(listOptions, listRow.slot(), wd, listRow.layer());
        float top = listY + 4, track = shown * LIST_ROW_H;
        ui.clip(listX, top, listW, track);
        for (int i = 0; i < n; i++) {
            float ry = top + (i - listScroll) * LIST_ROW_H;
            // (every row can take the focus; those clipped out of sight are scrolled to)
            ui.target(listX + 3, ry, rowW - 6, LIST_ROW_H, Ui.Kind.BUTTON);
            if (i < listScroll || i >= listScroll + shown) continue;
            Option o = listOptions.get(i);
            boolean hot = !listDragging && ui.hover(listX, ry, rowW, LIST_ROW_H);
            if (hot) ui.rect(listX + 3, ry, rowW - 6, LIST_ROW_H, iron ? Theme.rgb(84, 90, 100, 1f)
                    : Theme.withAlpha(Medieval.BRASS, 0.35f));
            if (i == at) ui.outline(listX + 3, ry, rowW - 6, LIST_ROW_H, 1, Medieval.GOLD);
            if (o.wearable() != null) ui.disc(listX + 13, ry + LIST_ROW_H / 2, 4, o.wearable().wearable().rarity().colour(1f));
            String label = o.item() == null ? "None" : o.label(listRow.slot());
            if (iron && o.wearable() != null && Armour.sides(o.wearable()) > 0) label += "   d" + Armour.sides(o.wearable());
            ui.text(m.body, fit(m.body, label, rowW - 36), listX + 24, ry + (LIST_ROW_H - m.body.lineHeight()) / 2,
                    iron ? STEEL_TEXT : o.item() == null ? Medieval.INK_FADED : Medieval.INK);
        }
        ui.unclip();
        if (bar) {
            // the scroll bar: how far down the list is, and how much of it is in sight
            float bx = listX + listW - LIST_BAR - 3;
            float thumb = Math.max(18, track * shown / n);
            float thumbY = top + (track - thumb) * listScroll / Math.max(1, n - shown);
            boolean hot = listDragging || ui.hover(bx - 2, top, LIST_BAR + 5, track);
            ui.rect(bx, top, LIST_BAR, track, Theme.withAlpha(iron ? STEEL : Medieval.INK, iron ? 0.9f : 0.18f));
            ui.rect(bx + 1, thumbY, LIST_BAR - 2, thumb, iron ? (hot ? STEEL_TEXT : STEEL_LIGHT)
                    : hot ? Medieval.GOLD : Medieval.BRASS);
            // how many more there are above and below
            float[] c = iron ? STEEL_TEXT : Medieval.INK_FADED;
            if (listScroll > 0) moreMark(ui, m, bx, top + 2, listScroll, true, c);
            int below = n - listScroll - shown;
            if (below > 0) moreMark(ui, m, bx, top + track - m.small.lineHeight() - 2, below, false, c);
        }
    }

    /** "3 more" and a little arrow, up or down, just left of the scroll bar at {@code barX}. */
    private static void moreMark(Ui ui, Medieval m, float barX, float y, int more, boolean up, float[] c) {
        String s = more + " more";
        float tx = barX - m.small.width(s) - 18;
        ui.rect(tx - 4, y - 1, m.small.width(s) + 20, m.small.lineHeight() + 2, Theme.withAlpha(Medieval.INK, 0.06f));
        ui.text(m.small, s, tx + 12, y, c);
        float ax = tx + 4, ay = y + m.small.lineHeight() / 2, d = up ? -1 : 1;
        ui.batch().quad(ui.white(), ax - 4, ay - 2 * d, 0, ax + 4, ay - 2 * d, 0, ax, ay + 3 * d, 0, ax, ay + 3 * d, 0,
                0, 0, 1, 1, c[0], c[1], c[2], c[3]);
    }

    /** What the focused layer wears, by name: "the Baggy shirt". */
    private String current(Slot slot) {
        List<Option> opts = opts(slot);
        int at = current(opts, slot, wd, focusLayer);
        return "the " + (at < 0 ? label(slot, worn(wd, slot, focusLayer)) : opts.get(at).label(slot));
    }

    private void focusOn(Slot slot, Boolean layer) {
        if (focus != slot || !java.util.Objects.equals(focusLayer, layer)) offered = null;
        focus = slot;
        focusLayer = layer;
    }

    /** What the dye shelf's layer is called: the row's name ("Gorget", "Necklace"), else the slot's. */
    private String focusName() {
        for (Section s : Section.values()) {
            for (Row r : rowsNow(s)) {
                if (r.slot() == focus && java.util.Objects.equals(r.layer(), focusLayer)) return r.label();
            }
        }
        return focus.label();
    }

    /**
     * What the armour worn on {@code at} rolls to block a blow there ("d8 + d6"),
     * or that nothing guards it ("bare").
     */
    private String armourDice(Armour.Location at) {
        List<String> dice = new ArrayList<>();
        for (Armour.Piece p : Armour.worn(wd, player == null ? null : player.inventory())) {
            if (p.at() == at) dice.add(p.notation(Armour.Rules.defaults()));
        }
        return dice.isEmpty() ? "bare" : String.join(" + ", dice);
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
     * What everything worn gives ({@link com.larsons.game.world.Gear}): a
     * "Gifts" button in the cornice, by Back, and the list it opens (or shows
     * while the pointer is on it) - a line each, in its colour. Nothing when
     * nothing worn was forged with an effect.
     */
    private void gifts(Ui ui, Medieval m, float bx, float by, float right) {
        if (player == null) return;
        com.larsons.game.world.Gear.Totals g = com.larsons.game.world.Gear.of(wd, player.inventory());
        if (g.isEmpty()) {
            giftsOpen = false;
            return;
        }
        List<java.util.Map.Entry<com.larsons.game.world.Gear.Effect, Double>> lines =
                new ArrayList<>(g.amounts().entrySet());
        float bw = 160, bh = 34;
        if (m.button("Gifts (" + lines.size() + ")", bx, by, bw, bh, giftsOpen, true)) {
            giftsOpen = !giftsOpen;
            Sounds.play(SoundKeys.ui("tab"));
        }
        if (!giftsOpen && !ui.hover(bx, by, bw, bh)) return;
        String heading = "What you wear gives";
        float lh = m.small.lineHeight() + 3, pw = m.small.width(heading);
        for (var e : lines) pw = Math.max(pw, m.small.width(e.getKey().said(e.getValue())));
        pw = Math.min(pw + 28, right - 40);
        float ph = 14 + (lines.size() + 1) * lh, px = right - pw, py = by + bh + 8;
        m.shadow(px, py, pw, ph, 6);
        ui.rect(px, py, pw, ph, Theme.withAlpha(Medieval.INK, 0.94f));
        ui.outline(px, py, pw, ph, 1, Medieval.GOLD);
        float ly = py + 7;
        ui.text(m.small, heading, px + 14, ly, Medieval.GOLD);
        ly += lh;
        for (var e : lines) {
            ui.text(m.small, fit(m.small, e.getKey().said(e.getValue()), pw - 28), px + 14, ly,
                    Theme.hex(e.getKey().rgb(), 1f));
            ly += lh;
        }
    }

    /**
     * Her close up, waist up, at rest, from the side the mirror is turned to
     * ({@link CloseUp}: every item, layer on layer, playing on); where those
     * are not drawn for her body, her sprite brought close instead. Returns
     * the frame shown, or -1 for the sprite.
     */
    private int closeUp(Ui ui, float x, float y, float w, float h, Wardrobe wd) {
        if (closeUps == null) closeUps = new CloseUp(game);
        try {
            int frame = closeUps.draw(ui, x, y, w, h, wd, player == null ? null : player.inventory(), facing, time, 1);
            if (frame >= 0) return frame;
        } catch (RuntimeException e) {
            // (a close-up that cannot be drawn is never worth the game: her sprite is shown instead)
            System.err.println("[wardrobe] close-up: " + e);
        }
        // no close-ups of this body: the sprite, brought close on the head and shoulders
        SpriteLibrary lib = game.sprites();
        SpriteView view = new SpriteView(Elevation.SIDE, facing, Elevation.SIDE.renderAngle());
        LayerStack.Result r = LayerStack.resolve(lib, wd, AnimState.REST, time, view, previewMemory);
        // (about the top 0.85 m of her - head to waist - fills the glass)
        float ppm = h / 0.85f;
        float footY = y + h * 0.06f + 1.78f * ppm;
        LayerStack.drawFlat(ui.batch(), r, x + w / 2, footY, ppm);
        return -1;
    }

    private static String fit(Font font, String s, float width) {
        if (font.width(s) <= width) return s;
        while (s.length() > 1 && font.width(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
