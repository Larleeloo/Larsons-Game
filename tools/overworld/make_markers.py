"""
Larson's Game's overworld map marks: the plain silhouettes a destination
can be drawn as instead of its painted icon - a traveller's map's marks,
pale shapes with a dark rim. Each comes filled (she can get there) and
hollow (not yet).

    python3 tools/overworld/make_markers.py [assets/overworld]

Writes assets/overworld/markers.png: every mark 48 x 48 in a row, in the
order of Overworld.MARKERS, the filled ones on the top row and the hollow
ones under them; and lists them in tiles.json under "markers".

Every shape is drawn here from plain geometry four times over size and
shrunk, so its edges are smooth; the same picture comes out every time.
"""

import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
M = 48            # a mark's side in the picture
SS = 4            # drawn this many times larger, then shrunk
N = M * SS

FILL = (242, 235, 217)     # parchment white
RIM = (26, 23, 20)         # ink

MARKERS = ["city", "hamlet", "fort", "keep", "watchtower", "lighthouse", "farm", "mill", "lumber_camp", "docks",
           "bridge", "inn", "temple", "standing_stone", "grove", "cavern", "mine_shaft", "barrow", "ruined_tower",
           "lair", "camp_tents", "gateway", "workshop_gears", "landmark"]


def u(v):
    """A length in 48ths of the mark, in the large canvas's pixels."""
    return v * SS


def P(*pts):
    return [(u(x), u(y)) for x, y in pts]


def crenels(d, x0, x1, y, h, n, val=255):
    """A battlement along y from x0 to x1: n teeth h tall."""
    w = (x1 - x0) / (2 * n - 1)
    for i in range(n):
        d.rectangle([u(x0 + 2 * i * w), u(y - h), u(x0 + (2 * i + 1) * w), u(y)], fill=val)


def gear(d, cx, cy, r_out, r_in, teeth, hole):
    pts = []
    for i in range(teeth * 4):
        a = i / (teeth * 4) * 2 * math.pi
        r = r_out if (i % 4) in (1, 2) else r_in
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d.polygon(P(*pts), fill=255)
    d.ellipse([u(cx - hole), u(cy - hole), u(cx + hole), u(cy + hole)], fill=0)


