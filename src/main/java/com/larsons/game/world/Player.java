package com.larsons.game.world;

import com.larsons.game.ambient.AnimalKind;
import com.larsons.game.math.Pchip;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.ToDoubleFunction;

/**
 * The player character: a position on the ground, a heading, a little jump
 * physics, and the animation state machine that picks which sprite set plays.
 *
 * <p>What plays depends on her {@link Stance} - what she holds: the
 * wardrobe's sword and shield, or a battle axe, a bow or a crossbow she has
 * picked up, or nothing at all ({@link #handsEmpty}) - and on whether she is
 * crouched (C toggles it):
 *
 * <pre>
 *   airborne or mid-jump          → the stance's jump
 *   an action under way           → it (attack, heavy, spin, parry, bash,
 *                                   the bow's draw and loose, an emote, a pick-up,
 *                                   the dash, the roll)
 *   blocking (held)               → the stance's block / the shield up
 *   moving                        → the stance's walk / run (Shift) / sprint (Ctrl),
 *                                   or its crouch walk (fast with Shift)
 *   otherwise                     → the stance's idle, or its crouch
 * </pre>
 *
 * <p>Actions play once, in place: she does not move until they end, except
 * that moving cuts an emote short. The bow's attack is held: the draw plays
 * and holds at full draw while the attack is held, and letting go looses the
 * arrow - or, before the draw is most of the way back, lets the string down.
 * She moves at the speed her locomotion state's feet travel at
 * ({@link AnimState#speed()}). A heavy attack can only be made once every
 * {@link #HEAVY_COOLDOWN} seconds ({@link #heavyWait}).
 *
 * <p>The dodge key starts a <b>dash</b> at once - a low burst of ~3.5 m the
 * way she is moving (or facing), whatever she holds. Still held when its
 * push-off is over ({@link AnimState#DODGE_SHARED} frames, a sixth of a
 * second), it turns into the <b>combat roll</b> there: a dive into a shoulder
 * roll and up again, ~4.7 m, during which nothing can hurt her
 * ({@link #invulnerable}) and she rolls through the training dummies
 * ({@link #passesThrough}). Either can start from standing, moving, crouched
 * or behind the shield, not in the air or in the middle of another action
 * (an emote is cut short); the next one can start {@link #DODGE_COOLDOWN}
 * seconds after the last one ended.
 *
 * <p><b>Hills.</b> She walks on the world's {@link Terrain}: uphill slower,
 * downhill a little faster, and not at all up ground steeper than {@link
 * Terrain#STEEP}. Off the edge of a ledge she falls (the stance's jump,
 * held in the air); landing from more than {@link #SAFE_FALL} m hurts, {@link
 * #FALL_DAMAGE} a metre beyond it - unless the dodge key went down in the air,
 * when she lands in the combat roll and nothing hurts. Walking into a
 * {@link Terrain.Wall} she <b>climbs</b> it ({@link AnimState#CLIMB}, her
 * hands empty): into the wall is up, away from it down, along it sideways, at
 * {@link #CLIMB_SPEED}; with her hands at its lip she <b>steps up</b> over it
 * ({@link AnimState#LEDGE_UP}). Walking off the top of a wall she can climb,
 * she <b>steps down</b> over its lip onto it instead ({@link
 * AnimState#LEDGE_DOWN}, turned to face it) and climbs on down. Both carry her
 * along {@link #LEDGE_PATH}. Climbing spends her <b>stamina</b> ({@link
 * #stamina()}), which fills back up on the ground; when it runs out - or the
 * jump, crouch or dodge key lets go - she drops; too tired to climb down, she
 * stops at the lip.
 *
 * <p>She has <b>health</b> ({@link #health()}): a fall, or a flier's swoop,
 * takes some; it fills back up a while after the last hurt ({@link World}
 * puts her back at the start when it is gone).
 *
 * <p>The ranged attacks have <b>aimed-up</b> variants (the bow's draw and loose,
 * the crossbow's shot, the staff's and the wand's casts, standing): she
 * plays one when what she aims at is well above her ({@link #setAimUp}).
 *
 * <p>She <b>rides</b> a saddled horse ({@link World#mount}): a hop up into the
 * saddle ({@link #MOUNT_TIME}), then she steers it - it turns no faster than
 * {@link #RIDE_TURN}, slowing round a sharp turn - at a walk, a canter (the
 * run key) or a gallop (the sprint key), never off a ledge nor into deep
 * water; and a hop down off its side. Sitting it she plays {@link
 * AnimState#RIDE} (at a stand and a walk) or {@link AnimState#RIDE_RUN},
 * frame for frame with the horse's sheet - its clock is hers ({@link
 * #horseTime}) - and her feet are where its {@link RideTrack} puts the seat
 * in that frame, so she rises and falls with its back.
 *
 * <p>For looking at the art rather than playing, a state can be
 * <em>previewed</em>: it loops in place (the one-shots included) until the
 * player moves or clears it.
 */
public final class Player {

    public static final double WALK_SPEED = AnimState.WALK.speed();
    public static final double RUN_SPEED = AnimState.RUN.speed();
    public static final double SPRINT_SPEED = AnimState.SPRINT.speed();
    public static final double GRAVITY = 15.0;
    public static final double TURN_RATE = 14.0;
    /** When a jump leaves the ground, as a fraction of its animation — after the crouch. */
    public static final double JUMP_LAUNCH = 0.2;
    /** How much of the jump animation is spent in the air. */
    public static final double JUMP_AIRTIME = 0.62;
    /** When a pick-up's hand closes on the item, as a fraction of its animation. */
    public static final double GRAB_AT = 0.5;
    /** How far back the bow must be drawn (a fraction of the draw) for letting go to loose it. */
    public static final double DRAW_READY = 0.7;
    /**
     * The least time from one heavy attack to the next, in seconds, unless
     * the game says otherwise ({@link #setHeavyCooldown}; {@code combat.json}'s
     * {@code cooldowns.heavySeconds}).
     */
    public static final double HEAVY_COOLDOWN = 10;
    /**
     * The least time from the end of one dodge to the start of the next, in
     * seconds, unless the game says otherwise ({@link #setDodgeCooldown};
     * {@code combat.json}'s {@code cooldowns.dodgeSeconds}): dodging is no
     * quicker a way about than sprinting.
     */
    public static final double DODGE_COOLDOWN = 0.35;
    /**
     * How fast the dash carries her (m/s) at its frames (30 a second): the
     * push-off, the glide, the brake on the back foot - 3.5 m in all.
     */
    private static final double[][] DASH_SPEED = {{0, 5}, {1.5, 14}, {6, 13}, {9, 4}, {11, 0}};
    /**
     * The roll's: the dash's up to its push-off's end (the two the same to
     * {@link AnimState#DODGE_SHARED}), then the dive and the roll over the
     * shoulder, slowing as she comes up out of it - 4.7 m in all.
     */
    private static final double[][] ROLL_SPEED = {{0, 5}, {1.5, 14}, {6, 13}, {9, 7}, {15, 3}, {18, 1}, {20, 0}};
    /** The roll's frames (from, to) nothing can hurt her in: from the dive until she lands. */
    private static final double[] ROLL_SAFE = {AnimState.DODGE_SHARED, 16};
    /** Faster than this (m/s), a dodge leaves afterimages behind her. */
    public static final double AFTERIMAGE_SPEED = 6;

    /**
     * The jump attack ({@link AnimState#JUMP_ATTACK}), started in the air with
     * the attack key: the weapon drawn up over her head to its frame {@link
     * #PLUNGE_DIVE} - a little lift as she does - then she drops at {@link
     * #PLUNGE_SPEED} held on frame {@link #PLUNGE_HOLD} until she lands, and the
     * blow strikes the ground all round her on frame {@link #PLUNGE_IMPACT} (a
     * die more than a swing: {@code combat.Move}). Frames of the sheet's 24,
     * the same as {@code feminine_model_jump_attack.py}'s.
     */
    public static final int PLUNGE_DIVE = 5, PLUNGE_HOLD = 8, PLUNGE_IMPACT = 11, PLUNGE_FRAMES = 24;
    /** How fast she drops in the jump attack, m/s, and how high she must be (metres) to start one. */
    public static final double PLUNGE_SPEED = 14, PLUNGE_MIN = 0.2;
    /** A jump attack lands unhurt from up to this high (metres): she drives into it. */
    public static final double PLUNGE_SAFE = 8;

    /**
     * Swimming: water deeper than {@link #SWIM_DEPTH} she swims in (her weapon
     * stowed), treading water ({@link AnimState#SWIM_IDLE}) or swimming the
     * breaststroke on the surface ({@link AnimState#SWIM}); the crouch key
     * dives - {@link #DIVE_STEP} deeper each press - and the jump key comes back
     * up, a step at a time ({@link AnimState#SWIM_UNDER}). Under the water her
     * breath runs down over {@link #MAX_BREATH} seconds, then she drowns {@link
     * #DROWN} health a second until she comes up; at the surface it comes back
     * {@link #BREATH_BACK} times as fast. The run and sprint keys swim faster,
     * for stamina.
     */
    public static final double SWIM_DEPTH = 1.2;
    /**
     * Where the water's surface is on her sprite, metres over its ground:
     * treading water, and swimming on the surface; and the middle of her body
     * swimming - the sheets' {@code WATERLINE} and {@code SWIM_MIDDLE}
     * ({@code feminine_model_swim.py}).
     */
    public static final double TREAD_LINE = 1.32, SURFACE_LINE = 1.02, SWIM_MIDDLE = 0.9;
    /** How fast she swims, m/s: the breaststroke, faster (the run key), and under the water. */
    public static final double SWIM_SPEED = 1.2, SWIM_FAST = 2.1, SWIM_UNDER_SPEED = 1.0;
    /** Stamina a second swimming fast. */
    public static final double SWIM_COST = 7;
    /** How far a press of the dive key takes her down, and how fast she goes up or down, m and m/s. */
    public static final double DIVE_STEP = 1.5, DIVE_SPEED = 1.1;
    /** How far under (her middle below the surface) she is at the least, diving, and above the bottom at the most. */
    public static final double DIVE_MIN = 0.6, OFF_BOTTOM = 0.55;
    /** Her breath, seconds; how much faster it comes back than it ran out; health a second drowning. */
    public static final double MAX_BREATH = 20, BREATH_BACK = 4, DROWN = 6;

