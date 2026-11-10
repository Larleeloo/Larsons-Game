#!/usr/bin/env python3
"""Writes the horde maps the game ships with into assets/horde/.

    python3 tools/levels/make_horde_maps.py [assets/horde]

A horde map is a level (level.json, as Creative Mode saves it) with a
"horde" block: the rules of the game played on it. Each map here is a walled
yard: gaps in its walls boarded up with barricades, Risen spawns outside
them, weapon boards along the walls, a mystery box, and a gate that opens
for points onto a second yard with better boards. Open one in the horde
editor (main menu, Horde tab, with the Dev tab unlocked) to change it.
"""
import json
import math
import os
import random
import sys


class Level:
    def __init__(self, name, size=128):
        self.name, self.size, self.objects, self.next = name, size, [], 1
        self.head = {}

    def add(self, type_, x, z, yaw=0, **props):
        o = {"id": self.next, "type": type_, "x": round(x, 2), "z": round(z, 2), "yaw": yaw}
        o.update(props)
        self.objects.append(o)
        self.next += 1
        return o

    def wall(self, x0, z0, x1, z1, height=2.6, gaps=()):
        """A wall from (x0, z0) to (x1, z1), in pieces, leaving `gaps`: (distance along it, width)."""
        length = math.hypot(x1 - x0, z1 - z0)
        ux, uz = (x1 - x0) / length, (z1 - z0) / length
        yaw = round(math.degrees(math.atan2(uz, ux)), 1) % 360
        cuts = sorted((max(0, at - w / 2), min(length, at + w / 2)) for at, w in gaps)
        spans, start = [], 0.0
        for a, b in cuts:
            if a > start:
                spans.append((start, a))
            start = b
        if start < length:
            spans.append((start, length))
        for a, b in spans:
            d = a
            while d < b - 1e-6:
                step = min(12.0, b - d)
                mid = d + step / 2
                self.add("wall", x0 + ux * mid, z0 + uz * mid, yaw, length=round(step, 2), height=height,
                         opening="none")
                d += step
        return yaw

    def write(self, root, folder):
        path = os.path.join(root, folder)
        os.makedirs(path, exist_ok=True)
        head = {"format": 1, "name": self.name, "size": self.size}
        head.update(self.head)
        head["objects"] = self.objects
        with open(os.path.join(path, "level.json"), "w", encoding="utf-8") as f:
            f.write("{\n")
            keys = [k for k in head if k != "objects"]
            for k in keys:
                f.write(" %s: %s,\n" % (json.dumps(k), json.dumps(head[k], ensure_ascii=False)))
            f.write(' "objects": [\n')
            f.write(",\n".join("  " + json.dumps(o, ensure_ascii=False) for o in self.objects))
            f.write("\n ]\n}\n")
        print("wrote", path, "-", len(self.objects), "objects")


def night(fog=70, start=21.5):
    return {"zenith": "#1E2A4A", "horizon": "#56607E", "fog": fog, "backdrops": [],
            "time": {"start": start, "day": 60},
            "planet": {"show": True, "azimuth": 150, "elevation": 34, "size": 30, "rings": True, "seed": 4},
            "clouds": 0.3, "stars": 1.0, "aurora": 0.25, "mist": {"thick": 0.45, "height": 2.2}, "rays": 0.2}


def ring_of_trees(lv, rng, inner, outer, n, kinds, keep=()):
    for _ in range(n):
        a, d = rng.uniform(0, math.tau), rng.uniform(inner, outer)
        x, z = math.cos(a) * d, math.sin(a) * d
        if abs(x) > lv.size / 2 - 3 or abs(z) > lv.size / 2 - 3:
            continue
        if any(math.hypot(x - kx, z - kz) < kr for kx, kz, kr in keep):
            continue
        lv.add("scenery", x, z, rng.randrange(0, 360), sprite=rng.choice(kinds))


def board(lv, x, z, yaw, item, cost, tier=None):
    props = {"name": "", "item": item, "tier": tier or "its first", "cost": cost, "again": 0}
    lv.add("wall_buy", x, z, yaw, **props)


