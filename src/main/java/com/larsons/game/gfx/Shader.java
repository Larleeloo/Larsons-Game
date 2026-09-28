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
 */
public final class Shader implements AutoCloseable {

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
    }

    private int compile(int type, String source) {
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

    public void set(String uniform, float x, float y, float z) {
        glUniform3f(location(uniform), x, y, z);
    }

    public void set(String uniform, float x, float y, float z, float w) {
        glUniform4f(location(uniform), x, y, z, w);
    }

    @Override
    public void close() {
        glDeleteProgram(program);
    }
}
