#!/usr/bin/env python3
"""
The Khorigale's horns and tail, drawn straight onto sprite sheets.

    python3 tools/sprites/make_khorigale.py                 # every sheet: both bodies, both sizes
    python3 tools/sprites/make_khorigale.py --preview out.png --state walk --skin emerald

The other layers are rendered from the Blender models; these two are small
enough to be drawn by rule, frame by frame, on what is already rendered:

  * the head is where the crew cut is (hair/crew_cut), the hips where the
    briefs are (underwear/cotton_briefs) - both are drawn in every frame of
    every sheet;
  * which way she faces is the sheet's direction, unless the nose, seen from
    above, says she has turned (the spins);
  * how far she leans is the line from hips to head, from the side view and
    the view from above together: the horns keep upright on a head that
    only nods and turn over with one that rolls, the tail hangs from hips
    that stand and trails behind hips that swim.

A pair of swept-back horns (horns/khorigale_horns, in bone, ebony or amber)
and a long tail with a leaf tip that sways as the frames go by
(tail/khorigale_tail, in her skin's colours). Sheets are palette PNGs like
the rest: entry 0 transparent, entry 1 the empty-frame marker.
"""
import argparse
import json
import math
import sys
from multiprocessing import Pool
from pathlib import Path

import numpy as np
from PIL import Image

ASSETS = Path(__file__).resolve().parents[2] / "assets"
BODIES = {"feminine": "sprites", "masculine": "sprites_masculine"}
STYLES = {"px128": 128, "px64": 64}
ELEVATIONS = {"side": 0.0, "middle": 45.0, "top": 90.0}
DIRECTIONS = {"s": 0, "se": 45, "e": 90, "ne": 135, "n": 180, "nw": -135, "w": -90, "sw": -45}

HORNS, TAIL = "khorigale_horns", "khorigale_tail"

# line, shade, base, light - all of the 128 colours
HORN_COLOURS = {
    "bone": ["#3e3631", "#918780", "#b8a28c", "#fff0db"],
    "ebony": ["#020100", "#231c18", "#3e3631", "#665d56"],
    "amber": ["#4d1b02", "#a44f08", "#d9740d", "#fb992c"],
}
H_LINE, H_SHADE, H_BASE, H_LIGHT = 2, 3, 4, 5
# the tail in the ears' palette (their skin channel): label 5 the line, 1 the shade, 2 the skin
T_LINE, T_SHADE, T_BASE = 7, 3, 4

# A horn, in head radii: to her right, up, forward. The left one is its mirror.
HORN = [(0.60, 0.70, -0.05), (0.98, 1.12, -0.22), (1.22, 1.62, -0.60), (1.20, 2.05, -1.08), (1.02, 2.36, -1.62)]
# The tail from the small of her back, in 128ths of a frame: up, forward.
TAIL_PATH = [(2, -6), (-2, -11), (-12, -15), (-24, -17), (-34, -20), (-40, -26), (-39, -32), (-34, -35)]


def cells(path, fw):
    """A sheet as its cells: (n, fw, fw) palette indices."""
    a = np.array(Image.open(path))
    rows, cols = a.shape[0] // fw, a.shape[1] // fw
    return a[:rows * fw, :cols * fw].reshape(rows, fw, cols, fw).swapaxes(1, 2).reshape(rows * cols, fw, fw), rows, cols


def box(cell):
    ys, xs = np.nonzero(cell > 1)
    if len(xs) == 0:
        return None
    return xs.min(), xs.max() + 1, ys.min(), ys.max() + 1


def head_at(cell, radius, above, was):
    """The middle of her head: the crew cut's box, its top a head's radius above it when little of it shows."""
    b = box(cell)
    if b is None:
        return was
    x0, x1, y0, y1 = b
    whole = above or y1 - y0 >= 2 * radius - 2
    return (x0 + x1) / 2, (y0 + y1) / 2 if whole else y0 + radius


def hips_at(cell, was):
    b = box(cell)
    if b is None:
        return was
    return (b[0] + b[1]) / 2, (b[2] + b[3]) / 2


def smooth(lo, hi, v):
    t = min(1.0, max(0.0, (v - lo) / (hi - lo)))
    return t * t * (3 - 2 * t)


