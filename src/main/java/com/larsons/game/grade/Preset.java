package com.larsons.game.grade;

import com.larsons.game.sprite.Variants.Oklab;

import java.util.Locale;

/**
 * Colour grades ready made: the art as it was drawn, the looks of old
 * machines - each palette colour sent to the nearest of the machine's own -
 * and a few moods. A preset is somewhere to start: any of it can be changed
 * after ({@link Grading}).
 */
public enum Preset {

    ORIGINAL("Original", "The 128 colours as the art was drawn.") {
        @Override
        void apply(Grading g) { }
    },
    GAMEBOY("Gameboy", "Four greens, darkest to lightest, as on the first handheld's screen.") {
        @Override
        void apply(Grading g) {
            ramp(g, 0x0F380F, 0x306230, 0x8BAC0F, 0x9BBC0F);
        }
    },
    POCKET("Pocket", "Four greys: the handheld's later, sharper screen.") {
        @Override
        void apply(Grading g) {
            ramp(g, 0x181818, 0x5A5A5A, 0xA5A5A5, 0xE7E7E7);
        }
    },
    NES("8-bit console", "The 54 colours of an 8-bit home console.") {
        @Override
        void apply(Grading g) {
            nearest(g, NES_COLOURS);
        }
    },
    C64("Home computer", "The 16 colours of an 8-bit home computer.") {
        @Override
        void apply(Grading g) {
            nearest(g, 0x000000, 0xFFFFFF, 0x68372B, 0x70A4B2, 0x6F3D86, 0x588D43, 0x352879, 0xB8C76F, 0x6F4F25,
                    0x433900, 0x9A6759, 0x444444, 0x6C6C6C, 0x9AD284, 0x6C5EB5, 0x959595);
        }
    },
    CGA("Four-colour PC", "Black, cyan, magenta and white: an early PC's graphics card.") {
        @Override
        void apply(Grading g) {
            nearest(g, 0x000000, 0x55FFFF, 0xFF55FF, 0xFFFFFF);
        }
    },
    N64("N64", "Bright and saturated, in 15-bit colour: a 64-bit console's cartridges.") {
        @Override
        void apply(Grading g) {
            g.putShift(0, 1.35, 1.05, 1.08, false);
            for (int i = 0; i < g.size(); i++) {
                int c = g.originals()[i];
                int r = five((c >> 16) & 255), gr = five((c >> 8) & 255), b = five(c & 255);
                g.put(i, (r << 16) | (gr << 8) | b);
            }
        }
    },
    RED_VISOR("Red visor", "Black and three reds: a headset that showed one colour.") {
        @Override
        void apply(Grading g) {
            ramp(g, 0x000000, 0x550000, 0xAA0000, 0xFF0000);
        }
    },
    ONE_COLOUR("One colour", "Every colour the one hue - turn Hue to choose it - as light or dark as it was.") {
        @Override
        void apply(Grading g) {
            g.putShift(38, 1, 1, 1, true);
        }
    },
    GREYSCALE("Greyscale", "No colour at all: each as light or dark as it was.") {
        @Override
        void apply(Grading g) {
            for (int i = 0; i < g.size(); i++) {
                double l = Oklab.of(g.originals()[i])[0];
                g.put(i, Oklab.rgb(l, 0, 0));
            }
        }
    },
    SEPIA("Sepia", "An old photograph's browns.") {
        @Override
        void apply(Grading g) {
            for (int i = 0; i < g.size(); i++) {
                double l = Oklab.of(g.originals()[i])[0];
                g.put(i, Oklab.rgb(l * 0.96 + 0.02, 0.012 + 0.02 * l, 0.03 + 0.05 * l));
            }
        }
    },
    TWO_COLOURS("Two colours", "The whole palette brought down to two.") {
        @Override
        void apply(Grading g) {
            g.putCount(2);
            g.putSnap(true);
        }
    },
    VIVID("Vivid", "More colour, more contrast.") {
        @Override
        void apply(Grading g) {
            g.putShift(0, 1.4, 1.02, 1.12, false);
        }
    },
    FADED("Faded", "Washed out: less colour, less contrast.") {
        @Override
        void apply(Grading g) {
            g.putShift(0, 0.6, 1.08, 0.85, false);
        }
    },
    MOONLIT("Moonlit", "Everything in the blue of a clear night.") {
        @Override
        void apply(Grading g) {
            g.putShift(222, 0.8, 0.85, 1, true);
        }
    };

    private static final int[] NES_COLOURS = {
            0x7C7C7C, 0x0000FC, 0x0000BC, 0x4428BC, 0x940084, 0xA80020, 0xA81000, 0x881400, 0x503000, 0x007800,
            0x006800, 0x005800, 0x004058, 0x000000, 0xBCBCBC, 0x0078F8, 0x0058F8, 0x6844FC, 0xD800CC, 0xE40058,
            0xF83800, 0xE45C10, 0xAC7C00, 0x00B800, 0x00A800, 0x00A844, 0x008888, 0xF8F8F8, 0x3CBCFC, 0x6888FC,
            0x9878F8, 0xF878F8, 0xF85898, 0xF87858, 0xFCA044, 0xF8B800, 0xB8F818, 0x58D854, 0x58F898, 0x00E8D8,
            0x787878, 0xFCFCFC, 0xA4E4FC, 0xB8B8F8, 0xD8B8F8, 0xF8B8F8, 0xF8A4C0, 0xF0D0B0, 0xFCE0A8, 0xF8D878,
            0xD8F878, 0xB8F8B8, 0xB8F8D8, 0x00FCFC};

    private final String label, about;

    Preset(String label, String about) {
        this.label = label;
        this.about = about;
    }

    public String label() { return label; }

    /** A line on what it is. */
    public String about() { return about; }

    public String key() { return name().toLowerCase(Locale.ROOT); }

    public static Preset byKey(String key) {
        for (Preset p : values()) if (p.key().equalsIgnoreCase(key)) return p;
        return null;
    }

    abstract void apply(Grading g);

    /** Each palette colour sent to one of {@code steps}, darkest to lightest, by how light it is; snapped. */
    static void ramp(Grading g, int... steps) {
        double lo = 1, hi = 0;
        for (int c : g.originals()) {
            double l = Oklab.of(c)[0];
            lo = Math.min(lo, l);
            hi = Math.max(hi, l);
        }
        for (int i = 0; i < g.size(); i++) {
            double l = (Oklab.of(g.originals()[i])[0] - lo) / Math.max(1e-6, hi - lo);
            g.put(i, steps[Math.min(steps.length - 1, (int) (l * steps.length))]);
        }
        g.putSnap(true);
    }

    /** Each palette colour sent to the nearest of {@code set} (in OKLab); snapped. */
    static void nearest(Grading g, int... set) {
        double[][] lab = new double[set.length][];
        for (int k = 0; k < set.length; k++) lab[k] = Oklab.of(set[k]);
        for (int i = 0; i < g.size(); i++) {
            double[] c = Oklab.of(g.originals()[i]);
            int best = 0;
            double bd = Double.MAX_VALUE;
            for (int k = 0; k < set.length; k++) {
                double d = (c[0] - lab[k][0]) * (c[0] - lab[k][0]) + (c[1] - lab[k][1]) * (c[1] - lab[k][1])
                        + (c[2] - lab[k][2]) * (c[2] - lab[k][2]);
                if (d < bd) {
                    bd = d;
                    best = k;
                }
            }
            g.put(i, set[best]);
        }
        g.putSnap(true);
    }

    /** A channel in five bits. */
    private static int five(int v) {
        int q = v >> 3;
        return (q << 3) | (q >> 2);
    }
}
