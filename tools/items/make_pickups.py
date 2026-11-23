#!/usr/bin/env python3
"""Hand-pixelled pickups: the supplies, the makings and the field kits.

The weapons, the clothes and the food are rendered from Blender models
(3D-Modeling's items/item_spin_sprites.py). These are drawn instead, as pixel
art on a 32-pixel grid - a coin, a bundle of arrows, the upgrade ingot as a
shining gear, a lump of ore - and turned like a coin turns: squeezed to its
edge and round to its back, a little thickness showing as it goes, outlined
again in every frame. Each logical pixel is four of the sheet's, so they read
as big, chunky pixel art beside the rendered things.

Every colour is one of the game's 128 (assets/sprites/pixel_palette.json),
six shades to a material as the rendered sheets have them, so a material is
painted its item's colour in the game (TierLook.paint) the same way.

    python3 tools/items/make_pickups.py                 # every pickup, into assets/sprites/items
    python3 tools/items/make_pickups.py supply_coins    # only these
    python3 tools/items/make_pickups.py preview OUT.png # a sheet of them all, to look at
"""

import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
ITEMS = os.path.join(ROOT, "assets", "sprites", "items")
PALETTE = os.path.join(ROOT, "assets", "sprites", "pixel_palette.json")

N = 32            # the art's grid
SCALE = 4         # sheet pixels to one of the grid's: 128-pixel frames
FRAMES = 32       # once round
COLUMNS = 8
# the six shades of a material, as the rendered sheets have them
DARK, MID, LIGHT, HIGH, INNER, OUTLINE = range(6)
LIGHT_DIR = (-0.62, -0.78)   # the light comes from the top left


# --- colour ---------------------------------------------------------------------------

def _lin(c):
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _gam(c):
    c = max(0.0, min(1.0, c))
    return 255.0 * (12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055)


def oklab(rgb):
    r, g, b = (_lin(float(v)) for v in rgb)
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l, m, s = (math.copysign(abs(v) ** (1 / 3), v) for v in (l, m, s))
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)


def from_oklab(lab):
    L, a, b = lab
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    bb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    return tuple(int(round(_gam(v))) for v in (r, g, bb))


class Palette:
    """The game's 128 colours: 100 fills and 28 line darks."""

    def __init__(self, path=PALETTE):
        with open(path) as f:
            j = json.load(f)
        self.fills = [tuple(c) for c in j["fills"]]
        self.lines = [tuple(c) for c in j["lines"]]
        self.lab = {c: oklab(c) for c in self.fills + self.lines}

    def near(self, lab, pool, avoid=(), lighter_than=None, darker_than=None):
        best, bd = None, 1e9
        for c in pool:
            if c in avoid:
                continue
            cl = self.lab[c]
            if lighter_than is not None and cl[0] <= lighter_than:
                continue
            if darker_than is not None and cl[0] >= darker_than:
                continue
            d = (cl[0] - lab[0]) ** 2 + ((cl[1] - lab[1]) ** 2 + (cl[2] - lab[2]) ** 2) * 3.0
            if d < bd:
                best, bd = c, d
        return best

    def ramp(self, rgb):
        """Six shades of {@code rgb}: dark, mid, light, highlight, inner line, outline - all of the 128."""
        L, a, b = oklab(((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255))

        def at(dl, k):
            return (max(0.02, min(0.985, L + dl)), a * k, b * k)

        mid = self.near((L, a, b), self.fills)
        lm = self.lab[mid][0]
        dark = self.near(at(-0.13, 1.0), self.fills, avoid=(mid,), darker_than=lm) or self.near(at(-0.13, 1.0), self.fills)
        light = self.near(at(0.09, 0.9), self.fills, avoid=(mid,), lighter_than=lm) or mid
        high = self.near(at(0.19, 0.55), self.fills, avoid=(mid, light),
                         lighter_than=self.lab[light][0]) or light
        inner = self.near(at(-0.30, 0.8), self.fills + self.lines, darker_than=self.lab[dark][0]) or dark
        outline = self.near((min(L * 0.32, 0.22), a * 0.5, b * 0.5), self.lines)
        return [dark, mid, light, high, inner, outline]


# --- the art --------------------------------------------------------------------------

class Art:
    """A 32-pixel drawing: each pixel a material and a shade, or nothing; glints are light and never outlined."""

    def __init__(self, materials):
        self.materials = materials            # [(part, rgb)]
        self.mat = np.full((N, N), -1, int)
        self.lab = np.zeros((N, N), int)
        self.glint = np.zeros((N, N), bool)
        self.thickness = 2

    # masks

    @staticmethod
    def mask(draw):
        im = Image.new("L", (N, N), 0)
        draw(ImageDraw.Draw(im))
        return np.array(im) > 0

    def put(self, m, mat, shade=None, labels=None, glint=False):
        """Lay {@code m} (a mask) down as material {@code mat}, shaded by {@code shade}(ys, xs, m) or one label."""
        ys, xs = np.nonzero(m)
        if len(ys) == 0:
            return
        if labels is None:
            labels = shade(ys, xs, m) if callable(shade) else np.full(len(ys), MID if shade is None else shade)
        self.mat[ys, xs] = mat
        self.lab[ys, xs] = labels
        self.glint[ys, xs] = glint

    def px(self, x, y, mat, label, glint=False):
        if 0 <= x < N and 0 <= y < N:
            self.mat[y, x] = mat
            self.lab[y, x] = label
            self.glint[y, x] = glint

    def recolour(self, m, mat, label):
        """Within the drawn pixels of {@code m}, set the shade (and material) - a line or a stitch on what is there."""
        sel = m & (self.mat >= 0)
        self.mat[sel] = mat
        self.lab[sel] = label

    # shapes

    def disc(self, cx, cy, r, mat, shade="round"):
        m = self.mask(lambda d: d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=1))
        self.put(m, mat, shading(shade, m))
        return m

    def ellipse(self, cx, cy, rx, ry, mat, shade="round"):
        m = self.mask(lambda d: d.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=1))
        self.put(m, mat, shading(shade, m))
        return m

    def poly(self, pts, mat, shade="bevel"):
        m = self.mask(lambda d: d.polygon([tuple(p) for p in pts], fill=1))
        self.put(m, mat, shading(shade, m))
        return m

    def rect(self, x0, y0, x1, y1, mat, shade="bevel"):
        m = np.zeros((N, N), bool)
        m[max(0, y0):min(N, y1 + 1), max(0, x0):min(N, x1 + 1)] = True
        self.put(m, mat, shading(shade, m))
        return m

    def rod(self, x0, y0, x1, y1, w, mat, bands=None):
        """A round rod from (x0, y0) to (x1, y1), {@code w} pixels across, shaded across its width."""
        m = self.mask(lambda d: d.line((x0, y0, x1, y1), fill=1, width=max(1, int(round(w)))))
        if w <= 1.5:
            m = line_mask(x0, y0, x1, y1)
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln           # across
        if nx * LIGHT_DIR[0] + ny * LIGHT_DIR[1] < 0:
            nx, ny = -nx, -ny                 # towards the light
        bands = bands or [MID, LIGHT, HIGH, LIGHT, MID, DARK]

        def sh(ys, xs, _m):
            t = ((xs - x0) * nx + (ys - y0) * ny)
            lo, hi = t.min(), t.max()
            u = (t - lo) / max(1e-6, hi - lo)
            return np.array([bands[len(bands) - 1 - min(len(bands) - 1, int(v * len(bands)))] for v in u])

        self.put(m, mat, sh)
        return m

    def line(self, x0, y0, x1, y1, mat, label, glint=False):
        m = line_mask(x0, y0, x1, y1)
        self.put(m, mat, label, glint=glint)
        return m


