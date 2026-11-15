package com.larsons.game.scene;

import com.larsons.game.core.Game;
import com.larsons.game.cutscene.CloseupLibrary;
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
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

/**
 * A character to look at, in a box that fits them: the whole figure, which
 * can be turned through the eight directions (the arrows, or dragged with the
 * mouse), seen from the three heights the sprites are drawn from, zoomed in
 * on (the buttons, or the wheel) and played in a few poses - or the face,
 * close up and at rest, to tell one nose or one pair of eyes from another
 * (the conversation close-ups' quiet frame, {@code assets/closeups}).
 *
 * <p>In a pose that swings or raises something, the hands hold it: what the
 * character's class carries ({@link #hold}), or a sword and a shield.
 *
 * <p>The new game's pages all show one ({@link CreatorScene}), and the people
 * editor's ({@code editor/CreativeScene}).
 */
public final class Portrait {

    /** The most the figure is zoomed in. */
    public static final double CLOSEST = 4;
    /** A character's height at size 1, and the room left over a head for hair, a hat or horns (m). */
    private static final double HEIGHT = 1.78, HEADROOM = 0.34;
    private static final String FACE_CLIP = "listen";

    /** A pose the figure can be played in. */
    private enum Pose {
        STAND("Standing", false), WALK("Walking", false), RUN("Running", false), JUMP("Jumping", false),
        ATTACK("Attack", true), SPIN("Spin attack", true), PARRY("Parry", true), BLOCK("Guard", true),
        BASH("Shield bash", true), LAUGH("Laughing", false), SURPRISE("Surprised", false);

        final String label;
        /** Whether it is a move made with what is in hand. */
        final boolean armed;

        Pose(String label, boolean armed) {
            this.label = label;
            this.armed = armed;
        }
    }

    private final Game game;
    private final LayerStack.Memory memory = new LayerStack.Memory();
    private Facing facing = Facing.SOUTH;
    private Elevation elevation = Elevation.SIDE;
    private Pose pose = Pose.STAND;
    private Stance holding;
    private boolean turning = true, face, mirrored;
    private double zoom = 1, time, turnTimer, dragged;
    private boolean dragging;
    /** A state to play in place of the pose's (a script's: any state at all), or null. */
    private AnimState override;

    public Portrait(Game game) {
        this.game = game;
    }

    /** What the character holds in every pose (a class's weapon); null: nothing, but in the armed poses a sword and a shield. */
    public void hold(Stance stance) {
        holding = stance;
    }

    public boolean face() { return face; }

    public void setFace(boolean on) {
        face = on;
    }

    public double zoom() { return zoom; }

    public void setZoom(double z) {
        zoom = Math.max(1, Math.min(CLOSEST, z));
    }

    public void setTurning(boolean on) {
        turning = on;
    }

    public void setFacing(Facing f) {
        facing = f;
    }

    /** The height the controls under the picture take. */
    public static float controlsHeight() {
        return 76;
    }

    /**
     * Draw {@code wardrobe}'s character, {@code size} times the usual height
     * (the box fits the tallest there can be, {@code tallest}), in {@code (x,
     * y, w, h)}: the picture, the controls under it, and {@code caption}
     * (null: none) along the bottom.
     */
    public void draw(Ui ui, double dt, float x, float y, float w, float h, Wardrobe wardrobe, double size,
                     double tallest, String caption) {
        time += dt;
        if (turning && !face && !dragging) {
            turnTimer += dt;
            if (turnTimer > 1.4) {
                turnTimer = 0;
                facing = facing.counterClockwise();
            }
        }
        ui.parchment(x, y, w, h);
        float captionH = caption == null ? 0 : 26;
        float ax = x + 10, ay = y + 10, aw = w - 20, ah = h - 20 - controlsHeight() - captionH;
        Input in = game.window().input();
        boolean over = ui.hover(ax, ay, aw, ah);
        if (over && in.scroll() != 0) setZoom(zoom + in.scroll() * 0.25);
        if (in.mousePressed(GLFW_MOUSE_BUTTON_LEFT) && over) {
            dragging = true;
            dragged = 0;
        }
        if (dragging) {
            if (!in.mouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
                dragging = false;
            } else {
                dragged += in.mouseDX();
                while (Math.abs(dragged) >= 34) {
                    if (face) mirrored = !mirrored;
                    else facing = dragged > 0 ? facing.counterClockwise() : facing.clockwise();
                    dragged -= Math.signum(dragged) * 34;
                    turning = false;
                }
            }
        }
        ui.rect(ax, ay, aw, ah, Theme.withAlpha(Theme.SOOT, 0.10f));
        ui.clip(ax, ay, aw, ah);
        try {
            if (face) face(ui, ax, ay, aw, ah, wardrobe);
            else figure(ui, ax, ay, aw, ah, wardrobe, size, tallest);
        } catch (RuntimeException e) {
            ui.textCentered(ui.small, "(no sprites for this look)", ax + aw / 2, ay + ah / 2, Theme.INK_FADED);
        }
        ui.unclip();
        ui.outline(ax, ay, aw, ah, 1, Theme.withAlpha(Theme.INK_FADED, 0.5f));
        controls(ui, x + 10, ay + ah + 8, w - 20);
        if (caption != null) {
            ui.textCentered(ui.body, Ui.fit(ui.body, caption, w - 16), x + w / 2, y + h - 30, Theme.INK);
        }
    }

