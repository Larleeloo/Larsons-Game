package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.SceneryProfile;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;

import java.util.Locale;

/**
 * Felling a tree with an axe: what it takes, how it falls, what it leaves.
 *
 * <p>She walks up to a standing tree with an axe in her hands and swings at
 * it (attack, or E): each blow is the felling chop ({@code axe_chop}), the
 * bit striking the trunk straight ahead of her at about hip height. A tree
 * takes so many blows ({@link Rule#chops}: its kind's, from its profile's
 * {@code fell}, or what the level set for that one tree) and a better axe
 * does more with each ({@link #power}: a Basic axe one blow's worth, a
 * Mythic two and a half). The last one fells it: it creaks, leans away from
 * her and comes down, faster and faster ({@link #fallAngle}), bounces once
 * on the ground and lies there a moment; then it breaks up in a burst of
 * leaves and bark, and what it leaves lies along where it fell - timber,
 * and whatever else its loot pool gives ({@link Rule#drops}: its kind's, or
 * the level's own for that tree, set in Creative Mode). Its stump stands
 * where it stood, in her way as the trunk was, until it grows back ({@link
 * Rule#regrow}) - which it does only when she is not close by.
 *
 * <p>The tree is drawn as it always is - two cards crossed through its axis -
 * and falls as those two cards, cut at {@link #CUT} metres above the ground
 * and turned together about the cut; it falls along one of its cards ({@link
 * #fallHeading}: the one leading most nearly away from her), so as it lies
 * that card shows the tree on its side and the other lies flat on the ground.
 * Pure data and arithmetic: {@link Fellings} keeps a world's felled trees,
 * {@link SceneryRenderer} draws them.
 */
public final class Felling {

    private Felling() {}

    /** Where the trunk is cut, metres above the ground (times the tree's size): the stump's height. */
    public static final double CUT = 0.34;
    /** How long a tree of {@link #TALL} metres takes from the last blow to the ground, seconds. */
    public static final double FALL_SECONDS = 1.7;
    public static final double TALL = 5.0;
    /** How long it lies on the ground before it breaks up into what it leaves, seconds. */
    public static final double LIE_SECONDS = 1.3;
    /** How long the bounce on landing lasts, and how high it bounces, radians. */
    public static final double BOUNCE_SECONDS = 0.45, BOUNCE = Math.toRadians(5);

    /**
     * Where the axe's bit strikes in the felling chop, from where she stands:
     * this far to her left and ahead (the 3D-Modeling feminine_model_chop's
     * measured bite, frame 12 of 28). She is stood so the trunk's bark meets
     * it ({@link #stand}).
     */
    public static final double BITE_LEFT = 0.25, BITE_AHEAD = 1.24;
    /** How far into the trunk the bit sinks, as a share of its radius. */
    public static final double BITE_DEPTH = 0.45;
    /** The bite's height, metres: where the chips fly from. */
    public static final double BITE_HEIGHT = 0.8;
    /** When in the chop the bit strikes: frame 12 of its 28. */
    public static final double BITE_AT = 12.0 / 28.0;

    /** How far from the bark she can be and still swing at it, metres (from where she stands to the bark). */
    public static final double REACH = 1.9;
    /** How near a foe may be before a swing at a tree is a swing at the foe, metres. */
    public static final double FOE_NEAR = 6.0;

    /** A blow's stamina: this share of a light swing's (combat.json's stamina.swing). */
    public static final double STAMINA_SHARE = 0.6;
    /** What a blow made out of breath does, of a full one. */
    public static final double TIRED = 0.7;

    /** Felling with no fell block in a tree's profile. */
    public static final SceneryProfile.Fell DEFAULT = new SceneryProfile.Fell(null, 5, "timber x2-4", 600);

    /**
     * One tree's own settings, from its level object (Creative Mode): whether
     * it can be felled at all, the blows it takes (0: its kind's), what it
     * leaves ("": its kind's), and the minutes it takes to grow back (below
     * 0: its kind's; 0: never).
     */
    public record Own(boolean fells, int chops, String drops, double regrowMinutes) {
        /** As its kind is. */
        public static final Own KIND = new Own(true, 0, "", -1);

