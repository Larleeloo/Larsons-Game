#!/usr/bin/env python3
"""
The first map of the main lands - the Brass Vale - written to
assets/overworld/maps/brass_vale.json, with assets/overworld/campaign.json
naming it as the map a new game starts on.

    python3 tools/overworld/make_main_lands.py

The land is made from a few layers of noise shaped by hand: the sea to the
south and west, the Anvil mountains along the north, the Whispering Wood in
the west, the Sallow Marsh in the south-east and the Crystal Reach in the
north-east, a river from the mountains to the sea. The destinations and the
roads between them are written out below; the map editor (Dev tab) opens the
result like any other map, and what is changed there is kept - run this
again only to start the Vale afresh.
"""
import json
import math
import os
import random

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
W, H = 60, 38
SEED = 1025


def noise_grid(rng, cells_x, cells_y):
    return [[rng.random() for _ in range(cells_x + 2)] for _ in range(cells_y + 2)]


def sample(grid, x, y):
    x0, y0 = int(math.floor(x)), int(math.floor(y))
    fx, fy = x - x0, y - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = grid[y0][x0], grid[y0][x0 + 1]
    c, d = grid[y0 + 1][x0], grid[y0 + 1][x0 + 1]
    return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy


def fractal(rng, w, h, cell):
    grids = [noise_grid(rng, int(w / (cell / 2 ** o)) + 1, int(h / (cell / 2 ** o)) + 1) for o in range(3)]

    def at(x, y):
        v, amp, tot = 0.0, 1.0, 0.0
        for o, g in enumerate(grids):
            c = cell / 2 ** o
            v += sample(g, x / c, y / c) * amp
            tot += amp
            amp *= 0.5
        return v / tot
    return at


