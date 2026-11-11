package com.larsons.game.ui;

import com.larsons.game.gfx.Font;
import com.larsons.game.input.Action;
import com.larsons.game.input.Bind;
import com.larsons.game.input.Input;
import com.larsons.game.input.Pad;

import java.util.List;

/**
 * What to press, drawn: a key as its cap - a cream key with its name inked on
 * it - a mouse button as a plate, and the gamepad's buttons as they look on
 * an Xbox controller: A green, B red, X blue, Y yellow, the bumpers,
 * triggers and sticks dark with their names. Which is shown follows what was
 * last touched ({@link Input#usingPad}).
 */
public final class Glyphs {

    private Glyphs() {}

    private static final float[] KEY_FACE = Theme.rgb(232, 218, 188, 1f), KEY_SIDE = Theme.rgb(150, 126, 92, 1f);
    private static final float[][] FACE = {Theme.rgb(96, 176, 74, 1f), Theme.rgb(204, 66, 54, 1f),
            Theme.rgb(62, 120, 204, 1f), Theme.rgb(226, 184, 50, 1f)};

    /** The bind shown for {@code a}: its first on what was last touched, else its first on the other; null: none. */
    public static Bind shown(Input in, Action a) {
        List<Bind> first = in.bindings().of(a, in.usingPad()), second = in.bindings().of(a, !in.usingPad());
        return !first.isEmpty() ? first.get(0) : second.isEmpty() ? null : second.get(0);
    }

    private static String shortLabel(Bind b) {
        if (b == null) return "?";
        if (b.kind() != Bind.Kind.PAD) return b.label();
        return switch (b.code()) {
            case Pad.DPAD_UP -> "D↑";
            case Pad.DPAD_DOWN -> "D↓";
            case Pad.DPAD_LEFT -> "D←";
            case Pad.DPAD_RIGHT -> "D→";
            case Pad.LS -> "L3";
            case Pad.RS -> "R3";
            case Pad.LS_UP -> "LS↑";
            case Pad.LS_DOWN -> "LS↓";
            case Pad.LS_LEFT -> "LS←";
            case Pad.LS_RIGHT -> "LS→";
            case Pad.RS_UP -> "RS↑";
            case Pad.RS_DOWN -> "RS↓";
            case Pad.RS_LEFT -> "RS←";
            case Pad.RS_RIGHT -> "RS→";
            default -> b.label();
        };
    }

    private static Font font(Ui ui, float h) {
        return h >= 28 ? ui.body : h >= 20 ? ui.small : ui.tiny;
    }

    /** How wide {@link #draw} draws {@code a}'s key or button, {@code h} tall. */
    public static float width(Ui ui, Input in, Action a, float h) {
        return width(ui, shown(in, a), h);
    }

    public static float width(Ui ui, Bind b, float h) {
        if (b != null && b.kind() == Bind.Kind.PAD && b.code() <= Pad.Y) return h;
        return Math.max(h, font(ui, h).width(shortLabel(b)) + h * 0.55f);
    }

    /** Draw what to press for {@code a} with its top-left corner at {@code (x, y)}, {@code h} tall; returns its width. */
    public static float draw(Ui ui, Input in, Action a, float x, float y, float h) {
        return draw(ui, shown(in, a), x, y, h);
    }

    public static float draw(Ui ui, Bind b, float x, float y, float h) {
        float w = width(ui, b, h);
        Font f = font(ui, h);
        String label = shortLabel(b);
        if (b != null && b.kind() == Bind.Kind.PAD && b.code() <= Pad.Y) {
            // a face button: a coloured dome with its letter
            ui.disc(x + h / 2, y + h / 2 + 1, h / 2, Theme.SOOT);
            ui.disc(x + h / 2, y + h / 2, h / 2 - 1, FACE[b.code()]);
            ui.disc(x + h / 2 - h * 0.08f, y + h / 2 - h * 0.1f, h * 0.3f, new float[]{1, 1, 1, 0.16f});
            ui.text(f, label, x + (h - f.width(label)) / 2, y + (h - f.lineHeight()) / 2, Theme.SOOT);
            return w;
        }
        if (b != null && b.kind() == Bind.Kind.PAD) {
            // a bumper, a trigger, a stick: dark iron with a brass rim
            ui.rect(x, y, w, h, Theme.IRON);
            ui.rect(x + 1, y + 1, w - 2, Math.max(1, h * 0.4f), new float[]{1, 1, 1, 0.08f});
            ui.outline(x, y, w, h, 1, Theme.SOOT);
            ui.outline(x + 1, y + 1, w - 2, h - 2, 1, Theme.BRASS);
            ui.text(f, label, x + (w - f.width(label)) / 2, y + (h - f.lineHeight()) / 2, Theme.TITLE);
            return w;
        }
        // a key: its cream top standing on its darker side
        ui.rect(x, y, w, h, KEY_SIDE);
        ui.rect(x + 1, y + 1, w - 2, h - 4, KEY_FACE);
        ui.rect(x + 1, y + 1, w - 2, 1, new float[]{1, 1, 1, 0.5f});
        ui.outline(x, y, w, h, 1, Theme.SOOT);
        ui.text(f, label, x + (w - f.width(label)) / 2, y + (h - 3 - f.lineHeight()) / 2, Theme.INK);
        return w;
    }
}