    /** Her health and stamina when full. */
    public static final double MAX_HEALTH = 100, MAX_STAMINA = 100;
    /** A fall from higher than this (metres, feet to feet) hurts: {@link #FALL_DAMAGE} for each metre beyond. */
    public static final double SAFE_FALL = 3.0, FALL_DAMAGE = 9.0;
    /** From higher than this, a primed landing roll plays (lower, she just lands). */
    public static final double ROLL_FALL = 1.2;
    /** How long after the last hurt her health starts filling back up, and how fast (a second). */
    public static final double HEALTH_DELAY = 5, HEALTH_REGEN = 4;
    /**
     * How fast she climbs, m/s: what the climb sheets' holds slide down past
     * her at ({@code feminine_model_climb.CLIMB_SPEED}); sideways along a wall
     * a little slower.
     */
    public static final double CLIMB_SPEED = 0.82, CLIMB_SIDEWAYS = 0.6;
    /** Stamina a second spent climbing, and hanging still on a wall. */
    public static final double CLIMB_COST = 9, HANG_COST = 3;
    /** The least stamina she starts a climb with. */
    public static final double CLIMB_MIN = 15;
    /** How long after she last spent stamina it starts filling back up, and how fast (a second). */
    public static final double STAMINA_DELAY = 0.8, STAMINA_REGEN = 25;
    /**
     * How far out from a wall's face she hangs (her middle: from the rock where
     * her hands are), and how near its foot she has to walk to take hold.
     */
    public static final double CLING = 0.3, GRAB_REACH = 0.6;
    /**
     * Stepping up over a wall's lip ({@link AnimState#LEDGE_UP}) and down over
     * it ({@link AnimState#LEDGE_DOWN}, the same played the other way round):
     * where the ground under her middle is at each key frame of the step up -
     * {frame, metres out from the lip, metres up from the top} - from hanging
     * by her hands on the lip to standing behind it; a monotone cubic between
     * the keys ({@link Pchip}). The same as {@code feminine_model_ledge.PATH},
     * which the sheets are posed along.
     */
    public static final double[][] LEDGE_PATH = {{0, 0.30, -1.60}, {4, 0.30, -1.63}, {13, 0.22, -1.08},
            {18, 0.14, -0.93}, {23, 0.06, -0.80}, {27, -0.08, -0.45}, {31, -0.30, -0.06}, {35, -0.35, 0}};
    /** How far below a wall's top her feet are as she hangs from its lip, and how far behind it she stands. */
    public static final double LEDGE_RISE = -LEDGE_PATH[0][2], LEDGE_BEHIND = -LEDGE_PATH[LEDGE_PATH.length - 1][1];
    /** How far through the stance's jump the pose she falls in is. */
    public static final double FALL_POSE = 0.5;
    /** The highest step down she walks down (metres) rather than falling off it. */
    public static final double STEP_DOWN = 0.45;
    /** Ground steeper than this (rise a metre) she cannot stand on: she slides down it, this fast (a second). */
    public static final double SLIDE = Terrain.STEEP * 1.6, SLIDE_SPEED = 4;

    /** How long getting up into the saddle takes, and getting down off it, seconds. */
    public static final double MOUNT_TIME = 0.6, DISMOUNT_TIME = 0.5;
    /** How high over the straight way between she hops, getting on or off, metres. */
    public static final double MOUNT_HOP = 0.45;
    /** The horse's paces under her: a walk, a canter (the run key), a gallop (the sprint key), metres a second. */
    public static final double RIDE_WALK = 1.9, RIDE_CANTER = 6.0, RIDE_GALLOP = 9.5;
    /** How fast she turns the horse, radians a second (a gallop turns wider). */
    public static final double RIDE_TURN = 2.8;
    /** Faster than this the horse plays its run sheet (and she {@link AnimState#RIDE_RUN}), metres a second. */
    public static final double RIDE_RUN_ABOVE = 3.2;

    /** Whether she rides: not, getting up into the saddle, sitting it, getting down. */
    public enum Riding { NO, MOUNTING, RIDING, DISMOUNTING }

    /**
     * The low things about - a table, a bench, a crate, a barrel, a chest -
     * she can stand on, step up onto (no higher than {@link #STEP_UP}), jump
     * onto, and climb up onto from the ground ({@link #CLIMB_ONTO} high at
     * most, about her own height): their tops, and their edges.
     */
    public interface Tops {
        /** The highest top over {@code (x, z)} no higher than {@code below}, or NaN when there is none. */
        double top(double x, double z, double below);

        /**
         * The edge of the nearest of them ahead of {@code (x, z)} going {@code
         * (dx, dz)} (a unit vector), no further than {@code reach}, whose top is
         * {@code lo} to {@code hi} metres over {@code feetY}: {@code {edge x,
         * edge z, top}} - or null.
         */
        double[] ahead(double x, double z, double dx, double dz, double reach, double feetY, double lo, double hi);

        Tops NONE = new Tops() {
            @Override
            public double top(double x, double z, double below) { return Double.NaN; }

            @Override
            public double[] ahead(double x, double z, double dx, double dz, double reach, double feetY, double lo,
                                  double hi) {
                return null;
            }
        };
    }

    /** The highest she steps up onto something low as she walks (a step, a low bench), metres. */
    public static final double STEP_UP = 0.4;
    /** The highest top she climbs up onto from the ground (Space by it), metres over her feet: about her height. */
    public static final double CLIMB_ONTO = 1.8;
    /** How far ahead of her the edge of what she climbs onto can be, from her middle, metres. */
    public static final double CLIMB_REACH = 0.8;

    /** Something that happened to her this frame, for the world to show: a fall's hurt, a roll out of one... */
    public record Event(Kind kind, double amount) {
        public enum Kind {
            /** Landed hard: {@code amount} damage. */
            FALL,
            /** Landed unhurt, from a jump or a drop of {@code amount} metres. */
            LANDED,
            /** Came up for air after {@code amount} seconds under. */
            SURFACED,
            /** Out of the water onto her feet. */
            LEFT_WATER,
            /** Landed a jump attack, from {@code amount} metres up. */
            PLUNGED,
            /** Landed in a roll from a fall that would have hurt ({@code amount}: what it would have). */
            ROLLED,
            /** Stepped up over the top of a wall. */
            CLIMBED,
            /** Stepped down over the top of a wall, onto it. */
            STEPPED_DOWN,
            /** Let go of a wall, or fell off it with no stamina left ({@code amount} 1). */
            LET_GO,
            /** Hurt by something ({@code amount}). */
            HURT,
            /** Her health ran out. */
            KNOCKED_OUT,
            /** Up in the saddle. */
            MOUNTED,
            /** Down off the horse, on her feet. */
            DISMOUNTED
        }
    }

    /**
     * What the controls ask for this frame. The one-frame requests ({@code
     * jump}, {@code attack}, {@code crouch}, {@code heavy}, {@code spin},
     * {@code parry}, {@code bash}, {@code emote}) are true on the frame the key
     * goes down; {@code attackHeld}, {@code block} and {@code dodgeHeld} for as
     * long as it is held.
     *
     * @param crouch toggles crouching
     * @param emote  an emote to play ({@link AnimState#EMOTE_LAUGH} ...), or null
     * @param dodge  the dodge key went down: a dash - a roll if it is still
     *               held ({@code dodgeHeld}) once the dash's push-off is over
     */
    public record Intent(double moveX, double moveZ, boolean run, boolean sprint,
                         boolean jump, boolean attack, boolean attackHeld, boolean crouch,
                         boolean heavy, boolean spin, boolean parry, boolean block, boolean bash,
                         AnimState emote, boolean dodge, boolean dodgeHeld) {
        public static final Intent NONE = new Intent(0, 0, false, false, false, false);

        /** Moving, running, jumping and attacking only. */
        public Intent(double moveX, double moveZ, boolean run, boolean sprint, boolean jump, boolean attack) {
            this(moveX, moveZ, run, sprint, jump, attack, attack, false, false, false, false, false, false, null);
        }

        /** Everything but the dodge. */
        public Intent(double moveX, double moveZ, boolean run, boolean sprint,
                      boolean jump, boolean attack, boolean attackHeld, boolean crouch,
                      boolean heavy, boolean spin, boolean parry, boolean block, boolean bash,
                      AnimState emote) {
            this(moveX, moveZ, run, sprint, jump, attack, attackHeld, crouch, heavy, spin, parry, block, bash, emote,
                    false, false);
        }

        /** This, with the dodge key gone down ({@code dodge}) and held ({@code held}) or not. */
        public Intent withDodge(boolean dodge, boolean held) {
            return new Intent(moveX, moveZ, run, sprint, jump, attack, attackHeld, crouch, heavy, spin, parry, block,
                    bash, emote, dodge, held);
        }

        boolean moving() {
            return Math.abs(moveX) > 1e-3 || Math.abs(moveZ) > 1e-3;
        }

        /** Whether it asks for anything to happen besides moving. */
        boolean acts() {
            return jump || attack || crouch || heavy || spin || parry || block || bash || emote != null || dodge;
        }
    }

    private Vec3 position = Vec3.ZERO;
    private double height;          // feet above the ground
    private double vy;
    private double heading;
    private double wantHeading;
    private Vec3 velocity = Vec3.ZERO;

    private AnimState state = AnimState.IDLE;
    private double stateTime;
    private AnimState preview;