        public Own {
            drops = drops == null ? "" : drops.strip();
        }
    }

    /**
     * What felling one tree takes and gives, its own settings over its
     * kind's: the stump it leaves (a scenery sprite, or null: the foot of its
     * own picture), the blows, the loot pool, and the seconds until it grows
     * back (0: never).
     */
    public record Rule(String stump, int chops, Drops drops, double regrow) {}

    /** The rule for felling {@code p}, or null when it cannot be felled (not a tree, or set not to be). */
    public static Rule rule(Scenery.Placed p) {
        if (p == null) return null;
        SceneryProfile prof = p.profile();
        if (prof.kind() != SceneryProfile.Kind.TREE) return null;
        Own own = p.felling() == null ? Own.KIND : p.felling();
        if (!own.fells()) return null;
        SceneryProfile.Fell kind = prof.fell() != null ? prof.fell() : DEFAULT;
        int chops = own.chops() > 0 ? own.chops() : kind.chops();
        Drops drops = own.drops().isEmpty() ? Drops.parse(kind.drops()) : Drops.parse(own.drops());
        double regrow = own.regrowMinutes() >= 0 ? own.regrowMinutes() * 60 : kind.regrow();
        return new Rule(kind.stump(), Math.max(1, chops), drops, Math.max(0, regrow));
    }

    /** Whether {@code item} fells trees: an axe (the battle axe, at any tier, or one forged as an axe). */
    public static boolean axe(Item item) {
        return item != null && item.def().stance() == Stance.AXE;
    }

    /**
     * How much of a blow one swing of {@code axe} is: a Common (Basic) axe
     * one, then a quarter more for every rarity up - an Uncommon (Sharper)
     * 1.25, an Epic (Crystal) 1.75, a Legendary (Perfect) 2, a Mythic 2.5.
     */
    public static double power(Item axe) {
        if (axe == null) return 1;
        return switch (axe.rarity()) {
            case COMMON -> 1;
            case UNCOMMON -> 1.25;
            case RARE -> 1.5;
            case EPIC -> 1.75;
            case LEGENDARY -> 2;
            case MYTHIC -> 2.5;
        };
    }

    /**
     * A blow's stamina with {@code axe}: {@link #STAMINA_SHARE} of a light
     * swing's ({@code tuning}'s, combat.json's {@code stamina.swing}), times
     * what the Forge set it to use ({@link Forged#staminaUse}).
     */
    public static double staminaCost(com.larsons.game.combat.CombatTuning tuning, Item axe) {
        Forged f = axe == null ? null : axe.forged();
        return STAMINA_SHARE * (tuning == null ? 6 : tuning.staminaSwing) * (f == null ? 1 : f.staminaUse());
    }

    /** How many blows of {@code power} fell a tree of {@code chops}. */
    public static int blows(int chops, double power) {
        return (int) Math.ceil(chops / Math.max(0.1, power) - 1e-9);
    }

    /** The tree's height, metres: its frame's, times its size. */
    public static double height(Scenery.Placed p) {
        return p.profile().frameWorldHeight() * p.scale();
    }

    /** Where it is cut, metres above its foot. */
    public static double cut(Scenery.Placed p) {
        return CUT * Math.max(0.6, Math.min(2.5, p.scale() * Math.max(1, p.profile().radius() / 0.3)));
    }

    /** How long {@code p} takes to come down, seconds: a tall tree falls slower. */
    public static double fallSeconds(Scenery.Placed p) {
        return FALL_SECONDS * Math.sqrt(Math.max(0.3, height(p) / TALL));
    }