def turn(v, axis, angle):
    """v about axis by angle (Rodrigues)."""
    c, s = math.cos(angle), math.sin(angle)
    return v * c + np.cross(axis, v) * s + axis * np.dot(axis, v) * (1 - c)


def spline(points, steps):
    """Catmull-Rom through the points: steps samples a span."""
    p = np.asarray(points, dtype=float)
    p = np.vstack([2 * p[0] - p[1], p, 2 * p[-1] - p[-2]])
    out = []
    for i in range(1, len(p) - 2):
        for k in range(steps):
            t = k / steps
            out.append(0.5 * ((2 * p[i]) + (-p[i - 1] + p[i + 1]) * t
                              + (2 * p[i - 1] - 5 * p[i] + 4 * p[i + 1] - p[i + 2]) * t * t
                              + (-p[i - 1] + 3 * p[i] - 3 * p[i + 1] + p[i + 2]) * t ** 3))
    out.append(p[-2])
    return np.array(out)


def bresenham(x0, y0, x1, y1):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        yield x0, y0
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


class Tube:
    """A tapering line on a cell: what it fills, how far along and to which side each pixel is, and how deep."""

    def __init__(self, fw, pts, radii, depth):
        self.fw = fw
        n = len(pts)
        # only the corner of the cell it is in
        reach = float(np.max(radii)) + 2
        x0 = max(0, int(math.floor(np.min(pts[:, 0]) - reach)))
        x1 = min(fw, int(math.ceil(np.max(pts[:, 0]) + reach)) + 1)
        y0 = max(0, int(math.floor(np.min(pts[:, 1]) - reach)))
        y1 = min(fw, int(math.ceil(np.max(pts[:, 1]) + reach)) + 1)
        self.fill = np.zeros((fw, fw), dtype=bool)
        self.along = np.zeros((fw, fw))
        self.side = np.zeros((fw, fw))
        self.depth = np.zeros((fw, fw))
        if x1 <= x0 or y1 <= y0:
            return
        gy, gx = np.mgrid[y0:y1, x0:x1]
        gx = gx + 0.5
        gy = gy + 0.5
        best = np.full(gx.shape, 1e9)
        along = np.zeros(gx.shape)
        side = np.zeros(gx.shape)
        deep = np.zeros(gx.shape)
        for k in range(n - 1):
            ax, ay = pts[k]
            bx, by = pts[k + 1]
            dx, dy = bx - ax, by - ay
            ll = dx * dx + dy * dy
            u = np.zeros(gx.shape) if ll < 1e-9 else np.clip(((gx - ax) * dx + (gy - ay) * dy) / ll, 0, 1)
            ox, oy = gx - (ax + u * dx), gy - (ay + u * dy)
            score = np.hypot(ox, oy) - (radii[k] + u * (radii[k + 1] - radii[k]))
            better = score < best
            best = np.where(better, score, best)
            along = np.where(better, (k + u) / (n - 1), along)
            side = np.where(better, ox * 0.6 + oy * 0.8, side)                 # towards the lower right: the shade
            deep = np.where(better, depth[k] + u * (depth[k + 1] - depth[k]), deep)
        self.fill[y0:y1, x0:x1] = best <= 0
        self.along[y0:y1, x0:x1] = along
        self.side[y0:y1, x0:x1] = side
        self.depth[y0:y1, x0:x1] = deep
        # a thin stretch is still a line: no gaps
        for k in range(n - 1):
            a = (int(math.floor(pts[k][0])), int(math.floor(pts[k][1])))
            b = (int(math.floor(pts[k + 1][0])), int(math.floor(pts[k + 1][1])))
            if max(abs(a[0]), abs(a[1]), abs(b[0]), abs(b[1])) > 4 * fw:
                continue
            for x, y in bresenham(a[0], a[1], b[0], b[1]):
                if 0 <= x < fw and 0 <= y < fw:
                    self.fill[y, x] = True

    def outline(self):
        f = self.fill
        o = np.zeros_like(f)
        o[1:, :] |= f[:-1, :]
        o[:-1, :] |= f[1:, :]
        o[:, 1:] |= f[:, :-1]
        o[:, :-1] |= f[:, 1:]
        return o & ~f


