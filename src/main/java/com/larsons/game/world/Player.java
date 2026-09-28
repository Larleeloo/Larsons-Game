package com.larsons.game.world;

import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.Facing;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Wardrobe;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * The player character: a position on the ground, a heading, a little jump
 * physics, and the animation state machine that picks which sprite set plays.
 *
 * <pre>
 *   airborne or mid-jump  → jump
 *   swinging              → attack
 *   moving                → walk / run (Shift) / sprint (Ctrl)
 *   otherwise             → idle
 * </pre>
 *
 * <p>For looking at the art rather than playing, a state can be
 * <em>previewed</em>: it loops in place (jump and attack included) until the
 * player moves or clears it.
 */
public final class Player {

    public static final double WALK_SPEED = 1.7;
    public static final double RUN_SPEED = 4.2;
    public static final double SPRINT_SPEED = 7.0;
    public static final double GRAVITY = 15.0;
    public static final double TURN_RATE = 14.0;
    /** When a jump leaves the ground, as a fraction of its animation — after the crouch. */
    public static final double JUMP_LAUNCH = 0.2;
    /** How much of the jump animation is spent in the air. */
    public static final double JUMP_AIRTIME = 0.62;

    /** What the controls ask for this frame. */
    public record Intent(double moveX, double moveZ, boolean run, boolean sprint,
                         boolean jump, boolean attack) {
        public static final Intent NONE = new Intent(0, 0, false, false, false, false);

        boolean moving() {
            return Math.abs(moveX) > 1e-3 || Math.abs(moveZ) > 1e-3;
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

    private boolean jumping, launched;
    private double actionDuration = 0.6;

    private final Wardrobe wardrobe;
    private final LayerStack.Memory memory = new LayerStack.Memory();
    private final Set<String> inventory = new LinkedHashSet<>();

    public Player(Wardrobe wardrobe) {
        this.wardrobe = wardrobe;
    }

    /**
     * Advance one frame. {@code durations} says how long each state's
     * animation lasts for this character right now (from its sprite sheets),
     * which is how long a jump or a swing takes.
     */
    public void update(double dt, Intent in, ToDoubleFunction<AnimState> durations) {
        if (preview != null && (in.moving() || in.jump() || in.attack())) preview = null;
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
        boolean attacking = state == AnimState.ATTACK && stateTime < actionDuration;

        if (in.attack() && grounded && !attacking && !jumping) {
            setState(AnimState.ATTACK);
            attacking = true;
            actionDuration = durations.applyAsDouble(AnimState.ATTACK);
        }
        if (in.jump() && grounded && !jumping && !attacking) {
            jumping = true;
            launched = false;
            setState(AnimState.JUMP);
            actionDuration = durations.applyAsDouble(AnimState.JUMP);
        }

        // Movement, relative to the camera (the caller already rotated it).
        double speed = in.sprint() ? SPRINT_SPEED : in.run() ? RUN_SPEED : WALK_SPEED;
        Vec3 wish = new Vec3(in.moveX(), 0, in.moveZ());
        if (wish.length() > 1) wish = wish.normalize();
        if (attacking) wish = Vec3.ZERO;
        Vec3 target = wish.scale(speed);
        velocity = velocity.lerp(target, 1 - Math.exp(-dt * (grounded ? 14 : 3)));
        position = position.add(velocity.scale(dt));
        if (in.moving() && !attacking) wantHeading = SpriteView.headingOf(wish.x(), wish.z());

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
        if (jumping) next = AnimState.JUMP;
        else if (attacking) next = AnimState.ATTACK;
        else if (velocity.horizontalLength() > 0.25 && in.moving()) {
            next = in.sprint() ? AnimState.SPRINT : in.run() ? AnimState.RUN : AnimState.WALK;
        } else next = AnimState.IDLE;
        setState(next);
        stateTime += dt;
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

    // --- preview and posing ----------------------------------------------------------

    /** Loop {@code s} in place (null to go back to live states). */
    public void preview(AnimState s) {
        preview = s;
        if (s != null) {
            jumping = false;
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

    public Set<String> inventory() { return inventory; }

    public double speed() { return velocity.horizontalLength(); }
}