def line_mask(x0, y0, x1, y1):
    m = np.zeros((N, N), bool)
    x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        if 0 <= x0 < N and 0 <= y0 < N:
            m[y0, x0] = True
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy
    return m


def shading(kind, m):
    """How a shape is shaded: 'round' (a dome lit from the top left), 'bevel' (flat, its top-left edge lit and
    its bottom-right edge dark), 'cyl_v'/'cyl_h' (a cylinder standing / lying), a label, or a function."""
    if kind is None or isinstance(kind, (int, np.integer)) or callable(kind):
        return kind
    ys, xs = np.nonzero(m)
    if len(ys) == 0:
        return MID
    if kind == "round":
        cx, cy = (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2
        rx, ry = max(1, (xs.max() - xs.min()) / 2 + 0.5), max(1, (ys.max() - ys.min()) / 2 + 0.5)

        def f(ys, xs, _m):
            u, v = (xs - cx) / rx, (ys - cy) / ry
            d = u * LIGHT_DIR[0] + v * LIGHT_DIR[1]
            rr = u * u + v * v
            out = np.where(d > 0.42, LIGHT, np.where(d > -0.38, MID, DARK))
            out = np.where((d > 0.3) & (rr < 0.55) & (rr > 0.12) & (np.abs(u * 0.78 - v * 0.62) < 0.22), HIGH, out)
            return out
        return f
    if kind == "bevel":
        def f(ys, xs, mm):
            out = np.full(len(ys), MID)
            up = np.array([y == 0 or not mm[y - 1, x] for y, x in zip(ys, xs)])
            left = np.array([x == 0 or not mm[y, x - 1] for y, x in zip(ys, xs)])
            down = np.array([y == N - 1 or not mm[y + 1, x] for y, x in zip(ys, xs)])
            right = np.array([x == N - 1 or not mm[y, x + 1] for y, x in zip(ys, xs)])
            out[down | right] = DARK
            out[up | left] = LIGHT
            out[up & left] = HIGH
            return out
        return f
    if kind in ("cyl_v", "cyl_h"):
        bands = [LIGHT, HIGH, LIGHT, MID, MID, MID, DARK, DARK]

        def f(ys, xs, _m):
            a = xs if kind == "cyl_v" else ys
            lo, hi = a.min(), a.max()
            u = (a - lo) / max(1, hi - lo + 1)
            return np.array([bands[min(len(bands) - 1, int(v * len(bands)))] for v in u])
        return f
    raise ValueError(kind)


# --- turning it -----------------------------------------------------------------------

def frame(art, k, extras=None):
    """Frame {@code k} of the turn: the art squeezed to cos of its angle (its back, mirrored and a shade darker,
    past a quarter turn), its thickness showing beside it, outlined; then any light drawn over it."""
    th = 2 * math.pi * k / FRAMES
    s, sn = math.cos(th), math.sin(th)
    mat = np.full((N, N), -1, int)
    lab = np.zeros((N, N), int)
    glint = np.zeros((N, N), bool)
    c = N / 2
    back = s < 0
    if abs(s) > 0.04:
        for x in range(N):
            sx = c + (x + 0.5 - c) / s - 0.5
            xi = int(math.floor(sx + 0.5))
            if 0 <= xi < N:
                mat[:, x] = art.mat[:, xi]
                lab[:, x] = art.lab[:, xi]
                glint[:, x] = art.glint[:, xi]
    if back:
        darker = {HIGH: LIGHT, LIGHT: MID, MID: DARK, DARK: DARK, INNER: INNER}
        lab = np.vectorize(lambda v: darker.get(int(v), v))(lab)
    # its thickness, on the side turning towards the eye
    e = int(round(abs(sn) * art.thickness))
    if e > 0:
        step = 1 if sn > 0 else -1
        solid = (mat >= 0) & ~glint
        for y in range(N):
            row = solid[y]
            if not row.any():
                src = (art.mat[y] >= 0) & ~art.glint[y]
                if src.any():
                    xs = np.nonzero(src)[0]
                    mm = art.mat[y, xs[len(xs) // 2]]
                    for x in range(int(c - e / 2), int(c - e / 2) + e):
                        if 0 <= x < N:
                            mat[y, x], lab[y, x] = mm, DARK
                continue
            xs = range(N) if step > 0 else range(N - 1, -1, -1)
            prev = False
            ends = []
            for x in xs:
                if prev and not row[x]:
                    ends.append(x - step)
                prev = row[x]
            if prev:
                ends.append(xs[-1])
            for x0 in ends:
                mm = mat[y, x0]
                for i in range(1, e + 1):
                    x = x0 + step * i
                    if 0 <= x < N and mat[y, x] < 0:
                        mat[y, x], lab[y, x] = mm, DARK if i < e or e == 1 else INNER
    # outlined round what is solid
    solid = (mat >= 0) & ~glint
    out = mat.copy()
    olab = lab.copy()
    for y in range(N):
        for x in range(N):
            if mat[y, x] >= 0:
                continue
            for dx, dy in ((0, 1), (1, 0), (-1, 0), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < N and 0 <= yy < N and solid[yy, xx]:
                    out[y, x], olab[y, x] = mat[yy, xx], OUTLINE
                    break
    if extras:
        for (x, y, m, l) in extras(k):
            if 0 <= x < N and 0 <= y < N:
                out[y, x], olab[y, x] = m, l
    return out, olab


def sheet(art, ramps, extras=None):
    """The 32 frames, 8 by 4, as a palette image: 0 clear, 1 unused, then six shades a material."""
    w, h = COLUMNS * N * SCALE, (FRAMES // COLUMNS) * N * SCALE
    idx = np.zeros((h, w), np.uint8)
    for k in range(FRAMES):
        mat, lab = frame(art, k, extras)
        f = np.where(mat >= 0, 2 + mat * 6 + lab, 0).astype(np.uint8)
        f = np.kron(f, np.ones((SCALE, SCALE), np.uint8))
        cx, cy = (k % COLUMNS) * N * SCALE, (k // COLUMNS) * N * SCALE
        idx[cy:cy + N * SCALE, cx:cx + N * SCALE] = f
    im = Image.fromarray(idx, "P")
    pal = [0, 0, 0, 0, 0, 0]
    for r in ramps:
        for c in r:
            pal += list(c)
    im.putpalette(pal + [0] * (768 - len(pal)))
    return im


# --- the pickups ----------------------------------------------------------------------

PICKUPS = {}


def pickup(pid, size, materials, thickness=2):
    """Register a drawing: {@code size} is how wide its frame is in the world, metres."""
    def wrap(fn):
        PICKUPS[pid] = (fn, size, materials, thickness)
        return fn
    return wrap


def sparkle(x, y, r, mat, label=HIGH):
    """A four-pointed glint at (x, y), {@code r} pixels out: [(x, y, mat, label)]."""
    out = [(x, y, mat, label)]
    for i in range(1, r + 1):
        lb = label if i < r else LIGHT
        out += [(x + i, y, mat, lb), (x - i, y, mat, lb), (x, y + i, mat, lb), (x, y - i, mat, lb)]
    return out


def gear(a, mat, cx, cy, teeth, r_body, r_tip, hole, tooth_w=0.42, groove=None):
    m = np.zeros((N, N), bool)
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            rr = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            tooth = (math.cos(teeth * ang) + 1) / 2 > 1 - tooth_w
            if rr <= r_body or (rr <= r_tip and tooth):
                if rr > hole:
                    m[y, x] = True
    a.put(m, mat, shading("round", m))
    # its teeth lit on the light side, dark on the other, with an edge line
    ys, xs = np.nonzero(m)
    for y, x in zip(ys, xs):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        rr = math.hypot(dx, dy)
        d = (dx * LIGHT_DIR[0] + dy * LIGHT_DIR[1]) / max(rr, 1e-6)
        if rr > r_body + 0.3:
            a.lab[y, x] = LIGHT if d > 0.25 else DARK if d < -0.35 else MID
        elif hole < rr <= hole + 1.2:
            a.lab[y, x] = DARK if d > 0.2 else LIGHT if d < -0.3 else MID   # the hole's rim, lit inside
        if groove and abs(rr - groove) < 0.55:
            a.lab[y, x] = INNER
    return m


@pickup("supply_coins", 0.30, [("gold", 0xF0C030)], thickness=2)
def coins(a):
    m = a.disc(15.5, 15.5, 13, 0, "round")
    face = a.mask(lambda d: d.ellipse((5, 5, 26, 26), fill=1))
    a.recolour(m & ~face, 0, LIGHT)
    # the rim: lit top left, in shadow bottom right
    ys, xs = np.nonzero(m & ~face)
    for y, x in zip(ys, xs):
        d = (x + 0.5 - 15.5) * LIGHT_DIR[0] + (y + 0.5 - 15.5) * LIGHT_DIR[1]
        a.lab[y, x] = HIGH if d > 9 else LIGHT if d > -2 else DARK
    ring = a.mask(lambda d: d.ellipse((5, 5, 26, 26), outline=1))
    a.recolour(ring, 0, INNER)
    inner = face & ~ring
    a.recolour(inner, 0, MID)
    # a crown stamped on it: raised, lit on top, its shadow under it
    crown = [(10, 20), (10, 12), (12.5, 15), (15.5, 10), (18.5, 15), (21, 12), (21, 20)]
    cm = a.mask(lambda d: d.polygon(crown, fill=1))
    a.recolour(cm & inner, 0, LIGHT)
    for y in range(N):
        for x in range(N):
            if cm[y, x] and (y + 1 >= N or not cm[y + 1, x] or x + 1 >= N or not cm[y, x + 1]):
                a.lab[y, x] = DARK
            if cm[y, x] and (not cm[y - 1, x] or not cm[y, x - 1]):
                a.lab[y, x] = HIGH
    a.recolour(a.mask(lambda d: d.line((10, 17, 21, 17), fill=1)), 0, DARK)
    for x in (12, 15, 16, 19):
        a.px(x, 13 if x in (15, 16) else 16, 0, HIGH)
    a.px(15, 9, 0, HIGH)
    a.px(16, 9, 0, HIGH)
    a.recolour(a.mask(lambda d: d.rectangle((10, 21, 21, 22), fill=1)), 0, LIGHT)
    a.recolour(a.mask(lambda d: d.line((10, 22, 21, 22), fill=1)), 0, DARK)


def gear_sparkles(k):
    # a shine that runs round as it turns: big, then small, then gone, then back
    p = (k % 16) / 16
    out = []
    if p < 0.5:
        r = 3 if p < 0.25 else 2
        out += sparkle(26, 5, r, 0)
    if 0.25 <= p < 0.75:
        out += sparkle(5, 25, 2 if p < 0.5 else 1, 0)
    if p >= 0.6:
        out += sparkle(27, 23, 1, 0)
    return out


@pickup("supply_ingot", 0.34, [("metal", 0xE8B830)], thickness=3)
def upgrade_gear(a):
    gear(a, 0, 15.5, 15.5, 8, 9.6, 13.4, 3.2, groove=6.6)
    # a shine across its face
    for i in range(-2, 3):
        a.recolour(line_mask(8 + i, 12 - i, 12 + i, 8 - i), 0, HIGH)
    a.recolour(line_mask(10, 14, 14, 10), 0, HIGH)


PICKUPS["supply_ingot"] = PICKUPS["supply_ingot"] + (gear_sparkles,)


@pickup("mat_gear", 0.28, [("metal", 0xD6AA5C)], thickness=3)
def brass_gear(a):
    gear(a, 0, 15.5, 15.5, 10, 9.8, 13.2, 3.6, tooth_w=0.4)
    a.recolour(line_mask(9, 11, 11, 9), 0, HIGH)


def arrow(a, x0, y0, x1, y1, fl, wood, iron, head=4, vane=5, bolt=False):
    dx, dy = x1 - x0, y1 - y0
    ln = math.hypot(dx, dy)
    ux, uy = dx / ln, dy / ln
    nx, ny = -uy, ux
    a.rod(x0, y0, x1 - ux * (head - 1), y1 - uy * (head - 1), 2 if bolt else 1, wood,
          bands=[LIGHT, DARK] if bolt else [MID])
    # the head: a point
    hb = head * (0.75 if bolt else 0.6)
    tip = (x1 + ux * 0.6, y1 + uy * 0.6)
    base = (x1 - ux * head, y1 - uy * head)
    pts = [tip, (base[0] + nx * hb, base[1] + ny * hb), (base[0] - nx * hb, base[1] - ny * hb)]
    a.poly(pts, iron, "bevel")
    # the vanes at the nock
    for side in (1, -1):
        pts = [(x0 + ux * vane, y0 + uy * vane), (x0 + ux * 0.5 + side * nx * 2.6, y0 + uy * 0.5 + side * ny * 2.6),
               (x0 - ux * 0.8 + side * nx * 2.6, y0 - uy * 0.8 + side * ny * 2.6), (x0, y0)]
        a.poly(pts, fl, "bevel")


@pickup("supply_arrows", 0.52, [("fletching", 0xD8283C), ("wood", 0xB98043), ("iron", 0xB8C0C5)], thickness=1)
def arrows(a):
    arrow(a, 5, 22, 22, 3, 0, 1, 2)
    arrow(a, 9, 26, 26, 7, 0, 1, 2)
    arrow(a, 5, 27, 27, 3, 0, 1, 2, head=5)
    # tied in the middle
    for t in (0, 1):
        a.line(13 + t, 21 + t, 18 + t, 16 + t, 1, HIGH if t == 0 else DARK)


@pickup("supply_bolts", 0.44, [("fletching", 0x2F6FD8), ("wood", 0xB98043), ("iron", 0xB8C0C5)], thickness=2)
def bolts(a):
    arrow(a, 6, 22, 21, 7, 0, 1, 2, head=5, vane=4, bolt=True)
    arrow(a, 10, 26, 25, 11, 0, 1, 2, head=5, vane=4, bolt=True)
    for t in (0, 1, 2):
        a.line(12 + t, 21 + t, 17 + t, 16 + t, 1, HIGH if t == 1 else DARK)


def crystal(a, mat, cx, base_y, w, h, lean=0.0, tip=None):
    """A six-sided crystal: lit left face, a ridge, a dark right face, its point."""
    tip = tip or w * 0.9
    top = base_y - h
    sx = lean * h
    L = [(cx - w / 2, base_y), (cx - w / 2 + sx, top), (cx + sx, top - tip), (cx + sx, base_y)]
    R = [(cx + sx, base_y), (cx + sx, top - tip), (cx + w / 2 + sx, top), (cx + w / 2, base_y)]
    ml = a.poly(L, mat, LIGHT)
    mr = a.poly(R, mat, DARK)
    a.line(cx + sx, base_y, cx + sx, top - tip + 1, mat, MID)
    a.line(cx - w / 2 + sx + 1, top + 1, cx - w / 2 + 1, base_y - 1, mat, HIGH)
    a.px(int(round(cx + sx)), int(round(top - tip + 1)), mat, HIGH)
    return ml | mr


@pickup("supply_crystal", 0.34, [("crystal", 0x6FB8FF), ("rock", 0x918780)], thickness=2)
def crystals(a):
    a.ellipse(15.5, 25, 12, 4.5, 1, "round")
    crystal(a, 0, 9, 25, 6, 9, lean=-0.35)
    crystal(a, 0, 22, 25, 6, 10, lean=0.3)
    crystal(a, 0, 15.5, 25, 8, 16)


@pickup("mat_shard", 0.30, [("crystal", 0x8AD8F0)], thickness=2)
def shard(a):
    crystal(a, 0, 14, 28, 10, 16, lean=0.12, tip=7)
    crystal(a, 0, 23, 28, 5, 6, lean=0.4, tip=4)
    # where it broke from the cluster: a rough, dark foot
    a.poly([(9, 27), (19, 27), (18, 29), (10, 29)], 0, DARK)


@pickup("supply_studs", 0.32, [("metal", 0xB08A4A), ("leather", 0x7C3C24)], thickness=2)
def studs(a):
    a.poly([(2, 14), (29, 9), (30, 18), (3, 23)], 1, "bevel")
    for i in range(6):
        a.px(4 + i * 5, 14 - i, 1, INNER)
        a.px(5 + i * 5, 21 - i, 1, INNER)
    for (x, y) in ((8, 16.5), (16, 15), (24, 13.5)):
        a.disc(x, y, 3.4, 0, "round")


@pickup("supply_wedge", 0.30, [("iron", 0x6E7480)], thickness=3)
def wedge(a):
    a.poly([(6, 6), (26, 6), (17, 28), (15, 28)], 0, MID)
    a.poly([(6, 6), (14, 6), (15.5, 28), (15, 28)], 0, LIGHT)
    a.poly([(20, 6), (26, 6), (17, 28), (16.5, 28)], 0, DARK)
    a.poly([(6, 3), (26, 3), (26, 6), (6, 6)], 0, HIGH)
    a.line(6, 6, 26, 6, 0, INNER)
    for x in (9, 13, 21):
        a.px(x, 4, 0, LIGHT)


@pickup("supply_whetstone", 0.30, [("stone", 0x8C9098), ("leather", 0x9D5E34)], thickness=3)
def whetstone(a):
    m = a.mask(lambda d: d.polygon([(5, 22), (9, 26), (27, 12), (23, 7), (11, 13)], fill=1))
    m |= a.mask(lambda d: d.ellipse((3, 15, 13, 27), fill=1)) | a.mask(lambda d: d.ellipse((19, 5, 29, 16), fill=1))
    a.put(m, 0, shading("round", m))
    for (x, y) in ((9, 20), (12, 17), (19, 13), (22, 11), (7, 23), (25, 12)):
        a.px(x, y, 0, DARK)
    for (x, y) in ((10, 17), (20, 10), (24, 9)):
        a.px(x, y, 0, HIGH)
    # a leather band wrapped round its middle, across it
    for (cx, cy) in ((14, 18), (17, 15)):
        band = a.mask(lambda d, cx=cx, cy=cy: d.line((cx - 5, cy - 6, cx + 5, cy + 6), fill=1, width=2))
        a.recolour(band & m, 1, MID)
        a.recolour(line_mask(cx - 5, cy - 6, cx + 5, cy + 6) & m, 1, LIGHT)


@pickup("supply_scroll", 0.40, [("ribbon", 0x9B59D0), ("paper", 0xE0BE8F)], thickness=3)
def scroll(a):
    a.rod(7, 25, 24, 8, 9, 1, bands=[HIGH, LIGHT, MID, MID, MID, DARK])
    # its ends: the roll seen end on
    for (cx, cy) in ((6, 26), (25, 7)):
        a.ellipse(cx, cy, 3.6, 3.6, 1, LIGHT)
        a.recolour(a.mask(lambda d: d.ellipse((cx - 2, cy - 2, cx + 2, cy + 2), outline=1)), 1, DARK)
        a.px(cx, cy, 1, INNER)
    # the ribbon round its middle, tied in a bow, its ends hanging
    a.rod(12, 13, 19, 20, 3, 0, bands=[LIGHT, MID, DARK])
    a.poly([(15, 18), (12, 25), (14, 26), (16.5, 19)], 0, "bevel")
    a.poly([(17, 18), (20, 24), (22, 23), (18, 17)], 0, "bevel")
    a.ellipse(13.5, 15.5, 2, 1.4, 0, "round")
    a.ellipse(18.5, 18.5, 1.4, 2, 0, "round")
    a.disc(16, 17, 1.5, 0, LIGHT)


@pickup("mat_ore", 0.30, [("ore", 0x9A5232)], thickness=3)
def ore(a):
    pts = [(5, 20), (8, 11), (14, 7), (21, 8), (27, 13), (28, 21), (23, 26), (12, 27), (6, 25)]
    m = a.poly(pts, 0, "round")
    # facets: a lit face top left, a dark one bottom right
    a.recolour(a.mask(lambda d: d.polygon([(8, 11), (14, 7), (17, 12), (11, 16)], fill=1)) & m, 0, LIGHT)
    a.recolour(a.mask(lambda d: d.polygon([(20, 18), (28, 21), (23, 26), (17, 24)], fill=1)) & m, 0, DARK)
    for seg in ((11, 16, 17, 12), (17, 12, 21, 8), (11, 16, 12, 22), (17, 24, 20, 18), (20, 18, 27, 13)):
        a.recolour(line_mask(*seg) & m, 0, INNER)
    for (x, y) in ((10, 11), (13, 9), (22, 12), (8, 20), (15, 20), (25, 16)):
        a.px(x, y, 0, HIGH)


@pickup("mat_logs", 0.36, [("wood", 0x7A5030), ("grain", 0xD6A86A)], thickness=4)
def logs(a):
    for (cx, cy) in ((9.5, 21.5), (22.5, 21.5), (16, 11)):
        # the bark running back, then the cut end
        a.disc(cx + 2.2, cy + 1.2, 6.2, 0, "round")
        a.disc(cx, cy, 5.6, 1, MID)
        a.recolour(a.mask(lambda d: d.ellipse((cx - 5.6, cy - 5.6, cx + 5.6, cy + 5.6), outline=1)), 0, LIGHT)
        a.recolour(a.mask(lambda d: d.ellipse((cx - 3.2, cy - 3.2, cx + 3.2, cy + 3.2), outline=1)), 1, DARK)
        a.px(int(cx), int(cy), 1, INNER)
        a.px(int(cx) - 2, int(cy) - 3, 1, HIGH)
        a.px(int(cx) - 3, int(cy) - 2, 1, HIGH)
        a.recolour(line_mask(cx, cy, cx + 3, cy - 4), 1, DARK)


@pickup("mat_heap", 0.32, [("heap", 0xD8C08A)], thickness=2)
def heap(a):
    m = a.mask(lambda d: d.chord((2, 8, 29, 40), 180, 360, fill=1))
    m[27:, :] = False
    m |= a.mask(lambda d: d.ellipse((1, 23, 30, 28), fill=1))     # spilt at its foot
    a.put(m, 0, shading("round", m))
    rng = np.random.RandomState(7)
    ys, xs = np.nonzero(m)
    for i in rng.choice(len(ys), 26, replace=False):
        a.px(xs[i], ys[i], 0, DARK if rng.rand() < 0.55 else LIGHT)
    for (x, y) in ((11, 12), (12, 11), (14, 11), (13, 12)):
        a.px(x, y, 0, HIGH)
    a.disc(20, 18, 1.3, 0, DARK)      # a pebble or two in it
    a.px(19, 17, 0, LIGHT)
    a.disc(9, 21, 1.0, 0, DARK)


@pickup("mat_sheaf", 0.36, [("stalks", 0xB8B060), ("twine", 0xB98043)], thickness=2)
def sheaf(a):
    tops = [(5, 7), (8, 4), (12, 3), (16, 2), (20, 3), (24, 4), (27, 7)]
    for i, (tx, ty) in enumerate(tops):
        bx = 12 + i * 1.3
        a.line(bx, 29, 14 + i * 0.7, 18, 0, MID if i % 2 else LIGHT)
        a.line(14 + i * 0.7, 18, tx, ty + 3, 0, MID if i % 2 else DARK)
        a.ellipse(tx, ty + 1.5, 1.4, 2.2, 0, "round")
        a.px(tx, ty + 1, 0, HIGH)
    for t in (0, 1):
        a.rod(11, 18 + t * 2, 21, 18 + t * 2, 1, 1, bands=[LIGHT if t == 0 else DARK])
    a.line(16, 21, 13, 25, 1, MID)


@pickup("mat_resin", 0.28, [("resin", 0xD08A2A)], thickness=2)
def resin(a):
    m = a.mask(lambda d: d.ellipse((6, 11, 26, 29), fill=1)) | \
        a.mask(lambda d: d.polygon([(10, 14), (16, 2), (22, 14)], fill=1))
    a.put(m, 0, shading("round", m))
    # deep amber low down and to the right, light caught on the left
    a.recolour(a.mask(lambda d: d.chord((9, 14, 25, 28), 300, 140, fill=1)) & m, 0, DARK)
    for (x, y) in ((11, 14), (10, 15), (10, 16), (12, 13), (15, 6), (9, 18)):
        a.px(x, y, 0, HIGH)
    # a bubble caught in it
    a.px(14, 20, 0, LIGHT)
    a.px(15, 19, 0, HIGH)
    a.px(19, 23, 0, LIGHT)


@pickup("mat_bar", 0.30, [("metal", 0x6E7480)], thickness=4)
def bar(a):
    a.poly([(10, 9), (22, 9), (26, 15), (6, 15)], 0, LIGHT)        # its top
    a.poly([(6, 15), (26, 15), (29, 24), (3, 24)], 0, MID)         # its front
    a.poly([(6, 15), (8, 15), (5, 24), (3, 24)], 0, LIGHT)         # the lit slope of its front
    a.poly([(24, 15), (26, 15), (29, 24), (27, 24)], 0, DARK)      # the dark one
    a.line(10, 9, 22, 9, 0, HIGH)
    a.line(6, 15, 26, 15, 0, HIGH)
    a.line(3, 24, 29, 24, 0, DARK)
    for i in range(3):
        a.px(11 + i, 10, 0, HIGH)
    a.px(10, 11, 0, HIGH)
    a.line(11, 19, 21, 19, 0, DARK)   # its stamp
    a.line(11, 20, 21, 20, 0, LIGHT)


@pickup("mat_glass", 0.30, [("glass", 0xB8E0E8)], thickness=2)
def glass(a):
    a.poly([(8, 4), (26, 7), (24, 28), (6, 25)], 0, "bevel")
    for o in (0, 6):
        a.recolour(line_mask(10 + o, 15, 16 + o, 8), 0, HIGH)
        a.recolour(line_mask(11 + o, 15, 17 + o, 8), 0, LIGHT)
    a.recolour(line_mask(12, 24, 22, 13), 0, LIGHT)


@pickup("mat_planks", 0.36, [("wood", 0xB08050)], thickness=2)
def planks(a):
    for i, y in enumerate((20, 14, 8)):
        x0 = 3 + (i % 2) * 2
        a.poly([(x0, y + 4), (x0 + 3, y), (x0 + 25, y), (x0 + 22, y + 4)], 0, LIGHT)   # its top
        a.rect(x0, y + 4, x0 + 22, y + 6, 0, MID)                                      # its edge
        a.poly([(x0 + 22, y + 4), (x0 + 25, y), (x0 + 25, y + 2), (x0 + 22, y + 6)], 0, DARK)
        a.line(x0 + 5, y + 2, x0 + 13, y + 2, 0, DARK)
        a.line(x0 + 15, y + 1, x0 + 20, y + 1, 0, DARK)
        a.line(x0, y + 6, x0 + 22, y + 6, 0, INNER)


@pickup("mat_crucible", 0.30, [("clay", 0xB06A44)], thickness=3)
def crucible(a):
    a.poly([(5, 9), (26, 9), (23, 26), (8, 26)], 0, "cyl_v")
    a.ellipse(15.5, 9, 10.5, 3.2, 0, LIGHT)
    a.ellipse(15.5, 9, 8, 2, 0, INNER)
    a.ellipse(15.5, 26, 7.5, 2, 0, DARK)
    a.poly([(24, 7), (29, 5), (27, 9)], 0, LIGHT)   # its lip
    for (x, y) in ((10, 15), (19, 19), (13, 22)):
        a.px(x, y, 0, DARK)


@pickup("mat_lens", 0.26, [("glass", 0xB8E0E8), ("rim", 0xD6AA5C)], thickness=2)
def lens(a):
    a.disc(15.5, 15.5, 12.5, 1, "round")
    a.disc(15.5, 15.5, 9.5, 0, "round")
    for i in range(4):
        a.px(10 + i, 9 - (1 if i in (1, 2) else 0), 0, HIGH)
    a.px(9, 10, 0, HIGH)
    a.px(9, 11, 0, HIGH)
    a.recolour(a.mask(lambda d: d.ellipse((6, 6, 25, 25), outline=1)), 1, INNER)


@pickup("kit_bandage", 0.28, [("cloth", 0xE8E0D0), ("herb", 0x5E9A3A)], thickness=4)
def bandage(a):
    # the loose end, unrolled and trailing
    a.poly([(20, 20), (25, 20), (17, 27), (5, 28), (6, 25), (15, 25)], 0, "bevel")
    a.line(8, 26, 15, 26, 0, DARK)
    # the roll, lying on its side: its body, then its end with the layers wound round
    a.rect(10, 7, 25, 21, 0, "cyl_h")
    for x in (14, 18, 22):
        a.line(x, 8, x, 20, 0, MID)
    a.ellipse(10, 14, 4.2, 7.4, 0, LIGHT)
    a.recolour(a.mask(lambda d: d.ellipse((7, 9, 13, 19), outline=1)), 0, DARK)
    a.recolour(a.mask(lambda d: d.ellipse((9, 12, 11, 16), outline=1)), 0, DARK)
    a.px(10, 14, 0, INNER)
    # a sprig of sage tucked in
    a.line(22, 7, 27, 2, 1, DARK)
    a.ellipse(25, 3, 2, 1.2, 1, "round")
    a.ellipse(28, 5, 1.6, 1.2, 1, "round")
    a.ellipse(21.5, 4.5, 1.4, 1.1, 1, "round")


@pickup("kit_pellet", 0.26, [("shell", 0x8A8A92), ("fuse", 0x80562F), ("smoke", 0xC2C4CD)], thickness=2)
def pellet(a):
    a.disc(15, 20, 9, 0, "round")
    a.line(8, 20, 22, 20, 0, INNER)           # the seam
    a.line(16, 11, 18, 6, 1, MID)
    a.line(17, 11, 19, 7, 1, DARK)
    a.px(19, 5, 1, HIGH, glint=True)
    for (cx, cy, r) in ((21, 4, 1.6), (24.5, 3, 2.1), (27.5, 5.5, 1.5)):
        a.disc(cx, cy, r, 2, "round")


@pickup("craft_spyglass", 0.48, [("metal", 0xD6AA5C), ("leather", 0x7C3C24), ("glass", 0x9AD0E8)], thickness=2)
def spyglass(a):
    a.rod(4, 28, 11, 21, 5, 0)
    a.rod(10, 22, 18, 14, 7, 1)
    a.rod(17, 15, 26, 6, 9, 0)
    for (x, y, r) in ((11, 21, 3), (17.5, 14.5, 4), (25.5, 6.5, 4.6)):
        a.recolour(a.mask(lambda d, x=x, y=y, r=r: d.line((x - r * 0.7, y - r * 0.7, x + r * 0.7, y + r * 0.7),
                                                          fill=1, width=2)), 0, HIGH)
    a.ellipse(27, 5, 3.3, 3.3, 2, "round")
    a.px(26, 4, 2, HIGH)


def lantern_glow(k):
    return [(15, 17, 2, HIGH), (16, 16, 2, HIGH)] if (k // 3) % 2 else []


@pickup("craft_lantern", 0.40, [("metal", 0xD6AA5C), ("glass", 0xE8E0B0), ("flame", 0xFAB33E)], thickness=3)
def lantern(a):
    a.recolour(np.zeros((N, N), bool), 0, MID)
    ring = a.mask(lambda d: d.ellipse((11, 0, 20, 7), outline=1, width=2))
    a.put(ring, 0, LIGHT)
    a.poly([(9, 9), (22, 9), (19, 5), (12, 5)], 0, "bevel")      # its cap
    a.rect(8, 9, 23, 10, 0, DARK)
    a.rect(9, 11, 22, 25, 1, "bevel")                               # its glass
    a.ellipse(15.5, 19, 3, 4.5, 2, MID)                             # the flame
    a.ellipse(15.5, 20, 1.6, 2.6, 2, LIGHT)
    a.px(15, 14, 2, LIGHT)
    for x in (9, 15, 16, 22):
        a.line(x, 11, x, 25, 0, MID if x in (9, 15) else DARK)
    a.rect(7, 26, 24, 28, 0, "bevel")                               # its foot


PICKUPS["craft_lantern"] = PICKUPS["craft_lantern"] + (lantern_glow,)


# --- writing them ---------------------------------------------------------------------

def build(pid, pal):
    entry = PICKUPS[pid]
    fn, size, materials, thickness = entry[:4]
    extras = entry[4] if len(entry) > 4 else None
    a = Art(materials)
    a.thickness = thickness
    fn(a)
    ramps = [pal.ramp(rgb) for (_, rgb) in materials]
    return a, ramps, extras, size


def write(pid, pal, root=ITEMS):
    a, ramps, extras, size = build(pid, pal)
    out = os.path.join(root, pid, "spin_px128")
    os.makedirs(out, exist_ok=True)
    im = sheet(a, ramps, extras)
    trns = bytes([0, 1] + [255] * (len(ramps) * 6))
    for view in ("side", "middle", "top"):
        im.save(os.path.join(out, "spin_%s.png" % view), transparency=trns, optimize=True)
    parts = {}
    for i, (part, _) in enumerate(a.materials):
        parts.setdefault(part, []).append(i)
    with open(os.path.join(out, "parts.json"), "w") as f:
        json.dump({"labelsPerMaterial": 6, "parts": parts, "materials": [p for p, _ in a.materials]}, f, indent=1)
    with open(os.path.join(out, "profile.json"), "w") as f:
        json.dump({"frameWidth": N * SCALE, "frameHeight": N * SCALE, "frameWorldSize": size, "pivotHeight": 0.0,
                   "fps": 10.0, "pixelArt": True, "frames": FRAMES, "length": round(size * 0.85, 4),
                   "handPixelled": True}, f, indent=1)
    with open(os.path.join(out, "variants.json"), "w") as f:
        json.dump({"version": 1, "labels": 6 * len(a.materials), "default": {}, "channels": {}}, f)
    return out


def preview(path, pal, ids):
    """Each pickup's front at rest and six frames of its turn, on a dark ground, twice size."""
    cols = 7
    cell = N * SCALE
    img = Image.new("RGBA", (cols * cell, len(ids) * cell), (52, 56, 64, 255))
    for r, pid in enumerate(ids):
        a, ramps, extras, _ = build(pid, pal)
        im = sheet(a, ramps, extras).convert("RGBA")
        arr = np.array(im)
        idx = np.array(sheet(a, ramps, extras))
        arr[idx == 0, 3] = 0
        im = Image.fromarray(arr)
        for c, k in enumerate((0, 2, 4, 6, 8, 12, 16)):
            fx, fy = (k % COLUMNS) * cell, (k // COLUMNS) * cell
            img.alpha_composite(im.crop((fx, fy, fx + cell, fy + cell)), (c * cell, r * cell))
    img.save(path)


def main(argv):
    pal = Palette()
    if argv and argv[0] == "preview":
        ids = argv[2:] or list(PICKUPS)
        preview(argv[1], pal, ids)
        print("preview:", argv[1])
        return
    for pid in (argv or list(PICKUPS)):
        print(write(pid, pal))


if __name__ == "__main__":
    main(sys.argv[1:])