def frame_axes(yaw):
    """Her right, up and forward, as (to the screen's right, up, towards the camera)."""
    c, s = math.cos(yaw), math.sin(yaw)
    return np.array([-c, 0.0, s]), np.array([0.0, 1.0, 0.0]), np.array([s, 0.0, c])


def project(offsets, elevation):
    """World offsets (right, up, towards the camera) on the screen at an elevation: x, y (down) and depth."""
    phi = math.radians(elevation)
    x = offsets[:, 0]
    y = -(offsets[:, 1] * math.cos(phi) - offsets[:, 2] * math.sin(phi))
    d = offsets[:, 2] * math.cos(phi) + offsets[:, 1] * math.sin(phi)
    return x, y, d


class Pose:
    """Where her head and hips are in one frame, which way she faces and how she leans."""

    def __init__(self):
        self.head = {}
        self.hips = {}
        self.yaw = 0.0
        self.axis = np.array([1.0, 0.0, 0.0])
        self.lean = 0.0
        self.hips_up = 0.0

    def axes(self, part):
        """Her axes with part of the lean: 0 upright whatever the hips do, 1 along her back."""
        r, u, f = frame_axes(self.yaw)
        a = self.lean * part
        if a < 1e-3:
            return r, u, f
        return turn(r, self.axis, a), turn(u, self.axis, a), turn(f, self.axis, a)


def poses(sheets, fw, direction):
    """A pose a frame, from the hair's and the briefs' sheets of the three elevations and the nose from above."""
    scale = fw / 128.0
    radius = 5.0 * scale
    nominal = math.radians(DIRECTIONS[direction])
    hair, briefs, nose_top = sheets["hair"], sheets["briefs"], sheets["nose"]
    count = len(hair["side"])
    centre = (fw / 2, fw / 2)
    out = []
    was_head = {e: centre for e in ELEVATIONS}
    was_hips = {e: centre for e in ELEVATIONS}
    for i in range(count):
        p = Pose()
        for e in ELEVATIONS:
            p.head[e] = was_head[e] = head_at(hair[e][i], radius, e == "top", was_head[e])
            p.hips[e] = was_hips[e] = hips_at(briefs[e][i], was_hips[e])
        p.hips_up = fw / 2 - p.hips["side"][1]
        # which way she faces: the sheet's way, unless the nose says otherwise by a good deal
        p.yaw = nominal
        b = box(nose_top[i])
        if b is not None:
            nx, ny = (b[0] + b[1]) / 2 - p.head["top"][0], (b[2] + b[3]) / 2 - p.head["top"][1]
            if math.hypot(nx, ny) >= 3 * scale:
                seen = math.atan2(nx, ny)
                off = (seen - nominal + math.pi) % (2 * math.pi) - math.pi
                if abs(off) > math.radians(40):
                    p.yaw = seen
        # how she leans: hips to head, from the side and from above
        spine = np.array([p.head["side"][0] - p.hips["side"][0],
                          p.hips["side"][1] - p.head["side"][1],
                          p.head["top"][1] - p.hips["top"][1]])
        length = np.linalg.norm(spine)
        if length > 4 * scale:
            spine /= length
            p.lean = math.acos(max(-1.0, min(1.0, spine[1])))
            axis = np.cross(np.array([0.0, 1.0, 0.0]), spine)
            n = np.linalg.norm(axis)
            if n > 1e-6:
                p.axis = axis / n
            else:
                p.lean = 0.0 if spine[1] > 0 else math.pi
        out.append(p)
    return out


