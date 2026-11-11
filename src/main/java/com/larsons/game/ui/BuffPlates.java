package com.larsons.game.ui;

import com.larsons.game.world.Buffs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the cards she has played - and the potions she has drunk, the scrolls
 * she has read - are doing for her now, down the left of the screen: a brass
 * plate for each, with its icon in a socket, what it is and how much ("Spell
 * damage ×1.3"), where it came from, and a gauge that runs down with its
 * time (or its hits, or its ward) beside the time left. One about to end
 * turns red.
 */
public final class BuffPlates {

    /** A plate's size, and the gap under it. */
    public static final float WIDTH = 236, HEIGHT = 42, GAP = 4;
    /** How many are shown before "and N more". */
    public static final int MOST = 8;

    private BuffPlates() {}

    /** The helps shown: those that end (a perk's, for good, is not one). */
    public static List<Buffs.Active> shown(Buffs buffs) {
        List<Buffs.Active> out = new ArrayList<>();
        for (Buffs.Active a : buffs.active()) {
            if (a.source.startsWith("Perk: ") && a.left() == Double.POSITIVE_INFINITY && a.charges() < 0) continue;
            out.add(a);
        }
        return out;
    }

    /** The plates, the first with its top-left corner at {@code (x, y)}; returns the height they took. */
    public static float draw(Ui ui, Buffs buffs, float x, float y, double time) {
        List<Buffs.Active> all = shown(buffs);
        float top = y;
        int n = Math.min(all.size(), MOST);
        for (int i = 0; i < n; i++) {
            plate(ui, all.get(i), x, y, time);
            y += HEIGHT + GAP;
        }
        if (all.size() > n) {
            ui.textShadowed(ui.small, "and " + (all.size() - n) + " more", x + 6, y, Theme.HINT);
            y += ui.small.lineHeight() + GAP;
        }
        return y - top;
    }

    private static void plate(Ui ui, Buffs.Active a, float x, float y, double time) {
        boolean ending = a.left() < 10;
        boolean blink = ending && (time * 3) % 1 < 0.5;
        float[] edge = ending ? Theme.WARNING : Theme.BRASS;
        ui.plate(x, y, WIDTH, HEIGHT, edge);
        // its icon, in a socket
        float r = HEIGHT / 2 - 3, cx = x + 4 + r, cy = y + HEIGHT / 2;
        ui.disc(cx, cy, r + 1, Theme.BRASS_DARK);
        ui.disc(cx, cy, r - 1, Theme.rgb(18, 13, 10, 1f));
        if (!CardArt.icon(ui, a.kind.key(), Math.round(cx - 12), Math.round(cy - 12), 24, 1f)) {
            ui.diamond(cx, cy, 7, Theme.ACCENT);
        }
        // what it is, and how much
        float tx = x + HEIGHT + 4, right = x + WIDTH - 8;
        String amount = a.amount();
        String what = a.kind.label() + (amount.isEmpty() ? "" : " " + amount)
                + (a.only == Buffs.Only.ANY ? "" : " (" + a.only.name().toLowerCase(Locale.ROOT) + ")");
        String span = span(a);
        float spanW = ui.small.width(span);
        ui.text(ui.small, Ui.fit(ui.small, what, right - tx - spanW - 8), tx, y + 4, Theme.TITLE);
        ui.text(ui.small, span, right - spanW, y + 4, blink ? Theme.WARNING : Theme.ACCENT);
        // where it came from, and the gauge that runs down
        float gy = y + HEIGHT - 13, gw = 78, gx = right - gw;
        ui.text(ui.tiny, Ui.fit(ui.tiny, a.source, gx - tx - 8), tx, gy - 3, Theme.ITEM_DISABLED);
        ui.rect(gx - 1, gy - 1, gw + 2, 8, Theme.SOOT);
        ui.rect(gx, gy, gw, 6, Theme.rgb(46, 34, 24, 1f));
        float k = (float) a.share();
        ui.rect(gx, gy, gw * k, 6, ending ? Theme.WARNING : Theme.ACCENT);
        ui.rect(gx, gy, gw * k, 2, Theme.withAlpha(Theme.TITLE, 0.35f));
    }

    /** How long it has: "4:12", "3 hits", or both the shorter way round. */
    private static String span(Buffs.Active a) {
        String time = "";
        if (a.left() < Double.POSITIVE_INFINITY) {
            int t = (int) Math.ceil(a.left());
            time = t / 60 + ":" + String.format(Locale.ROOT, "%02d", t % 60);
        }
        if (a.charges() > 0) {
            String hits = a.charges() + (a.charges() == 1 ? " hit" : " hits");
            return time.isEmpty() ? hits : hits + " · " + time;
        }
        return time;
    }
}
