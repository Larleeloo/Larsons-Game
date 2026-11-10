"""
Larson's Game's card art: a picture for every card and an icon for every
kind of effect, as pixel art in the game's 128 colours.

    python3 tools/cards/make_card_art.py [assets/cards]

Writes, into assets/cards/:

    art.png      every card's picture, 96 x 64, in a grid (ART_COLS across)
    art.json     {"cell": [96, 64], "columns": 16, "cards": {"<card id>": <cell>, ...}}
    icons.png    every effect's icon, 24 x 24, in a row-major grid (ICON_COLS across)
    icons.json   {"cell": 24, "columns": 8, "icons": {"<effect kind>": <cell>, ...}}

A card's picture is its class's scene - the Mage's observatory, the Knight's
battlements, the Warlock's laboratory ... - laid out afresh from the card's
own name (where the gears, the stars, the towers and the lanterns are, which
of the scene's lights it is seen in), with the emblem of what the card does
in the middle and of what else it does in the corners. The same card always
comes out the same.
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
W, H = 96, 64
ART_COLS = 16
ICON = 24
ICON_COLS = 8
SS = 4                      # an icon is drawn at four times its size and reduced

with open(os.path.join(ROOT, "assets", "sprites", "pixel_palette.json")) as _f:
    _p = json.load(_f)
PALETTE = np.array(_p["fills"] + _p["lines"], dtype=np.float64)
LINE = (23, 22, 24)

# --- colours ---------------------------------------------------------------------------

STEEL = ((70, 78, 96), (150, 160, 176), (224, 230, 240))
GOLD = ((140, 92, 26), (226, 170, 60), (255, 230, 140))
RED = ((120, 20, 24), (214, 60, 50), (255, 150, 126))
BLUE = ((28, 56, 150), (72, 130, 236), (170, 210, 255))
GREEN = ((22, 90, 40), (62, 170, 72), (170, 234, 136))
PURPLE = ((76, 22, 128), (160, 72, 222), (226, 170, 255))
BROWN = ((66, 38, 18), (130, 84, 44), (196, 146, 94))
CREAM = ((150, 128, 96), (236, 222, 186), (255, 250, 236))
TEAL = ((18, 86, 92), (67, 159, 157), (160, 230, 220))
ICE = ((80, 140, 200), (170, 220, 250), (244, 252, 255))
BRASS = ((96, 68, 30), (176, 134, 64), (232, 196, 116))
RARITY = {"common": (255, 255, 255), "uncommon": (36, 200, 53), "rare": (93, 134, 250), "epic": (183, 34, 231),
          "legendary": (251, 153, 44), "mythic": (67, 159, 157)}


def snap(rgb):
    """Each pixel of an (h, w, 3) array to the nearest of the 128 colours."""
    flat = rgb.reshape(-1, 1, 3).astype(np.float64)
    d = ((flat - PALETTE[None, :, :]) ** 2 * np.array([0.9, 1.2, 0.8])).sum(axis=2)
    return PALETTE[d.argmin(axis=1)].reshape(rgb.shape).astype(np.uint8)


BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 + 1 / 32.0


def gradient(colours, vertical=True, w=W, h=H, bend=None):
    """A dithered gradient through `colours` (top to bottom): flat bands of each, the next one stippled in."""
    out = np.zeros((h, w, 3), dtype=np.uint8)
    n = len(colours) - 1
    for y in range(h):
        for x in range(w):
            t = (y / (h - 1)) if vertical else (x / (w - 1))
            if bend is not None:
                t = min(1.0, max(0.0, t + bend(x, y)))
            f = t * n
            i = min(n - 1, int(f))
            out[y, x] = colours[i + 1] if (f - i) > BAYER[y % 4, x % 4] else colours[i]
    return out


# --- icons -----------------------------------------------------------------------------

class Pen:
    """An icon being drawn at SS times its size, on a grid of 24."""

    def __init__(self):
        self.im = Image.new("RGBA", (ICON * SS, ICON * SS), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    def _p(self, pts):
        return [(x * SS, y * SS) for x, y in pts]

    def poly(self, pts, c):
        self.d.polygon(self._p(pts), fill=tuple(c) + (255,))

    def line(self, pts, c, w=2.0):
        self.d.line(self._p(pts), fill=tuple(c) + (255,), width=max(1, int(round(w * SS))), joint="curve")
        r = w * SS / 2
        for x, y in self._p(pts):
            self.d.ellipse([x - r, y - r, x + r, y + r], fill=tuple(c) + (255,))

    def circle(self, cx, cy, r, c):
        self.d.ellipse([(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], fill=tuple(c) + (255,))

    def ring(self, cx, cy, r, c, w=1.5):
        self.d.ellipse([(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], outline=tuple(c) + (255,),
                       width=int(round(w * SS)))

    def rect(self, x0, y0, x1, y1, c):
        self.d.rectangle([x0 * SS, y0 * SS, x1 * SS - 1, y1 * SS - 1], fill=tuple(c) + (255,))

    def arc(self, cx, cy, r, a0, a1, c, w=1.5):
        self.d.arc([(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], a0, a1, fill=tuple(c) + (255,),
                   width=int(round(w * SS)))

    def done(self):
        """Reduced to 24 x 24: a pixel is solid where most of its block is, in its block's commonest colour."""
        a = np.asarray(self.im).reshape(ICON, SS, ICON, SS, 4).transpose(0, 2, 1, 3, 4).reshape(ICON, ICON, SS * SS, 4)
        out = np.zeros((ICON, ICON, 4), dtype=np.uint8)
        for y in range(ICON):
            for x in range(ICON):
                block = a[y, x]
                solid = block[block[:, 3] > 127]
                if len(solid) * 2 < SS * SS:
                    continue
                cols, counts = np.unique(solid[:, :3], axis=0, return_counts=True)
                out[y, x, :3] = cols[counts.argmax()]
                out[y, x, 3] = 255
        out[:, :, :3] = snap(out[:, :, :3])
        return outline(out)


def outline(img):
    """A dark line round whatever is drawn, one pixel, where there is room for it."""
    a = img[:, :, 3] > 0
    grown = a.copy()
    grown[1:, :] |= a[:-1, :]
    grown[:-1, :] |= a[1:, :]
    grown[:, 1:] |= a[:, :-1]
    grown[:, :-1] |= a[:, 1:]
    edge = grown & ~a
    out = img.copy()
    out[edge, :3] = LINE
    out[edge, 3] = 255
    return out


def star(p, cx, cy, r, c, points=4, inner=0.38, turn=0.0):
    pts = []
    for i in range(points * 2):
        a = turn + i * math.pi / points - math.pi / 2
        rr = r if i % 2 == 0 else r * inner
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    p.poly(pts, c)


def heart(p, cx, cy, s, tones):
    d, m, l = tones
    p.circle(cx - s * 0.5, cy - s * 0.3, s * 0.56, m)
    p.circle(cx + s * 0.5, cy - s * 0.3, s * 0.56, m)
    p.poly([(cx - s * 1.03, cy - s * 0.1), (cx + s * 1.03, cy - s * 0.1), (cx, cy + s * 1.05)], m)
    p.poly([(cx, cy - s * 0.1), (cx + s * 1.03, cy - s * 0.1), (cx, cy + s * 1.05)], d)
    p.circle(cx + s * 0.5, cy - s * 0.3, s * 0.5, d)
    p.circle(cx + s * 0.38, cy - s * 0.36, s * 0.42, m)
    p.circle(cx - s * 0.55, cy - s * 0.45, s * 0.2, l)