def shape(name):
    """The mark's silhouette, a mask (255 inside)."""
    im = Image.new("L", (N, N), 0)
    d = ImageDraw.Draw(im)
    if name == "city":
        d.rectangle([u(6), u(26), u(42), u(42)], fill=255)                 # the wall
        crenels(d, 6, 42, 26, 3, 7)
        for cx, top, w in ((12, 12, 7), (24, 6, 9), (36, 12, 7)):           # three towers
            d.rectangle([u(cx - w / 2), u(top + 6), u(cx + w / 2), u(42)], fill=255)
            d.polygon(P((cx - w / 2 - 1.5, top + 7), (cx, top - 1), (cx + w / 2 + 1.5, top + 7)), fill=255)
        d.chord([u(20), u(32), u(28), u(44)], 180, 360, fill=0)             # the gate
        d.rectangle([u(20), u(38), u(28), u(42)], fill=0)
    elif name == "hamlet":
        for x0, y0, w, h in ((5, 22, 18, 18), (23, 16, 20, 24)):
            d.rectangle([u(x0 + 2), u(y0 + 8), u(x0 + w - 2), u(y0 + h)], fill=255)
            d.polygon(P((x0, y0 + 9), (x0 + w / 2, y0), (x0 + w, y0 + 9)), fill=255)
            d.rectangle([u(x0 + w / 2 - 2), u(y0 + h - 7), u(x0 + w / 2 + 2), u(y0 + h)], fill=0)
        d.rectangle([u(35), u(14), u(38), u(20)], fill=255)                 # a chimney
        d.rectangle([u(4), u(40), u(44), u(42)], fill=255)
    elif name == "fort":
        d.rectangle([u(7), u(18), u(41), u(42)], fill=255)
        crenels(d, 7, 41, 18, 4, 6)
        for x in (5, 37):
            d.rectangle([u(x), u(12), u(x + 6), u(42)], fill=255)
            crenels(d, x, x + 6, 12, 3, 2)
        d.chord([u(19), u(28), u(29), u(46)], 180, 360, fill=0)
        d.rectangle([u(19), u(37), u(29), u(42)], fill=0)
    elif name == "keep":
        d.rectangle([u(15), u(16), u(33), u(43)], fill=255)
        crenels(d, 13, 35, 16, 4, 4)
        d.rectangle([u(13), u(13), u(35), u(16)], fill=255)
        d.line([u(24), u(13), u(24), u(3)], fill=255, width=int(u(1.6)))        # the flag
        d.polygon(P((24.5, 3), (33, 5.5), (24.5, 8)), fill=255)
        d.rectangle([u(22), u(22), u(26), u(28)], fill=0)                   # a window
        d.chord([u(20), u(34), u(28), u(46)], 180, 360, fill=0)
        d.rectangle([u(20), u(40), u(28), u(43)], fill=0)
    elif name == "watchtower":
        d.polygon(P((12, 44), (18, 18), (21, 18), (16, 44)), fill=255)       # legs
        d.polygon(P((36, 44), (30, 18), (27, 18), (32, 44)), fill=255)
        d.line([u(15), u(34), u(33), u(26)], fill=255, width=int(u(2)))
        d.line([u(15), u(26), u(33), u(34)], fill=255, width=int(u(2)))
        d.rectangle([u(14), u(12), u(34), u(19)], fill=255)                 # the lookout
        d.polygon(P((11, 13), (24, 3), (37, 13)), fill=255)
        d.rectangle([u(18), u(14), u(30), u(17)], fill=0)
    elif name == "lighthouse":
        d.polygon(P((17, 44), (20, 16), (28, 16), (31, 44)), fill=255)
        d.rectangle([u(18), u(12), u(30), u(16)], fill=255)
        d.rectangle([u(19.5), u(7), u(28.5), u(12)], fill=255)
        d.polygon(P((18, 7.5), (24, 2), (30, 7.5)), fill=255)
        d.rectangle([u(21.5), u(8.5), u(26.5), u(11)], fill=0)
        for s in (-1, 1):                                                   # its light
            d.polygon(P((24 + s * 6, 8), (24 + s * 20, 3), (24 + s * 20, 7), (24 + s * 6, 10)), fill=255)
        for y in (23, 32):
            d.polygon(P((19, y), (29, y), (29.4, y + 3), (18.6, y + 3)), fill=0)
        d.rectangle([u(8), u(42), u(40), u(45)], fill=255)
    elif name == "farm":
        d.rectangle([u(9), u(22), u(39), u(42)], fill=255)                  # a barn
        d.polygon(P((5, 24), (12, 12), (36, 12), (43, 24)), fill=255)
        d.rectangle([u(19), u(28), u(29), u(42)], fill=0)
        d.line([u(19), u(28), u(29), u(42)], fill=255, width=int(u(1.6)))
        d.line([u(29), u(28), u(19), u(42)], fill=255, width=int(u(1.6)))
        d.rectangle([u(21), u(15), u(27), u(19)], fill=0)
    elif name == "mill":
        d.polygon(P((17, 44), (20, 22), (28, 22), (31, 44)), fill=255)
        d.polygon(P((18, 23), (24, 15), (30, 23)), fill=255)
        cx, cy = 24, 19
        for a in (45, 135, 225, 315):                                       # four sails
            r = math.radians(a)
            dx, dy = math.cos(r), math.sin(r)
            px, py = -dy, dx
            pts = [(cx + dx * 4 + px * 1.2, cy + dy * 4 + py * 1.2), (cx + dx * 18 + px * 1.2, cy + dy * 18 + py * 1.2),
                   (cx + dx * 18 + px * 6, cy + dy * 18 + py * 6), (cx + dx * 6 + px * 6, cy + dy * 6 + py * 6)]
            d.polygon(P(*pts), fill=255)
            d.line([u(cx), u(cy), u(cx + dx * 18), u(cy + dy * 18)], fill=255, width=int(u(1.8)))
        d.ellipse([u(cx - 2.5), u(cy - 2.5), u(cx + 2.5), u(cy + 2.5)], fill=255)
        d.rectangle([u(22), u(36), u(26), u(44)], fill=0)
    elif name == "lumber_camp":
        for cx, cy in ((15, 37), (33, 37), (24, 29)):                       # a stack of logs
            d.ellipse([u(cx - 8), u(cy - 7), u(cx + 8), u(cy + 7)], fill=255)
            d.ellipse([u(cx - 3), u(cy - 2.5), u(cx + 3), u(cy + 2.5)], fill=0)
            d.ellipse([u(cx - 1), u(cy - 0.8), u(cx + 1), u(cy + 0.8)], fill=255)
        d.line([u(10), u(22), u(31), u(5)], fill=255, width=int(u(2.4)))         # an axe over them
        d.polygon(P((26, 4), (34, 1), (38, 9), (31, 11)), fill=255)
    elif name == "docks":
        d.ellipse([u(20), u(3), u(28), u(11)], fill=255)                    # an anchor
        d.ellipse([u(22.2), u(5.2), u(25.8), u(8.8)], fill=0)
        d.rectangle([u(22.3), u(10), u(25.7), u(40)], fill=255)
        d.rectangle([u(15), u(14), u(33), u(17.5)], fill=255)
        d.arc([u(8), u(16), u(40), u(43)], 20, 160, fill=255, width=int(u(3.6)))
        for s in (-1, 1):
            d.polygon(P((24 + s * 16.5, 26), (24 + s * 18.5, 34), (24 + s * 12, 31)), fill=255)
    elif name == "bridge":
        d.rectangle([u(3), u(17), u(45), u(22)], fill=255)                  # the deck
        d.rectangle([u(3), u(17), u(45), u(36)], fill=255)
        d.chord([u(11), u(24), u(37), u(52)], 180, 360, fill=0)             # its arch
        d.rectangle([u(11), u(38), u(37), u(44)], fill=0)
        d.chord([u(-4), u(28), u(9), u(46)], 180, 360, fill=0)
        d.chord([u(39), u(28), u(52), u(46)], 180, 360, fill=0)
        for x in range(4, 46, 5):
            d.rectangle([u(x), u(12), u(x + 2), u(17)], fill=255)
        d.rectangle([u(3), u(11), u(45), u(13)], fill=255)
        d.rectangle([u(0), u(36), u(48), u(44)], fill=0)
    elif name == "inn":
        d.rounded_rectangle([u(10), u(12), u(32), u(43)], radius=int(u(3)), fill=255)   # a tankard
        d.arc([u(26), u(17), u(42), u(36)], 270, 90, fill=255, width=int(u(3.4)))
        for x in (13, 21):
            d.ellipse([u(x - 3), u(5), u(x + 7), u(15)], fill=255)
        d.ellipse([u(24), u(7), u(33), u(15)], fill=255)
        for x in (16, 21, 26):
            d.rectangle([u(x - 0.8), u(19), u(x + 0.8), u(38)], fill=0)
    elif name == "temple":
        d.polygon(P((4, 16), (24, 4), (44, 16)), fill=255)                  # the roof
        d.rectangle([u(6), u(16), u(42), u(19)], fill=255)
        d.polygon(P((14, 14), (24, 8.5), (34, 14)), fill=0)
        for x in (8, 17, 26, 35):
            d.rectangle([u(x), u(20), u(x + 5), u(38)], fill=255)
        d.rectangle([u(5), u(38), u(43), u(41)], fill=255)
        d.rectangle([u(3), u(41), u(45), u(44)], fill=255)
    elif name == "standing_stone":
        d.polygon(P((19, 44), (18, 12), (22, 4), (27, 5), (30, 14), (29, 44)), fill=255)
        d.polygon(P((5, 44), (6, 25), (10, 20), (14, 22), (15, 44)), fill=255)
        d.polygon(P((33, 44), (34, 23), (38, 18), (42, 21), (43, 44)), fill=255)
        d.line([u(22), u(14), u(25), u(22), u(22), u(30)], fill=0, width=int(u(1.2)))   # a rune
        d.rectangle([u(2), u(43), u(46), u(45)], fill=255)
    elif name == "grove":
        for cx, cy, r in ((14, 22, 9), (34, 22, 9), (24, 14, 11)):
            d.rectangle([u(cx - 1.6), u(cy), u(cx + 1.6), u(43)], fill=255)
            d.ellipse([u(cx - r), u(cy - r), u(cx + r), u(cy + r)], fill=255)
        d.rectangle([u(4), u(42), u(44), u(44)], fill=255)
    elif name == "cavern":
        d.polygon(P((2, 44), (8, 28), (15, 20), (21, 11), (28, 9), (35, 17), (41, 26), (46, 44)), fill=255)
        d.chord([u(13), u(24), u(35), u(54)], 180, 360, fill=0)
        d.rectangle([u(13), u(39), u(35), u(44)], fill=0)
        d.polygon(P((19, 28), (21, 33), (23, 28)), fill=255)                # its teeth
        d.polygon(P((26, 27), (28, 31), (30, 28)), fill=255)
    elif name == "mine_shaft":
        d.polygon(P((6, 44), (16, 14), (32, 14), (42, 44)), fill=255)        # a timbered mouth
        d.polygon(P((13, 44), (19, 22), (29, 22), (35, 44)), fill=0)
        d.rectangle([u(12), u(10), u(36), u(15)], fill=255)
        d.line([u(24), u(3), u(24), u(11)], fill=255, width=int(u(2.2)))   # a pick over it
        d.arc([u(10), u(1), u(38), u(17)], 200, 340, fill=255, width=int(u(2.8)))
    elif name == "barrow":
        d.chord([u(2), u(16), u(46), u(64)], 180, 360, fill=255)             # the mound
        d.rectangle([u(2), u(40), u(46), u(44)], fill=255)
        d.polygon(P((17, 44), (17, 31), (24, 25), (31, 31), (31, 44)), fill=0)   # its door
        d.polygon(P((20, 44), (20, 32), (24, 28.5), (28, 32), (28, 44)), fill=255)
        d.rectangle([u(23.2), u(31), u(24.8), u(44)], fill=0)
        d.polygon(P((22, 18), (23, 7), (26, 8), (27, 18)), fill=255)        # a stone on top
    elif name == "ruined_tower":
        d.polygon(P((13, 44), (15, 16), (19, 12), (21, 16), (24, 9), (27, 14), (30, 11), (33, 18), (35, 44)),
                  fill=255)
        d.polygon(P((26, 44), (28, 28), (31, 25), (33, 44)), fill=0)         # a crack
        d.rectangle([u(19), u(22), u(23), u(28)], fill=0)
        for x, y in ((8, 41), (39, 40), (42, 43)):                           # fallen stones
            d.rectangle([u(x), u(y), u(x + 4), u(y + 3)], fill=255)
    elif name == "lair":
        d.ellipse([u(11), u(10), u(37), u(34)], fill=255)                    # a horned skull
        d.rounded_rectangle([u(16), u(28), u(32), u(40)], radius=int(u(2)), fill=255)
        for s in (-1, 1):
            d.polygon(P((24 + s * 10, 15), (24 + s * 21, 3), (24 + s * 15, 17)), fill=255)
            d.ellipse([u(24 + s * 6.5 - 4), u(19), u(24 + s * 6.5 + 4), u(26)], fill=0)
        d.polygon(P((22.5, 28), (24, 25), (25.5, 28)), fill=0)
        for x in (19, 23, 27):
            d.rectangle([u(x), u(34), u(x + 2), u(40)], fill=0)
    elif name == "camp_tents":
        d.polygon(P((2, 43), (15, 17), (28, 43)), fill=255)
        d.polygon(P((20, 43), (33, 13), (46, 43)), fill=255)
        d.polygon(P((11, 43), (15, 32), (19, 43)), fill=0)
        d.polygon(P((29, 43), (33, 30), (37, 43)), fill=0)
        d.line([u(33), u(13), u(33), u(6)], fill=255, width=int(u(1.4)))
        d.polygon(P((33.5, 6), (40, 8), (33.5, 10)), fill=255)
    elif name == "gateway":
        d.chord([u(6), u(4), u(42), u(44)], 180, 360, fill=255)              # a ring of standing stones
        d.rectangle([u(6), u(23), u(16), u(44)], fill=255)
        d.rectangle([u(32), u(23), u(42), u(44)], fill=255)
        d.chord([u(14), u(12), u(34), u(36)], 180, 360, fill=0)
        d.rectangle([u(16), u(23), u(32), u(44)], fill=0)
        for a in range(195, 345, 30):                                       # its joints
            r = math.radians(a)
            d.line([u(24 + 10 * math.cos(r)), u(24 + 10 * math.sin(r)), u(24 + 18 * math.cos(r)),
                    u(24 + 18 * math.sin(r))], fill=0, width=int(u(1.1)))
        d.ellipse([u(20), u(26), u(28), u(34)], fill=255)                   # the light in it
        d.rectangle([u(3), u(43), u(45), u(45)], fill=255)
    elif name == "workshop_gears":
        gear(d, 20, 26, 15, 11.5, 9, 4.5)
        gear(d, 36, 13, 9.5, 7, 7, 3)
    elif name == "landmark":
        pts = []                                                            # a compass star
        for i in range(16):
            a = i / 16 * 2 * math.pi - math.pi / 2
            r = 21 if i % 4 == 0 else 9 if i % 2 == 0 else 5.5
            pts.append((24 + r * math.cos(a), 24 + r * math.sin(a)))
        d.polygon(P(*pts), fill=255)
        d.ellipse([u(21), u(21), u(27), u(27)], fill=0)
    else:
        raise ValueError(name)
    return im