def draw_horns(fw, pose, elevation, big):
    """One frame's horns: a cell of palette indices."""
    scale = fw / 128.0
    radius = 5.0 * scale
    cell = np.zeros((fw, fw), dtype=np.uint8)
    hx, hy = pose.head[elevation]
    right, up, forward = pose.axes(smooth(math.radians(40), math.radians(100), pose.lean))
    curve = spline(HORN, 4)
    n = len(curve)
    widths = np.linspace(1.15, 0.45, n) * scale * (1.0 if big else 1.0)
    tubes = []
    for side in (1.0, -1.0):
        world = np.outer(curve[:, 0] * side, right) + np.outer(curve[:, 1], up) + np.outer(curve[:, 2], forward)
        x, y, d = project(world * radius, ELEVATIONS[elevation])
        pts = np.stack([hx + x, hy + y], axis=1)
        tubes.append((float(d.mean()), Tube(fw, pts, widths, d)))
    tubes.sort(key=lambda t: t[0])                     # the far one first
    gy, gx = np.mgrid[0:fw, 0:fw]
    in_head = (gx + 0.5 - hx) ** 2 + (gy + 0.5 - hy) ** 2 < (radius - 0.6 * scale) ** 2
    for _, t in tubes:
        hidden = in_head & (t.depth < -0.12 * radius)   # behind her head
        line = t.outline() & ~hidden
        fill = t.fill & ~hidden
        if not big:
            # on the small frames a full outline is a blob: the line under and beside it only
            f = t.fill
            under = np.zeros_like(f)
            under[1:, :] |= f[:-1, :]
            under[:, 1:] |= f[:, :-1]
            line &= under
        cell[line] = H_LINE
        tone = np.where(t.side > 0.3 * scale, H_SHADE, np.where((t.side < -0.25 * scale) | (t.along > 0.82), H_LIGHT, H_BASE))
        if not big:
            tone = np.where(t.along > 0.7, H_LIGHT, H_BASE)     # a pixel wide: no room for a shaded side
        cell[fill] = tone[fill].astype(np.uint8)
    return cell


def tail_points(fw, pose, phase):
    """The tail's line in the world, from her hips: (n, 3) offsets and the radius at each."""
    scale = fw / 128.0
    right, up, forward = pose.axes(smooth(math.radians(35), math.radians(75), pose.lean))
    curve = spline(TAIL_PATH, 5) * scale
    n = len(curve)
    t = np.linspace(0, 1, n)
    # it never hangs dead straight: a slow bend one way and the tip the other, and the sway on top
    sway = scale * (6.0 * t * np.sin(1.4 * math.pi * t) + 3.0 * t ** 1.5 * np.sin(phase - 3.0 * t))
    world = np.outer(sway, right) + np.outer(curve[:, 0], up) + np.outer(curve[:, 1], forward)
    # it lies on the ground it reaches
    ground = -48 * scale + 1.5 * scale - pose.hips_up
    world[:, 1] = np.maximum(world[:, 1], ground)
    radii = np.where(t < 0.84, 1.7 - 0.95 * t / 0.84, 0.0)
    tip = (t >= 0.84)
    k = (t[tip] - 0.84) / 0.16                                  # the leaf at its end
    radii[tip] = np.where(k < 0.45, 0.75 + (1.6 - 0.75) * k / 0.45, 1.6 - 1.3 * (k - 0.45) / 0.55)
    return world, radii * scale


def draw_tail(fw, pose, elevation, phase, body, big):
    cell = np.zeros((fw, fw), dtype=np.uint8)
    scale = fw / 128.0
    world, radii = tail_points(fw, pose, phase)
    x, y, d = project(world, ELEVATIONS[elevation])
    px, py = pose.hips[elevation]
    t = Tube(fw, np.stack([px + x, py + y], axis=1), radii, d)
    line, fill = t.outline(), t.fill
    if not big:
        f = t.fill
        under = np.zeros_like(f)
        under[1:, :] |= f[:-1, :]
        under[:, 1:] |= f[:, :-1]
        line &= under
    cell[line] = T_LINE
    tone = np.where(t.side > 0.45 * scale, T_SHADE, T_BASE)
    cell[fill] = tone[fill].astype(np.uint8)
    if elevation == "top" and body is not None:
        cell[body > 1] = 0                                      # from above she is over it
    return cell


def sheet_of(cells_, rows, cols, fw, frames):
    """Cells back into a sheet; a frame with nothing in it is marked so it is still a frame."""
    out = np.zeros((rows * fw, cols * fw), dtype=np.uint8)
    for i, c in enumerate(cells_):
        if i < frames and not (c > 1).any():
            c = c.copy()
            c[0, 0] = 1
        r, k = divmod(i, cols)
        out[r * fw:(r + 1) * fw, k * fw:(k + 1) * fw] = c
    return out


def rgb(colour):
    return [int(colour[i:i + 2], 16) for i in (1, 3, 5)]


def save(sheet, palette, path):
    im = Image.fromarray(sheet, "P")
    flat = [0, 0, 0, 0, 0, 0]
    for c in palette:
        flat += c
    im.putpalette(flat)
    im.save(path, transparency=bytes([0, 1] + [255] * len(palette)), optimize=True)


