package com.larsons.game.scene;

import com.larsons.game.audio.SoundKeys;
import com.larsons.game.audio.Sounds;
import com.larsons.game.core.Game;
import com.larsons.game.input.Action;
import com.larsons.game.input.Input;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.ui.Theme;
import com.larsons.game.ui.Ui;
import com.larsons.game.world.Biome;
import com.larsons.game.world.Goods;
import com.larsons.game.world.Item;
import com.larsons.game.world.Player;
import com.larsons.game.world.Rarity;
import com.larsons.game.world.Supplies;
import com.larsons.game.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fishing: with a rod in her hand and water before her, a click casts - the
 * float flies out and settles; after a while it dips ("!") and a click in
 * time hooks what took it; then the reel: the fish darts up and down a
 * gauge, and holding the attack key raises her catch along it, letting go
 * lets it sink - keep the fish inside it until the line is in.
 *
 * <p>What bites depends on the water's biome and on the rod ({@link
 * Goods#RODS}): a better rod means a longer moment to strike, a wider catch
 * on the gauge, and rarer fish - and now and then something that is not a
 * fish at all.
 */
final class Fishing {

    enum Phase { IDLE, CAST, WAIT, BITE, REEL }

    /** How far out she casts at most, how long the float flies, how far she may step before the line comes in. */
    static final double REACH = 8, FLIGHT = 0.7, LEASH = 1.6;
    private static final double GAUGE = 220;

    private final Game game;
    private final World world;
    private final Random random = new Random();

    private Phase phase = Phase.IDLE;
    private double time, wait, window, nibble;
    private Vec3 from = Vec3.ZERO, to = Vec3.ZERO, stood = Vec3.ZERO;
    private Goods.Good rod, biting;
    private Item prize;
    // the reel
    private double fish, fishTo, fishClock, zone, zoneSpeed, progress, quick;

    Fishing(Game game, World world) {
        this.game = game;
        this.world = world;
    }

    Phase phase() { return phase; }

    /** The rod in her hand, or null. */
    Goods.Good rodHeld() {
        Goods.Good g = Goods.of(world.player().inventory().held());
        return g != null && g.kind() == Goods.Kind.ROD ? g : null;
    }

    private int tier(Goods.Good r) {
        return Math.max(0, Goods.RODS.indexOf(r));
    }

    /** Where a cast from where she stands would land: the first water before her, or null. */
    Vec3 waterAhead() {
        Player p = world.player();
        Vec3 at = p.ground(), dir = SpriteView.headingVector(p.heading());
        for (double d = 1.4; d <= REACH; d += 0.4) {
            double x = at.x() + dir.x() * d, z = at.z() + dir.z() * d;
            double ground = world.terrain().height(x, z);
            double surface = world.water().surface(x, z, ground);
            if (!Double.isNaN(surface) && surface - ground > 0.12) {
                // a little further out than the bank
                double x2 = x + dir.x() * 1.2, z2 = z + dir.z() * 1.2, g2 = world.terrain().height(x2, z2);
                double s2 = world.water().surface(x2, z2, g2);
                return Double.isNaN(s2) ? new Vec3(x, surface, z) : new Vec3(x2, s2, z2);
            }
        }
        return null;
    }

    private void stop(String said, float[] colour) {
        phase = Phase.IDLE;
        world.setFishingLine(null);
        if (said != null) game.toast(said, colour);
    }