    /**
     * The way {@code p} falls, felled by someone standing at {@code from}: a
     * heading ({@link SpriteView#headingVector}) along one of its two cards -
     * the one of the four ways along them leading most nearly away from her.
     */
    public static double fallHeading(Scenery.Placed p, Vec3 from) {
        double ax = p.x() - from.x(), az = p.z() - from.z();
        if (Math.hypot(ax, az) < 1e-6) {
            ax = 1;
            az = 0;
        }
        double c = Math.cos(p.yaw()), s = Math.sin(p.yaw());
        // the cards run along (c, s) and (-s, c) (SceneryRenderer's cross)
        double[][] ways = {{c, s}, {-c, -s}, {-s, c}, {s, -c}};
        double best = -2, bx = 1, bz = 0;
        double n = Math.hypot(ax, az);
        for (double[] w : ways) {
            double d = (w[0] * ax + w[1] * az) / n;
            if (d > best) {
                best = d;
                bx = w[0];
                bz = w[1];
            }
        }
        return SpriteView.headingOf(bx, bz);
    }

    /**
     * How far over the tree has gone {@code t} seconds after the last blow,
     * radians from upright: slow at first - the creak, the lean - then faster
     * and faster to {@code land} at {@code seconds}, a bounce, and lying.
     */
    public static double fallAngle(double t, double seconds, double land) {
        if (t <= 0) return 0;
        if (t < seconds) {
            double u = t / seconds;
            return land * Math.pow(u, 2.4);
        }
        double b = t - seconds;
        if (b >= BOUNCE_SECONDS) return land;
        double k = b / BOUNCE_SECONDS;
        return land - BOUNCE * Math.abs(Math.sin(k * Math.PI)) * (1 - k);
    }

    /**
     * How far over {@code p} comes to lie when it falls along {@code
     * heading} on {@code terrain}: level ground a quarter turn; uphill less,
     * downhill a little more - its crown on the ground.
     */
    public static double landAngle(Scenery.Placed p, double heading, Terrain terrain) {
        if (terrain == null) return Math.PI / 2;
        Vec3 d = SpriteView.headingVector(heading);
        double along = Math.max(1, (height(p) - cut(p)) * 0.6);
        double ground = terrain.height(p.x() + d.x() * along, p.z() + d.z() * along);
        double rise = ground + 0.2 - (p.y() + cut(p));
        double a = Math.PI / 2 - Math.atan2(rise, along);
        return Math.max(Math.toRadians(55), Math.min(Math.toRadians(112), a));
    }

    /**
     * Where she stands to fell {@code p}, and which way she faces ({@code
     * [x, z, heading]}): turned towards it, as far back from it that the axe's
     * bit ({@link #BITE_LEFT}, {@link #BITE_AHEAD}) meets its bark.
     */
    public static double[] stand(Scenery.Placed p, Vec3 from) {
        double r = p.radius();
        double ahead = BITE_AHEAD + r * (1 - BITE_DEPTH);
        double toward = SpriteView.headingOf(p.x() - from.x(), p.z() - from.z());
        // the trunk BITE_LEFT to her left of straight ahead: she faces that much to its right
        double turn = Math.atan2(BITE_LEFT, ahead);
        double h = toward - turn;
        Vec3 f = SpriteView.headingVector(h);
        Vec3 left = new Vec3(f.z(), 0, -f.x());
        double x = p.x() - f.x() * ahead - left.x() * BITE_LEFT;
        double z = p.z() - f.z() * ahead - left.z() * BITE_LEFT;
        return new double[]{x, z, h};
    }

    /** Where the bit strikes {@code p}'s bark, struck by someone standing at {@code from}. */
    public static Vec3 bite(Scenery.Placed p, Vec3 from) {
        double dx = from.x() - p.x(), dz = from.z() - p.z(), n = Math.max(1e-6, Math.hypot(dx, dz));
        double r = p.radius();
        return new Vec3(p.x() + dx / n * r, p.y() + BITE_HEIGHT * Math.min(1.5, Math.max(0.6, p.scale())),
                p.z() + dz / n * r);
    }

    /** What a tree's kind is called in a sentence: "oak tree", "pine", "great oak". */
    public static String called(Scenery.Placed p) {
        String n = p.sprite().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (n.endsWith(" tree")) return n;
        return n;
    }
}