def tail_palette(root, style):
    """The ears' colours: the tail is of her skin, and takes her skin's colour as they do."""
    im = Image.open(root / "ears" / f"pointed_{style}" / "idle_side_s.png")
    pal = im.getpalette()
    return [pal[i * 3:i * 3 + 3] for i in range(2, 14)]


def load(root, body, style, state, direction):
    fw = STYLES[style]
    sheets = {"hair": {}, "briefs": {}}
    shape = None
    for e in ELEVATIONS:
        name = f"{state}_{e}_{direction}.png"
        sheets["hair"][e], rows, cols = cells(root / "hair" / f"crew_cut_{style}" / name, fw)
        sheets["briefs"][e], _, _ = cells(root / "underwear" / f"cotton_briefs_{style}" / name, fw)
        if e == "top":
            shape = (rows, cols)
    top = f"{state}_top_{direction}.png"
    sheets["nose"], _, _ = cells(root / "nose" / f"straight_{style}" / top, fw)
    sheets["body"], _, _ = cells(root / "body" / f"{body}_{style}" / top, fw)
    side, _, _ = cells(root / "body" / f"{body}_{style}" / f"{state}_side_{direction}.png", fw)
    frames = max(i for i in range(len(side)) if (side[i] > 1).any()) + 1
    return sheets, shape, frames


def make(job):
    body, style, state, direction = job
    root = ASSETS / BODIES[body]
    fw = STYLES[style]
    big = fw >= 128
    sheets, (rows, cols), frames = load(root, body, style, state, direction)
    ps = poses(sheets, fw, direction)
    horn_pal = [rgb(c) for c in HORN_COLOURS["bone"]]
    tail_pal = tail_palette(root, style)
    for e in ELEVATIONS:
        horns, tails = [], []
        for i in range(rows * cols):
            if i >= frames:
                horns.append(np.zeros((fw, fw), dtype=np.uint8))
                tails.append(horns[-1])
                continue
            phase = 2 * math.pi * i / frames
            horns.append(draw_horns(fw, ps[i], e, big))
            tails.append(draw_tail(fw, ps[i], e, phase, sheets["body"][i], big))
        name = f"{state}_{e}_{direction}.png"
        save(sheet_of(horns, rows, cols, fw, frames), horn_pal, root / "horns" / f"{HORNS}_{style}" / name)
        save(sheet_of(tails, rows, cols, fw, frames), tail_pal, root / "tail" / f"{TAIL}_{style}" / name)
    return job


def states(root, body, style):
    names = set()
    for f in (root / "body" / f"{body}_{style}").glob("*_side_s.png"):
        names.add(f.name[:-len("_side_s.png")])
    return sorted(names)


def folders(root, style):
    fw = STYLES[style]
    profile = {"frameWidth": fw, "frameHeight": fw, "pixelArt": True}
    horns = root / "horns" / f"{HORNS}_{style}"
    tail = root / "tail" / f"{TAIL}_{style}"
    for d in (horns, tail):
        d.mkdir(parents=True, exist_ok=True)
        (d / "profile.json").write_text(json.dumps(profile))
    (horns / "variants.json").write_text(json.dumps({
        "version": 1, "labels": 4, "default": {"own": "bone"},
        "channels": {"own": {"labels": [0, 1, 2, 3], "options": HORN_COLOURS,
                             "swatch": {k: v[2] for k, v in HORN_COLOURS.items()}}}}, separators=(",", ":")))
    (tail / "variants.json").write_text((root / "ears" / f"pointed_{style}" / "variants.json").read_text())


def check_colours():
    palette = json.loads((ASSETS / "sprites" / "pixel_palette.json").read_text())
    known = {tuple(c) for key in ("fills", "lines") for c in palette[key]}
    for name, colours in HORN_COLOURS.items():
        for c in colours:
            if tuple(rgb(c)) not in known:
                sys.exit(f"{name}: {c} is not one of the 128 colours")


