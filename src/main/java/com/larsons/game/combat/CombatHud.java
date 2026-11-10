package com.larsons.game.combat;

import com.larsons.game.gfx.Batch;
import com.larsons.game.math.Mat4;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SheetTexture;
import com.larsons.game.sprite.SpriteProfile;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.OrbitCamera;
import com.larsons.game.world.Ammo;
import com.larsons.game.world.Buffs;
import com.larsons.game.world.Item;
import com.larsons.game.world.ItemDef;
import com.larsons.game.world.Player;
import com.larsons.game.world.Stealth;
import com.larsons.game.world.Rarity;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The fighting on the HUD, over the world:
 *
 * <ul>
 *   <li><b>the dice</b> above her head - one D20 for each the swing rolls (a
 *       forged weapon's own dice: d4 to d20, a d% as two), in the weapon's
 *       rarity's colour (or the forged dice's), tumbling, then coming up their numbers
 *       one after another as the blow lands, their total over them and what
 *       the weapon's border adds under that;</li>
 *   <li><b>the damage</b> floating up off whatever was hit, in the weapon's
 *       colour, and poison's ticks in Venom's green;</li>
 *   <li>a dummy's or a flier's <b>health</b> over it once it has been hit;</li>
 *   <li>the <b>lock-on</b>'s reticle round what she is locked on to, its name
 *       and health under it ({@link #drawLock});</li>
 *   <li>her <b>mana</b>, beside the hotbar, and on its other side how soon
 *       she can make a <b>heavy attack</b> again; over them her own
 *       <b>health</b> and her <b>stamina</b> ({@link #drawVitals}).</li>
 * </ul>
 *
 * <p>The dice are pixel art, drawn at a whole number of screen pixels a
 * sprite pixel: the 64-pixel sheets on an ordinary screen, the 128-pixel
 * sheets on a high-density one, both 64 points across.
 */
public final class CombatHud {

    /** How big a die is on screen, in points. */
    public static final float DIE = 64;
    /** The most dice in a row; more go in a second row over the first. */
    public static final int ROW = 4;
    /** How long a die's landing plays: 8 frames at 24 fps. */
    public static final double LAND = 8 / 24.0;
    /** How far apart the dice come up, in seconds. */
    public static final double STAGGER = 0.06;

    private static final int MANA_RGB = 0x5D86FA, MANA_LIGHT = 0x92B9FF;
    private static final int HEAVY_RGB = 0xFB992C, HEAVY_LIGHT = 0xFFD27A;

    private final CombatSprites fx;

    public CombatHud(CombatSprites fx) {
        this.fx = fx;
    }

    /**
     * Draw the dice, the numbers and the dummies' health ({@code scale}:
     * device pixels to a point, which picks the dice's sheets).
     */
    public void draw(Ui ui, World world, OrbitCamera camera, float scale) {
        Combat combat = world.combat();
        Mat4 vp = camera.viewProjection(ui.width() / (double) Math.max(1, ui.height()));
        double now = combat.now();

        for (Target d : world.targets()) {
            if (!d.alive() || (d.sinceHurt() > 4 && d.health() >= d.maxHealth())) continue;
            float[] at = over(vp, camera, d.position(), d.height() + 0.12, ui);
            if (at == null) continue;
            float w = 64, h = 7, x = at[0] - w / 2, y = at[1] - h;
            float k = (float) (d.health() / d.maxHealth());
            ui.rect(x - 1, y - 1, w + 2, h + 2, Theme.withAlpha(Theme.BACKGROUND, 0.8f));
            ui.rect(x, y, w * k, h, k > 0.5f ? Theme.OK : k > 0.25f ? Theme.ACCENT : Theme.WARNING);
        }

        for (Combat.Popup p : combat.popups()) {
            double age = now - p.born();
            float[] at = over(vp, camera, p.ground(), p.rise(), ui);
            if (at == null) continue;
            float a = (float) Math.max(0, Math.min(1, (Combat.POPUP_SECONDS - age) / 0.4));
            float jitter = (float) ((Math.floorMod(Double.hashCode(p.born()) * 31 + p.text().hashCode(), 5) - 2) * 9);
            var font = p.small() ? ui.body : ui.large;
            float y = at[1] - (float) (age * 46) - font.lineHeight();
            float x = at[0] + jitter - font.width(p.text()) / 2;
            ui.textShadowed(font, p.text(), x, y, CombatColours.rgba(p.rgb(), a));
        }

        Combat.Show show = combat.show();
        if (show != null) dice(ui, world, camera, vp, show, now, scale);
    }

    /** The dice over her head. */
    private void dice(Ui ui, World world, OrbitCamera camera, Mat4 vp, Combat.Show show, double now, float scale) {
        float[] head = over(vp, camera, world.player().feet(), 1.85, ui);
        if (head == null) return;
        Strike s = show.strike();
        DiceRoll roll = s.roll();
        String px = scale >= 1.5f ? "_px128" : "_px64";
        int[] palette = CombatColours.diePalette(s.rarity(), s.border(), s.diceRgb());
        double t = now - show.start();
        double settle = Math.max(0, show.land() - show.start() - LAND);
        float fade = (float) Math.max(0, Math.min(1, Math.min(t / 0.08, (show.end() - now) / 0.3)));

        // a d% is two dice side by side: the tens' (00 - 90) and a d10's ones;
        // a die doing other than adding has its sum before it (× ÷ − %)
        List<List<Slot>> rows = rows(slots(roll), DIE, ROW);
        float gap = 2, bottom = head[1];
        boolean allUp = true;
        for (int r = 0; r < rows.size(); r++) {
            List<Slot> row = rows.get(r);
            float x = head[0] - width(row, DIE, gap) / 2;
            float y0 = bottom - (r + 1) * DIE - r * gap;
            for (Slot sl : row) {
                float w = sl.width(DIE);
                if (sl.glyph()) {
                    glyph(ui, sl.op(), x, y0, w, DIE, fade);
                    x += w + gap;
                    continue;
                }
                int i = sl.die();
                float y = y0;
                double landAt = settle + i * STAGGER;
                String[] die = face(roll.sidesOf(i), roll.face(i), sl.half());
                SheetTexture sheet;
                int frame;
                int j = i * 2 + sl.half();
                if (t < landAt) {
                    allUp = false;
                    sheet = fx.sheet(die[0] + px, "roll");
                    frame = (int) Math.floor((t + j * 0.137) * 24);
                    y -= (float) Math.abs(Math.sin((t + j * 0.37) * 9)) * 7;     // the dice hop as they tumble
                } else {
                    sheet = fx.sheet(die[0] + px, die[1]);
                    frame = (int) Math.min(7, Math.floor((t - landAt) * 24));
                    if (t - landAt < LAND) allUp = false;
                }
                if (sheet != null) image(ui.batch(), sheet, frame, palette, x, y, DIE, fade);
                x += w + gap;
            }
        }

        float top = bottom - rows.size() * DIE - (rows.size() - 1) * gap - 4;
        if (allUp) {
            String total = String.valueOf(roll.total());
            float[] c = CombatColours.rgba(s.rarity() == Rarity.COMMON ? 0xFFFFFF : s.rarity().rgb(), fade);
            top -= ui.large.lineHeight();
            ui.textShadowed(ui.large, total, head[0] - ui.large.width(total) / 2, top, c);
            String sum = roll.count() > 1 || !roll.plain() ? roll.notation() : "";
            if (!sum.isEmpty()) {
                ui.textShadowed(ui.small, sum, head[0] + ui.large.width(total) / 2 + 6,
                        top + ui.large.lineHeight() - ui.small.lineHeight() - 3, Theme.withAlpha(Theme.HINT, fade));
            }
        }
        for (Combat.Note note : show.notes()) {
            top -= ui.small.lineHeight() + 1;
            ui.textShadowed(ui.small, note.text(), head[0] - ui.small.width(note.text()) / 2, top,
                    CombatColours.rgba(note.rgb(), fade));
        }
    }

    /**
     * A row of dice of {@code sides} come up {@code values}, each {@code size}
     * points, centred on {@code cx} with their tops at {@code y}, in {@code
     * palette} - the Forge's look at a weapon's dice ({@code scale}: device
     * pixels to a point, which picks the sheets).
     */
    public static void landed(Ui ui, CombatSprites fx, int sides, int[] values, int[] palette, float cx, float y,
                              float size, float scale) {
        landed(ui, fx, new DiceRoll(sides, values), palette, cx, y, size, scale);
    }

    /** {@code roll}'s dice come up, and the sums between them, in a row (or rows) - as {@link #landed above}. */
    public static void landed(Ui ui, CombatSprites fx, DiceRoll roll, int[] palette, float cx, float y,
                              float size, float scale) {
        String px = scale * size / DIE >= 1.5f ? "_px128" : "_px64";
        float gap = 2;
        List<List<Slot>> rows = rows(slots(roll), size, 12);
        for (List<Slot> row : rows) {
            float x = cx - width(row, size, gap) / 2;
            for (Slot sl : row) {
                float w = sl.width(size);
                if (sl.glyph()) {
                    glyph(ui, sl.op(), x, y, w, size, 1f);
                } else {
                    String[] die = face(roll.sidesOf(sl.die()), roll.face(sl.die()), sl.half());
                    SheetTexture sheet = fx.sheet(die[0] + px, die[1]);
                    if (sheet != null) image(ui.batch(), sheet, 7, palette, x, y, size, 1f);
                }
                x += w + gap;
            }
            y += size + gap;
        }
    }

    /** One place in a row of dice: a die (or half a d%'s pair), or the sum before the next. */
    record Slot(int die, int half, String op) {
        boolean glyph() { return op != null; }

        float width(float die) { return glyph() ? die * 0.45f : die; }
    }

    /**
     * {@code roll}'s dice in order, a d% as its two, with the sum before
     * each die that does other than add - and a + between groups of dice of
     * different kinds.
     */
    static List<Slot> slots(DiceRoll roll) {
        List<Slot> out = new ArrayList<>();
        for (int i = 0; i < roll.count(); i++) {
            DiceRoll.Op op = roll.op(i);
            boolean group = i > 0 && (roll.sidesOf(i) != roll.sidesOf(i - 1) || op != roll.op(i - 1));
            if (op != DiceRoll.Op.ADD || group) out.add(new Slot(i, 0, op.symbol()));
            out.add(new Slot(i, 0, null));
            if (roll.sidesOf(i) == 100) out.add(new Slot(i, 1, null));
        }
        return out;
    }

    /** {@code slots} in rows of at most {@code most} dice (a sum stays with the die after it). */
    static List<List<Slot>> rows(List<Slot> slots, float die, int most) {
        List<List<Slot>> out = new ArrayList<>();
        List<Slot> row = new ArrayList<>();
        int dice = 0;
        for (Slot sl : slots) {
            boolean full = dice >= most && (sl.glyph() || row.isEmpty() || !row.get(row.size() - 1).glyph());
            if (full) {
                out.add(row);
                row = new ArrayList<>();
                dice = 0;
            }
            row.add(sl);
            if (!sl.glyph()) dice++;
        }
        if (!row.isEmpty()) out.add(row);
        return out;
    }

    private static float width(List<Slot> row, float die, float gap) {
        float w = 0;
        for (Slot sl : row) w += sl.width(die) + gap;
        return Math.max(0, w - gap);
    }

    /** A sum between dice (+ − × ÷ %), centred in its place. */
    private static void glyph(Ui ui, String op, float x, float y, float w, float h, float fade) {
        var font = h >= 40 ? ui.large : ui.body;
        ui.textShadowed(font, op, x + (w - font.width(op)) / 2, y + (h - font.lineHeight()) / 2,
                Theme.withAlpha(Theme.TITLE, fade));
    }

    /**
     * Which die shows {@code value} of a roll of {@code sides}, and its sheet:
     * {@code {"d8", "land_05"}}. A d% shows on two ({@code half} 0 the tens'
     * die, 00 - 90, and 1 a d10's ones, 0 for ten): 47 is 40 and 7, 100 is 00
     * and 0. Dice of a kind there is no art for show as a d20's.
     */
    static String[] face(int sides, int value, int half) {
        if (sides == 100) {
            int ones = value % 10;
            return half == 0 ? new String[]{"dpct", String.format("land_%02d", (value - ones) % 100)}
                    : new String[]{"d10", String.format("land_%02d", ones == 0 ? 10 : ones)};
        }
        String kind = switch (sides) {
            case 4, 6, 8, 10, 12, 20 -> "d" + sides;
            default -> "d20";
        };
        return new String[]{kind, String.format("land_%02d", Math.max(1, Math.min(sides, value)))};
    }

    /**
     * Her mana: a bar to the right of the hotbar, whose slots' middle is
     * {@code cy} on screen.
     */
    public void drawMana(Ui ui, Combat combat, float cy) {
        float w = 150, h = 12;
        float x = ui.width() / 2f + 2 * 64 + 28 + 26, y = cy - h / 2;
        float k = (float) (combat.mana() / Math.max(1e-6, combat.maxMana()));
        ui.bar(x - 3, y - 3, w + 6, h + 6, k, CombatColours.rgba(MANA_RGB, 0.95f));
        String label = "Mana " + (int) Math.floor(combat.mana());
        ui.textShadowed(ui.small, label, x, y - ui.small.lineHeight() - 3, CombatColours.rgba(MANA_LIGHT, 1f));
    }

    /**
     * The heavy attack's cooldown: a bar to the left of the hotbar, whose
     * slots' middle is {@code cy} on screen, filling back up after each heavy
     * attack - "Heavy (X) ready" once it is full. Nothing while what she
     * holds has no heavy attack.
     */
    public void drawHeavy(Ui ui, Player player, float cy) {
        if (player.stance().heavy() == null) return;
        float w = 150, h = 12;
        float x = ui.width() / 2f - 2 * 64 - 28 - 26 - w, y = cy - h / 2;
        double wait = player.heavyWait(), full = player.heavyCooldown();
        float k = full <= 0 ? 1 : (float) Math.max(0, Math.min(1, 1 - wait / full));
        boolean ready = wait <= 0;
        ui.bar(x - 3, y - 3, w + 6, h + 6, k, CombatColours.rgba(HEAVY_RGB, ready ? 0.95f : 0.55f));
        String key = ui.input().label(com.larsons.game.input.Action.HEAVY);
        String label = ready ? "Heavy (" + key + ") ready" : "Heavy (" + key + ") in " + (int) Math.ceil(wait) + " s";
        ui.textShadowed(ui.small, label, x + w - ui.small.width(label), y - ui.small.lineHeight() - 3,
                CombatColours.rgba(ready ? HEAVY_LIGHT : 0xFFFFFF, 1f));
    }

    private static final int LOCK_RGB = 0xFFD27A, HEALTH_RGB = 0xE0503C, HEALTH_LIGHT = 0xFF8A6E;
    private static final int STAMINA_RGB = 0x7CC24E, STAMINA_LIGHT = 0xB8EA7E;

    /**
     * The lock-on's reticle: four corner brackets closing round what she is
     * locked on to (a beat after locking on they settle and pulse), its name
     * and its health under it, and "High ground" when her shots at it have
     * it.
     */
    public void drawLock(Ui ui, World world, OrbitCamera camera) {
        Target t = world.combat().locked();
        if (t == null) return;
        Mat4 vp = camera.viewProjection(ui.width() / (double) Math.max(1, ui.height()));
        float[] mid = screen(vp, camera, t.position().add(0, t.height() / 2, 0), ui);
        float[] top = screen(vp, camera, t.position().add(0, t.height(), 0), ui);
        if (mid == null || top == null) return;
        double now = world.combat().now();
        float r = Math.max(22, Math.min(90, Math.abs(mid[1] - top[1]) * 1.25f + 10));
        r *= 1 + 0.06f * (float) Math.sin(now * 6);
        float len = r * 0.42f, th = 3;
        float[] c = CombatColours.rgba(LOCK_RGB, 0.95f), shade = Theme.withAlpha(Theme.BACKGROUND, 0.7f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                float x = mid[0] + sx * r, y = mid[1] + sy * r;
                float hx = sx < 0 ? x : x - len, vy = sy < 0 ? y : y - len;
                ui.rect(hx - 1, (sy < 0 ? y : y - th) - 1, len + 2, th + 2, shade);
                ui.rect((sx < 0 ? x : x - th) - 1, vy - 1, th + 2, len + 2, shade);
                ui.rect(hx, sy < 0 ? y : y - th, len, th, c);
                ui.rect(sx < 0 ? x : x - th, vy, th, len, c);
            }
        }
        String label = t.name() + "  " + (int) Math.ceil(t.health()) + " / " + (int) Math.round(t.maxHealth());
        if (Combat.highGround(world.player().feet(), t)) label += "  ·  High ground";
        ui.textShadowed(ui.small, label, mid[0] - ui.small.width(label) / 2, mid[1] + r + 6, c);
    }

    /**
     * Her health, and her stamina while it is not full or she climbs: two
     * bars over the hotbar's left, whose slots' middle is {@code cy}.
     */
    /** The breath bar's colours: water blue, and its highlight. */
    static final int BREATH_RGB = 0x3C8CD8, BREATH_LIGHT = 0xA8DCFF;

    public void drawVitals(Ui ui, Player player, float cy) {
        float w = 150, h = 10;
        float x = ui.width() / 2f - 2 * 64 - 28 - 26 - w, y = cy - 44;
        bar(ui, x, y, w, h, player.health() / Player.MAX_HEALTH, HEALTH_RGB, HEALTH_LIGHT,
                "Health " + (int) Math.ceil(player.health()), player.sinceHurt() < 0.3);
        float sx = ui.width() / 2f + 2 * 64 + 28 + 26;
        if (player.stamina() < Player.MAX_STAMINA - 0.5 || player.climbing() != null) {
            bar(ui, sx, y, w, h, player.stamina() / Player.MAX_STAMINA, STAMINA_RGB, STAMINA_LIGHT,
                    "Stamina " + (int) Math.ceil(player.stamina()), player.stamina() < Player.CLIMB_MIN);
        }
        if (player.underWater() || player.breath() < Player.MAX_BREATH - 0.05) {
            // her breath, over the stamina: under the water it runs out
            bar(ui, sx, y - 30, w, h, player.breath() / Player.MAX_BREATH, BREATH_RGB, BREATH_LIGHT,
                    player.breath() <= 0 ? "Drowning!" : "Breath " + (int) Math.ceil(player.breath()) + " s",
                    player.breath() < 5);
        }
    }

    /**
     * How easily she is noticed ({@link Stealth}), over the health bar while
     * she is crouched, hidden or it is dark: a meter emptying the quieter she is.
     */
    public void drawStealth(Ui ui, World world, float cy) {
        Player p = world.player();
        Stealth s = world.stealth();
        if (!p.crouched() && !s.hidden() && s.dark() < 0.5 && !p.underWater()) return;
        float w = 150, h = 6;
        float x = ui.width() / 2f - 2 * 64 - 28 - 26 - w, y = cy - 44 - 30;
        int rgb = s.hidden() ? STEALTH_HIDDEN : s.score() > 1.2 ? 0xFF9A5A : 0xE8E0C8;
        String label = (s.hidden() ? "Hidden - sneak attacks ×" + trim(Stealth.SNEAK_DAMAGE) : s.label())
                + (s.grass() ? " · in the grass" : "") + (s.dark() > 0.5 ? " · dark" : "");
        bar(ui, x, y, w, h, Math.min(1, s.score() / 1.7), rgb, rgb, label, false);
    }

    /**
     * Under the mana bar: the ammunition of what she holds - the uses left of
     * the one in use, and how many more she carries - and the coins in her purse.
     */
    public void drawSupplies(Ui ui, World world, float cy) {
        float x = ui.width() / 2f + 2 * 64 + 28 + 26, y = cy + 12;
        Player p = world.player();
        Item held = world.wielded();
        ItemDef ammo = held == null ? null : Ammo.of(held.def());
        if (ammo != null && world.ammo().enabled()) {
            int left = world.ammo().left(held), carried = world.ammo().carried(held, p.inventory());
            boolean out = left == 0 && carried == 0;
            String line = out ? (Ammo.shoots(held.def()) ? "Out of " + Ammo.said(ammo) : "Dull: no " + Ammo.said(ammo))
                    : ammo.name() + ": " + left + (left == 1 ? " use" : " uses") + " · " + carried + " more";
            if (world.buffs().has(Buffs.Kind.AMMO_FREE)) line = ammo.name() + ": none used (a card)";
            ui.textShadowed(ui.small, line, x, y, CombatColours.rgba(out ? 0xFF8A6E : Combat.AMMO_RGB, 1f));
            y += ui.small.lineHeight() + 2;
        }
        ui.textShadowed(ui.small, p.coins() + (p.coins() == 1 ? " coin" : " coins"), x, y,
                CombatColours.rgba(COIN_RGB, 1f));
    }

    static final int COIN_RGB = 0xF0C030;

    /** The stealth meter's colour when she is hidden: a dusk green. */
    static final int STEALTH_HIDDEN = 0x9FD8A0;

    /** What the cards she played are doing for her: a plate each, down the left under the dice ({@link com.larsons.game.ui.BuffPlates}). */
    public void drawBuffs(Ui ui, Player p, float x, float y) {
        com.larsons.game.ui.BuffPlates.draw(ui, p.buffs(), x, y, System.nanoTime() / 1e9);
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static void bar(Ui ui, float x, float y, float w, float h, double fill, int rgb, int light, String label,
                            boolean warn) {
        float k = (float) Math.max(0, Math.min(1, fill));
        ui.bar(x - 3, y - 3, w + 6, h + 6, k, CombatColours.rgba(rgb, 0.95f));
        if (warn) ui.outline(x - 4, y - 4, w + 8, h + 8, 1, CombatColours.rgba(0xFFFFFF, 0.85f));
        ui.textShadowed(ui.small, label, x, y - ui.small.lineHeight() - 3, CombatColours.rgba(light, 1f));
    }

    /** One frame of a sheet over a {@code size}-point square at {@code (x, y)} (its cells are cropped). */
    private static void image(Batch batch, SheetTexture sheet, int frame, int[] palette, float x, float y,
                              float size, float alpha) {
        double[] reg = sheet.region(false);
        if (reg[2] <= reg[0] || reg[3] <= reg[1]) return;
        float[] uv = sheet.uv(frame, false);
        x = Math.round(x);
        y = Math.round(y);                          // on whole points, so the pixels stay square
        batch.rect(sheet.texture(), x + (float) reg[0] * size, y + (float) reg[1] * size,
                (float) (reg[2] - reg[0]) * size, (float) (reg[3] - reg[1]) * size,
                uv[0], uv[1], uv[2], uv[3], 1, 1, 1, alpha, batch.palette(sheet.indexed() ? palette : null));
    }

    /**
     * Where the top of something {@code height} tall standing at {@code
     * ground} is on screen, over its sprite. The sprites are cut-outs
     * standing straight up the screen, rendered from 0°, 45° or straight
     * down, so a thing's height comes to {@code height × cos} of the angle
     * its set was rendered from, up the card from its feet (and not to the
     * true point over them, which leans outward toward the screen's edges).
     * Null when it is behind the camera.
     */
    static float[] over(Mat4 vp, OrbitCamera camera, Vec3 ground, double height, Ui ui) {
        Vec3 pivot = ground.add(0, SpriteProfile.defaults().pivotHeight(), 0);
        double a = Math.toRadians(SpriteView.of(camera.eye(), pivot, 0, camera.yaw()).elevation().renderAngle());
        return screen(vp, camera, ground.add(camera.up().scale(height * Math.cos(a) + 0.2 * Math.sin(a))), ui);
    }

    /** Where {@code p} is on screen, in points, or null when it is behind the camera. */
    static float[] screen(Mat4 vp, OrbitCamera camera, Vec3 p, Ui ui) {
        if (p.sub(camera.eye()).dot(camera.forward()) < 0.05) return null;
        Vec3 n = vp.transformPoint(p);
        return new float[]{(float) ((n.x() + 1) / 2 * ui.width()), (float) ((1 - n.y()) / 2 * ui.height())};
    }
}
