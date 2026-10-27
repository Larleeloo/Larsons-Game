package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;

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
            action = null;
            if (ready) start(st.release(crouched), durations);
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
        Vec3 target = wish.scale(moving.speed());
        if (dodging()) {
            velocity = SpriteView.headingVector(dodgeHeading).scale(dodgeSpeed(action, stateTime + dt / 2));
        } else {
            velocity = velocity.lerp(target, 1 - Math.exp(-dt * (grounded ? 14 : 3)));
        }
        position = position.add(velocity.scale(dt));
        if (in.moving() && !rooted) wantHeading = SpriteView.headingOf(wish.x(), wish.z());

        // The jump: crouch, then launch with just enough speed to come down
        // as the animation reaches its landing.
        if (jumping) {
            if (!launched && stateTime >= JUMP_LAUNCH * actionDuration) {
                launched = true;
                vy = GRAVITY * (JUMP_AIRTIME * actionDuration) / 2;
            }
            if (launched && height <= 0 && vy <= 0 && stateTime >= actionDuration) jumping = false;
        }
        vy -= GRAVITY * dt;
        height = Math.max(0, height + vy * dt);
        if (height <= 0 && vy < 0) vy = 0;

        turnToward(dt);

        AnimState next;
        if (jumping) next = jumpState;
        else if (action != null) next = action;
        else if (blocking) next = st.block();
        else if (velocity.horizontalLength() > 0.25 && in.moving()) next = moving;
        else next = st.idle(crouched);
        setState(next);
        stateTime += dt;

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
        if (action != null || jumping || height > 0) return false;
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

    /** Position on the ground (where the shadow goes). */
    public Vec3 ground() { return position; }

    public double height() { return height; }

    public void setPosition(Vec3 p) {
        position = new Vec3(p.x(), 0, p.z());
    }

    public double heading() { return heading; }

    public AnimState state() { return state; }

    public double stateTime() { return stateTime; }

    public Wardrobe wardrobe() { return wardrobe; }

    public LayerStack.Memory memory() { return memory; }

    /** What she carries; the selected hotbar slot is what is in her hands ({@link World#equipSelected}). */
    public Inventory inventory() { return inventory; }

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
        position = new Vec3(centre.x() + dx / d * radius, 0, centre.z() + dz / d * radius);
    }

    public double speed() { return velocity.horizontalLength(); }
}