    /** The whole figure, feet on the floor, zoomed towards the head. */
    private void figure(Ui ui, float x, float y, float w, float h, Wardrobe wardrobe, double size, double tallest) {
        SpriteLibrary lib = game.sprites();
        SpriteView view = new SpriteView(elevation, facing, elevation.renderAngle());
        Stance stance = holding != null ? holding : Stance.SWORD;
        AnimState state = override != null ? override : state(stance);
        boolean empty = override != null ? override.stance() == Stance.FREE : holding == null && !pose.armed;
        // what is in the hands for this pose: the class's own weapon is the stance's; a sword (and a shield) is the wardrobe's
        String right = wardrobe.get(Slot.CARRY_RIGHT), left = wardrobe.get(Slot.CARRY_LEFT);
        boolean sword = !empty && (stance == Stance.SWORD || stance == Stance.BLADE);
        if (sword) {
            wardrobe.set(Slot.CARRY_RIGHT, "sword");
            wardrobe.set(Slot.CARRY_LEFT, stance == Stance.SWORD ? "round_shield" : null);
        }
        LayerStack.Result r;
        try {
            double duration = LayerStack.duration(lib, wardrobe, state, view);
            double t = state.loops() ? time : time % (duration + 0.5);
            r = LayerStack.resolve(lib, wardrobe, state, t, view, memory, empty);
        } finally {
            if (sword) {
                wardrobe.set(Slot.CARRY_RIGHT, right);
                wardrobe.set(Slot.CARRY_LEFT, left);
            }
        }
        // the box fits the tallest there can be, head to foot; zoomed in, it closes on the head
        double whole = HEIGHT * tallest + HEADROOM;
        float fit = (float) Math.min((h - 22) / whole, (w - 8) / (1.5 * tallest));
        float ppm = (float) (fit * zoom * size);
        double head = HEIGHT * 0.9;                                  // (in the figure's own metres: scaled by its size)
        double near = Math.min(1, (zoom - 1) / 1.5);
        double floor = y + h - 14, middle = y + h / 2.0;
        // at no zoom the feet stand on the floor; closer, the head comes to the middle of the box
        double footY = floor + (middle + head * ppm - floor) * near;
        if (zoom <= 1.001) {
            ui.batch().rect(ui.white(), x + w / 2 - 46, (float) footY - 5, 92, 10, 0, 0, 1, 1, 0, 0, 0, 0.22f);
            // the usual height, marked on the wall behind
            float usual = (float) (fit * HEIGHT);
            ui.rect(x + 6, (float) footY - usual, 22, 2, Theme.INK_FADED);
            ui.text(ui.tiny, "1.00", x + 6, (float) footY - usual - 14, Theme.INK_FADED);
        }
        LayerStack.drawFlat(ui.batch(), r, x + w / 2, (float) footY, ppm);
    }

    private AnimState state(Stance stance) {
        return switch (pose) {
            case STAND -> holding == null ? AnimState.IDLE : stance.idle(false);
            case WALK -> holding == null ? AnimState.WALK : stance.moving(Stance.Pace.WALK, false);
            case RUN -> holding == null ? AnimState.RUN : stance.moving(Stance.Pace.RUN, false);
            case JUMP -> holding == null ? AnimState.JUMP : stance.jump();
            case ATTACK -> stance.attack(false);
            case SPIN -> stance.spin();
            case PARRY -> stance.parry();
            case BLOCK -> stance.block();
            case BASH -> stance.bash();
            case LAUGH -> AnimState.EMOTE_LAUGH;
            case SURPRISE -> AnimState.EMOTE_SURPRISE;
        };
    }

