"""The inventory's background, assets/ui/inventory_background.png: a medieval
chest-lid of oak planks bound in iron, in the game's 128-colour palette.

Drawn at half size (216 x 384) a pixel at a time and doubled, so it is as
chunky as the sprites. Where the game draws over it (assets/ui/README.md):
the title and count on a carved plaque in the top tenth, the slots' lattice
on a dark leather panel 7 - 93 % across and 11 - 74 % down, the hotbar on a
stitched leather belt 78 - 96 % down - studded between its five slots.

    python3 tools/ui/make_inventory_background.py [out.png]
"""
import sys

import numpy as np
from PIL import Image

W, H = 216, 384                     # half of 432 x 768

# every colour below is one of the palette's (3D-Modeling generic_feminine_model/pixel_palette.json)
OAK = [(39, 13, 9), (77, 27, 2), (109, 54, 26), (125, 81, 41), (146, 89, 42)]
IRON = [(23, 22, 24), (35, 28, 24), (65, 54, 47), (93, 93, 98), (144, 144, 148), (194, 196, 205)]
LEATHER = [(21, 21, 6), (31, 18, 21), (35, 28, 24), (39, 13, 9), (49, 3, 3)]
BELT = [(35, 17, 1), (54, 3, 0), (77, 27, 2), (111, 34, 1)]
BRASS = [(35, 17, 1), (159, 84, 16), (174, 120, 58), (249, 195, 109), (254, 237, 199)]
THREAD = (152, 119, 74)

rng = np.random.default_rng(1025)
img = np.zeros((H, W, 3), np.uint8)


def noise(h, w, sy, sx, seed):
    """Smooth value noise, stretched sy x sx (grain runs along the long way)."""
    r = np.random.default_rng(seed)
    gy, gx = h // sy + 2, w // sx + 2
    g = r.random((gy, gx))
    y = np.arange(h)[:, None] / sy
    x = np.arange(w)[None, :] / sx
    y0, x0 = np.floor(y).astype(int), np.floor(x).astype(int)
    fy, fx = y - y0, x - x0
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    a = g[y0, x0] * (1 - fx) + g[y0, x0 + 1] * fx
    b = g[y0 + 1, x0] * (1 - fx) + g[y0 + 1, x0 + 1] * fx
    return a * (1 - fy) + b * fy


def ramp(colours, t):
    """Colour of each t (0 - 1) from a ramp, dark to light."""
    i = np.clip((t * len(colours)).astype(int), 0, len(colours) - 1)
    return np.array(colours, np.uint8)[i]


def fill(x0, y0, x1, y1, colour):
    img[y0:y1, x0:x1] = colour


def put(x, y, colour):
    if 0 <= x < W and 0 <= y < H:
        img[y, x] = colour


# --- oak planks, standing, the lid's boards -------------------------------------------------
grain = noise(H, W, 22, 2, 1) * 0.55 + noise(H, W, 5, 1, 2) * 0.3 + rng.random((H, W)) * 0.15
edges = [0, 27, 52, 81, 108, 134, 163, 189, W]
for k in range(len(edges) - 1):
    x0, x1 = edges[k], edges[k + 1]
    shade = 0.12 * ((k * 7) % 3 - 1)                          # each board a touch lighter or darker
    t = np.clip(0.18 + grain[:, x0:x1] * 0.7 + shade, 0, 0.99)
    t[:, 0] = 0.0                                               # the gap between boards
    t[:, 1] = np.minimum(t[:, 1], 0.3)
    if x1 - x0 > 3:
        t[:, -1] = np.minimum(t[:, -1] + 0.18, 0.99)            # its other edge catching the light
    img[:, x0:x1] = ramp(OAK, t)
    for _ in range(2):                                           # a knot or two
        kx, ky = rng.integers(x0 + 6, max(x0 + 7, x1 - 6)), rng.integers(40, H - 40)
        for dy in range(-4, 5):
            for dx in range(-2, 3):
                d = (dx / 2.5) ** 2 + (dy / 4.5) ** 2
                if d < 1:
                    put(kx + dx, ky + dy, OAK[0] if d < 0.35 else OAK[1])


