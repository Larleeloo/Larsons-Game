package com.larsons.game.gfx;

import com.larsons.game.math.Mat4;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/**
 * A linked GLSL 3.30 core program with a cached uniform table.
 *
 * <p>Compile and link failures throw with the driver's log attached — a shader
 * that fails quietly draws nothing and says nothing, and that is the hardest
 * failure a GPU renderer has to diagnose.
 *
 * <p><b>Loops the driver cannot unroll.</b> Every program has {@code uniform
 * int uNoUnroll}, never set, so always 0: a loop counted to {@code 8 +
 * uNoUnroll} is the same loop, but its count is no longer known when the
 * program is built, so the driver keeps it a loop instead of copying its body
 * out eight times. Apple's OpenGL (on Metal, an M1's) builds a program at its
 * first draw, and the world's big shaders - the sky and the ground with
 * every function copied in and every loop unrolled - took it over a minute
 * there: the game stood still on its first frame. The world's shaders count
 * their loops this way.
 */
public final class Shader implements AutoCloseable {

    /** What every program has after its {@code #version} line ({@link Shader the loops}). */
    static final String NO_UNROLL = "uniform int uNoUnroll;    // never set: always 0 (gfx/Shader)\n";

    /** The programs that took long to get ready at their first draw ({@link #firstDraw}), for a hang report. */
    private static final java.util.List<String> SLOW = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** A first draw slower than this is told on the console, seconds. */
    private static final double SLOW_SECONDS = 0.5;
    private boolean drawn;

    private final int program;
    private final String name;
    private final Map<String, Integer> uniforms = new HashMap<>();

    public Shader(String name, String vertexSource, String fragmentSource) {
        this.name = name;
        int vs = compile(GL_VERTEX_SHADER, vertexSource);
        int fs = compile(GL_FRAGMENT_SHADER, fragmentSource);
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("shader '" + name + "' failed to link:\n" + log);
        }
        // the sun's shadow map has its own unit, in every program that can read it - left at unit 0 it would
        // share the unit of a texture of another kind, and nothing it draws would be drawn
        int shadow = glGetUniformLocation(program, "uShadowMap");
        if (shadow >= 0) {
            int was = glGetInteger(GL_CURRENT_PROGRAM);
            glUseProgram(program);
            glUniform1i(shadow, SceneLight.SHADOW_UNIT);
            glUseProgram(was);
        }
    }

    /** {@code source} with {@link #NO_UNROLL} after its {@code #version} line. */
    static String withNoUnroll(String source) {
        if (!source.startsWith("#version")) return NO_UNROLL + source;
        int nl = source.indexOf('\n');
        return nl < 0 ? source + "\n" + NO_UNROLL : source.substring(0, nl + 1) + NO_UNROLL + source.substring(nl + 1);
    }

    /**
     * Run {@code draw} - this program's draw call - and the first time, wait
     * for it and say how long it took if it was slow: Apple's OpenGL builds a
     * program for the GPU only when it first draws with it, and a big one
     * can take seconds. Kept for a hang report ({@link #slowBuilds}).
     */
    public void firstDraw(Runnable draw) {
        if (drawn) {
            draw.run();
            return;
        }
        drawn = true;
        long t0 = System.nanoTime();
        draw.run();
        glFinish();
        double s = (System.nanoTime() - t0) / 1e9;
        if (s > SLOW_SECONDS) {
            String line = String.format(java.util.Locale.ROOT, "%s took %.1f s at its first draw", name, s);
            SLOW.add(line);
            System.out.println("[gpu] shader " + line + " (the graphics driver builds it then)");
        }
    }

    /** "void took 12.3 s at its first draw; ..." - or "" when none was slow. */
    public static String slowBuilds() {
        return String.join("; ", SLOW);
    }

    private int compile(int type, String source) {
        source = withNoUnroll(source);
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            String stage = type == GL_VERTEX_SHADER ? "vertex" : "fragment";
            throw new IllegalStateException("shader '" + name + "' (" + stage
                    + ") failed to compile:\n" + log);
        }
        return shader;
    }

    public void use() {
        glUseProgram(program);
    }

    private int location(String uniform) {
        return uniforms.computeIfAbsent(uniform, u -> glGetUniformLocation(program, u));
    }

    public void set(String uniform, Mat4 m) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.floats(m.columnMajor());
            glUniformMatrix4fv(location(uniform), false, buf);
        }
    }

    public void set(String uniform, float v) {
        glUniform1f(location(uniform), v);
    }

    public void set(String uniform, int v) {
        glUniform1i(location(uniform), v);
    }

    public void set(String uniform, float x, float y) {
        glUniform2f(location(uniform), x, y);
    }

    /** An {@code ivec2}. */
    public void set2i(String uniform, int x, int y) {
        glUniform2i(location(uniform), x, y);
    }

    public void set(String uniform, float x, float y, float z) {
        glUniform3f(location(uniform), x, y, z);
    }

    public void set(String uniform, float x, float y, float z, float w) {
        glUniform4f(location(uniform), x, y, z, w);
    }

    /** A {@code vec3} array uniform, {@code xyz} three floats an element. */
    public void set3v(String uniform, float[] xyz) {
        glUniform3fv(location(uniform), xyz);
    }

    /** An {@code int} array uniform. */
    public void set1iv(String uniform, int[] v) {
        glUniform1iv(location(uniform), v);
    }

    /** A {@code float} array uniform. */
    public void set1fv(String uniform, float[] v) {
        glUniform1fv(location(uniform), v);
    }

    @Override
    public void close() {
        glDeleteProgram(program);
    }
}