def build():
    rng = random.Random(SEED)
    height = fractal(rng, W, H, 12)
    wet = fractal(rng, W, H, 9)
    rough = fractal(rng, W, H, 5)
    detail = fractal(rng, W, H, 3)
    tiles = [["g"] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            nx, ny = x / (W - 1), y / (H - 1)
            # the land's shape: high in the north, falling to the sea in the south and the west
            e = height(x, y) * 0.5 + (1 - ny) * 0.62 - 0.04
            # the sea: the south coast, a bay in the south-west, the western shore
            line = 0.87 + 0.03 * math.sin(nx * 9.0) - (0.10 if nx < 0.40 else 0.0) * min(1.0, (0.40 - nx) * 8)
            sea = ny > line + (height(x, y) - 0.5) * 0.16 or nx < 0.04 + (rough(x, y) - 0.5) * 0.07
            m = wet(x, y)
            if sea:
                edge = ny > line + 0.07 or nx < 0.015
                t = "d" if edge else "w"
            elif e > 0.84:
                t = "s" if e > 0.93 else "m"
            elif e > 0.74:
                t = "h" if rough(x, y) > 0.35 else "p"
            elif nx > 0.72 and ny < 0.42 and rough(x, y) > 0.42:
                t = "c"                                   # the Crystal Reach
            elif nx < 0.36 and 0.22 < ny < 0.72 and m > 0.36:
                t = "p" if e > 0.60 else "f"              # the Whispering Wood
            elif nx > 0.76 and ny > 0.58 and m > 0.30:
                t = "x"                                   # the Sallow Marsh
            elif 0.40 < nx < 0.80 and ny > 0.50 and m < 0.46:
                t = "y"                                   # the dry meadows
            elif m > 0.66:
                t = "f"
            elif m < 0.22 and e < 0.5:
                t = "y"
            else:
                t = "g"
            tiles[y][x] = t
    # sand where land meets the sea
    for y in range(H):
        for x in range(W):
            if tiles[y][x] in "wd":
                continue
            near = any(0 <= x + dx < W and 0 <= y + dy < H and tiles[y + dy][x + dx] in "wd"
                       for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            if near and tiles[y][x] in "gyfx":
                tiles[y][x] = "b"
    # the land is not all of a piece: clearings and old pines in the woods, pools in the marsh, groves on the plain
    for y in range(H):
        for x in range(W):
            t, r = tiles[y][x], detail(x, y)
            if t == "f":
                tiles[y][x] = "g" if r < 0.34 else "p" if r > 0.68 else "f"
            elif t == "x":
                tiles[y][x] = "w" if r > 0.70 else "g" if r < 0.30 else "x"
            elif t == "g":
                tiles[y][x] = "f" if r > 0.70 else "h" if r < 0.24 else "g"
            elif t == "c":
                tiles[y][x] = "g" if r < 0.30 else "c"
            elif t == "y":
                tiles[y][x] = "g" if r > 0.70 else "y"
    # the Brassrun: a river from the mountains to the sea
    x, y = 33.0, 3.0
    while y < H - 1:
        for dx in (0, 1):
            xi, yi = int(x) + dx, int(y)
            if 0 <= xi < W and tiles[yi][xi] not in "d":
                tiles[yi][xi] = "w"
        y += 1
        x += math.sin(y * 0.45) * 0.9 + (0.25 if y > 18 else -0.1)
    # a burnt waste round the old watchtower
    for y in range(H):
        for x in range(W):
            if math.hypot(x - 49, (y - 9) * 1.3) < 4.2 and tiles[y][x] not in "wdms":
                tiles[y][x] = "a"
    return tiles


NODES = [
    # id, name, kind, x, y, level, clear, open, about
    ("oakhollow", "Oakhollow", "village", 22, 24, "village", "visit", True,
     "Home: a village of oak and thatch on the edge of the Whispering Wood."),
    ("millers", "Miller's Crossing", "camp", 28, 21, "millers_crossing", "foes", False,
     "The mill on the Brassrun. Bortzal raiders have taken the bridge."),
    ("brasswick", "Brasswick", "town", 37, 20, "town", "visit", True,
     "The market town of the Vale: smiths, alchemists and the notice board."),
    ("wood", "The Whispering Wood", "ruins", 13, 17, "whispering_wood", "quest", False,
     "Old pines and older stones. Something large and blue has been seen among them."),
    ("fishers", "Fisher's Rest", "port", 20, 30, "fishers_rest", "visit", True,
     "A jetty, a smokehouse and the best fishing on the south shore."),
    ("mine", "Copperpit Mine", "mine", 30, 11, "copperpit_mine", "exit", False,
     "The Vale's copper comes from here - or did, before the lamps went out."),
    ("observatory", "The Drurin Observatory", "tower", 42, 13, "drurin_observatory", "visit", True,
     "Drurinean scholars watch the great planet from its brass dome."),
    ("camp", "Khorigale Camp", "camp", 45, 26, "khorigale_camp", "visit", True,
     "Wagons and cook-fires on the dry meadows: the Khorigale trade in everything."),
    ("marsh", "The Sallow Marsh", "crypt", 52, 30, "sallow_marsh", "foes", False,
     "Reeds, mist and the dead who do not lie still."),
    ("watchtower", "The Old Watchtower", "castle", 49, 9, "old_watchtower", "exit", False,
     "The Bortzal warband's hold, on a hill it burnt bare."),
    ("hollow", "Crystal Hollow", "shrine", 54, 14, "crystal_hollow", "exit", False,
     "The edge of the Crystal Reach, where the sky serpents come through."),
    ("gate", "The East Gate", "portal", 57, 20, "", "visit", False,
     "The road east, out of the Vale. Beyond it the main lands go on."),
]

WAYS = [
    ("oakhollow", "millers", [(25, 23)]),
    ("oakhollow", "wood", [(18, 21), (15, 19)]),
    ("oakhollow", "fishers", [(21, 27)]),
    ("millers", "brasswick", [(32, 21)]),
    ("millers", "mine", [(29, 16)]),
    ("brasswick", "observatory", [(40, 17)]),
    ("brasswick", "camp", [(41, 23)]),
    ("camp", "marsh", [(49, 28)]),
    ("observatory", "watchtower", [(45, 11)]),
    ("watchtower", "hollow", [(52, 11)]),
    ("hollow", "gate", [(56, 17)]),
    ("camp", "gate", [(51, 23), (55, 21)]),
]


def main():
    tiles = build()
    # a destination stands on open ground; a road never runs through deep water
    for (_, _, kind, x, y, *_rest) in NODES:
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if tiles[y + dy][x + dx] in "wd" and not (kind == "port"):
                    tiles[y + dy][x + dx] = "g"
        if tiles[y][x] in "wdms":
            tiles[y][x] = "g"
    out = {
        "name": "The Brass Vale",
        "width": W, "height": H, "start": "oakhollow", "music": "shuffle",
        "tiles": ["".join(r) for r in tiles],
        "nodes": [dict(id=i, name=n, kind=k, x=x, y=y, level=lv, open=op, about=ab, clear=cl)
                  for (i, n, k, x, y, lv, cl, op, ab) in NODES],
        "paths": [dict({"from": a, "to": b}, via=[list(v) for v in via]) for (a, b, via) in WAYS],
    }
    folder = os.path.join(ROOT, "assets", "overworld", "maps")
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, "brass_vale.json"), "w") as f:
        json.dump(out, f, indent=1)
        f.write("\n")
    with open(os.path.join(ROOT, "assets", "overworld", "campaign.json"), "w") as f:
        json.dump({"start": "brass_vale"}, f)
        f.write("\n")
    for row in out["tiles"]:
        print(row)
    print(len(NODES), "destinations,", len(WAYS), "roads ->", folder)


if __name__ == "__main__":
    main()