def nail(x, y):
    put(x, y, IRON[4])
    put(x + 1, y, IRON[3])
    put(x, y + 1, IRON[2])
    put(x + 1, y + 1, IRON[1])


def stud(x, y, metal=BRASS):
    """A round-headed stud, 4 x 4, lit from the top left."""
    shape = [" ab ", "abcd", "bcdd", " dd "]
    look = {"a": metal[-2], "b": metal[-3], "c": metal[1], "d": metal[0]}
    for dy, row in enumerate(shape):
        for dx, ch in enumerate(row):
            if ch != " ":
                put(x + dx, y + dy, look[ch])


def band(y0, y1, rivets=True):
    """An iron strap right across."""
    h = y1 - y0
    for y in range(y0, y1):
        t = (y - y0) / max(1, h - 1)
        c = IRON[4] if y == y0 else IRON[1] if y == y1 - 1 else IRON[3] if t < 0.4 else IRON[2]
        img[y, :] = c
    img[y1:y1 + 1, :] = OAK[0]                                   # its shadow on the wood
    if rivets:
        for x in range(9, W - 8, 22):
            stud(x, y0 + h // 2 - 2, IRON)


def bevel(x0, y0, x1, y1, inner, light, dark):
    """A sunken panel: shadowed along its top and left, lit along its bottom and right."""
    img[y0:y1, x0:x1] = inner
    img[y0, x0:x1] = dark
    img[y0 + 1, x0 + 1:x1 - 1] = dark
    img[y0:y1, x0] = dark
    img[y0 + 1:y1 - 1, x0 + 1] = dark
    img[y1 - 1, x0:x1] = light
    img[y0:y1, x1 - 1] = light


# --- the leather panel the slots lie on -------------------------------------------------------
LX0, LY0, LX1, LY1 = 11, 43, W - 11, 290
img[LY0 - 3:LY1 + 3, LX0 - 3:LX1 + 3] = OAK[4]                    # a moulding round it
img[LY1 + 1:LY1 + 3, LX0 - 3:LX1 + 3] = OAK[2]
img[LY0 - 3:LY1 + 3, LX1 + 1:LX1 + 3] = OAK[2]
img[LY0 - 3, LX0 - 3:LX1 + 3] = OAK[3]
leather = noise(LY1 - LY0, LX1 - LX0, 6, 6, 3) * 0.6 + rng.random((LY1 - LY0, LX1 - LX0)) * 0.4
t = np.clip(0.15 + leather * 0.6, 0, 0.99)
img[LY0:LY1, LX0:LX1] = ramp(LEATHER[:4], t * 0.95)
img[LY0:LY0 + 2, LX0:LX1] = LEATHER[0]
img[LY0:LY1, LX0:LX0 + 2] = LEATHER[0]
for x in range(LX0 + 4, LX1 - 3, 4):                               # stitched along its edge
    put(x, LY0 + 3, BELT[2])
    put(x + 1, LY0 + 3, BELT[2])
    put(x, LY1 - 4, BELT[2])
    put(x + 1, LY1 - 4, BELT[2])
for y in range(LY0 + 4, LY1 - 3, 4):
    put(LX0 + 3, y, BELT[2])
    put(LX0 + 3, y + 1, BELT[2])
    put(LX1 - 4, y, BELT[2])
    put(LX1 - 4, y + 1, BELT[2])

# --- the iron strap between it and the belt ------------------------------------------------
band(293, 299)

# --- the belt the hotbar's on -----------------------------------------------------------------
BX0, BY0, BX1, BY1 = 6, 303, W - 6, 370
belt = noise(BY1 - BY0, BX1 - BX0, 3, 18, 4) * 0.75 + noise(BY1 - BY0, BX1 - BX0, 1, 6, 8) * 0.13 + rng.random((BY1 - BY0, BX1 - BX0)) * 0.12
img[BY0:BY1, BX0:BX1] = ramp(BELT, np.clip(0.1 + belt * 0.75, 0, 0.99))
img[BY0, BX0:BX1] = BELT[3]
img[BY1 - 1, BX0:BX1] = LEATHER[0]
img[BY0:BY1, BX0] = BELT[3]
img[BY0:BY1, BX1 - 1] = LEATHER[0]
for x in range(BX0 + 3, BX1 - 3, 4):
    for y in (BY0 + 3, BY1 - 4):
        put(x, y, THREAD)
        put(x + 1, y, THREAD)
step = 0.86 * W / 5
cy = round(0.87 * H)
for i in range(6):                                                 # studs between the slots
    x = round(0.07 * W + step * i)
    stud(x - 2, cy - 2)
img[BY1:BY1 + 1, BX0:BX1] = OAK[0]

# --- the lintel, its plaque, the title on it ------------------------------------------------
TY1 = 39
lintel = noise(TY1, W, 2, 26, 5) * 0.6 + rng.random((TY1, W)) * 0.25
img[0:TY1, :] = ramp(OAK, np.clip(0.25 + lintel * 0.6, 0, 0.99))
img[0, :] = OAK[4]
img[TY1 - 1, :] = OAK[0]
img[TY1, :] = OAK[0]
PX0, PX1, PY0, PY1 = 58, W - 58, 3, 37
img[PY0:PY1, PX0:PX1] = BRASS[1]
img[PY0, PX0:PX1] = BRASS[3]
img[PY0:PY1, PX0] = BRASS[2]
img[PY1 - 1, PX0:PX1] = BRASS[0]
img[PY0:PY1, PX1 - 1] = BRASS[0]
inner = noise(PY1 - PY0 - 4, PX1 - PX0 - 4, 2, 12, 6)
img[PY0 + 2:PY1 - 2, PX0 + 2:PX1 - 2] = ramp(LEATHER[1:4], np.clip(inner * 0.9, 0, 0.99))
for (x, y) in ((PX0 + 2, PY0 + 2), (PX1 - 4, PY0 + 2), (PX0 + 2, PY1 - 4), (PX1 - 4, PY1 - 4)):
    nail(x, y)
for x in (5, W - 9):
    stud(x, 17)

# --- the frame: iron at the corners, nails down the sides ---------------------------------
for x in (0, 1, W - 2, W - 1):
    img[:, x] = OAK[0]
for y in (H - 1, H - 2):
    img[y, :] = OAK[0]
img[H - 14:H - 2, :] = ramp(OAK, np.clip(0.25 + noise(12, W, 2, 26, 7) * 0.6, 0, 0.99))
img[H - 14, :] = OAK[0]


def bracket(x0, y0, fx, fy):
    """An iron corner: a strap each way, with studs."""
    L, T = 30, 6
    for i in range(L):
        for j in range(T):
            c = IRON[4] if j == 0 else IRON[1] if j == T - 1 else IRON[3] if j < 3 else IRON[2]
            put(x0 + fx * i, y0 + fy * j, c)
            put(x0 + fx * j, y0 + fy * i, c)
    for i in (4, 15, 25):
        stud(x0 + fx * i - (3 if fx < 0 else 0), y0 + fy * 1 - (3 if fy < 0 else 0), IRON)
        stud(x0 + fx * 1 - (3 if fx < 0 else 0), y0 + fy * i - (3 if fy < 0 else 0), IRON)


bracket(0, 0, 1, 1)
bracket(W - 1, 0, -1, 1)
bracket(0, H - 1, 1, -1)
bracket(W - 1, H - 1, -1, -1)
for y in range(50, 285, 26):                                       # nails down the frame's sides
    nail(4, y)
    nail(W - 6, y)

out = sys.argv[1] if len(sys.argv) > 1 else "assets/ui/inventory_background.png"
Image.fromarray(img).resize((W * 2, H * 2), Image.NEAREST).save(out)
print(out, W * 2, "x", H * 2)