def preview(path, body, style, state, frame, skin, zoom):
    """A look at one frame from every side, dressed in nothing but the two layers."""
    root = ASSETS / BODIES[body]
    fw = STYLES[style]
    big = fw >= 128

    def swap(folder, name, option):
        im = Image.open(root / folder / name)
        pal = im.getpalette()
        variants = json.loads((root / folder / "variants.json").read_text())
        ch = variants["channels"].get("skin")
        if ch and option in ch["options"]:
            for label, colour in zip(ch["labels"], ch["options"][option]):
                pal[(label + 2) * 3:(label + 2) * 3 + 3] = rgb(colour)
        return np.array(im), pal

    def rgba(indices, pal):
        out = np.zeros(indices.shape + (4,), dtype=np.uint8)
        table = np.array(pal + [0] * (768 - len(pal)), dtype=np.uint8).reshape(-1, 3)
        out[..., :3] = table[indices]
        out[..., 3] = np.where(indices > 1, 255, 0)
        return Image.fromarray(out, "RGBA")

    horn_pal = [0] * 6 + [v for c in HORN_COLOURS["bone"] for v in rgb(c)]
    tail_pal = [0] * 6 + [v for c in tail_palette(root, style) for v in c]
    variants = json.loads((root / "ears" / f"pointed_{style}" / "variants.json").read_text())["channels"]["skin"]
    for label, colour in zip(variants["labels"], variants["options"][skin]):
        tail_pal[(label + 2) * 3:(label + 2) * 3 + 3] = rgb(colour)
    sheet = Image.new("RGBA", (fw * 8, fw * 3), (86, 104, 86, 255))
    for col, direction in enumerate(DIRECTIONS):
        sheets, (rows, cols), frames = load(root, body, style, state, direction)
        ps = poses(sheets, fw, direction)
        i = min(frame, frames - 1)
        for row, e in enumerate(ELEVATIONS):
            name = f"{state}_{e}_{direction}.png"
            at = ((i % cols) * fw, (i // cols) * fw)

            def layer(folder, option=None):
                indices, pal = swap(folder, name, option)
                return rgba(indices[at[1]:at[1] + fw, at[0]:at[0] + fw], pal)

            tail = rgba(draw_tail(fw, ps[i], e, 2 * math.pi * i / frames, sheets["body"][i], big), tail_pal)
            horns = rgba(draw_horns(fw, ps[i], e, big), horn_pal)
            front = e != "top" and direction in ("s", "se", "sw")
            tile = Image.new("RGBA", (fw, fw), (0, 0, 0, 0))
            if front:
                tile.alpha_composite(tail)
            tile.alpha_composite(layer(f"body/{body}_{style}", skin))
            tile.alpha_composite(layer(f"underwear/cotton_briefs_{style}"))
            tile.alpha_composite(layer(f"ears/pointed_{style}", skin))
            tile.alpha_composite(layer(f"nose/straight_{style}", skin))
            if not front:
                tile.alpha_composite(tail)
            tile.alpha_composite(layer(f"hair/crew_cut_{style}"))
            tile.alpha_composite(horns)
            sheet.alpha_composite(tile, (col * fw, row * fw))
    sheet.resize((sheet.width * zoom, sheet.height * zoom), Image.NEAREST).save(path)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--preview", help="write one frame from every side to this picture instead")
    ap.add_argument("--body", default=None, choices=list(BODIES))
    ap.add_argument("--style", default=None, choices=list(STYLES))
    ap.add_argument("--state", default=None)
    ap.add_argument("--frame", type=int, default=0)
    ap.add_argument("--skin", default="emerald")
    ap.add_argument("--zoom", type=int, default=3)
    ap.add_argument("--jobs", type=int, default=4)
    args = ap.parse_args()
    check_colours()
    if args.preview:
        preview(args.preview, args.body or "feminine", args.style or "px128", args.state or "idle",
                args.frame, args.skin, args.zoom)
        return
    jobs = []
    for body in ([args.body] if args.body else BODIES):
        root = ASSETS / BODIES[body]
        for style in ([args.style] if args.style else STYLES):
            folders(root, style)
            for state in ([args.state] if args.state else states(root, body, style)):
                for direction in DIRECTIONS:
                    jobs.append((body, style, state, direction))
    done = 0
    with Pool(args.jobs) as pool:
        for _ in pool.imap_unordered(make, jobs, chunksize=4):
            done += 1
            if done % 200 == 0 or done == len(jobs):
                print(f"{done}/{len(jobs)}", flush=True)


if __name__ == "__main__":
    main()