    private Stance stance = Stance.SWORD;
    private boolean handsEmpty;
    private boolean crouched;
    private boolean jumping, launched;
    /** In the air in a jump attack (until she lands), and in the air loosing a shot. */
    private boolean plunging, aerial;
    /** Swimming, and under the water; how deep her middle is under the surface, and how deep she is going. */
    private boolean swimming, under;
    private double diveDepth, diveTo, breath = MAX_BREATH, drowned;
    /** The jump under way: the stance's own (they all leave the ground on the same frames). */
    private AnimState jumpState = AnimState.JUMP;
    /** The one-shot under way (null: none), and how long it lasts. */
    private AnimState action;
    private double actionDuration = 0.6;
    private boolean drawing;
    private Runnable grab;
    /** How long a heavy attack takes to come back, and how long is left before the next can start. */
    private double heavyCooldown = HEAVY_COOLDOWN, heavyWait;
    /** The same for a dodge (counted from the end of the last). */
    private double dodgeCooldown = DODGE_COOLDOWN, dodgeWait;
    /** The dodge under way: the way it goes, its animation's length over the art's (1: as authored). */
    private double dodgeHeading, dodgeScale = 1;
    /** Whether the dodge key was let go since the dash started (it stays a dash). */
    private boolean dodgeLetGo;

    /** The ground she walks on. */
    private Terrain terrain = Terrain.FLAT;
    /** The tops of the low things she can stand on and climb onto (a table, a crate, a barrel). */
    private Tops tops = Tops.NONE;
    /** Climbing up onto one of them: where she took hold of its edge, which way, its top, from which frame. */
    private boolean mantling;
    private Vec3 mantleEdge = Vec3.ZERO, mantleFrom = Vec3.ZERO;
    private double mantleHeading, mantleTop, mantleF0, mantleTime;
    private WaterMap water = WaterMap.NONE;
    /** The wall she is climbing, or null. */
    private Terrain.Wall wall;
    /** Stepping up or down over its lip: which, how far along it, its lip (out from its foot), how long since it began. */
    private AnimState ledge;
    private double ledgeAlong, ledgeLip, ledgeTime;
    /** Whether she is off the ground (a jump, a ledge), and the highest her feet were since she left it. */
    private boolean airborne;
    private double fallFrom;
    /** The dodge key went down in the air: she lands in a roll. */
    private boolean rollPrimed;
    private double health = MAX_HEALTH, stamina = MAX_STAMINA;
    private double sinceHurt = Double.MAX_VALUE, sinceTired = Double.MAX_VALUE;
    private BooleanSupplier aimUp = () -> false;
    private final List<Event> events = new ArrayList<>();

    // riding: the hop on or off (from, to, how far into it), the pace, the seat under her
    private Riding riding = Riding.NO;
    private double rideTime, rideSpeed, rideLift, rideAhead;
    private Vec3 hopFrom = Vec3.ZERO, hopTo = Vec3.ZERO;
    private double hopFromLift, hopFromAhead, hopFromHeading, hopToHeading;
    private RideTrack rideTrack = RideTrack.standard();
    private String horseSheet = "idle";
    private double horseTime, horseRate = 1;

    private final Wardrobe wardrobe;
    private final LayerStack.Memory memory = new LayerStack.Memory();
    private final Inventory inventory;

    public Player(Wardrobe wardrobe) {
        this(wardrobe, Inventory.DEFAULT_SLOTS);
    }

    /** A player whose inventory has {@code slots} slots, the hotbar's five included. */
    public Player(Wardrobe wardrobe, int slots) {
        this.wardrobe = wardrobe;
        this.inventory = new Inventory(slots);
    }

    /**
     * Advance one frame. {@code durations} says how long each state's
     * animation lasts for this character right now (from its sprite sheets),
     * which is how long a jump, a swing or an emote takes.
     */
    public void update(double dt, Intent in, ToDoubleFunction<AnimState> durations) {
        heavyWait = Math.max(0, heavyWait - dt);
        if (!dodging()) dodgeWait = Math.max(0, dodgeWait - dt);
        if (sinceHurt < Double.MAX_VALUE) sinceHurt += dt;
        if (sinceTired < Double.MAX_VALUE) sinceTired += dt;
        if (sinceHurt >= HEALTH_DELAY && health > 0) health = Math.min(MAX_HEALTH, health + HEALTH_REGEN * dt);
        if (wall == null && sinceTired >= STAMINA_DELAY) stamina = Math.min(MAX_STAMINA, stamina + STAMINA_REGEN * dt);
        if (preview != null && (in.moving() || in.acts())) preview = null;
        if (preview != null) {
            velocity = Vec3.ZERO;
            setState(preview);
            stateTime += dt;
            // One-shots loop too while previewed, with a beat's pause between.
            if (!preview.loops() && stateTime > durations.applyAsDouble(preview) + 0.35) stateTime = 0;
            turnToward(dt);
            return;
        }
        if (riding != Riding.NO) {
            crouched = false;
            if (riding == Riding.RIDING) steer(dt, in);
            else hop(dt, durations);
            return;
        }
        if (ledge != null) {
            overLip(dt, durations);
            return;
        }
        if (wall != null) {
            climb(dt, in);
            return;
        }
        if (mantling) {
            climbOnto(dt, durations);
            return;
        }
        if (swimming) {
            swim(dt, in);
            return;
        }
        breathe(dt);

        boolean grounded = height <= 1e-6 && vy <= 0;
        Stance st = stance();

        // --- what is under way ---------------------------------------------------------
        // moving, or a dodge, cuts an emote short
        if (action != null && (in.moving() || in.dodge()) && isEmote(action)) action = null;
        if (action != null && !drawing && stateTime >= actionDuration) {
            if (dodging()) dodgeWait = dodgeCooldown;
            action = null;
        }
        if (drawing && !in.attackHeld()) {
            // the bow: let go - loosed if drawn far enough, else the string let down
            drawing = false;
            boolean ready = stateTime >= DRAW_READY * actionDuration;
            AnimState drawn = action;
            action = null;
            if (ready) start(drawn == AnimState.BOW_DRAW_UP ? AnimState.BOW_FIRE_UP : st.release(crouched), durations);
        }
        if (action != null && grab != null && stateTime >= GRAB_AT * actionDuration) {
            Runnable g = grab;
            grab = null;
            g.run();
            st = stance();          // (what she picked up may be a weapon of another stance)
        }
        boolean busy = action != null || jumping;

        // --- crouching ---------------------------------------------------------------------
        if (in.crouch() && grounded && !jumping) crouched = !crouched;
        if (in.sprint() && in.moving() && !busy) crouched = false;           // sprinting stands her up

        // --- starting something ----------------------------------------------------------
        boolean blocking = false;
        if (!grounded && in.dodge()) rollPrimed = true;                     // she will land in a roll
        if (!grounded && in.attack() && action == null && height > PLUNGE_MIN && st.attack(false) != null) {
            // in the air: a bow or a crossbow looses a shot down at what is below; anything else, the jump attack
            jumping = false;
            crouched = false;
            if (st == Stance.BOW || st == Stance.CROSSBOW) {
                aerial = true;
                start(st.release(false), durations);
            } else {
                plunging = true;
                vy = Math.max(vy, 1.5);
                start(AnimState.JUMP_ATTACK, durations);
            }
            busy = true;
        }
        if (!busy && grounded) {
            AnimState next = null;
            if (in.dodge() && dodgeWait <= 0) {
                // the way she is moving, else the way she faces; she turns to it at once
                next = AnimState.DASH;
                crouched = false;
                dodgeHeading = in.moving() ? SpriteView.headingOf(in.moveX(), in.moveZ()) : wantHeading;
                heading = wantHeading = dodgeHeading;
                dodgeLetGo = !in.dodgeHeld();
            } else if (in.emote() != null) {
                next = in.emote();
                crouched = false;
            } else if (in.parry()) {
                next = st.parry();
            } else if (in.spin() && st.spin() != null) {
                next = st.spin();
                crouched = false;
            } else if (in.heavy() && st.heavy() != null && heavyWait <= 0) {
                next = st.heavy();
                crouched = false;
                heavyWait = heavyCooldown;
            } else if (st.bash() != null && (in.bash() || in.block() && in.attack())) {
                next = st.bash();
                crouched = false;
            } else if (in.attack() && st.attack(crouched) != null) {
                if (st == Stance.SWORD || st == Stance.BLADE) crouched = false;   // she stands to swing
                next = st.attack(crouched);
                // aimed up at what is above her (standing: the crouched shots have no such variant)
                if (!crouched && next.up() != next && aimUp.getAsBoolean()) next = next.up();
                drawing = st.drawn() && in.attackHeld();
            } else if (in.jump() && st.canJump() && startClimbOnto(in, 0.25)) {
                climbOnto(0, durations);
                return;
            } else if (in.jump() && st.canJump()) {
                crouched = false;
                jumping = true;
                launched = false;
                jumpState = st.jump();
                setState(jumpState);
                actionDuration = durations.applyAsDouble(jumpState);
            } else if (in.block() && st.block() != null) {
                blocking = true;
                crouched = false;
            }
            if (next != null) start(next, durations);
            if (next == AnimState.DASH) dodgeScale = scale(AnimState.DASH);
        }
        busy = action != null || jumping;

        // --- movement, relative to the camera (the caller already rotated it) -----------
        Stance.Pace pace = in.sprint() ? Stance.Pace.SPRINT : in.run() ? Stance.Pace.RUN : Stance.Pace.WALK;
        AnimState moving = st.moving(pace, crouched);
        Vec3 wish = new Vec3(in.moveX(), 0, in.moveZ());
        if (wish.length() > 1) wish = wish.normalize();
        boolean rooted = (busy && !jumping) || blocking;
        if (rooted) wish = Vec3.ZERO;
        // walking into a wall she can climb, she takes hold of it; off the top of one, she steps down onto it
        if (!busy && grounded && !blocking && wish.length() > 0.5 && grabWall(wish)) {
            climb(0, in);
            return;
        }
        if (!busy && grounded && !blocking && wish.length() > 0.5) {
            wish = stepDown(wish);
            if (ledge != null) {
                overLip(0, durations);
                return;
            }
        }
        Vec3 target = wish.scale(moving.speed() * slopeFactor(wish));
        if (dodging()) {
            velocity = SpriteView.headingVector(dodgeHeading).scale(dodgeSpeed(action, stateTime + dt / 2));
        } else {
            velocity = velocity.lerp(target, 1 - Math.exp(-dt * (grounded ? 14 : 3)));
        }
        move(velocity.scale(dt), grounded);
        if (in.moving() && !rooted) wantHeading = SpriteView.headingOf(wish.x(), wish.z());

        // The jump: crouch, then launch with just enough speed to come down
        // as the animation reaches its landing (on level ground; off a ledge
        // she falls on once it is over).
        if (jumping) {
            if (!launched && stateTime >= JUMP_LAUNCH * actionDuration) {
                launched = true;
                vy = GRAVITY * (JUMP_AIRTIME * actionDuration) / 2;
            }
            if (launched && stateTime >= actionDuration) jumping = false;
        }
        fall(dt, durations);
        if (intoDeepWater()) {
            startSwimming();
            return;
        }
        // in the air, coming up against the edge of something low with its top in reach: she climbs up onto it
        if (airborne && height > 0.15 && wish.length() > 0.5 && startClimbOnto(in, 0.05)) {
            climbOnto(0, durations);
            return;
        }

        turnToward(dt);

        AnimState next;
        boolean falling = falling();
        if (jumping) next = jumpState;
        else if (action != null) next = action;
        else if (falling) next = st.jump() != null ? st.jump() : AnimState.JUMP;
        else if (blocking) next = st.block();
        else if (velocity.horizontalLength() > 0.25 && in.moving()) next = moving;
        else next = st.idle(crouched);
        setState(next);
        if (falling) stateTime = FALL_POSE * durations.applyAsDouble(next);    // held in the air
        else stateTime += dt;
        if (plunging) {
            // the draw over her head, then down: held on the drop until she lands
            if (stateTime >= plungeAt(PLUNGE_DIVE)) vy = Math.min(vy, -PLUNGE_SPEED);
            if (airborne()) stateTime = Math.min(stateTime, plungeAt(PLUNGE_HOLD));
        }
        if (aerial && (action == null || !airborne())) aerial = false;

        // The dash: held through its push-off, it turns into the roll - at
        // the frame it has reached, the two being the same until then.
        if (action == AnimState.DASH) {
            if (!in.dodgeHeld()) dodgeLetGo = true;
            if (!dodgeLetGo && stateTime >= AnimState.DODGE_SHARED / AnimState.DEFAULT_FPS * dodgeScale) {
                double t = stateTime;
                start(AnimState.ROLL, durations);
                dodgeScale = scale(AnimState.ROLL);
                stateTime = t;
            }
        }
    }