def drop(p, cx, cy, s, tones):
    d, m, l = tones
    p.poly([(cx, cy - s * 1.25), (cx + s * 0.72, cy + s * 0.1), (cx - s * 0.72, cy + s * 0.1)], m)
    p.circle(cx, cy + s * 0.3, s * 0.75, m)
    p.arc(cx, cy + s * 0.3, s * 0.72, -20, 100, d, 1.6)
    p.circle(cx - s * 0.25, cy + s * 0.1, s * 0.18, l)


def bolt(p, cx, cy, s, tones):
    d, m, l = tones
    pts = [(0.25, -1.2), (-0.7, 0.15), (-0.05, 0.15), (-0.35, 1.2), (0.7, -0.25), (0.05, -0.25), (0.6, -1.2)]
    p.poly([(cx + x * s, cy + y * s) for x, y in pts], m)
    p.poly([(cx + x * s, cy + y * s) for x, y in [(-0.05, 0.15), (-0.35, 1.2), (0.7, -0.25), (0.05, -0.25)]], d)
    p.line([(cx + 0.2 * s, cy - 1.0 * s), (cx - 0.35 * s, cy - 0.05 * s)], l, 0.7)


def arrow_mark(p, cx, cy, up, c):
    s = -1 if up else 1
    p.poly([(cx - 3, cy - 1.5 * s), (cx + 3, cy - 1.5 * s), (cx, cy + 3 * s)], c)
    p.rect(cx - 1, min(cy - 1.5 * s, cy - 4.5 * s), cx + 1, max(cy - 1.5 * s, cy - 4.5 * s), c)


def plus_mark(p, cx, cy, c):
    p.rect(cx - 1, cy - 3.5, cx + 1, cy + 3.5, c)
    p.rect(cx - 3.5, cy - 1, cx + 3.5, cy + 1, c)


def sword(p, tones, hilt=GOLD, x0=5.0, y0=19.0, x1=19.5, y1=4.5):
    d, m, l = tones
    dx, dy = x1 - x0, y1 - y0
    n = math.hypot(dx, dy)
    ux, uy, px, py = dx / n, dy / n, -dy / n, dx / n
    g = 0.34                       # where the guard is, along it
    gx, gy = x0 + dx * g, y0 + dy * g
    p.line([(x0, y0), (gx, gy)], BROWN[1], 2.0)
    p.circle(x0, y0, 1.6, hilt[1])
    p.poly([(gx + px * 1.7, gy + py * 1.7), (x1 - ux * 2.5 + px * 1.5, y1 - uy * 2.5 + py * 1.5), (x1, y1),
            (x1 - ux * 2.5 - px * 1.5, y1 - uy * 2.5 - py * 1.5), (gx - px * 1.7, gy - py * 1.7)], m)
    p.poly([(gx, gy), (x1, y1), (x1 - ux * 2.5 - px * 1.5, y1 - uy * 2.5 - py * 1.5), (gx - px * 1.7, gy - py * 1.7)], l)
    p.line([(gx + px * 4, gy + py * 4), (gx - px * 4, gy - py * 4)], hilt[1], 1.8)


def icon_damage(p):
    sword(p, STEEL)
    star(p, 6.5, 6.5, 4.5, GOLD[2])


def icon_melee(p):
    p.line([(6, 20), (17, 5)], BROWN[1], 2.2)
    p.poly([(13, 4), (20, 3), (22, 10), (18, 14), (15, 9)], STEEL[1])
    p.poly([(20, 3), (22, 10), (18, 14), (19, 8)], STEEL[2])
    p.poly([(13, 4), (15, 9), (11, 10), (10, 6)], STEEL[0])


def icon_ranged(p):
    p.arc(8, 12, 10, -62, 62, BROWN[1], 2.2)
    p.line([(12.5, 3.5), (12.5, 20.5)], CREAM[1], 0.8)
    p.line([(3, 12), (20, 12)], BROWN[2], 1.2)
    p.poly([(23, 12), (18.5, 9), (18.5, 15)], STEEL[2])
    p.poly([(3, 12), (1, 9.5), (5, 11)], RED[1])
    p.poly([(3, 12), (1, 14.5), (5, 13)], RED[1])


def icon_spell(p):
    p.line([(5, 20), (14, 10)], BROWN[1], 2.0)
    p.line([(5, 20), (7, 18)], GOLD[1], 2.4)
    star(p, 16, 8, 6.5, PURPLE[2])
    star(p, 16, 8, 3.5, CREAM[2], turn=math.pi / 4)
    p.circle(7, 6, 1.1, BLUE[2])
    p.circle(21, 17, 1.1, BLUE[2])


def icon_mana_cost(p):
    drop(p, 10, 12, 7, BLUE)
    arrow_mark(p, 19, 14, False, GOLD[2])


def icon_stamina_cost(p):
    bolt(p, 10, 12, 8, GREEN)
    arrow_mark(p, 19, 14, False, GOLD[2])


def icon_speed(p):
    p.poly([(8, 6), (14, 6), (14, 14), (21, 16), (21, 20), (8, 20)], BROWN[1])
    p.poly([(8, 17.5), (21, 17.5), (21, 20), (8, 20)], BROWN[0])
    p.rect(8, 6, 14, 8, BROWN[2])
    p.poly([(8, 9), (1, 4), (3, 9), (0.5, 9.5), (4, 12.5), (2, 13.5), (8, 14.5)], CREAM[2])
    p.line([(8, 11.5), (3, 9)], CREAM[0], 0.6)


def icon_defense(p):
    pts = [(12, 2.5), (20.5, 5), (20, 13), (12, 21.5), (4, 13), (3.5, 5)]
    p.poly(pts, STEEL[1])
    p.poly([(12, 2.5), (20.5, 5), (20, 13), (12, 21.5)], STEEL[0])
    p.poly([(12, 5), (18, 6.8), (17.6, 12.4), (12, 18.6), (6.4, 12.4), (6, 6.8)], BLUE[1])
    p.poly([(12, 5), (18, 6.8), (17.6, 12.4), (12, 18.6)], BLUE[0])
    star(p, 12, 11, 4.2, GOLD[2])


def icon_stealth(p):
    p.poly([(1.5, 12), (12, 5), (22.5, 12), (12, 19)], CREAM[2])
    p.circle(12, 12, 4.6, PURPLE[1])
    p.circle(12, 12, 2.2, LINE)
    p.circle(10.6, 10.6, 1.0, CREAM[2])
    p.poly([(1.5, 12), (12, 5), (22.5, 12), (20, 12), (12, 8), (4, 12)], PURPLE[0])
    p.line([(3, 20), (21, 4)], PURPLE[2], 1.6)


def icon_jump(p):
    p.poly([(12, 1.5), (19, 9), (15, 9), (15, 13), (9, 13), (9, 9), (5, 9)], GREEN[2])
    p.poly([(12, 1.5), (19, 9), (15, 9), (15, 13), (12, 13)], GREEN[1])
    for i, y in enumerate((15.5, 18.5, 21.5)):
        p.line([(7 + i, y), (17 - i, y)], STEEL[1 if i % 2 else 2], 1.5)