def grow(mask, px):
    """The mask grown by about px of the large canvas's pixels."""
    out = mask
    while px > 0:
        k = min(px, 6)
        out = out.filter(ImageFilter.MaxFilter(2 * k + 1))
        px -= k
    return out


def shrink(mask, px):
    out = mask
    while px > 0:
        k = min(px, 6)
        out = out.filter(ImageFilter.MinFilter(2 * k + 1))
        px -= k
    return out


def compose(fill_mask, rim_mask, shadow_mask):
    """RGBA at the large size: a soft shadow, the ink rim, the pale fill."""
    f = np.asarray(fill_mask, dtype=np.float64) / 255
    r = np.asarray(rim_mask, dtype=np.float64) / 255
    sh = np.asarray(shadow_mask.filter(ImageFilter.GaussianBlur(SS * 1.2)), dtype=np.float64) / 255 * 0.45
    rgb = np.zeros((N, N, 3))
    a = np.zeros((N, N))
    # shadow, then rim, then fill, each over what is under it
    for layer, colour in ((sh, (0, 0, 0)), (r, RIM), (f, FILL)):
        rgb = rgb * (1 - layer[..., None]) + np.array(colour) / 255.0 * layer[..., None]
        a = a + layer * (1 - a)
    # (rgb is premultiplied: straighten it)
    straight = np.dstack([np.where(a > 1e-6, rgb[..., i] / np.maximum(a, 1e-6), 0) for i in range(3)] + [a])
    return Image.fromarray((np.clip(straight, 0, 1) * 255).astype(np.uint8), "RGBA")