    // --- riding --------------------------------------------------------------------------

    /**
     * Get up on the saddled horse standing at {@code horseAt} facing {@code
     * horseHeading} (its seat where {@code track} says): a hop from where she
     * stands into the saddle, turning to face its way. False - and nothing
     * happens - when she is on one already, climbing, in the air, or busy.
     */
    public boolean mount(Vec3 horseAt, double horseHeading, RideTrack track) {
        if (riding != Riding.NO || wall != null || ledge != null || airborne() || preview != null
                || action != null && !isEmote(action)) {
            return false;
        }
        rideTrack = track == null ? RideTrack.standard() : track;
        riding = Riding.MOUNTING;
        rideTime = 0;
        hopFrom = position;
        hopTo = horseAt;
        hopFromLift = 0;
        hopFromAhead = 0;
        hopFromHeading = heading;
        hopToHeading = horseHeading;
        action = null;
        drawing = false;
        jumping = false;
        plunging = aerial = false;
        grab = null;
        crouched = false;
        rollPrimed = false;
        velocity = Vec3.ZERO;
        rideSpeed = 0;
        horseSheet = "idle";
        horseTime = 0;
        horseRate = 1;
        return true;
    }

    /**
     * Get down off the horse onto {@code to} (its ground, beside it): a hop
     * down off the saddle. False when she is not sitting one.
     */
    public boolean dismount(Vec3 to) {
        if (riding != Riding.RIDING) return false;
        riding = Riding.DISMOUNTING;
        rideTime = 0;
        hopFrom = position;
        hopTo = to;
        hopFromLift = rideLift;
        hopFromAhead = rideAhead;
        hopFromHeading = hopToHeading = heading;
        velocity = Vec3.ZERO;
        rideSpeed = 0;
        return true;
    }

    /** Off the horse at once, standing where it stood (put somewhere else, knocked out, a new level). */
    public void unseat() {
        if (riding == Riding.NO) return;
        riding = Riding.NO;
        rideLift = rideAhead = 0;
        rideSpeed = 0;
        velocity = Vec3.ZERO;
        setState(stance().idle(false));
    }

    /** The hop up into the saddle, or down off it. */
    private void hop(double dt, ToDoubleFunction<AnimState> durations) {
        boolean up = riding == Riding.MOUNTING;
        rideTime += dt;
        double t = Math.min(1, rideTime / (up ? MOUNT_TIME : DISMOUNT_TIME));
        double e = t * t * (3 - 2 * t);
        position = new Vec3(hopFrom.x() + (hopTo.x() - hopFrom.x()) * e, hopFrom.y() + (hopTo.y() - hopFrom.y()) * e,
                hopFrom.z() + (hopTo.z() - hopFrom.z()) * e);
        height = 0;
        double seat = up ? rideTrack.lift("idle", 0) : 0;
        rideLift = hopFromLift + (seat - hopFromLift) * e + MOUNT_HOP * 4 * t * (1 - t);
        rideAhead = hopFromAhead + ((up ? rideTrack.seatAhead() : 0) - hopFromAhead) * e;
        double turn = Math.atan2(Math.sin(hopToHeading - hopFromHeading), Math.cos(hopToHeading - hopFromHeading));
        heading = wantHeading = hopFromHeading + turn * e;
        velocity = Vec3.ZERO;
        // her jump's rise getting on - sitting once she is over the saddle - and its fall getting off
        AnimState jump = stance().jump() != null ? stance().jump() : AnimState.JUMP;
        if (up && t >= 0.7) {
            setState(AnimState.RIDE);
            stateTime = 0;
        } else {
            setState(jump);
            double top = JUMP_LAUNCH + JUMP_AIRTIME * 0.5;
            double from = up ? JUMP_LAUNCH : top, to = up ? top : 1.0;
            stateTime = durations.applyAsDouble(jump) * (from + (to - from) * (up ? t / 0.7 : t));
        }
        if (t < 1) return;
        if (up) {
            riding = Riding.RIDING;
            horseSheet = "idle";
            horseTime = 0;
            horseRate = 1;
            events.add(new Event(Event.Kind.MOUNTED, 0));
        } else {
            riding = Riding.NO;
            rideLift = rideAhead = 0;
            position = terrain.onGround(hopTo);
            setState(stance().idle(false));
            events.add(new Event(Event.Kind.DISMOUNTED, 0));
        }
    }

    /**
     * Sitting the horse: it turns toward the way she steers no faster than
     * {@link #RIDE_TURN}, and goes the way it faces at the pace she asks -
     * slower round a sharp turn, uphill and through water - gathering
     * itself up more slowly than it pulls up. Its sheet follows its pace, and
     * hers its sheet, on its clock.
     */
    private void steer(double dt, Intent in) {
        Vec3 wish = new Vec3(in.moveX(), 0, in.moveZ());
        double len = Math.min(1, wish.length());
        double want = 0;
        if (len > 0.05) {
            wantHeading = SpriteView.headingOf(wish.x(), wish.z());
            want = (in.sprint() ? RIDE_GALLOP : in.run() ? RIDE_CANTER : RIDE_WALK) * len;
        }
        double diff = Math.atan2(Math.sin(wantHeading - heading), Math.cos(wantHeading - heading));
        double most = RIDE_TURN * (rideSpeed > RIDE_CANTER ? 0.65 : 1) * dt;
        heading += Math.max(-most, Math.min(most, diff));
        Vec3 ahead = SpriteView.headingVector(heading);
        double along = Math.cos(Math.min(Math.abs(diff), Math.PI / 2));
        double target = want * (0.3 + 0.7 * along) * slopeFactor(ahead);
        rideSpeed += (target - rideSpeed) * (1 - Math.exp(-dt * (target > rideSpeed ? 1.8 : 3.2)));
        if (target == 0 && rideSpeed < 0.05) rideSpeed = 0;
        if (!rideMove(ahead.scale(rideSpeed * dt))) rideSpeed *= 0.5;          // up against something
        velocity = ahead.scale(rideSpeed);

        AnimalKind horse = AnimalKind.HORSE;
        String sheet;
        double rate;
        if (rideSpeed < 0.15) {
            sheet = "idle";
            rate = 1;
        } else if (rideSpeed < RIDE_RUN_ABOVE) {
            sheet = "walk";
            rate = Math.max(0.6, Math.min(2.2, rideSpeed / horse.walkPace()));
        } else {
            sheet = "run";
            rate = Math.max(0.6, Math.min(2.2, rideSpeed / horse.runPace()));
        }
        if (sheet.equals(horseSheet)) {
            horseTime += dt * rate;
        } else {
            horseSheet = sheet;
            horseTime = 0;
        }
        horseRate = rate;
        setState(sheet.equals("run") ? AnimState.RIDE_RUN : AnimState.RIDE);
        stateTime = sheet.equals("idle") ? 0 : horseTime;           // standing, she sits still
        double[] seat = rideTrack.seat(horseSheet, horseTime);
        rideAhead = seat[0];
        rideLift = seat[1] - RideTrack.SEAT_Z;
    }

    /**
     * The horse's step along the ground: up no steeper than she could walk,
     * never off a ledge (a drop steeper than it could step down), nor onto
     * ground too steep to stand on, nor into deep water - sliding along what
     * stops it a way at a time. False when it did not go the whole step.
     */
    private boolean rideMove(Vec3 step) {
        if (step.horizontalLength() < 1e-12) return true;
        for (Vec3 s : new Vec3[]{step, new Vec3(step.x(), 0, 0), new Vec3(0, 0, step.z())}) {
            if (s.horizontalLength() < 1e-12) continue;
            double x = position.x() + s.x(), z = position.z() + s.z();
            if (!canStep(x, z, s, position.y(), true)) continue;
            double ground = terrain.height(x, z);
            if (position.y() - ground > s.horizontalLength() * 2.5 + 0.12) continue;
            if (terrain.slopeUnder(x, z) > SLIDE) continue;
            position = new Vec3(x, ground, z);
            return s == step;
        }
        return false;
    }

    /** Whether she rides: not, getting on, sitting the horse, getting off. */
    public Riding riding() { return riding; }

