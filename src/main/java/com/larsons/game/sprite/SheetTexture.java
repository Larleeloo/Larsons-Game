package com.larsons.game.sprite;

import com.larsons.game.gfx.Texture;

/**
 * A sprite sheet on the GPU: one texture holding every frame of one
 * (layer, state, elevation, direction), and where each frame is in it.
 */
public final class SheetTexture implements AutoCloseable {

    private final Texture texture;
    private final int frameWidth, frameHeight, frameCount, columns;
    private final String source;
    private boolean closed;
    long lastUsedFrame;

    public SheetTexture(Texture texture, SheetImage layout, String source) {
        this.texture = texture;
        this.frameWidth = layout.frameWidth();
        this.frameHeight = layout.frameHeight();
        this.frameCount = layout.frameCount();
        this.columns = layout.columns();
        this.source = source;
    }

    public Texture texture() { return texture; }

    public int frameCount() { return frameCount; }

    public int frameWidth() { return frameWidth; }

    public int frameHeight() { return frameHeight; }

    /** Where this came from — a file path, or "fallback" — for the HUD. */
    public String source() { return source; }

    /**
     * Texture coordinates of {@code frame}: {@code {u0, v0, u1, v1}}, top-left
     * then bottom-right; {@code mirrored} swaps left and right.
     */
    public float[] uv(int frame, boolean mirrored) {
        int f = Math.floorMod(frame, Math.max(1, frameCount));
        float tw = texture.width(), th = texture.height();
        int col = f % columns, row = f / columns;
        // A hair inside the cell, so nearest sampling at the exact edge of a
        // magnified 32-pixel frame never picks up its neighbour's column.
        float eps = 0.001f;
        float u0 = (col * frameWidth + eps) / tw, u1 = ((col + 1) * frameWidth - eps) / tw;
        float v0 = (row * frameHeight + eps) / th, v1 = ((row + 1) * frameHeight - eps) / th;
        return mirrored ? new float[]{u1, v0, u0, v1} : new float[]{u0, v0, u1, v1};
    }

    public long bytes() { return texture.bytes(); }

    /** Whether the GPU texture has been released (evicted, reloaded or shut down). */
    public boolean closed() { return closed; }

    @Override
    public void close() {
        closed = true;
        texture.close();
    }
}
