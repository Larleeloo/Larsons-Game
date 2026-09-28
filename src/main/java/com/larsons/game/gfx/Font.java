package com.larsons.game.gfx;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Text on the GPU: a glyph atlas rasterised once by AWT (headless — it never
 * opens a window) and drawn as textured quads through the {@link Batch}.
 *
 * <p>Rasterised at the display's pixel density so text stays crisp on HiDPI
 * screens, and measured in logical pixels so layout code never has to know.
 */
public final class Font implements AutoCloseable {

    /** Beyond ASCII: the handful of symbols the UI uses, when the font has them. */
    private static final String EXTRA = "·—–←→↑↓°×•▶◀✓…";

    private record Glyph(float u0, float v0, float u1, float v1, float w, float h, float advance) {}

    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Texture texture;
    private final float lineHeight;
    private final float ascent;
    /** The one device pixel of padding left of each glyph in the atlas, in logical pixels. */
    private final float pad;

    /**
     * @param size  point size in logical pixels
     * @param bold  bold weight
     * @param scale device pixels per logical pixel
     */
    public Font(int size, boolean bold, float scale) {
        float s = Math.max(1f, scale);
        pad = 1f / s;
        java.awt.Font awt = new java.awt.Font(java.awt.Font.SANS_SERIF,
                bold ? java.awt.Font.BOLD : java.awt.Font.PLAIN, Math.round(size * s));

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(awt);
        FontMetrics fm = pg.getFontMetrics();
        int glyphH = fm.getAscent() + fm.getDescent() + 2;
        ascent = fm.getAscent() / s;
        lineHeight = (fm.getAscent() + fm.getDescent()) / s;

        StringBuilder chars = new StringBuilder();
        for (char c = 32; c < 127; c++) chars.append(c);
        for (char c : EXTRA.toCharArray()) if (awt.canDisplay(c)) chars.append(c);

        // Shelf-pack every glyph into rows of a 1024-wide atlas.
        int atlasW = 1024, x = 1, y = 1;
        int[][] placed = new int[chars.length()][];
        for (int i = 0; i < chars.length(); i++) {
            int w = Math.max(1, fm.charWidth(chars.charAt(i))) + 2;
            if (x + w >= atlasW) {
                x = 1;
                y += glyphH + 1;
            }
            placed[i] = new int[]{x, y, w};
            x += w + 1;
        }
        int atlasH = Integer.highestOneBit(y + glyphH + 1) * 2;
        pg.dispose();

        BufferedImage atlas = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(awt);
        g.setColor(Color.WHITE);
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            int[] p = placed[i];
            g.drawString(String.valueOf(c), p[0] + 1, p[1] + fm.getAscent());
            glyphs.put((int) c, new Glyph(
                    p[0] / (float) atlasW, p[1] / (float) atlasH,
                    (p[0] + p[2]) / (float) atlasW, (p[1] + glyphH) / (float) atlasH,
                    p[2] / s, glyphH / s, fm.charWidth(c) / s));
        }
        g.dispose();
        texture = Texture.of(atlas, false);
    }

    /** Height of one line of text, in logical pixels. */
    public float lineHeight() { return lineHeight; }

    /** Distance from the top of a line to its baseline. */
    public float ascent() { return ascent; }

    public float width(String text) {
        float w = 0;
        for (int i = 0; i < text.length(); i++) w += glyph(text.charAt(i)).advance;
        return w;
    }

    private Glyph glyph(char c) {
        Glyph g = glyphs.get((int) c);
        return g != null ? g : glyphs.get((int) '?');
    }

    /** Whether the font can draw {@code c} (otherwise it draws a '?'). */
    public boolean has(char c) {
        return glyphs.containsKey((int) c);
    }

    /**
     * Draw {@code text} with its top-left corner at {@code (x, y)}; returns the
     * width drawn. Straight-alpha colour.
     */
    public float draw(Batch batch, String text, float x, float y,
                      float r, float g, float b, float a) {
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            Glyph gl = glyph(text.charAt(i));
            if (text.charAt(i) != ' ') {
                batch.rect(texture, cx - pad, y, gl.w, gl.h,
                        gl.u0, gl.v0, gl.u1, gl.v1, r, g, b, a);
            }
            cx += gl.advance;
        }
        return cx - x;
    }

    /** Draw with a one-pixel drop shadow, for text over the 3D view. */
    public float drawShadowed(Batch batch, String text, float x, float y,
                              float r, float g, float b, float a) {
        draw(batch, text, x + 1, y + 1, 0, 0, 0, a * 0.7f);
        return draw(batch, text, x, y, r, g, b, a);
    }

    @Override
    public void close() {
        texture.close();
    }
}