    /** Whether she sits a horse (not getting on or off it). */
    public boolean mounted() { return riding == Riding.RIDING; }

    /** The horse's sheet under her: {@code idle}, {@code walk} or {@code run}. */
    public String horseSheet() { return horseSheet; }

    /** How far into {@link #horseSheet} the horse is, seconds at its rate - her own clock while she sits it. */
    public double horseTime() { return horseTime; }

    /** How fast the horse's sheet plays. */
    public double horseRate() { return horseRate; }

    /** How far above the ground her feet (her sprite's ground) are, sitting the saddle or hopping to or off it. */
    public double rideLift() { return rideLift; }

    /** The seat she sits on, its {@code ride.json}. */
    public RideTrack rideTrack() { return rideTrack; }

    /** Which way she, and her horse, are going, metres a second. */
    public Vec3 velocity() { return velocity; }

    // --- the ground under her ----------------------------------------------------------

    /**
     * How much faster or slower than on the level she goes along {@code
     * wish} on the slope she stands on: uphill slower (two thirds the speed
     * up a 1-in-2), downhill up to a fifth faster.
     */
    private double slopeFactor(Vec3 wish) {
        double wade = 1;
        double deep = waterDepth();
        if (deep > WaterMap.WADE) wade = deep > 0.5 ? 0.5 : 0.7;          // wading slows her
        return wade * hillFactor(wish);
    }

    private double hillFactor(Vec3 wish) {
        double len = wish.horizontalLength();
        if (len < 1e-6 || terrain.flat()) return 1;
        double rise = terrain.rise(position.x(), position.z(), wish.x() / len, wish.z() / len);
        return rise > 0 ? 1 / (1 + 1.1 * rise) : 1 + 0.2 * Math.min(1, -rise);
    }

    /**
     * Move her {@code step} along the ground: stopped where it rises
     * steeper than {@link Terrain#STEEP} or higher than her feet (a step at a
     * time along each way, so she slides along a rock face rather than
     * sticking to it), down a slope she can walk, and off a ledge into the
     * air.
     */
    private void move(Vec3 step, boolean grounded) {
        if (step.horizontalLength() < 1e-12) return;
        double feetY = position.y() + height;
        Vec3 to = null;
        for (Vec3 s : new Vec3[]{step, new Vec3(step.x(), 0, 0), new Vec3(0, 0, step.z())}) {
            if (s.horizontalLength() < 1e-12) continue;
            if (canStep(position.x() + s.x(), position.z() + s.z(), s, feetY, grounded)) {
                to = s;
                break;
            }
        }
        if (to == null) {
            velocity = Vec3.ZERO;
            return;
        }
        double x = position.x() + to.x(), z = position.z() + to.z();
        double ground = groundAt(x, z, feetY + (grounded ? STEP_UP : 0.02));
        if (grounded && !airborne && feetY - ground <= STEP_DOWN && slopeAt(x, z, ground) <= SLIDE) {
            height = 0;                                          // down a slope she walks
        } else {
            height = Math.max(0, feetY - ground);
        }
        position = new Vec3(x, ground, z);
    }

    /**
     * The ground under {@code (x, z)}: the terrain, or the top of a low thing
     * standing on it there no higher than {@code below} - a table she is on,
     * a crate she steps up onto.
     */
    private double groundAt(double x, double z, double below) {
        double g = terrain.height(x, z);
        double t = tops.top(x, z, below);
        return Double.isNaN(t) ? g : Math.max(g, t);
    }

    /** How steep it is under {@code (x, z)} at height {@code y}: the terrain's slope, or none on top of something. */
    private double slopeAt(double x, double z, double y) {
        return y > terrain.height(x, z) + 0.05 ? 0 : terrain.slopeUnder(x, z);
    }

    /** Whether she can go to {@code (x, z)} from where her feet are, by {@code step}. */
    private boolean canStep(double x, double z, Vec3 step, double feetY, boolean grounded) {
        if (!water.isEmpty()) {
            // water too deep to wade: not in, unless she is in it already (then only towards the shallows)
            double there = water.depth(x, z, terrain.height(x, z));
            if (riding != Riding.NO && there > WaterMap.TOO_DEEP && there > waterDepth() - 1e-6) return false;
        }
        double rise = groundAt(x, z, feetY + (grounded ? STEP_UP : 0.02)) - feetY;
        if (rise <= 0.02) return true;                           // level, or down
        if (!grounded) return rise <= 0.02;                      // in the air: nothing higher than her feet
        if (rise > terrain.height(x, z) - feetY + 0.02) return rise <= STEP_UP;   // up onto something low
        double len = step.horizontalLength();
        double steep = terrain.rise(x, z, step.x() / len, step.z() / len);
        return rise <= 0.4 && steep <= Terrain.STEEP;
    }

    /** Gravity, and the landing: hurt by a long fall, or rolling out of it. */
    private void fall(double dt, ToDoubleFunction<AnimState> durations) {
        if (height <= 1e-6 && vy <= 0 && slopeAt(position.x(), position.z(), position.y()) > SLIDE) slide(dt);
        double feetY = position.y() + height;
        if (height > 0) {
            // coming down over something low - a table, a barrel - she comes down on its top
            double under = groundAt(position.x(), position.z(), feetY + 0.02);
            if (under > position.y()) {
                position = new Vec3(position.x(), under, position.z());
                height = feetY - under;
            }
        }
        if (height > 0 || vy > 0) {
            if (!airborne) {
                airborne = true;
                fallFrom = feetY;
            }
            fallFrom = Math.max(fallFrom, feetY);
            vy -= GRAVITY * dt;
            height = Math.max(0, height + vy * dt);
            if (height > 0) return;
        }
        if (!airborne) {
            vy = 0;
            return;
        }
        if (slopeAt(position.x(), position.z(), position.y()) > SLIDE) {
            vy = Math.min(vy, 0);                                // on a face too steep to stand: on down it
            return;
        }
        // landed
        airborne = false;
        double drop = fallFrom - position.y();
        vy = 0;
        double hurt = drop > SAFE_FALL ? FALL_DAMAGE * (drop - SAFE_FALL) : 0;
        if (plunging) {
            // the jump attack strikes down: unhurt from as high as PLUNGE_SAFE, the blow landing as she does
            plunging = false;
            rollPrimed = false;
            velocity = Vec3.ZERO;
            hurt = drop > PLUNGE_SAFE ? FALL_DAMAGE * (drop - PLUNGE_SAFE) : 0;
            if (hurt > 0) hurt(Math.round(hurt));
            events.add(new Event(Event.Kind.PLUNGED, drop));
            return;
        }
        if (rollPrimed && drop >= ROLL_FALL) {
            jumping = false;
            drawing = false;
            crouched = false;
            dodgeHeading = speed() > 0.5 ? SpriteView.headingOf(velocity.x(), velocity.z()) : heading;
            heading = wantHeading = dodgeHeading;
            dodgeLetGo = true;
            start(AnimState.ROLL, durations);
            dodgeScale = scale(AnimState.ROLL);
            events.add(new Event(Event.Kind.ROLLED, hurt));
        } else if (hurt > 0) {
            hurt(Math.round(hurt));
            events.add(new Event(Event.Kind.FALL, Math.round(hurt)));
        } else {
            events.add(new Event(Event.Kind.LANDED, drop));
        }
        rollPrimed = false;
    }

    /** Down a face too steep to stand on, her feet where they were: off it, she falls. */
    private void slide(double dt) {
        Vec3 n = terrain.normal(position.x(), position.z());
        double len = Math.hypot(n.x(), n.z());
        if (len < 1e-9) return;
        double feetY = position.y() + height;
        double x = position.x() + n.x() / len * SLIDE_SPEED * dt, z = position.z() + n.z() / len * SLIDE_SPEED * dt;
        double ground = terrain.height(x, z);
        if (ground > feetY) return;
        position = new Vec3(x, ground, z);
        height = feetY - ground;
        if (!airborne) {
            airborne = true;
            fallFrom = feetY;
        }
    }

    /** Whether she is in the air off a ledge (not a jump, not a dodge): she shows the falling pose. */
    private boolean falling() {
        return airborne && !jumping && action == null && height > 0.3;
    }

    // --- swimming ----------------------------------------------------------------------

    /** Whether her feet have gone into water too deep to wade: she swims. */
    private boolean intoDeepWater() {
        if (water.isEmpty() || riding != Riding.NO || wall != null) return false;
        double depth = waterDepth();
        if (depth <= SWIM_DEPTH) return false;
        double surface = water.surface(position.x(), position.z(), position.y());
        return position.y() + height <= surface - SURFACE_LINE + 0.3;
    }

    /** Into the water: what she was doing stops, her weapon stowed; she comes up treading water. */
    private void startSwimming() {
        swimming = true;
        double surface = water.surface(position.x(), position.z(), position.y());
        double feetY = position.y() + height;
        // dropped in from high, she goes under a little way and comes back up
        under = feetY < surface - TREAD_LINE - 0.4 && vy < -6;
        diveDepth = under ? Math.min(surface - feetY - SWIM_MIDDLE, maxDive()) : 0;
        diveTo = 0;
        action = null;
        drawing = jumping = plunging = aerial = false;
        crouched = false;
        rollPrimed = false;
        airborne = false;
        vy = 0;
        grab = null;
        velocity = new Vec3(velocity.x() * 0.4, 0, velocity.z() * 0.4);
        setState(under ? AnimState.SWIM_UNDER : AnimState.SWIM_IDLE);
        placeInWater(surface);
    }

    /** As deep as her middle can go here: {@link #OFF_BOTTOM} over the bottom. */
    private double maxDive() {
        return Math.max(0, waterDepth() - OFF_BOTTOM);
    }

