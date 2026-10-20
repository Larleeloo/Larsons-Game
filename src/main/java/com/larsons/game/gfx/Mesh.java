package com.larsons.game.gfx;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Static flat-shaded geometry on the GPU: position, normal and colour per
 * vertex, drawn as plain triangles. Built once by a {@link MeshBuilder}.
 */
public final class Mesh implements AutoCloseable {

    static final int FLOATS_PER_VERTEX = 9; // xyz nxnynz rgb

    private final int vao, vbo;
    private final int vertices;

    Mesh(float[] data) {
        vertices = data.length / FLOATS_PER_VERTEX;
        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(data.length);
        buf.put(data).flip();
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
        MemoryUtil.memFree(buf);
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glBindVertexArray(0);
    }

    public void draw() {
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertices);
        glBindVertexArray(0);
    }

    public int triangles() { return vertices / 3; }

    @Override
    public void close() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