def foundry_yard():
    """The Foundry Yard: a walled works yard at night, three boarded gaps, a gate east to the store yard."""
    lv = Level("The Foundry Yard")
    lv.head = {"sky": night(), "music": "shuffle",
               "horde": {"points": 500, "first": 6, "more": 3, "atOnce": 12, "growth": 0.12, "rest": 10,
                         "lives": 1, "kit": "dagger, health potion", "kinds": []}}
    rng = random.Random(1025)
    W, N, E, S = -14, -11, 14, 11
    # the yard's walls, each with its gap (the east one a gate)
    lv.wall(W, N, E, N, gaps=[(14, 2.3)])               # north: a gap in the middle
    lv.wall(W, S, E, S, gaps=[(8, 2.3)])                # south: a gap west of the middle
    lv.wall(W, N, W, S, gaps=[(11, 2.3)])               # west: a gap in the middle
    lv.wall(E, N, E, S, gaps=[(11, 4.4)])               # east: the gate
    lv.add("barricade", 0, N, 0, name="North boards", planks=6)
    lv.add("barricade", -6, S, 0, name="South boards", planks=6)
    lv.add("barricade", W, 0, 90, name="West boards", planks=6)
    lv.add("lock", E, 0, 90, name="The east gate", key="iron_key", consumes=True, cost=750, model="iron_gate")
    # the store yard beyond the gate
    E2, N2, S2 = 32, -9, 9
    lv.wall(E, N2, E2, N2, gaps=[(9, 2.3)])
    lv.wall(E, S2, E2, S2)
    lv.wall(E2, N2, E2, S2, gaps=[(9, 2.3)])
    lv.add("barricade", E + 9, N2, 0, name="Store yard boards", planks=6)
    lv.add("barricade", E2, 0, 90, name="East boards", planks=6)
    # where the Risen come out of the ground
    for x, z in ((0, -19), (-4, -21), (4, -22)):
        lv.add("horde_spawn", x, z, kind="any", **{"from": 1}, after="")
    for x, z in ((-6, 19), (-10, 21)):
        lv.add("horde_spawn", x, z, kind="any", **{"from": 1}, after="")
    for x, z in ((-22, 0), (-24, 4)):
        lv.add("horde_spawn", x, z, kind="any", **{"from": 2}, after="")
    for x, z in ((E + 9, -17), (E + 6, -19)):
        lv.add("horde_spawn", x, z, kind="any", **{"from": 1}, after="The east gate")
    for x, z in ((40, 0), (42, -3)):
        lv.add("horde_spawn", x, z, kind="any", **{"from": 1}, after="The east gate")
    lv.add("spawn", 0, 3, 180)
    # the boards: cheap ones in the yard, the better past the gate
    board(lv, -8, N + 0.5, 180, "dagger", 250)
    board(lv, 7, N + 0.5, 180, "sword", 500)
    board(lv, W + 0.5, -6, 270, "mace", 600)
    board(lv, 4, S - 0.5, 0, "longbow", 750)
    board(lv, W + 0.5, 6, 270, "health_potion", 150)
    board(lv, 20, S2 - 0.5, 0, "crossbow", 900)
    board(lv, 27, S2 - 0.5, 0, "battle_axe", 1000)
    board(lv, 17, N2 + 0.5, 180, "wand", 1200)
    board(lv, 29, N2 + 0.5, 180, "crystal_staff", 1500)
    board(lv, E2 - 0.5, -5, 90, "stoneskin_potion", 1500)
    board(lv, E2 - 0.5, 5, 90, "might_potion", 2000)
    board(lv, E + 0.5, 6, 270, "sword", 3000, "crystal")
    lv.add("mystery_box", 23, 2, 0, name="Mystery box", cost=950, pool="")
    # the works: what stands about the yard
    lv.add("building", -9, 6.5, 90, kind="boiler")
    lv.add("building", 9.5, -7, 0, kind="tesla_coil")
    lv.add("building", 10, 7.5, 0, kind="crate")
    lv.add("building", 11.2, 6.6, 30, kind="crate")
    lv.add("building", 9, 8.8, 0, kind="barrel")
    lv.add("building", -11.5, -8.5, 0, kind="barrel")
    lv.add("building", -10.4, -9, 0, kind="barrel")
    lv.add("building", -2.5, 9.6, 0, kind="hand_cart")
    lv.add("building", 28, -5.5, 0, kind="tesla_coil")
    lv.add("building", 18, 5.5, 20, kind="crate")
    lv.add("workbench", -4.5, N + 1.1, 180, name="Workbench", model="workbench")
    lv.add("cauldron", 12, -2.5, 270, name="Cauldron", model="cauldron")
    for x, z in ((-12.5, -9.6), (12.5, -9.6), (-12.5, 9.6), (12.5, 9.6), (30.5, -7.6), (30.5, 7.6), (16, 7.6)):
        lv.add("light", x, z, colour="lantern", radius=9, strength=1, fixture="lantern_post")
    lv.add("light", 0, 0, colour="fire", radius=10, strength=0.9, fixture="glow")
    lv.add("building", -30, -34, 140, kind="foundry")
    lv.add("building", 6, 40, 200, kind="windmill")
    keep = [(0, 0, 26), (23, 0, 22), (-30, -34, 12), (6, 40, 9), (0, -20, 5), (-8, 20, 5), (-23, 2, 5), (41, -1, 5),
            (22, -18, 5)]
    ring_of_trees(lv, rng, 20, 60, 90, ["dead_tree", "dead_tree", "pine_tree", "tall_pine", "oak_tree", "thorn_bush"],
                  keep)
    ring_of_trees(lv, rng, 16, 58, 60, ["boulder", "mossy_rock", "standing_stone", "tall_grass_a", "tall_grass_b",
                                        "fern", "mossy_log", "stones"], keep)
    return lv


