"""
The mouse pointers of Larson's Game, one for each class, as 32-pixel pixel
art in the art's 128-colour palette: a wand for the Mage, a lute for the
Bard, a sword for the Knight, an axe for the Warrior, a gloved hand for the
Warlock, a leafy branch for the Druid, a dagger for the Rogue, an arrow for
the Ranger and a holy symbol - a golden sun-cross on its rod - for the
Cleric. Each points up and to the left, its tip the pointer's hot spot.

    python3 tools/ui/make_pointers.py [assets/ui/pointers] [palette=../3D-Modeling/generic_feminine_model/pixel_palette.json]

Writes <name>.png (32 x 32, and <name>@2x.png at 64 for high-density
screens) and pointers.json, each pointer's hot spot. The game (ui/Pointers)
uses the class's - or the one chosen in Pause → Class, or custom.png, any
picture dropped in the folder.

Each pointer is drawn from shapes - capsules, polygons, discs - over a 32
by 32 grid, each pixel the shape covering most of it, cel shaded: a light
edge to the upper left (where the light is), a shadow to the lower right,
and a near-black outline round it all so it shows on any ground.
"""

import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
N = 32
SS = 4  # samples a pixel, each way

# material: (shadow, mid, light)
MATS = {
    "wood": ((74, 40, 18), (122, 72, 34), (170, 112, 58)),
    "dark_wood": ((40, 22, 12), (70, 42, 22), (104, 66, 36)),
    "steel": ((92, 98, 116), (168, 176, 192), (236, 240, 248)),
    "iron": ((52, 56, 66), (92, 98, 112), (140, 148, 160)),
    "gold": ((150, 96, 10), (232, 176, 40), (255, 236, 140)),
    "leather": ((60, 30, 14), (102, 56, 26), (146, 90, 50)),
    "crystal": ((40, 70, 190), (93, 134, 250), (200, 226, 255)),
    "glow": ((180, 200, 255), (230, 240, 255), (255, 255, 255)),
    "glove": ((40, 14, 56), (82, 34, 108), (132, 70, 170)),
    "glove_dark": ((30, 10, 42), (62, 24, 84), (100, 50, 132)),
    "rune": ((150, 60, 220), (210, 120, 255), (250, 210, 255)),
    "leaf": ((20, 80, 24), (52, 140, 46), (130, 200, 80)),
    "red": ((110, 16, 20), (196, 36, 44), (240, 110, 100)),
    "feather": ((150, 150, 140), (220, 216, 204), (250, 248, 240)),
    "string": ((150, 140, 120), (210, 200, 176), (240, 236, 220)),
    "lute_face": ((120, 70, 24), (196, 130, 56), (236, 186, 110)),
    "hole": ((18, 10, 6), (30, 18, 10), (40, 26, 14)),
}
OUTLINE = (14, 10, 18)


