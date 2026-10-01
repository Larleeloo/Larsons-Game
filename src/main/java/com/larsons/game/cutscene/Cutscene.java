package com.larsons.game.cutscene;

import com.larsons.game.sprite.Slot;
import com.larsons.game.sprite.Wardrobe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A cutscene: actors and a timeline of cues, read from a script, played by
 * {@link #update}. What is on screen is the actors ({@link #actors()}), drawn
 * by {@link CutsceneStage}, and the line being spoken ({@link #line()}).
 *
 * <h2>The script</h2>
 * One command a line; {@code #} starts a comment; text is in double quotes.
 * <pre>
 *   actor &lt;id&gt; "&lt;name&gt;" player | &lt;slot&gt;=&lt;item&gt; ... [colour.&lt;slot&gt;=&lt;colour&gt; ...] [style=px128|px64]
 *   stand &lt;id&gt; left | right
 *   say &lt;id&gt; [&lt;clip&gt;] "&lt;text&gt;" [seconds]
 *   clip &lt;id&gt; &lt;clip&gt; [seconds]
 *   colour &lt;id&gt; &lt;slot&gt; &lt;option&gt; | #rrggbb | own | reset [seconds]
 *   wear &lt;id&gt; &lt;slot&gt; &lt;item&gt; | none
 *   flash &lt;id&gt; | all #rrggbb [seconds]
 *   tint &lt;id&gt; | all #rrggbb | none [amount] [seconds]
 *   fade &lt;id&gt; | all &lt;alpha&gt; [seconds]
 *   wait &lt;seconds&gt;
 *   loop
 * </pre>
 * An actor is the player's own character ({@code player}: a copy of their
 * wardrobe, so nothing the scene does to it is saved) or dressed item by item
 * like the wardrobe file. {@code say} plays the clip ({@code talk} by default)
 * for the length of the line - as long as it takes to read, unless given - and
 * the speaker goes back to {@code listen} after; it is the only command, with
 * {@code wait}, that takes time. Everything else starts and carries on while
 * the script moves on: {@code colour} fades a layer to another colour - an
 * option of the item's, any colour at all, the item's {@code own}, or what it
 * was at the start ({@code reset}) - {@code flash} and {@code tint} light the
 * actors, {@code clip} plays a clip (for a while, or until another), and
 * {@code wear} changes what an actor wears. {@code loop} starts over, every
 * actor as it began.
 */
public final class Cutscene {

    /** A line being spoken: by whom, what, and since when. */
    public record Line(Actor speaker, String text, double start, double seconds) {

        /** How much of the text is shown {@code now} (it types itself out). */
        public String shown(double now) {
            int n = (int) Math.min(text.length(), Math.max(0, (now - start) * CHARS_PER_SECOND));
            return text.substring(0, n);
        }
    }

    /** How fast a line types itself out. */
    public static final double CHARS_PER_SECOND = 48;
    /** The clip a line is spoken with when the script names none. */
    public static final String TALK = "talk";

    private interface Cue {
        /** Run the cue; the seconds the timeline then waits. */
        double run(Cutscene cs);
    }

    private final Map<String, ActorSpec> specs = new LinkedHashMap<>();
    private final Map<String, Actor> actors = new LinkedHashMap<>();
    private final List<Cue> cues = new ArrayList<>();
    private final Supplier<Wardrobe> player;
    private boolean loops;
    private int next;
    private double now, resume;
    private Line line;
    private boolean finished;

    private record ActorSpec(String id, String name, boolean player, Wardrobe wardrobe) {}

    private Cutscene(Supplier<Wardrobe> player) {
        this.player = player;
    }

    /**
     * Read a script. {@code player} supplies the player's wardrobe for actors
     * that are the player's character (copied as the scene starts and at every
     * loop). Throws {@link IllegalArgumentException} naming the line on a
     * mistake.
     */
    public static Cutscene parse(String script, Supplier<Wardrobe> player) {
        Cutscene cs = new Cutscene(player);
        String[] lines = script.split("\\R");      // \n, or \r\n as git on Windows checks it out
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            List<String> t = tokens(raw);
            if (t.isEmpty()) continue;
            try {
                cs.read(t);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("line " + (i + 1) + ": " + e.getMessage() + ": " + raw.trim(), e);
            }
        }
        cs.restart();
        return cs;
    }

    private void read(List<String> t) {
        String cmd = t.get(0);
        switch (cmd) {
            case "actor" -> {
                String id = t.get(1), name = t.get(2).startsWith("\"") ? unquote(t.get(2)) : t.get(2);
                boolean isPlayer = t.size() > 3 && t.get(3).equals("player");
                Wardrobe w = new Wardrobe();
                w.setStyle(Wardrobe.Style.PIXEL_128);
                for (String kv : t.subList(isPlayer ? 4 : 3, t.size())) {
                    String[] p = kv.split("=", 2);
                    if (p.length != 2) throw new IllegalArgumentException("expected slot=item, got " + kv);
                    if (p[0].equals("style")) {
                        w.setStyle(Wardrobe.Style.byKey(p[1]));
                    } else if (p[0].startsWith("colour.") || p[0].startsWith("color.")) {
                        w.setColour(slot(p[0].substring(p[0].indexOf('.') + 1)), p[1]);
                    } else {
                        w.set(slot(p[0]), p[1].equals("none") ? null : p[1]);
                    }
                }
                specs.put(id, new ActorSpec(id, name, isPlayer, w));
            }
            case "stand" -> {
                String id = known(t.get(1));
                Actor.Side side = Actor.Side.valueOf(t.get(2).toUpperCase());
                cues.add(cs -> {
                    cs.actor(id).stand(side);
                    return 0;
                });
            }
            case "say" -> {
                String id = known(t.get(1));
                int q = t.get(2).startsWith("\"") ? 2 : 3;
                String clip = q == 3 ? t.get(2) : TALK;
                String text = unquote(t.get(q));
                double seconds = t.size() > q + 1 ? num(t.get(q + 1))
                        : Math.max(1.8, 1.1 + text.length() / CHARS_PER_SECOND + text.length() * 0.035);
                cues.add(cs -> {
                    Actor a = cs.actor(id);
                    a.play(clip, seconds);
                    for (Actor other : cs.actors.values()) {
                        if (other != a && !isReacting(other)) other.play(Actor.IDLE_CLIP);
                    }
                    cs.line = new Line(a, text, cs.now, seconds);
                    return seconds;
                });
            }
            case "clip" -> {
                String id = known(t.get(1)), clip = t.get(2);
                double seconds = t.size() > 3 ? num(t.get(3)) : Double.POSITIVE_INFINITY;
                cues.add(cs -> {
                    cs.actor(id).play(clip, seconds);
                    return 0;
                });
            }
            case "colour", "color" -> {
                String id = known(t.get(1));
                Slot slot = slot(t.get(2));
                String colour = t.get(3);
                double seconds = t.size() > 4 ? num(t.get(4)) : 0;
                cues.add(cs -> {
                    String c = switch (colour) {
                        case "own" -> null;
                        case "reset" -> cs.startColour(id, slot);
                        default -> colour;
                    };
                    cs.actor(id).colour(slot, c, seconds);
                    return 0;
                });
            }
            case "wear" -> {
                String id = known(t.get(1));
                Slot slot = slot(t.get(2));
                String item = t.get(3).equals("none") ? null : t.get(3);
                cues.add(cs -> {
                    cs.actor(id).wardrobe().set(slot, item);
                    return 0;
                });
            }
            case "flash" -> {
                String who = who(t.get(1));
                int rgb = rgb(t.get(2));
                double seconds = t.size() > 3 ? num(t.get(3)) : 0.3;
                cues.add(cs -> {
                    for (Actor a : cs.select(who)) a.flash(rgb, seconds);
                    return 0;
                });
            }
            case "tint" -> {
                String who = who(t.get(1));
                boolean none = t.get(2).equals("none");
                int rgb = none ? 0xFFFFFF : rgb(t.get(2));
                double amount = none ? 0 : t.size() > 3 ? num(t.get(3)) : 0.3;
                int at = none ? 3 : 4;
                double seconds = t.size() > at ? num(t.get(at)) : 0.5;
                cues.add(cs -> {
                    for (Actor a : cs.select(who)) a.tint(rgb, amount, seconds);
                    return 0;
                });
            }
            case "fade" -> {
                String who = who(t.get(1));
                float alpha = (float) num(t.get(2));
                double seconds = t.size() > 3 ? num(t.get(3)) : 0.5;
                cues.add(cs -> {
                    for (Actor a : cs.select(who)) a.fade(alpha, seconds);
                    return 0;
                });
            }
            case "wait" -> {
                double seconds = num(t.get(1));
                cues.add(cs -> {
                    cs.line = null;
                    return seconds;
                });
            }
            case "loop" -> loops = true;
            default -> throw new IllegalArgumentException("unknown command " + cmd);
        }
    }

    private static boolean isReacting(Actor a) {
        return !a.clip().equals(Actor.IDLE_CLIP) && !a.clip().equals(TALK);
    }

    /** Back to the start: every actor dressed and coloured as the script has it, the player as they are now. */
    public void restart() {
        actors.clear();
        for (ActorSpec s : specs.values()) {
            Wardrobe w = s.player() ? player.get().copy() : s.wardrobe().copy();
            actors.put(s.id(), new Actor(s.id(), s.name(), w));
        }
        next = 0;
        now = 0;
        resume = 0;
        line = null;
        finished = false;
    }

    /** Advance the scene by {@code dt} seconds. */
    public void update(double dt) {
        now += dt;
        for (Actor a : actors.values()) a.update(dt);
        if (line != null && now >= line.start() + line.seconds()) line = null;
        while (!finished && now >= resume) {
            if (next >= cues.size()) {
                if (loops && !cues.isEmpty()) {
                    restart();
                    continue;
                }
                finished = true;
                break;
            }
            double wait = cues.get(next++).run(this);
            resume = now + wait;
            if (wait > 0) break;
        }
    }

    /** The actors, in the order the script introduces them (and draws them). */
    public List<Actor> actors() { return List.copyOf(actors.values()); }

    public Actor actor(String id) {
        Actor a = actors.get(id);
        if (a == null) throw new IllegalArgumentException("no actor " + id);
        return a;
    }

    /** The line being spoken, or null. */
    public Line line() { return line; }

    /** Seconds since the scene (or its last loop) started. */
    public double now() { return now; }

    /** Whether a scene that does not loop has run out. */
    public boolean finished() { return finished; }

    public boolean loops() { return loops; }

    /** Play the scene through once, even if its script loops. */
    public void playOnce() { loops = false; }

    /** {@link #parse} a script file. */
    public static Cutscene load(java.nio.file.Path file, Supplier<Wardrobe> player) throws java.io.IOException {
        return parse(java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8), player);
    }

    // --- parsing helpers ---------------------------------------------------------------

    private String startColour(String id, Slot slot) {
        ActorSpec s = specs.get(id);
        return s.player() ? player.get().colour(slot) : s.wardrobe().colour(slot);
    }

    private List<Actor> select(String who) {
        return who.equals("all") ? List.copyOf(actors.values()) : List.of(actor(who));
    }

    private String known(String id) {
        if (!specs.containsKey(id)) throw new IllegalArgumentException("no actor " + id + " (declare it first)");
        return id;
    }

    private String who(String id) {
        return id.equals("all") ? id : known(id);
    }

    private static Slot slot(String key) {
        Slot s = Slot.byKey(key);
        if (s == null) throw new IllegalArgumentException("no slot " + key);
        return s;
    }

    private static double num(String s) {
        return Double.parseDouble(s);
    }

    private static int rgb(String s) {
        if (!s.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("expected #rrggbb, got " + s);
        return Integer.parseInt(s.substring(1), 16);
    }

    private static String unquote(String s) {
        if (!s.startsWith("\"")) throw new IllegalArgumentException("expected \"text\", got " + s);
        return s.substring(1, s.length() - 1);
    }

    /** Words, a double-quoted string as one (quotes kept), up to a # outside quotes. */
    static List<String> tokens(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                cur.append(c);
                if (c == '"') {
                    quoted = false;
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else if (c == '"') {
                if (!cur.isEmpty()) out.add(cur.toString());
                cur.setLength(0);
                cur.append(c);
                quoted = true;
            } else if (c == '#' && cur.isEmpty() && !colourAt(line, i)) {
                break;                 // a comment; a word that is a colour (#rrggbb) is not one
            } else if (Character.isWhitespace(c)) {
                if (!cur.isEmpty()) out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (quoted) throw new IllegalArgumentException("unclosed quote");
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    /** Whether a {@code #rrggbb} colour - a word, not a comment - starts at {@code i} of the line. */
    private static boolean colourAt(String line, int i) {
        int end = i + 7;
        if (end > line.length() || (end < line.length() && !Character.isWhitespace(line.charAt(end)))) return false;
        for (int j = i + 1; j < end; j++) {
            if (Character.digit(line.charAt(j), 16) < 0) return false;
        }
        return true;
    }

}