    /** Swimming: her stroke, the dive key and the jump key, her breath; out onto the shallows or a low bank. */
    private void swim(double dt, Intent in) {
        // diving down a step, coming up a step
        if (in.crouch()) {
            diveTo = (under ? diveTo : 0) + DIVE_STEP;
            under = true;
        }
        if (in.jump() && under) diveTo = Math.max(0, diveTo - DIVE_STEP);
        diveTo = Math.min(diveTo, Math.max(DIVE_MIN, maxDive()));
        if (under) {
            double want = Math.max(diveTo, 0);
            double d = want - diveDepth, step = DIVE_SPEED * dt;
            diveDepth += Math.max(-step, Math.min(step, d));
            if (diveTo <= 0 && diveDepth <= 0.05) {
                under = false;               // up: a breath
                diveDepth = 0;
                events.add(new Event(Event.Kind.SURFACED, MAX_BREATH - breath));
            }
        }
        breathe(dt);

        Vec3 wish = new Vec3(in.moveX(), 0, in.moveZ());
        if (wish.length() > 1) wish = wish.normalize();
        boolean fast = (in.run() || in.sprint()) && stamina > 1 && wish.length() > 0.3;
        if (fast) {
            stamina = Math.max(0, stamina - SWIM_COST * dt);
            sinceTired = 0;
        }
        double speed = (under ? SWIM_UNDER_SPEED : SWIM_SPEED) * (fast ? SWIM_FAST / SWIM_SPEED : 1);
        velocity = velocity.lerp(wish.scale(speed), 1 - Math.exp(-dt * 3));
        Vec3 step = velocity.scale(dt);
        if (step.horizontalLength() > 1e-9) {
            double x = position.x() + step.x(), z = position.z() + step.z();
            double ground = terrain.height(x, z);
            double surface = water.surface(position.x(), position.z(), position.y());
            double there = water.depth(x, z, ground);
            if (ground > surface + 0.6) {
                velocity = Vec3.ZERO;                                // a bank too high to climb out onto
            } else if (!under && there < SWIM_DEPTH - 0.15) {
                // into the shallows, or up onto a low bank: on her feet
                swimming = false;
                position = new Vec3(x, groundAt(x, z, surface + 0.6), z);
                height = 0;
                vy = 0;
                setState(stance().idle(false));
                events.add(new Event(Event.Kind.LEFT_WATER, 0));
                return;
            } else {
                position = new Vec3(x, ground, z);
                diveDepth = Math.min(diveDepth, maxDive());
            }
        }
        if (in.moving()) wantHeading = SpriteView.headingOf(wish.x(), wish.z());
        turnToward(dt);
        AnimState next = under ? AnimState.SWIM_UNDER
                : velocity.horizontalLength() > 0.3 ? AnimState.SWIM : AnimState.SWIM_IDLE;
        setState(next);
        stateTime += dt * (next == AnimState.SWIM_IDLE ? 1 : Math.max(0.6, velocity.horizontalLength()
                / (under ? SWIM_UNDER_SPEED : SWIM_SPEED)));
        placeInWater(water.surface(position.x(), position.z(), position.y()));
    }

    /** Her sprite's ground where her state puts the water's surface on her (or her middle under it). */
    private void placeInWater(double surface) {
        double feetY;
        if (under) {
            feetY = surface - SWIM_MIDDLE - Math.max(diveDepth, 0);
        } else {
            double t = Math.min(1, velocity.horizontalLength() / SWIM_SPEED);
            feetY = surface - (TREAD_LINE + (SURFACE_LINE - TREAD_LINE) * t);
        }
        height = Math.max(0, feetY - position.y());
    }

    /** Her breath: running out under the water (then drowning), coming back above it. */
    private void breathe(double dt) {
        if (swimming && under) {
            breath = Math.max(0, breath - dt);
            if (breath <= 0) {
                drowned += DROWN * dt;
                if (drowned >= 1) {
                    double d = Math.floor(drowned);
                    drowned -= d;
                    hurt(d);
                }
            }
        } else {
            breath = Math.min(MAX_BREATH, breath + BREATH_BACK * dt);
            drowned = 0;
        }
    }

    /** Whether she is swimming (in water too deep to wade), and under it. */
    public boolean swimming() { return swimming; }

    public boolean underWater() { return swimming && under; }

    /** Her breath left, seconds ({@link #MAX_BREATH} full). */
    public double breath() { return breath; }

    /** How far her middle is under the water's surface, diving (0 on it). */
    public double diveDepth() { return under ? diveDepth : 0; }

    // --- climbing ----------------------------------------------------------------------

    /** Take hold of the wall she is walking into, if there is one in reach: true if she does. */
    private boolean grabWall(Vec3 wish) {
        if (stamina < CLIMB_MIN || terrain.walls().isEmpty()) return false;
        Terrain.Wall w = terrain.wallAt(position.x(), position.z(), GRAB_REACH);
        if (w == null) return false;
        double len = wish.horizontalLength();
        double into = -(wish.x() * w.nx() + wish.z() * w.nz()) / len;
        double feetY = position.y() + height;
        if (into < 0.7 || feetY > w.top() - LEDGE_RISE - 0.1 || feetY < w.bottom() - 0.3) return false;
        takeHold(w);
        setState(AnimState.CLIMB);
        stateTime = 1000;                 // (well past 0, so climbing down can play it backwards)
        return true;
    }

    /** On wall {@code w}, whatever she was doing. */
    private void takeHold(Terrain.Wall w) {
        wall = w;
        crouched = false;
        drawing = false;
        action = null;
        jumping = false;
        plunging = aerial = false;
        airborne = false;
        rollPrimed = false;
        vy = 0;
        velocity = Vec3.ZERO;
    }

    /** How far out from wall {@code w}'s foot she hangs with her feet at {@code feetY}: {@link #CLING} from the rock her hands are on. */
    private double cling(Terrain.Wall w, double along, double feetY) {
        return terrain.faceOut(w, along, Math.min(w.top() - 0.01, feetY + LEDGE_RISE)) + CLING;
    }

    /**
     * A frame on the wall: up, down and along it as the keys say, stamina
     * spent; over the lip with her hands at it, off at the bottom, and
     * dropped when the stamina is gone or a key lets go.
     */
    private void climb(double dt, Intent in) {
        Terrain.Wall w = wall;
        velocity = Vec3.ZERO;
        double len = Math.max(1e-6, w.length());
        double ux = (w.x1() - w.x0()) / len, uz = (w.z1() - w.z0()) / len;
        double mx = in.moveX(), mz = in.moveZ(), m = Math.hypot(mx, mz);
        if (m > 1) {
            mx /= m;
            mz /= m;
        }
        double up = -(mx * w.nx() + mz * w.nz()), side = mx * ux + mz * uz;
        double feetY = position.y() + height;
        double climbY = Math.min(w.top() - LEDGE_RISE, Math.max(w.bottom(), feetY + up * CLIMB_SPEED * dt));
        double along = Math.max(0.3, Math.min(len - 0.3, w.along(position.x(), position.z())
                + side * CLIMB_SIDEWAYS * dt));
        Vec3 at = w.at(along, cling(w, along, climbY));
        double ground = terrain.height(at.x(), at.z());
        position = new Vec3(at.x(), ground, at.z());
        heading = wantHeading = w.facing();
        boolean moving = Math.abs(up) > 0.1 || Math.abs(side) > 0.1;
        stamina = Math.max(0, stamina - (moving ? CLIMB_COST : HANG_COST) * dt);
        sinceTired = 0;
        setState(AnimState.CLIMB);
        stateTime += dt * (Math.abs(up) > 0.1 ? up : Math.abs(side) * 0.8);

        if (climbY >= w.top() - LEDGE_RISE - 1e-6 && up > 0.1) {
            // her hands at the lip: up over it
            startLedge(AnimState.LEDGE_UP, w, along);
            return;
        }
        if (climbY <= ground + 0.01 && up < 0) {
            wall = null;                       // down at the foot: standing
            height = 0;
            return;
        }
        height = Math.max(0, climbY - ground);
        boolean letGo = in.jump() || in.dodge() || in.crouch();
        if (letGo || stamina <= 0) {
            wall = null;
            Vec3 off = w.at(along, cling(w, along, climbY) + 0.25);
            position = new Vec3(off.x(), terrain.height(off.x(), off.z()), off.z());
            height = Math.max(0, climbY - position.y());
            airborne = height > 0;
            fallFrom = climbY;
            vy = 0;
            events.add(new Event(Event.Kind.LET_GO, letGo ? 0 : 1));
        }
    }

    /**
     * Walking towards the lip of a wall she stands on top of: she steps down
     * over it onto the wall ({@link #ledge} starts) once she is near enough,
     * or - too tired to climb - stops short of it. Returns how she goes on
     * walking.
     */
    private Vec3 stepDown(Vec3 wish) {
        double feetY = position.y() + height;
        for (Terrain.Wall w : terrain.walls()) {
            double along = w.along(position.x(), position.z());
            if (along < 0.3 || along > w.length() - 0.3 || Math.abs(feetY - w.top()) > 0.3) continue;
            double lip = terrain.lipOut(w, along), out = w.out(position.x(), position.z()) - lip;
            if (out < -LEDGE_BEHIND - 0.8 || out > 0.1) continue;
            double toward = (wish.x() * w.nx() + wish.z() * w.nz()) / wish.horizontalLength();
            if (toward < 0.5) continue;
            if (stamina < CLIMB_MIN) {
                // too tired to climb down: no further out than where she would step down from
                if (out < -LEDGE_BEHIND - 0.15) continue;
                double o = wish.x() * w.nx() + wish.z() * w.nz(), v = velocity.x() * w.nx() + velocity.z() * w.nz();
                if (v > 0) velocity = velocity.sub(new Vec3(w.nx() * v, 0, w.nz() * v));
                return wish.sub(new Vec3(w.nx() * o, 0, w.nz() * o));
            }
            if (out < -LEDGE_BEHIND - 0.15 || toward < 0.7) continue;
            takeHold(w);
            startLedge(AnimState.LEDGE_DOWN, w, along);
            events.add(new Event(Event.Kind.STEPPED_DOWN, 0));
            return Vec3.ZERO;
        }
        return wish;
    }

    /** Begin stepping up over wall {@code w}'s lip ({@link AnimState#LEDGE_UP}) or down over it, {@code along} it. */
    private void startLedge(AnimState s, Terrain.Wall w, double along) {
        ledge = s;
        ledgeAlong = along;
        ledgeLip = terrain.lipOut(w, along);
        ledgeTime = 0;
        heading = wantHeading = w.facing();
        setState(s);
    }

