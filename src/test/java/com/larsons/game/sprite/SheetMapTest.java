package com.larsons.game.sprite;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** An item folder's sheets, kept as two arrays of shared names: still a map of keys to files. */
class SheetMapTest {

    @Test
    void itIsTheMapItWasMadeFromWithItsKeysInOrder() {
        Path folder = Path.of("assets", "sprites", "equipment", "sword");
        Map<String, String> sheets = new LinkedHashMap<>();
        sheets.put("walk_side_e", "walk_side_e.png");
        sheets.put("attack_low_n", "low/attack_low_n.png");
        sheets.put("idle_side_s", "idle_side_s.png");
        Map<String, Path> m = SpriteLibrary.SheetMap.of(folder, sheets);
        assertEquals(3, m.size());
        assertEquals(folder.resolve("walk_side_e.png"), m.get("walk_side_e"));
        assertEquals(folder.resolve("low/attack_low_n.png"), m.get("attack_low_n"));
        assertTrue(m.containsKey("idle_side_s"));
        assertNull(m.get("run_side_w"));
        assertFalse(m.containsKey(null));
        assertNull(m.get(42));
        List<String> keys = new ArrayList<>(m.keySet());
        assertEquals(List.of("attack_low_n", "idle_side_s", "walk_side_e"), keys);
        Map<String, Path> plain = new java.util.TreeMap<>();
        sheets.forEach((k, v) -> plain.put(k, folder.resolve(v)));
        assertEquals(plain, m);
        assertEquals(plain.hashCode(), m.hashCode());
    }

    @Test
    void anEmptyFolderIsAnEmptyMap() {
        assertTrue(SpriteLibrary.SheetMap.of(Path.of("x"), Map.of()).isEmpty());
    }
}