    /** A frame, the story's panels down: her clicks, the float, the fish. */
    void update(double dt, Input in) {
        Goods.Good held = rodHeld();
        Player p = world.player();
        if (phase != Phase.IDLE && (held != rod || p.ground().horizontalDistance(stood) > LEASH
                || p.mounted() || p.swimming())) {
            stop(phase == Phase.REEL ? "The line went slack" : null, Theme.HINT);
        }
        if (held == null) return;
        boolean click = in.pressed(Action.ATTACK);
        time += dt;
        switch (phase) {
            case IDLE -> {
                if (!click) return;
                Vec3 water = waterAhead();
                if (water == null) {
                    Sounds.play(SoundKeys.ui("error"), 0.6);
                    game.toast("Face some water to cast into (within " + (int) REACH + " m)", Theme.HINT);
                    return;
                }
                rod = held;
                stood = p.ground();
                from = hand();
                to = water;
                time = 0;
                phase = Phase.CAST;
                world.combat().sound("fishing/cast", p.ground());
            }
            case CAST -> {
                if (time >= FLIGHT) {
                    phase = Phase.WAIT;
                    time = 0;
                    wait = (3.5 + random.nextDouble() * 6) / (1 + 0.25 * tier(rod));
                    nibble = 1 + random.nextDouble() * 2;
                    world.combat().sound("fishing/plop", to);
                }
            }
            case WAIT -> {
                if (click) {
                    stop("Reeled in - nothing on the hook", Theme.HINT);
                    world.combat().sound("fishing/reel", p.ground());
                    return;
                }
                if (time >= wait) {
                    phase = Phase.BITE;
                    time = 0;
                    window = 0.75 + 0.15 * tier(rod);
                    choose();
                    world.combat().say("!", 0xFFD27A, to, 0.9, false);
                    world.combat().sound("fishing/bite", to);
                }
            }
            case BITE -> {
                if (click) {
                    phase = Phase.REEL;
                    time = 0;
                    fish = fishTo = 0.5;
                    zone = 0.35;
                    zoneSpeed = 0;
                    progress = 0.3;
                    fishClock = 0;
                    quick = switch (prize.rarity()) {
                        case COMMON -> 0.55;
                        case UNCOMMON -> 0.8;
                        case RARE -> 1.1;
                        case EPIC -> 1.45;
                        default -> 1.8;
                    };
                    world.combat().sound("fishing/hooked", to);
                } else if (time >= window) {
                    phase = Phase.WAIT;
                    time = 0;
                    wait = 2 + random.nextDouble() * 5;
                    world.combat().say("It got away", 0xC8D2DC, to, 0.9, true);
                }
            }
            case REEL -> reel(dt, in.down(Action.ATTACK));
        }
        if (phase != Phase.IDLE) world.setFishingLine(line());
    }