class Canvas:
    def __init__(self):
        n = N * SS
        self.mat = np.full((n, n), -1, int)
        self.names = []
        c = (np.arange(n) + 0.5) / SS
        self.x, self.y = np.meshgrid(c, c)

    def _id(self, mat):
        if mat not in self.names:
            self.names.append(mat)
        return self.names.index(mat)

    def capsule(self, a, b, width, mat):
        ax, ay = a
        bx, by = b
        dx, dy = bx - ax, by - ay
        t = np.clip(((self.x - ax) * dx + (self.y - ay) * dy) / max(dx * dx + dy * dy, 1e-9), 0, 1)
        d = np.hypot(self.x - (ax + t * dx), self.y - (ay + t * dy))
        self.mat[d <= width / 2] = self._id(mat)

    def poly(self, pts, mat):
        pts = np.asarray(pts, float)
        inside = np.zeros(self.x.shape, bool)
        px, py = pts[:, 0], pts[:, 1]
        qx, qy = np.roll(px, -1), np.roll(py, -1)
        for ax, ay, bx, by in zip(px, py, qx, qy):
            cross = (ay > self.y) != (by > self.y)
            with np.errstate(divide="ignore", invalid="ignore"):
                xi = ax + (self.y - ay) * (bx - ax) / (by - ay)
            inside ^= cross & (self.x < xi)
        self.mat[inside] = self._id(mat)

    def disc(self, c, r, mat, squash=1.0):
        d = np.hypot(self.x - c[0], (self.y - c[1]) / squash)
        self.mat[d <= r] = self._id(mat)

    def image(self, palette):
        # each pixel: the material covering most of its samples (or none)
        m = self.mat.reshape(N, SS, N, SS).transpose(0, 2, 1, 3).reshape(N, N, SS * SS)
        px = np.full((N, N), -1, int)
        for i in range(N):
            for j in range(N):
                vals, counts = np.unique(m[i, j], return_counts=True)
                k = np.argmax(counts)
                if vals[k] >= 0 or counts[k] < SS * SS * 0.5:
                    filled = vals >= 0
                    if filled.any() and counts[filled].sum() >= SS * SS * 0.45:
                        px[i, j] = vals[filled][np.argmax(counts[filled])]
        out = np.zeros((N, N, 4), np.uint8)
        for i in range(N):
            for j in range(N):
                k = px[i, j]
                if k < 0:
                    near = [px[a, b] for a, b in ((i - 1, j), (i + 1, j), (i, j - 1), (i, j + 1),
                                                    (i - 1, j - 1), (i + 1, j + 1), (i - 1, j + 1), (i + 1, j - 1))
                            if 0 <= a < N and 0 <= b < N]
                    if any(v >= 0 for v in near):
                        out[i, j] = (*snap(OUTLINE, palette), 255)
                    continue
                shadow, mid, light = MATS[self.names[k]]

                def other(a, b):
                    return not (0 <= a < N and 0 <= b < N) or px[a, b] != k
                if other(i - 1, j) or other(i, j - 1):
                    c = light
                elif other(i + 1, j) or other(i, j + 1):
                    c = shadow
                else:
                    c = mid
                out[i, j] = (*snap(c, palette), 255)
        return Image.fromarray(out, "RGBA")


def snap(rgb, palette):
    if palette is None:
        return tuple(int(v) for v in rgb)
    d = ((palette - np.asarray(rgb, float)) ** 2).sum(1)
    return tuple(int(v) for v in palette[int(np.argmin(d))])


# --- the pointers: tip at (1, 1), drawn toward the lower right -------------------------

def wand(c):
    c.capsule((7, 7), (27, 27), 3.2, "dark_wood")
    c.capsule((21, 21), (27, 27), 3.8, "wood")
    c.poly([(1, 1), (9, 3.2), (7.5, 7.5), (3.2, 9)], "crystal")
    c.disc((3.8, 3.8), 1.3, "glow")
    c.capsule((12, 2.5), (12, 6.5), 1.0, "glow")
    c.capsule((10, 4.5), (14, 4.5), 1.0, "glow")


def lute(c):
    c.capsule((3, 3), (16, 16), 3.0, "dark_wood")
    c.poly([(1, 1), (6, 2), (2, 6)], "dark_wood")
    for k in range(3):
        c.disc((4 + 2.4 * k, 2.2 + 2.4 * k), 0.9, "gold")
    c.disc((21, 21), 9.5, "lute_face", squash=0.95)
    c.disc((20, 20), 2.6, "hole")
    c.capsule((24.5, 24.5), (27, 27), 2.2, "dark_wood")
    c.capsule((6, 6), (24.5, 24.5), 0.8, "string")


def sword(c):
    c.poly([(1, 1), (5, 2.2), (19, 16), (16, 19), (2.2, 5)], "steel")
    c.capsule((13, 21), (21, 13), 2.6, "gold")
    c.capsule((19, 19), (26, 26), 2.8, "leather")
    c.disc((27.5, 27.5), 2.4, "gold")


def axe(c):
    c.capsule((5, 5), (28, 28), 2.8, "wood")
    c.poly([(1, 7), (3, 3), (7, 1), (14, 4), (16, 9), (9, 16), (4, 14)], "steel")
    c.poly([(7, 7), (13, 6), (12, 9), (9, 12), (6, 13)], "iron")
    c.capsule((26, 26), (29, 29), 3.2, "leather")


