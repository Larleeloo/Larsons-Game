package com.larsons.game.world;

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

    /** Something that happened to her this frame, for the world to show: a fall's hurt, a roll out of one... */
    public record Event(Kind kind, double amount) {
        public enum Kind {
            /** Landed hard: {@code amount} damage. */
            FALL,
            /** Landed unhurt, from a jump or a drop of {@code amount} metres. */
            LANDED,
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
            KNOCKED_OUT
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
        if (ledge != null) {
            overLip(dt, durations);
            return;
        }
        if (wall != null) {
            climb(dt, in);
            return;
        }

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

    // --- the ground under her ----------------------------------------------------------

    /**
     * How much faster or slower than on the level she goes along {@code
     * wish} on the slope she stands on: uphill slower (two thirds the speed
     * up a 1-in-2), downhill up to a fifth faster.
     */
    private double slopeFactor(Vec3 wish) {
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
        double ground = terrain.height(x, z);
        if (grounded && !airborne && feetY - ground <= STEP_DOWN && terrain.slopeUnder(x, z) <= SLIDE) {
            height = 0;                                          // down a slope she walks
        } else {
            height = Math.max(0, feetY - ground);
        }
        position = new Vec3(x, ground, z);
    }

    /** Whether she can go to {@code (x, z)} from where her feet are, by {@code step}. */
    private boolean canStep(double x, double z, Vec3 step, double feetY, boolean grounded) {
        double rise = terrain.height(x, z) - feetY;
        if (rise <= 0.02) return true;                           // level, or down
        if (!grounded) return rise <= 0.02;                      // in the air: nothing higher than her feet
        double len = step.horizontalLength();
        double steep = terrain.rise(x, z, step.x() / len, step.z() / len);
        return rise <= 0.4 && steep <= Terrain.STEEP;
    }

    /** Gravity, and the landing: hurt by a long fall, or rolling out of it. */
    private void fall(double dt, ToDoubleFunction<AnimState> durations) {
        if (height <= 1e-6 && vy <= 0 && terrain.slopeUnder(position.x(), position.z()) > SLIDE) slide(dt);
        double feetY = position.y() + height;
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
        if (terrain.slopeUnder(position.x(), position.z()) > SLIDE) {
            vy = Math.min(vy, 0);                                // on a face too steep to stand: on down it
            return;
        }
        // landed
        airborne = false;
        double drop = fallFrom - position.y();
        vy = 0;
        double hurt = drop > SAFE_FALL ? FALL_DAMAGE * (drop - SAFE_FALL) : 0;
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
            jumping = false;
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

    /** Feet position, including the jump height. */
    public Vec3 feet() {
        return position.add(0, height, 0);
    }

    /** Position on the ground (where the shadow goes): its height the ground's under her. */
    public Vec3 ground() { return position; }

    public double height() { return height; }

    /** Put her standing at {@code p} (on the ground there, off any wall, not falling). */
    public void setPosition(Vec3 p) {
        position = terrain.onGround(p);
        height = 0;
        vy = 0;
        wall = null;
        ledge = null;
        airborne = false;
        rollPrimed = false;
    }

    /** The ground she walks on. */
    public Terrain terrain() { return terrain; }

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
        action = null;
        jumping = false;
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