def gravewood():
    """Gravewood Clearing: no walls at all - a ring of standing stones in a dead wood, the Risen from every side."""
    lv = Level("Gravewood Clearing")
    lv.head = {"sky": night(fog=55, start=23), "music": "shuffle",
               "horde": {"points": 500, "first": 5, "more": 3, "atOnce": 10, "growth": 0.12, "rest": 12,
                         "lives": 2, "kit": "dagger, health potion x2", "kinds": []}}
    rng = random.Random(7)
    lv.add("spawn", 0, 2, 180)
    # the ring of stones, the box in the middle of it
    for i in range(12):
        a = math.tau * i / 12
        if i % 3 == 0:
            continue                                     # (four ways in)
        lv.add("scenery", math.cos(a) * 9, math.sin(a) * 9, rng.randrange(0, 360), sprite="standing_stone")
    lv.add("mystery_box", 0, -3, 0, name="Mystery box", cost=950, pool="")
    lv.add("light", 0, 0, colour="magic", radius=12, strength=1, fixture="glow")
    # boards on the four roads out, each by a lantern
    roads = [(0, -16, 180, "dagger", 250, "sword", 500), (16, 0, 270, "mace", 600, "longbow", 750),
             (0, 16, 0, "crossbow", 900, "battle_axe", 1000), (-16, 0, 90, "wand", 1200, "health_potion", 150)]
    for x, z, yaw, a, ca, b, cb in roads:
        px, pz = (2.2, 0) if x == 0 else (0, 2.2)
        board(lv, x - px, z - pz, yaw, a, ca)
        board(lv, x + px, z + pz, yaw, b, cb)
        lv.add("light", x * 0.8, z * 0.8, colour="lantern", radius=9, strength=1, fixture="lantern_post")
    board(lv, 11.5, 11.5, 315, "crystal_staff", 1500)
    board(lv, -11.5, 11.5, 45, "stoneskin_potion", 1500)
    board(lv, -11.5, -11.5, 135, "might_potion", 2000)
    board(lv, 11.5, -11.5, 225, "sword", 3000, "crystal")
    lv.add("cauldron", 4, 5, 200, name="Cauldron", model="cauldron")
    lv.add("workbench", -4.5, 5, 160, name="Workbench", model="workbench")
    # they come out of the graves, all round: the nearer ring at once, the farther from round 3
    for i in range(10):
        a = math.tau * i / 10 + 0.3
        lv.add("horde_spawn", math.cos(a) * 26, math.sin(a) * 26, kind="any", **{"from": 1}, after="")
    for i in range(8):
        a = math.tau * i / 8
        lv.add("horde_spawn", math.cos(a) * 38, math.sin(a) * 38, kind="any", **{"from": 3}, after="")
    for i in range(4):
        a = math.tau * i / 4 + 0.8
        lv.add("horde_spawn", math.cos(a) * 30, math.sin(a) * 30, kind="risen_brute", **{"from": 5}, after="")
    keep = [(0, 0, 19)]
    ring_of_trees(lv, rng, 19, 60, 150, ["dead_tree", "dead_tree", "dead_tree", "pine_tree", "tall_pine", "thorn_bush",
                                         "willow_tree"], keep)
    ring_of_trees(lv, rng, 12, 58, 110, ["standing_stone", "mossy_rock", "boulder", "tall_grass_a", "tall_grass_b",
                                         "fern", "mossy_log", "stones", "flat_rock"], keep[:0] + [(0, 0, 11)])
    return lv


def main(argv):
    root = argv[1] if len(argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "..", "assets", "horde")
    foundry_yard().write(root, "foundry_yard")
    gravewood().write(root, "gravewood_clearing")


if __name__ == "__main__":
    main(sys.argv)
