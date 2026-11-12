package com.larsons.game.actor;

import com.larsons.game.combat.Combat;
import com.larsons.game.combat.Move;
import com.larsons.game.combat.Target;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.AnimState;
import com.larsons.game.sprite.LayerStack;
import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.SpriteView;
import com.larsons.game.sprite.Stance;
import com.larsons.game.sprite.Wardrobe;
import com.larsons.game.world.Item;

import java.util.Map;

/**
 * One of the folk of the world: someone to talk to, or someone to fight. A
 * person of one of the {@link People} is drawn from the character's own
 * sheets - a {@link Wardrobe} of her own, any weapon in her hands, taller or
 * smaller - and plays the character's states; a Bortzal is a creature of its
 * own sheets ({@code assets/sprites/objects/bortzal_*}). Either way it is a
 * {@link Target}: her blows and shots land on it, and {@link Folks} thinks
 * for it - where it goes, when it swings, when it guards.
 *
 * <p>What it does is its {@link Mode}; what it is seen doing is its {@link
 * Act}, which a person turns into the {@link AnimState} of the stance of
 * what she holds and a creature into one of its sheets.
 */
public final class Folk implements Target {

    /** What it is to her. */
    public enum Role {
        /** Fights her (unless it has been told not to, until she hits it). */
        FOE,
        /** Someone to talk to: never fights, cannot be hurt. */
        PERSON,
        /** One of the horde: comes for her wherever she is. */
        RISEN
    }

    /** What it is doing. */
    public enum Mode { STAND, WANDER, CHASE, STRIKE, LOOSE, RECOVER, GUARD, STAGGER, DOWN }

    /** What it is seen doing. */
    public enum Act { IDLE, WALK, RUN, ATTACK, LOOSE, GUARD, SPIT, HURT }

    /** How long a hit's white flash lasts; how long one felled lies before it fades, and the fade, seconds. */
    public static final double FLASH = 0.14, LIES_FOR = 5, FADE = 1.2;
    /** A creature's sheets: how long its blow, its flinch and its fall take, and when in the blow it lands. */
    public static final double CREATURE_ATTACK = 0.7, CREATURE_BLOW = 0.4, CREATURE_HURT = 0.3, CREATURE_FALL = 0.6;
    /** How fast a creature's walk and run were drawn going, metres a second at its usual size. */
    public static final double CREATURE_WALK = 0.875, CREATURE_RUN = 2.78;
    /** The Bortzal's skins: goblin yellow and ashy purple. */
    public static final int GOBLIN_YELLOW = 0xC8B428, ASHY_PURPLE = 0x8E7AA8, GRAVE_GREEN = 0x7A9A6A;
    /** The Risen's skins: grave green, ashen grey, bruised blue, a sallow yellow. */
    public static final int[] RISEN_SKINS = {GRAVE_GREEN, 0x8C8A84, 0x6E7A96, 0x9A9A6A, GRAVE_GREEN};

    private final int id;
    private final Breed breed;
    private final People people;
    private final Role role;
    private final boolean masculine;
    private final Wardrobe wardrobe;
    private final String sprite;
    private final LayerStack.Memory memory = new LayerStack.Memory();
    private String name;
    private int tint = -1;
    private double size = 1;
    private Item weapon;
    private Stance stance = Stance.BLADE;
    private Move.Kind shot;
    private Map<String, Object> props = Map.of();

    double x, y, z, heading, homeX, homeZ, roam = 4;
    double health, maxHealth;
    double walk = 1.5, run = 3.8, damage = 8, sight = 16, every = 1.6, guard, reach = 1.45, keep;
    boolean hostile = true, relentless, steady, crouched, alerted, guardedHit, hasVia, barred;
    double viaX, viaZ;
    Mode mode = Mode.STAND;
    double modeTime, timer, cooldown, blowAt, strikeFor, frozen, vx, vz, mvx, mvz, downFor, lostFor;
    double sinceHurt = Double.MAX_VALUE, alpha = 1;
    boolean struck, gone, talking;
    double toX, toZ;

    private Act act = Act.IDLE;
    private AnimState state = AnimState.IDLE;
    private String sheet = "idle";
    private double stateTime, rate = 1;