    /**
     * A frame of stepping up over the lip or down over it: carried along
     * {@link #LEDGE_PATH} (backwards, stepping down) as its sheets play;
     * standing on the top at the end of the step up, hanging on the wall by
     * her hands at the end of the step down.
     */
    private void overLip(double dt, ToDoubleFunction<AnimState> durations) {
        Terrain.Wall w = wall;
        ledgeTime += dt;
        stamina = Math.max(0, stamina - CLIMB_COST * dt);
        sinceTired = 0;
        velocity = Vec3.ZERO;
        double last = LEDGE_PATH[LEDGE_PATH.length - 1][0];
        double duration = durations.applyAsDouble(ledge);
        double f = Math.min(last, ledgeTime / Math.max(1e-6, duration) * (last + 1));
        if (ledge == AnimState.LEDGE_DOWN) f = last - f;
        Vec3 at = w.at(ledgeAlong, ledgeLip + Pchip.at(LEDGE_PATH, 1, f));
        double feetY = w.top() + Pchip.at(LEDGE_PATH, 2, f);
        // (in under the lip, the ground under her middle is in the rock: she is on it, not under it)
        position = new Vec3(at.x(), Math.min(feetY, terrain.height(at.x(), at.z())), at.z());
        height = feetY - position.y();
        heading = wantHeading = w.facing();
        setState(ledge);
        stateTime = ledgeTime;
        if (ledgeTime < duration) return;
        if (ledge == AnimState.LEDGE_UP) {
            // up: standing on the top behind the lip
            ledge = null;
            wall = null;
            position = terrain.onGround(at);
            height = 0;
            events.add(new Event(Event.Kind.CLIMBED, 0));
        } else {
            // down: hanging by her hands, climbing
            ledge = null;
            setState(AnimState.CLIMB);
            stateTime = 1000;
        }
    }

    /**
     * Space by something low - a table, a crate, a chest - or coming up
     * against its edge in the air: if its top is {@code lo} to {@link
     * #CLIMB_ONTO} over her feet and its edge in reach the way she is going
     * (or facing), she starts climbing up onto it. Whether she does.
     */
    private boolean startClimbOnto(Intent in, double lo) {
        Vec3 dir = in.moving() ? new Vec3(in.moveX(), 0, in.moveZ()).normalize()
                : SpriteView.headingVector(heading);
        double feetY = position.y() + height;
        double[] e = tops.ahead(position.x(), position.z(), dir.x(), dir.z(), CLIMB_REACH, feetY, lo, CLIMB_ONTO);
        if (e == null) return false;
        double h = e[2] - feetY;
        // from as far into the step up over a lip (LEDGE_PATH) as the top is high: the end of it for something low
        double last = LEDGE_PATH[LEDGE_PATH.length - 1][0], f0 = 0;
        if (h < LEDGE_RISE) {
            f0 = LEDGE_PATH[1][0];
            while (f0 < last && -Pchip.at(LEDGE_PATH, 2, f0) > h) f0 += 0.25;
        }
        mantling = true;
        mantleEdge = new Vec3(e[0], 0, e[1]);
        mantleHeading = SpriteView.headingOf(dir.x(), dir.z());
        mantleTop = e[2];
        mantleF0 = Math.min(f0, last - 4);
        mantleTime = 0;
        Vec3 start = pathAt(mantleF0);
        mantleFrom = new Vec3(position.x() - start.x(), 0, position.z() - start.z());
        jumping = false;
        plunging = aerial = false;
        action = null;
        drawing = false;
        grab = null;
        crouched = false;
        velocity = Vec3.ZERO;
        vy = 0;
        return true;
    }

    /** Where she is at frame {@code f} of climbing onto the top she has hold of (its y her feet's). */
    private Vec3 pathAt(double f) {
        Vec3 dir = SpriteView.headingVector(mantleHeading);
        double out = Pchip.at(LEDGE_PATH, 1, f);
        return new Vec3(mantleEdge.x() - dir.x() * out, mantleTop + Pchip.at(LEDGE_PATH, 2, f),
                mantleEdge.z() - dir.z() * out);
    }

    /**
     * A frame of climbing up onto something low: carried along the end of
     * {@link #LEDGE_PATH} as the step up's sheets play from that frame - from
     * where she stood, eased in - standing on its top at the end.
     */
    private void climbOnto(double dt, ToDoubleFunction<AnimState> durations) {
        mantleTime += dt;
        velocity = Vec3.ZERO;
        double last = LEDGE_PATH[LEDGE_PATH.length - 1][0];
        double perFrame = durations.applyAsDouble(AnimState.LEDGE_UP) / (last + 1);
        double f = Math.min(last, mantleF0 + mantleTime / Math.max(1e-6, perFrame));
        Vec3 at = pathAt(f);
        double ease = Math.max(0, 1 - mantleTime / 0.18);
        double x = at.x() + mantleFrom.x() * ease, z = at.z() + mantleFrom.z() * ease;
        double feetY = Math.max(at.y(), position.y() + height - 0.05 * (1 - ease));
        if (f >= last) feetY = mantleTop;
        double under = groundAt(x, z, Math.min(feetY, mantleTop) + 0.02);
        position = new Vec3(x, Math.min(feetY, under), z);
        height = Math.max(0, feetY - position.y());
        heading = wantHeading = mantleHeading;
        setState(AnimState.LEDGE_UP);
        stateTime = mantleF0 * perFrame + mantleTime;
        if (f < last) return;
        mantling = false;
        position = new Vec3(x, groundAt(x, z, mantleTop + 0.05), z);
        height = 0;
        vy = 0;
        airborne = false;
        events.add(new Event(Event.Kind.CLIMBED, 0));
    }

    /** Whether she is climbing up onto something low. */
    public boolean climbingOnto() { return mantling; }

    /** Stand on, step up onto and climb onto the tops of {@code t} from now on (null: none). */
    public void setTops(Tops t) {
        tops = t == null ? Tops.NONE : t;
    }

    /** The dodge under way's animation's length over its art's ({@link AnimState#defaultFrames} at 30 a second). */
    private double scale(AnimState s) {
        double art = s.defaultFrames() / AnimState.DEFAULT_FPS;
        return actionDuration > 1e-6 ? actionDuration / art : 1;
    }

    /** How fast dodge {@code s} carries her {@code t} seconds into it (m/s). */
    private double dodgeSpeed(AnimState s, double t) {
        double[][] keys = s == AnimState.ROLL ? ROLL_SPEED : DASH_SPEED;
        double f = t / dodgeScale * AnimState.DEFAULT_FPS;
        if (f <= keys[0][0]) return keys[0][1] / dodgeScale;
        for (int i = 1; i < keys.length; i++) {
            if (f <= keys[i][0]) {
                double u = (f - keys[i - 1][0]) / (keys[i][0] - keys[i - 1][0]);
                return (keys[i - 1][1] + u * (keys[i][1] - keys[i - 1][1])) / dodgeScale;
            }
        }
        return keys[keys.length - 1][1] / dodgeScale;
    }

    /** How far dodge {@code s} carries her, in metres, played as authored. */
    public static double dodgeDistance(AnimState s) {
        double[][] keys = s == AnimState.ROLL ? ROLL_SPEED : DASH_SPEED;
        double d = 0;
        for (int i = 1; i < keys.length; i++) {
            d += (keys[i][0] - keys[i - 1][0]) * (keys[i][1] + keys[i - 1][1]) / 2;
        }
        return d / AnimState.DEFAULT_FPS;
    }

    /** When the jump attack's frame {@code frame} (of its {@link #PLUNGE_FRAMES}) comes, seconds into it. */
    private double plungeAt(int frame) {
        return actionDuration * frame / PLUNGE_FRAMES;
    }

    /** Whether she is in the air in a jump attack, dropping onto what is under her. */
    public boolean plunging() { return plunging; }

    /** Whether she is in the air loosing a shot (a jump's bow or crossbow shot). */
    public boolean aerialShot() { return aerial; }

    private void start(AnimState s, ToDoubleFunction<AnimState> durations) {
        if (s == null) return;
        action = s;
        actionDuration = durations.applyAsDouble(s);
        setState(s);
        stateTime = 0;
    }

    private static boolean isEmote(AnimState s) {
        return s.key().startsWith("emote_");
    }

    private void setState(AnimState s) {
        if (s != state) {
            state = s;
            stateTime = 0;
        }
    }

    private void turnToward(double dt) {
        double d = wantHeading - heading;
        d = Math.atan2(Math.sin(d), Math.cos(d));
        heading += d * (1 - Math.exp(-dt * TURN_RATE));
    }

    // --- stances and picking things up ------------------------------------------------

    /**
     * What she holds: a weapon she picked up, or the wardrobe's own - the
     * sword and shield, or the sword alone when she carries no shield - or,
     * with her hands empty, the sword alone's stance with nothing drawn in
     * either hand (the free arm hanging, no shield to raise).
     */
    public Stance stance() {
        if (stance != Stance.SWORD) return stance;
        return handsEmpty ? Stance.BLADE : Stance.ofWardrobe(wardrobe);
    }

    /**
     * Whether nothing is in her hands: the selected hotbar slot is empty or
     * holds something worn ({@link World#equipSelected}). The wardrobe's sword
     * and shield are then not drawn, and a swing is a blow with no weapon.
     */
    public boolean handsEmpty() {
        return handsEmpty && stance == Stance.SWORD;
    }

    /** Empty her hands, or take the wardrobe's sword (and shield) back up (interrupting a swing, not a dodge). */
    public void setHandsEmpty(boolean empty) {
        if (empty == handsEmpty) return;
        handsEmpty = empty;
        if (stance == Stance.SWORD && action != null && !isEmote(action) && !dodging()) action = null;
        drawing = false;
    }

    /** Take up {@code s} (interrupting whatever she was doing with the last weapon, but a dodge). */
    public void setStance(Stance s) {
        if (s == Stance.BLADE) s = Stance.SWORD;          // the wardrobe's, with or without a shield
        if (s == null || s == Stance.FREE || s == Stance.ALL || s == stance) return;
        stance = s;
        if (action != null && !isEmote(action) && !dodging()) action = null;   // (a dodge goes on, whatever is in hand)
        drawing = false;
    }