    /** The face, close up and at rest: the conversation close-up's quiet first frame, layer on layer. */
    private void face(Ui ui, float x, float y, float w, float h, Wardrobe wd) {
        SpriteLibrary lib = game.sprites();
        CloseupLibrary closeups = game.closeups();
        String root = lib.rootOf(wd.get(Slot.BODY));
        record Drawn(SheetTexture sheet, int[] palette) {}
        List<Drawn> layers = new ArrayList<>();
        boolean waiting = false;
        for (Slot slot : LayerStack.drawOrder(false, Elevation.SIDE, Facing.SOUTH_EAST)) {
            if (wd.hidden(slot)) continue;
            CloseupLibrary.Entry e = closeups.entryIn(root, slot, wd.get(slot), wd.style());
            SpriteLibrary.Resolved r = closeups.resolve(e, FACE_CLIP, null, mirrored);
            if (r == null) continue;
            SheetTexture t = lib.sheet(r);
            if (t == null) {
                waiting = true;
                continue;
            }
            int[] base = t.sharedPalette();
            int[] palette = base == null || e.variants() == null ? base : Variants.recolored(base,
                    e.variants().recolor(CloseupLibrary.choice(slot, wd.colour(slot), wd.skin())));
            layers.add(new Drawn(t, palette));
        }
        if (layers.isEmpty()) {
            ui.textCentered(ui.small, waiting ? "…" : "(no close-up of this look)", x + w / 2, y + h / 2,
                    Theme.INK_FADED);
            return;
        }
        // the frame shows the chest to over the head: the face sits a third of the way down it
        float size = (float) (Math.min(w, h) * (0.9 + 0.55 * (zoom - 1)));
        float fx = x + w / 2 - size / 2;
        float fy = (float) (y + h / 2 - size * (0.5 - 0.17 * Math.min(1, zoom - 1)));
        for (Drawn d : layers) {
            double[] region = d.sheet().region(mirrored);
            if (region[2] <= region[0] || region[3] <= region[1]) continue;
            float[] uv = d.sheet().uv(0, mirrored);
            ui.batch().rect(d.sheet().texture(), fx + (float) region[0] * size, fy + (float) region[1] * size,
                    (float) (region[2] - region[0]) * size, (float) (region[3] - region[1]) * size,
                    uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1,
                    ui.batch().palette(d.sheet().indexed() ? d.palette() : null));
        }
    }

    /** Turn, the three heights; closer and further, the face, the pose. */
    private void controls(Ui ui, float x, float y, float w) {
        float bh = 30, gap = 4;
        // --- the first row: round and round, and from how high
        float bw = 34;
        if (ui.button("<", x, y, bw, bh)) step(-1);
        if (ui.button(face ? "Flip" : turning ? "Stop" : "Turn", x + bw + gap, y, 62, bh, turning && !face, true)) {
            if (face) mirrored = !mirrored;
            else turning = !turning;
        }
        if (ui.button(">", x + bw + gap + 62 + gap, y, bw, bh)) step(1);
        float ex = x + 2 * bw + 62 + 3 * gap + 6;
        Elevation[] heights = Elevation.values();
        float ew = (x + w - ex - (heights.length - 1) * gap) / heights.length;
        for (int i = 0; i < heights.length; i++) {
            Elevation e = heights[i];
            String label = e == Elevation.SIDE ? "Level" : e == Elevation.MIDDLE ? "Above" : "Top";
            if (ui.button(label, ex + i * (ew + gap), y, ew, bh, e == elevation && !face, !face)) elevation = e;
        }
        // --- the second row: closer and further, the face, the pose
        float y2 = y + bh + 6;
        if (ui.button("-", x, y2, bw, bh, false, zoom > 1)) setZoom(zoom - 0.5);
        if (ui.button("+", x + bw + gap, y2, bw, bh, false, zoom < CLOSEST)) setZoom(zoom + 0.5);
        float fx = x + 2 * (bw + gap);
        if (ui.button(face ? "Body" : "Face", fx, y2, 58, bh, face, true)) face = !face;
        float px = fx + 58 + gap + 6, pw = x + w - px;
        Pose[] poses = Pose.values();
        if (ui.button("<", px, y2, 26, bh, false, !face)) pose(Math.floorMod(pose.ordinal() - 1, poses.length));
        if (ui.button(">", px + pw - 26, y2, 26, bh, false, !face)) pose((pose.ordinal() + 1) % poses.length);
        String label = face ? "At rest" : pose.label;
        ui.rect(px + 28, y2, pw - 56, bh, Theme.withAlpha(Theme.SOOT, 0.14f));
        ui.textCentered(ui.small, Ui.fit(ui.small, label, pw - 60), px + pw / 2, y2 + (bh - ui.small.lineHeight()) / 2,
                Theme.INK);
    }

    private void step(int by) {
        if (face) mirrored = !mirrored;
        else facing = by < 0 ? facing.clockwise() : facing.counterClockwise();
        turning = false;
    }

    private void pose(int index) {
        pose = Pose.values()[index];
        override = null;
        time = 0;
    }

    /**
     * Script commands: {@code face on|off}, {@code zoom n}, {@code turn
     * on|off|<facing>}, {@code height side|middle|top}, {@code pose <name>},
     * {@code state <key>} (any state there is, by its key: "swim").
     */
    public boolean command(String command, String argument) {
        String a = argument == null ? "" : argument.trim().toLowerCase(Locale.ROOT);
        switch (command) {
            case "face" -> face = !a.equals("off");
            case "zoom" -> setZoom(Double.parseDouble(a));
            case "turn" -> {
                if (a.equals("on") || a.equals("off")) turning = a.equals("on");
                else {
                    for (Facing f : Facing.values()) if (f.key().equals(a)) facing = f;
                    turning = false;
                }
            }
            case "height" -> {
                for (Elevation e : Elevation.values()) if (e.key().equals(a)) elevation = e;
            }
            case "state" -> {
                override = AnimState.byKey(a);
                time = 0;
            }
            case "pose" -> {
                for (Pose p : Pose.values()) {
                    if (p.name().equalsIgnoreCase(a) || p.label.equalsIgnoreCase(a)) pose(p.ordinal());
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