    /**
     * @param wardrobe how a person looks; null for a creature
     * @param sprite   a creature's sheets ({@code assets/sprites/objects/<sprite>}); null for a person
     */
    public Folk(int id, Breed breed, People people, Role role, boolean masculine, Wardrobe wardrobe, String sprite,
                Vec3 at, double heading) {
        this.id = id;
        this.breed = breed;
        this.people = people;
        this.role = role;
        this.masculine = masculine;
        this.wardrobe = wardrobe;
        this.sprite = sprite;
        this.x = at.x();
        this.y = at.y();
        this.z = at.z();
        this.homeX = toX = x;
        this.homeZ = toZ = z;
        this.heading = heading;
        this.health = this.maxHealth = breed == null ? 50 : breed.health();
        if (wardrobe != null) {
            stance = Stance.ofWardrobe(wardrobe);
            state = stance.idle(false);
        }
    }

    // --- who it is ---------------------------------------------------------------------

    /** The level object it is (a summoned one: a number of its own). */
    public int id() { return id; }

    /** Its fighting kind, or null (a person who is no one's foe). */
    public Breed breed() { return breed; }

    public People people() { return people; }

    public Role role() { return role; }

    public boolean masculine() { return masculine; }

    @Override
    public String name() {
        return name != null && !name.isBlank() ? name : breed != null ? breed.label() : people.label();
    }

    public Folk named(String n) {
        name = n == null || n.isBlank() ? null : n.strip();
        return this;
    }

    /** Whether it was given a name of its own. */
    public boolean hasName() { return name != null; }

    /** What its level object keeps for it: what it says, what it asks of her. */
    public Map<String, Object> props() { return props; }

    public Folk with(Map<String, Object> p) {
        props = p == null ? Map.of() : p;
        return this;
    }

    public String str(String key, String fallback) {
        Object v = props.get(key);
        return v == null || v.toString().isBlank() ? fallback : v.toString();
    }