def icon_breath(p):
    p.circle(9, 14, 6.5, TEAL[1])
    p.circle(9, 14, 5.2, ICE[1])
    p.circle(7, 12, 1.6, ICE[2])
    p.circle(18, 8, 3.8, TEAL[1])
    p.circle(18, 8, 2.7, ICE[1])
    p.circle(17, 7, 0.9, ICE[2])
    p.circle(18.5, 18.5, 2.3, TEAL[1])
    p.circle(18.5, 18.5, 1.3, ICE[2])


def coin(p, cx, cy, r):
    p.circle(cx, cy + 1, r, GOLD[0])
    p.circle(cx, cy, r, GOLD[1])
    p.circle(cx, cy, r - 1.4, GOLD[2])
    p.rect(cx - 0.7, cy - r + 2.6, cx + 0.7, cy + r - 2.6, GOLD[0])


def icon_coins(p):
    for y in (19, 16, 13):
        p.poly([(3, y - 1.5), (13, y - 1.5), (13, y + 1.5), (3, y + 1.5)], GOLD[0])
        p.rect(3, y - 1.5, 13, y + 0.2, GOLD[1])
        p.rect(4, y - 1.5, 9, y - 0.7, GOLD[2])
    coin(p, 16.5, 9, 5.5)


def icon_xp(p):
    star(p, 12, 12.5, 10.5, GOLD[1], points=5, inner=0.45)
    star(p, 12, 12.5, 6.5, GOLD[2], points=5, inner=0.45)
    p.circle(9.5, 9.5, 1.2, CREAM[2])


def icon_dice(p):
    pts = [(12, 2), (21, 7), (21, 17), (12, 22), (3, 17), (3, 7)]
    p.poly(pts, CREAM[1])
    p.poly([(12, 12), (21, 7), (21, 17), (12, 22)], CREAM[0])
    p.poly([(12, 2), (21, 7), (12, 12), (3, 7)], CREAM[2])
    for x, y in ((12, 7), (7, 12.5), (7.5, 17), (16.5, 12.5), (17, 17.5), (16.8, 15)):
        p.circle(x, y, 1.1, RED[0])


def icon_regen_health(p):
    heart(p, 10.5, 12, 7.5, RED)
    plus_mark(p, 19.5, 6, GREEN[2])


def icon_regen_mana(p):
    drop(p, 10, 13, 7, BLUE)
    plus_mark(p, 19.5, 6, GREEN[2])


def icon_regen_stamina(p):
    bolt(p, 10, 13, 8, GREEN)
    plus_mark(p, 19.5, 6, GOLD[2])


def icon_lifesteal(p):
    p.poly([(4, 3), (9, 3), (7.5, 13)], CREAM[2])
    p.poly([(15, 3), (20, 3), (16.5, 13)], CREAM[2])
    p.poly([(6.5, 3), (9, 3), (7.5, 13)], CREAM[0])
    p.poly([(17.5, 3), (20, 3), (16.5, 13)], CREAM[0])
    drop(p, 12, 17, 4.6, RED)


def icon_thorns(p):
    for i in range(8):
        a = i * math.pi / 4
        p.poly([(12 + 6 * math.cos(a - 0.3), 12 + 6 * math.sin(a - 0.3)),
                (12 + 11 * math.cos(a), 12 + 11 * math.sin(a)),
                (12 + 6 * math.cos(a + 0.3), 12 + 6 * math.sin(a + 0.3))], GREEN[2] if i % 2 else GREEN[1])
    p.ring(12, 12, 7, BROWN[1], 2.6)
    p.arc(12, 12, 7, 180, 300, BROWN[2], 1.0)


def icon_freeze(p):
    for i in range(3):
        a = i * math.pi / 3
        dx, dy = math.cos(a), math.sin(a)
        p.line([(12 - 10 * dx, 12 - 10 * dy), (12 + 10 * dx, 12 + 10 * dy)], ICE[1], 1.6)
        for s in (-1, 1):
            bx, by = 12 + s * 6.5 * dx, 12 + s * 6.5 * dy
            for t in (-1, 1):
                b = a + t * 0.9
                p.line([(bx, by), (bx + s * 3 * math.cos(b), by + s * 3 * math.sin(b))], ICE[1], 1.2)
    p.circle(12, 12, 2.4, ICE[2])


def icon_transfigure(p):
    for cx, cy, r in ((8, 13, 4.2), (13, 11.5, 4.8), (17, 13.5, 3.8), (10.5, 15.5, 3.8), (15, 16, 3.6)):
        p.circle(cx, cy, r, CREAM[2])
    p.circle(5.5, 12.5, 2.8, BROWN[0])
    p.rect(8.5, 18, 10, 22, BROWN[0])
    p.rect(15, 18, 16.5, 22, BROWN[0])
    p.circle(13, 15.5, 2.6, CREAM[1])
    star(p, 18.5, 5.5, 4.5, PURPLE[2])
    star(p, 6, 5, 2.6, PURPLE[2])


def icon_poison(p):
    p.poly([(9.5, 3), (14.5, 3), (14.5, 9), (20, 18), (18, 21.5), (6, 21.5), (4, 18), (9.5, 9)], ICE[1])
    p.poly([(7.2, 13.5), (16.8, 13.5), (20, 18), (18, 21.5), (6, 21.5), (4, 18)], GREEN[1])
    p.poly([(12, 13.5), (16.8, 13.5), (20, 18), (18, 21.5), (12, 21.5)], GREEN[0])
    p.rect(8.5, 2, 15.5, 4, BROWN[1])
    p.circle(9.5, 17.5, 1.2, GREEN[2])
    p.circle(13.5, 16.2, 0.9, GREEN[2])
    p.circle(17.5, 8, 1.3, GREEN[2])


def icon_ammo_free(p):
    p.poly([(7, 9), (17, 9), (15.5, 22), (8.5, 22)], BROWN[1])
    p.poly([(12, 9), (17, 9), (15.5, 22), (12, 22)], BROWN[0])
    p.rect(6.5, 9, 17.5, 11, GOLD[1])
    for x, c in ((9, RED[1]), (12, BLUE[1]), (15, GREEN[1])):
        p.line([(x, 9), (x, 4)], BROWN[2], 0.9)
        p.poly([(x - 1.8, 2), (x + 1.8, 2), (x + 1.4, 6), (x - 1.4, 6)], c)
    p.ring(10, 16, 1.7, GOLD[2], 0.8)
    p.ring(13.4, 16, 1.7, GOLD[2], 0.8)


def icon_sneak(p):
    sword(p, STEEL, hilt=STEEL, x0=17.5, y0=5.5, x1=7, y1=19.5)
    p.poly([(1.5, 9), (9, 2), (16, 4), (9, 6), (6, 12)], PURPLE[0])
    p.poly([(9, 2), (16, 4), (9, 6)], PURPLE[1])