def mark(name, hollow):
    m = shape(name)
    rim = grow(m, SS * 2)
    shadow = grow(m, SS * 2).transform(m.size, Image.AFFINE, (1, 0, -SS * 1.2, 0, 1, -SS * 1.6))
    if hollow:
        # the shape's edge alone: a pale line with ink either side, the inside left clear
        inner = shrink(m, SS * 2)
        line = Image.fromarray(np.clip(np.asarray(m, dtype=np.int32) - np.asarray(inner, dtype=np.int32), 0, 255)
                               .astype(np.uint8))
        rim_line = Image.fromarray(np.clip(np.asarray(rim, dtype=np.int32)
                                           - np.asarray(shrink(inner, SS * 2), dtype=np.int32), 0, 255)
                                   .astype(np.uint8))
        big = compose(line, rim_line, Image.new("L", m.size, 0))
    else:
        big = compose(m, rim, shadow)
    return big.resize((M, M), Image.LANCZOS)


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "assets", "overworld")
    sheet = Image.new("RGBA", (M * len(MARKERS), M * 2), (0, 0, 0, 0))
    for i, name in enumerate(MARKERS):
        sheet.paste(mark(name, False), (i * M, 0))
        sheet.paste(mark(name, True), (i * M, M))
    sheet.save(os.path.join(out_dir, "markers.png"))
    meta_path = os.path.join(out_dir, "tiles.json")
    if os.path.exists(meta_path):
        with open(meta_path) as f:
            meta = json.load(f)
        meta["marker"] = M
        meta["markers"] = MARKERS
        with open(meta_path, "w") as f:
            json.dump(meta, f, indent=1)
            f.write("\n")
    print("wrote", os.path.join(out_dir, "markers.png"), sheet.size)


if __name__ == "__main__":
    main()