    public double num(String key, double fallback) {
        Object v = props.get(key);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return v == null ? fallback : Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // --- how it looks ------------------------------------------------------------------

    /** Whether it is drawn from sheets of its own (a Bortzal) rather than the character's. */
    public boolean creature() { return sprite != null; }

    public String sprite() { return sprite; }

    public Wardrobe wardrobe() { return wardrobe; }

    public LayerStack.Memory memory() { return memory; }

    /** Its skin's colour ({@code 0xRRGGBB}) for a creature, or -1: as drawn. */
    public int tint() { return tint; }

    public Folk tinted(int rgb) {
        tint = rgb;
        return this;
    }

    /** How tall it stands, times its kind's usual height. */
    public double size() { return size; }

    /** Make it {@code s} times its usual height: bigger hits harder and takes more. */
    public Folk sized(double s) {
        size = Math.max(0.4, Math.min(1.8, s));
        if (breed != null) {
            maxHealth = Math.round(breed.health() * Math.pow(size, 1.5));
            health = maxHealth;
        }
        tune();
        return this;
    }

    /** Give it {@code times} its health (a level's champion, a quest's quarry). */
    public Folk tougher(double times) {
        maxHealth = Math.max(1, Math.round(maxHealth * times));
        health = maxHealth;
        return this;
    }

    /** The icon over its head ({@code assets/cards/icons.png}), or null: none. */
    public String icon() { return icon; }

    public void setIcon(String key) {
        icon = key;
    }

    private String icon;

    /** What it holds, or null (bare hands; a creature's weapon is in its sheets). */
    public Item weapon() { return weapon; }

    public Stance stance() { return stance; }

    /** Put {@code item} in its hands (null: nothing): any weapon there is. */
    public Folk arm(Item item) {
        weapon = item;
        if (wardrobe != null) {
            wardrobe.clear(Slot.CARRY_RIGHT);
            if (item != null && item.def().stance() == Stance.SWORD) wardrobe.set(Slot.CARRY_RIGHT, Wardrobe.WEAPON);
            stance = item == null || item.def().stance() == Stance.SWORD ? Stance.ofWardrobe(wardrobe)
                    : item.def().stance();
        }
        tune();
        act = null;
        act(Act.IDLE, 0);
        return this;
    }

    /** A creature that shoots (a Bortzal with a bow). */
    Folk archer() {
        shot = Move.Kind.ARROW;
        keep = 10;
        every = (breed == null ? 1.7 : breed.every()) * 1.3;
        return this;
    }

    /** Work out what its blow does from what it holds and how big it is. */
    private void tune() {
        double base = breed == null ? 8 : breed.damage(), wait = breed == null ? 1.6 : breed.every();
        double dmg = weapon == null ? 0.85 : 1, slow = 1;
        reach = 1.4;
        keep = 0;
        if (!creature()) shot = null;
        if (breed != null && breed.spits()) {
            shot = Move.Kind.SPELL;
            keep = 8;
        } else if (!creature()) {
            switch (stance) {
                case AXE -> {
                    dmg = 1.5;
                    slow = 1.4;
                    reach = 1.7;
                }
                case MACE -> {
                    dmg = 1.2;
                    slow = 1.15;
                    reach = 1.5;
                }
                case DAGGER -> {
                    dmg = 0.7;
                    slow = 0.65;
                    reach = 1.25;
                }
                case BOW -> {
                    shot = Move.Kind.ARROW;
                    dmg = 0.9;
                    slow = 1.3;
                    keep = 11;
                }
                case CROSSBOW -> {
                    shot = Move.Kind.BOLT;
                    dmg = 1.25;
                    slow = 1.7;
                    keep = 10;
                }
                case WAND -> {
                    shot = Move.Kind.SPELL;
                    dmg = 0.85;
                    slow = 1.1;
                    keep = 9;
                }
                case STAFF -> {
                    shot = Move.Kind.SPELL;
                    dmg = 1.1;
                    slow = 1.5;
                    keep = 10;
                }
                default -> { }
            }
        } else if (shot != null) {
            keep = 10;
            slow = 1.3;
        }
        damage = Math.max(1, Math.round(base * dmg * size));
        every = wait * slow;
        reach *= 0.7 + 0.3 * size;
    }

    /** What it shoots - an arrow, a bolt, a spell - or null: it fights hand to hand. */
    public Move.Kind shot() { return shot; }

    /** The colour of the spell it casts (or what it spits). */
    public int shotColour() {
        if (breed != null && breed.spits()) return 0x7EE728;
        return weapon == null ? 0xC86FFF : Combat.spellColour(weapon);
    }

    // --- a target ----------------------------------------------------------------------

    @Override
    public Vec3 position() { return new Vec3(x, y, z); }

    public double heading() { return heading; }

    @Override
    public double radius() { return (creature() ? 0.4 : 0.32) * Math.max(0.6, size); }

    @Override
    public double height() { return (crouched ? 0.95 : creature() ? 1.7 : 1.75) * size; }

    @Override
    public boolean alive() { return health > 0 && mode != Mode.DOWN && role != Role.PERSON; }

    /** Whether it is still about (a person always; a foe until it is felled). */
    public boolean standing() { return mode != Mode.DOWN; }

    @Override
    public double health() { return health; }

    @Override
    public double maxHealth() { return maxHealth; }

    @Override
    public void hurt(double amount, Vec3 from, boolean react) {
        if (!alive()) return;
        if (mode == Mode.GUARD && from != null && react) {
            Vec3 f = SpriteView.headingVector(heading);
            double dx = from.x() - x, dz = from.z() - z, d = Math.hypot(dx, dz);
            if (d > 1e-6 && (dx * f.x() + dz * f.z()) / d > 0.2) {
                amount *= 0.3;
                guardedHit = true;
            }
        }
        health -= amount;
        sinceHurt = 0;
        alerted = true;
        hostile = true;
        if (health <= 0) {
            health = 0;
            fall();
            return;
        }
        if (react && !steady && from != null && !guardedHit) {
            double dx = x - from.x(), dz = z - from.z(), d = Math.hypot(dx, dz);
            if (d > 1e-6) {
                vx = dx / d * 2.6;
                vz = dz / d * 2.6;
            }
            if (mode != Mode.STRIKE || modeTime < blowAt * 0.6) {
                enter(Mode.STAGGER);
                act(Act.HURT, 0);
            }
        }
    }

    /** Felled: over it goes. */
    void fall() {
        enter(Mode.DOWN);
        downFor = 0;
        if (creature()) {
            sheet = "fall";
            stateTime = 0;
            rate = 1;
        }
    }

    @Override
    public Vec3 velocity() { return new Vec3(mvx, 0, mvz); }

    @Override
    public double sinceHurt() { return sinceHurt; }

    @Override
    public void freeze(double seconds) {
        frozen = Math.max(frozen, seconds);
    }

    @Override
    public boolean frozen() { return frozen > 0; }

    /** How white it is drawn this frame (0 - 1): just struck. */
    public double flash() {
        return sinceHurt < FLASH ? 1 - sinceHurt / FLASH : 0;
    }

    /** How far over a felled one has gone (0 - 1). */
    public double fallen() {
        return mode == Mode.DOWN ? Math.min(1, downFor / 0.45) : 0;
    }

    public double alpha() { return alpha; }

    /** Gone for good: felled and faded. */
    public boolean gone() { return gone; }

    /** Take it out of the world now. */
    public void remove() {
        gone = true;
    }

    public Mode mode() { return mode; }

    /** Whether it fights her now. */
    public boolean hostile() { return hostile && role != Role.PERSON; }

    public Folk peaceful() {
        hostile = false;
        return this;
    }

    /** Whether it has noticed her. */
    public boolean alerted() { return alerted; }

    /** Come for her from wherever it is, and never give up (the horde's). */
    public Folk relentless() {
        relentless = true;
        alerted = true;
        return this;
    }

    /**
     * Go by way of {@code p} before coming for her (the gap in a wall the
     * horde comes through; null: straight for her). While {@code barred} it
     * stops there and tears at what bars the way.
     */
    public Folk via(Vec3 p, boolean barred) {
        hasVia = p != null;
        if (p != null) {
            viaX = p.x();
            viaZ = p.z();
        }
        this.barred = barred && p != null;
        return this;
    }

    public boolean hasVia() { return hasVia; }

    /** Whether it is at what bars its way, tearing at it. */
    public boolean atBar() {
        return hasVia && barred && Math.hypot(viaX - x, viaZ - z) < 1.25 && alive();
    }

    /** Keep within {@code metres} of where it was put when it has nothing to do (0: stand there). */
    public Folk roaming(double metres) {
        roam = Math.max(0, metres);
        return this;
    }

    /** She is talking to it (or has stopped): it stands and faces her. */
    public void setTalking(boolean on) {
        talking = on;
    }

    public void faceTo(Vec3 at) {
        double dx = at.x() - x, dz = at.z() - z;
        if (dx * dx + dz * dz > 1e-6) heading = SpriteView.headingOf(dx, dz);
    }

    /** Whether she, standing at {@code at}, is near enough to talk to it. */
    public boolean inReach(Vec3 at) {
        return standing() && Math.hypot(at.x() - x, at.z() - z) <= 2.2 && Math.abs(at.y() - y) <= 1.6;
    }

    void enter(Mode m) {
        mode = m;
        modeTime = 0;
        struck = false;
    }

    // --- what it is seen doing ---------------------------------------------------------

    /** Be seen doing {@code a}, going at {@code speed} metres a second (a walk's and a run's feet keep to the ground). */
    void act(Act a, double speed) {
        boolean changed = a != act;
        act = a;
        if (creature()) {
            String s = switch (a) {
                case WALK -> "walk";
                case RUN -> "run";
                case ATTACK, LOOSE, SPIT -> "attack";
                case HURT -> "hurt";
                default -> "idle";
            };
            if (!s.equals(sheet) || changed) stateTime = 0;
            sheet = s;
            double pace = (a == Act.RUN ? CREATURE_RUN : CREATURE_WALK) * size;
            rate = a == Act.WALK || a == Act.RUN ? Math.max(0.4, Math.min(2.4, speed / pace)) : 1;
            return;
        }
        AnimState s = switch (a) {
            case WALK -> stance.moving(Stance.Pace.WALK, crouched);
            case RUN -> stance.moving(Stance.Pace.RUN, crouched);
            case ATTACK -> orElse(stance.attack(false), AnimState.ATTACK);
            case LOOSE -> orElse(stance.release(false), orElse(stance.attack(false), AnimState.ATTACK));
            case GUARD -> orElse(stance.block(), orElse(stance.parry(), stance.idle(crouched)));
            case SPIT -> AnimState.EMOTE_ANGRY;
            default -> stance.idle(crouched);
        };
        if (s != state || changed) stateTime = 0;
        state = s;
        double pace = s.speed() * size;
        rate = (a == Act.WALK || a == Act.RUN) && pace > 0 ? Math.max(0.35, Math.min(1.8, speed / pace)) : 1;
    }

    private static AnimState orElse(AnimState s, AnimState other) {
        return s == null ? other : s;
    }

    void advance(double dt) {
        stateTime += dt * rate;
    }

    public Act act() { return act; }

    /** The state a person plays now. */
    public AnimState state() { return state; }

    /** The sheet a creature plays now. */
    public String sheet() { return sheet; }

    /** Whether a creature's sheet repeats. */
    public boolean loops() {
        return sheet.equals("idle") || sheet.equals("walk") || sheet.equals("run");
    }

    /** How far into its state or sheet it is, seconds. */
    public double stateTime() { return stateTime; }
}