def icon_ward(p):
    p.ring(12, 12, 10, TEAL[1], 2.0)
    p.arc(12, 12, 10, 190, 280, TEAL[2], 1.0)
    p.poly([(12, 4.5), (17.5, 12), (12, 19.5), (6.5, 12)], TEAL[2])
    p.poly([(12, 4.5), (17.5, 12), (12, 19.5)], TEAL[1])
    star(p, 12, 12, 3.2, CREAM[2])
    for a in (45, 135, 225, 315):
        r = math.radians(a)
        p.circle(12 + 10 * math.cos(r), 12 + 10 * math.sin(r), 1.5, GOLD[2])


def icon_heal(p):
    heart(p, 12, 12, 9.5, RED)


def icon_mana(p):
    drop(p, 12, 12.5, 9, BLUE)


def icon_stamina(p):
    bolt(p, 12, 12, 9.5, GREEN)


def icon_mana_to_health(p):
    drop(p, 6, 10, 4.6, BLUE)
    heart(p, 17.5, 15, 5.2, RED)
    p.line([(8, 19), (13, 21)], GOLD[2], 1.2)
    p.poly([(15, 22), (11.5, 22.5), (13, 19)], GOLD[2])


def icon_health_to_mana(p):
    heart(p, 6.5, 9, 5.2, RED)
    drop(p, 17.5, 15.5, 4.6, BLUE)
    p.line([(4.5, 17.5), (10, 20.5)], GOLD[2], 1.2)
    p.poly([(12.5, 21.5), (9, 22), (10.5, 18.5)], GOLD[2])


def card_shape(p, cx, cy, turn, tones, mark):
    d, m, l = tones
    c, s = math.cos(turn), math.sin(turn)
    def t(x, y):
        return (cx + x * c - y * s, cy + x * s + y * c)
    p.poly([t(-5, -7.5), t(5, -7.5), t(5, 7.5), t(-5, 7.5)], d)
    p.poly([t(-4, -6.5), t(4, -6.5), t(4, 6.5), t(-4, 6.5)], l)
    p.poly([t(-2.6, -4.8), t(2.6, -4.8), t(2.6, 0.4), t(-2.6, 0.4)], mark)
    p.line([t(-2.4, 2.6), t(2.4, 2.6)], d, 0.6)
    p.line([t(-2.4, 4.4), t(1.2, 4.4)], d, 0.6)


def icon_draw(p):
    card_shape(p, 8.5, 13, -0.32, CREAM, BLUE[1])
    card_shape(p, 15, 11.5, 0.2, CREAM, RED[1])


def icon_coins_now(p):
    p.poly([(8, 8), (16, 8), (20.5, 16), (19, 21.5), (5, 21.5), (3.5, 16)], BROWN[1])
    p.poly([(12, 8), (16, 8), (20.5, 16), (19, 21.5), (12, 21.5)], BROWN[0])
    p.poly([(8.5, 3), (15.5, 3), (14, 8), (10, 8)], BROWN[2])
    p.rect(7.5, 7, 16.5, 9, GOLD[1])
    coin(p, 12, 15, 3.6)


def icon_xp_now(p):
    p.poly([(2, 7), (12, 9.5), (12, 21), (2, 18.5)], CREAM[2])
    p.poly([(22, 7), (12, 9.5), (12, 21), (22, 18.5)], CREAM[1])
    p.line([(2, 18.5), (12, 21), (22, 18.5)], BROWN[1], 1.3)
    for y in (11.5, 13.8, 16):
        p.line([(4, y), (10, y + 1.4)], BROWN[0], 0.5)
    star(p, 17, 7, 5.2, GOLD[2], points=5, inner=0.45)


def icon_breath_now(p):
    p.circle(12, 12, 10, TEAL[1])
    p.circle(12, 12, 8.4, ICE[1])
    p.arc(12, 12, 6.2, 190, 290, ICE[2], 1.6)
    p.circle(15.5, 15.5, 1.2, ICE[2])


# a few more, for the HUD and the menus (not effects of cards)
def icon_card_back(p):
    card_shape(p, 12, 12, 0.0, BRASS, RED[0])


def icon_quest(p):
    p.poly([(4, 3), (20, 3), (20, 21), (4, 21)], CREAM[1])
    p.poly([(12, 3), (20, 3), (20, 21), (12, 21)], CREAM[0])
    for y in (7, 10, 13):
        p.line([(7, y), (17, y)], BROWN[0], 0.6)
    p.circle(15.5, 17, 2.6, RED[1])


def icon_key(p):
    p.ring(7.5, 8, 4.5, GOLD[1], 2.2)
    p.line([(10.5, 11), (20, 20)], GOLD[1], 2.0)
    p.line([(17, 17), (19.5, 14.5)], GOLD[1], 1.6)
    p.line([(14.5, 14.5), (16.5, 12.5)], GOLD[1], 1.6)
    p.arc(7.5, 8, 4.5, 180, 270, GOLD[2], 1.0)


def icon_skull(p):
    p.circle(12, 10, 8.5, CREAM[2])
    p.poly([(7, 14), (17, 14), (16, 21), (8, 21)], CREAM[2])
    p.circle(8.5, 10.5, 2.5, LINE)
    p.circle(15.5, 10.5, 2.5, LINE)
    p.poly([(12, 13), (13.4, 16), (10.6, 16)], LINE)
    for x in (10, 12, 14):
        p.line([(x, 18), (x, 21)], CREAM[0], 0.5)


def icon_potion(p):
    p.poly([(10, 3), (14, 3), (14, 8), (19.5, 13), (19.5, 19), (16.5, 22), (7.5, 22), (4.5, 19), (4.5, 13), (10, 8)], ICE[1])
    p.poly([(5, 14), (19, 14), (19.5, 19), (16.5, 22), (7.5, 22), (4.5, 19)], RED[1])
    p.poly([(12, 14), (19, 14), (19.5, 19), (16.5, 22), (12, 22)], RED[0])
    p.rect(9, 1.5, 15, 3.6, BROWN[1])
    p.circle(8.5, 17.5, 1.3, RED[2])


def icon_fish(p):
    p.poly([(3, 12), (9, 6.5), (16, 8), (18, 12), (16, 16), (9, 17.5)], BLUE[1])
    p.poly([(3, 12), (9, 12.5), (16, 12), (18, 12), (16, 16), (9, 17.5)], BLUE[0])
    p.poly([(17, 12), (22.5, 6.5), (21, 12), (22.5, 17.5)], BLUE[2])
    p.circle(7, 11, 1.1, CREAM[2])


def icon_hammer(p):
    p.line([(5, 21), (14, 9)], BROWN[1], 2.2)
    p.poly([(8, 5), (16, 2), (21, 8), (13, 12)], STEEL[1])
    p.poly([(16, 2), (21, 8), (17, 10), (12.5, 4)], STEEL[2])


def icon_zombie(p):
    p.circle(12, 10, 8, GREEN[1])
    p.poly([(7.5, 14), (16.5, 14), (15.5, 21), (8.5, 21)], GREEN[1])
    p.poly([(12, 2), (20, 10), (16.5, 14), (15.5, 21), (12, 21)], GREEN[0])
    p.circle(8.8, 10.5, 2.3, LINE)
    p.circle(15.4, 10, 2.0, LINE)
    p.circle(8.8, 10.5, 0.9, RED[2])
    p.circle(15.4, 10, 0.8, RED[2])
    p.line([(9, 17.5), (15, 17.5)], LINE, 0.8)


