package com.larsons.game.gfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Every program gets the uniform its loops are counted with, right after its version line. */
class ShaderSourceTest {

    @Test
    void theNoUnrollUniformGoesRightAfterTheVersionLine() {
        String s = Shader.withNoUnroll("#version 330 core\nout vec4 c;\nvoid main() { c = vec4(1.0); }\n");
        assertTrue(s.startsWith("#version 330 core\nuniform int uNoUnroll;"), s);
        assertTrue(s.endsWith("out vec4 c;\nvoid main() { c = vec4(1.0); }\n"));
        assertEquals(1, s.split("uniform int uNoUnroll", -1).length - 1);
    }

    @Test
    void aSourceWithoutAVersionLineGetsItFirst() {
        assertTrue(Shader.withNoUnroll("void main() {}").startsWith("uniform int uNoUnroll;"));
    }

    @Test
    void theWorldsBigShadersCountTheirLoopsSoTheDriverCannotUnrollThem() throws Exception {
        int loops = 0;
        for (Class<?> k : new Class<?>[]{com.larsons.game.world.VoidRenderer.class, SceneLight.class,
                com.larsons.game.world.GroundTexture.class}) {
            for (String name : new String[]{"FRAGMENT", "GLSL"}) {
                String src;
                try {
                    java.lang.reflect.Field f = k.getDeclaredField(name);
                    f.setAccessible(true);
                    src = (String) f.get(null);
                } catch (NoSuchFieldException e) {
                    continue;
                }
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("for \\(int \\w+ = [^;]+; \\w+ <=? ([^;]+);").matcher(src);
                while (m.find()) {
                    String bound = m.group(1);
                    loops++;
                    assertTrue(bound.contains("uNoUnroll") || bound.matches("min\\(u\\w+, \\d+\\)"),
                            k.getSimpleName() + ": a loop counted to " + bound + " is unrolled by the driver");
                }
            }
        }
        assertTrue(loops >= 10, "found " + loops + " loops");
    }
}
