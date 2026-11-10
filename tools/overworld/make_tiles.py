"""
Larson's Game's overworld map art: the terrain tiles and the destinations'
icons, as pixel art in the game's 128 colours.

    python3 tools/overworld/make_tiles.py [assets/overworld]

Writes, into assets/overworld/:

    tiles.png    every terrain's tile, 16 x 16: a row a terrain, VARIANTS across
                 (a tile shows the variant its place on the map picks; water's
                 are its waves, stepped through)
    icons.png    every kind of destination, 24 x 24, in a row, then the marks
                 drawn over them (cleared, locked, here)
    tiles.json   {"tile": 16, "variants": 4, "terrains": [{"key": "g", "id": "grass", "name": "Grassland",
                 "water": false}, ...], "icon": 24, "icons": ["town", ...]}

The same picture comes out every time (each tile is laid out from its own
name), so the file only changes when this does.
"""

import hashlib
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
T = 16
VARIANTS = 4
ICON = 24
SS = 4

with open(os.path.join(ROOT, "assets", "sprites", "pixel_palette.json")) as _f:
    _p = json.load(_f)
PALETTE = np.array(_p["fills"] + _p["lines"], dtype=np.float64)
LINE = (23, 22, 24)

# key, id, name, whether it is water (she cannot stand a destination on it; its tiles are stepped through as waves)
TERRAINS = [
    ("g", "grass", "Grassland", False), ("f", "forest", "Forest", False), ("p", "pines", "Pine forest", False),
    ("j", "jungle", "Jungle", False), ("h", "hills", "Hills", False), ("m", "mountains", "Mountains", False),
    ("s", "snow", "Snow", False), ("b", "sand", "Sand", False), ("y", "savannah", "Savannah", False),
    ("x", "marsh", "Marsh", False), ("a", "waste", "Waste", False), ("c", "crystal", "Crystal", False),
    ("w", "water", "Water", True), ("d", "deep", "Deep water", True),
]
ICONS = ["town", "village", "castle", "camp", "cave", "tower", "port", "ruins", "mine", "workshop", "shrine",
         "arena", "crypt", "portal", "cleared", "locked", "here"]


def snap(rgb):
    flat = rgb.reshape(-1, 1, 3).astype(np.float64)
    d = ((flat - PALETTE[None, :, :]) ** 2 * np.array([0.9, 1.2, 0.8])).sum(axis=2)
    return PALETTE[d.argmin(axis=1)].reshape(rgb.shape).astype(np.uint8)


def rng_for(*parts):
    return random.Random(int(hashlib.sha1("/".join(map(str, parts)).encode()).hexdigest()[:12], 16))


def fill(c):
    a = np.zeros((T, T, 3), dtype=np.int32)
    a[:, :] = c
    return a


def speck(a, r, colours, n):
    for _ in range(n):
        a[r.randrange(T), r.randrange(T)] = r.choice(colours)


def px(a, x, y, c):
    if 0 <= x < T and 0 <= y < T:
        a[y, x] = c


def blob(a, cx, cy, rx, ry, dark, mid, light):
    """A round shape lit from the top left: a tree's crown, a hill, a rock."""
    for y in range(T):
        for x in range(T):
            dx, dy = (x - cx) / rx, (y - cy) / ry
            d = dx * dx + dy * dy
            if d <= 1.0:
                lit = -(dx + dy) * 0.6 + (1 - d) * 0.5
                a[y, x] = light if lit > 0.55 else mid if lit > -0.15 else dark


def tree(a, cx, cy, crown):
    px(a, cx, cy + 3, (72, 44, 22))
    px(a, cx, cy + 4, (72, 44, 22))
    blob(a, cx, cy, 3.2, 3.2, *crown)


def pine(a, cx, cy, tones):
    dark, mid, light = tones
    px(a, cx, cy + 4, (72, 44, 22))
    for row in range(7):
        half = row // 2 if row < 6 else 2
        for x in range(cx - half, cx + half + 1):
            px(a, x, cy - 3 + row, light if x < cx and row % 2 == 0 else mid if x <= cx else dark)


def grass(r, base=(86, 160, 70), dark=(62, 132, 58), light=(120, 190, 86)):
    a = fill(base)
    speck(a, r, [dark], 9)
    speck(a, r, [light], 7)
    for _ in range(3):                       # a tuft: two blades
        x, y = r.randrange(1, T - 1), r.randrange(2, T)
        px(a, x, y, dark)
        px(a, x + 1, y - 1, dark)
    return a