ICONS = [
    ("damage", icon_damage), ("melee_damage", icon_melee), ("ranged_damage", icon_ranged),
    ("spell_damage", icon_spell), ("mana_cost", icon_mana_cost), ("stamina_cost", icon_stamina_cost),
    ("speed", icon_speed), ("defense", icon_defense), ("stealth", icon_stealth), ("jump", icon_jump),
    ("breath", icon_breath), ("coins", icon_coins), ("xp", icon_xp), ("extra_dice", icon_dice),
    ("regen_health", icon_regen_health), ("regen_mana", icon_regen_mana), ("regen_stamina", icon_regen_stamina),
    ("lifesteal", icon_lifesteal), ("thorns", icon_thorns), ("freeze", icon_freeze),
    ("transfigure", icon_transfigure), ("poison", icon_poison), ("ammo_free", icon_ammo_free),
    ("sneak", icon_sneak), ("ward", icon_ward), ("heal", icon_heal), ("mana", icon_mana),
    ("stamina", icon_stamina), ("mana_to_health", icon_mana_to_health), ("health_to_mana", icon_health_to_mana),
    ("draw", icon_draw), ("coins_now", icon_coins_now), ("xp_now", icon_xp_now), ("breath_now", icon_breath_now),
    ("card_back", icon_card_back), ("quest", icon_quest), ("key", icon_key), ("skull", icon_skull),
    ("potion", icon_potion), ("fish", icon_fish), ("hammer", icon_hammer), ("zombie", icon_zombie),
]


def make_icons():
    out = {}
    for name, fn in ICONS:
        p = Pen()
        fn(p)
        out[name] = p.done()
    return out


# --- scenes ----------------------------------------------------------------------------

def blank(colours, **kw):
    return Image.fromarray(gradient(colours, **kw), "RGB")


def gear(d, cx, cy, r, teeth, c, hub=None, turn=0.0):
    """A cog: its teeth, its rim, its spokes and its hub."""
    for i in range(teeth):
        a = turn + i * 2 * math.pi / teeth
        x, y = cx + (r + 1.5) * math.cos(a), cy + (r + 1.5) * math.sin(a)
        d.rectangle([x - 1.5, y - 1.5, x + 1.5, y + 1.5], fill=c)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=c)
    inner = r * 0.62
    if hub is not None and r >= 5:
        d.ellipse([cx - inner, cy - inner, cx + inner, cy + inner], fill=hub)
        for i in range(4):
            a = turn + i * math.pi / 2
            d.line([cx, cy, cx + inner * math.cos(a), cy + inner * math.sin(a)], fill=c, width=2 if r > 8 else 1)
        d.ellipse([cx - r * 0.22, cy - r * 0.22, cx + r * 0.22, cy + r * 0.22], fill=c)