    public boolean crouched() { return crouched; }

    /**
     * Reach down and pick something up: the pick-up plays (crouched, if she
     * is), and {@code grab} runs as her hand closes on it. Returns false (and
     * does nothing) while she is busy - mid-air or mid-action.
     */
    public boolean pickUp(Runnable grab, ToDoubleFunction<AnimState> durations) {
        if (action != null || jumping || height > 0 || wall != null) return false;
        preview = null;
        this.grab = grab;
        start(crouched ? AnimState.CROUCH_PICKUP : AnimState.PICKUP, durations);
        return true;
    }

    /** The one-shot under way, or null. */
    public AnimState action() { return action; }

    /** How long the one-shot under way lasts, in seconds (from its sheets). */
    public double actionDuration() { return actionDuration; }

    // --- the heavy attack's cooldown ---------------------------------------------------

    /** Seconds until a heavy attack can be made again (0: now). */
    public double heavyWait() { return heavyWait; }

    /** The least time from one heavy attack to the next, in seconds. */
    public double heavyCooldown() { return heavyCooldown; }

    /** Make heavy attacks wait {@code seconds} after each other from now on (0: no wait). */
    public void setHeavyCooldown(double seconds) {
        heavyCooldown = Math.max(0, seconds);
        heavyWait = Math.min(heavyWait, heavyCooldown);
    }

    // --- the dodges ------------------------------------------------------------------

    /** Whether she is dashing or rolling. */
    public boolean dodging() {
        return action == AnimState.DASH || action == AnimState.ROLL;
    }

    /** Whether nothing can hurt her now: from the roll's dive until she lands. */
    public boolean invulnerable() {
        if (action != AnimState.ROLL) return false;
        double f = stateTime / dodgeScale * AnimState.DEFAULT_FPS;
        return f >= ROLL_SAFE[0] && f < ROLL_SAFE[1];
    }

    /** Whether she rolls through what would stop her (the training dummies): all the roll long. */
    public boolean passesThrough() {
        return action == AnimState.ROLL;
    }

    /** Whether she is going fast enough in a dodge to leave afterimages. */
    public boolean afterimages() {
        return dodging() && speed() > AFTERIMAGE_SPEED;
    }

    /** Seconds until she can dodge again (0: now). */
    public double dodgeWait() { return dodgeWait; }

    /** The least time from the end of one dodge to the start of the next, in seconds. */
    public double dodgeCooldown() { return dodgeCooldown; }

    /** Make dodges wait {@code seconds} after the last one ended from now on (0: no wait). */
    public void setDodgeCooldown(double seconds) {
        dodgeCooldown = Math.max(0, seconds);
        dodgeWait = Math.min(dodgeWait, dodgeCooldown);
    }

    // --- preview and posing ----------------------------------------------------------

    /** Loop {@code s} in place (null to go back to live states). */
    public void preview(AnimState s) {
        preview = s;
        if (s != null) {
            wall = null;
            ledge = null;
            mantling = false;
            jumping = false;
            plunging = aerial = false;
            action = null;
            drawing = false;
            grab = null;
            setState(s);
            stateTime = 0;
        }
    }

    public AnimState previewing() { return preview; }

    /** Turn to show {@code facing} to a camera at {@code cameraYaw}. */
    public void face(Facing facing, double cameraYaw) {
        wantHeading = SpriteView.headingShowing(facing, cameraYaw);
    }

    /** Turn by a number of 45° steps (positive turns the character to its left). */
    public void turnSteps(int steps) {
        double step = Math.PI / 4;
        wantHeading = Math.round(wantHeading / step) * step + steps * step;
    }

    /** Turn (over the next few frames) to face along heading {@code h}. */
    public void turnTo(double h) {
        wantHeading = h;
    }

    public void snapHeading(double h) {
        heading = wantHeading = h;
    }

    // --- accessors -------------------------------------------------------------------

    /** Feet position, including the jump height - and, riding, up on the saddle (her sprite's ground). */
    public Vec3 feet() {
        if (riding == Riding.NO) return position.add(0, height, 0);
        Vec3 f = SpriteView.headingVector(heading);
        return position.add(f.x() * rideAhead, height + rideLift, f.z() * rideAhead);
    }

    /** Position on the ground (where the shadow goes): its height the ground's under her. */
    public Vec3 ground() { return position; }

    public double height() { return height; }

    /** Put her standing at {@code p} (on the ground there, off any wall and any horse, not falling). */
    public void setPosition(Vec3 p) {
        unseat();
        swimming = under = false;
        position = terrain.onGround(p);
        height = 0;
        vy = 0;
        wall = null;
        ledge = null;
        mantling = false;
        airborne = false;
        rollPrimed = false;
    }

    /** The ground she walks on. */
    public Terrain terrain() { return terrain; }

    /** The water she wades through: too deep, she cannot go in. */
    public WaterMap water() { return water; }

    public void setWater(WaterMap w) {
        water = w == null ? WaterMap.NONE : w;
    }

    /** How deep the water is where she stands (0: dry). */
    public double waterDepth() {
        return water.isEmpty() ? 0 : water.depth(position.x(), position.z(), position.y());
    }

    /** Walk on {@code t} from now on (null: flat ground); she is put on it where she stands. */
    public void setTerrain(Terrain t) {
        terrain = t == null ? Terrain.FLAT : t;
        double feetY = position.y() + height;
        position = terrain.onGround(position);
        height = Math.max(0, feetY - position.y());
    }

    public double heading() { return heading; }

    public AnimState state() { return state; }

    public double stateTime() { return stateTime; }

    public Wardrobe wardrobe() { return wardrobe; }

    public LayerStack.Memory memory() { return memory; }

    /** What she carries; the selected hotbar slot is what is in her hands ({@link World#equipSelected}). */
    public Inventory inventory() { return inventory; }

    // --- health, stamina, climbing ------------------------------------------------------

    public double health() { return health; }

    public double stamina() { return stamina; }

    /** Seconds since she was last hurt (a lot: never). */
    public double sinceHurt() { return sinceHurt; }

    /** Set her health (scripts and tests; 0 knocks her out). */
    public void setHealth(double h) {
        health = Math.max(0, Math.min(MAX_HEALTH, h));
    }

    /** Set her stamina (scripts and tests). */
    public void setStamina(double s) {
        stamina = Math.max(0, Math.min(MAX_STAMINA, s));
    }

    /**
     * Take {@code damage} (a fall, a flier's swoop): her health goes down,
     * and when it is gone she is knocked out ({@link Event.Kind#KNOCKED_OUT}).
     * Returns what was taken. Whether something can hurt her at all (the
     * roll) is for it to ask first ({@link #invulnerable}).
     */
    public double hurt(double damage) {
        if (damage <= 0 || health <= 0) return 0;
        double took = Math.min(health, damage);
        health -= took;
        sinceHurt = 0;
        events.add(new Event(Event.Kind.HURT, took));
        if (health <= 0) events.add(new Event(Event.Kind.KNOCKED_OUT, 0));
        return took;
    }

    /** Back on her feet at {@code at}, health and stamina full, doing nothing. */
    public void respawn(Vec3 at) {
        setPosition(at);
        health = MAX_HEALTH;
        stamina = MAX_STAMINA;
        sinceHurt = sinceTired = Double.MAX_VALUE;
        action = null;
        drawing = false;
        jumping = false;
        plunging = aerial = false;
        swimming = under = false;
        breath = MAX_BREATH;
        grab = null;
        crouched = false;
        velocity = Vec3.ZERO;
    }

    /** The wall she is climbing, or null. */
    public Terrain.Wall climbing() { return wall; }

    /** Put her {@code metres} up in the air where she stands, falling from there (scripts and tests). */
    public void lift(double metres) {
        wall = null;
        ledge = null;
        mantling = false;
        action = null;
        jumping = false;
        plunging = aerial = false;
        height = Math.max(0, metres);
        vy = 0;
        airborne = height > 0;
        fallFrom = position.y() + height;
    }

    /** Whether she is in the air: a jump, or off a ledge. */
    public boolean airborne() { return airborne || height > 0; }

    /** Whether the dodge key went down in the air: she will land in a roll. */
    public boolean rollPrimed() { return rollPrimed; }

    /**
     * Ask {@code wanted} as each ranged attack starts whether to play its
     * aimed-up variant (what she aims at is well above her).
     */
    public void setAimUp(BooleanSupplier wanted) {
        aimUp = wanted == null ? () -> false : wanted;
    }

    /** What happened to her since the last call (a fall's hurt, a climb's top...). */
    public List<Event> drainEvents() {
        List<Event> out = new ArrayList<>(events);
        events.clear();
        return out;
    }

    /**
     * Keep out of a round obstacle {@code radius} metres across its middle
     * {@code centre} on the ground (a chest): pushed back out to its edge.
     */
    public void keepOut(Vec3 centre, double radius) {
        double dx = position.x() - centre.x(), dz = position.z() - centre.z();
        double d = Math.hypot(dx, dz);
        if (d >= radius) return;
        if (d < 1e-6) {
            dx = 0;
            dz = 1;
            d = 1;
        }
        double feetY = position.y() + height;
        position = terrain.onGround(new Vec3(centre.x() + dx / d * radius, 0, centre.z() + dz / d * radius));
        height = Math.max(0, feetY - position.y());
    }

    /** Put her at {@code (x, z)} on the ground there, her feet as high as they were: pushed out of a wall. */
    public void shoveTo(double x, double z) {
        double feetY = position.y() + height;
        position = terrain.onGround(new Vec3(x, 0, z));
        height = Math.max(0, feetY - position.y());
    }

    /** Keep her on the ground from {@code x0} to {@code x1} and {@code z0} to {@code z1}: a level's edges. */
    public void keepWithin(double x0, double x1, double z0, double z1) {
        double x = Math.max(x0, Math.min(x1, position.x())), z = Math.max(z0, Math.min(z1, position.z()));
        if (x == position.x() && z == position.z()) return;
        double feetY = position.y() + height;
        position = terrain.onGround(new Vec3(x, 0, z));
        height = Math.max(0, feetY - position.y());
    }

    public double speed() { return velocity.horizontalLength(); }
}