def gloved_hand(c):
    # a pointing hand in a dark glove: the index finger up and to the left, the other
    # fingers curled under it, the thumb over it, the cuff of the gauntlet behind
    c.poly([(19, 23), (23, 19), (30, 26), (26, 30)], "leather")
    c.poly([(10, 14), (14, 10), (22, 15), (24, 21), (21, 24), (15, 22)], "glove")
    for k in range(3):
        c.capsule((9 + 2.2 * k, 16 + 1.0 * k), (13 + 2.2 * k, 21 + 1.0 * k), 2.6, "glove_dark")
    c.capsule((15, 9), (19, 6.5), 2.6, "glove_dark")
    c.capsule((2, 2), (12, 12), 3.4, "glove")
    c.disc((18, 17), 1.8, "rune")


def leafy_branch(c):
    c.capsule((2, 2), (28, 28), 2.4, "wood")
    c.capsule((14, 14), (22, 10), 1.6, "wood")
    for (x, y, dx, dy) in ((5, 9, -3, 4), (9, 5, 4, -3), (13, 17, -4, 5), (21, 9, 5, -2), (19, 23, -4, 4)):
        c.poly([(x, y), (x + dx * 0.5 + dy * 0.4, y + dy * 0.5 - dx * 0.4), (x + dx, y + dy),
                (x + dx * 0.5 - dy * 0.4, y + dy * 0.5 + dx * 0.4)], "leaf")
    c.poly([(1, 1), (6, 2.5), (2.5, 6)], "leaf")


def dagger(c):
    c.poly([(1, 1), (5, 2.5), (14, 11.5), (11.5, 14), (2.5, 5)], "steel")
    c.capsule((9, 16), (16, 9), 2.4, "iron")
    c.capsule((14, 14), (20, 20), 2.8, "leather")
    c.disc((21, 21), 2.0, "iron")


def arrow(c):
    c.capsule((6, 6), (27, 27), 1.8, "wood")
    c.poly([(1, 1), (9, 3.5), (6.5, 6.5), (3.5, 9)], "iron")
    c.poly([(21, 25), (25, 21), (30, 24), (24, 30)], "red")
    c.poly([(19, 21.5), (21.5, 19), (24, 21), (21, 24)], "feather")


def holy_symbol(c):
    # a golden sun-cross: a ring and its cross, on a short rod, the cross's arm the tip
    c.capsule((12, 12), (28, 28), 2.6, "dark_wood")
    c.disc((9, 9), 6.4, "gold")
    c.disc((9, 9), 4.4, "glow")
    c.capsule((2, 2), (16, 16), 2.6, "gold")
    c.capsule((3.5, 14.5), (14.5, 3.5), 2.6, "gold")
    c.disc((9, 9), 1.6, "red")
    c.disc((28, 28), 2.0, "gold")


POINTERS = {"wand": wand, "lute": lute, "sword": sword, "axe": axe, "gloved_hand": gloved_hand,
            "leafy_branch": leafy_branch, "dagger": dagger, "arrow": arrow, "holy_symbol": holy_symbol}


def main(args):
    out = os.path.join(ROOT, "assets", "ui", "pointers")
    palette_path = os.path.join(ROOT, "..", "3D-Modeling", "generic_feminine_model", "pixel_palette.json")
    for a in args:
        if a.startswith("palette="):
            palette_path = a.split("=", 1)[1]
        else:
            out = a
    palette = None
    if os.path.isfile(palette_path):
        p = json.load(open(palette_path))
        palette = np.asarray(p["fills"] + p["lines"], float)
    os.makedirs(out, exist_ok=True)
    spots = {}
    sheet = Image.new("RGBA", (N * len(POINTERS) * 2, N * 2), (40, 44, 60, 255))
    for i, (name, draw) in enumerate(POINTERS.items()):
        c = Canvas()
        draw(c)
        img = c.image(palette)
        img.save(os.path.join(out, name + ".png"))
        img.resize((N * 2, N * 2), Image.NEAREST).save(os.path.join(out, name + "@2x.png"))
        spots[name] = [1, 1]
        sheet.alpha_composite(img.resize((N * 2, N * 2), Image.NEAREST), (i * N * 2, 0))
    with open(os.path.join(out, "pointers.json"), "w") as f:
        json.dump({"hotspot": spots, "order": list(POINTERS)}, f, indent=2)
        f.write("\n")
    sheet.save(os.path.join(out, "preview.png"))
    print(f"{len(POINTERS)} pointers -> {out}")


if __name__ == "__main__":
    main(sys.argv[1:])