    /** What has taken the bait: a fish of this water - or, now and then, something else. */
    private void choose() {
        int t = tier(rod);
        if (random.nextDouble() < 0.07 + 0.01 * t) {
            double r = random.nextDouble();
            biting = null;
            prize = r < 0.35 ? Item.of(Goods.EMPTY_BOTTLE.def()) : r < 0.6 ? Item.of(Goods.TWINE.def())
                    : r < 0.8 ? Item.of(Supplies.COINS) : r < 0.92 ? Item.of(Goods.RUSTY_KEY.def())
                    : r < 0.97 ? Item.of(Goods.PARCHMENT.def()) : Item.of(Goods.SCROLL_RETURN.def());
            return;
        }
        Biome b = world.biome(to.x(), to.z());
        List<Goods.Good> pool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        Object[][] table = switch (b == null ? Biome.PLAINS : b) {
            case MARSH, JUNGLE -> new Object[][]{{Goods.MINNOW, 30.0}, {Goods.PERCH, 40.0}, {Goods.PIKE, 14.0},
                    {Goods.GOLDSCALE, 2.0}};
            case OCEAN -> new Object[][]{{Goods.SEA_BASS, 50.0}, {Goods.MINNOW, 22.0}, {Goods.EMBERFIN, 6.0},
                    {Goods.GOLDSCALE, 2.0}};
            case SAVANNAH, WASTE -> new Object[][]{{Goods.MINNOW, 40.0}, {Goods.PERCH, 20.0}, {Goods.EMBERFIN, 14.0}};
            case CRYSTAL -> new Object[][]{{Goods.MINNOW, 30.0}, {Goods.TROUT, 30.0}, {Goods.GOLDSCALE, 9.0},
                    {Goods.CRYSTAL_EEL, 4.0}};
            default -> new Object[][]{{Goods.MINNOW, 40.0}, {Goods.TROUT, 36.0}, {Goods.PERCH, 10.0},
                    {Goods.PIKE, 8.0}, {Goods.GOLDSCALE, 1.5}};
        };
        double total = 0;
        for (Object[] row : table) {
            Goods.Good g = (Goods.Good) row[0];
            double w = (Double) row[1];
            // a better rod: fewer minnows, more of what is worth having
            w *= g.rarity().ordinal() >= Rarity.RARE.ordinal() ? 1 + t : g.rarity() == Rarity.COMMON
                    ? 1 / (1 + 0.3 * t) : 1 + 0.3 * t;
            pool.add(g);
            weights.add(w);
            total += w;
        }
        double roll = random.nextDouble() * total;
        biting = pool.get(pool.size() - 1);
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0) {
                biting = pool.get(i);
                break;
            }
        }
        prize = Item.of(biting.def());
    }

    /** How much of the gauge her catch takes up with this rod. */
    private double zoneSize() {
        return 0.26 + 0.045 * tier(rod);
    }

    private void reel(double dt, boolean holding) {
        // the fish: off to somewhere else on the gauge every little while, quicker the rarer it is
        fishClock -= dt;
        if (fishClock <= 0) {
            fishClock = (0.35 + random.nextDouble() * 0.9) / quick;
            fishTo = Math.max(0.04, Math.min(0.96, fish + (random.nextDouble() - 0.5) * (0.5 + 0.3 * quick)));
        }
        fish += (fishTo - fish) * Math.min(1, dt * 3.2 * quick);
        // her catch: up while she holds, sinking when she lets go
        double size = zoneSize();
        zoneSpeed += (holding ? 3.0 : -2.4) * dt;
        zoneSpeed *= Math.exp(-dt * 2.2);
        zone += zoneSpeed * dt;
        if (zone < 0) {
            zone = 0;
            zoneSpeed = Math.max(0, -zoneSpeed * 0.3);
        } else if (zone > 1 - size) {
            zone = 1 - size;
            zoneSpeed = Math.min(0, -zoneSpeed * 0.3);
        }
        boolean in = fish >= zone && fish <= zone + size;
        progress += dt * (in ? 0.34 : -0.24);
        if (progress >= 1) landed();
        else if (progress <= 0) {
            stop("It got away", Theme.WARNING);
            world.combat().sound("fishing/snap", world.player().ground());
        }
    }

    private void landed() {
        Player p = world.player();
        phase = Phase.IDLE;
        world.setFishingLine(null);
        if (prize.def() == Supplies.COINS) {
            int n = 5 + random.nextInt(16);
            p.addCoins(n);
            game.toast("A purse on the hook: " + n + " coins", Theme.ACCENT);
        } else {
            if (p.inventory().put(prize, 1, true) > 0) world.spawn(prize, p.ground());
            game.toast("Caught: " + prize.fullName(), prize.rarity().colour(1f));
            world.earn(4 + 6 * prize.rarity().ordinal());
        }
        world.combat().sound("fishing/catch", p.ground());
        world.combat().say(prize.fullName(), prize.rarity().rgb(), p.feet(), 2.2, false);
    }

    private Vec3 hand() {
        Player p = world.player();
        Vec3 dir = SpriteView.headingVector(p.heading());
        return p.ground().add(dir.scale(0.3)).add(0, 1.15 * p.size(), 0);
    }

    /** The rod, the line and the float, for the world to draw. */
    private World.FishingLine line() {
        Player p = world.player();
        Vec3 dir = SpriteView.headingVector(p.heading()), hand = hand();
        boolean taut = phase == Phase.REEL;
        Vec3 tip = hand.add(dir.scale(taut ? 1.25 : 1.05)).add(0, taut ? 0.95 : 1.25, 0);
        Vec3 bob;
        double dip = 0;
        if (phase == Phase.CAST) {
            double u = Math.min(1, time / FLIGHT);
            bob = tip.add(to.sub(tip).scale(u)).add(0, Math.sin(u * Math.PI) * 2.2, 0);
        } else {
            bob = to;
            if (phase == Phase.WAIT) {
                // the float rides the water; a nibble now and then
                dip = 0.02 * Math.sin(time * 2.4);
                double since = time % nibble;
                if (time > 1 && since < 0.25) dip -= 0.05 * Math.sin(since / 0.25 * Math.PI);
            } else if (phase == Phase.BITE) {
                dip = -0.16;
            } else if (phase == Phase.REEL) {
                // pulled in as the line comes, and about as the fish fights
                double pull = Math.max(0, Math.min(1, (progress - 0.3) / 0.7)) * 0.6;
                Vec3 back = tip.sub(to);
                bob = to.add(new Vec3(back.x() * pull, 0, back.z() * pull))
                        .add(Math.sin(time * 9) * 0.12 * (fish - 0.5) * 2, 0, Math.cos(time * 7) * 0.1);
                dip = -0.1 + 0.05 * Math.sin(time * 14);
            }
        }
        return new World.FishingLine(hand, tip, bob.add(0, dip, 0), taut, rod.def().food().rgb(),
                phase == Phase.BITE || phase == Phase.REEL ? 1 : phase == Phase.WAIT && time < 0.5 ? 1 - time * 2 : 0);
    }

    /** The reel's gauge, and what a click does now. */
    void draw(Ui ui) {
        if (rodHeld() == null) return;
        if (phase == Phase.REEL) {
            float h = (float) GAUGE, w = 34, x = ui.width() / 2f + 150, y = ui.height() / 2f - h / 2;
            ui.plate(x - 10, y - 34, w + 74, h + 60, Theme.BRASS);
            ui.text(ui.small, "Reel", x - 2, y - 28, Theme.TITLE);
            ui.rect(x, y, w, h, Theme.withAlpha(Theme.rgb(30, 60, 90, 1f), 0.95f));
            ui.outline(x - 1, y - 1, w + 2, h + 2, 1, Theme.BRASS_LIGHT);
            float zh = (float) (zoneSize() * h), zy = y + h - (float) (zone * h) - zh;
            boolean in = fish >= zone && fish <= zone + zoneSize();
            ui.rect(x + 2, zy, w - 4, zh, Theme.withAlpha(in ? Theme.rgb(110, 220, 120, 1f)
                    : Theme.rgb(200, 200, 120, 1f), 0.75f));
            float fy = y + h - (float) (fish * h);
            float[] uv = com.larsons.game.ui.CardArt.iconUv("fish");
            float[] c = prize == null ? new float[]{1, 1, 1, 1} : prize.rarity().colour(1f);
            if (uv != null) ui.image(com.larsons.game.ui.CardArt.iconTexture(), x + 5, fy - 12, 24, 24, uv[0], uv[1],
                    uv[2], uv[3], c);
            else ui.disc(x + w / 2, fy, 6, c);
            // how much of the line is in
            ui.rect(x + w + 12, y, 12, h, Theme.withAlpha(Theme.SOOT, 0.8f));
            float ph = (float) (Math.max(0, Math.min(1, progress)) * h);
            ui.rect(x + w + 12, y + h - ph, 12, ph, progress > 0.66 ? Theme.OK : progress > 0.3 ? Theme.ACCENT
                    : Theme.WARNING);
            ui.outline(x + w + 11, y - 1, 14, h + 2, 1, Theme.BRASS_DARK);
            String hint = "Hold " + ui.input().label(Action.ATTACK) + ": up";
            ui.text(ui.tiny, hint, x - 4, y + h + 6, Theme.HINT);
            return;
        }
        String text = switch (phase) {
            case IDLE -> waterAhead() != null ? "Cast the line" : null;
            case WAIT -> "Wait for a bite…  (click: reel in)";
            case BITE -> "A bite! Strike!";
            default -> null;
        };
        if (text == null) return;
        String key = ui.input().label(Action.ATTACK);
        float tw = ui.body.width(key + "   " + text) + 28, x = (ui.width() - tw) / 2, y = ui.height() - 176;
        ui.plate(x, y, tw, 30, phase == Phase.BITE ? Theme.ACCENT : Theme.BRASS);
        ui.text(ui.body, key, x + 14, y + (30 - ui.body.lineHeight()) / 2, Theme.TITLE);
        ui.text(ui.body, text, x + 14 + ui.body.width(key + "   "), y + (30 - ui.body.lineHeight()) / 2,
                phase == Phase.BITE ? Theme.ACCENT : Theme.ITEM);
    }
}