def planet(d, cx, cy, r, rng):
    """The gas giant the moon goes round: banded, a storm on it, its far side in shadow."""
    bands = [(214, 146, 90), (237, 178, 81), (184, 162, 140), (196, 110, 70), (236, 222, 186)]
    for y in range(-r, r + 1):
        half = int(math.sqrt(max(0, r * r - y * y)))
        c = bands[(y + r) * len(bands) // (2 * r + 1) % len(bands)]
        d.line([cx - half, cy + y, cx + half, cy + y], fill=c)
        d.line([cx + half * 0.45, cy + y, cx + half, cy + y], fill=tuple(int(v * 0.62) for v in c))
    d.ellipse([cx - r * 0.5, cy + r * 0.15, cx - r * 0.1, cy + r * 0.4], fill=(170, 60, 40))


def stars(d, rng, n, top=0, bottom=H // 2, colours=((255, 250, 236), (170, 210, 255), (255, 230, 140))):
    for _ in range(n):
        x, y = rng.randrange(W), rng.randrange(top, max(top + 1, bottom))
        d.point((x, y), fill=rng.choice(colours))
        if rng.random() < 0.15:
            d.point((x + 1, y), fill=colours[0])
            d.point((x, y + 1), fill=colours[0])


def pipes(d, rng, n, c, light):
    """Steam pipes down the sides and across the top, with their collars."""
    for _ in range(n):
        if rng.random() < 0.5:
            x = rng.choice([rng.randrange(2, 20), rng.randrange(W - 22, W - 4)])
            d.rectangle([x, 0, x + 2, H], fill=c)
            d.line([x, 0, x, H], fill=light)
            for y in range(rng.randrange(6, 14), H, rng.randrange(14, 22)):
                d.rectangle([x - 1, y, x + 3, y + 1], fill=light)
        else:
            y = rng.randrange(2, 12)
            d.rectangle([0, y, W, y + 2], fill=c)
            d.line([0, y, W, y], fill=light)
            for x in range(rng.randrange(4, 16), W, rng.randrange(18, 30)):
                d.rectangle([x, y - 1, x + 1, y + 3], fill=light)


def scene_mage(rng, mood):
    skies = [[(20, 14, 52), (44, 34, 110), (76, 62, 160), (150, 110, 190)],
             [(8, 20, 48), (20, 52, 110), (40, 100, 170), (120, 180, 220)],
             [(34, 10, 44), (86, 30, 100), (150, 60, 140), (236, 150, 150)]]
    im = blank(skies[mood])
    d = ImageDraw.Draw(im)
    stars(d, rng, 40, bottom=44)
    if rng.random() < 0.7:
        planet(d, rng.randrange(14, 82), rng.randrange(10, 22), rng.randrange(7, 12), rng)
    # the observatory: a tower with a brass dome and its telescope, somewhere along the hill
    hill = (24, 20, 40)
    base = rng.randrange(38, 50)
    d.polygon([(0, H), (0, base + 10), (30, base + 2), (60, base + 6), (W, base - 2), (W, H)], fill=hill)
    tx = rng.choice([rng.randrange(8, 26), rng.randrange(62, 80)])
    d.rectangle([tx, base - 14, tx + 10, base + 8], fill=(52, 44, 80))
    d.pieslice([tx - 2, base - 22, tx + 12, base - 8], 180, 360, fill=BRASS[1])
    d.line([tx + 5, base - 16, tx + 16, base - 26], fill=BRASS[2], width=2)
    d.rectangle([tx + 4, base - 8, tx + 6, base - 4], fill=(255, 230, 140))
    # an orrery's rings hanging in the sky
    cx, cy = rng.randrange(30, 66), rng.randrange(18, 34)
    for r in (rng.randrange(15, 19), rng.randrange(21, 26)):
        d.arc([cx - r, cy - r * 0.45, cx + r, cy + r * 0.45], 0, 360, fill=BRASS[1 + (r % 2)])
    return im


def scene_bard(rng, mood):
    walls = [[(60, 30, 18), (110, 62, 30), (150, 96, 50)], [(52, 24, 30), (104, 46, 46), (160, 84, 60)],
             [(40, 32, 20), (86, 70, 36), (140, 116, 60)]]
    im = blank(walls[mood])
    d = ImageDraw.Draw(im)
    # the stage's curtain, swagged either side
    cur = [(120, 20, 24), (170, 40, 40)] if mood != 2 else [(30, 60, 120), (60, 100, 170)]
    for side in (0, 1):
        for i in range(5):
            x = i * 5 if side == 0 else W - 1 - i * 5
            w = 22 - i * 3 + rng.randrange(0, 3)
            d.rectangle([min(x, x + (4 if side == 0 else -4)), 0, max(x, x + (4 if side == 0 else -4)), w + 22],
                        fill=cur[i % 2])
    d.rectangle([0, 0, W, 5], fill=cur[0])
    for x in range(0, W, 8):
        d.polygon([(x, 5), (x + 8, 5), (x + 4, 10)], fill=cur[1])
    # the boards, and lanterns hung on chains
    d.rectangle([0, 50, W, H], fill=(70, 42, 22))
    for x in range(rng.randrange(0, 9), W, 12):
        d.line([x, 50, x - 6, H], fill=(46, 26, 14))
    for _ in range(rng.randrange(2, 5)):
        x, y = rng.randrange(24, 72), rng.randrange(10, 24)
        d.line([x, 6, x, y], fill=(40, 30, 20))
        d.rectangle([x - 2, y, x + 2, y + 5], fill=(255, 230, 140))
        d.rectangle([x - 3, y - 1, x + 3, y], fill=BRASS[0])
        d.rectangle([x - 3, y + 5, x + 3, y + 6], fill=BRASS[0])
    # notes drifting up
    for _ in range(rng.randrange(3, 7)):
        x, y = rng.randrange(20, 76), rng.randrange(14, 46)
        d.ellipse([x, y + 4, x + 3, y + 6], fill=(255, 250, 236))
        d.line([x + 3, y + 5, x + 3, y], fill=(255, 250, 236))
        d.line([x + 3, y, x + 6, y + 1], fill=(255, 250, 236))
    return im


def scene_knight(rng, mood):
    skies = [[(60, 110, 190), (120, 170, 230), (200, 224, 244)], [(190, 90, 60), (236, 150, 90), (255, 220, 150)],
             [(50, 60, 90), (100, 110, 140), (170, 176, 190)]]
    im = blank(skies[mood])
    d = ImageDraw.Draw(im)
    for _ in range(rng.randrange(2, 5)):                       # clouds
        x, y, w = rng.randrange(-6, 80), rng.randrange(4, 22), rng.randrange(14, 28)
        d.ellipse([x, y, x + w, y + 6], fill=(255, 250, 236))
        d.ellipse([x + 4, y - 3, x + w - 6, y + 4], fill=(255, 250, 236))
    stone, shade, dark = (150, 160, 176), (104, 112, 132), (70, 78, 96)
    top = rng.randrange(34, 42)
    d.rectangle([0, top, W, H], fill=stone)
    for x in range(-4 + rng.randrange(0, 6), W, 12):            # the battlements
        d.rectangle([x, top - 6, x + 6, top], fill=stone)
        d.line([x + 6, top - 6, x + 6, top], fill=shade)
    for y in range(top + 5, H, 6):                              # the courses
        d.line([0, y, W, y], fill=shade)
        for x in range((y // 6 % 2) * 6 + rng.randrange(0, 2), W, 12):
            d.line([x, y - 5, x, y], fill=shade)
    for _ in range(rng.randrange(1, 4)):                        # towers, with banners
        x, w, h = rng.randrange(4, 80), rng.randrange(10, 16), rng.randrange(18, 30)
        d.rectangle([x, top - h, x + w, H], fill=shade)
        d.rectangle([x - 1, top - h - 3, x + w + 1, top - h], fill=dark)
        for bx in range(x - 1, x + w + 1, 4):
            d.rectangle([bx, top - h - 6, bx + 1, top - h - 3], fill=dark)
        d.rectangle([x + w // 2 - 1, top - h + 6, x + w // 2, top - h + 11], fill=LINE)
        d.line([x + w // 2, top - h - 6, x + w // 2, top - h - 16], fill=dark)
        c = rng.choice([RED[1], BLUE[1], GOLD[1], GREEN[1]])
        d.polygon([(x + w // 2 + 1, top - h - 16), (x + w // 2 + 9, top - h - 13), (x + w // 2 + 1, top - h - 10)], fill=c)
    return im


def scene_warrior(rng, mood):
    skies = [[(30, 8, 8), (90, 22, 14), (170, 56, 24), (240, 130, 40)], [(20, 12, 16), (64, 30, 30), (130, 60, 40), (220, 150, 80)],
             [(40, 10, 30), (100, 24, 40), (190, 60, 50), (250, 170, 90)]]
    im = blank(skies[mood])
    d = ImageDraw.Draw(im)
    for _ in range(rng.randrange(14, 30)):                      # embers
        x, y = rng.randrange(W), rng.randrange(H)
        d.point((x, y), fill=rng.choice([(255, 230, 140), (255, 150, 60), (240, 90, 40)]))
    dark = (22, 14, 14)
    for _ in range(rng.randrange(2, 4)):                        # chimneys, smoking
        x, w, h = rng.randrange(2, 84), rng.randrange(7, 12), rng.randrange(22, 40)
        d.rectangle([x, H - h, x + w, H], fill=dark)
        d.rectangle([x - 1, H - h - 2, x + w + 1, H - h], fill=(46, 30, 26))
        for i in range(rng.randrange(3, 6)):
            r = 3 + i
            d.ellipse([x + w // 2 - r + i * 2, H - h - 6 - i * 5 - r, x + w // 2 + r + i * 2, H - h - 6 - i * 5 + r],
                      fill=(70, 50, 50) if i % 2 else (96, 70, 64))
    # the anvil, lit from the forge
    ax = rng.randrange(28, 52)
    d.polygon([(ax, 46), (ax + 22, 46), (ax + 26, 49), (ax + 16, 51), (ax + 16, 56), (ax + 20, 60), (ax + 2, 60),
               (ax + 6, 56), (ax + 6, 51), (ax - 4, 49)], fill=(40, 36, 44))
    d.line([ax, 46, ax + 22, 46], fill=(240, 150, 80))
    d.rectangle([0, 60, W, H], fill=dark)
    gear(d, rng.choice([8, 88]), rng.randrange(44, 58), rng.randrange(7, 11), 8, (60, 30, 24), (30, 14, 12),
         rng.random())
    return im


def scene_warlock(rng, mood):
    murk = [[(18, 8, 30), (50, 20, 80), (90, 40, 130)], [(8, 24, 20), (20, 60, 46), (50, 110, 70)],
            [(30, 8, 24), (80, 20, 60), (130, 40, 90)]]
    im = blank(murk[mood])
    d = ImageDraw.Draw(im)
    pipes(d, rng, rng.randrange(2, 5), (40, 30, 50), BRASS[0])
    glow = [(120, 230, 110), (200, 120, 255), (255, 120, 150)][mood]
    deep = [(40, 130, 60), (110, 50, 180), (170, 40, 80)][mood]
    for _ in range(rng.randrange(2, 4)):                        # vats, bubbling
        x, w, h = rng.randrange(4, 78), rng.randrange(10, 16), rng.randrange(16, 26)
        d.rectangle([x, H - 6 - h, x + w, H - 6], fill=(170, 220, 250))
        d.rectangle([x + 1, H - 6 - h + 4, x + w - 1, H - 7], fill=deep)
        d.line([x + 2, H - 6 - h + 4, x + w - 2, H - 6 - h + 4], fill=glow)
        d.rectangle([x - 1, H - 6 - h - 2, x + w + 1, H - 6 - h], fill=BRASS[1])
        d.rectangle([x - 1, H - 7, x + w + 1, H - 5], fill=BRASS[1])
        for _ in range(3):
            d.point((rng.randrange(x + 2, x + w - 1), rng.randrange(H - h, H - 8)), fill=glow)
    d.rectangle([0, H - 5, W, H], fill=(20, 12, 24))
    # a rune circle on the far wall
    cx, cy, r = rng.randrange(30, 66), rng.randrange(18, 30), rng.randrange(11, 16)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=glow)
    d.ellipse([cx - r + 3, cy - r + 3, cx + r - 3, cy + r - 3], outline=deep)
    for i in range(5):
        a, b = i * 2 * math.pi / 5 - math.pi / 2, (i + 2) * 2 * math.pi / 5 - math.pi / 2
        d.line([cx + (r - 3) * math.cos(a), cy + (r - 3) * math.sin(a), cx + (r - 3) * math.cos(b),
                cy + (r - 3) * math.sin(b)], fill=deep)
    return im


def scene_druid(rng, mood):
    airs = [[(170, 220, 130), (90, 170, 80), (40, 110, 56), (20, 66, 40)], [(236, 222, 150), (190, 170, 70), (120, 110, 40), (60, 60, 26)],
            [(120, 200, 200), (60, 150, 140), (30, 96, 96), (16, 56, 60)]]
    im = blank(airs[mood])
    d = ImageDraw.Draw(im)
    trunk, leaf, leaf2 = (56, 36, 20), airs[mood][3], airs[mood][2]
    for _ in range(rng.randrange(3, 6)):                        # trunks, and the canopy over them
        x, w = rng.randrange(-4, W), rng.randrange(4, 9)
        d.rectangle([x, 8, x + w, H], fill=trunk)
        d.line([x + w, 8, x + w, H], fill=(36, 22, 12))
    for _ in range(rng.randrange(10, 16)):
        x, y, r = rng.randrange(-6, W), rng.randrange(-6, 16), rng.randrange(7, 13)
        d.ellipse([x, y, x + r * 2, y + r], fill=rng.choice([leaf, leaf2]))
    for _ in range(rng.randrange(2, 5)):                        # shafts of light
        x = rng.randrange(10, 86)
        for y in range(0, H):
            if (y + x) % 3 == 0:
                d.point((x + y // 3, y), fill=(255, 250, 200))
    stone = (150, 160, 150)
    for _ in range(rng.randrange(2, 5)):                        # standing stones
        x, w, h = rng.randrange(6, 84), rng.randrange(5, 9), rng.randrange(12, 24)
        d.polygon([(x, H - 6), (x + 1, H - 6 - h), (x + w - 1, H - 6 - h + 2), (x + w, H - 6)], fill=stone)
        d.line([x + w, H - 6, x + w - 1, H - 6 - h + 2], fill=(96, 104, 100))
        d.arc([x + 1, H - 6 - h + 4, x + w - 1, H - 6 - h + 4 + w - 2], 0, 360, fill=(60, 170, 140))
    d.rectangle([0, H - 6, W, H], fill=(30, 70, 36))
    for x in range(0, W, 3):
        d.line([x, H - 6, x + rng.randrange(-1, 2), H - 9 - rng.randrange(0, 3)], fill=(60, 140, 60))
    return im


def scene_rogue(rng, mood):
    nights = [[(10, 12, 30), (24, 30, 66), (50, 60, 110)], [(20, 10, 30), (50, 26, 66), (96, 56, 110)],
              [(8, 20, 26), (20, 50, 60), (46, 90, 100)]]
    im = blank(nights[mood])
    d = ImageDraw.Draw(im)
    stars(d, rng, 30, bottom=36)
    planet(d, rng.randrange(16, 80), rng.randrange(10, 20), rng.randrange(8, 13), rng)
    dark, mid = (14, 14, 22), (30, 30, 46)
    x = -4
    while x < W:                                                # the rooftops
        w, h = rng.randrange(12, 24), rng.randrange(14, 30)
        if rng.random() < 0.5:
            d.polygon([(x, H - h + 8), (x + w // 2, H - h), (x + w, H - h + 8), (x + w, H), (x, H)], fill=dark)
        else:
            d.rectangle([x, H - h + 4, x + w, H], fill=dark)
        if rng.random() < 0.7:                                  # a chimney and its steam
            cx = x + rng.randrange(2, max(3, w - 4))
            d.rectangle([cx, H - h - 4, cx + 3, H - h + 6], fill=mid)
            for i in range(3):
                d.ellipse([cx - 1 + i, H - h - 9 - i * 4, cx + 4 + i, H - h - 5 - i * 4], fill=(96, 100, 130))
        for _ in range(rng.randrange(0, 3)):                    # lit windows
            wx, wy = x + rng.randrange(2, max(3, w - 3)), H - rng.randrange(4, max(5, h - 6))
            d.rectangle([wx, wy, wx + 1, wy + 2], fill=(255, 220, 120))
        x += w + rng.randrange(0, 3)
    pipes(d, rng, 1, (26, 26, 40), BRASS[0])
    return im


def scene_ranger(rng, mood):
    skies = [[(250, 190, 110), (240, 140, 90), (170, 90, 110), (80, 60, 110)], [(150, 200, 240), (190, 224, 240), (236, 240, 220)],
             [(60, 80, 130), (120, 130, 170), (220, 170, 160), (250, 220, 170)]]
    im = blank(skies[mood])
    d = ImageDraw.Draw(im)
    if rng.random() < 0.6:
        planet(d, rng.randrange(14, 82), rng.randrange(10, 20), rng.randrange(6, 10), rng)
    far, mid, near = [(110, 120, 150), (60, 100, 80), (26, 60, 40)]
    for colour, base, amp in ((far, 34, 8), (mid, 44, 6), (near, 54, 4)):       # three ranges of hills
        ph, f = rng.random() * 6, rng.uniform(0.05, 0.11)
        pts = [(0, H)] + [(x, base + amp * math.sin(ph + x * f) + amp * 0.4 * math.sin(ph * 2 + x * f * 2.7))
                          for x in range(0, W + 1, 2)] + [(W, H)]
        d.polygon(pts, fill=colour)
        if colour != far:
            for _ in range(rng.randrange(5, 10)):                               # pines on them
                x = rng.randrange(2, W - 2)
                y = base + amp * math.sin(ph + x * f) + amp * 0.4 * math.sin(ph * 2 + x * f * 2.7)
                h = rng.randrange(6, 11) if colour == near else rng.randrange(4, 7)
                d.polygon([(x - h // 3, y + 1), (x, y - h), (x + h // 3, y + 1)],
                          fill=(16, 44, 30) if colour == near else (40, 76, 60))
    tx = rng.choice([rng.randrange(8, 24), rng.randrange(66, 84)])              # a watchtower
    d.rectangle([tx, 26, tx + 5, 50], fill=(70, 46, 26))
    d.rectangle([tx - 3, 22, tx + 8, 27], fill=(96, 62, 34))
    d.polygon([(tx - 4, 22), (tx + 2.5, 15), (tx + 9, 22)], fill=(120, 30, 30))
    d.rectangle([tx + 2, 23, tx + 3, 25], fill=(255, 230, 140))
    return im


def scene_cleric(rng, mood):
    walls = [[(60, 50, 70), (110, 96, 110), (170, 150, 140)], [(70, 56, 40), (130, 104, 70), (190, 160, 110)],
             [(40, 50, 76), (80, 96, 130), (140, 160, 190)]]
    im = blank(walls[mood])
    d = ImageDraw.Draw(im)
    glass = [RED[1], BLUE[1], GOLD[1], GREEN[1], PURPLE[1], TEAL[1], (255, 250, 236)]
    n = rng.choice([1, 3])
    for i in range(n):                                          # stained glass, under pointed arches
        w = 26 if n == 1 else 16
        x = (W - w) // 2 if n == 1 else 12 + i * 28
        top = rng.randrange(6, 12)
        d.polygon([(x, top + w // 2), (x + w // 2, top - 4), (x + w, top + w // 2), (x + w, 50), (x, 50)], fill=LINE)
        for yy in range(top, 50, 5):
            for xx in range(x + 1, x + w - 1, 5):
                if yy < top + w // 2 and abs(xx + 2 - (x + w // 2)) > (yy - top + 4):
                    continue
                d.rectangle([xx, yy, xx + 3, yy + 3], fill=rng.choice(glass))
        d.line([x + w // 2, top - 2, x + w // 2, 50], fill=LINE)
        for y in range(52, H):                                  # its light on the floor
            if y % 2 == 0:
                d.line([x - (y - 50), y, x + w + (y - 50), y], fill=(255, 230, 160))
    for x in (2, W - 8):                                        # pillars
        d.rectangle([x, 0, x + 5, H], fill=walls[mood][2])
        d.line([x + 5, 0, x + 5, H], fill=walls[mood][0])
        d.rectangle([x - 1, H - 6, x + 6, H], fill=BRASS[1])
    for _ in range(rng.randrange(2, 5)):                        # candles
        x = rng.randrange(14, 82)
        d.rectangle([x, H - 9, x + 1, H - 3], fill=(255, 250, 236))
        d.point((x, H - 10), fill=(255, 190, 80))
        d.point((x, H - 11), fill=(255, 240, 160))
    return im


def scene_neutral(rng, mood):
    sheets = [[(150, 110, 60), (190, 150, 90), (226, 196, 140)], [(110, 90, 80), (150, 126, 106), (200, 180, 150)],
              [(96, 110, 96), (140, 150, 120), (196, 200, 160)]]
    im = blank(sheets[mood], vertical=False)
    d = ImageDraw.Draw(im)
    for _ in range(rng.randrange(4, 8)):                        # a clockwork: cogs of every size
        r = rng.randrange(5, 15)
        gear(d, rng.randrange(0, W), rng.randrange(0, H), r, max(6, r), rng.choice([BRASS[0], BRASS[1], (96, 60, 40)]),
             sheets[mood][0], rng.random())
    pipes(d, rng, rng.randrange(1, 3), BRASS[0], BRASS[2])
    return im


SCENES = {"mage": scene_mage, "bard": scene_bard, "knight": scene_knight, "warrior": scene_warrior,
          "warlock": scene_warlock, "druid": scene_druid, "rogue": scene_rogue, "ranger": scene_ranger,
          "cleric": scene_cleric, None: scene_neutral}


def paste(canvas, icon, cx, cy, scale):
    """An icon onto an (h, w, 3) picture, its middle at (cx, cy), each of its pixels `scale` across."""
    big = np.repeat(np.repeat(icon, scale, axis=0), scale, axis=1)
    h, w = big.shape[:2]
    x0, y0 = int(cx - w / 2), int(cy - h / 2)
    for y in range(h):
        for x in range(w):
            yy, xx = y0 + y, x0 + x
            if 0 <= yy < canvas.shape[0] and 0 <= xx < canvas.shape[1] and big[y, x, 3] > 0:
                canvas[yy, xx] = big[y, x, :3]


def card_art(card, icons):
    seed = int(hashlib.sha1(card["id"].encode()).hexdigest()[:12], 16)
    rng = random.Random(seed)
    cls = card.get("class")
    im = SCENES.get(cls, scene_neutral)(rng, seed % 3)
    a = np.asarray(im).astype(np.float64)
    # darker toward its corners, and a glow in its rarity's colour behind the emblem
    yy, xx = np.mgrid[0:H, 0:W]
    r = np.hypot((xx - W / 2) / (W / 2), (yy - H / 2) / (H / 2))
    a *= np.clip(1.12 - 0.42 * r ** 2, 0.5, 1.0)[:, :, None]
    glow = np.array(RARITY.get(card.get("rarity", "common"), RARITY["common"]), dtype=np.float64)
    g = np.clip(1 - np.hypot(xx - W / 2, (yy - H / 2) * 1.1) / 27.0, 0, 1) ** 1.5
    stipple = (g * 0.85 > BAYER[yy % 4, xx % 4]).astype(np.float64) * 0.55 + g * 0.25
    a = a * (1 - stipple[:, :, None]) + glow * 0.9 * stipple[:, :, None] + a * 0.1 * stipple[:, :, None]
    out = snap(np.clip(a, 0, 255))
    kinds = [e["kind"] for e in card.get("effects", [])]
    main = icons.get(kinds[0] if kinds else "draw", icons["draw"])
    paste(out, main, W // 2, H // 2, 2)
    for k, at in zip(kinds[1:3], ((W - 15, H - 15), (15, H - 15))):
        if k in icons and k != kinds[0]:
            paste(out, icons[k], at[0], at[1], 1)
    return out


def main(folder):
    with open(os.path.join(folder, "cards.json")) as f:
        book = json.load(f)
    icons = make_icons()
    names = [n for n, _ in ICONS]
    rows = (len(names) + ICON_COLS - 1) // ICON_COLS
    sheet = np.zeros((rows * ICON, ICON_COLS * ICON, 4), dtype=np.uint8)
    for i, n in enumerate(names):
        y, x = (i // ICON_COLS) * ICON, (i % ICON_COLS) * ICON
        sheet[y:y + ICON, x:x + ICON] = icons[n]
    Image.fromarray(sheet, "RGBA").save(os.path.join(folder, "icons.png"), optimize=True)
    with open(os.path.join(folder, "icons.json"), "w") as f:
        json.dump({"cell": ICON, "columns": ICON_COLS, "icons": {n: i for i, n in enumerate(names)}}, f, indent=1)
        f.write("\n")

    cards = book["cards"]
    rows = (len(cards) + ART_COLS - 1) // ART_COLS
    atlas = np.zeros((rows * H, ART_COLS * W, 3), dtype=np.uint8)
    index = {}
    for i, c in enumerate(cards):
        y, x = (i // ART_COLS) * H, (i % ART_COLS) * W
        atlas[y:y + H, x:x + W] = card_art(c, icons)
        index[c["id"]] = i
    Image.fromarray(atlas, "RGB").save(os.path.join(folder, "art.png"), optimize=True)
    with open(os.path.join(folder, "art.json"), "w") as f:
        json.dump({"cell": [W, H], "columns": ART_COLS, "cards": index}, f, indent=1)
        f.write("\n")
    print(f"{len(cards)} pictures, {len(names)} icons -> {folder}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "assets", "cards"))