def tile(terrain, v):
    r = rng_for("tile", terrain, v)
    if terrain == "grass":
        return grass(r)
    if terrain == "forest":
        a = grass(r, (70, 140, 62), (52, 112, 52), (96, 168, 74))
        spots = [(4, 4), (11, 5), (7, 10), (13, 11), (2, 11)]
        r.shuffle(spots)
        for cx, cy in sorted(spots[:4], key=lambda s: s[1]):
            tree(a, cx + r.randrange(-1, 2), cy, ((24, 84, 44), (40, 122, 52), (86, 168, 66)))
        return a
    if terrain == "pines":
        a = grass(r, (74, 132, 78), (56, 108, 66), (98, 156, 92))
        spots = [(3, 4), (9, 3), (13, 6), (6, 9), (11, 11), (2, 11)]
        r.shuffle(spots)
        for cx, cy in sorted(spots[:5], key=lambda s: s[1]):
            pine(a, cx, cy, ((16, 62, 60), (28, 96, 76), (60, 134, 96)))
        return a
    if terrain == "jungle":
        a = fill((30, 104, 52))
        speck(a, r, [(20, 78, 44)], 24)
        for _ in range(5):
            blob(a, r.randrange(2, T - 2), r.randrange(2, T - 2), r.uniform(2.2, 3.4), r.uniform(1.6, 2.6),
                 (16, 70, 40), (38, 132, 58), (110, 196, 74))
        for _ in range(2):
            px(a, r.randrange(T), r.randrange(T), (236, 96, 150))
        return a
    if terrain == "hills":
        a = grass(r, (104, 164, 72), (80, 138, 60), (140, 194, 90))
        for cx, cy in [(5 + r.randrange(-1, 2), 6), (11 + r.randrange(-1, 2), 11)]:
            blob(a, cx, cy + 2, 5.0, 3.6, (84, 122, 52), (122, 162, 66), (170, 204, 98))
            for x in range(cx - 4, cx + 5):
                px(a, x, cy + 5, (66, 104, 48))
        return a
    if terrain == "mountains":
        a = grass(r, (112, 130, 96), (90, 108, 82), (136, 150, 112))
        peaks = [(4 + r.randrange(-1, 2), 9, 6), (11 + r.randrange(-1, 2), 12, 8)]
        for cx, base, h in peaks:
            for row in range(h + 1):
                y = base - h + row
                for x in range(cx - row, cx + row + 1):
                    snowy = row <= h // 3
                    c = (236, 240, 248) if snowy and x <= cx else (196, 204, 222) if snowy else \
                        (150, 150, 160) if x < cx else (96, 96, 112)
                    px(a, x, y, c)
            px(a, cx, base - h, (255, 255, 255))
        return a
    if terrain == "snow":
        a = fill((236, 242, 250))
        speck(a, r, [(206, 220, 240)], 16)
        speck(a, r, [(255, 255, 255)], 10)
        for _ in range(2):
            x, y = r.randrange(2, T - 3), r.randrange(2, T - 2)
            for k in range(3):
                px(a, x + k, y, (180, 200, 232))
        return a
    if terrain == "sand":
        a = fill((232, 206, 144))
        speck(a, r, [(210, 180, 118)], 14)
        speck(a, r, [(250, 232, 176)], 9)
        return a
    if terrain == "savannah":
        a = fill((206, 184, 96))
        speck(a, r, [(178, 152, 74)], 14)
        speck(a, r, [(232, 214, 124)], 9)
        for _ in range(3):
            x, y = r.randrange(1, T - 1), r.randrange(2, T)
            px(a, x, y, (150, 124, 56))
            px(a, x + 1, y - 1, (150, 124, 56))
        if v % 2 == 0:                       # a lone flat-topped tree
            cx, cy = r.randrange(5, 11), r.randrange(6, 11)
            px(a, cx, cy + 1, (88, 56, 28))
            px(a, cx, cy + 2, (88, 56, 28))
            for x in range(cx - 3, cx + 4):
                px(a, x, cy - 1, (96, 136, 52))
                px(a, x, cy, (70, 108, 44))
            for x in range(cx - 2, cx + 3):
                px(a, x, cy - 2, (128, 164, 66))
        return a
    if terrain == "marsh":
        a = fill((74, 104, 62))
        speck(a, r, [(56, 84, 54)], 18)
        for _ in range(3):
            blob(a, r.randrange(3, T - 3), r.randrange(3, T - 3), r.uniform(1.8, 3.0), r.uniform(1.0, 1.8),
                 (44, 78, 82), (58, 100, 100), (96, 140, 128))
        for _ in range(4):
            x, y = r.randrange(T), r.randrange(3, T)
            px(a, x, y, (128, 148, 70))
            px(a, x, y - 1, (128, 148, 70))
            px(a, x, y - 2, (110, 70, 40))
        return a
    if terrain == "waste":
        a = fill((92, 86, 90))
        speck(a, r, [(68, 62, 70)], 18)
        speck(a, r, [(122, 114, 116)], 9)
        x, y = r.randrange(2, 8), r.randrange(2, T - 2)
        for k in range(r.randrange(5, 9)):   # a crack, with an ember in it
            px(a, x + k, y + (k // 3) * r.choice([-1, 1]), (36, 30, 38))
        px(a, r.randrange(T), r.randrange(T), (236, 110, 50))
        if v % 2:
            cx, cy = r.randrange(4, 12), r.randrange(6, 12)
            for k in range(5):               # a dead tree
                px(a, cx, cy - k, (48, 40, 44))
            px(a, cx - 1, cy - 3, (48, 40, 44))
            px(a, cx + 1, cy - 4, (48, 40, 44))
            px(a, cx - 2, cy - 4, (48, 40, 44))
        return a
    if terrain == "crystal":
        a = fill((108, 84, 150))
        speck(a, r, [(84, 62, 126)], 16)
        speck(a, r, [(140, 116, 184)], 8)
        for _ in range(3):
            cx, cy, h = r.randrange(2, T - 2), r.randrange(6, T - 1), r.randrange(4, 7)
            for k in range(h):
                px(a, cx, cy - k, (150, 232, 232) if k > 1 else (67, 159, 157))
                if k < h - 2:
                    px(a, cx + 1, cy - k, (67, 159, 157))
            px(a, cx, cy - h, (255, 255, 255))
        return a
    if terrain in ("water", "deep"):
        base, dark, light = ((60, 132, 214), (44, 108, 190), (150, 204, 250)) if terrain == "water" else \
            ((34, 80, 164), (26, 62, 138), (84, 140, 220))
        a = fill(base)
        r2 = rng_for("tile", terrain)       # the same waves each frame, moved along
        for _ in range(5):
            x, y, n = r2.randrange(T), r2.randrange(T), r2.randrange(2, 5)
            for k in range(n):
                px(a, (x + k + v * 2) % T, (y + (v % 2)) % T, light if k < n - 1 else dark)
        for _ in range(6):
            px(a, (r2.randrange(T) + v) % T, r2.randrange(T), dark)
        return a
    raise ValueError(terrain)


# --- the destinations' icons, drawn large and reduced ------------------------------------

def canvas():
    return Image.new("RGBA", (ICON * SS, ICON * SS), (0, 0, 0, 0))


def rect(d, x0, y0, x1, y1, c):
    d.rectangle([x0 * SS, y0 * SS, x1 * SS - 1, y1 * SS - 1], fill=c)


def poly(d, pts, c):
    d.polygon([(x * SS, y * SS) for x, y in pts], fill=c)


def circ(d, cx, cy, r, c):
    d.ellipse([(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], fill=c)


def house(d, x, y, w, h, wall, roof, door=True):
    rect(d, x, y, x + w, y + h, wall)
    poly(d, [(x - 1, y), (x + w / 2, y - h * 0.8), (x + w + 1, y)], roof)
    if door:
        rect(d, x + w / 2 - 1, y + h - 3, x + w / 2 + 1, y + h, (66, 38, 18))


def icon(name):
    im = canvas()
    d = ImageDraw.Draw(im)
    stone, stone_d, stone_l = (150, 150, 162), (96, 96, 112), (206, 208, 220)
    wood, wood_d = (150, 100, 52), (88, 56, 28)
    red, red_d = (200, 60, 48), (130, 30, 30)
    brass, brass_l = (176, 134, 64), (236, 200, 120)
    if name == "town":
        house(d, 2, 12, 8, 8, (232, 214, 176), red)
        house(d, 13, 13, 8, 7, (220, 200, 160), (70, 110, 180))
        house(d, 7, 9, 9, 11, (244, 230, 196), red_d)
        rect(d, 10, 12, 12, 14, (120, 190, 240))
        rect(d, 14, 3, 15, 8, stone_d)          # a chimney, and its smoke
        circ(d, 15.5, 2, 1.4, (220, 220, 226))
    elif name == "village":
        house(d, 3, 13, 8, 7, (232, 214, 176), (190, 150, 70))
        house(d, 13, 11, 8, 9, (220, 200, 160), (170, 128, 56))
    elif name == "castle":
        rect(d, 3, 9, 21, 21, stone)
        rect(d, 3, 9, 21, 11, stone_l)
        for x in (2, 9, 16):
            rect(d, x, 5, x + 6, 21, stone)
            rect(d, x, 5, x + 2, 7, stone_l)
            rect(d, x + 4, 5, x + 6, 7, stone_l)
            rect(d, x + 4, 7, x + 6, 21, stone_d)
        rect(d, 10, 15, 14, 21, (56, 34, 20))
        circ(d, 12, 15, 2, (56, 34, 20))
        rect(d, 11.5, 1, 12.2, 5, wood_d)
        poly(d, [(12.2, 1), (17, 2.5), (12.2, 4)], red)
    elif name == "camp":
        poly(d, [(3, 20), (11, 6), (19, 20)], (206, 170, 110))
        poly(d, [(11, 6), (19, 20), (14, 20)], (150, 116, 70))
        poly(d, [(9, 20), (11, 12), (13, 20)], (60, 40, 26))
        circ(d, 20, 19, 2, (236, 130, 50))      # the fire
        circ(d, 20, 18.4, 1, (255, 220, 110))
    elif name == "cave":
        circ(d, 12, 14, 10, stone)
        rect(d, 2, 14, 22, 22, stone)
        circ(d, 9, 9, 3, stone_l)
        circ(d, 12, 16, 5.5, (30, 24, 34))
        rect(d, 6.5, 16, 17.5, 22, (30, 24, 34))
    elif name == "tower":
        rect(d, 8, 6, 16, 22, stone)
        rect(d, 13, 6, 16, 22, stone_d)
        poly(d, [(6, 7), (12, 0), (18, 7)], (110, 70, 170))
        rect(d, 11, 10, 13, 13, (255, 220, 120))
        rect(d, 10, 17, 14, 22, (56, 34, 20))
        circ(d, 19, 4, 1.2, (150, 232, 232))    # a spark off its top
    elif name == "port":
        rect(d, 2, 16, 22, 22, (60, 132, 214))
        poly(d, [(4, 13), (20, 13), (17, 18), (7, 18)], wood)
        poly(d, [(4, 13), (20, 13), (19, 15), (5, 15)], wood_d)
        rect(d, 11.5, 2, 12.5, 13, wood_d)
        poly(d, [(12.5, 3), (19, 11), (12.5, 11)], (244, 236, 214))
        poly(d, [(11.5, 5), (6, 11), (11.5, 11)], (220, 208, 180))
    elif name == "ruins":
        for x, h in ((3, 12), (9, 7), (15, 14)):
            rect(d, x, 21 - h, x + 4, 21, stone)
            rect(d, x, 21 - h, x + 1.5, 21, stone_l)
            rect(d, x + 3, 21 - h + 1, x + 4, 21, stone_d)
        rect(d, 2, 20, 22, 22, stone_d)
        circ(d, 12, 19, 1.5, (86, 150, 70))
    elif name == "mine":
        poly(d, [(2, 22), (12, 4), (22, 22)], (128, 104, 84))
        poly(d, [(12, 4), (22, 22), (15, 22)], (92, 72, 60))
        rect(d, 8, 14, 16, 22, (30, 24, 34))
        rect(d, 7, 13, 17, 15, wood)
        rect(d, 7, 13, 9, 22, wood_d)
        rect(d, 15, 13, 17, 22, wood_d)
    elif name == "workshop":
        house(d, 4, 12, 14, 9, (190, 150, 110), (96, 68, 30), door=False)
        circ(d, 11, 16, 4, brass)               # a great cog on its wall
        circ(d, 11, 16, 2.6, (190, 150, 110))
        circ(d, 11, 16, 1.2, brass_l)
        for k in range(8):
            a = k * math.pi / 4
            circ(d, 11 + math.cos(a) * 4.4, 16 + math.sin(a) * 4.4, 1.0, brass)
        rect(d, 17, 3, 19.5, 12, stone_d)       # its chimney
        circ(d, 18.5, 2, 1.6, (220, 220, 226))
    elif name == "shrine":
        rect(d, 5, 18, 19, 21, stone_d)
        rect(d, 7, 8, 9, 18, stone_l)
        rect(d, 15, 8, 17, 18, stone)
        poly(d, [(4, 8), (12, 2), (20, 8)], stone_l)
        circ(d, 12, 14, 2.2, (255, 226, 130))
    elif name == "arena":
        d.ellipse([2 * SS, 7 * SS, 22 * SS, 21 * SS], fill=stone)
        d.ellipse([5 * SS, 10 * SS, 19 * SS, 18 * SS], fill=(214, 186, 128))
        for x in range(4, 21, 4):
            rect(d, x, 7.5, x + 1.2, 11, stone_d)
        poly(d, [(11, 12), (13, 12), (12, 16)], red)
    elif name == "crypt":
        rect(d, 5, 9, 19, 21, stone_d)
        poly(d, [(4, 9), (12, 3), (20, 9)], stone)
        rect(d, 9, 13, 15, 21, (30, 24, 34))
        circ(d, 12, 15, 2.2, (232, 232, 222))   # a skull over its door
        rect(d, 11, 16.4, 13, 18, (232, 232, 222))
        circ(d, 11.2, 14.8, 0.6, (30, 24, 34))
        circ(d, 12.8, 14.8, 0.6, (30, 24, 34))
    elif name == "portal":
        circ(d, 12, 12, 10, brass)
        circ(d, 12, 12, 8, (40, 24, 70))
        circ(d, 12, 12, 6, (110, 70, 190))
        circ(d, 12, 12, 3.4, (150, 232, 232))
        circ(d, 12, 12, 1.4, (255, 255, 255))
    elif name == "cleared":
        rect(d, 10, 4, 11.4, 22, wood_d)
        poly(d, [(11.4, 4), (21, 7.5), (11.4, 11)], (62, 170, 72))
        circ(d, 10.7, 22, 2, (40, 30, 24))
    elif name == "locked":
        d.arc([7 * SS, 3 * SS, 17 * SS, 15 * SS], 180, 360, fill=stone_l, width=2 * SS + 2)
        rect(d, 5, 10, 19, 21, brass)
        rect(d, 5, 10, 19, 12, brass_l)
        circ(d, 12, 15, 1.6, (40, 30, 24))
        rect(d, 11.4, 15, 12.6, 18.5, (40, 30, 24))
    elif name == "here":
        poly(d, [(12, 22), (5, 9), (19, 9)], red)
        circ(d, 12, 8, 7, red)
        circ(d, 12, 8, 3.4, (255, 250, 236))
    else:
        raise ValueError(name)
    small = im.resize((ICON, ICON), Image.LANCZOS)
    a = np.array(small)
    alpha = a[:, :, 3] > 110
    rgb = snap(a[:, :, :3].copy())
    out = np.zeros((ICON, ICON, 4), dtype=np.uint8)
    out[:, :, :3] = rgb
    out[:, :, 3] = np.where(alpha, 255, 0)
    # a dark line round it
    edge = np.zeros_like(alpha)
    for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        edge |= np.roll(alpha, (dy, dx), axis=(0, 1))
    edge &= ~alpha
    out[edge] = (*LINE, 255)
    return Image.fromarray(out, "RGBA")


def main(folder):
    os.makedirs(folder, exist_ok=True)
    sheet = Image.new("RGB", (T * VARIANTS, T * len(TERRAINS)))
    for row, (_, name, _, _) in enumerate(TERRAINS):
        for v in range(VARIANTS):
            a = snap(np.clip(tile(name, v), 0, 255).astype(np.uint8))
            sheet.paste(Image.fromarray(a, "RGB"), (v * T, row * T))
    sheet.save(os.path.join(folder, "tiles.png"))
    icons = Image.new("RGBA", (ICON * len(ICONS), ICON), (0, 0, 0, 0))
    for i, name in enumerate(ICONS):
        icons.paste(icon(name), (i * ICON, 0))
    icons.save(os.path.join(folder, "icons.png"))
    with open(os.path.join(folder, "tiles.json"), "w") as f:
        json.dump({"tile": T, "variants": VARIANTS,
                   "terrains": [{"key": k, "id": i, "name": n, "water": w} for k, i, n, w in TERRAINS],
                   "icon": ICON, "icons": ICONS}, f, indent=1)
        f.write("\n")
    print(f"{len(TERRAINS)} terrains x {VARIANTS}, {len(ICONS)} icons -> {folder}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "assets", "overworld"))
