package com.larsons.game.overworld;

import com.larsons.game.level.FolkTypes;
import com.larsons.game.level.Level;
import com.larsons.game.level.LevelIO;
import com.larsons.game.level.LevelObject;
import com.larsons.game.math.Vec3;
import com.larsons.game.story.Quest;
import com.larsons.game.story.Quests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Where on a map the missions she follows are to be done: for each
 * destination with a level, the quests that level has and the people in
 * it; and from those and a save's quests, which destinations to mark -
 * where a mission under way has its ring (or whoever is to be talked to),
 * and, once it is done, where whoever it is handed in to is.
 *
 * <p>A level is read once (until its file changes): the map is opened often.
 */
public final class Missions {

    private Missions() {}

    /** What a level has that a mission can be about. */
    private record Has(long stamp, List<Quest> quests, Set<String> people) {}

    private static final Map<Path, Has> READ = new HashMap<>();

    private static Has read(Path folder) {
        Path file = folder.resolve("level.json");
        long stamp;
        try {
            stamp = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return null;
        }
        Has had = READ.get(folder);
        if (had != null && had.stamp == stamp) return had;
        List<Quest> quests = new ArrayList<>();
        Set<String> people = new HashSet<>();
        try {
            Level level = LevelIO.load(folder);
            for (LevelObject o : level.objects()) {
                if (FolkTypes.QUEST.equals(o.type())) {
                    quests.add(Quest.of(level.name(), o.id(), new Vec3(o.x(), 0, o.z()), o.props()));
                } else if (FolkTypes.PERSON.equals(o.type())) {
                    String name = o.str("name", "").strip().toLowerCase(Locale.ROOT);
                    if (!name.isEmpty()) people.add(name);
                }
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
        Has has = new Has(stamp, quests, people);
        READ.put(folder, has);
        return has;
    }

    /** A destination marked: the missions to be done there, and whether one of them is ready to hand in. */
    public record Mark(List<String> titles, boolean ready) {}

    /**
     * The destinations of {@code map} to mark for the missions followed in
     * {@code store} (a save's quests), by destination id. {@code levels} is
     * the folder the levels are in.
     */
    public static Map<String, Mark> marks(Overworld map, Path levels, Map<String, Object> store) {
        Map<String, Mark> out = new LinkedHashMap<>();
        List<Quests.Elsewhere> followed = Quests.elsewhere(store, null);
        if (followed.isEmpty()) return out;
        for (Quests.Elsewhere m : followed) {
            boolean ready = m.state() == Quests.State.READY;
            List<String> best = new ArrayList<>(), any = new ArrayList<>();
            for (Overworld.Node n : map.nodes()) {
                if (!n.playable()) continue;
                Has has = read(levels.resolve(n.level));
                if (has == null) continue;
                for (Quest q : has.quests) {
                    if (!q.id().equals(m.id())) continue;
                    any.add(n.id);
                    String who = (ready ? q.turnIn() : q.kind() == Quest.Kind.TALK ? q.target() : "")
                            .strip().toLowerCase(Locale.ROOT);
                    boolean here = ready || q.kind() == Quest.Kind.TALK ? has.people.contains(who)
                            : q.radius() > 0 || q.kind() == Quest.Kind.FETCH;
                    if (here) best.add(n.id);
                }
            }
            for (String node : best.isEmpty() ? any : best) {
                Mark was = out.get(node);
                List<String> titles = was == null ? new ArrayList<>() : new ArrayList<>(was.titles());
                titles.add(m.title());
                out.put(node, new Mark(titles, ready || was != null && was.ready()));
            }
        }
        return out;
    }
}
